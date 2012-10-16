// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.jms;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.io.*;
import java.util.*;

/**
 * Wrapper of discovery message. This wrapper keeps message type, source node Id
 * and destination address inside.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJmsDiscoveryMessage implements Serializable {
    /** Discovery message type. */
    private GridJmsDiscoveryMessageType msgType;

    /** Source node Id. */
    private final UUID nodeId;

    /** List of source node attributes. */
    private final Map<String, Object> attrs;

    /** Remote (destination) node address. */
    private final String addr;

    /** */
    @GridToStringExclude private GridNodeMetrics metrics;

    /**
     * Creates instance of message wrapper. This message is for the certain node.
     *
     * @param msgType Message type.
     * @param nodeId Source node UID this message is sent from.
     * @param attrs Source node attributes.
     * @param addr Destination node address.
     * @param metrics TODO
     */
    GridJmsDiscoveryMessage(GridJmsDiscoveryMessageType msgType, UUID nodeId, Map<String, Object> attrs,
        String addr, GridNodeMetrics metrics) {
        assert msgType != null;
        assert nodeId != null;

        this.msgType = msgType;
        this.nodeId = nodeId;
        this.attrs = attrs;
        this.addr = addr;
        this.metrics = metrics;
    }

    /**
     * Creates broadcast message.
     *
     * @param msgType Message type.
     * @param nodeId Source UID this message is sent from.
     */
    GridJmsDiscoveryMessage(GridJmsDiscoveryMessageType msgType, UUID nodeId) {
        assert msgType != null;
        assert nodeId != null;

        this.msgType = msgType;
        this.nodeId = nodeId;

        attrs = null;
        addr = null;
        metrics = null;
    }

    /**
     * Gets message type.
     *
     * @return Message type.
     */
    GridJmsDiscoveryMessageType getMessageType() { return msgType; }

    /**
     * Sets message type.
     *
     * @param msgType Message type.
     */
    void setMessageType(GridJmsDiscoveryMessageType msgType) { this.msgType = msgType; }

    /**
     * Gets local node UID this message is sent from.
     *
     * @return Source node UID.
     */
    UUID getNodeId() { return nodeId; }

    /**
     * Gets names source node attributes.
     *
     * @return Source node attributes.
     */
    Map<String, Object> getAttributes() { return attrs; }

    /**
     * Gets destination node address.
     *
     * @return Destination node address.
     */
    String getAddress() { return addr; }

    /**
     * Gets node metrics.
     *
     * @return Node metrics.
     */
    GridNodeMetrics getMetrics() { return metrics; }

    /**
     * Sets node metrics.
     *
     * @param metrics Node metrics.
     */
    void setMetrics(GridNodeMetrics metrics) { this.metrics = metrics; }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJmsDiscoveryMessage.class, this);
    }
}
