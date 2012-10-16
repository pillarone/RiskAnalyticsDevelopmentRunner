// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.cloud.ec2lite;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;
import java.util.*;

/**
 * Management bean that provides general administrative and configuration information
 * about EC2-based cloud SPI.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to EC2-based Cloud SPI configuration.")
public interface GridEc2LiteCloudSpiMBean extends GridSpiManagementMBean {
    /**
     * @return Cloud ID.
     */
    @GridMBeanDescription("Cloud ID.")
    public String getCloudId();

    /**
     * @return State check frequency.
     */
    @GridMBeanDescription("State check frequency.")
    public long getStateCheckFrequency();

    /**
     * @return EC2 access key.
     */
    @GridMBeanDescription("EC2 access key.")
    public String getAccessKeyId();

    /**
     * @return HTTP proxy host.
     */
    @GridMBeanDescription("HTTP proxy host.")
    public String getProxyHost();

    /**
     * @return HTTP proxy port.
     */
    @GridMBeanDescription("HTTP proxy port.")
    public int getProxyPort();

    /**
     * @return HTTP proxy user name.
     */
    @GridMBeanDescription("HTTP proxy user name.")
    public String getProxyUsername();

    /**
     * @return CloudWatch monitoring flag.
     */
    @GridMBeanDescription("Is CloudWatch monitoring enabled by default.")
    public boolean isEnableMonitoring();

    /**
     * @return IDs of EC2 images used in this cloud.
     */
    @GridMBeanDescription("IDs of EC2 images used in this cloud.")
    public List<String> getImageIds();
}
