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
import org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.cache.GridCachePeekMode.*;
import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * DHT cache.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtCache<K, V> extends GridDistributedCacheAdapter<K, V> {
    /** Near cache. */
    @GridToStringExclude
    private GridNearCache<K, V> near;

    /** Topology. */
    private GridDhtPartitionTopology<K, V> top;

    /** Preloader. */
    private GridCachePreloader<K, V> preldr;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtCache() {
        // No-op.
    }

    /**
     * @param ctx Context.
     */
    public GridDhtCache(GridCacheContext<K, V> ctx) {
        super(ctx, ctx.config().getStartSize());

        top = new GridDhtPartitionTopologyImpl<K, V>(ctx);
    }

    /**
     * @param p Partition ID.
     * @param create If {@code true}, then partition will be created if it's not there.
     * @return Local partition.
     */
    @Nullable public GridDhtLocalPartition<K, V> localPartition(int p, boolean create) {
        return top.localPartition(p, create);
    }

    /** {@inheritDoc} */
    @Override protected void init() {
        map.setEntryFactory(new GridCacheMapEntryFactory<K, V>() {
            /** {@inheritDoc} */
            @Override public GridCacheMapEntry<K, V> create(GridCacheContext<K, V> ctx, K key, int hash, V val,
                GridCacheMapEntry<K, V> next, long ttl) {
                return new GridDhtCacheEntry<K, V>(ctx, key, hash, val, next, ttl);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public String name() {
        String name = super.name();

        return name == null ? "defaultDhtCache" : name + "Dht";
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        super.start();

        metrics.delegate(ctx.dht().near().metrics0());

        preldr = new GridDhtPreloader<K, V>(ctx);

        preldr.start();

        ctx.io().addHandler(GridNearGetRequest.class, new CI2<UUID, GridNearGetRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridNearGetRequest<K, V> req) {
                processNearGetRequest(nodeId, req);
            }
        });

        ctx.io().addHandler(GridDhtTxPrepareRequest.class, new CI2<UUID, GridDhtTxPrepareRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridDhtTxPrepareRequest<K, V> req) {
                processDhtTxPrepareRequest(nodeId, req);
            }
        });

        ctx.io().addHandler(GridDhtTxPrepareResponse.class, new CI2<UUID, GridDhtTxPrepareResponse<K, V>>() {
            @Override public void apply(UUID nodeId, GridDhtTxPrepareResponse<K, V> res) {
                processDhtTxPrepareResponse(nodeId, res);
            }
        });

        ctx.io().addHandler(GridNearTxPrepareRequest.class, new CI2<UUID, GridNearTxPrepareRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridNearTxPrepareRequest<K, V> req) {
                processNearTxPrepareRequest(nodeId, req);
            }
        });

        ctx.io().addHandler(GridNearTxFinishRequest.class, new CI2<UUID, GridNearTxFinishRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridNearTxFinishRequest<K, V> req) {
                processNearTxFinishRequest(nodeId, req);
            }
        });

        ctx.io().addHandler(GridDhtTxFinishRequest.class, new CI2<UUID, GridDhtTxFinishRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridDhtTxFinishRequest<K, V> req) {
                processDhtTxFinishRequest(nodeId, req);
            }
        });

        ctx.io().addHandler(GridDhtTxFinishResponse.class, new CI2<UUID, GridDhtTxFinishResponse<K, V>>() {
            @Override public void apply(UUID nodeId, GridDhtTxFinishResponse<K, V> req) {
                processDhtTxFinishResponse(nodeId, req);
            }
        });

        ctx.io().addHandler(GridNearLockRequest.class, new CI2<UUID, GridNearLockRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridNearLockRequest<K, V> req) {
                processNearLockRequest(nodeId, req);
            }
        });

        ctx.io().addHandler(GridDhtLockRequest.class, new CI2<UUID, GridDhtLockRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridDhtLockRequest<K, V> req) {
                processDhtLockRequest(nodeId, req);
            }
        });

        ctx.io().addHandler(GridDhtLockResponse.class, new CI2<UUID, GridDhtLockResponse<K, V>>() {
            @Override public void apply(UUID nodeId, GridDhtLockResponse<K, V> req) {
                processDhtLockResponse(nodeId, req);
            }
        });

        ctx.io().addHandler(GridNearUnlockRequest.class, new CI2<UUID, GridNearUnlockRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridNearUnlockRequest<K, V> req) {
                processNearUnlockRequest(nodeId, req);
            }
        });

        ctx.io().addHandler(GridDhtUnlockRequest.class, new CI2<UUID, GridDhtUnlockRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridDhtUnlockRequest<K, V> req) {
                processDhtUnlockRequest(nodeId, req);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public void stop() {
        super.stop();

        if (preldr != null)
            preldr.stop();
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        super.onKernalStart();

        preldr.onKernalStart();
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop() {
        if (preldr != null)
            preldr.onKernalStop();
    }

    /**
     * @return Near cache.
     */
    public GridNearCache<K, V> near() {
        return near;
    }

    /**
     * @param near Near cache.
     */
    public void near(GridNearCache<K, V> near) {
        this.near = near;
    }

    /**
     * @return Partition topology.
     */
    public GridDhtPartitionTopology<K, V> topology() {
        return top;
    }

    /** {@inheritDoc} */
    @Override public GridCachePreloader<K, V> preloader() {
        return preldr;
    }

    /**
     * @return DHT preloader.
     */
    public GridDhtPreloader<K, V> dhtPreloader() {
        assert preldr instanceof GridDhtPreloader;

        return (GridDhtPreloader<K, V>)preldr;
    }

    /**
     * @param key Key.
     * @return DHT entry.
     */
    @Nullable public GridDhtCacheEntry<K, V> peekExx(K key) {
        return (GridDhtCacheEntry<K, V>)peekEx(key);
    }

    /**
     * @param key Key.
     * @return DHT entry.
     */
    GridDhtCacheEntry<K, V> entryExx(K key) {
        return (GridDhtCacheEntry<K, V>)entryEx(key);
    }

    /**
     * This method is used internally. Use {@link #getDhtAsync(UUID, Collection, boolean, GridPredicate[])}
     * method instead to retrieve DHT value.
     *
     * @param keys {@inheritDoc}
     * @param filter {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override public GridFuture<Map<K, V>> getAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return getAllAsync(keys, /*don't check local tx. */false, filter);
    }

    /**
     * @param reader Reader node ID.
     * @param keys Keys to get.
     * @param reload Reload flag.
     * @param filter Optional filter.
     * @return DHT future.
     */
    public GridDhtFuture<K, Collection<GridCacheEntryInfo<K, V>>> getDhtAsync(UUID reader,
        Collection<? extends K> keys, boolean reload, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        GridDhtGetFuture<K, V> fut = new GridDhtGetFuture<K, V>(ctx, -1, reader, keys, reload, /*tx*/ null, filter);

        fut.init();

        return fut;
    }

    /**
     * @param req Request.
     * @throws GridException If failed.
     */
    private void unmarshal(GridDistributedTxPrepareRequest<K, V> req) throws GridException {
        for (GridCacheTxEntry<K, V> e : F.concat(false, req.reads(), req.writes()))
            e.unmarshal(ctx, ctx.deploy().globalLoader());
    }

    /**
     * @param nearNode Near node that initiated transaction.
     * @param req Near prepare request.
     * @return Future for transaction.
     */
    public GridFuture<GridCacheTx> prepareTx(final GridNode nearNode, final GridNearTxPrepareRequest<K, V> req) {
        try {
            unmarshal(req);
        }
        catch (GridException e) {
            return new GridFinishedFuture<GridCacheTx>(ctx.kernalContext(), e);
        }

        GridFuture<Object> fut = ctx.preloader().request(
            F.viewReadOnly(F.concat(false, req.reads(), req.writes()), CU.<K, V>tx2key()));

        return new GridEmbeddedFuture<GridCacheTx, Object>(
            ctx.kernalContext(),
            fut,
            new C2<Object, Exception, GridFuture<GridCacheTx>>() {
                @Override public GridFuture<GridCacheTx> apply(Object o, Exception ex) {
                    if (ex != null)
                        throw new GridClosureException(ex);

                    GridDhtTxLocal<K, V> tx = new GridDhtTxLocal<K, V>(nearNode.id(), req.version(), req.futureId(),
                        req.miniId(), req.threadId(), /*implicit*/false, ctx, req.concurrency(), req.isolation(),
                        req.timeout(), req.isInvalidate(), req.syncCommit(), req.syncRollback(), false);

                    tx = ctx.tm().onCreated(tx);

                    if (tx != null) {
                        try {
                            GridCompoundFuture<Boolean, GridCacheTx> txFut = null;

                            if (req.reads() != null)
                                for (GridCacheTxEntry<K, V> e : req.reads()) {
                                    GridFuture<Boolean> f = tx.addEntry(req.messageId(), e);

                                    // If reader has been added, then we have to wait for
                                    // transactions that affect this key to complete.
                                    if (f != null) {
                                        if (txFut == null)
                                            txFut = new GridCompoundFuture<Boolean, GridCacheTx>(ctx.kernalContext(),
                                                F.<Boolean, GridCacheTx>continuousReducer(tx));

                                        txFut.add(f);
                                    }
                                }

                            if (req.writes() != null)
                                for (GridCacheTxEntry<K, V> e : req.writes()) {
                                    GridFuture<Boolean> f = tx.addEntry(req.messageId(), e);

                                    // If reader has been added, then we have to wait for
                                    // transactions that affect this key to complete.
                                    if (f != null) {
                                        if (txFut == null)
                                            txFut = new GridCompoundFuture<Boolean, GridCacheTx>(ctx.kernalContext(),
                                                F.<Boolean, GridCacheTx>continuousReducer(tx));

                                        txFut.add(f);
                                    }
                                }

                            if (txFut != null)
                                txFut.markInitialized();

                            final GridDhtTxLocal<K, V> t = tx;

                            // Wait for active transactions that conflict with reader additions.
                            return txFut == null || txFut.isDone() ?
                                tx.prepareAsync() :
                                new GridEmbeddedFuture<GridCacheTx, GridCacheTx>(txFut,
                                    new C2<GridCacheTx, Exception, GridFuture<GridCacheTx>>() {
                                        @Override public GridFuture<GridCacheTx> apply(GridCacheTx tx, Exception e) {
                                            if (e != null)
                                                throw new GridClosureException(e);

                                            return t.prepareAsync();
                                        }
                                    }, ctx.kernalContext());
                        }
                        catch (GridException e) {
                            tx.setRollbackOnly();

                            return new GridFinishedFuture<GridCacheTx>(ctx.kernalContext(), e);
                        }
                    }
                    else {
                        tx = ctx.tm().tx(req.version());

                        if (tx != null)
                            return tx.future();

                        return new GridFinishedFuture<GridCacheTx>(ctx.kernalContext(), (GridCacheTx)null);
                    }
                }
            },
            new C2<GridCacheTx, Exception, GridCacheTx>() {
                @Nullable @Override public GridCacheTx apply(GridCacheTx tx, Exception e) {
                    if (e != null) {
                        if (tx != null)
                            tx.setRollbackOnly(); // Just in case.

                        if (!(e instanceof GridCacheTxOptimisticException))
                            U.error(log, "Failed to prepare DHT transaction: " + tx, e);
                    }

                    return tx;
                }
            }
        );
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     * @return Future.
     */
    public GridFuture<GridCacheTx> commitTx(UUID nodeId, GridNearTxFinishRequest<K, V> req) {
        return finish(nodeId, req);
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     * @return Future.
     */
    public GridFuture<GridCacheTx> rollbackTx(UUID nodeId, GridNearTxFinishRequest<K, V> req) {
        return finish(nodeId, req);
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     * @return Future.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    private GridFuture<GridCacheTx> finish(UUID nodeId, GridNearTxFinishRequest<K, V> req) {
        assert nodeId != null;
        assert req != null;

        GridCacheVersion dhtVer = ctx.tm().mappedVersion(req.version());

        GridDhtTxLocal<K, V> tx = null;

        if (dhtVer == null) {
            if (log.isDebugEnabled())
                log.debug("Received transaction finish request for unknown near version (was lock explicit?): " + req);
        }
        else
            tx = ctx.tm().tx(dhtVer);

        try {
            if (req.commit()) {
                if (tx == null) {
                    // Create transaction and add entries.
                    tx = ctx.tm().onCreated(
                        new GridDhtTxLocal<K, V>(
                            nodeId,
                            req.version(),
                            req.futureId(),
                            req.miniId(),
                            req.threadId(),
                            true,
                            ctx,
                            PESSIMISTIC,
                            READ_COMMITTED,
                            /*timeout */0,
                            req.isInvalidate(),
                            req.commit() && req.replyRequired(),
                            !req.commit() && req.replyRequired(),
                            req.explicitLock()));

                    if (tx == null || !ctx.tm().onStarted(tx))
                        throw new GridCacheTxRollbackException("Attempt to start a completed transaction: " + req);
                }

                tx.nearFinishFutureId(req.futureId());
                tx.nearFinishMiniId(req.miniId());

                boolean set = tx.commitVersion(req.commitVersion());

                assert set : "Failed to set commit version on transaction [req=" + req + ", tx=" + tx + ']';

                Collection<GridCacheTxEntry<K, V>> writeEntries = req.writes();

                if (!F.isEmpty(writeEntries)) {
                    // In OPTIMISTIC mode, we get the values at PREPARE stage.
                    assert tx.concurrency() == PESSIMISTIC;

                    for (GridCacheTxEntry<K, V> entry : writeEntries)
                        tx.addEntry(req.messageId(), entry);
                }

                if (tx.pessimistic())
                    tx.prepare();

                return tx.commitAsync();
            }
            else {
                assert tx != null : "Transaction is null for near rollback request [nodeId=" +
                    nodeId + ", req=" + req + "]";

                tx.nearFinishFutureId(req.futureId());
                tx.nearFinishMiniId(req.miniId());

                return tx.rollbackAsync();
            }
        }
        catch (Throwable e) {
            U.error(log, "Failed completing transaction [commit=" + req.commit() + ", tx=" + tx + ']', e);

            if (tx != null)
                return tx.rollbackAsync();

            return new GridFinishedFuture<GridCacheTx>(ctx.kernalContext(), e);
        }
    }

    /**
     * @param ctx Context.
     * @param nodeId Node ID.
     * @param tx Transaction.
     * @param req Request.
     * @param writes Writes.
     */
    private void finish(GridCacheContext<K, V> ctx, UUID nodeId, GridCacheTxRemoteEx<K, V> tx,
        GridDistributedTxFinishRequest<K, V> req, Collection<GridCacheTxEntry<K, V>> writes) {
        // We don't allow explicit locks for transactions and
        // therefore immediately return if transaction is null.
        // However, we may decide to relax this restriction in
        // future.
        if (tx == null) {
            if (req.commit())
                // Must be some long time duplicate, but we add it anyway.
                ctx.tm().addCommittedTx(req.version());
            else
                ctx.tm().addRolledbackTx(req.version());

            if (log.isDebugEnabled())
                log.debug("Received finish request for non-existing transaction (added to completed set) " +
                    "[senderNodeId=" + nodeId + ", res=" + req + ']');

            return;
        }
        else if (log.isDebugEnabled())
            log.debug("Received finish request for transaction [senderNodeId=" + nodeId + ", req=" + req +
                ", tx=" + tx + ']');

        try {
            ClassLoader ldr = ctx.deploy().globalLoader();

            if (req.commit()) {
                boolean set = tx.commitVersion(req.commitVersion());

                assert set;

                if (!F.isEmpty(writes)) {
                    // In OPTIMISTIC mode, we get the values at PREPARE stage.
                    assert tx.concurrency() == PESSIMISTIC;

                    for (GridCacheTxEntry<K, V> entry : writes) {
                        // Unmarshal write entries.
                        entry.unmarshal(ctx, ldr);

                        if (log.isDebugEnabled())
                            log.debug("Unmarshalled transaction entry from pessimistic transaction [key=" +
                                entry.key() + ", value=" + entry.value() + ", tx=" + tx + ']');

                        if (!tx.setWriteValue(entry))
                            U.warn(log, "Received entry to commit that was not present in transaction [entry=" +
                                entry + ", tx=" + tx + ']');
                    }
                }

                // Add completed versions.
                tx.doneRemote(req.baseVersion(), req.committedVersions(), req.rolledbackVersions());

                if (tx.pessimistic())
                    tx.prepare();

                tx.commit();
            }
            else {
                assert tx != null;

                tx.doneRemote(req.baseVersion(), req.committedVersions(), req.rolledbackVersions());

                tx.rollback();
            }
        }
        catch (Throwable e) {
            U.error(log, "Failed completing transaction [commit=" + req.commit() + ", tx=" + tx + ']', e);

            if (tx != null) {
                // Mark transaction for invalidate.
                tx.invalidate(true);

                try {
                    tx.commit();
                }
                catch (GridException ex) {
                    U.error(log, "Failed to invalidate transaction: " + tx, ex);
                }
            }
        }
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     * @return Remote transaction.
     * @throws GridException If failed.
     */
    @Nullable GridDhtTxRemote<K, V> startRemoteTx(UUID nodeId, GridDhtTxPrepareRequest<K, V> req) throws GridException {
        if (!F.isEmpty(req.writes())) {
            GridDhtTxRemote<K, V> tx = new GridDhtTxRemote<K, V>(
                req.nearNodeId(),
                req.futureId(),
                ctx.deploy().globalLoader(),
                nodeId,
                req.threadId(),
                req.version(),
                req.commitVersion(),
                req.concurrency(),
                req.isolation(),
                req.isInvalidate(),
                req.timeout(),
                req.writes(),
                ctx);

            tx = ctx.tm().onCreated(tx);

            if (tx == null || !ctx.tm().onStarted(tx)) {
                if (log.isDebugEnabled())
                    log.debug("Attempt to start a completed transaction (will ignore): " + tx);

                return null;
            }

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
    @Nullable GridDhtTxRemote<K, V> startRemoteTx(UUID nodeId, GridDhtTxFinishRequest<K, V> req)
        throws GridException, GridDistributedLockCancelledException {

        GridDhtTxRemote<K, V> tx;

        ClassLoader ldr = ctx.deploy().globalLoader();

        if (ldr != null) {
            // Handle implicit locks for pessimistic transactions.
            tx = ctx.tm().tx(req.version());

            for (GridCacheTxEntry<K, V> txEntry : req.writes()) {
                GridDistributedCacheEntry<K, V> entry = null;

                while (true) {
                    try {
                        entry = entryExx(txEntry.key());

                        // Handle implicit locks for pessimistic transactions.
                        tx = ctx.tm().tx(req.version());

                        if (tx != null)
                            tx.addWrite(txEntry.key(), txEntry.keyBytes(), txEntry.value(), txEntry.valueBytes());
                        else {
                            tx = new GridDhtTxRemote<K, V>(
                                req.nearNodeId(),
                                req.futureId(),
                                nodeId,
                                req.threadId(),
                                req.version(),
                                /*commitVer*/null,
                                PESSIMISTIC,
                                req.isolation(),
                                req.isInvalidate(),
                                0,
                                txEntry.key(),
                                txEntry.keyBytes(),
                                txEntry.value(),
                                txEntry.valueBytes(),
                                ctx);

                            tx = ctx.tm().onCreated(tx);

                            if (tx == null || !ctx.tm().onStarted(tx))
                                throw new GridCacheTxRollbackException("Failed to acquire lock " +
                                    "(transaction has been completed): " + req.version());
                        }

                        // Add remote candidate before reordering.
                        if (txEntry.explicitVersion() == null)
                            entry.addRemote(req.nearNodeId(), nodeId, req.threadId(), req.version(), 0, tx.ec(), true);

                        // Remote candidates for ordered lock queuing.
                        entry.addRemoteCandidates(
                            Collections.<GridCacheMvccCandidate<K>>emptyList(),
                            req.version(),
                            req.committedVersions(),
                            req.rolledbackVersions());

                        // Double-check in case if sender node left the grid.
                        if (ctx.discovery().node(req.nearNodeId()) == null) {
                            if (log.isDebugEnabled())
                                log.debug("Node requesting lock left grid (lock request will be ignored): " + req);

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

                        tx.clearEntry(entry.key());

                        if (log.isDebugEnabled())
                            log.debug("Cleared removed entry from remote transaction (will retry) [entry=" +
                                entry + ", tx=" + tx + ']');
                    }
                }
            }
        }
        else {
            String err = "Failed to acquire deployment class for message: " + req;

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
    @Nullable GridDhtTxRemote<K, V> startRemoteTx(UUID nodeId, GridDistributedLockRequest<K, V> req)
        throws GridException, GridDistributedLockCancelledException {
        List<byte[]> keyBytes = req.keyBytes();

        GridDhtTxRemote<K, V> tx = null;

        ClassLoader ldr = ctx.deploy().globalLoader();

        if (ldr != null) {
            for (int i = 0; i < keyBytes.size(); i++) {
                byte[] bytes = keyBytes.get(i);

                K key = req.keys().get(i);

                Collection<GridCacheMvccCandidate<K>> cands = req.candidatesByIndex(i);

                if (bytes == null)
                    continue;

                if (log.isDebugEnabled())
                    log.debug("Unmarshalled key: " + key);

                GridDistributedCacheEntry<K, V> entry = null;

                while (true) {
                    try {
                        entry = entryExx(key);

                        // Handle implicit locks for pessimistic transactions.
                        if (req.inTx()) {
                            tx = ctx.tm().tx(req.version());

                            if (tx != null)
                                tx.addWrite(key, bytes, /*value*/null, /*value bytes*/null);
                            else {
                                tx = new GridDhtTxRemote<K, V>(
                                    req.nodeId(),
                                    req.futureId(),
                                    nodeId,
                                    req.threadId(),
                                    req.version(),
                                    /*commitVer*/null,
                                    PESSIMISTIC,
                                    req.isolation(),
                                    req.isInvalidate(),
                                    req.timeout(),
                                    key,
                                    bytes,
                                    null, // Value.
                                    null, // Value bytes.
                                    ctx);

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
            String err = "Failed to acquire deployment class for message: " + req;

            U.warn(log, err);

            throw new GridException(err);
        }

        return tx;
    }

    /**
     * @param nodeId Node ID.
     * @param req Get request.
     */
    private void processNearGetRequest(final UUID nodeId, final GridNearGetRequest<K, V> req) {
        GridFuture<Collection<GridCacheEntryInfo<K, V>>> fut =
            getDhtAsync(nodeId, req.keys(), req.reload(), req.filter());

        fut.listenAsync(new CI1<GridFuture<Collection<GridCacheEntryInfo<K, V>>>>() {
            @Override public void apply(GridFuture<Collection<GridCacheEntryInfo<K, V>>> f) {
                GridNearGetResponse<K, V> res = new GridNearGetResponse<K, V>(
                    req.futureId(), req.miniId(), req.version());

                GridDhtFuture<K, Collection<GridCacheEntryInfo<K, V>>> fut =
                    (GridDhtFuture<K, Collection<GridCacheEntryInfo<K, V>>>)f;

                try {
                    Collection<GridCacheEntryInfo<K, V>> entries = fut.get();

                    res.entries(entries);
                }
                catch (GridException e) {
                    U.error(log, "Failed processing get request: " + req, e);

                    res.error(e);
                }

                res.retries(fut.retries());

                try {
                    ctx.io().send(nodeId, res);
                }
                catch (GridException e) {
                    U.error(log, "Failed to send get response to node (is node still alive?) [nodeId=" + nodeId +
                        ",req=" + req + ", res=" + res + ']', e);
                }
            }
        });
    }

    /**
     * @param nodeId Near node ID.
     * @param req Request.
     */
    private void processNearTxPrepareRequest(UUID nodeId, GridNearTxPrepareRequest<K, V> req) {
        assert nodeId != null;
        assert req != null;

        GridNode nearNode = ctx.node(nodeId);

        if (nearNode == null) {
            if (log.isDebugEnabled())
                log.debug("Received transaction request from node that left grid (will ignore): " + nodeId);

            return;
        }

        prepareTx(nearNode, req);
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    private void processNearTxFinishRequest(final UUID nodeId, final GridNearTxFinishRequest<K, V> req) {
        if (log.isDebugEnabled())
            log.debug("Processing near tx finish request [nodeId=" + nodeId + ", req=" + req + "]");

        GridFuture<?> f = finish(nodeId, req);

        // Only for error logging.
        f.listenAsync(new CI1<GridFuture<?>>() {
            @Override public void apply(GridFuture<?> f) {
                try {
                    f.get();
                }
                catch (GridException e) {
                    U.error(log, "Failed to process finish request from near node [nodeId=" + nodeId +
                        ", req=" + req + ']', e);
                }
            }
        });
    }

    /**
     * @param nodeId Sender node ID.
     * @param req Request.
     */
    @SuppressWarnings({"ConstantConditions"})
    private void processDhtTxPrepareRequest(UUID nodeId, GridDhtTxPrepareRequest<K, V> req) {
        assert nodeId != null;
        assert req != null;

        if (log.isDebugEnabled())
            log.debug("Processing dht tx prepare request [locNodeId=" + locNodeId + ", nodeId=" + nodeId + ", req=" +
                req + ']');

        GridDhtTxRemote<K, V> dhtTx = null;
        GridNearTxRemote<K, V> nearTx = null;

        GridDhtTxPrepareResponse<K, V> res;

        try {
            // Start near transaction first.
            nearTx = near.startRemoteTx(ctx.deploy().globalLoader(), nodeId, req);
            dhtTx = startRemoteTx(nodeId, req);

            res = new GridDhtTxPrepareResponse<K, V>(req.version(), req.futureId(), req.miniId());

            // Set evicted keys from near transaction.
            if (nearTx != null) {
                if (nearTx.hasEvictedBytes())
                    res.nearEvictedBytes(nearTx.evictedBytes());
                else
                    res.nearEvicted(nearTx.evicted());
            }

            if (req.concurrency() == EVENTUALLY_CONSISTENT)
                // Don't send anything back for EC.
                return;
        }
        catch (GridException e) {
            if (e instanceof GridCacheTxRollbackException)
                U.error(log, "Transaction was rolled back before prepare completed: " + dhtTx, e);
            else if (e instanceof GridCacheTxOptimisticException) {
                if (log.isDebugEnabled())
                    log.debug("Optimistic failure for remote transaction (will rollback): " + dhtTx);
            }
            else
                U.error(log, "Failed to process prepare request: " + req, e);

            if (nearTx != null)
                nearTx.rollback();

            if (dhtTx != null)
                dhtTx.rollback();

            // Don't send response.
            if (req.concurrency() == EVENTUALLY_CONSISTENT)
                return;

            res = new GridDhtTxPrepareResponse<K, V>(req.version(), req.futureId(), req.miniId(), e);
        }

        assert req.concurrency() != EVENTUALLY_CONSISTENT;

        GridNode node = ctx.discovery().node(nodeId);

        if (node != null) {
            try {
                // Reply back to sender.
                ctx.io().send(node, res);
            }
            catch (GridException e) {
                U.error(log, "Failed to send tx response to node (did the node leave grid?) [node=" +
                    node.id() + ", msg=" + res + ']', e);

                if (nearTx != null)
                    nearTx.rollback();

                if (dhtTx != null)
                    dhtTx.rollback();
            }
        }
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void processDhtTxPrepareResponse(UUID nodeId, GridDhtTxPrepareResponse<K, V> res) {
        GridDhtTxPrepareFuture<K, V> fut = (GridDhtTxPrepareFuture<K, V>)ctx.mvcc().<GridCacheTx>future(res.xid(),
            res.futureId());

        if (fut == null) {
            if (log.isDebugEnabled())
                log.debug("Received response for unknown future (will ignore): " + res);

            return;
        }

        fut.onResult(nodeId, res);
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    @SuppressWarnings({"unchecked"})
    private void processDhtTxFinishRequest(UUID nodeId, GridDhtTxFinishRequest<K, V> req) {
        assert nodeId != null;
        assert req != null;

        if (log.isDebugEnabled())
            log.debug("Processing dht tx finish request [locNodeId=" + locNodeId + ", nodeId=" + nodeId +
                ", req=" + req + ']');

        GridDhtTxRemote<K, V> dhtTx = ctx.tm().tx(req.version());
        GridCacheTxEx<K, V> nearTx = near.context().tm().tx(req.version());

        try {
            if (dhtTx == null && !F.isEmpty(req.writes()))
                dhtTx = startRemoteTx(nodeId, req);

            if (nearTx != null && !F.isEmpty(req.nearWrites()))
                nearTx = near.startRemoteTx(nodeId, req);
        }
        catch (GridException e) {
            U.error(log, "Failed to start remote DHT and Near transactions (will invalidate transactions) [dhtTx=" +
                dhtTx + ", nearTx=" + nearTx + ']', e);

            if (dhtTx != null)
                dhtTx.invalidate(true);

            if (nearTx != null)
                nearTx.invalidate(true);
        }
        catch (GridDistributedLockCancelledException ignore) {
            U.warn(log, "Received commit request to cancelled lock (will invalidate transaction) [dhtTx=" +
                dhtTx + ", nearTx=" + nearTx + ']');

            if (dhtTx != null)
                dhtTx.invalidate(true);

            if (nearTx != null)
                nearTx.invalidate(true);
        }

        // Safety - local transaction will finish explicitly.
        if (nearTx != null && nearTx.local())
            nearTx = null;

        finish(ctx, nodeId, dhtTx, req, req.writes());
        finish(near.context(), nodeId, (GridCacheTxRemoteEx<K, V>)nearTx, req, req.nearWrites());

        if (req.replyRequired()) {
            GridCacheMessage res = new GridDhtTxFinishResponse<K, V>(req.version(), req.futureId(), req.miniId());

            try {
                ctx.io().send(nodeId, res);
            }
            catch (Throwable e) {
                // Double-check.
                if (ctx.discovery().node(nodeId) == null) {
                    if (log.isDebugEnabled())
                        log.debug("Node left while sending finish response [nodeId=" + nodeId + ", res=" + res + ']');
                }
                else
                    U.error(log, "Failed to send finish response to node [nodeId=" + nodeId + ", res=" + res + ']', e);
            }
        }
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    private void processNearLockRequest(UUID nodeId, GridNearLockRequest<K, V> req) {
        assert nodeId != null;
        assert req != null;

        if (log.isDebugEnabled())
            log.debug("Processing near lock request [locNodeId=" + locNodeId + ", nodeId=" + nodeId + ", req=" + req +
                ']');

        GridNode nearNode = ctx.discovery().node(nodeId);

        if (nearNode == null) {
            U.warn(log, "Received lock request from unknown node (will ignore): " + nodeId);

            return;
        }

        GridFuture<?> f = lockAllAsync(nearNode, req, null, null);

        // Register listener just so we print out errors.
        // Exclude lock timeout exception since it's not a fatal exception.
        f.listenAsync(CU.errorLogger(log, GridCacheLockTimeoutException.class));
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    @SuppressWarnings({"RedundantTypeArguments", "ConstantConditions"})
    private void processDhtLockRequest(UUID nodeId, GridDhtLockRequest<K, V> req) {
        assert nodeId != null;
        assert req != null;
        assert !nodeId.equals(locNodeId);

        if (log.isDebugEnabled())
            log.debug("Processing dht lock request [locNodeId=" + locNodeId + ", nodeId=" + nodeId + ", req=" + req +
                ']');

        List<byte[]> keys = req.keyBytes();

        int cnt = keys.size();

        GridDhtLockResponse<K, V> res = null;

        ClassLoader ldr = ctx.deploy().globalLoader();

        GridDhtTxRemote<K, V> dhtTx = null;
        GridNearTxRemote<K, V> nearTx = null;

        boolean fail = false;
        boolean cancelled = false;

        try {
            if (ldr != null) {
                dhtTx = startRemoteTx(nodeId, req);
                nearTx = near.startRemoteTx(nodeId, req);

                res = new GridDhtLockResponse<K, V>(req.version(), req.futureId(), req.miniId(), cnt);

                if (nearTx != null) {
                    // This check allows to avoid extra serialization.
                    if (nearTx.hasEvictedBytes())
                        res.nearEvictedBytes(nearTx.evictedBytes());
                    else
                        res.nearEvicted(nearTx.evicted());
                }
                else if (!F.isEmpty(req.nearKeyBytes()))
                    res.nearEvictedBytes(req.nearKeyBytes());
            }
        }
        catch (GridException e) {
            String err = "Failed processing DHT lock request: " + req;

            log.error(err, e);

            res = new GridDhtLockResponse<K, V>(req.version(), req.futureId(), req.miniId(), new GridException(err, e));

            fail = true;
        }
        catch (GridDistributedLockCancelledException ignored) {
            // Received lock request for cancelled lock.
            if (log.isDebugEnabled())
                log.debug("Received lock request for canceled lock (will ignore): " + req);

            res = null;

            fail = true;
            cancelled = true;
        }

        GridNode node = ctx.discovery().node(nodeId);

        boolean releaseAll = false;

        if (node != null && res != null) {
            try {
                // Reply back to sender.
                ctx.io().send(node, res);
            }
            catch (GridTopologyException ignored) {
                U.warn(log, "Failed to send lock reply to remote node because it left grid: " + node.id());

                fail = true;
                releaseAll = true;
            }
            catch (GridException e) {
                U.error(log, "Failed to send lock reply to node (lock will not be acquired): " + node.id(), e);

                fail = true;
            }
        }
        // If sender left grid, release all locks acquired so far.
        else {
            fail = true;
            releaseAll = true;
        }

        if (fail) {
            if (dhtTx != null)
                dhtTx.rollback();

            if (nearTx != null) // Even though this should never happen, we leave this check for consistency.
                nearTx.rollback();

            List<byte[]> keyByteList = req.keyBytes();

            for (byte[] keyBytes : keyByteList) {
                try {
                    K key = U.<K>unmarshal(ctx.marshaller(), new ByteArrayInputStream(keyBytes), ldr);

                    while (true) {
                        GridDistributedCacheEntry<K, V> entry = peekExx(key);

                        try {
                            if (entry != null) {
                                // Release all locks because sender node left grid.
                                if (releaseAll)
                                    entry.removeExplicitNodeLocks(req.nodeId());
                                else
                                    entry.removeLock(req.version());
                            }

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Attempted to remove lock on removed entity during during failure " +
                                    "handling for dht lock request (will retry): " + entry);
                        }
                    }
                }
                catch (GridException e) {
                    U.error(log, "Failed to unmarshal at least one of the keys for lock request: " + req, e);
                }
            }

            if (releaseAll && !cancelled)
                U.warn(log, "Sender node left grid in the midst of lock acquisition (locks have been released).");
        }
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void processDhtTxFinishResponse(UUID nodeId, GridDhtTxFinishResponse<K, V> res) {
        assert nodeId != null;
        assert res != null;

        GridDhtTxFinishFuture<K, V> fut = (GridDhtTxFinishFuture<K, V>)ctx.mvcc().<GridCacheTx>future(res.xid().id(),
            res.futureId());

        if (fut == null) {
            if (log.isDebugEnabled())
                log.debug("Received response for unknown future (will ignore): " + res);

            return;
        }

        fut.onResult(nodeId, res);
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void processDhtLockResponse(UUID nodeId, GridDhtLockResponse<K, V> res) {
        assert nodeId != null;
        assert res != null;
        GridDhtLockFuture<K, V> fut = (GridDhtLockFuture<K, V>)ctx.mvcc().<Boolean>future(res.version().id(),
            res.futureId());

        if (fut == null) {
            if (log.isDebugEnabled())
                log.debug("Received response for unknown future (will ignore): " + res);

            return;
        }

        fut.onResult(nodeId, res);
    }

    /** {@inheritDoc} */
    @Override public GridCacheTxLocalAdapter<K, V> newTx(boolean implicit, GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation, long timeout, boolean invalidate, boolean syncCommit, boolean syncRollback,
        boolean swapEnabled, boolean storeEnabled) {
        assert false;
        return null;
    }

    /** {@inheritDoc} */
    @Override public GridDhtFuture<K, Boolean> lockAllAsync(@Nullable Collection<? extends K> keys, long timeout,
        GridCacheTxLocalEx<K, V> txx, boolean isInvalidate, boolean isRead, boolean retval,
        GridCacheTxIsolation isolation, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (keys == null || keys.isEmpty())
            return new GridDhtFinishedFuture<K, Boolean>(ctx.kernalContext(), true);

        GridDhtTxLocal<K, V> tx = (GridDhtTxLocal<K, V>)txx;

        assert tx != null;

        GridDhtLockFuture<K, V> fut = new GridDhtLockFuture<K, V>(ctx, tx.nearNodeId(), tx.nearXidVersion(),
            keys.size(), isRead, timeout, tx, filter);

        for (K key : keys) {
            if (key == null)
                continue;

            while (true) {
                GridDhtCacheEntry<K, V> entry = entryExx(key);

                try {
                    fut.addEntry(entry);

                    break;
                }
                catch (GridCacheEntryRemovedException ignore) {
                    if (log.isDebugEnabled())
                        log.debug("Got removed entry when adding lock (will retry): " + entry);
                }
                catch (GridDistributedLockCancelledException e) {
                    if (log.isDebugEnabled())
                        log.debug("Got lock request for cancelled lock (will ignore): " + entry);

                    return new GridDhtFinishedFuture<K, Boolean>(ctx.kernalContext(), e);
                }
            }
        }

        ctx.mvcc().addFuture(fut);

        fut.map();

        return fut;
    }

    /**
     * @param nearNode Near node.
     * @param req Request.
     * @param keys Keys.
     * @param filter Filter.
     * @return Future.
     */
    @SuppressWarnings({"ConstantConditions"})
    public GridDhtFuture<K, GridNearLockResponse<K, V>> lockAllAsync(final GridNode nearNode,
        final GridNearLockRequest<K, V> req, Collection<K> keys, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        List<byte[]> keyBytes = req.keyBytes();

        int cnt = keys == null ? keyBytes.size() : keys.size();

        GridDhtTxLocal<K, V> tx = null;

        if (req.inTx()) {
            GridCacheVersion dhtVer = ctx.tm().mappedVersion(req.version());

            if (dhtVer != null)
                tx = ctx.tm().tx(dhtVer);
        }

        final List<GridCacheEntryEx<K, V>> entries = new ArrayList<GridCacheEntryEx<K, V>>(cnt);

        try {
            ClassLoader ldr = ctx.deploy().globalLoader();

            if (ldr != null) {
                // Unmarshal filter first.
                if (filter == null)
                    filter = req.filter();

                GridDhtLockFuture<K, V> fut = null;

                if (!req.inTx()) {
                    fut = new GridDhtLockFuture<K, V>(ctx, nearNode.id(), req.version(), cnt, req.txRead(),
                        req.timeout(), tx, filter);

                    // Add before mapping.
                    if (!ctx.mvcc().addFuture(fut))
                        throw new IllegalStateException("Duplicate future ID: " + fut);
                }

                // Unmarshal keys.
                if (keys == null) {
                    for (int i = 0; i < keyBytes.size(); i++) {
                        byte[] bytes = keyBytes.get(i);

                        if (bytes == null)
                            continue;

                        K key = req.keys().get(i);

                        if (log.isDebugEnabled())
                            log.debug("Unmarshalled key: " + key);

                        while (true) {
                            GridDhtCacheEntry<K, V> entry = entryExx(key);

                            try {
                                assert bytes != null;

                                // Stick key bytes into entry to avoid extra serialization.
                                entry.keyBytes(bytes);

                                if (fut != null)
                                    fut.addEntry(key == null ? null : entry);

                                entries.add(entry);

                                break;
                            }
                            catch (GridCacheEntryRemovedException ignore) {
                                if (log.isDebugEnabled())
                                    log.debug("Got removed entry when adding lock (will retry): " + entry);
                            }
                            catch (GridDistributedLockCancelledException e) {
                                if (log.isDebugEnabled())
                                    log.debug("Got lock request for cancelled lock (will ignore): " + entry);

                                fut.onError(e);

                                return new GridDhtFinishedFuture<K, GridNearLockResponse<K, V>>(ctx.kernalContext(), e);
                            }
                        }
                    }
                }
                else {
                    for (K key : keys) {
                        while (true) {
                            GridDhtCacheEntry<K, V> entry = entryExx(key);

                            try {
                                if (fut != null)
                                    fut.addEntry(key == null ? null : entry);

                                entries.add(entry);

                                break;
                            }
                            catch (GridCacheEntryRemovedException ignore) {
                                if (log.isDebugEnabled())
                                    log.debug("Got removed entry when adding lock (will retry): " + entry);
                            }
                            catch (GridDistributedLockCancelledException e) {
                                if (log.isDebugEnabled())
                                    log.debug("Got lock request for cancelled lock (will ignore): " + entry);

                                fut.onError(e);

                                return new GridDhtFinishedFuture<K, GridNearLockResponse<K, V>>(ctx.kernalContext(), e);
                            }
                        }
                    }
                }

                // Handle implicit locks for pessimistic transactions.
                if (req.inTx()) {
                    if (tx == null) {
                        tx = new GridDhtTxLocal<K, V>(
                            nearNode.id(),
                            req.version(),
                            req.futureId(),
                            req.miniId(),
                            req.threadId(),
                            false,
                            ctx,
                            PESSIMISTIC,
                            req.isolation(),
                            req.timeout(),
                            req.isInvalidate(),
                            req.syncCommit(),
                            req.syncRollback(),
                            false);

                        tx = ctx.tm().onCreated(tx);

                        if (tx == null || !tx.init()) {
                            String msg = "Failed to acquire lock (transaction has been completed): " +
                                req.version();

                            U.warn(log, msg);

                            if (tx != null)
                                tx.rollback();

                            return new GridDhtFinishedFuture<K, GridNearLockResponse<K, V>>(ctx.kernalContext(),
                                new GridException(msg));
                        }
                    }

                    ctx.tm().txContext(tx);

                    if (log.isDebugEnabled())
                        log.debug("Performing DHT lock [tx=" + tx + ", entries=" +
                            entries + ']');

                    GridFuture<GridCacheReturn<V>> txFut = tx.lockAllAsync(
                        F.viewReadOnly(entries, CU.<K, V>entry2Key(), F.notNull()),
                        req.messageId(),
                        req.implicitTx(),
                        req.txRead());

                    final GridDhtTxLocal<K, V> t = tx;

                    return new GridDhtEmbeddedFuture<K, GridNearLockResponse<K, V>, GridCacheReturn<V>>(
                        ctx.kernalContext(),
                        txFut,
                        new C2<GridCacheReturn<V>, Exception, GridNearLockResponse<K, V>>() {
                            @Override public GridNearLockResponse<K, V> apply(GridCacheReturn<V> ret, Exception e) {
                                if (e != null)
                                    e = U.unwrap(e);

                                return closureLockReply(nearNode, entries, req, t, t.xidVersion(), e);
                            }
                        });
                }
                else {
                    assert fut != null;

                    // This will send remote messages.
                    fut.map();

                    final GridCacheVersion mappedVer = fut.version();

                    return new GridDhtEmbeddedFuture<K, GridNearLockResponse<K, V>, Boolean>(
                        ctx.kernalContext(),
                        fut,
                        new C2<Boolean, Exception, GridNearLockResponse<K, V>>() {
                            @Override public GridNearLockResponse<K, V> apply(Boolean b, Exception e) {
                                if (e != null)
                                    e = U.unwrap(e);
                                else if (!b)
                                    e = new GridCacheLockTimeoutException(req.version());

                                return closureLockReply(nearNode, entries, req, null, mappedVer, e);
                            }
                        });
                }
            }
            else {
                String err = "Failed to acquire deployment class for message: " + req;

                U.warn(log, err);

                return new GridDhtFinishedFuture<K, GridNearLockResponse<K, V>>(ctx.kernalContext(),
                    new GridException(err));
            }
        }
        catch (GridException e) {
            String err = "Failed to unmarshal at least one of the keys for lock request message: " + req;

            log.error(err, e);

            if (tx != null) {
                try {
                    tx.rollback();
                }
                catch (GridException ex) {
                    U.error(log, "Failed to rollback the transaction: " + tx, ex);
                }
            }

            return new GridDhtFinishedFuture<K, GridNearLockResponse<K, V>>(ctx.kernalContext(),
                new GridException(err, e));
        }
    }

    /**
     * @param nearNode Near node.
     * @param entries Entries.
     * @param req Lock request.
     * @param tx Transaction.
     * @param mappedVer Mapped version.
     * @param err Error.
     * @return Response.
     */
    private GridNearLockResponse<K, V> closureLockReply(GridNode nearNode, List<GridCacheEntryEx<K, V>> entries,
        GridNearLockRequest<K, V> req, GridDhtTxLocal<K, V> tx, GridCacheVersion mappedVer, Throwable err) {
        assert mappedVer != null;
        assert tx == null || tx.xidVersion().equals(mappedVer);

        try {
            // Send reply back to originating near node.
            GridNearLockResponse<K, V> res = new GridNearLockResponse<K, V>(
                req.version(), req.futureId(), req.miniId(), entries.size(), err);

            res.pending(ctx.mvcc().localPendingVersions(req.keys(), req.version()));

            // We have to add completed versions for cases when nearLocal and remote transactions
            // execute concurrently.
            res.completedVersions(ctx.tm().committedVersions(req.version()), ctx.tm().rolledbackVersions(req.version()));

            int i = 0;

            for (ListIterator<GridCacheEntryEx<K, V>> it = entries.listIterator(); it.hasNext();) {
                GridCacheEntryEx<K, V> e = it.next();

                GridCacheVersion dhtVer = req.dhtVersion(i);

                while (true) {
                    try {
                        GridCacheVersion ver = e.version();

                        boolean ret = req.returnValue(i) || dhtVer == null || !dhtVer.equals(ver);

                        if (ret)
                            // Ignore transaction for DHT reads.
                            e.innerGet(/*tx*/null, true/*swap*/, true/*read-through*/, /*fail-fast.*/false,
                                /*update-metrics*/false, /*event notification*/req.returnValue(i), CU.<K, V>empty());

                        assert e.candidate(mappedVer).owner() :
                            "Entry does not own lock for tx [entry=" + e + ", tx=" + tx + ", req=" + req + ']';

                        // We include values into response since they are required for local
                        // calls and won't be serialized. We are also including DHT version.
                        res.addValueBytes(
                            e.peek(GLOBAL, CU.<K, V>empty()),
                            ret ? e.valueBytes(null) : null,
                            mappedVer,
                            ctx);

                        break;
                    }
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry when sending reply to DHT lock request (will retry): " + e);

                        e = entryExx(e.key());

                        it.set(e);
                    }
                    catch (GridCacheFilterFailedException ex) {
                        assert false : "Filter should never fail if fail-fast is false.";

                        ex.printStackTrace();

                        break;
                    }
                }

                i++;
            }

            // Don't send reply message to this node.
            if (!nearNode.id().equals(ctx.nodeId()))
                ctx.io().send(nearNode, res);

            // Throw error after sending reply.
            if (err != null)
                throw new GridClosureException(err);

            return res;
        }
        catch (GridException e) {
            U.error(log, "Failed to reply to lock request from node (will rollback transaction): " +
                U.toShortString(nearNode), e);

            if (tx != null)
                tx.rollbackAsync();

            // Convert to closure exception as this method is only called form closures.
            throw new GridClosureException(e);
        }
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    private void processDhtUnlockRequest(UUID nodeId, GridDhtUnlockRequest<K, V> req) {
        clearLocks(nodeId, req);

        near.clearLocks(nodeId, req);
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    @SuppressWarnings({"RedundantTypeArguments"})
    private void clearLocks(UUID nodeId, GridDistributedUnlockRequest<K, V> req) {
        assert nodeId != null;

        try {
            ClassLoader ldr = ctx.deploy().globalLoader();

            List<byte[]> keys = req.keyBytes();

            if (keys != null) {
                for (byte[] keyBytes : keys) {
                    K key = U.<K>unmarshal(ctx.marshaller(), new ByteArrayInputStream(keyBytes), ldr);

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
        catch (GridException e) {
            U.error(log, "Failed to unmarshal unlock key (unlock will not be performed): " + req, e);
        }
    }

    /**
     * @param nodeId Sender ID.
     * @param req Request.
     */
    @SuppressWarnings({"RedundantTypeArguments", "TypeMayBeWeakened"})
    private void processNearUnlockRequest(UUID nodeId, GridNearUnlockRequest<K, V> req) {
        assert nodeId != null;

        try {
            ClassLoader ldr = ctx.deploy().globalLoader();

            List<byte[]> keyBytes = req.keyBytes();

            Collection<K> keys = new ArrayList<K>(keyBytes.size());

            for (byte[] bytes : keyBytes) {
                K key = U.<K>unmarshal(ctx.marshaller(), new ByteArrayInputStream(bytes), ldr);

                keys.add(key);
            }

            removeLocks(nodeId, req.version(), keys);
        }
        catch (GridException e) {
            U.error(log, "Failed to unmarshal unlock key (unlock will not be performed): " + req, e);
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param cached Entry.
     * @param dhtMap DHT map.
     * @param nearMap Near map.
     * @throws GridException If failed.
     */
    private void map(UUID nodeId, GridDhtCacheEntry<K, V> cached, Map<GridNode, List<T2<K, byte[]>>> dhtMap,
        Map<GridNode, List<T2<K, byte[]>>> nearMap) throws GridException {
        Collection<GridNode> dhtNodes = ctx.dht().topology().nodes(cached.partition());

        GridNode primary = CU.primary(dhtNodes);

        if (!primary.id().equals(ctx.nodeId())) {
            if (log.isDebugEnabled())
                log.debug("Primary node mismatch for unlock [entry=" + cached + ", expected=" + ctx.nodeId() +
                    ", actual=" + U.toShortString(primary) + ']');

            return;
        }

        if (log.isDebugEnabled())
            log.debug("Mapping entry to DHT nodes [nodes=" + U.toShortString(dhtNodes) + ", entry=" + cached + ']');

        Collection<UUID> readers = cached.readers();

        Collection<GridNode> nearNodes = null;

        if (!F.isEmpty(readers)) {
            nearNodes = ctx.discovery().nodes(readers, F.<UUID>not(F.idForNodeId(nodeId)));

            if (log.isDebugEnabled())
                log.debug("Mapping entry to near nodes [nodes=" + U.toShortString(nearNodes) + ", entry=" + cached + ']');
        }
        else {
            if (log.isDebugEnabled())
                log.debug("Entry has no near readers: " + cached);
        }

        map(cached, F.view(dhtNodes, F.remoteNodes(ctx.nodeId())), dhtMap); // Exclude local node.
        map(cached, nearNodes, nearMap);
    }

    /**
     * @param entry Entry.
     * @param nodes Nodes.
     * @param map Map.
     * @throws GridException If failed.
     */
    private void map(GridCacheEntryEx<K, V> entry, Iterable<? extends GridNode> nodes,
        Map<GridNode, List<T2<K, byte[]>>> map) throws GridException {
        if (nodes != null) {
            for (GridNode n : nodes) {
                List<T2<K, byte[]>> keys = map.get(n);

                if (keys == null)
                    map.put(n, keys = new LinkedList<T2<K, byte[]>>());

                keys.add(new T2<K, byte[]>(entry.key(), entry.getOrMarshalKeyBytes()));
            }
        }
    }

    /**
     * @param nodeId Node ID.
     * @param ver Version.
     * @param keys Keys.
     */
    public void removeLocks(UUID nodeId, GridCacheVersion ver, Iterable<? extends K> keys) {
        if (F.isEmpty(keys))
            return;

        // Remove mapped versions.
        GridCacheVersion dhtVer = ctx.mvcc().unmapVersion(ver);

        if (dhtVer == null) {
            U.warn(log, "Attempting to remove locks for unknown DHT version (will ignore) [nodeId=" + nodeId +
                ", ver=" + ver + ", keys=" + keys + ']');

            return;
        }

        Map<GridNode, List<T2<K, byte[]>>> dhtMap = new HashMap<GridNode, List<T2<K, byte[]>>>();
        Map<GridNode, List<T2<K, byte[]>>> nearMap = new HashMap<GridNode, List<T2<K, byte[]>>>();

        for (K key : keys) {
            while (true) {
                GridDhtCacheEntry<K, V> entry = peekExx(key);

                try {
                    if (entry != null) {
                        // Note that we don't reorder completed versions here,
                        // as there is no point to reorder relative to the version
                        // we are about to remove.
                        if (entry.removeLock(dhtVer)) {
                            // Map to backups and near readers.
                            map(nodeId, entry, dhtMap, nearMap);

                            if (log.isDebugEnabled())
                                log.debug("Removed lock [lockId=" + ver + ", key=" + key + ']');
                        }
                        else if (log.isDebugEnabled())
                            log.debug("Received unlock request for unknown candidate " +
                                "(added to cancelled locks set) [ver=" + ver + ", entry=" + entry + ']');
                    }
                    else if (log.isDebugEnabled())
                        log.debug("Received unlock request for entry that could not be found: " + key);

                    break;
                }
                catch (GridCacheEntryRemovedException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Received remove lock request for removed entry (will retry): " + entry);
                }
                catch (GridException e) {
                    U.error(log, "Failed to remove locks for keys: " + keys, e);
                }
            }
        }

        Collection<GridCacheVersion> committed = ctx.tm().committedVersions(ver);
        Collection<GridCacheVersion> rolledback = ctx.tm().rolledbackVersions(ver);

        // Backups.
        for (Map.Entry<GridNode, List<T2<K, byte[]>>> entry : dhtMap.entrySet()) {
            GridNode n = entry.getKey();

            List<T2<K, byte[]>> keyBytes = entry.getValue();

            GridDhtUnlockRequest<K, V> req = new GridDhtUnlockRequest<K, V>(keyBytes.size());

            req.version(dhtVer);

            try {
                for (T2<K, byte[]> key : keyBytes)
                    req.addKey(key.get1(), key.get2(), ctx);

                keyBytes = nearMap.get(n);

                if (keyBytes != null)
                    for (T2<K, byte[]> key : keyBytes)
                        req.addNearKey(key.get1(), key.get2(), ctx);

                req.completedVersions(committed, rolledback);

                ctx.io().send(n, req);
            }
            catch (GridTopologyException ignore) {
                if (log.isDebugEnabled())
                    log.debug("Node left while sending unlock request: " + n);
            }
            catch (GridException e) {
                U.error(log, "Failed to send unlock request to node (will make best effort to complete): " + n, e);
            }
        }

        // Readers.
        for (Map.Entry<GridNode, List<T2<K, byte[]>>> entry : nearMap.entrySet()) {
            GridNode n = entry.getKey();

            if (!dhtMap.containsKey(n)) {
                List<T2<K, byte[]>> keyBytes = entry.getValue();

                GridDhtUnlockRequest<K, V> req = new GridDhtUnlockRequest<K, V>(keyBytes.size());

                try {
                    for (T2<K, byte[]> key : keyBytes)
                        req.addNearKey(key.get1(), key.get2(), ctx);

                    req.completedVersions(committed, rolledback);

                    ctx.io().send(n, req);
                }
                catch (GridTopologyException ignore) {
                    if (log.isDebugEnabled())
                        log.debug("Node left while sending unlock request: " + n);
                }
                catch (GridException e) {
                    U.error(log, "Failed to send unlock request to node (will make best effort to complete): " + n, e);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void unlockAll(Collection<? extends K> keys,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        assert false;
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, Collection<K>> mapKeysToNodes(Collection<? extends K> keys) {
        return CU.mapKeysToNodes(ctx, keys);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtCache.class, this);
    }
}
