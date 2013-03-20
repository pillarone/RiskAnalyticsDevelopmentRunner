// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.marshaller.jdk;

import org.gridgain.grid.marshaller.*;
import org.jetbrains.annotations.*;

import java.io.*;

/**
 * This class defines own object output stream.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJdkMarshallerObjectOutputStream extends ObjectOutputStream {
    /**
     * @param out Output stream.
     * @throws IOException Thrown in case of any I/O errors.
     */
    GridJdkMarshallerObjectOutputStream(OutputStream out) throws IOException {
        super(out);

        enableReplaceObject(true);
    }

    /** {@inheritDoc} */
    @Nullable @Override protected Object replaceObject(Object o) throws IOException {
        return o == null || GridMarshallerController.isExcluded(o.getClass()) ? null :
            o.getClass().equals(Object.class) ? new GridJdkMarshallerDummySerializable() : super.replaceObject(o);
    }
}

