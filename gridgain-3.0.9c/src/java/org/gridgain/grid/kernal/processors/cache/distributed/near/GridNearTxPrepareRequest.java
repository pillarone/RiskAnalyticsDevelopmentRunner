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
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Near transaction prepare request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearTxPrepareRequest<K, V> extends GridDistributedTxPrepareRequest<K, V> {
    /** Future ID. */
    private GridUuid futId;

    /** Mini future ID. */
    private GridUuid miniId;

    /** Synchronous commit flag. */
    private boolean syncCommit;

    /** Synchronous rollback flag. */
    private boolean syncRollback;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridNearTxPrepareRequest() {
        // No-op.
    }

    /**
     * @param futId Future ID.
     * @param tx Transaction.
     * @param reads Read entries.
     * @param writes Write entries.
     * @param syncCommit Synchronous commit.
     * @param syncRollback Synchronous rollback.
     */
    public GridNearTxPrepareRequest(GridUuid futId, GridCacheTxEx<K, V> tx,
        Collection<GridCacheTxEntry<K, V>> reads, Collection<GridCacheTxEntry<K, V>> writes,
        boolean syncCommit, boolean syncRollback) {
        super(tx, reads, writes);

        assert futId != null;

        this.futId = futId;
        this.syncCommit = syncCommit;
        this.syncRollback = syncRollback;
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

    /**
     * @param miniId Mini future ID.
     */
    public void miniId(GridUuid miniId) {
        this.miniId = miniId;
    }

    /**
     * @return Synchronous commit.
     */
    public boolean syncCommit() {
        return syncCommit;
    }

    /**
     * @return Synchronous rollback.
     */
    public boolean syncRollback() {
        return syncRollback;
    }

    /**
     * @param ctx Cache context.
     */
    void cloneEntries(GridCacheContext<K, V> ctx) {
        reads(cloneEntries(ctx, reads()));
        writes(cloneEntries(ctx, writes()));
    }

    /**
     * @param ctx Cache context.
     * @param c Collection of entries to clone.
     * @return Cloned collection.
     */
    private Collection<GridCacheTxEntry<K, V>> cloneEntries(GridCacheContext<K, V> ctx,
        Collection<GridCacheTxEntry<K, V>> c) {
        if (F.isEmpty(c))
            return c;

        Collection<GridCacheTxEntry<K, V>> cp = new ArrayList<GridCacheTxEntry<K, V>>(c.size());

        for (GridCacheTxEntry<K, V> e : c)
            cp.add(e.cleanCopy(ctx));

        return cp;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        assert futId != null;
        assert miniId != null;

        U.writeGridUuid(out, futId);
        U.writeGridUuid(out, miniId);

        out.writeBoolean(syncCommit);
        out.writeBoolean(syncRollback);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        futId = U.readGridUuid(in);
        miniId = U.readGridUuid(in);

        assert futId != null;
        assert miniId != null;

        syncCommit = in.readBoolean();
        syncRollback = in.readBoolean();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearTxPrepareRequest.class, this, super.toString());
    }
}
