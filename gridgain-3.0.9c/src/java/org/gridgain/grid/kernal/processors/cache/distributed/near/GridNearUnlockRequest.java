// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.near;

import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;

/**
 * Near cache unlock request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNearUnlockRequest<K, V> extends GridDistributedUnlockRequest<K, V> {
    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridNearUnlockRequest() {
        // No-op.
    }

    /**
     * @param keyCnt Key count.
     */
    public GridNearUnlockRequest(int keyCnt) {
        super(keyCnt);
    }

    /**
     * {@inheritDoc}
     */
    @Override public String toString() {
        return S.toString(GridNearUnlockRequest.class, this);
    }
}
