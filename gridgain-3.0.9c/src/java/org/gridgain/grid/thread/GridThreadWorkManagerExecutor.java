// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.thread;

import commonj.work.*;
import org.gridgain.grid.*;
import javax.naming.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * CommonJ-based wrapper for {@link ExecutorService}.
 * Implementation delegates all execution request to the work manager. Note that CommonJ
 * work manager is used and/or supported by wide verity of application servers and frameworks
 * such as Coherence, Weblogic, Websphere, Spring, Globus, apache projects, etc.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridThreadWorkManagerExecutor extends AbstractExecutorService {
    /** Work manager with all tasks are delegated to. */
    private final WorkManager workMgr;

    /** Whether service is being stopped or not. */
    private boolean isBeingShutdown;

    /** List of executing or scheduled for execution works. */
    private final Collection<WorkItem> works = new ArrayList<WorkItem>();

    /** Rejected or completed tasks listener. */
    private final WorkListener termListener = new WorkTerminateListener();

    /** */
    private final Object mux = new Object();

    /**
     * Creates new instance of execution service based on CommonJ implementation.
     *
     * @param jndiName Work manager JNDI name.
     * @throws GridException If work manager is unreachable.
     */
    @SuppressWarnings({"JNDIResourceOpenedButNotSafelyClosed"})
    public GridThreadWorkManagerExecutor(String jndiName) throws GridException {
        InitialContext ctx = null;

        try {
            // Create initial context and obtain work manager.
            ctx = new InitialContext();

            workMgr = (WorkManager)ctx.lookup(jndiName);
        }
        catch(NamingException e) {
            throw new GridException("Failed to obtain initial context or lookup given JNDI name: " + jndiName, e);
        }
        finally {
            closeCtx(ctx);
        }
    }

    /**
     * @param ctx JNDI context to close.
     * @throws GridException Thrown in case of failure closing initial context.
     */
    private void closeCtx(InitialContext ctx) throws GridException {
        if (ctx != null) {
            try {
                ctx.close();
            }
            catch (NamingException e) {
                throw new GridException("Failed to close initial context.", e);
            }
        }
    }

    /**
     * Creates new instance of execution service based on CommonJ implementation.
     *
     * @param mgr Work manager.
     */
    public GridThreadWorkManagerExecutor(WorkManager mgr) {
        workMgr = mgr;
    }

    /** {@inheritDoc} */
    @Override public void execute(Runnable command) {
        Work work = new RunnableWorkAdapter(command);

        try {
            synchronized(mux) {
                // If service is being shut down reject all requests.
                if (!isBeingShutdown) {
                    // We put it here to control shutdown process.
                    works.add(workMgr.schedule(work, termListener));
                }
                else {
                    throw new RejectedExecutionException("Failed to execute command (service is being shut down).");
                }
            }
        }
        catch (WorkException e) {
            // Unfortunately RejectedExecutionException is the closest thing we have
            // as proper RuntimeException.
            throw new RejectedExecutionException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public void shutdown() {
        synchronized(mux) {
            if (isBeingShutdown) {
                return;
            }

            isBeingShutdown = true;
        }

        // Wait for all tasks to be finished or rejected.
        synchronized(works) {
            if (!works.isEmpty()) {
                try {
                    if (!workMgr.waitForAll(works, WorkManager.INDEFINITE)) {
                        throw new IllegalStateException("Failed to shutdown service properly " +
                            "(tasks execution is timed out).");
                    }
                }
                catch (InterruptedException e) {
                    throw new IllegalStateException("Failed to shutdown service properly " +
                        "(waiting was interrupted).", e);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public List<Runnable> shutdownNow() {
        // Since we do not control execution we have to
        // wait until all tasks are executed. It's a conventional
        // implementation.
        shutdown();

        return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override public boolean isShutdown() {
        synchronized(mux) {
            synchronized(works) {
                return isBeingShutdown && works.isEmpty();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isTerminated() {
        return isShutdown();
    }

    /** {@inheritDoc} */
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        // Wait for all tasks to be finished or rejected.
        synchronized(works) {
            if (!works.isEmpty()) {
                if (!workMgr.waitForAll(works, unit.toMillis(timeout))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Listener to track works.
     */
    private class WorkTerminateListener implements WorkListener {
        /** {@inheritDoc} */
        @Override public void workAccepted(WorkEvent workEvent) {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public void workRejected(WorkEvent workEvent) {
            synchronized (works) {
                works.remove(workEvent.getWorkItem());
            }
        }

        /** {@inheritDoc} */
        @Override public void workStarted(WorkEvent workEvent) {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public void workCompleted(WorkEvent workEvent) {
            synchronized (works) {
                works.remove(workEvent.getWorkItem());
            }
        }
    }

    /**
     * Compatibility {@link Work} adapter.
     */
    private static final class RunnableWorkAdapter implements Work {
        /** Command which is wrapped by adapter. */
        private final Runnable cmd;

        /**
         * Creates new adapter for the given command.
         *
         * @param cmd Command to execute.
         */
        private RunnableWorkAdapter(Runnable cmd) { this.cmd = cmd; }

        /** {@inheritDoc} */
        @Override public void release() {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public boolean isDaemon() {
            return false;
        }

        /** {@inheritDoc} */
        @Override public void run() {
            cmd.run();
        }
    }
}
