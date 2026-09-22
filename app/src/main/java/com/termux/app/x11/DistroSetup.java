package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Whether the Display place has anything worth offering: what a distro container still needs
 * before the apps inside it work.
 *
 * <p>Reading only. Nothing here runs anything and nothing here builds a command — the command the
 * user is handed is {@link GuiAppsSetup}'s, and this class only decides whether the place should
 * point at the screen that builds it. All of it is tested against fixture rootfs directories.
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
 * <h3>Which containers it will read</h3>
 *
 * Only ones with {@code apt-get} in them. A container of another kind is one the user set up
 * themselves, their own way, and a home screen has no business telling them it is wrong.
 */
public final class DistroSetup {

    /** Where {@code xfonts-base} puts the core fonts, and where a non-Debian layout puts them. */
    private static final String[] FONT_DIRS = {"usr/share/fonts/X11/misc", "usr/share/fonts/misc"};

    /** The one file that says the flow knows how to install packages in this container. */
    private static final String APT = "usr/bin/apt-get";

    private DistroSetup() {}

    /** One thing a container can still be missing, in the order the setup would supply it. */
    public enum Step {
        INSTALL_DISTRO("distro"),
        CREATE_USER("user"),
        INSTALL_FONTS("fonts");

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
}
