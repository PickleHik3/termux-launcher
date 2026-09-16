package com.termux.app.statusbar;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.place.PlaceLayout.Edge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A detail card is a live surface, not a picture: the stats card's process list arrives a sample
 * after the card is already up and adds a whole section to it. The card was placed once, from the
 * size it opened at, so on a bottom bar it grew the only way a wrap-height popup can — downward,
 * out of its own anchored corner and over the dock and the keyboard. Tapping the widget a second
 * time looked right only because the list was already in the snapshot by then.
 *
 * <p>So: whatever the card grows to, the edge facing the bar is the edge that does not move.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, qualifiers = "w411dp-h891dp-xxhdpi", application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class StatusCardHostGrowthTest {

    /** A status bar's own thickness, in the phone's pixels. */
    private static final int BAR = 264;

    private ActivityController<Activity> controller;
    private Activity activity;
    private View screen;
    private StatusCardHost host;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        // The real screen, so the card is placed inside the canvas every other bar is placed in.
        screen = LayoutInflater.from(activity).inflate(R.layout.activity_termux, null);
        activity.setContentView(screen,
            new ViewGroup.LayoutParams(displayWidth(), displayHeight()));
        idle();
        screen.measure(View.MeasureSpec.makeMeasureSpec(displayWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(displayHeight(), View.MeasureSpec.EXACTLY));
        screen.layout(0, 0, displayWidth(), displayHeight());
        host = new StatusCardHost();
    }

    @After
    public void tearDown() {
        if (host != null) host.dismiss();
        if (controller != null) controller.close();
    }

    @Test
    public void aCardThatGrowsUnderABottomBarKeepsItsBottomAboveTheBar() {
        int barTop = canvasHeight() - BAR;
        View bar = addBar(barTop);
        SystemStatsCardView card = new SystemStatsCardView(activity);
        card.bind(stats(0));                       // nothing sampled yet: no process section

        host.setEdge(Edge.BOTTOM);
        host.setDropEdge(bar);
        host.show(bar, card, style(), null);

        Rect opened = host.cardBoundsInWindow();
        assertNotNull("the card was never placed", opened);
        assertTrue("it must open clear of the bar: " + opened,
            opened.bottom <= barTop - gap());

        card.bind(stats(12));                      // the running processes arrive a sample later
        layoutCardWindow(card);
        idle();

        Rect grown = host.cardBoundsInWindow();
        assertNotNull(grown);
        assertTrue("the process list has to make the card taller, or nothing is being tested",
            grown.height() > opened.height());
        assertEquals("a bottom bar pins the card's bottom", opened.bottom, grown.bottom);
        assertTrue("and it stays clear of the bar: " + grown, grown.bottom <= barTop - gap());
        assertInsideCanvas(grown);
    }

    @Test
    public void aCardThatGrowsUnderATopBarKeepsItsTopBelowTheBar() {
        View bar = addBar(0);
        SystemStatsCardView card = new SystemStatsCardView(activity);
        card.bind(stats(0));

        host.setEdge(Edge.TOP);
        host.setDropEdge(bar);
        host.show(bar, card, style(), null);

        Rect opened = host.cardBoundsInWindow();
        assertNotNull("the card was never placed", opened);
        assertTrue("it must open clear of the bar: " + opened, opened.top >= BAR + gap());

        card.bind(stats(12));
        layoutCardWindow(card);
        idle();

        Rect grown = host.cardBoundsInWindow();
        assertNotNull(grown);
        assertTrue("the process list has to make the card taller",
            grown.height() > opened.height());
        assertEquals("a top bar pins the card's top", opened.top, grown.top);
        assertTrue("and it stays clear of the bar: " + grown, grown.top >= BAR + gap());
        assertInsideCanvas(grown);
    }

    // -------------------------------------------------------------------------------- helpers

    /** What the card's own window does on the frame after its content changed: measure, lay out. */
    private void layoutCardWindow(@NonNull View card) {
        ViewGroup container = (ViewGroup) card.getParent();
        assertNotNull("the card is not in a popup", container);
        Rect bounds = host.cardBoundsInWindow();
        assertNotNull(bounds);
        container.measure(
            View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        container.layout(0, 0, container.getMeasuredWidth(), container.getMeasuredHeight());
    }

    /**
     * A bar standing on one edge of the screen. It is a real child of the real screen — the card
     * is clamped inside that canvas — with the one thing a test cannot lay out for itself, where
     * on the window it stands, answered directly.
     */
    @NonNull
    private View addBar(int topInWindow) {
        ViewGroup container = screen.findViewById(R.id.terminal_root_container);
        assertNotNull(container);
        View bar = new FixedBar(activity, topInWindow);
        container.addView(bar, new ViewGroup.LayoutParams(canvasWidth(), BAR));
        bar.measure(View.MeasureSpec.makeMeasureSpec(canvasWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(BAR, View.MeasureSpec.EXACTLY));
        bar.layout(0, topInWindow, canvasWidth(), topInWindow + BAR);
        return bar;
    }

    private static final class FixedBar extends View {
        private final int mTop;

        FixedBar(@NonNull Context context, int top) {
            super(context);
            mTop = top;
        }

        @Override public void getLocationInWindow(int[] outLocation) {
            outLocation[0] = 0;
            outLocation[1] = mTop;
        }

        @Override public void getLocationOnScreen(int[] outLocation) {
            getLocationInWindow(outLocation);
        }
    }

    @NonNull
    private SystemStatsController.Stats stats(int processes) {
        SystemStatsController.Stats stats = new SystemStatsController.Stats();
        stats.cpuPercent = 21;
        stats.cores = 4;
        stats.corePercent = new int[]{12, 34, 8, 60};
        stats.load1 = 1.25;
        stats.memTotalKb = 8_000_000;
        stats.memUsedKb = 4_200_000;
        stats.memAvailKb = 3_100_000;
        stats.cachedKb = 900_000;
        for (int i = 0; i < processes; i++) {
            stats.top.add(new SystemStatsController.Proc("process" + i, 3.5 + i, 40_000L + i));
        }
        return stats;
    }

    @NonNull
    private StatusCardHost.StyleProvider style() {
        return new StatusCardHost.StyleProvider() {
            @NonNull @Override public Drawable cardBackground() {
                return new ColorDrawable(0xFF101010);
            }

            @Override public float cornerRadiusPx() { return 0f; }

            @Override public float contentInsetPx() { return 0f; }
        };
    }

    private void assertInsideCanvas(@NonNull Rect card) {
        assertTrue("off the top of the canvas: " + card, card.top >= 0);
        assertTrue("off the left of the canvas: " + card, card.left >= 0);
        assertTrue("past the canvas's right: " + card, card.right <= canvasWidth());
        assertTrue("past the canvas's bottom: " + card, card.bottom <= canvasHeight());
    }

    /** The drop gap the host keeps between every card and its bar. */
    private int gap() {
        return Math.round(4 * activity.getResources().getDisplayMetrics().density);
    }

    private int canvasWidth() {
        View root = screen.getRootView();
        return root != null && root.getWidth() > 0 ? root.getWidth() : displayWidth();
    }

    private int canvasHeight() {
        View root = screen.getRootView();
        return root != null && root.getHeight() > 0 ? root.getHeight() : displayHeight();
    }

    private int displayWidth() {
        return metrics().widthPixels;
    }

    private int displayHeight() {
        return metrics().heightPixels;
    }

    @NonNull
    private DisplayMetrics metrics() {
        return activity.getResources().getDisplayMetrics();
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
}
