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
 * This annotation allows to call a method right before job is submitted to
 * Failover SPI. In this method job can re-create necessary state that was
 * cleared, for example, in method with {@link GridJobAfterSend} annotation.
 * <p>
 * This annotation can be applied to {@link GridJob} instances only. It is
 * invoked no caller node after remote execution has failed and before the
 * job gets failed over to another node.
 * <p>
 * Example:
 * <pre name="code" class="java">
 * public class MyGridJob implements GridJob {
 *     ...
 *     &#64;GridJobBeforeFailover
 *     public void onJobBeforeFailover() {
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
public @interface GridJobBeforeFailover {
    // No-op.
}
