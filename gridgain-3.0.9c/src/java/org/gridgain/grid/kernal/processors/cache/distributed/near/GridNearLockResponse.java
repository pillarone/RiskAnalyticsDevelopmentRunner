// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Near cache lock response.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearLockResponse<K, V> extends GridDistributedLockResponse<K, V> {
    /** Retries. */
    @GridToStringInclude
    private Collection<K> retries;

    /** Retry bytes. */
    @GridToStringExclude
    private Collection<byte[]> retryBytes;

    /** Collection of versions that are pending and less than lock version. */
    @GridToStringInclude
    private Collection<GridCacheVersion> pending;

    /** */
    private GridUuid miniId;

    /** DHT versions. */
    @GridToStringInclude
    private GridCacheVersion[] dhtVers;

    /**
     * Empty constructor (required by {@link Externalizable}).
     */
    public GridNearLockResponse() {
        // No-op.
    }

    /**
     * @param lockVer Lock ID.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     * @param cnt Count.
     * @param err Error.
     */
    public GridNearLockResponse(GridCacheVersion lockVer, GridUuid futId, GridUuid miniId, int cnt, Throwable err) {
        super(lockVer, futId, cnt, err);

        assert miniId != null;

        this.miniId = miniId;

        dhtVers = new GridCacheVersion[cnt];
    }

    /**
     * Gets pending versions that are less than {@link #version()}.
     *
     * @return Pending versions.
     */
    public Collection<GridCacheVersion> pending() {
        return pending;
    }

    /**
     * Sets pending versions that are less than {@link #version()}.
     *
     * @param pending Pending versions.
     */
    public void pending(Collection<GridCacheVersion> pending) {
        this.pending = pending;
    }

    /**
     * @return Failed filter set.
     */
    public Collection<K> retries() {
        return retries == null ? Collections.<K>emptyList() : retries;
    }

    /**
     * @param retries Keys to retry due to ownership shift.
     */
    public void retries(Collection<K> retries) {
        this.retries = retries;
    }

    /**
     * @return Mini future ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /**
     * @param idx Index.
     * @return DHT version.
     */
    public GridCacheVersion dhtVersion(int idx) {
        return dhtVers[idx];
    }

    /**
     * @param val Value.
     * @param valBytes Value bytes (possibly {@code null}).
     * @param dhtVer DHT version.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    public void addValueBytes(V val, byte[] valBytes, GridCacheVersion dhtVer, GridCacheContext<K, V> ctx)
        throws GridException {
        dhtVers[values().size()] = dhtVer;

        // Delegate to super.
        addValueBytes(val, valBytes, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (retryBytes == null)
            retryBytes = marshalCollection(retries, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (retries == null)
            retries = unmarshalCollection(retryBytes, ctx, ldr);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeCollection(out, retryBytes);
        U.writeCollection(out, pending);
        U.writeArray(out, dhtVers);

        assert miniId != null;

        U.writeGridUuid(out, miniId);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        retryBytes = U.readList(in);
        pending = U.readSet(in);
        dhtVers = U.readArray(in, CU.versionArrayFactory());
        miniId = U.readGridUuid(in);

        assert miniId != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearLockResponse.class, this, super.toString());
    }
}
