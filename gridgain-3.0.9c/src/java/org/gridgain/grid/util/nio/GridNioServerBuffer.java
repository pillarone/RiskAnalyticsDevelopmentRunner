// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.nio;

import org.gridgain.grid.lang.utils.*;
import java.nio.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridNioServerBuffer {
    /** Preallocate 8K. */
    private GridByteArrayList msgBytes = new GridByteArrayList(1024 << 3);

    /** */
    private int msgSize = -1;

    /** */
    void reset() {
        msgBytes.reset();

        msgSize = -1;
    }

    /**
     * Gets message size.
     *
     * @return Message size.
     */
    int getMessageSize() { return msgSize; }

    /**
     * Gets message bytes read so far.
     *
     * @return Message bytes read so far.
     */
    GridByteArrayList getMessageBytes() { return msgSize < 0 ? null : msgBytes; }

    /**
     * Checks whether the byte array is filled.
     *
     * @return Flag indicating whether byte array is filled or not.
     */
    boolean isFilled() { return msgSize > 0 && msgBytes.getSize() == msgSize; }

    /**
     * @param buf TODO
     */
    void read(ByteBuffer buf) {
        if (msgSize < 0) {
            int remaining = buf.remaining();

            if (remaining > 0) {
                int missing = 4 - msgBytes.getSize();

                msgBytes.add(buf, missing < remaining ? missing : remaining);

                assert msgBytes.getSize() <= 4;

                if (msgBytes.getSize() == 4) {
                    msgSize = msgBytes.getInt(0);

                    assert msgSize > 0;

                    msgBytes.reset();

                    // Allocate required size.
                    msgBytes.allocate(msgSize);
                }
            }
        }

        int remaining = buf.remaining();

        // If there are more bytes in buffer.
        if (remaining > 0) {
            int missing = msgSize - msgBytes.getSize();

            // Read only up to message size.
            if (missing > 0) {
                msgBytes.add(buf, missing < remaining ? missing : remaining);
            }
        }
    }
}