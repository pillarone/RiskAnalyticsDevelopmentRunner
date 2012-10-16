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
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Transaction completion message.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDistributedTxFinishRequest<K, V> extends GridDistributedBaseMessage<K, V> {
    /** Future ID. */
    private GridUuid futId;

    /** Thread ID. */
    private long threadId;

    /** Commit version. */
    private GridCacheVersion commitVer;

    /** Invalidate flag. */
    private boolean invalidate;

    /** Commit flag. */
    private boolean commit;

    /** Min version used as base for completed versions. */
    private GridCacheVersion baseVer;

    /** Transaction write entries. */
    @GridToStringInclude
    private Collection<GridCacheTxEntry<K, V>> writeEntries;

    /** */
    private boolean reply;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridDistributedTxFinishRequest() {
        /* No-op. */
    }

    /**
     * @param xidVer Transaction ID.
     * @param futId future ID.
     * @param threadId Thread ID.
     * @param commitVer Commit version.
     * @param commit Commit flag.
     * @param invalidate Invalidate flag.
     * @param baseVer Base version.
     * @param committedVers Committed versions.
     * @param rolledbackVers Rolled back versions.
     * @param writeEntries Write entries.
     * @param reply Reply flag.
     */
    public GridDistributedTxFinishRequest(
        GridCacheVersion xidVer,
        GridUuid futId,
        GridCacheVersion commitVer,
        long threadId,
        boolean commit,
        boolean invalidate,
        GridCacheVersion baseVer,
        Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers,
        Collection<GridCacheTxEntry<K, V>> writeEntries,
        boolean reply
    ) {
        super(xidVer, writeEntries == null ? 0 : writeEntries.size());
        assert xidVer != null;

        this.futId = futId;
        this.commitVer = commitVer;
        this.threadId = threadId;
        this.commit = commit;
        this.invalidate = invalidate;
        this.baseVer = baseVer;
        this.writeEntries = writeEntries;
        this.reply = reply;

        completedVersions(committedVers, rolledbackVers);
    }

    /**
     * @return Transaction ID.
     */
    public UUID xid() {
        return version().id();
    }

    /**
     * @return Future ID.
     */
    public GridUuid futureId() {
        return futId;
    }

    /**
     * @return Thread ID.
     */
    public long threadId() {
        return threadId;
    }

    /**
     * @return Commit version.
     */
    public GridCacheVersion commitVersion() {
        return commitVer;
    }

    /**
     * @return Commit flag.
     */
    public boolean commit() {
        return commit;
    }

    /**
     *
     * @return Invalidate flag.
     */
    public boolean isInvalidate() {
        return invalidate;
    }

    /**
     * @return Base version.
     */
    public GridCacheVersion baseVersion() {
        return baseVer;
    }

    /**
     * @return Write entries.
     */
    public Collection<GridCacheTxEntry<K, V>> writes() {
        return writeEntries;
    }

    /**
     *
     * @return {@code True} if reply is required.
     */
    public boolean replyRequired() {
        return reply;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        marshalTx(writeEntries, ctx);
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        unmarshalTx(writeEntries, ctx, ldr);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeLong(threadId);
        out.writeBoolean(commit);
        out.writeBoolean(invalidate);
        out.writeBoolean(reply);

        U.writeGridUuid(out, futId);
        CU.writeVersion(out, commitVer);
        CU.writeVersion(out, baseVer);

        U.writeCollection(out, writeEntries);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        threadId = in.readLong();
        commit = in.readBoolean();
        invalidate = in.readBoolean();
        reply = in.readBoolean();

        futId = U.readGridUuid(in);
        commitVer = CU.readVersion(in);
        baseVer = CU.readVersion(in);

        writeEntries = U.readList(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridDistributedTxFinishRequest.class, this,
            "super", super.toString());
    }
}