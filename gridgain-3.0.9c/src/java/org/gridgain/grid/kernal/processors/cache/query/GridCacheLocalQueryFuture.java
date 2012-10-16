// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.kernal.processors.cache.query;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;

import java.io.*;
import java.util.*;

/**
 * Local query future.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheLocalQueryFuture<K, V, R> extends GridCacheQueryFutureAdapter<K, V, R> {
    /**
     * Required by {@link Externalizable}.
     */
    public GridCacheLocalQueryFuture() {
        // No-op.
    }

    /**
     * @param ctx Context.
     * @param qry Query.
     * @param lsnr Listener.
     * @param loc Local query or not.
     * @param single Single result or not.
     */
    protected GridCacheLocalQueryFuture(GridCacheContext<K, V> ctx, GridCacheQueryBaseAdapter<K, V> qry,
        GridInClosure<? super GridFuture<Collection<R>>> lsnr, boolean loc, boolean single) {
        super(ctx, qry, lsnr, loc, single);

        locFut = ctx.closures().runLocalSafe(new LocalQueryRunnable<K, V, R>(ctx.queries(), this, single));
    }

    /** {@inheritDoc} */
    @Override protected void cancelQuery() throws GridException {
        locFut.cancel();
    }

    /** {@inheritDoc} */
    @Override protected boolean onLastPage(UUID nodeId) {
        return true;
    }
}
