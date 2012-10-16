// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.swapspace;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Utility wrapper class that represents {@code byte} array or a sub-array.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridSwapByteArray implements Serializable {
    /** Underlying array. */
    @GridToStringExclude
    private byte[] arr;

    /** Offset in underlying array. */
    private int off;

    /** Length in underlying array. */
    private int len;

    /**
     * Creates uninitialized byte array wrapper.
     */
    public GridSwapByteArray() {
        /* No-op. */
    }

    /**
     * Creates byte array wrapper with given underlying base array. Note that in this
     * case {@link #isEntire()} will return {@code true}.
     *
     * @param arr Underlying base array. Note that offset will be set to {@code 0} and
     *      length will be set to {@code arr.length}. Method {@link #isEntire()} will
     *      return {@code true}.
     */
    public GridSwapByteArray(byte[] arr) {
        init(arr);
    }

    /**
     * Creates byte sub-array wrapper with with given parameters.
     *
     * @param arr Underlying base array.
     * @param off Non-negative offset in the array.
     * @param len Non-negative length of the sub-array in the base array.
     */
    public GridSwapByteArray(byte[] arr, int off, int len) {
        init(arr, off, len);
    }

    /**
     * Initializes or re-initializes byte array wrapper with given underlying base array.
     * Note that in this case {@link #isEntire()} will return {@code true}.
     *
     * @param arr Underlying base array. Note that offset will be set to {@code 0} and
     *      length will be set to {@code arr.length}. Method {@link #isEntire()} will
     *      return {@code true}.
     */
    public void init(byte[] arr) {
        init(arr, 0, arr.length);
    }

    /**
     * Initializes or re-initializes byte sub-array wrapper with with given parameters.
     *
     * @param arr Underlying base array.
     * @param off Non-negative offset in the array.
     * @param len Non-negative length of the sub-array in the base array.
     */
    public void init(byte[] arr, int off, int len) {
        assert arr != null;
        assert off >= 0;
        assert len >= 0;

        this.arr = arr;
        this.off = off;
        this.len = len;
    }

    /**
     * Tests whether or not this class wraps the entire array, i.e. offset is zero and
     * length is equal to the length of the base array. If this method return {@code true}
     * caller can simply use {@link #getArray()} to get the array from this wrapper.
     *
     * @return Whether or not this class wraps the entire array.
     * @see #getAsEntire
     */
    public boolean isEntire() {
        return arr != null && off == 0 && len == arr.length;
    }

    /**
     * This method returns sub-array as an array. If method {@link #isEntire()} returns
     * {@code true} this method <b>will not</b> create new array and will return
     * {@link #getArray()} instead - otherwise the new byte array of {@link #getLength()}
     * length will be created, contents from the underlying base array will be copied,
     * and resulting byte array will be returned.
     *
     * @return Wrapped sub-array as an array (making sure not to create extra byte array
     *      when unnecessary).
     */
    public byte[] getAsEntire() {
        if (arr == null) {
            return null;
        }

        if (isEntire()) {
            return getArray();
        }

        byte[] dest = new byte[len];

        System.arraycopy(arr, off, dest, 0, len);

        return dest;
    }

    /**
     * Gets underlying base array. If method {@link #isEntire()} return {@code true} -
     * this array represents the entire array.
     *
     * @return Underlying base array
     * @see #getAsEntire
     */
    public byte[] getArray() {
        return arr;
    }

    /**
     * Gets non-negative offset in the underlying base array.
     *
     * @return Non-negative offset in the underlying base array.
     */
    public int getOffset() {
        return off;
    }

    /**
     * Gets non-negative length in the underlying base array.
     *
     * @return Non-negative length in the underlying base array.
     */
    public int getLength() {
        return len;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof GridSwapByteArray)) {
            return false;
        }

        GridSwapByteArray other = (GridSwapByteArray)obj;

        return Arrays.equals(arr, other.arr) && off == other.off && len == other.len;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return arr.hashCode() + 31 * off + 31 * 31 * len;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridSwapByteArray.class, this);
    }
}
