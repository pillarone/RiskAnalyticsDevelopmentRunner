// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.metrics.jdk;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.metrics.*;
import org.gridgain.grid.util.mbean.*;

/**
 * Management MBean for {@link GridJdkLocalMetricsSpi} SPI.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to JDK local metrics SPI configuration.")
public interface GridJdkLocalMetricsSpiMBean extends GridLocalMetrics, GridSpiManagementMBean {
    /**
     * Configuration parameter indicating if Hyperic Sigar should be used regardless
     * of JDK version. Hyperic Sigar is used to provide CPU load. Starting with JDK 1.6,
     * method {@code OperatingSystemMXBean.getSystemLoadAverage()} method was added.
     * However, even in 1.6 and higher this method does not always provide CPU load
     * on some operating systems - in such cases Hyperic Sigar will be used automatically.
     *
     * @return If {@code true} then Hyperic Sigar should be used regardless of JDK version,
     *      if {@code false}, then implementation will attempt to use
     *      {@code OperatingSystemMXBean.getSystemLoadAverage()} for JDK 1.6 and higher.
     */
    @GridMBeanDescription("Parameter indicating if Hyperic Sigar should be used regardless of JDK version.")
    public boolean isPreferSigar();
}
