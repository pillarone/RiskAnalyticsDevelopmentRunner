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
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Reply for synchronous phase 2.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearTxFinishResponse<K, V> extends GridDistributedTxFinishResponse<K, V> {
    /** Retries. */
    private Collection<K> retries;

    /** Retry bytes. */
    private Collection<byte[]> retryBytes;

    /** Heuristic error. */
    private Throwable err;

    /** Mini future ID. */
    private GridUuid miniId;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridNearTxFinishResponse() {
        // No-op.
    }

    /**
     * @param xid Xid version.
     * @param futId Future ID.
     * @param miniId Mini future Id.
     * @param err Error.
     */
    public GridNearTxFinishResponse(GridCacheVersion xid, GridUuid futId, GridUuid miniId, @Nullable Throwable err) {
        super(xid, futId);

        assert miniId != null;

        this.miniId = miniId;
        this.err = err;
    }

    /**
     * @return Error.
     */
    @Nullable public Throwable error() {
        return err;
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

    /**
     * @return Mini future ID.
     */
    public GridUuid miniId() {
        return miniId;
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

        assert miniId != null;

        U.writeCollection(out, retryBytes);
        U.writeGridUuid(out, miniId);

        out.writeObject(err);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        retryBytes = U.readList(in);
        miniId = U.readGridUuid(in);

        err = (Throwable)in.readObject();

        assert miniId != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearTxFinishResponse.class, this, "super", super.toString());
    }
}
