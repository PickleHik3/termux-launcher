package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tour.TourEdition;

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
 * Debian trixie, Ubuntu noble and Arch's own package search for route two. VAJ's own apt
 * repository (repo.pathayam.xyz) was checked separately on 2026-09-21 and uses the same package
 * names as Termux's {@code x11} repository, minus a browser, which it does not carry. A name
 * that is wrong is a command that stops halfway with the user watching, so they are written down
 * here rather than guessed at per distro.
 */
public final class GuiAppsSetup {

    /** The file the script writes the chosen account name into, beside the container's rootfs. */
    public static final String RECORD_FILE_NAME = "launcher-user";
    /** The X keyboard data the display server needs before it can start. */
    public static final String KEYBOARD_DATA_PACKAGE = "xkeyboard-config";
    /** The session bus: a whole desktop needs one and XFCE starts none of its own on X11. */
    public static final String BUS_PACKAGE = "dbus";
    /**
     * The window manager the display starts by default ({@link X11WindowManager}): without it
     * every app opens at its own size in a corner and the status bar lists no windows. It runs on
     * the Termux side, beside the server, whichever route the apps came from.
     */
    public static final String WINDOW_MANAGER_PACKAGE = "openbox";

    /**
     * fcitx5 and its toolkit modules: GTK 3, which every starter app uses, and Qt, so a Qt app
     * installed later reports focus too rather than leaving taps in it to the cursor alone.
     */
    private static final String[] INPUT_METHOD_PACKAGES = {"fcitx5", "fcitx5-gtk3", "fcitx5-qt"};
    /**
     * The sound server the launcher runs for every terminal session, Linux app and distro
     * ({@code LauncherSound}). It runs on the Termux side whichever route the apps came from.
     */
    private static final String AUDIO_PACKAGE = "pulseaudio";

    /**
     * {@code /etc/asound.conf} in a distro: ALSA's default device is PulseAudio, so an app that
     * plays through ALSA alone reaches the launcher's server too (sound decision 6). Its first
     * line marks it as the launcher's, which is what lets a later run rewrite it.
     */
    static final String ASOUND_CONF = "# written by termux-launcher: ALSA plays through the launcher sound server\n"
        + "pcm.!default { type pulse }\n"
        + "ctl.!default { type pulse }\n";

    /** Where {@code proot-distro} 5.x keeps its containers, as a Termux shell writes it. */
    private static final String CONTAINERS_PATH = "$PREFIX/var/lib/proot-distro/containers";

    /**
     * The Arch image for arm64 phones. The official {@code archlinux} image is amd64-only, so
     * proot-distro 5.x installs Arch Linux ARM from this multi-arch image, rebuilt daily and the
     * one upstream points to (termux/proot-distro issue 672). It ships its own pacman keyring.
     */
    private static final String ARCH_ARM_IMAGE = "menci/archlinuxarm";

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

    /**
     * The routes {@code edition} offers on the "Get GUI apps" screen, in menu order.
     *
     * <p>Decision D1 (user, 2026-09-20), reversed (user, 2026-09-21): VAJ's own apt repository
     * (repo.pathayam.xyz, aarch64) now carries {@code xkeyboard-config}, {@code pcmanfm},
     * {@code mousepad}, {@code thunar} and {@code xfce4-terminal}, so its X11 route works there
     * after all and is offered again alongside the distro route — see
     * {@link #command(Route, Distro, Set, TourEdition)} for the two things that route drops for
     * VAJ (the separate {@code x11-repo} step, which that repo has no equivalent of, and the
     * browser, which it ships none of). Decision (user, 2026-09-20): the nix edition offers
     * neither route — graphical apps there come from nixpkgs and {@code home.nix}, a third way
     * this class does not build a command for, so an empty list here is what tells the screen to
     * show neither a route choice nor a command to copy.
     */
    @NonNull
    public static List<Route> routesFor(@NonNull TourEdition edition) {
        if (edition == TourEdition.NIX) return Collections.emptyList();
        return Collections.unmodifiableList(Arrays.asList(Route.X11_REPO, Route.DISTRO));
    }

    /**
     * The route to fall back to for {@code edition} when nothing is stored yet, or a stored
     * choice no longer applies; {@code null} when {@code edition} offers no route at all.
     */
    @Nullable
    public static Route defaultRoute(@NonNull TourEdition edition) {
        List<Route> routes = routesFor(edition);
        return routes.isEmpty() ? null : routes.get(0);
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
            // dbus-launch lives in dbus-x11 on Debian; the plain "dbus" package is only the daemon.
            "dbus-x11",
            // ALSA's PulseAudio plugin; it brings libpulse with it.
            "libasound2-plugins",
            // Debian ships Firefox as the ESR build; plain "firefox" is not a package there.
            "firefox-esr", "pcmanfm", "mousepad", "xterm"),

        UBUNTU("ubuntu", "ubuntu", PackageManager.APT,
            "xfonts-base fonts-dejavu-core", "libgl1 libgl1-mesa-dri",
            // Same split as Debian: dbus-launch is in dbus-x11, not in dbus itself.
            "dbus-x11",
            "libasound2-plugins",
            // Not "firefox": on Ubuntu that package is a stub that installs the snap, and snaps
            // cannot run inside a proot container at all. Falkon is a real deb and a real browser.
            "falkon", "pcmanfm", "mousepad", "xterm"),

        ARCH("archlinux", "arch", PackageManager.PACMAN,
            "xorg-fonts-misc ttf-dejavu", "mesa",
            // Arch does not split this: dbus-launch is in the dbus package itself.
            "dbus",
            // Arch's plugin only suggests libpulse, so it is asked for by name.
            "alsa-plugins libpulse",
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
        /** Carries {@code dbus-launch}, so a desktop that starts no session bus of its own gets one. */
        @NonNull public final String busPackage;
        /** ALSA's PulseAudio plugin, so ALSA apps reach the launcher's sound server. */
        @NonNull public final String alsaPackages;

        @NonNull private final String browser;
        @NonNull private final String fileManager;
        @NonNull private final String textEditor;
        @NonNull private final String terminal;

        Distro(@NonNull String alias, @NonNull String key, @NonNull PackageManager packageManager,
               @NonNull String fontPackages, @NonNull String graphicsPackages,
               @NonNull String busPackage, @NonNull String alsaPackages,
               @NonNull String browser, @NonNull String fileManager,
               @NonNull String textEditor, @NonNull String terminal) {
            this.alias = alias;
            this.key = key;
            this.packageManager = packageManager;
            this.fontPackages = fontPackages;
            this.graphicsPackages = graphicsPackages;
            this.busPackage = busPackage;
            this.alsaPackages = alsaPackages;
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

    /**
     * What {@code app} is called on the screen once {@code route} and {@code distro} are chosen:
     * the name the user will see in their app drawer afterwards, not the package name.
     *
     * <p>The Starter Apps rows carry this as their hint (user, 2026-09-22), because "Browser"
     * alone does not say whether the command is about to fetch Firefox or Falkon - and on Ubuntu
     * it is Falkon, for a reason that belongs in {@link Distro} rather than in front of the user.
     */
    @NonNull
    public static String starterAppName(@NonNull Route route, @NonNull Distro distro,
                                        @NonNull StarterApp app) {
        return prettyName(route == Route.X11_REPO
            ? termuxPackageFor(app) : distro.packageFor(app));
    }

    /** A package name as people write it. Anything unlisted is handed back as it came. */
    @NonNull
    private static String prettyName(@NonNull String packageName) {
        switch (packageName) {
            case "firefox": return "Firefox";
            case "firefox-esr": return "Firefox ESR";
            case "falkon": return "Falkon";
            case "pcmanfm": return "PCManFM";
            case "mousepad": return "Mousepad";
            case "xfce4-terminal": return "Xfce Terminal";
            default: return packageName;
        }
    }

    /** Where the chosen account name is written, as a Termux shell spells it. */
    @NonNull
    public static String recordPath(@NonNull Distro distro) {
        return CONTAINERS_PATH + "/" + distro.alias + "/" + RECORD_FILE_NAME;
    }

    /**
     * The Termux edition's command for {@code route}. Kept for callers with no edition of their
     * own to hand in yet; {@link #command(Route, Distro, Set, TourEdition)} is what the "Get GUI
     * apps" screen calls.
     *
     * @param distro   which Linux to put inside; ignored by {@link Route#X11_REPO}
     * @param starters the apps the user ticked, in menu order however they were given
     */
    @NonNull
    public static String command(@NonNull Route route, @NonNull Distro distro,
                                 @NonNull Set<StarterApp> starters) {
        return command(route, distro, starters, TourEdition.TERMUX);
    }

    /**
     * The command for {@code route}. One string, ready for the clipboard, ending without a
     * newline so the user's own Enter is what starts it.
     *
     * @param distro   which Linux to put inside; ignored by {@link Route#X11_REPO}
     * @param starters the apps the user ticked, in menu order however they were given
     * @param edition  only changes {@link Route#X11_REPO}: VAJ's apt repository has no separate
     *                 {@code x11-repo} to add and ships no browser
     */
    @NonNull
    public static String command(@NonNull Route route, @NonNull Distro distro,
                                 @NonNull Set<StarterApp> starters, @NonNull TourEdition edition) {
        return command(route, distro, starters, edition, null, null);
    }

    /**
     * The command for a run that cannot answer questions (the setup wizard's headless run). With a
     * {@code user} the distro route asks nothing: the account name is set from the shell-quoted
     * {@code user} instead of {@code read -r LUSER}, and the password is set with
     * {@code chpasswd} from the shell-quoted {@code password} instead of an interactive
     * {@code passwd}. The password goes into the container as the environment variable
     * {@code PASS}; nothing here prints it. With a null {@code user} this is the interactive
     * command of {@link #command(Route, Distro, Set, TourEdition)}. Both are ignored by
     * {@link Route#X11_REPO}.
     *
     * @throws IllegalArgumentException when {@code user} is given without {@code password}
     */
    @NonNull
    public static String command(@NonNull Route route, @NonNull Distro distro,
                                 @NonNull Set<StarterApp> starters, @NonNull TourEdition edition,
                                 @Nullable String user, @Nullable String password) {
        if (user != null && password == null) {
            throw new IllegalArgumentException("a user needs a password");
        }
        return route == Route.X11_REPO
            ? x11Command(starters, edition) + " && " + markerStep(route, distro)
            : distroCommand(distro, starters, user, password);
    }

    /** Where the command's last step records that it finished (Termux side, outside the container). */
    public static final String MARKER_DIR = "$PREFIX/var/lib/termux-launcher";
    public static final String MARKER_FILE_NAME = "gui-apps-setup";

    /** The marker file as the app sees it. */
    @NonNull
    public static java.io.File markerFile() {
        return new java.io.File(
            com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH
                + "/var/lib/termux-launcher/" + MARKER_FILE_NAME);
    }

    /** The last step: runs only when everything before it succeeded; the file holds route and distro. */
    @NonNull
    static String markerStep(@NonNull Route route, @NonNull Distro distro) {
        return "mkdir -p \"" + MARKER_DIR + "\" && printf '%s\\n' '" + (route == Route.DISTRO ? route.key + " " + distro.alias : route.key)
            + "' > \"" + MARKER_DIR + "/" + MARKER_FILE_NAME + "\"";
    }

    /**
     * Termux: two installs, chained — the repository first, because the apps are not in the
     * index until it is there. VAJ: everything is already in one repository, its apps are packaged
     * there under the same names, and it ships no browser at all, so a ticked
     * {@link StarterApp#BROWSER} is silently dropped rather than naming a package that does not
     * exist. With nothing ticked it is just the keyboard data (plus, for Termux, the repository),
     * which is still a sensible thing to hand someone who wants to pick their own apps afterwards.
     */
    @NonNull
    private static String x11Command(@NonNull Set<StarterApp> starters,
                                     @NonNull TourEdition edition) {
        boolean vaj = edition == TourEdition.VAJ;
        List<String> packages = new ArrayList<>();
        // The display itself needs the keyboard data before it can start; it rides along with
        // whatever apps were ticked, so a fresh install gets a display that starts.
        packages.add(KEYBOARD_DATA_PACKAGE);
        // A whole desktop needs a session bus and XFCE starts none of its own on X11 (D6); it
        // rides along the same way, not as a tickable starter app.
        packages.add(BUS_PACKAGE);
        // The window manager the display starts. VAJ's repository was last checked before it
        // mattered, so there it is asked for on its own after the rest, and a repository without
        // it stops nothing.
        if (!vaj) packages.add(WINDOW_MANAGER_PACKAGE);
        // Exact text-field focus: fcitx5 and its GTK3 and Qt modules tell the display when a text
        // field takes focus. GTK3 because the starter apps are GTK3. Termux's x11 repository
        // only; VAJ's repository has not been checked for them.
        if (!vaj) packages.addAll(Arrays.asList(INPUT_METHOD_PACKAGES));
        // Sound: the launcher runs this server for everything (LauncherSound). Termux's repository
        // only; VAJ's has not been checked.
        if (!vaj) packages.add(AUDIO_PACKAGE);
        for (StarterApp app : StarterApp.values()) {
            if (vaj && app == StarterApp.BROWSER) continue;
            if (starters.contains(app)) packages.add(termuxPackageFor(app));
        }
        String install = "pkg install -y " + join(packages);
        return vaj ? install + " && { pkg install -y " + WINDOW_MANAGER_PACKAGE + " || true; }"
            : "pkg install -y x11-repo && " + install;
    }

    /**
     * The step that fetches the container. Arch is the one distro with no image for arm64 on
     * Docker Hub, so on an arm64 phone it comes from an Arch Linux ARM image, under the same name.
     */
    @NonNull
    static String installStep(@NonNull Distro distro) {
        if (distro != Distro.ARCH) return "proot-distro install " + distro.alias;
        return "{\n"
            + "if [ \"$(uname -m)\" = aarch64 ]; then\n"
            + "proot-distro install " + ARCH_ARM_IMAGE + " --name " + distro.alias + "\n"
            + "else\n"
            + "proot-distro install " + distro.alias + "\n"
            + "fi\n"
            + "}";
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
                                        @NonNull Set<StarterApp> starters,
                                        @Nullable String user, @Nullable String password) {
        boolean unattended = user != null;
        StringBuilder out = new StringBuilder();
        out.append("( set -e\n");
        out.append("command -v proot-distro >/dev/null 2>&1 || pkg install -y proot-distro\n");
        // The display needs xkeyboard-config; the Termux edition finds it in x11-repo, the VAJ
        // edition in its main repository, so the repo step is allowed to fail quietly.
        out.append("[ -d \"$PREFIX/share/X11/xkb\" ] || { pkg install -y x11-repo >/dev/null 2>&1 || true; "
            + "pkg install -y ").append(KEYBOARD_DATA_PACKAGE).append("; }\n");
        // The window manager runs beside the server, on the Termux side, not in the container.
        out.append("command -v ").append(WINDOW_MANAGER_PACKAGE)
            .append(" >/dev/null 2>&1 || { pkg install -y x11-repo >/dev/null 2>&1 || true; "
                + "pkg install -y ").append(WINDOW_MANAGER_PACKAGE).append("; }\n");
        // The sound server runs on the Termux side too (LauncherSound); a repository without it
        // costs the distro its sound and stops nothing.
        out.append("command -v ").append(AUDIO_PACKAGE).append(" >/dev/null 2>&1 || pkg install -y ")
            .append(AUDIO_PACKAGE).append(" || true\n");
        out.append("[ -d \"").append(CONTAINERS_PATH).append('/').append(distro.alias)
            .append("/rootfs\" ] || ").append(installStep(distro)).append('\n');
        // The name is asked for out here, not inside the container: the container is entered once,
        // and a name that was refused would mean entering it again.
        if (unattended) {
            // A run with nobody to answer: the name and the password are literals, quoted for the shell.
            out.append("LUSER=").append(singleQuote(user)).append('\n');
            out.append("PASS=").append(singleQuote(password)).append('\n');
        } else {
            out.append("LUSER=\n");
            out.append("while [ -z \"$LUSER\" ]; do\n");
            out.append("printf 'Choose a username for Linux: '\n");
            out.append("read -r LUSER\n");
            out.append("LUSER=$(printf '%s' \"$LUSER\" | tr 'A-Z' 'a-z')\n");
            out.append("case \"$LUSER\" in ''|*[!a-z0-9_]*)"
                + " printf 'Use lowercase letters, digits and underscores.\\n'; LUSER= ;; esac\n");
            out.append("done\n");
        }
        out.append("proot-distro login ").append(distro.alias)
            .append(" -e LUSER=\"$LUSER\"").append(unattended ? " -e PASS=\"$PASS\"" : "")
            .append(" -- /bin/sh -c ")
            .append(singleQuote(insideScript(distro, starters, unattended))).append('\n');
        // Written after the login returns, so the file only ever names an account that exists.
        out.append("printf '%s\\n' \"$LUSER\" > \"").append(recordPath(distro)).append("\"\n");
        out.append(markerStep(Route.DISTRO, distro)).append('\n');
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
        return insideScript(distro, starters, false);
    }

    /** {@code unattended}: the password comes from {@code $PASS} through chpasswd, no question asked. */
    @NonNull
    static String insideScript(@NonNull Distro distro, @NonNull Set<StarterApp> starters,
                               boolean unattended) {
        StringBuilder out = new StringBuilder();
        // Its own stop-at-the-first-failure, so a container that would not take the account never
        // reaches the package install and never gets its name written down outside.
        out.append("set -e\n");
        out.append("id \"$LUSER\" >/dev/null 2>&1"
            + " || useradd -m -s \"$(command -v bash || command -v sh)\" \"$LUSER\"\n");
        out.append(unattended ? "printf '%s:%s\\n' \"$LUSER\" \"$PASS\" | chpasswd\n"
            : "passwd \"$LUSER\"\n");
        out.append(distro.packageManager.refresh()).append('\n');
        List<String> packages = new ArrayList<>();
        packages.addAll(Arrays.asList(distro.fontPackages.split(" ")));
        packages.addAll(Arrays.asList(distro.graphicsPackages.split(" ")));
        packages.add(distro.busPackage);
        packages.addAll(Arrays.asList(distro.alsaPackages.split(" ")));
        for (StarterApp app : StarterApp.values()) {
            if (starters.contains(app)) packages.add(distro.packageFor(app));
        }
        out.append(distro.packageManager.install(join(packages))).append('\n');
        // ALSA's default device goes to PulseAudio, unless somebody else's asound.conf is there.
        out.append("if [ ! -e /etc/asound.conf ] || grep -q 'written by termux-launcher' /etc/asound.conf;"
            + " then printf '%s' ").append(singleQuote(ASOUND_CONF)).append(" > /etc/asound.conf; fi");
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
