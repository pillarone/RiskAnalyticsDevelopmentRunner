// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.multicast;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.port.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.spi.discovery.multicast.GridMulticastDiscoveryMessageType.*;
import static org.gridgain.grid.spi.discovery.multicast.GridMulticastDiscoveryNodeState.*;

/**
 * Discovery SPI implementation that uses IP-multicast for node discovery. At startup
 * SPI starts sending IP/Multicast heartbeat messages. Once other nodes
 * receive these messages, they use TCP/IP to exchange node attributes and then
 * add the new node to their topology. When a node shuts down, it sends {@code LEAVE}
 * heartbeat to other nodes, so every node in the grid can gracefully remove
 * this node from topology.
 * <p>
 * Note that since IP/Multicast is not a reliable protocol, there is no guarantee that
 * a node will be discovered by other grid members. However, IP/Multicast works
 * very reliably within LANs and in most cases this SPI provides a very light weight
 * and easy to use grid node discovery.
 * <p>
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * The following configuration parameters are optional:
 * <ul>
 * <li>Heartbeat frequency (see {@link #setHeartbeatFrequency(long)})</li>
 * <li>Number of retries to send leaving notification(see {@link #setLeaveAttempts(int)})</li>
 * <li>Local IP address (see {@link #setLocalAddress(String)})</li>
 * <li>Port number (see {@link #setTcpPort(int)})</li>
 * <li>Messages TTL(see {@link #setTimeToLive(int)})</li>
 * <li>Number of heartbeats that could be missed (see {@link #setMaxMissedHeartbeats(int)})</li>
 * <li>Multicast IP address (see {@link #setMulticastGroup(String)})</li>
 * <li>Multicast port number (see {@link #setMulticastPort(int)})</li>
 * <li>Local port range (see {@link #setLocalPortRange(int)}</li>
 * <li>Check whether multicast is enabled (see {@link #setCheckMulticastEnabled(boolean)}</li>
 * </ul>
 * <h2 class="header">Java Example</h2>
 * GridMulticastDiscoverySpi is used by default and should be explicitly configured
 * only if some SPI configuration parameters need to be overridden. Examples below
 * insert own multicast group value that differs from default 228.1.2.4.
 * <pre name="code" class="java">
 * GridMulticastDiscoverySpi spi = new GridMulticastDiscoverySpi();
 *
 * // Put another multicast group.
 * spi.setMulticastGroup("228.10.10.157");
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default discovery SPI.
 * cfg.setDiscoverySpi(spi);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridMulticastDiscoverySpi can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *         ...
 *         &lt;property name="discoverySpi"&gt;
 *             &lt;bean class="org.gridgain.grid.spi.discovery.multicast.GridMulticastDiscoverySpi"&gt;
 *                 &lt;property name="multicastGroup" value="228.10.10.157"/&gt;
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
public class GridMulticastDiscoverySpi extends GridSpiAdapter implements GridDiscoverySpi,
    GridMulticastDiscoverySpiMBean {
    /** Default heartbeat delay (value is {@code 3000}). */
    public static final long DFLT_HEARTBEAT_FREQ = 3000;

    /** Default heartbeat thread priority. */
    public static final int DFLT_HEARTBEAT_THREAD_PRIORITY = 6;

    /** Default number of heartbeat messages that could be missed (value is {@code 3}). */
    public static final int DFLT_MAX_MISSED_HEARTBEATS = 3;

    /** Default multicast IP address (value is {@code 228.1.2.4}). */
    public static final String DFLT_MCAST_GROUP = "228.1.2.4";

    /** Default multicast port number (value is {@code 47200}). */
    public static final int DFLT_MCAST_PORT = 47200;

    /** Default local port number for SPI (value is {@code 47300}). */
    public static final int DFLT_TCP_PORT = 47300;

    /**
     * Default local port range (value is {@code 100}).
     * See {@link #setLocalPortRange(int)} for details.
     */
    public static final int DFLT_PORT_RANGE = 100;

    /** Default number of attempts to send leaving notification (value is {@code 3}). */
    public static final int DFLT_LEAVE_ATTEMPTS = 3;

    /**  Default multicast messages time-to-live value (value is {@code 8}). */
    public static final int DFLT_TTL = 8;

    /** Heartbeat attribute key should be the same on all nodes. */
    private static final String HEARTBEAT_ATTRIBUTE_KEY = "gg:disco:heartbeat";

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridLoggerResource
    private GridLogger log;

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridMarshallerResource
    private GridMarshaller marshaller;

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridLocalNodeIdResource
    private UUID nodeId;

    /** */
    private String gridName;

    /** Map of all nodes in grid. */
    private Map<UUID, GridMulticastDiscoveryNode> allNodes;

    /** Set of remote nodes that have state {@code READY}. */
    private List<GridNode> rmtNodes;

    /** Local node representation. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private GridMulticastDiscoveryNode locNode;

    /** Local node attributes. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private Map<String, Object> nodeAttrs;

    /** Delay between heartbeat requests. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private long beatFreq = DFLT_HEARTBEAT_FREQ;

    /** Heartbeat thread priority. */
    private int beatThreadPri = DFLT_HEARTBEAT_THREAD_PRIORITY;

    /** Number of heartbeat messages that could be missed before remote node is considered as failed one. */
    private int maxMissedBeats = DFLT_MAX_MISSED_HEARTBEATS;

    /** Local IP address. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private InetAddress locHost;

    /** Multicast IP address as string. */
    private String mcastGroup = DFLT_MCAST_GROUP;

    /** Multicast IP address. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private InetAddress mcastAddr;

    /** Multicast port number. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private int mcastPort = DFLT_MCAST_PORT;

    /** Local port number. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private int tcpPort = DFLT_TCP_PORT;

    /** Number of attempts to send leaving notification. */
    private int leaveAttempts = DFLT_LEAVE_ATTEMPTS;

    /** Local IP address as string. */
    @GridLocalHostResource
    private String localAddr;

    /** */
    private int localPortRange = DFLT_PORT_RANGE;

    /** */
    private volatile GridDiscoverySpiListener lsnr;

    /** */
    private GridDiscoveryMetricsProvider metricsProvider;

    /** */
    private MulticastHeartbeatSender mcastSender;

    /** */
    private MulticastHeartbeatReceiver mcastRcvr;

    /** */
    private TcpHandshakeListener tcpLsnr;

    /** */
    private NodeSweeper nodeSweeper;

    /** Set of threads that requests attributes from remote nodes. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private Set<GridSpiThread> workers;

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private long startTime = -1;

    /** Messages TTL value. */
    private int ttl = DFLT_TTL;

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private int boundTcpPort = -1;

    /**
     * Flag to check whether multicast is enabled on local node or not. This
     * check is performed by sending an multicast message to itself.
     */
    private boolean isMcastEnabled;

    /** Flag indicating whether need to check for multicast enabled or not. */
    private boolean isCheckMulticastEnabled = true;

    /** Set to {@code true} when {@link #spiStop()} is called. */
    private final AtomicBoolean isStopping = new AtomicBoolean(false);

    /** */
    private final Object mux = new Object();

    /**
     * Sets IP address of multicast group.
     * <p>
     * If not provided, default value is {@link #DFLT_MCAST_GROUP}.
     *
     * @param mcastGroup Multicast IP address.
     */
    @GridSpiConfiguration(optional = true)
    public void setMulticastGroup(String mcastGroup) {
        this.mcastGroup = mcastGroup;
    }

    /** {@inheritDoc} */
    @Override public String getMulticastGroup() {
        return mcastGroup;
    }

    /**
     * Sets port number which multicast messages are sent to.
     * <p>
     * If not provided, default value is {@link #DFLT_MCAST_PORT}.
     *
     * @param mcastPort Multicast port number.
     */
    @GridSpiConfiguration(optional = true)
    public void setMulticastPort(int mcastPort) {
        this.mcastPort = mcastPort;
    }

    /** {@inheritDoc} */
    @Override public int getMulticastPort() {
        return mcastPort;
    }

    /**
     * Sets local TCP port number to be used for node attribute
     * exchange upon discovery.
     * <p>
     * If not provided, default value is {@link #DFLT_TCP_PORT}.
     *
     * @param tcpPort Port number.
     */
    @GridSpiConfiguration(optional = true)
    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    /** {@inheritDoc} */
    @Override public int getTcpPort() {
        return tcpPort;
    }

    /**
     * Sets delay between heartbeat requests. SPI sends broadcast messages in
     * configurable time interval to other nodes to notify them about its state.
     * <p>
     * If not provided, default value is {@link #DFLT_HEARTBEAT_FREQ}.
     *
     * @param beatFreq Time in milliseconds.
     */
    @GridSpiConfiguration(optional = true)
    public void setHeartbeatFrequency(long beatFreq) {
        this.beatFreq = beatFreq;
    }

    /** {@inheritDoc} */
    @Override public long getHeartbeatFrequency() {
        return beatFreq;
    }

    /** {@inheritDoc} */
    @Override public int getHeartbeatThreadPriority() {
        return beatThreadPri;
    }

    /**
     * Sets heartbeat thread priority.
     *
     * @param beatThreadPri Heartbeat thread priority.
     */
    @GridSpiConfiguration(optional = true)
    public void setHeartbeatThreadPriority(int beatThreadPri) {
        this.beatThreadPri = beatThreadPri;
    }

    /**
     * Sets number of heartbeat requests that could be missed before remote
     * node is considered to be failed.
     * <p>
     * If not provided, default value is {@link #DFLT_MAX_MISSED_HEARTBEATS}.
     *
     * @param maxMissedBeats Number of missed requests.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaxMissedHeartbeats(int maxMissedBeats) {
        this.maxMissedBeats = maxMissedBeats;
    }

    /** {@inheritDoc} */
    @Override public int getMaximumMissedHeartbeats() {
        return maxMissedBeats;
    }

    /**
     * Sets number of attempts to notify another nodes that this one is leaving
     * grid. Multiple leave requests are sent to increase the chance of successful
     * delivery to every node, since IP Multicast protocol is unreliable.
     * Note that on most networks loss of IP Multicast packets is generally
     * negligible.
     * <p>
     * If not provided, default value is {@link #DFLT_LEAVE_ATTEMPTS}.
     *
     * @param leaveAttempts Number of attempts.
     */
    @GridSpiConfiguration(optional = true)
    public void setLeaveAttempts(int leaveAttempts) {
        this.leaveAttempts = leaveAttempts;
    }

    /** {@inheritDoc} */
    @Override public int getLeaveAttempts() {
        return leaveAttempts;
    }

    /**
     * Sets local host IP address that discovery SPI uses.
     * <p>
     * If not provided, by default a first found non-loopback address
     * will be used. If there is no non-loopback address available,
     * then {@link InetAddress#getLocalHost()} will be used.
     *
     * @param localAddr IP address.
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalAddress(String localAddr) {
        this.localAddr = localAddr;
    }

    /**
     * Gets TCP messages time-to-live.
     *
     * @return TTL.
     */
    @Override public int getTimeToLive() {
        return ttl;
    }

    /**
     * Sets Multicast messages time-to-live in router hops.
     * <p>
     * If not provided, default value is {@link #DFLT_TTL}.
     *
     * @param ttl Messages TTL.
     */
    @GridSpiConfiguration(optional = true)
    public void setTimeToLive(int ttl) {
        this.ttl = ttl;
    }

    /**
     * Sets local port range for TCP and Multicast ports (value must greater than or equal to {@code 0}).
     * If provided local port (see {@link #setMulticastPort(int)} or {@link #setTcpPort(int)} is occupied,
     * implementation will try to increment the port number for as long as it is less than
     * initial value plus this range.
     * <p>
     * If port range value is {@code 0}, then implementation will try bind only to the port provided by
     * {@link #setMulticastPort(int)} or {@link #setTcpPort(int)} methods and fail if binding to these
     * ports did not succeed.
     * <p>
     * Local port range is very useful during development when more than one grid nodes need to run
     * on the same physical machine.
     *
     * @param localPortRange New local port range.
     * @see #DFLT_PORT_RANGE
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalPortRange(int localPortRange) {
        this.localPortRange = localPortRange;
    }

    /** {@inheritDoc} */
    @Override public int getLocalPortRange() {
        return localPortRange;
    }

    /** {@inheritDoc} */
    @Override public boolean isCheckMulticastEnabled() {
        return isCheckMulticastEnabled;
    }

    /**
     * Enables or disabled check whether multicast is enabled on local node.
     * By default this value is {@code true}. On startup GridGain will check
     * if local node can receive multicast packets, and if not, will not allow
     * the node to startup.
     * <p>
     * This property should be disabled in rare cases when loopback multicast
     * is disabled, but multicast to other remote boxes is enabled.
     *
     * @param isCheckMulticastEnabled {@code True} for enabling multicast check,
     *      {@code false} for disabling it.
     */
    public void setCheckMulticastEnabled(boolean isCheckMulticastEnabled) {
        this.isCheckMulticastEnabled = isCheckMulticastEnabled;
    }

    /** {@inheritDoc} */
    @Override public void setNodeAttributes(Map<String, Object> attrs) {
        nodeAttrs = U.sealMap(attrs);
    }

    /** {@inheritDoc} */
    @Override public List<GridNode> getRemoteNodes() {
        synchronized (mux) {
            if (rmtNodes == null) {
                rmtNodes = new ArrayList<GridNode>(allNodes.size());

                for (GridMulticastDiscoveryNode node : allNodes.values()) {
                    assert !node.equals(locNode);

                    if (node.getState() == READY) {
                        rmtNodes.add(node);
                    }
                }

                // Seal it.
                rmtNodes = Collections.unmodifiableList(rmtNodes);
            }

            return rmtNodes;
        }
    }

    /** {@inheritDoc} */
    @Override @Nullable
    public GridNode getNode(UUID nodeId) {
        assert nodeId != null;

        if (locNode.id().equals(nodeId)) {
            return locNode;
        }

        synchronized (mux) {
            GridMulticastDiscoveryNode node = allNodes.get(nodeId);

            return node != null && node.getState() == READY ? node : null;
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<UUID> getRemoteNodeIds() {
        Collection<UUID> ids = new HashSet<UUID>();

        for (GridNode node : getRemoteNodes()) {
            ids.add(node.id());
        }

        return ids;
    }

    /** {@inheritDoc} */
    @Override public int getRemoteNodeCount() {
        Collection<GridNode> tmp = getRemoteNodes();

        return tmp == null ? 0 : tmp.size();
    }

    /** {@inheritDoc} */
    @Override public GridNode getLocalNode() {
        return locNode;
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
    @Override public Map<String, Object> getNodeAttributes() throws GridSpiException {
        return F.<String, Object>asMap(createSpiAttributeName(HEARTBEAT_ATTRIBUTE_KEY), getHeartbeatFrequency());
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        // Start SPI start stopwatch.
        startStopwatch();

        assertParameter(mcastGroup != null, "mcastGroup != null");
        assertParameter(mcastPort >= 0, "mcastPort >= 0");
        assertParameter(mcastPort <= 65535, "mcastPort <= 65535");
        assertParameter(tcpPort >= 0, "tcpPort >= 0");
        assertParameter(tcpPort <= 65535, "tcpPort <= 65535");
        assertParameter(beatFreq > 0, "beatFreq > 0");
        assertParameter(maxMissedBeats > 0, "maxMissedBeats > 0");
        assertParameter(leaveAttempts > 0, "leaveAttempts > 0");
        assertParameter(ttl > 0, "ttl > 0");
        assertParameter(localPortRange >= 0, "localPortRange >= 0");

        startTime = System.currentTimeMillis();

        synchronized (mux) {
            allNodes = new HashMap<UUID, GridMulticastDiscoveryNode>();

            workers = new HashSet<GridSpiThread>();
        }

        // Verify valid addresses.
        try {
            locHost = localAddr == null ? U.getLocalHost() : InetAddress.getByName(localAddr);
        }
        catch (IOException e) {
            throw new GridSpiException("Unknown local address: " + localAddr, e);
        }

        try {
            mcastAddr = InetAddress.getByName(mcastGroup);
        }
        catch (UnknownHostException e) {
            throw new GridSpiException("Unknown multicast group: " + mcastGroup, e);
        }

        if (!mcastAddr.isMulticastAddress()) {
            throw new GridSpiException("Invalid multicast group address : " + mcastAddr);
        }

        // Ack parameters.
        if (log.isDebugEnabled()) {
            log.debug(configInfo("mcastGroup", mcastGroup));
            log.debug(configInfo("mcastPort", mcastPort));
            log.debug(configInfo("tcpPort", tcpPort));
            log.debug(configInfo("localPortRange", localPortRange));
            log.debug(configInfo("beatFreq", beatFreq));
            log.debug(configInfo("maxMissedBeats", maxMissedBeats));
            log.debug(configInfo("leaveAttempts", leaveAttempts));
            log.debug(configInfo("localHost", locHost.getHostAddress()));
            log.debug(configInfo("ttl", ttl));
        }

        // Warn on odd beat frequency.
        if (beatFreq < 2000) {
            U.warn(log, "Heartbeat frequency is too low (at least 2000 ms): " + beatFreq);
        }

        // Warn on odd maximum missed heartbeats.
        if (maxMissedBeats < 3) {
            U.warn(log, "Maximum missed heartbeats value is too low (at least 3): " + maxMissedBeats);
        }

        this.gridName = gridName;

        // Create TCP listener first, as it initializes boundTcpPort used
        // by MulticastHeartbeatSender to send heartbeats.
        tcpLsnr = new TcpHandshakeListener();
        mcastRcvr = new MulticastHeartbeatReceiver();
        mcastSender = new MulticastHeartbeatSender();
        nodeSweeper = new NodeSweeper();

        // Initialize local node prior to starting threads, as they
        // are using data from local node.
        locNode = new GridMulticastDiscoveryNode(nodeId, locHost, boundTcpPort, startTime, metricsProvider);

        locNode.setAttributes(nodeAttrs);

        registerMBean(gridName, this, GridMulticastDiscoverySpiMBean.class);

        tcpLsnr.start();
        mcastRcvr.start();
        mcastSender.start();
        nodeSweeper.start();

        // Ack local node.
        if (log.isInfoEnabled()) {
            log.info("Local node: " + locNode);
        }

        try {
            // Wait to discover other nodes.
            if (log.isInfoEnabled()) {
                log.info("Waiting for initial heartbeat timeout (" + beatFreq + " ms)");
            }

            // Wait for others to add this node to topology.
            Thread.sleep(beatFreq);

            if (isCheckMulticastEnabled) {
                long delta = beatFreq * maxMissedBeats;
                long end = System.currentTimeMillis() + delta;

                synchronized (mux) {
                    while (!isMcastEnabled && delta > 0) {
                        mux.wait(delta);

                        delta = end - System.currentTimeMillis();
                    }

                    if (!isMcastEnabled) {
                        throw new GridSpiException("Multicast is not enabled on this node. Check you firewall " +
                            "settings or contact network administrator if Windows group policy is used.");
                    }
                }
            }
        }
        catch (InterruptedException e) {
            throw new GridSpiException("Got interrupted while starting multicast discovery.", e);
        }

        // Ack ok start.
        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        isStopping.set(true);

        U.interrupt(mcastSender);
        U.interrupt(nodeSweeper);
        U.interrupt(mcastRcvr);
        U.interrupt(tcpLsnr);

        U.join(mcastSender, log);
        U.join(nodeSweeper, log);
        U.join(mcastRcvr, log);
        U.join(tcpLsnr, log);

        Set<GridSpiThread> ws = null;

        synchronized (mux) {
            // Copy to local set to avoid deadlock.
            if (workers != null) {
                ws = new HashSet<GridSpiThread>(workers);
            }
        }

        if (ws != null) {
            U.interrupt(ws);
            U.joinThreads(ws, log);
        }

        startTime = -1;

        //Clear inner collections.
        synchronized(mux) {
            rmtNodes = null;
            allNodes = null;
        }

        mcastSender = null;
        mcastRcvr = null;
        tcpLsnr = null;
        nodeSweeper = null;
        workers = null;

        unregisterMBean();

        // Ack ok stop.
        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void onContextInitialized(GridSpiContext spiCtx) throws GridSpiException {
        super.onContextInitialized(spiCtx);

        getSpiContext().registerPort(boundTcpPort, GridPortProtocol.TCP);
        getSpiContext().registerPort(mcastPort, GridPortProtocol.UDP);
    }

    /** {@inheritDoc} */
    @Override public void onContextDestroyed() {
        getSpiContext().deregisterPorts();

        super.onContextDestroyed();
    }

    /**
     * Method is called when any discovery event occurs. It calls external listener.
     *
     * @param type Discovery event type. See {@link GridDiscoveryEvent} for more details.
     * @param node Remote node this event is connected with.
     */
    private void notifyDiscovery(int type, GridMulticastDiscoveryNode node) {
        assert type > 0;
        assert node != null;

        if (node.getState() != NEW) {
            GridDiscoverySpiListener lsnr = this.lsnr;

            if (lsnr != null) {
                lsnr.onDiscovery(type, node);
            }
        }
    }


    /** {@inheritDoc} */
    @Override public boolean pingNode(UUID nodeId) {
        assert nodeId != null;

        GridMulticastDiscoveryNode node;

        if (locNode.id().equals(nodeId)) {
            node = locNode;
        }
        else {
            synchronized (mux) {
                node = allNodes.get(nodeId);
            }
        }

        if (node == null || node.getInetAddress() == null || node.getTcpPort() <= 0) {
            if (log.isDebugEnabled()) {
                log.debug("Ping failed (invalid node): " + nodeId);
            }

            return false;
        }

        Socket sock = null;
        InputStream in = null;
        OutputStream out = null;

        try {
            sock = new Socket(node.getInetAddress(), node.getTcpPort());

            in = sock.getInputStream();
            out = sock.getOutputStream();

            U.marshal(marshaller, new GridMulticastDiscoveryMessage(PING_REQUEST), out);

            GridMulticastDiscoveryMessage res = U.unmarshal(marshaller, in, getClass().getClassLoader());

            if (res == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Ping failed (invalid response): " + nodeId);
                }

                return false;
            }

            assert res.getType() == PING_RESPONSE;
        }
        catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug("Ping failed (" + e.getMessage() + "): " + nodeId);
            }

            return false;
        }
        catch (GridException e) {
            if (e.getCause() instanceof ClassNotFoundException) {
                U.error(log, "Ping failed (Invalid class for ping response).", e);
            }
            else if (log.isDebugEnabled()) {
                log.debug("Ping failed ("
                    + (e.getCause() instanceof IOException ? e.getCause().getMessage() : e.getMessage()) + "): "
                    + nodeId);
            }

            return false;
        }
        finally {
            U.close(out, log);
            U.close(in, log);
            U.close(sock, log);
        }

        if (log.isDebugEnabled()) {
            log.debug("Ping ok: " + nodeId);
        }

        return true;
    }

    /**
     * @param e IO error.
     */
    private void handleNetworkChecks(IOException e) {
        if (e instanceof SocketException && U.isWindowsVista()) {
            U.warn(log, "Note that Windows Vista has a known problem with recovering network " +
                "connectivity after waking up from deep sleep or hibernate mode. " +
                "Due to this error GridGain cannot recover from this error " +
                "automatically and you will need to restart this grid node manually.");
        }

        try {
            if (U.isLocalHostChanged()) {
                U.warn(log, "It appears that you are running on DHCP and " +
                    "local host has been changed. GridGain cannot recover from this error " +
                    "automatically and you will need to manually restart this grid node. For this " +
                    "reason we do not recommend running grid node on DHCP (at least not in a " +
                    "production environment).");
            }
        }
        catch (IOException ignored) {
            // Ignore this error as we probably experiencing the same
            // network problem.
        }
    }

    /**
     * Heartbeat sending thread. It sends heartbeat messages every
     * {@link GridMulticastDiscoverySpi#beatFreq} milliseconds. If node is going
     * to leave grid it sends corresponded message with leaving state.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class MulticastHeartbeatSender extends GridSpiThread {
        /** Heartbeat message helper. */
        private final GridMulticastDiscoveryHeartbeat beat = new GridMulticastDiscoveryHeartbeat(
            nodeId, locHost, boundTcpPort, false, startTime);

        /** Multicast socket to send broadcast messages. */
        private volatile MulticastSocket sock;

        /** */
        private final Object beatMux = new Object();

        /** */
        private boolean errMsgThrottle;

        /**
         * Creates new instance of sender.
         *
         * @throws GridSpiException Thrown if SPI is unable to create multicast socket.
         */
        MulticastHeartbeatSender() throws GridSpiException {
            super(gridName, "grid-mcast-disco-beat-sender", log);

            setPriority(getHeartbeatThreadPriority());

            try {
                createSocket();
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to create multicast sender socket.", e);
            }
        }

        /** {@inheritDoc} */
        @Override protected void cleanup() {
            // Potentially double closing, better safe than sorry.
            U.close(sock);
        }

        /**
         * Creates new multicast socket and disables loopback mode.
         *
         * @throws IOException Thrown if unable to create socket.
         */
        private void createSocket() throws IOException {
            sock = new MulticastSocket(new InetSocketAddress(locHost, 0));

            // Number of router hops (0 for loopback).
            sock.setTimeToLive(ttl);
        }

        /** */
        @SuppressWarnings({"NakedNotify"})
        void wakeUp() {
            // Wake up waiting sender, so the heartbeat
            // can be sent right away.
            synchronized (beatMux) {
                beatMux.notifyAll();
            }
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"UnconditionalWait"})
        @Override public void body() throws InterruptedException {
            int n = leaveAttempts;

            // Remote nodes will be able to extract node address, port,
            // and alive status from the beat.
            DatagramPacket packet = new DatagramPacket(beat.getData(), beat.getData().length, mcastAddr,
                mcastPort);

            while (n > 0) {
                beat.setLeaving(isInterrupted());

                try {
                    if (sock == null) {
                        createSocket();
                    }

                    // Update node metrics.
                    beat.setMetrics(metricsProvider.getMetrics());

                    // Reset buffer before every send.
                    packet.setData(beat.getData());

                    sock.send(packet);

                    // Reset error message throttle flag.
                    errMsgThrottle = false;
                }
                catch (IOException e) {
                    if (!errMsgThrottle) {
                        handleNetworkChecks(e);

                        U.error(log, "Failed to send heart beat (will try again in " + beatFreq + "ms) [locAddr=" +
                            localAddr + ", mcastAddr=" + mcastAddr + "]. Note that " +
                            "this error message will appear only once for this network problem to avoid log flooding " +
                            "but attempts to reconnect will continue.", e);

                        errMsgThrottle = true;
                    }

                    U.close(sock);

                    sock = null;
                }

                if (isInterrupted()) {
                    n--;
                }
                else {
                    try {
                        synchronized (beatMux) {
                            beatMux.wait(beatFreq);
                        }
                    }
                    catch (InterruptedException e) {
                        if (!isInterrupted()) {
                            throw e;
                        }
                    }
                }
            }
        }
    }

    /**
     * Multicast messages receiving thread. This class process all heartbeat messages
     * that comes from the others.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class MulticastHeartbeatReceiver extends GridSpiThread {
        /** Multicast socket message is read from. */
        private MulticastSocket sock;

        /**
         * Creates new instance of receiver and joins multicast group.
         *
         * @throws GridSpiException Thrown if SPI is unable to create socket or join group.
         */
        MulticastHeartbeatReceiver() throws GridSpiException {
            super(gridName, "grid-mcast-disco-beat-rcvr", log);

            setPriority(getHeartbeatThreadPriority());

            try {
                createSocket();
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to create multicast socket.", e);
            }
        }

        /**
         * Creates new multicast socket and joins to multicast group.
         *
         * @return Created socket.
         * @throws IOException Thrown if it's impossible to create socket or join multicast group.
         */
        private MulticastSocket createSocket() throws IOException {
            synchronized (mux) {
                // Note, that we purposely don't specify local host binding,
                // as it does not work on some OS (including Fedora).
                sock = new MulticastSocket(mcastPort);

                // Enable support for more than one node on the same machine.
                sock.setLoopbackMode(false);

                // If loopback mode did not get enabled.
                if (sock.getLoopbackMode()) {
                    U.warn(log, "Loopback mode is disabled which prevents nodes on the same machine from discovering " +
                        "each other.");

                    if (!isMcastEnabled) {
                        // Since there is no way to check if multicast is enabled,
                        // we assume that it is enabled.
                        isMcastEnabled = true;

                        mux.notifyAll();
                    }
                }

                // Set to local bind interface.
                sock.setInterface(locHost);

                // Join multicast group.
                sock.joinGroup(mcastAddr);

                if (log.isInfoEnabled()) {
                    log.info("Successfully bound to Multicast port: " + mcastPort);
                }

                return sock;
            }
        }

        /** {@inheritDoc} */
        @Override public void interrupt() {
            super.interrupt();

            synchronized (mux) {
                U.close(sock);

                sock = null;
            }
        }

        /** {@inheritDoc} */
        @Override protected void cleanup() {
            synchronized (mux) {
                // Potentially double closing, better safe than sorry.
                U.close(sock);

                sock = null;
            }
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"NonStaticInitializer"})
        @Override public void body() throws InterruptedException {
            // Note that local host can be IPv4 or IPv6.
            byte[] data = new byte[GridMulticastDiscoveryHeartbeat.STATIC_DATA_LENGTH + locHost.getAddress().length];

            DatagramPacket pckt = new DatagramPacket(data, data.length);

            while (!isInterrupted()) {
                try {
                    MulticastSocket sock;

                    synchronized (mux) {
                        // Avoid recreating socket after cancel.
                        if (isInterrupted()) {
                            return;
                        }

                        sock = this.sock;

                        if (sock == null) {
                            sock = createSocket();
                        }
                    }

                    // Wait for node attributes.
                    sock.receive(pckt);

                    GridMulticastDiscoveryHeartbeat beat = new GridMulticastDiscoveryHeartbeat(pckt.getData());

                    UUID beatNodeId = beat.getNodeId();

                    GridMulticastDiscoveryNode node;

                    GridMulticastDiscoveryNode beatNode = null;

                    synchronized (mux) {
                        if (beatNodeId.equals(nodeId)) {
                            // If received a heartbeat from itself, then multicast
                            // is enabled on local node.
                            if (!isMcastEnabled) {
                                isMcastEnabled = true;

                                mux.notifyAll();
                            }

                            beatNode = locNode;

                            node = locNode;
                        }
                        else {
                            if (beat.isLeaving()) {
                                node = allNodes.get(beatNodeId);

                                if (node != null && node.getState() == READY) {
                                    node.onLeft();

                                    assert node.getState() == LEFT : "Invalid node state: " + node.getState();

                                    rmtNodes = null;

                                    // Listener notification.
                                    notifyDiscovery(EVT_NODE_LEFT, node);

                                    if (log.isDebugEnabled()) {
                                        log.debug("Node left grid: " + node);
                                    }

                                    // Notify threads waiting for node to change state.
                                    mux.notifyAll();
                                }
                                else {
                                    // Either receiving heartbeats after node was removed
                                    // by topology cleaner or node left before joined.
                                    continue;
                                }
                            }
                            else {
                                node = allNodes.get(beatNodeId);

                                // If found new node.
                                if (node == null) {
                                    // Local node cannot communicate with itself.
                                    assert !nodeId.equals(beatNodeId);

                                    allNodes.put(
                                        beatNodeId,
                                        node = new GridMulticastDiscoveryNode(
                                            beatNodeId,
                                            beat.getInetAddress(),
                                            beat.getTcpPort(),
                                            beat.getStartTime(),
                                            beat.getMetrics()));

                                    rmtNodes = null;

                                    // No listener notification since node is not READY yet.
                                    assert node.getState() == NEW : "Invalid node state: " + node.getState();

                                    if (log.isDebugEnabled()) {
                                        log.debug("Added NEW node to grid: " + node);
                                    }
                                }
                                else if (node.getState() == NEW) {
                                    node.onHeartbeat(beat.getMetrics());

                                    if (log.isDebugEnabled()) {
                                        log.debug("Received heartbeat for new node (will ignore): " + node);
                                    }

                                    continue;
                                }
                                // If zombie.
                                else if (node.getState() == LEFT) {
                                    if (log.isDebugEnabled()) {
                                        log.debug("Received zombie heartbeat for left node (will ignore): " + node);
                                    }

                                    continue;
                                }
                                // If duplicate node ID.
                                else if (node.getStartTime() < beat.getStartTime()) {
                                    U.warn(log, "Node with duplicate node ID is trying to join (will ignore): " +
                                        node.id());

                                    continue;
                                }
                                else {
                                    // New heartbeat callback.
                                    node.onHeartbeat(beat.getMetrics());

                                    beatNode = node;
                                }
                            }
                        }
                    }

                    // Metrics update callback.
                    if (beatNode != null) {
                        notifyDiscovery(EVT_NODE_METRICS_UPDATED, beatNode);
                    }

                    assert node != null;

                    // If node is new, initiate TCP handshake.
                    if (node.getState() == NEW) {
                        assert !nodeId.equals(node.id());

                        // Wake up heartbeat sender, so heartbeat will be
                        // sent immediately.
                        mcastSender.wakeUp();

                        synchronized (mux) {
                            // If stopping process started, no point
                            // to initiate handshakes.
                            if (!isStopping.get()) {
                                // Node with larger UUID gets to initiate handshake.
                                boolean activeHandshakeSender = node.id().compareTo(locNode.id()) > 0;

                                GridSpiThread sender = new TcpHandshakeSender(beat, node, activeHandshakeSender);

                                // Register sender.
                                workers.add(sender);

                                sender.start();
                            }
                        }
                    }
                }
                catch (IOException e) {
                    if (!isInterrupted()) {
                        U.error(log, "Failed to listen to heartbeats (will wait for " + beatFreq + "ms and try again)", e);

                        synchronized (mux) {
                            U.close(sock);

                            sock = null;
                        }

                        Thread.sleep(beatFreq);
                    }
                }
            }
        }
    }

    /**
     * Tcp handshake sender.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class TcpHandshakeSender extends GridSpiThread {
        /** Heartbeat. */
        private final GridMulticastDiscoveryHeartbeat beat;

        /** New node. */
        private final GridMulticastDiscoveryNode newNode;

        /** */
        private volatile Socket attrSock;

        /** True if this handshake sender should send handshake immediately. */
        private boolean actSnd;

        /**
         * @param beat Heartbeat received.
         * @param newNode Joining node.
         * @param actSnd whether send handshake immediately.
         */
        TcpHandshakeSender(GridMulticastDiscoveryHeartbeat beat, GridMulticastDiscoveryNode newNode, boolean actSnd) {
            super(gridName, "grid-mcast-disco-tcp-handshake-sender", log);

            assert beat != null;
            assert newNode != null;

            this.beat = beat;
            this.newNode = newNode;
            this.actSnd = actSnd;
        }

        /** {@inheritDoc} */
        @Override public void body() {
            try {
                if (!actSnd) {
                    try {
                        Thread.sleep(beatFreq * maxMissedBeats);
                    }
                    catch (InterruptedException ignored) {
                        return;
                    }
                    synchronized (mux) {
                        if (newNode.getState() != NEW || isStopping.get()) {
                            return; // Handshake already come.
                        }
                    }
                }

                if (log.isDebugEnabled()) {
                    log.debug("Request attributes from node [addr=" + beat.getInetAddress() +
                        ", port=" + beat.getTcpPort() + ']');
                }

                attrSock = new Socket(beat.getInetAddress(), beat.getTcpPort(), locHost, 0);

                InputStream in = null;
                OutputStream out = null;

                try {
                    out = attrSock.getOutputStream();
                    in = attrSock.getInputStream();

                    U.marshal(marshaller, new GridMulticastDiscoveryMessage(ATTRS_REQUEST, nodeId,
                        locHost, boundTcpPort, nodeAttrs, startTime, metricsProvider.getMetrics()), out);

                    GridMulticastDiscoveryMessage msg =
                        U.unmarshal(marshaller, in, getClass().getClassLoader());

                    // Safety check.
                    if (msg.getType() != ATTRS_RESPONSE) {
                        U.warn(log, "Received message of wrong type [expected=ATTRS_RESPONSE"
                            + ", actual=" + msg.getType() + ']');

                        return;
                    }

                    // Safety check.
                    if (!msg.getNodeId().equals(beat.getNodeId())) {
                        U.warn(log, "Received attributes from unexpected node [expected=" +
                            beat.getNodeId() + ", actual=" + msg.getNodeId() + ']');

                        return;
                    }

                    synchronized (mux) {
                        if (newNode.getState() == NEW) {
                            assert msg.getNodeId().equals(newNode.id());

                            newNode.setAttributes(msg.getAttributes());

                            rmtNodes = null;

                            if (log.isDebugEnabled()) {
                                log.debug("Node moved from NEW to READY: " + newNode);
                            }

                            // Listener notification.
                            notifyDiscovery(EVT_NODE_JOINED, newNode);
                        }
                        else {
                            // Node might be in state LEFT if it was swept.
                            if (log.isDebugEnabled()) {
                                log.debug("Node is not in NEW state: " + newNode);
                            }

                            return;
                        }

                        // Notify threads waiting for node to change state.
                        mux.notifyAll();
                    }

                    // Confirm received attributes.
                    U.marshal(marshaller, new GridMulticastDiscoveryMessage(ATTRS_CONFIRMED), out);
                }
                finally {
                    U.close(out, log);
                    U.close(in, log);
                }
            }
            catch (ConnectException e) {
                if (!isStopping.get() && !isInterrupted()) {
                    U.warn(log, "Failed to connect to node (did the node stop?) [addr=" + beat.getInetAddress() +
                        ", port=" + beat.getTcpPort() + ", err=" + e.getMessage() + "]. " +
                        "Make sure that destination node is alive and has properly " +
                        "configured firewall that allows GridGain incoming traffic " +
                        "(especially on Windows Vista).");
                }
            }
            catch (IOException e) {
                if (!isStopping.get() && !isInterrupted()) {
                    if (remoteNodeExists()) {
                        // Output error only if remote node exists.
                        U.error(log, "Failed to request node attributes from node (did the node stop?) [addr=" +
                            beat.getInetAddress() + ", port=" + beat.getTcpPort() + ']', e);

                        handleNetworkChecks(e);
                    }
                }
            }
            catch (GridException e) {
                if (!isStopping.get() && !isInterrupted()) {
                    if (remoteNodeExists())
                        // Output error only if remote node exists.
                        U.error(log, "Failed to request node attributes from node.", e);
                }
            }
            finally {
                U.closeQuiet(attrSock);
            }
        }

        /**
         * @return {@code True} if remote node exists.
         */
        private boolean remoteNodeExists() {
            Socket testSock = null;

            try {
                // Is node still alive and accepting connections?
                testSock = new Socket(beat.getInetAddress(), beat.getTcpPort(), locHost, 0);

                return true;
            }
            catch (IOException ignored) {
                // No-op.
            }
            finally {
                U.closeQuiet(testSock);
            }

            return false;
        }

        /** {@inheritDoc} */
        @Override public void cleanup() {
            synchronized (mux) {
                workers.remove(this);
            }
        }

        /** {@inheritDoc} */
        @Override public void interrupt() {
            super.interrupt();

            U.closeQuiet(attrSock);
        }
    }

    /**
     * Listener that processes TCP messages.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class TcpHandshakeListener extends GridSpiThread {
        /** Socket TCP listener is set to. */
        private ServerSocket tcpSock;

        /**
         * Creates new instance of listener.
         *
         * @throws GridSpiException Thrown if SPI is unable to create socket.
         */
        TcpHandshakeListener() throws GridSpiException {
            super(gridName, "grid-mcast-disco-tcp-handshake-listener", log);

            try {
                createTcpSocket();
            }
            catch (IOException e) {
                throw new GridSpiException("Failed to create TCP server for receiving node attributes.", e);
            }
        }

        /**
         * Creates new socket.
         *
         * @return Created socket.
         * @throws IOException Thrown if socket could not be open.
         */
        private ServerSocket createTcpSocket() throws IOException {
            int maxPort = tcpPort + localPortRange;

            synchronized (mux) {
                for (int port = tcpPort; port < maxPort; port++) {
                    try {
                        tcpSock = new ServerSocket(port, 0, locHost);

                        boundTcpPort = port;

                        if (log.isInfoEnabled()) {
                            log.info("Successfully bound to TCP port: " + boundTcpPort);
                        }

                        break;
                    }
                    catch (BindException e) {
                        if (port + 1 < maxPort) {
                            if (log.isInfoEnabled()) {
                                log.info("Failed to bind to local TCP port (will try next port within range): " +
                                    port);
                            }
                        }
                        else {
                            throw e;
                        }
                    }
                }

                return tcpSock;
            }
        }

        /** {@inheritDoc} */
        @Override public void interrupt() {
            super.interrupt();

            synchronized (mux) {
                U.close(tcpSock, log);
            }
        }

        /** {@inheritDoc} */
        @Override protected void cleanup() {
            synchronized (mux) {
                // Potentially double closing, better safe than sorry.
                U.close(tcpSock, log);
            }
        }

        /** {@inheritDoc} */
        @Override public void body() throws InterruptedException {
            while (!isInterrupted()) {
                try {
                    ServerSocket server;

                    synchronized (mux) {
                        server = tcpSock;

                        if (server == null) {
                            server = createTcpSocket();
                        }
                    }

                    Socket sock = server.accept();

                    try {
                        InputStream in = sock.getInputStream();
                        OutputStream out = sock.getOutputStream();

                        try {
                            GridMulticastDiscoveryMessage msg =
                                U.unmarshal(marshaller, in, getClass().getClassLoader());

                            if (msg.getType() == PING_REQUEST) {
                                U.marshal(marshaller, new GridMulticastDiscoveryMessage(PING_RESPONSE),
                                    out);
                            }
                            else if (msg.getType() == ATTRS_REQUEST) {
                                UUID id = msg.getNodeId();

                                // Local node cannot communicate with itself.
                                assert !nodeId.equals(id);

                                // Get metrics outside the synchronization to avoid
                                // possible deadlocks.
                                GridNodeMetrics locMetrics = metricsProvider.getMetrics();

                                GridMulticastDiscoveryNode node;

                                synchronized (mux) {
                                    node = allNodes.get(id);

                                    if (node == null) {
                                        node = new GridMulticastDiscoveryNode(id, msg.getAddress(),
                                            msg.getPort(), msg.getStartTime(), msg.getMetrics());
                                    }

                                    assert id.equals(node.id());

                                    Map<String, Object> attrs = msg.getAttributes();

                                    // Send back own attributes. This requires confirmation (ATTRS_CONFIRMED).
                                    GridMulticastDiscoveryMessage confirmMsg =
                                        new GridMulticastDiscoveryMessage(ATTRS_RESPONSE, nodeId, locHost,
                                            boundTcpPort, nodeAttrs, startTime, locMetrics);

                                    U.marshal(marshaller, confirmMsg, out);

                                    msg = U.unmarshal(marshaller, in, getClass().getClassLoader());

                                    if (msg.getType() == ATTRS_CONFIRMED) {
                                        if (node.getState() != NEW) {
                                            if (log.isDebugEnabled()) {
                                                log.debug("Received handshake request from stopping node (will ignore): " +
                                                    node);
                                            }
                                        }
                                        else {
                                            node.setAttributes(attrs);

                                            allNodes.put(id, node);

                                            if (log.isDebugEnabled()) {
                                                log.debug("Node moved to READY: " + node);
                                            }

                                            rmtNodes = null;

                                            // Listener notification.
                                            notifyDiscovery(EVT_NODE_JOINED, node);
                                        }
                                    }
                                    else {
                                        U.warn(log, "Received message of wrong type [expected=ATTRS_CONFIRMED"
                                            + ", actual=" + msg.getType() + ']');

                                        continue;
                                    }

                                    // Notify threads waiting for node to change state.
                                    mux.notifyAll();
                                }

                                if (log.isDebugEnabled()) {
                                    log.debug("Added new node to the topology: " + node);
                                }
                            }
                            else {
                                U.error(log, "Received unknown message: " + msg);
                            }
                        }
                        finally {
                            U.close(out, log);
                            U.close(in, log);
                        }
                    }
                    catch (IOException e) {
                        if (!isStopping.get() && !isInterrupted()) {
                            U.error(log, "Failed to send local node attributes to remote node (did the node stop?): " +
                                sock, e);
                        }
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to send local node attributes to remote node.", e);
                    }
                    finally {
                        U.close(sock, log);
                    }
                }
                catch (IOException e) {
                    if (!isStopping.get() && !isInterrupted()) {
                        U.error(log, "Failed to accept remote TCP connections, will wait for " + beatFreq +
                            "ms and try again.", e);

                        synchronized (mux) {
                            U.close(tcpSock, log);

                            tcpSock = null;
                        }

                        Thread.sleep(beatFreq);
                    }
                }
            }
        }
    }

    /**
     * Node sweeper implementation that cleans up dead nodes. This thread looks after
     * available nodes list and removes those ones that did not send heartbeat message
     * last {@link GridMulticastDiscoverySpi#maxMissedBeats} * {@link GridMulticastDiscoverySpi#beatFreq}
     * milliseconds.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class NodeSweeper extends GridSpiThread {
        /**
         * Creates new instance of node sweeper.
         */
        NodeSweeper() {
            super(gridName, "grid-mcast-disco-node-sweeper", log);
        }

        /** {@inheritDoc} */
        @Override public void body() throws InterruptedException {
            long maxSilenceTime = beatFreq * maxMissedBeats;

            while (!isInterrupted()) {
                synchronized (mux) {
                    for (Iterator<GridMulticastDiscoveryNode> iter = allNodes.values().iterator();
                         iter.hasNext();) {
                        GridMulticastDiscoveryNode node = iter.next();

                        // Check if node needs to be removed from topology.
                        if (System.currentTimeMillis() - node.getLastHeartbeat() > maxSilenceTime) {
                            if (node.getState() != LEFT) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Removed failed node from topology: " + node);
                                }

                                boolean notify = node.getState() == READY;

                                node.onFailed();

                                assert node.getState() == LEFT : "Invalid node state: " + node.getState();

                                rmtNodes = null;

                                if (notify) {
                                    // Notify listener of failure only for ready nodes.
                                    notifyDiscovery(EVT_NODE_FAILED, node);
                                }

                                mux.notifyAll();
                            }

                            iter.remove();
                        }
                    }
                }

                Thread.sleep(beatFreq);
            }
        }
    }

    /** {@inheritDoc} */
    @Override protected List<String> getConsistentAttributeNames() {
        List<String> attrs = new ArrayList<String>(3);

        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_CLASS));
        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_VER));
        attrs.add(createSpiAttributeName(HEARTBEAT_ATTRIBUTE_KEY));

        return attrs;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMulticastDiscoverySpi.class, this);
    }
}
