// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;

/**
 * Management bean that provides access to {@link GridFactory}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridFactoryMBeanAdapter implements GridFactoryMBean {
    /** {@inheritDoc} */
    @Override public String getState() {
        return G.getState().toString();
    }

    /** {@inheritDoc} */
    @Override public String getState(String name) {
        if (name.length() == 0) {
            name = null;
        }

        return G.getState(name).toString();
    }

    /** {@inheritDoc} */
    @Override public boolean stop(boolean cancel) {
        return G.stop(cancel);
    }

    /** {@inheritDoc} */
    @Override public boolean stop(String name, boolean cancel) {
        return G.stop(name, cancel);
    }

    /** {@inheritDoc} */
    @Override public void stopAll(boolean cancel) {
        G.stopAll(cancel);
    }

    /** {@inheritDoc} */
    @Override public boolean stop(boolean cancel, boolean wait) {
        return G.stop(cancel, wait);
    }

    /** {@inheritDoc} */
    @Override public boolean stop(String name, boolean cancel, boolean wait) {
        return G.stop(name, cancel, wait);
    }

    /** {@inheritDoc} */
    @Override public void stopAll(boolean cancel, boolean wait) {
        G.stopAll(cancel, wait);
    }

    /** {@inheritDoc} */
    @Override public void restart(boolean cancel, boolean wait) {
        G.restart(cancel, wait);
    }
}
