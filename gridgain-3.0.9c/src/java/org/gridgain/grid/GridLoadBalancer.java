// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.spi.loadbalancing.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Load balancer is used for finding the best balanced node according
 * to load balancing policy. Internally load balancer will
 * query the {@link GridLoadBalancingSpi}
 * to get the balanced node.
 * <p>
 * Load balancer can be used <i>explicitly</i> from inside {@link GridTask#map(List, Object)}
 * method when you implement {@link GridTask} interface directly or use
 * {@link GridTaskAdapter}. If you use {@link GridTaskSplitAdapter} then
 * load balancer is accessed <i>implicitly</i> by the adapter so you don't have
 * to use it directly in your logic.
 * <h1 class="header">Coding Examples</h1>
 * If you are using {@link GridTaskSplitAdapter} then load balancing logic
 * is transparent to your code and is handled automatically by the adapter.
 * Here is an example of how your task will look:
 * <pre name="code" class="java">
 * public class MyFooBarTask extends GridTaskSplitAdapter&lt;String> {
 *     &#64;Override
 *     protected Collection&lt;? extends GridJob> split(int gridSize, String arg) throws GridException {
 *         List&lt;MyFooBarJob> jobs = new ArrayList&lt;MyFooBarJob>(gridSize);
 *
 *         for (int i = 0; i &lt; gridSize; i++) {
 *             jobs.add(new MyFooBarJob(arg));
 *         }
 *
 *         // Node assignment via load balancer
 *         // happens automatically.
 *         return jobs;
 *     }
 *     ...
 * }
 * </pre>
 * If you need more fine-grained control over how some jobs within task get mapped to a node
 * and use affinity load balancing for some other jobs within task, then you should use
 * {@link GridTaskAdapter}. Here is an example of how your task will look. Note that in this
 * case we manually inject load balancer and use it to pick the best node. Doing it in
 * such way would allow user to map some jobs manually and for others use load balancer.
 * <pre name="code" class="java">
 * public class MyFooBarTask extends GridTaskAdapter&lt;String, String> {
 *     // Inject load balancer.
 *     &#64;GridLoadBalancerResource
 *     GridLoadBalancer balancer;
 *
 *     // Map jobs to grid nodes.
 *     public Map&lt;? extends GridJob, GridNode> map(List&lt;GridNode> subgrid, String arg) throws GridException {
 *         Map&lt;MyFooBarJob, GridNode> jobs = new HashMap&lt;MyFooBarJob, GridNode>(subgrid.size());
 *
 *         // In more complex cases, you can actually do
 *         // more complicated assignments of jobs to nodes.
 *         for (int i = 0; i &lt; subgrid.size(); i++) {
 *             // Pick the next best balanced node for the job.
 *             GridJob myJob = new MyFooBarJob(arg);
 *
 *             jobs.put(myJob, balancer.getBalancedNode(myJob, null));
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
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridLoadBalancer extends GridMetadataAware {
    /**
     * Gets the next balanced node according to the underlying load balancing policy.
     *
     * @param job Job to get the balanced node for.
     * @param exclNodes Optional collection of nodes that should be excluded from balanced nodes.
     *      If collection is {@code null} or empty - no nodes will be excluded.
     * @return Next balanced node.
     * @throws GridException If any error occurred when finding next balanced node.
     */
    @Nullable public GridNode getBalancedNode(GridJob job, @Nullable Collection<GridNode> exclNodes)
        throws GridException;
}
