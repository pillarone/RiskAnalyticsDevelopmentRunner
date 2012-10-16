// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.query;

import org.gridgain.grid.cache.*;
import org.jetbrains.annotations.*;

/**
 * Cache query metrics used to obtain statistics on query. You can get query metrics via
 * {@link GridCache#queryMetrics()} method which will provide metrics for all queries
 * executed on cache.
 * <p>
 * Note that in addition to query metrics, you can also enable query tracing by setting
 * {@code "org.gridgain.cache.queries"} logging category to {@code DEBUG} level.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCacheQueryMetrics {
    /**
     * Gets time of the first query execution.
     *
     * @return First execution time.
     */
    public long firstRunTime();

    /**
     * Gets time of the last query execution.
     *
     * @return Last execution time.
     */
    public long lastRunTime();

    /**
     * Gets minimum execution time of query.
     *
     * @return Minimum execution time of query.
     */
    public long minimumTime();

    /**
     * Gets maximum execution time of query.
     *
     * @return Maximum execution time of query.
     */
    public long maximumTime();

    /**
     * Gets average execution time of query.
     *
     * @return Average execution time of query.
     */
    public long averageTime();

    /**
     * Gets total number execution of query.
     *
     * @return Number of executions.
     */
    public int executions();

    /**
     * Gets total number of times a query execution failed.
     *
     * @return total number of times a query execution failed.
     */
    public int fails();

    /**
     * Gets query clause.
     *
     * @return Query clause.
     */
    @Nullable public String clause();

    /**
     * Gets query type.
     *
     * @return type Query type.
     */
    public GridCacheQueryType type();

    /**
     * Gets Java class name of the values selected by the query.
     *
     * @return Java class name of the values selected by the query.
     */
    @Nullable public String className();
}
