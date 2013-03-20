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
 * Response to initial preload request expressed by {@link GridReplicatedPreloadRequest}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridReplicatedPreloadResponse<K, V> extends GridCacheMessage<K, V> {
    /** */
    private int mod;

    /** */
    private boolean failed;

    /**
     * Required by {@link Externalizable}.
     */
    public GridReplicatedPreloadResponse() {
        /* No-op. */
    }

    /**
     * @param mod Mod to use.
     * @param failed Failed or not.
     */
    public GridReplicatedPreloadResponse(int mod, boolean failed) {
        this.mod = mod;
        this.failed = failed;
    }

    /**
     * @return Mod to use.
     */
    public int mod() {
        return mod;
    }

    /**
     * @return {@code true} if node failed to process preload request.
     */
    public boolean failed() {
        return failed;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(mod);
        out.writeBoolean(failed);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        mod = in.readInt();
        failed = in.readBoolean();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridReplicatedPreloadResponse.class, this);
    }
}