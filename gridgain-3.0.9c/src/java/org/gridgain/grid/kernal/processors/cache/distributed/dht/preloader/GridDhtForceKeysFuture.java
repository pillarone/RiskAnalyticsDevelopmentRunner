// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.processors.cache.distributed.dht.GridDhtPartitionState.*;

/**
 * Force keys request future.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtForceKeysFuture<K, V> extends GridCompoundFuture<Object, Collection<K>> {
    /** Wait for 1 second for topology to change. */
    private static final long REMAP_PAUSE = 1000;

    /** Cache context. */
    private GridCacheContext<K, V> cctx;

    /** Topology. */
    private GridDhtPartitionTopology<K, V> top;

    /** Logger. */
    private GridLogger log;

    /** Keys to request. */
    private Collection<? extends K> keys;

    /** Keys for which local node is no longer primary. */
    private Collection<K> missed = new LinkedList<K>();

    /** Topology change counter. */
    private AtomicInteger topVer = new AtomicInteger(1);

    /** Future ID. */
    private GridUuid futId = GridUuid.randomUuid();

    /**
     * @param cctx Cache context.
     * @param keys Keys.
     */
    public GridDhtForceKeysFuture(GridCacheContext<K, V> cctx, Collection<? extends K> keys) {
        super(cctx.kernalContext());

        assert !F.isEmpty(keys);

        this.cctx = cctx;
        this.keys = keys;

        top = cctx.dht().topology();

        log = cctx.logger(getClass());
    }

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtForceKeysFuture() {
        // No-op.
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    /**
     * @param f Future.
     * @return {@code True} if mini-future.
     */
    private boolean isMini(GridFuture<?> f) {
        return f.getClass().equals(MiniFuture.class);
    }

    /**
     * @param evt Discovery event.
     */
    @SuppressWarnings( {"unchecked"})
    public void onDiscoveryEvent(GridDiscoveryEvent evt) {
        topVer.incrementAndGet();
        
        int type = evt.type();

        for (GridFuture<?> f : futures()) {
            if (isMini(f)) {
                MiniFuture mini = (MiniFuture)f;

                mini.onDiscoveryEvent();
                
                if (type == EVT_NODE_LEFT || type == EVT_NODE_FAILED) {
                    if (mini.node().id().equals(evt.eventNodeId())) {
                        mini.onResult(new GridTopologyException("Node left grid (will retry): " + evt.eventNodeId()));

                        break;
                    }

                }
            }
        }
    }

    /**
     * @param nodeId Node left callback.
     * @param res Response.
     */
    @SuppressWarnings( {"unchecked"})
    public void onResult(UUID nodeId, GridDhtForceKeysResponse<K, V> res) {
        for (GridFuture<Object> f : futures())
            if (isMini(f)) {
                MiniFuture mini = (MiniFuture)f;

                if (mini.miniId().equals(res.miniId())) {
                    mini.onResult(res);

                    return;
                }
            }

        if (log.isDebugEnabled())
            log.debug("Failed to find mini future for response [cacheName=" + cctx.name() + ", res=" + res + ']');
    }

    /**
     * Initializes this future.
     */
    public void init() {
        map(keys, Collections.<GridNode>emptyList());

        markInitialized();
    }

    /**
     * @param keys Keys.
     * @param exc Exclude nodes.
     * @return {@code True} if some mapping was added.
     */
    private boolean map(Iterable<? extends K> keys, Collection<GridNode> exc) {
        Map<GridNode, Set<K>> mappings = new HashMap<GridNode, Set<K>>();

        GridNode loc = cctx.localNode();

        int curTopVer = topVer.get();

        for (K key : keys)
            map(key, mappings, exc);

        if (isDone())
            return false;

        boolean ret = false;

        // Create mini futures.
        for (Map.Entry<GridNode, Set<K>> mapped : mappings.entrySet()) {
            GridNode n = mapped.getKey();
            Set<K> mappedKeys = mapped.getValue();

            int cnt = F.size(mappedKeys);

            if (cnt > 0) {
                ret = true;

                MiniFuture fut = new MiniFuture(n, mappedKeys, curTopVer, exc);

                GridDhtForceKeysRequest<K, V> req = new GridDhtForceKeysRequest<K, V>(futId, fut.miniId(), mappedKeys);

                try {
                    add(fut); // Append new future.

                    assert !n.id().equals(loc.id());

                    if (log.isDebugEnabled())
                        log.debug("Sending force key request [cacheName=" + cctx.name() + ", req=" + req + ']');

                    cctx.io().send(n, req);
                }
                catch (GridTopologyException e) {
                    fut.onResult(e);
                }
                catch (GridException e) {
                    // Fail the whole thing.
                    fut.onResult(e);
                }
            }
        }

        return ret;
    }

    /**
     * @param key Key.
     * @param exc Exclude nodes.
     * @param mappings Mappings.
     */
    private void map(K key, Map<GridNode, Set<K>> mappings, Collection<GridNode> exc) {
        GridNode loc = cctx.localNode();

        int part = cctx.partition(key);

        Collection<GridRichNode> nodes = cctx.affinity(part, CU.allNodes(cctx));

        GridRichNode primary = F.first(nodes);

        assert primary != null : "Primary node returned by affinity cannot be null: " + key;

        if (!loc.id().equals(primary.id())) {
            missed.add(key);

            if (log.isDebugEnabled())
                log.debug("Will not preload key (local node is not primary) [cacheName=" + cctx.name() +
                    ", key=" + key + ", part=" + part + ", locId=" + cctx.nodeId() + ']');
        }
        else {
            GridCacheEntryEx<K, V> e = cctx.dht().peekEx(key);

            try {
                if (e != null && !e.isNew()) {
                    if (log.isDebugEnabled())
                        log.debug("Will not preload key (entry is not new) [cacheName=" + cctx.name() +
                            ", key=" + key + ", part=" + part + ", locId=" + cctx.nodeId() + ']');
                    
                    // Key has been preloaded or retrieved already.
                    return;
                }
            }
            catch (GridCacheEntryRemovedException ignore) {
                if (log.isDebugEnabled())
                    log.debug("Received removed DHT entry for force keys request [entry=" + e +
                        ", locId=" + cctx.nodeId() + ']');
            }

            List<GridNode> owners = F.isEmpty(exc) ? top.owners(part) :
                new ArrayList<GridNode>(F.view(top.owners(part), F.notIn(exc)));

            if (owners.isEmpty() || (owners.contains(loc) && cctx.preloadEnabled())) {
                if (log.isDebugEnabled())
                    log.debug("Will not preload key (local node is owner) [key=" + key + ", part=" + part +
                        ", locId=" + cctx.nodeId() + ']');
                
                // Key is already preloaded.
                return;
            }

            // Create partition.
            GridDhtLocalPartition<K, V> locPart = top.localPartition(part, true);

            assert locPart != null;

            // If preloader is disabled, then local partition is always MOVING.
            if (locPart.state() == MOVING) {
                Collections.sort(owners, CU.nodeComparator(false));

                // Load from youngest owner.
                GridNode pick = F.first(owners);

                assert pick != null;

                if (!cctx.preloadEnabled() && loc.id().equals(pick.id()))
                    pick = F.first(F.view(owners, F.<GridNode>remoteNodes(loc.id())));

                if (pick == null) {
                    if (log.isDebugEnabled())
                        log.debug("Will not preload key (no nodes to request from with preloading disabled) [key=" +
                            key + ", part=" + part + ", locId=" + cctx.nodeId() + ']');

                    return;
                }

                Collection<K> mappedKeys = F.addIfAbsent(mappings, pick, F.<K>newSet());

                assert mappedKeys != null;

                mappedKeys.add(key);

                if (log.isDebugEnabled())
                    log.debug("Will preload key from node [cacheName=" + cctx.namex() + ", key=" + key + ", part=" +
                        part + ", node=" + pick.id() + ", locId=" + cctx.nodeId() + ']');
            }
            else {
                if (log.isDebugEnabled())
                    log.debug("Will not preload key (local partition is not MOVING) [cacheName=" + cctx.name() +
                        ", key=" + key + ", part=" + locPart + ", locId=" + cctx.nodeId() + ']');
            }
        }
    }

    /**
     * Mini-future for get operations. Mini-futures are only waiting on a single
     * node as opposed to multiple nodes.
     */
    private class MiniFuture extends GridFutureAdapter<Object> {
        /** Mini-future ID. */
        private GridUuid miniId = GridUuid.randomUuid();

        /** Node. */
        private GridNode node;

        /** Requested keys. */
        private Collection<K> keys;

        /** Topology version for this mini-future. */
        private int curTopVer;

        /** Pause latch for remapping missed keys. */
        private CountDownLatch pauseLatch = new CountDownLatch(1);

        private Collection<GridNode> exc;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public MiniFuture() {
            // No-op.
        }

        /**
         * @param node Node.
         * @param keys Keys.
         * @param curTopVer Topology version for this mini-future.
         * @param exc Exclude node list.
         */
        MiniFuture(GridNode node, Collection<K> keys, int curTopVer, Collection<GridNode> exc) {
            super(cctx.kernalContext());

            assert node != null;
            assert curTopVer > 0;
            assert exc != null;

            this.node = node;
            this.keys = keys;
            this.curTopVer = curTopVer;
            this.exc = exc;
        }

        /**
         * @return Mini-future ID.
         */
        GridUuid miniId() {
            return miniId;
        }

        /**
         * @return Node ID.
         */
        GridNode node() {
            return node;
        }

        /**
         * 
         */
        void onDiscoveryEvent() {
            pauseLatch.countDown();
        }

        /**
         * @param e Error.
         */
        void onResult(Throwable e) {
            if (log.isDebugEnabled())
                log.debug("Failed to get future result [fut=" + this + ", err=" + e + ']');

            // Fail.
            onDone(e);
        }

        /**
         * @param e Node failure.
         */
        void onResult(GridTopologyException e) {
            if (log.isDebugEnabled())
                log.debug("Remote node left grid while sending or waiting for reply (will retry): " + this);

            // Remap.
            map(keys, /*exclude*/F.asList(node));

            onDone(true);
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridDhtForceKeysResponse<K, V> res) {
            Collection<K> missedKeys = res.missedKeys();

            boolean remapMissed = false;

            if (!F.isEmpty(missed)) {
                if (curTopVer != topVer.get() || pauseLatch.getCount() == 0)
                    map(missedKeys, Collections.<GridNode>emptyList());
                else
                    remapMissed = true;
            }

            // If preloading is disabled, we need to check other backups.
            if (!cctx.preloadEnabled()) {
                Collection<K> retryKeys = F.view(
                    keys,
                    F.notIn(missedKeys),
                    F.notIn(F.viewReadOnly(res.forcedEntries(), CU.<K, V>info2Key())));

                if (!retryKeys.isEmpty())
                    map(retryKeys, F.concat(false, node, exc));
            }

            GridRichNode loc = cctx.localNode();

            for (GridCacheEntryInfo<K, V> info : res.forcedEntries()) {
                int p = cctx.partition(info.key());

                if (cctx.primary(loc, p)) {
                    GridDhtLocalPartition<K, V> locPart = top.localPartition(p, false);

                    if (locPart != null && locPart.state() == MOVING && locPart.reserve()) {
                        GridCacheEntryEx<K, V> entry = cctx.dht().entryEx(info.key());

                        try {
                            entry.initialValue(
                                info.value(),
                                info.valueBytes(),
                                info.version(),
                                info.ttl(),
                                info.expireTime(),
                                info.metrics()
                            );
                        }
                        catch (GridException e) {
                            onDone(e);

                            return;
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Trying to preload removed entry (will ignore) [cacheName=" +
                                    cctx.namex() + ", entry=" + entry + ']');
                        }
                        finally {
                            locPart.release();
                        }
                    }
                }
            }

            if (remapMissed) {
                if (pause())
                    map(missedKeys, Collections.<GridNode>emptyList());
            }

            // Finish mini future.
            onDone(true);
        }

        /**
         * Pause to avoid crazy resending in case of topology changes.
         *
         * @return {@code True} if was not interrupted.
         */
        private boolean pause() {
            try {
                pauseLatch.await(REMAP_PAUSE, TimeUnit.MILLISECONDS);

                return true;
            }
            catch (InterruptedException e) {
                // Reset interrupted flag.
                Thread.currentThread().interrupt();

                // Fail.
                onDone(e);

                return false;
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this, super.toString());
        }
    }
}
