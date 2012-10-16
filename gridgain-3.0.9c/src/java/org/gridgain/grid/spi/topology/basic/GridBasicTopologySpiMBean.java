// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.topology.basic;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;

/**
 * Management bean for {@link GridBasicTopologySpi}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to basic topology SPI configuration.")
public interface GridBasicTopologySpiMBean extends GridSpiManagementMBean {
    /**
     * Indicates whether or not to return remote nodes.
     *
     * @return Whether or not to return remote nodes.
     */
    @GridMBeanDescription("Indicates whether or not to return remote nodes.")
    public boolean isRemoteNodes();

    /**
     * Indicates whether or not to return local node.
     *
     * @return Whether or not to return local node.
     */
    @GridMBeanDescription("Indicates whether or not to return local node.")
    public boolean isLocalNode();
}
