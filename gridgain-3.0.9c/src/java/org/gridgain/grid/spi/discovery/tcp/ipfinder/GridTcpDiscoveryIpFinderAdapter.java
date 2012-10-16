// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.ipfinder;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.net.*;
import java.util.*;

/**
 * IP finder interface implementation adapter.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridTcpDiscoveryIpFinderAdapter implements GridTcpDiscoveryIpFinder {
    /** Addresses for segment check. */
    @GridToStringInclude
    private Collection<InetAddress> segChkAddrs;

    /** Shared flag. */
    private boolean shared;

    /** {@inheritDoc} */
    @Override public boolean isShared() {
        return shared;
    }

    /**
     * Sets shared flag.
     *
     * @param shared {@code true} if this IP finder is shared.
     */
    @GridSpiConfiguration(optional = true)
    public void setShared(boolean shared) {
        this.shared = shared;
    }

    /** {@inheritDoc} */
    @Override public Collection<InetAddress> getSegmentCheckAddresses() {
        return segChkAddrs;
    }

    /**
     * Sets segment check addresses.
     *
     * @param segChkAddrs Segment check address.
     */
    public void setSegmentCheckAddrs(Collection<InetAddress> segChkAddrs) {
        this.segChkAddrs = segChkAddrs;
    }

    /**
     * Sets segment check addresses.
     * <p>
     * Parses each provided entry and initializes the underlying collection.
     * <p>
     * Addresses may be represented as follows:
     * <ul>
     *     <li>IP address (i.e. 127.0.0.1, 9.9.9.9, etc);</li>
     *     <li>Hostname (i.e. host1.com, host2, etc).</li>
     * </ul>
     *
     * @param segChkAddrs Segment check addresses.
     * @throws GridSpiException If an error occurs.
     */
    @GridSpiConfiguration(optional = true)
    public void setSegmentCheckAddressesAsStrings(Iterable<String> segChkAddrs) throws GridSpiException {
        if (F.isEmpty(segChkAddrs))
            return;

        this.segChkAddrs = new LinkedHashSet<InetAddress>();

        for (String addr : segChkAddrs) {
            try {
                this.segChkAddrs.add(InetAddress.getByName(addr));
            }
            catch (UnknownHostException e) {
                throw new GridSpiException("Failed to resolve host address: " + addr, e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryIpFinderAdapter.class, this);
    }
}
