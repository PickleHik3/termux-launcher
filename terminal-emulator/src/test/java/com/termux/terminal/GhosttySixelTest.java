package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Sixel on the Ghostty core. The decoder tests run everywhere; the rest run only in builds made
 * with {@code -Pghostty=true}. The Java core cannot draw a sixel on a JVM (its pixels live in an
 * Android Bitmap, which the unit-test stubs do not provide), so the placement and cursor the two
 * cores must agree on are written out here from {@link TerminalBitmap}'s and
 * {@code TerminalEmulator}'s rules: cells 13 x 15 pixels, the image padded to whole cells and no
 * wider than the room right of the cursor, then the cursor on the row below it in the column the
 * sixel started in.
 */
public class GhosttySixelTest extends TerminalTestCase {

    /** 20 x 30 pixels of opaque red: five six-pixel bands of 20 columns. */
    private static final String RED_20X30 = "\"1;1;20;30#1;2;100;0;0#1!20~-!20~-!20~-!20~-!20~";

    private static String sixel(String body) {
        return "\033Pq" + body + "\033\\";
    }

    private static boolean libraryBuilt() {
        return System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) != null;
    }

    private GhosttyKittyGraphicsTest.RecordingOutput mRecording;
    private GhosttyKittyGraphicsTest.FakeBitmaps mBitmaps;
    private GhosttyImages mImages;

    private boolean ghostty(int columns, int rows) {
        if (!libraryBuilt()) return false;
        mRecording = new GhosttyKittyGraphicsTest.RecordingOutput();
        mOutput = mRecording;
        mTerminal = new TerminalEmulator(mOutput, false, columns, rows, INITIAL_CELL_WIDTH_PIXELS,
            INITIAL_CELL_HEIGHT_PIXELS, rows * 2, null);
        assertTrue("library failed to load: " + GhosttyNative.loadError(), mTerminal.useGhosttyCore());
        mBitmaps = new GhosttyKittyGraphicsTest.FakeBitmaps();
        GhosttyCore core = mTerminal.ghosttyCore();
        mImages = new GhosttyImages(core, mOutput, () -> 1000L,
            new GhosttyImageCache<>(core, mBitmaps, new KittyImageStore.FrameBudget(), KittyImageStore.MAX_FRAME_BYTES));
        return true;
    }

    private List<KittyPlacement> collect() {
        List<KittyPlacement> out = new ArrayList<>();
        mImages.collect(0, mTerminal.mRows, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, out);
        return out;
    }

    // ---- the decoder, with the Java core's rules ----

    private static int[] meta() {
        return new int[GhosttySixel.META_COUNT];
    }

    private static int[] decode(String body, int cursorX, int columns, int[] meta) {
        GhosttySixel decoder = new GhosttySixel(null);
        decoder.begin();
        byte[] bytes = body.getBytes(StandardCharsets.ISO_8859_1);
        decoder.feed(bytes, bytes.length);
        return decoder.end(cursorX, columns, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, meta);
    }

    public void testDecoderPadsToWholeCellsAndKeepsTheColours() {
        int[] meta = meta();
        int[] pixels = decode(RED_20X30, 0, 20, meta);
        assertNotNull(pixels);
        // 20 pixels wide is not whole cells of 13: padded to 26; 30 is two cells of 15.
        assertEquals(26, meta[GhosttySixel.META_WIDTH]);
        assertEquals(30, meta[GhosttySixel.META_HEIGHT]);
        assertEquals(2, meta[GhosttySixel.META_CELLS_WIDE]);
        assertEquals(2, meta[GhosttySixel.META_CELLS_HIGH]);
        int stride = meta[GhosttySixel.META_RASTER_WIDTH];
        assertTrue(stride >= 20 && meta[GhosttySixel.META_RASTER_HEIGHT] >= 30);
        assertEquals(0xffff0000, pixels[0]);
        assertEquals(0xffff0000, pixels[29 * stride + 19]);
        // Nothing was drawn past the sixel's own width.
        assertEquals(0, pixels[20]);
    }

    public void testDecoderReadsColorRegistersAndRepeats() {
        int[] meta = meta();
        // Register 2 green, register 3 the initial map's colour 3; one column of bit 0 in each, then
        // a repeat of a full six-pixel column.
        int[] pixels = decode("#2;2;0;100;0#2@#3@!3~$", 0, 20, meta);
        assertNotNull(pixels);
        int stride = meta[GhosttySixel.META_RASTER_WIDTH];
        assertEquals(0xff00ff00, pixels[0]);
        // '@' sets only the top pixel; '~' (all six) over it from column 0 on, in the last colour.
        assertEquals(0xff33cc33, pixels[5 * stride + 2]);
        assertEquals(0, pixels[6 * stride]);
    }

    public void testDecoderLimitsTheWidthToTheRoomRightOfTheCursor() {
        int[] meta = meta();
        // 60 pixels wide at column 18 of 20: two cells of room, so 26 pixels.
        assertNotNull(decode("#1;2;100;0;0#1!60~", 18, 20, meta));
        assertEquals(26, meta[GhosttySixel.META_WIDTH]);
        assertEquals(2, meta[GhosttySixel.META_CELLS_WIDE]);
        assertEquals(1, meta[GhosttySixel.META_CELLS_HIGH]);
    }

    public void testDecoderKeepsTheRasterWhenTheContentIsWholeCells() {
        int[] meta = meta();
        // 26 x 30 is whole cells, so the Java core keeps its bitmap as it is: 100 x 100 here.
        assertNotNull(decode("\"1;1;26;30#1;2;100;0;0#1!26~-!26~-!26~-!26~-!26~", 0, 20, meta));
        assertEquals(meta[GhosttySixel.META_RASTER_WIDTH], meta[GhosttySixel.META_WIDTH]);
        assertEquals(meta[GhosttySixel.META_RASTER_HEIGHT], meta[GhosttySixel.META_HEIGHT]);
    }

    public void testDecoderDropsAnOverlongRepeatAndLaterBytes() {
        int[] meta = meta();
        assertNull(decode("#1;2;100;0;0#1!9000~~~", 0, 20, meta));
    }

    public void testDecoderDropsAStringPastTheCapAndReadsTheNext() {
        GhosttySixel decoder = new GhosttySixel(null, GhosttySixel.maxPixelBytes(), 64);
        decoder.begin();
        byte[] big = ("#1;2;100;0;0#1" + "~".repeat(200)).getBytes(StandardCharsets.ISO_8859_1);
        decoder.feed(big, big.length);
        assertFalse(decoder.isDecoding());
        assertNull(decoder.end(0, 20, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, meta()));
        // The next sixel starts clean.
        decoder.begin();
        byte[] small = "#1;2;100;0;0#1~".getBytes(StandardCharsets.ISO_8859_1);
        decoder.feed(small, small.length);
        assertNotNull(decoder.end(0, 20, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, meta()));
    }

    public void testDecoderDropsPixelsPastTheCap() {
        // 10,000 bytes of pixels: the starting 100 x 100 bitmap fits, a 200-wide one does not.
        GhosttySixel decoder = new GhosttySixel(null, 40_000, 1 << 20);
        decoder.begin();
        byte[] bytes = "#1;2;100;0;0#1!200~".getBytes(StandardCharsets.ISO_8859_1);
        decoder.feed(bytes, bytes.length);
        assertNull(decoder.end(0, 20, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, meta()));
    }

    public void testDecoderReadsTheSameWhereverTheStringIsCut() {
        byte[] bytes = RED_20X30.getBytes(StandardCharsets.ISO_8859_1);
        int[] whole = meta();
        int[] expected = decode(RED_20X30, 0, 20, whole);
        for (int cut = 1; cut < bytes.length; cut++) {
            GhosttySixel decoder = new GhosttySixel(null);
            decoder.begin();
            decoder.feed(java.util.Arrays.copyOfRange(bytes, 0, cut), cut);
            decoder.feed(java.util.Arrays.copyOfRange(bytes, cut, bytes.length), bytes.length - cut);
            int[] meta = meta();
            int[] pixels = decoder.end(0, 20, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, meta);
            assertNotNull("cut at " + cut, pixels);
            assertTrue("cut at " + cut, java.util.Arrays.equals(whole, meta));
            assertTrue("pixels, cut at " + cut, java.util.Arrays.equals(expected, pixels));
        }
    }

    // ---- on the Ghostty core ----

    public void testSixelIsPlacedAtTheCursorAndLeavesItBelow() {
        if (!ghostty(20, 8)) return;
        enterString("\033[2;4Hab");
        // Cursor on row 1, column 5 (0-based).
        enterString(sixel(RED_20X30));
        List<KittyPlacement> placements = collect();
        assertEquals(1, placements.size());
        KittyPlacement p = placements.get(0);
        assertEquals(5, p.getColumn());
        assertEquals(1, p.getRow());
        assertEquals(26, p.getSourceWidth());
        assertEquals(30, p.getSourceHeight());
        assertTrue("an id clear of program-chosen ones", p.getImageId() >= 0xF0000000L);
        int[] pixels = mBitmaps.pixels.get(p.getBitmap());
        assertEquals(0xffff0000, pixels[0]);
        assertEquals(0xffff0000, pixels[29 * 26 + 19]);
        assertEquals(0, pixels[29 * 26 + 20]);
        // The image covers rows 1 and 2; the cursor is on row 3, in the column it started in.
        assertCursorAt(3, 5);
        enterString("x");
        assertCursorAt(3, 6);
        assertLineIs(1, "   ab" + " ".repeat(15));
    }

    public void testSixelAtTheBottomScrollsLikeTheJavaCore() {
        if (!ghostty(20, 6)) return;
        enterString("top\033[6;3H");
        enterString(sixel(RED_20X30));
        // Two rows of image from row 5 scroll the screen once; the cursor row below them scrolls it
        // once more: the image on rows 3 and 4, the cursor on row 5 in column 2, and "top" gone.
        List<KittyPlacement> placements = collect();
        assertEquals(1, placements.size());
        assertEquals(3, placements.get(0).getRow());
        assertEquals(2, placements.get(0).getColumn());
        assertCursorAt(5, 2);
    }

    public void testSixelTouchingTheRightEdgeStillLeavesTheCursorInItsColumn() {
        if (!ghostty(20, 8)) return;
        enterString("\033[1;19H");
        enterString(sixel(RED_20X30));
        // Two cells wide from column 18 ends at the edge; the image is cut no wider than that.
        List<KittyPlacement> placements = collect();
        assertEquals(1, placements.size());
        assertEquals(18, placements.get(0).getColumn());
        assertCursorAt(2, 18);
    }

    public void testSixelWiderThanTheRoomIsCutAtTheEdge() {
        if (!ghostty(20, 8)) return;
        enterString("\033[1;19H");
        enterString(sixel("#1;2;100;0;0#1!60~"));
        List<KittyPlacement> placements = collect();
        assertEquals(1, placements.size());
        assertEquals(26, placements.get(0).getSourceWidth());
        assertCursorAt(1, 18);
    }

    public void testSixelSplitAcrossAppendsGivesTheSameScreen() {
        if (!ghostty(20, 8)) return;
        String stream = "ab" + sixel(RED_20X30) + "cd";
        enterString(stream);
        List<KittyPlacement> whole = collect();
        int row = mTerminal.getCursorRow(), col = mTerminal.getCursorCol();
        assertEquals(1, whole.size());
        for (int cut = 1; cut < stream.length(); cut++) {
            assertTrue(ghostty(20, 8));
            enterString(stream.substring(0, cut));
            enterString(stream.substring(cut));
            List<KittyPlacement> parts = collect();
            assertEquals("cut at " + cut, 1, parts.size());
            assertEquals("cut at " + cut, whole.get(0).getRow(), parts.get(0).getRow());
            assertEquals("cut at " + cut, whole.get(0).getColumn(), parts.get(0).getColumn());
            assertEquals("cut at " + cut, whole.get(0).getSourceWidth(), parts.get(0).getSourceWidth());
            assertEquals("cut at " + cut, row, mTerminal.getCursorRow());
            assertEquals("cut at " + cut, col, mTerminal.getCursorCol());
        }
    }

    public void testMalformedSixelIsDroppedAndLaterOutputParses() {
        if (!ghostty(20, 8)) return;
        enterString("a" + sixel("#1;2;100;0;0#1!9000~~~") + "b");
        assertEquals(0, collect().size());
        assertCursorAt(0, 2);
        // A string cancelled half way draws nothing and ends at the CAN.
        enterString("\033Pq#1~~~\030c");
        assertEquals(0, collect().size());
        assertCursorAt(0, 3);
        // Nonsense inside the string is skipped, as the Java core skips it.
        enterString(sixel("\001zz#x~~"));
        assertEquals(1, collect().size());
    }

    public void testOtherDcsStringsAreLeftToGhostty() {
        if (!ghostty(20, 8)) return;
        enterString("a\033P1;2|abc\033\\b\033P$q\"p\033\\c");
        assertEquals(0, collect().size());
        assertCursorAt(0, 3);
    }

    public void testSixelClearsAndScrollsWithTheText() {
        if (!ghostty(20, 6)) return;
        enterString(sixel(RED_20X30));
        assertEquals(1, collect().size());
        enterString("\033[2J");
        assertEquals(0, collect().size());
        enterString("\033[H" + sixel(RED_20X30));
        assertEquals(1, collect().size());
        enterString("\n\n\n\n\n\n\n\n\n\n\n\n");
        assertEquals("scrolled off the screen", 0, collect().size());
    }

    public void testSeveralSixelsGetOwnImages() {
        if (!ghostty(20, 10)) return;
        enterString(sixel(RED_20X30) + sixel(RED_20X30));
        List<KittyPlacement> placements = collect();
        assertEquals(2, placements.size());
        assertTrue(placements.get(0).getImageId() != placements.get(1).getImageId());
        assertEquals(2, Math.abs(placements.get(0).getRow() - placements.get(1).getRow()));
        assertCursorAt(4, 0);
    }

    public void testDa1ReportsSixelOnGhostty() {
        if (!ghostty(10, 3)) return;
        enterString("\033[c");
        assertEquals("\033[?62;4;22c", mOutput.getOutputAndClear());
        // The secondary answer stays the library's.
        enterString("\033[>c");
        assertEquals("\033[>1;0;0c", mOutput.getOutputAndClear());
    }
}
