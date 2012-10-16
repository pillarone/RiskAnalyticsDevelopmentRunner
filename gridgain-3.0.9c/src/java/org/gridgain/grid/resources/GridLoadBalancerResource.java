// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.resources;

import org.gridgain.grid.*;
import org.gridgain.grid.spi.loadbalancing.*;
import java.lang.annotation.*;

/**
 * Annotates a field or a setter method for injection of {@link GridLoadBalancer}.
 * Specific implementation for grid load balancer is defined by
 * {@link GridLoadBalancingSpi}
 * which is provided to grid via {@link GridConfiguration}..
 * <p>
 * Load balancer can be injected into instances of following classes:
 * <ul>
 * <li>{@link GridTask}</li>
 * </ul>
 * <p>
 * Here is how injection would typically happen:
 * <pre name="code" class="java">
 * public class MyGridTask extends GridTask&lt;String, Integer&gt; {
 *    &#64;GridLoadBalancerResource
 *    private GridLoadBalancer balancer;
 * }
 * </pre>
 * or
 * <pre name="code" class="java">
 * public class MyGridTask extends GridTask&lt;String, Integer&gt; {
 *     ...
 *     private GridLoadBalancer balancer;
 *     ...
 *     &#64;GridLoadBalancerResource
 *     public void setBalancer(GridLoadBalancer balancer) {
 *         this.balancer = balancer;
 *     }
 *     ...
 * }
 * </pre>
 * <p>
 * See {@link GridConfiguration#getLoadBalancingSpi()} for Grid configuration details.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface GridLoadBalancerResource {
    // No-op.
}
