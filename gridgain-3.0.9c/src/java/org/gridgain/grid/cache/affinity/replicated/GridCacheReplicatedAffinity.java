// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.affinity.replicated;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;

import java.util.*;

/**
 * Cache affinity implementation for replicated cache. If filter is provided, then
 * it will be used to further filter the nodes. Otherwise, all cache nodes will be
 * used.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheReplicatedAffinity<K> implements GridCacheAffinity<K> {
    /** Filter. */
    private GridPredicate2<Integer, GridRichNode> filter;

    /**
     * Empty constructor.
     */
    public GridCacheReplicatedAffinity() {
        // No-op.
    }

    /**
     * Initializes affinity with given filter. Only nodes that pass the filter will be included.
     * 
     * @param filter Affinity filter.
     */
    public GridCacheReplicatedAffinity(GridPredicate2<Integer, GridRichNode> filter) {
        this.filter = filter;
    }

    /**
     * Gets optional affinity filter ({@code null} if it has not bee provided).
     *
     * @return Affinity filter.
     */
    public GridPredicate2<Integer, GridRichNode> getFilter() {
        return filter;
    }

    /**
     * Sets optional affinity filter.
     *
     * @param filter Affinity filter.
     */
    public void setFilter(GridPredicate2<Integer, GridRichNode> filter) {
        this.filter = filter;
    }

    /** {@inheritDoc} */
    @Override public int partition(K key) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public int partitions() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridRichNode> nodes(final int part, Collection<GridRichNode> nodes) {
        final GridPredicate2<Integer, GridRichNode> filter = this.filter;

        if (filter == null)
            return nodes;

        return F.view(nodes, new PN() {
            @Override public boolean apply(GridRichNode n) {
                return filter.apply(part, n);
            }
        });
    }

    /** {@inheritDoc} */
    @Override public void reset() {
        // No-op.
    }
}
