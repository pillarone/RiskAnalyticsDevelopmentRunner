// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.eviction.lirs;

import org.gridgain.grid.util.mbean.*;

/**
 * MBean for {@code LIRS} eviction policy.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean for LIRS cache eviction policy.")
public interface GridCacheLirsEvictionPolicyMBean {
    /**
     * Gets maximum allowed cache size.
     *
     * @return Maximum allowed cache size.
     */
    @GridMBeanDescription("Maximum allowed main stack size.")
    public int getMaxSize();

    /**
     * Gets ratio for {@code HIRS} queue size relative to main stack size.
     *
     * @return Ratio for {@code HIRS} queue size relative to main stack size.
     */
    @GridMBeanDescription("Ratio for HIRS queue size relative to main stack size.")
    public double getQueueSizeRatio();

    /**
     * Gets maximum allowed size of {@code HIRS} queue before entries will start getting evicted.
     * This value is computed based on {@link #getQueueSizeRatio()} value.
     *
     * @return Maximum allowed size of {@code HIRS} queue before entries will start getting evicted.
     */
    @GridMBeanDescription("Maximum allowed HIRS queue size.")
    public int getMaxQueueSize();

    /**
     * Gets maximum allowed size of main stack This value is computed based on
     * {@link #getQueueSizeRatio()} value.
     *
     * @return Maximum allowed size of main stack.
     */
    @GridMBeanDescription("Maximum allowed HIRS queue size.")
    public int getMaxStackSize();

    /**
     * Gets current main stack size.
     *
     * @return Current main stack size.
     */
    @GridMBeanDescription("Current main stack size.")
    public int getCurrentStackSize();

    /**
     * Gets current {@code HIRS} queue size.
     *
     * @return Current {@code HIRS} queue size.
     */
    @GridMBeanDescription("Current HIRS queue size.")
    public int getCurrentQueueSize();

    /**
     * Gets number of voided nodes that remain on stack to be removed later for better concurrency.
     * This concept is similar to Garbage Collection Eden space, hence the name.
     *
     * @return Number of voided nodes that remain on stack.
     */
    @GridMBeanDescription("Current stack eden size.")
    public int getCurrentStackEdenSize();

    /**
     * Gets number of voided nodes that remain on queue to be removed later for better concurrency.
     * This concept is similar to Garbage Collection Eden space, hence the name.
     *
     * @return Number of voided nodes that remain on queue.
     */
    @GridMBeanDescription("Current queue eden size.")
    public int getCurrentQueueEdenSize();
}
