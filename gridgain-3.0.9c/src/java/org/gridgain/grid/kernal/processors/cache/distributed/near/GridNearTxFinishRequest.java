// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Near transaction finish request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearTxFinishRequest<K, V> extends GridDistributedTxFinishRequest<K, V> {
    /** Mini future ID. */
    private GridUuid miniId;

    /** Explicit lock flag. */
    private boolean explicitLock;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearTxFinishRequest() {
        // No-op.
    }

    /**
     * @param futId Future ID.
     * @param xidVer Transaction ID.
     * @param threadId Thread ID.
     * @param commitVer Commit version.
     * @param commit Commit flag.
     * @param invalidate Invalidate flag.
     * @param explicitLock Explicit lock flag.
     * @param baseVer Base version.
     * @param committedVers Committed versions.
     * @param rolledbackVers Rolled back versions.
     * @param writeEntries Write entries.
     * @param reply Reply flag.
     */
    public GridNearTxFinishRequest(
        GridUuid futId,
        GridCacheVersion xidVer,
        GridCacheVersion commitVer,
        long threadId,
        boolean commit,
        boolean invalidate,
        boolean explicitLock,
        GridCacheVersion baseVer, Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers,
        Collection<GridCacheTxEntry<K, V>> writeEntries,
        boolean reply) {
        super(xidVer, futId, commitVer, threadId, commit, invalidate, baseVer, committedVers, rolledbackVers,
            writeEntries, reply);

        this.explicitLock = explicitLock;
    }

    /**
     * @return Explicit lock flag.
     */
    public boolean explicitLock() {
        return explicitLock;
    }

    /**
     * @return Mini future ID.
     */
    public GridUuid miniId() {
        return miniId;
    }

    /**
     * @param miniId Mini future ID.
     */
    public void miniId(GridUuid miniId) {
        this.miniId = miniId;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeBoolean(explicitLock);

        assert miniId != null;

        U.writeGridUuid(out, miniId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        explicitLock = in.readBoolean();

        miniId = U.readGridUuid(in);

        assert miniId != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridNearTxFinishRequest.class, this, "super", super.toString());
    }
}
