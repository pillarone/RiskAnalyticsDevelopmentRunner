// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.inbox;

import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.mail.*;
import javax.mail.*;
import java.io.*;
import java.util.*;

/**
 * This class provides default implementation for {@link GridMailInboxMessage}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridMailInboxMessageAdapter implements GridMailInboxMessage {
    /** Mail message. */
    private final Message msg;

    /** Message UID. */
    private final String uid;

    /** Grid logger. */
    private final GridLogger log;

    /** Message's received date. */
    private Date rcvDate;

    /**
     * Creates message with given arguments.
     *
     * @param msg Mail message.
     * @param uid Message UID.
     * @param log Logger to log.
     */
    public GridMailInboxMessageAdapter(Message msg, String uid, GridLogger log) {
        assert msg != null;
        assert uid != null;
        assert log != null;

        this.msg = msg;
        this.uid = uid;
        this.log = log.getLogger(getClass());
    }

    /**
     * Creates message with given arguments.
     *
     * @param msg Mail message.
     * @param uid Message UID.
     * @param rcvDate Received date.
     * @param log Logger to log.
     */
    public GridMailInboxMessageAdapter(Message msg, String uid, Date rcvDate, GridLogger log) {
        assert msg != null;
        assert uid != null;
        assert rcvDate != null;
        assert log != null;

        this.msg = msg;
        this.uid = uid;
        this.rcvDate = rcvDate;
        this.log = log.getLogger(getClass());
    }

    /** {@inheritDoc} */
    @Override public String getUid() throws GridMailException {
        return uid;
    }

    /** {@inheritDoc} */
    @Override public String getSubject() throws GridMailException {
        try {
            return msg.getSubject();
        }
        catch (MessagingException e) {
            throw new GridMailException("Error when getting subject.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public String[] getHeader(String name) throws GridMailException {
        assert name != null;

        try {
            return msg.getHeader(name);
        }
        catch (MessagingException e) {
            throw new GridMailException("Error when getting message header: " + name, e);
        }
    }

    /** {@inheritDoc} */
    @Override public Date getReceivedDate() throws GridMailException {
        try {
            if (rcvDate != null) {
                return rcvDate;
            }

            if (msg.getReceivedDate() != null) {
                return msg.getReceivedDate();
            }

            return new Date();
        }
        catch (MessagingException e) {
            throw new GridMailException("Error when getting message received date." , e);
        }
    }

    /** {@inheritDoc} */
    @Override public int getAttachmentCount() throws GridMailException {
        int n = 0;

        try {
            if (msg.getContent() instanceof Multipart) {
                n = ((Multipart)msg.getContent()).getCount();
            }
        }
        catch (IOException e) {
            throw new GridMailException("Error when getting attachments count .", e);
        }
        catch (MessagingException e) {
            throw new GridMailException("Error when getting attachments count .", e);
        }

        return n;
    }

    /** {@inheritDoc} */
    @Override public GridMailInboxAttachment getAttachment(int idx) throws GridMailException {
        try {
            if (msg.getContent() instanceof Multipart) {
                Multipart body = (Multipart)msg.getContent();

                if (body.getCount() <= idx) {
                    return null;
                }

                return new GridMailInboxAttachmentAdapter(body.getBodyPart(idx), log);
            }

            throw new GridMailException("Error when getting attachment: " + idx);
        }
        catch (IOException e) {
            throw new GridMailException("Error when getting attachment: " + idx, e);
        }
        catch (MessagingException e) {
            throw new GridMailException("Error when getting attachment: " + idx, e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMailInboxMessageAdapter.class, this);
    }
}
