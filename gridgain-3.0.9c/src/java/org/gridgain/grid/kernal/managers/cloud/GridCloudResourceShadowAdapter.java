// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.cloud;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.util.*;

/**
 * Convenient adapter for {@link GridCloudResourceShadow}
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCloudResourceShadowAdapter extends GridMetadataAwareAdapter implements GridCloudResourceShadow {
    /** Resource ID. */
    private String id;

    /** Cloud ID. */
    private String cloudId;

    /** Resource type. */
    private int type;

    /** Resource parameters. */
    @GridToStringInclude
    private Map<String, String> params;

    /**
     * Creates cloud resource shadow adapter.
     *
     * @param rsrc Cloud resource.
     */
    GridCloudResourceShadowAdapter(GridCloudResource rsrc) {
        assert rsrc != null;

        id = rsrc.id();
        cloudId = rsrc.cloudId();
        type = rsrc.type();
        params = rsrc.parameters() != null ? Collections.unmodifiableMap(rsrc.parameters()) : null;
    }

    /** {@inheritDoc} */
    @Override public int type() {
        return type;
    }

    /** {@inheritDoc} */
    @Override public String id() {
        return id;
    }

    /** {@inheritDoc} */
    @Override public String cloudId() {
        return cloudId;
    }

    /** {@inheritDoc} */
    @Override public Map<String, String> parameters() {
        return params;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudResourceShadowAdapter.class, this);
    }
}
