// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.query;

import org.gridgain.grid.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * API for creating cache queries. The queries are executed as follows:
 * <ol>
 * <li>Query is sent to requested grid nodes.</li>
 * <li>
 *  If query is not of {@link GridCacheQueryType#SCAN} query type, each node will execute the received query
 *  against index tables to retrieve the keys of the values in the result set. In {@code SCAN} mode this
 *  step is redundant.
 * <li>
 *  If there is a key filter provided via {@link #remoteKeyFilter(GridClosure)}
 *  method, then each returned key will be evaluated against the provided key filter.
 * </li>
 * <li>
 *  All the keys that still remain will be used to retrieve corresponding values from cache.
 *  If {@code read-through} flag is enabled, then all values that are {@code null}
 *  (possible if {@code 'invalidation'} mode is set to {@code true}) will be loaded
 *  from the persistent store, otherwise only values that are available in memory will
 *  be returned. 
 * </li>
 * <li>
 *  If there is a value filter provided via {@link #remoteValueFilter(GridClosure)} method,
 *  all retrieved values will be evaluated against the provided value filter.
 * </li>
 * <li>
 *  If query was executed via {@link #visit(GridPredicate,GridProjection...)} method, then
 *  a small acknowledgement message is sent back, otherwise the resulting key-value pairs
 *  will be sent back to requesting node one page at a time.
 * </li>
 * <li>
 *  If the requesting node will receive a page of next query results, it will provide it to the ongoing
 *  iterator, which will start returning received key-value pairs to user. If {@link #keepAll(boolean)}
 *  flag is set to false, then the received page will be immediately discarded after it has been
 *  returned to user. In this case, the {@link GridCacheQueryFuture#get()} method will always return
 *  only the last page. If {@link #keepAll(boolean)} flag is {@code true}, then all query result pages
 *  will be accumulated in memory and the full query result will be returned to user. 
 * </li>
 * </ol>
 * <h1 class="header">SQL Queries</h1>
 * {@link GridCacheQueryType#SQL} query type allows to execute distributed cache
 * queries using standard SQL syntax. All values participating in where clauses
 * or joins must be annotated with {@link GridCacheQuerySqlField} annotation.
 * There are almost no restrictions as to which SQL syntax can be used. All inner, outer, or
 * full joins are supported, as well as rich set of SQL grammar and functions. GridGain relies on
 * {@code H2 SQL Engine} for SQL compilation and indexing. For full set of supported
 * {@code Numeric}, {@code String}, and {@code Date/Time} SQL functions please refer
 * to <a href="http://www.h2database.com/html/functions.html">H2 Functions</a> documentation
 * directly. For full set of supported SQL syntax refer to
 * <a href="http://www.h2database.com/html/grammar.html#select">H2 SQL Select Grammar</a>.
 * <p>
 * Note that whenever using {@code 'group by'} queries, only individual page results will be
 * sorted and not the full result sets. However, if a single node is queried, then the result
 * set will be quite accurate.
 * <h1 class="header">Text Queries</h1>
 * GridGain supports two type of text queries: {@link GridCacheQueryType#LUCENE} and
 * {@link GridCacheQueryType#H2TEXT}. All fields that are expected to show up in text
 * query results must be annotated with {@link GridCacheQueryLuceneField} or
 * {@link GridCacheQueryH2TextField} accordingly. The {@code Lucene} based text search
 * utilizes {@code Apache Lucene} internally for text indexing, and the {@code H2 TEXT}
 * search stores text indexes in special H2 index tables.
 * <h1 class="header">Scan Queries</h1>
 * Sometimes when it is known in advance that SQL query will cause a full data scan,
 * or whenever data set is relatively small, the {@link GridCacheQueryType#SCAN}
 * query type may be used. With this query type GridGain will iterate over all cache
 * entries, skipping over the entries that don't pass the optionally provided key or value filters
 * (see @link #remoteKeyFilter(GridOutClosure)} or {@link #remoteValueFilter(GridClosure)} methods).
 * In this mode the query clause should not be provided.
 * <h1 class="header">Execute vs. Visit</h1>
 * If there is no need to return result to the caller node, you can save on a potentially significant
 * network overhead by visiting all query results directly on remote nodes by calling
 * {@link #visit(GridPredicate,GridProjection...)} method. With this method, all the logic is performed
 * inside of query predicate directly on the queried nodes. If the predicate will return {@code false}
 * while visiting, then visiting will finish immediately.
 * <h1 class="header">Optional Key and Value filters</h1>
 * Note that all query results may be additionally filtered by specifying
 * predicates for key and value filtering via {@link #remoteKeyFilter(GridClosure)}
 * and {@link #remoteValueFilter(GridClosure)} methods. These additional filters
 * are useful whenever filtering is based on logic or methods not available in {@code SQL}
 * or {@code TEXT} queries. For {@code 'SCAN'} queries this filters should be usually provided
 * as they are used directly to filter the query results during full scan.
 * <h1 class="header">Query Future Iterators</h1>
 * Note that {@link GridCacheQueryFuture} implements {@link Iterable} interface directly and
 * therefore can be used in regular iterator or {@code foreach} loops. The iterator will
 * immediately return all query results that are currently available and will block on page
 * boundaries, whenever the next page is not available yet. Whenever the full result set is
 * needed as a collection, then {@link #keepAll(boolean)} flag should be set to {@code true}
 * and any of the future's {@code get(..)} methods should be called. 
 * <h1 class="header">Query usage</h1>
 * As an example, suppose we have data model consisting of {@code 'Employee'} and {@code 'Organization'}
 * classes defined as follows:
 * <pre name="code" class="java">
 * public class Organization {
 *     &#64;GridCacheQuerySqlField(unique = true)
 *     private long id;
 *
 *     &#64;GridCacheQuerySqlField
 *     private String name;
 *     ...
 * }
 *
 * public class Person {
 *     // Unique index.
 *     &#64;GridCacheQuerySqlField(unique=true)
 *     private long id;
 *
 *     &#64;GridCacheQuerySqlField
 *     private long orgId; // Organization ID.
 *
 *     // Not indexed.
 *     private String name;
 *
 *     // Non-unique index.
 *     &#64;GridCacheQuerySqlField
 *     private double salary;
 *
 *     // Index for text search.
 *     &#64;GridCacheQueryLuceneField
 *     private String resume;
 *     ...
 * }
 * </pre>
 * Then you can create and execute queries that check various salary ranges like so:
 * <pre name="code" class="java">
 * GridCache&lt;Long, Person&gt; cache = G.grid().cache();
 * ...
 * // Create query which selects salaries based on range for all employees
 * // that work for a certain company.
 * GridCacheQuery&lt;Long, Person&gt; qry = cache.createQuery(SQL, Person.class,
 *     "from Person, Organization where Person.orgId = Organization.id " +
 *         "and Organization.name = ? and Person.salary &gt; ? and Person.salary &lt;= ?");
 *
 * // Query all nodes to find all cached GridGain employees
 * // with salaries less than 1000.
 * qry.queryArguments("GridGain", 0, 1000).execute(grid);
 *
 * // Query only remote nodes to find all remotely cached GridGain employees
 * // with salaries greater than 1000 and less than 2000.
 * qry.queryArguments("GridGain", 1000, 2000).execute(grid.remoteProjection());
 *
 * // Query local node only to find all locally cached GridGain employees
 * // with salaries greater than 2000.
 * qry.queryArguments(2000, Integer.MAX_VALUE).execute(grid.localNode());
 * </pre>
 * Here is a possible query that will use Lucene text search to scan all resumes to
 * check if employees have {@code Master} degree:
 * <pre name="code" class="java">
 * GridCacheQuery&lt;Long, Person&gt; mastersQry = cache.createQuery(LUCENE, Person.class, "Master");
 *
 * // Query all cache nodes.
 * mastersQry.execute(grid.localNode()));
 * </pre>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCacheQuery<K, V> extends GridCacheQueryBase<K, V> {
    /**
     * Optional query arguments that get passed into query SQL.
     *
     * @param args Optional query arguments.
     * @return This query with the passed in arguments preset.
     */
    public GridCacheQuery<K, V> queryArguments(@Nullable Object... args);

    /**
     * Optional arguments for closures to be used by {@link #remoteKeyFilter(GridClosure)},
     * and {@link #remoteValueFilter(GridClosure)}.
     *
     * @param args Optional query arguments.
     * @return This query with the passed in arguments preset.
     */
    public GridCacheQuery<K, V> closureArguments(@Nullable Object... args);

    /**
     * Executes the query and returns the first result in the result set. If more
     * than one key-value pair are returned they will be ignored.
     * <p>
     * Note that if the passed in grid projection is a local node, then query
     * will be executed locally without distribution to other nodes.
     *
     * @param grid Optional subgrid projection to execute this query on
     *      (if not provided, then the whole grid is used).
     * @return Future for the single query result.
     */
    @GridEnterpriseFeature("Distributed queries are enterprise-only feature " +
        "(local queries are available in community edition)")
    public GridFuture<Map.Entry<K, V>> executeSingle(GridProjection... grid);

    /**
     * Executes the query and returns the query future. Caller may decide to iterate
     * over the returned future directly in which case the iterator may block until
     * the next value will become available, or wait for the whole query to finish
     * by calling any of the {@code 'get(..)'} methods on the returned future. If
     * {@link #keepAll(boolean)} flag is set to {@code false}, then {@code 'get(..)'}
     * methods will only return the last page received, otherwise all pages will be
     * accumulated and returned to user as a collection.
     * <p>
     * Note that if the passed in grid projection is a local node, then query
     * will be executed locally without distribution to other nodes.
     *
     * @param grid Optional subgrid projection to execute this query on
     *      (if not provided, then the whole grid is used).
     * @return Future for the query result.
     */
    @GridEnterpriseFeature("Distributed queries are enterprise-only feature " +
        "(local queries are available in community edition)")
    public GridCacheQueryFuture<Map.Entry<K, V>> execute(GridProjection... grid);

    /**
     * Visits every entry from query result on every queried node for as long as
     * the visitor predicate returns {@code true}. Once the predicate returns false
     * or all entries in query result have been visited, the visiting process stops.
     * <p>
     * Note that if the passed in grid projection is a local node, then query
     * will be executed locally without distribution to other nodes.
     *
     * @param vis Visitor predicate.
     * @param grid Optional subgrid projection to execute this query on
     *      (if not provided, then the whole grid is used).
     * @return Future which will complete whenever visiting on all remote nodes completes or fails.
     */
    @GridEnterpriseFeature("Distributed queries are enterprise-only feature " +
        "(local queries are available in community edition)")
    public GridFuture<?> visit(GridPredicate<Map.Entry<K, V>> vis, GridProjection... grid);
}
