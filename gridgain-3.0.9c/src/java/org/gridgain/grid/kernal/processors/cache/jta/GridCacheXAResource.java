// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.jta;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;

import javax.transaction.xa.*;

import static org.gridgain.grid.cache.GridCacheTxState.*;

/**
 * Cache XA resource implementation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridCacheXAResource implements XAResource {
    /** Context. */
    private GridCacheContext ctx;

    /** Cache transaction. */
    private GridCacheTxEx cacheTx;

    /** */
    private GridLogger log;

    /** */
    private Xid xid;

    /** */
    private static final Xid[] NO_XID = new Xid[] {};

    /**
     * @param cacheTx Cache jta.
     * @param ctx Cache context.
     */
    public GridCacheXAResource(GridCacheTxEx cacheTx, GridCacheContext ctx) {
        assert cacheTx != null;
        assert ctx != null;

        this.ctx = ctx;
        this.cacheTx = cacheTx;

        log = ctx.logger(getClass());
    }

    /** {@inheritDoc} */
    @Override public void start(Xid xid, int flags) throws XAException {
        if (log.isDebugEnabled()) {
            log.debug("XA resource start(...) [xid=" + xid + ", flags=<" + flags(flags) + ">]");
        }

        // Simply save global transaction id.
        this.xid = xid;
    }

    /** {@inheritDoc} */
    @Override public void rollback(Xid xid) throws XAException {
        assert this.xid.equals(xid);

        if (log.isDebugEnabled()) {
            log.debug("XA resource rollback(...) [xid=" + xid + "]");
        }

        try {
            cacheTx.rollback();
        }
        catch (GridException e) {
            U.error(log, "Failed to rollback cache transaction.", e);

            throw new XAException("Failed to rollback cache transaction: " + e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override public int prepare(Xid xid) throws XAException {
        assert this.xid.equals(xid);

        if (log.isDebugEnabled()) {
            log.debug("XA resource prepare(...) [xid=" + xid + "]");
        }

        if (cacheTx.state() != ACTIVE) {
            throw new XAException("Cache transaction is not in active state.");
        }

        try {
            cacheTx.prepare();
        }
        catch (GridException e) {
            throw new XAException("Failed to prepare cache transaction.");
        }

        return XA_OK;
    }

    /** {@inheritDoc} */
    @Override public void end(Xid xid, int flags) throws XAException {
        assert this.xid.equals(xid);

        if (log.isDebugEnabled()) {
            log.debug("XA resource end(...) [xid=" + xid + ", flags=<" + flags(flags) + ">]");
        }

        if ((flags & TMFAIL) > 0) {
            cacheTx.setRollbackOnly();
        }
    }

    /** {@inheritDoc} */
    @Override public void commit(Xid xid, boolean onePhase) throws XAException {
        assert this.xid.equals(xid);

        if (log.isDebugEnabled()) {
            log.debug("XA resource commit(...) [xid=" + xid + ", onePhase=" + onePhase + "]");
        }

        try {
            cacheTx.commit();
        }
        catch (GridException e) {
            U.error(log, "Failed to commit cache transaction.", e);

            throw new XAException("Failed to commit cache transaction: " + e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override public void forget(Xid xid) throws XAException {
        assert this.xid.equals(xid);

        if (log.isDebugEnabled()) {
            log.debug("XA resource forget(...) [xid=" + xid + "]");
        }

        try {
            cacheTx.invalidate(true);

            cacheTx.commit();
        }
        catch (GridException e) {
            U.error(log, "Failed to forget cache transaction.", e);

            throw new XAException("Failed to forget cache transaction: " + e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override public Xid[] recover(int i) throws XAException {
        if (cacheTx.state() == PREPARED) {
            return new Xid[] { xid };
        }

        return NO_XID;
    }

    /**
     * @param flags JTA Flags.
     * @return Comma-separated flags string.
     */
    private String flags(int flags) {
        StringBuffer res = new StringBuffer();

        addFlag(res, flags, TMENDRSCAN, "TMENDRSCAN");
        addFlag(res, flags, TMFAIL, "TMFAIL");
        addFlag(res, flags, TMJOIN, "TMJOIN");
        addFlag(res, flags, TMNOFLAGS, "TMNOFLAGS");
        addFlag(res, flags, TMONEPHASE, "TMONEPHASE");
        addFlag(res, flags, TMRESUME, "TMRESUME");
        addFlag(res, flags, TMSTARTRSCAN, "TMSTARTRSCAN");
        addFlag(res, flags, TMSUCCESS, "TMSUCCESS");
        addFlag(res, flags, TMSUSPEND, "TMSUSPEND");

        return res.toString();
    }

    /**
     * @param buf String buffer.
     * @param flags Flags bit set.
     * @param mask Bit mask.
     * @param flagName String name of the flag specified by given mask.
     * @return String buffer appended by flag if it's presented in bit set.
     */
    private StringBuffer addFlag(StringBuffer buf, int flags, int mask, String flagName) {
        if ((flags & mask) > 0) {
            buf.append(buf.length() > 0 ? "," : "").append(flagName);
        }

        return buf;
    }

    /** {@inheritDoc} */
    @Override public int getTransactionTimeout() throws XAException {
        return (int)cacheTx.timeout();
    }

    /** {@inheritDoc} */
    @Override public boolean isSameRM(XAResource xar) throws XAException {
        if (xar == this) {
            return true;
        }

        if  (!(xar instanceof GridCacheXAResource)) {
            return false;
        }

        GridCacheXAResource other = (GridCacheXAResource)xar;

        return ctx == other.ctx;
    }

    /** {@inheritDoc} */
    @Override public boolean setTransactionTimeout(int i) throws XAException {
        cacheTx.timeout(i);

        return true;
    }

    /**
     *
     * @return {@code true} if jta was already committed or rolled back.
     */
    public boolean isFinished() {
        GridCacheTxState state = cacheTx.state();

        return state == COMMITTED || state == ROLLED_BACK;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheXAResource.class, this);
    }
}