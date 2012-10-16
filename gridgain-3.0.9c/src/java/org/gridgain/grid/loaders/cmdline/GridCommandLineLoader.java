// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.loaders.cmdline;

import org.gridgain.grid.*;
import org.gridgain.grid.loaders.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;
import org.springframework.util.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.GridFactoryState.*;
import static org.gridgain.grid.GridSystemProperties.*;

/**
 * This class defines command-line GridGain loader. This loader can be used to start GridGain
 * outside of any hosting environment from command line. This loader is a Java application with
 * {@link #main(String[])} method that accepts command line arguments. It accepts just one
 * parameter which is Spring XML configuration file path. You can run this class from command
 * line without parameters to get help message.
 * <p>
 * Note that scripts {@code ${GRIDGAIN_HOME}/bin/ggstart.{sh|bat}} shipped with GridGain use
 * this loader and you can use them as an example.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@SuppressWarnings({"CallToSystemExit"})
@GridLoader(description = "Command line loader")
public final class GridCommandLineLoader {
    /** Ant-augmented version number. */
    private static final String VER = "3.0.9c";

    /** Ant-augmented build number. */
    private static final String BUILD = "19052011";

    /** Ant-augmented copyright blurb. */
    private static final String COPYRIGHT = "2005-2011 Copyright (C) GridGain Systems, Inc.";

    /** Latch. */
    private static CountDownLatch latch;

    /**
     * Enforces singleton.
     */
    private GridCommandLineLoader() {
        // No-op.
    }

    /**
     * Exists with optional error message, usage show and exit code.
     *
     * @param errMsg Optional error message.
     * @param showUsage Whether or not to show usage information.
     * @param exitCode Exit code.
     */
    private static void exit(@Nullable String errMsg, boolean showUsage, int exitCode) {
        if (errMsg != null)
            X.error(errMsg);

        String runner = System.getProperty(GG_PROG_NAME, "ggstart.{sh|bat}");

        int space = runner.indexOf(' ');

        runner = runner.substring(0, space == -1 ? runner.length() : space);

        if (showUsage) {
            X.error(
                "Usage:",
                "    " + runner + " [?]|[path {-v}]|[-i]",
                "    Where:",
                "    ?, /help, -help  - show this message.",
                "    -v               - verbose mode (quiet by default).",
                "    -i               - interactive mode (choose configuration file from list).",
                "    path             - path to Spring XML configuration file.",
                "                       Path can be absolute or relative to GRIDGAIN_HOME.",
                " ",
                "Spring file should contain one bean definition of Java type",
                "org.gridgain.grid.GridConfiguration. Note that bean will be",
                "fetched by the type and its ID is not used.");
        }

        System.exit(exitCode);
    }

    /**
     * Tests whether argument is help argument.
     *
     * @param arg Command line argument.
     * @return {@code true} if given argument is a help argument, {@code false} otherwise.
     */
    @SuppressWarnings({"IfMayBeConditional"})
    private static boolean isHelp(String arg) {
        String s;

        if (arg.startsWith("--"))
            s = arg.substring(2);
        else if (arg.startsWith("-") || arg.startsWith("/") || arg.startsWith("\\"))
            s = arg.substring(1);
        else
            s = arg;

        return "?".equals(s) || "help".equalsIgnoreCase(s) || "h".equalsIgnoreCase(s);
    }

    /**
     *
     */
    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    private static void createSuccessFile() {
        String file = System.getProperty(GG_SUCCESS_FILE);

        if (StringUtils.hasText(file))
            try {
                new File(file).createNewFile();
            }
            catch (IOException e) {
                X.error("Failed to create success file '" + file + "': " + e.getMessage());
            }
    }

    /**
     * Interactively asks for configuration file path.
     *
     * @return Configuration file path. {@code null} if operation  was cancelled.
     * @throws IOException In case of error.
     */
    private static String askConfigFile() throws IOException {
        List<String> files = GridConfigurationFinder.getConfigurationFiles();

        String title = "Available configuration files:";

        X.println(title);
        X.println(U.dash(title.length()));

        for (int i = 0; i < files.size(); i++) {
            System.out.println(i + ":\t" + files.get(i));
        }

        X.print("\nChoose configuration file ('c' to cancel) [0]: ");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        String line = reader.readLine();

        if ("c".equalsIgnoreCase(line)) {
            System.out.println("\nOperation cancelled.");

            return null;
        }

        if ("".equals(line))
            line = "0";

        try {
            String file = files.get(Integer.valueOf(line));

            X.println("\nUsing configuration: " + file + "\n");

            return file;
        }
        catch (Exception ignored) {
            X.error("\nInvalid selection: " + line);

            return null;
        }
    }

    /**
     * Main entry point.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        X.println("GridGain Command Line Loader, ver. " + VER + "." + BUILD);
        X.println(COPYRIGHT);
        X.println();

        if (args.length > 1)
            exit("Too many arguments.", true, -1);

        if (args.length > 0 && isHelp(args[0]))
            exit(null, true, 0);

        if (args.length > 0 && args[0].charAt(0) == '-')
            exit("Invalid arguments: " + args[0], true, -1);

        G.addListener(new GridFactoryListener() {
            @Override public void onStateChange(String name, GridFactoryState state) {
                if (state == STOPPED && latch != null)
                    latch.countDown();
            }
        });

        String cfg = null;

        if (args.length > 0)
            cfg = args[0];
        else {
            try {
                cfg = askConfigFile();

                if (cfg == null)
                    exit(null, false, 0);
            }
            catch (IOException e) {
                exit("Failed to run interactive mode: " + e.getMessage(), false, -1);
            }
        }

        try {
            G.start(cfg);
        }
        catch (GridException e) {
            e.printStackTrace();

            exit("Failed to start grid: " + e.getMessage(), false, -1);
        }

        // Create success file.
        createSuccessFile();

        latch = new CountDownLatch(G.allGrids().size());

        try {
            while (latch.getCount() > 0)
                latch.await();
        }
        catch (InterruptedException e) {
            X.error("Loader was interrupted (exiting): " + e.getMessage());
        }

        String code = System.getProperty(GG_RESTART_CODE);

        if (code != null)
            try {
                System.exit(Integer.parseInt(code));
            }
            catch (NumberFormatException ignore) {
                System.exit(0);
            }
        else
            System.exit(0);
    }
}
