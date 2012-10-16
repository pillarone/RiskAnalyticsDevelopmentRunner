// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.tools.portscanner;

import java.io.*;
import java.net.*;
import java.nio.channels.*;

/**
 * GridGain port scanner.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridPortScanner {
    /** Minimum port number */
    private static final int MIN_PORT = 49112;

    /** Maximum port number */
    private static final int MAX_PORT = 65535;

    /**
     * Private constructor
     */
    private GridPortScanner() {
        // No-op.
    }

    /**
     * Makes a search of available port. Start port is taken from temp file, it is
     * then replaced with newly found port.
     *
     * @param args Program arguments.
     * @throws IOException In case of error while reading or writing to file.
     */
    public static void main(String[] args) throws IOException {
        int port;

        RandomAccessFile file = null;
        FileLock lock = null;

        try {
            file = new RandomAccessFile(new File(System.getProperty("java.io.tmpdir"), "gridgain.lastport.tmp"),
                "rw");

            lock = file.getChannel().lock();

            file.seek(0);

            String startPortStr = file.readLine();

            int startPort;

            if (startPortStr != null && startPortStr.length() > 0) {
                startPort = Integer.valueOf(startPortStr) + 1;

                if (startPort > MAX_PORT)
                    startPort = MIN_PORT;
            } else
                startPort = MIN_PORT;

            port = findPort(startPort);

            file.setLength(0);

            file.writeBytes(String.valueOf(port));
        }
        finally {
            if (lock != null)
                lock.release();

            if (file != null)
                file.close();
        }

        // Ack the port for others to read...
        System.out.println(port);
    }

    /**
     * Finds first available port beginning from start port.
     *
     * @param startPort Start Port number.
     * @return Available port number.
     */
    private static int findPort(int startPort) {
        int port = startPort;

        while (true) {
            if (isAvailable(port))
                return port;

            port++;
        }
    }

    /**
     * Checks whether port is available.
     *
     * @param port Port number.
     * @return {@code true} if port is available
     */
    private static boolean isAvailable(int port) {
        ServerSocket sock = null;

        try {
            sock = new ServerSocket(port);

            return true;
        }
        catch (IOException ignored) {
            return false;
        }
        finally {
            if (sock != null)
                try {
                    sock.close();
                }
                catch (IOException ignored) {
                    // No-op
                }
        }
    }
}
