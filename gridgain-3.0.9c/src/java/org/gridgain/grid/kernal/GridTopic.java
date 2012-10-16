// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Communication topic.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public enum GridTopic {
    /** */
    TOPIC_JOB,

    /** */
    TOPIC_JOB_SIBLINGS,

    /** */
    TOPIC_TASK,

    /** */
    TOPIC_CHECKPOINT,

    /** */
    TOPIC_CANCEL,

    /** */
    TOPIC_CLASSLOAD,

    /** */
    TOPIC_EVENT,

    /** Cache topic. */
    TOPIC_CACHE,

    /** */
    TOPIC_COMM_USER,

    /** */
    TOPIC_COMM_SYNC,

    /** */
    TOPIC_CLOUD_STATE,

    /** */
    TOPIC_CLOUD_CONTROL,

    /** */
    TOPIC_REST;

    /** Enum values. */
    private static final GridTopic[] VALS = values();

    /**
     * Efficiently gets enumerated value from its ordinal.
     *
     * @param ord Ordinal value.
     * @return Enumerated value.
     */
    @Nullable public static GridTopic fromOrdinal(int ord) {
        return ord >= 0 && ord < VALS.length ? VALS[ord] : null;
    }

    /**
     * This method uses cached instances of {@link StringBuilder} to avoid
     * constant resizing and object creation.
     *
     * @param suffixes Suffixes to append to topic name.
     * @return Grid message topic with specified suffixes.
     */
    public String name(String... suffixes) {
        SB sb = GridStringBuilderFactory.acquire();

        try {
            sb.a(name());

            for (String suffix : suffixes)
                sb.a('-').a(suffix);

            return sb.toString();
        }
        finally {
            GridStringBuilderFactory.release(sb);
        }
    }

    /**
     * This method uses cached instances of {@link StringBuilder} to avoid
     * constant resizing and object creation.
     *
     * @param ids Topic IDs.
     * @return Grid message topic with specified IDs.
     */
    public String name(UUID... ids) {
        SB sb = GridStringBuilderFactory.acquire();

        try {
            sb.a(name());

            for (UUID id : ids)
                sb.a('-').a(id.getLeastSignificantBits()).a('-').a(id.getMostSignificantBits());

            return sb.toString();
        }
        finally {
            GridStringBuilderFactory.release(sb);
        }
    }
}
