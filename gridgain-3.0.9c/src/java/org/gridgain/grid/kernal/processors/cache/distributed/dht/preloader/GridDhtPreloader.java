// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.cache.GridCachePreloadMode.*;

/**
 * DHT cache preloader.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtPreloader<K, V> extends GridCachePreloaderAdapter<K, V> {
    /** Exchange history size. */
    private static final int EXCHANGE_HISTORY_SIZE = 1000;

    /** */
    private final GridDhtPartitionTopology<K, V> top;

    /** Partition map futures. */
    private final ExchangeFutureSet exchFuts = new ExchangeFutureSet();

    /** Force key futures. */
    private final ConcurrentHashMap<GridUuid, GridDhtForceKeysFuture<K, V>> forceKeyFuts =
        new ConcurrentHashMap<GridUuid, GridDhtForceKeysFuture<K, V>>();

    /** Partition suppliers. */
    private GridDhtPartitionSupplyPool<K, V> supplyPool;

    /** Partition demanders. */
    private GridDhtPartitionDemandPool<K, V> demandPool;

    /** Start future. */
    private final GridFutureAdapter<?> startFut;

    /** Busy lock to prevent activities from accessing exchanger while it's stopping. */
    private final ReadWriteLock busyLock = new ReentrantReadWriteLock();

    /** Discovery listener. */
    private final GridLocalEventListener discoLsnr = new GridLocalEventListener() {
        @Override public void onEvent(GridEvent evt) {
            if (!enterBusy())
                return;

            try {
                GridRichNode loc = cctx.localNode();

                GridDiscoveryEvent e = (GridDiscoveryEvent)evt;

                assert e.type() == EVT_NODE_JOINED || e.type() == EVT_NODE_LEFT || e.type() == EVT_NODE_FAILED;

                GridNodeShadow n = e.shadow();

                assert !loc.id().equals(n.id());

                GridDhtPartitionExchangeId exchId = exchangeId(n.id(), n.order(), e.type(), e.timestamp());

                // Start exchange process.
                GridDhtPartitionsExchangeFuture<K, V> exchFut = exchangeFuture(exchId, e);

                if (e.type() == EVT_NODE_LEFT || e.type() == EVT_NODE_FAILED) {
                    assert cctx.discovery().node(n.id()) == null;

                    // Remove mapping for non-existing node.
                    top.remove(n.id());

                    for (GridDhtPartitionsExchangeFuture<K, V> f : exchFuts.values())
                        f.onNodeLeft(n.id(), exchFut);
                }

                for (GridDhtForceKeysFuture<K, V> f : forceKeyFuts.values())
                    f.onDiscoveryEvent(e);

                if (log.isDebugEnabled())
                    log.debug("Discovery event (will start exchange): " + exchId);

                // Event callback - without this callback future will never complete.
                exchFut.onEvent(exchId, e);

                demandPool.onDiscoveryEvent(n.id(), exchFut);
            }
            finally {
                leaveBusy();
            }
        }
    };

    /**
     * @param cctx Cache context.
     */
    public GridDhtPreloader(GridCacheContext<K, V> cctx) {
        super(cctx);

        top = cctx.dht().topology();

        startFut = new GridFutureAdapter<Object>(cctx.kernalContext());
    }

    /** {@inheritDoc} */
    @Override public void start() {
        if (log.isDebugEnabled())
            log.debug("Starting DHT preloader ...");

        cctx.io().addHandler(GridDhtPartitionsSingleMessage.class,
            new MessageHandler<GridDhtPartitionsSingleMessage<K, V>>() {
                @Override public void onMessage(GridRichNode node, GridDhtPartitionsSingleMessage<K, V> msg) {
                    processSinglePartitionUpdate(node, msg);
                }
            });

        cctx.io().addHandler(GridDhtPartitionsFullMessage.class,
            new MessageHandler<GridDhtPartitionsFullMessage<K, V>>() {
                @Override public void onMessage(GridRichNode node, GridDhtPartitionsFullMessage<K, V> msg) {
                    processFullPartitionUpdate(node, msg);
                }
            });

        cctx.io().addHandler(GridDhtPartitionsSingleRequest.class,
            new MessageHandler<GridDhtPartitionsSingleRequest<K, V>>() {
                @Override public void onMessage(GridRichNode node, GridDhtPartitionsSingleRequest<K, V> msg) {
                    processSinglePartitionRequest(node, msg);
                }
            });

        cctx.io().addHandler(GridDhtForceKeysRequest.class,
            new MessageHandler<GridDhtForceKeysRequest<K, V>>() {
                @Override public void onMessage(GridRichNode node, GridDhtForceKeysRequest<K, V> msg) {
                    processForceKeysRequest(node, msg);
                }
            });

        cctx.io().addHandler(GridDhtForceKeysResponse.class,
            new MessageHandler<GridDhtForceKeysResponse<K, V>>() {
                @Override public void onMessage(GridRichNode node, GridDhtForceKeysResponse<K, V> msg) {
                    processForceKeyResponse(node, msg);
                }
            });

        supplyPool = new GridDhtPartitionSupplyPool<K, V>(cctx, busyLock);
        demandPool = new GridDhtPartitionDemandPool<K, V>(cctx, busyLock);

        cctx.events().addListener(discoLsnr, EVT_NODE_JOINED, EVT_NODE_LEFT, EVT_NODE_FAILED);
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart() throws GridException {
        if (log.isDebugEnabled())
            log.debug("DHT preloader onKernalStart callback.");

        GridNode loc = cctx.localNode();

        long startTime = loc.metrics().getStartTime();

        assert startTime > 0;

        GridDhtPartitionExchangeId exchId = exchangeId(loc.id(), loc.order(), EVT_NODE_JOINED,
            loc.metrics().getStartTime());

        // Generate dummy discovery event for local node joining.
        GridDiscoveryEvent discoEvt = new GridDiscoveryEvent(loc.id(), "Local node joined.", EVT_NODE_JOINED, loc.id());

        discoEvt.shadow(cctx.discovery().shadow(loc));

        GridDhtPartitionsExchangeFuture<K, V> fut = exchangeFuture(exchId, discoEvt);

        supplyPool.start();
        demandPool.start(fut);

        if (log.isDebugEnabled())
            log.debug("Beginning to wait on exchange future: " + fut);

        try {
            fut.get(cctx.preloadExchangeTimeout());

            startFut.onDone();
        }
        catch (GridFutureTimeoutException e) {
            GridException err = new GridException("Timed out waiting for exchange future: " + fut, e);

            startFut.onDone(err);

            throw err;
        }

        if (log.isDebugEnabled())
            log.debug("Finished waiting on exchange: " + fut.exchangeId());

        if (cctx.config().getPreloadMode() == SYNC) {
            long start = System.currentTimeMillis();

            U.log(log, "Starting preloading in SYNC mode ...");

            demandPool.syncFuture().get();

            U.log(log, "Completed preloading in SYNC mode in " + (System.currentTimeMillis() - start) + "ms.");
        }
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop() {
        if (log.isDebugEnabled())
            log.debug("DHT preloader onKernalStop callback.");

        cctx.events().removeListener(discoLsnr);

        // Acquire write busy lock.
        busyLock.writeLock().lock();

        // Finish all exchange futures.
        for (GridDhtPartitionsExchangeFuture<K, V> f : exchFuts.values())
            f.onDone(new GridInterruptedException("Grid is stopping: " + cctx.gridName()));

        supplyPool.stop();
        demandPool.stop();
    }

    /**
     * @return Start future.
     */
    @Override public GridFuture<?> startFuture() {
        return startFut;
    }

    /**
     * @return Exchange futures.
     */
    @SuppressWarnings( {"unchecked", "RedundantCast"})
    public List<GridFuture<?>> exchangeFutures() {
        return (List<GridFuture<?>>)(List)exchFuts.values();
    }

    /**
     * @return {@code true} if entered to busy state.
     */
    private boolean enterBusy() {
        if (busyLock.readLock().tryLock())
            return true;

        if (log.isDebugEnabled())
            log.debug("Failed to enter busy state on node (exchanger is stopping): " + cctx.nodeId());

        return false;
    }

    /**
     *
     */
    private void leaveBusy() {
        busyLock.readLock().unlock();
    }

    /**
     * @param node Node.
     * @param msg Message.
     */
    private void processForceKeysRequest(GridNode node, GridDhtForceKeysRequest<K, V> msg) {
        if (!enterBusy())
            return;

        try {
            GridNode loc = cctx.localNode();

            GridDhtForceKeysResponse<K, V> res = new GridDhtForceKeysResponse<K, V>(msg.futureId(), msg.miniId());

            for (K k : msg.keys()) {
                int p = cctx.partition(k);

                GridDhtLocalPartition<K, V> locPart = top.localPartition(p, false);

                // If this node is no longer an owner.
                if (locPart == null && !top.owners(p).contains(loc))
                    res.addMissed(k);

                GridCacheEntryEx<K, V> entry = cctx.dht().peekEx(k);

                // If entry is null, then local partition may have left
                // after the message was received. In that case, we are
                // confident that primary node knows of any changes to the key.
                if (entry != null) {
                    GridCacheEntryInfo<K, V> info = entry.info();

                    if (info != null)
                        res.addInfo(entry.info());
                }
                else if (log.isDebugEnabled())
                    log.debug("Key is not present in DHT cache: " + k);
            }

            if (log.isDebugEnabled())
                log.debug("Sending force key response [node=" + node.id() + ", res=" + res + ']');

            cctx.io().send(node, res);
        }
        catch (GridTopologyException ignore) {
            if (log.isDebugEnabled())
                log.debug("Received force key request form failed node (will ignore) [nodeId=" + node.id() +
                    ", req=" + msg + ']');
        }
        catch (GridException e) {
            U.error(log, "Failed to reply to force key request [nodeId=" + node.id() + ", req=" + msg + ']', e);
        }
        finally {
            leaveBusy();
        }
    }

    /**
     * @param node Node.
     * @param msg Message.
     */
    private void processForceKeyResponse(GridNode node, GridDhtForceKeysResponse<K, V> msg) {
        if (!enterBusy())
            return;

        try {
            GridDhtForceKeysFuture<K, V> f = forceKeyFuts.get(msg.futureId());

            if (f != null) {
                f.onResult(node.id(), msg);
            }
            else if (log.isDebugEnabled())
                log.debug("Receive force key response for unknown future (is it duplicate?) [nodeId=" + node.id() +
                    ", res=" + msg + ']');
        }
        finally {
            leaveBusy();
        }
    }

    /**
     * @param node Node ID.
     * @param msg Message.
     */
    private void processSinglePartitionRequest(GridNode node, GridDhtPartitionsSingleRequest<K, V> msg) {
        if (!enterBusy())
            return;

        try {
            try {
                sendLocalPartitions(node, msg.exchangeId());
            }
            catch (GridException e) {
                U.error(log, "Failed to send local partition map to node [nodeId=" + node.id() + ", exchId=" +
                    msg.exchangeId() + ']', e);
            }
        }
        finally {
            leaveBusy();
        }
    }

    /**
     * @param node Node.
     * @param msg Message.
     */
    private void processFullPartitionUpdate(GridNode node, GridDhtPartitionsFullMessage<K, V> msg) {
        if (!enterBusy())
            return;

        try {
            if (msg.exchangeId() == null)
                top.update(null, msg.partitions());
            else
                exchangeFuture(msg.exchangeId(), null).onReceive(node.id(), msg);
        }
        finally {
            leaveBusy();
        }
    }

    /**
     * @param node Node ID.
     * @param msg Message.
     */
    private void processSinglePartitionUpdate(GridNode node, GridDhtPartitionsSingleMessage<K, V> msg) {
        if (!enterBusy())
            return;

        try {
            if (msg.exchangeId() == null)
                top.update(null, msg.partitions());
            else
                exchangeFuture(msg.exchangeId(), null).onReceive(node.id(), msg);
        }
        finally {
            leaveBusy();
        }
    }

    /**
     * Partition refresh callback.
     *
     * @throws GridInterruptedException If interrupted.
     */
    void refreshPartitions() throws GridInterruptedException {
        GridNode oldest = CU.oldest(CU.allNodes(cctx));

        if (log.isDebugEnabled())
            log.debug("Refreshing partitions [oldest=" + oldest.id() + ", loc=" + cctx.nodeId() + ']');

        Collection<GridRichNode> rmts = null;

        try {
            // If this is the oldest node.
            if (oldest.id().equals(cctx.nodeId())) {
                rmts = CU.remoteNodes(cctx);

                sendAllPartitions(rmts, top.partitionMap(true));
            }
            else
                sendLocalPartitions(oldest, null);
        }
        catch (GridInterruptedException e) {
            throw e;
        }
        catch (GridException e) {
            U.error(log, "Failed to refresh partition map [oldest=" + oldest.id() + ", rmts=" + U.nodeIds(rmts) +
                ", loc=" + cctx.nodeId() + ']', e);
        }
    }

    /**
     * @param nodes Nodes.
     * @param map Partition map.
     * @return {@code True} if message was sent, {@code false} if node left grid.
     * @throws GridException If failed.
     */
    private boolean sendAllPartitions(Collection<? extends GridNode> nodes, GridDhtPartitionFullMap map)
        throws GridException {
        GridDhtPartitionsFullMessage<K, V> m = new GridDhtPartitionsFullMessage<K, V>(null, map);

        if (log.isDebugEnabled())
            log.debug("Sending all partitions [nodeIds=" + U.nodeIds(nodes) + ", msg=" + m + ']');

        cctx.io().safeSend(nodes, m, null);

        return true;
    }

    /**
     * @param node Node.
     * @param id ID.
     * @return {@code True} if message was sent, {@code false} if node left grid.
     * @throws GridException If failed.
     */
    private boolean sendLocalPartitions(GridNode node, @Nullable GridDhtPartitionExchangeId id)
        throws GridException {
        GridDhtPartitionsSingleMessage<K, V> m = new GridDhtPartitionsSingleMessage<K, V>(id, top.localPartitionMap());

        if (log.isDebugEnabled())
            log.debug("Sending local partitions [nodeId=" + node.id() + ", msg=" + m + ']');

        try {
            cctx.io().send(node, m);

            return true;
        }
        catch (GridTopologyException ignore) {
            if (log.isDebugEnabled())
                log.debug("Failed to send partition update to node because it left grid (will ignore) [node=" +
                    node.id() + ", msg=" + m + ']');

            return false;
        }
    }

    /**
     * @param nodes Nodes.
     * @throws GridException If failed.
     */
    private void sendLocalPartitions(Collection<GridNode> nodes) throws GridException {
        GridDhtPartitionsSingleMessage<K, V> m = new GridDhtPartitionsSingleMessage<K, V>(null, top.localPartitionMap());

        if (log.isDebugEnabled())
            log.debug("Sending local partitions [nodeIds=" + F.viewReadOnly(nodes, F.node2id()) + ", msg=" + m + ']');

        cctx.io().safeSend(nodes, m, null);
    }

    /**
     * @param nodeId Cause node ID.
     * @param order Cause node order.
     * @param evt Event type.
     * @param timestamp Event timestamp.
     * @return ActivityFuture id.
     */
    private GridDhtPartitionExchangeId exchangeId(UUID nodeId, long order, int evt, long timestamp) {
        return new GridDhtPartitionExchangeId(nodeId, evt, order, timestamp);
    }

    /**
     * @param exchId Exchange ID.
     * @param discoEvt Discovery event.
     * @return Exchange future.
     */
    GridDhtPartitionsExchangeFuture<K, V> exchangeFuture(GridDhtPartitionExchangeId exchId,
        @Nullable GridDiscoveryEvent discoEvt) {
        GridDhtPartitionsExchangeFuture<K, V> fut;

        GridDhtPartitionsExchangeFuture<K, V> old = exchFuts.addx(
            fut = new GridDhtPartitionsExchangeFuture<K, V>(cctx, busyLock, exchId));

        if (old != null)
            fut = old;

        if (discoEvt != null)
            fut.onEvent(exchId, discoEvt);

        return fut;
    }

    /**
     * @param keys Keys to request.
     * @return Future for request.
     */
    @SuppressWarnings( {"RedundantCast", "unchecked"})
    @Override public GridFuture<Object> request(Collection<? extends K> keys) {
        final GridDhtForceKeysFuture<K, V> fut = new GridDhtForceKeysFuture<K, V>(cctx, keys);

        forceKeyFuts.put(fut.futureId(), fut);

        fut.listenAsync(new CI1<GridFuture<Collection<K>>>() {
            @Override public void apply(GridFuture<Collection<K>> t) {
                forceKeyFuts.remove(fut.futureId());
            }
        });

        if (demandPool.syncFuture().isDone())
            fut.init();
        else
            demandPool.syncFuture().listenAsync(new CI1<GridFuture<?>>() {
                @Override public void apply(GridFuture<?> syncFut) {
                    fut.init();
                }
            });

        return (GridFuture<Object>)(GridDhtForceKeysFuture)fut;
    }

    /**
     *
     */
    private abstract class MessageHandler<M> extends GridInClosure2<UUID, M> {
        /** {@inheritDoc} */
        @Override public void apply(UUID nodeId, M msg) {
            GridRichNode node = cctx.node(nodeId);

            if (node == null) {
                if (log.isDebugEnabled())
                    log.debug("Received message from failed node [node=" + nodeId + ", msg=" + msg + ']');

                return;
            }

            if (log.isDebugEnabled())
                log.debug("Received message from node [node=" + nodeId + ", msg=" + msg + ']');

            onMessage(node , msg);
        }

        /**
         * @param node Node.
         * @param msg Message.
         */
        protected abstract void onMessage(GridRichNode node, M msg);
    }

    /**
     *
     */
    private class ExchangeFutureSet extends GridListSet<GridDhtPartitionsExchangeFuture<K, V>> {
        /**
         * Creates ordered, not strict list set.
         */
        private ExchangeFutureSet() {
            super(new Comparator<GridDhtPartitionsExchangeFuture<K, V>>() {
                @Override public int compare(
                    GridDhtPartitionsExchangeFuture<K, V> f1,
                    GridDhtPartitionsExchangeFuture<K, V> f2) {
                    long t1 = f1.exchangeId().timestamp();
                    long t2 = f2.exchangeId().timestamp();

                    assert t1 > 0;
                    assert t2 > 0;

                    // Reverse order.
                    return t1 < t2 ? 1 : t1 == t2 ? 0 : -1;
                }
            }, /*not strict*/false);
        }

        /**
         * @param fut Future to add.
         * @return {@code True} if added.
         */
        @Override public synchronized GridDhtPartitionsExchangeFuture<K, V> addx(
            GridDhtPartitionsExchangeFuture<K, V> fut) {
            GridDhtPartitionsExchangeFuture<K, V> cur = super.addx(fut);

            while (size() > EXCHANGE_HISTORY_SIZE)
                removeLast();

            // Return the value in the set.
            return cur == null ? fut : cur;
        }

        /**
         * @return Values.
         */
        @Override public synchronized List<GridDhtPartitionsExchangeFuture<K, V>> values() {
            return super.values();
        }

        /** {@inheritDoc} */
        @Override public synchronized String toString() {
            return S.toString(ExchangeFutureSet.class, this, super.toString());
        }
    }
}
