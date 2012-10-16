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
 * Represents mail inbox message.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridMailInboxMessage {
    /**
     * Gets message UID.
     *
     * @return Message UID.
     * @throws GridMailException Thrown in case of any errors.
     */
    public String getUid() throws GridMailException;

    /**
     * Gets message subject.
     *
     * @return Message subject.
     * @throws GridMailException Thrown in case of any errors.
     */
    public String getSubject() throws GridMailException;

    /**
     * Gets message header.
     *
     * @param name Header name.
     * @return Header value.
     * @throws GridMailException Thrown in case of any errors.
     */
    public String[] getHeader(String name) throws GridMailException;

    /**
     * Gets message received date.
     *
     * @return Message received date.
     * @throws GridMailException Thrown in case of any errors.
     */
    public Date getReceivedDate() throws GridMailException;

    /**
     * Gets mail inbox attachment.
     *
     * @param idx Index of requested attachment.
     * @return Mail inbox attachment.
     * @throws GridMailException Thrown in case of any errors.
     */
    public GridMailInboxAttachment getAttachment(int idx) throws GridMailException;

    /**
     * Gets attachments count.
     *
     * @return Attachments count.
     * @throws GridMailException Thrown in case of any errors.
     */
    public int getAttachmentCount() throws GridMailException;
}
