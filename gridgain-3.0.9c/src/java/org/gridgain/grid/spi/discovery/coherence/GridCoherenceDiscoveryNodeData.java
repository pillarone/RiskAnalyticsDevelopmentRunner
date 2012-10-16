// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.coherence;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Data that are sent by discovery SPI. They include node unique identifier
 * and node attributes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCoherenceDiscoveryNodeData implements Serializable {
    /** Node IP address. */
    private final InetAddress addr;

    /** Cluster member identifier. */
    private final byte[] mbrUid;

    /** Node attributes. */
    private Map<String, Object> attrs;

    /** Node unique Id. */
    private final UUID id;

    /** Node metrics. */
    private final GridNodeMetrics metrics;

    /** Flag indicates whether it's left or not.*/
    private boolean leave;

    /**
     * Creates new instance of Coherence node data.
     *
     * @param id Node identifier.
     * @param mbrUid Cluster member identifier.
     * @param addr Node IP address.
     * @param attrs Node attributes.
     * @param metrics Node metrics.
     */
    GridCoherenceDiscoveryNodeData(UUID id, byte[] mbrUid, InetAddress addr, Map<String, Object> attrs,
        GridNodeMetrics metrics) {
        assert id != null;
        assert mbrUid != null;
        assert addr != null;

        this.id = id;
        this.addr = addr;
        this.mbrUid = mbrUid;
        this.attrs = attrs;
        this.metrics = metrics;
    }

    /**
     * Creates new instance of Coherence node data.
     *
     * @param id Node identifier.
     * @param mbrUid Cluster member identifier.
     * @param addr Node IP address.
     * @param attrs Node attributes.
     * @param leave Flag indicates whether node left or not.
     * @param metrics Node metrics.
     */
    GridCoherenceDiscoveryNodeData(UUID id, byte[] mbrUid, InetAddress addr, Map<String, Object> attrs,
        boolean leave, GridNodeMetrics metrics) {
        this(id, mbrUid, addr, attrs, metrics);

        this.leave = leave;
    }

    /**
     * Gets node IP address.
     *
     * @return Node IP address.
     */
    public InetAddress getAddress() {
        return addr;
    }

    /**
     * Gets node Id.
     *
     * @return Node Id.
     */
    public UUID getId() {
        return id;
    }

    /**
     * Gets cluster member Id.
     *
     * @return Cluster member Id.
     */
    byte[] getMemberUid() { return mbrUid; }

    /**
     * Gets node attributes.
     *
     * @return Node attributes.
     */
    Map<String, Object> getAllAttributes() { return attrs; }

    /**
     * Gets flag whether node left or not.
     *
     * @return TODO.
     */
    public boolean isLeave() {
        return leave;
    }

    /**
     * Sets flag whether node left or not.
     *
     * @param leave Node leaving status.
     */
    public void setLeave(boolean leave) {
        this.leave = leave;
    }

    /**
     * Gets node metrics.
     *
     * @return TODO.
     */
    public GridNodeMetrics getMetrics() {
        return metrics;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCoherenceDiscoveryNodeData.class, this,
            "mbrUid", U.byteArray2HexString(mbrUid));
    }
}
