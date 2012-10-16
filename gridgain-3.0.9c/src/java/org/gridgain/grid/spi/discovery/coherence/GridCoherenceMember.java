// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.coherence;

import com.tangosol.net.*;
import com.tangosol.util.*;
import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.net.*;

/**
 * Contains data from Coherence {@link Member} interface. An instance of this class
 * can be accessed by calling {@link GridNode#getAttribute(String) GridNode.getAttribute(GridCoherenceDiscoverySpi.ATTR_COHERENCE_MBR)}
 * method.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCoherenceMember implements Serializable {
    /** */
    private int id = -1;

    /** */
    private int machineId = -1;

    /** */
    private String machineName;

    /** */
    private String mbrName;

    /** */
    private InetAddress addr;

    /** */
    private int port = -1;

    /** */
    private long tstamp = -1;

    /** */
    private UID uid;

    /**
     * Creates bean with member data.
     *
     * @param mbr Coherence node (member).
     */
    public GridCoherenceMember(Member mbr) {
        id = mbr.getId();
        machineId = mbr.getMachineId();
        machineName = mbr.getMachineName();
        mbrName = mbr.getMemberName();
        addr = mbr.getAddress();
        port = mbr.getPort();
        tstamp = mbr.getTimestamp();
        uid = mbr.getUid();
    }

    /**
     * Return a small number that uniquely identifies the Member at this
     * point in time and does not change for the life of this Member.
     *
     * @return Mini-id of the Member.
     */
    public int getId() {
        return id;
    }

    /**
     * Return the Member's machine Id.
     *
     * @return Member's machine Id.
     */
    public int getMachineId() {
        return machineId;
    }

    /**
     * Determine the configured name for the Machine (such as a host name)
     * in which this Member resides.
     *
     * @return configured Machine name or null.
     */
    public String getMachineName() {
        return machineName;
    }

    /**
     * Determine the configured name for the Member.
     *
     * @return Configured Member name or null
     */
    public String getMemberName() {
        return mbrName;
    }

    /**
     * Return the IP address of the Member's DatagramSocket
     * for point-to-point communication.
     *
     * @return IP address of the Member's DatagramSocket.
     */
    public InetAddress getAddress() {
        return addr;
    }

    /**
     * Return the port of the Member's DatagramSocket
     * for point-to-point communication.
     *
     * @return Port of the Member's DatagramSocket.
     */
    public int getPort() {
        return port;
    }

    /**
     * Return the date/time value (in cluster time)
     * that the Member joined.
     *
     * @return Cluster date/time value that the Member joined.
     */
    public long getTimestamp() {
        return tstamp;
    }

    /**
     * Return the unique Coherence identifier of the Member.
     *
     * @return Unique identifier of the Member.
     */
    public UID getUid() {
        return uid;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCoherenceMember.class, this);
    }
}
