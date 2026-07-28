package com.termux.app.terminal;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TerminalWorkspaceStoreTest {

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void jsonRoundTrip_preservesNestedTreeFocusAndCommand() throws Exception {
        TerminalWorkspace workspace = sample("dev");
        TerminalWorkspace decoded = TerminalWorkspace.fromJson(workspace.toJson());

        assertEquals(TerminalWorkspace.VERSION, decoded.version);
        assertEquals("dev", decoded.name);
        assertEquals(3, decoded.paneCount());
        assertEquals(1, decoded.commandCount());
        assertEquals(1, decoded.sessions.get(0).windows.get(0).activePane);
        TerminalWorkspace.Split root =
            (TerminalWorkspace.Split) decoded.sessions.get(0).windows.get(0).root;
        assertEquals(TerminalWorkspace.Split.HORIZONTAL, root.orientation);
        assertEquals(1.25f, root.weightA, .001f);
        TerminalWorkspace.Pane commandPane = (TerminalWorkspace.Pane) ((TerminalWorkspace.Split) root.b).a;
        assertEquals(Arrays.asList("/data/data/com.termux/files/usr/bin/nvim", "notes.md"),
            commandPane.command);
    }

    @Test
    public void storeSaveLoadListDelete_andOverwritePolicy() throws Exception {
        TerminalWorkspaceStore store = new TerminalWorkspaceStore(temporary.newFolder("home"));
        store.save("dev", sample("dev"), false);

        assertEquals(3, store.load("dev").paneCount());
        assertEquals(1, store.list().size());
        assertEquals("dev", store.list().get(0).name);
        assertTrue(store.fileForTesting("dev").isFile());
        expectCode("conflict", () -> store.save("dev", sample("dev"), false));
        store.save("dev", sample("dev"), true);
        store.delete("dev");
        assertTrue(store.list().isEmpty());
        expectCode("not_found", () -> store.load("dev"));
    }

    @Test
    public void namesRejectTraversalSuffixAndLeadingPunctuation() throws Exception {
        assertEquals("Project 1.2", TerminalWorkspaceStore.validateName("  Project 1.2  "));
        expectCode("invalid_name", () -> TerminalWorkspaceStore.validateName("../escape"));
        expectCode("invalid_name", () -> TerminalWorkspaceStore.validateName(".hidden"));
        expectCode("invalid_name", () -> TerminalWorkspaceStore.validateName("work.json"));
        expectCode("invalid_name", () -> TerminalWorkspaceStore.validateName("a/b"));
        expectCode("invalid_name", () -> TerminalWorkspaceStore.validateName("a\\b"));
    }

    @Test
    public void loadRejectsUnsupportedVersionCorruptJsonAndMismatchedName() throws Exception {
        File home = temporary.newFolder("home");
        TerminalWorkspaceStore store = new TerminalWorkspaceStore(home);
        store.save("dev", sample("dev"), false);
        File file = store.fileForTesting("dev");

        JSONObject unsupported = sample("dev").toJson().put("version", 99);
        Files.write(file.toPath(), unsupported.toString().getBytes(StandardCharsets.UTF_8));
        expectCode("unsupported_version", () -> store.load("dev"));

        Files.write(file.toPath(), "{".getBytes(StandardCharsets.UTF_8));
        expectCode("invalid_workspace", () -> store.load("dev"));

        Files.write(file.toPath(), sample("other").toJson().toString().getBytes(StandardCharsets.UTF_8));
        expectCode("invalid_workspace", () -> store.load("dev"));
    }

    @Test
    public void validationRejectsBadIndexesWeightsAndEmptySessions() throws Exception {
        TerminalWorkspace empty = new TerminalWorkspace("empty", 1L, 0, Collections.emptyList());
        expectCode("invalid_workspace", empty::validate);

        TerminalWorkspace.Window badWindow = new TerminalWorkspace.Window(2,
            new TerminalWorkspace.Split(TerminalWorkspace.Split.HORIZONTAL, Float.NaN, 1f,
                new TerminalWorkspace.Pane("/", null, null),
                new TerminalWorkspace.Pane("/", null, null)));
        TerminalWorkspace bad = new TerminalWorkspace("bad", 1L, 0,
            Collections.singletonList(new TerminalWorkspace.Session(null, 0,
                Collections.singletonList(badWindow))));
        expectCode("invalid_workspace", bad::validate);
    }

    private static TerminalWorkspace sample(String name) {
        TerminalWorkspace.Node nested = new TerminalWorkspace.Split(
            TerminalWorkspace.Split.VERTICAL, .8f, 1.2f,
            new TerminalWorkspace.Pane("/home/project", "editor",
                Arrays.asList("/data/data/com.termux/files/usr/bin/nvim", "notes.md")),
            new TerminalWorkspace.Pane("/home/project/tests", null, null));
        TerminalWorkspace.Node root = new TerminalWorkspace.Split(
            TerminalWorkspace.Split.HORIZONTAL, 1.25f, .75f,
            new TerminalWorkspace.Pane("/home", "shell", null), nested);
        TerminalWorkspace.Window window = new TerminalWorkspace.Window(1, root);
        TerminalWorkspace.Session session = new TerminalWorkspace.Session(
            "project", 0, Collections.singletonList(window));
        return new TerminalWorkspace(name, 1234L, 0, Collections.singletonList(session));
    }

    private interface ThrowingRunnable { void run() throws Exception; }

    private static void expectCode(String code, ThrowingRunnable action) throws Exception {
        try {
            action.run();
            fail("Expected workspace error " + code);
        } catch (TerminalWorkspace.WorkspaceException e) {
            assertEquals(code, e.code);
            assertFalse(e.getMessage().isEmpty());
        }
    }
}
