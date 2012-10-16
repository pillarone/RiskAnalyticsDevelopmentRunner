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
import org.gridgain.grid.kernal.managers.communication.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.lang.utils.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;

/**
 * This class defines task processor.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTaskProcessor extends GridProcessorAdapter {
    /** Wait for 5 seconds to allow discovery to take effect (best effort). */
    private static final long DISCO_TIMEOUT = 5000;

    /** */
    private final GridMarshaller marshaller;

    /** */
    private final Map<UUID, GridTaskWorker<?, ?>> tasks = new HashMap<UUID, GridTaskWorker<?, ?>>();

    /** */
    private boolean stopping;

    /** */
    private boolean waiting;

    /** */
    private int callCnt;

    /** */
    private final GridLocalEventListener discoLsnr;

    /** */
    private final ThreadLocal<Collection<? extends GridNode>> subgridCtx =
        new ThreadLocal<Collection<? extends GridNode>>();

    /** */
    private final Object mux = new Object();

    /**
     * @param ctx Kernal context.
     */
    public GridTaskProcessor(GridKernalContext ctx) {
        super(ctx);

        marshaller = ctx.config().getMarshaller();

        discoLsnr = new TaskDiscoveryListener();
    }

    /** {@inheritDoc} */
    @Override public void start() {
        if (log.isDebugEnabled())
            log.debug("Started task processor.");
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel, boolean wait) {
        if (log.isDebugEnabled())
            log.debug("Stopped task processor.");
    }

    /**
     * Registers listener with discovery SPI. Note that discovery listener
     * registration cannot be done during start because task executor
     * starts before discovery manager.
     */
    @Override public void onKernalStart() {
        ctx.event().addLocalEventListener(discoLsnr,
            EVT_NODE_FAILED,
            EVT_NODE_LEFT);

        ctx.io().addSyncMessageHandler(TOPIC_JOB_SIBLINGS, new JobSiblingsMessageHandler());

        Collection<GridNode> allNodes = ctx.discovery().allNodes();

        List<GridTaskWorker<?, ?>> tasks;

        synchronized (mux) {
            tasks = new ArrayList<GridTaskWorker<?, ?>>(this.tasks.values());
        }

        // Outside of synchronization.
        for (GridTaskWorker<?, ?> task : tasks)
            // Synchronize nodes with discovery SPI just in case if
            // some node left before listener was registered.
            task.synchronizeNodes(allNodes);

        if (log.isDebugEnabled())
            log.debug("Added discovery listener and synchronized nodes.");
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop(boolean cancel, boolean wait) {
        List<GridTaskWorker<?, ?>> execTasks;

        synchronized (mux) {
            // Set stopping flag.
            stopping = true;

            waiting = wait;

            // Wait for all method calls to complete. Note that we can only
            // do it after interrupting all tasks.
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

            // Save executing tasks so we can wait for their completion
            // outside of synchronization.
            execTasks = new ArrayList<GridTaskWorker<?, ?>>(tasks.values());
        }

        if (!execTasks.isEmpty()) {
            if (cancel)
                U.warn(log, "Canceling unfinished tasks due to stopping of the grid [cnt=" + execTasks.size() + "]");

            if (wait)
                U.warn(log, "Will wait for all job responses from tasks (this may take some time)...");
        }

        // Interrupt jobs outside of synchronization.
        for (GridTaskWorker<?, ?> task : execTasks) {
            if (wait) {
                if (cancel) {
                    try {
                        task.getTaskFuture().cancel();
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to cancel task: " + task, e);

                        task.cancel();

                        @SuppressWarnings({"ThrowableInstanceNeverThrown"})
                        Throwable ex = new GridException("Task failed due to stopping of the grid: " + task);

                        task.finishTask(null, ex);
                    }
                }

                try {
                    task.getTaskFuture().get();
                }
                catch (GridException e) {
                    U.error(log, "Task failed: " + task, e);
                }
            }
            else {
                if (cancel)
                    task.cancel();

                @SuppressWarnings({"ThrowableInstanceNeverThrown"})
                Throwable ex = new GridException("Task failed due to stopping of the grid: " + task);

                task.finishTask(null, ex);
            }

            U.join(task, log);
        }

        synchronized (mux) {
            // Finish job will remove all tasks.
            assert tasks.isEmpty();

            waiting = false;
        }

        // Unsubscribe discovery last to avoid waiting for results from
        // non-existing jobs during stopping.
        ctx.event().removeLocalEventListener(discoLsnr);

        ctx.io().removeSyncMessageHandler(TOPIC_JOB_SIBLINGS);

        if (log.isDebugEnabled())
            log.debug("All tasks have been cancelled and no more tasks will execute due to kernal stop.");
    }

    /**
     * @param nodes Nodes for the subgrid.
     */
    public void setProjectionContext(Collection<? extends GridNode> nodes) {
        subgridCtx.set(nodes);
    }

    /**
     * Gets currently used deployments.
     *
     * @return Currently used deployments.
     */
    public Collection<GridDeployment> getUsedDeployments() {
        Collection<GridDeployment> deps = new HashSet<GridDeployment>();

        synchronized (mux) {
            for (GridTaskWorker worker : tasks.values())
                deps.add(worker.getDeployment());
        }

        return deps;
    }

    /**
     * Gets currently used deployments mapped by task name or aliases.
     *
     * @return Currently used deployments.
     */
    public Map<String, GridDeployment> getUsedDeploymentMap() {
        Map<String, GridDeployment> deps = new HashMap<String, GridDeployment>();

        synchronized (mux) {
            for (GridTaskWorker w : tasks.values()) {
                GridTaskSessionImpl ses = w.getSession();

                deps.put(ses.getTaskClassName(), w.getDeployment());

                if (ses.getTaskName() != null && ses.getTaskClassName().equals(ses.getTaskName()))
                    deps.put(ses.getTaskName(), w.getDeployment());
            }
        }

        return deps;
    }

    /**
     * Waits for all tasks to be finished.
     *
     * @throws InterruptedException if waiting was interrupted.
     */
    public void waitForTasksFinishing() throws InterruptedException {
        synchronized (mux) {
            while (!tasks.isEmpty())
                mux.wait(2000);
        }
    }

    /**
     * @param taskCls Task class.
     * @param arg Optional execution argument.
     * @param timeout Execution timeout.
     * @param lsnr Optional task execution listener.
     * @return Task future.
     * @param <T> Task argument type.
     * @param <R> Task return value type.
     */
    @SuppressWarnings({"deprecation"})
    public <T, R> GridTaskFuture<R> execute(Class<? extends GridTask<T, R>> taskCls, @Nullable T arg, long timeout,
        @Nullable GridTaskListener lsnr) {
        assert taskCls != null;

        synchronized (mux) {
            // Prohibit execution after stop has been called.
            if (stopping)
                throw new IllegalStateException("Failed to execute task due to grid shutdown: " + taskCls);

            callCnt++;
        }

        try {
            return startTask(null, taskCls, null, UUID.randomUUID(), timeout, lsnr, arg, false);
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
     * @param task Actual task.
     * @param arg Optional task argument.
     * @param timeout Task timeout.
     * @param lsnr Optional task listener.
     * @return Task future.
     * @param <T> Task argument type.
     * @param <R> Task return value type.
     */
    @SuppressWarnings({"deprecation"})
    public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg, long timeout,
        @Nullable GridTaskListener lsnr) {
        return execute(task, arg, timeout, lsnr, false);
    }

    /**
     * @param task Actual task.
     * @param arg Optional task argument.
     * @param timeout Task timeout.
     * @param lsnr Optional task listener.
     * @param sys If {@code true}, then system pool will be used.
     * @return Task future.
     * @param <T> Task argument type.
     * @param <R> Task return value type.
     */
    @SuppressWarnings({"deprecation"})
    public <T, R> GridTaskFuture<R> execute(GridTask<T, R> task, @Nullable T arg, long timeout,
        @Nullable GridTaskListener lsnr, boolean sys) {
        synchronized (mux) {
            // Prohibit execution after stop has been called.
            if (stopping)
                throw new IllegalStateException("Failed to execute task due to grid shutdown: " + task);

            callCnt++;
        }

        try {
            return startTask(null, null, task, UUID.randomUUID(), timeout, lsnr, arg, sys);
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
     * @param taskName Task name.
     * @param arg Optional execution argument.
     * @param timeout Execution timeout.
     * @param lsnr Optional task execution listener.
     * @return Task future.
     * @param <T> Task argument type.
     * @param <R> Task return value type.
     */
    @SuppressWarnings({"deprecation"})
    public <T, R> GridTaskFuture<R> execute(String taskName, @Nullable T arg, long timeout,
        @Nullable GridTaskListener lsnr) {
        assert taskName != null;
        assert timeout >= 0;

        synchronized (mux) {
            // Prohibit execution after stop has been called.
            if (stopping)
                throw new IllegalStateException("Failed to execute task due to grid shutdown: " + taskName);

            callCnt++;
        }

        try {
            return startTask(taskName, null, null, UUID.randomUUID(), timeout, lsnr, arg, false);
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
     * @param taskName Task name.
     * @param taskCls Task class.
     * @param task Task.
     * @param sesId Task session ID.
     * @param timeout Timeout.
     * @param lsnr Optional task listener.
     * @param arg Optional task argument.
     * @param sys If {@code true}, then system pool will be used.
     * @return Task future.
     * @param <T> Task argument type.
     * @param <R> Task return value type.
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    private <T, R> GridTaskFuture<R> startTask(
        @Nullable String taskName,
        @Nullable Class<? extends GridTask<T, R>> taskCls,
        @Nullable GridTask<T, R> task,
        UUID sesId,
        long timeout,
        @Nullable GridTaskListener lsnr,
        @Nullable T arg,
        boolean sys) {
        assert sesId != null;

        long endTime = timeout == 0 ? Long.MAX_VALUE : timeout + System.currentTimeMillis();

        long startTime = System.currentTimeMillis();

        // Account for overflow.
        if (endTime < 0)
            endTime = Long.MAX_VALUE;

        GridException deployEx = null;
        GridDeployment dep = null;

        // User provided task name.
        if (taskName != null) {
            assert taskCls == null;
            assert task == null;

            try {
                dep = ctx.deploy().getDeployment(taskName);

                if (dep == null)
                    throw new GridException("Unknown task name or failed to auto-deploy " +
                        "task (was task (re|un)deployed?): " + taskName);

                taskCls = (Class<? extends GridTask<T, R>>)dep.deployedClass(taskName);

                if (!GridTask.class.isAssignableFrom(taskCls))
                    throw new GridException("Failed to auto-deploy task (deployed class is not a task) [taskName=" +
                        taskName + ", depCls=" + taskCls + ']');
            }
            catch (GridException e) {
                deployEx = e;
            }
        }
        // Deploy user task class.
        else if (taskCls != null) {
            assert task == null;

            try {
                // Implicit deploy.
                dep = ctx.deploy().deploy(taskCls, U.detectClassLoader(taskCls));

                if (dep == null)
                    throw new GridException("Failed to auto-deploy task (was task (re|un)deployed?): " + taskCls);

                GridTaskName ann = dep.annotation(taskCls, GridTaskName.class);

                if (ann != null) {
                    taskName = ann.value();

                    if (F.isEmpty(taskName))
                        throw new GridException("Task name specified by @GridTaskName annotation cannot be " +
                            "empty for class: " + taskCls);
                }
                else {
                    taskName = taskCls.getName();
                }
            }
            catch (GridException e) {
                taskName = taskCls.getName();

                deployEx = e;
            }
        }
        // Deploy user task.
        else if (task != null) {
            taskCls = (Class<? extends GridTask<T, R>>)task.getClass();

            try {
                ClassLoader ldr;

                Class<?> cls;

                if (task instanceof GridPeerDeployAware) {
                    GridPeerDeployAware depAware = (GridPeerDeployAware)task;

                    cls = depAware.deployClass();
                    ldr = depAware.classLoader();

                    taskName = cls.getName();

                    // Implicit deploy.
                    dep = ctx.deploy().deploy(cls, ldr);
                }
                else {
                    cls = task.getClass();
                    ldr = U.detectClassLoader(cls);

                    // Implicit deploy.
                    dep = ctx.deploy().deploy(cls, ldr);

                    GridTaskName ann = dep.annotation(taskCls, GridTaskName.class);

                    if (ann != null) {
                        taskName = ann.value();

                        if (F.isEmpty(taskName))
                            throw new GridException("Task name specified by @GridTaskName annotation cannot be empty " +
                                "for class: " + taskCls);
                    }
                    else
                        taskName = taskCls.getName();
                }

                if (dep == null)
                    throw new GridException("Failed to auto-deploy task (was task (re|un)deployed?): " + taskCls);
            }
            catch (GridException e) {
                taskName = task.getClass().getName();

                deployEx = e;
            }
        }

        assert taskName != null;

        Collection<GridJobSibling> siblings = Collections.emptyList();

        Map<Object, Object> attrs = Collections.emptyMap();

        // Creates task session with task name and task version.
        GridTaskSessionImpl ses = ctx.session().createTaskSession(
            sesId,
            ctx.config().getNodeId(),
            taskName,
            dep == null ? "" : dep.userVersion(),
            dep == null ? 0 : dep.sequenceNumber(),
            taskCls == null ? null : taskCls.getName(),
            startTime,
            endTime,
            siblings,
            attrs
        );

        GridTaskFutureImpl<R> fut = new GridTaskFutureImpl<R>(ses, ctx);

        if (deployEx == null) {
            if (dep == null || !dep.acquire())
                handleException(lsnr, new GridException("Task not deployed: " + ses.getTaskName()), fut);
            else {
                Collection<? extends GridNode> subgrid = subgridCtx.get();

                if (subgrid.isEmpty())
                    handleException(lsnr, new GridEmptyProjectionException("Projection is empty."), fut);
                else {
                    GridTaskWorker<?, ?> taskWorker = new GridTaskWorker<T, R>(
                        ctx,
                        arg,
                        ses,
                        fut,
                        taskCls,
                        task,
                        dep,
                        lsnr,
                        new TaskEventListener(),
                        subgrid
                    );

                    synchronized (mux) {
                        if (task != null)
                            // Check if someone reuse the same task instance by walking
                            // through the "tasks" map
                            for (GridTaskWorker worker : tasks.values()) {
                                GridTask workerTask = worker.getTask();

                                // Check that the same instance of task is being used by comparing references.
                                if (workerTask != null && task == workerTask)
                                    U.warn(log, "Most likely the same task instance is being executed. " +
                                        "Please avoid executing the same task instances in parallel because " +
                                        "they may have concurrent resources access and conflict each other: " + task);
                            }

                        if (tasks.containsKey(sesId)) {
                            Throwable e = new GridException("Failed to create unique session ID (is node ID unique?): "
                                + sesId);

                            handleException(lsnr, e, fut);

                            return fut;
                        }
                        else
                            tasks.put(sesId, taskWorker);
                    }

                    try {
                        // Start task execution in another thread.
                        if (sys)
                            ctx.config().getSystemExecutorService().execute(taskWorker);
                        else
                            ctx.config().getExecutorService().execute(taskWorker);
                    }
                    catch (RejectedExecutionException e) {
                        synchronized (mux) {
                            tasks.remove(sesId);
                        }

                        release(dep);

                        handleException(lsnr, new GridExecutionRejectedException("Failed to execute task " +
                            "due to thread pool execution rejection: " + taskName, e), fut);
                    }
                }
            }
        }
        else
            handleException(lsnr, deployEx, fut);

        return fut;
    }

    /**
     * @param dep Deployment to release.
     */
    private void release(GridDeployment dep) {
        assert dep != null;

        dep.release();

        if (dep.isObsolete())
            ctx.resource().onUndeployed(dep);
    }

    /**
     * @param lsnr Task listener.
     * @param ex Exception.
     * @param fut Task future.
     * @param <R> Result type.
     */
    @SuppressWarnings({"deprecation"})
    private <R> void handleException(@Nullable GridTaskListener lsnr, Throwable ex, GridTaskFutureImpl<R> fut) {
        assert ex != null;
        assert fut != null;

        fut.onDone(ex);

        if (lsnr != null)
            lsnr.onFinished(fut);
    }

    /**
     * @param ses Task session.
     * @param attrs Attributes.
     * @throws GridException Thrown in case of any errors.
     */
    public void setAttributes(GridTaskSessionImpl ses, Map<?, ?> attrs) throws GridException {
        long timeout = ses.getEndTime() - System.currentTimeMillis();

        if (timeout <= 0) {
            U.warn(log, "Task execution timed out (remote session attributes won't be set): " + ses);

            return;
        }

        // If setting from task or future.
        if (log.isDebugEnabled()) {
            log.debug("Setting session attribute(s) from task or future: " + ses);
        }

        // Siblings should never be empty. However, future
        // may set attributes prior to map method being called.
        GridByteArrayList serAttrs = U.marshal(marshaller, attrs);

        sendSessionAttributes(serAttrs, attrs, ses);
    }

    /**
     * This method will make the best attempt to send attributes to all jobs.
     *
     * @param serAttrs Serialized session attributes.
     * @param attrs Deserialized session attributes.
     * @param ses Task session.
     * @throws GridException If send to any of the jobs failed.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "BusyWait"})
    private void sendSessionAttributes(GridByteArrayList serAttrs, Map<?, ?> attrs, GridTaskSessionImpl ses)
        throws GridException {
        assert serAttrs != null;
        assert attrs != null;
        assert ses != null;

        Collection<GridJobSibling> siblings = ses.getJobSiblings();

        GridIoManager commMgr = ctx.io();

        long timeout = ses.getEndTime() - System.currentTimeMillis();

        if (timeout <= 0) {
            U.warn(log, "Session attributes won't be set due to task timeout: " + attrs);

            return;
        }

        Map<UUID, Long> msgIds = new HashMap<UUID, Long>(siblings.size(), 1.0f);

        UUID locNodeId = ctx.discovery().localNode().id();

        synchronized (ses) {
            if (ses.isClosed()) {
                if (log.isDebugEnabled())
                    log.debug("Setting session attributes on closed session (will ignore): " + ses);

                return;
            }

            ses.setInternal(attrs);

            // Do this inside of synchronization block, so every message
            // ID will be associated with a certain session state.
            for (GridJobSibling s : siblings) {
                GridJobSiblingImpl sibling = (GridJobSiblingImpl)s;

                UUID nodeId = sibling.nodeId();

                if (!nodeId.equals(locNodeId) && !sibling.isJobDone() && !msgIds.containsKey(nodeId))
                    msgIds.put(nodeId, commMgr.getNextMessageId(sibling.jobTopic(), nodeId));
            }
        }

        if (ctx.event().isRecordable(EVT_TASK_SESSION_ATTR_SET)) {
            GridTaskEvent evt = new GridTaskEvent();

            evt.message("Changed attributes: " + attrs);
            evt.nodeId(ctx.discovery().localNode().id());
            evt.taskName(ses.getTaskName());
            evt.taskSessionId(ses.getId());
            evt.type(EVT_TASK_SESSION_ATTR_SET);

            ctx.event().record(evt);
        }

        GridException ex = null;

        // Every job gets an individual message to keep track of ghost requests.
        for (GridJobSibling s : ses.getJobSiblings()) {
            GridJobSiblingImpl sibling = (GridJobSiblingImpl)s;

            UUID nodeId = sibling.nodeId();

            Long msgId = msgIds.remove(nodeId);

            // Pair can be null if job is finished.
            if (msgId != null) {
                assert msgId > 0;

                GridNode node = ctx.discovery().node(nodeId);

                // Check that node didn't change (it could happen in case of failover).
                if (node != null) {
                    GridTaskSessionRequest req = new GridTaskSessionRequest(ses.getId(), null, serAttrs);

                    try {
                        commMgr.sendOrderedMessage(
                            node,
                            sibling.jobTopic(),
                            msgId,
                            req,
                            GridIoPolicy.SYSTEM_POOL,
                            timeout);
                    }
                    catch (GridException e) {
                        node = ctx.discovery().node(nodeId);

                        if (node != null) {
                            try {
                                // Since communication on remote node may stop before
                                // we get discovery notification, we give ourselves the
                                // best effort to detect it.
                                Thread.sleep(DISCO_TIMEOUT);
                            }
                            catch (InterruptedException ignore) {
                                U.warn(log, "Got interrupted while sending session attributes.");
                            }

                            node = ctx.discovery().node(nodeId);
                        }

                        String err = "Failed to send session attribute request message to node " +
                            "(normal case if node left grid) [node=" + node + ", req=" + req + ']';

                        if (node != null)
                            U.warn(log, err);
                        else if (log.isDebugEnabled())
                            log.debug(err);

                        if (ex == null)
                            ex = e;
                    }
                }
            }
        }

        if (ex != null)
            throw ex;
    }

    /**
     * Listener for individual task events.
     */
    @SuppressWarnings({"deprecation"})
    private class TaskEventListener implements GridTaskEventListener {
        /** */
        private final GridMessageListener msgLsnr = new JobMessageListener();

        /** {@inheritDoc} */
        @Override public void onTaskStarted(GridTaskWorker<?, ?> worker) {
            // Register for timeout notifications.
            ctx.timeout().addTimeoutObject(worker);

            ctx.checkpoint().onSessionStart(worker.getSession());
        }

        /** {@inheritDoc} */
        @Override public void onJobSend(GridTaskWorker<?, ?> worker, GridJobSiblingImpl sibling) {
            // Listener is stateless, so same listener can be reused for all jobs.
            ctx.io().addMessageListener(sibling.taskTopic(), msgLsnr);
        }

        /** {@inheritDoc} */
        @Override public void onJobFailover(GridTaskWorker<?, ?> worker, GridJobSiblingImpl sibling, UUID nodeId) {
            GridIoManager ioMgr = ctx.io();

            // Remove message ID registration and old listener.
            ioMgr.removeMessageId(sibling.jobTopic());
            ioMgr.removeMessageListener(sibling.taskTopic(), msgLsnr);

            synchronized (worker.getSession()) {
                // Reset ID on sibling prior to sending request.
                sibling.nodeId(nodeId);
            }

            // Register new listener on new topic.
            ioMgr.addMessageListener(sibling.taskTopic(), msgLsnr);
        }

        /** {@inheritDoc} */
        @Override public void onJobFinished(GridTaskWorker<?, ?> worker, GridJobSiblingImpl sibling) {
            // Mark sibling finished for the purpose of
            // setting session attributes.
            synchronized (worker.getSession()) {
                sibling.onJobDone();
            }
        }

        /** {@inheritDoc} */
        @Override public void onTaskFinished(GridTaskWorker<?, ?> worker) {
            synchronized (worker.getSession()) {
                worker.getSession().onClosed();
            }

            synchronized (mux) {
                tasks.remove(worker.getTaskSessionId());

                mux.notifyAll();
            }

            GridTaskSessionImpl ses = worker.getSession();

            ctx.checkpoint().onSessionEnd(ses, false);

            // Delete session altogether.
            ctx.session().removeSession(ses.getId());

            // Unregister from timeout notifications.
            ctx.timeout().removeTimeoutObject(worker);

            release(worker.getDeployment());

            // Unregister job message listener from all job topics.
            try {
                for (GridJobSibling sibling : worker.getSession().getJobSiblings()) {
                    GridJobSiblingImpl s = (GridJobSiblingImpl)sibling;

                    ctx.io().removeMessageId(s.jobTopic());
                    ctx.io().removeMessageListener(s.taskTopic(), msgLsnr);
                }
            }
            catch (GridException e) {
                U.error(log, "Failed to unregister job communication message listeners and counters.", e);
            }
        }
    }

    /**
     * Handles job execution responses.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     */
    @SuppressWarnings({"deprecation"})
    private class JobMessageListener implements GridMessageListener {
        @Override public void onMessage(UUID nodeId, Object msg) {
            if (!(msg instanceof GridTaskMessage)) {
                U.warn(log, "Received message of unknown type: " + msg);

                return;
            }

            GridTaskWorker<?, ?> task;

            synchronized (mux) {
                if (stopping && !waiting) {
                    U.warn(log, "Received job execution response while stopping grid (will ignore): " + msg);

                    return;
                }

                task = tasks.get(((GridTaskMessage)msg).getSessionId());

                if (task == null) {
                    if (log.isDebugEnabled())
                        log.debug("Received task message for unknown task (was task already reduced?): " + msg);

                    return;
                }

                callCnt++;
            }

            try {
                if (msg instanceof GridJobExecuteResponse)
                    processJobExecuteResponse(nodeId, (GridJobExecuteResponse)msg, task);
                else if (msg instanceof GridTaskSessionRequest)
                    processTaskSessionRequest(nodeId, (GridTaskSessionRequest)msg, task);
                else
                    U.warn(log, "Received message of unknown type: " + msg);
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
         * @param nodeId Node ID.
         * @param msg Execute response message.
         * @param task Grid task worker.
         */
        private void processJobExecuteResponse(UUID nodeId, GridJobExecuteResponse msg, GridTaskWorker<?, ?> task) {
            assert nodeId != null;
            assert msg != null;
            assert task != null;

            if (log.isDebugEnabled())
                log.debug("Received grid job response message [msg=" + msg + ", nodeId=" + nodeId + ']');

            task.onResponse(msg);
        }

        /**
         * @param nodeId Node ID.
         * @param msg Execute response message.
         * @param task Grid task worker.
         */
        @SuppressWarnings({"unchecked"})
        private void processTaskSessionRequest(UUID nodeId, GridTaskSessionRequest msg, GridTaskWorker task) {
            assert nodeId != null;
            assert msg != null;
            assert task != null;

            try {
                Map<Object, Object> attrs = U.unmarshal(marshaller, msg.getAttributes(),
                    task.getTask().getClass().getClassLoader());

                GridTaskSessionImpl ses = task.getSession();

                sendSessionAttributes(msg.getAttributes(), attrs, ses);
            }
            catch (GridException e) {
                U.error(log, "Failed to deserialize session request: " + msg, e);
            }
        }
    }

    /**
     * Listener to node discovery events.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class TaskDiscoveryListener implements GridLocalEventListener {
        /** {@inheritDoc} */
        @Override public void onEvent(GridEvent evt) {
            assert evt instanceof GridDiscoveryEvent;

            UUID nodeId = ((GridDiscoveryEvent)evt).eventNodeId();

            if (evt.type() == EVT_NODE_FAILED || evt.type() == EVT_NODE_LEFT) {
                List<GridTaskWorker<?, ?>> taskList;

                synchronized (mux) {
                    if (stopping && !waiting) {
                        U.warn(log, "Task executor received discovery event while stopping (will ignore): " + evt);

                        return;
                    }

                    taskList = new ArrayList<GridTaskWorker<?, ?>>(tasks.values());

                    callCnt++;
                }

                // Outside of synchronization.
                try {
                    for (GridTaskWorker<?, ?> task : taskList)
                        task.onNodeLeft(nodeId);
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

    /** */
    private class JobSiblingsMessageHandler implements
        GridIoSyncMessageHandler<GridJobSiblingsRequest, Collection<GridJobSibling>> {
        /** {@inheritDoc} */
        @Nullable @Override public Collection<GridJobSibling> handleMessage(UUID nodeId, GridJobSiblingsRequest msg) {
            UUID sesId = msg.getSessionId();

            GridTaskWorker<?, ?> worker;

            synchronized (mux) {
                worker = tasks.get(sesId);
            }

            if (worker != null)
                try {
                    return worker.getSession().getJobSiblings();
                }
                catch (GridException e) {
                    U.error(log, "Failed to get job siblings [request=" + msg + ", ses=" + worker.getSession() + ']', e);

                    return null;
                }

            if (log.isDebugEnabled())
                log.debug("Received job siblings request for unknown or finished task (will ignore): " + msg);

            return null;
        }
    }
}
