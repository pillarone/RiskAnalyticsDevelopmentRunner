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
 * This exception is thrown when license violation is detected. Note that this
 * exception is thrown only in Enterprise Edition. It is included into
 * Community Edition only for compilation compatibility.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridLicenseException extends GridException {
    /**
     * Creates new license exception with given error message.
     *
     * @param msg Error message.
     */
    public GridLicenseException(String msg) {
        super(msg);
    }

    /**
     * Creates new license exception given throwable as a cause and
     * source of error message.
     *
     * @param cause Non-null throwable cause.
     */
    public GridLicenseException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    /**
     * Creates new license exception with given error message and optional nested exception.
     *
     * @param msg Error message.
     * @param cause Optional nested exception (can be {@code null}).
     */
    public GridLicenseException(String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }
}
