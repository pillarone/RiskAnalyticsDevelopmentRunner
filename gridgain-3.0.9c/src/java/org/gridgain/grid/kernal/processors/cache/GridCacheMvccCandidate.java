// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.kernal.processors.cache.GridCacheMvccCandidate.Mask.*;

/**
 * Lock candidate.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheMvccCandidate<K> implements Externalizable, Comparable<GridCacheMvccCandidate<K>> {
    /** Locking node ID. */
    @GridToStringInclude
    private UUID nodeId;

    /** Lock version. */
    @GridToStringInclude
    private GridCacheVersion ver;

    /** Maximum wait time. */
    @GridToStringInclude
    private long timeout;

    /** Candidate timestamp. */
    @GridToStringInclude
    private long timestamp;

    /** Thread ID. */
    @GridToStringInclude
    private long threadId;

    /** Use flags approach to preserve space. */
    @GridToStringExclude
    private short flags;

    /** Linked reentry. */
    private GridCacheMvccCandidate<K> reentry;

    /** Previous lock for the thread. */
    @GridToStringExclude
    private transient GridCacheMvccCandidate<K> prev;

    /** Next lock for the thread. */
    @GridToStringExclude
    private transient GridCacheMvccCandidate<K> next;

    /** Parent entry. */
    @GridToStringExclude
    private transient GridCacheEntryEx<K, ?> parent;

    /** Alternate node ID specifying additional node involved in this lock. */
    private transient UUID otherNodeId;

    /** Other lock version (near version vs dht version). */
    private transient GridCacheVersion otherVer;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridCacheMvccCandidate() {
        /* No-op. */
    }

    /**
     * @param parent Parent entry.
     * @param nodeId Requesting node ID.
     * @param otherNodeId Near node ID.
     * @param otherVer Other version.
     * @param threadId Requesting thread ID.
     * @param ver Cache version.
     * @param timeout Maximum wait time.
     * @param loc {@code True} if the lock is local.
     * @param reentry {@code True} if candidate is for reentry.
     * @param ec EC flag.
     * @param tx Transaction flag.
     * @param nearLocal Near-local flag.
     * @param dhtLocal DHT local flag.
     */
    GridCacheMvccCandidate(GridCacheEntryEx<K, ?> parent, UUID nodeId, @Nullable UUID otherNodeId,
        @Nullable GridCacheVersion otherVer, long threadId, GridCacheVersion ver, long timeout, boolean loc,
        boolean reentry, boolean ec, boolean tx, boolean nearLocal, boolean dhtLocal) {
        assert nodeId != null;
        assert ver != null;
        assert parent != null;

        this.parent = parent;
        this.nodeId = nodeId;
        this.otherNodeId = otherNodeId;
        this.otherVer = otherVer;
        this.threadId = threadId;
        this.ver = ver;
        this.timeout = timeout;

        mask(LOCAL, loc);
        mask(REENTRY, reentry);
        mask(EC, ec);
        mask(TX, tx);
        mask(NEAR_LOCAL, nearLocal);
        mask(DHT_LOCAL, dhtLocal);

        timestamp = System.currentTimeMillis();
    }

    /**
     * Sets mask value.
     *
     * @param mask Mask.
     * @param on Flag.
     */
    private synchronized void mask(Mask mask, boolean on) {
        flags = mask.set(flags, on);
    }

    /**
     * @return Flags.
     */
    public synchronized short flags() {
        return flags;
    }

    /**
     * @return Parent entry.
     */
    @SuppressWarnings({"unchecked"})
    public <V> GridCacheEntryEx<K, V> parent() {
        return (GridCacheEntryEx<K, V>)parent;
    }

    /**
     * @return Reentry candidate.
     */
    public GridCacheMvccCandidate<K> reenter() {
        GridCacheMvccCandidate<K> old = reentry;

        GridCacheMvccCandidate<K> reentry = new GridCacheMvccCandidate<K>(parent, nodeId, otherNodeId, otherVer,
            threadId, ver, timeout, local(), /*reentry*/true, ec(), tx(), nearLocal(), dhtLocal());

        if (old != null)
            reentry.reentry = old;

        this.reentry = reentry;

        return reentry;
    }

    /**
     * @return {@code True} if has reentry.
     */
    public boolean hasReentry() {
        return reentry != null;
    }

    /**
     * @return Removed reentry candidate or {@code null}.
     */
    @Nullable public GridCacheMvccCandidate<K> unenter() {
        if (reentry != null) {
            GridCacheMvccCandidate<K> old = reentry;

            // Link to next.
            reentry = reentry.reentry;

            return old;
        }

        return null;
    }

    /**
     * @param parent Sets locks parent entry.
     */
    public void parent(GridCacheEntryEx<K, ?> parent) {
        assert parent != null;

        this.parent = parent;
    }

    /**
     * @return Node ID.
     */
    public UUID nodeId() {
        return nodeId;
    }

    /**
     * @return Near node ID.
     */
    public UUID otherNodeId() {
        return otherNodeId;
    }

    /**
     * @return Near version.
     */
    public GridCacheVersion otherVersion() {
        return otherVer;
    }

    /**
     * @return Thread ID.
     * @see Thread#getId()
     */
    public long threadId() {
        return threadId;
    }

    /**
     * @return Lock version.
     */
    public GridCacheVersion version() {
        return ver;
    }

    /**
     * @return Gets unique lock identifier.
     */
    public UUID id() {
        return ver.id();
    }

    /**
     * @return Maximum wait time.
     */
    public long timeout() {
        return timeout;
    }

    /**
     * @return Timestamp at the time of entering pending set.
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * @return {@code True} if lock is local.
     */
    public boolean local() {
        return LOCAL.get(flags());
    }

    /**
     * @return {@code True} if EC flag is set.
     */
    public boolean ec() {
        return EC.get(flags());
    }

    /**
     * @return {@code True} if transaction flag is set.
     */
    public boolean tx() {
        return TX.get(flags());
    }

    /**
     * @return Near local flag.
     */
    public boolean nearLocal() {
        return NEAR_LOCAL.get(flags());
    }

    /**
     * @return Near local flag.
     */
    public boolean dhtLocal() {
        return DHT_LOCAL.get(flags());
    }

    /**
     * @return {@code True} if this candidate is a reentry.
     */
    public boolean reentry() {
        return REENTRY.get(flags());
    }

    /**
     * Sets reentry flag.
     */
    public void setReentry() {
        mask(REENTRY, true);
    }

    /**
     * @return Ready flag.
     */
    public boolean ready() {
        return READY.get(flags());
    }

    /**
     * Sets ready flag.
     */
    public void setReady() {
        mask(READY, true);
    }

    /**
     * @return {@code True} if lock was released.
     */
    public boolean used() {
        return USED.get(flags());
    }

    /**
     * Sets used flag.
     */
    public void setUsed() {
        mask(USED, true);
    }

    /**
     * @return {@code True} if is or was an owner.
     */
    public boolean owner() {
        return OWNER.get(flags());
    }

    /**
     * Sets owner flag.
     */
    public void setOwner() {
        mask(OWNER, true);
    }

    /**
     * @return Lock that comes before in the same thread, possibly <tt>null</tt>.
     */
    public synchronized GridCacheMvccCandidate<K> previous() {
        return prev;
    }

    /**
     * @param prev Lock that comes before in the same thread, possibly <tt>null</tt>.
     */
    public synchronized void previous(GridCacheMvccCandidate<K> prev) {
        this.prev = prev;
    }

    /**
     *
     * @return Gets next candidate in this thread.
     */
    public synchronized GridCacheMvccCandidate<K> next() {
        return next;
    }

    /**
     * @param next Next candidate in this thread.
     */
    public synchronized void next(GridCacheMvccCandidate<K> next) {
        this.next = next;
    }

    /**
     * @return Key.
     */
    public K key() {
        return parent.key();
    }

    /**
     * Checks if this candidate matches version or thread-nodeId combination.
     *
     * @param nodeId Node ID to check.
     * @param ver Version to check.
     * @param threadId Thread ID to check.
     * @return {@code True} if matched.
     */
    public boolean matches(GridCacheVersion ver, UUID nodeId, long threadId) {
        return ver.equals(this.ver) || (nodeId.equals(this.nodeId) && threadId == this.threadId);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        GridUtils.writeUuid(out, nodeId);

        CU.writeVersion(out, ver);

        out.writeLong(timeout);
        out.writeLong(threadId);
        out.writeShort(flags());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        nodeId = GridUtils.readUuid(in);

        ver = CU.readVersion(in);

        timeout = in.readLong();
        threadId = in.readLong();

        short flags = in.readShort();

        mask(OWNER, OWNER.get(flags));
        mask(USED, USED.get(flags));
        mask(EC, EC.get(flags));
        mask(TX, TX.get(flags));

        timestamp = System.currentTimeMillis();
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridCacheMvccCandidate<K> o) {
        return ver.compareTo(o.ver);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public boolean equals(Object o) {
        if (o == null)
            return false;

        if (o == this)
            return true;

        GridCacheMvccCandidate<K> other = (GridCacheMvccCandidate<K>)o;

        K k1 = key();
        K k2 = other.key();

        if (k1 != null && k2 != null)
            return ver.equals(other.ver) && k1.equals(k2);

        return ver.equals(other.ver);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return ver.hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        GridCacheMvccCandidate<?> prev = previous();
        GridCacheMvccCandidate<?> next = next();

        return S.toString(GridCacheMvccCandidate.class, this,
            "key", parent.key(),
            "masks", Mask.toString(flags()),
            "prevVer", (prev == null ? null : prev.version()),
            "nextVer", (next == null ? null : next.version()));
    }

    /**
     * Mask.
     */
    @SuppressWarnings({"PackageVisibleInnerClass"})
    enum Mask {
        LOCAL(0x01),
        OWNER(0x02),
        READY(0x04),
        REENTRY(0x08),
        USED(0x10),
        EC(0x20),
        TX(0x40),
        DHT_LOCAL(0x80),
        NEAR_LOCAL(0x100);

        /** All mask values. */
        private static final Mask[] MASKS = values();

        /** Mask bit. */
        private final short bit;

        /**
         * @param bit Mask value.
         */
        Mask(int bit) {
            this.bit = (short)bit;
        }

        /**
         * @param flags Flags to check.
         * @return {@code True} if mask is set.
         */
        boolean get(short flags) {
            return (flags & bit) == bit;
        }

        /**
         * @param flags Flags.
         * @param on Mask to set.
         * @return Updated flags.
         */
        short set(short flags, boolean on) {
            return (short)(on ? flags | bit : flags & ~bit);
        }

        /**
         * @param flags Flags to check.
         * @return {@code 1} if mask is set, {@code 0} otherwise.
         */
        int bit(short flags) {
            return get(flags) ? 1 : 0;
        }

        /**
         * @param flags Flags.
         * @return String builder containing all flags.
         */
        static String toString(short flags) {
            SB sb = new SB();

            for (Mask m : MASKS) {
                if (m.ordinal() != 0)
                    sb.a('|');

                sb.a(m.name().toLowerCase()).a('=').a(m.bit(flags));
            }

            return sb.toString();
        }
    }
}