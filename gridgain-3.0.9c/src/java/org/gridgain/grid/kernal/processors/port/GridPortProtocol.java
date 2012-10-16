// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.port;

import org.jetbrains.annotations.*;

/**
 * Protocols supported by port processor.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public enum GridPortProtocol {
    /**
     * TODO
     */
    TCP,

    /**
     * TODO
     */
    UDP;

    /** Enumerated values. */
    private static final GridPortProtocol[] VALS = values();

    /**
     * Efficiently gets enumerated value from its ordinal.
     *
     * @param ord Ordinal value.
     * @return Enumerated value.
     */
    @Nullable public static GridPortProtocol fromOrdinal(byte ord) {
        return ord >= 0 && ord < VALS.length ? VALS[ord] : null;
    }
}
