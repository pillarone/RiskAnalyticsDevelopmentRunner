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
 * Grid job event.
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
 * @see GridEventType#EVT_JOB_CANCELLED
 * @see GridEventType#EVT_JOB_FAILED
 * @see GridEventType#EVT_JOB_FAILED_OVER
 * @see GridEventType#EVT_JOB_FINISHED
 * @see GridEventType#EVT_JOB_MAPPED
 * @see GridEventType#EVT_JOB_QUEUED
 * @see GridEventType#EVT_JOB_REJECTED
 * @see GridEventType#EVT_JOB_RESULTED
 * @see GridEventType#EVT_JOB_STARTED
 * @see GridEventType#EVT_JOB_TIMEDOUT
 * @see GridEventType#EVTS_JOB_EXECUTION
 */
public class GridJobEvent extends GridEventAdapter {
    /** */
    private String taskName;

    /** */
    private UUID sesId;

    /** */
    private UUID jobId;

    /** */
    private UUID taskNodeId;

    /** {@inheritDoc} */
    @Override public String shortDisplay() {
        return name() + ": taskName=" + taskName;
    }

    /**
     * No-arg constructor.
     */
    public GridJobEvent() {
        // No-op.
    }

    /**
     * Creates job event with given parameters.
     *
     * @param nodeId Node ID.
     * @param msg Optional message.
     * @param type Event type.
     */
    public GridJobEvent(UUID nodeId, String msg, int type) {
        super(nodeId, msg, type);
    }

    /**
     * Gets name of the task that triggered the event.
     *
     * @return Name of the task that triggered the event.
     */
    public String taskName() {
        return taskName;
    }

    /**
     * Gets task session ID of the task that triggered this event.
     *
     * @return Task session ID of the task that triggered the event.
     */
    public UUID taskSessionId() {
        return sesId;
    }

    /**
     * Gets job ID.
     *
     * @return Job ID.
     */
    public UUID jobId() {
        return jobId;
    }

    /**
     * Sets name of the task that triggered this event.
     *
     * @param taskName Task name to set.
     */
    public void taskName(String taskName) {
        assert taskName != null;

        this.taskName = taskName;
    }

    /**
     * Sets task session ID of the task that triggered this event.
     *
     * @param sesId Task session ID to set.
     */
    public void taskSessionId(UUID sesId) {
        assert sesId != null;

        this.sesId = sesId;
    }

    /**
     * Sets job ID.
     *
     * @param jobId Job ID to set.
     */
    public void jobId(UUID jobId) {
        assert jobId != null;

        this.jobId = jobId;
    }

    /**
     * Get ID of the node where parent task of the job has originated.
     *
     * @return ID of the node where parent task of the job has originated.
     */
    public UUID taskNodeId() {
        return taskNodeId;
    }

    /**
     * Sets ID of the node where parent task of the job has originated.
     *
     * @param taskNodeId ID of the node where parent task of the job has originated.
     */
    public void taskNodeId(UUID taskNodeId) {
        this.taskNodeId = taskNodeId;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobEvent.class, this,
            "nodeId8", U.id8(nodeId()),
            "msg", message(),
            "type", name(),
            "tstamp", timestamp());
    }
}
