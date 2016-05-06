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
package com.qwazr.cluster.manager;

import com.datastax.driver.core.utils.UUIDs;
import com.qwazr.cluster.service.*;
import com.qwazr.utils.AnnotationsUtils;
import com.qwazr.utils.ArrayUtils;
import com.qwazr.utils.DatagramUtils;
import com.qwazr.utils.StringUtils;
import com.qwazr.utils.server.*;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class ClusterManager implements Consumer<DatagramPacket> {

	public final static String SERVICE_NAME_CLUSTER = "cluster";

	private static final Logger logger = LoggerFactory.getLogger(ClusterManager.class);

	public static ClusterManager INSTANCE = null;

	public synchronized static Class<? extends ClusterServiceInterface> load(final ExecutorService executor,
			final UdpServerThread udpServer, final String myAddress, final Collection<String> myGroups)
			throws IOException {
		if (INSTANCE != null)
			throw new IOException("Already loaded");
		try {
			INSTANCE = new ClusterManager(executor, udpServer, myAddress, myGroups);
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					try {
						INSTANCE.leaveCluster();
					} catch (IOException e) {
						logger.warn(e.getMessage(), e);
					}
				}
			});
			return ClusterServiceImpl.class;
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}

	private ClusterNodeMap clusterNodeMap;

	public final ClusterNodeAddress me;

	public final ExecutorService executor;

	private final Set<String> myServices;

	private final Set<String> myGroups;

	private final UdpServerThread udpServer;

	private final UUID nodeLiveId;

	private ClusterManager(final ExecutorService executor, final UdpServerThread udpServer, final String publicAddress,
			final Collection<String> myGroups) throws IOException, URISyntaxException {

		this.udpServer = udpServer;
		this.nodeLiveId = UUIDs.timeBased();
		this.executor = executor;

		me = new ClusterNodeAddress(publicAddress);

		if (logger.isInfoEnabled())
			logger.info("Server: " + me.httpAddressKey + " Groups: " + ArrayUtils.prettyPrint(myGroups));
		this.myGroups = myGroups != null ? new HashSet<>(myGroups) : null;
		this.myServices = new HashSet<>();

		clusterNodeMap = new ClusterNodeMap();
		clusterNodeMap.register(me.httpAddressKey, nodeLiveId);

		// Load the configuration
		String nodes_env = System.getenv("QWAZR_NODES");
		if (nodes_env == null)
			nodes_env = System.getenv("QWAZR_MASTERS");

		if (nodes_env != null)
			for (String node : StringUtils.split(nodes_env, ','))
				clusterNodeMap.register(node, null);

		if (this.udpServer != null)
			this.udpServer.register(this);
	}

	public boolean isGroup(String group) {
		if (group == null)
			return true;
		if (myGroups == null)
			return true;
		if (group.isEmpty())
			return true;
		return myGroups.contains(group);
	}

	public boolean isLeader(String service, String group) throws ServerException {
		TreeSet<String> nodes = clusterNodeMap.getGroupService(service, group);
		if (nodes == null || nodes.isEmpty())
			return false;
		return me.httpAddressKey.equals(nodes.first());
	}

	final public ClusterStatusJson getStatus() {
		final Map<String, ClusterNode> nodesMap = clusterNodeMap.getNodesMap();
		final TreeMap<String, ClusterNodeJson> nodesJsonMap = new TreeMap<>();
		if (nodesMap != null)
			nodesMap.forEach((address, clusterNode) -> nodesJsonMap.put(address,
					new ClusterNodeJson(clusterNode.address.httpAddressKey, clusterNode.nodeLiveId, clusterNode.groups,
							clusterNode.services)));
		return new ClusterStatusJson(nodesJsonMap, clusterNodeMap.getGroups(),
				clusterNodeMap.getServices());
	}

	final public Set<String> getNodes() {
		final Map<String, ClusterNode> nodesMap = clusterNodeMap.getNodesMap();
		return nodesMap == null ? Collections.emptySet() : nodesMap.keySet();
	}

	final public TreeMap<String, ClusterServiceStatusJson.StatusEnum> getServicesStatus(final String group) {
		final TreeMap<String, ClusterServiceStatusJson.StatusEnum> servicesStatus = new TreeMap();
		final Set<String> services = clusterNodeMap.getServices().keySet();
		if (services == null || services.isEmpty())
			return servicesStatus;
		services.forEach(service -> {
			final TreeSet<String> nodes = getNodesByGroupByService(group, service);
			if (nodes != null && !nodes.isEmpty())
				servicesStatus.put(service, ClusterServiceStatusJson.findStatus(nodes.size()));
		});
		return servicesStatus;
	}

	final public ClusterServiceStatusJson getServiceStatus(final String group, final String service) {
		final TreeSet<String> nodes = getNodesByGroupByService(group, service);
		return nodes == null || nodes.isEmpty() ? new ClusterServiceStatusJson() : new ClusterServiceStatusJson(nodes);
	}

	final public TreeSet<String> getNodesByGroupByService(final String group, final String service) {
		if (StringUtils.isEmpty(group))
			return clusterNodeMap.getByService(service);
		else if (StringUtils.isEmpty(service))
			return clusterNodeMap.getByGroup(group);
		else
			return clusterNodeMap.getGroupService(group, service);
	}

	final public String getLeaderNode(final String group, final String service) {
		final TreeSet<String> nodes = getNodesByGroupByService(group, service);
		if (nodes == null || nodes.isEmpty())
			return null;
		return nodes.first();
	}

	final public String getRandomNode(final String group, final String service) {
		final TreeSet<String> nodes = getNodesByGroupByService(group, service);
		if (nodes == null || nodes.isEmpty())
			return null;
		int rand = RandomUtils.nextInt(0, nodes.size());
		Iterator<String> it = nodes.iterator();
		for (; ; ) {
			final String node = it.next();
			if (rand == 0)
				return node;
			rand--;
		}
	}

	public synchronized void joinCluster(final Collection<Class<? extends ServiceInterface>> services,
			final Collection<InetSocketAddress> recipients) throws IOException {
		if (services != null) {
			myServices.clear();
			services.forEach(service -> {
				ServiceName serviceName = AnnotationsUtils.getFirstAnnotation(service, ServiceName.class);
				Objects.requireNonNull(serviceName, "The ServiceName annotation is missing for " + service);
				myServices.add(serviceName.value());
			});
		}
		final Collection<InetSocketAddress> fRecipients =
				recipients != null ? recipients : clusterNodeMap.getNodeAddresses();
		ClusterProtocol.newJoin(me.httpAddressKey, nodeLiveId, myGroups, myServices).send(fRecipients);
	}

	public synchronized void leaveCluster() throws IOException {
		final Set<InetSocketAddress> recipients = clusterNodeMap.getNodeAddresses();
		ClusterProtocol.newLeave(me.httpAddressKey, nodeLiveId).send(recipients);
	}

	final public void acceptJoin(ClusterProtocol.Full message) throws URISyntaxException, IOException {
		// Registering the node
		final ClusterNode node =
				clusterNodeMap.register(message);
		// Notify  peers
		ClusterProtocol.newNotify(message).send(clusterNodeMap.getNodeAddresses(),
				me.address, node.address.address);
	}

	final public void acceptNotify(ClusterProtocol.Address message) throws URISyntaxException, IOException {
		final ClusterNode clusterNode = clusterNodeMap.getIfExists(message.getAddress());
		// If we already know the node, we can leave
		if (clusterNode != null &&
				message.getNodeLiveId().equals(clusterNode.nodeLiveId))
			return;
		// Otherwise we forward our configuration
		ClusterProtocol.newForward(me.httpAddressKey, nodeLiveId, myGroups, myServices).send(message.getAddress());
	}

	final public void acceptForward(ClusterProtocol.Full message) throws URISyntaxException, IOException {
		clusterNodeMap.register(message);
		// Send back myself
		ClusterProtocol.newReply(me.httpAddressKey, nodeLiveId, myGroups, myServices)
				.send(message.getAddress());
	}

	final public void acceptReply(ClusterProtocol.Full message) throws URISyntaxException, IOException {
		clusterNodeMap.register(message);
	}

	@Override
	final public void accept(final DatagramPacket datagramPacket) {
		try {
			ClusterProtocol.Message message = new ClusterProtocol.Message(datagramPacket);
			logger.info("DATAGRAMPACKET FROM: " + datagramPacket.getAddress().toString() + " " + message.getCommand());
			switch (message.getCommand()) {
				case join:
					acceptJoin(message.getContent());
					break;
				case notify:
					acceptNotify(message.getContent());
					break;
				case forward:
					acceptForward(message.getContent());
					break;
				case reply:
					acceptReply(message.getContent());
					break;
				case leave:
					clusterNodeMap.unregister(message.getContent());
					break;
			}
		} catch (Exception e) {
			logger.warn(e.getMessage(), e);
		}
	}


}
