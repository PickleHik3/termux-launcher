package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
 * What the terminal sheet plane has to keep true now that the session browser and its prompts live
 * on it instead of on dialog windows.
 *
 * <p>Two properties carry the whole migration. The plane must never become a text editor — a focused
 * field here would take the {@code InputConnection} off {@code TerminalView} and summon the system
 * IME, which is the swap the browser's old search box cost on every open. And back must close one
 * card, not the stack: the workspace picker opens a confirmation over itself, and a press that took
 * both would drop the user on the terminal.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalSheetControllerTest {

    @Test
    public void aSheetOpensAndCloses() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();
        assertFalse(sheet.isOpen());

        sheet.show("Sessions", new TextView(activity));

        assertTrue(sheet.isOpen());
        assertEquals(1, sheet.depth());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.terminal_sheet_host).getVisibility());
        assertTrue(activity.isTerminalSheetOpen());

        sheet.dismiss();

        assertFalse(sheet.isOpen());
        assertEquals(View.INVISIBLE,
            activity.findViewById(R.id.terminal_sheet_host).getVisibility());
    }

    @Test
    public void backClosesTheTopSheetOnly() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();
        View picker = new TextView(activity);
        sheet.show("Load workspace", picker);
        sheet.show("Delete “work”?", new TextView(activity));
        assertEquals(2, sheet.depth());

        activity.onBackPressed();

        assertEquals("the confirmation went, the picker under it stayed", 1, sheet.depth());
        assertEquals(picker, ((ViewGroup) sheet.topCard()).getChildAt(1));

        activity.onBackPressed();

        assertFalse(sheet.isOpen());
    }

    /**
     * The route back actually travels on a device: KEYCODE_BACK is claimed in the key channel and
     * {@code onBackPressed()} never runs. Both routes have to pop exactly one card.
     */
    @Test
    public void aBackKeystrokeAlsoClosesTheTopSheetOnly() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();
        sheet.show("Load workspace", new TextView(activity));
        sheet.show("Delete “work”?", new TextView(activity));

        assertTrue(activity.handleTerminalSheetKey(KeyEvent.KEYCODE_BACK,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));

        assertEquals(1, sheet.depth());
    }

    /** The palette keeps the slot above the plane, so the sheet can never swallow its escape. */
    @Test
    public void thePaletteStillConsumesBackBeforeTheSheet() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();
        sheet.show("Sessions", new TextView(activity));
        TerminalCommandPaletteController palette = activity.getCommandPaletteController();
        ReflectionHelpers.setField(palette, "mOpen", true);

        activity.onBackPressed();

        assertFalse(palette.isOpen());
        assertTrue("the sheet must survive the back press that collapses the palette",
            sheet.isOpen());
    }

    @Test
    public void dismissClearsTheBrowsersRefreshCallback() {
        TermuxActivity activity = laidOutActivity();

        TerminalSessionBrowser.show(activity);

        assertNotNull("the browser subscribes to foreground refreshes while it is up",
            ReflectionHelpers.getField(activity, "mSessionBrowserRefreshCallback"));

        activity.getTerminalSheetController().dismiss();

        assertNull("a callback left behind would keep reloading a browser that is gone",
            ReflectionHelpers.getField(activity, "mSessionBrowserRefreshCallback"));
    }

    /** A sheet opened over the browser must not clear the browser's own subscription. */
    @Test
    public void aStackedSheetLeavesTheBrowsersRefreshCallbackAlone() {
        TermuxActivity activity = laidOutActivity();
        TerminalSessionBrowser.show(activity);
        TerminalSheetController sheet = activity.getTerminalSheetController();

        sheet.show("Workspace name", new TextView(activity));
        sheet.dismiss();

        assertEquals(1, sheet.depth());
        assertNotNull(ReflectionHelpers.getField(activity, "mSessionBrowserRefreshCallback"));
    }

    @Test
    public void theSheetIsNeverATextEditorAndNeverTakesFocus() {
        TermuxActivity activity = laidOutActivity();

        TerminalSessionBrowser.show(activity);

        View host = activity.findViewById(R.id.terminal_sheet_host);
        assertFalse(host.onCheckIsTextEditor());
        assertFalse(host.isFocusable());
        assertNull("a focused view here would take the InputConnection off TerminalView",
            activity.getCurrentFocus());
        View card = activity.getTerminalSheetController().topCard();
        assertNotNull(card);
        assertFalse(card.onCheckIsTextEditor());
        assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS,
            ((ViewGroup) card).getDescendantFocusability());
        assertNull("the search field must be a label typed from the key channel, not an EditText",
            findEditText(card));
    }

    /** …and the field it types instead really is driven by the key channel. */
    @Test
    public void typingReachesTheSearchFieldThroughTheKeyChannel() {
        TermuxActivity activity = laidOutActivity();
        TerminalSessionBrowser.show(activity);
        TextView search = activity.findViewById(R.id.session_browser_search);

        assertTrue(activity.handleTerminalSheetCodePoint('v', false));
        assertTrue(activity.handleTerminalSheetCodePoint('i', false));

        assertEquals("vi▏", search.getText().toString());

        assertTrue(activity.handleTerminalSheetKey(KeyEvent.KEYCODE_DEL,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)));

        assertEquals("v▏", search.getText().toString());
    }

    @Nullable
    private static EditText findEditText(@NonNull View view) {
        if (view instanceof EditText) return (EditText) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = findEditText(group.getChildAt(i));
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
