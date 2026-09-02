package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import juloo.keyboard2.TapGeometry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class TapModelStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static TapGeometry twoKeys() {
        return new TapGeometry(new float[]{0, 1}, new float[]{0, 0}, new float[]{1, 2},
            new float[]{1, 1}, new int[]{0, 0}, new boolean[]{true, true}, "two");
    }

    @Test
    public void missingFileLoadsEmpty() {
        TapModelStore store = TapModelStore.load(new File(folder.getRoot(), "absent.json"));
        assertEquals(0, store.entryCount());
        assertEquals(0f, store.totalTaps(), 0f);
        assertFalse(store.isDirty());
    }

    @Test
    public void roundTripsLearnedStatistics() throws Exception {
        File file = new File(folder.getRoot(), "model.json");
        TapModelStore store = new TapModelStore();
        TapGeometry g = twoKeys();
        TapModel model = store.modelFor("qwerty|two", 2, 10L);
        for (int i = 0; i < 25; i++)
            model.observe(g, 0, 0.7f, 0.4f, false);
        store.markDirty();
        TapModelStore.write(file, store.toJson());
        assertFalse(store.isDirty());

        TapModelStore loaded = TapModelStore.load(file);
        assertEquals(1, loaded.entryCount());
        assertEquals(25f, loaded.totalTaps(), 0f);
        TapModel back = loaded.modelFor("qwerty|two", 2, 11L);
        assertEquals(model.biasX(0), back.biasX(0), 1e-5f);
        assertEquals(model.biasY(0), back.biasY(0), 1e-5f);
    }

    @Test
    public void fileHoldsOnlyAggregatesNeverCharacters() throws Exception {
        TapModelStore store = new TapModelStore();
        TapGeometry g = twoKeys();
        store.modelFor("layout|sig", 2, 1L).observe(g, 1, 1.5f, 0.5f, false);
        String json = store.toJson();
        assertTrue(json.contains("\"n\""));
        assertTrue(json.contains("\"x\""));
        assertTrue(json.contains("\"y\""));
        assertFalse(json.contains("\"keys\""));
        assertFalse(json.contains("\"chars\""));
    }

    @Test
    public void sameKeyReturnsTheLiveModelAndAMismatchedSizeStartsFresh() {
        TapModelStore store = new TapModelStore();
        TapModel a = store.modelFor("k", 2, 1L);
        assertSame(a, store.modelFor("k", 2, 2L));
        TapModel fresh = store.modelFor("k", 3, 3L);
        assertNotSame(a, fresh);
        assertEquals(3, fresh.keyCount());
    }

    @Test
    public void evictsTheLeastRecentlyUsedGeometry() {
        TapModelStore store = new TapModelStore();
        TapGeometry g = twoKeys();
        for (int i = 0; i <= TapModelStore.MAX_ENTRIES; i++)
            store.modelFor("g" + i, 2, 100L + i).observe(g, 0, 0.5f, 0.5f, false);
        assertEquals(TapModelStore.MAX_ENTRIES, store.entryCount());
        // g0 was used first, so it is the one that went.
        TapModel g0 = store.modelFor("g0", 2, 500L);
        assertTrue(g0.isEmpty());
    }

    @Test
    public void corruptFileLoadsEmptyInsteadOfThrowing() throws Exception {
        File file = new File(folder.getRoot(), "model.json");
        Files.write(file.toPath(), "{not json".getBytes(StandardCharsets.UTF_8));
        TapModelStore store = TapModelStore.load(file);
        assertEquals(0, store.entryCount());
    }

    @Test
    public void unknownVersionLoadsEmpty() throws Exception {
        File file = new File(folder.getRoot(), "model.json");
        Files.write(file.toPath(),
            "{\"v\":99,\"entries\":{\"a\":{\"n\":[1],\"x\":[0],\"y\":[0]}}}"
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(0, TapModelStore.load(file).entryCount());
    }

    @Test
    public void deleteRemovesTheFile() throws Exception {
        File file = new File(folder.getRoot(), "model.json");
        TapModelStore.write(file, new TapModelStore().toJson());
        assertTrue(file.isFile());
        assertTrue(TapModelStore.delete(file));
        assertFalse(file.exists());
        assertTrue(TapModelStore.delete(file));
    }
}
