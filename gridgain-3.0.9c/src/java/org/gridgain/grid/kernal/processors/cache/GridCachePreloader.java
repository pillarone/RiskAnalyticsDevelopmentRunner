// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;

import java.util.*;

/**
 * Cache preloader that is responsible for loading cache entries either from remote
 * nodes (for distributed cache) or anywhere else at cache startup. 
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridCachePreloader<K, V> {
    /**
     * Starts preloading.
     *
     * @throws GridException If start failed.
     */
    public void start() throws GridException;

    /**
     * Stops preloading.
     */
    public void stop();

    /**
     * Kernal start callback.
     *
     * @throws GridException If failed.
     */
    public void onKernalStart() throws GridException;

    /**
     * Kernal stop callback.
     */
    public void onKernalStop();

    /**
     * @return Future which will complete when preloader is safe to use.
     */
    public GridFuture<?> startFuture();

    /**
     * Requests that preloader sends the request for the key.
     *
     * @param keys Keys to request.
     * @return Future to complete when all keys are preloaded.
     */
    public GridFuture<Object> request(Collection<? extends K> keys);
}
