package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** The tiling desktop's saved form on disk (#418), read and written through a temporary file. */
public class TilingDesktopStoreTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private static TilingStack threeColumns() {
        TilingStack stack = TilingStack.firstStart(1, "/home/a");
        stack.focusWindow(1);
        stack.open(WindowSpec.shell("/home/b"));
        stack.open(WindowSpec.shell("/home/c"));
        stack.focusLeft();
        return stack;
    }

    @Test
    public void whatIsSavedComesBackWithColumnsFocusAndShells() throws Exception {
        TilingDesktopStore store = new TilingDesktopStore(new File(folder.getRoot(), "w/.tiling.json"));
        TilingStack stack = threeColumns();
        stack.setLayoutState(1, "{\"scroll\":12}");
        Map<Long, String> shells = new HashMap<>();
        shells.put(1L, "handle-1");
        shells.put(3L, "handle-3");

        store.save(TilingDesktopStore.encode(stack, shells));
        TilingDesktopStore.Saved saved = store.load();

        assertNotNull(saved);
        assertEquals(Place.workspace(1), saved.stack.current());
        assertEquals(stack.focusedWindowId(), saved.stack.focusedWindowId());
        assertEquals(3, saved.stack.workspace(1).groups().size());
        assertEquals("/home/b", saved.stack.window(2).workingDirectory());
        assertEquals("{\"scroll\":12}", saved.stack.workspace(1).layoutState());
        assertEquals(shells, saved.shells);
    }

    @Test
    public void nothingSavedIsNothingToLoad() {
        TilingDesktopStore store = new TilingDesktopStore(new File(folder.getRoot(), "none.json"));
        assertNull(store.load());
    }

    @Test
    public void anUnreadableFileIsNothingToLoad() throws Exception {
        File file = folder.newFile("bad.json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("{\"version\":1,\"stack\":".getBytes(StandardCharsets.UTF_8));
        }
        assertNull(new TilingDesktopStore(file).load());
        assertNull(TilingDesktopStore.decode("[]"));
        assertNull(TilingDesktopStore.decode("{\"version\":1}"));
        assertNull(TilingDesktopStore.decode(null));
    }

    @Test
    public void aShellNamedForAWindowThatIsGoneIsDropped() {
        TilingStack stack = TilingStack.firstStart(1, null);
        Map<Long, String> shells = new HashMap<>();
        shells.put(1L, "kept");
        shells.put(99L, "orphan");
        TilingDesktopStore.Saved saved = TilingDesktopStore.decode(TilingDesktopStore.encode(stack, shells));

        assertNotNull(saved);
        assertEquals(1, saved.shells.size());
        assertEquals("kept", saved.shells.get(1L));
    }

    @Test
    public void aSaveReplacesTheFileWhole() throws Exception {
        File file = new File(folder.getRoot(), ".tiling.json");
        TilingDesktopStore store = new TilingDesktopStore(file);
        store.save(TilingDesktopStore.encode(threeColumns(), new HashMap<>()));
        store.save(TilingDesktopStore.encode(TilingStack.firstStart(1, null), new HashMap<>()));

        TilingDesktopStore.Saved saved = store.load();
        assertNotNull(saved);
        assertEquals(1, saved.stack.workspace(1).windows().size());
        String[] left = file.getParentFile().list();
        assertNotNull(left);
        assertEquals("no temporary file is left behind", 1, left.length);
        assertTrue(file.isFile());
    }

    @Test
    public void theFileLivesBesideTheArrangementAutosave() {
        File home = folder.getRoot();
        assertEquals(new File(new File(new File(home, ".termux"), "workspaces"), ".tiling-desktop.json"),
            TilingDesktopStore.inHome(home).file());
    }
}
