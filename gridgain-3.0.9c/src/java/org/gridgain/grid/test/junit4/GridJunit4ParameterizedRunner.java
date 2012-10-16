// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import org.gridgain.grid.*;
import org.junit.runner.*;
import org.junit.runners.Parameterized.*;
import org.junit.runners.model.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * JUnit 4 parameterized runner.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit4ParameterizedRunner extends GridJunit4SuiteRunner {
    /**
     * @param cls Suite class.
     */
    GridJunit4ParameterizedRunner(Class<?> cls) {
        super(cls);
    }

    /** {@inheritDoc} */
    @Override protected List<GridJunit4Runner> createChildren() {
        final Class<?> cls = getTestClass();

        final AtomicInteger childCnt = new AtomicInteger(0);

        List<GridJunit4Runner> children = new ArrayList<GridJunit4Runner>();

        try {
            for (Object param : getParametersList()) {
                if (param instanceof Object[]) {
                    children.add(new GridJunit4ClassRunner(cls) {
                        /** */
                        private int idx = childCnt.getAndIncrement();

                        /** {@inheritDoc} */
                        @Override protected Description getDescription(Method mtd) {
                            return Description.createTestDescription(cls,
                                String.format("%s[%s]", mtd.getName(), idx), mtd.getAnnotations());
                        }
                    });
                }
                else {
                    throw new GridRuntimeException(String.format("%s.%s() must return a Collection of arrays.",
                        cls.getName(), getParametersMethod().getName()));
                }
            }
        }
        catch (InitializationError e) {
            throw new GridRuntimeException("Failed to create children.", e);
        }

        return children;
    }

    /**
     * @return List of parameters.
     * @throws InitializationError If any exception occurs.
     */
    private Iterable<?> getParametersList() throws InitializationError {
        try {
            return (Iterable<?>)getParametersMethod().invoke(null);
        }
        catch (IllegalAccessException e) {
            throw new InitializationError(e);
        }
        catch (InvocationTargetException e) {
            throw new InitializationError(e);
        }
    }

    /**
     * @return Method annotated with {@link Parameters @Parameters} annotation.
     * @throws InitializationError If no public static parameters method on class. 
     */
    private Method getParametersMethod() throws InitializationError {
        for (Method mtd : GridJunit4Utils.getAnnotatedMethods(getTestClass(), Parameters.class)) {
            int modifiers = mtd.getModifiers();

            if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)) {
                return mtd;
            }
        }

        throw new InitializationError("No public static parameters method on class: " + getTestClass());
    }
}
