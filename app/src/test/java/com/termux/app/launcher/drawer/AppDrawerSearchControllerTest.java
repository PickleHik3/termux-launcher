package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import com.termux.app.terminal.ClipboardText;

import juloo.keyboard2.KeyValue;

/** The in-app keyboard's PASTE / PASTE_PLAIN and the editing keys the drawer search still drops. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerSearchControllerTest {

    @Test public void pasteAndPasteAsPlainTextInsertTheClipboardTextSanitizedToOneLine() {
        AppDrawerSearchController search = activeSearch(() -> "one\r\ntwo");

        assertTrue(search.interceptKeyValue(
            KeyValue.getKeyByName("paste"), false, false, false));
        assertEquals("one two", search.query());

        assertTrue(search.interceptKeyValue(
            KeyValue.getKeyByName("pasteAsPlainText"), false, false, false));
        assertEquals("one twoone two", search.query());
    }

    /** Copy, cut and select-all in the query stay unhandled: swallowed, and the query untouched. */
    @Test public void copyCutAndSelectAllAreSwallowedWithoutEffect() {
        AppDrawerSearchController search = activeSearch(() -> "never read");
        search.replaceQuery("gr", 2);

        assertTrue(search.interceptKeyValue(KeyValue.getKeyByName("copy"), false, false, false));
        assertTrue(search.interceptKeyValue(KeyValue.getKeyByName("cut"), false, false, false));
        assertTrue(
            search.interceptKeyValue(KeyValue.getKeyByName("selectAll"), false, false, false));
        assertEquals("gr", search.query());
    }

    private static AppDrawerSearchController activeSearch(ClipboardText clipboardText) {
        AppDrawerSearchController search = new AppDrawerSearchController(clipboardText);
        search.setHost(new AppDrawerSearchController.Host() {
            @Override public boolean isSearchActive() { return true; }
            @Override public void onSearchCommitRequested() {}
            @Override public void onSearchDismissRequested() {}
        });
        return search;
    }
}
