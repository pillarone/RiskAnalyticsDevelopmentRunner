// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.store;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Cache storage convenience adapter. It provides default implementation for bulk operations, such
 * as {@link #loadAll(String, GridCacheTx, Collection, GridInClosure2)},
 * {@link #putAll(String, GridCacheTx, Map)}, abd {@link #removeAll(String, GridCacheTx, Collection)}
 * by sequentially calling corresponding {@link #load(String, GridCacheTx, Object)},
 * {@link #put(String, GridCacheTx, Object, Object)}, and {@link #remove(String, GridCacheTx, Object)}
 * operations. Use this adapter whenever such behaviour is acceptable. However in many cases
 * it maybe more preferable to take advantage of database batch update functionality, and therefore
 * default adapter implementation may not be the best option.
 * <p>
 * Note that method {@link #loadAll(String, org.gridgain.grid.lang.GridInClosure2 , Object...)} has empty
 * implementation because it is essentially up to the user to invoke it with
 * specific arguments.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCacheStoreAdapter<K, V> implements GridCacheStore<K, V> {
    /**
     * Default empty implementation. This method needs to be overridden only if
     * {@link GridCache#loadCache(GridPredicate2, long, Object...)} method
     * is explicitly called.
     *
     * @param cacheName {@inheritDoc}
     * @param closure {@inheritDoc}
     * @param args {@inheritDoc}
     * @throws GridException {@inheritDoc}
     */
    @Override public void loadAll(@Nullable String cacheName, GridInClosure2<K, V> closure, Object... args)
        throws GridException {
        /* No-op. */
    }

    /** {@inheritDoc} */
    @Override public void loadAll(@Nullable String cacheName, @Nullable GridCacheTx tx, Collection<? extends K> keys,
        GridInClosure2<K, V> closure) throws GridException {
        assert keys != null;

        for (K key : keys)
            closure.apply(key, load(cacheName, tx, key));
    }

    /** {@inheritDoc} */
    @Override public void putAll(@Nullable String cacheName, GridCacheTx tx, Map<? extends K, ? extends V> map)
        throws GridException {
        assert map != null;

        for (Map.Entry<? extends K, ? extends V> e : map.entrySet())
            put(cacheName, tx, e.getKey(), e.getValue());
    }

    /** {@inheritDoc} */
    @Override public void removeAll(@Nullable String cacheName, GridCacheTx tx, Collection<? extends K> keys)
        throws GridException {
        assert keys != null;

        for (K key : keys)
            remove(cacheName, tx, key);
    }

    /**
     * Default empty implementation for ending transactions. Note that if explicit cache
     * transactions are not used, then transactions do not have to be explicitly ended -
     * for all other cases this method should be overridden with custom commit/rollback logic.
     *
     * @param cacheName {@inheritDoc}
     * @param tx {@inheritDoc}
     * @param commit {@inheritDoc}
     * @throws GridException {@inheritDoc}
     */
    @Override public void txEnd(@Nullable String cacheName, GridCacheTx tx, boolean commit) throws GridException {
        // No-op.
    }
}
