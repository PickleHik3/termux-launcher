package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WidgetTokensTest {
    @Test public void theInsetIsSixteenOnTwoRowsAndTwelveOnOneRowAndAtOneByOne() {
        assertEquals(12, WidgetTokens.insetDp(BuiltinWidgetSpan.ONE_BY_ONE));
        assertEquals(12, WidgetTokens.insetDp(BuiltinWidgetSpan.TWO_BY_ONE));
        assertEquals(12, WidgetTokens.insetDp(BuiltinWidgetSpan.FOUR_BY_ONE));
        assertEquals(16, WidgetTokens.insetDp(BuiltinWidgetSpan.TWO_BY_TWO));
        assertEquals(16, WidgetTokens.insetDp(BuiltinWidgetSpan.FOUR_BY_TWO));
    }

    @Test public void theOneByOneFitUsesTheCompactInset() {
        assertEquals(WidgetTokens.INSET_COMPACT_DP, OneByOneFit.INSET_DP);
    }

    @Test public void theInnerRadiusIsConcentricAndNeverNegative() {
        assertEquals(8f, WidgetTokens.innerRadius(24f, 16f), 0f);
        assertEquals(12f, WidgetTokens.innerRadius(24f, 12f), 0f);
        assertEquals(0f, WidgetTokens.innerRadius(8f, 16f), 0f);
    }

    @Test public void gapsAreFourEightAndTwelve() {
        assertEquals(4, WidgetTokens.GAP_TIGHT_DP);
        assertEquals(8, WidgetTokens.GAP_DP);
        assertEquals(12, WidgetTokens.GAP_LOOSE_DP);
        assertEquals(48, WidgetTokens.TOUCH_TARGET_DP);
    }

    @Test public void theRolesFollowTheScaleAndNoneIsUnderTheFloor() {
        assertEquals(24f, WidgetTokens.Role.HEADLINE.sp, 0f);
        assertEquals(16f, WidgetTokens.Role.TITLE.sp, 0f);
        assertEquals(14f, WidgetTokens.Role.BODY.sp, 0f);
        assertEquals(12f, WidgetTokens.Role.LABEL.sp, 0f);
        assertEquals(11f, WidgetTokens.Role.LABEL_FORCED.sp, 0f);
        for (WidgetTokens.Role role : WidgetTokens.Role.values()) {
            if (role == WidgetTokens.Role.HERO) continue;
            assertTrue(role.name(), role.sp >= WidgetTokens.TEXT_FLOOR_SP);
        }
        assertEquals(28f, WidgetTokens.sizeDp(WidgetTokens.Role.BODY, 2f), 0.001f);
    }

    @Test public void theHeroIsFitBetweenItsSmallestAndLargestAndHoldsTheFloor() {
        assertEquals(24f, WidgetTokens.heroMinDp(BuiltinWidgetSpan.TWO_BY_ONE, 1f), 0f);
        assertEquals(36f, WidgetTokens.heroMinDp(BuiltinWidgetSpan.TWO_BY_TWO, 1f), 0f);
        assertEquals(33f, WidgetTokens.heroMinDp(BuiltinWidgetSpan.ONE_BY_ONE, 3f), 0.001f);
        assertEquals(57f, WidgetTokens.heroMaxDp(), 0f);
    }

    @Test public void theTypeScaleIsTheSystemScaleTimesTheSharedTextSize() {
        assertEquals(1.95f, WidgetTokens.typeScale(1.3f, 1.5f), 0.001f);
        com.termux.app.theme.TextScale.set(1.5f);
        try {
            assertEquals(21f, WidgetTokens.textSp(WidgetTokens.Role.BODY), 0.001f);
        } finally {
            com.termux.app.theme.TextScale.set(1f);
        }
    }
}
