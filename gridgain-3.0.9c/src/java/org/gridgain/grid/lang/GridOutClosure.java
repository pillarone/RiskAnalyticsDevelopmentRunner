// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import java.util.concurrent.*;

/**
 * Defines a convenient {@code one-way} factory closure. This closure takes no parameters
 * and returns instance of given type every time its {@link #apply()} method is called. Most
 * implementations will return a new instance every time, however, there's no requirement for that.
 * Note also that factory closure doesn't have free variables (i.e. it has {@code void} as its
 * fre variable).
 * <h2 class="header">Thread Safety</h2>
 * Note that this interface does not impose or assume any specific thread-safety by its
 * implementations. Each implementation can elect what type of thread-safety it provides,
 * if any.
 * <p>
 * Note that this class implements {@link GridJob} interface for convenience and can be
 * used in {@link GridTask} implementations directly, if needed, as an alternative to
 * {@link GridJobAdapterEx}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @param <T> Type of return value from this closure.
 * @see GridFunc
 */
public abstract class GridOutClosure<T> extends GridLambdaAdapter implements Callable<T>, GridJob {
    /**
     * One-way factory closure body.
     *
     * @return Element.
     */
    public abstract T apply();

    /**
     * Delegates to {@link #apply()} method.
     * <p>
     * {@inheritDoc} 
     */
    @Override public final T call() {
        return apply();
    }

    /**
     * Does nothing by default. Child classes may override this method
     * to provide implementation-specific cancellation logic.
     * <p>
     * Note that this method is here only to support {@link GridJob} interface
     * and only makes sense whenever this class is used as grid job or is
     * executed via any of {@link GridProjection} methods.
     * <p>
     * {@inheritDoc}
     */
    @Override public void cancel() {
        // No-op.
    }

    /**
     * Gets closure that ignores its argument and returns the same value as this
     * out closure.
     *
     * @param <E> Type of ignore argument.
     * @return Closure that ignores its argument and returns the same value as this
     *      out closure.
     */
    public <E> GridClosure<E, T> uncurry() {
        GridClosure<E, T> c = new C1<E, T>() {
            @Override public T apply(E e) {
                return GridOutClosure.this.apply();
            }
        };

        c.peerDeployLike(this);

        return withMeta(c);
    }

    /**
     * Gets closure that ignores its arguments and returns the same value as this
     * out closure.
     *
     * @param <E1> Type of 1st ignore argument.
     * @param <E2> Type of 2nd ignore argument.
     * @return Closure that ignores its arguments and returns the same value as this
     *      out closure.
     */
    public <E1, E2> GridClosure2<E1, E2, T> uncurry2() {
        GridClosure2<E1, E2, T> c = new C2<E1, E2, T>() {
            @Override public T apply(E1 e1, E2 e2) {
                return GridOutClosure.this.apply();
            }
        };

        c.peerDeployLike(this);

        return withMeta(c);
    }

    /**
     * Gets closure that ignores its arguments and returns the same value as this
     * out closure.
     *
     * @param <E1> Type of 1st ignore argument.
     * @param <E2> Type of 2nd ignore argument.
     * @param <E3> Type of 3rd ignore argument.
     * @return Closure that ignores its arguments and returns the same value as this
     *      out closure.
     */
    public <E1, E2, E3> GridClosure3<E1, E2, E3, T> uncurry3() {
        GridClosure3<E1, E2, E3, T> c = new C3<E1, E2, E3, T>() {
            @Override public T apply(E1 e1, E2 e2, E3 e3) {
                return GridOutClosure.this.apply();
            }
        };

        c.peerDeployLike(this);

        return withMeta(c);
    }

    /**
     * Delegates to {@link #apply()} method.
     * <p>
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws GridException {@inheritDoc}
     */
    @Override public final Object execute() throws GridException {
        try {
            return apply();
        }
        catch (Throwable e) {
            throw U.cast(e);
        }
    }
}
