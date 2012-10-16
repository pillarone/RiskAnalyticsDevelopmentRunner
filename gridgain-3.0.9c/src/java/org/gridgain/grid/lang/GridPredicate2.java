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
 * Defines predicate construct. Predicate like closure is a first-class function
 * that is defined with (or closed over) its free variables that are bound to the closure
 * scope at execution.
 * <p>
 * This form of predicate is essentially a syntactic "sugar" providing shorter syntax for:
 * <pre name="code" class="java">
 * ...
 * GridPredicate&lt;GridTuple2&lt;E1, E2&gt;&gt;
 * ...
 * </pre>
 * <h2 class="header">Type Alias</h2>
 * To provide for more terse code you can use a typedef {@link P2} class or various factory methods in
 * {@link GridFunc} class. Note, however, that since typedefs in Java rely on inheritance you should
 * not use these type aliases in signatures.
 * <h2 class="header">Thread Safety</h2>
 * Note that this interface does not impose or assume any specific thread-safety by its
 * implementations. Each implementation can elect what type of thread-safety it provides,
 * if any.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <E1> Type of the first free variable, i.e. the element the closure is called on.
 * @param <E2> Type of the second free variable, i.e. the element the closure is called on.
 * @see P2
 * @see GridFunc
 */
public abstract class GridPredicate2<E1, E2> extends GridLambdaAdapter {
    /**
     * Predicate body.
     *
     * @param e1 First bound free variable, i.e. the element the closure is called or closed on.
     * @param e2 Second bound free variable, i.e. the element the closure is called or closed on.
     * @return Return value.
     */
    public abstract boolean apply(E1 e1, E2 e2);

    /**
     * Curries this predicate with given value. When result predicate is called it will
     * be executed with given value.
     *
     * @param e1 Value to curry with.
     * @return Curried or partially applied predicate with given value.
     */
    public GridPredicate<E2> curry(final E1 e1) {
        return withMeta(new P1<E2>() {
            {
                peerDeployLike(GridPredicate2.this);
            }

            @Override public boolean apply(E2 e2) {
                return GridPredicate2.this.apply(e1, e2);
            }
        });
    }

    /**
     * Curries this predicate with given values. When result predicate is called it will
     * be executed with given values.
     *
     * @param e1 Value to curry with.
     * @param e2 Value to curry with.
     * @return Curried or partially applied predicate with given values.
     */
    public GridAbsPredicate curry(final E1 e1, final E2 e2) {
        return withMeta(new GridAbsPredicate() {
            {
                peerDeployLike(GridPredicate2.this);
            }

            @Override public boolean apply() {
                return GridPredicate2.this.apply(e1, e2);
            }
        });
    }

    /**
     * Gets predicate that ignores its third argument and returns the same value as
     * this predicate with first and second arguments.
     *
     * @param <E3> Type of 3d argument that is ignored.
     * @return Predicate that ignores its third argument and returns the same value as
     *      this predicate with first and second arguments.
     */
    public <E3> GridPredicate3<E1, E2, E3> uncurry3() {
        GridPredicate3<E1, E2, E3> p = new P3<E1, E2, E3>() {
            @Override public boolean apply(E1 e1, E2 e2, E3 e3) {
                return GridPredicate2.this.apply(e1, e2);
            }
        };

        p.peerDeployLike(this);

        return withMeta(p);
    }
}
