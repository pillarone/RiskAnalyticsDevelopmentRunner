// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.multicast;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;
import java.util.*;

/**
 * Management bean for {@link GridMulticastDiscoverySpi}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to IP-multicast based discovery SPI configuration.")
public interface GridMulticastDiscoverySpiMBean extends GridSpiManagementMBean {
    /**
     * Gets IP address of multicast group.
     *
     * @return Multicast IP address.
     */
    @GridMBeanDescription("IP address of multicast group.")
    public String getMulticastGroup();

    /**
     * Gets port number which multicast messages are sent to.
     *
     * @return Port number.
     */
    @GridMBeanDescription("Port number which multicast messages are sent to.")
    public int getMulticastPort();

    /**
     * Gets local port number that is used by discovery SPI.
     *
     * @return Port number.
     */
    @GridMBeanDescription("Local port number that is used by discovery SPI.")
    public int getTcpPort();

    /**
     * Gets local port range for either TCP or multicast ports. See
     * {@link GridMulticastDiscoverySpi#setLocalPortRange(int)} for details.
     *
     * @return Local port range
     */
    @GridMBeanDescription("Local port range for either TCP or multicast ports.")
    public int getLocalPortRange();

    /**
     * Gets delay between heartbeat requests. SPI sends broadcast messages in
     * configurable time interval to another nodes to notify them about node state.
     *
     * @return Time period in milliseconds.
     */
    @GridMBeanDescription("Delay between heartbeat requests in milliseconds.")
    public long getHeartbeatFrequency();

    /**
     * Gets heartbeat thread priority.
     *
     * @return Heartbeat thread priority.
     */
    @GridMBeanDescription("Heartbeat thread priority.")
    public int getHeartbeatThreadPriority();

    /**
     * Gets number of heartbeat requests that could be missed before remote
     * node is considered to be failed.
     *
     * @return Number of requests.
     */
    @GridMBeanDescription("Number of heartbeat requests that could be missed before remote node is considered to be failed.")
    public int getMaximumMissedHeartbeats();

    /**
     * Gets number of attempts to notify another nodes that this one is leaving grid.
     * It might be impossible to send leaving request and node will try to do
     * it several times.
     *
     * @return Number of retries.
     */
    @GridMBeanDescription("Number of attempts to notify another nodes that this one is leaving grid.")
    public int getLeaveAttempts();

    /**
     * Gets set of remote nodes IDs that have {@code READY} state.
     *
     * @return Set of remote nodes IDs.
     */
    @GridMBeanDescription("Set of remote nodes IDs.")
    public Collection<UUID> getRemoteNodeIds();

    /**
     * Gets the number of remote nodes.
     *
     * @return Number of remote nodes.
     */
    @GridMBeanDescription("Number of remote nodes.")
    public int getRemoteNodeCount();

    /**
     * Gets TCP messages time-to-live.
     *
     * @return TCP messages time-to-live.
     */
    @GridMBeanDescription("TCP messages time-to-live.")
    public int getTimeToLive();

    /**
     * By default this value is {@code true}. On startup GridGain will check
     * if local node can receive multicast packets, and if not, will not allow
     * the node to startup.
     *
     * @return checkMulticastEnabled {@code True} if multicast check is enabled,
     *      {@code false} otherwise.
     */
    public boolean isCheckMulticastEnabled();
}
