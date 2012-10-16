// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.tostring;

import java.lang.annotation.*;

/**
 * Attach this annotation to a field to provide its order in
 * {@code toString()} output. By default the order the order is the same as
 * the order of declaration in the class. Fields with smaller order value
 * will come before in {@code toString()} output. If order is not specified
 * the {@link Integer#MAX_VALUE} will be used.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface GridToStringOrder {
    /**
     * Numeric order value.
     */
    @SuppressWarnings({"JavaDoc"}) int value();
}
