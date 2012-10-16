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
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.cache.GridCachePeekMode.*;

/**
 * Partitioned cache entry public API.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridPartitionedCacheEntryImpl<K, V> extends GridCacheEntryImpl<K, V> {
    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridPartitionedCacheEntryImpl() {
        // No-op.
    }

    /**
     * @param nearPrj Parent projection or {@code null} if entry belongs to default cache.
     * @param nearCtx Near cache context.
     * @param key key.
     * @param cached Cached entry (either from near or dht cache map).
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    public GridPartitionedCacheEntryImpl(GridCacheProjectionImpl<K, V> nearPrj, GridCacheContext<K, V> nearCtx, K key,
        @Nullable GridCacheEntryEx<K, V> cached) {
        super(nearPrj, nearCtx, key, cached);

        assert !ctx.isDht();
    }

    /**
     * @return Dht cache.
     */
    private GridCacheAdapter<K, V> dht() {
        return ctx.near().dht();
    }

    /** {@inheritDoc} */
    @Override public V peek() {
        try {
            return peek(SMART);
        }
        catch (GridException e) {
            // Should never happen.
            throw new GridRuntimeException("Unable to perform entry peek() operation.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public V peek(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        V val = super.peek(filter);

        if (val == null)
            val = peekDht(filter);

        return val;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Nullable @Override public V peek(@Nullable GridCachePeekMode mode) throws GridException {
        V val = super.peek(mode);

        if (val == null)
            val = peekDht0(mode, CU.<K, V>empty());

        return val;
    }

    /** {@inheritDoc} */
    @Override public V peek(@Nullable Collection<GridCachePeekMode> modes) throws GridException {
        V val = super.peek(modes);

        if (val == null)
            val = peekDht0(modes, CU.<K, V>empty());

        return val;
    }

    /**
     * @param filter Filter.
     * @return Peeked value.
     */
    @Nullable public V peekDht(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        try {
            return peekDht0(SMART, filter);
        }
        catch (GridException e) {
            // Should never happen.
            throw new GridRuntimeException("Unable to perform entry peek() operation.", e);
        }
    }

    /**
     * @param modes Peek modes.
     * @param filter Optional entry filter.
     * @return Peeked value.
     * @throws GridException If failed.
     */
    @Nullable private V peekDht0(@Nullable Collection<GridCachePeekMode> modes,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        if (F.isEmpty(modes))
            return peekDht0(SMART, filter);

        assert modes != null;

        for (GridCachePeekMode mode : modes) {
            V val = peekDht0(mode, filter);

            if (val != null)
                return val;
        }

        return null;
    }

    /**
     * @param mode Peek mode.
     * @param filter Optional entry filter.
     * @return Peeked value.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable private V peekDht0(@Nullable GridCachePeekMode mode,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        if (mode == null)
            mode = SMART;

        while (true) {
            GridCacheProjectionImpl<K, V> prjPerCall =
                (prj instanceof GridCacheProjectionImpl) ? ((GridCacheProjectionImpl)prj) : null;

            if (prjPerCall != null)
                filter = ctx.vararg(F.and(ctx.vararg(prj.entryPredicate()), filter));

            GridCacheProjectionImpl<K, V> prev = ctx.gate().enter(prjPerCall);

            try {
                return ctx.cloneOnFlag(
                    dht().entryEx(key).peek0(false, mode, filter, ctx.tm().<GridCacheTxEx<K, V>>tx())
                );
            }
            catch (GridCacheEntryRemovedException ignore) {
                // No-op.
            }
            catch (GridCacheFilterFailedException e) {
                e.printStackTrace();

                assert false;

                return null;
            }
            finally {
                ctx.gate().leave(prev);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(@Nullable final Collection<GridCachePeekMode> modes) {
        final GridCacheTxEx<K, V> tx = ctx.tm().tx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<V>() {
            @Nullable @Override public V call() throws GridException {
                ctx.tm().txContext(tx);

                return peek(modes);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public void timeToLive(long ttl) {
        super.timeToLive(ttl);

        GridCacheEntryEx<K, V> dhtEntry = dht().peekEx(key);

        if (dhtEntry != null)
            try {
                dhtEntry.ttl(ttl);
            }
            catch (GridCacheEntryRemovedException ignored) {
                // No-op.
            }
    }

    /** {@inheritDoc} */
    @Override protected GridCacheEntryEx<K, V> entryEx() {
        return ctx.belongs(key, ctx.localNode()) ? dht().entryEx(key) : ctx.near().entryEx(key);
    }

    /** {@inheritDoc} */
    @Override public GridCacheMetrics metrics() {
        while (true)
            try {
                GridCacheEntryEx<K, V> nearEntry = ctx.near().peekEx(key);

                GridCacheMetrics m = null;

                if (nearEntry != null)
                    m = nearEntry.metrics();

                GridCacheEntryEx<K, V> dhtEntry = dht().peekEx(key);

                if (dhtEntry != null)
                    m = m != null ? GridCacheMetricsAdapter.merge(m, dhtEntry.metrics()) : dhtEntry.metrics();

                return m != null ? m : new GridCacheMetricsAdapter();
            }
            catch (GridCacheEntryRemovedException ignored) {
                // Do it again.
            }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridPartitionedCacheEntryImpl.class, this, super.toString());
    }
}
