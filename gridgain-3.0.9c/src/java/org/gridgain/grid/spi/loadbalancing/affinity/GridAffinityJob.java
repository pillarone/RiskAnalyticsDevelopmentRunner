// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.loadbalancing.affinity;

import org.gridgain.grid.*;

/**
 * Affinity job which extends {@link GridJob} and adds
 * {@link #getAffinityKey()} method. Basically the job should know the
 * data it will access and provide the key for it to GridGain which will
 * route it to the grid node on which this data is cached.
 * <p>
 * Note that if {@link GridAffinityLoadBalancingSpi} will receive regular
 * {@link GridJob} instead of {@code GridAffinityJob}, then a random
 * available node will be picked.
 * <p>
 * Here is an example of how affinity job is implemented:
 * <pre name="code" class="java">
 * public class MyGridAffinityJob extends GridAffinityJobAdapter&lt;Integer, Serializable&gt; {
 *    public MyGridAffinityJob(Integer cacheKey) {
 *        // Pass cache key as a job argument.
 *        super(cacheKey);
 *    }
 *
 *    public Serializable execute() throws GridException {
 *        ...
 *        // Access data by the same key returned
 *        // in 'getAffinityKey()' method.
 *        mycache.get(getAffinityKey());
 *        ...
 *    }
 * }
 * </pre>
 * <p>
 * Here is another example on how it can be used from task {@code map} (or {@code split}) method.
 * <pre name="code" class="java">
 * public class MyFooBarAffinityTask extends GridTaskSplitAdapter&lt;List&lt;Integer&gt;,Object&gt; {
 *    // For this example we receive a list of cache keys and for every key
 *    // create a job that accesses it.
 *    &#64;Override
 *    protected Collection&lt;? extends GridJob&gt; split(int gridSize, List&lt;Integer&gt; cacheKeys) throws GridException {
 *        List&lt;MyGridAffinityJob&gt; jobs = new ArrayList&lt;MyGridAffinityJob&gt;(gridSize);
 *
 *        for (Integer cacheKey : cacheKeys) {
 *            jobs.add(new MyGridAffinityJob(cacheKey));
 *        }
 *
 *        // Node assignment via load balancer
 *        // happens automatically.
 *        return jobs;
 *    }
 *    ...
 * }
 * </pre>
 * For complete documentation on how affinity jobs are created and used, refer to {@link GridAffinityLoadBalancingSpi}
 * documentation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <A> Affinity key type.
 */
public interface GridAffinityJob<A> extends GridJob {
    /**
     * Gets affinity key for this job.
     *
     * @return Affinity key for this job.
     */
    public A getAffinityKey();
}
