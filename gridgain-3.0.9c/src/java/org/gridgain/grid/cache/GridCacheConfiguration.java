// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.cache.cloner.*;
import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.cache.eviction.*;
import org.gridgain.grid.cache.eviction.lirs.*;
import org.gridgain.grid.cache.jta.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.cache.store.*;

import java.util.*;

/**
 * This interface defines grid cache configuration. This configuration is passed to
 * grid via {@link GridConfiguration#getCacheConfiguration()} method. It defines all configuration
 * parameters required to start a cache within grid instance. You can have multiple caches
 * configured with different names within one grid.
 * <p>
 * Note, that absolutely every configuration property in {@code GridCacheConfiguration} is optional.
 * One can simply create new instance of {@link GridCacheConfigurationAdapter}, for example,
 * and pass it to {@link GridConfiguration#getCacheConfiguration()} to start grid cache with
 * default configuration.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCacheConfiguration {
    /** Default log name. */
    public static final String DFLT_QUERY_LOGGER_NAME = "org.gridgain.cache.queries";

    /** Default atomic sequence reservation size. */
    public static final int DFLT_ATOMIC_SEQUENCE_RESERVE_SIZE = 1000;

    /** Default size of preload thread pool. */
    public static final int DFLT_PRELOAD_THREAD_POOL_SIZE = 2;

    /**
     * Default time to live. The value is <tt>0</tt> which means that
     * cached objects never expire based on time.
     */
    public static final long DFLT_TIME_TO_LIVE = 0;

    /** Default caching mode. */
    public static final GridCacheMode DFLT_CACHE_MODE = GridCacheMode.REPLICATED;

    /** Default transaction timeout. */
    public static final long DFLT_TRANSACTION_TIMEOUT = 0;

    /** Default lock timeout. */
    public static final long DFLT_LOCK_TIMEOUT = 0;

    /** Default concurrency mode. */
    public static final GridCacheTxConcurrency DFLT_TX_CONCURRENCY = GridCacheTxConcurrency.OPTIMISTIC;

    /** Default transaction isolation level. */
    public static final GridCacheTxIsolation DFLT_TX_ISOLATION = GridCacheTxIsolation.REPEATABLE_READ;

    /** Initial default cache size. */
    public static final int DFLT_START_SIZE = 1024;

    /** Default cache size to use with default eviction policy. */
    public static final int DFLT_CACHE_SIZE = 100000;

    /** Initial default near cache size. */
    public static final int DFLT_NEAR_START_SIZE = DFLT_START_SIZE / 4;

    /** Default near cache size to use with default near eviction policy. */
    public static final int DFLT_NEAR_SIZE = 10000;

    /** Default preload mode for distributed cache. */
    public static final GridCachePreloadMode DFLT_PRELOAD_MODE = GridCachePreloadMode.ASYNC;

    /** Default preload batch size in bytes. */
    public static final int DFLT_PRELOAD_BATCH_SIZE = 102400;

    /** */
    public static final int DFLT_IDX_MAX_OPERATIONAL_MEM = 100000;

    /** Default frequency of running H2 "ANALYZE" command. */
    public static final int DFLT_IDX_ANALYZE_FREQ = 10 * 60 * 1000;

    /** Default number samples used to run H2 "ANALYZE" command. */
    public static final int DFLT_IDX_ANALYZE_SAMPLE_SIZE = 10000;

    /** */
    public static final int DFLT_GC_FREQUENCY = 0;

    /** Default index parent folder name. */
    public static final String DFLT_IDX_PARENT_FOLDER_NAME = "work/cache/indexes";

    /**
     * Cache name. If not provided or {@code null}, then this will be considered a default
     * cache which can be accessed via {@link Grid#cache()} method. Otherwise, if name
     * is provided, the cache will be accessed via {@link Grid#cache(String)} method.
     *
     * @return Cache name.
     */
    public String getName();

    /**
     * Gets caching mode to use. You can configure cache either to be local-only,
     * fully replicated, partitioned, or near. If not provided, {@link GridCacheMode#REPLICATED}
     * mode will be used by default (defined by #DFLT_CACHE_MODE} constant).
     *
     * @return {@code True} if cache is local.
     */
    public GridCacheMode getCacheMode();

    /**
     * Gets time to live for all objects in cache. This value can be overridden for individual objects.
     * If not set, then value is {@code 0} which means that objects never expire.
     *
     * @return Time to live for all objects in cache.
     */
    public long getDefaultTimeToLive();

    /**
     * Gets cache eviction policy. By default, {@link GridCacheLirsEvictionPolicy}
     * will be used with default settings.
     *
     * @return Cache eviction policy.
     */
    public <K, V> GridCacheEvictionPolicy<K, V> getEvictionPolicy();


    /**
     * Gets eviction policy for {@code near} cache which is different from the one used for
     * {@code partitioned} cache. By default, {@link GridCacheLirsEvictionPolicy}
     * will be used with maximum size set to {@link #DFLT_NEAR_SIZE} value.
     *
     * @return Cache eviction policy.
     */
    public <K, V> GridCacheEvictionPolicy<K, V> getNearEvictionPolicy();


    /**
     * Default cache transaction isolation to use when one is not explicitly
     * specified. Default value is defined by {@link #DFLT_TX_ISOLATION}.
     *
     * @return Default cache transaction isolation.
     * @see GridCacheTx
     */
    public GridCacheTxIsolation getDefaultTxIsolation();

    /**
     * Default cache transaction concurrency to use when one is not explicitly
     * specified. Default value is defined by {@link #DFLT_TX_CONCURRENCY}.
     *
     * @return Default cache transaction concurrency.
     * @see GridCacheTx
     */
    public GridCacheTxConcurrency getDefaultTxConcurrency();

    /**
     * Gets initial cache size which will be used to pre-create internal
     * hash table after start. Default value is defined by {@link #DFLT_START_SIZE}.
     *
     * @return Initial cache size.
     */
    public int getStartSize();

    /**
     * Gets initial cache size for near cache which will be used to pre-create internal
     * hash table after start. Default value is defined by {@link #DFLT_START_SIZE}.
     *
     * @return Initial near cache size.
     */
    public int getNearStartSize();

    /**
     * Gets flag indicating whether near cache is enabled in case of
     * {@link GridCacheMode#PARTITIONED PARTITIONED} mode. It's {@code true}
     * by default.
     *
     * @return Flag indicating whether near cache is enabled or not.
     */
    public boolean isNearEnabled();

    /**
     * Gets underlying persistent storage for read-through and write-through operations.
     * If not provided, cache will not exhibit read-through or write-through behavior.
     *
     * @return Underlying persistent storage for read-through and write-through operations.
     */
    public <K, V> GridCacheStore<K, V> getStore();

    /**
     * Gets key topology resolver to provide mapping from keys to nodes.
     *
     * @return Key topology resolver to provide mapping from keys to nodes.
     */
    public <K> GridCacheAffinity<K> getAffinity();

    /**
     * If {@code true}, then all transactional values will be written to persistent
     * storage at {@link GridCacheTx#commit()} phase. If {@code false}, then values
     * will be persisted after every operation. Default value is {@code true}.
     *
     * @return Flag indicating whether to persist once on commit, or after every
     *      operation.
     */
    public boolean isBatchUpdateOnCommit();

    /**
     * Gets default transaction timeout. Default value is defined by {@link #DFLT_TRANSACTION_TIMEOUT}
     * which is {@code 0} and means that transactions will never time out.
     *
     * @return Default transaction timeout.
     */
    public long getDefaultTxTimeout();

    /**
     * Gets default lock acquisition timeout. Default value is defined by {@link #DFLT_LOCK_TIMEOUT}
     * which is {@code 0} and means that lock acquisition will never timeout.
     *
     * @return Default lock timeout.
     */
    public long getDefaultLockTimeout();

    /**
     * Invalidation flag. If {@code true}, values will be invalidated (nullified) upon commit.
     *
     * @return Invalidation flag.
     */
    public boolean isInvalidate();

    /**
     * Gets refresh-ahead ratio. If non-zero, then entry will be preloaded in the back-ground
     * whenever it's accessed and this refresh ratio of it's total time-to-live has passed.
     * This feature ensures that entries are always automatically re-cached whenever they are
     * nearing expiration.
     * <p>
     * For example, if refresh ration is set to {@code 0.75} and entry's time-to-live is
     * {@code 1} minute, then if this entry is accessed any time after {@code 45} seconds
     * (which is 0.75 of a minute), the cached value will be immediately returned, but
     * entry will be automatically reloaded from persistent store in the background.
     *
     * @return Refresh-ahead ratio.
     */
    public double getRefreshAheadRatio();

    /**
     * Gets transaction manager finder for integration for JEE app servers.
     *
     * @return Transaction manager finder.
     */
    public GridCacheTmLookup getTransactionManagerLookup();

    /**
     * Gets preload mode for distributed cache.
     *
     * @return Preload mode.
     */
    public GridCachePreloadMode getPreloadMode();

    /**
     * Gets size (in number bytes) to be loaded within a single preload message.
     * Preloading algorithm will split total data set on every node into multiple
     * batches prior to sending data.
     *
     * @return Size in bytes of a single preload message.
     */
    public int getPreloadBatchSize();

    /**
     * Gets size of preloading thread pool. Note that size serves as a hint and implementation
     * may create more threads for preloading than specified here (but never less threads).
     * <p>
     * Default value is {@link #DFLT_PRELOAD_THREAD_POOL_SIZE}.
     *
     * @return Size of preloading thread pool.
     */
    public int getPreloadThreadPoolSize();

    /**
     * Gets query types to use to auto index values of boxed and unboxed primitive types,
     * Strings and Dates.
     *
     * @return Query types to use to auto index values of primitives, strings, and dates.
     */
    public Collection<GridCacheQueryType> getAutoIndexQueryTypes();

    /**
     * Absolute or relative to {@code GRIDGAIN_HOME} path for storing query indexes on disk
     * (if they are configured to be stored on disk). If not provided, by default indexes will be
     * stored under default folder defined by {@link #DFLT_IDX_PARENT_FOLDER_NAME} constant.
     *
     * @return Absolute or relative to {@code GRIDGAIN_HOME} path for storing query indexes on disk.
     */
    public String getIndexPath();

    /**
     * Flag indicating whether full class names, i.e. {@link Class#getName()} values, or
     * simple class names, i.e. {@link Class#getSimpleName()} values should be used in
     * queries.
     * <p>
     * Default value is {@code false}.
     *
     * @return Use full class names for index tables or short.
     */
    public boolean isIndexFullClassName();

    /**
     * This flag indicates that the same key object can only be associated with the same value
     * type and a value type can only be associated with keys of the same type.
     * <p>
     * For example, let's assume that you have keys {@code K1} and {@code K2} of types
     * {@code Kt1} and {@code Kt2}, and values {@code V1} and {@code V2} of type {@code Vt1}
     * and {@code Vt2}. If this flag is set to {@code true}, then once key {@code K1} is
     * associated with value of type {@code Vt1}, this {@code K1} can never be associated
     * with a value of type {@code Vt2}. Also, once a value of type {@code Vt1} is associated with
     * a key of type {@code Kt1}, all values of type {@code Vt1} will have to be associated with
     * keys of type {@code Kt1} and can never be associated with keys of type {@code Kt2}.
     * <p>
     * The behavior described above is how we usually operate with data. However, in certain
     * cases it may be desired to associate a key with values of different types over time and
     * in that case you should set this flag to {@code false}.
     * <p>
     * Setting this flag to {@code true}, which is default, allows cache implementation to
     * perform performance optimizations for queries.
     *
     * @return {@code True} for fixed typing, {@code false} otherwise.
     */
    public boolean isIndexFixedTyping();

    /**
     * Flag indicating whether query storage should be deleted or not upon start
     * (default is {@code true}).
     *
     * @return If {@code true}, cache indexes will be cleaned up upon start.
     */
    public boolean isIndexCleanup();

    /**
     * Flag indicating whether query index should be stored only in memory (not on disk).
     * <p>
     * Note that cache queries with {@link GridCacheQueryType#LUCENE LUCENE} type cannot
     * be used in case of in-memory index database, i.e. if this property is {@code true}.
     *
     * @return {@code True} if index should be stored only in memory (not on disk).
     */
    public boolean isIndexMemoryOnly();

    /**
     * Gets the maximum memory used per single operation with query index
     * (store and remove), in bytes. Operations that use more memory are buffered
     * to disk, slowing down the operation. The default max size is 100000. 0 means no limit.
     *
     * @return Maximum memory used for a single query index operation in bytes. 0 means no limit.
     */
    public int getIndexMaxOperationMemory();

    /**
     *
     * @return Addition options to H2 database (query storage).
     */
    public String getIndexH2Options();

    /**
     * Gets frequency of running H2 "ANALYZE" command in order to update
     * selectivity statistics of H2 database tables. Default value is
     * defined by {@link #DFLT_IDX_ANALYZE_FREQ} and equals to 10 minutes.
     *
     * @return Frequency (in milliseconds) for running H2 "ANALYZE" command.
     */
    public long getIndexAnalyzeFrequency();

    /**
     * Gets number of samples used to run H2 "ANALYZE" command in order to update
     * selectivity statistics of H2 database tables. In other words, this value
     * means the number of rows to scan for each db table. Default value is defined
     * by {@link #DFLT_IDX_ANALYZE_SAMPLE_SIZE} and equals to 10000.
     *
     * @return Frequency (in milliseconds) for running H2 "ANALYZE" command.
     */
    public long getIndexAnalyzeSampleSize();

    /**
     * Optional user name for index store.
     *
     * @return Optional user name for index store.
     */
    public String getIndexUsername();

    /**
     * Optional password for index store.
     *
     * @return Optional password for index store.
     */
    public String getIndexPassword();

    /**
     * Gets frequency at which distributed garbage collector will
     * check other nodes if there are any zombie locks left over.
     *
     * @return Frequency of GC in milliseconds ({@code 0} to disable GC).
     */
    public int getGarbageCollectorFrequency();

    /**
     * Flag indicating whether GridGain should wait for commit replies from all nodes. By default
     * GridGain will not wait for responses from participating nodes, which means that remote
     * nodes may get their state updated a bit after {@link GridCacheTx#commit()} method completes.
     * Setting this flag to {@code true} guarantees that update will have reached all nodes prior
     * to completing {@link GridCacheTx#commit()} method.
     *
     * @return {@code True} in case of synchronous commit.
     */
    public boolean isSynchronousCommit();

    /**
     * Flag indicating whether GridGain should wait for rollback replies from all nodes. By default
     * GridGain will not wait for responses from participating nodes, which means that remote
     * nodes may get their state updated a bit after {@link GridCacheTx#commit()} method completes.
     * Setting this flag to {@code true} guarantees that update will have reached all nodes prior
     * to completing {@link GridCacheTx#commit()} method.
     *
     * @return {@code True} in case of synchronous rollback.
     */
    public boolean isSynchronousRollback();

    /**
     * Flag indicating whether GridGain should use swap storage by default if user did not
     * specify this explicitly using those methods whether it is possible.
     * <p>
     * Note that this flag may be overridden for cache projection created with flag
     * {@link GridCacheFlag#SKIP_SWAP}.
     *
     * @return {@code true} if swap storage is used by default for those methods that may
     *      read from or write to it.
     */
    public boolean isSwapEnabled();

    /**
     * Flag indicating whether GridGain should activate read-through/write-through behaviour
     * by default.
     * <p>
     * Note that this flag may be overridden for cache projection created with flag
     * {@link GridCacheFlag#SKIP_STORE}.
     *
     * @return {@code true} if configured persistent store is used by default.
     */
    public boolean isStoreEnabled();

    /**
     * Cloner to be used for cloning values that are returned to user only if {@link GridCacheFlag#CLONE}
     * is set on {@link GridCacheProjection}. Cloning values is useful when it is needed to get value from
     * cache, change it and put it back (if the value was not cloned, then user would be updating the
     * cached reference which would violate cache integrity).
     * <p>
     * <b>NOTE:</b> by default, cache uses {@link GridCacheBasicCloner} implementation which will clone only objects
     * implementing {@link Cloneable} interface. You can also configure cache to use
     * {@link GridCacheDeepCloner} which will perform deep-cloning of all objects returned from cache,
     * regardless of the {@link Cloneable} interface. If none of the above cloners fit your
     * logic, you can also provide your own implementation of {@link GridCacheCloner} interface.
     *
     * @return Cloner to be used if {@link GridCacheFlag#CLONE} flag is set on cache projection.
     */
    public GridCacheCloner getCloner();

    /**
     * Affinity key mapper used to provide custom affinity key for any given key.
     * Affinity mapper is particularly useful when several objects need to be collocated
     * on the same node (they will also be backed up on the same nodes as well).
     * <p>
     * If not provided, then default implementation will be used. The default behavior
     * is described in {@link GridCacheAffinityMapper} documentation.
     *
     * @return Mapper to use for affinity key mapping.
     */
    public <K> GridCacheAffinityMapper<K> getAffinityMapper();

    /**
     * Gets default number of sequence values reserved for {@link GridCacheAtomicSequence} instances. After
     * a certain number has been reserved, consequent increments of sequence will happen locally,
     * without communication with other nodes, until the next reservation has to be made.
     * <p>
     * Default value is {@link #DFLT_ATOMIC_SEQUENCE_RESERVE_SIZE}.
     *
     * @return Atomic sequence reservation size.
     */
    public int getAtomicSequenceReserveSize();
}
