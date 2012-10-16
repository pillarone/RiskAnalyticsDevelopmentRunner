// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.worker.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Distributed Garbage Collector for cache.
 *
 * TODO: need near/dht support.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheDgcManager<K, V> extends GridCacheManager<K, V> {
    /** GC thread. */
    private GridThread gcThread;

    /** Response thread. */
    private GridThread resThread;

    /** Response worker. */
    private ResponseWorker resWorker;

    /** {@inheritDoc} */
    @Override public void start0() throws GridException {
        if (cctx.config().getCacheMode() == GridCacheMode.LOCAL)
            // No-op for local cache.
            return;

        if (cctx.config().getGarbageCollectorFrequency() != 0) {
            gcThread = new GridThread(new GcWorker());

            gcThread.start();
        }

        resThread = new GridThread(resWorker = new ResponseWorker());

        resThread.start();

        cctx.io().addHandler(GridCacheDgcMessage.class, new CI2<UUID, GridCacheDgcMessage<K, V>>() {
            @Override public void apply(UUID nodeId, GridCacheDgcMessage<K, V> req) {
                processMessage(nodeId, req);
            }
        });
    }

    /**
     * Stops GC manager.
     */
    @Override public void stop0(boolean cancel, boolean wait) {
        if (cctx.config().getCacheMode() == GridCacheMode.LOCAL)
            // No-op for local cache.
            return;

        if (resThread != null) {
            GridUtils.interrupt(resThread);

            GridUtils.join(resThread, log);
        }

        if (gcThread != null) {
            GridUtils.interrupt(gcThread);

            GridUtils.join(gcThread, log);
        }
    }

    /**
     * @param nodeId Node id.
     * @param msg Message
     */
    private void processMessage(UUID nodeId, GridCacheDgcMessage<K, V> msg) {
        if (log.isDebugEnabled())
            log.debug("Received Dgc message [senderNode=" + nodeId + ", req=" + msg + ']');

        if (msg.request())
            resWorker.addGcRequest(nodeId, msg);
        else
            removeLocks(msg);
    }

    /**
     * @param msg Removes all locks that are provided in message.
     */
    private void removeLocks(GridCacheDgcMessage<K, V> msg) {
        assert msg != null;

        GridCacheVersion newVer = cctx.versions().next();

        for (Map.Entry<K, Collection<GridCacheVersion>> reqEntry : msg.candidatesMap().entrySet()) {
            K key = reqEntry.getKey();
            Collection<GridCacheVersion> col = reqEntry.getValue();

            while (true) {
                GridCacheEntryEx<K, V> cached = cctx.cache().peekEx(key);

                if (cached != null)
                    try {
                        // Invalidate before removing lock.
                        try {
                            cached.invalidate(null, newVer);
                        }
                        catch (GridException e) {
                            U.error(log, "Failed to invalidate entry: " + cached, e);
                        }

                        for (GridCacheVersion ver : col)
                            cached.removeLock(ver);

                        break; // While loop.
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Attempted to remove lock on obsolete entry (will retry).");
                    }
                else
                    break;
            }
        }
    }

    /**
     * Send message to node.
     *
     * @param nodeId Node id.
     * @param msg  Message to send.
     */
    private void sendMessage(UUID nodeId, GridCacheMessage<K, V> msg) {
        try {
            cctx.io().send(nodeId, msg);
        }
        catch (GridTopologyException ignored) {
            if (log.isDebugEnabled())
                log.debug("Failed to send message to node (node left grid): " + nodeId);
        }
        catch (GridException e) {
            U.error(log, "Failed to send message to node: " + nodeId, e);
        }
    }

    /**
     * Worker that scans current locks and initiates GC requests if needed.
     */
    private class GcWorker extends GridWorker {
        /**
         * Default constructor.
         */
        private GcWorker() {
            super(cctx.gridName(), "cache-gc", log);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"BusyWait"})
        @Override public void body() throws InterruptedException {
            while (!isCancelled()) {
                Thread.sleep(cctx.config().getGarbageCollectorFrequency());

                Map<UUID, GridCacheDgcMessage<K, V>> map = new HashMap<UUID, GridCacheDgcMessage<K, V>>();

                for (GridCacheMvccCandidate<K> lock : cctx.mvcc().remoteCandidates()) {
                    GridCacheDgcMessage<K, V> req = F.addIfAbsent(map, lock.nodeId(),
                        new GridCacheDgcMessage<K, V>(true /* request */));

                    assert req != null;

                    req.addCandidate(lock.key(), lock.version());
                }

                for (Map.Entry<UUID, GridCacheDgcMessage<K, V>> entry : map.entrySet()) {
                    UUID nodeId = entry.getKey();
                    GridCacheDgcMessage<K, V> req = entry.getValue();

                    if (cctx.discovery().node(nodeId) == null)
                        // Node has left the topology, safely remove all locks.
                        removeLocks(req);
                    else {
                        if (log.isDebugEnabled())
                            log.debug("Sending Dgc message [remoteNodeId=" + nodeId + ", msg=" + req + ']');

                        sendMessage(nodeId, req);
                    }
                }
            }
        }
    }

    /**
     * Worker that processes GC requests and sends required responses.
     */
    private class ResponseWorker extends GridWorker {
        /** */
        private BlockingQueue<GridTuple2<UUID, GridCacheDgcMessage<K, V>>> queue =
            new LinkedBlockingQueue<GridTuple2<UUID, GridCacheDgcMessage<K, V>>>();

        /**
         * Default constructor.
         */
        ResponseWorker() {
            super(cctx.gridName(), "cache-gc-response", log);
        }

        /**
         * @param nodeId Node id.
         * @param req request.
         */
        void addGcRequest(UUID nodeId, GridCacheDgcMessage<K, V> req) {
            assert nodeId != null;
            assert req != null;

            try {
                queue.put(F.t(nodeId, req));
            }
            catch (InterruptedException ignored) {
                U.warn(log, "Queue was interrupted.");
            }
        }

        /** {@inheritDoc} */
        @Override public void body() throws InterruptedException {
            while (!isCancelled()) {
                GridTuple2<UUID, GridCacheDgcMessage<K, V>> pair = queue.take();

                UUID senderId = pair.get1();
                GridCacheDgcMessage<K, V> req = pair.get2();

                GridCacheDgcMessage<K, V> res = new GridCacheDgcMessage<K, V>(false /* response */);

                for (Map.Entry<K, Collection<GridCacheVersion>> entry : req.candidatesMap().entrySet()) {
                    K key = entry.getKey();
                    Collection<GridCacheVersion> versions = entry.getValue();

                    while (true) {
                        GridCacheEntryEx<K, V> cached = cctx.cache().peekEx(key);

                        try {
                            if (cached != null) {
                                for (GridCacheVersion ver : versions)
                                    if (!cached.lockedBy(ver))
                                        res.addCandidate(key, ver);
                            }
                            else
                                // Entry is removed, add all versions to response.
                                for (GridCacheVersion ver : versions)
                                    res.addCandidate(key, ver);

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignored) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry during DGC (will retry): " + cached);
                        }
                    }
                }

                if (!res.candidatesMap().isEmpty()) {
                    if (log.isDebugEnabled())
                        log.debug("Sending DGC response [remoteNodeId=" + senderId + ", msg=" + res + ']');

                    sendMessage(senderId, res);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheDgcManager.class, this);
    }
}
