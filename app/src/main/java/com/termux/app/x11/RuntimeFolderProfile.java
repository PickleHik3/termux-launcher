package com.termux.app.x11;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;

/**
 * The one runtime folder every shell shares (ADR 0036): {@code XDG_RUNTIME_DIR} defaults to the
 * prefix's tmp folder, in Termux's login shells ({@code etc/profile.d}), in fish
 * ({@code etc/fish/conf.d}, which does not read {@code profile.d}) and, as one block, in the
 * scripts the launcher starts itself. Every text fills the variable only when it is empty, so a
 * user's own value stays. Inside a proot-distro container the same rule gives a private folder of
 * its own, the one a container's desktop is started with.
 *
 * <p>Written by {@link #ensurePrefix()} before the first shell of a launcher start opens, and into
 * each container with its display profile ({@link DistroDisplayProfile#refreshInstalled}). A file
 * whose text is unchanged is not rewritten, and a file that is not the launcher's is left alone
 * ({@link DistroDisplayProfile#write}).
 */
public final class RuntimeFolderProfile {

    /** The sh file, relative to the prefix or a container's rootfs. */
    public static final String SH_PATH = "etc/profile.d/termux-launcher-runtime.sh";
    /** The fish file, relative to the prefix. */
    public static final String FISH_PATH = "etc/fish/conf.d/termux-launcher-runtime.fish";
    /**
     * A container's folder, as its shell reads it: under the container's own tmp, private to it.
     * A container's desktop is started with this same value.
     */
    public static final String CONTAINER_FOLDER = "${TMPDIR:-/tmp}/termux-x11-runtime";

    private static final String LOG_TAG = "RuntimeFolderProfile";
    private static final String MARKER = "# written by termux-launcher";

    private RuntimeFolderProfile() {}

    /** The prefix's sh text: {@code $TMPDIR}, else the prefix's tmp. */
    @NonNull
    public static String prefixSh(@NonNull String prefixTmp) {
        return MARKER + " for the runtime folder; it rewrites this file.\n"
            + "# Every shell on the launcher shares one XDG_RUNTIME_DIR; one you set yourself is kept.\n"
            + prefixLaunchBlock(prefixTmp);
    }

    /** The prefix's fish text, the same rule for the shell that does not read {@code profile.d}. */
    @NonNull
    public static String prefixFish(@NonNull String prefixTmp) {
        return MARKER + " for the runtime folder; it rewrites this file.\n"
            + "# Every shell on the launcher shares one XDG_RUNTIME_DIR; one you set yourself is kept.\n"
            + "if test -z \"$XDG_RUNTIME_DIR\"\n"
            + "    if test -n \"$TMPDIR\"\n"
            + "        set -gx XDG_RUNTIME_DIR \"$TMPDIR\"\n"
            + "    else\n"
            + "        set -gx XDG_RUNTIME_DIR \"" + prefixTmp + "\"\n"
            + "    end\n"
            + "end\n";
    }

    /** A container's sh text: its own private folder, made when the login shell starts. */
    @NonNull
    public static String containerSh() {
        return MARKER + " for the runtime folder; it rewrites this file.\n"
            + "# A login shell here gets a private XDG_RUNTIME_DIR; one you set yourself is kept.\n"
            + "if [ -z \"${XDG_RUNTIME_DIR:-}\" ]; then\n"
            + "    XDG_RUNTIME_DIR=\"" + CONTAINER_FOLDER + "\"\n"
            + "    export XDG_RUNTIME_DIR\n"
            + "    mkdir -p \"$XDG_RUNTIME_DIR\" && chmod 700 \"$XDG_RUNTIME_DIR\"\n"
            + "fi\n";
    }

    /** The sh lines without the comments: what a script the launcher starts begins with. */
    @NonNull
    public static String prefixLaunchBlock() {
        return prefixLaunchBlock(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH);
    }

    @NonNull
    static String prefixLaunchBlock(@NonNull String prefixTmp) {
        return "if [ -z \"${XDG_RUNTIME_DIR:-}\" ]; then\n"
            + "    XDG_RUNTIME_DIR=\"${TMPDIR:-" + prefixTmp + "}\"\n"
            + "    export XDG_RUNTIME_DIR\n"
            + "fi\n";
    }

    /**
     * The prefix's two files, written or refreshed. Does nothing until the prefix has its
     * {@code etc} (no bootstrap yet: a folder made early would get in the installer's way), so it
     * is asked again as each session is about to open. Cheap when nothing changed. Never throws.
     */
    public static void ensurePrefix() {
        try {
            if (!TermuxConstants.TERMUX_ETC_PREFIX_DIR.isDirectory()) return;
            String tmp = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH;
            File prefix = TermuxConstants.TERMUX_PREFIX_DIR;
            DistroDisplayProfile.write(prefix, SH_PATH, prefixSh(tmp));
            DistroDisplayProfile.write(prefix, FISH_PATH, prefixFish(tmp));
        } catch (IOException | RuntimeException e) {
            Logger.logWarn(LOG_TAG, "No runtime folder files: " + e.getMessage());
        }
    }

    /** One container's file. */
    static void writeContainer(@NonNull File rootfs) throws IOException {
        DistroDisplayProfile.write(rootfs, SH_PATH, containerSh());
    }
}
