// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 *
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearGetFuture<K, V> extends GridCompoundIdentityFuture<Map<K, V>>
    implements GridCacheFuture<Map<K, V>> {
    /** Context. */
    private GridCacheContext<K, V> cacheCtx;

    /** Keys. */
    private Collection<? extends K> keys;

    /** Reload flag. */
    private boolean reload;

    /** Future ID. */
    private GridUuid futId;

    /** Version. */
    private GridCacheVersion ver;

    /** Transaction. */
    private GridCacheTxLocalEx<K, V> tx;

    /** Filters. */
    private GridPredicate<? super GridCacheEntry<K, V>>[] filters;

    /** Logger. */
    private GridLogger log;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearGetFuture() {
        // No-op.
    }

    /**
     * @param cacheCtx Context.
     * @param keys Keys.
     * @param reload Reload flag.
     * @param tx Transaction.
     * @param filters Filters.
     */
    public GridNearGetFuture(GridCacheContext<K, V> cacheCtx, Collection<? extends K> keys, boolean reload,
        @Nullable GridCacheTxLocalEx<K, V> tx, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filters) {
        super(cacheCtx.kernalContext(), CU.<K, V>mapsReducer(keys.size()));

        assert cacheCtx != null;
        assert !F.isEmpty(keys);

        this.cacheCtx = cacheCtx;
        this.keys = keys;
        this.reload = reload;
        this.filters = filters;
        this.tx = tx;

        futId = GridUuid.randomUuid();

        ver = tx == null ? cacheCtx.versions().next() : tx.xidVersion();

        log = cacheCtx.logger(getClass());
    }

    /**
     * Initializes future.
     */
    void init() {
        map(keys, Collections.<GridRichNode, Collection<K>>emptyMap());

        markInitialized();
    }

    /**
     * @return Keys.
     */
    Collection<? extends K> keys() {
        return keys;
    }

    /** {@inheritDoc} */
    @Override public GridUuid futureId() {
        return futId;
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion version() {
        return ver;
    }

    /** {@inheritDoc} */
    @Override public Collection<? extends GridNode> nodes() {
        return
            F.viewReadOnly(futures(), new GridClosure<GridFuture<Map<K, V>>, GridRichNode>() {
                @Nullable @Override public GridRichNode apply(GridFuture<Map<K, V>> f) {
                    if (isMini(f))
                        return ((MiniFuture)f).node();

                    return cacheCtx.rich().rich(cacheCtx.discovery().localNode());
                }
            });
    }

    /** {@inheritDoc} */
    @Override public boolean onNodeLeft(UUID nodeId) {
        for (GridFuture<Map<K, V>> fut : futures())
            if (isMini(fut)) {
                MiniFuture f = (MiniFuture)fut;

                if (f.node().id().equals(nodeId)) {
                    f.onResult(new GridTopologyException("Remote node left grid (will retry): " + nodeId));

                    return true;
                }
            }

        return false;
    }

    /**
     * @param nodeId Sender.
     * @param res Result.
     */
    void onResult(UUID nodeId, GridNearGetResponse<K, V> res) {
        for (GridFuture<Map<K, V>> fut : futures())
            if (isMini(fut)) {
                MiniFuture f = (MiniFuture)fut;

                if (f.futureId().equals(res.miniId())) {
                    assert f.node().id().equals(nodeId);

                    f.onResult(res);
                }
            }
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(Map<K, V> res, Throwable err) {
        if (super.onDone(res, err)) {
            // Don't forget to clean up.
            cacheCtx.mvcc().removeFuture(this);

            return true;
        }

        return false;
    }

    /**
     * @param f Future.
     * @return {@code True} if mini-future.
     */
    private boolean isMini(GridFuture<Map<K, V>> f) {
        return f.getClass().equals(MiniFuture.class);
    }

    /**
     * @param keys Keys.
     * @param mapped Mappings to check for duplicates.
     */
    private void map(Iterable<? extends K> keys, Map<GridRichNode, Collection<K>> mapped) {
        Collection<GridRichNode> nodes = CU.allNodes(cacheCtx);

        ConcurrentMap<GridRichNode, Collection<K>> mappings =
            new ConcurrentHashMap<GridRichNode, Collection<K>>(nodes.size());

        // Assign keys to primary nodes.
        for (K key : keys)
            map(key, mappings, nodes, mapped);

        if (isDone())
            return;

        Collection<K> retries = new LinkedList<K>();

        // Create mini futures.
        for (Map.Entry<GridRichNode, Collection<K>> entry : mappings.entrySet()) {
            final GridRichNode n = entry.getKey();

            final Collection<K> mappedKeys = entry.getValue();

            assert !mappedKeys.isEmpty();

            // If this is the primary or backup node for the keys.
            if (n.isLocal()) {
                final GridDhtFuture<K, Collection<GridCacheEntryInfo<K, V>>> fut =
                    dht().getDhtAsync(n.id(), mappedKeys, reload, filters);

                retries.addAll(fut.retries());

                if (!retries.isEmpty())
                    // Remap.
                    map(retries, mappings);

                // Add new future.
                add(new GridEmbeddedFuture<Map<K, V>, Collection<GridCacheEntryInfo<K, V>>>(
                    cacheCtx.kernalContext(),
                    fut,
                    new C2<Collection<GridCacheEntryInfo<K, V>>, Exception, Map<K, V>>() {
                        @Override public Map<K, V> apply(Collection<GridCacheEntryInfo<K, V>> infos, Exception e) {
                            if (e != null) {
                                U.error(log, "Failed to get values from dht cache [fut=" + fut + "]", e);

                                onDone(e);

                                return Collections.emptyMap();
                            }

                            return loadEntries(n.id(), mappedKeys, infos);
                        }
                    })
                );
            }
            else {
                MiniFuture fut = new MiniFuture(n, mappedKeys);

                GridCacheMessage<K, V> req = new GridNearGetRequest<K, V>(futId, fut.futureId(), ver, mappedKeys,
                    reload, filters);

                add(fut); // Append new future.

                try {
                    cacheCtx.io().send(n, req);
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
    }

    /**
     * @param mappings Mappings.
     * @param key Key to map.
     * @param nodes Nodes.
     * @param mapped Previously mapped.
     */
    private void map(K key, ConcurrentMap<GridRichNode, Collection<K>> mappings, Collection<GridRichNode> nodes,
        Map<GridRichNode, Collection<K>> mapped) {
        GridCacheEntryEx<K, V> entry = cache().peekEx(key);

        while (true) {
            try {
                V v = null;

                boolean near = entry != null;

                // First we peek into near cache.
                if (entry != null)
                    v = entry.innerGet(tx, /*swap*/true, /*read-through*/false, /*fail-fast*/true, true, true, filters);

                if (v == null) {
                    entry = cache().dht().peekEx(key);

                    // If near cache does not have value, then we peek DHT cache.
                    if (entry != null)
                        v = entry.innerGet(tx, /*swap*/true, /*read-through*/false, /*fail-fast*/true, true, !near,
                            filters);
                }

                if (v != null)
                    add(new GridFinishedFuture<Map<K, V>>(cacheCtx.kernalContext(), Collections.singletonMap(key, v)));
                else {
                    GridRichNode node = CU.primary0(cacheCtx.affinity(key, nodes));

                    Collection<K> keys = mapped.get(node);

                    if (keys != null && keys.contains(key)) {
                        onDone(new GridException("Failed to remap key to a new node (key got remapped to the " +
                            "same node) [key=" + key + ", node=" + U.toShortString(node) + ']'));

                        return;
                    }

                    CU.getOrSet(mappings, node).add(key);
                }

                break;
            }
            catch (GridException e) {
                onDone(e);

                break;
            }
            catch (GridCacheEntryRemovedException ignored) {
                entry = cache().peekEx(key);
            }
            catch (GridCacheFilterFailedException e) {
                if (log.isDebugEnabled())
                    log.debug("Filter validation failed for entry: " + e);

                break;
            }
        }
    }

    /**
     * @return Near cache.
     */
    private GridNearCache<K, V> cache() {
        return (GridNearCache<K, V>)cacheCtx.cache();
    }

    /**
     * @return DHT cache.
     */
    private GridDhtCache<K, V> dht() {
        return cache().dht();
    }

    /**
     * @param nodeId Node id.
     * @param keys Keys.
     * @param infos Entry infos.
     * @return Result map.
     */
    private Map<K, V> loadEntries(UUID nodeId, Collection<K> keys, Collection<GridCacheEntryInfo<K, V>> infos) {
        boolean empty = F.isEmpty(keys);

        Map<K, V> map = empty ? Collections.<K, V>emptyMap() : new GridLeanMap<K, V>(keys.size());

        if (!empty) {
            GridCacheVersion ver = F.isEmpty(infos) ? null : cacheCtx.versions().next();

            for (GridCacheEntryInfo<K, V> info : infos) {
                // Entries available locally in DHT should not loaded into near cache for reading.
                if (!ctx.localNodeId().equals(nodeId)) {
                    while (true) {
                        try {
                            GridNearCacheEntry<K, V> entry = cache().entryExx(info.key());

                            // Load entry into cache.
                            entry.loadedValue(tx, nodeId, info.value(), info.valueBytes(), ver, info.ttl(),
                                info.expireTime(), true);

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry while processing get response (will retry");
                        }
                        catch (GridException e) {
                            // Fail.
                            onDone(e);

                            return Collections.emptyMap();
                        }
                    }
                }

                map.put(info.key(), info.value());
            }
        }

        return map;
    }

    /**
     * Mini-future for get operations. Mini-futures are only waiting on a single
     * node as opposed to multiple nodes.
     */
    private class MiniFuture extends GridFutureAdapter<Map<K, V>> {
        /** */
        private final GridUuid futId = GridUuid.randomUuid();

        /** Node ID. */
        private GridRichNode node;

        /** Keys. */
        @GridToStringInclude
        private Collection<K> keys;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public MiniFuture() {
            // No-op.
        }

        /**
         * @param node Node.
         * @param keys Keys.
         */
        MiniFuture(GridRichNode node, Collection<K> keys) {
            super(cacheCtx.kernalContext());

            this.node = node;
            this.keys = keys;
        }

        /**
         * @return Future ID.
         */
        GridUuid futureId() {
            return futId;
        }

        /**
         * @return Node ID.
         */
        public GridRichNode node() {
            return node;
        }

        /**
         * @return Keys.
         */
        public Collection<K> keys() {
            return keys;
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

        void onResult(GridTopologyException e) {
            if (log.isDebugEnabled())
                log.debug("Remote node left grid while sending or waiting for reply (will retry): " + this);

            // Remap.
            map(keys, F.t(node, keys));

            onDone(Collections.<K, V>emptyMap());
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridNearGetResponse<K, V> res) {
            Collection<K> retries = res.retries();

            if (!F.isEmpty(retries)) {
                if (log.isDebugEnabled())
                    log.debug("Remapping mini get future [leftOvers=" + retries + ", fut=" + this + ']');

                // This will append new futures to compound list.
                map(retries, F.t(node, keys));
            }

            onDone(loadEntries(node.id(), keys, res.entries()));
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this);
        }
    }
}
