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

import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import com.qwazr.utils.server.RestApplication;

public interface StoreSchemaServiceInterface {

	@GET
	@Path("/")
	@Produces(RestApplication.APPLICATION_JSON_UTF8)
	Set<String> getSchemas(@QueryParam("local") Boolean local,
			@QueryParam("timeout") Integer msTimeout);

	@GET
	@Path("/{schema_name}")
	@Produces(RestApplication.APPLICATION_JSON_UTF8)
	public StoreSchemaDefinition getSchema(
			@PathParam("schema_name") String schemaName,
			@QueryParam("local") Boolean local,
			@QueryParam("timeout") Integer msTimeout);

	@POST
	@Path("/{schema_name}")
	@Produces(RestApplication.APPLICATION_JSON_UTF8)
	public StoreSchemaDefinition createSchema(
			@PathParam("schema_name") String schemaName,
			StoreSchemaDefinition schemaDef,
			@QueryParam("local") Boolean local,
			@QueryParam("timeout") Integer msTimeout);

	@DELETE
	@Path("/{schema_name}")
	@Produces(RestApplication.APPLICATION_JSON_UTF8)
	public StoreSchemaDefinition deleteSchema(
			@PathParam("schema_name") String schemaName,
			@QueryParam("local") Boolean local,
			@QueryParam("timeout") Integer msTimeout);

}
