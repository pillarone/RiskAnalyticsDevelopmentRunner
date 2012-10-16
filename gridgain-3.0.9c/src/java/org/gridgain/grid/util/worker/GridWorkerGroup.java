// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.worker;

import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Group to which all workers belong. This class contains general
 * management functionality for workers.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public final class GridWorkerGroup {
    /** Value for {@code null} names. */
    private static final String NULL = UUID.randomUUID().toString();

    /** Singleton instance. */
    private static final ConcurrentMap<String, GridWorkerGroup> grps = new ConcurrentHashMap<String, GridWorkerGroup>();

    /** Grid name (only for {@link #toString()} method). */
    private final String gridName;

    /** Map of runnables concurrently executing. */
    private final Map<Thread, GridWorker> activeWorkers = new ConcurrentHashMap<Thread, GridWorker>(100, 0.75f, 64);

    /**
     * Create a group for specified grid.
     *
     * @param gridName Name of grid to create group for. Can be {@code null}.
     */
    private GridWorkerGroup(@Nullable String gridName) {
        this.gridName = gridName;
    }

    /**
     * @return Collection of active groups.
     */
    public static Collection<GridWorkerGroup> getActiveGroups() {
        return new ArrayList<GridWorkerGroup>(grps.values());
    }

    /**
     * Gets singleton instance. This method is called very
     * frequently, for example, every time a job starts and
     * ends execution, and for that reason is well-optimized
     * for concurrent access.
     *
     * @param gridName Name of grid the group is for. Can be {@code null}.
     * @return Singleton instance.
     */
    public static GridWorkerGroup instance(@Nullable String gridName) {
        String name = maskNull(gridName);

        GridWorkerGroup grp = grps.get(name);

        if (grp == null) {
            GridWorkerGroup cur = grps.putIfAbsent(name, grp = new GridWorkerGroup(gridName));

            if (cur != null) {
                grp = cur;
            }
        }

        return grp;
    }

    /**
     * Removes grid runnable group for given grid.
     * This should happen only on grid shutdown.
     *
     * @param gridName Name of grid.
     */
    public static void removeInstance(String gridName) {
        grps.remove(maskNull(gridName));
    }

    /**
     * @param name Name to mask if {@code null}.
     * @return Not {@code null} name.
     */
    private static String maskNull(String name) {
        return name == null ? NULL : name;
    }

    /**
     * @return Grid name.
     */
    public String gridName() {
        return gridName;
    }

    /**
     * Gets currently running runnable tasks.
     *
     * @return Currently running runnable tasks.
     */
    public Set<GridWorker> activeWorkers() {
        return new HashSet<GridWorker>(activeWorkers.values());
    }

    /**
     * @return Worker for current thread.
     */
    @Nullable public GridWorker currentWorker() {
        return activeWorkers.get(Thread.currentThread());
    }

    /**
     * Callback initiated by instances of {@link GridWorker} at execution startup.
     *
     * @param w Worker task that got started.
     */
    void onStart(GridWorker w) {
        // No synchronization for concurrent map.
        activeWorkers.put(w.runner(), w);
    }

    /**
     * Callback initiated by instances of {@link GridWorker} at end of execution.
     *
     * @param w Worker task that is ending.
     */
    void onFinish(GridWorker w) {
        // No synchronization for concurrent map.
        activeWorkers.remove(w.runner());
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridWorkerGroup.class, this);
    }
}
