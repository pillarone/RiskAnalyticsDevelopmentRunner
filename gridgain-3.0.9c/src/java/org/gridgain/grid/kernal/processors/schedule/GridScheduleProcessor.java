// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.schedule;

import it.sauronsoftware.cron4j.*;
import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Schedules cron-based execution of grid tasks and closures.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridScheduleProcessor extends GridProcessorAdapter {
    /** Cron scheduler. */
    private Scheduler sched;

    private Set<GridScheduleFuture<?>> schedFuts = new GridConcurrentHashSet<GridScheduleFuture<?>>();

    /**
     * @param ctx Kernal context.
     */
    public GridScheduleProcessor(GridKernalContext ctx) {
        super(ctx);
    }

    /**
     * @param c Closure to schedule to run as a background cron-based job.
     * @param pattern Scheduling pattern in UNIX cron format with prefix "{n1, n2} " where n1 is delay of scheduling
     *      and n2 is the number of task calls.
     * @return Descriptor of the scheduled execution.
     * @throws GridException Thrown in case of any errors.
     */
    public GridScheduleFuture<?> schedule(final Runnable c, String pattern) throws GridException {
        assert c != null;
        assert pattern != null;

        GridScheduleFutureImpl<Object> fut = new GridScheduleFutureImpl<Object>(sched, ctx, pattern);

        fut.schedule(new GridCallable<Object>() {
            @Nullable @Override public Object call() {
                c.run();

                return null;
            }
        });

        return fut;
    }

    /**
     * @param c Closure to schedule to run as a background cron-based job.
     * @param pattern Scheduling pattern in UNIX cron format with prefix "{n1, n2} " where n1 is delay of scheduling
     *      and n2 is the number of task calls.
     * @return Descriptor of the scheduled execution.
     * @throws GridException Thrown in case of any errors.
     */
    public <R> GridScheduleFuture<R> schedule(Callable<R> c, String pattern) throws GridException {
        assert c != null;
        assert pattern != null;

        GridScheduleFutureImpl<R> fut = new GridScheduleFutureImpl<R>(sched, ctx, pattern);

        fut.schedule(c);

        return fut;
    }

    /**
     *
     * @return Future objects of currently scheduled active(not finished) tasks.
     */
    public Collection<GridScheduleFuture<?>> getScheduledFutures() {
        return Collections.unmodifiableList(new ArrayList<GridScheduleFuture<?>>(schedFuts));
    }

    /**
     * Removes future object from the collection of scheduled futures.
     *
     * @param fut Future object.
     */
    void onDescheduled(GridScheduleFuture<?> fut) {
        assert fut != null;

        schedFuts.remove(fut);
    }

    /**
     * Adds future object to the collection of scheduled futures.
     *
     * @param fut Future object.
     */
    void onScheduled(GridScheduleFuture<?> fut) {
        assert fut != null;

        schedFuts.add(fut);
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        sched = new Scheduler();

        sched.start();
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        if (sched.isStarted()) {
            sched.stop();
        }

        sched = null;
    }
}