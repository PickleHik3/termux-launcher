package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The saved Workspaces' files (#431): list, save, rename, delete and Open at start, in a temp dir. */
public class SavedWorkspaceStoreTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void savedWorkspacesListByNameWithTheirDocuments() throws Exception {
        SavedWorkspaceStore store = new SavedWorkspaceStore(folder.newFolder("terl"));

        assertEquals(SavedWorkspaceStore.Result.OK, store.save("work", workspace("/w"), false));
        assertEquals(SavedWorkspaceStore.Result.OK, store.save("Alpha", workspace("/a"), false));

        List<SavedWorkspaceStore.Entry> entries = store.list();
        assertEquals(2, entries.size());
        assertEquals("Alpha", entries.get(0).name);
        assertEquals("work", entries.get(1).name);
        assertNotNull(entries.get(1).workspace);
        assertEquals(1, store.load("work").windowCount());
    }

    @Test
    public void aMissingDirectoryListsNothing() {
        SavedWorkspaceStore store = new SavedWorkspaceStore(new File(folder.getRoot(), "none"));
        assertTrue(store.list().isEmpty());
        assertNull(store.load("work"));
    }

    @Test
    public void inHomeIsItsOwnFolderBesideV1sSavedSessions() {
        File home = folder.getRoot();
        assertEquals(new File(home, ".termux/workspaces/terl"), SavedWorkspaceStore.inHome(home).directory());
    }

    @Test
    public void aTakenNameIsRefusedUnlessReplacing() {
        SavedWorkspaceStore store = new SavedWorkspaceStore(new File(folder.getRoot(), "terl"));
        store.save("work", workspace("/one"), false);

        assertEquals(SavedWorkspaceStore.Result.NAME_TAKEN, store.save("work", workspace("/two"), false));
        assertEquals("/one", firstDirectory(store.load("work")));
        assertEquals(SavedWorkspaceStore.Result.OK, store.save("work", workspace("/two"), true));
        assertEquals("/two", firstDirectory(store.load("work")));
    }

    @Test
    public void namesAreTrimmedAndCheckedAsV1sAre() {
        assertEquals("my work", SavedWorkspaceStore.cleanName("  my work "));
        assertEquals("v1.2_x-y", SavedWorkspaceStore.cleanName("v1.2_x-y"));
        assertNull(SavedWorkspaceStore.cleanName(""));
        assertNull(SavedWorkspaceStore.cleanName("   "));
        assertNull(SavedWorkspaceStore.cleanName("../up"));
        assertNull(SavedWorkspaceStore.cleanName("a/b"));
        assertNull(SavedWorkspaceStore.cleanName(".hidden"));
        assertNull(SavedWorkspaceStore.cleanName(String.join("", Collections.nCopies(65, "a"))));
        SavedWorkspaceStore store = new SavedWorkspaceStore(new File(folder.getRoot(), "terl"));
        assertEquals(SavedWorkspaceStore.Result.INVALID_NAME, store.save("a/b", workspace("/x"), false));
    }

    @Test
    public void renameMovesTheFileAndRefusesATakenName() {
        SavedWorkspaceStore store = new SavedWorkspaceStore(new File(folder.getRoot(), "terl"));
        store.save("one", workspace("/1"), false);
        store.save("two", workspace("/2"), false);

        assertEquals(SavedWorkspaceStore.Result.NAME_TAKEN, store.rename("one", "two"));
        assertEquals(SavedWorkspaceStore.Result.NOT_FOUND, store.rename("three", "four"));
        assertEquals(SavedWorkspaceStore.Result.INVALID_NAME, store.rename("one", ""));
        assertEquals(SavedWorkspaceStore.Result.OK, store.rename("one", " uno "));

        assertNull(store.load("one"));
        assertEquals("/1", firstDirectory(store.load("uno")));
        assertEquals(SavedWorkspaceStore.Result.OK, store.rename("uno", "uno"));
    }

    @Test
    public void deleteRemovesOnlyItsOwnFile() {
        SavedWorkspaceStore store = new SavedWorkspaceStore(new File(folder.getRoot(), "terl"));
        store.save("one", workspace("/1"), false);
        store.save("two", workspace("/2"), false);

        assertEquals(SavedWorkspaceStore.Result.OK, store.delete("one"));
        assertEquals(SavedWorkspaceStore.Result.NOT_FOUND, store.delete("one"));

        assertEquals(1, store.list().size());
        assertEquals("two", store.list().get(0).name);
    }

    @Test
    public void openAtStartIsSetOnTheFileAndListed() {
        SavedWorkspaceStore store = new SavedWorkspaceStore(new File(folder.getRoot(), "terl"));
        store.save("one", workspace("/1"), false);
        store.save("two", workspace("/2"), false);

        assertEquals(SavedWorkspaceStore.Result.OK, store.setOpenAtStart("two", true));
        assertEquals(SavedWorkspaceStore.Result.NOT_FOUND, store.setOpenAtStart("three", true));

        List<String> flagged = new ArrayList<>();
        for (SavedWorkspaceStore.Entry entry : store.list()) {
            if (entry.workspace != null && entry.workspace.openAtStart()) flagged.add(entry.name);
        }
        assertEquals(Collections.singletonList("two"), flagged);
        assertEquals("/2", firstDirectory(store.load("two")));
        store.setOpenAtStart("two", false);
        assertFalse(store.load("two").openAtStart());
    }

    @Test
    public void unreadableAndForeignFilesAreLeftAlone() throws Exception {
        File directory = folder.newFolder("terl");
        Files.write(new File(directory, "broken.json").toPath(), "{".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(directory, "notes.txt").toPath(), "hi".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(directory, ".hidden.json").toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        SavedWorkspaceStore store = new SavedWorkspaceStore(directory);

        List<SavedWorkspaceStore.Entry> entries = store.list();

        assertEquals(1, entries.size());
        assertEquals("broken", entries.get(0).name);
        assertNull("listed, so it can be deleted, but it has nothing to load", entries.get(0).workspace);
        assertEquals(SavedWorkspaceStore.Result.OK, store.delete("broken"));
        assertTrue(new File(directory, "notes.txt").isFile());
    }

    private static SavedWorkspace workspace(String directory) {
        TilingStack stack = TilingStack.empty(0);
        stack.openIn(Place.workspace(1), WindowSpec.shell(directory), false);
        return SavedWorkspace.of(stack, 1, Collections.emptyMap());
    }

    private static String firstDirectory(SavedWorkspace saved) {
        return saved.preview().windows().get(0).workingDirectory();
    }
}
