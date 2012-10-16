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

/**
 *
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearTxFinishFuture<K, V> extends GridCompoundIdentityFuture<GridCacheTx>
    implements GridCacheFuture<GridCacheTx> {
    /** Context. */
    private GridCacheContext<K, V> cacheCtx;

    /** Future ID. */
    private GridUuid futId;

    /** Transaction. */
    @GridToStringExclude
    private GridNearTxLocal<K, V> tx;

    /** Commit flag. */
    private boolean commit;

    /** Logger. */
    private GridLogger log;

    /** Error. */
    private AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);

    /** Node mappings. */
    private ConcurrentMap<UUID, GridDistributedTxMapping<K, V>> mappings;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearTxFinishFuture() {
        // No-op.
    }

    /**
     * @param cacheCtx Context.
     * @param tx Transaction.
     * @param commit Commit flag.
     */
    public GridNearTxFinishFuture(GridCacheContext<K, V> cacheCtx, final GridNearTxLocal<K, V> tx, boolean commit) {
        super(cacheCtx.kernalContext(), new GridReducer<GridCacheTx, GridCacheTx>() {
            @Override public boolean collect(GridCacheTx e) {
                return true;
            }

            @Override public GridCacheTx apply() {
                return tx; // Nothing to aggregate.
            }

            @Override public String toString() {
                // Can't print the whole transaction here due to stack overflow.
                return "Near finish reducer for tx: " + tx.xidVersion();
            }
        });

        assert cacheCtx != null;

        this.cacheCtx = cacheCtx;
        this.tx = tx;
        this.commit = commit;

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
                    if (isMini(f))
                        return ((MiniFuture)f).node();

                    return cacheCtx.rich().rich(cacheCtx.discovery().localNode());
                }
            });
    }

    /** {@inheritDoc} */
    @Override public boolean onNodeLeft(UUID nodeId) {
        for (GridFuture<?> fut : futures())
            if (isMini(fut)) {
                MiniFuture f = (MiniFuture)fut;

                if (f.node().id().equals(nodeId)) {
                    // Remove previous mapping.
                    mappings.remove(nodeId);

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
    void onResult(UUID nodeId, GridNearTxFinishResponse<K, V> res) {
        if (!isDone())
            for (GridFuture<GridCacheTx> fut : futures()) {
                if (isMini(fut)) {
                    MiniFuture f = (MiniFuture)fut;

                    if (f.futureId().equals(res.miniId())) {
                        assert f.node().id().equals(nodeId);

                        f.onResult(res);
                    }
                }
            }
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(GridCacheTx tx, Throwable err) {
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
        onDone(tx, err.get());
    }

    /**
     * Completes this future.
     */
    void complete() {
        onComplete();
    }

    /**
     * @return Synchronous flag.
     */
    private boolean isSync() {
        return tx.syncCommit() && commit || tx.syncRollback() && !commit;
    }

    /**
     * Initializes future.
     */
    @SuppressWarnings({"unchecked"})
    void finish() {
        if (mappings != null) {
            finish(mappings.values());

            if (!isSync()) {
                boolean complete = true;

                for (GridFuture<?> f : pending())
                    if (isMini(f) && !f.isDone())
                        complete = false;

                if (complete)
                    onComplete();
            }
        }
        else {
            assert !commit;

            try {
                tx.rollback();
            }
            catch (GridException e) {
                U.error(log, "Failed to rollback empty transaction: " + tx, e);
            }
        }

        markInitialized();
    }

    /**
     * @param mappings Mappings.
     */
    @SuppressWarnings({"unchecked"})
    private void finish(Iterable<GridDistributedTxMapping<K, V>> mappings) {
        // Create mini futures.
        for (GridDistributedTxMapping<K, V> m : mappings)
            finish(m);
    }

    /**
     * @param reads Read entries.
     * @param writes Write entries.
     */
    @SuppressWarnings({"unchecked"})
    private void prepare(Iterable<GridCacheTxEntry<K, V>> reads, Iterable<GridCacheTxEntry<K, V>> writes) {
        Collection<GridRichNode> nodes = CU.allNodes(cacheCtx);

        // Assign keys to primary nodes.
        for (GridCacheTxEntry<K, V> read : reads)
            map(read, nodes);

        for (GridCacheTxEntry<K, V> write : writes)
            map(write, nodes);

        // Create mini futures.
        for (GridDistributedTxMapping<K, V> m : mappings.values())
            finish(m);
    }

    /**
     * @param m Mapping.
     */
    @SuppressWarnings({"unchecked"})
    private void finish(GridDistributedTxMapping<K, V> m) {
        GridRichNode n = m.node();

        assert !m.isEmpty();

        GridNearTxFinishRequest req = new GridNearTxFinishRequest<K, V>(
            futId,
            tx.xidVersion(),
            tx.commitVersion(),
            tx.threadId(),
            commit,
            tx.isInvalidate(),
            m.explicitLock(),
            null,
            null,
            null,
            commit && tx.pessimistic() ? m.writes() : null,
            tx.syncCommit() && commit || tx.syncRollback() && !commit
        );

        // If this is the primary node for the keys.
        if (n.isLocal()) {
            req.miniId(GridUuid.randomUuid());

            GridFuture<GridCacheTx> fut = commit ?
                dht().commitTx(n.id(), req) : dht().rollbackTx(n.id(), req);

            // Add new future.
            add(fut);
        }
        else {
            MiniFuture fut = new MiniFuture(m);

            req.miniId(fut.futureId());

            add(fut); // Append new future.

            try {
                cacheCtx.io().send(n, req);

                // If we don't wait for result, then mark future as done.
                if (!isSync() && !m.explicitLock())
                    fut.onDone();
            }
            catch (GridTopologyException e) {
                // Remove previous mapping.
                mappings.remove(m.node().id());

                fut.onResult(e);
            }
            catch (GridException e) {
                // Fail the whole thing.
                fut.onResult(e);
            }
        }
    }

    /**
     * @param entry Transaction entry.
     * @param nodes Nodes.
     */
    private void map(GridCacheTxEntry<K, V> entry, Collection<GridRichNode> nodes) {
        GridRichNode primary = CU.primary0(cacheCtx.affinity(entry.key(), nodes));

        GridDistributedTxMapping<K, V> t = mappings.get(primary.id());

        if (t == null)
            mappings.put(primary.id(), t = new GridDistributedTxMapping<K, V>(primary));

        t.add(entry);
    }

    /**
     * @return DHT cache.
     */
    private GridDhtCache<K, V> dht() {
        return cacheCtx.near().dht();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearTxFinishFuture.class, this, super.toString());
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

            if (!isDone()) {
                // Remap.
                prepare(m.reads(), m.writes());

                onDone();
            }
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridNearTxFinishResponse<K, V> res) {
            onDone();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this, super.toString());
        }
    }
}
