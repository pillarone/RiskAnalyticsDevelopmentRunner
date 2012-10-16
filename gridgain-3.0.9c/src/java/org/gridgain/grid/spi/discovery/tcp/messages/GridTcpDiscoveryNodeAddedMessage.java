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
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Message telling nodes that new node should be added to topology.
 * When newly added node receives the message it connects to its next and finishes
 * join process.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridTcpDiscoveryEnsureDelivery
public class GridTcpDiscoveryNodeAddedMessage extends GridTcpDiscoveryAbstractMessage {
    /** Added node. */
    private GridTcpDiscoveryNode node;

    /** Pending messages from previous node. */
    private Collection<GridTcpDiscoveryAbstractMessage> msgs;

    /** Current topology. Initialized by coordinator. */
    @GridToStringInclude
    private Collection<GridTcpDiscoveryNode> top;

    /**
     * Public default no-arg constructor for {@link Externalizable} interface.
     */
    public GridTcpDiscoveryNodeAddedMessage() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param creatorNodeId Creator node ID.
     * @param node Node to add to topology.
     */
    public GridTcpDiscoveryNodeAddedMessage(UUID creatorNodeId, GridTcpDiscoveryNode node) {
        super(creatorNodeId);

        assert node != null;

        this.node = node;
    }

    /**
     * Gets newly added node.
     *
     * @return New node.
     */
    public GridTcpDiscoveryNode node() {
        return node;
    }

    /**
     * Gets pending messages sent to new node by its previous.
     *
     * @return Pending messages from previous node.
     */
    @Nullable public Collection<GridTcpDiscoveryAbstractMessage> messages() {
        return msgs;
    }

    /**
     * Sets pending messages to send to new node.
     *
     * @param msgs Pending messages to send to new node.
     */
    public void messages(@Nullable Collection<GridTcpDiscoveryAbstractMessage> msgs) {
        this.msgs = msgs;
    }

    /**
     * Gets topology.
     *
     * @return Current topology.
     */
    public Collection<GridTcpDiscoveryNode> topology() {
        return top;
    }

    /**
     * Sets topology.
     *
     * @param top Current topology.
     */
    public void topology(@Nullable Collection<GridTcpDiscoveryNode> top) {
        this.top = top;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeObject(node);
        U.writeCollection(out, msgs);
        U.writeCollection(out, top);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        node = (GridTcpDiscoveryNode)in.readObject();
        msgs = U.readCollection(in);
        top = U.readCollection(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryNodeAddedMessage.class, this, "super", super.toString());
    }
}
