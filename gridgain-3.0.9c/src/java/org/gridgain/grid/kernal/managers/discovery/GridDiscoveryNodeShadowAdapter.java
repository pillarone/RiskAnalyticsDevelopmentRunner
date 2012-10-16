// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.discovery;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.util.*;

import static org.gridgain.grid.kernal.GridNodeAttributes.*;

/**
 * Convenient adapter for {@link GridNodeShadow}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridDiscoveryNodeShadowAdapter extends GridMetadataAwareAdapter implements GridNodeShadow {
    /** Node ID. */
    private UUID id;

    /** Node attributes. */
    @GridToStringExclude
    private Map<String, Object> attrs;

    /** Internal addresses. */
    @GridToStringInclude
    private Collection<String> intAddrs;

    /** External addresses. */
    @GridToStringInclude
    private Collection<String> extAddrs;

    /** Node startup order. */
    private long order;

    /** Creation timestamp. */
    private long created;

    /** */
    private boolean daemon;

    /** Last metrics snapshot. */
    @GridToStringExclude
    private GridNodeMetrics lastMetrics;

    /**
     * Creates node shadow adapter.
     *
     * @param node Node.
     */
    GridDiscoveryNodeShadowAdapter(GridNode node) {
        assert node != null;

        created = System.currentTimeMillis();
        id = node.id();
        attrs = Collections.unmodifiableMap(node.attributes());
        intAddrs = Collections.unmodifiableCollection(node.internalAddresses());
        extAddrs = Collections.unmodifiableCollection(node.externalAddresses());
        order = node.order();
        lastMetrics = node.metrics();
        daemon = "true".equalsIgnoreCase(this.<String>attribute(ATTR_DAEMON));
    }

    /** {@inheritDoc} */
    @Override public boolean isDaemon() {
        return daemon;
    }

    /** {@inheritDoc} */
    @Override public long created() {
        return created;
    }

    /** {@inheritDoc} */
    @Override public GridNodeMetrics lastMetrics() {
        return lastMetrics;
    }

    /** {@inheritDoc} */
    @Override public UUID id() {
        return id;
    }

    /** {@inheritDoc} */
    @Override public String gridName() {
        return attribute(ATTR_GRID_NAME);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <T> T attribute(String name) {
        return (T)attrs.get(name);
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> attributes() {
        return attrs;
    }

    /** {@inheritDoc} */
    @Override public Collection<String> internalAddresses() {
        return intAddrs;
    }

    /** {@inheritDoc} */
    @Override public Collection<String> externalAddresses() {
        return extAddrs;
    }

    /** {@inheritDoc} */
    @Override public long order() {
        return order;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDiscoveryNodeShadowAdapter.class, this, "gridName", gridName());
    }
}
