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
        return prefs;
    }
}
