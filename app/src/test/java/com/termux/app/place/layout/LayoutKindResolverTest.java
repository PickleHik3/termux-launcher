package com.termux.app.place.layout;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.wall.WallPages.PageKind;

import org.junit.Test;

/** Which layout a page wears (ADR 0020 §1, spec §9 point 3). */
public class LayoutKindResolverTest {

    @Test
    public void eachKindWearsItsOwn() {
        assertEquals(LayoutKind.HOME, LayoutKind.of(PageKind.HOME, false));
        assertEquals(LayoutKind.TERMINAL, LayoutKind.of(PageKind.TERMINAL, false));
        assertEquals(LayoutKind.DISPLAY, LayoutKind.of(PageKind.DISPLAY, false));
    }

    @Test
    public void minimalDressesTheTerminalAndTheDisplayButNeverHome() {
        assertEquals(LayoutKind.HOME, LayoutKind.of(PageKind.HOME, true));
        assertEquals(LayoutKind.TERMINAL_MINIMAL, LayoutKind.of(PageKind.TERMINAL, true));
        assertEquals(LayoutKind.TERMINAL_MINIMAL, LayoutKind.of(PageKind.DISPLAY, true));
    }

    @Test
    public void theEditorNamesTheLayout() {
        assertEquals("Home layout", LayoutKind.HOME.title);
        assertEquals("Terminal layout", LayoutKind.TERMINAL.title);
        assertEquals("Display layout", LayoutKind.DISPLAY.title);
        assertEquals("Terminal minimal layout", LayoutKind.TERMINAL_MINIMAL.title);
    }

    @Test
    public void keysNameTheDocuments() {
        for (LayoutKind kind : LayoutKind.values())
            assertEquals(kind, LayoutKind.forKey(kind.key));
        assertNull(LayoutKind.forKey("page1"));
    }

    @Test
    public void theAssistantWearsItsOwnLayoutWhateverMinimalSays() {
        assertEquals(LayoutKind.ASSISTANT, LayoutKind.of(PageKind.ASSISTANT, false));
        assertEquals(LayoutKind.ASSISTANT, LayoutKind.of(PageKind.ASSISTANT, true));
        assertEquals(PageKind.ASSISTANT, LayoutKind.ASSISTANT.pageKind());
        assertEquals("assistant", LayoutKind.ASSISTANT.key);
        assertEquals("Assistant layout", LayoutKind.ASSISTANT.title);
        assertEquals(LayoutKind.ASSISTANT, LayoutKind.forKey("assistant"));
    }

    @Test
    public void theAssistantDocumentStartsFromHomesWithoutItsGridDressOrKeyboard() {
        KindLayout home = KindLayout.EMPTY
            .withWide("dock_design", "glass_tray")
            .withWide(LayoutsJson.KEYBOARD_OPEN, true)
            .with(com.termux.app.place.PlaceOrientation.PORTRAIT, "widget_columns", 5)
            .with(com.termux.app.place.PlaceOrientation.PORTRAIT, "dress.status", false)
            .with(com.termux.app.place.PlaceOrientation.PORTRAIT, "bar_order", "a,b");
        Layouts layouts = Layouts.shipped().withKind(LayoutKind.HOME, home);
        assertTrue(AssistantSeed.needsSeed(layouts));
        Layouts seeded = AssistantSeed.seeded(layouts);
        KindLayout assistant = seeded.kind(LayoutKind.ASSISTANT);
        assertEquals("glass_tray", assistant.wide().get("dock_design"));
        assertFalse(assistant.wide().has(LayoutsJson.KEYBOARD_OPEN));
        assertFalse(assistant.portrait().has("widget_columns"));
        assertFalse(assistant.portrait().has("dress.status"));
        assertEquals("a,b", assistant.portrait().get("bar_order"));
        assertEquals("Home keeps its own", home, seeded.kind(LayoutKind.HOME));
        // Once written, it is never seeded again.
        KindLayout edited = assistant.withWide("dock_design", "other");
        Layouts again = seeded.withKind(LayoutKind.ASSISTANT, edited);
        assertFalse(AssistantSeed.needsSeed(again));
        assertSame(again, AssistantSeed.seeded(again));
    }

    @Test
    public void theAssistantDocumentIsOneOfTheDocumentsTheFileHolds() {
        assertTrue(LayoutStore.documents(Layouts.shipped()).containsKey("assistant"));
    }
}
