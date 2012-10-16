// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.coherence;

import com.tangosol.net.*;
import com.tangosol.util.*;
import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.net.*;
import java.util.*;
import java.util.Map.*;
import java.util.UUID;
import java.util.concurrent.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.processors.port.GridPortProtocol.*;

/**
 * Oracle Coherence implementation of {@link GridDiscoverySpi} SPI. It uses Coherence
 * cluster capabilities discover remote nodes in grid. SPI works with Coherence distributed cache
 * named {@link #DFLT_GRIDGAIN_CACHE} and every node in the cluster works with that cache
 * to communicate with other remote nodes.
 * <p>
 * All grid nodes have information about Coherence cluster members they are associated with in
 * attribute by name {@link #ATTR_COHERENCE_MBR}. Use
 * {@link GridNode#getAttribute(String) GridNode.getAttribute(ATTR_COHERENCE_MBR)} to get a handle
 * on {@link GridCoherenceMember} class.
 * <p>
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * This SPI has the following optional parameters:
 * <ul>
 * <li>Coherence cache name (see {@link #setCacheName(String)}).</li>
 * <li>Grid node metrics update frequency (see {@link #setMetricsFrequency(long)}.</li>
 * </ul>
 * <h2 class="header">Java Example</h2>
 * GridCoherenceDiscoverySpi needs to be explicitly configured to override default Multicast discovery SPI.
 * <pre name="code" class="java">
 * GridCoherenceDiscoverySpi spi = new GridCoherenceDiscoverySpi();
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default discovery SPI.
 * cfg.setDiscoverySpi(spi);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * <p>
 * <h2 class="header">Spring Example</h2>
 * GridCoherenceDiscoverySpi can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *         ...
 *         &lt;property name="discoverySpi"&gt;
 *             &lt;bean class="org.gridgain.grid.spi.discovery.coherence.GridCoherenceDiscoverySpi"/&gt;
 *         &lt;/property&gt;
 *         ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <b>Note</b>: Coherence is not shipped with GridGain. If you don't have Coherence, you need to
 * download it separately. See <a target=_blank href="http://www.oracle.com/tangosol/index.html">http://www.oracle.com/tangosol/index.html</a> for
 * more information. Once installed, Coherence should be available on the classpath for
 * GridGain. If you use {@code ${GRIDGAIN_HOME}/bin/ggstart.{sh|bat}} script to start
 * a grid node you can simply add Coherence JARs to {@code ${GRIDGAIN_HOME}/bin/setenv.{sh|bat}}
 * scripts that's used to set up class path for the main scripts.
 * <p>
 * <b>Note</b>: When using Coherence SPIs (communication or discovery) you cannot start
 * multiple GridGain instances in the same VM due to limitations of Coherence. GridGain runtime
 * will detect this situation and prevent GridGain from starting in such case.
 * See {@link GridSpiMultipleInstancesSupport} for details.
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridDiscoverySpi
 */
@SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(false)
public class GridCoherenceDiscoverySpi extends GridSpiAdapter implements GridDiscoverySpi,
    GridCoherenceDiscoverySpiMBean {
    /**
     * Default Coherence configuration path relative to GridGain installation home folder
     * (value is {@code config/coherence/coherence.xml}).
     */
    public static final String DFLT_CONFIG_FILE = "config/coherence/coherence.xml";

    /** Default Coherence cache name (value is {@code gridgain.discovery.cache}). */
    public static final String DFLT_GRIDGAIN_CACHE = "gridgain.discovery.cache";

    /**
     * Name of cluster {@link GridCoherenceMember} attribute added to local node attributes
     * at startup (value is {@code disco.coherence.member}).
     */
    public static final String ATTR_COHERENCE_MBR = "disco.coherence.member";

    /** Name of Coherence cache used by SPI (value is {@code disco.coherence.cache}). */
    public static final String ATTR_COHERENCE_CACHE_NAME = "disco.coherence.cache";

    /** Default metrics heartbeat delay (value is {@code 3000}).*/
    public static final long DFLT_METRICS_FREQ = 3000;

    /** Prefix used for keys which nodes updates cache with new metrics value (value is {@code heartbeat_}).*/
    private static final String HEARTBEAT_KEY_PREFIX = "heartbeat_";

    /** Heartbeat attribute key should be the same on all nodes. */
    private static final String HEARTBEAT_ATTR_KEY = "gg:disco:heartbeat";

    /** */
    @GridLoggerResource
    private GridLogger log;

    /** */
    @GridLocalNodeIdResource
    private UUID nodeId;

    /** Map of all discovered nodes with different statuses. */
    private Map<UUID, GridCoherenceDiscoveryNode> allNodes = new HashMap<UUID, GridCoherenceDiscoveryNode>();

    /** Set of remote nodes that have state {@code READY}. */
    private List<GridNode> rmtNodes;

    /** Local node. */
    private GridCoherenceDiscoveryNode locNode;

    /** Discovery listener. */
    private GridDiscoverySpiListener lsnr;

    /** Local node attributes. */
    private Map<String, Object> nodeAttrs;

    /** Local node data. */
    private GridCoherenceDiscoveryNodeData locData;

    /** */
    private final Object mux = new Object();

    /** Coherence service name. */
    private String cacheName = DFLT_GRIDGAIN_CACHE;

    /** IoC configuration parameter to specify the name of the Coherence configuration file. */
    private String cfgFile = DFLT_CONFIG_FILE;

    /** */
    private String gridName;

    /** */
    private GridSpiThread metricsUpdater;

    /** Local node metrics provider. */
    private GridDiscoveryMetricsProvider metricsProvider;

    /** Delay between metrics requests. */
    private long metricsFreq = DFLT_METRICS_FREQ;

    /** */
    private NamedCache cache;

    /** */
    private MapListener cacheLsnr;

    /** */
    private MemberListener mbrListener;

    /** */
    private boolean stopping;

    /** Enable/disable flag for cache heartbeat updater. */
    private volatile boolean cacheBusy;

    /** */
    private ExecutorService clusterExec;

    /** Flag to start Coherence cache server on a dedicated daemon thread. */
    private boolean startCacheServerDaemon;

    /**
     * Sets name for Coherence cache used in grid.
     * <p>
     * If not provided, default value is {@link #DFLT_GRIDGAIN_CACHE}.
     *
     * @param cacheName Cache name.
     */
    @GridSpiConfiguration(optional = true)
    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    /** {@inheritDoc} */
    @Override public String getCacheName() {
        return cacheName;
    }

    /**
     * Sets delay between metrics requests. SPI sends broadcast messages in
     * configurable time interval to other nodes to notify them about its metrics.
     * <p>
     * If not provided the default value is {@link #DFLT_METRICS_FREQ}.
     *
     * @param metricsFreq Time in milliseconds.
     */
    @GridSpiConfiguration(optional = true)
    public void setMetricsFrequency(long metricsFreq) {
        this.metricsFreq = metricsFreq;
    }

    /** {@inheritDoc} */
    @Override public long getMetricsFrequency() {
        return metricsFreq;
    }

    /**
     * Sets either absolute or relative to GridGain installation home folder path to Coherence XML
     * configuration file. This configuration parameter is optional.
     * <p>
     * If not provided, default value is {@link #DFLT_CONFIG_FILE}.
     *
     * @param cfgFile Path to Coherence configuration file.
     */
    @GridSpiConfiguration(optional = true)
    public void setConfigurationFile(String cfgFile) {
        this.cfgFile = cfgFile;
    }

    /** {@inheritDoc} */
    @Override public String getConfigurationFile() {
        return cfgFile;
    }

    /** {@inheritDoc} */
    @Override public boolean isStartCacheServerDaemon() {
        return startCacheServerDaemon;
    }

    /**
     * Sets flag to start Coherence cache server on a dedicated daemon thread.
     * This configuration parameter is optional.
     * See <a href="http://download.oracle.com/otn_hosted_doc/coherence/342/com/tangosol/net/DefaultCacheServer.html">DefaultCacheServer</a>
     * for more information.
     * Note that Coherence cluster services used in SPI should be declared
     * as "autostart" in configuration with started {@code DefaultCacheServer}
     * to avoid reconnection problem between grid nodes.
     * If not provided, default value is {@code false}.
     *
     * @param startCacheServerDaemon Flag indicates whether
     *      Coherence DefaultCacheServer should be started in SPI or not.
     */
    @GridSpiConfiguration(optional = true)
    public void setStartCacheServerDaemon(boolean startCacheServerDaemon) {
        this.startCacheServerDaemon = startCacheServerDaemon;
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

                for (GridCoherenceDiscoveryNode node : allNodes.values()) {
                    if (!node.equals(locNode)) {
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
    @Override public GridNode getNode(UUID nodeId) {
        assert nodeId != null;

        if (locNode.id().equals(nodeId)) {
            return locNode;
        }

        synchronized (mux) {
            return allNodes.get(nodeId);
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
        return F.<String, Object>asMap(createSpiAttributeName(HEARTBEAT_ATTR_KEY), getMetricsFrequency());
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void spiStart(String gridName) throws GridSpiException {
        this.gridName = gridName;

        // Start SPI start stopwatch.
        startStopwatch();

        assertParameter(cacheName != null, "cacheName != null");
        assertParameter(metricsFreq > 0, "metricsFrequency > 0");

        // Ack parameters.
        if (log.isDebugEnabled()) {
            log.debug(configInfo("cacheName", cacheName));
            log.debug(configInfo("metricsFrequency", metricsFreq));
            log.debug(configInfo("cfgFile", cfgFile));
            log.debug(configInfo("startCacheServerDaemon", startCacheServerDaemon));
        }

        assertParameter(cacheName != null, "cacheName != null");

        clusterExec = Executors.newCachedThreadPool(
            new GridSpiThreadFactory(gridName, "grid-disco-coherence-restarter", log));

        stopping = false;

        if (cfgFile != null) {
            URL cfgUrl = U.resolveGridGainUrl(cfgFile);

            if (cfgUrl == null) {
                throw new GridSpiException("Invalid Coherence configuration file: " + cfgFile);
            }
            else if (log.isDebugEnabled()) {
                log.debug("Coherence configuration: " + cfgUrl);
            }

            ConfigurableCacheFactory factory = new DefaultConfigurableCacheFactory(cfgUrl.toString());

            // Specify singleton factory.
            CacheFactory.setConfigurableCacheFactory(factory);

            // Start all services that are declared as requiring an "autostart" in the configurable factory.
            DefaultCacheServer.start(factory);

            // Get coherence cache defined in configuration file.
            cache = factory.ensureCache(cacheName, getClass().getClassLoader());
        }
        else {
            cache = CacheFactory.getCache(cacheName);
        }

        if (cache == null) {
            throw new GridSpiException("Failed to create coherence cache.");
        }

        if (startCacheServerDaemon) {
            // Start the cache server on a dedicated daemon thread.
            DefaultCacheServer.startDaemon();
        }

        Member locMbr = CacheFactory.getCluster().getLocalMember();

        Map<String, Object> attrs = new HashMap<String, Object>(nodeAttrs);

        attrs.put(ATTR_COHERENCE_MBR, new GridCoherenceMember(locMbr));
        attrs.put(ATTR_COHERENCE_CACHE_NAME, cacheName);

        nodeAttrs = attrs;

        locNode = new GridCoherenceDiscoveryNode(locMbr.getAddress(), locMbr.getUid().toByteArray(), metricsProvider);

        locData = new GridCoherenceDiscoveryNodeData(nodeId, locMbr.getUid().toByteArray(), locMbr.getAddress(),
            nodeAttrs, metricsProvider.getMetrics());

        locNode.onDataReceived(locData);

        cacheLsnr = createMapListener();
        mbrListener = createServiceMembershipListener();

        // Process all cache entries.
        synchronized (mux) {
            // Add local node data. Put it in mux to avoid finding this node
            // on remote.
            cache.put(locNode.id(), locData);

            // Add map listener for cache.
            cache.addMapListener(cacheLsnr);

            // Add listener for cluster event notifications.
            cache.getCacheService().addMemberListener(mbrListener);

            for (Entry entry : (Set<Entry>)cache.entrySet()) {
                if (entry.getKey() instanceof UUID) {
                    if (entry.getValue() instanceof GridCoherenceDiscoveryNodeData) {
                        if (log.isDebugEnabled()) {
                            log.debug("Found node data in cache [key=" + entry.getKey() +
                                ", value=" + entry.getValue() + ']');
                        }

                        // Handle node data from cache.
                        processNewOrUpdatedNode((UUID)entry.getKey(), (GridCoherenceDiscoveryNodeData)entry.getValue(),
                            false);
                    }
                    else {
                        U.warn(log, "Unknown node data type found during SPI start [nodeId=" + entry.getKey() +
                            ", nodeDataClass=" + (entry.getValue() == null ? null :
                            entry.getValue().getClass().getName()));
                    }
                }
            }

            // Refresh returned collection.
            rmtNodes = null;
        }

        metricsUpdater = new CoherenceNodesMetricsUpdater();

        metricsUpdater.start();

        registerMBean(gridName, this, GridCoherenceDiscoverySpiMBean.class);

        // Ack ok start.
        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void spiStop() throws GridSpiException {
        stopping = true;

        if (cache != null && locData != null) {
            locData.setLeave(true);

            // Update cache and all nodes should update source node flag.
            cache.put(nodeId, locData);
        }

        U.interrupt(metricsUpdater);
        U.join(metricsUpdater, log);

        metricsUpdater = null;

        if (cache != null) {
            // Remove cache listener and local node object with attributes.
            cache.removeMapListener(cacheLsnr);
            cache.remove(nodeId);

            // Remove listener for cluster event notifications.
            cache.getCacheService().removeMemberListener(mbrListener);
        }

        // Stop coherence restarter thread pool.
        U.shutdownNow(getClass(), clusterExec, log);

        // Shutdown the cache server.
        if (startCacheServerDaemon) {
            DefaultCacheServer.shutdown();
        }

        cache = null;
        cacheLsnr = null;
        mbrListener = null;
        rmtNodes = null;
        locData = null;

        // Unregister SPI MBean.
        unregisterMBean();

        // Ack ok stop.
        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void onContextInitialized(GridSpiContext spiCtx) throws GridSpiException {
        super.onContextInitialized(spiCtx);

        getSpiContext().registerPort(cache.getCacheService().getCluster().getLocalMember().getPort(), TCP);
    }

    /** {@inheritDoc} */
    @Override public void onContextDestroyed() {
        getSpiContext().deregisterPorts();

        super.onContextDestroyed();
    }

    /** {@inheritDoc} */
    @Override public boolean pingNode(UUID nodeId) {
        assert nodeId != null;

        if (this.nodeId.equals(nodeId)) {
            return true;
        }

        synchronized (mux) {
            for (GridCoherenceDiscoveryNode node : allNodes.values()) {
                if (node.id() != null && nodeId.equals(node.id())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Creates map listener for nodes in replicated cache.
     *
     * @return Listener.
     */
    private MapListener createMapListener() {
        return new MapListener() {
            @Override public void entryInserted(MapEvent evt) {
                if (log.isDebugEnabled()) {
                    log.debug("Inserted in cache [key=" + evt.getKey() + ", value=" + evt.getNewValue() + ']');
                }

                if (evt.getKey() instanceof UUID) {
                    assert evt.getNewValue() instanceof GridCoherenceDiscoveryNodeData;

                    processNewOrUpdatedNode((UUID)evt.getKey(), (GridCoherenceDiscoveryNodeData)evt.getNewValue(),
                        !nodeId.equals(evt.getKey()));
                }
            }

            /** {@inheritDoc} */
            @Override public void entryUpdated(MapEvent evt) {
                if (log.isDebugEnabled()) {
                    log.debug("Updated in cache [key=" + evt.getKey() + ", oldValue=" + evt.getOldValue() +
                        ", newValue=" + evt.getNewValue() + ']');
                }

                if (evt.getKey() instanceof UUID) {
                    assert evt.getNewValue() instanceof GridCoherenceDiscoveryNodeData;

                    processNewOrUpdatedNode((UUID)evt.getKey(), (GridCoherenceDiscoveryNodeData)evt.getNewValue(),
                        !nodeId.equals(evt.getKey()));
                }
            }

            /** {@inheritDoc} */
            @Override public void entryDeleted(MapEvent evt) {
                if (log.isDebugEnabled()) {
                    log.debug("Deleted in cache [key=" + evt.getKey() + ", oldValue=" + evt.getOldValue() +
                        ", newValue=" + evt.getNewValue() + ']');
                }

                if (evt.getKey() instanceof UUID) {
                    processDeletedNode((UUID)evt.getKey(), !nodeId.equals(evt.getKey()));
                }
            }
        };
    }

    /**
     * Creates membership listener for cluster.
     *
     * @return Listener.
     */
    private MemberListener createServiceMembershipListener() {
        return new MemberListener() {
            @SuppressWarnings("unchecked")
            @Override public void memberJoined(MemberEvent evt) {
                Member mbr = evt.getMember();

                if (log.isDebugEnabled()) {
                    log.debug("Coherence cluster member joined: " + getMemberInfo(mbr));
                }

                // If local node joined in cluster again then we need to create
                // new local node and rescan cache for exist nodes.
                // Usually local node joined in cluster after disconnection (and local cluster
                // services will be restarted).
                if (evt.isLocal()) {
                    final Member locMbr = CacheFactory.getCluster().getLocalMember();

                    assert mbr.getUid().equals(locMbr.getUid());

                    U.warn(log, "Local node rejoined. Perhaps Coherence services was restarted [oldMbrUid=" +
                        U.byteArray2HexString(locNode.getMemberUid()) +
                        ", newMbrUid=" + U.byteArray2HexString(locMbr.getUid().toByteArray()) + ']');

                    final Map<String, Object> attrs = new HashMap<String, Object>(nodeAttrs);

                    attrs.put(ATTR_COHERENCE_MBR, new GridCoherenceMember(locMbr));
                    attrs.put(ATTR_COHERENCE_CACHE_NAME, cacheName);

                    // Cluster services should be restarted after apply in separate thread.
                    clusterExec.submit(new Runnable() {
                        @Override public void run() {
                            synchronized (mux) {
                                nodeAttrs = attrs;

                                // Create new local node with new attributes.
                                locNode = new GridCoherenceDiscoveryNode(locMbr.getAddress(),
                                    locMbr.getUid().toByteArray(), metricsProvider);

                                locData = new GridCoherenceDiscoveryNodeData(nodeId, locMbr.getUid().toByteArray(),
                                    locMbr.getAddress(), nodeAttrs, metricsProvider.getMetrics());

                                locNode.onDataReceived(locData);

                                // Add local node data. Put it in mux to avoid finding this node on remote.
                                cache.put(locNode.id(), locData);

                                Collection<UUID> nodeIds = new HashSet<UUID>(allNodes.keySet());

                                // Process all cache entries.
                                for (Entry entry : (Set<Entry>)cache.entrySet()) {
                                    if (entry.getKey() instanceof UUID) {
                                        UUID uid = (UUID)entry.getKey();

                                        Object val = entry.getValue();

                                        if (entry.getValue() instanceof GridCoherenceDiscoveryNodeData) {
                                            GridCoherenceDiscoveryNodeData data =
                                                (GridCoherenceDiscoveryNodeData)entry.getValue();

                                            if (log.isDebugEnabled()) {
                                                log.debug("Found node data in cache [key=" + uid + ", value=" + data +
                                                    ']');
                                            }

                                            // Handle node data from cache.
                                            processNewOrUpdatedNode(uid, data, !nodeId.equals(uid));
                                        }
                                        else {
                                            U.warn(log, "Unknown node data type found during node rejoin " +
                                                "[nodeId=" + uid + ", nodeDataClass=" +
                                                (val == null ? null : val.getClass().getName()));
                                        }

                                        nodeIds.remove(uid);
                                    }
                                }

                                // Process disappeared nodes.
                                for (UUID id : nodeIds) {
                                    boolean notify = !nodeId.equals(id);
                                    // Print deleted and current nodes collection.
                                    if (log.isDebugEnabled()) {
                                        log.debug("Process disappeared node [nodeId=" + id +
                                            ", allNodes=" + getAllNodesIdsInfo() + ']');
                                    }

                                    for (Iterator<GridCoherenceDiscoveryNode> iter = allNodes.values().iterator();
                                         iter.hasNext();) {
                                        GridCoherenceDiscoveryNode node = iter.next();

                                        if (id.equals(node.id())) {
                                            iter.remove();

                                            if (log.isInfoEnabled()) {
                                                log.info("Node " + (node.isLeaving() ? "left" : "failed") +
                                                    ": " + node);
                                            }

                                            if (notify) {
                                                notifyDiscovery(node.isLeaving() ?
                                                    EVT_NODE_LEFT :
                                                    EVT_NODE_FAILED, node);
                                            }

                                            break;
                                        }
                                    }
                                }

                                // Refresh returned collection.
                                rmtNodes = null;

                                // Mark cache as not busy to enable heartbeat updater.
                                cacheBusy = false;

                                if (log.isDebugEnabled()) {
                                    log.debug("Enable cache heartbeat updater.");
                                }
                            }
                        }
                    });
                }

                if (log.isDebugEnabled()) {
                    log.debug("Return from 'memberJoined' listener apply: " + nodeId);
                }
            }

            /** {@inheritDoc} */
            @Override public void memberLeaving(MemberEvent evt) {
                Member mbr = evt.getMember();

                if (log.isDebugEnabled()) {
                    log.debug("Coherence cluster member leaving: " + getMemberInfo(mbr));
                }
            }

            /** {@inheritDoc} */
            @Override public void memberLeft(MemberEvent evt) {
                final Member mbr = evt.getMember();

                if (log.isDebugEnabled()) {
                    log.debug("Coherence member left: " + getMemberInfo(mbr));
                }

                // Cluster services should be restarted after apply in separate thread.
                clusterExec.submit(new Runnable() {
                    @Override public void run() {
                        synchronized (mux) {
                            for (Iterator<GridCoherenceDiscoveryNode> iter = allNodes.values().iterator();
                                 iter.hasNext();) {
                                GridCoherenceDiscoveryNode node = iter.next();

                                if (Arrays.equals(node.getMemberUid(), mbr.getUid().toByteArray())) {
                                    if (log.isInfoEnabled()) {
                                        log.info("Node " + (node.isLeaving() ? "left" : "failed") + ": " + node);
                                    }

                                    iter.remove();

                                    // Refresh returned collection.
                                    rmtNodes = null;

                                    // Remove local node from collection if local node left cluster.
                                    // There is code fix for disconnected nodes when local node should be rejoined.
                                    // Local node will be created as new when Coherence cluster services restarted.
                                    if (Arrays.equals(locNode.getMemberUid(), node.getMemberUid())) {
                                        // Mark cache as busy to disable heartbeat updater.
                                        cacheBusy = true;

                                        if (log.isDebugEnabled()) {
                                            log.debug("Disable cache heartbeat updater.");
                                        }

                                        U.warn(log, "Coherence cluster services was stopped on local node and member" +
                                            " has left cluster. Node was disconnected: " + nodeId);
                                    }
                                    else {
                                        notifyDiscovery(node.isLeaving() ?
                                            EVT_NODE_LEFT :
                                            EVT_NODE_FAILED, node);

                                        if (log.isDebugEnabled()) {
                                            log.debug("Removed node from cache: " + node);
                                        }

                                        cache.remove(node.id());
                                        cache.remove(HEARTBEAT_KEY_PREFIX + node.id());
                                    }

                                    break;
                                }
                            }
                        }

                        // This apply used to restart cluster service on local node if stopped.
                        cache.getCacheService().getCluster().getLocalMember();
                    }
                });

                if (log.isDebugEnabled()) {
                    log.debug("Return from 'memberLeft' listener apply: " + nodeId);
                }
            }
        };
    }

    /**
     * @param key TODO
     * @param nodeData TODO
     * @param notify TODO
     */
    private void processNewOrUpdatedNode(UUID key, GridCoherenceDiscoveryNodeData nodeData, boolean notify) {
        assert key != null;
        assert nodeData != null;

        synchronized (mux) {
            // Print new/updated node and current nodes collection.
            if (log.isDebugEnabled()) {
                log.debug("Process new or updated node [nodeId=" + key + ", allNodes=" + getAllNodesIdsInfo() + ']');
            }

            GridCoherenceDiscoveryNode node = getAnyNode(key);

            if (node == null) {
                node = new GridCoherenceDiscoveryNode(nodeData);

                allNodes.put(node.id(), node);

                // Refresh returned collection.
                rmtNodes = null;

                // Call users listeners outside the synchronization.
                if (log.isDebugEnabled()) {
                    log.debug("Node joined: " + node);
                }

                if (notify) {
                    notifyDiscovery(EVT_NODE_JOINED, node);
                }
            }
            // Set leaving flag for target node.
            // It is used for differs situations when node fails or normal turned off.
            else if (nodeData.isLeave()) {
                node.onLeaving();
            }
        }
    }

    /**
     * @param key Node id.
     * @param notify Notify flag.
     */
    private void processDeletedNode(UUID key, boolean notify) {
        assert key != null;

        synchronized (mux) {
            // Print deleted and current nodes collection.
            if (log.isDebugEnabled()) {
                log.debug("Process deleted node [nodeId=" + key + ", allNodes=" + getAllNodesIdsInfo() + ']');
            }

            for (Iterator<GridCoherenceDiscoveryNode> iter = allNodes.values().iterator(); iter.hasNext();) {
                GridCoherenceDiscoveryNode node = iter.next();

                if (key.equals(node.id())) {
                    iter.remove();

                    // Refresh returned collection.
                    rmtNodes = null;

                    if (log.isInfoEnabled()) {
                        log.info("Node " + (node.isLeaving() ? "left" : "failed") + ": " + node);
                    }

                    if (notify) {
                        notifyDiscovery(node.isLeaving() ?
                            EVT_NODE_LEFT :
                            EVT_NODE_FAILED, node);
                    }

                    break;
                }
            }
        }
    }

    /**
     * Method is called when any discovery event occurs.
     *
     * @param type Discovery event type. See {@link org.gridgain.grid.events.GridDiscoveryEvent} for more details.
     * @param node Remote node this event is connected with.
     */
    private void notifyDiscovery(int type, GridNode node) {
        GridDiscoverySpiListener localCopy = lsnr;

        if (localCopy != null) {
            localCopy.onDiscovery(type, node);
        }
    }

    /**
     * Prepare member data info for logging.
     *
     * @param mbr Cluster member.
     * @return String with cluster member information.
     */
    @Nullable private String getMemberInfo(Member mbr) {
        if (mbr == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        builder.append("[uid=").append(U.byteArray2HexString(mbr.getUid().toByteArray()));
        builder.append(", id=").append(mbr.getId());
        builder.append(", addr=").append(mbr.getAddress());
        builder.append(", port=").append(mbr.getPort());
        builder.append(", machineId=").append(mbr.getMachineId());
        builder.append(", machineName=").append(mbr.getMachineName());
        builder.append(", processName=").append(mbr.getProcessName());
        builder.append(']');

        return builder.toString();
    }

    /**
     * Prepare all nodes ID's data info for logging.
     *
     * @return String with nodes ID's.
     */
    private String getAllNodesIdsInfo() {
        assert Thread.holdsLock(mux);

        StringBuilder builder = new StringBuilder();

        Set<UUID> set = allNodes.keySet();

        builder.append('{');

        int i = 1;

        for (UUID uuid : set) {
            builder.append(uuid);

            if (i < set.size()) {
                builder.append(", ");
            }

            i++;
        }

        builder.append('}');

        return builder.toString();
    }

    /**
     * Make node search by node id in collection of discovered nodes.
     *
     * @param nodeId Node id.
     * @return Coherence node.
     */
    private GridCoherenceDiscoveryNode getAnyNode(UUID nodeId) {
        synchronized (mux) {
            return allNodes.get(nodeId);
        }
    }

    /**
     * Gets local node metrics.
     *
     * @return Local node metrics.
     */
    private byte[] getLocalNodeMetrics() {
        byte[] data = new byte[GridDiscoveryMetricsHelper.METRICS_SIZE];

        // Local node metrics.
        GridDiscoveryMetricsHelper.serialize(data, 0, metricsProvider.getMetrics());

        return data;
    }

    /**
     * Cluster metrics sender.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class CoherenceNodesMetricsUpdater extends GridSpiThread {
        /** Heartbeat cache listener. */
        private final CoherenceStateListener heartbeatLsnr;

        /**
         * Creates new nodes metrics updater.
         */
        CoherenceNodesMetricsUpdater() {
            super(gridName, "grid-disco-coherence-metrics-updater", log);

            heartbeatLsnr = new CoherenceStateListener();

            /* Register heartbeat listener. */
            cache.addMapListener(heartbeatLsnr);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked", "BusyWait"})
        @Override protected void body() throws InterruptedException {
            try {
                while (!isInterrupted()) {
                    if (!cacheBusy) {
                        byte[] localNodeMetrics = getLocalNodeMetrics();

                        // Check that cache contains local node data.
                        if (!cache.containsKey(nodeId)) {
                            U.warn(log, "Cache doesn't contain local node: " + nodeId);

                            cache.put(nodeId, locData);
                        }

                        // Heartbeats based on string keys.
                        cache.put(HEARTBEAT_KEY_PREFIX + nodeId.toString(), localNodeMetrics, metricsFreq);

                        if (log.isDebugEnabled()) {
                            log.debug("Local node metrics were published.");
                        }
                    }

                    Thread.sleep(metricsFreq);
                }
            }
            catch (InterruptedException e) {
                // Do not re-throw if it is being stopped.
                if (!stopping) {
                    throw e;
                }
            }
            finally {
                cache.removeMapListener(heartbeatLsnr);

                cache.remove(HEARTBEAT_KEY_PREFIX + nodeId.toString());
            }
        }

        /**
         * Listener that updates remote nodes metrics.
         *
         * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
         * @version 3.0.9c.19052011
         */
        private class CoherenceStateListener implements MapListener {
            /** {@inheritDoc} */
            @Override public void entryInserted(MapEvent evt) { updateMetric(evt); }

            /** {@inheritDoc} */
            @Override public void entryUpdated(MapEvent evt) { updateMetric(evt); }

            /** {@inheritDoc} */
            @Override public void entryDeleted(MapEvent evt) { /* No-op. */ }

            /**
             * Update metrics for node.
             *
             * @param evt TODO
             */
            private void updateMetric(MapEvent evt) {
                assert evt != null;

                if (evt.getKey() instanceof String) {
                    String key = (String)evt.getKey();

                    int idx = key.indexOf(HEARTBEAT_KEY_PREFIX);

                    if (idx != -1) {
                        UUID keyNodeId;

                        try {
                            keyNodeId = UUID.fromString(key.substring(idx + HEARTBEAT_KEY_PREFIX.length()));
                        }
                        catch (IllegalArgumentException e) {
                            U.error(log, "Failed to get nodeId from key: " + key, e);

                            return;
                        }

                        assert keyNodeId != null;

                        byte[] metrics = (byte[]) evt.getNewValue();

                        GridCoherenceDiscoveryNode node;

                        boolean notify = false;

                        synchronized(mux) {
                            node = getAnyNode(keyNodeId);

                            if (node != null) {
                                // Ignore local node.
                                if (!node.id().equals(nodeId)) {
                                    node.onMetricsReceived(GridDiscoveryMetricsHelper.deserialize(metrics, 0));

                                    if (log.isDebugEnabled()) {
                                        log.debug("Node metrics were updated: " + node);
                                    }
                                }

                                notify = true;
                            }
                            else {
                                U.warn(log, "Received metrics from unknown node: " + keyNodeId);
                            }
                        }

                        if (notify) {
                            assert node != null;

                            // Notify about new metrics update for remote node.
                            notifyDiscovery(EVT_NODE_METRICS_UPDATED, node);
                        }
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override protected List<String> getConsistentAttributeNames() {
        List<String> attrs = new ArrayList<String>(3);

        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_CLASS));
        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_VER));
        attrs.add(createSpiAttributeName(HEARTBEAT_ATTR_KEY));

        return attrs;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCoherenceDiscoverySpi.class, this);
    }
}


