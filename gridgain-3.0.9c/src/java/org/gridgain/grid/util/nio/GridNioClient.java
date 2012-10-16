package org.gridgain.grid.util.nio;

import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.lang.utils.*;
import java.io.*;
import java.net.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridNioClient {
    /** */
    private final Socket sock;

    /** Cached byte buffer. */
    private final GridByteArrayList bytes = new GridByteArrayList(512);

    /** Grid logger. */
    private final GridLogger log;

    /** Time when this client was last used. */
    private long lastUsed = System.currentTimeMillis();

    /**
     *
     * @param addr TODO
     * @param port TODO
     * @param localHost TODO
     * @param log TODO
     * @throws GridException TODO
     */
    public GridNioClient(InetAddress addr, int port, InetAddress localHost, GridLogger log) throws GridException {
        assert addr != null;
        assert port > 0 && port < 0xffff;
        assert localHost != null;
        assert log != null;

        try {
            sock = new Socket(addr, port, localHost, 0);
        }
        catch (IOException e) {
            throw new GridException("Failed to connect to remote host [addr=" + addr + ", port=" + port +
                ", localHost=" + localHost + ']', e);
        }

        this.log = log;
    }

    /** */
    public synchronized void close() {
        if (log.isDebugEnabled()) {
            log.debug("Closing client: " + this);
        }

        U.close(sock, log);
    }

    /**
     * Gets idle time of this client.
     *
     * @return Idle time of this client.
     */
    public synchronized long getIdleTime() {
        return System.currentTimeMillis() - lastUsed;
    }

    /**
     *
     * @param data Data to send.
     * @param len Size of data in bytes.
     * @throws GridException TODO
     */
    public synchronized void sendMessage(byte[] data, int len) throws GridException {
        lastUsed = System.currentTimeMillis();

        bytes.reset();

        // Allocate 4 bytes for size.
        bytes.add(len);

        bytes.add(data, 0, len);

        try {
            // We assume that this call does not return until the message
            // is fully sent.
            sock.getOutputStream().write(bytes.getInternalArray(), 0, bytes.getSize());
        }
        catch (IOException e) {
            throw new GridException("Failed to send message to remote node: " + sock.getRemoteSocketAddress(), e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNioClient.class, this);
    }
}

