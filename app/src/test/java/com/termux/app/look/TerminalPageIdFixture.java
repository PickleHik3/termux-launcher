package com.termux.app.look;

import android.content.SharedPreferences;

import com.termux.shared.termux.settings.preferences.PageLookKeys;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Gives test preferences a Terminal page, so the Workspace half maps to its page overrides the
 * way it does in the app (the halves became pages, ADR 0015 6). Without one the Workspace reads
 * the wall's keys.
 */
public final class TerminalPageIdFixture {

    private TerminalPageIdFixture() { }

    public static final int TERMINAL_PAGE_ID = 2;

    /** Records the Terminal page in the preferences' store and tells the half shim. */
    public static TermuxAppSharedPreferences give(TermuxAppSharedPreferences prefs) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store != null)
            store.edit().putInt(PageLookKeys.KEY_TERMINAL_PAGE_ID, TERMINAL_PAGE_ID).commit();
        TermuxAppSharedPreferences.setTerminalPageId(TERMINAL_PAGE_ID);
        installKinds();
        return prefs;
    }

    /**
     * Tells every look grid that the Terminal page is the Terminal kind, as the app's wall does
     * (ADR 0020 §2: a page's look is its kind's). Other pages have no kind here, so they read
     * the wall's cells: Home's half stays the wall's and the Workspace's the Terminal kind's.
     */
    public static void installKinds() {
        com.termux.shared.termux.settings.preferences.LookGrid.install(page ->
            page == TERMINAL_PAGE_ID
                ? com.termux.shared.termux.settings.preferences.LookGrid.Kind.TERMINAL : null,
            null);
    }
}
