// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;
import java.io.*;

import static org.gridgain.grid.GridSystemProperties.*;

/**
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridLoggerProxy extends GridMetadataAwareAdapter implements GridLogger, Externalizable {
    /** */
    private static ThreadLocal<GridTuple2<String, Object>> stash = new ThreadLocal<GridTuple2<String, Object>>() {
        @Override protected GridTuple2<String, Object> initialValue() {
            return F.t2();
        }
    };

    /** */
    private GridLogger impl;

    /** */
    private String gridName;

    /** */
    private boolean gg;

    /** */
    @GridToStringExclude
    private Object ctgr;

    /** */
    private static final boolean quiet = !(System.getProperty(GG_QUIET) != null &&
        "false".equalsIgnoreCase(System.getProperty(GG_QUIET)));

    /** Whether or not to log grid name. */
    private static final boolean logGridName = System.getProperty(GG_LOG_GRID_NAME) != null;

    /**
     * No-arg constructor is required by externalization.
     */
    public GridLoggerProxy() {
        // No-op.
    }

    /**
     *
     * @param impl Logger implementation to proxy to.
     * @param ctgr Optional logger category.
     * @param gridName Grid name (can be {@code null} for default grid).
     */
    public GridLoggerProxy(GridLogger impl, @Nullable Object ctgr, @Nullable String gridName) {
        assert impl != null;

        this.impl = impl;
        this.ctgr = ctgr;
        this.gridName = gridName;

        // Make sure we don't hide user's log.
        // We should only hide GridGain's log in QUIET mode.
        gg = ctgr != null && (ctgr.toString().contains("org.gridgain"));
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, gridName);
        out.writeObject(ctgr);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        GridTuple2<String, Object> t = stash.get();

        t.set1(U.readString(in));
        t.set2(in.readObject());
    }

    /**
     * Reconstructs object on demarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of demarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            GridTuple2<String, Object> t = stash.get();

            String gridNameR = t.get1();
            Object ctgrR = t.get2();

            return G.grid(gridNameR).log().getLogger(ctgrR);
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public GridLogger getLogger(Object ctgr) {
        assert ctgr != null;
        
        return new GridLoggerProxy(impl.getLogger(ctgr), ctgr, gridName);
    }

    /** {@inheritDoc} */
    @Override public void debug(String msg) {
        impl.debug(enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void info(String msg) {
        impl.info(enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void warning(String msg) {
        impl.warning(enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void warning(String msg, Throwable e) {
        impl.warning(enrich(msg), e);
    }

    /** {@inheritDoc} */
    @Override public void error(String msg) {
        impl.error(enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void error(String msg, Throwable e) {
        impl.error(enrich(msg), e);
    }

    /** {@inheritDoc} */
    @Override public boolean isDebugEnabled() {
        return (!quiet || !gg) && impl.isDebugEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isInfoEnabled() {
        return (!quiet || !gg) && impl.isInfoEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isQuiet() {
        return quiet;
    }

    /**
     * Enriches the log message with grid name if {@link GridSystemProperties#GG_LOG_GRID_NAME}
     * system property is set.
     *
     * @param m Message to enrich.
     * @return Enriched message or the original one.
     */
    private String enrich(@Nullable String m) {
        return logGridName && m != null ? "<" + gridName + "> " + m : m;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridLoggerProxy.class, this);
    }
}
