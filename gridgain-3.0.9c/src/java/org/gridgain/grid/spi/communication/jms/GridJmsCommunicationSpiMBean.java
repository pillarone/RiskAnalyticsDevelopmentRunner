// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.communication.jms;

import org.gridgain.grid.spi.*;
import org.gridgain.grid.util.mbean.*;
import javax.jms.*;
import javax.jms.Queue;
import java.util.*;

/**
 * Management bean that provides read-only access to the JMS communication
 * SPI configuration. Beside connectivity this bean shows message delivery mode,
 * queue/topic transaction mode and messages priority.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridMBeanDescription("MBean provides access to the JMS communication SPI configuration.")
public interface GridJmsCommunicationSpiMBean extends GridSpiManagementMBean {
    /**
     * Indicates whether JMS messages are transacted or not.
     *
     * @return {@code true} if session supports transactions,
     *      otherwise {@code false}.
     */
    @GridMBeanDescription("Indicates whether JMS messages are transacted or not.")
    public boolean isTransacted();

    /**
     * Gets messages delivery mode.
     *
     * @return Either {@link DeliveryMode#PERSISTENT} or
     *      {@link DeliveryMode#NON_PERSISTENT}.
     */
    @GridMBeanDescription("Messages delivery mode.")
    public int getDeliveryMode();

    /**
     * Gets messages delivery priority as defined in {@link Message}.
     * The lower the faster.
     *
     * @return Message priority.
     */
    @GridMBeanDescription("Message priority.")
    public int getPriority();

    /**
     * Gets messages lifetime. Messages stays in the queue/topic until they
     * run out of time.
     *
     * @return Time-to-live value in milliseconds.
     */
    @GridMBeanDescription("Time-to-live value in milliseconds.")
    public long getTimeToLive();

    /**
     * Gets JNDI name for JMS queue.
     * If provided, then {@code queue} will be used for node-to-node
     * communication otherwise {@code topic} will be used.
     *
     * @return Name of the queue in JNDI tree.
     */
    @GridMBeanDescription("Name of the queue in JNDI tree.")
    public String getQueueName();

    /**
     * Gets JMS queue.
     * If provided, then {@code queue} will be used for node-to-node
     * communication otherwise {@code topic} will be used.
     *
     * @return JMS queue.
     */
    @GridMBeanDescription("JMS queue.")
    public Queue getQueue();

    /**
     * Gets JNDI name of the JMS topic.
     *
     * @return Name of JMS topic in JNDI tree.
     */
    @GridMBeanDescription("Name of JMS topic in JNDI tree.")
    public String getTopicName();

    /**
     * Gets JMS topic.
     *
     * @return JMS topic.
     */
    @GridMBeanDescription("JMS topic.")
    public Topic getTopic();

    /**
     * Gets naming context variables which are used by node to establish JNDI
     * tree connection.
     *
     * @return Map of JNDI environment variables.
     */
    @GridMBeanDescription("Map of JNDI environment variables.")
    public Map<Object, Object> getJndiEnvironment();

    /**
     * Returns name of the JMS connection factory in JNDI tree.
     *
     * @return Connection factory name.
     */
    @GridMBeanDescription("Connection factory name.")
    public String getConnectionFactoryName();

    /**
     * Returns JMS connection factory.
     *
     * @return Connection factory.
     */
    @GridMBeanDescription("Connection factory.")
    public ConnectionFactory getConnectionFactory();

    /**
     * Gets JMS connection user name for connectivity authentication.
     *
     * @return Name of the user.
     */
    @GridMBeanDescription("Name of the user.")
    public String getUser();

    /**
     * Gets JMS connection password for connectivity authentication.
     *
     * @return User password.
     */
    @GridMBeanDescription("User password.")
    public String getPassword();
}
