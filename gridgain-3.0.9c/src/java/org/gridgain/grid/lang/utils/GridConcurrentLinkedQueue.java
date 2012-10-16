// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang.utils;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Concurrent queue which fixes infinite growth of JDK
 * {@link ConcurrentLinkedQueue} implementation. On top of that, it provides
 * public access to internal {@code 'nodes'}, so middle elements of the queue
 * can be accessed immediately.
 * <p>
 * The problem of JDK implementation is that it must follow regular queue
 * semantics and will only work if you call {@link Queue#offer(Object)} and
 * {@link Queue#poll()}, and will leave nullified nodes mingling around if
 * {@link Collection#remove(Object)} was called which can lead to memory leaks.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridConcurrentLinkedQueue<E> extends GridSerializableCollection<E> implements Queue<E> {
    /** Dummy stamp holder. */
    private static final boolean[] DUMMY = new boolean[1];

    /** List head. */
    @GridToStringExclude
    protected final AtomicReference<Node<E>> head = new AtomicReference<Node<E>>(new Node<E>());

    /** List tail. */
    @GridToStringExclude
    protected final AtomicReference<Node<E>> tail = new AtomicReference<Node<E>>(head.get());

    /** Current size. */
    @GridToStringInclude
    private final AtomicInteger size = new AtomicInteger();

    /** Compacting flag.*/
    private final AtomicBoolean compacting = new AtomicBoolean(false);

    /** Counter of void nodes ready to be GC'ed. */
    private final AtomicInteger eden = new AtomicInteger(0);

    /**
     * Gets count of cleared nodes that are stuck in the queue and should be removed.
     * To clear, call {@link #gc(int)} method.
     * 
     * @return Eden count.
     */
    public int eden() {
        return eden.get();
    }

    /**
     * @param t Capsule to check.
     * @return {@code True} if this capsule is first.
     */
    public boolean isFirst(E t) {
        while (true) {
            Node<E> n = peekNode();

            if (n == null) {
                return false;
            }

            E cap = n.value();

            if (cap == null) {
                continue; // Try again.
            }

            return cap == t;
        }
    }

    /**
     * This method needs to be periodically called whenever elements are
     * cleared or removed from the middle of the queue. It will make sure
     * that all empty nodes that exceed {@code maxEden} counter will be
     * cleared.
     * 
     * @param maxEden Maximum number of void nodes in this LIRS collection.
     */
    public void gc(int maxEden) {
        if (eden.get() >= maxEden && compacting.compareAndSet(false, true)) {
            try {
                Node<E> prev, next = null;

                // Find gap.
                for (prev = head.get(); prev != null; prev = prev.next()) {
                    next = prev.next();

                    if (next == null || next.cleared()) {
                        break;
                    }
                }

                // Start shrinking.
                while (prev != null && next != null) {
                    Node<E> h = head.get();
                    Node<E> t = tail.get();

                    Node<E> n = prev.next();

                    if (n == null) {
                        return;
                    }

                    next = n.next();

                    if (h == head.get()) {
                        if (prev == h) {
                            // Following logic is the same as clearing from head.
                            if (h == t) {
                                if (prev.cleared()) {
                                    return;
                                }
                                else {
                                    casTail(t, n);
                                }
                            }
                            else {
                                assert n != null;

                                if (!n.cleared()) {
                                    return;
                                }
                                else {
                                    // This is GC step to remove obsolete nodes.
                                    if (casHead(h, n)) {
                                        prev = n;
                                        
                                        decreaseEden();
                                    }
                                }
                            }
                        }
                        else {
                            // Once the first gap is over, we break.
                            // (we check next for null to make sure we don't mess around with tail pointer).
                            if (next == null || !n.cleared() || !prev.casNextIfNotCleared(n, next)) {
                                break;
                            }

                            decreaseEden();
                        }
                    }
                }
            }
            finally {
                compacting.set(false);
            }
        }
    }

    /**
     * @param n Node to clear.
     * @return New size.
     */
    public boolean clearNode(Node<E> n) {
        if (n.clear() != null) {
            increaseEden();

            size.decrementAndGet();

            return true;
        }

        return false;
    }

    /**
     * Node to add to the queue (cannot be {@code null}).
     *
     * @param n New node.
     * @return The same node as passed in.
     */
    public Node<E> addNode(Node<E> n) {
        A.notNull(n, "n");

        while(true) {
            Node<E> t = tail.get();

            Node<E> s = t.next();

            if (t == tail.get()) {
                if (s == null) {
                    if (t.casNext(s, n)) {
                        casTail(t, n);

                        size.incrementAndGet();

                        return n;
                    }
                }
                else {
                    casTail(t, s);
                }
            }
        }
    }

    /**
     * New element to add (cannot be {@code null}).
     *
     * @param e New element.
     * @return Created node.
     */
    public Node<E> addNode(E e) {
        return addNode(new Node<E>(e));
    }

    /**
     * @return First node.
     */
    @Nullable public Node<E> peekNode() {
        while (true) {
            Node<E> h = head.get();
            Node<E> t = tail.get();

            Node<E> first = h.next();

            if (h == head.get()) {
                if (h == t) {
                    if (first == null) {
                        return null;
                    }
                    else {
                        casTail(t, first);
                    }
                }
                else {
                    assert first != null;

                    if (!first.cleared()) {
                        return first;
                    }
                    else {
                        // This is GC step to remove obsolete nodes.
                        if (casHead(h, first)) {
                            decreaseEden();
                        }
                    }
                }
            }
        }
    }

    /**
     * Gets first queue element (head of the queue).
     *
     * @return First queue element.
     */
    @Override @Nullable public E peek() {
        while (true) {
            Node<E> n = peekNode();

            if (n == null) {
                return null;
            }

            E e = n.value();

            if (e != null) {
                return e;
            }
        }
    }

    /**
     * @return Previous head of the queue.
     */
    @Override @Nullable public E poll() {
        while (true) {
            Node<E> h = head.get();
            Node<E> t = tail.get();

            Node<E> first = h.next();

            if (h == head.get()) {
                if (h == t) {
                    if (first == null) {
                        return null;
                    }
                    else {
                        casTail(t, first);
                    }
                }
                else if (casHead(h, first)) { // Move head pointer.
                    assert first != null;

                    E c = first.value();

                    if (c != null) {
                        if (removeNode(first)) {
                            // We just cleared a node, so bring the counter back.
                            decreaseEden();

                            return c;
                        }
                    }

                    // We encountered cleared node.
                    decreaseEden();
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public boolean add(E e) {
        return addNode(e) != null;
    }

    @Override public boolean offer(E e) {
        return add(e);
    }

    /** {@inheritDoc} */
    @Nullable @Override public E remove() {
        return poll();
    }

    /** {@inheritDoc} */
    @Override public E element() {
        E e = peek();

        if (e == null) {
            throw new NoSuchElementException();
        }

        return e;
    }

    /** {@inheritDoc} */
    @Override public int size() {
        return size.get();
    }

    /** {@inheritDoc} */
    @Override public Iterator<E> iterator() {
        return new QueueIterator(false);
    }

    /**
     * If {@code 'readOnly'} flag is {@code true}, then returned
     * iterator {@code 'remove()'} method will throw
     * {@link UnsupportedOperationException}.
     *
     * @param readOnly Read-only flag.
     * @return Iterator over this queue.
     */
    public Iterator<E> iterator(boolean readOnly) {
        return new QueueIterator(readOnly);
    }

    /**
     * Removes node from queue by calling {@link Node#clear()} on the
     * passed in node. Child classes that override this method
     * should always make sure to call {@link Node#clear()}.
     *
     * @param n Node to remove.
     * @return {@code True} if node was cleared by this method call, {@code false}
     *      if it was already cleared.
     */
    protected boolean removeNode(Node<E> n) {
        return n.clear() != null;
    }

    /**
     *
     */
    private void increaseEden() {
        eden.incrementAndGet();
    }

    /**
     *
     */
    private void decreaseEden() {
        int e = eden.decrementAndGet();

        if (e < 0) {
            eden.compareAndSet(e, 0);
        }
    }

    /**
     * @param old Old value.
     * @param val New value.
     * @return {@code True} if value was set.
     */
    private boolean casTail(Node<E> old, Node<E> val) {
        return tail.compareAndSet(old, val);
    }

    /**
     * @param old Old value.
     * @param val New value.
     * @return {@code True} if value was set.
     */
    private boolean casHead(Node<E> old, Node<E> val) {
        return head.compareAndSet(old, val);
    }

    /**
     * Iterator.
     */
    private class QueueIterator implements Iterator<E> {
        /** Next node to return item for. */
        private Node<E> nextNode;

        /** Next cache entry to return. */
        private E nextItem;

        /** Last node. */
        private Node<E> lastNode;

        /** Read-only flag. */
        private final boolean readOnly;

        /**
         * @param readOnly Read-only flag.
         */
        QueueIterator(boolean readOnly) {
            this.readOnly = readOnly;
            
            advance();
        }

        /**
         * @return Next entry.
         */
        private E advance() {
            lastNode = nextNode;

            E x = nextItem;

            Node<E> p = (nextNode == null)? peekNode() : nextNode.next();

            while (true) {
                if (p == null) {
                    nextNode = null;

                    nextItem = null;

                    return x;
                }

                E e = p.value();

                if (e != null) {
                    nextNode = p;

                    nextItem = e;

                    return x;
                }

                p = p.next();
            }
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return nextNode != null;
        }

        @Override public E next() {
            if (nextNode == null) {
                throw new NoSuchElementException();
            }

            return advance();
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            if (readOnly) {
                throw new UnsupportedOperationException();
            }

            Node<E> last = lastNode;

            if (last == null) {
                throw new IllegalStateException();
            }

            // Future gc() or first() calls will remove it.
            last.clear();

            lastNode = null;
        }
    }

    /**
     * Concurrent linked queue node. It can be effectively cleared (nullified)
     * by calling {@link GridConcurrentLinkedQueue#clearNode(Node)} which allows
     * to clear nodes without having to iterate through the queue. Note that
     * clearing a node only nullifies its value and does not remove the node from
     * the queue. To make sure that memory is reclaimed you must call
     * {@link GridConcurrentLinkedQueue#gc(int)} explicitly.
     */
    @SuppressWarnings({"PublicInnerClass"})
    public static class Node<E> extends AtomicMarkableReference<Node<E>> {
        /** Entry. */
        @GridToStringInclude
        private volatile E e;

        /**
         * Head constructor.
         */
        private Node() {
            super(null, false);

            e = null;
        }

        /**
         * @param e Capsule.
         */
        public Node(E e) {
            super(null, false);

            A.notNull(e, "e");

            this.e = e;
        }

        /**
         * @return Entry.
         */
        @Nullable public E value() {
            return e;
        }

        /**
         * @return {@code True} if entry is cleared from node.
         */
        public boolean cleared() {
            return isMarked();
        }

        /**
         * Atomically clears entry.
         *
         * @return Non-null value if cleared or {@code null} if was already clear.
         */
        @Nullable private E clear() {
            if (e != null) {
                while (true) {
                    Node<E> next = next();

                    if (compareAndSet(next, next, false, true)) {
                        E ret = e;

                        e = null;

                        return ret;
                    }

                    if (next == next()) {
                        return null;
                    }
                }
            }

            return null;
        }

        /**
         * @param cur Current value.
         * @param next New value.
         * @return {@code True} if set.
         */
        private boolean casNext(Node<E> cur, Node<E> next) {
            while (true) {
                boolean mark = isMarked();

                if (compareAndSet(cur, next, mark, mark)) {
                    return true;
                }

                if (mark == isMarked()) {
                    return false;
                }
            }
        }

        /**
         * @param cur Current value.
         * @param next New value.
         * @return {@code True} if set.
         */
        boolean casNextIfNotCleared(Node<E> cur, Node<E> next) {
            return compareAndSet(cur, next, false, false);
        }

        /**
         * @return Next linked node.
         */
        @Nullable private Node<E> next() {
            return get(DUMMY);
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            return o == this;
        }

        /** {@inheritDoc} */
        @Override public String toString() { return S.toString(Node.class, this); }
    }
}
