// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.mail.inbox;

import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.mail.*;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;

/**
 * This is an adapter for inbox attachment.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridMailInboxAttachmentAdapter implements GridMailInboxAttachment {
    /** Mail body part. */
    private final Part part;

    /** Grid logger. */
    private final GridLogger log;

    /**
     * Creates new attachment adapter with given parameters.
     *
     * @param part Mail body part.
     * @param log Grid logger.
     */
    public GridMailInboxAttachmentAdapter(Part part, GridLogger log) {
        assert part != null;
        assert log != null;

        this.part = part;
        this.log = log.getLogger(getClass());
    }

    /** {@inheritDoc} */
    @Override public Object getContent(GridMarshaller marshaller) throws GridMailException {
        assert marshaller != null;

        InputStream in = null;

        try {
            in = part.getInputStream();

            return U.unmarshal(marshaller, in, getClass().getClassLoader());
        }
        catch (IOException e) {
            throw new GridMailException("Error when getting attachment content.", e);
        }
        catch (MessagingException e) {
            throw new GridMailException("Error when getting attachment content.", e);
        }
        catch (GridException e) {
            throw new GridMailException("Error when getting attachment content.", e);
        }
        finally {
            U.close(in, log);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isMimeType(String mimeType) throws GridMailException {
        try {
            return part.isMimeType(mimeType);
        }
        catch (MessagingException e) {
            throw new GridMailException("Error when getting attachment mime type.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public String getFileName() throws GridMailException {
        try {
            return part.getFileName();
        }
        catch (MessagingException e) {
            throw new GridMailException("Error when getting attachment file name.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public void saveToFile(File file) throws GridMailException {
        assert file != null;

        if (part instanceof MimeBodyPart == false) {
            throw new GridMailException("Only MIME messages can be saved.");
        }

        try {
            ((MimeBodyPart)part).saveFile(file);
        }
        catch (MessagingException e) {
            throw new GridMailException("Error when saving attachment file: " + file, e);
        }
        catch (IOException e) {
            throw new GridMailException("Error when saving attachment file: " + file, e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMailInboxAttachmentAdapter.class, this);
    }
}
