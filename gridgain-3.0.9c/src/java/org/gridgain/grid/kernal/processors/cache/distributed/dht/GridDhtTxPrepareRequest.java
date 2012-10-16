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

import java.io.*;
import java.util.*;

/**
 * DHT prepare request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtTxPrepareRequest<K, V> extends GridDistributedTxPrepareRequest<K, V> {
    /** Max order. */
    private UUID nearNodeId;

    /** Future ID. */
    private GridUuid futId;

    /** Mini future ID. */
    private GridUuid miniId;

    /** Near writes. */
    private Collection<GridCacheTxEntry<K, V>> nearWrites;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtTxPrepareRequest() {
        // No-op.
    }

    /**
     * @param futId Future ID.
     * @param miniId Mini future ID.
     * @param tx Transaction.
     * @param dhtWrites DHT writes.
     * @param nearWrites Near writes.
     */
    public GridDhtTxPrepareRequest(
        GridUuid futId,
        GridUuid miniId,
        GridDhtTxLocal<K, V> tx,
        Collection<GridCacheTxEntry<K, V>> dhtWrites,
        Collection<GridCacheTxEntry<K, V>> nearWrites
    ) {
        super(tx, null, dhtWrites);

        assert futId != null;
        assert miniId != null;

        this.futId = futId;
        this.nearWrites = nearWrites;
        this.miniId = miniId;

        nearNodeId = tx.nearNodeId();
    }

    /**
     * @return Maximum topology order allowed.
     */
    public UUID nearNodeId() {
        return nearNodeId;
    }

    /**
     * @return Near writes.
     */
    public Collection<GridCacheTxEntry<K, V>> nearWrites() {
        return nearWrites == null ? Collections.<GridCacheTxEntry<K, V>>emptyList() : nearWrites;
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

        marshalTx(nearWrites, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        unmarshalTx(nearWrites, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        assert futId != null;
        assert miniId != null;
        assert nearNodeId != null;

        U.writeUuid(out, nearNodeId);
        U.writeGridUuid(out, futId);
        U.writeGridUuid(out, miniId);
        U.writeCollection(out, nearWrites);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        nearNodeId = U.readUuid(in);
        futId = U.readGridUuid(in);
        miniId = U.readGridUuid(in);
        nearWrites = U.readCollection(in);

        assert futId != null;
        assert miniId != null;
        assert nearNodeId != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtTxPrepareRequest.class, this,
            "super", super.toString());
    }
}
