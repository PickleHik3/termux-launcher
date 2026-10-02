package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Layout editor v2 on the canvas (project-docs/reference/layout-editor-v2/DECISIONS.md): eye-off
 * is the only hide target and only an accepted drop highlights it (item 2), a selected bar shows
 * its destinations and a move control whose menu reaches them (items 4 and 5), Back cancels a
 * lift (item 4), and the app icons bar is seven placeholders whatever is pinned (item 8).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LayoutCanvasV2Test {

    /** Eye-off's highlight, below the canvas in the sheet, as the editor reports it. */
    private static final RectF EYE_OFF = new RectF(300f, 820f, 364f, 884f);

    private static PlaceLayout bottomBars() {
        return new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    /** A frame-filling canvas, as the editor hosts it, with eye-off's rect outside it. */
    private static LayoutCanvasView editorCanvas(PlaceLayout layout) {
        FrameLayout parent = new FrameLayout(RuntimeEnvironment.getApplication());
        LayoutCanvasView view = new LayoutCanvasView(parent.getContext());
        parent.addView(view, new FrameLayout.LayoutParams(400, 800));
        parent.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 400, 800);
        view.setLegendVisible(false);
        view.setFillsView(true);
        view.setShape(LayoutStyle.FLOATING, 20f, 12f);
        view.setExternalTrayRect(EYE_OFF);
        view.setLayout(layout, PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        return view;
    }

    private static void touch(LayoutCanvasView view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        view.onTouchEvent(event);
        event.recycle();
    }

    private static void lift(LayoutCanvasView view, LayoutCanvasView.Block bar) {
        RectF rect = bar == LayoutCanvasView.Block.KEYBOARD ? view.keyboardRect()
            : view.blockRect(bar);
        assertNotNull("a bar to press: " + bar, rect);
        float slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        touch(view, MotionEvent.ACTION_DOWN, rect.centerX(), rect.centerY());
        touch(view, MotionEvent.ACTION_MOVE, rect.centerX(), rect.centerY() - 3f * slop);
    }

    /** What the canvas told the editor about eye-off, and what it dropped. */
    private static final class Recorder implements LayoutCanvasView.OnCanvasEditListener,
        LayoutCanvasView.OnBarDroppedListener {
        final List<boolean[]> offers = new ArrayList<>();
        final List<Edge> drops = new ArrayList<>();
        int dropCount;
        boolean keyboardPutAway;

        @Override public void onTrayOfferChanged(boolean offered, boolean hovered) {
            offers.add(new boolean[] {offered, hovered});
        }

        @Override public void onKeyboardPutAway() {
            keyboardPutAway = true;
        }

        @Override public void onBarDropped(LayoutCanvasView.Block bar, Edge edge, int index) {
            drops.add(edge);
            dropCount++;
        }

        boolean[] lastOffer() {
            return offers.isEmpty() ? new boolean[] {false, false} : offers.get(offers.size() - 1);
        }
    }

    // ---- Item 2: eye-off is the one hide target --------------------------------------------------

    @Test
    public void aLiftedBarDroppedOnEyeOffIsHiddenAndEyeOffHighlightsWhileItWouldAccept() {
        LayoutCanvasView view = editorCanvas(bottomBars());
        Recorder recorder = new Recorder();
        view.setOnCanvasEditListener(recorder);
        view.setOnBarDroppedListener(recorder);

        lift(view, LayoutCanvasView.Block.APPS_ROW);
        assertTrue("offered as soon as the bar is in the air", recorder.lastOffer()[0]);
        assertFalse("not hovered yet", recorder.lastOffer()[1]);
        int trays = 0;
        for (MiniatureDragPolicy.Slot slot : view.slots()) {
            if (slot.isTray()) trays++;
        }
        assertEquals("eye-off is the one hide target", 1, trays);

        touch(view, MotionEvent.ACTION_MOVE, EYE_OFF.centerX(), EYE_OFF.centerY());
        assertTrue(recorder.lastOffer()[0]);
        assertTrue("hovered over eye-off", recorder.lastOffer()[1]);
        assertTrue(view.hoveredSlot().isTray());

        touch(view, MotionEvent.ACTION_UP, EYE_OFF.centerX(), EYE_OFF.centerY());
        assertEquals(1, recorder.dropCount);
        assertNull("a drop on eye-off hides: no edge", recorder.drops.get(0));
        assertFalse("the offer goes with the lift", recorder.lastOffer()[0]);
    }

    @Test
    public void eyeOffsDropAreaIsLargerThanItsTarget() {
        LayoutCanvasView view = editorCanvas(bottomBars());
        float density = view.getResources().getDisplayMetrics().density;
        // The editor hands over the 64dp highlight, not the 48dp button.
        RectF highlight = new RectF(0f, 820f, 64f * density, 820f + 64f * density);
        view.setExternalTrayRect(highlight);
        Recorder recorder = new Recorder();
        view.setOnCanvasEditListener(recorder);
        view.setOnBarDroppedListener(recorder);
        lift(view, LayoutCanvasView.Block.EXTRA_KEYS);
        // Just inside the highlight's corner, outside a 48dp square centred in it.
        float edge = 4f * density;
        touch(view, MotionEvent.ACTION_MOVE, highlight.left + edge, highlight.top + edge);
        assertTrue("the highlight's whole area accepts", recorder.lastOffer()[1]);
    }

    @Test
    public void aTileDraggedInFromTheSheetIsNeverOfferedEyeOffAgain() {
        LayoutCanvasView view = editorCanvas(new PlaceLayout(Edge.TOP, RowPlacement.HIDDEN, true,
            Edge.BOTTOM, RowPlacement.BOTTOM, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5));
        Recorder recorder = new Recorder();
        view.setOnCanvasEditListener(recorder);
        assertTrue(view.beginHiddenDrag(LayoutCanvasView.Block.APPS_ROW));
        for (MiniatureDragPolicy.Slot slot : view.slots())
            assertFalse("only accepted drops highlight", slot.isTray());
        assertFalse(recorder.lastOffer()[0]);
        view.endHiddenDrag();
    }

    @Test
    public void theKeyboardLiftedIsOfferedEyeOffAndDroppedThereIsSwitchedOff() {
        LayoutCanvasView view = editorCanvas(bottomBars());
        Recorder recorder = new Recorder();
        view.setOnCanvasEditListener(recorder);
        view.setOnBarDroppedListener(recorder);
        lift(view, LayoutCanvasView.Block.KEYBOARD);
        assertTrue(recorder.lastOffer()[0]);
        touch(view, MotionEvent.ACTION_MOVE, EYE_OFF.centerX(), EYE_OFF.centerY());
        touch(view, MotionEvent.ACTION_UP, EYE_OFF.centerX(), EYE_OFF.centerY());
        assertTrue(recorder.keyboardPutAway);
    }

    // ---- Item 4: selection, destinations, cancel -------------------------------------------------

    @Test
    public void aSelectedBarHighlightsOnlyTheEdgesItMayMoveTo() {
        LayoutCanvasView view = editorCanvas(bottomBars());
        assertTrue(view.selectionTargets().isEmpty());
        view.setSelectedBlock(LayoutCanvasView.Block.APPS_ROW);
        List<MiniatureDragPolicy.Slot> targets = view.selectionTargets();
        assertFalse(targets.isEmpty());
        for (MiniatureDragPolicy.Slot slot : targets)
            assertFalse("eye-off is the sheet's, never an edge highlight", slot.isTray());
        view.setSelectedBlock(LayoutCanvasView.Block.CANVAS);
        assertTrue("the pane is the anchor: it goes nowhere", view.selectionTargets().isEmpty());
        view.setSelectedBlock(null);
        assertTrue(view.selectionTargets().isEmpty());
    }

    @Test
    public void backCancelsALiftAndWritesNothing() {
        LayoutCanvasView view = editorCanvas(bottomBars());
        Recorder recorder = new Recorder();
        view.setOnCanvasEditListener(recorder);
        view.setOnBarDroppedListener(recorder);
        assertFalse("nothing in the air: Back is not the canvas's", view.cancelLift());
        lift(view, LayoutCanvasView.Block.APPS_ROW);
        assertTrue(view.isLifting());
        assertTrue(view.cancelLift());
        assertNull("the bar is over nothing on its way home", view.hoveredSlot());
        assertFalse("the offer is withdrawn", recorder.lastOffer()[0]);
        touch(view, MotionEvent.ACTION_UP, EYE_OFF.centerX(), EYE_OFF.centerY());
        assertEquals("a cancelled lift writes nothing", 0, recorder.dropCount);
    }

    @Test
    public void aReleaseOutsideTheCanvasAndOffEyeOffCancels() {
        LayoutCanvasView view = editorCanvas(bottomBars());
        Recorder recorder = new Recorder();
        view.setOnBarDroppedListener(recorder);
        lift(view, LayoutCanvasView.Block.EXTRA_KEYS);
        touch(view, MotionEvent.ACTION_MOVE, -40f, 1200f);
        touch(view, MotionEvent.ACTION_UP, -40f, 1200f);
        assertEquals(0, recorder.dropCount);
    }

    // ---- Item 5: the move control and its menu ---------------------------------------------------

    @Test
    public void theMoveMenuListsTheEdgesTheDragWouldOfferAndHide() {
        LayoutCanvasView view = editorCanvas(bottomBars());
        List<LayoutCanvasView.Destination> apps =
            view.moveDestinations(LayoutCanvasView.Block.APPS_ROW);
        assertTrue(apps.contains(LayoutCanvasView.Destination.TOP));
        assertFalse("not the edge it already stands on",
            apps.contains(LayoutCanvasView.Destination.BOTTOM));
        assertEquals("hide comes last", LayoutCanvasView.Destination.HIDE,
            apps.get(apps.size() - 1));
        assertEquals("the keyboard can only be hidden",
            Collections.singletonList(LayoutCanvasView.Destination.HIDE),
            view.moveDestinations(LayoutCanvasView.Block.KEYBOARD));
        assertTrue("the pane is the anchor",
            view.moveDestinations(LayoutCanvasView.Block.CANVAS).isEmpty());
    }

    @Test
    public void aDestinationRunsTheAccessibilityMoveAndWritesLikeADrop() {
        LayoutCanvasView view = editorCanvas(bottomBars());
        Recorder recorder = new Recorder();
        view.setOnCanvasEditListener(recorder);
        view.setOnBarDroppedListener(recorder);
        assertTrue(view.moveTo(LayoutCanvasView.Block.APPS_ROW, LayoutCanvasView.Destination.TOP));
        assertEquals(Collections.singletonList(Edge.TOP), recorder.drops);
        assertTrue(view.moveTo(LayoutCanvasView.Block.EXTRA_KEYS,
            LayoutCanvasView.Destination.HIDE));
        assertNull("hide is a drop with no edge", recorder.drops.get(1));
        assertTrue(view.moveTo(LayoutCanvasView.Block.KEYBOARD,
            LayoutCanvasView.Destination.HIDE));
        assertTrue(recorder.keyboardPutAway);
        assertFalse("a destination it does not offer is refused",
            view.moveTo(LayoutCanvasView.Block.APPS_ROW, LayoutCanvasView.Destination.BOTTOM));
        for (LayoutCanvasView.Destination destination : LayoutCanvasView.Destination.values()) {
            assertTrue(LayoutCanvasView.actionOf(destination) != 0);
            assertTrue(LayoutCanvasView.labelOf(destination) != 0);
        }
    }

    @Test
    public void theMoveControlStandsOnTheBarsOuterEdgeWithNoRoomOutsideThePhone() {
        LayoutCanvasView view = editorCanvas(bottomBars());
        assertNull("nothing selected, no control", view.moveControlRect());
        view.setSelectedBlock(LayoutCanvasView.Block.APPS_ROW);
        RectF control = view.moveControlRect();
        RectF bar = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        assertNotNull(control);
        assertNotNull(bar);
        float density = view.getResources().getDisplayMetrics().density;
        assertEquals(48f * density, control.width(), 0.5f);
        assertEquals(48f * density, control.height(), 0.5f);
        assertTrue("flush with a bottom bar's outer side, away from the pane",
            control.bottom >= Math.min(bar.bottom, view.getHeight()) - 0.5f);
        view.setSelectedBlock(LayoutCanvasView.Block.CANVAS);
        assertNull("the pane has no move control", view.moveControlRect());
    }

    /**
     * With room round the phone (the editor's gutter), the control stands outside the frame:
     * beside it, level with a row, and never on the selected bar or any other element.
     */
    @Test
    public void theMoveControlStandsInTheGutterOutsideThePhone() {
        LayoutCanvasView view = editorCanvas(bottomBars());
        float density = view.getResources().getDisplayMetrics().density;
        float gutter = 64f * density;
        view.setMoveControlRoom(new RectF(-gutter, 0f, view.getWidth() + gutter,
            view.getHeight() + gutter));
        for (LayoutCanvasView.Block block : new LayoutCanvasView.Block[] {
                LayoutCanvasView.Block.APPS_ROW, LayoutCanvasView.Block.STATUS_BAR,
                LayoutCanvasView.Block.KEYBOARD}) {
            view.setSelectedBlock(block);
            RectF control = view.moveControlRect();
            if (control == null) continue;
            RectF frame = view.frameRect();
            assertNotNull(frame);
            assertFalse(block + ": outside the phone's frame", RectF.intersects(control, frame));
            RectF bar = view.blockRect(block);
            assertNotNull(bar);
            assertTrue(block + ": level with the row", control.centerY() >= bar.top - 0.5f
                && control.centerY() <= bar.bottom + 0.5f
                || control.top <= 0.5f || control.bottom >= view.getHeight() - 0.5f);
            for (LayoutCanvasView.Block other : LayoutCanvasView.Block.values()) {
                RectF rect = view.blockRect(other);
                if (rect != null && !rect.isEmpty())
                    assertFalse(block + "'s control on " + other, RectF.intersects(control, rect));
            }
        }
    }

    /**
     * The destination guides are the accepted zones' outlines only, cut so that none crosses
     * another element: no guide meets the keyboard or another bar, selected or lifted.
     */
    @Test
    public void theDestinationGuidesNeverCrossAnotherElement() {
        LayoutCanvasView view = editorCanvas(bottomBars());
        view.setSelectedBlock(LayoutCanvasView.Block.APPS_ROW);
        List<RectF> selected = view.guideRects();
        assertFalse("a selected bar shows where it can go", selected.isEmpty());
        assertGuidesClear(view, selected, LayoutCanvasView.Block.APPS_ROW);
        view.setSelectedBlock(null);
        lift(view, LayoutCanvasView.Block.APPS_ROW);
        assertTrue(view.isLifting());
        List<RectF> lifted = view.guideRects();
        assertFalse("a lifted bar shows its accepted destinations", lifted.isEmpty());
        assertGuidesClear(view, lifted, LayoutCanvasView.Block.APPS_ROW);
        view.cancelLift();
    }

    private static void assertGuidesClear(LayoutCanvasView view, List<RectF> guides,
                                          LayoutCanvasView.Block moving) {
        for (RectF guide : guides) {
            for (LayoutCanvasView.Block other : LayoutCanvasView.Block.values()) {
                if (other == moving || other == LayoutCanvasView.Block.CANVAS) continue;
                RectF rect = view.blockRect(other);
                if (rect == null || rect.isEmpty()) continue;
                RectF overlap = new RectF();
                boolean meets = overlap.setIntersect(guide, rect)
                    && overlap.width() > 0.5f && overlap.height() > 0.5f;
                assertFalse("a guide " + guide + " crosses " + other + " " + rect, meets);
            }
        }
    }

    // ---- Item 8: placeholder app icons -----------------------------------------------------------

    @Test
    public void theAppIconsBarIsSevenPlaceholdersWhateverIsPinned() {
        assertEquals(7, LayoutCanvasArtwork.dockSlotsFor(336f));
        assertEquals("a rail as long as a row draws the same seven", 7,
            LayoutCanvasArtwork.dockSlotsFor(336f));
        assertEquals("a longer bar does not add any", 7, LayoutCanvasArtwork.dockSlotsFor(800f));
        assertEquals("a bar too short for seven draws what fits", 3,
            LayoutCanvasArtwork.dockSlotsFor(76f));
        assertEquals(7, LayoutCanvasArtwork.DOCK_GLYPHS.length);
    }

    @Test
    public void nothingFeedsTheCanvasThePinnedApps() {
        List<String> canvas = new ArrayList<>();
        for (Method method : LayoutCanvasView.class.getMethods()) canvas.add(method.getName());
        assertFalse(canvas.contains("setSlotCounts"));
        assertFalse(canvas.contains("setSlotContent"));
        assertTrue("extra keys stay live", canvas.contains("setExtraKeySlots"));
        List<String> host = new ArrayList<>();
        for (Method method : LayoutEditorController.Host.class.getMethods())
            host.add(method.getName());
        assertFalse(host.contains("pinnedAppIcons"));
        assertFalse(host.contains("pinnedAppCount"));
        assertTrue(host.containsAll(Arrays.asList("extraKeyCount", "extraKeySlots")));
    }
}
