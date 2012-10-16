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
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.thread.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.worker.*;
import org.h2.fulltext.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.sql.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.cache.GridCacheConfiguration.*;
import static org.gridgain.grid.cache.query.GridCacheQueryType.*;

/**
 * Cache query index. Manages full life-cycle of query index database (h2).
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"UnnecessaryFullyQualifiedName"})
public class GridCacheQueryIndex<K, V> {
    /** Default DB name. */
    private static final String DFLT_DB_NAME = "gridgain_indexes";

    /**
     * Index db schema name. It's strongly recommended to use "PUBLIC" schema
     * because of internal problems in H2 fulltext search engine.
     */
    private static final String DFLT_SCHEMA_NAME = "PUBLIC";

    /** Default DB options. */
    private static final String DFLT_DB_OPTIONS = ";MVCC=true;LOCK_MODE=3;DB_CLOSE_ON_EXIT=FALSE";

    /** */
    private static final String KEY_FIELD_NAME = "_key";

    /** */
    private static final String VALUE_FIELD_NAME = "_val";

    /** */
    private static final String VALUE_STRING_FIELD_NAME = "_val_str";

    /** */
    private static final String VERSION_FIELD_NAME = "_ver";

    /** */
    private static final String KEY_CLS_LDR_FIELD_NAME = "_key_cls_ldr";

    /** */
    private static final String ANALYZE_THREAD_NAME = "query-index-analyzer";

    /** Cache context. */
    private final GridCacheContext<K, V> cacheCtx;

    /** Logger. */
    private final GridLogger log;

    /** Busy lock. */
    private final ReadWriteLock busyLock = new ReentrantReadWriteLock();

    /** {@code True} if db schema is already created. */
    private final AtomicBoolean schemaCreated = new AtomicBoolean();

    /** Released once db schema is created. */
    private final CountDownLatch schemaLatch = new CountDownLatch(1);

    /** */
    private ReadWriteLock schemaLock = new ReentrantReadWriteLock();

    /** Index db schema name. */
    private static final String schema = DFLT_SCHEMA_NAME;

    /** Class -> query type(key class, value class). */
    private final ConcurrentMap<Class<?>, QueryType> clsMap = new ConcurrentHashMap<Class<?>, QueryType>();

    /** Collection of registered tables. */
    private final Collection<TableDescriptor> tables = new GridConcurrentHashSet<TableDescriptor>();

    /** */
    private static final Collection<Class<?>> simpleTypes = new ArrayList<Class<?>>();

    /** Index db folder. */
    private volatile File folder;

    static {
        Collections.<Class<?>>addAll(simpleTypes,
            int.class,
            Integer.class,
            boolean.class,
            Boolean.class,
            byte.class,
            Byte.class,
            short.class,
            Short.class,
            long.class,
            Long.class,
            BigDecimal.class,
            double.class,
            Double.class,
            float.class,
            Float.class,
            Time.class,
            Timestamp.class,
            java.util.Date.class,
            java.sql.Date.class,
            char.class,
            Character.class,
            String.class,
            UUID.class);
    }

    /** */
    private String dbUrl;

    /** */
    private ThreadLocal<GridByteArrayOutputStream> streamCache = new ThreadLocal<GridByteArrayOutputStream>() {
        @Override public GridByteArrayOutputStream get() {
            GridByteArrayOutputStream out = super.get();

            out.reset();

            return out;
        }

        @Override protected GridByteArrayOutputStream initialValue() {
            return new GridByteArrayOutputStream(4096);
        }
    };

    /** */
    private ThreadLocal<ConnectionWrapper> connectionCache = new ThreadLocal<ConnectionWrapper>() {
        @Nullable @Override public ConnectionWrapper get() {
            ConnectionWrapper c = super.get();

            if (c == null) {
                c = initialValue();

                if (c != null)
                    set(c);
            }

            return c;
        }

        @Nullable @Override protected ConnectionWrapper initialValue() {
            if (!enterBusy())
                return null;

            Connection c = null;

            try {
                String user = cacheCtx.config().getIndexUsername();
                String password = cacheCtx.config().getIndexPassword();

                c = user != null || password != null ? DriverManager.getConnection(dbUrl, user, password) :
                    DriverManager.getConnection(dbUrl);

                connections.add(c);

                return new ConnectionWrapper(c);
            }
            catch (SQLException e) {
                U.error(log, "Failed to initialize DB connection: " + dbUrl, e);

                U.close(c, log);

                return null;
            }
            finally {
                leaveBusy();
            }
        }
    };

    /** */
    private ThreadLocal<SqlStatementCache> stmtCache = new ThreadLocal<SqlStatementCache>();

    /** */
    private final Collection<Connection> connections = new GridConcurrentWeakHashSet<Connection>();

    /** */
    private final Collection<SqlStatementCache> stmtCaches = new GridConcurrentWeakHashSet<SqlStatementCache>();

    /** */
    private GridThread analyzeThread;

    /**
     * @param cacheCtx Cache context.
     */
    GridCacheQueryIndex(GridCacheContext<K, V> cacheCtx) {
        assert cacheCtx != null;

        this.cacheCtx = cacheCtx;

        log = cacheCtx.logger(getClass());
    }

    /**
     * Enters to busy state.
     *
     * @return {@code true} if entered to busy state.
     */
    protected boolean enterBusy() {
        boolean entered = busyLock.readLock().tryLock();

        if (!entered && log.isDebugEnabled())
            log.debug("Failed to enter to busy state since query index is stopping.");

        return entered;
    }

    /**
     * Release read lock for queries execution.
     */
    protected void leaveBusy() {
        busyLock.readLock().unlock();
    }

    /**
     * @throws GridException If failed to start.
     */
    void start() throws GridException {
        if (log.isDebugEnabled())
            log.debug("Starting cache query index...");

        GridCacheConfigurationAdapter cfg = cacheCtx.config();

        if (cfg.getIndexAnalyzeFrequency() <= 0)
            throw new GridException("Configuration parameter 'indexAnalyzeFrequency' must be greater than 0.");

        if (cfg.getIndexAnalyzeSampleSize() <= 0)
            throw new GridException("Configuration parameter 'indexAnalyzeSampleSize' must be greater than 0.");

        SB opt = new SB();

        opt.a(DFLT_DB_OPTIONS);
        opt.a(";MAX_OPERATION_MEMORY=").a(cfg.getIndexMaxOperationMemory());

        if (!F.isEmpty(cfg.getIndexH2Options())) {
            if (!cfg.getIndexH2Options().startsWith(";"))
                opt.a(';');

            opt.a(cfg.getIndexH2Options());
        }

        String dbName = cacheCtx.nodeId().toString() + "_" + cacheCtx.namex();

        if (cfg.isIndexMemoryOnly())
            dbUrl = "jdbc:h2:mem:" + DFLT_DB_NAME + "_" + dbName + opt;
        else {
            if (cfg.getIndexPath() != null) {
                folder = new File(cfg.getIndexPath());

                if (!folder.isAbsolute())
                    folder = !F.isEmpty(U.getGridGainHome()) ?
                        new File(U.getGridGainHome(), cfg.getIndexPath()) :
                        new File(tmpDir(), cfg.getIndexPath());
            }
            else {
                if (!cfg.isIndexCleanup())
                    throw new GridException("Parameter 'indexCleanup' cannot be 'false' if 'indexPath' " +
                        "is not specified for cache: " + cfg.getName());

                if (!F.isEmpty(U.getGridGainHome())) {
                    File root = new File(U.getGridGainHome(), DFLT_IDX_PARENT_FOLDER_NAME);

                    // Create relative by default.
                    folder = new File(root, dbName);
                }
                else {
                    // Use temporary folder when GRIDGAIN_HOME isn't set.
                    String tmpDir = tmpDir();

                    File root = new File(tmpDir, DFLT_IDX_PARENT_FOLDER_NAME);

                    folder = new File(root, dbName);
                }
            }

            if (folder != null && !folder.exists()) {
                if (folder.isFile())
                    throw new GridException("Failed to create cache index directory " +
                        "(file with same name already exists): " + folder.getAbsolutePath());

                if (!folder.mkdirs() && !folder.exists())
                    throw new GridException("Cache index directory does not exist and could not be created: " +
                        folder);

                if (log.isDebugEnabled())
                    log.debug("Created cache index folder: " + folder.getAbsolutePath());
            }

            assert folder != null;

            dbUrl = "jdbc:h2:" + folder.getAbsolutePath() + '/' + DFLT_DB_NAME + opt;
        }

        try {
            Class.forName("org.h2.Driver");
        }
        catch (ClassNotFoundException e) {
            throw new GridException("Failed to find org.h2.Driver class", e);
        }

        analyzeThread = new GridThread(new AnalyzeWorker());

        analyzeThread.setPriority(Thread.NORM_PRIORITY - 1);

        analyzeThread.start();

        if (log.isDebugEnabled())
            log.debug("Cache query index started [grid=" + cacheCtx.gridName() + "]");
    }

    /**
     *
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"}) void stop() {
        if (log.isDebugEnabled())
            log.debug("Stopping cache query index...");

        // Acquire busy lock so that any new activity could not be started.
        busyLock.writeLock().lock();

        U.interrupt(analyzeThread);

        U.join(analyzeThread, log);

        writeLock();

        try {
            boolean wasIdx = false;

            for (QueryType type : clsMap.values())
                if (type.indexed()) {
                    wasIdx = true;

                    break;
                }

            if (wasIdx) {
                try {
                    Connection conn = connectionForThread(false);

                    if (conn != null) {
                        Statement stmt = null;

                        try {
                            stmt = conn.createStatement();

                            stmt.execute("shutdown");
                        }
                        catch (SQLException e) {
                            U.error(log, "Failed to shutdown database.", e);
                        }
                        finally {
                            U.close(stmt, log);
                        }
                    }
                }
                catch (GridException e) {
                    U.error(log, "Failed to receive connection to shutdown database.", e);
                }

                for (SqlStatementCache cache : stmtCaches)
                    cache.close(log);

                stmtCaches.clear();

                for (Connection conn : connections)
                    U.close(conn, log);

                connections.clear();
            }

            tables.clear();

            cleanupIndex();
        }
        finally {
            writeUnlock();
        }

        if (log.isDebugEnabled())
            log.debug("Cache query index stopped [grid=" + cacheCtx.gridName() + "]");
    }

    /**
     * Acquire schema "write" lock.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    private void writeLock() {
        schemaLock.writeLock().lock();
    }

    /**
     * Release schema "write" lock.
     */
    private void writeUnlock() {
        schemaLock.writeLock().unlock();
    }

    /**
     * Acquire schema "read" lock.
     */
    @SuppressWarnings({"LockAcquiredButNotSafelyReleased"})
    private void readLock() {
        schemaLock.readLock().lock();
    }

    /**
     * Release schema "read" lock.
     */
    private void readUnlock() {
        schemaLock.readLock().unlock();
    }

    /**
     * Gets DB connection.
     *
     * @return DB connection.
     * @throws GridException In case of error.
     */
    private Connection connectionForThread() throws GridException {
        return connectionForThread(true);
    }

    /**
     * Gets DB connection.
     *
     * @param setSchema Whether to set schema for connection or not.
     * @return DB connection.
     * @throws GridException In case of error.
     */
    private Connection connectionForThread(boolean setSchema) throws GridException {
        ConnectionWrapper c = connectionCache.get();

        if (c == null)
            throw new GridException("Failed to get DB connection for thread (check log for details).");

        if (setSchema && !c.schemaSet()) {
            Statement stmt = null;

            try {
                stmt = c.connection().createStatement();

                stmt.executeUpdate("SET SCHEMA \"" + schema + '"');

                if (log.isDebugEnabled())
                    log.debug("Initialized H2 schema for queries on space: " + schema);

                c.schemaSet(true);
            }
            catch (SQLException e) {
                throw new GridException("Failed to set schema for DB connection for thread [schemaCreated=" +
                    schemaCreated.get() + "]", e);
            }
            finally {
                U.close(stmt, log);
            }
        }

        return c.connection();
    }

    /**
     * Creates db schema if it has not been created yet.
     *
     * @throws GridException If failed to create db schema.
     */
    private void createSchemaIfAbsent() throws GridException {
        if (schemaCreated.compareAndSet(false, true)) {
            Statement stmt = null;

            try {
                Connection c = connectionForThread(false);

                stmt = c.createStatement();

                stmt.executeUpdate("CREATE SCHEMA IF NOT EXISTS \"" + schema + '"');

                if (log.isDebugEnabled())
                    log.debug("Created H2 schema for index database: " + schema);
            }
            catch (SQLException e) {
                onSqlException();

                throw new GridException("Failed to create H2 schema for index database for space: " + schema, e);
            }
            finally {
                schemaLatch.countDown();

                U.close(stmt, log);
            }
        }
        else
            try {
                schemaLatch.await();
            }
            catch (InterruptedException ignored) {
                if (log.isDebugEnabled())
                    log.debug("Got interrupted exception while waiting on schema latch.");
            }
    }

    /**
     * @return Temporary directory.
     * @throws GridException If could not find system property.
     */
    private String tmpDir() throws GridException {
        String tmpDir = System.getProperty("java.io.tmpdir");

        if (tmpDir == null)
            throw new GridException("System temporary folder is not found for 'java.io.tmpdir' " +
                "system property (either set GRIDGAIN_HOME or define temporary directory system property).");

        return tmpDir;
    }

    /**
     *
     */
    private void cleanupIndex() {
        if (cacheCtx.config().isIndexCleanup() && folder != null && folder.exists()) {
            if (U.delete(folder)) {
                if (log.isDebugEnabled())
                    log.debug("Deleted cache query index folder: " + folder);
            }
            else
                U.warn(log, "Failed to delete cache query index folder: " + folder);
        }
    }

    /**
     * Removes entry with specified key from any tables (if exist).
     *
     * @param key Key.
     * @param keyBytes Byte array with key data.
     * @param valCls Value class.
     * @throws GridException In case of error.
     */
    private void removeKey(K key, @Nullable byte[] keyBytes, Class<?> valCls) throws GridException {
        try {
            if (tables.size() > 1)
                for (TableDescriptor table : tables)
                    if (!table.type().valueClass().equals(valCls) && table.type().keyClass().equals(key.getClass())) {
                        PreparedStatement stmt = statementCacheForThread().removeStatement(valCls);

                        bindKey(stmt, 1, key, keyBytes, table);

                        stmt.executeUpdate();
                    }
        }
        catch (SQLException e) {
            throw new GridException("Failed to remove key: " + key, e);
        }
    }

    /**
     * Binds key as a parameter to prepared statement.
     *
     * @param stmt Statement.
     * @param idx Index in prepared statement.
     * @param key Key.
     * @param keyBytes Byte array with key data.
     * @param table Table descriptor.
     * @throws SQLException In case of SQL error.
     * @throws GridException In case of marshaller error.
     */
    private void bindKey(PreparedStatement stmt, int idx, K key, @Nullable byte[] keyBytes, TableDescriptor table)
        throws SQLException, GridException {
        if (cacheCtx.config().isIndexFixedTyping() && DBTypeEnum.fromClass(key.getClass()) != DBTypeEnum.BINARY) {
            if (DBTypeEnum.fromClass(table.type().keyClass()) != DBTypeEnum.fromClass(key.getClass()))
                throw new GridException("Failed to put key to database, same key type is set in configuration but " +
                    "you are trying to put key of different type [registeredType=" +
                    table.type().keyClass().getName() + ", keyType=" + key.getClass().getName() +
                    ", valeType=" + table.type().valueClass().getName() + ']');

            bindObject(stmt, idx, key);
        }
        else {
            byte[] buf;

            if (keyBytes != null)
                buf = keyBytes;
            else {
                GridByteArrayOutputStream out = streamCache.get();

                try {
                    U.marshal(cacheCtx.marshaller(), key, out);

                    buf = out.toByteArray();
                }
                catch (GridException e) {
                    throw new GridException("Failed to marshal key [key=" + key + ", table=" + table + ']', e);
                }
            }

            stmt.setBytes(idx, buf);
        }
    }

    /**
     * Binds additional fields to SQL statement.
     *
     * @param stmt SQL statement.
     * @param startIdx Start index in prepared statement.
     * @param val Value.
     * @param valCls Value class.
     * @throws SQLException In case of SQL error.
     * @throws GridException In case of errors.
     */
    private void bindFields(PreparedStatement stmt, int startIdx, V val, Class<?> valCls)
        throws SQLException, GridException {
        int cnt = startIdx;

        TableDescriptor table = tableDescriptor(valCls);

        assert table != null;

        for (QueryTypeProperty prop : table.type().properties()) {
            Object obj = propertyValue(prop, val);

            if (obj == null) {
                stmt.setNull(cnt, DBTypeEnum.fromClass(prop.type()).dBType());

                cnt++;
            }
            else {
                if (DBTypeEnum.fromClass(obj.getClass()) == DBTypeEnum.BINARY)
                    bindField(stmt, cnt++, obj);
                else
                    bindObject(stmt, cnt++, obj);
            }
        }
    }

    /**
     * Binds field to prepared statement.
     *
     * @param stmt SQL statement.
     * @param idx Index.
     * @param field Value to store.
     * @throws GridException In case of errors.
     */
    private void bindField(PreparedStatement stmt, int idx, Object field) throws GridException {
        try {
            if (field == null)
                stmt.setNull(idx, Types.BINARY);
            else {
                GridByteArrayOutputStream out = streamCache.get();

                U.marshal(cacheCtx.marshaller(), field, out);

                // Performance optimization. If internal array is more than 50% full,
                // then use it directly, otherwise, create a copy of exact size.
                byte[] buf = out.size() > out.getInternalArray().length / 2 ? out.getInternalArray() : out.toByteArray();

                stmt.setBytes(idx, buf);
            }
        }
        catch (Throwable e) {
            throw new GridException("Failed to bind field to prepared statement: " + field, e);
        }
    }

    /**
     * Binds object to prepared statement.
     *
     * @param stmt SQL statement.
     * @param idx Index.
     * @param obj Value to store.
     * @throws SQLException In case of errors.
     */
    private void bindObject(PreparedStatement stmt, int idx, Object obj) throws SQLException {
        stmt.setObject(idx, obj);
    }

    /**
     * Binds object to prepared statement.
     *
     * @param stmt SQL statement.
     * @param idx Index.
     * @param val Value to store.
     * @param table Table descriptor.
     * @throws GridException In case of errors.
     */
    private void bindValue(PreparedStatement stmt, int idx, V val, TableDescriptor table) throws GridException {
        assert stmt != null;
        assert idx > 0;
        assert val != null;
        assert table != null;

        try {
            if (DBTypeEnum.fromClass(val.getClass()) == DBTypeEnum.BINARY) {
                GridByteArrayOutputStream out = streamCache.get();

                try {
                    U.marshal(cacheCtx.marshaller(), val, out);

                    byte[] x = out.toByteArray();

                    stmt.setBytes(idx, x);
                }
                catch (GridException e) {
                    throw new GridException("Failed to marshal value [val=" + val + ", table=" + table + ']', e);
                }
            }
            else
                bindObject(stmt, idx, val);

            idx++;

            // Set value as string.
            if (table.valueH2TextIndex() || table.valueLuceneIndex())
                stmt.setString(idx, val.toString());
            else
                stmt.setNull(idx, Types.BINARY);
        }
        catch (SQLException e) {
            throw new GridException("Failed to bind value to prepared statement: " + val, e);
        }
    }

    /**
     * Binds key class loader id to SQL statement.
     *
     * @param stmt SQL statement.
     * @param idx Index in prepared statement.
     * @param key Key.
     * @param table Table descriptor.
     * @throws SQLException In case of SQL error.
     */
    private void bindKeyClassLoader(PreparedStatement stmt, int idx, K key, TableDescriptor table) throws SQLException {
        assert stmt != null;
        assert key != null;
        assert table != null;

        UUID clsLdrId = cacheCtx.deploy().getClassLoaderId(key.getClass().getClassLoader());

        if (clsLdrId != null)
            stmt.setString(idx, clsLdrId.toString());
        else
            stmt.setNull(idx, Types.VARCHAR);
    }


    /**
     * Binds cache version object to SQL statement.
     *
     * @param stmt SQL statement.
     * @param idx Index in prepared statement.
     * @param ver Version.
     * @param table Table descriptor.
     * @throws SQLException In case of SQL error.
     */
    private void bindVersion(PreparedStatement stmt, int idx, GridCacheVersion ver, TableDescriptor table)
        throws SQLException {
        assert stmt != null;
        assert ver != null;
        assert table != null;

        stmt.setString(idx, ver.id().toString() + ver.order());
    }

    /**
     * Gets sql statement cache that holds all prepared statements for the current
     * thread (creates it if necessary).
     *
     * @return Sql statement cache for the current thread.
     */
    private SqlStatementCache statementCacheForThread() {
        SqlStatementCache cache = stmtCache.get();

        if (cache == null) {
            stmtCache.set(cache = new SqlStatementCache());

            stmtCaches.add(cache);
        }

        return cache;
    }

    /**
     *
     */
    private void onSqlException() {
        Connection conn = connectionCache.get().connection();

        connectionCache.set(null);

        if (conn != null) {
            connections.remove(conn);

            // Reset connection to receive new one at next call.
            U.close(conn, log);
        }
    }

    /**
     * Writes value to swap space.
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

        if (log.isDebugEnabled())
            log.debug("Storing key to cache query index: [key=" + key + ", value=" + val + "]");

        QueryType queryType = checkRegister(key.getClass(), val.getClass());

        if (!queryType.indexed())
            return;

        Connection conn = connectionForThread();

        readLock();

        try {
            if (!clsMap.containsKey(val.getClass()))
                // Class was concurrently undeployed.
                return;

            conn.setAutoCommit(false);

            if (!cacheCtx.config().isIndexFixedTyping())
                removeKey(key, keyBytes, val.getClass());

            TableDescriptor table = tableDescriptor(val.getClass());

            if (table == null)
                throw new GridException("Found not registered class: " + val.getClass());

            PreparedStatement stmt = statementCacheForThread().writeStatement(val.getClass());

            bindKey(stmt, 1, key, keyBytes, table);

            bindKeyClassLoader(stmt, 2, key, table);

            bindVersion(stmt, 3, ver, table);

            bindValue(stmt, 4, val, table);

            bindFields(stmt, 6, val, val.getClass());

            int updated = stmt.executeUpdate();

            if (log.isDebugEnabled())
                log.debug("Updated rows in query index: " + updated);

            conn.commit();
        }
        catch (SQLException e) {
            U.rollbackConnection(conn, log);

            onSqlException();

            throw new GridException("Failed to put value to DB table [key=" + key + ", spaceName=" + schema + ']', e);
        }
        finally {
            readUnlock();

            try {
                if (!conn.isClosed())
                    conn.setAutoCommit(true);
            }
            catch (SQLException e) {
                U.error(log, "Failed to set auto-commit connection property.", e);
            }
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

        if (log.isDebugEnabled())
            log.debug("Removing key from cache query index: " + key);

        readLock();

        boolean res = false;

        try {
            for (TableDescriptor table : tables)
                if (table.type().keyClass().equals(key.getClass())) {
                    PreparedStatement stmt = statementCacheForThread().removeStatement(table.type().valueClass());

                    bindKey(stmt, 1, key, keyBytes, table);

                    if (stmt.executeUpdate() > 0)
                        res = true;
                }
        }
        catch (SQLException e) {
            onSqlException();

            throw new GridException("Failed to remove key [key=" + key + ", spaceName=" + schema + ']', e);
        }
        finally {
            readUnlock();
        }

        return res;
    }

    /**
     * Removes index tables for all classes belonging to given class loader.
     *
     * @param ldr Class loader to undeploy.
     */
    public void onUndeploy(ClassLoader ldr) {
        assert ldr != null;

        if (log.isDebugEnabled())
            log.debug("Removing query indexes for class loader: " + ldr);

        writeLock();

        try {
            for (Class<?> cls : clsMap.keySet())
                if (ldr.equals(cls.getClassLoader()))
                    try {
                        TableDescriptor table = tableDescriptor(cls);

                        if (table != null)
                            removeTable(table);
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to remove cache query index table for class: " + cls, e);
                    }
        }
        finally {
            writeUnlock();
        }
    }

    /**
     * Drops table form h2 database and clear all related indexes (h2 text, lucene).
     *
     * @param table Table to unregister.
     * @throws GridException If failed to unregister.
     */
    private void removeTable(TableDescriptor table) throws GridException {
        assert table != null;

        if (log.isDebugEnabled())
            log.debug("Removing query index table: " + table.fullTableName());

        Class<?> valCls = table.type().valueClass();

        Connection c = connectionForThread(false);

        Statement stmt = null;

        try {
            FullText.dropIndex(c, schema, table.tableName().toUpperCase());

            // NOTE: there is no method dropIndex() for lucene engine correctly working.
            // So we have to drop all lucene index.
            FullTextLucene.dropAll(c);

            stmt = c.createStatement();

            String sql = "DROP TABLE IF EXISTS " + table.fullTableName();

            if (log.isDebugEnabled())
                log.debug("Dropping database index table with SQL: " + sql);

            stmt.executeUpdate(sql);
        }
        catch (SQLException e) {
            onSqlException();

            throw new GridException("Failed to drop database index table: [class=" + valCls +
                ", table=" + table.fullTableName() + "]", e);
        }
        finally {
            U.close(stmt, log);
        }

        tables.remove(table);

        clsMap.remove(valCls);

        for (SqlStatementCache cache : stmtCaches)
            cache.unregister(valCls);
    }

    /**
     * @return Collection of currently registered table names.
     */
    Collection<String> indexTables() {
        return F.viewReadOnly(tables, new C1<TableDescriptor, String>() {
            @Override public String apply(TableDescriptor table) {
                return table.fullTableName();
            }
        });
    }

    /**
     * Performs full text query.
     *
     * @param qry Query.
     * @param loc Local query or not.
     * @return Iterator of found values.
     * @throws GridException In case of error.
     */
    Iterator<GridCacheQueryIndexRow<K, V>> queryText(GridCacheQueryBaseAdapter qry, boolean loc) throws
        GridException {
        createSchemaIfAbsent();

        Connection conn = connectionForThread(false);

        ClassLoader ldr = loc ? cacheCtx.deploy().localLoader() : cacheCtx.deploy().globalLoader();

        readLock();

        try {
            TableDescriptor table;

            try {
                table = tableDescriptor(qry.queryClass(ldr));
            }
            catch (ClassNotFoundException e) {
                throw new GridException("Failed to load class: " + qry.className(), e);
            }

            if (table == null)
                return Collections.<GridCacheQueryIndexRow<K, V>>emptyList().iterator();

            Statement stmt = null;

            try {
                String func = qry.type() == H2TEXT ? "FT_SEARCH_DATA" : "FTL_SEARCH_DATA";

                String fullTabName = table.fullTableName().toUpperCase();

                String tabName = table.tableName().toUpperCase();

                String sql = "SELECT " +
                    fullTabName + '.' + KEY_FIELD_NAME + ',' +
                    fullTabName + '.' + KEY_CLS_LDR_FIELD_NAME + ',' +
                    fullTabName + '.' + VERSION_FIELD_NAME + ',' +
                    fullTabName + '.' + VALUE_FIELD_NAME +
                    " FROM " + func + "('" + qry.clause() + "', 0, 0) FT," + fullTabName +
                    " WHERE FT.table='" + tabName + "' AND " + fullTabName + '.' + KEY_FIELD_NAME + "=FT.KEYS[0]";

                stmt = conn.createStatement();

                // Need to update "schemaSet" flag to enforce schema to be set during the next call.
                ConnectionWrapper wrap = connectionCache.get();

                if (wrap != null)
                    wrap.schemaSet(false);

                // NOTE: It's required to set PUBLIC schema so that H2 can find its full text search functions.
                stmt.executeUpdate("SET SCHEMA \"PUBLIC\"");

                ResultSet rs = stmt.executeQuery(sql);

                return new ResultSetIterator(rs, stmt);
            }
            catch (SQLException e) {
                // NOTE: The statement must not be closed in block "finally",
                // otherwise corresponding result set will be closed too prior
                // to sending results to the user.
                U.close(stmt, log);

                onSqlException();

                throw new GridException("Failed to perform full text search: " + qry.clause(), e);
            }
        }
        finally {
            readUnlock();
        }
    }

    /**
     * Performs sql query.
     *
     * @param query Query.
     * @param loc Local query or not.
     * @return Iterator of found values.
     * @throws GridException In case of error.
     */
    Iterator<GridCacheQueryIndexRow<K, V>> querySql(GridCacheQueryBaseAdapter query, boolean loc) throws GridException {
        createSchemaIfAbsent();

        ClassLoader ldr = cacheCtx.deploy().isGlobalLoader() || !loc ?
            cacheCtx.deploy().globalLoader() : cacheCtx.deploy().localLoader();

        Class<?> valCls;

        try {
            valCls = query.queryClass(ldr);
        }
        catch (ClassNotFoundException e) {
            throw new GridException("Failed to load class: " + query.className(), e);
        }

        TableDescriptor table = tableDescriptor(valCls);

        if (table == null)
            return Collections.<GridCacheQueryIndexRow<K, V>>emptyList().iterator();

        Connection conn = connectionForThread();

        PreparedStatement stmt = query.preparedStatementForThread();

        try {
            if (stmt == null) {
                stmt = prepareStatement(conn, query.clause(), table, /* limit */ 0, /* offset */ 0);

                query.preparedStatementForThread(stmt);
            }
        }
        catch (SQLException e) {
            throw new GridException("Failed to parse query: " + query.clause(), e);
        }

        try {
            if (!F.isEmpty(query.arguments())) {
                int idx = 1;

                for (Object arg : query.arguments()) {
                    if (!arg.getClass().isArray() && DBTypeEnum.fromClass(arg.getClass()) == DBTypeEnum.BINARY) {
                        GridByteArrayOutputStream out = streamCache.get();

                        try {
                            U.marshal(cacheCtx.marshaller(), arg, out);
                        }
                        catch (GridException e) {
                            throw new GridException("Failed to marshal parameter:" + arg, e);
                        }

                        stmt.setBytes(idx++, out.toByteArray());
                    }
                    else
                        bindObject(stmt, idx++, arg);
                }
            }

            ResultSet rs = stmt.executeQuery();

            return new ResultSetIterator(rs, null);
        }
        catch (SQLException e) {
            onSqlException();

            throw new GridException("Failed to query entries: " + query.clause(), e);
        }
    }

    /**
     * Prepares statement for query.
     *
     * @param conn Connection.
     * @param query Query string.
     * @param table Table to use.
     * @param limit Limit.
     * @param offset Offset.
     * @return Prepared statement.
     * @throws GridException In case of error.
     * @throws SQLException In case of error.
     */
    private PreparedStatement prepareStatement(Connection conn, String query, TableDescriptor table, int limit,
        int offset) throws GridException, SQLException {
        boolean needSelect = true;

        String str = query.trim().toUpperCase();

        if (!str.startsWith("FROM")) {
            if (str.startsWith("SELECT")) {
                StringTokenizer st = new StringTokenizer(str, " ");

                String errMsg = "Wrong query format, query must start with 'select * from' " +
                    "or 'from' or without such keywords.";

                if (st.countTokens() > 3) {
                    st.nextToken();
                    String wildcard = st.nextToken();
                    String from = st.nextToken();

                    if (!"*".equals(wildcard) || !"FROM".equals(from))
                        throw new GridException(errMsg);

                    needSelect = false;
                }
                else
                    throw new GridException(errMsg);
            }
            else
                query = "FROM " + table.fullTableName() + " WHERE " + query;
        }

        String ptrn = "SELECT {0}." + KEY_FIELD_NAME + ", {0}." + KEY_CLS_LDR_FIELD_NAME +
            ", {0}." + VERSION_FIELD_NAME + ", {0}." + VALUE_FIELD_NAME;

        String sql = needSelect ? MessageFormat.format(ptrn, table.fullTableName()) + ' ' + query : query;

        if (limit > 0) {
            if (offset < 0)
                throw new GridException("Invalid offset value (must be greater or equal to zero): " + offset);

            sql += " LIMIT " + limit + " OFFSET " + offset;
        }

        return conn.prepareStatement(sql);
    }

    /**
     * Check if this value class must be registered first in SPI and register it if necessary.
     *
     * @param keyCls Key class.
     * @param valCls Value class.
     * @return Query type.
     * @throws GridException in case of registration error.
     */
    private QueryType checkRegister(Class<?> keyCls, Class<?> valCls) throws GridException {
        assert keyCls != null;
        assert valCls != null;

        writeLock();

        try {
            QueryType type = clsMap.get(valCls);

            if (type == null) {
                type = new QueryType(keyCls, valCls, cacheCtx);

                processAnnotationsInClass(valCls, type, null);

                if (type.indexed())
                    registerType(type);

                clsMap.put(valCls, type);
            }

            return type;
        }
        finally {
            writeUnlock();
        }
    }

    /**
     * Registers new class description.
     *
     * @param type Class description.
     * @throws GridException In case of error.
     */
    private void registerType(QueryType type) throws GridException {
        validateQueryType(type);

        for (TableDescriptor table : tables)
            // Need to compare class names rather than classes to define
            // whether a class was previously undeployed.
            if (table.type().valueClass().getClass().getName().equals(type.valueClass().getName()))
                throw new GridException("Failed to register type in query index because" +
                    " class is already registered (most likely that class with the same name" +
                    " was not properly undeployed): " + type);

        TableDescriptor table = new TableDescriptor(type);

        createSchemaIfAbsent();

        Statement stmt = null;

        try {
            Connection conn = connectionForThread(false);

            stmt = conn.createStatement();

            createTable(table, stmt);

            int cnt = createSimpleIndexes(table, stmt);

            createGroupIndexes(table, stmt, cnt);

            createFullTextIndexes(table, conn);

            tables.add(table);
        }
        catch (SQLException e) {
            onSqlException();

            throw new GridException("Failed to register query type: " + type, e);
        }
        finally {
            U.close(stmt, log);
        }
    }

    /**
     * Validates properties described by query types.
     *
     * @param type Query type.
     * @throws GridException If validation failed.
     */
    private void validateQueryType(QueryType type) throws GridException {
        assert type != null;

        Collection<String> names = new HashSet<String>();

        String ptrn = "Name ''{0}'' is reserved and cannot be used as a field name [class=" +
            type.valueClass().getName() + "]";

        for (QueryTypeProperty prop : type.properties()) {
            String name = prop.name();

            if (name.equals(KEY_FIELD_NAME) ||
                name.equals(VALUE_FIELD_NAME) ||
                name.equals(VALUE_STRING_FIELD_NAME) ||
                name.equals(VERSION_FIELD_NAME) ||
                name.equals(KEY_CLS_LDR_FIELD_NAME))
                throw new GridException(MessageFormat.format(ptrn, name));

            if (names.contains(name))
                throw new GridException("Found duplicated properties with the same name:" + name);
            else
                names.add(name);
        }
    }

    /**
     * Create db table by using given table descriptor.
     *
     * @param table Table descriptor.
     * @param stmt Sql statement (should not be closed after sql execution).
     * @throws SQLException If failed to create db table.
     */
    private void createTable(TableDescriptor table, Statement stmt) throws SQLException {
        assert table != null;

        String keyType = cacheCtx.config().isIndexFixedTyping() ? dbTypeFromClass(table.type().keyClass()) : "BINARY";
        DBTypeEnum valType = DBTypeEnum.fromClass(table.type().valueClass());

        String valTypeStr = valType == DBTypeEnum.BINARY ? "BINARY" : valType.dBTypeAsString();

        SB sql = new SB();

        sql.a("CREATE TABLE IF NOT EXISTS ").a(table.fullTableName()).a(" (").a(KEY_FIELD_NAME);
        sql
            .a(' ').a(keyType).a(" PRIMARY KEY,")
            .a(KEY_CLS_LDR_FIELD_NAME).a(' ').a("VARCHAR,")
            .a(VERSION_FIELD_NAME).a(' ').a("VARCHAR,")
            .a(VALUE_FIELD_NAME).a(' ').a(valTypeStr).a(',')
            .a(VALUE_STRING_FIELD_NAME).a(' ').a("VARCHAR(").a(Integer.MAX_VALUE).a(")");

        for (QueryTypeProperty prop : table.type().properties())
            sql.a(',').a(prop.name()).a(' ').a(dbTypeFromClass(prop.type()));

        sql.a(')');

        if (log.isDebugEnabled())
            log.debug("Creating DB table with SQL: " + sql);

        stmt.executeUpdate(sql.toString());
    }

    /**
     * Creates simple (non-group) db indexes using given table descriptor.
     *
     * @param table Table descriptor.
     * @param stmt Sql statement (should not be closed after sql execution).
     * @return Number of indexes created by this call.
     * @throws SQLException If failed to create indexes.
     */
    private int createSimpleIndexes(TableDescriptor table, Statement stmt) throws SQLException {
        DBTypeEnum valType = DBTypeEnum.fromClass(table.type().valueClass());

        // Create index for value if it is of primitive type and can be queried.
        if (valType != DBTypeEnum.BINARY) {
            SB sql = new SB();

            sql.a("CREATE INDEX ").a(table.fullTableName()).a("Value");
            sql.a(" ON ").a(table.fullTableName());
            sql.a("( " + VALUE_FIELD_NAME + " )");

            if (log.isDebugEnabled())
                log.debug("Creating index with SQL: " + sql);

            stmt.executeUpdate(sql.toString());
        }

        int cnt = 0;

        for (QueryTypeProperty prop : table.type().properties())
            if (prop.index() && F.isEmpty(prop.groups())) {
                SB sql = new SB();

                sql.a("CREATE ").a(prop.unique() ? "UNIQUE" : "");
                sql.a(" INDEX ").a(table.fullTableName()).a(cnt++);
                sql.a(" ON ").a(table.fullTableName());
                sql.a('(');
                sql.a(prop.name());
                sql.a(')');

                String idxSql = sql.toString();

                if (log.isDebugEnabled())
                    log.debug("Creating index with SQL: " + idxSql);

                stmt.executeUpdate(idxSql);
            }

        return cnt;
    }

    /**
     * Creates group db indexes using given table descriptor.
     *
     * @param table Table descriptor.
     * @param stmt Sql statement (should not be closed after sql execution).
     * @param cnt Naming counter to start with.
     * @throws SQLException If failed to create indexes.
     */
    private void createGroupIndexes(TableDescriptor table, Statement stmt, int cnt) throws SQLException {
        Map<String, IndexGroup> grpMap = new HashMap<String, IndexGroup>();

        for (QueryTypeProperty prop : table.type().properties())
            if (prop.index() && !F.isEmpty(prop.groups())) {
                for (String grpName : prop.groups()) {
                    IndexGroup grp = grpMap.get(grpName);

                    if (grp == null) {
                        grp = new IndexGroup();

                        grpMap.put(grpName, grp);
                    }

                    grp.columns().add(prop.name());

                    if (prop.unique())
                        grp.unique(true);
                }
            }

        for (IndexGroup grp : grpMap.values()) {
            SB sql = new SB();

            sql.a("CREATE ").a(grp.unique() ? "UNIQUE" : "");
            sql.a(" INDEX ").a(table.fullTableName()).a(cnt++);
            sql.a(" ON ").a(table.fullTableName());
            sql.a('(');

            int colCnt = 0;

            for (String colName : grp.columns()) {
                sql.a(colName);

                if (colCnt++ < grp.columns().size() - 1)
                    sql.a(',');
            }

            sql.a(')');

            String idxSql = sql.toString();

            if (log.isDebugEnabled())
                log.debug("Creating index with SQL: " + idxSql);

            stmt.executeUpdate(idxSql);
        }
    }

    /**
     * Create full text search indexes for the given table.
     *
     * @param table Table descriptor.
     * @param conn Db connection.
     * @throws SQLException If failed to create full text indexes.
     */
    private void createFullTextIndexes(TableDescriptor table, Connection conn) throws SQLException {
        assert table != null;

        SB h2TxtCols = new SB();
        SB lucTxtCols = new SB();

        if (table.valueH2TextIndex())
            h2TxtCols.a(VALUE_STRING_FIELD_NAME.toUpperCase());

        if (table.valueLuceneIndex())
            lucTxtCols.a(VALUE_STRING_FIELD_NAME.toUpperCase());

        for (QueryTypeProperty prop : table.type().properties()) {
            if (U.containsObjectArray(prop.indexTypes(), H2TEXT)) {
                if (h2TxtCols.length() != 0)
                    h2TxtCols.a(',');

                h2TxtCols.a(prop.name().toUpperCase());
            }

            if (U.containsObjectArray(prop.indexTypes(), LUCENE)) {
                if (lucTxtCols.length() != 0)
                    lucTxtCols.a(',');

                lucTxtCols.a(prop.name().toUpperCase());
            }
        }

        if (h2TxtCols.length() != 0)
            FullText.createIndex(conn, schema, table.tableName().toUpperCase(), h2TxtCols.toString());

        if (lucTxtCols.length() != 0)
            FullTextLucene.createIndex(conn, schema, table.tableName().toUpperCase(), lucTxtCols.toString());
    }

    /**
     * Gets corresponding DB type from java class.
     *
     * @param cls Java class.
     * @return DB type name.
     */
    private String dbTypeFromClass(Class<?> cls) {
        return DBTypeEnum.fromClass(cls).dBTypeAsString();
    }

    /**
     * Process annotations for class.
     *
     * @param valCls Value class.
     * @param queryType Class description.
     * @param parent Parent in case of embeddable.
     * @throws GridException In case of error.
     */
    private void processAnnotationsInClass(Class<?> valCls, QueryType queryType, @Nullable Member parent) throws
        GridException {
        if (parent == null) { // Check class annotation at top level only.
            GridCacheQueryH2TextField txtAnnCls = valCls.getAnnotation(GridCacheQueryH2TextField.class);

            if (txtAnnCls != null)
                queryType.valueH2TextIndex(true);

            GridCacheQueryLuceneField lucAnnCls = valCls.getAnnotation(GridCacheQueryLuceneField.class);

            if (lucAnnCls != null)
                queryType.valueLuceneIndex(true);
        }

        for (Class<?> c = valCls; !c.equals(Object.class); c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                GridCacheQuerySqlField sqlAnn = field.getAnnotation(GridCacheQuerySqlField.class);
                GridCacheQueryH2TextField txtAnn = field.getAnnotation(GridCacheQueryH2TextField.class);
                GridCacheQueryLuceneField lucAnn = field.getAnnotation(GridCacheQueryLuceneField.class);

                if (sqlAnn != null || txtAnn != null || lucAnn != null) {
                    QueryTypeProperty prop = new QueryTypeProperty(field);

                    prop.parent(parent);

                    queryType.addProperty(prop);

                    processAnnotation(sqlAnn, txtAnn, lucAnn, field, field.getType(), prop, queryType, parent != null);
                }
            }

            for (Method mtd : c.getDeclaredMethods()) {
                GridCacheQuerySqlField sqlAnn = mtd.getAnnotation(GridCacheQuerySqlField.class);
                GridCacheQueryH2TextField txtAnn = mtd.getAnnotation(GridCacheQueryH2TextField.class);
                GridCacheQueryLuceneField lucAnn = mtd.getAnnotation(GridCacheQueryLuceneField.class);

                if (sqlAnn != null || txtAnn != null || lucAnn != null) {
                    if (mtd.getParameterTypes().length != 0)
                        throw new GridException("Getter with GridCacheQuerySqlField " +
                            "annotation cannot have parameters: " + mtd);

                    QueryTypeProperty prop = new QueryTypeProperty(mtd);

                    prop.parent(parent);

                    queryType.addProperty(prop);

                    processAnnotation(sqlAnn, txtAnn, lucAnn, mtd, mtd.getReturnType(), prop, queryType, parent != null);
                }
            }
        }

        if (log.isDebugEnabled())
            log.debug("Registered query class annotations: " + valCls);
    }

    /**
     * Processes annotation at field or method.
     *
     * @param sqlAnn SQL annotation, can be {@code null}.
     * @param txtAnn H2 text annotation, can be {@code null}.
     * @param lucAnn Lucene annotation, can be {@code null}.
     * @param current Field or Method in case of embeddable elements, otherwise {@code null}
     * @param type Class of field or return type for method.
     * @param prop Current property.
     * @param cls Class description.
     * @param embeddable {@code true} if embeddable annotation is processed.
     * @throws GridException In case of error.
     */
    private void processAnnotation(GridCacheQuerySqlField sqlAnn, GridCacheQueryH2TextField txtAnn,
        GridCacheQueryLuceneField lucAnn, Member current, Class<?> type,
        QueryTypeProperty prop, QueryType cls, boolean embeddable) throws GridException {
        List<GridCacheQueryType> types = new ArrayList<GridCacheQueryType>();

        if (sqlAnn != null) {
            if (!sqlAnn.index() && sqlAnn.unique())
                throw new GridException("Wrong GridCacheQuerySqlField annotation value " +
                    "('unique' can not be true if 'index' is false): " + sqlAnn);

            if (!embeddable && isComplexType(type))
                processAnnotationsInClass(type, cls, current);

            if (sqlAnn.name().length() > 0)
                prop.name(sqlAnn.name());

            if (sqlAnn.index()) {
                prop.unique(sqlAnn.unique());

                if (sqlAnn.group().length > 0)
                    prop.groups(Arrays.asList(sqlAnn.group()));
            }

            types.add(SQL);
        }

        if (txtAnn != null)
            types.add(H2TEXT);

        if (lucAnn != null)
            types.add(LUCENE);

        prop.indexTypes(types.toArray(new GridCacheQueryType[types.size()]));
    }

    /**
     * Returns {@code true} if class is complex, so we can check annotations inside it too.
     *
     * @param cls Class to check.
     * @return {@code true} if class is complex, otherwise {@code false}.
     */
    private boolean isComplexType(Class<?> cls) {
        return !simpleTypes.contains(cls);
    }

    /**
     * Gets value of field or method of object including parent processing.
     *
     * @param prop Type property to use.
     * @param obj Object that have this field.
     * @return Value of field or object.
     * @throws GridException In case of errors.
     */
    @Nullable private Object propertyValue(QueryTypeProperty prop, Object obj) throws GridException {
        try {
            if (prop.parent() != null) {
                Object parentObj;

                if (prop.parent() instanceof Field) {
                    Field parentField = (Field)prop.parent();

                    parentField.setAccessible(true);

                    parentObj = parentField.get(obj);
                }
                else {
                    Method parentMtd = (Method)prop.parent();

                    parentMtd.setAccessible(true);

                    parentObj = parentMtd.invoke(obj);
                }

                if (parentObj == null)
                    return null;

                return fieldOrMethodValue(prop, parentObj);
            }
            else
                return fieldOrMethodValue(prop, obj);
        }
        catch (IllegalAccessException e) {
            throw new GridException("Failed to get entry field value.", e);
        }
        catch (InvocationTargetException e) {
            throw new GridException("Failed to get entry value from method.", e);
        }
    }

    /**
     * Gets value of field or method of given object.
     *
     * @param prop Type property to use.
     * @param obj Object that have this field.
     * @return Value of field or object.
     * @throws IllegalAccessException In case of field access error.
     * @throws InvocationTargetException In case of method access error.
     */
    private Object fieldOrMethodValue(QueryTypeProperty prop, Object obj)
        throws IllegalAccessException, InvocationTargetException {
        assert obj != null;

        if (prop.member() instanceof Field) {
            Field field = (Field)prop.member();

            field.setAccessible(true);

            return field.get(obj);
        }
        else {
            Method method = (Method)prop.member();

            method.setAccessible(true);

            return method.invoke(obj);
        }
    }

    /**
     * Gets table descriptor by space name and value class.
     *
     * @param valCls Value class.
     * @return Table descriptor or {@code null} if not found.
     */
    @Nullable private TableDescriptor tableDescriptor(Class<?> valCls) {
        for (TableDescriptor table : tables)
            if (table.type().valueClass().equals(valCls))
                return table;

        return null;
    }

    /**
     * Wrapper to store connection and flag is schema set or not.
     */
    private static class ConnectionWrapper {
        /** */
        private Connection conn;

        /** */
        private volatile boolean schemaSet;

        /**
         * @param conn Connection to use.
         */
        ConnectionWrapper(Connection conn) {
            this.conn = conn;
        }

        /**
         * @return {@code True} if schema is set.
         */
        public boolean schemaSet() {
            return schemaSet;
        }

        /**
         * @param schemaSet {@code True} if schema is set. otherwise {@code false}.
         */
        public void schemaSet(boolean schemaSet) {
            this.schemaSet = schemaSet;
        }

        /**
         * @return Connection.
         */
        public Connection connection() {
            return conn;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(ConnectionWrapper.class, this);
        }
    }

    /**
     * Description of type property.
     */
    private static class QueryTypeProperty {
        /** */
        private Member member;

        /** */
        private Member parent;

        /** */
        private boolean index;

        /** */
        private boolean unique;

        /** */
        private List<String> grps;

        /** */
        private String name;

        /** */
        private GridCacheQueryType[] indexTypes;

        /**
         * Constructor.
         *
         * @param member Element.
         */
        QueryTypeProperty(Member member) {
            this.member = member;

            name = member instanceof Method && member.getName().startsWith("get") && member.getName().length() > 3 ?
                member.getName().substring(3) : member.getName();
        }

        /**
         * @param name Property name.
         */
        public void name(String name) {
            this.name = name;
        }

        /**
         * @return Property name.
         */
        public String name() {
            return name;
        }

        /**
         * @return Class member type.
         */
        public Class<?> type() {
            return member instanceof Field ? ((Field)member).getType() : ((Method)member).getReturnType();
        }

        /**
         * @return Class member (field or method).
         */
        public Member member() {
            return member;
        }

        /**
         * @param member Class member (field or method).
         */
        public void member(Member member) {
            this.member = member;
        }

        /**
         * @return Parent field or method if this is embeddable element.
         */
        public Member parent() {
            return parent;
        }

        /**
         * @param parent Parent field or method if this is embeddable element.
         */
        public void parent(Member parent) {
            this.parent = parent;
        }

        /**
         * @return {@code true} if db index must be created.
         */
        public boolean index() {
            return index;
        }

        /**
         * @param index {@code true} if db index must be created.
         */
        public void index(boolean index) {
            this.index = index;
        }

        /**
         * @return {@code true} if index is unique.
         */
        public boolean unique() {
            return unique;
        }

        /**
         * @param unique {@code true} if index is unique.
         */
        public void unique(boolean unique) {
            this.unique = unique;
        }

        /**
         * @return Index groups where this property is used.
         */
        public List<String> groups() {
            return grps;
        }

        /**
         * @param grps index groups where this property is used.
         */
        public void groups(List<String> grps) {
            this.grps = grps;
        }

        /**
         * @return Types of index to support.
         */
        public GridCacheQueryType[] indexTypes() {
            return indexTypes;
        }

        /**
         * @param indexTypes Types of index to support.
         */
        public void indexTypes(GridCacheQueryType[] indexTypes) {
            this.indexTypes = indexTypes;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(QueryTypeProperty.class, this);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"unchecked"})
        @Override public boolean equals(Object obj) {
            return obj == this || (obj instanceof QueryTypeProperty) && name.equals(((QueryTypeProperty)obj).name);
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return name.hashCode();
        }
    }

    /**
     * Type description.
     */
    private static class QueryType {
        /** */
        private Class<?> valCls;

        /** */
        private Class<?> keyCls;

        /** */
        private final List<QueryTypeProperty> props = new LinkedList<QueryTypeProperty>();

        /** */
        private boolean valSqlIndex;

        /** */
        private boolean valH2TextIndex;

        /** */
        private boolean valLuceneIndex;

        /**
         * @param valCls Class.
         * @param ctx Cache context.
         * @param keyCls ?ey Class.
         */
        QueryType(Class<?> keyCls, Class<?> valCls, GridCacheContext ctx) {
            this.keyCls = keyCls;
            this.valCls = valCls;

            DBTypeEnum valType = valCls.isArray() ? DBTypeEnum.fromClass(valCls.getComponentType()) :
                DBTypeEnum.fromClass(valCls);

            // Simple type that DB supports.
            if (valType != DBTypeEnum.BINARY && !F.isEmpty(ctx.config().getAutoIndexQueryTypes())) {
                valSqlIndex = ctx.config().getAutoIndexQueryTypes().contains(SQL);
                valH2TextIndex = ctx.config().getAutoIndexQueryTypes().contains(H2TEXT);
                valLuceneIndex = ctx.config().getAutoIndexQueryTypes().contains(LUCENE);
            }
        }

        /**
         * @return {@code true} If value indexed with SQL query type.
         */
        public boolean valueSqlIndex() {
            return valSqlIndex;
        }

        /**
         * @return {@code true} If value indexed with h2 full text.
         */
        public boolean valueH2TextIndex() {
            return valH2TextIndex;
        }

        /**
         * @param valH2TextIndex {@code true} If value indexed with h2 full text.
         */
        public void valueH2TextIndex(boolean valH2TextIndex) {
            this.valH2TextIndex = valH2TextIndex;
        }

        /**
         * @return {@code true} If value indexed with lucene.
         */
        public boolean valueLuceneIndex() {
            return valLuceneIndex;
        }

        /**
         * @param valLuceneIndex {@code true} If value indexed with lucene.
         */
        public void valueLuceneIndex(boolean valLuceneIndex) {
            this.valLuceneIndex = valLuceneIndex;
        }

        /**
         * Add new property description.
         *
         * @param prop Element (field or method).
         */
        void addProperty(QueryTypeProperty prop) {
            props.add(prop);
        }

        /**
         * @return Key class.
         */
        Class<?> keyClass() {
            return keyCls;
        }

        /**
         * @return Value class.
         */
        Class<?> valueClass() {
            return valCls;
        }

        /**
         * Gets all element descriptions.
         *
         * @return Element descriptions.
         */
        List<QueryTypeProperty> properties() {
            return props;
        }

        /**
         * @return {@code false} if this value type does not have
         *      any indexed fields and it not indexed by value.
         */
        public boolean indexed() {
            return !F.isEmpty(props) || valSqlIndex || valH2TextIndex || valLuceneIndex;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(QueryType.class, this);
        }
    }

    /** Enum that helps to map java types to database types. */
    private enum DBTypeEnum {
        /** */
        INT("INT"),

        /** */
        BOOL("BOOL"),

        /** */
        TINYINT("TINYINT"),

        /** */
        SMALLINT("SMALLINT"),

        /** */
        BIGINT("BIGINT"),

        /** */
        DECIMAL("DECIMAL"),

        /** */
        DOUBLE("DOUBLE"),

        /** */
        REAL("REAL"),

        /** */
        TIME("TIME"),

        /** */
        TIMESTAMP("TIMESTAMP"),

        /** */
        DATE("DATE"),

        /** */
        VARCHAR("VARCHAR"),

        /** */
        CHAR("CHAR"),

        /** */
        BINARY("BINARY");

        /** Map of Class to enum. */
        private static final Map<Class<?>, DBTypeEnum> map = new HashMap<Class<?>, DBTypeEnum>();

        /**
         * Initialize map of DB types.
         */
        static {
            map.put(int.class, INT);
            map.put(Integer.class, INT);
            map.put(boolean.class, BOOL);
            map.put(Boolean.class, BOOL);
            map.put(byte.class, TINYINT);
            map.put(Byte.class, TINYINT);
            map.put(short.class, SMALLINT);
            map.put(Short.class, SMALLINT);
            map.put(long.class, BIGINT);
            map.put(Long.class, BIGINT);
            map.put(BigDecimal.class, DECIMAL);
            map.put(double.class, DOUBLE);
            map.put(Double.class, DOUBLE);
            map.put(float.class, REAL);
            map.put(Float.class, REAL);
            map.put(Time.class, TIME);
            map.put(Timestamp.class, TIMESTAMP);
            map.put(java.util.Date.class, DATE);
            map.put(java.sql.Date.class, DATE);
            map.put(char.class, CHAR);
            map.put(Character.class, CHAR);
            map.put(String.class, VARCHAR);
        }

        /** */
        private String dbType;

        /**
         * Constructs new instance.
         *
         * @param dbType DB type name.
         */
        DBTypeEnum(String dbType) {
            this.dbType = dbType;
        }

        /**
         * Resolves enum by class.
         *
         * @param cls Class.
         * @return Enum value.
         */
        public static DBTypeEnum fromClass(Class<?> cls) {
            DBTypeEnum res = map.get(cls);

            if (res == null)
                res = BINARY;

            return res;
        }

        /**
         * Gets DB type name.
         *
         * @return DB type name.
         */
        public String dBTypeAsString() {
            return dbType;
        }

        /**
         * Gets DB type name.
         *
         * @return DB type name.
         */
        public int dBType() {
            switch (this) {
                case INT: {
                    return Types.INTEGER;
                }
                case BOOL: {
                    return Types.BOOLEAN;
                }
                case TINYINT: {
                    return Types.TINYINT;
                }
                case SMALLINT: {
                    return Types.SMALLINT;
                }
                case BIGINT: {
                    return Types.BIGINT;
                }
                case DECIMAL: {
                    return Types.DECIMAL;
                }
                case DOUBLE: {
                    return Types.DOUBLE;
                }
                case REAL: {
                    return Types.REAL;
                }
                case TIME: {
                    return Types.TIME;
                }
                case TIMESTAMP: {
                    return Types.TIMESTAMP;
                }
                case DATE: {
                    return Types.DATE;
                }
                case VARCHAR: {
                    return Types.VARCHAR;
                }
                case CHAR: {
                    return Types.CHAR;
                }
                case BINARY: {
                    return Types.BINARY;
                }
            }

            return Types.BINARY;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(DBTypeEnum.class, this);
        }
    }

    /**
     * Class to store information about group index.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     */
    private static class IndexGroup {
        /** */
        private boolean unique;

        /** */
        private List<String> cols = new ArrayList<String>();

        /**
         * @return {@code true} If index is unique.
         */
        boolean unique() {
            return unique;
        }

        /**
         * @param unique {@code true} If index is unique.
         */
        void unique(boolean unique) {
            this.unique = unique;
        }

        /**
         * @return List of columns.
         */
        List<String> columns() {
            return cols;
        }

        /**
         * @param cols List of columns.
         */
        void columns(List<String> cols) {
            this.cols = cols;
        }
    }

    /**
     * Information about table in database.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     */
    private class TableDescriptor {
        /** */
        private String fullTableName;

        /** */
        private String tableName;

        /** */
        private QueryType type;

        /**
         * @param type Space type.
         */
        TableDescriptor(QueryType type) {
            this.type = type;

            tableName = (cacheCtx.config().isIndexFullClassName()) ?
                type.valueClass().getName().replace('.', '_') :
                type.valueClass().getSimpleName();

            if (type.valueClass().isArray())
                tableName = tableName.substring(0, tableName.length() - 2) + "_array";

            fullTableName = '\"' + schema + "\"." + tableName;
        }

        /**
         * @return Database table name.
         */
        String fullTableName() {
            return fullTableName;
        }

        /**
         * @return Database table name.
         */
        String tableName() {
            return tableName;
        }

        /**
         * @return Type.
         */
        QueryType type() {
            return type;
        }

        /**
         * @return {@code true} If value is indexed in h2 full text or not.
         */
        public boolean valueH2TextIndex() {
            return type.valueH2TextIndex();
        }

        /**
         * @return Is value indexed in lucene or not.
         */
        public boolean valueLuceneIndex() {
            return type.valueLuceneIndex();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(TableDescriptor.class, this);
        }
    }

    /**
     * Class that holds all prepared statements for space.
     */
    private class SqlStatementCache {
        /** */
        private ConcurrentMap<Class<?>, PreparedStatement> writeStmts =
            new ConcurrentHashMap<Class<?>, PreparedStatement>();

        /** */
        private ConcurrentMap<Class<?>, PreparedStatement> rmvStmts =
            new ConcurrentHashMap<Class<?>, PreparedStatement>();

        /**
         * Gets cached write statement.
         *
         * @param cls Class to get statement for.
         * @return Prepared statement for write.
         * @throws GridException In case of error.
         */
        PreparedStatement writeStatement(Class<?> cls) throws GridException {
            PreparedStatement stmt = writeStmts.get(cls);

            if (stmt == null) {
                TableDescriptor table = tableDescriptor(cls);

                assert table != null;

                SB mergeSql = new SB();

                mergeSql.a("MERGE INTO ");
                mergeSql.a(table.fullTableName()).a(" VALUES (?,?,?,?,?");

                for (int i = 0; i < table.type().properties().size(); i++)
                    mergeSql.a(",?");

                mergeSql.a(')');

                Connection conn = connectionForThread();

                try {
                    stmt = conn.prepareStatement(mergeSql.toString());

                    writeStmts.putIfAbsent(cls, stmt);
                }
                catch (SQLException e) {
                    throw new GridException("Failed to create merge statement with SQL: " + mergeSql, e);
                }
            }

            return stmt;
        }

        /**
         * Gets cached remove statement.
         *
         * @param cls Class.
         * @return Prepared statement for remove.
         * @throws GridException In case of error.
         */
        PreparedStatement removeStatement(Class<?> cls) throws GridException {
            PreparedStatement stmt = rmvStmts.get(cls);

            if (stmt == null) {
                TableDescriptor table = tableDescriptor(cls);

                assert table != null;

                // Remove statement.
                String rmvSql = "DELETE FROM " + table.fullTableName() + " WHERE " + KEY_FIELD_NAME + "=?";

                Connection conn = connectionForThread();

                try {
                    stmt = conn.prepareStatement(rmvSql);

                    rmvStmts.putIfAbsent(cls, stmt);
                }
                catch (SQLException e) {
                    throw new GridException("Failed to create remove statement with SQL: " + rmvSql, e);
                }
            }

            return stmt;
        }

        /**
         * @param cls Class to clear frm cache.
         */
        void unregister(Class<?> cls) {
            rmvStmts.remove(cls);
            writeStmts.remove(cls);
        }

        /**
         * Closes all statements.
         *
         * @param log Log to use.
         */
        void close(GridLogger log) {
            for (PreparedStatement stmt : rmvStmts.values())
                U.close(stmt, log);

            for (PreparedStatement stmt : writeStmts.values())
                U.close(stmt, log);
        }
    }

    /**
     *
     */
    private class AnalyzeWorker extends GridWorker {
        /** {@inheritDoc} */
        protected AnalyzeWorker() {
            super(cacheCtx.gridName(), ANALYZE_THREAD_NAME, log);
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"BusyWait"})
        @Override protected void body() throws InterruptedException, GridInterruptedException {
            while (!isCancelled()) {
                Thread.sleep(cacheCtx.config().getIndexAnalyzeFrequency());

                if (!enterBusy())
                    return;

                try {
                    analyze();
                }
                catch (GridException e) {
                    U.error(log, "Failed to run ANALYZE h2 command (analyze thread will stop).", e);

                    return;
                }
                finally {
                    leaveBusy();
                }
            }
        }

        /**
         * Performs h2 special command ANALYZE in order to refresh columns selectivity
         * which in turn speeds up execution of sql statements where multiple indexes
         * are used. For more information about tuning h2 performance refer to
         * <a href="http://www.h2database.com/html/performance.html">H2 Performance</a>.
         *
         * @throws GridException If failed get connection.
         */
        private void analyze() throws GridException {
            if (log.isDebugEnabled())
                log.debug("Performing H2 ANALYZE command against query index database.");

            Connection conn = connectionForThread(false);

            if (conn != null) {
                Statement stmt = null;

                try {
                    stmt = conn.createStatement();

                    stmt.execute("ANALYZE SAMPLE_SIZE " + cacheCtx.config().getIndexAnalyzeSampleSize());
                }
                catch (SQLException e) {
                    U.error(log, "Failed to execute ANALYZE.", e);
                }
                finally {
                    U.close(stmt, log);
                }
            }
        }
    }

    /**
     * Special iterator based on database result set.
     */
    private class ResultSetIterator implements Iterator<GridCacheQueryIndexRow<K, V>> {
        /** */
        private ResultSet rs;

        /** */
        private Statement stmt;

        /** */
        private GridCacheQueryIndexRow<K, V> next;

        /**
         * @param rs Result set.
         * @param stmt Statement to close at the end (if provided).
         * @throws GridException In case of error.
         */
        ResultSetIterator(ResultSet rs, Statement stmt) throws GridException {
            this.rs = rs;
            this.stmt = stmt;

            try {
                if (rs.next())
                    next = loadRow();
                else {
                    U.close(rs, log);
                    U.close(stmt, log);
                }
            }
            catch (SQLException e) {
                onSqlException();

                throw new GridException("Failed to iterate SQL result set.", e);
            }
        }

        /**
         * Loads row from result set.
         *
         * @return Object associated with row of the result set.
         * @throws SQLException In case of SQL error.
         * @throws GridException In case of error.
         */
        @SuppressWarnings({"unchecked"})
        private GridCacheQueryIndexRow<K, V> loadRow() throws SQLException, GridException {
            K key;

            if (cacheCtx.config().isIndexFixedTyping() && rs.getMetaData().getColumnType(1) != Types.VARBINARY)
                key = (K)rs.getObject(1);
            else {
                byte[] buf = rs.getBytes(1);

                String keyLdrId = rs.getString(2);

                ClassLoader keyLdr = keyLdrId != null ?
                    cacheCtx.deploy().getClassLoader(UUID.fromString(keyLdrId)) : cacheCtx.deploy().localLoader();

                key = keyLdr != null ? (K)U.unmarshal(cacheCtx.marshaller(), new GridByteArrayList(buf), keyLdr) : null;
            }

            boolean binary = rs.getMetaData().getColumnType(4) == Types.VARBINARY;

            return new GridCacheQueryIndexRow<K, V>(
                key,
                binary ? null : (V)rs.getObject(4),
                binary ? rs.getBytes(4) : null,
                rs.getString(3)
            );
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            checkInterrupted();

            return next != null;
        }

        /**
         *
         */
        private void checkInterrupted() {
            if (Thread.currentThread().isInterrupted()) {
                next = null;

                U.close(rs, log);
                U.close(stmt, log);
            }
        }

        /** {@inheritDoc} */
        @Override public GridCacheQueryIndexRow<K, V> next() {
            checkInterrupted();

            GridCacheQueryIndexRow<K, V> res = next;

            try {
                if (!rs.isClosed() && rs.next())
                    next = loadRow();
                else {
                    next = null;

                    U.close(rs, log);
                }
            }
            catch (SQLException e) {
                onSqlException();

                throw new GridRuntimeException("Failed to iterate SQL result set.", e);
            }
            catch (GridException e) {
                throw new GridRuntimeException("Failed to load row.", e);
            }

            if (res == null)
                throw new NoSuchElementException();

            return res;
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            throw new UnsupportedOperationException("Remove is not supported.");
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(ResultSetIterator.class, this);
        }
    }
}
