// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.multicast;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Multicast discovery message.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridMulticastDiscoveryMessage implements Serializable {
    /** */
    private final GridMulticastDiscoveryMessageType type;

    /** Sender node ID. */
    private final UUID nodeId;

    /** */
    private final InetAddress addr;

    /** */
    private final int port;

    /** Map of node attributes. */
    private final Map<String, Object> attrs;

    /** */
    private final long startTime;

    /** */
    @GridToStringExclude private final GridNodeMetrics metrics;

    /**
     * @param type Message type.
     */
    GridMulticastDiscoveryMessage(GridMulticastDiscoveryMessageType type) {
        assert type != null;

        this.type = type;

        nodeId = null;
        attrs = null;
        addr = null;
        port = -1;
        startTime = -1;
        metrics = null;
    }

    /**
     * @param type Message type.
     * @param nodeId TODO
     * @param addr TODO
     * @param port TODO
     * @param attrs Node attributes.
     * @param startTime Start time.
     * @param metrics TODO
     */
    GridMulticastDiscoveryMessage(GridMulticastDiscoveryMessageType type, UUID nodeId, InetAddress addr,
        int port, Map<String, Object> attrs, long startTime, GridNodeMetrics metrics) {
        assert type != null;
        assert nodeId != null;
        assert addr != null;
        assert port > 0;
        assert attrs != null;
        assert startTime > 0;
        assert metrics != null;

        this.type = type;
        this.nodeId = nodeId;
        this.attrs = attrs;
        this.addr = addr;
        this.port = port;
        this.startTime = startTime;
        this.metrics = metrics;
    }

    /**
     * Gets message data.
     *
     * @return Message data.
     */
    Map<String, Object> getAttributes() { return attrs; }

    /**
     * Gets message type.
     *
     * @return Message type.
     */
    GridMulticastDiscoveryMessageType getType() { return type; }

    /**
     * Gets sender node ID.
     *
     * @return Sender node ID.
     */
    UUID getNodeId() { return nodeId; }

    /**
     * Gets sender address.
     *
     * @return Sender address.
     */
    InetAddress getAddress() { return addr; }

    /**
     * Gets sender port.
     *
     * @return Sender port.
     */
    int getPort() { return port; }

    /**
     * Gets sender node start time.
     *
     * @return sender node start time.
     */
    long getStartTime() { return startTime; }

    /**
     * Gets node metrics.
     *
     * @return Node metrics.
     */
    GridNodeMetrics getMetrics() { return metrics; }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMulticastDiscoveryMessage.class, this);
    }
}
