/**
 * Copyright 2015 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package com.qwazr.search.index;

import com.qwazr.utils.StringUtils;
import com.qwazr.utils.TimeTracker;
import com.qwazr.utils.server.ServerException;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.TermsQuery;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.postingshighlight.PostingsHighlighter;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;

class QueryUtils {

	final static SortField buildSortField(Map<String, FieldDefinition> fields, String field,
			QueryDefinition.SortEnum sortEnum) throws ServerException {

		final boolean reverse;
		final Object missingValue;
		switch (sortEnum) {
		case ascending:
			missingValue = null;
			reverse = false;
			break;
		case ascending_missing_first:
			missingValue = SortField.STRING_LAST;
			reverse = false;
			break;
		case ascending_missing_last:
			missingValue = SortField.STRING_FIRST;
			reverse = false;
			break;
		case descending:
			missingValue = null;
			reverse = true;
			break;
		case descending_missing_first:
			missingValue = SortField.STRING_LAST;
			reverse = true;
			break;
		case descending_missing_last:
			missingValue = SortField.STRING_FIRST;
			reverse = true;
			break;
		default:
			missingValue = null;
			reverse = false;
			break;
		}

		final SortField sortField;

		if ("$score".equals(field)) {
			sortField = new SortField(null, SortField.Type.SCORE, !reverse);
		} else if ("$doc".equals(field)) {
			sortField = new SortField(null, SortField.Type.DOC, reverse);
		} else {
			FieldDefinition fieldDefinition = fields.get(field);
			if (fieldDefinition == null)
				throw new ServerException(Response.Status.BAD_REQUEST, "Unknown sort field: " + field);
			sortField = fieldDefinition.getSortField(field, reverse);
		}
		if (missingValue != null)
			sortField.setMissingValue(missingValue);
		return sortField;
	}

	final static Sort buildSort(Map<String, FieldDefinition> fields,
			LinkedHashMap<String, QueryDefinition.SortEnum> sorts) throws ServerException {
		if (sorts.isEmpty())
			return null;
		final SortField[] sortFields = new SortField[sorts.size()];
		int i = 0;
		for (Map.Entry<String, QueryDefinition.SortEnum> sort : sorts.entrySet())
			sortFields[i++] = buildSortField(fields, sort.getKey(), sort.getValue());
		if (sortFields.length == 1)
			return new Sort(sortFields[0]);
		return new Sort(sortFields);
	}

	final static Term facetTerm(String indexedField, String dim, String... path) {
		return new Term(indexedField, FacetsConfig.pathToString(dim, path));
	}

	final static List<Term> facetTerms(String indexedField, String dim, Collection<String> terms) {
		List<Term> termList = new ArrayList<>(terms.size());
		for (String term : terms)
			termList.add(facetTerm(indexedField, dim, term));
		return termList;
	}

	final static Query facetTermQuery(FacetsConfig facetsConfig, String dim, Set<String> filter_terms) {
		String indexedField = facetsConfig.getDimConfig(dim).indexFieldName;
		if (filter_terms.size() == 1)
			return new TermQuery(facetTerm(indexedField, dim, filter_terms.iterator().next()));
		return new TermsQuery(facetTerms(indexedField, dim, filter_terms));
	}

	final static Query buildFacetFiltersQuery(FacetsConfig facetsConfig, List<Map<String, Set<String>>> facet_filters,
			Query query) {
		if (facet_filters.isEmpty())
			return query;

		DrillDownQuery ddq;

		BooleanQuery.Builder rootBuilder = new BooleanQuery.Builder();
		rootBuilder.add(query, BooleanClause.Occur.MUST);

		for (Map<String, Set<String>> mapEntry : facet_filters) {
			for (Map.Entry<String, Set<String>> entry : mapEntry.entrySet()) {
				Set<String> filter_terms = entry.getValue();
				if (filter_terms == null || filter_terms.isEmpty())
					continue;
				String dim = entry.getKey();
				Query orFilterQuery = facetTermQuery(facetsConfig, dim, filter_terms);
				rootBuilder.add(orFilterQuery, BooleanClause.Occur.FILTER);
			}
		}

		return rootBuilder.build();
	}

	final static Query getLuceneQuery(QueryDefinition queryDef, UpdatableAnalyzer analyzer)
			throws QueryNodeException, ParseException {

		// Configure the QueryParser
		final StandardQueryParser parser = new StandardQueryParser(analyzer);
		if (queryDef.multi_field != null && !queryDef.multi_field.isEmpty()) {
			Set<String> fieldSet = queryDef.multi_field.keySet();
			String[] fieldArray = fieldSet.toArray(new String[fieldSet.size()]);
			parser.setMultiFields(fieldArray);
			parser.setFieldsBoost(queryDef.multi_field);
		}
		if (queryDef.default_operator != null)
			parser.setDefaultOperator(queryDef.default_operator);
		if (queryDef.allow_leading_wildcard != null)
			parser.setAllowLeadingWildcard(queryDef.allow_leading_wildcard);
		if (queryDef.phrase_slop != null)
			parser.setPhraseSlop(queryDef.phrase_slop);
		if (queryDef.enable_position_increments != null)
			parser.setEnablePositionIncrements(queryDef.enable_position_increments);

		// Deal wih query string
		final String qs;
		// Check if we have to escape some characters
		if (queryDef.escape_query != null && queryDef.escape_query) {
			if (queryDef.escaped_chars != null && queryDef.escaped_chars.length > 0)
				qs = StringUtils.escape_chars(queryDef.query_string, queryDef.escaped_chars);
			else
				qs = QueryParser.escape(queryDef.query_string);
		} else
			qs = queryDef.query_string;

		// Parse the query
		Query query = parser.parse(qs, queryDef.default_field);

		if (queryDef.auto_generate_phrase_query != null && queryDef.auto_generate_phrase_query && qs != null
				&& qs.length() > 0 && qs.indexOf('"') == -1) {
			Query phraseQuery = parser.parse('"' + qs + '"', queryDef.default_field);
			query = new BooleanQuery.Builder().add(query, BooleanClause.Occur.SHOULD)
					.add(phraseQuery, BooleanClause.Occur.SHOULD).build();
		}

		// Overload query with facet filters
		if (queryDef.facet_filters != null)
			query = buildFacetFiltersQuery(analyzer.getContext().facetsConfig, queryDef.facet_filters, query);

		return query;
	}

	final static ResultDefinition search(IndexSearcher indexSearcher, QueryDefinition queryDef,
			UpdatableAnalyzer analyzer)
			throws ServerException, IOException, QueryNodeException, InterruptedException, ParseException {

		Query query = getLuceneQuery(queryDef, analyzer);

		final IndexReader indexReader = indexSearcher.getIndexReader();
		final TimeTracker timeTracker = new TimeTracker();
		final TopDocs topDocs;
		final Facets facets;
		final AnalyzerContext analyzerContext = analyzer.getContext();

		final Sort sort = queryDef.sorts == null ? null : buildSort(analyzerContext.fields, queryDef.sorts);

		if (queryDef.facets != null && queryDef.facets.size() > 0) {
			FacetsCollector facetsCollector = new FacetsCollector();
			SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(indexReader);
			if (sort != null) {
				boolean bNeedScore = sort.needsScores();
				topDocs = FacetsCollector
						.search(indexSearcher, query, queryDef.getEnd(), sort, bNeedScore, bNeedScore, facetsCollector);
			} else
				topDocs = FacetsCollector.search(indexSearcher, query, queryDef.getEnd(), facetsCollector);
			timeTracker.next("search_query");
			facets = new SortedSetDocValuesFacetCounts(state, facetsCollector);
			timeTracker.next("facet_count");
		} else {
			if (sort != null) {
				boolean bNeedScore = sort.needsScores();
				topDocs = indexSearcher.search(query, queryDef.getEnd(), sort, bNeedScore, bNeedScore);
			} else
				topDocs = indexSearcher.search(query, queryDef.getEnd());
			timeTracker.next("search_query");
			facets = null;
		}

		Map<String, String[]> postingsHighlightersMap = null;
		if (queryDef.postings_highlighter != null) {
			postingsHighlightersMap = new LinkedHashMap<String, String[]>();
			for (Map.Entry<String, Integer> entry : queryDef.postings_highlighter.entrySet()) {
				String field = entry.getKey();
				PostingsHighlighter highlighter = new PostingsHighlighter(entry.getValue());
				String highlights[] = highlighter.highlight(field, query, indexSearcher, topDocs);
				if (highlights != null) {
					postingsHighlightersMap.put(field, highlights);
				}
			}
			timeTracker.next("postings_highlighters");
		}

		return new ResultDefinition(analyzerContext.fields, timeTracker, indexSearcher, topDocs, queryDef, facets,
				postingsHighlightersMap, query);

	}

	final static MoreLikeThis getMoreLikeThis(MltQueryDefinition mltQueryDef, IndexReader reader,
			UpdatableAnalyzer analyzer) throws IOException {

		final MoreLikeThis mlt = new MoreLikeThis(reader);
		if (mltQueryDef.boost != null)
			mlt.setBoost(mltQueryDef.boost);
		if (mltQueryDef.boost_factor != null)
			mlt.setBoostFactor(mltQueryDef.boost_factor);
		if (mltQueryDef.fieldnames != null)
			mlt.setFieldNames(mltQueryDef.fieldnames);
		if (mltQueryDef.max_doc_freq != null)
			mlt.setMaxDocFreq(mltQueryDef.max_doc_freq);
		if (mltQueryDef.max_doc_freq_pct != null)
			mlt.setMaxDocFreqPct(mltQueryDef.max_doc_freq_pct);
		if (mltQueryDef.max_num_tokens_parsed != null)
			mlt.setMaxNumTokensParsed(mltQueryDef.max_num_tokens_parsed);
		if (mltQueryDef.max_query_terms != null)
			mlt.setMaxQueryTerms(mltQueryDef.max_query_terms);
		if (mltQueryDef.max_word_len != null)
			mlt.setMaxWordLen(mltQueryDef.max_word_len);
		if (mltQueryDef.min_doc_freq != null)
			mlt.setMinDocFreq(mltQueryDef.min_doc_freq);
		if (mltQueryDef.min_term_freq != null)
			mlt.setMinTermFreq(mltQueryDef.min_term_freq);
		if (mltQueryDef.min_word_len != null)
			mlt.setMinWordLen(mltQueryDef.min_word_len);
		if (mltQueryDef.stop_words != null)
			mlt.setStopWords(mltQueryDef.stop_words);
		mlt.setAnalyzer(analyzer);
		return mlt;
	}
}
