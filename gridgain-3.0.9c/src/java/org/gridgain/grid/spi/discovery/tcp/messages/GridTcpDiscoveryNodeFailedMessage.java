// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.messages;

import org.gridgain.grid.spi.discovery.tcp.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Sent by node that has detected nodes failure to coordinator across the ring,
 * then sent by coordinator across the ring.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridTcpDiscoveryEnsureDelivery
public class GridTcpDiscoveryNodeFailedMessage extends GridTcpDiscoveryAbstractMessage {
    /** IDs of the failed nodes. */
    @GridToStringInclude
    private Collection<UUID> failedNodesIds;

    /**
     * Public default no-arg constructor for {@link Externalizable} interface.
     */
    public GridTcpDiscoveryNodeFailedMessage() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param creatorNodeId ID of the node that detects nodes failure.
     * @param failedNodesIds IDs of the failed nodes.
     */
    public GridTcpDiscoveryNodeFailedMessage(UUID creatorNodeId, Collection<UUID> failedNodesIds) {
        super(creatorNodeId);

        this.failedNodesIds = failedNodesIds;
    }

    /**
     * Gets IDs of the failed nodes.
     *
     * @return IDs of the failed nodes.
     */
    public Collection<UUID> failedNodesIds() {
        return failedNodesIds;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeCollection(out, failedNodesIds);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        failedNodesIds = U.readList(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryNodeFailedMessage.class, this, "super", super.toString());
    }
}
