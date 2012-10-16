// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment.protocol.gg;

import org.gridgain.grid.kernal.managers.deployment.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Connection implementation for custom protocol.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridUrlConnection extends URLConnection {
    /** Deployment manager. */
    private GridDeploymentManager mgr;

    /** Input stream. */
    private InputStream in;

    /**
     * Creates connection.
     *
     * @param url Specified URL.
     * @param mgr Deployment manager.
     */
    public GridUrlConnection(URL url, GridDeploymentManager mgr) {
        super(url);

        assert mgr != null;

        this.mgr = mgr;
    }

    /** {@inheritDoc} */
    @Override public void connect() throws IOException {
        URL url = getURL();

        // Gets class loader UUID.
        UUID ldrId = UUID.fromString(url.getHost());

        // Gets resource name.
        String name = url.getPath();

        GridDeployment dep = mgr.getDeployment(ldrId);

        if (dep != null) {
            in = dep.classLoader().getParent().getResourceAsStream(name);

            // If resource exists
            connected = true;
        }
    }

    /** {@inheritDoc} */
    @Override public InputStream getInputStream() throws IOException {
        /* TODO
        if (!connected) {
            connect();
        }*/

        return in;
    }
}
