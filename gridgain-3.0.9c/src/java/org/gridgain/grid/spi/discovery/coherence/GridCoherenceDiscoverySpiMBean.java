// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.coherence;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;
import java.util.*;

/**
 * Management bean for {@link GridCoherenceDiscoverySpi}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to Coherence-based discovery SPI configuration.")
public interface GridCoherenceDiscoverySpiMBean extends GridSpiManagementMBean {
    /**
     * Gets set of discovered remote nodes IDs.
     *
     * @return Set of remote nodes IDs.
     */
    @GridMBeanDescription("Set of remote nodes IDs.")
    public Collection<UUID> getRemoteNodeIds();

    /**
     * Gets number of remote nodes.
     *
     * @return Number of remote nodes.
     */
    @GridMBeanDescription("Number of remote nodes.")
    public int getRemoteNodeCount();

    /**
     * Gets Coherence cache name.
     *
     * @return Coherence cache name
     */
    @GridMBeanDescription("Coherence cache name.")
    public String getCacheName();

    /**
     * Gets delay between metrics requests. SPI sends broadcast messages in
     * configurable time interval to another nodes to notify them about node metrics.
     *
     * @return Time period in milliseconds.
     */
    @GridMBeanDescription("Time period in milliseconds.")
    public long getMetricsFrequency();

    /**
     * Gets either absolute or relative to GridGain installation home folder path
     * to Coherence XML configuration file.
     *
     * @return Path to Coherence configuration file.
     */
    @GridMBeanDescription("Path to Coherence configuration file.")
    public String getConfigurationFile();

    /**
     * Gets flag to start Coherence cache server {@code DefaultCacheServer}
     * on a dedicated daemon thread or not.
     *
     * @return Flag indicates whether Coherence DefaultCacheServer should be started in SPI or not.
     */
    @GridMBeanDescription("Flag to start Coherence DefaultCacheServer as daemon on a dedicated daemon thread.")
    public boolean isStartCacheServerDaemon();
}
