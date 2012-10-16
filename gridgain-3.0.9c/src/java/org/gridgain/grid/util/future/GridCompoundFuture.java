// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.future;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Future composed of multiple inner futures.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCompoundFuture<T, R> extends GridFutureAdapter<R> {
    /** Futures. */
    @GridToStringInclude
    private final Collection<GridFuture<T>> futs = new ConcurrentLinkedQueue<GridFuture<T>>();

    /** Pending futures. */
    @GridToStringExclude
    private final Collection<GridFuture<T>> pending = new ConcurrentLinkedQueue<GridFuture<T>>();

    /** Listener call count. */
    private final AtomicInteger lsnrCalls = new AtomicInteger();

    /** Finished flag. */
    private final AtomicBoolean finished = new AtomicBoolean();

    /** Reducer. */
    @GridToStringInclude
    private GridReducer<T, R> rdc;

    /** Initialize flag. */
    private AtomicBoolean init = new AtomicBoolean(false);

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridCompoundFuture() {
        // No-op.
    }

    /**
     * @param ctx Context.
     */
    public GridCompoundFuture(GridKernalContext ctx) {
        super(ctx);
    }

    /**
     * @param ctx Context.
     * @param rdc Reducer.
     */
    public GridCompoundFuture(GridKernalContext ctx, @Nullable GridReducer<T, R> rdc) {
        super(ctx);

        this.rdc = rdc;
    }

    /**
     * @param ctx Context.
     * @param rdc Reducer to add.
     * @param futs Futures to add.
     */
    public GridCompoundFuture(GridKernalContext ctx, @Nullable GridReducer<T, R> rdc,
        @Nullable Iterable<GridFuture<T>> futs) {
        super(ctx);

        this.rdc = rdc;

        addAll(futs);
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() throws GridException {
        if (onCancelled()) {
            for (GridFuture<T> fut : futs)
                fut.cancel();

            return true;
        }

        return false;
    }

    /**
     * Gets collection of futures.
     *
     * @return Collection of futures.
     */
    public Collection<GridFuture<T>> futures() {
        return futs;
    }

    /**
     * Gets pending (unfinished) futures.
     *
     * @return Pending futures.
     */
    public Collection<GridFuture<T>> pending() {
        return pending;
    }

    /**
     * Checks if there are pending futures. This is not the same as
     * {@link #isDone()} because child classes may override {@link #onDone(Object, Throwable)}
     * call and delay completion.
     *
     * @return {@code True} if there are pending futures.
     */
    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /**
     * Adds a future to this compound future.
     *
     * @param fut Future to add.
     */
    public void add(GridFuture<T> fut) {
        assert fut != null;

        pending.add(fut);
        futs.add(fut);

        fut.listenAsync(new Listener());

        if (isCancelled())
            try {
                fut.cancel();
            }
            catch (GridException e) {
                onDone(e);
            }
    }

    /**
     * Adds futures to this compound future.
     *
     * @param futs Futures to add.
     */
    public void addAll(@Nullable GridFuture<T>... futs) {
        addAll(F.asList(futs));
    }

    /**
     * Adds futures to this compound future.
     *
     * @param futs Futures to add.
     */
    public void addAll(@Nullable Iterable<GridFuture<T>> futs) {
        if (futs != null)
            for (GridFuture<T> fut : futs)
                add(fut);
    }

    /**
     * Gets optional reducer.
     *
     * @return Optional reducer.
     */
    @Nullable public GridReducer<T, R> reducer() {
        return rdc;
    }

    /**
     * Sets optional reducer.
     *
     * @param rdc Optional reducer.
     */
    public void reducer(@Nullable GridReducer<T, R> rdc) {
        this.rdc = rdc;
    }

    /**
     * Mark this future as initialized.
     */
    public void markInitialized() {
        if (init.compareAndSet(false, true))
            // Check complete to make sure that we take care
            // of all the ignored callbacks.
            checkComplete();
    }

    /**
     * Check completeness of the future.
     */
    private void checkComplete() {
        if (init.get() && lsnrCalls.get() == futs.size() && finished.compareAndSet(false, true)) {
            R res = null;

            try {
                if (rdc != null)
                    res = rdc.apply();
            }
            catch (Exception e) {
                U.error(log, "Failed to execute compound future reducer: " + this, e);
            }

            onDone(res);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCompoundFuture.class, this, "done", isDone(), "cancelled", isCancelled(), "err", error());
    }

    /**
     * Listener for futures.
     */
    private class Listener extends GridInClosure<GridFuture<T>> {
        /** {@inheritDoc} */
        @Override public void apply(GridFuture<T> fut) {
            pending.remove(fut);

            try {
                T t = fut.get();

                try {
                    if (rdc != null && !rdc.collect(t) && finished.compareAndSet(false, true))
                        onDone(rdc.apply());
                }
                catch (RuntimeException e) {
                    U.error(log, "Failed to execute compound future reducer: " + this, e);

                    onDone(e);
                }
                catch (AssertionError e) {
                    U.error(log, "Failed to execute compound future reducer: " + this, e);

                    onDone(e);

                    throw e;
                }

                lsnrCalls.incrementAndGet();

                checkComplete();
            }
            catch (GridCacheTxOptimisticException e) {
                if (log.isDebugEnabled())
                    log.debug("Optimistic failure [fut=" + GridCompoundFuture.this + ", err=" + e + ']');

                onDone(e);
            }
            catch (GridException e) {
                U.error(log, "Failed to execute compound future reducer: " + this, e);

                onDone(e);
            }
            catch (RuntimeException e) {
                U.error(log, "Failed to execute compound future reducer: " + this, e);

                onDone(e);
            }
            catch (AssertionError e) {
                U.error(log, "Failed to execute compound future reducer: " + this, e);

                onDone(e);

                throw e;
            }
        }

        @Override public String toString() {
            return "Compound future listener: " + GridCompoundFuture.this;
        }
    }
}
