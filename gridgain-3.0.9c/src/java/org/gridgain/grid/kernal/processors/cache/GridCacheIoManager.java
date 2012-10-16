// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.managers.deployment.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.stopwatch.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.managers.communication.GridIoPolicy.*;

/**
 * Cache communication manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheIoManager<K, V> extends GridCacheManager<K, V> {
    /** Number of retries using to send messages. */
    private static final short RETRY_CNT = 3;

    /** Delay in milliseconds between retries. */
    private static final long RETRY_DELAY = 1000;

    /** Maximum number of message IDs. */
    private static final int MAX_MSG_IDS = 4000;

    /** Message ID generator. */
    private static final AtomicLong idGen = new AtomicLong();

    /** Topic. */
    private String topic;

    /** Handler registry. */
    private ConcurrentMap<Class<?>, GridInClosure2<UUID, Object>> clsHandlers =
        new ConcurrentHashMap<Class<?>, GridInClosure2<UUID, Object>>();

    /** Ordered handler registry. */
    private ConcurrentMap<String, GridInClosure2<UUID, Object>> orderedHandlers =
        new ConcurrentHashMap<String, GridInClosure2<UUID, Object>>();

    /** Processed message IDs. */
    private Collection<MessageId> msgIds = new GridBoundedConcurrentOrderedSet<MessageId>(MAX_MSG_IDS);

    /** Stopping flag. */
    private boolean stopping;

    /** Error flag. */
    private final AtomicBoolean startErr = new AtomicBoolean();

    /** Mutex. */
    private final ReadWriteLock rw = new ReentrantReadWriteLock();

    /** Message listener. */
    @SuppressWarnings({"deprecation"})
    private GridMessageListener lsnr = new GridMessageListener() {
        @SuppressWarnings({"CatchGenericClass", "unchecked"})
        @Override public void onMessage(final UUID nodeId, final Object msg) {
            // Check for duplicates.
            if (!addMessage(nodeId, msg))
                return;

            rw.readLock().lock();

            try {
                if (stopping) {
                    if (log.isDebugEnabled())
                        log.debug("Received cache communication message while stopping (will ignore) [nodeId=" +
                            nodeId + ", msg=" + msg + ']');

                    return;
                }

                if (CU.isPreloaderMessage(msg))
                    processUnordered(nodeId, msg);
                else {
                    GridFuture startFut = cctx.preloader().startFuture();

                    if (startFut.isDone())
                        processUnordered(nodeId, msg);
                    else
                        // Don't hold this thread waiting for preloading to complete.
                        startFut.listenAsync(new CI1<GridFuture<?>>() {
                            @Override public void apply(GridFuture<?> f) {
                                rw.readLock().lock();

                                try {
                                    if (stopping) {
                                        if (log.isDebugEnabled())
                                            log.debug("Received cache communication message while stopping " +
                                                "(will ignore) [nodeId=" + nodeId + ", msg=" + msg + ']');

                                        return;
                                    }

                                    f.get();

                                    processUnordered(nodeId, msg);
                                }
                                catch (GridException e) {
                                    // Log once.
                                    if (startErr.compareAndSet(false, true))
                                        U.error(log, "Failed to complete preload start future (will ignore message) " +
                                            "[fut=" + f + ", nodeId=" + nodeId + ", msg=" + msg + ']', e);
                                }
                                finally {
                                    rw.readLock().unlock();
                                }
                            }
                        });
                }

            }
            catch (Throwable e) {
                U.error(log, "Failed processing message [senderId=" + nodeId + ']', e);
            }
            finally {
                rw.readLock().unlock();
            }
        }
    };

    /**
     * @param nodeId Node ID.
     * @param msg Message.
     */
    private void processUnordered(UUID nodeId, Object msg) {
        try {
            GridInClosure2<UUID, Object> c = clsHandlers.get(msg.getClass());

            if (c == null) {
                U.warn(log, "Received message without registered handler (will ignore) [msg=" + msg +
                    ", nodeId=" + nodeId + ']');

                return;
            }

            if (log.isDebugEnabled())
                log.debug("Received cache communication message [nodeId=" + nodeId + ", msg=" + msg + ']');

            unmarshall(nodeId, msg);

            GridNode n = cctx.discovery().node(nodeId);

            // Start clean.
            CU.resetTxContext(cctx);

            GridStopwatch watch = W.stopwatch(msg.getClass().getSimpleName());

            try {
                // Pass the same ID object as in the node, so we don't end up
                // storing a bunch of new UUIDs in each cache entry.
                c.apply(n == null ? nodeId : n.id(), msg);
            }
            finally {
                watch.stop();
            }

            if (log.isDebugEnabled())
                log.debug("Finished processing cache communication message [nodeId=" + nodeId + ", msg=" + msg + ']');
        }
        catch (Throwable e) {
            U.error(log, "Failed processing message [senderId=" + nodeId + ']', e);
        }
        finally {
            // Clear thread-local tx contexts.
            CU.resetTxContext(cctx);

            // Unwind eviction notifications.
            CU.unwindEvicts(cctx);

            // Unwind event queue after every cache message.
            cctx.events().unwind();
        }
    }

    /** {@inheritDoc} */
    @Override public void start0() throws GridException {
        String cacheName = cctx.name();

        topic = TOPIC_CACHE.name(cacheName == null ? "defaultCache-topic" : cacheName + "-topic");

        cctx.gridIO().addMessageListener(topic, lsnr);
    }

    /** {@inheritDoc} */
    @Override protected void onKernalStop0() {
        cctx.gridIO().removeMessageListener(topic);

        for (String ordTopic : orderedHandlers.keySet())
            cctx.gridIO().removeMessageListener(ordTopic);

        rw.writeLock().lock();

        try {
            stopping = true;
        }
        finally {
            rw.writeLock().unlock();
        }
    }

    /**
     * Pre-processes message prior to send.
     *
     * @param msg Message to send.
     * @throws GridException If failed.
     */
    private void onSend(GridCacheMessage<K, V> msg) throws GridException {
        if (msg.messageId() < 0)
            // Generate and set message ID.
            msg.messageId(idGen.incrementAndGet());

        msg.p2pMarshal(cctx);

        if (msg instanceof GridCacheDeployable)
            cctx.deploy().prepare((GridCacheDeployable)msg);
    }

    /**
     * Sends communication message.
     *
     * @param node Node to send the message to.
     * @param msg Message to send.
     * @throws GridException If sending failed.
     * @throws GridTopologyException If receiver left.
     */
    @SuppressWarnings({"BusyWait"})
    public void send(GridNode node, GridCacheMessage<K, V> msg) throws GridException {
        onSend(msg);

        if (log.isDebugEnabled())
            log.debug("Sending cache message [msg=" + msg + ", node=" + U.toShortString(node) + ']');

        int cnt = 0;

        while (cnt <= RETRY_CNT) {
            try {
                cnt++;

                cctx.gridIO().send(node, topic, msg, SYSTEM_POOL);

                return;
            }
            catch (GridInterruptedException e) {
                throw e;
            }
            catch (GridException e) {
                if (cctx.discovery().node(node.id()) == null)
                    throw new GridTopologyException("Node left grid while sending message: " + msg, e);

                if (cnt == RETRY_CNT)
                    throw e;
                else if (log.isDebugEnabled())
                    log.debug("Failed to send message to node (will retry): " + node.id());
            }

            U.sleep(RETRY_DELAY);
        }

        if (log.isDebugEnabled())
            log.debug("Sent cache message [msg=" + msg + ", node=" + U.toShortString(node) + ']');
    }

    /**
     * Sends communication message.
     *
     * @param nodes Nodes to send the message to.
     * @param msg Message to send.
     * @throws GridException If sending failed.
     */
    public void send(Collection<? extends GridNode> nodes, GridCacheMessage<K, V> msg) throws GridException {
        onSend(msg);

        if (nodes.isEmpty()) {
            if (log.isDebugEnabled())
                log.debug("Message will not be sent as collection of nodes is empty: " + msg);

            return;
        }

        if (log.isDebugEnabled())
            log.debug("Sending cache message [msg=" + msg + ", node=" + U.toShortString(nodes) + ']');

        cctx.gridIO().send(nodes, topic, msg, SYSTEM_POOL);

        if (log.isDebugEnabled())
            log.debug("Sent cache message [msg=" + msg + ", node=" + U.toShortString(nodes) + ']');
    }

    /**
     * Sends message and automatically accounts for lefts nodes.
     *
     * @param nodes Nodes to send to.
     * @param msg Message to send.
     * @param fallback Callback for failed nodes.
     * @return {@code True} if nodes are empty or message was sent, {@code false} if
     *      all nodes have left topology while sending this message.
     * @throws GridException If send failed.
     */
    @SuppressWarnings( {"BusyWait"})
    public boolean safeSend(Collection<? extends GridNode> nodes, GridCacheMessage<K, V> msg,
        @Nullable GridPredicate<GridNode> fallback) throws GridException {
        assert nodes != null;
        assert msg != null;

        if (nodes.isEmpty()) {
            if (log.isDebugEnabled())
                log.debug("Message will not be sent as collection of nodes is empty: " + msg);

            return true;
        }

        onSend(msg);

        if (log.isDebugEnabled())
            log.debug("Sending cache message [msg=" + msg + ", node=" + U.toShortString(nodes) + ']');

        final Collection<UUID> leftIds = new LinkedList<UUID>();

        int cnt = 0;

        while (cnt < RETRY_CNT) {
            try {
                cctx.gridIO().send(F.view(nodes, new P1<GridNode>() {
                    @Override public boolean apply(GridNode e) {
                        return !leftIds.contains(e.id());
                    }
                }), topic, msg, SYSTEM_POOL);

                break;
            }
            catch (GridException e) {
                boolean added = false;

                for (GridNode n : nodes) {
                    if (!leftIds.contains(n.id()) && cctx.discovery().node(n.id()) == null) {
                        leftIds.add(n.id());

                        if (fallback != null && !fallback.apply(n))
                            // If fallback signalled to stop.
                            return false;

                        added = true;
                    }
                }

                if (!added) {
                    cnt++;

                    if (cnt == RETRY_CNT)
                        throw e;

                    U.sleep(RETRY_DELAY);
                }

                if (!F.exist(nodes, new P1<GridNode>() {
                    @Override public boolean apply(GridNode e) {
                        return !leftIds.contains(e.id());
                    }
                })) {
                    if (log.isDebugEnabled())
                        log.debug("Message will not be sent because all nodes left topology [msg=" + msg + ", nodes=" +
                            U.toShortString(nodes) + ']');

                    return false;
                }

                if (log.isDebugEnabled())
                    log.debug("Message send will be retried [msg=" + msg + ", nodes=" + U.toShortString(nodes) +
                        ", leftIds=" + leftIds + ']');
            }
        }

        if (log.isDebugEnabled())
            log.debug("Sent cache message [msg=" + msg + ", node=" + U.toShortString(nodes) + ']');

        return true;
    }

    /**
     * Sends communication message.
     *
     * @param nodeId ID of node to send the message to.
     * @param msg Message to send.
     * @throws GridException If sending failed.
     */
    public void send(UUID nodeId, GridCacheMessage<K, V> msg) throws GridException {
        GridNode n = cctx.discovery().node(nodeId);

        if (n == null)
            throw new GridTopologyException("Failed to send message because node left grid [node=" + n + ", msg=" +
                msg + ']');

        send(n, msg);
    }

    /**
     * @param node Destination node.
     * @param topic Topic to send the message to.
     * @param msgId Ordered message ID.
     * @param msg Message to send.
     * @param timeout Timeout to keep a message on receiving queue.
     * @throws GridException Thrown in case of any errors.
     */
    public void sendOrderedMessage(GridNode node, String topic, long msgId, GridCacheMessage<K, V> msg,
        long timeout) throws GridException {
        onSend(msg);

        int cnt = 0;

        while (cnt <= RETRY_CNT) {
            try {
                cnt++;

                cctx.gridIO().sendOrderedMessage(node, topic, msgId, msg, SYSTEM_POOL, timeout);

                if (log.isDebugEnabled())
                    log.debug("Sent ordered cache message [topic=" + topic + ", msg=" + msg +
                        ", nodeId=" + node.id() + ']');

                return;
            }
            catch (GridException e) {
                if (cctx.discovery().node(node.id()) == null)
                    throw new GridTopologyException("Node left grid while sending message: " + msg, e);

                if (cnt == RETRY_CNT)
                    throw e;
                else if (log.isDebugEnabled())
                    log.debug("Failed to send message to node (will retry): " + node.id());
            }

            U.sleep(RETRY_DELAY);
        }
    }

    /**
     * @param topic Message topic.
     * @param nodeId Node ID.
     * @return Next ordered message ID.
     */
    public long messageId(String topic, UUID nodeId) {
        return cctx.gridIO().getNextMessageId(topic, nodeId);
    }

    /**
     * @return ID that auto-grows based on local counter and counters received
     *      from other nodes.
     */
    public long nextIoId() {
        return idGen.incrementAndGet();
    }

    /**
     * Adds message handler.
     *
     * @param type Type of message.
     * @param c Handler.
     */
    @SuppressWarnings({"unchecked"})
    public void addHandler(Class<?> type, GridInClosure2<UUID, ?> c) {
        if (clsHandlers.putIfAbsent(type, (GridInClosure2<UUID, Object>)c) != null)
            assert false : "Handler for class already registered [cls=" + type + ", old=" + clsHandlers.get(type) +
                ", new=" + c + ']';

        if (log != null && log.isDebugEnabled())
            log.debug("Registered cache communication handler [cacheName=" + cctx.name() + ", type=" + type +
                ", handler=" + c + ']');
    }

    /**
     * Adds ordered message handler.
     *
     * @param topic Topic.
     * @param c Handler.
     */
    @SuppressWarnings({"unchecked"})
    public void addOrderedHandler(String topic, GridInClosure2<UUID, ?> c) {
        if (orderedHandlers.putIfAbsent(topic, (GridInClosure2<UUID, Object>)c) == null) {
            cctx.gridIO().addMessageListener(topic, new OrderedMessageListener(topic));

            if (log != null && log.isDebugEnabled())
                log.debug("Registered ordered cache communication handler [topic=" + topic + ", handler=" + c + ']');
        }
        else if (log != null)
            U.warn(log, "Failed to registered ordered cache communication handler because it is already " +
                "registered for this topic [topic=" + topic + ", handler=" + c + ']');
    }

    /**
     * Removed ordered message handler.
     *
     * @param topic Topic.
     */
    @SuppressWarnings({"unchecked"})
    public void removeOrderedHandler(String topic) {
        if (orderedHandlers.remove(topic) != null) {
            cctx.gridIO().removeMessageListener(topic);

            if (log != null && log.isDebugEnabled())
                log.debug("Unregistered ordered cache communication handler for topic:" + topic);
        }
        else if (log != null)
            U.warn(log, "Failed to unregistered ordered cache communication handler because it was not found " +
                "for topic: " + topic);
    }

    /**
     * Registers newly arrived message.
     *
     * @param nodeId Node ID.
     * @param o Message.
     * @return {@code True} if message is not duplicate.
     */
    private boolean addMessage(UUID nodeId, Object o) {
        GridCacheMessage m = (GridCacheMessage)o;

        while (true) {
            long id = idGen.get();

            if (m.messageId() > id)
                // Auto-grow IDs.
                if (!idGen.compareAndSet(id, m.messageId()))
                    continue;

            break;
        }

        if (m instanceof GridCacheVersionable) {
            GridCacheVersion ver = ((GridCacheVersionable)m).version();

            assert ver != null : "Versionable message has null version: " + m;

            cctx.versions().onReceived(nodeId, ver);
        }

        boolean added = msgIds.add(new MessageId(m.messageId(), nodeId));

        if (!added && log.isDebugEnabled())
            log.debug("Received duplicate message (will ignore) [nodeId=" + nodeId + ", msg=" + o + ']');

        return added;
    }

    /**
     * @param nodeId Sender node ID.
     * @param msg Message.
     * @throws GridException If failed.
     */
    @SuppressWarnings( {"unchecked"})
    private void unmarshall(UUID nodeId, Object msg) throws GridException {
        if (msg instanceof GridCacheMessage) {
            GridCacheMessage<K, V> cacheMsg = (GridCacheMessage<K, V>)msg;

            GridDeploymentInfo bean = cacheMsg.deployInfo();

            if (bean != null) {
                cctx.deploy().p2pContext(nodeId, bean.classLoaderId(), bean.userVersion(),
                    bean.deployMode(), bean.participants());

                if (log.isDebugEnabled())
                    log.debug("Set P2P context [senderId=" + nodeId + ", msg=" + msg + ']');
            }

            cacheMsg.p2pUnmarshal(cctx, cctx.deploy().globalLoader());
        }
    }

    /**
     * Cache message ID.
     */
    private class MessageId implements Comparable<MessageId> {
        /** Message ID. */
        private long msgId;

        /** Node ID. */
        private UUID nodeId;

        /**
         * @param msgId Message ID.
         * @param nodeId Node ID.
         */
        private MessageId(long msgId, UUID nodeId) {
            this.msgId = msgId;
            this.nodeId = nodeId;
        }

        /** {@inheritDoc} */
        @Override public int compareTo(MessageId m) {
            if (m.msgId == msgId)
                return m.nodeId.compareTo(nodeId);

            return msgId < m.msgId ? -1 : 1;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object obj) {
            if (obj == this)
                return true;

            @SuppressWarnings({"unchecked"})
            MessageId other = (MessageId)obj;

            return msgId == other.msgId && nodeId.equals(other.nodeId);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return 31 * ((int)(msgId ^ (msgId >>> 32))) + nodeId.hashCode();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MessageId.class, this);
        }
    }

    /** Ordered message listener. */
    @SuppressWarnings({"deprecation"})
    private class OrderedMessageListener implements GridMessageListener {
        /** */
        private String topic;

        /**
         * @param topic Topic.
         */
        OrderedMessageListener(String topic) {
            this.topic = topic;
        }

        @SuppressWarnings({"CatchGenericClass"})
        @Override public void onMessage(final UUID nodeId, final Object msg) {
            // Check for duplicates.
            if (!addMessage(nodeId, msg))
                return;

            rw.readLock().lock();

            try {
                if (stopping) {
                    if (log.isDebugEnabled())
                        log.debug("Received cache ordered message while stopping (will ignore) [nodeId=" + nodeId +
                            ", msg=" + msg + ']');

                    return;
                }

                if (CU.isPreloaderMessage(msg))
                    processOrdered(nodeId, msg);
                else {
                    GridFuture<?> startFut = cctx.preloader().startFuture();

                    if (startFut.isDone())
                        processOrdered(nodeId, msg);
                    else
                        // Don't hold this thread waiting for preloading to complete.
                        startFut.listenAsync(new CI1<GridFuture<?>>() {
                            @Override public void apply(GridFuture<?> f) {
                                rw.readLock().lock();

                                try {
                                    if (stopping) {
                                        if (log.isDebugEnabled())
                                            log.debug("Received cache ordered message while stopping (will ignore) " +
                                                "[nodeId=" + nodeId + ", msg=" + msg + ']');

                                        return;
                                    }

                                    f.get();

                                    processOrdered(nodeId, msg);
                                }
                                catch (GridException e) {
                                    // Log once.
                                    if (startErr.compareAndSet(false, true))
                                        U.error(log, "Failed to complete preload start future (will ignore message) " +
                                            "[fut=" + f + ", nodeId=" + nodeId + ", msg=" + msg + ']', e);
                                }
                                finally {
                                    rw.readLock().unlock();
                                }
                            }
                        });
                }

            }
            catch (Throwable e) {
                U.error(log, "Failed processing ordered message [senderId=" + nodeId + ']', e);
            }
            finally {
                rw.readLock().unlock();
            }
        }

        /**
         * @param nodeId Node ID.
         * @param msg Message ID.
         */
        private void processOrdered(UUID nodeId, Object msg) {
            try {
                GridInClosure2<UUID, Object> c = orderedHandlers.get(topic);

                if (c == null) {
                    U.warn(log, "Received ordered message without registered handler (will ignore) [topic=" + topic +
                        ", msg=" + msg + ", nodeId=" + nodeId + ']');

                    return;
                }

                if (log.isDebugEnabled())
                    log.debug("Received cache ordered message [nodeId=" + nodeId + ", msg=" + msg + ']');

                unmarshall(nodeId, msg);

                GridNode n = cctx.discovery().node(nodeId);

                // Start clean.
                CU.resetTxContext(cctx);

                GridStopwatch watch = W.stopwatch(msg.getClass().getSimpleName());

                try {
                    // Pass the same ID object as in the node, so we don't end up
                    // storing a bunch of new UUIDs in each cache entry.
                    c.apply(n == null ? nodeId : n.id(), msg);
                }
                finally {
                    watch.stop();
                }

                if (log.isDebugEnabled())
                    log.debug("Finished processing cache ordered message [nodeId=" + nodeId + ", msg=" + msg + ']');
            }
            catch (Throwable e) {
                U.error(log, "Failed processing ordered message [senderId=" + nodeId + ']', e);
            }
            finally {
                // Clear thread-local tx contexts.
                CU.resetTxContext(cctx);

                // Unwind eviction notifications.
                CU.unwindEvicts(cctx);

                // Unwind event queue after every cache message.
                cctx.events().unwind();
            }
        }
    }
}
