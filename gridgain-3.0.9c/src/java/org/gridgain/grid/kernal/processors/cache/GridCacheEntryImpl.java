// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCachePeekMode.*;

/**
 * Entry wrapper that never obscures obsolete entries from user.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheEntryImpl<K, V> implements GridCacheEntry<K, V>, Externalizable {
    /** Cache context. */
    protected GridCacheContext<K, V> ctx;

    /** Parent projection. */
    protected GridCacheProjection<K, V> prj;

    /** Key. */
    @GridToStringInclude
    protected K key;

    /** Cached entry. */
    @GridToStringInclude
    protected volatile GridCacheEntryEx<K, V> cached;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridCacheEntryImpl() {
        // No-op.
    }

    /**
     * @param prj Parent projection or {@code null} if entry belongs to default cache.
     * @param ctx Context.
     * @param key key.
     * @param cached Cached entry.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    protected GridCacheEntryImpl(GridCacheProjectionImpl<K, V> prj,
        GridCacheContext<K, V> ctx, K key,
        GridCacheEntryEx<K, V> cached) {
        assert ctx != null;
        assert key != null;

        this.prj = prj != null ? prj : ctx.cache();
        this.ctx = ctx;
        this.key = key;
        this.cached = cached;
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> parent() {
        return prj;
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheFlag> flags() {
        return prj.flags();
    }

    /**
     * @return Cache entry.
     */
    public GridCacheEntryEx<K, V> unwrap() {
        GridCacheEntryEx<K, V> cached = this.cached;

        if (cached == null)
            this.cached = cached = entryEx();

        return cached;
    }

    /** {@inheritDoc} */
    protected GridCacheEntryEx<K, V> entryEx() {
        return ctx.cache().entryEx(key);
    }

    /**
     * Reset cached value so it will be re-cached.
     */
    protected void reset() {
        cached = null;
    }

    /** {@inheritDoc} */
    @Override public K getKey() throws IllegalStateException {
        return key;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public V getValue() throws IllegalStateException {
        try {
            return get(CU.<K, V>empty());
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V setValue(V val) {
        try {
            return set(val, CU.<K, V>empty());
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public Object version() {
        while (true) {
            try {
                return unwrap().version();
            }
            catch (GridCacheEntryRemovedException ignore) {
                reset();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public long expirationTime() {
        while (true) {
            try {
                return unwrap().expireTime();
            }
            catch (GridCacheEntryRemovedException ignore) {
                reset();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheMetrics metrics() {
        while (true) {
            try {
                return GridCacheMetricsAdapter.copyOf(unwrap().metrics());
            }
            catch (GridCacheEntryRemovedException ignore) {
                reset();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public boolean primary() {
        return ctx.config().getCacheMode() != PARTITIONED ||
            ctx.nodeId().equals(CU.primary(ctx.affinity(key, CU.allNodes(ctx))).id());
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
    @Nullable @Override public V peek(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        try {
            return peek0(SMART, filter, ctx.tm().<GridCacheTxEx<K, V>>tx());
        }
        catch (GridException e) {
            // Should never happen.
            throw new GridRuntimeException("Unable to perform entry peek() operation.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public V peek(@Nullable GridCachePeekMode[] modes) throws GridException {
        return peek(F.asList(modes));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(@Nullable final Collection<GridCachePeekMode> modes) {
        final GridCacheTxEx<K, V> tx = ctx.tm().tx();

        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<V>() {
            @Nullable @Override public V call() {
                try {
                    return peek0(modes, CU.<K, V>empty(), tx);
                }
                catch (GridException e) {
                    throw new GridClosureException(e);
                }
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(@Nullable GridCachePeekMode[] modes) {
        return peekAsync(F.asList(modes));
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Nullable @Override public V peek(@Nullable GridCachePeekMode mode) throws GridException {
        return peek0(mode, CU.<K, V>empty(), ctx.tm().<GridCacheTxEx<K, V>>tx());
    }

    /** {@inheritDoc} */
    @Override public V peek(@Nullable Collection<GridCachePeekMode> modes) throws GridException {
        return peek0(modes, CU.<K, V>empty(), ctx.tm().<GridCacheTxEx<K, V>>tx());
    }

    /**
     * @param mode Peek mode.
     * @param filter Optional entry filter.
     * @param tx Transaction to peek at (if mode is TX).
     * @return Peeked value.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable private V peek0(@Nullable GridCachePeekMode mode,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter, GridCacheTxEx<K, V> tx) throws GridException {
        if (mode == null)
            mode = SMART;

        GridCacheProjectionImpl<K, V> prjPerCall =
            (prj instanceof GridCacheProjectionImpl) ? ((GridCacheProjectionImpl)prj) : null;

        if (prjPerCall != null)
            filter = ctx.vararg(F.and(ctx.vararg(prj.entryPredicate()), filter));

        GridCacheProjectionImpl<K, V> prev = ctx.gate().enter(prjPerCall);

        try {
            while (true)
                try {
                    return ctx.cloneOnFlag(unwrap().peek0(false, mode, filter, tx));
                }
                catch (GridCacheEntryRemovedException ignore) {
                    reset();
                }
                catch (GridCacheFilterFailedException e) {
                    e.printStackTrace();

                    assert false;

                    return null;
                }
        }
        finally {
            ctx.gate().leave(prev);
        }
    }

    /**
     * @param modes Peek modes.
     * @param filter Optional entry filter.
     * @param tx Transaction to peek at (if modes contains TX value).
     * @return Peeked value.
     * @throws GridException If failed.
     */
    @Nullable private V peek0(@Nullable Collection<GridCachePeekMode> modes,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter, GridCacheTxEx<K, V> tx) throws GridException {
        if (F.isEmpty(modes))
            return peek0(SMART, filter, tx);

        assert modes != null;

        for (GridCachePeekMode mode : modes) {
            V val = peek0(mode, filter, tx);

            if (val != null)
                return val;
        }

        return null;
    }

    /** {@inheritDoc} */
    @Nullable @Override public V reload(GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return prj.reload(key, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> reloadAsync(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return prj.reloadAsync(key, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean evict(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return prj.evict(key, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean clear(@Nullable GridPredicate<? super GridCacheEntry<K, V>>... filter) {
        return prj.clear(key, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean invalidate(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return prj.invalidate(key, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean compact(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return prj.compact(key, filter);
    }

    /** {@inheritDoc} */
    @Nullable @Override public V get(GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return prj.get(key, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> getAsync(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return prj.getAsync(key, filter);
    }

    /** {@inheritDoc} */
    @Nullable @Override public V set(V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return prj.put(key, val, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> setAsync(V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return prj.putAsync(key, val, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean setx(V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return prj.putx(key, val, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> setxAsync(V val, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return prj.putxAsync(key, val, filter);
    }

    /** {@inheritDoc} */
    @Nullable @Override public V replace(V val) throws GridException {
        return prj.replace(key, val);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> replaceAsync(V val) {
        return prj.replaceAsync(key, val);
    }

    /** {@inheritDoc} */
    @Override public boolean replace(V oldVal, V newVal) throws GridException {
        return prj.replace(key, oldVal, newVal);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replaceAsync(V oldVal, V newVal) {
        return prj.replaceAsync(key, oldVal, newVal);
    }

    /** {@inheritDoc} */
    @Override public long timeToLive() {
        while (true) {
            try {
                return unwrap().ttl();
            }
            catch (GridCacheEntryRemovedException ignore) {
                reset();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void timeToLive(long ttl) {
        while (true) {
            try {
                unwrap().ttl(ttl);

                return;
            }
            catch (GridCacheEntryRemovedException ignore) {
                reset();
            }
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public V setIfAbsent(V val) throws GridException {
        return prj.putIfAbsent(key, val);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> setIfAbsentAsync(V val) {
        return prj.putIfAbsentAsync(key, val);
    }

    /** {@inheritDoc} */
    @Override public boolean setxIfAbsent(V val) throws GridException {
        return prj.putxIfAbsent(key, val);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> setxIfAbsentAsync(V val) {
        return prj.putxIfAbsentAsync(key, val);
    }

    /** {@inheritDoc} */
    @Override public boolean replacex(V val) throws GridException {
        return prj.replacex(key, val);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replacexAsync(V val) {
        return prj.replacexAsync(key, val);
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection() {
        return prj.gridProjection(Arrays.asList(key));
    }

    /** {@inheritDoc} */
    @Override public boolean hasValue(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return peek(filter) != null;
    }

    /** {@inheritDoc} */
    @Override public boolean hasValue(@Nullable GridCachePeekMode mode) throws GridException {
        return peek(mode) != null;
    }

    /** {@inheritDoc} */
    @Nullable @Override public V remove(GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return prj.remove(key, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> removeAsync(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return prj.removeAsync(key, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean removex(GridPredicate<GridCacheEntry<K, V>>[] filter) throws GridException {
        return prj.removex(key, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removexAsync(GridPredicate<GridCacheEntry<K, V>>[] filter) {
        return prj.removexAsync(key, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean remove(V val) throws GridException {
        return prj.remove(key, val);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removeAsync(V val) {
        return prj.removeAsync(key, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMeta(String name, V val) {
        return unwrap().addMeta(name, val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <V> V meta(String name) {
        return (V)unwrap().meta(name);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <V> V removeMeta(String name) {
        return (V)unwrap().removeMeta(name);
    }

    /** {@inheritDoc} */
    @Override public <V> Map<String, V> allMeta() {
        return unwrap().allMeta();
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name) {
        return unwrap().hasMeta(name);
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name, Object val) {
        return unwrap().hasMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V putMetaIfAbsent(String name, V val) {
        return unwrap().putMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V putMetaIfAbsent(String name, Callable<V> c) {
        return unwrap().putMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMetaIfAbsent(String name, V val) {
        return unwrap().addMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMetaIfAbsent(String name, Callable<V> c) {
        return unwrap().addMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @Override public <V> boolean replaceMeta(String name, V curVal, V newVal) {
        return unwrap().replaceMeta(name, curVal, newVal);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(GridMetadataAware from) {
        unwrap().copyMeta(from);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(Map<String, ?> data) {
        unwrap().copyMeta(data);
    }

    /** {@inheritDoc} */
    @Override public <V> boolean removeMeta(String name, V val) {
        return unwrap().removeMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public boolean isLocked() {
        while (true) {
            try {
                return unwrap().lockedByAny();
            }
            catch (GridCacheEntryRemovedException ignore) {
                reset();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isLockedByThread() {
        while (true) {
            try {
                return unwrap().lockedByThread();
            }
            catch (GridCacheEntryRemovedException ignore) {
                reset();
            }
        }
    }

    /** {@inheritDoc} */
    @Override public boolean lock(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return prj.lock(key, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return prj.lockAsync(key, filter);
    }

    /** {@inheritDoc} */
    @Override public boolean lock(long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return prj.lock(key, timeout, filter);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return prj.lockAsync(key, timeout, filter);
    }

    /** {@inheritDoc} */
    @Override public void unlock(GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        prj.unlock(key, filter);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(ctx);
        out.writeObject(prj);
        out.writeObject(key);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        ctx = (GridCacheContext<K, V>)in.readObject();
        prj = (GridCacheProjection<K, V>)in.readObject();
        key = (K)in.readObject();
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return unwrap().hashCode();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (!(obj instanceof GridCacheEntryImpl))
            return false;

        GridCacheEntryImpl<K, V> other = (GridCacheEntryImpl<K, V>)obj;

        return unwrap().equals(other.unwrap());
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheEntryImpl.class, this);
    }
}
