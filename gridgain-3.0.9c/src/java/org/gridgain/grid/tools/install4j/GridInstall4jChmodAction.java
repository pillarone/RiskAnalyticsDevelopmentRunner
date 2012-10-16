// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.tools.install4j;

import com.install4j.api.actions.*;
import com.install4j.api.context.*;
import java.io.*;
import java.util.regex.*;

/**
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridInstall4jChmodAction extends AbstractInstallAction {
    /** */
    private String mode = "744";

    /** */
    private String regex;

    /**
     * @return TODO
     */
    public String getFilenameRegex() {
        return regex;
    }

    /**
     * @param regex TODO
     */
    public void setFilenameRegex(String regex) {
        this.regex = regex;
    }

    /**
     * @return TODO
     */
    public String getMode() {
        return mode;
    }

    /**
     * @param mode TODO
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /** {@inheritDoc} */
    @Override
    public boolean install(InstallerContext ctx) throws UserCanceledException {
        File installRoot = ctx.getInstallationDirectory();

        if (!installRoot.isDirectory()) {
            throw new UserCanceledException("Installation directory is not a directory.");
        }

        Pattern pat = Pattern.compile(regex);

        processDirectory(installRoot, pat);

        return true;
    }

    /**
     * @param dir TODO
     * @param pat TODO
     */
    private void processDirectory(File dir, Pattern pat) {
        assert dir != null;
        assert pat != null;
        assert dir.isDirectory();

        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                processDirectory(file, pat);
            }
            else if (pat.matcher(file.getAbsolutePath()).matches()) {
                runChmod(file, mode);
            }
        }
    }

    /**
     * @param file TODO
     * @param mode TODO
     */
    private void runChmod(File file, String mode) {
        try {
            Process p = Runtime.getRuntime().exec("chmod " + mode + ' ' + file.getAbsolutePath());

            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            while (in.readLine() != null) {}
            while (err.readLine() != null) {}
        }
        catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }
}
