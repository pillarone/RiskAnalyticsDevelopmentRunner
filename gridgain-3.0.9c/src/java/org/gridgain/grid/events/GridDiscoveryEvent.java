// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.events;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;

import java.util.*;

/**
 * Grid discovery event.
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
 * @see GridEventType#EVT_NODE_METRICS_UPDATED
 * @see GridEventType#EVT_NODE_FAILED
 * @see GridEventType#EVT_NODE_JOINED
 * @see GridEventType#EVT_NODE_LEFT
 * @see GridEventType#EVTS_DISCOVERY_ALL
 * @see GridEventType#EVTS_DISCOVERY
 */
public class GridDiscoveryEvent extends GridEventAdapter {
    /** */
    private UUID evtNodeId;

    /** */
    private GridNodeShadow shadow;

    /** {@inheritDoc} */
    @Override public String shortDisplay() {
        return name() + ": nodeId8=" + U.id8(evtNodeId);
    }

    /**
     * No-arg constructor.
     */
    public GridDiscoveryEvent() {
        // No-op.
    }

    /**
     * Creates new discovery event with given parameters.
     *
     * @param nodeId Local node ID.
     * @param msg Optional event message.
     * @param type Event type.
     * @param evtNodeId ID of the node that caused this event to be generated.
     */
    public GridDiscoveryEvent(UUID nodeId, String msg, int type, UUID evtNodeId) {
        super(nodeId, msg, type);

        this.evtNodeId = evtNodeId;
    }

    /**
     * Creates new discovery event with given parameters.
     *
     * @param nodeId Local node ID.
     * @param msg Optional event message.
     * @param type Event type.
     */
    public GridDiscoveryEvent(UUID nodeId, String msg, int type) {
        super(nodeId, msg, type);
    }

    /**
     * Sets ID of the node this event is referring to.
     *
     * @param evtNodeId Event node ID. Note that event node ID is different from node ID
     *      available via {@link #nodeId()} method.
     */
    public void eventNodeId(UUID evtNodeId) {
        this.evtNodeId = evtNodeId;
    }

    /**
     * Sets node shadow.
     *
     * @param shadow Node shadow to set.
     */
    public void shadow(GridNodeShadow shadow) {
        this.shadow = shadow;
    }

    /**
     * Gets node shadow.
     *
     * @return Node shadow or {@code null} if one wasn't set.
     */
    public GridNodeShadow shadow() {
        return shadow;
    }

    /**
     * Gets ID of the node that caused this event to be generated. It is potentially different from the node
     * on which this event was recorded. For example, node {@code A} locally recorded the event that a remote node
     * {@code B} joined the topology. In this case this method will return ID of {@code B} and
     * method {@link #nodeId()} will return ID of {@code A}
     *
     * @return Event node ID.
     */
    public UUID eventNodeId() {
        return evtNodeId;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDiscoveryEvent.class, this,
            "nodeId8", U.id8(nodeId()),
            "msg", message(),
            "type", name(),
            "tstamp", timestamp());
    }
}
