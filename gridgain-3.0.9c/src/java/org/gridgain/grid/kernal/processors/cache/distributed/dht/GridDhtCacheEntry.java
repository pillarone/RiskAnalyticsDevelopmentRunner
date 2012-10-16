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
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Replicated cache entry.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtCacheEntry<K, V> extends GridDistributedCacheEntry<K, V> {
    /** Gets node value from reader ID. */
    private static final GridClosure<ReaderId, UUID> R2N = new C1<ReaderId, UUID>() {
        @Override public UUID apply(ReaderId e) {
            return e.nodeId();
        }
    };

    /** Reader clients. */
    @GridToStringInclude
    private volatile List<ReaderId> readers = Collections.emptyList();

    /** Local partition. */
    private final GridDhtLocalPartition<K, V> locPart;

    /** Transactions future for added readers. */
    private volatile GridCacheMultiTxFuture<K, V> txFut;

    /**
     * @param ctx Cache context.
     * @param key Cache key.
     * @param hash Key hash value.
     * @param val Entry value.
     * @param next Next entry in the linked list.
     * @param ttl Time to live.
     */
    public GridDhtCacheEntry(GridCacheContext<K, V> ctx, K key, int hash, V val, GridCacheMapEntry<K, V> next,
        long ttl) {
        super(ctx, key, hash, val, next, ttl);

        // Record this entry with partition.
        locPart = ctx.dht().topology().onAdded(this);
    }

    /** {@inheritDoc} */
    @Override public boolean partitionValid() {
        return locPart.valid();
    }

    /** {@inheritDoc} */
    @Override public boolean markObsolete(GridCacheVersion ver) {
        boolean rmv;

        synchronized (mux) {
            rmv = super.markObsolete(ver);
        }

        // Remove this entry from partition mapping.
        if (rmv)
            cctx.dht().topology().onRemoved(this);

        return rmv;
    }

    /**
     * Add local candidate.
     *
     * @param nearNodeId Near node ID.
     * @param nearVer Near version.
     * @param threadId Owning thread ID.
     * @param ver Lock version.
     * @param timeout Timeout to acquire lock.
     * @param reenter Reentry flag.
     * @param ec Eventually consistent flag.
     * @param tx Tx flag.
     * @return New candidate.
     * @throws GridCacheEntryRemovedException If entry has been removed.
     * @throws GridDistributedLockCancelledException If lock was cancelled.
     */
    @Nullable public GridCacheMvccCandidate<K> addDhtLocal(UUID nearNodeId, GridCacheVersion nearVer, long threadId,
        GridCacheVersion ver, long timeout, boolean reenter, boolean ec, boolean tx)
        throws GridCacheEntryRemovedException, GridDistributedLockCancelledException {
        GridCacheMvccCandidate<K> cand;
        GridCacheMvccCandidate<K> prev;
        GridCacheMvccCandidate<K> owner;

        V val;

        synchronized (mux) {
            // Check removed locks prior to obsolete flag.
            checkRemoved(ver);

            checkObsolete();

            prev = mvcc.anyOwner();

            boolean emptyBefore = mvcc.isEmpty();

            cand = mvcc.addLocal(this, nearNodeId, nearVer, threadId, ver, timeout, reenter, ec, tx, true);

            owner = mvcc.anyOwner();

            boolean emptyAfter = mvcc.isEmpty();

            if (prev != owner)
                mux.notifyAll();

            checkCallbacks(emptyBefore, emptyAfter);

            val = rawGet();
        }

        // Don't link reentries.
        if (cand != null && !cand.reentry())
            // Link with other candidates in the same thread.
            cctx.mvcc().addNext(cand);

        checkOwnerChanged(prev, owner, val);

        return cand;
    }

    /** {@inheritDoc} */
    @Override public boolean tmLock(GridCacheTxEx<K, V> tx, long timeout)
        throws GridCacheEntryRemovedException, GridDistributedLockCancelledException {
        if (tx.local()) {
            GridDhtTxLocal<K, V> dhtTx = (GridDhtTxLocal<K, V>)tx;

            // Null is returned if timeout is negative and there is other lock owner.
            return addDhtLocal(dhtTx.nearNodeId(), dhtTx.nearXidVersion(), tx.threadId(), tx.xidVersion(), timeout,
                false, tx.ec(), true) != null;
        }

        try {
            addRemote(tx.nodeId(), tx.otherNodeId(), tx.threadId(), tx.xidVersion(), tx.timeout(), tx.ec(), true);

            return true;
        }
        catch (GridDistributedLockCancelledException ignored) {
            if (log.isDebugEnabled())
                log.debug("Attempted to enter tx lock for cancelled ID (will ignore): " + tx);

            return false;
        }
    }

    /**
     * @return Readers.
     */
    public Collection<UUID> readers() {
        return F.viewReadOnly(readers, R2N);
    }

    /**
     * @param nodeId Node ID.
     * @return reader ID.
     */
    @Nullable private ReaderId readerId(UUID nodeId) {
        for (ReaderId reader : readers)
            if (reader.nodeId().equals(nodeId))
                return reader;

        return null;
    }

    /**
     * @param nodeId Reader to add.
     * @param msgId Message ID.
     * @return Future for all relevant transactions that were active at the time of adding reader,
     *      or {@code null} if reader was added
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    @Nullable public GridFuture<Boolean> addReader(UUID nodeId, long msgId) throws GridCacheEntryRemovedException {
        // Don't add local node as reader.
        if (cctx.nodeId().equals(nodeId))
            return null;

        GridNode node = cctx.discovery().node(nodeId);

        // If remote node has no near cache, don't add it.
        if (node == null || !U.hasNearCache(node, cctx.dht().near().name()))
            return null;

        // If remote node is (primary?) or back up, don't add it as a reader.
        if (U.nodeIds(cctx.affinity(partition(), CU.allNodes(cctx))).contains(nodeId))
            return null;

        boolean ret = false;

        GridCacheMultiTxFuture<K, V> txFut;

        Collection<GridCacheMvccCandidate<K>> cands = null;

        synchronized (mux) {
            checkObsolete();

            txFut = this.txFut;

            ReaderId reader = readerId(nodeId);

            if (reader == null) {
                reader = new ReaderId(nodeId, msgId);

                readers = new LinkedList<ReaderId>(readers);

                readers.add(reader);

                // Seal.
                readers = Collections.unmodifiableList(readers);

                txFut = this.txFut = new GridCacheMultiTxFuture<K, V>(cctx);

                cands = localCandidates();

                ret = true;
            }
            else {
                long id = reader.messageId();

                if (id < msgId)
                    reader.messageId(msgId);
            }
        }

        if (ret) {
            assert txFut != null;

            if (!F.isEmpty(cands)) {
                for (GridCacheMvccCandidate<K> c : cands) {
                    GridCacheTxEx<K, V> tx = cctx.tm().<GridCacheTxEx<K, V>>tx(c.version());

                    if (tx != null) {
                        assert tx.local();

                        txFut.addTx(tx);
                    }
                }
            }

            txFut.init();

            if (!txFut.isDone()) {
                txFut.listenAsync(new CI1<GridFuture<?>>() {
                    @Override public void apply(GridFuture<?> f) {
                        synchronized (mux) {
                            // Release memory.
                            GridDhtCacheEntry.this.txFut = null;
                        }
                    }
                });
            }
            else
                // Release memory.
                txFut = this.txFut = null;
        }

        return txFut;
    }

    /**
     * @param nodeId Reader to remove.
     * @param msgId Message ID.
     * @return {@code True} if reader was removed as a result of this operation.
     * @throws GridCacheEntryRemovedException If entry was removed.
     */
    public boolean removeReader(UUID nodeId, long msgId) throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            ReaderId reader = readerId(nodeId);

            if (reader == null || reader.messageId() > msgId)
                return false;

            readers = new LinkedList<ReaderId>(readers);

            readers.remove(reader);

            // Seal.
            readers = Collections.unmodifiableList(readers);

            return true;
        }
    }

    /**
     * Clears all readers (usually when partition becomes invalid and ready for eviction).
     */
    public void clearReaders() {
        synchronized (mux) {
            readers = Collections.emptyList();
        }
    }

    /**
     * @throws GridCacheEntryRemovedException If removed.
     */
    public void checkReaders() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkObsolete();

            if (!readers.isEmpty()) {
                readers = new LinkedList<ReaderId>(readers);

                for (Iterator<ReaderId> it = readers.iterator(); it.hasNext();) {
                    ReaderId reader = it.next();

                    if (!cctx.discovery().alive(reader.nodeId()))
                        it.remove();
                }

                readers = Collections.unmodifiableList(readers);
            }
        }
    }

    /** {@inheritDoc} */
    @Override protected boolean hasReaders() throws GridCacheEntryRemovedException {
        synchronized (mux) {
            checkReaders();

            return !readers.isEmpty();
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheEntry<K, V> wrap(boolean prjAware) {
        GridCacheContext<K, V> nearCtx = cctx.dht().near().context();

        GridCacheProjectionImpl<K, V> prjPerCall = nearCtx.projectionPerCall();

        if (prjPerCall != null && prjAware)
            return new GridPartitionedCacheEntryImpl<K, V>(prjPerCall, nearCtx, key, this);

        GridCacheEntryImpl<K, V> wrapper = this.wrapper;

        if (wrapper == null)
            this.wrapper = wrapper = new GridPartitionedCacheEntryImpl<K, V>(null, nearCtx, key, this);

        return wrapper;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtCacheEntry.class, this, "super", super.toString());
    }

    /**
     * Reader ID.
     */
    private static class ReaderId {
        /** Node ID. */
        private UUID nodeId;

        /** Message ID. */
        private long msgId;

        /**
         * @param nodeId Node ID.
         * @param msgId Message ID.
         */
        ReaderId(UUID nodeId, long msgId) {
            this.nodeId = nodeId;
            this.msgId = msgId;
        }

        /**
         * @return Node ID.
         */
        UUID nodeId() {
            return nodeId;
        }

        /**
         * @return Message ID.
         */
        long messageId() {
            return msgId;
        }

        /**
         * @param msgId Message ID.
         */
        void messageId(long msgId) {
            this.msgId = msgId;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(ReaderId.class, this);
        }
    }
}
