// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.typedef.*;

/**
 * Defines generic {@code for-all} or {@code reduce} type of closure. Unlike {@code for-each} type of closure
 * that returns optional value on each execution of the closure - the reducer returns a single
 * value for one or more collected values.
 * <p>
 * Closures are a first-class functions that are defined with
 * (or closed over) their free variables that are bound to the closure scope at execution. Since
 * Java 6 doesn't provide a language construct for first-class function the closures are implemented
 * as abstract classes.
 * <h2 class="header">Thread Safety</h2>
 * Note that this interface does not impose or assume any specific thread-safety by its
 * implementations. Each implementation can elect what type of thread-safety it provides,
 * if any.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <E1> Type of the first free variable, i.e. the element the closure is called on.
 * @param <E2> Type of the second free variable, i.e. the element the closure is called on.
 * @param <E3> Type of the third free variable, i.e. the element the closure is called on.
 * @param <R> Type of the closure's return value.
 */
public abstract class GridReducer3<E1, E2, E3, R> extends GridOutClosure<R> {
    /**
     * Collects given values. All values will be reduced by {@link #apply()} method.
     *
     * @param e1 First bound free variable, i.e. the element the closure is called on.
     * @param e2 Second bound free variable, i.e. the element the closure is called on.
     * @param e3 Third bound free variable, i.e. the element the closure is called on.
     * @return {@code true} to continue collecting, {@code false} to instruct caller to stop
     *      collecting and call {@link #apply()} method.
     */
    public abstract boolean collect(E1 e1, E2 e2, E3 e3);

   /**
     * Curries this closure with given tuple values. When result closure is called it will
     * be executed with given values.
     *
     * @param t Tuples each with three values.
     * @return Curried or partially applied closure with given values.
     */
    public GridOutClosure<R> curry(final GridTuple3<E1, E2, E3>... t) {
       CO<R> rdc = new CO<R>() {
           @Override public R apply() {
               for (GridTuple3<E1, E2, E3> p : t) {
                   collect(p.get1(), p.get2(), p.get3());
               }

               return GridReducer3.this.apply();
           }
       };

       rdc.peerDeployLike(this);

       return withMeta(rdc);
    }
}
