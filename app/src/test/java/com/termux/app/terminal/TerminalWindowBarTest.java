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
    public void surfaceStyle_rebuildsTabsWithStatusBarCornerRadius() {
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
}
