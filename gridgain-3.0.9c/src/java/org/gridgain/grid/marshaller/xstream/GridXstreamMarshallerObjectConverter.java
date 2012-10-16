// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.marshaller.xstream;

import com.thoughtworks.xstream.converters.*;
import com.thoughtworks.xstream.io.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridXstreamMarshallerObjectConverter implements Converter {
    /** {@inheritDoc} */
    @Override public boolean canConvert(Class cls) {
        return cls.equals(Object.class);
    }

    /** {@inheritDoc} */
    @Override public void marshal(Object val, HierarchicalStreamWriter writer, MarshallingContext ctx) {
        writer.startNode(Object.class.getName());
        writer.endNode();
    }

    /** {@inheritDoc} */
    @Override public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext ctx) {
        return new Object();
    }
}
