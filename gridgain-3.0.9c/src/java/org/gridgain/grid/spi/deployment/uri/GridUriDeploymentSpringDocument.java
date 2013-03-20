// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.deployment.uri;

import org.gridgain.grid.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.typedef.internal.*;
import org.springframework.beans.*;
import org.springframework.beans.factory.xml.*;
import java.util.*;

/**
 * Helper class which helps to read deployer and tasks information from
 * {@code Spring} configuration file.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridUriDeploymentSpringDocument {
    /** Initialized springs beans factory. */
    private final XmlBeanFactory factory;

    /** List of tasks from GAR description. */
    private List<Class<? extends GridTask<?, ?>>> tasks;

    /**
     * Creates new instance of configuration helper with given configuration.
     *
     * @param factory Configuration factory.
     */
    GridUriDeploymentSpringDocument(XmlBeanFactory factory) {
        assert factory != null;

        this.factory = factory;
    }

    /**
     * Loads tasks declared in configuration by given class loader.
     *
     * @param clsLdr Class loader.
     * @return Declared tasks.
     * @throws GridSpiException Thrown if there are no tasks in
     *      configuration or configuration could not be read.
     */
    @SuppressWarnings({"unchecked"})
    List<Class<? extends GridTask<?, ?>>> getTasks(ClassLoader clsLdr) throws GridSpiException {
        assert clsLdr!= null;

        try {
            if (tasks == null) {
                tasks = new ArrayList<Class<? extends GridTask<?, ?>>>();

                Map<String, List<String>> beans = factory.getBeansOfType(List.class);

                if (beans.size() > 0) {
                    for (List<String> list : beans.values()) {
                        for (String clsName : list) {
                            Class taskCls;

                            try {
                                taskCls = clsLdr.loadClass(clsName);
                            }
                            catch (ClassNotFoundException e) {
                                throw new GridSpiException("Failed to load task class [className=" + clsName + ']', e);
                            }

                            assert taskCls != null;

                            tasks.add(taskCls);
                        }
                    }
                }
            }
        }
        catch (BeansException e) {
            throw new GridSpiException("Failed to get tasks declared in XML file.", e);
        }

        return tasks;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridUriDeploymentSpringDocument.class, this);
    }
}
