// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.processors.cache.query.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.cache.GridCacheFlag.*;

/**
 * Cache projection.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheProjectionImpl<K, V> extends GridMetadataAwareAdapter implements GridCacheProjection<K, V>,
    Externalizable {
    /** Key-value filter taking null values. */
    @GridToStringExclude
    private KeyValueFilter<K, V> withNullKvFilter;

    /** Key-value filter not allowing null values. */
    @GridToStringExclude
    private KeyValueFilter<K, V> noNullKvFilter;

    /** Constant array to avoid recreation. */
    @GridToStringExclude
    private GridPredicate2<K, V>[] withNullKvFilterArray;

    /** Constant array to avoid recreation. */
    @GridToStringExclude
    private GridPredicate2<K, V>[] noNullKvFilterArray;

    /** Entry filter built with {@link #withNullKvFilter}. */
    @GridToStringExclude
    private FullFilter<K, V> withNullEntryFilter;

    /** Entry filter built with {@link #noNullKvFilter}. */
    @GridToStringExclude
    private FullFilter<K, V> noNullEntryFilter;

    /** Constant array to avoid recreation. */
    @GridToStringExclude
    private GridPredicate<GridCacheEntry<K, V>>[] withNullEntryFilterArray;

    /** Constant array to avoid recreation. */
    @GridToStringExclude
    private GridPredicate<GridCacheEntry<K, V>>[] noNullEntryFilterArray;

    /** Base cache. */
    private GridCache<K, V> cache;

    /** Cache context. */
    private GridCacheContext<K, V> ctx;

    /** Logger. */
    private GridLogger log;

    /** Parent projection. */
    private GridCacheProjection<K, V> parent;

    /** Flags. */
    @GridToStringInclude
    private Set<GridCacheFlag> flags;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridCacheProjectionImpl() {
        // No-op.
    }

    /**
     * @param parent Parent projection.
     * @param ctx Cache context.
     * @param kvFilter Key-value filter.
     * @param entryFilter Entry filter.
     * @param flags Flags for new projection
     */
    @SuppressWarnings({"unchecked", "TypeMayBeWeakened"})
    public GridCacheProjectionImpl(
        GridCacheProjection<K, V> parent,
        GridCacheContext<K, V> ctx,
        @Nullable GridPredicate2<K, V>[] kvFilter,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] entryFilter,
        @Nullable Set<GridCacheFlag> flags) {
        assert parent != null;
        assert ctx != null;

        // Check if projection flags are conflicting with an ongoing transaction, if any.
        ctx.checkTxFlags(flags);

        this.parent = parent;
        this.ctx = ctx;
        log = ctx.logger(GridCacheProjectionImpl.class);

        this.flags = !F.isEmpty(flags) ? EnumSet.copyOf(flags) : EnumSet.noneOf(GridCacheFlag.class);

        Set<GridCacheFlag> f = this.flags;

        if (!F.isEmpty(kvFilter)) {
            f = EnumSet.copyOf(f);

            f.add(STRICT);
        }

        this.flags = Collections.unmodifiableSet(f);

        boolean strictFlag = this.flags.contains(STRICT);

        withNullKvFilter = new KeyValueFilter<K, V>(kvFilter, false, strictFlag);
        withNullKvFilterArray = new GridPredicate2[] {withNullKvFilter};

        noNullKvFilter = new KeyValueFilter<K, V>(kvFilter, true, strictFlag);
        noNullKvFilterArray = new GridPredicate2[] {noNullKvFilter};

        withNullEntryFilter = new FullFilter<K, V>(withNullKvFilterArray, entryFilter);
        withNullEntryFilterArray = ctx.vararg(withNullEntryFilter);

        noNullEntryFilter = new FullFilter<K, V>(noNullKvFilterArray, entryFilter);
        noNullEntryFilterArray = ctx.vararg(noNullEntryFilter);

        cache = new GridCacheProxyImpl<K, V>(ctx, this);
    }

    /**
     * @param noNulls Flag indicating whether filter should accept nulls or not.
     * @return Entry filter for the flag.
     */
    GridPredicate<GridCacheEntry<K, V>>[] entryFilter(boolean noNulls) {
        return noNulls ? noNullEntryFilterArray : withNullEntryFilterArray;
    }

    /**
     * @param noNulls Flag indicating whether filter should accept nulls or not.
     * @return Key-value filter for the flag.
     */
    GridPredicate2<K, V>[] kvFilter(boolean noNulls) {
        return noNulls ? noNullKvFilterArray : withNullKvFilterArray;
    }

    /**
     * {@code Ands} passed in filter with projection filter.
     *
     * @param filter filter to {@code and}.
     * @param noNulls Flag indicating whether filter should accept nulls or not.
     * @return {@code Anded} filter array.
     */
    GridPredicate<GridCacheEntry<K, V>>[] and(
        GridPredicate<? super GridCacheEntry<K, V>> filter, boolean noNulls) {
        GridPredicate<GridCacheEntry<K, V>>[] entryFilter = entryFilter(noNulls);

        if (filter == null) {
            return entryFilter;
        }

        return ctx.vararg(F.and(entryFilter, filter));
    }

    /**
     * {@code Ands} passed in filter with projection filter.
     *
     * @param filter filter to {@code and}.
     * @param noNulls Flag indicating whether filter should accept nulls or not.
     * @return {@code Anded} filter array.
     */
    GridPredicate<GridCacheEntry<K, V>>[] and(GridPredicate<? super GridCacheEntry<K, V>>[] filter, boolean noNulls) {
        GridPredicate<GridCacheEntry<K, V>>[] entryFilter = entryFilter(noNulls);

        if (F.isEmpty(filter))
            return entryFilter(noNulls);

        return ctx.vararg(F.and(entryFilter, filter));
    }

    /**
     * {@code Ands} passed in filter with projection filter.
     *
     * @param filter filter to {@code and}.
     * @param noNulls Flag indicating whether filter should accept nulls or not.
     * @return {@code Anded} filter array.
     */
    @SuppressWarnings({"unchecked"}) GridPredicate2<K, V>[] and(final GridPredicate2<K, V> filter, boolean noNulls) {
        final GridPredicate2<K, V>[] kvFilter = kvFilter(noNulls);

        if (filter == null) {
            return kvFilter;
        }

        GridPredicate2<K, V> anded = new P2<K, V>() {
            @Override public boolean apply(K k, V v) {
                return F.isAll2(k, v, kvFilter) && filter.apply(k, v);
            }
        };

        return new GridPredicate2[] {anded};
    }

    /**
     * {@code Ands} passed in filter with projection filter.
     *
     * @param filter filter to {@code and}.
     * @param noNulls Flag indicating whether filter should accept nulls or not.
     * @return {@code Anded} filter array.
     */
    @SuppressWarnings({"unchecked"}) GridPredicate2<K, V>[] and(final GridPredicate2<K, V>[] filter, boolean noNulls) {
        final GridPredicate2<K, V>[] kvFilter = kvFilter(noNulls);

        if (filter == null) {
            return kvFilter;
        }

        GridPredicate2<K, V> anded = new P2<K, V>() {
            @Override public boolean apply(K k, V v) {
                return F.isAll2(k, v, kvFilter) && F.isAll2(k, v, filter);
            }
        };

        return new GridPredicate2[] {anded};
    }

    /**
     * {@code Ands} two passed in filters.
     *
     * @param f1 First filter.
     * @param f2 Second filter.
     * @return {@code Anded} filter.
     */
    @SuppressWarnings({"unchecked"})
    private GridPredicate2<K, V>[] and(@Nullable final GridPredicate2<K, V>[] f1,
        @Nullable final GridPredicate2<K, V>[] f2) {
        GridPredicate2<K, V> anded = new P2<K, V>() {
            @Override public boolean apply(K k, V v) {
                return F.isAll2(k, v, f1) && F.isAll2(k, v, f2);
            }
        };

        return new GridPredicate2[] {anded};
    }

    /**
     * {@code Ands} two passed in filters.
     *
     * @param f1 First filter.
     * @param f2 Second filter.
     * @return {@code Anded} filter.
     */
    @SuppressWarnings({"unchecked"})
    private GridPredicate<GridCacheEntry<K, V>>[] and(@Nullable final GridPredicate<GridCacheEntry<K, V>>[] f1,
        @Nullable final GridPredicate<GridCacheEntry<K, V>>[] f2) {
        GridPredicate<GridCacheEntry<K, V>> anded = new P1<GridCacheEntry<K, V>>() {
            @Override public boolean apply(GridCacheEntry<K, V> t) {
                return F.isAll(t, f1) && F.isAll(t, f2);
            }
        };

        return new GridPredicate[] {anded};
    }

    /**
     * @param e Entry to verify.
     * @param noNulls Flag indicating whether filter should accept nulls or not.
     * @return {@code True} if filter passed.
     */
    boolean isAll(GridCacheEntry<K, V> e, boolean noNulls) {
        GridCacheFlag[] f = ctx.forceLocalRead();

        try {
            return F.isAll(e, entryFilter(noNulls));
        }
        finally {
            ctx.forceFlags(f);
        }
    }

    /**
     * @param e Entry to verify.
     * @param filter Filter to verify.
     * @param noNulls Flag indicating whether filter should accept nulls or not.
     * @return {@code True} if filter passed.
     */
    boolean isAll(GridCacheEntry<K, V> e, GridPredicate<? super GridCacheEntry>[] filter, boolean noNulls) {
        GridCacheFlag[] f = ctx.forceLocalRead();

        try {
            return F.isAll(e, and(filter, noNulls));
        }
        finally {
            ctx.forceFlags(f);
        }
    }

    /**
     * @param k Key.
     * @param v Value.
     * @param noNulls Flag indicating whether filter should accept nulls or not.
     * @return {@code True} if filter passed.
     */
    boolean isAll(K k, V v, boolean noNulls) {
        for (GridPredicate2<K, V> p : kvFilter(noNulls)) {
            GridCacheFlag[] f = ctx.forceLocalRead();

            try {
                if (!p.apply(k, v)) {
                    return false;
                }
            }
            finally {
                ctx.forceFlags(f);
            }
        }

        return true;
    }

    /**
     * @param map Map.
     * @param noNulls Flag indicating whether filter should accept nulls or not.
     * @return {@code True} if filter passed.
     */
    Map<? extends K, ? extends V> isAll(Map<? extends K, ? extends V> map, boolean noNulls) {
        if (F.isEmpty(map)) {
            return Collections.<K, V>emptyMap();
        }

        boolean failed = false;

        // Optimize for passing.
        for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
            K k = e.getKey();
            V v = e.getValue();

            if (!isAll(k, v, noNulls)) {
                failed = true;

                break;
            }
        }

        if (!failed) {
            return map;
        }

        Map<K, V> cp = new HashMap<K, V>(map.size(), 1.0f);

        for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
            K k = e.getKey();
            V v = e.getValue();

            if (isAll(k, v, noNulls)) {
                cp.put(k, v);
            }
        }

        return cp;
    }

    /**
     * Entry projection-filter-aware visitor.
     *
     * @param vis Visitor.
     * @return Projection-filter-aware visitor.
     */
    private GridInClosure<GridCacheEntry<K, V>> visitor(final GridInClosure<? super GridCacheEntry<K, V>> vis) {
        return new CI1<GridCacheEntry<K, V>>() {
            @Override public void apply(GridCacheEntry<K, V> e) {
                if (isAll(e, true)) {
                    vis.apply(e);
                }
            }
        };
    }

    /**
     * Entry projection-filter-aware visitor.
     *
     * @param vis Visitor.
     * @return Projection-filter-aware visitor.
     */
    private GridPredicate<GridCacheEntry<K, V>> visitor(final GridPredicate<? super GridCacheEntry<K, V>> vis) {
        return new P1<GridCacheEntry<K, V>>() {
            @Override public boolean apply(GridCacheEntry<K, V> e) {
                // If projection filter didn't pass, go to the next element.
                // Otherwise, delegate to the visitor.
                return !isAll(e, true) || vis.apply(e);
            }
        };
    }

    /**
     * Entry projection-filter-aware reducer.
     *
     * @param rdc Reducer.
     * @return Projection-filter-aware reducer.
     */
    private <R> GridReducer<? super GridCacheEntry<K, V>, R> reducer(
        final GridReducer<? super GridCacheEntry<K, V>, R> rdc) {
        return new GridReducer<GridCacheEntry<K, V>, R>() {
            @Override public boolean collect(GridCacheEntry<K, V> e) {
                return !isAll(e, true) || rdc.collect(e);
            }

            @Override public R apply() {
                return rdc.apply();
            }
        };
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> GridCacheProjection<K, V> parent() {
        return (GridCacheProjection<K, V>)parent;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> GridCache<K, V> cache() {
        return (GridCache<K, V>)cache;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K1, V1> GridCacheProjection<K1, V1> projection(Class<?> keyType, Class<?> valType) {
        A.notNull(keyType, "keyType", valType, "valType");

        try {
            ctx.deploy().registerClasses(keyType, valType);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }

        return new GridCacheProjectionImpl<K1, V1>(
            (GridCacheProjection<K1, V1>)this,
            (GridCacheContext<K1, V1>)ctx,
            CU.<K1, V1>typeFilter(keyType, valType),
            (GridPredicate<? super GridCacheEntry<K1, V1>>[])noNullEntryFilter.entryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> projection(GridPredicate2<K, V>[] p) {
        if (F.isEmpty(p)) {
            return this;
        }

        GridPredicate2<K, V>[] kvFilter = p;

        if (!F.isEmpty(noNullKvFilter.kvFilter)) {
            kvFilter = and(noNullKvFilter.kvFilter, p);
        }

        try {
            ctx.deploy().registerClasses(p);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }

        return new GridCacheProjectionImpl<K, V>(this, ctx, kvFilter, noNullEntryFilter.entryFilter, flags);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public GridCacheProjection<K, V> projection(GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (F.isEmpty(filter)) {
            return this;
        }

        if (!F.isEmpty(noNullEntryFilter.entryFilter)) {
            filter = and((GridPredicate<GridCacheEntry<K, V>>[])filter,
                (GridPredicate<GridCacheEntry<K, V>>[])noNullEntryFilter.entryFilter);
        }

        try {
            ctx.deploy().registerClasses(filter);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }

        return new GridCacheProjectionImpl<K, V>(this, ctx, noNullKvFilter.kvFilter, filter, flags);
    }

    /** {@inheritDoc} */
    @Override public int size() {
        return cache.isEmpty() ? 0 : F.size(iterator(), entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public int keySize() {
        return keySet().size();
    }

    /** {@inheritDoc} */
    @Override public boolean isEmpty() {
        return cache.isEmpty() || size() == 0;
    }

    /** {@inheritDoc} */
    @Override public boolean containsKey(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.containsKey(key, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean containsValue(V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.containsValue(val, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllKeys(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.containsAllKeys(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllKeys(@Nullable K[] keys) {
        return cache.containsAllKeys(F.asList(keys), entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllKeys(@Nullable final GridPredicate<? super K>[] filter) {
        return cache.containsAllEntries(ctx.vararg(new PCE<K, V>() {
            @Override public boolean apply(GridCacheEntry<K, V> e) {
                // Check projection filter first.
                return isAll(e, true) && F.isAll(e.getKey(), filter);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyKeys(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.containsAnyKeys(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyKeys(@Nullable K[] keys) {
        return F.isEmpty(keys) || cache.containsAnyKeys(F.asList(keys), entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyKeys(@Nullable final GridPredicate<? super K>[] filter) {
        return cache.containsAnyEntries(ctx.vararg(new PCE<K, V>() {
            @Override public boolean apply(GridCacheEntry<K, V> e) {
                // Check projection filter first.
                return isAll(e, true) && F.isAll(e.getKey(), filter);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllValues(@Nullable Collection<? extends V> vals,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.containsAllValues(vals, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllValues(@Nullable V[] vals) {
        return F.isEmpty(vals) || cache.containsAllValues(F.asList(vals), entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllValues(@Nullable final GridPredicate<? super V>[] filter) {
        return cache.containsAllEntries(ctx.vararg(new PCE<K, V>() {
            @Override public boolean apply(GridCacheEntry<K, V> e) {
                V v = e.peek();

                return v != null && isAll(e, true) && F.isAll(v, filter);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyValues(@Nullable Collection<? extends V> vals,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.containsAnyValues(vals, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyValues(@Nullable V[] vals) {
        return F.isEmpty(vals) || cache.containsAnyValues(F.asList(vals), entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyValues(@Nullable final GridPredicate<? super V>[] filter) {
        return cache.containsAnyEntries(ctx.vararg(new PCE<K, V>() {
            @Override public boolean apply(GridCacheEntry<K, V> e) {
                V v = e.peek();

                return v != null && isAll(e, true) && F.isAll(v, filter);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAnyEntries(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.containsAnyEntries(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean containsAllEntries(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.containsAllEntries(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys) {
        cache.forEach(visitor(vis), keys);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> forEachAsync(GridInClosure<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys) {
        return cache.forEachAsync(visitor(vis), keys);
    }

    /** {@inheritDoc} */
    @Override public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis) {
        cache.forEach(visitor(vis));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> forEachAsync(GridInClosure<? super GridCacheEntry<K, V>> vis) {
        return cache.forEachAsync(visitor(vis));
    }

    /** {@inheritDoc} */
    @Override public void forEach(GridInClosure<? super GridCacheEntry<K, V>> vis, K[] keys) {
        cache.forEach(visitor(vis), keys);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> forEachAsync(GridInClosure<? super GridCacheEntry<K, V>> vis, K[] keys) {
        return cache.forEachAsync(visitor(vis), keys);
    }

    /** {@inheritDoc} */
    @Override public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis) {
        return cache.forAll(visitor(vis));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> forAllAsync(GridPredicate<? super GridCacheEntry<K, V>> vis) {
        return cache.forAllAsync(visitor(vis));
    }

    /** {@inheritDoc} */
    @Override public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys) {
        return cache.forAll(visitor(vis), keys);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> forAllAsync(GridPredicate<? super GridCacheEntry<K, V>> vis,
        @Nullable Collection<? extends K> keys) {
        return cache.forAllAsync(visitor(vis), keys);
    }

    /** {@inheritDoc} */
    @Override public boolean forAll(GridPredicate<? super GridCacheEntry<K, V>> vis, @Nullable K[] keys) {
        return cache.forAll(visitor(vis), keys);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> forAllAsync(GridPredicate<? super GridCacheEntry<K, V>> vis,
        @Nullable K[] keys) {
        return cache.forAllAsync(visitor(vis), keys);
    }

    /** {@inheritDoc} */
    @Override public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc) {
        return cache.reduce(reducer(rdc));
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> reduceAsync(GridReducer<? super GridCacheEntry<K, V>, R> rdc) {
        return cache.reduceAsync(reducer(rdc));
    }

    /** {@inheritDoc} */
    @Override public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc,
        @Nullable Collection<? extends K> keys) {
        return cache.reduce(reducer(rdc), keys);
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> reduceAsync(GridReducer<? super GridCacheEntry<K, V>, R> rdc,
        @Nullable Collection<? extends K> keys) {
        return cache.reduceAsync(reducer(rdc), keys);
    }

    /** {@inheritDoc} */
    @Override public <R> R reduce(GridReducer<? super GridCacheEntry<K, V>, R> rdc, @Nullable K[] keys) {
        return cache.reduce(reducer(rdc), keys);
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<R> reduceAsync(GridReducer<? super GridCacheEntry<K, V>, R> rdc,
        @Nullable K[] keys) {
        return cache.reduceAsync(reducer(rdc), keys);
    }

    /** {@inheritDoc} */
    @Override public V peek(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.peek(key, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.peekAll(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable K[] keys) {
        return F.isEmpty(keys) ? Collections.<K, V>emptyMap() : cache.peekAll(F.asList(keys), entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.peekAll(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public V reload(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        return cache.reload(key, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> reloadAsync(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.reloadAsync(key, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public void reloadAll() throws GridException {
        cache.reloadAll(entryFilter(false));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> reloadAllAsync() {
        return cache.reloadAllAsync(entryFilter(false));
    }

    /** {@inheritDoc} */
    @Override public void reloadAll(@Nullable Collection<? extends K> keys,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        cache.reloadAll(keys, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> reloadAllAsync(@Nullable Collection<? extends K> keys,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.reloadAllAsync(keys, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public void reloadAll(@Nullable K[] keys) throws GridException {
        cache.reloadAll(F.asList(keys), entryFilter(false));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> reloadAllAsync(@Nullable K[] keys) {
        return cache.reloadAllAsync(F.asList(keys), entryFilter(false));
    }

    /** {@inheritDoc} */
    @Override public void reloadAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        cache.reloadAll(and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> reloadAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.reloadAllAsync(and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public V get(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return cache.get(key, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> getAsync(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.getAsync(key, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> getAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return cache.getAll(keys, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> getAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.getAllAsync(keys, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> getAll(@Nullable K[] keys) throws GridException {
        return cache.getAll(F.asList(keys), entryFilter(false));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> getAllAsync(@Nullable K[] keys) {
        return cache.getAllAsync(F.asList(keys), entryFilter(false));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> getAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        return cache.getAll(and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> getAllAsync(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.getAllAsync(and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public V put(K key, V val, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        return putAsync(key, val, filter).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> putAsync(K key, V val,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        A.notNull(key, "key", val, "val");

        // Check k-v predicate first.
        if (!isAll(key, val, true)) {
            return new GridFinishedFuture<V>(ctx.kernalContext());
        }

        return cache.putAsync(key, val, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public boolean putx(K key, V val,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return putxAsync(key, val, filter).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> putxAsync(K key, V val,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        A.notNull(key, "key", val, "val");

        // Check k-v predicate first.
        if (!isAll(key, val, true)) {
            return new GridFinishedFuture<Boolean>(ctx.kernalContext(), false);
        }

        return cache.putxAsync(key, val, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public V putIfAbsent(K key, V val) throws GridException {
        return putIfAbsentAsync(key, val).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> putIfAbsentAsync(K key, V val) {
        return putAsync(key, val, ctx.noPeekArray());
    }

    /** {@inheritDoc} */
    @Override public boolean putxIfAbsent(K key, V val) throws GridException {
        return putxIfAbsentAsync(key, val).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> putxIfAbsentAsync(K key, V val) {
        return putxAsync(key, val, ctx.noPeekArray());
    }

    /** {@inheritDoc} */
    @Override public V replace(K key, V val) throws GridException {
        return replaceAsync(key, val).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> replaceAsync(K key, V val) {
        return putAsync(key, val, ctx.hasPeekArray());
    }

    /** {@inheritDoc} */
    @Override public boolean replacex(K key, V val) throws GridException {
        return replacexAsync(key, val).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replacexAsync(K key, V val) {
        return putxAsync(key, val, ctx.hasPeekArray());
    }

    /** {@inheritDoc} */
    @Override public boolean replace(K key, V oldVal, V newVal) throws GridException {
        return replaceAsync(key, oldVal, newVal).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> replaceAsync(K key, V oldVal, V newVal) {
        return putxAsync(key, newVal, and(F.<K, V>cacheContainsPeek(oldVal), false));
    }

    /** {@inheritDoc} */
    @Override public void putAll(Map<? extends K, ? extends V> m,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        putAllAsync(m, filter).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> putAllAsync(Map<? extends K, ? extends V> m,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        m = isAll(m, true);

        if (F.isEmpty(m)) {
            return new GridFinishedFuture<Object>(ctx.kernalContext());
        }

        return cache.putAllAsync(m, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet() {
        return cache.keySet(entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.keySet(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.keySet(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public Set<K> keySet(@Nullable K[] keys) {
        return cache.keySet(F.asList(keys), entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values() {
        return cache.values(entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.values(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.values(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public Collection<V> values(@Nullable K[] keys) {
        return cache.values(F.asList(keys), entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet() {
        return cache.entrySet(entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.entrySet(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet(@Nullable K[] keys) {
        return cache.entrySet(F.asList(keys), entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheEntry<K, V>> entrySet(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.entrySet(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public Set<GridCacheFlag> flags() {
        GridCacheFlag[] forced = ctx.forcedFlags();

        if (F.isEmpty(forced))
            return flags;

        // We don't expect too many flags, so default size is fine.
        Set<GridCacheFlag> ret = new HashSet<GridCacheFlag>();

        ret.addAll(flags);
        ret.addAll(F.asList(forced));

        return Collections.unmodifiableSet(ret);
    }

    /** {@inheritDoc} */
    @Override public GridPredicate2<K, V> keyValuePredicate() {
        if (F.isEmpty(withNullKvFilter.kvFilter))
            return null;

        return new P2<K, V>() {
            @Override public boolean apply(K k, V v) {
                for (GridPredicate2<K, V> f : withNullKvFilter.kvFilter)
                    if (!f.apply(k, v))
                        return false;

                return true;
            }
        };
    }

    /** {@inheritDoc} */
    @Override public GridPredicate<? super GridCacheEntry<K, V>> entryPredicate() {
        if (F.isEmpty(withNullEntryFilter.entryFilter))
            return null;

        return F.and(withNullEntryFilter.entryFilter);
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> flagsOn(@Nullable GridCacheFlag[] flags) {
        if (F.isEmpty(flags)) {
            return this;
        }

        Set<GridCacheFlag> res = EnumSet.noneOf(GridCacheFlag.class);

        if (!F.isEmpty(this.flags)) {
            res.addAll(this.flags);
        }

        res.addAll(EnumSet.copyOf(F.asList(flags)));

        return new GridCacheProjectionImpl<K, V>(
            this,
            ctx,
            noNullKvFilter.kvFilter,
            noNullEntryFilter.entryFilter,
            res);
    }

    /** {@inheritDoc} */
    @Override public GridCacheProjection<K, V> flagsOff(@Nullable GridCacheFlag[] flags) {
        if (F.isEmpty(flags)) {
            return this;
        }

        Set<GridCacheFlag> res = EnumSet.noneOf(GridCacheFlag.class);

        if (!F.isEmpty(this.flags)) {
            res.addAll(this.flags);
        }

        res.removeAll(EnumSet.copyOf(F.asList(flags)));

        return new GridCacheProjectionImpl<K, V>(
            this,
            ctx,
            noNullKvFilter.kvFilter,
            noNullEntryFilter.entryFilter,
            res);
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return cache.name();
    }

    /** {@inheritDoc} */
    @Override public V peek(K key) {
        return cache.peek(key, entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public V peek(K key, @Nullable GridCachePeekMode[] modes) throws GridException {
        return peek(key, F.asList(modes));
    }

    /** {@inheritDoc} */
    @Override public V peek(K key, @Nullable Collection<GridCachePeekMode> modes) throws GridException {
        V val = cache.peek(key, modes);

        return isAll(key, val, true) ? val : null;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(K key, @Nullable GridCachePeekMode[] modes) {
        return peekAsync(key, F.asList(modes));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> peekAsync(final K key, @Nullable final Collection<GridCachePeekMode> modes) {
        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<V>() {
            @Nullable @Override public V call() throws GridException {
                return peek(key, modes);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys) {
        return cache.peekAll(keys, entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys,
        @Nullable GridCachePeekMode[] modes) throws GridException {
        return peekAll(keys, F.asList(modes));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> peekAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridCachePeekMode[] modes) {
        return peekAllAsync(keys, F.asList(modes));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map<K, V>> peekAllAsync(@Nullable final Collection<? extends K> keys,
        @Nullable final Collection<GridCachePeekMode> modes) {
        return ctx.closures().callLocalSafe(ctx.projectSafe(new GPC<Map<K, V>>() {
            @Nullable @Override public Map<K, V> call() throws GridException {
                return peekAll(keys, modes);
            }
        }));
    }

    /** {@inheritDoc} */
    @Override public Map<K, V> peekAll(@Nullable Collection<? extends K> keys,
        @Nullable Collection<GridCachePeekMode> modes) throws GridException {
        if (F.isEmpty(keys)) {
            return Collections.emptyMap();
        }

        assert keys != null;

        Map<K, V> map = new HashMap<K, V>(keys.size());

        for (K key : keys) {
            map.put(key, peek(key, modes));
        }

        return map;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public GridCacheEntry<K, V> entry(K key) {
        V val = peek(key);

        if (!isAll(key, val, true)) {
            return null;
        }

        return cache.entry(key);
    }

    /** {@inheritDoc} */
    @Override public boolean evict(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.evict(key, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public void evictAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        cache.evictAll(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public void evictAll(@Nullable K[] keys) {
        evictAll(F.asList(keys), null);
    }

    /** {@inheritDoc} */
    @Override public void evictAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        cache.evictAll(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public void evictAll() {
        evictAll(entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public void clearAll() {
        cache.clearAll(entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public void clearAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        cache.clearAll(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public void clearAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        cache.clearAll(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public void clearAll(@Nullable K[] keys) {
        clearAll(F.asList(keys), null);
    }

    /** {@inheritDoc} */
    @Override public boolean clear(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.clear(key, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean invalidate(K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return cache.invalidate(key, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public void invalidateAll(@Nullable Collection<K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        cache.invalidateAll(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public void invalidateAll(@Nullable K[] keys) throws GridException {
        cache.invalidateAll(F.asList(keys), entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public boolean compact(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        return cache.compact(key, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public void compactAll(@Nullable Collection<K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        cache.compactAll(keys, and(filter, false));
    }

    /** {@inheritDoc} */
    @Override public void compactAll(@Nullable K[] keys) throws GridException {
        cache.compactAll(F.asList(keys), entryFilter(true));
    }

    /** {@inheritDoc} */
    @Override public V remove(K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return removeAsync(key, filter).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<V> removeAsync(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (flags.contains(STRICT)) {
            try {
                V val = get(key, filter);

                if (!isAll(key, val, true)) {
                    return new GridFinishedFuture<V>(ctx.kernalContext());
                }
            }
            catch (GridException e) {
                U.error(log, "Unable to get value from cache to pass to key-value filter", e);

                return new GridFinishedFuture<V>(ctx.kernalContext(), e);
            }
        }

        return cache.removeAsync(key, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean removex(K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return removexAsync(key, filter).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removexAsync(K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        if (flags.contains(STRICT)) {
            try {
                V val = get(key, filter);

                if (!isAll(key, val, true)) {
                    return new GridFinishedFuture<Boolean>(ctx.kernalContext(), false);
                }
            }
            catch (GridException e) {
                U.error(log, "Unable to get value from cache to pass to key-value filter", e);

                return new GridFinishedFuture<Boolean>(ctx.kernalContext(), false);
            }
        }

        return cache.removexAsync(key, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean remove(K key, V val) throws GridException {
        return removeAsync(key, val).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removeAsync(K key, V val) {
        return !isAll(key, val, true) ? new GridFinishedFuture<Boolean>(ctx.kernalContext(), false) :
            cache.removeAsync(key, val);
    }

    /** {@inheritDoc} */
    @Override public void removeAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        cache.removeAll(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> removeAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.removeAllAsync(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public void removeAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        cache.removeAll(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> removeAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.removeAllAsync(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean lock(K key, @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        return cache.lock(key, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(K key,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.lockAsync(key, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean lock(K key, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return cache.lock(key, timeout, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAsync(K key, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.lockAsync(key, timeout, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return cache.lockAll(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.lockAllAsync(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return cache.lockAll(timeout, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.lockAllAsync(timeout, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return cache.lockAll(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.lockAllAsync(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(@Nullable Collection<? extends K> keys, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        return cache.lockAll(keys, timeout, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(@Nullable Collection<? extends K> keys, long timeout,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        return cache.lockAllAsync(keys, timeout, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(@Nullable K[] keys) throws GridException {
        return lockAllAsync(keys).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(@Nullable K[] keys) {
        return F.isEmpty(keys) ? new GridFinishedFuture<Boolean>(ctx.kernalContext(), true) :
            lockAllAsync(F.asList(keys), CU.<K, V>empty());
    }

    /** {@inheritDoc} */
    @Override public boolean lockAll(long timeout, @Nullable K[] keys) throws GridException {
        return lockAllAsync(timeout, keys).get();
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> lockAllAsync(long timeout, @Nullable K[] keys) {
        return F.isEmpty(keys) ? new GridFinishedFuture<Boolean>(ctx.kernalContext(), true) :
            lockAllAsync(F.asList(keys), timeout, null);
    }

    /** {@inheritDoc} */
    @Override public void unlock(K key, GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        cache.unlock(key, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public void unlockAll(@Nullable K[] keys) throws GridException {
        if (!F.isEmpty(keys)) {
            unlockAll(F.asList(keys), null);
        }
    }

    /** {@inheritDoc} */
    @Override public void unlockAll(@Nullable Collection<? extends K> keys,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        cache.unlockAll(keys, and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public void unlockAll(@Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter)
        throws GridException {
        cache.unlockAll(and(filter, true));
    }

    /** {@inheritDoc} */
    @Override public boolean isLocked(K key) {
        return cache.isLocked(key);
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLocked(@Nullable GridPredicate<? super K>[] filter) {
        return cache.isAllLocked(filter);
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLocked(Collection<? extends K> keys) {
        return cache.isAllLocked(keys);
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLocked(K[] keys) {
        return cache.isAllLocked(keys);
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLockedByThread(@Nullable GridPredicate<? super K>[] filter) {
        return cache.isAllLockedByThread(filter);
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLockedByThread(@Nullable Collection<? extends K> keys) {
        return cache.isAllLockedByThread(keys);
    }

    /** {@inheritDoc} */
    @Override public boolean isAllLockedByThread(@Nullable K[] keys) {
        return cache.isAllLockedByThread(keys);
    }

    /** {@inheritDoc} */
    @Override public boolean isLockedByThread(K key) {
        return cache.isLockedByThread(key);
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, Collection<K>> mapKeysToNodes(@Nullable Collection<? extends K> keys) {
        return cache.mapKeysToNodes(keys);
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, Collection<K>> mapKeysToNodes(@Nullable GridPredicate<? super K>[] filter) {
        return cache.mapKeysToNodes(filter);
    }

    /** {@inheritDoc} */
    @Override public Map<UUID, Collection<K>> mapKeysToNodes(@Nullable K[] keys) {
        return cache.mapKeysToNodes(keys);
    }

    @Override public UUID mapKeyToNode(K key) {
        return cache.mapKeyToNode(key);
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection() {
        return gridProjection(keySet());
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection(@Nullable Collection<? extends K> keys) {
        return cache.gridProjection(keySet(keys, null));
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection(@Nullable K[] keys) {
        return gridProjection(F.<K>asList(keys));
    }

    /** {@inheritDoc} */
    @Override public GridProjection gridProjection(@Nullable GridPredicate<? super K>[] filter) {
        return cache.gridProjection(keySet(ctx.vararg(F.<K, V>cacheKeys(filter))));
    }

    /** {@inheritDoc} */
    @Override public V unswap(K key) throws GridException {
        return cache.unswap(key);
    }

    /** {@inheritDoc} */
    @Override public void unswapAll(@Nullable Collection<? extends K> keys) throws GridException {
        cache.unswapAll(keys);
    }

    /** {@inheritDoc} */
    @Override public void unswapAll(@Nullable K[] keys) throws GridException {
        cache.unswapAll(keys);
    }

    /** {@inheritDoc} */
    @Override public void inTx(GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws IllegalStateException, GridException {
        cache.inTx(closures);
    }

    /** {@inheritDoc} */
    @Override public void inTx(long timeout, GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws IllegalStateException, GridException {
        cache.inTx(timeout, closures);
    }

    /** {@inheritDoc} */
    @Override public void inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridInClosure<GridCacheProjection<K, V>>[] closures) throws IllegalStateException, GridException {
        cache.inTx(concurrency, isolation, closures);
    }

    /** {@inheritDoc} */
    @Override public void inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation, long timeout,
        boolean invalidate, GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws IllegalStateException, GridException {
        cache.inTx(concurrency, isolation, timeout, invalidate, closures);
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> inTx(GridOutClosure<? super R>[] closures) throws GridException {
        return cache.inTx(closures);
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> inTx(long timeout, GridOutClosure<? super R>[] closures)
        throws IllegalStateException, GridException {
        return cache.inTx(timeout, closures);
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridOutClosure<? super R>[] closures) throws IllegalStateException, GridException {
        return cache.inTx(concurrency, isolation, closures);
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> inTx(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        long timeout, boolean invalidate, GridOutClosure<? super R>[] closures)
        throws IllegalStateException, GridException {
        return cache.inTx(concurrency, isolation, timeout, invalidate, closures);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws GridException {
        return cache.inTxAsync(closures);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(long timeout,
        GridInClosure<GridCacheProjection<K, V>>[] closures) throws IllegalStateException, GridException {
        return cache.inTxAsync(timeout, closures);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        GridInClosure<GridCacheProjection<K, V>>[] closures) throws IllegalStateException, GridException {
        return cache.inTxAsync(concurrency, isolation, closures);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> inTxAsync(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        long timeout, boolean invalidate, GridInClosure<GridCacheProjection<K, V>>[] closures)
        throws IllegalStateException, GridException {
        return cache.inTxAsync(concurrency, isolation, timeout, invalidate, closures);
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(
        GridOutClosure<? super R>[] closures) throws IllegalStateException, GridException {
        return cache.inTxAsync(closures);
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(long timeout,
        GridOutClosure<? super R>[] closures) throws IllegalStateException, GridException {
        return cache.inTxAsync(timeout, closures);
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation, GridOutClosure<? super R>[] closures)
        throws IllegalStateException, GridException {
        return cache.inTxAsync(concurrency, isolation, closures);
    }

    /** {@inheritDoc} */
    @Override public <R> GridFuture<Collection<R>> inTxAsync(GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation, long timeout, boolean invalidate,
        GridOutClosure<? super R>[] closures) throws IllegalStateException, GridException {
        return cache.inTxAsync(concurrency, isolation, timeout, invalidate, closures);
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery() {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createQuery(noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery(GridCacheQueryType type) {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createQuery(type, noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, @Nullable Class<?> cls,
        @Nullable String clause) {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createQuery(type, cls, clause, noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, @Nullable String clsName,
        @Nullable String clause) {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createQuery(type, clsName, clause, noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery() {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createTransformQuery(noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type) {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createTransformQuery(type, noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type,
        @Nullable Class<?> cls, @Nullable String clause) {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createTransformQuery(type, cls, clause, noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type,
        @Nullable String clsName, @Nullable String clause) {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createTransformQuery(type, clsName, clause, noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery() {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createReduceQuery(noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type) {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createReduceQuery(type, noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type,
        @Nullable Class<?> cls, @Nullable String clause) {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createReduceQuery(type, cls, clause, noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type,
        @Nullable String clsName, @Nullable String clause) {
        GridCacheQueryManager<K,V> qryMgr = ctx.queries();

        if (qryMgr == null)
            throw new GridEnterpriseFeatureException("Distributed Cache Queries");

        return qryMgr.createReduceQuery(type, clsName, clause, noNullEntryFilter, flags);
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart() throws IllegalStateException {
        return cache.txStart();
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart(long timeout) {
        return cache.txStart(timeout);
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation,
        long timeout, boolean invalidate) {
        return cache.txStart(concurrency, isolation, timeout, invalidate);
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx txStart(GridCacheTxConcurrency concurrency, GridCacheTxIsolation isolation) {
        return cache.txStart(concurrency, isolation);
    }

    /** {@inheritDoc} */
    @Override public GridCacheTx tx() {
        return cache.tx();
    }

    /** {@inheritDoc} */
    @Override public ConcurrentMap<K, V> toMap() {
        return new GridCacheMapAdapter<K, V>(this);
    }

    /** {@inheritDoc} */
    @Override public Iterator<GridCacheEntry<K, V>> iterator() {
        return cache.entrySet(entryFilter(true)).iterator();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(ctx);

        out.writeObject(noNullEntryFilter);
        out.writeObject(withNullEntryFilter);

        out.writeObject(noNullKvFilter);
        out.writeObject(withNullKvFilter);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        ctx = (GridCacheContext<K, V>)in.readObject();

        noNullEntryFilter = (FullFilter<K, V>)in.readObject();
        withNullEntryFilter = (FullFilter<K, V>)in.readObject();

        noNullEntryFilterArray = new GridPredicate[] {noNullEntryFilter};
        withNullEntryFilterArray = new GridPredicate[] {withNullEntryFilter};

        noNullKvFilter = (KeyValueFilter<K, V>)in.readObject();
        withNullKvFilter = (KeyValueFilter<K, V>)in.readObject();

        noNullKvFilterArray = new GridPredicate2[] {noNullKvFilter};
        withNullKvFilterArray = new GridPredicate2[] {withNullKvFilter};

        cache = ctx.cache();
    }

    /** {@inheritDoc} */
    @Override public String toString() { return S.toString(GridCacheProjectionImpl.class, this); }

    /**
     * @param <K> Key type.
     * @param <V> Value type.
     */
    private static class FullFilter<K, V> extends GridPredicate<GridCacheEntry<K, V>> {
        /** Key filter. */
        private GridPredicate2<K, V>[] kvFilter;

        /** Constant array to avoid recreation. */
        private GridPredicate<? super GridCacheEntry<K, V>>[] entryFilter;

        /**
         * @param kvFilter Key-value filter.
         * @param entryFilter Entry filter.
         */
        private FullFilter(GridPredicate2<K, V>[] kvFilter, GridPredicate<? super GridCacheEntry<K, V>>[] entryFilter) {
            assert !F.isEmpty(kvFilter);

            this.kvFilter = kvFilter;
            this.entryFilter = entryFilter;
        }

        /** {@inheritDoc} */
        @Override public boolean apply(GridCacheEntry<K, V> e) {
            for (GridPredicate2<K, V> p : kvFilter)
                if (!p.apply(e.getKey(), e.peek()))
                    return false;

            return F.isAll(e, entryFilter);
        }
    }

    /**
     * @param <K> Key type.
     * @param <V> Value type.
     */
    private static class KeyValueFilter<K, V> extends GridPredicate2<K, V> {
        /** Key filter. */
        private GridPredicate2<K, V>[] kvFilter;

        /** No nulls flag. */
        private boolean noNulls;

        /** Whether projection is strict or not. */
        private boolean strictFlag;

        /**
         * @param kvFilter Key-value filter.
         * @param noNulls Filter without null-values.
         * @param strictFlag Whether {@link GridCacheFlag#STRICT} flag is set meaning
         *      that projection is strict or not. If user key-value filter is set or this
         *      parameter is {@code true} - filter works, otherwise - filter will always
         *      pass through.
         */
        private KeyValueFilter(GridPredicate2<K, V>[] kvFilter, boolean noNulls, boolean strictFlag) {
            this.kvFilter = kvFilter;
            this.noNulls = noNulls;
            this.strictFlag = strictFlag;
        }

        /** {@inheritDoc} */
        @Override public boolean apply(K k, V v) {
            if (k == null) { // Should never happen, but just in case.
                return false;
            }

            if (F.isEmpty(kvFilter) && !strictFlag) {
                return true;
            }

            if (v == null) {
                return !noNulls;
            }

            if (!F.isEmpty(kvFilter)) {
                for (GridPredicate2<K, V> p : kvFilter) {
                    if (!p.apply(k, v)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }
}
