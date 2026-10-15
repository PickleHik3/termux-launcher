package com.termux.terminal;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Video through Unicode placeholders: the program keeps its placeholder cells on screen and
 * retransmits the image behind one id a dozen times a second ({@code a=T,U=1,i=N}, no delete in
 * between — Claude Code's Image element does exactly this). Every draw between a retransmission
 * and its decode landing must still find a picture for the id, or the cells blank at the frame
 * rate. The worker is stepped by hand and each decode stood in for, as in
 * {@link KittyGraphicsLayerOrderTest}; a stand-in's identity is what tells the frames apart.
 */
public class KittyPlaceholderRetransmitTest extends TerminalTestCase {

    private static final String BEGIN_UPDATE = "\033[?2026h";
    private static final String END_UPDATE = "\033[?2026l";
    private static final long ID = 7;

    private final List<Runnable> mWorker = new ArrayList<>();
    private Bitmap mNextDecode;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        withTerminalSized(20, 10);
        KittyGraphicsProtocol kitty = mTerminal.kittyGraphics();
        kitty.decoder = mWorker::add;
        kitty.decodeForTests = () -> mNextDecode;
    }

    public void testARetransmittedFrameKeepsThePreviousPictureUntilItsOwnLands() throws Exception {
        Bitmap first = KittyPlacementLayerTest.standInBitmap();
        Bitmap second = KittyPlacementLayerTest.standInBitmap();
        transmit();
        landNext(first);
        assertSame(first, drawn());

        transmit();
        assertSame("the draw between the transmission and its decode keeps the old frame",
            first, drawn());
        landNext(second);
        assertSame(second, drawn());
        assertNull("the stand-in goes in the step that commits the new frame",
            store().get(ID).standIn);
    }

    public void testManyFramesInARowNeverLeaveTheIdWithoutAPicture() throws Exception {
        transmit();
        landNext(KittyPlacementLayerTest.standInBitmap());
        for (int frame = 0; frame < 24; frame++) {
            Bitmap next = KittyPlacementLayerTest.standInBitmap();
            transmit();
            assertNotNull("frame " + frame + " decoding", drawn());
            landNext(next);
            assertSame(next, drawn());
        }
    }

    /**
     * A sender that outpaces the decoder replaces every frame before it lands. The late frame is
     * still newer than what is on screen, so it shows until the newest one lands, rather than
     * being thrown away and freezing the picture.
     */
    public void testAFrameOvertakenByTheNextStillShowsUntilTheNextLands() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        Bitmap overtaken = KittyPlacementLayerTest.standInBitmap();
        Bitmap newest = KittyPlacementLayerTest.standInBitmap();
        transmit();
        landNext(shown);

        transmit();
        transmit();
        assertSame(shown, drawn());
        landNext(overtaken);
        assertSame(overtaken, drawn());
        landNext(newest);
        assertSame(newest, drawn());
        assertSame(newest, store().get(ID).bitmap);
    }

    /** Delete, then the next frame under the same id, in one synchronized update: a replacement. */
    public void testADeleteThenRetransmitInsideASynchronizedUpdateHasNoGap() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        Bitmap next = KittyPlacementLayerTest.standInBitmap();
        transmit();
        landNext(shown);

        enterString(BEGIN_UPDATE + "\033_Ga=d,d=I,i=" + ID + ",q=2\033\\");
        transmit();
        enterString(END_UPDATE);
        assertSame("the update ended, but its frame has not decoded yet", shown, drawn());

        landNext(next);
        assertSame(next, drawn());
    }

    /** A delete the screen has shown is not undone by the next transmission under that id. */
    public void testADeleteOutsideAnUpdateIsNotUndoneByTheNextTransmission() throws Exception {
        transmit();
        landNext(KittyPlacementLayerTest.standInBitmap());

        enterString("\033_Ga=d,d=I,i=" + ID + ",q=2\033\\");
        assertNull(drawn());
        transmit();
        assertNull("the deleted picture does not come back while the new one decodes", drawn());
        Bitmap next = KittyPlacementLayerTest.standInBitmap();
        landNext(next);
        assertSame(next, drawn());
    }

    /** Nor does a held picture outlive the update it was deleted in. */
    public void testAPictureDeletedInAnEarlierUpdateDoesNotStandInLater() throws Exception {
        transmit();
        landNext(KittyPlacementLayerTest.standInBitmap());

        enterString(BEGIN_UPDATE + "\033_Ga=d,d=I,i=" + ID + ",q=2\033\\" + END_UPDATE);
        assertNull(drawn());
        transmit();
        assertNull(drawn());
    }

    /** A delete while a frame decodes clears the screen now and stays cleared when it lands. */
    public void testADeleteWhileAFrameDecodesTakesThePictureDown() throws Exception {
        transmit();
        landNext(KittyPlacementLayerTest.standInBitmap());

        transmit();
        enterString("\033_Ga=d,d=i,i=" + ID + ",q=2\033\\");
        assertNull(drawn());
        assertNull("the stand-in is released with the placements", store().get(ID).standIn);
        landNext(KittyPlacementLayerTest.standInBitmap());
        assertNull(drawn());
    }

    /** A frame that fails to decode takes its stand-in with it: the id is gone, kitty's answer. */
    public void testAFailedFrameReleasesItsStandIn() throws Exception {
        transmit();
        landNext(KittyPlacementLayerTest.standInBitmap());

        transmit();
        landNext(null);
        assertNull(store().get(ID));
        assertEquals(0, store().totalBytes());
    }

    /**
     * The stand-in is the one frame per id in flight, charged while held and released on commit,
     * on delete and on replacement, and it never costs a transmission its place.
     */
    public void testTheStandInIsChargedOnceAndOnlyFromHeadroom() throws Exception {
        KittyImageStore store = new KittyImageStore(new KittyImageStore.FrameBudget());
        place(store, 1, 400);
        assertEquals(400, store.totalBytes());

        store.reserve(1, 0, 10, 10, 400);
        store.putVirtualPlacement(store.get(1), placement());
        assertEquals("the new frame's estimate plus the frame it replaces", 800, store.totalBytes());
        store.reserve(1, 0, 10, 10, 400);
        store.putVirtualPlacement(store.get(1), placement());
        assertEquals("an id carries one stand-in however many frames are pending",
            800, store.totalBytes());
        assertTrue(store.complete(1, KittyPlacementLayerTest.standInBitmap(), 400));
        assertEquals(400, store.totalBytes());

        store.reserve(1, 0, 10, 10, 400);
        store.remove(1);
        assertEquals(0, store.totalBytes());

        int big = (int) (KittyImageStore.MAX_STORED_BYTES * 3 / 5);
        place(store, 2, big);
        assertFalse(store.wouldExceedLimits(2, big));
        store.reserve(2, 0, 10, 10, big);
        assertNull("no headroom for the old frame, so the new one is still accepted",
            store.get(2).standIn);
        assertEquals(big, store.totalBytes());
    }

    private static void place(KittyImageStore store, long id, int bytes) throws Exception {
        store.reserve(id, 0, 10, 10, bytes);
        store.putVirtualPlacement(store.get(id), placement());
        assertTrue(store.complete(id, KittyPlacementLayerTest.standInBitmap(), bytes));
    }

    private static KittyImageStore.VirtualPlacement placement() {
        return new KittyImageStore.VirtualPlacement(0, 0, 0, 10, 10, 2, 1);
    }

    /** The bytes Claude Code sends per frame, as raw pixels so the JVM needs no PNG decoder. */
    private void transmit() {
        // o=z defers the payload check to the worker, where the stand-in decode replaces it.
        enterString("\033_Ga=T,U=1,q=2,f=32,o=z,s=1,v=1,c=2,r=1,i=" + ID + ";AAAA\033\\");
    }

    private void landNext(Bitmap pixels) {
        mNextDecode = pixels;
        mWorker.remove(0).run();
        mOutput.drainTerminalUpdates();
    }

    /** What a placeholder cell for the id draws right now, or null when it draws nothing. */
    private Bitmap drawn() {
        KittyImagePlaceholder out = new KittyImagePlaceholder();
        return mTerminal.getKittyImagePlaceholder(ID, 0, out) ? out.bitmap : null;
    }

    private KittyImageStore store() {
        return mTerminal.kittyGraphics().store();
    }
}
