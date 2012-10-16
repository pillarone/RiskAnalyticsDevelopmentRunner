// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.typedef.*;
import java.util.*;

/**
 * Management bean that provides access to {@link GridCache}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCacheMBeanAdapter implements GridCacheMBean {
    /** Cache. */
    private GridCache<?, ?> cache;

    /**
     * Creates MBean;
     *
     * @param cache Cache.
     */
    GridCacheMBeanAdapter(GridCache<?, ?> cache) {
        assert cache != null;

        this.cache = cache;
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return cache.name();
    }

    /** {@inheritDoc} */
    @Override public String metricsFormatted() {
        return String.valueOf(cache.metrics());
    }

    /** {@inheritDoc} */
    @Override public long overflowSize() {
        try {
            return cache.overflowSize();
        } catch (GridException ignored) {
            return -1;
        }
    }

    /** {@inheritDoc} */
    @Override public int size() {
        return cache.size();
    }

    /** {@inheritDoc} */
    @Override public boolean isEmpty() {
        return cache.isEmpty();
    }

    /** {@inheritDoc} */
    @Override public Collection<String> keySetFormatted() {
        Collection<?> keys = cache.keySet();

        if (!F.isEmpty(keys)) {
            return F.transform(keys, F.<Object>string());
        }

        return Collections.emptyList();
    }
}
