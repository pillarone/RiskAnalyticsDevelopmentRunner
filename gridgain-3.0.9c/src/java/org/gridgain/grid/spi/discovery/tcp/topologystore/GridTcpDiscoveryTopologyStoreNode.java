// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.topologystore;

import java.util.*;

/**
 * Node interface for topology store.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridTcpDiscoveryTopologyStoreNode {
    /**
     * Gets node ID.
     *
     * @return ID of the node.
     */
    public UUID id();

    /**
     * Gets node order within grid topology.
     *
     * @return Node order.
     */
    public long order();

    /**
     * Gets state of the node.
     *
     * @return State of the node.
     */
    public GridTcpDiscoveryTopologyStoreNodeState state();

    /**
     * Gets topology version of the node.
     *
     * @return Topology version.
     */
    public long topologyVersion();

    /**
     * Sets topology version of the node.
     *
     * @param topVer Topology version.
     */
    public void topologyVersion(long topVer);
}
