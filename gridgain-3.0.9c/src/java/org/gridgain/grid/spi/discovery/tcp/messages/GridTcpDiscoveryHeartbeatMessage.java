// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.messages;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Heartbeat message.
 * <p>
 * It is sent by coordinator node across the ring once a configured period.
 * In case if topology does not use metrics store, message makes two passes.
 * <ol>
 *      <li>During first pass, all nodes add their metrics to the message and
 *          update local metrics with metrics currently present in the message.</li>
 *      <li>During second pass, all nodes update all metrics present in the message
 *          and remove their own metrics from the message.</li>
 * </ol>
 * When message reaches coordinator second time it is discarded (it finishes the
 * second pass).
 * <p>
 * If topology uses metrics store then message makes only one pass and metrics map
 * is always empty. Nodes exchange their metrics using metrics store.
 * <p>
 * if topology uses topology store, {@link #topologyVersion()} will return the
 * least version of the topology used by the nodes. Each node should update this
 * field in message, if it uses topology with less version.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTcpDiscoveryHeartbeatMessage extends GridTcpDiscoveryAbstractMessage {
    /** Map to store nodes metrics. */
    @GridToStringExclude
    private Map<UUID, GridNodeMetrics> metrics = new HashMap<UUID, GridNodeMetrics>();

    /**
     * Public default no-arg constructor for {@link Externalizable} interface.
     */
    public GridTcpDiscoveryHeartbeatMessage() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param creatorNodeId Creator node.
     */
    public GridTcpDiscoveryHeartbeatMessage(UUID creatorNodeId) {
        super(creatorNodeId);
    }

    /**
     * Sets metrics for particular node.
     *
     * @param nodeId Node ID.
     * @param metrics Node metrics.
     */
    public void setMetrics(UUID nodeId, GridNodeMetrics metrics) {
        assert nodeId != null;
        assert metrics != null;

        this.metrics.put(nodeId, metrics);
    }

    /**
     * Removes metrics for particular node from the message.
     *
     * @param nodeId Node ID.
     */
    public void removeMetrics(UUID nodeId) {
        assert nodeId != null;

        metrics.remove(nodeId);
    }

    /**
     * Gets metrics map.
     *
     * @return Metrics map.
     */
    public Map<UUID, GridNodeMetrics> metrics() {
        return metrics;
    }

    /**
     * Sets metrics map.
     *
     * @param metrics Metrics map (unmodifiable).
     */
    public void metrics(Map<UUID, GridNodeMetrics> metrics) {
        this.metrics = metrics;
    }

    /**
     * Gets the least topology version used.
     * <p>
     * This method and the underlying field is used only if topology store is used.
     *
     * @return Topology version.
     */
    @Override public long topologyVersion() {
        return super.topologyVersion();
    }

    /**
     * Sets the least topology version used.
     * <p>
     * This method and the underlying field is used only if topology store is used.
     *
     * @param topVer Topology version.
     */
    @Override public void topologyVersion(long topVer) {
        super.topologyVersion(topVer);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeMap(out, metrics);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        metrics = U.readMap(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryHeartbeatMessage.class, this, "super", super.toString());
    }
}
