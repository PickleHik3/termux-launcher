package com.termux.app.sound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Decision 11's artwork: local files only. A player in a proot distro names its art by a path
 * inside the distro, so the path is looked up in that distro's rootfs first; which distro is read
 * off the player's process ancestry, where {@code proot} was started with the rootfs on its
 * command line. Pure; the caller reads {@code /proc} and the filesystem.
 */
final class ProotArt {

    private ProotArt() {}

    /** The 5.x layout, and the one before it. */
    private static final String[] CONTAINER_MARKERS = {
        "/proot-distro/containers/", "/proot-distro/installed-rootfs/"};

    /**
     * The distro a process runs in, from its ancestors' command lines (its own first): the first
     * argument naming a {@code proot-distro} rootfs. Null for a process of the prefix.
     */
    @Nullable
    static String distroOf(@NonNull List<List<String>> ancestry) {
        for (List<String> argv : ancestry) {
            for (String argument : argv) {
                for (String marker : CONTAINER_MARKERS) {
                    int at = argument.indexOf(marker);
                    if (at < 0) continue;
                    String rest = argument.substring(at + marker.length());
                    int slash = rest.indexOf('/');
                    String name = slash < 0 ? rest : rest.substring(0, slash);
                    if (!name.isEmpty()) return name;
                }
            }
        }
        return null;
    }

    /** The parent pid out of {@code /proc/PID/stat}; 0 when it cannot be read. */
    static int parentPid(@Nullable String stat) {
        if (stat == null) return 0;
        // The command name is in parentheses and may hold spaces and parentheses itself.
        int close = stat.lastIndexOf(')');
        if (close < 0) return 0;
        String[] fields = stat.substring(close + 1).trim().split("\\s+");
        if (fields.length < 2) return 0;
        try {
            return Integer.parseInt(fields[1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * The absolute path an {@code mpris:artUrl} names, or null for anything but a local file
     * ({@code http} art is skipped). {@code file:///a%20b.jpg} and a bare {@code /a b.jpg} both
     * give {@code /a b.jpg}.
     */
    @Nullable
    static String localPath(@Nullable String artUrl) {
        if (artUrl == null || artUrl.isEmpty()) return null;
        String path;
        if (artUrl.startsWith("file://")) {
            path = artUrl.substring("file://".length());
            if (path.startsWith("localhost/")) path = path.substring("localhost".length());
            path = percentDecode(path);
        } else if (artUrl.startsWith("/")) {
            path = artUrl;
        } else {
            return null;
        }
        if (path == null || !path.startsWith("/") || path.contains("/../")) return null;
        return path;
    }

    /** Which file to read for art at {@code path}. */
    interface Files {
        boolean isFile(@NonNull File file);
    }

    /**
     * The file for {@code path}: inside {@code rootfs} first, for a distro's player; the path as
     * it is otherwise, which is also where a distro's bind mounts ({@code /sdcard}, {@code /tmp})
     * really are. Null when neither is a file.
     */
    @Nullable
    static File resolve(@NonNull String path, @Nullable File rootfs, @NonNull Files files) {
        if (rootfs != null) {
            File inside = new File(rootfs, path.substring(1));
            if (files.isFile(inside)) return inside;
        }
        File host = new File(path);
        return files.isFile(host) ? host : null;
    }

    /** RFC 3986 percent-decoding as UTF-8; a {@code +} stays a plus. Null when malformed. */
    @Nullable
    static String percentDecode(@NonNull String value) {
        if (value.indexOf('%') < 0) return value;
        ByteArrayOutputStream out = new ByteArrayOutputStream(value.length());
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < raw.length; i++) {
            byte b = raw[i];
            if (b != '%') {
                out.write(b);
                continue;
            }
            if (i + 2 >= raw.length) return null;
            int high = Character.digit(raw[i + 1], 16);
            int low = Character.digit(raw[i + 2], 16);
            if (high < 0 || low < 0) return null;
            out.write((high << 4) | low);
            i += 2;
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
