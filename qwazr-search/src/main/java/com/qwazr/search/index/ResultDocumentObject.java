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
 **/
package com.qwazr.search.index;

import com.qwazr.search.field.Converters.ValueConverter;
import com.qwazr.utils.server.ServerException;
import org.apache.lucene.search.ScoreDoc;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

public class ResultDocumentObject<T> extends ResultDocumentAbstract {

	final public T record;

	private ResultDocumentObject(final Builder<T> builder) {
		super(builder);
		this.record = builder.record;
	}

	public ResultDocumentObject(final ResultDocumentAbstract resultDocument, final T record) {
		super(resultDocument);
		this.record = record;
	}

	public T getRecord() {
		return record;
	}

	static class Builder<T> extends ResultDocumentBuilder<ResultDocumentObject<T>> {

		private final T record;
		private final Map<String, Field> fieldMap;

		Builder(final int pos, final ScoreDoc scoreDoc, final float maxScore, final Class<T> objectClass,
				Map<String, Field> fieldMap) {
			super(pos, scoreDoc, maxScore);
			try {
				this.record = objectClass.newInstance();
			} catch (ReflectiveOperationException e) {
				throw new ServerException(e);
			}
			this.fieldMap = fieldMap;
		}

		@Override
		final ResultDocumentObject build() {
			return new ResultDocumentObject(this);
		}

		@Override
		void setDocValuesField(final String fieldName, final ValueConverter converter, final int docId) {
			final Field field = fieldMap.get(fieldName);
			if (field == null)
				throw new ServerException("Unknown field " + fieldName + " for class " + record.getClass());
			final Class<?> fieldType = field.getType();
			try {
				if (Collection.class.isAssignableFrom(fieldType))
					converter.fillCollection(record, field, fieldType, docId);
				else
					converter.fillSingleValue(record, field, docId);
			} catch (ReflectiveOperationException e) {
				throw new ServerException(e);
			}
		}

		@Override
		final void setStoredField(final String fieldName, final Object fieldValue) {
			final Field field = fieldMap.get(fieldName);
			if (field == null)
				throw new ServerException("Unknown field " + fieldName + " for class " + record.getClass());

			final Class<?> fieldType = field.getType();
			final Class<?> fieldValueClass = fieldValue.getClass();

			try {
				if (fieldType.isAssignableFrom(fieldValueClass)) {
					field.set(record, fieldValue);
					return;
				}
				Object value = field.get(record);
				if (value == null) {
					if (Collection.class.isAssignableFrom(fieldType)) {
						value = fieldType.newInstance();
						field.set(record, value);
						return;
					}
				} else {
					if (value instanceof Collection) {
						((Collection) value).add(fieldValue);
						return;
					}
				}
				throw new UnsupportedOperationException(
						"The field " + fieldName + " does not support this type: " + fieldValueClass.getSimpleName());
			} catch (IllegalAccessException | InstantiationException e) {
				throw new ServerException(e);
			}
		}
	}

}
