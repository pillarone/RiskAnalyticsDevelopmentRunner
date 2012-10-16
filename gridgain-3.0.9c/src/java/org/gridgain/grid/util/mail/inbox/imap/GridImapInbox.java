// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.inbox.imap;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.mail.*;
import org.gridgain.grid.util.mail.inbox.*;
import javax.mail.*;
import javax.mail.search.*;
import java.util.*;
import java.util.Map.*;

/**
 * This class provides IMAP implementation for {@link GridMailInbox}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridImapInbox implements GridMailInbox {
    /** Mail inbox configuration. */
    private final GridMailInboxConfiguration cfg;

    /** Mail inbox matcher. */
    private final GridMailInboxMatcher matcher;

    /** Message store. */
    private Store store;

    /** Message folder. */
    private Folder folder;

    /** Session authenticator. */
    private Authenticator auth;

    /** Connection properties. */
    private Properties props = new Properties();

    /** Last message UID. */
    private Long lastMsgUid;

    /** Value between sessions must be the same. Shows that any cached UIDs are not stale. */
    private Long uidValidity;

    /** Whether or not inbox is opened. */
    private boolean isOpened;

    /**
     * Creates a mail inbox with specified configuration and message filter.
     * All messages from mail inbox will be filtered with rules defined in
     * {@code matcher} argument.
     *
     * @param cfg Mail inbox configuration.
     * @param matcher Message filter.
     */
    public GridImapInbox(GridMailInboxConfiguration cfg, GridMailInboxMatcher matcher) {
        assert cfg.getProtocol() != null;
        assert cfg.getConnectionType() != null;
        assert cfg.getHost() != null;
        assert cfg.getPort() > 0;
        assert matcher != null;
        assert cfg.getProtocol() == GridMailInboxProtocol.IMAP || cfg.getProtocol() == GridMailInboxProtocol.IMAPS;

        this.cfg = cfg;
        this.matcher = matcher;

        prepareParameters();
    }

    /** {@inheritDoc} */
    @Override public void open(boolean readOnly) throws GridMailException {
        if (isOpened) {
            throw new GridMailException("IMAP mailbox has already been opened: " + U.hidePassword(cfg.uri()));
        }

        // Mail properties must be initialized.
        assert props != null;

        try {
            store = Session.getInstance(props, auth).getStore();

            store.connect();

            folder = store.getFolder(cfg.getFolderName());

            folder.open(readOnly == true ? Folder.READ_ONLY : Folder.READ_WRITE);
        }
        catch (MessagingException e) {
            throw new GridMailException("Failed to open IMAP mailbox: " + U.hidePassword(cfg.uri()), e);
        }

        // Calculate last message uid.
        initializeLastMessageUid();

        isOpened = true;
    }

    /** {@inheritDoc} */
    @Override public void close(boolean purge) throws GridMailException {
        Exception e = null;

        try {
            if (folder != null) {
                folder.close(purge);
            }
        }
        catch (MessagingException e1) {
            e = e1;
        }

        try {
            if (store != null) {
                store.close();
            }
        }
        catch (MessagingException e1) {
            // Don't loose the initial exception.
            if (e == null) {
                e = e1;
            }
        }

        folder = null;
        store = null;

        isOpened = false;

        if (e != null) {
            throw new GridMailException("Failed to close IMAP mailbox: " + U.hidePassword(cfg.uri()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public List<GridMailInboxMessage> readNew() throws GridMailException {
        if (isOpened == false) {
            throw new GridMailException("IMAP mailbox is not opened: " + U.hidePassword(cfg.uri()));
        }

        List<GridMailInboxMessage> msgList = null;

        try {
            UIDFolder uidFolder = (UIDFolder)folder;

            Message[] msgs = uidFolder.getMessagesByUID(lastMsgUid + 1, UIDFolder.LASTUID);

            if (msgs != null && msgs.length > 0) {
                // Method getMessagesByUID() always return last message for range where lastUid not present.
                if (msgs.length == 1 && lastMsgUid == uidFolder.getUID(msgs[0])) {
                    return null;
                }

                // Get last message UID.
                long uid = uidFolder.getUID(msgs[msgs.length - 1]);

                List<SearchTerm> terms = makeSearchTerms(matcher);

                terms.add(new FlagTerm(new Flags(Flags.Flag.DELETED), false));

                // Construct search term.
                SearchTerm term = new AndTerm(terms.toArray(new SearchTerm[terms.size()]));

                msgs = folder.search(term, msgs);

                // Set last message UID to new position.
                lastMsgUid = uid;

                if (msgs != null && msgs.length > 0) {
                    msgList = new ArrayList<GridMailInboxMessage>(msgs.length);

                    for (Message msg : msgs) {
                        if (msg.isExpunged()) {
                            // Ignore expunged messages.
                            continue;
                        }

                        long msgUid = uidFolder.getUID(msg);

                        assert msgUid > 0;

                        msgList.add(new GridMailInboxMessageAdapter(msg, String.valueOf(msgUid), cfg.getLogger()));
                    }
                }
            }
        }
        catch (MessagingException e) {
            throw new GridMailException("Failed to get new IMAP messages from mailbox: " +
                U.hidePassword(cfg.uri()), e);
        }

        return msgList;
    }

    /** {@inheritDoc} */
    @Override public List<GridMailInboxMessage> readAll() throws GridMailException {
        if (isOpened == false) {
            throw new GridMailException("IMAP mailbox is not opened: " + U.hidePassword(cfg.uri()));
        }

        List<GridMailInboxMessage> msgsList = null;

        try {
            UIDFolder uidFolder = (UIDFolder)folder;

            Message[] msgs = uidFolder.getMessagesByUID(1, UIDFolder.LASTUID);

            if (msgs != null && msgs.length > 0) {
                // Get last message UID.
                long uid = uidFolder.getUID(msgs[msgs.length - 1]);

                List<SearchTerm> terms = makeSearchTerms(matcher);

                terms.add(new FlagTerm(new Flags(Flags.Flag.DELETED), false));

                // Construct search term.
                SearchTerm term = new AndTerm(terms.toArray(new SearchTerm[terms.size()]));

                msgs = folder.search(term, msgs);

                // Set last message UID to new position.
                lastMsgUid = uid;

                if (msgs != null && msgs.length > 0) {
                    msgsList = new ArrayList<GridMailInboxMessage>(msgs.length);

                    for (Message msg : msgs) {
                        if (msg.isExpunged()) {
                            // Ignore expunged messages.
                            continue;
                        }

                        long msgUid = uidFolder.getUID(msg);

                        assert msgUid > 0;

                        msgsList.add(new GridMailInboxMessageAdapter(msg, String.valueOf(msgUid), cfg.getLogger()));
                    }
                }
            }
        }
        catch (MessagingException e) {
            throw new GridMailException("Failed to get all IMAP messages from mailbox: " +
                U.hidePassword(cfg.uri()), e);
        }

        return msgsList;
    }

    /** {@inheritDoc} */
    @Override public int removeOld(Date date) throws GridMailException {
        assert date != null;

        if (isOpened == false) {
            throw new GridMailException("IMAP mailbox is not opened: " + U.hidePassword(cfg.uri()));
        }

        if (folder.isOpen() == true && folder.getMode() != Folder.READ_WRITE) {
            throw new GridMailException("IMAP mailbox is opened in readonly mode: " + U.hidePassword(cfg.uri()));
        }

        try {
            List<SearchTerm> termList = makeSearchTerms(matcher);

            termList.add(new ReceivedDateTerm(ComparisonTerm.LT, date));
            termList.add(new FlagTerm(new Flags(Flags.Flag.DELETED), false));

            // Construct search term.
            SearchTerm term = new AndTerm(termList.toArray(new SearchTerm[termList.size()]));

            Message[] foundMsgs = folder.search(term);

            int n = 0;

            if (foundMsgs != null && foundMsgs.length > 0) {
                for (Message msg : foundMsgs) {
                    msg.setFlag(Flags.Flag.DELETED, true);
                }

                n = foundMsgs.length;
            }

            return n;
        }
        catch (MessagingException e) {
            throw new GridMailException("Failed to remove old POP3 messages from mailbox: " +
                U.hidePassword(cfg.uri()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public void flush() throws GridMailException {
        // No-op.
    }

    /**
     * Prepares Java Mail properties.
     */
    private void prepareParameters() {
        String protoName = cfg.getProtocol().toString().toLowerCase();

        // Session properties.
        props.setProperty("mail.store.protocol", protoName);

        String mailProto = "mail." + protoName;

        props.setProperty(mailProto + ".host", cfg.getHost());
        props.setProperty(mailProto + ".port", Integer.toString(cfg.getPort()));

        if (cfg.getConnectionType() == GridMailConnectionType.STARTTLS) {
            props.setProperty(mailProto + ".starttls.enable", "true");
        }
        else if (cfg.getConnectionType() == GridMailConnectionType.SSL) {
            props.setProperty(mailProto + ".ssl", "true");
        }

        // Add property for authentication by username.
        if (cfg.getUsername() != null) {
            props.setProperty(mailProto + ".auth", "true");

            auth = new Authenticator() {
                @Override public PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(cfg.getUsername(), cfg.getPassword());
                }
            };
        }

        if (cfg.getCustomProperties() != null) {
            props.putAll(cfg.getCustomProperties());
        }
    }

    /**
     * Creates list of search rules for IMAP protocol.
     *
     * @param matcher Message filter.
     * @return List of search rules.
     */
    private List<SearchTerm> makeSearchTerms(GridMailInboxMatcher matcher) {
        assert matcher != null;

        List<SearchTerm> terms = new ArrayList<SearchTerm>();

        if (matcher.getSubject() != null) {
            // Note: don't use SubjectTerm. It doesn't work in JavaMail 1.4
            terms.add(new HeaderTerm("Subject", matcher.getSubject()));
        }

        if (matcher.getHeaders() != null) {
            for (Entry<String, String> entry : matcher.getHeaders().entrySet()) {
                if (entry.getValue() != null) {
                    terms.add(new HeaderTerm(entry.getKey(), entry.getValue()));
                }
            }
        }

        return terms;
    }

    /**
     * Prepares parameter used as last message resolver.
     *
     * @throws GridMailException Thrown in case of any error.
     */
    private void initializeLastMessageUid() throws GridMailException {
        try {
            UIDFolder uidFolder = (UIDFolder)folder;

            if (uidValidity == null || uidValidity != uidFolder.getUIDValidity()) {
                uidValidity = uidFolder.getUIDValidity();

                lastMsgUid = getLastMessageUid();
            }

            assert uidValidity != null;
            assert lastMsgUid != null;
        }
        catch (MessagingException e) {
            // Reset values if some errors occurred.
            uidValidity = null;
            lastMsgUid = null;

            throw new GridMailException("Failed to initialize last IMAP message UID for mailbox: " +
                U.hidePassword(cfg.uri()), e);
        }
    }

    /**
     * Calculates last message UID.
     *
     * @return Last message UID.
     * @throws MessagingException Thrown in case of any error.
     */
    private long getLastMessageUid() throws MessagingException {
        int msgCnt = folder.getMessageCount();

        return msgCnt == 0 ? 0 : ((UIDFolder)folder).getUID(folder.getMessage(msgCnt));
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridImapInbox.class, this);
    }
}
