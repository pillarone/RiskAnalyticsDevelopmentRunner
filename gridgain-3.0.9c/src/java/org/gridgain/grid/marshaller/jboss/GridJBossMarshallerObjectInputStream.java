// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.marshaller.jboss;

import org.jboss.serial.io.*;
import java.io.*;

/**
 * Custom JBoss object input stream.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJBossMarshallerObjectInputStream extends JBossObjectInputStream {
    /**
     * @param in Input stream. 
     * @param ldr Class loader.
     * @throws IOException If stream creation failed.
     */
    GridJBossMarshallerObjectInputStream(InputStream in, ClassLoader ldr) throws IOException {
        super(in, ldr);

        enableResolveObject(true);
    }

    /** {@inheritDoc} */
    @Override protected Object resolveObject(Object o) throws IOException {
        return super.resolveObject(o);
    }

    /**
     * Hack to resolve JBoss bug of {@link #resolveObject(Object)} not called.
     * This way we make sure that it will always be called.
     * <p>
     * For more information see <a href="https://jira.jboss.org/browse/JBSER-103">JBSER-103</a>.
     *
     * @return Resolved object.
     * @throws IOException If read failed.
     * @throws ClassNotFoundException If class not found.
     */
    @Override public Object readObjectUsingDataContainer() throws IOException, ClassNotFoundException {
        return resolveObject(super.readObjectUsingDataContainer());
    }

    /**
     * Hack to resolve JBoss bug of {@link #resolveObject(Object)} not called.
     * This way we make sure that it will always be called.
     * <p>
     * For more information see <a href="https://jira.jboss.org/browse/JBSER-103">JBSER-103</a>.
     *
     * @return Resolved object.
     * @throws IOException If read failed.
     * @throws ClassNotFoundException If class not found.
     */
    @Override public Object readObjectOverride() throws IOException, ClassNotFoundException {
        return resolveObject(super.readObjectOverride());
    }
}
