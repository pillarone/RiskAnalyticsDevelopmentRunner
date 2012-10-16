// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.communication.mail;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;
import java.util.*;

/**
 * Management bean that provides read-only access to the Mail communication
 * SPI configuration.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean provides access to the Mail communication SPI configuration.")
public interface GridMailCommunicationSpiMBean extends GridSpiManagementMBean {
    /**
     * Gets interval in milliseconds between checking for new messages.
     *
     * @return Interval between receiving messages.
     */
    @GridMBeanDescription("Interval between receiving messages.")
    public long getReceiverDelay();

    /**
     * Gets incoming messages life-time on mail server in milliseconds.
     *
     * @return Time to live for incoming messages.
     */
    @GridMBeanDescription("Time to live for incoming messages.")
    public long getLeaveMessagesOnServer();

    /**
     * Gets type of outgoing mail connection. Should be one of the following:
     * <ul>
     * <li>NONE</li>
     * <li>SSL</li>
     * <li>STARTTLS</li>
     * <li>null</li>
     * </ul>
     *
     * @return Connection type.
     */
    @GridMBeanDescription("Connection type.")
    public String getOutConnectionTypeFormatted();

    /**
     * Gets outgoing mail protocol. Could be one of the following:
     * <ul>
     * <li>SMTP</li>
     * <li>SMTPS</li>
     * <li>null</li>
     * </ul>
     *
     * @return Outgoing mail protocol.
     */
    @GridMBeanDescription("Outgoing mail protocol.")
    public String getOutProtocolFormatted();

    /**
     * Gets outgoing host name for sending email (usually either SMTP or IMAP).
     *
     * @return Outgoing email host name.
     */
    @GridMBeanDescription("Outgoing email host name.")
    public String getOutHost();

    /**
     * Gets port number for outgoing mail.
     *
     * @return Outgoing email port number.
     */
    @GridMBeanDescription("Outgoing email port number.")
    public int getOutPort();

    /**
     * Gets username for outgoing mail authentication.
     *
     * @return Outbox username.
     */
    @GridMBeanDescription("Outbox username.")
    public String getOutUsername();

    /**
     * Gets type of incoming mail connection. Should be one of the following:
     * <ul>
     * <li>NONE</li>
     * <li>SSL</li>
     * <li>STARTTLS</li>
     * <li>null</li>
     * </ul>
     *
     * @return Connection type.
     */
    @GridMBeanDescription("Connection type.")
    public String getInConnectionTypeFormatted();

    /**
     * Gets number of messages fetched from mail server at a time.
     *
     * @return Number of fetched messages.
     */
    @GridMBeanDescription("Number of fetched messages.")
    public int getReadBatchSize();

    /**
     * Sets incoming mail protocol. Could be one of the following:
     * <ul>
     * <li>POP3</li>
     * <li>POP3S</li>
     * <li>IMAP</li>
     * <li>IMAPS</li>
     * <li>null</li>
     * </ul>
     *
     * @return Incoming protocol.
     */
    @GridMBeanDescription("Incoming protocol.")
    public String getInProtocolFormatted();

    /**
     * Gets incoming host name for receiving email (usually either POP or IMAP).
     *
     * @return Incoming email host name.
     */
    @GridMBeanDescription("Incoming email host name.")
    public String getInHost();

    /**
     * Gets incoming host port number for receiving email.
     *
     * @return Incoming email host name.
     */
    @GridMBeanDescription("Incoming email host name.")
    public int getInPort();

    /**
     * Gets incoming host username for receiving email.
     *
     * @return Incoming email host name.
     */
    @GridMBeanDescription("Incoming email host name.")
    public String getInUsername();

    /**
     * Gets folder name of email folder on mail server.
     *
     * @return Email folder name.
     */
    @GridMBeanDescription("Email folder name.")
    public String getFolderName();

    /**
     * Gets email message subject.
     *
     * @return Email message subject.
     */
    @GridMBeanDescription("Email message subject.")
    public String getSubject();

    /**
     * Gets custom properties required for outgoing connection.
     *
     * @return Outgoing connection properties.
     */
    @GridMBeanDescription("Outgoing connection properties.")
    public Properties getOutCustomProperties();

    /**
     * Gets custom properties required for receiving connection.
     *
     * @return Properties.
     */
    @GridMBeanDescription("Custom properties required for receiving connection.")
    public Properties getInCustomProperties();

    /**
     * Gets message field 'From' all email messages.
     *
     * @return Message field 'From'.
     */
    @GridMBeanDescription("Message field 'From' for all email messages.")
    public String getFromAddress();

    /**
     * Gets locally stored full file name for all read messages. Can be either full path
     * or a path relative to GridGain installation home folder.
     *
     * @return Store file path.
     */
    @GridMBeanDescription("Locally stored full file name for all read messages.")
    public String getStoreFileName();
}
