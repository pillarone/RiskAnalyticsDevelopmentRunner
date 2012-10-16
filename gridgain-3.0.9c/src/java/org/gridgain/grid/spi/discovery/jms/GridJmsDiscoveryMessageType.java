// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.jms;

/**
 * Enumeration of JMS discovery message types.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
enum GridJmsDiscoveryMessageType {
    /** Node is ready. */
    HEARTBEAT,

    /** Node is leaving. */
    LEAVE_GRID,

    /** Request attributes. Node ID contains ID of the requested node. */
    REQUEST_ATTRIBUTES,

    /** Node's attributes. */
    RESULT_ATTRIBUTES,

    /** Request ping response form node. */
    REQUEST_PING,

    /** Response for ping. */
    RESPONSE_PING
}
