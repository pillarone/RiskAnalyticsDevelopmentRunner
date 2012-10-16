// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.session;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTaskSessionProcessor extends GridProcessorAdapter {
    /** Sessions. */
    private final ConcurrentMap<UUID, GridTuple2<GridTaskSessionImpl, Integer>> sesMap =
        new ConcurrentHashMap<UUID, GridTuple2<GridTaskSessionImpl, Integer>>();

    /**
     * @param ctx Grid kernal context.
     */
    public GridTaskSessionProcessor(GridKernalContext ctx) {
        super(ctx);
    }

    /**
     * Starts session processor.
     */
    @Override public void start() throws GridException {
        if (log.isDebugEnabled()) {
            log.debug("Session processor started.");
        }
    }

    /**
     * Stops session processor.
     */
    @Override public void stop(boolean cancel, boolean wait) throws GridException {
        if (log.isDebugEnabled()) {
            log.debug("Session processor stopped.");
        }
    }

    /**
     * @param sesId Session ID.
     * @param taskNodeId Task node ID.
     * @param taskName Task name.
     * @param userVer Deployment user version.
     * @param seqNum Deployment sequence number.
     * @param taskClsName Task class name.
     * @param startTime Execution start time.
     * @param endTime Execution end time.
     * @param siblings Collection of siblings.
     * @param attrs Map of attributes.
     * @return New session if one did not exist, or existing one.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    public GridTaskSessionImpl createTaskSession(
        UUID sesId,
        UUID taskNodeId,
        String taskName,
        String userVer,
        Long seqNum,
        String taskClsName,
        long startTime,
        long endTime,
        Collection<GridJobSibling> siblings,
        Map<Object, Object> attrs
    ) {
        while (true) {
            GridTuple2<GridTaskSessionImpl, Integer> sesTup = sesMap.get(sesId);

            if (sesTup == null) {
                GridTuple2<GridTaskSessionImpl, Integer> t = sesMap.putIfAbsent(
                    sesId,
                    sesTup = F.t(
                        new GridTaskSessionImpl(
                            taskNodeId,
                            taskName,
                            userVer,
                            seqNum,
                            taskClsName,
                            sesId,
                            startTime,
                            endTime,
                            siblings,
                            attrs,
                            ctx

                        ), 1)
                );

                if (t == null) {
                    return sesTup.get1();
                }

                sesTup = t;
            }

            synchronized (sesTup) {
                if (sesTup.get2() == 0) {
                    // Don't increment count 0 as it may have been removed by
                    // another thread already. Instead, remove it and try again.
                    sesMap.remove(sesId, sesTup);

                    continue;
                }

                // Increment usage.
                sesTup.set2(sesTup.get2() + 1);

                return sesTup.get1();
            }
        }
    }

    /**
     * @param sesId Session ID.
     * @return Session for a given session ID.
     */
    @Nullable public GridTaskSessionImpl getSession(UUID sesId) {
        GridTuple2<GridTaskSessionImpl, Integer> sesTup = sesMap.get(sesId);

        return sesTup == null ? null : sesTup.get1();
    }

    /**
     * Removes session for a given session ID.
     *
     * @param sesId ID of session to remove.
     * @return {@code True} if session was removed.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    public boolean removeSession(UUID sesId) {
        GridTuple2<GridTaskSessionImpl, Integer> sesTup = sesMap.get(sesId);

        if (sesTup != null) {
            synchronized (sesTup) {
                // Decrement usage.
                sesTup.set2(sesTup.get2() - 1);

                // Clear memory if there is no more usage.
                if (sesTup.get2() == 0) {
                    return sesMap.remove(sesId, sesTup);
                }
            }

            return false;
        }

        return true;
    }
}
