// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.inbox.pop3;

import com.sun.mail.pop3.*;
import org.gridgain.grid.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.mail.*;
import org.gridgain.grid.util.mail.inbox.*;
import javax.mail.*;
import javax.mail.Flags.*;
import java.io.*;
import java.util.*;

/**
 * This class provides POP3 implementation for {@link GridMailInbox}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridPop3Inbox implements GridMailInbox {
    /** Mail inbox configuration. */
    private final GridMailInboxConfiguration cfg;

    /** Message filter. */
    private final GridMailInboxMatcher matcher;

    /** Whether or not inbox is opened. */
    private boolean isOpened;

    /** Inbox store. */
    private Store store;

    /** Inbox folder. */
    private Folder folder;

    /** Inbox fetch profile. */
    private final FetchProfile fetchProf = new FetchProfile();

    /** Message descriptors. */
    private Map<String, GridPop3MessageDescriptor> descs = new HashMap<String, GridPop3MessageDescriptor>();

    /** Whether or not messages are loaded. */
    private boolean isLoaded;

    /** Session authenticator. */
    private Authenticator auth;

    /** Connection properties. */
    private Properties props = new Properties();

    /** Marshaller to marshal and unmarshal messages to the local database. */
    private GridMarshaller marshaller;

    /**
     * Creates mail inbox with specified configuration and message filter.
     * All messages from mail inbox will be filtered with rules defined in
     * {@code matcher} argument.
     *
     * @param cfg Mail inbox configuration.
     * @param matcher Message filter.
     * @param marshaller Marshaller to marshal and unmarshal objects.
     */
    public GridPop3Inbox(GridMailInboxConfiguration cfg, GridMailInboxMatcher matcher, GridMarshaller marshaller) {
        assert cfg != null;
        assert matcher != null;
        assert cfg.getProtocol() == GridMailInboxProtocol.POP3 || cfg.getProtocol() == GridMailInboxProtocol.POP3S;
        assert marshaller != null;

        this.cfg = cfg;
        this.matcher = matcher;
        this.marshaller = marshaller;

        // Prepare fetchProfile.
        fetchProf.add(UIDFolder.FetchProfileItem.UID);

        if (matcher.getSubject() != null) {
            fetchProf.add("Subject");
        }

        if (matcher.getHeaders() != null) {
            for (String header : matcher.getHeaders().keySet()) {
                fetchProf.add(header);
            }
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    public void open(boolean readOnly) throws GridMailException {
        boolean wasLoaded = isLoaded;

        if (isLoaded == false) {
            assert descs.isEmpty() == true;

            // Load local messages database from disk.
            File file = new File(cfg.getStoreFileName());

            if (file.exists()) {
                InputStream in = null;

                try {
                    in = new FileInputStream(file);

                    if (in.available() > 0) {
                        descs.putAll((Map<? extends String, ? extends GridPop3MessageDescriptor>)
                            U.unmarshal(marshaller, in, getClass().getClassLoader()));
                    }
                }
                catch (IOException e) {
                    throw new GridMailException("Failed to load local POP3 storage file: " + file.getAbsolutePath(), e);
                }
                catch (GridException e) {
                    throw new GridMailException("Failed to load local POP3 storage file: " + file.getAbsolutePath(), e);
                }
                finally {
                    U.close(in, cfg.getLogger());
                }
            }

            initializeStoreParameters();

            isLoaded = true;
        }

        if (isOpened == false) {
            try {
                store = Session.getInstance(props, auth).getStore(cfg.getProtocol().toString().toLowerCase());

                store.connect();

                folder = store.getFolder(cfg.getFolderName());

                folder.open(readOnly == true ? Folder.READ_ONLY : Folder.READ_WRITE);

                isOpened = true;
            }
            catch (MessagingException e) {
                throw new GridMailException("Failed to open POP3 mailbox: " + U.hidePassword(cfg.uri().toString()), e);
            }
        }

        if (wasLoaded == false) {
            // Read old messages.
            readNew();
        }
    }

    /**
     * Prepares Java Mail properties.
     */
    private void initializeStoreParameters() {
        if (cfg.getUsername() != null) {
            auth = new Authenticator() {
                @Override public PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(cfg.getUsername(), cfg.getPassword());
                }
            };
        }

        String protoName = cfg.getProtocol().toString().toLowerCase();

        // Session properties.
        props.setProperty("mail.store.protocol", protoName);

        String mailProto = "mail." + protoName;

        props.setProperty(mailProto + ".host", cfg.getHost());
        props.setProperty(mailProto + ".port", Integer.toString(cfg.getPort()));

        switch (cfg.getConnectionType()) {
            case SSL: { props.setProperty(mailProto + ".ssl", "true"); break; }
            case STARTTLS: { props.setProperty(mailProto + ".starttls.enable", "true"); break; }
            case NONE: { break; } // No-op. Use defaults.

            default: {
                // Unknown connection type.
                assert false;
            }
        }

        // Add property for authentication by username.
        if (cfg.getUsername() != null) {
            props.setProperty(mailProto + ".auth", "true");
        }

        if (cfg.getCustomProperties() != null) {
            props.putAll(cfg.getCustomProperties());
        }
    }

    /** {@inheritDoc} */
    @Override public void flush() throws GridMailException {
        OutputStream out = null;

        try {
            out = new FileOutputStream(cfg.getStoreFileName());

            U.marshal(marshaller, descs, out);
        }
        catch (IOException e) {
            throw new GridMailException("Failed to flush messages to local POP3 storage.", e);
        }
        catch (GridException e) {
            throw new GridMailException("Failed to flush messages to local POP3 storage.", e);
        }
        finally {
            U.close(out, cfg.getLogger());
        }
    }

    /** {@inheritDoc} */
    @Override public void close(boolean purge) throws GridMailException {
        if (isOpened == false) {
            // No-op.
            return;
        }

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
            throw new GridMailException("Failed to close POP3 mailbox: " + U.hidePassword(cfg.uri()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public List<GridMailInboxMessage> readNew() throws GridMailException {
        if (isOpened == false) {
            throw new GridMailException("POP3 mailbox is not opened: " + U.hidePassword(cfg.uri()));
        }

        try {
            Message[] folderMsgs = folder.getMessages();

            List<GridMailInboxMessage> retval = new LinkedList<GridMailInboxMessage>();

            if (folderMsgs != null && folderMsgs.length > 0) {
                int currMsgIdx = folderMsgs.length;

                boolean stop = false;

                while (stop == false && currMsgIdx > 0) {
                    int fetchCount = currMsgIdx >= cfg.getReadBatchSize() ? cfg.getReadBatchSize() : currMsgIdx;

                    currMsgIdx -= fetchCount;

                    Message[] fetchedMsgs = new Message[fetchCount];

                    System.arraycopy(folderMsgs, currMsgIdx, fetchedMsgs, 0, fetchCount);

                    folder.fetch(fetchedMsgs, fetchProf);

                    for (int i = fetchedMsgs.length - 1; i >= 0; i--) {
                        Message fetchedMsg = fetchedMsgs[i];

                        // Message can be expunged if another client expunged it while being processed here.
                        if (fetchedMsg.isExpunged()) {
                            continue;
                        }

                        String uid = getUid(fetchedMsg);

                        // UID can be null if message was deleted by another client while processed here.
                        if (uid == null) {
                            continue;
                        }

                        if (descs.containsKey(uid)) {
                            // Stop scanning folder.
                            stop = true;

                            break;
                        }

                        // Prepare message descriptor.
                        GridPop3MessageDescriptor md = new GridPop3MessageDescriptor();

                        Date rcvDate = new Date();

                        md.setUid(uid);
                        md.setReceiveDate(rcvDate);

                        GridMailInboxMessageAdapter msg = new GridMailInboxMessageAdapter(fetchedMsg, uid, rcvDate,
                            cfg.getLogger());

                        if (isMatch(msg)) {
                            md.setAccepted(true);

                            retval.add(msg);
                        }

                        descs.put(uid, md);
                    }
                }
            }

            return retval;
        }
        catch (MessagingException e) {
            throw new GridMailException("Failed to get new POP3 messages from mailbox: " +
                U.hidePassword(cfg.uri()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public List<GridMailInboxMessage> readAll() throws GridMailException {
        if (isOpened == false) {
            throw new GridMailException("POP3 mailbox is not opened.");
        }

        try {
            Message[] folderMsgs = folder.getMessages();

            List<GridMailInboxMessage> retval = new LinkedList<GridMailInboxMessage>();

            Set<String> retainUids = new HashSet<String>(descs.size());

            if (folderMsgs != null && folderMsgs.length > 0) {
                folder.fetch(folderMsgs, fetchProf);

                for (Message folderMsg : folderMsgs) {
                    // Message can be expunged if another client expunged it while being processed here.
                    if (folderMsg.isExpunged()) {
                        continue;
                    }

                    String uid = getUid(folderMsg);

                    // UID can be null if message was deleted by another client while processed here.
                    if (uid == null) {
                        continue;
                    }

                    retainUids.add(uid);

                    boolean isAccepted = false;

                    Date rcvDate = getReceivedDate(uid);

                    assert rcvDate != null;

                    GridMailInboxMessageAdapter msg = new GridMailInboxMessageAdapter(folderMsg, uid, rcvDate,
                        cfg.getLogger());

                    if (isMatch(msg)) {
                        isAccepted = true;

                        retval.add(msg);
                    }

                    if (descs.containsKey(uid) == false) {
                        GridPop3MessageDescriptor md = new GridPop3MessageDescriptor();

                        md.setUid(uid);
                        md.setReceiveDate(rcvDate);
                        md.setAccepted(isAccepted);

                        descs.put(uid, md);
                    }
                }
            }

            // Remove messages in local database.
            descs.keySet().retainAll(retainUids);

            return retval;
        }
        catch (MessagingException e) {
            throw new GridMailException("Failed to get all POP3 messages from mailbox: " +
                U.hidePassword(cfg.uri()), e);
        }
    }

    /**
     * Tests whether the message properties match with filtering rules.
     *
     * @param msg Message to test.
     * @return {@code true} if message matches, {@code false} otherwise.
     * @throws GridMailException Thrown in case of any error.
     */
    private boolean isMatch(GridMailInboxMessage msg) throws GridMailException {
        // If subjects don't match, fail right away.
        if (matcher.getSubject() != null && matcher.getSubject().equals(msg.getSubject()) == false) {
            return false;
        }

        Map<String, String> hdrs = matcher.getHeaders();

        if (hdrs == null) {
            return true;
        }

        // Compare all headers in the matcher to the headers in the message.
        for (Map.Entry<String, String> hdrEntry : hdrs.entrySet()) {
            String matcherHdrVal = hdrEntry.getValue();

            // Should not match on null.
            assert matcherHdrVal != null;

            // Get all headers from message for a given header name.
            String[] msgHdrVals = msg.getHeader(hdrEntry.getKey());

            // There are no headers with given name.
            if (msgHdrVals == null) {
                return false;
            }

            // Fail if none of the header values for given name matched.
            if (U.containsStringArray(msgHdrVals, matcherHdrVal, true) == false) {
                return false;
            }
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public int removeOld(Date date) throws GridMailException {
        assert date != null;

        if (isOpened == false) {
            throw new GridMailException("POP3 mailbox is not opened.");
        }

        try {
            List<Message> folderMsgs = Arrays.asList(folder.getMessages());

            if (folderMsgs == null) {
                return 0;
            }

            // Use a suitable FetchProfile.
            FetchProfile oldFetchProfile = new FetchProfile();

            oldFetchProfile.add(FetchProfile.Item.FLAGS);
            oldFetchProfile.add(UIDFolder.FetchProfileItem.UID);

            Set<String> retainUids = new HashSet<String>(descs.size());

            int removeCnt = 0;
            int foundCnt = 0;
            int currIdx = 0;

            boolean stop = false;

            while (stop == false && currIdx < folderMsgs.size()) {
                int fetchCnt = currIdx + cfg.getReadBatchSize() < folderMsgs.size() ? cfg.getReadBatchSize() :
                    folderMsgs.size() - currIdx;

                Message[] fetchMsgs = folderMsgs.subList(currIdx, currIdx + fetchCnt).toArray(new Message[fetchCnt]);

                folder.fetch(fetchMsgs, oldFetchProfile);

                for (Message fetchedMsg : fetchMsgs) {
                    String uid = getUid(fetchedMsg);

                    // This checks if message was expunged by another client while being processed here.
                    if (fetchedMsg.isExpunged() == true || uid == null) {
                        continue;
                    }

                    GridPop3MessageDescriptor msgDesc = descs.get(uid);

                    if (msgDesc == null) {
                        // Stop on unread message.
                        stop = true;

                        break;
                    }

                    // Only delete messages accepted by this client.
                    if (msgDesc.isAccepted()) {
                        if (date.after(msgDesc.getReceiveDate())) {
                            fetchedMsg.setFlag(Flag.DELETED, true);

                            removeCnt++;
                        }
                        else {
                            retainUids.add(uid);
                        }

                        foundCnt++;
                    }
                    else {
                        retainUids.add(uid);
                    }

                    if (foundCnt == descs.size()) {
                        // Stop if all messages are processed.
                        currIdx = folderMsgs.size();

                        break;
                    }
                }

                currIdx += fetchCnt;
            }

            // Remove messages.
            descs.keySet().retainAll(retainUids);

            return removeCnt;
        }
        catch (MessagingException e) {
            throw new GridMailException("Failed to remove old POP3 messages from mailbox: " +
                U.hidePassword(cfg.uri()), e);
        }
    }

    /**
     * Gets received date for message UID.
     *
     * @param uid Message UID.
     * @return Received date.
     */
    private Date getReceivedDate(String uid) {
        GridPop3MessageDescriptor desc = descs.get(uid);

        if (desc != null) {
            return desc.getReceiveDate();
        }

        return new Date();
    }

    /**
     * Gets message UID.
     *
     * @param msg Message.
     * @return String formatted UID.
     * @throws GridMailException Thrown in case of any error.
     */
    private String getUid(Message msg) throws GridMailException {
        // Unknown POP3 provider implementation.
        if (folder instanceof POP3Folder == false) {
            throw new GridMailException("Unknown POP3 provider implementation: " +
                folder.getClass().getCanonicalName());
        }

        try {
            // POP3Folder doesn't implement javax.mail.UIDFolder interface.
            // We need to point to concrete provider implementation.
            return ((POP3Folder)folder).getUID(msg);
        }
        catch (MessagingException e) {
            throw new GridMailException("Failed to get POP3 message UID from: " + msg, e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridPop3Inbox.class, this);
    }
}
