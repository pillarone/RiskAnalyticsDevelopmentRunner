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
import java.io.*;
import java.util.*;

/**
 * TODO: add file description.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridUriDeploymentFileProcessorResult {
    /** */
    private ClassLoader clsLdr;

    /** */
    private List<Class<? extends GridTask<?, ?>>> taskClss;

    /** */
    private File file;

    /**
     * Getter for property 'clsLdr'.
     *
     * @return Value for property 'clsLdr'.
     */
    public ClassLoader getClassLoader() {
        return clsLdr;
    }

    /**
     * Setter for property 'clsLdr'.
     *
     * @param clsLdr Value to set for property 'clsLdr'.
     */
    public void setClassLoader(ClassLoader clsLdr) {
        this.clsLdr = clsLdr;
    }

    /**
     * Getter for property 'taskClss'.
     *
     * @return Value for property 'taskClss'.
     */
    public List<Class<? extends GridTask<?, ?>>> getTaskClasses() {
        return taskClss;
    }

    /**
     * Setter for property 'taskClss'.
     *
     * @param taskClss Value to set for property 'taskClss'.
     */
    public void setTaskClasses(List<Class<? extends GridTask<?, ?>>> taskClss) {
        this.taskClss = taskClss;
    }

    /**
     * Gets GAR file.
     *
     * @return GAR file.
     */
    public File getFile() {
        return file;
    }

    /**
     * Sets GAR file.
     *
     * @param file GAR file.
     */
    public void setFile(File file) {
        this.file = file;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridUriDeploymentFileProcessorResult.class, this);
    }
}
