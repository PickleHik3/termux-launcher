package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.R;
import com.termux.app.TermuxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import juloo.keyboard2.KeyValue;

/**
 * The palette's own paste key, in {@link TerminalCommandPaletteController}'s normal (list) mode.
 *
 * <p>The palette binds the activity's real layout through a {@link FakeCommandPaletteHost}
 * ({@link TerminalSheetPromptsTest} lays out the same activity for the sheet plane), and uses the
 * package-private constructor that injects a fake {@link ClipboardText} rather than the real
 * {@code ClipboardManager}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalCommandPalettePasteTest {

    @Test
    public void pasteAndPasteAsPlainTextInsertTheClipboardTextSanitizedToOneLine() {
        TermuxActivity activity = laidOutActivity();
        TerminalCommandPaletteController palette =
            new TerminalCommandPaletteController(hostOver(activity), () -> "one\r\ntwo");
        palette.show();
        assertTrue(palette.isOpen());

        assertTrue(palette.interceptKeyValue(
            KeyValue.getKeyByName("paste"), false, false, false));
        assertEquals("one two", palette.query());

        assertTrue(palette.interceptKeyValue(
            KeyValue.getKeyByName("pasteAsPlainText"), false, false, false));
        assertEquals("one twoone two", palette.query());
    }

    /** Copy, cut and select-all in the query stay unhandled: swallowed, and the query untouched. */
    @Test
    public void copyCutAndSelectAllAreSwallowedWithoutEffect() {
        TermuxActivity activity = laidOutActivity();
        TerminalCommandPaletteController palette =
            new TerminalCommandPaletteController(hostOver(activity), () -> "never read");
        palette.show();
        palette.interceptKeyValue(KeyValue.makeCharKey('g'), false, false, false);

        assertTrue(palette.interceptKeyValue(
            KeyValue.getKeyByName("copy"), false, false, false));
        assertTrue(palette.interceptKeyValue(
            KeyValue.getKeyByName("cut"), false, false, false));
        assertTrue(palette.interceptKeyValue(
            KeyValue.getKeyByName("selectAll"), false, false, false));
        assertEquals("g", palette.query());
    }

    private static TermuxActivity laidOutActivity() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        return activity;
    }

    private static FakeCommandPaletteHost hostOver(TermuxActivity activity) {
        FakeCommandPaletteHost host = new FakeCommandPaletteHost(activity);
        host.viewRoot = activity.getWindow().getDecorView();
        return host;
    }
}
