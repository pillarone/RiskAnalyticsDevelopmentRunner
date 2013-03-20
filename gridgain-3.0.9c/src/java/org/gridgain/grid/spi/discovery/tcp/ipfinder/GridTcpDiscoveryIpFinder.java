// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.ipfinder;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.tcp.*;
import org.jetbrains.annotations.*;

import java.net.*;
import java.util.*;

/**
 * IP finder interface for {@link GridTcpDiscoverySpi}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridTcpDiscoveryIpFinder {
    /**
     * Gets all addresses registered in this finder.
     *
     * @return All known addresses, potentially empty, but never {@code null}.
     * @throws GridSpiException In case of error.
     */
    public Collection<InetSocketAddress> getRegisteredAddresses() throws GridSpiException;

    /**
     * Gets special addresses that should be accessible from all nodes withing a good segment to
     * avoid incorrect segmentation of the network.
     * <p>
     * May return {@code null}, but in this case segment check should be disabled in SPI.
     *
     * @return Segment check addresses.
     */
    @Nullable public Collection<InetAddress> getSegmentCheckAddresses();

    /**
     * Checks whether IP finder is shared or not.
     * <p>
     * If it is shared then only coordinator can unregister addresses.
     * <p>
     * All nodes should register their address themselves, as early as possible on node start.
     *
     * @return {@code true} if IP finder is shared.
     */
    public boolean isShared();

    /**
     * Registers new addresses.
     * <p>
     * Implementation should accept duplicates quietly, but should not register address if it
     * is already registered.
     *
     * @param addrs Addresses to register. Not {@code null} and not empty.
     * @throws GridSpiException In case of error.
     */
    public void registerAddresses(Collection<InetSocketAddress> addrs) throws GridSpiException;

    /**
     * Unregisters provided addresses.
     * <p>
     * Implementation should accept addresses that are currently not
     * registered quietly (just no-op).
     *
     * @param addrs Addresses to unregister. Not {@code null} and not empty.
     * @throws GridSpiException In case of error.
     */
    public void unregisterAddresses(Collection<InetSocketAddress> addrs) throws GridSpiException;
}
