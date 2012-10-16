// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.kernal.processors.cache.query;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Query information (local or distributed).
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCacheQueryInfo<K, V> {
    /** */
    private boolean local;

    /** */
    private boolean single;

    /** */
    private GridPredicate<K> keyFilter;

    /** */
    private GridPredicate<V> valFilter;

    /** */
    private GridPredicate<GridCacheEntry<K, V>> prjPredicate;

    /** */
    private GridClosure<V, Object> trans;

    /** */
    private GridReducer<Map.Entry<K, V>, Object> rdc;

    /** */
    private GridCacheQueryBaseAdapter<K, V> query;

    /** */
    private int pageSize;

    /** */
    private boolean readThrough;

    /** */
    private boolean clone;

    /** */
    private boolean incBackups;

    /** */
    private GridCacheQueryFutureAdapter<K, V, ?> locFut;

    /** */
    private UUID senderId;

    /** */
    private long reqId;

    /**
     * @param local {@code true} if local query.
     * @param single Single result or not.
     * @param keyFilter Key filter.
     * @param valFilter Value filter.
     * @param prjPredicate Projection predicate.
     * @param trans Transforming closure.
     * @param rdc Reducer.
     * @param query Query base.
     * @param pageSize Page size.
     * @param readThrough {@code true} if read-through behaviour is enabled.
     * @param clone {@code true} if values should be cloned.
     * @param incBackups {@code true} if need to include backups.
     * @param locFut Query future in case of local query.
     * @param senderId Sender node id.
     * @param reqId Request id in case of distributed query.
     */
    GridCacheQueryInfo(
        boolean local,
        boolean single,
        GridPredicate<K> keyFilter,
        GridPredicate<V> valFilter,
        GridPredicate<GridCacheEntry<K, V>> prjPredicate,
        GridClosure<V, Object> trans,
        GridReducer<Map.Entry<K, V>, Object> rdc,
        GridCacheQueryBaseAdapter<K, V> query,
        int pageSize,
        boolean readThrough,
        boolean clone,
        boolean incBackups,
        GridCacheQueryFutureAdapter<K, V, ?> locFut,
        UUID senderId,
        long reqId
    ) {
        this.local = local;
        this.single = single;
        this.keyFilter = keyFilter;
        this.valFilter = valFilter;
        this.prjPredicate = prjPredicate;
        this.trans = trans;
        this.rdc = rdc;
        this.query = query;
        this.pageSize = pageSize;
        this.readThrough = readThrough;
        this.clone = clone;
        this.incBackups = incBackups;
        this.locFut = locFut;
        this.senderId = senderId;
        this.reqId = reqId;
    }

    /**
     * @return Local or not.
     */
    boolean local() {
        return local;
    }

    /**
     * @return Single result or not.
     */
    boolean single() {
        return single;
    }

    /**
     * @return Id of sender node.
     */
    @Nullable UUID senderId() {
        return senderId;
    }

    /**
     * @return Query.
     */
    GridCacheQueryBaseAdapter<K, V> query() {
        return query;
    }

    /**
     * @return Key filter.
     */
    GridPredicate<K> keyFilter() {
        return keyFilter;
    }

    /**
     * @return Value filter.
     */
    GridPredicate<V> valueFilter() {
        return valFilter;
    }

    /**
     * @return Projection predicate.
     */
    GridPredicate<GridCacheEntry<K, V>> projectionPredicate() {
        return prjPredicate;
    }

    /**
     * @return Transformer.
     */
    GridClosure<V, Object> transformer() {
        return trans;
    }

    /**
     * @return Reducer.
     */
    GridReducer<Map.Entry<K, V>, Object> reducer() {
        return rdc;
    }

    /**
     * @return Page size.
     */
    int pageSize() {
        return pageSize;
    }

    /**
     * @return {@code true} if read-through behaviour is enabled.
     */
    boolean readThrough() {
        return readThrough;
    }

    /**
     * @return {@code true} if values should be cloned.
     */
    boolean cloneValues() {
        return clone;
    }

    /**
     * @return {@code true} if need to include backups.
     */
    boolean includeBackups() {
        return incBackups;
    }

    /**
     * @return Query future in case of local query.
     */
    @Nullable GridCacheQueryFutureAdapter<K, V, ?> localQueryFuture() {
        return locFut;
    }

    /**
     * @return Request id in case of distributed query.
     */
    long requestId() {
        return reqId;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheQueryInfo.class, this);
    }
}
