// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.lang.*;
import java.util.*;

/**
 * This interface is deprecated in favor of new listening methods:
 * <ul>
 * <li>{@link GridProjection#listen(GridPredicate2[])}</li>
 * <li>{@link GridProjection#remoteListenAsync(GridPredicate, GridPredicate2[])}</li>
 * <li>{@link GridProjection#remoteListenAsync(Collection, GridPredicate2[])}</li>
 * </ul>
 * Listener for messages received from remote nodes. Messages can be sent to remote
 * nodes via the following methods:
 * <ul>
 * <li>{@link GridProjection#send(Object, org.gridgain.grid.lang.GridPredicate[])}</li>
 * <li>{@link GridProjection#send(Collection, org.gridgain.grid.lang.GridPredicate[])}</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Deprecated
public interface GridMessageListener extends EventListener {
    /**
     * Notification for received messages.
     *
     * @param nodeId ID of node that sent the message. Note that may have already
     *      left topology by the time this message is received.
     * @param msg Message received.
     */
    @Deprecated
    public void onMessage(UUID nodeId, Object msg);
}
