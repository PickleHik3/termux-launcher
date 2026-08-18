package com.termux.app.statusbar;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.launcher.widget.LauncherWidgetHostController;
import com.termux.app.launcher.widget.LauncherWidgetRecord;
import com.termux.app.launcher.widget.LauncherWidgetRepository;
import com.termux.app.launcher.widget.WidgetCellRect;
import com.termux.app.launcher.widget.WidgetPaneView;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Settled-FULL status-row ownership: applyFrame's geometry is the row's only writer while FULL
 * is engaged. The production window-bar refresh (window label polls, shell attention, onResume)
 * runs freely during settled FULL and must not re-anchor the row to its normal-state rule —
 * CENTER_VERTICAL while the clock is collapsed — which parks it mid-pane on a real display.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w411dp-h919dp-420dpi")
public class FullStatusBarRowBottomTest {

    @Test public void settledFullRowStaysOnPaneBottomAcrossRefreshWhileEmpty() {
        assertSettledFullGeometry(new Fixture(false, TopStatusBarState.COMPACT));
    }

    @Test public void settledFullRowStaysOnPaneBottomAcrossRefreshWhilePopulated() {
        assertSettledFullGeometry(new Fixture(true, TopStatusBarState.COMPACT));
    }

    @Test public void fullBandsStayDisjointThroughoutSpringAndAfterRefresh() {
        Fixture fixture = new Fixture(false, TopStatusBarState.EXPANDED);
        for (float progress : new float[] {0f, .5f, 1f}) {
            fixture.setFullProgress(progress);
            if (progress == 1f) {
                fixture.activity.refreshTerminalWindowBar();
                fixture.layoutRoot();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
            }
            ViewGroup root = fixture.activity.findViewById(R.id.activity_termux_root_view);
            Rect top = boundsIn(root,
                fixture.activity.findViewById(R.id.terminal_top_widget_area));
            Rect body = boundsIn(root, fixture.activity.findViewById(R.id.widget_pane));
            Rect status = boundsIn(root,
                fixture.activity.findViewById(R.id.terminal_status_row));
            assertEquals("progress=" + progress, top.bottom, body.top);
            assertEquals("progress=" + progress, status.top, body.bottom);
            assertFalse("top/body overlap at progress=" + progress, Rect.intersects(top, body));
            assertFalse("body/status overlap at progress=" + progress,
                Rect.intersects(body, status));
            assertFalse("top/status overlap at progress=" + progress,
                Rect.intersects(top, status));
        }
    }

    private static void assertSettledFullGeometry(Fixture fixture) {
        // The refresh that runs while FULL is settled; before the ownership guard it re-parked
        // the row mid-pane through applyStatusBarStyle's normal-state anchor.
        fixture.activity.refreshTerminalWindowBar();
        fixture.layoutRoot();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        ViewGroup root = fixture.activity.findViewById(R.id.activity_termux_root_view);
        Rect host = boundsIn(root,
            fixture.activity.findViewById(R.id.terminal_window_bar_host));
        Rect top = boundsIn(root, fixture.activity.findViewById(R.id.terminal_top_widget_area));
        Rect body = boundsIn(root, fixture.activity.findViewById(R.id.widget_pane));
        Rect status = boundsIn(root, fixture.activity.findViewById(R.id.terminal_status_row));

        float density = fixture.activity.getResources().getDisplayMetrics().density;
        int inset = Math.round(2f * density);
        assertTrue("status row " + status + " must end inside pane " + host,
            status.bottom <= host.bottom);
        assertTrue("status row " + status + " must sit within one inset (" + inset
                + "px) of the pane bottom " + host.bottom,
            host.bottom - status.bottom <= inset);

        int chromelessSpan = host.height() - top.height() - status.height();
        assertTrue("widget body " + body + " must fill >= 70% of the " + chromelessSpan
                + "px span between the chrome bands",
            body.height() >= Math.round(0.7f * chromelessSpan));

        assertEquals(top.bottom, body.top);
        assertEquals(status.top, body.bottom);
        assertFalse(Rect.intersects(top, status));
        assertFalse(Rect.intersects(body, status));
    }

    private static Rect boundsIn(ViewGroup ancestor, View descendant) {
        Rect bounds = new Rect(0, 0, descendant.getWidth(), descendant.getHeight());
        ancestor.offsetDescendantRectToMyCoords(descendant, bounds);
        return bounds;
    }

    private static final class Fixture {
        final TermuxActivity activity;
        final FullStatusBarController full;
        final int fullHeight;

        Fixture(boolean populated, TopStatusBarState prior) {
            activity = Robolectric.buildActivity(TermuxActivity.class).get();
            activity.setContentView(R.layout.activity_termux);
            TermuxAppSharedPreferences preferences = ReflectionHelpers.callConstructor(
                TermuxAppSharedPreferences.class,
                ReflectionHelpers.ClassParameter.from(Context.class, activity));
            ReflectionHelpers.setField(activity, "mPreferences", preferences);
            // COMPACT prior is the on-device repro: the collapsed-clock preference selects the
            // CENTER_VERTICAL normal-state row anchor that mid-parks a full-height pane.
            preferences.setTopPaneClockCollapsed(prior == TopStatusBarState.COMPACT);
            // The repro depends on the persisted collapsed flag actually being read back by
            // applyStatusBarStyle; a silently defaulted preference would make the test vacuous.
            if (preferences.isTopPaneClockCollapsed() != (prior == TopStatusBarState.COMPACT)) {
                throw new IllegalStateException("collapsed preference did not round-trip");
            }

            LauncherWidgetRepository repository = new LauncherWidgetRepository(new Memory());
            if (populated) {
                repository.putRecord(new LauncherWidgetRecord(20,
                    new ComponentName("provider.pkg", "Provider"), 0,
                    LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 2, 2),
                    new Bundle(), null));
            }
            WidgetPaneView pane = activity.findViewById(R.id.widget_pane);
            pane.render(repository, LauncherWidgetHostController.Capability.AVAILABLE);

            View host = activity.findViewById(R.id.terminal_window_bar_host);
            host.setVisibility(View.VISIBLE);
            layoutRoot();
            ReflectionHelpers.callInstanceMethod(activity, "createFullStatusBarController");
            full = ReflectionHelpers.getField(activity, "mFullStatusBarController");
            full.restoreFullImmediate(prior);
            layoutRoot();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            fullHeight = host.getHeight();
        }

        void setFullProgress(float progress) {
            View host = activity.findViewById(R.id.terminal_window_bar_host);
            View top = activity.findViewById(R.id.terminal_top_widget_area);
            int expandedHeight = Math.round(96f
                * activity.getResources().getDisplayMetrics().density);
            int height = Math.round(expandedHeight + (fullHeight - expandedHeight) * progress);
            ReflectionHelpers.callInstanceMethod(activity, "applyTopStatusBarInteractiveHeight",
                ReflectionHelpers.ClassParameter.from(View.class, host),
                ReflectionHelpers.ClassParameter.from(View.class, top),
                ReflectionHelpers.ClassParameter.from(int.class, height),
                ReflectionHelpers.ClassParameter.from(boolean.class, false));
            layoutRoot();
        }

        void layoutRoot() {
            View root = activity.findViewById(R.id.activity_termux_root_view);
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2412, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2412);
        }
    }

    private static final class Memory implements LauncherWidgetRepository.Storage {
        private String value;
        @Override public String read() { return value; }
        @Override public boolean write(String next) { value = next; return true; }
    }
}
