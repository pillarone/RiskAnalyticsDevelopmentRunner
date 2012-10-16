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
 * Adapter for cache queries.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheQueryAdapter<K, V> extends GridCacheQueryBaseAdapter<K, V> implements GridCacheQuery<K, V> {
    /**
     * @param ctx Cache registry.
     * @param type Query type.
     * @param clause Query clause.
     * @param clsName Query class name.
     * @param prjFilter Projection filter.
     * @param prjFlags Projection flags.
     */
    public GridCacheQueryAdapter(GridCacheContext<K, V> ctx, GridCacheQueryType type, String clause, String clsName,
        GridPredicate<GridCacheEntry<K, V>> prjFilter, Collection<GridCacheFlag> prjFlags) {
        super(ctx, type, clause, clsName, prjFilter, prjFlags);
    }

    /**
     * @param query Query to copy from (ignoring arguments).
     */
    @SuppressWarnings({"TypeMayBeWeakened"})
    private GridCacheQueryAdapter(GridCacheQueryAdapter<K, V> query) {
        super(query);
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> queryArguments(@Nullable Object[] args) {
        GridCacheQueryAdapter<K, V> copy = new GridCacheQueryAdapter<K, V>(this);

        copy.arguments(args);

        return copy;
    }

    /** {@inheritDoc} */
    @Override public GridCacheQuery<K, V> closureArguments(@Nullable Object[] args) {
        GridCacheQueryAdapter<K, V> copy = new GridCacheQueryAdapter<K, V>(this);

        copy.setClosureArguments(args);

        return copy;
    }

    /** {@inheritDoc} */
    @Override protected void registerClasses() throws GridException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Map.Entry<K, V>> executeSingle(GridProjection[] grid) {
        Collection<GridRichNode> nodes = F.retain(CU.allNodes(cacheCtx), true, nodes(grid));

        if (qryLog.isDebugEnabled())
            qryLog.debug(U.compact("Executing query for single result " + toShortString(nodes)));

        final GridFuture<Map.Entry<K, V>> fut = new SingleFuture<Map.Entry<K, V>>(nodes);

        fut.listenAsync(new CI1<GridFuture<?>>() {
            @Override public void apply(GridFuture<?> e) {
                queryExecuted("Executed query for single result ", fut);
            }
        });

        return fut;
    }

    /** {@inheritDoc} */
    @Override public GridCacheQueryFuture<Map.Entry<K, V>> execute(GridProjection[] grid) {
        Collection<GridRichNode> nodes = F.retain(CU.allNodes(cacheCtx), true, nodes(grid));

        if (qryLog.isDebugEnabled())
            qryLog.debug(U.compact("Executing query " + toShortString(nodes)));

        final GridCacheQueryFuture<Map.Entry<K, V>> fut = execute(nodes, null, false);

        fut.listenAsync(new CI1<GridFuture<?>>() {
            @Override public void apply(GridFuture<?> e) {
                queryExecuted("Executed query ", fut);
            }
        });

        return fut;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> visit(GridPredicate<Map.Entry<K, V>> vis, GridProjection[] grid) {
        Collection<GridRichNode> nodes = F.retain(CU.allNodes(cacheCtx), true, nodes(grid));

        if (qryLog.isDebugEnabled())
            qryLog.debug(U.compact("Executing query with visitor " + toShortString(nodes)));

        final GridFuture<?> fut = visit(vis, nodes);

        fut.listenAsync(new CI1<GridFuture<?>>() {
            @Override public void apply(GridFuture<?> e) {
                queryExecuted("Executed query with visitor ", fut);
            }
        });

        return fut;
    }

    /** {@inheritDoc} */
    @Override public GridCacheQueryMetrics metrics() {
        return metrics;
    }

    /**
     * @param vis Visitor.
     * @param nodes Node.
     * @return Future.
     */
    private GridFuture<?> visit(GridPredicate<Map.Entry<K, V>> vis, Collection<GridRichNode> nodes) {
        return new VisitorFuture(vis, nodes);
    }

    /** {@inheritDoc} */
    @Override public void close() throws IOException {
        // No-op.
    }

    /**
     *
     */
    private class VisitorFuture extends GridFutureAdapter<Map.Entry<K, V>> {
        /** */
        private GridCacheQueryFuture<Map.Entry<K, V>> fut;

        /**
         * For Externalizable.
         */
        public VisitorFuture() {
            // No-op.
        }

        /**
         *
         * @param vis Visitor.
         * @param nodes Nodes.
         */
        VisitorFuture(final GridPredicate<Map.Entry<K, V>> vis, Collection<GridRichNode> nodes) {
            super(cacheCtx.kernalContext());

            fut = execute(nodes, null, false);

            context().closures().runLocalSafe(new GPR() {
                @Override public void run() {
                    try {
                        while (fut.hasNextX()) {
                            Map.Entry<K, V> entry = fut.nextX();

                            if (!vis.apply(entry)) {
                                onDone((Map.Entry<K, V>)null);

                                return;
                            }
                        }

                        onDone((Map.Entry<K, V>)null);
                    }
                    catch (Throwable e) {
                        onDone(e);
                    }
                }
            });
        }

        /** {@inheritDoc} */
        @Override public boolean cancel() throws GridException {
            if (onCancelled()) {
                fut.cancel();

                return true;
            }
            else
                return false;
        }
    }
}
