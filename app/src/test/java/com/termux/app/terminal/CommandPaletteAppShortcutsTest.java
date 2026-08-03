package com.termux.app.terminal;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class CommandPaletteAppShortcutsTest {

    private static final String FIREFOX = "org.mozilla.firefox/.App";
    private static final String WORK_MAIL = "com.mail/.Main#userSerial=10";

    /** Resolves the three spellings the dispatcher accepts: package, label, stable id. */
    private static final CommandPaletteAppShortcuts.Lookup LOOKUP = query -> {
        switch (query) {
            case "org.mozilla.firefox":
            case "firefox":
            case FIREFOX:
                return FIREFOX;
            case WORK_MAIL:
                return WORK_MAIL;
            default:
                return null;
        }
    };

    @Test
    public void index_resolvesPackageLabelAndStableIdArgumentsAlike() {
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("org.mozilla.firefox", "ctrl+alt+f");

        assertEquals("ctrl+alt+f",
            CommandPaletteAppShortcuts.index(bindings, LOOKUP).get(FIREFOX));

        bindings.clear();
        bindings.put("firefox", "ctrl+alt+f");
        assertEquals("ctrl+alt+f",
            CommandPaletteAppShortcuts.index(bindings, LOOKUP).get(FIREFOX));

        bindings.clear();
        bindings.put(FIREFOX, "ctrl+alt+f");
        assertEquals("ctrl+alt+f",
            CommandPaletteAppShortcuts.index(bindings, LOOKUP).get(FIREFOX));
    }

    @Test
    public void index_dropsWhatItCannotResolveRatherThanGuessing() {
        // A row advertising a chord that launches something else is worse than showing none.
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("com.uninstalled.app", "ctrl+alt+u");
        bindings.put("", "ctrl+alt+e");

        assertEquals(0, CommandPaletteAppShortcuts.index(bindings, LOOKUP).size());
    }

    @Test
    public void index_keepsTheFirstStrokeWhenAnAppHasSeveral() {
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("org.mozilla.firefox", "ctrl+alt+f");
        bindings.put("firefox", "ctrl+alt+b");

        Map<String, String> indexed = CommandPaletteAppShortcuts.index(bindings, LOOKUP);
        assertEquals(1, indexed.size());
        assertEquals("ctrl+alt+f", indexed.get(FIREFOX));
    }

    @Test
    public void bindingArgument_isTheBarePackageForADefaultActivity() {
        assertEquals("org.mozilla.firefox",
            CommandPaletteAppShortcuts.bindingArgumentFor(FIREFOX, FIREFOX));
    }

    @Test
    public void bindingArgument_keepsTheStableIdForANonDefaultOrWorkProfileActivity() {
        // The package name would resolve to the personal-profile default, launching the wrong one.
        assertEquals(WORK_MAIL,
            CommandPaletteAppShortcuts.bindingArgumentFor(WORK_MAIL, "com.mail/.Main"));
        assertEquals(WORK_MAIL, CommandPaletteAppShortcuts.bindingArgumentFor(WORK_MAIL, null));
        assertEquals("com.mail/.Settings",
            CommandPaletteAppShortcuts.bindingArgumentFor("com.mail/.Settings", "com.mail/.Main"));
    }

    @Test
    public void packageOf_handlesAMalformedStableIdWithoutThrowing() {
        assertEquals("com.mail", CommandPaletteAppShortcuts.packageOf(WORK_MAIL));
        assertEquals("", CommandPaletteAppShortcuts.packageOf("nothing-here"));
        assertEquals("", CommandPaletteAppShortcuts.packageOf("/leading"));
        // A stable id with no activity part falls back to the full id rather than an empty query.
        assertEquals("bare", CommandPaletteAppShortcuts.bindingArgumentFor("bare", "bare"));
    }

    @Test
    public void lookupContract_returnsNullForAnUnknownQuery() {
        assertNull(LOOKUP.stableIdFor("com.unknown"));
        assertFalse(CommandPaletteAppShortcuts.index(
            new LinkedHashMap<>(), LOOKUP).containsKey(FIREFOX));
    }
}
