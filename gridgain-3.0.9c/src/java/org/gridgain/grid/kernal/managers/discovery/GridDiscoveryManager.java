// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.discovery;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.kernal.processors.jobmetrics.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.spi.metrics.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridNodeAttributes.*;

/**
 * Discovery SPI manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDiscoveryManager extends GridManagerAdapter<GridDiscoverySpi> {
    /** System line separator. */
    private static final String NL = System.getProperty("line.separator");

    /** */
    private static final String PREFIX = "Topology snapshot";

    /** Predicate filtering out daemon nodes. */
    private static final GridPredicate<GridNode> daemonFilter = new P1<GridNode>() {
        @Override public boolean apply(GridNode n) {
            return !isDaemon(n);
        }
    };

    /** Alive filter. */
    private final GridPredicate<GridNode> aliveFilter = new P1<GridNode>() {
        @Override public boolean apply(GridNode n) {
            return node(n.id()) != null;
        }
    };

    /** Discovery event worker. */
    private DiscoveryWorker discoWrk = new DiscoveryWorker();

    /** Discovery event worker thread. */
    private GridThread discoWrkThread;

    /** */
    private final AtomicLong lastLoggedTop = new AtomicLong(0);

    /** Local node. */
    private GridNode locNode;

    /** */
    private boolean isLocDaemon;

    /** */
    private final Object mux = new Object();

    /**
     * @param ctx Context.
     */
    public GridDiscoveryManager(GridKernalContext ctx) {
        super(GridDiscoverySpi.class, ctx, ctx.config().getDiscoverySpi());
    }

    /**
     * Sets local node attributes into discovery SPI.
     *
     * @param attrs Attributes to set.
     */
    public void setNodeAttributes(Map<String, Object> attrs) {
        getSpi().setNodeAttributes(attrs);
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        getSpi().setMetricsProvider(new GridDiscoveryMetricsProvider() {
            /** */
            private final long startTime = System.currentTimeMillis();

            /** {@inheritDoc} */
            @Override public GridNodeMetrics getMetrics() {
                GridJobMetrics jm = ctx.jobMetric().getJobMetrics();

                GridDiscoveryMetricsAdapter nm = new GridDiscoveryMetricsAdapter();

                nm.setLastUpdateTime(System.currentTimeMillis());

                // Job metrics.
                nm.setMaximumActiveJobs(jm.getMaximumActiveJobs());
                nm.setCurrentActiveJobs(jm.getCurrentActiveJobs());
                nm.setAverageActiveJobs(jm.getAverageActiveJobs());
                nm.setMaximumWaitingJobs(jm.getMaximumWaitingJobs());
                nm.setCurrentWaitingJobs(jm.getCurrentWaitingJobs());
                nm.setAverageWaitingJobs(jm.getAverageWaitingJobs());
                nm.setMaximumRejectedJobs(jm.getMaximumRejectedJobs());
                nm.setCurrentRejectedJobs(jm.getCurrentRejectedJobs());
                nm.setAverageRejectedJobs(jm.getAverageRejectedJobs());
                nm.setMaximumCancelledJobs(jm.getMaximumCancelledJobs());
                nm.setCurrentCancelledJobs(jm.getCurrentCancelledJobs());
                nm.setAverageCancelledJobs(jm.getAverageCancelledJobs());
                nm.setTotalRejectedJobs(jm.getTotalRejectedJobs());
                nm.setTotalCancelledJobs(jm.getTotalCancelledJobs());
                nm.setTotalExecutedJobs(jm.getTotalExecutedJobs());
                nm.setMaximumJobWaitTime(jm.getMaximumJobWaitTime());
                nm.setCurrentJobWaitTime(jm.getCurrentJobWaitTime());
                nm.setAverageJobWaitTime(jm.getAverageJobWaitTime());
                nm.setMaximumJobExecuteTime(jm.getMaximumJobExecuteTime());
                nm.setCurrentJobExecuteTime(jm.getCurrentJobExecuteTime());
                nm.setAverageJobExecuteTime(jm.getAverageJobExecuteTime());
                nm.setCurrentIdleTime(jm.getCurrentIdleTime());
                nm.setTotalIdleTime(jm.getTotalIdleTime());
                nm.setAverageCpuLoad(jm.getAverageCpuLoad());

                GridLocalMetrics lm = ctx.localMetric().getMetrics();

                // VM metrics.
                nm.setAvailableProcessors(lm.getAvailableProcessors());
                nm.setCurrentCpuLoad(lm.getCurrentCpuLoad());
                nm.setHeapMemoryInitialized(lm.getHeapMemoryInitialized());
                nm.setHeapMemoryUsed(lm.getHeapMemoryUsed());
                nm.setHeapMemoryCommitted(lm.getHeapMemoryCommitted());
                nm.setHeapMemoryMaximum(lm.getHeapMemoryMaximum());
                nm.setNonHeapMemoryInitialized(lm.getNonHeapMemoryInitialized());
                nm.setNonHeapMemoryUsed(lm.getNonHeapMemoryUsed());
                nm.setNonHeapMemoryCommitted(lm.getNonHeapMemoryCommitted());
                nm.setNonHeapMemoryMaximum(lm.getNonHeapMemoryMaximum());
                nm.setUpTime(lm.getUptime());
                nm.setStartTime(lm.getStartTime());
                nm.setNodeStartTime(startTime);
                nm.setCurrentThreadCount(lm.getThreadCount());
                nm.setMaximumThreadCount(lm.getPeakThreadCount());
                nm.setTotalStartedThreadCount(lm.getTotalStartedThreadCount());
                nm.setCurrentDaemonThreadCount(lm.getDaemonThreadCount());
                nm.setFileSystemFreeSpace(lm.getFileSystemFreeSpace());
                nm.setFileSystemTotalSpace(lm.getFileSystemTotalSpace());
                nm.setFileSystemUsableSpace(lm.getFileSystemUsableSpace());

                // Data metrics.
                nm.setLastDataVersion(ctx.cache().lastDataVersion());

                return nm;
            }
        });

        // Start discovery worker.
        discoWrkThread = new GridThread(ctx.gridName(), "disco-event-worker", discoWrk);

        discoWrkThread.start();

        getSpi().setListener(new GridDiscoverySpiListener() {
            @Override public void onDiscovery(int type, GridNode node) {
                discoWrk.addEvent(type, node);
            }
        });

        startSpi();

        checkAttributes();

        locNode = getSpi().getLocalNode();

        isLocDaemon = isDaemon(locNode);

        ackTopology();

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /**
     * Checks whether edition and build version of the local node
     * are consistent with remote nodes.
     *
     * @throws GridException If check fails.
     */
    private void checkAttributes() throws GridException {
        GridNode locNode = getSpi().getLocalNode();

        assert locNode != null;

        // Fetch local node attributes once.
        String locEnt = locNode.attribute(ATTR_ENT_EDITION);
        String locBuildVer = locNode.attribute(ATTR_BUILD_VER);

        for (GridNode n : getSpi().getRemoteNodes()) {
            String rmtEnt = n.attribute(ATTR_ENT_EDITION);
            String rmtBuildVer = n.attribute(ATTR_BUILD_VER);

            if (!F.eq(rmtEnt, locEnt))
                throw new GridException("Local node's edition differs from remote node's " +
                    "(all nodes in topology should have identical edition) " +
                    "[locEdition=" + editionName(locEnt) + ", rmtEdition=" + editionName(rmtEnt) +
                    ", locNode=" + locNode + ", rmtNode=" + n + ']');

            if (!F.eq(rmtBuildVer, locBuildVer))
                throw new GridException("Local node's build version differs from remote node's " +
                    "(all nodes in topology should have identical build version) " +
                    "[locBuildVer=" + locBuildVer + ", rmtBuildVer=" + rmtBuildVer +
                    ", locNode=" + locNode + ", rmtNode=" + n + ']');
        }

        if (log.isDebugEnabled())
            log.debug("Finished node attributes consistency check.");
    }

    /**
     * Returns edition name.
     *
     * @param ent {@code 'True'} string for enterprise edition.
     * @return Edition name.
     */
    private String editionName(String ent) {
        return "true".equalsIgnoreCase(ent) ? "Enterprise edition" : "Community edition";
    }

    /**
     * Tests whether this node is a daemon node.
     *
     * @param node Node to test.
     * @return {@code True} if given node is daemon.
     */
    private static boolean isDaemon(GridNode node) {
        return "true".equalsIgnoreCase(node.<String>attribute(ATTR_DAEMON));
    }

    /**
     * Logs grid size for license compliance.
     */
    private void ackTopology() {
        Collection<GridNode> rmtNodes = remoteNodes();

        GridNode locNode = getSpi().getLocalNode();

        Collection<GridNode> allNodes = new ArrayList<GridNode>(rmtNodes.size() + 1);

        allNodes.addAll(rmtNodes);
        allNodes.add(locNode);

        long hash = topologyHash(allNodes);

        // Prevent ack-ing topology for the same topology.
        // Can happen only during node startup.
        if (lastLoggedTop.getAndSet(hash) == hash)
            return;

        int totalCpus = ctx.grid().cpus();

        if (log.isQuiet())
            U.quiet(PREFIX + " [" +
                "nodes=" + (rmtNodes.size() + 1) +
                ", CPUs=" + totalCpus +
                ", hash=0x" + Long.toHexString(hash).toUpperCase() +
                ']');
        else if (log.isDebugEnabled()) {
            String dbg = "";

            dbg += NL + NL +
                ">>> +----------------+" + NL +
                ">>> " + PREFIX + "." + NL +
                ">>> +----------------+" + NL +
                ">>> Grid name: " + (ctx.gridName() == null ? "default" : ctx.gridName()) + NL +
                ">>> Number of nodes: " + (rmtNodes.size() + 1) + NL +
                ">>> Topology hash: 0x" + Long.toHexString(hash).toUpperCase() + NL;

            dbg += ">>> Local: " +
                locNode.id().toString().toUpperCase() + ", " +
                getAddresses(locNode) + ", " +
                locNode.attribute("os.name") + ' ' +
                locNode.attribute("os.arch") + ' ' +
                locNode.attribute("os.version") + ", " +
                System.getProperty("user.name") + ", " +
                locNode.attribute("java.runtime.name") + ' ' +
                locNode.attribute("java.runtime.version") + NL;

            for (GridNode node : rmtNodes)
                dbg += ">>> Remote: " +
                    node.id().toString().toUpperCase() +  ", " +
                    getAddresses(node) +  ", " +
                    node.attribute("os.name") + ' ' +
                    node.attribute("os.arch") +  ' ' +
                    node.attribute("os.version") + ", " +
                    node.attribute(ATTR_USER_NAME) + ", " +
                    node.attribute("java.runtime.name") + ' ' +
                    node.attribute("java.runtime.version") + NL;

            dbg += ">>> Total number of CPUs: " + totalCpus + NL;

            log.debug(dbg);
        }
        else if (log.isInfoEnabled())
            log.info(PREFIX + " [" +
                "nodes=" + (rmtNodes.size() + 1) +
                ", CPUs=" + totalCpus +
                ", hash=0x" + Long.toHexString(hash).toUpperCase() +
                ']');
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop() {
        // Stop receiving notifications.
        getSpi().setListener(null);

        // Stop discovery worker.
        discoWrk.cancel();

        // Wait for the thread.
        U.join(discoWrkThread, log);

        discoWrkThread = null;

        super.onKernalStop();
    }

    /** {@inheritDoc} */
    @Override public void stop() throws GridException {
        // Stop SPI itself.
        stopSpi();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /**
     * Gets node shadow.
     *
     * @param node Node.
     * @return Node's shadow.
     */
    public GridNodeShadow shadow(GridNode node) {
        return new GridDiscoveryNodeShadowAdapter(node);
    }

    /**
     * @param nodeId Node ID.
     * @return {@code True} if node for given ID is alive.
     */
    public boolean alive(UUID nodeId) {
        assert nodeId != null;

        return node(nodeId) != null;
    }

    /**
     * @param node Node.
     * @return {@code True} if node is alive.
     */
    public boolean alive(GridNode node) {
        assert node != null;

        return alive(node.id());
    }

    /**
     * @param nodeId ID of the node.
     * @return {@code True} if ping succeeded.
     */
    public boolean pingNode(UUID nodeId) {
        assert nodeId != null;

        return getSpi().pingNode(nodeId);
    }

    /**
     * @param nodeId ID of the node.
     * @return Node for ID.
     */
    @Nullable public GridNode node(UUID nodeId) {
        assert nodeId != null;

        return getSpi().getNode(nodeId);
    }

    /**
     * @param nodeId Node ID.
     * @return Rich node for ID.
     */
    @Nullable public GridRichNode richNode(UUID nodeId) {
        return ctx.rich().rich(node(nodeId));
    }

    /**
     * @param nodes Nodes.
     * @return Alive nodes.
     */
    @SuppressWarnings( {"unchecked"})
    public Collection<GridNode> aliveNodes(Collection<? extends GridNode> nodes) {
        return F.view((Collection<GridNode>)nodes, aliveFilter);
    }

    /**
     * @param p Filters.
     * @return Collection of nodes for given filters.
     */
    public Collection<GridNode> nodes(GridPredicate<GridNode>... p) {
        return F.isEmpty(p) ? allNodes() : F.view(allNodes(), p);
    }

    /**
     * Gets collection of node for given node IDs and predicates.
     *
     * @param ids Ids to include.
     * @param p Filter for IDs.
     * @return Collection with all alive nodes for given IDs.
     */
    public Collection<GridNode> nodes(@Nullable Collection<UUID> ids, GridPredicate<UUID>... p) {
        return F.isEmpty(ids) ? Collections.<GridNode>emptyList() :
            F.view(
                F.viewReadOnly(ids, U.id2Node(ctx), p),
                F.notNull()
            );
    }

    /**
     * Gets collection of rich nodes for given node IDs.
     *
     * @param ids Ids to include.
     * @return Collection with all alive nodes for given IDs.
     */
    public Collection<GridRichNode> richNodes(@Nullable Collection<UUID> ids) {
        return F.isEmpty(ids) ? Collections.<GridRichNode>emptyList() :
            F.view(
                F.viewReadOnly(ids, U.id2RichNode(ctx)),
                F.notNull()
            );
    }

    /**
     * Gets topology hash for given set of nodes.
     *
     * @param nodes Subset of grid nodes for hashing.
     * @return Hash for given topology.
     * @see Grid#topologyHash(Iterable)
     */
    public long topologyHash(Iterable<? extends GridNode> nodes) {
        assert nodes != null;

        Iterator<? extends GridNode> iter = nodes.iterator();

        if (!iter.hasNext())
            return 0; // Special case.

        List<String> uids = new ArrayList<String>();

        for (GridNode node : nodes)
            uids.add(node.id().toString());

        Collections.sort(uids);

        CRC32 hash = new CRC32();

        for (String uuid : uids)
            hash.update(uuid.getBytes());

        return hash.getValue();
    }

    /**
     * @return All node count.
     */
    public int count() {
        return remoteNodes().size() + 1;
    }

    /**
     * @return All non-daemon remote nodes in topology.
     */
    public Collection<GridNode> remoteNodes() {
        return F.view(getSpi().getRemoteNodes(), daemonFilter);
    }

    /**
     * @return All non-daemon nodes in topology.
     */
    public Collection<GridNode> allNodes() {
        return F.view(F.concat(false, localNode(), getSpi().getRemoteNodes()), daemonFilter);
    }

    /**
     * @return All daemon nodes in topology.
     */
    public Collection<GridNode> daemonNodes() {
        return F.view(F.concat(false, localNode(), getSpi().getRemoteNodes()), F.not(daemonFilter));
    }

    /**
     * @return Local node.
     */
    public GridNode localNode() {
        return locNode == null ? getSpi().getLocalNode() : locNode;
    }

    /**
     *
     * @param node Grid node to get addresses for.
     * @return String containing distinct internal and external addresses.
     */
    private String getAddresses(GridNode node) {
        Collection<String> addrs = new HashSet<String>();

        addrs.addAll(node.internalAddresses());
        addrs.addAll(node.externalAddresses());

        return addrs.toString();
    }

    /**
     * Worker for discovery events.
     */
    private class DiscoveryWorker extends GridWorker {
        /** Event queue. */
        private final Queue<GridTuple2<Integer, GridNode>> evts = new ConcurrentLinkedQueue<GridTuple2<Integer, GridNode>>();

        /** */
        private DiscoveryWorker() {
            super(ctx.gridName(), "discovery-worker", log);
        }

        /**
         * @param rmtNode Remote node to verify configuration for.
         */
        private void verifyVersion(GridNode rmtNode) {
            assert rmtNode != null;

            GridNode locNode = getSpi().getLocalNode();

            String locVer = locNode.attribute(ATTR_BUILD_VER);
            String rmtVer = rmtNode.attribute(ATTR_BUILD_VER);

            assert locVer != null;
            assert rmtVer != null;

            if (!locVer.equals(rmtVer) && (!locVer.contains("x.x") && !rmtVer.contains("x.x")))
                U.warn(log, "Remote node has inconsistent build version [locVer=" + locVer + ", rmtVer=" +
                    rmtVer + ", rmtNodeId=" + rmtNode.id() + ']');
        }

        /**
         * Method is called when any discovery event occurs.
         *
         * @param type Discovery event type. See {@link GridDiscoveryEvent} for more details.
         * @param node Remote node this event is connected with.
         */
        private void recordEvent(int type, GridNode node) {
            assert node != null;

            if (ctx.event().isRecordable(type)) {
                GridDiscoveryEvent evt = new GridDiscoveryEvent();

                evt.nodeId(ctx.localNodeId());
                evt.eventNodeId(node.id());
                evt.type(type);
                evt.shadow(new GridDiscoveryNodeShadowAdapter(node));

                if (type == EVT_NODE_METRICS_UPDATED)
                    evt.message("Metrics were updated: " + node);
                else if (type == EVT_NODE_JOINED)
                    evt.message("Node joined: " + node);
                else if (type == EVT_NODE_LEFT)
                    evt.message("Node left: " + node);
                else if (type == EVT_NODE_FAILED)
                    evt.message("Node failed: " + node);
                else if (type == EVT_NODE_DISCONNECTED)
                    evt.message("Node disconnected: " + node);
                else if (type == EVT_NODE_RECONNECTED)
                    evt.message("Node reconnected: " + node);
                else
                    assert false;

                ctx.event().record(evt);
            }
        }

        /**
         * @param type Event type.
         * @param node Node.
         */
        void addEvent(int type, GridNode node) {
            assert node != null;

            synchronized (mux) {
                evts.add(F.t(type, node));

                mux.notifyAll();
            }
        }

        /**
         *
         * @param node Node to get a short description for.
         * @return Short description for the node to be used in 'quiet' mode.
         */
        private String quietNode(GridNode node) {
            assert node != null;

            return "nodeId8=" + node.id().toString().substring(0, 8) + ", " +
                "addr=" + getAddresses(node) + ", " +
                "CPUs=" + node.metrics().getTotalCpus();
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"DuplicateCondition"})
        @Override protected void body() throws InterruptedException {
            while (!isCancelled()) {
                synchronized (mux) {
                    while (evts.isEmpty())
                        mux.wait();
                }

                while (!evts.isEmpty()) {
                    GridTuple2<Integer, GridNode> evt = evts.poll();

                    int type = evt.get1();

                    GridNode node = evt.get2();

                    boolean isDaemon = isDaemon(node);

                    switch (type) {
                        case EVT_NODE_JOINED: {
                            if (!isDaemon)
                                if (!isLocDaemon) {
                                    if (log.isQuiet())
                                        U.quiet("Node JOINED [" + quietNode(node) + ']');
                                    else if (log.isInfoEnabled())
                                        log.info("Added new node to topology: " + node);

                                    verifyVersion(node);

                                    ackTopology();
                                }
                                else if (log.isDebugEnabled())
                                    log.debug("Added new node to topology: " + node);
                            else
                                if (log.isDebugEnabled())
                                    log.debug("Added new daemon node to topology: " + node);

                            break;
                        }

                        case EVT_NODE_LEFT: {
                            if (!isDaemon)
                                if (!isLocDaemon) {
                                    if (log.isQuiet())
                                        U.quiet("Node LEFT [" + quietNode(node) + ']');
                                    else if (log.isInfoEnabled())
                                        log.info("Node left topology: " + node);

                                    ackTopology();
                                }
                                else if (log.isDebugEnabled())
                                    log.debug("Node left topology: " + node);
                            else
                                if (log.isDebugEnabled())
                                    log.debug("Daemon node left topology: " + node);

                            break;
                        }

                        case EVT_NODE_FAILED: {
                            if (!isDaemon)
                                if (!isLocDaemon) {
                                    U.warn(log, "Node FAILED: " + node);

                                    ackTopology();
                                }
                                else if (log.isDebugEnabled())
                                    log.debug("Node FAILED: " + node);
                            else
                                if (log.isDebugEnabled())
                                    log.debug("Daemon node FAILED: " + node);

                            break;
                        }

                        case EVT_NODE_DISCONNECTED: {
                            if (!isLocDaemon) {
                                U.warn(log, "Node DISCONNECTED: " + node);

                                ackTopology();
                            }
                            else if (log.isDebugEnabled())
                                log.debug("Node DISCONNECTED: " + node);

                            break;
                        }

                        case EVT_NODE_RECONNECTED: {
                            if (!isLocDaemon) {
                                if (log.isQuiet())
                                    U.quiet("Node RECONNECTED [" + quietNode(node) + ']');
                                else if (log.isInfoEnabled())
                                    log.info("Node RECONNECTED: " + node);

                                ackTopology();
                            }
                            else if (log.isDebugEnabled())
                                log.debug("Node RECONNECTED: " + node);

                            break;
                        }

                        // Don't log metric update to avoid flooding the log.
                        case EVT_NODE_METRICS_UPDATED:
                            break;

                        default:
                            assert false : "Invalid discovery event: " + type;
                    }

                    recordEvent(type, node);
                }
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(DiscoveryWorker.class, this);
        }
    }
}
