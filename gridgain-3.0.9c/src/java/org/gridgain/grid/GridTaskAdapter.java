// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import java.util.*;

import static org.gridgain.grid.GridJobResultPolicy.*;

/**
 * Convenience adapter for {@link GridTask} interface. Here is an example of
 * how {@code GridTaskAdapter} can be used:
 * <pre name="code" class="java">
 * public class MyFooBarTask extends GridTaskAdapter&lt;String, String&gt; {
 *     // Inject load balancer.
 *     &#64;GridLoadBalancerResource
 *     GridLoadBalancer balancer;
 *
 *     // Map jobs to grid nodes.
 *     public Map&lt;? extends GridJob, GridNode&gt; map(List&lt;GridNode&gt; subgrid, String arg) throws GridException {
 *         Map&lt;MyFooBarJob, GridNode&gt; jobs = new HashMap&lt;MyFooBarJob, GridNode&gt;(subgrid.size());
 *
 *         // In more complex cases, you can actually do
 *         // more complicated assignments of jobs to nodes.
 *         for (int i = 0; i &lt; subgrid.size(); i++) {
 *             // Pick the next best balanced node for the job.
 *             jobs.put(new MyFooBarJob(arg), balancer.getBalancedNode())
 *         }
 *
 *         return jobs;
 *     }
 *
 *     // Aggregate results into one compound result.
 *     public String reduce(List&lt;GridJobResult&gt; results) throws GridException {
 *         // For the purpose of this example we simply
 *         // concatenate string representation of every
 *         // job result
 *         StringBuilder buf = new StringBuilder();
 *
 *         for (GridJobResult res : results) {
 *             // Append string representation of result
 *             // returned by every job.
 *             buf.append(res.getData().string());
 *         }
 *
 *         return buf.string();
 *     }
 * }
 * </pre>
 * For more information refer to {@link GridTask} documentation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <T> Type of the task argument.
 * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
 */
public abstract class GridTaskAdapter<T, R> extends GridMetadataAwareAdapter implements GridTask<T, R>,
    GridPeerDeployAware {
    /** Peer deploy aware class. */
    private transient volatile GridPeerDeployAware pda;

    /** {@inheritDoc} */
    @Override public Class<?> deployClass() {
        if (pda == null)
            pda = U.detectPeerDeployAware(this);

        return pda.deployClass();
    }

    /** {@inheritDoc} */
    @Override public ClassLoader classLoader() {
        if (pda == null)
            pda = U.detectPeerDeployAware(this);

        return pda.classLoader();
    }

    /**
     * Empty constructor.
     */
    protected GridTaskAdapter() {
        // No-op.
    }

    /**
     * Constructor that receives deployment information for task.
     *
     * @param pda Deployment information.
     */
    protected GridTaskAdapter(GridPeerDeployAware pda) {
        setPeerDeployAware(pda);
    }

    /**
     * Sets deployment information for this task.
     *
     * @param pda Deployment information.
     */
    public void setPeerDeployAware(GridPeerDeployAware pda) {
        assert pda != null;

        this.pda = pda;
    }

    /**
     * Default implementation which will wait for all jobs to complete before
     * calling {@link #reduce(List)} method.
     * <p>
     * If remote job resulted in exception ({@link GridJobResult#getException()} is not {@code null}),
     * then {@link GridJobResultPolicy#FAILOVER} policy will be returned if the exception is instance
     * of {@link GridTopologyException} or {@link GridExecutionRejectedException}, which means that
     * remote node either failed or job execution was rejected before it got a chance to start. In all
     * other cases the exception will be rethrown which will ultimately cause task to fail.
     *
     * @param res Received remote grid executable result.
     * @param rcvd All previously received results.
     * @return Result policy that dictates how to process further upcoming
     *       job results.
     * @throws GridException If handling a job result caused an error effectively rejecting
     *      a failover. This exception will be thrown out of {@link GridTaskFuture#get()} method.
     */
    @Override public GridJobResultPolicy result(GridJobResult res, List<GridJobResult> rcvd) throws GridException {
        GridException e = res.getException();

        // Try to failover if result is failed.
        if (e != null) {
            // Don't failover user's code errors.
            if (e instanceof GridExecutionRejectedException || e instanceof GridTopologyException)
                return FAILOVER;

            throw new GridException("Remote job threw user exception (override or implement GridTask.result(..) " +
                "method if you would like to have automatic failover for this exception).", e);
        }

        // Wait for all job responses.
        return WAIT;
    }
}
