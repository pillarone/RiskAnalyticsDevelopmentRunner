// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.inbox;

import org.gridgain.grid.typedef.internal.*;
import java.util.*;

/**
 * Container for message matching rules used by mail inbox when reading messages.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridMailInboxMatcher {
    /** Subject. */
    private String subj;

    /** Map of message headers. */
    private Map<String, String> hdrs = new HashMap<String, String>();

    /**
     * Sets rule for mail "Subject".
     *
     * @param subj Rule for mail "Subject".
     */
    public void setSubject(String subj) {
        this.subj = subj;
    }

    /**
     * Gets subject used for messages, or {@code null} if messages are not unified by subject.
     *
     * @return Subject, or {@code null} if messages are not unified by subject.
     */
    public String getSubject() {
        return subj;
    }

    /**
     * Appends header to the map of allowed headers.
     *
     * @param name Header name.
     * @param val Header value
     */
    public void addHeader(String name, String val) {
        assert name != null;
        assert val != null;

        hdrs.put(name, val);
    }

    /**
     * Removes header from the map of allowed headers.
     *
     * @param name Header name.
     */
    public void removeHeader(String name) {
        assert name != null;

        hdrs.remove(name);
    }

    /**
     * Gets headers used for messages, or {@code null} if messages not unified by headers.
     *
     * @return Map of allowed headers.
     */
    public Map<String, String> getHeaders() {
        Map<String, String> map = new HashMap<String, String>();

        map.putAll(hdrs);

        return map;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMailInboxMatcher.class, this);
    }
}
