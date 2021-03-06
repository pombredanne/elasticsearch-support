
package org.xbib.elasticsearch.support;

import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.network.NetworkUtils;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.node.Node;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.net.URI;
import java.util.Map;
import java.util.Random;

import static org.elasticsearch.common.collect.Maps.newHashMap;
import static org.elasticsearch.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS;
import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

public abstract class AbstractNodeTest extends Assert {

    public final String INDEX = "test-" + NetworkUtils.getLocalAddress().getHostName().toLowerCase();

    protected final String CLUSTER = "test-cluster-" + NetworkUtils.getLocalAddress().getHostName();

    protected int PORT;

    protected URI ADDRESS;

    protected Settings defaultSettings = ImmutableSettings
            .settingsBuilder()
            .put("cluster.name", CLUSTER)
            .build();

    private Map<String, Node> nodes = newHashMap();
    private Map<String, Client> clients = newHashMap();
    private Map<String, InetSocketTransportAddress> addresses = newHashMap();

    @BeforeMethod
    public void createIndices() throws Exception {
        startNode("1");

        NodesInfoRequest nodesInfoRequest = new NodesInfoRequest().transport(true);
        NodesInfoResponse response = client("1").admin().cluster().nodesInfo(nodesInfoRequest).actionGet();
        InetSocketTransportAddress address = (InetSocketTransportAddress)response.iterator().next()
                        .getTransport().getAddress().publishAddress();
        PORT = address.address().getPort();

        ADDRESS = URI.create("es://localhost:"+PORT+"?es.cluster.name=" + CLUSTER);

        addresses.put("1", address);

        client("1").admin().indices()
                .create(new CreateIndexRequest(INDEX))
                .actionGet();
    }

    @AfterMethod
    public void deleteIndices() {
        try {
            // clear test index
            client("1").admin().indices()
                    .delete(new DeleteIndexRequest().indices(INDEX))
                    .actionGet();
        } catch (IndexMissingException e) {
            // ignore
        }
        closeNode("1");
        closeAllNodes();
    }

    public void putDefaultSettings(Settings.Builder settings) {
        putDefaultSettings(settings.build());
    }

    public void putDefaultSettings(Settings settings) {
        defaultSettings = ImmutableSettings.settingsBuilder().put(defaultSettings).put(settings).build();
    }

    public Node startNode(String id) {
        return buildNode(id).start();
    }

    public Node startNode(String id, Settings.Builder settings) {
        return startNode(id, settings.build());
    }

    public Node startNode(String id, Settings settings) {
        return buildNode(id, settings)
                .start();
    }

    public Node buildNode(String id) {
        return buildNode(id, EMPTY_SETTINGS);
    }

    public Node buildNode(String id, Settings.Builder settings) {
        return buildNode(id, settings.build());
    }

    public Node buildNode(String id, Settings settings) {
        String settingsSource = getClass().getName().replace('.', '/') + ".yml";
        Settings finalSettings = settingsBuilder()
                .loadFromClasspath(settingsSource)
                .put(defaultSettings)
                .put(settings)
                .put("name", id)
                .build();

        if (finalSettings.get("gateway.type") == null) {
            // default to non gateway
            finalSettings = settingsBuilder().put(finalSettings).put("gateway.type", "none").build();
        }
        if (finalSettings.get("cluster.routing.schedule") != null) {
            // decrease the routing schedule so new nodes will be added quickly
            finalSettings = settingsBuilder().put(finalSettings).put("cluster.routing.schedule", "50ms").build();
        }
        Node node = nodeBuilder().settings(finalSettings).build();

        Client client = node.client();

        nodes.put(id, node);
        clients.put(id, client);
        return node;
    }

    public void closeNode(String id) {
        Client client = clients.remove(id);
        if (client != null) {
            client.close();
        }
        Node node = nodes.remove(id);
        if (node != null) {
            node.close();
        }
    }

    public Node node(String id) {
        return nodes.get(id);
    }

    public Client client(String id) {
        return clients.get(id);
    }

    public InetSocketTransportAddress address(String id) {
        return addresses.get(id);
    }

    public void closeAllNodes() {
        for (Client client : clients.values()) {
            client.close();
        }
        clients.clear();
        for (Node node : nodes.values()) {
            node.close();
        }
        nodes.clear();
    }

    private static Random random = new Random();
    private static char[] numbersAndLetters = ("0123456789abcdefghijklmnopqrstuvwxyz").toCharArray();

    protected String randomString(int len) {
        final char[] buf = new char[len];
        final int n = numbersAndLetters.length - 1;
        for (int i = 0; i < buf.length; i++) {
            buf[i] = numbersAndLetters[random.nextInt(n)];
        }
        return new String(buf);
    }

}
