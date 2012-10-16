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
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheTxState.*;

/**
 * Replicated user transaction.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridReplicatedTxLocal<K, V> extends GridCacheTxLocalAdapter<K, V> {
    /** All keys participating in transaction. */
    private Set<K> allKeys;

    /** Future. */
    private final AtomicReference<GridReplicatedTxPrepareFuture<K, V>> prepareFut =
        new AtomicReference<GridReplicatedTxPrepareFuture<K, V>>();

    /** Future. */
    private final AtomicReference<GridReplicatedTxCommitFuture<K, V>> commitFut =
        new AtomicReference<GridReplicatedTxCommitFuture<K, V>>();

    /** Future. */
    private final AtomicReference<GridReplicatedTxCommitFuture<K, V>> rollbackFut =
        new AtomicReference<GridReplicatedTxCommitFuture<K, V>>();

    /** */
    private boolean syncCommit;

    /** */
    private boolean syncRollback;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridReplicatedTxLocal() {
        // No-op.
    }

    /**
     * @param ctx Cache context.
     * @param implicit Implicit flag.
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout Timeout.
     * @param invalidate Invalidation policy.
     * @param syncCommit Synchronous commit flag.
     * @param syncRollback Synchronous rollback flag.
     * @param swapEnabled Whether to use swap storage.
     * @param storeEnabled Whether to use read/write through.
     */
    GridReplicatedTxLocal(
        GridCacheContext<K, V> ctx,
        boolean implicit,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        long timeout,
        boolean invalidate,
        boolean syncCommit,
        boolean syncRollback,
        boolean swapEnabled,
        boolean storeEnabled) {
        super(ctx, ctx.versions().next(), implicit, concurrency, isolation, timeout, invalidate, swapEnabled,
            storeEnabled);

        assert ctx != null;

        this.syncCommit = syncCommit;
        this.syncRollback = syncRollback;
    }

    /** {@inheritDoc} */
    @Override public boolean syncCommit() {
        return syncCommit;
    }

    /** {@inheritDoc} */
    @Override public boolean syncRollback() {
        return syncRollback;
    }

    /** {@inheritDoc} */
    @Override public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner) {
        GridReplicatedTxCommitFuture<K, V> fut = commitFut.get();

        return fut != null && fut.onOwnerChanged(entry, owner);
    }

    /**
     * @return Prepare fut.
     */
    @Override public GridFuture<GridCacheTx> future() {
        return prepareFut.get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheTx> finishFuture() {
        return commitFut.get() == null ? rollbackFut.get() : commitFut.get();
    }

    /**
     *
     */
    private void initializeKeys() {
        if (allKeys == null) {
            Collection<K> readSet = readSet();
            Collection<K> writeSet = writeSet();

            Set<K> allKeys = new HashSet<K>(readSet.size() + writeSet.size(), 1.0f);

            allKeys.addAll(readSet);
            allKeys.addAll(writeSet);

            this.allKeys = allKeys;
        }
    }

    /**
     * @return Node group for transaction.
     */
    private Collection<GridRichNode> resolveNodes() {
        initializeKeys();

        if (allKeys.isEmpty())
            return Collections.emptyList();

        // Do not include local node for transaction processing, as
        // it is included by doing local transaction commit/rollback
        // operations already.
        return ctx.remoteNodes(allKeys);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Override public boolean finishEC(boolean commit) throws GridException {
        GridException err = null;

        try {
            if (commit)
                state(COMMITTING);
            else
                state(ROLLING_BACK);

            if (commit && !isRollbackOnly()) {
                if (!userCommitEC())
                    return false;
            }
            else
                userRollback();
        }
        catch (GridException e) {
            err = e;

            commit = false;

            // If heuristic error.
            if (!isRollbackOnly())
                invalidate(true);
        }

        GridReplicatedTxCommitFuture<K, V> fut = commit ? commitFut.get() : rollbackFut.get();

        if (err != null) {
            state(UNKNOWN);

            if (fut != null)
                fut.onError(err);

            throw err;
        }
        else {
            if (!state(commit ? COMMITTED : ROLLED_BACK)) {
                if (fut != null)
                    fut.onError(new GridException("Invalid transaction state for commit or rollback: " + this));

                state(UNKNOWN);
            }
            else if (fut != null)
                fut.complete();
        }

        return true;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"CatchGenericClass", "ThrowableInstanceNeverThrown"})
    @Override public void finish(boolean commit) throws GridException {
        if (ec()) {
            finishEC(commit);

            return;
        }

        initializeKeys();

        GridReplicatedTxPrepareFuture<K, V> prep = prepareFut.get();

        GridReplicatedTxCommitFuture<K, V> fin = commit ? commitFut.get() : rollbackFut.get();

        assert fin != null;

        Collection<? extends GridNode> nodes =
            commit ?
                prep == null ? Collections.<GridNode>emptyList() : prep.nodes() :
                fin.txNodes();

        GridException err = null;

        // Commit to DB first. This way if there is a failure, transaction
        // won't be committed.
        try {
            if (commit && !isRollbackOnly())
                userCommit();
            else
                userRollback();
        }
        catch (GridException e) {
            err = e;

            commit = false;

            // If heuristic error.
            if (!isRollbackOnly()) {
                invalidate = true;

                U.warn(log, "Set transaction invalidation flag to true due to error [tx=" + this + ", err=" + err + ']');
            }
        }

        try {
            if (!ec()) {
                if (!allKeys.isEmpty() && !nodes.isEmpty()) {
                    assert ctx.mvcc().hasFuture(fin);

                    // We write during commit only for pessimistic transactions.
                    Collection<GridCacheTxEntry<K, V>> writeEntries = pessimistic() ? writeEntries() : null;

                    boolean reply = (syncCommit && commit) || (syncRollback && !commit);

                    GridCacheMessage<K, V> req = new GridDistributedTxFinishRequest<K, V>(
                        xidVersion(),
                        fin.futureId(),
                        commitVersion(),
                        threadId,
                        commit,
                        isInvalidate(),
                        completedBase,
                        committedVers,
                        rolledbackVers,
                        writeEntries,
                        reply);

                    try {
                        ctx.io().safeSend(nodes, req, null);
                    }
                    catch (Throwable e) {
                        String msg = "Failed to send finish request to nodes [node=" + U.toShortString(nodes) +
                            ", req=" + req + ']';

                        log.error(msg, e);

                        if (err == null)
                            err = new GridException("Failed to finish transaction " +
                                "(it may remain on some nodes until cleaned by timeout): " + this, e);
                    }
                }
                else if (log.isDebugEnabled()) {
                    if (allKeys.isEmpty())
                        log.debug("Transaction has no keys to persist: " + this);
                    else {
                        assert nodes.isEmpty();

                        log.debug("Transaction has no remote nodes to send commit request to: " + this);
                    }
                }
            }
            else {
                assert ec();

                if (log.isDebugEnabled())
                    log.debug("Commit will not be distributed because transaction is EC: " + this);
            }
        }
        catch (Throwable e) {
            if (err == null)
                err = new GridException("Failed to finish transaction: " + this, e);
            else
                U.error(log, "Failed to finish transaction: " + this, e);
        }

        if (err != null) {
            state(UNKNOWN);

            if (prep != null)
                prep.onError(err);

            fin.onTxFinished();

            throw err;
        }
        else {
            if (!state(commit ? COMMITTED : ROLLED_BACK)) {
                state(UNKNOWN);

                if (prep != null)
                    prep.onError(new GridException("Invalid transaction state for commit or rollback: " + this));
            }

            fin.onTxFinished();
        }
    }

    /**
     *
     * @param req Request.
     * @param txEntries Transaction entries.
     */
    void candidatesByKey(GridDistributedBaseMessage<K, V> req, Map<K, GridCacheTxEntry<K, V>> txEntries) {
        if (txEntries != null) {
            for (GridCacheTxEntry<K, V> txEntry : txEntries.values()) {
                while (true) {
                    try {
                        GridCacheMapEntry<K, V> entry = (GridCacheMapEntry<K, V>)txEntry.cached();

                        req.candidatesByKey(entry.key(), entry.localCandidates(/*exclude version*/xidVer));

                        break;
                    }
                    // Possible if entry cached within transaction is obsolete.
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry while setting local candidates for entry (will retry) [entry=" +
                                txEntry.cached() + ", tx=" + this + ']');

                        txEntry.cached(ctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheTx> prepareAsync() {
        GridReplicatedTxPrepareFuture<K, V> fut = prepareFut.get();

        if (fut == null) {
            Collection<GridRichNode> nodeGrp = resolveNodes();

            // Future must be created before any exception can be thrown.
            if (!prepareFut.compareAndSet(null, fut = new GridReplicatedTxPrepareFuture<K, V>(ctx, this, nodeGrp)))
                return prepareFut.get();
        }

        if (!state(PREPARING)) {
            if (setRollbackOnly()) {
                if (timedOut())
                    fut.onError(new GridCacheTxTimeoutException("Transaction timed out and was rolled back: " + this));
                else
                    fut.onError(new GridException("Invalid transaction state for prepare [state=" + state() +
                        ", tx=" + this + ']'));
            }

            return fut;
        }

        // For pessimistic mode we don't distribute prepare request.
        if (pessimistic()) {
            try {
                userPrepare();

                if (!state(PREPARED)) {
                    setRollbackOnly();

                    fut.onError(new GridException("Invalid transaction state for commit [state=" + state() +
                        ", tx=" + this + ']'));

                    return fut;
                }

                fut.complete();

                return fut;
            }
            catch (GridException e) {
                fut.onError(e);

                return fut;
            }
        }

        // OPTIMISTIC locking.
        if (allKeys.isEmpty()) {
            // Move transition to committed state.
            if (state(PREPARED))
                fut.complete();
            else if (state(ROLLING_BACK)) {
                if (doneFlag.compareAndSet(false, true)) {
                    ctx.tm().rollbackTx(this);

                    state(ROLLED_BACK);

                    fut.onError(new GridCacheTxRollbackException("Transaction was rolled back: " + this));

                    if (log.isDebugEnabled())
                        log.debug("Rolled back empty transaction: " + this);
                }
            }

            fut.complete();

            return fut;
        }

        try {
            userPrepare();

            if (fut.nodes().isEmpty())
                fut.onAllReplies();
            else {
                // This will attempt to locally commit
                // EVENTUALLY CONSISTENT transactions.
                fut.onPreparedEC();

                GridDistributedBaseMessage<K, V> req = new GridDistributedTxPrepareRequest<K, V>(
                    this,
                    optimistic() && serializable() ? readEntries() : null,
                    writeEntries());

                // Set local candidates.
                candidatesByKey(req, writeMap());

                // Completed versions.
                req.completedVersions(ctx.tm().committedVersions(minVer), ctx.tm().rolledbackVersions(minVer));

                try {
                    ctx.mvcc().addFuture(fut);

                    ctx.io().safeSend(fut.nodes(), req, new GridPredicate<GridNode>() {
                        @Override public boolean apply(GridNode n) {
                            GridReplicatedTxPrepareFuture<K, V> fut = prepareFut.get();

                            fut.onNodeLeft(n.id());

                            return !fut.isDone();
                        }
                    });
                }
                catch (GridException e) {
                    String msg = "Failed to send prepare request to nodes [req=" + req + ", nodes=" +
                        U.toShortString(fut.nodes()) + ']';

                    log.error(msg, e);

                    fut.onError(new GridCacheTxRollbackException(msg, e));
                }
            }

            return fut;
        }
        catch (GridCacheTxTimeoutException e) {
            fut.onError(e);

            return fut;
        }
        catch (GridCacheTxOptimisticException e) {
            fut.onError(e);

            return fut;
        }
        catch (GridException e) {
            setRollbackOnly();

            try {
                rollback();
            }
            catch (GridException e1) {
                log().error("Failed to rollback transaction: " + this, e1);
            }

            fut.onError(new GridCacheTxRollbackException("Failed to prepare transaction: " + this, e));

            return fut;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Override public GridFuture<GridCacheTx> commitAsync() {
        prepareAsync();

        GridReplicatedTxPrepareFuture<K, V> prep = prepareFut.get();

        assert prep != null;

        GridReplicatedTxCommitFuture<K, V> fin = commitFut.get();

        if (fin == null)
            // Future must be created before any exception can be thrown.
            if (!commitFut.compareAndSet(null, fin = new GridReplicatedTxCommitFuture<K, V>(ctx, this, prep.nodes())))
                return commitFut.get();

        assert allKeys != null;

        // OPTIMISTIC locking.
        if (allKeys.isEmpty()) {
            // Move transition to committed state.
            if (state(COMMITTING)) {
                if (doneFlag.compareAndSet(false, true)) {
                    ctx.tm().commitTx(this);

                    state(COMMITTED);

                    if (log.isDebugEnabled())
                        log.debug("Committed empty transaction: " + this);
                }
            }
            else if (state(ROLLING_BACK)) {
                if (doneFlag.compareAndSet(false, true)) {
                    ctx.tm().rollbackTx(this);

                    state(ROLLED_BACK);

                    if (log.isDebugEnabled())
                        log.debug("Rolled back empty transaction: " + this);
                }
            }

            fin.complete();

            return fin;
        }

        final GridReplicatedTxCommitFuture<K, V> fut = fin;

        prep.listenAsync(new CI1<GridFuture<GridCacheTx>>() {
            @Override public void apply(GridFuture<GridCacheTx> f) {
                try {
                    f.get();
                }
                catch (GridException e) {
                    // Failed during prepare, can't commit.
                    fut.onError(e);

                    return;
                }

                if (!state(COMMITTING)) {
                    GridCacheTxState state = state();

                    if (state != COMMITTING && state != COMMITTED) {
                        fut.onError(new GridException("Invalid transaction state for commit [state=" + state() +
                            ", tx=" + this + ']'));

                        return;
                    }
                    else {
                        if (log.isDebugEnabled())
                            log.debug("Invalid transaction state for commit (another thread is committing): " + this);

                        return;
                    }
                }

                ctx.mvcc().addFuture(fut);

                fut.init();
            }
        });

        return fin;
    }

    /** {@inheritDoc} */
    @Override public void rollback() throws GridException {
        setRollbackOnly();

        GridReplicatedTxCommitFuture<K, V> fin = rollbackFut.get();

        GridReplicatedTxPrepareFuture<K, V> prep = prepareFut.get();

        Collection<? extends GridNode> nodes =
            optimistic() ?
                prep == null ? Collections.<GridNode>emptyList() : prep.nodes() :
                resolveNodes();

        if (fin == null && !ec()) {
            // Future must be created before any exception can be thrown.
            if (!rollbackFut.compareAndSet(null, fin = new GridReplicatedTxCommitFuture<K, V>(ctx, this, nodes)))
                return;
        }

        if (!state(ROLLING_BACK)) {
            if (log.isDebugEnabled())
                log.debug("Invalid transaction state for rollback [state=" + state() + ", tx=" + this + ']');

            return;
        }

        if (!ec()) {
            assert fin != null;

            ctx.mvcc().addFuture(fin);

            fin.init();

            if (syncRollback) {
                if (F.isEmpty(fin.nodes()))
                    fin.complete();
            }
            else
                fin.complete();

            fin.get();
        }
        else
            finish(false);
    }

    /** {@inheritDoc} */
    @Override public void addLocalCandidates(K key, Collection<GridCacheMvccCandidate<K>> cands) {
        /* No-op. */
    }

    /** {@inheritDoc} */
    @Override public Map<K, Collection<GridCacheMvccCandidate<K>>> localCandidates() {
        return Collections.emptyMap();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridReplicatedTxLocal.class, this, "super", super.toString());
    }
}
