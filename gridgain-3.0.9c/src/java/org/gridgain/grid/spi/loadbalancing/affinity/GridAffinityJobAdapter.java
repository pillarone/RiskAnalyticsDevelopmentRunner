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
 * Convenience adapter for {@link GridAffinityJob} interface that provides
 * default implementation of {@link GridJob#cancel()} method and allows
 * to add arguments.
 * <p>
 * Here is example of how job affinity adapter could be used:
 * <pre name="code" class="java">
 * public class MyGridAffinityJob extends GridAffinityJobAdapter&lt;Integer&gt; {
 *    public MyGridAffinityJob(Integer cacheKey) {
 *        // Pass cache key as a job argument.
 *        super(cacheKey);
 *    }
 *
 *    public Object execute() throws GridException {
 *        ...
 *        // Access data by the same key returned
 *        // in 'getAffinityKey()' method.
 *        mycache.get(getAffinityKey());
 *        ...
 *    }
 * }
 * </pre>
 * <p>
 * For more information and examples, see {@link GridAffinityLoadBalancingSpi}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <A> Affinity key type.
 */
public abstract class GridAffinityJobAdapter<A> extends GridJobAdapterEx implements GridAffinityJob<A> {
    /** */
    private A affKey;

    /**
     * No-arg constructor.
     * <p>
     * Note that job argument(s) and affinity key will be {@code null}. Please use
     * {@link #setArguments(Object...)} to set job argument(s) and
     * {@link #setAffinityKey(Object)} to set affinity key.
     */
    protected GridAffinityJobAdapter() { /* No-op. */ }

    /**
     * Creates affinity job with a given key.
     * <p>
     * Note that job argument(s) will be {@code null}. Please use
     * {@link #setArguments(Object...)} to set job argument.
     *
     * @param affKey Affinity key.
     */
    protected GridAffinityJobAdapter(A affKey) { this.affKey = affKey; }

    /**
     * Creates a fully initialized affinity job with a given key and
     * specified job argument.
     *
     * @param affKey Affinity key.
     * @param arg Job argument.
     */
    protected GridAffinityJobAdapter(A affKey, Object arg) {
        super(arg);

        this.affKey = affKey;
    }

    /** {@inheritDoc} */
    @Override public A getAffinityKey() {
        return affKey;
    }

    /**
     * Sets affinity key.
     *
     * @param affKey Affinity key
     */
    public void setAffinityKey(A affKey) {
        this.affKey = affKey;
    }
}
