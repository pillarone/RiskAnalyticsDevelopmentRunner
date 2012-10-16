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
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Base for all messages in replicated cache.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridDistributedBaseMessage<K, V> extends GridCacheMessage<K, V> implements GridCacheDeployable,
    GridCacheVersionable {
    /** Lock or transaction version. */
    @GridToStringInclude
    protected GridCacheVersion ver;

    /**
     * Candidates for every key ordered in the order of keys. These
     * can be either local-only candidates in case of lock acquisition,
     * or pending candidates in case of transaction commit.
     */
    @GridToStringInclude
    private Collection<GridCacheMvccCandidate<K>>[] candsByIdx;

    /** Collections of local lock candidates. */
    @GridToStringInclude
    private Map<K, Collection<GridCacheMvccCandidate<K>>> candsByKey;

    /** Collections of local lock candidates in serialized form. */
    @GridToStringInclude
    private byte[] candsByKeyBytes;

    /** Committed versions with order higher than one for this message (needed for commit ordering). */
    @GridToStringInclude
    private Collection<GridCacheVersion> committedVers;

    /** Rolled back versions with order higher than one for this message (needed for commit ordering). */
    @GridToStringInclude
    private Collection<GridCacheVersion> rolledbackVers;

    /** Count of keys referenced in candidates array (needed only locally for optimization). */
    @GridToStringInclude
    private transient int cnt;

    /**
     * Empty constructor required by {@link Externalizable}
     */
    protected GridDistributedBaseMessage() {
        /* No-op. */
    }

    /**
     * @param cnt Count of keys references in list of candidates.
     */
    protected GridDistributedBaseMessage(int cnt) {
        assert cnt >= 0;

        this.cnt = cnt;
    }

    /**
     * @param ver Either lock or transaction version.
     * @param cnt Key count.
     */
    protected GridDistributedBaseMessage(GridCacheVersion ver, int cnt) {
        this(cnt);

        assert ver != null;

        this.ver = ver;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridCacheContext<K, V> ctx) throws GridException {
        super.p2pMarshal(ctx);

        if (candsByKey != null && candsByKeyBytes == null) {
            for (K key : candsByKey.keySet())
                prepareObject(key, ctx);

            candsByKeyBytes = CU.marshal(ctx, candsByKey).getEntireArray();
        }
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(GridCacheContext<K, V> ctx, ClassLoader ldr) throws GridException {
        super.p2pUnmarshal(ctx, ldr);

        if (candsByKeyBytes != null && candsByKey == null)
            candsByKey = U.unmarshal(ctx.marshaller(), new GridByteArrayList(candsByKeyBytes), ldr);
    }

    /**
     * @return Version.
     */
    @Override public GridCacheVersion version() {
        return ver;
    }

    /**
     * @param ver Version.
     */
    public void version(GridCacheVersion ver) {
        this.ver = ver;
    }

    /**
     * @param committedVers Committed versions.
     * @param rolledbackVers Rolled back versions.
     */
    public void completedVersions(Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers) {
        this.committedVers = committedVers;
        this.rolledbackVers = rolledbackVers;
    }

    /**
     * @return Committed versions.
     */
    public Collection<GridCacheVersion> committedVersions() {
        return committedVers == null ? Collections.<GridCacheVersion>emptyList() : committedVers;
    }

    /**
     * @return Rolled back versions.
     */
    public Collection<GridCacheVersion> rolledbackVersions() {
        return rolledbackVers == null ? Collections.<GridCacheVersion>emptyList() : rolledbackVers;
    }

    /**
     * @param idx Key index.
     * @param candsByIdx List of candidates for that key.
     */
    @SuppressWarnings({"unchecked"})
    public void candidatesByIndex(int idx, Collection<GridCacheMvccCandidate<K>> candsByIdx) {
        assert idx < cnt;

        // If nothing to add.
        if (candsByIdx == null || candsByIdx.isEmpty()) {
            return;
        }

        if (this.candsByIdx == null) {
            this.candsByIdx = new Collection[cnt];
        }

        this.candsByIdx[idx] = candsByIdx;
    }

    /**
     * @param idx Key index.
     * @return Candidates for given key.
     */
    public Collection<GridCacheMvccCandidate<K>> candidatesByIndex(int idx) {
        return candsByIdx == null || candsByIdx[idx] == null ? Collections.<GridCacheMvccCandidate<K>>emptyList() : candsByIdx[idx];
    }

    /**
     * @param key Candidates key.
     * @param candsByKey Collection of local candidates.
     */
    public void candidatesByKey(K key, Collection<GridCacheMvccCandidate<K>> candsByKey) {
        if (this.candsByKey == null) {
            this.candsByKey = new HashMap<K, Collection<GridCacheMvccCandidate<K>>>(1, 1.0f);
        }

        this.candsByKey.put(key, candsByKey);
    }

    /**
     *
     * @param key Candidates key.
     * @return Collection of lock candidates at given index.
     */
    @Nullable public Collection<GridCacheMvccCandidate<K>> candidatesByKey(K key) {
        assert key != null;

        if (candsByKey == null) {
            return null;
        }

        return candsByKey.get(key);
    }

    /**
     * @return Map of candidates.
     */
    public Map<K, Collection<GridCacheMvccCandidate<K>>> candidatesByKey() {
        return candsByKey == null ? Collections.<K, Collection<GridCacheMvccCandidate<K>>>emptyMap() : candsByKey;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        CU.writeVersion(out, ver);

        // Serialize candidates by index.
        out.writeInt(candsByIdx == null ? 0 : candsByIdx.length); // Size1

        if (candsByIdx != null) {
            for (Collection<GridCacheMvccCandidate<K>> cands : candsByIdx) {
                U.writeCollection(out, cands);
            }
        }

        // Serialize candidates by key.
        U.writeByteArray(out, candsByKeyBytes);

        U.writeCollection(out, F.isEmpty(committedVers) ? null : committedVers);
        U.writeCollection(out, F.isEmpty(rolledbackVers) ? null : rolledbackVers);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        ver = CU.readVersion(in);

        // Deserialize candidates by index.
        int size = in.readInt();

        if (size > 0) {
            candsByIdx = new Collection[size];

            for (int i = 0; i < size; i++) {
                candsByIdx[i] = U.readCollection(in);
            }
        }

        // Deserialize candidates by key.
        candsByKeyBytes = U.readByteArray(in);

        committedVers = U.readCollection(in);
        rolledbackVers = U.readCollection(in);

        if (committedVers == null) {
            committedVers = Collections.emptyList();
        }

        if (rolledbackVers == null) {
            rolledbackVers = Collections.emptyList();
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDistributedBaseMessage.class, this);
    }
}