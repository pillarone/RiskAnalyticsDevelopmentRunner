// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.mail;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.util.*;

import static org.gridgain.grid.spi.discovery.mail.GridMailDiscoveryNodeState.*;

/**
 * Represents local or remote node and its attributes. Discovery SPI use this
 * description to check node status (alive/failed), keep local and remote node
 * attributes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridMailDiscoveryNode extends GridMetadataAwareAdapter implements GridNode {
    /** Node Id. */
    private final UUID id;

    /** Collection of addresses for this node. */
    private final Collection<String> addrs;

    /** Node start time. */
    private final long startTime;

    /** Last heartbeat time. */
    private long lastHeartbeat = System.currentTimeMillis();

    /** Node attributes. */
    @GridToStringExclude
    private Map<String, Object> attrs;

    /** Node state. */
    private GridMailDiscoveryNodeState state;

    /** Node metrics. */
    @GridToStringExclude private GridNodeMetrics metrics;

    /** Metrics provider. */
    @SuppressWarnings({"TransientFieldNotInitialized"}) @GridToStringExclude
    private final transient GridDiscoveryMetricsProvider metricsProvider;

    /**
     * Creates new node instance.
     *
     * @param id Node Id.
     * @param fromAddr Email 'From' address.
     * @param startTime Node start time.
     * @param state Node state.
     * @param metrics Node metrics.
     * @param metricsProvider Metrics provider.
     */
    GridMailDiscoveryNode(UUID id, String fromAddr, long startTime, GridMailDiscoveryNodeState state,
        GridNodeMetrics metrics, GridDiscoveryMetricsProvider metricsProvider) {
        assert id != null;
        assert fromAddr != null;
        assert startTime > 0;
        assert state != null;
        assert metrics != null || metricsProvider != null;

        this.id = id;
        this.startTime = startTime;
        this.state = state;
        this.metrics = metrics;
        this.metricsProvider = metricsProvider;

        addrs = Collections.singleton(fromAddr);
    }

    /** {@inheritDoc} */
    @Override public long order() {
        return getMetrics().getNodeStartTime();
    }

    /**
     * Gets time when SPI was started.
     *
     * @return Time in milliseconds.
     */
    long getStartTime() {
        return startTime;
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

    /**
     * Gets node state.
     *
     * @return Node state.
     * @see GridMailDiscoveryNodeState
     */
    synchronized GridMailDiscoveryNodeState getState() {
        return state;
    }

    /**
     * Sets node state.
     *
     * @param state Node state.
     */
    synchronized void setState(GridMailDiscoveryNodeState state) {
        this.state = state;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <T> T getAttribute(String name) {
        return (T)attrs.get(name);
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> getAttributes() {
        return new HashMap<String, Object>(attrs);
    }

    /** {@inheritDoc} */
    @Override public GridNodeMetrics getMetrics() {
        if (metricsProvider != null) {
            return metricsProvider.getMetrics();
        }

        synchronized (this) {
            return metrics;
        }
    }

    /**
     * Gets last heartbeat time.
     *
     * @return Time in milliseconds.
     */
    synchronized long getLastHeartbeat() {
        return lastHeartbeat;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        return F.eqNodes(this, o);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return id.hashCode();
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

    /**
     * Update last heartbeat time.
     *
     * @param metrics Node metrics.
     */
    synchronized void onHeartbeat(GridNodeMetrics metrics) {
        lastHeartbeat = System.currentTimeMillis();

        this.metrics = metrics;
    }

    /**
     * This method is called to initialize local node.
     *
     * @param attrs Local node attributes.
     */
    void setAttributes(Map<String, Object> attrs) {
        this.attrs = U.sealMap(new HashMap<String, Object>(attrs));

        synchronized (this) {
            state = READY;
        }
    }

    /**
     * Update node with received node attributes.
     *
     * @param attrs Node attributes.
     */
    void onDataReceived(Map<String, Object> attrs) {
        this.attrs = new HashMap<String, Object>(attrs);

        synchronized (this) {
            state = READY;
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMailDiscoveryNode.class, this);
    }
}
