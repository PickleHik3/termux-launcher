package com.termux.app;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.dock.SideRowViews;
import com.termux.app.place.EdgeStackView;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.StackLanes;
import com.termux.app.window.Counters;
import com.termux.app.window.FrameGuards;
import com.termux.app.window.FramePipeline;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The stack's writer over the side lanes (D1-A step 2): at rest the front side's lane stands at 0
 * and writes nothing, the other's stands off its page's side; in a crossing each lane slides with
 * its page and the shared rows stay; the crossing's last frame is the arriving rest, so a landing
 * writes nothing and counts no landing-wrote.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StackRendererLanesTest {

    private static final int WIDTH = 1080;

    private static final class Counted extends FrameLayout {
        int writes;
        Counted(Context context) {
            super(context);
        }
        @Override public void setTranslationY(float y) { writes++; super.setTranslationY(y); }
        @Override public void setTranslationX(float x) { writes++; super.setTranslationX(x); }
        @Override public void setClipBounds(Rect clip) { writes++; super.setClipBounds(clip); }
    }

    private final SparseArray<View> mViews = new SparseArray<>();
    private final java.util.List<View> mMoved = new java.util.ArrayList<>();
    private boolean mFrontHome;
    private StackRenderer mRenderer;
    private Counted mContainer;
    private EdgeStackView mRowStack;
    private Counted mApps;
    private Counted mKeys;
    private Counted mHomeRow;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        mContainer = new Counted(context);
        mRowStack = new EdgeStackView(context);
        mRowStack.setEdge(Edge.BOTTOM);
        mApps = new Counted(context);
        mKeys = new Counted(context);
        mHomeRow = new Counted(context);
        mApps.setId(SideRowViews.id(Element.APPS, SideRowViews.Part.BOTTOM_HOST));
        mHomeRow.setId(SideRowViews.id(Element.AZ, SideRowViews.Part.BOTTOM_HOST));
        mKeys.setId(SideRowViews.id(Element.EXTRA_KEYS, SideRowViews.Part.BOTTOM_HOST));
        mRowStack.addView(mApps);
        mRowStack.addView(mHomeRow);
        mRowStack.addView(mKeys);
        mRowStack.setLane(mHomeRow, StackLanes.HOME);
        mRowStack.setLane(mKeys, StackLanes.WORKSPACE);
        mContainer.addView(mRowStack);
        mContainer.layout(0, 0, WIDTH, 600);
        mViews.put(R.id.accessory_stack_container, mContainer);
        mViews.put(R.id.accessory_row_stack, mRowStack);
        mViews.put(mApps.getId(), mApps);
        mViews.put(mHomeRow.getId(), mHomeRow);
        mViews.put(mKeys.getId(), mKeys);
        mRenderer = new StackRenderer(new StackRenderer.Views() {
            @Nullable @Override public View view(int id) { return mViews.get(id); }
            @Override public int keyboardDesiredHeightPx() { return 0; }
            @Override public boolean underKeyboardBandsShown() { return false; }
            @Override public boolean keyboardShown() { return false; }
            @Override public boolean frontIsHome() { return mFrontHome; }
            @Override public void laneMoved(View host) { mMoved.add(host); }
        });
        Counters.clear();
        Counters.enable(true, null);
    }

    @After public void tearDown() {
        Counters.enable(false, null);
        Counters.clear();
    }

    private int laneWrites() {
        return mApps.writes + mKeys.writes + mHomeRow.writes;
    }

    @Test public void onTheWorkspaceItsLaneStandsAtZeroAndHomesIsOffToTheLeft() {
        mFrontHome = false;
        mRenderer.apply();
        assertEquals(0f, mKeys.getTranslationX(), 0f);
        assertEquals(-WIDTH, mHomeRow.getTranslationX(), 0f);
        assertEquals("the shared row never slides", 0f, mApps.getTranslationX(), 0f);
        assertEquals("only Home's lane needed a write", 1, laneWrites());
        mRenderer.apply();
        mRenderer.applyAtRest();
        assertEquals("a frame equal to the last writes nothing", 1, laneWrites());
    }

    @Test public void aLaneWriteReachesWhateverDrawsTheRowFromOutsideTheHost() {
        mFrontHome = true;
        mRenderer.apply();
        mMoved.clear();
        mRenderer.setEnds(true, false, 0.25f);
        assertEquals("both moved lanes were announced", 2, mMoved.size());
        org.junit.Assert.assertTrue(mMoved.contains(mHomeRow));
        mMoved.clear();
        mRenderer.setEnds(true, false, 0.25f);
        assertEquals("a frame equal to the last announces nothing", 0, mMoved.size());
    }

    @Test public void onHomeTheWorkspacesLaneStandsOffToTheRight() {
        mFrontHome = true;
        mRenderer.apply();
        assertEquals(0f, mHomeRow.getTranslationX(), 0f);
        assertEquals(WIDTH, mKeys.getTranslationX(), 0f);
        assertEquals(0f, mApps.getTranslationX(), 0f);
    }

    @Test public void inACrossingEachLaneSlidesWithItsPage() {
        mFrontHome = true;
        mRenderer.apply();
        mRenderer.setEnds(true, false, 0.25f);
        assertEquals(-0.25f * WIDTH, mHomeRow.getTranslationX(), 0.01f);
        assertEquals(0.75f * WIDTH, mKeys.getTranslationX(), 0.01f);
        assertEquals(0f, mApps.getTranslationX(), 0f);
        mRenderer.setEnds(true, false, 0.75f);
        assertEquals(-0.75f * WIDTH, mHomeRow.getTranslationX(), 0.01f);
        assertEquals(0.25f * WIDTH, mKeys.getTranslationX(), 0.01f);
        // And back: the drag reverses, the lanes with it.
        mRenderer.setEnds(true, false, 0f);
        assertEquals(0f, mHomeRow.getTranslationX(), 0.01f);
        assertEquals(WIDTH, mKeys.getTranslationX(), 0.01f);
    }

    @Test public void theCrossingsLastFrameIsTheArrivingRestSoTheLandingWritesNothing() {
        FramePipeline frames = new FramePipeline();
        mRenderer.attach(frames);
        mFrontHome = true;
        mRenderer.apply();
        mRenderer.setEnds(true, false, 0.5f);
        frames.flush("test");
        mRenderer.setEnds(true, false, 1f);
        frames.flush("test");
        int before = laneWrites();
        // The landing: the front page changes, the ends go.
        mFrontHome = false;
        mRenderer.onLanding();
        frames.flush("test");
        assertEquals("the last frame already stood the arriving rest", before, laneWrites());
        assertEquals(0f, mKeys.getTranslationX(), 0f);
        assertEquals(-WIDTH, mHomeRow.getTranslationX(), 0f);
        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
    }

    @Test public void aWriteByAnyoneElseToALaneRowIsHeard() {
        FramePipeline frames = new FramePipeline();
        mRenderer.attach(frames);
        mFrontHome = false;
        frames.flush("test");
        mRenderer.apply();
        mKeys.setTranslationX(9f);
        mRenderer.apply();
        assertEquals(1, Counters.countOf(FrameGuards.FOREIGN_WRITER));
    }
}
