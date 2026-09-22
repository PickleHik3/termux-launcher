package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Linux apps installed in the prefix and in every {@code proot-distro} container, read from
 * their {@code .desktop} files the way any desktop's menu reads them. The launcher lists these in
 * its app drawer beside Android apps; a tap runs the app on the display, or in a terminal pane for
 * one marked {@code Terminal=true} ({@link LinuxApp#terminal}).
 *
 * <p>Whole desktop environments are read the same way, out of {@code share/xsessions} beside the
 * applications, and carry {@link LinuxApp#session}: one tap takes the display for as long as the
 * desktop runs. Wayland sessions are not read — none of them can run on an X11 display.
 *
 * <p>Pure file reading, so it is tested against fixture files. Nothing here touches the display
 * or a pane.
 */
public final class LinuxAppCatalog {

    /** One directory of desktop files, and whose it is. */
    public static final class Root {
        @NonNull public final File dir;
        @NonNull public final ProotDistro.Container container;
        /**
         * This directory holds whole-desktop sessions ({@code share/xsessions}), not applications.
         * The directory is the only reliable signal there is: a desktop's own entry says
         * {@code Type=Application} as often as not — eleven of the nineteen in Termux's x11
         * repository do — so nothing inside the file tells the two apart.
         */
        public final boolean sessions;

        public Root(@NonNull File dir, @NonNull ProotDistro.Container container) {
            this(dir, container, false);
        }

        public Root(@NonNull File dir, @NonNull ProotDistro.Container container, boolean sessions) {
            this.dir = dir;
            this.container = container;
            this.sessions = sessions;
        }
    }

    /** One launchable entry: what to show and what to run. */
    public static final class LinuxApp {
        /**
         * The launcher-wide id — the desktop file's name for a prefix app, that name qualified by
         * its container for one installed in a distro ({@link X11Apps#qualify}). A session's
         * carries {@link X11Apps#SESSION_PREFIX} inside the desktop-file part
         * ({@link X11Apps#qualifySession}), so a desktop and an application of the same basename
         * in the same container are two entries rather than one. Stable across reinstalls, and
         * what pins and rankings are keyed on.
         */
        @NonNull public final String id;
        /** The desktop file's own name without its extension, unqualified. */
        @NonNull public final String desktopFile;
        /** Where this app lives: the prefix, or one distro container. */
        @NonNull public final ProotDistro.Container container;
        /** What the app calls itself. The container is never part of it. */
        @NonNull public final String name;
        /**
         * The Exec line with its field codes ({@code %f %u …}) removed; a shell command line, in
         * the app's own world. {@link #command()} is what a host shell runs.
         */
        @NonNull public final String exec;
        /** The Icon key: a theme icon name or an absolute path, or empty. */
        @NonNull public final String icon;
        @NonNull public final String comment;
        /**
         * The {@code StartupWMClass} key: what this app sets as the class part of its windows'
         * {@code WM_CLASS} when that is not simply its desktop-file name. Empty when unset. This
         * is how a window on the display is traced back to the app that opened it.
         */
        @NonNull public final String startupWmClass;
        /**
         * {@code Terminal=true} (D5): a command-line program with a menu entry rather than a
         * window of its own — {@code htop}, {@code ranger}, a distro's package-manager front end.
         * Shown in the drawer like any other entry, but a tap opens it in a terminal pane instead
         * of on the display; see {@link com.termux.app.x11.LinuxTerminalAppRunner}.
         */
        public final boolean terminal;
        /**
         * A whole desktop environment rather than an app: one tap takes the display for as long as
         * it runs. True exactly when the entry was read from a session root
         * ({@link Root#sessions}); the {@code Type} value never says.
         */
        public final boolean session;
        /**
         * The {@code DesktopNames} key: what this session calls itself to the programs it starts
         * — {@code XFCE}, {@code LXQt}, {@code MATE} — and so what belongs in
         * {@code XDG_CURRENT_DESKTOP}. Empty when the file names none, which nine of the nineteen
         * Termux session files do. Always empty for an application.
         */
        @NonNull public final String desktopNames;

        LinuxApp(@NonNull String desktopFile, @NonNull String name, @NonNull String exec,
                 @NonNull String icon, @NonNull String comment) {
            this(desktopFile, name, exec, icon, comment, "", false);
        }

        LinuxApp(@NonNull String desktopFile, @NonNull String name, @NonNull String exec,
                 @NonNull String icon, @NonNull String comment, @NonNull String startupWmClass) {
            this(desktopFile, name, exec, icon, comment, startupWmClass, false);
        }

        LinuxApp(@NonNull String desktopFile, @NonNull String name, @NonNull String exec,
                 @NonNull String icon, @NonNull String comment, @NonNull String startupWmClass,
                 boolean terminal) {
            this(ProotDistro.Container.PREFIX, desktopFile, name, exec, icon, comment,
                startupWmClass, terminal);
        }

        LinuxApp(@NonNull ProotDistro.Container container, @NonNull String desktopFile,
                 @NonNull String name, @NonNull String exec, @NonNull String icon,
                 @NonNull String comment, @NonNull String startupWmClass, boolean terminal) {
            this(container, desktopFile, name, exec, icon, comment, startupWmClass, terminal,
                false, "");
        }

        LinuxApp(@NonNull ProotDistro.Container container, @NonNull String desktopFile,
                 @NonNull String name, @NonNull String exec, @NonNull String icon,
                 @NonNull String comment, @NonNull String startupWmClass, boolean terminal,
                 boolean session, @NonNull String desktopNames) {
            this.container = container;
            this.desktopFile = desktopFile;
            this.id = session ? X11Apps.qualifySession(container.name, desktopFile)
                : X11Apps.qualify(container.name, desktopFile);
            this.name = name;
            this.exec = exec;
            this.icon = icon;
            this.comment = comment;
            this.startupWmClass = startupWmClass;
            this.terminal = terminal;
            this.session = session;
            this.desktopNames = desktopNames;
        }

        /**
         * The shell line that runs this app: its own command for a prefix app, that command inside
         * a {@code proot-distro login} for one in a container.
         */
        @NonNull
        public String command() {
            return commandWith("");
        }

        /**
         * {@link #command()} with {@code extraArgs} appended to the app's own command — the same
         * wrapping, one more flag, for a caller that has to run the app a second way.
         */
        @NonNull
        public String commandWith(@NonNull String extraArgs) {
            String command = extraArgs.isEmpty() ? exec : exec + " " + extraArgs;
            return ProotDistro.loginCommand(container, command);
        }
    }

    private LinuxAppCatalog() {}

    /**
     * Every directory of desktop files worth reading — applications and whole-desktop sessions
     * alike: the prefix's, then each installed container's. Re-enumerated on every call, so a
     * distro installed a moment ago is simply there — nothing is watched and nothing is
     * remembered.
     */
    @NonNull
    public static List<Root> roots() {
        List<Root> roots = new ArrayList<>(rootsOf(ProotDistro.Container.PREFIX));
        ProotDistro.Container nix = ProotDistro.nixProfile();
        if (nix != null) roots.addAll(rootsOf(nix));
        for (ProotDistro.Container container : ProotDistro.installed()) {
            roots.addAll(rootsOf(container));
        }
        return roots;
    }

    /**
     * One container's directories, as roots: its applications first, then its whole-desktop
     * sessions. A session's id carries its own marker, so the two kinds never shadow each other
     * however they are ordered.
     */
    @NonNull
    public static List<Root> rootsOf(@NonNull ProotDistro.Container container) {
        List<Root> roots = new ArrayList<>(5);
        for (File dir : container.applicationDirs()) roots.add(new Root(dir, container, false));
        for (File dir : container.sessionDirs()) roots.add(new Root(dir, container, true));
        return roots;
    }

    /** Plain directories read as the prefix's, for a caller that has its own list of them. */
    @NonNull
    public static List<Root> prefixRoots(@NonNull List<File> dirs) {
        List<Root> roots = new ArrayList<>(dirs.size());
        for (File dir : dirs) roots.add(new Root(dir, ProotDistro.Container.PREFIX));
        return roots;
    }

    /**
     * Every launchable app in {@code roots}, sorted by name; a later root does not shadow an
     * earlier id. Ids carry their container, so the same desktop file in the prefix and in two
     * distros is three entries, not one.
     */
    @NonNull
    public static List<LinuxApp> scan(@NonNull List<Root> roots) {
        List<LinuxApp> apps = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Root root : roots) {
            File[] files = root.dir.listFiles((d, name) -> name.endsWith(".desktop"));
            if (files == null) continue;
            for (File file : files) {
                String desktopFile =
                    file.getName().substring(0, file.getName().length() - ".desktop".length());
                String id = root.sessions
                    ? X11Apps.qualifySession(root.container.name, desktopFile)
                    : X11Apps.qualify(root.container.name, desktopFile);
                if (seen.contains(id)) continue;
                LinuxApp app = parse(root.container, desktopFile, file, root.sessions);
                if (app == null) continue;
                seen.add(id);
                apps.add(app);
            }
        }
        // Two distros can both call an app Firefox; the id breaks the tie so the order is stable.
        Collections.sort(apps, (a, b) -> {
            int byName = a.name.compareToIgnoreCase(b.name);
            return byName != 0 ? byName : a.id.compareTo(b.id);
        });
        return apps;
    }

    /**
     * A cheap fingerprint of {@code roots} — their modification times — so a caller can tell that
     * something was installed or removed without reading every file. A container that appears or
     * goes brings its roots with it, so installing a whole distro moves this too.
     */
    public static long signature(@NonNull List<Root> roots) {
        long signature = 0L;
        for (Root root : roots) {
            signature = signature * 31 + root.container.name.hashCode();
            // The path itself, not only its contents: a nix switch leaves the same desktop files
            // in a directory of a different name, and that new name is the whole of the news.
            signature = signature * 31 + root.dir.getPath().hashCode();
            String[] names = root.dir.list();
            boolean present = names != null;
            // A directory that is not there yet is a reading of its own, and is kept well apart
            // from anything a real one can give back — `lastModified` answers 0 for an absent
            // file, which an existing directory could in principle answer too. Every candidate
            // directory is hashed whether or not it exists, so the one that appears when a user
            // writes their first desktop file by hand moves this number.
            signature = signature * 31 + (present ? root.dir.lastModified() : ABSENT);
            signature = signature * 31 + (present ? names.length : ABSENT);
        }
        return signature;
    }

    /** What an absent directory contributes: a value no reading of a real one can collide with. */
    private static final long ABSENT = Long.MIN_VALUE / 2;

    /**
     * Read one desktop file. Null when it is not an application, asks not to be shown ({@code
     * NoDisplay}/{@code Hidden} — the author saying "not in a menu"), or names a binary that is
     * not installed. {@code Terminal=true} (D5) is kept and carried on {@link LinuxApp#terminal}
     * rather than dropped: it is a real menu entry that wants a terminal pane instead of a window.
     *
     * <p>{@code sessions} says which kind of directory this file came out of, because nothing in
     * the file does. In a session root two things change and nothing else:
     * <ul>
     *   <li>the type test widens. {@code Type=XSession} is a display-manager convention the
     *       Desktop Entry Specification does not define, and a missing {@code Type} is just as
     *       common (wmaker ships none), so both are accepted alongside {@code Type=Application}.</li>
     *   <li>the installed test tightens (D7). The {@code TryExec} test stays, and the first word
     *       of {@code Exec} must resolve too — cinnamon's {@code TryExec} names a binary that is
     *       there while its {@code Exec} names one that is not, so a resolving {@code TryExec} is
     *       no proof the session will run.</li>
     * </ul>
     * An application root keeps today's rules byte for byte: nothing that appears in the drawer
     * now stops appearing.
     */
    @Nullable
    static LinuxApp parse(@NonNull ProotDistro.Container container, @NonNull String desktopFile,
                          @NonNull File file, boolean sessions) {
        String type = "", name = "", exec = "", icon = "", comment = "", tryExec = "";
        String startupWmClass = "", desktopNames = "";
        boolean noDisplay = false, hidden = false, terminal = false, inEntry = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(container.readable(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[")) {
                    inEntry = line.equals("[Desktop Entry]");
                    continue;
                }
                if (!inEntry) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                switch (key) {
                    case "Type": type = value; break;
                    case "Name": name = value; break;
                    case "Exec": exec = value; break;
                    case "TryExec": tryExec = value; break;
                    case "Icon": icon = value; break;
                    case "Comment": comment = value; break;
                    case "StartupWMClass": startupWmClass = value; break;
                    case "DesktopNames": desktopNames = value; break;
                    case "NoDisplay": noDisplay = "true".equalsIgnoreCase(value); break;
                    case "Hidden": hidden = "true".equalsIgnoreCase(value); break;
                    case "Terminal": terminal = "true".equalsIgnoreCase(value); break;
                    default: break;
                }
            }
        } catch (IOException e) {
            return null;
        }
        if (!typeIsLaunchable(type, sessions) || name.isEmpty() || exec.isEmpty()) return null;
        if (noDisplay || hidden) return null;
        if (!tryExec.isEmpty() && !executableExists(container, tryExec, file)) return null;
        String command = stripFieldCodes(exec);
        if (sessions) {
            // D7, session roots only: the command itself has to be there, not only the TryExec
            // that may or may not be beside it.
            String binary = execBinary(command);
            if (binary.isEmpty() || !executableExists(container, binary, file)) return null;
        }
        return new LinuxApp(container, desktopFile, name, command, icon, comment,
            startupWmClass, terminal, sessions, sessions ? desktopNames : "");
    }

    /**
     * Whether a {@code Type} value is one this root can launch. An application root takes
     * {@code Application} and nothing else, exactly as it always has. A session root also takes
     * {@code XSession} — the display managers' convention, used by seven of Termux's nineteen
     * session files — and a file with no {@code Type} at all, which wmaker ships.
     */
    private static boolean typeIsLaunchable(@NonNull String type, boolean sessions) {
        if ("Application".equals(type)) return true;
        return sessions && ("XSession".equals(type) || type.isEmpty());
    }

    /**
     * The binary an {@code Exec} line runs: its first word, once the field codes are gone. A word
     * the desktop file quoted because its path has spaces in it is unquoted here, since what comes
     * back is a path to look up and not a shell word any more.
     */
    @NonNull
    static String execBinary(@NonNull String exec) {
        String first = exec.trim().split("\\s", 2)[0];
        if (first.length() >= 2 && first.startsWith("\"") && first.endsWith("\"")) {
            first = first.substring(1, first.length() - 1);
        }
        return first;
    }

    /** Whether {@code binary} — a {@code TryExec} value, or an {@code Exec}'s first word — is there. */
    private static boolean executableExists(@NonNull ProotDistro.Container container,
                                            @NonNull String binary, @NonNull File desktopFile) {
        // Absolute inside a container means absolute in the container's world, not Android's.
        if (binary.startsWith("/")) return container.inside(binary).canExecute();
        if (container.kind == ProotDistro.Container.Kind.NIX) {
            // A bare name is resolved by the nix session's own PATH when the app runs; from here
            // the profile's bin is where that PATH would find it.
            File profile = container.profile;
            return profile != null && container.under(profile, "bin/" + binary).canExecute();
        }
        if (!container.isPrefix()) {
            return container.inside("/usr/bin/" + binary).canExecute()
                || container.inside("/usr/local/bin/" + binary).canExecute();
        }
        // Relative: the prefix's bin, or — for fixtures — the directory beside the desktop file.
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, binary).canExecute()
            || new File(desktopFile.getParentFile(), binary).canExecute();
    }

    /**
     * Drop the desktop-entry field codes: {@code %f %F %u %U %d %D %n %N %i %c %k %v %m} and the
     * literal {@code %%}. The launcher opens no files, so every code is an empty expansion.
     */
    @NonNull
    static String stripFieldCodes(@NonNull String exec) {
        StringBuilder out = new StringBuilder(exec.length());
        for (int i = 0; i < exec.length(); i++) {
            char c = exec.charAt(i);
            if (c == '%' && i + 1 < exec.length()) {
                char code = exec.charAt(i + 1);
                if (code == '%') {
                    out.append('%');
                } else if ("fFuUdDnNickvm".indexOf(code) < 0) {
                    out.append(c).append(code);
                }
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString().trim().replaceAll("\\s{2,}", " ");
    }

    /** Case-insensitive lookup by id. */
    @Nullable
    public static LinuxApp find(@NonNull List<LinuxApp> apps, @NonNull String id) {
        for (LinuxApp app : apps) {
            if (app.id.equalsIgnoreCase(id)) return app;
        }
        return null;
    }

    @NonNull
    static String lower(@NonNull String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
