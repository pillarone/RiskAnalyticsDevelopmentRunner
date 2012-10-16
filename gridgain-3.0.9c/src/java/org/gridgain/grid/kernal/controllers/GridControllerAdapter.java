// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.util.tostring.*;

/**
 * Convenient adapter for grid controllers.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridControllerAdapter implements GridController {
    /** Kernal context. */
    @GridToStringExclude
    protected GridKernalContext ctx;

    /** Grid logger. */
    @GridToStringExclude
    protected GridLogger log;

    /**
     * {@inheritDoc}
     */
    @Override public void init() throws GridException {
        // No-op.
    }

    /**
     * {@inheritDoc}
     */
    @Override public boolean implemented() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override public void afterKernalStart(GridKernalContext ctx) throws GridException {
        assert ctx != null;

        this.ctx = ctx;

        log = ctx.config().getGridLogger().getLogger(getClass());
    }

    /**
     * {@inheritDoc}
     */
    @Override public void beforeKernalStop(boolean cancel) {
        // No-op.
    }
}
