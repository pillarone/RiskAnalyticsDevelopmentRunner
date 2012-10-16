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
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * DHT cache unlock request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtUnlockRequest<K, V> extends GridDistributedUnlockRequest<K, V> {
    /** Near keys. */
    private List<byte[]> nearKeyBytes;

    /** */
    private List<K> nearKeys;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridDhtUnlockRequest() {
        // No-op.
    }

    /**
     * @param dhtCnt Key count.
     */
    public GridDhtUnlockRequest(int dhtCnt) {
        super(dhtCnt);

        nearKeyBytes = new LinkedList<byte[]>();
    }

    /**
     * @return Near keys.
     */
    public List<byte[]> nearKeyBytes() {
        return nearKeyBytes;
    }

    /**
     * @return Near keys.
     */
    public List<K> nearKeys() {
        return nearKeys;
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

        nearKeyBytes.add(keyBytes);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        nearKeys = unmarshalCollection(nearKeyBytes, ctx, ldr);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        U.writeCollection(out, nearKeyBytes);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        nearKeyBytes = U.readList(in);
    }

    /**
     * {@inheritDoc}
     */
    @Override public String toString() {
        return S.toString(GridDhtUnlockRequest.class, this);
    }
}
