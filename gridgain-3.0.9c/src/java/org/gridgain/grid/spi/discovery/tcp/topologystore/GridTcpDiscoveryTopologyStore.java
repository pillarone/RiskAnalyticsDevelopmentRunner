// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.topologystore;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.tcp.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Topology store interface for {@link GridTcpDiscoverySpi}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridTcpDiscoveryTopologyStore {
    /**
     * Gets current topology version.
     * <p>
     * Implementation should obtain read lock for store.
     *
     * @return Topology version.
     * @throws GridSpiException If any error occurs.
     */
    public long topologyVersion() throws GridSpiException;

    /**
     * Clears topology and sets current version to 0.
     * <p>
     * First node in topology (coordinator) should call this method prior to putting
     * itself to store using {@link #put(GridTcpDiscoveryTopologyStoreNode)}.
     * <p>
     * Implementation should obtain exclusive write lock for store.
     *
     * @throws GridSpiException If any error occurs.
     */
    public void clear() throws GridSpiException;

    /**
     * Gets all nodes with topology version greater, than the first parameter value
     * and less or equal to the second parameter value ordered by topology version.
     * (see {@link GridTcpDiscoveryTopologyStoreNode#topologyVersion()}).
     * <p>
     * Implementation should obtain read lock for store.
     *
     * @param minTopVer Minimal topology version.
     * @param maxTopVer Max topology version.
     * @return Nodes having topology version greater, than the first parameter
     * value and less or equal to the second parameter value.
     * @throws GridSpiException If any error occurs.
     */
    public Collection<GridTcpDiscoveryTopologyStoreNode> nodes(long minTopVer, long maxTopVer) throws GridSpiException;

    /**
     * Puts node to topology store.
     * <p>
     * Coordinator puts node whenever its state changes.
     * <p>
     * Implementation should obtain exclusive write lock for store.
     *
     * @param node Node to put.
     * @throws GridSpiException If any error occurs.
     */
    public void put(GridTcpDiscoveryTopologyStoreNode node) throws GridSpiException;

    /**
     * Evicts nodes having topology version less or equal to parameter value.
     * <p>
     * Coordinator calls this method with the minimal topology version nodes in
     * topology have.
     * <p>
     * Implementation should obtain exclusive write lock for store.
     *
     * @param maxTopVer Maximum topology version to evict.
     * @throws GridSpiException If any error occurs.
     */
    public void evict(long maxTopVer) throws GridSpiException;

    /**
     * Gets actual node state.
     * <p>
     * Implementation should obtain read lock for store.
     *
     * @param id Node ID.
     * @return Node state.
     * @throws GridSpiException If any error occurs.
     */
    @Nullable public GridTcpDiscoveryTopologyStoreNodeState state(UUID id) throws GridSpiException;
}
