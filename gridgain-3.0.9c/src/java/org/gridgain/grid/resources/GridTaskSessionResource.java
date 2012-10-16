// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.resources;

import org.gridgain.grid.*;
import java.lang.annotation.*;

/**
 * Annotates a field or a setter method for injection of {@link GridTaskSession} resource.
 * Logger can be injected into instances of following classes:
 * <p>
 * Distributed Task Session can be injected into instances of following classes:
 * <ul>
 * <li>{@link GridTask}</li>
 * <li>{@link GridJob}</li>
 * </ul>
 * <p>
 * Here is how injection would typically happen:
 * <pre name="code" class="java">
 * public class MyGridJob implements GridJob {
 *      ...
 *      &#64;GridTaskSessionResource
 *      private GridTaskSession taskSes;
 *      ...
 *  }
 * </pre>
 * or
 * <pre name="code" class="java">
 * public class MyGridJob implements GridJob {
 *     ...
 *     private GridTaskSession taskSes;
 *     ...
 *     &#64;GridTaskSessionResource
 *     public void setTaskSession(GridTaskSession taskSes) {
 *          this.taskSes = taskSes;
 *     }
 *     ...
 * }
 * </pre>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface GridTaskSessionResource {
    // No-op.
}
