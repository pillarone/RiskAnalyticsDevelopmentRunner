// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;

/**
 * Request for single partition info.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridDhtPartitionsAbstractMessage<K, V> extends GridCacheMessage<K, V> {
    /** Exchange ID. */
    private GridDhtPartitionExchangeId exchId;

    /**
     * Required by {@link Externalizable}.
     */
    public GridDhtPartitionsAbstractMessage() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public boolean isPreloaderMessage() {
        return true;
    }

    /**
     * @param exchId Exchange ID.
     */
    GridDhtPartitionsAbstractMessage(GridDhtPartitionExchangeId exchId) {
        this.exchId = exchId;
    }

    /**
     * @return Exchange ID.
     */
    public GridDhtPartitionExchangeId exchangeId() {
        return exchId;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeObject(exchId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        exchId = (GridDhtPartitionExchangeId)in.readObject();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPartitionsAbstractMessage.class, this, super.toString());
    }
}
