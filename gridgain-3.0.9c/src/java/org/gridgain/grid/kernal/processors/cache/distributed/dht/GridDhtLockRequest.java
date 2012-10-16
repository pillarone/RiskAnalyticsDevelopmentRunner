// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * DHT lock request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtLockRequest<K, V> extends GridDistributedLockRequest<K, V> {
    /** Near keys to lock. */
    @GridToStringExclude
    private List<byte[]> nearKeyBytes;

    /** Mini future ID. */
    private GridUuid miniId;

    /** Owner mapped version, if any. */
    @GridToStringInclude
    private Map<K, List<GridCacheVersion>> owned;

    /** Near keys. */
    @GridToStringInclude
    private transient List<K> nearKeys;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtLockRequest() {
        // No-op.
    }

    /**
     * @param nodeId Node ID.
     * @param threadId Thread ID.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     * @param lockVer Cache version.
     * @param isInTx {@code True} if implicit transaction lock.
     * @param isRead Indicates whether implicit lock is for read or write operation.
     * @param isolation Transaction isolation.
     * @param isInvalidate Invalidation flag.
     * @param timeout Lock timeout.
     * @param dhtCnt DHT count.
     * @param nearCnt Near count.
     */
    public GridDhtLockRequest(UUID nodeId, long threadId, GridUuid futId, GridUuid miniId, GridCacheVersion lockVer,
        boolean isInTx, boolean isRead, GridCacheTxIsolation isolation, boolean isInvalidate, long timeout,
        int dhtCnt, int nearCnt) {
        super(nodeId, threadId, futId, lockVer, isInTx, isRead, isolation, isInvalidate, timeout,
            dhtCnt == 0 ? nearCnt : dhtCnt);

        nearKeyBytes = nearCnt == 0 ? Collections.<byte[]>emptyList() : new ArrayList<byte[]>(nearCnt);
        nearKeys = nearCnt == 0 ? Collections.<K>emptyList() : new ArrayList<K>(nearCnt);

        assert miniId != null;

        this.miniId = miniId;
    }

    /**
     * @return Near node ID.
     */
    public UUID nearNodeId() {
        return nodeId();
    }

    /**
     * @return Near keys.
     */
    public List<byte[]> nearKeyBytes() {
        return nearKeyBytes == null ? Collections.<byte[]>emptyList() : nearKeyBytes;
    }

    /**
     * Adds a Near key.
     *
     * @param key Key.
     * @param keyBytes Key bytes.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    public void addNearKey(K key, byte[] keyBytes, GridCacheContext<K, V> ctx) throws GridException {
        prepareObject(key, ctx);

        nearKeys.add(key);
        nearKeyBytes.add(keyBytes);
    }

    /**
     * @return Near keys.
     */
    public List<K> nearKeys() {
        return nearKeys == null ? Collections.<K>emptyList() : nearKeys;
    }

    /**
     * Adds a DHT key.
     *
     * @param key Key.
     * @param keyBytes Key bytes.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    public void addDhtKey(K key, byte[] keyBytes, GridCacheContext<K, V> ctx) throws GridException {
        addKeyBytes(key, keyBytes, false, null, ctx);
    }

    /**
     * Sets owner and its mapped version.
     *
     * @param key Key.
     * @param owner Owner version.
     * @param ownerMapped Owner mapped version.
     */
    public void owned(K key, GridCacheVersion owner, GridCacheVersion ownerMapped) {
        if (owned == null)
            owned = new GridLeanMap<K, List<GridCacheVersion>>(3);

        owned.put(key, F.asList(owner, ownerMapped));
    }

    /**
     * @param key Key.
     * @return Owner and its mapped versions.
     */
    public Collection<GridCacheVersion> owned(K key) {
        Collection<GridCacheVersion> vers = owned == null ? null : owned.get(key);

        return vers == null ? Collections.<GridCacheVersion>emptyList() : vers;
    }

    /**
     * @return Mini ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (F.isEmpty(nearKeyBytes) && !F.isEmpty(nearKeys))
            nearKeyBytes = marshalCollection(nearKeys, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (nearKeys == null && nearKeyBytes != null)
            nearKeys = unmarshalCollection(nearKeyBytes, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        assert miniId != null;

        U.writeCollection(out, nearKeyBytes);
        U.writeMap(out, owned);
        U.writeGridUuid(out, miniId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        nearKeyBytes = U.readList(in);
        owned = U.readMap(in);
        miniId = U.readGridUuid(in);

        assert miniId != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtLockRequest.class, this, "nearKeyBytesSize", nearKeyBytes.size(),
             "super", super.toString());
    }
}
