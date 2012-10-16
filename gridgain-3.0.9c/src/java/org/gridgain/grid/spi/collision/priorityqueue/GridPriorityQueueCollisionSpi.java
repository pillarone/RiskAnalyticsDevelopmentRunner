// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.collision.priorityqueue;

import org.gridgain.grid.*;
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
 * This class provides implementation for Collision SPI based on priority queue. Jobs are first ordered
 * by their priority, if one is specified, and only first {@link #getParallelJobsNumber()} jobs
 * is allowed to execute in parallel. Other jobs will be queued up.
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * This SPI has following optional configuration parameters:
 * <ul>
 * <li>
 *      Number of jobs that can be executed in parallel (see {@link #setParallelJobsNumber(int)}).
 *      This number should usually be set to no greater than number of threads in the execution thread pool.
 * </li>
 * <li>
 *      Priority attribute session key (see {@link #getPriorityAttributeKey()}). Prior to
 *      returning from {@link GridTask#map(List, Object)} method, task implementation should
 *      set a value into the task session keyed by this attribute key. See {@link GridTaskSession}
 *      for more information about task session.
 * </li>
 * <li>
 *      Priority attribute job context key (see {@link #getJobPriorityAttributeKey()}).
 *      It is used for specifying job priority.
 *      See {@link GridJobContext} for more information about job context.
 * </li>
 * <li>Default priority value (see {@link #getDefaultPriority()}). It is used when no priority is set.</li>
 * <li>
 *      Default priority increase value (see {@link #getStarvationIncrement()}).
 *      It is used for increasing priority when job gets bumped down.
 *      This future is used for preventing starvation waiting jobs execution. 
 * </li>
 * <li>
 *      Default increasing priority flag value (see {@link #isStarvationPreventionEnabled()}).
 *      It is used for enabling increasing priority when job gets bumped down.
 *      This future is used for preventing starvation waiting jobs execution.
 * </li>
 * </ul>
 * Below is a Java example of configuration for priority collision SPI:
 * <pre name="code" class="java">
 * GridPriorityQueueCollisionSpi colSpi = new GridPriorityQueueCollisionSpi();
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
 * Here is Spring XML configuration example:
 * <pre name="code" class="xml">
 * &lt;property name="collisionSpi"&gt;
 *     &lt;bean class="org.gridgain.grid.spi.collision.priorityqueue.GridPriorityQueueCollisionSpi"&gt;
 *         &lt;property name="priorityAttributeKey" value="myPriorityAttributeKey"/&gt;
 *         &lt;property name="parallelJobsNumber" value="10"/&gt;
 *     &lt;/bean&gt;
 * &lt;/property&gt;
 * </pre>
 * <p>
 * <h1 class="header">Coding Example</h1>
 * Here is an example of a grid tasks that uses priority collision SPI configured in example above.
 * Note that priority collision resolution is absolutely transparent to the user and is simply a matter of proper
 * grid configuration. Also, priority may be defined only for task (it can be defined within the task,
 * not at a job level). All split jobs will be started with priority declared in their owner task.
 * <p>
 * This example demonstrates how urgent task may be declared with a higher priority value.
 * Priority SPI guarantees (see its configuration in example above, where number of parallel
 * jobs is set to {@code 1}) that all jobs from {@code MyGridUrgentTask} will most likely
 * be activated first (one by one) and jobs from {@code MyGridUsualTask} with lowest priority
 * will wait. Once higher priority jobs complete, lower priority jobs will be scheduled.
 * <pre name="code" class="java">
 * public class MyGridUsualTask extends GridTaskSplitAdapter&lt;Object, Object&gt; {
 *    public static final int SPLIT_COUNT = 20;
 *
 *    &#64;GridTaskSessionResource
 *    private GridTaskSession taskSes;
 *
 *    &#64;Override
 *    protected Collection&lt;? extends GridJob&gt; split(int gridSize, Object arg) throws GridException {
 *        ...
 *        // Set low task priority (note that attribute name is used by the SPI
 *        // and should not be changed).
 *        taskSes.setAttribute("grid.task.priority", 5);
 *
 *        Collection&lt;GridJob&gt; jobs = new ArrayList&lt;GridJob&gt;(SPLIT_COUNT);
 *
 *        for (int i = 1; i &lt;= SPLIT_COUNT; i++) {
 *            jobs.add(new GridJobAdapter&lt;Integer&gt;(i) {
 *                ...
 *            });
 *        }
 *        ...
 *    }
 * }
 * </pre>
 * and
 * <pre name="code" class="java">
 * public class MyGridUrgentTask extends GridTaskSplitAdapter&lt;Object, Object&gt; {
 *    public static final int SPLIT_COUNT = 5;
 *
 *    &#64;GridTaskSessionResource
 *    private GridTaskSession taskSes;
 *
 *    &#64;Override
 *    protected Collection&lt;? extends GridJob&gt; split(int gridSize, Object arg) throws GridException {
 *        ...
 *        // Set high task priority (note that attribute name is used by the SPI
 *        // and should not be changed).
 *        taskSes.setAttribute("grid.task.priority", 10);
 *
 *        Collection&lt;GridJob&gt; jobs = new ArrayList&lt;GridJob&gt;(SPLIT_COUNT);
 *
 *        for (int i = 1; i &lt;= SPLIT_COUNT; i++) {
 *            jobs.add(new GridJobAdapter&lt;Integer&gt;(i) {
 *                ...
 *            });
 *        }
 *        ...
 *    }
 * }
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
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
public class GridPriorityQueueCollisionSpi extends GridSpiAdapter implements GridCollisionSpi,
    GridPriorityQueueCollisionSpiMBean {
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
     * Default waiting jobs number. If number of waiting jobs exceed this number,
     * jobs will be rejected. Default value is {@link Integer#MAX_VALUE}.
     */
    public static final int DFLT_WAIT_JOBS_NUM = Integer.MAX_VALUE;

    /** Default priority attribute key (value is {@code grid.task.priority}). */
    public static final String DFLT_PRIORITY_ATTRIBUTE_KEY = "grid.task.priority";

    /** Default job priority attribute key (value is {@code grid.job.priority}). */
    public static final String DFLT_JOB_PRIORITY_ATTRIBUTE_KEY = "grid.job.priority";

    /**
     * Default priority that will be assigned if job does not have a
     * priority attribute set (value is {@code 0}).
     */
    public static final int DFLT_PRIORITY = 0;

    /** Default value on which job priority will be increased every time when job gets bumped down. */
    public static final int DFLT_STARVATION_INCREMENT = 1;

    /** Default flag for preventing starvation of lower priority jobs. */
    public static final boolean DFLT_PREVENT_STARVATION_ENABLED = true;

    /** Priority attribute key should be the same on all nodes. */
    private static final String PRIORITY_ATTRIBUTE_KEY = "gg:collision:priority";

    /** Number of jobs that can be executed in parallel. */
    private int parallelJobsNum = DFLT_PARALLEL_JOBS_NUM;

    /** Wait jobs number. */
    private int waitJobsNum = DFLT_WAIT_JOBS_NUM;

    /** Number of jobs that were active last time. */
    private final AtomicInteger runningCnt = new AtomicInteger(0);

    /** Number of jobs that were waiting for execution last time. */
    private final AtomicInteger waitingCnt = new AtomicInteger(0);

    /** Number of currently held jobs. */
    private final AtomicInteger heldCnt = new AtomicInteger(0);

    /** */
    private String taskAttrKey = DFLT_PRIORITY_ATTRIBUTE_KEY;

    /** */
    private String jobAttrKey = DFLT_JOB_PRIORITY_ATTRIBUTE_KEY;

    /** */
    private int dfltPriority = DFLT_PRIORITY;

    /** */
    private int starvationInc = DFLT_STARVATION_INCREMENT;

    /** */
    private boolean preventStarvation = DFLT_PREVENT_STARVATION_ENABLED;

    /** */
    @GridLoggerResource private GridLogger log;

    /**
     * Sets number of jobs that are allowed to be executed in parallel on
     * this node.
     * <p>
     * If not provided, default value is {@code {@link #DFLT_PARALLEL_JOBS_NUM}}.
     *
     * @param parallelJobsNum Maximum number of jobs to be executed in parallel.
     */
    @GridSpiConfiguration(optional = true)
    public void setParallelJobsNumber(int parallelJobsNum) {
        this.parallelJobsNum = parallelJobsNum;
    }

    /** {@inheritDoc} */
    @Override public int getParallelJobsNumber() {
        return parallelJobsNum;
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
        this.waitJobsNum = waitJobsNum;
    }

    /** {@inheritDoc} */
    @Override public int getWaitingJobsNumber() {
        return waitJobsNum;
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

    /**
     * Sets task priority attribute key. This key will be used to look up task
     * priorities from task context (see {@link GridTaskSession#getAttribute(Object)}).
     * <p>
     * If not provided, default value is {@code {@link #DFLT_PRIORITY_ATTRIBUTE_KEY}}.
     *
     * @param taskAttrKey Priority session attribute key.
     */
    @GridSpiConfiguration(optional = true)
    public void setPriorityAttributeKey(String taskAttrKey) {
        this.taskAttrKey = taskAttrKey;
    }

    /**
     * Sets job priority attribute key. This key will be used to look up job
     * priorities from job context (see {@link GridJobContext#getAttribute(Object)}).
     * <p>
     * If not provided, default value is {@code {@link #DFLT_JOB_PRIORITY_ATTRIBUTE_KEY}}.
     *
     * @param jobAttrKey Job priority attribute key.
     */
    @GridSpiConfiguration(optional = true)
    public void setJobPriorityAttributeKey(String jobAttrKey) {
        this.jobAttrKey = jobAttrKey;
    }

    /** {@inheritDoc} */
    @Override public String getPriorityAttributeKey() {
        return taskAttrKey;
    }

    /** {@inheritDoc} */
    @Override public String getJobPriorityAttributeKey() {
        return jobAttrKey;
    }

    /** {@inheritDoc} */
    @Override public int getDefaultPriority() {
        return dfltPriority;
    }

    /** {@inheritDoc} */
    @Override public int getStarvationIncrement() {
        return starvationInc;
    }

    /** {@inheritDoc} */
    @Override public boolean isStarvationPreventionEnabled() {
        return preventStarvation;
    }

    /**
     * Sets default job priority. If job has no set priority this value
     * will be used to compare with another job.
     * <p>
     * If not provided, default value is {@code {@link #DFLT_PRIORITY}}.
     *
     * @param dfltPriority Default job priority.
     */
    @GridSpiConfiguration(optional = true)
    public void setDefaultPriority(int dfltPriority) {
        this.dfltPriority = dfltPriority;
    }

    /**
     * Sets value to increment job priority by every time a lower priority job gets
     * behind a higher priority job.
     * <p>
     * If not provided, default value is {@code {@link #DFLT_STARVATION_INCREMENT }}.
     *
     * @param starvationInc Job priority increment value.
     */
    @GridSpiConfiguration(optional = true)
    public void setStarvationIncrement(int starvationInc) {
        this.starvationInc = starvationInc;
    }

    /**
     * Enables/disabled job starvation (default is enabled).
     * <p>
     * If not provided, default value is {@code {@link #DFLT_PREVENT_STARVATION_ENABLED }}.
     *
     * @param preventStarvation Flag to enable job starvation prevention.
     */
    @GridSpiConfiguration(optional = true)
    public void setStarvationPreventionEnabled(boolean preventStarvation) {
        this.preventStarvation = preventStarvation;
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> getNodeAttributes() throws GridSpiException {
        return F.<String, Object>asMap(createSpiAttributeName(PRIORITY_ATTRIBUTE_KEY), getPriorityAttributeKey());
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        assertParameter(parallelJobsNum > 0, "parallelJobsNum > 0");
        assertParameter(waitJobsNum >= 0, "waitingJobsNum >= 0");
        assertParameter(taskAttrKey != null, "taskAttrKey != null");
        assertParameter(jobAttrKey != null, "jobAttrKey != null");

        // Start SPI start stopwatch.
        startStopwatch();

        // Ack parameters.
        if (log.isDebugEnabled()) {
            log.debug(configInfo("parallelJobsNum", parallelJobsNum));
            log.debug(configInfo("taskAttrKey", taskAttrKey));
            log.debug(configInfo("jobAttrKey", jobAttrKey));
            log.debug(configInfo("dfltPriority", dfltPriority));
            log.debug(configInfo("starvationInc", starvationInc));
            log.debug(configInfo("preventStarvation", preventStarvation));
        }

        registerMBean(gridName, this, GridPriorityQueueCollisionSpiMBean.class);

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

        waitingCnt.set(waitJobs.size());
        runningCnt.set(activeSize);
        heldCnt.set(activeJobs.size() - activeSize);

        int waitSize = waitJobs.size();

        int activateCnt = parallelJobsNum - activeSize;

        if (activateCnt > 0 && !waitJobs.isEmpty()) {
            if (waitJobs.size() <= activateCnt) {
                for (GridCollisionJobContext waitJob : waitJobs) {
                    waitJob.activate();

                    waitSize--;
                }
            }
            else {
                List<GridCollisionJobContext> passiveList = new ArrayList<GridCollisionJobContext>(waitJobs);

                Collections.sort(passiveList, new Comparator<GridCollisionJobContext>() {
                    /** {@inheritDoc} */
                    @Override public int compare(GridCollisionJobContext o1, GridCollisionJobContext o2) {
                        int p1 = getJobPriority(o1);
                        int p2 = getJobPriority(o2);

                        return p1 < p2 ? 1 : p1 == p2 ? 0 : -1;
                    }
                });

                if (preventStarvation)
                    bumpPriority(waitJobs, passiveList);

                for (int i = 0; i < activateCnt; i++) {
                    passiveList.get(i).activate();

                    waitSize--;
                }
            }
        }

        if (waitSize > waitJobsNum) {
            List<GridCollisionJobContext> waitList = new ArrayList<GridCollisionJobContext>(waitJobs);

            // Put jobs with highest priority first.
            Collections.sort(waitList, new Comparator<GridCollisionJobContext>() {
                /** {@inheritDoc} */
                @Override public int compare(GridCollisionJobContext o1, GridCollisionJobContext o2) {
                    int p1 = getJobPriority(o1);
                    int p2 = getJobPriority(o2);

                    return p1 < p2 ? 1 : p1 == p2 ? 0 : -1;
                }
            });

            int skip = waitJobs.size() - waitSize;

            int i = 0;

            for (GridCollisionJobContext waitCtx : waitList) {
                if (++i >= skip) {
                    waitCtx.cancel();

                    if (--waitSize <= waitJobsNum)
                        break;
                }
            }
        }
    }

    /**
     * Increases priority if job has bumped down.
     *
     * @param waitJobs Ordered collection of collision contexts for jobs that are currently waiting
     *      for execution.
     * @param passiveJobs Reordered collection of collision contexts for waiting jobs.
     */
    private void bumpPriority(Collection<GridCollisionJobContext> waitJobs, List<GridCollisionJobContext> passiveJobs) {
        assert waitJobs != null;
        assert passiveJobs != null;
        assert waitJobs.size() == passiveJobs.size();

        for (int i = 0; i < passiveJobs.size(); i++) {
            GridCollisionJobContext ctx = passiveJobs.get(i);

            if (i > indexOf(waitJobs, ctx))
                ctx.getJobContext().setAttribute(jobAttrKey, getJobPriority(ctx) + starvationInc);
        }
    }

    /**
     * @param ctxs Collection of contexts.
     * @param ctx Context to find.
     * @return Index of found context.
     */
    private int indexOf(Iterable<GridCollisionJobContext> ctxs, GridCollisionJobContext ctx) {
        int i = 0;

        for (GridCollisionJobContext c : ctxs) {
            if (c == ctx) {
                return i;
            }

            i++;
        }

        assert false : "Failed to find collision context [ctx=" + ctx + ", ctxs=" + ctxs + ']';
        
        return -1;
    }

    /**
     * Gets job priority. At first tries to get from job context. If job context has no priority,
     * then tries to get from task session. If task session has no priority default one will be used.
     *
     * @param ctx Collision job context.
     * @return Job priority.
     */
    private int getJobPriority(GridCollisionJobContext ctx) {
        assert ctx != null;

        Integer p = null;

        GridJobContext jctx = ctx.getJobContext();

        try {
            p = (Integer)jctx.getAttribute(jobAttrKey);
        }
        catch (ClassCastException e) {
            LT.error(log, e, "Type of job context priority attribute '" + jobAttrKey +
                "' is not java.lang.Integer [type=" + jctx.getAttribute(jobAttrKey).getClass() + ']');
        }

        if (p == null) {
            GridTaskSession ses = ctx.getTaskSession();

            try {
                p = (Integer)ses.getAttribute(taskAttrKey);
            }
            catch (ClassCastException e) {
                LT.error(log, e, "Type of task session priority attribute '" + taskAttrKey +
                    "' is not java.lang.Integer [type=" + ses.getAttribute(taskAttrKey).getClass() + ']');
            }

            if (p == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed get priority from job context attribute '" + jobAttrKey +
                        "' and task session attribute '" + taskAttrKey + "' (will use default priority): " +
                        dfltPriority);
                }

                p = dfltPriority;
            }
        }

        assert p != null;

        return p;
    }

    /** {@inheritDoc} */
    @Override protected List<String> getConsistentAttributeNames() {
        return Collections.singletonList(createSpiAttributeName(PRIORITY_ATTRIBUTE_KEY));
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridPriorityQueueCollisionSpi.class, this);
    }
}
