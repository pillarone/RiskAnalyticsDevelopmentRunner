// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.replicated;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.internal.*;
import java.io.*;

/**
 * Initial request for preload data from a remote node.
 * Once the remote node has received this request it starts sending
 * cache entries split into batches.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridReplicatedPreloadRequest<K, V> extends GridCacheMessage<K, V> {
    /** Partition. */
    private int part;

    /** */
    private int mod;

    /** */
    private int cnt;

    /**
     * Required by {@link Externalizable}.
     */
    public GridReplicatedPreloadRequest() {
        // No-op.
    }

    /**
     * @param part Partition.
     * @param mod Mod to use.
     * @param cnt Size to use.
     */
    public GridReplicatedPreloadRequest(int part, int mod, int cnt) {
        this.part = part;
        this.mod = mod;
        this.cnt = cnt;
    }

    /**
     * @return Partition.
     */
    public int partition() {
        return part;
    }

    /**
     *
     * @return Mod to use for key selection.
     */
    public int mod() {
        return mod;
    }

    /**
     *
     * @return Number to use as a denominator when calculating a mod.
     */
    public int nodeCount() {
        return cnt;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(part);
        out.writeInt(mod);
        out.writeInt(cnt);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        
        part = in.readInt();
        mod = in.readInt();
        cnt = in.readInt();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridReplicatedPreloadRequest.class, this);
    }
}