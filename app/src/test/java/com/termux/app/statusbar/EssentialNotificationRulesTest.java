package com.termux.app.statusbar;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EssentialNotificationRulesTest {

    @Test
    public void emptyRuleListLeavesTheFeatureIdle() {
        assertTrue(EssentialNotificationRules.parse(null).isEmpty());
        assertTrue(EssentialNotificationRules.parse("").isEmpty());
        assertTrue(EssentialNotificationRules.parse("[]").isEmpty());
        assertNull(EssentialNotificationRules.firstMatch(Collections.emptyList(),
            "com.whatsapp", "Amma", "Flight lands 9:40pm"));
    }

    @Test
    public void aRuleWithNeitherPackageNorMatchIsRejected() {
        EssentialNotificationRule rule = new EssentialNotificationRule("x", "", "", false);
        assertFalse(rule.isUsable());
        assertFalse(rule.matches("com.whatsapp", "Amma", "hello"));
    }

    @Test
    public void packageOnlyRuleMatchesAnyContentFromThatApp() {
        EssentialNotificationRule rule = new EssentialNotificationRule("p", "com.whatsapp", "", false);
        assertTrue(rule.matches("com.whatsapp", "Amma", "anything"));
        assertTrue(rule.matches("com.whatsapp", null, null));
        assertFalse(rule.matches("com.slack", "Amma", "anything"));
    }

    @Test
    public void substringMatchesTitleOrBodyCaseInsensitively() {
        EssentialNotificationRule rule = new EssentialNotificationRule("s", "", "otp", false);
        assertTrue(rule.matches("com.bank", "HDFC", "OTP 449213 valid 10 minutes"));
        assertTrue(rule.matches("com.bank", "Your OTP", null));
        assertFalse(rule.matches("com.bank", "HDFC", "balance updated"));
    }

    @Test
    public void packageAndSubstringMustBothHold() {
        EssentialNotificationRule rule =
            new EssentialNotificationRule("b", "com.bank", "otp", true);
        assertTrue(rule.matches("com.bank", "HDFC", "OTP 449213"));
        assertFalse(rule.matches("com.other", "HDFC", "OTP 449213"));
        assertFalse(rule.matches("com.bank", "HDFC", "statement ready"));
        assertTrue(rule.clearOnDismiss);
    }

    @Test
    public void firstMatchWins() {
        List<EssentialNotificationRule> rules = Arrays.asList(
            new EssentialNotificationRule("a", "com.bank", "otp", true),
            new EssentialNotificationRule("b", "com.bank", "", false));
        EssentialNotificationRule matched =
            EssentialNotificationRules.firstMatch(rules, "com.bank", "HDFC", "OTP 1234");
        assertNotNull(matched);
        assertEquals("a", matched.id);
        assertTrue(matched.clearOnDismiss);

        EssentialNotificationRule fallback =
            EssentialNotificationRules.firstMatch(rules, "com.bank", "HDFC", "statement");
        assertNotNull(fallback);
        assertEquals("b", fallback.id);
    }

    @Test
    public void serializeRoundTripsAndDropsUnusableEntries() {
        List<EssentialNotificationRule> rules = Arrays.asList(
            new EssentialNotificationRule("a", "com.whatsapp", "amma", false),
            new EssentialNotificationRule("b", "", "otp", true));
        List<EssentialNotificationRule> parsed =
            EssentialNotificationRules.parse(EssentialNotificationRules.serialize(rules));
        assertEquals(2, parsed.size());
        assertEquals("com.whatsapp", parsed.get(0).packageName);
        assertEquals("otp", parsed.get(1).match);
        assertTrue(parsed.get(1).clearOnDismiss);

        // Unusable and id-less entries never reach the matcher.
        assertTrue(EssentialNotificationRules.parse(
            "[{\"id\":\"x\",\"package\":\"\",\"match\":\"\"},{\"package\":\"com.a\"}]").isEmpty());
        assertTrue(EssentialNotificationRules.parse("not json").isEmpty());
    }

    @Test
    public void duplicateIdsCollapseToOne() {
        List<EssentialNotificationRule> parsed = EssentialNotificationRules.parse(
            "[{\"id\":\"a\",\"match\":\"otp\"},{\"id\":\"a\",\"match\":\"pin\"}]");
        assertEquals(1, parsed.size());
        assertEquals("otp", parsed.get(0).match);
    }

    @Test
    public void derivedIdIsStableAndCaseInsensitive() {
        assertEquals(EssentialNotificationRules.deriveId("com.Bank", "OTP"),
            EssentialNotificationRules.deriveId(" com.bank ", "otp "));
        assertFalse(EssentialNotificationRules.deriveId("com.bank", "otp")
            .equals(EssentialNotificationRules.deriveId("com.bank", "pin")));
    }

    @Test
    public void findByIdIsCaseInsensitiveAndNullSafe() {
        List<EssentialNotificationRule> rules = Collections.singletonList(
            new EssentialNotificationRule("Ab12", "com.a", "", false));
        assertNotNull(EssentialNotificationRules.findById(rules, "ab12"));
        assertNull(EssentialNotificationRules.findById(rules, "zz"));
        assertNull(EssentialNotificationRules.findById(rules, null));
    }

    @Test
    public void describeReportsTheStackCeiling() {
        assertEquals(TopPaneSlotMode.MAX_PINNED,
            EssentialNotificationRules.describe(Collections.emptyList()).optInt("maxPinned"));
        assertEquals(0, EssentialNotificationRules.describe(Collections.emptyList()).optInt("count"));
    }
}
