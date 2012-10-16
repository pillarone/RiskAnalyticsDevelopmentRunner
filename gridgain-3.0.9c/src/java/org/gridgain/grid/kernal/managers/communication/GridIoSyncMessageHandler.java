// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.communication;

import org.gridgain.grid.*;
import java.util.*;

/**
 * Message handler for synchronous communication messages.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @param <M> Type of message.
 * @param <R> Type of result.
 */
public interface GridIoSyncMessageHandler<M, R> {
    /**
     * Handles synchronous message and returns response.
     *
     * @param nodeId Sender node ID.
     * @param msg Received message.
     * @return Response to message.
     * @throws GridException If message processing failed.
     */
    public R handleMessage(UUID nodeId, M msg) throws GridException;
}
