// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.checkpoint.coherence;

import org.gridgain.grid.logger.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.checkpoint.*;
import org.gridgain.grid.typedef.internal.*;
import java.util.*;

/**
 * Implementation of {@link GridSpiThread} that takes care about outdated check point data.
 * Every checkpoint has expiration date after which it makes no sense to
 * keep it. This class periodically compares files last access time with given
 * expiration time.
 * <p>
 * If this data was not accessed then it is deleted.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCoherenceTimeoutTask extends GridSpiThread {
    /** List of data with  access and expiration date. */
    private Map<String, GridCoherenceTimeData> map = new HashMap<String, GridCoherenceTimeData>();

    /** Messages logger. */
    private GridLogger log;

    /** */
    private final Object mux = new Object();

    /** Timeout listener. */
    private GridCheckpointListener lsnr;

    /**
     * Creates new instance of task that looks after data.
     *
     * @param gridName Grid name.
     * @param log Messages logger.
     */
    GridCoherenceTimeoutTask(String gridName, GridLogger log) {
        super(gridName, "grid-checkpoint-coherence-timeout-worker", log);

        assert log != null;

        this.log = log.getLogger(getClass());
    }

    /** {@inheritDoc} */
    @Override public void body() throws InterruptedException {
        long nextTime = 0;

        Collection<String> delCpKeys = new HashSet<String>();

        while (!isInterrupted()) {
            delCpKeys.clear();

            synchronized (mux) {
                long delay = System.currentTimeMillis() - nextTime;

                if (nextTime != 0 && delay > 0) {
                    mux.wait(delay);
                }

                long now = System.currentTimeMillis();

                nextTime = -1;

                // check map one by one and physically remove
                // if (now - last modification date) > expiration time
                for (Iterator<Map.Entry<String, GridCoherenceTimeData>> iter = map.entrySet().iterator();
                    iter.hasNext();) {
                    Map.Entry<String, GridCoherenceTimeData> entry = iter.next();

                    String key = entry.getKey();
                    GridCoherenceTimeData timeData = entry.getValue();

                    if (timeData.getExpireTime() > 0) {
                        if (timeData.getExpireTime() <= now) {
                            iter.remove();

                            delCpKeys.add(timeData.getKey());

                            if (log.isDebugEnabled()) {
                                log.debug("Data was deleted by timeout: " + key);
                            }
                        }
                        else {
                            if (timeData.getExpireTime() < nextTime || nextTime == -1) {
                                nextTime = timeData.getExpireTime();
                            }
                        }
                    }
                }
            }

            GridCheckpointListener tmp = lsnr;

            if (tmp != null) {
                for (String key : delCpKeys) {
                    tmp.onCheckpointRemoved(key);
                }
            }
        }

        synchronized (mux) {
            map.clear();
        }
    }

    /**
     * Adds data to a list of files this task should look after.
     *
     * @param timeData  File expiration and access information.
     */
    void add(GridCoherenceTimeData timeData) {
        assert timeData != null;

        synchronized (mux) {
            map.put(timeData.getKey(), timeData);

            mux.notifyAll();
        }
    }

    /**
     * Adds list of data this task should looks after.
     *
     * @param newData List of data.
     */
    void add(Iterable<GridCoherenceTimeData> newData) {
        assert newData != null;

        synchronized (mux) {
            for(GridCoherenceTimeData data : newData) {
                map.put(data.getKey(), data);
            }

            mux.notifyAll();
        }
    }

    /**
     * Removes data.
     *
     * @param key TODO
     */
    public void remove(String key) {
        assert key != null;

        synchronized (mux) {
            map.remove(key);
        }
    }

    /**
     * Sets listener.
     *
     * @param lsnr Listener.
     */
    void setCheckpointListener(GridCheckpointListener lsnr) {
        this.lsnr = lsnr;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCoherenceTimeoutTask.class, this);
    }
}
