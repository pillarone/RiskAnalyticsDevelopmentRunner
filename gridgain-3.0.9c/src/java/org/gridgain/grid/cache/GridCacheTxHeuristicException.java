// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache;

import org.gridgain.grid.*;

/**
 * Exception thrown whenever grid transaction enters an unknown state.
 * This exception is usually thrown whenever commit partially succeeds.
 * Cache will still resolve this situation automatically to ensure data
 * integrity, by invalidating all values participating in this transaction
 * on remote nodes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheTxHeuristicException extends GridException {
    /**
     * Creates new heuristic exception with given error message.
     *
     * @param msg Error message.
     */
    public GridCacheTxHeuristicException(String msg) {
        super(msg);
    }

    /**
     * Creates new heuristic exception with given error message and optional nested exception.
     *
     * @param msg Error message.
     * @param cause Optional nested exception (can be <tt>null</tt>).
     */
    public GridCacheTxHeuristicException(String msg, Throwable cause) {
        super(msg, cause);
    }
}