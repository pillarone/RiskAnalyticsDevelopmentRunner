// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.checkpoint.coherence;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;

/**
 * Management bean that provides general administrative and configuration information
 * about Tangosol Coherence checkpoint SPI.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean for Coherence-based checkpoint SPI.")
public interface GridCoherenceCheckpointSpiMBean extends GridSpiManagementMBean {
    /**
     * Gets name for Coherence cache where all checkpoints are saved.
     *
     * @return Name for Coherence cache.
     */
    @GridMBeanDescription("Name for Coherence cache where all checkpoints are saved.")
    public String getCacheName();

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
