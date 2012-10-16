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
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Lock request message.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDistributedUnlockRequest<K, V> extends GridDistributedBaseMessage<K, V> {
    /** Keys to unlock. */
    @GridToStringInclude
    private List<byte[]> keyBytes;

    /** Keys. */
    private List<K> keys;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridDistributedUnlockRequest() {
        /* No-op. */
    }

    /**
     * @param keyCnt Key count.
     */
    public GridDistributedUnlockRequest(int keyCnt) {
        super(keyCnt);

        keyBytes = new LinkedList<byte[]>();
    }

    /**
     * @return Key to lock.
     */
    public List<byte[]> keyBytes() {
        return keyBytes;
    }

    /**
     * @return Keys.
     */
    public List<K> keys() {
        return keys;
    }

    /**
     * @param key Key.
     * @param bytes Key bytes.
     * @param ctx Context.
     * @throws GridException If failed.
     */
    public void addKey(K key, byte[] bytes, GridCacheContext<K, V> ctx) throws GridException {
        prepareObject(key, ctx);

        keyBytes.add(bytes);
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (F.isEmpty(keyBytes) && !F.isEmpty(keys))
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

        out.writeInt(keyBytes.size());

        for (byte[] key : keyBytes)
            GridUtils.writeByteArray(out, key);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        int size = in.readInt();

        keyBytes = new ArrayList<byte[]>(size);

        for (int i = 0; i < size; i++)
            keyBytes.add(GridUtils.readByteArray(in));
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDistributedUnlockRequest.class, this, "super", super.toString());
    }
}