// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.deployment.uri;

import org.gridgain.grid.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import static org.gridgain.grid.spi.deployment.uri.GridUriDeploymentSpi.*;

/**
 * Utility class.
 * <p>
 * Provides useful and common functions for URI deployment.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
final class GridUriDeploymentFileProcessor {
    /**
     * Enforces singleton.
     */
    private GridUriDeploymentFileProcessor() {
        // No-op.
    }

    /**
     * Method processes given GAR file and extracts all tasks from it which are
     * either mentioned in GAR descriptor or implements interface {@link GridTask}
     * if there is no descriptor in file.
     *
     * @param file GAR file with tasks.
     * @param uri GAR file deployment URI.
     * @param deployDir deployment directory with downloaded GAR files.
     * @param log Logger.
     * @throws GridSpiException Thrown if file could not be read.
     * @return List of tasks from given file.
     */
    @Nullable
    static GridUriDeploymentFileProcessorResult processFile(File file, String uri, File deployDir, GridLogger log)
        throws GridSpiException {
        File gar = file;

        if (!checkIntegrity(file, log)) {
            U.error(log, "Tasks in GAR not loaded in configuration. Invalid file signature. [uri=" +
                U.hidePassword(uri) + ']');

            return null;
        }

        if (!file.isDirectory()) {
            gar = new File(deployDir, "dirzip_" + file.getName());

            gar.mkdirs();

            try {
                U.unzip(file, gar, log);
            }
            catch (IOException e) {
                throw new GridSpiException("IO error when unzipping GAR file: " + file.getAbsolutePath(), e);
            }
        }

        if (gar.isDirectory()) {
            try {
                File xml = new File(gar, XML_DESCRIPTOR_PATH);

                if (!xml.exists() || xml.isDirectory()) {
                    if (log.isInfoEnabled())
                        log.info("Process deployment without descriptor file [path=" + XML_DESCRIPTOR_PATH +
                            ", gar=" + gar.getAbsolutePath() + ']');

                    return processNoDescriptorFile(gar, uri, log);
                }
                else {
                    InputStream in = null;

                    try {
                        in = new BufferedInputStream(new FileInputStream(xml));

                        // Parse XML task definitions and add them to cache.
                        GridUriDeploymentSpringDocument doc = GridUriDeploymentSpringParser.parseTasksDocument(in, log);

                        assert doc != null;

                        return processWithDescriptorFile(doc, gar, uri, log);
                    }
                    finally {
                        U.close(in, log);
                    }
                }
            }
            catch (IOException e) {
                throw new GridSpiException("IO error when parsing GAR directory: " + gar.getAbsolutePath(), e);
            }
        }

        return null;
    }

    /**
     * Cleanup class loaders resource.
     *
     * @param clsLdr Released class loader.
     * @param log Logger.
     */
    static void cleanupUnit(ClassLoader clsLdr, GridLogger log) {
        assert clsLdr != null;
        assert log != null;

        if (clsLdr instanceof URLClassLoader) {
            URLClassLoader clsLdr0 = (URLClassLoader)clsLdr;

            U.close(clsLdr0, log);

            try {
                URL url = clsLdr0.getURLs()[0];

                File dir = new File(url.toURI());

                U.delete(dir);

                if (dir.getName().startsWith("dirzip_")) {
                    File jarFile = new File(dir.getParentFile(), dir.getName().substring(7));

                    U.delete(jarFile);
                }
            }
            catch (Exception e) {
                U.error(log, "Failed to cleanup unit: [clsLdr=" + clsLdr + ']', e);
            }
        }
    }

    /**
     * Processes given GAR file and returns back all tasks which are in
     * descriptor.
     *
     * @param doc GAR file descriptor.
     * @param file GAR file.
     * @param uri GAR file deployment URI.
     * @param log Logger.
     * @throws GridSpiException Thrown if it's impossible to open file.
     * @return List of tasks from descriptor.
     */
    @SuppressWarnings({"ClassLoader2Instantiation"})
    private static GridUriDeploymentFileProcessorResult processWithDescriptorFile(GridUriDeploymentSpringDocument doc,
        File file, String uri, GridLogger log) throws GridSpiException {
        ClassLoader clsLdr = GridUriDeploymentClassLoaderFactory.create(
            GridUriDeploymentClassLoaderFactory.class.getClassLoader(), file, log);

        List<Class<? extends GridTask<?, ?>>> tasks = doc.getTasks(clsLdr);

        List<Class<? extends GridTask<?, ?>>> validTasks = null;

        if (!F.isEmpty(tasks)) {
            validTasks = new ArrayList<Class<? extends GridTask<?, ?>>>();

            for (Class<? extends GridTask<?, ?>> task : tasks) {
                if (!isAllowedTaskClass(task)) {
                    U.warn(log, "Failed to load task. Task should be public none-abstract class " +
                        "(might be inner static one) that implements GridTask interface [taskCls=" + task + ']');
                }
                else {
                    if (log.isDebugEnabled())
                        log.debug("Found grid deployment task: " + task.getName());

                    validTasks.add(task);
                }
            }
        }

        GridUriDeploymentFileProcessorResult res = new GridUriDeploymentFileProcessorResult();

        res.setFile(file);
        res.setClassLoader(clsLdr);

        if (!F.isEmpty(validTasks)) {
            res.setTaskClasses(validTasks);
        }
        else if (log.isDebugEnabled()) {
            log.debug("No tasks loaded from file [file=" + file.getAbsolutePath() +
                ", uri=" + U.hidePassword(uri) + ']');
        }

        return res;
    }

    /**
     * Processes GAR files which have no descriptor. It scans every class and
     * checks if it is a valid task or not. All valid tasks are returned back.
     *
     * @param file GAR file or directory.
     * @param uri GAR file deployment URI.
     * @param log Logger.
     * @throws GridSpiException Thrown if file reading error happened.
     * @return List of tasks from given file.
     */
    private static GridUriDeploymentFileProcessorResult processNoDescriptorFile(File file, String uri, GridLogger log)
        throws GridSpiException {
        ClassLoader clsLdr = GridUriDeploymentClassLoaderFactory.create(
            GridUriDeploymentClassLoaderFactory.class.getClassLoader(), file, log);

        Set<Class<? extends GridTask<?, ?>>> clss = GridUriDeploymentDiscovery.getClasses(clsLdr, file);

        GridUriDeploymentFileProcessorResult res = new GridUriDeploymentFileProcessorResult();

        res.setFile(file);
        res.setClassLoader(clsLdr);

        if (clss != null) {
            List<Class<? extends GridTask<?, ?>>> validTasks =
                new ArrayList<Class<? extends GridTask<?, ?>>>(clss.size());

            for (Class<? extends GridTask<?, ?>> cls : clss) {
                if (isAllowedTaskClass(cls)) {
                    if (log.isDebugEnabled())
                        log.debug("Found grid deployment task: " + cls.getName());

                    validTasks.add(cls);
                }
            }

            if (!validTasks.isEmpty()) {
                res.setTaskClasses(validTasks);
            }
            else if (log.isDebugEnabled()) {
                log.debug("No tasks loaded from file [file=" + file.getAbsolutePath() +
                    ", uri=" + U.hidePassword(uri) + ']');
            }
        }

        return res;
    }

    /**
     * Check that class may be instantiated as {@link GridTask} and used
     * in deployment.
     *
     * Loaded task class must implement interface {@link GridTask}.
     * Only non-abstract, non-interfaces and public classes allowed.
     * Inner static classes also allowed for loading.
     *
     * @param cls Class to check
     * @return {@code true} if class allowed for deployment.
     */
    private static boolean isAllowedTaskClass(Class<?> cls) {
        if (!GridTask.class.isAssignableFrom(cls))
            return false;

        int modifiers = cls.getModifiers();

        return !Modifier.isAbstract(modifiers) && !Modifier.isInterface(modifiers) &&
            (!cls.isMemberClass() || Modifier.isStatic(modifiers)) && Modifier.isPublic(modifiers);
    }

    /**
     * Make integrity check for GAR file.
     * Method returns {@code false} if GAR file has incorrect signature.
     *
     * @param file GAR file which should be verified.
     * @param log Logger.
     * @return {@code true} if given file is a directory of verification
     *      completed successfully otherwise returns {@code false}.
     */
    private static boolean checkIntegrity(File file, GridLogger log) {
        try {
            return file.isDirectory() || GridUriDeploymentJarVerifier.verify(file.getAbsolutePath(), false, log);
        }
        catch (IOException e) {
            U.error(log, "Error while making integrity file check.", e);
        }

        return false;
    }
}
