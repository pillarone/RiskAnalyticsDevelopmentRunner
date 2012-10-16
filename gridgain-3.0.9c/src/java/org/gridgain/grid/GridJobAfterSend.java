// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import java.lang.annotation.*;

/**
 * This annotation allows to call a method right after the job has been
 * successfully sent for execution. It is useful to clean up the internal
 * state of the job when it is not immediately needed while maintaining
 * effective memory management.
 * <p>
 * This annotation can be applied to {@link GridJob} instance only. It is invoked
 * on the caller node after the job has been sent to remote node for execution.
 * <p>
 * Example:
 * <pre name="code" class="java">
 * public class MyGridJob implements GridJob {
 *     ...
 *     &#64;GridJobAfterSend
 *     public void onJobAfterSend() {
 *          ...
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
@Target({ElementType.METHOD})
public @interface GridJobAfterSend {
    // No-op.
}
