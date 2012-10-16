// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.topologystore.vm;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.discovery.tcp.topologystore.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.spi.discovery.tcp.topologystore.GridTcpDiscoveryTopologyStoreNodeState.*;

/**
 * Local JVM-based topology store.
 * <h1 class="header">Configuration</h1>
 * There are no configuration parameters.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridTcpDiscoveryVmTopologyStore implements GridTcpDiscoveryTopologyStore {
    /** Map to store nodes. */
    @GridToStringInclude
    private final NavigableMap<Long, GridTcpDiscoveryTopologyStoreNode> store =
        new TreeMap<Long, GridTcpDiscoveryTopologyStoreNode>();

    /** Topology version. */
    private long topVer;

    /** Lock. */
    @GridToStringExclude
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** {@inheritDoc} */
    @Override public long topologyVersion() {
        lock.readLock().lock();

        try {
            return topVer;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public void clear() {
        lock.writeLock().lock();

        try {
            topVer = 0;

            store.clear();
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridTcpDiscoveryTopologyStoreNode> nodes(long minTopVer, long maxTopVer) {
        assert minTopVer >= 0;
        assert maxTopVer > minTopVer;

        lock.readLock().lock();

        try {
            return new LinkedList<GridTcpDiscoveryTopologyStoreNode>(X.cloneObject(store.tailMap(minTopVer, false).
                headMap(maxTopVer, true).values(), true, true));
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public void put(GridTcpDiscoveryTopologyStoreNode node) {
        assert node != null;
        assert node.order() > 0;
        assert node.state() != null;

        lock.writeLock().lock();

        try {
            node = X.cloneObject(node, true, true);

            assert node != null;

            // Increment topology version and put node to store.
            node.topologyVersion(++topVer);

            store.put(topVer, node);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public void evict(long maxTopVer) {
        assert maxTopVer > 0;

        lock.writeLock().lock();

        try {
            Collection<UUID> evictedNodes = new HashSet<UUID>();

            for (Iterator<Map.Entry<Long, GridTcpDiscoveryTopologyStoreNode>> iter = store.headMap(maxTopVer, true).
                descendingMap().entrySet().iterator(); iter.hasNext(); ) {
                GridTcpDiscoveryTopologyStoreNode node = iter.next().getValue();

                if (node.state() == LEAVING || node.state() == FAILED)
                    evictedNodes.add(node.id());

                if (evictedNodes.contains(node.id()))
                    iter.remove();
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridTcpDiscoveryTopologyStoreNodeState state(UUID id) throws GridSpiException {
        assert id != null;

        lock.readLock().lock();

        try {
            for (GridTcpDiscoveryTopologyStoreNode node : store.descendingMap().values())
                if (id.equals(node.id()))
                    return node.state();

            return null;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpDiscoveryVmTopologyStore.class, this);
    }
}
