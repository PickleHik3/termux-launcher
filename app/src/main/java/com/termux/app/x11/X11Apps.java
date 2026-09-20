package com.termux.app.x11;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.AppRef;

/**
 * How a Linux app is named inside the launcher's app catalogue: an {@link AppRef} whose package
 * is the reserved {@link #PACKAGE} and whose activity is the desktop file's id. Everything keyed
 * by {@code stableId} — usage ranking, pins, folders — works unchanged; the few places that ask
 * Android about a package check {@link #isLinuxApp} first.
 */
public final class X11Apps {

    /** Never a real Android package name: no dot, and colons are not allowed in one. */
    public static final String PACKAGE = "x11:linux";

    /**
     * What marks an id as belonging to a distro container rather than to the prefix. The same
     * {@code firefox.desktop} can be installed in the prefix, in Debian and in Arch at once, so a
     * container app's id carries its container: {@code distro:<container>:<desktop file>}. A
     * prefix app's id stays the bare desktop-file name it has always been, which is what keeps
     * existing pins, rankings and folders from noticing this change at all.
     */
    private static final String CONTAINER_PREFIX = "distro:";

    private X11Apps() {}

    /**
     * The launcher-wide id of a desktop file: the file's own name for a prefix app, the name
     * qualified by its container for one installed inside a distro. A container name cannot
     * contain a colon — it is checked against a safe alphabet before the container is listed at
     * all — so the first colon after the marker always ends the container, whatever the desktop
     * file is called.
     */
    @NonNull
    public static String qualify(@NonNull String container, @NonNull String desktopFileId) {
        return container.isEmpty() ? desktopFileId
            : CONTAINER_PREFIX + container + ":" + desktopFileId;
    }

    /** The container an id names, or empty when the id is a prefix app's. */
    @NonNull
    public static String containerOf(@NonNull String id) {
        if (!id.startsWith(CONTAINER_PREFIX)) return "";
        int end = id.indexOf(':', CONTAINER_PREFIX.length());
        return end < 0 ? "" : id.substring(CONTAINER_PREFIX.length(), end);
    }

    /** The desktop file's own name behind an id, whichever kind of id it is. */
    @NonNull
    public static String desktopFileOf(@NonNull String id) {
        if (!id.startsWith(CONTAINER_PREFIX)) return id;
        int end = id.indexOf(':', CONTAINER_PREFIX.length());
        return end < 0 ? id : id.substring(end + 1);
    }

    @NonNull
    public static AppRef ref(@NonNull String desktopId) {
        return new AppRef(PACKAGE, desktopId);
    }

    public static boolean isLinuxApp(@NonNull AppRef ref) {
        return PACKAGE.equals(ref.packageName);
    }

    /**
     * The id behind a Linux app's ref: {@link #qualify}'s output, so a container app's carries its
     * container. {@link #containerOf} and {@link #desktopFileOf} take it apart.
     */
    @NonNull
    public static String desktopId(@NonNull AppRef ref) {
        return ref.activityName;
    }
}
