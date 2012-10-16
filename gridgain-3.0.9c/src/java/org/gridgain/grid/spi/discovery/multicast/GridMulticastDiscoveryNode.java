// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.multicast;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.net.*;
import java.util.*;

import static org.gridgain.grid.spi.discovery.multicast.GridMulticastDiscoveryNodeState.*;

/**
 * Class represents single node in the grid. Every node has unique identifier, state , attributes,
 * IP address and port.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridMulticastDiscoveryNode extends GridMetadataAwareAdapter implements GridNode {
    /** Node unique identifier. */
    private final UUID id;

    /** Node state. */
    private GridMulticastDiscoveryNodeState state = NEW;

    /** Node attributes. */
    @GridToStringExclude
    private Map<String, Object> attrs;

    /** Time when node sent heartbeat request last. */
    private long lastHeartbeat = System.currentTimeMillis();

    /** Collection of IP addresses. */
    @GridToStringInclude
    private Collection<String> addrs;

    /** Node IP address. */
    private InetAddress addr;

    /** Node port number. */
    private int tcpPort;

    /** Node wake-up time. */
    private long startTime = -1;

    /** */
    @GridToStringExclude
    private GridNodeMetrics metrics;

    /** */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    @GridToStringExclude
    private final transient GridDiscoveryMetricsProvider metricsProvider;

    /**
     * Creates new node instance.
     *
     * @param id Node unique identifier.
     * @param addr Node IP address.
     * @param tcpPort Node port number.
     * @param startTime Node wake-up time.
     * @param metrics Node metrics.
     */
    GridMulticastDiscoveryNode(UUID id, InetAddress addr, int tcpPort, long startTime, GridNodeMetrics metrics) {
        assert id != null;
        assert addr != null;
        assert tcpPort > 0;
        assert tcpPort <= 0xffff;

        this.id = id;
        this.addr = addr;
        this.tcpPort = tcpPort;
        this.startTime = startTime;
        this.metrics = metrics;

        addrs = Collections.singleton(addr.getHostAddress());

        metricsProvider = null;

        state = NEW;
    }

    /**
     * Creates new node instance.
     *
     * @param id Node unique identifier.
     * @param addr Node IP address.
     * @param tcpPort Node port number.
     * @param startTime Node wake-up time.
     * @param metricsProvider Metrics provider.
     */
    GridMulticastDiscoveryNode(UUID id, InetAddress addr, int tcpPort, long startTime,
        GridDiscoveryMetricsProvider metricsProvider) {
        assert id != null;
        assert addr != null;
        assert tcpPort > 0;
        assert tcpPort <= 0xffff;
        assert metricsProvider != null;

        this.id = id;
        this.addr = addr;
        this.tcpPort = tcpPort;
        this.startTime = startTime;
        this.metricsProvider = metricsProvider;

        addrs = Collections.singleton(addr.getHostAddress());

        // State new because node does not have attributes yet.
        state = NEW;
    }

    /** {@inheritDoc} */
    @Override public UUID getId() {
        return id;
    }

    /** {@inheritDoc} */
    @Override public UUID id() {
        return id;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <T> T attribute(String name) {
        return (T)getAttribute(name);
    }

    /** {@inheritDoc} */
    @Override public GridNodeMetrics metrics() {
        return getMetrics();
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> attributes() {
        return getAttributes();
    }

    /** {@inheritDoc} */
    @Override public long order() {
        return getMetrics().getNodeStartTime();
    }

    /**
     * Gets node local IP address.
     *
     * @return IP address.
     */
    InetAddress getInetAddress() {
        return addr;
    }

    /**
     * Gets node local port number.
     *
     * @return Port number.
     */
    int getTcpPort() {
        return tcpPort;
    }

    /**
     * Gets node wake-up time.
     *
     * @return Time in milliseconds.
     */
    long getStartTime() {
        return startTime;
    }

    /** {@inheritDoc} */
    @Override public Collection<String> internalAddresses() {
        return addrs;
    }

    /** {@inheritDoc} */
    @Override public Collection<String> externalAddresses() {
        return addrs;
    }

    /** {@inheritDoc} */
    @Override public String getPhysicalAddress() {
        assert !F.isEmpty(addrs);

        return F.first(addrs);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <T> T getAttribute(String name) {
        assert getState() == READY || getState() == LEFT : "Invalid state: " + getState();

        return (T)attrs.get(name);
    }

    /** {@inheritDoc} */
    @Override public GridNodeMetrics getMetrics() {
        if (metricsProvider == null) {
            synchronized (this) {
                assert metrics != null;

                return metrics;
            }
        }

        return setGetMetrics(metricsProvider.getMetrics());
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public Map<String, Object> getAttributes() {
        assert getState() == READY || getState() == LEFT : "Invalid state: " + getState();

        return attrs;
    }

    /**
     * This method is called to initialize local node.
     *
     * @param attrs Local node attributes.
     */
    void setAttributes(Map<String, Object> attrs) {
        createAttributes(attrs);

        synchronized (this) {
            state = READY;
        }
    }

    /**
     * Method creates internal map of attributes.
     *
     * @param attrs Initial attributes.
     */
    private void createAttributes(Map<String, Object> attrs) {
        assert this.attrs == null;

        // Seal it.
        this.attrs = attrs != null ? U.sealMap(attrs) : Collections.<String, Object>emptyMap();
    }

    /**
     * This method is called when heartbeat request from remote node represented
     * by this object comes.
     *
     * @param metrics Node Metrics.
     */
    synchronized void onHeartbeat(GridNodeMetrics metrics) {
        this.metrics = metrics;

        lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * @param metrics Metrics to set.
     * @return The passed in metrics.
     */
    private synchronized GridNodeMetrics setGetMetrics(GridNodeMetrics metrics) {
        this.metrics = metrics;

        return metrics;
    }

    /**
     * Gets time when heartbeat request from this node came last.
     *
     * @return Time in milliseconds.
     */
    synchronized long getLastHeartbeat() {
        return lastHeartbeat;
    }

    /**
     * Method is called when remote node represented by this object changes its state to {@code LEFT}.
     */
    synchronized void onLeft() {
        state = LEFT;

        lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * Called for failed nodes.
     */
    synchronized void onFailed() {
        state = LEFT;
    }

    /**
     * Gets current node state.
     *
     * @return Node state.
     * @see GridMulticastDiscoveryNodeState
     */
    synchronized GridMulticastDiscoveryNodeState getState() {
        return state;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return id.hashCode();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        return F.eqNodes(this, o);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMulticastDiscoveryNode.class, this);
    }
}
