package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
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

import java.util.Arrays;
import java.util.List;

/**
 * The terminal prompts that used to be dialog windows, now that they run on
 * {@link TerminalSheetController}.
 *
 * <p>Scrollback search is the one that mattered: it carried a focused {@code EditText}, so searching
 * a transcript swapped the system IME in over the terminal. It must now be typed entirely through
 * the key channel and hold nothing focusable. The hint picker's letter jump used to be a dialog key
 * listener that only ever saw hardware strokes, and has to keep selecting through the same channel.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalSheetPromptsTest {

    @Test
    public void scrollbackSearchIsTypedThroughTheKeyChannelAndNeverFocusesAnEditor() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();
        int[] jumped = {Integer.MIN_VALUE};

        TerminalScrollbackSearchOverlay.show(activity, transcript(), row -> jumped[0] = row);

        View card = sheet.topCard();
        assertNotNull(card);
        assertNull("a focused field here would take the InputConnection off TerminalView",
            findEditText(card));
        assertNull(activity.getCurrentFocus());

        assertTrue(activity.handleTerminalSheetCodePoint('e', false));
        assertTrue(activity.handleTerminalSheetCodePoint('r', false));
        assertTrue(activity.handleTerminalSheetCodePoint('r', false));

        assertEquals("err▏", typedLabel(card));
        ListView list = findListView(card);
        assertNotNull(list);
        assertEquals("both matching rows, newest first", 2, list.getAdapter().getCount());

        assertTrue(activity.handleTerminalSheetKey(KeyEvent.KEYCODE_ENTER,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));

        assertEquals("⏎ takes the newest match", -1, jumped[0]);
        assertFalse("choosing a match closes the search", sheet.isOpen());
    }

    /** Backspace edits the query rather than reaching the shell behind the sheet. */
    @Test
    public void scrollbackSearchBackspaceNarrowsNothingBackToEverything() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();
        TerminalScrollbackSearchOverlay.show(activity, transcript(), row -> {});
        View card = sheet.topCard();

        activity.handleTerminalSheetCodePoint('e', false);
        activity.handleTerminalSheetCodePoint('x', false);
        assertEquals(0, findListView(card).getAdapter().getCount());

        assertTrue(activity.handleTerminalSheetKey(KeyEvent.KEYCODE_DEL,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)));

        assertEquals("e▏", typedLabel(card));
        assertTrue("the shortened query has to be searched again, not left at its last result",
            findListView(card).getAdapter().getCount() > 0);
    }

    @Test
    public void aHintLetterStillSelectsItsHint() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();

        TerminalHintsOverlay.show(activity, "edit ./src/main.java please");

        assertTrue(sheet.isOpen());
        assertTrue(activity.handleTerminalSheetCodePoint('a', false));

        assertEquals("./src/main.java", clipboard(activity));
        assertFalse("picking a hint closes the picker", sheet.isOpen());
    }

    /**
     * The shift variant, which used to ride on {@code KeyEvent.isShiftPressed()} — a flag no soft
     * keyboard sets. It travels as the case of the character now, and still forces the copy branch
     * for a URL that would otherwise be handed to another app.
     */
    @Test
    public void aCapitalHintLetterCopiesAUrlInsteadOfOpeningIt() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();

        TerminalHintsOverlay.show(activity, "see https://example.com/x now");
        assertTrue(activity.handleTerminalSheetCodePoint('A', false));

        assertEquals("https://example.com/x", clipboard(activity));
        assertFalse(sheet.isOpen());
    }

    /** A letter no hint carries is swallowed by the modal sheet rather than reaching the shell. */
    @Test
    public void anUnboundHintLetterLeavesThePickerUp() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();

        TerminalHintsOverlay.show(activity, "edit ./src/main.java please");
        assertTrue(activity.handleTerminalSheetCodePoint('z', false));

        assertTrue(sheet.isOpen());
        assertNull(clipboard(activity));
    }

    /** Prompts stack, and back has to give back the one underneath. */
    @Test
    public void backPopsOnePromptCard() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();
        TerminalHintsOverlay.show(activity, "edit ./src/main.java please");
        TerminalScrollbackSearchOverlay.show(activity, transcript(), row -> {});
        assertEquals(2, sheet.depth());

        assertTrue(activity.handleTerminalSheetKey(KeyEvent.KEYCODE_BACK,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));

        assertEquals("the search went, the hint picker under it stayed", 1, sheet.depth());
    }

    /**
     * The Save-workspace prompt: the caret is on the field before a key is pressed, and the answers
     * are the tick and the cross in its heading rather than a button row of their own.
     */
    @Test
    public void theSaveWorkspacePromptShowsItsCaretAndAnswersFromTheHeading() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();

        TerminalSessionBrowser.promptSaveWorkspace(activity);

        View card = sheet.topCard();
        assertNotNull(card);
        TextView field = findCaretField(card);
        assertNotNull("nothing said the prompt was already taking keys", field);
        assertTrue("the hint follows the caret rather than replacing it",
            field.getText().toString().startsWith("▏"));

        assertNotNull("the cross closes the panel", findByDescription(card, "Cancel"));
        assertNotNull("the tick commits it", findByDescription(card, "OK"));

        findByDescription(card, "Cancel").performClick();
        assertFalse(sheet.isOpen());
    }

    /**
     * The caret blinks like a text cursor, and typing holds it solid — the glyph itself never
     * leaves the string, so the draft does not shuffle sideways twice a second.
     */
    @Test
    public void theCaretBlinksWithoutMovingTheDraft() {
        TermuxActivity activity = laidOutActivity();
        TerminalScrollbackSearchOverlay.show(activity, transcript(), row -> {});
        View card = activity.getTerminalSheetController().topCard();
        TextView field = findCaretField(card);
        assertNotNull(field);

        assertTrue("the caret is solid the moment the prompt opens", caretVisible(field));
        String beforeBlink = field.getText().toString();

        org.robolectric.shadows.ShadowLooper.idleMainLooper(
            600L, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertFalse("half a second in, the caret is off", caretVisible(field));
        assertEquals("the glyph stays in the text so nothing moves", beforeBlink,
            field.getText().toString());

        activity.handleTerminalSheetCodePoint('e', false);
        assertTrue("typing holds the cursor solid", caretVisible(field));
    }

    /** Whether the caret glyph is painted: the blink is a colour span over it, not a deletion. */
    private static boolean caretVisible(@NonNull TextView field) {
        CharSequence text = field.getText();
        if (!(text instanceof android.text.Spanned)) return true;
        android.text.style.ForegroundColorSpan[] spans = ((android.text.Spanned) text).getSpans(
            0, text.length(), android.text.style.ForegroundColorSpan.class);
        for (android.text.style.ForegroundColorSpan span : spans) {
            if (android.graphics.Color.alpha(span.getForegroundColor()) == 0) return false;
        }
        return true;
    }

    @Nullable
    private static TextView findCaretField(@NonNull View view) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains("▏"))
            return (TextView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findCaretField(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    @Nullable
    private static View findByDescription(@NonNull View view, @NonNull String description) {
        CharSequence own = view.getContentDescription();
        if (own != null && description.contentEquals(own)) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findByDescription(group.getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
    }

    @NonNull
    private static List<TerminalScrollbackSearchModel.Line> transcript() {
        return Arrays.asList(
            new TerminalScrollbackSearchModel.Line(-2, "error: nothing here"),
            new TerminalScrollbackSearchModel.Line(-1, "error again"),
            new TerminalScrollbackSearchModel.Line(0, "everything is fine"));
    }

    /** The draft as the sheet renders it: the typed value plus the caret it draws instead of one. */
    @Nullable
    private static String typedLabel(@NonNull View card) {
        TextView field = findTyped(card);
        return field == null ? null : field.getText().toString();
    }

    @Nullable
    private static TextView findTyped(@NonNull View view) {
        if (view instanceof TextView && ((TextView) view).getText().toString().endsWith("▏"))
            return (TextView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findTyped(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    @Nullable
    private static ListView findListView(@NonNull View view) {
        if (view instanceof ListView) return (ListView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            ListView found = findListView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
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

    @Nullable
    private static String clipboard(@NonNull TermuxActivity activity) {
        ClipboardManager manager =
            (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || manager.getPrimaryClip() == null
            || manager.getPrimaryClip().getItemCount() == 0) return null;
        CharSequence text = manager.getPrimaryClip().getItemAt(0).getText();
        return text == null ? null : text.toString();
    }

    private static TermuxActivity laidOutActivity() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        return activity;
    }
}
