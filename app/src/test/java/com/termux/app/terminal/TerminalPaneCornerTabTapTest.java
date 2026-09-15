package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.RectF;
import android.os.Build;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.termux.app.wall.PaneControlsView;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * What a finger on a terminal pane's corner tab reaches. The tab is the same
 * {@link PaneControlsView} the Widgets and Display pages carry now, so this asks it the only way a
 * finger can: tap where a button is and see what ran.
 *
 * <p>Modelled on the Widgets page's tap test, with one extra question that page never had to ask —
 * whether a tab built from a list of any length still hands every one of its buttons to the right
 * action, five of them included, since a fifth is what the Layout editor will add.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalPaneCornerTabTapTest {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 1000;

    /** What the pane's tab asked the launcher for, in order. */
    private static final class Calls implements TerminalPaneController.Host {
        final List<String> log = new ArrayList<>();
        @Override public TerminalSession createShell(String cwd) { return terminal(); }
        @Override public void configurePaneView(TerminalView view) {}
        @Override public void removeShell(TerminalSession session) {}
        @Override public void onActivePaneChanged() {}
        @Override public void onTreesChanged() {}
        @Override public String defaultCwd() { return "/"; }
        @Override public void showHelpOverlay() { log.add("help"); }
        @Override public void openSurfaceEditor() { log.add("editor"); }
    }

    // ---------------------------------------------------------------- the four actions

    /** A pane on its own has nothing to move, maximise or close: it offers the editor, and help. */
    @Test
    public void aLonePanesTabOffersTheEditorAndHelp() {
        Fixture fixture = fixture();
        fixture.showTab();
        assertEquals("two buttons on a lone pane", 2, fixture.slots().length);

        fixture.tapSlot(0);
        assertEquals(Arrays.asList("editor"), fixture.host.log);

        fixture.showTab();
        fixture.tapSlot(1);
        assertEquals(Arrays.asList("editor", "help"), fixture.host.log);
    }

    /**
     * Split, the same corner carries the four it always did — the move grip, maximise, close and
     * help — in that order, and each slot hands its own action over.
     */
    @Test
    public void aSplitPanesTabCarriesMoveMaximiseCloseAndHelp() {
        Fixture fixture = fixture();
        assertTrue(fixture.controller.split(LinearLayout.VERTICAL));
        fixture.layout();
        fixture.showTab();

        assertEquals("four buttons in a split", 4, fixture.slots().length);
        assertEquals(Arrays.asList(0, 1, 2, 4), fixture.idsAtEverySlot());
    }

    /** Maximised there is no neighbour to move onto, so that slot goes and the rest shuffle up. */
    @Test
    public void aMaximisedPanesTabDropsTheMoveGrip() {
        Fixture fixture = fixture();
        assertTrue(fixture.controller.split(LinearLayout.VERTICAL));
        fixture.layout();
        fixture.showTab();

        // Slot 1 is maximise: the pane takes the whole wall, and its tab is re-asserted on it.
        fixture.tapSlot(1);
        fixture.layout();
        assertNotNull("the pane is maximized",
            ReflectionHelpers.getField(fixture.controller, "mMaximizedLeaf"));
        assertEquals("three buttons maximized", 3, fixture.slots().length);
        assertEquals(Arrays.asList(1, 2, 4), fixture.idsAtEverySlot());
    }

    // ---------------------------------------------------------------- a fifth button

    /**
     * The tab is a list now, not four slots. A fifth button lays out inside the pane, gets a slot
     * of its own, and a tap on it is handed over like any other — which is what the Layout editor
     * needs before it can be added.
     */
    @Test
    public void aFifthActionLaysOutAndIsTappable() {
        Fixture fixture = fixture();
        assertTrue(fixture.controller.split(LinearLayout.VERTICAL));
        fixture.layout();
        fixture.showTab();
        RectF paneTab = new RectF();
        fixture.controls.tabBounds(paneTab);
        float fourWide = paneTab.width();

        fixture.controls.setActions(
            PaneControlsView.Action.glyph(10, ""),
            PaneControlsView.Action.glyph(11, ""),
            PaneControlsView.Action.glyph(12, ""),
            PaneControlsView.Action.glyph(13, ""),
            PaneControlsView.Action.glyph(14, "󰕮"));

        RectF[] slots = fixture.slots();
        assertEquals("five slots", 5, slots.length);
        fixture.controls.tabBounds(paneTab);
        assertTrue("the fifth button widened the tab", paneTab.width() > fourWide);
        assertTrue("and the tab still fits its pane", paneTab.left >= 0f && paneTab.right <= WIDTH);
        for (RectF slot : slots) {
            assertTrue("every slot has room for a thumb", slot.width() > 0f);
        }

        assertEquals(Arrays.asList(10, 11, 12, 13, 14), fixture.idsAtEverySlot());
    }

    /**
     * And on a pane far too narrow for five, they shrink rather than overflow: every one of them
     * is still inside the pane and still answers a tap at its own centre.
     */
    @Test
    public void fiveActionsOnANarrowPaneShrinkAndStayTappable() {
        // A wall 150px wide: a third of the room five 30dp buttons ask for at this density.
        Fixture fixture = fixture(150, HEIGHT);
        fixture.showTab();

        fixture.controls.setActions(
            PaneControlsView.Action.glyph(10, "\uf013"),
            PaneControlsView.Action.glyph(11, "\uf040"),
            PaneControlsView.Action.glyph(12, "\uf1de"),
            PaneControlsView.Action.glyph(13, "\uf011"),
            PaneControlsView.Action.glyph(14, "\udb81\udd6e"));

        float density = RuntimeEnvironment.getApplication().getResources()
            .getDisplayMetrics().density;
        RectF tab = new RectF();
        fixture.controls.tabBounds(tab);
        assertTrue("the tab was cut down to the pane", tab.width() < 192f * density);
        assertTrue("and never hangs over the pane's sides",
            tab.left >= 0f && tab.right <= 150f);
        for (RectF slot : fixture.slots()) {
            assertTrue("every slot still has room for a thumb", slot.width() > 0f);
            assertTrue("and stays inside the tab",
                slot.left >= tab.left - .01f && slot.right <= tab.right + .01f);
        }

        assertEquals(Arrays.asList(10, 11, 12, 13, 14), fixture.idsAtEverySlot());
    }

    // ---------------------------------------------------------------- the gestures

    /**
     * The move grip is a handle, not a button: it is dragged onto a neighbour, and that still
     * swaps the two panes rather than running an action.
     */
    @Test
    public void theMoveGripStillDragsThePaneOntoItsNeighbour() {
        Fixture fixture = fixture();
        assertTrue(fixture.controller.split(LinearLayout.VERTICAL));
        fixture.layout();
        TerminalPaneController.Window window = fixture.window;
        TerminalPaneController.Split root = (TerminalPaneController.Split) window.root;
        TerminalSession top = ((TerminalPaneController.Leaf) root.a).session;
        TerminalSession bottom = ((TerminalPaneController.Leaf) root.b).session;
        fixture.showTab();

        RectF grip = fixture.slots()[0];
        fixture.touch(MotionEvent.ACTION_DOWN, grip.centerX(), grip.centerY());
        fixture.touch(MotionEvent.ACTION_MOVE, WIDTH / 2f, HEIGHT * 0.8f);
        fixture.touch(MotionEvent.ACTION_UP, WIDTH / 2f, HEIGHT * 0.8f);
        fixture.idle();

        root = (TerminalPaneController.Split) window.root;
        assertEquals("the pane moved onto its neighbour",
            bottom, ((TerminalPaneController.Leaf) root.a).session);
        assertEquals(top, ((TerminalPaneController.Leaf) root.b).session);
    }

    /** A tap anywhere but the tab puts it away, as it always did. */
    @Test
    public void aTapOffTheTabPutsItAway() {
        Fixture fixture = fixture();
        fixture.showTab();
        assertTrue(fixture.controls.isControlsShown());

        fixture.tap(WIDTH / 2f, HEIGHT / 2f);
        assertFalse(fixture.controls.isControlsShown());
    }

    // ---------------------------------------------------------------- fixture

    private static Fixture fixture() {
        return fixture(WIDTH, HEIGHT);
    }

    private static Fixture fixture(int width, int height) {
        Calls host = new Calls();
        FrameLayout hostView = new FrameLayout(RuntimeEnvironment.getApplication());
        Context context = RuntimeEnvironment.getApplication();
        TerminalPaneController controller =
            new TerminalPaneController(host, hostView, LayoutInflater.from(context));
        TerminalPaneController.Window window = controller.newWindow(terminal());
        controller.showWindow(window);
        Fixture fixture = new Fixture(host, hostView, controller, window, width, height);
        fixture.layout();
        return fixture;
    }

    private static final class Fixture {
        final Calls host;
        final FrameLayout hostView;
        final TerminalPaneController controller;
        final TerminalPaneController.Window window;
        final View overlay;
        final PaneControlsView controls;
        final int width;
        final int height;

        Fixture(Calls host, FrameLayout hostView, TerminalPaneController controller,
                TerminalPaneController.Window window, int width, int height) {
            this.host = host;
            this.hostView = hostView;
            this.controller = controller;
            this.window = window;
            this.width = width;
            this.height = height;
            this.overlay = ReflectionHelpers.getField(controller, "mInteractionOverlay");
            this.controls = ReflectionHelpers.getField(overlay, "mControls");
        }

        /** Measure and lay the host out, so every pane frame has real bounds to hang a tab off. */
        void layout() {
            hostView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            hostView.layout(0, 0, width, height);
        }

        /** The tab's reveal and retract are 190 ms, and nothing on it answers mid-motion. */
        void idle() {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        }

        void touch(int action, float x, float y) {
            MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
            overlay.onTouchEvent(event);
            event.recycle();
        }

        void tap(float x, float y) {
            touch(MotionEvent.ACTION_DOWN, x, y);
            touch(MotionEvent.ACTION_UP, x, y);
            idle();
        }

        /** A tap on the pane's top-trailing corner, which is where the tab has always come out. */
        void showTab() {
            tap(width - 2f, 2f);
            assertTrue("the corner tap should have dropped the tab", controls.isControlsShown());
        }

        /** Where the tab's buttons are, as the view itself laid them out. */
        RectF[] slots() {
            RectF tab = new RectF();
            controls.tabBounds(tab);
            return ReflectionHelpers.getField(controls, "mButtons");
        }

        void tapSlot(int slot) {
            RectF button = slots()[slot];
            tap(button.centerX(), button.centerY());
        }

        /**
         * The id each slot hands over, read by tapping every one of them with the page's own
         * listener stood aside — the mapping from slot to action, which is the thing a fixed
         * four-slot tab used to get wrong as soon as there were three or five.
         */
        @NonNull
        List<Integer> idsAtEverySlot() {
            List<Integer> ran = new ArrayList<>();
            controls.setListener(ran::add);
            RectF[] buttons = slots();
            for (RectF button : buttons) {
                float x = button.centerX();
                float y = button.centerY();
                // The move grip is a drag handle: it never reaches the listener, so it is read
                // off the tab directly and put in the list where a tap on it would have gone.
                int id = controls.actionAt(x, y);
                if (id == 0) {
                    ran.add(id);
                    continue;
                }
                touch(MotionEvent.ACTION_DOWN, x, y);
                touch(MotionEvent.ACTION_UP, x, y);
            }
            return ran;
        }
    }

    private static TerminalSession terminal() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }
}
