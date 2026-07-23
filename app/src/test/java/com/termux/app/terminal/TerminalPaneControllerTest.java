package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;
import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalPaneControllerTest {

    @Test
    public void clampFirstWeight_keepsBothPanesAtLeastEighteenPercent() {
        assertEquals(.36f, TerminalPaneController.clampFirstWeight(2f, 0f), .001f);
        assertEquals(1.64f, TerminalPaneController.clampFirstWeight(2f, 2f), .001f);
        assertEquals(1.1f, TerminalPaneController.clampFirstWeight(2f, 1.1f), .001f);
    }

    @Test
    public void interactionOverlay_isAbsentForLonePaneButPersistsWhileMaximized() {
        assertFalse(TerminalPaneController.shouldShowInteractionOverlay(1, false));
        assertTrue(TerminalPaneController.shouldShowInteractionOverlay(2, false));
        assertTrue(TerminalPaneController.shouldShowInteractionOverlay(1, true));
    }

    @Test
    public void snapFirstWeightToCell_alignsFinalDividerAndKeepsMinimumPaneSize() {
        assertEquals(1.0f,
            TerminalPaneController.snapFirstWeightToCell(2f, 1000f, .97f, 100f), .001f);
        assertEquals(.36f,
            TerminalPaneController.snapFirstWeightToCell(2f, 1000f, .1f, 100f), .001f);
        assertEquals(1.64f,
            TerminalPaneController.snapFirstWeightToCell(2f, 1000f, 1.9f, 100f), .001f);
    }

    @Test
    public void touchedBorderIndex_preservesOriginalPaneOwnershipAtSharedDivider() {
        java.util.List<RectF> panes = Arrays.asList(
            new RectF(0f, 0f, 499f, 500f),
            new RectF(501f, 0f, 1000f, 500f));

        assertEquals(0, TerminalPaneController.touchedBorderIndex(
            panes, 1, 498f, 250f, 12f));
        assertEquals(1, TerminalPaneController.touchedBorderIndex(
            panes, 0, 502f, 250f, 12f));
        assertEquals(0, TerminalPaneController.touchedBorderIndex(
            panes, 0, 500f, 250f, 12f));
        assertEquals(1, TerminalPaneController.touchedBorderIndex(
            panes, 1, 500f, 250f, 12f));
    }
}
