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
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.gridgain.grid.cache.GridCacheFlag.*;
import static org.gridgain.grid.cache.GridCacheConfiguration.*;

/**
 * Query adapter.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCacheQueryBaseAdapter<K, V> extends GridMetadataAwareAdapter implements
    GridCacheQueryBase<K, V> {
    /** Sequence of query id.  */
    protected static final AtomicInteger seq = new AtomicInteger();

    /** Query id.  */
    protected final int id;

    /** Empty array. */
    private static final GridPredicate[] EMPTY_NODES = new GridPredicate[0];

    /** Default query timeout. */
    public static final long DFLT_TIMEOUT = 30 * 1000;

    /** */
    @GridToStringExclude
    private GridThreadLocal<PreparedStatement> stmt = new GridThreadLocal<PreparedStatement>();

    /** */
    protected final GridCacheContext<K, V> cacheCtx;

    /** Query activity logger. */
    protected final GridLogger qryLog;

    /** Default logger. */
    protected final GridLogger log;

    /** */
    private GridCacheQueryType type;

    /** */
    private String clause;

    /** */
    private String clsName;

    /** */
    private Class<?> cls;

    /** */
    private GridClosure<Object[], GridPredicate<? super K>> rmtKeyFilter;

    /** */
    private GridClosure<Object[], GridPredicate<? super V>> rmtValFilter;

    /** */
    private GridPredicate<GridCacheEntry<K, V>> prjFilter;

    /** */
    private int pageSize = GridCacheQuery.DFLT_PAGE_SIZE;

    /** */
    private long timeout = DFLT_TIMEOUT;

    /** */
    private Object[] args;

    /** */
    private Object[] closureArgs;

    /** */
    private boolean keepAll = true;

    /** */
    private boolean incBackups;

    /** */
    private boolean readThrough;

    /** */
    private boolean clone;

    /** Query metrics.*/
    protected GridCacheQueryMetricsAdapter metrics;

    /**
     * @param cacheCtx Cache registry.
     * @param type Query type.
     * @param clause Query clause.
     * @param clsName Query class name.
     * @param prjFilter Projection filter.
     * @param prjFlags Projection flags.
     */
    protected GridCacheQueryBaseAdapter(GridCacheContext<K, V> cacheCtx, @Nullable GridCacheQueryType type,
        @Nullable String clause, @Nullable String clsName, GridPredicate<GridCacheEntry<K, V>> prjFilter,
        Collection<GridCacheFlag> prjFlags) {
        assert cacheCtx != null;

        this.cacheCtx = cacheCtx;
        this.type = type;
        this.clause = clause;
        this.clsName = clsName;
        this.prjFilter = prjFilter;

        log = cacheCtx.logger(getClass());

        qryLog = cacheCtx.kernalContext().config().getGridLogger().getLogger(DFLT_QUERY_LOGGER_NAME);

        clone = prjFlags.contains(CLONE);

        id = seq.incrementAndGet();

        validateSql();

        createMetrics();
    }

    /**
     * @param qry Query to copy from.
     */
    protected GridCacheQueryBaseAdapter(GridCacheQueryBaseAdapter<K, V> qry) {
        stmt = qry.stmt;
        cacheCtx = qry.cacheCtx;
        type = qry.type;
        clause = qry.clause;
        prjFilter = qry.prjFilter;
        clsName = qry.clsName;
        cls = qry.cls;
        rmtKeyFilter = qry.rmtKeyFilter;
        rmtValFilter = qry.rmtValFilter;
        args = qry.args;
        closureArgs = qry.closureArgs;
        pageSize = qry.pageSize;
        timeout = qry.timeout;
        keepAll = qry.keepAll;
        readThrough = qry.readThrough;
        clone = qry.clone;

        log = cacheCtx.logger(getClass());

        qryLog = cacheCtx.kernalContext().config().getGridLogger().getLogger(DFLT_QUERY_LOGGER_NAME);

        metrics = qry.metrics;

        id = qry.id;
    }

    /** */
    private void validateSql() {
        if (type == GridCacheQueryType.SQL) {
            if (clause == null)
                throw new IllegalArgumentException("SQL string cannot be null for query.");

            if (clause.startsWith("where"))
                throw new IllegalArgumentException("SQL string cannot start with 'where' ('where' keyword is assumed). " +
                    "Valid examples: \"col1 like '%val1%'\" or \"from MyClass1 c1, MyClass2 c2 where c1.col1 = c2.col1 " +
                    "and c1.col2 like '%val2%'");
        }
    }

    /**
     * @return Context.
     */
    protected GridCacheContext<K, V> context() {
        return cacheCtx;
    }

    /** {@inheritDoc} */
    @Override public GridCacheQueryType type() {
        return type;
    }

    /** {@inheritDoc} */
    @Override public void type(GridCacheQueryType type) {
        this.type = type;

        createMetrics();
    }

    /** {@inheritDoc} */
    @Override public String clause() {
        return clause;
    }

    /** {@inheritDoc} */
    @Override public void clause(String clause) {
        this.clause = clause;

        validateSql();

        createMetrics();
    }

    /** {@inheritDoc} */
    @Override public int pageSize() {
        return pageSize;
    }

    /** {@inheritDoc} */
    @Override public void pageSize(int pageSize) {
        this.pageSize = pageSize < 1 ? GridCacheQuery.DFLT_PAGE_SIZE : pageSize;
    }

    /** {@inheritDoc} */
    @Override public long timeout() {
        return timeout;
    }

    /** {@inheritDoc} */
    @Override public void timeout(long timeout) {
        this.timeout = timeout <= 0 ? DFLT_TIMEOUT : timeout;
    }

    /** {@inheritDoc} */
    @Override public boolean keepAll() {
        return keepAll;
    }

    /** {@inheritDoc} */
    @Override public void keepAll(boolean keepAll) {
        this.keepAll = keepAll;
    }

    /** {@inheritDoc} */
    @Override public boolean includeBackups() {
        return incBackups;
    }

    /** {@inheritDoc} */
    @Override public void includeBackups(boolean incBackups) {
        this.incBackups = incBackups;
    }

    /** {@inheritDoc} */
    @Override public boolean readThrough() {
        return readThrough;
    }

    /**
     * @return Clone values flag.
     */
    public boolean cloneValues() {
        return clone;
    }

    /** {@inheritDoc} */
    @Override public void readThrough(boolean readThrough) {
        this.readThrough = readThrough;
    }

    /** {@inheritDoc} */
    @Override public String className() {
        return clsName;
    }

    /** {@inheritDoc} */
    @Override public void className(String clsName) {
        this.clsName = clsName;

        createMetrics();
    }

    /**
     * Gets query class.
     *
     * @param ldr Classloader.
     * @return Query class.
     * @throws ClassNotFoundException Thrown if class not found.
     */
    public Class<?> queryClass(ClassLoader ldr) throws ClassNotFoundException {
        if (cls == null)
            cls = Class.forName(clsName, true, ldr);

        return cls;
    }

    /**
     * @return Remote key filter.
     */
    public GridClosure<Object[], GridPredicate<? super K>> remoteKeyFilter() {
        return rmtKeyFilter;
    }

    /**
     *
     * @param rmtKeyFilter Remote key filter
     */
    @Override public void remoteKeyFilter(GridClosure<Object[], GridPredicate<? super K>> rmtKeyFilter) {
        this.rmtKeyFilter = rmtKeyFilter;
    }

    /**
     * @return Remote value filter.
     */
    public GridClosure<Object[], GridPredicate<? super V>> remoteValueFilter() {
        return rmtValFilter;
    }

    /**
     * @param rmtValFilter Remote value filter.
     */
    @Override public void remoteValueFilter(GridClosure<Object[], GridPredicate<? super V>> rmtValFilter) {
        this.rmtValFilter = rmtValFilter;
    }

    /**
     * @return Projection filter.
     */
    public GridPredicate<GridCacheEntry<K, V>> projectionFilter() {
        return prjFilter;
    }

    /**
     * @param prjFilter Projection filter.
     */
    public void projectionFilter(GridPredicate<GridCacheEntry<K, V>> prjFilter) {
        this.prjFilter = prjFilter;
    }

    /**
     * @param args Arguments.
     */
    public void arguments(@Nullable Object[] args) {
        this.args = args;
    }

    /**
     * @return Arguments.
     */
    public Object[] arguments() {
        return args;
    }

    /**
     * Sets closure arguments.
     * <p>
     * Note that the name of the method has "set" in it not to conflict with
     * {@link GridCacheQuery#closureArguments(Object...)} method.
     *
     * @param closureArgs Arguments.
     */
    public void setClosureArguments(Object[] closureArgs) {
        this.closureArgs = closureArgs;
    }

    /**
     * Gets closure arguments.
     * <p>
     * Note that the name of the method has "set" in it not to conflict with
     * {@link GridCacheQuery#closureArguments(Object...)} method.
     *
     * @return Closure's arguments.
     */
    public Object[] getClosureArguments() {
        return closureArgs;
    }

    /**
     * Sets PreparedStatement for this query for this thread.
     *
     * @param stmt PreparedStatement to set.
     */
    public void preparedStatementForThread(PreparedStatement stmt) {
        this.stmt.set(stmt);
    }

    /** @return PreparedStatement for this query for this thread. */
    public PreparedStatement preparedStatementForThread() {
        return stmt.get();
    }

    /**
     * @throws GridException In case of error.
     */
    protected abstract void registerClasses() throws GridException;

    /**
     * @param nodes Nodes.
     * @param lsnr Future listener.
     * @param single {@code true} if single result requested, {@code false} if multiple.
     * @param <R> Result type.
     * @return Future.
     */
    protected <R> GridCacheQueryFuture<R> execute(Collection<GridRichNode> nodes,
        @Nullable GridInClosure<? super GridFuture<Collection<R>>> lsnr, boolean single) {
        /*
            This logging is used only for debugging. For all other cases should be use
            {@code GridCacheConfiguration.isLogQueries()} flag.
        */
        if (log.isDebugEnabled())
            log.debug("Executing query [query=" + this + ", nodes=" + nodes + ']');

        try {
            cacheCtx.deploy().registerClasses(cls, rmtKeyFilter, rmtValFilter, prjFilter);

            registerClasses();

            cacheCtx.deploy().registerClasses(args);
            cacheCtx.deploy().registerClasses(closureArgs);
        }
        catch (GridException e) {
            return new GridCacheErrorQueryFuture<R>(cacheCtx.kernalContext(), e);
        }

        if (F.isEmpty(nodes))
            nodes = CU.allNodes(cacheCtx);

        GridCacheQueryManager<K, V> qryMgr = cacheCtx.queries();

        assert qryMgr != null;

        return nodes.size() == 1 && nodes.iterator().next().equals(cacheCtx.discovery().localNode()) ?
            qryMgr.queryLocal(this, lsnr, single) :
            qryMgr.queryDistributed(this, nodes, lsnr, single);
    }

    /**
     * @param grid Grid.
     * @return Predicates for nodes.
     */
    @SuppressWarnings( {"unchecked"})
    protected GridPredicate<GridRichNode>[] nodes(GridProjection[] grid) {
        if (F.isEmpty(grid))
            return EMPTY_NODES;

        GridPredicate<GridRichNode>[] res = new GridPredicate[grid.length];

        for (int i = 0; i < grid.length; i++)
            res[i] = grid[i].predicate();

        return res;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheQueryBaseAdapter.class, this);
    }

    /**
     * @param nodes Nodes.
     * @return Short representation of query.
     */
    public String toShortString(Collection<? extends GridNode> nodes) {
        return "[id=" + id + ", clause=" + clause + ", type=" + type + ", clsName=" + clsName + ", nodes=" +
            U.toShortString(nodes) + ']';
    }

    /**
     * Future for single query result.
     *
     * @param <R> Result type.
     */
    protected class SingleFuture<R> extends GridFutureAdapter<R> {
        /** */
        private GridCacheQueryFuture<R> fut;

        /**
         * Required by {@link Externalizable}.
         */
        public SingleFuture() {
            super(cacheCtx.kernalContext());
        }

        /**
         * @param nodes Nodes.
         */
        SingleFuture(Collection<GridRichNode> nodes) {
            super(cacheCtx.kernalContext());

            fut = execute(nodes, null, true);

            cacheCtx.closures().runLocalSafe(new GPR() {
                @Override public void run() {
                    try {
                        if (fut.hasNextX()) {
                            onDone(fut.nextX());

                            fut.cancel();

                            return;
                        }

                        onDone(null, null);
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

            return false;
        }
    }

    /**
     * Create new metric if metrics hasn't created or instead of already existed metrics;
     */
    @SuppressWarnings("unchecked")
    private void createMetrics() {
        if (metrics == null || metrics.executions() > 0) {
            metrics = new GridCacheQueryMetricsAdapter(this);

            GridCacheQueryManager qryMgr = cacheCtx.queries();

            assert qryMgr != null;

            qryMgr.metrics().add(metrics);
        }
    }

    /**
     *
     * @param fut Future which was executed.
     * @param msg Prefix for log message.
     */
    public void queryExecuted(String msg, GridFuture fut) {
        boolean fail = false;

        // Get future result.
        try {
            fut.get();
        }
        catch (GridException ignored) {
            fail = true;
        }

        metrics.onQueryExecute(fut.startTime(), fut.duration(), fail);

        if (qryLog.isDebugEnabled())
            qryLog.debug(msg + "[id=" + id + ", duration=" + fut.duration() + ", fail=" + fail + ']');
    }
}
