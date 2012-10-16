// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.future;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.stopwatch.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.*;

/**
 * Future adapter.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridFutureAdapter<R> extends GridMetadataAwareAdapter implements GridFuture<R>, Externalizable {
    /** Done flag. */
    private boolean done;

    /** Cancelled flag. */
    private boolean cancelled;

    /** Result. */
    @GridToStringInclude
    private R res;

    /** Error. */
    private Throwable err;

    /** Set to {@code false} on deserialization whenever incomplete future is serialized. */
    private boolean valid = true;

    /** Asynchronous listener. */
    private final Set<GridInClosure<? super GridFuture<R>>> lsnrs = new GridLeanSet<GridInClosure<? super GridFuture<R>>>();

    /** Creator thread. */
    private Thread thread = Thread.currentThread();

    /** Mutex. */
    private final Object mux = new Object();

    /** Context. */
    protected GridKernalContext ctx;

    /** Logger. */
    protected GridLogger log;

    /** Future start time. */
    protected final long startTime = System.currentTimeMillis();

    /** Synchronous notification flag. */
    private volatile boolean syncNotify = U.isFutureNotificationSynchronous();

    /** Concurrent notification flag. */
    private volatile boolean concurNotify = U.isFutureNotificationConcurrent();

    /** Future end time. */
    private volatile long endTime;

    /** Watch. */
    protected GridStopwatch watch;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridFutureAdapter() {
        // No-op.
    }

    /**
     * @param ctx Kernal context.
     */
    public GridFutureAdapter(GridKernalContext ctx) {
        assert ctx != null;

        this.ctx = ctx;

        log = ctx.log(getClass());
    }

    /** {@inheritDoc} */
    @Override public long startTime() {
        return startTime;
    }

    /** {@inheritDoc} */
    @Override public long duration() {
        long endTime = this.endTime;

        return endTime == 0 ? System.currentTimeMillis() - startTime : endTime - startTime;
    }

    /** {@inheritDoc} */
    @Override public boolean concurrentNotify() {
        return concurNotify;
    }

    /** {@inheritDoc} */
    @Override public void concurrentNotify(boolean concurNotify) {
        this.concurNotify = concurNotify;
    }

    /** {@inheritDoc} */
    @Override public boolean syncNotify() {
        return syncNotify;
    }

    /** {@inheritDoc} */
    @Override public void syncNotify(boolean syncNotify) {
        this.syncNotify = syncNotify;
    }

    /**
     * Adds a watch to this future.
     *
     * @param name Name of the watch.
     */
    public void addWatch(String name) {
        assert name != null;

        watch = W.stopwatch(name);
    }

    /**
     * Adds a watch to this future.
     *
     * @param watch Watch to add.
     */
    public void addWatch(GridStopwatch watch) {
        assert watch != null;

        this.watch = watch;
    }

    /**
     * Checks that future is in usable state.
     */
    protected void checkValid() {
        if (!valid)
            throw new IllegalStateException("Incomplete future was serialized and cannot " +
                "be used after deserialization.");
    }

    /**
     * @return Valid flag.
     */
    protected boolean isValid() {
        return valid;
    }

    /**
     * Gets internal mutex.
     *
     * @return Internal mutex.
     */
    protected Object mutex() {
        checkValid();

        return mux;
    }

    /**
     * @return Value of error.
     */
    protected Throwable error() {
        checkValid();

        synchronized (mux) {
            return err;
        }
    }

    /**
     * @return Value of result.
     */
    protected R result() {
        checkValid();

        synchronized (mux) {
            return res;
        }
    }

    /** {@inheritDoc} */
    @Override public R call() throws Exception {
        return get();
    }

    /** {@inheritDoc} */
    @Override public R get(long timeout) throws GridException {
        return get(timeout, MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override public R get() throws GridException {
        checkValid();

        try {
            synchronized (mux) {
                while (!done && !cancelled)
                    mux.wait();

                if (done) {
                    if (err != null)
                        throw U.cast(err);

                    return res;
                }

                throw new GridFutureCancelledException("Future was cancelled: " + this);
            }
        }
        catch (InterruptedException e) {
            throw new GridInterruptedException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public R get(long timeout, TimeUnit unit) throws GridException {
        A.ensure(timeout >= 0, "timeout cannot be negative: " + timeout);
        A.notNull(unit, "unit");

        checkValid();

        try {
            long now = System.currentTimeMillis();

            long end = timeout == 0 ? Long.MAX_VALUE : now + MILLISECONDS.convert(timeout, unit);

            // Account for overflow.
            if (end < 0)
                end = Long.MAX_VALUE;

            synchronized (mux) {
                while (!done && !cancelled && now < end) {
                    mux.wait(end - now);

                    if (!done)
                        now = System.currentTimeMillis();
                }

                if (done) {
                    if (err != null)
                        throw U.cast(err);

                    return res;
                }

                if (cancelled)
                    throw new GridFutureCancelledException("Future was cancelled: " + this);

                throw new GridFutureTimeoutException("Timeout was reached before computation completed [duration=" +
                    duration() + "ms, timeout=" + unit.toMillis(timeout) + "ms]");
            }
        }
        catch (InterruptedException e) {
            throw new GridInterruptedException("Got interrupted while waiting for future to complete [duration=" +
                duration() + "ms, timeout=" + unit.toMillis(timeout) + "ms]", e);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void listenAsync(@Nullable final GridInClosure<? super GridFuture<R>> lsnr) {
        if (lsnr != null) {
            checkValid();

            boolean done;

            synchronized (mux) {
                done = this.done;

                if (!done)
                    lsnrs.add(lsnr);
            }

            if (done) {
                if (syncNotify)
                    notifyListener(lsnr);
                else
                    ctx.closure().runLocalSafe(new GPR() {
                        @Override public void run() {
                            notifyListener(lsnr);
                        }
                    });
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void stopListenAsync(@Nullable GridInClosure<? super GridFuture<R>>... lsnr) {
        if (F.isEmpty(lsnr))
            synchronized (mux) {
                lsnrs.clear();
            }
        else
            synchronized (mux) {
                lsnrs.removeAll(F.asList(lsnr));
            }
    }

    /**
     * Notifies all registered listeners.
     */
    private void notifyListeners() {
        final Collection<GridInClosure<? super GridFuture<R>>> tmp;

        synchronized (mux) {
            tmp = new ArrayList<GridInClosure<? super GridFuture<R>>>(lsnrs);
        }

        boolean concurNotify = this.concurNotify;
        boolean syncNotify = this.syncNotify;

        if (concurNotify) {
            for (final GridInClosure<? super GridFuture<R>> lsnr : tmp)
                ctx.closure().runLocalSafe(new GPR() {
                    @Override public void run() {
                        notifyListener(lsnr);
                    }
                }, true);
        }
        else {
            // Always notify in the thread different from start thread.
            if (Thread.currentThread() == thread && !syncNotify) {
                ctx.closure().runLocalSafe(new GPR() {
                    @Override public void run() {
                        // Since concurrent notifications are off, we notify
                        // all listeners in one thread.
                        for (GridInClosure<? super GridFuture<R>> lsnr : tmp)
                            notifyListener(lsnr);
                    }
                }, true);
            }
            else
                for (GridInClosure<? super GridFuture<R>> lsnr : tmp)
                    notifyListener(lsnr);
        }
    }

    /**
     * Notifies single listener.
     *
     * @param lsnr Listener.
     */
    private void notifyListener(GridInClosure<? super GridFuture<R>> lsnr) {
        assert lsnr != null;

        try {
            lsnr.apply(this);
        }
        catch (RuntimeException e) {
            U.error(log, "Failed to notify listener: " + lsnr, e);

            throw e;
        }
        catch (AssertionError e) {
            U.error(log, "Failed to notify listener: " + lsnr, e);

            throw e;
        }
    }

    /**
     * Default no-op implementation that always returns {@code false}.
     * Futures that do support cancellation should override this method
     * and call {@link #onCancelled()} callback explicitly if cancellation
     * indeed did happen.
     */
    @Override public boolean cancel() throws GridException {
        checkValid();

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean isDone() {
        // Don't check for "valid" here, as "done" flag can be read
        // even in invalid state.
        synchronized (mux) {
            return done || cancelled;
        }
    }

    /** {@inheritDoc} */
    @Override public GridAbsPredicate predicate() {
        return new PA() {
            @Override public boolean apply() {
                return isDone();
            }
        };
    }

    /** {@inheritDoc} */
    @Override public boolean isCancelled() {
        checkValid();

        synchronized (mux) {
            return cancelled;
        }
    }

    /**
     * Callback to notify that future is finished with {@code null} result.
     * This method must delegate to {@link #onDone(Object, Throwable)} method.
     *
     * @return {@code True} if result was set by this call.
     */
    public final boolean onDone() {
        return onDone(null, null);
    }

    /**
     * Callback to notify that future is finished.
     * This method must delegate to {@link #onDone(Object, Throwable)} method.
     *
     * @param res Result.
     * @return {@code True} if result was set by this call.
     */
    public final boolean onDone(R res) {
        return onDone(res, null);
    }

    /**
     * Callback to notify that future is finished.
     * This method must delegate to {@link #onDone(Object, Throwable)} method.
     *
     * @param err Error.
     * @return {@code True} if result was set by this call.
     */
    public final boolean onDone(Throwable err) {
        return onDone(null, err);
    }

    /**
     * Callback to notify that future is finished. Note that if non-{@code null} exception is passed in
     * the result value will be ignored.
     *
     * @param res Optional result.
     * @param err Optional error.
     * @return {@code True} if result was set by this call.
     */
    public boolean onDone(@Nullable R res, @Nullable Throwable err) {
        checkValid();

        boolean notify = false;

        boolean gotDone = false;

        try {
            synchronized (mux) {
                if (!done) {
                    gotDone = true;

                    endTime = System.currentTimeMillis();

                    this.res = res;
                    this.err = err;

                    done = true;

                    notify = true;

                    mux.notifyAll(); // Notify possibly waiting child classes.

                    return true;
                }

                return false;
            }
        }
        finally {
            if (gotDone) {
                GridStopwatch w = watch;

                if (w != null)
                    w.stop();
            }

            if (notify)
                notifyListeners();
        }
    }

    /**
     * Callback to notify that future is cancelled.
     *
     * @return {@code True} if cancel flag was set by this call.
     */
    public boolean onCancelled() {
        checkValid();

        synchronized (mux) {
            if (cancelled || done)
                return false;

            cancelled = true;

            mux.notifyAll(); // Notify possibly waiting child classes.
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        boolean done;
        boolean cancelled;
        Object res;
        Throwable err;
        boolean syncNotify;
        boolean concurNotify;

        synchronized (mux) {
            done = this.done;
            cancelled = this.cancelled;
            res = this.res;
            err = this.err;
            syncNotify = this.syncNotify;
            concurNotify = this.concurNotify;
        }

        out.writeBoolean(done);
        out.writeBoolean(syncNotify);
        out.writeBoolean(concurNotify);

        // Don't write any further if not done, as deserialized future
        // will be invalid anyways.
        if (done) {
            out.writeBoolean(cancelled);
            out.writeObject(res);
            out.writeObject(err);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        boolean done = in.readBoolean();

        syncNotify = in.readBoolean();
        concurNotify = in.readBoolean();

        if (!done)
            valid = false;
        else {
            boolean cancelled = in.readBoolean();

            R res = (R)in.readObject();

            Throwable err = (Throwable)in.readObject();

            synchronized (mux) {
                this.done = done;
                this.cancelled = cancelled;
                this.res = res;
                this.err = err;
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridFutureAdapter.class, this);
    }
}
