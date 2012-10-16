// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.inbox;

import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.util.mail.*;
import java.io.*;

/**
 * This class represents mail attachment. Attachment should use {@link Serializable}
 * object or file content.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public interface GridMailInboxAttachment {
    /**
     * Gets object that belongs to attachment. If attachment is based on file content method
     * returns {@code null}.
     *
     * @param marshaller Marshaller to unmarshal an object.
     * @return Object in attachment or {@code null}.
     * @throws GridMailException Thrown in case of any error.
     */
    public Object getContent(GridMarshaller marshaller) throws GridMailException;

    /**
     * Tests whether an attachment's mime type equals to given MIME type.
     *
     * @param mimeType Mime type to check.
     * @return {@code true} if equals, {@code false} otherwise.
     * @throws GridMailException Thrown in case of any error.
     */
    boolean isMimeType(String mimeType) throws GridMailException;

    /**
     * Gets file name in attachment.
     *
     * @return File name if file was attached, otherwise {@code null}.
     * @throws GridMailException Thrown in case of any error.
     */
    public String getFileName() throws GridMailException;

    /**
     * Save attachment on disk.
     *
     * @param file File path where file will be saved.
     * @throws GridMailException Thrown in case of any error.
     */
    public void saveToFile(File file) throws GridMailException;
}
