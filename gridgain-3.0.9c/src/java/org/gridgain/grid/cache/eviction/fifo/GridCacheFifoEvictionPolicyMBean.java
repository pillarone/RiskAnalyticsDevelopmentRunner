// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.eviction.fifo;

import org.gridgain.grid.util.mbean.*;

/**
 * MBean for {@code FIFO} eviction policy.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean for FIFO cache eviction policy.")
public interface GridCacheFifoEvictionPolicyMBean {
    /**
     * Gets maximum allowed cache size.
     *
     * @return Maximum allowed cache size.
     */
    @GridMBeanDescription("Maximum allowed cache size.")
    public int getMaxSize();

    /**
     * Gets current {@code HIRS} queue size.
     *
     * @return Current {@code HIRS} queue size.
     */
    @GridMBeanDescription("Current FIFO queue size.")
    public int getCurrentSize();

    /**
     * Gets number of voided nodes that remain on queue to be removed later for better concurrency.
     * This concept is similar to Garbage Collection Eden space, hence the name.
     *
     * @return Number of voided nodes that remain on queue.
     */
    @GridMBeanDescription("Current FIFO queue eden size.")
    public int getCurrentEdenSize();
}
