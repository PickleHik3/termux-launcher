package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Live blur's budget order: the dock and the panels, then the floats, then the widgets. A full
 * budget gives a better surface the newest place of the worst rank below it; a widget past the
 * budget keeps its stored frame.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU)
public class LiveBackdropRankTest {

    @Test
    public void evictIndexTakesTheNewestOfTheWorstRankBelow() {
        int c = LiveBackdrop.RANK_CHROME, f = LiveBackdrop.RANK_FLOAT, w = LiveBackdrop.RANK_WIDGET;
        assertEquals(3, LiveBackdrop.evictIndex(new int[] {c, w, f, w, f}, 5, c));
        assertEquals(3, LiveBackdrop.evictIndex(new int[] {c, w, f, w, f}, 5, f));
        assertEquals(-1, LiveBackdrop.evictIndex(new int[] {c, w, f, w, f}, 5, w));
        assertEquals(2, LiveBackdrop.evictIndex(new int[] {c, f, f}, 3, c));
        assertEquals(-1, LiveBackdrop.evictIndex(new int[] {c, f, f}, 3, f));
        assertEquals(-1, LiveBackdrop.evictIndex(new int[] {c, c}, 2, c));
        assertEquals(-1, LiveBackdrop.evictIndex(new int[0], 0, c));
    }

    @Test
    public void theOrderHoldsWithMoreCandidatesThanPlaces() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout root = new FrameLayout(context);
        LiveBackdrop recorder = new LiveBackdrop(root);
        int max = LiveBackdrop.MAX_LIVE_SURFACES;
        View[] widgets = new View[max + 2];
        LivePicture[] widgetPictures = new LivePicture[widgets.length];
        for (int i = 0; i < widgets.length; i++) {
            widgets[i] = add(root, context);
            widgetPictures[i] = recorder.attach(widgets[i], 20f, LiveBackdrop.RANK_WIDGET);
        }
        for (int i = 0; i < max; i++) assertNotNull("widget " + i, widgetPictures[i]);
        assertNull("past the budget a widget keeps its stored frame", widgetPictures[max]);
        assertNull(widgetPictures[max + 1]);

        // The dock arrives: the newest widget gives its place.
        View dock = add(root, context);
        assertNotNull(recorder.attach(dock, 20f));
        assertTrue(widgetPictures[max - 1].released());
        assertFalse(widgetPictures[max - 2].released());

        // Three floats: three more widgets, newest first.
        LivePicture[] floatPictures = new LivePicture[3];
        for (int i = 0; i < floatPictures.length; i++) {
            floatPictures[i] = recorder.attach(add(root, context), 20f, LiveBackdrop.RANK_FLOAT);
            assertNotNull("float " + i, floatPictures[i]);
        }
        for (int i = max - 4; i < max; i++) assertTrue("widget " + i, widgetPictures[i].released());
        for (int i = 0; i < max - 4; i++) assertFalse("widget " + i, widgetPictures[i].released());
        assertEquals(max, recorder.size());

        // A displaced widget asking again gets nothing; more floats take every widget's place,
        // and past that a float gets nothing either.
        assertNull(recorder.attach(widgets[max - 1], 20f, LiveBackdrop.RANK_WIDGET));
        int floatsLive = floatPictures.length;
        for (int i = 0; i < max; i++) {
            if (recorder.attach(add(root, context), 20f, LiveBackdrop.RANK_FLOAT) != null)
                floatsLive++;
        }
        assertEquals("every widget gave way to a float", max - 1, floatsLive);
        for (LivePicture picture : widgetPictures) {
            if (picture != null) assertTrue(picture.released());
        }
        for (LivePicture picture : floatPictures) assertFalse(picture.released());
        assertEquals(max, recorder.size());
    }

    private static View add(FrameLayout root, Context context) {
        View view = new View(context);
        root.addView(view);
        return view;
    }
}
