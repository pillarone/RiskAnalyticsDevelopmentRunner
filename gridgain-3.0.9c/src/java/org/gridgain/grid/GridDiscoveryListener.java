// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.events.*;
import java.util.*;

/**
 * Deprecated in favor of new unified event management. See the following:
 * <ul>
 * <li>{@link GridEvent}</li>
 * <li>{@link Grid#addLocalEventListener(GridLocalEventListener, int...)}</li>
 * <li>{@link GridDiscoveryEvent}</li>
 * <li>{@link GridEventType}</li>
 * </ul>
 * <p>
 * Listener for grid node discovery events.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Deprecated
public interface GridDiscoveryListener extends EventListener {
    /**
     * Deprecated in favor of new unified event management. See the following:
     * <ul>
     * <li>{@link GridEvent}</li>
     * <li>{@link Grid#addLocalEventListener(GridLocalEventListener, int...)}</li>
     * <li>{@link GridDiscoveryEvent}</li>
     * <li>{@link GridEventType}</li>
     * </ul>
     * <p>
     * Notification for grid node discovery events.
     *
     * @param type Node discovery event type.
     * @param node Node affected. Either newly joined node, left node or failed node.
     */
    @Deprecated
    public void onDiscovery(GridDiscoveryEventType type, GridNode node);
}
