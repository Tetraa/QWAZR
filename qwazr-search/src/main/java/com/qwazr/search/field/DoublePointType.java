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
package com.qwazr.search.field;

import com.qwazr.search.index.BytesRefUtils;
import com.qwazr.search.index.FieldConsumer;
import com.qwazr.search.index.QueryDefinition;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.search.SortField;
import org.apache.lucene.util.BytesRef;

class DoublePointType extends StorableFieldType {

	DoublePointType(final String fieldName, final FieldDefinition fieldDef) {
		super(fieldName, fieldDef);
	}

	@Override
	final public void fillValue(final Object value, final FieldConsumer consumer) {
		double doubleValue =
				value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
		consumer.accept(new DoublePoint(fieldName, doubleValue));
		if (store == store.YES)
			consumer.accept(new StoredField(fieldName, doubleValue));
	}

	@Override
	public final SortField getSortField(final QueryDefinition.SortEnum sortEnum) {
		final SortField sortField = new SortField(fieldName, SortField.Type.DOUBLE, SortUtils.sortReverse(sortEnum));
		SortUtils.sortDoubleMissingValue(sortEnum, sortField);
		return sortField;
	}

	@Override
	public final Object toTerm(final BytesRef bytesRef) {
		if (bytesRef == null || bytesRef.bytes == null)
			return null;
		return DoublePoint.decodeDimension(bytesRef.bytes, 0);
	}

}
