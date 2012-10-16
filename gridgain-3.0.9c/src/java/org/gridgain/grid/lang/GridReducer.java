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
 * @param <E1> Type of the free variable, i.e. the element the closure is called on.
 * @param <R> Type of the closure's return value.
 */
public abstract class GridReducer<E1, R> extends GridOutClosure<R> {
    /**
     * Collects given value. All values will be reduced by {@link #apply()} method.
     *
     * @param e Value to collect.
     * @return {@code true} to continue collecting, {@code false} to instruct caller to stop
     *      collecting and call {@link #apply()} method.
     */
    public abstract boolean collect(E1 e);

    /**
     * Curries this closure with given value. When result closure is called it will
     * be executed with given value.
     *
     * @param e1 Values to curry with.
     * @return Curried or partially applied closure with given value.
     */
    public GridOutClosure<R> curry(final E1... e1) {
        GridOutClosure<R> rdc = new CO<R>() {
            @Override public R apply() {
                for (E1 e : e1) {
                    collect(e);
                }

                return GridReducer.this.apply();
            }
        };

        rdc.peerDeployLike(this);

        return withMeta(rdc);
    }

    /**
     * Gets reducer that ignores its second argument passed to method
     * {@link GridReducer2#collect(Object, Object)} and works as this reducer with
     * just one first argument.
     *
     * @param <E2> Type of 2nd argument that is ignored.
     * @return Reducer that ignores its second argument passed to method
     *      {@link GridReducer2#collect(Object, Object)} and works as this reducer with
     *      just one first argument. 
     */
    public <E2> GridReducer2<E1, E2, R> uncurry2() {
        GridReducer2<E1, E2, R> rdc = new R2<E1, E2, R>() {
            @Override public boolean collect(E1 e1, E2 e2) {
                return GridReducer.this.collect(e1);
            }

            @Override public R apply() {
                return GridReducer.this.apply();
            }
        };

        rdc.peerDeployLike(this);

        return withMeta(rdc);
    }

    /**
     * Gets reducer that ignores its second and third argument passed to method
     * {@link GridReducer3#collect(Object, Object, Object)} and works as this reducer with
     * just one first argument.
     *
     * @param <E2> Type of 2nd argument that is ignored.
     * @param <E3> Type of 3d argument that is ignored.
     * @return Reducer that ignores its second and third argument passed to method
     *      {@link GridReducer3#collect(Object, Object, Object)} and works as this reducer
     *      with just one first argument.
     */
    public <E2, E3> GridReducer3<E1, E2, E3, R> uncurry3() {
        GridReducer3<E1, E2, E3, R> rdc = new R3<E1, E2, E3, R>() {
            @Override public boolean collect(E1 e1, E2 e2, E3 e3) {
                return GridReducer.this.collect(e1);
            }

            @Override public R apply() {
                return GridReducer.this.apply();
            }
        };

        rdc.peerDeployLike(this);

        return withMeta(rdc);
    }
}
