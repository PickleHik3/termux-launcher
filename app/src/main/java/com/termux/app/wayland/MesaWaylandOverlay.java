package com.termux.app.wayland;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Whether the patched {@code libEGL_mesa.so.0} that tlstore's {@code mesa-wayland-egl} item
 * installs can be used. The item puts the library and a {@code MESA_VERSION} line (the Termux
 * {@code mesa} package version it was built from) in {@link #DIR_PATH}; it is used only while
 * that version names the same Mesa release as the installed one: the {@code Version:} of package
 * {@code mesa} in dpkg's status file, else the {@code %VERSION%} of the pacman local database
 * entry whose {@code %NAME%} is {@code mesa}. Only the upstream release is compared (epoch and
 * package revision dropped): the library must match Mesa's release, while the package revision
 * differs between package managers ({@code 26.3.1-0} on pacman, {@code 26.3.1-1} on apt).
 * {@link #resolve} is pure; {@link #check} reads the files.
 */
public final class MesaWaylandOverlay {

    public static final String ITEM_ID = "mesa-wayland-egl";
    public static final String DIR_PATH = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/opt/tl-mesa-wayland";
    public static final String LIB_DIR_PATH = DIR_PATH + "/lib";
    public static final String DPKG_STATUS_PATH = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/dpkg/status";

    public static final String PACMAN_LOCAL_PATH = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/pacman/local";

    public enum State { USE, MISSING, MISMATCH, NO_MESA }

    /** One answer; {@link #libDir} only for USE, the versions only where known. */
    public static final class Answer {
        @NonNull public final State state;
        @Nullable public final String libDir;
        @Nullable public final String overlayVersion;
        @Nullable public final String mesaVersion;

        Answer(@NonNull State state, @Nullable String libDir, @Nullable String overlayVersion,
               @Nullable String mesaVersion) {
            this.state = state;
            this.libDir = libDir;
            this.overlayVersion = overlayVersion;
            this.mesaVersion = mesaVersion;
        }
    }

    private MesaWaylandOverlay() {}

    /**
     * @param libPresent     the overlay's library file exists.
     * @param overlayVersion the text of {@code MESA_VERSION}, or null when absent.
     * @param dpkgStatus     the text of dpkg's status file, or null when unreadable.
     * @param libDir         what USE answers with.
     */
    @NonNull
    public static Answer resolve(boolean libPresent, @Nullable String overlayVersion,
                                 @Nullable String dpkgStatus, @NonNull String libDir) {
        return resolveVersion(libPresent, overlayVersion, mesaVersion(dpkgStatus), libDir);
    }

    /** As {@link #resolve} with the installed Mesa version already found (null when none). */
    @NonNull
    public static Answer resolveVersion(boolean libPresent, @Nullable String overlayVersion,
                                        @Nullable String mesa, @NonNull String libDir) {
        String overlay = firstLine(overlayVersion);
        if (!libPresent || overlay.isEmpty()) return new Answer(State.MISSING, null, null, null);
        if (mesa == null) return new Answer(State.NO_MESA, null, overlay, null);
        if (!upstreamVersion(mesa).equals(upstreamVersion(overlay))) return new Answer(State.MISMATCH, null, overlay, mesa);
        return new Answer(State.USE, libDir, overlay, mesa);
    }

    /** The {@code Version:} of package {@code mesa} in dpkg status text, or null. Tolerant of CRLF and junk. */
    @Nullable
    public static String mesaVersion(@Nullable String dpkgStatus) {
        if (dpkgStatus == null) return null;
        boolean inMesa = false;
        for (String raw : dpkgStatus.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.trim().isEmpty()) { inMesa = false; continue; }
            if (line.startsWith("Package:")) {
                inMesa = line.substring("Package:".length()).trim().equals("mesa");
            } else if (inMesa && line.startsWith("Version:")) {
                String v = line.substring("Version:".length()).trim();
                return v.isEmpty() ? null : v;
            }
        }
        return null;
    }

    /** The version without its epoch ({@code N:}) and package revision (from the last {@code -}). */
    @NonNull
    public static String upstreamVersion(@NonNull String version) {
        String v = version.trim();
        int colon = v.indexOf(':');
        if (colon >= 0) v = v.substring(colon + 1);
        int dash = v.lastIndexOf('-');
        return dash > 0 ? v.substring(0, dash) : v;
    }

    /** The {@code %VERSION%} of a pacman {@code desc} whose {@code %NAME%} is exactly {@code mesa}, or null. */
    @Nullable
    public static String pacmanMesaVersion(@Nullable String desc) {
        if (desc == null) return null;
        String name = null, version = null, section = null;
        for (String raw : desc.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty()) { section = null; continue; }
            if (line.startsWith("%") && line.endsWith("%")) { section = line; continue; }
            if ("%NAME%".equals(section) && name == null) name = line;
            else if ("%VERSION%".equals(section) && version == null) version = line;
        }
        return "mesa".equals(name) ? version : null;
    }

    @Nullable
    private static String installedMesaVersion() {
        String v = mesaVersion(read(new File(DPKG_STATUS_PATH)));
        if (v != null) return v;
        try {
            File[] dirs = new File(PACMAN_LOCAL_PATH).listFiles();
            if (dirs == null) return null;
            for (File d : dirs) {
                if (!d.getName().startsWith("mesa-")) continue;
                v = pacmanMesaVersion(read(new File(d, "desc")));
                if (v != null) return v;
            }
        } catch (RuntimeException e) {
            // unreadable database: treated as no Mesa
        }
        return null;
    }

    /** Reads the overlay and package database files. Off the main thread; never throws. */
    @NonNull
    public static Answer check() {
        File dir = new File(DIR_PATH);
        boolean lib = new File(dir, "lib/libEGL_mesa.so.0").isFile();
        return resolveVersion(lib, read(new File(dir, "MESA_VERSION")), installedMesaVersion(), LIB_DIR_PATH);
    }

    @Nullable
    private static String read(@NonNull File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @NonNull
    private static String firstLine(@Nullable String text) {
        if (text == null) return "";
        int nl = text.indexOf('\n');
        return (nl < 0 ? text : text.substring(0, nl)).trim();
    }
}
