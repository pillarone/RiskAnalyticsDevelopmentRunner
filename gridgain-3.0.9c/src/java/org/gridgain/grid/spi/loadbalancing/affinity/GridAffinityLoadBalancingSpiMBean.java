// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.loadbalancing.affinity;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;
import java.io.*;
import java.util.*;

/**
 * Management bean for {@link GridAffinityLoadBalancingSpi}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to affinity load balancing SPI configuration.")
public interface GridAffinityLoadBalancingSpiMBean extends GridSpiManagementMBean {
    /**
     * Gets number of virtual nodes for Consistent Hashing
     * algorithm. For more information about algorithm, see
     * <a href="http://weblogs.java.net/blog/tomwhite/archive/2007/11/consistent_hash.html">Tom White's Blog</a>.
     *
     * @return Number of virtual nodes.
     */
    @GridMBeanDescription("Gets number of virtual nodes for Consistent Hashing algorithm.")
    public int getVirtualNodeCount();

    /**
     * Gets map of node attributes for nodes that should participate in affinity assignment.
     * Only nodes that have this attributes set will be included.
     * <p>
     * Default value is {@code null} which means all nodes will be added.
     *
     * @return Map of node attributes.
     */
    @GridMBeanDescription("Gets map of node attributes for nodes that should participate in affinity assignment.")
    public Map<String, ? extends Serializable> getAffinityNodeAttributes();

    /**
     * Gets affinity seed used by Consistent Hashing algorithm.
     * By default this seed is empty string.
     * <p>
     * Whenever starting multiple instances of this SPI, you should make
     * sure that every instance has a different seed to achieve different
     * affinity assignment. Otherwise, affinity assignment for different
     * instances of this SPI will be identical, which defeats the purpose
     * of starting multiple affinity load balancing SPI's altogether.
     * <p>
     * <b>Note that affinity seed must be identical for corresponding
     * instances of this SPI on all nodes.</b> If this is not the case,
     * then different nodes will calculate affinity differently which may
     * result in multiple nodes responsible for the same affinity key.
     *
     * @return Non-null value for affinity seed.
     */
    @GridMBeanDescription("Gets affinity seed used for Consistent Hashing algorithm.")
    public String getAffinitySeed();
}
