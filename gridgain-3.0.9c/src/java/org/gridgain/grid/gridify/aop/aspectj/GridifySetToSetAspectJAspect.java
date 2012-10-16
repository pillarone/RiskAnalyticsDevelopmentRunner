// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.gridify.aop.aspectj;

import org.aspectj.lang.*;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.*;
import org.gridgain.grid.*;
import org.gridgain.grid.gridify.*;
import org.gridgain.grid.gridify.aop.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.util.gridify.*;
import java.lang.reflect.*;

import static org.gridgain.grid.GridFactoryState.*;
import static org.gridgain.grid.util.gridify.GridifyUtils.*;

/**
 * AspectJ aspect that cross-cuts on all methods grid-enabled with
 * {@link GridifySetToSet} annotation and potentially executes them on
 * remote node.
 * <p>
 * See {@link GridifySetToSet} documentation for more information about execution of
 * {@code gridified} methods.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 * @see GridifySetToSet
 */
@Aspect
public class GridifySetToSetAspectJAspect extends GridifySetToSetAbstractAspect {
    /**
     * Aspect implementation which executes grid-enabled methods on remote
     * nodes.
     *
     * @param joinPoint Join point provided by AspectJ AOP.
     * @return Method execution result.
     * @throws Throwable If execution failed.
     */
    @SuppressWarnings({"ProhibitedExceptionDeclared", "ProhibitedExceptionThrown", "CatchGenericClass"})
    @Around("execution(@org.gridgain.grid.gridify.GridifySetToSet * *(..)) && !cflow(call(* org.gridgain.grid.GridJob.*(..)))")
    public Object gridify(ProceedingJoinPoint joinPoint) throws Throwable {
        Method mtd = ((MethodSignature)joinPoint.getSignature()).getMethod();

        GridifySetToSet ann = mtd.getAnnotation(GridifySetToSet.class);

        assert ann != null : "Intercepted method does not have gridify annotation.";

        // Since annotations in Java don't allow 'null' as default value
        // we have accept an empty string and convert it here.
        // NOTE: there's unintended behavior when user specifies an empty
        // string as intended grid name.
        // NOTE: the 'ann.gridName() == null' check is added to mitigate
        // annotation bugs in some scripting languages (e.g. Groovy).
        String gridName = F.isEmpty(ann.gridName()) ? null : ann.gridName();

        if (G.getState(gridName) != STARTED) {
            throw new GridException("Grid is not locally started: " + gridName);
        }

        GridifyNodeFilter nodeFilter = null;

        if (!ann.nodeFilter().equals(GridifyNodeFilter.class)) {
            nodeFilter = ann.nodeFilter().newInstance();
        }

        // Check method return type.
        checkMethodSignature(mtd);

        GridifyArgumentBuilder argBuilder = new GridifyArgumentBuilder();

        // Creates task argument.
        GridifyRangeArgument arg = argBuilder.createTaskArgument(
            mtd.getDeclaringClass(),
            mtd.getName(),
            mtd.getReturnType(),
            mtd.getParameterTypes(),
            mtd.getParameterAnnotations(),
            joinPoint.getArgs(),
            joinPoint.getTarget());

        if (!ann.interceptor().equals(GridifyInterceptor.class)) {
            // Check interceptor first.
            if (!ann.interceptor().newInstance().isGridify(ann, arg)) {
                return joinPoint.proceed();
            }
        }

        // Proceed locally for negative threshold parameter.
        if (ann.threshold() < 0) {
            return joinPoint.proceed();
        }

        // Analyse where to execute method (remotely or locally).
        if (arg.getInputSize() != UNKNOWN_SIZE && arg.getInputSize() <= ann.threshold()) {
            return joinPoint.proceed();
        }

        // Check is split to jobs allowed for input method argument with declared splitSize.
        checkIsSplitToJobsAllowed(arg, ann);

        try {
            Grid grid = G.grid(gridName);

            return execute(grid, joinPoint.getSignature().getDeclaringType(), arg, nodeFilter,
                ann.threshold(), ann.splitSize(), ann.timeout());
        }
        catch (Throwable e) {
            for (Class<?> ex : ((MethodSignature)joinPoint.getSignature()).getMethod().getExceptionTypes()) {
                // Descend all levels down.
                Throwable cause = e.getCause();

                while (cause != null) {
                    if (ex.isAssignableFrom(cause.getClass())) {
                        throw cause;
                    }

                    cause = cause.getCause();
                }

                if (ex.isAssignableFrom(e.getClass())) {
                    throw e;
                }
            }

            throw new GridifyRuntimeException("Undeclared exception thrown: " + e.getMessage(), e);
        }
    }
}
