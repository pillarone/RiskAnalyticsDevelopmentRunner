// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.jetbrains.annotations.*;
import java.util.concurrent.*;

/**
 * Defines interface for node-local storage.
 * <p>
 * Node-local values are similar to thread locals in a way that these values are not
 * distributed and kept only on local node (similar like thread local values are attached to the
 * current thread only). Node-local values are used primarily by closures executed from the remote
 * nodes to keep intermediate state on the local node between executions.
 * <p>
 * Currently, this interface simply extends {@link ConcurrentMap} and serves as a future
 * extension point.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridNodeLocal<K, V> extends ConcurrentMap<K, V>, GridMetadataAware {
    /**
     * Gets the value with given key. If that value does not exist, calls given closure
     * to get the default value, puts it into the map and returns it. If closure is {@code null}
     * return {@code null}.
     *
     * @param key Key to get the value for.
     * @param dflt Default value producing closure.
     * @return Value for the key or the value produced by the closure if key
     *      does not exist in the map. Return {@code null} if key is not found and
     *      closure is {@code null}.
     */
    public V addIfAbsent(K key, @Nullable Callable<V> dflt);

    /**
     * Unlike its sibling method {@link #putIfAbsent(Object, Object)} this method returns
     * current mapping from the map.
     *
     * @param key Key.
     * @param val Value to put if one does not exist.
     * @return Current mapping for a given key.
     */
    public V addIfAbsent(K key, V val);
}
