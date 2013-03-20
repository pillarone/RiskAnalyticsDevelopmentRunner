// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.spi.discovery.tcp.internal.*;
import org.gridgain.grid.spi.discovery.tcp.messages.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.spi.discovery.tcp.metricsstore.*;
import org.gridgain.grid.spi.discovery.tcp.topologystore.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.processors.port.GridPortProtocol.*;
import static org.gridgain.grid.spi.discovery.tcp.internal.GridTcpDiscoverySpiState.*;
import static org.gridgain.grid.spi.discovery.tcp.messages.GridTcpDiscoveryStatusCheckMessage.*;
import static org.gridgain.grid.spi.discovery.tcp.topologystore.GridTcpDiscoveryTopologyStoreNodeState.*;

/**
 * Discovery SPI implementation that uses TCP/IP for node discovery.
 * <p>
 * Node are organized in ring. So almost all network exchange (except few cases) is
 * done across it.
 * <p>
 * At startup SPI tries to send messages to random IP taken from
 * {@link GridTcpDiscoveryIpFinder} about self start (stops when send succeeds)
 * and then this info goes to coordinator. When coordinator processes join request
 * it locks the topology and issues node added messages and all other nodes then
 * receive info about new node.
 * <p>
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * <ul>
 * <li>IP finder to share info about nodes IP addresses
 * (see {@link #setIpFinder(GridTcpDiscoveryIpFinder)}).
 * See the following IP finder implementations for details on configuration:
 * <ul>
 * <li>{@code GridTcpDiscoverySharedFsIpFinder} - available in Enterprise edition only.</li>
 * <li>{@code GridTcpDiscoveryS3IpFinder} - available in Enterprise edition only.</li>
 * <li>{@link GridTcpDiscoveryVmIpFinder}</li>
 * </ul>
 * </li>
 * </ul>
 * <h2 class="header">Optional</h2>
 * The following configuration parameters are optional:
 * <ul>
 * <li>Metrics store (see {@link #setMetricsStore(GridTcpDiscoveryMetricsStore)})</li>
 * <li>Local address (see {@link #setLocalAddress(String)})</li>
 * <li>Local port to bind to (see {@link #setLocalPort(int)} )</li>
 * <li>Local port range to try binding to if previous ports are in use
 *      (see {@link #setLocalPortRange(int)})</li>
 * <li>Heartbeat frequency (see {@link #setHeartbeatFrequency(int)})</li>
 * <li>Max missed heartbeats (see {@link #setMaxMissedHeartbeats(int)})</li>
 * <li>Number of times node tries to (re)establish connection to another node
 *      (see {@link #setReconnectCount(int)} )</li>
 * <li>Network timeout (see {@link #setNetworkTimeout(int)} )</li>
 * <li>Thread priority for threads started by SPI (see {@link #setThreadPriority(int)})</li>
 * <li>IP finder and Metrics Store clean frequency (see {@link #setStoresCleanFrequency(int)})</li>
 * <li>Status print frequency (see {@link #setStatisticsPrintFrequency(int)}</li>
 * <li>Enable or disable network segment check on start, other node leave or failure
 *      (see {@link #setCheckSegmentEnabled(boolean)} )</li>
 * <li>Require all address reachability when checking segment
 *      (see {@link #setAllAddressesReachabilityRequired(boolean)})</li>
 * </ul>
 *
 * <h2 class="header">Java Example</h2>
 * <pre name="code" class="java">
 * GridTcpDiscoverySpi spi = new GridTcpDiscoverySpi();
 *
 * GridTcpDiscoveryVmIpFinder finder =
 *     new GridTcpDiscoveryVmIpFinder();
 *
 * spi.setIpFinder(finder);
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default discovery SPI.
 * cfg.setDiscoverySpi(spi);
 *
 * // Start grid.
 * GridFactory.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridTcpDiscoverySpi can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *         ...
 *         &lt;property name="discoverySpi"&gt;
 *             &lt;bean class="org.gridgain.grid.spi.discovery.tcp.GridTcpDiscoverySpi"&gt;
 *                 &lt;property name="ipFinder"&gt;
 *                     &lt;bean class="org.gridgain.grid.spi.discovery.tcp.ipfinder.vm.GridTcpDiscoveryVmIpFinder" /&gt;
 *                 &lt;/property&gt;
 *             &lt;/bean&gt;
 *         &lt;/property&gt;
 *         ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridDiscoverySpi
 */
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(true)
@GridDiscoverySpiOrderSupport(true)
public class GridTcpDiscoverySpi extends GridSpiAdapter implements GridDiscoverySpi, GridTcpDiscoverySpiMBean {
    /** Default port to listen (value is <tt>47500</tt>). */
    public static final int DFLT_PORT = 47500;

    /** Default local port range (value is <tt>100</tt>). */
    public static final int DFLT_PORT_RANGE = 100;

    /** Default network timeout in milliseconds (value is <tt>3000</tt>). */
    public static final int DFLT_NETWORK_TIMEOUT = 3000;

    /** Default reconnect attempts count (value is <tt>2</tt>). */
    public static final int DFLT_RECONNECT_CNT = 2;

    /** Default heartbeat messages issuing frequency (value is <tt>3000</tt>). */
    public static final int DFLT_HEARTBEAT_FREQ = 3000;

    /** Default max heartbeats count node can miss without initiating status check (value is <tt>3</tt>). */
    public static final int DFLT_MAX_MISSED_HEARTBEATS = 3;

    /** Default value for thread priority (value is <tt>7</tt>). */
    public static final int DFLT_THREAD_PRI = 7;

    /** Default stores (IP finder clean and metrics store) frequency in milliseconds (value is <tt>60000</tt>). */
    public static final int DFLT_STORES_CLEAN_FREQ = 60 * 1000;

    /** Default statistics print frequency in milliseconds (value is <tt>0</tt>). */
    public static final int DFLT_STATS_PRINT_FREQ = 0;

    /** Default value for segment check (value is <tt>false</tt>). */
    public static final boolean DFLT_SEGMENT_CHECK_ENABLED = false;

    /** Default value for all addresses reachability required (value is <tt>true</tt>). */
    public static final boolean DFLT_ALL_ADDRS_REACHABILITY_REQUIRED = true;

    /** Response OK. */
    private static final int RES_OK = 1;

    /** Response CONTINUE JOIN. */
    private static final int RES_CONTINUE_JOIN = 100;

    /** Response WAIT. */
    private static final int RES_WAIT = 200;

    /** Predicate to filter visible nodes. */
    private static final GridPredicate<GridTcpDiscoveryNode> VISIBLE_NODES = new P1<GridTcpDiscoveryNode>() {
        @Override public boolean apply(GridTcpDiscoveryNode node) {
            return node.visible();
        }
    };

    /** Local port which node uses. */
    private int locPort = DFLT_PORT;

    /** Local port range. */
    private int locPortRange = DFLT_PORT_RANGE;

    /** Statistics print frequency. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private int statsPrintFreq = DFLT_STATS_PRINT_FREQ;

    /** Network timeout. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private int netTimeout = DFLT_NETWORK_TIMEOUT;

    /** Heartbeat messages issuing frequency. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private int hbFreq = DFLT_HEARTBEAT_FREQ;

    /** Max heartbeats count node can miss without initiating status check. */
    private int maxMissedHbs = DFLT_MAX_MISSED_HEARTBEATS;

    /** Thread priority for all threads started by SPI. */
    private int threadPri = DFLT_THREAD_PRI;

    /** Stores clean frequency. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private int storesCleanFreq = DFLT_STORES_CLEAN_FREQ;

    /** Reconnect attempts count. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private int reconCnt = DFLT_RECONNECT_CNT;

    /** All addresses reachability (during segment check) required flag. */
    private boolean allAddrsReachReq = DFLT_ALL_ADDRS_REACHABILITY_REQUIRED;

    /** Check segment flag. If {@code false} segment check will be omitted. */
    private boolean checkSegEnabled = DFLT_SEGMENT_CHECK_ENABLED;

    /** Name of the grid. */
    @GridNameResource
    private String gridName;

    /** Grid logger. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridLoggerResource
    private GridLogger log;

    /** Marshaller. */
    @GridMarshallerResource
    private GridMarshaller marsh;

    /** Local node Id. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridLocalNodeIdResource
    private UUID locNodeId;

    /** Local node. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private GridTcpDiscoveryNode locNode;

    /** Local IP address. */
    @GridLocalHostResource
    private String locAddr;

    /** Complex variable that represents this node IP address. */
    private InetAddress locHost;

    /** Grid discovery listener. */
    private volatile GridDiscoverySpiListener lsnr;

    /** Metrics provider. */
    private GridDiscoveryMetricsProvider metricsProvider;

    /** Local node attributes. */
    private Map<String, Object> nodeAttrs;

    /** IP finder. */
    private GridTcpDiscoveryIpFinder ipFinder;

    /** Metrics store. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private GridTcpDiscoveryMetricsStore metricsStore;

    /** Topology store. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private GridTcpDiscoveryTopologyStore topStore;

    /** Nodes ring. */
    private final GridTcpDiscoveryNodesRing ring = new GridTcpDiscoveryNodesRing();

    /** Discovery state. */
    private GridTcpDiscoverySpiState spiState;

    /** Socket readers. */
    private final Collection<SocketReader> readers = new LinkedList<SocketReader>();

    /** TCP server for discovery SPI. */
    private TcpServer tcpSrvr;

    /** Message worker. */
    private MessageWorker msgWorker;

    /** Metrics sender. */
    private HeartbeatsSender hbsSnd;

    /** Status checker. */
    private CheckStatusSender chkStatusSnd;

    /** Metrics update notifier. */
    private MetricsUpdateNotifier metricsUpdateNtf;

    /** Stores cleaner. */
    private StoresCleaner storesCleaner;

    /** SPI reconnect thread. */
    private volatile SpiReconnectThread spiReconnectThread;

    /** Topology store worker. */
    private TopologyStoreWorker topStoreWorker;

    /** Statistics printer thread. */
    private StatisticsPrinter statsPrinter;

    /** Failed nodes (but still in topology). */
    private Collection<GridTcpDiscoveryNode> failedNodes = new HashSet<GridTcpDiscoveryNode>();

    /** Leaving nodes (but still in topology). */
    private Collection<GridTcpDiscoveryNode> leavingNodes = new HashSet<GridTcpDiscoveryNode>();

    /** Statistics. */
    private final GridTcpDiscoveryStatistics stats = new GridTcpDiscoveryStatistics();

    /** If non-shared IP finder is used this flag shows whether IP finder contains local address. */
    private boolean ipFinderHasLocAddr;

    /** Join requests results (for handling concurrent starts). */
    private final Map<InetSocketAddress, Integer> joinRequestResults =
        new ConcurrentHashMap<InetSocketAddress, Integer>();

    /** Topology version (if topology store is used). */
    private final AtomicLong topVer = new AtomicLong();

    /** Mutex. */
    private final Object mux = new Object();

    /**
     * Sets local host IP address that discovery SPI uses.
     * <p>
     * If not provided, by default a first found non-loopback address
     * will be used. If there is no non-loopback address available,
     * then {@link InetAddress#getLocalHost()} will be used.
     *
     * @param locAddr IP address.
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalAddress(String locAddr) {
        this.locAddr = locAddr;
    }

    /** {@inheritDoc} */
    @Override public int getReconnectCount() {
        return reconCnt;
    }

    /**
     * Number of times node tries to (re)establish connection to another node.
     * <p>
     * If not specified, default is {@link #DFLT_RECONNECT_CNT}.
     *
     * @param reconCnt Number of retries during message sending.
     */
    @GridSpiConfiguration(optional = true)
    public void setReconnectCount(int reconCnt) {
        this.reconCnt = reconCnt;
    }

    /** {@inheritDoc} */
    @Override public int getNetworkTimeout() {
        return netTimeout;
    }

    /**
     * Sets maximum network timeout to use for network operations.
     * <p>
     * If not specified, default is {@link #DFLT_NETWORK_TIMEOUT}.
     *
     * @param netTimeout Network timeout.
     */
    @GridSpiConfiguration(optional = true)
    public void setNetworkTimeout(int netTimeout) {
        this.netTimeout = netTimeout;
    }

    /** {@inheritDoc} */
    @Override public int getLocalPort() {
        return locPort;
    }

    /**
     * Sets local port to listen to.
     * <p>
     * If not specified, default is {@link #DFLT_PORT}.
     *
     * @param locPort Local port to bind.
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalPort(int locPort) {
        this.locPort = locPort;
    }

    /** {@inheritDoc} */
    @Override public int getLocalPortRange() {
        return locPortRange;
    }

    /**
     * Range for local ports. Local node will try to bind on first available port
     * starting from {@link #getLocalPort()} up until
     * <tt>{@link #getLocalPort()} {@code + locPortRange}</tt>.
     * <p>
     * If not specified, default is {@link #DFLT_PORT_RANGE}.
     *
     * @param locPortRange Local port range to bind.
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalPortRange(int locPortRange) {
        this.locPortRange = locPortRange;
    }

    /** {@inheritDoc} */
    @Override public int getHeartbeatFrequency() {
        return hbFreq;
    }

    /**
     * Sets delay between issuing of heartbeat messages. SPI sends heartbeat messages
     * in configurable time interval to other nodes to notify them about its state.
     * <p>
     * If not provided, default value is {@link #DFLT_HEARTBEAT_FREQ}.
     *
     * @param hbFreq Heartbeat frequency in milliseconds.
     */
    @GridSpiConfiguration(optional = true)
    public void setHeartbeatFrequency(int hbFreq) {
        this.hbFreq = hbFreq;
    }

    /** {@inheritDoc} */
    @Override public int getMaxMissedHeartbeats() {
        return maxMissedHbs;
    }

    /**
     * Sets max heartbeats count node can miss without initiating status check.
     * <p>
     * If not provided, default value is {@link #DFLT_MAX_MISSED_HEARTBEATS}.
     *
     * @param maxMissedHbs Max missed heartbeats.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaxMissedHeartbeats(int maxMissedHbs) {
        this.maxMissedHbs = maxMissedHbs;
    }

    /** {@inheritDoc} */
    @Override public int getStatisticsPrintFrequency() {
        return statsPrintFreq;
    }

    /**
     * Sets statistics print frequency.
     * <p>
     * If not set default value is {@link #DFLT_STATS_PRINT_FREQ}.
     * 0 indicates that no print is required. If value is greater than 0 and log is
     * not quiet then statistics are printed out with INFO level.
     *
     * @param statsPrintFreq Statistics print frequency in milliseconds.
     */
    @GridSpiConfiguration(optional = true)
    public void setStatisticsPrintFrequency(int statsPrintFreq) {
        this.statsPrintFreq = statsPrintFreq;
    }

    /**
     * Sets IP finder for IP addresses sharing and storing.
     *
     * @param ipFinder IP finder.
     */
    @GridSpiConfiguration(optional = false)
    public void setIpFinder(GridTcpDiscoveryIpFinder ipFinder) {
        this.ipFinder = ipFinder;
    }

    /**
     * Sets topology store.
     * <p>
     * If provided, SPI gets topology change notifications by querying store.
     * It is recommended to provide topology store when working with large
     * topologies.
     *
     * @param topStore Topology store.
     */
    @GridSpiConfiguration(optional = true)
    public void setTopologyStore(GridTcpDiscoveryTopologyStore topStore) {
        this.topStore = topStore;
    }

    /** {@inheritDoc} */
    @Override public boolean isAllAddressesReachabilityRequired() {
        return allAddrsReachReq;
    }

    /**
     * Sets all addresses reachability required flag.
     * <p>
     * If {@code true} then all segment check addresses will be checked on SPI start
     * and all of them should be reachable for node to be in a good segment.
     * Otherwise, at least one of the check addresses should be reachable for node to
     * be in a good segment.
     * <p>
     * If not provided, default value is {@link #DFLT_ALL_ADDRS_REACHABILITY_REQUIRED}.
     *
     * @param allAddrsReachReq {@code true} if SPI should check each address reachability.
     */
    @GridSpiConfiguration(optional = true)
    public void setAllAddressesReachabilityRequired(boolean allAddrsReachReq) {
        this.allAddrsReachReq = allAddrsReachReq;
    }

    /** {@inheritDoc} */
    @Override public boolean isCheckSegmentEnabled() {
        return checkSegEnabled;
    }

    /**
     * Sets check segment flag. If {@code false} segment check will be omitted.
     * <p>
     * If not provided, default value is {@link #DFLT_SEGMENT_CHECK_ENABLED}
     *
     * @param checkSegEnabled {@code false} if segment check should be omitted
     */
    @GridSpiConfiguration(optional = true)
    public void setCheckSegmentEnabled(boolean checkSegEnabled) {
        this.checkSegEnabled = checkSegEnabled;
    }

    /** {@inheritDoc} */
    @Override public int getThreadPriority() {
        return threadPri;
    }

    /**
     * Sets thread priority. All threads within SPI will be started with it.
     * <p>
     * If not provided, default value is {@link #DFLT_THREAD_PRI}
     *
     * @param threadPri Thread priority.
     */
    @GridSpiConfiguration(optional = true)
    public void setThreadPriority(int threadPri) {
        this.threadPri = threadPri;
    }

    /** {@inheritDoc} */
    @Override public int getStoresCleanFrequency() {
        return storesCleanFreq;
    }

    /**
     * Sets stores (IP finder and metrics store) clean frequency in milliseconds.
     * <p>
     * If not provided, default value is {@link #DFLT_STORES_CLEAN_FREQ}
     *
     * @param storesCleanFreq Stores clean frequency.
     */
    @GridSpiConfiguration(optional = true)
    public void setStoresCleanFrequency(int storesCleanFreq) {
        this.storesCleanFreq = storesCleanFreq;
    }

    /** {@inheritDoc} */
    @Override public String getSpiState() {
        synchronized (mux) {
            return spiState.name();
        }
    }

    /** {@inheritDoc} */
    @Override public String getIpFinderName() {
        return ipFinder.toString();
    }

    /** {@inheritDoc} */
    @Override @Nullable public String getMetricsStoreName() {
        return metricsStore != null ? metricsStore.toString() : null;
    }

    /** {@inheritDoc} */
    @Override @Nullable public String getTopologyStoreName() {
        return topStore != null ? topStore.toString() : null;
    }

    /** {@inheritDoc} */
    @Override public int getMessageWorkerQueueSize() {
        return msgWorker.queue.size();
    }

    /** {@inheritDoc} */
    @Override public long getNodesJoined() {
        return stats.joinedNodesCount();
    }

    /** {@inheritDoc} */
    @Override public long getNodesLeft() {
        return stats.leftNodesCount();
    }

    /** {@inheritDoc} */
    @Override public long getNodesFailed() {
        return stats.failedNodesCount();
    }

    /** {@inheritDoc} */
    @Override public long getPendingMessagesRegistered() {
        return stats.pendingMessagesRegistered();
    }

    /** {@inheritDoc} */
    @Override public long getPendingMessagesDiscarded() {
        return stats.pendingMessagesDiscarded();
    }

    /** {@inheritDoc} */
    @Override public long getAvgMessageProcessingTime() {
        return stats.avgMessageProcessingTime();
    }

    /** {@inheritDoc} */
    @Override public long getMaxMessageProcessingTime() {
        return stats.maxMessageProcessingTime();
    }

    /** {@inheritDoc} */
    @Override public int getTotalReceivedMessages() {
        return stats.totalReceivedMessages();
    }

    /** {@inheritDoc} */
    @Override public Map<String, Integer> getReceivedMessages() {
        return stats.receivedMessages();
    }

    /** {@inheritDoc} */
    @Override public int getTotalProcessedMessages() {
        return stats.totalProcessedMessages();
    }

    /** {@inheritDoc} */
    @Override public Map<String, Integer> getProcessedMessages() {
        return stats.processedMessages();
    }

    /** {@inheritDoc} */
    @Override public long getCoordinatorSinceTimestamp() {
        return stats.coordinatorSinceTimestamp();
    }

    /** {@inheritDoc} */
    @Nullable @Override public UUID getCoordinator() {
        GridTcpDiscoveryNode crd = resolveCoordinator();

        return crd != null ? crd.id() : null;
    }

    /**
     * Sets metrics store.
     * <p>
     * If provided, SPI does not send metrics across the ring and uses metrics
     * store to exchange metrics. It is recommended to provide metrics store when
     * working with large topologies.
     *
     * @param metricsStore Metrics store.
     */
    @GridSpiConfiguration(optional = true)
    public void setMetricsStore(GridTcpDiscoveryMetricsStore metricsStore) {
        this.metricsStore = metricsStore;
    }

    /** {@inheritDoc} */
    @Override public GridNode getLocalNode() {
        return locNode;
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridNode getNode(UUID nodeId) {
        GridTcpDiscoveryNode node = ring.node(nodeId);

        assert node == null || node.visible() : "Invisible node has been requested explicitly: " + node;

        return node;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridNode> getRemoteNodes() {
        return new ArrayList<GridNode>(F.view(ring.remoteNodes(), VISIBLE_NODES));
    }

    /** {@inheritDoc} */
    @Override public void setListener(GridDiscoverySpiListener lsnr) {
        this.lsnr = lsnr;
    }

    /** {@inheritDoc} */
    @Override public void setMetricsProvider(GridDiscoveryMetricsProvider metricsProvider) {
        this.metricsProvider = metricsProvider;
    }

    /** {@inheritDoc} */
    @Override public void setNodeAttributes(Map<String, Object> attrs) {
        nodeAttrs = attrs;
    }

    /** {@inheritDoc} */
    @Override public Collection<Object> injectables() {
        Collection<Object> res = new LinkedList<Object>();

        if (metricsStore != null)
            res.add(metricsStore);

        if (topStore != null)
            res.add(topStore);

        res.add(ipFinder);

        return res;
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        startStopwatch();

        assertParameter(ipFinder != null, "ipFinder != null");
        assertParameter(storesCleanFreq > 0, "ipFinderCleanFreq > 0");
        assertParameter(locPort > 1023, "localPort > 1023");
        assertParameter(locPortRange >= 0, "localPortRange >= 0");
        assertParameter(locPort + locPortRange <= 0xffff, "locPort + locPortRange <= 0xffff");
        assertParameter(netTimeout > 0, "networkTimeout > 0");
        assertParameter(reconCnt > 0, "reconnectCnt > 0");
        assertParameter(hbFreq > 0, "heartbeatFreq > 0");
        assertParameter(maxMissedHbs > 0, "maxMissedHeartbeats > 0");
        assertParameter(threadPri > 0, "threadPri > 0");
        assertParameter(statsPrintFreq >= 0, "statsPrintFreq >= 0");

        // Initialize SPI state.
        synchronized (mux) {
            spiState = DISCONNECTED;
        }

        try {
            locHost = F.isEmpty(locAddr) ? U.getLocalHost() : InetAddress.getByName(locAddr);
        }
        catch (IOException e) {
            throw new GridSpiException("Unknown local address: " + locAddr, e);
        }

        if (log.isDebugEnabled()) {
            log.debug(configInfo("localHost", locHost.getHostAddress()));
            log.debug(configInfo("localPort", locPort));
            log.debug(configInfo("localPortRange", locPortRange));
            log.debug(configInfo("checkSegmentEnabled", checkSegEnabled));
            log.debug(configInfo("allAddrsReachabilityRequired", allAddrsReachReq));
            log.debug(configInfo("threadPri", threadPri));
            log.debug(configInfo("networkTimeout", netTimeout));
            log.debug(configInfo("reconnectCount", reconCnt));
            log.debug(configInfo("ipFinder", ipFinder));
            log.debug(configInfo("ipFinderCleanFreq", storesCleanFreq));
            log.debug(configInfo("heartbeatFreq", hbFreq));
            log.debug(configInfo("maxMissedHeartbeats", maxMissedHbs));
            log.debug(configInfo("metricsStore", metricsStore));
            log.debug(configInfo("topStore", topStore));
            log.debug(configInfo("statsPrintFreq", statsPrintFreq));
        }

        // Warn on odd network timeout.
        if (netTimeout < 3000)
            U.warn(log, "Network timeout is too low (at least 3000 ms): " + netTimeout);

        // Warn on odd heartbeat frequency.
        if (hbFreq < 3000)
            U.warn(log, "Heartbeat frequency is too low (at least 3000 ms): " + hbFreq);

        // Warn on odd max missed heartbeats.
        if (maxMissedHbs < 3)
            U.warn(log, "Maximum missed heartbeats value is too low (at least 3): " + maxMissedHbs);

        if (checkSegEnabled)
            checkSegment();
        else if (log.isDebugEnabled())
            log.debug("Safely omitting segment check (segment check is disabled in configuration).");

        msgWorker = new MessageWorker();

        msgWorker.start();

        tcpSrvr = new TcpServer();

        tcpSrvr.start();

        // Init locNode.
        locNode = new GridTcpDiscoveryNode(locNodeId, new InetSocketAddress(locHost, tcpSrvr.port),
            metricsProvider);

        locNode.setAttributes(nodeAttrs);

        ring.localNode(locNode);

        if (ipFinder.isShared())
            ipFinder.registerAddresses(Arrays.asList(locNode.address()));
        else {
            if (ipFinder.getRegisteredAddresses().isEmpty())
                throw new GridSpiException("Non-shared IP finder does not have any addresses.");

            ipFinderHasLocAddr = ipFinderHasLocalAddress();
        }

        if (statsPrintFreq > 0 && log.isInfoEnabled() && !log.isQuiet()) {
            statsPrinter = new StatisticsPrinter();
            statsPrinter.start();
        }

        stats.onJoinStarted();

        joinTopology();

        stats.onJoinFinished();

        hbsSnd = new HeartbeatsSender();
        hbsSnd.start();

        chkStatusSnd = new CheckStatusSender();
        chkStatusSnd.start();

        if (metricsStore != null) {
            metricsUpdateNtf = new MetricsUpdateNotifier();
            metricsUpdateNtf.start();
        }

        if (ipFinder.isShared() || metricsStore != null) {
            storesCleaner = new StoresCleaner();
            storesCleaner.start();
        }

        if (topStore != null) {
            topStoreWorker = new TopologyStoreWorker();
            topStoreWorker.start();
        }

        registerMBean(gridName, this, GridTcpDiscoverySpiMBean.class);

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} }*/
    @Override public void onContextInitialized(GridSpiContext spiCtx) throws GridSpiException {
        super.onContextInitialized(spiCtx);

        getSpiContext().registerPort(tcpSrvr.port, TCP);
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        if (log.isDebugEnabled())
            log.debug("Preparing to start local node stop procedure.");

        U.interrupt(spiReconnectThread);
        U.join(spiReconnectThread, log);

        if (msgWorker != null) {
            msgWorker.addMessage(new GridTcpDiscoveryNodeLeftMessage(locNodeId));

            synchronized (mux) {
                long threshold = System.currentTimeMillis() + netTimeout;

                long timeout = netTimeout;

                while (spiState != LEFT && timeout > 0)
                    try {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }
                    catch (InterruptedException e) {
                        throw new GridSpiException("Thread has been interrupted.", e);
                    }

                if (spiState == LEFT) {
                    if (log.isDebugEnabled())
                        log.debug("Verification for local node leave has been received from coordinator" +
                            " (continuing stop procedure).");
                }
                else
                    U.warn(log, "No verification for local node leave has been received from coordinator" +
                        " (will stop node anyway).");
            }
        }

        U.interrupt(tcpSrvr);
        U.join(tcpSrvr, log);

        Collection<SocketReader> tmp;

        synchronized (mux) {
            tmp = new ArrayList<SocketReader>(readers);
        }

        U.interrupt(tmp);
        U.joinThreads(tmp, log);

        U.interrupt(hbsSnd);
        U.join(hbsSnd, log);

        U.interrupt(chkStatusSnd);
        U.join(chkStatusSnd, log);

        U.interrupt(storesCleaner);
        U.join(storesCleaner, log);

        U.interrupt(metricsUpdateNtf);
        U.join(metricsUpdateNtf, log);

        U.interrupt(topStoreWorker);
        U.join(topStoreWorker, log);

        U.interrupt(msgWorker);
        U.join(msgWorker, log);

        U.interrupt(statsPrinter);
        U.join(statsPrinter, log);

        unregisterMBean();

        if (log.isDebugEnabled())
            log.debug(stopInfo());

        printStatistics();
    }

    /** {@inheritDoc} */
    @Override public void onContextDestroyed() {
        getSpiContext().deregisterPorts();

        super.onContextDestroyed();
    }

    /**
     * @throws GridSpiException If any error occurs.
     * @return {@code true} if IP finder contains local address.
     */
    private boolean ipFinderHasLocalAddress() throws GridSpiException {
        for (InetSocketAddress addr : ipFinder.getRegisteredAddresses())
            try {
                InetSocketAddress resolved = addr.isUnresolved() ?
                    new InetSocketAddress(InetAddress.getByName(addr.getHostName()), addr.getPort()) : addr;

                if (resolved.equals(locNode.address()))
                    return true;
            }
            catch (UnknownHostException ignored) {
                U.warn(log, "Failed to resolve address from IpFinder (host is unknown): " + addr);
            }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean pingNode(UUID nodeId) {
        assert nodeId != null;

        if (nodeId == locNodeId)
            return true;

        GridTcpDiscoveryNode node = ring.node(nodeId);

        if (node != null) {
            assert node.visible() :  "Invisible node has been requested to ping: " + node;

            return pingNode(node);
        }

        return false;
    }

    /**
     * Pings the remote node to see if it's alive.
     *
     * @param node Node.
     * @return {@code True} if ping succeeds.
     */
    private boolean pingNode(GridTcpDiscoveryNode node) {
        assert node != null;

        if (node.id().equals(locNodeId))
            return true;

        try {
            // ID returned by the node should be the same as ID of the parameter for ping to succeed.
            return node.id().equals(pingNode(node.address()));
        }
        catch (GridSpiException e) {
            if (log.isDebugEnabled())
                log.debug("Failed to ping node [node=" + node + ", err=" + e.getMessage() + ']');
        }

        return false;
    }

    /**
     * Pings the remote node by its address to see if it's alive.
     *
     * @param addr Address of the node.
     * @return ID of the remote node if node alive, {@code null} otherwise.
     * @throws GridSpiException If an error occurs.
     */
    @Nullable private UUID pingNode(InetSocketAddress addr) throws GridSpiException {
        assert addr != null;

        if (addr.equals(locNode.address()))
            return locNodeId;

        Exception err = null;

        Socket sock = null;

        for (int i = 0; i < reconCnt; i++)
            try {
                if(addr.isUnresolved())
                    addr = new InetSocketAddress(InetAddress.getByName(addr.getHostName()), addr.getPort());

                long tstamp = System.currentTimeMillis();

                sock = new Socket(addr.getAddress(), addr.getPort(), locHost, 0);

                // Handshake response will act as ping response.
                U.writeUuid(new DataOutputStream(sock.getOutputStream()), locNodeId);

                UUID remoteNodeId = U.readUuid(new DataInputStream(sock.getInputStream()));

                stats.onClientSocketInitialized(System.currentTimeMillis() - tstamp);

                return remoteNodeId;
            }
            catch (IOException e) {
                if (err == null)
                    err = e;
            }
            finally {
                U.closeQuiet(sock);
            }

        assert err != null;

        throw new GridSpiException("Failed to ping node by address: " + addr, err);
    }

    /**
     * Checks whether node runs in the correct segment.
     *
     * @throws GridSpiException If node is not in the correct segment or any other error occurs.
     */
    private void checkSegment() throws GridSpiException {
        assert checkSegEnabled;

        if (log.isDebugEnabled())
            log.debug("Starting network segment check.");

        Collection<InetAddress> addrs = ipFinder.getSegmentCheckAddresses();

        if (addrs == null || addrs.isEmpty())
            throw new GridSpiException("Failed to check segment because IP finder returned empty collection of " +
                "segment check addresses (either disable segment checking or specify at least one segment " +
                "check address).");

        for (InetAddress addr : addrs) {
            boolean reachable;

            try {
                reachable = addr.isReachable(NetworkInterface.getByInetAddress(locHost), 0, netTimeout);

                if (log.isDebugEnabled())
                    log.debug("Checked address reachability [addr=" + addr + ", reachable=" + reachable + ']');
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to reach segment check address: " + addr, e);
            }

            if (reachable && !allAddrsReachReq)
                break;

            if (!reachable && allAddrsReachReq)
                throw new GridSpiException("Failed to reach segment check address: " + addr);
        }

        if (log.isInfoEnabled())
            log.info("Network segment check has been finished (segment is correct).");
    }

    /**
     * Tries to join this node to topology.
     *
     * @throws GridSpiException If any error occurs.
     */
    private void joinTopology() throws GridSpiException {
        synchronized (mux) {
            assert spiState == CONNECTING || spiState == DISCONNECTED;

            spiState = CONNECTING;
        }

        while (true) {
            if (!sendJoinRequestMessage()) {
                if (log.isDebugEnabled())
                    log.debug("Join request message has not been sent (local node is the first in the topology).");

                if (ring.hasRemoteNodes())
                    ring.clear();

                ring.currentVersion(1);

                locNode.visible(true);

                locNode.order(1);

                if (topStore != null) {
                    topVer.compareAndSet(0, 1);

                    locNode.state(ONLINE);

                    locNode.topologyVersion(1);

                    // Clear the store and put local node to.
                    topStore.clear();

                    long tstamp = System.currentTimeMillis();

                    topStore.put(locNode);

                    stats.onTopologyStoreNodePut(System.currentTimeMillis() - tstamp);
                }

                synchronized (mux) {
                    spiState = CONNECTED;

                    mux.notifyAll();
                }

                break;
            }

            if (log.isDebugEnabled())
                log.debug("Join request message has been sent (waiting for coordinator response).");

            synchronized (mux) {
                long threshold = System.currentTimeMillis() + netTimeout;

                long timeout = netTimeout;

                while (spiState != CONNECTED && timeout > 0)
                    try {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }
                    catch (InterruptedException e) {
                        throw new GridSpiException("Thread has been interrupted.", e);
                    }

                if (spiState == CONNECTED)
                    break;
                else
                    U.warn(log, "Discovery SPI has not been connected to topology (will repeat join process).");
            }
        }

        assert locNode.order() != 0;

        if (log.isDebugEnabled())
            log.debug("Discovery SPI has been connected to topology with order: " + locNode.order());
    }

    /**
     * Tries to send join request message to a random node presenting in topology.
     * Address is provided by {@link GridTcpDiscoveryIpFinder} and message is
     * sent to first node connection succeeded to.
     *
     * @return {@code true} if send succeeded.
     * @throws GridSpiException If any error occurs.
     */
    private boolean sendJoinRequestMessage() throws GridSpiException {
        GridTcpDiscoveryAbstractMessage joinReq = new GridTcpDiscoveryJoinRequestMessage(locNode);

        while (true) {
            List<InetSocketAddress> addrs = resolvedAddresses();

            if (addrs.isEmpty())
                return false;

            List<InetSocketAddress> shuffled = new ArrayList<InetSocketAddress>(addrs);

            // Shuffle addresses to send join request to different nodes.
            Collections.shuffle(shuffled);

            boolean retry = false;

            for (InetSocketAddress addr : shuffled)
                try {
                    Integer res = sendMessageDirectly(joinReq, addr, true);

                    assert res != null;

                    joinRequestResults.remove(addr);

                    switch (res) {
                        case RES_WAIT:
                            // Concurrent startup, try sending join request again or wait if no success.
                            retry = true;

                            break;
                        case RES_OK:
                            if (log.isDebugEnabled())
                                log.debug("Join request message has been sent to address: " + addr);

                            // Join request sending succeeded, wait for node connect message from coordinator.
                            return true;

                        default:
                            // Concurrent startup, try next node.
                            assert res == RES_CONTINUE_JOIN : "Unexpected response to join request: " + res;

                            break;
                    }
                }
                catch (GridSpiException e) {
                    if (log.isDebugEnabled())
                        log.debug("Failed to send join request message [toAddr=" + addr + ", msg=" + joinReq +
                            ", err=" + e.getMessage() + ']');

                    joinRequestResults.put(addr, 0);
                }

            if (retry) {
                if (log.isDebugEnabled())
                    log.debug("Concurrent discovery SPI start has been detected (local node should wait).");

                synchronized (mux) {
                    long threshold = System.currentTimeMillis() + netTimeout;

                    long timeout = netTimeout;

                    while (timeout > 0)
                        try {
                            mux.wait(timeout);

                            timeout = threshold - System.currentTimeMillis();
                        }
                        catch (InterruptedException e) {
                            throw new GridSpiException("Thread has been interrupted.", e);
                        }
                }
            }
            else if (!ipFinder.isShared() && !ipFinderHasLocAddr) {
                U.warn(log, "Failed to connect to any address from IP finder (local node should wait until one of " +
                    "the addresses responds): " + addrs);

                synchronized (mux) {
                    long threshold = System.currentTimeMillis() + netTimeout;

                    long timeout = 2000;

                    while (timeout > 0)
                        try {
                            mux.wait(timeout);

                            timeout = threshold - System.currentTimeMillis();
                        }
                        catch (InterruptedException e) {
                            throw new GridSpiException("Thread has been interrupted.", e);
                        }
                }
            }
            else
                break;
        }

        return false;
    }

    /**
     * Establishes connection to an address, sends message and returns the response
     * (if any).
     *
     * @param msg Message to send.
     * @param addr Address to send message to.
     * @param readRes Read response. If {@code true} response is read and returned,
     * otherwise {@code null} is returned.
     * @return Response read from the recipient or {@code null} if no response is supposed.
     * @throws GridSpiException If an error occurs.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    @Nullable private <T> T sendMessageDirectly(GridTcpDiscoveryAbstractMessage msg, InetSocketAddress addr,
        boolean readRes)
        throws GridSpiException {
        assert msg != null;
        assert addr != null;

        Exception err = null;

        Socket sock = null;

        for (int i = 0; i < reconCnt; i++)
            try {
                long tstamp = System.currentTimeMillis();

                sock = new Socket(addr.getAddress(), addr.getPort(), locHost, 0);

                // Handshake.
                U.writeUuid(new DataOutputStream(sock.getOutputStream()), locNodeId);

                UUID remoteNodeId = U.readUuid(new DataInputStream(sock.getInputStream()));

                stats.onClientSocketInitialized(System.currentTimeMillis() - tstamp);

                msg.senderNodeId(locNodeId);

                tstamp = System.currentTimeMillis();

                marsh.marshal(msg, sock.getOutputStream());

                if (log.isDebugEnabled())
                    log.debug("Message has been sent directly to address [msg=" + msg + ", addr=" + addr +
                        ", remoteNodeId=" + remoteNodeId + ']');

                T res = null;

                if (readRes) {
                    // Response is required.
                    InputStream in = sock.getInputStream();

                    res = marsh.<T>unmarshal(in, getClass().getClassLoader());

                    if (log.isDebugEnabled())
                        log.debug("Received response for message [res=" + res + ", msg=" + msg +
                            ", remoteAddr=" + addr + ']');
                }

                stats.onMessageSent(msg, System.currentTimeMillis() - tstamp);

                return res;
            }
            catch (IOException e) {
                if (err == null)
                    err = e;
            }
            catch (GridException e) {
                if (err == null)
                    err = e;
            }
            finally {
                U.closeQuiet(sock);
            }

        throw new GridSpiException("Failed to send message directly to address [addr=" + addr + ", msg=" + msg +
            ", err=" + err.getMessage() + ']', err);
    }

    /**
     * Notify external listener on discovery event.
     *
     * @param type Discovery event type. See {@link GridDiscoveryEvent} for more details.
     * @param node Remote node this event is connected with.
     */
    private void notifyDiscovery(int type, GridTcpDiscoveryNode node) {
        assert type > 0;
        assert node != null;

        GridDiscoverySpiListener lsnr = this.lsnr;

        if (lsnr != null && node.visible())
            lsnr.onDiscovery(type, node);
    }

    /**
     * Resolves addresses registered in the IP finder, removes duplicates and local host
     * address and returns the collection of.
     *
     * @return Resolved addresses without duplicates and local address (potentially
     * empty but never null).
     * @throws GridSpiException If an error occurs.
     */
    private List<InetSocketAddress> resolvedAddresses() throws GridSpiException {
        List<InetSocketAddress> res = new ArrayList<InetSocketAddress>();

        for (InetSocketAddress addr : registeredAddresses()) {
            assert addr != null;

            try {
                InetSocketAddress resolved = addr.isUnresolved() ?
                    new InetSocketAddress(InetAddress.getByName(addr.getHostName()), addr.getPort()) : addr;

                if ((!locHost.equals(resolved.getAddress()) || resolved.getPort() != tcpSrvr.port))
                    res.add(resolved);
            }
            catch (UnknownHostException e) {
                if (log.isDebugEnabled())
                    log.debug("Failed to resolve address [addr=" + addr + ", err=" + e.getMessage() + ']');
            }
        }

        return res;
    }

    /**
     * Gets addresses registered in the IP finder, initializes addresses having no
     * port (or 0 port) with {@link #getLocalPort()}.
     *
     * @return Registered addresses.
     * @throws GridSpiException If an error occurs.
     */
    private Collection<InetSocketAddress> registeredAddresses() throws GridSpiException {
        Collection<InetSocketAddress> res = new LinkedList<InetSocketAddress>();

        for (InetSocketAddress addr : ipFinder.getRegisteredAddresses()) {
            if (addr.getPort() == 0)
                addr = addr.isUnresolved() ? new InetSocketAddress(addr.getHostName(), locPort) :
                    new InetSocketAddress(addr.getAddress(), locPort);

            res.add(addr);
        }

        return res;
    }

    /**
     * Checks whether local node is coordinator. Nodes that are leaving or failed
     * (but are still in topology) are removed from search.
     *
     * @return {@code true} if local node is coordinator.
     */
    private boolean isLocalNodeCoordinator() {
        synchronized (mux) {
            boolean crd = spiState == CONNECTED && locNode.equals(resolveCoordinator());

            if (crd)
                stats.onBecomingCoordinator();

            return crd;
        }
    }

    /**
     * @return Spi state copy.
     */
    private GridTcpDiscoverySpiState spiStateCopy() {
        GridTcpDiscoverySpiState state;

        synchronized (mux) {
            state = spiState;
        }

        return state;
    }

    /**
     * Resolves coordinator. Nodes that are leaving or failed (but are still in
     * topology) are removed from search.
     *
     * @return Coordinator node or {@code null} if there are no coordinator
     * (i.e. local node is the last one and is currently stopping).
     */
    @Nullable private GridTcpDiscoveryNode resolveCoordinator() {
        return resolveCoordinator(null);
    }

    /**
     * Resolves coordinator. Nodes that are leaving or failed (but are still in
     * topology) are removed from search as well as provided filter.
     *
     * @param filter Nodes to exclude when resolving coordinator (optional).
     * @return Coordinator node or {@code null} if there are no coordinator
     * (i.e. local node is the last one and is currently stopping).
     */
    @Nullable private GridTcpDiscoveryNode resolveCoordinator(
        @Nullable Collection<GridTcpDiscoveryNode> filter) {
        synchronized (mux) {
            Collection<GridTcpDiscoveryNode> excluded = F.concat(false, failedNodes, leavingNodes);

            if (!F.isEmpty(filter))
                excluded = F.concat(false, excluded, filter);

            return ring.coordinator(excluded);
        }
    }

    /**
     * Prints SPI statistics.
     */
    private void printStatistics() {
        if (log.isInfoEnabled() && !log.isQuiet() && statsPrintFreq > 0)
            log.info("Discovery SPI statistics [statistics=" + stats + ", spiState=" + spiStateCopy() +
                ", topSize=" + ring.allNodes().size() +
                ", msgWorker.queue.size=" + msgWorker.queue.size() +
                ", lastUpdate=" + locNode.lastUpdateTime() + ']');
    }

    /**
     * <strong>FOR TEST ONLY!!!</strong>
     * <p>
     * Simulates this node failure by stopping service threads. So, node will become
     * unresponsive.
     * <p>
     * This method is intended for test purposes only.
     */
    void simulateNodeFailure() {
        U.warn(log, "Simulating node failure: " + locNodeId);

        U.interrupt(tcpSrvr);
        U.join(tcpSrvr, log);

        U.interrupt(hbsSnd);
        U.join(hbsSnd, log);

        U.interrupt(chkStatusSnd);
        U.join(chkStatusSnd, log);

        U.interrupt(storesCleaner);
        U.join(storesCleaner, log);

        U.interrupt(metricsUpdateNtf);
        U.join(metricsUpdateNtf, log);

        Collection<SocketReader> tmp;

        synchronized (mux) {
            tmp = new ArrayList<SocketReader>(readers);
        }

        U.interrupt(tmp);
        U.joinThreads(tmp, log);

        U.interrupt(msgWorker);
        U.join(msgWorker, log);

        U.interrupt(statsPrinter);
        U.join(statsPrinter, log);
    }

    /**
     * <strong>FOR TEST ONLY!!!</strong>
     * <p>
     * This method is intended for test purposes only.
     *
     * @param msg Message.
     */
    void onBeforeMessageSentAcrossRing(Serializable msg) {
        // No-op.
    }

    /**
     * <strong>FOR TEST ONLY!!!</strong>
     * <p>
     * This method is intended for test purposes only.
     *
     * @return Nodes ring.
     */
    GridTcpDiscoveryNodesRing ring() {
        return ring;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoverySpi.class, this);
    }

    /**
     * Thread that sends heartbeats.
     */
    private class HeartbeatsSender extends GridSpiThread {
        /**
         * Constructor.
         */
        private HeartbeatsSender() {
            super(gridName, "tcp-disco-metric-sender", log);

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            synchronized (mux) {
                while (!isLocalNodeCoordinator())
                    mux.wait(hbFreq);
            }

            if (log.isDebugEnabled())
                log.debug("Heartbeats sender has been started.");

            while (!isInterrupted()) {
                GridTcpDiscoveryHeartbeatMessage msg = new GridTcpDiscoveryHeartbeatMessage(locNodeId);

                if (topStore != null)
                    msg.topologyVersion(topVer.get());

                msgWorker.addMessage(msg);

                synchronized (mux) {
                    long threshold = System.currentTimeMillis() + hbFreq;

                    long timeout = hbFreq;

                    while (timeout > 0) {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }

                    if (spiState != CONNECTED) {
                        if (log.isDebugEnabled())
                            log.debug("Stopping heartbeats sender (SPI is not connected to topology).");

                        return;
                    }
                }
            }
        }
    }

    /**
     * Thread that sends status check messages to next node if local node has not
     * been receiving heartbeats ({@link GridTcpDiscoveryHeartbeatMessage})
     * for {@link GridTcpDiscoverySpi#getMaxMissedHeartbeats()} *
     * {@link GridTcpDiscoverySpi#getHeartbeatFrequency()}.
     */
    private class CheckStatusSender extends GridSpiThread {
        /**
         * Constructor.
         */
        private CheckStatusSender() {
            super(gridName, "tcp-disco-status-check-sender", log);

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            if (log.isDebugEnabled())
                log.debug("Status check sender has been started.");

            long checkTimeout = (long) maxMissedHbs * hbFreq;

            long lastSent = 0;

            while (!isInterrupted()) {
                if (lastSent < locNode.lastUpdateTime())
                    lastSent = locNode.lastUpdateTime();

                synchronized (mux) {
                    long threshold = lastSent + checkTimeout;

                    long timeout = threshold - System.currentTimeMillis();

                    while (timeout > 0) {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }

                    if (spiState != CONNECTED) {
                        if (log.isDebugEnabled())
                            log.debug("Stopping status check sender (SPI is not connected to topology).");

                        return;
                    }
                }

                if (locNode.lastUpdateTime() > lastSent || !ring.hasRemoteNodes())
                    continue;

                lastSent = System.currentTimeMillis();

                msgWorker.addMessage(new GridTcpDiscoveryStatusCheckMessage(locNode));
            }
        }
    }

    /**
     * Thread that cleans SPI stores (IP finder and metrics store) and keeps them in
     * the correct state, unregistering addresses and metrics of the nodes that has
     * left the topology.
     * <p>
     * This thread should run only on coordinator node and will clean IP finder
     * if and only if {@link GridTcpDiscoveryIpFinder#isShared()} is {@code true}.
     */
    private class StoresCleaner extends GridSpiThread {
        /**
         * Constructor.
         */
        private StoresCleaner() {
            super(gridName, "tcp-disco-stores-cleaner", log);

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            synchronized (mux) {
                while (!isLocalNodeCoordinator())
                    mux.wait(netTimeout);
            }

            if (log.isDebugEnabled())
                log.debug("IP finder cleaner has been started.");

            while (!isInterrupted()) {
                synchronized (mux) {
                    long threshold = System.currentTimeMillis() + storesCleanFreq;

                    long timeout = storesCleanFreq;

                    while (timeout > 0) {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }

                    if (spiState != CONNECTED) {
                        if (log.isDebugEnabled())
                            log.debug("Stopping IP finder cleaner (SPI is not connected to topology).");

                        return;
                    }
                }

                if (ipFinder.isShared())
                    cleanIpFinder();

                if (metricsStore != null)
                    cleanMetricsStore();
            }
        }

        /**
         * Cleans IP finder.
         */
        private void cleanIpFinder() {
            assert ipFinder.isShared();

            try {
                Collection<InetSocketAddress> currAddrs = F.viewReadOnly(ring.allNodes(),
                    new C1<GridTcpDiscoveryNode, InetSocketAddress>() {
                        @Override public InetSocketAddress apply(GridTcpDiscoveryNode node) {
                            return node.address();
                        }
                    });

                // Remove all addresses that belong to alive nodes.
                Collection<InetSocketAddress> addrs = F.view(registeredAddresses(),
                    F.notContains(currAddrs),
                    new P1<InetSocketAddress>() {
                        private final Map<InetSocketAddress, Boolean> pingResMap =
                            new HashMap<InetSocketAddress, Boolean>();

                        @Override public boolean apply(InetSocketAddress addr) {
                            Boolean res = pingResMap.get(addr);

                            if (res == null)
                                try {
                                    res = pingNode(addr) != null;
                                }
                                catch (GridSpiException e) {
                                    if (log.isDebugEnabled())
                                        log.debug("Failed to ping node [addr=" + addr +
                                            ", err=" + e.getMessage() + ']');

                                    res = false;
                                }
                                finally {
                                    pingResMap.put(addr, res);
                                }

                            return !res;
                        }
                    });

                if (!addrs.isEmpty()) {
                    ipFinder.unregisterAddresses(addrs);

                    if (log.isDebugEnabled())
                       log.debug("Unregistered addresses from IpFinder: " + addrs);
                }
            }
            catch (GridSpiException e) {
                U.error(log, "Failed to clean IpFinder up.", e);
            }
        }

        /**
         * Cleans metrics store.
         */
        private void cleanMetricsStore() {
            assert metricsStore != null;

            try {
                Collection<UUID> ids = F.view(metricsStore.allNodeIds(), F.notContains(
                    F.viewReadOnly(ring.allNodes(), F.node2id())));

                if (!ids.isEmpty())
                    metricsStore.removeMetrics(ids);
            }
            catch (GridSpiException e) {
                U.error(log, "Failed to clean metrics store up.", e);
            }
        }
    }

    /**
     * Thread that disconnects SPI then tries to reconnect back following join process.
     */
    private class SpiReconnectThread extends GridSpiThread {
        /**
         * Constructor.
         */
        private SpiReconnectThread() {
            super(gridName, "tcp-disco-spi-reconnect-thread", log);

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            if (log.isDebugEnabled())
                log.debug("Discovery SPI reconnect thread has been started.");

            String errMsg = null;

            // Check whether network segment is correct and reconnect Spi if correct.
            while (!isInterrupted()) {
                try {
                    if (checkSegEnabled)
                        checkSegment();

                    reconnectSpi();

                    break;
                }
                catch (GridSpiException e) {
                    if (errMsg == null || !errMsg.equalsIgnoreCase(e.getMessage())) {
                        U.error(log, "Failed to reconnect discovery SPI to topology.", e);

                        errMsg = e.getMessage();
                    }
                }

                synchronized (mux) {
                    long threshold = System.currentTimeMillis() + netTimeout;

                    long timeout = netTimeout;

                    while (timeout > 0) {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }
                }
            }
        }

        /**
         * Reconnects SPI to topology.
         *
         * @throws GridSpiException If join process fails.
         */
        private void reconnectSpi() throws GridSpiException {
            if (log.isDebugEnabled())
                log.debug("Trying to reconnect discovery SPI to topology.");

            joinTopology();

            hbsSnd = new HeartbeatsSender();
            hbsSnd.start();

            chkStatusSnd = new CheckStatusSender();
            chkStatusSnd.start();

            if (metricsStore != null) {
                metricsUpdateNtf = new MetricsUpdateNotifier();
                metricsUpdateNtf.start();
            }

            if (ipFinder.isShared() || metricsStore != null) {
                storesCleaner = new StoresCleaner();
                storesCleaner.start();
            }

            notifyDiscovery(EVT_NODE_RECONNECTED, locNode);
        }
    }

    /**
     * Message worker thread for messages processing.
     */
    private class MessageWorker extends GridSpiThread {
        /** Socket to next node. */
        private Socket nextNodeSock;

        /** Next node. */
        @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
        private GridTcpDiscoveryNode next;

        /** First queue message gets in. */
        private final BlockingDeque<GridTcpDiscoveryAbstractMessage> queue =
            new LinkedBlockingDeque<GridTcpDiscoveryAbstractMessage>();

        /** Pending messages. */
        private final Map<GridUuid, GridTcpDiscoveryAbstractMessage> pendingMsgs =
            new LinkedHashMap<GridUuid, GridTcpDiscoveryAbstractMessage>();

        /** Last evicted topology version. */
        private long lastEvictedTopVer;

        /** Max topology version received from the store. */
        private long maxTopVerRcvd;

        /** Constructor. */
        private MessageWorker() {
            super(gridName, "tcp-disco-msg-wrk", log);

            setPriority(threadPri);
        }

        /**
         * Adds message to queue.
         *
         * @param msg Message to add.
         */
        void addMessage(GridTcpDiscoveryAbstractMessage msg) {
            assert msg != null;

            if (msg instanceof GridTcpDiscoveryHeartbeatMessage)
                queue.addFirst(msg);

            else
                queue.add(msg);

            if (log.isDebugEnabled())
                log.debug("Message has been added to queue: " + msg);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            while (!isInterrupted() || queue.peek() != null) {
                GridTcpDiscoveryAbstractMessage msg = queue.take();

                if (log.isDebugEnabled())
                    log.debug("Processing message: " + msg);

                stats.onMessageProcessingStarted(msg);

                if (msg instanceof GridTcpDiscoveryJoinRequestMessage)
                    processJoinRequestMessage((GridTcpDiscoveryJoinRequestMessage)msg);

                else if (msg instanceof GridTcpDiscoveryNodeAddedMessage)
                    processNodeAddedMessage((GridTcpDiscoveryNodeAddedMessage)msg);

                else if (msg instanceof GridTcpDiscoveryNodeAddFinishedMessage)
                    processNodeAddFinishedMessage((GridTcpDiscoveryNodeAddFinishedMessage)msg);

                else if (msg instanceof GridTcpDiscoveryNodeLeftMessage)
                    processNodeLeftMessage((GridTcpDiscoveryNodeLeftMessage)msg);

                else if (msg instanceof GridTcpDiscoveryNodeFailedMessage)
                    processNodeFailedMessage((GridTcpDiscoveryNodeFailedMessage)msg);

                else if (msg instanceof GridTcpDiscoveryHeartbeatMessage) {
                    if (metricsStore != null)
                        processHeartbeatMessageMetricsStore((GridTcpDiscoveryHeartbeatMessage)msg);
                    else
                        processHeartbeatMessage((GridTcpDiscoveryHeartbeatMessage)msg);
                }
                else if (msg instanceof GridTcpDiscoveryStatusCheckMessage)
                    processStatusCheckMessage((GridTcpDiscoveryStatusCheckMessage)msg);

                else if (msg instanceof GridTcpDiscoveryDiscardMessage)
                    processDiscardMessage((GridTcpDiscoveryDiscardMessage)msg);

                else if (msg instanceof GridTcpDiscoveryUpdateTopologyMessage)
                    processUpdateTopologyMessage((GridTcpDiscoveryUpdateTopologyMessage)msg);

                else
                    assert false : "Unknown message type: " + msg.getClass().getSimpleName();

                stats.onMessageProcessingFinished(msg);
            }
        }

        /** {@inheritDoc} */
        @Override protected void cleanup() {
            super.cleanup();

            U.closeQuiet(nextNodeSock);
        }

        /**
         * Sends message across the ring.
         *
         * @param msg Message to send
         *
         */
        private void sendMessageAcrossRing(GridTcpDiscoveryAbstractMessage msg) {
            assert msg != null;

            assert ring.hasRemoteNodes();

            onBeforeMessageSentAcrossRing(msg);

            Collection<GridTcpDiscoveryNode> failedNodes;

            GridTcpDiscoverySpiState state;

            synchronized (mux) {
                failedNodes = new ArrayList<GridTcpDiscoveryNode>(GridTcpDiscoverySpi.this.failedNodes);

                state = spiState;
            }

            Exception err = null;

            boolean sent = false;

            boolean searchNext = true;

            while (true) {
                if (searchNext) {
                    GridTcpDiscoveryNode newNext = ring.nextNode(failedNodes);

                    if (log.isDebugEnabled())
                        log.debug("Finished searching new next [newNext=" + newNext + ", formerNext=" + next + ']');

                    if (newNext == null)
                        break;

                    if (!newNext.equals(next)) {
                        U.closeQuiet(nextNodeSock);

                        nextNodeSock = null;

                        next = newNext;
                    }
                }

                // Flag that shows whether next node exists and accepts incoming connections.
                boolean nextNodeExists = false;

                for (int i = 0; i < reconCnt; i++) {
                    if (nextNodeSock == null) {
                        // Restore ring.
                        try {
                            long tstamp = System.currentTimeMillis();

                            nextNodeSock = new Socket(next.address().getAddress(), next.address().getPort(),
                                locHost, 0);

                            // Handshake.
                            U.writeUuid(new DataOutputStream(nextNodeSock.getOutputStream()), locNodeId);

                            UUID nextId = U.readUuid(new DataInputStream(nextNodeSock.getInputStream()));

                            stats.onClientSocketInitialized(System.currentTimeMillis() - tstamp);

                            if (nextId == null || !next.id().equals(nextId)) {
                                U.warn(log, "Failed to restore ring because next node ID received is not as expected " +
                                    "[expectedId=" + next.id() + ", rcvdId=" + nextId + ']');

                                U.closeQuiet(nextNodeSock);

                                nextNodeSock = null;

                                break;
                            }
                            else {
                                if (log.isDebugEnabled())
                                    log.debug("Initialized connection with next node: " + next);

                                // Next node exists.
                                nextNodeExists = true;

                                err = null;
                            }
                        }
                        catch (IOException e) {
                            if (err == null)
                                err = e;

                            U.closeQuiet(nextNodeSock);

                            nextNodeSock = null;

                            continue;
                        }
                    }

                    try {
                        OutputStream out = nextNodeSock.getOutputStream();

                        boolean failure;

                        synchronized (mux) {
                            failure = GridTcpDiscoverySpi.this.failedNodes.size() < failedNodes.size();
                        }

                        boolean sendPending = true;

                        if (msg instanceof GridTcpDiscoveryNodeAddedMessage) {
                            GridTcpDiscoveryNodeAddedMessage nodeAddedMsg =
                                (GridTcpDiscoveryNodeAddedMessage)msg;

                            assert topStore == null;

                            // If new node is next, then send topology to and all pending messages
                            // as a part of message.
                            if (nodeAddedMsg.node().equals(next)) {
                                nodeAddedMsg.topology(F.view(ring.allNodes(), F.notEqualTo(nodeAddedMsg.node())));

                                nodeAddedMsg.topologyVersion(ring.currentVersion());

                                nodeAddedMsg.messages(pendingMsgs.values());

                                sendPending = false;
                            }
                        }

                        if (failure && sendPending)
                            for (GridTcpDiscoveryAbstractMessage pendingMsg : pendingMsgs.values()) {
                                if (pendingMsg instanceof GridTcpDiscoveryNodeAddedMessage) {
                                    final GridTcpDiscoveryNodeAddedMessage nodeAddedMsg =
                                        (GridTcpDiscoveryNodeAddedMessage)pendingMsg;

                                    // If new node is next, we don't know whether pending node added message
                                    // reached it, we should send topology (all preceding nodes) and
                                    // all pending messages as a part of message.
                                    if (nodeAddedMsg.node().equals(next)) {
                                        nodeAddedMsg.topology(F.view(ring.allNodes(),
                                            new P1<GridTcpDiscoveryNode>() {
                                                @Override public boolean apply(GridTcpDiscoveryNode n) {
                                                    return n.order() < nodeAddedMsg.node().order();
                                                }
                                            }));

                                        nodeAddedMsg.topologyVersion(ring.currentVersion());

                                        nodeAddedMsg.messages(pendingMsgs.values());
                                    }
                                }

                                pendingMsg.senderNodeId(locNodeId);

                                long tstamp = System.currentTimeMillis();

                                marsh.marshal(pendingMsg, out);

                                stats.onMessageSent(pendingMsg, System.currentTimeMillis() - tstamp);
                            }

                        msg.senderNodeId(locNodeId);

                        long tstamp = System.currentTimeMillis();

                        marsh.marshal(msg, out);

                        stats.onMessageSent(msg, System.currentTimeMillis() - tstamp);

                        if (log.isDebugEnabled())
                            log.debug("Message has been sent to next node [msg=" + msg + ", next=" + next + ']');

                        registerPendingMessage(msg);

                        sent = true;

                        break;
                    }
                    catch (IOException e) {
                        if (err == null)
                            err = e;
                    }
                    catch (GridException e) {
                        if (err == null)
                            err = e;
                    }
                    finally {
                        if (!sent) {
                            U.closeQuiet(nextNodeSock);

                            nextNodeSock = null;
                        }
                    }
                }

                if (!sent) {
                    if (topStore != null) {
                        try {
                            long tstamp = System.currentTimeMillis();

                            GridTcpDiscoveryTopologyStoreNodeState nextNodeState = topStore.state(next.id());

                            stats.onTopologyStoreGetNodeState(System.currentTimeMillis() - tstamp);

                            if (log.isDebugEnabled())
                                log.debug("Checked next node state in topology store [next=" + next +
                                    ", state=" + nextNodeState + ']');

                            if (nextNodeState == FAILED || nextNodeState == LEAVING) {
                                synchronized (mux) {
                                    GridTcpDiscoverySpi.this.failedNodes.add(next);
                                }

                                failedNodes.add(next);
                            }
                        }
                        catch (GridSpiException e) {
                            U.error(log, "Failed to get node state from topology store: " + next.id(), e);
                        }
                    }

                    if (!failedNodes.contains(next)) {
                        failedNodes.add(next);

                        if (err != null && state == CONNECTED)
                            // If node existed on connection initialization we should check
                            // whether it has not gone yet.
                            if (nextNodeExists && pingNode(next))
                                U.error(log, "Failed to send message to next node [msg=" + msg +
                                    ", next=" + next + ']', err);
                            else
                                if (log.isDebugEnabled())
                                    log.debug("Failed to send message to next node [msg=" + msg + ", next=" + next +
                                        ", errMsg" + err.getMessage() + ']');
                    }

                    searchNext = true;

                    next = null;

                    err = null;
                }
                else
                    break;
            }

            synchronized (mux) {
                failedNodes.removeAll(GridTcpDiscoverySpi.this.failedNodes);
            }

            if (!failedNodes.isEmpty()) {
                if (state == CONNECTED) {
                    if (!sent && log.isDebugEnabled())
                        // Message has not been sent due to some problems.
                        log.debug("Message has not been sent: " + msg);

                    if (log.isDebugEnabled())
                        log.debug("Detected failed nodes: " + failedNodes);
                }

                synchronized (mux) {
                    GridTcpDiscoverySpi.this.failedNodes.addAll(failedNodes);
                }

                msgWorker.addMessage(new GridTcpDiscoveryNodeFailedMessage(locNodeId, F.viewReadOnly(failedNodes,
                    F.node2id())));
            }
        }

        /**
         * Registers pending message.
         *
         * @param msg Message to register.
         */
        private void registerPendingMessage(GridTcpDiscoveryAbstractMessage msg) {
            assert msg != null;

            if (U.getAnnotation(msg.getClass(), GridTcpDiscoveryEnsureDelivery.class) != null) {
                // Remove then put again to manage order.
                GridTcpDiscoveryAbstractMessage prev = pendingMsgs.remove(msg.id());

                pendingMsgs.put(msg.id(), msg);

                if (prev == null) {
                    stats.onPendingMessageRegistered();

                    if (log.isDebugEnabled())
                        log.debug("Pending message has been registered: " + msg.id());
                }
            }
        }

        /**
         * Processes join request message.
         *
         * @param msg Join request message.
         */
        private void processJoinRequestMessage(GridTcpDiscoveryJoinRequestMessage msg) {
            assert msg != null;

            if (isLocalNodeCoordinator()) {
                GridTcpDiscoveryNode node = msg.node();

                if (ring.node(node.id()) != null) {
                    if (log.isDebugEnabled())
                        log.debug("Received join request from node that is already in topology (will ignore): " + msg);

                    return;
                }

                long topVer = ring.incrementCurrentVersion();

                if (topStore == null) {
                    node.order(topVer);

                    processNodeAddedMessage(new GridTcpDiscoveryNodeAddedMessage(locNodeId, node));
                }
                else
                    try {
                        long tstamp = System.currentTimeMillis();

                        GridTcpDiscoveryTopologyStoreNodeState state = topStore.state(node.id());

                        stats.onTopologyStoreGetNodeState(System.currentTimeMillis() - tstamp);

                        if (state == null) {
                            // Node has not yet been added to topology store.
                            node.order(topVer);

                            node.state(ONLINE);

                            tstamp = System.currentTimeMillis();

                            topStore.put(node);

                            stats.onTopologyStoreNodePut(System.currentTimeMillis() - tstamp);
                        }

                        addMessage(new GridTcpDiscoveryUpdateTopologyMessage(locNodeId));
                    }
                    catch (GridSpiException e) {
                        U.error(log, "Failed to put node to topology store: " + node, e);
                    }
            }
            else if (ring.hasRemoteNodes())
                sendMessageAcrossRing(msg);
        }

        /**
         * Processes node added message.
         *
         * @param msg Node added message.
         */
        private void processNodeAddedMessage(GridTcpDiscoveryNodeAddedMessage msg) {
            assert msg != null;

            GridTcpDiscoveryNode node = msg.node();

            assert node != null;

            if (isLocalNodeCoordinator() && topStore == null) {
                if (msg.verified()) {
                    stats.onRingMessageReceived(msg);

                    addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                    addMessage(new GridTcpDiscoveryNodeAddFinishedMessage(locNodeId, node.id()));

                    return;
                }

                msg.verify(locNodeId);
            }

            if (msg.verified() && !locNodeId.equals(node.id())) {
                if (metricsStore != null) {
                    node.metricsStore(metricsStore);

                    node.logger(log);
                }

                boolean topChanged = ring.add(node);

                if (!topChanged && log.isDebugEnabled())
                    log.debug("Node has not been added to topology [node=" + node + ", ring=" + ring + ']');

                if (topChanged && topStore != null) {
                    assert topVer.get() < msg.topologyVersion();

                    topVer.set(msg.topologyVersion());
                }
            }

            if (msg.verified() && locNodeId.equals(node.id())) {
                synchronized (mux) {
                    if (spiState == CONNECTING) {
                        if (topStore != null) {
                            for (GridTcpDiscoveryNode n : ring.remoteNodes()) {
                                if (metricsStore != null) {
                                    n.metricsStore(metricsStore);

                                    n.logger(log);
                                }

                                // Make all preceding nodes and local node visible.
                                n.visible(true);
                            }

                            locNode.visible(true);

                            // Artificial "restore" topology to order nodes in the ring.
                            ring.restoreTopology(ring.remoteNodes(), msg.node().order());

                            if (log.isDebugEnabled())
                                log.debug("Restored topology: " + ring);

                            assert topVer.get() < msg.topologyVersion();

                            topVer.set(msg.topologyVersion());

                            locNode.topologyVersion(msg.topologyVersion());
                        }
                        else {
                            // Initialize topology.
                            Collection<GridTcpDiscoveryNode> top = msg.topology();

                            assert top != null;
                            assert !top.isEmpty();
                            assert msg.topologyVersion() > 0;

                            for (GridTcpDiscoveryNode n : top) {
                                if (metricsStore != null) {
                                    n.metricsStore(metricsStore);

                                    n.logger(log);
                                }

                                // Make all preceding nodes and local node visible.
                                n.visible(true);
                            }

                            locNode.visible(true);

                            // Restore topology with all nodes visible.
                            ring.restoreTopology(top, msg.topologyVersion());

                            if (log.isDebugEnabled())
                                log.debug("Restored topology from node added message: " + ring);

                            // Initialize pending messages using info from previous node.
                            Collection<GridTcpDiscoveryAbstractMessage> msgs = msg.messages();

                            if (msgs != null && !msgs.isEmpty())
                                for (GridTcpDiscoveryAbstractMessage m : msgs)
                                    registerPendingMessage(m);

                            // Clear data to minimize message size.
                            msg.messages(null);

                            msg.topology(null);
                        }
                    }
                }
            }

            if (ring.hasRemoteNodes() && topStore == null)
                sendMessageAcrossRing(msg);
        }

        /**
         * Processes node add finished message.
         *
         * @param msg Node add finished message.
         */
        private void processNodeAddFinishedMessage(GridTcpDiscoveryNodeAddFinishedMessage msg) {
            assert msg != null;

            UUID nodeId = msg.nodeId();

            assert nodeId != null;

            if (isLocalNodeCoordinator()) {
                if (msg.verified()) {
                    stats.onRingMessageReceived(msg);

                    addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                    return;
                }

                msg.verify(locNodeId);
            }

            GridTcpDiscoveryNode node = ring.node(nodeId);

            boolean fireEvent = false;

            if (node != null && !node.visible()) {
                node.visible(true);

                fireEvent = true;
            }

            if (msg.verified() && !locNodeId.equals(nodeId) && spiStateCopy() == CONNECTED && fireEvent)
                onNodeAdded(node);

            if (msg.verified() && locNodeId.equals(nodeId) && spiStateCopy() == CONNECTING) {
                assert node != null;

                synchronized (mux) {
                    spiState = CONNECTED;

                    mux.notifyAll();
                }
            }

            if (ring.hasRemoteNodes())
                sendMessageAcrossRing(msg);
        }


        /**
         * Processes node left message.
         *
         * @param msg Node left message.
         */
        private void processNodeLeftMessage(GridTcpDiscoveryNodeLeftMessage msg) {
            assert msg != null;

            UUID leavingNodeId = msg.creatorNodeId();

            if (locNodeId.equals(leavingNodeId)) {
                if (msg.senderNodeId() == null) {
                    synchronized (mux) {
                        if (log.isDebugEnabled())
                            log.debug("Starting local node stop procedure.");

                        spiState = STOPPING;

                        mux.notifyAll();
                    }

                    if (!ipFinder.isShared() || !ring.hasRemoteNodes())
                        try {
                            ipFinder.unregisterAddresses(Arrays.asList(locNode.address()));
                        }
                        catch (GridSpiException e) {
                            U.error(log, "Failed to unregister local node address from IP finder.", e);
                        }

                    if (metricsStore != null && !ring.hasRemoteNodes())
                        try {
                            metricsStore.removeMetrics(Arrays.asList(locNodeId));
                        }
                        catch (GridSpiException e) {
                            U.error(log, "Failed to remove local node metrics from metrics store.", e);
                        }
                }

                if (msg.verified() || !ring.hasRemoteNodes()) {
                    synchronized (mux) {
                        if (spiState == STOPPING) {
                            spiState = LEFT;

                            mux.notifyAll();
                        }
                    }

                    return;
                }
            }

            GridTcpDiscoveryNode leavingNode = ring.node(leavingNodeId);

            if (leavingNode != null)
                synchronized (mux) {
                    leavingNodes.add(leavingNode);
                }

            if (isLocalNodeCoordinator()) {
                if (topStore != null) {
                    if (!msg.verified()) {
                        GridTcpDiscoveryNode leftNode = ring.node(leavingNodeId);

                        if (leftNode != null) {
                            try {
                                leftNode.state(LEAVING);

                                // Empty attributes to make node thin.
                                leftNode.setAttributes(Collections.<String, Object>emptyMap());

                                long tstamp = System.currentTimeMillis();

                                topStore.put(leftNode);

                                stats.onTopologyStoreNodePut(System.currentTimeMillis() - tstamp);

                                addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                                addMessage(new GridTcpDiscoveryUpdateTopologyMessage(locNodeId));
                            }
                            catch (GridSpiException e) {
                                U.error(log, "Failed to update left node in topology store: " + leftNode, e);
                            }
                        }

                        return;
                    }
                }
                else {
                    if (msg.verified()) {
                        stats.onRingMessageReceived(msg);

                        addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                        return;
                    }

                    msg.verify(locNodeId);
                }
            }

            if (msg.verified() && !locNodeId.equals(leavingNodeId)) {
                GridTcpDiscoveryNode leftNode = ring.removeNode(leavingNodeId);

                if (leftNode != null) {
                    if (leftNode.equals(next) && topStore == null)
                        try {
                            msg.senderNodeId(locNodeId);

                            marsh.marshal(msg, nextNodeSock.getOutputStream());

                            if (log.isDebugEnabled())
                                log.debug("Sent verified node left message to leaving node: " + msg);
                        }
                        catch (GridException e) {
                            if (log.isDebugEnabled())
                                log.debug("Failed to send verified node left message to leaving node [msg=" + msg +
                                    ", err=" + e.getMessage() + ']');
                        }
                        catch (IOException e) {
                            if (log.isDebugEnabled())
                                log.debug("Failed to send verified node left message to leaving node [msg=" + msg +
                                    ", err=" + e.getMessage() + ']');
                        }

                    if (spiStateCopy() == CONNECTED)
                        onNodeLeft(leftNode);

                    if (topStore != null) {
                        assert topVer.get() < msg.topologyVersion();

                        topVer.set(msg.topologyVersion());
                    }
                }
            }

            if (ring.hasRemoteNodes()) {
                if (topStore == null || !msg.verified())
                    sendMessageAcrossRing(msg);
            }
            else {
                if (log.isDebugEnabled())
                    log.debug("Unable to send message across the ring (topology has no remote nodes): " + msg);

                U.closeQuiet(nextNodeSock);
            }
        }

        /**
         * Processes node failed message.
         *
         * @param msg Node failed message.
         */
        private void processNodeFailedMessage(GridTcpDiscoveryNodeFailedMessage msg) {
            assert msg != null;

            Collection<UUID> nodeIds = msg.failedNodesIds();

            assert nodeIds != null && !nodeIds.isEmpty();

            synchronized (mux) {
                for (UUID id : nodeIds) {
                    GridTcpDiscoveryNode node = ring.node(id);

                    if (node != null)
                        failedNodes.add(node);
                }
            }

            if (isLocalNodeCoordinator()) {
                if (topStore != null) {
                    if (!msg.verified()) {
                        for (UUID id : nodeIds) {
                            GridTcpDiscoveryNode failedNode = ring.node(id);

                            if (failedNode != null)
                                try {
                                    failedNode.state(FAILED);

                                    // Empty attributes to make node thin.
                                    failedNode.setAttributes(Collections.<String, Object>emptyMap());

                                    long tstamp = System.currentTimeMillis();

                                    topStore.put(failedNode);

                                    stats.onTopologyStoreNodePut(System.currentTimeMillis() - tstamp);
                                }
                                catch (GridSpiException e) {
                                    U.error(log, "Failed to update failed node in topology store: " + failedNode, e);
                                }
                        }

                        addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                        addMessage(new GridTcpDiscoveryUpdateTopologyMessage(locNodeId));

                        // Message will be added from topology store worker.
                        return;
                    }
                }
                else {
                    if (msg.verified()) {
                        stats.onRingMessageReceived(msg);

                        addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                        return;
                    }

                    msg.verify(locNodeId);
                }
            }

            if (msg.verified()) {
                Collection<GridTcpDiscoveryNode> nodes = ring.removeNodes(nodeIds);

                if (!nodes.isEmpty()) {
                    if (spiStateCopy() == CONNECTED)
                        onNodesFailed(nodes);

                    if (topStore != null) {
                        assert topVer.get() < msg.topologyVersion();

                        topVer.set(msg.topologyVersion());
                    }
                }
            }

            if (ring.hasRemoteNodes()) {
                if (topStore == null || !msg.verified())
                    sendMessageAcrossRing(msg);
            }
            else {
                if (log.isDebugEnabled())
                    log.debug("Unable to send message across the ring (topology has no remote nodes): " + msg);

                U.closeQuiet(nextNodeSock);
            }
        }

        /**
         * Processes status check message.
         *
         * @param msg Status check message.
         */
        private void processStatusCheckMessage(GridTcpDiscoveryStatusCheckMessage msg) {
            assert msg != null;

            if (isLocalNodeCoordinator() && !locNodeId.equals(msg.creatorNodeId())) {
                // Local node is real coordinator, it should respond and discard message.
                if (ring.node(msg.creatorNodeId()) != null) {
                    // Sender is in topology, send message via ring.
                    msg.status(STATUS_OK);

                    sendMessageAcrossRing(msg);
                }
                else {
                    // Sender is not in topology, it should reconnect.
                    msg.status(STATUS_RECONNECT);

                    try {
                        sendMessageDirectly(msg, msg.creatorNode().address(), false);

                        if (log.isDebugEnabled())
                            log.debug("Responded to status check message " +
                                "[recipient=" + msg.creatorNodeId() + ", status=" + msg.status() + ']');
                    }
                    catch (GridSpiException e) {
                        if (e.hasCause(ConnectException.class)) {
                            if (log.isDebugEnabled()) {
                                log.debug("Failed to respond to status check message (connection refused) " +
                                    "[recipient=" + msg.creatorNodeId() + ", status=" + msg.status() + ']');
                            }
                        }
                        else {
                            if (pingNode(msg.creatorNode())) {
                                // Node exists and accepts incoming connections.
                                U.error(log, "Failed to respond to status check message " +
                                    "[recipient=" + msg.creatorNodeId() + ", status=" + msg.status() + ']', e);
                            }
                            else if (log.isDebugEnabled()) {
                                log.debug("Failed to respond to status check message (did the node stop?) " +
                                    "[recipient=" + msg.creatorNodeId() + ", status=" + msg.status() + ']');
                            }
                        }
                    }
                }

                return;
            }

            if (locNodeId.equals(msg.creatorNodeId()) && msg.senderNodeId() == null && spiStateCopy() != CONNECTED)
                return;

            if (locNodeId.equals(msg.creatorNodeId()) && msg.senderNodeId() != null) {
                if (spiStateCopy() != CONNECTED)
                    return;

                if (msg.status() == STATUS_OK) {
                    if (log.isDebugEnabled())
                        log.debug("Received OK status response from coordinator: " + msg);
                }
                else if (msg.status() == STATUS_RECONNECT) {
                    U.warn(log, "Received reconnect request from coordinator (will reconnect to grid): " + msg);

                    startSpiReconnectThread();
                }
                else
                    if (log.isDebugEnabled())
                        log.debug("Status value was not updated in status response: " + msg);

                // Discard the message.
                return;
            }

            if (ring.hasRemoteNodes())
                sendMessageAcrossRing(msg);
        }

        /**
         * Processes regular heartbeat message.
         *
         * @param msg Heartbeat message.
         */
        private void processHeartbeatMessage(GridTcpDiscoveryHeartbeatMessage msg) {
            assert msg != null;

            if (ring.node(msg.creatorNodeId()) == null) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by unknown node [msg=" + msg +
                        ", ring=" + ring + ']');

                return;
            }

            if (isLocalNodeCoordinator() && !locNodeId.equals(msg.creatorNodeId())) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by non-coordinator node: " + msg);

                return;
            }

            if (!isLocalNodeCoordinator() && locNodeId.equals(msg.creatorNodeId())) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by local node (node is no more coordinator): " +
                        msg);

                return;
            }

            if (locNodeId.equals(msg.creatorNodeId()) && msg.metrics().get(locNodeId) == null &&
                msg.senderNodeId() != null) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message that has made two passes: " + msg);

                if (topStore != null) {
                    // Check if local node is still coordinator and heartbeat arrived from previous node
                    // to evict nodes properly.
                    GridTcpDiscoveryNode prev = ring.previousNode();

                    if (isLocalNodeCoordinator() && prev != null && msg.senderNodeId().equals(prev.id()))
                        evictNodes(msg.topologyVersion());
                }

                return;
            }

            long tstamp = System.currentTimeMillis();

            if (!msg.metrics().isEmpty() && spiStateCopy() == CONNECTED)
                for (Map.Entry<UUID, GridNodeMetrics> e : msg.metrics().entrySet()) {
                    GridTcpDiscoveryNode node = ring.node(e.getKey());

                    if (node != null) {
                        node.setMetrics(e.getValue());

                        node.lastUpdateTime(tstamp);

                        notifyDiscovery(EVT_NODE_METRICS_UPDATED, node);
                    }
                    else
                        if (log.isDebugEnabled())
                            log.debug("Received metrics from unknown node: " + e.getKey());
                }

            if (ring.hasRemoteNodes()) {
                if ((locNodeId.equals(msg.creatorNodeId()) && msg.senderNodeId() == null ||
                    msg.metrics().get(locNodeId) == null) && spiStateCopy() == CONNECTED)

                    // Message is on its first ring or just created on coordinator.
                    msg.setMetrics(locNodeId, metricsProvider.getMetrics());
                else
                    // Message is on its second ring.
                    msg.removeMetrics(locNodeId);

                sendMessageAcrossRing(msg);
            }
            else {
                locNode.lastUpdateTime(tstamp);

                notifyDiscovery(EVT_NODE_METRICS_UPDATED, locNode);

                // If ring does not have remote nodes, evict with current topology  version.
                if (topStore != null)
                    evictNodes(topVer.get());
            }
        }

        /**
         * Processes heartbeat message when working with metrics store.
         *
         * @param msg Heartbeat message.
         */
        private void processHeartbeatMessageMetricsStore(GridTcpDiscoveryHeartbeatMessage msg) {
            assert msg != null;
            assert metricsStore != null;

            assert msg.metrics().isEmpty();

            if (ring.node(msg.creatorNodeId()) == null) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by unknown node [msg=" + msg +
                        ", ring=" + ring + ']');

                return;
            }

            if (isLocalNodeCoordinator() && !locNodeId.equals(msg.creatorNodeId())) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by non-coordinator node: " + msg);

                return;
            }

            if (!isLocalNodeCoordinator() && locNodeId.equals(msg.creatorNodeId())) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message issued by local node (node is no more coordinator): " +
                        msg);

                return;
            }

            if (locNodeId.equals(msg.creatorNodeId()) && msg.senderNodeId() != null) {
                if (log.isDebugEnabled())
                    log.debug("Discarding heartbeat message that has made full ring pass: " + msg);

                if (topStore != null) {
                    // Check if local node is still coordinator and heartbeat arrived from previous node
                    // to evict nodes properly.
                    GridTcpDiscoveryNode prev = ring.previousNode();

                    if (isLocalNodeCoordinator() && prev != null && msg.senderNodeId().equals(prev.id()))
                        evictNodes(msg.topologyVersion());
                }

                return;
            }

            long tstamp = System.currentTimeMillis();

            try {
                if (spiStateCopy() == CONNECTED) {
                    // Cache metrics in node.
                    GridNodeMetrics metrics = locNode.getMetrics();

                    if (ring.hasRemoteNodes())
                        // Send metrics to store only if there are remote nodes.
                        metricsStore.updateLocalMetrics(locNodeId, metrics);

                    locNode.lastUpdateTime(tstamp);

                    notifyDiscovery(EVT_NODE_METRICS_UPDATED, locNode);
                }
            }
            catch (GridSpiException e) {
                U.error(log, "Failed to update local node metrics in metrics store.", e);
            }

            if (ring.hasRemoteNodes())
                sendMessageAcrossRing(msg);
            else
                // If ring does not have remote nodes, evict with current topology  version.
                if (topStore != null)
                    evictNodes(topVer.get());
        }

        /**
         * Processes discard message and discards previously registered pending messages.
         *
         * @param msg Discard message.
         */
        private void processDiscardMessage(GridTcpDiscoveryDiscardMessage msg) {
            assert msg != null;

            GridUuid msgId = msg.msgId();

            assert msgId != null;

            if (isLocalNodeCoordinator())
                if (!locNodeId.equals(msg.verifierNodeId()))
                    // Message is not verified or verified by former coordinator.
                    msg.verify(locNodeId);
                else
                    // Discard the message.
                    return;

            if (msg.verified())
                if (pendingMsgs.containsKey(msgId)) {
                    for (Iterator<Map.Entry<GridUuid, GridTcpDiscoveryAbstractMessage>>
                        iterator = pendingMsgs.entrySet().iterator(); iterator.hasNext();) {
                        Map.Entry<GridUuid, GridTcpDiscoveryAbstractMessage> e = iterator.next();

                        iterator.remove();

                        stats.onPendingMessageDiscarded();

                        if (log.isDebugEnabled())
                            log.debug("Removed pending message from map: " + e.getValue());

                        if (msgId.equals(e.getValue().id()))
                            break;
                    }
                }
                else
                    if (log.isDebugEnabled())
                        log.debug("Pending messages map does not contain received id: " + msgId);

            if (ring.hasRemoteNodes())
                sendMessageAcrossRing(msg);
        }

        /**
         * Processes update topology message.
         *
         * @param msg Update topology message.
         */
        private void processUpdateTopologyMessage(GridTcpDiscoveryUpdateTopologyMessage msg) {
            assert msg != null;
            assert topStore != null;

            if (isLocalNodeCoordinator()) {
                if (msg.verified() && msg.senderNodeId() != null) {
                    // Message has finished the pass.
                    addMessage(new GridTcpDiscoveryDiscardMessage(locNodeId, msg.id()));

                    for (GridTcpDiscoveryNode node : ring.allNodes()) {
                        if (!node.visible() && node.topologyVersion() <= msg.topologyVersion())
                            addMessage(new GridTcpDiscoveryNodeAddFinishedMessage(locNodeId, node.id()));

                        if (node.topologyVersion() > msg.topologyVersion())
                            break;
                    }

                    return;
                }

                if (!msg.verified()) {
                    // Init topology version and verify the message.
                    msg.verify(locNodeId);

                    try {
                        msg.topologyVersion(topStore.topologyVersion());
                    }
                    catch (GridSpiException e) {
                        U.error(log, "Failed to get topology version from the store.", e);
                    }

                    if (msg.topologyVersion() == topVer.get())
                        // Ignore message
                        return;
                }
            }

            if (!msg.processed()) {
                long maxTopVerRcvdCopy = maxTopVerRcvd;

                if (maxTopVerRcvd < msg.topologyVersion())
                    try {
                        long tstamp = System.currentTimeMillis();

                        Collection<GridTcpDiscoveryTopologyStoreNode> nodes = topStore.nodes(maxTopVerRcvd,
                            msg.topologyVersion());

                        stats.onTopologyStoreGetNodes(System.currentTimeMillis() - tstamp);

                        for (GridTcpDiscoveryTopologyStoreNode node : nodes) {
                            if (log.isDebugEnabled())
                                log.debug("Read node from topology store: " + node);

                            GridTcpDiscoveryAbstractMessage msg1 = null;

                            switch (node.state()) {
                                case ONLINE:
                                    msg1 = new GridTcpDiscoveryNodeAddedMessage(locNodeId, (GridTcpDiscoveryNode)node);

                                    break;

                                case LEAVING:
                                    msg1 = new GridTcpDiscoveryNodeLeftMessage(node.id());

                                    break;

                                case FAILED:
                                    msg1 = new GridTcpDiscoveryNodeFailedMessage(locNodeId, Arrays.asList(node.id()));

                                    break;

                                default:
                                    assert false : "Unexpected node state: " + node.state();
                            }

                            assert node.topologyVersion() > maxTopVerRcvd;

                            msg1.topologyVersion(node.topologyVersion());

                            msg1.verify(locNodeId);

                            addMessage(msg1);

                            maxTopVerRcvd = node.topologyVersion();
                        }
                    }
                    catch (GridSpiException e) {
                        U.error(log, "Failed to get topology snapshot from the store.", e);
                    }

                assert maxTopVerRcvd >= msg.topologyVersion();

                if (maxTopVerRcvd > maxTopVerRcvdCopy) {
                    // Add message back to queue, if and only if new nodes have been read from top store.
                    msg.processed(true);

                    addMessage(msg);
                }
            }
            else
                // All topology updates intended by this message have been processed.
                if (ring.hasRemoteNodes())
                    sendMessageAcrossRing(msg);
        }

        /**
         * Evicts nodes from topology store.
         *
         * @param topVer Topology version.
         */
        private void evictNodes(long topVer) {
            assert topStore != null;
            assert topVer >= 0;

            if (topVer == 0 || lastEvictedTopVer >= topVer)
                return;

            try {
                long tstamp = System.currentTimeMillis();

                topStore.evict(topVer);

                stats.onTopologyStoreEvict(System.currentTimeMillis() - tstamp);

                lastEvictedTopVer = topVer;

                if (log.isDebugEnabled())
                    log.debug("Evicted nodes using topology version: " + topVer);
            }
            catch (GridSpiException e) {
                U.error(log, "Failed to evict nodes using topology version: " + topVer, e);
            }
        }

        /**
         * Performs actions on node adding to topology.
         *
         * @param node Added node.
         */
        private void onNodeAdded(GridTcpDiscoveryNode node) {
            assert node != null;

            assert spiStateCopy() == CONNECTED;

            stats.onNodeJoined();

            notifyDiscovery(EVT_NODE_JOINED, node);

            try {
                if (!ipFinder.isShared() || isLocalNodeCoordinator())
                    ipFinder.registerAddresses(Arrays.asList(node.address()));
            }
            catch (GridSpiException e) {
                U.error(log, "Failed to register new node address:" + node, e);
            }
        }

        /**
         * Performs necessary actions on some node leaving topology.
         *
         * @param node Left node.
         */
        private void onNodeLeft(GridTcpDiscoveryNode node) {
            assert node != null;

            assert spiStateCopy() == CONNECTED;

            stats.onNodeLeft();

            notifyDiscovery(EVT_NODE_LEFT, node);

            synchronized (mux) {
                failedNodes.remove(node);

                leavingNodes.remove(node);
            }

            boolean segmentIncorrect = false;

            if (checkSegEnabled)
                try {
                    checkSegment();
                }
                catch (GridSpiException e) {
                    U.error(log, "Node is in the incorrect segment.", e);

                    segmentIncorrect = true;
                }
            else
                if (log.isDebugEnabled())
                    log.debug("Segment check is omitted.");

            if (segmentIncorrect)
                startSpiReconnectThread();
            else {
                try {
                    if (!ipFinder.isShared() || isLocalNodeCoordinator())
                        ipFinder.unregisterAddresses(Arrays.asList(node.address()));
                }
                catch (GridSpiException e) {
                    U.error(log, "Failed to unregister left node address: " + node, e);
                }

                if (metricsStore != null && isLocalNodeCoordinator())
                    try {
                        metricsStore.removeMetrics(Collections.singletonList(node.id()));
                    }
                    catch (GridSpiException e) {
                        U.error(log, "Failed to remove left node metrics from store: " + node.id(), e);
                    }
            }
        }

        /**
         * Performs necessary actions on some nodes failure.
         *
         * @param nodes Failed nodes.
         */
        private void onNodesFailed(Collection<GridTcpDiscoveryNode> nodes) {
            assert !F.isEmpty(nodes);

            assert spiStateCopy() == CONNECTED;

            for (GridTcpDiscoveryNode node : nodes) {
                notifyDiscovery(EVT_NODE_FAILED, node);

                stats.onNodeFailed();
            }

            synchronized (mux) {
                failedNodes.removeAll(nodes);

                leavingNodes.removeAll(nodes);
            }

            boolean segmentIncorrect = false;

            if (checkSegEnabled)
                try {
                    checkSegment();
                }
                catch (GridSpiException e) {
                    U.error(log, "Node is in the incorrect segment.", e);

                    segmentIncorrect = true;
                }
            else
                if (log.isDebugEnabled())
                    log.debug("Segment check is omitted.");

            if (segmentIncorrect)
                startSpiReconnectThread();
            else {
                try {
                    if (!ipFinder.isShared() || isLocalNodeCoordinator())
                        ipFinder.unregisterAddresses(F.viewReadOnly(nodes,
                            new C1<GridTcpDiscoveryNode, InetSocketAddress>() {
                                @Override public InetSocketAddress apply(GridTcpDiscoveryNode node) {
                                    return node.address();
                                }
                            }));
                }
                catch (GridSpiException e) {
                    U.error(log, "Failed to unregister failed node addresses.", e);
                }

                if (metricsStore != null && isLocalNodeCoordinator()) {
                    Collection<UUID> ids = F.viewReadOnly(nodes, F.node2id());

                    try {
                        metricsStore.removeMetrics(ids);
                    }
                    catch (GridSpiException e) {
                        U.error(log, "Failed to remove failed nodes metrics from store: " + ids, e);
                    }
                }
            }
        }

        /**
         * Creates and starts Spi reconnect thread.
         */
        private void startSpiReconnectThread() {
            if (spiReconnectThread == null || spiReconnectThread.isInterrupted()) {
                disconnectSpi();

                spiReconnectThread = new SpiReconnectThread();

                spiReconnectThread.start();
            }
            else
                if (log.isDebugEnabled())
                    log.debug("Discovery SPI reconnect thread has not been created (it is alive).");
        }

        /**
         * Disconnects SPI from topology.
         */
        private void disconnectSpi() {
            if (log.isDebugEnabled())
                log.debug("Disconnecting discovery SPI from topology.");

            synchronized (mux) {
                spiState = DISCONNECTING;
            }

            Collection<SocketReader> tmp;

            synchronized (mux) {
                tmp = new ArrayList<SocketReader>(readers);
            }

            U.interrupt(tmp);
            U.joinThreads(tmp, log);

            U.interrupt(hbsSnd);
            U.join(hbsSnd, log);

            if (chkStatusSnd != null) {
                U.interrupt(chkStatusSnd);
                U.join(chkStatusSnd, log);
            }

            if (storesCleaner != null) {
                U.interrupt(storesCleaner);
                U.join(storesCleaner, log);
            }

            ring.clear();

            synchronized (mux) {
                spiState = DISCONNECTED;
            }

            notifyDiscovery(EVT_NODE_DISCONNECTED, locNode);
        }
    }

    /**
     * Thread that accepts incoming Tcp connections.
     * <p>
     * Tcp server will call provided closure when accepts incoming connection.
     * From that moment server is no more responsible for the socket.
     */
    private class TcpServer extends GridSpiThread {
        /** Socket Tcp server listens to. */
        private ServerSocket srvrSock;

        /** Port to listen. */
        private int port;

        /**
         * Constructor.
         *
         * @throws GridSpiException In case of error.
         */
        private TcpServer() throws GridSpiException {
            super(gridName, "tcp-disco-srvr", log);

            setPriority(threadPri);

            try {
                for (port = locPort; port < locPort + locPortRange; port++) {
                    try {
                        srvrSock = new ServerSocket(port, 0, locHost);

                        break;
                    }
                    catch (BindException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to bind to local port (will try next port within range) " +
                                "[port=" + port + ", localHost=" + locHost + ']');
                    }
                }

                if (srvrSock == null)
                    throw new GridSpiException("Failed to bind Tcp server socket (possibly all ports in range " +
                        "are in use) [firstPort=" + locPort + ", lastPort=" + (locPort + locPortRange - 1) +
                        ", addr=" + locHost + ']');
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to create Tcp server.", e);
            }

            if (log.isInfoEnabled())
                log.info("Successfully bound to TCP port [port=" + port + ", localHost=" + locHost + ']');
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            try {
                while (!isInterrupted()) {
                    Socket sock = srvrSock.accept();

                    long tstamp = System.currentTimeMillis();

                    if (log.isDebugEnabled())
                        log.debug("Accepted incoming connection from addr: " + sock.getRemoteSocketAddress());

                    SocketReader reader = new SocketReader(sock);

                    synchronized (mux) {
                        readers.add(reader);
                    }

                    reader.setPriority(threadPri);

                    reader.start();

                    stats.onServerSocketInitialized(System.currentTimeMillis() - tstamp);
                }
            }
            catch (IOException e) {
                if (!isInterrupted())
                    U.error(log, "Failed to accept TCP connection.", e);
            }
            finally {
                U.closeQuiet(srvrSock);
            }
        }

        /** {@inheritDoc} */
        @Override public void interrupt() {
            super.interrupt();

            U.close(srvrSock, log);
        }
    }

    /**
     * Thread that reads messages from the socket created for incoming connections.
     */
    private class SocketReader extends GridSpiThread {
        /** Socket to read data from. */
        private final Socket sock;

        /**
         * Constructor.
         *
         * @param sock Socket to read data from.
         */
        private SocketReader(Socket sock) {
            super(gridName, "tcp-disco-sock-reader", log);

            this.sock = sock;

            setPriority(threadPri);

            stats.onSocketReaderCreated();
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            try {
                ClassLoader ldr = getClass().getClassLoader();

                InputStream input;

                UUID nodeId;

                // Handshake.
                try {
                    input = sock.getInputStream();

                    nodeId = U.readUuid(new DataInputStream(input));

                    if (nodeId == null) {
                        if (log.isDebugEnabled())
                            log.debug("Stopping socket reader due to receiving null as node id.");

                        return;
                    }

                    U.writeUuid(new DataOutputStream(sock.getOutputStream()), locNodeId);

                    if (log.isDebugEnabled())
                        log.debug("Initialized connection with remote node: " + nodeId);
                }
                catch (IOException e) {
                    if (!isInterrupted())
                        U.error(log, "Failed to initialize connection.", e);

                    return;
                }

                while (!isInterrupted())
                    try {
                        GridTcpDiscoveryAbstractMessage msg = marsh.unmarshal(input, ldr);

                        assert msg != null;

                        if (log.isDebugEnabled())
                            log.debug("Message has been received: " + msg);

                        if (!nodeId.equals(msg.senderNodeId()))
                            U.warn(log, "Received message has unexpected sender ID value " +
                                "[expectedSenderId=" + nodeId + ", msg=" + msg + ']');

                        stats.onMessageReceived(msg);

                        if (msg instanceof GridTcpDiscoveryJoinRequestMessage)
                            processJoinRequestMessage((GridTcpDiscoveryJoinRequestMessage)msg);

                        else
                            msgWorker.addMessage(msg);
                    }
                    catch (GridException e) {
                        if (isInterrupted())
                            return;

                        boolean remoteNodeRemoving;

                        synchronized (mux) {
                            remoteNodeRemoving = F.viewReadOnly(F.concat(false, failedNodes, leavingNodes),
                                F.node2id()).contains(nodeId);
                        }

                        if (!remoteNodeRemoving && topStore != null) {
                            try {
                                long tstamp = System.currentTimeMillis();

                                GridTcpDiscoveryTopologyStoreNodeState state = topStore.state(nodeId);

                                stats.onTopologyStoreGetNodeState(System.currentTimeMillis() - tstamp);

                                remoteNodeRemoving = state == LEAVING || state == FAILED;
                            }
                            catch (GridSpiException e1) {
                                U.error(log, "Failed to get node state from topology store: " + nodeId, e1);
                            }
                        }

                        if (e.getCause() instanceof IOException || ring.node(nodeId) == null || remoteNodeRemoving) {
                            if (log.isDebugEnabled())
                                log.debug("Socket was closed [sock=" + sock + ", locNodeId=" + locNodeId +
                                    ", remoteNodeId=" + nodeId + ']');
                        }
                        else
                            U.error(log, "Failed to read message [sock=" + sock + ", locNodeId=" + locNodeId +
                                ", remoteNodeId=" + nodeId + ']', e);

                        return;
                    }
            }
            finally {
                U.closeQuiet(sock);
            }
        }

        /**
         * @param msg Join request message.
         */
        private void processJoinRequestMessage(GridTcpDiscoveryJoinRequestMessage msg) {
            assert msg != null;

            GridTcpDiscoverySpiState state = spiStateCopy();

            if (msg.responded())
                // Join request should be send to coordinator across the ring.
                msgWorker.addMessage(msg);
            else if (state == CONNECTED)
                // Direct join request - socket should be closed after handling.
                try {
                    marsh.marshal(RES_OK, sock.getOutputStream());

                    if (log.isDebugEnabled())
                        log.debug("Responded to join request message [msg=" + msg + ", res=" + RES_OK + ']');

                    msg.responded(true);

                    msgWorker.addMessage(msg);
                }
                catch (GridException e) {
                    U.error(log, "Failed to respond to join request [msg=" + msg + ", res=" + RES_OK + ']', e);
                }
                catch (IOException e) {
                    U.error(log, "Failed to respond to join request [msg=" + msg + ", res=" + RES_OK + ']', e);
                }
                finally {
                    U.closeQuiet(sock);
                }
            else {
                // Direct join request - socket should be closed after handling.
                Integer res = null;

                try {
                    stats.onMessageProcessingStarted(msg);

                    res = state == CONNECTING && joinRequestResults.containsKey(msg.node().address()) ? RES_WAIT :
                        (state == CONNECTING && locNodeId.compareTo(msg.creatorNodeId()) > 0) ?
                            RES_CONTINUE_JOIN : RES_WAIT;

                    marsh.marshal(res, sock.getOutputStream());

                    if (log.isDebugEnabled())
                        log.debug("Responded to join request message [msg=" + msg + ", res=" + res + ']');

                    stats.onMessageProcessingFinished(msg);
                }
                catch (GridException e) {
                    U.error(log, "Failed to respond to join request [msg=" + msg + ", res=" + res + ']', e);
                }
                catch (IOException e) {
                    U.error(log, "Failed to respond to join request [msg=" + msg + ", res=" + res + ']', e);
                }
                finally {
                    U.closeQuiet(sock);
                }
            }
        }

        /** {@inheritDoc} */
        @Override public void interrupt() {
            super.interrupt();

            U.closeQuiet(sock);
        }

        /** {@inheritDoc} */
        @Override protected void cleanup() {
            super.cleanup();

            synchronized (mux) {
                readers.remove(this);
            }

            stats.onSocketReaderRemoved();
        }
    }

    /**
     * Metrics update notifier.
     */
    private class MetricsUpdateNotifier extends GridSpiThread {
        /** Constructor. */
        private MetricsUpdateNotifier() {
            super(gridName, "tcp-disco-metrics-update-notifier", log);

            assert metricsStore != null;

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            if (log.isDebugEnabled())
                log.debug("Metrics update notifier has been started.");

            while (!isInterrupted()) {
                synchronized (mux) {
                    long timeout = metricsStore.getMetricsExpireTime();

                    long threshold = System.currentTimeMillis() + metricsStore.getMetricsExpireTime();

                    while (timeout > 0) {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }

                    if (spiState != CONNECTED) {
                        if (log.isDebugEnabled())
                            log.debug("Stopping metrics update notifier (SPI is not connected to topology).");

                        return;
                    }
                }

                long tstamp = System.currentTimeMillis();

                // Event is fired for all nodes in the topology since all alive nodes should update their metrics
                // on time. If it is not so, most probably, nodes have failed and failure will be detected by common
                // failure detection logic.
                for (GridTcpDiscoveryNode node : ring.remoteNodes()) {
                    node.lastUpdateTime(tstamp);

                    notifyDiscovery(EVT_NODE_METRICS_UPDATED, node);
                }
            }
        }
    }

    /**
     * Topology store worker is started when node is about to leave the topology.
     * It generates {@link GridTcpDiscoveryNodeLeftMessage} when the state of
     * the local node is changed in the store and then adds the message to message
     * worker.
     */
    private class TopologyStoreWorker extends GridSpiThread {
        /**
         * Constructor.
         */
        private TopologyStoreWorker() {
            super(gridName, "tcp-disco-top-store-wrk", log);

            assert topStore != null;

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            synchronized (mux) {
                while (spiState != STOPPING)
                    mux.wait(netTimeout);
            }

            if (log.isDebugEnabled())
                log.debug("Topology store worker has been started.");

            while (!isInterrupted()) {
                synchronized (mux) {
                    long timeout = 2000;

                    long threshold = System.currentTimeMillis() + timeout;

                    while (timeout > 0) {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }
                }

                try {
                    long tstamp = System.currentTimeMillis();

                    GridTcpDiscoveryTopologyStoreNodeState state = topStore.state(locNodeId);

                    stats.onTopologyStoreGetNodeState(System.currentTimeMillis() - tstamp);

                    if (state == LEAVING || state == null) {
                        // Node has been updated in the store or already evicted.
                        GridTcpDiscoveryNodeLeftMessage nodeLeftMsg = new GridTcpDiscoveryNodeLeftMessage(locNodeId);

                        nodeLeftMsg.verify(locNodeId);

                        msgWorker.addMessage(nodeLeftMsg);

                        if (log.isDebugEnabled())
                            log.debug("Stopping topology store worker (node left message has been added).");

                        return;
                    }
                }
                catch (GridSpiException e) {
                    U.error(log, "Failed to get local node state from topology store.", e);
                }
            }
        }
    }

    /**
     * SPI Statistics printer.
     */
    private class StatisticsPrinter extends GridSpiThread {
        /**
         * Constructor.
         */
        private StatisticsPrinter() {
            super(gridName, "tcp-disco-stats-printer", log);

            assert statsPrintFreq > 0;

            assert log.isInfoEnabled() && !log.isQuiet();

            setPriority(threadPri);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            if (log.isDebugEnabled())
                log.debug("Statistics printer has been started.");

            while (!isInterrupted()) {
                synchronized (mux) {
                    long timeout = statsPrintFreq;

                    long threshold = System.currentTimeMillis() + timeout;

                    while (timeout > 0) {
                        mux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }
                }

                printStatistics();
            }
        }
    }
}
