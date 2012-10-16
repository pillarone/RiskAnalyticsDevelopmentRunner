// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.task;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.managers.communication.GridIoPolicy.*;

/**
 * Grid task worker. Handles full task life cycle.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <T> Task argument type.
 * @param <R> Task return value type.
 */
class GridTaskWorker<T, R> extends GridWorker implements GridTimeoutObject {
    /** */
    private enum State {
        /** */
        WAITING,

        /** */
        REDUCING,

        /** */
        REDUCED,

        /** */
        FINISHING
    }

    /** */
    private final GridKernalContext ctx;

    /** */
    @SuppressWarnings({"deprecation"})
    private final GridTaskListener taskLsnr;

    /** */
    private final GridLogger log;

    /** */
    private final GridMarshaller marshaller;

    /** */
    private final GridTaskSessionImpl ses;

    /** */
    private final GridTaskFutureImpl<R> fut;

    /** */
    private final T arg;

    /** */
    private final GridTaskEventListener evtLsnr;

    /** */
    private Map<UUID, GridJobResultImpl> jobRes;

    /** */
    private State state = State.WAITING;

    /** */
    private final GridDeployment dep;

    /** Task class. */
    private final Class<? extends GridTask<T, R>> taskCls;

    /** Optional subgrid. */
    private final Collection<? extends GridNode> subgrid;

    /** */
    private GridTask<T, R> task;

    /** */
    private final Queue<GridJobExecuteResponse> delayedResp = new ConcurrentLinkedQueue<GridJobExecuteResponse>();

    /** */
    private boolean continuous;

    /** */
    private final Object mux = new Object();

    /** */
    private boolean lockRespProc = true;

    /** Continuous mapper. */
    private final GridTaskContinuousMapper mapper = new GridTaskContinuousMapper() {
        /** {@inheritDoc} */
        @Override public void send(GridJob job, GridNode node) throws GridException {
            A.notNull(job, "job");
            A.notNull(node, "node");

            processMappedJobs(Collections.singletonMap(job, node));
        }

        /** {@inheritDoc} */
        @Override public void send(Map<? extends GridJob, GridNode> mappedJobs) throws GridException {
            A.notNull(mappedJobs, "mappedJobs");

            processMappedJobs(mappedJobs);
        }

        /** {@inheritDoc} */
        @Override public void send(GridJob job) throws GridException {
            A.notNull(job, "job");

            send(Collections.singleton(job));
        }

        /** {@inheritDoc} */
        @Override public void send(Collection<? extends GridJob> jobs) throws GridException {
            A.notNull(jobs, "jobs");

            if (jobs.isEmpty()) {
                throw new GridException("Empty jobs collection passed to send(...) method.");
            }

            GridLoadBalancer balancer = ctx.loadBalancing().getLoadBalancer(ses, getTaskTopology());

            for (GridJob job : jobs) {
                if (job == null) {
                    throw new GridException("Null job passed to send(...) method.");
                }

                processMappedJobs(Collections.singletonMap(job, balancer.getBalancedNode(job, null)));
            }
        }
    };

    /**
     * @param ctx Kernal context.
     * @param arg Task argument.
     * @param ses Grid task session.
     * @param fut Task future.
     * @param taskCls Task class.
     * @param task Task instance that might be null.
     * @param dep Deployed task.
     * @param taskLsnr Grid task listener.
     * @param evtLsnr Event listener.
     * @param subgrid Subgrid for task execution.
     */
    @SuppressWarnings({"deprecation"})
    GridTaskWorker(
        GridKernalContext ctx,
        T arg,
        GridTaskSessionImpl ses,
        GridTaskFutureImpl<R> fut,
        Class<? extends GridTask<T, R>> taskCls,
        GridTask<T, R> task,
        GridDeployment dep,
        GridTaskListener taskLsnr,
        GridTaskEventListener evtLsnr,
        Collection<? extends GridNode> subgrid) {
        super(ctx.config().getGridName(), "grid-task-worker", ctx.config().getGridLogger());

        assert ses != null;
        assert fut != null;
        assert evtLsnr != null;
        assert dep != null;
        assert subgrid != null;

        this.arg = arg;
        this.ctx = ctx;
        this.fut = fut;
        this.ses = ses;
        this.taskCls = taskCls;
        this.task = task;
        this.dep = dep;
        this.taskLsnr = taskLsnr;
        this.evtLsnr = evtLsnr;
        this.subgrid = subgrid;

        log = ctx.config().getGridLogger().getLogger(getClass());

        marshaller = ctx.config().getMarshaller();
    }

    /**
     * @return Task session ID.
     */
    UUID getTaskSessionId() {
        return ses.getId();
    }

    /**
     * @return Task session.
     */
    GridTaskSessionImpl getSession() {
        return ses;
    }

    /**
     * @return Task future.
     */
    GridTaskFutureImpl<R> getTaskFuture() {
        return fut;
    }

    /**
     * Gets property dep.
     *
     * @return Property dep.
     */
    GridDeployment getDeployment() {
        return dep;
    }

    /**
     * @return Grid task.
     */
    public GridTask<T, R> getTask() {
        return task;
    }

    /**
     * @param task Deployed task.
     */
    public void setTask(GridTask<T, R> task) {
        this.task = task;
    }

    /** {@inheritDoc} */
    @Override public UUID timeoutId() {
        return ses.getId();
    }

    /** {@inheritDoc} */
    @Override public void onTimeout() {
        synchronized (mux) {
            if (state != State.WAITING)
                return;
        }

        U.warn(log, "Task has timed out: " + ses);

        recordTaskEvent(EVT_TASK_TIMEDOUT, "Task has timed out.");

        @SuppressWarnings({"ThrowableInstanceNeverThrown"})
        Throwable e = new GridTaskTimeoutException("Task timed out (check logs for error messages): " + ses);

        finishTask(null, e);
    }

    /** {@inheritDoc} */
    @Override public long endTime() {
        return ses.getEndTime();
    }

    /**
     * @param taskCls Task class.
     * @return Task instance.
     * @throws GridException Thrown in case of any instantiation error.
     */
    private GridTask<T, R> newTask(Class<? extends GridTask<T, R>> taskCls) throws GridException {
        GridTask<T, R> task = U.newInstance(taskCls);

        if (task == null)
            throw new GridException("Failed to instantiate task class: " + taskCls);

        return task;
    }

    /** */
    private void initializeSpis() {
        GridTaskSpis spis = U.getAnnotation(task.getClass(), GridTaskSpis.class);

        if (spis != null) {
            ses.setTopologySpi(spis.topologySpi());
            ses.setLoadBalancingSpi(spis.loadBalancingSpi());
            ses.setFailoverSpi(spis.failoverSpi());
            ses.setCheckpointSpi(spis.checkpointSpi());
        }
    }

    /**
     * Maps this task's jobs to nodes and sends them out.
     */
    @Override protected void body() {
        assert dep != null;

        evtLsnr.onTaskStarted(this);

        recordTaskEvent(EVT_TASK_STARTED, "Task started.");

        try {
            // Use either user task or deployed one.
            if (task == null)
                setTask(newTask(taskCls));

            initializeSpis();

            ses.setClassLoader(dep.classLoader());

            final List<GridNode> shuffledNodes = getTaskTopology();

            // Load balancer.
            GridLoadBalancer balancer = ctx.loadBalancing().getLoadBalancer(ses, shuffledNodes);

            continuous = ctx.resource().isAnnotationPresent(dep, getTask(),
                GridTaskContinuousMapperResource.class);

            if (log.isDebugEnabled())
                log.debug("Injected task resources [continuous=" + continuous + ']');

            // Inject resources.
            ctx.resource().inject(dep, getTask(), ses, balancer, mapper);

            Map<? extends GridJob, GridNode> mappedJobs = U.withThreadLoader(dep.classLoader(),
                new Callable<Map<? extends GridJob, GridNode>>() {
                    @Override public Map<? extends GridJob, GridNode> call() throws GridException {
                        return getTask().map(shuffledNodes, arg);
                    }
                });

            if (log.isDebugEnabled())
                log.debug("Mapped task jobs to nodes [jobCnt=" + (mappedJobs != null ? mappedJobs.size() : 0) +
                    ", mappedJobs=" + mappedJobs + ", ses=" + ses + ']');

            if (F.isEmpty(mappedJobs)) {
                synchronized (mux) {
                    // Check if some jobs are sent from continuous mapper.
                    if (F.isEmpty(jobRes))
                        throw new GridException("Task map operation produced no mapped jobs: " + ses);
                }
            }
            else
                processMappedJobs(mappedJobs);

            synchronized (mux) {
                lockRespProc = false;
            }

            processDelayedResponses();
        }
        catch (GridException e) {
            U.error(log, "Failed to map task jobs to nodes: " + ses, e);

            finishTask(null, e);
        }
        // Catch throwable to protect against bad user code.
        catch (Throwable e) {
            String errMsg = "Failed to map task jobs to nodes due to undeclared user exception: " + ses;

            log.error(errMsg, e);

            finishTask(null, new GridUserUndeclaredException(errMsg, e));
        }
    }

    /**
     * @param jobs Map of jobs.
     * @throws GridException Thrown in case of any error.
     */
    private void processMappedJobs(Map<? extends GridJob, GridNode> jobs) throws GridException {
        if (F.isEmpty(jobs))
            return;

        Collection<GridJobResultImpl> jobResList = new ArrayList<GridJobResultImpl>(jobs.size());

        Collection<GridJobSibling> sibs = new ArrayList<GridJobSibling>(jobs.size());

        // Map jobs to nodes for computation.
        for (Map.Entry<? extends GridJob, GridNode> mappedJob : jobs.entrySet()) {
            GridJob job = mappedJob.getKey();
            GridNode node = mappedJob.getValue();

            if (job == null)
                throw new GridException("Job can not be null [mappedJob=" + mappedJob + ", ses=" + ses + ']');

            if (node == null)
                throw new GridException("Node can not be null [mappedJob=" + mappedJob + ", ses=" + ses + ']');

            UUID jobId = UUID.randomUUID();

            GridJobSiblingImpl sib = new GridJobSiblingImpl(ses.getId(), jobId, node.id(), ctx);

            jobResList.add(new GridJobResultImpl(job, jobId, node, sib));

            sibs.add(sib);

            recordJobEvent(EVT_JOB_MAPPED, jobId, node.id(), "Job got mapped.");
        }

        synchronized (mux) {
            if (state != State.WAITING)
                throw new GridException("Task is not in waiting state (did you call get() on the future?): " + ses);

            ses.addJobSiblings(sibs);

            if (jobRes == null)
                jobRes = new HashMap<UUID, GridJobResultImpl>();

            // Populate all remote mappedJobs into map, before mappedJobs are sent.
            // This is done to avoid race condition when we start
            // getting results while still sending out references.
            for (GridJobResultImpl res : jobResList) {
                if (jobRes.put(res.getJobContext().getJobId(), res) != null)
                    throw new GridException("Duplicate job ID for remote job found: " + res.getJobContext().getJobId());

                res.setOccupied(true);
            }
        }

        // Set mapped flag.
        fut.onMapped();

        // Send out all remote mappedJobs.
        for (GridJobResultImpl res : jobResList) {
            evtLsnr.onJobSend(this, res.getSibling());

            try {
                sendRequest(res);
            }
            finally {
                // Open job for processing results.
                synchronized (mux) {
                    res.setOccupied(false);
                }
            }
        }

        processDelayedResponses();
    }

    /**
     * @return Topology for this task.
     * @throws GridException Thrown in case of any error.
     */
    private List<GridNode> getTaskTopology() throws GridException {
        // Obtain topology from topology SPI.
        Collection<? extends GridNode> nodes = ctx.topology().getTopology(ses, subgrid);

        if (F.isEmpty(nodes))
            throw new GridException("Task topology provided by topology SPI is empty or null.");

        List<GridNode> shuffledNodes = new ArrayList<GridNode>(nodes);

        // Shuffle nodes prior to giving them to user.
        Collections.shuffle(shuffledNodes);

        // Load balancer.
        return shuffledNodes;
    }

    /** */
    private void processDelayedResponses() {
        GridJobExecuteResponse res = delayedResp.poll();

        if (res != null)
            onResponse(res);
    }

    /**
     * @param res Job execution response.
     */
    @SuppressWarnings({"unchecked", "ThrowableInstanceNeverThrown"})
    void onResponse(GridJobExecuteResponse res) {
        assert res != null;

        GridJobResultImpl jobRes = null;

        // Flag indicating whether occupied flag for
        // job response was changed in this method apply.
        boolean selfOccupied = false;

        try {
            synchronized (mux) {
                // If task is not waiting for responses,
                // then there is no point to proceed.
                if (state != State.WAITING) {
                    if (log.isDebugEnabled())
                        log.debug("Ignoring response since task is already reducing or finishing [res=" + res +
                            ", job=" + ses + ", state=" + state + ']');

                    return;
                }

                jobRes = this.jobRes.get(res.getJobId());

                if (jobRes == null) {
                    if (log.isDebugEnabled())
                        U.warn(log, "Received response for unknown child job (was job presumed failed?): " + res);

                    return;
                }

                // Only process 1st response and ignore following ones. This scenario
                // is possible if node has left topology and and fake failure response
                // was created from discovery listener and when sending request failed.
                if (jobRes.hasResponse()) {
                    if (log.isDebugEnabled())
                        log.debug("Received redundant response for a job (will ignore): " + res);

                    return;
                }

                if (!jobRes.getNode().id().equals(res.getNodeId())) {
                    if (log.isDebugEnabled())
                        log.debug("Ignoring stale response as job was already resent to other node [res=" + res +
                            ", jobRes=" + jobRes + ']');

                    return;
                }

                if (jobRes.isOccupied()) {
                    if (log.isDebugEnabled())
                        log.debug("Adding response to delayed queue (job is either being sent or processing " +
                            "another response): " + res);

                    delayedResp.offer(res);

                    return;
                }

                if (lockRespProc) {
                    delayedResp.offer(res);

                    return;
                }

                lockRespProc = true;

                selfOccupied = true;

                // Prevent processing 2 responses for the same job simultaneously.
                jobRes.setOccupied(true);
            }

            if (res.getFakeException() != null) {
                jobRes.onResponse(null, res.getFakeException(), null, false);
            }
            else {
                ClassLoader clsLdr = dep.classLoader();

                try {
                    jobRes.onResponse(
                        U.unmarshal(marshaller, res.getJobResult(), clsLdr),
                        (GridException)U.unmarshal(marshaller, res.getException(), clsLdr),
                        (Map<Object, Object>)
                        U.unmarshal(marshaller, res.getJobAttributes(), clsLdr),
                        res.isCancelled()
                    );
                }
                catch (GridException e) {
                    U.error(log, "Error deserializing job response: " + res, e);

                    finishTask(null, e);
                }
            }

            List<GridJobResult> results;

            synchronized (mux) {
                results = getRemoteResults();
            }

            GridJobResultPolicy policy = result(jobRes, results);

            if (policy == null) {
                String errMsg = "Failed to obtain remote job result policy for result from GridTask.result(..) " +
                    "method that returned null (will fail the whole task): " + jobRes;

                finishTask(null, new GridException(errMsg));

                return;
            }

            // If instructed not to cache results, then set the result to null.
            if (dep.annotation(taskCls, GridTaskNoResultCache.class) != null) {
                jobRes.clearData();
            }

            // Get nodes topology for failover.
            Collection<? extends GridNode> top = ctx.topology().getTopology(ses, ctx.discovery().allNodes());

            synchronized (mux) {
                switch (policy) {
                    // Start reducing all results received so far.
                    case REDUCE: {
                        state = State.REDUCING;

                        break;
                    }

                    // Keep waiting if there are more responses to come,
                    // otherwise, reduce.
                    case WAIT: {
                        assert results.size() <= this.jobRes.size();

                        // If there are more results to wait for.
                        if (results.size() == this.jobRes.size()) {
                            policy = GridJobResultPolicy.REDUCE;

                            // All results are received, proceed to reduce method.
                            state = State.REDUCING;
                        }

                        break;
                    }

                    case FAILOVER: {
                        // Make sure that fail-over SPI provided a new node.
                        if (!failover(res, jobRes, top))
                            policy = null;

                        break;
                    }
                }
            }

            // Outside of synchronization.
            if (policy != null) {
                // Handle failover.
                if (policy == GridJobResultPolicy.FAILOVER)
                    sendFailoverRequest(jobRes);
                else {
                    evtLsnr.onJobFinished(this, jobRes.getSibling());

                    if (policy == GridJobResultPolicy.REDUCE)
                        reduce(results);
                }
            }
        }
        catch (GridException e) {
            U.error(log, "Failed to obtain topology [ses=" + ses + ", err=" + e + ']', e);

            finishTask(null, e);
        }
        finally {
            // Open up job for processing responses.
            // Only unset occupied flag, if it was
            // set in this method.
            if (selfOccupied) {
                assert jobRes != null;

                synchronized (mux) {
                    jobRes.setOccupied(false);

                    lockRespProc = false;
                }

                processDelayedResponses();
            }
        }
    }

    /**
     * @param jobRes Job result.
     * @param results Existing job results.
     * @return Job result policy.
     */
    @SuppressWarnings({"CatchGenericClass"})
    @Nullable private GridJobResultPolicy result(final GridJobResult jobRes, final List<GridJobResult> results) {
        assert !Thread.holdsLock(mux);
        return U.wrapThreadLoader(dep.classLoader(), new CO<GridJobResultPolicy>() {
            @Nullable @Override public GridJobResultPolicy apply() {
                try {
                    // Obtain job result policy.
                    GridJobResultPolicy policy = null;

                    try {
                        policy = getTask().result(jobRes, results);
                    }
                    finally {
                        recordJobEvent(EVT_JOB_RESULTED, jobRes.getJobContext().getJobId(),
                            jobRes.getNode().id(), "Job got resulted with: " + policy);
                    }

                    if (log.isDebugEnabled())
                        log.debug("Obtained job result policy [policy=" + policy + ", ses=" + ses + ']');

                    return policy;
                }
                catch (GridException e) {
                    U.error(log, "Failed to obtain remote job result policy for result from GridTask.result(..) method " +
                        "(will fail the whole task): " + jobRes, e);

                    finishTask(null, e);

                    return null;
                }
                catch (Throwable e) {
                    String errMsg = "Failed to obtain remote job result policy for result from GridTask.result(..) " +
                        "method due to undeclared user exception (will fail the whole task): " + jobRes;

                    log.error(errMsg, e);

                    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
                    Throwable tmp = new GridUserUndeclaredException(errMsg, e);

                    // Failed to successfully obtain result policy and
                    // hence forced to fail the whole deployed task.
                    finishTask(null, tmp);

                    return null;
                }
            }
        });
    }

    /**
     * @param results Job results.
     */
    @SuppressWarnings({"CatchGenericClass"})
    private void reduce(final List<GridJobResult> results) {
        try {
            R reduceRes;

            try {
                // Reduce results.
                reduceRes = U.withThreadLoader(dep.classLoader(), new Callable<R>() {
                    @Nullable @Override public R call() throws GridException {
                        return getTask().reduce(results);
                    }
                });
            }
            finally {
                synchronized (mux) {
                    assert state == State.REDUCING : "Invalid task state: " + state;

                    state = State.REDUCED;
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Reduced job responses [reduceRes=" + reduceRes + ", ses=" + ses + ']');
            }

            recordTaskEvent(EVT_TASK_REDUCED, "Task reduced.");

            finishTask(reduceRes, null);

            // Try to cancel child jobs out of courtesy.
            cancelChildren();
        }
        catch (GridException e) {
            U.error(log, "Failed to reduce job results for task: " + getTask(), e);

            finishTask(null, e);
        }
        // Catch Throwable to protect against bad user code.
        catch (Throwable e) {
            String errMsg = "Failed to reduce job results due to undeclared user exception [task=" +
                getTask() + ", err=" + e + ']';

            log.error(errMsg, e);

            //noinspection ThrowableInstanceNeverThrown
            finishTask(null, new GridUserUndeclaredException(errMsg ,e));
        }
     }

    /**
     * @param res Execution response.
     * @param jobRes Job result.
     * @param top Topology.
     * @return {@code True} if fail-over SPI returned a new node.
     */
    @SuppressWarnings({"CatchGenericClass"})
    private boolean failover(GridJobExecuteResponse res, GridJobResultImpl jobRes, Collection<? extends GridNode> top) {
        assert Thread.holdsLock(mux);

        try {
            List<GridNode> nodes = new ArrayList<GridNode>(top);

            // Shuffle nodes prior to giving them to user.
            Collections.shuffle(nodes);

            ctx.resource().invokeAnnotated(dep, jobRes.getJob(), GridJobBeforeFailover.class);

            // Map to a new node.
            GridNode node = ctx.failover().failover(ses, jobRes, nodes);

            if (node == null) {
                String msg = "Failed to failover a job to another node (failover SPI returned null) [job=" +
                    jobRes.getJob() + ", node=" + jobRes.getNode() + ']';

                if (log.isDebugEnabled())
                    log.debug(msg);

                @SuppressWarnings({"ThrowableInstanceNeverThrown"})
                Throwable e = new GridTopologyException(msg, jobRes.getException());

                finishTask(null, e);

                return false;
            }

            if (log.isDebugEnabled())
                log.debug("Resolved job failover [newNode=" + node + ", oldNode=" + jobRes.getNode() +
                    ", job=" + jobRes.getJob() + ", resMsg=" + res + ']');

            jobRes.setNode(node);
            jobRes.resetResponse();

            return true;
        }
        // Catch Throwable to protect against bad user code.
        catch (Throwable e) {
            String errMsg = "Failed to failover job due to undeclared user exception [job=" +
                jobRes.getJob() + ", err=" + e + ']';

            log.error(errMsg, e);

            //noinspection ThrowableInstanceNeverThrown
            finishTask(null, new GridUserUndeclaredException(errMsg, e));

            return false;
        }
    }

    /**
     * @param jobRes Job result.
     */
    private void sendFailoverRequest(GridJobResultImpl jobRes) {
        // Internal failover notification.
        evtLsnr.onJobFailover(this, jobRes.getSibling(), jobRes.getNode().id());

        long timeout = ses.getEndTime() - System.currentTimeMillis();

        if (timeout > 0) {
            recordJobEvent(EVT_JOB_FAILED_OVER, jobRes.getJobContext().getJobId(),
                jobRes.getNode().id(), "Job failed over.");

            // Send new reference to remote nodes for execution.
            sendRequest(jobRes);
        }
        else {
            // Don't apply 'finishTask(..)' here as it will
            // be called from 'onTimeout(..)' callback.
            U.warn(log, "Failed to fail-over job due to task timeout: " + jobRes);
        }
    }

    /**
     * Interrupts child jobs on remote nodes.
     */
    private void cancelChildren() {
        Collection<GridJobResultImpl> doomed = new LinkedList<GridJobResultImpl>();

        synchronized (mux) {
            // Only interrupt unfinished jobs.
            for (GridJobResultImpl res : jobRes.values())
                if (!res.hasResponse())
                    doomed.add(res);
        }

        // Send cancellation request to all unfinished children.
        for (GridJobResultImpl res : doomed) {
            try {
                GridNode node = ctx.discovery().node(res.getNode().id());

                if (node != null)
                    ctx.io().send(node,
                        TOPIC_CANCEL,
                        new GridJobCancelRequest(ses.getId(), res.getJobContext().getJobId(), /*courtesy*/true),
                        PUBLIC_POOL);
            }
            catch (GridException e) {
                if (!isDeadNode(res.getNode().id()))
                    U.error(log, "Failed to send cancel request to node (will ignore) [nodeId=" +
                        res.getNode().id() + ", taskName=" + ses.getTaskName() +
                        ", taskSesId=" + ses.getId() + ", jobSesId=" + res.getJobContext().getJobId() + ']', e);
            }
        }
    }

    /**
     * @param res Job result.
     */
    private void sendRequest(GridJobResult res) {
        assert res != null;

        GridJobExecuteRequest req = null;

        GridNode node = res.getNode();

        try {
            GridNode curNode = ctx.discovery().node(node.id());

            // Check if node exists prior to sending to avoid cases when a discovery
            // listener notified about node leaving after topology resolution. Note
            // that we make this check because we cannot count on exception being
            // thrown in case of send failure.
            if (curNode == null) {
                U.warn(log, "Failed to send job request because remote node left grid (will attempt fail-over to " +
                    "another node) [node=" + node + ", taskName=" + ses.getTaskName() + ", taskSesId=" +
                    ses.getId() + ", jobSesId=" + res.getJobContext().getJobId() + ']');

                ctx.resource().invokeAnnotated(dep, res.<GridJob>getJob(), GridJobAfterSend.class);

                GridJobExecuteResponse fakeRes = new GridJobExecuteResponse(node.id(), ses.getId(),
                    res.getJobContext().getJobId(), null, null, null, false);

                //noinspection ThrowableInstanceNeverThrown
                fakeRes.setFakeException(new GridTopologyException("Failed to send job due to node failure: " + node));

                onResponse(fakeRes);
            }
            else {
                long timeout = ses.getEndTime() - System.currentTimeMillis();

                if (timeout > 0) {
                    req = new GridJobExecuteRequest(
                        ses.getId(),
                        res.getJobContext().getJobId(),
                        ses.getTaskName(),
                        ses.getUserVersion(),
                        ses.getSequenceNumber(),
                        ses.getTaskClassName(),
                        U.marshal(marshaller, res.getJob()),
                        ses.getStartTime(),
                        timeout,
                        ctx.config().getNodeId(),
                        ses.getJobSiblings(),
                        U.marshal(marshaller, ses.getAttributes()),
                        U.marshal(marshaller, res.getJobContext().getAttributes()),
                        ses.getCheckpointSpi(),
                        dep.classLoaderId(),
                        dep.deployMode(),
                        continuous,
                        dep.participants());

                    if (log.isDebugEnabled())
                        log.debug("Sending grid job request [req=" + req + ", node=" + node + ']');

                    // Send job execution request.
                    ctx.io().send(node, TOPIC_JOB, req, PUBLIC_POOL);

                    ctx.resource().invokeAnnotated(dep, res.<GridJob>getJob(), GridJobAfterSend.class);
                }
                else
                    U.warn(log, "Job timed out prior to sending job execution request: " + res.getJob());
            }
        }
        catch (GridException e) {
            // Avoid stack trace if node has left grid.
            if (isDeadNode(res.getNode().id()))
                U.warn(log, "Failed to send job request because remote node left grid (will attempt fail-over to " +
                    "another node) [node=" + node + ", taskName=" + ses.getTaskName() +
                    ", taskSesId=" + ses.getId() + ", jobSesId=" + res.getJobContext().getJobId() + ']');
            else
                U.error(log, "Failed to send job request: " + req, e);

            GridJobExecuteResponse fakeRes = new GridJobExecuteResponse(node.id(), ses.getId(),
                res.getJobContext().getJobId(), null, null, null, false);

            //noinspection ThrowableInstanceNeverThrown
            fakeRes.setFakeException(new GridTopologyException("Failed to send job due to node failure: " + node, e));

            onResponse(fakeRes);
        }
    }

    /**
     * @param nodeId Node ID.
     */
    void onNodeLeft(UUID nodeId) {
        Collection<GridJobExecuteResponse> resList = new ArrayList<GridJobExecuteResponse>();

        synchronized (mux) {
            // First check if job cares about future responses.
            if (state != State.WAITING)
                return;

            if (jobRes != null) {
                for (GridJobResultImpl jr : jobRes.values()) {
                    if (!jr.hasResponse() && jr.getNode().id().equals(nodeId)) {
                        if (log.isDebugEnabled())
                            log.debug("Creating fake response because node left grid [job=" + jr.getJob() +
                                ", nodeId=" + nodeId + ']');

                        GridJobExecuteResponse fakeRes = new GridJobExecuteResponse(nodeId, ses.getId(),
                            jr.getJobContext().getJobId(), null, null, null, false);

                        //noinspection ThrowableInstanceNeverThrown
                        fakeRes.setFakeException(new GridTopologyException("Node has left grid: " + nodeId));

                        // Artificial response in case if a job is waiting for a response from
                        // non-existent node.
                        resList.add(fakeRes);
                    }
                }
            }
        }

        // Simulate responses without holding synchronization.
        for (GridJobExecuteResponse res : resList) {
            if (log.isDebugEnabled())
                log.debug("Simulating fake response from left node [res=" + res + ", nodeId=" + nodeId + ']');

            onResponse(res);
        }
    }

    /**
     * @param nodes Nodes.
     */
    void synchronizeNodes(Iterable<GridNode> nodes) {
        Collection<GridJobExecuteResponse> responses = new ArrayList<GridJobExecuteResponse>();

        synchronized (mux) {
            // First check if job cares about future responses.
            if (state != State.WAITING)
                return;

            if (jobRes != null) {
                for (GridJobResultImpl jr : jobRes.values()) {
                    if (!jr.hasResponse()) {
                        boolean found = false;

                        for (GridNode node : nodes) {
                            if (jr.getNode().id().equals(node.id())) {
                                found = true;

                                break;
                            }
                        }

                        // If node does not exist.
                        if (!found) {
                            if (log.isDebugEnabled())
                                log.debug("Creating fake response when synchronizing nodes for job result: " + jr);

                            GridJobExecuteResponse fakeRes = new GridJobExecuteResponse(jr.getNode().id(),
                                ses.getId(), jr.getJobContext().getJobId(), null, null, null, false);

                            //noinspection ThrowableInstanceNeverThrown
                            fakeRes.setFakeException(new GridTopologyException("Node has left grid: " +
                                jr.getNode()));

                            // Artificial response in case if a job result
                            // is waiting for a response from non-existent node.
                            responses.add(fakeRes);
                        }
                    }
                }
            }
        }

        // Simulate responses without holding synchronization.
        for (GridJobExecuteResponse res : responses)
            onResponse(res);
    }

    /**
     * @param evtType Event type.
     * @param msg Event message.
     */
    private void recordTaskEvent(int evtType, String msg) {
        if (ctx.event().isRecordable(evtType)) {
            GridTaskEvent evt = new GridTaskEvent();

            evt.message(msg);
            evt.nodeId(ctx.discovery().localNode().id());
            evt.taskName(ses.getTaskName());
            evt.taskSessionId(ses.getId());
            evt.type(evtType);

            ctx.event().record(evt);
        }
    }

    /**
     * @param evtType Event type.
     * @param jobId Job ID.
     * @param evtNodeId Event node ID.
     * @param msg Event message.
     */
    private void recordJobEvent(int evtType, UUID jobId, UUID evtNodeId, String msg) {
        if (ctx.event().isRecordable(evtType)) {
            GridJobEvent evt = new GridJobEvent();

            evt.message(msg);
            evt.nodeId(ctx.discovery().localNode().id());
            evt.taskName(ses.getTaskName());
            evt.taskSessionId(ses.getId());
            evt.taskNodeId(evtNodeId);
            evt.jobId(jobId);
            evt.type(evtType);

            ctx.event().record(evt);
        }
    }

    /**
     * @return Collection of job results.
     */
    private List<GridJobResult> getRemoteResults() {
        assert Thread.holdsLock(mux);

        List<GridJobResult> results = new ArrayList<GridJobResult>(jobRes.size());

        for (GridJobResultImpl jobResult : jobRes.values())
            if (jobResult.hasResponse())
                results.add(jobResult);

        return results;
    }

    /**
     * @param res Task result.
     * @param e Exception.
     */
    @SuppressWarnings({"deprecation"})
    void finishTask(@Nullable R res, @Nullable Throwable e) {
        // Avoid finishing a job more than once from
        // different threads.
        synchronized (mux) {
            if (state == State.REDUCING || state == State.FINISHING)
                return;

            state = State.FINISHING;
        }

        if (e == null)
            recordTaskEvent(EVT_TASK_FINISHED, "Task finished.");
        else
            recordTaskEvent(EVT_TASK_FAILED, "Task failed.");

        // Clean resources prior to finishing future.
        evtLsnr.onTaskFinished(this);

        // Locally executing jobs that timed out will still exit normally.
        // In that case will record events, but do not notify listener,
        // since it was already notified at timeout time.
        if (e != null) {
            fut.onDone(e);

            if (taskLsnr != null)
                taskLsnr.onFinished(fut);
        }
        else {
            fut.onDone(res);

            // If task was invoked asynchronously.
            if (taskLsnr != null)
                taskLsnr.onFinished(fut);
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

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null)
            return false;

        assert obj instanceof GridTaskWorker;

        return ses.getId().equals(((GridTaskWorker<T, R>)obj).ses.getId());
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return ses.getId().hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTaskWorker.class, this);
    }
}
