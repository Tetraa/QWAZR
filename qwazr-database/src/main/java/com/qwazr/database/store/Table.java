/**
 * Copyright 2015-2016 Emmanuel Keller / QWAZR
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
package com.qwazr.database.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.qwazr.database.model.ColumnDefinition;
import com.qwazr.database.store.CollectorInterface.LongCounter;
import com.qwazr.database.store.keys.*;
import com.qwazr.utils.LockUtils;
import com.qwazr.utils.server.ServerException;
import org.roaringbitmap.IntIterator;
import org.roaringbitmap.RoaringBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Table implements Closeable {

	public static final String ID_COLUMN_NAME = "$id$";

	private static final Logger logger = LoggerFactory.getLogger(Table.class);

	private final LockUtils.ReadWriteLock rwlColumns = new LockUtils.ReadWriteLock();

	final File directory;

	final KeyStore keyStore;

	final ColumnDefsKey columnDefsKey;

	final PrimaryIndexKey primaryIndexKey;

	private static final ExecutorService readExecutor;

	private static final ExecutorService writeExecutor;

	static {
		readExecutor = Executors.newFixedThreadPool(12);
		writeExecutor = Executors.newFixedThreadPool(4);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				readExecutor.shutdown();
				writeExecutor.shutdown();
			}
		});
	}

	public final static TypeReference<Map<String, Integer>> MapStringIntegerTypeRef =
			new TypeReference<Map<String, Integer>>() {
			};

	private static final ByteConverter.JsonTypeByteConverter MapStringIntegerByteConverter =
			new ByteConverter.JsonTypeByteConverter(MapStringIntegerTypeRef);

	Table(File directory) throws IOException {
		this.directory = directory;
		logger.info("Load table: " + directory);
		File dbFile = new File(directory, "storedb");
		keyStore = new KeyStore(dbFile);
		columnDefsKey = new ColumnDefsKey();
		primaryIndexKey = new PrimaryIndexKey();
	}

	void closeNoLock() throws IOException {
		keyStore.close();
	}

	@Override
	public void close() throws IOException {
		closeNoLock();
		Tables.close(directory);
	}

	public void delete() throws IOException {
		if (keyStore.exists())
			keyStore.delete();
	}

	public Map<String, ColumnDefinition> getColumns() throws IOException {
		return rwlColumns.readEx(() -> {
			Map<String, ColumnDefinition.Internal> map = columnDefsKey.getColumns(keyStore);
			Map<String, ColumnDefinition> res = new LinkedHashMap<String, ColumnDefinition>();
			map.forEach((s, columnInternalDefinition) -> res.put(s, new ColumnDefinition(columnInternalDefinition)));
			return res;
		});
	}

	public void addColumn(String columnName, ColumnDefinition columnDefinition) throws IOException {
		rwlColumns.writeEx(() -> {
			// Check if the column already exists
			Map<String, ColumnDefinition.Internal> columns = columnDefsKey.getColumns(keyStore);
			if (columns != null) {
				ColumnDefinition.Internal colDef = columns.get(columnName);
				if (colDef != null)
					throw new DatabaseException("Cannot add an already existing column: " + columnName);
			}

			// Find the next available column id
			BitSet bitset = new BitSet();
			if (columns != null)
				columns.forEach((s, columnInternalDefinition) -> bitset.set(columnInternalDefinition.column_id));
			final int columnId = bitset.nextClearBit(0);

			// Write the new column
			new ColumnDefKey(columnName).setValue(keyStore, new ColumnDefinition.Internal(columnDefinition, columnId));
		});
	}

	final public void removeColumn(final String columnName) throws IOException {
		rwlColumns.writeEx(() -> {
			// Check if the column exists
			ColumnDefKey columnDefKey = new ColumnDefKey(columnName);
			ColumnDefinition.Internal colDef = columnDefKey.getValue(keyStore);
			if (colDef == null)
				throw new DatabaseException("Cannot delete an unknown column: " + columnName);
			// Delete stored values
			new ColumnStoresKey(colDef.column_id).deleteAll(keyStore);
			// Delete any index
			new ColumnIndexesKey(colDef).deleteAll(keyStore);
			// Remove the column definition
			columnDefKey.deleteValue(keyStore);
		});
	}

	private void deleteRow(final int docId) throws IOException {
		rwlColumns.readEx(() -> {
			for (ColumnDefinition.Internal colDef : columnDefsKey.getColumns(keyStore).values()) {
				ColumnStoreKey<?, ?> columnStoreKey = ColumnStoreKey.newInstance(colDef, docId);
				if (colDef.mode == ColumnDefinition.Mode.INDEXED)
					new ColumnIndexesKey(colDef).remove(keyStore, columnStoreKey);
				columnStoreKey.deleteValue(keyStore);
			}
		});
	}

	final public LinkedHashMap<String, Object> getRow(final String key, final Set<String> columnNames)
			throws IOException {
		if (key == null)
			return null;
		final Integer docId = new PrimaryIdsKey(key).getValue(keyStore);
		if (docId == null)
			return null;
		return rwlColumns.readEx(() -> {
			final Map<String, ColumnDefinition.Internal> columns = columnNames == null || columnNames.isEmpty() ?
					columnDefsKey.getColumns(keyStore) :
					getColumns(columnNames);
			return getRowByIdNoLock(docId, columns);
		});
	}

	final public boolean deleteRow(final String key) throws IOException {
		if (key == null)
			return false;
		PrimaryIdsKey primaryIdsKey = new PrimaryIdsKey(key);
		Integer docId = primaryIdsKey.getValue(keyStore);
		if (docId == null)
			return false;
		deleteRow(docId);
		primaryIndexKey.remove(keyStore, docId);
		primaryIdsKey.deleteValue(keyStore);
		return true;
	}

	private boolean upsertRowNoCommit(String key, final Map<String, Object> row) throws IOException {
		if (row == null)
			return false;
		// Check if the primary key is present
		if (key == null) {
			Object o = row.get(ID_COLUMN_NAME);
			if (o != null)
				key = o.toString();
			if (key == null)
				throw new DatabaseException("The primary key is missing (" + ID_COLUMN_NAME + ")");
		}
		PrimaryIdsKey primaryIdsKey = new PrimaryIdsKey(key);
		Integer tempId = primaryIdsKey.getValue(keyStore);
		final AtomicBoolean newDocIdUsed;
		if (tempId == null) {
			tempId = primaryIndexKey.nextDocId(keyStore, key);
			newDocIdUsed = new AtomicBoolean(false);
		} else
			newDocIdUsed = null;
		final int docId = tempId;
		try {
			return rwlColumns.readEx(() -> {
				final Map<String, ColumnDefinition.Internal> columns = columnDefsKey.getColumns(keyStore);
				for (Map.Entry<String, Object> entry : row.entrySet()) {
					String colName = entry.getKey();
					if (ID_COLUMN_NAME.equals(colName))
						continue;
					final ColumnDefinition.Internal colDef = columns.get(colName);
					if (colDef == null)
						throw new DatabaseException("Unknown column: " + colName);
					final ColumnStoreKey<?, ?> columnStoreKey = ColumnStoreKey.newInstance(colDef, docId);
					final Object valueObject = entry.getValue();
					if (colDef.mode == ColumnDefinition.Mode.INDEXED) {
						final ColumnIndexesKey<?, ?> columnIndexesKey = new ColumnIndexesKey(colDef);
						// TODO optimization : Check if values are identical, if they are we don't have to update anything
						columnIndexesKey.remove(keyStore, columnStoreKey);
						columnIndexesKey.select(keyStore, valueObject, docId);
					}
					columnStoreKey.setObjectValue(keyStore, valueObject);
				}
				primaryIndexKey.select(keyStore, docId);
				if (newDocIdUsed != null)
					newDocIdUsed.set(true);
				return true;
			});
		} finally {
			if (newDocIdUsed != null && !newDocIdUsed.get())
				primaryIndexKey.remove(keyStore, docId);
		}
	}

	final public boolean upsertRow(final String key, final Map<String, Object> row) throws IOException {
		return upsertRowNoCommit(key, row);
	}

	final public int upsertRows(final Collection<Map<String, Object>> rows) throws IOException {
		int count = 0;
		for (Map<String, Object> row : rows)
			if (upsertRowNoCommit(null, row))
				count++;
		return count;
	}

	final public int getSize() throws IOException {
		return primaryIndexKey.getValue(keyStore).getCardinality();
	}

	private Map<String, ColumnDefinition.Internal> getColumns(final Set<String> columnNames) throws IOException {
		if (columnNames == null || columnNames.isEmpty())
			return Collections.emptyMap();
		final Map<String, ColumnDefinition.Internal> columnDefs = columnDefsKey.getColumns(keyStore);
		final Map<String, ColumnDefinition.Internal> columns = new LinkedHashMap<>();
		for (String columnName : columnNames) {
			ColumnDefinition.Internal columnDef = columnDefs.get(columnName);
			if (columnDef == null) {
				if (columnName.equals(Table.ID_COLUMN_NAME))
					columnDef = ColumnDefinition.Internal.PRIMARYKEY_COLUMN;
				else
					throw new DatabaseException("Column not found: " + columnName);
			}
			columns.put(columnName, columnDef);
		}
		return columns;
	}

	private LinkedHashMap<String, Object> getRowByIdNoLock(final Integer docId,
			final Map<String, ColumnDefinition.Internal> columns) throws IOException {
		if (docId == null)
			return null;
		final LinkedHashMap<String, Object> row = new LinkedHashMap<>();
		columns.forEach((name, internal) -> {
			try {
				final Object value = internal == ColumnDefinition.Internal.PRIMARYKEY_COLUMN ?
						primaryIndexKey.getKey(keyStore, docId) :
						ColumnStoreKey.newInstance(internal, docId).getValue(keyStore);
				row.put(name, value);
			} catch (IOException e) {
				throw new ServerException(e);
			}
		});
		return row;
	}

	final public void getRows(final RoaringBitmap bitmap, final Set<String> columnNames, final long start,
			final long rows, final List<LinkedHashMap<String, Object>> results) throws IOException {
		if (bitmap == null || bitmap.isEmpty())
			return;
		rwlColumns.readEx(() -> {
			final Map<String, ColumnDefinition.Internal> columns = getColumns(columnNames);
			final IntIterator docIterator = bitmap.getIntIterator();
			long s = start;
			while (s-- > 0 && docIterator.hasNext())
				docIterator.next();
			long r = rows;
			while (r-- > 0 && docIterator.hasNext()) {
				final LinkedHashMap<String, Object> row = getRowByIdNoLock(docIterator.next(), columns);
				if (row != null)
					results.add(row);
			}
		});
	}

	final public void getRows(final Set<String> keys, Set<String> columnNames,
			final List<LinkedHashMap<String, Object>> results) throws IOException {
		if (keys == null || keys.isEmpty())
			return;
		final ArrayList<Integer> ids = new ArrayList<>(keys.size());
		PrimaryIdsKey.fillExistingIds(keyStore, keys, ids);
		if (ids == null || ids.isEmpty())
			return;
		rwlColumns.readEx(() -> {
			final Map<String, ColumnDefinition.Internal> columns = getColumns(columnNames);
			for (Integer id : ids) {
				final LinkedHashMap<String, Object> row = getRowByIdNoLock(id, columns);
				// Id can be null if the document did not exists
				if (row != null)
					results.add(row);
			}
		});
	}

	private ColumnDefinition.Internal getIndexedColumn(final String columnName) throws IOException {
		final ColumnDefinition.Internal columnDef = new ColumnDefKey(columnName).getValue(keyStore);
		if (columnDef == null)
			throw new DatabaseException("Column not found: " + columnName);
		if (columnDef.mode != ColumnDefinition.Mode.INDEXED)
			throw new DatabaseException("The column is not indexed: " + columnName);
		return columnDef;
	}

	final public QueryContext getNewQueryContext() throws IOException {
		return new QueryContext(keyStore, columnDefsKey.getColumns(keyStore));
	}

	final public QueryResult query(final Query query, final Map<String, Map<String, LongCounter>> facets)
			throws IOException {
		return rwlColumns.readEx(() -> {
			// long lastTime = System.currentTimeMillis();
			final QueryContext context = getNewQueryContext();

			// First we search for the document using Bitset
			final RoaringBitmap finalBitmap =
					query != null ? query.execute(context, readExecutor) : primaryIndexKey.getValue(keyStore);
			if (finalBitmap == null || finalBitmap.isEmpty())
				return new QueryResult(context, finalBitmap);

			// Build the collector chain
			CollectorInterface collector = CollectorInterface.build();

			// Collector chain for facets
			final Map<String, Map<Object, LongCounter>> facetsMap;
			if (facets != null) {
				facetsMap = new HashMap<>();
				for (Map.Entry<String, Map<String, LongCounter>> entry : facets.entrySet()) {
					final String facetField = entry.getKey();
					final Map<Object, LongCounter> facetMap = new HashMap<>();
					facetsMap.put(facetField, facetMap);
					collector = context.newFacetCollector(collector, facetField, facetMap);
				}
			} else
				facetsMap = null;

			// Collect the data
			collector.collect(finalBitmap);

			return new QueryResult(context, finalBitmap);
		});
	}

}
