// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.collision.jobstealing;

import java.lang.annotation.*;

/**
 * This annotation disables job stealing if corresponding feature is configured.
 * Add this annotation to the job class to disable stealing this kind of jobs
 * from nodes where they were mapped to.
 * <p>
 * Here is an example of how this annotation can be attached to a job class:
 * <pre name="code" class="java">
 * &#64;GridJobStealingDisabled
 * public class MyJob extends GridJobAdapter&lt;Object&gt; {
 *     public Serializable execute() throws GridException {
 *         // Job logic goes here.
 *         ...
 *     }
 * }
 * </pre>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface GridJobStealingDisabled {
    // No-op.
}
