// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;

/**
 * General grid exception. This exception is used to indicate any error condition
 * within Grid.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridException extends Exception {
    /**
     * Creates new exception with given error message.
     *
     * @param msg Error message.
     */
    public GridException(String msg) {
        super(msg);
    }

    /**
     * Creates new grid exception given throwable as a cause and
     * source of error message.
     *
     * @param cause Non-null throwable cause.
     */
    public GridException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    /**
     * Creates new exception with given error message and optional nested exception.
     *
     * @param msg Error message.
     * @param cause Optional nested exception (can be {@code null}).
     */
    public GridException(String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }

    /**
     * Checks if this exception has given class in {@code 'cause'} hierarchy.
     *
     * @param cls Cause classes to check (if {@code null} or empty, {@code false} is returned).
     * @return {@code True} if one of the causing exception is an instance of passed in classes,
     *      {@code false} otherwise.
     */
    public boolean hasCause(@Nullable Class<? extends Throwable>... cls) {
        return X.hasCause(this, cls);
    }

    /**
     * Gets first exception of given class from {@code 'cause'} hierarchy if any.
     *
     * @param cls Cause class to get cause (if {@code null}, {@code null} is returned).
     * @return First causing exception of passed in class, {@code null} otherwise.
     */
    @Nullable public <T extends Throwable> T getCause(@Nullable Class<T> cls) {
        return X.cause(this, cls);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return getClass() + ": " + getMessage();
    }
}
