package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.res.Resources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;

import juloo.keyboard2.KeyValue;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.LayoutModifier;

/** Extra keys merge into the layout through {@code LayoutModifier.modify}. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class InAppKeyboardExtraKeysTest {

    private Resources resources;
    private KeyboardData layout;

    @Before
    public void setUp() {
        resources = RuntimeEnvironment.getApplication().getResources();
        layout = KeyboardData.load(resources, juloo.keyboard2.R.xml.latn_qwerty_us);
        assertNotNull(layout);
    }

    private LayoutModifier.LayoutOptions options(String storedCsv) {
        return new LayoutModifier.LayoutOptions(true, false, true,
            InAppKeyboardExtraKeys.resolve(storedCsv));
    }

    @Test
    public void locKeysAreStrippedWhenNotEnabled() {
        KeyboardData modified = LayoutModifier.modify(layout,
            new LayoutModifier.LayoutOptions(true, false, true), resources);

        // "loc esc" is declared on the q key's south-east slot.
        assertFalse(modified.getKeys().containsKey(KeyValue.getKeyByName("esc")));
        assertNull(modified.rows.get(0).keys.get(0).getKeyValue(4));
        // "loc tab" on the a key and the bottom row's loc-only keys go too.
        assertFalse(modified.getKeys().containsKey(KeyValue.getKeyByName("tab")));
        assertFalse(modified.getKeys().containsKey(KeyValue.getKeyByName("switch_greekmath")));
    }

    @Test
    public void enabledLocKeysAreKeptInPlace() {
        KeyboardData modified = LayoutModifier.modify(layout, options("tab,esc"), resources);

        assertTrue(modified.getKeys().containsKey(KeyValue.getKeyByName("esc")));
        assertTrue(modified.getKeys().containsKey(KeyValue.getKeyByName("tab")));
        // Kept in the slot the layout declared, not re-added elsewhere.
        assertEquals(KeyValue.getKeyByName("esc"),
            modified.rows.get(0).keys.get(0).getKeyValue(4));
        assertEquals(KeyValue.getKeyByName("tab"),
            modified.rows.get(1).keys.get(0).getKeyValue(1));
    }

    @Test
    public void enabledKeyAbsentFromLayoutIsPlaced() {
        KeyboardData modified = LayoutModifier.modify(layout, options("copy"), resources);

        assertTrue(modified.getKeys().containsKey(KeyValue.getKeyByName("copy")));
        // Preferred position is next to the c key, on its free south-east slot.
        assertEquals(KeyValue.getKeyByName("copy"),
            modified.rows.get(2).keys.get(3).getKeyValue(4));
    }

    @Test
    public void defaultsMatchTheTerminalFirstSelection() {
        Map<KeyValue, KeyboardData.PreferredPos> defaults =
            InAppKeyboardExtraKeys.resolve(InAppKeyboardExtraKeys.defaultStoredValue());
        String[] expected = { "tab", "esc", "capslock", "copy", "paste", "cut", "alt" };
        for (String name : expected)
            assertTrue(name + " should be enabled by default",
                defaults.containsKey(KeyValue.getKeyByName(name)));
        assertEquals(expected.length, defaults.size());
        // Navigation keys the terminal extra-keys bar covers stay off by default.
        for (String name : new String[]{ "home", "end", "page_up", "page_down", "compose",
                "switch_greekmath", "meta" })
            assertFalse(name + " should not be enabled by default",
                defaults.containsKey(KeyValue.getKeyByName(name)));
        // The sentinel and null resolve to the same defaults.
        assertEquals(defaults.keySet(),
            InAppKeyboardExtraKeys.resolve("__default__").keySet());
        assertEquals(defaults.keySet(), InAppKeyboardExtraKeys.resolve(null).keySet());
        // "loc €/ß/§/†" on QWERTY are not default-on, matching upstream.
        assertFalse(defaults.containsKey(KeyValue.getKeyByName("€")));
    }

    @Test
    public void modifyNeverMutatesTheSharedSourceLayout() {
        KeyboardData first = LayoutModifier.modify(layout, options("copy"), resources);
        KeyboardData second = LayoutModifier.modify(layout, options("cut"), resources);

        KeyValue copy = KeyValue.getKeyByName("copy");
        KeyValue cut = KeyValue.getKeyByName("cut");
        // The first result is unaffected by the second modification.
        assertTrue(first.getKeys().containsKey(copy));
        assertFalse(first.getKeys().containsKey(cut));
        assertTrue(second.getKeys().containsKey(cut));
        assertFalse(second.getKeys().containsKey(copy));
        // The shared source layout still has both anchor slots empty.
        assertNull(layout.rows.get(2).keys.get(3).getKeyValue(4)); // c, south-east
        assertNull(layout.rows.get(2).keys.get(2).getKeyValue(4)); // x, south-east
    }
}
