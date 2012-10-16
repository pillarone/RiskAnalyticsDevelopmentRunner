// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.swapspace;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.swapspace.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.lang.utils.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.gridgain.grid.GridEventType.*;

/**
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridSwapSpaceManager extends GridManagerAdapter<GridSwapSpaceSpi> {
    /** */
    private final String globalSpace;

    /** Local node ID. */
    private UUID locNodeId;

    /**
     * Creates nw manager instance.
     *
     * @param ctx Grid kernal context.
     */
    public GridSwapSpaceManager(GridKernalContext ctx) {
        super(GridSwapSpaceSpi.class, ctx, ctx.config().getSwapSpaceSpi());

        globalSpace = "global-swapspace-" + UUID.randomUUID().toString();
    }

    /** {@inheritDoc} */
    @Override public void start() throws GridException {
        startSpi();

        locNodeId = ctx.localNodeId();

        if (log.isDebugEnabled()) {
            log.debug(startInfo());
        }
    }

    /** {@inheritDoc} */
    @Override public void stop() throws GridException {
        stopSpi();

        if (log.isDebugEnabled()) {
            log.debug(stopInfo());
        }
    }

    /**
     * Reads value from swap.
     *
     * @param space Space name.
     * @param key Key.
     * @return Value.
     * @throws GridException In case of error.
     */
    @Nullable
    public GridSwapByteArray read(String space, GridSwapByteArray key) throws GridException {
        assert space != null;
        assert key != null;

        try {
            GridSwapByteArray val = getSpi().read(space, key);

            if (val != null) {
                recordEvent(EVT_SWAP_SPACE_DATA_READ, space);
            }

            return val;
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to read from swap space [space=" + space + ", key=" + key + ']', e);
        }
    }

    /**
     * Reads value from swap.
     *
     * @param space Space name.
     * @param key Key.
     * @return Value.
     * @throws GridException In case of error.
     */
    @SuppressWarnings({"unchecked", "IfMayBeConditional"})
    @Nullable
    public <T> T read(String space, Object key) throws GridException {
        assert space != null;
        assert key != null;

        try {
            GridSwapByteArray marshKey = marshal(key);

            GridSwapByteArray val = getSpi().read(space, marshKey);

            if (val != null) {
                recordEvent(EVT_SWAP_SPACE_DATA_READ, space);

                return (T)unmarshal(val, key.getClass().getClassLoader());
            }
            else {
                return null;
            }
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to read from swap space [space=" + space + ", key=" + key + ']', e);
        }
    }

    /**
     * Writes value to swap.
     *
     * @param space Space name.
     * @param key Key.
     * @param val Value.
     * @throws GridException In case of error.
     */
    public void write(String space, GridSwapByteArray key, GridSwapByteArray val) throws GridException {
        assert space != null;
        assert key != null;
        assert val != null;

        try {
            getSpi().store(space, key, val);

            recordEvent(EVT_SWAP_SPACE_DATA_STORED, space);
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to write to swap space [space=" + space + ", key=" + key +
                ", val=" + val + ']', e);
        }
    }

    /**
     * Writes value to swap.
     *
     * @param space Space name.
     * @param key Key.
     * @param val Value.
     * @throws GridException In case of error.
     */
    public void write(String space, Object key, @Nullable Object val) throws GridException {
        assert space != null;
        assert key != null;

        try {
            getSpi().store(space, marshal(key), marshal(val));

            recordEvent(EVT_SWAP_SPACE_DATA_STORED, space);
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to write to swap space [space=" + space + ", key=" + key +
                ", val=" + val + ']', e);
        }
    }

    /**
     * Removes value from swap.
     *
     * @param space Space name.
     * @param key Key.
     * @param c Optional closure that takes removed value and executes after actual
     *      removing. If there was no value in storage the closure is executed given
     *      {@code null} value as parameter.
     * @return {@code true} if value was actually removed, {@code false} otherwise.
     * @throws GridException In case of error.
     */
    public boolean remove(String space, GridSwapByteArray key,
        @Nullable GridInClosure<GridSwapByteArray> c) throws GridException {
        assert space != null;
        assert key != null;

        try {
            boolean removed = getSpi().remove(space, key, c);

            if (removed) {
                recordEvent(EVT_SWAP_SPACE_DATA_REMOVED, space);
            }

            return removed;
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to remove from swap space [space=" + space + ", key=" + key + ']', e);
        }
    }

    /**
     * Removes value from swap.
     *
     * @param space Space name.
     * @param key Key.
     * @param c Optional closure that takes removed value and executes after actual
     *      removing. If there was no value in storage the closure is executed given
     *      {@code null} value as parameter.
     * @return {@code true} if value was actually removed, {@code false} otherwise.
     * @throws GridException In case of error.
     */
    public boolean remove(String space, Object key, @Nullable GridInClosure<GridSwapByteArray> c)
        throws GridException {
        assert space != null;
        assert key != null;

        GridSwapByteArray mKey;

        try {
            mKey = marshal(key);
        }
        catch (GridException e) {
            throw new GridException("Failed to remove from swap space [space=" + space + ", key=" + key + ']', e);
        }

        return remove(space, mKey, c);
    }

    /**
     * @param space Space name.
     * @return Swap size.
     * @throws GridException If failed.
     */
    public long swapSize(String space) throws GridException {
        assert space != null;

        try {
            return getSpi().size(space);
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to get swap size for space: " + space, e);
        }
    }

    /**
     * On session start handler.
     *
     * @param ses Task session.
     */
    public void onSessionStart(GridTaskSessionImpl ses) {
        /* No-op. */
        assert ses != null;
    }

    /**
     * On session end handler.
     *
     * @param ses Task session.
     * @param cleanup Whether cleanup or not.
     */
    public void onSessionEnd(GridTaskSessionImpl ses, boolean cleanup) {
        assert ses != null;

        String space = ses.getId().toString();

        // If on task node.
        if (ses.isTaskNode()) {
            try {
                getSpi(ses.getSwapSpaceSpi()).clear(space);

                recordEvent(EVT_SWAP_SPACE_CLEARED, space);
            }
            catch (GridSpiException e) {
                U.error(log, "Failed to clear swap space: " + space, e);
            }
        }
        // If on job node.
        else {
            if (cleanup) {
                try {
                    getSpi(ses.getSwapSpaceSpi()).clear(space);

                    recordEvent(EVT_SWAP_SPACE_CLEARED, space);
                }
                catch (GridSpiException e) {
                    U.error(log, "Failed to clear swap space: " + space, e);
                }
            }
        }
    }

    /**
     * Writes value to swap space with defined scope.
     *
     * @param ses Task session.
     * @param key Key.
     * @param val Value.
     * @param scope Scope to use.
     * @throws GridException Thrown in case of any errors.
     */
    public void write(GridTaskSession ses, Object key, @Nullable Object val, GridTaskSessionScope scope)
        throws GridException {
        assert ses != null;
        assert key != null;
        assert scope != null;

        GridSwapByteArray swapKey = marshal(key);
        GridSwapByteArray swapVal = marshal(val);

        switch (scope) {
            case GLOBAL_SCOPE: {
                write(globalSpace, swapKey, swapVal);

                break;
            }

            case SESSION_SCOPE: {
                long now = System.currentTimeMillis();

                if (now > ses.getEndTime()) {
                    U.warn(log, "Value will not be swapped due to session timeout [key=" + key +
                        ", val=" + val + ", ses=" + ses + ']');

                    return;
                }

                write(ses.getId().toString(), swapKey, swapVal);

                break;
            }

            default: {
                assert false : "Unknown swap scope: " + scope;
            }
        }
    }

    /**
     * Reads value from session if found.
     *
     * @param ses Task session.
     * @param key Key.
     * @return Found value or <tt>null</tt> if not found.
     * @throws GridException Thrown in case of any errors.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable
    public <T> T read(GridTaskSession ses, Object key) throws GridException {
        assert ses != null;
        assert key != null;

        String space = ses.getId().toString();

        GridSwapByteArray swapKey = marshal(key);
        GridSwapByteArray swapVal = read(space, swapKey);

        if (swapVal != null) {
            return (T)unmarshal(swapVal, ses.getClassLoader());
        }

        swapVal = read(globalSpace, swapKey);

        if (swapVal != null) {
            return (T)unmarshal(swapVal, ses.getClassLoader());
        }

        return null;
    }

    /**
     * Removes value from swap space.
     *
     * @param ses Task session.
     * @param key Key.
     * @throws GridException Thrown in case of any errors.
     */
    public void remove(GridTaskSession ses, Object key) throws GridException {
        assert ses != null;
        assert key != null;

        final GridSwapByteArray swapKey = marshal(key);

        remove(ses.getId().toString(), swapKey, new CIX1<GridSwapByteArray>() {
            @Override public void applyx(GridSwapByteArray removed) throws GridException {
                if (removed != null) {
                    // If value has been actually removed from session-related space
                    // then remove from global space.
                    remove(globalSpace, swapKey, null);
                }
            }
        });
    }

    /**
     *
     * @throws GridException Thrown in case of any errors.
     */
    public void clearGlobal() throws GridException {
        clear(globalSpace);
    }

    /**
     * Clears swap space for a given task session.
     * @param ses Task session.
     * @throws GridException Thrown in case of any errors.
     */
    public void clear(GridTaskSession ses) throws GridException {
        assert ses != null;

        clear(ses.getId().toString());
    }

    /**
     *
     * @param space Space name.
     * @throws GridException Thrown in case of any errors.
     */
    public void clear(String space) throws GridException {
        assert space != null;

        try {
            getSpi().clear(space);

            recordEvent(EVT_SWAP_SPACE_CLEARED, space);
        }
        catch (GridSpiException e) {
            throw new GridException("Failed to clear swap space [space=" + space + ']', e);
        }
    }

    /**
     * Writes value to swap space with global scope.
     *
     * @param key Key.
     * @param val Value.
     * @throws GridException Thrown in case of any errors.
     */
    public void writeGlobal(Object key, Object val) throws GridException {
        assert key != null;
        assert val != null;

        write(globalSpace, marshal(key), marshal(val));
    }

    /**
     * Reads value from if found.
     *
     * @param key Key.
     * @return Found value or <tt>null</tt> if not found.
     * @throws GridException Thrown in case of any errors.
     */
    @SuppressWarnings({"unchecked"})
    @Nullable
    public <T> T readGlobal(Object key) throws GridException {
        assert key != null;

        GridSwapByteArray swapVal = read(globalSpace, marshal(key));

        if (swapVal != null) {
            return (T)unmarshal(swapVal, key.getClass().getClassLoader());
        }

        return null;
    }

    /**
     * Removes value from swap space.
     *
     * @param key Key.
     * @param c Optional closure that takes removed value and executes after actual
     *      removing. If there was no value in storage the closure is executed given
     *      {@code null} value as parameter.
     * @return {@code true} if value was actually removed, {@code false} otherwise.
     * @throws GridException Thrown in case of any errors.
     */
    public boolean removeGlobal(Object key, @Nullable GridInClosure<GridSwapByteArray> c) throws GridException {
        assert key != null;

        return remove(globalSpace, marshal(key), c);
    }

    /**
     * @param swapBytes Swap bytes to unmarshal.
     * @param ldr Class loader.
     * @param <T> Unmarshalled type.
     * @return Unmarshalled value.
     * @throws GridException If failed.
     */
    @SuppressWarnings({"unchecked"})
    private <T> T unmarshal(GridSwapByteArray swapBytes, ClassLoader ldr) throws GridException {
        return (T)U.unmarshal(ctx.config().getMarshaller(), new GridByteArrayList(swapBytes.getAsEntire()), ldr);
    }

    /**
     * Marshals object.
     *
     * @param obj Object to marshal.
     * @return Marshalled array.
     * @throws GridException If failed.
     */
    private GridSwapByteArray marshal(Object obj) throws GridException {
        GridByteArrayList bytes = U.marshal(ctx.config().getMarshaller(), obj);

        return new GridSwapByteArray(bytes.getInternalArray(), 0, bytes.getSize());
    }

    /**
     * Creates and records swap space event.
     *
     * @param evtType Event type.
     * @param space Swap space name.
     */
    private void recordEvent(int evtType, @Nullable String space) {
        if (ctx.event().isRecordable(evtType)) {
            String msg = null;

            switch (evtType) {
                case EVT_SWAP_SPACE_DATA_READ: {
                    msg = "Swap space data read [space=" + space + ']';

                    break;
                }

                case EVT_SWAP_SPACE_DATA_STORED: {
                    msg = "Swap space data stored [space=" + space + ']';

                    break;
                }

                case EVT_SWAP_SPACE_DATA_REMOVED: {
                    msg = "Swap space data removed [space=" + space + ']';

                    break;
                }

                case EVT_SWAP_SPACE_CLEARED: {
                    msg = "Swap space cleared [space=" + space + ']';

                    break;
                }

                default: {
                    assert false;
                }
            }

            ctx.event().record(new GridSwapSpaceEvent(locNodeId, msg, evtType, space));
        }
    }
}
