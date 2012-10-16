// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.controllers.license;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import java.io.*;
import java.security.*;

/**
 * GridGain license util.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.0.9c.19052011
 */
public class GridLicenseUtil {
    /** Date format pattern. */
    private static final String DATE_PTRN = "MM/dd/yyyy";

    /** Signature algorithm. */
    private static final String SIGN_ALG = "SHA1withDSA";

    /** Security provider. */
    private static final String PRV = "SUN";

    /**
     * Ensures singleton.
     */
    private GridLicenseUtil() {
        // No-op.
    }

    /**
     * Gets license version 1 data for sign.
     *
     * @param lic GridGain license version 1.
     * @return Data for sign.
     */
    public static byte[] getDataV1ForSign(GridLicenseV1 lic) {
        assert lic != null;

        try {
            return new SB()
                .a(lic.getVersion())
                .a(lic.getId())
                .a(lic.getIssueDate() != null ? U.format(lic.getIssueDate(), DATE_PTRN) : "")
                .a(lic.getIssueOrganization())
                .a(lic.getUserOrganization())
                .a(lic.getUserWww())
                .a(lic.getUserEmail())
                .a(lic.getType())
                .a(lic.getExpireDate() != null ? U.format(lic.getExpireDate(), DATE_PTRN) : "")
                .a(lic.getMeteringKey1())
                .a(lic.getMeteringKey2())
                .a(lic.getMaxCpus())
                .a(lic.getMaxNodes())
                .toString()
                .getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new GridRuntimeException(e);
        }
    }

    /**
     * Gets license version 2 data for sign.
     *
     * @param lic GridGain license version 2.
     * @return Data for sign.
     */
    public static byte[] getDataV2ForSign(GridLicenseV2 lic) {
        assert lic != null;

        try {
            return new SB()
                .a(lic.getVersion())
                .a(lic.getId())
                .a(lic.getIssueDate() != null ? U.format(lic.getIssueDate(), DATE_PTRN) : "")
                .a(lic.getIssueOrganization())
                .a(lic.getUserOrganization())
                .a(lic.getUserWww())
                .a(lic.getUserEmail())
                .a(lic.getUserName())
                .a(lic.getLicenseNote())
                .a(lic.getVersionRegexp())
                .a(lic.getType())
                .a(lic.getExpireDate() != null ? U.format(lic.getExpireDate(), DATE_PTRN) : "")
                .a(lic.getMeteringKey1())
                .a(lic.getMeteringKey2())
                .a(lic.getMaxCpus())
                .a(lic.getMaxComputers())
                .a(lic.getMaxNodes())
                .a(lic.getMaxUpTime())
                .toString()
                .getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new GridRuntimeException(e);
        }
    }

    /**
     * Verifies license version 1 signature.
     *
     * @param key Public key.
     * @param lic License version 1.
     * @return {@code true} if signature was verified, {@code false} - otherwise.
     * @throws GeneralSecurityException If any exception occurs while verifying.
     */
    public static boolean verifySignatureV1(PublicKey key, GridLicenseV1 lic) throws GeneralSecurityException {
        assert key != null;
        assert lic != null;

        byte[] data = getDataV1ForSign(lic);
        byte[] sign = U.hexString2ByteArray(lic.getSignature());

        return verifySignature(SIGN_ALG, PRV, key, data, sign);
    }

    /**
     * Verifies license version 2 signature.
     *
     * @param key Public key.
     * @param lic License version 2.
     * @return {@code true} if signature was verified, {@code false} - otherwise.
     * @throws GeneralSecurityException If any exception occurs while verifying.
     */
    public static boolean verifySignatureV2(PublicKey key, GridLicenseV2 lic) throws GeneralSecurityException {
        assert key != null;
        assert lic != null;

        byte[] data = getDataV2ForSign(lic);
        byte[] sign = U.hexString2ByteArray(lic.getSignature());

        return verifySignature(SIGN_ALG, PRV, key, data, sign);
    }

    /**
     * Verifies signature for data.
     *
     * @param alg Algorithm.
     * @param prv Provider.
     * @param key Public key.
     * @param data Data.
     * @param sign Signature.
     * @return {@code true} if signature was verified, {@code false} - otherwise.
     * @throws GeneralSecurityException If any exception occurs while verifying.
     */
    public static boolean verifySignature(String alg, String prv, PublicKey key, byte[] data, byte[] sign)
        throws GeneralSecurityException {
        assert !F.isEmpty(alg);
        assert !F.isEmpty(prv);
        assert key != null;
        assert data != null;
        assert sign != null;

        Signature sign0 = Signature.getInstance(alg, prv);

        sign0.initVerify(key);
        sign0.update(data);

        return sign0.verify(sign);
    }

    /**
     * Create signature for license version 1.
     *
     * @param key Private key.
     * @param lic License version 1.
     * @return Signature.
     * @throws GeneralSecurityException If any exception occurs while signature creation.
     */
    public static String createSignatureV1(PrivateKey key, GridLicenseV1 lic) throws GeneralSecurityException {
        assert key != null;
        assert lic != null;

        byte[] data = getDataV1ForSign(lic);
        byte[] sign = createSignature(SIGN_ALG, PRV, key, data);

        return U.byteArray2HexString(sign);
    }

    /**
     * Create signature for license version 2.
     *
     * @param key Private key.
     * @param lic License version 2.
     * @return Signature.
     * @throws GeneralSecurityException If any exception occurs while signature creation.
     */
    public static String createSignatureV2(PrivateKey key, GridLicenseV2 lic) throws GeneralSecurityException {
        assert key != null;
        assert lic != null;

        byte[] data = getDataV2ForSign(lic);
        byte[] sign = createSignature(SIGN_ALG, PRV, key, data);

        return U.byteArray2HexString(sign);
    }

    /**
     * Creates signature for data.
     *
     * @param alg Algorithm.
     * @param prv Provider.
     * @param key Private key.
     * @param data Data.
     * @return Signature.
     * @throws GeneralSecurityException If any exception occurs while signature creation.
     */
    public static byte[] createSignature(String alg, String prv, PrivateKey key, byte[] data)
        throws GeneralSecurityException {
        assert !F.isEmpty(alg);
        assert !F.isEmpty(prv);
        assert key != null;
        assert data != null;

        Signature sign = Signature.getInstance(alg, prv);

        sign.initSign(key);
        sign.update(data);

        return sign.sign();
    }
}
