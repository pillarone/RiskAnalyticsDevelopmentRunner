// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.datastructures;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * This interface provides a rich API for working with Data Grid-based distributed queue.
 * <p>
 * Note that queue is only available in <b>Enterprise Edition</b>.
 * <p>
 * <h1 class="header">Functionality</h1>
 * Cache queue provides an access to cache elements using typical queue API. Cache queue also implements
 * {@link Collection} interface.
 * <p>
 * Note that all {@link Collection} methods in the queue can throw {@link GridRuntimeException}.
 * <p>
 * Queue can be unbounded or bounded. Capacity of queue can be set when queue will be created and can't be
 * changed later. Here is an example of how create bounded {@code LIFO} queue with capacity of 1000 items
 * {@link GridCache#queue(String, GridCacheQueueType, int)}
 * <pre name="code" class="java">
 * GridCacheQueue&lt;String&gt; queue = cache().queue("anyName", LIFO, 1000);
 * ...
 * queue.add("item");
 * </pre>
 * <p>
 * Queue provides different access to queue elements:
 * <ul>
 *      <li>{@link GridCacheQueueType#FIFO} (default)</li>
 *      <li>{@link GridCacheQueueType#LIFO}</li>
 *      <li>{@link GridCacheQueueType#PRIORITY}</li>
 * </ul>
 * For more information about types of queue refer to {@link GridCacheQueueType} documentation.
 * <p>
 * Priority queue provides sorting queue items by priority.
 * Priority in the queue item can be set using annotation {@link GridCacheQueuePriority}.
 * Only one field or method can be annotated in queue item class.
 * Annotated field must be {@code int} or {@code Integer} and annotated method must
 * return {@code int} or {@code Integer} result.
 * Here is an example of how annotate queue item:
 * <pre name="code" class="java">
 * private static class PriorityMethodItem {
 *       // Priority field.
 *       private final int priority;
 *
 *       private PriorityMethodItem(int priority) {
 *           this.priority = priority;
 *       }
 *
 *       // Annotated priority method.
 *       &#64;GridCacheQueuePriority
 *       int priority() {
 *           return priority;
 *       }
 *   }
 * </pre>
 * <p>
 * Queue items can be placed on one node or distributed through grid nodes (governed by the cache that
 * created that queue).
 * Distributed mode provides only for partitioned cache and it is configured via {@code collocated} parameter.
 * If {@code collocated} is {@code true} all queue items will be placed on one node, else items will be distributed
 * through all grid nodes.
 * <p>
 * Here is an example of how create unbounded {@link GridCacheQueueType#PRIORITY} queue with items
 * distributed through grid topology.
 * {@link GridCache#queue(String, GridCacheQueueType, int)}
 * <pre name="code" class="java">
 * GridCacheQueue&lt;String&gt; queue = cache().queue("anyName", PRIORITY, Integer.MAX_VALUE, false);
 * ...
 * queue.add("item");
 * </pre>
 * <p>
 * Distributed Cache Queue provides implementation for:
 * <ul>
 *      <li>blocking,</li>
 *      <li>non-blocking,</li>
 *      <li>and asynchronous operations.</li>
 * </ul>
 * <h1 class="header">Creating Distributed Cache Queue</h1>
 * Instance of distributed cache queue can be created by calling one of the following methods:
 * <ul>
 *     <li>{@link GridCache#queue(String)}</li>
 *     <li>{@link GridCache#queue(String, GridCacheQueueType)}</li>
 *     <li>{@link GridCache#queue(String, GridCacheQueueType, int)}</li>
 *     <li>{@link GridCache#queue(String, GridCacheQueueType, int, boolean)}</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridCache#queue(String)
 * @see GridCache#queue(String, GridCacheQueueType)
 * @see GridCache#queue(String, GridCacheQueueType, int)
 * @see GridCache#queue(String, GridCacheQueueType, int, boolean)
 */
public interface GridCacheQueue<T> extends GridMetadataAware, Collection<T> {
    /**
     * Gets queue name.
     *
     * @return Queue name.
     */
    public String name();

    /**
     * Gets queue type {@link GridCacheQueueType}.
     *
     * @return Queue type.
     */
    public GridCacheQueueType type();

    /**
     * Adds specified item to the queue.
     *
     * @param item Queue item to add.
     * @throws GridException If operation failed.
     * @return {@code true} if item was added, {@code false} if item wasn't added because queue is full.
     */
    public boolean addx(T item) throws GridException;

    /**
     * Retrieves and removes the head item of the queue, or returns {@code null} if this queue is empty.
     *
     * @return Item from the head of the queue.
     * @throws GridException If operation failed.
     */
    @Nullable public T poll() throws GridException;

    /**
     * Retrieves and removes the tail item of the queue, or returns {@code null} if this queue is empty.
     *
     * @return Item from the tail of the queue.
     * @throws GridException If operation failed.
     */
    @Nullable public T pollLast() throws GridException;

    /**
     * Retrieves, but does not remove, the head of this queue, or returns {@code null} if this queue is empty.
     *
     * @return The head of this queue, or {@code null} if this queue is empty.
     * @throws GridException If operation failed.
     */
    @Nullable public T peek() throws GridException;

    /**
     * Retrieves, but does not remove, the tail of this queue, or returns {@code null} if this queue is empty.
     *
     * @return The tail of this queue, or {@code null} if this queue is empty.
     * @throws GridException If operation failed.
     */
    @Nullable public T peekLast() throws GridException;

    /**
     * Deletes specified item from the queue.
     * If queue becomes empty as a result of this call.
     *
     * @param item Item to delete.
     * @return {@code true} if item was deleted, {@code false} otherwise.
     * @throws GridException If operation failed.
     */
    public boolean removex(T item) throws GridException;

    /**
     * Gets position of the specified item in the queue. First element (head of the queue) is at position {@code 0}.
     * <p>
     * Note this operation is supported only in {@code collocated} mode.
     *
     * @param item Item to get position for.
     * @return Position of specified item in the queue or {@code -1} if item is not found.
     * @throws GridException If operation failed or operations executes in {@code non-collocated} mode.
     */
    public int position(T item) throws GridException;

    /**
     * Gets items from the queue at specified positions. First element (head of the queue) is at position {@code 0}.
     * <p>
     * Note this operation is supported only in {@code collocated} mode.
     *
     * @param positions Positions of items to get from queue.
     * @return Queue items at specified positions.
     * @throws GridException If operation failed or operations executes in {@code non-collocated} mode.
     */
    @Nullable public Collection<T> items(Integer... positions) throws GridException;

    /**
     * Puts specified item to the queue. Waits until place will be available in this queue.
     *
     * @param item Queue item to put.
     * @throws GridException If operation failed.
     */
    public void put(T item) throws GridException;

    /**
     * Try to put specified item to the queue during timeout.
     *
     * @param item Queue item to put.
     * @param timeout Timeout.
     * @param unit Type of time representations.
     * @return {@code false} if timed out while waiting for queue to go below maximum capacity,
     *      {@code true} otherwise. If queue is not bounded, then {@code true} is always returned.
     * @throws GridException If operation failed or timeout was exceeded.
     */
    public boolean put(T item, long timeout, TimeUnit unit) throws GridException;

    /**
     * Puts specified item to the queue asynchronously.
     *
     * @param item Queue item to put.
     * @throws GridException If operation failed.
     * @return Future which will complete whenever this operation completes.
     */
    public GridFuture<Boolean> putAsync(T item) throws GridException;

    /**
     * Retrieves and removes the head item of the queue. Waits if no elements are present in this queue.
     *
     * @return Item from the head of the queue.
     * @throws GridException If operation failed.
     */
    @Nullable public T take() throws GridException;

    /**
     * Retrieves and removes the tail item of the queue. Waits if no elements are present in this queue.
     *
     * @return Item from the tail of the queue.
     * @throws GridException If operation failed.
     */
    @Nullable public T takeLast() throws GridException;

    /**
     * Try to retrieve and remove the head item of the queue during timeout.
     *
     * @param timeout Timeout.
     * @param unit Type of time representations.
     * @return Item from the head of the queue, or {@code null} if method timed out
     *      before queue had at least one item.
     * @throws GridException If operation failed or timeout was exceeded.
     */
    @Nullable public T take(long timeout, TimeUnit unit) throws GridException;

    /**
     * Try to retrieve and remove the tail item of the queue during timeout.
     *
     * @param timeout Timeout.
     * @param unit Type of time representations.
     * @return Item from the tail of the queue, or {@code null} if method timed out
     *      before queue had at least one item.
     * @throws GridException If operation failed or timeout was exceeded.
     */
    @Nullable public T takeLast(long timeout, TimeUnit unit) throws GridException;

    /**
     * Retrieves and removes the head item of the queue asynchronously.
     *
     * @throws GridException If operation failed.
     * @return Future for the take operation.
     */
    @Nullable public GridFuture<T> takeAsync() throws GridException;

    /**
     * Try to retrieve and remove the tail item of the queue asynchronously.
     *
     * @throws GridException If operation failed.
     * @return Future which will complete whenever this operation completes.
     */
    @Nullable public GridFuture<T> takeLastAsync() throws GridException;

    /**
     * Retrieves, but does not remove, the head of this queue. Waits if no elements are present in this queue.
     *
     * @return The head of this queue.
     * @throws GridException If operation failed.
     */
    @Nullable public T get() throws GridException;

    /**
     * Retrieves, but does not remove, the head of this queue. Waits if no elements are present in this queue.
     *
     * @return The tail of this queue.
     * @throws GridException If operation failed.
     */
    @Nullable public T getLast() throws GridException;

    /**
     * Try to retrieve but does not remove the head of this queue during timeout.
     * Waits if no elements are present in this queue.
     *
     * @param timeout Timeout.
     * @param unit Type of time representations.
     * @return The head of this queue or {@code null} if method timed out
     *      before queue had at least one item.
     * @throws GridException If operation failed or timeout was exceeded.
     */
    @Nullable public T get(long timeout, TimeUnit unit) throws GridException;

    /**
     * Try to retrieve but does not remove the tail of this queue during timeout.
     * Waits if no elements are present in this queue.
     *
     * @param timeout Timeout.
     * @param unit Type of time representations.
     * @return The tail of this queue, or {@code null} if method timed out
     *      before queue had at least one item.
     * @throws GridException If operation failed or timeout was exceeded.
     */
    @Nullable public T getLast(long timeout, TimeUnit unit) throws GridException;

    /**
     * Try to retrieve but does not remove the tail of this queue asynchronously.
     *
     * @throws GridException If operation failed.
     * @return Future which will complete whenever this operation completes.
     */
    public GridFuture<T> getAsync() throws GridException;

    /**
     * Try to retrieve but does not remove the tail of this queue asynchronously.
     *
     * @throws GridException If operation failed.
     * @return Future which will complete whenever this operation completes.
     */
    public GridFuture<T> getLastAsync() throws GridException;

    /**
     * Clears the queue.
     *
     * @throws GridException If operation failed.
     */
    public void clearx() throws GridException;

    /**
     * Clears the queue asynchronously asynchronously.
     *
     * @throws GridException If operation failed.
     * @return Future which will complete whenever this operation completes.
     */
    public GridFuture<Boolean> clearAsync() throws GridException;

    /**
     * Returns {@code true} if this queue doesn't contain items.
     *
     * @return {@code true} if this queue is empty.
     * @throws GridException If operation failed.
     */
    public boolean isEmptyx() throws GridException;

    /**
     * Gets size of the queue.
     *
     * @return Size of the queue.
     * @throws GridException If operation failed.
     */
    public int sizex() throws GridException;

    /**
     * Gets maximum number of elements of the queue.
     *
     * @return Maximum number of elements. If queue is unbounded {@code Integer.MAX_SIZE} will return.
     * @throws GridException If operation failed.
     */
    public int capacity() throws GridException;

    /**
     * Returns {@code true} if this queue is bounded.
     *
     * @return {@code true} if this queue is bounded.
     * @throws GridException If operation failed.
     */
    public boolean bounded() throws GridException;

    /**
     * Returns {@code true} if this queue can be kept on the one node only.
     * Returns {@code false} if this queue can be kept on the many nodes.
     *
     * @return {@code true} if this queue is in {@code collocated} mode {@code false} otherwise.
     * @throws GridException If operation failed.
     */
    public boolean collocated() throws GridException;

    /**
     * Gets status of queue.
     *
     * @return {@code true} if queue was removed from cache {@code false} otherwise.
     */
    public boolean removed();
}
