// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.worker;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Pool of runnable workers. This class automatically takes care of
 * error handling that has to do with executing a runnable task and
 * ensures that all tasks are finished when stop occurs.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridWorkerPool {
    /** */
    private final Executor exec;

    /** */
    private final GridLogger log;

    /** */
    private final Collection<GridWorker> workers = new GridConcurrentHashSet<GridWorker>();

    /**
     * @param exec Executor service.
     * @param log Logger to use.
     */
    public GridWorkerPool(Executor exec, GridLogger log) {
        assert exec != null;
        assert log != null;

        this.exec = exec;
        this.log = log;
    }

    /**
     * Schedules runnable task for execution.
     *
     * @param w Runnable task.
     * @throws GridException Thrown if any exception occurred.
     */
    @SuppressWarnings({"CatchGenericClass", "ProhibitedExceptionThrown"})
    public void execute(final GridWorker w) throws GridException {
        workers.add(w);

        try {
            exec.execute(new Runnable() {
                @Override public void run() {
                    try {
                        w.run();
                    }
                    finally {
                        workers.remove(w);
                    }
                }
            });
        }
        catch (RejectedExecutionException e) {
            workers.remove(w);

            throw new GridExecutionRejectedException("Failed to execute worker due to execution rejection.", e);
        }
        catch (RuntimeException e) {
            workers.remove(w);

            throw new GridException("Failed to execute worker due to runtime exception.", e);
        }
        catch (Error e) {
            workers.remove(w);

            throw e;
        }
    }

    /**
     * Waits for all workers to finish.
     *
     * @param cancel Flag to indicate whether workers should be cancelled
     *      before waiting for them to finish.
     */
    public void join(boolean cancel) {
        if (cancel)
            U.cancel(workers);

        // Record current interrupted status of calling thread.
        boolean interrupted = Thread.interrupted();

        try {
            U.join(workers, log);
        }
        finally {
            // Reset interrupted flag on calling thread.
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }
}
