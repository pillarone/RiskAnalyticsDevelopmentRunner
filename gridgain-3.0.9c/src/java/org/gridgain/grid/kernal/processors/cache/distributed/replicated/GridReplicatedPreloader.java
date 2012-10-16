// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.replicated;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.stopwatch.*;
import org.gridgain.grid.util.tostring.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.cache.GridCachePreloadMode.*;

/**
 * Class that takes care about entries preloading in replicated cache.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridReplicatedPreloader<K, V> extends GridCachePreloaderAdapter<K, V> {
    /** */
    private static final String SENDER_THREAD_NAME = "replicated-preload-sender";

    /** */
    private static final String LOADER_THREAD_NAME = "replicated-preloader";

    /** Batch sender worker. */
    private BatchSender sender;

    /** Batch sender thread. */
    private GridThread senderThread;

    /** Batch loader worker. */
    private BatchLoader ldr;

    /** Batch loader thread. */
    private GridThread ldrThread;

    /** Busy lock to control activeness of threads (loader, sender). */
    private final ReadWriteLock busyLock = new ReentrantReadWriteLock();

    /** Future for waiting for start signal requests. */
    private final StartFuture startFut = new StartFuture(cctx.kernalContext());

    /** Ids of nodes to wait start signal responses from. */
    private final Collection<UUID> startRess = new GridConcurrentHashSet<UUID>();

    /** It's set to {@code true} once preloading has finished. */
    private final AtomicBoolean finished = new AtomicBoolean(false);

    /** Latch to wait for the end of preloading on. */
    private final CountDownLatch latch = new CountDownLatch(1);

    /** {node id, partition, mod} -> session */
    private final ConcurrentMap<GridTuple3<UUID, Integer, Integer>, Session> ses =
        new ConcurrentHashMap<GridTuple3<UUID, Integer, Integer>, Session>();

    /** {partition, mod} -> {node id, initial preload request} */
    private final Map<GridTuple2<Integer, Integer>, GridTuple2<UUID, GridReplicatedPreloadRequest<K, V>>> progress =
        new ConcurrentHashMap<GridTuple2<Integer, Integer>, GridTuple2<UUID, GridReplicatedPreloadRequest<K, V>>>();

    /**
     * Constructor.
     *
     * @param ctx Cache registry.
     */
    public GridReplicatedPreloader(GridCacheContext<K, V> ctx) {
        super(ctx);
    }

    /**
     * @throws GridException In case of error.
     */
    @Override public void start() throws GridException {
        if (log.isDebugEnabled())
            log.debug("Starting replicated preloader...");

        registerHandlers();
    }

    /**
     * @throws GridException In case of error.
     */
    @Override public void onKernalStart() throws GridException {
        senderThread = new GridThread(sender = new BatchSender());

        senderThread.start();

        ldrThread = new GridThread(ldr = new BatchLoader());

        ldrThread.start();

        GridRichNode loc = cctx.localNode();

        assert loc != null;

        startFut.waitFrom(F.nodeIds(F.view(CU.allNodes(cctx, loc.order()), F.<GridNode>remoteNodes(loc.id()))));

        try {
            startFut.get();
        }
        catch (GridException e) {
            Collection<UUID> waitFrom = new HashSet<UUID>(startFut.waitFrom);

            waitFrom.removeAll(startFut.gotFrom);

            throw new GridException("Failed to wait for start message from remote nodes: [waitFrom=" + waitFrom +
                ", local=" + cctx.nodeId() + "]", e);
        }

        cctx.events().addPreloadEvent(-1, EVT_CACHE_PRELOAD_STARTED, cctx.discovery().shadow(cctx.localNode()),
            EVT_NODE_JOINED, cctx.localNode().metrics().getNodeStartTime());

        if (log.isInfoEnabled())
            log.info("Preloading started.");

        sendInitialPreloadRequests();

        if (cctx.config().getPreloadMode() == SYNC) {
            try {
                latch.await();
            }
            catch (InterruptedException e) {
                U.error(log, "Preload latch was interrupted.", e);
            }
        }
    }

    /**
     *
     */
    private void registerHandlers() {
        cctx.events().addListener(new GridLocalEventListener() {
                @Override public void onEvent(GridEvent evt) {
                    onDiscoveryEvent((GridDiscoveryEvent)evt);
                }
            }, EVTS_DISCOVERY);

        cctx.io().addHandler(GridReplicatedStartSignalMessage.class, new CI2<UUID, GridReplicatedStartSignalMessage>() {
            @Override public void apply(UUID nodeId, GridReplicatedStartSignalMessage msg) {
                if (log.isDebugEnabled())
                    log.debug("Processing start signal message from node [nodeId=" + nodeId + ",msg=" + msg + "]");

                if (msg.request()) {
                    startFut.onReceive(nodeId);

                    if (cctx.node(nodeId) != null)
                        sendStartSignalResponse(nodeId);
                }
                else {
                    // Remove node id from waiting collection.
                    startRess.remove(nodeId);
                }
            }
        });

        cctx.io().addHandler(GridReplicatedPreloadRequest.class, new CI2<UUID, GridReplicatedPreloadRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridReplicatedPreloadRequest<K, V> req) {
                processInitialPreloadRequest(nodeId, req);
            }
        });

        cctx.io().addHandler(GridReplicatedPreloadResponse.class, new CI2<UUID, GridReplicatedPreloadResponse<K, V>>() {
            @Override public void apply(UUID nodeId, GridReplicatedPreloadResponse<K, V> res) {
                processInitialPreloadResponse(nodeId, res);
            }
        });

        cctx.io().addHandler(GridReplicatedPreloadBatchRequest.class,
            new CI2<UUID, GridReplicatedPreloadBatchRequest<K, V>>() {
                @Override public void apply(UUID nodeId, GridReplicatedPreloadBatchRequest<K, V> req) {
                    processPreloadBatchRequest(nodeId, req);
                }
            });

        cctx.io().addHandler(GridReplicatedPreloadBatchResponse.class,
            new CI2<UUID, GridReplicatedPreloadBatchResponse<K, V>>() {
                @Override public void apply(UUID nodeId, GridReplicatedPreloadBatchResponse<K, V> res) {
                    processPreloadBatchResponse(nodeId, res);
                }
            });
    }

    /**
     * @return {@code true} if entered to busy state.
     */
    private boolean enterBusy() {
        return busyLock.readLock().tryLock();
    }

    /**
     *
     */
    private void leaveBusy() {
        busyLock.readLock().unlock();
    }

    /**
     * @param evt Event.
     */
    private void onDiscoveryEvent(GridDiscoveryEvent evt) {
        assert evt != null;

        switch (evt.type()) {
            case EVT_NODE_JOINED: {
                onNodeJoined(evt.eventNodeId());

                break;
            }

            case EVT_NODE_LEFT:
            case EVT_NODE_FAILED: {
                onNodeLeft(evt.eventNodeId());

                break;
            }

            default: {
                assert false; // Should never happen.
            }
        }
    }

    /**
     * @param nodeId Joined node id.
     */
    @SuppressWarnings({"unchecked"})
    private void onNodeJoined(final UUID nodeId) {
        if (log.isDebugEnabled())
            log.debug("Node joined [nodeId=" + nodeId + ", local=" + cctx.nodeId() + "]");

        GridRichNode node = cctx.node(nodeId);

        if (node == null || !CU.cacheNode(cctx, node))
            return;

        final Set<Integer> parts = partitions(node);

        cctx.partitionReleaseFuture(parts).listenAsync(
            new CI1<GridFuture<?>>() {
                @Override public void apply(GridFuture<?> fut) {
                    try {
                        fut.get();
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to wait until partitions are released: " + parts, e);

                        return; // Should never happen.
                    }

                    sendStartSignalRequest(nodeId);
                }
            });
    }

    /**
     * @param node Node.
     * @return Collection of partition numbers for the node.
     */
    public Set<Integer> partitions(GridRichNode node) {
        assert node != null;

        GridStopwatch watch = W.stopwatch("CONTEXT_PARTITIONS", false);

        try {
            GridCacheAffinity<Object> aff = cctx.config().getAffinity();

            Collection<GridRichNode> nodes = CU.allNodes(cctx);

            Set<Integer> parts = new HashSet<Integer>();

            int partCnt = aff.partitions();

            for (int i = 0; i < partCnt; i++) {
                Collection<GridRichNode> affNodes = aff.nodes(i, nodes);

                if (affNodes.contains(node))
                    parts.add(i);
            }

            return parts;
        }
        finally {
            watch.stop();
        }
    }

    /**
     * @param nodeId Left node id.
     */
    private void onNodeLeft(UUID nodeId) {
        if (log.isDebugEnabled())
            log.debug("Node left [nodeId=" + nodeId + ", local=" + cctx.nodeId() + "]");

        startFut.onReceive(nodeId);

        startRess.remove(nodeId);

        if (!finished.get())
            resendPreloadRequest(nodeId);
    }

    /**
     * @param nodeId Node id.
     */
    private void sendStartSignalRequest(UUID nodeId) {
        if (log.isDebugEnabled())
            log.debug("Sending start signal request to node [nodeId=" + nodeId + ", local=" + cctx.nodeId() + "]");

        try {
            startRess.add(nodeId);

            cctx.io().send(nodeId, new GridReplicatedStartSignalMessage<K, V>(/* request */true));
        }
        catch (GridTopologyException ignored) {
            startRess.remove(nodeId);

            if (log.isDebugEnabled())
                log.debug("Failed to send start signal request to node since the node left grid: " + nodeId);

            return;
        }
        catch (GridException e) {
            U.error(log, "Failed to send start signal request to node (will be resent): " + nodeId, e);
        }

        if (cctx.node(nodeId) != null)
            resendStartSignalRequest(nodeId);
    }

    /**
     * Re-sends start signal request after timeout has elapsed.
     *
     * @param nodeId Node id.
     */
    private void resendStartSignalRequest(final UUID nodeId) {
        cctx.time().addTimeoutObject(new GridTimeoutObject() {
            private UUID id = UUID.randomUUID();

            private long endTime = System.currentTimeMillis() + cctx.gridConfig().getNetworkTimeout();

            @Override public UUID timeoutId() {
                return id;
            }

            @Override public long endTime() {
                return endTime;
            }

            @Override public void onTimeout() {
                if (cctx.node(nodeId) != null && startRess.contains(nodeId)) {
                    sendStartSignalRequest(nodeId);
                }
            }
        });
    }

    /**
     * @param nodeId Node id.
     */
    private void sendStartSignalResponse(UUID nodeId) {
        if (log.isDebugEnabled())
            log.debug("Sending start signal response to node [nodeId=" + nodeId + ", local=" + cctx.nodeId() + "]");

        try {
            cctx.io().send(nodeId, new GridReplicatedStartSignalMessage<K, V>(/* response */false));
        }
        catch (GridException e) {
            // Don't resend response here since remote will resend request anyway.
            U.error(log, "Failed to send start signal response to node: " + nodeId, e);
        }
    }

    /**
     * Send initial preload requests to remote nodes.
     *
     * @throws GridException If failed to send initial preload requests.
     */
    private void sendInitialPreloadRequests() throws GridException {
        Collection<GridRichNode> nodes = new HashSet<GridRichNode>(CU.remoteNodes(cctx));

        if (nodes.isEmpty()) {
            if (log.isDebugEnabled())
                log.debug("There are no nodes to send preload request (preloading will finish).");

            finish();

            return;
        }

        Set<Integer> parts = partitions(cctx.localNode());

        for (int part : parts) {
            HashSet<GridRichNode> partNodes = new HashSet<GridRichNode>(aff.nodes(part, nodes));

            if (partNodes.isEmpty())
                continue;

            int cnt = partNodes.size();

            int mod = 0;

            GridReplicatedPreloadRequest<K, V> req = new GridReplicatedPreloadRequest<K, V>(part, mod, cnt);

            while (mod < cnt) {
                for (Iterator<GridRichNode> it = partNodes.iterator(); it.hasNext(); ) {
                    GridNode node = it.next();

                    progress.put(F.t(part, req.mod()), F.t(node.id(), req));

                    // Send preload request.
                    try {
                        cctx.io().send(node.id(), req);

                        if (log.isDebugEnabled())
                            log.debug("Sent initial preload request to node [nodeId=" +
                                node.id() + ", req=" + req + ']');

                        mod++;

                        if (mod == cnt)
                            break;

                        req = new GridReplicatedPreloadRequest<K, V>(part, mod, cnt);
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to send initial preload request to node [node=" +
                            node.id() + ", req=" + req + "]", e);

                        it.remove();
                    }
                }

                if (partNodes.isEmpty()) {
                    if (log.isDebugEnabled())
                        log.debug("There are no nodes to send initial preload request (preloading will finish).");

                    throw new GridException("Failed to send initial preload request to any node.");
                }
            }
        }

        if (progress.isEmpty())
            finish();
    }

    /**
     *
     * @param nodeId Sender node ID.
     * @param req Preload request.
     */
    private void processInitialPreloadRequest(UUID nodeId, GridReplicatedPreloadRequest<K, V> req) {
        if (log.isDebugEnabled())
            log.debug("Processing initial preload request [node=" + nodeId + ", req=" + req);

        GridNode node = cctx.node(nodeId);

        if (node == null) {
            if (log.isDebugEnabled())
                log.debug("Node has left the grid, initial preload request will be ignored [node=" +
                    nodeId + ", req=" + req);

            return;
        }

        boolean failed = !finished.get();

        if (!failed) {
            Session ses = new Session(nodeId, req.partition(), req.mod(), req.nodeCount());

            try {
                ses.start();
            }
            catch (GridException e) {
                U.error(log, "Failed to start node sending session: " + ses, e);

                failed = true;
            }
        }
        else {
            if (log.isDebugEnabled())
                log.debug("Received preload request while node itself is in preload state (will fail request).");
        }

        GridCacheMessage<K, V> res = new GridReplicatedPreloadResponse<K, V>(req.mod(), failed);

        try {
            // Reply back to sender.
            cctx.io().send(node, res);

            if (log.isDebugEnabled())
                log.debug("Sent initial preload response to node [nodeId=" + nodeId + ", res=" + res + ']');
        }
        catch (GridException e) {
            U.error(log, "Failed to send initial preload response to node (did the node leave grid?) [node=" +
                nodeId + ", msg=" + res + ']', e);
        }
    }

    /**
     * @param nodeId Sender node id.
     * @param req Preload batch request.
     */
    private void processPreloadBatchRequest(UUID nodeId, GridReplicatedPreloadBatchRequest<K, V> req) {
        if (log.isDebugEnabled())
            log.debug("Processing preload batch request [node=" + nodeId + ", req=" + req + "]");

        ldr.addBatch(nodeId, req);
    }

    /**
     * @param nodeId Sender node id.
     * @param res Preload response.
     */
    private void processPreloadBatchResponse(UUID nodeId, GridReplicatedPreloadBatchResponse<K, V> res) {
        if (log.isDebugEnabled())
            log.debug("Processing preload batch response [node=" + nodeId + ", res=" + res + "]");

        Session ses = nodeSession(nodeId, res.partition(), res.mod());

        if (ses != null)
            ses.onConfirmed(res);
    }

    /**
     * @param nodeId Node id.
     * @param part Partition.
     * @param mod Mod.
     * @return Node session.
     */
    @Nullable private Session nodeSession(UUID nodeId, int part, int mod) {
        return ses.get(F.t(nodeId, part, mod));
    }

    /**
     *
     */
    private void finish() {
        finished.set(true);

        latch.countDown();

        cctx.events().addPreloadEvent(-1, EVT_CACHE_PRELOAD_STOPPED, cctx.discovery().shadow(cctx.localNode()),
            EVT_NODE_JOINED, cctx.localNode().metrics().getNodeStartTime());

        if (log.isInfoEnabled())
            log.info("Preloading finished.");
    }

    /**
     * Stops response thread.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    @Override public void stop() {
        if (log.isDebugEnabled())
            log.debug("Stopping replicated preloader...");

        // Acquire write lock so that any new thread could not be started.
        busyLock.writeLock().lock();

        startRess.clear();

        // Stop sender.
        if (sender != null)
            sender.cancel();

        U.interrupt(senderThread);

        U.join(senderThread, log);

        // Stop loader.
        if (ldr != null)
            ldr.cancel();

        U.interrupt(ldrThread);

        U.join(ldrThread, log);

        if (log.isDebugEnabled())
            log.debug("Replicated preloader has stopped.");
    }

    /**
     * @param failedNodeId Failed node id.
     */
    private void resendPreloadRequest(UUID failedNodeId) {
        if (log.isDebugEnabled())
            log.debug("Resending initial preloader request for failed node: " + failedNodeId);

        for (Entry<GridTuple2<Integer, Integer>, GridTuple2<UUID, GridReplicatedPreloadRequest<K, V>>> e :
            progress.entrySet()) {
            GridTuple2<UUID, GridReplicatedPreloadRequest<K, V>> t = e.getValue();

            if (t.get1().equals(failedNodeId)) {
                GridReplicatedPreloadRequest req = t.get2();

                resendPreloadRequest(failedNodeId, e.getKey().get1(), req.mod(), req.nodeCount());
            }
        }
    }

    /**
     * Resend preload request.
     *
     * @param failedNodeId Node that must be excluded for resending.
     * @param part Partition.
     * @param mod Mod.
     * @param nodeCnt Number of nodes.
     */
    public void resendPreloadRequest(UUID failedNodeId, int part, int mod, int nodeCnt) {
        if (log.isDebugEnabled())
            log.debug("Resending initial preloader request [failedNodeId=" + failedNodeId + ", part=" + part +
                ", mod=" + mod + ", nodeCnt=" + nodeCnt + "]");

        GridRichNode failedNode = cctx.node(failedNodeId);

        List<GridRichNode> nodes = new ArrayList<GridRichNode>(GridCacheUtils.remoteNodes(cctx));

        Collections.sort(nodes, new Comparator<GridRichNode>() {
            @Override public int compare(GridRichNode n1, GridRichNode n2) {
                return n1.order() > n2.order() ? 1 : -1;
            }
        });

        if (failedNode != null)
            nodes.remove(failedNode);

        GridReplicatedPreloadRequest<K, V> req = new GridReplicatedPreloadRequest<K, V>(part, mod, nodeCnt);

        if (!nodes.isEmpty()) {
            boolean sent = false;

            for (GridRichNode node : nodes) {
                if (!cctx.belongs(part, node))
                    continue;

                try {
                    cctx.io().send(node.id(), req);

                    progress.put(F.t(part, mod), F.t(node.id(), req));

                    sent = true;

                    if (log.isDebugEnabled())
                        log.debug("Sent initial preload request to node [nodeId=" + node.id() + ", req=" + req + ']');

                    break;
                }
                catch (GridTopologyException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Failed to resend initial preload request since node left grid [nodeId=" +
                            node.id() + ", req=" + req + "]");
                }
                catch (GridException e) {
                    U.error(log, "Failed to send initial preload request to node [nodeId=" +
                        node.id() + ", req=" + req + ']', e);
                }
            }

            if (!sent) {
                if (log.isDebugEnabled())
                    log.debug("Cannot resend initial preload request to any node (will be skipped): " + req);

                progress.remove(F.t(part, mod));
            }
        }
        else {
            if (log.isDebugEnabled())
                log.debug("There are no nodes to resend preload request to (preloading will be finished): " + req);

            finish();
        }
    }

    /**
     * @param nodeId Sender node id.
     * @param msg Preload response.
     */
    @SuppressWarnings({"unchecked"})
    public void processInitialPreloadResponse(UUID nodeId, GridReplicatedPreloadResponse<K, V> msg) {
        if (log.isDebugEnabled())
            log.debug("Processing initial preload response: " + msg);

        if (msg.failed())
            resendPreloadRequest(nodeId);
    }

    /**
     * Preload batch sender.
     */
    private class BatchSender extends GridWorker {
        /** Batches to send. */
        private BlockingQueue<GridTuple2<UUID, GridReplicatedPreloadBatchRequest<K, V>>> queue =
            new LinkedBlockingQueue<GridTuple2<UUID, GridReplicatedPreloadBatchRequest<K, V>>>();

        /**
         *
         */
        BatchSender() {
            super(cctx.gridName(), SENDER_THREAD_NAME, log);
        }

        /**
         * @param nodeId Node id to send to.
         * @param batch Batch request to send.
         */
        void addBatch(UUID nodeId, GridReplicatedPreloadBatchRequest<K, V> batch) {
            try {
                queue.put(F.t(nodeId, batch));
            }
            catch (InterruptedException ignored) {
                U.warn(log, "Preload queue was interrupted on node: " + cctx.nodeId());
            }
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public void body() throws InterruptedException {
            while (!isCancelled()) {
                GridTuple2<UUID, GridReplicatedPreloadBatchRequest<K, V>> pair = queue.take();

                UUID nodeId = pair.get1();
                GridReplicatedPreloadBatchRequest<K, V> batch = pair.get2();

                if (log.isDebugEnabled())
                    log.debug("Took batch from sending queue [node=" + nodeId + ", batch=" + batch + ']');

                if (!enterBusy())
                    break;

                try {
                    GridRichNode node = cctx.node(nodeId);

                    if (node == null) {
                        if (log.isDebugEnabled())
                            log.debug("Node has left the grid, batch will be sent: [node=" + nodeId + "]");

                        continue;
                    }

                    Session ses = nodeSession(nodeId, batch.partition(), batch.mod());

                    try {
                        cctx.io().send(nodeId, batch);

                        if (ses != null)
                            ses.onSent(batch);
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to send preload batch to node (did node leave grid?)" +
                            " [node=" + nodeId + ", batch=" + batch + "]", e);

                        if (ses != null)
                            ses.onSendFailure(batch);
                    }
                }
                finally {
                    leaveBusy();
                }
            }
        }
    }

    /**
     * Batch loader.
     */
    private class BatchLoader extends GridWorker {
        /** Preload queue. */
        private BlockingQueue<GridTuple2<UUID, GridReplicatedPreloadBatchRequest<K, V>>> queue =
            new LinkedBlockingQueue<GridTuple2<UUID, GridReplicatedPreloadBatchRequest<K, V>>>();

        /** {@inheritDoc} */
        protected BatchLoader() {
            super(cctx.gridName(), LOADER_THREAD_NAME, log);
        }

        /**
         * @param nodeId Sender node id.
         * @param req Preload batch.
         */
        private void addBatch(UUID nodeId, GridReplicatedPreloadBatchRequest<K, V> req) {
            try {
                queue.put(F.t(nodeId, req));
            }
            catch (InterruptedException e) {
                U.error(log, "Preload queue was interrupted.", e);
            }
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            if (log.isDebugEnabled())
                log.debug("Starting batch loader.");

            while (!isCancelled()) {
                GridTuple2<UUID, GridReplicatedPreloadBatchRequest<K, V>> t = queue.take();

                UUID nodeId = t.get1();
                GridReplicatedPreloadBatchRequest<K, V> batchReq = t.get2();

                if (log.isDebugEnabled())
                    log.debug("Took batch from preload queue [batch=" + batchReq +
                        ", queue size=" + queue.size() + "]");

                if (!enterBusy())
                    break;

                try {
                    processBatch(nodeId, batchReq);
                }
                finally {
                    leaveBusy();
                }
            }
        }

        /**
         * @param nodeId Sender node id.
         * @param batchReq Batch request.
         */
        private void processBatch(UUID nodeId, GridReplicatedPreloadBatchRequest<K, V> batchReq) {
            // We need to set p2p context manually because we receive and process
            // batches in different threads.
            p2pContext(nodeId, batchReq);

            if (batchReq.entries() != null) {
                for (GridCacheEntryInfo<K, V> preloaded : batchReq.entries()) {
                    try {
                        GridCacheEntryEx<K, V> cached = null;

                        try {
                            cached = cctx.cache().entryEx(preloaded.key());

                            if (!cached.initialValue(
                                preloaded.value(),
                                preloaded.valueBytes(),
                                preloaded.version(),
                                preloaded.ttl(),
                                preloaded.expireTime(),
                                preloaded.metrics())) {
                                if (log.isDebugEnabled())
                                    log.debug("Preloading entry is already in cache (will ignore): " + cached);
                            }
                        }
                        catch (GridCacheEntryRemovedException ignored) {
                            if (log.isDebugEnabled())
                                log.debug("Entry has been concurrently removed while preloading: " + cached);
                        }
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to put preloaded entry.", e);
                    }
                }
            }

            if (batchReq.last()) {
                progress.remove(F.t(batchReq.partition(), batchReq.mod()));

                if (progress.isEmpty())
                    finish();
            }

            try {
                cctx.io().send(
                    nodeId,
                    new GridReplicatedPreloadBatchResponse<K, V>(batchReq.partition(), batchReq.mod(), batchReq.index())
                );
            }
            catch (GridTopologyException ignored) {
                if (log.isDebugEnabled())
                    log.debug("Failed to send batch response since the node left grid [node=" +
                        nodeId + ", batch=" + batchReq + "]");
            }
            catch (GridException e) {
                U.error(log, "Failed to send batch response [node=" + nodeId + ", batch=" + batchReq + "]", e);
            }
        }
    }

    /**
     *
     */
    private static class StartFuture extends GridFutureAdapter<Object> {
        /** */
        private final Collection<UUID> waitFrom = new GridConcurrentHashSet<UUID>();

        /** */
        private final Collection<UUID> gotFrom = new GridConcurrentHashSet<UUID>();

        /**
         * @param ctx Context.
         */
        StartFuture(GridKernalContext ctx) {
            super(ctx);
        }

        /**
         * Empty constructor required for {@link java.io.Externalizable}.
         */
        public StartFuture() {
            // No-op.
        }

        /**
         * @param waitFrom Nodes to wait from.
         */
        void waitFrom(Collection<UUID> waitFrom) {
            if (F.isEmpty(waitFrom)) {
                onDone();

                return;
            }

            this.waitFrom.addAll(waitFrom);

            checkDone();
        }

        /**
         * @param nodeId Node id.
         */
        void onReceive(UUID nodeId) {
            assert nodeId != null;

            gotFrom.add(nodeId);

            checkDone();
        }

        /**
         *
         */
        private void checkDone() {
            if (!F.isEmpty(waitFrom) && gotFrom.containsAll(waitFrom))
                onDone();
        }
    }

    /**
     * Preload session.
     */
    private class Session {
        /** */
        private final UUID nodeId;

        /** */
        private int part;

        /** */
        private int mod;

        /** */
        private int nodeCnt;

        /** */
        private final AtomicInteger idxGen = new AtomicInteger();

        /** */
        @GridToStringExclude
        private Iterator<GridCacheEntryEx<K, V>> entryIter;

        /** */
        @GridToStringExclude
        private AtomicReference<GridReplicatedPreloadBatchRequest<K, V>> lastBatch =
            new AtomicReference<GridReplicatedPreloadBatchRequest<K, V>>(null);

        /** */
        private Queue<K> hangingKeys = new ConcurrentLinkedQueue<K>();

        /** */
        private AtomicBoolean started = new AtomicBoolean();

        /** */
        private AtomicBoolean finished = new AtomicBoolean();

        /**
         * @param nodeId Node id.
         * @param part Partition.
         * @param mod Mod.
         * @param nodeCnt Number of nodes.
         */
        Session(UUID nodeId, int part, int mod, int nodeCnt) {
            assert nodeId != null;
            assert part >= 0;
            assert nodeCnt > 0;
            assert mod >= 0 && mod <= nodeCnt;

            this.part = part;
            this.nodeId = nodeId;
            this.mod = mod;
            this.nodeCnt = nodeCnt;
        }

        /**
         * @return {@code true} if session is finished.
         */
        boolean finished() {
            return finished.get();
        }

        /**
         *
         * @throws GridException If failed to start.
         */
        void start() throws GridException {
            if (log.isDebugEnabled())
                log.debug("Starting preloader session: " + this);

            if (started.get() || finished.get())
                throw new IllegalStateException("Session is either started or finished: " + this);

            entryIter = cctx.cache().entries().iterator();

            GridReplicatedPreloadBatchRequest<K, V> batch = nextBatch();

            if (batch != null) {
                started.set(true);

                Session prev = ses.putIfAbsent(F.t(nodeId, part, mod), this);

                assert prev == null;

                send(batch);

                if (log.isDebugEnabled())
                    log.debug("Preloading started for node: " + nodeId);
            }
            else
                finish();
        }

        /**
         * @param batch Batch.
         */
        private void send(GridReplicatedPreloadBatchRequest<K, V> batch) {
            assert batch != null;

            if (lastBatch.compareAndSet(null, batch))
                sender.addBatch(nodeId, batch);
            else {
                U.error(log, "Try to send next batch while the previous was not confirmed [ses=" + this + "]");

                assert false;
            }
        }

        /**
         * @return Next batch.
         * @throws GridException If failed to get next sending batch.
         */
        @Nullable private GridReplicatedPreloadBatchRequest<K, V> nextBatch() throws GridException {
            if (finished.get())
                throw new IllegalStateException("Sending session is finished: " + this);

            GridRichNode node = cctx.node(nodeId);

            if (node == null)
                return null;

            GridReplicatedPreloadBatchRequest<K, V> batch =
                new GridReplicatedPreloadBatchRequest<K, V>(part, mod, idxGen.incrementAndGet());

            int batchSize = 0;

            K key;

            while ((key = hangingKeys.poll()) != null) {
                if (log.isDebugEnabled())
                    log.debug("Took hanging key from queue: " + key);

                GridCacheEntryEx<K, V> entry = cctx.cache().peekEx(key);

                if (entry != null) {
                    int bytesAdded = addToBatch(entry, batch, batchSize);

                    if (bytesAdded < 0) {
                        hangingKeys.add(entry.key());

                        return batch;
                    }

                    batchSize += bytesAdded;
                }
            }

            while (entryIter.hasNext()) {
                GridCacheEntryEx<K, V> entry = entryIter.next();

                // Mod entry hash to the number of nodes.
                if (entry.hashCode() % nodeCnt != mod || cctx.partition(entry.key()) != part)
                    continue;

                if (log.isDebugEnabled())
                    log.debug("Took next key with iterator: " + entry.key());

                int bytesAdded = addToBatch(entry, batch, batchSize);

                if (bytesAdded < 0) {
                    hangingKeys.add(entry.key());

                    return batch;
                }

                batchSize += bytesAdded;
            }

            // Cache entries are fully iterated at this point.
            batch.last(true);

            return batch;
        }

        /**
         * @param entry Cache entry.
         * @param batch Batch.
         * @param batchSize Batch size.
         * @return Number of bytes added to batch. If {@code -1} is returned it means
         *      that entry was not added to batch due to batch size limitation.
         * @throws GridException If failed to add.
         */
        private int addToBatch(GridCacheEntryEx<K, V> entry, GridReplicatedPreloadBatchRequest<K, V> batch,
            int batchSize) throws GridException {
            assert entry != null;
            assert batch != null;

            GridCacheEntryInfo<K, V> preloadInfo = createEntryInfo(entry);

            if (preloadInfo != null) {
                byte[] entryBytes = batch.marshal(preloadInfo, cctx);

                if (batchSize + entryBytes.length <= cctx.config().getPreloadBatchSize() || batch.isEmpty()) {
                    batch.addSerializedEntry(entryBytes);

                    return entryBytes.length;
                }
                else
                    return -1;
            }

            return 0;
        }

        /**
         * @param batch Batch to resend.
         */
        private void resendIfNotConfirmed(final GridReplicatedPreloadBatchRequest<K, V> batch) {
            cctx.time().addTimeoutObject(new GridTimeoutObject() {
                private UUID id = UUID.randomUUID();

                private long endTime = System.currentTimeMillis() + cctx.gridConfig().getNetworkTimeout();

                @Override public UUID timeoutId() {
                    return id;
                }

                @Override public long endTime() {
                    return endTime;
                }

                @Override public void onTimeout() {
                    if (cctx.node(nodeId) == null) {
                        finish();

                        return;
                    }

                    // We have to create a copy because communication manager may cache
                    // serialized message with old message id.
                    GridReplicatedPreloadBatchRequest<K, V> newBatch =
                        new GridReplicatedPreloadBatchRequest<K, V>(batch);

                    if (lastBatch.compareAndSet(batch, newBatch)) {
                        if (log.isDebugEnabled())
                            log.debug("Resending last batch as it was not confirmed: " + batch);

                        sender.addBatch(nodeId, newBatch);
                    }
                }
            });
        }

        /**
         * @param batch Preload request.
         */
        void onSent(GridReplicatedPreloadBatchRequest<K, V> batch) {
            assert batch != null;

            if (log.isDebugEnabled())
                log.debug("Preload batch was sent: " + batch);

            resendIfNotConfirmed(batch);
        }

        /**
         * @param batch Batch that was not sent.
         */
        void onSendFailure(GridReplicatedPreloadBatchRequest<K, V> batch) {
            if (cctx.node(nodeId) == null)
                finish();
            else
                resendIfNotConfirmed(batch);
        }

        /**
         * @param res Batch confirmation message.
         */
        void onConfirmed(GridReplicatedPreloadBatchResponse<K, V> res) {
            assert res != null;

            if (finished.get())
                return;

            GridReplicatedPreloadBatchRequest<K, V> batch = lastBatch.get();

            if (batch == null || batch.index() != res.batchIndex())
                // Received confirmation for already confirmed batch (duplication).
                return;

            if (!lastBatch.compareAndSet(batch, null))
                return;

            if (batch.last()) {
                finish();

                return;
            }

            try {
                GridReplicatedPreloadBatchRequest<K, V> nextBatch = nextBatch();

                if (nextBatch != null)
                    send(nextBatch);
                else
                    finish();
            }
            catch (GridException e) {
                U.error(log, "Failed to prepare next preload batch for session: " + this, e);
            }
        }

        /**
         *
         */
        private void finish() {
            if (finished.compareAndSet(false, true)) {
                if (log.isDebugEnabled())
                    log.debug("Closing sending session: " + this);

                ses.remove(F.t(nodeId, part, mod));

                if (log.isDebugEnabled())
                    log.debug("Preloading finished for node: " + nodeId);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(Session.class, this, "lastBatch", lastBatch.get());
        }
    }
}
