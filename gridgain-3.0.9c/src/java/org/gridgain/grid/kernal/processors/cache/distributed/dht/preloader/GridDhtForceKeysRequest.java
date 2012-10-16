// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Force keys request. This message is sent by node while preloading to force
 * another node to put given keys into the next batch of transmitting entries.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtForceKeysRequest<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable {
    /** Future ID. */
    private GridUuid futId;

    /** Mini-future ID. */
    private GridUuid miniId;

    /** Serialized keys. */
    private Collection<byte[]> keyBytes;

    /** Keys to request. */
    @GridToStringInclude
    private transient Collection<K> keys;

    /**
     * @param futId Future ID.
     * @param miniId Mini-future ID.
     * @param keys Keys.
     */
    GridDhtForceKeysRequest(GridUuid futId, GridUuid miniId, Collection<K> keys) {
        assert futId != null;
        assert miniId != null;
        assert !F.isEmpty(keys);

        this.futId = futId;
        this.miniId = miniId;
        this.keys = keys;
    }

    /**
     * Required by {@link Externalizable}.
     */
    public GridDhtForceKeysRequest() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public boolean isPreloaderMessage() {
        return true;
    }

    /**
     * @param keys Collection of keys.
     */
    public GridDhtForceKeysRequest(Collection<K> keys) {
        assert !F.isEmpty(keys);

        this.keys = keys;
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    /**
     * @return Mini-future ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /**
     * @return Collection of serialized keys.
     */
    public Collection<byte[]> keyBytes() {
        return keyBytes;
    }

    /**
     * @return Keys.
     */
    public Collection<K> keys() {
        return keys;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        keyBytes = marshalCollection(keys, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        keys = unmarshalCollection(keyBytes, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeGridUuid(out, futId);
        U.writeGridUuid(out, miniId);
        U.writeCollection(out, keyBytes);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        futId = U.readGridUuid(in);
        miniId = U.readGridUuid(in);
        keyBytes = U.readList(in);
    }

    /**
     * @return Key count.
     */
    private int keyCount() {
        return keyBytes == null ? keys.size() : keyBytes.size();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtForceKeysRequest.class, this, "keyCnt", keyCount(), "super", super.toString());
    }
}
