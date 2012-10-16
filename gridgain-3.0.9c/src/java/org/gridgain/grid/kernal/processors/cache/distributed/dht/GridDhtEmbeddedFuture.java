// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;

import java.io.*;
import java.util.*;

/**
 * Embedded DHT future.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridDhtEmbeddedFuture<K, A, B> extends GridEmbeddedFuture<A, B> implements GridDhtFuture<K, A> {
    /** Retries. */
    private Collection<K> retries;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtEmbeddedFuture() {
        // No-op.
    }

    /**
     * @param ctx Context.
     * @param embedded Embedded.
     * @param c Closure.
     */
    public GridDhtEmbeddedFuture(GridKernalContext ctx, GridFuture<B> embedded, GridClosure2<B, Exception, A> c) {
        super(ctx, embedded, c);

        retries = Collections.emptyList();
    }

    /**
     * @param ctx Context.
     * @param embedded Embedded.
     * @param c Closure.
     * @param retries Retries.
     */
    public GridDhtEmbeddedFuture(GridKernalContext ctx, GridFuture<B> embedded, GridClosure2<B, Exception, A> c,
        Collection<K> retries) {
        super(ctx, embedded, c);

        this.retries = retries;
    }

    /** {@inheritDoc} */
    @Override public Collection<K> retries() {
        return retries;
    }

    /**
     * {@inheritDoc}
     */
    @Override public String toString() {
        return S.toString(GridDhtEmbeddedFuture.class, this, super.toString());
    }
}
