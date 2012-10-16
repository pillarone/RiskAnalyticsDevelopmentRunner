// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.communication;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.kernal.managers.eventstorage.*;
import org.gridgain.grid.kernal.processors.timeout.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.communication.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.managers.communication.GridIoPolicy.*;

/**
 * Grid communication manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridIoManager extends GridManagerAdapter<GridCommunicationSpi> {
    /** */
    public static final int MAX_CLOSED_TOPICS = 10000;

    /** */
    static final int RETRY_SEND_CNT = 50;

    /** */
    private final ConcurrentMap<String, GridConcurrentHashSet<GridFilteredMessageListener>> lsnrMap =
        new ConcurrentHashMap<String, GridConcurrentHashSet<GridFilteredMessageListener>>();

    /** Internal worker pool. */
    private GridWorkerPool workerPool;

    /** Internal P2P pool. */
    private GridWorkerPool p2pPool;

    /** Internal system pool. */
    private GridWorkerPool sysPool;

    /** */
    private GridLocalEventListener discoLsnr;

    /** */
    private final Map<String, Map<UUID, GridCommunicationMessageSet>> msgSetMap =
        new HashMap<String, Map<UUID, GridCommunicationMessageSet>>();

    /** */
    private final ConcurrentMap<String, ConcurrentMap<UUID, AtomicLong>> msgIdMap =
        new ConcurrentHashMap<String, ConcurrentMap<UUID, AtomicLong>>();

    /** Finished job topic names queue with the fixed size. */
    private final Collection<String> closedTopics = new GridBoundedLinkedHashSet<String>(MAX_CLOSED_TOPICS);

    /** Local node ID. */
    private final UUID locNodeId;

    /** */
    private final long discoDelay;

    /** Cache for messages that were received prior to discovery. */
    private final Map<UUID, List<GridIoMessage>> discoWaitMap =
        new HashMap<UUID, List<GridIoMessage>>();

    /** Communication message listener. */
    @SuppressWarnings("deprecation")
    private GridMessageListener msgLsnr;

    /** */
    private int callCnt;

    /** */
    private boolean stopping;

    /** Mutex. */
    private final Object mux = new Object();

    /** Grid marshaller. */
    private final GridMarshaller marshaller;

    /** */
    private final Map<UUID, Map<Long, GridIoResult>> syncReqMap =
        new HashMap<UUID, Map<Long, GridIoResult>>();

    /** */
    private long syncReqIdCnt;

    /** */
    private ThreadLocal<GridTuple2<Object, GridByteArrayList>> cacheMsg =
        new ThreadLocal<GridTuple2<Object, GridByteArrayList>>() {
            @Nullable
            @Override protected GridTuple2<Object, GridByteArrayList> initialValue() {
                return null;
            }
        };

    /**
     * @param ctx Grid kernal context.
     */
    public GridIoManager(GridKernalContext ctx) {
        super(GridCommunicationSpi.class, ctx, ctx.config().getCommunicationSpi());

        locNodeId = ctx.config().getNodeId();

        long cfgDiscoDelay = ctx.config().getDiscoveryStartupDelay();

        discoDelay = cfgDiscoDelay == 0 ? GridConfiguration.DFLT_DISCOVERY_STARTUP_DELAY : cfgDiscoDelay;

        marshaller = ctx.config().getMarshaller();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    @Override public void start() throws GridException {
        assertParameter(discoDelay > 0, "discoveryStartupDelay > 0");

        startSpi();

        workerPool = new GridWorkerPool(ctx.config().getExecutorService(), log);
        p2pPool = new GridWorkerPool(ctx.config().getPeerClassLoadingExecutorService(), log);
        sysPool = new GridWorkerPool(ctx.config().getSystemExecutorService(), log);

        getSpi().setListener(msgLsnr = new GridMessageListener() {
            @SuppressWarnings("deprecation")
            @Override public void onMessage(UUID nodeId, Object msg) {
                assert nodeId != null;
                assert msg != null;

                if (log.isDebugEnabled())
                    log.debug("Received communication message: " + msg);

                GridIoMessage commMsg = (GridIoMessage)msg;

                // If discovery was not started, then it means that we got bound to the
                // same port as a previous node that was started on this IP and got a message
                // destined for a previous node.
                if (!commMsg.destinationIds().contains(locNodeId)) {
                    U.error(log, "Received message whose destination does not match this node (will ignore): " + msg);

                    return;
                }

                if (!nodeId.equals(commMsg.senderId())) {
                    U.error(log, "Expected node ID does not match the one in message " +
                        "[expected=" + nodeId + ", msgNodeId=" + commMsg.senderId() + ']');

                    return;
                }

                GridNode node = ctx.discovery().node(nodeId);

                // Get the same ID instance as the node.
                commMsg.senderId(nodeId = node == null ? nodeId : node.id());

                boolean isCalled = false;

                try {
                    synchronized (mux) {
                        if (stopping) {
                            if (log.isDebugEnabled())
                                log.debug("Received communication message while stopping grid: " + msg);

                            return;
                        }

                        // Although we check closed topics prior to processing
                        // every message, we still check it here to avoid redundant
                        // placement of messages on wait list whenever possible.
                        if (closedTopics.contains(commMsg.topic())) {
                            if (log.isDebugEnabled())
                                log.debug("Message is ignored as it came for the closed topic: " + msg);

                            return;
                        }

                        callCnt++;

                        isCalled = true;

                        // Remove expired messages from wait list.
                        processWaitList();

                        // Received message before a node got discovered or after it left.
                        if (ctx.discovery().node(nodeId) == null) {
                            if (log.isDebugEnabled())
                                log.debug("Adding message to waiting list [senderId=" + nodeId + ", msg=" + msg + ']');

                            addToWaitList(commMsg);

                            return;
                        }
                    }

                    // If message is P2P, then process in P2P service.
                    // This is done to avoid extra waiting and potential deadlocks
                    // as thread pool may not have any available threads to give.
                    switch (commMsg.policy()) {
                        case P2P_POOL: {
                            processP2PMessage(nodeId, commMsg);

                            break;
                        }

                        case PUBLIC_POOL:
                        case SYSTEM_POOL: {
                            if (!commMsg.isOrdered())
                                processRegularMessage(nodeId, commMsg, commMsg.policy());
                            else
                                processOrderedMessage(nodeId, commMsg, commMsg.policy());

                            break;
                        }
                    }
                }
                finally {
                    if (isCalled)
                        synchronized (mux) {
                            callCnt--;

                            if (callCnt == 0)
                                mux.notifyAll();
                        }
                }
            }
        });

        addMessageListener(TOPIC_COMM_SYNC, new GridMessageListener() {
            @Override public void onMessage(UUID nodeId, Object msg) {
                GridIoSyncMessageResponse msgRes = (GridIoSyncMessageResponse)msg;

                GridIoResult res = null;

                synchronized (syncReqMap) {
                    Map<Long, GridIoResult> msgMap = syncReqMap.get(nodeId);

                    if (msgMap != null)
                        res = msgMap.remove(msgRes.getRequestId());
                }

                if (res != null)
                    if (msgRes.getException() != null)
                        res.setException(msgRes.getException());
                    else
                        res.setResult(msgRes.getResult());
            }
        });

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        super.onKernalStart();

        discoLsnr = new GridLocalEventListener() {
            /**
             * This listener will remove all message sets that came from failed or left node.
             *
             * @param evt Local grid event.
             */
            @SuppressWarnings("deprecation")
            @Override public void onEvent(GridEvent evt) {
                assert evt instanceof GridDiscoveryEvent;

                GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                UUID nodeId = discoEvt.eventNodeId();

                switch (evt.type()) {
                    case EVT_NODE_JOINED: {
                        List<GridIoMessage> waitList;

                        synchronized (mux) {
                            // Note, that we still may get a new wait list
                            // if the joining node left while we are still
                            // in this code. In this case we don't care about it
                            // and will let those messages naturally expire.
                            waitList = discoWaitMap.remove(nodeId);
                        }

                        if (waitList != null)
                            // Process messages on wait list outside of synchronization.
                            for (GridIoMessage msg : waitList)
                                msgLsnr.onMessage(msg.senderId(), msg);

                        break;
                    }

                    case EVT_NODE_LEFT:
                    case EVT_NODE_FAILED: {
                        synchronized (syncReqMap) {
                            Map<Long, GridIoResult> msgMap = syncReqMap.remove(nodeId);

                            if (msgMap != null)
                                for (GridIoResult res : msgMap.values())
                                    res.setException(new GridTopologyException("Node has left: " + nodeId));
                        }

                        synchronized (mux) {
                            // Remove messages waiting for this node to join.
                            List<GridIoMessage> waitList = discoWaitMap.remove(nodeId);

                            if (log.isDebugEnabled())
                                log.debug("Removed messages from discovery startup delay list " +
                                    "(sender node left topology): " + waitList);

                            // Clean up ordered messages.
                            for (Iterator<Map<UUID, GridCommunicationMessageSet>> iter = msgSetMap.values().iterator();
                                 iter.hasNext();) {
                                Map<UUID, GridCommunicationMessageSet> map = iter.next();

                                GridCommunicationMessageSet set = map.remove(nodeId);

                                if (set != null) {
                                    if (log.isDebugEnabled())
                                        log.debug("Removing message set due to node leaving grid: " + set);

                                    // Unregister timeout listener.
                                    ctx.timeout().removeTimeoutObject(set);

                                    // Node may still send stale messages for this topic
                                    // even after discovery notification is done.
                                    closedTopics.add(set.getTopic());
                                }

                                if (map.isEmpty())
                                    iter.remove();
                            }
                        }

                        break;
                    }

                    default: { /** No-op. */}
                }
            }
        };

        ctx.event().addLocalEventListener(discoLsnr,
            EVT_NODE_JOINED,
            EVT_NODE_LEFT,
            EVT_NODE_FAILED);

        // Make sure that there are no stale nodes due to window between communication
        // manager start and kernal start.
        synchronized (mux) {
            // Clean up ordered messages.
            F.drop(msgSetMap.values(), new P1<Map<UUID, GridCommunicationMessageSet>>() {
                @Override public boolean apply(Map<UUID, GridCommunicationMessageSet> map) {
                    F.drop(map.values(), new P1<GridCommunicationMessageSet>() {
                        @Override public boolean apply(GridCommunicationMessageSet set) {
                            // If message set belongs to failed or left node.
                            if (ctx.discovery().node(set.getNodeId()) == null) {
                                if (log.isDebugEnabled())
                                    log.debug("Removing message set due to node leaving grid: " + set);

                                // Unregister timeout listener.
                                ctx.timeout().removeTimeoutObject(set);

                                // Node may still send stale messages for this topic
                                // even after discovery notification is done.
                                closedTopics.add(set.getTopic());

                                return true;
                            }

                            return false;
                        }
                    });

                    return map.isEmpty();
                }
            });
        }
    }

    /**
     * Adds new message to discovery wait list.
     *
     * @param newMsg Message to add.
     */
    private void addToWaitList(GridIoMessage newMsg) {
        assert Thread.holdsLock(mux);

        // Add new message.
        List<GridIoMessage> list = F.addIfAbsent(discoWaitMap, newMsg.senderId(), F.<GridIoMessage>newList());

        assert list != null;

        list.add(newMsg);
    }

    /**
     * Removes expired messages from wait list.
     */
    private void processWaitList() {
        assert Thread.holdsLock(mux);

        F.drop(discoWaitMap.values(), new P1<List<GridIoMessage>>() {
            @Override
            public boolean apply(List<GridIoMessage> msgs) {
                F.drop(msgs, new P1<GridIoMessage>() {
                    @Override
                    public boolean apply(GridIoMessage msg) {
                        if (System.currentTimeMillis() - msg.receiveTime() > discoDelay) {
                            if (log.isDebugEnabled())
                                log.debug("Removing expired message from discovery wait list. " +
                                    "This is normal when received a message after sender node has left the grid. " +
                                    "It also may happen (although rarely) if sender node has not been " +
                                    "discovered yet and 'GridConfiguration.getDiscoveryStartupDelay()' value is " +
                                    "too small. Make sure to increase this parameter " +
                                    "if you believe that message should have been processed. Removed message: " + msg);

                            return true;
                        }

                        return false;
                    }
                });

                return msgs.isEmpty();
            }
        });
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop() {
        // No more communication messages.
        getSpi().setListener(null);

        synchronized (mux) {
            // Set stopping flag.
            stopping = true;

            // Wait for all method calls to complete. Note that we can only
            // do it after interrupting all tasks.
            while (true) {
                assert callCnt >= 0;

                // This condition is taken out of the loop to avoid
                // potentially wrong optimization by the compiler of
                // moving field access out of the loop causing this loop
                // to never exit.
                if (callCnt == 0)
                    break;

                if (log.isDebugEnabled())
                    log.debug("Waiting for communication listener to finish: " + callCnt);

                try {
                    // Release mux.
                    mux.wait(5000); // Check again in 5 secs.
                }
                catch (InterruptedException e) {
                    U.error(log, "Got interrupted while stopping (shutdown is incomplete)", e);
                }
            }
        }

        GridEventStorageManager evtMgr = ctx.event();

        if (evtMgr != null && discoLsnr != null)
            evtMgr.removeLocalEventListener(discoLsnr);

        super.onKernalStop();
    }

    /** {@inheritDoc} */
    @Override public void stop() throws GridException {
        stopSpi();

        // Stop should be done in proper order.
        // First we stop regular workers,
        // Then ordered and urgent at the end.
        if (workerPool != null)
            workerPool.join(true);

        if (sysPool != null)
            sysPool.join(true);

        if (p2pPool != null)
            p2pPool.join(true);

        // Clear cache.
        cacheMsg.set(null);

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /**
     * Gets execution pool for policy.
     *
     * @param policy Policy.
     * @return Execution pool.
     */
    private GridWorkerPool getPool(GridIoPolicy policy) {
        switch (policy) {
            case P2P_POOL:
                return p2pPool;
            case SYSTEM_POOL:
                return sysPool;
            case PUBLIC_POOL:
                return workerPool;

            default: {
                assert false : "Invalid communication policy: " + policy;

                // Never reached.
                return null;
            }
        }
    }

    /**
     * @param nodeId Node ID.
     * @param msg Urgent message.
     */
    @SuppressWarnings("deprecation")
    private void processP2PMessage(final UUID nodeId, final GridIoMessage msg) {
        assert msg.policy() == P2P_POOL;

        final Set<GridFilteredMessageListener> lsnrs;

        synchronized (mux) {
            if (closedTopics.contains(msg.topic())) {
                if (log.isDebugEnabled())
                    log.debug("Message is ignored because it came for the closed topic: " + msg);

                return;
            }

            lsnrs = lsnrMap.get(msg.topic());
        }

        // Note, that since listeners are stored in unmodifiable collection, we
        // don't have to hold synchronization lock during event notifications.
        if (!F.isEmpty(lsnrs)) {
            final Runnable closure = new Runnable() {
                @Override public void run() {
                    try {
                        Object obj = unmarshal(msg);

                        for (GridMessageListener lsnr : lsnrs)
                            lsnr.onMessage(nodeId, obj);
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to deserialize P2P communication message:" + msg, e);
                    }
                }
            };

            try {
                p2pPool.execute(new GridWorker(ctx.config().getGridName(), "comm-mgr-urgent-worker", log) {
                    @Override protected void body() {
                        closure.run();
                    }
                });
            }
            catch (GridExecutionRejectedException e) {
                U.error(log, "Failed to process P2P message due to execution rejection. Increase the upper bound " +
                    "on 'ExecutorService' provided by 'GridConfiguration.getPeerClassLoadingExecutorService()'. " +
                    "Will attempt to process message in the listener thread instead.", e);

                closure.run();
            }
            catch (GridException e) {
                U.error(log, "Failed to process P2P message due to system error.", e);
            }
        }
    }

    /**
     * @param nodeId Node ID.
     * @param msg Regular message.
     * @param policy Execution policy.
     */
    @SuppressWarnings("deprecation")
    private void processRegularMessage(final UUID nodeId, final GridIoMessage msg, GridIoPolicy policy) {
        assert !msg.isOrdered();

        final Set<GridFilteredMessageListener> lsnrs;

        synchronized (mux) {
            if (closedTopics.contains(msg.topic())) {
                if (log.isDebugEnabled())
                    log.debug("Message is ignored because it came for the closed topic: " + msg);

                return;
            }

            lsnrs = lsnrMap.get(msg.topic());
        }

        if (!F.isEmpty(lsnrs)) {
            final Runnable closure = new Runnable() {
                @Override public void run() {
                    try {
                        Object obj = unmarshal(msg);

                        for (GridMessageListener lsnr : lsnrs)
                            lsnr.onMessage(nodeId, obj);
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to deserialize regular communication message: " + msg, e);
                    }
                }
            };

            // Note, that since listeners are stored in unmodifiable collection, we
            // don't have to hold synchronization lock during event notifications.
            try {
                getPool(policy).execute(new GridWorker(ctx.config().getGridName(), "comm-mgr-unordered-worker", log) {
                    @Override protected void body() {
                        closure.run();
                    }
                });
            }
            catch (GridExecutionRejectedException e) {
                U.error(log, "Failed to process regular message due to execution rejection. Increase the upper bound " +
                    "on 'ExecutorService' provided by 'GridConfiguration.getExecutorService()'. " +
                    "Will attempt to process message in the listener thread instead.", e);

                closure.run();
            }
            catch (GridException e) {
                U.error(log, "Failed to process regular message due to system error.", e);
            }
        }
    }

    /**
     * @param nodeId Node ID.
     * @param msg Ordered message.
     * @param policy Execution policy.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    private void processOrderedMessage(UUID nodeId, GridIoMessage msg, GridIoPolicy policy) {
        assert msg.isOrdered();

        assert msg.timeout() > 0 : "Message timeout of 0 should never be sent: " + msg;

        long endTime = msg.timeout() + System.currentTimeMillis();

        // Account for overflow.
        if (endTime < 0)
            endTime = Long.MAX_VALUE;

        final GridCommunicationMessageSet msgSet;

        boolean isNew = false;

        synchronized (mux) {
            if (closedTopics.contains(msg.topic())) {
                if (log.isDebugEnabled())
                    log.debug("Message is ignored as it came for the closed topic: " + msg);

                return;
            }

            Map<UUID, GridCommunicationMessageSet> map = F.addIfAbsent(msgSetMap, msg.topic(),
                F.<UUID, GridCommunicationMessageSet>newMap());

            assert map != null;

            GridCommunicationMessageSet set = map.get(nodeId);

            if (set == null) {
                map.put(nodeId, set = new GridCommunicationMessageSet(policy, msg.topic(), nodeId, endTime));

                isNew = true;
            }

            msgSet = set;
        }

        if (isNew && endTime != Long.MAX_VALUE)
            ctx.timeout().addTimeoutObject(msgSet);

        final Set<GridFilteredMessageListener> lsnrs;

        synchronized (msgSet) {
            msgSet.add(msg);

            lsnrs = lsnrMap.get(msg.topic());
        }

        if (!F.isEmpty(lsnrs)) {
            try {
                getPool(policy).execute(new GridWorker(ctx.config().getGridName(), "comm-mgr-ordered-worker", log) {
                    @Override protected void body() {
                        unwindMessageSet(msgSet, lsnrs);
                    }
                });
            }
            catch (GridExecutionRejectedException e) {
                U.error(log, "Failed to process ordered message due to execution rejection. Increase the upper bound " +
                    "on system executor service provided by 'GridConfiguration.getSystemExecutorService()'). Will " +
                    "attempt to process message in the listener thread instead.", e);

                unwindMessageSet(msgSet, lsnrs);
            }
            catch (GridException e) {
                U.error(log, "Failed to process ordered message due to system error.", e);
            }
        }
        else {
            // Note that we simply keep messages if listener is not
            // registered yet, until one will be registered.
            if (log.isDebugEnabled())
                log.debug("Received message for unknown listener " +
                    "(messages will be kept until a listener is registered): " + msg);
        }
    }

    /**
     * @param msgSet Message set to unwind.
     * @param lsnrs Listeners to notify.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "deprecation"})
    private void unwindMessageSet(GridCommunicationMessageSet msgSet, Iterable<GridFilteredMessageListener> lsnrs) {
        // Loop until message set is empty or
        // another thread owns the reservation.
        while (true) {
            boolean selfReserved = false;

            try {
                Collection<GridIoMessage> orderedMsgs;

                synchronized (msgSet) {
                    if (msgSet.reserve()) {
                        selfReserved = true;

                        orderedMsgs = msgSet.unwind();

                        // No more messages to process.
                        if (orderedMsgs.isEmpty())
                            return;
                    }
                    else
                        // Another thread owns reservation.
                        return;
                }

                if (!orderedMsgs.isEmpty())
                    for (GridIoMessage msg : orderedMsgs)
                        try {
                            Object obj = unmarshal(msg);

                            // Don't synchronize on listeners as the collection
                            // is immutable.
                            for (GridMessageListener lsnr : lsnrs) {
                                // Notify messages without synchronizing on msgSet.
                                lsnr.onMessage(msgSet.getNodeId(), obj);
                            }
                        }
                        catch (GridException e) {
                            U.error(log, "Failed to deserialize ordered communication message:" + msg, e);
                        }
                else if (log.isDebugEnabled())
                    log.debug("No messages were unwound: " + msgSet);
            }
            finally {
                if (selfReserved)
                    synchronized (msgSet) {
                        msgSet.release();
                    }
            }
        }
    }

    /**
     * Unmarshal given message with appropriate class loader.
     *
     * @param msg communication message
     * @return Unmarshalled message.
     * @throws GridException If deserialization failed.
     */
    private Object unmarshal(GridIoMessage msg) throws GridException {
        return U.unmarshal(marshaller, msg.message(), U.detectClassLoader(getClass()));
    }

    /**
     * @param node Destination node.
     * @param topic Topic to send the message to.
     * @param topicOrd GridTopic enumeration ordinal.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @param msgId Message ID.
     * @param timeout Timeout.
     * @throws GridException Thrown in case of any errors.
     */
    private void send(GridNode node, String topic, int topicOrd, Object msg, GridIoPolicy policy,
        long msgId, long timeout) throws GridException {
        assert node != null;
        assert topic != null;
        assert msg != null;
        assert policy != null;

        GridByteArrayList serMsg = marshalSendingMessage(msg);

        try {
            getSpi().sendMessage(node, new GridIoMessage(locNodeId, node.id(), topic, topicOrd,
                serMsg, policy, msgId, timeout));
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to send message [node=" + node + ", topic=" + topic +
                ", msg=" + msg + ", policy=" + policy + ']', e);
        }
    }

    /**
     * @param nodeId Id of destination node.
     * @param topic Topic to send the message to.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @throws GridException Thrown in case of any errors.
     */
    public void send(UUID nodeId, String topic, Object msg, GridIoPolicy policy) throws GridException {
        GridNode node = ctx.discovery().node(nodeId);

        if (node == null)
            throw new GridException("Failed to send message to node (has node left grid?): " + nodeId);

        send(node, topic, msg, policy);
    }

    /**
     * @param nodeId Id of destination node.
     * @param topic Topic to send the message to.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @throws GridException Thrown in case of any errors.
     */
    @SuppressWarnings("TypeMayBeWeakened")
    public void send(UUID nodeId, GridTopic topic, Object msg, GridIoPolicy policy) throws GridException {
        GridNode node = ctx.discovery().node(nodeId);

        if (node == null)
            throw new GridException("Failed to send message to node (has node left grid?): " + nodeId);

        send(node, topic.name(), topic.ordinal(), msg, policy, -1, 0);
    }

    /**
     * @param node Destination node.
     * @param topic Topic to send the message to.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @throws GridException Thrown in case of any errors.
     */
    public void send(GridNode node, String topic, Object msg, GridIoPolicy policy) throws GridException {
        send(node, topic, -1, msg, policy, -1, 0);
    }

    /**
     * @param node Destination node.
     * @param topic Topic to send the message to.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @throws GridException Thrown in case of any errors.
     */
    @SuppressWarnings("TypeMayBeWeakened")
    public void send(GridNode node, GridTopic topic, Object msg, GridIoPolicy policy) throws GridException {
        send(node, topic.name(), topic.ordinal(), msg, policy, -1, 0);
    }

    /**
     * @param topic Message topic.
     * @param nodeId Node ID.
     * @return Next ordered message ID.
     */
    public long getNextMessageId(String topic, UUID nodeId) {
        ConcurrentMap<UUID, AtomicLong> map = msgIdMap.get(topic);

        if (map == null) {
            ConcurrentMap<UUID, AtomicLong> lastMap = msgIdMap.putIfAbsent(topic,
                map = new ConcurrentHashMap<UUID, AtomicLong>(1, 1.0f, 16));

            if (lastMap != null)
                map = lastMap;
        }

        AtomicLong msgId = map.get(nodeId);

        if (msgId == null) {
            AtomicLong lastMsgId = map.putIfAbsent(nodeId, msgId = new AtomicLong(0));

            if (lastMsgId != null)
                msgId = lastMsgId;
        }

        if (log.isDebugEnabled())
            log.debug("Getting next message ID for topic: " + topic);

        return msgId.incrementAndGet();
    }

    /**
     * @param topic Message topic.
     */
    public void removeMessageId(String topic) {
        if (log.isDebugEnabled())
            log.debug("Remove message ID for topic: " + topic);

        msgIdMap.remove(topic);
    }

    /**
     * @param node Destination node.
     * @param topic Topic to send the message to.
     * @param msgId Ordered message ID.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @param timeout Timeout to keep a message on receiving queue.
     * @throws GridException Thrown in case of any errors.
     */
    public void sendOrderedMessage(GridNode node, String topic, long msgId, Object msg,
        GridIoPolicy policy, long timeout) throws GridException {
        send(node, topic, (byte)-1, msg, policy, msgId, timeout);
    }

    /**
     * @param nodes Destination nodes.
     * @param topic Topic to send the message to.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @throws GridException Thrown in case of any errors.
     */
    public void send(Collection<? extends GridNode> nodes, String topic, Object msg,
        GridIoPolicy policy) throws GridException {
        send(nodes, topic, -1, msg, policy);
    }

    /**
     * @param nodes Destination nodes.
     * @param topic Topic to send the message to.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @throws GridException Thrown in case of any errors.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    public void send(Collection<? extends GridNode> nodes, GridTopic topic, Object msg,
        GridIoPolicy policy) throws GridException {
        send(nodes, topic.name(), topic.ordinal(), msg, policy);
    }

    /**
     * Sends a peer deployable user message.
     *
     * @param nodes Destination nodes.
     * @param msg Message to send.
     * @throws GridException Thrown in case of any errors.
     */
    public void sendUserMessage(Collection<? extends GridNode> nodes, Object msg) throws GridException {
        GridByteArrayList serSrc = U.marshal(ctx.config().getMarshaller(), msg);

        GridDeployment dep = ctx.deploy().deploy(msg.getClass(), U.detectClassLoader(msg.getClass()));

        if (dep == null)
            throw new GridException("Failed to deploy user message: " + msg);

        Serializable serMsg = new GridIoUserMessage(
            serSrc,
            msg.getClass().getName(),
            dep.classLoaderId(),
            dep.deployMode(),
            dep.sequenceNumber(),
            dep.userVersion(),
            dep.participants());

        send(nodes, TOPIC_COMM_USER, serMsg, PUBLIC_POOL);
    }

    /**
     *
     * @param nodes Collection of nodes to listen from.
     * @param ps Message predicate.
     */
    @SuppressWarnings("deprecation")
    public <T> void listenAsync(Collection<GridRichNode> nodes, @Nullable final GridPredicate2<UUID, T>[] ps) {
        if (!F.isEmpty(ps)) {
            final UUID[] ids = F.nodeIds(nodes).toArray(new UUID[nodes.size()]);

            // Optimize search over IDs.
            Arrays.sort(ids);

            assert ps != null;

            // Set local context.
            for (GridPredicate2<UUID, T> p : ps)
                if (p instanceof GridListenActor)
                    ((GridListenActor)p).setContext(ctx.grid(), nodes);

            addMessageListener(TOPIC_COMM_USER, new GridMessageListener() {
                @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "unchecked", "deprecated"})
                @Override public void onMessage(UUID nodeId, Object msg) {
                    if (Arrays.binarySearch(ids, nodeId) >= 0) {
                        if (!(msg instanceof GridIoUserMessage)) {
                            U.error(log, "Received unknown message (potentially fatal problem): " + msg);

                            return;
                        }

                        GridIoUserMessage req = (GridIoUserMessage)msg;

                        GridNode node = ctx.discovery().node(nodeId);

                        if (node == null) {
                            U.warn(log, "Failed to resolve sender node that does not exist: " + nodeId);

                            return;
                        }

                        Object srcMsg = null;

                        try {
                            GridDeployment dep = ctx.deploy().getGlobalDeployment(
                                req.getDeploymentMode(),
                                req.getSourceClassName(),
                                req.getSourceClassName(),
                                req.getSequenceNumber(),
                                req.getUserVersion(),
                                nodeId,
                                req.getClassLoaderId(),
                                req.getLoaderParticipants(),
                                null);

                            if (dep == null)
                                throw new GridException("Failed to obtain deployment for user message " +
                                    "(is peer class loading turned on?): " + req);

                            srcMsg = U.unmarshal(ctx.config().getMarshaller(), req.getSource(), dep.classLoader());

                            // Resource injection.
                            ctx.resource().inject(dep, dep.deployedClass(req.getSourceClassName()), srcMsg);
                        }
                        catch (GridException e) {
                            U.error(log, "Failed to send user message [node=" + nodeId + ", message=" + msg + ']', e);
                        }

                        if (srcMsg != null)
                            for (GridPredicate2<UUID, T> p : ps)
                                synchronized (p) {
                                    if (!p.apply(nodeId, (T)srcMsg)) {
                                        removeMessageListener(TOPIC_COMM_USER, this);

                                        // Short-circuit.
                                        return;
                                    }
                                }
                    }
                }
            }, F.<Object>alwaysTrue());
        }
    }

    /**
     * @param nodes Destination nodes.
     * @param topic Topic to send the message to.
     * @param topicOrd Topic ordinal value.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @throws GridException Thrown in case of any errors.
     */
    private void send(Collection<? extends GridNode> nodes, String topic, int topicOrd, Object msg,
        GridIoPolicy policy) throws GridException {
        assert nodes != null;
        assert topic != null;
        assert msg != null;
        assert policy != null;

        try {
            // Small optimization, as communication SPIs may have lighter implementation for sending
            // messages to one node vs. many.
            if (nodes.size() == 1)
                send(F.first(nodes), topic, topicOrd, msg, policy, -1, 0);
            else if (nodes.size() > 1) {
                GridByteArrayList serMsg = marshalSendingMessage(msg);

                List<UUID> destIds = new ArrayList<UUID>(nodes.size());

                for (GridNode node : nodes)
                    destIds.add(node.id());

                getSpi().sendMessage(nodes, new GridIoMessage(locNodeId, destIds, topic, topicOrd, serMsg, policy));
            }
            else
                U.warn(log, "Failed to send message to empty nodes collection [topic=" + topic + ", msg=" +
                    msg + ", policy=" + policy + ']', "Failed to send message to empty nodes collection.");
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to send message [nodes=" + nodes + ", topic=" + topic +
                ", msg=" + msg + ", policy=" + policy + ']', e);
        }
    }

    /**
     * @param nodes Destination nodes.
     * @param topic Topic to send the message to.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @return Future for this sending.
     * @throws GridException Thrown in case of any errors.
     */
    @SuppressWarnings("TypeMayBeWeakened")
    public Future sendAsync(Collection<? extends GridNode> nodes, GridTopic topic, Object msg, GridIoPolicy policy)
        throws GridException {
        return sendAsync(nodes, topic.name(), topic.ordinal(), msg, policy);
    }

    /**
     * @param nodes Destination nodes.
     * @param topic Topic to send the message to.
     * @param topicOrd Topic ordinal.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @return Future for this sending.
     * @throws GridException Thrown in case of any errors.
     */
    private Future sendAsync(final Collection<? extends GridNode> nodes, final String topic, final int topicOrd,
        final Object msg, final GridIoPolicy policy) throws GridException {
        final RunnableFuture fut = new FutureTask<Object>(new Callable<Object>() {
            @Nullable
            @Override public Object call() throws GridException {
                send(nodes, topic, topicOrd, msg, policy);

                return null;
            }
        });

        sysPool.execute(new GridWorker(ctx.config().getGridName(), "comm-mgr-async-msg-worker", log) {
            @Override protected void body() {
                fut.run();
            }
        });

        return fut;
    }

    /**
     * @param node Destination node.
     * @param topic Topic to send the message to.
     * @param msg Message to send.
     * @param policy Type of processing.
     * @return Future for this sending.
     * @throws GridException Thrown in case of any errors.
     */
    public Future sendMessageAsync(GridNode node, GridTopic topic, Object msg, GridIoPolicy policy)
        throws GridException {
        return sendAsync(Collections.singleton(node), topic, msg, policy);
    }

    /**
     * Marshal message.
     *
     * @param msg Message to send.
     * @return Return message serialized in bytes.
     * @throws GridException In case of error.
     */
    private GridByteArrayList marshalSendingMessage(Object msg) throws GridException {
        GridByteArrayList serMsg;

        GridTuple2<Object, GridByteArrayList> cacheEntry = cacheMsg.get();

        // Check cached message in ThreadLocal for optimization.
        if (cacheEntry != null && cacheEntry.get1() == msg)
            serMsg = cacheEntry.get2();
        else {
            serMsg = U.marshal(marshaller, msg);

            cacheMsg.set(F.t(msg, serMsg));
        }

        return serMsg;
    }

    /**
     * @param topic Listener's topic.
     * @param lsnr Listener to add.
     * @param p Predicates to be applied.
     */
    @SuppressWarnings({"TypeMayBeWeakened", "deprecation"})
    public void addMessageListener(GridTopic topic, GridMessageListener lsnr, GridPredicate<Object>... p) {
        addMessageListener(topic.name(), lsnr, p);
    }

    /**
     * @param topic Listener's topic.
     * @param lsnr Listener to add.
     * @param p Predicates to be applied.
     */
    @SuppressWarnings("deprecation")
    public void addMessageListener(String topic, GridMessageListener lsnr, GridPredicate<Object>... p) {
        assert lsnr != null;
        assert topic != null;

        Collection<GridCommunicationMessageSet> msgSets;

        final GridFilteredMessageListener filteredLsnr = new GridFilteredMessageListener(lsnr, p);

        synchronized (mux) {
            GridConcurrentHashSet<GridFilteredMessageListener> temp = lsnrMap.get(topic);

            if (temp == null) {
                GridConcurrentHashSet<GridFilteredMessageListener> lsnrs =
                    new GridConcurrentHashSet<GridFilteredMessageListener>(1);

                lsnrs.add(filteredLsnr);

                temp = lsnrMap.putIfAbsent(topic, lsnrs);

                if (temp != null) {
                    lsnrs = temp;

                    lsnrs.add(filteredLsnr);
                }

                Map<UUID, GridCommunicationMessageSet> map = msgSetMap.get(topic);

                msgSets = map != null ? map.values() : null;

                // Make sure that new topic is not in the list of closed topics.
                closedTopics.remove(topic);
            }
            else {
                msgSets = null;

                temp.add(filteredLsnr);
            }
        }

        if (msgSets != null)
            try {
                for (final GridCommunicationMessageSet msgSet : msgSets)
                    getPool(msgSet.getPolicy()).execute(
                        new GridWorker(ctx.config().getGridName(), "comm-mgr-ordered-worker", log) {
                            @Override protected void body() {
                                unwindMessageSet(msgSet, Collections.singletonList(filteredLsnr));
                            }
                        }
                    );
            }
            catch (GridExecutionRejectedException e) {
                U.error(log, "Failed to process delayed message due to execution rejection. Increase the upper bound " +
                    "on executor service provided in 'GridConfiguration.getExecutorService()'). Will attempt to " +
                    "process message in the listener thread instead instead.", e);

                for (GridCommunicationMessageSet msgSet : msgSets) {
                    unwindMessageSet(msgSet, Collections.singletonList(filteredLsnr));
                }
            }
            catch (GridException e) {
                U.error(log, "Failed to process delayed message due to system error.", e);
            }
    }

    /**
     * @param topic Message topic.
     * @return Whether or not listener was indeed removed.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    public boolean removeMessageListener(GridTopic topic) {
        return removeMessageListener(topic.name());
    }

    /**
     * @param topic Message topic.
     * @return Whether or not listener was indeed removed.
     */
    public boolean removeMessageListener(String topic) {
        return removeMessageListener(topic, null);
    }

    /**
     * @param topic Listener's topic.
     * @param lsnr Listener to remove.
     * @return Whether or not the lsnr was removed.
     */
    @SuppressWarnings({"TypeMayBeWeakened", "deprecation"})
    public boolean removeMessageListener(GridTopic topic, @Nullable GridMessageListener lsnr) {
        return removeMessageListener(topic.name(), lsnr);
    }

    /**
     * @param topic Listener's topic.
     * @param lsnr Listener to remove.
     * @return Whether or not the lsnr was removed.
     */
    @SuppressWarnings("deprecation")
    public boolean removeMessageListener(String topic, @Nullable GridMessageListener lsnr) {
        assert topic != null;

        boolean removed = true;

        Collection<GridCommunicationMessageSet> msgSets = null;

        synchronized (mux) {
            // If listener is null, then remove all listeners.
            if (lsnr == null) {
                removed = lsnrMap.remove(topic) != null;

                Map<UUID, GridCommunicationMessageSet> map = msgSetMap.remove(topic);

                if (map != null) {
                    msgSets = map.values();
                }

                closedTopics.add(topic);
            }
            else {
                GridConcurrentHashSet<GridFilteredMessageListener> lsnrs = lsnrMap.get(topic);

                // If removing listener before subscription happened.
                if (lsnrs == null) {
                    Map<UUID, GridCommunicationMessageSet> map = msgSetMap.remove(topic);

                    if (map != null) {
                        msgSets = map.values();
                    }

                    closedTopics.add(topic);

                    removed = false;
                }
                else {
                    GridFilteredMessageListener filteredLsnr = new GridFilteredMessageListener(lsnr);

                    if (lsnrs.contains(filteredLsnr)) {
                        // If removing last subscribed listener.
                        if (lsnrs.size() == 1) {
                            Map<UUID, GridCommunicationMessageSet> map = msgSetMap.remove(topic);

                            if (map != null) {
                                msgSets = map.values();
                            }

                            lsnrMap.remove(topic);

                            closedTopics.add(topic);
                        }
                        // Remove the specified listener and leave
                        // other subscribed listeners untouched.
                        else {
                            lsnrs.remove(filteredLsnr);
                        }
                    }
                    // Nothing to remove. Put listeners back.
                    else {
                        removed = false;

                        lsnrMap.put(topic, lsnrs);
                    }
                }
            }
        }

        if (msgSets != null) {
            for (GridCommunicationMessageSet msgSet : msgSets) {
                ctx.timeout().removeTimeoutObject(msgSet);
            }
        }

        if (removed && log.isDebugEnabled()) {
            log.debug("Removed message listener [topic=" + topic + ", lsnr=" + lsnr + ']');
        }

        return removed;
    }

    /**
     * Adds a user message listener to support the deprecated method.
     *
     * This is a workaround for implementation of the deprecated
     * {@link Grid#addMessageListener(GridMessageListener, GridPredicate[])} method.
     * In general you should not use it.
     *
     * @param lsnr Listener to add.
     * @param p Predicates to be applied.
     */
    @SuppressWarnings("deprecation")
    public void addUserMessageListener(GridMessageListener lsnr, GridPredicate<Object>... p) {
        // We transform the messages before we send them. Therefore we have to wrap
        // the predicates in the new listener object as well. Then we can apply
        // these predicates to the source message after opposite transformation.

        addMessageListener(TOPIC_COMM_USER, new GridUserMessageListener(lsnr, p));
    }

    /**
     * Removes a user message listener to support the deprecated method.
     *
     * This is a workaround for implementation of the deprecated
     * {@link Grid#removeMessageListener(GridMessageListener)} method.
     * In general you should not use it.
     *
     * @param lsnr Listener to remove.
     * @return Whether or not the lsnr was removed.
     */
    @SuppressWarnings("deprecation")
    public boolean removeUserMessageListener(GridMessageListener lsnr) {
        return removeMessageListener(TOPIC_COMM_USER, new GridUserMessageListener(lsnr));
    }

    /**
     * @param node Grid node.
     * @param topic Communication topic.
     * @param msg Message to send.
     * @param timeout Timeout, {@code 0} for never.
     * @param policy Execution policy.
     * @param lsnr Grid communication result listener.
     * @return Communication future.
     */
    public GridIoFuture sendSync(GridNode node, String topic, Object msg, long timeout,
        GridIoPolicy policy, GridIoResultListener lsnr) {
        return sendSyncByNodeId(node.id(), topic, msg, timeout, policy, lsnr);
    }

    /**
     * @param nodeId Grid node ID.
     * @param topic Communication topic.
     * @param msg Message to send.
     * @param timeout Timeout, {@code 0} for never.
     * @param policy Execution policy.
     * @param lsnr Grid communication result listener.
     * @return Communication future.
     */
    public GridIoFuture sendSyncByNodeId(UUID nodeId, String topic, Object msg, long timeout,
        GridIoPolicy policy, GridIoResultListener lsnr) {
        return sendSyncByNodeId(Collections.singletonList(nodeId), topic, -1, msg, timeout, policy, lsnr);
    }

    /**
     * @param nodes Grid nodes.
     * @param topic Communication topic.
     * @param msg Message to send.
     * @param timeout Timeout, {@code 0} for never.
     * @param policy Execution policy.
     * @param lsnr Grid communication result listener.
     * @return Communication future.
     */
    public GridIoFuture sendSync(Collection<GridNode> nodes, String topic, Object msg,
        long timeout, GridIoPolicy policy, GridIoResultListener lsnr) {
        return sendSync(nodes, topic, -1, msg, policy, -1, timeout, lsnr);
    }

    /**
     * @param node Grid node.
     * @param topic Communication topic.
     * @param msg Message to send.
     * @param timeout Timeout, {@code 0} for never.
     * @param policy Execution policy.
     * @param lsnr Grid communication result listener.
     * @return Communication future.
     */
    public GridIoFuture sendSync(GridNode node, GridTopic topic, Object msg, long timeout,
        GridIoPolicy policy, GridIoResultListener lsnr) {
        return sendSyncByNodeId(node.id(), topic, msg, timeout, policy, lsnr);
    }

    /**
     * @param nodeId Grid node ID.
     * @param topic Communication topic.
     * @param msg Message to send.
     * @param timeout Timeout, {@code 0} for never.
     * @param policy Execution policy.
     * @param lsnr Grid communication result listener.
     * @return Communication future.
     */
    @SuppressWarnings("TypeMayBeWeakened")
    public GridIoFuture sendSyncByNodeId(UUID nodeId, GridTopic topic, Object msg, long timeout,
        GridIoPolicy policy, GridIoResultListener lsnr) {
        return sendSyncByNodeId(Collections.singletonList(nodeId), topic.name(), topic.ordinal(),
            msg, timeout, policy, lsnr);
    }

    /**
     * @param nodes Grid nodes.
     * @param topic Communication topic.
     * @param topicOrd Topic ordinal.
     * @param msg Message to send.
     * @param msgId Message identifier.
     * @param timeout Timeout, {@code 0} for never.
     * @param policy Execution policy.
     * @param lsnr Grid communication result listener.
     * @return Communication future.
     */
    private GridIoFuture sendSync(Collection<GridNode> nodes, String topic, int topicOrd, Object msg,
        GridIoPolicy policy, long msgId, long timeout, GridIoResultListener lsnr) {
        Map<UUID, GridIoResult> resMap = new HashMap<UUID, GridIoResult>(nodes.size(), 1);

        for (GridNode node : nodes) {
            // Does not hurt to check again.
            if (ctx.discovery().node(node.id()) != null) {
                resMap.put(node.id(), new GridIoResult(node.id()));
            }
        }

        resMap = Collections.unmodifiableMap(resMap);

        GridIoFuture fut = new GridIoFuture(resMap, timeout, ctx, lsnr);

        sendSyncInternal(nodes, resMap, topic, topicOrd, msg, policy, msgId, timeout);

        return fut;
    }

    /**
     * @param nodeIds Grid node IDs.
     * @param topic Communication topic.
     * @param topicOrd Topic ordinal.
     * @param msg Message to send.
     * @param timeout Timeout, {@code 0} for never.
     * @param policy Execution policy.
     * @param lsnr Grid communication result listener.
     * @return Communication future.
     */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    public GridIoFuture sendSyncByNodeId(Collection<UUID> nodeIds, String topic,
        int topicOrd, Object msg, long timeout, GridIoPolicy policy, GridIoResultListener lsnr) {
        assert new HashSet<UUID>(nodeIds).size() == nodeIds.size(); // All elements are distinct.
        assert timeout >= 0;
        assert topic != null;
        assert !nodeIds.isEmpty();

        Map<UUID, GridIoResult> resMap = new HashMap<UUID, GridIoResult>(nodeIds.size(), 1);

        for (UUID nodeId : nodeIds) {
            resMap.put(nodeId, new GridIoResult(nodeId));
        }

        resMap = Collections.unmodifiableMap(resMap);

        GridIoFuture fut = new GridIoFuture(resMap, timeout, ctx, lsnr);

        Collection<GridNode> destNodes = new ArrayList<GridNode>(nodeIds.size());

        for (UUID nodeId : nodeIds) {
            GridNode node = ctx.discovery().node(nodeId);

            if (node != null) {
                destNodes.add(node);
            }
            else {
                resMap.get(nodeId).setException(
                    new GridTopologyException("Failed to send message (node has left topology): " + nodeId));
            }
        }

        sendSyncInternal(destNodes, resMap, topic, topicOrd, msg, policy, -1, timeout);

        return fut;
    }

    /**
     * @param destNodes Grid destination nodes.
     * @param resMap Messages.
     * @param topic Communication topic.
     * @param topicOrd Topic ordinal.
     * @param msg Message to send.
     * @param policy Execution policy.
     * @param msgId Message identifier.
     * @param timeout Timeout.
     */
    private void sendSyncInternal(Iterable<GridNode> destNodes, Map<UUID, GridIoResult> resMap,
        String topic, int topicOrd, Object msg, GridIoPolicy policy, long msgId, long timeout) {
        long reqId;

        synchronized (syncReqMap) {
            reqId = syncReqIdCnt++;

            for (GridNode node : destNodes) {
                Map<Long, GridIoResult> msgMap = syncReqMap.get(node.id());

                if (msgMap == null) {
                    msgMap = new HashMap<Long, GridIoResult>(1, 1);

                    syncReqMap.put(node.id(), msgMap);
                }

                msgMap.put(reqId, resMap.get(node.id()));
            }
        }

        GridIoSyncMessageRequest req = new GridIoSyncMessageRequest(reqId, msg);

        try {
            GridByteArrayList serMsg = U.marshal(marshaller, req);

            for (GridNode node : destNodes) {
                try {
                    getSpi().sendMessage(node, new GridIoMessage(locNodeId, node.id(), topic, topicOrd,
                        serMsg, policy, msgId, timeout));
                }
                catch (GridSpiException e) {
                    resMap.get(node.id()).setException(e);

                    U.error(log, "Failed to send message [node=" + node + ", topic=" + topic +
                        ", msg=" + msg + ", policy=" + policy + ']', e);
                }
            }
        }
        catch (GridException e) {
            U.error(log, "Failed to serialize message: " + req, e);

            for (GridIoResult res : resMap.values()) {
                res.setException(e);
            }
        }
    }

    /**
     * @param <M> Type of message
     * @param <R> Type of result.
     * @param topic Communication topic.
     * @param handler Message handler.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    public <M, R> void addSyncMessageHandler(GridTopic topic, GridIoSyncMessageHandler<M, R> handler) {
        addSyncMessageHandler(topic.name(), handler);
    }

    /**
     * @param <M> Type of message
     * @param <R> Type of result.
     * @param topic Communication topic.
     * @param handler Message handler.
     */
    @SuppressWarnings("deprecation")
    public <M, R> void addSyncMessageHandler(final String topic, final GridIoSyncMessageHandler<M, R> handler) {
        assert !removeMessageListener(topic);
        assert topic != null;
        assert handler != null;

        addMessageListener(topic, new GridMessageListener() {
            @SuppressWarnings({"deprecation", "unchecked"})
            @Override
            public void onMessage(UUID nodeId, Object msg) {
                GridIoSyncMessageRequest req = (GridIoSyncMessageRequest)msg;

                GridIoSyncMessageResponse resMsg;

                try {
                    R resObj = handler.handleMessage(nodeId, (M)req.getRequest());

                    resMsg = new GridIoSyncMessageResponse(req.getRequestId(), resObj);
                }
                catch (Throwable e) {
                    resMsg = new GridIoSyncMessageResponse(req.getRequestId(), e);

                    U.error(log, "Failed to handle synchronized message on topic: " + topic, e);
                }

                for (int i = 0; i < RETRY_SEND_CNT; i++) {
                    GridNode node = ctx.discovery().node(nodeId);

                    if (node == null) { // Node fail.
                        break;
                    }

                    try {
                        send(node, TOPIC_COMM_SYNC, resMsg, SYSTEM_POOL);

                        break;
                    }
                    catch (GridException e) {
                        U.error(log, "Fail to send message to node " + node, e);
                    }
                }
            }
        });
    }

    /**
     * @param topic Communication topic.
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    public void removeSyncMessageHandler(GridTopic topic) {
        removeMessageListener(topic.name());
    }

    /**
     * This class represents a pair of listener and its corresponding message p.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    @SuppressWarnings("deprecation")
    private class GridFilteredMessageListener implements GridMessageListener {
        /** MessageFilter. */
        private final GridPredicate<Object>[] p;

        /** MessageListener. */
        private final GridMessageListener lsnr;

        /**
         * Constructs an object with the specified listener and message predicates.
         *
         * @param lsnr Listener to bind.
         * @param p Filter to apply.
         */
        GridFilteredMessageListener(GridMessageListener lsnr, GridPredicate<Object>... p) {
            assert lsnr != null;
            assert p != null;

            this.lsnr = lsnr;
            this.p = p;
        }

        /** {@inheritDoc} */
        @Override public void onMessage(UUID nodeId, Object msg) {
            if (F.isAll(msg, p)) {
                lsnr.onMessage(nodeId, msg);
            }
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            return o instanceof GridFilteredMessageListener && (this == o ||
                lsnr.equals(((GridFilteredMessageListener)o).lsnr));
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return lsnr.hashCode();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(GridFilteredMessageListener.class, this);
        }
    }

    /**
     * This class represents a message listener wrapper that knows about peer deployment.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    @SuppressWarnings("deprecation")
    private class GridUserMessageListener implements GridMessageListener {
        /** MessageFilter. */
        private final GridPredicate<Object>[] p;

        /** MessageListener. */
        private final GridMessageListener lsnr;

        /**
         * Constructs an object with the specified listener.
         *
         * @param lsnr Listener to bind.
         * @param p Filters to apply.
         */
        GridUserMessageListener(GridMessageListener lsnr, GridPredicate<Object>... p) {
            assert lsnr != null;
            assert p != null;

            this.lsnr = lsnr;
            this.p = p;
        }

        /** {@inheritDoc} */
        @Override public void onMessage(UUID nodeId, Object msg) {
            if (!(msg instanceof GridIoUserMessage)) {
                U.error(log, "Received unknown message (potentially fatal error): " + msg);

                return;
            }

            GridIoUserMessage req = (GridIoUserMessage)msg;

            GridNode node = ctx.discovery().node(nodeId);

            if (node == null) {
                U.warn(log, "Failed to resolve sender node (did node leave the grid?): " + nodeId);

                return;
            }

            Object srcMsg = null;

            try {
                GridDeployment dep = ctx.deploy().getGlobalDeployment(
                    req.getDeploymentMode(),
                    req.getSourceClassName(),
                    req.getSourceClassName(),
                    req.getSequenceNumber(),
                    req.getUserVersion(),
                    nodeId,
                    req.getClassLoaderId(),
                    req.getLoaderParticipants(),
                    null);

                if (dep == null) {
                    throw new GridException("Failed to obtain deployment for user message " +
                        "(is peer class loading turned on?): " + req);
                }

                srcMsg = U.unmarshal(ctx.config().getMarshaller(), req.getSource(), dep.classLoader());

                // Resource injection.
                ctx.resource().inject(dep, dep.deployedClass(req.getSourceClassName()), srcMsg);
            }
            catch (GridException e) {
                U.error(log, "Failed to unmarshal user message [node=" + nodeId + ", message=" + msg + ']', e);
            }

            if (srcMsg != null && F.isAll(srcMsg, p)) {
                lsnr.onMessage(nodeId, srcMsg);
            }
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            return o instanceof GridUserMessageListener && (this == o ||
                lsnr.equals(((GridUserMessageListener)o).lsnr));
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return lsnr.hashCode();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(GridUserMessageListener.class, this);
        }
    }

    /**
     * Ordered communication message set.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private class GridCommunicationMessageSet implements GridTimeoutObject {
        /** */
        private final UUID nodeId;

        /** */
        private final long endTime;

        /** */
        private final UUID timeoutId;

        /** */
        private final String topic;

        /** */
        private final GridIoPolicy policy;

        /** */
        @GridToStringInclude
        private final List<GridIoMessage> msgs = new ArrayList<GridIoMessage>();

        /** */
        private long nextMsgId = 1;

        /** */
        private boolean reserved;

        /**
         * @param policy Communication policy.
         * @param topic Communication topic.
         * @param nodeId Node ID.
         * @param endTime endTime.
         */
        GridCommunicationMessageSet(GridIoPolicy policy, String topic, UUID nodeId, long endTime) {
            assert nodeId != null;
            assert topic != null;
            assert policy != null;

            this.policy = policy;
            this.nodeId = nodeId;
            this.topic = topic;
            this.endTime = endTime;

            timeoutId = UUID.randomUUID();
        }

        /** {@inheritDoc} */
        @Override public UUID timeoutId() {
            return timeoutId;
        }

        /** {@inheritDoc} */
        @Override public long endTime() {
            return endTime;
        }

        /** {@inheritDoc} */
        @Override public void onTimeout() {
            assert !Thread.holdsLock(this);

            if (log.isDebugEnabled()) {
                log.debug("Removing message set due to timeout: " + this);
            }

            synchronized (mux) {
                Map<UUID, GridCommunicationMessageSet> map = msgSetMap.get(topic);

                if (map != null) {
                    map.remove(nodeId);

                    if (map.isEmpty()) {
                        msgSetMap.remove(topic);
                    }
                }
            }
        }

        /**
         * @return ID of node that sent the messages in the set.
         */
        UUID getNodeId() {
            return nodeId;
        }

        /**
         * @return Communication policy.
         */
        GridIoPolicy getPolicy() {
            return policy;
        }

        /**
         * @return Message topic.
         */
        String getTopic() {
            return topic;
        }

        /**
         * @return {@code True} if successful.
         */
        boolean reserve() {
            assert Thread.holdsLock(this);

            if (reserved) {
                return false;
            }

            reserved = true;

            return true;
        }

        /**
         * Releases reservation.
         */
        void release() {
            assert Thread.holdsLock(this);

            assert reserved : "Message set was never reserved: " + this;

            reserved = false;
        }

        /**
         * @return Session request.
         */
        Collection<GridIoMessage> unwind() {
            assert Thread.holdsLock(this);

            assert reserved;

            if (msgs.isEmpty()) {
                return Collections.emptyList();
            }

            Collection<GridIoMessage> orderedMsgs = new LinkedList<GridIoMessage>();

            for (Iterator<GridIoMessage> iter = msgs.iterator(); iter.hasNext();) {
                GridIoMessage msg = iter.next();

                if (msg.messageId() == nextMsgId) {
                    orderedMsgs.add(msg);

                    nextMsgId++;

                    iter.remove();
                }
                else {
                    break;
                }
            }

            return orderedMsgs;
        }

        /**
         * @param msg Message to add.
         */
        void add(GridIoMessage msg) {
            assert Thread.holdsLock(this);

            msgs.add(msg);

            Collections.sort(msgs, new Comparator<GridIoMessage>() {
                @Override public int compare(GridIoMessage o1, GridIoMessage o2) {
                    return o1.messageId() < o2.messageId() ? -1 : o1.messageId() == o2.messageId() ? 0 : 1;
                }
            });
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(GridCommunicationMessageSet.class, this);
        }
    }
}
