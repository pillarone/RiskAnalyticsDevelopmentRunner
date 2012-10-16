// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.loaders.weblogic;

import org.gridgain.grid.loaders.*;
import org.gridgain.grid.typedef.*;
import weblogic.common.*;
import java.util.*;

/**
 * GridGain loader for WebLogic implemented as a pair of start and shutdown
 * classes. This is a shutdown class. Please consult WebLogic documentation
 * on how to configure startup classes in Weblogic. Weblogic loader should
 * be used for tight integration with Weblogic AS. Specifically, Weblogic
 * loader integrates GridGain with Weblogic logging, MBean server, work
 * manager (<a target=_blank href="http://jcp.org/en/jsr/detail?id=237">JSR-237</a>).
 * <p>
 * The following steps should be taken to configure startup and shutdown classes:
 * <ol>
 * <li>
 *      Add Startup and Shutdown Class in admin console ({@code Environment -> Startup & Shutdown Classes -> New}).
 * </li>
 * <li>
 *      Add the following parameters for startup class:
 *      <ul>
 *      <li>Name: {@code GridWeblogicStartup}</li>
 *      <li>Classname: {@code org.gridgain.grid.loaders.weblogic.GridWeblogicStartup}</li>
 *      <li>Arguments: {@code cfgFilePath=config/default-spring.xml}</li>
 *      </ul>
 * </li>
 * <li>
 *      Add the following parameters for shutdown class:
 *      <ul>
 *      <li>Name: {@code GridWeblogicShutdown}</li>
 *      <li>Classname: {@code org.gridgain.grid.loaders.weblogic.GridWeblogicShutdown}</li>
 *      </ul>
 * </li>
 * <li>
 *      Change classpath for WebLogic server in startup script:
 *      {@code CLASSPATH="${CLASSPATH}:${GRIDGAIN_HOME}/gridgain.jar:${GRIDGAIN_HOME}/libs/"}
 * </li>
 * </ol>
 * <p>
 * For more information see
 * <a target=wiki href="http://www.gridgainsystems.com:8080/wiki/display/GG15UG/Configuring+and+Starting+GridGain">Configuring and Starting GridGain</a> and
 * <a target=_blank href="http://edocs.bea.com/wls/docs100/ConsoleHelp/taskhelp/startup_shutdown/UseStartupAndShutdownClasses.html">Startup and Shutdown Classes.</a>
 * <p>
 * <b>Note</b>: Weblogic is not shipped with GridGain. If you don't have Weblogic, you need to
 * download it separately. See <a target=_blank href="http://www.bea.com">http://www.bea.com</a> for
 * more information.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
@GridLoader(description = "Weblogic loader")
@SuppressWarnings({"deprecation", "unchecked"})
public class GridWeblogicShutdown implements GridWeblogicShutdownMBean {
    /**
     * See <a href="http://edocs.bea.com/wls/docs81/javadocs/weblogic/common/T3ShutdownDef.html">
     * http://edocs.bea.com/wls/docs81/javadocs/weblogic/common/T3ShutdownDef.html</a> for more
     * information.
     *
     * @param str Virtual class name.
     * @param params Name-value parameters supplied with shutdown class registration.
     * @return Return string.
     * @throws Exception Thrown if error occurred.
     */
    public String shutdown(String str, Hashtable params) throws Exception {
        G.stopAll(true);

        return getClass().getSimpleName() + " stopped successfully.";
    }

    /**
     * See <a href="http://edocs.bea.com/wls/docs81/javadocs/weblogic/common/T3ShutdownDef.html">
     * http://edocs.bea.com/wls/docs81/javadocs/weblogic/common/T3ShutdownDef.html</a> for more
     * information.
     *
     * @param t3ServicesDef Weblogic services accessor.
     */
    public void setServices(T3ServicesDef t3ServicesDef) {
        // No-op.
    }
}
