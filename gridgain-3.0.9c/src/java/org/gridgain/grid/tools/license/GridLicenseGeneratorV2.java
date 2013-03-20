// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.tools.license;

import org.apache.commons.beanutils.*;
import org.apache.commons.beanutils.converters.*;
import org.gridgain.grid.*;
import org.gridgain.grid.kernal.controllers.license.*;
import org.gridgain.grid.kernal.controllers.license.impl.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;

import javax.xml.bind.*;
import java.io.*;
import java.security.*;
import java.text.*;
import java.util.*;

/**
 * GridGain license generator.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridLicenseGeneratorV2 {
    /** Property key-value separator. */
    private static final char SEPARATOR = '=';

    /** Default license version. */
    private static final String LIC_VER = "2.0";

    /** Available parameters with mapping to license properties. */
    private static final Map<String, String> params = new HashMap<String, String>();

    /** Key store. */
    private static final String KEY_STORE = "gglicense.store";

    /** Key store password. */
    private static final String KEY_STORE_PWD = "gr1dga1n";

    /** Key alias. */
    private static final String KEY_ALIAS = "gglicense";

    /** Default license file name. */
    private static final String DFLT_LIC_FILE_NAME = "gridgain-license.xml";

    /** */
    private static final String FS = File.separator;

    /**
     * Converter registration and parameters initialization.
     */
    static {
        DateTimeConverter c = new DateConverter();

        c.setPatterns(new String[] {"MM/dd/yy", "MM/dd/yyyy"});

        ConvertUtils.register(c, Date.class);

        ConvertUtils.register(new AbstractConverter() {
            @SuppressWarnings({"unchecked"})
            @Override protected Object convertToType(Class cls, Object o) throws Throwable {
                assert GridLicenseType.class.isAssignableFrom(cls);
                assert o instanceof String;

                return Enum.valueOf(cls, (String)o);
            }

            @Override protected Class getDefaultType() {
                return GridLicenseType.class;
            }
        }, GridLicenseType.class);

        params.put("user-org", "userOrganization");
        params.put("user-www", "userWww");
        params.put("user-email", "userEmail");
        params.put("user-name", "userName");
        params.put("version-regexp", "versionRegexp");
        params.put("type", "type");
        params.put("expire-date", "expireDate");
        params.put("issue-date", "issueDate");
        params.put("license-note", "licenseNote");
        params.put("max-nodes", "maxNodes");
        params.put("max-cpus", "maxCpus");
        params.put("max-uptime", "maxUpTime"); // In minutes.
        params.put("grace-period", "gracePeriod"); // In minutes.
        params.put("max-computers", "maxComputers");
        params.put("disabled-subsystems", "disabledSubsystems");
        params.put("file", null);
    }

    /**
     * Generate license by program parameters.
     * Example set of parameters: "user-org=User Organization" "user-www=www.user.org"
     * "user-email=info@user-org.org" "type=ENT" "issue-date=08/25/2011" "expire-date=08/26/2011" "max-cpus=40"
     * "max-nodes=40"
     *
     * @param args Program parameters.
     * @throws GridException If any exception occurs while license generating.
     */
    public static void main(String[] args) throws GridException {
        Map<String, String> argMap = getArgumentsMap(args);

        if (argMap.isEmpty())
            throw new GridException("User arguments cannot be empty.");

        GridLicenseV2 lic = buildLicense(argMap);

        saveLicense(lic, argMap);
    }

    /**
     * Ensures singleton.
     */
    private GridLicenseGeneratorV2() {
        // No-op.
    }

    /**
     * Generates and saves license file.
     *
     * @param lic License.
     * @param args User arguments.
     * @throws GridException If any exception occurs while license generation or saving.
     */
    private static void saveLicense(GridLicenseV2 lic, Map<String, String> args) throws GridException {
        assert lic != null;
        assert args != null;

        String path = args.get("file");

        path = U.getGridGainHome() + FS + "keystore" + FS + (F.isEmpty(path) ? DFLT_LIC_FILE_NAME : path);

        try {
            Marshaller marsh = JAXBContext.newInstance(GridLicenseV2Adapter.class).createMarshaller();

            marsh.setProperty("jaxb.encoding", "UTF-8");
            marsh.setProperty("jaxb.formatted.output", true);

            marsh.marshal(lic, new File(path));

            System.out.println("License was generated: " + path);
        }
        catch (Exception e) {
            throw new GridException("Failed to generate and store license in file: " + path, e);
        }
    }

    /**
     * Build license.
     *
     * @param args User arguments.
     * @return License.
     * @throws GridException If any exception occurs while license building.
     */
    private static GridLicenseV2 buildLicense(Map<String, String> args) throws GridException {
        assert args != null;

        GridLicenseV2Adapter lic = new GridLicenseV2Adapter();

        setUserArguments(lic, args);
        setSystemProperties(lic);

        return lic;
    }

    /**
     * Sets license user properties.
     *
     * @param lic License.
     * @param args User arguments.
     * @throws GridException If property set failed.
     */
    private static void setUserArguments(GridLicenseV2Adapter lic, Map<String, String> args) throws GridException {
        assert lic != null;
        assert args != null;

        if (!F.isEmpty(args)) {
            for (Map.Entry<String, String> e : args.entrySet()) {
                String prop = params.get(e.getKey());

                if (F.isEmpty(prop))
                    continue;

                String val = e.getValue();

                assert !F.isEmpty(val);

                try {
                    BeanUtils.setProperty(lic, prop, val);
                }
                catch (Exception ex) {
                    throw new GridException("Failed to set property value [prop=" + prop + ", val=" + val + ']',
                        ex);
                }
            }
        }
    }

    /**
     * Sets license system properties.
     *
     * @param lic License.
     * @throws GridException If property set failed.
     */
    private static void setSystemProperties(GridLicenseV2Adapter lic) throws GridException {
        assert lic != null;

        lic.setId(UUID.randomUUID());
        lic.setVersion(LIC_VER);

        try {
            lic.setSignature(GridLicenseUtil.createSignatureV2(getKey(), lic));
        }
        catch (GeneralSecurityException e) {
            throw new GridException("Failed to create signature for license:" + lic, e);
        }
    }

    /**
     * Load private key from key store.
     *
     * @return Private key.
     * @throws GridException If key loading failed.
     */
    private static PrivateKey getKey() throws GridException {
        KeyStore ks;

        try {
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
        }
        catch (Exception e) {
            throw new GridException("Failed to get key store instance.", e);    
        }

        File ksFile = new File(U.getGridGainHome() + FS + "keystore", KEY_STORE);

        if (!ksFile.exists() || !ksFile.canRead())
            throw new GridException("Key store file doesn't exist or is not readable: " + ksFile.getAbsolutePath());

        InputStream in;

        try {
            in = new FileInputStream(ksFile);
        }
        catch (IOException e) {
            throw new GridException("Failed to open key store file: " + ksFile.getAbsolutePath(), e);
        }

        try {
            ks.load(in, KEY_STORE_PWD.toCharArray());

            return (PrivateKey)ks.getKey(KEY_ALIAS, KEY_STORE_PWD.toCharArray());
        }
        catch (Exception e) {
            throw new GridException("Failed to get private key from key store [keystore=" + KEY_STORE +
                ", alias=" + KEY_ALIAS + ']', e);
        }
        finally {
            U.close(in, null);
        }
    }

    /**
     * Gets arguments map from program arguments.
     *
     * @param args Program arguments.
     * @return Arguments map.
     * @throws GridException If any program argument is invalid.
     */
    private static Map<String, String> getArgumentsMap(String[] args) throws GridException {
        assert args != null;

        Map<String, String> map = new HashMap<String, String>();

        // Put defaults in (EVAL license).
        map.put("type", "EVL");
        map.put("issue-org", "GridGain Systems");
        map.put("user-org", "GridGain Evaluation");
        map.put("license-note", "Internal Evaluation Only");
        map.put("issue-date", DateFormat.getDateInstance(DateFormat.SHORT).format(new Date()));
        map.put("max-nodes", "2");
        map.put("max-cpus", "8");

        if (!F.isEmpty(args)) {
            Collection<String> usedKeys = new HashSet<String>();

            for (String s : args) {
                int idx = s.indexOf(SEPARATOR);

                if (idx <= 0)
                    throw new GridException("Invalid key-value argument [arg=" + s + "]");

                String key = s.substring(0, idx);

                if (!params.containsKey(key))
                    throw new GridException("Unknown argument: " + key);

                if (usedKeys.contains(key))
                    throw new GridException("Duplicate argument: " + key);

                usedKeys.add(key);

                String val = s.substring(idx + 1);

                if (val.isEmpty())
                    throw new GridException("Argument value can't be empty [key=" + key + "]");

                map.put(key, val);
            }
        }

        return map;
    }
}
