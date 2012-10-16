// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.job;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.collision.*;
import org.gridgain.grid.kernal.managers.communication.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.managers.discovery.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.kernal.processors.jobmetrics.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.spi.collision.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.managers.communication.GridIoPolicy.*;

/**
 * Responsible for all grid job execution and communication.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"deprecation"})
public class GridJobProcessor extends GridProcessorAdapter {
    /** */
    private static final int CANCEL_REQS_NUM = 1000;

    /** */
    private final GridMarshaller marsh;

    /** */
    private final Map<UUID, GridJobWorker> activeJobs = new LinkedHashMap<UUID, GridJobWorker>();

    /** */
    private final Map<UUID, GridJobWorker> passiveJobs = new LinkedHashMap<UUID, GridJobWorker>();

    /** */
    private final Map<UUID, GridJobWorker> cancelledJobs = new LinkedHashMap<UUID, GridJobWorker>();

    /** */
    private final Collection<UUID> cancelReqs = new GridBoundedLinkedHashSet<UUID>(CANCEL_REQS_NUM);

    /** */
    private final GridJobEventListener evtLsnr;

    /** */
    private final GridMessageListener cancelLsnr;

    /** */
    private final GridMessageListener reqLsnr;

    /** */
    private final GridLocalEventListener discoLsnr;

    /** */
    private final GridCollisionExternalListener colLsnr;

    /** */
    private int callCnt;

    /** Needed for statistics. */
    private final AtomicInteger finishedJobsCnt = new AtomicInteger(0);

    /** Total job execution time (unaccounted for in metrics). */
    private final AtomicLong finishedJobsTime = new AtomicLong(0);

    /** */
    private final AtomicReference<CollisionSnapshot> lastSnapshot = new AtomicReference<CollisionSnapshot>(null);

    /**
     * This flag is used a guard to prevent a new collision resolution when
     * there were no changes since last one.
     */
    private boolean collisionsHandled;

    /** */
    private boolean stopping;

    /** */
    private final Object mux = new Object();

    /**
     * @param ctx Kernal context.
     */
    public GridJobProcessor(GridKernalContext ctx) {
        super(ctx);

        marsh = ctx.config().getMarshaller();

        evtLsnr = new JobEventListener();
        cancelLsnr = new JobCancelListener();
        reqLsnr = new JobExecutionListener();
        discoLsnr = new JobDiscoveryListener();
        colLsnr = new CollisionExternalListener();
    }

    /** */
    @Override public void start() {
        ctx.collision().setCollisionExternalListener(colLsnr);

        GridIoManager ioMgr = ctx.io();

        ioMgr.addMessageListener(TOPIC_CANCEL, cancelLsnr);
        ioMgr.addMessageListener(TOPIC_JOB, reqLsnr);

        if (log.isDebugEnabled())
            log.debug("Job processor started.");
    }

    /**
     * Registers listener with discovery SPI. Note that discovery listener
     * registration cannot be done during start because job processor
     * starts before discovery manager.
     *
     * {@inheritDoc}
     */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Override public void onKernalStart() throws GridException {
        super.onKernalStart();

        ctx.event().addLocalEventListener(discoLsnr,
            EVT_NODE_FAILED,
            EVT_NODE_JOINED,
            EVT_NODE_LEFT,
            EVT_NODE_METRICS_UPDATED);

        GridDiscoveryManager discoMgr = ctx.discovery();

        List<GridJobWorker> jobsToCancel;
        List<GridJobWorker> jobsToReject;

        synchronized (mux) {
            jobsToReject = new ArrayList<GridJobWorker>();

            for (Iterator<GridJobWorker> iter = passiveJobs.values().iterator(); iter.hasNext();) {
                GridJobWorker job = iter.next();

                if (discoMgr.node(job.getTaskNodeId()) == null) {
                    iter.remove();

                    jobsToReject.add(job);
                }
            }

            jobsToCancel = new ArrayList<GridJobWorker>();

            for (Iterator<GridJobWorker> iter = activeJobs.values().iterator(); iter.hasNext();) {
                GridJobWorker job = iter.next();

                if (discoMgr.node(job.getTaskNodeId()) == null) {
                    iter.remove();

                    cancelledJobs.put(job.getJobId(), job);

                    jobsToCancel.add(job);
                }
            }

            collisionsHandled = false;
        }

        // Passive jobs.
        for (GridJobWorker job : jobsToReject) {
            GridException e = new GridTopologyException("Originating task node left grid [nodeId=" +
                job.getTaskNodeId() + ", jobSes=" + job.getSession() + ", job=" + job + ']');

            log.error(e.getMessage(), e);

            finishJob(job, null, e, false);
        }

        // Cancelled jobs.
        for (GridJobWorker job : jobsToCancel)
            cancelJob(job, false);

        // Force collision handling on node startup.
        handleCollisions();
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) {
        // Clear collections.
        synchronized (mux) {
            activeJobs.clear();
            cancelledJobs.clear();
            cancelReqs.clear();
        }

        if (log.isDebugEnabled())
            log.debug("Job processor stopped.");
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop(boolean cancel, boolean wait) {
        // Stop receiving new requests and sending responses.
        GridIoManager commMgr = ctx.io();

        commMgr.removeMessageListener(TOPIC_JOB, reqLsnr);
        commMgr.removeMessageListener(TOPIC_CANCEL, cancelLsnr);

        // Ignore external collision events.
        ctx.collision().setCollisionExternalListener(null);

        List<GridJobWorker> jobsToReject;
        List<GridJobWorker> jobsToCancel;
        List<GridJobWorker> jobsToJoin;

        synchronized (mux) {
            // Set stopping flag first.
            stopping = true;

            // Wait for all listener callbacks to complete.
            while (true) {
                assert callCnt >= 0;

                // This condition is taken out of the loop to avoid
                // potentially wrong optimization by the compiler of
                // moving field access out of the loop causing this loop
                // to never exit.
                if (callCnt == 0)
                    break;

                if (log.isDebugEnabled())
                    log.debug("Waiting for job calls to finish: " + callCnt);

                try {
                    // Release mux.
                    mux.wait(5000);
                }
                catch (InterruptedException e) {
                    U.error(log, "Got interrupted while stopping (shutdown is incomplete)", e);
                }
            }

            jobsToReject = new ArrayList<GridJobWorker>(passiveJobs.values());

            passiveJobs.clear();

            jobsToCancel = new ArrayList<GridJobWorker>(activeJobs.values());
            jobsToJoin = new ArrayList<GridJobWorker>(cancelledJobs.values());

            jobsToJoin.addAll(jobsToCancel);
        }

        // Rejected jobs.
        for (GridJobWorker job : jobsToReject)
            rejectJob(job);

        // Cancel only if we force grid to stop
        if (cancel)
            for (GridJobWorker job : jobsToCancel) {
                job.onStopping();

                cancelJob(job, false);
            }

        U.join(jobsToJoin, log);

        // Ignore topology changes.
        ctx.event().removeLocalEventListener(discoLsnr);

        if (log.isDebugEnabled())
            log.debug("Job processor will not process any more jobs due to kernal stopping.");
    }

    /**
     * Gets active job.
     *
     * @param jobId Job ID.
     * @return Active job.
     */
    public GridJobWorker activeJob(UUID jobId) {
        assert jobId != null;

        synchronized (mux) {
            return activeJobs.get(jobId);
        }
    }

    /**
     * @param job Rejected job.
     */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    private static void rejectJob(GridJobWorker job) {
        GridException e = new GridExecutionRejectedException("Job was cancelled before execution [taskSesId=" +
            job.getSession().getId() + ", jobId=" + job.getJobId() + ", job=" + job.getJob() + ']');

        job.finishJob(null, e, true);
    }

    /**
     * @param job Canceled job.
     * @param system System flag.
     */
    private static void cancelJob(GridJobWorker job, boolean system) {
        job.cancel(system);
    }

    /**
     * @param job Finished job.
     * @param res Job's result.
     * @param ex Optional exception.
     * @param sendReply Send reply flag.
     */
    private static void finishJob(GridJobWorker job, Serializable res, GridException ex, boolean sendReply) {
        job.finishJob(res, ex, sendReply);
    }

    /**
     * @param dep Deployment to release.
     */
    private void release(GridDeployment dep) {
        dep.release();

        if (dep.isObsolete())
            ctx.resource().onUndeployed(dep);
    }

    /**
     * @param ses Session.
     * @param attrs Attributes.
     * @throws GridException If failed.
     */
    public void setAttributes(GridTaskSessionInternal ses, Map<?, ?> attrs) throws GridException {
        long timeout = ses.getEndTime() - System.currentTimeMillis();

        if (timeout <= 0) {
            U.warn(log, "Task execution timed out (remote session attributes won't be set): " + ses);

            return;
        }

        if (log.isDebugEnabled())
            log.debug("Setting session attribute(s) from job: " + ses);

        GridNode taskNode = ctx.discovery().node(ses.getTaskNodeId());

        if (taskNode == null)
            throw new GridException("Node that originated task execution has left grid: " +
                ses.getTaskNodeId());

        GridByteArrayList serAttrs = U.marshal(marsh, attrs);

        String topic = TOPIC_TASK.name(ses.getJobId(), ctx.discovery().localNode().getId());

        ctx.io().sendOrderedMessage(
            taskNode,
            topic, // Job topic.
            ctx.io().getNextMessageId(topic, taskNode.id()),
            new GridTaskSessionRequest(ses.getId(), ses.getJobId(), serAttrs),
            SYSTEM_POOL,
            timeout);
    }

    /**
     * @param ses Session.
     * @return Siblings.
     * @throws GridException If failed.
     */
    public Collection<GridJobSibling> requestJobSiblings(GridTaskSession ses) throws GridException {
        assert ses != null;

        GridNode taskNode = ctx.discovery().node(ses.getTaskNodeId());

        if (taskNode == null)
            throw new GridException("Node that originated task execution has left grid: " +
                ses.getTaskNodeId());

        GridIoFuture fut = ctx.io().sendSync(taskNode,
            TOPIC_JOB_SIBLINGS, new GridJobSiblingsRequest(ses.getId()), ctx.config().getNetworkTimeout(),
            SYSTEM_POOL, null);

        GridIoResult res;

        try {
            res = fut.getFirstResult();
        }
        catch (InterruptedException e) {
            throw new GridException("Interrupted while job siblings response waiting: " + ses, e);
        }

        if (res.isSuccess())
            return res.getResult();
        else
            throw new GridException("Failed to get job siblings (task node threw exception): " + ses,
                res.getException());
    }

    /** */
    private void handleCollisions() {
        CollisionSnapshot snapshot;

        synchronized (mux) {
            // Don't do anything if collisions were handled by another thread.
            if (collisionsHandled)
                return;

            snapshot = new CollisionSnapshot();

            // Create collection of passive contexts.
            for (GridJobWorker job : passiveJobs.values())
                snapshot.addPassive(job);

            // Create collection of active contexts. Note that we don't
            // care about cancelled jobs that are still running.
            for (GridJobWorker job : activeJobs.values())
                snapshot.addActive(job);

            // Even though lastSnapshot is atomic, we still set
            // it inside of synchronized block because we want
            // to maintain proper order of snapshots.
            lastSnapshot.set(snapshot);

            // Mark collisions as handled to make sure that none will
            // happen if job topology did not change.
            collisionsHandled = true;
        }

        // Outside of synchronization handle whichever snapshot was added last
        if ((snapshot = lastSnapshot.getAndSet(null)) != null)
            snapshot.onCollision();
    }

    /** */
    private class CollisionSnapshot {
        /** */
        private final Collection<GridCollisionJobContext> passiveCtxs = new LinkedList<GridCollisionJobContext>();

        /** */
        private final Collection<GridCollisionJobContext> activeCtxs = new LinkedList<GridCollisionJobContext>();

        /** */
        private int startedCtr;

        /** */
        private int activeCtr;

        /** */
        private int passiveCtr;

        /** */
        private int cancelCtr;

        /** */
        private int rejectCtr;

        /** */
        private long totalWaitTime;

        /** */
        private CollisionJobContext oldestPassive;

        /** */
        private CollisionJobContext oldestActive;

        /**
         * @param job Passive job.
         */
        void addPassive(GridJobWorker job) {
            passiveCtxs.add(new CollisionJobContext(job, true));
        }

        /**
         * @param job Active job.
         */
        void addActive(GridJobWorker job) {
            activeCtxs.add(new CollisionJobContext(job, false));
        }

        /**
         * Handles collisions.
         */
        void onCollision() {
            // Invoke collision SPI.
            ctx.collision().onCollision(passiveCtxs, activeCtxs);

            // Process waiting list.
            for (GridCollisionJobContext c : passiveCtxs) {
                CollisionJobContext jobCtx = (CollisionJobContext)c;

                if (jobCtx.isCancelled()) {
                    rejectJob(jobCtx.getJobWorker());

                    rejectCtr++;
                }
                else {
                    if (jobCtx.isActivated()) {
                        totalWaitTime += jobCtx.getJobWorker().getQueuedTime();

                        try {
                            // Execute in a different thread.
                            ctx.config().getExecutorService().execute(jobCtx.getJobWorker());

                            startedCtr++;

                            activeCtr++;
                        }
                        catch (RejectedExecutionException e) {
                            synchronized (mux) {
                                activeJobs.remove(jobCtx.getJobWorker().getJobId());
                            }

                            GridException e2 = new GridExecutionRejectedException(
                                "Job was cancelled before execution [jobSes=" + jobCtx.getJobWorker().getSession() +
                                    ", job=" + jobCtx.getJobWorker().getJob() + ']', e);

                            finishJob(jobCtx.getJobWorker(), null, e2, true);
                        }
                    }
                    // Job remains on passive list.
                    else {
                        passiveCtr++;
                    }

                    // Since jobs are ordered, first job is the oldest passive job.
                    if (oldestPassive == null)
                        oldestPassive = jobCtx;
                }
            }

            // Process active list.
            for (GridCollisionJobContext c : activeCtxs) {
                CollisionJobContext ctx = (CollisionJobContext)c;

                if (ctx.isCancelled()) {
                    boolean isCancelled = ctx.getJobWorker().isCancelled();

                    // We do apply cancel as many times as user cancel job.
                    cancelJob(ctx.getJobWorker(), false);

                    // But we don't increment number of cancelled jobs if it
                    // was already cancelled.
                    if (!isCancelled)
                        cancelCtr++;
                }
                // Job remains on active list.
                else {
                    if (oldestActive == null)
                        oldestActive = ctx;

                    activeCtr++;
                }
            }

            updateCollisionMetrics();
        }

        /** */
        private void updateCollisionMetrics() {
            int curCancelled = cancelCtr;

            GridJobMetricsSnapshot m = new GridJobMetricsSnapshot();

            m.setActiveJobs(activeCtr);
            m.setCancelJobs(curCancelled);
            m.setMaximumExecutionTime(oldestActive == null ? 0 : oldestActive.getJobWorker().getExecuteTime());
            m.setMaximumWaitTime(oldestPassive == null ? 0 : oldestPassive.getJobWorker().getQueuedTime());
            m.setPassiveJobs(passiveCtr);
            m.setRejectJobs(rejectCtr);
            m.setWaitTime(totalWaitTime);
            m.setStartedJobs(startedCtr);

            // Get and reset finished jobs metrics.
            m.setFinishedJobs(finishedJobsCnt.getAndSet(0));
            m.setExecutionTime(finishedJobsTime.getAndSet(0));

            // CPU load.
            m.setCpuLoad(ctx.localMetric().getMetrics().getCurrentCpuLoad());

            ctx.jobMetric().addSnapshot(m);
        }

        /**
         *
         */
        private class CollisionJobContext extends GridCollisionJobContextAdapter {
            /** */
            private final boolean isPassive;

            /** */
            private boolean isActivated;

            /** */
            private boolean isCancelled;

            /**
             * @param jobWorker Job Worker.
             * @param isPassive {@code True} if job is active.
             */
            CollisionJobContext(GridJobWorker jobWorker, boolean isPassive) {
                super(jobWorker);

                this.isPassive = isPassive;
            }

            /** {@inheritDoc} */
            @Override public boolean activate() {
                synchronized (mux) {
                    if (passiveJobs.remove(getJobWorker().getJobId()) != null) {
                        activeJobs.put(getJobWorker().getJobId(), getJobWorker());

                        isActivated = true;
                    }

                    return isActivated;
                }
            }

            /** {@inheritDoc} */
            @Override public boolean cancel() {
                synchronized (mux) {
                    // If waiting job being rejected.
                    if (isPassive) {
                        isCancelled = passiveJobs.remove(getJobWorker().getJobId()) != null;
                    }
                    // If active job being cancelled.
                    else if (activeJobs.remove(getJobWorker().getJobId()) != null) {
                        cancelledJobs.put(getJobWorker().getJobId(), getJobWorker());

                        isCancelled = true;
                    }

                    return isCancelled;
                }
            }

            /**
             * {@code True} if context was activated.
             *
             * @return {@code True} if context was activated.
             */
            public boolean isActivated() {
                synchronized (mux) {
                    return isActivated;
                }
            }

            /**
             * {@code True} if context was cancelled.
             *
             * @return {@code True} if context was cancelled.
             */
            public boolean isCancelled() {
                synchronized (mux) {
                    return isCancelled;
                }
            }

            /** {@inheritDoc} */
            @Override public String toString() {
                return S.toString(CollisionJobContext.class, this);
            }
        }
    }

    /**
     *
     */
    private class CollisionExternalListener implements GridCollisionExternalListener {
        /** {@inheritDoc} */
        @Override public void onExternalCollision() {
            if (log.isDebugEnabled())
                log.debug("Received external collision event.");

            synchronized (mux) {
                if (stopping) {
                    if (log.isInfoEnabled())
                        log.info("Received external collision notification while stopping grid (will ignore).");

                    return;
                }

                collisionsHandled = false;

                callCnt++;
            }

            try {
                handleCollisions();
            }
            finally {
                synchronized (mux) {
                    callCnt--;

                    if (callCnt == 0)
                        mux.notifyAll();
                }
            }
        }
    }

    /**
     * Handles job state changes.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     */
    private class JobEventListener implements GridJobEventListener {
        private final GridMessageListener sesLsnr = new JobSessionListener();

        /** {@inheritDoc} */
        @Override public void onJobStarted(GridJobWorker worker) {
            // Register for timeout notifications.
            ctx.timeout().addTimeoutObject(worker);

            // Register session request listener for this job.
            ctx.io().addMessageListener(worker.getJobTopic(), sesLsnr);

            // Register checkpoints.
            ctx.checkpoint().onSessionStart(worker.getSession());
        }

        /** {@inheritDoc} */
        @Override public void onJobFinished(GridJobWorker worker) {
            // If last job for the task on this node.
            if (ctx.session().removeSession(worker.getSession().getId()))
                worker.getSession().onClosed();

            // Unregister session request listener for this jobs.
            ctx.io().removeMessageListener(worker.getJobTopic());

            // Unregister message IDs used for sending.
            ctx.io().removeMessageId(worker.getTaskTopic());

            // Unregister checkpoints.
            ctx.checkpoint().onSessionEnd(worker.getSession(), worker.isSystemCanceled());

            // Unregister from timeout notifications.
            ctx.timeout().removeTimeoutObject(worker);

            release(worker.getDeployment());

            synchronized (mux) {
                assert !passiveJobs.containsKey(worker.getJobId());

                activeJobs.remove(worker.getJobId());
                cancelledJobs.remove(worker.getJobId());

                collisionsHandled = false;

                callCnt++;
            }

            try {
                // Increment job execution counter. This counter gets
                // reset once this job will be accounted for in metrics.
                finishedJobsCnt.incrementAndGet();

                // Increment job execution time. This counter gets
                // reset once this job will be accounted for in metrics.
                finishedJobsTime.addAndGet(worker.getExecuteTime());

                handleCollisions();
            }
            finally {
                synchronized (mux) {
                    callCnt--;

                    if (callCnt == 0)
                        mux.notifyAll();
                }
            }
        }
    }

    /**
     * Handles task and job cancellations.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     */
    private class JobCancelListener implements GridMessageListener {
        @Override public void onMessage(UUID nodeId, Object msg) {
            GridJobCancelRequest cancelMsg = (GridJobCancelRequest)msg;

            assert nodeId != null;
            assert cancelMsg != null;

            if (log.isDebugEnabled())
                log.debug("Received cancellation request message [cancelMsg=" + cancelMsg + ", nodeId=" + nodeId + ']');

            Collection<GridJobWorker> jobsToCancel = new ArrayList<GridJobWorker>();
            Collection<GridJobWorker> jobsToReject = new ArrayList<GridJobWorker>();

            synchronized (mux) {
                if (stopping) {
                    if (log.isDebugEnabled())
                        log.debug("Received task cancellation request while stopping grid (will ignore): " + cancelMsg);

                    return;
                }

                callCnt++;

                // Put either job id or session id (they are unique).
                if (cancelMsg.getJobId() != null)
                    cancelReqs.add(cancelMsg.getJobId());
                else
                    cancelReqs.add(cancelMsg.getSessionId());

                // Passive jobs.
                for (Iterator<GridJobWorker> iter = passiveJobs.values().iterator(); iter.hasNext();) {
                    GridJobWorker job = iter.next();

                    if (job.getSession().getId().equals(cancelMsg.getSessionId())) {
                        // If job session ID is provided, then match it too.
                        if (cancelMsg.getJobId() != null) {
                            if (job.getJobId().equals(cancelMsg.getJobId())) {
                                iter.remove();

                                jobsToReject.add(job);

                                collisionsHandled = false;
                            }
                        }
                        else {
                            iter.remove();

                            jobsToReject.add(job);

                            collisionsHandled = false;
                        }
                    }
                }

                // Active jobs.
                for (Iterator<GridJobWorker> iter = activeJobs.values().iterator(); iter.hasNext();) {
                    GridJobWorker job = iter.next();

                    if (job.getSession().getId().equals(cancelMsg.getSessionId())) {
                        // If job session ID is provided, then match it too.
                        if (cancelMsg.getJobId() != null) {
                            if (job.getJobId().equals(cancelMsg.getJobId())) {
                                iter.remove();

                                cancelledJobs.put(job.getJobId(), job);

                                jobsToCancel.add(job);

                                collisionsHandled = false;
                            }
                        }
                        else {
                            iter.remove();

                            cancelledJobs.put(job.getJobId(), job);

                            jobsToCancel.add(job);

                            collisionsHandled = false;
                        }
                    }
                }
            }

            try {
                for (GridJobWorker job : jobsToReject)
                    rejectJob(job);

                for (GridJobWorker job : jobsToCancel)
                    cancelJob(job, cancelMsg.isSystem());

                handleCollisions();
            }
            finally {
                synchronized (mux) {
                    callCnt--;

                    if (callCnt == 0)
                        mux.notifyAll();
                }
            }
        }
    }

    /**
     * Handles job execution requests.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class JobExecutionListener implements GridMessageListener {
        @SuppressWarnings({"unchecked", "ThrowableInstanceNeverThrown"})
        @Override public void onMessage(UUID nodeId, Object msg) {
            assert nodeId != null;
            assert msg != null;

            if (log.isDebugEnabled())
                log.debug("Received job request message [msg=" + msg + ", nodeId=" + nodeId + ']');

            synchronized (mux) {
                if (stopping) {
                    if (log.isInfoEnabled())
                        log.info("Received job execution request while stopping this node (will ignore): " + msg);

                    return;
                }

                callCnt++;
            }

            try {
                GridJobExecuteRequest req = (GridJobExecuteRequest)msg;

                long endTime = req.getCreateTime() + req.getTimeout();

                // Account for overflow.
                if (endTime < 0)
                    endTime = Long.MAX_VALUE;

                List<GridJobSibling> siblings = !req.isDynamicSiblings() ?
                    new ArrayList<GridJobSibling>(req.getSiblings()) : null;

                GridDeployment dep = ctx.deploy().getGlobalDeployment(
                    req.getDeploymentMode(),
                    req.getTaskName(),
                    req.getTaskClassName(),
                    req.getSequenceNumber(),
                    req.getUserVersion(),
                    req.getTaskNodeId(),
                    req.getClassLoaderId(),
                    req.getLoaderParticipants(),
                    null);

                if (dep == null) {
                    // Check local tasks.
                    for (Map.Entry<String, GridDeployment> d : ctx.task().getUsedDeploymentMap().entrySet()) {
                        if (d.getValue().classLoaderId().equals(req.getClassLoaderId())) {
                            assert d.getValue().isLocal();

                            dep = d.getValue();

                            break;
                        }
                    }
                }

                if (dep != null && dep.acquire()) {
                    GridJobSessionImpl jobSes;
                    GridJobContextImpl jobCtx;

                    try {
                        // Note that we unmarshall session/job attributes here with proper class loader.
                        GridTaskSessionImpl taskSes = ctx.session().createTaskSession(
                            req.getSessionId(),
                            nodeId,
                            req.getTaskName(),
                            req.getUserVersion(),
                            req.getSequenceNumber(),
                            req.getTaskClassName(),
                            req.getStartTaskTime(),
                            endTime,
                            siblings,
                            (Map<Object, Object>)U.unmarshal(marsh, req.getSessionAttributes(), dep.classLoader())
                        );

                        taskSes.setCheckpointSpi(req.getCheckpointSpi());
                        taskSes.setClassLoader(dep.classLoader());

                        jobSes = new GridJobSessionImpl(ctx, taskSes, req.getJobId());

                        jobCtx = new GridJobContextImpl(ctx, req.getJobId(),
                            (Map<? extends Serializable, ? extends Serializable>)U.unmarshal(
                                marsh, req.getJobAttributes(), dep.classLoader()));
                    }
                    catch (GridException e) {
                        GridException ex = new GridException("Failed to deserialize task  attributes [taskName=" +
                            req.getTaskName() + ", taskClsName=" + req.getTaskClassName() + ", codeVer=" +
                            req.getUserVersion() + ", taskClsLdr=" + dep.classLoader() + ']');

                        log.error(ex.getMessage(), e);

                        handleException(req, ex, endTime);

                        release(dep);

                        return;
                    }

                    GridJobWorker job = new GridJobWorker(
                        ctx,
                        dep,
                        req.getCreateTime(),
                        jobSes,
                        jobCtx,
                        req.getJobBytes(),
                        req.getTaskNodeId(),
                        evtLsnr);

                    if (job.initialize(dep, dep.deployedClass(req.getTaskClassName()))) {
                        synchronized (mux) {
                            // Check if job or task has already been canceled.
                            if (cancelReqs.contains(req.getJobId()) ||
                                cancelReqs.contains(req.getSessionId())) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Received execution request for the cancelled job (will ignore) " +
                                        "[srcNode=" + req.getTaskNodeId() + ", jobId=" + req.getJobId() +
                                        ", sesId=" + req.getSessionId() + ']');
                                }

                                return;
                            }
                            else if (passiveJobs.containsKey(job.getJobId()) ||
                                activeJobs.containsKey(job.getJobId()) ||
                                cancelledJobs.containsKey(job.getJobId())) {
                                U.error(log, "Received computation request with duplicate job ID " +
                                    "(could be network malfunction, source node may hang if task timeout was not set) " +
                                    "[srcNode=" + req.getTaskNodeId() +
                                    ", jobId=" + req.getJobId() +
                                    ", sesId=" + req.getSessionId() +
                                    ", locNodeId=" + ctx.localNodeId() +
                                    ", isActive=" + activeJobs.containsKey(job.getJobId()) +
                                    ", isPassive=" + passiveJobs.containsKey(job.getJobId()) +
                                    ", isCancelled=" + cancelledJobs.containsKey(job.getJobId()) +
                                    ']');

                                return;
                            }
                            else {
                                passiveJobs.put(job.getJobId(), job);

                                collisionsHandled = false;
                            }
                        }

                        handleCollisions();
                    }
                }
                // If deployment is null.
                else {
                    GridException ex = new GridException("Task was not deployed or was redeployed since task " +
                        "execution [taskName=" + req.getTaskName() + ", taskClsName=" + req.getTaskClassName() +
                        ", codeVer=" + req.getUserVersion() + ", clsLdrId=" + req.getClassLoaderId() + ", seqNum=" +
                        req.getSequenceNumber() + ", depMode=" + req.getDeploymentMode() + ", dep=" + dep + ']');

                    log.error(ex.getMessage(), ex);

                    handleException(req, ex, endTime);
                }
            }
            finally {
                synchronized (mux) {
                    callCnt--;

                    if (callCnt == 0)
                        mux.notifyAll();
                }
            }
        }

        /**
         * Handles errors that happened prior to job creation.
         *
         * @param req Job execution request.
         * @param ex Exception that happened.
         * @param endTime Job end time.
         */
        private void handleException(GridJobExecuteRequest req, GridException ex, long endTime) {
            UUID locNodeId = ctx.localNodeId();

            GridNode senderNode = ctx.discovery().node(req.getTaskNodeId());

            try {
                GridJobExecuteResponse jobRes = new GridJobExecuteResponse(
                    locNodeId,
                    req.getSessionId(),
                    req.getJobId(),
                    U.marshal(marsh, ex),
                    U.marshal(marsh, null),
                    U.marshal(marsh, Collections.emptyMap()),
                    false);

                // Job response topic.
                String topic = TOPIC_TASK.name(req.getJobId(), locNodeId);

                long timeout = endTime - System.currentTimeMillis();

                if (timeout <= 0)
                    // Ignore the actual timeout and send response anyway.
                    timeout = 1;

                if (senderNode == null) {
                    U.error(log, "Failed to reply to sender node because it left grid [nodeId=" + req.getTaskNodeId() +
                        ", jobId=" + req.getJobId() + ']');

                    if (ctx.event().isRecordable(EVT_JOB_FAILED)) {
                        GridJobEvent evt = new GridJobEvent();

                        evt.jobId(req.getJobId());
                        evt.message("Job reply failed (original task node left grid): " + req.getJobId());
                        evt.nodeId(locNodeId);
                        evt.taskName(req.getTaskName());
                        evt.taskSessionId(req.getSessionId());
                        evt.type(EVT_JOB_FAILED);
                        evt.taskNodeId(req.getTaskNodeId());

                        // Record job reply failure.
                        ctx.event().record(evt);
                    }
                }
                else {
                    // Send response to designated job topic.
                    ctx.io().sendOrderedMessage(
                        senderNode,
                        topic,
                        ctx.io().getNextMessageId(topic, senderNode.id()),
                        jobRes,
                        SYSTEM_POOL,
                        timeout);
                }
            }
            catch (GridException e) {
                // The only option here is to log, as we must assume that resending will fail too.
                if (isDeadNode(req.getTaskNodeId()))
                    // Avoid stack trace for left nodes.
                    U.error(log, "Failed to reply to sender node because it left grid [nodeId=" + req.getTaskNodeId() +
                        ", jobId=" + req.getJobId() + ']');
                else {
                    assert senderNode != null;

                    U.error(log, "Error sending reply for job [nodeId=" + senderNode.id() + ", jobId=" +
                        req.getJobId() + ']', e);
                }

                if (ctx.event().isRecordable(EVT_JOB_FAILED)) {
                    GridJobEvent evt = new GridJobEvent();

                    evt.jobId(req.getJobId());
                    evt.message("Failed to send reply for job: " + req.getJobId());
                    evt.nodeId(locNodeId);
                    evt.taskName(req.getTaskName());
                    evt.taskSessionId(req.getSessionId());
                    evt.type(EVT_JOB_FAILED);
                    evt.taskNodeId(req.getTaskNodeId());

                    // Record job reply failure.
                    ctx.event().record(evt);
                }
            }
        }

        /**
         * Checks whether node is alive or dead.
         *
         * @param uid UID of node to check.
         * @return {@code true} if node is dead, {@code false} is node is alive.
         */
        private boolean isDeadNode(UUID uid) {
            return ctx.discovery().node(uid) == null || !ctx.discovery().pingNode(uid);
        }
    }

    /** */
    private class JobSessionListener implements GridMessageListener {
        @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
        @Override public void onMessage(UUID nodeId, Object msg) {
            assert nodeId != null;
            assert msg != null;

            if (log.isDebugEnabled())
                log.debug("Received session attribute request message [msg=" + msg + ", nodeId=" + nodeId + ']');

            GridTaskSessionRequest req = (GridTaskSessionRequest)msg;

            synchronized (mux) {
                if (stopping) {
                    if (log.isInfoEnabled())
                        log.info("Received job session request while stopping grid (will ignore): " + req);

                    return;
                }

                callCnt++;
            }

            try {
                GridTaskSessionImpl ses = ctx.session().getSession(req.getSessionId());

                if (ses == null) {
                    U.warn(log, "Received job session request for non-existing session: " + req);

                    return;
                }

                Map<Object, Object> attrs = U.unmarshal(marsh, req.getAttributes(), ses.getClassLoader());

                if (ctx.event().isRecordable(EVT_TASK_SESSION_ATTR_SET)) {
                    GridTaskEvent evt = new GridTaskEvent();

                    evt.message("Changed attributes: " + attrs);
                    evt.nodeId(ctx.discovery().localNode().getId());
                    evt.taskName(ses.getTaskName());
                    evt.taskSessionId(ses.getId());
                    evt.type(EVT_TASK_SESSION_ATTR_SET);

                    ctx.event().record(evt);
                }

                synchronized (ses) {
                    ses.setInternal(attrs);
                }
            }
            catch (GridException e) {
                U.error(log, "Failed to deserialize session attributes.", e);
            }
            finally {
                synchronized (mux) {
                    callCnt--;

                    if (callCnt == 0)
                        mux.notifyAll();
                }
            }
        }
    }

    /**
     * Listener to node discovery events.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class JobDiscoveryListener implements GridLocalEventListener {
        /**
         * Counter used to determine whether all nodes updated metrics or not.
         * This counter is reset every time collisions are handled.
         */
        private int metricsUpdateCntr;

        @SuppressWarnings({"ThrowableInstanceNeverThrown"})
        @Override public void onEvent(GridEvent evt) {
            assert evt instanceof GridDiscoveryEvent;

            UUID nodeId = ((GridDiscoveryEvent)evt).eventNodeId();

            Collection<GridJobWorker> jobsToReject = new ArrayList<GridJobWorker>();
            Collection<GridJobWorker> jobsToCancel = new ArrayList<GridJobWorker>();

            synchronized (mux) {
                callCnt++;
            }

            try {
                switch (evt.type()) {
                    case EVT_NODE_LEFT:
                    case EVT_NODE_FAILED: {
                        synchronized (mux) {
                            if (stopping) {
                                if (log.isDebugEnabled())
                                    log.debug("Received discovery event while stopping (will ignore): " + evt);

                                return;
                            }

                            for (Iterator<GridJobWorker> iter = passiveJobs.values().iterator();
                                 iter.hasNext();) {
                                GridJobWorker job = iter.next();

                                if (job.getTaskNodeId().equals(nodeId)) {
                                    // Remove from passive jobs.
                                    iter.remove();

                                    jobsToReject.add(job);

                                    collisionsHandled = false;
                                }
                            }

                            for (Iterator<GridJobWorker> iter = activeJobs.values().iterator();
                                 iter.hasNext();) {
                                GridJobWorker job = iter.next();

                                if (job.getTaskNodeId().equals(nodeId) && !job.isFinishing()) {
                                    // Remove from active jobs.
                                    iter.remove();

                                    // Add to cancelled jobs.
                                    cancelledJobs.put(job.getJobId(), job);

                                    jobsToCancel.add(job);

                                    collisionsHandled = false;
                                }
                            }
                        }

                        // Outside of synchronization.
                        for (GridJobWorker job : jobsToReject) {
                            GridException e = new GridTopologyException("Task originating node left grid " +
                                "(job will fail) [nodeId=" + nodeId + ", jobSes=" + job.getSession() +
                                ", job=" + job + ']');

                            log.error(e.getMessage(), e);

                            finishJob(job, null, e, false);
                        }

                        for (GridJobWorker job : jobsToCancel) {
                            U.warn(log, "Job is being cancelled because master task node left grid (as there is " +
                                "no one waiting for results, job will not be failed over)");

                            cancelJob(job, true);
                        }

                        handleCollisions();

                        break;
                    }

                    case EVT_NODE_METRICS_UPDATED: {
                        // Update metrics for all nodes.
                        int gridSize = ctx.discovery().allNodes().size();

                        synchronized (mux) {
                            // Check for less-than-equal rather than just equal
                            // in guard against topology changes.
                            if (gridSize <= ++metricsUpdateCntr) {
                                collisionsHandled = false;

                                metricsUpdateCntr = 0;
                            }
                        }

                        handleCollisions();

                        break;
                    }

                    case EVT_NODE_JOINED: {
                        break;
                    } // No-op.

                    default: {
                        assert false;
                    }
                }
            }
            finally {
                synchronized (mux) {
                    callCnt--;

                    if (callCnt == 0)
                        mux.notifyAll();
                }
            }
        }
    }
}
