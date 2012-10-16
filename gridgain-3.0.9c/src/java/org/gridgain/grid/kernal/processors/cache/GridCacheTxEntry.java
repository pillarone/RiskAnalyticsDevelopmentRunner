// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.kernal.processors.cache.GridCacheOperation.*;

/**
 * Transaction entry. Note that it is essential that this class does not override
 * {@link #equals(Object)} method, as transaction entries should use referential
 * equality.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheTxEntry<K, V> implements Externalizable {
    /** Owning transaction. */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridToStringExclude
    private GridCacheTxEx<K, V> tx;

    /** Cache key. */
    @GridToStringInclude
    private transient K key;

    /** Key bytes. */
    private byte[] keyBytes;

    /** Cache value. */
    @GridToStringInclude
    private transient V val;

    /** Value bytes. */
    private byte[] valBytes;

    /** Filter bytes. */
    private byte[] filterBytes;

    /** Cache operation. */
    @GridToStringInclude
    private GridCacheOperation op;

    /** Time to live. */
    private long ttl;

    /** Time to live. */
    @GridToStringInclude
    private long expireTime;

    /** Explicit lock version if there is one. */
    @GridToStringInclude
    private GridCacheVersion explicitVer;

    /** DHT version. */
    private transient volatile GridCacheVersion dhtVer;

    /** Put filters. */
    @GridToStringInclude
    private GridPredicate<? super GridCacheEntry<K, V>>[] filters;

    /** Underlying cache entry. */
    private transient volatile GridCacheEntryEx<K, V> entry;

    /** Cache registry. */
    private transient GridCacheContext<K, V> ctx;

    /** Committing flag. */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private transient AtomicBoolean committing = new AtomicBoolean(false);

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridToStringExclude
    private transient GridCacheTxEntry<K, V> parent;

    /** */
    @GridToStringExclude
    private transient GridCacheTxEntry<K, V> child;

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridToStringExclude
    private transient GridCacheTxEntry<K, V> root;

    /** Operation ID. */
    private transient int opId;

    /** */
    @GridToStringExclude
    private transient GridFuture<Boolean> fut;

    /** Mutex. */
    private transient Object mux;

    /**
     * This flag is 'volatile' for quick read checks. Updates to this value
     * are still done within transaction synchronization.
     */
    private transient boolean marked;

    /** Assigned node ID (required only for partitioned cache). */
    private transient UUID nodeId;

    /**
     * Required by {@link Externalizable}
     */
    public GridCacheTxEntry() {
        /* No-op. */
    }

    /**
     * @param ctx Cache registry.
     * @param tx Owning transaction.
     * @param op Operation.
     * @param val Value.
     * @param ttl Time to live.
     * @param entry Cache entry.
     */
    public GridCacheTxEntry(GridCacheContext<K, V> ctx, GridCacheTxEx<K, V> tx, GridCacheOperation op, V val,
        long ttl, GridCacheEntryEx<K, V> entry) {
        assert ctx != null;
        assert tx != null;
        assert op != null;
        assert entry != null;

        this.ctx = ctx;
        this.tx = tx;
        this.op = op;
        this.val = val;
        this.entry = entry;
        this.ttl = ttl;

        opId = -1;

        key = entry.key();
        keyBytes = entry.keyBytes();

        expireTime = toExpireTime(ttl);

        mux = new Object();
    }

    /**
     * @param ctx Cache registry.
     * @param tx Owning transaction.
     * @param opId Operation ID.
     * @param parent Root of the linked list.
     * @param op Operation.
     * @param val Value.
     * @param ttl Time to live.
     * @param entry Cache entry.
     * @param filters Put filters.
     */
    public GridCacheTxEntry(GridCacheContext<K, V> ctx, GridCacheTxEx<K, V> tx, int opId,
        GridCacheTxEntry<K, V> parent, GridCacheOperation op, V val, long ttl, GridCacheEntryEx<K,V> entry,
        GridPredicate<? super GridCacheEntry<K, V>>[] filters) {
        assert ctx != null;
        assert tx != null;
        assert op != null;
        assert entry != null;

        this.ctx = ctx;
        this.tx = tx;
        this.op = op;
        this.val = val;
        this.entry = entry;
        this.ttl = ttl;
        this.opId = opId;
        this.parent = parent;
        this.filters = filters;

        key = entry.key();
        keyBytes = entry.keyBytes();

        expireTime = toExpireTime(ttl);

        // Set root.
        for (root = this; root.parent != null; root = root.parent) {}

        // If this is root.
        if (parent == null)
            mux = new Object();
        else
            synchronized (mutex()) {
                parent.child = this;
            }
    }

    /**
     * @param ctx Context.
     * @return Clean copy of this entry.
     */
    public GridCacheTxEntry<K, V> cleanCopy(GridCacheContext<K, V> ctx) {
        GridCacheTxEntry<K, V> cp = new GridCacheTxEntry<K, V>();

        cp.mux = new Object();

        cp.key = key;
        cp.ctx = ctx;

        synchronized (mutex()) {
            cp.val = val;
            cp.keyBytes = keyBytes;
            cp.valBytes = valBytes;
            cp.op = op;
            cp.filters = filters;
            cp.val = val;
            cp.ttl = ttl;
            cp.expireTime = expireTime;
            cp.explicitVer = explicitVer;
        }

        return cp;
    }

    /**
     * @return Node ID.
     */
    public UUID nodeId() {
        return nodeId;
    }

    /**
     * @param nodeId Node ID.
     */
    public void nodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * @return DHT version.
     */
    public GridCacheVersion dhtVersion() {
        return dhtVer;
    }

    /**
     * @param dhtVer DHT version.
     */
    public void dhtVersion(GridCacheVersion dhtVer) {
        this.dhtVer = dhtVer;
    }

    /**
     * Marks entry as committing.
     *
     * @return {@code True} if entry has been marked by this call.
     */
    public boolean markCommitting() {
        return committing.compareAndSet(false, true);
    }

    /**
     * @return {@code True} if committing flag is set.
     */
    public boolean committing() {
        return committing.get();
    }

    /**
     * @return Root of the linked list.
     */
    GridCacheTxEntry<K, V> root() {
        return root;
    }

    /**
     * @return {@code True} if this entry is root.
     */
    boolean isRoot() {
        return parent == null;
    }

    /**
     * @return Next entry.
     */
    GridCacheTxEntry<K, V> child() {
        synchronized (mutex()) {
            return child;
        }
    }

    /**
     * @return Previous link.
     */
    GridCacheTxEntry<K, V> parent() {
        return parent;
    }

    /**
     * @return Operation ID.
     */
    int opId() {
        return opId;
    }

    /**
     * @return Future.
     */
    GridFuture<Boolean> future() {
        return fut;
    }

    /**
     * @param fut Future.
     */
    void future(GridFuture<Boolean> fut) {
        this.fut = fut;
    }

    /**
     * @return Entry that is last in linked chain.
     */
    GridCacheTxEntry<K, V> last() {
        GridCacheTxEntry<K, V> e = this;

        synchronized (mutex()) {
            while (e.child != null)
                e = e.child;

            return e;
        }
    }

    /**
     * Marks as checked after acquiring lock.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    void mark() {
        Object mux = mutex();

        synchronized (mux) {
            if (!marked) {
                marked = true;

                mux.notifyAll();
            }
        }
    }

    /**
     * @return Checked flag.
     */
    boolean marked() {
        synchronized (mutex()) {
            return marked;
        }
    }

    /**
     * Waits for parent to be marked.
     *
     * @param mark If {@code true}, then entry will be marked once parent is marked.
     * @return Root value.
     * @throws GridInterruptedException If interrupted or rolled back.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    V waitForParent(boolean mark) throws GridException {
        if (!isRoot()) {
            assert parent != null;

            Object mux = mutex();

            synchronized (mux) {
                if (!marked) {
                    while (!tx.isRollbackOnly() && !parent.marked)
                        U.wait(mux);

                    if (tx.isRollbackOnly())
                        throw new GridException("Transaction is set to rollback while waiting for a lock: " + this);

                    if (mark) {
                        marked = true;

                        mux.notifyAll();
                    }
                }

                return root.value();
            }
        }

        return value();
    }

    /**
     * Sets value and then marks this entry.
     *
     * @param val Value to set.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    void setAndMark(V val) {
        Object mux = mutex();

        synchronized (mux) {
            this.val = val;

            if (!marked) {
                marked = true;

                mux.notifyAll();
            }
        }
    }

    /**
     * Sets value and then marks this entry.
     *
     * @param op Operation.
     * @param val Value to set.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    void setAndMark(GridCacheOperation op, V val) {
        Object mux = mutex();

        synchronized (mux) {
            this.op = op;
            this.val = val;

            if (!marked) {
                marked = true;

                mux.notifyAll();
            }
        }
    }

    /**
     * @return {@code True} if last entry in link chain is marked.
     */
    boolean lastMarked() {
        synchronized (mutex()) {
            return last().marked();
        }
    }

    /**
     * @return Root value.
     */
    V rootValue() {
        return root.value();
    }

    /**
     * @return Mutex for this link chain.
     */
    final Object mutex() {
        if (isRoot()) {
            assert mux != null;

            return mux;
        }
        else {
            assert mux == null;

            return root.mux;
        }
    }

    /**
     * @param from Entry to copy from.
     * @param mark If {@code true} then {@code 'from'} entry will be marked.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    void copyFrom(GridCacheTxEntry<K, V> from, boolean mark) {
        // Make sure that copying happens within the same link chain.
        assert root == from.root;

        Object mux = mutex();

        synchronized (mux) {
            op = from.op;
            val = from.val;
            ttl = from.ttl;
            filters = from.filters;
            expireTime = from.expireTime;
            explicitVer = from.explicitVer;
            valBytes = from.valBytes;

            if (from.keyBytes != null)
                keyBytes = from.keyBytes;

            if (mark && !from.marked) {
                from.marked = true;

                mux.notifyAll();
            }
        }
    }

    /**
     *
     */
    void markAndCopyToRoot() {
        if (!isRoot()) {
            assert parent != null;

            root.copyFrom(this, true);
        }
        else
            mark();
    }

    /**
     *
     * @param ttl Time to live.
     * @return Expire time.
     */
    private long toExpireTime(long ttl) {
        long expireTime = ttl == 0 ? 0 : System.currentTimeMillis() + ttl;

        if (expireTime <= 0)
            expireTime = 0;

        return expireTime;
    }

    /**
     * @return Entry key.
     */
    public K key() {
        return key;
    }

    /**
     *
     * @return Key bytes.
     */
    @Nullable public byte[] keyBytes() {
        byte[] bytes;

        synchronized (mutex()) {
            bytes = keyBytes;
        }

        if (bytes == null && entry != null) {
            bytes = entry.keyBytes();

            synchronized (mutex()) {
                keyBytes = bytes;
            }
        }

        return bytes;
    }

    /**
     * @param keyBytes Key bytes.
     */
    public void keyBytes(byte[] keyBytes) {
        initKeyBytes(keyBytes);
    }

    /**
     * @return Underlying cache entry.
     */
    public GridCacheEntryEx<K, V> cached() {
        return entry;
    }

    /**
     * @param entry Cache entry.
     * @param keyBytes Key bytes, possibly {@code null}.
     */
    public void cached(GridCacheEntryEx<K,V> entry, @Nullable byte[] keyBytes) {
        assert entry != null;

        this.entry = entry;

        initKeyBytes(keyBytes);
    }

    /**
     * Initialized key bytes locally and on the underlying entry.
     *
     * @param bytes Key bytes to initialize.
     */
    private void initKeyBytes(@Nullable byte[] bytes) {
        if (bytes != null) {
            synchronized (mutex()) {
                keyBytes = bytes;
            }

            while (true) {
                try {
                    if (entry != null)
                        entry.keyBytes(bytes);

                    break;
                }
                catch (GridCacheEntryRemovedException ignore) {
                    entry = ctx.cache().entryEx(key);
                }
            }
        }
        else if (entry != null) {
            bytes = entry.keyBytes();

            if (bytes != null)
                synchronized (mutex()) {
                    keyBytes = bytes;
                }
        }
    }

    /**
     * @return Entry value.
     */
    public V value() {
        synchronized (mutex()) {
            return val;
        }
    }

    /**
     * @return Value bytes.
     */
    public byte[] valueBytes() {
        synchronized (mutex()) {
            return valBytes;
        }
    }

    /**
     * @param valBytes Value bytes.
     */
    public void valueBytes(byte[] valBytes) {
        synchronized (mutex()) {
            this.valBytes = valBytes;
        }
    }

    /**
     * @return Expire time.
     */
    public long expireTime() {
        synchronized (mutex()) {
            return expireTime;
        }
    }

    /**
     * @param expireTime Expiration time.
     */
    public void expireTime(long expireTime) {
        synchronized (mutex()) {
            this.expireTime = expireTime;
        }
    }

    /**
     * @return Time to live.
     */
    public long ttl() {
        synchronized (mutex()) {
            return ttl;
        }
    }

    /**
     * @param ttl Time to live.
     */
    public void ttl(long ttl) {
        synchronized (mutex()) {
            this.ttl = ttl;
        }
    }

    /**
     * @param val Entry value.
     * @return Old value.
     */
    public V value(V val) {
        synchronized (mutex()) {
            V oldVal = this.val;

            this.val = val;

            return oldVal;
        }
    }

    /**
     * @return Cache operation.
     */
    public GridCacheOperation op() {
        synchronized (mutex()) {
            return op;
        }
    }

    /**
     * @param op Cache operation.
     */
    public void op(GridCacheOperation op) {
        synchronized (mutex()) {
            this.op = op;
        }
    }

    /**
     * @return {@code True} if read entry.
     */
    public boolean isRead() {
        return op() == READ;
    }

    /**
     * @param explicitVer Explicit version.
     */
    public void explicitVersion(GridCacheVersion explicitVer) {
        synchronized (mutex()) {
            this.explicitVer = explicitVer;
        }
    }

    /**
     * @return Explicit version.
     */
    public GridCacheVersion explicitVersion() {
        synchronized (mutex()) {
            return explicitVer;
        }
    }

    /**
     * @return Put filters.
     */
    public GridPredicate<? super GridCacheEntry<K, V>>[] filters() {
        synchronized (mutex()) {
            return filters;
        }
    }

    /**
     * @param filters Put filters.
     */
    public void filters(GridPredicate<? super GridCacheEntry<K, V>>[] filters) {
        synchronized (mutex()) {
            this.filters = filters;
        }
    }

    /**
     * @param ctx Context.
     * @throws GridException If failed.
     */
    public void marshal(GridCacheContext<K, V> ctx) throws GridException {
        byte[] keyBytes;
        byte[] valBytes;
        byte[] filterBytes;
        GridCacheOperation op;
        GridPredicate<? super GridCacheEntry<K, V>>[] filters;
        V val;

        synchronized (mutex()) {
            keyBytes = this.keyBytes;
            valBytes = this.valBytes;
            filters = this.filters;
            filterBytes = this.filterBytes;
            val = this.val;
            op = this.op;
        }

        if (keyBytes == null)
            keyBytes = entry.getOrMarshalKeyBytes();

        // Don't serialize values for read operations.
        if (op == READ || op == NOOP)
            valBytes = null;
        else if (valBytes == null && val != null)
            valBytes = CU.marshal(ctx, val).getArray();

        // Do not serialize filters if they are null.
        if (F.isEmpty(filters))
            filterBytes = null;
        else if (filterBytes == null)
            filterBytes = CU.marshal(ctx, filterBytes).getArray();

        synchronized (mutex()) {
            this.keyBytes = keyBytes;
            this.valBytes = valBytes;
            this.filterBytes = filterBytes;
        }
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        byte[] keyBytes;
        byte[] valBytes;
        byte[] filterBytes;
        GridCacheOperation op;
        long ttl;
        long expireTime;
        GridCacheVersion explicitVer;

        synchronized (mutex()) {
            keyBytes = this.keyBytes;
            valBytes = this.valBytes;
            filterBytes = this.filterBytes;
            op = this.op;
            ttl = this.ttl;
            expireTime = this.expireTime;
            explicitVer = this.explicitVer;
        }

        U.writeByteArray(out, keyBytes);
        U.writeByteArray(out, valBytes);
        U.writeByteArray(out, filterBytes);

        out.writeByte(op.ordinal());

        out.writeLong(ttl);

        long remaining;

        // 0 means never expires.
        if (expireTime == 0)
            remaining = -1;
        else {
            remaining = expireTime - System.currentTimeMillis();

            if (remaining < 0)
                remaining = 0;
        }

        // Write remaining time.
        out.writeLong(remaining);

        CU.writeVersion(out, explicitVer);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        mux = new Object();

        byte[] keyBytes = U.readByteArray(in);
        byte[] valBytes = U.readByteArray(in);
        byte[] filterBytes = U.readByteArray(in);

        if (filterBytes == null)
            filters(CU.<K, V>empty());

        GridCacheOperation op = fromOrdinal(in.readByte());

        long ttl = in.readLong();

        long remaining = in.readLong();

        long expireTime = remaining < 0 ? 0 : System.currentTimeMillis() + remaining;

        // Account for overflow.
        if (expireTime < 0)
            expireTime = 0;

        GridCacheVersion explicitVer = CU.readVersion(in);

        synchronized (mutex()) {
            this.keyBytes = keyBytes;
            this.valBytes = valBytes;
            this.filterBytes = filterBytes;
            this.op = op;
            this.ttl = ttl;
            this.expireTime = expireTime;
            this.explicitVer = explicitVer;
        }
    }

    /**
     * Unmarshalls entry.
     *
     * @param ctx Cache context.
     * @param clsLdr Class loader.
     * @throws GridException If un-marshalling failed.
     */
    @SuppressWarnings({"unchecked"})
    public void unmarshal(GridCacheContext<K, V> ctx, ClassLoader clsLdr) throws GridException {
        this.ctx = ctx;

        byte[] keyBytes;
        byte[] valBytes;
        byte[] filterBytes;
        GridCacheOperation op;
        GridPredicate<? super GridCacheEntry<K, V>>[] filters;
        V val;

        synchronized (mutex()) {
            keyBytes = this.keyBytes;
            valBytes = this.valBytes;
            filterBytes = this.filterBytes;
            val = this.val;
            op = this.op;
            filters = this.filters;
        }

        // Don't unmarshal more than once by checking key for null.
        if (key == null)
            key = (K)U.unmarshal(ctx.marshaller(), new ByteArrayInputStream(keyBytes), clsLdr);

        if (op != READ && valBytes != null && val == null)
            value((V)U.unmarshal(ctx.marshaller(), new ByteArrayInputStream(valBytes), clsLdr));

        if (filters == null && filterBytes != null) {
            filters = (GridPredicate<? super GridCacheEntry<K, V>>[])
                U.unmarshal(ctx.marshaller(), new ByteArrayInputStream(filterBytes), clsLdr);

            if (filters == null)
                filters = CU.empty();

            filters(filters);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        byte[] keyBytes;
        byte[] valBytes;

        synchronized (mutex()) {
            keyBytes = this.keyBytes;
            valBytes = this.valBytes;
        }

        return GridToStringBuilder.toString(GridCacheTxEntry.class, this,
            "keyBytesSize", keyBytes == null ? "null" : Integer.toString(keyBytes.length),
            "valBytesSize", valBytes == null ? "null" : Integer.toString(valBytes.length),
            "xidVer", tx == null ? "null" : tx.xidVersion());
    }
}
