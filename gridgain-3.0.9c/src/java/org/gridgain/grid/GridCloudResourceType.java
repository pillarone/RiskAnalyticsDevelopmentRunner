// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

/**
 * Defines set of built-in cloud resources. These are defined as constants vs. enumeration
 * to let users to define their own resources.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCloudResourceType {
    /**
     * Cloud instance resource.
     * <p>
     * Note that all values below <tt>1000</tt> are reserved for internal GridGain usage.
     */
    public static final int CLD_INSTANCE = 1;

    /**
     * Cloud RAM resource.
     * <p>
     * Note that all values below <tt>1000</tt> are reserved for internal GridGain usage.
     */
    public static final int CLD_RAM = 2;

    /**
     * Cloud CPU resource.
     * <p>
     * Note that all values below <tt>1000</tt> are reserved for internal GridGain usage.
     */
    public static final int CLD_CPU = 3;

    /**
     * Cloud storage resource.
     * <p>
     * Note that all values below <tt>1000</tt> are reserved for internal GridGain usage.
     */
    public static final int CLD_STORAGE = 4;

    /**
     * Cloud bandwidth resource.
     * <p>
     * Note that all values below <tt>1000</tt> are reserved for internal GridGain usage.
     */
    public static final int CLD_BANDWIDTH = 5;

    /**
     * Cloud image.
     * <p>
     * Note that all values below <tt>1000</tt> are reserved for internal GridGain usage.
     */
    public static final int CLD_IMAGE = 6;

    /**
     * Cloud GridGain node.
     * <p>
     * Note that all values below <tt>1000</tt> are reserved for internal GridGain usage.
     */
    public static final int CLD_NODE = 7;

    /**
     * Cloud profile.
     * <p>
     * Note that all values below <tt>1000</tt> are reserved for internal GridGain usage.
     */
    public static final int CLD_PROFILE = 8;

    /**
     * Cloud security group.
     * <p>
     * Note that all values below <tt>1000</tt> are reserved for internal GridGain usage.
     */
    public static final int CLD_SECURITY_GROUP = 9;
}
