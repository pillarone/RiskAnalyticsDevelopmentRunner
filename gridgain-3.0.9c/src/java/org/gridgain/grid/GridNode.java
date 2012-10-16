// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.kernal.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.multicast.*;
import java.util.*;

/**
 * Interface representing a single grid node. Use {@link #getAttribute(String)} or
 * {@link #getMetrics()} to get static and dynamic information about remote nodes.
 * {@code GridNode} list, which includes all nodes within task topology, is provided
 * to {@link GridTask#map(List, Object)} method. You can also get a handle on
 * discovered nodes by calling any of the following methods:
 * <ul>
 * <li>{@link Grid#localNode()}</li>
 * <li>{@link Grid#remoteNodes(org.gridgain.grid.lang.GridPredicate[])}</li>
 * <li>{@link Grid#nodes(org.gridgain.grid.lang.GridPredicate[])}</li>
 * </ul>
 * <p>
 * <h1 class="header">Grid Node Attributes</h1>
 * You can use grid node attributes to provide static information about a node.
 * This information is initialized once within grid, during node startup, and
 * remains the same throughout the lifetime of a node. Use
 * {@link GridConfiguration#getUserAttributes()} method to initialize your custom
 * node attributes at startup. For example, to provide benchmark data about
 * every node from Spring XML configuration file, you would do the following:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.cfg" class="org.gridgain.grid.GridConfigurationAdapter" scope="singleton">
 *     ...
 *     &lt;property name="userAttributes">
 *         &lt;map>
 *             &lt;entry key="grid.node.benchmark">
 *                 &lt;bean class="org.gridgain.grid.benchmarks.GridLocalNodeBenchmark" init-method="start"/>
 *             &lt;/entry>
 *         &lt;/map>
 *     &lt;/property>
 *     ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * The system adds the following attributes automatically:
 * <ul>
 * <li>{@code {@link System#getProperties()}} - All system properties.</li>
 * <li>{@code {@link System#getenv(String)}} - All environment properties.</li>
 * <li>{@code org.gridgain.build.ver} - GridGain build version.</li>
 * <li>{@code org.gridgain.jit.name} - Name of JIT compiler used.</li>
 * <li>{@code org.gridgain.net.itf.name} - Name of network interface.</li>
 * <li>{@code org.gridgain.user.name} - Operating system user name.</li>
 * <li>{@code org.gridgain.grid.name} - Grid name (see {@link Grid#getName()}).</li>
 * <li>
 *      {@code spiName.org.gridgain.spi.class} - SPI implementation class for every SPI,
 *      where {@code spiName} is the name of the SPI (see {@link GridSpi#getName()}.
 * </li>
 * <li>
 *      {@code spiName.org.gridgain.spi.ver} - SPI version for every SPI,
 *      where {@code spiName} is the name of the SPI (see {@link GridSpi#getName()}.
 * </li>
 * </ul>
 * <p>
 * Note that all System and Environment properties for all nodes are automatically included
 * into node attributes. This gives you an ability to get any information specified
 * in {@link System#getProperties()} about any node. So for example, in order to print out
 * information about Operating System for all nodes you would do the following:
 * <pre name="code" class="java">
 * for (GridNode node : G.grid().nodes()) {
 *     System.out.println("Operating system name: " + node.getAttribute("os.name"));
 *     System.out.println("Operating system architecture: " + node.getAttribute("os.arch"));
 *     System.out.println("Operating system version: " + node.getAttribute("os.version"));
 * }
 * </pre>
 * <p>
 * This interface provide a system view on the node instance. All user-level APIs work with
 * {@link GridRichNode} interface that provides much more functionality and extends this
 * interface. Consult {@link GridRichNode} for more information.
 * <p>
 * <h1 class="header">Grid Node Metrics</h1>
 * Grid node metrics (see {@link #getMetrics()}) are updated frequently for all nodes
 * and can be used to get dynamic information about a node. The frequency of update
 * is often directly related to the heartbeat exchange between nodes. So if, for example,
 * default {@link GridMulticastDiscoverySpi} is used,
 * the metrics data will be updated every {@code 3} seconds by default.
 * <p>
 * Grid node metrics provide information about other nodes that can frequently change,
 * such as Heap and Non-Heap memory utilization, CPU load, number of active and waiting
 * grid jobs, etc... This information can become useful during job collision resolution or
 * {@link GridTask#map(List, Object)} operation when jobs are assigned to remote nodes
 * for execution. For example, you can only pick nodes that don't have any jobs waiting
 * to be executed.
 * <p>
 * Local node metrics are registered as {@code MBean} and can be accessed from
 * any JMX management console. The simplest way is to use standard {@code jconsole}
 * that comes with JDK as it also provides ability to view any node parameter
 * as a graph.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridRichNode
 */
public interface GridNode extends GridMetadataAware {
    /**
     * Deprecated in favor of {@link #id()} method.
     * <p>
     * Gets globally unique node ID.
     *
     * @return Globally unique node ID.
     */
    @Deprecated
    public UUID getId();

    /**
     * Gets globally unique node ID.
     *
     * @return Globally unique node ID.
     */
    public UUID id();

    /**
     * This method is deprecated in favor of the following two methods:
     * <ul>
     * <li>{@link #externalAddresses()}</li>
     * <li>{@link #internalAddresses()}</li>
     * </ul>
     * Default implementation returns one of the addressed returned by
     * {@link #externalAddresses()} or {@link #internalAddresses()} method.
     * <p>
     * Gets physical address of the node. In most cases, although it is not
     * strictly guaranteed, it is an IP address of a node.
     *
     * @return Physical address of the node.
     */
    @Deprecated
    public String getPhysicalAddress();

    /**
     * Deprecated in favor of {@link #attribute(String)} method.
     * <p>
     * Gets a node attribute. Attributes are assigned to nodes at startup
     * via {@link GridConfiguration#getUserAttributes()} method.
     * <p>
     * The system adds the following attributes automatically:
     * <ul>
     * <li>{@code {@link System#getProperties()}} - All system properties.</li>
     * <li>{@code {@link System#getenv(String)}} - All environment properties.</li>
     * <li>All attributes defined in {@link GridNodeAttributes}</li>
     * </ul>
     * <p>
     * Note that attributes cannot be changed at runtime.
     *
     * @param <T> Attribute Type.
     * @param name Attribute name. <b>Note</b> that attribute names starting with
     *      {@code org.gridgain} are reserved for internal use.
     * @return Attribute value or {@code null}.
     */
    @Deprecated
    public <T> T getAttribute(String name);

    /**
     * Gets a node attribute. Attributes are assigned to nodes at startup
     * via {@link GridConfiguration#getUserAttributes()} method.
     * <p>
     * The system adds the following attributes automatically:
     * <ul>
     * <li>{@code {@link System#getProperties()}} - All system properties.</li>
     * <li>{@code {@link System#getenv(String)}} - All environment properties.</li>
     * <li>All attributes defined in {@link GridNodeAttributes}</li>
     * </ul>
     * <p>
     * Note that attributes cannot be changed at runtime.
     *
     * @param <T> Attribute Type.
     * @param name Attribute name. <b>Note</b> that attribute names starting with
     *      {@code org.gridgain} are reserved for internal use.
     * @return Attribute value or {@code null}.
     */
    public <T> T attribute(String name);

    /**
     * Deprecated in favor of {@link #metrics()} method.
     * <p>
     * Gets metrics snapshot for this node. Note that node metrics are constantly updated
     * and provide up to date information about nodes. For example, you can get
     * an idea about CPU load on remote node via {@link GridNodeMetrics#getCurrentCpuLoad()}
     * method and use it during {@link GridTask#map(List, Object)} or during collision
     * resolution.
     * <p>
     * Node metrics are updated with some delay which is directly related to heartbeat
     * frequency. For example, when used with default
     * {@link GridMulticastDiscoverySpi} the update will happen every {@code 2} seconds.
     *
     * @return Runtime metrics snapshot for this node.
     */
    @Deprecated
    public GridNodeMetrics getMetrics();

    /**
     * Gets metrics snapshot for this node. Note that node metrics are constantly updated
     * and provide up to date information about nodes. For example, you can get
     * an idea about CPU load on remote node via {@link GridNodeMetrics#getCurrentCpuLoad()}
     * method and use it during {@link GridTask#map(List, Object)} or during collision
     * resolution.
     * <p>
     * Node metrics are updated with some delay which is directly related to heartbeat
     * frequency. For example, when used with default
     * {@link GridMulticastDiscoverySpi} the update will happen every {@code 2} seconds.
     *
     * @return Runtime metrics snapshot for this node.
     */
    public GridNodeMetrics metrics();

    /**
     * Deprecated in favor of {@link #attributes()} method.
     * <p>
     * Gets all node attributes. Attributes are assigned to nodes at startup
     * via {@link GridConfiguration#getUserAttributes()} method.
     * <p>
     * The system adds the following attributes automatically:
     * <ul>
     * <li>{@code {@link System#getProperties()}} - All system properties.</li>
     * <li>{@code {@link System#getenv(String)}} - All environment properties.</li>
     * <li>All attributes defined in {@link GridNodeAttributes}</li>
     * </ul>
     * <p>
     * Note that attributes cannot be changed at runtime.
     *
     * @return All node attributes.
     */
    @Deprecated
    public Map<String, Object> getAttributes();

    /**
     * Gets all node attributes. Attributes are assigned to nodes at startup
     * via {@link GridConfiguration#getUserAttributes()} method.
     * <p>
     * The system adds the following attributes automatically:
     * <ul>
     * <li>{@code {@link System#getProperties()}} - All system properties.</li>
     * <li>{@code {@link System#getenv(String)}} - All environment properties.</li>
     * <li>All attributes defined in {@link GridNodeAttributes}</li>
     * </ul>
     * <p>
     * Note that attributes cannot be changed at runtime.
     *
     * @return All node attributes.
     */
    public Map<String, Object> attributes();

    /**
     * Gets collection of IP addresses this node is known by internally on the same LAN.
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
     * Gets collection of IP addresses this node is known by externally outside of ths LAN.
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
     * <p>
     * <b>NOTE</b>: in cases when discovery SPI doesn't support ordering GridGain cannot
     * guarantee that orders on all nodes will be unique or chronologically correct.
     * If such guarantee is required - make sure use discovery SPI that provides ordering.
     *
     * @return Node startup order.
     */
    public long order();
}
