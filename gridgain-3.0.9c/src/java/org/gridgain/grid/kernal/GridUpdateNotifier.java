// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import org.jetbrains.annotations.*;
import org.w3c.dom.*;
import org.w3c.tidy.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * This class is responsible for notification about new version availability. Note that this class
 * does not send any information and merely accesses the {@code www.gridgain.org} web site for the
 * latest version data.
 * <p>
 * Note also that this connectivity is not necessary to successfully start the system as it will
 * gracefully ignore any errors occurred during notification and verification process.
 * See {@link #HTTP_URL} for specific access URL used.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridUpdateNotifier {
    /*
     * *********************************************************
     * DO NOT CHANGE THIS URL OR HOW IT IS PUT IN ONE LINE.    *
     * THIS URL IS HANDLED BY POST-BUILD PROCESS AND IT HAS TO *
     * BE PLACED EXACTLY HOW IT IS SHOWING.                    *
     * *********************************************************
     */
    /** Access URL to be used to access latest version data. */
    private static final String HTTP_URL =
        "http://www.gridgain.org/update_status_cmn.php?v=3.0.9c.19052011";

    /** Ant-enhanced system version. */
    private static final String VER = "3.0.9c";

    /** Ant-augmented build number. */
    private static final String BUILD = "19052011";

    /** Throttling for logging out. */
    private static final long THROTTLE_PERIOD = 24 * 60 * 60 * 1000; // 1 day.

    /** Asynchronous checked. */
    private GridWorker checker;

    /** Latest version. */
    private volatile String latestVer;

    /** HTML parsing helper. */
    private final Tidy tidy;

    /** Grid name. */
    private final String gridName;

    /**  Whether or not to report only new version. */
    private final boolean reportOnlyNew;

    /** */
    private final int topSize;

    /** */
    private long lastLog = -1;

    /**
     * Creates new notifier with default values.
     *
     * @param gridName gridName
     * @param reportOnlyNew Whether or not to report only new version.
     * @param topSize Size of topology for license verification purpose.
     */
    GridUpdateNotifier(String gridName, boolean reportOnlyNew, int topSize) {
        tidy = new Tidy();

        tidy.setQuiet(true);
        tidy.setOnlyErrors(true);
        tidy.setShowWarnings(false);
        tidy.setInputEncoding("UTF8");
        tidy.setOutputEncoding("UTF8");

        this.gridName = gridName;
        this.reportOnlyNew = reportOnlyNew;
        this.topSize = topSize;
    }

    /**
     * Starts asynchronous process for retrieving latest version data from {@link #HTTP_URL}.
     *
     * @param exec Executor service.
     * @param log Logger.
     */
    void checkForNewVersion(Executor exec, GridLogger log) {
        assert log != null;

        log = log.getLogger(getClass());

        if (checker == null)
            try {
                exec.execute(checker = new UpdateChecker(log));
            }
            catch (RejectedExecutionException e) {
                U.error(log, "Failed to schedule a thread due to execution rejection (safely ignoring): " +
                    e.getMessage());
            }
    }

    /**
     * Logs out latest version notification if such was received and available.
     *
     * @param log Logger.
     */
    void reportStatus(GridLogger log) {
        assert log != null;

        log = log.getLogger(getClass());

        // Don't join it to avoid any delays on update checker.
        // Checker thread will eventually exit.
        U.cancel(checker);

        String latestVer = this.latestVer;

        if (latestVer != null)
            if (latestVer.equals(VER + '-' + BUILD)) {
                if (!reportOnlyNew)
                    throttle(log, "Your version is up to date.");
            }
            else
                throttle(log, "New version is available at www.gridgain.com: " + latestVer);
        else
            if (!reportOnlyNew)
                throttle(log, "Update status is not available.");
    }

    /**
     *
     * @param log Logger to use.
     * @param msg Message to log.
     */
    private void throttle(GridLogger log, String msg) {
        assert(log != null);
        assert(msg != null);

        long now = System.currentTimeMillis();

        if (now - lastLog > THROTTLE_PERIOD) {
            U.log(log, msg);

            lastLog = now;
        }
    }

    /**
     * Asynchronous checker of the latest version available.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     */
    private class UpdateChecker extends GridWorker {
        /** Logger. */
        private final GridLogger log;

        /**
         * Creates checked with given logger.
         *
         * @param log Logger.
         */
        UpdateChecker(GridLogger log) {
            super(gridName, "grid-version-checker", log);

            this.log = log.getLogger(getClass());
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            try {
                URLConnection conn = new URL(HTTP_URL + (HTTP_URL.endsWith(".php") ? '?' : '&') +
                    (topSize > 0 ? "t=" + topSize + "&" : "") + "p=" + gridName).openConnection();

                if (!isCancelled()) {
                    // Timeout after 3 seconds.
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);

                    InputStream in = null;

                    Document dom = null;

                    try {
                        in = conn.getInputStream();

                        if (in == null)
                            return;

                        dom = tidy.parseDOM(in, null);
                    }
                    finally {
                        U.close(in, log);
                    }

                    if (dom != null)
                        latestVer = obtainVersionFrom(dom);
                }
            }
            catch (IOException ignore) {
                // Ignore this error.
            }
        }

        /**
         * Gets the version from the current {@code node}, if one exists.
         *
         * @param node W3C DOM node.
         * @return Version or {@code null} if one's not found.
         */
        @SuppressWarnings("UnnecessaryFullyQualifiedName")
        @Nullable
        private String obtainVersionFrom(org.w3c.dom.Node node) {
            assert node != null;

            if (node instanceof Element && "meta".equals(node.getNodeName().toLowerCase())) {
                Element meta = (Element)node;

                String name = meta.getAttribute("name");

                if ("version".equals(name)) {
                    String content = meta.getAttribute("content");

                    if (content != null && content.length() > 0)
                        return content;
                }
            }

            NodeList childNodes = node.getChildNodes();

            for (int i = 0; i < childNodes.getLength(); i++) {
                String ver = obtainVersionFrom(childNodes.item(i));

                if (ver != null)
                    return ver;
            }

            return null;
        }
    }
}
