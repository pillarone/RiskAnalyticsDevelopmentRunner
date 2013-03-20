// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.events;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.jsr305.*;

import java.util.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Cloud event.
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
 * @see GridEventType#EVT_CLOUD_CHANGED
 * @see GridEventType#EVT_CLOUD_COMMAND_EXECUTED
 * @see GridEventType#EVT_CLOUD_COMMAND_FAILED
 * @see GridEventType#EVT_CLOUD_COMMAND_REJECTED_BY_POLICY
 * @see GridEventType#EVT_CLOUD_COMMAND_REJECTED_BY_ID
 * @see GridEventType#EVT_CLOUD_RESOURCE_ADDED
 * @see GridEventType#EVT_CLOUD_RESOURCE_CHANGED
 * @see GridEventType#EVT_CLOUD_RESOURCE_REMOVED
 */
public class GridCloudEvent extends GridEventAdapter {
    /** Cloud ID. */
    private String cloudId;

    /** Cloud version. */
    private long ver;

    /** Command execution ID. */
    private UUID cmdExecId;

    /** Cloud resource ID. */
    private String rsrcId;

    /** Cloud resource type. */
    private int rsrcType;

    /** Cloud resource shadow. */
    private GridCloudResourceShadow shadow;

    /** {@inheritDoc} */
    @Override public String shortDisplay() {
        return name() + ": cloudId=" + cloudId;
    }

    /**
     * Creates cloud event.
     *
     * @param nodeId Node ID.
     * @param msg Optional message.
     * @param type Event type.
     * @param cloudId Cloud ID.
     * @param ver Cloud version.
     */
    public GridCloudEvent(UUID nodeId, String msg, @NonNegative int type, String cloudId, long ver) {
        super(nodeId, msg, type);

        assert !F.isEmpty(cloudId);
        assert ver > 0;

        this.cloudId = cloudId;
        this.ver = ver;
    }

    /**
     * Creates cloud event.
     *
     * @param nodeId Node ID.
     * @param msg Optional message.
     * @param type Event type.
     * @param cloudId Cloud ID.
     * @param cmdExecId Command execution ID.
     */
    public GridCloudEvent(UUID nodeId, String msg, @NonNegative int type, String cloudId, UUID cmdExecId) {
        super(nodeId, msg, type);

        assert !F.isEmpty(cloudId);
        assert cmdExecId != null;

        this.cloudId = cloudId;
        this.cmdExecId = cmdExecId;
    }

    /**
     * Creates cloud event.
     *
     * @param nodeId Node ID.
     * @param msg Optional message.
     * @param type Event type.
     * @param cloudId Cloud ID.
     * @param rsrcId Cloud resource ID.
     * @param rsrcType Cloud resource type.
     * @param shadow Cloud resource shadow.
     */
    public GridCloudEvent(UUID nodeId, String msg, @NonNegative int type, String cloudId, String rsrcId, int rsrcType,
        GridCloudResourceShadow shadow) {
        super(nodeId, msg, type);

        assert !F.isEmpty(cloudId);
        assert !F.isEmpty(rsrcId);
        assert rsrcType > 0;
        assert type != EVT_CLOUD_RESOURCE_REMOVED && shadow == null ||
            type == EVT_CLOUD_RESOURCE_REMOVED && shadow != null;

        this.cloudId = cloudId;
        this.rsrcId = rsrcId;
        this.rsrcType = rsrcType;
        this.shadow = shadow;
    }

    /**
     * Get cloud ID.
     *
     * @return Cloud ID.
     */
    public String cloudId() {
        return cloudId;
    }

    /**
     * Gets cloud version.
     *
     * @return Cloud version.
     */
    public long version() {
        return ver;
    }

    /**
     * Gets command execution ID.
     *
     * @return Command execution ID.
     */
    public UUID executionId() {
        return cmdExecId;
    }

    /**
     * Gets cloud resource ID.
     *
     * @return Cloud resource ID.
     */
    public String resourceId() {
        return rsrcId;
    }

    /**
     * Gets cloud resource type.
     *
     * @return Cloud resource type.
     */
    public int resourceType() {
        return rsrcType;
    }

    /**
     * Gets cloud resource shadow.
     *
     * @return Cloud resource shadow.
     */
    public GridCloudResourceShadow shadow() {
        return shadow;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudEvent.class, this,
            "nodeId8", U.id8(nodeId()),
            "msg", message(),
            "type", name(),
            "tstamp", timestamp());
    }
}
