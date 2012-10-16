package org.gridgain.grid.events;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.*;

/**
 * Grid swap space event.
 * <p>
 * Grid events are used for notification about what happens within the grid. Note that by
 * design GridGain keeps all events generated on the local node locally and it provides
 * APIs for performing a distributed queries across multiple nodes:
 * <ul>
 *      <li>
 *          {@link Grid#remoteEvents(org.gridgain.grid.lang.GridPredicate , long, org.gridgain.grid.lang.GridPredicate[])} -
 *          querying events occurred on the nodes specified, including remote nodes.
 *      </li>
 *      <li>
 *          {@link Grid#remoteEventsAsync(org.gridgain.grid.lang.GridPredicate , long, org.gridgain.grid.lang.GridPredicate[])} -
 *          asynchronously querying events occurred on the nodes specified, including remote nodes.
 *      </li>
 *      <li>
 *          {@link Grid#localEvents(org.gridgain.grid.lang.GridPredicate[])} -
 *          querying only local events stored on this local node.
 *      </li>
 *      <li>
 *          {@link Grid#addLocalEventListener(GridLocalEventListener , int...)} -
 *          listening to local grid events (events from remote nodes not included).
 *      </li>
 * </ul>
 * User can also wait for events using the following two methods:
 * <ul>
 *      <li>{@link Grid#waitForEventAsync(org.gridgain.grid.lang.GridPredicate , int...)}</li>
 *      <li>{@link Grid#waitForEvent(long, Runnable, org.gridgain.grid.lang.GridPredicate , int...)}</li>
 * </ul>
 * <h1 class="header">Events and Performance</h1>
 * Note that by default all events in GridGain are enabled and therefore generated and stored
 * by whatever event storage SPI is configured. GridGain can and often does generate thousands events per seconds
 * under the load and therefore it creates a significant additional load on the system. If these events are
 * not needed by the application this load is unnecessary and leads to significant performance degradation.
 * <p>
 * It is <b>highly recommended</b> to enable only those events that your application logic requires
 * by using either  {@link GridConfiguration#getExcludeEventTypes()} or
 * {@link GridConfiguration#getIncludeEventTypes()} methods in GridGain configuration. Note that certain
 * events are required for GridGain's internal operations and such events will still be generated but not stored by
 * event storage SPI if they are disabled in GridGain configuration.
 * 
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridEventType#EVT_SWAP_SPACE_DATA_READ
 * @see GridEventType#EVT_SWAP_SPACE_DATA_STORED
 * @see GridEventType#EVT_SWAP_SPACE_DATA_REMOVED
 * @see GridEventType#EVT_SWAP_SPACE_CLEARED
 */
public class GridSwapSpaceEvent extends GridEventAdapter {
    /** Swap space name. */
    private String space;

    /** {@inheritDoc} */
    @Override public String shortDisplay() {
        return name() + ": space=" + space;
    }

    /**
     * Creates swap space event.
     *
     * @param nodeId Node ID.
     * @param msg Optional message.
     * @param type Event type.
     * @param space Swap space name.
     */
    public GridSwapSpaceEvent(UUID nodeId, String msg, int type, String space) {
        super(nodeId, msg, type);

        assert space != null;

        this.space = space;
    }

    /**
     * Gets swap space name.
     *
     * @return Swap space name.
     */
    public String space() {
        return space;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridSwapSpaceEvent.class, this,
            "nodeId8", U.id8(nodeId()),
            "msg", message(),
            "type", name(),
            "tstamp", timestamp());
    }
}
