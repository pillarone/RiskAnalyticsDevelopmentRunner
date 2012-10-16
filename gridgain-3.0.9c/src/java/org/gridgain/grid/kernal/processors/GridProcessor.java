// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors;

import org.gridgain.grid.*;
import org.gridgain.grid.util.tostring.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
public interface GridProcessor {
    /**
     * Starts grid processor.
     *
     * @throws GridException Throws in case of any errors.
     */
    public void start() throws GridException;

    /**
     * Stops grid processor.
     *
     * @param cancel If {@code true}, then all ongoing tasks or jobs for relevant
     *      processors need to be cancelled.
     * @param wait Flag indicating whether to wait for task completion.
     * @throws GridException Thrown in case of any errors.
     */
    public void stop(boolean cancel, boolean wait) throws GridException;

    /**
     * Callback that notifies that kernal has successfully started,
     * including all managers and processors.
     *
     * @throws GridException Thrown in case of any errors.
     */
    public void onKernalStart() throws GridException;

    /**
     * Callback to notify that kernal is about to stop.
     *
     * @param cancel Flag indicating whether jobs should be canceled.
     * @param wait Flag indicating whether to wait for task completion.
     */
    public void onKernalStop(boolean cancel, boolean wait);
}
