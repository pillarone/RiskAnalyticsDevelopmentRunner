// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi;

import org.gridgain.grid.*;

/**
 * Exception thrown by SPI implementations.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridSpiException extends GridException {
    /**
     * Creates new SPI exception with given error message.
     *
     * @param msg Error message.
     */
    public GridSpiException(String msg) {
        super(msg);
    }

    /**
     * Creates new SPI exception given throwable as a cause and
     * source of error message.
     *
     * @param cause Non-null throwable cause.
     */
    public GridSpiException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    /**
     * Creates new SPI exception with given error message and optional nested exception.
     *
     * @param msg Error message.
     * @param cause Optional nested message.
     */
    public GridSpiException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
