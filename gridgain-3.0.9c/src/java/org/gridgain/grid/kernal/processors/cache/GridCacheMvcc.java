// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Replicated lock based on MVCC paradigm. This class ensures that locks are acquired
 * in proper order and that there is no more than only one active lock present at all
 * times. It also ensures that new generated lock candidates will appear after
 * old ones in the pending set, hence preventing lock starvation.
 * See {@link GridCacheVersionManager#next()} for information on how lock IDs are
 * generated to prevent starvation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheMvcc<K> {
    /** Cache context. */
    @GridToStringExclude
    private final GridCacheContext<K, ?> ctx;

    /** Logger. */
    @GridToStringExclude
    private final GridLogger log;

    /** Local queue. */
    @GridToStringInclude
    private LinkedList<GridCacheMvccCandidate<K>> locs;

    /** Remote queue. */
    @GridToStringInclude
    private LinkedList<GridCacheMvccCandidate<K>> rmts;

    /**
     * @param ctx Cache context.
     */
    public GridCacheMvcc(GridCacheContext<K, ?> ctx) {
        assert ctx != null;

        this.ctx = ctx;

        log = ctx.logger(getClass());
    }

    /**
     * @return Any owner.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> anyOwner() {
        GridCacheMvccCandidate<K> owner = localOwner();

        if (owner == null)
            owner = remoteOwner();

        return owner;
    }

    /**
     * @return Remote candidate only if it's first in the list and is marked
     *      as <tt>'used'</tt>.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> remoteOwner() {
        if (rmts != null) {
            assert !rmts.isEmpty();

            GridCacheMvccCandidate<K> first = rmts.getFirst();

            return first.used() && first.owner() ? first : null;
        }

        return null;
    }

    /**
     * @return Local candidate only if it's first in the list and is marked
     *      as <tt>'owner'</tt>.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> localOwner() {
        if (locs != null) {
            assert !locs.isEmpty();

            GridCacheMvccCandidate<K> first = locs.getFirst();

            return first.owner() ? first : null;
        }

        return null;
    }

    /**
     * @param cands Candidates to search.
     * @param ver Version.
     * @return Candidate for the version.
     */
    @Nullable private GridCacheMvccCandidate<K> candidate(Iterable<GridCacheMvccCandidate<K>> cands,
        GridCacheVersion ver) {
        assert ver != null;

        if (cands != null)
            for (GridCacheMvccCandidate<K> c : cands)
                if (c.version().equals(ver))
                    return c;

        return null;
    }

    /**
     *
     * @param threadId Thread ID.
     * @param reentry Reentry flag.
     * @return Local candidate for the thread.
     */
    @Nullable private GridCacheMvccCandidate<K> localCandidate(long threadId, boolean reentry) {
        if (locs != null)
            for (GridCacheMvccCandidate<K> cand : locs) {
                if (cand.threadId() == threadId) {
                    if (cand.reentry() && !reentry)
                        continue;

                    return cand;
                }
            }

        return null;
    }

    /**
     * @param cand Candidate to add.
     */
    private void add0(GridCacheMvccCandidate<K> cand) {
        assert cand != null;

        // Local.
        if (cand.local()) {
            if (locs == null)
                locs = new LinkedList<GridCacheMvccCandidate<K>>();

            if (!locs.isEmpty()) {
                GridCacheMvccCandidate<K> c = locs.getFirst();

                if (c.owner()) {
                    // If reentry, add at the beginning. Note that
                    // no reentry happens for DHT-local candidates.
                    if (!cand.dhtLocal() && c.threadId() == cand.threadId()) {
                        cand.setOwner();
                        cand.setReady();
                        cand.setReentry();

                        locs.addFirst(cand);

                        return;
                    }
                }

                // Iterate in reverse order.
                for (ListIterator<GridCacheMvccCandidate<K>> it = locs.listIterator(locs.size());
                     it.hasPrevious(); ) {
                    c = it.previous();

                    assert !c.version().equals(cand.version());

                    // Add after the owner.
                    if (c.owner()) {
                        // Threads are checked above.
                        assert cand.dhtLocal() || c.threadId() != cand.threadId();

                        // Reposition.
                        it.next();

                        it.add(cand);

                        return;
                    }

                    // If not the owner, add after the lesser version.
                    if (c.version().isLess(cand.version())) {
                        // Reposition.
                        it.next();

                        it.add(cand);

                        return;
                    }
                }
            }

            // Either list is empty or candidate is first.
            locs.addFirst(cand);
        }
        // Remote.
        else {
            if (rmts == null)
                rmts = new LinkedList<GridCacheMvccCandidate<K>>();

            assert cand.ec() || !cand.owner() || localOwner() == null : "Cannot have local and remote owners " +
                "at the same time [cand=" + cand + ", locs=" + locs + ", rmts=" + rmts + ']';

            GridCacheMvccCandidate<K> cur = candidate(rmts, cand.version());

            // For existing candidates, we only care about owners and keys.
            if (cur != null) {
                if (cand.owner() && !cand.ec())
                    cur.setOwner();

                return;
            }

            // Only add if one does not exist yet.
            for (ListIterator<GridCacheMvccCandidate<K>> it = rmts.listIterator(); it.hasNext(); ) {
                GridCacheMvccCandidate<K> c = it.next();

                // Skip over owners or lesser candidates at the beginning of the list.
                if (c.used() || c.owner() || c.version().isLess(cand.version()))
                    continue;

                // Reposition.
                it.previous();

                it.add(cand);

                return;
            }

            // Either list is empty or candidate is last.
            rmts.add(cand);
        }

        if (cand.ec())
            reassign();
    }

    /**
     * @param ver Version.
     * @param preferLocal Whether or not to prefer local candidates.
     */
    private void remove0(GridCacheVersion ver, boolean preferLocal) {
        if (preferLocal) {
            if (!remove0(locs, ver))
                remove0(rmts, ver);
        }
        else if (!remove0(rmts, ver))
            remove0(locs, ver);

        if (locs != null && locs.isEmpty())
            locs = null;

        if (rmts != null && rmts.isEmpty())
            rmts = null;
    }

    /**
     * Removes candidate from collection.
     *
     * @param col Collection.
     * @param ver Version of the candidate to remove.
     * @return {@code True} if candidate was removed.
     */
    private boolean remove0(Collection<GridCacheMvccCandidate<K>> col, GridCacheVersion ver) {
        if (col != null) {
            for (Iterator<GridCacheMvccCandidate<K>> it = col.iterator(); it.hasNext(); ) {
                GridCacheMvccCandidate<K> cand = it.next();

                if (cand.version().equals(ver)) {
                    cand.setUsed();

                    it.remove();

                    reassign();

                    if (!cand.local())
                        ctx.mvcc().removeRemote(cand);
                    else
                        ctx.mvcc().removeLocal(cand);

                    return true;
                }
            }
        }

        return false;
    }

    /**
     *
     * @param exclude Versions to exclude form check.
     * @return {@code True} if lock is empty.
     */
    public synchronized boolean isEmpty(GridCacheVersion... exclude) {
        if (locs == null && rmts == null && exclude.length == 0)
            return true;

        if (locs != null) {
            assert !locs.isEmpty();

            for (GridCacheMvccCandidate<K> cand : locs)
                if (!U.containsObjectArray(exclude, cand.version()))
                    return false;
        }

        if (rmts != null) {
            assert !rmts.isEmpty();

            for (GridCacheMvccCandidate<K> cand : rmts)
                if (!U.containsObjectArray(exclude, cand.version()))
                    return false;
        }

        return true;
    }

    /**
     * Moves completed candidates right before the base one. Note that
     * if base is not found, then nothing happens and {@code false} is
     * returned.
     *
     * @param baseVer Base version.
     * @param committedVers Committed versions relative to base.
     * @param rolledbackVers Rolled back versions relative to base.
     * @return Lock owner.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> orderCompleted(GridCacheVersion baseVer,
        Collection<GridCacheVersion> committedVers, Collection<GridCacheVersion> rolledbackVers) {
        assert baseVer != null;

        if (rmts != null && !F.isEmpty(committedVers)) {
            Deque<GridCacheMvccCandidate<K>> mvAfter = null;

            int maxIdx = -1;

            for (ListIterator<GridCacheMvccCandidate<K>> it = rmts.listIterator(rmts.size()); it.hasPrevious(); ) {
                GridCacheMvccCandidate<K> cur = it.previous();

                if (!cur.version().equals(baseVer) && committedVers.contains(cur.version())) {
                    // EC transactions still get reordered, but don't become owners.
                    // They will become owners whenever they become first in the list.
                    if (!cur.ec()) {
                        cur.setOwner();

                        assert localOwner() == null : "Cannot not have local owner and remote completed " +
                            "transactions at the same time [baseVer=" + baseVer +
                            ", committedVers=" + committedVers + ", rolledbackVers=" + rolledbackVers +
                            ", localOwner=" + localOwner() + ", locs=" + locs + ", rmts=" + rmts + ']';
                    }

                    if (maxIdx < 0)
                        maxIdx = it.nextIndex();
                }
                else if (maxIdx >= 0 && cur.version().isGreaterEqual(baseVer)) {
                    if (--maxIdx >= 0) {
                        if (mvAfter == null)
                            mvAfter = new LinkedList<GridCacheMvccCandidate<K>>();

                        it.remove();

                        mvAfter.addFirst(cur);
                    }
                }

                // If base is completed, then set it to owner too.
                if (!cur.owner() && cur.version().equals(baseVer) && committedVers.contains(cur.version()))
                    cur.setOwner();
            }

            if (maxIdx >= 0 && mvAfter != null) {
                ListIterator<GridCacheMvccCandidate<K>> it = rmts.listIterator(maxIdx + 1);

                for (GridCacheMvccCandidate<K> cand : mvAfter)
                    it.add(cand);
            }

            // Remove rolled back versions.
            if (!F.isEmpty(rolledbackVers)) {
                for (Iterator<GridCacheMvccCandidate<K>> it = rmts.iterator(); it.hasNext(); ) {
                    GridCacheMvccCandidate<K> cand = it.next();

                    if (rolledbackVers.contains(cand.version())) {
                        cand.setUsed(); // Mark as used to be consistent, even though we are about to remove it.

                        it.remove();
                    }
                }

                if (rmts.isEmpty())
                    rmts = null;
            }

            if (rmts != null) {
                GridCacheMvccCandidate<K> first = rmts.getFirst();

                assert first != null;

                if (first.ec())
                    reassign();
            }
        }

        return anyOwner();
    }

    /**
     * Puts owned versions in front of base.
     *
     * @param base Base version.
     * @param owned Owned list.
     * @return Current owner.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> orderOwned(GridCacheVersion base,
        Collection<GridCacheVersion> owned) {
        if (F.isEmpty(owned))
            return anyOwner();

        if (rmts != null) {
            GridCacheMvccCandidate<K> cand = candidate(rmts, base);

            if (cand != null) {
                List<GridCacheMvccCandidate<K>> mv = null;

                // Reverse order.
                for (ListIterator<GridCacheMvccCandidate<K>> it = rmts.listIterator(rmts.size()); it.hasPrevious(); ) {
                    GridCacheMvccCandidate<K> c = it.previous();

                    if (c == cand) {
                        if (mv != null)
                            for (GridCacheMvccCandidate<K> o : mv)
                                it.add(o);

                        break;
                    }
                    else if (owned.contains(c.version())) {
                        it.remove();

                        if (mv == null)
                            mv = new LinkedList<GridCacheMvccCandidate<K>>();

                        mv.add(c);

                        c.setOwner();
                    }
                }
            }
        }

        return anyOwner();
    }

    /**
     * @param parent Parent entry.
     * @param threadId Thread ID.
     * @param ver Lock version.
     * @param timeout Lock acquisition timeout.
     * @param reenter Reentry flag ({@code true} if reentry is allowed).
     * @param ec Eventually consistent flag.
     * @param tx Transaction flag.
     * @return New lock candidate if lock was added, or current owner if lock was reentered,
     *      or <tt>null</tt> if lock was owned by another thread and timeout is negative.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> addLocal(GridCacheEntryEx<K, ?> parent, long threadId,
        GridCacheVersion ver, long timeout, boolean reenter, boolean ec, boolean tx) {
        return addLocal(parent, null, null, threadId, ver, timeout, reenter, ec, tx, false);
    }

    /**
     * @param parent Parent entry.
     * @param nearNodeId Near node ID.
     * @param nearVer Near version.
     * @param threadId Thread ID.
     * @param ver Lock version.
     * @param timeout Lock acquisition timeout.
     * @param reenter Reentry flag ({@code true} if reentry is allowed).
     * @param ec Eventually consistent flag.
     * @param tx Transaction flag.
     * @param dhtLocal DHT local flag.
     * @return New lock candidate if lock was added, or current owner if lock was reentered,
     *      or <tt>null</tt> if lock was owned by another thread and timeout is negative.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> addLocal(GridCacheEntryEx<K, ?> parent,
        @Nullable UUID nearNodeId, @Nullable GridCacheVersion nearVer, long threadId, GridCacheVersion ver,
        long timeout, boolean reenter, boolean ec, boolean tx, boolean dhtLocal) {
        if (log.isDebugEnabled())
            log.debug("Adding local candidate [mvcc=" + this + ", parent=" + parent + ", threadId=" + threadId +
                ", ver=" + ver + ", timeout=" + timeout + ", reenter=" + reenter + ", ec=" + ec + ", tx=" + tx + "]");

        // Don't check reenter for DHT candidates.
        if (!dhtLocal && !reenter) {
            GridCacheMvccCandidate<K> owner = localOwner();

            if (owner != null && owner.threadId() == threadId)
                return null;
        }

        // If there are pending locks and timeout is negative,
        // then we give up right away.
        if (timeout < 0) {
            if (locs != null || rmts != null) {
                GridCacheMvccCandidate<K> owner = localOwner();

                // Only proceed if this is a re-entry.
                if (owner == null || owner.threadId() != threadId)
                    return null;
            }
        }

        UUID locNodeId = ctx.nodeId();

        // If this is a reentry, then reentry flag will be flipped within 'add0(..)' method.
        GridCacheMvccCandidate<K> cand = new GridCacheMvccCandidate<K>(parent, locNodeId, nearNodeId, nearVer, threadId,
            ver, timeout, /*local*/true, /*reenter*/false, /*EC*/ec, /*TX*/tx, false, dhtLocal);

        ctx.mvcc().addLocal(cand);

        if (ec)
            cand.setReady();

        add0(cand);

        return cand;
    }

    /**
     * Adds new lock candidate.
     *
     * @param parent Parent entry.
     * @param nodeId Node ID.
     * @param otherNodeId Other node ID.
     * @param threadId Thread ID.
     * @param ver Lock version.
     * @param timeout Lock acquire timeout.
     * @param ec Eventually consistent flag.
     * @param tx Transaction flag.
     * @param nearLocal Near-local flag.
     * @return Remote candidate.
     */
    public synchronized GridCacheMvccCandidate<K> addRemote(GridCacheEntryEx<K, ?> parent, UUID nodeId,
        @Nullable UUID otherNodeId, long threadId, GridCacheVersion ver, long timeout, boolean ec, boolean tx,
        boolean nearLocal) {
        GridCacheMvccCandidate<K> cand = new GridCacheMvccCandidate<K>(parent, nodeId, otherNodeId, null, threadId, ver,
            timeout, /*local*/false, /*reentry*/false, ec, tx, nearLocal, false);

        addRemote(cand);

        return cand;
    }

    /**
     * @param cand Remote candidate.
     */
    public synchronized void addRemote(GridCacheMvccCandidate<K> cand) {
        assert !cand.local();

        if (log.isDebugEnabled())
            log.debug("Adding remote candidate [mvcc=" + this + ", cand=" + cand + "]");

        ctx.versions().onReceived(cand.nodeId(), cand.version());

        add0(cand);

        ctx.mvcc().addRemote(cand);
    }

    /**
     * @param ver Lock version to acquire or set to ready.
     * @return Current owner.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> readyLocal(GridCacheVersion ver) {
        GridCacheMvccCandidate<K> cand = candidate(ver);

        if (cand == null)
            return anyOwner();

        assert cand.local();

        return readyLocal(cand);
    }

    /**
     * @param cand Local candidate added in any of the {@code addLocal(..)} methods.
     * @return Current lock owner.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> readyLocal(GridCacheMvccCandidate<K> cand) {
        assert cand.local();

        cand.setReady();

        reassign();

        return anyOwner();
    }

    /**
     * Sets remote candidate to done.
     *
     * @param ver Version.
     * @param pending Pending versions.
     * @param committed Committed versions.
     * @param rolledback Rolledback versions.
     * @return Lock owner.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> doneRemote(
        GridCacheVersion ver,
        Collection<GridCacheVersion> pending,
        Collection<GridCacheVersion> committed,
        Collection<GridCacheVersion> rolledback) {
        assert ver != null;

        if (log.isDebugEnabled())
            log.debug("Setting remote candidate to done [mvcc=" + this + ", ver=" + ver + "]");

        GridCacheMvccCandidate<K> cand = candidate(rmts, ver);

        if (cand != null) {
            assert !cand.local() : "Remote candidate is marked as local: " + cand;

            cand.setOwner();
            cand.setUsed();

            if (cand.nearLocal()) {
                assert rmts != null;
                assert !rmts.isEmpty();

                List<GridCacheMvccCandidate<K>> mvAfter = null;

                for (ListIterator<GridCacheMvccCandidate<K>> it = rmts.listIterator(); it.hasNext(); ) {
                    GridCacheMvccCandidate<K> c = it.next();

                    assert c == cand || !c.used() || !c.nearLocal() : "Invalid candidate: " + c;

                    if (c == cand) {
                        if (mvAfter != null)
                            for (GridCacheMvccCandidate<K> mv : mvAfter)
                                it.add(mv);

                        break;
                    }
                    else if (c.nearLocal() || (!committed.contains(c.version()) && !rolledback.contains(c.version()) &&
                        pending.contains(c.version()))) {
                        it.remove();

                        if (mvAfter == null)
                            mvAfter = new LinkedList<GridCacheMvccCandidate<K>>();

                        mvAfter.add(c);
                    }
                }
            }
        }

        return anyOwner();
    }

    /**
     * Assigns local lock.
     */
    private void reassign() {
        GridCacheMvccCandidate<K> firstRmt = null;

        if (rmts != null) {
            for (GridCacheMvccCandidate<K> cand : rmts) {
                if (firstRmt == null)
                    firstRmt = cand;

                // If there is a remote owner, then local cannot be an owner,
                // so no reassignment happens.
                if (cand.owner()) {
                    // Only make first entry into owner.
                    if (firstRmt.ec()) {
                        firstRmt.setOwner();
                        firstRmt.setUsed();
                    }

                    return;
                }
            }
        }

        if (locs != null) {
            for (ListIterator<GridCacheMvccCandidate<K>> it = locs.listIterator(); it.hasNext(); ) {
                GridCacheMvccCandidate<K> cand = it.next();

                if (cand.owner())
                    return;

                if (cand.ready()) {
                    GridCacheMvccCandidate<K> prev = nonRollbackPrevious(cand);

                    // If previous has not been acquired, this candidate cannot acquire lock either,
                    // so we move on to the next one.
                    if (prev != null && !prev.owner())
                        continue;

                    boolean assigned = false;

                    if (firstRmt != null && cand.version().isGreater(firstRmt.version())) {
                        // Check previous candidates for 2 cases:
                        // 1. If this candidate is waiting for a smaller remote version,
                        //    then we must check if previous candidate is the owner and
                        //    has the same remote candidate version. In that case, we can
                        //    safely set this candidate to owner as well.
                        // 2. If this candidate is waiting for a smaller remote version,
                        //    then we must check if previous candidate is the owner and
                        //    any of the local candidates with versions smaller than first
                        //    remote version have the same key as the previous owner. In
                        //    that case, we can safely set this candidate to owner as well.
                        while (prev != null && prev.owner()) {
                            for (GridCacheMvccCandidate<K> c : prev.parent().remoteMvccSnapshot()) {
                                if (c.version().equals(firstRmt.version())) {
                                    cand.setOwner();

                                    assigned = true;

                                    break; // For.
                                }
                            }

                            if (!assigned) {
                                for (GridCacheMvccCandidate<K> c : locs) {
                                    if (c == cand || c.version().isGreater(firstRmt.version()))
                                        break;

                                    for (GridCacheMvccCandidate<K> p = c.previous(); p != null; p = p.previous()) {
                                        if (p.key().equals(prev.key())) {
                                            cand.setOwner();

                                            assigned = true;

                                            break; // For.
                                        }
                                    }

                                    if (assigned)
                                        break; // For.
                                }
                            }

                            if (assigned)
                                break; // While.

                            prev = prev.previous();
                        }
                    }

                    if (!assigned) {
                        if (firstRmt != null) {
                            if (cand.version().isLess(firstRmt.version())) {
                                cand.setOwner();

                                assigned = true;
                            }
                            // Automatically assign EC locks.
                            else if (firstRmt.ec()) {
                                firstRmt.setOwner();
                                firstRmt.setUsed();
                            }
                        }
                        else {
                            cand.setOwner();

                            assigned = true;
                        }
                    }

                    if (assigned) {
                        it.remove();

                        // Owner must be first in the list.
                        locs.addFirst(cand);
                    }

                    return;
                }
            }
        }

        // Automatically assign EC locks.
        if (firstRmt != null && firstRmt.ec()) {
            firstRmt.setOwner();
            firstRmt.setUsed();
        }
    }

    /**
     * @param cand Candidate to check.
     * @return First predecessor that is owner or is not used.
     */
    @Nullable private GridCacheMvccCandidate<K> nonRollbackPrevious(GridCacheMvccCandidate<K> cand) {
        for (GridCacheMvccCandidate<K> c = cand.previous(); c != null; c = c.previous()) {
            if (c.owner() || !c.used())
                return c;
        }

        return null;
    }

    /**
     * Checks if lock should be assigned.
     *
     * @return Owner.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> recheck() {
        reassign();

        return anyOwner();
    }

    /**
     * Local local release.
     * @return Removed lock candidate or <tt>null</tt> if candidate was not removed.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> releaseLocal() {
        return releaseLocal(Thread.currentThread().getId());
    }

    /**
     * Local release.
     *
     * @param threadId ID of the thread.
     * @return Current owner.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> releaseLocal(long threadId) {
        GridCacheMvccCandidate<K> owner = localOwner();

        if (owner == null || owner.threadId() != threadId)
            // Release had no effect.
            return owner;

        owner.setUsed();

        remove0(owner.version(), true);

        return anyOwner();
    }

    /**
     * Removes lock even if it is not owner.
     *
     * @param ver Lock version.
     * @return Current owner.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> remove(GridCacheVersion ver) {
        remove0(ver, false);

        return anyOwner();
    }

    /**
     * Removes all candidates for node.
     *
     * @param nodeId Node ID.
     * @return Current owner.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> removeExplicitNodeCandidates(UUID nodeId) {
        if (rmts != null) {
            for (Iterator<GridCacheMvccCandidate<K>> it = rmts.iterator(); it.hasNext(); ) {
                GridCacheMvccCandidate<K> cand = it.next();

                if (cand.nodeId().equals(nodeId) && !cand.tx()) {
                    cand.setUsed(); // Mark as used to be consistent.

                    it.remove();
                }
            }

            if (rmts.isEmpty())
                rmts = null;
        }

        reassign();

        return anyOwner();
    }

    /**
     * Gets candidate for lock ID.
     *
     * @param ver Lock version.
     * @return Candidate or <tt>null</tt> if there is no candidate for given ID.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> candidate(GridCacheVersion ver) {
        GridCacheMvccCandidate<K> cand = candidate(locs, ver);

        if (cand == null)
            cand = candidate(rmts, ver);

        return cand;
    }

    /**
     * Gets candidate for lock ID.
     *
     * @param threadId Thread ID.
     * @return Candidate or <tt>null</tt> if there is no candidate for given ID.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> localCandidate(long threadId) {
        // Don't return reentries.
        return localCandidate(threadId, false);
    }

    /**
     * @param nodeId Node ID.
     * @param threadId Thread ID.
     * @return Remote candidate.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> remoteCandidate(UUID nodeId, long threadId) {
        if (rmts != null)
            for (GridCacheMvccCandidate<K> c : rmts)
                if (c.nodeId().equals(nodeId) && c.threadId() == threadId)
                    return c;

        return null;
    }

    /**
     *
     * @param ver Version.
     * @return {@code True} if candidate with given version exists.
     */
    public synchronized boolean hasCandidate(GridCacheVersion ver) {
        return candidate(ver) != null;
    }

    /**
     * @param excludeVers Exclude versions.
     * @return Collection of local candidates.
     */
    public synchronized Collection<GridCacheMvccCandidate<K>> localCandidates(GridCacheVersion... excludeVers) {
        return candidates(locs, false, excludeVers);
    }

    /**
     * @param reentries Flag to include reentries.
     * @param excludeVers Exclude versions.
     * @return Collection of local candidates.
     */
    public synchronized List<GridCacheMvccCandidate<K>> localCandidates(boolean reentries,
        GridCacheVersion... excludeVers) {
        return candidates(locs, reentries, excludeVers);
    }

    /**
     * @param excludeVers Exclude versions.
     * @return Collection of remote candidates.
     */
    public synchronized List<GridCacheMvccCandidate<K>> remoteCandidates(GridCacheVersion... excludeVers) {
        return candidates(rmts, false, excludeVers);
    }

    /**
     * @param col Collection of candidates.
     * @param reentries Reentry flag.
     * @param excludeVers Exclude versions.
     * @return Collection of candidates minus the exclude versions.
     */
    private List<GridCacheMvccCandidate<K>> candidates(Collection<GridCacheMvccCandidate<K>> col,
        boolean reentries, GridCacheVersion... excludeVers) {
        if (col == null)
            return Collections.emptyList();

        assert !col.isEmpty();

        List<GridCacheMvccCandidate<K>> cands = new ArrayList<GridCacheMvccCandidate<K>>(col.size());

        for (GridCacheMvccCandidate<K> c : col) {
            // Don't include reentries.
            if ((!c.reentry() || (reentries && c.reentry())) && !U.containsObjectArray(excludeVers, c.version()))
                cands.add(c);
        }

        return cands;
    }

    /**
     * @return {@code True} if lock is owner by current thread.
     */
    public synchronized boolean isLocallyOwnedByCurrentThread() {
        return isLocallyOwnedByThread(Thread.currentThread().getId());
    }

    /**
     * @param threadId Thread ID to check.
     * @param exclude Versions to ignore.
     * @return {@code True} if lock is owned by the thread with given ID.
     */
    public synchronized boolean isLocallyOwnedByThread(long threadId, GridCacheVersion... exclude) {
        GridCacheMvccCandidate<K> owner = localOwner();

        return owner != null && owner.threadId() == threadId && owner.nodeId().equals(ctx.nodeId()) &&
            !U.containsObjectArray(exclude, owner.version());
    }

    /**
     *
     * @param threadId Thread ID.
     * @param nodeId Node ID.
     * @return {@code True} if lock is held by given thread and node IDs.
     */
    public synchronized boolean isLockedByThread(long threadId, UUID nodeId) {
        GridCacheMvccCandidate<K> owner = anyOwner();

        return owner != null && owner.threadId() == threadId && owner.nodeId().equals(nodeId);
    }

    /**
     * @return {@code True} if lock is owned by any thread or node.
     */
    public synchronized boolean isOwnedByAny() {
        return anyOwner() != null;
    }

    /**
     * @param lockId ID of lock candidate.
     * @return {@code True} if candidate is owner.
     */
    public synchronized boolean isLocallyOwned(UUID lockId) {
        GridCacheMvccCandidate<K> owner = localOwner();

        return owner != null && owner.version().id().equals(lockId);
    }

    /**
     * @return First remote entry or <tt>null</tt>.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> firstRemote() {
        return rmts == null ? null : rmts.getFirst();
    }

    /**
     * @return First local entry or <tt>null</tt>.
     */
    @Nullable public synchronized GridCacheMvccCandidate<K> firstLocal() {
        return locs == null ? null : locs.getFirst();
    }

    /**
     * @param ver Version to check for ownership.
     * @return {@code True} if lock is owned by the specified version.
     */
    public synchronized boolean isOwnedBy(GridCacheVersion ver) {
        GridCacheMvccCandidate<K> cand = anyOwner();

        return cand != null && cand.version().equals(ver);
    }

    /** {@inheritDoc} */
    @Override public synchronized String toString() {
        return S.toString(GridCacheMvcc.class, this);
    }
}
