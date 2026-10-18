package com.termux.app.launcher.widget.builtin;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalPaneController;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A Terminal widget and its shell, end to end through the coordinator, a real pane controller and
 * a real widget view: the shell waits for the start to settle, starts in the directory the widget
 * remembers, comes back as the same shell while it runs, is never anyone else's, ends with the
 * widget, and an ended shell waits on the card for a tap. A resize or a new handle in the record
 * never rebuilds the card under the terminal.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalWidgetCoordinatorTest {

    private static final int ID = 42;

    private Activity activity;
    private FakeHost host;
    private TerminalWidgetCoordinator coordinator;
    private TerminalPaneController panes;
    private FrameLayout cell;

    @Before public void setUp() {
        android.provider.Settings.Global.putFloat(
            org.robolectric.RuntimeEnvironment.getApplication().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        panes = new TerminalPaneController(new SilentPaneHost(), new FrameLayout(activity),
            LayoutInflater.from(activity));
        host = new FakeHost(panes);
        host.widgets.put(ID, new Bundle());
        coordinator = new TerminalWidgetCoordinator(host);
        FrameLayout page = new FrameLayout(activity);
        activity.setContentView(page);
        cell = new FrameLayout(activity);
        page.addView(cell, new FrameLayout.LayoutParams(600, 300));
    }

    @Test public void theShellWaitsForTheStartThenStartsWhereTheWidgetWasLast() {
        host.widgets.get(ID).putString(TerminalWidgetPolicy.KEY_CWD, "/");
        TerminalWidgetView widget = place();
        assertTrue("on screen and asking", host.attached > 0);
        assertTrue("nothing started while the start is open", host.created.isEmpty());
        assertNull(panes.hostedFloatFor(ID));

        host.ready = true;
        coordinator.onShellsReady();
        assertEquals("one shell, in the remembered directory", 1, host.created.size());
        assertEquals("/", host.created.get(0));
        TerminalPaneController.Window window = panes.hostedFloatFor(ID);
        assertNotNull(window);
        assertTrue(panes.isHostedFloatInSlot(window));
        assertTrue("its terminal is in the card", holds(widget, panes.windowActiveSession(window)));
        assertEquals("its handle is the record's now", panes.windowActiveSession(window).mHandle,
            host.widgets.get(ID).getString(TerminalWidgetPolicy.KEY_SHELL));
        assertFalse(widget.isShowingEnded());
    }

    @Test public void aRunningShellComesBackAsTheSameShell() {
        TerminalSession running = shell();
        host.live.put(running.mHandle, running);
        host.widgets.get(ID).putString(TerminalWidgetPolicy.KEY_SHELL, running.mHandle);
        host.ready = true;
        TerminalWidgetView widget = place();

        assertTrue("no new shell", host.created.isEmpty());
        TerminalPaneController.Window window = panes.hostedFloatFor(ID);
        assertNotNull(window);
        assertSame(running, panes.windowActiveSession(window));
        assertTrue(holds(widget, running));
    }

    @Test public void aWidgetsShellIsNeverAnyoneElsesEvenOffScreen() {
        TerminalSession running = shell();
        host.widgets.get(ID).putString(TerminalWidgetPolicy.KEY_SHELL, running.mHandle);
        assertTrue("named by its record, no widget on screen", coordinator.isWidgetShell(running));
        assertFalse("a Workspace shell", coordinator.isWidgetShell(shell()));
        assertFalse(coordinator.isWidgetShell(null));
    }

    @Test public void theWidgetGoneTakesItsShellWithIt() {
        host.ready = true;
        place();
        TerminalPaneController.Window window = panes.hostedFloatFor(ID);
        assertNotNull(window);
        TerminalSession shell = panes.windowActiveSession(window);

        coordinator.reconcile();
        assertTrue("still on Home: nothing ends", host.killed.isEmpty());

        host.widgets.remove(ID);
        coordinator.reconcile();
        assertEquals(1, host.killed.size());
        assertSame(shell, host.killed.get(0));
        assertNull(panes.hostedFloatFor(ID));
        assertNull(panes.windowOf(shell));
    }

    @Test public void anEndedShellWaitsOnTheCardForATap() {
        host.ready = true;
        TerminalWidgetView widget = place();
        TerminalSession first = panes.windowActiveSession(panes.hostedFloatFor(ID));

        assertEquals(TerminalPaneController.FINISHED_PANE, panes.onSessionFinished(first));
        coordinator.onShellEnded(ID);
        assertTrue(widget.isShowingEnded());
        assertTrue(TerminalWidgetPolicy.isEnded(host.widgets.get(ID)));
        assertNull(host.widgets.get(ID).getString(TerminalWidgetPolicy.KEY_SHELL));

        // Off screen and back: still ended, nothing started behind the card.
        cell.removeAllViews();
        cell.addView(widget, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        idle();
        assertTrue(widget.isShowingEnded());
        assertEquals(1, host.created.size());

        widget.performClick();
        assertEquals("a tap starts a new one", 2, host.created.size());
        assertFalse(widget.isShowingEnded());
        assertFalse(TerminalWidgetPolicy.isEnded(host.widgets.get(ID)));
        assertNotNull(panes.hostedFloatFor(ID));
    }

    @Test public void removingAnIdleWidgetGoesAtOnceAndABusyOneAsksFirst() {
        host.ready = true;
        place();
        int[] removed = {0};
        host.idle = true;
        coordinator.confirmRemoval(activity, ID, () -> removed[0]++);
        assertEquals(1, removed[0]);
        host.idle = null;
        coordinator.confirmRemoval(activity, ID, () -> removed[0]++);
        assertEquals("unknown does not ask", 2, removed[0]);
        host.idle = false;
        // The question is a Material dialog: it needs a Material theme to be put up.
        coordinator.confirmRemoval(new android.view.ContextThemeWrapper(activity,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar),
            ID, () -> removed[0]++);
        assertEquals("busy: the user is asked first", 2, removed[0]);
    }

    @Test public void aResizeOrANewHandleKeepsTheCardItsTerminalIsIn() {
        host.ready = true;
        TerminalWidgetView widget = place();
        FrameLayout frame = widget.paneFrame();
        TerminalSession shell = panes.windowActiveSession(panes.hostedFloatFor(ID));

        // A 1x1 to a 4x2: another bucket, the same card.
        cell.setLayoutParams(new FrameLayout.LayoutParams(180, 180));
        idle();
        cell.setLayoutParams(new FrameLayout.LayoutParams(760, 380));
        idle();
        assertSame(frame, widget.paneFrame());
        assertTrue(holds(widget, shell));

        Bundle config = new Bundle(host.widgets.get(ID));
        config.putString(TerminalWidgetPolicy.KEY_SHELL, "another");
        config.putString(TerminalWidgetPolicy.KEY_CWD, "/tmp");
        widget.bind(config);
        idle();
        assertSame("state is no new setting", frame, widget.paneFrame());
        assertTrue(holds(widget, shell));
    }

    @Test public void editingHomeGivesTheKeyboardBack() {
        host.ready = true;
        place();
        TerminalPaneController.Window window = panes.hostedFloatFor(ID);
        panes.focusSession(panes.windowActiveSession(window));
        assertSame(window, panes.focusedHomeFloat());
        coordinator.onWidgetEditing(false);
        assertSame(window, panes.focusedHomeFloat());
        coordinator.onWidgetEditing(true);
        assertNull(panes.focusedHomeFloat());
    }

    // --- helpers ---

    @NonNull private TerminalWidgetView place() {
        BuiltinWidgetServices services = new BuiltinWidgetServices(activity, new ServicesHost());
        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(activity,
            BuiltinWidgetStyle.Direction.values()[0], false, null);
        TerminalWidgetView widget = (TerminalWidgetView) BuiltinWidgetFactory.create(activity,
            BuiltinWidgetKind.TERMINAL, services, style);
        widget.setWidgetId(ID);
        widget.bind(host.widgets.get(ID));
        cell.addView(widget, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        idle();
        return widget;
    }

    private static boolean holds(@NonNull View view, @Nullable TerminalSession shell) {
        if (view instanceof TerminalView) return ((TerminalView) view).getCurrentSession() == shell;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++)
            if (holds(group.getChildAt(i), shell)) return true;
        return false;
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @NonNull private static TerminalSession shell() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }

    /** The widget's services, forwarding the Terminal widget's calls as the activity does. */
    private final class ServicesHost implements BuiltinWidgetServices.Host {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command, String title) {
            return false;
        }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
        @Override public void attachTerminalWidget(int widgetId, @NonNull TerminalWidgetView widget) {
            host.attached++;
            coordinator.attach(widgetId, widget);
        }
        @Override public void detachTerminalWidget(int widgetId, @NonNull TerminalWidgetView widget) {
            coordinator.detach(widgetId, widget);
        }
        @Override public void restartTerminalWidget(int widgetId) { coordinator.restart(widgetId); }
    }

    private static final class FakeHost implements TerminalWidgetCoordinator.Host {
        final TerminalPaneController panes;
        final Map<Integer, Bundle> widgets = new HashMap<>();
        final Map<String, TerminalSession> live = new HashMap<>();
        final List<String> created = new ArrayList<>();
        final List<TerminalSession> killed = new ArrayList<>();
        boolean ready;
        @Nullable Boolean idle;
        int attached;

        FakeHost(@NonNull TerminalPaneController panes) { this.panes = panes; }

        @Override @Nullable public TerminalPaneController panes() { return panes; }
        @Override public boolean shellsReady() { return ready; }
        @Override @Nullable public TerminalSession liveShell(@NonNull String handle) {
            return live.get(handle);
        }
        @Override @Nullable public TerminalSession createShell(@Nullable String cwd) {
            created.add(cwd);
            TerminalSession shell = shell();
            live.put(shell.mHandle, shell);
            return shell;
        }
        @Override public void killShell(@NonNull TerminalSession shell) {
            killed.add(shell);
            live.remove(shell.mHandle);
        }
        @Override @Nullable public Bundle widgetConfig(int widgetId) {
            Bundle config = widgets.get(widgetId);
            return config == null ? null : new Bundle(config);
        }
        @Override public void writeWidgetConfig(int widgetId, @NonNull Bundle config) {
            if (widgets.containsKey(widgetId)) widgets.put(widgetId, new Bundle(config));
        }
        @Override @NonNull public Map<Integer, Bundle> terminalWidgets() {
            return new HashMap<>(widgets);
        }
        @Override public void ensureFloatRoom() { }
        @Override @Nullable public Boolean isShellIdle(@NonNull TerminalSession shell) {
            return idle;
        }
    }

    private static final class SilentPaneHost implements TerminalPaneController.Host {
        @Override public TerminalSession createShell(String cwd) { return shell(); }
        @Override public void configurePaneView(TerminalView view) { }
        @Override public void removeShell(TerminalSession session) { }
        @Override public void onActivePaneChanged() { }
        @Override public void onTreesChanged() { }
        @Override public String defaultCwd() { return "/"; }
    }
}
