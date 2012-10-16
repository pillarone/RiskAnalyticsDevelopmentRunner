// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht.preloader;

import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Partition demand request.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridDhtPartitionDemandMessage<K, V> extends GridCacheMessage<K, V> {
    /** Update sequence. */
    private long updateSeq;

    /** Partition. */
    @GridToStringInclude
    private Set<Integer> parts;

    /** Topic. */
    private String topic;

    /** Timeout. */
    private long timeout;

    /** Worker ID. */
    private int workerId = -1;

    /**
     * @param updateSeq Update sequence for this node.
     */
    GridDhtPartitionDemandMessage(long updateSeq) {
        assert updateSeq > 0;

        this.updateSeq = updateSeq;
    }

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtPartitionDemandMessage() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public boolean isPreloaderMessage() {
        return true;
    }

    /**
     * @param p Partition.
     */
    void addPartition(int p) {
        if (parts == null)
            parts = new HashSet<Integer>();

        parts.add(p);
    }


    /**
     * @return Partition.
     */
    Set<Integer> partitions() {
        return parts;
    }

    /**
     * @return Update sequence.
     */
    long updateSequence() {
        return updateSeq;
    }

    /**
     * @return Reply message timeout.
     */
    long timeout() {
        return timeout;
    }

    /**
     * @param timeout Timeout.
     */
    void timeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * @return Topic.
     */
    String topic() {
        return topic;
    }

    /**
     * @param topic Topic.
     */
    void topic(String topic) {
        this.topic = topic;
    }

    /**
     * @return Worker ID.
     */
    int workerId() {
        return workerId;
    }

    /**
     * @param workerId Worker ID.
     */
    void workerId(int workerId) {
        this.workerId = workerId;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        assert !F.isEmpty(parts);
        assert !F.isEmpty(topic);
        assert workerId >= 0;

        out.writeInt(workerId);
        out.writeLong(updateSeq);
        out.writeLong(timeout);

        U.writeCollection(out, parts);
        U.writeString(out, topic);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        workerId = in.readInt();
        updateSeq = in.readLong();
        timeout = in.readLong();

        parts = U.readSet(in);
        topic = U.readString(in);

        assert !F.isEmpty(parts);
        assert !F.isEmpty(topic);
        assert workerId >= 0;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPartitionDemandMessage.class, this, "partCnt", parts.size(), "super",
            super.toString());
    }
}
