// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.jetbrains.annotations.*;

/**
 * Defines life-time scopes for checkpoint and swap space operations. Such operations include:
 * <ul>
 *      <li>{@link GridTaskSession#saveCheckpoint(String, Object, GridTaskSessionScope , long)}</li>
 *      <li>{@link GridTaskSession#writeToSwap(Object, Object, GridTaskSessionScope)}</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public enum GridTaskSessionScope {
    /**
     * Data saved with this scope will be automatically removed
     * once the task session is completed (i.e. execution of the task is completed)
     * or when they time out. This is the most often used scope for checkpoints and swap space.
     * It provides behavior for use case when jobs can failover on other nodes
     * within the same session and thus checkpoints or data saved to swap space should be
     * preserved for the duration of the entire session.
     */
    SESSION_SCOPE,

    /**
     * Data saved with this scope will only be removed automatically
     * if they time out and time out is supported. Currently, only checkpoints support timeouts.
     * Any data, however, can always be removed programmatically via methods on {@link GridTaskSession}
     * interface.
     */
    GLOBAL_SCOPE;

    /** Enumerated values. */
    private static final GridTaskSessionScope[] VALS = values();

    /**
     * Efficiently gets enumerated value from its ordinal.
     *
     * @param ord Ordinal value.
     * @return Enumerated value.
     */
    @Nullable public static GridTaskSessionScope fromOrdinal(byte ord) {
        return ord >= 0 && ord < VALS.length ? VALS[ord] : null;
    }
}
