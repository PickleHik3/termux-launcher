package com.termux.app.setup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.AppEdition;
import com.termux.app.x11.GuiAppsSetup;

import java.util.ArrayList;
import java.util.List;

/** The Display card's plain decisions: which routes it offers, what it runs, how a run reads. */
final class DisplayCardLogic {

    private DisplayCardLogic() {}

    /** Longest progress line, in characters; longer ones end in an ellipsis. */
    static final int PROGRESS_MAX_CHARS = 90;
    /** How many of the transcript's last lines the log peek keeps. */
    static final int LOG_LINES = 200;

    /**
     * Whether the wizard can run the distro route itself. It can because the page asks for the
     * username and password up front and the command takes them as literals (no
     * {@code read}, no interactive {@code passwd}); the run is headless and read-only.
     */
    static final boolean DISTRO_RUNNABLE = true;

    static final String DEFAULT_USER = "user";
    static final int USER_MAX_CHARS = 32;
    static final int PASSWORD_MIN_CHARS = 4;

    /** Lowercase letters, digits and underscores, not starting with a digit (useradd refuses that). */
    static boolean isValidUser(@Nullable String user) {
        if (user == null || user.isEmpty() || user.length() > USER_MAX_CHARS) return false;
        for (int i = 0; i < user.length(); i++) {
            char c = user.charAt(i);
            boolean letter = c >= 'a' && c <= 'z';
            boolean digit = c >= '0' && c <= '9';
            if (!(letter || c == '_' || (digit && i > 0))) return false;
        }
        return true;
    }

    /** At least {@link #PASSWORD_MIN_CHARS} characters and one line (chpasswd reads lines). */
    static boolean isValidPassword(@Nullable String password) {
        return password != null && password.length() >= PASSWORD_MIN_CHARS
            && password.indexOf('\n') < 0 && password.indexOf('\r') < 0;
    }

    /** What stands in for the password where it would show. */
    static final String REDACTED = "••••";

    /** {@code text} with every occurrence of {@code secret} replaced; unchanged when there is none. */
    @NonNull
    static String redact(@NonNull String text, @Nullable String secret) {
        if (secret == null || secret.isEmpty()) return text;
        return text.replace(secret, REDACTED);
    }

    /** The routes the card offers {@code edition}: the ones it has and the wizard can run. */
    @NonNull
    static List<GuiAppsSetup.Route> offeredRoutes(@NonNull AppEdition edition) {
        List<GuiAppsSetup.Route> out = new ArrayList<>();
        for (GuiAppsSetup.Route route : GuiAppsSetup.routesFor(edition)) {
            if (route == GuiAppsSetup.Route.DISTRO && !DISTRO_RUNNABLE) continue;
            out.add(route);
        }
        return out;
    }

    /** The stored route when it is still offered, else the first offered one; null when none. */
    @Nullable
    static GuiAppsSetup.Route resolveRoute(@Nullable String stored,
                                           @NonNull List<GuiAppsSetup.Route> offered) {
        for (GuiAppsSetup.Route route : offered) if (route.key.equals(stored)) return route;
        return offered.isEmpty() ? null : offered.get(0);
    }

    /** The command a run executes: the one the guide would copy, with the default starter apps. */
    @NonNull
    static String command(@NonNull GuiAppsSetup.Route route, @NonNull GuiAppsSetup.Distro distro,
                          @NonNull AppEdition edition, @Nullable String user,
                          @Nullable String password) {
        return GuiAppsSetup.command(route, distro, GuiAppsSetup.defaultStarters(), edition,
            route == GuiAppsSetup.Route.DISTRO ? user : null,
            route == GuiAppsSetup.Route.DISTRO ? password : null);
    }

    /**
     * What the script puts before the command so no package question can stall the run: the
     * non-interactive frontend, and {@code pkg install} that keeps the new version of a config file.
     */
    static final String SCRIPT_PRELUDE =
        "export DEBIAN_FRONTEND=noninteractive\n"
            + "pkg() {\n"
            + "  if [ \"$1\" = install ]; then\n"
            + "    shift\n"
            + "    command pkg install -y -o Dpkg::Options::=--force-confnew \"$@\"\n"
            + "  else\n"
            + "    command pkg \"$@\"\n"
            + "  fi\n"
            + "}\n";

    /** The script file's text: a bash shebang (resolved to the prefix's bash), the prelude, the command. */
    @NonNull
    static String script(@NonNull String command) {
        return "#!/bin/bash\n" + SCRIPT_PRELUDE + command + "\n";
    }

    /** A setup run counts as done when it exited 0 and its last step left the marker file. */
    static boolean succeeded(int exitStatus, boolean markerExists) {
        return exitStatus == 0 && markerExists;
    }

    /**
     * The last non-empty line of {@code transcript}, trimmed to one line. A carriage return
     * inside a line keeps what was written last, as a terminal shows it.
     */
    @NonNull
    static String progressLine(@Nullable String transcript) {
        if (transcript == null) return "";
        String[] lines = transcript.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lastSegment(lines[i]).trim();
            if (line.isEmpty()) continue;
            if (line.length() > PROGRESS_MAX_CHARS) {
                return line.substring(0, PROGRESS_MAX_CHARS - 1).trim() + "…";
            }
            return line;
        }
        return "";
    }

    /** The last {@code count} lines of {@code transcript}, without the empty tail the grid pads with. */
    @NonNull
    static String lastLines(@Nullable String transcript, int count) {
        if (transcript == null || count <= 0) return "";
        String[] lines = transcript.split("\n", -1);
        int end = lines.length;
        while (end > 0 && lastSegment(lines[end - 1]).trim().isEmpty()) end--;
        int start = Math.max(0, end - count);
        StringBuilder out = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (out.length() > 0) out.append('\n');
            out.append(lastSegment(lines[i]).replaceAll("\\s+$", ""));
        }
        return out.toString();
    }

    @NonNull
    private static String lastSegment(@NonNull String line) {
        int cr = line.lastIndexOf('\r');
        if (cr < 0) return line;
        String tail = line.substring(cr + 1);
        // A line that ends in a bare return keeps what came before it.
        return tail.isEmpty() ? lastSegment(line.substring(0, cr)) : tail;
    }
}
