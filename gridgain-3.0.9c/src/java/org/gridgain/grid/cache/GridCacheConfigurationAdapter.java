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
import org.gridgain.grid.cache.jta.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.cache.store.*;
import org.gridgain.grid.typedef.internal.*;

import javax.transaction.*;
import java.util.*;

/**
 * Cache configuration adapter. Use this convenience adapter when creating
 * cache configuration to set on {@link GridConfigurationAdapter#setCacheConfiguration(GridCacheConfiguration...)}
 * method. This adapter is a simple bean and can be configured from Spring XML files
 * (or other DI frameworks).
 * <p>
 * Note that absolutely all configuration properties are optional, so users
 * should only change what they need.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheConfigurationAdapter implements GridCacheConfiguration {
    /** Cache name. */
    private String name;

    /** Default batch size for all cache's sequences. */
    private int seqReserveSize = DFLT_ATOMIC_SEQUENCE_RESERVE_SIZE;

    /** Preload thread pool size. */
    private int preloadPoolSize = DFLT_PRELOAD_THREAD_POOL_SIZE;

    /** Default time to live for cache entries. */
    private long ttl = DFLT_TIME_TO_LIVE;

    /** Cache expiration policy. */
    private GridCacheEvictionPolicy evictPolicy;

    /** Near cache eviction policy. */
    private GridCacheEvictionPolicy nearEvictPolicy;

    /** Transaction isolation. */
    private GridCacheTxIsolation dfltIsolation = DFLT_TX_ISOLATION;

    /** Cache concurrency. */
    private GridCacheTxConcurrency dfltConcurrency = DFLT_TX_CONCURRENCY;

    /** Default transaction timeout. */
    private long dfltTxTimeout = DFLT_TRANSACTION_TIMEOUT;

    /** Default lock timeout. */
    private long dfltLockTimeout = DFLT_LOCK_TIMEOUT;

    /** Default cache start size. */
    private int startSize = DFLT_START_SIZE;

    /** Default near cache start size. */
    private int nearStartSize = DFLT_NEAR_START_SIZE;

    /** Near cache flag. */
    private boolean nearEnabled = true;

    /** */
    private GridCacheStore<?, ?> store;

    /** Node group resolver. */
    private GridCacheAffinity<?> aff;

    /** Cache mode. */
    private GridCacheMode cacheMode;

    /** Flag to enable transactional batch update. */
    private boolean isTxBatchUpdate = true;

    /** Flag indicating whether this is invalidation-based cache. */
    private boolean invalidate;

    /** Refresh-ahead ratio. */
    private double refreshAheadRatio;

    /** */
    private GridCacheTmLookup tmLookup;

    /** Distributed cache preload mode. */
    private GridCachePreloadMode preloadMode = DFLT_PRELOAD_MODE;

    /** Preload batch size. */
    private int preloadBatchSize = DFLT_PRELOAD_BATCH_SIZE;

    /** */
    private Collection<GridCacheQueryType> autoIndexTypes;

    /** Path to index database, default will be used if null. */
    private String idxPath;

    /** Use full class names for index tables or short. */
    private boolean idxFullClassName;

    /** Mark that all keys will be the same type to make possible to store them as native database type. */
    private boolean idxFixedTyping = true;

    /** Leave database after exit or not. */
    private boolean idxCleanup = true;

    /** Use only memory for index database if true.*/
    private boolean idxMemOnly;

    /** Maximum memory used for delete and insert in bytes. 0 means no limit. */
    private int idxMaxOperationMem = DFLT_IDX_MAX_OPERATIONAL_MEM;

    /** */
    private String idxUser;

    /** */
    private String idxPswd;

    /** */
    private int gcFreq = DFLT_GC_FREQUENCY;

    /** */
    private boolean syncCommit;

    /** */
    private boolean syncRollback;

    /** */
    private boolean swapEnabled = true;

    /** */
    private boolean storeEnabled = true;

    /** */
    private String idxH2Opt;

    /** */
    private long idxAnalyzeFreq = DFLT_IDX_ANALYZE_FREQ;

    /** */
    private long idxAnalyzeSampleSize = DFLT_IDX_ANALYZE_SAMPLE_SIZE;

    /** */
    private GridCacheCloner cloner;

    /** */
    private GridCacheAffinityMapper affMapper;

    /**
     * Empty constructor (all values are initialized to their defaults).
     */
    public GridCacheConfigurationAdapter() {
        /* No-op. */
    }

    /**
     * Copy constructor.
     *
     * @param cacheCfg Configuration to copy.
     */
    public GridCacheConfigurationAdapter(GridCacheConfiguration cacheCfg) {
        /*
         * NOTE: MAKE SURE TO PRESERVE ALPHABETIC ORDER!
         * ==============================================
         */
        aff = cacheCfg.getAffinity();
        autoIndexTypes = cacheCfg.getAutoIndexQueryTypes();
        cacheMode = cacheCfg.getCacheMode();
        cloner = cacheCfg.getCloner();
        dfltConcurrency = cacheCfg.getDefaultTxConcurrency();
        dfltIsolation = cacheCfg.getDefaultTxIsolation();
        dfltLockTimeout = cacheCfg.getDefaultLockTimeout();
        dfltTxTimeout = cacheCfg.getDefaultTxTimeout();
        evictPolicy = cacheCfg.getEvictionPolicy();
        gcFreq = cacheCfg.getGarbageCollectorFrequency();
        idxH2Opt = cacheCfg.getIndexH2Options();
        idxAnalyzeFreq = cacheCfg.getIndexAnalyzeFrequency();
        idxAnalyzeSampleSize = cacheCfg.getIndexAnalyzeSampleSize();
        idxCleanup = cacheCfg.isIndexCleanup();
        idxFixedTyping = cacheCfg.isIndexFixedTyping();
        idxFullClassName = cacheCfg.isIndexFullClassName();
        idxMaxOperationMem = cacheCfg.getIndexMaxOperationMemory();
        idxMemOnly = cacheCfg.isIndexMemoryOnly();
        idxPath = cacheCfg.getIndexPath();
        idxPswd = cacheCfg.getIndexPassword();
        idxUser = cacheCfg.getIndexUsername();
        invalidate = cacheCfg.isInvalidate();
        isTxBatchUpdate = cacheCfg.isBatchUpdateOnCommit();
        name = cacheCfg.getName();
        nearStartSize = cacheCfg.getNearStartSize();
        nearEnabled = cacheCfg.isNearEnabled();
        nearEvictPolicy = cacheCfg.getNearEvictionPolicy();
        preloadMode = cacheCfg.getPreloadMode();
        preloadBatchSize = cacheCfg.getPreloadBatchSize();
        preloadPoolSize = cacheCfg.getPreloadThreadPoolSize();
        refreshAheadRatio = cacheCfg.getRefreshAheadRatio();
        seqReserveSize = cacheCfg.getAtomicSequenceReserveSize();
        startSize = cacheCfg.getStartSize();
        store = cacheCfg.getStore();
        storeEnabled = cacheCfg.isStoreEnabled();
        swapEnabled = cacheCfg.isSwapEnabled();
        syncCommit = cacheCfg.isSynchronousCommit();
        syncRollback = cacheCfg.isSynchronousRollback();
        tmLookup = cacheCfg.getTransactionManagerLookup();
        ttl = cacheCfg.getDefaultTimeToLive();
    }

    /** {@inheritDoc} */
    @Override public String getName() {
        return name;
    }

    /**
     * Sets cache name.
     *
     * @param name Cache name. May be <tt>null</tt>, but may not be empty string.
     */
    public void setName(String name) {
        A.ensure(name == null || !name.isEmpty(), "Name cannot be null or empty.");

        this.name = name;
    }

    @Override public long getDefaultTimeToLive() {
        return ttl;
    }

    /**
     * Sets time to live for all objects in cache. This value can be override for individual objects.
     *
     * @param ttl Time to live for all objects in cache.
     */
    public void setDefaultTimeToLive(long ttl) {
        this.ttl = ttl;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> GridCacheEvictionPolicy<K, V> getEvictionPolicy() {
        return evictPolicy;
    }

    /**
     * Sets cache eviction policy.
     *
     * @param evictPolicy Cache expiration policy.
     */
    public <K, V> void setEvictionPolicy(GridCacheEvictionPolicy<K, V> evictPolicy) {
        this.evictPolicy = evictPolicy;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> GridCacheEvictionPolicy<K, V> getNearEvictionPolicy() {
        return nearEvictPolicy;
    }

    /**
     * Sets eviction policy for near cache. This property is only used for
     * {@link GridCacheMode#PARTITIONED} caching mode.
     *
     * @param nearEvictPolicy Eviction policy for near cache.
     */
    public void setNearEvictionPolicy(GridCacheEvictionPolicy nearEvictPolicy) {
        this.nearEvictPolicy = nearEvictPolicy;
    }

    /** {@inheritDoc} */
    @Override public GridCacheTxConcurrency getDefaultTxConcurrency() {
        return dfltConcurrency;
    }

    /**
     * Sets default transaction concurrency.
     *
     * @param dfltConcurrency Default cache transaction concurrency.
     */
    public void setDefaultTxConcurrency(GridCacheTxConcurrency dfltConcurrency) {
        this.dfltConcurrency = dfltConcurrency;
    }

    /** {@inheritDoc} */
    @Override public GridCacheTxIsolation getDefaultTxIsolation() {
        return dfltIsolation;
    }

    /**
     * Sets default transaction isolation.
     *
     * @param dfltIsolation Default cache transaction isolation.
     */
    public void setDefaultTxIsolation(GridCacheTxIsolation dfltIsolation) {
        this.dfltIsolation = dfltIsolation;
    }

    /** {@inheritDoc} */
    @Override public int getStartSize() {
        return startSize;
    }

    /**
     * Initial size for internal hash map.
     *
     * @param startSize Cache start size.
     */
    public void setStartSize(int startSize) {
        this.startSize = startSize;
    }

    /** {@inheritDoc} */
    @Override public int getNearStartSize() {
        return nearStartSize;
    }

    /**
     * Start size for near cache. This property is only used for
     * {@link GridCacheMode#PARTITIONED} caching mode.
     *
     * @param nearStartSize Start size for near cache.
     */
    public void setNearStartSize(int nearStartSize) {
        this.nearStartSize = nearStartSize;
    }

    /** {@inheritDoc} */
    @Override public boolean isNearEnabled() {
        return nearEnabled;
    }

    /**
     * Sets flag indicating whether near cache is enabled in case of
     * {@link GridCacheMode#PARTITIONED PARTITIONED} mode. It is {@code true}
     * by default.
     *
     * @param nearEnabled Flag indicating whether near cache is enabled.
     */
    public void setNearEnabled(boolean nearEnabled) {
        this.nearEnabled = nearEnabled;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K, V> GridCacheStore<K, V> getStore() {
        return (GridCacheStore<K, V>)store;
    }

    /**
     * Sets persistent storage for cache data.
     *
     * @param store Persistent cache store.
     */
    public <K, V> void setStore(GridCacheStore<K, V> store) {
        this.store = store;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K> GridCacheAffinity<K> getAffinity() {
        return (GridCacheAffinity<K>)aff;
    }

    /**
     * Sets affinity for cache keys.
     *
     * @param aff Cache key affinity.
     */
    public <K> void setAffinity(GridCacheAffinity<K> aff) {
        this.aff = aff;
    }

    /** {@inheritDoc} */
    @Override public GridCacheMode getCacheMode() {
        return cacheMode;
    }

    /**
     * Sets caching mode.
     *
     * @param cacheMode Caching mode.
     */
    public void setCacheMode(GridCacheMode cacheMode) {
        this.cacheMode = cacheMode;
    }

    /** {@inheritDoc} */
    @Override public boolean isBatchUpdateOnCommit() {
        return isTxBatchUpdate;
    }

    /**
     * Sets flag indicating if persistent store should be updated after every cache
     * operation or once at commit time. Default is {@code true}.
     *
     * @param txBatchUpdate {@code True} if updates should be batched at the end of transaction,
     *      {@code false} if updates should be propagated to persistent store
     *      individually as they occur (without waiting to the end of transaction).
     */
    public void setBatchUpdateOnCommit(boolean txBatchUpdate) {
        isTxBatchUpdate = txBatchUpdate;
    }

    /** {@inheritDoc} */
    @Override public long getDefaultTxTimeout() {
        return dfltTxTimeout;
    }

    /**
     * Sets default transaction timeout in milliseconds. By default this value
     * is defined by {@link #DFLT_TRANSACTION_TIMEOUT}.
     *
     * @param dfltTxTimeout Default transaction timeout.
     */
    public void setDefaultTxTimeout(long dfltTxTimeout) {
        this.dfltTxTimeout = dfltTxTimeout;
    }

    /** {@inheritDoc} */
    @Override public long getDefaultLockTimeout() {
        return dfltLockTimeout;
    }

    /**
     * Sets default lock timeout in milliseconds. By default this value
     * is defined by {@link #DFLT_LOCK_TIMEOUT}.
     *
     * @param dfltLockTimeout Default lock timeout.
     */
    public void setDefaultLockTimeout(long dfltLockTimeout) {
        this.dfltLockTimeout = dfltLockTimeout;
    }

    /** {@inheritDoc} */
    @Override public boolean isInvalidate() {
        return invalidate;
    }

    /**
     * Sets invalidation flag for this transaction. Default is {@code false}.
     *
     * @param invalidate Flag to set this cache into invalidation-based mode.
     *      Default value is {@code false}.
     */
    public void setInvalidate(boolean invalidate) {
        this.invalidate = invalidate;
    }

    /** {@inheritDoc} */
    @Override public double getRefreshAheadRatio() {
        return refreshAheadRatio;
    }

    /**
     * Sets refresh-ahead ration for this transaction. Values other than zero
     * specify how soon entries will be auto-reloaded from persistent store prior to
     * expiration.
     *
     * @param refreshAheadRatio Refresh-ahead ratio.
     */
    public void setRefreshAheadRatio(double refreshAheadRatio) {
        this.refreshAheadRatio = refreshAheadRatio;
    }

    /** {@inheritDoc} */
    @Override public GridCacheTmLookup getTransactionManagerLookup() {
        return tmLookup;
    }

    /**
     * Sets look up mechanism for available {@link TransactionManager} implementation, if any.
     *
     * @param tmLookup Lookup implementation that is used to receive JTA transaction manager.
     */
    public void setTransactionManagerLookup(GridCacheTmLookup tmLookup) {
        this.tmLookup = tmLookup;
    }

    /**
     * Sets cache preload mode.
     *
     * @param preloadMode Preload mode.
     */
    public void setPreloadMode(GridCachePreloadMode preloadMode) {
        this.preloadMode = preloadMode;
    }

    /** {@inheritDoc} */
    @Override public GridCachePreloadMode getPreloadMode() {
        return preloadMode;
    }

    /** {@inheritDoc} */
    @Override public int getPreloadBatchSize() {
        return preloadBatchSize;
    }

    /**
     * Sets preload batch size.
     *
     * @param preloadBatchSize Preload batch size.
     */
    public void setPreloadBatchSize(int preloadBatchSize) {
        this.preloadBatchSize = preloadBatchSize;
    }

    /** {@inheritDoc} */
    @Override public String getIndexPath() {
        return idxPath;
    }

    /**
     * Sets file path (absolute or relative to {@code GRIDGAIN_HOME} to store
     * cache indexes.
     *
     * @param idxPath Path to index database, default will be used if null.
     */
    public void setIndexPath(String idxPath) {
        this.idxPath = idxPath;
    }

    /** {@inheritDoc} */
    @Override public boolean isIndexFullClassName() {
        return idxFullClassName;
    }

    /**
     * Flag indicating weather full or simple class names should be used for querying.
     * Simple class name may result in more concise queries, but may not be unique.
     * Default is {@code false}. This property must be set to {@code true} whenever
     * simple class names are not unique.
     *
     * @param idxFullClassName Flag indicating weather full or simple class names should be used for querying.
     */
    public void setIndexFullClassName(boolean idxFullClassName) {
        this.idxFullClassName = idxFullClassName;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCacheQueryType> getAutoIndexQueryTypes() {
        return autoIndexTypes;
    }

    /**
     * Sets query types to use to auto index values of primitive types. If not empty,
     * then all encountered primitive or boxed types will be auto-indexed at all times
     * for specified query types.
     *
     * @param autoIndexTypes Query types to use to auto index values of primitive types.
     */
    public void setAutoIndexQueryTypes(Collection<GridCacheQueryType> autoIndexTypes) {
        this.autoIndexTypes = autoIndexTypes;
    }

    /** {@inheritDoc} */
    @Override public boolean isIndexFixedTyping() {
        return idxFixedTyping;
    }

    /**
     * Sets fixed typing flag. See {@link #isIndexFixedTyping()} documentation for
     * explanation about this parameter.
     *
     * @param idxFixedTyping {@code True} for fixed typing.
     * @see #isIndexFixedTyping()
     */
    public void setIndexFixedTyping(boolean idxFixedTyping) {
        this.idxFixedTyping = idxFixedTyping;
    }

    /** {@inheritDoc} */
    @Override public boolean isIndexCleanup() {
        return idxCleanup;
    }

    /**
     * Flag indicating whether indexes should be deleted on system shutdown
     * or startup. Default is {@code true}.
     *
     * @param idxCleanup Flag indicating whether indexes should be deleted on stop or start.
     */
    public void setIndexCleanup(boolean idxCleanup) {
        this.idxCleanup = idxCleanup;
    }

    /** {@inheritDoc} */
    @Override public boolean isIndexMemoryOnly() {
        return idxMemOnly;
    }

    /**
     * Flag indicating whether query indexes should be kept only in memory
     * or offloaded on disk as well. Default is {@code false}.
     *
     * @param idxMemOnly Use only memory for indexes if {@code true}.
     */
    public void setIndexMemoryOnly(boolean idxMemOnly) {
        this.idxMemOnly = idxMemOnly;
    }

    /** {@inheritDoc} */
    @Override public int getIndexMaxOperationMemory() {
        return idxMaxOperationMem;
    }

    /**
     * Maximum memory used for delete and insert in bytes. {@code 0} means no limit.
     *
     * @param idxMaxOperationMem Maximum memory used for delete and insert in bytes.
     */
    public void setIndexMaxOperationMemory(int idxMaxOperationMem) {
        this.idxMaxOperationMem = idxMaxOperationMem;
    }

    /** {@inheritDoc} */
    @Override public String getIndexH2Options() {
        return idxH2Opt;
    }

    /**
     * Any additional options for the underlying H2 database used for querying.
     *
     * @param idxH2Opt Addition options for underlying H2 database.
     */
    public void setIndexH2Options(String idxH2Opt) {
        this.idxH2Opt = idxH2Opt;
    }

    /** {@inheritDoc} */
    @Override public long getIndexAnalyzeFrequency() {
        return idxAnalyzeFreq;
    }

    /**
     * Sets frequency of running H2 "ANALYZE" command.
     *
     * @param idxAnalyzeFreq Frequency in milliseconds.
     */
    public void setIndexAnalyzeFrequency(long idxAnalyzeFreq) {
        this.idxAnalyzeFreq = idxAnalyzeFreq;
    }

    /** {@inheritDoc} */
    @Override public long getIndexAnalyzeSampleSize() {
        return idxAnalyzeSampleSize;
    }

    /**
     * Sets number of samples used to run H2 "ANALYZE" command.
     *
     * @param idxAnalyzeSampleSize Number of samples (db table rows rows).
     */
    public void setIndexAnalyzeSampleSize(long idxAnalyzeSampleSize) {
        this.idxAnalyzeSampleSize = idxAnalyzeSampleSize;
    }

    /** {@inheritDoc} */
    @Override public String getIndexUsername() {
        return idxUser;
    }

    /**
     * Optional username to login to index database.
     *
     * @param idxUser Index database user name.
     */
    public void setIndexUsername(String idxUser) {
        this.idxUser = idxUser;
    }

    /** {@inheritDoc} */
    @Override public String getIndexPassword() {
        return idxPswd;
    }

    /**
     * Optional password to login to index database.
     *
     * @param idxPswd Index database password.
     */
    public void setIndexPassword(String idxPswd) {
        this.idxPswd = idxPswd;
    }

    /** {@inheritDoc} */
    @Override public int getGarbageCollectorFrequency() {
        return gcFreq;
    }

    /**
     * Sets frequency in milliseconds for internal distributed garbage collector - {@code 0} to disable
     * distributed garbage collection.
     *
     * @param gcFreq Frequency of GC in milliseconds. 0 to disable GC.
     */
    public void setGarbageCollectorFrequency(int gcFreq) {
        this.gcFreq = gcFreq;
    }

    /** {@inheritDoc} */
    @Override public boolean isSynchronousCommit() {
        return syncCommit;
    }

    /**
     * Flag indicating whether nodes on which user transaction completed should wait
     * for the same transaction on remote nodes to complete.
     *
     * @param syncCommit {@code True} in case of synchronous commit.
     */
    public void setSynchronousCommit(boolean syncCommit) {
        this.syncCommit = syncCommit;
    }

    /** {@inheritDoc} */
    @Override public boolean isSynchronousRollback() {
        return syncRollback;
    }

    /**
     * Flag indicating whether nodes on which user transaction was rolled back should wait
     * for the same transaction on remote nodes to complete.
     *
     * @param syncRollback {@code True} in case of synchronous rollback.
     */
    public void setSynchronousRollback(boolean syncRollback) {
        this.syncRollback = syncRollback;
    }

    /** {@inheritDoc} */
    @Override public boolean isSwapEnabled() {
        return swapEnabled;
    }

    /**
     * Flag indicating whether swap storage ise enabled or not.
     *
     * @param swapEnabled {@code true} if swap storage is enabled by default.
     */
    public void setSwapEnabled(boolean swapEnabled) {
        this.swapEnabled = swapEnabled;
    }

    /** {@inheritDoc} */
    @Override public boolean isStoreEnabled() {
        return storeEnabled;
    }

    /**
     *
     * @param storeEnabled {@code true} if store is enabled by default.
     */
    public void setStoreEnabled(boolean storeEnabled) {
        this.storeEnabled = storeEnabled;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public GridCacheCloner getCloner() {
        return cloner;
    }

    /**
     * Sets cloner to be used if {@link GridCacheFlag#CLONE} flag is set on projection.
     *
     * @param cloner Cloner to use.
     * @see #getCloner()
     */
    public void setCloner(GridCacheCloner cloner) {
        this.cloner = cloner;
    }

    /** {@inheritDoc} */
    @Override public int getAtomicSequenceReserveSize() {
        return seqReserveSize;
    }

    /**
     * Gets default number of sequence values reserved for {@link GridCacheAtomicSequence} instances. After
     * a certain number has been reserved, consequent increments of sequence will happen locally,
     * without communication with other nodes, until the next reservation has to be made.
     *
     * @param seqReserveSize Atomic sequence reservation size.
     * @see #getAtomicSequenceReserveSize()
     */
    public void setAtomicSequenceReserveSize(int seqReserveSize) {
        this.seqReserveSize = seqReserveSize;
    }

    /** {@inheritDoc} */
    @Override public int getPreloadThreadPoolSize() {
        return preloadPoolSize;
    }

    /**
     * Sets size of preloading thread pool. Note that size serves as a hint and implementation
     * may create more threads for preloading than specified here (but never less threads).
     *
     * @param preloadPoolSize Size of preloading thread pool.
     */
    public void setPreloadThreadPoolSize(int preloadPoolSize) {
        this.preloadPoolSize = preloadPoolSize;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <K> GridCacheAffinityMapper<K> getAffinityMapper() {
        return (GridCacheAffinityMapper<K>)affMapper;
    }

    /**
     * Sets custom affinity mapper. If not provided, then default implementation
     * will be used. The default behavior is described in
     * {@link GridCacheAffinityMapper} documentation.
     *
     * @param <K> Key type.
     * @param affMapper Affinity mapper.
     */
    public <K> void setAffinityMapper(GridCacheAffinityMapper<K> affMapper) {
        this.affMapper = affMapper;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheConfigurationAdapter.class, this);
    }
}
