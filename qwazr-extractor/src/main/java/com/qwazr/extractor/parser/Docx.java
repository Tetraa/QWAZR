/**
 * Copyright 2014-2016 Emmanuel Keller / QWAZR
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
package com.qwazr.extractor.parser;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.poi.POIXMLProperties.CoreProperties;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import com.qwazr.extractor.ParserAbstract;
import com.qwazr.extractor.ParserDocument;
import com.qwazr.extractor.ParserField;

public class Docx extends ParserAbstract {

	public static final String[] DEFAULT_MIMETYPES = {
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.template" };

	public static final String[] DEFAULT_EXTENSIONS = { "docx", "dotx" };

	final protected static ParserField TITLE = ParserField.newString("title",
			"The title of the document");

	final protected static ParserField CREATOR = ParserField.newString(
			"creator", "The name of the creator");

	final protected static ParserField CREATION_DATE = ParserField.newDate(
			"creation_date", null);

	final protected static ParserField MODIFICATION_DATE = ParserField.newDate(
			"modification_date", null);

	final protected static ParserField DESCRIPTION = ParserField.newString(
			"description", null);

	final protected static ParserField KEYWORDS = ParserField.newString(
			"keywords", null);

	final protected static ParserField SUBJECT = ParserField.newString(
			"subject", "The subject of the document");

	final protected static ParserField CONTENT = ParserField.newString(
			"content", "The content of the document");

	final protected static ParserField LANG_DETECTION = ParserField.newString(
			"lang_detection", "Detection of the language");

	final protected static ParserField[] FIELDS = { TITLE, CREATOR,
			CREATION_DATE, MODIFICATION_DATE, DESCRIPTION, KEYWORDS, SUBJECT,
			CONTENT, LANG_DETECTION };

	public Docx() {
	}

	@Override
	protected ParserField[] getParameters() {
		return null;
	}

	@Override
	protected ParserField[] getFields() {
		return FIELDS;
	}

	@Override
	protected String[] getDefaultExtensions() {
		return DEFAULT_EXTENSIONS;
	}

	@Override
	protected String[] getDefaultMimeTypes() {
		return DEFAULT_MIMETYPES;
	}

	@Override
	protected void parseContent(InputStream inputStream, String extension,
			String mimeType) throws IOException {

		XWPFDocument document = new XWPFDocument(inputStream);
		XWPFWordExtractor word = null;
		try {
			word = new XWPFWordExtractor(document);

			CoreProperties info = word.getCoreProperties();
			if (info != null) {
				metas.add(TITLE, info.getTitle());
				metas.add(CREATOR, info.getCreator());
				metas.add(CREATION_DATE, info.getCreated());
				metas.add(MODIFICATION_DATE, info.getModified());
				metas.add(SUBJECT, info.getSubject());
				metas.add(DESCRIPTION, info.getDescription());
				metas.add(KEYWORDS, info.getKeywords());
			}
			ParserDocument parserDocument = getNewParserDocument();
			parserDocument.add(CONTENT, word.getText());
			parserDocument.add(LANG_DETECTION,
					languageDetection(CONTENT, 10000));
		} finally {
			IOUtils.closeQuietly(word);
		}
	}
}
