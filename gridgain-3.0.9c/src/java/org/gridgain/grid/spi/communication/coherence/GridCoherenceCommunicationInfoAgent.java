// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.communication.coherence;

import com.tangosol.net.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.typedef.internal.*;
import java.util.*;

/**
 * Contains task which should be running on destination Coherence member node.
 * SPI will send that objects only to members with started invocation service
 * {@link InvocationService} with name
 * {@link GridCoherenceCommunicationSpi#setServiceName(String)}. The agents used as
 * transport to notify remote communication SPI's.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridCoherenceCommunicationInfoAgent extends AbstractInvocable {
    /** Sender UID. */
    private UUID srcNodeId;

    /**
     * Creates an agent.
     *
     * @param srcNodeId Sender UID.
     */
    GridCoherenceCommunicationInfoAgent(UUID srcNodeId) {
        assert srcNodeId != null;

        this.srcNodeId = srcNodeId;
    }

    /** {@inheritDoc} */
    @Override public void run() {
       GridSpiManagementMBean spi = (GridCoherenceCommunicationSpi)getService().getUserContext();

        getLog().println("Node sent agent to remote node for getting nodeId: " + srcNodeId);

        if (spi != null) {
            setResult(spi.getLocalNodeId());
        }
        else {
            getErr().println("Failed to get SPI from user context.");
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCoherenceCommunicationInfoAgent.class, this);
    }
}
