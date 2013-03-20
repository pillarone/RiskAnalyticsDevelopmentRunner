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
import org.gridgain.grid.lang.*;

/**
 * This interface provides a rich API for working with distributed atomic reference.
 * <p>
 * Note that atomic reference is only available in <b>Enterprise Edition.</b>
 * <p>
 * <h1 class="header">Functionality</h1>
 * Distributed atomic reference includes the following main functionality:
 * <ul>
 * <li>
 * Method {@link #get()} synchronously gets current value of an atomic reference.
 * </li>
 * <li>
 * Method {@link #set(Object)} synchronously and unconditionally sets the value in the an atomic reference.
 * </li>
 * <li>
 * Methods {@code compareAndSet(...)} synchronously and conditionally set the value in the an atomic reference.
 * </li>
 * </ul>
 * All previously described methods have asynchronous analogs.
 * <ul>
 * <li>
 * Method {@link #name()} gets name of atomic reference.
 * </li>
 * </ul>
 * <h1 class="header">Creating Distributed Atomic Reference</h1>
 * Instance of distributed atomic reference can be created by calling one of the following methods:
 * <ul>
 *     <li>{@link GridCache#atomicReference(String)}</li>
 *     <li>{@link GridCache#atomicReference(String, Object, boolean)}</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridCache#atomicReference(String)
 * @see GridCache#atomicReference(String, Object, boolean)
 */
public interface GridCacheAtomicReference<T> extends GridMetadataAware{
    /**
     * Name of atomic reference.
     *
     * @return Name of an atomic reference.
     */
    public String name();

    /**
     * Gets current value of an atomic reference.
     *
     * @return current value of an atomic reference.
     * @throws GridException If operation failed.
     */
    public T get() throws GridException;

    /**
     * Gets both current value of an atomic reference asynchronously.
     *
     * @return Future that completes once calculation has finished.
     * @throws GridException If operation failed.
     */
    public GridFuture<T> getAsync() throws GridException;

    /**
     * Unconditionally sets the value.
     *
     * @param val Value.
     * @throws GridException If operation failed.
     */
    public void set(T val) throws GridException;

    /**
     * Unconditionally sets the value asynchronously.
     *
     * @param val Value.
     * @return Future that completes once calculation has finished. If {@code true} than
     *      value have been updated.
     * @throws GridException If operation failed.
     */
    public GridFuture<Boolean> setAsync(T val) throws GridException;

    /**
     * Conditionally sets the new value. That will be set if {@code expVal} is equal
     * to current value respectively.
     *
     * @param expVal Expected value.
     * @param newVal New value.
     * @return Result of operation execution. If {@code true} than value have been updated.
     * @throws GridException If operation failed.
     */
    public boolean compareAndSet(T expVal, T newVal) throws GridException;

    /**
     * Conditionally sets the new value. It will be set if {@code expVal} is equal
     * to current value respectively.
     *
     * @param expVal Expected value.
     * @param newValClos Closure which generates new value.
     * @return Result of operation execution. If {@code true} than value have been updated.
     * @throws GridException If operation failed.
     */
    public boolean compareAndSet(T expVal, GridClosure<T, T> newValClos) throws GridException;

    /**
     * Conditionally sets the new value. It will be set if {@code expValPred} is
     * evaluate to {@code true}.
     *
     * @param expValPred Predicate which should evaluate to {@code true} for value to be set.
     * @param newValClos Closure which generates new value.
     * @return Result of operation execution. If {@code true} than value have been updated.
     * @throws GridException If operation failed.
     */
    public boolean compareAndSet(GridPredicate<T> expValPred, GridClosure<T, T> newValClos) throws GridException;

    /**
     * Conditionally sets the new value. It will be set if {@code expValPred} is
     * evaluate to {@code true}.
     *
     * @param expValPred Predicate which should evaluate to {@code true} for value to be set
     * @param newVal New value.
     * @return Result of operation execution. If {@code true} than value have been updated.
     * @throws GridException If operation failed.
     */
    public boolean compareAndSet(GridPredicate<T> expValPred, T newVal) throws GridException;

    /**
     * Conditionally asynchronously sets the new value. It will be set if {@code expVal}
     * is equal to current value respectively.
     *
     * @param expVal Expected value.
     * @param newVal New value.
     * @return Future that completes once calculation has finished. If {@code true} than value
     *      have been updated.
     * @throws GridException If operation failed.
     */
    public GridFuture<Boolean> compareAndSetAsync(T expVal, T newVal)
        throws GridException;

    /**
     * Conditionally asynchronously sets the new value. It will be set if {@code expVal}
     * is equal to current value respectively.
     *
     * @param expVal Expected value.
     * @param newValClos Closure generates new value.
     * @return Future that completes once calculation has finished. If {@code true} than value have been updated.
     * @throws GridException If operation failed.
     */
    public GridFuture<Boolean> compareAndSetAsync(T expVal, GridClosure<T, T> newValClos) throws GridException;

    /**
     * Conditionally asynchronously sets the new value. It will be set if {@code expValPred}
     * is evaluate to {@code true}.
     *
     * @param expValPred Predicate which should evaluate to {@code true} for value to be set.
     * @param newValClos Closure generates new value.
     * @return Future that completes once calculation has finished. If {@code true} than value have been updated.
     * @throws GridException If operation failed.
     */
    public GridFuture<Boolean> compareAndSetAsync(GridPredicate<T> expValPred, GridClosure<T, T> newValClos)
        throws GridException;

    /**
     * Conditionally asynchronously sets the new value. It will be set if {@code expValPred}
     * is evaluate to {@code true}.
     *
     * @param expValPred Predicate which should evaluate to {@code true} for value to be set.
     * @param newVal New value.
     * @return Future that completes once calculation has finished. If {@code true} than value have been updated.
     * @throws GridException If operation failed.
     */
    public GridFuture<Boolean> compareAndSetAsync(GridPredicate<T> expValPred, T newVal) throws GridException;

    /**
     * Gets status of atomic.
     *
     * @return {@code true} if an atomic reference was removed from cache, {@code false} otherwise.
     */
    public boolean removed();
}
