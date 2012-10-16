// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.util.future.*;

import java.util.*;

/**
 * DHT future wrapper.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtFutureWrapper<K, A, T> extends GridFutureWrapper<T, A> implements GridDhtFuture<K, T> {
    /** Retries. */
    private Collection<K> retries;

    /**
     * @param wrapped Wrapped future.
     * @param c Transformer.
     */
    public GridDhtFutureWrapper(GridFuture<A> wrapped, GridClosure<A, T> c) {
        super(wrapped, c);

        retries = Collections.emptyList();
    }

    /**
     * @param wrapped Wrapped future.
     * @param c Transformer.
     * @param retries Keys to retry.
     */
    public GridDhtFutureWrapper(GridFuture<A> wrapped, GridClosure<A, T> c, Collection<K> retries) {
        this(wrapped, c);
        
        this.retries = retries == null ? Collections.<K>emptyList() : retries;
    }

    /** {@inheritDoc} */
    @Override public Collection<K> retries() {
        return retries;
    }
}
