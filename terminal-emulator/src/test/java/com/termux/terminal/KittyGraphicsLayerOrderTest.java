package com.termux.terminal;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Deletes and placements reach the placement layer in stream order while a display decode is on
 * the worker: a delete that arrives behind a decode lands with it, in the same posted update, and
 * no longer cancels it. Inside a synchronized update (mode 2026) every layer change waits for the
 * update to end and for every decode it carried, then applies in one step. With neither, a delete
 * still lands at once.
 *
 * <p>The worker is stepped by hand and the decode is stood in for, since the JVM's stub
 * {@link Bitmap} decodes nothing; each stand-in's identity is what tells the frames apart.</p>
 */
public class KittyGraphicsLayerOrderTest extends TerminalTestCase {

    private static final String BEGIN_UPDATE = "\033[?2026h";
    private static final String END_UPDATE = "\033[?2026l";

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

    public void testADeleteWithNothingInFlightStillLandsAtOnce() throws Exception {
        Bitmap first = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(first);
        assertSame(first, placedBitmap(1));

        assertEnteringStringGivesResponse("\033_Ga=d,d=i,i=1\033\\", "\033_Gi=1;OK\033\\");
        assertTrue("nothing was on the worker, so nothing to wait for",
            mTerminal.kittyPlacementsFor(1).isEmpty());
    }

    /** Pattern B: a same-id transmission replaces its own placement when it lands, and not before. */
    public void testASameIdTransmissionReplacesThePictureWhenItLands() throws Exception {
        Bitmap first = KittyPlacementLayerTest.standInBitmap();
        Bitmap second = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(first);

        transmit(1);
        assertSame("the old picture stays while the new one decodes", first, placedBitmap(1));
        landNext(second);
        assertSame(second, placedBitmap(1));
        assertEquals(1, mTerminal.kittyPlacementsFor(1).size());
    }

    /**
     * Pattern B pipelined: the earlier frame's decode lands after the later frame has reserved the
     * id. It must not complete that reservation, or the store would keep the earlier frame's pixels
     * for an image whose newest frame is on screen.
     */
    public void testAnEarlierFrameDoesNotFillALaterFramesReservation() throws Exception {
        Bitmap earlier = KittyPlacementLayerTest.standInBitmap();
        Bitmap later = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        transmit(1);
        landNext(earlier);
        assertSame(earlier, placedBitmap(1));
        assertNull("the newer reservation still waits for its own pixels",
            mTerminal.kittyGraphics().store().get(1).bitmap);
        landNext(later);
        assertSame(later, placedBitmap(1));
        assertSame(later, mTerminal.kittyGraphics().store().get(1).bitmap);
    }

    /**
     * Pattern C, double buffering: the new picture goes up under a second id, then the old id is
     * deleted. The delete used to land at once, leaving nothing on screen until the decode finished.
     */
    public void testDoubleBufferingKeepsTheOldPictureUntilTheNewOneLands() throws Exception {
        Bitmap first = KittyPlacementLayerTest.standInBitmap();
        Bitmap second = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(first);

        transmit(2);
        assertEnteringStringGivesResponse("\033_Ga=d,d=i,i=1\033\\", "\033_Gi=1;OK\033\\");
        assertSame("the delete waits for the decode ahead of it", first, placedBitmap(1));
        assertTrue(mTerminal.kittyPlacementsFor(2).isEmpty());

        landNext(second);
        assertTrue("and lands in the same update as it", mTerminal.kittyPlacementsFor(1).isEmpty());
        assertSame(second, placedBitmap(2));
    }

    /** Pattern C inside a synchronized update: the same, with the update ending in between. */
    public void testDoubleBufferingInsideASynchronizedUpdateHasNoGap() throws Exception {
        Bitmap first = KittyPlacementLayerTest.standInBitmap();
        Bitmap second = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(first);

        enterString(BEGIN_UPDATE);
        transmit(2);
        enterString("\033_Ga=d,d=i,i=1,q=2\033\\");
        enterString(END_UPDATE);
        assertSame(first, placedBitmap(1));
        assertTrue(mTerminal.kittyPlacementsFor(2).isEmpty());

        landNext(second);
        assertTrue(mTerminal.kittyPlacementsFor(1).isEmpty());
        assertSame(second, placedBitmap(2));
    }

    /** A delete used to move the generation on, which threw away every decode still in flight. */
    public void testADeleteOfOneImageNoLongerDropsAnotherImagesDecode() throws Exception {
        Bitmap pixels = KittyPlacementLayerTest.standInBitmap();
        transmit(2);
        enterString("\033_Ga=d,d=i,i=1,q=2\033\\");
        landNext(pixels);
        assertSame(pixels, placedBitmap(2));
    }

    /**
     * Pattern A, terminal-browser's frame: delete the image, retransmit it under the same id, all
     * inside a synchronized update, from an idle terminal. The old picture stays up after the update
     * ends and until the new frame's decode lands, and is replaced in that same step.
     */
    public void testADeleteThenRetransmitInsideASynchronizedUpdateHasNoGap() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        Bitmap next = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        enterString(BEGIN_UPDATE);
        enterString("\033_Ga=d,d=i,i=1,q=2\033\\");
        assertSame("nothing applies inside the update", shown, placedBitmap(1));
        transmit(1);
        enterString(END_UPDATE);
        assertSame("the update ended, but its frame has not decoded yet", shown, placedBitmap(1));

        landNext(next);
        assertSame(next, placedBitmap(1));
    }

    /**
     * Pattern A with the previous frame still decoding when the next update arrives: that frame
     * lands on its own, ahead of the update, and the update's delete lands with its own frame.
     * Freeing ({@code d=I}) must not take the new frame's reservation.
     */
    public void testADeleteThenRetransmitBehindAFrameStillDecodingHasNoGap() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        Bitmap previous = KittyPlacementLayerTest.standInBitmap();
        Bitmap next = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        transmit(1);
        enterString(BEGIN_UPDATE + "\033_Ga=d,d=I,i=1,q=2\033\\");
        transmit(1);
        enterString(END_UPDATE);
        assertSame("the delete did not land ahead of the frame still decoding", shown, placedBitmap(1));

        landNext(previous);
        assertSame("the frame from before the update lands on its own", previous, placedBitmap(1));
        landNext(next);
        assertSame(next, placedBitmap(1));
        assertSame("the frame after the delete keeps its stored image", next,
            mTerminal.kittyGraphics().store().get(1).bitmap);
    }

    /** Pattern D inside a synchronized update: delete everything, then the next frame. */
    public void testADeleteOfEverythingInsideASynchronizedUpdateHasNoGap() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        Bitmap next = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        enterString(BEGIN_UPDATE + "\033_Ga=d,d=A,q=2\033\\");
        transmit(2);
        enterString(END_UPDATE);
        assertSame(shown, placedBitmap(1));

        landNext(next);
        assertTrue(mTerminal.kittyPlacementsFor(1).isEmpty());
        assertSame(next, placedBitmap(2));
        KittyImageStore store = mTerminal.kittyGraphics().store();
        assertNull(store.get(1));
        assertNotNull("transmitted after the delete, so not freed by it", store.get(2));
    }

    /** Pattern D outside an update: the delete waits for the frame ahead of it, end state intact. */
    public void testADeleteOfEverythingBetweenFramesWaitsForTheFrameAheadOfIt() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        Bitmap previous = KittyPlacementLayerTest.standInBitmap();
        Bitmap next = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        transmit(2);
        enterString("\033_Ga=d,d=A,q=2\033\\");
        transmit(3);
        assertSame("the delete did not land ahead of the frame still decoding", shown, placedBitmap(1));

        landNext(previous);
        landNext(next);
        assertTrue(mTerminal.kittyPlacementsFor(1).isEmpty());
        assertTrue(mTerminal.kittyPlacementsFor(2).isEmpty());
        assertSame(next, placedBitmap(3));
        KittyImageStore store = mTerminal.kittyGraphics().store();
        assertNull(store.get(1));
        assertNull(store.get(2));
        assertNotNull("transmitted after the delete, so not freed by it", store.get(3));
    }

    /** An update that carried no decode applies its changes when it ends. */
    public void testAnUpdateWithNothingToDecodeAppliesWhenItEnds() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        enterString(BEGIN_UPDATE + "\033_Ga=d,d=i,i=1,q=2\033\\");
        assertSame(shown, placedBitmap(1));
        enterString(END_UPDATE);
        assertTrue(mTerminal.kittyPlacementsFor(1).isEmpty());
    }

    /** A program that never ends its update still gets its pictures once the hold runs out. */
    public void testAnUpdateThatTimesOutStillApplies() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        Bitmap next = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        enterString(BEGIN_UPDATE + "\033_Ga=d,d=i,i=1,q=2\033\\");
        transmit(1);
        Thread.sleep(TerminalEmulator.SYNCHRONIZED_UPDATE_TIMEOUT_MILLIS + 50);
        // What the session's wake-up asks when the hold's deadline passes.
        assertFalse(mTerminal.isScreenUpdateHeld());
        assertSame(shown, placedBitmap(1));

        landNext(next);
        assertSame(next, placedBitmap(1));
    }

    /**
     * terminal-browser's frame over a slow link: delete, then a store-only transmission in chunks
     * that outlasts the hold, then its placement. The hold running out mid-picture must not apply
     * the delete on its own: the old picture stays until the whole frame lands in one step.
     */
    public void testAFrameStillArrivingWhenTheHoldRunsOutKeepsTheOldPicture() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        Bitmap next = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        enterString(BEGIN_UPDATE + "\033_Ga=d,d=I,i=1,q=2\033\\");
        enterString("\033_Ga=t,f=32,s=1,v=1,i=1,q=2,m=1;AAAA\033\\");
        Thread.sleep(TerminalEmulator.SYNCHRONIZED_UPDATE_TIMEOUT_MILLIS + 50);
        assertFalse("the hold ran out mid-picture", mTerminal.isScreenUpdateHeld());
        assertSame(shown, placedBitmap(1));

        enterString("\033_Gm=0;AA==\033\\");
        enterString("\033_Ga=p,i=1,C=1,q=2\033\\" + END_UPDATE);
        assertSame(shown, placedBitmap(1));
        landNext(next);
        assertSame("the frame lands whole, with its placement", shown, placedBitmap(1));
        mWorker.remove(0).run();
        mOutput.drainTerminalUpdates();
        assertSame(next, placedBitmap(1));
    }

    /** A picture that stalls holds the window only for the grace period, then the update applies. */
    public void testAStalledPictureHoldsTheWindowOnlyForItsGrace() throws Exception {
        mTerminal.kittyGraphics().windowUploadGraceMillis = 1;
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        enterString(BEGIN_UPDATE + "\033_Ga=d,d=i,i=1,q=2\033\\");
        enterString("\033_Ga=t,f=32,s=1,v=1,i=2,q=2,m=1;AAAA\033\\");
        Thread.sleep(TerminalEmulator.SYNCHRONIZED_UPDATE_TIMEOUT_MILLIS + 50);
        assertFalse(mTerminal.isScreenUpdateHeld());
        assertTrue("past its grace the delete applied", mTerminal.kittyPlacementsFor(1).isEmpty());
    }

    /** The next update seals a window still open past its hold, so two frames never merge. */
    public void testTheNextUpdateSealsAWindowKeptOpenPastItsHold() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        enterString(BEGIN_UPDATE + "\033_Ga=d,d=i,i=1,q=2\033\\");
        enterString("\033_Ga=t,f=32,s=1,v=1,i=2,q=2,m=1;AAAA\033\\");
        Thread.sleep(TerminalEmulator.SYNCHRONIZED_UPDATE_TIMEOUT_MILLIS + 50);
        assertFalse(mTerminal.isScreenUpdateHeld());
        assertSame("kept open while the picture arrives", shown, placedBitmap(1));

        enterString(BEGIN_UPDATE);
        assertTrue("the old window sealed and its delete applied",
            mTerminal.kittyPlacementsFor(1).isEmpty());
    }

    /** A decode that fails still counts as back: the rest of its update applies. */
    public void testAFailedDecodeDoesNotHoldItsUpdateBack() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        enterString(BEGIN_UPDATE + "\033_Ga=d,d=i,i=1,q=2\033\\");
        transmit(1);
        enterString(END_UPDATE);
        assertSame(shown, placedBitmap(1));

        landNext(null);
        assertTrue("the delete applied", mTerminal.kittyPlacementsFor(1).isEmpty());
    }

    /**
     * Leaving the screen a queued delete was addressed to lands it first, on that screen: the decode
     * ahead of it is dropped by the switch, so waiting would leave the old picture behind.
     */
    public void testAScreenSwitchLandsAQueuedDeleteOnTheScreenItWasFor() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        transmit(2);
        enterString("\033_Ga=d,d=i,i=1,q=2\033\\");
        enterString("\033[?1049h");
        assertTrue(mTerminal.kittyPlacementsFor(1).isEmpty());
        enterString("\033[?1049l");
        landNext(KittyPlacementLayerTest.standInBitmap());
        assertTrue("a decode from before the switch is still dropped",
            mTerminal.kittyPlacementsFor(2).isEmpty());
    }

    /** A display transmission of one 1x1 raw pixel at the cursor, leaving the cursor where it is. */
    private void transmit(long id) {
        // o=z defers the payload check to the worker, where the stand-in decode replaces it.
        enterString("\033_Ga=T,f=32,o=z,s=1,v=1,C=1,q=2,i=" + id + ";AAAA\033\\");
    }

    /**
     * Run the oldest decode on the worker with {@code pixels} as its result (null fails it), then
     * post it back.
     */
    private void landNext(Bitmap pixels) {
        mNextDecode = pixels;
        mWorker.remove(0).run();
        mOutput.drainTerminalUpdates();
    }

    private Bitmap placedBitmap(long id) {
        List<KittyPlacement> placements = mTerminal.kittyPlacementsFor(id);
        assertEquals("one placement of image " + id, 1, placements.size());
        return placements.get(0).getBitmap();
    }
}
