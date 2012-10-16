// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.tcp.ipfinder.*;
import org.gridgain.grid.spi.discovery.tcp.metricsstore.*;
import org.gridgain.grid.spi.discovery.tcp.topologystore.*;
import org.gridgain.grid.util.mbean.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Management bean for {@link GridTcpDiscoverySpi}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridTcpDiscoverySpiMBean extends GridSpiManagementMBean {
    /**
     * Gets delay between heartbeat messages sent by coordinator.
     *
     * @return Time period in milliseconds.
     */
    @GridMBeanDescription("Heartbeat frequency.")
    public int getHeartbeatFrequency();

    /**
     * Gets current SPI state.
     *
     * @return Current SPI state.
     */
    @GridMBeanDescription("SPI state.")
    public String getSpiState();

    /**
     * Gets {@link GridTcpDiscoveryIpFinder} (string representation).
     *
     * @return IPFinder (string representation).
     */
    @GridMBeanDescription("IP Finder.")
    public String getIpFinderName();

    /**
     * Gets {@link GridTcpDiscoveryMetricsStore} (string representation).
     *
     * @return Metrics store string representation or {@code null} if SPI
     * does not use metrics store.
     */
    @GridMBeanDescription("Metrics store.")
    @Nullable public String getMetricsStoreName();

    /**
     * Gets number of connection attempts.
     *
     * @return Number of connection attempts.
     */
    @GridMBeanDescription("Reconnect count.")
    public int getReconnectCount();

    /**
     * Gets network timeout.
     *
     * @return Network timeout.
     */
    @GridMBeanDescription("Network timeout.")
    public int getNetworkTimeout();

    /**
     * Gets local TCP port SPI listens to.
     *
     * @return Local port range.
     */
    @GridMBeanDescription("Local TCP port.")
    public int getLocalPort();

    /**
     * Gets local TCP port range.
     *
     * @return Local port range.
     */
    @GridMBeanDescription("Local TCP port range.")
    public int getLocalPortRange();

    /**
     * Gets max heartbeats count node can miss without initiating status check.
     *
     * @return Max missed heartbeats.
     */
    @GridMBeanDescription("Max missed heartbeats.")
    public int getMaxMissedHeartbeats();

    /**
     * Gets thread priority. All threads within SPI will be started with it.
     *
     * @return Thread priority.
     */
    @GridMBeanDescription("Threads priority.")
    public int getThreadPriority();

    /**
     * Gets stores (IP finder and metrics store) clean frequency.
     *
     * @return IP finder clean frequency.
     */
    @GridMBeanDescription("Stores clean frequency.")
    public int getStoresCleanFrequency();

    /**
     * Gets statistics print frequency.
     *
     * @return Statistics print frequency in milliseconds.
     */
    @GridMBeanDescription("Statistics print frequency.")
    public int getStatisticsPrintFrequency();

    /**
     * Gets {@link GridTcpDiscoveryTopologyStore} (string representation).
     *
     * @return Topology store string representation or {@code null} if SPI
     * does not use topology store.
     */
    @GridMBeanDescription("Topology store.")
    @Nullable public String getTopologyStoreName();

    /**
     * Gets message worker queue current size.
     *
     * @return Message worker queue current size.
     */
    @GridMBeanDescription("Message worker queue current size.")
    public int getMessageWorkerQueueSize();

    /**
     * Gets joined nodes count.
     *
     * @return Nodes joined count.
     */
    @GridMBeanDescription("Nodes joined count.")
    public long getNodesJoined();

    /**
     * Gets left nodes count.
     *
     * @return Left nodes count.
     */
    @GridMBeanDescription("Nodes left count.")
    public long getNodesLeft();

    /**
     * Gets failed nodes count.
     *
     * @return Failed nodes count.
     */
    @GridMBeanDescription("Nodes failed count.")
    public long getNodesFailed();

    /**
     * Gets pending messages registered count.
     *
     * @return Pending messages registered count.
     */
    @GridMBeanDescription("Pending messages registered.")
    public long getPendingMessagesRegistered();

    /**
     * Gets pending messages discarded count.
     *
     * @return Pending messages registered count.
     */
    @GridMBeanDescription("Pending messages discarded.")
    public long getPendingMessagesDiscarded();

    /**
     * Gets avg message processing time.
     *
     * @return Avg message processing time.
     */
    @GridMBeanDescription("Avg message processing time.")
    public long getAvgMessageProcessingTime();

    /**
     * Gets max message processing time.
     *
     * @return Max message processing time.
     */
    @GridMBeanDescription("Max message processing time.")
    public long getMaxMessageProcessingTime();

    /**
     * Gets total received messages count.
     *
     * @return Total received messages count.
     */
    @GridMBeanDescription("Total received messages count.")
    public int getTotalReceivedMessages();

    /**
     * Gets received messages counts (grouped by type).
     *
     * @return Map containing message types and respective counts.
     */
    @GridMBeanDescription("Received messages by type.")
    public Map<String, Integer> getReceivedMessages();

    /**
     * Gets total processed messages count.
     *
     * @return Total processed messages count.
     */
    @GridMBeanDescription("Total processed messages count.")
    public int getTotalProcessedMessages();

    /**
     * Gets processed messages counts (grouped by type).
     *
     * @return Map containing message types and respective counts.
     */
    @GridMBeanDescription("Received messages by type.")
    public Map<String, Integer> getProcessedMessages();

    /**
     * Gets time local node has been coordinator since.
     *
     * @return Time local node is coordinator since.
     */
    @GridMBeanDescription("Local node is coordinator since.")
    public long getCoordinatorSinceTimestamp();

    /**
     * Gets current coordinator.
     *
     * @return Gets current coordinator.
     */
    @GridMBeanDescription("Coordinator node ID.")
    @Nullable public UUID getCoordinator();

    /**
     * Gets check all addresses flag.
     *
     * @return {@code true} if all addresses reachability is required by configuration.
     */
    @GridMBeanDescription("All addresses reachability required.")
    public boolean isAllAddressesReachabilityRequired();

    /**
     * Gets check segment flag. If {@code false} segment check will be omitted.
     *
     * @return {@code true} if segment check is enabled.
     */
    @GridMBeanDescription("Check segment enabled.")
    public boolean isCheckSegmentEnabled();
}
