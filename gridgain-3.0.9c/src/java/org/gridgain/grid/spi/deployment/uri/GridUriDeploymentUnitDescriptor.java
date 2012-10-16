// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.deployment.uri;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.spi.deployment.uri.GridUriDeploymentUnitDescriptor.Type.*;

/**
 * Container for information about tasks and file where classes placed. It also contains tasks instances.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridUriDeploymentUnitDescriptor {
    /**
     * Container type.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     */
    @SuppressWarnings({"PackageVisibleInnerClass"}) enum Type {
        /**
         * Container has reference to the file with tasks.
         */
        FILE,

        /**
         * Container keeps tasks deployed directly.
         */
        CLASS
    }

    /**
     * Container type.
     */
    private final Type type;

    /**
     * If type is {@link Type#FILE} contains URI of {@link #file} otherwise must be null.
     */
    @GridToStringExclude
    private final String uri;

    /**
     * If type is {@link Type#FILE} contains file with tasks otherwise must be null.
     */
    private final File file;

    /**
     * Tasks deployment timestamp.
     */
    private final long tstamp;

    /** */
    private final ClassLoader clsLdr;

    /** Map of all resources. */
    private final Map<String, String> rsrcs = new HashMap<String, String>();

    /**
     * Constructs descriptor for GAR file.
     *
     * @param uri GAR file URI.
     * @param file File itself.
     * @param tstamp Tasks deployment timestamp.
     * @param clsLdr Class loader.
     */
    GridUriDeploymentUnitDescriptor(String uri, File file, long tstamp, ClassLoader clsLdr) {
        assert uri != null;
        assert file != null;
        assert tstamp > 0;

        this.uri = uri;
        this.file = file;
        this.tstamp = tstamp;
        this.clsLdr = clsLdr;
        type = FILE;
    }

    /**
     * Constructs deployment unit descriptor based on timestamp and {@link GridTask} instances.
     *
     * @param tstamp Tasks deployment timestamp.
     * @param clsLdr Class loader.
     */
    GridUriDeploymentUnitDescriptor(long tstamp, ClassLoader clsLdr) {
        assert clsLdr != null;
        assert tstamp > 0;

        this.tstamp = tstamp;
        this.clsLdr = clsLdr;
        uri = null;
        file = null;
        type = CLASS;
    }

    /**
     * Gets descriptor type.
     *
     * @return Descriptor type.
     */
    public Type getType() {
        return type;
    }

    /**
     * Gets file URL.
     *
     * @return {@code null} it tasks were deployed directly and reference to the GAR file URI if tasks were deployed
     *         from the file.
     */
    public String getUri() {
        return uri;
    }

    /**
     * Tasks GAR file.
     *
     * @return {@code null} if tasks were deployed directly and GAR file if tasks were deployed from it.
     */
    public File getFile() {
        return file;
    }

    /**
     * Gets tasks deployment timestamp.
     *
     * @return Tasks deployment timestamp.
     */
    public long getTimestamp() {
        return tstamp;
    }

    /**
     * Deployed task.
     *
     * @return Deployed task.
     */
    public ClassLoader getClassLoader() {
        return clsLdr;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return clsLdr.hashCode();
    }

    /**
     * Getter for property 'rsrcs'.
     *
     * @return Value for property 'rsrcs'.
     */
    public Map<String, String> getResources() {
        return rsrcs;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        return obj instanceof GridUriDeploymentUnitDescriptor &&
            clsLdr.equals(((GridUriDeploymentUnitDescriptor)obj).clsLdr);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridUriDeploymentUnitDescriptor.class, this,
            "uri", U.hidePassword(uri));
    }
}
