// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.eventstorage;

import org.apache.commons.lang.*;
import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.kernal.managers.communication.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.eventstorage.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.managers.communication.GridIoPolicy.*;

/**
 * Grid event storage SPI manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridEventStorageManager extends GridManagerAdapter<GridEventStorageSpi> {
    /** Internally-used events. */
    private static final int[] INT_EVTS;

    /** Events that are never passed into SPI, i.e. hidden from system. */
    private static final int[] HIDDEN_EVTS;

    /**
     * Initialize internally used and hidden events.
     * <p>
     * Internal events are always "recordable" for notification
     * purposes (regardless of whether they were enabled or disabled).
     * But won't be sent down to SPI level if user specifically excluded them.
     * <p>
     * Hidden events are NEVER sent to SPI level. They serve purpose of local
     * notification for the local node.
     */
    static {
        INT_EVTS = new int[6];

        INT_EVTS[0] = EVT_NODE_FAILED;
        INT_EVTS[1] = EVT_NODE_LEFT;
        INT_EVTS[2] = EVT_NODE_JOINED;
        INT_EVTS[3] = EVT_NODE_METRICS_UPDATED;
        INT_EVTS[4] = EVT_NODE_DISCONNECTED;
        INT_EVTS[5] = EVT_NODE_RECONNECTED;

        Arrays.sort(INT_EVTS);

        HIDDEN_EVTS = new int[6];

        HIDDEN_EVTS[0] = EVT_NODE_METRICS_UPDATED;
    }

    /** */
    private final ConcurrentMap<Integer, Set<GridLocalEventListener>> lsnrs =
        new ConcurrentHashMap<Integer, Set<GridLocalEventListener>>();

    /** */
    private RequestListener msgLsnr;

    /** Busy lock to control activity of threads. */
    private ReadWriteLock busyLock = new ReentrantReadWriteLock();

    /** Events of these types should be recorded. */
    private int[] inclEvtTypes;

    /** Events of these types should not be recorded. */
    private int[] exclEvtTypes;

    /**
     * Constructs manager.
     *
     * @param ctx Kernal context.
     */
    public GridEventStorageManager(GridKernalContext ctx) {
        super(GridEventStorageSpi.class, ctx, ctx.config().getEventStorageSpi());
    }

    /**
     *
     */
    @SuppressWarnings("deprecation")
    private class RequestListener implements GridMessageListener {
        @SuppressWarnings("deprecation")
        @Override public void onMessage(UUID nodeId, Object msg) {
            assert nodeId != null;
            assert msg != null;

            if (!enterBusy())
                return;

            try {
                if (!(msg instanceof GridEventStorageMessage)) {
                    U.warn(log, "Received unknown message: " + msg);

                    return;
                }

                GridEventStorageMessage req = (GridEventStorageMessage)msg;

                GridNode node = ctx.discovery().node(nodeId);

                if (node == null) {
                    U.warn(log, "Failed to resolve sender node that does not exist: " + nodeId);

                    return;
                }

                if (log.isDebugEnabled())
                    log.debug("Received event query request: " + req);

                Throwable ex = null;

                GridPredicate<GridEvent> filter = null;

                Collection<GridEvent> evts;

                try {
                    GridDeployment dep = ctx.deploy().getGlobalDeployment(
                        req.deploymentMode(),
                        req.filterClassName(),
                        req.filterClassName(),
                        req.sequenceNumber(),
                        req.userVersion(),
                        nodeId,
                        req.classLoaderId(),
                        req.loaderParticipants(),
                        null);

                    if (dep == null)
                        throw new GridException("Failed to obtain deployment for event filter " +
                            "(is peer class loading turned on?): " + req);

                    filter = U.unmarshal(ctx.config().getMarshaller(), req.filter(), dep.classLoader());

                    // Resource injection.
                    ctx.resource().inject(dep, dep.deployedClass(req.filterClassName()), filter);

                    // Get local events.
                    evts = localEvents(filter);
                }
                catch (GridException e) {
                    U.error(log, "Failed to query events [nodeId=" + nodeId + ", filter=" + filter + ']', e);

                    evts = Collections.emptyList();

                    ex = e;
                }
                catch (Throwable e) {
                    U.error(log, "Failed to query events due to user exception [nodeId=" + nodeId +
                        ", filter=" + filter + ']', e);

                    evts = Collections.emptyList();

                    ex = e;
                }

                // Response message.
                Serializable res = new GridEventStorageMessage(evts, ex);

                try {
                    if (log.isDebugEnabled())
                        log.debug("Sending event query response to node [nodeId=" + nodeId + "res=" + res + ']');

                    ctx.io().send(node, req.responseTopic(), res, PUBLIC_POOL);
                }
                catch (GridException e) {
                    U.error(log, "Failed to send event query response to node [node=" + nodeId + ", res=" +
                        res + ']', e);
                }
            }
            finally {
                leaveBusy();
            }
        }
    }

    /**
     * Enters busy state in which manager cannot be stopped.
     *
     * @return {@code true} if entered to busy state.
     */
    private boolean enterBusy() {
        return busyLock.readLock().tryLock();
    }

    /**
     * Leaves busy state.
     */
    private void leaveBusy() {
        busyLock.readLock().unlock();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    @Override public void onKernalStop() {
        // Acquire write lock so that any new thread could not be started.
        busyLock.writeLock().lock();

        GridIoManager io = ctx.io();

        if (io != null) {
            io.removeMessageListener(TOPIC_EVENT.name(), msgLsnr);
            io.removeMessageListener(TOPIC_EVENT);
        }

        msgLsnr = null;

        lsnrs.clear();
    }

    /** {@inheritDoc} */
    @Override public void stop() throws GridException {
        stopSpi();

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        inclEvtTypes = ctx.config().getIncludeEventTypes();
        exclEvtTypes = ctx.config().getExcludeEventTypes();

        if (!ArrayUtils.isEmpty(inclEvtTypes) && !ArrayUtils.isEmpty(exclEvtTypes))
            throw new GridException("Both 'include' event types and 'exclude' event types cannot be provided " +
                "in configuration.");

        startSpi();

        msgLsnr = new RequestListener();

        ctx.io().addMessageListener(TOPIC_EVENT, msgLsnr);

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /**
     * Records event if it's recordable.
     *
     * @param evt Event to record.
     */
    public void record(GridEvent evt) {
        assert evt != null;

        if (!enterBusy())
            return;

        try {
            int type = evt.type();

            if (isUserRecordable(type) && !isHiddenEvent(type)) {
                try {
                    getSpi().record(evt);
                }
                catch (GridSpiException e) {
                    U.error(log, "Failed to record event: " + evt, e);
                }
            }

            if (isRecordable(type))
                notifyListeners(evt);
        }
        finally {
            leaveBusy();
        }
    }

    /**
     *
     * @param type Event type.
     * @return Whether or not this is an internal event.
     */
    private boolean isInternalEvent(int type) {
        return ArrayUtils.contains(INT_EVTS, type);
    }

    /**
     * Checks if the event type is user-recordable.
     *
     * @param type Event type to check.
     * @return {@code true} if passed event should be recorded, {@code false} - otherwise.
     */
    private boolean isUserRecordable(int type) {
        return inclEvtTypes != null ? ArrayUtils.contains(inclEvtTypes, type) :
            exclEvtTypes == null || !ArrayUtils.contains(exclEvtTypes, type);
    }

    /**
     * Checks whether or not this event is a hidden system event.
     *
     * @param type Event type to check.
     * @return {@code true} if this is a system hidden event.
     */
    private boolean isHiddenEvent(int type) {
        return ArrayUtils.contains(HIDDEN_EVTS, type);
    }

    /**
     * Checks whether this event type should be recorded. Note that internal event types are
     * always recordable for notification purposes but may not be sent down to SPI level for
     * storage and subsequent querying.
     *
     * @param type Event type to check.
     * @return Whether or not this event type should be recorded.
     */
    public boolean isRecordable(int type) {
        return isInternalEvent(type) || isUserRecordable(type);
    }

    /**
     * Adds local event listener. Note that this method specifically disallow an empty
     * array of event type to prevent accidental subscription for all system event that
     * may lead to a drastic performance decrease.
     *
     * @param lsnr Listener to add.
     * @param types Event types to subscribe listener for.
     */
    public void addLocalEventListener(GridLocalEventListener lsnr, int[] types) {
        assert lsnr != null;
        assert types != null;
        assert types.length > 0;

        if (enterBusy())
            try {
                for (int t : types)
                    getOrCreate(t).add(lsnr);
            }
            finally {
                leaveBusy();
            }
    }

    public void addLocalEventListener(GridLocalEventListener lsnr, int type, @Nullable int... types) {
        assert lsnr != null;
        assert types != null;

        if (enterBusy())
            try {
                getOrCreate(type).add(lsnr);

                if (types != null)
                    for (int t : types)
                        getOrCreate(t).add(lsnr);
            }
            finally {
                leaveBusy();
            }
    }

    /**
     * @param type Event type.
     * @return Listeners for given event type.
     */
    private Collection<GridLocalEventListener> getOrCreate(Integer type) {
        Set<GridLocalEventListener> set = lsnrs.get(type);

        if (set == null) {
            set = new GridConcurrentHashSet<GridLocalEventListener>();

            Set<GridLocalEventListener> prev = lsnrs.putIfAbsent(type, set);

            if (prev != null)
                set = prev;
        }

        assert set != null;

        return set;
    }

    /**
     * Removes listener for specified events, if any. If no event types provided - it
     * remove the listener for all its registered events.
     *
     * @param lsnr Listener.
     * @param types Event types.
     * @return Returns {@code true} if removed.
     */
    public boolean removeLocalEventListener(GridLocalEventListener lsnr, @Nullable int... types) {
        assert lsnr != null;

        boolean found = false;

        if (F.isEmpty(types)) {
            for (Set<GridLocalEventListener> set : lsnrs.values())
                if (set.remove(lsnr))
                    found = true;
        }
        else {
            assert types != null;

            for (int type : types) {
                Set<GridLocalEventListener> set = lsnrs.get(type);

                if (set != null && set.remove(lsnr))
                    found = true;
            }
        }

        return found;
    }

    /**
     *
     * @param p Optional predicate.
     * @param types Event types to wait for.
     * @return Event future.
     */
    public GridFuture<GridEvent> waitForEvent(@Nullable final GridPredicate<? super GridEvent> p, int... types) {
        final GridFutureAdapter<GridEvent> fut = new GridFutureAdapter<GridEvent>(ctx);

        addLocalEventListener(new GridLocalEventListener() {
            @Override public void onEvent(GridEvent evt) {
                if (p != null && p.apply(evt) || p == null) {
                    fut.onDone(evt);

                    removeLocalEventListener(this);
                }
            }
        }, types);

        return fut;
    }

    /**
     *
     * @param timeout Timeout.
     * @param c Optional continuation.
     * @param p Optional predicate.
     * @param types Event types to wait for.
     * @return Event.
     * @throws GridException Thrown in case of any errors.
     */
    public GridEvent waitForEvent(long timeout, @Nullable Runnable c,
        @Nullable final GridPredicate<? super GridEvent> p, int... types) throws GridException {
        assert timeout >= 0;

        final GridFutureAdapter<GridEvent> fut = new GridFutureAdapter<GridEvent>(ctx);

        addLocalEventListener(new GridLocalEventListener() {
            @Override public void onEvent(GridEvent evt) {
                if (p != null && p.apply(evt) || p == null) {
                    fut.onDone(evt);

                    removeLocalEventListener(this);
                }
            }
        }, types);

        try {
            if (c != null)
                c.run();
        }
        catch (Exception e) {
            throw new GridException(e);
        }

        return fut.get(timeout);
    }

    /**
     * @param evt Event to notify about.
     */
    private void notifyListeners(GridEvent evt) {
        assert evt != null;

        notifyListeners(lsnrs.get(evt.type()), evt);
    }

    /**
     * @param set Set of listeners.
     * @param evt Grid event.
     */
    private void notifyListeners(@Nullable Collection<GridLocalEventListener> set, GridEvent evt) {
        assert evt != null;

        if (!F.isEmpty(set)) {
            assert set != null;

            for (GridLocalEventListener lsnr : set)
                try {
                    lsnr.onEvent(evt);
                }
                catch (Throwable e) {
                    U.error(log, "Unexpected exception in listener notification for event: " + evt, e);
                }
        }
    }

    /**
     * @param p Grid event predicate.
     * @return Collection of grid events.
     */
    public Collection<GridEvent> localEvents(GridPredicate<? super GridEvent>... p) {
        assert p != null;

        return getSpi().localEvents(p);
    }

    /**
     * @param p Grid event predicate.
     * @param nodes Collection of nodes.
     * @param timeout Maximum time to wait for result, if {@code 0}, then wait until result is received.
     * @return Collection of events.
     * @throws GridException Thrown in case of any errors.
     */
    public GridFuture<List<GridEvent>> remoteEventsAsync(final GridPredicate<? super GridEvent> p,
        final Collection<? extends GridNode> nodes, final long timeout) throws GridException {
        assert p != null;
        assert nodes != null && !nodes.isEmpty();

        final GridFutureAdapter<List<GridEvent>> fut = new GridFutureAdapter<List<GridEvent>>(ctx);

        ctx.closure().runLocal(new GPR() {
            @Override public void run() {
                try {
                    fut.onDone(query(p, nodes, timeout));
                }
                catch (GridException e) {
                    fut.onDone(e);
                }
            }
        }, true);

        return fut;
    }

    /**
     * @param p Grid event predicate.
     * @param nodes Collection of nodes.
     * @param timeout Maximum time to wait for result, if {@code 0}, then wait until result is received.
     * @return Collection of events.
     * @throws GridException Thrown in case of any errors.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "deprecation"})
    private List<GridEvent> query(GridPredicate<? super GridEvent> p, Collection<? extends GridNode> nodes,
        long timeout) throws GridException {
        assert p != null;
        assert nodes != null;

        if (nodes.isEmpty()) {
            U.warn(log, "Failed to query events for empty nodes collection.");

            return Collections.emptyList();
        }

        GridIoManager ioMgr = ctx.io();

        final List<GridEvent> evts = new ArrayList<GridEvent>();

        final AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);

        final Set<UUID> uids = new HashSet<UUID>();

        final Object qryMux = new Object();

        for (GridNode node : nodes)
            uids.add(node.id());

        GridLocalEventListener evtLsnr = new GridLocalEventListener() {
            @Override public void onEvent(GridEvent evt) {
                assert evt instanceof GridDiscoveryEvent;

                synchronized (qryMux) {
                    uids.remove(((GridDiscoveryEvent)evt).eventNodeId());

                    if (uids.isEmpty()) {
                        qryMux.notifyAll();
                    }
                }
            }
        };

        GridMessageListener resLsnr = new GridMessageListener() {
            @SuppressWarnings("deprecation")
            @Override public void onMessage(UUID nodeId, Object msg) {
                assert nodeId != null;
                assert msg != null;

                if (!(msg instanceof GridEventStorageMessage)) {
                    U.error(log, "Received unknown message: " + msg);

                    return;
                }

                GridEventStorageMessage res = (GridEventStorageMessage)msg;

                synchronized (qryMux) {
                    if (uids.remove(nodeId)) {
                        if (res.events() != null)
                            evts.addAll(res.events());
                    }
                    else
                        U.warn(log, "Received duplicate response (ignoring) [nodeId=" + nodeId +
                            ", msg=" + res + ']');

                    if (res.exception() != null)
                        err.set(res.exception());

                    if (uids.isEmpty() || err.get() != null)
                        qryMux.notifyAll();
                }
            }
        };

        String resTopic = TOPIC_EVENT.name(UUID.randomUUID());

        try {
            addLocalEventListener(evtLsnr, new int[] {
                EVT_NODE_LEFT,
                EVT_NODE_FAILED
            });

            ioMgr.addMessageListener(resTopic, resLsnr);

            GridByteArrayList serFilter = U.marshal(ctx.config().getMarshaller(), p);

            GridDeployment dep = ctx.deploy().deploy(p.getClass(), U.detectClassLoader(p.getClass()));

            if (dep == null)
                throw new GridException("Failed to deploy event filter: " + p);

            Serializable msg = new GridEventStorageMessage(
                resTopic,
                serFilter,
                p.getClass().getName(),
                dep.classLoaderId(),
                dep.deployMode(),
                dep.sequenceNumber(),
                dep.userVersion(),
                dep.participants());

            ioMgr.send(nodes, TOPIC_EVENT, msg, PUBLIC_POOL);

            if (timeout == 0)
                timeout = Long.MAX_VALUE;

            long now = System.currentTimeMillis();

            // Account for overflow of long value.
            long endTime = now + timeout <= 0 ? Long.MAX_VALUE : now + timeout;

            long delta = timeout;

            Collection<UUID> uidsCopy = null;

            synchronized (qryMux) {
                try {
                    while (!uids.isEmpty() && err.get() == null && delta > 0) {
                        qryMux.wait(delta);

                        delta = endTime - System.currentTimeMillis();
                    }
                }
                catch (InterruptedException e) {
                    throw new GridException("Got interrupted while waiting for event query responses.", e);
                }

                if (err.get() != null)
                    throw new GridException("Failed to query events due to exception on remote node.", err.get());

                if (!uids.isEmpty())
                    uidsCopy = new LinkedList<UUID>(uids);
            }

            // Outside of synchronization.
            if (uidsCopy != null) {
                for (Iterator<UUID> iter = uidsCopy.iterator(); iter.hasNext();)
                    // Ignore nodes that have left the grid.
                    if (ctx.discovery().node(iter.next()) == null)
                        iter.remove();

                if (!uidsCopy.isEmpty())
                    throw new GridException("Failed to receive event query response from following nodes: " +
                        uidsCopy);
            }
        }
        finally {
            ioMgr.removeMessageListener(resTopic, resLsnr);

            removeLocalEventListener(evtLsnr);
        }

        return evts;
    }
}
