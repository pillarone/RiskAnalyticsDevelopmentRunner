// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.gridify.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.collision.*;
import org.gridgain.grid.spi.failover.*;
import org.jetbrains.annotations.*;
import java.io.*;
import java.util.*;

/**
 * Grid task interface defines a task that can be executed on the grid. Grid task
 * is responsible for splitting business logic into multiple grid jobs, receiving
 * results from individual grid jobs executing on remote nodes, and reducing
 * (aggregating) received jobs' results into final grid task result.
 * <p>
 * <h1 class="header">Grid Task Execution Sequence</h1>
 * <ol>
 * <li>
 *      Upon request to execute a grid task with given task name system will find
 *      deployed task with given name. Task needs to be deployed prior to execution
 *      (see {@link Grid#deployTask(Class)} method), however if task does not specify
 *      its name explicitly via {@link GridTaskName @GridTaskName} annotation, it
 *      will be auto-deployed first time it gets executed.
 * </li>
 * <li>
 *      System will create new distributed task session (see {@link GridTaskSession}).
 * </li>
 * <li>
 *      System will inject all annotated resources (including task session) into grid task instance.
 *      See <a href="http://www.gridgain.com/javadoc/org/gridgain/grid/resources/package-summary.html">org.gridgain.grid.resources</a>
 *      package for the list of injectable resources.
 * </li>
 * <li>
 *      System will apply {@link #map(List, Object) map(List, Object)}. This
 *      method is responsible for splitting business logic of grid task into
 *      multiple grid jobs (units of execution) and mapping them to
 *      grid nodes. Method {@link #map(List, Object) map(List, Object)} returns
 *      a map of with grid jobs as keys and grid node as values.
 * </li>
 * <li>
 *      System will send mapped grid jobs to their respective nodes.
 * </li>
 * <li>
 *      Upon arrival on the remote node a grid job will be handled by collision SPI
 *      (see {@link GridCollisionSpi}) which will determine how a job will be executed
 *      on the remote node (immediately, buffered or canceled).
 * </li>
 * <li>
 *      Once job execution results become available method {@link #result(GridJobResult, List) result(GridJobResult, List)}
 *      will be called for each received job result. The policy returned by this method will
 *      determine the way task reacts to every job result:
 *      <ul>
 *      <li>
 *          If {@link GridJobResultPolicy#WAIT} policy is returned, task will continue to wait
 *          for other job results. If this result is the last job result, then
 *          {@link #reduce(List) reduce(List)} method will be called.
 *      </li>
 *      <li>
 *          If {@link GridJobResultPolicy#REDUCE} policy is returned, then method
 *          {@link #reduce(List) reduce(List)} will be called right away without waiting for
 *          other jobs' completion (all remaining jobs will receive a cancel request).
 *      </li>
 *      <li>
 *          If {@link GridJobResultPolicy#FAILOVER} policy is returned, then job will
 *          be failed over to another node for execution. The node to which job will get
 *          failed over is decided by {@link GridFailoverSpi} SPI implementation.
 *          Note that if you use {@link GridTaskAdapter} adapter for {@code GridTask}
 *          implementation, then it will automatically fail jobs to another node for 2
 *          known failure cases:
 *          <ul>
 *          <li>
 *              Job has failed due to node crash. In this case {@link GridJobResult#getException()}
 *              method will return an instance of {@link GridTopologyException} exception.
 *          </li>
 *          <li>
 *              Job execution was rejected, i.e. remote node has cancelled job before it got
 *              a chance to execute, while it still was on the waiting list. In this case
 *              {@link GridJobResult#getException()} method will return an instance of
 *              {@link GridExecutionRejectedException} exception.
 *          </li>
 *          </ul>
 *      </li>
 *      </ul>
 * </li>
 * <li>
 *      Once all results are received or {@link #result(GridJobResult, List) result(GridJobResult, List)}
 *      method returned {@link GridJobResultPolicy#REDUCE} policy, method {@link #reduce(List) reduce(List)}
 *      is called to aggregate received results into one final result. Once this method is finished the
 *      execution of the grid task is complete. This result will be returned to the user through
 *      {@link GridTaskFuture#get()} method.
 * </li>
 * </ol>
 * <p>
 * <h1 class="header">Resource Injection</h1>
 * Grid task implementation can be injected using IoC (dependency injection) with
 * grid resources. Both, field and method based injection are supported.
 * The following grid resources can be injected:
 * <ul>
 * <li>{@link GridTaskSessionResource}</li>
 * <li>{@link GridInstanceResource}</li>
 * <li>{@link GridLoggerResource}</li>
 * <li>{@link GridHomeResource}</li>
 * <li>{@link GridExecutorServiceResource}</li>
 * <li>{@link GridLocalNodeIdResource}</li>
 * <li>{@link GridMBeanServerResource}</li>
 * <li>{@link GridMarshallerResource}</li>
 * <li>{@link GridSpringApplicationContextResource}</li>
 * <li>{@link GridSpringResource}</li>
 * </ul>
 * Refer to corresponding resource documentation for more information.
 * <p>
 * <h1 class="header">Grid Task Adapters</h1>
 * {@code GridTask} comes with several convenience adapters to make the usage easier:
 * <ul>
 * <li>
 * {@link GridTaskAdapter} provides default implementation for {@link GridTask#result(GridJobResult, List)}
 * method which provides automatic fail-over to another node if remote job has failed
 * due to node crash (detected by {@link GridTopologyException} exception) or due to job
 * execution rejection (detected by {@link GridExecutionRejectedException} exception).
 * Here is an example of how a you would implement your task using {@link GridTaskAdapter}:
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
 * </li>
 * <li>
 * {@link GridTaskSplitAdapter} hides the job-to-node mapping logic from
 * user and provides convenient {@link GridTaskSplitAdapter#split(int, Object)}
 * method for splitting task into sub-jobs in homogeneous environments.
 * Here is an example of how you would implement your task using {@link GridTaskSplitAdapter}:
 * <pre name="code" class="java">
 * public class MyFooBarTask extends GridTaskSplitAdapter&lt;Object, String&gt; {
 *     &#64;Override
 *     protected Collection&lt;? extends GridJob&gt; split(int gridSize, Object arg) throws GridException {
 *         List&lt;MyFooBarJob&gt; jobs = new ArrayList&lt;MyFooBarJob&gt;(gridSize);
 *
 *         for (int i = 0; i &lt; gridSize; i++) {
 *             jobs.add(new MyFooBarJob(arg));
 *         }
 *
 *         // Node assignment via load balancer
 *         // happens automatically.
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
 * </li>
 * </ul>
 * Refer to corresponding adapter documentation for more information.
 * <p>
 * <h1 class="header">Examples</h1>
 * Many task example usages are available on GridGain <a href="http://wiki.gridgain.org" target="_top">Wiki</a>.
 * To see example on how to use {@code GridTask} for basic split/aggregate logic refer to
 * <a href="http://www.gridgainsystems.com:8080/wiki/display/GG15UG/HelloWorld+-+Basic+Grid+Task" target="_top">HelloWorld Task Example</a>.
 * For example on how to use {@code GridTask} with automatic grid-enabling via
 * {@link Gridify @Gridify} annotation refer to
 * <a href="http://www.gridgainsystems.com:8080/wiki/display/GG15UG/HelloWorld+-+Gridify+With+Custom+Task" target="_top">Gridify HelloWorld Example</a>.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <T> Type of the task argument that is passed into {@link GridTask#map(List, Object)} method.
 * @param <R> Type of the task result returning from {@link GridTask#reduce(List)} method.
 */
public interface GridTask<T, R> extends Serializable {
    /**
     * This method is called to map or split grid task into multiple grid jobs. This is the
     * first method that gets called when task execution starts.
     *
     * @param arg Task execution argument. Can be {@code null}. This is the same argument
     *      as the one passed into {@code Grid#execute(...)} methods.
     * @param subgrid Nodes available for this task execution. Note that order of nodes is
     *      guaranteed to be randomized by container. This ensures that every time
     *      you simply iterate through grid nodes, the order of nodes will be random which
     *      over time should result into all nodes being used equally.
     * @return Map of grid jobs assigned to subgrid node. If {@code null} or empty map
     *      is returned, exception will be thrown.
     * @throws GridException If mapping could not complete successfully. This exception will be
     *      thrown out of {@link GridTaskFuture#get()} method.
     */
    @Nullable public Map<? extends GridJob, GridNode> map(List<GridNode> subgrid, @Nullable T arg) throws GridException;

    /**
     * Asynchronous callback invoked every time a result from remote execution is
     * received. It is ultimately upto this method to return a policy based
     * on which the system will either wait for more results, reduce results
     * received so far, or failover this job to another node. See
     * {@link GridJobResultPolicy} for more information about result policies.
     *
     * @param res Received remote grid executable result.
     * @param rcvd All previously received results. Note that if task class has
     *      {@link GridTaskNoResultCache} annotation, actual job result won't be cached
     *      and {@link GridJobResult#getData()} method will return {@code null}.
     * @return Result policy that dictates how to process further upcoming
     *       job results.
     * @throws GridException If handling a job result caused an error. This exception will
     *      be thrown out of {@link GridTaskFuture#get()} method.
     */
    public GridJobResultPolicy result(GridJobResult res, List<GridJobResult> rcvd) throws GridException;

    /**
     * Reduces (or aggregates) results received so far into one compound result to be returned to
     * caller via {@link GridTaskFuture#get()} method.
     * <p>
     * Note, that if some jobs did not succeed and could not be failed over then the list of
     * results passed into this method will include the failed results. Otherwise, failed
     * results will not be in the list.
     *
     * @param results Received results of broadcasted remote executions. Note that if task class has
     *      {@link GridTaskNoResultCache} annotation, actual job results won't be cached
     *      and {@link GridJobResult#getData()} methods will return {@code null}.
     * @return Grid job result constructed from results of remote executions.
     * @throws GridException If reduction or results caused an error. This exception will
     *      be thrown out of {@link GridTaskFuture#get()} method.
     */
    @Nullable public R reduce(List<GridJobResult> results) throws GridException;
}
