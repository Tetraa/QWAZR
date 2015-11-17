/**
 * Copyright 2014-2015 Emmanuel Keller / QWAZR
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
package com.qwazr.crawler.web.service;

import com.qwazr.crawler.web.WebCrawlerServer;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

@RolesAllowed(WebCrawlerServer.SERVICE_NAME_WEBCRAWLER)
@Path("/crawler/web")
public interface WebCrawlerServiceInterface {

	@GET
	@Path("/sessions")
	@Produces(MediaType.APPLICATION_JSON)
	public TreeMap<String, WebCrawlStatus> getSessions(@QueryParam("local") Boolean local);

	@GET
	@Path("/sessions/{session_name}")
	@Produces(MediaType.APPLICATION_JSON)
	public WebCrawlStatus getSession(@PathParam("session_name") String session_name,
					@QueryParam("local") Boolean local);

	@DELETE
	@Path("/sessions/{session_name}")
	public Response abortSession(@PathParam("session_name") String session_name,
					@QueryParam("reason") String aborting_reason, @QueryParam("local") Boolean local);

	@POST
	@PUT
	@Path("/sessions/{session_name}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public WebCrawlStatus runSession(@PathParam("session_name") String session_name,
					WebCrawlDefinition crawlDefinition);

	public WebCrawlStatus runSession(String session_name, String jsonCrawlDefinition) throws IOException;

}
