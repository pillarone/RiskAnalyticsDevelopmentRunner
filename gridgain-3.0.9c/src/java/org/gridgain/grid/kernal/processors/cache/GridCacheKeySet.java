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
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.*;

/**
 * Key set based on 
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheKeySet<K, V> extends GridSerializableSet<K> {
    /** Cache context. */
    private final GridCacheContext<K, V> ctx;

    /** Filter. */
    private final GridPredicate<? super GridCacheEntry<K, V>>[] filter;

    /** Base map. */
    private final Map<K, GridCacheEntry<K, V>> map;

    /**
     * @param ctx Cache context.
     * @param c Entry collection.
     * @param filter Filter.
     */
    public GridCacheKeySet(GridCacheContext<K, V> ctx, Collection<? extends GridCacheEntry<K, V>> c,
        GridPredicate<? super GridCacheEntry<K, V>>[] filter) {
        map = new HashMap<K, GridCacheEntry<K,V>>(c.size(), 1.0f);

        assert ctx != null;

        this.ctx = ctx;
        this.filter = filter == null ? CU.<K, V>empty() : filter;

        for (GridCacheEntry<K, V> e : c) {
            map.put(e.getKey(), e);
        }
    }

    /** {@inheritDoc} */
    @Override public Iterator<K> iterator() {
        return new GridCacheIterator<K, V, K>(map.values(), F.<K>mapEntry2Key(), filter);
    }

    /** {@inheritDoc} */
    @Override public void clear() {
        ctx.cache().clearAll(F.viewReadOnly(map.values(), F.<K>mapEntry2Key(), filter));

        map.clear();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"SuspiciousMethodCalls"})
    @Override public boolean remove(Object o) {
        GridCacheEntry<K, V> e = map.get(o);

        if (e == null || !F.isAll(e, filter)) {
            return false;
        }

        map.remove(o);

        try {
            e.removex();
        }
        catch (GridException ex) {
            throw new GridRuntimeException(ex);
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public int size() {
        return F.size(map.values(), filter);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"SuspiciousMethodCalls"})
    @Override public boolean contains(Object o) {
        GridCacheEntry<K, V> e = map.get(o);

        return e != null && F.isAll(e, filter);
    }
}