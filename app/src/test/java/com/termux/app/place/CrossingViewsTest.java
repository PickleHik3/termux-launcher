package com.termux.app.place;

import static com.termux.app.place.CrossingTest.BOTH;
import static com.termux.app.place.CrossingTest.HOME_REST;
import static com.termux.app.place.CrossingTest.REVISION;
import static com.termux.app.place.CrossingTest.ROW;
import static com.termux.app.place.CrossingTest.STEP;
import static com.termux.app.place.CrossingTest.WIDTH;
import static com.termux.app.place.CrossingTest.WORKSPACE_REST;
import static com.termux.app.place.CrossingTest.across;
import static com.termux.app.place.CrossingTest.half;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.SuggestionBarView;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.PaneWallPage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * A crossing driven from begin to land through its one production adapter, {@link CrossingViews},
 * over real views and a fake activity: the views the frames move are back at rest when the wall
 * lands, whether it crossed or came back, and a frame repeated at the same offset writes nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class CrossingViewsTest {

    /** Every write the adapter can make to a view, counted. */
    private static final class Writes {
        int count;
    }

    private static final class CountedView extends View {
        final Writes writes;
        CountedView(Context context, Writes writes) {
            super(context);
            this.writes = writes;
        }
        @Override public void setTranslationX(float x) { if (writes != null) writes.count++; super.setTranslationX(x); }
        @Override public void setTranslationY(float y) { if (writes != null) writes.count++; super.setTranslationY(y); }
        @Override public void setAlpha(float alpha) { if (writes != null) writes.count++; super.setAlpha(alpha); }
        @Override public void setClipBounds(Rect clip) { if (writes != null) writes.count++; super.setClipBounds(clip); }
        @Override public void invalidate() {
            if (writes != null) writes.count++;
            super.invalidate();
        }
    }

    private static final class CountedFrame extends FrameLayout {
        final Writes writes;
        CountedFrame(Context context, Writes writes) {
            super(context);
            this.writes = writes;
        }
        @Override public void setTranslationX(float x) { if (writes != null) writes.count++; super.setTranslationX(x); }
        @Override public void setTranslationY(float y) { if (writes != null) writes.count++; super.setTranslationY(y); }
        @Override public void setAlpha(float alpha) { if (writes != null) writes.count++; super.setAlpha(alpha); }
        @Override public void setClipBounds(Rect clip) { if (writes != null) writes.count++; super.setClipBounds(clip); }
        @Override public void invalidate() {
            if (writes != null) writes.count++;
            super.invalidate();
        }
    }

    /** The activity's side: its views by id, and what the adapter hands it, recorded. */
    private static final class FakeHost implements CrossingViews.Host {
        final SparseArray<View> views = new SparseArray<>();
        float dockPx = Float.NaN, keyboardPx = Float.NaN, barX = Float.NaN;
        int coverPx = -1, preSizePx = -1;
        final List<PaneWallPage> preRolls = new ArrayList<>();

        @Nullable @Override public View hotView(int id) { return views.get(id); }
        @Override public void translateStack(float dock, float keyboard) {
            dockPx = dock;
            keyboardPx = keyboard;
        }
        @Override public void rideStatusX(float px) { barX = px; }
        @Override public void coverWindowInFront(int px) { coverPx = px; }
        @Override public void preRollKeyboard(@NonNull PaneWallPage target) { preRolls.add(target); }
        @Override public boolean extraKeysRowOnScreen() { return true; }
        @Override public int terminalFlushPaddingPx() { return 12; }
        @Nullable @Override public SuggestionBarView dockRow() { return null; }
        @Override public void syncDockBeadsWithMorph() {}
        @Override public void onDockRowContentChanged() {}
        @Nullable @Override public StackPicture stackPicture(@Nullable View stack) { return null; }
        @Override public int wallWidthPx() { return WIDTH; }
        @Override public void applyTerminalPreSize(int px) { preSizePx = px; }
    }

    /** The layers a sink or a fold moves, and the views the alphas fade. */
    private static final int[] LAYERS = {R.id.accessory_surface_host, R.id.apps_bar_az_fx_underlay,
        R.id.accessory_row_stack, R.id.apps_bar_az_fx_overlay, R.id.apps_bar_az_label_overlay};
    private static final int[] FADED = {R.id.terminal_status_row, R.id.terminal_status_lens,
        R.id.place_apps_bar_host, R.id.place_extra_keys_host, R.id.place_az_bar_host,
        R.id.place_off_dock_plank_host};

    private Activity mActivity;
    private final Writes mWrites = new Writes();
    private FakeHost mHost;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mHost = new FakeHost();
        // The row stack holds the extra keys, laid out first so its own layout leaves them be.
        CountedFrame rowStack = new CountedFrame(mActivity, mWrites);
        CountedView keys = new CountedView(mActivity, mWrites);
        rowStack.addView(keys);
        rowStack.layout(0, 0, WIDTH, 300);
        keys.layout(0, 150, WIDTH, 250);
        mHost.views.put(R.id.accessory_row_stack, rowStack);
        mHost.views.put(R.id.terminal_toolbar_host, keys);
        for (int id : LAYERS) {
            if (id == R.id.accessory_row_stack) continue;
            CountedView layer = new CountedView(mActivity, mWrites);
            layer.layout(0, 0, WIDTH, 300);
            mHost.views.put(id, layer);
        }
        for (int id : FADED) mHost.views.put(id, new CountedView(mActivity, mWrites));
        CountedFrame stack = new CountedFrame(mActivity, mWrites);
        stack.layout(0, 0, WIDTH, 400);
        stack.setPadding(0, 0, 0, 24);
        mHost.views.put(R.id.accessory_stack_container, stack);
        CountedView backdrop = new CountedView(mActivity, mWrites);
        mHost.views.put(R.id.accessory_blur_backdrop, backdrop);
        mWrites.count = 0;
    }

    /** The crossing over the adapter, driven as the activity's travel pass applies a frame. */
    private final class Drive {
        final CrossingViews views = new CrossingViews(mActivity, mHost);
        final Crossing crossing = new Crossing(views, views, views, views, views);
        final PaneWallPage committed;
        final SeamCrossing.Endpoint here, there;
        final PlaceChromeTravel.States states;

        Drive(@NonNull PaneWallPage from) {
            views.attach(crossing);
            committed = from;
            here = half(from, false);
            there = half(across(from), false);
            states = place -> new Rest(false, false, true);
        }

        void step(float fraction) {
            float offset = committed == PaneWallPage.WIDGETS ? -fraction * WIDTH : fraction * WIDTH;
            Frame frame = PlaceChromeTravel.at(ROW, committed, offset, WIDTH, states, committed);
            PaneWallPage other = SeamTravel.otherHalfPage(frame, committed);
            if (other != null && other != crossing.seam().other()) {
                boolean fromHome = committed == PaneWallPage.WIDGETS;
                crossing.begin(here, there, SeamCrossing.Stage.of(false, () -> false, true),
                    fromHome ? HOME_REST : WORKSPACE_REST, fromHome ? WORKSPACE_REST : HOME_REST,
                    REVISION);
            }
            SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
            if (answers.relayout()) crossing.seam().standLayout();
            float translation = crossing.stack(answers, answers.dockReveal(), 0, 200, 150);
            if (crossing.holdsContent()) crossing.coverAt(translation);
            crossing.rideBar(frame, true, WIDTH);
            crossing.fade(frame.statusReveal, frame.dockReveal);
        }
    }

    private float layerSink(int id) {
        return mHost.views.get(id).getTranslationY();
    }

    /** Every view the frames moved is back where it is laid out, as the settle finds it. */
    private void assertAtRest(String what) {
        for (int id : LAYERS) {
            View layer = mHost.views.get(id);
            assertEquals(what + ": a layer's sink", 0f, layer.getTranslationY(), 0f);
            assertNull(what + ": a layer's cut", layer.getClipBounds());
        }
        for (int id : FADED) assertEquals(what + ": an alpha", 1f, mHost.views.get(id).getAlpha(), 0f);
        assertEquals(what + ": the stack's slide", 0f,
            mHost.views.get(R.id.accessory_stack_container).getTranslationX(), 0f);
        assertEquals(what + ": the stack's translation", 0f, mHost.dockPx, 0f);
        assertEquals(what + ": the keyboard's share", 0f, mHost.keyboardPx, 0f);
        assertEquals(what + ": the bar's ride", 0f, mHost.barX, 0f);
    }

    @Test
    public void aCrossingEitherWayLandsWithEveryViewAtRest() {
        for (PaneWallPage from : BOTH) {
            setUp();
            Drive drive = new Drive(from);
            boolean folded = false;
            for (float f = STEP; f < 1f; f += STEP) {
                drive.step(f);
                // Home stands the window's rows less the extra keys: the rows fold with the way.
                if (layerSink(R.id.accessory_row_stack) != 0f) folded = true;
            }
            drive.step(1f);
            assertTrue(from + ": the rows sank with the fold on the way", folded);
            drive.crossing.land(PageRef.of(across(from)), REVISION);
            assertAtRest(from + " landed across");
            drive.crossing.end();
            assertTrue(from + ": nothing of the crossing left", drive.crossing.idle());
        }
    }

    @Test
    public void aCrossingAbandonedMidwayLandsBackWithEveryViewAtRest() {
        for (PaneWallPage from : BOTH) {
            setUp();
            Drive drive = new Drive(from);
            for (float f = STEP; f <= 0.5f; f += STEP) drive.step(f);
            assertTrue(from + ": the rows sank mid-way", layerSink(R.id.accessory_row_stack) != 0f);
            // A finger that turns back: the frames retrace to where the wall left.
            for (float f = 0.5f; f > 0f; f -= STEP) drive.step(f);
            drive.step(0f);
            drive.crossing.land(PageRef.of(from), REVISION);
            assertAtRest(from + " came back");
            drive.crossing.end();
            assertTrue(from + ": nothing of the crossing left", drive.crossing.idle());
        }
    }

    @Test
    public void aFrameRepeatedAtTheSameOffsetWritesNoView() {
        for (PaneWallPage from : BOTH) {
            setUp();
            Drive drive = new Drive(from);
            for (float f = STEP; f <= 0.3f; f += STEP) drive.step(f);
            drive.step(0.3f);
            assertTrue(from + ": the frame moved something",
                layerSink(R.id.accessory_row_stack) != 0f);
            mWrites.count = 0;
            drive.step(0.3f);
            assertEquals(from + ": views written by a repeated frame", 0, mWrites.count);
        }
    }
}
