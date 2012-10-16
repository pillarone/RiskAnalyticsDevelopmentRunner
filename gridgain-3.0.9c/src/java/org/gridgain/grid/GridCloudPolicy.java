// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

/**
 * Defines pluggable SLA/QoS control for execution of cloud commands.
 * Cloud policy is user defined class whose lifecycle is managed by
 * GridGain distributed runtime, i.e. policies will be activated and
 * deactivated automatically by GridGain.
 * <p>
 * When user issues a cloud command the GridGain implementation will
 * first run this command against all registered cloud policies. If
 * all policies accept this command then it will be passed to cloud
 * SPI for the execution.
 * <p>
 * Note that like cloud strategies the policies are activated only
 * on the cloud coordinator node. In other words, for any given cloud
 * there is only one policy instance that is active in the topology.
 * <p>
 * Note also that policies should be configured on all nodes
 * that can be a cloud coordinator. If node's configuration excludes it
 * from being a coordinator (see {@link GridConfiguration#isDisableCloudCoordinator()})
 * policies can be omitted from the configuration.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCloudPolicy extends GridCloudEnabled {
    /**
     * Gets called by the system before cloud command is actually executed
     * by the corresponding cloud SPI.
     * <p>
     * Only if all registered policies for the given cloud accept provided command
     * it will proceed to be executed by the corresponding cloud SPI.
     *
     * @param cloudId ID of the cloud that is invoking this command.
     * @param cmd Cloud command to check for acceptance.
     * @return {@code true} if accepts, {@code false} otherwise.
     */
    public abstract boolean accept(String cloudId, GridCloudCommand cmd);
}
