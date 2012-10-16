// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.swapspace;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Swap space SPI.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridSwapSpaceSpi extends GridSpi, GridSpiJsonConfigurable {
    /**
     * Entirely clears data space with given name, if any.
     *
     * @param space Space name to clear
     * @throws GridSpiException In case of any errors.
     */
    public void clear(String space) throws GridSpiException;

    /**
     * Gets size in bytes for data space with given name. If specified space does
     * not exist this method returns {@code 0}.
     *
     * @param space Space name to get size for.
     * @return Size in bytes.
     * @throws GridSpiException In case of any errors.
     */
    public long size(String space) throws GridSpiException;

    /**
     * Gets number of stored entries (keys) in data space with given name. If specified
     * space does not exist this method returns {@code 0}.
     *
     * @param space Space name to get number of entries for.
     * @return Number of stored entries in specified space.
     * @throws GridSpiException In case of any errors.
     */
    public int count(String space) throws GridSpiException;

    /**
     * Gets storage total size.
     *
     * @return Total size.
     */
    public long totalSize();

    /**
     * Gets total number of entries (keys) in the swap storage.
     *
     * @return Total number of entries in the swap storage.
     */
    public long totalCount();

    /**
     * Reads stored value as array of bytes by key from data space with given name.
     * If specified space does not exist this method returns {@code null}.
     *
     * @param space Name of the data space to read from.
     * @param key Key used to read value from data space.
     * @return Value as array of bytes stored in specified data space that matches
     *      to given key.
     * @throws GridSpiException In case of any errors.
     */
    @Nullable public GridSwapByteArray read(String space, GridSwapByteArray key) throws GridSpiException;

    /**
     * Reads stored values as array of bytes by all passed keys from data space with
     * given name. If specified space does not exist this method returns empty map.
     *
     * @param space Name of the data space to read from.
     * @param keys Keys used to read values from data space.
     * @return Map in which keys are the ones passed into method and values are
     *      corresponding values read from swap storage.   
     * @throws GridSpiException In case of any errors.
     */
    public Map<GridSwapByteArray, GridSwapByteArray> readAll(String space, Iterable<GridSwapByteArray> keys)
        throws GridSpiException;

    /**
     * Removes value stored in data space with given name corresponding to specified key.
     *
     * @param space Space name to remove value from.
     * @param key Key to remove value in the specified space for.
     * @param c Optional closure that takes removed value and executes after actual
     *      removing. If there was no value in storage the closure is not executed..
     * @return {@code true} if value was actually removed, {@code false} otherwise.
     * @throws GridSpiException In case of any errors.
     */
    public boolean remove(String space, GridSwapByteArray key, @Nullable GridInClosure<GridSwapByteArray> c)
        throws GridSpiException;

    /**
     * Removes values stored in data space with given name corresponding to specified keys.
     *
     * @param space Space name to remove values from.
     * @param keys Keys to remove value in the specified space for.
     * @param c Optional closure that takes removed value and executes after actual
     *      removing.
     * @throws GridSpiException In case of any errors.
     */
    public void removeAll(String space, Collection<GridSwapByteArray> keys,
        @Nullable GridInClosure2<GridSwapByteArray, GridSwapByteArray> c) throws GridSpiException;

    /**
     * Stores value as array of bytes with given key into data space with given name.
     *
     * @param space Space name to store key-value pair into.
     * @param key Key to store given value for. This key can be used further to
     *      read or remove stored value.
     * @param val Some value as array of bytes to store into specified data space.
     * @throws GridSpiException In case of any errors.
     */
    public void store(String space, GridSwapByteArray key, @Nullable GridSwapByteArray val) throws GridSpiException;

    /**
     * Stores key-value pairs (both keys and values are arrays of bytes) into data
     * space with given name.
     *
     * @param space Space name to store key-value pairs into.
     * @param pairs Map of stored key-value pairs where each one is an array of bytes.
     * @throws GridSpiException In case of any errors.
     */
    public void storeAll(String space, Map<GridSwapByteArray, GridSwapByteArray> pairs) throws GridSpiException;
}
