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
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.util.*;
import java.util.concurrent.locks.*;

/**
 * Convenient adapter for {@link GridCloud}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCloudAdapter implements GridCloud {
    /** Read-write lock. */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Cloud ID. */
    private String id;

    /** Cloud version. */
    private long ver;

    /** Cloud resources. */
    @GridToStringInclude
    private Collection<GridCloudResource> rsrcs;

    /** Cloud parameters. */
    @GridToStringInclude
    private Map<String, String> params;

    /**
     * Creates cloud adapter.
     *
     * @param id Cloud ID.
     * @param ver Cloud version.
     * @param rsrcs Cloud resources.
     * @param params Cloud parameters.
     */
    GridCloudAdapter(String id, long ver, Collection<GridCloudResource> rsrcs, Map<String, String> params) {
        assert !F.isEmpty(id);
        assert ver > 0;

        this.id = id;
        this.ver = ver;
        this.rsrcs = F.isEmpty(rsrcs) ? Collections.<GridCloudResource>emptyList() : rsrcs;
        this.params = params != null ? Collections.unmodifiableMap(params) : Collections.<String, String>emptyMap();
    }

    /** {@inheritDoc} */
    @Override public String id() {
        return id;
    }

    /**
     * Gets cloud version.
     *
     * @return Cloud version.
     */
    long version() {
        lock.readLock().lock();

        try {
            return ver;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Sets cloud version.
     *
     * @param ver Cloud version.
     */
    void version(long ver) {
        assert ver > 0;

        lock.writeLock().lock();

        try {
            this.ver = ver;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<GridCloudResource> resources(GridPredicate<? super GridCloudResource>... p) {
        lock.readLock().lock();

        try {
            return F.retain(rsrcs, true, p);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Sets cloud resources.
     *
     * @param rsrcs Cloud resources.
     */
    void resources(Collection<GridCloudResource> rsrcs) {
        lock.writeLock().lock();

        try {
            this.rsrcs = F.isEmpty(rsrcs) ? Collections.<GridCloudResource>emptyList() : rsrcs;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public Map<String, String> parameters() {
        lock.readLock().lock();

        try {
            return params;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    void parameters(Map<String,String> params) {
        lock.writeLock().lock();

        try {
            this.params = params != null ? Collections.unmodifiableMap(params) : Collections.<String, String>emptyMap();
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Finds resource that assigned to this cloud and evaluates to {@code true} for provided predicates.
     *
     * @param p Filter.
     * @return Cloud resource.
     */
    GridCloudResource resource(GridPredicate<? super GridCloudResource>... p) {
        lock.readLock().lock();

        try {
            return F.find(rsrcs, null, p);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCloudAdapter.class, this);
    }
}
