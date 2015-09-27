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
 **/
package com.qwazr.database.store.keys;

import com.qwazr.database.store.ByteConverter;
import com.qwazr.database.store.KeyStore;
import com.qwazr.utils.CharsetUtils;
import com.qwazr.utils.IOUtils;
import org.roaringbitmap.RoaringBitmap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Set;

public abstract class KeyAbstract<T> implements KeyInterface<T> {

    private final KeyEnum keyType;

    private final ByteConverter<T> byteConverter;

    private byte[] keyBytes;

    protected KeyAbstract(KeyEnum keyType, ByteConverter<T> byteConverter) {
	this.keyType = keyType;
	this.byteConverter = byteConverter;
	this.keyBytes = null;
    }

    @Override
    public void buildKey(final ObjectOutputStream os) throws IOException {
	os.writeInt(keyType.id);
    }

    final public synchronized byte[] getCachedKey() throws IOException {
	if (keyBytes != null)
	    return keyBytes;
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	try {
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    try {
		buildKey(oos);
		oos.close();
		oos = null;
		return baos.toByteArray();
	    } finally {
		if (oos != null)
		    IOUtils.close(oos);
	    }
	} finally {
	    if (baos != null)
		IOUtils.close(baos);
	}
    }

    @Override
    final public T getValue(final KeyStore store) throws IOException {
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