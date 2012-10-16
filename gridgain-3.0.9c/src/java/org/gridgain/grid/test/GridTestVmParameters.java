// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test;

/**
 * GridGain JUnit VM configuration parameters that can be used to
 * override defaults.Note that VM configuration parameters have priority
 * over the same configuration specified in {@link GridifyTest @GridifyTest}
 * annotation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public enum GridTestVmParameters {
    /**
     * Name of VM parameter to disable grid. The value of the parameter
     * should be either {@code true} or {@code false}. The default
     * value is {@code false}.
     * <p>
     * For example, the parameter {@code "-DGRIDGAIN_GRIDGAIN_DISABLED=true"} will
     * disable grid and force all tests to execute locally.
     */
    GRIDGAIN_DISABLED,

    /**
     * Name of VM parameter to specify full class name of JUnit router. By
     * default {@link GridTestRouterAdapter} name is used.
     * <p>
     * For example, the parameter {@code "-DGRIDGAIN_TEST_ROUTER=foo.bar.MyJunitRouter"}
     * will specify a custom JUnit router. The specified router muster have an empty
     * constructor.
     */
    GRIDGAIN_TEST_ROUTER,

    /**
     * Name of VM parameter to specify path to GridGain configuration used
     * to run distributed JUnits. By default {@code "config/junit/junit-spring.xml"}
     * is used.
     * <p>
     * For example, the parameter {@code "-DGRIDGAIN_CONFIG="c:/foo/bar/mygrid-spring.xml"}
     * overrides the default configuration path.
     */
    GRIDGAIN_CONFIG,

    /**
     * Optional timeout in milliseconds for distributed test suites. By default,
     * test suites never timeout.
     * For example, the parameter {@code "-DGRIDGAIN_TEST_TIMEOUT=600000"} will
     * stop test suite execution after {@code 10} minutes.
     */
    GRIDGAIN_TEST_TIMEOUT,

    /**
     * Name of VM parameter to specify whether tests should be preferably routed
     * to remote nodes. This parameter is used by {@link GridTestRouterAdapter} which
     * will use remote nodes if there any, otherwise local node will still be used.
     * <p>
     * The value of this parameter is either {@code true} or {@code false}. For example,
     * the parameter {@code "-DGRIDGAIN_ROUTER_PREFER_REMOTE=true"} will tell the router
     * to prefer remote nodes for execution.
     */
    GRIDGAIN_ROUTER_PREFER_REMOTE,

    /**
     * Name of VM parameter that specifies name of the grid started for distributed junits.
     * This parameter should not be set explicitely. GridGain will detect grid name from
     * the configuration file and set it as system properties, so nested JUnit tests or
     * suites will be able to detect if grid has been started to avoid double starts.
     */
    GRIDGAIN_NAME,
}
