package com.termux.terminal;

import android.graphics.Bitmap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Kitty placements as a layer over the cells: where one lands, that moving it is a field update
 * and not pixel work, that nothing is left behind where it was, and that the cells under it keep
 * their text.
 *
 * <p>The JVM has no real bitmaps, so the image is put straight into the store with a stand-in
 * {@link Bitmap} instance; that instance's identity is what proves a placement shares the store's
 * pixels rather than copying them.</p>
 */
public class KittyPlacementLayerTest extends TerminalTestCase {

    private static final int CELL_WIDTH = INITIAL_CELL_WIDTH_PIXELS;
    private static final int CELL_HEIGHT = INITIAL_CELL_HEIGHT_PIXELS;

    private Bitmap mImage;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        withTerminalSized(20, 10);
        mImage = standInBitmap();
        KittyImageStore store = mTerminal.kittyGraphics().store();
        store.reserve(5, 0, 40, 20, 40 * 20 * 4);
        assertTrue(store.complete(5, mImage, 40 * 20 * 4));
    }

    public void testAPlacementAtAPixelOffsetIsOneLayerEntryWithItsExactRectangle() {
        enterString("\033[4;3Habc");
        enterString("\033[4;3H\033_Ga=p,i=5,p=1,X=3,Y=4,c=4,r=1,z=-1,C=1,q=2\033\\");

        List<KittyPlacement> placements = visible();
        assertEquals(1, placements.size());
        KittyPlacement placement = placements.get(0);
        assertSame("the placement draws the store's own bitmap", mImage, placement.getBitmap());
        assertEquals(2, placement.getColumn());
        assertEquals(3, placement.getRow());
        assertEquals(3, placement.getOffsetX());
        assertEquals(4, placement.getOffsetY());
        assertEquals(4 * CELL_WIDTH, placement.getWidth());
        assertEquals(CELL_HEIGHT, placement.getHeight());
        assertEquals(0, placement.getSourceX());
        assertEquals(0, placement.getSourceY());
        assertEquals(40, placement.getSourceWidth());
        assertEquals(20, placement.getSourceHeight());
        assertEquals(-1, placement.getZ());
        assertEquals(CELL_WIDTH, placement.getCellWidth());
        assertEquals(CELL_HEIGHT, placement.getCellHeight());
        // X=3 pushes four cells' worth of picture into a fifth cell, Y=4 into a second row.
        assertEquals(5, placement.getColumns());
        assertEquals(2, placement.getRows());
        // The text under a picture is still there, and no cell became a bitmap cell.
        assertLineIs(3, "  abc               ");
        for (int column = 0; column < 20; column++)
            assertFalse(TextStyle.isBitmap(mTerminal.getScreen().getStyleAt(3, column)));
        assertEquals("C=1 leaves the cursor where it was", 2, mTerminal.getCursorCol());
    }

    public void testReplacingAPlacementMovesItInPlaceWithoutDecodingOrCopying() {
        enterString("\033[2;1H\033_Ga=p,i=5,p=1,C=1,q=2\033\\");
        KittyPlacement first = visible().get(0);
        int decodes = mTerminal.kittyGraphics().decodeCount;
        long generation = mTerminal.getKittyPlacementGeneration();

        enterString("\033[6;4H\033_Ga=p,i=5,p=1,x=10,y=2,w=20,h=12,X=6,Y=1,z=2,C=1,q=2\033\\");

        List<KittyPlacement> placements = visible();
        assertEquals("one placement, not a second one beside the first", 1, placements.size());
        KittyPlacement moved = placements.get(0);
        assertSame("the same placement, updated in place", first, moved);
        assertSame(mImage, moved.getBitmap());
        assertEquals(decodes, mTerminal.kittyGraphics().decodeCount);
        assertTrue(mTerminal.getKittyPlacementGeneration() > generation);
        assertEquals(3, moved.getColumn());
        assertEquals(5, moved.getRow());
        assertEquals(10, moved.getSourceX());
        assertEquals(2, moved.getSourceY());
        assertEquals(20, moved.getSourceWidth());
        assertEquals(12, moved.getSourceHeight());
        assertEquals(20, moved.getWidth());
        assertEquals(12, moved.getHeight());
        assertEquals(6, moved.getOffsetX());
        assertEquals(1, moved.getOffsetY());
        assertEquals(2, moved.getZ());
        // Nothing is left at the old place: its row carries no anchor and nothing reaches it.
        assertFalse(rowAt(1).hasKittyPlacements());
        assertTrue(visibleIn(1, 1).isEmpty());
    }

    public void testManyMovesAcrossRowsLeaveExactlyOnePlacement() {
        for (int step = 0; step < 30; step++) {
            int row = 1 + step % 8;
            enterString("\033[" + row + ";1H\033_Ga=p,i=5,p=7,X=" + (step % CELL_WIDTH)
                + ",Y=" + (step % CELL_HEIGHT) + ",C=1,q=2\033\\");
        }
        assertEquals(1, visible().size());
        assertEquals(1, mTerminal.kittyPlacementsFor(5).size());
    }

    public void testDeletingAPlacementClearsItAndKeepsTheImage() {
        enterString("\033[3;1H\033_Ga=p,i=5,p=1,C=1,q=2\033\\");
        enterString("\033[3;1H\033_Ga=p,i=5,p=2,X=2,C=1,q=2\033\\");
        assertEquals(2, visible().size());
        long generation = mTerminal.getKittyPlacementGeneration();

        enterString("\033_Ga=d,d=i,i=5,p=1,q=2\033\\");
        List<KittyPlacement> placements = visible();
        assertEquals(1, placements.size());
        assertEquals(2, placements.get(0).getPlacementId());
        assertTrue(mTerminal.getKittyPlacementGeneration() > generation);

        enterString("\033_Ga=d,d=i,i=5,q=2\033\\");
        assertTrue(visible().isEmpty());
        assertNotNull("lowercase d keeps the image data", mTerminal.kittyGraphics().store().get(5));
    }

    public void testDeleteByCellHitsAPlacementThatCoversItNotOnlyItsAnchor() {
        enterString("\033[3;2H\033_Ga=p,i=5,p=1,c=3,r=2,C=1,q=2\033\\");
        // Row 4, column 4 (one-based) is the bottom-right cell of the 3x2 placement.
        enterString("\033_Ga=d,d=p,x=4,y=4,q=2\033\\");
        assertTrue(visible().isEmpty());
    }

    public void testWritingTextOverAPictureKeepsThePicture() {
        enterString("\033[2;1H\033_Ga=p,i=5,p=1,c=6,r=2,z=-1,C=1,q=2\033\\");
        enterString("\033[2;1Hhello\r\nworld");
        assertEquals(1, visible().size());
        assertLineIs(1, "hello               ");
    }

    public void testAPlacementScrollsWithItsRowAndIsGoneWhenItsRowIsErased() {
        enterString("\033[2;1H\033_Ga=p,i=5,p=1,C=1,q=2\033\\");
        enterString("\033[10;1H\n\n\n");
        List<KittyPlacement> placements = visibleIn(-3, 13);
        assertEquals(1, placements.size());
        assertEquals("three linefeeds at the bottom moved it up three rows", -2, placements.get(0).getRow());
        // Scrolling far past the transcript erases the anchor row, and the placement with it.
        StringBuilder feeds = new StringBuilder();
        for (int i = 0; i < TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS + 20; i++) feeds.append('\n');
        enterString(feeds.toString());
        assertTrue(mTerminal.kittyPlacementsFor(5).isEmpty());
    }

    public void testThePlacementIsKeptAcrossTheAlternateScreenAndTheAlternateScreenStartsEmpty() {
        enterString("\033[2;1H\033_Ga=p,i=5,p=1,C=1,q=2\033\\");
        enterString("\033[?1049h");
        assertTrue(visible().isEmpty());
        enterString("\033_Ga=p,i=5,p=9,C=1,q=2\033\\");
        assertEquals(1, visible().size());
        enterString("\033[?1049l");
        List<KittyPlacement> placements = visible();
        assertEquals(1, placements.size());
        assertEquals(1, placements.get(0).getPlacementId());
        assertEquals(1, placements.get(0).getRow());
        enterString("\033[?1049h");
        assertTrue("an old alternate-screen picture does not come back", visible().isEmpty());
    }

    public void testEraseDisplayClearsThePicturesOnScreen() {
        enterString("\033[2;1H\033_Ga=p,i=5,p=1,C=1,q=2\033\\");
        enterString("\033[2J");
        assertTrue(visible().isEmpty());
    }

    public void testAResizeThatReflowsKeepsThePicture() {
        enterString("\033[2;1Htext\033[2;1H\033_Ga=p,i=5,p=1,c=4,C=1,q=2\033\\");
        mTerminal.resize(12, 10, CELL_WIDTH, CELL_HEIGHT);
        List<KittyPlacement> placements = mTerminal.kittyPlacementsFor(5);
        assertEquals(1, placements.size());
        assertTrue(placements.get(0).getColumns() <= 12);
    }

    public void testAPlacementOfAStoredImageCostsTheSessionNoBytes() {
        enterString("\033[2;1H\033_Ga=p,i=5,p=1,c=10,r=5,C=1,q=2\033\\");
        assertEquals(0, mTerminal.getScreen().getKittyImageBytes());
    }

    private List<KittyPlacement> visible() {
        return visibleIn(0, mTerminal.mRows);
    }

    private List<KittyPlacement> visibleIn(int topRow, int rows) {
        List<KittyPlacement> out = new ArrayList<>();
        mTerminal.collectKittyPlacements(topRow, rows, out);
        return out;
    }

    private TerminalRow rowAt(int externalRow) {
        TerminalBuffer screen = mTerminal.getScreen();
        return screen.allocateFullLineIfNecessary(screen.externalToInternalRow(externalRow));
    }

    /** A {@link Bitmap} instance without the platform behind it: identity is all these tests need. */
    static Bitmap standInBitmap() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        return (Bitmap) unsafe.getClass().getMethod("allocateInstance", Class.class)
            .invoke(unsafe, Bitmap.class);
    }
}
