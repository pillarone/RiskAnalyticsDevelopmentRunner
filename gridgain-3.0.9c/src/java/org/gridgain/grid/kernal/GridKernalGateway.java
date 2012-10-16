// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.util.tostring.*;

/**
 * This interface guards access to implementations of public methods that access kernal
 * functionality from the following main API interfaces:
 * <ul>
 * <li>{@link GridProjection}</li>
 * <li>{@link GridRichCloud}</li>
 * <li>{@link GridRichNode}</li>
 * </ul>
 * Note that this kernal gateway <b>should not</b> be used to guard against method from
 * the following non-rich interfaces since their implementations are already managed
 * by their respective implementing classes:
 * <ul>
 * <li>{@link Grid}</li>
 * <li>{@link GridCloud}</li>
 * <li>{@link GridNode}</li>
 * </ul>
 * Kernal gateway is also responsible for notifying various futures about the change in
 * kernal state so that issued futures could properly interrupt themselves when kernal
 * becomes unavailable while future is held externally by the user.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridToStringExclude
public interface GridKernalGateway {
    /**
     * Should be called on entering every kernal related call
     * <b>originated directly or indirectly via public API</b>.
     * <p>
     * This method essentially acquires a read lock and multiple threads
     * can enter the call without blocking.
     *
     * @throws IllegalStateException Thrown in case when no kernal calls are allowed.
     */
    public void readLock() throws IllegalStateException;

    /**
     * Sets kernal state. Various kernal states drive the logic inside of the gateway.
     *
     * @param state Kernal state to set.
     */
    public void setState(GridKernalState state);

    /**
     * Gets current kernal state.
     *
     * @return Kernal state.
     */
    public GridKernalState getState();

    /**
     * Should be called on leaving every kernal related call
     * <b>originated directly or indirectly via public API</b>.
     * <p>
     * This method essentially releases the internal read-lock acquired previously
     * by {@link #readLock()} method.
     */
    public void readUnlock();

    /**
     * This method waits for all current calls to exit and blocks any further
     * {@link #readLock()} calls until {@link #writeUnlock()} method is called.
     * <p>
     * This method essentially acquires the internal write lock.
     */
    public void writeLock();
    
    /**
     * This method unblocks {@link #writeLock()}.
     * <p>
     * This method essentially releases internal write lock previously acquired
     * by {@link #writeLock()} method.
     */
    public void writeUnlock();

    /**
     * Adds stop listener. Note that the identity set will be used to store listeners for
     * performance reasons. Futures can register a listener to be notified when they need to
     * be internally interrupted.
     *
     * @param lsnr Listener to add.
     */
    public void addStopListener(Runnable lsnr);

    /**
     * Removes previously added stop listener.
     *
     * @param lsnr Listener to remove.
     */
    public void removeStopListener(Runnable lsnr);
}

