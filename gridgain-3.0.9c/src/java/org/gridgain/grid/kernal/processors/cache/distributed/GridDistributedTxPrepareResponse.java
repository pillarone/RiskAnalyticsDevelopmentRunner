// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Response to prepare request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDistributedTxPrepareResponse<K, V> extends GridDistributedBaseMessage<K, V> {
    /** Collections of local lock candidates. */
    @GridToStringInclude
    private Map<K, Collection<GridCacheMvccCandidate<K>>> cands;

    /** */
    private byte[] candsBytes;

    /** Error. */
    @GridToStringExclude
    private Throwable err;

    /**
     * Empty constructor (required by {@link Externalizable}).
     */
    public GridDistributedTxPrepareResponse() {
        /* No-op. */
    }

    /**
     * @param xid Transaction ID.
     */
    public GridDistributedTxPrepareResponse(GridCacheVersion xid) {
        super(xid, 0);
    }

    /**
     * @param xid Lock ID.
     * @param err Error.
     */
    public GridDistributedTxPrepareResponse(GridCacheVersion xid, Throwable err) {
        super(xid, 0);

        this.err = err;
    }

    /**
     *
     * @return Lock ID.
     */
    public UUID xid() {
        return ver.id();
    }

    /**
     * @return Error.
     */
    public Throwable error() {
        return err;
    }

    /**
     * @param err Error to set.
     */
    public void error(Throwable err) {
        this.err = err;
    }

    /**
     * @return Rollback flag.
     */
    public boolean isRollback() {
        return err != null;
    }

    /**
     * @param key Candidates key.
     * @param cands Collection of local candidates.
     */
    public void candidates(K key, Collection<GridCacheMvccCandidate<K>> cands) {
        if (this.cands == null)
            this.cands = new HashMap<K, Collection<GridCacheMvccCandidate<K>>>(1, 1.0f);

        this.cands.put(key, cands);
    }

    /**
     * @param cands Candidates map to set.
     */
    public void candidates(Map<K, Collection<GridCacheMvccCandidate<K>>> cands) {
        this.cands = cands;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (candsBytes == null && cands != null) {
            for (K k : cands.keySet())
                prepareObject(k, ctx);

            candsBytes = CU.marshal(ctx, cands).getEntireArray();
        }
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (candsBytes != null && cands == null)
            cands = U.unmarshal(ctx.marshaller(), new GridByteArrayList(candsBytes), ldr);
    }

    /**
     *
     * @param key Candidates key.
     * @return Collection of lock candidates at given index.
     */
    @Nullable public Collection<GridCacheMvccCandidate<K>> candidatesForKey(K key) {
        assert key != null;

        if (cands == null)
            return null;

        return cands.get(key);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeObject(err);

        if (err == null) {
            U.writeByteArray(out, candsBytes);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        err = (Throwable)in.readObject();

        if (err == null)
            candsBytes = U.readByteArray(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridDistributedTxPrepareResponse.class, this, "err",
            err == null ? "" : err.toString(), "super", super.toString());
    }
}