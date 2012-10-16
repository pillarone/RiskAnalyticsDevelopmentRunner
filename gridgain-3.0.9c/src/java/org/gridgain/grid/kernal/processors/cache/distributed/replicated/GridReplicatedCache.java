// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.replicated;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.cache.GridCachePreloadMode.*;
import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * Fully replicated cache implementation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridReplicatedCache<K, V> extends GridDistributedCacheAdapter<K, V> {
    /** Preloader. */
    private GridCachePreloader<K, V> preldr;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridReplicatedCache() {
        // No-op.
    }

    /**
     * @param ctx Cache registry.
     */
    public GridReplicatedCache(GridCacheContext<K, V> ctx) {
        super(ctx, ctx.config().getStartSize());
    }

    /** {@inheritDoc} */
    @Override public GridCacheTxLocalAdapter<K, V> newTx(
        boolean implicit,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        long timeout,
        boolean invalidate,
        boolean syncCommit,
        boolean syncRollback,
        boolean swapEnabled,
        boolean storeEnabled) {
        return new GridReplicatedTxLocal<K, V>(ctx, implicit, concurrency, isolation, timeout,
            invalidate, syncCommit, syncRollback, swapEnabled, storeEnabled);
    }

    /** {@inheritDoc} */
    @Override protected void init() {
        map.setEntryFactory(new GridCacheMapEntryFactory<K, V>() {
            /** {@inheritDoc} */
            @Override public GridCacheMapEntry<K, V> create(GridCacheContext<K, V> reg, K key, int hash, V val,
                GridCacheMapEntry<K, V> next, long ttl) {
                return new GridReplicatedCacheEntry<K, V>(reg, key, hash, val, next, ttl);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        super.start();

        GridCacheIoManager<K, V> io = ctx.io();

        io.addHandler(GridDistributedLockRequest.class, new CI2<UUID, GridDistributedLockRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridDistributedLockRequest<K, V> req) {
                processLockRequest(nodeId, req);
            }
        });

        io.addHandler(GridDistributedLockResponse.class, new CI2<UUID, GridDistributedLockResponse<K, V>>() {
            @Override public void apply(UUID nodeId, GridDistributedLockResponse<K, V> res) {
                processLockResponse(nodeId, res);
            }
        });

        io.addHandler(GridDistributedTxFinishRequest.class, new CI2<UUID, GridDistributedTxFinishRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridDistributedTxFinishRequest<K, V> req) {
                processFinishRequest(nodeId, req);
            }
        });

        io.addHandler(GridDistributedTxFinishResponse.class, new CI2<UUID, GridDistributedTxFinishResponse>() {
            @Override public void apply(UUID nodeId, GridDistributedTxFinishResponse res) {
                processFinishResponse(nodeId, res);
            }
        });

        io.addHandler(GridDistributedTxPrepareRequest.class, new CI2<UUID, GridDistributedTxPrepareRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridDistributedTxPrepareRequest<K, V> req) {
                processPrepareRequest(nodeId, req);
            }
        });

        io.addHandler(GridDistributedTxPrepareResponse.class, new CI2<UUID, GridDistributedTxPrepareResponse<K, V>>() {
            @Override public void apply(UUID nodeId, GridDistributedTxPrepareResponse<K, V> res) {
                processPrepareResponse(nodeId, res);
            }
        });

        io.addHandler(GridDistributedUnlockRequest.class, new CI2<UUID, GridDistributedUnlockRequest<K, V>>() {
            @Override public void apply(UUID nodeId, GridDistributedUnlockRequest<K, V> req) {
                processUnlockRequest(nodeId, req);
            }
        });

        preldr = cacheCfg.getPreloadMode() != NONE ? new GridReplicatedPreloader<K, V>(ctx) :
            new GridCachePreloaderAdapter<K, V>(ctx);

        preldr.start();
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

        if (preldr != null)
            preldr.onKernalStart();
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop() {
        super.onKernalStop();
    }

    /** {@inheritDoc} */
    @Override protected GridCachePreloader<K, V> preloader() {
        return preldr;
    }

    /**
     * Processes lock request.
     *
     * @param nodeId Sender node ID.
     * @param msg Lock request.
     */
    @SuppressWarnings({"unchecked", "ThrowableInstanceNeverThrown"})
    private void processLockRequest(UUID nodeId, GridDistributedLockRequest<K, V> msg) {
        assert !nodeId.equals(locNodeId);

        List<byte[]> keys = msg.keyBytes();

        int cnt = keys.size();

        GridReplicatedTxRemote<K, V> tx = null;

        GridDistributedLockResponse res;

        ClassLoader ldr = null;

        try {
            ldr = ctx.deploy().globalLoader();

            if (ldr != null) {
                res = new GridDistributedLockResponse(msg.version(), msg.futureId(), cnt);

                for (int i = 0; i < keys.size(); i++) {
                    byte[] bytes = keys.get(i);
                    K key = msg.keys().get(i);

                    Collection<GridCacheMvccCandidate<K>> cands = msg.candidatesByIndex(i);

                    if (bytes == null)
                        continue;

                    if (log.isDebugEnabled())
                        log.debug("Unmarshalled key: " + key);

                    GridDistributedCacheEntry<K, V> entry = null;

                    while (true) {
                        try {
                            entry = entryexx(key);

                            // Handle implicit locks for pessimistic transactions.
                            if (msg.inTx()) {
                                tx = ctx.tm().tx(msg.version());

                                if (tx != null) {
                                    if (msg.txRead())
                                        tx.addRead(key, bytes);
                                    else
                                        tx.addWrite(key, bytes);
                                }
                                else {
                                    tx = new GridReplicatedTxRemote<K, V>(
                                        nodeId,
                                        msg.threadId(),
                                        msg.version(),
                                        null,
                                        PESSIMISTIC,
                                        msg.isolation(),
                                        msg.isInvalidate(),
                                        msg.timeout(),
                                        key,
                                        bytes,
                                        msg.txRead(),
                                        ctx);

                                    tx = ctx.tm().onCreated(tx);

                                    if (tx == null || !ctx.tm().onStarted(tx))
                                        throw new GridCacheTxRollbackException("Failed to acquire lock " +
                                            "(transaction has been completed): " + msg.version());
                                }
                            }

                            // Add remote candidate before reordering.
                            entry.addRemote(msg.nodeId(), null, msg.threadId(), msg.version(), msg.timeout(),
                                tx != null && tx.ec(), tx != null);

                            // Remote candidates for ordered lock queuing.
                            entry.addRemoteCandidates(
                                cands,
                                msg.version(),
                                msg.committedVersions(),
                                msg.rolledbackVersions());

                            // Double-check in case if sender node left the grid.
                            if (ctx.discovery().node(msg.nodeId()) == null) {
                                if (log.isDebugEnabled())
                                    log.debug("Node requesting lock left grid (lock request will be ignored): " + msg);

                                if (tx != null)
                                    tx.rollback();

                                return;
                            }

                            res.setCandidates(
                                i,
                                entry.localCandidates(),
                                ctx.tm().committedVersions(msg.version()),
                                ctx.tm().rolledbackVersions(msg.version()));

                            res.addValueBytes(entry.rawGet(), msg.returnValue(i) ? entry.valueBytes(null) : null, ctx);

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
                String err = "Failed to acquire deployment class for message: " + msg;

                U.warn(log, err);

                res = new GridDistributedLockResponse(msg.version(), msg.futureId(), new GridException(err));
            }
        }
        catch (GridException e) {
            String err = "Failed to unmarshal at least one of the keys for lock request message: " + msg;

            log.error(err, e);

            res = new GridDistributedLockResponse(msg.version(), msg.futureId(), new GridException(err, e));

            if (tx != null)
                tx.rollback();
        }
        catch (GridDistributedLockCancelledException ignored) {
            // Received lock request for cancelled lock.
            if (log.isDebugEnabled())
                log.debug("Received lock request for canceled lock (will ignore): " + msg);

            if (tx != null)
                tx.rollback();

            // Don't send response back.
            return;
        }

        GridNode node = ctx.discovery().node(msg.nodeId());

        boolean releaseAll = false;

        if (node != null) {
            try {
                // Reply back to sender.
                ctx.io().send(node, res);
            }
            catch (GridException e) {
                U.error(log, "Failed to send message to node (did the node leave grid?): " + node.id(), e);

                releaseAll = ldr != null;
            }
        }
        // If sender left grid, release all locks acquired so far.
        else
            releaseAll = ldr != null;

        // Release all locks because sender node left grid.
        if (releaseAll) {
            for (K key : msg.keys()) {
                while (true) {
                    GridDistributedCacheEntry<K, V> entry = peekexx(key);

                    try {
                        if (entry != null)
                            entry.removeExplicitNodeLocks(msg.nodeId());

                        break;
                    }
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled())
                            log.debug("Attempted to remove lock on removed entity during failure " +
                                "of replicated lock request handling (will retry): " + entry);
                    }
                }
            }

            U.warn(log, "Sender node left grid in the midst of lock acquisition (locks will be released).");
        }
    }

    /**
     * @param lockId Lock ID.
     * @param futId Future ID.
     * @return Lock future.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable private GridReplicatedLockFuture<K, V> futurex(UUID lockId, GridUuid futId) {
        return (GridReplicatedLockFuture)ctx.mvcc().future(lockId, futId);
    }

    /**
     * Processes lock response.
     *
     * @param nodeId Sender node ID.
     * @param res Lock response.
     */
    private void processLockResponse(UUID nodeId, GridDistributedLockResponse<K, V> res) {
        GridReplicatedLockFuture<K, V> fut = futurex(res.lockId(), res.futureId());

        if (fut == null) {
            U.warn(log, "Received lock response for non-existing future (will ignore): " + res);
        }
        else {
            fut.onResult(nodeId, res);

            if (fut.isDone()) {
                ctx.mvcc().removeFuture(fut);

                if (log.isDebugEnabled())
                    log.debug("Received all replies for future (future was removed): " + fut);
            }
        }
    }

    /**
     * Processes unlock request.
     *
     * @param nodeId Sender node ID.
     * @param req Unlock request.
     */
    @SuppressWarnings({"unchecked"})
    private void processUnlockRequest(UUID nodeId, GridDistributedUnlockRequest req) {
        assert nodeId != null;

        try {
            ClassLoader ldr = ctx.deploy().globalLoader();
            List<byte[]> keys = req.keyBytes();

            for (byte[] keyBytes : keys) {
                K key = (K)U.unmarshal(ctx.marshaller(), new ByteArrayInputStream(keyBytes), ldr);

                while (true) {
                    GridDistributedCacheEntry<K, V> entry = peekexx(key);

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
        catch (GridException e) {
            U.error(log, "Failed to unmarshal unlock key (unlock will not be performed): " + req, e);
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param msg Prepare request.
     */
    @SuppressWarnings({"InstanceofCatchParameter"})
    private void processPrepareRequest(UUID nodeId, GridDistributedTxPrepareRequest<K, V> msg) {
        assert nodeId != null;
        assert msg != null;

        GridReplicatedTxRemote<K, V> tx = null;

        GridDistributedTxPrepareResponse<K, V> res;

        try {
            tx = new GridReplicatedTxRemote<K, V>(
                ctx.deploy().globalLoader(),
                nodeId,
                msg.threadId(),
                msg.version(),
                msg.commitVersion(),
                msg.concurrency(),
                msg.isolation(),
                msg.isInvalidate(),
                msg.timeout(),
                msg.reads(),
                msg.writes(),
                ctx);

            tx = ctx.tm().onCreated(tx);

            if (tx == null || !ctx.tm().onStarted(tx))
                throw new GridCacheTxRollbackException("Attempt to start a completed transaction: " + tx);

            // Prepare prior to reordering, so the pending locks added
            // in prepare phase will get properly ordered as well.
            tx.prepare();

            // Add remote candidates and reorder completed and uncompleted versions.
            tx.addRemoteCandidates(msg.candidatesByKey(), msg.committedVersions(), msg.rolledbackVersions());

            if (msg.concurrency() == EVENTUALLY_CONSISTENT) {
                if (log.isDebugEnabled())
                    log.debug("Committing transaction during remote prepare: " + tx);

                tx.commit();

                if (log.isDebugEnabled())
                    log.debug("Committed transaction during remote prepare: " + tx);

                // Don't send response.
                return;
            }

            res = new GridDistributedTxPrepareResponse<K, V>(msg.version());

            Map<K, Collection<GridCacheMvccCandidate<K>>> cands = tx.localCandidates();

            // Add local candidates (completed version must be set below).
            res.candidates(cands);
        }
        catch (GridException e) {
            if (e instanceof GridCacheTxRollbackException) {
                if (log.isDebugEnabled())
                    log.debug("Transaction was rolled back before prepare completed: " + tx);
            }
            else if (e instanceof GridCacheTxOptimisticException) {
                if (log.isDebugEnabled())
                    log.debug("Optimistic failure for remote transaction (will rollback): " + tx);
            }
            else {
                U.error(log, "Failed to process prepare request: " + msg, e);
            }

            if (tx != null)
                // Automatically rollback remote transactions.
                tx.rollback();

            // Don't send response.
            if (msg.concurrency() == EVENTUALLY_CONSISTENT)
                return;

            res = new GridDistributedTxPrepareResponse<K, V>(msg.version());

            res.error(e);
        }

        // Add completed versions.
        res.completedVersions(
            ctx.tm().committedVersions(msg.version()),
            ctx.tm().rolledbackVersions(msg.version()));

        assert msg.concurrency() != EVENTUALLY_CONSISTENT;

        GridNode node = ctx.discovery().node(nodeId);

        if (node != null) {
            try {
                // Reply back to sender.
                ctx.io().send(node, res);
            }
            catch (GridException e) {
                U.error(log, "Failed to send tx response to node (did the node leave grid?) [node=" + node.id()
                    + ", msg=" + res + ']', e);

                if (tx != null)
                    tx.rollback();
            }
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param msg Response to prepare request.
     */
    private void processPrepareResponse(UUID nodeId, GridDistributedTxPrepareResponse<K, V> msg) {
        assert nodeId != null;
        assert msg != null;

        GridReplicatedTxLocal<K, V> tx = ctx.tm().tx(msg.version());

        if (tx == null) {
            if (log.isDebugEnabled())
                log.debug("Received prepare response for non-existing transaction [senderNodeId=" + nodeId +
                    ", res=" + msg + ']');

            return;
        }

        GridReplicatedTxPrepareFuture<K, V> future = (GridReplicatedTxPrepareFuture<K, V>)tx.future();

        if (future != null)
            future.onResult(nodeId, msg);
        else
            U.error(log, "Received prepare response for transaction with no future [res=" + msg + ", tx=" + tx + ']');
    }

    /**
     * @param nodeId Sender node ID.
     * @param req Finish transaction message.
     */
    @SuppressWarnings({"CatchGenericClass"})
    private void processFinishRequest(UUID nodeId, GridDistributedTxFinishRequest<K, V> req) {
        assert nodeId != null;
        assert req != null;

        GridReplicatedTxRemote<K, V> tx = ctx.tm().tx(req.version());

        try {
            ClassLoader ldr = ctx.deploy().globalLoader();

            if (req.commit()) {
                // If lock was acquired explicitly.
                if (tx == null) {
                    // Create transaction and add entries.
                    tx = ctx.tm().onCreated(
                        new GridReplicatedTxRemote<K, V>(
                            ldr,
                            nodeId,
                            req.threadId(),
                            req.version(),
                            req.commitVersion(),
                            PESSIMISTIC,
                            READ_COMMITTED,
                            req.isInvalidate(),
                            /*timeout */0,
                            /*read entries*/null,
                            req.writes(),
                            ctx));

                    if (tx == null)
                        throw new GridCacheTxRollbackException("Attempt to start a completed " +
                            "transaction: " + req);
                }
                else {
                    boolean set = tx.commitVersion(req.commitVersion());

                    assert set;
                }

                Collection<GridCacheTxEntry<K, V>> writeEntries = req.writes();

                if (!F.isEmpty(writeEntries)) {
                    // In OPTIMISTIC mode, we get the values at PREPARE stage.
                    assert tx.concurrency() == PESSIMISTIC;

                    for (GridCacheTxEntry<K, V> entry : writeEntries) {
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
            else if (tx != null) {
                tx.doneRemote(req.baseVersion(), req.committedVersions(), req.rolledbackVersions());

                tx.rollback();
            }

            if (req.replyRequired()) {
                GridCacheMessage<K, V> res = new GridDistributedTxFinishResponse<K, V>(req.version(), req.futureId());

                try {
                    ctx.io().send(nodeId, res);
                }
                catch (Throwable e) {
                    // Double-check.
                    if (ctx.discovery().node(nodeId) == null) {
                        if (log.isDebugEnabled())
                            log.debug("Node left while sending finish response [nodeId=" + nodeId + ", res=" + res +
                                ']');
                    }
                    else
                        U.error(log, "Failed to send finish response to node [nodeId=" + nodeId + ", res=" + res + ']', e);
                }
            }
        }
        catch (Throwable e) {
            U.error(log, "Failed completing transaction [commit=" + req.commit() + ", tx=" + CU.txString(tx) + ']', e);

            if (tx != null)
                tx.rollback();
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param msg Finish transaction response.
     */
    private void processFinishResponse(UUID nodeId, GridDistributedTxFinishResponse msg) {
        GridReplicatedTxCommitFuture<K, V> fut =
            (GridReplicatedTxCommitFuture<K, V>)ctx.mvcc().<GridCacheTx>future(msg.xid().id(), msg.futureId());

        if (fut != null)
            fut.onResult(nodeId);
        else
            U.warn(log, "Received finish response for unknown transaction: " + msg);
    }

    /**
     * @param key Cache key.
     * @return Replicated cache entry.
     */
    @Nullable GridDistributedCacheEntry<K, V> peekexx(K key) {
        return (GridDistributedCacheEntry<K, V>)peekEx(key);
    }

    /**
     * @param key Cache key.
     * @return Replicated cache entry.
     */
    GridDistributedCacheEntry<K, V> entryexx(K key) {
        return (GridDistributedCacheEntry<K, V>)entryEx(key);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked", "ThrowableInstanceNeverThrown"})
    @Override protected GridFuture<Boolean> lockAllAsync(Collection<? extends K> keys, long timeout,
        GridCacheTxLocalEx<K, V> tx, boolean isInvalidate, boolean isRead, boolean retval,
        GridCacheTxIsolation isolation, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (keys.isEmpty())
            return new GridFinishedFuture<Boolean>(ctx.kernalContext(), true);

        Collection<GridRichNode> nodes = ctx.remoteNodes(keys);

        final GridReplicatedLockFuture<K, V> fut = new GridReplicatedLockFuture<K, V>(ctx, keys, tx, this, nodes, timeout,
            filter);

        GridDistributedLockRequest<K, V> req = new GridDistributedLockRequest<K, V>(
            locNodeId,
            Thread.currentThread().getId(),
            fut.futureId(),
            fut.version(),
            tx != null,
            isRead,
            isolation,
            isInvalidate,
            timeout,
            keys.size()
        );

        try {
            // Must add future before redying locks.
            if (!ctx.mvcc().addFuture(fut))
                throw new IllegalStateException("Duplicate future ID: " + fut);

            boolean distribute = false;

            for (K key : keys) {
                while (true) {
                    GridDistributedCacheEntry<K, V> entry = null;

                    try {
                        entry = entryexx(key);

                        if (!ctx.isAll(entry.wrap(false), filter)) {
                            if (log.isDebugEnabled())
                                log.debug("Entry being locked did not pass filter (will not lock): " + entry);

                            fut.onDone(false);

                            return fut;
                        }

                        // Removed exception may be thrown here.
                        GridCacheMvccCandidate<K> cand = fut.addEntry(entry);

                        if (cand != null) {
                            req.addKeyBytes(
                                key,
                                cand.reentry() ? null : entry.getOrMarshalKeyBytes(),
                                retval,
                                entry.localCandidates(fut.version()),
                                ctx);

                            req.completedVersions(
                                ctx.tm().committedVersions(fut.version()),
                                ctx.tm().rolledbackVersions(fut.version()));

                            distribute = !cand.reentry();
                        }
                        else if (fut.isDone())
                            return fut;

                        break;
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry in lockAsync(..) method (will retry): " + entry);
                    }
                }
            }

            // If nothing to distribute at this point,
            // then all locks are reentries.
            if (!distribute)
                fut.complete(true);

            if (nodes.isEmpty())
                fut.readyLocks();

            // No reason to send request if all locks are locally re-entered,
            // or if timeout is negative and local locks could not be acquired.
            if (fut.isDone())
                return fut;

            try {
                ctx.io().safeSend(
                    fut.nodes(),
                    req,
                    new P1<GridNode>() {
                        @Override public boolean apply(GridNode node) {
                            fut.onNodeLeft(node.id());

                            return !fut.isDone();
                        }
                    }
                );
            }
            catch (GridException e) {
                U.error(log, "Failed to send lock request to node [nodes=" + U.toShortString(nodes) +
                    ", req=" + req + ']', e);

                fut.onError(e);
            }

            return fut;
        }
        catch (GridException e) {
            Throwable err = new GridException("Failed to acquire asynchronous lock for keys: " + keys, e);

            // Clean-up.
            fut.onError(err);

            ctx.mvcc().removeFuture(fut);

            return fut;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void unlockAll(Collection<? extends K> keys,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (keys == null || keys.isEmpty())
            return;

        Collection<? extends GridNode> nodes = ctx.remoteNodes(keys);

        try {
            GridDistributedUnlockRequest<K, V> req = new GridDistributedUnlockRequest<K, V>(keys.size());

            for (K key : keys) {
                GridDistributedCacheEntry<K, V> entry = entryexx(key);

                if (!ctx.isAll(entry.wrap(false), filter))
                    continue;

                // Unlock local lock first.
                GridCacheMvccCandidate<K> rmv = entry.removeLock();

                if (rmv != null && !nodes.isEmpty()) {
                    if (!rmv.reentry()) {
                        req.addKey(entry.key(), entry.getOrMarshalKeyBytes(), ctx);

                        // We are assuming that lock ID is the same for all keys.
                        req.version(rmv.version());

                        if (log.isDebugEnabled())
                            log.debug("Removed lock (will distribute): " + rmv);
                    }
                    else {
                        if (log.isDebugEnabled())
                            log.debug("Locally unlocked lock reentry without distributing to other nodes [removed=" +
                                rmv + ", entry=" + entry + ']');
                    }
                }
                else {
                    if (log.isDebugEnabled())
                        log.debug("Current thread still owns lock (or there are no other nodes) [lock=" + rmv +
                            ", curThreadId=" + Thread.currentThread().getId() + ']');
                }
            }

            // Don't proceed of no keys to unlock.
            if (req.keyBytes().isEmpty()) {
                if (log.isDebugEnabled())
                    log.debug("No keys to unlock locally (was it reentry unlock?): " + keys);

                return;
            }

            // We don't wait for reply to this message. Receiving side will have
            // to make sure that unlock requests don't come before lock requests.
            ctx.io().safeSend(nodes, req, null);
        }
        catch (GridException e) {
            U.error(log, "Failed to unlock class for keys: " + keys, e);
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

        Collection<GridRichNode> nodes = ctx.remoteNodes(keys);

        try {
            // Send request to remove from remote nodes.
            GridDistributedUnlockRequest<K, V> req = new GridDistributedUnlockRequest<K, V>(keys.size());

            req.version(ver);

            for (K key : keys) {
                while (true) {
                    GridDistributedCacheEntry<K, V> entry = peekexx(key);

                    try {
                        if (entry != null) {
                            GridCacheMvccCandidate<K> cand = entry.candidate(ver);

                            if (cand != null) {
                                // Remove candidate from local node first.
                                if (entry.removeLock(cand.version())) {
                                    // If there is only local node in this lock's topology,
                                    // then there is no reason to distribute the request.
                                    if (nodes.isEmpty())
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

            if (nodes.isEmpty())
                return;

            req.completedVersions(
                ctx.tm().committedVersions(ver),
                ctx.tm().rolledbackVersions(ver));

            if (!req.keyBytes().isEmpty())
                // We don't wait for reply to this message.
                ctx.io().safeSend(nodes, req, null);
        }
        catch (GridException ex) {
            U.error(log, "Failed to unlock the lock for keys: " + keys, ex);
        }
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, Collection<K>> mapKeysToNodes(Collection<? extends K> keys) {
        Map<UUID, Collection<K>> map = new HashMap<UUID, Collection<K>>();

        for (K key : keys) {
            Collection<GridRichNode> nodes = ctx.remoteNodes(key);

            if (F.isEmpty(nodes))
                nodes = F.asList(ctx.localNode());

            for (GridNode node : nodes) {
                Collection<K> keyCol = map.get(node.id());

                if (keyCol == null)
                    map.put(node.id(), keyCol = new LinkedList<K>());

                keyCol.add(key);
            }
        }

        return map;
    }

    /**
     * @param keys Keys to get nodes for.
     * @return Subgrid for given keys.
     */
    @Override public GridProjection gridProjection(Collection<? extends K> keys) {
        return ctx.grid().projectionForNodes(ctx.allNodes(keys));
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridReplicatedCache.class, this, "super", super.toString());
    }
}
