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

/**
 * This interface provides a rich API for working with distributed atomic sequence.
 * <p>
 * Note that distributed atomic sequence is only available in <b>Enterprise Edition</b>.
 * <p>
 * <h1 class="header">Functionality</h1>
 * Distrubuted atomic sequence includes the following main functionality:
 * <ul>
 * <li>
 * Method {@link #get()} synchronously gets current value from atomic sequence.
 * </li>
 * <li>
 * Various {@code get..(..)} methods synchronously get current value from atomic sequence
 * and increase atomic sequences value.
 * </li>
 * <li>
 * Various {@code add..(..)} {@code increment(..)} methods synchronously increase atomic sequences value
 * and return increased value.
 * </li>
 * </ul>
 * All previously described methods have asynchronous analogs.
 * <ul>
 * <li>
 * Method {@link #batchSize(int size)} sets batch size of current atomic sequence.
 * </li>
 * <li>
 * Method {@link #batchSize()} gets current batch size of atomic sequence.
 * </li>
 * <li>
 * Method {@link #name()} gets name of atomic sequence.
 * </li>
 * </ul>
 * <h1 class="header">Creating Distributed Atomic Sequence</h1>
 * Instance of distributed atomic sequence can be created by calling one of the following methods:
 * <ul>
 *     <li>{@link GridCache#atomicSequence(String)}</li>
 *     <li>{@link GridCache#atomicSequence(String, long, boolean)}</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridCache#atomicSequence(String)
 * @see GridCache#atomicSequence(String, long, boolean)
 */
public interface GridCacheAtomicSequence extends GridMetadataAware{
    /**
     * Name of atomic sequence.
     *
     * @return Name of atomic sequence.
     */
    public String name();

    /**
     * Gets current value of atomic sequence.
     *
     * @return Value of atomic sequence.
     * @throws GridException If operation failed.
     */
    public long get() throws GridException;

    /**
     * Gets current value of atomic sequence.
     *
     * @return Value of atomic sequence.
     * @throws GridException If operation failed.
     */
    public long incrementAndGet() throws GridException;

    /**
     * Asynchronously increments and returns atomic sequence value.
     *
     * @return Future that completes once calculation has finished.
     * @throws GridException If operation failed.
     */
    public GridFuture<Long> incrementAndGetAsync() throws GridException;

    /**
     * Gets and increments current value of atomic sequence.
     *
     * @return Value of atomic sequence.
     * @throws GridException If operation failed.
     */
    public long getAndIncrement() throws GridException;

    /**
     * Asynchronously increments atomic sequence value and returns previous atomic sequence value.
     *
     * @return Future that completes once calculation has finished.
     * @throws GridException If operation failed.
     */
    public GridFuture<Long> getAndIncrementAsync() throws GridException;

    /**
     * Adds {@code l} elements to atomic sequence and gets value of atomic sequence.
     *
     * @param l Number of added elements.
     * @return Value of atomic sequence.
     * @throws GridException If operation failed.
     */
    public long addAndGet(long l) throws GridException;

    /**
     * Asynchronously adds {@code l} elements to atomic sequence and gets value of atomic sequence.
     *
     * @param l Number of added elements.
     * @return Future that completes once calculation has finished.
     * @throws GridException If operation failed.
     */
    public GridFuture<Long> addAndGetAsync(long l) throws GridException;

    /**
     * Gets current value of atomic sequence and adds {@code l} elements.
     *
     * @param l Number of added elements.
     * @return Value of atomic sequence.
     * @throws GridException If operation failed.
     */
    public long getAndAdd(long l) throws GridException;

    /**
     * Asynchronously gets current value of atomic sequence and adds {@code l} elements.
     *
     * @param l Number of added elements.
     * @return Future that completes once calculation has finished.
     * @throws GridException If operation failed.
     */
    public GridFuture<Long> getAndAddAsync(long l) throws GridException;

    /**
     * Gets local batch size for this atomic sequence.
     *
     * @return Sequence batch size.
     */
    public int batchSize();

    /**
     * Sets local batch size for atomic sequence.
     *
     * @param size Sequence batch size. Must be more then 0.
     */
    public void batchSize(int size);

    /**
     * Gets status of atomic sequence.
     *
     * @return {@code true} if atomic sequence was removed from cache, {@code false} otherwise.
     */
    public boolean removed();
}
