// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.loadbalancing.coherence;

import org.gridgain.grid.*;

/**
 * Affinity job which extends {@link GridJob} and asks user to implement
 * {@link #getAffinityKey()} and {@link #getCacheName()} methods. Basically
 * the job should know the data it will access and provide the key and
 * Coherence cache name for it to GridGain which will route it to the
 * grid node on which this data is cached.
 * <p>
 * Here is an example of how affinity job is implemented:
 * <pre name="code" class="java">
 * public class MyFooBarCoherenceAffinityJob extends GridCoherenceAffinityJobAdapter&lt;Integer, Serializable&gt; {
 *    ...
 *    private static final String CACHE_NAME = "myDistributedCache";
 *
 *    public MyFooBarCoherenceAffinityJob(Integer cacheKey) {
 *        super(CACHE_NAME, cacheKey);
 *    }
 *
 *    public Serializable execute() throws GridException {
 *        ...
 *        // Access data by the same key returned in 'getAffinityKey()' method
 *        // and for cache with name returned in 'getCacheName()'.
 *        NamedCache mycache = CacheFactory.cache(getCacheName);
 *
 *        mycache.get(getAffinityKey());
 *        ...
 *    }
 * }
 * </pre>
 * For complete documentation on how affinity jobs are created and used, refer to {@link GridCoherenceLoadBalancingSpi}
 * documentation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <A> Affinity key type.
 */
public interface GridCoherenceAffinityJob<A> extends GridJob {
    /**
     * Gets affinity key for this job.
     *
     * @return Affinity key for this job.
     */
    public A getAffinityKey();

    /**
     * Gets cache name for this job.
     *
     * @return Cache name for this job.
     */
    public String getCacheName();
}
