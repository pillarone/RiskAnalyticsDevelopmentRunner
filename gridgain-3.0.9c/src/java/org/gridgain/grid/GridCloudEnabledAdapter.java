// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
*  __  ____/___________(_)______  /__  ____/______ ____(_)_______
*  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
*  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
*  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
*/

package org.gridgain.grid;

import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;
import org.gridgain.grid.util.*;

/**
 * Adapter for cloud enabled entities such as strategies and policies. Such entities can
 * be configured with cloud IDs they should be enabled for. By default, it is enabled for any cloud.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridCloudEnabledAdapter extends GridMetadataAwareAdapter implements GridCloudEnabled {
    /** Cloud identifiers. */
    private Iterable<String> cloudIds;

    /** */
    private final GridMutex mux = new GridMutex();

    /** {@inheritDoc} */
    @Override public boolean isEnabledFor(String cloudId) {
        assert cloudId != null;

        synchronized (mux) {
            if (cloudIds == null)
                return true;

            for (String id : cloudIds)
                if (cloudId.equals(id))
                    return true;
        }

        return false;
    }

    /**
     * Gets ids of clouds this algorithm should be enabled for.
     *
     * @return Cloud ids.
     */
    public Iterable<String> getCloudIds() {
        synchronized (mux) {
            return cloudIds;
        }
    }

    /**
     * Sets ids of clouds this algorithm should be enabled for.
     *
     * @param cloudIds Cloud ids.
     */
    public void setCloudIds(@Nullable Iterable<String> cloudIds) {
        synchronized (mux) {
            this.cloudIds = cloudIds;
        }
    }
}
