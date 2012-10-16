// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.collision;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.spi.collision.*;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * This class defines a collision manager.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCollisionManager extends GridManagerAdapter<GridCollisionSpi> {
    /** */
    private final AtomicReference<GridCollisionExternalListener> extListener =
        new AtomicReference<GridCollisionExternalListener>(null);

    /**
     * @param ctx Grid kernal context.
     */
    public GridCollisionManager(GridKernalContext ctx) {
        super(GridCollisionSpi.class, ctx, ctx.config().getCollisionSpi());
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        startSpi();

        getSpi().setExternalCollisionListener(new GridCollisionExternalListener() {
            @Override public void onExternalCollision() {
                GridCollisionExternalListener lsnr = extListener.get();

                if (lsnr != null)
                    lsnr.onExternalCollision();
            }
        });

        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void stop() throws GridException {
        stopSpi();

        // Unsubscribe.
        getSpi().setExternalCollisionListener(null);

        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /**
     * @param lsnr Listener to external collision events.
     */
    public void setCollisionExternalListener(GridCollisionExternalListener lsnr) {
        if (lsnr != null && !extListener.compareAndSet(null, lsnr))
            assert false : "Collision external listener has already been set " +
                "(perhaps need to add support for multiple listeners)";
        else if (log.isDebugEnabled())
            log.debug("Successfully set external collision listener: " + lsnr);
    }

    /**
     * @param waitJobs List of waiting jobs.
     * @param activeJobs List of active jobs.
     */
    public void onCollision(Collection<GridCollisionJobContext> waitJobs,
        Collection<GridCollisionJobContext> activeJobs) {

        // Do not log "empty" collision resolution.
        if (!waitJobs.isEmpty() || !activeJobs.isEmpty()) {
            if (log.isDebugEnabled())
                log.debug("Resolving job collisions [waitJobs=" + waitJobs + ", activeJobs=" + activeJobs + ']');
        }

        getSpi().onCollision(waitJobs, activeJobs);
    }
}
