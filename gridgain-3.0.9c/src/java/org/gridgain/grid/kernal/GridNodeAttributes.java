// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

/**
 * This class defines constants (NOT enums) for internal node attributes.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public final class GridNodeAttributes {
    /** Prefix for internally reserved attribute names. */
    static final String ATTR_PREFIX = "org.gridgain";

    /** Internal attribute name constant. */
    public static final String ATTR_BUILD_VER = ATTR_PREFIX + ".build.ver";

    /** Internal attribute name constant. */
    public static final String ATTR_ENT_EDITION = ATTR_PREFIX + ".ent.edition";

    /** Internal attribute name constant. */
    public static final String ATTR_JIT_NAME = ATTR_PREFIX + ".jit.name";

    /** Internal attribute name constant. */
    public static final String ATTR_LANG_RUNTIME = ATTR_PREFIX + ".lang.rt";

    /** Internal attribute name constant. */
    public static final String ATTR_USER_NAME = ATTR_PREFIX + ".user.name";

    /** Internal attribute name constant. */
    public static final String ATTR_GRID_NAME = ATTR_PREFIX + ".grid.name";

    /** Deployment mode. */
    public static final String ATTR_DEPLOYMENT_MODE = ATTR_PREFIX + ".grid.dep.mode";

    /** Internal attribute name postfix constant. */
    public static final String ATTR_SPI_VER = ATTR_PREFIX + ".spi.ver";

    /** Internal attribute name postfix constant. */
    public static final String ATTR_SPI_CLASS = ATTR_PREFIX + ".spi.class";

    /** Internal attribute name constant. */
    public static final String ATTR_CACHE = ATTR_PREFIX + ".cache";

    /** Internal attribute name constant. */
    public static final String ATTR_CLOUD_IDS = ATTR_PREFIX + ".cloud.ids";

    /** Internal attribute name constant. */
    public static final String ATTR_DAEMON = ATTR_PREFIX + ".daemon";

    /** Internal attribute name constant. */
    public static final String ATTR_JMX_PORT = ATTR_PREFIX + ".jmx.port";

    /** Internal attribute name constant. */
    public static final String ATTR_IPS = ATTR_PREFIX + ".ips";

    /** Internal attribute name constant. */
    public static final String ATTR_MACS = ATTR_PREFIX + ".macs";

    /** Internal attribute name constant. */
    public static final String ATTR_CLOUD_CRD_DISABLE = ATTR_PREFIX + ".cloud.crd.disable";

    /**
     * Enforces singleton.
     */
    private GridNodeAttributes() {
        /* No-op. */
    }
}
