/**
 * Copyright 2015 Emmanuel Keller / QWAZR
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.search.index;

import com.qwazr.search.SearchServer;
import com.qwazr.utils.IOUtils;
import com.qwazr.utils.TimeTracker;
import com.qwazr.utils.json.JsonMapper;
import com.qwazr.utils.server.ServerException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.lucene.index.*;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

final public class IndexInstance implements Closeable {

	private static final Logger logger = LoggerFactory.getLogger(IndexInstance.class);

	private final static String INDEX_DATA = "data";
	private final static String INDEX_BACKUP = "backup";
	private final static String FIELDS_FILE = "fields.json";
	private final static String SETTINGS_FILE = "settings.json";

	private final FileSet fileSet;

	private final SchemaInstance schema;
	private final Directory luceneDirectory;
	private final LiveIndexWriterConfig indexWriterConfig;
	private final SnapshotDeletionPolicy snapshotDeletionPolicy;
	private final IndexWriter indexWriter;
	private final SearcherManager searcherManager;
	private final IndexSettingsDefinition settings;

	private final IndexAnalyzer indexAnalyzer;
	private volatile LinkedHashMap<String, FieldDefinition> fieldMap;

	private IndexInstance(SchemaInstance schema, Directory luceneDirectory, IndexSettingsDefinition settings,
					LinkedHashMap<String, FieldDefinition> fieldMap, FileSet fileSet, IndexWriter indexWriter,
					SearcherManager searcherManager) {
		this.schema = schema;
		this.fileSet = fileSet;
		this.luceneDirectory = luceneDirectory;
		this.fieldMap = fieldMap;
		this.indexWriter = indexWriter;
		this.indexWriterConfig = indexWriter.getConfig();
		this.indexAnalyzer = (IndexAnalyzer) indexWriterConfig.getAnalyzer();
		this.snapshotDeletionPolicy = (SnapshotDeletionPolicy) indexWriterConfig.getIndexDeletionPolicy();
		this.settings = settings;
		this.searcherManager = searcherManager;
	}

	private static class FileSet {

		private final File settingsFile;
		private final File indexDirectory;
		private final File backupDirectory;
		private final File dataDirectory;
		private final File fieldMapFile;

		private FileSet(File indexDirectory) {
			this.indexDirectory = indexDirectory;
			this.backupDirectory = new File(indexDirectory, INDEX_BACKUP);
			this.dataDirectory = new File(indexDirectory, INDEX_DATA);
			this.fieldMapFile = new File(indexDirectory, FIELDS_FILE);
			this.settingsFile = new File(indexDirectory, SETTINGS_FILE);
		}
	}

	/**
	 * @param schema
	 * @param indexDirectory
	 * @return
	 */
	final static IndexInstance newInstance(SchemaInstance schema, File indexDirectory, IndexSettingsDefinition settings)
					throws ServerException, IOException, ReflectiveOperationException, InterruptedException {
		IndexAnalyzer indexAnalyzer = null;
		IndexWriter indexWriter = null;
		Directory luceneDirectory = null;
		try {

			SearchServer.checkDirectoryExists(indexDirectory);
			FileSet fileSet = new FileSet(indexDirectory);

			//Loading the settings
			if (settings == null) {
				settings = fileSet.settingsFile.exists() ?
								JsonMapper.MAPPER.readValue(fileSet.settingsFile, IndexSettingsDefinition.class) :
								IndexSettingsDefinition.EMPTY;
			} else {
				JsonMapper.MAPPER.writeValue(fileSet.settingsFile, settings);
			}

			//Loading the fields
			File fieldMapFile = new File(indexDirectory, FIELDS_FILE);
			LinkedHashMap<String, FieldDefinition> fieldMap = fieldMapFile.exists() ?
							JsonMapper.MAPPER.readValue(fieldMapFile, FieldDefinition.MapStringFieldTypeRef) :
							null;
			indexAnalyzer = new IndexAnalyzer(schema.getFileClassCompilerLoader(), fieldMap);

			// Open and lock the data directory
			luceneDirectory = FSDirectory.open(fileSet.dataDirectory.toPath());

			// Set
			IndexWriterConfig indexWriterConfig = new IndexWriterConfig(indexAnalyzer);
			if (settings != null && settings.similarity_class != null)
				indexWriterConfig.setSimilarity(IndexUtils
								.findSimilarity(schema.getFileClassCompilerLoader(), settings.similarity_class));
			indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
			SnapshotDeletionPolicy snapshotDeletionPolicy = new SnapshotDeletionPolicy(
							indexWriterConfig.getIndexDeletionPolicy());
			indexWriterConfig.setIndexDeletionPolicy(snapshotDeletionPolicy);
			indexWriter = new IndexWriter(luceneDirectory, indexWriterConfig);
			if (indexWriter.hasUncommittedChanges())
				indexWriter.commit();

			// Finally we build the SearchSercherManger
			SearcherManager searcherManager = new SearcherManager(indexWriter, true, null);

			return new IndexInstance(schema, luceneDirectory, settings, fieldMap, fileSet, indexWriter,
							searcherManager);
		} catch (IOException | ServerException | ReflectiveOperationException | InterruptedException e) {
			// We failed in opening the index. We close everything we can
			if (indexAnalyzer != null)
				IOUtils.closeQuietly(indexAnalyzer);
			if (indexWriter != null)
				IOUtils.closeQuietly(indexWriter);
			if (luceneDirectory != null)
				IOUtils.closeQuietly(luceneDirectory);
			throw e;
		}
	}

	public IndexSettingsDefinition getSettings() {
		return settings;
	}

	@Override
	public void close() {
		IOUtils.closeQuietly(searcherManager);
		if (indexWriter.isOpen())
			IOUtils.closeQuietly(indexWriter);
		IOUtils.closeQuietly(luceneDirectory);
	}

	/**
	 * Delete the index. The directory is deleted from the local file system.
	 */
	void delete() {
		close();
		if (fileSet.indexDirectory.exists())
			FileUtils.deleteQuietly(fileSet.indexDirectory);
	}

	private IndexStatus getIndexStatus() throws IOException {
		final IndexSearcher indexSearcher = searcherManager.acquire();
		try {
			return new IndexStatus(indexSearcher.getIndexReader(), settings, fieldMap);
		} finally {
			searcherManager.release(indexSearcher);
		}
	}

	LinkedHashMap<String, FieldDefinition> getFields() {
		return fieldMap;
	}

	IndexStatus getStatus() throws IOException, InterruptedException {
		final Semaphore sem = schema.acquireReadSemaphore();
		try {
			return getIndexStatus();
		} finally {
			if (sem != null)
				sem.release();
		}
	}

	synchronized void setFields(LinkedHashMap<String, FieldDefinition> fields) throws ServerException, IOException {
		indexAnalyzer.update(schema.getFileClassCompilerLoader(), fields);
		JsonMapper.MAPPER.writeValue(fileSet.fieldMapFile, fields);
		fieldMap = fields;
	}

	void deleteField(String field_name) throws IOException, ServerException {
		LinkedHashMap<String, FieldDefinition> fields = (LinkedHashMap<String, FieldDefinition>) fieldMap.clone();
		if (fields.remove(field_name) == null)
			throw new ServerException(Response.Status.NOT_FOUND, "Field not found: " + field_name);
		setFields(fields);
	}

	void setField(String field_name, FieldDefinition field) throws IOException, ServerException {
		LinkedHashMap<String, FieldDefinition> fields = (LinkedHashMap<String, FieldDefinition>) fieldMap.clone();
		fields.put(field_name, field);
		setFields(fields);
	}

	private void nrtCommit() throws IOException, ServerException {
		indexWriter.commit();
		searcherManager.maybeRefresh();
		schema.mayBeRefresh();
	}

	final synchronized BackupStatus backup(Integer keepLastCount) throws IOException, InterruptedException {
		Semaphore sem = schema.acquireReadSemaphore();
		try {
			final IndexCommit commit = snapshotDeletionPolicy.snapshot();
			File backupdir = null;
			try {
				int files_count = 0;
				long bytes_size = 0;
				if (!fileSet.backupDirectory.exists())
					fileSet.backupDirectory.mkdir();
				backupdir = new File(fileSet.backupDirectory, Long.toString(commit.getGeneration()));
				if (!backupdir.exists())
					backupdir.mkdir();
				if (!backupdir.exists())
					throw new IOException("Cannot create the backup directory: " + backupdir);
				for (String fileName : commit.getFileNames()) {
					File sourceFile = new File(fileSet.dataDirectory, fileName);
					File targetFile = new File(backupdir, fileName);
					files_count++;
					bytes_size += sourceFile.length();
					if (targetFile.exists() && targetFile.length() == sourceFile.length()
									&& targetFile.lastModified() == sourceFile.lastModified())
						continue;
					FileUtils.copyFile(sourceFile, targetFile, true);
				}
				purgeBackups(keepLastCount);
				return new BackupStatus(commit.getGeneration(), backupdir.lastModified(), bytes_size, files_count);
			} catch (IOException e) {
				if (backupdir != null)
					FileUtils.deleteQuietly(backupdir);
				throw e;
			} finally {
				snapshotDeletionPolicy.release(commit);
			}
		} finally {
			if (sem != null)
				sem.release();
		}
	}

	private void purgeBackups(Integer keepLastCount) {
		if (keepLastCount == null)
			return;
		if (keepLastCount == 0)
			return;
		List<BackupStatus> backups = backups();
		if (backups.size() <= keepLastCount)
			return;
		for (int i = keepLastCount; i < backups.size(); i++) {
			File backupDir = new File(fileSet.backupDirectory, Long.toString(backups.get(i).generation));
			FileUtils.deleteQuietly(backupDir);
		}
	}

	private List<BackupStatus> backups() {
		List<BackupStatus> list = new ArrayList<BackupStatus>();
		if (!fileSet.backupDirectory.exists())
			return list;
		File[] dirs = fileSet.backupDirectory.listFiles((FileFilter) DirectoryFileFilter.INSTANCE);
		if (dirs == null)
			return list;
		for (File dir : dirs) {
			BackupStatus status = BackupStatus.newBackupStatus(dir);
			if (status != null)
				list.add(status);
		}
		list.sort(new Comparator<BackupStatus>() {
			@Override
			public int compare(BackupStatus o1, BackupStatus o2) {
				return o2.generation.compareTo(o1.generation);
			}
		});
		return list;
	}

	final List<BackupStatus> getBackups() throws InterruptedException {
		final Semaphore sem = schema.acquireReadSemaphore();
		try {
			return backups();
		} finally {
			if (sem != null)
				sem.release();
		}
	}

	final void deleteAll() throws IOException, InterruptedException, ServerException {
		final Semaphore sem = schema.acquireWriteSemaphore();
		try {
			indexWriter.deleteAll();
			nrtCommit();
		} finally {
			if (sem != null)
				sem.release();
		}
	}

	final Object postDocument(Map<String, Object> document) throws IOException, ServerException, InterruptedException {
		if (document == null || document.isEmpty())
			return null;
		final Semaphore sem = schema.acquireWriteSemaphore();
		try {
			schema.checkSize(1);
			Object id = IndexUtils.addNewLuceneDocument(indexAnalyzer.getContext(), document, indexWriter);
			nrtCommit();
			return id;
		} finally {
			if (sem != null)
				sem.release();
		}
	}

	final List<Object> postDocuments(List<Map<String, Object>> documents)
					throws IOException, ServerException, InterruptedException {
		if (documents == null || documents.isEmpty())
			return null;
		final Semaphore sem = schema.acquireWriteSemaphore();
		try {
			schema.checkSize(documents.size());
			final AnalyzerContext context = indexAnalyzer.getContext();
			List<Object> ids = new ArrayList<Object>(documents.size());
			for (Map<String, Object> document : documents)
				ids.add(IndexUtils.addNewLuceneDocument(context, document, indexWriter));
			nrtCommit();
			return ids;
		} finally {
			if (sem != null)
				sem.release();
		}
	}

	final void updateDocumentValues(Map<String, Object> document)
					throws IOException, ServerException, InterruptedException {
		if (document == null || document.isEmpty())
			return;
		final Semaphore sem = schema.acquireWriteSemaphore();
		try {
			IndexUtils.updateDocValues(indexAnalyzer.getContext(), document, indexWriter);
			nrtCommit();
		} finally {
			if (sem != null)
				sem.release();
		}
	}

	final void updateDocumentsValues(List<Map<String, Object>> documents)
					throws IOException, ServerException, InterruptedException {
		if (documents == null || documents.isEmpty())
			return;
		final Semaphore sem = schema.acquireWriteSemaphore();
		try {
			final AnalyzerContext context = indexAnalyzer.getContext();
			for (Map<String, Object> document : documents)
				IndexUtils.updateDocValues(context, document, indexWriter);
			nrtCommit();
		} finally {
			if (sem != null)
				sem.release();
		}
	}

	final ResultDefinition deleteByQuery(QueryDefinition queryDef)
					throws IOException, InterruptedException, QueryNodeException, ParseException, ServerException {
		final Semaphore sem = schema.acquireWriteSemaphore();
		try {
			final Query query = QueryUtils.getLuceneQuery(queryDef, indexAnalyzer);
			int docs = indexWriter.numDocs();
			indexWriter.deleteDocuments(query);
			nrtCommit();
			docs -= indexWriter.numDocs();
			return new ResultDefinition(docs);
		} finally {
			if (sem != null)
				sem.release();
		}
	}

	final ResultDefinition search(QueryDefinition queryDef)
					throws ServerException, IOException, QueryNodeException, InterruptedException, ParseException {
		final Semaphore sem = schema.acquireReadSemaphore();
		try {
			final IndexSearcher indexSearcher = searcherManager.acquire();
			try {
				return QueryUtils.search(indexSearcher, queryDef, indexAnalyzer);
			} finally {
				searcherManager.release(indexSearcher);
			}
		} finally {
			if (sem != null)
				sem.release();
		}
	}

	private MoreLikeThis getMoreLikeThis(MltQueryDefinition mltQueryDef, IndexReader reader) throws IOException {

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
		mlt.setAnalyzer(indexAnalyzer);
		return mlt;
	}

	ResultDefinition mlt(MltQueryDefinition mltQueryDef)
					throws ServerException, IOException, QueryNodeException, InterruptedException {
		final Semaphore sem = schema.acquireReadSemaphore();
		try {
			final IndexSearcher indexSearcher = searcherManager.acquire();
			try {
				final IndexReader indexReader = indexSearcher.getIndexReader();
				final TimeTracker timeTracker = new TimeTracker();
				final Query filterQuery = new StandardQueryParser(indexAnalyzer)
								.parse(mltQueryDef.document_query, mltQueryDef.query_default_field);
				final TopDocs filterTopDocs = indexSearcher.search(filterQuery, 1, Sort.INDEXORDER);
				if (filterTopDocs.totalHits == 0)
					return new ResultDefinition(timeTracker);
				final TopDocs topDocs;
				final Query query = getMoreLikeThis(mltQueryDef, indexReader).like(filterTopDocs.scoreDocs[0].doc);
				topDocs = indexSearcher.search(query, mltQueryDef.getEnd());
				return new ResultDefinition(fieldMap, timeTracker, indexSearcher, topDocs, mltQueryDef, query);
			} finally {
				searcherManager.release(indexSearcher);
			}
		} finally {
			if (sem != null)
				sem.release();
		}
	}

	Directory getLuceneDirectory() {
		return luceneDirectory;
	}

	void fillFields(final Map<String, FieldDefinition> fieldMap) {
		if (fieldMap == null || this.fieldMap == null)
			return;
		this.fieldMap.forEach(new BiConsumer<String, FieldDefinition>() {
			@Override
			public void accept(String name, FieldDefinition fieldDefinition) {
				if (!fieldMap.containsKey(name))
					fieldMap.put(name, fieldDefinition);
			}
		});
	}

}
