package com.termux.terminal;

import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kitty graphics on the Ghostty core, against the real libghostty-vt built for this machine. Runs
 * only in builds made with {@code -Pghostty=true}; elsewhere every test returns at once.
 */
public class GhosttyKittyGraphicsTest extends TerminalTestCase {

    /** An output that keeps the delayed redraws GhosttyImages posts. */
    static final class RecordingOutput extends MockTerminalOutput {
        final List<Long> delays = new ArrayList<>();
        Runnable pending;
        int cancels;
        int screenChanges;

        @Override
        public void postTerminalUpdateDelayed(Runnable update, long delayMillis) {
            delays.add(delayMillis);
            pending = update;
        }

        @Override
        public void cancelTerminalUpdateDelayed(Runnable update) {
            cancels++;
            if (pending == update) pending = null;
        }

        @Override
        public void onScreenChanged() {
            screenChanges++;
        }
    }

    /** Bitmaps that are stand-ins, with the pixels each was given kept beside it. */
    static final class FakeBitmaps implements GhosttyImageCache.Bitmaps<Bitmap> {
        final Map<Bitmap, int[]> pixels = new HashMap<>();
        int created;

        @Override
        public Bitmap create(int width, int height) {
            try {
                Bitmap bitmap = KittyPlacementLayerTest.standInBitmap();
                pixels.put(bitmap, new int[width * height]);
                created++;
                return bitmap;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void setRows(Bitmap bitmap, int[] argb, int width, int y, int rows) {
            System.arraycopy(argb, 0, pixels.get(bitmap), y * width, rows * width);
        }
    }

    private RecordingOutput mRecording;
    private long mNow = 1000;
    private FakeBitmaps mBitmaps;
    private KittyImageStore.FrameBudget mBudget;
    private GhosttyImages mImages;

    private static boolean libraryBuilt() {
        return System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) != null;
    }

    /** A Ghostty terminal whose images are read through a GhosttyImages with fakes around it. */
    private boolean ghostty(int columns, int rows) {
        if (!libraryBuilt()) return false;
        mRecording = new RecordingOutput();
        mOutput = mRecording;
        mTerminal = new TerminalEmulator(mOutput, false, columns, rows, INITIAL_CELL_WIDTH_PIXELS,
            INITIAL_CELL_HEIGHT_PIXELS, rows * 2, null);
        assertTrue("library failed to load: " + GhosttyNative.loadError(), mTerminal.useGhosttyCore());
        mBitmaps = new FakeBitmaps();
        mBudget = new KittyImageStore.FrameBudget();
        GhosttyCore core = mTerminal.ghosttyCore();
        mImages = new GhosttyImages(core, mOutput, () -> mNow,
            new GhosttyImageCache<>(core, mBitmaps, mBudget, KittyImageStore.MAX_FRAME_BYTES));
        return true;
    }

    private static String rgba(int... argb) {
        byte[] bytes = new byte[argb.length * 4];
        for (int i = 0; i < argb.length; i++) {
            bytes[i * 4] = (byte) (argb[i] >> 16);
            bytes[i * 4 + 1] = (byte) (argb[i] >> 8);
            bytes[i * 4 + 2] = (byte) argb[i];
            bytes[i * 4 + 3] = (byte) (argb[i] >>> 24);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String apc(String control, String payload) {
        return "\033_G" + control + (payload == null ? "" : ";" + payload) + "\033\\";
    }

    private List<KittyPlacement> collect() {
        List<KittyPlacement> out = new ArrayList<>();
        mImages.collect(0, mTerminal.mRows, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, out);
        return out;
    }

    // ---- a=q answers are the Java core's, byte for byte ----

    private String javaReply(String input) {
        withTerminalSized(20, 5);
        mOutput.getOutputAndClear();
        enterString(input);
        return mOutput.getOutputAndClear();
    }

    private String ghosttyReply(String input) {
        assertTrue(ghostty(20, 5));
        mOutput.getOutputAndClear();
        enterString(input);
        return mOutput.getOutputAndClear();
    }

    private String ghosttyReplySplit(String input, int at) {
        assertTrue(ghostty(20, 5));
        mOutput.getOutputAndClear();
        enterString(input.substring(0, at));
        enterString(input.substring(at));
        return mOutput.getOutputAndClear();
    }

    /** A 2 x 1 RGBA PNG: opaque red, then half-transparent green. */
    private static byte[] tinyPng() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        chunk(out, "IHDR", new byte[] {0, 0, 0, 2, 0, 0, 0, 1, 8, 6, 0, 0, 0});
        byte[] raw = {0, (byte) 0xff, 0, 0, (byte) 0xff, 0, (byte) 0xff, 0, (byte) 0x80};
        java.util.zip.Deflater deflater = new java.util.zip.Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] buffer = new byte[256];
        int n = deflater.deflate(buffer);
        deflater.end();
        chunk(out, "IDAT", java.util.Arrays.copyOf(buffer, n));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws Exception {
        java.io.DataOutputStream stream = new java.io.DataOutputStream(out);
        stream.writeInt(data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        stream.write(typeBytes);
        stream.write(data);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(typeBytes);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }

    public void testQueryAnswersMatchTheJavaCore() throws Exception {
        if (!libraryBuilt()) return;
        String png = Base64.getEncoder().encodeToString(tinyPng());
        File file = File.createTempFile("ghostty-kitty-query", ".rgb");
        file.deleteOnExit();
        java.nio.file.Files.write(file.toPath(), new byte[] {1, 2, 3});
        String path = Base64.getEncoder().encodeToString(file.getPath().getBytes(StandardCharsets.UTF_8));
        String missing = Base64.getEncoder().encodeToString("/nonexistent/ghostty-kitty".getBytes(StandardCharsets.UTF_8));
        String[] queries = {
            apc("i=31,s=1,v=1,a=q,t=d,f=24", "AAAA"),            // the canonical support probe
            apc("i=1,s=2,v=1,a=q,t=d,f=24", "AAAA"),             // wrong length
            apc("i=2,a=q,t=d,f=7", "AAAA"),                      // unknown format
            apc("i=3,a=q,t=s,f=100", "AAAA"),                    // shared memory: refused
            apc("i=4,s=1,v=1,a=q,t=s,f=24", "AAAA"),
            apc("i=5,a=q,f=100", png),                           // a real PNG
            apc("i=6,a=q,f=100", "AAAAAAAA"),                    // not a PNG
            apc("i=7,s=1,v=1,a=q,f=24", "!!!!"),                 // not base64
            apc("i=8,s=1,v=1,a=q,t=f,f=24", path),               // a file of the right size
            apc("i=9,s=1,v=1,a=q,t=f,f=24", missing),            // a missing file
            apc("i=10,s=1,v=1,a=q,f=24,o=x", "AAAA"),            // unknown compression
            apc("i=11,s=1,v=1,a=q,f=24,q=1", "AAAA"),            // quiet: no OK
            apc("i=12,s=2,v=1,a=q,f=24,q=2", "AAAA"),            // quieter: no error either
            apc("i=13,I=4,p=2,s=1,v=1,a=q,f=24", "AAAA"),        // number and placement echoed
            apc("a=q,i=14,s=1,v=1,f=32", "AAAAAA=="),            // a first
        };
        for (String query : queries) {
            String expected = javaReply(query);
            assertEquals(query, expected, ghosttyReply(query));
            assertEquals("split " + query, expected, ghosttyReplySplit(query, 6));
            assertEquals("split in the payload " + query, expected, ghosttyReplySplit(query, query.length() - 4));
        }
    }

    public void testQueryRepliesStayInOrderWithGhosttysOwn() {
        if (!libraryBuilt()) return;
        String input = "\033[c" + apc("i=31,s=1,v=1,a=q,t=d,f=24", "AAAA") + "\033[5n";
        String reply = ghosttyReply(input);
        int da = reply.indexOf("\033[?");
        int query = reply.indexOf("\033_Gi=31;OK\033\\");
        int status = reply.indexOf("\033[0n");
        assertTrue(reply, da >= 0 && query > da && status > query);
    }

    public void testATransmissionIsNotTakenForAQuery() {
        if (!ghostty(10, 4)) return;
        enterString(apc("a=T,i=1,f=32,s=1,v=1", rgba(0xff112233)));
        assertEquals("\033_Gi=1;OK\033\\", mOutput.getOutputAndClear());
        assertEquals(1, collect().size());
    }

    /** A one-pixel RGBA file named the way kitten names its temp files, in the JVM's temp dir. */
    private static String tempFilePayload() throws Exception {
        File file = File.createTempFile("kitty-tty-graphics-protocol-", ".rgba");
        file.deleteOnExit();
        java.nio.file.Files.write(file.toPath(), new byte[] {1, 2, 3, 4});
        return Base64.getEncoder().encodeToString(file.getPath().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * kitten icat probes t=t with a query and then sends every GIF frame by the medium the reply
     * allowed, so the reply must be the Ghostty core's own: an OK for a temp file Ghostty then
     * refuses drew nothing and leaked "ENOENT: image not found" for each animation command.
     */
    public void testATempFileQueryAgreesWithWhatGhosttyLoads() throws Exception {
        if (!ghostty(10, 4)) return;
        mOutput.getOutputAndClear();
        enterString(apc("a=q,i=21,s=1,v=1,f=32,t=t", tempFilePayload()));
        boolean queryOk = "\033_Gi=21;OK\033\\".equals(mOutput.getOutputAndClear());
        enterString(apc("a=t,i=22,s=1,v=1,f=32,t=t,S=4", tempFilePayload()));
        boolean loaded = "\033_Gi=22;OK\033\\".equals(mOutput.getOutputAndClear());
        assertEquals("query OK " + queryOk + ", load OK " + loaded, loaded, queryOk);
    }

    // ---- transmit, place, pixels ----

    public void testATransmittedImageIsPlacedAtTheCursorWithItsPixels() {
        if (!ghostty(10, 4)) return;
        enterString("ab");
        enterString(apc("a=T,i=3,f=32,s=2,v=1,q=2", rgba(0xffff0000, 0x8000ff00)));
        List<KittyPlacement> placements = collect();
        assertEquals(1, placements.size());
        KittyPlacement p = placements.get(0);
        assertEquals(3, p.getImageId());
        assertEquals(2, p.getColumn());
        assertEquals(0, p.getRow());
        assertEquals(2, p.getWidth());
        assertEquals(1, p.getHeight());
        assertEquals(2, p.getSourceWidth());
        assertEquals(1, p.getSourceHeight());
        assertEquals(KittyPlacement.LAYER_ABOVE_TEXT, p.getLayer());
        assertEquals(INITIAL_CELL_WIDTH_PIXELS, p.getCellWidth());
        int[] pixels = mBitmaps.pixels.get(p.getBitmap());
        assertEquals(0xffff0000, pixels[0]);
        assertEquals(0x8000ff00, pixels[1]);
        assertEquals(8, mImages.cache().bytes());
        assertEquals(8, mBudget.used());
    }

    public void testRgbAndPngImagesDecode() throws Exception {
        if (!ghostty(10, 4)) return;
        byte[] rgb = {(byte) 0x10, (byte) 0x20, (byte) 0x30};
        enterString(apc("a=T,i=4,f=24,s=1,v=1,q=2", Base64.getEncoder().encodeToString(rgb)));
        enterString(apc("a=T,i=5,f=100,q=2", Base64.getEncoder().encodeToString(tinyPng())));
        List<KittyPlacement> placements = collect();
        assertEquals(2, placements.size());
        for (KittyPlacement p : placements) {
            int[] pixels = mBitmaps.pixels.get(p.getBitmap());
            if (p.getImageId() == 4) {
                assertEquals(0xff102030, pixels[0]);
            } else {
                assertEquals(2, p.getSourceWidth());
                assertEquals(0xffff0000, pixels[0]);
                assertEquals(0x8000ff00, pixels[1]);
            }
        }
    }

    public void testAPlacementIsReadAgainOnlyWhenTheListMoves() {
        if (!ghostty(10, 4)) return;
        enterString(apc("a=T,i=3,f=32,s=1,v=1,q=2", rgba(0xffffffff)));
        KittyPlacement first = collect().get(0);
        long generation = mImages.placementsGeneration();
        enterString("text only");
        KittyPlacement again = collect().get(0);
        assertSame(first, again);
        assertEquals(generation, mImages.placementsGeneration());
        assertEquals("one copy of the pixels", 1, mBitmaps.created);
        enterString(apc("a=p,i=3,p=9,q=2", null));
        assertEquals(2, collect().size());
        assertTrue(generation != mImages.placementsGeneration());
    }

    public void testDeleteForms() {
        if (!ghostty(10, 6)) return;
        enterString(apc("a=T,i=1,p=1,f=32,s=1,v=1,q=2", rgba(0xffffffff)) + "\r\n");
        enterString(apc("a=T,i=2,p=1,f=32,s=1,v=1,q=2", rgba(0xffffffff)) + "\r\n");
        enterString(apc("a=p,i=2,p=2,q=2", null) + "\r\n");
        assertEquals(3, collect().size());
        enterString(apc("a=d,d=i,i=2,p=2,q=2", null));
        assertEquals(2, collect().size());
        enterString(apc("a=d,d=i,i=1,q=2", null));
        List<KittyPlacement> left = collect();
        assertEquals(1, left.size());
        assertEquals(2, left.get(0).getImageId());
        enterString(apc("a=d,d=A,q=2", null));
        assertEquals(0, collect().size());
        // Two frames later the bitmaps of images no longer drawn are gone, and their budget with them.
        collect();
        assertEquals(0, mImages.cache().size());
        assertEquals(0, mBudget.used());
    }

    public void testLayersFollowZ() {
        if (!ghostty(10, 6)) return;
        enterString(apc("a=T,i=1,f=32,s=1,v=1,z=5,q=2", rgba(0xffffffff)) + "\r\n");
        enterString(apc("a=T,i=2,f=32,s=1,v=1,z=-1,q=2", rgba(0xffffffff)) + "\r\n");
        enterString(apc("a=T,i=3,f=32,s=1,v=1,z=-1073741825,q=2", rgba(0xffffffff)) + "\r\n");
        enterString(apc("a=T,i=4,f=32,s=1,v=1,z=-7,q=2", rgba(0xffffffff)) + "\r\n");
        Map<Long, KittyPlacement> byId = new HashMap<>();
        for (KittyPlacement p : collect()) byId.put(p.getImageId(), p);
        assertEquals(KittyPlacement.LAYER_ABOVE_TEXT, byId.get(1L).getLayer());
        assertEquals(KittyPlacement.LAYER_BELOW_TEXT, byId.get(2L).getLayer());
        assertEquals(KittyPlacement.LAYER_BELOW_BACKGROUND, byId.get(3L).getLayer());
        assertEquals(KittyPlacement.LAYER_BELOW_TEXT, byId.get(4L).getLayer());
        assertEquals(5, byId.get(1L).getZ());
        assertEquals(-7, byId.get(4L).getZ());
        assertEquals(-1073741825, byId.get(3L).getZ());
    }

    // ---- Unicode placeholders ----

    public void testUnicodePlaceholdersResolveThroughTheirCells() {
        if (!ghostty(10, 4)) return;
        // A virtual placement 2 columns by 1 row of image 7, then its two cells: image id in the
        // foreground colour, row and column as diacritics.
        enterString(apc("a=T,U=1,i=7,c=2,r=1,f=32,s=2,v=1,q=2", rgba(0xffff0000, 0xff0000ff)));
        enterString("\033[38;5;7m􎻮̅̅􎻮̅̍\033[m");
        List<KittyPlacement> placements = collect();
        assertEquals("a placeholder image draws in its row, not as a placement", 0, placements.size());
        TerminalRow row = mTerminal.getContent().row(0);
        // The cell's diacritics, after the placeholder's own surrogate pair, as the renderer reads them.
        int end = row.findStartOfColumn(1);
        KittyUnicodePlaceholder.Cell cell = KittyUnicodePlaceholder.decode(row.mText, 2, end,
            TextStyle.decodeForeColor(row.getStyle(0)), TextStyle.DECORATION_COLOR_DEFAULT,
            TextStyle.DECORATION_COLOR_DEFAULT, null);
        assertNotNull(cell);
        assertEquals(7, cell.imageId);
        KittyImagePlaceholder out = new KittyImagePlaceholder();
        assertTrue(mImages.placeholder(7, 0, out));
        assertEquals(2, out.columns);
        assertEquals(1, out.rows);
        assertEquals(2, out.sourceWidth);
        assertEquals(1, out.sourceHeight);
        assertEquals(0xffff0000, mBitmaps.pixels.get(out.bitmap)[0]);
        assertEquals(out.generation, mImages.imageGeneration(7));
        assertFalse(mImages.placeholder(8, 0, out));
        // The emulator answers the renderer from Ghostty too (its own cache cannot make platform
        // bitmaps on the JVM, so the generation is what is compared).
        assertEquals(out.generation, mTerminal.getKittyImageGeneration(7));
        assertEquals(0, mTerminal.getKittyImageGeneration(8));
    }

    // ---- animation ----

    private void startTwoFrameAnimation() {
        enterString(apc("a=T,i=9,f=32,s=1,v=1,q=2", rgba(0xffff0000)));
        enterString(apc("a=f,i=9,f=32,s=1,v=1,z=100,q=2", rgba(0xff0000ff)));
        enterString(apc("a=a,i=9,r=1,z=100,q=2", null));
        enterString(apc("a=a,i=9,s=3,v=1,q=2", null));
    }

    /** kitten icat's GIF sequence: addressed by image number, frame controls without q. */
    public void testAGifAddressedByImageNumberAnimates() {
        if (!ghostty(10, 4)) return;
        mOutput.getOutputAndClear();
        String number = "I=1401116184";
        enterString(apc("a=T," + number + ",f=32,s=1,v=1,q=2", rgba(0xffff0000)));
        enterString(apc("a=a," + number + ",r=1,z=100,v=1", null));
        enterString(apc("a=a," + number + ",s=2", null));
        enterString(apc("a=f," + number + ",f=32,s=1,v=1,z=100,q=2", rgba(0xff0000ff)));
        enterString(apc("a=a," + number + ",s=3", null));
        String replies = mOutput.getOutputAndClear();
        assertFalse(replies, replies.contains("ENOENT"));
        List<KittyPlacement> placements = collect();
        assertEquals(1, placements.size());
        KittyPlacement p = placements.get(0);
        int firstPixel = mBitmaps.pixels.get(p.getBitmap())[0];
        assertEquals(0xffff0000, firstPixel);
        assertEquals(1, mRecording.delays.size());
        mNow += mRecording.delays.get(0);
        mRecording.pending.run();
        p = collect().get(0);
        assertEquals(0xff0000ff, mBitmaps.pixels.get(p.getBitmap())[0]);
    }

    public void testAnimationTicksOncePerFrameAndPostsOneRedraw() {
        if (!ghostty(10, 4)) return;
        startTwoFrameAnimation();
        KittyPlacement p = collect().get(0);
        long first = mImages.imageGeneration(9);
        int firstPixel = mBitmaps.pixels.get(p.getBitmap())[0];
        assertEquals(1, mRecording.delays.size());
        long delay = mRecording.delays.get(0);
        assertTrue("delay " + delay, delay >= GhosttyImages.MIN_REDRAW_MILLIS && delay <= 100);
        // Another frame before the redraw is due posts nothing new.
        mNow += 1;
        collect();
        assertEquals(1, mRecording.delays.size());
        // The redraw runs when due; the frame it draws shows the next animation frame.
        mNow += delay;
        mRecording.pending.run();
        assertEquals(1, mRecording.screenChanges);
        p = collect().get(0);
        assertTrue(first != mImages.imageGeneration(9));
        assertTrue(firstPixel != mBitmaps.pixels.get(p.getBitmap())[0]);
        assertEquals("the same bitmap, refilled", 1, mBitmaps.created);
        assertEquals(2, mRecording.delays.size());
    }

    public void testRedrawsAreNeverSoonerThanAFrame() {
        if (!ghostty(10, 4)) return;
        enterString(apc("a=T,i=9,f=32,s=1,v=1,q=2", rgba(0xffff0000)));
        enterString(apc("a=f,i=9,f=32,s=1,v=1,z=1,q=2", rgba(0xff0000ff)));
        enterString(apc("a=a,i=9,r=1,z=1,q=2", null));
        enterString(apc("a=a,i=9,s=3,v=1,q=2", null));
        collect();
        assertEquals(Long.valueOf(GhosttyImages.MIN_REDRAW_MILLIS), mRecording.delays.get(0));
    }

    public void testNothingIsScheduledAtRest() {
        if (!ghostty(10, 4)) return;
        enterString(apc("a=T,i=9,f=32,s=1,v=1,q=2", rgba(0xffff0000)));
        collect();
        collect();
        assertTrue(mRecording.delays.isEmpty());
    }

    public void testAHiddenPaneDoesNotTick() {
        if (!ghostty(10, 4)) return;
        startTwoFrameAnimation();
        collect();
        assertEquals(1, mRecording.delays.size());
        mImages.setVisible(false);
        assertNull("the posted redraw is withdrawn", mRecording.pending);
        assertEquals("a hidden pane keeps no bitmaps", 0, mBudget.used());
        long generation = mImages.cache().generationOf(9);
        mNow += 1000;
        collect();
        assertEquals("no tick, no redraw", 1, mRecording.delays.size());
        assertEquals(generation, mTerminal.ghosttyCore().imageGeneration(9));
        mImages.setVisible(true);
        collect();
        assertEquals(2, mRecording.delays.size());
    }

    // ---- memory ----

    public void testBitmapsAreFreedOnTrimAndClose() {
        if (!ghostty(10, 4)) return;
        enterString(apc("a=T,i=3,f=32,s=1,v=1,q=2", rgba(0xffffffff)));
        collect();
        assertEquals(4, mBudget.used());
        mImages.trim();
        assertEquals(0, mBudget.used());
        collect();
        assertEquals(4, mBudget.used());
        mImages.release();
        assertEquals(0, mBudget.used());
    }

    public void testTheEmulatorReleasesItsBitmapsWithTheCore() {
        if (!libraryBuilt()) return;
        withTerminalSized(10, 4);
        assertTrue(mTerminal.useGhosttyCore());
        long before = KittyImageStore.sharedFrameBudget().used();
        enterString(apc("a=T,i=3,f=32,s=4,v=4,q=2", rgba(new int[16])));
        mTerminal.collectKittyPlacements(0, 4, new ArrayList<>());
        // On the JVM the platform bitmap is a stub that cannot be made, so nothing is charged;
        // what matters is that nothing stays charged after the pane closes or memory is trimmed.
        mTerminal.dropKittyAnimationFrames();
        assertEquals(before, KittyImageStore.sharedFrameBudget().used());
        mTerminal.releaseGhosttyCore();
        assertEquals(before, KittyImageStore.sharedFrameBudget().used());
    }

    public void testTheStoresShareTheJavaCoresConstants() {
        if (!libraryBuilt()) return;
        // Peak cost of a Ghostty pane's images: Ghostty's store, MAX_STORED_BYTES per screen, plus
        // on-screen bitmaps charged to the process-wide frame budget, MAX_FRAME_BYTES at most.
        assertEquals(32L * 1024 * 1024, KittyImageStore.MAX_STORED_BYTES);
        assertTrue(KittyImageStore.MAX_FRAME_BYTES >= 64L * 1024 * 1024);
        assertTrue(KittyImageStore.MAX_FRAME_BYTES <= 256L * 1024 * 1024);
        if (!ghostty(10, 4)) return;
        GhosttyImageCache<Bitmap> cache = GhosttyImages.bitmapCache(mTerminal.ghosttyCore());
        assertEquals(KittyImageStore.MAX_FRAME_BYTES, cache.limit());
        assertSame(KittyImageStore.sharedFrameBudget(), cache.budget());
        // Ghostty's store takes MAX_STORED_BYTES, not its own 10 MB default: a 20 MiB image fits,
        // one over 32 MiB does not.
        mOutput.getOutputAndClear();
        enterString(apc("a=t,i=20,f=32,s=2048,v=2560,o=z", zeros(2048 * 2560 * 4)));
        assertEquals("\033_Gi=20;OK\033\\", mOutput.getOutputAndClear());
        enterString(apc("a=t,i=21,f=32,s=2048,v=4097,o=z", zeros(2048 * 4097 * 4)));
        assertFalse(mOutput.getOutputAndClear().contains(";OK"));
    }

    private static String zeros(int length) {
        java.util.zip.Deflater deflater = new java.util.zip.Deflater();
        deflater.setInput(new byte[length]);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
        deflater.end();
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
