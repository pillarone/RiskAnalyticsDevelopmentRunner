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
import java.util.concurrent.atomic.*;

/**
 *
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtTxFinishFuture<K, V> extends GridCompoundIdentityFuture<GridCacheTx>
    implements GridCacheFuture<GridCacheTx> {
    /** Context. */
    private GridCacheContext<K, V> cacheCtx;

    /** Future ID. */
    private GridUuid futId;

    /** Transaction. */
    @GridToStringExclude
    private GridDhtTxLocal<K, V> tx;

    /** Commit flag. */
    private boolean commit;

    /** Logger. */
    private GridLogger log;

    /** Error. */
    @GridToStringExclude
    private AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);

    /** DHT mappings. */
    private Map<UUID, GridDistributedTxMapping<K, V>> dhtMap;

    /** Near mappings. */
    private Map<UUID, GridDistributedTxMapping<K, V>> nearMap;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtTxFinishFuture() {
        // No-op.
    }

    /**
     * @param cacheCtx Context.
     * @param tx Transaction.
     * @param commit Commit flag.
     */
    public GridDhtTxFinishFuture(GridCacheContext<K, V> cacheCtx, final GridDhtTxLocal<K, V> tx, boolean commit) {
        super(cacheCtx.kernalContext(), new GridReducer<GridCacheTx, GridCacheTx>() {
            @Override public boolean collect(GridCacheTx e) {
                return true;
            }

            @Override public GridCacheTx apply() {
                return tx; // Nothing to aggregate.
            }

            @Override public String toString() {
                return "DHT finish reducer for tx: " + tx;
            }
        });

        assert cacheCtx != null;

        this.cacheCtx = cacheCtx;
        this.tx = tx;
        this.commit = commit;

        dhtMap = tx.dhtMap();
        nearMap = tx.nearMap();

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
    void onResult(UUID nodeId, GridDhtTxFinishResponse<K, V> res) {
        if (!isDone())
            for (GridFuture<GridCacheTx> fut : futures())
                if (isMini(fut)) {
                    MiniFuture f = (MiniFuture)fut;

                    if (f.futureId().equals(res.miniId())) {
                        assert f.node().id().equals(nodeId);

                        f.onResult(res);
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
    @SuppressWarnings({"unchecked"}) void finish() {
        if (!F.isEmpty(dhtMap) || !F.isEmpty(nearMap)) {
            finish(dhtMap, nearMap);

            if (!isSync())
                onComplete();
        }
        else
            // No backup or near nodes to send commit message too (just complete then).
            onComplete();

        markInitialized();
    }

    /**
     * @param dhtMap DHT map.
     * @param nearMap Near map.
     */
    @SuppressWarnings({"unchecked"})
    private void finish(Map<UUID, GridDistributedTxMapping<K, V>> dhtMap,
        Map<UUID, GridDistributedTxMapping<K, V>> nearMap) {
        // Create mini futures.
        for (GridDistributedTxMapping<K, V> dhtMapping : dhtMap.values()) {
            GridRichNode n = dhtMapping.node();

            assert !n.isLocal();

            GridDistributedTxMapping<K, V> nearMapping = nearMap.get(n.id());

            if (dhtMapping.isEmpty() && nearMapping != null && nearMapping.isEmpty())
                // Nothing to send.
                continue;

            MiniFuture fut = new MiniFuture(dhtMapping, nearMapping);

            add(fut); // Append new future.

            GridCacheMessage req = new GridDhtTxFinishRequest<K, V>(
                tx.nearNodeId(),
                futId,
                fut.futureId(),
                tx.xidVersion(),
                tx.commitVersion(),
                tx.threadId(),
                tx.isolation(),
                commit,
                tx.isInvalidate(),
                tx.completedBase(),
                tx.committedVersions(),
                tx.rolledbackVersions(),
                tx.pessimistic() ? dhtMapping.writes() : null,
                tx.pessimistic() && nearMapping != null ? nearMapping.writes() : null,
                commit ? tx.syncCommit() : tx.syncRollback());

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
                if (nearMapping.isEmpty())
                    // Nothing to send.
                    continue;

                MiniFuture fut = new MiniFuture(null, nearMapping);

                add(fut); // Append new future.

                GridCacheMessage req = new GridDhtTxFinishRequest<K, V>(
                    tx.nearNodeId(),
                    futId,
                    fut.futureId(),
                    tx.xidVersion(),
                    tx.commitVersion(),
                    tx.threadId(),
                    tx.isolation(),
                    commit,
                    tx.isInvalidate(),
                    tx.completedBase(),
                    tx.committedVersions(),
                    tx.rolledbackVersions(),
                    null,
                    tx.pessimistic() ? nearMapping.writes() : null,
                    commit ? tx.syncCommit() : tx.syncRollback());

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
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtTxFinishFuture.class, this, super.toString());
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
            // No-op.
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

            // If node left, then there is nothing to commit on it.
            onDone();
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridDhtTxFinishResponse<K, V> res) {
            if (log.isDebugEnabled())
                log.debug("Transaction synchronously completed on node [node=" + node() + ", res=" + res + ']');

            onDone();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this);
        }
    }
}
