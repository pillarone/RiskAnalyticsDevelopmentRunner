// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.outbox;

import org.gridgain.grid.typedef.internal.*;
import javax.activation.*;
import java.io.*;

/**
 * This class provides implementation for {@link DataSource} based
 * on byte array data.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridMailOutboxDataSource implements DataSource {
    /** Data content type. */
    private static final String CONTENT_TYPE = "application/octet-stream";

    /** Data source name. */
    private final String name;

    /** Byte array presentation of data object. */
    private byte[] arr;

    /**
     * Create new data source based on given byte array.
     *
     * @param name Name of data source.
     * @param arr Data to get raw data.
     */
    public GridMailOutboxDataSource(String name, byte[] arr) {
        assert arr != null;
        assert name != null;

        this.name = name;
        this.arr = arr;
    }

    /** {@inheritDoc} */
    @Override public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(arr);
    }

    /** {@inheritDoc} */
    @Override public OutputStream getOutputStream() throws IOException {
        throw new IOException("Unsupported operation.");
    }

    /** {@inheritDoc} */
    @Override public String getContentType() {
        return CONTENT_TYPE;
    }

    /** {@inheritDoc} */
    @Override public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMailOutboxDataSource.class, this);
    }
}
