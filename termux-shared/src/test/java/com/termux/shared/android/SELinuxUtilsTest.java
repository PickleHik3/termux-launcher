package com.termux.shared.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;

/** Tests the classification of the SELinux domain the app is running in. */
public class SELinuxUtilsTest {

    private static final String UNTRUSTED_APP_27 = "u:r:untrusted_app_27:s0:c123,c256,c512,c768";

    private static final String UNTRUSTED_APP = "u:r:untrusted_app:s0:c123,c256,c512,c768";

    @Test
    public void getDomain_readsTheThirdField() {
        assertEquals("untrusted_app_27", SELinuxUtils.getDomain(UNTRUSTED_APP_27));
        assertEquals("untrusted_app", SELinuxUtils.getDomain(UNTRUSTED_APP));
        assertEquals("untrusted_app_27", SELinuxUtils.getDomain("u:r:untrusted_app_27:s0"));
    }

    @Test
    public void getDomain_isNullForAContextItCannotRead() {
        assertNull(SELinuxUtils.getDomain(null));
        assertNull(SELinuxUtils.getDomain(""));
        assertNull(SELinuxUtils.getDomain("u:r"));
        assertNull(SELinuxUtils.getDomain("u:r::s0"));
    }

    @Test
    public void execution_isAllowedInTheLegacyDomains() {
        assertFalse(SELinuxUtils.isAppDataFileExecutionRestricted(Build.VERSION_CODES.TIRAMISU, UNTRUSTED_APP_27));
        assertFalse(SELinuxUtils.isAppDataFileExecutionRestricted(Build.VERSION_CODES.TIRAMISU, "u:r:untrusted_app_25:s0:c1"));
    }

    @Test
    public void execution_isRestrictedInTheModernDomains() {
        assertTrue(SELinuxUtils.isAppDataFileExecutionRestricted(Build.VERSION_CODES.TIRAMISU, UNTRUSTED_APP));
        assertTrue(SELinuxUtils.isAppDataFileExecutionRestricted(Build.VERSION_CODES.TIRAMISU, "u:r:untrusted_app_29:s0:c1"));
        assertTrue(SELinuxUtils.isAppDataFileExecutionRestricted(Build.VERSION_CODES.TIRAMISU, "u:r:untrusted_app_32:s0:c1"));
    }

    @Test
    public void execution_isNeverRestrictedBeforeAndroid10() {
        assertFalse(SELinuxUtils.isAppDataFileExecutionRestricted(Build.VERSION_CODES.P, UNTRUSTED_APP));
    }

    /** A device we cannot classify must never be told its working install is broken. */
    @Test
    public void execution_isNotRestrictedWhenTheDomainIsUnknown() {
        assertFalse(SELinuxUtils.isAppDataFileExecutionRestricted(Build.VERSION_CODES.TIRAMISU, null));
        assertFalse(SELinuxUtils.isAppDataFileExecutionRestricted(Build.VERSION_CODES.TIRAMISU, "u:r:magisk_app:s0:c1"));
        assertFalse(SELinuxUtils.isAppDataFileExecutionRestricted(Build.VERSION_CODES.TIRAMISU, "u:r:shell:s0"));
    }
}
