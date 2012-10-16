// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import java.util.*;

/**
 * Local transaction API.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCacheTxRemoteEx<K, V> extends GridCacheTxEx<K, V> {
    /**
     * @param baseVer Base version.
     * @param committedVers Committed version.
     * @param rolledbackVers Rolled back version.
     */
    public void doneRemote(GridCacheVersion baseVer, Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers);

    /**
     * @param e Sets write value for pessimistic transactions.
     * @return {@code True} if entry was found.
     */
    public boolean setWriteValue(GridCacheTxEntry<K, V> e);

    /**
     * Adds remote candidates and completed versions to all involved entries.
     *
     * @param cands Candidates.
     * @param committedVers Committed versions.
     * @param rolledbackVers Rolled back versions.
     */
    public void addRemoteCandidates(
        Map<K, Collection<GridCacheMvccCandidate<K>>> cands,
        Collection<GridCacheVersion> committedVers,
        Collection<GridCacheVersion> rolledbackVers);
}
