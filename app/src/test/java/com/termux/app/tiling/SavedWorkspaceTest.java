package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A saved Workspace (#431): taken from a stack, written, read back and loaded into another stack. */
public class SavedWorkspaceTest {

    private static final ScrollingLayoutPolicy.Viewport VIEWPORT =
        new ScrollingLayoutPolicy.Viewport(1000f, 2000f, 8f, false);

    @Test
    public void aWorkspaceRoundTripsThroughItsTextIntoAnotherStack() {
        TilingStack source = TilingStack.empty(1);
        long shell = source.openIn(Place.workspace(1), WindowSpec.shell("/proj"), false);
        long app = source.openIn(Place.workspace(1), WindowSpec.linuxApp("org.gimp", "GIMP"), false);
        long floating = source.openIn(Place.workspace(1), WindowSpec.shell("/tmp"), true);
        Map<Long, String> keys = new HashMap<>();
        keys.put(app, "gimp");
        keys.put(shell, "not a Linux window");

        SavedWorkspace saved = SavedWorkspace.decode(SavedWorkspace.of(source, 1, keys).encode());
        assertNotNull(saved);
        assertFalse(saved.openAtStart());
        assertEquals(3, saved.windowCount());

        TilingStack target = TilingStack.firstStart(1, "/home");
        SavedWorkspace.Loaded loaded = saved.loadInto(target, "dev");

        assertNotNull(loaded);
        assertEquals(2, loaded.number);
        TilingWorkspace workspace = target.workspace(2);
        assertEquals("dev", workspace.name());
        assertEquals(3, workspace.windows().size());
        TilingWindow newShell = target.window(loaded.ids.get(shell));
        assertEquals(WindowKind.SHELL, newShell.kind());
        assertEquals("/proj", newShell.workingDirectory());
        assertTrue(target.window(loaded.ids.get(floating)).isFloat());
        assertEquals(1, loaded.linux.size());
        SavedWorkspace.Relaunch relaunch = loaded.linux.get(0);
        assertEquals((long) loaded.ids.get(app), relaunch.windowId);
        assertEquals("org.gimp", relaunch.appId);
        assertEquals("gimp", relaunch.key);
        assertEquals("the front stays", Place.home(0), target.current());
    }

    @Test
    public void anEmptyOrMissingWorkspaceIsNothingToSave() {
        TilingStack stack = TilingStack.empty(1);
        assertNull(SavedWorkspace.of(stack, 1, Collections.emptyMap()));
        assertNull(SavedWorkspace.of(stack, 4, Collections.emptyMap()));
    }

    @Test
    public void openAtStartIsKeptWithTheDocument() {
        SavedWorkspace saved = SavedWorkspace.of(oneShell(), 1, Collections.emptyMap()).withOpenAtStart(true);

        assertTrue(SavedWorkspace.decode(saved.encode()).openAtStart());
        assertFalse(saved.withOpenAtStart(false).openAtStart());
    }

    @Test
    public void eachSaveHasItsOwnIdAndALoadMarksItsWorkspaceWithIt() {
        TilingStack source = oneShell();
        SavedWorkspace first = SavedWorkspace.of(source, 1, Collections.emptyMap());
        SavedWorkspace second = SavedWorkspace.of(source, 1, Collections.emptyMap());
        assertNotNull(first.id());
        assertFalse(first.id().equals(second.id()));
        assertEquals(first.id(), SavedWorkspace.decode(first.encode()).id());
        assertEquals("Open at start keeps the id", first.id(), first.withOpenAtStart(true).id());

        TilingStack target = TilingStack.firstStart(1, "/home");
        SavedWorkspace.Loaded loaded = first.loadInto(target, "dev");

        assertEquals(first.id(), target.workspace(loaded.number).savedId());
        SavedWorkspace again = SavedWorkspace.of(target, loaded.number, Collections.emptyMap());
        assertFalse("a save of a loaded Workspace is a new one", first.id().equals(again.id()));
        assertFalse("the Workspace's own mark is not saved", again.encode().contains("savedId"));
    }

    @Test
    public void unknownFieldsSurviveAndANewerVersionIsNotRead() throws Exception {
        org.json.JSONObject json = new org.json.JSONObject(
            SavedWorkspace.of(oneShell(), 1, Collections.emptyMap()).encode());
        json.put("later", "kept");
        SavedWorkspace read = SavedWorkspace.decode(json.toString());

        assertEquals("kept", new org.json.JSONObject(read.withOpenAtStart(true).encode()).getString("later"));

        json.put("version", 2);
        assertNull("a newer form is not read, so it is never written back older",
            SavedWorkspace.decode(json.toString()));
    }

    @Test
    public void unreadableTextIsNull() {
        assertNull(SavedWorkspace.decode(null));
        assertNull(SavedWorkspace.decode(""));
        assertNull(SavedWorkspace.decode("not json"));
        assertNull(SavedWorkspace.decode("{\"version\":1}"));
        assertNull("a Workspace with no window it can read",
            SavedWorkspace.decode("{\"workspace\":{\"groups\":[{\"windows\":[{\"id\":1,\"kind\":\"what\"}]}]}}"));
    }

    @Test
    public void scrollingWidthsFollowTheirWindowsToTheirNewIds() {
        TilingStack source = TilingStack.empty(0);
        long a = source.openIn(Place.workspace(1), WindowSpec.shell("/a"), false);
        long b = source.openIn(Place.workspace(1), WindowSpec.shell("/b"), false);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(source.workspace(1), null, VIEWPORT);
        ScrollingLayoutPolicy.State halves = new ScrollingLayoutPolicy.State(Arrays.asList(
            new ScrollingLayoutPolicy.Column(new long[] {a}, 0.5f, true),
            new ScrollingLayoutPolicy.Column(new long[] {b}, 0.5f, true)), state.focusedWindow, 0f);
        source.setLayoutState(1, ScrollingColumns.encode(halves));

        TilingStack target = TilingStack.firstStart(0, "/home");
        SavedWorkspace.Loaded loaded = SavedWorkspace.of(source, 1, Collections.emptyMap()).loadInto(target, null);

        ScrollingLayoutPolicy.State restored = ScrollingColumns.decode(target.workspace(loaded.number).layoutState());
        assertNotNull(restored);
        assertEquals(2, restored.columnCount());
        assertEquals((long) loaded.ids.get(a), restored.column(0).windowAt(0));
        assertEquals((long) loaded.ids.get(b), restored.column(1).windowAt(0));
        assertEquals(0.5f, restored.column(1).widthFraction, 0.001f);
    }

    @Test
    public void aDwindleTreeFollowsItsWindowsToTheirNewIds() {
        TilingStack source = TilingStack.empty(0);
        long a = source.openIn(Place.workspace(1), WindowSpec.shell("/a"), false);
        long b = source.openIn(Place.workspace(1), WindowSpec.shell("/b"), false);
        long c = source.openIn(Place.workspace(1), WindowSpec.shell("/c"), false);
        source.setLayout(1, WorkspaceLayout.DWINDLE);
        assertTrue(source.joinTab(c, b));
        DwindleTree tree = DwindleTree.empty();
        tree.sync(DwindleTree.tilesOf(source.workspace(1)), 1000f, 2000f, 8f);
        source.setLayoutState(1, tree.encode());
        float[] before = tree.roomOf(b, 1000f, 2000f, 8f);

        TilingStack target = TilingStack.firstStart(0, "/home");
        SavedWorkspace.Loaded loaded = SavedWorkspace.of(source, 1, Collections.emptyMap()).loadInto(target, null);

        TilingWorkspace workspace = target.workspace(loaded.number);
        DwindleTree restored = DwindleTree.decode(workspace.layoutState());
        assertNotNull(restored);
        restored.sync(DwindleTree.tilesOf(workspace), 1000f, 2000f, 8f);
        long newB = loaded.ids.get(b);
        assertTrue(Arrays.equals(before, restored.roomOf(newB, 1000f, 2000f, 8f)));
        DwindleTree.Tile tile = restored.tileOf(newB);
        assertTrue(Arrays.equals(new long[] {newB, loaded.ids.get(c)}, tile.windows));
        assertNotNull(restored.tileOf(loaded.ids.get(a)));
    }

    @Test
    public void aDisplayTheStackHasAlreadyAndAnyAssistantAreLeftOut() {
        TilingStack source = TilingStack.empty(0);
        source.openIn(Place.workspace(1), WindowSpec.shell("/a"), false);
        long display = source.openIn(Place.workspace(1), WindowSpec.display(), false);
        long assistant = source.openIn(Place.workspace(1), WindowSpec.assistant(), false);
        SavedWorkspace saved = SavedWorkspace.of(source, 1, Collections.emptyMap());

        TilingStack withDisplay = TilingStack.firstStart(0, "/home");
        withDisplay.openIn(Place.workspace(1), WindowSpec.display(), false);
        SavedWorkspace.Loaded loaded = saved.loadInto(withDisplay, null);

        List<TilingWindow> windows = withDisplay.workspace(loaded.number).windows();
        assertEquals(1, windows.size());
        assertFalse(loaded.ids.containsKey(display));
        // The Assistant is the AI page now (ADR 0043), never a window of a Workspace.
        assertFalse(loaded.ids.containsKey(assistant));
        assertNull(withDisplay.firstOfKind(WindowKind.ASSISTANT));
    }

    @Test
    public void aWorkspaceOfOnlyWindowsTheStackHasLoadsNothing() {
        TilingStack source = TilingStack.empty(0);
        source.openIn(Place.workspace(1), WindowSpec.display(), false);
        SavedWorkspace saved = SavedWorkspace.of(source, 1, Collections.emptyMap());
        TilingStack target = TilingStack.empty(0);
        target.openIn(Place.workspace(1), WindowSpec.display(), false);

        assertNull(saved.loadInto(target, "x"));
        assertEquals(2, target.workspaceCount());
    }

    @Test
    public void itsPreviewIsTheWorkspaceAlone() {
        SavedWorkspace saved = SavedWorkspace.of(oneShell(), 1, Collections.emptyMap());

        TilingWorkspace preview = saved.preview();

        assertNotNull(preview);
        assertEquals(1, preview.windows().size());
    }

    private static TilingStack oneShell() {
        TilingStack stack = TilingStack.empty(0);
        stack.openIn(Place.workspace(1), WindowSpec.shell("/x"), false);
        return stack;
    }
}
