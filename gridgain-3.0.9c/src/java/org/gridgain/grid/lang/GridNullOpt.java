// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.typedef.internal.*;
import java.util.*;

/**
 * This is a companion class to {@link GridOpt} allowing {@code null} values.
 * Unlike {@link GridOpt} this class allows {@code null} for "some" values. This has less protection
 * against referencing {@code null} reference but allows to distinguish between valid value (even if
 * it is {@code null}) and "none" value. 
 * <p>
 * Note that this class also implements {@link Iterable} interface so it can be used in
 * monadic way with various method in {@link GridFunc} class. If this option's {@link #isNone()}
 * method returns {@code true} the iterator will be empty (which can be safely passed into any
 * method that expects the iterable object). If this option's {@link #isSome()} method returns
 * {@code true} then iterator will have just one value (i.e. the value returned by {@link #get()}
 * method which can be {@code null}.
 *  
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridOpt
 */
public class GridNullOpt<T> extends GridAbsPredicate implements GridTypedProduct<T> {
    /** */
    private T val;

    /** */
    @SuppressWarnings("unchecked")
    private static final GridNullOpt NONE = new GridNullOpt(null);

    /** {@inheritDoc} */
    @Override public Iterator<T> iterator() {
        return new Iterator<T>() {
            private boolean returned;

            @Override public boolean hasNext() {
                return isSome() && !returned;
            }

            @Override public void remove() {
                throw new UnsupportedOperationException();
            }
            
            @Override public T next() {
                if (hasNext()) {
                    returned = true;

                    return val;
                }
                else {
                    throw new NoSuchElementException();
                }
            }
        };
    }

    /** {@inheritDoc} */
    @Override public int arity() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override public T part(int n) {
        if (n != 0)
            throw new IndexOutOfBoundsException("Invalid product index: " + n);

        return get();
    }

    /**
     *
     * @param val Option value.
     */
    private GridNullOpt(T val) {
        this.val = val;
    }

    /**
     * Gets option value. If this option is "none" this method will throw
     * {@link IllegalArgumentException} exception. Note that this method
     * may return {@code null}.
     *
     * @return An option value.
     * @throws IllegalArgumentException Thrown in case if this option is "none".
     */
    public T get() {
        A.ensure(this != NONE, "Value 'none' of GridOpt cannot be used.");

        assert val != null;

        return val;
    }

    /**
     * Factory method that creates "some" option with given value.
     *
     * @param t A "some" value (possibly {@code null}).
     * @param <T> Type of the value.
     * @return Option that contains given value.
     */
    public static <T> GridNullOpt<T> some(T t) {
        return new GridNullOpt<T>(t);
    }

    /**
     * Tests whether this option contains "some" value.
     *
     * @return {@code true} if this option is "some" value, {@code false} otherwise.
     */
    public boolean isSome() {
        return !isNone();
    }

    /**
     * Tests whether this option contains "none" value.
     *
     * @return {@code true} if this option is "none" value, {@code false} otherwise.
     */
    public boolean isNone() {
        return this == NONE;
    }

    /** {@inheritDoc} */
    @Override public boolean apply() {
        return isSome();
    }

    /**
     * Factory method that creates "none" option.
     *
     * @return "None" option.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridNullOpt<T> none() {
        return (GridNullOpt<T>)NONE;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNullOpt.class, this);
    }
}
