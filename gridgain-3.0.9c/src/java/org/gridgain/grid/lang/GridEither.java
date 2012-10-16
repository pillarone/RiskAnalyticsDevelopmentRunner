// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import java.util.*;

/**
 * This class defines an abstract "union" of two values called "left" and "right". This idiom
 * is often used to return values of two different types from the method (while method in Java
 * can return only one value). Most often it is used to return either exception or normal return
 * value from closures.
 *  
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridClosureException
 */
public class GridEither<L/*left*/, R/*right*/> extends GridOutClosure<GridTuple2<L, R>> implements GridProduct {
    /** */
    private L lval;

    /** */
    private R rval;

    /**
     * Internal constructor. One value has to be a not {@code null}.
     *
     * @param lval Left value.
     * @param rval Right value.
     */
    private GridEither(L lval, R rval) {
        A.ensure(lval == null && rval != null || lval != null && rval == null,
            "(lval == null && rval != null) || (lval != null && rval == null)");

        this.lval = lval;
        this.rval = rval;
    }

    /**
     * Only for subclasses.
     */
    protected GridEither() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public GridTuple2<L, R> apply() {
        return F.t(lval, rval);
    }

    /** {@inheritDoc} */
    @Override public Object part(int n) {
        if (n != 0)
            throw new IndexOutOfBoundsException("Invalid product index: " + n);

        return isLeft() ? lval : rval;
    }

    /** {@inheritDoc} */
    @Override public int arity() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override public Iterator<Object> iterator() {
        return new Iterator<Object>() {
            private boolean hasNext = true;

            @Override public boolean hasNext() {
                return hasNext;
            }

            @Override public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                hasNext = false;

                return isLeft() ? lval : rval;
            }

            @Override public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Create "either" with left value.
     *
     * @param lval Non-{@code null} left value
     * @param <L> Type of the left value.
     * @param <R> Type of the right value.
     * @return "Either" initialized with left non-{@code null} value.
     */
    public static <L, R> GridEither<L, R> makeLeft(L lval) {
        return new GridEither<L, R>(lval, null);
    }

    /**
     * Create "either" with right value.
     *
     * @param rval Non-{@code null} right value
     * @param <L> Type of the left value.
     * @param <R> Type of the right value.
     * @return "Either" initialized with right non-{@code null} value.
     */
    public static <L, R> GridEither<L, R> makeRight(R rval) {
        return new GridEither<L, R>(null, rval);
    }

    /**
     * Gets "left" value.
     *
     * @return "left" value.
     * @throws IllegalArgumentException if there's no "left" value.
     */
    public L left() {
        A.ensure(lval != null, "This 'Either' doesn't have 'left' value.");

        return lval;
    }

    /**
     * Gets "right" value.
     *
     * @return "right" value.
     * @throws IllegalArgumentException if there's no "right" value.
     */
    public R right() {
        A.ensure(rval != null, "This 'Either' doesn't have 'right' value.");

        return rval;
    }

    /**
     * Tests if there's "left" value.
     *
     * @return {@code true} if there's "left" value, {@code false} otherwise.
     */
    public boolean isLeft() {
        return lval != null;
    }

    /**
     * Tests if there's "right" value.
     *
     * @return {@code true} if there's "right" value, {@code false} otherwise.
     */
    public boolean isRight() {
        return rval != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridEither.class, this);
    }
}
