// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.coherence;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.discovery.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.net.*;
import java.util.*;

/**
 * Represents local or remote node and its attributes. Discovery SPI use this
 * description to check node status (alive/failed), keep local and remote node
 * attributes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCoherenceDiscoveryNode extends GridMetadataAwareAdapter implements GridNode {
    /** Node IP address. */
    @GridToStringInclude private final Collection<String> addrs;

    /** Cluster member identifier. */
    private final byte[] mbrUid;

    /** Node attributes. */
    @GridToStringExclude private Map<String, Object> attrs = new HashMap<String, Object>();

    /** Node unique Id. */
    private UUID id;

    /** Hash value. */
    private int hash;

    /** */
    @GridToStringExclude private GridNodeMetrics metrics;

    /** */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    @GridToStringExclude private final transient GridDiscoveryMetricsProvider metricsProvider;

    /** */
    private boolean leaving;

    /**
     * Create instance of node description with given IP address and
     * cluster member Uid.
     *
     * @param addr Node IP address.
     * @param mbrUid Cluster member Uid.
     * @param metricsProvider Local node metrics provider.
     */
    GridCoherenceDiscoveryNode(InetAddress addr, byte[] mbrUid, GridDiscoveryMetricsProvider metricsProvider) {
        assert addr != null;
        assert mbrUid != null;

        this.metricsProvider = metricsProvider;
        this.mbrUid = mbrUid;

        addrs = Collections.singleton(addr.getHostAddress());

        hash = 17;

        for (byte b : mbrUid) {
            hash = 37 * hash + b;
        }
    }

    /**
     * Create instance of node description with given node data.
     *
     * @param data Node data.
     */
    GridCoherenceDiscoveryNode(GridCoherenceDiscoveryNodeData data) {
        this(data.getAddress(), data.getMemberUid(), null);

        onDataReceived(data);
    }

    /** {@inheritDoc} */
    @Override public long order() {
        return getMetrics().getNodeStartTime();
    }

    /**
     * Update node with received data.
     *
     * @param data Node data.
     */
    void onDataReceived(GridCoherenceDiscoveryNodeData data) {
        assert data != null;
        assert Arrays.equals(data.getMemberUid(), mbrUid);
        assert attrs.isEmpty();
        assert data.getId() != null;

        Map<String, Object> dataAttrs = data.getAllAttributes();

        if (dataAttrs != null) {
            attrs.putAll(dataAttrs);
        }

        attrs = U.sealMap(attrs);
        
        synchronized (this) {
            metrics = data.getMetrics();

            id = data.getId();
        }
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
        assert addrs != null;

        return F.first(addrs);
    }

    /**
     * Sets node leaving status.
     */
    void onLeaving() {
        leaving = true;
    }

    /**
     * Gets node leaving status.
     *
     * @return Node leaving status.
     */
    public boolean isLeaving() {
        return leaving;
    }

    /**
     * Gets cluster member Uid.
     *
     * @return Cluster member Uid.
     */
    byte[] getMemberUid() {
        return mbrUid;
    }

    /** {@inheritDoc} */
    @Override public synchronized UUID getId() {
        return id;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <T> T getAttribute(String name) {
        return (T)attrs.get(name);
    }

    /** {@inheritDoc} */
    @Override public  Map<String, Object> getAttributes() {
        // Attributes should not be modified after creation.
        return attrs;
    }

    /** {@inheritDoc} */
    @Override public GridNodeMetrics getMetrics() {
        if (metricsProvider == null) {
            synchronized (this) {
                assert metrics != null;

                return metrics;
            }
        }

        return metricsProvider.getMetrics();
    }

    /**
     * Update node metrics.
     *
     * @param metrics Up-to-date node metrics.
     */
    synchronized void onMetricsReceived(GridNodeMetrics metrics) {
        this.metrics = metrics;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        return F.eqNodes(this, o);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return hash;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCoherenceDiscoveryNode.class, this, "mbrUid", U.byteArray2HexString(mbrUid));
    }
}
