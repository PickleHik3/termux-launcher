package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.GlassAnchor;
import com.termux.app.dock.SideRowViews;
import com.termux.app.place.EdgeStackView;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.StackLanes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The stack's writer while Home's card and the Terminal's extra-keys card stand apart (SideCards):
 * the glass host is Home's card alone, so it takes no shift, slides with Home's lane, and stands
 * Home's drop lower with Home's other layers and lane rows; the plank tilts the Terminal's card
 * while the Terminal is in front. Not apart, every one of those is as it was.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StackRendererApartTest {

    private static final int WIDTH = 1080;
    private static final float DROP = 16f;

    private final SparseArray<View> mViews = new SparseArray<>();
    private boolean mFrontHome;
    private boolean mKeyboardShown;
    private StackRenderer mRenderer;
    private View mHost;
    private View mUnderlay;
    private View mHomeRow;
    private View mKeysRow;
    private View mSharedRow;
    private View mKeysCard;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout container = new FrameLayout(context);
        EdgeStackView rowStack = new EdgeStackView(context);
        rowStack.setEdge(Edge.BOTTOM);
        mSharedRow = new FrameLayout(context);
        mHomeRow = new FrameLayout(context);
        mKeysRow = new FrameLayout(context);
        mSharedRow.setId(SideRowViews.id(Element.APPS, SideRowViews.Part.BOTTOM_HOST));
        mHomeRow.setId(SideRowViews.id(Element.AZ, SideRowViews.Part.BOTTOM_HOST));
        mKeysRow.setId(SideRowViews.id(Element.EXTRA_KEYS, SideRowViews.Part.BOTTOM_HOST));
        rowStack.addView(mSharedRow);
        rowStack.addView(mHomeRow);
        rowStack.addView(mKeysRow);
        rowStack.setLane(mHomeRow, StackLanes.HOME);
        rowStack.setLane(mKeysRow, StackLanes.TERMINAL);
        container.addView(rowStack);
        container.layout(0, 0, WIDTH, 600);
        mHost = new FrameLayout(context);
        mUnderlay = new FrameLayout(context);
        mKeysCard = new FrameLayout(context);
        mViews.put(R.id.accessory_stack_container, container);
        mViews.put(R.id.accessory_row_stack, rowStack);
        mViews.put(R.id.accessory_surface_host, mHost);
        mViews.put(R.id.apps_bar_az_fx_underlay, mUnderlay);
        mViews.put(R.id.extra_keys_card, mKeysCard);
        mViews.put(mSharedRow.getId(), mSharedRow);
        mViews.put(mHomeRow.getId(), mHomeRow);
        mViews.put(mKeysRow.getId(), mKeysRow);
        mRenderer = new StackRenderer(new StackRenderer.Views() {
            @Nullable @Override public View view(int id) { return mViews.get(id); }
            @Override public int keyboardDesiredHeightPx() { return 0; }
            @Override public boolean underKeyboardBandsShown() { return false; }
            @Override public boolean keyboardShown() { return mKeyboardShown; }
            @Override public boolean frontIsHome() { return mFrontHome; }
        });
    }

    @Test public void apartAtHomesRestTheHostStandsAtHomesDropAndSlideZero() {
        mFrontHome = true;
        mRenderer.setSidesApart(true, DROP);
        mRenderer.apply();
        assertEquals(0f, mHost.getTranslationX(), 0f);
        assertEquals(DROP, mHost.getTranslationY(), 0f);
        assertEquals(DROP, mUnderlay.getTranslationY(), 0f);
        assertEquals("Home's lane rows stand the drop lower", DROP, mHomeRow.getTranslationY(), 0f);
        assertEquals(0f, mKeysRow.getTranslationY(), 0f);
        assertEquals("the shared row keeps its place", 0f, mSharedRow.getTranslationY(), 0f);
        assertEquals(0f, mRenderer.hostShiftPx(), 0f);
    }

    @Test public void apartAtTheTerminalsRestTheHostIsParkedAWallToTheLeft() {
        mFrontHome = false;
        mRenderer.setSidesApart(true, DROP);
        mRenderer.apply();
        assertEquals(-WIDTH, mHost.getTranslationX(), 0f);
        assertEquals(DROP, mHost.getTranslationY(), 0f);
        assertEquals(0f, mRenderer.hostShiftPx(), 0f);
    }

    @Test public void apartMidCrossingTheHostFollowsHomesSlideAndTheDropStays() {
        mFrontHome = true;
        mRenderer.setSidesApart(true, DROP);
        mRenderer.apply();
        mRenderer.setEnds(true, false, 0.25f);
        assertEquals(-0.25f * WIDTH, mHost.getTranslationX(), 0.01f);
        assertEquals(DROP, mHost.getTranslationY(), 0f);
        assertEquals("no shift while apart", 0f, mRenderer.hostShiftPx(), 0f);
        assertEquals(-0.25f * WIDTH, mHomeRow.getTranslationX(), 0.01f);
        mRenderer.setEnds(true, false, 0.75f);
        assertEquals(-0.75f * WIDTH, mHost.getTranslationX(), 0.01f);
        mRenderer.setEnds(false, true, 0.25f);
        assertEquals("back toward Home: Home's slide runs the other way",
            -0.75f * WIDTH, mHost.getTranslationX(), 0.01f);
        assertEquals(DROP, mHost.getTranslationY(), 0f);
        mFrontHome = true;
        mRenderer.onLanding();
        mRenderer.apply();
        assertEquals(0f, mHost.getTranslationX(), 0f);
    }

    @Test public void aHomeToHomeCrossingMovesNothingNew() {
        mFrontHome = true;
        mRenderer.setSidesApart(true, DROP);
        mRenderer.apply();
        int writes = mRenderer.mWriteCount;
        mRenderer.setEnds(true, true, 0.5f);
        assertEquals(0f, mHost.getTranslationX(), 0f);
        assertEquals(writes, mRenderer.mWriteCount);
    }

    @Test public void aFrameEqualToTheLastWritesNothing() {
        mFrontHome = true;
        mRenderer.setSidesApart(true, DROP);
        mRenderer.apply();
        int writes = mRenderer.mWriteCount;
        mRenderer.apply();
        mRenderer.setSidesApart(true, DROP);
        mRenderer.apply();
        assertEquals(writes, mRenderer.mWriteCount);
    }

    @Test public void notApartTheHostAndTheLanesAreAsTheyWere() {
        mFrontHome = true;
        mRenderer.setSidesApart(false, DROP);
        mRenderer.apply();
        mRenderer.setEnds(true, false, 0.5f);
        assertEquals(0f, mHost.getTranslationX(), 0f);
        assertEquals(0f, mHost.getTranslationY(), 0f);
        assertEquals(0f, mUnderlay.getTranslationY(), 0f);
        assertEquals(0f, mHomeRow.getTranslationY(), 0f);
        assertNull("no offset is published to the glass anchor", motionTag(mHost));
        assertEquals(0f, GlassAnchor.motionX(mHost), 0f);
    }

    @Test public void apartPublishesTheHostsOffsetToItsAnchorAndEndingItWithdrawsIt() {
        mFrontHome = false;
        mRenderer.setSidesApart(true, DROP);
        mRenderer.apply();
        assertEquals(-WIDTH, GlassAnchor.motionX(mHost), 0f);
        assertEquals(DROP, GlassAnchor.motionY(mHost), 0f);
        mRenderer.setSidesApart(false, 0f);
        mRenderer.apply();
        assertEquals(0f, GlassAnchor.motionX(mHost), 0f);
        assertEquals(0f, GlassAnchor.motionY(mHost), 0f);
        assertEquals(0f, mHost.getTranslationX(), 0f);
        assertEquals(0f, mHost.getTranslationY(), 0f);
    }

    @Test public void theKeyboardsPlankTiltsTheTerminalsCardWhileApartAndTheTerminalIsInFront() {
        mKeyboardShown = true;
        DockPlankController.Slab slab = new DockPlankController.Slab();
        slab.translationY = 5f;
        mFrontHome = false;
        mRenderer.setSidesApart(true, DROP);
        mRenderer.onSlab(slab);
        mRenderer.apply();
        assertEquals("the card dips with the plank", 5f, mKeysCard.getTranslationY(), 0f);
        assertEquals("the parked host does not", DROP, mHost.getTranslationY(), 0f);
        assertEquals(mKeysCard, mRenderer.plankTargetView());
        mFrontHome = true;
        mRenderer.apply();
        assertEquals("Home in front: the host tilts", DROP + 5f, mHost.getTranslationY(), 0f);
        assertEquals(0f, mKeysCard.getTranslationY(), 0f);
        assertEquals(mHost, mRenderer.plankTargetView());
    }

    @Test public void notApartTheKeyboardsPlankTiltsTheHostWhicheverSideIsInFront() {
        mKeyboardShown = true;
        DockPlankController.Slab slab = new DockPlankController.Slab();
        slab.translationY = 5f;
        mFrontHome = false;
        mRenderer.setSidesApart(false, 0f);
        mRenderer.onSlab(slab);
        mRenderer.apply();
        assertEquals(5f, mHost.getTranslationY(), 0f);
        assertEquals(0f, mKeysCard.getTranslationY(), 0f);
        assertEquals(mHost, mRenderer.plankTargetView());
    }

    @Nullable
    private static Object motionTag(View view) {
        return view.getTag(R.id.glass_anchor_motion);
    }
}
