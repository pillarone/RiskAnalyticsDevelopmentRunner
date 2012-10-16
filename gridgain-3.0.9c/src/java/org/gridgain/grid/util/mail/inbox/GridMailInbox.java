// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.inbox;

import org.gridgain.grid.util.mail.*;
import java.util.*;

/**
 * This interface defines methods for working with mail inbox.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridMailInbox {
    /**
     * Connects and opens mail inbox.
     *
     * @param readOnly Open mode flag. If {@code true} then mail inbox opens
     *      in read-only mode, otherwise read-write mode.
     * @throws GridMailException Thrown if any error occurs.
     */
    public void open(boolean readOnly) throws GridMailException;

    /**
     * Closes mail inbox and terminates connection.
     *
     * @param purge Expunges all deleted messages if this flag is {@code true}.
     * @throws GridMailException Thrown if any error occurs.
     */
    public void close(boolean purge) throws GridMailException;

    /**
     * Returns list of new mail messages since the last read. This method should be used
     * after calling {@link #readAll()} method.
     *
     * @return List of new messages since the last read.
     * @throws GridMailException Thrown if any error occurs.
     */
    public List<GridMailInboxMessage> readNew() throws GridMailException;

    /**
     * Returns list of all mail messages in mail inbox.
     *
     * @return List of all messages in mail inbox.
     * @throws GridMailException Thrown if an error occurs.
     */
    public List<GridMailInboxMessage> readAll() throws GridMailException;

    /**
     * Deletes messages in mail inbox with received date before argument date.
     * Note that some mail providers don't support mail's received date.
     *
     * @param date All messages received before this date will be deleted.
     * @return Number of messages marked as deleted.
     * @throws GridMailException Thrown if an error occurs.
     */
    public int removeOld(Date date) throws GridMailException;

    /**
     * Flushes working data.
     *
     * @throws GridMailException Thrown if an error occurs.
     */
    public void flush() throws GridMailException;
}
