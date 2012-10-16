// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.util.mbean.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * This interface defines JMX view on {@link GridCloud}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to cloud descriptor.")
public interface GridCloudMBean {
    /**
     * Gets unique ID of this cloud.
     *
     * @return Unique ID of this cloud.
     */
    @GridMBeanDescription("Cloud unique ID.")
    public String id();

    /**
     * Gets optional vendor's parameters associated with this cloud. In general, it is up to the
     * cloud SPI implementation to decide what, if any, metadata will be provided. Also, there is
     * no specific requirement fot this metadata to be constant from call to call and SPI implementation
     * can decide to make the metadata dynamically changing. Please consult cloud SPI
     * implementation for further details.
     *
     * @return Vendor's parameters associated with this cloud.
     */
    @GridMBeanDescription("Cloud vendor's parameters.")
    @Nullable public Map<String, String> parameters();

    /**
     * Gets collection of formatted resources that define this cloud current state.
     *
     * @return Collection of cloud resources that define this cloud current state.
     */
    @GridMBeanDescription("Formatted cloud resources.")
    @Nullable public Collection<String> resourcesFormatted();
}
