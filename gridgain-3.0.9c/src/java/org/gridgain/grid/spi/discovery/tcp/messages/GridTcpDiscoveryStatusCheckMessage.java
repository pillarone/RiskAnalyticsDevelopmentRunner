// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.messages;

import org.gridgain.grid.spi.discovery.tcp.internal.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;

/**
 * Message sent by node to its next to ensure that next node and
 * connection to it are alive. Receiving node should send it across the ring,
 * until message does not reach coordinator. Coordinator responds directly to node.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTcpDiscoveryStatusCheckMessage extends GridTcpDiscoveryAbstractMessage {
    /** Status OK. */
    public static final int STATUS_OK = 1;

    /** Status RECONNECT. */
    public static final int STATUS_RECONNECT = 2;

    /** Creator node. */
    private GridTcpDiscoveryNode creatorNode;

    /** Creator node status (initialized by coordinator). */
    private int status;

    /**
     * Public default no-arg constructor for {@link Externalizable} interface.
     */
    public GridTcpDiscoveryStatusCheckMessage() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param creatorNode Creator node.
     */
    public GridTcpDiscoveryStatusCheckMessage(GridTcpDiscoveryNode creatorNode) {
        super(creatorNode.id());

        this.creatorNode = creatorNode;
    }

    /**
     * Gets creator node.
     *
     * @return Creator node.
     */
    public GridTcpDiscoveryNode creatorNode() {
        return creatorNode;
    }

    /**
     * Gets creator status.
     *
     * @return Creator node status.
     */
    public int status() {
        return status;
    }

    /**
     * Sets creator node status (should be set by coordinator).
     *
     * @param status Creator node status.
     */
    public void status(int status) {
        this.status = status;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeObject(creatorNode);
        out.writeInt(status);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        creatorNode = (GridTcpDiscoveryNode)in.readObject();
        status = in.readInt();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryStatusCheckMessage.class, this, "super", super.toString());
    }
}
