// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.local;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheTxState.*;

/**
 * Local cache transaction.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridLocalTx<K, V> extends GridCacheTxLocalAdapter<K, V> {
    /** Transaction future. */
    private final AtomicReference<GridLocalTxFuture<K, V>> fut = new AtomicReference<GridLocalTxFuture<K, V>>();

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridLocalTx() {
        // No-op.
    }

    /**
     * @param ctx Cache registry.
     * @param implicit {@code True} if transaction is implicitly created by the system,
     *      {@code false} if user explicitly created the transaction.
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout Timeout.
     * @param invalidate Invalidation policy.
     * @param swapEnabled Whether to use swap storage.
     * @param storeEnabled Whether to use read/write through.
     */
    GridLocalTx(
        GridCacheContext<K, V> ctx,
        boolean implicit,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        long timeout,
        boolean invalidate,
        boolean swapEnabled,
        boolean storeEnabled) {
        super(ctx, ctx.versions().next(), implicit, concurrency, isolation, timeout, invalidate, swapEnabled,
            storeEnabled);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheTx> future() {
        return fut.get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheTx> finishFuture() {
        return fut.get();
    }

    /** {@inheritDoc} */
    @Override public boolean onOwnerChanged(GridCacheEntryEx<K, V> entry, GridCacheMvccCandidate<K> owner) {
        GridLocalTxFuture<K, V> fut = this.fut.get();

        return fut != null && fut.onOwnerChanged(entry, owner);
    }

    /** {@inheritDoc} */
    @Override public void prepare() throws GridException {
        if (!state(PREPARING)) {
            GridCacheTxState state = state();

            // If other thread is doing "prepare", then no-op.
            if (state == PREPARING || state == PREPARED || state == COMMITTING || state == COMMITTED) {
                return;
            }

            setRollbackOnly();

            throw new GridException("Invalid transaction state for prepare [state=" + state + ", tx=" + this + ']');
        }

        try {
            userPrepare();

            state(PREPARED);
        }
        catch (GridException e) {
            setRollbackOnly();

            throw e;
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheTx> prepareAsync() {
        try {
            prepare();

            return new GridFinishedFuture<GridCacheTx>(ctx.kernalContext(), this);
        }
        catch (GridException e) {
            return new GridFinishedFuture<GridCacheTx>(ctx.kernalContext(), e);
        }
    }

    /**
     * Commits EC transaction.
     *
     * @throws GridException If commit failed.
     */
    void commitEC0() throws GridException {
        assert concurrency == GridCacheTxConcurrency.EVENTUALLY_CONSISTENT;

        state(COMMITTING);

        if (state() == COMMITTING) {
            // Special handling for EC transactions.
            try {
                if (userCommitEC()) {
                    state(COMMITTED);
                }
            }
            finally {
                if (!done()) {
                    if (isRollbackOnly()) {
                        state(ROLLING_BACK);

                        userRollback();

                        state(ROLLED_BACK);
                    }
                }
            }
        }
    }

    /**
     * Commits without prepare.
     *
     * @throws GridException If commit failed.
     */
    void commit0() throws GridException {
        assert !ec();

        if (state(COMMITTING)) {
            try {
                userCommit();
            }
            finally {
                if (!done()) {
                    if (isRollbackOnly()) {
                        state(ROLLING_BACK);

                        userRollback();

                        state(ROLLED_BACK);
                    }
                    else {
                        state(COMMITTED);
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheTx> commitAsync() {
        try {
            prepare();
        }
        catch (GridException e) {
            state(UNKNOWN);

            return new GridFinishedFuture<GridCacheTx>(ctx.kernalContext(), e);
        }

        GridLocalTxFuture<K, V> fut = this.fut.get();

        if (fut == null) {
            if (this.fut.compareAndSet(null, fut = new GridLocalTxFuture<K, V>(ctx, this))) {
                ctx.mvcc().addFuture(fut);

                fut.checkLocks();

                return fut;
            }
        }

        return this.fut.get();
    }

    /** {@inheritDoc} */
    @Override public void rollback() throws GridException {
        state(ROLLING_BACK);

        userRollback();

        state(ROLLED_BACK);
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
    @Override public void finish(boolean commit) throws GridException {
        assert false;
    }

    /** {@inheritDoc} */
    @Override public boolean finishEC(boolean commit) throws GridException {
        assert false; return false;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridLocalTx.class, this, "super", super.toString());
    }
}