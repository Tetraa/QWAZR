/**
 * Copyright 2015-2016 Emmanuel Keller / QWAZR
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
 */
package com.qwazr.search.query;

import com.qwazr.search.function.AbstractValueSource;
import com.qwazr.search.index.QueryContext;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;

import java.io.IOException;
import java.util.Objects;

public class FunctionQuery extends AbstractQuery {

	final public AbstractValueSource source;

	public FunctionQuery() {
		source = null;
	}

	public FunctionQuery(AbstractValueSource source) {
		this.source = source;
	}

	@Override
	final public org.apache.lucene.queries.function.FunctionQuery getQuery(QueryContext queryContext)
			throws IOException, ParseException, QueryNodeException, ReflectiveOperationException, InterruptedException {
		Objects.requireNonNull(source, "The source property is missing");
		return new org.apache.lucene.queries.function.FunctionQuery(source.getValueSource(queryContext));
	}

	final public static org.apache.lucene.queries.function.FunctionQuery[] getQueries(FunctionQuery[] scoringQueries,
			QueryContext queryContext)
			throws ParseException, IOException, QueryNodeException, ReflectiveOperationException, InterruptedException {
		if (scoringQueries == null)
			return null;
		final org.apache.lucene.queries.function.FunctionQuery[] functionQueries =
				new org.apache.lucene.queries.function.FunctionQuery[scoringQueries.length];
		int i = 0;
		for (FunctionQuery scoringQuery : scoringQueries)
			functionQueries[i++] = scoringQuery.getQuery(queryContext);
		return functionQueries;
	}
}
