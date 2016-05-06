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
package com.qwazr.cluster.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.qwazr.cluster.service.ClusterServiceStatusJson.StatusEnum;
import com.qwazr.utils.server.ServerException;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

@JsonInclude(Include.NON_NULL)
public class ClusterStatusJson {

	public final TreeMap<String, ClusterNodeJson> active_nodes;
	public final TreeMap<String, TreeSet<String>> groups;
	public final TreeMap<String, StatusEnum> services;

	public ClusterStatusJson() {
		active_nodes = null;
		groups = null;
		services = null;
	}

	public ClusterStatusJson(final TreeMap<String, ClusterNodeJson> nodesMap,
			final TreeMap<String, TreeSet<String>> groups,
			final TreeMap<String, TreeSet<String>> services) throws ServerException {
		this.active_nodes = nodesMap;
		this.groups = groups;
		this.services = new TreeMap<>();
		if (services != null) {
			services.forEach((service, nodesSet) -> this.services
					.put(service, ClusterServiceStatusJson.findStatus(nodesSet.size())));
		}
	}

}
