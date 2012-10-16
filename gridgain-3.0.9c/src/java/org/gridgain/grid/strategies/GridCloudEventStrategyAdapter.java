// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.strategies;

import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;

/**
 * Cloud strategy adapter event based strategies. Subclasses should implement
 * {@link #onEvent(GridEvent)} method.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCloudEventStrategyAdapter extends GridCloudEnabledAdapter implements GridCloudStrategy {
    /** Grid instance. */
    @GridInstanceResource
    private Grid grid;

    /** Logger. */
    @GridLoggerResource
    private GridLogger log;

    /** */
    private final GridMutex mux = new GridMutex();

    /** Event types to sign on. */
    private int[] evtTypes;

    /** */
    private GridLocalEventListener lsnr = new GridLocalEventListener() {
        @Override public void onEvent(GridEvent evt) {
            GridCloudEventStrategyAdapter.this.onEvent(evt);
        }
    };

    /** {@inheritDoc} */
    @Override public void activate() throws GridException {
        assert grid != null;

        int[] types;

        synchronized (mux) {
            types = evtTypes;
        }

        grid.addLocalEventListener(lsnr, types);
    }

    /**
     * Called on event.
     *
     * @param evt Event occurred.
     */
    public abstract void onEvent(GridEvent evt);

    /** {@inheritDoc} */
    @Override public void deactivate() {
        grid.removeLocalEventListener(lsnr);
    }

    /**
     * Sets event types. This method should be called before activation.
     *
     * @param evtTypes Event types.
     */
    public void setEventTypes(int... evtTypes) {
        synchronized (mux) {
            this.evtTypes = evtTypes;
        }
    }

    /**
     * Gets event types.
     *
     * @return Event types.
     */
    public int[] getEventTypes() {
        synchronized (mux) {
            return evtTypes;
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudEventStrategyAdapter.class, this);
    }
}
