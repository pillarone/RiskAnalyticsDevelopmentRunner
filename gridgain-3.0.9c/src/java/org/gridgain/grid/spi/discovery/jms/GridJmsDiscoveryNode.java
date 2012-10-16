// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.jms;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Represents local or remote node and its attributes. Discovery SPI use this
 * description to check node status (alive/failed), keep local and remote node
 * attributes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJmsDiscoveryNode extends GridMetadataAwareAdapter implements GridNode {
    /**
     * Node state. If node sends heartbeats and  its attributes
     * node state will be ready. Otherwise node state is not ready.
     */
    private volatile boolean ready;

    /** Last received heartbeat message time. */
    private volatile long lastHeartbeat = System.currentTimeMillis();

    /** Node attributes. */
    @GridToStringExclude
    private Map<String, Object> attrs;

    /** */
    private final UUID id;

    /** Collection of IP addresses. */
    @GridToStringInclude private final Collection<String> addrs;

    /** Node TCP/IP address. */
    private final String addr;

    /** */
    @GridToStringExclude
    private volatile GridNodeMetrics metrics;

    /** */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    @GridToStringExclude
    private final transient GridDiscoveryMetricsProvider metricsProvider;

    /**
     * Create instance of node description with given UID and TCP/IP address.
     *
     * @param id Node UID.
     * @param addr Node address.
     * @param metrics Metrics.
     * @param metricsProvider Metrics provider.
     */
    GridJmsDiscoveryNode(UUID id, String addr, GridNodeMetrics metrics, GridDiscoveryMetricsProvider metricsProvider) {
        assert id != null;
        assert addr != null;
        assert metrics != null || metricsProvider != null;

        this.id = id;
        this.addr = addr;
        this.metrics = metrics;
        this.metricsProvider = metricsProvider;

        addrs = Collections.singleton(addr);
    }

    /** {@inheritDoc} */
    @Override public UUID getId() {
        assert id != null;

        return id;
    }

    /** {@inheritDoc} */
    @Override public long order() {
        return getMetrics().getNodeStartTime();
    }

    /** {@inheritDoc} */
    @Override public Collection<String> internalAddresses() {
        return addrs;
    }

    /** {@inheritDoc} */
    @Override public Collection<String> externalAddresses() {
        return addrs;
    }

    /**
     * Gets node TCP/IP address.
     *
     * @return Node address.
     */
    @Override public String getPhysicalAddress() {
        assert addr != null;

        return addr;
    }

    /** {@inheritDoc} */
    @Override public UUID id() {
        return getId();
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
     * Gets node state whether it's ready or not.
     *
     * @return Node state.
     */
    public boolean isReady() {
        return ready;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable
    @Override public <T> T getAttribute(String name) {
        // No need to synchronize here, as attributes never set and retrieved concurrently.
        // Attributes get set during start and get retrieved after start.
        return (T) ((T)attrs == null ? null : attrs.get(name));
    }

    /**
     * Gets last received heartbeat time.
     *
     * @return Time in milliseconds.
     */
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    /**
     * Method process heartbeat notifications. Every time when delivery SPI gets
     * a heartbeat message from remote node associated with this instance last is notified.
     *
     * @param metrics Metrics.
     */
    void onHeartbeat(GridNodeMetrics metrics) {
        lastHeartbeat = System.currentTimeMillis();

        this.metrics = metrics;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public Map<String, Object> getAttributes() {
        return attrs;
    }

    /**
     * When discovery SPI receives attributes from remote node associated with this instance it calls this
     * method. Method changes node state to {@code ready}
     *
     * @param attrs Local node attributes.
     * @see #isReady()
     */
    void setAttributes(Map<String, Object> attrs) {
        // Seal it. No need to synchronize here, as attributes never set and retrieved concurrently.
        // Attributes get set during start and get retrieved after start.
        this.attrs = U.sealMap(attrs);

        ready = true;
    }

    /** {@inheritDoc} */
    @Override public GridNodeMetrics getMetrics() {
        if (metricsProvider != null) {
            return metricsProvider.getMetrics();
        }

        return metrics;
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
    @Override public String toString() {
        return S.toString(GridJmsDiscoveryNode.class, this);
    }
}
