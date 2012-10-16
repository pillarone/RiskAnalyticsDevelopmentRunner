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
 * Defines generic {@code for-each} type of closure. Closure is a first-class function that is defined with
 * (or closed over) its free variables that are bound to the closure scope at execution. Since
 * Java 6 doesn't provide a language construct for first-class function the closures are implemented
 * as abstract classes.
 * <h2 class="header">Type Alias</h2>
 * To provide for more terse code you can use a typedef {@link C1} class or various factory methods in
 * {@link GridFunc} class. Note, however, that since typedefs in Java rely on inheritance you should
 * not use these type aliases in signatures.
 * <h2 class="header">Thread Safety</h2>
 * Note that this interface does not impose or assume any specific thread-safety by its
 * implementations. Each implementation can elect what type of thread-safety it provides,
 * if any.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <E1> Type of the free variable, i.e. the element the closure is called or closed on.
 * @param <R> Type of the closure's return value.
 * @see C1
 * @see GridFunc
 */
public abstract class GridClosure<E1, R> extends GridLambdaAdapter {
    /**
     * Closure body.
     *
     * @param e Bound free variable, i.e. the element the closure is called or closed on.
     * @return Optional return value.
     */
    public abstract R apply(E1 e);

    /**
     * Curries this closure with given value. When result closure is called it will
     * be executed with given value.
     *
     * @param e Value to curry with.
     * @return Curried or partially applied closure with given value.
     */
    public GridOutClosure<R> curry(final E1 e) {
        return withMeta(new CO<R>() {
            {
                peerDeployLike(GridClosure.this);
            }

            @Override public R apply() {
                return GridClosure.this.apply(e);
            }
        });
    }

    /**
     * Gets closure that ignores its second argument and returns the same value as this
     * closure with just one first argument.
     *
     * @param <E2> Type of 2nd argument that is ignored.
     * @return Closure that ignores its second argument and returns the same value as this
     *      closure with just one first argument.
     */
    public <E2> GridClosure2<E1, E2, R> uncurry2() {
        GridClosure2<E1, E2, R> c = new C2<E1, E2, R>() {
            @Override public R apply(E1 e1, E2 e2) {
                return GridClosure.this.apply(e1);
            }
        };

        c.peerDeployLike(this);

        return withMeta(c);
    }

    /**
     * Gets closure that ignores its second and third argument and returns the same value
     * as this closure with just one first argument.
     *
     * @param <E2> Type of 2nd argument that is ignored.
     * @param <E3> Type of 3d argument that is ignored.
     * @return Closure that ignores its second and third argument and returns the same value
     *      as this closure with just one first argument.
     */
    public <E2, E3> GridClosure3<E1, E2, E3, R> uncurry3() {
        GridClosure3<E1, E2, E3, R> c = new C3<E1, E2, E3, R>() {
            @Override public R apply(E1 e1, E2 e2, E3 e3) {
                return GridClosure.this.apply(e1);
            }
        };

        c.peerDeployLike(this);

        return withMeta(c);
    }
}
