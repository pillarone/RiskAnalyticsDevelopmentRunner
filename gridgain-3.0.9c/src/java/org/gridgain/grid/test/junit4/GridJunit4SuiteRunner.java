// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import junit.framework.*;
import org.gridgain.grid.*;
import org.junit.*;
import org.junit.experimental.runners.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;
import org.junit.runners.*;
import org.junit.runners.Suite.*;
import org.junit.runners.model.*;
import java.util.*;

/**
 * JUnit 4 suite runner.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
class GridJunit4SuiteRunner extends GridJunit4Runner {
    /** */
    private final Class<?> cls;

    /** */
    private List<GridJunit4Runner> children;

    /** */
    private transient Description desc;

    /** */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private transient Collection<GridJunit4Runner> locRunners = new HashSet<GridJunit4Runner>();

    /**
     * Constructor required by JUnit4.
     *
     * @param cls Suite class.
     */
    GridJunit4SuiteRunner(Class<?> cls) {
        assert cls != null;

        this.cls = cls;
    }

    /**
     * @return Annotated classes.
     * @throws InitializationError If
     */
    private Class<?>[] getTestClasses() throws InitializationError {
        SuiteClasses ann = cls.getAnnotation(SuiteClasses.class);

        if (ann == null) {
            throw new InitializationError(String.format("class '%s' must have a SuiteClasses annotation",
                cls.getName()));
        }

        return ann.value();
    }

    /** {@inheritDoc} */
    @Override public final Description getDescription() {
        if (desc == null) {
            desc = createDescription();
        }

        return desc;
    }

    /**
     * @return Description for test class.
     */
    protected Description createDescription() {
        Description desc = Description.createSuiteDescription(cls.getName());

        for (Runner child : getChildren()) {
            desc.addChild(child.getDescription());
        }

        return desc;
    }

    /** {@inheritDoc} */
    @Override public void run(RunNotifier notifier) {
        for (GridJunit4Runner child : getChildren()) {
            child.run(notifier);
        }
    }

    /** {@inheritDoc} */
    @Override Class<?> getTestClass() {
        return cls;
    }

    /** {@inheritDoc} */
    @Override boolean setResult(GridJunit4Result res) {
        for (GridJunit4Runner child : getChildren()) {
            if (child.setResult(res)) {
                return true;
            }
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override boolean setResult(List<GridJunit4Result> res) {
        for (GridJunit4Result result : res) {
            if (!setResult(result)) {
                return false;
            }
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override void copyResults(GridJunit4Runner runner) {
        GridJunit4SuiteRunner resRunner = (GridJunit4SuiteRunner)runner;

        for (int i = 0; i < getChildren().size(); i++) {
            getChildren().get(i).copyResults(resRunner.getChildren().get(i));
        }
    }

    /**
     * @return Child runners.
     */
    final List<GridJunit4Runner> getChildren() {
        if (children == null) {
            children = createChildren();
        }

        return children;
    }

    /**
     * @return Child runners.
     */
    protected List<GridJunit4Runner> createChildren() {
        List<GridJunit4Runner> children = new ArrayList<GridJunit4Runner>();

        try {
            for (Class<?> testCls : getTestClasses()) {
                children.add(getRunner(testCls));
            }
        }
        catch (InitializationError e) {
            throw new GridRuntimeException("Failed to create suite children.", e);
        }

        return children;
    }

    /**
     * @param runner Runner.
     * @return {@code True} if runner is local.
     */
    protected boolean isLocal(GridJunit4Runner runner) {
        return locRunners.contains(runner);
    }

    /**
     * @param testCls Test class.
     * @return Runner for the class.
     * @throws InitializationError If runner other than 'Suite'.
     */
    @SuppressWarnings({"IfMayBeConditional", "ClassReferencesSubclass"})
    protected GridJunit4Runner getRunner(Class<?> testCls) throws InitializationError {
        if (testCls.getAnnotation(Ignore.class) != null) {
            return new GridJunit4IgnoredClassRunner(testCls);
        }

        RunWith runWith = testCls.getAnnotation(RunWith.class);

        if (runWith != null) {
            Class<?> runnerCls = runWith.value();

            if (runnerCls.equals(GridJunit4LocalSuite.class)) {
                GridJunit4Runner runner = new GridJunit4SuiteRunner(testCls);

                locRunners.add(runner);

                return runner;
            }
            else if (runnerCls.equals(Suite.class) || runnerCls.equals(GridJunit4Suite.class)) {
                return new GridJunit4SuiteRunner(testCls);
            }
            else if (runnerCls.equals(AllTests.class)) {
                return new GridJunit38SuiteRunner(testCls);
            }
            else if (runnerCls.equals(Parameterized.class)) {
                return new GridJunit4ParameterizedRunner(testCls);
            }
            else if (runnerCls.equals(Enclosed.class)) {
                return new GridJunit4EnclosedRunner(testCls);
            }

            throw new InitializationError("Runners other than 'Suite' are not supported yet: " + runWith.value());
        }
        else if (GridJunit4Utils.hasSuiteMethod(testCls)) {
            return new GridJunit38SuiteRunner(testCls);
        }
        else if (isPre4Test(testCls)) {
            return new GridJunit38SuiteRunner(testCls);
        }
        else {
            // According to Junit4 code, this is the best we can do here.
            return new GridJunit4ClassRunner(testCls);
        }
    }

    /**
     * @param testClass Test class.
     * @return {@code True} if test is Junit3 test.
     */
    private boolean isPre4Test(Class<?> testClass) {
        return TestCase.class.isAssignableFrom(testClass);
    }
}
