package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.ViewGroup;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerAppCellViewTest {

    @Test public void verticalAndHorizontalBindingsShareArtworkLabelAndGeometry() {
        LauncherAppEntry entry = entry("Alpha");
        AppDrawerGridMetrics vertical = AppDrawerGridMetrics.resolve(400f, 1f, 11f, 4);
        AppDrawerHorizontalGridMetrics horizontal = AppDrawerHorizontalGridMetrics.resolve(
            400f, 200f, 1f, 11f, 4, 2);
        AppDrawerAppCellView first = cell();
        AppDrawerAppCellView second = cell();
        first.bind(null, entry, vertical, AppDrawerAppCellView.ALLOW_CLICKS);
        second.bind(null, entry, horizontal, AppDrawerAppCellView.ALLOW_CLICKS);
        assertEquals(first.label.getText(), second.label.getText());
        assertSame(entry.icon, first.icon.getDrawable());
        assertSame(entry.icon, second.icon.getDrawable());
        assertEquals(first.getContentDescription(), second.getContentDescription());
        assertEquals(Math.round(vertical.iconPx), first.icon.getLayoutParams().width);
        assertEquals(Math.round(horizontal.iconPx), second.icon.getLayoutParams().width);
    }

    @Test public void clickGateIsConsultedOnTheRetainedClosingUp() {
        int[] checks = {0};
        AppDrawerAppCellView cell = cell();
        cell.bind(null, entry("Beta"), AppDrawerGridMetrics.resolve(400f, 1f, 11f),
            () -> { checks[0]++; return true; });
        assertTrue(cell.performClick());
        assertEquals(1, checks[0]);
    }

    @Test public void bothMetricBindingsInstallTheSameTintClickAndLongPressSeams() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SuggestionBarView dock = new SuggestionBarView(activity, null);
        LauncherAppEntry entry = entry("Delta");
        AppDrawerAppCellView vertical = new AppDrawerAppCellView(activity);
        AppDrawerAppCellView horizontal = new AppDrawerAppCellView(activity);
        vertical.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
        horizontal.setLayoutParams(new ViewGroup.LayoutParams(100, 100));

        vertical.bind(dock, entry, AppDrawerGridMetrics.resolve(400f, 1f, 11f, 4),
            AppDrawerAppCellView.ALLOW_CLICKS);
        horizontal.bind(dock, entry, AppDrawerHorizontalGridMetrics.resolve(
            400f, 200f, 1f, 11f, 4, 2), AppDrawerAppCellView.ALLOW_CLICKS);

        assertTrue(vertical.hasOnClickListeners());
        assertTrue(horizontal.hasOnClickListeners());
        assertTrue(vertical.isLongClickable());
        assertTrue(horizontal.isLongClickable());
        assertEquals(vertical.icon.getColorFilter(), horizontal.icon.getColorFilter());
        assertEquals(vertical.label.getCurrentTextColor(), horizontal.label.getCurrentTextColor());
    }

    @Test public void unbindClearsDrawableListenersAndTransientAppearance() {
        AppDrawerAppCellView cell = cell();
        cell.bind(null, entry("Gamma"), AppDrawerGridMetrics.resolve(400f, 1f, 11f),
            AppDrawerAppCellView.ALLOW_CLICKS);
        cell.setOnLongClickListener(v -> true);
        cell.setLongClickable(true);
        cell.setAlpha(0.28f);
        cell.setScaleX(1.08f);
        cell.setScaleY(1.08f);
        cell.unbind();
        assertNull(cell.icon.getDrawable());
        assertEquals("", cell.label.getText().toString());
        assertFalse(cell.isLongClickable());
        assertEquals(1f, cell.getAlpha(), 0f);
        assertEquals(1f, cell.getScaleX(), 0f);
        assertEquals(1f, cell.getScaleY(), 0f);
    }

    private static AppDrawerAppCellView cell() {
        AppDrawerAppCellView cell = new AppDrawerAppCellView(RuntimeEnvironment.getApplication());
        cell.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
        return cell;
    }

    private static LauncherAppEntry entry(String label) {
        return new LauncherAppEntry(new AppRef("pkg." + label, "Main"), label,
            new ColorDrawable(Color.RED));
    }
}
