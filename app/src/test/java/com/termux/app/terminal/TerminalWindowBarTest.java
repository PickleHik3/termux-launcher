package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalWindowBarTest {

    @Test
    public void setWindows_marksSelectionAndDispatchesClicks() {
        TerminalWindowBar bar = new TerminalWindowBar(ApplicationProvider.getApplicationContext(), null);
        assertTrue(bar.getClipToPadding());
        AtomicInteger selected = new AtomicInteger(-1);
        AtomicInteger created = new AtomicInteger();
        bar.setOnWindowSelectedListener(selected::set);
        bar.setOnCreateWindowListener(created::incrementAndGet);
        bar.setWindows(Arrays.asList(
            new TerminalWindowBar.WindowItem("fish-icon home", "fish in home"),
            new TerminalWindowBar.WindowItem("ssh-icon zbook", "ssh in zbook")), 1);

        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        assertEquals(3, tabs.getChildCount());
        assertFalse(tabs.getChildAt(0).isSelected());
        assertTrue(tabs.getChildAt(1).isSelected());
        assertEquals("ssh-icon zbook", ((TextView) tabs.getChildAt(1)).getText().toString());
        assertEquals(Math.round(3.5f * bar.getResources().getDisplayMetrics().density),
            tabs.getChildAt(1).getPaddingLeft());
        assertEquals(tabs.getChildAt(1).getPaddingLeft(), tabs.getChildAt(1).getPaddingRight());

        tabs.getChildAt(0).performClick();
        assertEquals(0, selected.get());
        tabs.getChildAt(2).performClick();
        assertEquals(1, created.get());
        assertEquals(null, tabs.getChildAt(2).getBackground());
    }

    @Test
    public void nullSession_usesStableWindowNumber() {
        TerminalWindowBar.WindowItem item = TerminalWindowBar.itemFor(null, 2);
        assertEquals("window 3", item.spokenLabel);
        assertTrue(item.label.endsWith(" 3"));
    }

    @Test
    public void middleEllipsize_preservesMeaningfulEnds() {
        assertEquals("verylong…name", TerminalWindowBar.middleEllipsize("verylongfoldername", 13));
    }

    @Test
    public void surfaceStyle_updatesTabsWithStatusBarCornerRadius() {
        TerminalWindowBar bar = new TerminalWindowBar(ApplicationProvider.getApplicationContext(), null);
        bar.setWindows(Arrays.asList(new TerminalWindowBar.WindowItem("home", "home")), 0);
        bar.setSurfaceStyle(false, 40f);
        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        assertEquals(0f,
            ((GradientDrawable) tabs.getChildAt(0).getBackground()).getCornerRadius(), .01f);

        bar.setSurfaceStyle(true, 40f);
        tabs = (LinearLayout) bar.getChildAt(0);
        assertEquals(40f,
            ((GradientDrawable) tabs.getChildAt(0).getBackground()).getCornerRadius(), .01f);
    }

    @Test
    public void selectionChange_reusesStationaryTabsInsteadOfWigglingSelectedLabel() {
        TerminalWindowBar bar = new TerminalWindowBar(ApplicationProvider.getApplicationContext(), null);
        java.util.List<TerminalWindowBar.WindowItem> items = Arrays.asList(
            new TerminalWindowBar.WindowItem("home", "home"),
            new TerminalWindowBar.WindowItem("work", "work"),
            new TerminalWindowBar.WindowItem("ssh", "ssh"));
        bar.setWindows(items, 0);
        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        android.view.View second = tabs.getChildAt(1);

        bar.setWindows(items, 1);

        assertSame(second, tabs.getChildAt(1));
        assertEquals(0f, tabs.getChildAt(1).getTranslationX(), .01f);
        assertEquals(1f, tabs.getChildAt(1).getAlpha(), .01f);
        assertTrue(tabs.getChildAt(1).isSelected());
        assertFalse(tabs.getChildAt(0).isSelected());
        assertEquals(320L, TerminalWindowBar.WINDOW_SWITCH_ANIMATION_DURATION_MS);
    }
}
