// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.jms;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.jms.*;

/**
 * JMS discovery SPI configuration bean. Provides all necessary
 * properties to configure JMS connectivity.
 * <p>
 * Unless explicitly specified, the following properties will
 * be assigned default values.
 * <ul>
 * <li>Topic name. Default is {@link #DFLT_TOPIC_NAME}.</li>
 * <li>Heartbeat frequency. Default is {@link #DFLT_HEARTBEAT_FREQ}.</li>
 * <li>Number of missed heartbeats. Default is {@link #DFLT_MAX_MISSED_HEARTBEATS}.</li>
 * <li>Ping wait time. Default is {@link #DFLT_PING_WAIT_TIME}.</li>
 * <li>Time-To-Live value. Default is {@link #DFLT_TIME_TO_LIVE}.</li>
 * </ul>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJmsDiscoveryConfiguration extends GridJmsConfiguration {
    /** Default heartbeat frequency in milliseconds (value is {@code 5000}). */
    public static final long DFLT_HEARTBEAT_FREQ = 5000;

    /** Default Time-To-Live value in milliseconds (value is {@code 10000}). */
    public static final long DFLT_TIME_TO_LIVE = 10000;

    /**
     * Default number of heartbeat messages that could be missed until
     * node is considered to be failed (value is {@code 3}).
     */
    public static final int DFLT_MAX_MISSED_HEARTBEATS = 3;

    /**
     * Default ping timeout value in milliseconds. If there is no answer
     * after this time remote node is considered to be failed
     * (value is {@code 5000}).
     */
    public static final long DFLT_PING_WAIT_TIME = 5000;

    /**
     * Default handshake timeout in milliseconds. If there is no answer
     * after this time remote node would not join to the grid.
     */
    public static final long DFLT_HANDSHAKE_WAIT_TIME = 5000;

    /**
     * Default JMS topic name that will be used by discovery process
     * (value is {@code org.gridgain.grid.spi.discovery.jms.GridJmsDiscoveryConfiguration.jms.topic}).
     */
    public static final String DFLT_TOPIC_NAME = GridJmsDiscoveryConfiguration.class.getName() + ".jms.topic";

    /** Default maximum handshake threads. */
    public static final int DFLT_MAX_HANDSHAKE_THREADS = 10;

    /** Delay between heartbeat requests. */
    private long beatFreq = DFLT_HEARTBEAT_FREQ;

    /** Number of heartbeat messages that could be missed before remote node is considered as failed one. */
    private long maxMissedBeats = DFLT_MAX_MISSED_HEARTBEATS;

    /** Ping wait timeout. */
    private long pingWaitTime = DFLT_PING_WAIT_TIME;

    /** Handshake wait timeout. */
    private long handshakeWaitTime = DFLT_HANDSHAKE_WAIT_TIME;

    /** Maximum handshake threads. */
    private int maxHandshakeThreads = DFLT_MAX_HANDSHAKE_THREADS;

    /**
     * Creates instance of configuration. Sets topic name and Time-To-Live to default values.
     *
     * @see #DFLT_TOPIC_NAME
     * @see #DFLT_TIME_TO_LIVE
     */
    GridJmsDiscoveryConfiguration() {
        setTimeToLive(DFLT_TIME_TO_LIVE);
        setTopicName(DFLT_TOPIC_NAME);
    }

    /**
     * Gets interval for heartbeat messages.
     *
     * @return Time in milliseconds.
     */
    final long getHeartbeatFrequency() {
        return beatFreq;
    }

    /**
     * Sets interval for heartbeat messages.This configuration parameter is optional.
     * If not provided, default value is {@link GridJmsDiscoveryConfiguration#DFLT_HEARTBEAT_FREQ}.
     *
     * @param beatFreq Time in milliseconds.
     */
    final void setHeartbeatFrequency(long beatFreq) {
        this.beatFreq = beatFreq;
    }

    /**
     * Gets numbers of heartbeat messages that could be missed before node
     * is considered to be failed.
     *
     * @return Number of heartbeat messages.
     */
    final long getMaximumMissedHeartbeats() {
        return maxMissedBeats;
    }

    /**
     * Sets numbers of heartbeat messages that could be missed before
     * node is considered to be failed. This configuration parameter is optional.
     * <p>
     * If not provided, default value is {@link GridJmsDiscoveryConfiguration#DFLT_MAX_MISSED_HEARTBEATS}.
     *
     * @param maxMissedBeats Number of heartbeat messages.
     */
    final void setMaximumMissedHeartbeats(long maxMissedBeats) {
        this.maxMissedBeats = maxMissedBeats;
    }

    /**
     * Gets ping timeout after which if there is no answer node is
     * considered to be failed.
     *
     * @return Time in milliseconds.
     */
    final long getPingWaitTime() {
        return pingWaitTime;
    }

    /**
     * Sets ping timeout after which if there is no answer node is
     * considered to be failed. This configuration parameter is optional.
     * <p>
     * If not provided, default value is {@link GridJmsDiscoveryConfiguration#DFLT_PING_WAIT_TIME}.
     *
     * @param pingWaitTime Time in milliseconds.
     */
    final void setPingWaitTime(long pingWaitTime) {
        this.pingWaitTime = pingWaitTime;
    }

    /**
     * Gets handshake timeout.
     *
     * @return handshakeWaitTime Time in milliseconds.
     */
    final long getHandshakeWaitTime() {
        return handshakeWaitTime;
    }

    /**
     * Sets handshake timeout. When node gets heartbeat from remote node
     * it asks for the attributes. If remote node does not send them back
     * and this time is out remote node would not be added in grid.
     *
     * @param handshakeWaitTime Time in milliseconds.
     */
    final void setHandshakeWaitTime(long handshakeWaitTime) {
        this.handshakeWaitTime = handshakeWaitTime;
    }

    /**
     * Returns maximum number of handshake threads. This means maximum
     * number of handshakes that can be executed in parallel.
     *
     * @return maximum number of handshake threads.
     */
    final int getMaximumHandshakeThreads() {
        return maxHandshakeThreads;
    }

    /**
     * Sets maximum number of handshake threads. This means maximum
     * number of handshakes that can be executed in parallel.
     * This configuration parameter is optional.
     * <p>
     * If not provided, default value is {@link #DFLT_MAX_HANDSHAKE_THREADS}.
     *
     * @param maxHandshakeThreads maximum number of handshake threads.
     */
    final void setMaximumHandshakeThreads(int maxHandshakeThreads) {
        this.maxHandshakeThreads = maxHandshakeThreads;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJmsDiscoveryConfiguration.class, this);
    }
}
