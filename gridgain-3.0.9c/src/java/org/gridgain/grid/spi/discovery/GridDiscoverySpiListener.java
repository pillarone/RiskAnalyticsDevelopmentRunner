// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery;

import org.gridgain.grid.*;

/**
 * Listener for grid node discovery events. See
 * {@link GridDiscoverySpi} for information on how grid nodes get discovered.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridDiscoverySpiListener {
    /**
     * Notification for grid node discovery events.
     *
     * @param type Node discovery event type. See {@link org.gridgain.grid.events.GridDiscoveryEvent}
     * @param node Node affected. Either newly joined node, left node or failed node.
     */
    public void onDiscovery(int type, GridNode node);
}
