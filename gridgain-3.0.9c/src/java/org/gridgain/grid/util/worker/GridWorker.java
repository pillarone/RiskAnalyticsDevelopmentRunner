// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.worker;

import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Extension to standard {@link Runnable} interface. Adds proper details to be used
 * with {@link Executor} implementations. Only for internal use.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridWorker implements Runnable {
    /** Grid logger. */
    private static final AtomicReference<GridLogger> clsLog = new AtomicReference<GridLogger>(null);

    /** Thread name. */
    private final String name;

    /** */
    private final String gridName;

    /** */
    private final GridWorkerListener lsnr;

    /** Deferred result. */
    private volatile FutureTask<Void> fut;

    /** Whether or not this runnable is cancelled. */
    private volatile boolean isCancelled;

    /** Actual thread runner. */
    private volatile Thread runner;

    /** Parent thread. */
    private final Thread parent;

    /** Inherited thread locals. */
    private final Map<GridThreadLocalEx<?>, ?> inherited;

    /**
     * Creates new grid worker with given parameters.
     *
     * @param gridName Name of grid this runnable is used in.
     * @param name Worker name. Note that in general thread name and worker (runnable) name are two
     *      different things. The same worker can be executed by multiple threads and therefore
     *      for logging and debugging purposes we separate the two.
     * @param log Grid logger to be used.
     * @param lsnr Listener for life-cycle events.
     */
    protected GridWorker(String gridName, String name, GridLogger log, @Nullable GridWorkerListener lsnr) {
        assert name != null;
        assert log != null;

        parent = Thread.currentThread();

        this.gridName = gridName;
        this.name = name;
        this.lsnr = lsnr;

        if (clsLog.get() == null)
            clsLog.compareAndSet(null, log.getLogger(GridWorker.class));

        inherited = GridThreadLocalEx.inherit();

        reset();
    }

    /**
     * Creates new grid worker with given parameters.
     *
     * @param gridName Name of grid this runnable is used in.
     * @param name Worker name. Note that in general thread name and worker (runnable) name are two
     *      different things. The same worker can be executed by multiple threads and therefore
     *      for logging and debugging purposes we separate the two.
     * @param log Grid logger to be used.
     */
    protected GridWorker(String gridName, String name, GridLogger log) {
        this(gridName, name, log, null);
    }

    /**
     * Enter thread locals.
     */
    private void enterThreadLocals() {
        GridThreadLocal.enter();
        GridThreadLocalEx.enter(inherited);
    }

    /**
     * Leave thread locals.
     */
    private void leaveThreadLocals() {
        GridThreadLocalEx.leave();
        GridThreadLocal.leave();
    }

    /**
     * Resets this instance in case if it needs to be executed more than once.
     */
    public final void reset() {
        isCancelled = false;

        fut = new FutureTask<Void>(new Runnable() {
            @SuppressWarnings("CatchGenericClass")
            @Override public final void run() {
                // Runner thread must be recorded first as other operations
                // may depend on it being present.
                runner = Thread.currentThread();

                GridWorkerGroup.instance(gridName).onStart(GridWorker.this);

                enterThreadLocals();

                GridLogger log = clsLog.get();

                if (log.isDebugEnabled())
                    log.debug("Grid runnable started: " + name);

                try {
                    // Special case, when task gets cancelled before it got scheduled.
                    if (isCancelled)
                        runner.interrupt();

                    // Listener callback.
                    if (lsnr != null)
                        lsnr.onStarted(GridWorker.this);

                    body();
                }
                catch (GridInterruptedException e) {
                    if (log.isDebugEnabled())
                        log.debug("Caught interrupted exception: " + e);
                }
                catch (InterruptedException e) {
                    if (log.isDebugEnabled())
                        log.debug("Caught interrupted exception: " + e);
                }
                // Catch everything to make sure that it gets logged properly and
                // not to kill any threads from the underlying thread pool.
                catch (Throwable e) {
                    U.error(log, "Runtime error caught during grid runnable execution: " + GridWorker.this, e);
                }
                finally {
                    cleanup();

                    if (lsnr != null)
                        lsnr.onStopped(GridWorker.this);

                    if (log.isDebugEnabled())
                        if (isCancelled)
                            log.debug("Grid runnable finished due to cancellation: " + name);
                        else if (runner.isInterrupted())
                            log.debug("Grid runnable finished due to interruption without cancellation: " + name);
                        else
                            log.debug("Grid runnable finished normally: " + name);

                    leaveThreadLocals();

                    GridWorkerGroup.instance(gridName).onFinish(GridWorker.this);

                    // Need to set runner to null, to make sure that
                    // further operations on this runnable won't
                    // affect the thread which could have been recycled
                    // by thread pool.
                    runner = null;
                }
            }
        }, null);
    }

    /** {@inheritDoc} */
    @Override public final void run() {
        fut.run();
    }

    /**
     * The implementation should provide the execution body for this runnable.
     *
     * @throws InterruptedException Thrown in case of interruption.
     * @throws GridInterruptedException If interrupted.
     */
    protected abstract void body() throws InterruptedException, GridInterruptedException;

    /**
     * Optional method that will be called after runnable is finished. Default
     * implementation is no-op.
     */
    protected void cleanup() {
        /* No-op. */
    }

    /**
     * @return Parent thread.
     */
    public Thread parent() {
        return parent;
    }

    /**
     * @return Runner thread.
     */
    public Thread runner() {
        return runner;
    }

    /**
     * Gets name of the grid this runnable belongs to.
     *
     * @return Name of the grid this runnable belongs to.
     */
    public String gridName() {
        return gridName;
    }

    /**
     * Gets this runnable name.
     *
     * @return This runnable name.
     */
    public String name() {
        return name;
    }

    /**
     * Gets logger associated with this runnable.
     *
     * @return Logger associated with this runnable.
     */
    public GridLogger log() {
        return clsLog.get();
    }

    /**
     * Cancels this runnable interrupting actual runner.
     */
    public void cancel() {
        if (clsLog.get().isDebugEnabled())
            clsLog.get().debug("Cancelling grid runnable: " + this);

        isCancelled = true;

        Thread runner = this.runner;

        // Cannot apply Future.cancel() because if we do, then Future.get() would always
        // throw CancellationException and we would not be able to wait for task completion.
        if (runner != null)
            runner.interrupt();
    }

    /**
     * Joins this runnable.
     *
     * @throws InterruptedException Thrown in case of interruption.
     */
    public void join() throws InterruptedException {
        GridLogger log = clsLog.get();

        if (log.isDebugEnabled())
            log.debug("Joining grid runnable: " + this);

        try {
            if (runner == null && isCancelled)
                return;

            fut.get();
        }
        catch (ExecutionException e) {
            U.error(log, "Grid runnable execution threw an error: " + this, e);

            // Should never happen as no exception should penetrate
            // through the 'run' method.
            assert false : "Grid runnable execution should never throw errors: " + this;
        }
        catch (CancellationException e) {
            String err = "Grid runnable got cancelled via Future.cancel() which should never be called: " + this;

            log.error(err, e);

            assert false : err;
        }
    }

    /**
     * Tests whether or not this runnable is cancelled.
     *
     * @return {@code true} if this runnable is cancelled - {@code false} otherwise.
     * @see Future#isCancelled()
     */
    public boolean isCancelled() {
        Thread runner = this.runner;

        return isCancelled || (runner != null && runner.isInterrupted());
    }

    /**
     * Tests whether or not this runnable is finished.
     *
     * @return {@code true} if this runnable is finished - {@code false} otherwise.
     * @see Future#isDone()
     */
    public boolean isDone() {
        return fut.isDone();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        Thread runner = this.runner;

        return S.toString(GridWorker.class, this,
            "hashCode", hashCode(),
            "interrupted", (runner != null ? runner.isInterrupted() : "unknown"),
            "runner", (runner == null ? "null" : runner.getName()));
    }
}
