// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;
import java.util.*;

/**
 * This class provide limited version of Scala {@code Option} class. It defines an optional
 * reference value (i.e. nullable value in Java). Using this class has two advantages: it
 * indicates to the caller that returned reference can be {@code null}, and it gently
 * forces the caller to check for it before using that reference. Note that calling
 * method {@link #get()} on the object whose method {@link #isNone()} returns {@code true} will
 * produce {@link IllegalArgumentException} exception.
 * <p>
 * Note that this class also implements {@link Iterable} interface so it can be used in
 * monadic way with various method in {@link GridFunc} class. If this option's {@link #isNone()}
 * method returns {@code true} the iterator will be empty (which can be safely passed into any
 * method that expects the iterable object). If this option's {@link #isSome()} method returns
 * {@code true} then iterator will have just one value (i.e. the value returned by {@link #get()}
 * method.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridNullOpt
 * @see GridFunc#opt(GridOpt, GridInClosure)
 * @see GridFunc#opt(GridOpt, GridClosure, Object)
 */
public class GridOpt<T> extends GridAbsPredicate implements GridTypedProduct<T> {
    /** */
    private T opt;

    /** */
    @SuppressWarnings("unchecked")
    private static final GridOpt NONE = new GridOpt(null);

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

                    return opt;
                }
                else
                    throw new NoSuchElementException();
            }
        };
    }

    /** {@inheritDoc} */
    @Override public T part(int n) {
        if (n != 0)
            throw new IndexOutOfBoundsException("Invalid product index: " + n);

        return get();
    }

    /** {@inheritDoc} */
    @Override public int arity() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override public boolean apply() {
        return isSome();
    }

    /**
     * @param opt Option's value.
     */
    private GridOpt(@Nullable T opt) {
        this.opt = opt;
    }

    /**
     * Tests whether this option has a non-{@code null} value.
     *
     * @return {@code true} if this option has a non-{@code null} value, {@code false} otherwise.
     */
    public boolean isSome() {
        return !isNone();
    }

    /**
     * Tests whether this option has a {@code null} value.
     *
     * @return {@code true} if this option has a {@code null} value, {@code false} otherwise.
     */
    public boolean isNone() {
        return this == NONE;
    }

    /**
     * Gets a non-{@code null} value. If this option contains a {@code null} value this
     * method will throw {@link IllegalArgumentException} exception. Note that this method
     * will never return {@code null}.
     *
     * @return A non-{@code null} value.
     * @throws IllegalArgumentException Thrown in case if this option contains a {@code null} value.
     */
    public T get() {
        A.ensure(this != NONE, "Value 'none' of GridOpt cannot be used.");

        assert opt != null;

        return opt;
    }

    /**
     * If {@link #isSome()} returns {@code this}, otherwise given option.
     *
     * @param o Option to return if {@link #isNone()}.
     * @return If {@link #isSome()} returns {@code this}, otherwise given option.
     */
    public GridOpt<T> orElse(GridOpt<T> o) {
        return isSome() ? this : o;
    }

    /**
     * Gets value if {@link #isSome()}, otherwise given default value.
     *
     * @param dflt Default value to return if {@link #isNone()}.
     * @return Value if {@link #isSome()}, otherwise given default value.
     */
    public T getOrElse(T dflt) {
        return isSome() ? get() : dflt;
    }

    /**
     * Factory method that creates a non-{@code null} option with given value.
     *
     * @param t A non-{@code null} value.
     * @param <T> Type of the value.
     * @return Option that contains given non-{@code null} value.
     * @throws IllegalArgumentException Thrown if given value is {@code null}.
     */
    public static <T> GridOpt<T> some(final T t) {
        A.ensure(t != null, "Value of 'some' cannot be 'null'.");

        return new GridOpt<T>(t) {
            {
                peerDeployLike(U.peerDeployAware(t));
            }
        };
    }

    /**
     * Factory method that returns a non-{@code null} option if given value is not {@code null}, or
     * {@code null} option otherwise.
     *
     * @param t Option value (may be {@code null}).
     * @param <T> Type of the option value.
     * @return An option.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridOpt<T> make(T t) {
        return t == null ? GridOpt.<T>none() : some(t);
    }

    /**
     * Factory method that creates a {@code null} option. Note that this method
     * returns predefined static final instance.
     *
     * @return Option that contains {@code null} value.
     */
    @SuppressWarnings("unchecked")
    public static <T> GridOpt<T> none() {
        return NONE;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridOpt.class, this);
    }
}
