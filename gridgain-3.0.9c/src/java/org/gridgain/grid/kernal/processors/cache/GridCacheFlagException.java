// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Exception caused by projection flags check.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheFlagException extends GridRuntimeException {
    /** Flags that caused this exception. */
    private Collection<GridCacheFlag> flags;

    /**
     * @param flags Cause flags.
     */
    public GridCacheFlagException(@Nullable GridCacheFlag... flags) {
        this(F.asList(flags));
    }

    /**
     * @param flags Cause flags.
     */
    public GridCacheFlagException(@Nullable Collection<GridCacheFlag> flags) {
        super(message(flags));

        this.flags = flags;
    }

    /**
     * @return Cause flags.
     */
    public Collection<GridCacheFlag> flags() {
        return flags;
    }

    /**
     * @param flags Flags.
     * @return String information about cause flags.
     */
    private static String message(Collection<GridCacheFlag> flags) {
        return "Cache projection flag exception occurred (if flag is LOCAL, make sure to use peek(..) " +
            "instead of get(..) methods)" + (F.isEmpty(flags) ? "." : " [flags=" + flags + ']');
    }
}
