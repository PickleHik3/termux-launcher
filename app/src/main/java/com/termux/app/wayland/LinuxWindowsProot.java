package com.termux.app.wayland;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * What a Linux app inside a proot-distro container needs to open under Wayland Display: the
 * variables that name the socket, and no bind of its own. Pure; {@code X11LinuxAppRunner} puts
 * the answer on the {@code proot-distro login} line ({@code ProotDistro.LoginExtras}).
 *
 * <p>The socket has the same path inside the container as outside it
 * ({@code <prefix>/tmp/wayland-0}, see {@link LinuxWindowsEnv}): a default login, which is the
 * only kind the launcher makes, binds the whole prefix at its own path (proot-distro 5.x,
 * {@code commands/login/proot_cmd.py}), so {@code WAYLAND_DISPLAY} is that path and nothing more
 * is bound. The container's {@code /tmp} is its own: proot-distro shares the prefix's tmp there
 * only with {@code --shared-tmp}, whatever its help says about the default mode, so a socket in
 * the container's {@code /tmp} would need a bind that {@code /tmp/wayland-0} alone cannot make
 * safely (the socket comes and goes with the host). An {@code --isolated} login would need the
 * socket bound; the launcher never makes one.
 *
 * <p>The container renders in software, with its own Mesa. Nothing of the prefix's graphics goes
 * in: no GPU profile variable ({@code LIBGL_KOPPER_DRI2}, {@code MESA_LOADER_DRIVER_OVERRIDE},
 * {@code VK_DRIVER_FILES}) and no {@code LD_LIBRARY_PATH} (the prefix's libraries are bionic and
 * must never be on a glibc distro's path, the Mesa overlay's directory included). Xwayland's
 * socket needs no bind of its own: {@code --shared-x11} already maps the prefix's
 * {@code tmp/.X11-unix} to {@code /tmp/.X11-unix}, so {@code DISPLAY=:n} reaches the shared
 * Xwayland. Whole desktops in a container stay on X11 Display.
 */
public final class LinuxWindowsProot {

    private LinuxWindowsProot() {}

    private static void requireAbsolute(@NonNull String socketPath) {
        if (!socketPath.startsWith("/") || socketPath.lastIndexOf('/') < 1) {
            throw new IllegalArgumentException("socket path must be absolute: " + socketPath);
        }
    }

    /** The {@code host:container} bind list for {@code proot-distro login --bind}: none, see the class comment. */
    @NonNull
    public static List<String> binds(@NonNull String socketPath) {
        requireAbsolute(socketPath);
        return Collections.emptyList();
    }

    /**
     * The variables for {@code -e}: {@link LinuxWindowsEnv#forClient} with the socket's own path
     * and no GPU profile, so {@code WAYLAND_DISPLAY}, {@code DISPLAY} while Xwayland runs
     * ({@code xDisplay} not {@link LinuxWindowsEnv#NO_X_DISPLAY}) and the toolkit hints.
     */
    @NonNull
    public static List<String> env(@NonNull String socketPath, int xDisplay) {
        requireAbsolute(socketPath);
        return LinuxWindowsEnv.forClient(socketPath, xDisplay, null);
    }
}
