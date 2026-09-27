package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The keyboard's clipboard history: its bounds, its ordering, and what survives a restart. */
public class ClipboardHistoryTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    /** A pin store in memory, so a "restart" is a second history built on the same list. */
    private static final class MemoryPins implements ClipboardHistory.PinStore {
        List<String> saved = new ArrayList<>();
        int saves;

        @Override public List<String> load() {
            return new ArrayList<>(saved);
        }

        @Override public void save(List<String> pins) {
            saved = new ArrayList<>(pins);
            saves++;
        }
    }

    private static ClipboardHistory history(ClipboardHistory.PinStore store) {
        return new ClipboardHistory(store, Runnable::run);
    }

    @Test
    public void newestCopyIsFirstAndRecopyingMovesItUpInsteadOfDoubling() {
        ClipboardHistory history = history(new MemoryPins());
        assertTrue(history.record("one"));
        assertTrue(history.record("two"));
        assertTrue(history.record("three"));
        assertEquals(Arrays.asList("three", "two", "one"), history.recent());

        assertTrue(history.record("one"));
        assertEquals(Arrays.asList("one", "three", "two"), history.recent());
        assertEquals(3, history.recentCount());
    }

    @Test
    public void recentItemsAreBoundedOldestOut() {
        ClipboardHistory history = history(new MemoryPins());
        for (int i = 0; i < ClipboardHistory.MAX_RECENT + 5; i++) history.record("item " + i);
        assertEquals(ClipboardHistory.MAX_RECENT, history.recentCount());
        List<String> recent = history.recent();
        assertEquals("item " + (ClipboardHistory.MAX_RECENT + 4), recent.get(0));
        assertFalse(recent.contains("item 0"));
        assertFalse(recent.contains("item 4"));
        assertTrue(recent.contains("item 5"));
    }

    @Test
    public void blankAndOversizedTextsAreRefusedNotTruncated() {
        ClipboardHistory history = history(new MemoryPins());
        assertFalse(history.record(null));
        assertFalse(history.record(""));
        assertFalse(history.record("   \n\t "));

        StringBuilder big = new StringBuilder();
        while (big.length() <= ClipboardHistory.MAX_TEXT_BYTES) big.append("0123456789abcdef");
        assertFalse(ClipboardHistory.fits(big.toString()));
        assertFalse(history.record(big.toString()));
        assertTrue(history.isEmpty());

        // The cap is bytes, not chars: a multi-byte text trips it well under the char count.
        StringBuilder wide = new StringBuilder();
        for (int i = 0; i < ClipboardHistory.MAX_TEXT_BYTES / 3 + 10; i++) wide.append('€');
        assertTrue(wide.length() < ClipboardHistory.MAX_TEXT_BYTES);
        assertFalse(ClipboardHistory.fits(wide.toString()));

        String exact = new String(new char[ClipboardHistory.MAX_TEXT_BYTES]).replace('\0', 'a');
        assertTrue(ClipboardHistory.fits(exact));
        assertTrue(history.record(exact));
    }

    @Test
    public void pinsComeFirstAndAreBoundedWhileRecentsKeepFlowing() {
        MemoryPins pins = new MemoryPins();
        ClipboardHistory history = history(pins);
        history.record("a");
        history.record("b");
        history.record("c");
        assertTrue(history.pin("a"));
        assertTrue(history.pin("c"));
        assertEquals(Arrays.asList("c", "a"), history.pinned());
        assertEquals(Collections.singletonList("b"), history.recent());

        List<ClipboardHistory.Item> items = history.items();
        assertEquals(3, items.size());
        assertTrue(items.get(0).pinned);
        assertEquals("c", items.get(0).text);
        assertTrue(items.get(1).pinned);
        assertFalse(items.get(2).pinned);
        assertEquals("b", items.get(2).text);

        // Copying a pinned text again neither doubles it nor moves it into the recents.
        assertTrue(history.record("a"));
        assertEquals(Collections.singletonList("b"), history.recent());
        assertEquals(Arrays.asList("c", "a"), history.pinned());

        // Pins are capped; the refusal leaves the item where it was.
        for (int i = 0; i < ClipboardHistory.MAX_PINNED + 3; i++) {
            history.record("pin " + i);
            history.pin("pin " + i);
        }
        assertEquals(ClipboardHistory.MAX_PINNED, history.pinnedCount());
        assertFalse(history.pin("pin " + (ClipboardHistory.MAX_PINNED + 2)));
        assertTrue(history.recent().contains("pin " + (ClipboardHistory.MAX_PINNED + 2)));
        assertFalse(history.pin("never copied"));
    }

    @Test
    public void unpinAndRemoveAndClearBehaveDeliberately() {
        MemoryPins pins = new MemoryPins();
        ClipboardHistory history = history(pins);
        history.record("x");
        history.record("y");
        history.pin("x");
        assertTrue(history.unpin("x"));
        assertEquals(Arrays.asList("x", "y"), history.recent());
        assertEquals(0, history.pinnedCount());
        assertFalse(history.unpin("x"));

        history.pin("y");
        assertTrue(history.remove("y"));
        assertTrue(history.remove("x"));
        assertFalse(history.remove("x"));
        assertTrue(history.isEmpty());

        history.record("keep");
        history.pin("keep");
        history.record("gone 1");
        history.record("gone 2");
        history.clearRecent();
        assertEquals(0, history.recentCount());
        assertEquals(Collections.singletonList("keep"), history.pinned());
    }

    @Test
    public void pinsSurviveARestartAndRecentsDoNot() {
        MemoryPins pins = new MemoryPins();
        ClipboardHistory first = history(pins);
        first.record("recent only");
        first.record("pinned one");
        first.record("pinned two");
        first.pin("pinned one");
        first.pin("pinned two");
        assertEquals(2, pins.saves);

        // A new process: a fresh history on the same store.
        ClipboardHistory second = history(pins);
        assertEquals(Arrays.asList("pinned two", "pinned one"), second.pinned());
        assertEquals(0, second.recentCount());
        assertFalse(second.items().stream().anyMatch(item -> item.text.equals("recent only")));
    }

    @Test
    public void listenerHearsEveryChangeAndOnlyChanges() {
        ClipboardHistory history = history(new MemoryPins());
        int[] changes = {0};
        history.setListener(() -> changes[0]++);
        history.record("a");
        assertEquals(1, changes[0]);
        history.record("");
        assertEquals(1, changes[0]);
        history.pin("a");
        history.unpin("a");
        history.remove("a");
        assertEquals(4, changes[0]);
        history.clearRecent();
        assertEquals(4, changes[0]);
        history.setListener(null);
        history.record("b");
        assertEquals(4, changes[0]);
    }

    @Test
    public void fileStoreRoundTripsAndWritesNothingForNoPins() throws Exception {
        File file = new File(folder.getRoot(), "pins.json");
        ClipboardHistory.FilePinStore store = new ClipboardHistory.FilePinStore(file);
        assertTrue(store.load().isEmpty());

        store.save(Arrays.asList("first\nline two", "emoji 😀", "\"quoted\""));
        assertTrue(file.isFile());
        String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(json, json.contains("\"pins\""));
        assertTrue(json, json.contains("\"v\":" + ClipboardHistory.VERSION));
        assertEquals(Arrays.asList("first\nline two", "emoji 😀", "\"quoted\""), store.load());
        assertFalse(new File(folder.getRoot(), "pins.json.tmp").exists());

        store.save(Collections.emptyList());
        assertFalse(file.exists());
        assertTrue(store.load().isEmpty());
    }

    @Test
    public void fileStoreShrugsOffGarbage() throws Exception {
        File file = new File(folder.getRoot(), "pins.json");
        Files.write(file.toPath(), "not json".getBytes(StandardCharsets.UTF_8));
        ClipboardHistory.FilePinStore store = new ClipboardHistory.FilePinStore(file);
        assertTrue(store.load().isEmpty());
        ClipboardHistory history = new ClipboardHistory(store, Runnable::run);
        assertTrue(history.isEmpty());
        assertTrue(history.items().isEmpty());
    }

    @Test
    public void aStoreOverTheCapIsTrimmedOnLoad() {
        MemoryPins pins = new MemoryPins();
        for (int i = 0; i < ClipboardHistory.MAX_PINNED + 4; i++) pins.saved.add("p" + i);
        pins.saved.add("p0");
        ClipboardHistory history = history(pins);
        assertEquals(ClipboardHistory.MAX_PINNED, history.pinnedCount());
        assertEquals("p0", history.pinned().get(0));
    }
}
