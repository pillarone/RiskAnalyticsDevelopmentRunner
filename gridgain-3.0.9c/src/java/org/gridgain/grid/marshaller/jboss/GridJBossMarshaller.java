// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.marshaller.jboss;

import org.gridgain.grid.*;
import org.gridgain.grid.marshaller.*;
import org.gridgain.grid.typedef.internal.*;
import org.jboss.serial.io.*;
import java.io.*;

/**
 * Implementation of {@link GridMarshaller} based on JBoss serialization.
 * <p>
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This marshaller has no mandatory configuration parameters.
 * <h2 class="header">Java Example</h2>
 * GridJBossMarshaller is used by default but it can be explicitly configured.
 * <pre name="code" class="java">
 * GridJBossMarshaller marshaller = new GridJBossMarshaller();
 *
 * GridConfigurationAdapter cfg = new GridConfigurationAdapter();
 *
 * // Override marshaller.
 * cfg.setMarshaller(marshaller);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * <h2 class="header">Spring Example</h2>
 * GridJBossMarshaller can be configured from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.custom.cfg" class="org.gridgain.grid.GridConfigurationAdapter" singleton="true"&gt;
 *     ...
 *     &lt;property name="marshaller"&gt;
 *         &lt;bean class="org.gridgain.grid.marshaller.jboss.GridJBossMarshaller"/&gt;
 *     &lt;/property&gt;
 *     ...
 * &lt;/bean&gt;
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 * <h2 class="header">Injection Example</h2>
 * GridJBossMarshaller can be injected in users task, job or SPI as following:
 * <pre name="code" class="java">
 * public class MyGridJob implements GridJob {
 *     ...
 *     &#64;GridMarshallerResource
 *     private GridMarshaller marshaller;
 *     ...
 * }
 * </pre>
 * or
 * <pre name="code" class="java">
 * public class MyGridJob implements GridJob {
 *     ...
 *     private GridMarshaller marshaller;
 *     ...
 *     &#64;GridMarshallerResource
 *     public void setMarshaller(GridMarshaller marshaller) {
 *         this.marshaller = marshaller;
 *     }
 *     ...
 * }
 * </pre>
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridJBossMarshaller implements GridMarshaller {
    /** {@inheritDoc} */
    @Override public void marshal(Object obj, OutputStream out) throws GridException {
        assert out != null;

        JBossObjectOutputStream objOut = null;

        try {
            objOut = new GridJBossMarshallerObjectOutputStream(new GridJbossMarshallerOutputStreamWrapper(out));

            // Make sure that we serialize only task, without class loader.
            objOut.writeObject(obj);

            objOut.flush();
        }
        catch (IOException e) {
            throw new GridException("Failed to serialize object: " + obj, e);
        }
        finally{
            U.close(objOut, null);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public <T> T unmarshal(InputStream in, ClassLoader clsLdr) throws GridException {
        assert in != null;
        assert clsLdr != null;

        ObjectInputStream objIn = null;

        try {
            objIn = new GridJBossMarshallerObjectInputStream(new GridJbossMarshallerInputStreamWrapper(in), clsLdr);

            return (T)objIn.readObject();
        }
        catch (ClassNotFoundException e) {
            throw new GridException("Failed to deserialize object with given class loader: " + clsLdr, e);
        }
        catch (IOException e) {
            throw new GridException("Failed to deserialize object with given class loader: " + clsLdr, e);
        }
        finally{
            U.close(objIn, null);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJBossMarshaller.class, this);
    }
}
