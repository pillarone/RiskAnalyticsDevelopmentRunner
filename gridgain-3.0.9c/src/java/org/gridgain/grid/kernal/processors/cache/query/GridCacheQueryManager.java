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
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.cache.GridCachePeekMode.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.query.GridCacheQueryType.*;

/**
 * Query and index manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"UnnecessaryFullyQualifiedName"})
public abstract class GridCacheQueryManager<K, V> extends GridCacheManager<K, V> {
    /** Number of entries to keep in annotation cache. */
    private static final int DFLT_CLASS_CACHE_SIZE = 1000;

    /** */
    private static final List<GridCachePeekMode> DB_SWAP_GLOBAL = F.asList(DB, SWAP, GLOBAL);

    /** */
    private static final List<GridCachePeekMode> SWAP_GLOBAL = F.asList(SWAP, GLOBAL);

    /** */
    private GridCacheQueryIndex<K, V> idx;

    /** Busy lock. */
    private final ReadWriteLock busyLock = new ReentrantReadWriteLock();

    /** Queries metrics bounded cache. */
    private final GridBoundedLinkedHashSet<GridCacheQueryMetrics> metrics;

    /** */
    protected GridCacheQueryManager() {
        metrics = new GridBoundedLinkedHashSet<GridCacheQueryMetrics>(DFLT_CLASS_CACHE_SIZE);
    }

    /** {@inheritDoc} */
    @Override public void start0() throws GridException {
        idx = new GridCacheQueryIndex<K, V>(cctx);

        idx.start();
    }

    /**
     * Stops query manager.
     *
     * @param cancel Cancel queries.
     * @param wait Wait for current queries finish.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    @Override public final void stop0(boolean cancel, boolean wait) {
        try {
            if (cancel)
                onCancelAtStop();

            if (wait)
                onWaitAtStop();
        }
        finally {
            // Acquire write lock so that any new activity could not be started.
            busyLock.writeLock().lock();

            idx.stop();
        }

        if (log.isDebugEnabled())
            log.debug("Stopped cache query manager.");
    }

    /**
     * Enters to busy state.
     *
     * @return {@code true} if entered to busy state.
     */
    protected boolean enterBusy() {
        return busyLock.readLock().tryLock();
    }

    /**
     * Release read lock for queries execution.
     */
    protected void leaveBusy() {
        busyLock.readLock().unlock();
    }

    /**
     * Marks this request as canceled.
     *
     * @param reqId Request id.
     */
    void onQueryFutureCanceled(long reqId) {
        // No-op.
    }

    /**
     * Cancel flag handler at stop.
     */
    void onCancelAtStop() {
        // No-op.
    }

    /**
     * Wait flag handler at stop.
     */
    void onWaitAtStop() {
        // No-op.
    }

    /**
     * Writes key-value pair to index.
     *
     * @param key Key.
     * @param keyBytes Byte array with key data.
     * @param val Value.
     * @param ver Cache entry version.
     * @throws GridException In case of error.
     */
    public void store(K key, @Nullable byte[] keyBytes, V val, GridCacheVersion ver) throws GridException {
        assert key != null;
        assert val != null;

        if (!enterBusy()) {
            if (log.isDebugEnabled())
                log.debug("Received store request while stopping or after shutdown (will ignore).");

            return;
        }

        try {
            idx.store(key, keyBytes, val, ver);
        }
        finally {
            leaveBusy();
        }
    }

    /**
     * @param key Key.
     * @param keyBytes Byte array with key value.
     * @return {@code true} if key was found and removed, otherwise {@code false}.
     * @throws GridException Thrown in case of any errors.
     */
    public boolean remove(K key, @Nullable byte[] keyBytes) throws GridException {
        assert key != null;

        if (!enterBusy()) {
            if (log.isDebugEnabled())
                log.debug("Received remove request while stopping or after shutdown (will ignore).");

            return false;
        }

        try {
            return idx.remove(key, keyBytes);
        }
        finally {
            leaveBusy();
        }
    }

    /**
     * Undeploys given class loader.
     *
     * @param ldr Class loader to undeploy.
     */
    public void onUndeploy(ClassLoader ldr) {
        if (!enterBusy()) {
            if (log.isDebugEnabled())
                log.debug("Received onUndeploy() request while stopping or after shutdown (will ignore).");

            return;
        }

        try {
            idx.onUndeploy(ldr);
        }
        finally {
            leaveBusy();
        }
    }

    /**
     * NOTE: For testing purposes.
     *
     * @return Collection of currently registered table names.
     */
    public Collection<String> indexTables() {
        return idx.indexTables();
    }

    /**
     * Executes distributed query.
     *
     * @param qry Query.
     * @param lsnr Future listener.
     * @param single {@code true} if single result requested, {@code false} if multiple.
     * @return Iterator over query results. Note that results become available as they come.
     */
    public abstract <R> GridCacheQueryFuture<R> queryLocal(GridCacheQueryBaseAdapter<K, V> qry,
        GridInClosure<? super GridFuture<Collection<R>>> lsnr, boolean single);

    /**
     * Executes distributed query.
     *
     * @param qry Query.
     * @param nodes Nodes.
     * @param lsnr Future listener.
     * @param single {@code true} if single result requested, {@code false} if multiple.
     * @return Iterator over query results. Note that results become available as they come.
     */
    public abstract <R> GridCacheQueryFuture<R> queryDistributed(GridCacheQueryBaseAdapter<K, V> qry,
        Collection<GridRichNode> nodes, GridInClosure<? super GridFuture<Collection<R>>> lsnr, boolean single);

    /**
     * Performs query.
     *
     * @param qry Query.
     * @param loc Local query or not.
     * @return Collection of found keys.
     * @throws GridException In case of error.
     */
    private Iterator<GridCacheQueryIndexRow<K, V>> executeQuery(GridCacheQueryBaseAdapter qry, boolean loc) throws
        GridException {
        return qry.type() == SQL ? idx.querySql(qry, loc) : qry.type() == SCAN ? scanIterator(qry) :
            idx.queryText(qry, loc);
    }

    /**
     * @param qry query
     * @return Full-scan row iterator.
     * @throws GridException If failed to get iterator.
     */
    @SuppressWarnings({"unchecked"})
    private Iterator<GridCacheQueryIndexRow<K, V>> scanIterator(GridCacheQueryBaseAdapter qry) throws GridException {
        GridPredicate<GridCacheEntry<K, V>>[] filter = cctx.vararg(qry.projectionFilter());

        Set<Map.Entry<K, V>> entries =
            qry.readThrough() ?
                cctx.cache().getAll(filter).entrySet() :
                cctx.cache().peekAll(filter).entrySet();

        return F.iterator(entries,
            new C1<Map.Entry<K, V>, GridCacheQueryIndexRow<K, V>>() {
                @Override public GridCacheQueryIndexRow<K, V> apply(Map.Entry<K, V> e) {
                    return new GridCacheQueryIndexRow<K, V>(e.getKey(), e.getValue(), null, null);
                }
            }, true);
    }

    /**
     * @param obj Object to inject resources to.
     * @throws GridException If failure occurred while injecting resources.
     */
    private void injectResources(@Nullable Object obj) throws GridException {
        GridKernalContext ctx = cctx.kernalContext();

        if (obj != null)
            ctx.resource().inject(ctx.deploy().getDeployment(obj.getClass().getName()), obj.getClass(), obj);
    }

    /**
     * Processes cache query request.
     *
     * @param qryInfo Query info.
     */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    protected <R> void runQuery(GridCacheQueryInfo<K, V> qryInfo) {
        if (log.isDebugEnabled())
            log.debug("Running query: " + qryInfo);

        boolean loc = qryInfo.local();

        try {
            // Preparing query closures.
            GridPredicate<Object> keyFilter = (GridPredicate<Object>)qryInfo.keyFilter();
            GridPredicate<Object> valFilter = (GridPredicate<Object>)qryInfo.valueFilter();
            GridPredicate<GridCacheEntry<K, V>>[] prjFilter = cctx.vararg(qryInfo.projectionPredicate());
            GridClosure<Object, Object> trans = (GridClosure<Object, Object>)qryInfo.transformer();
            GridReducer<Map.Entry<K, V>, Object> rdc = qryInfo.reducer();

            // Injecting resources into query closures.
            injectResources(keyFilter);
            injectResources(valFilter);
            injectResources(prjFilter);
            injectResources(trans);
            injectResources(rdc);

            GridCacheQueryBaseAdapter<K, V> qry = qryInfo.query();

            boolean single = qryInfo.single();

            int pageSize = qryInfo.pageSize();

            boolean readThrough = qryInfo.readThrough();

            boolean incBackups = qryInfo.includeBackups();

            Map<K, Object> map = new LinkedHashMap<K, Object>(pageSize);

            Iterator<GridCacheQueryIndexRow<K, V>> iter = executeQuery(qry, loc);

            GridCacheAdapter<K, V> cache = cctx.cache();

            int cnt = 0;

            boolean stop = false;

            while (iter.hasNext()) {
                GridCacheQueryIndexRow<K, V> row = iter.next();

                K key = row.key();

                if (cctx.config().getCacheMode() != LOCAL && !incBackups && !cctx.primary(cctx.localNode(), key))
                    continue;

                if (!F.isAll(key, keyFilter))
                    continue;

                V val = row.value();

                if (val == null) {
                    assert row.valueBytes() != null;

                    GridCacheEntryEx<K, V> entry = cache.entryEx(key);

                    boolean unmarshal;

                    try {
                        val = readThrough ? entry.peek(DB_SWAP_GLOBAL, prjFilter) : entry.peek(SWAP_GLOBAL, prjFilter);

                        if (qry.cloneValues())
                            val = cctx.cloneValue(val);

                        GridCacheVersion ver = entry.version();

                        unmarshal = !row.version().equals(ver.id().toString() + ver.order());
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        // If entry has been removed concurrently we have to unmarshal from bytes.
                        unmarshal = true;
                    }

                    if (unmarshal)
                        val = (V)U.unmarshal(cctx.marshaller(), new GridByteArrayList(row.valueBytes()),
                            loc ? cctx.deploy().localLoader() : cctx.deploy().globalLoader());
                }

                if (log.isDebugEnabled())
                    log.debug("Record [pNode=" + CU.primaryNode(cctx, row.key()).id8() + ", node=" +
                        cctx.grid().localNode().id8() + ", key=" + row.key() + ", val=" + val + ", incBackups=" +
                        incBackups + ']');

                if (val == null || !F.isAll(val, valFilter))
                    continue;

                map.put(row.key(), trans == null ? val : trans.apply(val));

                if (single)
                    break;

                if (++cnt == pageSize || !iter.hasNext()) {
                    if (rdc == null) {
                        boolean finished = !iter.hasNext();

                        if (loc)
                            onPageReady(loc, qryInfo, map.entrySet(), finished, null);
                        else {
                            // Put GridCacheQueryResponseEntry as map value to avoid using any new container.
                            for (Map.Entry entry : map.entrySet())
                                entry.setValue(new GridCacheQueryResponseEntry(entry.getKey(), entry.getValue()));

                            if (!onPageReady(loc, qryInfo, map.values(), finished, null))
                                // Finish processing on any error.
                                return;
                        }

                        cnt = 0;

                        if (finished)
                            return;
                    }
                    else {
                        for (Map.Entry<K, Object> entry : map.entrySet())
                            if (!rdc.collect((Map.Entry<K, V>)entry)) {
                                stop = true;

                                break; // for
                            }
                    }

                    map = new LinkedHashMap<K, Object>(pageSize);

                    if (stop)
                        break; // while
                }
            }

            Collection<?> data;

            if (rdc == null) {
                if (!loc) {
                    for (Map.Entry entry : map.entrySet())
                        entry.setValue(new GridCacheQueryResponseEntry(entry.getKey(), entry.getValue()));

                    data = map.values();
                }
                else
                    data = map.entrySet();
            }
            else {
                if (!stop)
                    for (Map.Entry<K, Object> entry : map.entrySet())
                        if (!rdc.collect((Map.Entry<K, V>)entry))
                            break;

                data = Collections.singletonList(rdc.apply());
            }

            onPageReady(loc, qryInfo, data, true, null);
        }
        catch (Throwable e) {
            onPageReady(loc, qryInfo, null, true, e);
        }

        if (log.isDebugEnabled())
            log.debug("End of running query: " + qryInfo);
    }

    /**
     * Called when data for page is ready.
     *
     * @param loc Local query or not.
     * @param qryInfo Query info.
     * @param data Result data.
     * @param finished Last page or not.
     * @param e Exception in case of error.
     * @return {@code true} if page was processed right.
     */
    protected abstract boolean onPageReady(boolean loc, GridCacheQueryInfo<K, V> qryInfo, @Nullable Collection<?> data,
        boolean finished, @Nullable Throwable e);

    /**
     *
     * @param qry Query to validate.
     * @throws GridException In case of validation error.
     */
    public void validateQuery(GridCacheQueryBase<K, V> qry) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Validating query: " + qry);

        if (qry.type() == null)
            throw new GridException("Type must be set for query.");

        if (qry.type() == SQL) {
            if (qry.className() == null || qry.className().length() == 0)
                throw new GridException("Class must be set for SQL query.");

            if (qry.clause() == null || qry.clause().length() == 0)
                throw new GridException("Clause must be set for SQL query.");
        }

        if (qry.type() == H2TEXT && F.isEmpty(qry.clause()))
            throw new GridException("Clause must be set for H2 text query.");

        if (qry.type() == LUCENE && F.isEmpty(qry.clause()))
            throw new GridException("Clause must be set for Lucene query.");
    }

    /**
     * Creates user's query.
     *
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery(GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheQueryAdapter<K, V>(cctx, null, null, null, filter, flags);
    }

    /**
     * Creates user's query.
     *
     * @param type Query type.
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, GridPredicate<GridCacheEntry<K, V>> filter,
        Set<GridCacheFlag> flags) {
        return new GridCacheQueryAdapter<K, V>(cctx, type, null, null, filter, flags);
    }

    /**
     * Creates user's query.
     *
     * @param type Query type.
     * @param clsName Query class name (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, String clsName, String clause,
        GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheQueryAdapter<K, V>(cctx, type, clause, clsName, filter, flags);
    }

    /**
     * Creates user's query.
     *
     * @param type Query type.
     * @param cls Query class (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public GridCacheQuery<K, V> createQuery(GridCacheQueryType type, Class<?> cls, String clause,
        @Nullable GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheQueryAdapter<K, V>(cctx, type, clause,
            (cls != null ? cls.getName() : null), filter, flags);
    }

    /**
     * Creates user's transform query.
     *
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridPredicate<GridCacheEntry<K, V>> filter,
        Set<GridCacheFlag> flags) {
        return new GridCacheTransformQueryAdapter<K, V, T>(cctx, null, null, null,
            filter, flags);
    }

    /**
     * Creates user's transform query.
     *
     * @param type Query type.
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type,
        GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheTransformQueryAdapter<K, V, T>(cctx, type, null, null,
            filter, flags);
    }

    /**
     * Creates user's transform query.
     *
     * @param type Query type.
     * @param clsName Query class name (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type, String clsName,
        String clause, GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheTransformQueryAdapter<K, V, T>(cctx, type, clause,
            clsName, filter, flags);
    }

    /**
     * Creates user's transform query.
     *
     * @param type Query type.
     * @param cls Query class (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <T> GridCacheTransformQuery<K, V, T> createTransformQuery(GridCacheQueryType type, Class<?> cls,
        String clause, GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheTransformQueryAdapter<K, V, T>(cctx, type, clause,
            (cls != null ? cls.getName() : null), filter, flags);
    }

    /**
     * Creates user's reduce query.
     *
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridPredicate<GridCacheEntry<K, V>> filter,
        Set<GridCacheFlag> flags) {
        return new GridCacheReduceQueryAdapter<K, V, R1, R2>(cctx, null, null,
            null, filter, flags);
    }

    /**
     * Creates user's reduce query.
     *
     * @param type Query type.
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type,
        GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheReduceQueryAdapter<K, V, R1, R2>(cctx, type, null,
            null, filter, flags);
    }

    /**
     * Creates user's reduce query.
     *
     * @param type Query type.
     * @param clsName Query class name (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type, String clsName,
        String clause, GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheReduceQueryAdapter<K, V, R1, R2>(cctx, type, clause,
            clsName, filter, flags);
    }

    /**
     * Creates user's reduce query.
     *
     * @param type Query type.
     * @param cls Query class (may be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param clause Query clause (should be {@code null} for {@link GridCacheQueryType#SCAN} queries).
     * @param filter Projection filter.
     * @param flags Projection flags
     * @return Created query.
     */
    public <R1, R2> GridCacheReduceQuery<K, V, R1, R2> createReduceQuery(GridCacheQueryType type, Class<?> cls,
        String clause, GridPredicate<GridCacheEntry<K, V>> filter, Set<GridCacheFlag> flags) {
        return new GridCacheReduceQueryAdapter<K, V, R1, R2>(cctx, type, clause,
            (cls != null ? cls.getName() : null), filter, flags);
    }

    /**
     * Gets cache queries metrics.
     *
     * @return Cache queries metrics.
     */
    public Collection<GridCacheQueryMetrics> metrics() {
        return metrics;
    }
}
