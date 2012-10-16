// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.mail;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;
import java.util.*;

/**
 * Management bean for {@link GridMailDiscoverySpi}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean that provides access to email-based discovery SPI configuration.")
public interface GridMailDiscoverySpiMBean extends GridSpiManagementMBean {
    /**
     * Gets collection of remote nodes' IDs.
     *
     * @return Set of remote nodes IDs.
     */
    @GridMBeanDescription("Set of remote nodes IDs.")
    public Collection<UUID> getRemoteNodeIds();

    /**
     * Gets the number of remote nodes.
     *
     * @return Number of remote nodes.
     */
    @GridMBeanDescription("Number of remote nodes.")
    public int getRemoteNodeCount();

    /**
     * Gets interval in milliseconds between checking for new messages.
     *
     * @return Time period in milliseconds.
     */
    @GridMBeanDescription("Interval in milliseconds between checking for new messages.")
    public long getReceiverDelay();

    /**
     * Gets ping wait timeout in milliseconds.
     *
     * @return Ping wait timeout in milliseconds
     */
    @GridMBeanDescription("Time interval in milliseconds between checking for new messages.")
    public long getPingResponseWait();

    /**
     * Gets delay between heartbeat requests. SPI sends broadcast messages in
     * configurable time interval to another nodes to notify them about node state.
     *
     * @return Time period in milliseconds.
     */
    @GridMBeanDescription("Delay between heartbeat requests in milliseconds.")
    public long getHeartbeatFrequency();

    /**
     * Gets number of heartbeat requests that could be missed before remote
     * node is considered to be failed.
     *
     * @return Number of requests.
     */
    @GridMBeanDescription("Number of heartbeat requests that could be missed before remote node is considered to be failed.")
    public long getMaximumMissedHeartbeats();

    /**
     * Gets incoming messages life-time on mail server in milliseconds.
     *
     * @return Time to live for incoming messages.
     */
    @GridMBeanDescription("Incoming messages life-time on mail server in milliseconds.")
    public long getLeaveMessagesOnServer();

    /**
     * Gets type of outgoing mail connection. It should be one of the following:
     * <ul>
     * <li>NONE</li>
     * <li>SSL</li>
     * <li>STARTTLS</li>
     * <li>null</li>
     * </ul>
     *
     * @return Connection type.
     */
    @GridMBeanDescription("Type of outgoing mail connection.")
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
     * Gets outgoing host name for sending email.
     *
     * @return Outgoing email host name.
     */
    @GridMBeanDescription("Outgoing host name for sending email.")
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
     * Gets type of ingoing mail connection. It should be one of the following:
     * <ul>
     * <li>NONE</li>
     * <li>SSL</li>
     * <li>STARTTLS</li>
     * <li>null</li>
     * </ul>
     *
     * @return Connection type.
     */
    @GridMBeanDescription("Type of ingoing mail connection.")
    public String getInConnectionTypeFormatted();

    /**
     * Gets number of messages fetched from mail server at a time.
     *
     * @return Number of fetched messages.
     */
    @GridMBeanDescription("Number of messages fetched from mail server at a time.")
    public int getReadBatchSize();

    /**
     * Gets incoming mail protocol. Could be one of the following:
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
    @GridMBeanDescription("Incoming mail protocol.")
    public String getInProtocolFormatted();

    /**
     * Gets incoming host name for receiving email.
     *
     * @return Incoming email host name.
     */
    @GridMBeanDescription("Incoming host name for receiving email.")
    public String getInHost();

    /**
     * Gets port number for incoming mail.
     *
     * @return Incoming email port number.
     */
    @GridMBeanDescription("Incoming email port number.")
    public int getInPort();

    /**
     * Gets username for incoming mail authentication.
     *
     * @return Inbox username.
     */
    @GridMBeanDescription("Username for incoming mail authentication.")
    public String getInUsername();

    /**
     * Gets folder name for incoming mail.
     *
     * @return Incoming email folder name.
     */
    @GridMBeanDescription("Incoming email folder name.")
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
     * @return Properties.
     */
    @GridMBeanDescription("Custom properties required for outgoing connection.")
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
    @GridMBeanDescription("Message field 'From' all email messages.")
    public String getFromAddress();

    /**
     * Gets broadcast address used for sending broadcast messages.
     *
     * @return Broadcast email address.
     */
    @GridMBeanDescription("Broadcast address used for sending broadcast messages.")
    public String getBroadcastAddress();

    /**
     * Gets locally stored full file name for all read messages. Can be either full path
     * or a path relative to GridGain installation home folder.
     *
     * @return Store file path.
     */
    @GridMBeanDescription("Locally stored full file name for all read messages.")
    public String getStoreFileName();
}
