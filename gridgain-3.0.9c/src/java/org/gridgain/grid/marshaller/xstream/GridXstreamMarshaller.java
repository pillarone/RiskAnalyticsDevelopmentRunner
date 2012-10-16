// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.marshaller.xstream;

import com.thoughtworks.xstream.*;
import org.gridgain.grid.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import java.io.*;

/**
 * Marshaller that uses <a href="http://xstream.codehaus.org/">XStream</a>
 * to marshal objects. This marshaller does not require objects to implement
 * {@link Serializable}.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridXstreamMarshaller implements GridMarshaller {
    /** XStream instance to use with system class loader. */
    @GridToStringExclude
    private final XStream dfltXs;

    /**
     * Initializes {@code XStream} marshaller.
     */
    public GridXstreamMarshaller() {
        dfltXs = createXstream(getClass().getClassLoader());
    }

    /**
     * @param ldr Class loader for created XStream object.
     * @return created Xstream object.
     */
    private XStream createXstream(ClassLoader ldr) {
        XStream xs = new XStream();

        xs.registerConverter(new GridXstreamMarshallerExternalizableConverter(xs.getMapper()));
        xs.registerConverter(new GridXstreamMarshallerResourceConverter());
        xs.registerConverter(new GridXstreamMarshallerObjectConverter());

        xs.setClassLoader(ldr);

        return xs;
    }

    /** {@inheritDoc} */
    @Override public void marshal(Object obj, OutputStream out) throws GridException {
        dfltXs.toXML(obj, out);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <T> T unmarshal(InputStream in, ClassLoader clsLdr) throws GridException {
        if (getClass().getClassLoader().equals(clsLdr)) {
            return (T)dfltXs.fromXML(in);
        }

        return (T)createXstream(clsLdr).fromXML(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridXstreamMarshaller.class, this);
    }
}