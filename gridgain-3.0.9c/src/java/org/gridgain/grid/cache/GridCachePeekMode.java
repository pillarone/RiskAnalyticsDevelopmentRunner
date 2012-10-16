// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.cache;

import org.jetbrains.annotations.*;

/**
 * Enumeration of all supported cache peek modes. Peek modes can be passed into various
 * {@code 'GridCacheProjection.peek(..)'} and {@code GridCacheEntry.peek(..)} methods,
 * such as {@link GridCacheProjection#peek(Object, GridCachePeekMode...)},
 * {@link GridCacheEntry#peek(GridCachePeekMode...)}, and others.
 * <p>
 * The following modes are supported:
 * <ul>
 * <li>{@link #TX}</li>
 * <li>{@link #GLOBAL}</li>
 * <li>{@link #SMART}</li>
 * <li>{@link #SWAP}</li>
 * <li>{@link #DB}</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public enum GridCachePeekMode {
    /** Peeks value only from in-transaction memory of an ongoing transaction, if any. */
    TX,

    /** Peeks at cache global (not in-transaction) memory. */
    GLOBAL,

    /**
     * In this mode value is peeked from in-transaction memory first using {@link #TX}
     * mode and then, if it has not been found there, {@link #GLOBAL} mode is used to
     * search in committed cached values.
     */
    SMART,

    /** Peeks value only from cache swap storage without loading swapped value into cache. */
    SWAP,

    /** Peek value from the underlying persistent storage without loading this value into cache. */
    DB;

    /** Enumerated values. */
    private static final GridCachePeekMode[] VALS = values();

    /**
     * Efficiently gets enumerated value from its ordinal.
     *
     * @param ord Ordinal value.
     * @return Enumerated value or {@code null} if ordinal out of range.
     */
    @Nullable public static GridCachePeekMode fromOrdinal(byte ord) {
        return ord >= 0 && ord < VALS.length ? VALS[ord] : null;
    }
}
