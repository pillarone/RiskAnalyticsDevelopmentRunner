// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Defines cloud command. Cloud command is a unit of operation that can be performed
 * on the cloud. Cloud commands are handled by cloud coordinator only and therefore sent from the
 * originating node to the cloud coordinator where they are executed by specific cloud SPI.
 * <p>
 * It is important to note that only <b>specific cloud SPI defines</b> what commands are supported.
 * You need to consult specific cloud SPI documentation to see what specific commands are supported
 * and what parameters, if any, are expected.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCloudCommand extends GridMetadataAware, Comparable<GridCloudCommand> {
    /**
     * Gets command ID.
     * <p>
     * Giving cloud command a proper ID is <i>essential for filtering out duplicate commands</i> sent
     * from the different grid nodes. GridGain internally maintains a topology version for each cloud.
     * Whenever cloud changes (new instance, new instance parameters, etc) its topology version
     * is changing too. When a node responsible for coordinating cloud operations ("cloud coordinator")
     * receives cloud command it first checks if command with the same ID was already sent for execution
     * for the current cloud version. If it was - all further commands with this ID will be ignored until
     * the version of the cloud is changed.
     * <p>
     * Note that it is impossible to avert all logical duplication of cloud controls. Since cloud commands
     * could be issued simultaneously by multiple nodes some of them may arrive after the cloud topology
     * changed as the result of the previously processed command - even with the same ID. In this situation
     * even though remote nodes reacted to the same condition to issue their commands - full duplicate
     * prevention is impossible in general. It is highly advisable that application cloud control logic
     * should be designed in a such a way that it can compensate for the "extra" produced by duplicate command
     * execution.
     * <p>
     * The simplest way to come up with command ID is to <i>name</i> the place in your code that issues
     * the cloud command. Often combination of class name and method name is ideal command ID (unless you
     * have multiple cloud commands in the same method).
     * <p>
     * Note also that you should not confuse this command ID with command execution ID returned from
     * {@code invoke(...)} methods on {@link GridRichCloud} interface.
     * Execution ID will always be unique for each execution (even of the same command). Both command
     * ID and execution ID are available through {@link org.gridgain.grid.events.GridCloudEvent}.
     *
     * @return Command ID.
     */
    public String id();

    /**
     * Gets optional numeric value associated with this command. The meaning of its value depends on
     * the resource type, e.g. number of instances to start or stop, amount of memory to add or remove, etc.
     * Please, consult specific cloud SPI implementation for more details.
     *
     * @return Numeric value associated with this command.
     * @see #action()
     */
    public int number();

    /**
     * Gets optional action string associated with this command. The meaning of its value is usually depends on
     * resource type and specific to a given SPI implementation. You need to consult specific cloud SPI
     * implementation for more details.
     *
     * @return Action string associated with this command.
     * @see #number()
     */
    @Nullable public String action();

    /**
     * Gets collection of resources associated with this cloud command.
     *
     * @return Collection of resources associated with this cloud command or {@code null}
     *      if none defined.
     */
    @Nullable public Collection<GridCloudResource> resources();

    /**
     * Gets user defined parameters for this command. User defined parameters will be handled
     * by the cloud SPI when this command will be executed. Note that the cloud SPI for this command
     * must know how to handle these parameters. Please consult specific cloud SPI for more information.
     *
     * @return User defined parameters associated with this command
     *      or {@code null} if none defined.
     */
    @Nullable public Map<String, String> parameters();
}
