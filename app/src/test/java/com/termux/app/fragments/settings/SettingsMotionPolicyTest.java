package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;

import com.termux.app.fragments.settings.SettingsMotionPolicy.Kind;
import com.termux.app.fragments.settings.SettingsMotionPolicy.Move;

import org.junit.Test;

/** F2: one motion per kind of move, and an instant cut whenever motion is off. */
public class SettingsMotionPolicyTest {

    private static final String PAGES = "com.termux.app.fragments.settings.termux.";
    private static final String HOME = PAGES + "HomePreferencesFragment";
    private static final String TERMINAL = PAGES + "TerminalPreferencesFragment";
    private static final String TEXT = PAGES + "TerminalTextPreferencesFragment";
    private static final String KEYBOARD = PAGES + "KeyboardPreferencesFragment";
    private static final String THEME = PAGES + "KeyboardColorSchemeFragment";
    private static final String AI = PAGES + "TaiPreferencesFragment";
    private static final String VOICE = PAGES + "KeyboardVoicePreferencesFragment";

    @Test
    public void goingDeeperIsTheSharedAxis() {
        assertEquals(Kind.SHARED_AXIS, SettingsMotionPolicy.choose(Move.PUSH, SettingsTree.ROOT, HOME, true, false));
        assertEquals(Kind.SHARED_AXIS, SettingsMotionPolicy.choose(Move.PUSH, TERMINAL, TEXT, true, false));
        assertEquals("a screen the tree does not know still slides",
            Kind.SHARED_AXIS, SettingsMotionPolicy.choose(Move.PUSH, AI, PAGES + "TaiBenchHomeFragment", false, false));
    }

    @Test
    public void aRowOpeningItsEditorGrowsOutOfTheRow() {
        assertEquals(Kind.CONTAINER, SettingsMotionPolicy.choose(Move.PUSH, KEYBOARD, THEME, true, false));
        assertEquals("no row on screen to grow from: a slide",
            Kind.SHARED_AXIS, SettingsMotionPolicy.choose(Move.PUSH, KEYBOARD, THEME, false, false));
    }

    @Test
    public void crossingTheTreeFadesThrough() {
        assertEquals(Kind.FADE_THROUGH, SettingsMotionPolicy.choose(Move.JUMP, TERMINAL, TEXT, false, false));
        assertEquals(Kind.FADE_THROUGH, SettingsMotionPolicy.choose(Move.JUMP, null, HOME, false, false));
        assertEquals("a button on one section's page opening another section's page",
            Kind.FADE_THROUGH, SettingsMotionPolicy.choose(Move.PUSH, AI, VOICE, false, false));
    }

    @Test
    public void reducedMotionAndRestoresAreAnInstantCut() {
        for (Move move : Move.values()) {
            assertEquals(move.name(), Kind.NONE, SettingsMotionPolicy.choose(move, KEYBOARD, THEME, true, true));
        }
        assertEquals(Kind.NONE, SettingsMotionPolicy.choose(Move.RESTORE, TERMINAL, TEXT, false, false));
    }
}
