// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.deployment.uri.scanners.mail;

import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.spi.deployment.uri.*;
import org.gridgain.grid.spi.deployment.uri.scanners.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.mail.*;
import org.gridgain.grid.util.mail.inbox.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * URI deployment mail scanner.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridUriDeploymentMailScanner extends GridUriDeploymentScanner {
    /** */
    static final String DFLT_STORE_FILE_NAME = "grid-email-deploy-msgs.dat";

    /** */
    private static final String MIME_JAR = "application/x-jar";

    /** */
    private static final String MIME_ZIP = "application/zip";

    /** */
    private static final String MIME_OCTET_STREAM = "application/octet-stream";

    /** */
    private GridMailInbox inbox;

    /** */
    private final GridMailInboxConfiguration cfg;

    /** */
    private String subj = GridUriDeploymentSpi.DFLT_MAIL_SUBJECT;

    /** */
    private boolean allMsgsCheck;

    /** */
    private Collection<String> msgsFileUidCache = new HashSet<String>();

    /** */
    private final GridMarshaller marsh;

    /**
     * @param gridName Grid instance name.
     * @param uri Mail URI.
     * @param deployDir Deployment directory.
     * @param freq Scanner frequency.
     * @param filter File name filter.
     * @param lsnr Deployment filter.
     * @param log Logger to use.
     * @param marsh Marshaller to marshal and unmarshal objects.
     */
    public GridUriDeploymentMailScanner(
        String gridName,
        URI uri,
        File deployDir,
        long freq,
        FilenameFilter filter,
        GridUriDeploymentScannerListener lsnr,
        GridLogger log,
        GridMarshaller marsh) {
        super(gridName, uri, deployDir, freq, filter, lsnr, log);

        this.marsh = marsh;

        cfg = initializeConfiguration(uri);

        cfg.setLogger(log);
    }

    /**
     * @return Initialized inbox.
     */
    private GridMailInbox initializeMailbox() {
        GridMailInboxMatcher matcher = new GridMailInboxMatcher();

        matcher.setSubject(subj);

        return GridMailInboxFactory.createInbox(cfg, matcher, marsh);
    }

    /**
     * @param uri Configuration URI.
     * @return Mail inbox configuration.
     */
    private GridMailInboxConfiguration initializeConfiguration(URI uri) {
        GridMailInboxConfiguration cfg = new GridMailInboxConfiguration();

        GridMailConnectionType connType = GridMailConnectionType.NONE;

        String userInfo = uri.getUserInfo();
        String username = null;
        String pswd = null;

        if (userInfo != null) {
            String[] arr = userInfo.split(";");

            if (arr != null && arr.length > 0) {
                for (String el : arr) {
                    if (el.startsWith("auth=")) {
                        connType = GridMailConnectionType.valueOf(el.substring(5).toUpperCase());
                    }
                    else if (el.startsWith("freq=")) {
                        // No-op.
                    }
                    else if (el.startsWith("subj=")) {
                        subj = el.substring(5);
                    }
                    else if (el.indexOf(':') != -1) {
                        int idx = el.indexOf(':');

                        username = el.substring(0, idx);
                        pswd = el.substring(idx + 1);
                    }
                    else {
                        username = el;
                    }
                }
            }
        }

        cfg.setConnectionType(connType);
        cfg.setProtocol(GridMailInboxProtocol.valueOf(uri.getScheme().toUpperCase()));
        cfg.setHost(uri.getHost());
        cfg.setPort(uri.getPort());
        cfg.setUsername(username);
        cfg.setPassword(pswd);
        cfg.setStoreFileName(DFLT_STORE_FILE_NAME);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void process() {
        long start = System.currentTimeMillis();

        processMail();

        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Mail scanner time in milliseconds: " + (System.currentTimeMillis() - start));
        }
    }

    /** */
    private void processMail() {
        try {
            if (inbox == null)
                inbox = initializeMailbox();

            // Open mailbox with readonly mode.
            inbox.open(true);

            List<GridMailInboxMessage> msgs = allMsgsCheck ? inbox.readNew() : inbox.readAll();

            if (msgs != null) {
                for (GridMailInboxMessage msg : msgs) {
                    if (msg.getAttachmentCount() > 0) {
                        for (int i = 0; i < msg.getAttachmentCount(); i++) {
                            GridMailInboxAttachment attach = msg.getAttachment(i);

                            String fileName = attach.getFileName();

                            String msgFileUid = msg.getUid() + '_' + i;

                            if (fileName != null &&
                                (
                                    attach.isMimeType(MIME_OCTET_STREAM) ||
                                    attach.isMimeType(MIME_JAR) ||
                                    attach.isMimeType(MIME_ZIP)
                                )
                                &&
                                getFilter().accept(null, fileName.toLowerCase())
                                &&
                                !msgsFileUidCache.contains(msgFileUid)
                                ) {
                                try {
                                    File file = createTempFile(fileName, getDeployDirectory());

                                    attach.saveToFile(file);

                                    String fileUri = getFileUri(msgFileUid);

                                    // Delete file when JVM stopped.
                                    file.deleteOnExit();

                                    Date date = msg.getReceivedDate();

                                    assert date != null;

                                    getListener().onNewOrUpdatedFile(file, fileUri, date.getTime());
                                }
                                catch (IOException e) {
                                    getLogger().error("Failed to save: " + fileName, e);
                                }
                            }

                            msgsFileUidCache.add(msgFileUid);
                        }
                    }
                }
            }

            allMsgsCheck = true;

            msgsFileUidCache.clear();
        }
        catch (GridMailException e) {
            if (!isCancelled()) {
                String maskedUri = getUri() != null ? U.hidePassword(getUri().toString()) : null;

                if (e.hasCause(ConnectException.class))
                    LT.warn(getLogger(), e, "Failed to connect to mail server (connection refused): " + maskedUri);

                else if (e.hasCause(UnknownHostException.class))
                    LT.warn(getLogger(), e, "Failed to connect to mail server (host is unknown): " + maskedUri);

                else
                    U.error(getLogger(), "Failed to get messages from mail server: " + maskedUri, e);
            }
        }
        finally {
            if (inbox != null) {
                try {
                    inbox.flush();
                }
                catch (GridMailException e) {
                    if (!isCancelled())
                        U.error(getLogger(), "Failed to flush mailbox.", e);
                }

                U.closeQuiet(inbox, false);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridUriDeploymentMailScanner.class, this,
            "uri", getUri() != null ? U.hidePassword(getUri().toString()) : null);
    }
}
