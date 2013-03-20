// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.cloud;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.util.*;

/**
 * Convenient adapter for {@link GridCloudSpiSnapshot} supporting metadata. This class is intended
 * for use by SPI implementations.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCloudSpiSnapshotAdapter extends GridMetadataAwareAdapter implements GridCloudSpiSnapshot {
    /** Snapshot ID. */
    private UUID id;
    
    /** Snapshot time. */
    private long time;

    /** Cloud coordinator node ID. */
    private UUID crdId;

    /** Cloud resources. */
    @GridToStringInclude
    private Collection<GridCloudResource> rsrcs;

    /** Cloud parameters. */
    @GridToStringInclude
    private Map<String, String> params;

    /**
     * Creates snapshot.
     *
     * @param id Snapshot ID.
     * @param time Snapshot time.
     * @param crdId Cloud coordinator node ID.
     * @param rsrcs Cloud resources.
     * @param params Cloud parameters.
     */
    public GridCloudSpiSnapshotAdapter(UUID id, long time, UUID crdId, Collection<GridCloudResource> rsrcs,
        Map<String, String> params) {
        assert id != null;
        assert time > 0;
        assert crdId != null;
        assert rsrcs != null;
        assert params != null;
        
        this.id = id;
        this.time = time;
        this.crdId = crdId;
        this.rsrcs = rsrcs;
        this.params = params;
    }

    /** {@inheritDoc} */
    @Override public UUID getId() {
        return id;
    }

    /** {@inheritDoc} */
    @Override public long getTime() {
        return time;
    }

    /** {@inheritDoc} */
    @Override public UUID getCoordinatorNodeId() {
        return crdId;
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCloudResource> getResources() {
        return rsrcs;
    }

    /** {@inheritDoc} */
    @Override public Map<String, String> getParameters() {
        return params;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudSpiSnapshotAdapter.class, this);
    }
}
