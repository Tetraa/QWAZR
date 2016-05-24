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
package com.qwazr.database.store.keys;

import com.qwazr.database.store.ByteConverter;
import com.qwazr.database.store.KeyStore;
import com.qwazr.utils.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class KeyAbstract<T, V> implements KeyInterface<T, V> {

	private final KeyEnum keyType;

	protected final ByteConverter<T, V> byteConverter;

	private byte[] keyBytes;

	protected KeyAbstract(KeyEnum keyType, ByteConverter<T, V> byteConverter) {
		this.keyType = keyType;
		this.byteConverter = byteConverter;
		this.keyBytes = null;
	}

	@Override
	public void buildKey(final DataOutputStream output) throws IOException {
		output.writeChar(keyType.id);
	}

	final public synchronized byte[] getCachedKey() throws IOException {
		if (keyBytes != null)
			return keyBytes;
		try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			try (final DataOutputStream output = new DataOutputStream(baos)) {
				buildKey(output);
				output.flush();
				keyBytes = baos.toByteArray();
				return keyBytes;
			}
		}
	}

	@Override
	final public V getValue(final KeyStore store) throws IOException {
		byte[] bytes = store.get(getCachedKey());
		if (bytes == null)
			return null;
		return byteConverter.toValue(bytes);
	}

	@Override
	final public void setValue(final KeyStore store, final T value) throws IOException {
		store.put(getCachedKey(), byteConverter.toBytes(value));
	}

	@Override
	final public void deleteValue(final KeyStore store) throws IOException {
		store.delete(getCachedKey());
	}

}