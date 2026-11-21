package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;

import com.termux.shared.termux.extrakeys.ExtraKeyIcons;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import juloo.keyboard2.KeyValue;

/** The keyboard's Roundline table: every mapped symbol has a drawing, the rest stay on the font. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class KeyboardSymbolIconsTest {

    private KeyboardSymbolIcons icons;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        icons = new KeyboardSymbolIcons(context);
    }

    private static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    @Test
    public void everyMappedKeyFontSymbolHasADrawable() {
        for (int[] row : KeyboardSymbolIcons.KEY_FONT_ICONS) {
            assertNotNull("U+" + Integer.toHexString(row[0]), icons.iconFor(cp(row[0]), true));
        }
        for (int[] row : KeyboardSymbolIcons.OTHER_ICONS) {
            assertNotNull("U+" + Integer.toHexString(row[0]), icons.iconFor(cp(row[0]), false));
        }
    }

    @Test
    public void everyKeyFontSymbolTheKeyboardNamesMapsToItsKey() {
        assertEquals(KeyValue.getKeyByName("enter").getString(), cp(0xE00E));
        assertEquals(KeyValue.getKeyByName("backspace").getString(), cp(0xE011));
        assertEquals(KeyValue.getKeyByName("delete").getString(), cp(0xE010));
        assertEquals(KeyValue.getKeyByName("tab").getString(), cp(0xE00F));
        assertEquals(KeyValue.getKeyByName("space").getString(), cp(0xE00D));
        assertEquals(KeyValue.getKeyByName("up").getString(), cp(0xE005));
        assertEquals(KeyValue.getKeyByName("right").getString(), cp(0xE006));
        assertEquals(KeyValue.getKeyByName("down").getString(), cp(0xE007));
        assertEquals(KeyValue.getKeyByName("left").getString(), cp(0xE008));
        assertEquals(KeyValue.getKeyByName("page_up").getString(), cp(0xE002));
        assertEquals(KeyValue.getKeyByName("page_down").getString(), cp(0xE003));
        assertEquals(KeyValue.getKeyByName("home").getString(), cp(0xE00B));
        assertEquals(KeyValue.getKeyByName("end").getString(), cp(0xE00C));
        assertEquals(KeyValue.getKeyByName("paste").getString(), cp(0xE032));
        assertEquals(KeyValue.getKeyByName("undo").getString(), cp(0xE036));
        assertEquals(KeyValue.getKeyByName("voice_typing").getString(), cp(0xE015));
        assertEquals(KeyValue.getKeyByName("textAssist").getString(), cp(0xE038));
    }

    @Test
    public void everyBarIconCodePointHasADrawable() {
        for (int icon : ExtraKeyIcons.ALL) {
            if (icon == ExtraKeyIcons.PAGE_SWITCHER) continue;
            assertNotNull("U+" + Integer.toHexString(icon), icons.iconFor(cp(icon), false));
        }
    }

    @Test
    public void symbolsWithNoDrawingYetStayOnTheFont() {
        int[] kept = {0xE00A /* shift */, 0xE034 /* share */, 0xE035 /* paste plain */};
        for (int c : kept) assertNull("U+" + Integer.toHexString(c), icons.iconFor(cp(c), true));
    }

    @Test
    public void noCodePointIsMappedTwice() {
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int[] row : KeyboardSymbolIcons.KEY_FONT_ICONS) {
            assertEquals("U+" + Integer.toHexString(row[0]), true, seen.add(row[0]));
        }
        seen.clear();
        for (int[] row : KeyboardSymbolIcons.OTHER_ICONS) {
            assertEquals("U+" + Integer.toHexString(row[0]), true, seen.add(row[0]));
        }
    }

    @Test
    public void configAndTheInputMethodKeysAreIcons() {
        assertEquals(cp(0xE004), KeyValue.CONFIG.getString());
        assertEquals(cp(0xE009), KeyValue.CHANGE_METHOD.getString());
        assertEquals(cp(0xE013), KeyValue.getKeyByName("switch_forward").getString());
        assertEquals(cp(0xE014), KeyValue.getKeyByName("switch_backward").getString());
        assertEquals(cp(0xE012), KeyValue.getKeyByName("capslock").getString());
        assertEquals(cp(0xE01B), KeyValue.getKeyByName("delete_word").getString());
        assertEquals(cp(0xE01C), KeyValue.getKeyByName("forward_delete_word").getString());
        assertEquals(cp(0xE037), KeyValue.getKeyByName("redo").getString());
        assertEquals(cp(0xE031), KeyValue.getKeyByName("cut").getString());
        assertEquals(cp(0xE030), KeyValue.getKeyByName("copy").getString());
        assertEquals(cp(0xE033), KeyValue.getKeyByName("selectAll").getString());
        assertEquals(cp(0xE001), KeyValue.getKeyByName("switch_emoji").getString());
        assertEquals(cp(0xE017), KeyValue.getKeyByName("switch_clipboard").getString());
        assertEquals(cp(0xE016), KeyValue.COMPOSE.getString());
        for (int c : new int[] {0xE004, 0xE009, 0xE013, 0xE014, 0xE012, 0xE01B, 0xE01C, 0xE037,
            0xE031, 0xE030, 0xE033, 0xE001, 0xE017, 0xE016, 0xE01A}) {
            assertNotNull("U+" + Integer.toHexString(c), icons.iconFor(cp(c), true));
        }
    }

    @Test
    public void accentAndCombiningMarksNeverMap() {
        for (int c = 0xE050; c <= 0xE063; c++) assertNull(icons.iconFor(cp(c), true));
        for (int c = 0xE150; c <= 0xE162; c++) assertNull(icons.iconFor(cp(c), true));
        for (int c = 0xE200; c <= 0xE223; c++) assertNull(icons.iconFor(cp(c), true));
        assertNull(icons.iconFor(cp(0xE050), false));
    }

    @Test
    public void aKeyFontCodePointIsNotAnIconWithoutTheKeyFont() {
        // The same BMP code point is some other Nerd glyph when it is not the key font's.
        assertNull(icons.iconFor(cp(0xE00E), false));
        assertNull(icons.iconFor(cp(0xF02E9 + 1), false));
    }

    @Test
    public void textAndLongLabelsStayText() {
        assertNull(icons.iconFor("a", false));
        assertNull(icons.iconFor("", false));
        assertNull(icons.iconFor(cp(0xE00E) + cp(0xE00E), true));
    }

    @Test
    public void theLayoutsShippedToolGlyphsResolveThroughTheBarsTable() {
        // The command palette's slot on the space bar: the keyboard layouts' own glyph.
        String palette = icons.toolLabel("app.command_palette", cp(0xF13B1));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.COMMAND), palette);
        assertNotNull(icons.iconFor(palette, false));
        // An old shipped display of a tool the bar already knows.
        String dictate = icons.toolLabel("voice.dictate", cp(0xF036C));
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.MIC), dictate);
        assertNotNull(icons.iconFor(dictate, false));
    }

    @Test
    public void windowAndSessionMarksAndUsersOwnGlyphsKeepTheirSymbol() {
        for (String[] mark : new String[][] {
            {"window.previous", cp(0xF0733)}, {"window.next", cp(0xF0736)},
            {"session.previous", cp(0xF0739)}, {"session.next", cp(0xF0730)},
            {"app.command_palette", "x"}}) {
            assertEquals(mark[1], icons.toolLabel(mark[0], mark[1]));
        }
    }

    @Test
    public void thePastePictureStaysAGlyphWhilePlainPasteIsAnIcon() {
        assertNotNull(icons.iconFor(cp(0xE032), true));
        assertNotNull(icons.iconFor(cp(0xF02E9), false));
        assertNotEquals(KeyboardSymbolIcons.drawableFor(0xF02E9, false),
            KeyboardSymbolIcons.drawableFor(0xE032, true));
    }

    @Test
    public void windowAndSessionMarksAreIcons() {
        for (int c : new int[] {0xF0733, 0xF0736, 0xF0739, 0xF0730}) {
            assertNotNull("U+" + Integer.toHexString(c), icons.iconFor(cp(c), false));
        }
    }
}
