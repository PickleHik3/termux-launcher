package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The drawer's two halves and the state a row can be in.
 *
 * <p>Everything here used to be somewhere else: rename opened a chip after closing the browser,
 * close opened a confirmation card over the list it was about, and loading a workspace was a picker
 * that opened a second panel to ask append or replace. The property that replaces all of it is that
 * a row only ever unfolds one thing at a time, and that the way out is the same tap as the way in.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SessionsDrawerViewTest {

    /**
     * The drawer is under half the terminal wide, so on a phone three actions no longer fit across
     * a row; a strip that clipped its last one would lose Close. It stacks instead, and unstacks
     * when it is given the room back.
     */
    @Test
    public void anActionStripStacksItsButtonsWhenTheyDoNotFitAcross() {
        SessionsDrawerView.ActionStrip strip =
            new SessionsDrawerView.ActionStrip(RuntimeEnvironment.getApplication());
        for (int i = 0; i < 3; i++) {
            View button = new View(RuntimeEnvironment.getApplication());
            button.setMinimumWidth(100);
            button.setMinimumHeight(36);
            strip.addView(button, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        int height = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        strip.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), height);
        assertFalse("three 100px buttons fit across 400px", strip.isStacked());

        strip.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), height);
        assertTrue("and stack once there is only 200px", strip.isStacked());
        assertEquals("one under another, each as wide as the strip",
            ViewGroup.LayoutParams.MATCH_PARENT, strip.getChildAt(1).getLayoutParams().width);
        assertEquals(3 * 36, strip.getMeasuredHeight());

        strip.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), height);
        assertFalse("and lie flat again when the room comes back", strip.isStacked());
    }

    @Test
    public void theLiveHalfListsSessionsAndExpandsOneToItsWindows() {
        SessionsDrawerView drawer = drawer(new RecordingListener());
        drawer.bindLive(Arrays.asList(session(7, 0, true, "build", 70, 71),
            session(8, 1, false, null, 80)));

        assertEquals(SessionsDrawerView.Tab.LIVE, drawer.tab());
        assertEquals("one row per session, none of their windows yet", 2, rowCount(drawer));

        findText(drawer, "›").performClick();

        assertEquals("the session plus its two windows, and the other session",
            4, rowCount(drawer));
    }

    @Test
    public void tappingASessionOrAWindowActivatesIt() {
        RecordingListener listener = new RecordingListener();
        SessionsDrawerView drawer = drawer(listener);
        drawer.bindLive(Collections.singletonList(session(7, 0, true, "build", 70, 71)));

        drawer.rowsForTest().getChildAt(0).performClick();
        assertEquals(Collections.singletonList("activate:7"), listener.events);

        listener.events.clear();
        findText(drawer, "›").performClick();
        drawer.rowsForTest().getChildAt(1).performClick();
        assertEquals(Collections.singletonList("window:7/70"), listener.events);
    }

    /** idle → actions → rename → committed, and the drawer is idle again when it lands. */
    @Test
    public void aRowUnfoldsItsActionsThenARenameField() {
        RecordingListener listener = new RecordingListener();
        SessionsDrawerView drawer = drawer(listener);
        drawer.bindLive(Collections.singletonList(session(7, 0, true, "build", 70)));
        assertEquals(SessionsDrawerView.RowState.IDLE, drawer.rowState(7));

        findText(drawer, "⋯").performClick();
        assertEquals(SessionsDrawerView.RowState.ACTIONS, drawer.rowState(7));

        findText(drawer, string(R.string.session_browser_rename)).performClick();
        assertEquals(SessionsDrawerView.RowState.RENAME, drawer.rowState(7));
        assertTrue("nothing can be typed into a field nobody summoned keys for",
            listener.events.contains("typing"));
        assertTrue(drawer.isTyping());

        drawer.onText("x");
        findText(drawer, string(R.string.sessions_drawer_done)).performClick();

        assertEquals(Arrays.asList("typing", "rename:7=buildx"), listener.events);
        assertEquals("the row folds back once the answer has landed",
            SessionsDrawerView.RowState.IDLE, drawer.rowState(7));
        assertFalse(drawer.isTyping());
    }

    /** idle → actions → close confirmation → kept, which must leave the session alone. */
    @Test
    public void closingAsksInTheRowAndKeepingIsTheWayBack() {
        RecordingListener listener = new RecordingListener();
        SessionsDrawerView drawer = drawer(listener);
        drawer.bindLive(Collections.singletonList(session(7, 0, true, "build", 70)));

        findText(drawer, "⋯").performClick();
        findText(drawer, string(R.string.sessions_drawer_end)).performClick();
        assertEquals(SessionsDrawerView.RowState.CONFIRM_CLOSE, drawer.rowState(7));
        assertNotNull("the question has to be on screen, over the row it is about",
            findText(drawer, string(R.string.sessions_drawer_close_question)));

        findText(drawer, string(R.string.sessions_drawer_keep)).performClick();

        assertEquals(SessionsDrawerView.RowState.IDLE, drawer.rowState(7));
        assertTrue("Keep is not End", listener.events.isEmpty());

        findText(drawer, "⋯").performClick();
        findText(drawer, string(R.string.sessions_drawer_end)).performClick();
        findText(drawer, string(R.string.sessions_drawer_end)).performClick();
        assertEquals(Collections.singletonList("close:7"), listener.events);
    }

    /** A second tap on the same … is the way out of the actions it opened. */
    @Test
    public void theTapThatOpensARowsActionsAlsoClosesThem() {
        SessionsDrawerView drawer = drawer(new RecordingListener());
        drawer.bindLive(Collections.singletonList(session(7, 0, true, "build", 70)));

        findText(drawer, "⋯").performClick();
        assertEquals(SessionsDrawerView.RowState.ACTIONS, drawer.rowState(7));
        findText(drawer, "⋯").performClick();
        assertEquals(SessionsDrawerView.RowState.IDLE, drawer.rowState(7));
    }

    @Test
    public void theSavedHalfOffersAddToCurrentOrReplaceWhereTheNameWasTapped() {
        RecordingListener listener = new RecordingListener();
        SessionsDrawerView drawer = drawer(listener);
        drawer.setTab(SessionsDrawerView.Tab.SAVED);
        drawer.bindSaved(Collections.singletonList(
            new SessionsDrawerView.SavedWorkspace("work", 4, 0L, false)));

        assertEquals(1, rowCount(drawer));
        drawer.rowsForTest().getChildAt(0).performClick();

        assertEquals(SessionsDrawerView.SavedState.LOAD, drawer.savedState("work"));
        assertNotNull(findText(drawer, string(R.string.sessions_drawer_add_to_current)));
        findText(drawer, string(R.string.workspace_picker_replace)).performClick();

        assertEquals(Collections.singletonList("load:work replace"), listener.events);
        assertEquals(SessionsDrawerView.SavedState.IDLE, drawer.savedState("work"));
    }

    @Test
    public void deletingASavedWorkspaceIsConfirmedInItsOwnRow() {
        RecordingListener listener = new RecordingListener();
        SessionsDrawerView drawer = drawer(listener);
        drawer.setTab(SessionsDrawerView.Tab.SAVED);
        drawer.bindSaved(Collections.singletonList(
            new SessionsDrawerView.SavedWorkspace("work", 4, 0L, false)));

        findText(drawer, "⋯").performClick();
        assertEquals(SessionsDrawerView.SavedState.CONFIRM_DELETE, drawer.savedState("work"));
        assertTrue("deleting a file cannot be undone, so nothing happens on the first tap",
            listener.events.isEmpty());

        findText(drawer, string(R.string.workspace_delete_confirm)).performClick();
        assertEquals(Collections.singletonList("delete:work"), listener.events);
    }

    /** Running commands again is only offered by a workspace that actually captured some. */
    @Test
    public void theRunCommandsBoxAppearsOnlyForAWorkspaceThatCapturedThem() {
        SessionsDrawerView drawer = drawer(new RecordingListener());
        drawer.setTab(SessionsDrawerView.Tab.SAVED);
        drawer.bindSaved(Arrays.asList(
            new SessionsDrawerView.SavedWorkspace("bare", 2, 0L, false),
            new SessionsDrawerView.SavedWorkspace("busy", 2, 0L, true)));

        drawer.rowsForTest().getChildAt(0).performClick();
        assertNull(findText(drawer, string(R.string.sessions_drawer_run_commands)));

        // The bare row is unfolded, so the busy row has moved down past its action strip.
        drawer.rowsForTest().getChildAt(2).performClick();
        assertNotNull(findText(drawer, string(R.string.sessions_drawer_run_commands)));
    }

    @Test
    public void theSaveFooterBecomesANameFieldAndFoldsBackAgain() {
        RecordingListener listener = new RecordingListener();
        SessionsDrawerView drawer = drawer(listener);
        drawer.bindLive(Collections.singletonList(session(7, 0, true, "build", 70)));
        assertFalse(drawer.isSaveFieldOpen());

        findText(drawer, string(R.string.sessions_drawer_save_workspace)).performClick();
        assertTrue(drawer.isSaveFieldOpen());
        assertTrue(drawer.isTyping());

        drawer.onText("w");
        drawer.onText("k");
        findText(drawer, string(R.string.session_browser_save_workspace)).performClick();

        assertEquals(Arrays.asList("typing", "save:wk"), listener.events);
        assertFalse(drawer.isSaveFieldOpen());
    }

    /** Back folds one level at a time, and says so, so the drawer's own Back can take over. */
    @Test
    public void collapseOneClosesWhatIsUnfoldedAndReportsWhetherItHadAnything() {
        SessionsDrawerView drawer = drawer(new RecordingListener());
        drawer.bindLive(Collections.singletonList(session(7, 0, true, "build", 70)));

        assertFalse("nothing is unfolded, so the press belongs to the drawer",
            drawer.collapseOne());

        findText(drawer, "⋯").performClick();
        assertTrue(drawer.collapseOne());
        assertEquals(SessionsDrawerView.RowState.IDLE, drawer.rowState(7));
        assertFalse(drawer.collapseOne());
    }

    /** A session that goes away while its row is unfolded must not leave the state behind. */
    @Test
    public void aRowThatDisappearsTakesItsUnfoldedStateWithIt() {
        SessionsDrawerView drawer = drawer(new RecordingListener());
        drawer.bindLive(Collections.singletonList(session(7, 0, true, "build", 70)));
        findText(drawer, "⋯").performClick();

        drawer.bindLive(Collections.singletonList(session(8, 0, true, "other", 80)));

        assertEquals(SessionsDrawerView.RowState.IDLE, drawer.rowState(7));
        assertFalse(drawer.isTyping());
    }

    /** Switching halves is not a place to leave a half-typed name or an open confirmation. */
    @Test
    public void switchingTabsFoldsEverythingAway() {
        SessionsDrawerView drawer = drawer(new RecordingListener());
        drawer.bindLive(Collections.singletonList(session(7, 0, true, "build", 70)));
        findText(drawer, "⋯").performClick();

        drawer.setTab(SessionsDrawerView.Tab.SAVED);

        assertEquals(SessionsDrawerView.Tab.SAVED, drawer.tab());
        assertEquals(SessionsDrawerView.RowState.IDLE, drawer.rowState(7));
    }

    // ------------------------------------------------------------------ helpers

    @NonNull
    private static SessionsDrawerView drawer(@NonNull SessionsDrawerView.Listener listener) {
        SessionsDrawerView view =
            new SessionsDrawerView(RuntimeEnvironment.getApplication());
        view.setListener(listener);
        return view;
    }

    @NonNull
    private static String string(int res) {
        return RuntimeEnvironment.getApplication().getString(res);
    }

    private static int rowCount(@NonNull SessionsDrawerView drawer) {
        return drawer.rowsForTest().getChildCount();
    }

    @NonNull
    private static SessionBrowserModel.Session session(long id, int index, boolean current,
                                                       @Nullable String name, long... windowIds) {
        List<SessionBrowserModel.Window> windows = new ArrayList<>();
        for (int i = 0; i < windowIds.length; i++) {
            windows.add(new SessionBrowserModel.Window(windowIds[i], i, i == 0, 0,
                Collections.singletonList(new SessionBrowserModel.Pane("/home", "bash")), null));
        }
        return new SessionBrowserModel.Session(id, index, current, name, windows);
    }

    /** The first descendant whose text is exactly this; the drawer has no ids to find by. */
    @Nullable
    private static View findText(@NonNull View view, @NonNull String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findText(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private static final class RecordingListener implements SessionsDrawerView.Listener {
        final List<String> events = new ArrayList<>();

        @Override public void onNewSession() { events.add("new"); }
        @Override public void onNewSessionPrompt() { events.add("newPrompt"); }
        @Override public void onActivateSession(long id) { events.add("activate:" + id); }
        @Override public void onActivateWindow(long s, long w) { events.add("window:" + s + "/" + w); }
        @Override public void onCloneSession(long id) { events.add("clone:" + id); }
        @Override public void onRenameSession(long id, @NonNull String name) {
            events.add("rename:" + id + "=" + name);
        }
        @Override public void onCloseSession(long id) { events.add("close:" + id); }
        @Override public void onSaveWorkspace(@NonNull String name, boolean capture) {
            events.add("save:" + name);
        }
        @Override public void onLoadWorkspace(@NonNull String name, boolean replace, boolean run) {
            events.add("load:" + name + (replace ? " replace" : " append") + (run ? " run" : ""));
        }
        @Override public void onDeleteWorkspace(@NonNull String name) {
            events.add("delete:" + name);
        }
        @Override public void onTypingStarted() { events.add("typing"); }
    }
}
