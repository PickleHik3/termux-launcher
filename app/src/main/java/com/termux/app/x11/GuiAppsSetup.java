package com.termux.app.x11;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The one command the "Get GUI apps" screen hands the user. Two routes to a graphical app on this
 * phone, and the shell line that takes each of them.
 *
 * <p>Nothing here runs anything and nothing here touches Android: a route, a distro and a handful
 * of ticked apps go in, one string comes out, and every branch of it is read back by a test. The
 * screen copies that string to the clipboard and the user pastes it into the terminal — which is
 * the whole point of the flow, because everything the command does (fetching a few hundred
 * megabytes, asking for a password) is better watched in a terminal than hidden behind a
 * progress bar.
 *
 * <h3>The two routes</h3>
 *
 * <ul>
 *   <li><b>Termux's X11 apps.</b> One extra repository and the apps themselves, built for this
 *       phone. Small and quick, and the apps are the ones Termux packages.
 *   <li><b>A full Linux inside.</b> A whole distro under {@code proot-distro}, with the distro's
 *       own package manager and its own far larger catalogue. Slower to set up and it asks for an
 *       account name and a password, because a desktop app expects to be an ordinary user.
 * </ul>
 *
 * <h3>Package names</h3>
 *
 * Checked against the live indexes on 2026-09-20: Termux's {@code x11} repository for route one,
 * Debian trixie, Ubuntu noble and Arch's own package search for route two. A name that is wrong
 * is a command that stops halfway with the user watching, so they are written down here rather
 * than guessed at per distro.
 */
public final class GuiAppsSetup {

    /** The file the script writes the chosen account name into, beside the container's rootfs. */
    public static final String RECORD_FILE_NAME = "launcher-user";

    /** Where {@code proot-distro} 5.x keeps its containers, as a Termux shell writes it. */
    private static final String CONTAINERS_PATH = "$PREFIX/var/lib/proot-distro/containers";

    /** The one line the whole thing ends on, so the user knows it worked and where to look. */
    public static final String DONE_LINE =
        "Done. Your apps appear in the app drawer under Linux apps.";

    private GuiAppsSetup() {}

    /** Which of the two ways to a graphical app the user picked. */
    public enum Route {
        /** Termux's own X11 packages, installed straight into the prefix. */
        X11_REPO("x11"),
        /** A distro container, with its own package manager and its own account. */
        DISTRO("distro");

        /** The value this route is stored under, so a stored choice survives a rename. */
        @NonNull public final String key;

        Route(@NonNull String key) {
            this.key = key;
        }

        /** The route named by {@code key}, or {@link #X11_REPO} when it names none. */
        @NonNull
        public static Route of(String key) {
            for (Route route : values()) if (route.key.equals(key)) return route;
            return X11_REPO;
        }
    }

    /** One of the four apps the screen offers to install alongside the route. */
    public enum StarterApp {
        BROWSER("browser"),
        FILE_MANAGER("files"),
        TEXT_EDITOR("editor"),
        TERMINAL("terminal");

        /** The value this app is stored under. */
        @NonNull public final String key;

        StarterApp(@NonNull String key) {
            this.key = key;
        }
    }

    /**
     * What is ticked before the user touches anything: enough to have somewhere to type, somewhere
     * to look at files and something to browse with. A terminal is left out because the phone
     * already has the one the user is pasting this command into.
     */
    @NonNull
    public static Set<StarterApp> defaultStarters() {
        return EnumSet.of(StarterApp.BROWSER, StarterApp.FILE_MANAGER, StarterApp.TEXT_EDITOR);
    }

    /**
     * A Linux to put inside: its {@code proot-distro} name, its package manager, and the names
     * that manager knows the four starter apps by.
     *
     * <p>Debian is the recommended one and the default. It is the distro every device fact in
     * this repo was measured against, and the one whose arm64 desktop packages are known to work
     * rather than hoped to.
     */
    public enum Distro {

        DEBIAN("debian", "debian", PackageManager.APT,
            "xfonts-base fonts-dejavu-core", "libgl1 libgl1-mesa-dri",
            // Debian ships Firefox as the ESR build; plain "firefox" is not a package there.
            "firefox-esr", "pcmanfm", "mousepad", "xterm"),

        UBUNTU("ubuntu", "ubuntu", PackageManager.APT,
            "xfonts-base fonts-dejavu-core", "libgl1 libgl1-mesa-dri",
            // Not "firefox": on Ubuntu that package is a stub that installs the snap, and snaps
            // cannot run inside a proot container at all. Falkon is a real deb and a real browser.
            "falkon", "pcmanfm", "mousepad", "xterm"),

        ARCH("archlinux", "arch", PackageManager.PACMAN,
            "xorg-fonts-misc ttf-dejavu", "mesa",
            "firefox", "pcmanfm", "mousepad", "xterm");

        /** The name {@code proot-distro} installs and logs into this one by. */
        @NonNull public final String alias;
        /** The value this distro is stored under. */
        @NonNull public final String key;
        /** How packages are installed inside it. */
        @NonNull public final PackageManager packageManager;
        /** The X core fonts, without which several apps open no window at all. */
        @NonNull public final String fontPackages;
        /** The distro's own graphics drivers, so an app is not left with no renderer. */
        @NonNull public final String graphicsPackages;

        @NonNull private final String browser;
        @NonNull private final String fileManager;
        @NonNull private final String textEditor;
        @NonNull private final String terminal;

        Distro(@NonNull String alias, @NonNull String key, @NonNull PackageManager packageManager,
               @NonNull String fontPackages, @NonNull String graphicsPackages,
               @NonNull String browser, @NonNull String fileManager,
               @NonNull String textEditor, @NonNull String terminal) {
            this.alias = alias;
            this.key = key;
            this.packageManager = packageManager;
            this.fontPackages = fontPackages;
            this.graphicsPackages = graphicsPackages;
            this.browser = browser;
            this.fileManager = fileManager;
            this.textEditor = textEditor;
            this.terminal = terminal;
        }

        /** What this distro calls {@code app}. */
        @NonNull
        public String packageFor(@NonNull StarterApp app) {
            switch (app) {
                case BROWSER: return browser;
                case FILE_MANAGER: return fileManager;
                case TEXT_EDITOR: return textEditor;
                default: return terminal;
            }
        }

        /** The distro named by {@code key}, or {@link #DEBIAN} when it names none. */
        @NonNull
        public static Distro of(String key) {
            for (Distro distro : values()) if (distro.key.equals(key)) return distro;
            return DEBIAN;
        }
    }

    /** The two package managers the flow speaks, and how each installs a list of names. */
    public enum PackageManager {
        APT, PACMAN;

        /** Refresh the package list. Run once, before anything is installed. */
        @NonNull
        String refresh() {
            return this == APT ? "apt-get update" : "pacman -Syu --noconfirm";
        }

        /** Install {@code packages} without asking the user anything. */
        @NonNull
        String install(@NonNull String packages) {
            return this == APT
                ? "DEBIAN_FRONTEND=noninteractive apt-get install -y " + packages
                : "pacman -S --needed --noconfirm " + packages;
        }
    }

    /** Termux's own name for {@code app} in the {@code x11} repository. */
    @NonNull
    public static String termuxPackageFor(@NonNull StarterApp app) {
        switch (app) {
            // Termux's Firefox is a real build and needs no sandbox flag to start, which its
            // Chromium does.
            case BROWSER: return "firefox";
            case FILE_MANAGER: return "pcmanfm";
            case TEXT_EDITOR: return "mousepad";
            // The x11 repository has no xterm; xfce4-terminal is its plainest terminal.
            default: return "xfce4-terminal";
        }
    }

    /** Where the chosen account name is written, as a Termux shell spells it. */
    @NonNull
    public static String recordPath(@NonNull Distro distro) {
        return CONTAINERS_PATH + "/" + distro.alias + "/" + RECORD_FILE_NAME;
    }

    /**
     * The command for {@code route}. One string, ready for the clipboard, ending without a
     * newline so the user's own Enter is what starts it.
     *
     * @param distro   which Linux to put inside; ignored by {@link Route#X11_REPO}
     * @param starters the apps the user ticked, in menu order however they were given
     */
    @NonNull
    public static String command(@NonNull Route route, @NonNull Distro distro,
                                 @NonNull Set<StarterApp> starters) {
        return route == Route.X11_REPO ? x11Command(starters) : distroCommand(distro, starters);
    }

    /**
     * Two installs, chained: the repository first, because the apps are not in the index until it
     * is there. With nothing ticked it is just the repository, which is still a sensible thing to
     * hand someone who wants to pick their own apps afterwards.
     */
    @NonNull
    private static String x11Command(@NonNull Set<StarterApp> starters) {
        String base = "pkg install -y x11-repo";
        List<String> packages = new ArrayList<>();
        for (StarterApp app : StarterApp.values()) {
            if (starters.contains(app)) packages.add(termuxPackageFor(app));
        }
        if (packages.isEmpty()) return base;
        return base + " && pkg install -y " + join(packages);
    }

    /**
     * The whole distro route as one command.
     *
     * <p>It is wrapped in a subshell for two reasons. {@code set -e} inside it stops at the first
     * thing that did not work, with the failure still on screen, and cannot take the user's own
     * shell down with it; and a shell reads the whole {@code ( … )} before running any of it, so a
     * paste that arrived in pieces waits at a continuation prompt instead of running half a setup.
     *
     * <p>Every step is safe to run twice: the tool and the container are only fetched when they
     * are not there, the account is only created when it does not exist, and the package manager
     * is asked for names it may already have.
     */
    @NonNull
    private static String distroCommand(@NonNull Distro distro,
                                        @NonNull Set<StarterApp> starters) {
        StringBuilder out = new StringBuilder();
        out.append("( set -e\n");
        out.append("command -v proot-distro >/dev/null 2>&1 || pkg install -y proot-distro\n");
        out.append("[ -d \"").append(CONTAINERS_PATH).append('/').append(distro.alias)
            .append("/rootfs\" ] || proot-distro install ").append(distro.alias).append('\n');
        // The name is asked for out here, not inside the container: the container is entered once,
        // and a name that was refused would mean entering it again.
        out.append("LUSER=\n");
        out.append("while [ -z \"$LUSER\" ]; do\n");
        out.append("printf 'Choose a username for Linux: '\n");
        out.append("read -r LUSER\n");
        out.append("LUSER=$(printf '%s' \"$LUSER\" | tr 'A-Z' 'a-z')\n");
        out.append("case \"$LUSER\" in ''|*[!a-z0-9_]*)"
            + " printf 'Use lowercase letters, digits and underscores.\\n'; LUSER= ;; esac\n");
        out.append("done\n");
        out.append("proot-distro login ").append(distro.alias)
            .append(" -e LUSER=\"$LUSER\" -- /bin/sh -c ")
            .append(singleQuote(insideScript(distro, starters))).append('\n');
        // Written after the login returns, so the file only ever names an account that exists.
        out.append("printf '%s\\n' \"$LUSER\" > \"").append(recordPath(distro)).append("\"\n");
        out.append("printf '%s\\n' ").append(singleQuote(DONE_LINE)).append('\n');
        out.append(')');
        return out.toString();
    }

    /**
     * What runs inside the container, as root: the account, its password, then the packages.
     *
     * <p>{@code passwd} is interactive on purpose and is the only thing here that asks a question.
     * The launcher never sees that password and never stores it; it is typed into the terminal and
     * lives in the container's own {@code /etc/shadow}, like any other Linux account.
     */
    @NonNull
    static String insideScript(@NonNull Distro distro, @NonNull Set<StarterApp> starters) {
        StringBuilder out = new StringBuilder();
        // Its own stop-at-the-first-failure, so a container that would not take the account never
        // reaches the package install and never gets its name written down outside.
        out.append("set -e\n");
        out.append("id \"$LUSER\" >/dev/null 2>&1"
            + " || useradd -m -s \"$(command -v bash || command -v sh)\" \"$LUSER\"\n");
        out.append("passwd \"$LUSER\"\n");
        out.append(distro.packageManager.refresh()).append('\n');
        List<String> packages = new ArrayList<>();
        packages.addAll(Arrays.asList(distro.fontPackages.split(" ")));
        packages.addAll(Arrays.asList(distro.graphicsPackages.split(" ")));
        for (StarterApp app : StarterApp.values()) {
            if (starters.contains(app)) packages.add(distro.packageFor(app));
        }
        out.append(distro.packageManager.install(join(packages)));
        return out.toString();
    }

    /** {@code values} as one space-separated list, each name once and in the order given. */
    @NonNull
    private static String join(@NonNull List<String> values) {
        Set<String> unique = new LinkedHashSet<>(values);
        StringBuilder out = new StringBuilder();
        for (String value : unique) {
            if (out.length() > 0) out.append(' ');
            out.append(value);
        }
        return out.toString();
    }

    /**
     * {@code value} as one shell word. A single quote cannot appear inside single quotes, so it is
     * spelled the only way a POSIX shell accepts: close, escape, reopen. Kept here rather than
     * borrowed so this class stays a plain-Java string builder with nothing behind it.
     */
    @NonNull
    static String singleQuote(@NonNull String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /** Every starter app, in the order the screen lists them. */
    @NonNull
    public static List<StarterApp> starterApps() {
        return Collections.unmodifiableList(Arrays.asList(StarterApp.values()));
    }
}
