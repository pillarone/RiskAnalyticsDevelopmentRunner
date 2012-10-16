// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.marshaller;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.executor.*;
import org.gridgain.grid.kernal.processors.*;
import org.gridgain.grid.logger.*;
import org.springframework.context.*;
import javax.management.*;
import java.util.concurrent.*;

/**
 * Controls what classes should be excluded from marshalling by default.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public final class GridMarshallerController {
    /**
     * Classes that must be included in serialization. All marshallers must
     * included these classes.
     * <p>
     * Note that this list supercedes {@link #EXCL_CLASSES}.
     */
    private static final Class<?>[] INCL_CLASSES = new Class[] {
        // GridGain classes.
        GridLoggerProxy.class,
        GridExecutorService.class
    };

    /**
     * Excluded grid classes from serialization. All marshallers must omit
     * these classes. Fields of these types should be serialized as {@code null}.
     * <p>
     * Note that {@link #INCL_CLASSES} supercedes this list.
     */
    private static final Class<?>[] EXCL_CLASSES = new Class[] {
        // Non-GridGain classes.
        MBeanServer.class,
        ExecutorService.class,
        ApplicationContext.class,

        // GridGain classes.
        GridLogger.class,
        GridTaskSession.class,
        GridLoadBalancer.class,
        GridJobContext.class,
        GridMarshaller.class,
        GridProcessor.class,
        GridTaskContinuousMapper.class
    };

    /**
     * Ensures singleton.
     */
    private GridMarshallerController() {
        // No-op.
    }

    /**
     * Checks whether or not given class should be excluded from marshalling. 
     *
     * @param cls Class to check.
     * @return {@code true} if class should be excluded, {@code false} otherwise.
     */
    public static boolean isExcluded(Class<?> cls) {
        assert cls != null;

        for (Class<?> c : INCL_CLASSES) {
            if (c.isAssignableFrom(cls)) {
                return false;
            }
        }

        for (Class<?> c : EXCL_CLASSES) {
            if (c.isAssignableFrom(cls)) {
                return true;
            }
        }

        return false;
    }
}
