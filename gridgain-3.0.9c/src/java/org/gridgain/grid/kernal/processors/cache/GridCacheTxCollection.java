// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;

import java.io.*;
import java.util.*;

/**
 * Grid cache transaction read or write set.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheTxCollection<K, V, T> extends AbstractSet<T> implements Externalizable {
    /** Base transaction map. */
    private Collection<GridCacheTxEntry<K, V>> col;

    private int size = -1;

    /** Empty flag. */
    private Boolean empty;

    /** Sealed flag. */
    private boolean sealed;

    /** Filter. */
    private GridPredicate<GridCacheTxEntry<K, V>> filter;

    /** Transformer. */
    private GridClosure<GridCacheTxEntry<K, V>, T> trans;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridCacheTxCollection() {
        // No-op.
    }

    /**
     * @param col Base collection.
     * @param filter Filter.
     * @param trans Transformer.
     */
    public GridCacheTxCollection(Collection<GridCacheTxEntry<K, V>> col, GridPredicate<GridCacheTxEntry<K, V>> filter,
        GridClosure<GridCacheTxEntry<K, V>, T> trans) {
        this.col = col;
        this.filter = filter;
        this.trans = trans;
    }

    /**
     * Seals this map.
     *
     * @return This map for chaining.
     */
    public GridCacheTxCollection<K, V, T> seal() {
        sealed = true;

        return this;
    }

    /**
     * @return Sealed flag.
     */
    public boolean sealed() {
        return sealed;
    }

    /** {@inheritDoc} */
    @Override public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Iterator<GridCacheTxEntry<K,V>> it = col.iterator();

            private GridCacheTxEntry<K, V> cur;

            // Constructor.
            { advance(); }

            @Override public boolean hasNext() {
                return cur != null;
            }

            @Override public T next() {
                if (cur == null) {
                    throw new NoSuchElementException();
                }

                GridCacheTxEntry<K, V> last = cur;

                advance();

                return trans.apply(last);
            }

            @Override public void remove() {
                throw new UnsupportedOperationException();
            }

            private void advance() {
                GridCacheTxEntry<K, V> last = cur;

                cur = null;

                while (last != null) {
                    last = last.child();

                    if (last != null && filter.apply(last)) {
                        cur = last;

                        break;
                    }
                }

                while (cur == null && it.hasNext()) {
                    last = it.next();

                    if (filter.apply(last)) {
                        cur = last;
                    }
                    else {
                        while (last != null) {
                            last = last.child();

                            if (last != null && filter.apply(last)) {
                                cur = last;

                                break;
                            }
                        }
                    }
                }
            }
        };
    }

    /** {@inheritDoc} */
    @Override public int size() {
        return !sealed ? F.size(iterator()) : size == -1 ? size = F.size(iterator()) : size;
    }

    /** {@inheritDoc} */
    @Override public boolean isEmpty() {
        return !sealed ? !iterator().hasNext() : empty == null ? empty = !iterator().hasNext() : empty;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        throw new IllegalStateException("Transaction view map should never be serialized: " + this);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        throw new IllegalStateException("Transaction view map should never be serialized: " + this);
    }
}
