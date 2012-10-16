// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.typedef.internal.*;

import java.io.*;

/**
 * Request for single partition info.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridDhtPartitionsSingleRequest<K, V> extends GridDhtPartitionsAbstractMessage<K, V> {
    /**
     * Required by {@link Externalizable}.
     */
    public GridDhtPartitionsSingleRequest() {
        // No-op.
    }

    /**
     * @param id Exchange ID.
     */
    GridDhtPartitionsSingleRequest(GridDhtPartitionExchangeId id) {
        super(id);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPartitionsSingleRequest.class, this, super.toString());
    }
}
