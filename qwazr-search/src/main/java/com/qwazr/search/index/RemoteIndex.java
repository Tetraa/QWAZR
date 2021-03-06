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
package com.qwazr.search.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qwazr.utils.server.RemoteService;

import java.net.URISyntaxException;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RemoteIndex extends RemoteService {

	final public String schema;
	final public String index;

	public RemoteIndex() {
		schema = null;
		index = null;
	}

	public RemoteIndex(final RemoteService.Builder builder, final String schema, final String index) {
		super(builder);
		this.schema = schema;
		this.index = index;
	}

	/**
	 * Build an array of RemoteIndex using an array of URL.
	 * The form of the URL should be:
	 * {protocol}://{username:password@}{host}:{port}/indexes/{schema}/{index}?timeout={timeout}
	 *
	 * @param remoteIndexUrl
	 * @return an array of RemoteIndex
	 */
	public static RemoteIndex[] build(String... remoteIndexUrl) throws URISyntaxException {

		if (remoteIndexUrl == null || remoteIndexUrl.length == 0)
			return null;

		List<RemoteService.Builder> builders = RemoteService.builders(remoteIndexUrl);
		if (builders == null)
			return null;

		final RemoteIndex[] remotes = new RemoteIndex[builders.size()];
		int i = 0;
		for (RemoteService.Builder builder : builders) {

			final String schema = builder.getPathSegment(1);
			final String index = builder.getPathSegment(2);

			if (schema == null || index == null)
				throw new URISyntaxException(remoteIndexUrl[i],
						"The URL form should be: /" + IndexServiceInterface.PATH + "/{shema}/{index}?"
								+ TIMEOUT_PARAMETER + "={timeout}");

			builder.setPath(IndexServiceInterface.PATH);
			remotes[i++] = new RemoteIndex(builder, schema, index);

		}
		return remotes;
	}

}
