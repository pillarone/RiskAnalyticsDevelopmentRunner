// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.cloud;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.cloud.ec2lite.*;
import org.gridgain.grid.spi.cloud.jvm.*;
import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Cloud SPI provides ability to manage cloud providers in a uniform fashion.
 * GridGain supports multiple cloud SPIs, where each particular SPI implementation
 * can represent different cloud provider or different configurations for the
 * same cloud provider.
 * <p>
 * Cloud SPIs get activated only on coordinator nodes. Coordinator node acts as a
 * gateway to all clouds it has SPIs configured for. All interaction with its clouds
 * happens only through coordinator node. If {@link GridConfiguration#isDisableCloudCoordinator()}
 * is set to {@code true} then a node cannot be a cloud coordinator. Cloud SPI gets
 * {@link #deactivate() deactivated} when the coordinator node leaves the topology.
 * Note that deactivation is not
 * guaranteed when coordinator node exits abnormally. Since coordinator node can
 * leave the topology, the SPI can "migrate" from one node to another (i.e. from
 * one coordinator to another) via a sequence of deactivation on one node and activation
 * on another.
 * <p>
 * SPI only becomes enabled when it is {@link #activate() activated}. This is a special behavior for that
 * SPI - all other SPIs in GridGain become enabled just upon creation without special lifecycle.
 * Implementations of this SPI should pay special attention to that fact.
 * <p>
 * Once activated the SPI deals with three main entities:
 * <ul>
 * <li>{@link GridCloudCommand} - object that encapsulates the user command to be performed
 *      on the cloud.
 * <li>{@link GridCloudSpiSnapshot} - the snapshot of the cloud's state. This state get exchanged
 *      between nodes.
 * <li>{@link GridCloudResource} - object that represents an individual resource in the cloud.
 * </ul>
 * GridGain provides the following {@code GridCloudSpi} implementations:
 * <ul>
 * <li>{@link GridJvmCloudSpi}</li>
 * <li>{@link GridEc2LiteCloudSpi}</li>
 * <li>{@code GridRackspaceCloudSpi} in Enterprise Edition only</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCloudSpi extends GridSpi {
    /**
     * Gets cloud ID.
     *
     * @return Cloud ID which this SPI manages.
     */
    public String getCloudId();

    /**
     * Sets or unsets (if {@code null}) cloud listener.
     *
     * @param lsnr Cloud listener to set (or unset if {@code null}).
     */
    public void setListener(@Nullable GridCloudSpiListener lsnr);

    /**
     * Activates SPI.
     *
     * @throws GridSpiException If any exception occurs.
     * @return Initial cloud state.
     */
    public GridCloudSpiSnapshot activate() throws GridSpiException;

    /**
     * Deactivates SPI.
     */
    public void deactivate();

    /**
     * Executes cloud command.
     *
     * @param cmd Command to execute.
     * @param cmdExecId Command execution ID.
     * @throws GridSpiException If any exception occurs.
     */
    public void process(GridCloudCommand cmd, UUID cmdExecId) throws GridSpiException;

    /**
     * Compares two snapshots and return delta (potentially empty but never {@code null}).
     *
     * @param oldSnp Old snapshot.
     * @param newSnp New snapshot.
     * @return Difference between snapshots. This method never returns {@code null} but may
     *      return an empty collection if snapshots are identical.
     */
    public Collection<GridTuple2<GridCloudResource, GridCloudSpiResourceAction>> compare(
        GridCloudSpiSnapshot oldSnp, GridCloudSpiSnapshot newSnp);
}
