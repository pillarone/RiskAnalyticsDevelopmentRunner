// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.ipfinder.vm;

import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.net.*;
import java.util.*;

/**
 * Local JVM-based IP finder.
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * There are no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * <ul>
 *      <li>Addresses for initialization (see {@link #setAddresses(Iterable)})</li>
 *      <li>Shared flag (see {@link #setShared(boolean)})</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTcpDiscoveryVmIpFinder extends GridTcpDiscoveryIpFinderAdapter {
    /** Grid logger. */
    @GridLoggerResource
    private GridLogger log;

    /** Addresses. */
    @GridToStringInclude
    private Collection<InetSocketAddress> addrs = new LinkedHashSet<InetSocketAddress>();

    /**
     * Constructs new ip finder.
     */
    public GridTcpDiscoveryVmIpFinder() {
        // No-op.
    }

    /**
     * Constructs new ip finder.
     *
     * @param shared {@code true} if ip finder is shared.
     */
    public GridTcpDiscoveryVmIpFinder(boolean shared) {
        setShared(shared);
    }

    /**
     * Parses provided values and initializes the internal collection of addresses.
     * <p>
     * Addresses may be represented as follows:
     * <ul>
     *     <li>IP address (i.e. 127.0.0.1, 9.9.9.9, etc);</li>
     *     <li>IP address and port (i.e. 127.0.0.1:47500, 9.9.9.9:47501, etc);</li>
     *     <li>Hostname (i.e. host1.com, host2, etc);</li>
     *     <li>Hostname and port (i.e. host1.com:47500, host2:47502, etc).</li>
     * </ul>
     * If port is 0 or not provided then default port will be used (depends on
     * discovery SPI configuration).
     *
     * @param addrs Known nodes addresses.
     * @throws GridSpiException If any error occurs.
     */
    @GridSpiConfiguration(optional = true)
    public synchronized void setAddresses(Iterable<String> addrs) throws GridSpiException {
        if (F.isEmpty(addrs))
            return;

        Collection<InetSocketAddress> newAddrs = new LinkedHashSet<InetSocketAddress>();

        for (String ipStr : addrs) {
            if (ipStr.endsWith(":"))
                ipStr = ipStr.substring(0, ipStr.length() - 1);

            InetSocketAddress addr;

            if (ipStr.indexOf(':') >= 0) {
                StringTokenizer st = new StringTokenizer(ipStr, ":");

                if (st.countTokens() == 2) {
                    String addrStr = st.nextToken();
                    String portStr = st.nextToken();

                    try {
                        int port = Integer.parseInt(portStr);

                        addr = new InetSocketAddress(addrStr, port);
                    }
                    catch (NumberFormatException e) {
                        throw new GridSpiException("Failed to parse provided address: " + ipStr, e);
                    }
                    catch (IllegalArgumentException e) {
                        throw new GridSpiException("Failed to parse provided address: " + ipStr, e);
                    }
                }
                else
                    throw new GridSpiException("Failed to parse provided address: " + ipStr);
            }
            else
                // Provided address does not contain port (will use default one).
                addr = new InetSocketAddress(ipStr, 0);

            newAddrs.add(addr);
        }

        this.addrs = newAddrs;
    }

    /** {@inheritDoc} */
    @Override public synchronized Collection<InetSocketAddress> getRegisteredAddresses() {
        return Collections.unmodifiableCollection(addrs);
    }

    /** {@inheritDoc} */
    @Override public synchronized void registerAddresses(Collection<InetSocketAddress> addrs) {
        assert !F.isEmpty(addrs);

        this.addrs = new LinkedHashSet<InetSocketAddress>(this.addrs);

        this.addrs.addAll(addrs);
    }

    /** {@inheritDoc} */
    @Override public synchronized void unregisterAddresses(Collection<InetSocketAddress> addrs) {
        assert !F.isEmpty(addrs);

        this.addrs = new LinkedHashSet<InetSocketAddress>(this.addrs);

        this.addrs.removeAll(addrs);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryVmIpFinder.class, this, "super", super.toString());
    }
}
