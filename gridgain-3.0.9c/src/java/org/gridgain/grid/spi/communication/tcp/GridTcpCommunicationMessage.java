// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.communication.tcp;

import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.util.*;

/**
 * Wrapper of TCP message. A kind of container for all communication messages
 * over TCP/IP protocol. Beside message itself it provides sender node id.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridTcpCommunicationMessage implements Externalizable {
    /** Communication message. */
    private Serializable msg;

    /** Sender node unique identifier. */
    private UUID nodeId;

    /**
     * No-op constructor to support {@link Externalizable} interface.
     * This constructor is not meant to be used for other purposes.
     */
    public GridTcpCommunicationMessage() {
        /* No-op. */
    }

    /**
     * Creates new instance of wrapper.
     *
     * @param nodeId Sender node UID.
     * @param msg Message is being sent.
     */
    GridTcpCommunicationMessage(UUID nodeId, Serializable msg) {
        assert nodeId != null;
        assert msg != null;

        this.nodeId = nodeId;
        this.msg = msg;
    }

    /**
     * Gets unwrapped message.
     *
     * @return Message that was sent.
     */
    Serializable getMessage() {
        return msg;
    }

    /**
     * Gets sender node UID.
     *
     * @return Node ID.
     */
    UUID getNodeId() {
        return nodeId;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(msg);
        U.writeUuid(out, nodeId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        msg = (Serializable)in.readObject();
        nodeId = U.readUuid(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpCommunicationMessage.class, this);
    }
}

