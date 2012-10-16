// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.events.*;
import java.util.*;

/**
 * When a grid node leaves the topology it leaves a shadow. A node shadow is a read-only
 * snapshot of its last internal state. Node shadow is available on {@link GridDiscoveryEvent}
 * when node leaves the topology. 
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridNodeShadow extends GridMetadataAware {
    /**
     * Gets globally unique node ID.
     *
     * @return Globally unique node ID.
     */
    public UUID id();

    /**
     * Gets grid name for the node.
     *
     * @return Grid name for the node.
     */
    public String gridName();

    /**
     * Tests whether or not this node is a daemon.
     * <p>
     * Daemon nodes are the usual grid nodes that participate in topology but not
     * visible on the main APIs, i.e. they are not part of any projections. The only
     * way to see daemon nodes is to use this method.
     * <p>
     * Daemon nodes are used primarily for management and monitoring functionality that
     * is build on GridGain and needs to participate in the topology but also needs to be
     * excluded from "normal" topology so that it won't participate in task execution
     * or data grid storage.
     *
     * @return {@code true} if this is a daemon node, {@code false} otherwise.
     */
    public boolean isDaemon();

    /**
     * Gets the timestamp of when this shadow was created.
     *
     * @return Timestamp of creation.
     */
    public long created();

    /**
     * Gets the last snapshot of the metrics for this shadow's node.
     * Note that unlike the live node these metrics won't be updating
     * and calling this method multiple times will result in the same
     * value returned.
     *
     * @return Last snapshot of the metrics for this shadow's node.
     */
    public GridNodeMetrics lastMetrics();

    /**
     * Gets a node attribute.
     *  
     * @param <T> Attribute Type.
     * @param name Attribute name. <b>Note</b> that attribute names starting with
     *      {@code org.gridgain} are reserved for internal use.
     * @return Attribute value or {@code null}.
     */
    public <T> T attribute(String name);

    /**
     * Gets all node attributes. 
     *
     * @return All node attributes.
     */
    public Map<String, Object> attributes();

    /**
     * Gets collection of IP addresses known internally on the same LAN.
     * <p>
     * In many managed environments like Amazon EC2 a virtual instance and therefore a GridGain node
     * can have multiple internal and external IP addresses. Internal addresses are used to address the node
     * from within this environment, while external addresses used to address this node from the outside
     * of this environment.
     *
     * @return Collection of internal IP addresses. This collection is
     *      never {@code null} but can be empty.
     */
    public Collection<String> internalAddresses();

    /**
     * Gets collection of IP addresses known externally outside of ths LAN.
     * <p>
     * In many managed environments like Amazon EC2 a virtual instance and therefore a GridGain node
     * can have multiple internal and external IP addresses. Internal addresses are used to address the node
     * from within this environment, while external addresses used to address this node from the outside
     * of this environment.
     *
     * @return Collection of external IP addresses. This collection is
     *      never {@code null} but can be empty.
     */
    public Collection<String> externalAddresses();

    /**
     * Node order within grid topology. Discovery SPIs that support node ordering will
     * assign a proper order to each node and will guarantee that discovery event notifications
     * for new nodes will come in proper order. All other SPIs not supporting ordering
     * may choose to return node startup time here.
     *
     * @return Node startup order.
     */
    public long order();
}
