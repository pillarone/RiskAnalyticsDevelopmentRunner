package org.gridgain.grid.util.nio;

import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.worker.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * NIO server.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNioServer {
    /** */
    private final Selector selector;

    /** */
    private final GridNioServerListener listener;

    /** */
    private final GridLogger log;

    /** Buffer for reading. */
    private final ByteBuffer readBuf;

    /** */
    private final String gridName;

    /** */
    private final GridWorkerPool workerPool;

    /** */
    private volatile boolean closed;

    /**
     *
     * @param addr Address.
     * @param port Port.
     * @param listener Listener.
     * @param log Log.
     * @param exec Executor.
     * @param gridName Grid name.
     * @param directBuf Direct buffer flag.
     * @throws GridException If failed.
     */
    public GridNioServer(InetAddress addr, int port, GridNioServerListener listener, GridLogger log,
        Executor exec, String gridName, boolean directBuf)
        throws GridException {
        assert addr != null;
        assert port > 0 && port < 0xffff;
        assert listener != null;
        assert log != null;
        assert exec != null;

        this.listener = listener;
        this.log = log;
        this.gridName = gridName;

        workerPool = new GridWorkerPool(exec, log);

        readBuf = directBuf ? ByteBuffer.allocateDirect(8 << 10) : ByteBuffer.allocate(8 << 10);

        selector = createSelector(addr, port);
    }

    /**
     * Creates selector.
     *
     * @param addr Local address to listen on.
     * @param port Local port to listen on.
     * @return Created selector.
     * @throws GridException If selector could not be created.
     */
    private Selector createSelector(InetAddress addr, int port) throws GridException {
        Selector selector = null;

        ServerSocketChannel srvrCh = null;

        try {
            // Create a new selector
            selector = SelectorProvider.provider().openSelector();

            // Create a new non-blocking server socket channel
            srvrCh = ServerSocketChannel.open();

            srvrCh.configureBlocking(false);

            // Bind the server socket to the specified address and port
            srvrCh.socket().bind(new InetSocketAddress(addr, port));

            // Register the server socket channel, indicating an interest in
            // accepting new connections
            srvrCh.register(selector, SelectionKey.OP_ACCEPT);

            return selector;
        }
        catch (IOException e) {
            U.close(srvrCh, log);
            U.close(selector, log);

            throw new GridException("Failed to initialize NIO selector.", e);
        }
    }

    /**
     *
     */
    public void close() {
        if (!closed) {
            closed = true;

            selector.wakeup();
        }
    }

    /**
     * @throws GridException If failed.
     */
    public void accept() throws GridException {
        if (closed) {
            throw new GridException("Attempt to use closed nio server.");
        }

        try {
            while (!closed && selector.isOpen()) {
                // Wake up every 2 seconds to check if closed.
                if (selector.select(2000) > 0) {
                    // Walk through the ready keys collection and process date requests.
                    for (Iterator<SelectionKey> iter = selector.selectedKeys().iterator(); iter.hasNext();) {
                        SelectionKey key = iter.next();

                        iter.remove();

                        // Was key closed?
                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) {
                            // The key indexes into the selector so we
                            // can retrieve the socket that's ready for I/O
                            ServerSocketChannel srvrCh = (ServerSocketChannel)key.channel();

                            SocketChannel sockCh = srvrCh.accept();

                            sockCh.configureBlocking(false);

                            sockCh.register(selector, SelectionKey.OP_READ, new GridNioServerBuffer());

                            if (log.isDebugEnabled()) {
                                log.debug("Accepted new client connection: " + sockCh.socket().getRemoteSocketAddress());
                            }
                        }
                        else if (key.isReadable()) {
                            SocketChannel sockCh = (SocketChannel)key.channel();

                            SocketAddress rmtAddr = sockCh.socket().getRemoteSocketAddress();

                            try {
                                // Reset buffer to read bytes up to its capacity.
                                readBuf.clear();

                                // Attempt to read off the channel
                                int cnt = sockCh.read(readBuf);

                                if (log.isDebugEnabled()) {
                                    log.debug("Read bytes from client socket [cnt=" + cnt + ", rmtAddr=" + rmtAddr +
                                        ']');
                                }

                                if (cnt == -1) {
                                    if (log.isDebugEnabled()) {
                                        log.debug("Remote client closed connection: " + rmtAddr);
                                    }

                                    U.close(key, log);

                                    continue;
                                }
                                else if (cnt == 0) {
                                    continue;
                                }

                                // Sets limit to current position and
                                // resets position to 0.
                                readBuf.flip();

                                GridNioServerBuffer nioBuf = (GridNioServerBuffer)key.attachment();

                                // We have size let's test if we have object
                                while (readBuf.remaining() > 0) {
                                    // Always write into the buffer.
                                    nioBuf.read(readBuf);

                                    if (nioBuf.isFilled()) {
                                        if (log.isDebugEnabled()) {
                                            log.debug("Read full message from client socket: " + rmtAddr);
                                        }

                                        // Copy array so we can keep reading into the same buffer.
                                        final byte[] data = nioBuf.getMessageBytes().getArray();

                                        nioBuf.reset();

                                        workerPool.execute(new GridWorker(gridName, "grid-nio-worker", log) {
                                            @Override protected void body() {
                                                listener.onMessage(data);
                                            }
                                        });
                                    }
                                }
                            }
                            catch (IOException e) {
                                if (!closed) {
                                    U.error(log, "Failed to read data from client: " + rmtAddr, e);

                                    U.close(key, log);
                                }
                            }
                        }
                    }
                }
            }
        }
        // Ignore this exception as thread interruption is equal to 'close' call.
        catch (ClosedByInterruptException e) {
            if (log.isDebugEnabled()) {
                log.debug("Closing selector due to thread interruption: " + e.getMessage());
            }
        }
        catch (ClosedSelectorException e) {
            throw new GridException("Selector got closed while active.", e);
        }
        catch (IOException e) {
            throw new GridException("Failed to accept or read data.", e);
        }
        finally {
            closed = true;

            if (selector.isOpen()) {
                if (log.isDebugEnabled()) {
                    log.debug("Closing all client sockets.");
                }

                workerPool.join(true);

                // Close all channels registered with selector.
                for (SelectionKey key : selector.keys()) {
                    U.close(key.channel(), log);
                }

                if (log.isDebugEnabled()) {
                    log.debug("Closing NIO selector.");
                }

                U.close(selector, log);
            }
        }
    }
}

