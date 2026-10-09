package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.fragments.settings.MiniatureDragPolicy.Bar;
import com.termux.app.fragments.settings.MiniatureDragPolicy.Targets;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.List;

/**
 * Which moves the layout canvas offers a screen reader and a keyboard: always one of the drag's
 * own targets, a new edge at its innermost gap, a step along the bar's own edge within its side of
 * the keyboard, and the tray's rule for hiding.
 */
public class LayoutCanvasA11yPolicyTest {

    private static PlaceLayout layout() {
        return new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    private static Targets targets(Bar bar, PlaceOrientation orientation, PlaceLayout layout) {
        return MiniatureDragPolicy.targets(PaneWallPage.TERMINAL, orientation, layout, bar);
    }

    @Test
    public void aBarIsNotOfferedTheEdgeItAlreadyStandsOn() {
        PlaceLayout layout = layout();
        Targets targets = targets(Bar.STATUS_BAR, PlaceOrientation.PORTRAIT, layout);
        assertNull(LayoutCanvasA11yPolicy.toEdge(targets, layout, Element.STATUS, Edge.TOP));
    }

    @Test
    public void anotherEdgeIsOfferedAtItsInnermostGap() {
        PlaceLayout layout = layout();
        Targets targets = targets(Bar.STATUS_BAR, PlaceOrientation.PORTRAIT, layout);
        EdgeStackPolicy.Drop bottom =
            LayoutCanvasA11yPolicy.toEdge(targets, layout, Element.STATUS, Edge.BOTTOM);
        assertNotNull(bottom);
        assertEquals(Edge.BOTTOM, bottom.edge);
        assertEquals("against the canvas, after every band already there",
            EdgeStackPolicy.overKeyboard(layout).size(), bottom.index);
        assertFalse(bottom.underKeyboard);

        EdgeStackPolicy.Drop left =
            LayoutCanvasA11yPolicy.toEdge(targets, layout, Element.STATUS, Edge.LEFT);
        assertNotNull(left);
        assertEquals("a bare edge has the one gap", 0, left.index);
    }

    @Test
    public void everyMoveOfferedIsOneTheDragOffersInBothOrientations() {
        PlaceLayout layout = layout();
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (Bar bar : Bar.values()) {
                Targets targets = targets(bar, orientation, layout);
                for (Edge edge : Edge.values()) {
                    EdgeStackPolicy.Drop drop =
                        LayoutCanvasA11yPolicy.toEdge(targets, layout, bar.element(), edge);
                    if (drop != null) {
                        assertTrue(bar + " to " + edge + " in " + orientation,
                            targets.drops.contains(drop));
                    }
                }
                for (boolean outward : new boolean[]{true, false}) {
                    EdgeStackPolicy.Drop drop =
                        LayoutCanvasA11yPolicy.step(targets, layout, bar.element(), outward);
                    if (drop != null) {
                        assertTrue(bar + " step in " + orientation,
                            targets.drops.contains(drop)
                                || targets.underKeyboardDrops.contains(drop));
                    }
                }
            }
        }
    }

    @Test
    public void aMoveToAnotherEdgeLeavesTheBarThereAndTheOthersInOrder() {
        PlaceLayout layout = layout();
        List<Element> bottomBefore = EdgeStackPolicy.overKeyboard(layout);
        Targets targets = targets(Bar.STATUS_BAR, PlaceOrientation.PORTRAIT, layout);
        EdgeStackPolicy.Drop drop =
            LayoutCanvasA11yPolicy.toEdge(targets, layout, Element.STATUS, Edge.BOTTOM);
        assertNotNull(drop);
        PlaceLayout after = EdgeStackPolicy.withDrop(layout, Element.STATUS, drop.edge,
            drop.index, drop.underKeyboard);
        List<Element> bottomAfter = EdgeStackPolicy.overKeyboard(after);
        assertEquals(Edge.BOTTOM, after.slot(Element.STATUS).edge);
        assertEquals(bottomBefore, bottomAfter.subList(0, bottomBefore.size()));
        assertEquals(Element.STATUS, bottomAfter.get(bottomAfter.size() - 1));
    }

    @Test
    public void stepsStayWithinTheEdgeAndStopAtItsEnds() {
        PlaceLayout layout = layout();
        List<Element> bottom = EdgeStackPolicy.overKeyboard(layout);
        assertTrue("the shipped bottom edge carries more than one band", bottom.size() > 1);
        Element outermost = bottom.get(0);
        Element innermost = bottom.get(bottom.size() - 1);
        Targets outerTargets = targets(Bar.of(outermost), PlaceOrientation.PORTRAIT, layout);
        assertNull(LayoutCanvasA11yPolicy.step(outerTargets, layout, outermost, true));
        EdgeStackPolicy.Drop inward =
            LayoutCanvasA11yPolicy.step(outerTargets, layout, outermost, false);
        assertNotNull(inward);
        assertEquals(Edge.BOTTOM, inward.edge);
        assertEquals(1, inward.index);

        Targets innerTargets = targets(Bar.of(innermost), PlaceOrientation.PORTRAIT, layout);
        assertNull(LayoutCanvasA11yPolicy.step(innerTargets, layout, innermost, false));
        EdgeStackPolicy.Drop outward =
            LayoutCanvasA11yPolicy.step(innerTargets, layout, innermost, true);
        assertNotNull(outward);
        assertEquals(bottom.size() - 2, outward.index);
        PlaceLayout after = EdgeStackPolicy.withDrop(layout, innermost, outward.edge,
            outward.index, outward.underKeyboard);
        assertEquals(bottom.size() - 2, EdgeStackPolicy.overKeyboard(after).indexOf(innermost));
    }

    @Test
    public void anArrowTowardTheBarsOwnEdgeStepsOutwardAndAnyOtherMovesIt() {
        PlaceLayout layout = layout();
        List<Element> bottom = EdgeStackPolicy.overKeyboard(layout);
        Element innermost = bottom.get(bottom.size() - 1);
        Targets targets = targets(Bar.of(innermost), PlaceOrientation.PORTRAIT, layout);
        assertEquals(LayoutCanvasA11yPolicy.step(targets, layout, innermost, true),
            LayoutCanvasA11yPolicy.toward(targets, layout, innermost, Edge.BOTTOM));
        assertEquals(LayoutCanvasA11yPolicy.toEdge(targets, layout, innermost, Edge.RIGHT),
            LayoutCanvasA11yPolicy.toward(targets, layout, innermost, Edge.RIGHT));

        Targets status = targets(Bar.STATUS_BAR, PlaceOrientation.PORTRAIT, layout);
        assertNull("alone at the top, there is nowhere further up to go",
            LayoutCanvasA11yPolicy.toward(status, layout, Element.STATUS, Edge.TOP));
    }

    @Test
    public void aBarUnderTheKeyboardStepsAmongTheBandsUnderIt() {
        PlaceLayout layout = layout()
            .withSlot(Element.APPS, new Slot(false, Edge.BOTTOM, 0, true))
            .withSlot(Element.EXTRA_KEYS, new Slot(false, Edge.BOTTOM, 1, true));
        LayoutCanvasA11yPolicy.Position apps =
            LayoutCanvasA11yPolicy.positionOf(layout, Element.APPS);
        assertNotNull(apps);
        assertTrue(apps.underKeyboard);
        assertEquals(0, apps.index);
        assertEquals(2, apps.count);
        Targets targets = targets(Bar.APPS_ROW, PlaceOrientation.PORTRAIT, layout);
        EdgeStackPolicy.Drop inward =
            LayoutCanvasA11yPolicy.step(targets, layout, Element.APPS, false);
        assertNotNull(inward);
        assertTrue("it stays under the keyboard", inward.underKeyboard);
        assertEquals(1, inward.index);
        assertNull(LayoutCanvasA11yPolicy.toEdge(targets, layout, Element.APPS, Edge.BOTTOM));
    }

    @Test
    public void aHiddenBarCanOnlyBeShown() {
        PlaceLayout layout = layout().withSlot(Element.STATUS,
            layout().slot(Element.STATUS).withHidden(true));
        Targets targets = targets(Bar.STATUS_BAR, PlaceOrientation.PORTRAIT, layout);
        assertNull(LayoutCanvasA11yPolicy.positionOf(layout, Element.STATUS));
        for (Edge edge : Edge.values())
            assertNull(LayoutCanvasA11yPolicy.toEdge(targets, layout, Element.STATUS, edge));
        assertNull(LayoutCanvasA11yPolicy.step(targets, layout, Element.STATUS, true));
        assertFalse(LayoutCanvasA11yPolicy.canHide(targets, layout, Element.STATUS));
        assertTrue(LayoutCanvasA11yPolicy.canShow(layout, Element.STATUS));
    }

    @Test
    public void aShownBarMayHideAsTheTrayAllows() {
        PlaceLayout layout = layout();
        for (Bar bar : Bar.values()) {
            Targets targets = targets(bar, PlaceOrientation.PORTRAIT, layout);
            assertEquals(bar.toString(), targets.tray,
                LayoutCanvasA11yPolicy.canHide(targets, layout, bar.element()));
            assertFalse(LayoutCanvasA11yPolicy.canShow(layout, bar.element()));
        }
    }

    @Test
    public void sizeStepsStayInRangeAndDoNotDrift() {
        assertEquals(1.1f, LayoutCanvasA11yPolicy.stepScale(1.0f, 0.1f, 0.5f, 1.6f, 1), 0.0001f);
        assertEquals(0.9f, LayoutCanvasA11yPolicy.stepScale(1.0f, 0.1f, 0.5f, 1.6f, -1), 0.0001f);
        assertEquals(1.6f, LayoutCanvasA11yPolicy.stepScale(1.58f, 0.1f, 0.5f, 1.6f, 1), 0.0001f);
        assertEquals(0.5f, LayoutCanvasA11yPolicy.stepScale(0.52f, 0.1f, 0.5f, 1.6f, -1), 0.0001f);
        float value = 1.0f;
        for (int i = 0; i < 5; i++)
            value = LayoutCanvasA11yPolicy.stepScale(value, 0.1f, 0.5f, 1.6f, 1);
        assertEquals(1.5f, value, 0f);

        assertEquals(2, LayoutCanvasA11yPolicy.stepInt(0, 2, 0, 48, 1));
        assertEquals(0, LayoutCanvasA11yPolicy.stepInt(0, 2, 0, 48, -1));
        assertEquals(48, LayoutCanvasA11yPolicy.stepInt(47, 2, 0, 48, 1));
        assertEquals(5, LayoutCanvasA11yPolicy.stepInt(5, 1, 2, 8, 0));
    }
}
