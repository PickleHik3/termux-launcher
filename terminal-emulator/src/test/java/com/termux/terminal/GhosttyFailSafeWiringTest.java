package com.termux.terminal;

import android.content.ComponentCallbacks2;
import android.graphics.Bitmap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The hooks between the fail-safes (slice 6) and the links and images of slices 4 and 5: memory
 * trims reach a pane's image bitmaps, and a replay after a Ghostty error leaves nothing of the old
 * core behind. The library-backed cases run only with {@code -Pghostty=true}.
 */
public class GhosttyFailSafeWiringTest extends TerminalTestCase {

    private static final int COLUMNS = 20, ROWS = 6;
    private static final String LINK = "\033]8;;https://example.com/a\033\\link\033]8;;\033\\";

    private GhosttyKittyGraphicsTest.FakeBitmaps mBitmaps;
    private KittyImageStore.FrameBudget mBudget;
    private GhosttyImageCache<Bitmap> mCache;

    @Override
    protected void tearDown() throws Exception {
        GhosttyMemory.clearForTests();
        super.tearDown();
    }

    private static boolean hostLibrary() {
        return System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) != null;
    }

    /** A Ghostty pane whose own GhosttyImages draws from a cache of stand-in bitmaps. */
    private TerminalEmulator ghosttyPane() {
        TerminalEmulator terminal = new TerminalEmulator(mOutput, false, COLUMNS, ROWS, INITIAL_CELL_WIDTH_PIXELS,
            INITIAL_CELL_HEIGHT_PIXELS, 100, null);
        assertTrue("library failed to load: " + GhosttyNative.loadError(), terminal.useGhosttyCore());
        mBitmaps = new GhosttyKittyGraphicsTest.FakeBitmaps();
        mBudget = new KittyImageStore.FrameBudget();
        mCache = new GhosttyImageCache<>(terminal.ghosttyCore(), mBitmaps, mBudget, KittyImageStore.MAX_FRAME_BYTES);
        terminal.useGhosttyImageCacheForTests(mCache);
        return terminal;
    }

    private static void append(TerminalEmulator terminal, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        terminal.append(bytes, bytes.length);
    }

    /** A 2x1 RGBA image placed at the cursor. */
    private static String image(int id) {
        byte[] rgba = {(byte) 0xff, 0, 0, (byte) 0xff, 0, (byte) 0xff, 0, (byte) 0x80};
        return "\033_Ga=T,i=" + id + ",f=32,s=2,v=1,q=2;" + Base64.getEncoder().encodeToString(rgba) + "\033\\";
    }

    private static List<KittyPlacement> draw(TerminalEmulator terminal) {
        List<KittyPlacement> out = new ArrayList<>();
        terminal.collectKittyPlacements(0, ROWS, out);
        return out;
    }

    // ---- trim reaches the image cache ----

    public void testAPaneIsTrimmableExactlyWhileItsGhosttyImagesLive() {
        GhosttyImageCache<Bitmap> cache = new GhosttyImageCache<>(null, null, new KittyImageStore.FrameBudget(), 0);
        GhosttyImages images = new GhosttyImages(null, mOutput, () -> 0L, cache);
        assertTrue(GhosttyMemory.hasImageTrim(images));
        images.setVisible(false); // the cache gives its bitmaps back, the pane stays registered
        images.setVisible(true);
        assertTrue(GhosttyMemory.hasImageTrim(images));
        images.release();
        assertFalse(GhosttyMemory.hasImageTrim(images));
    }

    public void testTrimDropsTheBitmapsOfImagesOnScreen() {
        if (!hostLibrary()) return;
        TerminalEmulator terminal = ghosttyPane();
        append(terminal, "ab" + image(3));
        assertEquals(1, draw(terminal).size());
        assertEquals(8, mCache.bytes());
        assertEquals(8, mBudget.used());

        // A level below the trim level keeps them.
        GhosttyMemory.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
        assertEquals(8, mCache.bytes());

        GhosttyMemory.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND);
        assertEquals("bitmaps dropped", 0, mCache.size());
        assertEquals("budget given back", 0, mBudget.used());

        // Hidden and shown again, the pane is still reached by the next trim.
        terminal.setKittyAnimationsVisible(false);
        terminal.setKittyAnimationsVisible(true);
        assertEquals("drawn again from Ghostty's store", 1, draw(terminal).size());
        assertEquals(8, mCache.bytes());
        GhosttyMemory.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
        assertEquals(0, mCache.size());

        GhosttyImages images = terminal.ghosttyImages();
        terminal.releaseGhosttyCore();
        assertFalse("a closed pane is not trimmed", GhosttyMemory.hasImageTrim(images));
    }

    // ---- a replay leaves nothing of the old core behind ----

    public void testReplayWithLinksAndAnImageOnScreenLeavesACleanJavaPane() {
        if (!hostLibrary()) return;
        int coresBefore = GhosttyMemory.liveCores();
        TerminalEmulator terminal = ghosttyPane();
        append(terminal, "see " + LINK + "\r\nab" + image(5) + "\r\n");
        assertEquals(1, draw(terminal).size());
        assertEquals(8, mCache.bytes());
        assertEquals("https://example.com/a", terminal.getHyperlinkUriAt(0, 4));
        GhosttyCore core = terminal.ghosttyCore();
        GhosttyImages images = terminal.ghosttyImages();
        assertEquals(coresBefore + 1, GhosttyMemory.liveCores());

        byte[] more = "after".getBytes(StandardCharsets.UTF_8);
        terminal.appendBatched(more, more.length);
        terminal.failNextGhosttySyncForTests(new IllegalStateException("injected"), false);
        terminal.flushBatchedOutput();

        // On the Java core, with the screen carried over.
        assertFalse(terminal.isGhosttyCoreActive());
        assertNull(terminal.ghosttyCore());
        assertEquals("see link", terminal.getContent().text(0, 0, COLUMNS, 0).trim());
        // The native handle is freed and the process no longer counts it.
        assertFalse(core.isAlive());
        assertEquals(coresBefore, GhosttyMemory.liveCores());
        // Images: the bitmaps and their budget are given back, and no trim reaches the old pane.
        assertNull(terminal.ghosttyImages());
        assertEquals(0, mCache.size());
        assertEquals(0, mCache.bytes());
        assertEquals(0, mBudget.used());
        assertFalse(GhosttyMemory.hasImageTrim(images));
        assertTrue("the Java core has no images of its own", draw(terminal).isEmpty());
        // Links: every id on screen resolves in the fresh pool, and the pool holds nothing else.
        TerminalHyperlinks pool = terminal.getHyperlinks();
        int linked = 0;
        for (int y = 0; y < ROWS; y++) {
            TerminalRow row = terminal.getContent().row(y);
            for (int x = 0; x < COLUMNS; x++) {
                int id = row.getHyperlinkId(x);
                if (id == TerminalHyperlinks.NO_LINK) continue;
                linked++;
                assertNotNull("id " + id + " resolves", pool.getUri(id));
            }
        }
        assertTrue("no ids left from the Ghostty core", pool.size() <= (linked > 0 ? 1 : 0));

        // Later output goes through the Java parser.
        append(terminal, "\r\nnext");
        assertFalse(terminal.isGhosttyCoreActive());
    }
}
