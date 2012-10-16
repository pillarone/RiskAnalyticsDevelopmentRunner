// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.swapspace.file;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;

/**
 * Management bean that provides general administrative and configuration information
 * about file-based swapspace SPI.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to file-based swap space SPI configuration.")
public interface GridFileSwapSpaceSpiMBean extends GridSpiManagementMBean {
    /**
     * Data will not be removed from disk is this property is {@code true}.
     *
     * @return {@code true} if data must not be deleted at SPI stop, otherwise {@code false}.
     */
    @GridMBeanDescription("Whether or not to remove data from disk.")
    public boolean isPersistent();

    /**
     * If this property is {@code true} then spi will check for corrupted files at SPI
     * start and delete them.
     *
     * @return {@code true} if corrupted files should be deleted at SPI start,
     *      {@code false} otherwise.
     */
    @GridMBeanDescription("Whether or not to delete corrupted files from disk at SPI start.")
    public boolean isDeleteCorrupted();

    /**
     * Gets maximum size in bytes for data to store on disk.
     *
     * @return Maximum size in bytes for data to store on disk.
     */
    @GridMBeanDescription("Gets maximum size in bytes for data to store on disk.")
    public long getMaximumSwapSize();

    /**
     * Gets maximum count (number of entries) for all swap spaces.
     *
     * @return Maximum count for all swap spaces.
     */
    @GridMBeanDescription("Gets maximum count for all swap spaces.")
    public int getMaximumSwapCount();

    /**
     * Gets path to the directory where all swap space values are saved.
     *
     * @return Path to the swap space directory.
     */
    @GridMBeanDescription("Gets path to the directory where all swap space values are saved.")
    public String getDirectoryPath();
}
