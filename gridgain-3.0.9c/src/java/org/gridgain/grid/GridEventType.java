// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.events.*;
import java.util.*;

/**
 * Contains event type constants. The decision to use class and not enumeration
 * dictated by allowing users to create their own events and/or event types which
 * would be impossible with enumerations.
 * <p>
 * Note that this interface defines not only
 * individual type constants but arrays of types as well to be conveniently used with
 * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method:
 * <ul>
 * <li>{@link #EVTS_CHECKPOINT}</li>
 * <li>{@link #EVTS_DEPLOYMENT}</li>
 * <li>{@link #EVTS_DISCOVERY}</li>
 * <li>{@link #EVTS_DISCOVERY_ALL}</li>
 * <li>{@link #EVTS_JOB_EXECUTION}</li>
 * <li>{@link #EVTS_TASK_EXECUTION}</li>
 * <li>{@link #EVTS_CLOUD_ALL}</li>
 * <li>{@link #EVTS_CLOUD_COMMANDS}</li>
 * <li>{@link #EVTS_CACHE}</li>
 * <li>{@link #EVTS_SWAPSPACE}</li>
 * </ul>
 * <p>
 * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
 * internal GridGain events and should not be used by user-defined events.
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
 */
public interface GridEventType {
    /**
     * Built-in event type: checkpoint was saved.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridCheckpointEvent
     */
    public static final int EVT_CHECKPOINT_SAVED = 1;

    /**
     * Built-in event type: checkpoint was loaded.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridCheckpointEvent
     */
    public static final int EVT_CHECKPOINT_LOADED = 2;

    /**
     * Built-in event type: checkpoint was removed. Reasons are:
     * <ul>
     * <li>timeout expired, or
     * <li>or it was manually removed, or
     * <li>it was automatically removed by the task session
     * </ul>
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridCheckpointEvent
     */
    public static final int EVT_CHECKPOINT_REMOVED = 3;

    /**
     * Built-in event type: node joined topology.
     * <br>
     * New node has been discovered and joined grid topology.
     * Note that even though a node has been discovered there could be
     * a number of warnings in the log. In certain situations GridGain
     * doesn't prevent a node from joining but prints warning messages into the log.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDiscoveryEvent
     */
    public static final int EVT_NODE_JOINED = 10;

    /**
     * Built-in event type: node has normally left topology.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDiscoveryEvent
     */
    public static final int EVT_NODE_LEFT = 11;

    /**
     * Built-in event type: node failed.
     * <br>
     * GridGain detected that node has presumably crashed and is considered failed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDiscoveryEvent
     */
    public static final int EVT_NODE_FAILED = 12;

    /**
     * Built-in event type: node metrics updated.
     * <br>
     * Generated when node's metrics are updated. In most cases this callback
     * is invoked with every heartbeat received from a node (including local node).
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDiscoveryEvent
     */
    public static final int EVT_NODE_METRICS_UPDATED = 13;

    /**
     * Built-in event type: local node disconnected.
     * <br>
     * Generated when node is disconnected from the grid topology due to some reason.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDiscoveryEvent
     */
    public static final int EVT_NODE_DISCONNECTED = 14;

    /**
     * Built-in event type: local node reconnected.
     * <br>
     * Generated when node reconnects to grid topology after being disconnected from.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDiscoveryEvent
     */
    public static final int EVT_NODE_RECONNECTED = 15;

    /**
     * Built-in event type: task started.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridTaskEvent
     */
    public static final int EVT_TASK_STARTED = 20;

    /**
     * Built-in event type: task started.
     * <br>
     * Task got finished. This event is triggered every time
     * a task finished without exception.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridTaskEvent
     */
    public static final int EVT_TASK_FINISHED = 21;

    /**
     * Built-in event type: task failed.
     * <br>
     * Task failed. This event is triggered every time a task finished with an exception.
     * Note that prior to this event, there could be other events recorded specific
     * to the failure.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridTaskEvent
     */
    public static final int EVT_TASK_FAILED = 22;

    /**
     * Built-in event type: task timed out.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridTaskEvent
     */
    public static final int EVT_TASK_TIMEDOUT = 23;

    /**
     * Built-in event type: task session attribute set.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridTaskEvent
     */
    public static final int EVT_TASK_SESSION_ATTR_SET = 24;

    /**
     * Built-in event type: task reduced.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     */
    public static final int EVT_TASK_REDUCED = 25;

    /**
     * Built-in event type: non-task class deployed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDeploymentEvent
     */
    public static final int EVT_CLASS_DEPLOYED = 30;

    /**
     * Built-in event type: non-task class undeployed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDeploymentEvent
     */
    public static final int EVT_CLASS_UNDEPLOYED = 31;

    /**
     * Built-in event type: non-task class deployment failed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDeploymentEvent
     */
    public static final int EVT_CLASS_DEPLOY_FAILED = 32;

    /**
     * Built-in event type: task deployed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDeploymentEvent
     */
    public static final int EVT_TASK_DEPLOYED = 33;

    /**
     * Built-in event type: task undeployed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDeploymentEvent
     */
    public static final int EVT_TASK_UNDEPLOYED = 34;

    /**
     * Built-in event type: task deployment failed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridDeploymentEvent
     */
    public static final int EVT_TASK_DEPLOY_FAILED = 35;

    /**
     * Built-in event type: grid job was mapped in
     * {@link GridTask#map(List , Object)} method.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridJobEvent
     */
    public static final int EVT_JOB_MAPPED = 40;

    /**
     * Built-in event type: grid job result was received by
     * {@link GridTask#result(GridJobResult , List)} method.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridJobEvent
     */
    public static final int EVT_JOB_RESULTED = 41;

    /**
     * Built-in event type: grid job failed over.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridJobEvent
     */
    public static final int EVT_JOB_FAILED_OVER = 43;

    /**
     * Built-in event type: grid job started.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridJobEvent
     */
    public static final int EVT_JOB_STARTED = 44;

    /**
     * Built-in event type: grid job finished.
     * <br>
     * Job has successfully completed and produced a result which from the user perspective
     * can still be either negative or positive.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridJobEvent
     */
    public static final int EVT_JOB_FINISHED = 45;

    /**
     * Built-in event type: grid job timed out.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridJobEvent
     */
    public static final int EVT_JOB_TIMEDOUT = 46;

    /**
     * Built-in event type: grid job rejected during collision resolution.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridJobEvent
     */
    public static final int EVT_JOB_REJECTED = 47;

    /**
     * Built-in event type: grid job failed.
     * <br>
     * Job has failed. This means that there was some error event during job execution
     * and job did not produce a result.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridJobEvent
     */
    public static final int EVT_JOB_FAILED = 48;

    /**
     * Built-in event type: grid job queued.
     * <br>
     * Job arrived for execution and has been queued (added to passive queue during
     * collision resolution).
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridJobEvent
     */
    public static final int EVT_JOB_QUEUED = 49;

    /**
     * Built-in event type: grid job cancelled.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridJobEvent
     */
    public static final int EVT_JOB_CANCELLED = 50;

    /**
      * Built-in event type: entry created.
      * <p>
      * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
      * internal GridGain events and should not be used by user-defined events.
      */
     public static final int EVT_CACHE_ENTRY_CREATED = 60;

     /**
      * Built-in event type: entry destroyed.
      * <p>
      * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
      * internal GridGain events and should not be used by user-defined events.
      */
     public static final int EVT_CACHE_ENTRY_DESTROYED = 61;

     /**
      * Built-in event type: object put.
      * <p>
      * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
      * internal GridGain events and should not be used by user-defined events.
      */
     public static final int EVT_CACHE_OBJECT_PUT = 62;

     /**
      * Built-in event type: object read.
      * <p>
      * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
      * internal GridGain events and should not be used by user-defined events.
      */
     public static final int EVT_CACHE_OBJECT_READ = 63;

     /**
      * Built-in event type: task removed.
      * <p>
      * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
      * internal GridGain events and should not be used by user-defined events.
      */
     public static final int EVT_CACHE_OBJECT_REMOVED = 64;

     /**
      * Built-in event type: object locked.
      * <p>
      * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
      * internal GridGain events and should not be used by user-defined events.
      */
     public static final int EVT_CACHE_OBJECT_LOCKED = 65;

     /**
      * Built-in event type: object unlocked.
      * <p>
      * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
      * internal GridGain events and should not be used by user-defined events.
      */
     public static final int EVT_CACHE_OBJECT_UNLOCKED = 66;

    /**
     * Built-in event type: cache object swapped from swap storage.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     */
    public static final int EVT_CACHE_OBJECT_SWAPPED = 67;

    /**
     * Built-in event type: cache object unswapped from swap storage.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     */
    public static final int EVT_CACHE_OBJECT_UNSWAPPED = 68;

    /**
     * Built-in event type: cache object was expired when reading it.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     */
    public static final int EVT_CACHE_OBJECT_EXPIRED = 69;

    /**
     * Built-in event type: swap space data read.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridSwapSpaceEvent
     */
    public static final int EVT_SWAP_SPACE_DATA_READ = 70;

    /**
     * Built-in event type: swap space data stored.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridSwapSpaceEvent
     */
    public static final int EVT_SWAP_SPACE_DATA_STORED = 71;

    /**
     * Built-in event type: swap space data removed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridSwapSpaceEvent
     */
    public static final int EVT_SWAP_SPACE_DATA_REMOVED = 72;

    /**
     * Built-in event type: swap space cleared.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridSwapSpaceEvent
     */
    public static final int EVT_SWAP_SPACE_CLEARED = 73;

    /**
     * Built-in event type: cache preload started.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridSwapSpaceEvent
     */
    public static final int EVT_CACHE_PRELOAD_STARTED = 80;

    /**
     * Built-in event type: cache preload stopped.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridSwapSpaceEvent
     */
    public static final int EVT_CACHE_PRELOAD_STOPPED = 81;

    /**
     * Built-in event type: cache partition loaded.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridSwapSpaceEvent
     */
    public static final int EVT_CACHE_PRELOAD_PART_LOADED = 82;

    /**
     * Built-in event type: cloud changed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridCloudEvent
     */
    public static final int EVT_CLOUD_CHANGED = 100;

    /**
     * Built-in event type: cloud command executed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridCloudEvent
     */
    public static final int EVT_CLOUD_COMMAND_EXECUTED = 101;

    /**
     * Built-in event type: cloud command rejected by policy.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridCloudEvent
     */
    public static final int EVT_CLOUD_COMMAND_REJECTED_BY_POLICY = 102;

    /**
     * Built-in event type: cloud command rejected by version.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridCloudEvent
     */
    public static final int EVT_CLOUD_COMMAND_REJECTED_BY_ID = 103;

    /**
     * Built-in event type: cloud command failed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridCloudEvent
     */
    public static final int EVT_CLOUD_COMMAND_FAILED = 104;

    /**
     * Built-in event type: cloud resource added.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridCloudEvent
     */
    public static final int EVT_CLOUD_RESOURCE_ADDED = 105;

    /**
     * Built-in event type: cloud resource changed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridCloudEvent
     */
    public static final int EVT_CLOUD_RESOURCE_CHANGED = 106;

    /**
     * Built-in event type: cloud resource removed.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal GridGain events and should not be used by user-defined events.
     *
     * @see GridCloudEvent
     */
    public static final int EVT_CLOUD_RESOURCE_REMOVED = 107;

    /**
     * All checkpoint events. This array can be directly passed into
     * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method to
     * subscribe to all checkpoint events.
     *
     * @see GridCheckpointEvent
     */
    public static final int[] EVTS_CHECKPOINT = {
        EVT_CHECKPOINT_SAVED,
        EVT_CHECKPOINT_LOADED,
        EVT_CHECKPOINT_REMOVED
    };

    /**
     * All deployment events. This array can be directly passed into
     * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method to
     * subscribe to all deployment events.
     *
     * @see GridDeploymentEvent
     */
    public static final int[] EVTS_DEPLOYMENT = {
        EVT_CLASS_DEPLOYED,
        EVT_CLASS_UNDEPLOYED,
        EVT_CLASS_DEPLOY_FAILED,
        EVT_TASK_DEPLOYED,
        EVT_TASK_UNDEPLOYED,
        EVT_TASK_DEPLOY_FAILED
    };

    /**
     * All discovery events <b>except</b> for {@link #EVT_NODE_METRICS_UPDATED}. Subscription to
     * {@link #EVT_NODE_METRICS_UPDATED} can generate massive amount of event processing in most cases
     * is not necessary. If this event is indeed required you can subscribe to it individually or use
     * {@link #EVTS_DISCOVERY_ALL} array.
     * <p>
     * This array can be directly passed into
     * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method to
     * subscribe to all discovery events <b>except</b> for {@link #EVT_NODE_METRICS_UPDATED}.
     *
     * @see GridDiscoveryEvent
     */
    public static final int[] EVTS_DISCOVERY = {
        EVT_NODE_JOINED,
        EVT_NODE_LEFT,
        EVT_NODE_FAILED,
        EVT_NODE_DISCONNECTED,
        EVT_NODE_RECONNECTED
    };

    /**
     * All discovery events. This array can be directly passed into
     * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method to
     * subscribe to all discovery events.
     *
     * @see GridDiscoveryEvent
     */
    public static final int[] EVTS_DISCOVERY_ALL = {
        EVT_NODE_JOINED,
        EVT_NODE_LEFT,
        EVT_NODE_FAILED,
        EVT_NODE_METRICS_UPDATED,
        EVT_NODE_DISCONNECTED,
        EVT_NODE_RECONNECTED
    };

    /**
     * All grid job execution events. This array can be directly passed into
     * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method to
     * subscribe to all grid job execution events.
     *
     * @see GridJobEvent
     */
    public static final int[] EVTS_JOB_EXECUTION = {
        EVT_JOB_MAPPED,
        EVT_JOB_RESULTED,
        EVT_JOB_FAILED_OVER,
        EVT_JOB_STARTED,
        EVT_JOB_FINISHED,
        EVT_JOB_TIMEDOUT,
        EVT_JOB_REJECTED,
        EVT_JOB_FAILED,
        EVT_JOB_QUEUED,
        EVT_JOB_CANCELLED
    };

    /**
     * All grid task execution events. This array can be directly passed into
     * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method to
     * subscribe to all grid task execution events.
     *
     * @see GridTaskEvent
     */
    public static final int[] EVTS_TASK_EXECUTION = {
        EVT_TASK_STARTED,
        EVT_TASK_FINISHED,
        EVT_TASK_FAILED,
        EVT_TASK_TIMEDOUT,
        EVT_TASK_SESSION_ATTR_SET,
        EVT_TASK_REDUCED
    };

    /**
     * All cache events. This array can be directly passed into
     * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method to
     * subscribe to all cache events.
     */
    public static final int[] EVTS_CACHE = {
        EVT_CACHE_ENTRY_CREATED,
        EVT_CACHE_ENTRY_DESTROYED,
        EVT_CACHE_OBJECT_PUT,
        EVT_CACHE_OBJECT_READ,
        EVT_CACHE_OBJECT_REMOVED,
        EVT_CACHE_OBJECT_LOCKED,
        EVT_CACHE_OBJECT_UNLOCKED,
        EVT_CACHE_OBJECT_SWAPPED,
        EVT_CACHE_OBJECT_UNSWAPPED,
        EVT_CACHE_OBJECT_EXPIRED
    };

    /**
     * All cache preload events. This array can be directly passed into
     * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method to
     * subscribe to all cache preload events.
     */
    public static final int[] EVTS_CACHE_PRELOAD = {
        EVT_CACHE_PRELOAD_STARTED,
        EVT_CACHE_PRELOAD_STOPPED,
        EVT_CACHE_PRELOAD_PART_LOADED
    };

    /**
     * All cloud events. This array can be directly passed into
     * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method to
     * subscribe to all cloud events.
     *
     * @see GridCloudEvent
     */
    public static final int[] EVTS_CLOUD_ALL = {
        EVT_CLOUD_CHANGED,
        EVT_CLOUD_COMMAND_EXECUTED,
        EVT_CLOUD_COMMAND_REJECTED_BY_POLICY,
        EVT_CLOUD_COMMAND_REJECTED_BY_ID,
        EVT_CLOUD_COMMAND_FAILED,
        EVT_CLOUD_RESOURCE_ADDED,
        EVT_CLOUD_RESOURCE_CHANGED,
        EVT_CLOUD_RESOURCE_REMOVED
    };

    /**
     * Cloud command events. This array can be directly passed into
     * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method to
     * subscribe to all cloud events.
     *
     * @see GridCloudEvent
     */
    public static final int[] EVTS_CLOUD_COMMANDS = {
        EVT_CLOUD_COMMAND_EXECUTED,
        EVT_CLOUD_COMMAND_REJECTED_BY_POLICY,
        EVT_CLOUD_COMMAND_REJECTED_BY_ID,
        EVT_CLOUD_COMMAND_FAILED,
    };

    /**
     * All swap space events. This array can be directly passed into
     * {@link Grid#addLocalEventListener(GridLocalEventListener, int...)} method to
     * subscribe to all cloud events.
     *
     * @see GridCloudEvent
     */
    public static final int[] EVTS_SWAPSPACE = {
        EVT_SWAP_SPACE_CLEARED,
        EVT_SWAP_SPACE_DATA_REMOVED,
        EVT_SWAP_SPACE_DATA_READ,
        EVT_SWAP_SPACE_DATA_STORED
    };

    /**
     * All GridGain events (<b>including</b> metric update event).
     */
    public static final int[] EVTS_ALL = {
        // Swap events.
        EVT_SWAP_SPACE_CLEARED,
        EVT_SWAP_SPACE_DATA_REMOVED,
        EVT_SWAP_SPACE_DATA_READ,
        EVT_SWAP_SPACE_DATA_STORED,

        // Cloud events.
        EVT_CLOUD_CHANGED,
        EVT_CLOUD_COMMAND_EXECUTED,
        EVT_CLOUD_COMMAND_REJECTED_BY_POLICY,
        EVT_CLOUD_COMMAND_REJECTED_BY_ID,
        EVT_CLOUD_COMMAND_FAILED,
        EVT_CLOUD_RESOURCE_ADDED,
        EVT_CLOUD_RESOURCE_CHANGED,
        EVT_CLOUD_RESOURCE_REMOVED,

        // Cache events.
        EVT_CACHE_ENTRY_CREATED,
        EVT_CACHE_ENTRY_DESTROYED,
        EVT_CACHE_OBJECT_PUT,
        EVT_CACHE_OBJECT_READ,
        EVT_CACHE_OBJECT_REMOVED,
        EVT_CACHE_OBJECT_LOCKED,
        EVT_CACHE_OBJECT_UNLOCKED,
        EVT_CACHE_OBJECT_SWAPPED,
        EVT_CACHE_OBJECT_UNSWAPPED,
        EVT_CACHE_OBJECT_EXPIRED,

        // Preload events.
        EVT_CACHE_PRELOAD_STARTED,
        EVT_CACHE_PRELOAD_STOPPED,
        EVT_CACHE_PRELOAD_PART_LOADED,

        // Job execution events.
        EVT_JOB_MAPPED,
        EVT_JOB_RESULTED,
        EVT_JOB_FAILED_OVER,
        EVT_JOB_STARTED,
        EVT_JOB_FINISHED,
        EVT_JOB_TIMEDOUT,
        EVT_JOB_REJECTED,
        EVT_JOB_FAILED,
        EVT_JOB_QUEUED,
        EVT_JOB_CANCELLED,

        // Task execution events.
        EVT_TASK_STARTED,
        EVT_TASK_FINISHED,
        EVT_TASK_FAILED,
        EVT_TASK_TIMEDOUT,
        EVT_TASK_SESSION_ATTR_SET,
        EVT_TASK_REDUCED,

        // Discovery events.
        EVT_NODE_JOINED,
        EVT_NODE_LEFT,
        EVT_NODE_FAILED,
        EVT_NODE_METRICS_UPDATED,
        EVT_NODE_DISCONNECTED,
        EVT_NODE_RECONNECTED,

        // Checkpoint events.
        EVT_CHECKPOINT_SAVED,
        EVT_CHECKPOINT_LOADED,
        EVT_CHECKPOINT_REMOVED,

        // Deployment events.
        EVT_CLASS_DEPLOYED,
        EVT_CLASS_UNDEPLOYED,
        EVT_CLASS_DEPLOY_FAILED,
        EVT_TASK_DEPLOYED,
        EVT_TASK_UNDEPLOYED,
        EVT_TASK_DEPLOY_FAILED
    };

    /**
     * All GridGain events (<b>excluding</b> metric update event).
     */
    public static final int[] EVTS_ALL_MINUS_METRIC_UPDATE = {
        // Swap events.
        EVT_SWAP_SPACE_CLEARED,
        EVT_SWAP_SPACE_DATA_REMOVED,
        EVT_SWAP_SPACE_DATA_READ,
        EVT_SWAP_SPACE_DATA_STORED,

        // Cloud events.
        EVT_CLOUD_CHANGED,
        EVT_CLOUD_COMMAND_EXECUTED,
        EVT_CLOUD_COMMAND_REJECTED_BY_POLICY,
        EVT_CLOUD_COMMAND_REJECTED_BY_ID,
        EVT_CLOUD_COMMAND_FAILED,
        EVT_CLOUD_RESOURCE_ADDED,
        EVT_CLOUD_RESOURCE_CHANGED,
        EVT_CLOUD_RESOURCE_REMOVED,

        // Cache events.
        EVT_CACHE_ENTRY_CREATED,
        EVT_CACHE_ENTRY_DESTROYED,
        EVT_CACHE_OBJECT_PUT,
        EVT_CACHE_OBJECT_READ,
        EVT_CACHE_OBJECT_REMOVED,
        EVT_CACHE_OBJECT_LOCKED,
        EVT_CACHE_OBJECT_UNLOCKED,
        EVT_CACHE_OBJECT_SWAPPED,
        EVT_CACHE_OBJECT_UNSWAPPED,
        EVT_CACHE_OBJECT_EXPIRED,

        // Preload events.
        EVT_CACHE_PRELOAD_STARTED,
        EVT_CACHE_PRELOAD_STOPPED,
        EVT_CACHE_PRELOAD_PART_LOADED,

        // Job execution events.
        EVT_JOB_MAPPED,
        EVT_JOB_RESULTED,
        EVT_JOB_FAILED_OVER,
        EVT_JOB_STARTED,
        EVT_JOB_FINISHED,
        EVT_JOB_TIMEDOUT,
        EVT_JOB_REJECTED,
        EVT_JOB_FAILED,
        EVT_JOB_QUEUED,
        EVT_JOB_CANCELLED,

        // Task execution events.
        EVT_TASK_STARTED,
        EVT_TASK_FINISHED,
        EVT_TASK_FAILED,
        EVT_TASK_TIMEDOUT,
        EVT_TASK_SESSION_ATTR_SET,
        EVT_TASK_REDUCED,

        // Discovery events.
        EVT_NODE_JOINED,
        EVT_NODE_LEFT,
        EVT_NODE_FAILED,
        EVT_NODE_DISCONNECTED,
        EVT_NODE_RECONNECTED,

        // Checkpoint events.
        EVT_CHECKPOINT_SAVED,
        EVT_CHECKPOINT_LOADED,
        EVT_CHECKPOINT_REMOVED,

        // Deployment events.
        EVT_CLASS_DEPLOYED,
        EVT_CLASS_UNDEPLOYED,
        EVT_CLASS_DEPLOY_FAILED,
        EVT_TASK_DEPLOYED,
        EVT_TASK_UNDEPLOYED,
        EVT_TASK_DEPLOY_FAILED
    };
}
