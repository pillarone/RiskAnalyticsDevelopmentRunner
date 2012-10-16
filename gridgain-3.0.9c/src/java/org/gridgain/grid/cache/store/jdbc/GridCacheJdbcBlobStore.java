// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.store.jdbc;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.store.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * {@link GridCacheStore} implementation backed by JDBC.
 * <p>
 * Note that this class is intended for test purposes only and it is
 * not recommended to use it in production environment since it may
 * slow down performance. Store that is aware of key and value types
 * should be implemented for production systems.
 * <p>
 * Store will create table {@code ENTRIES} in the database to store data.
 * Table will have {@code key} and {@code val} fields.
 * <p>
 * If custom DDL and DML statements are provided, table and field names have
 * to be consistent for all statements and sequence of parameters have to be
 * preserved.
 * <h1>Configuration</h1>
 * Sections below describe mandatory and optional configuration settings as well
 * as providing example using Java and Spring XML.
 * <h2>Mandatory</h2>
 * There are no mandatory configuration parameters.
 * <h2>Optional</h2>
 * <ul>
 *     <li>Connection URL (see {@link #setConnectionUrl(String)})</li>
 *     <li>User name (see {@link #setUser(String)})</li>
 *     <li>Password (see {@link #setPassword(String)})</li>
 *     <li>Create table query (see {@link #setConnectionUrl(String)})</li>
 *     <li>Load entry query (see {@link #setLoadQuery(String)})</li>
 *     <li>Update entry query (see {@link #setUpdateQuery(String)})</li>
 *     <li>Insert entry query (see {@link #setInsertQuery(String)})</li>
 *     <li>Delete entry query (see {@link #setDeleteQuery(String)})</li>
 * </ul>
 * <h2>Java Example</h2>
 * <pre name="code" class="java">
 *     ...
 *     GridCacheJdbcBlobStore<String, String> store = new GridCacheJdbcBlobStore<String, String>();
 *     ...
 * </pre>
 * <h2>Spring Example</h2>
 * <pre name="code" class="xml">
 *     ...
 *     &lt;bean id=&quot;cache.jdbc.store1&quot; class=&quot;org.gridgain.grid.cache.store.jdbc.GridCacheJdbcBlobStore&quot;&gt;
 *         &lt;property name=&quot;connectionUrl&quot; value=&quot;jdbc:h2:mem:&quot;/&gt;
 *         &lt;property name=&quot;createTableQuery&quot; value=&quot;create table if not exists ENTRIES (key other, val other)&quot;/&gt;
 *     &lt;/bean&gt;
 *     ...
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheJdbcBlobStore<K, V> extends GridCacheStoreAdapter<K, V> {
    /** Default connection URL (value is <tt>jdbc:h2:mem:jdbcCacheStore;DB_CLOSE_DELAY=-1</tt>). */
    public static final String DFLT_CONN_URL = "jdbc:h2:mem:jdbcCacheStore;DB_CLOSE_DELAY=-1";

    /** Default create table query (value is <tt>create table if not exists ENTRIES (key other, val other)</tt>). */
    public static final String DFLT_CREATE_TBL_QRY = "create table if not exists ENTRIES (key other, val other)";

    /** Default load entry query (value is <tt>select * from ENTRIES where key=?</tt>). */
    public static final String DFLT_LOAD_QRY = "select * from ENTRIES where key=?";

    /** Default update entry query (value is <tt>select * from ENTRIES where key=?</tt>). */
    public static final String DFLT_UPDATE_QRY = "update ENTRIES set val=? where key=?";

    /** Default insert entry query (value is <tt>insert into ENTRIES (key, val) values (?, ?)</tt>). */
    public static final String DFLT_INSERT_QRY = "insert into ENTRIES (key, val) values (?, ?)";

    /** Default delete entry query (value is <tt>delete from ENTRIES where key=?</tt>). */
    public static final String DFLT_DEL_QRY = "delete from ENTRIES where key=?";

    /** Connection attribute name. */
    private static final String ATTR_CONN = "JDBC_STORE_CONNECTION";

    /** Connection URL. */
    private String connUrl = DFLT_CONN_URL;

    /** Query to create table. */
    private String createTblQry = DFLT_CREATE_TBL_QRY;

    /** Query to load entry. */
    private String loadQry = DFLT_LOAD_QRY;

    /** Query to update entry. */
    private String updateQry = DFLT_UPDATE_QRY;

    /** Query to insert entries. */
    private String insertQry = DFLT_INSERT_QRY;

    /** Query to delete entries. */
    private String delQry = DFLT_DEL_QRY;

    /** User name for database access. */
    private String user;

    /** Password for database access. */
    @GridToStringExclude
    private String passwd;

    /** Log. */
    @GridLoggerResource
    private GridLogger log;

    /** Marshaller. */
    @GridMarshallerResource
    private GridMarshaller marsh;

    /** Init guard. */
    @GridToStringExclude
    private final AtomicBoolean initGuard = new AtomicBoolean();

    /** Init latch. */
    @GridToStringExclude
    private final CountDownLatch initLatch = new CountDownLatch(1);

    /** Successful initialization flag. */
    private volatile boolean initOk;

    /** {@inheritDoc} */
    @Override public void txEnd(@Nullable String cacheName, GridCacheTx tx, boolean commit) throws GridException {
        init();

        Connection conn = tx.removeMeta(ATTR_CONN);

        if (conn != null) {
            try {
                if (commit)
                    conn.commit();
                else
                    conn.rollback();

                    if (log.isDebugEnabled())
                        log.debug("Transaction ended [xid=" + tx.xid() + ", commit=" + commit + ']');
            }
            catch (SQLException e) {
                throw new GridException("Failed to end transaction [xid=" + tx.xid() + ", commit=" + commit + ']', e);
            }
            finally {
                // Return connection back to the pool.
                U.closeQuiet(conn);
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"RedundantTypeArguments"})
    @Override public V load(@Nullable String cacheName, @Nullable GridCacheTx tx, Object key) throws GridException {
        init();

        if (log.isDebugEnabled())
            log.debug("Store load [key=" + key + ", tx=" + tx + ']');

        Connection conn = null;

        PreparedStatement stmt = null;

        try {
            conn = connection(tx);

            stmt = conn.prepareStatement(loadQry);

            stmt.setObject(1, toByteArray(key));

            ResultSet rs = stmt.executeQuery();

            if (rs.next())
                return marsh.<V>unmarshal(new ByteArrayInputStream(rs.getBytes(2)), getClass().getClassLoader());
        }
        catch (SQLException e) {
            throw new GridException("Failed to load object: " + key, e);
        }
        finally {
            U.closeQuiet(stmt);

            if (tx == null)
                // Close connection right away if there is no transaction.
                U.closeQuiet(conn);
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public void put(@Nullable String cacheName, GridCacheTx tx, K key, V val) throws GridException {
        init();

        if (log.isDebugEnabled())
            log.debug("Store put [key=" + key + ", val=" + val + ", tx=" + tx + ']');

        PreparedStatement stmt = null;

        try {
            Connection conn = connection(tx);

            stmt = conn.prepareStatement(updateQry);

            stmt.setObject(1, toByteArray(val));
            stmt.setObject(2, toByteArray(key));

            if (stmt.executeUpdate() == 0) {
                stmt.close();

                stmt = conn.prepareStatement(insertQry);

                stmt.setObject(1, toByteArray(key));
                stmt.setObject(2, toByteArray(val));

                stmt.executeUpdate();
            }
        }
        catch (SQLException e) {
            throw new GridException("Failed to put object [key=" + key + ", val=" + val + ']', e);
        }
        finally {
            U.closeQuiet(stmt);
        }
    }

    /** {@inheritDoc} */
    @Override public void remove(@Nullable String cacheName, GridCacheTx tx, K key) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Store remove [key=" + key + ", tx=" + tx + ']');

        PreparedStatement stmt = null;

        try {
            Connection conn = connection(tx);

            stmt = conn.prepareStatement(delQry);

            stmt.setObject(1, toByteArray(key));

            stmt.executeUpdate();
        }
        catch (SQLException e) {
            throw new GridException("Failed to remove object: " + key, e);
        }
        finally {
            U.closeQuiet(stmt);
        }
    }

    /**
     * @param obj Object to convert to byte array.
     * @return Byte array.
     * @throws GridException If failed to convert.
     */
    private byte[] toByteArray(Object obj) throws GridException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        marsh.marshal(obj, bos);

        return bos.toByteArray();
    }

    /**
     * @param tx Cache transaction.
     * @return Connection.
     * @throws SQLException In case of error.
     */
    private Connection connection(@Nullable GridCacheTx tx) throws SQLException  {
        if (tx != null) {
            Connection conn = tx.meta(ATTR_CONN);

            if (conn == null) {
                conn = openConnection();

                // Store connection in transaction metadata, so it can be accessed
                // for other operations on the same transaction.
                tx.addMeta(ATTR_CONN, conn);
            }

            return conn;
        }
        // Transaction can be null in case of simple load operation.
        else
            return openConnection();
    }

    /**
     * Gets connection from a pool.
     *
     * @return Pooled connection.
     * @throws SQLException In case of error.
     */
    private Connection openConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(connUrl, user, passwd);

        conn.setAutoCommit(false);

        return conn;
    }

    /**
     * Initializes store.
     *
     * @throws GridException If failed to initialize.
     */
    private void init() throws GridException {
        if (initGuard.compareAndSet(false, true)) {
            if (log.isDebugEnabled())
                log.debug("Initializing cache store.");

            if (F.isEmpty(connUrl))
                throw new GridException("Failed to initialize cache store (connection URL is not provided).");

            if (F.isEmpty(createTblQry))
                throw new GridException("Failed to initialize cache store (create table query is not provided).");

            Connection conn = null;

            Statement stmt = null;

            try {
                conn = openConnection();

                stmt = conn.createStatement();

                stmt.execute(createTblQry);

                conn.commit();

                initOk = true;
            }
            catch (SQLException e) {
                throw new GridException("Failed to create database table.", e);
            }
            finally {
                U.closeQuiet(stmt);
                U.closeQuiet(conn);

                initLatch.countDown();
            }
        }
        else {
            try {
                initLatch.await();
            }
            catch (InterruptedException ignored) {
                throw new GridException("Thread has been interrupted.");
            }

            if (!initOk)
                throw new GridException("Cache store was not properly initialized.");
        }
    }

    /**
     * Sets connection URL.
     *
     * @param connUrl Connection URL.
     */
    public void setConnectionUrl(String connUrl) {
        this.connUrl = connUrl;
    }

    /**
     * Sets create table query.
     *
     * @param createTblQry Create table query.
     */
    public void setCreateTableQuery(String createTblQry) {
        this.createTblQry = createTblQry;
    }

    /**
     * Sets load query.
     *
     * @param loadQry Load query
     */
    public void setLoadQuery(String loadQry) {
        this.loadQry = loadQry;
    }

    /**
     * Sets update entry query.
     *
     * @param updateQry Update entry query.
     */
    public void setUpdateQuery(String updateQry) {
        this.updateQry = updateQry;
    }

    /**
     * Sets insert entry query.
     *
     * @param insertQry Insert entry query.
     */
    public void setInsertQuery(String insertQry) {
        this.insertQry = insertQry;
    }

    /**
     * Sets delete entry query.
     *
     * @param delQry Delete entry query.
     */
    public void setDeleteQuery(String delQry) {
        this.delQry = delQry;
    }

    /**
     * Sets user name for database access.
     *
     * @param user User name.
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Sets password for database access.
     *
     * @param passwd Password.
     */
    public void setPassword(String passwd) {
        this.passwd = passwd;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheJdbcBlobStore.class, this, "passwd", passwd != null ? "*" : null);
    }
}
