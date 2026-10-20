package com.termux.app.terminal.inappkeyboard.touch;

import static org.junit.Assert.assertEquals;

import static com.termux.app.terminal.inappkeyboard.touch.TapModelTest.BACKSPACE;
import static com.termux.app.terminal.inappkeyboard.touch.TapModelTest.SPACE;

import com.termux.app.terminal.inappkeyboard.TapModelStore;

import juloo.keyboard2.TapGeometry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/** The pass bar's per-key counts: taps by key sent, take-backs by key meant and hit, per phase. */
public class PassBarCountsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final TapGeometry g = TapModelTest.spaceGrid();
    private static final int B = 4;

    private TouchEngine engine;
    private GridMemory grid;
    private long now;

    @Before
    public void setUp() {
        engine = new TouchEngine();
        engine.onSwitch(true);
        grid = new GridMemory(g.keyCount);
        now = 1_000_000L;
    }

    private void tap(int key, float x, float y) {
        now += 3_000L;
        engine.onRelease(grid, g, Tap.plain(key, x, y, now));
    }

    private void spaceTakeBack() {
        tap(B, 4.5f, 1.1f);
        now += 300L;
        engine.onRelease(grid, g, Tap.plain(BACKSPACE, 0.5f, 1.8f, now));
        now += 300L;
        engine.onRelease(grid, g, Tap.plain(SPACE, 4.5f, 1.8f, now));
    }

    @Test
    public void warmUpCountsTapsBySentKeyAndTakeBacksByMeantAndHitKey() {
        for (int i = 0; i < 5; i++)
            tap(SPACE, 4.5f, 1.8f);
        spaceTakeBack();
        PassBarCounts counts = grid.counts();
        assertEquals(6, counts.warmTaps()[SPACE]);
        assertEquals(1, counts.warmTaps()[B]);
        assertEquals(1, counts.warmTakeBacks()[SPACE]);
        assertEquals(0, counts.warmTakeBacks()[B]);
        assertEquals(1, counts.warmTakeBacksHit()[B]);
        assertEquals(0, counts.warmTakeBacksHit()[SPACE]);
        assertEquals(0, counts.activeTaps()[SPACE]);
        assertEquals(0, counts.activeTakeBacksHit()[B]);
    }

    @Test
    public void activeCorrectionCountsApart() {
        while (engine.gate().state() == LearningGate.STATE_LEARNING)
            tap(0, 0.5f, 0.6f);
        spaceTakeBack();
        PassBarCounts counts = grid.counts();
        assertEquals(1, counts.activeTakeBacks()[SPACE]);
        assertEquals(1, counts.activeTakeBacksHit()[B]);
        assertEquals(1, counts.activeTaps()[SPACE]);
        assertEquals(0, counts.warmTakeBacks()[SPACE]);
        assertEquals(0, counts.warmTakeBacksHit()[B]);
        assertEquals(LearningGate.WARM_UP_TAPS, counts.warmTaps()[0]);
    }

    @Test
    public void pausedTapsCountInNeitherPhase() {
        engine.gate().pause();
        spaceTakeBack();
        PassBarCounts counts = grid.counts();
        assertEquals(0, counts.warmTaps()[SPACE]);
        assertEquals(0, counts.activeTaps()[SPACE]);
        assertEquals(0, counts.warmTakeBacks()[SPACE]);
        assertEquals(0, counts.activeTakeBacks()[SPACE]);
    }

    @Test
    public void aNewWarmUpStartsTheCountsAgain() {
        spaceTakeBack();
        engine.gate().pause();
        engine.resume();
        tap(SPACE, 4.5f, 1.8f);
        PassBarCounts counts = grid.counts();
        assertEquals(1, counts.warmTaps()[SPACE]);
        assertEquals(0, counts.warmTakeBacks()[SPACE]);
        assertEquals(engine.epoch(), counts.epoch());
    }

    @Test
    public void theCountsSurviveTheFileAndAStaleGridIsWrittenEmpty() throws Exception {
        File file = new File(folder.getRoot(), "model.json");
        TapModelStore store = new TapModelStore();
        store.engine().onSwitch(true);
        GridMemory live = store.gridFor("qwerty|space", g.keyCount, 1L);
        GridMemory stale = store.gridFor("other|space", g.keyCount, 2L);
        TouchEngine e = store.engine();
        long t = 1_000_000L;
        for (int i = 0; i < 3; i++) {
            t += 3_000L;
            e.onRelease(stale, g, Tap.plain(SPACE, 4.5f, 1.8f, t));
        }
        e.resume();
        for (int i = 0; i < 4; i++) {
            t += 3_000L;
            e.onRelease(live, g, Tap.plain(SPACE, 4.5f, 1.8f, t));
        }
        // A miss into b, taken back as a space.
        t += 3_000L;
        e.onRelease(live, g, Tap.plain(B, 4.5f, 1.1f, t));
        t += 300L;
        e.onRelease(live, g, Tap.plain(BACKSPACE, 0.5f, 1.8f, t));
        t += 300L;
        e.onRelease(live, g, Tap.plain(SPACE, 4.5f, 1.8f, t));
        TapModelStore.write(file, store.toJson());

        TapModelStore loaded = TapModelStore.load(file);
        assertEquals(e.epoch(), loaded.engine().epoch());
        GridMemory back = loaded.gridFor("qwerty|space", g.keyCount, 3L);
        assertEquals(5, back.counts().warmTaps()[SPACE]);
        assertEquals(1, back.counts().warmTakeBacks()[SPACE]);
        assertEquals(1, back.counts().warmTakeBacksHit()[B]);
        GridMemory staleBack = loaded.gridFor("other|space", g.keyCount, 4L);
        assertEquals(0, staleBack.counts().warmTaps()[SPACE]);
    }
}
