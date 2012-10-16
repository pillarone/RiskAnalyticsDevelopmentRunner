package org.gridgain.grid.spi.communication.tcp;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.port.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.communication.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.nio.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * <tt>GridTcpCommunicationSpi</tt> is default communication SPI which uses
 * TCP/IP protocol and Java NIO to communicate with other nodes.
 * <p>
 * To enable communication with other nodes, this SPI adds {@link #ATTR_ADDR}
 * and {@link #ATTR_PORT} local node attributes (see {@link GridNode#getAttributes()}.
 * <p>
 * At startup, this SPI tries to start listening to local port specified by
 * {@link #setLocalPort(int)} method. If local port is occupied, then SPI will
 * automatically increment the port number until it can successfully bind for
 * listening. {@link #setLocalPortRange(int)} configuration parameter controls
 * maximum number of ports that SPI will try before it fails. Port range comes
 * very handy when starting multiple grid nodes on the same machine or even
 * in the same VM. In this case all nodes can be brought up without a single
 * change in configuration.
 * <p>
 * This SPI caches connections to remote nodes so it does not have to reconnect every
 * time a message is sent. By default, idle connections are kept active for
 * {@link #DFLT_IDLE_CONN_TIMEOUT} period and then are closed. Use
 * {@link #setIdleConnectionTimeout(long)} configuration parameter to configure
 * you own idle connection timeout.
 * <p>
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * The following configuration parameters are optional:
 * <ul>
 * <li>Node local IP address (see {@link #setLocalAddress(String)})</li>
 * <li>Node local port number (see {@link #setLocalPort(int)})</li>
 * <li>Local port range (see {@link #setLocalPortRange(int)}</li>
 * <li>Port resolver (see {@link #setSpiPortResolver(GridSpiPortResolver)}</li>
 * <li>Number of threads used for handling NIO messages (see {@link #setMessageThreads(int)})</li>
 * <li>Idle connection timeout (see {@link #setIdleConnectionTimeout(long)})</li>
 * <li>Direct or heap buffer allocation (see {@link #setDirectBuffer(boolean)})</li>
 * </ul>
 * <h2 class="header">Java Example</h2>
 * GridTcpCommunicationSpi is used by default and should be explicitly configured
 * only if some SPI configuration parameters need to be overridden. Examples below
 * enable encryption which is disabled by default.
 * <pre name="code" class="java">
 * GridTcpCommunicationSpi commSpi = new GridTcpCommunicationSpi();
 *
 * // Override local port.
 * commSpi.setLocalPort(4321);
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override default communication SPI.
 * cfg.setCommunicationSpi(commSpi);
 *
 * // Start grid.
 * GridFactory.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridTcpCommunicationSpi can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *         ...
 *         &lt;property name="communicationSpi"&gt;
 *             &lt;bean class="org.gridgain.grid.spi.communication.tcp.GridTcpCommunicationSpi"&gt;
 *                 &lt;!-- Override local port. --&gt;
 *                 &lt;property name="localPort" value="4321"/&gt;
 *             &lt;/bean&gt;
 *         &lt;/property&gt;
 *         ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridCommunicationSpi
 */
@SuppressWarnings({"deprecation"}) @GridSpiInfo(
    author = "GridGain Project",
    url = "www.gridgain.org",
    email = "support@gridgain.com",
    version = "3.0")
@GridSpiMultipleInstancesSupport(true)
public class GridTcpCommunicationSpi extends GridSpiAdapter implements GridCommunicationSpi,
    GridTcpCommunicationSpiMBean {
    /** Number of threads responsible for handling messages. */
    public static final int DFLT_MSG_THREADS = 5;

    /** Node attribute that is mapped to node IP address (value is <tt>comm.tcp.addr</tt>). */
    public static final String ATTR_ADDR = "comm.tcp.addr";

    /** Node attribute that is mapped to node port number (value is <tt>comm.tcp.port</tt>). */
    public static final String ATTR_PORT = "comm.tcp.port";

    /** Node attribute that is mapped to node's external ports numbers (value is <tt>comm.tcp.ext-ports</tt>). */
    public static final String ATTR_EXT_PORTS = "comm.tcp.ext-ports";

    /** Default port which node sets listener to (value is <tt>47100</tt>). */
    public static final int DFLT_PORT = 47100;

    /** Default idle connection timeout (value is <tt>30000</tt>ms). */
    public static final int DFLT_IDLE_CONN_TIMEOUT = 30000;

    /**
     * Default local port range (value is <tt>100</tt>).
     * See {@link #setLocalPortRange(int)} for details.
     */
    public static final int DFLT_PORT_RANGE = 100;

    /** Time, which SPI will wait before retry operation. */
    private static final long ERR_WAIT_TIME = 2000;

    /** */
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    @GridLoggerResource
    private GridLogger log;

    /** */
    @GridLocalNodeIdResource
    private UUID nodeId;

    /** */
    @GridMarshallerResource
    private GridMarshaller marshaller;

    /** Local IP address. */
    @GridLocalHostResource
    private String localAddr;

    /** Complex variable that represents this node IP address. */
    private volatile InetAddress localHost;

    /** Local port which node uses. */
    private int localPort = DFLT_PORT;

    /** */
    private int localPortRange = DFLT_PORT_RANGE;

    /** */
    @GridNameResource
    private String gridName;

    /** Allocate direct buffer or heap buffer. */
    private boolean directBuf = true;

    /** */
    private volatile long idleConnTimeout = DFLT_IDLE_CONN_TIMEOUT;

    /** */
    private GridNioServer nioSrvr;

    /** */
    private TcpServer tcpSrvr;

    /** Number of threads responsible for handling messages. */
    private int msgThreads = DFLT_MSG_THREADS;

    /** */
    private IdleClientWorker idleClientWorker;

    /** */
    private Map<UUID, GridNioClient> clients;

    /** SPI listener. */
    private volatile GridMessageListener lsnr;

    /** */
    private int boundTcpPort = -1;

    /** */
    private ThreadPoolExecutor nioExec;

    /** */
    private GridSpiPortResolver portRsvr;

    /** */
    private final Object mux = new Object();

    /**
     * Sets local host address for socket binding. Note that one node could have
     * additional addresses beside the loopback one. This configuration
     * parameter is optional.
     *
     * @param localAddr IP address. Default value is any available local
     *      IP address.
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalAddress(String localAddr) {
        this.localAddr = localAddr;
    }

    /** {@inheritDoc} */
    @Override public String getLocalAddress() {
        return localAddr;
    }

    /**
     * Number of threads used for handling messages received by NIO server.
     * This number usually should be no less than number of CPUs.
     * <p>
     * If not provided, default value is {@link #DFLT_MSG_THREADS}.
     *
     * @param msgThreads Number of threads.
     */
    @GridSpiConfiguration(optional = true)
    public void setMessageThreads(int msgThreads) {
        this.msgThreads = msgThreads;
    }

    /** {@inheritDoc} */
    @Override public int getMessageThreads() {
        return msgThreads;
    }

    /**
     * Sets local port for socket binding.
     * <p>
     * If not provided, default value is {@link #DFLT_PORT}.
     *
     * @param localPort Port number.
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    /** {@inheritDoc} */
    @Override public int getLocalPort() {
        return localPort;
    }

    /**
     * Sets local port range for local host ports (value must greater than or equal to <tt>0</tt>).
     * If provided local port (see {@link #setLocalPort(int)}} is occupied,
     * implementation will try to increment the port number for as long as it is less than
     * initial value plus this range.
     * <p>
     * If port range value is <tt>0</tt>, then implementation will try bind only to the port provided by
     * {@link #setLocalPort(int)} method and fail if binding to this port did not succeed.
     * <p>
     * Local port range is very useful during development when more than one grid nodes need to run
     * on the same physical machine.
     * <p>
     * If not provided, default value is {@link #DFLT_PORT_RANGE}.
     *
     * @param localPortRange New local port range.
     */
    @GridSpiConfiguration(optional = true)
    public void setLocalPortRange(int localPortRange) {
        this.localPortRange = localPortRange;
    }

    /** {@inheritDoc} */
    @Override public int getLocalPortRange() {
        return localPortRange;
    }

    /**
     * Sets port resolver for ports mapping determination.
     *
     * @param portRsvr Port resolver.
     */
    @GridSpiConfiguration(optional = true)
    public void setSpiPortResolver(GridSpiPortResolver portRsvr) {
        this.portRsvr = portRsvr;
    }

    /** {@inheritDoc} */
    @Override public GridSpiPortResolver getSpiPortResolver() {
        return portRsvr;
    }

    /**
     * Sets maximum idle connection timeout upon which a connection
     * to client will be closed.
     * <p>
     * If not provided, default value is {@link #DFLT_IDLE_CONN_TIMEOUT}.
     *
     * @param idleConnTimeout Maximum idle connection time.
     */
    @GridSpiConfiguration(optional = true)
    public void setIdleConnectionTimeout(long idleConnTimeout) {
        this.idleConnTimeout = idleConnTimeout;
    }

    /** {@inheritDoc} */
    @Override public long getIdleConnectionTimeout() {
        return idleConnTimeout;
    }

    /**
     * Sets flag to allocate direct or heap buffer in SPI.
     * If value is {@code true}, then SPI will use {@link ByteBuffer#allocateDirect(int)} call.
     * Otherwise, SPI will use {@link ByteBuffer#allocate(int)} call.
     * <p>
     * If not provided, default value is {@code true}.
     *
     * @param directBuf Flag indicates to allocate direct or heap buffer in SPI.
     */
    @GridSpiConfiguration(optional = true)
    public void setDirectBuffer(boolean directBuf) {
        this.directBuf = directBuf;
    }

    /** {@inheritDoc} */
    @Override public boolean isDirectBuffer() {
        return directBuf;
    }

    /** {@inheritDoc} */
    @Override public void setListener(GridMessageListener lsnr) {
        this.lsnr = lsnr;
    }

    /** {@inheritDoc} */
    @Override public int getNioActiveThreadCount() {
        assert nioExec != null;

        return nioExec.getActiveCount();
    }

    /** {@inheritDoc} */
    @Override public long getNioTotalCompletedTaskCount() {
        assert nioExec != null;

        return nioExec.getCompletedTaskCount();
    }

    /** {@inheritDoc} */
    @Override public int getNioCorePoolSize() {
        assert nioExec != null;

        return nioExec.getCorePoolSize();
    }

    /** {@inheritDoc} */
    @Override public int getNioLargestPoolSize() {
        assert nioExec != null;

        return nioExec.getLargestPoolSize();
    }

    /** {@inheritDoc} */
    @Override public int getNioMaximumPoolSize() {
        assert nioExec != null;

        return nioExec.getMaximumPoolSize();
    }

    /** {@inheritDoc} */
    @Override public int getNioPoolSize() {
        assert nioExec != null;

        return nioExec.getPoolSize();
    }

    /** {@inheritDoc} */
    @Override public long getNioTotalScheduledTaskCount() {
        assert nioExec != null;

        return nioExec.getTaskCount();
    }

    /** {@inheritDoc} */
    @Override public int getNioTaskQueueSize() {
        assert nioExec != null;

        return nioExec.getQueue().size();
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> getNodeAttributes() throws GridSpiException {
        assertParameter(localPort > 1023, "localPort > 1023");
        assertParameter(localPort <= 0xffff, "localPort < 0xffff");
        assertParameter(localPortRange >= 0, "localPortRange >= 0");
        assertParameter(msgThreads > 0, "msgThreads > 0");

        nioExec = new ThreadPoolExecutor(msgThreads, msgThreads, Long.MAX_VALUE, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(), new GridSpiThreadFactory(gridName, "grid-nio-msg-handler", log));

        try {
            localHost = localAddr == null || localAddr.length() == 0 ?
                U.getLocalHost() : InetAddress.getByName(localAddr);
        }
        catch (IOException e) {
            throw new GridSpiException("Failed to initialize local address: " + localAddr, e);
        }

        try {
            // This method potentially resets local port to the value
            // local node was bound to.
            nioSrvr = resetServer();
        }
        catch (GridException e) {
            throw new GridSpiException("Failed to initialize TCP server: " + localHost, e);
        }

        Collection<Integer> extPorts = null;

        if (portRsvr != null)
            try {
                extPorts = portRsvr.getExternalPorts(boundTcpPort);
            }
            catch (GridException e) {
                throw new GridSpiException("Failed to get mapped external ports for bound port: [portRsvr=" + portRsvr +
                    ", boundTcpPort=" + boundTcpPort + ']', e);
            }

        // Set local node attributes.
        return F.asMap(
            createSpiAttributeName(ATTR_ADDR), localHost,
            createSpiAttributeName(ATTR_PORT), boundTcpPort,
            createSpiAttributeName(ATTR_EXT_PORTS), extPorts);
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        assert localHost != null;

        // Start SPI start stopwatch.
        startStopwatch();

        assertParameter(idleConnTimeout > 0, "idleConnTimeout > 0");

        // Ack parameters.
        if (log.isDebugEnabled()) {
            log.debug(configInfo("localAddr", localAddr));
            log.debug(configInfo("msgThreads", msgThreads));
            log.debug(configInfo("localPort", localPort));
            log.debug(configInfo("localPortRange", localPortRange));
            log.debug(configInfo("idleConnTimeout", idleConnTimeout));
            log.debug(configInfo("directBuf", directBuf));
        }

        registerMBean(gridName, this, GridTcpCommunicationSpiMBean.class);

        synchronized (mux) {
            clients = new HashMap<UUID, GridNioClient>();
        }

        tcpSrvr = new TcpServer(nioSrvr);

        tcpSrvr.start();

        idleClientWorker = new IdleClientWorker();

        idleClientWorker.start();

        // Ack start.
        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} }*/
    @Override public void onContextInitialized(GridSpiContext spiCtx) throws GridSpiException {
        super.onContextInitialized(spiCtx);

        getSpiContext().registerPort(boundTcpPort, GridPortProtocol.TCP);
    }

    /**
     * Recreates tpcSrvr socket instance.
     *
     * @return Server socket.
     * @throws GridException Thrown if it's not possible to create tpcSrvr socket.
     */
    private GridNioServer resetServer() throws GridException {
        int maxPort = localPort + localPortRange;

        GridNioServerListener listener = new GridNioServerListener() {
            /** Cached class loader. */
            private final ClassLoader clsLdr = getClass().getClassLoader();

            /** {@inheritDoc} */
            @Override public void onMessage(byte[] data) {
                try {
                    GridTcpCommunicationMessage msg = (GridTcpCommunicationMessage)U.unmarshal(marshaller,
                        new GridByteArrayList(data, data.length), clsLdr);

                    notifyListener(msg);
                }
                catch (GridException e) {
                    U.error(log, "Failed to deserialize TCP message.", e);
                }
            }
        };

        GridNioServer srvr = null;

        // If bound TPC port was not set yet, then find first
        // available port.
        if (boundTcpPort < 0)
            for (int port = localPort; port < maxPort; port++)
                try {
                    srvr = new GridNioServer(localHost, port, listener, log, nioExec, gridName, directBuf);

                    boundTcpPort = port;

                    // Ack Port the TCP server was bound to.
                    if (log.isInfoEnabled())
                        log.info("Successfully bound to TCP port [port=" + boundTcpPort +
                            ", localHost=" + localHost + ']');

                    break;
                }
                catch (GridException e) {
                    if (port + 1 < maxPort) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to bind to local port (will try next port within range) [port=" + port +
                                ", localHost=" + localHost + ']');
                    }
                    else
                        throw new GridException("Failed to bind to any port within range [startPort=" + localPort +
                            ", portRange=" + localPortRange + ", localHost=" + localHost + ']', e);
                }
        // If bound TCP port is set, then always bind to it.
        else
            srvr = new GridNioServer(localHost, boundTcpPort, listener, log, nioExec, gridName, directBuf);

        assert srvr != null;

        return srvr;
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        U.interrupt(idleClientWorker);
        U.join(idleClientWorker, log);

        List<GridNioClient> clientsCopy = null;

        // Close all client connections.
        synchronized (mux) {
            if (clients != null)
                clientsCopy = new ArrayList<GridNioClient>(clients.values());

            clients = null;
        }

        if (clientsCopy != null)
            for (GridNioClient client : clientsCopy)
                client.close();

        // Stop TCP server.
        U.interrupt(tcpSrvr);
        U.join(tcpSrvr, log);

        unregisterMBean();

        // Stop NIO thread pool.
        U.shutdownNow(getClass(), nioExec, log);

        // Clear resources.
        tcpSrvr = null;
        nioSrvr = null;
        idleClientWorker = null;

        boundTcpPort = -1;

        // Ack stop.
        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @Override public void onContextDestroyed() {
        getSpiContext().deregisterPorts();

        super.onContextDestroyed();
    }

    /** {@inheritDoc} */
    @Override public void sendMessage(GridNode destNode, Serializable msg) throws GridSpiException {
        assert destNode != null;
        assert msg != null;

        send0(destNode, msg);
    }

    /** {@inheritDoc} */
    @Override public void sendMessage(Collection<? extends GridNode> destNodes, Serializable msg) throws
        GridSpiException {
        assert destNodes != null;
        assert msg != null;
        assert !destNodes.isEmpty();

        for (GridNode node : destNodes)
            send0(node, msg);
    }

    /**
     * Sends message to certain node. This implementation uses {@link GridMarshaller}
     * as stable and fast stream implementation.
     *
     * @param node Node message should be sent to.
     * @param msg Message that should be sent.
     * @throws GridSpiException Thrown if any socket operation fails.
     */
    private void send0(GridNode node, Serializable msg) throws GridSpiException {
        assert node != null;
        assert msg != null;

        if (log.isDebugEnabled())
            log.debug("Sending message to node [node=" + node + ", msg=" + msg + ']');

        //Shortcut for the local node
        if (node.id().equals(nodeId))
            // Call listener directly. The manager will execute this
            // callback in a different thread, so there should not be
            // a deadlock.
            notifyListener(new GridTcpCommunicationMessage(nodeId, msg));
        else
            try {
                GridNioClient client = getOrCreateClient(node);

                GridByteArrayList buf = U.marshal(marshaller, new GridTcpCommunicationMessage(nodeId, msg));

                client.sendMessage(buf.getInternalArray(), buf.getSize());
            }
            catch (GridException e) {
                throw new GridSpiException("Failed to send message to remote node: " + node, e);
            }
    }

    /**
     * Returns existing or just created client to node.
     *
     * @param node Node to which client should be open.
     * @return The existing or just created client.
     * @throws GridException Thrown if any exception occurs.
     */
    @SuppressWarnings("unchecked")
    private GridNioClient getOrCreateClient(GridNode node) throws GridException {
        assert node != null;

        GridNioClient client;

        synchronized (mux) {
            client = clients.get(node.id());

            if (client == null) {
                Collection<String> addrs = new LinkedHashSet<String>();

                // Try to connect first on bound address.
                InetAddress boundAddr = (InetAddress)node.attribute(createSpiAttributeName(ATTR_ADDR));

                if (boundAddr != null)
                    addrs.add(boundAddr.getHostAddress());

                Collection<String> addrs1;

                // Then on internal addresses.
                if ((addrs1 = node.internalAddresses()) != null)
                    addrs.addAll(addrs1);

                // And finally, try external addresses.
                if ((addrs1 = node.externalAddresses()) != null)
                    addrs.addAll(addrs1);

                if (addrs.isEmpty())
                    throw new GridException("Node doesn't have any bound, internal or external IP addresses: " + node);

                Collection<Integer> ports = new LinkedHashSet<Integer>();

                // Try to connect first on bound port.
                Integer boundPort = (Integer)node.attribute(createSpiAttributeName(ATTR_PORT));

                ports.add(boundPort);

                // Then on mapped external ports, if any.
                Collection<Integer> extPorts = (Collection<Integer>)node.attribute(
                    createSpiAttributeName(ATTR_EXT_PORTS));

                if (extPorts != null)
                    ports.addAll(extPorts);

                boolean conn = false;

                for (String addr : addrs) {
                    for (Integer port : ports)
                        try {
                            client = new GridNioClient(InetAddress.getByName(addr), port, localHost, log);

                            clients.put(node.id(), client);

                            conn = true;

                            break;
                        }
                        catch (Exception e) {
                            if (log.isDebugEnabled())
                                log.debug("Client creation failed [addr=" + addr + ", port=" + port +
                                    ", err=" + e + ']');
                        }

                    if (conn)
                        break;
                }

                // Wake up idle connection worker.
                mux.notifyAll();
            }
        }

        if (client == null)
            throw new GridSpiException("Failed to send message to the destination node. " +
                "Node does not have IP address or port set up. Check configuration and make sure " +
                "that you use the same communication SPI on all nodes. Remote node id: " + node.id());

        return client;
    }

    /**
     *
     * @param msg Communication message.
     */
    private void notifyListener(GridTcpCommunicationMessage msg) {
        GridMessageListener lsnr = this.lsnr;

        if (lsnr != null)
            // Notify listener of a new message.
            lsnr.onMessage(msg.getNodeId(), msg.getMessage());
        else if (log.isDebugEnabled())
            log.debug("Received communication message without any registered listeners (will ignore, " +
                "is node stopping?) [senderNodeId=" + msg.getNodeId() + ", msg=" + msg.getMessage() + ']');
    }

    /**
     *
     */
    private class TcpServer extends GridSpiThread {
        /** */
        private GridNioServer srvr;

        /**
         *
         * @param srvr NIO server.
         */
        TcpServer(GridNioServer srvr) {
            super(gridName, "grid-tcp-nio-srv", log);

            this.srvr = srvr;
        }

        /** {@inheritDoc} */
        @Override public void interrupt() {
            super.interrupt();

            synchronized (mux) {
                if (srvr != null)
                    srvr.close();
            }
        }

        /** {@inheritDoc} */
        @SuppressWarnings({"BusyWait"})
        @Override protected void body() throws InterruptedException {
            while (!isInterrupted())
                try {
                    GridNioServer locSrvr;

                    synchronized (mux) {
                        if (isInterrupted())
                            return;

                        locSrvr = srvr == null ? resetServer() : srvr;
                    }

                    locSrvr.accept();
                }
                catch (GridException e) {
                    if (!isInterrupted()) {
                        U.error(log, "Failed to accept remote connection (will wait for " + ERR_WAIT_TIME + "ms).", e);

                        Thread.sleep(ERR_WAIT_TIME);

                        synchronized (mux) {
                            srvr.close();

                            srvr = null;
                        }
                    }
                }
        }
    }

    /** */
    private class IdleClientWorker extends GridSpiThread {
        /** */
        IdleClientWorker() {
            super(gridName, "nio-idle-client-collector", log);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException {
            while (!isInterrupted()) {

                long nextIdleTime = System.currentTimeMillis() + idleConnTimeout;

                long now = System.currentTimeMillis();

                synchronized (mux) {
                    for (Iterator<Map.Entry<UUID, GridNioClient>> iter = clients.entrySet().iterator();
                         iter.hasNext();) {
                        Map.Entry<UUID, GridNioClient> e = iter.next();

                        GridNioClient client = e.getValue();

                        long idleTime = client.getIdleTime();

                        if (idleTime >= idleConnTimeout) {
                            if (log.isDebugEnabled())
                                log.debug("Closing idle connection to node: " + e.getKey());

                            client.close();

                            iter.remove();
                        }
                        else if (now + idleConnTimeout - idleTime < nextIdleTime)
                            nextIdleTime = now + idleConnTimeout - idleTime;
                    }

                    now = System.currentTimeMillis();

                    if (nextIdleTime - now > 0)
                        mux.wait(nextIdleTime - now);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override protected List<String> getConsistentAttributeNames() {
        List<String> attrs = new ArrayList<String>(2);

        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_CLASS));
        attrs.add(createSpiAttributeName(GridNodeAttributes.ATTR_SPI_VER));

        return attrs;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridTcpCommunicationSpi.class, this);
    }
}
