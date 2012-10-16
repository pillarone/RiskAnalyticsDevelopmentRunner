// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.outbox.smtp;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.mail.*;
import org.gridgain.grid.util.mail.outbox.*;
import javax.mail.*;
import java.util.*;

/**
 * This class provides SMTP implementation for {@link GridMailOutbox}. This implementation
 * is based on Java Mail API.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridSmtpOutbox implements GridMailOutbox {
    /** Mail outbox configuration. */
    private GridMailOutboxConfiguration cfg;

    /** Whether or not parameters are prepared. */
    private boolean isPrep;

    /** Additional properties. */
    private Properties props = new Properties();

    /** Session authenticator. */
    private Authenticator auth;

    /**
     * Creates new SMTP outbox with all default values.
     */
    public GridSmtpOutbox() {
        // No-op.
    }

    /**
     * Creates new SMTP mail outbox with specified configuration.
     *
     * @param cfg Mail outbox configuration.
     */
    public GridSmtpOutbox(GridMailOutboxConfiguration cfg) {
        assert cfg != null;

        this.cfg = cfg;
    }

    /**
     * Returns session object for working with mail outbox.
     *
     * @return Session object.
     */
    public GridMailOutboxSession getSession() {
        if (isPrep == false) {
            prepareParameters();

            isPrep = true;
        }

        return new GridMailOutboxSessionAdapter(cfg.getFrom(), cfg.getSubject(), props , auth);
    }

    /**
     * Returns mail outbox configuration.
     *
     * @return Mail outbox configuration.
     */
    public GridMailOutboxConfiguration getConfiguration() {
        return cfg;
    }

    /**
     * Sets mail outbox configuration. Configuration must be defined before method
     * {@link #getSession()} called.
     *
     * @param cfg Mail outbox configuration.
     */
    public void setConfiguration(GridMailOutboxConfiguration cfg) {
        assert cfg != null;

        this.cfg = cfg;
    }

    /**
     * Prepares Java Mail API properties.
     */
    private void prepareParameters() {
        String protoName = cfg.getProtocol().toString().toLowerCase();

        // Session properties.
        props.setProperty("mail.transport.protocol", protoName);

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
        if (cfg.getUsername() != null && cfg.getUsername().length() > 0) {
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

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridSmtpOutbox.class, this);
    }
}
