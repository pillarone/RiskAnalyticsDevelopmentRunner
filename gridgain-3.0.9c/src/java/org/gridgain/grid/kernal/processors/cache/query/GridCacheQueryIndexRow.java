// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.kernal.processors.cache.query;

import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

/**
 * Cache query index row.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCacheQueryIndexRow<K, V> {
    /** Key. */
    private K key;

    /** Value. */
    private V val;

    /** Value bytes. */
    private byte[] valBytes;

    /** Version id + order. */
    private String ver;

    /**
     * Constructs query index row.
     *
     * @param key Key.
     * @param val Value ({@code null} for non-primitive type).
     * @param valBytes Value bytes ({@code null} for primitive type).
     * @param ver Version (id + order).
     */
    GridCacheQueryIndexRow(K key, @Nullable V val, @Nullable byte[] valBytes, String ver) {
        assert key != null;
        assert val != null || valBytes != null;
        assert ver != null;

        this.key = key;
        this.val = val;
        this.valBytes = valBytes;
        this.ver = ver;
    }

    /**
     * @return Key.
     */
    public K key() {
        return key;
    }

    /**
     * @return Value.
     */
    public V value() {
        return val;
    }

    /**
     * @return Value bytes.
     */
    public byte[] valueBytes() {
        return valBytes;
    }

    /**
     * @return Version as a string (id + order).
     */
    public String version() {
        return ver;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheQueryIndexRow.class, this);
    }
}
