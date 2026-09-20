package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * Every door into the sessions drawer, and the fact that they all open the same one.
 *
 * <p>This is the defect the merge exists to remove. The chip's panel and the browser were separate
 * surfaces over the same sessions, so the chip could be showing a list while the browser was open
 * behind it, and closing one left the other describing a layout nobody was looking at. There is one
 * drawer now, and a second pull on any handle puts it away.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SessionsDrawerRoutingTest {

    @Test
    public void theSecondPullOnTheSameHandlePutsTheDrawerAway() {
        TermuxActivity activity = laidOutActivity();

        TerminalSessionBrowser.toggle(activity);
        assertTrue(TerminalSessionBrowser.isOpen(activity));

        TerminalSessionBrowser.toggle(activity);
        assertFalse(TerminalSessionBrowser.isOpen(activity));
    }

    /** The status chip's old panel and the browser are the same surface now. */
    @Test
    public void theChipAndTheBrowserSeamOpenAndCloseOneDrawer() {
        TermuxActivity activity = laidOutActivity();

        ReflectionHelpers.callInstanceMethod(activity, "toggleSessionsPanel");
        assertTrue("session.panel and the chip land on the drawer",
            TerminalSessionBrowser.isOpen(activity));
        assertEquals("and on exactly one card, not a panel over a browser",
            1, activity.getTerminalSheetController().depth());
        assertEquals(Boolean.TRUE,
            ReflectionHelpers.callInstanceMethod(activity, "isSessionsPanelShowing"));

        // session.browser's seam, aimed at the drawer the chip already opened.
        TerminalSessionBrowser.show(activity);
        assertFalse("the second trigger closed it rather than stacking a second one",
            TerminalSessionBrowser.isOpen(activity));
        assertEquals(0, activity.getTerminalSheetController().depth());
    }

    @Test
    public void theWorkspacePickerSeamOpensTheDrawerOnItsSavedHalf() {
        TermuxActivity activity = laidOutActivity();

        ReflectionHelpers.callInstanceMethod(activity, "showWorkspacePicker");

        assertTrue(TerminalSessionBrowser.isOpen(activity));
        SessionsDrawerView drawer = drawerOf(activity);
        assertNotNull(drawer);
        assertEquals(SessionsDrawerView.Tab.SAVED, drawer.tab());
    }

    @Test
    public void theSavePromptSeamOpensTheDrawerWithItsNameFieldTakingKeys() {
        TermuxActivity activity = laidOutActivity();

        ReflectionHelpers.callInstanceMethod(activity, "promptSaveWorkspace");

        SessionsDrawerView drawer = drawerOf(activity);
        assertNotNull(drawer);
        assertEquals(SessionsDrawerView.Tab.LIVE, drawer.tab());
        assertTrue(drawer.isSaveFieldOpen());
        assertTrue(drawer.isTyping());
    }

    /** Asking for the picker while the drawer is up moves it over rather than opening a second. */
    @Test
    public void aSeamAimedAtAnOpenDrawerMovesItRatherThanStackingOne() {
        TermuxActivity activity = laidOutActivity();
        TerminalSessionBrowser.toggle(activity);

        ReflectionHelpers.callInstanceMethod(activity, "showWorkspacePicker");

        assertEquals(1, activity.getTerminalSheetController().depth());
        assertEquals(SessionsDrawerView.Tab.SAVED, drawerOf(activity).tab());
    }

    /** Back folds the row's answer away first, and only then closes the drawer. */
    @Test
    public void backFoldsAnUnfoldedRowBeforeItClosesTheDrawer() {
        TermuxActivity activity = laidOutActivity();
        ReflectionHelpers.callInstanceMethod(activity, "promptSaveWorkspace");
        SessionsDrawerView drawer = drawerOf(activity);
        assertTrue(drawer.isSaveFieldOpen());

        activity.onBackPressed();

        assertTrue("the drawer survives the press that folds its field", drawer.isSaveFieldOpen()
            || TerminalSessionBrowser.isOpen(activity));
        assertFalse("and the field went with it", drawer.isSaveFieldOpen());
        assertTrue(TerminalSessionBrowser.isOpen(activity));

        activity.onBackPressed();
        assertFalse(TerminalSessionBrowser.isOpen(activity));
    }

    /** The drawer never becomes a text editor, whatever it has unfolded. */
    @Test
    public void theDrawerHoldsNothingFocusableEvenWithAFieldOpen() {
        TermuxActivity activity = laidOutActivity();
        ReflectionHelpers.callInstanceMethod(activity, "promptSaveWorkspace");

        View card = activity.getTerminalSheetController().topCard();
        assertNotNull(card);
        assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS,
            ((ViewGroup) card).getDescendantFocusability());
        assertFalse(card.onCheckIsTextEditor());
        assertEquals(null, activity.getCurrentFocus());
    }

    /** The drawer's title is its own header row, not a heading the card adds above it. */
    @Test
    public void theDrawerCarriesItsOwnHeader() {
        TermuxActivity activity = laidOutActivity();
        TerminalSessionBrowser.toggle(activity);

        View card = activity.getTerminalSheetController().topCard();
        assertNotNull(findText(card, activity.getString(R.string.session_browser_title)));
        assertNotNull(findText(card, activity.getString(R.string.sessions_drawer_live)));
        assertNotNull(findText(card, activity.getString(R.string.sessions_drawer_saved)));
    }

    @Nullable
    private static SessionsDrawerView drawerOf(@NonNull TermuxActivity activity) {
        View card = activity.getTerminalSheetController().topCard();
        return card == null ? null : findDrawer(card);
    }

    @Nullable
    private static SessionsDrawerView findDrawer(@NonNull View view) {
        if (view instanceof SessionsDrawerView) return (SessionsDrawerView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            SessionsDrawerView found = findDrawer(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

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

    private static TermuxActivity laidOutActivity() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        return activity;
    }
}
