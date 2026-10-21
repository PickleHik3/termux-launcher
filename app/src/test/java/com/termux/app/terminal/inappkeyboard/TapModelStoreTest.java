package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.inappkeyboard.touch.GridMemory;
import com.termux.app.terminal.inappkeyboard.touch.LearningGate;
import com.termux.app.terminal.inappkeyboard.touch.Tap;
import com.termux.app.terminal.inappkeyboard.touch.TapModel;

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

    static TapGeometry twoKeys() {
        return new TapGeometry(new float[]{0, 1}, new float[]{0, 0}, new float[]{1, 2},
            new float[]{1, 1}, new int[]{0, 0},
            new int[]{TapGeometry.ROLE_CHAR, TapGeometry.ROLE_CHAR}, "two");
    }

    @Test
    public void missingFileLoadsEmpty() {
        TapModelStore store = TapModelStore.load(new File(folder.getRoot(), "absent.json"));
        assertEquals(0, store.entryCount());
        assertEquals(0f, store.totalTaps(), 0f);
        assertEquals(LearningGate.STATE_LEARNING, store.state());
        assertFalse(store.isDirty());
    }

    @Test
    public void roundTripsWhatWasLearnedAndTheEngineState() throws Exception {
        File file = new File(folder.getRoot(), "model.json");
        TapModelStore store = new TapModelStore();
        TapGeometry g = twoKeys();
        store.engine().onSwitch(true);
        GridMemory grid = store.gridFor("qwerty|two", 2, 10L);
        for (int i = 0; i < 25; i++)
            store.engine().onRelease(grid, g, Tap.plain(0, 0.7f, 0.4f, 1_000_000L + i * 3_000L));
        store.engine().interrupt();
        store.markDirty();
        TapModelStore.write(file, store.toJson());
        assertFalse(store.isDirty());

        TapModelStore loaded = TapModelStore.load(file);
        assertEquals(1, loaded.entryCount());
        assertEquals(25f, loaded.totalTaps(), 0f);
        assertEquals(25L, loaded.tapsLearned());
        assertTrue(loaded.engine().switchedOn());
        assertEquals(25, loaded.engine().gate().warmUpTaps());
        GridMemory back = loaded.gridFor("qwerty|two", 2, 11L);
        TapModel model = grid.model();
        assertEquals(model.biasX(g, 0), back.model().biasX(g, 0), 1e-5f);
        assertEquals(model.biasY(g, 0), back.model().biasY(g, 0), 1e-5f);
        assertEquals(grid.log().sampleCount(0), back.log().sampleCount(0));
        assertEquals(grid.log().samples()[0], back.log().samples()[0]);
    }

    @Test
    public void aSnapshotKeepsTheStateItWasTakenAt() throws Exception {
        File file = new File(folder.getRoot(), "model.json");
        TapModelStore store = new TapModelStore();
        TapGeometry g = twoKeys();
        GridMemory grid = store.gridFor("k", 2, 1L);
        for (int i = 0; i < 5; i++)
            grid.model().observe(g, 0, 0.6f, 0.5f, 1f);
        store.markDirty();
        TapModelStore.Snapshot snapshot = store.snapshot();
        assertFalse(store.isDirty());
        // Typing goes on while the snapshot waits for the I/O thread.
        for (int i = 0; i < 20; i++)
            grid.model().observe(g, 0, 0.9f, 0.5f, 1f);
        store.engine().gate().pause();
        TapModelStore.write(file, snapshot.toJson());

        TapModelStore loaded = TapModelStore.load(file);
        assertEquals(5f, loaded.totalTaps(), 0f);
        assertEquals(LearningGate.STATE_LEARNING, loaded.state());
    }

    @Test
    public void aPauseSurvivesTheFile() throws Exception {
        File file = new File(folder.getRoot(), "model.json");
        TapModelStore store = new TapModelStore();
        store.engine().gate().pause();
        TapModelStore.write(file, store.toJson());
        assertEquals(LearningGate.STATE_PAUSED, TapModelStore.load(file).state());
    }

    @Test
    public void fileHoldsOnlyAggregatesNeverCharacters() throws Exception {
        TapModelStore store = new TapModelStore();
        TapGeometry g = twoKeys();
        store.gridFor("layout|sig", 2, 1L).model().observe(g, 1, 1.5f, 0.5f, 1f);
        String json = store.toJson();
        assertTrue(json.contains("\"n\""));
        assertTrue(json.contains("\"x\""));
        assertTrue(json.contains("\"y\""));
        assertFalse(json.contains("\"keys\""));
        assertFalse(json.contains("\"chars\""));
    }

    @Test
    public void sameKeyReturnsTheLiveGridAndAMismatchedSizeStartsFresh() {
        TapModelStore store = new TapModelStore();
        GridMemory a = store.gridFor("k", 2, 1L);
        assertSame(a, store.gridFor("k", 2, 2L));
        GridMemory fresh = store.gridFor("k", 3, 3L);
        assertNotSame(a, fresh);
        assertEquals(3, fresh.keyCount());
    }

    @Test
    public void evictsTheLeastRecentlyUsedGeometry() {
        TapModelStore store = new TapModelStore();
        TapGeometry g = twoKeys();
        for (int i = 0; i <= TapModelStore.MAX_ENTRIES; i++)
            store.gridFor("g" + i, 2, 100L + i).model().observe(g, 0, 0.5f, 0.5f, 1f);
        assertEquals(TapModelStore.MAX_ENTRIES, store.entryCount());
        assertTrue(store.gridFor("g0", 2, 500L).isEmpty());
    }

    @Test
    public void corruptFileLoadsEmptyInsteadOfThrowing() throws Exception {
        File file = new File(folder.getRoot(), "model.json");
        Files.write(file.toPath(), "{not json".getBytes(StandardCharsets.UTF_8));
        TapModelStore store = TapModelStore.load(file);
        assertEquals(0, store.entryCount());
    }

    @Test
    public void aVersionOneFileIsDropped() throws Exception {
        File file = new File(folder.getRoot(), "model.json");
        Files.write(file.toPath(),
            "{\"v\":1,\"entries\":{\"a\":{\"t\":1,\"n\":[30],\"x\":[6],\"y\":[0]}}}"
                .getBytes(StandardCharsets.UTF_8));
        TapModelStore store = TapModelStore.load(file);
        assertEquals(0, store.entryCount());
        assertEquals(0L, store.tapsLearned());
    }

    @Test
    public void aVersionTwoFileIsDroppedWithItsWarmUp() throws Exception {
        File file = new File(folder.getRoot(), "model.json");
        Files.write(file.toPath(),
            ("{\"v\":2,\"engine\":{\"on\":true,\"learned\":7900,\"epoch\":3,\"state\":1},"
                + "\"entries\":{\"qwerty|60:106:150:ab\":{\"t\":1,\"n\":[30],\"x\":[6],"
                + "\"y\":[0]}}}").getBytes(StandardCharsets.UTF_8));
        TapModelStore store = TapModelStore.load(file);
        assertEquals(0, store.entryCount());
        assertEquals(0L, store.tapsLearned());
        assertFalse(store.engine().switchedOn());
        assertEquals(LearningGate.STATE_LEARNING, store.state());
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
    public void clearForgetsTheEngineStateToo() {
        TapModelStore store = new TapModelStore();
        store.engine().gate().pause();
        store.clear();
        assertEquals(LearningGate.STATE_LEARNING, store.state());
        assertEquals(0, store.entryCount());
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
