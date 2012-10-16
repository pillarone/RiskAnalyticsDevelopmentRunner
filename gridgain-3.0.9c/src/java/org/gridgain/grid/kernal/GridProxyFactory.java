// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import javassist.util.proxy.*;
import org.gridgain.grid.*;
import org.jetbrains.annotations.*;
import org.springframework.beans.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Class creates proxy objects based on JavaAssist framework proxies.
 * <p>
 * Every proxy object has an interceptor which is notified about method calls.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridProxyFactory {
    /** */
    private final Collection<GridProxyListener> lsnrs = new ArrayList<GridProxyListener>();

    /**
     * Gets proxy object for given super class and constructor arguments.
     *
     * @param <T> Type of super class.
     * @param superCls Super class.
     * @param args Super class constructor arguments.
     * @return Proxy object what extends given super class.
     * @throws GridException If any instantiation error occurred.
     */
    @SuppressWarnings({"unchecked", "TypeMayBeWeakened"})
    private <T> T makeProxyFor(Class<T> superCls, Object... args) throws GridException {
        assert superCls != null;

        ProxyFactory fac = new ProxyFactory();

        fac.setSuperclass(superCls);

        Class<T> cls = fac.createClass();

        T res;

        try {
            res = createInstance(cls, args);
        }
        catch (Exception e) {
            throw new GridException("Failed to instantiate proxy for " + superCls.getSimpleName(), e);
        }

        assert isProxy(res);

        // Set handler if it is traceable.
        ((ProxyObject)res).setHandler(new GridMethodHandler(superCls));

        return res;
    }

    /**
     * Creates new proxy object which has given one as a "super object".
     * Proxy has the same super class as a given object class and all
     * properties copied from given object.
     * <p>
     * If object has no constructor with given arguments the
     * same instance is returned.
     *
     * @param <T> Type of the object to get proxy for.
     * @param obj Object instance to be wrapped.
     * @param args Super class constructor arguments.
     * @return Proxy object what extends given super class.
     * @throws GridException If any instantiation error occurred.
     */
    @SuppressWarnings({"unchecked"})
    public <T> T getProxy(T obj, Object... args) throws GridException {
        assert obj != null;

        if (isProxy(obj))
            throw new GridException("Failed to create proxy object for another proxy object: " + obj);

        T res = makeProxyFor((Class<T>)obj.getClass(), args);

        BeanUtils.copyProperties(obj, res, obj.getClass());

        return res;
    }

    /**
     * Creates new instance of given class by calling either constructor
     * without parameters if there are no arguments or with given arguments.
     *
     * @param <T> Class type.
     * @param cls Class to be instantiated.
     * @param args Constructor arguments. Note that odd are classes of the
     *      arguments.
     * @return Created instance.
     * @throws Exception Thrown in case of any errors.
     */
    @SuppressWarnings({"MethodWithTooExceptionsDeclared", "TypeMayBeWeakened"})
    private <T> T createInstance(Class<T> cls, Object... args) throws Exception {
        T res;

        if (args.length > 0) {
            // Create classes and objects array from arguments.
            Class<?>[] argCls = new Class[args.length / 2];
            Object[] argVal = new Object[args.length / 2];

            for (int i = 0; i < args.length; i += 2) {
                argVal[i / 2] = args[i];
                argCls[i / 2] = (Class<?>)args[i + 1];
            }

            Constructor<T> ctor = cls.getConstructor(argCls);

            if (ctor == null) {
                throw new IllegalArgumentException("Failed to instantiate proxy (no matching constructor found " +
                    "in class): " + cls.getName());
            }

            res = ctor.newInstance(argVal);
        }
        else {
            res = cls.newInstance();
        }
        return res;
    }

    /**
     * Tests whether given specified object is a proxy one.
     *
     * @param obj Object to test.
     * @return {@code true} if given object is a proxy one created by this class.
     */
    public boolean isProxy(Object obj) {
        return obj instanceof ProxyObject;
    }

    /**
     * Adds new interception listener.
     *
     * @param l New listener.
     */
    public void addListener(GridProxyListener l) {
        lsnrs.add(l);
    }

    /**
     * Removes interception listener. If there is no such listener
     * defined than silently ignores it.
     *
     * @param l Listener to be removed.
     */
    public void removeListener(GridProxyListener l) {
        lsnrs.remove(l);
    }

    /**
     * Internal method invocation handler. It is called by JavaAssist proxy.
     *
     * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
     * @version 3.0.9c.19052011
     */
    private final class GridMethodHandler implements MethodHandler {
        /** Original object class. */
        private final Class<?> origCls;

        /**
         * Creates new instance of method interceptor.
         *
         * @param origCls Original class.
         */
        private GridMethodHandler(Class<?> origCls) {
            this.origCls = origCls;
        }

        /**
         * Receives all notification about methods completion.
         *
         * @param cls Callee class.
         * @param methodName Callee method name.
         * @param args Callee method apply parameters.
         * @param res Call result. Might be {@code null} if apply
         *      returned {@code null} or if exception happened.
         * @param exc Exception thrown by given method apply if any.
         */
        private void afterCall(Class<?> cls, String methodName, Object[] args, Object res, Throwable exc) {
            assert cls != null;
            assert methodName != null;

            for (GridProxyListener lsnr : lsnrs)
                lsnr.afterCall(cls, methodName, args, res, exc);
        }

        /**
         * Called right before any traced method apply.
         *
         * @param cls Callee class.
         * @param methodName Callee method name.
         * @param args Callee method parameters.
         */
        private void beforeCall(Class<?> cls, String methodName, Object[] args) {
            assert cls != null;
            assert methodName != null;

            for (GridProxyListener lsnr : lsnrs)
                lsnr.beforeCall(cls, methodName, args);
        }

        /** {@inheritDoc} */
        @Override @SuppressWarnings({"ProhibitedExceptionDeclared", "CatchGenericClass", "ProhibitedExceptionThrown"})
        @Nullable
        public Object invoke(Object proxy, Method origMethod, Method proxyMethod, Object[] args)
            throws Throwable {
            beforeCall(origCls, origMethod.getName(), args);

            // Somehow proxy method can be null.
            // We were also confused but this could happen in some cases - just ignore them.
            if (proxyMethod != null) {
                Object res = null;

                Throwable exc = null;

                try {
                    res = proxyMethod.invoke(proxy, args);
                }
                catch (Throwable t) {
                    exc = t;

                    throw t;
                }
                finally {
                    afterCall(origCls, origMethod.getName(), args, res, exc);
                }

                return res;
            }

            return null;
        }
    }
}
