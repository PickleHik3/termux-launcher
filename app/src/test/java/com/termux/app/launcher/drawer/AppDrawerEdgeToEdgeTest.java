package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.dock.TestDockLayouts;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * The open drawer's rectangles on a host bled to the physical edges: Docked opens to a card of its
 * own inside the system bars and lays its content out in that card; Floating keeps its panel inside
 * the bars. The status-bar inset strip is covered by z-order and fades with the pane it continues.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class AppDrawerEdgeToEdgeTest {

    private static final int WIDTH_PX = 1080;
    private static final int HEIGHT_PX = 2412;
    /** The phone this was measured on: Nothing Phone 2, gesture nav. */
    private static final int STATUS_INSET_PX = 126;
    private static final int NAV_INSET_PX = 110;
    private static final int DOCK_INSET_PX = 16;
    private static final float OPEN_RADIUS_PX = 28f;
    /** Margin, the air the terminal pane leaves at the screen's sides. */
    private static final float MARGIN_PX = 24f;

    private View mRoot;
    private FakeAppDrawerHost mHost;
    private AppDrawerController mController;

    @Before public void setUp() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        mRoot = activity.findViewById(R.id.activity_termux_root_view);
        assertNotNull(mRoot);
        // What fitsSystemWindows does on a real device at the top, and what the activity then gives
        // back; the root pads nothing at the bottom, the sheets layer reaches the screen's edge.
        mRoot.setPadding(0, STATUS_INSET_PX, 0, 0);
        bleedDrawerHost(STATUS_INSET_PX);
        layOutRoot();

        mHost = new FakeAppDrawerHost(activity, null);
        mHost.viewRoot = mRoot;
        mHost.sheetsBandPx = NAV_INSET_PX;
        mHost.dockLayout = TestDockLayouts.defaultStyle(
            activity.getResources().getDisplayMetrics().density, DOCK_INSET_PX);
        mController = new AppDrawerController(mHost);
        assertTrue((Boolean) ReflectionHelpers.callInstanceMethod(mController, "bindViews"));
        ReflectionHelpers.setField(mController, "mOpenRadiusPx", OPEN_RADIUS_PX);
    }

    /**
     * Docked's drawer is a card of its own with the keyboard down too: Margin in from the sides,
     * Margin below the status bar and Margin above the navigation band. Before, the plane ran past
     * both bezels (one radius beyond each) and the content was laid out for that sheet, so the card
     * the keyboard made later clipped a grid and an A–Z strip laid out for the screen.
     */
    @Test public void dockedOpensToACardAndLaysItsContentOutInIt() {
        ReflectionHelpers.setField(mController, "mRoundedStyle", false);
        mHost.marginPx = MARGIN_PX;

        View host = mRoot.findViewById(R.id.app_drawer_host);
        assertEquals("the bled host is the whole screen", HEIGHT_PX, host.getHeight());

        Frame open = openRect();
        Frame content = contentBaseRect(open);

        assertEquals(MARGIN_PX, open.left, 0.5f);
        assertEquals(WIDTH_PX - MARGIN_PX, open.right, 0.5f);
        assertEquals(STATUS_INSET_PX + MARGIN_PX, open.top, 0.5f);
        assertEquals(HEIGHT_PX - NAV_INSET_PX - MARGIN_PX, open.bottom, 0.5f);
        // The grid, its labels and the strip are laid out in the card itself.
        assertEquals(open, content);
    }

    /** An unbled host (insets never arrived) starts the card Margin from its own top. */
    @Test public void anUnbledHostStartsTheCardMarginFromItsTop() {
        ReflectionHelpers.setField(mController, "mRoundedStyle", false);
        mHost.marginPx = MARGIN_PX;
        bleedDrawerHost(0);
        layOutRoot();

        Frame open = openRect();

        assertEquals(MARGIN_PX, open.top, 0.5f);
        assertEquals(open, contentBaseRect(open));
    }

    /** A host whose band is 0 (the system keyboard is up) ends the card Margin above its bottom. */
    @Test public void withNoBandTheDockedCardEndsMarginAboveTheBottom() {
        ReflectionHelpers.setField(mController, "mRoundedStyle", false);
        mHost.marginPx = MARGIN_PX;
        mHost.sheetsBandPx = 0;

        Frame open = openRect();
        Frame content = contentBaseRect(open);

        assertEquals(HEIGHT_PX - MARGIN_PX, open.bottom, 0.5f);
        assertEquals(open.bottom, content.bottom, 0.5f);
    }

    /** The floating card keeps floating above the pill, and with no band it reaches the edge. */
    @Test public void theRoundedRectFollowsTheHostsBand() {
        ReflectionHelpers.setField(mController, "mRoundedStyle", true);
        mHost.sheetsBandPx = 0;

        assertEquals(HEIGHT_PX, openRect().bottom, 0.5f);
    }

    /** The rounded style is a floating sheet inside the bars, and keeps the rectangle it had. */
    @Test public void theRoundedStyleKeepsTodaysRectangle() {
        ReflectionHelpers.setField(mController, "mRoundedStyle", true);

        Frame open = openRect();
        Frame content = contentBaseRect(open);

        assertEquals(STATUS_INSET_PX, open.top, 0.5f);
        assertEquals(HEIGHT_PX - NAV_INSET_PX, open.bottom, 0.5f);
        assertEquals("the floating sheet lays its grid out in its own rectangle",
            open.top, content.top, 0.5f);
        assertEquals(open.bottom, content.bottom, 0.5f);
    }

    /**
     * The status-bar inset strip stands in the ground layer and the drawer host in the sheets layer,
     * with no elevation on either, so the plane paints over the strip rather than under it. Reverse
     * the layers and the strip would stand on top of a full-screen drawer.
     */
    @Test public void theDrawerHostPaintsOverTheStatusInsetStrip() {
        ViewGroup container = mRoot.findViewById(R.id.terminal_root_container);
        View strip = mRoot.findViewById(R.id.terminal_status_bar_background);
        View host = mRoot.findViewById(R.id.app_drawer_host);
        assertNotNull(strip);
        assertNotNull(host);
        View ground = mRoot.findViewById(R.id.window_layer_ground);
        View sheets = mRoot.findViewById(R.id.window_layer_sheets);
        assertEquals("the strip stands in the ground layer", ground, strip.getParent());
        assertEquals("the drawer stands in the sheets layer", sheets, host.getParent());
        assertEquals(container, ground.getParent());
        assertEquals(container, sheets.getParent());
        assertTrue("the sheets layer is above the ground layer, so the drawer wins",
            container.indexOfChild(sheets) > container.indexOfChild(ground));
        assertEquals(0f, strip.getElevation(), 0.001f);
        assertEquals(0f, host.getElevation(), 0.001f);
    }

    /** The strip leaves on the fade of the pane whose glass it continues, and comes back at 1. */
    @Test public void theInsetStripFadesWithThePaneItContinues() {
        View strip = mRoot.findViewById(R.id.terminal_status_bar_background);
        ReflectionHelpers.setField(mController, "mStatusInsetStripView", strip);
        ReflectionHelpers.setField(mController, "mStatusBarView",
            mRoot.findViewById(R.id.terminal_window_bar_host));
        ReflectionHelpers.setField(mController, "mStatusBand",
            new AppDrawerAccessoryChoreography.Band(0f, 300f));
        ReflectionHelpers.setField(mController, "mStatusCompactHeightPx", 200f);

        applyStatusBand(0f);
        assertEquals("a dock-sized plane leaves the strip alone", 1f, strip.getAlpha(), 0.001f);

        applyStatusBand(1f);
        assertEquals("a full drawer has no second material at the top", 0f, strip.getAlpha(), 0.01f);
    }

    // ------------------------------------------------------------------ helpers

    /** The host bleeds at the top only; the bottom band is the sheets layer's, from the host. */
    private void bleedDrawerHost(int topInsetPx) {
        View host = mRoot.findViewById(R.id.app_drawer_host);
        ViewGroup.MarginLayoutParams margins =
            (ViewGroup.MarginLayoutParams) host.getLayoutParams();
        margins.topMargin = -topInsetPx;
        margins.bottomMargin = 0;
        host.setLayoutParams(margins);
    }

    private void layOutRoot() {
        mRoot.measure(View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY));
        mRoot.layout(0, 0, WIDTH_PX, HEIGHT_PX);
    }

    private Frame openRect() {
        Frame rect = ReflectionHelpers.callInstanceMethod(mController, "resolveOpenRect");
        assertNotNull(rect);
        return rect;
    }

    private Frame contentBaseRect(Frame openRect) {
        Frame rect = ReflectionHelpers.callInstanceMethod(mController, "resolveContentBaseRect",
            ClassParameter.from(Frame.class, openRect));
        assertNotNull(rect);
        return rect;
    }

    private void applyStatusBand(float progress) {
        ReflectionHelpers.callInstanceMethod(mController, "applyStatusBand",
            ClassParameter.from(float.class, progress));
    }

    private void applyFrame(float progress) {
        ReflectionHelpers.callInstanceMethod(mController, "applyFrame",
            ClassParameter.from(float.class, progress));
    }
}
