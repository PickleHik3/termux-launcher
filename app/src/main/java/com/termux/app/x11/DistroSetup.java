package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * D9's setup flow, as far as it can be decided without running anything: what a distro container
 * still needs before the apps inside it work, and the shell script that supplies it.
 *
 * <p>Everything here is file reading and string building, so all of it is tested against fixture
 * rootfs directories. Nothing in this class starts a process; {@link DistroSetupRunner} does that.
 *
 * <h3>What "not set up" means</h3>
 *
 * Three readings, and only readings that are cheap enough to take every time the user arrives at
 * the Display place — the probe is a handful of {@code stat} calls per container and never walks a
 * rootfs:
 * <ul>
 *   <li><b>No container at all.</b> {@link ProotDistro#containers} already answers this from one
 *       directory listing, and it answers it the same way whether {@code proot-distro} is
 *       installed or not, which is what lets the flow offer to install the tool itself.
 *   <li><b>No ordinary user.</b> Free: {@link ProotDistro.Container#user} is already the lowest
 *       non-root uid in the container's {@code /etc/passwd}, and it reads {@code "root"} exactly
 *       when there is none. D3 logs apps in as that user, so a root-only container is a container
 *       whose Electron apps fail silently.
 *   <li><b>No X core fonts.</b> One {@code list()} of the directory {@code xfonts-base} fills:
 *       a fresh distro image has no X fonts at all, and the measured symptom was an app that
 *       simply would not start. A directory that exists but is empty is not fonts.
 * </ul>
 *
 * <p>Graphics drivers and the starter apps are deliberately <em>not</em> probed. There is no cheap
 * honest test for "the right Mesa is here", and the spec parks whether it helps at all; they are
 * steps the flow performs, never a reason to tell the user something is wrong.
 *
 * <h3>Which containers it will touch</h3>
 *
 * Only ones with {@code apt-get} in them. The package names below are Debian's, and a launcher
 * that guessed at three package managers would be guessing at three sets of failures too. A user
 * whose only container is an Arch or Alpine one is left alone entirely — which is also the
 * behaviour wanted for someone who runs their container their own way.
 */
public final class DistroSetup {

    /**
     * The distro offered to someone who has none. Debian, because it is the one every device fact
     * in the spec was measured against, the one the guide walks through, and the one with the
     * largest set of ready-built arm64 desktop apps — so the packages named below are known to
     * exist and known to work rather than hoped to.
     */
    static final String DEFAULT_CONTAINER = "debian";

    /**
     * The account the flow creates. The user is not asked to choose it: D1's whole premise is that
     * there is no command for the user to run, and a text field wanting a lowercase name with no
     * spaces is a form that can be got wrong. It is never typed anywhere either — the launcher
     * logs apps in as whatever ordinary user it finds, so the name only ever shows in a shell
     * prompt inside the container.
     */
    static final String LOGIN_USER = "user";

    /** The X core fonts an app needs to open a window at all, plus a scalable family for text. */
    static final String FONT_PACKAGES = "xfonts-base fonts-dejavu-core";

    /** The distro's own graphics drivers, so a container app is not left with no renderer. */
    static final String GRAPHICS_PACKAGES = "libgl1 libgl1-mesa-dri";

    /**
     * The starter set: the smallest thing that proves the feature and is worth keeping. {@code
     * x11-apps} is the guide's own proof that the display works, and puts a tappable tile in the
     * drawer within seconds; {@code mousepad} is a real GUI app with its own name and icon and
     * costs a few megabytes. Anything larger is a choice the user should make themselves.
     */
    static final String STARTER_PACKAGES = "x11-apps mousepad";

    /** Where {@code xfonts-base} puts the core fonts, and where a non-Debian layout puts them. */
    private static final String[] FONT_DIRS = {"usr/share/fonts/X11/misc", "usr/share/fonts/misc"};

    /** The one file that says the flow knows how to install packages in this container. */
    private static final String APT = "usr/bin/apt-get";

    private DistroSetup() {}

    /** One thing the flow does, in the order it does it. */
    public enum Step {
        INSTALL_DISTRO("distro"),
        CREATE_USER("user"),
        INSTALL_FONTS("fonts"),
        INSTALL_GRAPHICS("graphics"),
        INSTALL_APPS("apps");

        /** The short name this step goes by in a dismissal signature. */
        @NonNull public final String key;

        Step(@NonNull String key) {
            this.key = key;
        }
    }

    /** What the launcher found, and what it would therefore do about it. */
    public static final class Readiness {

        /** The container the flow would work on, or null when there is nothing to offer. */
        @Nullable public final String container;
        /** What that container is missing, in order; empty when there is nothing to offer. */
        @NonNull public final List<Step> missing;
        /** The containers directory the reading came from, kept so the script can name paths. */
        @NonNull public final String containersDir;

        Readiness(@Nullable String container, @NonNull List<Step> missing,
                  @NonNull String containersDir) {
            this.container = container;
            this.missing = Collections.unmodifiableList(missing);
            this.containersDir = containersDir;
        }

        /** True exactly when there is something to offer the user. */
        public boolean needsSetup() {
            return !missing.isEmpty();
        }

        /** True when the flow would have to fetch a whole distro, not just finish one off. */
        public boolean startsFromNothing() {
            return missing.contains(Step.INSTALL_DISTRO);
        }

        /** The container the flow works on: the one found, or the one it would install. */
        @NonNull
        public String targetContainer() {
            return container == null ? DEFAULT_CONTAINER : container;
        }

        /**
         * What a "not now" is remembered against. It names the situation, not the moment: the
         * same situation stays dismissed forever, and any change to it — a container appearing, a
         * user created by hand, fonts installed in a shell — is a different situation and is
         * offered once more.
         */
        @NonNull
        public String signature() {
            StringBuilder out = new StringBuilder(container == null ? "" : container).append('|');
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) out.append(',');
                out.append(missing.get(i).key);
            }
            return out.toString();
        }

        @Override
        @NonNull
        public String toString() {
            return "Readiness{" + signature() + "}";
        }
    }

    /** What the flow reads the containers under {@code containersDir} as needing. */
    @NonNull
    public static Readiness read(@NonNull File containersDir) {
        return decide(ProotDistro.containers(containersDir), containersDir.getAbsolutePath());
    }

    /** What the flow reads the running prefix's containers as needing. */
    @NonNull
    public static Readiness read() {
        return read(ProotDistro.containersDir());
    }

    /**
     * The reading, given the containers already found. Nothing is offered when a container the
     * flow could manage is already complete, and nothing is offered when every container belongs
     * to a package manager the flow does not speak — in both cases the user has a Linux that
     * works, or one they are running their own way, and a home screen has no business nagging
     * about either.
     */
    @NonNull
    static Readiness decide(@NonNull List<ProotDistro.Container> containers,
                            @NonNull String containersDir) {
        if (containers.isEmpty()) {
            return new Readiness(null, new ArrayList<>(Arrays.asList(
                Step.INSTALL_DISTRO, Step.CREATE_USER, Step.INSTALL_FONTS)), containersDir);
        }
        Readiness first = null;
        for (ProotDistro.Container container : containers) {
            if (!isManageable(container.root)) continue;
            List<Step> missing = missingFor(container);
            if (missing.isEmpty()) return new Readiness(container.name, missing, containersDir);
            if (first == null) first = new Readiness(container.name, missing, containersDir);
        }
        return first != null ? first
            : new Readiness(null, Collections.<Step>emptyList(), containersDir);
    }

    /** What one container still needs. Only ever {@code CREATE_USER} and {@code INSTALL_FONTS}. */
    @NonNull
    static List<Step> missingFor(@NonNull ProotDistro.Container container) {
        List<Step> missing = new ArrayList<>(2);
        if (container.user.isEmpty() || "root".equals(container.user)) missing.add(Step.CREATE_USER);
        if (!hasXFonts(container.root)) missing.add(Step.INSTALL_FONTS);
        return missing;
    }

    /** Whether the flow speaks this container's package manager. */
    static boolean isManageable(@NonNull File rootfs) {
        return new File(rootfs, APT).isFile();
    }

    /**
     * Whether the container has the X core fonts. An empty directory left behind by a removed
     * package is not fonts, so the directory is listed rather than merely stat'd — one readdir of
     * one directory, which is the whole cost of this reading.
     */
    static boolean hasXFonts(@NonNull File rootfs) {
        for (String path : FONT_DIRS) {
            String[] entries = new File(rootfs, path).list();
            if (entries != null && entries.length > 0) return true;
        }
        return false;
    }

    /**
     * Everything the run will do: what is missing, then the two steps that are never probed for.
     * Empty when there is nothing to offer, so a run can never be started over a container that
     * is already working.
     */
    @NonNull
    public static List<Step> plan(@NonNull Readiness readiness) {
        if (!readiness.needsSetup()) return Collections.emptyList();
        List<Step> steps = new ArrayList<>(readiness.missing);
        steps.add(Step.INSTALL_GRAPHICS);
        steps.add(Step.INSTALL_APPS);
        return steps;
    }

    /**
     * Every sentence the run can print. Held as plain strings rather than resource ids so the
     * script builder stays pure and a test can read what a step would actually say; the caller
     * fills it from {@code strings.xml} ({@code DistroSetupDialog}).
     */
    public static final class Messages {
        @NonNull public final String header;
        @NonNull public final String gettingDistro;
        @NonNull public final String creatingUser;
        @NonNull public final String installingFonts;
        @NonNull public final String installingGraphics;
        @NonNull public final String installingApps;
        @NonNull public final String distroFailed;
        @NonNull public final String userFailed;
        @NonNull public final String fontsFailed;
        @NonNull public final String graphicsFailed;
        @NonNull public final String appsFailed;
        @NonNull public final String retry;
        @NonNull public final String done;

        public Messages(@NonNull String header, @NonNull String gettingDistro,
                        @NonNull String creatingUser, @NonNull String installingFonts,
                        @NonNull String installingGraphics, @NonNull String installingApps,
                        @NonNull String distroFailed, @NonNull String userFailed,
                        @NonNull String fontsFailed, @NonNull String graphicsFailed,
                        @NonNull String appsFailed, @NonNull String retry, @NonNull String done) {
            this.header = header;
            this.gettingDistro = gettingDistro;
            this.creatingUser = creatingUser;
            this.installingFonts = installingFonts;
            this.installingGraphics = installingGraphics;
            this.installingApps = installingApps;
            this.distroFailed = distroFailed;
            this.userFailed = userFailed;
            this.fontsFailed = fontsFailed;
            this.graphicsFailed = graphicsFailed;
            this.appsFailed = appsFailed;
            this.retry = retry;
            this.done = done;
        }

        /** What the step's banner says while it runs. */
        @NonNull
        String banner(@NonNull Step step) {
            switch (step) {
                case INSTALL_DISTRO: return gettingDistro;
                case CREATE_USER: return creatingUser;
                case INSTALL_FONTS: return installingFonts;
                case INSTALL_GRAPHICS: return installingGraphics;
                default: return installingApps;
            }
        }

        /** The one plain sentence the step prints when it did not work. */
        @NonNull
        String failure(@NonNull Step step) {
            switch (step) {
                case INSTALL_DISTRO: return distroFailed;
                case CREATE_USER: return userFailed;
                case INSTALL_FONTS: return fontsFailed;
                case INSTALL_GRAPHICS: return graphicsFailed;
                default: return appsFailed;
            }
        }
    }

    /**
     * The whole run, as one shell script for the pane it runs in.
     *
     * <p>Every step is written to be safe to run twice, and every step checks the world again
     * rather than trusting the reading the offer was made from — minutes can pass between the tap
     * and the step, and the user has a shell of their own. That is also what lets a failed run be
     * started again from the beginning and carry on where it stopped, which is what the failure
     * message promises: the steps that already worked cost a second each and do nothing.
     *
     * <p>A step that fails prints its own sentence and stops the script there, so the pane is left
     * showing what did not happen rather than the next step's noise on top of it.
     */
    @NonNull
    public static String script(@NonNull Readiness readiness, @NonNull Messages messages) {
        List<Step> steps = plan(readiness);
        String container = readiness.targetContainer();
        String rootfs = readiness.containersDir + "/" + container + "/rootfs";
        StringBuilder out = new StringBuilder();
        out.append("set -u\n");
        out.append("_say() { printf '\\n%s\\n' \"$1\"; }\n");
        out.append("_fail() { printf '\\n%s\\n%s\\n' \"$1\" ")
            .append(ProotDistro.singleQuote(messages.retry)).append("; exit 1; }\n");
        out.append(say(messages.header));
        boolean indexRefreshed = false;
        for (Step step : steps) {
            out.append(say(messages.banner(step)));
            String failed = ProotDistro.singleQuote(messages.failure(step));
            switch (step) {
                case INSTALL_DISTRO:
                    out.append("command -v proot-distro >/dev/null 2>&1 || pkg install -y proot-distro || _fail ")
                        .append(failed).append('\n');
                    out.append("[ -d ").append(ProotDistro.singleQuote(rootfs))
                        .append(" ] || proot-distro install ").append(container)
                        .append(" || _fail ").append(failed).append('\n');
                    break;
                case CREATE_USER:
                    out.append(login(container, "id " + LOGIN_USER + " >/dev/null 2>&1"
                            + " || useradd -m -s \"$(command -v bash || command -v sh)\" " + LOGIN_USER))
                        .append(" || _fail ").append(failed).append('\n');
                    break;
                default:
                    if (!indexRefreshed) {
                        indexRefreshed = true;
                        // The package list, refreshed once per run. A failure here is not a
                        // failure of the step: a stale list still installs what is already
                        // cached, and apt's own message about it is in the pane either way.
                        out.append(login(container, "apt-get update")).append(" || true\n");
                    }
                    out.append(login(container, aptInstall(packagesFor(step))))
                        .append(" || _fail ").append(failed).append('\n');
                    break;
            }
        }
        out.append(say(messages.done));
        return out.toString();
    }

    /** What one package step installs. */
    @NonNull
    static String packagesFor(@NonNull Step step) {
        switch (step) {
            case INSTALL_FONTS: return FONT_PACKAGES;
            case INSTALL_GRAPHICS: return GRAPHICS_PACKAGES;
            case INSTALL_APPS: return STARTER_PACKAGES;
            default: return "";
        }
    }

    @NonNull
    private static String aptInstall(@NonNull String packages) {
        return "DEBIAN_FRONTEND=noninteractive apt-get install -y " + packages;
    }

    /**
     * A command run inside the container as root. No {@code --shared-x11} and no forwarded
     * environment: nothing here draws anything, and the fewer things a package install can see of
     * the host the better.
     */
    @NonNull
    private static String login(@NonNull String container, @NonNull String command) {
        return "proot-distro login " + container + " -- /bin/sh -c "
            + ProotDistro.singleQuote(command);
    }

    @NonNull
    private static String say(@NonNull String message) {
        return "_say " + ProotDistro.singleQuote(message) + "\n";
    }
}
