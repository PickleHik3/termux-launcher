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
 * The open drawer as one sheet from the physical top edge to the physical bottom edge.
 *
 * <p>Two rectangles, and the whole change is that they stopped being the same one: the plane runs
 * past the system bars so no other material frames it, the content stays inside them so nothing of
 * the grid is under a bar. The two strips that used to frame it are dealt with separately — the
 * status-bar inset strip is covered by z-order and fades with the pane it continues, the under-pill
 * strip lives on the decor and can only be faded.
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

    private View mRoot;
    private FakeAppDrawerHost mHost;
    private AppDrawerController mController;

    @Before public void setUp() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        mRoot = activity.findViewById(R.id.activity_termux_root_view);
        assertNotNull(mRoot);
        // What fitsSystemWindows does on a real device, and what the activity then gives back.
        mRoot.setPadding(0, STATUS_INSET_PX, 0, NAV_INSET_PX);
        bleedDrawerHost(STATUS_INSET_PX, NAV_INSET_PX);
        layOutRoot();

        mHost = new FakeAppDrawerHost(activity, null);
        mHost.viewRoot = mRoot;
        mHost.dockLayout = TestDockLayouts.defaultStyle(
            activity.getResources().getDisplayMetrics().density, DOCK_INSET_PX);
        mController = new AppDrawerController(mHost);
        assertTrue((Boolean) ReflectionHelpers.callInstanceMethod(mController, "bindViews"));
        ReflectionHelpers.setField(mController, "mOpenRadiusPx", OPEN_RADIUS_PX);
    }

    @Test public void theOpenRectRunsPastBothSystemBarsAndTheContentRectDoesNot() {
        ReflectionHelpers.setField(mController, "mRoundedStyle", false);

        View host = mRoot.findViewById(R.id.app_drawer_host);
        assertEquals("the bled host is the whole screen", HEIGHT_PX, host.getHeight());

        Frame open = openRect();
        Frame content = contentBaseRect(open);

        // Past the top bezel by one radius and past the bottom by one, which is how a single-radius
        // outline clip is made to show square corners where the sheet meets the screen edge.
        assertEquals(-OPEN_RADIUS_PX, open.top, 0.5f);
        assertEquals(HEIGHT_PX + OPEN_RADIUS_PX, open.bottom, 0.5f);
        assertEquals(DOCK_INSET_PX, open.left, 0.5f);
        assertEquals(WIDTH_PX - DOCK_INSET_PX, open.right, 0.5f);

        // The grid keeps the two bars as padding: it starts exactly at the status inset and ends
        // exactly at the navigation inset, which is where it sat before the plane was bled.
        assertEquals(STATUS_INSET_PX, content.top, 0.5f);
        assertEquals(HEIGHT_PX - NAV_INSET_PX + OPEN_RADIUS_PX, content.bottom, 0.5f);
        assertEquals(open.left, content.left, 0.5f);
        assertEquals(open.right, content.right, 0.5f);
    }

    /**
     * The same two rectangles in screen coordinates, with and without the bleed: the plane grew by
     * exactly the two insets and the content did not move by a pixel.
     */
    @Test public void theContentKeepsTheEdgesItHadBeforeTheHostWasBled() {
        ReflectionHelpers.setField(mController, "mRoundedStyle", false);
        // The bled host starts at the physical top edge; the plain one starts below the status bar,
        // so every rectangle below is converted to screen coordinates before being compared.
        Frame bledOpen = openRect();
        Frame bledContent = contentBaseRect(bledOpen);

        bleedDrawerHost(0, 0);
        layOutRoot();
        Frame plainOpen = openRect();
        Frame plainContent = contentBaseRect(plainOpen);

        assertEquals("the plane gained the status inset at the top, plus the squared corner",
            STATUS_INSET_PX + OPEN_RADIUS_PX, (plainOpen.top + STATUS_INSET_PX) - bledOpen.top, 0.5f);
        assertEquals("an unbled host keeps the rectangle the drawer has always had",
            0f, plainOpen.top, 0.5f);
        assertEquals("and exactly the navigation inset at the bottom",
            NAV_INSET_PX, bledOpen.bottom - (plainOpen.bottom + STATUS_INSET_PX), 0.5f);
        assertEquals("the grid's top edge did not move",
            plainContent.top + STATUS_INSET_PX, bledContent.top, 0.5f);
        assertEquals("nor its bottom",
            plainContent.bottom + STATUS_INSET_PX, bledContent.bottom, 0.5f);
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
     * The status-bar inset strip is the drawer host's earlier sibling with no elevation of its own,
     * so the plane paints over it rather than under it. Reverse the two and the strip would stand
     * on top of a full-screen drawer.
     */
    @Test public void theDrawerHostPaintsOverTheStatusInsetStrip() {
        ViewGroup container = mRoot.findViewById(R.id.terminal_root_container);
        View strip = mRoot.findViewById(R.id.terminal_status_bar_background);
        View host = mRoot.findViewById(R.id.app_drawer_host);
        assertNotNull(strip);
        assertNotNull(host);
        assertEquals("both are children of the same container",
            container, strip.getParent());
        assertEquals(container, host.getParent());
        assertTrue("the drawer is the later sibling, so it wins in z",
            container.indexOfChild(host) > container.indexOfChild(strip));
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

    /** The under-pill strip cannot be covered, so it crosses over with the plane and is restored. */
    @Test public void theUnderPillStripCrossesOverWithThePlane() {
        ReflectionHelpers.setField(mController, "mRoundedStyle", false);
        Frame open = openRect();
        ReflectionHelpers.setField(mController, "mOpenRect", open);
        ReflectionHelpers.setField(mController, "mDockRect",
            new Frame(0f, HEIGHT_PX - 400f, WIDTH_PX, HEIGHT_PX - NAV_INSET_PX));

        applyFrame(0f);
        assertEquals("nothing moved, so the strip is untouched", 1f, mHost.decorNavStripAlpha, 0.001f);

        applyFrame(1f);
        assertEquals("the plane's glass has arrived underneath it", 0f,
            mHost.decorNavStripAlpha, 0.001f);

        ReflectionHelpers.callInstanceMethod(mController, "onClosed",
            ClassParameter.from(boolean.class, false));
        assertEquals("a closed drawer hands the strip back", 1f, mHost.decorNavStripAlpha, 0.001f);
    }

    // ------------------------------------------------------------------ helpers

    private void bleedDrawerHost(int topInsetPx, int bottomInsetPx) {
        View host = mRoot.findViewById(R.id.app_drawer_host);
        ViewGroup.MarginLayoutParams margins =
            (ViewGroup.MarginLayoutParams) host.getLayoutParams();
        margins.topMargin = -topInsetPx;
        margins.bottomMargin = -bottomInsetPx;
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
