// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.jms;

import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;
import javax.jms.*;
import javax.jms.Queue;
import java.util.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJmsConfiguration {
    /** */
    private GridLogger log;

    /** */
    private String user;

    /** */
    private String pswd;

    /** */
    private Map<Object, Object> jndiEnv = new HashMap<Object, Object>();

    /** */
    private String connFactoryName;

    /** */
    private String topicName;

    /** */
    private String queueName;

    /** */
    private int deliveryMode = DeliveryMode.NON_PERSISTENT;

    /** */
    private int priority = Message.DEFAULT_PRIORITY;

    /** */
    private long ttl = Message.DEFAULT_TIME_TO_LIVE;

    /** */
    private MessageListener topicMsgListener;

    /** */
    private MessageListener queueMsgListener;

    /** */
    private String selector;

    /** */
    private boolean transacted;

    /** */
    private ConnectionFactory connFactory;

    /** */
    private Queue queue;

    /** */
    private Topic topic;

    /**
     * @return TODO
     */
    public final String getUser() {
        return user;
    }

    /**
     * @param user TODO
     */
    public final void setUser(String user) {
        this.user = user;
    }

    /**
     * @return TODO
     */
    public final String getPassword() {
        return pswd;
    }

    /**
     * @param pswd TODO
     */
    public final void setPassword(String pswd) {
        this.pswd = pswd;
    }

    /**
     * @return TODO
     */
    public final Map<Object, Object> getJndiEnvironment() {
        return jndiEnv;
    }

    /**
     * @param jndiEnv TODO
     */
    public final void setJndiEnvironment(Map<Object, Object> jndiEnv) {
        this.jndiEnv = jndiEnv;
    }

    /**
     * @return TODO
     */
    public final String getConnectionFactoryName() {
        return connFactoryName;
    }

    /**
     * @param connFactoryName TODO
     */
    public final void setConnectionFactoryName(String connFactoryName) {
        this.connFactoryName = connFactoryName;
    }

    /**
     * @return TODO
     */
    public final String getTopicName() {
        return topicName;
    }

    /**
     * @param topicName TODO
     */
    public final void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    /**
     * @return TODO
     */
    public final String getQueueName() {
        return queueName;
    }

    /**
     * @param queueName TODO
     */
    public final void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    /**
     * @param log TODO
     */
    public final void setLogger(GridLogger log) {
        assert log != null;

        this.log = log;
    }

    /**
     * @return TODO
     */
    public final GridLogger getLogger() {
        return log;
    }

    /**
     * @return TODO
     */
    public final MessageListener getTopicMessageListener() {
        return topicMsgListener;
    }

    /**
     * @param topicMsgListener TODO
     */
    public final void setTopicMessageListener(MessageListener topicMsgListener) {
        this.topicMsgListener = topicMsgListener;
    }

    /**
     * @return TODO
     */
    public final MessageListener getQueueMessageListener() {
        return queueMsgListener;
    }

    /**
     * @param queueMsgListener TODO
     */
    public final void setQueueMessageListener(MessageListener queueMsgListener) {
        this.queueMsgListener = queueMsgListener;
    }

    /**
     * @return TODO
     */
    public final String getSelector() {
        return selector;
    }

    /**
     * @param selector TODO
     */
    public final void setSelector(String selector) {
        this.selector = selector;
    }

    /**
     * @return TODO
     */
    public final int getDeliveryMode() {
        return deliveryMode;
    }

    /**
     * @param deliveryMode TODO
     */
    public final void setDeliveryMode(int deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    /**
     * @return TODO
     */
    public final int getPriority() {
        return priority;
    }

    /**
     * @param priority TODO
     */
    public final void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * @return TODO
     */
    public final long getTimeToLive() {
        return ttl;
    }

    /**
     * @param ttl TODO
     */
    public final void setTimeToLive(long ttl) {
        this.ttl = ttl;
    }

    /**
     * @return TODO
     */
    public boolean isTransacted() {
        return transacted;
    }

    /**
     * @param transacted TODO
     */
    public void setTransacted(boolean transacted) {
        this.transacted = transacted;
    }

    /**
     * @return TODO
     */
    public ConnectionFactory getConnectionFactory() {
        return connFactory;
    }

    /**
     * @param connFactory TODO
     */
    public void setConnectionFactory(ConnectionFactory connFactory) {
        this.connFactory = connFactory;
    }

    /**
     * @return TODO
     */
    public Queue getQueue() {
        return queue;
    }

    /**
     * @param queue TODO
     */
    public void setQueue(Queue queue) {
        this.queue = queue;
    }

    /**
     * @return TODO
     */
    public Topic getTopic() {
        return topic;
    }

    /**
     * @param topic TODO
     */
    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJmsConfiguration.class, this);
    }
}
