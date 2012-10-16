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
 * Data grid (cache) preloading event. Preload event happens every time there is a change
 * in grid topology, which means that a node has either joined or left the grid.
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
 *          {@link Grid#addLocalEventListener(GridLocalEventListener , int[])} -
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
 * @see GridEventType#EVT_CACHE_PRELOAD_PART_LOADED
 * @see GridEventType#EVT_CACHE_PRELOAD_STARTED
 * @see GridEventType#EVT_CACHE_PRELOAD_STOPPED
 */
public class GridCachePreloadEvent extends GridEventAdapter {
    /** Cache name. */
    private String cacheName;

    /** Partition for the event. */
    private int partition;

    /** Discovery node. */
    private GridNodeShadow discoNode;

    /** Discovery event type. */
    private int discoEvtType;

    /** Discovery event time. */
    private long discoTimestamp;

    /**
     * Constructs cache event.
     *
     * @param cacheName Cache name.
     * @param nodeId Event node ID.
     * @param msg Event message.
     * @param type Event type.
     * @param partition Partition for the event (usually the partition the key belongs to).
     * @param discoNode Node that triggered this preloading event.
     * @param discoEvtType Discovery event type that triggered this preloading event.
     * @param discoTimestamp Timestamp of discovery event that triggered this preloading event.
     */
    public GridCachePreloadEvent(String cacheName, UUID nodeId, String msg, int type, int partition,
        GridNodeShadow discoNode, int discoEvtType, long discoTimestamp) {
        super(nodeId, msg, type);
        this.cacheName = cacheName;
        this.partition = partition;
        this.discoNode = discoNode;
        this.discoEvtType = discoEvtType;
        this.discoTimestamp = discoTimestamp;
    }

    /**
     * Gets cache name.
     *
     * @return Cache name.
     */
    public String cacheName() {
        return cacheName;
    }

    /**
     * Gets partition for the event.
     *
     * @return Partition for the event.
     */
    public int partition() {
        return partition;
    }

    /**
     * Gets shadow of the node that triggered this preloading event.
     *
     * @return Shadow of the node that triggered this preloading event.
     * @see GridDiscoveryEvent#shadow()
     */
    public GridNodeShadow discoveryNode() {
        return discoNode;
    }

    /**
     * Gets type of discovery event that triggered this preloading event.
     *
     * @return Type of discovery event that triggered this preloading event.
     * @see GridDiscoveryEvent#type()
     */
    public int discoveryEventType() {
        return discoEvtType;
    }

    /**
     * Gets name of discovery event that triggered this preloading event.
     *
     * @return Name of discovery event that triggered this preloading event.
     * @see GridDiscoveryEvent#name()
     */
    public String discoveryEventName() {
        return U.gridEventName(discoEvtType);
    }

    /**
     * Gets timestamp of discovery event that caused this preloading event.
     *
     * @return Timestamp of discovery event that caused this preloading event.
     */
    public long discoveryTimestamp() {
        return discoTimestamp;
    }

    /** {@inheritDoc} */
    @Override public String shortDisplay() {
        return name() + ": cache=" + (cacheName == null ? "<default>" : cacheName) + ", cause=" +
            discoveryEventName();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCachePreloadEvent.class, this,
            "discoEvtName", discoveryEventName(),
            "nodeId8", U.id8(nodeId()),
            "msg", message(),
            "type", name(),
            "tstamp", timestamp());
    }
}
