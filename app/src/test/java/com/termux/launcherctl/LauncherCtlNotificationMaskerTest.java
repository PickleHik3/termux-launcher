package com.termux.launcherctl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LauncherCtlNotificationMaskerTest {
    private static final String M = LauncherCtlNotificationMasker.MASK;

    @Test
    public void masksACodeBesideAKeyword() {
        assertEquals("Your verification code is " + M + ".",
            LauncherCtlNotificationMasker.mask("Your verification code is 482913."));
        assertEquals(M + " is your OTP", LauncherCtlNotificationMasker.mask("123456 is your OTP"));
        assertEquals("PIN: " + M, LauncherCtlNotificationMasker.mask("PIN: 4821"));
        assertEquals("Passcode " + M, LauncherCtlNotificationMasker.mask("Passcode 12345678"));
    }

    @Test
    public void masksEveryCodeInTheText() {
        assertEquals("code " + M + " or code " + M,
            LauncherCtlNotificationMasker.mask("code 111111 or code 222222"));
    }

    @Test
    public void masksACodeSplitByASpaceOrHyphen() {
        assertEquals("Your code: " + M, LauncherCtlNotificationMasker.mask("Your code: 123 456"));
        assertEquals("Your code: " + M, LauncherCtlNotificationMasker.mask("Your code: 123-456"));
    }

    @Test
    public void leavesNumbersWithNoKeywordNearby() {
        assertEquals("Order 12345678 shipped", LauncherCtlNotificationMasker.mask("Order 12345678 shipped"));
        assertEquals("Meeting at 1430", LauncherCtlNotificationMasker.mask("Meeting at 1430"));
        assertEquals("Total 4821 KWD", LauncherCtlNotificationMasker.mask("Total 4821 KWD"));
    }

    @Test
    public void leavesTooShortTooLongAndEmbeddedRuns() {
        assertEquals("code 123", LauncherCtlNotificationMasker.mask("code 123"));
        assertEquals("code 123456789", LauncherCtlNotificationMasker.mask("code 123456789"));
        assertEquals("code A1234B", LauncherCtlNotificationMasker.mask("code A1234B"));
        assertEquals("code 1234.56", LauncherCtlNotificationMasker.mask("code 1234.56"));
        assertEquals("code at 10:1234", LauncherCtlNotificationMasker.mask("code at 10:1234"));
    }

    @Test
    public void aKeywordFarAwayDoesNotCount() {
        String far = "Use the code below when asked" + " ".repeat(LauncherCtlNotificationMasker.NEAR + 5) + "98765";
        assertEquals(far, LauncherCtlNotificationMasker.mask(far));
    }

    @Test
    public void keywordsMatchWholeWordsOnly() {
        assertEquals("Encoded 5555 pinned", LauncherCtlNotificationMasker.mask("Encoded 5555 pinned"));
    }

    @Test
    public void passesNullAndShortTextThrough() {
        assertNull(LauncherCtlNotificationMasker.mask(null));
        assertEquals("", LauncherCtlNotificationMasker.mask(""));
        assertEquals("pin", LauncherCtlNotificationMasker.mask("pin"));
    }
}
