// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.loadbalancing.affinity;

import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.locks.*;

/**
 * Controls data to node affinity. For reference on algorithm see
 * <a href="http://weblogs.java.net/blog/tomwhite/archive/2007/11/consistent_hash.html">Tom White's Blog</a>.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridAffinity {
    /** Grid logger. */
    private final GridLogger log;

    /** Affinity seed. */
    private final String affSeed;

    /** Map of hash assignments. */
    private final SortedMap<Integer, GridNode> circle = new TreeMap<Integer, GridNode>();

    /** Flag indicating whether exception has been logged for hash function. */
    private boolean isErrLogged;

    /** Read/write lock. */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * @param affSeed Affinity seed.
     * @param log Grid logger.
     */
    GridAffinity(String affSeed, GridLogger log) {
        assert affSeed != null;
        assert log != null;

        this.affSeed = affSeed;
        this.log = log;
    }

    /**
     * Adds a node.
     *
     * @param node New node.
     * @param replicas Number of replicas for the node.
     */
    void add(GridNode node, int replicas) {
        String prefix = affSeed + node.id().toString();

        rwLock.writeLock().lock();

        try {
            for (int i = 0; i < replicas; i++) {
                circle.put(hash(prefix + i), node);
            }
        }
        finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Removes a node.
     *
     * @param node Node to remove.
     * @param replicas Number of replicas for the node.
     */
    void remove(GridNode node, int replicas) {
        String prefix = affSeed + node.id().toString();

        rwLock.writeLock().lock();

        try {
            for (int i = 0; i < replicas; i++) {
                circle.remove(hash(prefix + i));
            }
        }
        finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Gets node for a key.
     *
     * @param key Key.
     * @return Node.
     */
    @Nullable
    GridNode get(Object key) {
        int hash = hash(key);

        rwLock.readLock().lock();

        try {
            if (circle.isEmpty()) {
                U.warn(log, "There are no nodes present in topology.");

                return null;
            }

            SortedMap<Integer, GridNode> tailMap = circle.tailMap(hash);

            // Get first node hash in the circle clock-wise.
            return circle.get(tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey());
        }
        finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Gets node for a given key within given topology.
     *
     * @param key Key to get node for.
     * @param top Topology of nodes.
     * @return Node for key, or {@code null} if node was not found.
     */
    @Nullable
    GridNode get(Object key, Collection<GridNode> top) {
        int hash = hash(key);

        rwLock.readLock().lock();

        try {
            if (circle.isEmpty()) {
                U.warn(log, "There are no nodes present in topology.");

                return null;
            }

            SortedMap<Integer, GridNode> tailMap = circle.tailMap(hash);

            int size = circle.size();

            // Move clock-wise starting from selected position.
            int cnt = 0;

            for (GridNode node : tailMap.values()) {
                if (top.contains(node)) {
                    return node;
                }

                if (cnt++ >= size) {
                    break;
                }
            }

            if (cnt < size) {
                // Wrap around moving clock-wise.
                for (GridNode node : circle.values()) {
                    if (top.contains(node)) {
                        return node;
                    }

                    if (cnt++ >= size) {
                        break;
                    }
                }
            }
        }
        finally {
            rwLock.readLock().unlock();
        }

        return null;
    }

    /**
     * Gets hash code for a given object.
     *
     * @param o Object to get hash code for.
     * @return Hash code.
     */
    private int hash(Object o) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            return bytesToInt(md5.digest(U.intToBytes(o.hashCode())));
        }
        catch (NoSuchAlgorithmException e) {
            if (!isErrLogged) {
                isErrLogged = true;

                U.error(log, "Failed to get an instance of MD5 message digest (will use default hash code)", e);
            }

            return o.hashCode();
        }
    }

    /**
     * Constructs {@code int} from byte array.
     *
     * @param bytes Array of bytes.
     * @return Integer value.
     */
    private int bytesToInt(byte[] bytes) {
        assert bytes != null;

        int bytesCnt = Integer.SIZE >> 3;

        if (bytesCnt > bytes.length) {
            bytesCnt = bytes.length;
        }

        int off = 0;
        int res = 0;

        for (int i = 0; i < bytesCnt; i++) {
            int shift = (bytesCnt - i - 1) << 3;

            res |= (0xffL & bytes[off++]) << shift;
        }

        return res;
    }
}
