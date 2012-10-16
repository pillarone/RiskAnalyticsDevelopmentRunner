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
 * Convenience adapter for {@link GridCoherenceAffinityJob} interface that provides
 * default implementation of {@link GridJob#cancel()} method and allows
 * to add arguments.
 * <p>
 * Here is an example of how affinity job is implemented:
 * <pre name="code" class="java">
 * public class MyFooBarCoherenceAffinityJob extends GridCoherenceAffinityJobAdapter&lt;Integer&gt; {
 *    ...
 *    private static final String CACHE_NAME = "myDistributedCache";
 *
 *    public MyFooBarCoherenceAffinityJob(Integer cacheKey) {
 *        super(CACHE_NAME, cacheKey);
 *    }
 *
 *    public Object execute() throws GridException {
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
public abstract class GridCoherenceAffinityJobAdapter <A> extends GridJobAdapterEx implements
    GridCoherenceAffinityJob<A> {
    /** Coherence cache name. */
    private String cacheName;

    /** Affinity key. */
    private A affKey;

    /**
     * No-arg constructor. Note that all fields will be {@code null}.
     * <p>
     * Please use {@link #setArguments(Object...)} to set job argument(s),
     * {@link #setAffinityKey(Object)} to set affinity key, and
     * {@link #setCacheName(String)} to set cache name.
     */
    protected GridCoherenceAffinityJobAdapter() { /* No-op. */ }

    /**
     * Initializes coherence affinity job with cache name. Note that
     * all other fields will be {@code null}.
     * <p>
     * Please use {@link #setArguments(Object...)} to set job argument(s), and
     * {@link #setAffinityKey(Object)} to set affinity key.
     *
     * @param cacheName Coherence cache name.
     */
    protected GridCoherenceAffinityJobAdapter(String cacheName) {
        this.cacheName = cacheName;
    }

    /**
     * Initializes coherence affinity job with cache name. Note that
     * all arguments will be {@code null}.
     * <p>
     * Please use {@link #setArguments(Object...)} to set job argument(s).
     *
     * @param cacheName Coherence cache name.
     * @param affKey Coherence affinity key.
     */
    protected GridCoherenceAffinityJobAdapter(String cacheName, A affKey) {
        this.cacheName = cacheName;
        this.affKey = affKey;
    }

    /**
     * Creates fully initialized job with specified cache name, affinity key,
     * and job argument(s).
     *
     * @param cacheName Coherence cache name.
     * @param affKey Coherence affinity key.
     * @param arg Job argument.
     */
    protected GridCoherenceAffinityJobAdapter(String cacheName, A affKey, Object arg) {
        super(arg);

        this.cacheName = cacheName;
        this.affKey = affKey;
    }

    /** {@inheritDoc} */
    @Override public String getCacheName() {
        return cacheName;
    }

    /** {@inheritDoc} */
    @Override public A getAffinityKey() {
        return affKey;
    }

    /**
     * Sets Coherence cache name.
     *
     * @param cacheName Coherence cache name.
     */
    void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    /**
     * Sets affinity key.
     *
     * @param affKey Affinity key.
     */
    void setAffinityKey(A affKey) {
        this.affKey = affKey;
    }
}

