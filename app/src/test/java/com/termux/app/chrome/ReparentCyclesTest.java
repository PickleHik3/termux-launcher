package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

/**
 * Home's two page grids trade cells mid-swipe by detach and attach (pong 2026-11-23: a
 * RenderThread stack overflow in the first frame after the trade). A card's picture recorded the
 * grid beneath it while the card stood in the neighbour layer above; once the card is in that
 * grid, the recording draws the grid that draws the card: a loop. Such recordings drop at the
 * trade; the others stay.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Application.class)
public class ReparentCyclesTest {

    @Test
    public void selfLoopsAndRingsAreOnACycleChainsAreNot() {
        boolean[][] edge = new boolean[4][4];
        boolean[] out = new boolean[4];
        ReparentCycles.onCycle(edge, 4, out);
        for (boolean b : out) assertFalse(b);

        edge[0][0] = true;                 // a picture drawing its own holder
        edge[1][2] = true;                 // 1 -> 2 -> 1: a ring through two pictures
        edge[2][1] = true;
        edge[3][1] = true;                 // 3 draws into the ring but nothing draws 3
        ReparentCycles.onCycle(edge, 4, out);
        assertTrue(out[0]);
        assertTrue(out[1]);
        assertTrue(out[2]);
        assertFalse(out[3]);
    }

    @Test
    public void holdsWalksUpTheParentsOnly() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout grid = new FrameLayout(context);
        FrameLayout cell = new FrameLayout(context);
        View slab = new View(context);
        grid.addView(cell);
        cell.addView(slab);
        assertTrue(ReparentCycles.holds(grid, slab));
        assertTrue(ReparentCycles.holds(slab, slab));
        assertFalse(ReparentCycles.holds(slab, grid));
        assertFalse(ReparentCycles.holds(null, slab));
    }

    @Test
    public void aCardMovedIntoTheGridItsPictureRecordedDropsThatRecordingOnly() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout root = new FrameLayout(context);
        FrameLayout grid = new FrameLayout(context);
        FrameLayout neighbour = new FrameLayout(context);
        root.addView(grid);
        root.addView(neighbour);
        FrameLayout movedCard = new FrameLayout(context);
        View movedSlab = new View(context);
        movedCard.addView(movedSlab);
        neighbour.addView(movedCard);
        FrameLayout stayingCard = new FrameLayout(context);
        View stayingSlab = new View(context);
        stayingCard.addView(stayingSlab);
        neighbour.addView(stayingCard);

        LiveBackdrop backdrop = new LiveBackdrop(root);
        LivePicture moved = backdrop.attach(movedSlab, 40f);
        LivePicture staying = backdrop.attach(stayingSlab, 40f);
        moved.drawable().setCallback(movedSlab);
        staying.drawable().setCallback(stayingSlab);
        // Both recorded the grid beneath the neighbour layer, as the cards stood above it.
        moved.mRecordedLocal.add(grid);
        moved.mRecordHoldsLocal = true;
        moved.recorded();
        staying.mRecordedLocal.add(grid);
        staying.mRecordHoldsLocal = true;
        staying.recorded();
        assertTrue(moved.ready());

        // The trade: the card leaves the neighbour for the grid by detach and attach.
        neighbour.removeView(movedCard);
        grid.addView(movedCard);
        int dropped = backdrop.reparented(Collections.singletonList(movedCard), neighbour);

        assertEquals(1, dropped);
        assertFalse("the looping recording is dropped", moved.ready());
        assertTrue(moved.mRecordedLocal.isEmpty());
        assertTrue("a recording off the loop stays", staying.ready());
        assertEquals(1, staying.mRecordedLocal.size());
    }

    @Test
    public void aRecordingOfTheOldParentStillDrawsTheCellUntilThatParentDrawsAgain() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout root = new FrameLayout(context);
        FrameLayout grid = new FrameLayout(context);
        FrameLayout neighbour = new FrameLayout(context);
        root.addView(grid);
        root.addView(neighbour);
        FrameLayout card = new FrameLayout(context);
        View slab = new View(context);
        card.addView(slab);
        grid.addView(card);

        LiveBackdrop backdrop = new LiveBackdrop(root);
        LivePicture picture = backdrop.attach(slab, 40f);
        picture.drawable().setCallback(slab);
        // Recorded the grid the card is about to leave; after the move the grid's display list
        // still draws the card until it draws again.
        picture.mRecordedBeneath.add(grid);
        picture.recorded();

        grid.removeView(card);
        neighbour.addView(card);
        assertEquals(1, backdrop.reparented(Collections.singletonList(card), grid));
        assertFalse(picture.ready());
    }
}
