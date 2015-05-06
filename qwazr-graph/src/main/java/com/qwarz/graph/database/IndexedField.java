/**
 * Copyright 2015 OpenSearchServer Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwarz.graph.database;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.roaringbitmap.RoaringBitmap;
import org.xerial.snappy.Snappy;

import com.qwarz.graph.database.CollectorInterface.LongCounter;
import com.qwazr.utils.LockUtils;
import com.qwazr.utils.SerializationUtils;

public class IndexedField extends FieldAbstract {

	private final LockUtils.ReadWriteLock rwl = new LockUtils.ReadWriteLock();

	private final UniqueKey indexedDictionary;

	private final HashMap<Integer, RoaringBitmap> docBitsetsMap;
	private final File docBitsetsFile;
	private boolean docBitsetsMustBeSaved;

	private final HashMap<Integer, byte[]> termVectorMap;
	private final File termVectorFile;
	private boolean termVectorMustBeSaved;

	private final Map<Integer, String> storedInvertedDictionaryMap;

	public IndexedField(String name, long fieldId, File directory,
			UniqueKey indexedDictionary,
			Map<Integer, String> storedInvertedDictionaryMap,
			AtomicBoolean wasExisting) throws FileNotFoundException {
		super(name, fieldId);
		docBitsetsMustBeSaved = false;
		termVectorMustBeSaved = false;
		this.indexedDictionary = indexedDictionary;
		this.storedInvertedDictionaryMap = storedInvertedDictionaryMap;
		docBitsetsFile = new File(directory, "field." + fieldId + ".idx");
		if (docBitsetsFile.exists())
			docBitsetsMap = SerializationUtils.deserialize(docBitsetsFile);
		else
			docBitsetsMap = new HashMap<Integer, RoaringBitmap>();
		termVectorFile = new File(directory, "field." + fieldId + ".tv");
		if (termVectorFile.exists())
			termVectorMap = SerializationUtils.deserialize(termVectorFile);
		else
			termVectorMap = new HashMap<Integer, byte[]>();
	}

	@Override
	public void commit() throws IOException {
		rwl.r.lock();
		try {
			if (termVectorMustBeSaved) {
				SerializationUtils.serialize(docBitsetsMap, docBitsetsFile);
				termVectorMustBeSaved = false;
			}
			if (docBitsetsMustBeSaved) {
				SerializationUtils.serialize(termVectorMap, termVectorFile);
				docBitsetsMustBeSaved = false;
			}
		} finally {
			rwl.r.unlock();
		}
	}

	@Override
	public void delete() {
		if (docBitsetsFile.exists())
			docBitsetsFile.delete();
		if (termVectorFile.exists())
			termVectorFile.delete();
	}

	private void setTermDocNoLock(Integer docId, Integer termId) {
		RoaringBitmap docBitSet = docBitsetsMap.get(termId);
		if (docBitSet == null) {
			docBitSet = new RoaringBitmap();
			docBitsetsMap.put(termId, docBitSet);
			docBitsetsMustBeSaved = true;
		}
		if (!docBitSet.contains(docId)) {
			docBitSet.add(docId);
			docBitsetsMustBeSaved = true;
		}
	}

	private Integer getTermIdOrNew(String term) {
		AtomicBoolean isNewTerm = new AtomicBoolean();
		Integer termId = indexedDictionary.getIdOrNew(term, isNewTerm);
		// Its a new term, we store it in the dictionary
		if (isNewTerm.get())
			storedInvertedDictionaryMap.put(termId, term);
		return termId;
	}

	final static int[] getIntArrayOrNull(byte[] compressedByteArray)
			throws IOException {
		if (compressedByteArray == null)
			return null;
		return Snappy.uncompressIntArray(compressedByteArray);
	}

	private Set<Integer> getTermVectorIdSet(Integer docId) throws IOException {
		Set<Integer> idSet = new HashSet<Integer>();
		int[] idArray = getIntArrayOrNull(termVectorMap.get(docId));
		if (idArray != null)
			for (int id : idArray)
				idSet.add(id);
		return idSet;
	}

	private void putTermVectorIdSet(Integer docId, Set<Integer> termIdSet)
			throws IOException {
		termVectorMustBeSaved = true;
		if (termIdSet.isEmpty()) {
			termVectorMap.remove(docId);
			return;
		}
		int[] idArray = new int[termIdSet.size()];
		int i = 0;
		for (Integer termId : termIdSet)
			idArray[i++] = termId;
		termVectorMap.put(docId, Snappy.compress(idArray));
	}

	private void setTerms(Integer docId, Set<Integer> newTermIdSet)
			throws IOException {
		// Get the previous set (if any)
		Set<Integer> oldTermIdSet = getTermVectorIdSet(docId);

		// Check if the old and the new id set are identical
		boolean isIdentical = oldTermIdSet != null
				&& oldTermIdSet.size() == newTermIdSet.size()
				&& oldTermIdSet.containsAll(newTermIdSet);

		if (!isIdentical)
			putTermVectorIdSet(docId, newTermIdSet);

		// Update the bitmaps
		for (Integer termId : newTermIdSet)
			setTermDocNoLock(docId, termId);
	}

	private void setTerm(Integer docId, Integer termId) throws IOException {
		// Get the previous set (if any)
		Set<Integer> termIdSet = getTermVectorIdSet(docId);
		if (termIdSet == null)
			termIdSet = new HashSet<Integer>();

		// Check if the old and the new id set are identical
		boolean isIdentical = termIdSet != null && termIdSet.size() == 1
				&& termIdSet.contains(termId);

		if (!isIdentical)
			putTermVectorIdSet(docId, termIdSet);

		// Update the bitmap
		setTermDocNoLock(docId, termId);
	}

	@Override
	public void setValues(final Integer docId, Collection<String> values)
			throws IOException {

		if (values == null || values.isEmpty())
			return;

		// Prepare the id of the terms
		final Set<Integer> newTermIdSet = new HashSet<Integer>();
		for (String value : values)
			newTermIdSet.add(getTermIdOrNew(value));

		rwl.w.lock();
		try {
			setTerms(docId, newTermIdSet);
		} finally {
			rwl.w.unlock();
		}
	}

	@Override
	public void setValue(final Integer docId, String value) throws IOException {
		if (value == null)
			return;
		// Get the term ID
		final Integer termId = getTermIdOrNew(value);
		rwl.w.lock();
		try {
			setTerm(docId, termId);
		} finally {
			rwl.w.unlock();
		}
	}

	public int[] getTerms(Integer docId) throws IOException {
		rwl.r.lock();
		try {
			return getIntArrayOrNull(termVectorMap.get(docId));
		} finally {
			rwl.r.unlock();
		}
	}

	@Override
	public List<String> getValues(Integer docId) throws IOException {
		rwl.r.lock();
		try {
			int[] termIdArray = getIntArrayOrNull(termVectorMap.get(docId));
			if (termIdArray == null || termIdArray.length == 0)
				return null;
			List<String> list = new ArrayList<String>(termIdArray.length);
			for (int termId : termIdArray)
				list.add(storedInvertedDictionaryMap.get(termId));
			return list;
		} finally {
			rwl.r.unlock();
		}
	}

	private RoaringBitmap getDocBitSetNoLock(String term) {
		Integer termId = indexedDictionary.getExistingId(term);
		if (termId == null)
			return null;
		return docBitsetsMap.get(termId);
	}

	RoaringBitmap getTermBitSetOr(Set<String> terms) {
		rwl.r.lock();
		try {
			RoaringBitmap finalBitMap = null;
			for (String term : terms) {
				RoaringBitmap bitMap = getDocBitSetNoLock(term);
				if (bitMap == null)
					continue;
				if (finalBitMap == null)
					finalBitMap = bitMap;
				else
					finalBitMap.or(bitMap);
			}
			return finalBitMap;
		} finally {
			rwl.r.unlock();
		}
	}

	RoaringBitmap getDocBitSetAnd(Set<String> terms) {
		rwl.r.lock();
		try {
			RoaringBitmap finalBitMap = null;
			for (String term : terms) {
				RoaringBitmap bitMap = getDocBitSetNoLock(term);
				if (bitMap == null)
					continue;
				if (finalBitMap == null)
					finalBitMap = bitMap;
				else
					finalBitMap.and(bitMap);
			}
			return finalBitMap;
		} finally {
			rwl.r.unlock();
		}
	}

	@Override
	public void deleteDocument(Integer docId) throws IOException {
		rwl.r.lock();
		try {
			int[] termIdArray = getIntArrayOrNull(termVectorMap.remove(docId));
			if (termIdArray == null)
				return;
			termVectorMustBeSaved = true;
			for (int termId : termIdArray) {
				RoaringBitmap bitSet = docBitsetsMap.get(termId);
				if (bitSet != null && bitSet.contains(docId)) {
					bitSet.remove(docId);
					docBitsetsMustBeSaved = true;
				}
			}
		} finally {
			rwl.r.unlock();
		}
	}

	public CollectorInterface newFacetCollector(CollectorInterface collector,
			Map<Integer, LongCounter> termCounter) {
		rwl.r.lock();
		try {
			return collector.facets(termVectorMap, termCounter);
		} finally {
			rwl.r.unlock();
		}
	}

	public void resolveFacetsIds(Map<Integer, LongCounter> termIdMap,
			Map<String, LongCounter> termMap) {
		if (termIdMap == null)
			return;
		rwl.r.lock();
		try {
			for (Map.Entry<Integer, LongCounter> entry : termIdMap.entrySet()) {
				String term = storedInvertedDictionaryMap.get(entry.getKey());
				termMap.put(term, entry.getValue());
			}
		} finally {
			rwl.r.unlock();
		}
	}

}