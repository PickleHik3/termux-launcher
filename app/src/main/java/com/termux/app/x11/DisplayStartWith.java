package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The display's Start with choice (#166): nothing, one distro's XFCE desktop, or a command, run
 * when the display comes up — whatever brought it up — and never twice for one server start.
 *
 * <p>"Once per server" is kept in a marker file in the prefix's tmp (emptied at boot) holding the
 * identity of the server it was last run for ({@link X11ServerProcesses#runningServerIdentity}).
 * A file and not a field, because the display window closing and reopening, or the launcher's
 * process being restarted, both report the same server as newly running.
 *
 * <p>{@link #parse} and {@link #decide} are pure and tested; the rest is file work for a worker
 * thread.
 */
public final class DisplayStartWith {

    /** The stored value for nothing. */
    public static final String VALUE_NOTHING = "";
    /** The stored value for the command in its own key. */
    public static final String VALUE_COMMAND = "command";
    /** What a stored desktop choice starts with, before the distro's name. */
    public static final String DESKTOP_PREFIX = "desktop:";

    /** Where XFCE's session starter is inside a distro. */
    static final String XFCE_STARTER = "/usr/bin/startxfce4";

    /** The server the choice was last run for. */
    static final String MARKER_PATH =
        TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/termux-x11-start-with.server";

    private DisplayStartWith() {}

    /** What kind of thing the user chose. */
    public enum Kind { NOTHING, DESKTOP, COMMAND }

    /** The choice as stored, read. */
    public static final class Choice {
        @NonNull public final Kind kind;
        /** The distro's proot-distro name for {@link Kind#DESKTOP}; empty otherwise. */
        @NonNull public final String distro;
        /** The shell command for {@link Kind#COMMAND}; empty otherwise. */
        @NonNull public final String command;

        Choice(@NonNull Kind kind, @NonNull String distro, @NonNull String command) {
            this.kind = kind;
            this.distro = distro;
            this.command = command;
        }
    }

    /**
     * The choice from its two stored values. A desktop with no distro named, or a command left
     * empty, is nothing: there is nothing to start. Pure.
     */
    @NonNull
    public static Choice parse(@Nullable String stored, @Nullable String command) {
        String value = stored == null ? "" : stored.trim();
        if (value.startsWith(DESKTOP_PREFIX)) {
            String distro = value.substring(DESKTOP_PREFIX.length()).trim();
            if (!distro.isEmpty()) return new Choice(Kind.DESKTOP, distro, "");
        } else if (VALUE_COMMAND.equals(value)) {
            String line = command == null ? "" : command.trim();
            if (!line.isEmpty()) return new Choice(Kind.COMMAND, "", line);
        }
        return new Choice(Kind.NOTHING, "", "");
    }

    /** The stored value for {@code distro}'s desktop. */
    @NonNull
    public static String desktopValue(@NonNull String distro) {
        return DESKTOP_PREFIX + distro;
    }

    /** What to do now that a display is up. */
    public enum Action { NONE, START_DESKTOP, START_COMMAND }

    /** {@link #decide}'s answer. */
    public static final class Decision {
        @NonNull public final Action action;
        /** Whether this server is to be marked as handled, whatever the action. */
        public final boolean mark;

        Decision(@NonNull Action action, boolean mark) {
            this.action = action;
            this.mark = mark;
        }
    }

    /**
     * Whether to start {@code choice} on a display that has just been reported running.
     * <ul>
     *   <li>Nothing chosen: nothing, and the server is not marked, so a choice made while it runs
     *       still applies the next time its window opens.</li>
     *   <li>No server found ({@code serverId} null): nothing. Without a name for this start there
     *       is no way to keep it to once, and never twice beats once.</li>
     *   <li>{@code serverId} is the marked one: it was already handled. Nothing.</li>
     *   <li>A desktop chosen while a desktop is already on the display or on its way
     *       ({@code desktopOnDisplay}): marked, not started; two desktops on one display fight.</li>
     *   <li>Otherwise: started, and marked first, so a failing start is never tried again.</li>
     * </ul>
     * Pure.
     */
    @NonNull
    public static Decision decide(@NonNull Choice choice, @Nullable String serverId,
                                  @Nullable String markedServerId, boolean desktopOnDisplay) {
        if (choice.kind == Kind.NOTHING) return new Decision(Action.NONE, false);
        if (serverId == null || serverId.isEmpty()) return new Decision(Action.NONE, false);
        if (serverId.equals(markedServerId)) return new Decision(Action.NONE, false);
        if (choice.kind == Kind.DESKTOP) {
            return new Decision(desktopOnDisplay ? Action.NONE : Action.START_DESKTOP, true);
        }
        return new Decision(Action.START_COMMAND, true);
    }

    /** How a distro is named to the user: {@code debian} reads "Debian". Pure. */
    @NonNull
    public static String distroLabel(@NonNull String distro) {
        switch (distro) {
            case "archlinux": return "Arch Linux";
            case "opensuse": return "openSUSE";
            case "rockylinux": return "Rocky Linux";
            case "almalinux": return "AlmaLinux";
            case "void": return "Void Linux";
            default:
                if (distro.isEmpty()) return distro;
                return distro.substring(0, 1).toUpperCase(Locale.ROOT) + distro.substring(1);
        }
    }

    /** Whether {@code container} has XFCE installed. File work. */
    public static boolean hasXfce(@NonNull ProotDistro.Container container) {
        File starter = container.inside(XFCE_STARTER);
        if (starter.exists()) return true;
        // An absolute symlink inside the rootfs dangles from Android's side; its name is enough.
        String[] names = starter.getParentFile() == null ? null : starter.getParentFile().list();
        if (names == null) return false;
        for (String name : names) if (starter.getName().equals(name)) return true;
        return false;
    }

    /** The names of the installed distros that have XFCE, in {@code containers}' order. File work. */
    @NonNull
    public static List<String> distrosWithDesktop(@NonNull List<ProotDistro.Container> containers) {
        List<String> names = new ArrayList<>();
        for (ProotDistro.Container container : containers) {
            if (container.kind == ProotDistro.Container.Kind.DISTRO && hasXfce(container)) {
                names.add(container.name);
            }
        }
        return Collections.unmodifiableList(names);
    }

    /**
     * {@code container}'s XFCE desktop as the drawer lists it — so a tap on that entry while it
     * runs only shows it — or one built to the same id when its session file is missing. File work.
     */
    @NonNull
    static LinuxAppCatalog.LinuxApp desktopApp(@NonNull ProotDistro.Container container) {
        List<LinuxAppCatalog.Root> sessions = new ArrayList<>();
        for (LinuxAppCatalog.Root root : LinuxAppCatalog.rootsOf(container)) {
            if (root.sessions) sessions.add(root);
        }
        for (LinuxAppCatalog.LinuxApp app : LinuxAppCatalog.scan(sessions)) {
            if (app.session && "startxfce4".equals(LinuxAppCatalog.execBinary(app.exec))) return app;
        }
        return new LinuxAppCatalog.LinuxApp(container, "xfce", "Xfce Session", "startxfce4", "",
            "", "", false, true, "XFCE");
    }

    /** The command as an app the prefix runs on the display, in its own shell. Pure. */
    @NonNull
    static LinuxAppCatalog.LinuxApp commandApp(@NonNull String command) {
        return new LinuxAppCatalog.LinuxApp(ProotDistro.Container.PREFIX, "start-with",
            "Start with", "sh -c " + ProotDistro.singleQuote(command), "", "", "", false);
    }

    /** The server last handled, or null. File work. */
    @Nullable
    static String readMarker() {
        return readMarker(new File(MARKER_PATH));
    }

    @Nullable
    static String readMarker(@NonNull File file) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            byte[] bytes = new byte[256];
            int read = in.read(bytes);
            if (read <= 0) return null;
            String value = new String(bytes, 0, read, StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? null : value;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Marks {@code serverId} as handled. Best effort: a failed write is only a lost marker. File work. */
    static void writeMarker(@NonNull String serverId) {
        writeMarker(new File(MARKER_PATH), serverId);
    }

    static void writeMarker(@NonNull File file, @NonNull String serverId) {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(serverId.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) {
            // Nothing to do: at worst the choice runs again for this server.
        }
    }
}
