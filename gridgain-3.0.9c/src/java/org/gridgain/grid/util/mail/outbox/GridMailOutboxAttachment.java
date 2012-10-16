// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.outbox;

import org.gridgain.grid.typedef.internal.*;
import java.io.*;

/**
 * This class represents mail attachment.
 * Attachment should use {@link Serializable} object or file content.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridMailOutboxAttachment {
    /** Attachment data (serialized object). */
    private final byte[] data;

    /** Attachment name. */
    private final String name;

    /** Attachment file (if any). */
    private final File file;

    /** Attachment index. */
    private final int idx;

    /**
     * Creates attachment based on serialized object.
     *
     * @param data Data (object) placed in attachment.
     * @param name Attachment name in mail.
     * @param idx Index in the list of mail attachments.
     */
    GridMailOutboxAttachment(byte[] data, String name, int idx) {
        assert data != null;
        assert name != null;
        assert idx >= 0;

        this.data = data;
        this.name = name;
        this.idx = idx;

        file = null;
    }

    /**
     * Creates attachment based on file content.
     *
     * @param file File placed in attachment.
     * @param idx Index in the list of mail attachments.
     */
    GridMailOutboxAttachment(File file, int idx) {
        this.file = file;
        this.idx = idx;

        data = null;
        name = null;
    }

    /**
     * Tests whether this attachment is based on file content.
     *
     * @return {@code true} if attachment based on file content.
     */
    public boolean isFileAttachment() {
        return file != null;
    }

    /**
     * Returns the byte array of serialized object that belongs to this
     * attachment. If attachment is based on file content this method
     * returns {@code null}.
     *
     * @return Byte array in attachment or {@code null}.
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Returns the attachment name.
     *
     * @return Attachment name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the attachment file. If attachment is based on object data this
     * method returns {@code null}.
     *
     * @return File in attachment or {@code null}.
     */
    public File getFile() {
        return file;
    }

    /**
     * Returns the attachment index.
     *
     * @return Index number.
     */
    public int getIndex() {
        return idx;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMailOutboxAttachment.class, this);
    }
}
