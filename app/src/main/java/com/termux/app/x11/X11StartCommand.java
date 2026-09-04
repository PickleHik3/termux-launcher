package com.termux.app.x11;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The command line the launcher runs when it starts a display itself — the page's button, the
 * long-press menu and the start-up opt-in. The user's own command comes first, exactly as typed;
 * the settings that map to server flags are appended unless the command already carries them, so
 * a hand-written {@code -dpi 200} is never doubled and never overridden.
 *
 * <p>Pure, so the composition is tested without a shell.
 */
public final class X11StartCommand {

    private X11StartCommand() {}

    /**
     * @param command      the configured command line, e.g. {@code termux-x11 :0 -ac}
     * @param dpi          the screen's dots per inch, or 0 to leave it to the server
     * @param legacyDrawing pass {@code -legacy-drawing}
     * @param forceBgra    pass {@code -force-bgra}
     * @return argv, with the executable resolved into the prefix when it carries no path; empty
     *         when the command is blank
     */
    @NonNull
    public static String[] argv(@NonNull String command, int dpi, boolean legacyDrawing,
                                boolean forceBgra) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return new String[0];
        List<String> argv = new ArrayList<>(Arrays.asList(trimmed.split("\\s+")));
        if (!argv.get(0).contains("/")) {
            argv.set(0, TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/" + argv.get(0));
        }
        if (dpi > 0 && !argv.contains("-dpi")) {
            argv.add("-dpi");
            argv.add(String.valueOf(dpi));
        }
        if (legacyDrawing && !argv.contains("-legacy-drawing")) argv.add("-legacy-drawing");
        if (forceBgra && !argv.contains("-force-bgra")) argv.add("-force-bgra");
        return argv.toArray(new String[0]);
    }
}
