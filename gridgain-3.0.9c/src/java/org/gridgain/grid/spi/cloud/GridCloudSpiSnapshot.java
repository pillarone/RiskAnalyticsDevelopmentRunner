// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.cloud;

import org.gridgain.grid.*;
import java.util.*;

/**
 * Cloud state snapshot. It defines slice in time of an internal cloud state as seen by the SPI
 * implementation. In other words, it defines a "view" into the cloud that is understood by a
 * particular SPI. Note that each SPI has to provide method
 * {@link GridCloudSpi#compare(GridCloudSpiSnapshot, GridCloudSpiSnapshot)}
 * that allows to compare two snapshots and get the delta between them.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridCloudSpi#compare(GridCloudSpiSnapshot, GridCloudSpiSnapshot)
 */
public interface GridCloudSpiSnapshot {
    /**
     * Gets cloud resources.
     *
     * @return Cloud resources.
     */
    public Collection<GridCloudResource> getResources();

    /**
     * Gets snapshot creation time.
     *
     * @return Gets snapshot creation time.
     */
    public long getTime();

    /**
     * Gets snapshot ID.
     *
     * @return Snapshot ID.
     */
    public UUID getId();

    /**
     * Gets ID of node on which snapshot has been generated.
     *
     * @return Node ID.
     */
    public UUID getCoordinatorNodeId();

    /**
     * Gets cloud parameters.
     *
     * @return Cloud parameters.
     */
    public Map<String, String> getParameters();
}
