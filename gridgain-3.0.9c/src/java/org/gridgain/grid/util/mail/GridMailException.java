// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail;

import org.gridgain.grid.*;

/**
 * This exception can be thrown from classes in package {@link org.gridgain.grid.util.mail}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridMailException extends GridException {
    /**
     * Creates new mail exception with given error message.
     *
     * @param msg Error message.
     */
    public GridMailException(String msg) {
        super(msg);
    }

    /**
     * Creates new mail exception with given error message and optional nested
     * exception.
     *
     * @param msg Error message.
     * @param cause Optional nested exception (can be {@code null}).
     */
    public GridMailException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
