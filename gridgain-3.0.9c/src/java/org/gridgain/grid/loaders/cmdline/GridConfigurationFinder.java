// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.loaders.cmdline;

import org.gridgain.grid.typedef.internal.*;

import java.io.*;
import java.util.*;

/**
 * Finds configuration files located in {@code GRIDGAIN_HOME} folder
 * and its subfolders.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
final class GridConfigurationFinder {
    /** Path to default configuration file. */
    private static final String DFLT_CFG = "config" + File.separator + "default-spring.xml";

    /**
     * Ensure singleton.
     */
    private GridConfigurationFinder() {
        // no-op
    }

    /**
     * Lists paths to all GridGain configuration files
     * located in {@code GRIDGAIN_HOME} folder and its subfolders.
     * Default configuration file will be skipped.
     *
     * @return List of configuration files.
     * @throws IOException If error occurs.
     */
    static List<String> getConfigurationFiles() throws IOException {
        LinkedList<String> files = getConfigurationFiles(new File(U.getGridGainHome()));

        Collections.sort(files);

        files.addFirst(DFLT_CFG);

        return files;
    }

    /**
     * Lists paths to all GridGain configuration files
     * located in specified folder and its subfolders.
     * Default configuration file will be skipped.
     *
     * @param dir Directory.
     * @return List of configuration files in the directory.
     * @throws IOException If error occurs.
     */
    private static LinkedList<String> getConfigurationFiles(File dir) throws IOException {
        LinkedList<String> files = new LinkedList<String>();

        for (String name : dir.list()) {
            File file = new File(dir, name);

            if (file.isDirectory())
                files.addAll(getConfigurationFiles(file));
            else if (file.getName().endsWith(".xml")) {
                BufferedReader reader = new BufferedReader(new FileReader(file));

                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.contains("class=\"org.gridgain.grid.GridConfigurationAdapter\"")) {
                        String path =
                            file.getAbsolutePath().replace(U.getGridGainHome() + File.separator, "");

                        // Skip the default config file.
                        if (!path.equals(DFLT_CFG))
                            files.add(path);

                        break;
                    }
                }
            }
        }

        return files;
    }
}
