// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

/**
 * Cache gateway.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
public class GridCacheGateway<K, V> {
    /** Context. */
    private final GridCacheContext<K, V> ctx;

    /**
     * @param ctx Cache context.
     */
    public GridCacheGateway(GridCacheContext<K, V> ctx) {
        assert ctx != null;

        this.ctx = ctx;
    }

    /**
     * Enter a cache call.
     */
    public void enter() {
        ctx.deploy().onEnter();

        ctx.kernalContext().gateway().readLock();
    }

    /**
     * Leave a cache call entered by {@link #enter()} method.
     */
    public void leave() {
        // Unwind eviction notifications.
        CU.unwindEvicts(ctx);

        // Unwind events queue after every method call.
        ctx.cache().context().events().unwind();

        ctx.kernalContext().gateway().readUnlock();
    }

    /**
     * @param prj Projection to guard.
     * @return Previous projection set on this thread.
     */
    @Nullable public GridCacheProjectionImpl<K, V> enter(GridCacheProjectionImpl<K, V> prj) {
        ctx.deploy().onEnter();

        ctx.kernalContext().gateway().readLock();

        // Set thread local projection per call.
        GridCacheProjectionImpl<K, V> prev = ctx.projectionPerCall();

        ctx.projectionPerCall(prj);

        return prev;
    }

    /**
     * @param prev Previous.
     */
    public void leave(GridCacheProjectionImpl<K, V> prev) {
        // Unwind eviction notifications.
        CU.unwindEvicts(ctx);

        // Unwind events queue after every method call.
        ctx.cache().context().events().unwind();

        // Return back previous thread local projection per call.
        ctx.projectionPerCall(prev);

        ctx.kernalContext().gateway().readUnlock();
    }
}
