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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

    @Test
    public void jsonRoundTrip_preservesFloatingPanesAndBounds() throws Exception {
        TerminalWorkspace decoded = TerminalWorkspace.fromJson(sampleWithFloat("dev").toJson());

        assertEquals(4, decoded.paneCount());
        assertEquals(2, decoded.commandCount());
        TerminalWorkspace.Window window = decoded.sessions.get(0).windows.get(0);
        // The focused float indexes after the three tiled panes.
        assertEquals(3, window.activePane);
        assertEquals(1, window.floats.size());
        TerminalWorkspace.FloatingPane floating = window.floats.get(0);
        assertEquals("/home/floating", floating.pane.cwd);
        assertEquals(Collections.singletonList("/data/data/com.termux/files/usr/bin/htop"),
            floating.pane.command);
        assertEquals(.2f, floating.left, .001f);
        assertEquals(.15f, floating.top, .001f);
        assertEquals(.6f, floating.width, .001f);
        assertEquals(.5f, floating.height, .001f);
    }

    @Test
    public void versionOneJsonWithoutFloats_stillLoads() throws Exception {
        // Files written before floating panes carry version 1 and no floats key at all.
        JSONObject legacy = sample("dev").toJson().put("version", 1);
        TerminalWorkspace decoded = TerminalWorkspace.fromJson(legacy);

        assertEquals(1, decoded.version);
        assertEquals(3, decoded.paneCount());
        assertTrue(decoded.sessions.get(0).windows.get(0).floats.isEmpty());
    }

    @Test
    public void validationRejectsBadFloatBoundsAndCountsFloatsIntoActivePane() throws Exception {
        TerminalWorkspace.FloatingPane badBounds = new TerminalWorkspace.FloatingPane(
            new TerminalWorkspace.Pane("/", null, null), 0f, 0f, Float.NaN, .5f);
        expectCode("invalid_workspace", () -> workspaceAround(
            new TerminalWorkspace.Window(0, new TerminalWorkspace.Pane("/", null, null),
                Collections.singletonList(badBounds))).validate());

        TerminalWorkspace.FloatingPane sane = new TerminalWorkspace.FloatingPane(
            new TerminalWorkspace.Pane("/", null, null), .1f, .1f, .5f, .5f);
        // One tiled pane plus one float: index 1 is the float, index 2 is out of range.
        workspaceAround(new TerminalWorkspace.Window(1,
            new TerminalWorkspace.Pane("/", null, null),
            Collections.singletonList(sane))).validate();
        expectCode("invalid_workspace", () -> workspaceAround(
            new TerminalWorkspace.Window(2, new TerminalWorkspace.Pane("/", null, null),
                Collections.singletonList(sane))).validate());
    }

    @Test
    public void homeFloatRoundTrip_keepsPageMinimisedAndBothShapes() throws Exception {
        TerminalWorkspace.HomeFloat saved = new TerminalWorkspace.HomeFloat(1, true,
            new TerminalWorkspace.FloatShape(.1f, .2f, .5f, .4f),
            new TerminalWorkspace.FloatShape(.3f, .05f, .6f, .9f));
        TerminalWorkspace decoded = TerminalWorkspace.fromJson(withHomeFloat(saved).toJson());

        assertEquals(5, decoded.version);
        assertEquals(2, decoded.sessions.get(0).windows.size());
        assertNull("the row's window stays a window of the row",
            decoded.sessions.get(0).windows.get(0).homeFloat);
        TerminalWorkspace.Window window = decoded.sessions.get(0).windows.get(1);
        assertEquals("the float's one pane is its root", "/home/float",
            ((TerminalWorkspace.Pane) window.root).cwd);
        assertEquals("notes", window.name);
        TerminalWorkspace.HomeFloat homeFloat = window.homeFloat;
        assertNotNull(homeFloat);
        assertEquals(1, homeFloat.pageId);
        assertTrue(homeFloat.minimised);
        assertShape(.1f, .2f, .5f, .4f, homeFloat.portrait);
        assertShape(.3f, .05f, .6f, .9f, homeFloat.landscape);
        assertEquals(4, decoded.paneCount());
    }

    @Test
    public void homeFloatWithoutShapes_roundTripsWithNone() throws Exception {
        TerminalWorkspace decoded = TerminalWorkspace.fromJson(withHomeFloat(
            new TerminalWorkspace.HomeFloat(0, false, null, null)).toJson());

        TerminalWorkspace.HomeFloat homeFloat = decoded.sessions.get(0).windows.get(1).homeFloat;
        assertNotNull(homeFloat);
        assertEquals(0, homeFloat.pageId);
        assertFalse(homeFloat.minimised);
        assertNull(homeFloat.portrait);
        assertNull(homeFloat.landscape);
    }

    @Test
    public void versionThreeAndAWorkspaceWithoutAHomeFloat_loadAsTiledWindows() throws Exception {
        JSONObject v3 = sampleWithFloat("dev").toJson().put("version", 3);
        TerminalWorkspace three = TerminalWorkspace.fromJson(v3);
        assertEquals(3, three.version);
        assertNull(three.sessions.get(0).windows.get(0).homeFloat);
        assertEquals("a floating pane inside a window is untouched", 1,
            three.sessions.get(0).windows.get(0).floats.size());

        JSONObject v4 = sample("dev").toJson();
        assertEquals(TerminalWorkspace.VERSION, v4.getInt("version"));
        assertFalse(v4.getJSONArray("sessions").getJSONObject(0).getJSONArray("windows")
            .getJSONObject(0).has("homeFloat"));
        assertNull(TerminalWorkspace.fromJson(v4).sessions.get(0).windows.get(0).homeFloat);
    }

    @Test
    public void malformedHomeFloat_isDroppedAndTheWindowLoadsTiled() throws Exception {
        JSONObject[] broken = {
            new JSONObject().put("minimised", false),
            new JSONObject().put("page", -1),
            new JSONObject().put("page", 1.5),
            new JSONObject().put("page", "2"),
            new JSONObject().put("page", 0).put("minimised", "yes"),
            new JSONObject().put("page", 0).put("portrait", "big"),
            new JSONObject().put("page", 0).put("portrait",
                new JSONObject().put("left", 0).put("top", 0).put("width", 2).put("height", .5)),
            new JSONObject().put("page", 0).put("landscape",
                new JSONObject().put("left", 0).put("top", 0).put("width", .5)),
        };
        for (JSONObject homeFloat : broken) {
            JSONObject json = withHomeFloat(new TerminalWorkspace.HomeFloat(0, false, null, null))
                .toJson();
            json.getJSONArray("sessions").getJSONObject(0).getJSONArray("windows")
                .getJSONObject(1).put("homeFloat", homeFloat);
            TerminalWorkspace decoded = TerminalWorkspace.fromJson(json);
            assertNull(homeFloat.toString(), decoded.sessions.get(0).windows.get(1).homeFloat);
            assertEquals(2, decoded.sessions.get(0).windows.size());
        }

        JSONObject notAnObject = sample("dev").toJson();
        notAnObject.getJSONArray("sessions").getJSONObject(0).getJSONArray("windows")
            .getJSONObject(0).put("homeFloat", 3);
        assertNull(TerminalWorkspace.fromJson(notAnObject).sessions.get(0).windows.get(0).homeFloat);
    }

    @Test
    public void aHomeFloatOnAWindowOfManyPanesIsDropped() throws Exception {
        // The sample's window is a split: a Home float holds one pane, so the key cannot stand.
        JSONObject json = sample("dev").toJson();
        json.getJSONArray("sessions").getJSONObject(0).getJSONArray("windows").getJSONObject(0)
            .put("homeFloat", new JSONObject().put("page", 0));
        assertNull(TerminalWorkspace.fromJson(json).sessions.get(0).windows.get(0).homeFloat);
    }

    @Test
    public void versionsPastFiveAreStillUnsupported() throws Exception {
        expectCode("unsupported_version",
            () -> TerminalWorkspace.fromJson(sample("dev").toJson().put("version", 6)));
    }

    @Test
    public void theAutosaveSlotKeepsAnArrangementAndIsNoSavedSession() throws Exception {
        TerminalWorkspaceStore store = new TerminalWorkspaceStore(temporary.newFolder("home"));
        assertEquals("nothing autosaved yet", ArrangementRecovery.Saved.NONE,
            store.loadAutosave().kind);

        store.saveAutosave(withHomeFloat(new TerminalWorkspace.HomeFloat(1, true, null, null)));
        TerminalWorkspaceStore.Autosave autosave = store.loadAutosave();

        assertEquals(ArrangementRecovery.Saved.ARRANGEMENT, autosave.kind);
        assertNotNull(autosave.arrangement);
        TerminalWorkspace.Window floated = autosave.arrangement.sessions.get(0).windows.get(1);
        assertNotNull("the float keeps its page", floated.homeFloat);
        assertEquals(1, floated.homeFloat.pageId);
        assertTrue(floated.homeFloat.minimised);
        assertTrue("never listed among Saved sessions", store.list().isEmpty());
        expectCode("invalid_name", () -> store.load(".autosave"));
    }

    @Test
    public void anEmptyAutosaveSaysNothingWasLeftOpenAndAnArrangementReplacesIt() throws Exception {
        TerminalWorkspaceStore store = new TerminalWorkspaceStore(temporary.newFolder("home"));
        store.saveAutosave(null);
        assertEquals(ArrangementRecovery.Saved.EMPTY, store.loadAutosave().kind);
        assertNull(store.loadAutosave().arrangement);

        store.saveAutosave(sample(TerminalWorkspaceStore.AUTOSAVE_NAME));
        assertEquals(ArrangementRecovery.Saved.ARRANGEMENT, store.loadAutosave().kind);
        store.saveAutosave(null);
        assertEquals(ArrangementRecovery.Saved.EMPTY, store.loadAutosave().kind);
    }

    @Test
    public void aCorruptAutosaveReadsAsNone() throws Exception {
        File home = temporary.newFolder("home");
        TerminalWorkspaceStore store = new TerminalWorkspaceStore(home);
        store.saveAutosave(null);
        File file = new File(new File(new File(home, ".termux"), "workspaces"),
            TerminalWorkspaceStore.AUTOSAVE_FILE);
        assertTrue(file.isFile());
        Files.write(file.toPath(), "{not json".getBytes(StandardCharsets.UTF_8));
        assertEquals(ArrangementRecovery.Saved.NONE, store.loadAutosave().kind);
    }

    private static TerminalWorkspace withHomeFloat(TerminalWorkspace.HomeFloat homeFloat) {
        TerminalWorkspace base = sample("dev");
        TerminalWorkspace.Window tiled = base.sessions.get(0).windows.get(0);
        TerminalWorkspace.Window floated = new TerminalWorkspace.Window(0,
            new TerminalWorkspace.Pane("/home/float", "float", null), null, "notes", homeFloat);
        TerminalWorkspace.Session session = new TerminalWorkspace.Session(
            "project", 0, Arrays.asList(tiled, floated));
        return new TerminalWorkspace("dev", 1234L, 0, Collections.singletonList(session));
    }

    private static void assertShape(float left, float top, float width, float height,
                                    TerminalWorkspace.FloatShape shape) {
        assertNotNull(shape);
        assertEquals(left, shape.left, .001f);
        assertEquals(top, shape.top, .001f);
        assertEquals(width, shape.width, .001f);
        assertEquals(height, shape.height, .001f);
    }

    private static TerminalWorkspace workspaceAround(TerminalWorkspace.Window window) {
        return new TerminalWorkspace("w", 1L, 0, Collections.singletonList(
            new TerminalWorkspace.Session(null, 0, Collections.singletonList(window))));
    }

    private static TerminalWorkspace sampleWithFloat(String name) {
        TerminalWorkspace base = sample(name);
        TerminalWorkspace.Window tiled = base.sessions.get(0).windows.get(0);
        TerminalWorkspace.FloatingPane floating = new TerminalWorkspace.FloatingPane(
            new TerminalWorkspace.Pane("/home/floating", "monitor",
                Collections.singletonList("/data/data/com.termux/files/usr/bin/htop")),
            .2f, .15f, .6f, .5f);
        TerminalWorkspace.Window window = new TerminalWorkspace.Window(3, tiled.root,
            Collections.singletonList(floating));
        TerminalWorkspace.Session session = new TerminalWorkspace.Session(
            "project", 0, Collections.singletonList(window));
        return new TerminalWorkspace(name, 1234L, 0, Collections.singletonList(session));
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
