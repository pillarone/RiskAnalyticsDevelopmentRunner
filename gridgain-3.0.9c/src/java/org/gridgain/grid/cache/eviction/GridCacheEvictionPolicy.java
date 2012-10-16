// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.eviction;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.eviction.always.*;
import org.gridgain.grid.cache.eviction.fifo.*;
import org.gridgain.grid.cache.eviction.lirs.*;
import org.gridgain.grid.cache.eviction.lru.*;
import org.gridgain.grid.cache.eviction.never.*;
import org.gridgain.grid.cache.eviction.random.*;
import org.gridgain.grid.lang.*;

/**
 * Pluggable cache eviction policy. Usually, implementations will internally order
 * cache entries based on {@link #onEntryAccessed(boolean, GridCacheEntry)} notifications and
 * whenever an element needs to be evicted, {@link GridCacheEntry#evict(GridPredicate[])}
 * method should be called. If you need to access the underlying cache directly
 * from this policy, you can get it via {@link GridCacheEntry#parent()} method.
 * <p>
 * GridGain comes with following eviction policies out-of-the-box:
 * <ul>
 * <li>{@link GridCacheLruEvictionPolicy}</li>
 * <li>{@link GridCacheLirsEvictionPolicy}</li>
 * <li>{@link GridCacheRandomEvictionPolicy}</li>
 * <li>{@link GridCacheFifoEvictionPolicy}</li>
 * <li>{@link GridCacheAlwaysEvictionPolicy}</li> 
 * <li>{@link GridCacheNeverEvictionPolicy}</li>
 * </ul>
 * <p>
 * Note that implementations of all eviction policies provided by GridGain are very
 * light weight in a way that they are all lock-free (or very close to it), and do not
 * create any internal tables, arrays, or other expensive structures.
 * The eviction order is preserved by attaching light-weight meta-data to existing
 * cache entries.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCacheEvictionPolicy<K, V> {
    /**
     * Callback for whenever entry is accessed. 
     *
     * @param rmv {@code True} if entry has been removed, {@code false} otherwise.
     * @param entry Accessed entry.
     */
    public void onEntryAccessed(boolean rmv, GridCacheEntry<K, V> entry);
}
