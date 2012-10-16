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
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheTxState.*;

/**
 * Replicated cache transaction future.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridReplicatedTxCommitFuture<K, V> extends GridFutureAdapter<GridCacheTx>
    implements GridCacheMvccFuture<K, V, GridCacheTx> {
    /** Future ID. */
    private GridUuid futId = GridUuid.randomUuid();

    /** Cache registry. */
    @GridToStringExclude
    private GridCacheContext<K, V> ctx;

    /** Cache transaction. */
    @GridToStringExclude // Need to exclude due to circular dependencies.
    private GridCacheTxLocalEx<K, V> tx;

    /** Nodes enlisted into transaction. */
    private Collection<GridNode> txNodes;

    /** Nodes to expect replies from. */
    private Collection<UUID> nodes;

    /** Error. */
    @GridToStringExclude
    private AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);

    /** Commit flag. */
    private AtomicBoolean commit = new AtomicBoolean(false);

    /** Logger. */
    @GridToStringExclude
    private GridLogger log;

    /** */
    private AtomicBoolean released = new AtomicBoolean(false);

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridReplicatedTxCommitFuture() {
        // No-op.
    }

    /**
     * @param ctx Cache context.
     * @param tx Cache transaction.
     * @param nodes Nodes enlisted into transaction.
     */
    public GridReplicatedTxCommitFuture(
        GridCacheContext<K, V> ctx,
        GridCacheTxLocalEx<K, V> tx,
        Collection<? extends GridNode> nodes) {
        super(ctx.kernalContext());

        assert tx != null;
        assert nodes != null;

        this.ctx = ctx;
        this.tx = tx;

        txNodes = ctx.discovery().aliveNodes(nodes);

        if (isSync())
            this.nodes = new ConcurrentLinkedQueue<UUID>(F.nodeIds(nodes));

        log = ctx.logger(getClass());
    }

    /**
     * @return {@code True} if sync commit or rollback.
     */
    private boolean isSync() {
        return tx.syncCommit() || tx.syncRollback();
    }

    /**
     *
     */
    void init() {
        if (isSync()) {
            for (Iterator<UUID> it = nodes.iterator(); it.hasNext();) {
                UUID id = it.next();

                // Remove left node from wait list.
                if (ctx.discovery().node(id) == null)
                    it.remove();
            }
        }

        checkLocks(null);
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
     * @return Nodes to expect replies from.
     */
    @Override public Collection<? extends GridNode> nodes() {
        return ctx.discovery().nodes(nodes);
    }

    /**
     * @return Nodes enlisted into transaction.
     */
    public Collection<? extends GridNode> txNodes() {
        return txNodes;
    }

    /**
     * @return Lock version.
     */
    public GridCacheTxLocalEx<K, V> tx() {
        return tx;
    }

    /**
     * @param nodeId ID of removed node.
     */
    @Override public boolean onNodeLeft(UUID nodeId) {
        if (nodes != null)
            for (UUID id : nodes) {
                if (id.equals(nodeId)) {
                    onResult(nodeId);

                    return true;
                }
            }

        return false;
    }

    /**
     * @param nodeId Node ID.
     */
    void onResult(UUID nodeId) {
        if (nodes != null) {
            nodes.remove(nodeId);

            if (nodes.isEmpty() && released.get())
                onComplete();
        }
    }

    /**
     * Completes this future.
     */
    void complete() {
        onComplete();
    }

    /**
     *
     */
    public void onTxFinished() {
        if (released.compareAndSet(false, true)) {
            if (nodes != null && nodes.isEmpty())
                onComplete();
        }
    }

    /**
     * @param e Error.
     */
    void onError(Throwable e) {
        if (err.compareAndSet(null, e)) {
            boolean marked = tx.setRollbackOnly();

            if (e instanceof GridCacheTxRollbackException && marked)
                rollback();

            onComplete();
        }
    }

    /**
     * @param cached Entry.
     * @return {@code True} if locked.
     * @throws GridCacheEntryRemovedException If removed.
     */
    private boolean locked(GridCacheEntryEx<K, V> cached) throws GridCacheEntryRemovedException {
        // Reentry-aware check.
        return (cached.lockedLocally(tx.xid()) || cached.lockedByThread(tx.threadId()));
    }

    /**
     * Callback for whenever all replies are received.
     *
     * @param entry Owner entry.
     */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    private void checkLocks(GridCacheEntryEx<K, V> entry) {
        if (log.isDebugEnabled())
            log.debug("Transaction future received owner changed callback: " + entry);

        // For eventually consistent transactions, we commit as locks come.
        if (!tx.isRollbackOnly()) {
            if (!tx.ec() && !tx.pessimistic() && !commit.get() && !isDone()) {
                for (GridCacheTxEntry<K, V> txEntry : tx.writeEntries()) {
                    while (true) {
                        GridCacheEntryEx<K, V> cached = txEntry.cached();

                        try {
                            // Don't compare entry against itself.
                            if (cached != entry && !locked(cached)) {
                                if (log.isDebugEnabled())
                                    log.debug("Transaction entry is not locked by transaction (will wait) [entry=" + cached +
                                        ", tx=" + tx + ']');

                                return;
                            }

                            break; // While.
                        }
                        // Possible if entry cached within transaction is obsolete.
                        catch (GridCacheEntryRemovedException ignored) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry in future onAllReplies method (will retry): " + txEntry);

                            txEntry.cached(ctx.cache().entryEx(txEntry.key()), txEntry.keyBytes());
                        }
                    }
                }
            }

            commit();
        }
        else
            rollback();
    }

    /** {@inheritDoc} */
    @Override public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner) {
        if (log.isDebugEnabled())
            log.debug("Transaction future received owner changed callback [owner=" + owner + ", entry=" + entry + ']');

        checkLocks(entry);

        return false;
    }

    /**
     * Callback invoked when all locks succeeded.
     */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    private void commit() {
        // Even if committing state was set, we set it again here.
        tx.state(COMMITTING);

        if (tx.ec()) {
            try {
                if (tx.finishEC(true)) {
                    if (log.isDebugEnabled())
                        log.debug("All EVENTUALLY_CONSISTENT entries have been committed: " + tx);

                    onComplete();
                }
            }
            catch (GridCacheTxTimeoutException e) {
                onError(e);
            }
            catch (GridException e) {
                if (tx.state() == UNKNOWN)
                    onError(new GridCacheTxHeuristicException("Commit only partially succeeded " +
                        "(entries will be invalidated on remote nodes once transaction timeout passes): " + tx, e));
                else
                    onError(new GridCacheTxRollbackException("Failed to commit transaction (will attempt rollback): " +
                        tx, e));
            }
        }
        else if (commit.compareAndSet(false, true)) {
            try {
                tx.finish(true);

                if (!tx.syncCommit() || nodes.isEmpty())
                    onComplete();
            }
            catch (GridCacheTxTimeoutException e) {
                onError(e);
            }
            catch (GridException e) {
                if (tx.state() == UNKNOWN)
                    onError(new GridCacheTxHeuristicException("Commit only partially succeeded " +
                        "(entries will be invalidated on remote nodes once transaction timeout passes): " + tx, e));
                else
                    onError(new GridCacheTxRollbackException("Failed to commit transaction (will attempt rollback): " +
                        tx, e));
            }
        }
    }

    /**
     *
     */
    private void rollback() {
        try {
            tx.finish(false);

            if (!tx.syncRollback() || nodes.isEmpty())
                onComplete();
        }
        catch (GridException e) {
            onError(e);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() {
        if (log.isDebugEnabled())
            log.debug("Attempting to cancel committing transaction (will ignore): " + tx);

        return false;
    }

    /**
     * Completeness callback.
     */
    private void onComplete() {
        if (onDone(tx, err.get()))
            // Clean up.
            ctx.mvcc().removeFuture(this);
    }

    /**
     * Checks for errors.
     *
     * @throws GridException If check failed.
     */
    private void checkError() throws GridException {
        if (err.get() != null)
            throw U.cast(err.get());
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridReplicatedTxCommitFuture.class, this, "err",
            err == null ? "" : err.toString());
    }
}
