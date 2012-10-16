// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.jetbrains.annotations.*;

import java.util.*;

/**
 * This enumeration provides different types of actions following the last
 * received job result. See {@link GridTask#result(GridJobResult, List)} for
 * more details.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public enum GridJobResultPolicy {
    /**
     * Wait for results if any are still expected. If all results have been received -
     * it will start reducing results.
     */
    WAIT,

    /** Ignore all not yet received results and start reducing results. */
    REDUCE,

    /**
     * Fail-over job to execute on another node.
     */
    FAILOVER;

    /** Enumerated values. */
    private static final GridJobResultPolicy[] VALS = values();

    /**
     * Efficiently gets enumerated value from its ordinal.
     *
     * @param ord Ordinal value.
     * @return Enumerated value.
     */
    @Nullable
    public static GridJobResultPolicy fromOrdinal(byte ord) {
        return ord >= 0 && ord < VALS.length ? VALS[ord] : null;
    }
}
