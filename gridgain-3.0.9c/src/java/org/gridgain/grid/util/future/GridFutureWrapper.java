// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.future;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.*;

/**
 * Convenience future wrapper adapter.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridFutureWrapper<A, B> extends GridMetadataAwareAdapter implements GridFuture<A> {
    /** Wrapped future. */
    @GridToStringInclude
    private final GridFuture<B> wrapped;

    /** Transformer. */
    @GridToStringInclude
    private final GridClosure<B, A> trans;

    /** Listener mappings. */
    private final Collection<T2<GridInClosure<? super GridFuture<A>>, GridInClosure<? super GridFuture<B>>>> lsnrs =
        new ConcurrentLinkedQueue<T2<GridInClosure<? super GridFuture<A>>, GridInClosure<? super GridFuture<B>>>>();

    /**
     * @param wrapped Wrapped future.
     * @param trans Return value transformer.
     */
    public GridFutureWrapper(GridFuture<B> wrapped, GridClosure<B, A> trans) {
        this.wrapped = wrapped;
        this.trans = trans;
    }

    /** {@inheritDoc} */
    @Override public long startTime() {
        return wrapped.startTime();
    }

    /** {@inheritDoc} */
    @Override public long duration() {
        return wrapped.duration();
    }

    /** {@inheritDoc} */
    @Override public boolean concurrentNotify() {
        return wrapped.concurrentNotify();
    }

    /** {@inheritDoc} */
    @Override public void concurrentNotify(boolean concurNotify) {
        wrapped.concurrentNotify(concurNotify);
    }

    /** {@inheritDoc} */
    @Override public void syncNotify(boolean syncNotify) {
        wrapped.syncNotify(syncNotify);
    }

    /** {@inheritDoc} */
    @Override public boolean syncNotify() {
        return wrapped.syncNotify();
    }

    /** {@inheritDoc} */
    @Override public A call() throws Exception {
        return get();
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() throws GridException {
        return wrapped.cancel();
    }

    /** {@inheritDoc} */
    @Override public GridAbsPredicate predicate() {
        return new PA() {
            @Override public boolean apply() {
                return isDone();
            }
        };
    }

    /** {@inheritDoc} */
    @Override public boolean isCancelled() {
        return wrapped.isCancelled();
    }

    /** {@inheritDoc} */
    @Override public boolean isDone() {
        return wrapped.isDone();
    }

    /** {@inheritDoc} */
    @Override public A get() throws GridException {
        try {
            return trans.apply(wrapped.get());
        }
        catch (GridClosureException e) {
            throw U.cast(e.unwrap());
        }
    }

    /** {@inheritDoc} */
    @Override public A get(long timeout) throws GridException {
        return get(timeout, MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override public A get(long timeout, TimeUnit unit) throws GridException {
        try {
            return trans.apply(wrapped.get(timeout, unit));
        }
        catch (GridClosureException e) {
            throw U.cast(e.unwrap());
        }
    }

    /** {@inheritDoc} */
    @Override public void listenAsync(@Nullable final GridInClosure<? super GridFuture<A>> lsnr) {
        if (lsnr != null) {
            CI1<GridFuture<B>> c = new CI1<GridFuture<B>>() {
                @Override public void apply(GridFuture<B> fut) {
                    lsnr.apply(GridFutureWrapper.this);
                }
            };

            lsnrs.add(new T2<GridInClosure<? super GridFuture<A>>, GridInClosure<? super GridFuture<B>>>(lsnr, c));

            wrapped.listenAsync(c);
        }
    }

    /** {@inheritDoc} */
    @Override public void stopListenAsync(@Nullable GridInClosure<? super GridFuture<A>>... lsnr) {
        if (lsnr != null && lsnr.length > 0)
            for (GridInClosure<? super GridFuture<A>> l : lsnr) {
                for (Iterator<T2<GridInClosure<? super GridFuture<A>>, GridInClosure<? super GridFuture<B>>>> it =
                    lsnrs.iterator(); it.hasNext();) {
                    T2<GridInClosure<? super GridFuture<A>>, GridInClosure<? super GridFuture<B>>> t =
                        it.next();

                    if (t.get1().equals(l)) {
                        it.remove();

                        wrapped.stopListenAsync(t.get2());
                    }
                }
            }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridFutureWrapper.class, this);
    }
}
