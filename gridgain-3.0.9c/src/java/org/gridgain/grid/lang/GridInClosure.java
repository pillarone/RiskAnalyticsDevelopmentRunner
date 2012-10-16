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
 * Defines a convenient {@code one-way} closure, i.e. the closure that has {@code void} return type.
 * <h2 class="header">Thread Safety</h2>
 * Note that this interface does not impose or assume any specific thread-safety by its
 * implementations. Each implementation can elect what type of thread-safety it provides,
 * if any.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <E1> Type of the free variable, i.e. the element the closure is called or closed on.
 * @see GridFunc
 */
public abstract class GridInClosure<E1> extends GridLambdaAdapter {
    /**
     * In-closure body.
     *
     * @param t Bound free variable, i.t. the element the closure is called or closed on.
     */
    public abstract void apply(E1 t);

    /**
     * Curries this closure with given value. When result closure is called it will
     * be executed with given value.
     *
     * @param t Value to curry with.
     * @return Curried or partially applied closure with given value.
     */
    public GridAbsClosure curry(final E1 t) {
        return withMeta(new CA() {
            {
                peerDeployLike(GridInClosure.this);
            }

            @Override public void apply() {
                GridInClosure.this.apply(t);
            }
        });
    }

    /**
     * Gets closure that ignores its second argument and executes the same way as this
     * in-closure with just one first argument.
     *
     * @param <E2> Type of 2nd argument that is ignored.
     * @return Closure that ignores its second argument and executes the same way as this
     *      in-closure with just one first argument.
     */
    public <E2> GridInClosure2<E1, E2> uncurry2() {
        GridInClosure2<E1, E2> c = new CI2<E1, E2>() {
            @Override public void apply(E1 e1, E2 e2) {
                GridInClosure.this.apply(e1);
            }
        };

        c.peerDeployLike(this);

        return withMeta(c);
    }

    /**
     * Gets closure that ignores its second and third arguments and executes the same
     * way as this in-closure with just one first argument.
     *
     * @param <E2> Type of 2nd argument that is ignored.
     * @param <E3> Type of 3d argument that is ignored.
     * @return Closure that ignores its second and third arguments and executes the same
     *      way as this in-closure with just one first argument.
     */
    public <E2, E3> GridInClosure3<E1, E2, E3> uncurry3() {
        GridInClosure3<E1, E2, E3> c = new CI3<E1, E2, E3>() {
            @Override public void apply(E1 e1, E2 e2, E3 e3) {
                GridInClosure.this.apply(e1);
            }
        };

        c.peerDeployLike(this);

        return withMeta(c);
    }
}
