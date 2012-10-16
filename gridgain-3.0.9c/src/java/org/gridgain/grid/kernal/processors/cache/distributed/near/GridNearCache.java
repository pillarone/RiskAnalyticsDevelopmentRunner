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
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.cache.GridCacheFlag.*;
import static org.gridgain.grid.cache.GridCachePeekMode.*;
import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;

/**
 * Near cache.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearCache<K, V> extends GridDistributedCacheAdapter<K, V> {
    /** DHT cache. */
    private GridDhtCache<K, V> dht;

    /** Near has key. */
    private GridPredicate<? super K> nearHasKey = new P1<K>() {
        @Override public boolean apply(K k) {
            return peekNearOnly(k) != null;
        }

        @Override public String toString() {
            return "Predicate to check for key presence in Near cache.";
        }
    };

    /** DHT has key. */
    private GridPredicate<? super K> dhtHasKey = new P1<K>() {
        @Override public boolean apply(K k) {
            return dht.peek(k) != null;
        }

        @Override public String toString() {
            return "Predicate to check for key presence in DHT cache.";
        }
    };

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearCache() {
        // No-op.
    }

    /**
     * @param ctx Context.
     */
    public GridNearCache(GridCacheContext<K, V> ctx) {
        super(ctx, ctx.config().getNearStartSize());
    }

    /** {@inheritDoc} */
    @Override protected void init() {
        map.setEntryFactory(new GridCacheMapEntryFactory<K, V>() {
            /** {@inheritDoc} */
            @Override public GridCacheMapEntry<K, V> create(GridCacheContext<K, V> ctx, K key, int hash, V val,
                GridCacheMapEntry<K, V> next, long ttl) {
                // Can't hold any locks here - this method is invoked when
                // holding write-lock on the whole cache map.
                return new GridNearCacheEntry<K, V>(ctx, key, hash, val, next, ttl);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        super.start();

        ctx.io().addHandler(GridNearGetResponse.class, new CI2<UUID, GridNearGetResponse<K, V>>() {
            @Override public void apply(UUID nodeId, GridNearGetResponse<K, V> res) {
                processGetResponse(nodeId, res);
            }
        });

        ctx.io().addHandler(GridNearTxPrepareResponse.class, new CI2<UUID, GridNearTxPrepareResponse<K, V>>() {
            @Override public void apply(UUID nodeId, GridNearTxPrepareResponse<K, V> res) {
                processPrepareResponse(nodeId, res);
            }
        });

        ctx.io().addHandler(GridNearTxFinishResponse.class, new CI2<UUID, GridNearTxFinishResponse<K, V>>() {
            @Override public void apply(UUID nodeId, GridNearTxFinishResponse<K, V> res) {
                processFinishResponse(nodeId, res);
            }
        });

        ctx.io().addHandler(GridNearLockResponse.class, new CI2<UUID, GridNearLockResponse<K, V>>() {
            @Override public void apply(UUID nodeId, GridNearLockResponse<K, V> res) {
                processLockResponse(nodeId, res);
            }
        });
    }

    /**
     * @return DHT cache.
     */
    public GridDhtCache<K, V> dht() {
        return dht;
    }

    /**
     * @param dht DHT cache.
     */
    public void dht(GridDhtCache<K, V> dht) {
        this.dht = dht;
    }

    /** {@inheritDoc} */
    @Override public GridCachePreloader<K, V> preloader() {
        return dht.preloader();
    }

    /** {@inheritDoc} */
    @Override public GridCacheEntryEx<K, V> entryEx(K key) {
        GridNearCacheEntry<K, V> entry = null;

        while (true) {
            try {
                entry = (GridNearCacheEntry<K, V>)super.entryEx(key);

                entry.initializeFromDht();

                return entry;
            }
            catch (GridCacheEntryRemovedException ignore) {
                if (log.isDebugEnabled())
                    log.debug("Got removed near entry while initializing from DHT entry (will retry): "  + entry);
            }
        }
    }

    /**
     * @param key Key.
     * @return Entry.
     */
    GridNearCacheEntry<K, V> entryExx(K key) {
        return (GridNearCacheEntry<K, V>)entryEx(key);
    }

    /**
     * @param key Key.
     * @return Entry.
     */
    @Nullable public GridNearCacheEntry<K, V> peekExx(K key) {
        return (GridNearCacheEntry<K, V>)peekEx(key);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> getAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        ctx.denyOnFlag(LOCAL);

        if (F.isEmpty(keys))
            return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), Collections.<K, V>emptyMap());

        GridCacheTxLocalAdapter<K, V> tx = ctx.tm().tx();

        if (tx != null && !tx.implicit())
            return ctx.wrapCloneMap(tx.getAllAsync(keys, filter));

        return loadAsync(keys, false, filter);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @Override public GridFuture<Object> readThroughAllAsync(Collection<? extends K> keys, boolean reload,
        GridCacheTx tx, GridPredicate<? super GridCacheEntry<K, V>>[] filter, GridInClosure2<K, V> vis) {
        return (GridFuture)loadAsync(keys, reload, filter);
    }

    /** {@inheritDoc} */
    @Override public void reloadAll() throws GridException {
        super.reloadAll();

        dht.reloadAll();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public GridFuture<?> reloadAllAsync() {
        GridCompoundFuture fut = new GridCompoundFuture(ctx.kernalContext());

        fut.add(super.reloadAllAsync());
        fut.add(dht.reloadAllAsync());

        fut.markInitialized();

        return fut;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public GridFuture<?> reloadAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridCompoundFuture fut = new GridCompoundFuture(ctx.kernalContext());

        fut.add(super.reloadAllAsync());
        fut.add(dht.reloadAllAsync(filter));

        fut.markInitialized();

        return fut;
    }

    /**
     * @param keys Keys to load.
     * @param reload Reload flag.
     * @param filter Filter.
     * @return Loaded values.
     */
    public GridFuture<Map<K, V>> loadAsync(@Nullable Collection<? extends K> keys, boolean reload,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (F.isEmpty(keys))
            return new GridFinishedFuture<Map<K, V>>(ctx.kernalContext(), Collections.<K, V>emptyMap());

        GridNearGetFuture<K, V> fut = new GridNearGetFuture<K, V>(ctx, keys, reload, null, filter);

        // Register future for responses.
        ctx.mvcc().addFuture(fut);

        fut.init();

        return ctx.wrapCloneMap(fut);
    }

    /**
     * @param tx Transaction.
     * @param keys Keys to load.
     * @param filter Filter.
     * @return Future.
     */
    GridFuture<Map<K, V>> txLoadAsync(GridNearTxLocal<K, V> tx, @Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        assert tx != null;

        GridNearGetFuture<K, V> fut = new GridNearGetFuture<K, V>(ctx, keys, false, tx, filter);

        // Register future for responses.
        ctx.mvcc().addFuture(fut);

        fut.init();

        return fut;
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    public void clearLocks(UUID nodeId, GridDhtUnlockRequest<K, V> req) {
        assert nodeId != null;

        GridCacheVersion obsoleteVer = ctx.versions().next();

        List<K> keys = req.nearKeys();

        if (keys != null) {
            for (K key : keys) {
                while (true) {
                    GridDistributedCacheEntry<K, V> entry = peekExx(key);

                    try {
                        if (entry != null) {
                            entry.doneRemote(
                                req.version(),
                                req.version(),
                                req.committedVersions(),
                                req.rolledbackVersions());

                            // Note that we don't reorder completed versions here,
                            // as there is no point to reorder relative to the version
                            // we are about to remove.
                            if (entry.removeLock(req.version())) {
                                if (log.isDebugEnabled())
                                    log.debug("Removed lock [lockId=" + req.version() + ", key=" + key + ']');

                                // Try to evict near entry dht-mapped locally.
                                evictNearEntry(entry, obsoleteVer);
                            }
                            else {
                                if (log.isDebugEnabled())
                                    log.debug("Received unlock request for unknown candidate " +
                                        "(added to cancelled locks set): " + req);
                            }
                        }
                        else if (log.isDebugEnabled())
                            log.debug("Received unlock request for entry that could not be found: " + req);

                        break;
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Received remove lock request for removed entry (will retry) [entry=" + entry +
                                ", req=" + req + ']');
                    }
                }
            }
        }
    }

    /**
     * @param ldr Loader.
     * @param nodeId Sender node ID.
     * @param req Request.
     * @return Remote transaction.
     * @throws GridException If failed.
     */
    @Nullable public GridNearTxRemote<K, V> startRemoteTx(ClassLoader ldr, UUID nodeId,
        GridDhtTxPrepareRequest<K, V> req) throws GridException {
        if (!F.isEmpty(req.nearWrites())) {
            GridNearTxRemote<K, V> tx = new GridNearTxRemote<K, V>(
                ldr,
                nodeId,
                req.threadId(),
                req.version(),
                req.commitVersion(),
                req.concurrency(),
                req.isolation(),
                req.isInvalidate(),
                req.timeout(),
                req.nearWrites(),
                ctx
            );

            if (!tx.empty()) {
                tx = ctx.tm().onCreated(tx);

                if (tx == null || !ctx.tm().onStarted(tx))
                    throw new GridCacheTxRollbackException("Attempt to start a completed transaction: " + tx);

                // Prepare prior to reordering, so the pending locks added
                // in prepare phase will get properly ordered as well.
                tx.prepare();

                // Add remote candidates and reorder completed and uncompleted versions.
                tx.addRemoteCandidates(req.candidatesByKey(), req.committedVersions(), req.rolledbackVersions());

                if (req.concurrency() == EVENTUALLY_CONSISTENT) {
                    if (log.isDebugEnabled())
                        log.debug("Committing transaction during remote prepare: " + tx);

                    tx.commit();

                    if (log.isDebugEnabled())
                        log.debug("Committed transaction during remote prepare: " + tx);
                }
            }

            return tx;
        }

        return null;
    }

    /**
     * @param nodeId Primary node ID.
     * @param req Request.
     * @return Remote transaction.
     * @throws GridException If failed.
     * @throws GridDistributedLockCancelledException If lock has been cancelled.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    @Nullable public GridNearTxRemote<K, V> startRemoteTx(UUID nodeId, GridDhtLockRequest<K, V> req)
        throws GridException, GridDistributedLockCancelledException {
        List<byte[]> nearKeyBytes = req.nearKeyBytes();

        GridNearTxRemote<K, V> tx = null;

        ClassLoader ldr = ctx.deploy().globalLoader();

        if (ldr != null) {
            for (int i = 0; i < nearKeyBytes.size(); i++) {
                byte[] bytes = nearKeyBytes.get(i);

                if (bytes == null)
                    continue;

                K key = req.nearKeys().get(i);

                Collection<GridCacheMvccCandidate<K>> cands = req.candidatesByIndex(i);

                if (log.isDebugEnabled())
                    log.debug("Unmarshalled key: " + key);

                GridNearCacheEntry<K, V> entry = null;

                while (true) {
                    try {
                        entry = peekExx(key);

                        if (entry != null) {
                            entry.keyBytes(bytes);

                            // Handle implicit locks for pessimistic transactions.
                            if (req.inTx()) {
                                tx = ctx.tm().tx(req.version());

                                if (tx != null)
                                    tx.addWrite(key, bytes, null/*Value.*/, null/*Value bytes.*/);
                                else {
                                    tx = new GridNearTxRemote<K, V>(
                                        nodeId,
                                        req.threadId(),
                                        req.version(),
                                        null,
                                        PESSIMISTIC,
                                        req.isolation(),
                                        req.isInvalidate(),
                                        req.timeout(),
                                        key,
                                        bytes,
                                        null, // Value.
                                        null, // Value bytes.
                                        ctx);

                                    if (tx.empty())
                                        return tx;

                                    tx = ctx.tm().onCreated(tx);

                                    if (tx == null || !ctx.tm().onStarted(tx))
                                        throw new GridCacheTxRollbackException("Failed to acquire lock " +
                                            "(transaction has been completed): " + req.version());
                                }
                            }

                            // Add remote candidate before reordering.
                            entry.addRemote(req.nodeId(), nodeId, req.threadId(), req.version(), req.timeout(),
                                tx != null && tx.ec(), tx != null);

                            // Remote candidates for ordered lock queuing.
                            entry.addRemoteCandidates(
                                cands,
                                req.version(),
                                req.committedVersions(),
                                req.rolledbackVersions());

                            entry.orderOwned(req.version(), req.owned(entry.key()));
                        }

                        // Double-check in case if sender node left the grid.
                        if (ctx.discovery().node(req.nodeId()) == null) {
                            if (log.isDebugEnabled())
                                log.debug("Node requesting lock left grid (lock request will be ignored): " + req);

                            if (tx != null)
                                tx.rollback();

                            return null;
                        }

                        // Entry is legit.
                        break;
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        assert entry.obsoleteVersion() != null : "Obsolete flag not set on removed entry: " +
                            entry;

                        if (log.isDebugEnabled())
                            log.debug("Received entry removed exception (will retry on renewed entry): " + entry);

                        if (tx != null) {
                            tx.clearEntry(entry.key());

                            if (log.isDebugEnabled())
                                log.debug("Cleared removed entry from remote transaction (will retry) [entry=" +
                                    entry + ", tx=" + tx + ']');
                        }
                    }
                }
            }
        }
        else {
            String err = "Failed to acquire deployment class loader for message: " + req;

            U.warn(log, err);

            throw new GridException(err);
        }

        return tx;
    }

    /**
     * @param nodeId Primary node ID.
     * @param req Request.
     * @return Remote transaction.
     * @throws GridException If failed.
     * @throws GridDistributedLockCancelledException If lock has been cancelled.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    @Nullable public GridNearTxRemote<K, V> startRemoteTx(UUID nodeId, GridDhtTxFinishRequest<K, V> req)
        throws GridException, GridDistributedLockCancelledException {
        GridNearTxRemote<K, V> tx = null;

        ClassLoader ldr = ctx.deploy().globalLoader();

        if (ldr != null) {
            for (GridCacheTxEntry<K, V> txEntry : req.nearWrites()) {
                GridDistributedCacheEntry<K, V> entry = null;

                while (true) {
                    try {
                        entry = peekExx(txEntry.key());

                        if (entry != null) {
                            entry.keyBytes(txEntry.keyBytes());

                            // Handle implicit locks for pessimistic transactions.
                            tx = ctx.tm().tx(req.version());

                            if (tx != null) {
                                tx.addWrite(txEntry.key(), txEntry.keyBytes());
                            }
                            else {
                                tx = new GridNearTxRemote<K, V>(
                                    nodeId,
                                    req.threadId(),
                                    req.version(),
                                    null,
                                    PESSIMISTIC,
                                    req.isolation(),
                                    req.isInvalidate(),
                                    0,
                                    txEntry.key(),
                                    txEntry.keyBytes(),
                                    txEntry.value(),
                                    txEntry.valueBytes(),
                                    ctx);

                                if (tx.empty())
                                    return tx;

                                tx = ctx.tm().onCreated(tx);

                                if (tx == null || !ctx.tm().onStarted(tx))
                                    throw new GridCacheTxRollbackException("Failed to acquire lock " +
                                        "(transaction has been completed): " + req.version());
                            }

                            // Add remote candidate before reordering.
                            if (txEntry.explicitVersion() == null)
                                entry.addRemote(req.nearNodeId(), nodeId, req.threadId(), req.version(), 0, tx.ec(),
                                    true);

                            // Remote candidates for ordered lock queuing.
                            entry.addRemoteCandidates(
                                Collections.<GridCacheMvccCandidate<K>>emptyList(),
                                req.version(),
                                req.committedVersions(),
                                req.rolledbackVersions());
                        }

                        // Double-check in case if sender node left the grid.
                        if (ctx.discovery().node(req.nearNodeId()) == null) {
                            if (log.isDebugEnabled())
                                log.debug("Node requesting lock left grid (lock request will be ignored): " + req);

                            if (tx != null)
                                tx.rollback();

                            return null;
                        }

                        // Entry is legit.
                        break;
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        assert entry.obsoleteVersion() != null : "Obsolete flag not set on removed entry: " +
                            entry;

                        if (log.isDebugEnabled())
                            log.debug("Received entry removed exception (will retry on renewed entry): " + entry);

                        if (tx != null) {
                            tx.clearEntry(entry.key());

                            if (log.isDebugEnabled())
                                log.debug("Cleared removed entry from remote transaction (will retry) [entry=" +
                                    entry + ", tx=" + tx + ']');
                        }
                    }
                }
            }
        }
        else {
            String err = "Failed to acquire deployment class loader for message: " + req;

            U.warn(log, err);

            throw new GridException(err);
        }

        return tx;
    }

    /**
     * @param nodeId Sender ID.
     * @param res Response.
     */
    private void processGetResponse(UUID nodeId, GridNearGetResponse<K, V> res) {
        GridNearGetFuture<K, V> fut = (GridNearGetFuture<K, V>)ctx.mvcc().<Map<K, V>>future(
            res.version().id(), res.futureId());

        if (fut == null) {
            if (log.isDebugEnabled())
                log.debug("Failed to find future for get response [sender=" + nodeId + ", res=" + res + ']');

            return;
        }

        fut.onResult(nodeId, res);
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void processPrepareResponse(UUID nodeId, GridNearTxPrepareResponse<K, V> res) {
        GridNearTxPrepareFuture<K, V> fut = (GridNearTxPrepareFuture<K, V>)ctx.mvcc().<GridCacheTx>future(
            res.version().id(), res.futureId());

        if (fut == null) {
            if (log.isDebugEnabled())
                log.debug("Failed to find future for prepare response [sender=" + nodeId + ", res=" + res + ']');

            return;
        }

        fut.onResult(nodeId, res);
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void processFinishResponse(UUID nodeId, GridNearTxFinishResponse<K, V> res) {
        GridNearTxFinishFuture<K, V> fut = (GridNearTxFinishFuture<K, V>)ctx.mvcc().<GridCacheTx>future(
            res.xid().id(), res.futureId());

        if (fut == null) {
            if (log.isDebugEnabled())
                log.debug("Failed to find future for finish response [sender=" + nodeId + ", res=" + res + ']');

            return;
        }

        fut.onResult(nodeId, res);
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void processLockResponse(UUID nodeId, GridNearLockResponse<K, V> res) {
        assert nodeId != null;
        assert res != null;

        GridNearLockFuture<K, V> fut = (GridNearLockFuture<K, V>)ctx.mvcc().<Boolean>future(res.version().id(),
            res.futureId());

        if (fut != null)
            fut.onResult(nodeId, res);
    }

    /** {@inheritDoc} */
    @Override public GridCacheTxLocalAdapter<K, V> newTx(boolean implicit, GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation, long timeout, boolean invalidate, boolean syncCommit, boolean syncRollback,
        boolean swapEnabled, boolean storeEnabled) {
        return new GridNearTxLocal<K, V>(ctx, implicit, concurrency, isolation, timeout,
            invalidate, syncCommit, syncRollback, swapEnabled, storeEnabled);
    }

    /** {@inheritDoc} */
    @Override protected GridFuture<Boolean> lockAllAsync(Collection<? extends K> keys, long timeout,
        GridCacheTxLocalEx<K, V> tx, boolean isInvalidate, boolean isRead, boolean retval,
        GridCacheTxIsolation isolation, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridNearLockFuture<K, V> fut = new GridNearLockFuture<K, V>(ctx, keys, (GridNearTxLocal<K, V>)tx, isRead, retval,
            timeout, filter);

        if (!ctx.mvcc().addFuture(fut))
            throw new IllegalStateException("Duplicate future ID: " + fut);

        fut.map();

        return fut;
    }

    /**
     * @param e Transaction entry.
     * @return {@code True} if entry is locally mapped as a primary or back up node.
     */
    protected boolean isNearLocallyMapped(GridCacheEntryEx<K, V> e) {
        return F.contains(ctx.affinity(e.key(), CU.allNodes(ctx)), ctx.localNode());
    }

    /**
     *
     * @param e Entry to evict if it qualifies for eviction.
     * @param obsoleteVer Obsolete version.
     * @return {@code True} if attempt was made to evict the entry.
     */
    protected boolean evictNearEntry(GridCacheEntryEx<K, V> e, GridCacheVersion obsoleteVer) {
        assert e != null;
        assert obsoleteVer != null;

        if (isNearLocallyMapped(e)) {
            if (log.isDebugEnabled())
                log.debug("Evicting dht-local entry from near cache [entry=" + e + ", tx=" + this + ']');

            if (e != null && e.markObsolete(obsoleteVer, true))
                return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public void unlockAll(Collection<? extends K> keys,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (keys.isEmpty())
            return;

        try {
            GridCacheVersion ver = null;

            Collection<GridRichNode> affNodes = CU.allNodes(ctx);

            int keyCnt = (int)Math.ceil((double)keys.size() / affNodes.size());

            Map<GridRichNode, GridNearUnlockRequest<K, V>> map =
                new HashMap<GridRichNode, GridNearUnlockRequest<K, V>>(affNodes.size());

            Collection<K> locKeys = new LinkedList<K>();

            GridCacheVersion obsoleteVer = ctx.versions().next();

            for (K key : keys) {
                GridDistributedCacheEntry<K, V> entry = peekExx(key);

                if (entry == null || !ctx.isAll(entry.wrap(false), filter))
                    continue;

                // Send request to remove from remote nodes.
                GridRichNode primary = CU.primary0(ctx.affinity(key, affNodes));

                GridNearUnlockRequest<K, V> req = map.get(primary);

                if (req == null)
                    map.put(primary, req = new GridNearUnlockRequest<K, V>(keyCnt));

                // Remove candidate from local node first.
                GridCacheMvccCandidate<K> rmv = entry.removeLock();

                if (rmv != null) {
                    assert req != null;

                    if (!rmv.reentry()) {
                        if (ver != null && !ver.equals(rmv.version()))
                            throw new GridException("Failed to unlock (if keys were locked separately, " +
                                "then they need to be unlocked separately): " + keys);

                        ver = rmv.version();

                        req.version(rmv.version());

                        if (!primary.isLocal())
                            req.addKey(entry.key(), entry.getOrMarshalKeyBytes(), ctx);
                        else
                            locKeys.add(key);

                        if (log.isDebugEnabled())
                            log.debug("Removed lock (will distribute): " + rmv);
                    }
                    else {
                        if (log.isDebugEnabled())
                            log.debug("Current thread still owns lock (or there are no other nodes) [lock=" + rmv +
                                ", curThreadId=" + Thread.currentThread().getId() + ']');
                    }
                }

                // Try to evict near entry if it's dht-mapped locally.
                evictNearEntry(entry, obsoleteVer);
            }

            if (ver == null)
                return;

            for (Map.Entry<GridRichNode, GridNearUnlockRequest<K, V>> mapping : map.entrySet()) {
                GridRichNode n = mapping.getKey();

                GridDistributedUnlockRequest<K, V> req = mapping.getValue();

                if (n.isLocal())
                    dht.removeLocks(ctx.nodeId(), req.version(), locKeys);
                else if (!req.keyBytes().isEmpty())
                    // We don't wait for reply to this message.
                    ctx.io().send(n, req);
            }
        }
        catch (GridException ex) {
            U.error(log, "Failed to unlock the lock for keys: " + keys, ex);
        }
    }

    /**
     * Removes locks regardless of whether they are owned or not for given
     * version and keys.
     *
     * @param ver Lock version.
     * @param keys Keys.
     */
    @SuppressWarnings({"unchecked"})
    public void removeLocks(GridCacheVersion ver, Collection<? extends K> keys) {
        if (keys.isEmpty())
            return;

        try {
            Collection<GridRichNode> affNodes = CU.allNodes(ctx);

            int keyCnt = (int)Math.ceil((double)keys.size() / affNodes.size());

            Map<GridNode, GridNearUnlockRequest<K, V>> map =
                new HashMap<GridNode, GridNearUnlockRequest<K, V>>(affNodes.size());

            for (K key : keys) {
                // Send request to remove from remote nodes.
                GridNearUnlockRequest<K, V> req = null;

                GridRichNode primary = CU.primary0(ctx.affinity(key, affNodes));

                if (!primary.isLocal()) {
                    req = map.get(primary);

                    if (req == null) {
                        map.put(primary, req = new GridNearUnlockRequest<K, V>(keyCnt));

                        req.version(ver);
                    }
                }

                while (true) {
                    GridDistributedCacheEntry<K, V> entry = peekExx(key);

                    try {
                        if (entry != null) {
                            GridCacheMvccCandidate<K> cand = entry.candidate(ver);

                            if (cand != null) {
                                // Remove candidate from local node first.
                                if (entry.removeLock(cand.version())) {
                                    if (primary.isLocal())
                                        dht.removeLocks(primary.id(), ver, F.asList(key));

                                    if (req == null)
                                        continue;

                                    req.addKey(entry.key(), entry.getOrMarshalKeyBytes(), ctx);
                                }
                            }
                        }

                        break;
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Attempted to remove lock from removed entry (will retry) [rmvVer=" +
                                ver + ", entry=" + entry + ']');
                    }
                }
            }

            if (map.isEmpty())
                return;

            Collection<GridCacheVersion> committed = ctx.tm().committedVersions(ver);
            Collection<GridCacheVersion> rolledback = ctx.tm().rolledbackVersions(ver);

            for (Map.Entry<GridNode, GridNearUnlockRequest<K, V>> mapping : map.entrySet()) {
                GridNode n = mapping.getKey();

                GridDistributedUnlockRequest<K, V> req = mapping.getValue();

                if (!req.keyBytes().isEmpty()) {
                    req.completedVersions(committed, rolledback);

                    // We don't wait for reply to this message.
                    ctx.io().send(n, req);
                }
            }
        }
        catch (GridException ex) {
            U.error(log, "Failed to unlock the lock for keys: " + keys, ex);
        }
    }

    /** {@inheritDoc} */
    @Override public int keySize() {
        return super.keySize() + dht.keySize();
    }

    /**
     * @return Key size for near cache only.
     */
    public int nearKeySize() {
        return super.keySize();
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, Collection<K>> mapKeysToNodes(Collection<? extends K> keys) {
        return CU.mapKeysToNodes(ctx, keys);
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new EntrySet(super.entrySet(filter), dht.entrySet(filter));
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"UnnecessarySuperQualifier"})
    @Override public Set<GridCacheEntry<K, V>> entrySet(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new EntrySet(super.entrySet(keys, nearHasKey, filter), dht.entrySet(keys, dhtHasKey, filter));
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new GridCacheKeySet<K, V>(ctx, entrySet(filter), filter);
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new GridCacheKeySet<K, V>(ctx, entrySet(keys, filter), filter);
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new GridCacheValueCollection<K, V>(ctx, entrySet(filter), ctx.vararg(F.<K, V>cacheHasPeekValue()));
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return new GridCacheValueCollection<K, V>(ctx, entrySet(keys, filter), filter);
    }

    /** {@inheritDoc} */
    @Override public boolean containsKey(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return super.containsKey(key, filter) || dht.containsKey(key, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean evict(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        // Use unary 'and' to make sure that both sides execute.
        return super.evict(key, filter) & dht.evict(key, filter);
    }

    /** {@inheritDoc} */
    @Override public void evictAll(Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        super.evictAll(keys, filter);

        dht.evictAll(keys, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean invalidate(K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return super.invalidate(key, filter) | dht().invalidate(key, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean compact(K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return super.compact(key, filter) | dht().compact(key, filter);
    }

    /** {@inheritDoc} */
    @Override public GridCacheEntry<K, V> entry(K key) {
        // We don't try wrap entry from near or dht cache.
        // Created object will be wrapped once some method is called.
        return new GridPartitionedCacheEntryImpl<K, V>(ctx.projectionPerCall(), ctx, key, null);
    }

    /** {@inheritDoc} */
    @Override public V peek(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        V val;

        try {
            val = peek0(true, key, SMART, filter);
        }
        catch (GridCacheFilterFailedException ignored) {
            if (log.isDebugEnabled())
                log.debug("Filter validation failed for key: " + key);

            return null;
        }

        return val == null ? dht.peek(key, filter) : val;
    }

    /**
     * Peeks only near cache without looking into DHT cache.
     *
     * @param key Key.
     * @return Peeked value.
     */
    @Nullable public V peekNearOnly(K key) {
        try {
            return peek0(true, key, SMART, CU.<K, V>empty());
        }
        catch (GridCacheFilterFailedException ignored) {
            if (log.isDebugEnabled())
                log.debug("Filter validation failed for key: " + key);

            return null;
        }
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (keys == null || keys.isEmpty())
            return Collections.emptyMap();

        final Collection<K> skipped = new GridLeanSet<K>();

        final Map<K, V> map = peekAll0(keys, filter, skipped);

        if (map.size() + skipped.size() != keys.size()) {
            map.putAll(dht.peekAll(F.view(keys, new P1<K>() {
                @Override public boolean apply(K k) {
                    return !map.containsKey(k) && !skipped.contains(k);
                }
            }), filter));
        }

        return map;
    }

    /** {@inheritDoc} */
    @Override public V peek(K key, @Nullable Collection<GridCachePeekMode> modes) throws GridException {
        V val;

        try {
            val = peek0(true, key, modes, ctx.tm().txx());
        }
        catch (GridCacheFilterFailedException ignored) {
            if (log.isDebugEnabled())
                log.debug("Filter validation failed for key: " + key);

            return null;
        }

        return val == null ? dht.peek(key, modes) : val;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(final K key, @Nullable final Collection<GridCachePeekMode> modes) {
        final GridCacheTxEx<K, V> tx = ctx.tm().tx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<V>() {
            @Nullable @Override public V call() throws GridException {
                ctx.tm().txContext(tx);

                return peek(key, modes);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys,
        @Nullable Collection<GridCachePeekMode> modes) throws GridException {
        if (keys == null || keys.isEmpty())
            return Collections.emptyMap();

        final Collection<K> skipped = new GridLeanSet<K>();

        final Map<K, V> map = peekAll0(keys, modes, ctx.tm().txx(), skipped);

        if (map.size() != keys.size()) {
            map.putAll(dht.peekAll(F.view(keys, new P1<K>() {
                @Override public boolean apply(K k) {
                    return !map.containsKey(k) && !skipped.contains(k);
                }
            }), modes));
        }

        return map;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> peekAllAsync(@Nullable final Collection<? extends K> keys,
        @Nullable final Collection<GridCachePeekMode> modes) {
        final GridCacheTxEx<K, V> tx = ctx.tm().tx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<Map<K, V>>() {
            @Nullable @Override public Map<K, V> call() throws GridException {
                ctx.tm().txContext(tx);

                return peekAll(keys, modes);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public boolean clear(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return super.clear(key, filter) | dht.clear(key, filter);
    }

    /** {@inheritDoc} */
    @Override public void clearAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        super.clearAll(filter);

        dht.clearAll(filter);
    }

    /** {@inheritDoc} */
    @Override public void clearAll(Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        super.clearAll(keys, filter);

        dht.clearAll(keys, filter);
    }

    /** {@inheritDoc} */
    @Override public V unswap(K key) throws GridException {
        ctx.denyOnFlags(F.asList(READ, SKIP_SWAP));

        // Unswap only from DHT. Near cache does not have swap storage.
        return dht.unswap(key);
    }

    /**
     * Wrapper for entry set.
     */
    private class EntrySet extends AbstractSet<GridCacheEntry<K, V>> {
        /** Near entry set. */
        private Set<GridCacheEntry<K, V>> nearSet;

        /** Dht entry set. */
        private Set<GridCacheEntry<K, V>> dhtSet;

        /**
         * @param nearSet Near entry set.
         * @param dhtSet Dht entry set.
         */
        private EntrySet(Set<GridCacheEntry<K, V>> nearSet, Set<GridCacheEntry<K, V>> dhtSet) {
            assert nearSet != null;
            assert dhtSet != null;

            this.nearSet = nearSet;
            this.dhtSet = dhtSet;
        }

        /** {@inheritDoc} */
        @Override public Iterator<GridCacheEntry<K, V>> iterator() {
            return new EntryIterator(nearSet.iterator(),
                F.iterator0(dhtSet, false, new P1<GridCacheEntry<K, V>>() {
                    @Override public boolean apply(GridCacheEntry<K, V> e) {
                        return !GridNearCache.super.containsKey(e.getKey(), null);
                    }
                }));
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return F.size(iterator());
        }
    }

    /**
     * Entry set iterator.
     */
    private class EntryIterator implements Iterator<GridCacheEntry<K, V>> {
        /** */
        private Iterator<GridCacheEntry<K, V>> dhtIter;

        /** */
        private Iterator<GridCacheEntry<K, V>> nearIter;

        /** */
        private Iterator<GridCacheEntry<K, V>> currIter;

        /** */
        private GridCacheEntry<K, V> currEntry;

        /**
         * @param nearIter Near set iterator.
         * @param dhtIter Dht set iterator.
         */
        private EntryIterator(Iterator<GridCacheEntry<K, V>> nearIter, Iterator<GridCacheEntry<K, V>> dhtIter) {
            assert nearIter != null;
            assert dhtIter != null;

            this.nearIter = nearIter;
            this.dhtIter = dhtIter;

            currIter = nearIter;
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return nearIter.hasNext() || dhtIter.hasNext();
        }

        /** {@inheritDoc} */
        @Override public GridCacheEntry<K, V> next() {
            if (!hasNext())
                throw new NoSuchElementException();

            if (!currIter.hasNext())
                currIter = dhtIter;

            return currEntry = currIter.next();
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            if (currEntry == null)
                throw new IllegalStateException();

            assert currIter != null;

            currIter.remove();

            try {
                GridNearCache.this.remove(currEntry.getKey(), CU.<K, V>empty());
            }
            catch (GridException e) {
                throw new GridRuntimeException(e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearCache.class, this);
    }
}
