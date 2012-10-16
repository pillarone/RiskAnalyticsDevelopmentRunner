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

import java.io.*;
import java.util.*;

/**
 * Update topology message.
 * <p>
 * Used only if topology store is used.
 * <p>
 * It is sent by coordinator node across the ring each time topology store is
 * updated. Each node should synchronize local topologies with the store as
 * soon as it receives this message.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridTcpDiscoveryEnsureDelivery
public class GridTcpDiscoveryUpdateTopologyMessage extends GridTcpDiscoveryAbstractMessage {
    /** Local (transient) process flag. */
    private boolean processed;

    /**
     * Public default no-arg constructor for {@link Externalizable} interface.
     */
    public GridTcpDiscoveryUpdateTopologyMessage() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param creatorNodeId Creator node.
     */
    public GridTcpDiscoveryUpdateTopologyMessage(UUID creatorNodeId) {
        super(creatorNodeId);
    }

    /**
     * Gets topology version to update to.
     * <p>
     * This method and the underlying field is used only if topology store is used.
     *
     * @return Topology version.
     */
    @Override public long topologyVersion() {
        return super.topologyVersion();
    }

    /**
     * Sets least topology version to update to.
     * <p>
     * This method and the underlying field is used only if topology store is used.
     *
     * @param topVer Topology version.
     */
    @Override public void topologyVersion(long topVer) {
        super.topologyVersion(topVer);
    }

    /**
     * Gets processed flag.
     *
     * @return {@code true} if message has been locally processed.
     */
    public boolean processed() {
        return processed;
    }

    /**
     * Gets processed flag.
     *
     * @param processed Processed flag.
     */
    public void processed(boolean processed) {
        this.processed = processed;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryUpdateTopologyMessage.class, this, "super", super.toString());
    }
}
