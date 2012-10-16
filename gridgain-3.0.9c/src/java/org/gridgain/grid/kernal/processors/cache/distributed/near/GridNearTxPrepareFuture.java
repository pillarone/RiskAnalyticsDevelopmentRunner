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
public class GridNearTxPrepareFuture<K, V> extends GridCompoundIdentityFuture<GridCacheTx>
    implements GridCacheMvccFuture<K, V, GridCacheTx> {
    /** Context. */
    private GridCacheContext<K, V> cacheCtx;

    /** Future ID. */
    private GridUuid futId;

    /** Transaction. */
    @GridToStringExclude
    private GridNearTxLocal<K, V> tx;

    /** Mappings. */
    private Map<UUID, GridDistributedTxMapping<K, V>> mappings;

    /** Logger. */
    private GridLogger log;

    /** Error. */
    @GridToStringExclude
    private AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearTxPrepareFuture() {
        // No-op.
    }

    /**
     * @param cacheCtx Context.
     * @param tx Transaction.
     */
    public GridNearTxPrepareFuture(GridCacheContext<K, V> cacheCtx, final GridNearTxLocal<K, V> tx) {
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
        assert tx != null;

        this.cacheCtx = cacheCtx;
        this.tx = tx;

        mappings = tx.mappings();

        futId = GridUuid.randomUuid();

        log = cacheCtx.logger(getClass());
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

        if (owner != null && tx.hasWriteKey(entry.key())) {
            // This will check for locks.
            onDone();

            return true;
        }

        return false;
    }

    /**
     * @return {@code True} if all locks are owned.
     */
    private boolean checkLocks() {
        for (GridCacheTxEntry<K, V> txEntry : tx.writeEntries()) {
            while (true) {
                GridCacheEntryEx<K, V> cached = txEntry.cached();

                try {
                    GridCacheVersion ver = txEntry.explicitVersion() != null ?
                        txEntry.explicitVersion() : tx.xidVersion();

                    // If locks haven't been acquired yet, keep waiting.
                    if (!cached.lockedBy(ver)) {
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

        if (log.isDebugEnabled())
            log.debug("All locks are acquired for near prepare future: " + this);

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
     * @param e Error.
     */
    void onError(Throwable e) {
        if (err.compareAndSet(null, e)) {
            boolean marked = tx.setRollbackOnly();

            if (e instanceof GridCacheTxRollbackException)
                if (marked) {
                    try {
                        tx.rollback();
                    }
                    catch (GridException ex) {
                        U.error(log, "Failed to automatically rollback transaction: " + tx, ex);
                    }
                }

            onComplete();
        }
    }

    /**
     * @param nodeId Sender.
     * @param res Result.
     */
    void onResult(UUID nodeId, GridNearTxPrepareResponse<K, V> res) {
        if (!isDone())
            for (GridFuture<GridCacheTx> fut : pending())
                if (isMini(fut)) {
                    MiniFuture f = (MiniFuture)fut;

                    if (f.futureId().equals(res.miniId())) {
                        assert f.node().id().equals(nodeId);

                        f.onResult(res);
                    }
                }
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(GridCacheTx t, Throwable err) {
        // If locks were not acquired yet, delay completion.
        if (isDone() || (err == null && !tx.ec() && !checkLocks()))
            return false;

        this.err.compareAndSet(null, err);

        if (err == null)
            tx.state(PREPARED);

        if (super.onDone(tx, err)) {
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
    private boolean isMini(GridFuture<?> f) {
        return f.getClass().equals(MiniFuture.class);
    }

    /**
     * Completeness callback.
     */
    private void onComplete() {
        if (super.onDone(tx, err.get()))
            // Don't forget to clean up.
            cacheCtx.mvcc().removeFuture(this);
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
        prepare(
            tx.optimistic() && tx.serializable() ? tx.readEntries() : Collections.<GridCacheTxEntry<K, V>>emptyList(),
            tx.writeEntries());

        markInitialized();
    }

    /**
     * @param reads Read entries.
     * @param writes Write entries.
     */
    @SuppressWarnings({"unchecked"})
    private void prepare(Iterable<GridCacheTxEntry<K, V>> reads, Iterable<GridCacheTxEntry<K, V>> writes) {
        Collection<GridRichNode> nodes = CU.allNodes(cacheCtx);

        if (mappings == null)
            mappings = new ConcurrentHashMap<UUID, GridDistributedTxMapping<K, V>>(nodes.size());

        // Assign keys to primary nodes.
        for (GridCacheTxEntry<K, V> read : reads)
            map(read, nodes);

        for (GridCacheTxEntry<K, V> write : writes)
            map(write, nodes);

        Collection<K> retries = new LinkedList<K>();

        // Create mini futures.
        for (final GridDistributedTxMapping<K, V> m : mappings.values()) {
            if (isDone())
                return;

            assert !m.isEmpty();

            GridRichNode n = m.node();

            GridNearTxPrepareRequest<K, V> req = new GridNearTxPrepareRequest<K, V>(futId, tx,
                tx.optimistic() && tx.serializable() ? m.reads() : null, m.writes(), tx.syncCommit(),
                tx.syncRollback());

            // If this is the primary node for the keys.
            if (n.isLocal()) {
                // Make sure not to provide Near entries to DHT cache.
                req.cloneEntries(cacheCtx);

                req.miniId(GridUuid.randomUuid());

                // At this point, if any new node joined, then it is
                // waiting for this transaction to complete, so
                // partition reassignments are not possible here.
                GridFuture<GridCacheTx> fut = cacheCtx.near().dht().prepareTx(n, req);

                // Add new future.
                add(new GridEmbeddedFuture<GridCacheTx, GridCacheTx>(
                    cacheCtx.kernalContext(),
                    fut,
                    new C2<GridCacheTx, Exception, GridCacheTx>() {
                        @Override public GridCacheTx apply(GridCacheTx t, Exception ex) {
                            if (ex != null) {
                                onError(ex);

                                return t;
                            }

                            GridCacheTxLocalEx<K, V> dhtTx = (GridCacheTxLocalEx<K, V>)t;

                            tx.addDhtVersion(m.node().id(), dhtTx.xidVersion());

                            GridCacheVersion min = dhtTx.minVersion();

                            GridCacheTxManager<K, V> tm = cacheCtx.near().dht().context().tm();

                            tx.orderCompleted(m, tm.committedVersions(min), tm.rolledbackVersions(min));

                            return tx;
                        }
                    }
                ));
            }
            else {
                MiniFuture fut = new MiniFuture(m);

                req.miniId(fut.futureId());

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

        // Remap.
        prepareRetries(tx.readEntries(), tx.writeEntries(), retries);
    }

    /**
     * @param entry Transaction entry.
     * @param nodes Nodes.
     */
    private void map(GridCacheTxEntry<K, V> entry, Collection<GridRichNode> nodes) {
        GridRichNode primary = CU.primary0(cacheCtx.affinity(entry.key(), nodes));

        if (log.isDebugEnabled())
            log.debug("Mapped key to primary node [key=" + entry.key() + ", partition=" + cacheCtx.partition(entry.key()) +
                ", primary=" + U.toShortString(primary) + ", allNodes=" + U.toShortString(nodes) + ']');

        GridDistributedTxMapping<K, V> m = mappings.get(primary.id());

        if (m == null)
            mappings.put(primary.id(), m = new GridDistributedTxMapping<K, V>(primary));

        m.add(entry);

        entry.nodeId(primary.id());
    }

    /**
     * @param reads Reads.
     * @param writes Writes.
     * @param retries Retries.
     */
    private void prepareRetries(Collection<GridCacheTxEntry<K, V>> reads, Collection<GridCacheTxEntry<K, V>> writes,
        final Collection<K> retries) {
        if (!F.isEmpty(retries)) {
            if (log.isDebugEnabled())
                log.debug("Remapping mini get future [leftOvers=" + retries + ", fut=" + this + ']');

            P1<GridCacheTxEntry<K, V>> p = new P1<GridCacheTxEntry<K, V>>() {
                @Override public boolean apply(GridCacheTxEntry<K, V> e) {
                    return retries.contains(e.key());
                }
            };

            // This will append new futures to compound list.
            prepare(F.view(reads, p), F.view(writes, p));
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearTxPrepareFuture.class, this, super.toString());
    }

    /**
     * Mini-future for get operations. Mini-futures are only waiting on a single
     * node as opposed to multiple nodes.
     */
    private class MiniFuture extends GridFutureAdapter<GridCacheTx> {
        /** */
        private final GridUuid futId = GridUuid.randomUuid();

        /** Keys. */
        @GridToStringInclude
        private GridDistributedTxMapping<K, V> m;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public MiniFuture() {
            // No-op.
        }

        /**
         * @param m Mapping.
         */
        MiniFuture(GridDistributedTxMapping<K, V> m) {
            super(cacheCtx.kernalContext());

            this.m = m;
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
            return m.node();
        }

        /**
         * @return Keys.
         */
        public GridDistributedTxMapping<K, V> mapping() {
            return m;
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

            // Remove previous mapping.
            mappings.remove(m.node().id());

            // Remap.
            prepare(m.reads(), m.writes());

            onDone();
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridNearTxPrepareResponse<K, V> res) {
            if (res.error() != null) {
                // Fail the whole compound future.
                onError(res.error());
            }
            else {
                prepareRetries(m.reads(), m.writes(), res.retries());

                // Register DHT version.
                tx.addDhtVersion(m.node().id(), res.dhtVersion());

                tx.orderCompleted(m, res.committedVersions(), res.rolledbackVersions());

                // Finish this mini future.
                onDone();
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this, super.toString());
        }
    }
}
