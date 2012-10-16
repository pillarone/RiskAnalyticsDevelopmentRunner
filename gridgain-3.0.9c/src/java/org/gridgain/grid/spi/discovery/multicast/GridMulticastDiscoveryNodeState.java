// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.multicast;

/**
 * Remote node state from local one point of view. Being started remote node has {@link #NEW} state.
 * Than after receiving and exchanging attributes node gets status {@link #READY}.
 * When remote node is leaving grid it sends corresponded message and its state
 * is changed to {@link #LEFT}. Another way which node could move to {@link #LEFT}
 * state is just stop sending heartbeat requests and local node will change remote
 * one state to {@link #LEFT}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
enum GridMulticastDiscoveryNodeState {
    /** Node appears in grid. */
    NEW,

    /** Remote node attributes received and node is accessible. */
    READY,

    /** Remote node has left grid. */
    LEFT
}
