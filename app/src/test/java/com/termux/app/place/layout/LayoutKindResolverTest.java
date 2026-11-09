package com.termux.app.place.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.WallPages.PageKind;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** Which layout a page wears (ADR 0020 §1, spec §9 point 3; ADR 0025). */
public class LayoutKindResolverTest {

    @Test
    public void homeWearsItsOwnAndEveryWorkspacePageTheWorkspaces() {
        assertEquals(LayoutKind.HOME, LayoutKind.of(PageKind.HOME, false));
        assertEquals(LayoutKind.TERMINAL, LayoutKind.of(PageKind.TERMINAL, false));
        assertEquals(LayoutKind.TERMINAL, LayoutKind.of(PageKind.DISPLAY, false));
        assertEquals(LayoutKind.TERMINAL, LayoutKind.of(PageKind.ASSISTANT, false));
    }

    @Test
    public void minimalDressesTheWholeWorkspaceButNeverHome() {
        assertEquals(LayoutKind.HOME, LayoutKind.of(PageKind.HOME, true));
        assertEquals(LayoutKind.TERMINAL_MINIMAL, LayoutKind.of(PageKind.TERMINAL, true));
        assertEquals(LayoutKind.TERMINAL_MINIMAL, LayoutKind.of(PageKind.DISPLAY, true));
        assertEquals(LayoutKind.TERMINAL_MINIMAL, LayoutKind.of(PageKind.ASSISTANT, true));
    }

    @Test
    public void theEditorNamesTheLayout() {
        assertEquals("Home layout", LayoutKind.HOME.title);
        assertEquals("Workspace layout", LayoutKind.TERMINAL.title);
        assertEquals("Workspace minimal layout", LayoutKind.TERMINAL_MINIMAL.title);
    }

    @Test
    public void keysNameTheDocuments() {
        for (LayoutKind kind : LayoutKind.values())
            assertEquals(kind, LayoutKind.forKey(kind.key));
        assertNull(LayoutKind.forKey("page1"));
    }

    @Test
    public void theDisplayAndAssistantDocumentsAreRetiredIntoTheWorkspaces() {
        assertTrue(LayoutKind.DISPLAY.retired());
        assertTrue(LayoutKind.ASSISTANT.retired());
        assertFalse(LayoutKind.TERMINAL.retired());
        assertFalse(LayoutKind.HOME.retired());
        assertFalse(LayoutKind.TERMINAL_MINIMAL.retired());
        assertSame(LayoutKind.TERMINAL, LayoutKind.DISPLAY.live());
        assertSame(LayoutKind.TERMINAL, LayoutKind.ASSISTANT.live());
        assertSame(LayoutKind.TERMINAL_MINIMAL, LayoutKind.TERMINAL_MINIMAL.live());
    }

    @Test
    public void aRetiredKindReadsAndWritesTheWorkspacesDocument() {
        KindLayout terminal = KindLayout.EMPTY.with(PlaceOrientation.PORTRAIT, "bar_order", "a,b");
        Layouts layouts = Layouts.shipped().withKind(LayoutKind.TERMINAL, terminal);
        assertEquals(terminal, layouts.kind(LayoutKind.DISPLAY));
        assertEquals(terminal, layouts.kind(LayoutKind.ASSISTANT));
        assertEquals(terminal, layouts.forPage(PageKind.ASSISTANT, false));
        KindLayout edited = terminal.with(PlaceOrientation.PORTRAIT, "bar_order", "b,a");
        assertEquals(edited,
            layouts.withKind(LayoutKind.ASSISTANT, edited).kind(LayoutKind.TERMINAL));
    }

    /**
     * The migration (ADR 0025): an install that holds its own Display and Assistant documents
     * wears the Terminal's on every Workspace page; the old documents read without harm and are
     * left in the file as they were, for a downgrade.
     */
    @Test
    public void anUpgradeWearsTheTerminalsDocumentAndLeavesTheOldOnes() {
        Map<String, String> file = new HashMap<>();
        String display = "{\"version\":1,\"portrait\":{\"bar_order\":\"display\"},"
            + "\"landscape\":{}}";
        String assistant = "{\"version\":1,\"portrait\":{\"dress.extra_keys\":false},"
            + "\"landscape\":{}}";
        file.put(LayoutKind.TERMINAL.key, LayoutsJson.write(
            KindLayout.EMPTY.with(PlaceOrientation.PORTRAIT, "bar_order", "terminal")));
        file.put(LayoutKind.DISPLAY.key, display);
        file.put(LayoutKind.ASSISTANT.key, assistant);
        LayoutStore store = new LayoutStore(new LayoutStore.Storage() {
            @Override public String read(String key) { return file.get(key); }
            @Override public void write(Map<String, String> documents) { file.putAll(documents); }
        }, false);
        Layouts layouts = store.current();
        for (PageKind kind : new PageKind[]{PageKind.TERMINAL, PageKind.DISPLAY,
                PageKind.ASSISTANT}) {
            KindLayout worn = layouts.forPage(kind, false);
            assertEquals(kind.name(), "terminal", worn.portrait().string("bar_order"));
            assertFalse(kind.name(), worn.portrait().has("dress.extra_keys"));
        }
        // A write from any Workspace page lands on the Terminal's document alone.
        store.update(LayoutKind.ASSISTANT,
            k -> k.with(PlaceOrientation.PORTRAIT, "bar_order", "edited"));
        assertEquals("edited",
            store.current().kind(LayoutKind.TERMINAL).portrait().string("bar_order"));
        store.replaceAll(store.current());
        assertEquals(display, file.get(LayoutKind.DISPLAY.key));
        assertEquals(assistant, file.get(LayoutKind.ASSISTANT.key));
        assertFalse(LayoutStore.documents(Layouts.shipped()).containsKey("assistant"));
        assertFalse(LayoutStore.documents(Layouts.shipped()).containsKey("display"));
    }
}
