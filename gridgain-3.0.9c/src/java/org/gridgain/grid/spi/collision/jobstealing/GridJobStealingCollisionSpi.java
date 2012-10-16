// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.collision.jobstealing;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.collision.*;
import org.gridgain.grid.spi.failover.jobstealing.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Collision SPI that supports job stealing from over-utilized nodes to
 * under-utilized nodes. This SPI is especially useful if you have
 * some jobs within task complete fast, and others sitting in the waiting
 * queue on slower nodes. In such case, the waiting jobs will be <b>stolen</b>
 * from slower node and moved to the fast under-utilized node.
 * <p>
 * The design and ideas for this SPI are significantly influenced by
 * <a href="http://gee.cs.oswego.edu/dl/papers/fj.pdf">Java Fork/Join Framework</a>
 * authored by Doug Lea and planned for Java 7. {@code GridJobStealingCollisionSpi} took
 * similar concepts and applied them to the grid (as opposed to within VM support planned
 * in Java 7).
 * <p>
 * Quite often grids are deployed across many computers some of which will
 * always be more powerful than others. This SPI helps you avoid jobs being
 * stuck at a slower node, as they will be stolen by a faster node. In the following picture
 * when Node<sub>3</sub> becomes free, it steals Job<sub>13</sub> and Job<sub>23</sub>
 * from Node<sub>1</sub> and Node<sub>2</sub> respectively.
 * <p>
 * <center><img src="http://www.gridgain.com/images/job_stealing_white.gif"></center>
 * <p>
 * <i>
 * Note that this SPI must always be used in conjunction with
 * {@link GridJobStealingFailoverSpi}.
 * The responsibility of Job Stealing Failover SPI is to properly route <b>stolen</b>
 * jobs to the nodes that initially requested (<b>stole</b>) these jobs. The
 * SPI maintains a counter of how many times a jobs was stolen and
 * hence traveled to another node. {@code GridJobStealingCollisionSpi}
 * checks this counter and will not allow a job to be stolen if this counter
 * exceeds a certain threshold {@link GridJobStealingCollisionSpi#setMaximumStealingAttempts(int)}.
 * </i>
 * <p>
 * <h1 class="header">Configuration</h1>
 * In order to use this SPI, you should configure your grid instance
 * to use {@code GridJobStealingCollisionSpi} either from Spring XML file or
 * directly. The following configuration parameters are supported:
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * The following configuration parameters are optional:
 * <ul>
 * <li>
 *      Maximum number of active jobs that will be allowed by this SPI
 *      to execute concurrently (see {@link #setActiveJobsThreshold(int)}).
 * </li>
 * <li>
 *      Maximum number of waiting jobs. Once waiting queue size goes below
 *      this number, this SPI will attempt to steal jobs from over-utilized
 *      nodes by sending <b>"steal"</b> requests (see {@link #setWaitJobsThreshold(int)}).
 * </li>
 * <li>
 *      Steal message expire time. If no response was received from a node
 *      to which <b>steal</b> request was sent, then request will be considered
 *      lost and will be resent, potentially to another node (see {@link #setMessageExpireTime(long)}).
 * </li>
 * <li>
 *      Maximum number of stealing attempts for the job (see {@link #setMaximumStealingAttempts(int)}).
 * </li>
 * <li>
 *      Whether stealing enabled or not (see {@link #setStealingEnabled(boolean)}).
 * </li>
 * <li>
 *     Enables stealing to/from only nodes that have these attributes set
 *     (see {@link #setStealingAttributes(Map)}).
 * </li>
 * </ul>
 * Below is example of configuring this SPI from Java code:
 * <pre name="code" class="java">
 * GridJobStealingCollisionSpi spi = new GridJobStealingCollisionSpi();
 *
 * // Configure number of waiting jobs
 * // in the queue for job stealing.
 * spi.setWaitJobsThreshold(10);
 *
 * // Configure message expire time (in milliseconds).
 * spi.setMessageExpireTime(500);
 *
 * // Configure stealing attempts number.
 * spi.setMaximumStealingAttempts(10);
 *
 * // Configure number of active jobs that are allowed to execute
 * // in parallel. This number should usually be equal to the number
 * // of threads in the pool (default is 100).
 * spi.setActiveJobsThreshold(50);
 *
 * // Enable stealing.
 * spi.setStealingEnabled(true);
 *
 * // Set stealing attribute to steal from/to nodes that have it.
 * spi.setStealingAttributes(Collections.singletonMap("node.segment", "foobar"));
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default Collision SPI.
 * cfg.setCollisionSpi(spi);
 * </pre>
 * Here is an example of how this SPI can be configured from Spring XML configuration:
 * <pre name="code" class="xml">
 * &lt;property name="collisionSpi"&gt;
 *     &lt;bean class="org.gridgain.grid.spi.collision.jobstealing.GridJobStealingCollisionSpi"&gt;
 *         &lt;property name="activeJobsThreshold" value="100"/&gt;
 *         &lt;property name="waitJobsThreshold" value="0"/&gt;
 *         &lt;property name="messageExpireTime" value="1000"/&gt;
 *         &lt;property name="maximumStealingAttempts" value="10"/&gt;
 *         &lt;property name="stealingEnabled" value="true"/&gt;
 *         &lt;property name="stealingAttributes"&gt;
 *             &lt;map&gt;
 *                 &lt;entry key="node.segment" value="foobar"/&gt;
 *             &lt;/map&gt;
 *         &lt;/property&gt;
 *     &lt;/bean&gt;
 * &lt;/property&gt;
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings( {"SynchronizationOnLocalVariableOrMethodParameter", "deprecation"})
@GridSpiInfo(
    author = "GridGain Systems, Inc.",
    url = "www.gridgain.com",
    email = "support@gridgain.com",
    version = "3.0.9c.19052011")
@GridSpiMultipleInstancesSupport(true)
public class GridJobStealingCollisionSpi extends GridSpiAdapter implements GridCollisionSpi,
    GridJobStealingCollisionSpiMBean {
    /** Maximum number of attempts to steal job by another node (default is {@code 5}). */
    public static final int DFLT_MAX_STEALING_ATTEMPTS = 5;

    /**
     * Default number of parallel jobs allowed (value is {@code 95} which is
     * slightly less same as default value of threads in the execution thread pool
     * to allow some extra threads for system processing).
     */
    public static final int DFLT_ACTIVE_JOBS_THRESHOLD = 95;

    /**
     * Default steal message expire time in milliseconds (value is {@code 1000}).
     * Once this time is elapsed and no response for steal message is received,
     * the message is considered lost and another steal message will be generated,
     * potentially to another node.
     */
    public static final long DFLT_MSG_EXPIRE_TIME = 1000;

    /**
     * Default threshold of waiting jobs. If number of waiting jobs exceeds this threshold,
     * then waiting jobs will become available to be stolen (value is {@code 0}).
     */
    public static final int DFLT_WAIT_JOBS_THRESHOLD = 0;

    /**
     * Default start value for job priority (value is {@code 0}).
     */
    public static final int DFLT_JOB_PRIORITY = 0;

    /** Communication topic. */
    private static final String JOB_STEALING_COMM_TOPIC = "gridgain.collision.job.stealing.topic";

    /** Job context attribute for storing thief node UUID (this attribute is used in job stealing failover SPI). */
    public static final String THIEF_NODE_ATTR = "gridgain.collision.thief.node";

    /** Threshold of maximum jobs on waiting queue. */
    public static final String WAIT_JOBS_THRESHOLD_NODE_ATTR = "gridgain.collision.wait.jobs.threshold";

    /** Threshold of maximum jobs executing concurrently. */
    public static final String ACTIVE_JOBS_THRESHOLD_NODE_ATTR = "gridgain.collision.active.jobs.threshold";

    /**
     * Name of job context attribute containing current stealing attempt count.
     * This count is incremented every time the same job gets stolen for
     * execution.
     *
     * @see GridJobContext
     */
    public static final String STEALING_ATTEMPT_COUNT_ATTR = "gridgain.stealing.attempt.count";

    /** Maximum stealing attempts attribute name. */
    public static final String MAX_STEALING_ATTEMPT_ATTR = "gridgain.stealing.max.attempts";

    /** Stealing request expiration time attribute name. */
    public static final String MSG_EXPIRE_TIME_ATTR = "gridgain.stealing.msg.expire.time";

    /** Stealing priority attribute name. */
    public static final String STEALING_PRIORITY_ATTR = "gridgain.stealing.priority";

    /** Running (not held) jobs predicate. */
    private static final GridPredicate<GridCollisionJobContext> RUNNING_JOBS = new P1<GridCollisionJobContext>() {
        @Override public boolean apply(GridCollisionJobContext ctx) {
            return !ctx.getJobContext().heldcc();
        }
    };


    /** Grid logger. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridLoggerResource private GridLogger log;

    /** Number of jobs that can be executed in parallel. */
    private int activeJobsThreshold = DFLT_ACTIVE_JOBS_THRESHOLD;

    /** Configuration parameter defining waiting job count threshold for stealing to start. */
    private int waitJobsThreshold = DFLT_WAIT_JOBS_THRESHOLD;

    /** Message expire time configuration parameter. */
    private long msgExpireTime = DFLT_MSG_EXPIRE_TIME;

    /** Maximum number of attempts to steal job by another node. */
    private int maxStealingAttempts = DFLT_MAX_STEALING_ATTEMPTS;

    /** Flag indicating whether job stealing is enabled. */
    private boolean isStealingEnabled = true;

    /** */
    @GridToStringInclude private Map<String, ? extends Serializable> stealAttrs;

    /** Number of jobs that were active last time. */
    private final AtomicInteger runningCnt = new AtomicInteger(0);

    /** Number of jobs that were waiting for execution last time. */
    private final AtomicInteger waitingCnt = new AtomicInteger(0);

    /** Number of currently held jobs. */
    private final AtomicInteger heldCnt = new AtomicInteger(0);

    /** Total number of stolen jobs. */
    private final AtomicInteger totalStolenJobsNum = new AtomicInteger(0);

    /**
     * Map of sent messages. Note that we choose concurrency level of {@code 64} as
     * there is no way to predict number of concurrent threads and this is the closest
     * power of 2 that makes sense.
     */
    private final ConcurrentMap<UUID, MessageInfo> sendMsgMap =
        new ConcurrentHashMap<UUID, MessageInfo>(16, 0.75f, 64);

    /**
     * Map of received messages. Note that we choose concurrency level of {@code 64} as
     * there is no way to predict number of concurrent threads and this is the closest
     * power of 2 that makes sense.
     */
    private final ConcurrentMap<UUID, MessageInfo> rcvMsgMap =
        new ConcurrentHashMap<UUID, MessageInfo>(16, 0.75f, 64);

    /** */
    private final ConcurrentLinkedQueue<GridNode> nodeQueue = new ConcurrentLinkedQueue<GridNode>();

    /** */
    private GridCollisionExternalListener extLsnr;

    /** Discovery listener. */
    private GridLocalEventListener discoLsnr;

    /** Communication listener. */
    private GridMessageListener msgLsnr;

    /** Number of steal requests. */
    private final AtomicInteger stealReqs = new AtomicInteger(0);

    /**
     * Sets number of jobs that are allowed to be executed in parallel on
     * this node. Node that this attribute may be different for different
     * grid nodes as stronger nodes may be able to execute more jobs in
     * parallel.
     * <p>
     * If not provided, default value is {@code {@link #DFLT_ACTIVE_JOBS_THRESHOLD}}.
     *
     * @param activeJobsThreshold Maximum number of jobs to be executed in parallel.
     */
    @GridSpiConfiguration(optional = true)
    public void setActiveJobsThreshold(int activeJobsThreshold) {
        this.activeJobsThreshold = activeJobsThreshold;
    }

    /** {@inheritDoc} */
    @Override public int getActiveJobsThreshold() {
        return activeJobsThreshold;
    }

    /**
     * Sets wait jobs threshold. If number of waiting jobs exceeds this threshold,
     * then waiting jobs will become available to be stolen.
     * <p>
     * Note this value may be different (but does not have to be) for different
     * nodes in the grid. You may wish to give stronger nodes a smaller waiting
     * threshold so they can start stealing jobs from other nodes sooner.
     * <p>
     * If not provided, default value is {@code {@link #DFLT_WAIT_JOBS_THRESHOLD}}.
     *
     * @param waitJobsThreshold Default job priority.
     */
    @GridSpiConfiguration(optional = true)
    public void setWaitJobsThreshold(int waitJobsThreshold) {
        this.waitJobsThreshold = waitJobsThreshold;
    }

    /** {@inheritDoc} */
    @Override public int getWaitJobsThreshold() {
        return waitJobsThreshold;
    }

    /**
     * Message expire time configuration parameter. If no response is received
     * from a busy node to a job stealing request, then implementation will
     * assume that message never got there, or that remote node does not have
     * this node included into topology of any of the jobs it has. In any
     * case, job steal request will be resent (potentially to another node).
     * <p>
     * If not provided, default value is {@code {@link #DFLT_MSG_EXPIRE_TIME}}.
     *
     * @param msgExpireTime Message expire time.
     */
    @GridSpiConfiguration(optional = true)
    public void setMessageExpireTime(long msgExpireTime) {
        this.msgExpireTime = msgExpireTime;
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
    @Override public long getMessageExpireTime() {
        return msgExpireTime;
    }

    /**
     * Sets flag indicating whether this node should attempt to steal jobs
     * from other nodes. If {@code false}, then this node will steal allow
     * jobs to be stolen from it, but won't attempt to steal any jobs from
     * other nodes.
     * <p>
     * Default value is {@code true}.
     *
     * @param isStealingEnabled Flag indicating whether this node should attempt
     *      to steal jobs from other nodes
     */
    @GridSpiConfiguration(optional = true)
    public void setStealingEnabled(boolean isStealingEnabled) {
        this.isStealingEnabled = isStealingEnabled;
    }

    /** {@inheritDoc} */
    @Override public boolean isStealingEnabled() {
        return isStealingEnabled;
    }

    /** {@inheritDoc} */
    @Override public int getMaximumStealingAttempts() {
        return maxStealingAttempts;
    }

    /**
     * Sets maximum number of attempts to steal job by another node.
     * If not specified, {@link #DFLT_MAX_STEALING_ATTEMPTS} value will be used.
     * <p>
     * Note this value must be identical for all grid nodes in the grid.
     *
     * @param maxStealingAttempts Maximum number of attempts to steal job by
     *      another node.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaximumStealingAttempts(int maxStealingAttempts) {
        this.maxStealingAttempts = maxStealingAttempts;
    }

    /**
     * Configuration parameter to enable stealing to/from only nodes that
     * have these attributes set (see {@link GridNode#getAttribute(String)} and
     * {@link GridConfiguration#getUserAttributes()} methods).
     *
     * @param stealAttrs Node attributes to enable job stealing for.
     */
    @GridSpiConfiguration(optional = true)
    public void setStealingAttributes(Map<String, ? extends Serializable> stealAttrs) {
        this.stealAttrs = stealAttrs;
    }

    /** {@inheritDoc} */
    @Override public Map<String, ? extends Serializable> getStealingAttributes() {
        return stealAttrs;
    }

    /** {@inheritDoc} */
    @Override public int getCurrentWaitJobsCount() {
        return waitingCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getCurrentActiveJobsCount() {
        return runningCnt.get();
    }

    /** {@inheritDoc} */
    @Override public int getTotalStolenJobsCount() {
        return totalStolenJobsNum.get();
    }

    /** {@inheritDoc} */
    @Override public int getCurrentJobsToStealCount() {
        return stealReqs.get();
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> getNodeAttributes() throws GridSpiException {
        return F.<String, Object>asMap(
            createSpiAttributeName(WAIT_JOBS_THRESHOLD_NODE_ATTR), waitJobsThreshold,
            createSpiAttributeName(ACTIVE_JOBS_THRESHOLD_NODE_ATTR), activeJobsThreshold,
            createSpiAttributeName(MAX_STEALING_ATTEMPT_ATTR), maxStealingAttempts,
            createSpiAttributeName(MSG_EXPIRE_TIME_ATTR), msgExpireTime);
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        assertParameter(activeJobsThreshold >= 0, "activeJobsThreshold >= 0");
        assertParameter(waitJobsThreshold >= 0, "waitJobsThreshold >= 0");
        assertParameter(msgExpireTime > 0, "messageExpireTime > 0");
        assertParameter(maxStealingAttempts > 0, "maxStealingAttempts > 0");

        if (waitJobsThreshold < DFLT_WAIT_JOBS_THRESHOLD && activeJobsThreshold == DFLT_WAIT_JOBS_THRESHOLD) {
            throw new GridSpiException("'waitJobsThreshold' configuration parameter does not have effect " +
                "because 'parallelJobsNumber' is boundless by default. Make sure to limit 'parallelJobsNumber'" +
                "configuration parameter.");
        }

        // Start SPI start stopwatch.
        startStopwatch();

        // Ack parameters.
        if (log.isDebugEnabled()) {
            log.debug(configInfo("activeJobsThreshold", activeJobsThreshold));
            log.debug(configInfo("waitJobsThreshold", waitJobsThreshold));
            log.debug(configInfo("messageExpireTime", msgExpireTime));
            log.debug(configInfo("maxStealingAttempts", maxStealingAttempts));
        }

        registerMBean(gridName, this, GridJobStealingCollisionSpiMBean.class);

        // Ack start.
        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        unregisterMBean();

        // Ack ok stop.
        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void setExternalCollisionListener(GridCollisionExternalListener extLsnr) {
        this.extLsnr = extLsnr;
    }

    /** {@inheritDoc} */
    @Override public void onContextInitialized(GridSpiContext spiCtx) throws GridSpiException {
        super.onContextInitialized(spiCtx);

        Collection<GridNode> rmtNodes = getSpiContext().remoteNodes();

        for (GridNode node : rmtNodes) {
            sendMsgMap.put(node.id(), new MessageInfo());
            rcvMsgMap.put(node.id(), new MessageInfo());
        }

        nodeQueue.addAll(rmtNodes);

        getSpiContext().addLocalEventListener(discoLsnr = new GridLocalEventListener() {
            @Override public void onEvent(GridEvent evt) {
                assert evt instanceof GridDiscoveryEvent;

                GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                UUID evtNodeId = discoEvt.eventNodeId();

                GridNode node = getSpiContext().node(evtNodeId);

                switch (discoEvt.type()) {
                    case EVT_NODE_JOINED: {
                        if (node != null) {
                            nodeQueue.offer(node);

                            sendMsgMap.put(node.id(), new MessageInfo());
                            rcvMsgMap.put(node.id(), new MessageInfo());
                        }

                        break;
                    }

                    case EVT_NODE_LEFT:
                    case EVT_NODE_FAILED: {
                        Iterator<GridNode> iter = nodeQueue.iterator();

                        while (iter.hasNext()) {
                            GridNode nextNode = iter.next();

                            if (nextNode.id().equals(evtNodeId)) {
                                iter.remove();
                            }
                        }

                        sendMsgMap.remove(evtNodeId);
                        rcvMsgMap.remove(evtNodeId);

                        break;
                    }
                }
            }
        },
            EVT_NODE_FAILED,
            EVT_NODE_JOINED,
            EVT_NODE_LEFT
        );

        getSpiContext().addMessageListener(msgLsnr = new GridMessageListener() {
            @Override public void onMessage(UUID nodeId, Object msg) {
                MessageInfo info = rcvMsgMap.get(nodeId);

                if (info == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Ignoring message steal request as discovery event was not received yet for node: " +
                            nodeId);
                    }

                    return;
                }

                synchronized (info) {
                    // Increment total number of steal requests.
                    // Note that it is critical to increment total
                    // number of steal requests before resetting message info.
                    stealReqs.addAndGet((Integer)msg - info.getJobsToSteal());

                    info.reset((Integer)msg);
                }

                GridCollisionExternalListener tmp = extLsnr;

                // Let grid know that collisions should be resolved.
                if (tmp != null) {
                    tmp.onExternalCollision();
                }
            }
        },
        JOB_STEALING_COMM_TOPIC);
    }

    /** {@inheritDoc} */
    @Override public void onContextDestroyed() {
        if (discoLsnr != null) {
            getSpiContext().removeLocalEventListener(discoLsnr);
        }

        if (msgLsnr != null) {
            getSpiContext().removeMessageListener(msgLsnr, JOB_STEALING_COMM_TOPIC);
        }

        super.onContextDestroyed();
    }

    /** {@inheritDoc} */
    @Override public void onCollision(Collection<GridCollisionJobContext> waitJobs,
        Collection<GridCollisionJobContext> activeJobs) {
        assert activeJobs.size() <= activeJobsThreshold : "Amount of active jobs exceeds threshold [activeJobs=" +
            activeJobs.size() + ", activeJobsThreshold=" + activeJobsThreshold + ']';

        // Check if there are any jobs to activate or reject.
        int rejected = checkBusy(waitJobs, activeJobs);

        totalStolenJobsNum.addAndGet(rejected);

        // No point of stealing jobs if some jobs were rejected.
        if (rejected > 0) {
            if (log.isDebugEnabled()) {
                log.debug("Total count of rejected jobs: " + rejected);
            }

            return;
        }

        if (isStealingEnabled) {
            // Check if there are jobs to steal.
            checkIdle(waitJobs, activeJobs);
        }
    }

    /**
     * Check if node is busy and activate/reject proper number of jobs.
     *
     * @param waitJobs Waiting jobs.
     * @param activeJobs Active jobs.
     * @return Number of rejected jobs.
     */
    private int checkBusy(Collection<GridCollisionJobContext> waitJobs,
        Collection<GridCollisionJobContext> activeJobs) {

        int activeSize = F.size(activeJobs, RUNNING_JOBS);

        waitingCnt.set(waitJobs.size());
        runningCnt.set(activeSize);
        heldCnt.set(activeJobs.size() - activeSize);

        GridSpiContext ctx = getSpiContext();

        int activateCnt = activeJobsThreshold - activeSize;

        int cnt = 0;

        int rejected = 0;

        Collection<GridCollisionJobContext> waitPriorityJobs = sortJobs(waitJobs);

        for (GridCollisionJobContext waitCtx : waitPriorityJobs) {
            if (activateCnt > 0 && cnt < activateCnt) {
                cnt++;

                // If job was activated/cancelled by another thread, then
                // this method is no-op.
                waitCtx.activate();
            }
            else if (!waitCtx.getJob().getClass().isAnnotationPresent(GridJobStealingDisabled.class)
                && stealReqs.get() > 0) {
                // Collision count attribute.
                Integer stealingCnt = (Integer)waitCtx.getJobContext().getAttribute(STEALING_ATTEMPT_COUNT_ATTR);

                // Check that maximum stealing attempt threshold
                // has not been exceeded.
                if (stealingCnt != null) {
                    // If job exceeded failover threshold, skip it.
                    if (stealingCnt >= maxStealingAttempts) {
                        if (log.isDebugEnabled()) {
                            log.debug("Waiting job exceeded stealing attempts and won't be rejected " +
                                "(will try other jobs on waiting list): " + waitCtx);
                        }

                        continue;
                    }
                }
                else {
                    stealingCnt = 0;
                }

                // Check if allowed to reject job.
                int jobsToReject = waitPriorityJobs.size() - cnt - rejected - waitJobsThreshold;

                if (log.isDebugEnabled()) {
                    log.debug("Jobs to reject count [jobsToReject=" + jobsToReject + ", waitCtx=" + waitCtx + ']');
                }

                if (jobsToReject <= 0) {
                    break;
                }

                Integer priority = (Integer)waitCtx.getJobContext().getAttribute(STEALING_PRIORITY_ATTR);

                if (priority == null) {
                    priority = DFLT_JOB_PRIORITY;
                }

                // If we have an excess of waiting jobs, reject as many as there are
                // requested to be stolen. Note, that we use lose total steal request
                // counter to prevent excessive iteration over nodes under load.
                for (Iterator<Entry<UUID, MessageInfo>> iter = rcvMsgMap.entrySet().iterator();
                     iter.hasNext() && stealReqs.get() > 0;) {
                    Entry<UUID, MessageInfo> entry = iter.next();

                    UUID nodeId = entry.getKey();

                    // Node has left topology.
                    if (ctx.node(nodeId) == null) {
                        iter.remove();

                        continue;
                    }

                    MessageInfo info = entry.getValue();

                    synchronized (info) {
                        int jobsAsked = info.getJobsToSteal();

                        assert jobsAsked >= 0;

                        // Skip nodes that have not asked for jobs to steal.
                        if (jobsAsked == 0) {
                            // Move to next node.
                            continue;
                        }

                        // If message is expired, ignore it.
                        if (info.isExpired()) {
                            // Subtract expired messages.
                            stealReqs.addAndGet(-info.getJobsToSteal());

                            assert stealReqs.get() >= 0;

                            info.reset(0);

                            continue;
                        }

                        // Check that waiting job has thief node in topology.
                        try {
                            Collection<? extends GridNode> top = ctx.topology(waitCtx.getTaskSession(),
                                ctx.nodes());

                            boolean found = false;

                            for (GridNode node : top) {
                                if (node.id().equals(nodeId)) {
                                    found = true;

                                    break;
                                }
                            }

                            if (!found) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Thief node does not belong to task topology [thief=" + nodeId +
                                        ", task=" + waitCtx.getTaskSession() + ']');
                                }

                                continue;
                            }
                        }
                        catch (GridSpiException e) {
                            U.error(log, "Failed to check topology for job: " + waitCtx.getTaskSession(), e);

                            continue;
                        }

                        rejected++;

                        // If job was not cancelled already by another thread.
                        if (waitCtx.cancel()) {
                            stealReqs.decrementAndGet();

                            assert stealReqs.get() >= 0;

                            // Mark job as stolen.
                            waitCtx.getJobContext().setAttribute(THIEF_NODE_ATTR, nodeId);
                            waitCtx.getJobContext().setAttribute(STEALING_ATTEMPT_COUNT_ATTR, stealingCnt + 1);
                            waitCtx.getJobContext().setAttribute(STEALING_PRIORITY_ATTR, priority + 1);

                            info.reset(jobsAsked - 1);

                            if (log.isDebugEnabled()) {
                                log.debug("Rejecting job due to steal request [ctx=" + waitCtx + ", nodeId=" +
                                    nodeId + ']');
                            }
                        }

                        // Move to next job.
                        break;
                    }
                }

                assert stealReqs.get() >= 0;
            }
            // No more jobs to steal or activate.
            else {
                break;
            }
        }

        return rejected;
    }

    /**
     * Sort jobs by priority from high to lowest value.
     *
     * @param waitJobs Waiting jobs.
     * @return Sorted waiting jobs by priority.
     */
    private Collection<GridCollisionJobContext> sortJobs(Collection<GridCollisionJobContext> waitJobs) {
        List<GridCollisionJobContext> passiveList = new ArrayList<GridCollisionJobContext>(waitJobs);

        Collections.sort(passiveList, new Comparator<GridCollisionJobContext>() {
            @Override public int compare(GridCollisionJobContext o1, GridCollisionJobContext o2) {
                int p1 = getJobPriority(o1.getJobContext());
                int p2 = getJobPriority(o2.getJobContext());

                return p1 < p2 ? 1 : p1 == p2 ? 0 : -1;
            }
        });

        return passiveList;
    }

    /**
     * Gets task priority from task context. If task has no priority default
     * one will be used.
     *
     * @param ctx Job context.
     * @return Task priority.
     */
    private int getJobPriority(GridJobContext ctx) {
        assert ctx != null;

        String attrKey = STEALING_PRIORITY_ATTR;
        int dfltPriority = DFLT_JOB_PRIORITY;

        Integer p;

        try {
            p = (Integer)ctx.getAttribute(STEALING_PRIORITY_ATTR);
        }
        catch (ClassCastException e) {
            U.error(log, "Type of job context priority attribute '" + attrKey +
                "' is not java.lang.Integer (will use default priority) [type=" +
                ctx.getAttribute(attrKey).getClass() + ", dfltPriority=" + dfltPriority + ']', e);

            p = dfltPriority;
        }

        if (p == null) {
            if (log.isDebugEnabled()) {
                log.debug("Failed get priority from job context attribute '" + attrKey +
                    "' (will use default priority): " + dfltPriority);
            }

            p = dfltPriority;
        }

        assert p != null;

        return p;
    }

    /**
     * Check if the node is idle and steal as many jobs from other nodes
     * as possible.
     *
     * @param waitJobs Waiting jobs.
     * @param activeJobs Active jobs.
     */
    private void checkIdle(Collection<GridCollisionJobContext> waitJobs,
        Collection<GridCollisionJobContext> activeJobs) {
        // Check for overflow.
        int max = waitJobsThreshold + activeJobsThreshold < 0 ?
            Integer.MAX_VALUE : waitJobsThreshold + activeJobsThreshold;

        int jobsToSteal = max - (waitJobs.size() + activeJobs.size());

        if (log.isDebugEnabled()) {
            log.debug("Total number of jobs to be stolen: " + jobsToSteal);
        }

        if (jobsToSteal > 0) {
            int jobsLeft = jobsToSteal;

            GridNode next;

            int nodeCnt = getSpiContext().remoteNodes().size();

            int idx = 0;

            while (jobsLeft > 0 && idx++ < nodeCnt && (next = nodeQueue.poll()) != null) {
                int delta = 0;

                try {
                    // Remote node does not have attributes - do not still from it.
                    if (!F.isEmpty(stealAttrs)) {
                        if (next.attributes() == null || !U.containsAll(next.attributes(), stealAttrs)) {
                            if (log.isDebugEnabled()) {
                                log.debug("Skip node as it does not have all attributes: " + next.id());
                            }

                            continue;
                        }
                    }

                    MessageInfo msgInfo = sendMsgMap.get(next.id());

                    if (msgInfo == null) {
                        if (log.isDebugEnabled()) {
                            log.debug("Failed to find message info for node: " + next.id());
                        }

                        // Node left topology.
                        continue;
                    }

                    Integer waitThreshold =
                        (Integer)next.attribute(createSpiAttributeName(WAIT_JOBS_THRESHOLD_NODE_ATTR));

                    if (waitThreshold == null) {
                        U.error(log, "Remote node is not configured with GridJobStealingCollisionSpi and jobs will not " +
                            "be stolen from it (you must stop it and update its configuration to use " +
                            "GridJobStealingCollisionSpi): " + next);

                        continue;
                    }

                    delta = next.metrics().getCurrentWaitingJobs() - waitThreshold;

                    if (log.isDebugEnabled()) {
                        log.debug("Maximum number of jobs to steal from node [jobsToSteal=" + delta + ", node=" +
                            next.id() + ']');
                    }

                    // Nothing to steal from this node.
                    if (delta <= 0) {
                        continue;
                    }

                    synchronized (msgInfo) {
                        if (!msgInfo.isExpired() && msgInfo.getJobsToSteal() > 0) {
                            // Count messages being waited for as present.
                            jobsLeft -= msgInfo.getJobsToSteal();

                            continue;
                        }

                        if (jobsLeft < delta) {
                            delta = jobsLeft;
                        }

                        jobsLeft -= delta;

                        msgInfo.reset(delta);
                    }

                    // Send request to remote node to still jobs.
                    // Message is a plain integer represented by 'delta'.
                    getSpiContext().send(next, delta, JOB_STEALING_COMM_TOPIC);
                }
                catch (GridSpiException e) {
                    U.error(log, "Failed to send job stealing message to node: " + next, e);

                    // Rollback.
                    jobsLeft += delta;
                }
                finally {
                    // If node is alive, add back to the end of the queue.
                    if (getSpiContext().node(next.id()) != null) {
                        nodeQueue.offer(next);
                    }
                }
            }
        }
    }

    /** */
    private class MessageInfo {
        /** */
        private int jobsToSteal;

        /** */
        private long timestamp = System.currentTimeMillis();

        /**
         * @return Job to steal.
         */
        int getJobsToSteal() { return jobsToSteal; }

        /**
         * @return Message send time.
         */
        long getTimestamp() { return timestamp; }

        /**
         * @return {@code True} if message is expired.
         */
        boolean isExpired() { return jobsToSteal > 0 && System.currentTimeMillis() - timestamp >= msgExpireTime; }

        /**
         * @param jobsToSteal Jobs to steal.
         */
        void setJobsToSteal(int jobsToSteal) { this.jobsToSteal = jobsToSteal; }

        /**
         * @param jobsToSteal Jobs to steal.
         */
        void reset(int jobsToSteal) {
            this.jobsToSteal = jobsToSteal;

            timestamp = System.currentTimeMillis();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MessageInfo.class, this);
        }
    }

    /** {@inheritDoc} */
    @Override protected List<String> getConsistentAttributeNames() {
        List<String> attrs = new ArrayList<String>(2);

        attrs.add(createSpiAttributeName(MAX_STEALING_ATTEMPT_ATTR));
        attrs.add(createSpiAttributeName(MSG_EXPIRE_TIME_ATTR));

        return attrs;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobStealingCollisionSpi.class, this);
    }
}
