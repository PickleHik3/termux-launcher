package com.termux.app.x11;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.termux.shared.termux.TermuxConstants;

import org.junit.Test;

/** The start command: the user's line first, the settings' flags after, never doubled. */
public class X11StartCommandTest {

    private static final String BIN = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;

    @Test public void theBareDefaultResolvesIntoThePrefix() {
        assertArrayEquals(new String[]{BIN + "/termux-x11", ":0"},
            X11StartCommand.argv("termux-x11 :0", 0, false, false, null));
    }

    @Test public void theSettingsBecomeServerFlagsAfterTheUsersOwn() {
        assertArrayEquals(new String[]{BIN + "/termux-x11", ":0", "-ac", "-dpi", "200",
                "-legacy-drawing", "-force-bgra"},
            X11StartCommand.argv("termux-x11 :0 -ac", 200, true, true, null));
    }

    @Test public void aFlagTheUserAlreadyWroteIsNeitherDoubledNorOverridden() {
        assertArrayEquals(new String[]{BIN + "/termux-x11", ":1", "-dpi", "96", "-legacy-drawing"},
            X11StartCommand.argv("termux-x11 :1 -dpi 96 -legacy-drawing", 240, true, false, null));
    }

    @Test public void theWindowManagerRidesAsTheServersStartupCommand() {
        assertArrayEquals(new String[]{BIN + "/termux-x11", ":0", "-xstartup", "openbox --config-file /x/rc.xml"},
            X11StartCommand.argv("termux-x11 :0", 0, false, false, " openbox --config-file /x/rc.xml "));
        // A user who wrote their own -xstartup keeps it.
        assertArrayEquals(new String[]{BIN + "/termux-x11", ":0", "-xstartup", "xfce4-session"},
            X11StartCommand.argv("termux-x11 :0 -xstartup xfce4-session", 0, false, false, "openbox"));
    }

    @Test public void anAbsoluteExecutableIsLeftAlone() {
        assertEquals("/data/local/x11", X11StartCommand.argv("/data/local/x11 :0", 0, false, false, null)[0]);
    }

    @Test public void aBlankCommandIsNothingToRun() {
        assertEquals(0, X11StartCommand.argv("   ", 100, true, true, "openbox").length);
    }
}
