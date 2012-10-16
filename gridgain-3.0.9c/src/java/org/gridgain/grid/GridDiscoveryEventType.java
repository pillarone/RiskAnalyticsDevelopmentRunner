// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.events.*;

/**
 * Deprecated in favor of new unified event management. See the following:
 * <ul>
 * <li>{@link GridEvent}</li>
 * <li>{@link Grid#addLocalEventListener(GridLocalEventListener, int...)}</li>
 * <li>{@link GridDiscoveryEvent}</li>
 * <li>{@link GridEventType}</li>
 * </ul>
 * <p>
 * Event types for grid node discovery events.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Deprecated
public enum GridDiscoveryEventType {
    /**
     * Deprecated in favor of new unified event management. See the following:
     * <ul>
     * <li>{@link GridEvent}</li>
     * <li>{@link Grid#addLocalEventListener(GridLocalEventListener, int...)}</li>
     * <li>{@link GridDiscoveryEvent}</li>
     * <li>{@link GridEventType}</li>
     * </ul>
     * <p>
     * New node has been discovered and joined grid topology.
     * Note that even though a node has been discovered there could be
     * a number of warnings in the log. In certain situations GridGain
     * doesn't prevent a node from joining but prints warning messages into the log.
     */
    @Deprecated
    JOINED,

    /**
     * Deprecated in favor of new unified event management. See the following:
     * <ul>
     * <li>{@link GridEvent}</li>
     * <li>{@link Grid#addLocalEventListener(GridLocalEventListener, int...)}</li>
     * <li>{@link GridDiscoveryEvent}</li>
     * <li>{@link GridEventType}</li>
     * </ul>
     * <p>
     * Node has normally left the grid.
     */
    @Deprecated
    LEFT,

    /**
     * Deprecated in favor of new unified event management. See the following:
     * <ul>
     * <li>{@link GridEvent}</li>
     * <li>{@link Grid#addLocalEventListener(GridLocalEventListener, int...)}</li>
     * <li>{@link GridDiscoveryEvent}</li>
     * <li>{@link GridEventType}</li>
     * </ul>
     * <p>
     * GridGain detected that node has presumably crashed and is considered failed.
     */
    @Deprecated
    FAILED,

    /**
     * Deprecated in favor of new unified event management. See the following:
     * <ul>
     * <li>{@link GridEvent}</li>
     * <li>{@link Grid#addLocalEventListener(GridLocalEventListener, int...)}</li>
     * <li>{@link GridDiscoveryEvent}</li>
     * <li>{@link GridEventType}</li>
     * </ul>
     * <p>
     * Callback for when node's metrics are updated. In most cases this callback
     * is invoked with every heartbeat received from a node (including local node).
     */
    @Deprecated
    METRICS_UPDATED
}