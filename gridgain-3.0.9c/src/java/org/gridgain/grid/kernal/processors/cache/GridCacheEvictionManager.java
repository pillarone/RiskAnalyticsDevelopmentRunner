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
import org.gridgain.grid.cache.eviction.*;
import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Cache eviction manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheEvictionManager<K, V> extends GridCacheManager<K, V> {
    /** Eviction policy. */
    private GridCacheEvictionPolicy<K, V> policy;

    /** Transaction queue. */
    private ConcurrentLinkedQueue<GridCacheTxEx<K, V>> txs = new ConcurrentLinkedQueue<GridCacheTxEx<K, V>>();

    /** Unlock queue */
    private ConcurrentLinkedQueue<GridCacheEntryEx<K, V>> entries = new ConcurrentLinkedQueue<GridCacheEntryEx<K, V>>();

    /** Unwinding flag to make sure that only one thread unwinds. */
    private AtomicBoolean unwinding = new AtomicBoolean(false);

    /** {@inheritDoc} */
    @Override public void start0() {
        policy = cctx.isNear() ?
            cctx.config().<K, V>getNearEvictionPolicy() : cctx.config().<K, V>getEvictionPolicy();

        assert policy != null;
    }

    /**
     * @param tx Transaction to register for eviction policy notifications.
     */
    public void touch(GridCacheTxEx<K, V> tx) {
        txs.add(tx);
    }

    /**
     * @param entry Entry for eviction policy notification.
     */
    public void touch(GridCacheEntryEx<K, V> entry) {
        entries.add(entry);
    }

    /**
     * @param entry Entry to attempt to evict.
     * @param obsoleteVer Obsolete version.
     * @param filter Optional entry filter. *
     * @return {@code True} if entry was marked for eviction.
     * @throws GridException In case of error.
     */
    public boolean evict(@Nullable GridCacheEntryEx<K, V> entry, GridCacheVersion obsoleteVer,
        @Nullable GridPredicate<? super GridCacheEntry<K, V>>[] filter) throws GridException {
        if (entry == null)
            return true;

        // Entry cannot be evicted if entry contains GridCacheInternal key.
        if (entry.key() instanceof GridCacheInternal)
            return false;

        // TODO: Fix this.
//        if (!cctx.isSwapEnabled()) {
//            // Entry cannot be evicted on backup node if swap is disabled.
//            if (!cctx.isNear() && !entry.wrap(false).primary())
//                return false;
//        }

        if (entry.evictInternal(cctx.isSwapEnabled(), obsoleteVer, filter)) {
            cctx.cache().removeEntry(entry);

            return true;
        }

        return false;
    }

    /**
     * Notifications.
     */
    public void unwind() {
        // Only one thread should unwind for efficiency.
        if (unwinding.compareAndSet(false, true)) {
            GridCacheFlag[] old = cctx.forceLocal();

            try {
                // Touch first.
                for (GridCacheEntryEx<K, V> e = entries.poll(); e != null; e = entries.poll())
                    // Internal entry can't be checked in policy.
                    if (!(e.key() instanceof GridCacheInternal))
                        policy.onEntryAccessed(e.obsolete(), e.wrap(false));

                for (Iterator<GridCacheTxEx<K, V>> it = txs.iterator(); it.hasNext(); ) {
                    GridCacheTxEx<K, V> tx = it.next();

                    if (!tx.done())
                        return;

                    it.remove();

                    if (!tx.internal()) {
                        notify(tx.readEntries());
                        notify(tx.writeEntries());
                    }
                }
            }
            finally {
                unwinding.set(false);

                // This call will clear memory for tx queue.
                txs.peek();

                cctx.forceFlags(old);
            }
        }
    }

    /**
     * @param entries Transaction entries for eviction notifications.
     */
    private void notify(Iterable<GridCacheTxEntry<K, V>> entries) {
        for (GridCacheTxEntry<K, V> txe : entries) {
            GridCacheEntryEx<K, V> e = txe.cached();

            // Internal entry can't be checked in policy.
            if (!(e.key() instanceof GridCacheInternal))
                policy.onEntryAccessed(e.obsolete(), e.wrap(false));
        }
    }
}
