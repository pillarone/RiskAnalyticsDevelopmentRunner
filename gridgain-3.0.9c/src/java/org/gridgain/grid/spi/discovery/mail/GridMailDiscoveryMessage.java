// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.mail;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.io.*;
import java.util.*;

/**
 * Wrapper of discovery message. This wrapper keeps message type, source node Id, destination address
 * and another fields inside.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridMailDiscoveryMessage implements Serializable {
    /** Source node Id. */
    private final UUID srcNodeId;

    /** Flag indicates whether it's left or not.*/
    private final boolean leave;

    /** Time when message was sent. */
    private final long sendTime;

    /** Time when SPI was started. */
    private final long nodeStartTime;

    /** Node Id's requested to reply for ping. */
    private final Collection<UUID> pingedNodes;

    /** Node Id's requested to reply with attributes. */
    private final Collection<UUID> attrNodes;

    /** List of source node attributes. */
    private final Map<String, Object> nodeAttrs;

    /** Email message 'From' attribute. */
    private final String fromAddr;

    /** Grid node metrics. */
    @GridToStringExclude private final GridNodeMetrics metrics;

    /**
     * Creates message.
     *
     * @param srcNodeId Source node Id.
     * @param leave Node left or not.
     * @param pingedNodes Node Id's requested to reply for ping.
     * @param attrNodes Node Id's requested to reply with attributes.
     * @param nodeAttrs Node attributes.
     * @param fromAddr Email address 'From' field.
     * @param sendTime Time when message was sent.
     * @param nodeStartTime Time when SPI was started.
     * @param metrics Grid node metrics.
     */
    GridMailDiscoveryMessage(
        UUID srcNodeId,
        boolean leave,
        Collection<UUID> pingedNodes,
        Collection<UUID> attrNodes,
        Map<String, Object> nodeAttrs,
        String fromAddr,
        long sendTime,
        long nodeStartTime,
        GridNodeMetrics metrics) {
        assert srcNodeId != null;
        assert !leave || attrNodes == null && pingedNodes == null;
        assert sendTime > 0;
        assert nodeStartTime > 0;
        assert metrics != null;

        this.srcNodeId = srcNodeId;
        this.leave = leave;
        this.pingedNodes = pingedNodes;
        this.attrNodes = attrNodes;
        this.nodeAttrs = nodeAttrs;
        this.fromAddr = fromAddr;
        this.sendTime = sendTime;
        this.nodeStartTime = nodeStartTime;
        this.metrics = metrics;
    }

    /**
     * Gets node state.
     *
     * @return {@code true} if node is leaving grid and {@code false} otherwise.
     */
    boolean isLeave() { return leave; }

    /**
     * Email address 'From' field.
     *
     * @return Email address 'From' field.
     */
    String getFromAddress() { return fromAddr; }

    /**
     * Gets node identifier.
     *
     * @return Node identifier.
     */
    UUID getSourceNodeId() { return srcNodeId; }

    /**
     * Gets collection of node Id's requested to reply for ping.
     *
     * @return Collection of node Id's.
     */
    Collection<UUID> getPingedNodes() { return pingedNodes; }

    /**
     * Gets collection of node Id's requested to reply with attributes.
     *
     * @return Collection of node Id's.
     */
    Collection<UUID> getAttributeNodes() {  return attrNodes; }

    /**
     * Gets node attributes.
     *
     * @return Node attributes.
     */
    Map<String, Object> getAttributes() { return nodeAttrs; }

    /**
     * Gets time when message was sent.
     *
     * @return Time in milliseconds.
     */
    long getSendTime() { return sendTime; }

    /**
     * Gets time when SPI was started.
     *
     * @return Time in milliseconds.
     */
    long getNodeStartTime() { return nodeStartTime; }

    /**
     * Gets node metrics.
     *
     * @return Node metrics.
     */
    GridNodeMetrics getMetrics() { return metrics; }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMailDiscoveryMessage.class, this);
    }
}
