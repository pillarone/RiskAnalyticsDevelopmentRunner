// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.collision.fifoqueue;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.collision.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * This class provides implementation for Collision SPI based on FIFO queue. Jobs are ordered
 * as they arrived and only {@link #getParallelJobsNumber()} number of jobs is allowed to
 * execute in parallel. Other jobs will be buffered in the passive queue.
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * This SPI has following optional configuration parameters:
 * <ul>
 * <li>
 *      Number of jobs that can execute in parallel (see {@link #setParallelJobsNumber(int)}).
 *      This number should usually be set to the number of threads in the execution thread pool.
 * </li>
 * </ul>
 * <h2 class="header">Java Example</h2>
 * {@code GridFifoQueueCollisionSpi} can be configured as follows:
 * <pre name="code" class="java">
 * GridFifoQueueCollisionSpi colSpi = new GridFifoQueueCollisionSpi();
 *
 * // Execute all jobs sequentially by setting parallel job number to 1.
 * colSpi.setParallelJobsNumber(1);
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default collision SPI.
 * cfg.setCollisionSpi(colSpi);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * {@code GridFifoQueueCollisionSpi} can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *       ...
 *       &lt;property name="collisionSpi"&gt;
 *           &lt;bean class="org.gridgain.grid.spi.collision.fifoqueue.GridFifoQueueCollisionSpi"&gt;
 *               &lt;property name="parallelJobsNumber" value="1"/&gt;
 *           &lt;/bean&gt;
 *       &lt;/property&gt;
 *       ...
 * &lt;/bean&gt;
 * </pre>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(true)
public class GridFifoQueueCollisionSpi extends GridSpiAdapter implements GridCollisionSpi,
    GridFifoQueueCollisionSpiMBean {
    /** Running (not held) jobs predicate. */
    private static final GridPredicate<GridCollisionJobContext> RUNNING_JOBS = new P1<GridCollisionJobContext>() {
        @Override public boolean apply(GridCollisionJobContext ctx) {
            return !ctx.getJobContext().heldcc();
        }
    };

    /**
     * Default number of parallel jobs allowed (value is {@code 95} which is
     * slightly less same as default value of threads in the execution thread pool
     * to allow some extra threads for system processing).
     */
    public static final int DFLT_PARALLEL_JOBS_NUM = 95;

    /**
     * Default waiting jobs number. If number of waiting jobs exceeds this number,
     * jobs will be rejected. Default value is {@link Integer#MAX_VALUE}.
     */
    public static final int DFLT_WAIT_JOBS_NUM = Integer.MAX_VALUE;

    /** Number of jobs that can be executed in parallel. */
    private AtomicInteger parallelJobsNum = new AtomicInteger(DFLT_PARALLEL_JOBS_NUM);

    /** Wait jobs number. */
    private AtomicInteger waitJobsNum = new AtomicInteger(DFLT_WAIT_JOBS_NUM);

    /** Grid logger. */
    @GridLoggerResource
    private GridLogger log;

    /** Number of jobs that were active last time. */
    private final AtomicInteger runningCnt = new AtomicInteger(0);

    /** Number of jobs that were waiting for execution last time. */
    private final AtomicInteger waitingCnt = new AtomicInteger(0);

    /** Number of currently held jobs. */
    private final AtomicInteger heldCnt = new AtomicInteger(0);

    /**
     * Sets number of jobs that are allowed to be executed in parallel on this node.
     * If not provided, default value is {@code {@link #DFLT_PARALLEL_JOBS_NUM}}.
     *
     * @param parallelJobsNum Maximum number of jobs to be executed in parallel.
     */
    @GridSpiConfiguration(optional = true)
    public void setParallelJobsNumber(int parallelJobsNum) {
        this.parallelJobsNum.set(parallelJobsNum);
    }

    /** {@inheritDoc} */
    @Override public int getParallelJobsNumber() {
        return parallelJobsNum.get();
    }

    /**
     * Sets maximum number of jobs that are allowed to wait in waiting queue. If
     * number of jobs exceeds this number, jobs will be rejected. Default value
     * is defined by {@link #DFLT_WAIT_JOBS_NUM} constant.
     *
     * @param waitJobsNum Maximum waiting jobs number.
     */
    @GridSpiConfiguration(optional = true)
    public void setWaitingJobsNumber(int waitJobsNum) {
        this.waitJobsNum.set(waitJobsNum);
    }

    /** {@inheritDoc} */
    @Override public int getWaitingJobsNumber() {
        return waitJobsNum.get();
    }

    /** {@inheritDoc} */
    @Override public int getCurrentWaitJobsNumber() {
        return waitingCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getCurrentActiveJobsNumber() {
        return runningCnt.get() + heldCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getCurrentRunningJobsNumber() {
        return runningCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getCurrentHeldJobsNumber() {
        return heldCnt.get();
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        assertParameter(parallelJobsNum.get() > 0, "parallelJobsNum > 0");
        assertParameter(waitJobsNum.get() >= 0, "waitingJobsNum >= 0");

        // Start SPI start stopwatch.
        startStopwatch();

        // Ack parameters.
        if (log.isDebugEnabled())
            log.debug(configInfo("parallelJobsNum", parallelJobsNum));

        registerMBean(gridName, this, GridFifoQueueCollisionSpiMBean.class);

        // Ack start.
        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        unregisterMBean();

        // Ack ok stop.
        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @Override public void setExternalCollisionListener(GridCollisionExternalListener lsnr) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onCollision(Collection<GridCollisionJobContext> waitJobs,
        Collection<GridCollisionJobContext> activeJobs) {
        assert waitJobs != null;
        assert activeJobs != null;

        int activeSize = F.size(activeJobs, RUNNING_JOBS);

        int waitSize = waitJobs.size();

        waitingCnt.set(waitSize);
        runningCnt.set(activeSize);
        heldCnt.set(activeJobs.size() - activeSize);

        int activateCnt = parallelJobsNum.get() - activeSize;

        if (activateCnt > 0 && !waitJobs.isEmpty()) {
            int cnt = 0;

            for (GridCollisionJobContext waitCtx : waitJobs) {
                if (cnt++ == activateCnt)
                    break;

                waitCtx.activate();

                waitSize--;
            }
        }

        int waitJobsNum = this.waitJobsNum.get();

        if (waitSize > waitJobsNum) {
            int skip = waitJobs.size() - waitSize;

            int i = 0;

            for (GridCollisionJobContext waitCtx : waitJobs) {
                if (++i >= skip) {
                    waitCtx.cancel();

                    if (--waitSize <= waitJobsNum)
                        break;
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridFifoQueueCollisionSpi.class, this);
    }
}
