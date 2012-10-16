// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;

/**
 * Entry information that gets passed over wire.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheEntryInfo<K, V> implements Externalizable {
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

    /** Time to live. */
    private long ttl;

    /** Expiration time. */
    @GridToStringInclude
    private long expireTime;

    /** Entry version. */
    private GridCacheVersion ver;

    /** Metrics. */
    private GridCacheMetricsAdapter metrics;

    /**
     * @param key Entry key.
     */
    public void key(K key) {
        this.key = key;
    }

    /**
     * @return Entry key.
     */
    public K key() {
        return key;
    }

    /**
     * @return Key bytes.
     */
    public byte[] keyBytes() {
        return keyBytes;
    }

    /**
     * @param keyBytes Key bytes.
     */
    public void keyBytes(byte[] keyBytes) {
        this.keyBytes = keyBytes;
    }

    /**
     * @return Entry value.
     */
    public V value() {
        return val;
    }

    /**
     * @param val Entry value.
     */
    public void value(V val) {
        this.val = val;
    }

    /**
     * @return Value bytes.
     */
    public byte[] valueBytes() {
        return valBytes;
    }

    /**
     * @param valBytes Value bytes.
     */
    public void valueBytes(byte[] valBytes) {
        this.valBytes = valBytes;
    }

    /**
     * @return Expire time.
     */
    public long expireTime() {
        return expireTime;
    }

    /**
     * @param expireTime Expiration time.
     */
    public void expireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    /**
     * @return Time to live.
     */
    public long ttl() {
        return ttl;
    }

    /**
     * @param ttl Time to live.
     */
    public void ttl(long ttl) {
        this.ttl = ttl;
    }

    /**
     * @return Version.
     */
    public GridCacheVersion version() {
        return ver;
    }

    /**
     * @param ver Version.
     */
    public void version(GridCacheVersion ver) {
        this.ver = ver;
    }

    /**
     * @return Metrics.
     */
    public GridCacheMetricsAdapter metrics() {
        return metrics;
    }

    /**
     * @param metrics Metrics.
     */
    public void metrics(GridCacheMetricsAdapter metrics) {
        this.metrics = metrics;
    }

    /**
     * @param ctx Cache context.
     * @throws GridException In case of error.
     */
    public void marshal(GridCacheContext<K, V> ctx) throws GridException {
        if (keyBytes == null)
            keyBytes = CU.marshal(ctx, key).getArray();

        if (valBytes == null && val != null)
            valBytes = CU.marshal(ctx, val).getArray();
    }

    /**
     * Unmarshalls entry.
     *
     * @param ctx Cache context.
     * @param clsLdr Class loader.
     * @throws GridException If unmarshalling failed.
     */
    @SuppressWarnings({"unchecked"})
    public void unmarshal(GridCacheContext<K, V> ctx, ClassLoader clsLdr) throws GridException {
        GridMarshaller mrsh = ctx.marshaller();

        if (key == null)
            key = (K)U.unmarshal(mrsh, new GridByteArrayList(keyBytes), clsLdr);

        if (val == null && valBytes != null)
            val = (V)U.unmarshal(mrsh, new GridByteArrayList(valBytes), clsLdr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        GridUtils.writeByteArray(out, keyBytes);
        GridUtils.writeByteArray(out, valBytes);

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

        CU.writeVersion(out, ver);

        out.writeObject(metrics);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        keyBytes = GridUtils.readByteArray(in);
        valBytes = GridUtils.readByteArray(in);

        ttl = in.readLong();

        long remaining = in.readLong();

        expireTime = remaining < 0 ? 0 : System.currentTimeMillis() + remaining;

        // Account for overflow.
        if (expireTime < 0)
            expireTime = 0;

        ver = CU.readVersion(in);

        metrics = (GridCacheMetricsAdapter)in.readObject();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheEntryInfo.class, this,
            "keyBytesSize", (keyBytes == null ? "null" : Integer.toString(keyBytes.length)),
            "valBytesSize", (valBytes == null ? "null" : Integer.toString(valBytes.length)));
    }
}
