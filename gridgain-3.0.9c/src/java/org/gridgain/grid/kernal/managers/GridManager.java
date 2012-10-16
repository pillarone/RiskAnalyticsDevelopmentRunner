// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers;

import org.gridgain.grid.*;
import org.gridgain.grid.util.tostring.*;
import java.util.*;

/**
 * This interface defines life-cycle for kernal manager. Managers provide layer of indirection
 * between kernal and SPI modules. Kernel never calls SPI modules directly but
 * rather calls manager that further delegate the apply to specific SPI module.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
public interface GridManager {
    /**
     * Adds attributes from underlying SPI to map of all attributes.
     *
     * @param attrs Map of all attributes gotten from SPI's so far.
     * @throws GridException Wrapper for exception thrown by underlying SPI.
     */
    public void addSpiAttributes(Map<String, Object> attrs) throws GridException;

    /**
     * Starts grid manager.
     *
     * @throws GridException Throws in case of any errors.
     */
    public void start() throws GridException;

    /**
     * Stops grid managers.
     *
     * @throws GridException Thrown in case of any errors.
     */
    public void stop() throws GridException;

    /**
     * Callback that notifies that kernal has successfully started,
     * including all managers and processors.
     *
     * @throws GridException If manager of SPI could not be initialized.
     */
    public void onKernalStart() throws GridException;

    /**
     * Callback to notify that kernal is about to stop.
     */
    public void onKernalStop();
}
