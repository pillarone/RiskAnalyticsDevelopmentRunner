// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.outbox;

import org.gridgain.grid.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.mail.*;
import javax.activation.*;
import javax.mail.*;
import javax.mail.Message.*;
import javax.mail.internet.*;
import java.io.*;
import java.util.*;

/**
 * This class provides default implementation for {@link GridMailOutboxSession}. Default
 * implementation uses Java Mail API for working with mail servers.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridMailOutboxSessionAdapter implements GridMailOutboxSession {
    /** Mail field "From". */
    private String from;

    /** Mail field "Subject". */
    private String subj;

    /** Collection addresses "TO:". */
    private List<Address> toRcpts;

    /** Collection addresses "CC:". */
    private List<Address> ccRcpts;

    /** Collection addresses "BCC:". */
    private List<Address> bccRcpts;

    /** List of attachments. */
    private List<GridMailOutboxAttachment> attachs;

    /** Additional properties to be used by Java Mail API. */
    private Properties props;

    /** Session authenticator. */
    private Authenticator auth;

    /** Array of message IDs. */
    private String[] msgId;

    /**
     * Creates new mail outbox session with all default values.
     */
    public GridMailOutboxSessionAdapter() {
        // No-op.
    }

    /**
     * Creates new mail outbox session with specified configuration.
     *
     * @param from Value of the mail field "from".
     * @param subj Value of the mail field "subject".
     * @param props Additional properties used by Java Mail API.
     * @param auth Authenticator object used to apply back to the application
     *      when user name and password is needed.
     */
    public GridMailOutboxSessionAdapter(String from, String subj, Properties props, Authenticator auth) {
        assert from != null;
        assert subj != null;
        assert props != null;

        this.from = from;
        this.subj = subj;
        this.props = props;
        this.auth = auth;
    }

    /** {@inheritDoc} */
    @Override public void setFrom(String from) {
        assert from != null;

        this.from = from;
    }

    /** {@inheritDoc} */
    @Override public void setSubject(String subj) {
        assert subj != null;

        this.subj = subj;
    }

    /** {@inheritDoc} */
    @Override public void setAuthenticator(Authenticator auth) {
        assert auth != null;

        this.auth = auth;
    }

    /** {@inheritDoc} */
    @Override public void setProperties(Properties props) {
        assert props != null;

        this.props = props;
    }

    /** {@inheritDoc} */
    @Override public void send() throws GridMailException {
        if (toRcpts == null) {
            throw new GridMailException("Message has to have at least one 'TO' recipient.");
        }

        try {
            Session ses = Session.getInstance(props, auth);

            Message mimeMsg = new MimeMessage(ses);

            mimeMsg.setFrom(new InternetAddress(from));
            mimeMsg.setSubject(subj);
            mimeMsg.setSentDate(new Date());

            mimeMsg.setRecipients(RecipientType.TO, toRcpts.toArray(new Address[toRcpts.size()]));

            if (ccRcpts != null) {
                mimeMsg.setRecipients(RecipientType.CC, ccRcpts.toArray(new Address[ccRcpts.size()]));
            }

            if (bccRcpts != null) {
                mimeMsg.setRecipients(RecipientType.CC, bccRcpts.toArray(new Address[bccRcpts.size()]));
            }

            Multipart body = new MimeMultipart();

            if (attachs != null) {
                for (GridMailOutboxAttachment attach : attachs) {
                    BodyPart part;

                    if (attach.isFileAttachment()) {
                        part = createBodyPart(attach.getFile());
                    }
                    else {
                        part = createBodyPart(attach.getData(), attach.getName());
                    }

                    body.addBodyPart(part, attach.getIndex());
                }
            }

            mimeMsg.setContent(body);

            String protocol = props.getProperty("mail.transport.protocol");

            if (protocol != null && protocol.toLowerCase().equals("smtps")) {
                ses.setProtocolForAddress("rfc822", "smtps");
            }

            Transport.send(mimeMsg);

            msgId = mimeMsg.getHeader("Message-Id");
        }
        catch (MessagingException e) {
            throw new GridMailException("Failed to send message.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public String[] getMessageId() {
        return msgId;
    }

    /** {@inheritDoc} */
    @Override public void addAttachment(Serializable obj, String name, int idx, GridMarshaller marshaller)
        throws GridMailException {
        assert obj != null;
        assert name != null;
        assert idx >= 0;
        assert marshaller != null;

        if (attachs == null) {
            attachs = new ArrayList<GridMailOutboxAttachment>();
        }

        try {
            attachs.add(new GridMailOutboxAttachment(U.marshal(marshaller, obj).getArray(), name, idx));
        }
        catch (GridException e) {
            throw new GridMailException("Failed to add attachment.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public void addAttachment(File file, int idx) {
        assert file != null;
        assert idx >= 0;

        if (attachs == null) {
            attachs = new ArrayList<GridMailOutboxAttachment>();
        }

        attachs.add(new GridMailOutboxAttachment(file, idx));
    }

    /** {@inheritDoc} */
    @Override public void addToRecipient(String to) throws GridMailException {
        assert to != null;

        toRcpts = addRecipient(toRcpts, to);
    }

    /** {@inheritDoc} */
    @Override public void addCcRecipient(String cc) throws GridMailException {
        assert cc != null;

        ccRcpts = addRecipient(ccRcpts, cc);
    }

    /** {@inheritDoc} */
    @Override public void addBccRecipient(String bcc) throws GridMailException {
        assert bcc != null;

        bccRcpts = addRecipient(bccRcpts, bcc);
    }

    /**
     * Adds address to collection of recipients.
     *
     * @param addrs Collection of addresses.
     * @param addr Address to add in collection.
     * @return Result collection of recipients.
     * @throws GridMailException Thrown in case of invalid address.
     */
    private List<Address> addRecipient(List<Address> addrs, String addr) throws GridMailException {
        assert addr != null;

        if (addrs == null) {
            addrs = new ArrayList<Address>();
        }

        try {
            addrs.add(new InternetAddress(addr));
        }
        catch (AddressException e) {
            throw new GridMailException("Invalid email address: " + addr, e);
        }

        return addrs;
    }

    /**
     * Creates mail attachment with byte array (serialized object).
     *
     * @param data Data to put in attachment.
     * @param name Attachment name.
     * @return New body part object.
     * @throws MessagingException Thrown if Java Mail API error occurs.
     */
    private BodyPart createBodyPart(byte[] data, String name) throws MessagingException {
        assert data != null;
        assert name != null;

        BodyPart part = new MimeBodyPart();

        part.setDataHandler(new DataHandler(new GridMailOutboxDataSource("grid.email.datasource", data)));
        part.setFileName(name);

        return part;
    }

    /**
     * Creates attachment with file content.
     *
     * @param file File to put in attachment.
     * @return New body part object where file content placed.
     * @throws MessagingException Thrown if Java Mail API error occurs.
     */
    public BodyPart createBodyPart(File file) throws MessagingException {
        assert file != null;

        BodyPart part = new MimeBodyPart();

        // Add attachment.
        part.setDataHandler(new DataHandler(new FileDataSource(file)));
        part.setFileName(file.getName());

        return part;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMailOutboxSessionAdapter.class, this);
    }
}
