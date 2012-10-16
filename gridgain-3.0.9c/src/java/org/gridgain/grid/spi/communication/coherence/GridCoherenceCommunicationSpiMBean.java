// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.communication.coherence;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;

/**
 * Management bean that provides access to the Coherence-based communication
 * SPI configuration.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean provides access to the Coherence-based communication SPI configuration.")
public interface GridCoherenceCommunicationSpiMBean extends GridSpiManagementMBean {
    /**
     * Gets Coherence member identifier string representation.
     *
     * @return Coherence member identifier string representation.
     */
    @GridMBeanDescription("Coherence member identifier string representation.")
    public String getLocalUid();

    /**
     * Gets sending acknowledgment property.
     *
     * @return Sending acknowledgment property.
     */
    @GridMBeanDescription("Sending acknowledgment property.")
    public boolean isAcknowledgment();

    /**
     * Gets Coherence service invocation name.
     *
     * @return Coherence service invocation name.
     */
    @GridMBeanDescription("Coherence service invocation name.")
    public String getServiceName();

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
