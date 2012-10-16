// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.inbox;

import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.mail.*;
import org.gridgain.grid.util.tostring.*;

import java.util.*;

/**
 * This class represents mail inbox configuration.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridMailInboxConfiguration {
    /** Inbox protocol.  */
    private GridMailInboxProtocol proto = GridMailInboxProtocol.POP3;

    /** Mail connection type. */
    private GridMailConnectionType connType = GridMailConnectionType.NONE;

    /** Inbox folder name. */
    private String folderName = "Inbox";

    /** Number of messages read at a time. */
    private int readBatchSize = 100;

    /** Mail server host. */
    private String host;

    /** Mail server port. */
    private int port = 110;

    /** Mail server username. */
    private String user;

    /** Mail sever password. */
    @GridToStringExclude
    private String pswd;

    /** Additional properties. */
    private Properties props = new Properties();

    /** Mail store file name. */
    private String storeFileName;

    /** Grid logger. */
    private GridLogger log;

    /** URI of the mailbox. */
    private String uri;

    /**
     * Gets logger used by this instance.
     *
     * @return The logger.
     */
    public GridLogger getLogger() {
        return log;
    }

    /**
     * Set logger to be used by this instance.
     *
     * @param log The logger to set.
     */
    public void setLogger(GridLogger log) {
        this.log = log;
    }

    /**
     * Gets inbox protocol.
     *
     * @return Inbox protocol.
     */
    public GridMailInboxProtocol getProtocol() {
        return proto;
    }

    /**
     * Sets inbox protocol.
     *
     * @param proto Inbox protocol to set.
     */
    public void setProtocol(GridMailInboxProtocol proto) {
        this.proto = proto;
    }

    /**
     * Gets inbox folder name.
     *
     * @return Inbox folder name.
     */
    public String getFolderName() {
        return folderName;
    }

    /**
     * Sets inbox folder name.
     *
     * @param folderName Inbox folder name.
     */
    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    /**
     * Gets connection type.
     *
     * @return Connection type.
     */
    public GridMailConnectionType getConnectionType() {
        return connType;
    }

    /**
     * Sets connection type.
     *
     * @param connType Connection type to set.
     */
    public void setConnectionType(GridMailConnectionType connType) {
        this.connType = connType;
    }

    /**
     * Gets mail server host.
     *
     * @return Mail server host.
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets mail server host.
     *
     * @param host Mail server host to set.
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Gets mail server port.
     *
     * @return Mail server port.
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets mail server port.
     *
     * @param port Mail server port to set.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Gets account's user name.
     *
     * @return Account's user name.
     */
    public String getUsername() {
        return user;
    }

    /**
     * Sets account's user name.
     *
     * @param user Account's user name to set.
     */
    public void setUsername(String user) {
        this.user = user;
    }

    /**
     * Gets account's password.
     *
     * @return Account's password.
     */
    public String getPassword() {
        return pswd;
    }

    /**
     * Sets account's password.
     *
     * @param pswd Account's password to set.
     */
    public void setPassword(String pswd) {
        this.pswd = pswd;
    }

    /**
     * Gets additional connection properties.
     *
     * @return Additional connection properties.
     */
    public Properties getCustomProperties() {
        return props;
    }

    /**
     * Sets additional connection properties.
     *
     * @param props Additional connection properties to set.
     */
    public void setCustomProperties(Properties props) {
        this.props = props;
    }

    /**
     * Gets file path where mail inbox store data.
     *
     * @return File path or {@code null} if not used.
     */
    public String getStoreFileName() {
        return storeFileName;
    }

    /**
     * Sets file path where mail inbox store data.
     *
     * @param storeFileName File path or {@code null} if not used.
     */
    public void setStoreFileName(String storeFileName) {
        this.storeFileName = storeFileName;
    }

    /**
     * Gets number of messages read at a time.
     *
     * @return message batch size.
     */
    public int getReadBatchSize() {
        return readBatchSize;
    }

    /**
     * Sets number of messages read at a time.
     *
     * @param readBatchSize Message batch size.
     */
    public void setReadBatchSize(int readBatchSize) {
        this.readBatchSize = readBatchSize;
    }

    /**
     * Builds URI from configuration.
     * <p>
     * Result URI <strong>contains password</strong>.
     *
     * @return URI.
     */
    public String uri() {
        if (uri == null) {
            SB sb = new SB();

            if (proto != null)
                sb.a(proto.name()).a(":");

            if (!F.isEmpty(user) || !F.isEmpty(pswd) || !F.isEmpty(host) || port > 0)
                sb.a("//");

            if (!F.isEmpty(user) || !F.isEmpty(pswd))
                sb.a(user).a(":").a(pswd).a("@");

            if (!F.isEmpty(host))
                sb.a(host);

            if (port > 0)
                sb.a(":").a(port);

            uri = sb.toString().toLowerCase();
        }

        return uri;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMailInboxConfiguration.class, this);
    }
}
