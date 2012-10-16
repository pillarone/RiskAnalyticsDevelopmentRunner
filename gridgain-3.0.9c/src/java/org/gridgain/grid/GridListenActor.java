// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;
import java.util.*;

/**
 * Actor-base adapter for {@link Grid#listen(GridPredicate2[])}
 * method. Look at <tt>GridFunctionPingPong.java</tt> example class for usage sample.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public abstract class GridListenActor<T> extends GridPredicate2<UUID, T> {
    /** */
    private GridLogger log;

    /** */
    private boolean keepGoing = true;

    /** */
    private UUID nodeId;

    /** */
    private Grid grid;

    /** */
    private Collection<GridRichNode> nodes;

    /**
     * Internal method that is called by GridGain runtime to set local context.
     * This method is called once and before method {@link #receive(UUID, Object)} is
     * called for the first time.
     * <p>
     * Note that this method is only public due to fucked up visibility support in Java...
     *
     * @param grid Local grid instance this actor belongs to.
     * @param nodes Collection of nodes this actor is listening to.
     */
    public final void setContext(Grid grid, Collection<GridRichNode> nodes) {
        assert grid != null;
        assert !F.isEmpty(nodes);

        this.grid = grid;
        this.nodes = nodes;

        log = grid.log().getLogger(GridListenActor.class);
    }

    /**
     * Gets grid instance associated with this actor.
     *
     * @return Grid instance associated with this actor.
     */
    protected final Grid grid() {
        assert grid != null;

        return grid;
    }

    /**
     * Gets collection of nodes this actor is listening on.
     *
     * @return Collection of nodes this actor is listening on. 
     */
    protected final Collection<GridRichNode> nodes() {
        assert nodes != null;

        return nodes;
    }

    /** {@inheritDoc} */
    @Override public final boolean apply(UUID nodeId, T rcvMsg) {
        assert nodeId != null;
        assert rcvMsg != null;

        if (!keepGoing)
            return false;

        this.nodeId = nodeId;

        try {
            receive(nodeId, rcvMsg);
        }
        catch (Throwable e) {
            onError(e);
        }

        return keepGoing;
    }

    /**
     * This method is called in case when method {@link #receive(UUID, Object)} threw an exception.
     * Insides of this method the implementation should call any of the {@code respond}, {@code stop}
     * or {@code skip} methods. If overriding method does nothing - than return value of method
     * {@link #receive(UUID, Object)} is undefined.
     * <p>
     * Default implementation simply calls method {@link #stop()}.
     *
     * @param e Exception thrown from method {@link #receive(UUID, Object)}.
     */
    protected void onError(Throwable e) {
        U.error(log, "Listener operation failed.", e);

        stop();
    }

    /**
     * This method receives the message. This is the only method that subclass needs to override.
     * Insides of this method the implementation should call any of the {@code respond}, {@code stop}
     * or {@code skip} methods. Note that if none of these methods are called - listener will continue
     * listen for the new messages.
     * <p>
     * Note that like all predicate in {@link GridProjection#listen(GridPredicate2[])} method
     * this method is called in synchronized context so that only thread can access it at a time. 
     *
     * @param nodeId ID of the sender node.
     * @param recvMsg Received message.
     * @throws Throwable Thrown in case of any errors. Method {@link #onError(Throwable)}} will
     *      be called right before returning from this method. 
     */
    protected abstract void receive(UUID nodeId, T recvMsg) throws Throwable;

    /**
     * This method instructs underlying implementation to stop receiving new messages and unregister
     * the message listener.
     * <p>
     * Note that subclasses can call any of {@code respond}, {@code stop} or {@code skip} methods any
     * number of times. Only the last executed method will determine whether or not the implementation will 
     * continue listen for the new messages.
     */
    protected final void stop() {
        keepGoing = false;
    }

    /**
     * This method sends the response message to the original sender node and instructs underlying
     * implementation to stop receiving new messages and unregister the message listener.
     * <p>
     * Note that subclasses can call any of {@code respond}, {@code stop} or {@code skip} methods any
     * number of times. Only the last executed method will determine whether or not the implementation will
     * continue listen for the new messages.
     *
     * @param respMsg Optional response message. If not {@code null} - it will be sent to the original
     *      sender node.
     * @throws GridException Thrown in case of any errors.
     */
    protected final void stop(@Nullable Object respMsg) throws GridException {
        keepGoing = false;

        send(nodeId, respMsg);
    }

    /**
     * Skips current message and continues to listen for new message. This method simply calls
     * {@code respond(null)}.
     * <p>
     * Note that subclasses can call any of {@code respond}, {@code stop} or {@code skip} methods any
     * number of times. Only the last executed method will determine whether or not the implementation will
     * continue listen for the new messages.
     */
    protected final void skip() {
        checkReversing();

        keepGoing = true;
    }

    /**
     * Responds to the original sender node with given message and continues to listen for the new messages.
     * <p>
     * Note that subclasses can call any of {@code respond}, {@code stop} or {@code skip} methods any
     * number of times. Only the last executed method will determine whether or not the implementation will
     * continue listen for the new messages.
     *
     * @param respMsg Optional response message. If not {@code null} - it will be sent to the original
     *      sender node.
     * @throws GridException Thrown in case of any errors.
     */
    protected final void respond(@Nullable Object respMsg) throws GridException {
        checkReversing();

        keepGoing = true;

        send(nodeId, respMsg);
    }

    /**
     * Responds to the provided node with given message and continues to listen for the new messages.
     * <p>
     * Note that subclasses can call any of {@code respond}, {@code stop} or {@code skip} methods any
     * number of times. Only the last executed method will determine whether or not the implementation will
     * continue listen for the new messages.
     *
     * @param id ID of the node to send the message to, if any.
     * @param respMsg Optional response message. If not {@code null} - it will be sent to the original
     *      sender node.
     * @throws GridException Thrown in case of any errors.
     */
    protected final void respond(UUID id, @Nullable Object respMsg) throws GridException {
        checkReversing();

        keepGoing = true;

        send(id, respMsg);
    }

    /**
     * Checks reversing.
     */
    private void checkReversing() {
        if (!keepGoing)
            U.warn(log, "Suspect logic - reversing listener return status (was 'true', then 'false', " +
                "now 'true' again).");
    }

    /**
     * Sends optional message. If message is {@code null} - it's no-op.
     *
     * @param nodeId ID of the node to send message to.
     * @param respMsg Message to send.
     * @throws GridException Thrown in case of any errors.
     */
    private void send(UUID nodeId, @Nullable Object respMsg) throws GridException {
        assert nodeId != null;

        if (respMsg != null) {
            GridRichNode node = grid.node(nodeId);

            if (node != null)
                node.send(respMsg); // Can still fail.
            else
                throw new GridException("Failed to send message since destination node has " +
                    "left topology (ignoring) [nodeId=" +nodeId + ", respMsg=" + respMsg + ']');
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridListenActor.class, this);
    }
}
