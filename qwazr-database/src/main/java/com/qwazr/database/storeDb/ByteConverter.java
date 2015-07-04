/**
 * Copyright 2015 Emmanuel Keller / QWAZR
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package com.qwazr.database.storeDb;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface ByteConverter<T> {

	byte[] toBytes(T value);

	T toValue(byte[] bytes);

	public class IntegerByteConverter implements ByteConverter<Integer> {

		public final static IntegerByteConverter INSTANCE = new IntegerByteConverter();

		@Override
		final public byte[] toBytes(Integer value) {
			return ByteBuffer.allocate(4).putInt(value).array();
		}

		@Override
		final public Integer toValue(byte[] bytes) {
			return ByteBuffer.wrap(bytes).getInt();
		}
	}

	public class LongByteConverter implements ByteConverter<Long> {

		public final static LongByteConverter INSTANCE = new LongByteConverter();

		@Override
		final public byte[] toBytes(Long value) {
			return ByteBuffer.allocate(8).putLong(value).array();
		}

		@Override
		final public Long toValue(byte[] bytes) {
			return ByteBuffer.wrap(bytes).getLong();
		}
	}

	public class DoubleByteConverter implements ByteConverter<Double> {

		public final static DoubleByteConverter INSTANCE = new DoubleByteConverter();

		@Override
		final public byte[] toBytes(Double value) {
			return ByteBuffer.allocate(8).putDouble(value).array();
		}

		@Override
		final public Double toValue(byte[] bytes) {
			return ByteBuffer.wrap(bytes).getDouble();
		}
	}

	public class StringByteConverter implements ByteConverter<String> {

		public final static StringByteConverter INSTANCE = new StringByteConverter();

		@Override
		final public byte[] toBytes(String value) {
			return StandardCharsets.UTF_8.encode(value).array();
		}

		@Override
		final public String toValue(byte[] bytes) {
			return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes)).toString();
		}
	}
}