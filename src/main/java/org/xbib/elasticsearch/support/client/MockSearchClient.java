
package org.xbib.elasticsearch.support.client;

import org.xbib.elasticsearch.action.search.support.BasicRequest;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * Search client mock. Do not perform actions on a real cluster.
 */
public class MockSearchClient extends SearchClient {

    private final ESLogger logger = ESLoggerFactory.getLogger(MockSearchClient.class.getName());

    private final Set<InetSocketTransportAddress> addresses = new HashSet();

    /**
     * No special initial settings except cluster name
     *
     * @param uri
     * @return initial settings
     */
    @Override
    protected Settings initialSettings(URI uri, int n) {
        return ImmutableSettings.settingsBuilder()
                .put("cluster.name", findClusterName(uri))
                .build();
    }

    public MockSearchClient newClient() {
        return newClient(AbstractIngestClient.findURI());
    }

    public MockSearchClient newClient(URI uri) {
        settings = initialSettings(uri, 0);
        return this;
    }

    public synchronized void shutdown() {
    }

    public BasicRequest newRequest() {
        return new BasicRequest();
    }

    /**
     * Configure addresses, but do not connect.
     *
     * @param uri
     * @throws IOException
     */
    protected void connect(URI uri) throws IOException {
        String hostname = uri.getHost();
        int port = uri.getPort();
        if (!"es".equals(uri.getScheme())) {
            logger.warn("please specify URI scheme 'es'");
        }
        if ("hostname".equals(hostname)) {
            InetSocketTransportAddress address = new InetSocketTransportAddress(InetAddress.getLocalHost().getHostName(), port);
            if (!addresses.contains(address)) {
                logger.info("adding hostname address for transport client = {}", address);
                addresses.add(address);
            }
        } else if ("inet4".equals(hostname)) {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                for (InetAddress addr : Collections.list(inetAddresses)) {
                    if (addr instanceof Inet4Address) {
                        InetSocketTransportAddress address = new InetSocketTransportAddress(addr, port);
                        if (!addresses.contains(address)) {
                            logger.info("adding interface address for transport client = {}", address);
                            client.addTransportAddress(address);
                            addresses.add(address);
                        }
                    }
                }
            }
        } else if ("inet6".equals(hostname)) {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                for (InetAddress addr : Collections.list(inetAddresses)) {
                    if (addr instanceof Inet6Address) {
                        InetSocketTransportAddress address = new InetSocketTransportAddress(addr, port);
                        if (!addresses.contains(address)) {
                            logger.info("adding interface address for transport client = {}", address);
                            client.addTransportAddress(address);
                            addresses.add(address);
                        }
                    }
                }
            }
        } else {
            InetSocketTransportAddress address = new InetSocketTransportAddress(hostname, port);
            if (!addresses.contains(address)) {
                logger.info("adding custom address for transport client = {}", address);
                client.addTransportAddress(address);
                addresses.add(address);
            }
        }
        logger.info("configured addresses to connect = {}", addresses);
    }

    public MockSearchClient waitForHealthyCluster() throws IOException {
        return this;
    }

}
