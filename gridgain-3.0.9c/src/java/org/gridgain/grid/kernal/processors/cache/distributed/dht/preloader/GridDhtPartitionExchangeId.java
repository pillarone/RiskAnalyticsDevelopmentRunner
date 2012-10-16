// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * Exchange ID.
 */
public class GridDhtPartitionExchangeId implements Comparable<GridDhtPartitionExchangeId>, Externalizable {
    /** Node ID. */
    private UUID nodeId;

    /** Event. */
    @GridToStringExclude
    private int evt;

    /** Node order. */
    private long order;

    /** */
    private long timestamp;

    /**
     * @param nodeId Node ID.
     * @param evt Event.
     * @param order Order.
     * @param timestamp Event timestamp.
     */
    GridDhtPartitionExchangeId(UUID nodeId, int evt, long order, long timestamp) {
        assert nodeId != null;
        assert order > 0;
        assert timestamp > 0;
        assert evt == EVT_NODE_LEFT || evt == EVT_NODE_FAILED || evt == EVT_NODE_JOINED;

        this.nodeId = nodeId;
        this.evt = evt;
        this.order = order;
        this.timestamp = timestamp;
    }

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridDhtPartitionExchangeId() {
        // No-op.
    }

    /**
     * @return Node ID.
     */
    public UUID nodeId() {
        return nodeId;
    }

    /**
     * @return Event.
     */
    public int event() {
        return evt;
    }

    /**
     * @return Order.
     */
    public long order() {
        return order;
    }

    /**
     * @return Timestamp.
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * @return {@code True} if exchange is for new node joining.
     */
    public boolean isJoined() {
        return evt == EVT_NODE_JOINED;
    }

    /**
     * @return {@code True} if exchange is for node leaving.
     */
    public boolean isLeft() {
        return evt == EVT_NODE_LEFT || evt == EVT_NODE_FAILED;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeUuid(out, nodeId);
        out.writeLong(order);
        out.writeInt(evt);
        out.writeLong(timestamp);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        nodeId = U.readUuid(in);
        order = in.readLong();
        evt = in.readInt();
        timestamp = in.readLong();
    }

    /** {@inheritDoc} */
    @Override public int compareTo(GridDhtPartitionExchangeId o) {
        if (o == this)
            return 0;

        if (order != o.order)
            return isJoined() && o.isJoined() ? order < o.order ? -1 : 1 : timestamp < o.timestamp ? -1 : 1;

        if (!nodeId.equals(o.nodeId))
            return nodeId.compareTo(o.nodeId);

        // Same node, different events.
        if (evt != o.evt)
            return evt == EVT_NODE_JOINED ? -1 : 1;

        return 0;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = nodeId.hashCode();

        res = 31 * res + evt;
        res = 31 * res + (int)(order ^ (order >>> 32));

        return res;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (o == this)
            return true;

        GridDhtPartitionExchangeId id = (GridDhtPartitionExchangeId)o;

        return evt == id.evt && order == id.order && nodeId.equals(id.nodeId);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPartitionExchangeId.class, this, "evt", U.gridEventName(evt));
    }
}
