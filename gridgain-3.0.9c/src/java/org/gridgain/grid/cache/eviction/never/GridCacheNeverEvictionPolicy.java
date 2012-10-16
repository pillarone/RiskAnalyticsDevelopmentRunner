// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.eviction.never;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.eviction.*;

/**
 * Cache eviction policy that does not do anything. This eviction policy can be used
 * whenever it is known that cache size is constant and won't change or grow infinitely.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheNeverEvictionPolicy<K, V> implements GridCacheEvictionPolicy<K, V>,
    GridCacheNeverEvictionPolicyMBean {
    /** {@inheritDoc} */
    @Override public void onEntryAccessed(boolean rmv, GridCacheEntry<K, V> entry) {
        // No-op.
    }
}
