// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

import org.gridgain.grid.lang.utils.*;
import java.io.*;

/**
 * This class defines output stream backed by byte array.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridByteArrayOutputStream extends ByteArrayOutputStream {
    /** */
    public GridByteArrayOutputStream() {
        // No-op.
    }

    /**
     * @param size Byte array size.
     */
    public GridByteArrayOutputStream(int size) {
        super(size);
    }

    /**
     * @param buf Byte buffer.
     * @param count Byte buffer size.
     */
    public GridByteArrayOutputStream(byte[] buf, int count) {
        this.buf = buf;
        this.count = count;
    }

    /**
     * @param bytes Byte list.
     */
    public GridByteArrayOutputStream(GridByteArrayList bytes) {
        buf = bytes.getInternalArray();
        count = bytes.getSize();
    }

    /**
     * Gets internal array. Use with caution as changes to byte array
     * will affect this output stream.
     *
     * @return Internal byte array.
     */
    public byte[] getInternalArray() {
        return buf;
    }

    /**
     * Gets {@link GridByteArrayList} wrapper around the internal array.
     *
     * @return Wrapper around the internal array.
     */
    public GridByteArrayList toByteArrayList() {
        return new GridByteArrayList(buf, count);
    }
}
