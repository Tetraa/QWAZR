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

package com.qwazr.utils.http;

import com.qwazr.utils.CharsetUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class StringHttpResponseHandler extends HttpResponseHandler<String> {

	public StringHttpResponseHandler(ContentType expectedContentType, int... expectedCodes) {
		super(expectedContentType, expectedCodes);
	}

	public String handleResponse(HttpResponse response) throws IOException {
		super.handleResponse(response);
		return EntityUtils.toString(httpEntity, CharsetUtils.CharsetUTF8);
	}
}
