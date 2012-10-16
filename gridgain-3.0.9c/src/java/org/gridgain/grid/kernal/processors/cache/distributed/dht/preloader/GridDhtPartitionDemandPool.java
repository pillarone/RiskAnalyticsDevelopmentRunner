// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.stopwatch.*;
import org.gridgain.grid.util.tostring.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static java.util.concurrent.TimeUnit.*;
import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;
import static org.gridgain.grid.kernal.processors.cache.distributed.dht.GridDhtPartitionState.*;

/**
 * Thread pool for requesting partitions from other nodes
 * and populating local cache.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtPartitionDemandPool<K, V> {
    /** Dummy message to wake up a blocking queue if a node leaves. */
    private static final SupplyMessage DUMMY_TOP = new SupplyMessage();

    /** Dummy exchange to wake up a blocking queue if topology changes. */
    private static final GridDhtPartitionsExchangeFuture DUMMY_EXCH = new GridDhtPartitionsExchangeFuture();

    /** Pause to wait for topology change. */
    private static final long TOP_PAUSE = 1000;

    /** */
    private final GridCacheContext<K, V> cctx;

    /** */
    private final GridLogger log;

    /** */
    private final ReadWriteLock busyLock;

    /** */
    private final GridDhtPartitionTopology<K, V> top;

    /** */
    @GridToStringInclude
    private final Collection<DemandWorker> workers;

    /** Future for preload mode {@link GridCachePreloadMode#SYNC}. */
    @GridToStringInclude
    private SyncFuture syncFuture;

    /** Preload timeout. */
    private final AtomicLong timeout;

    /** */
    @GridToStringInclude
    private Collection<GridDhtPartitionsExchangeFuture<K, V>> exchFuts =
        new GridConcurrentHashSet<GridDhtPartitionsExchangeFuture<K,V>>();

    /** Current discovery event. */
    private final AtomicReference<GridDiscoveryEvent> discoEvt = new AtomicReference<GridDiscoveryEvent>();

    /** Demand worker busy counter. */
    private final AtomicInteger busyCntr;

    /** Last partition refresh. */
    private final AtomicLong lastRefresh = new AtomicLong(-1);

    /** */
    private int poolSize;

    /**
     * @param cctx Cache context.
     * @param busyLock Shutdown lock.
     */
    public GridDhtPartitionDemandPool(GridCacheContext<K, V> cctx, ReadWriteLock busyLock) {
        assert cctx != null;
        assert busyLock != null;

        this.cctx = cctx;
        this.busyLock = busyLock;

        log = cctx.logger(getClass());

        top = cctx.dht().topology();

        poolSize = cctx.preloadEnabled() ? cctx.config().getPreloadThreadPoolSize() : 1;

        busyCntr = new AtomicInteger(poolSize);

        workers = new ArrayList<DemandWorker>(poolSize);

        for (int i = 0; i < poolSize; i++)
            workers.add(new DemandWorker(i));

        syncFuture = new SyncFuture(workers);

        timeout = new AtomicLong(cctx.gridConfig().getNetworkTimeout());
    }

    /**
     * @param exchFut Exchange future for this node.
     */
    void start(GridDhtPartitionsExchangeFuture<K, V> exchFut) {
        assert exchFut.causeNodeId().equals(cctx.nodeId());

        for (DemandWorker w : workers)
            new GridThread(cctx.gridName(), "preloader-demand-worker", w).start();

        onDiscoveryEvent(cctx.nodeId(), exchFut);
    }

    /**
     *
     */
    void stop() {
        U.cancel(workers);

        if (log.isDebugEnabled())
            log.debug("Before joining on demand workers: " + workers);

        U.join(workers, log);

        if (log.isDebugEnabled())
            log.debug("After joining on demand workers: " + workers);
    }

    /**
     * @return Future for {@link GridCachePreloadMode#SYNC} mode.
     */
    GridFuture syncFuture() {
        return syncFuture;
    }

    /**
     * @return Size of this thread pool.
     */
    int poolSize() {
        return poolSize;
    }

    /**
     * @return {@code true} if entered to busy state.
     */
    private boolean enterBusy() {
        if (busyLock.readLock().tryLock())
            return true;

        if (log.isDebugEnabled())
            log.debug("Failed to enter to busy state (demander is stopping): " + cctx.nodeId());

        return false;
    }

    /**
     *
     */
    private void leaveBusy() {
        busyLock.readLock().unlock();
    }

    /**
     * @param type Type.
     * @param discoEvt Discovery event.
     */
    private void preloadEvent(int type, GridDiscoveryEvent discoEvt) {
        preloadEvent(-1, type, discoEvt);
    }

    /**
     * @param part Partition.
     * @param type Type.
     * @param discoEvt Discovery event.
     */
    private void preloadEvent(int part, int type, GridDiscoveryEvent discoEvt) {
        assert discoEvt != null;

        cctx.events().addPreloadEvent(part, type, discoEvt.shadow(), discoEvt.type(), discoEvt.timestamp());
    }

    /**
     * @return Dummy node-left message.
     */
    @SuppressWarnings( {"unchecked"})
    private SupplyMessage<K, V> dummyTopology() {
        return (SupplyMessage<K, V>)DUMMY_TOP;
    }

    /**
     * @param msg Message to check.
     * @return {@code True} if dummy message.
     */
    private boolean dummyTopology(SupplyMessage<K, V> msg) {
        return msg == DUMMY_TOP;
    }

    /**
     * @return Dummy node-left exchange.
     */
    @SuppressWarnings( {"unchecked"})
    private GridDhtPartitionsExchangeFuture<K, V> dummyExchange() {
        return (GridDhtPartitionsExchangeFuture<K, V>)DUMMY_EXCH;
    }

    /**
     * @param exch Exchange.
     * @return {@code True} if dummy exchange.
     */
    private boolean dummyExchange(GridDhtPartitionsExchangeFuture<K, V> exch) {
        return exch == DUMMY_EXCH;
    }

    /**
     * @return Current worker.
     */
    private GridWorker worker() {
        GridWorker w = GridWorkerGroup.instance(cctx.gridName()).currentWorker();

        assert w != null;

        return w;
    }

    /**
     * @param nodeId New node ID.
     * @param fut Exchange future.
     */
    void onDiscoveryEvent(UUID nodeId, GridDhtPartitionsExchangeFuture<K, V> fut) {
        if (!enterBusy())
            return;

        try {
            addFuture(fut);
        }
        finally {
            leaveBusy();
        }
    }

    /**
     * @param deque Deque to poll from.
     * @param time Time to wait.
     * @return Polled item.
     * @throws InterruptedException If interrupted.
     */
    @Nullable private <T> T poll(LinkedBlockingDeque<T> deque, long time) throws InterruptedException {
        beforeWait();

        return deque.poll(time, MILLISECONDS);
    }

    /**
     * @param deque Deque to poll from.
     * @return Polled item.
     * @throws InterruptedException If interrupted.
     */
    @Nullable private <T> T take(LinkedBlockingDeque<T> deque) throws InterruptedException {
        beforeWait();

        return deque.take();
    }

    /**
     * @param fut Future.
     * @return {@code True} if added.
     */
    boolean addFuture(GridDhtPartitionsExchangeFuture<K, V> fut) {
        if (fut.onAdded()) {
            boolean added = exchFuts.add(fut);

            assert added : "Failed to add exchange future [cacheName=" + cctx.name() + ", fut=" + fut + ']';

            fut.listenAsync(new CI1<GridFuture<?>>() {
                @Override public void apply(GridFuture<?> f) {
                    // Synchronize to ensure that all workers get futures in the same order.
                    synchronized (workers) {
                        for (DemandWorker w : workers)
                            w.exchangeFuture((GridDhtPartitionsExchangeFuture<K, V>)f);
                    }
                }
            });

            RealignLatch latch = new RealignLatch(workers.size(), fut);

            // Synchronize to ensure that all workers get futures in the same order.
            synchronized (workers) {
                for (DemandWorker w : workers) {
                    w.exchangeLatch(latch);

                    w.addMessage(dummyTopology());
                }
            }

            synchronized (workers) {
                for (DemandWorker w : workers)
                    w.exchangeFuture(dummyExchange());
            }

            if (log.isDebugEnabled())
                log.debug("Added exchange future to demand pool: " + fut);

            return true;
        }

        return false;
    }

    /**
     * There is currently a case where {@code interrupted}
     * flag on a thread gets flipped during stop which causes the pool to hang.  This check
     * will always make sure that interrupted flag gets reset before going into wait conditions.
     * <p>
     * The true fix should actually make sure that interrupted flag does not get reset or that
     * interrupted exception gets propagated. Until we find a real fix, this method should
     * always work to make sure that there is no hanging during stop.
     */
    private void beforeWait() {
        GridWorker w = worker();

        if (w != null && w.isCancelled())
            Thread.currentThread().interrupt();
    }


    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPartitionDemandPool.class, this);
    }

    /**
     *
     */
    private class DemandWorker extends GridWorker {
        /** Worker ID. */
        private int id = -1;

        /** Exchange queue. */
        private final LinkedBlockingDeque<RealignLatch> latchQ = new LinkedBlockingDeque<RealignLatch>();

        /** Future queue. */
        private final LinkedBlockingDeque<GridDhtPartitionsExchangeFuture<K, V>> futQ =
            new LinkedBlockingDeque<GridDhtPartitionsExchangeFuture<K, V>>();

        /** Message queue. */
        private final LinkedBlockingDeque<SupplyMessage<K, V>> msgQ =
            new LinkedBlockingDeque<SupplyMessage<K, V>>();

        /** Counter. */
        private long cntr;

        /** Pause latch. */
        private volatile CountDownLatch pauseLatch;

        /**
         * @param id Worker ID.
         */
        private DemandWorker(int id) {
            super(cctx.gridName(), "preloader-demand-worker", log);

            assert id >= 0;

            this.id = id;
        }

        /**
         * @param latch Exchange latch.
         */
        void exchangeLatch(RealignLatch latch) {
            assert latch != null;

            latchQ.offer(latch);

            CountDownLatch l = pauseLatch;

            if (l != null)
                l.countDown();
        }

        /**
         * @param fut Future.
         */
        void exchangeFuture(GridDhtPartitionsExchangeFuture<K, V> fut) {
            assert fut != null;

            futQ.offer(fut);
        }

        /**
         * @return {@code True} if topology changed.
         */
        private boolean topologyChanged() {
            return !latchQ.isEmpty();
        }

        /**
         * @param msg Message.
         */
        private void addMessage(SupplyMessage<K, V> msg) {
            if (!enterBusy())
                return;

            try {
                assert dummyTopology(msg) || msg.supply().workerId() == id;

                msgQ.offer(msg);
            }
            finally {
                leaveBusy();
            }
        }

        /**
         * @param timeout Timed out value.
         */
        private void growTimeout(long timeout) {
            long newTimeout = (long)(timeout * 1.5D);

            // Account for overflow.
            if (newTimeout < 0)
                newTimeout = Long.MAX_VALUE;

            // Grow by 50% only if another thread didn't do it already.
            if (GridDhtPartitionDemandPool.this.timeout.compareAndSet(timeout, newTimeout))
                U.warn(log, "Increased preloading message timeout [prevTimeout=" + timeout + ", newTimeout=" +
                    newTimeout + ']');
        }

        /**
         * @throws GridInterruptedException If interrupted.
         */
        private void initAll() throws GridInterruptedException {
            for (Iterator<GridDhtPartitionsExchangeFuture<K, V>> it = exchFuts.iterator(); it.hasNext();) {
                GridDhtPartitionsExchangeFuture<K, V> fut = it.next();

                fut.init();

                it.remove();
            }
        }

        /**
         * Refresh partitions.
         *
         * @throws GridInterruptedException If interrupted.
         */
        private void refreshPartitions() throws GridInterruptedException {
            long last = lastRefresh.get();

            long now = System.currentTimeMillis();

            if (last != -1 && now - last >= cctx.gridConfig().getNetworkTimeout()) {
                if (lastRefresh.compareAndSet(last, now))
                    cctx.dht().dhtPreloader().refreshPartitions();
            }
        }

        /**
         * @return {@code True} if successful.
         * @throws GridInterruptedException If interrupted.
         * @throws InterruptedException If interrupted.
         */
        @Nullable private Map<GridNode, GridDhtPartitionDemandMessage<K, V>> syncUp()
            throws GridInterruptedException, InterruptedException {

            GridStopwatch watch = W.stopwatch("PARTITION_DEMAND_SYNCUP");

            try {
                while (!isCancelled()) {
                    // Decrementing counter marks this thread as free.
                    if (busyCntr.decrementAndGet() == 0) {
                        // If not first preloading and no more topology events present,
                        // then we periodically refresh partition map.
                        if (latchQ.isEmpty() && syncFuture.isDone())
                            refreshPartitions();

                        GridDiscoveryEvent discoEvt = GridDhtPartitionDemandPool.this.discoEvt.getAndSet(null);

                        // Preload event notification.
                        if (discoEvt != null)
                            preloadEvent(EVT_CACHE_PRELOAD_STOPPED, discoEvt);
                    }

                    if (log.isDebugEnabled())
                        log.debug("Workers cancelled flag before waiting for latch [cancelled=" + isCancelled() +
                            ", interrupted=" + Thread.currentThread().isInterrupted() + ']');

                    // Wait until next topology change.
                    RealignLatch latch = poll(latchQ, cctx.gridConfig().getNetworkTimeout());

                    if (latch == null) {
                        busyCntr.incrementAndGet();

                        continue; // Main while loop.
                    }

                    watch.step("REALIGN_LATCH_RECEIVED");

                    try {
                        // Synchronize with other demand threads to make sure they all align at this step.
                        latch.realign1();

                        // Mark this thread as busy.
                        busyCntr.incrementAndGet();

                        watch.step("EXCHANGE_REALIGN_STEP1");

                        // After works line up and before preloading starts we initialize all futures.
                        initAll();

                        watch.step("FUTURES_INITIALIZED");

                        latch.realign2();

                        watch.step("EXCHANGE_REALIGN_STEP2");

                        if (log.isDebugEnabled())
                            log.debug("Before waiting for exchange futures [futs" +
                                F.view((cctx.dht().dhtPreloader()).exchangeFutures(), F.unfinishedFutures()) +
                                ", worker=" + worker() + ']');

                        GridDhtPartitionsExchangeFuture<K, V> exchFut = null;

                        while (!isCancelled() && (exchFut == null || dummyExchange(exchFut))) {
                            // Take next exchange future.
                            exchFut = take(futQ);

                            if (exchFut == null || dummyExchange(exchFut))
                                initAll();
                        }

                        if (isCancelled())
                            break;

                        if (log.isDebugEnabled())
                            log.debug("After waiting for exchange future [exchFut=" + exchFut + ", worker=" +
                                worker() + ']');

                        assert exchFut.isDone();

                        // Since future is done, we call 'get' just to check for errors.
                        exchFut.get();

                        watch.step("EXCHANGE_FUTURE_DONE");

                        // Just pick first worker to do this, so we don't
                        // invoke topology callback more than once for the
                        // same event.
                        if (id == 0)
                            top.afterExchange(exchFut.exchangeId());

                        latch.realign3();

                        // Preload event notification.
                        if (discoEvt.compareAndSet(null, exchFut.discoveryEvent()))
                            preloadEvent(EVT_CACHE_PRELOAD_STARTED, exchFut.discoveryEvent());

                        Map<GridNode, GridDhtPartitionDemandMessage<K, V>> assignments = latch.realign4(reassign());

                        watch.step("EXCHANGE_REALIGN_STEP3");

                        // Optimization.
                        if (latchQ.isEmpty())
                            return assignments;
                    }
                    catch (GridInterruptedException e) {
                        throw e;
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to wait for completion of partition map exchange " +
                            "(preloading will not start): " + latch, e);

                        // Wait for another topology change.
                        return null;
                    }
                }
            }
            finally {
                watch.stop();
            }

            return null;
        }

        /**
         * @return Assignments of partitions to nodes.
         */
        @Nullable private Map<GridNode, GridDhtPartitionDemandMessage<K, V>> reassign() {
            // No assignments for disabled preloader.
            if (!cctx.preloadEnabled())
                return null;

            int partCnt = cctx.partitions();

            GridRichNode loc = cctx.localNode();

            ConcurrentMap<GridNode, GridDhtPartitionDemandMessage<K, V>> map = null;

            // Only one thread handles reassignment.
            if (id == 0) {
                Collection<GridRichNode> allNodes = CU.allNodes(cctx);

                map = new ConcurrentHashMap<GridNode, GridDhtPartitionDemandMessage<K,V>>();

                for (int p = 0; p < partCnt && !isCancelled() && !topologyChanged(); p++) {
                    // If partition belongs to local node.
                    if (cctx.belongs(p, loc, allNodes)) {
                        GridDhtLocalPartition<K, V> part = top.localPartition(p, true);

                        assert part != null;
                        assert part.id() == p;

                        if (part.state() != MOVING) {
                            if (log.isDebugEnabled())
                                log.debug("Skipping partition assignment (state is not MOVING): " + part);

                            continue; // For.
                        }

                        Collection<GridNode> picked = pickedOwners(p);

                        if (picked.isEmpty())
                            top.own(part);
                        else {
                            GridNode n = F.first(picked);

                            GridDhtPartitionDemandMessage<K, V> msg = map.get(n);

                            if (msg == null)
                                msg = F.addIfAbsent(map, n,
                                    new GridDhtPartitionDemandMessage<K, V>(top.updateSequence()));

                            msg.addPartition(p);
                        }
                    }
                }
            }

            return map;
        }

        /**
         * @param parts Partitions to reassign.
         * @return Assignments of partitions to nodes.
         */
        @Nullable private Map<GridNode, GridDhtPartitionDemandMessage<K, V>> reassign(Iterable<Integer> parts) {
            GridRichNode loc = cctx.localNode();

            Map<GridNode, GridDhtPartitionDemandMessage<K, V>> map =
                new HashMap<GridNode, GridDhtPartitionDemandMessage<K,V>>();

            for (Integer p : parts) {
                // If partition belongs to local node.
                if (cctx.belongs(p, loc)) {
                    GridDhtLocalPartition<K, V> part = top.localPartition(p, true);

                    assert part != null;
                    assert part.id() == p;

                    if (part.state() != MOVING)
                        continue; // For.

                    Collection<GridNode> picked = pickedOwners(p);

                    assert !picked.isEmpty();

                    GridNode n = F.first(picked);

                    GridDhtPartitionDemandMessage<K, V> msg = map.get(n);

                    if (msg == null)
                        map.put(n, msg = new GridDhtPartitionDemandMessage<K, V>(top.updateSequence()));

                    msg.addPartition(p);
                }
            }

            return map;
        }

        /**
         * @param pick Node picked for preloading.
         * @param p Partition.
         * @param entry Preloaded entry.
         * @throws GridInterruptedException If interrupted.
         */
        private void preloadEntry(GridNode pick, int p, GridCacheEntryInfo<K, V> entry)
            throws GridInterruptedException {
            try {
                GridCacheEntryEx<K, V> cached = null;

                try {
                    cached = cctx.dht().entryEx(entry.key());

                    if (log.isDebugEnabled())
                        log.debug("Preloading key [key=" + entry.key() + ", part=" + p + ", node=" + pick.id() + ']');

                    if (!cached.initialValue(
                        entry.value(),
                        entry.valueBytes(),
                        entry.version(),
                        entry.ttl(),
                        entry.expireTime(),
                        entry.metrics())) {
                        if (log.isDebugEnabled())
                            log.debug("Preloading entry is already in cache (will ignore) [key=" + cached.key() +
                                ", part=" + p + ']');
                    }
                }
                catch (GridCacheEntryRemovedException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Entry has been concurrently removed while preloading (will ignore) [key=" +
                            cached.key() + ", part=" + p + ']');
                }
            }
            catch (GridInterruptedException e) {
                throw e;
            }
            catch (GridException e) {
                U.error(log, "Failed to cache preloaded entry [local=" + cctx.nodeId() + ", node=" + pick.id() +
                    ", key=" + entry.key() + ", part=" + p + ']', e);
            }
        }

        /**
         * @param p Partition.
         * @return Picked owners.
         */
        private Collection<GridNode> pickedOwners(int p) {
            Collection<GridRichNode> affNodes = cctx.affinity(p, CU.allNodes(cctx));

            int affCnt = affNodes.size();

            Collection<GridNode> rmts = remoteOwners(p);

            int rmtCnt = rmts.size();

            if (rmtCnt <= affCnt)
                return rmts;

            List<GridNode> sorted = new ArrayList<GridNode>(rmts);

            // Sort in descending order, so nodes with higher order will be first.
            Collections.sort(sorted, CU.nodeComparator(false));

            // Pick newest nodes.
            return sorted.subList(0, affCnt);
        }

        /**
         * @param p Partition.
         * @return Nodes owning this partition.
         */
        private Collection<GridNode> remoteOwners(int p) {
            return F.view(top.owners(p), cctx.remotes());
        }

        /**
         * @param idx Unique index for this topic.
         * @return Topic name for partition.
         */
        public String topic(long idx) {
            return TOPIC_CACHE.name(cctx.namexx(), "preloader#" + id, "idx#" + Long.toString(idx));
        }

        /**
         * @param node Node to demand from.
         * @param d Demand message.
         * @return Missed partitions.
         * @throws InterruptedException If interrupted.
         * @throws GridTopologyException If node left.
         * @throws GridException If failed to send message.
         */
        private Set<Integer> demandFromNode(GridNode node, GridDhtPartitionDemandMessage<K, V> d)
            throws InterruptedException, GridException {
            GridRichNode loc = cctx.localNode();

            cntr++;

            d.topic(topic(cntr));
            d.workerId(id);

            Set<Integer> missed = new HashSet<Integer>();

            Collection<Integer> remaining = new HashSet<Integer>(d.partitions());

            // Drain queue before processing a new node.
            drainQueue();

            if (isCancelled() || topologyChanged())
                return missed;

            cctx.io().addOrderedHandler(d.topic(), new CI2<UUID, GridDhtPartitionSupplyMessage<K, V>>() {
                @Override public void apply(UUID nodeId, GridDhtPartitionSupplyMessage<K, V> msg) {
                    addMessage(new SupplyMessage<K, V>(nodeId, msg));
                }
            });

            GridStopwatch watch = W.stopwatch("PARTITION_NODE_DEMAND");

            try {
                boolean retry;

                // DoWhile.
                // =======
                do {
                    retry = false;

                    long timeout = GridDhtPartitionDemandPool.this.timeout.get();

                    d.timeout(timeout);

                    if (log.isDebugEnabled())
                        log.debug("Sending demand message [node=" + node.id() + ", demand=" + d + ']');

                    // Send demand message.
                    cctx.io().send(node, d);

                    watch.step("PARTITION_DEMAND_SENT");

                    // While.
                    // =====
                    while (!isCancelled() && !topologyChanged()) {
                        SupplyMessage<K, V> s = poll(msgQ, timeout);

                        // If timed out.
                        if (s == null) {
                            if (msgQ.isEmpty()) { // Safety check.
                                U.warn(log, "Timed out waiting for partitions to load, will retry in " + timeout +
                                    "ms" + ']');

                                growTimeout(timeout);

                                // Resend message with larger timeout.
                                retry = true;

                                break; // While.
                            }
                            else
                                continue; // While.
                        }

                        // If topology changed.
                        if (dummyTopology(s)) {
                            if (topologyChanged())
                                break; // While.
                            else
                                continue; // While.
                        }

                        // Check that message was received from expected node.
                        if (!s.senderId().equals(node.id())) {
                            U.warn(log, "Received supply message from unexpected node [expectedId=" + node.id() +
                                ", rcvdId=" + s.senderId() + ", msg=" + s + ']');

                            continue; // While.
                        }

                        watch.step("SUPPLY_MSG_RCVD");

                        GridDhtPartitionSupplyMessage<K, V> supply = s.supply();

                        // Preload.
                        for (Map.Entry<Integer, Collection<GridCacheEntryInfo<K, V>>> e : supply.infos().entrySet()) {
                            int p = e.getKey();

                            if (cctx.belongs(p, loc)) {
                                GridDhtLocalPartition<K, V> part = top.localPartition(p, true);

                                assert part != null;

                                if (part.state() == MOVING) {
                                    boolean reserved = part.reserve();

                                    assert reserved : "Failed to reserve partition [gridName=" +
                                        cctx.gridName() + ", cacheName=" + cctx.namex() + ", part=" + part + ']';

                                    try {
                                        // Loop through all received entries and try to preload them.
                                        for (GridCacheEntryInfo<K, V> entry : e.getValue())
                                            preloadEntry(node, p, entry);

                                        watch.step("PRELOADED_ENTRIES");

                                        boolean last = supply.last().contains(p);

                                        // If message was last for this partition,
                                        // then we take ownership.
                                        if (last) {
                                            remaining.remove(p);

                                            top.own(part);

                                            if (log.isDebugEnabled())
                                                log.debug("Finished preloading partition: " + part);

                                            watch.step("LAST_PARTITION");

                                            preloadEvent(p, EVT_CACHE_PRELOAD_PART_LOADED, discoEvt.get());
                                        }
                                    }
                                    finally {
                                        part.release();
                                    }
                                }
                                else if (log.isDebugEnabled())
                                    log.debug("Skipping loading of partition (state is not MOVING): " + part);
                            }
                            else if (log.isDebugEnabled())
                                log.debug("Skipping loading of partition (it does not belong on current node): " + p);
                        }

                        remaining.removeAll(s.supply().missed());
                        missed.addAll(s.supply().missed());

                        if (remaining.isEmpty())
                            break; // While.
                    }
                }
                while (retry && !isCancelled() && !topologyChanged());

                return missed;
            }
            finally {
                cctx.io().removeOrderedHandler(d.topic());

                watch.stop();
            }
        }

        /**
         * @throws InterruptedException If interrupted.
         */
        private void drainQueue() throws InterruptedException {
            while (!msgQ.isEmpty()) {
                SupplyMessage<K, V> msg = msgQ.take();

                if (log.isDebugEnabled())
                    log.debug("Drained supply message: " + msg);
            }
        }

        /**
         * @throws InterruptedException If interrupted.
         */
        private void pause() throws InterruptedException {
            CountDownLatch l = pauseLatch = new CountDownLatch(1);

            l.await(TOP_PAUSE, MILLISECONDS);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException, GridInterruptedException {
            syncFuture.addWatch("PRELOAD_SYNC");

            while (!isCancelled()) {
                // Sync up all demand threads at this step.
                Map<GridNode, GridDhtPartitionDemandMessage<K, V>> assignments = syncUp();

                boolean resync = false;

                // While.
                // =====
                while (!isCancelled() && !topologyChanged() && !resync) {
                    // If preloader is disabled, assignments will be null.
                    if (assignments == null) {
                        syncFuture.onWorkerDone(this);

                        break;
                    }

                    Collection<Integer> missed = new HashSet<Integer>();

                    // For.
                    // ===
                    for (GridNode node : assignments.keySet()) {
                        if (topologyChanged() || isCancelled())
                            break; // For.

                        GridDhtPartitionDemandMessage<K, V> d = assignments.remove(node);

                        // If another thread is already processing this message,
                        // move tot the next node.
                        if (d == null)
                            continue; // For.

                        try {
                            Set<Integer> set = demandFromNode(node, d);

                            if (!set.isEmpty()) {
                                if (log.isDebugEnabled())
                                    log.debug("Missed partitions from node [nodeId=" + node.id() + ", missed=" +
                                        set + ']');

                                missed.addAll(set);
                            }
                        }
                        catch (GridInterruptedException e) {
                            throw e;
                        }
                        catch (GridTopologyException e) {
                            if (log.isDebugEnabled())
                                log.debug("Node left during preloading (will retry) [node=" + node.id() +
                                    ", msg=" + e.getMessage() + ']');

                            resync = true;

                            break; // For.
                        }
                        catch (GridException e) {
                            U.error(log, "Failed to receive partitions from node (preloading will not fully finish) " +
                                "[node=" + node.id() + ", msg=" + d + ']', e);
                        }
                    }

                    // Processed missed entries.
                    if (!missed.isEmpty()) {
                        if (log.isDebugEnabled())
                            log.debug("Reassigning keys that were missed: " + missed);

                        assignments = reassign(missed);

                        if (!topologyChanged() && !isCancelled())
                            pause(); // Wait for topology event or a timeout.
                    }
                    else {
                        syncFuture.onWorkerDone(this);

                        break; // While.
                    }
                }
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(DemandWorker.class, this, "latchQ", latchQ, "futQ", futQ, "msgQ", msgQ,
                "super", super.toString());
        }
    }

    /**
     * Exchange latch.
     */
    private class RealignLatch {
        /** */
        @GridToStringExclude
        private final CountDownLatch latch1;

        /** */
        @GridToStringExclude
        private final CountDownLatch latch2;

        /** */
        @GridToStringExclude
        private final CountDownLatch latch3;

        /** */
        @GridToStringExclude
        private final CountDownLatch latch4;

        /** */
        @GridToStringInclude
        private GridFuture<?> fut;

        /** Partition-to-node assignments. */
        private Map<GridNode, GridDhtPartitionDemandMessage<K, V>> assignments;

        /**
         * @param threadCnt Thread count.
         * @param fut Exchange future.
         */
        RealignLatch(int threadCnt, GridFuture<?> fut) {
            assert threadCnt > 0;
            assert fut != null;

            latch1 = new CountDownLatch(threadCnt);
            latch2 = new CountDownLatch(threadCnt);
            latch3 = new CountDownLatch(threadCnt);
            latch4 = new CountDownLatch(threadCnt);

            this.fut = fut;
        }

        /**
         * @return Reset exchange future.
         */
        GridFuture<?> future() {
            return fut;
        }

        /**
         * @throws GridException If failed.
         * @throws InterruptedException If interrupted.
         */
        void realign1() throws GridException, InterruptedException {
            beforeWait();

            latch1.countDown();

            if (log.isDebugEnabled())
                log.debug("Before waiting on latch1: " + worker());

            // Wait for threads to align at the beginning.
            U.await(latch1);

            if (log.isDebugEnabled())
                log.debug("After waiting on latch1: " + worker());
        }

        /**
         * @throws GridException If failed.
         * @throws InterruptedException If interrupted.
         */
        void realign2() throws GridException, InterruptedException {
            beforeWait();

            latch2.countDown();

            if (log.isDebugEnabled())
                log.debug("Before waiting on latch2: " + worker());

            // Wait for threads to align at the beginning.
            U.await(latch2);

            if (log.isDebugEnabled())
                log.debug("After waiting on latch2: " + worker());
        }

        /**
         * @throws GridException If failed.
         * @throws InterruptedException If interrupted.
         */
        void realign3() throws GridException, InterruptedException {
            beforeWait();

            latch3.countDown();

            if (log.isDebugEnabled())
                log.debug("Before waiting on latch3: " + worker());

            // Wait for threads to align at the beginning.
            U.await(latch3);

            if (log.isDebugEnabled())
                log.debug("After waiting on latch3: " + worker());
        }

        /**
         * @param assignments Partition to node assignments.
         * @return Partition to node assignments.
         * @throws GridException If failed.
         * @throws InterruptedException If interrupted.
         */
        Map<GridNode, GridDhtPartitionDemandMessage<K, V>> realign4(
            Map<GridNode, GridDhtPartitionDemandMessage<K, V>> assignments)
            throws GridException, InterruptedException {
            if (!F.isEmpty(assignments))
                this.assignments = assignments;

            beforeWait();

            latch4.countDown();

            if (log.isDebugEnabled())
                log.debug("Before waiting on latch4: " + worker());

            // Wait for threads to align at the beginning.
            U.await(latch4);

            if (log.isDebugEnabled())
                log.debug("After waiting on latch4: " + worker());

            return this.assignments;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(RealignLatch.class, this,
                "latch1", latch1.getCount(),
                "latch2", latch2.getCount(),
                "latch3", latch3.getCount(),
                "latch4", latch4.getCount()
            );
        }
    }

    /**
     *
     */
    private class SyncFuture extends GridFutureAdapter<Object> {
        /** Remaining workers. */
        private Collection<DemandWorker> remaining;

        /**
         * @param workers List of workers.
         */
        private SyncFuture(Collection<DemandWorker> workers) {
            super(cctx.kernalContext());

            assert workers.size() == poolSize();

            remaining = Collections.synchronizedList(new LinkedList<DemandWorker>(workers));
        }

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public SyncFuture() {
            assert false;
        }

        /**
         * @param w Worker who iterated through all partitions.
         */
        void onWorkerDone(DemandWorker w) {
            if (remaining.remove(w))
                if (log.isDebugEnabled())
                    log.debug("Completed full partition iteration for worker [worker=" + w + ']');

            if (remaining.isEmpty()) {
                if (log.isDebugEnabled())
                    log.debug("Completed sync future.");

                lastRefresh.compareAndSet(-1, System.currentTimeMillis());

                onDone();
            }
        }
    }

    /**
     * Supply message wrapper.
     */
    private static class SupplyMessage<K, V> {
        /** Sender ID. */
        private UUID senderId;

        /** Supply message. */
        private GridDhtPartitionSupplyMessage<K, V> supply;

        /**
         * Dummy constructor.
         */
        private SupplyMessage() {
            // No-op.
        }

        /**
         * @param senderId Sender ID.
         * @param supply Supply message.
         */
        SupplyMessage(UUID senderId, GridDhtPartitionSupplyMessage<K, V> supply) {
            this.senderId = senderId;
            this.supply = supply;
        }

        /**
         * @return Sender ID.
         */
        UUID senderId() {
            return senderId;
        }

        /**
         * @return Message.
         */
        GridDhtPartitionSupplyMessage<K, V> supply() {
            return supply;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(SupplyMessage.class, this);
        }
    }
}
