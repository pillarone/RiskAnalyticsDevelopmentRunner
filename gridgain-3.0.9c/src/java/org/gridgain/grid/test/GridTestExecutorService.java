// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test;

import org.gridgain.grid.thread.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Special {@link ExecutorService} executor service to be used for JUnit execution.
 * This executor service is specified by default in {@code $GRIDGAIN_HOME/config/junit/junit-spring.xml}.
 * Every thread created and used within this executor service will belong to its own
 * {@link ThreadGroup} instance that is different from all other threads. This way all threads
 * spawned by a single unit test will belong to the same thread group. This feature is
 * used for logging to be able to group log statements from different threads for individual
 * tests.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTestExecutorService extends ThreadPoolExecutor {
    /** Default core pool size (value is {@code 100}). */
    public static final int DFLT_CORE_POOL_SIZE = 50;

    /**
     * Creates a new service with default initial parameters. Each created thread will
     * belong to its own group.
     * <p>
     * Default values are:
     * <table class="doctable">
     * <tr>
     *      <th>Name</th>
     *      <th>Default Value</th>
     * </tr>
     * <tr>
     *      <td>Core Pool Size</td>
     *      <td>{@code 100} (see {@link #DFLT_CORE_POOL_SIZE}).</td>
     * </tr>
     * <tr>
     *      <td>Maximum Pool Size</td>
     *      <td>None, is it is not used for unbounded queues.</td>
     * </tr>
     * <tr>
     *      <td>Keep alive time</td>
     *      <td>No limit (see {@link Long#MAX_VALUE}).</td>
     * </tr>
     * <tr>
     *      <td>Blocking Queue (see {@link BlockingQueue}).</td>
     *      <td>Unbounded linked blocking queue (see {@link LinkedBlockingQueue}).</td>
     * </tr>
     * </table>
     *
     * @param gridName Name of the grid.
     */
    public GridTestExecutorService(String gridName) {
        super(DFLT_CORE_POOL_SIZE, DFLT_CORE_POOL_SIZE, Long.MAX_VALUE, NANOSECONDS,
            new LinkedBlockingQueue<Runnable>(), new GridJunitThreadFactory(gridName));
    }

    /**
     * Creates a new service with the given initial parameters. Each created thread
     * will belong to its own group.
     *
     * @param corePoolSize The number of threads to keep in the pool, even if they are idle.
     * @param maxPoolSize The maximum number of threads to allow in the pool.
     * @param keepAliveTime When the number of threads is greater than the core, this is the maximum time
     *      that excess idle threads will wait for new tasks before terminating.
     * @param workQueue The queue to use for holding tasks before they are executed. This queue will hold only
     *      runnable tasks submitted by the {@link #execute(Runnable)} method.
     * @param gridName Name of the grid.
     */
    public GridTestExecutorService(String gridName, int corePoolSize, int maxPoolSize, long keepAliveTime,
        BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maxPoolSize, keepAliveTime, MILLISECONDS, workQueue, new GridJunitThreadFactory(gridName));
    }

    /**
     * Creates a new service with the given initial parameters. Each created thread will
     * belong to its own group.
     *
     * @param corePoolSize The number of threads to keep in the pool, even if they are idle.
     * @param maxPoolSize The maximum number of threads to allow in the pool.
     * @param keepAliveTime When the number of threads is greater than the core, this is the maximum time
     *      that excess idle threads will wait for new tasks before terminating.
     * @param workQueue The queue to use for holding tasks before they are executed. This queue will hold only the
     *      runnable tasks submitted by the {@link #execute(Runnable)} method.
     * @param handler The handler to use when execution is blocked because the thread bounds and queue
     *      capacities are reached.
     * @param gridName Name of the grid.
     */
    public GridTestExecutorService(String gridName, int corePoolSize, int maxPoolSize, long keepAliveTime,
        BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maxPoolSize, keepAliveTime, MILLISECONDS, workQueue,
            new GridJunitThreadFactory(gridName), handler);
    }

    /**
     * This class provides implementation of {@link ThreadFactory}  factory
     * for creating JUnit grid threads. Note that in order to properly
     * sort out output from every thread, we create a new thread group for
     * every thread.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private static class GridJunitThreadFactory implements ThreadFactory {
        /** Grid name. */
        private final String gridName;

        /** Number of all system threads in the system. */
        private static final AtomicLong grpCntr = new AtomicLong(0);

        /**
         * Constructs new thread factory for given grid. All threads will belong
         * to the same default thread group.
         *
         * @param gridName Grid name.
         */
        GridJunitThreadFactory(String gridName) {
            this.gridName = gridName;
        }

        /** {@inheritDoc} */
        @Override public Thread newThread(Runnable r) {
            ThreadGroup parent = Thread.currentThread().getThreadGroup();

            while (parent instanceof GridJunitThreadGroup) {
                parent = parent.getParent();
            }

            return new GridThread(new GridJunitThreadGroup(parent, "gridgain-#" + grpCntr.incrementAndGet()),
                gridName, "gridgain", r);
        }

        /** */
        private class GridJunitThreadGroup extends ThreadGroup {
            /**
             * @param parent Group parent.
             * @param name Group name.
             */
            GridJunitThreadGroup(ThreadGroup parent, String name) {
                super(parent, name);

                assert !(parent instanceof GridJunitThreadGroup);
            }
        }
    }
}
