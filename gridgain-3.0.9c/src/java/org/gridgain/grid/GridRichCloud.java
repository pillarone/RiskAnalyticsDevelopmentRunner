// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Defines a "rich" cloud as a cloud-based grid projection.
 * <p>
 * All main grid entities such as grid, projection, cloud and a node instances can be viewed as
 * collection of grid nodes (in case of the grid node this collection consist of only one
 * element). As such they all share the same set of operations that can be performed on a set
 * grid nodes. These operations are defined in {@link GridProjection} interface and called
 * <tt>monadic</tt> as they are equally defined on any arbitrary set of nodes.
 * <h2 class="header">Thread Safety</h2>
 * Implementation of this interface provides necessary thread synchronization.
 * <h2 class="header">Nullable and Monads</h2>
 * Many methods in this interface accepts nullable parameters. Although it may seem counter intuitive
 * for some of them - it is done to promote monadic usage of this interface. Java doesn't support
 * natively concepts like <tt>Option</tt> in Scala and returning, accepting, and properly handling
 * {@code null} values is Java's way to support such monadic invocations.
 * <p>
 * All methods that accept {@code null} values (for monadic purposes) will gracefully handle it by
 * either returning a finished future, or empty collection, {@code null} value, or combination of the
 * above. Most method calls therefore can be chained without an explicit checks for {@code null}s.
 * <p>
 * The downside of this approach that inadvertent errors of passing {@code null} will not result
 * in {@link NullPointerException} and may be harder to catch.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridRichCloud extends GridProjection, GridCloud, Comparable<GridCloud> {
    /**
     * Gets collection of resources from this cloud.
     *
     * @param root Optional root resource. If provided - implementation will recursively
     *      take its adjacent resources. If {@code null} - all resources in the cloud
     *      will be taken.
     * @param p Optional set of resource predicates. Only resources that pass all
     *      predicates will be returned. If none provided (empty or {@code null}) -
     *      all found resources will be returned.
     * @return Collection of resources from this cloud.
     */
    public Collection<GridCloudResource> resources(@Nullable GridCloudResource root,
        @Nullable GridPredicate<? super GridCloudResource>... p);

    /**
     * Creates dynamic projection that consists of nodes recursively found from the
     * given root and satisfying all given predicates.
     *
     * @param root Optional root resource. If provided - implementation will recursively
     *      take its adjacent resources. If {@code null} - all resources in the cloud
     *      will be taken.
     * @param p Optional set of node predicates. Only nodes that pass all
     *      predicates will be returned. If none provided (empty or {@code null}) -
     *      all found nodes will be returned.
     * @return Dynamic projection that consists of nodes recursively found from the 
     *      given root and satisfying all given predicates.
     */
    public GridProjection projectionForResources(@Nullable GridCloudResource root,
        @Nullable GridPredicate<? super GridRichNode>... p);

    /**
     * Invokes given cloud command.
     * <p>
     * Cloud commands provide the facility to change the cloud by performing various operations
     * on the cloud like adding and removing virtual instances, changing the instance parameters,
     * adding block storage devices, etc. You can invoke one or several cloud commands. Each cloud
     * command will travel to the cloud coordinator - the single node that is responsible for
     * communication with a specific cloud - and will be executed there by the cloud SPI.
     * <p>
     * Note that executing commands on the cloud can take significant amount of time and depends
     * heavily on the cloud provider. For example, starting a new instance on Amazon EC2 cloud
     * can take up to 10 minutes (but takes only about a minute on average). Furthermore, not all
     * commands produce visible results or results that can be easily attributed back to a specific
     * cloud command. In some cases, the external modifications to the cloud can be mixed up with
     * modifications caused by cloud command invocation in which case it is impossible to distinguish
     * which results caused by what action. Yet in other cases, the external modifications can cancel
     * out the changes from cloud command.
     * <p>
     * To deal with this inherently non-deterministic nature of cloud operations GridGain relies
     * only on grid events to communicate cloud command invocations (including command execution ID) as well
     * as any changes on the cloud - internally or externally induced. Both cloud command invocation and
     * any detectable changes on the cloud produce grid events that are automatically distributed across
     * the grid and available on each node as local events. User can be subscribe for local events to
     * discern the state change of the cloud.
     *
     * @param cmd Cloud command to invoke.
     * @return Command execution ID.
     * @throws GridException Thrown in case of any errors.
     * @see GridCloudPolicy
     * @see GridCloudStrategy
     * @see Grid#addLocalEventListener(GridLocalEventListener, int...)
     * @see Grid#localEvents(GridPredicate[])
     * @see Grid#waitForEventAsync(org.gridgain.grid.lang.GridPredicate , int...)
     * @see Grid#waitForEvent(long, Runnable, GridPredicate, int...)
     */
    public UUID invoke(GridCloudCommand cmd) throws GridException;

    /**
     * Invokes given cloud commands.
     * <p>
     * Cloud commands provide the facility to change the cloud by performing various operations
     * on the cloud like adding and removing virtual instances, changing the instance parameters,
     * adding block storage devices, etc. You can invoke one or several cloud commands. Each cloud
     * command will travel to the cloud coordinator - the single node that is responsible for
     * communication with a specific cloud - and will be executed there by the cloud SPI.
     * <p>
     * Note that executing commands on the cloud can take significant amount of time and depends
     * heavily on the cloud provider. For example, starting a new instance on Amazon EC2 cloud
     * can take up to 10 minutes (but takes only about a minute on average). Furthermore, not all
     * commands produce visible results or results that can be easily attributed back to a specific
     * cloud command. In some cases, the external modifications to the cloud can be mixed up with
     * modifications caused by cloud command invocation in which case it is impossible to distinguish
     * which results caused by what action. Yet in other cases, the external modifications can cancel
     * out the changes from cloud command.
     * <p>
     * To deal with this inherently non-deterministic nature of cloud operations GridGain relies
     * only on grid events to communicate cloud command invocations (including command execution ID) as well
     * as any changes on the cloud - internally or externally induced. Both cloud command invocation and
     * any detectable changes on the cloud produce grid events that are automatically distributed across
     * the grid and available on each node as local events. User can be subscribe for local events to
     * discern the state change of the cloud.
     *
     * @param cmds Cloud commands to execute. Can be {@code null}.
     * @return Collection of command execution IDs. Returns empty collection if collection of commands
     *      either empty or {@code null}.
     * @throws GridException Thrown in case of any errors.
     * @see GridCloudPolicy
     * @see GridCloudStrategy
     * @see Grid#addLocalEventListener(GridLocalEventListener, int...)
     * @see Grid#localEvents(GridPredicate[])
     * @see Grid#waitForEventAsync(GridPredicate, int...)
     * @see Grid#waitForEvent(long, Runnable, org.gridgain.grid.lang.GridPredicate , int...)
     */
    public Collection<UUID> invoke(@Nullable Collection<? extends GridCloudCommand> cmds) throws GridException;
}
