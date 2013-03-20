// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.cloud;

/**
 * Defines types of actions for cloud resources. Note that cloud SPI supports only three
 * types of actions on its resources:
 * <ul>
 * <li>Resource can be newly added, i.e. resource didn't exist before and appears in the cloud
 *      snapshot for the first time.
 * <li>Resource changed, i.e. an existing resource's internal state was changed.
 * <li>Resource removed, i.e. previously existing resource doesn't appear in the cloud
 *      snapshot anymore.
 * <ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public enum GridCloudSpiResourceAction {
    /**
     * Cloud resource added. Resource didn't exist before and appears in the cloud
     * snapshot for the first time.
     */
    ADDED,

    /**
     * Cloud resource changed. An existing resource's internal state was changed.
     */
    CHANGED,

    /**
     * Cloud resource removed. Previously existing resource doesn't appear in the cloud
     * snapshot anymore. 
     */
    REMOVED
}
