// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.multicast;

import org.gridgain.grid.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.typedef.internal.*;
import java.net.*;
import java.util.*;

/**
 * Helper class that simplifies heartbeat request data manipulation. When node wakes up
 * it immediately begins to start sending heartbeats. Sending heartbeats is the only
 * way to notify another node that this one is still alive.
 * <p>
 * Heartbeat request data consist of
 * <ul>
 * <li>Node state (see {@link GridMulticastDiscoveryNodeState})</li>
 * <li>Node unique identifier (see {@link UUID})</li>
 * <li>Local node port</li>
 * <li>Node start time.</li>
 * <li>Node local IP address</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridMulticastDiscoveryHeartbeat {
    /** Length of data static part. */
    static final int STATIC_DATA_LENGTH =
        1/*leaving flag*/ +
        16/*UUID*/ +
        4/*port*/ *
        8/*start time*/ +
        GridDiscoveryMetricsHelper.METRICS_SIZE;

    /** Heartbeat data. */
    private final byte[] data;

    /** Node unique identifier. */
    private final UUID nodeId;

    /** Node local port number. */
    private final int tcpPort;

    /** Node local IP address. */
    private final InetAddress addr;

    /** Time when node woke up. */
    private final long startTime;

    /** Grid node metrics. */
    private GridNodeMetrics metrics;

    /** */
    private int off;

    /**
     * Creates new instance of helper based on node characteristics.
     *
     * @param nodeId Local node identifier.
     * @param addr Node local IP address.
     * @param tcpPort Node local port number.
     * @param isLeaving Indicates whether node is leaving grid or not. {@code true} if yes
     *      and {@code false} if no.
     * @param startTime Node wake-up time.
     */
    GridMulticastDiscoveryHeartbeat(UUID nodeId, InetAddress addr, int tcpPort, boolean isLeaving, long startTime) {
        assert nodeId != null;
        assert addr != null;
        assert tcpPort > 0;
        assert tcpPort < 0xffff;
        assert startTime > 0;

        this.nodeId = nodeId;
        this.tcpPort = tcpPort;
        this.addr = addr;
        this.startTime = startTime;

        // Even though metrics are serialized as object, we assume that size does not change.
        data = new byte[STATIC_DATA_LENGTH + addr.getAddress().length];

        data[0] = (byte)(isLeaving ? 1 : 0);

        off++;

        off = U.longToBytes(nodeId.getLeastSignificantBits(), data, off);
        off = U.longToBytes(nodeId.getMostSignificantBits(), data, off);
        off = U.intToBytes(tcpPort, data, off);
        off = U.longToBytes(startTime, data, off);

        byte[] addrBytes = addr.getAddress();

        System.arraycopy(addrBytes, 0, data, off, addrBytes.length);

        off += addrBytes.length;
    }

    /**
     * Creates new instance of helper based on row bytes array. It also
     * tries to resolve given IP address.
     *
     * @param data Heartbeat request data.
     * @throws UnknownHostException if given IP address could not be resolved.
     */
    GridMulticastDiscoveryHeartbeat(byte[] data) throws UnknownHostException {
        assert data != null;
        assert data.length > STATIC_DATA_LENGTH;

        this.data = data;

        // Leave 1st byte for 'isLeaving' flag.
        off = 1;

        long idLeastSignificantBits = U.bytesToLong(data, off);

        off += 8;

        long idMostSignificantBits = U.bytesToLong(data, off);

        off += 8;

        nodeId = new UUID(idMostSignificantBits, idLeastSignificantBits);

        tcpPort = U.bytesToInt(data, off);

        off += 4;

        startTime = U.bytesToLong(data, off);

        off += 8;

        // Initialize address.
        byte[] addrBytes = new byte[data.length - STATIC_DATA_LENGTH];

        System.arraycopy(data, off, addrBytes, 0, addrBytes.length);

        addr = InetAddress.getByAddress(addrBytes);

        off += addrBytes.length;

        metrics = GridDiscoveryMetricsHelper.deserialize(data, off);
    }

    /**
     * Gets heartbeat data as byte array.
     *
     * @return Heartbeat data.
     */
    byte[] getData() {
        return data;
    }

    /**
     * Gets node identifier.
     *
     * @return Node identifier.
     */
    UUID getNodeId() {
        return nodeId;
    }

    /**
     * Gets port number.
     *
     * @return Port number.
     */
    int getTcpPort() {
        return tcpPort;
    }

    /**
     * Gets node wake-up time.
     *
     * @return Wake-up time.
     */
    long getStartTime() {
        return startTime;
    }

    /**
     * Gets IP address.
     *
     * @return IP address.
     */
    InetAddress getInetAddress() {
        return addr;
    }

    /**
     * Gets node state.
     *
     * @return {@code true} if node is leaving grid and {@code false} otherwise.
     */
    boolean isLeaving() {
        return data[0] == (byte)1;
    }

    /**
     * Gets node metrics.
     *
     * @return Node metrics.
     */
    GridNodeMetrics getMetrics() {
        return metrics;
    }

    /**
     * Sets node metrics.
     *
     * @param metrics Node metrics.
     */
    void setMetrics(GridNodeMetrics metrics) {
        this.metrics = metrics;

        GridDiscoveryMetricsHelper.serialize(data, off, metrics);
    }

    /**
     * Sets node state in heartbeat data to {@code LEFT}.
     *
     * @param isLeaving {@code true} if node is leaving grid and {@code false} otherwise.
     */
    void setLeaving(boolean isLeaving) {
        data[0] = (byte)(isLeaving ? 1 : 0);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMulticastDiscoveryHeartbeat.class, this,
            "isLeaving", data[0] == (byte)1);
    }
}
