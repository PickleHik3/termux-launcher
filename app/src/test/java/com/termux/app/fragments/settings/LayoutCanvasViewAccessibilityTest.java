package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;

import com.termux.R;
import com.termux.app.layouteditor.LayoutEditorPlan;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * The layout canvas's accessibility actions and keys write through the same listener calls a
 * finger does: wired here the way the editor wires them, into a {@link LayoutEditorPlan}, so a
 * move made without touch lands in the store and Discard puts it back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LayoutCanvasViewAccessibilityTest {

    private LayoutEditorPlan mPlan;
    private LayoutCanvasView mView;
    private final List<String> mHeard = new ArrayList<>();

    @Before
    public void setUp() {
        TermuxAppSharedPreferences preferences =
            TermuxAppSharedPreferences.build(RuntimeEnvironment.getApplication(), true);
        assertNotNull(preferences);
        PlaceLayoutStore places = new PlaceLayoutStore(preferences);
        mPlan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT);
        mView = new LayoutCanvasView(RuntimeEnvironment.getApplication());
        mView.setLegendVisible(false);
        mView.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        mView.layout(0, 0, 1000, 400);
        // The editor's own wiring, in miniature: every report is a write, then a redraw.
        mView.setOnBarDroppedListener(new LayoutCanvasView.OnBarDroppedListener() {
            @Override
            public void onBarDropped(@NonNull LayoutCanvasView.Block bar, @Nullable Edge edge,
                                     int index) {
                onBarDropped(bar, edge, index, false);
            }

            @Override
            public void onBarDropped(@NonNull LayoutCanvasView.Block bar, @Nullable Edge edge,
                                     int index, boolean underKeyboard) {
                mHeard.add("drop " + bar + " " + edge + " " + index);
                MiniatureDragPolicy.Bar dragged = LayoutCanvasView.barOf(bar);
                assertNotNull(dragged);
                mPlan.drop(dragged, edge, index, underKeyboard);
                sync();
            }
        });
        mView.setOnCanvasEditListener(new LayoutCanvasView.OnCanvasEditListener() {
            @Override
            public void onDockHeightDragged(float scale) {
                mHeard.add("dock " + scale);
                mPlan.setDockHeightScale(scale);
                sync();
            }

            @Override
            public void onHandleReleased() {
                mHeard.add("released");
            }

            @Override
            public void onHiddenChipTapped(@NonNull LayoutCanvasView.Block block) {
                mHeard.add("restore " + block);
                LayoutEditorPlan.TrayItem item = trayItem(block);
                assertNotNull(item);
                mPlan.restore(item);
                sync();
            }
        });
        sync();
    }

    private void sync() {
        mView.setSizes(mPlan.dockHeightScale(), mPlan.keyboardHeightScale(),
            mPlan.keyboardChinDp());
        mView.setLayout(mPlan.shownLayout(), mPlan.shownOrientation(), mPlan.place());
    }

    @Nullable
    private static LayoutEditorPlan.TrayItem trayItem(@NonNull LayoutCanvasView.Block block) {
        switch (block) {
            case STATUS_BAR: return LayoutEditorPlan.TrayItem.STATUS_BAR;
            case APPS_ROW: return LayoutEditorPlan.TrayItem.PINNED_APPS;
            case ALPHABETS_ROW: return LayoutEditorPlan.TrayItem.AZ_INDEX;
            case EXTRA_KEYS: return LayoutEditorPlan.TrayItem.EXTRA_KEYS;
            default: return null;
        }
    }

    private Edge statusEdge() {
        return mPlan.shownLayout().slot(Element.STATUS).edge;
    }

    /** An edge the status bar is not on and the drag would offer it. */
    private Edge otherEdge() {
        return statusEdge() == Edge.LEFT ? Edge.RIGHT : Edge.LEFT;
    }

    private static int moveActionFor(Edge edge) {
        switch (edge) {
            case TOP: return R.id.layout_canvas_action_move_top;
            case BOTTOM: return R.id.layout_canvas_action_move_bottom;
            case LEFT: return R.id.layout_canvas_action_move_left;
            case RIGHT:
            default: return R.id.layout_canvas_action_move_right;
        }
    }

    private static List<Integer> actionIds(AccessibilityNodeInfoCompat node) {
        List<Integer> ids = new ArrayList<>();
        for (AccessibilityActionCompat action : node.getActionList()) ids.add(action.getId());
        return ids;
    }

    private AccessibilityNodeInfoCompat node(int virtualId) {
        AccessibilityNodeInfoCompat node = AccessibilityNodeInfoCompat.obtain();
        mView.populateVirtualNode(virtualId, node);
        return node;
    }

    @Test
    public void everyBarIsAVirtualViewNamedWithWhereItStands() {
        int status = LayoutCanvasView.virtualIdOf(LayoutCanvasView.Block.STATUS_BAR);
        assertTrue(mView.virtualViewIds().contains(status));
        AccessibilityNodeInfoCompat node = node(status);
        assertEquals("Status bar", String.valueOf(node.getContentDescription()));
        assertNotNull(node.getStateDescription());
        assertFalse(String.valueOf(node.getStateDescription()).isEmpty());
        List<Integer> actions = actionIds(node);
        assertTrue(actions.contains(AccessibilityNodeInfoCompat.ACTION_CLICK));
        assertTrue("a move to another edge is offered",
            actions.contains(moveActionFor(otherEdge())));
        assertFalse("never to the edge it already stands on",
            actions.contains(moveActionFor(statusEdge())));
        assertTrue(actions.contains(R.id.layout_canvas_action_hide));
    }

    @Test
    public void aMoveActionWritesThroughTheDropAndDiscardPutsItBack() {
        Edge before = statusEdge();
        Edge target = otherEdge();
        assertTrue(mView.performVirtualAction(
            LayoutCanvasView.virtualIdOf(LayoutCanvasView.Block.STATUS_BAR),
            moveActionFor(target), null));
        assertEquals(target, statusEdge());
        assertEquals(1, mHeard.size());
        assertTrue(mHeard.get(0), mHeard.get(0).startsWith("drop STATUS_BAR " + target));
        assertTrue(mPlan.isDirty());
        assertTrue(mView.elementState(LayoutCanvasView.Block.STATUS_BAR).contains(
            target == Edge.LEFT ? "Left edge" : "Right edge"));

        mPlan.revert();
        sync();
        assertEquals(before, statusEdge());
        assertFalse(mPlan.isDirty());
    }

    @Test
    public void anIllegalMoveWritesNothing() {
        assertFalse(mView.performVirtualAction(
            LayoutCanvasView.virtualIdOf(LayoutCanvasView.Block.STATUS_BAR),
            moveActionFor(statusEdge()), null));
        assertTrue(mHeard.isEmpty());
        assertFalse(mPlan.isDirty());
    }

    @Test
    public void hideIsTheTrayAndShowIsTheChip() {
        int apps = LayoutCanvasView.virtualIdOf(LayoutCanvasView.Block.APPS_ROW);
        assertTrue(mView.performVirtualAction(apps, R.id.layout_canvas_action_hide, null));
        assertEquals("drop APPS_ROW null -1", mHeard.get(0));
        assertFalse(EdgeStackPolicy.isShown(mPlan.shownLayout(), Element.APPS));
        assertTrue("a hidden bar keeps its node, to be shown again",
            mView.virtualViewIds().contains(apps));
        assertEquals("Hidden", mView.elementState(LayoutCanvasView.Block.APPS_ROW));
        assertTrue(actionIds(node(apps)).contains(AccessibilityNodeInfoCompat.ACTION_CLICK));

        assertTrue(mView.performVirtualAction(apps, AccessibilityNodeInfoCompat.ACTION_CLICK,
            null));
        assertEquals("restore APPS_ROW", mHeard.get(1));
        assertTrue(EdgeStackPolicy.isShown(mPlan.shownLayout(), Element.APPS));
    }

    @Test
    public void aSizeActionIsAHandleDragThenARelease() {
        float before = mPlan.dockHeightScale();
        assertTrue(mView.performVirtualAction(
            LayoutCanvasView.virtualIdOf(LayoutCanvasView.Block.APPS_ROW),
            R.id.layout_canvas_action_smaller, null));
        assertEquals(2, mHeard.size());
        assertTrue(mHeard.get(0).startsWith("dock "));
        assertEquals("released", mHeard.get(1));
        assertTrue(mPlan.dockHeightScale() < before);
        assertTrue(mPlan.isDirty());
    }

    @Test
    public void theSelectedElementsHandleIsARange() {
        int apps = LayoutCanvasView.virtualIdOf(LayoutCanvasView.Block.APPS_ROW);
        assertTrue(mView.performVirtualAction(apps, AccessibilityNodeInfoCompat.ACTION_CLICK,
            null));
        assertEquals(LayoutCanvasView.Block.APPS_ROW, mView.selectedBlock());
        int handle = LayoutCanvasView.virtualIdOf(LayoutCanvasView.Handle.DOCK_HEIGHT);
        assertTrue(mView.virtualViewIds().contains(handle));
        AccessibilityNodeInfoCompat node = node(handle);
        assertNotNull(node.getRangeInfo());
        assertEquals(mPlan.dockHeightScale(), node.getRangeInfo().getCurrent(), 0.0001f);
        assertTrue(node(apps).isSelected());
        assertTrue(actionIds(node).contains(AccessibilityActionCompat.ACTION_SET_PROGRESS.getId()));

        android.os.Bundle arguments = new android.os.Bundle();
        arguments.putFloat(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE, 1.5f);
        assertTrue(mView.performVirtualAction(handle,
            AccessibilityActionCompat.ACTION_SET_PROGRESS.getId(), arguments));
        assertEquals(1.5f, mPlan.dockHeightScale(), 0.0001f);

        // A second click deselects, and the handle goes with the selection.
        assertTrue(mView.performVirtualAction(apps, AccessibilityNodeInfoCompat.ACTION_CLICK,
            null));
        assertEquals(null, mView.selectedBlock());
        assertFalse(mView.virtualViewIds().contains(handle));
    }

    @Test
    public void anArrowKeyMovesTheSelectedBarToThatEdge() {
        Edge target = otherEdge();
        mView.setSelectedBlock(LayoutCanvasView.Block.STATUS_BAR);
        int code = target == Edge.LEFT ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        assertTrue(mView.onArrangementKey(new KeyEvent(KeyEvent.ACTION_DOWN, code)));
        assertEquals(target, statusEdge());
        assertEquals("it stays selected where it landed",
            LayoutCanvasView.Block.STATUS_BAR, mView.selectedBlock());
    }

    @Test
    public void withNothingSelectedArrowsAreLeftForWalkingTheElements() {
        Edge before = statusEdge();
        assertFalse(mView.onArrangementKey(
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)));
        assertEquals(before, statusEdge());
        assertTrue(mHeard.isEmpty());
    }

    @Test
    public void deleteHidesTheSelectedBar() {
        mView.setSelectedBlock(LayoutCanvasView.Block.STATUS_BAR);
        assertEquals(LayoutCanvasView.Block.STATUS_BAR, mView.selectedBlock());
        assertTrue(mView.onArrangementKey(
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL)));
        assertFalse(EdgeStackPolicy.isShown(mPlan.shownLayout(), Element.STATUS));
        assertEquals("the selection goes with the element", null, mView.selectedBlock());
    }

    @Test
    public void theOtherOrientationIsEditedTheSameWay() {
        Edge portraitBefore = statusEdge();
        mPlan.showOrientation(PlaceOrientation.LANDSCAPE);
        sync();
        Edge target = otherEdge();
        assertTrue(mView.performVirtualAction(
            LayoutCanvasView.virtualIdOf(LayoutCanvasView.Block.STATUS_BAR),
            moveActionFor(target), null));
        assertEquals(target, statusEdge());
        mPlan.showOrientation(PlaceOrientation.PORTRAIT);
        PlaceLayout portrait = mPlan.shownLayout();
        assertEquals("the portrait arrangement is untouched", portraitBefore,
            portrait.slot(Element.STATUS).edge);
    }
}
