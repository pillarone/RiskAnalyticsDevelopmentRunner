// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.eviction.fifo;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.eviction.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.*;

import static org.gridgain.grid.lang.utils.GridConcurrentLinkedQueue.*;

/**
 * Eviction policy based on {@code First In First Out (FIFO)} algorithm. This
 * implementation is very efficient since it is lock-free and does not
 * create any additional table-like data structures. The {@code FIFO} ordering
 * information is maintained by attaching ordering metadata to cache entries.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheFifoEvictionPolicy<K, V> implements GridCacheEvictionPolicy<K, V>,
    GridCacheFifoEvictionPolicyMBean {
    /** Tag. */
    private final String meta = UUID.randomUUID().toString();

    /** Maximum size. */
    private int max = -1;

    /** LRU queue. */
    private final GridConcurrentLinkedQueue<GridCacheEntry<K, V>> queue =
        new GridConcurrentLinkedQueue<GridCacheEntry<K,V>>();

    /**
     * Constructs LRU eviction policy with all defaults.
     */
    public GridCacheFifoEvictionPolicy() {
        // No-op.
    }

    /**
     * Constructs LRU eviction policy with maximum size.
     *
     * @param max Maximum allowed size of cache before entry will start getting evicted.
     */
    public GridCacheFifoEvictionPolicy(int max) {
        A.ensure(max > 0, "max > 0");

        this.max = max;
    }

    /**
     * Gets maximum allowed size of cache before entry will start getting evicted.
     *
     * @return Maximum allowed size of cache before entry will start getting evicted.
     */
    @Override public int getMaxSize() {
        return max;
    }

    /**
     * Sets maximum allowed size of cache before entry will start getting evicted.
     *
     * @param max Maximum allowed size of cache before entry will start getting evicted.
     */
    public void setMaxSize(int max) {
        A.ensure(max > 0, "max > 0");

        this.max = max;
    }

    /** {@inheritDoc} */
    @Override public int getCurrentSize() {
        return queue.size();
    }

    /** {@inheritDoc} */
    @Override public int getCurrentEdenSize() {
        return queue.eden();
    }

    /**
     * Gets read-only view on internal {@code FIFO} queue in proper order.
     *
     * @return Read-only view ono internal {@code 'FIFO'} queue.
     */
    public Collection<GridCacheEntry<K, V>> queue() {
        return Collections.unmodifiableCollection(queue);
    }

    /** {@inheritDoc} */
    @Override public void onEntryAccessed(boolean rmv, GridCacheEntry<K, V> entry) {
        if (!rmv) {
            touch(entry);
        }
        else {
            Node<GridCacheEntry<K, V>> n = entry.meta(meta);

            if (n != null) {
                queue.clearNode(n);
            }
        }

        shrink();
        
        queue.gc(max);
    }

    /**
     * @param entry Entry to touch.
     */
    private void touch(GridCacheEntry<K, V> entry) {
        Node<GridCacheEntry<K, V>> n = entry.meta(meta);

        if (n == null) {
            Node<GridCacheEntry<K, V>> old = entry.putMetaIfAbsent(meta, n = new Node<GridCacheEntry<K, V>>(entry));

            if (old == null) {
                queue.addNode(n);
            }
        }
    }

    /**
     * Shrinks LRU queue to maximum allowed size.
     */
    private void shrink() {
        int i = 0;

        while (queue.size() > max && i++ < max) {
            Node<GridCacheEntry<K, V>> n = queue.peekNode();

            if (n != null) {
                GridCacheEntry<K, V> e = n.value();

                if (e != null) {
                    if (queue.clearNode(n)) {
                        if (!e.evict()) {
                            // MOve to the beginning again.
                            touch(e);
                        }
                    }
                }
            }
        }
    }
}
