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

import java.io.*;
import java.util.*;

/**
 * Near cache prepare response.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearTxPrepareResponse<K, V> extends GridDistributedTxPrepareResponse<K, V> {
    /** Retries. */
    private Collection<K> retries;

    /** Retry bytes. */
    private Collection<byte[]> retryBytes;

    /** Future ID.  */
    private GridUuid futId;

    /** Mini future ID. */
    private GridUuid miniId;

    /** DHT version. */
    private GridCacheVersion dhtVer;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridNearTxPrepareResponse() {
        // No-op.
    }

    /**
     * @param xid Xid version.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     * @param dhtVer DHT version.
     * @param err Error.
     */
    public GridNearTxPrepareResponse(GridCacheVersion xid, GridUuid futId, GridUuid miniId, GridCacheVersion dhtVer,
        Throwable err) {
        super(xid, err);

        assert futId != null;
        assert miniId != null;
        assert dhtVer != null;

        this.futId = futId;
        this.miniId = miniId;
        this.dhtVer = dhtVer;
    }

    /**
     * @return Mini future ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    /**
     * @return DHT version.
     */
    public GridCacheVersion dhtVersion() {
        return dhtVer;
    }

    /**
     * @return Retries.
     */
    public Collection<K> retries() {
        return retries;
    }

    /**
     * @param retries Retries.
     */
    public void retries(Collection<K> retries) {
        this.retries = retries;
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

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeCollection(out, retryBytes);

        assert futId != null;
        assert miniId != null;
        assert dhtVer != null;

        U.writeGridUuid(out, futId);
        U.writeGridUuid(out, miniId);

        CU.writeVersion(out, dhtVer);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        retryBytes = U.readList(in);

        futId = U.readGridUuid(in);
        miniId = U.readGridUuid(in);

        dhtVer = CU.readVersion(in);

        assert futId != null;
        assert miniId != null;
        assert dhtVer != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearTxPrepareResponse.class, this, "super", super.toString());
    }
}
