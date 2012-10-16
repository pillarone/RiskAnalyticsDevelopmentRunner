// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * DHT transaction prepare response.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtTxPrepareResponse<K, V> extends GridDistributedTxPrepareResponse<K, V> {
    /** Evicted partitions. */
    @GridToStringInclude
    private int[] dhtEvicted;

    /** Evicted readers. */
    @GridToStringInclude
    private Collection<K> nearEvicted;

    /** */
    private Collection<byte[]> nearEvictedBytes;

    /** Future ID.  */
    private GridUuid futId;

    /** Mini future ID. */
    private GridUuid miniId;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridDhtTxPrepareResponse() {
        // No-op.
    }

    /**
     * @param xid Xid version.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     */
    public GridDhtTxPrepareResponse(GridCacheVersion xid, GridUuid futId, GridUuid miniId) {
        super(xid);

        assert futId != null;
        assert miniId != null;

        this.futId = futId;
        this.miniId = miniId;
    }

    /**
     * @param xid Xid version.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     * @param err Error.
     */
    public GridDhtTxPrepareResponse(GridCacheVersion xid, GridUuid futId, GridUuid miniId, Throwable err) {
        super(xid, err);

        assert futId != null;
        assert miniId != null;

        this.futId = futId;
        this.miniId = miniId;
    }

    /**
     * @return Retries.
     */
    public int[] dhtEvicted() {
        return dhtEvicted;
    }

    /**
     * @param dhtEvicted Evicted partitions.
     */
    public void dhtEvicted(int[] dhtEvicted) {
        this.dhtEvicted = dhtEvicted;
    }

    /**
     * @return Evicted readers.
     */
    public Collection<K> nearEvicted() {
        return nearEvicted;
    }

    /**
     * @param nearEvicted Evicted readers.
     */
    public void nearEvicted(Collection<K> nearEvicted) {
        this.nearEvicted = nearEvicted;
    }

    /**
     * @param nearEvictedBytes Near evicted bytes.
     */
    public void nearEvictedBytes(Collection<byte[]> nearEvictedBytes) {
        this.nearEvictedBytes = nearEvictedBytes;
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
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

        if (nearEvictedBytes != null)
            nearEvictedBytes = marshalCollection(nearEvicted, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (nearEvicted == null)
            nearEvicted = unmarshalCollection(nearEvictedBytes, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeObject(dhtEvicted);

        U.writeCollection(out, nearEvictedBytes);

        assert futId != null;
        assert miniId != null;

        U.writeGridUuid(out, futId);
        U.writeGridUuid(out, miniId);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        dhtEvicted = (int[])in.readObject();

        nearEvictedBytes = U.readCollection(in);

        futId = U.readGridUuid(in);
        miniId = U.readGridUuid(in);

        assert futId != null;
        assert miniId != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtTxPrepareResponse.class, this, "super", super.toString());
    }
}
