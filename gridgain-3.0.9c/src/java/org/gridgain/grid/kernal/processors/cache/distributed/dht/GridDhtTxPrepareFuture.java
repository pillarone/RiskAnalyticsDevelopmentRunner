// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
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
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheTxState.*;

/**
 *
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtTxPrepareFuture<K, V> extends GridCompoundIdentityFuture<GridCacheTx>
    implements GridCacheMvccFuture<K, V, GridCacheTx> {
    /** Context. */
    private GridCacheContext<K, V> cacheCtx;

    /** Future ID. */
    private GridUuid futId;

    /** Transaction. */
    @GridToStringExclude
    private GridDhtTxLocal<K, V> tx;

    /** Near mappings. */
    private Map<UUID, GridDistributedTxMapping<K, V>> nearMap;

    /** DHT mappings. */
    private Map<UUID, GridDistributedTxMapping<K, V>> dhtMap;

    /** Logger. */
    private GridLogger log;

    /** Error. */
    private AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);

    /** Replied flag. */
    private AtomicBoolean replied = new AtomicBoolean(false);

    /** All replies flag. */
    private AtomicBoolean allReplies = new AtomicBoolean(false);

    /** Latch to wait for reply to be sent. */
    @GridToStringExclude
    private CountDownLatch replyLatch = new CountDownLatch(1);

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtTxPrepareFuture() {
        // No-op.
    }

    /**
     * @param cacheCtx Context.
     * @param tx Transaction.
     */
    public GridDhtTxPrepareFuture(GridCacheContext<K, V> cacheCtx, final GridDhtTxLocal<K, V> tx) {
        super(cacheCtx.kernalContext(), new GridReducer<GridCacheTx, GridCacheTx>() {
            @Override public boolean collect(GridCacheTx e) {
                return true;
            }

            @Override public GridCacheTx apply() {
                // Nothing to aggregate.
                return tx;
            }
        });

        assert cacheCtx != null;

        this.cacheCtx = cacheCtx;
        this.tx = tx;

        futId = GridUuid.randomUuid();

        log = cacheCtx.logger(getClass());

        if (tx.ec()) {
            replied.set(true);

            replyLatch.countDown();
        }

        dhtMap = tx.dhtMap();
        nearMap = tx.nearMap();

        assert dhtMap != null;
        assert nearMap != null;
    }

    /** {@inheritDoc} */
    @Override public GridUuid futureId() {
        return futId;
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion version() {
        return tx.xidVersion();
    }

    /**
     * @return Involved nodes.
     */
    @Override public Collection<? extends GridNode> nodes() {
        return
            F.viewReadOnly(futures(), new GridClosure<GridFuture<?>, GridRichNode>() {
                @Nullable @Override public GridRichNode apply(GridFuture<?> f) {
                    if (isMini(f)) {
                        return ((MiniFuture)f).node();
                    }

                    return cacheCtx.rich().rich(cacheCtx.discovery().localNode());
                }
            });
    }

    /** {@inheritDoc} */
    @Override public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner) {
        if (log.isDebugEnabled())
            log.debug("Transaction future received owner changed callback: " + entry);

        K key = entry.key();

        boolean ret = tx.hasWriteKey(key);

        if (ret)
            return onDone(tx);

        return ret;
    }

    /**
     * @return Transaction.
     */
    GridDhtTxLocal<K, V> tx() {
        return tx;
    }

    /**
     * @return {@code True} if all locks are owned.
     */
    private boolean checkLocks() {
        for (GridCacheTxEntry<K, V> txEntry : tx.writeEntries()) {
            while (true) {
                GridCacheEntryEx<K, V> cached = txEntry.cached();

                try {
                    // Don't compare entry against itself.
                    if (!cached.lockedLocally(tx.xid())) {
                        if (log.isDebugEnabled())
                            log.debug("Transaction entry is not locked by transaction (will wait) [entry=" + cached +
                                ", tx=" + tx + ']');

                        return false;
                    }

                    break; // While.
                }
                // Possible if entry cached within transaction is obsolete.
                catch (GridCacheEntryRemovedException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Got removed entry in future onAllReplies method (will retry): " + txEntry);

                    txEntry.cached(cacheCtx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                }
            }
        }

        return true;
    }

    /**
     * Initializes future.
     */
    public void onPreparedEC() {
        if (tx.ec())
            // No reason to wait for replies.
            tx.state(PREPARED); // TODO: CODE: EC
    }

    /** {@inheritDoc} */
    @Override public boolean onNodeLeft(UUID nodeId) {
        for (GridFuture<?> fut : futures())
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
     * @param t Error.
     */
    void onError(Throwable t) {
        if (err.compareAndSet(null, t)) {
            tx.setRollbackOnly();

            // TODO: as an improvement, at some point we must rollback right away.
            // TODO: However, in this case need to make sure that reply is sent back
            // TODO: even for non-existing transactions whenever finish request comes in.
//            try {
//                tx.rollback();
//            }
//            catch (GridException ex) {
//                U.error(log, "Failed to automatically rollback transaction: " + tx, ex);
//            }
//
            // If not local node.
            if (!tx.nearNodeId().equals(cacheCtx.nodeId())) {
                // Send reply back to near node.
                GridCacheMessage<K, V> res = new GridNearTxPrepareResponse<K, V>(tx.nearXidVersion(), tx.nearFutureId(),
                    tx.nearMiniId(), tx.xidVersion(), t);

                try {
                    cacheCtx.io().send(tx.nearNodeId(), res);
                }
                catch (GridException e) {
                    U.error(log, "Failed to send reply to originating near node (will rollback): " + tx.nearNodeId(), e);

                    try {
                        tx.rollback();
                    }
                    catch (GridException ex) {
                        U.error(log, "Failed to rollback due to failure to communicate back up nodes: " + tx, ex);
                    }
                }
            }

            onComplete();
        }
    }

    /**
     * @param nodeId Sender.
     * @param res Result.
     */
    void onResult(UUID nodeId, GridDhtTxPrepareResponse<K, V> res) {
        if (!isDone()) {
            for (GridFuture<GridCacheTx> fut : pending())
                if (isMini(fut)) {
                    MiniFuture f = (MiniFuture)fut;

                    if (f.futureId().equals(res.miniId())) {
                        assert f.node().id().equals(nodeId);

                        f.onResult(res);

                        break;
                    }
                }

            if (!hasPending())
                onAllReplies();
        }
    }

    /**
     * Callback for whenever all replies are received.
     */
    public void onAllReplies() {
        // Ready all locks.
        if (allReplies.compareAndSet(false, true) && !tx.ec() && !isDone()) {
            if (log.isDebugEnabled())
                log.debug("Received replies from all participating nodes: " + this);

            for (GridCacheTxEntry<K, V> txEntry : tx.writeEntries()) {
                while (true) {
                    GridDistributedCacheEntry<K, V> entry = (GridDistributedCacheEntry<K, V>)txEntry.cached();

                    try {
                        GridCacheMvccCandidate<K> c = entry.readyLock(tx.xidVersion());

                        if (log.isDebugEnabled())
                            log.debug("Current lock owner for entry [owner=" + c + ", entry=" + entry + ']');

                        break; // While.
                    }
                    // Possible if entry cached within transaction is obsolete.
                    catch (GridCacheEntryRemovedException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry in future onAllReplies method (will retry): " + txEntry);

                        txEntry.cached(cacheCtx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(GridCacheTx tx0, Throwable err) {
        assert err != null || !hasPending();

        if (err == null)
            onAllReplies();

        // If locks were not acquired yet, delay completion.
        if (isDone() || (err == null && !tx.ec() && !checkLocks()))
            return false;

        this.err.compareAndSet(null, err);

        if (replied.compareAndSet(false, true)) {
            try {
                if (!tx.nearNodeId().equals(cacheCtx.nodeId())) {
                    // Send reply back to originating near node.
                    GridDistributedBaseMessage<K, V> res = new GridNearTxPrepareResponse<K, V>(tx.nearXidVersion(),
                        tx.nearFutureId(), tx.nearMiniId(), tx.xidVersion(), this.err.get());

                    GridCacheVersion min = tx.minVersion();

                    res.completedVersions(cacheCtx.tm().committedVersions(min), cacheCtx.tm().rolledbackVersions(min));

                    cacheCtx.io().send(tx.nearNodeId(), res);
                }
            }
            catch (GridException e) {
                onError(e);
            }
            finally {
                replyLatch.countDown();
            }
        }
        else {
            try {
                replyLatch.await();
            }
            catch (InterruptedException e) {
                onError(new GridException("Got interrupted while waiting for replies to be sent.", e));

                replyLatch.countDown();
            }
        }

        return onComplete();
    }

    /**
     * @param f Future.
     * @return {@code True} if mini-future.
     */
    private boolean isMini(GridFuture<?> f) {
        return f.getClass().equals(MiniFuture.class);
    }

    /**
     * Completeness callback.
     *
     * @return {@code True} if {@code done} flag was changed as a result of this call.
     */
    private boolean onComplete() {
        tx.state(PREPARED);

        if (super.onDone(tx, err.get())) {
            // Don't forget to clean up.
            cacheCtx.mvcc().removeFuture(this);

            return true;
        }

        return false;
    }

    /**
     * Completes this future.
     */
    void complete() {
        onComplete();
    }

    /**
     * Initializes future.
     */
    void prepare() {
        if (!prepare(tx.readEntries(), tx.writeEntries())) {
            onAllReplies();

            // If nowhere to send, mark done.
            onDone(tx);
        }

        markInitialized();
    }

    /**
     * @param reads Read entries.
     * @param writes Write entries.
     * @return {@code True} if some mapping was added.
     */
    @SuppressWarnings({"unchecked"})
    private boolean prepare(Iterable<GridCacheTxEntry<K, V>> reads, Iterable<GridCacheTxEntry<K, V>> writes) {
        // Assign keys to primary nodes.
        for (GridCacheTxEntry<K, V> read : reads)
            map(read);

        for (GridCacheTxEntry<K, V> write : writes)
            map(write);

        if (isDone())
            return false;

        boolean ret = false;

        GridCacheVersion minVer = tx.minVersion();

        Collection<GridCacheVersion> committed = cacheCtx.tm().committedVersions(minVer);
        Collection<GridCacheVersion> rolledback = cacheCtx.tm().rolledbackVersions(minVer);

        // Create mini futures.
        for (GridDistributedTxMapping<K, V> dhtMapping : dhtMap.values()) {
            assert !dhtMapping.isEmpty();

            GridRichNode n = dhtMapping.node();

            assert !n.isLocal();

            ret = true;

            GridDistributedTxMapping<K, V> nearMapping = nearMap.get(n.id());

            MiniFuture fut = new MiniFuture(dhtMapping, nearMapping);

            add(fut); // Append new future.

            GridDistributedBaseMessage<K, V> req = new GridDhtTxPrepareRequest<K, V>(futId, fut.futureId(), tx,
                dhtMapping.writes(), nearMapping == null ? null : nearMapping.writes());

            req.completedVersions(committed, rolledback);

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

        for (GridDistributedTxMapping<K, V> nearMapping : nearMap.values()) {
            if (!dhtMap.containsKey(nearMapping.node().id())) {
                assert nearMapping.writes() != null;

                ret = true;

                MiniFuture fut = new MiniFuture(null, nearMapping);

                add(fut); // Append new future.

                GridDistributedBaseMessage<K, V> req =
                    new GridDhtTxPrepareRequest<K, V>(futId, fut.futureId(), tx, null, nearMapping.writes());

                req.completedVersions(committed, rolledback);

                try {
                    cacheCtx.io().send(nearMapping.node(), req);
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
     * @param entry Transaction entry.
     */
    private void map(GridCacheTxEntry<K, V> entry) {
        GridDhtCacheEntry<K, V> cached = (GridDhtCacheEntry<K, V>)entry.cached();

        Collection<GridNode> dhtNodes = cacheCtx.dht().topology().nodes(cached.partition());

        if (log.isDebugEnabled())
            log.debug("Mapping entry to DHT nodes [nodes=" + U.toShortString(dhtNodes) + ", entry=" + entry + ']');

        Collection<UUID> readers = cached.readers();

        Collection<GridNode> nearNodes = null;

        if (!F.isEmpty(readers)) {
            nearNodes = cacheCtx.discovery().nodes(readers, F.<UUID>not(F.idForNodeId(tx.nearNodeId())));

            if (log.isDebugEnabled())
                log.debug("Mapping entry to near nodes [nodes=" + U.toShortString(nearNodes) + ", entry=" + entry + ']');
        }
        else if (log.isDebugEnabled())
            log.debug("Entry has no near readers: " + entry);

        map(entry, F.view(dhtNodes, F.remoteNodes(cacheCtx.nodeId())), dhtMap); // Exclude local node.
        map(entry, nearNodes, nearMap);
    }

    /**
     * @param entry Entry.
     * @param nodes Nodes.
     * @param map Map.
     */
    private void map(GridCacheTxEntry<K, V> entry, Iterable<GridNode> nodes,
        Map<UUID, GridDistributedTxMapping<K, V>> map) {
        if (nodes != null)
            for (GridNode n : nodes) {
                GridDistributedTxMapping<K, V> m = map.get(n.id());

                if (m == null)
                    map.put(n.id(), m = new GridDistributedTxMapping<K, V>(cacheCtx.rich().rich(n)));

                m.add(entry);
            }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtTxPrepareFuture.class, this, "super", super.toString());
    }

    /**
     * Mini-future for get operations. Mini-futures are only waiting on a single
     * node as opposed to multiple nodes.
     */
    private class MiniFuture extends GridFutureAdapter<GridCacheTx> {
        /** */
        private final GridUuid futId = GridUuid.randomUuid();

        /** DHT mapping. */
        @GridToStringInclude
        private GridDistributedTxMapping<K, V> dhtMapping;

        /** Near mapping. */
        @GridToStringInclude
        private GridDistributedTxMapping<K, V> nearMapping;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public MiniFuture() {
            super(cacheCtx.kernalContext());
        }

        /**
         * @param dhtMapping Mapping.
         * @param nearMapping nearMapping.
         */
        MiniFuture(GridDistributedTxMapping<K, V> dhtMapping, GridDistributedTxMapping<K, V> nearMapping) {
            super(cacheCtx.kernalContext());

            assert dhtMapping == null || nearMapping == null || dhtMapping.node() == nearMapping.node();

            this.dhtMapping = dhtMapping;
            this.nearMapping = nearMapping;
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
            return dhtMapping != null ? dhtMapping.node() : nearMapping.node();
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

            Collection<GridCacheTxEntry<K, V>> reads = null;
            Collection<GridCacheTxEntry<K, V>> writes = null;

            // Remove previous mapping.
            if (dhtMapping != null) {
                reads = dhtMapping.reads();
                writes = dhtMapping.writes();

                dhtMap.remove(dhtMapping.node().id());
            }

            if (nearMapping != null) {
                reads = F.concat(false, reads, nearMapping.reads());
                writes = F.concat(false, writes, nearMapping.writes());

                nearMap.remove(nearMapping.node().id());
            }

            // Remap.
            prepare(reads, writes);

            onDone();
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridDhtTxPrepareResponse<K, V> res) {
            if (res.error() != null)
                // Fail the whole compound future.
                onError(res.error());
            else {
                int[] dhtEvicted = res.dhtEvicted();

                if (dhtEvicted != null && dhtMapping != null) {
                    // Update mapping.
                    dhtMapping.evictPartitions(dhtEvicted);
                }

                if (nearMapping != null && !F.isEmpty(res.nearEvicted())) {
                    nearMapping.evictReaders(res.nearEvicted());

                    for (GridCacheTxEntry<K, V> entry : F.concat(false, nearMapping.reads(), nearMapping.writes())) {
                        if (res.nearEvicted().contains(entry.key())) {
                            while (true) {
                                try {
                                    GridDhtCacheEntry<K, V> cached = (GridDhtCacheEntry<K, V>)entry.cached();

                                    cached.removeReader(nearMapping.node().id(), res.messageId());

                                    break;
                                }
                                catch (GridCacheEntryRemovedException ignore) {
                                    GridCacheEntryEx<K, V> e = cacheCtx.cache().peekEx(entry.key());

                                    if (e == null) {
                                        break;
                                    }

                                    entry.cached(e, entry.keyBytes());
                                }
                            }
                        }
                    }
                }

                // Finish mini future.
                onDone(tx);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this, "super", super.toString());
        }
    }
}
