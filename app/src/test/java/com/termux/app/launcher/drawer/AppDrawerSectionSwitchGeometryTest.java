package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerSectionSwitchGeometryTest {

    private static final float D = 2f;
    private static final float EPS = 1e-3f;

    private static AppDrawerSectionSwitchGeometry.Layout layout(float apps, float cats, float max,
                                                                boolean rtl) {
        AppDrawerSectionSwitchGeometry.Layout l = new AppDrawerSectionSwitchGeometry.Layout();
        AppDrawerSectionSwitchGeometry.compute(l, apps, cats, D, max, rtl);
        return l;
    }

    @Test public void widthIsTwoSegmentsInsideTheBodyInset() {
        AppDrawerSectionSwitchGeometry.Layout l = layout(100f, 160f, 0f, false);
        float pad = AppDrawerSectionSwitchGeometry.SEGMENT_PADDING_DP * D;
        float inset = AppDrawerSectionSwitchGeometry.BODY_INSET_DP * D;
        assertEquals(2 * inset + 100f + 160f + 4 * pad, l.width, EPS);
        assertEquals(AppDrawerSectionSwitchGeometry.HEIGHT_DP * D, l.height, EPS);
        assertEquals(inset, l.segmentLeft[0], EPS);
        assertEquals(l.segmentRight[0], l.segmentLeft[1], EPS);
        assertEquals(l.width - inset, l.segmentRight[1], EPS);
        assertEquals(l.height - inset, l.capsuleBottom, EPS);
    }

    @Test public void capsuleInterpolatesBetweenSegmentsWithUnequalWidths() {
        AppDrawerSectionSwitchGeometry.Layout l = layout(100f, 160f, 0f, false);
        assertEquals(l.segmentLeft[0], AppDrawerSectionSwitchGeometry.capsuleLeft(l, 0f), EPS);
        assertEquals(l.segmentRight[0], AppDrawerSectionSwitchGeometry.capsuleRight(l, 0f), EPS);
        assertEquals(l.segmentLeft[1], AppDrawerSectionSwitchGeometry.capsuleLeft(l, 1f), EPS);
        assertEquals(l.segmentRight[1], AppDrawerSectionSwitchGeometry.capsuleRight(l, 1f), EPS);
        assertEquals((l.segmentLeft[0] + l.segmentLeft[1]) / 2f,
            AppDrawerSectionSwitchGeometry.capsuleLeft(l, 0.5f), EPS);
        assertEquals((l.segmentRight[0] + l.segmentRight[1]) / 2f,
            AppDrawerSectionSwitchGeometry.capsuleRight(l, 0.5f), EPS);
        assertEquals(l.segmentLeft[1], AppDrawerSectionSwitchGeometry.capsuleLeft(l, 7f), EPS);
    }

    @Test public void labelWeightsCrossFade() {
        assertEquals(1f, AppDrawerSectionSwitchGeometry.labelWeight(0, 0f), EPS);
        assertEquals(0f, AppDrawerSectionSwitchGeometry.labelWeight(1, 0f), EPS);
        assertEquals(0.5f, AppDrawerSectionSwitchGeometry.labelWeight(1, 0.5f), EPS);
        assertEquals(1f, AppDrawerSectionSwitchGeometry.labelWeight(1, 1f), EPS);
    }

    @Test public void hitPicksTheLabelAndRejectsOutside() {
        AppDrawerSectionSwitchGeometry.Layout l = layout(100f, 160f, 0f, false);
        assertEquals(0, AppDrawerSectionSwitchGeometry.hit(l, 1f, 10f));
        assertEquals(0, AppDrawerSectionSwitchGeometry.hit(l, l.segmentRight[0] - 1f, 10f));
        assertEquals(1, AppDrawerSectionSwitchGeometry.hit(l, l.segmentLeft[1] + 1f, 10f));
        assertEquals(1, AppDrawerSectionSwitchGeometry.hit(l, l.width - 1f, 10f));
        assertEquals(-1, AppDrawerSectionSwitchGeometry.hit(l, -1f, 10f));
        assertEquals(-1, AppDrawerSectionSwitchGeometry.hit(l, l.width + 1f, 10f));
        assertEquals(-1, AppDrawerSectionSwitchGeometry.hit(l, 10f, l.height + 1f));
    }

    @Test public void rtlMirrorsSegmentsAndHits() {
        AppDrawerSectionSwitchGeometry.Layout l = layout(100f, 160f, 0f, true);
        assertTrue(l.segmentLeft[1] < l.segmentLeft[0]);
        assertEquals(1, AppDrawerSectionSwitchGeometry.hit(l, 1f, 10f));
        assertEquals(0, AppDrawerSectionSwitchGeometry.hit(l, l.width - 1f, 10f));
    }

    @Test public void capAboveContentShrinksPaddingBeforeLabels() {
        AppDrawerSectionSwitchGeometry.Layout wide = layout(100f, 160f, 0f, false);
        float cap = wide.width - 8f;
        AppDrawerSectionSwitchGeometry.Layout l = layout(100f, 160f, cap, false);
        assertEquals(cap, l.width, EPS);
        assertEquals(100f, l.labelWidth[0], EPS);
        assertEquals(160f, l.labelWidth[1], EPS);
        assertTrue(l.padding < AppDrawerSectionSwitchGeometry.SEGMENT_PADDING_DP * D);
    }

    @Test public void largeFontCapsTotalWidthAndShortensLabels() {
        float cap = 200f;
        AppDrawerSectionSwitchGeometry.Layout l = layout(300f, 400f, cap, false);
        assertEquals(cap, l.width, EPS);
        assertEquals(AppDrawerSectionSwitchGeometry.COMPACT_PADDING_DP * D, l.padding, EPS);
        assertTrue(l.labelWidth[0] < 300f && l.labelWidth[1] < 400f);
        assertEquals(300f / 400f, l.labelWidth[0] / l.labelWidth[1], 1e-4f);
    }

    @Test public void absurdlySmallCapNeverGoesNegative() {
        AppDrawerSectionSwitchGeometry.Layout l = layout(300f, 400f, 10f, false);
        assertTrue(l.labelWidth[0] >= 0f && l.labelWidth[1] >= 0f);
    }
}
