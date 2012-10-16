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
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.managers.communication.GridIoPolicy.*;

/**
 * Job worker.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJobWorker extends GridWorker implements GridTimeoutObject {
    /** Per-thread halted flag. */
    private static final ThreadLocal<Boolean> HOLD = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() {
            return false;
        }
    };

    /** */
    private final long createTime;

    /** */
    private final AtomicLong startTime = new AtomicLong(0);

    /** */
    private final AtomicLong finishTime = new AtomicLong(0);

    /** */
    private final GridKernalContext ctx;

    /** */
    private final String jobTopic;

    /** */
    private final String taskTopic;

    /** */
    private GridByteArrayList jobBytes;

    /** Task originating node ID. */
    private final UUID taskNodeId;

    /** */
    private final UUID locNodeId;

    /** */
    private final GridLogger log;

    /** */
    private final GridMarshaller marshaller;

    /** */
    private final GridJobSessionImpl ses;

    /** */
    private final GridJobContextImpl jobCtx;

    /** */
    private final GridJobEventListener evtLsnr;

    /** Deployment. */
    private final GridDeployment dep;

    /** */
    private boolean isFinishing;

    /** */
    private boolean isTimedOut;

    /** */
    private final AtomicBoolean sysCancelled = new AtomicBoolean(false);

    /** */
    private final AtomicBoolean sysStopping = new AtomicBoolean(false);

    /** */
    private boolean isStarted;

    /** Deployed job. */
    private final AtomicReference<GridJob> job = new AtomicReference<GridJob>(null);

    /** Halted flag. */
    private boolean held;

    /** */
    private final Object mux = new Object();

    /**
     * @param ctx Kernal context.
     * @param dep Grid deployment.
     * @param createTime Create time.
     * @param ses Grid task session.
     * @param jobCtx Job context.
     * @param jobBytes Grid job bytes.
     * @param taskNodeId Grid task node ID.
     * @param evtLsnr Job event listener.
     */
    GridJobWorker(
        GridKernalContext ctx,
        GridDeployment dep,
        long createTime,
        GridJobSessionImpl ses,
        GridJobContextImpl jobCtx,
        GridByteArrayList jobBytes,
        UUID taskNodeId,
        GridJobEventListener evtLsnr) {
        super(ctx.gridName(), "grid-job-worker", ctx.log());


        assert ctx != null;
        assert ses != null;
        assert jobCtx != null;
        assert taskNodeId != null;
        assert evtLsnr != null;
        assert dep != null;

        this.ctx = ctx;
        this.createTime = createTime;
        this.evtLsnr = evtLsnr;
        this.dep = dep;
        this.ses = ses;
        this.jobCtx = jobCtx;
        this.jobBytes = jobBytes;
        this.taskNodeId = taskNodeId;

        log = ctx.log().getLogger(getClass());
        marshaller = ctx.config().getMarshaller();

        locNodeId = ctx.discovery().localNode().id();

        jobTopic = TOPIC_JOB.name(ses.getJobId(), locNodeId);
        taskTopic = TOPIC_TASK.name(ses.getJobId(), locNodeId);
    }

    /**
     * Gets deployed job or {@code null} of job could not be deployed.
     *
     * @return Deployed job.
     */
    public GridJob getJob() {
        return job.get();
    }

    /**
     * @return Deployed task.
     */
    GridDeployment getDeployment() {
        return dep;
    }

    /**
     * Returns {@code True} if job was cancelled by the system.
     *
     * @return {@code True} if job was cancelled by the system.
     */
    boolean isSystemCanceled() {
        return sysCancelled.get();
    }

    /**
     * @return Create time.
     */
    long getCreateTime() {
        return createTime;
    }

    /**
     * @return Unique job ID.
     */
    public UUID getJobId() {
        UUID jobId = ses.getJobId();

        assert jobId != null;

        return jobId;
    }

    /**
     * @return Job context.
     */
    public GridJobContext getJobContext() {
        return jobCtx;
    }

    /**
     * @return Job communication topic.
     */
    String getJobTopic() {
        return jobTopic;
    }

    /**
     * @return Task communication topic.
     */
    String getTaskTopic() {
        return taskTopic;
    }

    /**
     * @return Session.
     */
    public GridJobSessionImpl getSession() {
        return ses;
    }

    /**
     * Gets job finishing state.
     *
     * @return {@code true} if job is being finished after execution
     *      and {@code false} otherwise.
     */
    boolean isFinishing() {
        synchronized (mux) {
            return isFinishing;
        }
    }

    /**
     * @return Parent task node ID.
     */
    UUID getTaskNodeId() {
        return taskNodeId;
    }

    /**
     * @return Job execution time.
     */
    long getExecuteTime() {
        long startTime = this.startTime.get();
        long finishTime = this.finishTime.get();

        return startTime == 0 ? 0 : finishTime == 0 ?
            System.currentTimeMillis() - startTime : finishTime - startTime;
    }

    /**
     * @return Time job spent on waiting queue.
     */
    long getQueuedTime() {
        long startTime = this.startTime.get();

        return startTime == 0 ? System.currentTimeMillis() - createTime : startTime - createTime;
    }

    /** {@inheritDoc} */
    @Override public long endTime() {
        return ses.getEndTime();
    }

    /** {@inheritDoc} */
    @Override public UUID timeoutId() {
        UUID jobId = ses.getJobId();

        assert jobId != null;

        return jobId;
    }

    /**
     * @return {@code True} if job is timed out.
     */
    boolean isTimedOut() {
        synchronized (mux) {
            return isTimedOut;
        }
    }

    /** {@inheritDoc} */
    @Override public void onTimeout() {
        synchronized (mux) {
            if (isFinishing || isTimedOut) {
                return;
            }

            isTimedOut = true;
        }

        U.warn(log, "Job has timed out: " + ses);

        cancel(true);

        recordEvent(EVT_JOB_TIMEDOUT, "Job has timed out: " + job.get());
    }

    /**
     * Callback for whenever grid is stopping.
     */
    public void onStopping() {
        sysStopping.set(true);
    }

    /**
     * Unholds per-thread halted flag.
     */
    private void unhold() {
        HOLD.set(false);

        synchronized (mux) {
            held = false;
        }
    }

    /**
     * @return {@code True} if job was halted.
     */
    public boolean held() {
        synchronized (mux) {
            return held;
        }
    }

    /**
     * Sets halt flags.
     */
    public void hold() {
        HOLD.set(true);

        synchronized (mux) {
            held = true;
        }
    }

    /**
     * Initializes job. Handles deployments and event recording.
     *
     * @param dep Job deployed task.
     * @param taskCls Task class.
     * @return {@code True} if job was successfully initialized.
     */
    boolean initialize(GridDeployment dep, Class<?> taskCls) {
        assert dep != null;

        GridException ex = null;

        try {
            GridJob execJob = U.unmarshal(marshaller, jobBytes, dep.classLoader());

            // Inject resources.
            ctx.resource().inject(dep, taskCls, execJob, ses, jobCtx);

            job.set(execJob);

            recordEvent(EVT_JOB_QUEUED, "Job got queued for computation.");
        }
        catch (GridException e) {
            U.error(log, "Failed to initialize job [jobId=" + ses.getJobId() + ", ses=" + ses + ']', e);

            ex = e;
        }
        catch (Throwable e) {
            ex = handleThrowable(e);

            assert ex != null;
        }
        finally {
            if (ex != null)
                finishJob(null, ex, true);
        }

        return ex == null;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"CatchGenericClass"})
    @Override protected void body() {
        assert job.get() != null;

        startTime.set(System.currentTimeMillis());

        isStarted = true;

        // Event notification.
        evtLsnr.onJobStarted(this);

        recordEvent(EVT_JOB_STARTED, /*no message for success. */null);

        execute();
    }

    /**
     * Executes the job.
     */
    public void execute() {
        unhold();

        boolean sendRes = true;

        Object res = null;

        GridException ex = null;

        try {
            // If job has timed out, then
            // avoid computation altogether.
            if (isTimedOut()) {
                sendRes = false;
            }
            else {
                res = U.withThreadLoader(dep.classLoader(), new Callable<Object>() {
                    @Nullable @Override public Object call() throws GridException {
                        return job.get().execute();
                    }
                });

                if (log.isDebugEnabled())
                    log.debug("Job execution has successfully finished [job=" + job.get() + ", res=" + res + ']');
            }
        }
        catch (GridException e) {
            if (sysStopping.get() && e.hasCause(GridInterruptedException.class, InterruptedException.class)) {
                ex = handleThrowable(e);

                assert ex != null;
            }
            else {
                U.error(log, "Failed to execute job [jobId=" + ses.getJobId() + ", ses=" + ses + ']', e);

                ex = e;
            }
        }
        // Catch Throwable to protect against bad user code except
        // InterruptedException if job is being cancelled.
        catch (Throwable e) {
            ex = handleThrowable(e);

            assert ex != null;
        }
        finally {
            // Finish here only if was halted by this thread.
            if (!HOLD.get()) {
                finishJob(res, ex, sendRes);
            }
        }
    }

    /**
     * Handles {@link Throwable} generic exception for task
     * deployment and execution.
     *
     * @param e Exception.
     * @return Wrapped exception.
     */
    private GridException handleThrowable(Throwable e) {
        String msg = null;

        GridException ex = null;

        // Special handling for weird interrupted exception which
        // happens due to JDk 1.5 bug.
        if (e instanceof InterruptedException && !sysStopping.get()) {
            msg = "Failed to execute job due to interrupted exception.";

            // Turn interrupted exception into checked exception.
            ex = new GridException(msg, e);
        }
        // Special 'NoClassDefFoundError' handling if P2P is on. We had many questions
        // about this exception and decided to change error message.
        else if ((e instanceof NoClassDefFoundError || e instanceof ClassNotFoundException)
            && ctx.config().isPeerClassLoadingEnabled()) {
            msg = "Failed to execute job due to class or resource loading exception (make sure that task " +
                "originating node is still in grid and requested class is in the task class path) [jobId=" +
                ses.getJobId() + ", ses=" + ses + ']';

            ex = new GridUserUndeclaredException(msg, e);
        }
        else if (sysStopping.get()) {
            if (X.hasCause(e, InterruptedException.class, GridInterruptedException.class)) {
                msg = "Job got interrupted due to system stop (will attempt failover).";

                ex = new GridExecutionRejectedException(e);
            }
        }

        if (msg == null) {
            msg = "Failed to execute job due to unexpected runtime exception [jobId=" + ses.getJobId() +
                ", ses=" + ses + ']';

            ex = new GridUserUndeclaredException(msg, e);
        }

        assert msg != null;
        assert ex != null;

        log.error(msg, e);

        return ex;
    }

    /** {@inheritDoc} */
    @Override public void cancel() {
        cancel(false);
    }

    /**
     * @param system System flag.
     */
    public void cancel(boolean system) {
        try {
            super.cancel();

            final GridJob job = this.job.get();

            sysCancelled.set(system);

            if (job != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Cancelling job: " + ses);
                }

                U.wrapThreadLoader(dep.classLoader(), new CA() {
                    @Override public void apply() {
                        job.cancel();
                    }
                });
            }

            recordEvent(EVT_JOB_CANCELLED, "Job was cancelled: " + job);
        }
        // Catch throwable to protect against bad user code.
        catch (Throwable e) {
            U.error(log, "Failed to cancel job due to undeclared user exception [jobId=" + ses.getJobId() +
                ", ses=" + ses + ']', e);
        }
    }

    /**
     * @param evtType Event type.
     * @param msg Message.
     */
    private void recordEvent(int evtType, String msg) {
        if (ctx.event().isRecordable(evtType)) {
            GridJobEvent evt = new GridJobEvent();

            evt.jobId(ses.getJobId());
            evt.message(msg);
            evt.nodeId(locNodeId);
            evt.taskName(ses.getTaskName());
            evt.taskSessionId(ses.getId());
            evt.type(evtType);
            evt.taskNodeId(ses.getTaskNodeId());

            ctx.event().record(evt);
        }
    }

    /**
     * @param res Result.
     * @param ex Error.
     * @param sendReply If {@code true}, reply will be sent.
     */
    @SuppressWarnings({"CatchGenericClass"})
    void finishJob(Object res, GridException ex, boolean sendReply) {
        // Avoid finishing a job more than once from
        // different threads.
        synchronized (mux) {
            if (isFinishing) {
                return;
            }

            isFinishing = true;
        }

        Collection<GridTuple2<Integer, String>> evts = new LinkedList<GridTuple2<Integer, String>>();

        try {
            // Send response back only if job has not timed out.
            if (!isTimedOut()) {
                if (sendReply) {
                    GridNode senderNode = ctx.discovery().node(taskNodeId);

                    if (senderNode == null) {
                        U.error(log, "Failed to reply to sender node because it left grid [nodeId=" + taskNodeId +
                            ", ses=" + ses + ", jobId=" + ses.getJobId() + ", job=" + job + ']');

                        // Record job reply failure.
                        evts.add(F.t(EVT_JOB_FAILED, "Job reply failed (original task node left grid): " +
                            job.get()));
                    }
                    else {
                        try {
                            if (ex != null) {
                                if (isStarted) {
                                    // Job failed.
                                    evts.add(F.t(EVT_JOB_FAILED, "Job failed due to exception [ex=" +
                                        ex + ", job=" + job.get() + ']'));
                                }
                                else {
                                    // Job has been rejected.
                                    evts.add(F.t(EVT_JOB_REJECTED, "Job has been rejected before " +
                                        "exception [ex=" + ex + ", job=" + job.get() + ']'));
                                }
                            }
                            else {
                                evts.add(F.t(EVT_JOB_FINISHED, /*no message for success. */(String)null));
                            }

                            Serializable jobRes = new GridJobExecuteResponse(
                                ctx.localNodeId(),
                                ses.getId(),
                                ses.getJobId(),
                                U.marshal(marshaller, ex),
                                U.marshal(marshaller,res),
                                U.marshal(marshaller, jobCtx.getAttributes()),
                                isCancelled());

                            // Job response topic.
                            String topic = TOPIC_TASK.name(ses.getJobId(), locNodeId);

                            long timeout = ses.getEndTime() - System.currentTimeMillis();

                            if (timeout <= 0) {
                                // Ignore the actual timeout and send response anyway.
                                timeout = 1;
                            }

                            // Send response to designated job topic.
                            ctx.io().sendOrderedMessage(
                                senderNode,
                                topic,
                                ctx.io().getNextMessageId(topic, senderNode.id()),
                                jobRes,
                                SYSTEM_POOL,
                                timeout);

                            // Callback.
                            ctx.resource().invokeAnnotated(dep, job.get(), GridJobAfterExecute.class);
                        }
                        catch (GridException e) {
                            // The only option here is to log, as we must assume that resending will fail too.
                            if (isDeadNode(taskNodeId)) {
                                // Avoid stack trace for left nodes.
                                U.error(log, "Failed to reply to sender node because it left grid " +
                                    "[nodeId=" + taskNodeId + ", jobId=" + ses.getJobId() +
                                    ", ses=" + ses + ", job=" + job.get() + ']');
                            }
                            else {
                                U.error(log, "Error sending reply for job [nodeId=" + senderNode.id() + ", jobId=" +
                                    ses.getJobId() + ", ses=" + ses + ", job=" + job.get() + ']', e);
                            }

                            // Record job reply failure.
                            evts.add(F.t(EVT_JOB_FAILED, "Failed to send reply for job [nodeId=" +
                                taskNodeId + ", job=" + job + ']'));
                        }
                        // Catching interrupted exception because
                        // it gets thrown for some reason.
                        catch (Exception e) {
                            String msg = "Failed to send reply for job due to interrupted " +
                                "exception [nodeId=" + taskNodeId + ", job=" + job + ']';

                            log.error(msg, e);

                            // Record job reply failure.
                            evts.add(F.t(EVT_JOB_FAILED, msg));
                        }
                    }
                }
                else {
                    if (ex != null) {
                        if (isStarted) {
                            // Job failed.
                            evts.add(F.t(EVT_JOB_FAILED, "Job failed due to exception [ex=" +
                                ex + ", job=" + job.get() + ']'));
                        }
                        else {
                            // Job has been rejected.
                            evts.add(F.t(EVT_JOB_REJECTED, "Job has been rejected before exception [ex=" +
                                ex + ", job=" + job.get() + ']'));
                        }
                    }
                    else {
                        evts.add(F.t(EVT_JOB_FINISHED, /*no message for success. */(String)null));
                    }
                }
            }
            // Job timed out.
            else {
                // Record failure for timed out job.
                evts.add(F.t(EVT_JOB_FAILED, "Job failed due to timeout: " + job.get()));
            }
        }
        finally {
            // Listener callback
            evtLsnr.onJobFinished(this);

            finishTime.set(System.currentTimeMillis());

            for (GridTuple2<Integer, String> t : evts)
                recordEvent(t.get1(), t.get2());
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
    @Override public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        assert obj instanceof GridJobWorker;

        UUID jobId1 = ses.getJobId();
        UUID jobId2 = ((GridJobWorker)obj).ses.getJobId();

        assert jobId1 != null;
        assert jobId2 != null;

        return jobId1.equals(jobId2);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        UUID jobId = ses.getJobId();

        assert jobId != null;

        return jobId.hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobWorker.class, this);
    }
}
