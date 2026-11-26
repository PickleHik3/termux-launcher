package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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

public class SavedSessionsTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static TerminalWorkspace.Window window(String cwd, String... command) {
        return new TerminalWorkspace.Window(0,
            new TerminalWorkspace.Pane(cwd, null, Arrays.asList(command)));
    }

    private static TerminalWorkspace.Window homeFloat() {
        return new TerminalWorkspace.Window(0, new TerminalWorkspace.Pane("/f", null, null), null,
            null, new TerminalWorkspace.HomeFloat(3, false, null, null));
    }

    private static TerminalWorkspace.Session session(TerminalWorkspace.Window... windows) {
        return new TerminalWorkspace.Session(null, 0, Arrays.asList(windows));
    }

    @Test public void aNewSaveIsOneSessionOfOneWindowAndOpensAsThatWindow() {
        TerminalWorkspace.Window w = window("/home/a", "nvim");
        TerminalWorkspace saved = SavedSessions.of("dev", 5L, w);

        assertEquals(1, saved.sessions.size());
        assertEquals(1, saved.sessions.get(0).windows.size());
        assertEquals(Collections.singletonList(w), SavedSessions.windows(saved));
        TerminalWorkspace opening = SavedSessions.forOpening(saved);
        assertNotNull(opening);
        assertEquals("dev", opening.name);
        assertSame(w, opening.sessions.get(0).windows.get(0));
    }

    @Test public void anOldSaveOpensAsOneWorkspaceWithASessionPerWindowFirstInFront() {
        TerminalWorkspace.Window a = window("/a");
        TerminalWorkspace.Window b = window("/b");
        TerminalWorkspace.Window c = window("/c");
        TerminalWorkspace old = new TerminalWorkspace("old", 1L, 1,
            Arrays.asList(session(a, b), session(c)));

        assertEquals(Arrays.asList(a, b, c), SavedSessions.windows(old));
        TerminalWorkspace opening = SavedSessions.forOpening(old);
        assertNotNull(opening);
        assertEquals(1, opening.sessions.size());
        assertEquals(0, opening.sessions.get(0).currentWindow);
        assertEquals(Arrays.asList(a, b, c), opening.sessions.get(0).windows);
        assertEquals(0, opening.currentSession);
    }

    @Test public void homeFloatsAreLeftOutAndAFloatsOnlyFileHasNothingToOpen() {
        TerminalWorkspace.Window a = window("/a");
        TerminalWorkspace mixed = new TerminalWorkspace("mixed", 1L, 0,
            Collections.singletonList(session(a, homeFloat())));
        assertEquals(Collections.singletonList(a), SavedSessions.windows(mixed));

        TerminalWorkspace floatsOnly = new TerminalWorkspace("floats", 1L, 0,
            Collections.singletonList(session(homeFloat())));
        assertTrue(SavedSessions.windows(floatsOnly).isEmpty());
        assertNull(SavedSessions.forOpening(floatsOnly));
    }

    @Test public void renameKeepsTheDocumentAsItWasWrittenAndFreesTheOldName() throws Exception {
        TerminalWorkspaceStore store = new TerminalWorkspaceStore(temporary.newFolder("home"));
        // An old file: several windows, an older version number than this build writes.
        TerminalWorkspace old = new TerminalWorkspace("old", 1L, 0, Arrays.asList(
            session(window("/a"), window("/b")), session(window("/c"))));
        store.save("old", old, false);
        File file = store.fileForTesting("old");
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
            .replace("\"version\": " + TerminalWorkspace.VERSION, "\"version\": 3");
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));

        store.rename("old", "renamed");

        assertEquals(1, store.list().size());
        TerminalWorkspace loaded = store.load("renamed");
        assertEquals("renamed", loaded.name);
        assertEquals(3, loaded.version);
        assertEquals(3, SavedSessions.windows(loaded).size());
        assertFalse(file.exists());
    }

    @Test public void renameRefusesATakenNameAndAMissingFile() throws Exception {
        TerminalWorkspaceStore store = new TerminalWorkspaceStore(temporary.newFolder("home2"));
        store.save("a", SavedSessions.of("a", 1L, window("/a")), false);
        store.save("b", SavedSessions.of("b", 1L, window("/b")), false);
        try {
            store.rename("a", "b");
            assertTrue("a taken name must conflict", false);
        } catch (TerminalWorkspace.WorkspaceException e) {
            assertEquals("conflict", e.code);
        }
        try {
            store.rename("nothing", "c");
            assertTrue("a missing file must not rename", false);
        } catch (TerminalWorkspace.WorkspaceException e) {
            assertEquals("not_found", e.code);
        }
        List<String> names = new ArrayList<>();
        for (TerminalWorkspaceStore.Entry entry : store.list()) names.add(entry.name);
        assertEquals(Arrays.asList("a", "b"), names);
    }

    @Test public void theAutosaveKeepsWhichPlaceAWaitingSessionWaitsIn() throws Exception {
        TerminalWorkspace arrangement = new TerminalWorkspace("autosave", 1L, 0, Arrays.asList(
            session(window("/a"), window("/b")),
            new TerminalWorkspace.Session(null, 0, Collections.singletonList(window("/w1")), 1),
            new TerminalWorkspace.Session(null, 0, Collections.singletonList(window("/w2")), 0),
            session(window("/own"))));

        TerminalWorkspace decoded = TerminalWorkspace.fromJson(arrangement.toJson());

        assertEquals(-1, TerminalWorkspace.placeBehind(decoded.sessions, 0));
        assertEquals(1, TerminalWorkspace.placeBehind(decoded.sessions, 1));
        assertEquals(0, TerminalWorkspace.placeBehind(decoded.sessions, 2));
        // A session saved with no place is a Workspace of its own, as before.
        assertEquals(-1, TerminalWorkspace.placeBehind(decoded.sessions, 3));
        assertFalse(arrangement.toJson().getJSONArray("sessions").getJSONObject(3).has("behind"));
    }

    @Test public void aPlaceThatIsNotThereOrIsNotASingleWindowIsIgnored() {
        List<TerminalWorkspace.Session> sessions = Arrays.asList(
            session(window("/a")),
            new TerminalWorkspace.Session(null, 0, Collections.singletonList(window("/x")), 5),
            new TerminalWorkspace.Session(null, 0,
                Arrays.asList(window("/y"), window("/z")), 0),
            new TerminalWorkspace.Session(null, 0, Collections.singletonList(homeFloat()), 0));
        assertEquals(-1, TerminalWorkspace.placeBehind(sessions, 1));
        assertEquals(-1, TerminalWorkspace.placeBehind(sessions, 2));
        assertEquals(-1, TerminalWorkspace.placeBehind(sessions, 3));
        assertEquals(-1, TerminalWorkspace.placeBehind(sessions, 9));
    }
}
