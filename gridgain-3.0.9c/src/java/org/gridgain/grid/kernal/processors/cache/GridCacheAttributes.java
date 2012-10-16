// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.cache.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;

/**
 * Cache attributes.
 * <p>
 * This class contains information on a single cache configured on some node.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheAttributes implements Externalizable {
    /** Cache name. */
    private String cacheName;

    /** Cache mode. */
    private GridCacheMode cacheMode;

    /** Near cache enabled flag. */
    private boolean nearCacheEnabled;

    /** Preload mode. */
    private GridCachePreloadMode preloadMode;

    /** Affinity class name. */
    private String affClsName;

    /**
     * @param cacheName Cache name.
     * @param cacheMode Cache mode.
     * @param nearCacheEnabled Near cache enabled flag.
     * @param preloadMode Preload mode.
     * @param affClsName Affinity class name.
     */
    public GridCacheAttributes(String cacheName, GridCacheMode cacheMode, boolean nearCacheEnabled,
        GridCachePreloadMode preloadMode, String affClsName) {
        this.cacheName = cacheName;
        this.cacheMode = cacheMode;
        this.nearCacheEnabled = nearCacheEnabled;
        this.preloadMode = preloadMode;
        this.affClsName = affClsName;
    }

    /**
     * Public no-arg constructor for {@link Externalizable}.
     */
    public GridCacheAttributes() {
        // No-op.
    }

    /**
     * @return Cache name.
     */
    public String cacheName() {
        return cacheName;
    }

    /**
     * @return Cache mode.
     */
    public GridCacheMode cacheMode() {
        return cacheMode;
    }

    /**
     * @return {@code True} if near cache is enabled.
     */
    public boolean nearCacheEnabled() {
        return nearCacheEnabled;
    }

    /**
     * @return Preload mode.
     */
    public GridCachePreloadMode cachePreloadMode() {
        return preloadMode;
    }

    /**
     * @return Affinity class name.
     */
    public String cacheAffinityClassName() {
        return affClsName;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, cacheName);
        U.writeEnum(out, cacheMode);
        out.writeBoolean(nearCacheEnabled);
        U.writeEnum(out, preloadMode);
        U.writeString(out, affClsName);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        cacheName = U.readString(in);
        cacheMode = U.readEnum(in, GridCacheMode.class);
        nearCacheEnabled = in.readBoolean();
        preloadMode = U.readEnum(in, GridCachePreloadMode.class);
        affClsName = U.readString(in);
    }
}
