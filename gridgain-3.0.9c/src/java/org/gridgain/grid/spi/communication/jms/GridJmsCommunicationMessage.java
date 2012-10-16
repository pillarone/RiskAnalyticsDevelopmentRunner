// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.communication.jms;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.io.*;
import java.util.*;

/**
 * Wrapper of real message. This implementation is a helper class that keeps
 * source node ID and destination node IDs beside the message itself.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJmsCommunicationMessage implements Externalizable {
    /** Message that is being sent. */
    private Serializable msg;

    /** List of node UIDs message is being sent to. */
    private List<UUID> nodeIds;

    /** Source node UID which is sending the message. */
    @GridToStringExclude private UUID nodeId;

    /**
     * No-op constructor to support {@link Externalizable} interface.
     * This constructor is not meant to be used for other purposes.
     */
    public GridJmsCommunicationMessage() {
        // No-op.
    }

    /**
     * Creates instance of communication message. All parameters are mandatory.
     *
     * @param nodeId Source node UID.
     * @param msg Message that is being sent.
     * @param nodeIds Destination node UIDs
     */
    GridJmsCommunicationMessage(UUID nodeId, Serializable msg, List<UUID> nodeIds) {
        assert nodeId != null;
        assert msg != null;
        assert nodeIds != null;

        this.nodeId = nodeId;
        this.msg = msg;
        this.nodeIds = nodeIds;
    }

    /**
     * Gets UID of the node which sent this message.
     *
     * @return Source node UID.
     */
    UUID getNodeId() {
        return nodeId;
    }

    /**
     * Gets message that was sent.
     *
     * @return Sent message.
     */
    Serializable getMessage() {
        return msg;
    }

    /**
     * Gets UIDs of nodes this message was sent to.
     *
     * @return Destination node UIDs.
     */
    List<UUID> getNodesIds() {
        return nodeIds;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(msg);

        int destNodes = nodeIds.size();

        out.writeInt(destNodes);

        if (destNodes > 0) {
            for (UUID id : nodeIds) {
                U.writeUuid(out, id);
            }
        }

        U.writeUuid(out, nodeId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        msg = (Serializable)in.readObject();

        int destNodes = in.readInt();

        // Note, that all deserialized lists will be created as ArrayList.
        nodeIds = new ArrayList<UUID>(destNodes);

        if (destNodes > 0) {
            for (int i = 0; i < destNodes; i++) {
                nodeIds.add(U.readUuid(in));
            }
        }

        nodeId = U.readUuid(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJmsCommunicationMessage.class, this);
    }
}
