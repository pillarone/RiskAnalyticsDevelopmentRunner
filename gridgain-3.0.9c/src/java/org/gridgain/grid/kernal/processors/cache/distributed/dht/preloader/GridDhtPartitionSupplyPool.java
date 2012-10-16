// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.stopwatch.*;
import org.gridgain.grid.util.worker.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.kernal.processors.cache.distributed.dht.GridDhtPartitionState.*;

/**
 * Thread pool for supplying partitions to demanding nodes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtPartitionSupplyPool<K, V> {
    /** */
    private final GridCacheContext<K, V> cctx;

    /** */
    private final GridLogger log;

    /** */
    private final ReadWriteLock busyLock;

    /** */
    private final GridDhtPartitionTopology<K, V> top;

    /** */
    private final Collection<SupplyWorker> workers = new LinkedList<SupplyWorker>();

    /** */
    private final LinkedBlockingDeque<DemandMessage<K, V>> queue = new LinkedBlockingDeque<DemandMessage<K, V>>();

    /**
     * @param cctx Cache context.
     * @param busyLock Shutdown lock.
     */
    public GridDhtPartitionSupplyPool(GridCacheContext<K, V> cctx, ReadWriteLock busyLock) {
        assert cctx != null;
        assert busyLock != null;

        this.cctx = cctx;
        this.busyLock = busyLock;

        log = cctx.logger(getClass());

        top = cctx.dht().topology();

        int poolSize = cctx.preloadEnabled() ? cctx.config().getPreloadThreadPoolSize() : 0;

        for (int i = 0; i < poolSize; i++)
            workers.add(new SupplyWorker());
    }

    /**
     *
     */
    void start() {
        cctx.io().addHandler(GridDhtPartitionDemandMessage.class, new CI2<UUID, GridDhtPartitionDemandMessage<K, V>>() {
            @Override public void apply(UUID id, GridDhtPartitionDemandMessage<K, V> m) {
                processDemandMessage(id, m);
            }
        });

        for (SupplyWorker w : workers)
            new GridThread(cctx.gridName(), "preloader-supply-worker", w).start();
    }

    /**
     *
     */
    void stop() {
        U.cancel(workers);
        U.join(workers, log);
    }

    /**
     * @return Size of this thread pool.
     */
    int poolSize() {
        return cctx.config().getPreloadThreadPoolSize();
    }

    /**
     * @return {@code true} if entered to busy state.
     */
    private boolean enterBusy() {
        if (busyLock.readLock().tryLock())
            return true;

        if (log.isDebugEnabled())
            log.debug("Failed to enter to busy state (supplier is stopping): " + cctx.nodeId());

        return false;
    }

    /**
     * @param nodeId Sender node ID.
     * @param d Message.
     */
    private void processDemandMessage(UUID nodeId, GridDhtPartitionDemandMessage<K, V> d) {
        if (!enterBusy())
            return;

        try {
            if (cctx.preloadEnabled()) {
                if (log.isDebugEnabled())
                    log.debug("Received partition demand [node=" + nodeId + ", demand=" + d + ']');

                queue.offer(new DemandMessage<K, V>(nodeId, d));
            }
            else
                U.warn(log, "Received partition demand message when preloading is disabled (will ignore): " + d);
        }
        finally {
            leaveBusy();
        }
    }

    /**
     *
     */
    private void leaveBusy() {
        busyLock.readLock().unlock();
    }

    /**
     * Supply work.
     */
    private class SupplyWorker extends GridWorker {
        /** Partition supply watch. */
        private final GridStopwatch watch = W.stopwatch("PARTITION_SUPPLY");

        /**
         * Default constructor.
         */
        private SupplyWorker() {
            super(cctx.gridName(), "preloader-supply-worker", log);
        }

        /** {@inheritDoc} */
        @Override protected void cleanup() {
            watch.stop();
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException, GridInterruptedException {
            while (!isCancelled()) {
                watch.step("DEMAND_WAIT");

                DemandMessage<K, V> msg = queue.take();

                watch.step("DEMAND_RECEIVED");

                GridRichNode node = cctx.discovery().richNode(msg.senderId());

                if (node == null) {
                    if (log.isDebugEnabled())
                        log.debug("Received message from non-existing node (will ignore): " + msg);

                    continue;
                }

                GridDhtPartitionDemandMessage<K, V> d = msg.message();

                GridDhtPartitionSupplyMessage<K, V> s = new GridDhtPartitionSupplyMessage<K, V>(d.workerId(),
                    d.updateSequence());

                try {
                    for (Integer part : d.partitions()) {
                        GridDhtLocalPartition<K, V> loc = top.localPartition(part,  false);

                        if (loc == null || loc.state() != OWNING || !loc.reserve()) {
                            // Reply with partition of "-1" to let sender know that
                            // this node is no longer an owner.
                            s.missed(part);

                            if (log.isDebugEnabled())
                                log.debug("Requested partition is not owned by local node [part=" + part +
                                    ", demander=" + msg.senderId() + ']');

                            continue;
                        }

                        try {
                            for (GridCacheEntryEx<K, V> e : loc.entries()) {
                                if (!cctx.belongs(part, node)) {
                                    // Demander no longer needs this partition, so we send '-1' partition and move on.
                                    s.missed(part);

                                    watch.step("SUPPLY_INVALID_SENT");

                                    if (log.isDebugEnabled())
                                        log.debug("Demanding node does not need requested partition [part=" + part +
                                            ", nodeId=" + msg.senderId() + ']');

                                    continue;
                                }

                                if (s.messageSize() >= cctx.config().getPreloadBatchSize()) {
                                    if (!reply(node, d, s))
                                        // Demander left grid.
                                        break;

                                    watch.step("SUPPLY_SENT");

                                    s = new GridDhtPartitionSupplyMessage<K, V>(d.workerId(), d.updateSequence());
                                }

                                GridCacheEntryInfo<K, V> info = e.info();

                                if (info != null && info.value() != null)
                                    s.addEntry(part, info, cctx);
                            }

                            // Mark as last supply message.
                            s.last(part);

                            watch.step("SUPPLY_LAST_SENT");
                        }
                        finally {
                            loc.release();
                        }
                    }

                    reply(node, d, s);
                }
                catch (GridInterruptedException e) {
                    throw e;
                }
                catch (GridException e) {
                    log.error("Failed to send partition supply message to node: " + node.id(), e);
                }
            }
        }

        /**
         * @param n Node.
         * @param d Demand message.
         * @param s Supply message.
         * @return {@code True} if message was sent, {@code false} if recipient left grid.
         * @throws GridException If failed.
         */
        private boolean reply(GridNode n, GridDhtPartitionDemandMessage<K, V> d, GridDhtPartitionSupplyMessage<K, V> s)
            throws GridException {
            try {
                if (log.isDebugEnabled())
                    log.debug("Replying to partition demand [node=" + n.id() + ", demand=" + d + ", supply=" + s + ']');

                cctx.io().sendOrderedMessage(n, d.topic(), cctx.io().messageId(d.topic(), n.id()), s, d.timeout());

                return true;
            }
            catch (GridTopologyException ignore) {
                if (log.isDebugEnabled())
                    log.debug("Failed to send partition supply message because node left grid: " + n.id());

                return false;
            }
        }
    }

    /**
     * Supply message wrapper.
     */
    private static class DemandMessage<K, V> extends GridTuple2<UUID, GridDhtPartitionDemandMessage<K, V>> {
        /**
         * @param senderId Sender ID.
         * @param msg Message.
         */
        DemandMessage(UUID senderId, GridDhtPartitionDemandMessage<K, V> msg) {
            super(senderId, msg);
        }

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public DemandMessage() {
            // No-op.
        }

        /**
         * @return Sender ID.
         */
        UUID senderId() {
            return get1();
        }

        /**
         * @return Message.
         */
        public GridDhtPartitionDemandMessage<K, V> message() {
            return get2();
        }
    }
}
