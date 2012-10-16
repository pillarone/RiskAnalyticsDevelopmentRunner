// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.editions;

import java.lang.annotation.*;

/**
 * Indicates that this type is not available in Community Edition (only
 * available in Enterprise Edition).
 * <p>
 * <b>ALL FILES</b> explicitly marked <b>with this annotation</b> are commercially licensed by GridGain,
 * explicitly not included into Community Edition, and represent trade secrets of
 * GridGain.
 * <p>
 * <div class="warning">
 * <pre>
 * ANY AND ALL FORMS OF USE, DISTRIBUTION, AND MODIFICATION
 * OF THE FILES MARKED WITH THIS ANNOTATION AND/OR RESULTING OBJECT
 * CODE IS STRICTLY PROHIBITED WITHOUT EXPLICIT WRITTEN PERMISSION
 * FROM GRIDGAIN SYSTEMS.</pre>
 * </div>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface GridEnterpriseOnly {
    // No-op.
}
