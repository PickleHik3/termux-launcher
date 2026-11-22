package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.app.chrome.CornerZones;
import com.termux.app.wall.PaneControlsView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

/**
 * The drawer's corner tab sits in the drawer card's own corner: the content view is the card, so
 * the tab fills it with no margin, whichever way the search bar faces and whichever corner is held.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class AppDrawerCornerTabPlacementTest {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;
    private static final float EDGE_TOLERANCE_PX = 0.5f;

    private AppDrawerContentView content;

    @Before
    public void setUp() {
        Robolectric.getForegroundThreadScheduler().pause();
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        float density = context.getResources().getDisplayMetrics().density;
        content = new AppDrawerContentView(context);
        content.setInteractive(true);
        content.setMetrics(AppDrawerGridMetrics.resolve(WIDTH, density, 30f));
        content.bind(null, new AppDrawerSearchController());
        content.setCardCornerRadiusPx(26f * density);
    }

    private void layout() {
        content.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, WIDTH, HEIGHT);
    }

    @Test public void theTabLinesUpInsideTheBorderTheTerminalPaneStyleAnswers() {
        content.setTabStyleSource(new AppDrawerContentView.TabStyleSource() {
            @Override public com.termux.app.terminal.PaneSurfaceStyle style() { return null; }
            @Override public float borderPx() { return 4f; }
        });
        layout();
        content.cornerTab().showForTest(CornerZones.TOP_LEFT);
        layout();
        PaneControlsView tab = null;
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i) instanceof PaneControlsView) {
                tab = (PaneControlsView) content.getChildAt(i);
            }
        }
        assertNotNull(tab);
        RectF rect = new RectF();
        assertTrue(tab.restingTabBounds(rect));
        assertEquals(4f, rect.top, EDGE_TOLERANCE_PX);
        assertEquals(4f, rect.left, EDGE_TOLERANCE_PX);
    }

    @Test public void theTabIsTheCardsCornerWithTheBarAtTheTopAndAtTheBottom() {
        int[] corners = {CornerZones.TOP_LEFT, CornerZones.TOP_RIGHT,
            CornerZones.BOTTOM_RIGHT, CornerZones.BOTTOM_LEFT};
        for (boolean barAtBottom : new boolean[] {false, true}) {
            content.setSearchAtBottom(barAtBottom);
            layout();
            for (int corner : corners) {
                content.cornerTab().showForTest(corner);
                layout();
                PaneControlsView tab = null;
                for (int i = 0; i < content.getChildCount(); i++) {
                    if (content.getChildAt(i) instanceof PaneControlsView) {
                        tab = (PaneControlsView) content.getChildAt(i);
                    }
                }
                assertNotNull(tab);
                assertSame(content, tab.getParent());
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) tab.getLayoutParams();
                assertEquals(0, params.topMargin);
                assertEquals(0, params.bottomMargin);
                assertEquals(0, params.leftMargin);
                assertEquals(0, params.rightMargin);
                assertEquals(WIDTH, tab.getWidth());
                assertEquals(HEIGHT, tab.getHeight());
                // Drawn over the search bar and the section switch: last child of the card.
                assertSame(tab, content.getChildAt(content.getChildCount() - 1));

                RectF rect = new RectF();
                assertTrue(tab.restingTabBounds(rect));
                String where = "bar bottom=" + barAtBottom + " corner=" + corner;
                boolean top = CornerZones.isTop(corner);
                boolean left = corner == CornerZones.TOP_LEFT || corner == CornerZones.BOTTOM_LEFT;
                assertEquals(where, top ? 0f : HEIGHT, top ? rect.top : rect.bottom,
                    EDGE_TOLERANCE_PX);
                assertEquals(where, left ? 0f : WIDTH, left ? rect.left : rect.right,
                    EDGE_TOLERANCE_PX);
                content.cornerTab().reset();
            }
        }
    }
}
