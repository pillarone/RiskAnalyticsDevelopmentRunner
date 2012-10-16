// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.communication.*;
import org.gridgain.grid.spi.discovery.*;
import org.jetbrains.annotations.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * SPI context provides common functionality for all SPI implementations.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridSpiContext2 {
    /**
     * Gets a collection of remote grid nodes. Remote nodes are discovered via underlying
     * {@link GridDiscoverySpi} implementation used. Unlike {@link #nodes(GridPredicate[])},
     * this method does not include local grid node.
     *
     * @param p Optional filtering predicate. If none provided - all remote nodes will
     *      be returned.
     * @return Collection of remote grid nodes.
     */
    public Collection<GridNode> remoteNodes(@Nullable GridPredicate<GridNode>... p);

    /**
     * Gets a collection of all grid nodes. Remote nodes are discovered via underlying
     * {@link GridDiscoverySpi} implementation used. Unlike {@link #remoteNodes(GridPredicate[])},
     * this method does include local grid node.
     *
     * @param p Optional filtering predicate. If none provided - all remote nodes will
     *      be returned.
     * @return Collection of remote grid nodes.
     */
    public Collection<GridNode> nodes(@Nullable GridPredicate<GridNode>... p);

    /**
     * Gets local grid node. Instance of local node is provided by underlying {@link GridDiscoverySpi}
     * implementation used.
     *
     * @return Local grid node.
     * @see GridDiscoverySpi
     */
    public GridNode localNode();

    /**
     * Gets a node instance based on its ID.
     *
     * @param nodeId ID of a node to get.
     * @return Node for a given ID or {@code null} is such not has not been discovered.
     * @see GridDiscoverySpi
     */
    public GridNode node(UUID nodeId);

    /**
     * Pings a remote node. The underlying communication is provided via
     * {@link GridDiscoverySpi#pingNode(UUID)} implementation.
     * <p>
     * Discovery SPIs usually have some latency in discovering failed nodes. Hence,
     * communication to remote nodes may fail at times if an attempt was made to
     * establish communication with a failed node. This method can be used to check
     * if communication has failed due to node failure or due to some other reason.
     *
     * @param nodeId ID of a node to ping.
     * @return {@code true} if node for a given ID is alive, {@code false} otherwise.
     * @see GridDiscoverySpi
     */
    public boolean pingNode(UUID nodeId);

    /**
     *
     * @param c
     * @param sys
     * @param <R>
     * @return TODO
     * @throws GridException
     */
    public <R> GridFuture<R> callLocal(Callable<R> c, boolean sys) throws GridException;

    /**
     *
     * @param c
     * @param sys
     * @return TODO
     * @throws GridException
     */
    public GridFuture<?> runLocal(Runnable c, boolean sys) throws GridException;

    /**
     * Sends a message to a remote node. The underlying communication mechanism is defined by
     * {@link GridCommunicationSpi} implementation used.
     *
     * @param node Node to send a message to.
     * @param msg Message to send.
     * @param topic Topic to send message to.
     * @throws GridSpiException If failed to send a message to remote node.
     */
    @Deprecated
    public void send(GridNode node, Object msg, String topic) throws GridSpiException;

    /**
     * Sends given message to the nodes in this monad.
     *
     * @param msg Message to send.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If no predicates provided - all nodes in this monad will be used.
     * @throws GridException If failed to send a message to any of the nodes.
     */
    public void send(Object msg, @Nullable GridPredicate<GridRichNode>... p) throws GridException;

    /**
     * Sends given messages to the nodes in this monad.
     *
     * @param msgs Messages to send. Order of the sending is undefined. If the method produces
     *      the exception none or some messages could have been sent already.
     * @param p Optional set of filtering predicates. All predicates must evaluate to {@code true} for a
     *      node to be included. If no predicates provided - all nodes in this monad will be used.
     * @throws GridException If failed to send a message to any of the nodes.
     */
    public void send(Iterable<?> msgs, @Nullable GridPredicate<GridRichNode>... p) throws GridException;

    /**
     * Sends a message to a group of remote nodes. The underlying communication mechanism is defined by
     * {@link GridCommunicationSpi} implementation used.
     *
     * @param nodes Group of nodes to send a message to.
     * @param msg Message to send.
     * @param topic Topic to send message to.
     * @throws GridSpiException If failed to send a message to any of the remote nodes.
     */
    @Deprecated
    public void send(Collection<? extends GridNode> nodes, Object msg, String topic)
        throws GridSpiException;

    /**
     * Register a message listener to receive messages sent by remote nodes. The underlying
     * communication mechanism is defined by {@link GridCommunicationSpi} implementation used.
     *
     * @param lsnr Message listener to register.
     * @param topic Topic to register listener for.
     * @param p Optional filtering predicate. If none provided - all remote nodes will
     *      be returned.
     */
    public void addMessageListener(GridMessageListener lsnr, String topic, GridPredicate<Object>... p);

    /**
     * Removes a previously registered message listener.
     *
     * @param lsnr Message listener to remove.
     * @param topic Topic to unregister listener for.
     * @return {@code true} of message listener was removed, {@code false} if it was not
     *      previously registered.
     */
    public boolean removeMessageListener(GridMessageListener lsnr, String topic);

    /**
     * Adds an event listener for local events.
     *
     * @param lsnr Event listener for local events.
     * @param types Optional types for which this listener will be notified. If no types are provided
     *      this listener will be notified for all local events.
     * @see GridEvent
     */
    public void addLocalEventListener(GridLocalEventListener lsnr, int... types);

    /**
     *
     * @param p
     */
    public void listenAsync(GridPredicate2<UUID, Object> p);

    /**
     * Removes local event listener.
     *
     * @param lsnr Local event listener to remove.
     * @return {@code true} if listener was removed, {@code false} otherwise.
     */
    public boolean removeLocalEventListener(GridLocalEventListener lsnr);

    /**
     * Obtain grid node topology for a given task.
     *
     * @param taskSes Task session.
     * @param grid Available grid nodes.
     * @return Topology for given task session.
     * @throws GridSpiException If failed to get topology.
     */
    public Collection<? extends GridNode> topology(GridTaskSession taskSes, Collection<? extends GridNode> grid)
        throws GridSpiException;

    /**
     * Records local event.
     *
     * @param evt Local grid event to record.
     */
    public void recordEvent(GridEvent evt);

    /**
     * Registers open port.
     *
     * @param port Port.
     * @param protocol Protocol.
     */
    public void registerPort(int port, int protocol);

    /**
     * Deregisters closed port.
     *
     * @param port Port.
     * @param protocol Protocol.
     */
    public void unregisterPort(int port, int protocol);

    /**
     * Gets object from cache.
     *
     * @param cacheName Cache name.
     * @param key Object key.
     * @return Cached object.
     * @throws GridException Thrown if any exception occurs.
     */
    public <K, V> V get(String cacheName, K key) throws GridException;

    /**
     * Puts object in cache.
     *
     * @param cacheName Cache name.
     * @param key Object key.
     * @param val Cached object.
     * @param ttl Time to live, {@code 0} means the entry will never expire.
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Previous value associated with specified key, possibly {@code null}.
     * @throws GridException Thrown if any exception occurs.
     */
    public <K, V> V put(String cacheName, K key, V val, long ttl) throws GridException;

    /**
     * Puts object into cache if there was no previous object associated with
     * given key.
     *
     * @param cacheName Cache name.
     * @param key Cache key.
     * @param val Cache value.
     * @param ttl Time to live.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return Either existing value or {@code null} if there was no value for given key.
     * @throws GridException If put failed.
     */
    public <K, V> V putIfAbsent(String cacheName, K key, V val, long ttl) throws GridException;

    /**
     * Removes object from cache.
     *
     * @param cacheName Cache name.
     * @param key Object key.
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Previous value associated with specified key, possibly {@code null}.
     * @throws GridException Thrown if any exception occurs.
     */
    public <K, V> V remove(String cacheName, K key) throws GridException;

    /**
     * Returns {@code true} if this cache contains a mapping for the specified key.
     *
     * @param cacheName Cache name.
     * @param key Object key.
     * @param <K> Key type.
     * @return {@code true} if this cache contains a mapping for the specified key.
     */
    public <K> boolean containsKey(String cacheName, K key);
}
