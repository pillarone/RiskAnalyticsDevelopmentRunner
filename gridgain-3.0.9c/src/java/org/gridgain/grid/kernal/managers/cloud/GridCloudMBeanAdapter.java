// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.cloud;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Management bean that provides access to {@link GridCloud}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCloudMBeanAdapter implements GridCloudMBean {
    /** Cloud. */
    private final GridCloud cloud;

    /**
     * Creates MBean.
     * 
     * @param cloud Cloud.
     */
    GridCloudMBeanAdapter(GridCloud cloud) {
        assert cloud != null;

        this.cloud = cloud;
    }

    /** {@inheritDoc} */
    @Override public String id() {
        return cloud.id();
    }

    /** {@inheritDoc} */
    @Override public Map<String, String> parameters() {
        return new LinkedHashMap<String, String>(cloud.parameters());
    }

    /** {@inheritDoc} */
    @Override public Collection<String> resourcesFormatted() {
        Collection<GridCloudResource> rsrcs = cloud.resources();

        if (!F.isEmpty(rsrcs))
            return F.transform(rsrcs, F.<GridCloudResource>string());

        return null;
    }
}
