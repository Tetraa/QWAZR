/**
 * Copyright 2014-2016 Emmanuel Keller / QWAZR
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
package com.qwazr.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedHashSet;

public class IOUtils extends org.apache.commons.io.IOUtils {

	private final static Logger logger = LoggerFactory.getLogger(IOUtils.class);

	public static final void close(final AutoCloseable autoCloseable) {
		if (autoCloseable == null)
			return;
		try {
			autoCloseable.close();
		} catch (Exception e) {
			if (logger.isWarnEnabled())
				logger.warn("Close failure on " + autoCloseable, e);
		}
	}

	public static final void close(final AutoCloseable... autoCloseables) {
		if (autoCloseables == null)
			return;
		for (AutoCloseable autoCloseable : autoCloseables)
			close(autoCloseable);
	}

	public static final void close(final Collection<? extends AutoCloseable> autoCloseables) {
		if (autoCloseables == null)
			return;
		for (AutoCloseable autoCloseable : autoCloseables)
			close(autoCloseable);
	}

	public static final int copy(InputStream inputStream, File destFile) throws IOException {
		FileOutputStream fos = new FileOutputStream(destFile);
		try {
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			try {
				return copy(inputStream, fos);
			} finally {
				close(bos);
			}
		} finally {
			close(fos);
		}
	}

	public static final StringBuilder copy(InputStream inputStream, StringBuilder sb, String charsetName,
			boolean bCloseInputStream) throws IOException {
		if (inputStream == null)
			return sb;
		if (sb == null)
			sb = new StringBuilder();
		Charset charset = Charset.forName(charsetName);
		byte[] buffer = new byte[16384];
		int length;
		while ((length = inputStream.read(buffer)) != -1)
			sb.append(new String(buffer, 0, length, charset));
		if (bCloseInputStream)
			inputStream.close();
		return sb;
	}

	public static final void appendLines(File file, String... lines) throws IOException {
		FileWriter fw = null;
		PrintWriter pw = null;
		try {
			fw = new FileWriter(file, true);
			pw = new PrintWriter(fw);
			for (String line : lines)
				pw.println(line);
		} finally {
			close(fw, pw);
		}
	}

	public interface CloseableContext extends Closeable {

		<T extends AutoCloseable> T add(T autoCloseable);

		void close(AutoCloseable autoCloseable);
	}

	public static class CloseableList implements CloseableContext {

		private final LinkedHashSet<AutoCloseable> autoCloseables;

		public CloseableList() {
			autoCloseables = new LinkedHashSet<AutoCloseable>();
		}

		@Override
		public <T extends AutoCloseable> T add(T autoCloseable) {
			synchronized (autoCloseables) {
				autoCloseables.add(autoCloseable);
				return autoCloseable;
			}
		}

		@Override
		public void close(AutoCloseable autoCloseable) {
			IOUtils.close(autoCloseable);
			synchronized (autoCloseables) {
				autoCloseables.remove(autoCloseable);
			}
		}

		@Override
		public void close() {
			synchronized (autoCloseables) {
				IOUtils.close(autoCloseables);
				autoCloseables.clear();
			}
		}

	}

	/**
	 * Extract the content of a file to a string
	 *
	 * @param file the file
	 * @return the content of the file as a string
	 * @throws IOException if any I/O error occured
	 */
	public static String readFileAsString(File file) throws IOException {
		FileReader reader = new FileReader(file);
		try {
			return toString(reader);
		} finally {
			closeQuietly(reader);
		}
	}

	/**
	 * Write the string to a file
	 *
	 * @param content the text to write
	 * @param file    the destination file
	 * @throws IOException if any I/O error occured
	 */
	public static void writeStringAsFile(String content, File file) throws IOException {
		FileWriter writer = new FileWriter(file);
		try {
			writer.write(content);
		} finally {
			closeQuietly(writer);
		}
	}

}
