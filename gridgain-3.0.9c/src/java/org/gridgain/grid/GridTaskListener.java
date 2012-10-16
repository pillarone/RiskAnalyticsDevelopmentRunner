// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import java.util.*;

/**
 * Deprecated in favor of listener available in {@link GridFuture}.
 * <p>
 * This interface defines task execution listener.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@Deprecated
public interface GridTaskListener extends EventListener {
    /**
     * Deprecated in favor of listener available in {@link GridFuture}.
     * <p>
     * Called when grid task has finished its execution.
     *
     * @param taskFut Task future. The returned future will have completed task result
     *      and method {@link GridTaskFuture#isDone()} will always return {@code true}.
     */
    @Deprecated
    public void onFinished(GridTaskFuture<?> taskFut);
}
