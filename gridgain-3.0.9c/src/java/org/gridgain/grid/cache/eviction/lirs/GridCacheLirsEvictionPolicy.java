// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.eviction.lirs;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.eviction.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.gridgain.grid.cache.eviction.lirs.GridCacheLirsEvictionPolicy.State.*;
import static org.gridgain.grid.lang.utils.GridConcurrentLinkedQueue.*;

/**
 * Very efficient implementation of {@code LIRS} cache eviction policy which often provides
 * better hit ratio than the {@code LRU} eviction policy. It in particular offers much better
 * performance for access patterns with weak locality, such as regular access over more
 * entries than the cache size. Instead of standard {@code LRU} eviction based on
 * access order, {@code LIRS} maintains a main {@code LRU} stack, called {@code LIRS Stack},
 * as primary {@code Low Inter-reference Recency} stack, and a secondary queue, called
 * {@code HIRS Queue}) for {@code High Inter-Reference} recency entries.
 * <p>
 * Note that this implementation is extremely efficient as it is essentially lock-contention-free
 * and does not create any additional table-like data structures.
 * For more information see
 * <a href="http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.116.2184&rep=rep1&type=pdf">Low Inter-reference Recency Set (LIRS)</a>
 * algorithm by Sone Jiang and Xiaodong Zhang.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
public class GridCacheLirsEvictionPolicy<K, V> implements GridCacheEvictionPolicy<K, V>,
    GridCacheLirsEvictionPolicyMBean {
    /** Lock count. */
    private static final int LOCK_CNT = 64;

    /** Replica count. */
    private static final int REPLICA_CNT = 64;

    /** Locks. */
    private static final GridConsistentHash<Object> LOCKS = new GridConsistentHash<Object>();

    /** Debug flag. */
    private static final boolean DEBUG = false;

    /**
     * Default ratio of {@code HIRS} (High Inter-reference Recency Set). Default value is {@code 0.02},
     * which means that {@code HIRS} set size is {@code 2%} of {@code LIRS} set size.
     */
    public static final float DFLT_QUEUE_SIZE_RATIO = 0.02f;

    /** LIRS stack. */
    @GridToStringInclude
    private final LirsStack stack = new LirsStack();

    /** LIRS queue. */
    @GridToStringInclude
    private final HirsQueue queue = new HirsQueue();

    /** Maximum stack size. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private int max = -1;

    /** Ratio of {@code HIRS} (High Inter-reference Recency Set). */
    private double queueRatio = DFLT_QUEUE_SIZE_RATIO;

    /** Meta tag. */
    @GridToStringExclude
    private final String meta = UUID.randomUUID().toString();

    // Create locks.
    static {
        for (int i = 0; i < LOCK_CNT; i++)
            LOCKS.addNode(new Object(), REPLICA_CNT);
    }

    /**
     * Constructs LRU eviction policy with all defaults.
     */
    public GridCacheLirsEvictionPolicy() {
        // No-op.
    }

    /**
     * Constructs LRU eviction policy with maximum size.
     *
     * @param max Maximum allowed size of entries in cache.
     */
    public GridCacheLirsEvictionPolicy(int max) {
        A.ensure(max > 0, "max > 1");

        this.max = max;
    }

    /**
     * Constructs LRU eviction policy with maximum size and secondary queue ratio to compute
     * size of secondary queue.
     *
     * @param max Maximum allowed size of entries in cache.
     * @param queueRatio Ratio of {@code HIRS} queue size compared to maximum allowed size.
     */
    public GridCacheLirsEvictionPolicy(int max, float queueRatio) {
        A.ensure(max > 0, "max > 1");
        A.ensure(queueRatio > 0 && queueRatio <= 1, "queueRatio > 0 && queueRatio <= 1");

        this.max = max;
        this.queueRatio = queueRatio;
    }

    /** {@inheritDoc} */
    @Override public int getMaxSize() {
        return max;
    }

    /**
     * Sets maximum allowed size of cached entries.
     *
     * @param max Maximum allowed size of cached entries.
     */
    public void setMaxSize(int max) {
        A.ensure(max > 0, "max > 0");

        this.max = max;
    }

    /** {@inheritDoc} */
    @Override public double getQueueSizeRatio() {
        return queueRatio;
    }

    /**
     * Sets ratio of {@code HIRS} queue size compared to main stack size. Generally {@code HIRS}
     * size should be much smaller than main stack size. The default value is {@code 0.01}
     * defined by {@link #DFLT_QUEUE_SIZE_RATIO} constant.
     *
     * @param queueRatio Ratio of {@code HIRS} set size compared to main stack size.
     */
    public void setQueueSizeRatio(double queueRatio) {
        A.ensure(queueRatio > 0 && queueRatio <= 1, "queueRatio > 0 && queueRatio <= 1");

        this.queueRatio = queueRatio;
    }

    /** {@inheritDoc} */
    @Override public int getMaxQueueSize() {
        return (int)Math.ceil(max * queueRatio);
    }

    /** {@inheritDoc} */
    @Override public int getMaxStackSize() {
        return max - getMaxQueueSize();
    }

    /**
     * @return Current main stack size.
     */
    @Override public int getCurrentStackSize() {
        return stack.size();
    }

    /**
     * @return Current HIRS set size.
     */
    @Override public int getCurrentQueueSize() {
        return queue.size();
    }

    /** {@inheritDoc} */
    @Override public int getCurrentStackEdenSize() {
        return stack.eden();
    }

    /** {@inheritDoc} */
    @Override public int getCurrentQueueEdenSize() {
        return queue.eden();
    }

    /**
     * Gets read-only view on main internal stack for {@code LIRS} implementation.
     *
     * @return Read-only view on Main internal stack for {@code LIRS} implementation.
     */
    public Collection<GridCacheEntry<K, V>> stack() {
        return stack.entries();
    }

    /**
     * Gets read-only view on secondary {@code HIR} queue to hold {@code High Inter-reference Recency}
     * entries.
     *
     * @return Secondary read-only view on {@code HIR} queue.
     */
    public Collection<GridCacheEntry<K, V>> queue() {
        return queue.entries();
    }

    /** {@inheritDoc} */
    @Override public void onEntryAccessed(boolean rmv, GridCacheEntry<K, V> entry) {
        if (!rmv)
            touch(entry);
        else {
            Capsule c = entry.meta(meta);

            if (c != null)
                synchronized (c.lock()) {
                    c.clear();
                }
        }
    }

    /**
     * @param key Key to lock.
     * @return Lock for the key.
     */
    private static Object lock0(Object key) {
        Object lock = LOCKS.node(key);

        assert lock != null;

        return lock;
    }

    /**
     * @param entry Entry to touch.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    private void touch(GridCacheEntry<K, V> entry) {
        Capsule c = entry.meta(meta);

        Object lock = c == null ? lock0(entry.getKey()) : c.lock();

        boolean prune = false;
        boolean demote = false;
        boolean evict = false;

        State initState = stack.size() < getMaxStackSize() ? LIR : HIR_R;

        synchronized (lock) {
            boolean miss = false;

            if (c == null) {
                Capsule old = entry.putMetaIfAbsent(meta, c = new Capsule(entry, initState, lock));

                if (old != null)
                    c = old;
                else
                    miss = true;
            }

            // Replace removed entry.
            if (c.cleared())
                entry.addMeta(meta, c = new Capsule(entry, initState, lock));

            switch (c.state()) {
                // Low inter-recency.
                case LIR: {
                    if (stack.isFirst(c))
                        prune = true;

                    c.addStackNode(LIR);

                    break;
                }

                // High inter-recency resident.
                case HIR_R: {
                    if (c.inStack()) {
                        // Add to stack.
                        c.addStackNode(LIR);

                        // Remove from queue.
                        c.dequeue();

                        // Demote LIR from stack head to HIR_R.
                        demote = true;
                        prune = true;
                    }
                    else {
                        // Add to the top of the stack.
                        c.addStackNode(HIR_R);

                        // Move to the end of the queue.
                        c.addQueueNode(HIR_R);

                        if (miss && stack.full()) {
                            demote = true;
                            prune = true;
                        }
                    }


                    break;
                }

                // High inter-recency non-resident.
                case HIR_NR: {
                    // Dequeue from head and change to HIR_NR.
                    evict = true;

                    if (c.inStack()) {
                        c.addStackNode(LIR);

                        demote = true;
                        prune = true;
                    }
                    else {
                        c.addStackNode(HIR_R);
                        c.addQueueNode(HIR_R);
                    }

                    break;
                }

                default: {
                    assert false;
                }
            }
        }

        if (evict) {
            while (true) {
                Capsule cap = queue.poll();

                if (cap == null)
                    break;

                synchronized (cap.lock()) {
                    if (cap.pollQueue())
                        break;
                }
            }
        }

        // Shrink the HIR queue.
        if (queue.shrink())
            prune = true;

        if (prune) {
            while (true) {
                Capsule cap = stack.prune();

                if (cap != null && demote) {
                    synchronized (cap.lock()) {
                        if (!cap.demote())
                            continue;
                    }

                    // Prune again.
                    stack.prune();

                    queue.shrink();
                }

                break;
            }
        }

        stack.gc(max);
        queue.gc(getMaxQueueSize());
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheLirsEvictionPolicy.class, this,
            "maxStack", getMaxStackSize(), "maxQueue", getMaxQueueSize());
    }

    /**
     * Concurrent generic linked queue.
     */
    private abstract class LinkedQueue extends GridConcurrentLinkedQueue<Capsule> {
        /**
         * Collection view over cache entries (effectively skipping {@code nulls}.
         *
         * @return Collection view over cache entries.
         */
        public Collection<GridCacheEntry<K, V>> entries() {
            final GridTuple<GridCacheEntry<K, V>> t = F.t1();

            return F.viewReadOnly(this, new C1<Capsule, GridCacheEntry<K, V>>() {
                @Override public GridCacheEntry<K, V> apply(Capsule c) {
                    return t.get();
                }
            }, new P1<Capsule>() {
                @Override public boolean apply(Capsule c) {
                    GridCacheEntry<K, V> e = c.entry();

                    if (e != null) {
                        t.set(e);

                        return true;
                    }

                    return false;
                }
            });
        }
    }

    /**
     * Hirs queue.
     */
    private class HirsQueue extends LinkedQueue {
        /**
         * @return {@code True} if queue changed.
         */
        boolean shrink() {
            while (size() > getMaxQueueSize()) {
                Capsule c = poll();

                if (c == null)
                    return false;

                synchronized (c.lock()) {
                    if (c.state() == HIR_R) {
                        c.state(HIR_NR);

                        if (!c.inStack()) {
                            // It's OK to evict while holding lock on capsule.
                            if (!c.entry().evict())
                                // Add to the top again.
                                c.addStackNode(LIR);
                        }

                        return true;
                    }
                }
            }

            return false;
        }

        /** {@inheritDoc} */
        @Override protected boolean removeNode(Node<Capsule> n) {
            Capsule c = n.value();

            if (c != null)
                synchronized (c.lock()) {
                    if (c.dequeue())
                        return true;
                }

            return false;
        }

        /**
         * @return {@code True} if size is excessive.
         */
        boolean full() {
            return size() >= getMaxQueueSize();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(HirsQueue.class, this, "size", size(), "eden", eden());
        }
    }

    /**
     * Lirs stack.
     */
    private class LirsStack extends LinkedQueue {
        /**
         * Prunes stack.
         *
         * @return First LIR capsule.
         */
        @Nullable Capsule prune() {
            Capsule ret = null;

            while (true) {
                // This will clear obsolete nodes.
                Node<Capsule> first = peekNode();

                if (first == null)
                    break;

                Capsule c = first.value();

                // If capsule is null, then another thread pruned or cleared.
                // In this case we simply try again.
                if (c != null) {
                    if (c.cleared()) {
                        stack.clearNode(first); // Strange.

                        // Try again.
                        continue;
                    }

                    synchronized (c.lock()) {
                        if (c.state() == HIR_R) {
                            c.unstack();
                        }
                        // If need to evict.
                        else if (c.state() == HIR_NR) {
                            if (c.unstack()) {
                                // It's OK to evict while holding lock on capsule.
                                if (!c.entry().evict())
                                    // Add to the top again.
                                    c.addStackNode(LIR);
                            }
                        }
                        else {
                            ret = c;

                            break;
                        }
                    }
                }
            }

            return ret;
        }

        /**
         * @return {@code True} if size is greater or equal to {@code 'max'} cache size.
         */
        boolean full() {
            // We specifically compare against maximum cache size, since we don't keep
            // explicit count for non-resident nodes. This way stack may get slightly
            // over-sized, but implementation is simpler.
            return size() >= max;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(LirsStack.class, this, "size", size(), "eden", eden());
        }
    }

    /**
     * Capsule.
     */
    private class Capsule {
        /** */
        @GridToStringExclude
        private Node<Capsule> stackNode, queueNode;

        /** State. */
        @GridToStringInclude
        private State state;

        /** Entry. */
        @GridToStringInclude
        private volatile GridCacheEntry<K, V> entry;

        /** */
        @GridToStringExclude
        private final Object lock;

        /**
         * Constructor for head node.
         */
        Capsule() {
            lock = null;
        }

        /**
         * @param entry Entry.
         * @param state Initial state.
         * @param lock Lock.
         */
        Capsule(GridCacheEntry<K, V> entry, State state, Object lock) {
            assert entry != null;
            assert state != null;
            assert lock != null;

            this.state = state;
            this.entry = entry;
            this.lock = lock;
        }

        /**
         * Checks that lock is held if {@link GridCacheLirsEvictionPolicy#DEBUG} is {@code true}.
         */
        private void assertLock() {
            if (DEBUG)
                assert Thread.holdsLock(lock);
        }

        /**
         * @return Clears this capsule.
         */
        boolean clear() {
            assertLock();

            if (entry != null) {
                dequeue();
                unstack();

                entry = null;

                state = null;

                return true;
            }

            return false;
        }

        /**
         * @return {@code True} if capsule is cleared.
         */
        boolean cleared() {
            return entry == null;
        }

        /**
         * @return lock.
         */
        Object lock() {
            return lock;
        }

        /**
         * @return Entry.
         */
        GridCacheEntry<K, V> entry() {
            return entry;
        }

        /**
         * @return {@code True} if in stack.
         */
        boolean inStack() {
            assertLock();

            return stackNode != null && !stackNode.cleared();
        }

        /**
         * @return {@code True} if in queue.
         */
        boolean inQueue() {
            assertLock();

            return queueNode != null && !queueNode.cleared();
        }

        /**
         * @param state State.
         * @return Node.
         */
        Node<Capsule> addStackNode(State state) {
            assertLock();

            if (stackNode != null)
                stack.clearNode(stackNode);

            this.state = state;

            return stackNode = stack.addNode(this);
        }

        /**
         * @param state State.
         * @return Node.
         */
        Node<Capsule> addQueueNode(State state) {
            assertLock();

            if (queueNode != null)
                queue.clearNode(queueNode);

            this.state = state;

            return queueNode = queue.addNode(this);
        }

        /**
         * @return {@code True} if dequeued.
         */
        boolean dequeue() {
            assertLock();

            if (cleared())
                return false;

            if (queueNode != null) {
                queue.clearNode(queueNode);

                queueNode = null;

                return true;
            }

            return false;
        }

        /**
         * @return {@code True} if unstacked.
         */
        boolean unstack() {
            assertLock();

            if (cleared())
                return false;

            if (stackNode != null) {
                stack.clearNode(stackNode);

                stackNode = null;

                return true;
            }

            return false;
        }

        /**
         * @return {@code True} if demoted.
         */
        boolean demote() {
            assertLock();

            if (cleared())
                return false;

            if (state == LIR) {
                unstack();

                addQueueNode(HIR_R);

                return true;
            }

            return false;
        }

        /**
         * @return {@code True} if changed to non-resident status.
         */
        boolean pollQueue() {
            assertLock();

            if (cleared())
                return false;

            if (state == HIR_R) {
                state = HIR_NR;

                dequeue();

                if (!inStack()) {
                    // It's OK to evict while holding lock on capsule.
                    if (!entry.evict())
                        // Add to the top again.
                        addStackNode(LIR);
                }

                return true;
            }

            return false;
        }

        /**
         * @return Stack node.
         */
        Node<Capsule> stackNode() {
            assertLock();

            return stackNode;
        }

        /**
         *  @return Queue node .
         */
        Node<Capsule> queueNode() {
            assertLock();

            return queueNode;
        }

        /**
         * @return State.
         */
        State state() {
            assertLock();

            return state;
        }

        /**
         * @param state State.
         */
        void state(State state) {
            assertLock();

            this.state = state;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(Capsule.class, this);
        }
    }

    /**
     * LIRS state.
     */
    @SuppressWarnings({"PackageVisibleInnerClass"})
    enum State {
        LIR, HIR_R, HIR_NR
    }
}
