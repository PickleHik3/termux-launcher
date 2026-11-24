package com.termux.app.wayland;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * What a Linux app inside a proot-distro container needs to open under Wayland Display: the
 * socket's directory bound into the container, and the variables that name the socket as the
 * container sees it. Pure; {@code X11LinuxAppRunner} puts the answer on the
 * {@code proot-distro login} line ({@code ProotDistro.LoginExtras}).
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

    /** Where the socket's directory is bound inside the container. */
    public static final String SOCKET_DIR_IN_CONTAINER = "/tmp/tl-wayland";

    private LinuxWindowsProot() {}

    /** The directory of {@code socketPath} (absolute), the one thing bound. */
    @NonNull
    static String hostDir(@NonNull String socketPath) {
        int slash = socketPath.lastIndexOf('/');
        if (!socketPath.startsWith("/") || slash < 1) {
            throw new IllegalArgumentException("socket path must be absolute: " + socketPath);
        }
        return socketPath.substring(0, slash);
    }

    /** The socket's path inside the container. */
    @NonNull
    public static String containerSocketPath(@NonNull String socketPath) {
        return SOCKET_DIR_IN_CONTAINER + socketPath.substring(socketPath.lastIndexOf('/'));
    }

    /** The {@code host:container} bind list for {@code proot-distro login --bind}. */
    @NonNull
    public static List<String> binds(@NonNull String socketPath) {
        return Collections.singletonList(hostDir(socketPath) + ":" + SOCKET_DIR_IN_CONTAINER);
    }

    /**
     * The variables for {@code -e}: {@link LinuxWindowsEnv#forClient} with the container's socket
     * path and no GPU profile, so {@code WAYLAND_DISPLAY}, {@code DISPLAY} while Xwayland runs
     * ({@code xDisplay} not {@link LinuxWindowsEnv#NO_X_DISPLAY}) and the toolkit hints.
     */
    @NonNull
    public static List<String> env(@NonNull String socketPath, int xDisplay) {
        hostDir(socketPath);
        return LinuxWindowsEnv.forClient(containerSocketPath(socketPath), xDisplay, null);
    }
}
