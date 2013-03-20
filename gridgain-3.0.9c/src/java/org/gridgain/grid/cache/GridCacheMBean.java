// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache;

import org.gridgain.grid.util.mbean.*;

import java.util.*;

/**
 * This interface defines JMX view on {@link GridCache}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to cloud descriptor.")
public interface GridCacheMBean {
    /**
     * Gets name of this cache.
     *
     * @return Cache name.
     */
    @GridMBeanDescription("Cache name.")
    public String name();

    /**
     * Gets metrics (statistics) for this cache.
     *
     * @return Cache metrics.
     */
    @GridMBeanDescription("Formatted cache metrics.")
    public String metricsFormatted();

    /**
     * Gets number of entries that was swapped to disk.
     *
     * @return Number of entries that was swapped to disk.
     */
    @GridMBeanDescription("Number of entries that was swapped to disk.")
    public long overflowSize();

    /**
     * Returns number of non-{@code null} values in the cache.
     *
     * @return Number of non-{@code null} values in the cache.
     */
    @GridMBeanDescription("Number of non-null values in the cache.")
    public int size();

    /**
     * Returns {@code true} if this cache is empty.
     *
     * @return {@code true} if this cache is empty.
     */
    @GridMBeanDescription("True if cache is empty.")
    public boolean isEmpty();

    /**
     * Set of cached keys.
     *
     * @return Set of formatted keys for this cache.
     */
    @GridMBeanDescription("Set of formatted keys for this cache.")
    public Collection<String> keySetFormatted();
}
