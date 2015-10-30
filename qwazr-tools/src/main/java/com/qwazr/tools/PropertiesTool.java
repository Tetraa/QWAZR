/**
 * Copyright 2014-2015 Emmanuel Keller / QWAZR
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
package com.qwazr.tools;

import com.qwazr.utils.IOUtils;

import java.io.*;
import java.util.Properties;

public class PropertiesTool extends AbstractTool {

	@Override
	public void load(File parentDir) {

	}

	@Override
	public void unload() {

	}

	/**
	 * Load the properties from a file in TEXT format.
	 *
	 * @param path the path to the property file
	 * @return a new Properties instance
	 * @throws IOException if any I/O error occurs
	 */
	public Properties loadFromText(String path) throws IOException {
		Properties properties = new Properties();
		FileInputStream fis = new FileInputStream(path);
		try {
			properties.load(fis);
		} finally {
			IOUtils.closeQuietly(fis);
		}
		return properties;
	}

	/**
	 * Load the properties from a file in XML format.
	 *
	 * @param path the path to the property file
	 * @return a new Properties instance
	 * @throws IOException if any I/O error occurs
	 */
	public Properties loadFromXML(String path) throws IOException {
		Properties properties = new Properties();
		FileInputStream fis = new FileInputStream(path);
		try {
			properties.loadFromXML(fis);
		} finally {
			IOUtils.closeQuietly(fis);
		}
		return properties;
	}

	/**
	 * Store the properties to a file in TEXT format.
	 *
	 * @param properties the properties to store
	 * @param path       the path to the destination file
	 * @param comment    an optional comment
	 * @throws IOException if any I/O error occurs
	 */
	public void storeToText(Properties properties, String path, String comment) throws IOException {
		FileWriter fw = new FileWriter(path);
		try {
			properties.store(fw, comment);
		} finally {
			IOUtils.closeQuietly(fw);
		}
	}

	/**
	 * Stores the properties to a file in XML format.
	 *
	 * @param properties the properties to store
	 * @param path       the path to the destination file
	 * @param comment    an optional comment
	 * @param encoding   The charset to use
	 * @throws IOException if any I/O error occurs
	 */
	public void storeToXML(Properties properties, String path, String comment, String encoding) throws IOException {
		FileOutputStream fos = new FileOutputStream(path);
		try {
			properties.storeToXML(fos, comment, encoding);
		} finally {
			IOUtils.closeQuietly(fos);
		}
	}
}