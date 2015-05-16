/**
 * Copyright 2014-2015 Emmanuel Keller / QWAZR
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
package com.qwazr.store;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.TreeSet;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.qwazr.utils.http.HttpResponseEntityException;
import com.qwazr.utils.json.client.JsonClientAbstract;

public class StoreSchemaSingleClient extends JsonClientAbstract implements
		StoreSchemaServiceInterface {

	private final static String PREFIX_PATH = "/store_schema/";

	StoreSchemaSingleClient(String url, int msTimeOut)
			throws URISyntaxException {
		super(url, msTimeOut);
	}

	private URIBuilder getSchemaBaseUrl(String schemaName, Boolean local,
			Integer msTimeout) throws URISyntaxException {
		URIBuilder uriBuilder = getBaseUrl(PREFIX_PATH, schemaName);
		if (local != null)
			uriBuilder.setParameter("local", local.toString());
		if (msTimeout != null)
			uriBuilder.setParameter("timeout", msTimeout.toString());
		return uriBuilder;
	}

	public final static TypeReference<TreeSet<String>> SetStringTypeRef = new TypeReference<TreeSet<String>>() {
	};

	@Override
	public Set<String> getSchemas(Boolean local, Integer msTimeout) {
		try {
			URIBuilder uriBuilder = getSchemaBaseUrl(null, local, msTimeout);
			Request request = Request.Get(uriBuilder.build());
			return execute(request, null, msTimeOut, SetStringTypeRef, 200);
		} catch (HttpResponseEntityException e) {
			throw e.getWebApplicationException();
		} catch (URISyntaxException | IOException e) {
			throw new WebApplicationException(e.getMessage(), e,
					Status.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public StoreSchemaDefinition getSchema(String schemaName, Boolean local,
			Integer msTimeout) {
		try {
			URIBuilder uriBuilder = getSchemaBaseUrl(schemaName, local,
					msTimeout);
			Request request = Request.Get(uriBuilder.build());
			return execute(request, null, msTimeOut,
					StoreSchemaDefinition.class, 200);
		} catch (HttpResponseEntityException e) {
			throw e.getWebApplicationException();
		} catch (URISyntaxException | IOException e) {
			throw new WebApplicationException(e.getMessage(), e,
					Status.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public StoreSchemaDefinition createSchema(String schemaName,
			StoreSchemaDefinition schemaDef, Boolean local, Integer msTimeout) {
		try {
			URIBuilder uriBuilder = getSchemaBaseUrl(schemaName, local,
					msTimeout);
			Request request = Request.Post(uriBuilder.build());
			return execute(request, schemaDef, msTimeOut,
					StoreSchemaDefinition.class, 200);
		} catch (HttpResponseEntityException e) {
			throw e.getWebApplicationException();
		} catch (URISyntaxException | IOException e) {
			throw new WebApplicationException(e.getMessage(), e,
					Status.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public StoreSchemaDefinition deleteSchema(String schemaName, Boolean local,
			Integer msTimeout) {
		try {
			URIBuilder uriBuilder = getSchemaBaseUrl(schemaName, local,
					msTimeout);
			Request request = Request.Delete(uriBuilder.build());
			return execute(request, null, msTimeOut,
					StoreSchemaDefinition.class, 200);
		} catch (HttpResponseEntityException e) {
			throw e.getWebApplicationException();
		} catch (URISyntaxException | IOException e) {
			throw new WebApplicationException(e.getMessage(), e,
					Status.INTERNAL_SERVER_ERROR);
		}
	}

}
