// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.query;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Adapter for reduce cache queries.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheReduceQueryAdapter<K, V, R1, R2> extends GridCacheQueryBaseAdapter<K, V>
    implements GridCacheReduceQuery<K, V, R1, R2> {
    /** Remote reducer. */
    private GridClosure<Object[], GridReducer<Map.Entry<K, V>, R1>> rmtRdc;

    /** Local reducer. */
    private GridClosure<Object[], GridReducer<R1, R2>> locRdc;

    /** */
    private boolean rmtOnly;

    /**
     * @param ctx Cache registry.
     * @param type Query type.
     * @param clause Query clause.
     * @param clsName Query class name.
     * @param prjFilter Projection filter.
     * @param prjFlags Projection flags.
     */
    public GridCacheReduceQueryAdapter(GridCacheContext<K, V> ctx, GridCacheQueryType type, String clause,
        String clsName, GridPredicate<GridCacheEntry<K, V>> prjFilter, Collection<GridCacheFlag> prjFlags) {
        super(ctx, type, clause, clsName, prjFilter, prjFlags);
    }

    /**
     * @param query Query to copy from (ignoring arguments).
     */
    @SuppressWarnings( {"TypeMayBeWeakened"})
    private GridCacheReduceQueryAdapter(GridCacheReduceQueryAdapter<K, V, R1, R2> query) {
        super(query);

        rmtRdc = query.rmtRdc;
        locRdc = query.locRdc;
        rmtOnly = query.rmtOnly;
    }

    /** {@inheritDoc} */
    @Override protected void registerClasses() throws GridException {
        context().deploy().registerClass(rmtRdc);
    }

    /** {@inheritDoc} */
    @Override public void remoteReducer(GridClosure<Object[], GridReducer<Map.Entry<K, V>, R1>> rmtRdc) {
        this.rmtRdc = rmtRdc;
    }

    /**
     * @return Remote reducer.
     */
    public GridClosure<Object[], GridReducer<Map.Entry<K, V>, R1>> remoteReducer() {
        return rmtRdc;
    }

    /** {@inheritDoc} */
    @Override public void localReducer(GridClosure<Object[], GridReducer<R1, R2>> locRdc) {
        this.locRdc = locRdc;
    }

    /**
     * @return Local reducer.
     */
    public GridClosure<Object[], GridReducer<R1, R2>> localReducer() {
        return locRdc;
    }

    /** {@inheritDoc} */
    @Override public GridCacheReduceQuery<K, V, R1, R2> queryArguments(@Nullable Object... args) {
        GridCacheReduceQueryAdapter<K, V, R1, R2> copy = new GridCacheReduceQueryAdapter<K, V, R1, R2>(this);

        copy.arguments(args);

        return copy;
    }

    /** {@inheritDoc} */
    @Override public GridCacheReduceQuery<K, V, R1, R2> closureArguments(@Nullable Object... args) {
        GridCacheReduceQueryAdapter<K, V, R1, R2> copy = new GridCacheReduceQueryAdapter<K, V, R1, R2>(this);

        copy.setClosureArguments(args);

        return copy;
    }

    /**
     * @return Execute local reducer or not.
     */
    public boolean isRemoteOnly() {
        return rmtOnly;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<R2> reduce(GridProjection[] grid) {
        rmtOnly = false;

        Collection<GridRichNode> nodes = F.retain(CU.allNodes(cacheCtx), true, nodes(grid));

        if (qryLog.isDebugEnabled())
            qryLog.debug(U.compact("Executing reduce query " + toShortString(nodes)));

        final GridFuture<R2> fut = reduce(F.retain(CU.allNodes(context()), true, nodes(grid)));

        fut.listenAsync(new CI1<GridFuture<?>>() {
            @Override public void apply(GridFuture<?> e) {
                queryExecuted("Executed reduce query ", fut);
            }
        });

        return fut;
    }

    /**
     * @param nodes Nodes.
     * @return Result future.
     */
    private GridFuture<R2> reduce(Collection<GridRichNode> nodes) {
        if (rmtRdc == null) {
            GridFutureAdapter<R2> err = new GridFutureAdapter<R2>(cacheCtx.kernalContext());

            err.onDone(new GridException("Remote reducer must be set."));

            return err;
        }

        if (locRdc == null) {
            GridFutureAdapter<R2> err = new GridFutureAdapter<R2>(cacheCtx.kernalContext());

            err.onDone(new GridException("Local reducer must be set."));

            return err;
        }

        final ReduceFuture<R2> rdcFut = new ReduceFuture<R2>();

        GridCacheQueryFuture<R2> fut = execute(nodes, new GridInClosure<GridFuture<Collection<R2>>>() {
                @Override public void apply(GridFuture<Collection<R2>> f) {
                    try {
                        Collection<R2> coll = f.get();

                        rdcFut.onDone(coll.iterator().next());
                    }
                    catch (GridException e) {
                        rdcFut.onDone(e);
                    }
                }
            }, false);

        rdcFut.future(fut);

        return rdcFut;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Collection<R1>> reduceRemote(GridProjection[] grid) {
        if (rmtRdc == null) {
            GridFutureAdapter<Collection<R1>> err = new GridFutureAdapter<Collection<R1>>(cacheCtx.kernalContext());

            err.onDone(new GridException("Remote reducer must be set."));

            return err;
        }

        rmtOnly = true;

        Collection<GridRichNode> nodes = F.retain(CU.allNodes(cacheCtx), true, nodes(grid));

        if (qryLog.isDebugEnabled())
            qryLog.debug(U.compact("Executing reduce remote query " + toShortString(nodes)));

        final GridFuture<Collection<R1>> fut = execute(nodes, null, false);

        fut.listenAsync(new CI1<GridFuture<?>>() {
            @Override public void apply(GridFuture<?> e) {
                queryExecuted("Executed reduce remote query ", fut);
            }
        });

        return fut;
    }

    /** {@inheritDoc} */
    @Override public GridCacheQueryMetrics metrics() {
        return metrics;
    }

    /** {@inheritDoc} */
    @Override public void close() throws IOException {
        // No-op.
    }

    /**
     *
     */
    private class ReduceFuture<T> extends GridFutureAdapter<T> {
        /** */
        private GridCacheQueryFuture<R2> fut;

        /**
         *
         */
        public ReduceFuture() {
            super(cacheCtx.kernalContext());
        }

        /**
         * @param fut Future.
         */
        public void future(GridCacheQueryFuture<R2> fut) {
            this.fut = fut;
        }

        /** {@inheritDoc} */
        @Override public boolean cancel() throws GridException {
            return fut.cancel();
        }
    }
}
