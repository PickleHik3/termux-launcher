package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * The launcher's login script inside each proot-distro container,
 * {@code /etc/profile.d/termux-launcher-display.sh}: a shell the user opens by hand
 * ({@code proot-distro login debian}, ssh, a boot script) finds the launcher's display the way a
 * desktop session would, with no variable handed in. Apps the launcher starts keep getting their
 * variables on the login line as before.
 *
 * <p>The script itself decides at login time, so one text serves every state: it sets
 * {@code WAYLAND_DISPLAY} only while the launcher's Wayland socket is there and {@code DISPLAY}
 * only while an X socket is reachable, and only when the shell has neither already. It never sets
 * {@code XDG_RUNTIME_DIR}, GPU variables or library paths.
 *
 * <p>Read by login shells that read {@code /etc/profile} (sh, bash; zsh only where the distro's
 * {@code zprofile} sources it, as Arch's does and Debian's does not). fish reads
 * {@code /etc/fish/conf.d}, not {@code profile.d}, and gets nothing from this file.
 *
 * <p>Written into every installed container, refreshed when its text changes (a launcher update),
 * never over a file that is not the launcher's. A removed container takes it along; there is no
 * other undo.
 */
public final class DistroDisplayProfile {

    /** The script's place, relative to a container's rootfs. */
    public static final String PATH = "etc/profile.d/termux-launcher-display.sh";
    /** Where X clients look for {@code DISPLAY=:n}'s socket inside a container. */
    public static final String X_SOCKET_DIR = "/tmp/.X11-unix";
    /** Every file the launcher writes carries this line, so "is it ours?" is a read. */
    static final String MARKER = "# written by termux-launcher";
    private static final String LOG_TAG = "DistroDisplayProfile";
    /** Far above the script's size; a bigger file is somebody else's. */
    private static final int MAX_BYTES = 16 * 1024;

    private DistroDisplayProfile() {}

    /**
     * The script's text.
     *
     * @param waylandSocket the launcher's Wayland socket, absolute; the same path inside a
     *                      container, because a default login binds the prefix at its own path
     * @param xSocketDir    where X sockets are, {@link #X_SOCKET_DIR} in a container
     */
    @NonNull
    public static String script(@NonNull String waylandSocket, @NonNull String xSocketDir) {
        return MARKER + " for its display; it rewrites this file.\n"
            + "# Points programs started from a login shell at the launcher's display while it is there.\n"
            + "# WAYLAND_DISPLAY: the launcher's Wayland socket, at the same path as in Termux. A default\n"
            + "# proot-distro login binds the Termux prefix at its own path; an --isolated login does not,\n"
            + "# and then nothing is set.\n"
            + "# DISPLAY: X11 programs need a login that shares Termux's X sockets, made with\n"
            + "# --shared-tmp or --shared-x11. Without one, " + xSocketDir + " is the distro's own.\n"
            + "# Never sets XDG_RUNTIME_DIR.\n"
            + "if [ -z \"${WAYLAND_DISPLAY:-}\" ] && [ -S \"" + waylandSocket + "\" ]; then\n"
            + "    WAYLAND_DISPLAY=\"" + waylandSocket + "\"\n"
            + "    export WAYLAND_DISPLAY\n"
            + "fi\n"
            + "if [ -z \"${DISPLAY:-}\" ]; then\n"
            + "    termux_launcher_display=\n"
            + "    for termux_launcher_x in \"" + xSocketDir + "\"/X*; do\n"
            + "        [ -S \"$termux_launcher_x\" ] || continue\n"
            + "        termux_launcher_n=${termux_launcher_x##*/X}\n"
            + "        case $termux_launcher_n in\n"
            + "            ''|*[!0-9]*) continue ;;\n"
            + "        esac\n"
            + "        if [ -z \"$termux_launcher_display\" ] || [ \"$termux_launcher_n\" -lt \"$termux_launcher_display\" ]; then\n"
            + "            termux_launcher_display=$termux_launcher_n\n"
            + "        fi\n"
            + "    done\n"
            + "    if [ -n \"$termux_launcher_display\" ]; then\n"
            + "        DISPLAY=:$termux_launcher_display\n"
            + "        export DISPLAY\n"
            + "    fi\n"
            + "    unset termux_launcher_display termux_launcher_x termux_launcher_n\n"
            + "fi\n";
    }

    /**
     * Every installed container's script, written or refreshed; a container that refuses it is
     * logged and skipped. Blocking file work: call it off the main thread. Never throws.
     */
    public static void refreshInstalled() {
        try {
            String wanted = script(com.termux.app.wayland.WaylandAppRunner.socketPath(), X_SOCKET_DIR);
            for (ProotDistro.Container container : ProotDistro.installed()) {
                if (container.kind != ProotDistro.Container.Kind.DISTRO) continue;
                try {
                    if (write(container.root, wanted) == Result.FOREIGN) {
                        Logger.logInfo(LOG_TAG, "Leaving a display profile in " + container.name
                            + " that is not the launcher's");
                    }
                } catch (IOException | RuntimeException e) {
                    Logger.logWarn(LOG_TAG, "No display profile for " + container.name + ": "
                        + e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "No display profiles: " + e.getMessage());
        }
    }

    /** What {@link #write} did. */
    public enum Result {
        /** Absent or stale, and written. */
        WRITTEN,
        /** Already the wanted text. */
        KEPT,
        /** A file that is not the launcher's sits there; left alone. */
        FOREIGN
    }

    /**
     * Puts {@code wanted} at {@link #PATH} under {@code rootfs} unless it is already there or the
     * file there is not the launcher's. Written beside the destination and moved into place, so a
     * login never reads half a file; a symlink is refused, not followed.
     */
    @NonNull
    public static Result write(@NonNull File rootfs, @NonNull String wanted) throws IOException {
        File destination = new File(rootfs, PATH);
        if (Files.isSymbolicLink(destination.toPath())) {
            throw new IOException(destination + " is a symlink; refusing to write through it");
        }
        if (destination.exists()) {
            String current = read(destination);
            if (current == null || !current.contains(MARKER)) return Result.FOREIGN;
            if (current.equals(wanted)) return Result.KEPT;
        }
        File dir = destination.getParentFile();
        if (dir == null) throw new IOException(destination + " has no directory");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Failed to create " + dir);
        File temp = File.createTempFile(".termux-launcher-", ".tmp", dir);
        try {
            try (OutputStream out = new FileOutputStream(temp, false)) {
                out.write(wanted.getBytes(StandardCharsets.UTF_8));
            }
            // 0644: every account in the container reads it; profile.d scripts are sourced.
            temp.setReadable(true, false);
            temp.setWritable(false, false);
            temp.setWritable(true, true);
            temp.setExecutable(false, false);
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
        return Result.WRITTEN;
    }

    @Nullable
    private static String read(@NonNull File file) {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_BYTES) return null;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
