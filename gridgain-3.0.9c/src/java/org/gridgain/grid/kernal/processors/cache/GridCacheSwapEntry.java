// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Swap entry.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheSwapEntry<V> implements Externalizable {
    /** Value bytes. */
    private byte[] valBytes;

    /** Value. */
    private V val;

    /** Class loader id. */
    private UUID clsLdrId;

    /** Version. */
    private GridCacheVersion ver;

    /** Time to live. */
    private long ttl;

    /** Expire time. */
    private long expireTime;

    /** Metrics. */
    private GridCacheMetricsAdapter metrics;

    /**
     * Empty constructor.
     */
    public GridCacheSwapEntry() {
        // No-op.
    }

    /**
     *
     * @param valBytes Value.
     * @param ver Version.
     * @param ttl Entry time to live.
     * @param expireTime Expire time.
     * @param metrics Metrics.
     * @param clsLdrId Class loader id for entry value (can be {@code null} for local class loader).
     */
    public GridCacheSwapEntry(byte[] valBytes, GridCacheVersion ver, long ttl, long expireTime,
        GridCacheMetricsAdapter metrics, @Nullable UUID clsLdrId) {
        assert ver != null;
        assert metrics != null;

        this.valBytes = valBytes;
        this.ver = ver;
        this.ttl = ttl;
        this.expireTime = expireTime;
        this.metrics = metrics;
        this.clsLdrId = clsLdrId;
    }

    /**
     * @return Value bytes.
     */
    public byte[] valueBytes() {
        return valBytes;
    }

    /**
     * @return Value.
     */
    public V value() {
        return val;
    }

    /**
     * @param val Value.
     */
    void value(V val) { this.val = val; }

    /**
     * @return Version.
     */
    public GridCacheVersion version() {
        return ver;
    }

    /**
     * @return Time to live.
     */
    public long ttl() {
        return ttl;
    }

    /**
     * @return Expire time.
     */
    public long expireTime() {
        return expireTime;
    }

    /**
     * @return Metrics.
     */
    public GridCacheMetricsAdapter metrics() {
        return metrics;
    }

    /**
     * @return Class loader id for entry value.
     */
    public UUID classLoaderId() {
        return clsLdrId;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeByteArray(out, valBytes);

        out.writeObject(ver);
        out.writeObject(metrics);

        out.writeLong(ttl);
        out.writeLong(expireTime);

        U.writeUuid(out, clsLdrId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        valBytes = U.readByteArray(in);

        ver = (GridCacheVersion)in.readObject();
        metrics = (GridCacheMetricsAdapter)in.readObject();

        ttl = in.readLong();
        expireTime = in.readLong();

        clsLdrId = U.readUuid(in);

        assert ver != null;
        assert metrics != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override public String toString() {
        return S.toString(GridCacheSwapEntry.class, this);
    }
}