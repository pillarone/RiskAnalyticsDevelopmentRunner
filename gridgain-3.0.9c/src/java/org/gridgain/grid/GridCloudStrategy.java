// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.strategies.*;

/**
 * Defines cloud strategy for automated cloud management.
 * Cloud strategy is user defined class whose lifecycle is managed by
 * GridGain distributed runtime, i.e. strategies will be activated and
 * deactivated automatically by GridGain.
 * <p>
 * Cloud strategy is effectively a simple callback mechanism that is
 * managed by GridGain. Interface has no methods and inherits activation and
 * deactivation semantic.
 * <p>
 * Note that like cloud policies the strategies are activated only
 * on the cloud coordinator node. In other words, for any given cloud
 * there is only one strategy instance that is active in the topology.
 * <p>
 * Note also that policies should be configured on all nodes
 * that can be a cloud coordinator. If node's configuration excludes it
 * from being a coordinator (see {@link GridConfiguration#isDisableCloudCoordinator()})
 * policies can be omitted from the configuration.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridCloudTimeStrategyAdapter
 * @see GridCloudEventStrategyAdapter
 * @see GridCloudCpuStrategyAdapter
 */
public interface GridCloudStrategy extends GridCloudEnabled {
    // No-op.
}
