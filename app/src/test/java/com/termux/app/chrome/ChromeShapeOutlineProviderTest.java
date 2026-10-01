package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

/**
 * A chrome view that draws no background of its own is not asked for a new outline when a layout
 * pass resizes it, so the provider has to follow the size: the status bar's host kept the outline
 * of its Floating width after the Style went back to Docked.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ChromeShapeOutlineProviderTest {

    /** A view that counts the outline rebuilds it is asked for. */
    private static final class CountingView extends View {
        int rebuilds;

        CountingView(Context context) {
            super(context);
        }

        @Override
        public void invalidateOutline() {
            rebuilds++;
            super.invalidateOutline();
        }
    }

    private static CountingView view() {
        return new CountingView(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void aResizeRebuildsTheOutlineOfAFollowedView() {
        ChromeShapeOutlineProvider provider = new ChromeShapeOutlineProvider();
        CountingView host = view();
        host.setOutlineProvider(provider);
        provider.follow(host);

        host.layout(13, 0, 1067, 96);
        int afterFirst = host.rebuilds;
        assertTrue("the first layout sizes the view", afterFirst > 0);

        host.layout(0, 0, 1080, 96);
        assertTrue("the Docked width is a new size: the outline is built again for it",
            host.rebuilds > afterFirst);
    }

    @Test
    public void aLayoutThatKeepsTheSizeLeavesTheOutlineAlone() {
        ChromeShapeOutlineProvider provider = new ChromeShapeOutlineProvider();
        CountingView host = view();
        provider.follow(host);
        host.layout(0, 0, 1080, 96);
        int sized = host.rebuilds;

        host.layout(0, 40, 1080, 136);
        assertEquals("a move is not a resize", sized, host.rebuilds);
    }

    @Test
    public void followingTwiceListensOnce() {
        ChromeShapeOutlineProvider provider = new ChromeShapeOutlineProvider();
        CountingView host = view();
        provider.follow(host);
        provider.follow(host);
        host.layout(0, 0, 1080, 96);
        assertEquals(1, host.rebuilds);
    }

    @Test
    public void theCardCoversTheViewsLiveBoundsGrownByTheClipsReach() {
        int[] rect = new int[4];
        ChromeShapeOutlineProvider.cardRect(null, 1080, 96, rect);
        assertEquals(1080, rect[2]);
        assertEquals(96, rect[3]);

        // Floating: the card is the view's own bounds, whatever size the view has now.
        LiveChromeShape.Clip card = LiveChromeShape.cardClip(26f);
        ChromeShapeOutlineProvider.cardRect(card, 1054, 96, rect);
        assertEquals(1054, rect[2]);
        ChromeShapeOutlineProvider.cardRect(card, 1080, 96, rect);
        assertEquals("a view that grew has an outline that grew with it", 1080, rect[2]);
    }
}
