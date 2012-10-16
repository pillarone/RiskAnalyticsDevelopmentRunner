// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Distributed cache implementation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridDistributedCacheAdapter<K, V> extends GridCacheAdapter<K, V> {
    /**
     * Empty constructor required by {@link Externalizable}.
     */
    protected GridDistributedCacheAdapter() {
        // No-op.
    }

    /**
     * @param ctx Cache registry.
     * @param startSize Start size.
     */
    protected GridDistributedCacheAdapter(GridCacheContext<K, V> ctx, int startSize) {
        super(ctx, startSize);
    }

    /** {@inheritDoc} */
    @Override public abstract GridCacheTxLocalAdapter<K, V> newTx(
        boolean implicit,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        long timeout,
        boolean invalidate,
        boolean syncCommit,
        boolean syncRollback,
        boolean swapEnabled,
        boolean storeEnabled);

    /** {@inheritDoc} */
    @Override protected abstract void init();

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> txLockAsync(
        Collection<? extends K> keys,
        long timeout,
        GridCacheTxLocalEx<K, V> tx,
        boolean isRead,
        boolean retval,
        GridCacheTxIsolation isolation,
        boolean isInvalidate,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter
    ) {
        assert tx != null;

        return lockAllAsync(keys, timeout, tx, isInvalidate, isRead, retval, isolation, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(Collection<? extends K> keys, long timeout,
        GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        GridCacheTxLocalEx<K, V> tx = ctx.tm().tx();

        // Return value flag is true because we choose to bring values for explicit locks.
        return lockAllAsync(keys, timeout, tx, false, false, /*retval*/true, null, filter);
    }

    /**
     *
     * @param keys Keys to lock.
     * @param timeout Timeout.
     * @param tx Transaction
     * @param isInvalidate Invalidation flag.
     * @param isRead Indicates whether value is read or written.
     * @param retval Flag to return value.
     * @param isolation Transaction isolation.
     * @param filter Optional filter.
     * @return Future for locks.
     */
    protected abstract GridFuture<Boolean> lockAllAsync(Collection<? extends K> keys, long timeout,
        @Nullable GridCacheTxLocalEx<K, V> tx, boolean isInvalidate, boolean isRead, boolean retval,
        GridCacheTxIsolation isolation, GridPredicate<? super GridCacheEntry<K, V>>[] filter);


    /** {@inheritDoc} */
    @Override public abstract Map<UUID, Collection<K>> mapKeysToNodes(Collection<? extends K> keys);

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public abstract void unlockAll(Collection<? extends K> keys,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter);

    /**
     * @param keys Keys to get nodes for.
     * @return Subgrid for given keys.
     */
    @Override public GridProjection gridProjection(Collection<? extends K> keys) {
        return ctx.grid().projectionForNodes(ctx.allNodes(keys));
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDistributedCacheAdapter.class, this, "super", super.toString());
    }
}