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
 * that version equals the installed Mesa's, the {@code Version:} of package {@code mesa} in
 * dpkg's status file. {@link #resolve} is pure; {@link #check} reads the two files.
 */
public final class MesaWaylandOverlay {

    public static final String ITEM_ID = "mesa-wayland-egl";
    public static final String DIR_PATH = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/opt/tl-mesa-wayland";
    public static final String LIB_DIR_PATH = DIR_PATH + "/lib";
    public static final String DPKG_STATUS_PATH = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/dpkg/status";

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
        String overlay = firstLine(overlayVersion);
        if (!libPresent || overlay.isEmpty()) return new Answer(State.MISSING, null, null, null);
        String mesa = mesaVersion(dpkgStatus);
        if (mesa == null) return new Answer(State.NO_MESA, null, overlay, null);
        if (!mesa.equals(overlay)) return new Answer(State.MISMATCH, null, overlay, mesa);
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

    /** Reads the overlay and dpkg files. Off the main thread; never throws. */
    @NonNull
    public static Answer check() {
        File dir = new File(DIR_PATH);
        boolean lib = new File(dir, "lib/libEGL_mesa.so.0").isFile();
        return resolve(lib, read(new File(dir, "MESA_VERSION")), read(new File(DPKG_STATUS_PATH)), LIB_DIR_PATH);
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
