package com.termux.app.wall;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.res.Resources;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The Terminal page's one card in Edit pages: its name says how many windows it holds. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PageOverviewTerminalCardTest {

    private static Resources resources() {
        return RuntimeEnvironment.getApplication().getResources();
    }

    @Test
    public void oneWindowOrNoneIsJustTerminal() {
        assertEquals("Terminal", PageOverviewView.terminalLabel(resources(), 1));
        assertEquals("Terminal", PageOverviewView.terminalLabel(resources(), 0));
    }

    @Test
    public void moreThanOneWindowSaysHowMany() {
        assertEquals("Terminal, 4 windows", PageOverviewView.terminalLabel(resources(), 4));
        assertEquals("Terminal, 2 windows", PageOverviewView.terminalLabel(resources(), 2));
    }
}
