package org.gridgain.grid.spi.checkpoint.cache;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;

/**
 * Management bean that provides general administrative and configuration information
 * about cache checkpoint SPI.
 * 
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean provides information about cache checkpoint SPI.")
public interface GridCacheCheckpointSpiMBean extends GridSpiManagementMBean {
    /**
     * Gets cache name to be used by this SPI..
     *
     * @return Cache name to be used by this SPI.
     */
    @GridMBeanDescription("Cache name to be used by this SPI.")
    public String getCacheName();
}
