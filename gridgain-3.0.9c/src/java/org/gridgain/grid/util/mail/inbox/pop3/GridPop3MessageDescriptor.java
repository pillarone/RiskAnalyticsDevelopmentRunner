// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.inbox.pop3;

import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.util.*;

/**
 * Message descriptor contains short information about message from mail inbox.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridPop3MessageDescriptor implements Serializable {
    /** Message UID. */
    private Object uid;

    /** Message received date. */
    private Date rcvDate;

    /** Whether it was accepted or not. */
    private boolean accepted;

    /**
     * Gets message UID.
     *
     * @return Message UID.
     */
    public Object getUid() {
        return uid;
    }

    /**
     * Sets message UID.
     *
     * @param uid Message UID.
     */
    public void setUid(Object uid) {
        this.uid = uid;
    }

    /**
     * Gets message received date.
     *
     * @return Message received date.
     */
    public Date getReceiveDate() {
        return rcvDate;
    }

    /**
     * Sets message received date.
     *
     * @param rcvDate Message received date.
     */
    public void setReceiveDate(Date rcvDate) {
        this.rcvDate = rcvDate;
    }

    /**
     * Tests whether message accepted. Descriptor uses this flag to mark what messages matched for
     * required rules.
     *
     * @return {@code true} if message accepted, {@code false} otherwise.
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Sets {@code true} if message accepted.
     *
     * @param accepted Flag describing that message was accepted.
     */
    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridPop3MessageDescriptor.class, this);
    }
}
