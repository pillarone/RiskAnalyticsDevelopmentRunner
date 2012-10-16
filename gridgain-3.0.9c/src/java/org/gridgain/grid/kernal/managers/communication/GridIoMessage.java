// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.communication;

import org.gridgain.grid.kernal.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

/**
 * Wrapper for all grid messages.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridIoMessage implements Externalizable {
    /** Sender ID. */
    private UUID senderId;

    /** */
    @GridToStringInclude
    private List<UUID> destIds;

    /** Message topic. */
    private String topic;

    /** Topic ordinal. */
    private int topicOrd = -1;

    /** Message order. */
    private long msgId = -1;

    /** Message timeout. */
    private long timeout;

    /** Message body. */
    private GridByteArrayList msg;

    /** Message processing policy. */
    private GridIoPolicy policy;

    /** Message receive time. */
    private final long rcvTime = System.currentTimeMillis();

    /**
     * No-op constructor to support {@link Externalizable} interface.
     * This constructor is not meant to be used for other purposes.
     */
    public GridIoMessage() {
        // No-op.
    }

    /**
     * @param senderId Node ID.
     * @param destId Destination ID.
     * @param topic Communication topic.
     * @param topicOrd Topic ordinal value.
     * @param msg Communication message.
     * @param policy Thread policy.
     */
    public GridIoMessage(UUID senderId, UUID destId, String topic, int topicOrd, GridByteArrayList msg,
        GridIoPolicy policy) {
        this(senderId, Collections.singletonList(destId), topic, topicOrd, msg, policy);
    }

    /**
     * @param senderId Node ID.
     * @param destIds Destination IDs.
     * @param topic Communication topic.
     * @param topicOrd Topic ordinal value.
     * @param msg Communication message.
     * @param policy Thread policy.
     */
    public GridIoMessage(UUID senderId, List<UUID> destIds, String topic, int topicOrd,
        GridByteArrayList msg, GridIoPolicy policy) {
        assert senderId != null;
        assert destIds != null;
        assert topic != null;
        assert topicOrd <= Byte.MAX_VALUE;
        assert policy != null;
        assert msg != null;

        this.senderId = senderId;
        this.destIds = destIds;
        this.msg = msg;
        this.topic = topic;
        this.topicOrd = topicOrd;
        this.policy = policy;
    }

    /**
     * @param senderId Node ID.
     * @param destId Destination node ID.
     * @param topic Communication topic.
     * @param topicOrd Topic ordinal value.
     * @param msg Communication message.
     * @param policy Thread policy.
     * @param msgId Message ID.
     * @param timeout Timeout.
     */
    public GridIoMessage(UUID senderId, UUID destId, String topic, int topicOrd, GridByteArrayList msg,
        GridIoPolicy policy, long msgId, long timeout) {
        this(senderId, Collections.singletonList(destId), topic, topicOrd, msg, policy);

        this.msgId = msgId;
        this.timeout = timeout;
    }

    /**
     * @param senderId Node ID.
     * @param destIds Destination node IDs.
     * @param topic Communication topic.
     * @param topicOrd Topic ordinal value.
     * @param msg Communication message.
     * @param policy Thread policy.
     * @param msgId Message ID.
     * @param timeout Timeout.
     */
    public GridIoMessage(UUID senderId, List<UUID> destIds, String topic, int topicOrd,
        GridByteArrayList msg, GridIoPolicy policy, long msgId, long timeout) {
        this(senderId, destIds, topic, topicOrd, msg, policy);

        this.msgId = msgId;
        this.timeout = timeout;
    }

    /**
     * @return Topic.
     */
    String topic() {
        return topic;
    }

    /**
     * @return Topic ordinal.
     */
    int topicOrdinal() {
        return topicOrd;
    }

    /**
     * @return Message.
     */
    public GridByteArrayList message() {
        return msg;
    }

    /**
     * @return Policy.
     */
    GridIoPolicy policy() {
        return policy;
    }

    /**
     * @return Message ID.
     */
    long messageId() {
        return msgId;
    }

    /**
     * @return Message timeout.
     */
    public long timeout() {
        return timeout;
    }

    /**
     * @return {@code True} if message is ordered, {@code false} otherwise.
     */
    boolean isOrdered() {
        return msgId > 0;
    }

    /**
     * @return Sender node ID.
     */
    UUID senderId() {
        return senderId;
    }

    /**
     * @param senderId Sender ID.
     */
    void senderId(UUID senderId) {
        this.senderId = senderId;
    }

    /**
     * @return Gets destination node IDs.
     */
    Collection<UUID> destinationIds() {
        return destIds;
    }

    /**
     * @return Message receive time.
     */
    long receiveTime() {
        return rcvTime;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(msg);
        out.writeLong(msgId);
        out.writeLong(timeout);

        // Special enum handling.
        out.writeByte(policy.ordinal());

        U.writeUuid(out, senderId);

        out.writeByte(topicOrd);

        if (topicOrd < 0)
            U.writeString(out, topic);

        // Write destination IDs.
        out.writeInt(destIds.size());

        // Purposely don't use foreach loop for
        // better performance.
        for (UUID destId : destIds)
            U.writeUuid(out, destId);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        msg = (GridByteArrayList)in.readObject();
        msgId = in.readLong();
        timeout = in.readLong();

        byte ord = in.readByte();

        // Account for incorrect message and check for positive enum ordinal.
        policy = ord >= 0 ? GridIoPolicy.fromOrdinal(ord) : null;

        senderId = U.readUuid(in);

        topicOrd = in.readByte();

        if (topicOrd < 0) {
            topic = U.readString(in);
        }
        else {
            GridTopic topic = GridTopic.fromOrdinal(topicOrd);

            if (topic == null)
                throw new IOException("Failed to deserialize grid topic from ordinal: " + topicOrd);

            this.topic = topic.name();
        }

        int size = in.readInt();

        if (size == 1) {
            destIds = Collections.singletonList(U.readUuid(in));
        }
        else {
            destIds = new ArrayList<UUID>(size);

            for (int i = 0; i < size; i++)
                destIds.add(U.readUuid(in));
        }
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (!(obj instanceof GridIoMessage))
            return false;

        GridIoMessage other = (GridIoMessage)obj;

        return policy == other.policy && topic.equals(other.topic) && msgId == other.msgId &&
            senderId.equals(other.senderId) && destIds.equals(other.destIds);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = topic.hashCode();

        res = 31 * res + (int)(msgId ^ (msgId >>> 32));
        res = 31 * res + msg.hashCode();
        res = 31 * res + policy.hashCode();
        res = 31 * res + senderId.hashCode();
        res = 31 * res + topic.hashCode();

        return res;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridIoMessage.class, this);
    }
}
