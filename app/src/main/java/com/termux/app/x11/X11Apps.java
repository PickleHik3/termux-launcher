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

    /**
     * What marks an id as a whole desktop session rather than an application. It sits
     * <em>inside</em> the desktop-file part of an id — {@code session:xfce},
     * {@code distro:debian:session:xfce} — so {@link #qualify}, {@link #containerOf} and every
     * existing pin, ranking and folder go on reading an id exactly as they did. Without it,
     * {@code xfce.desktop} in {@code applications} and {@code xfce.desktop} in {@code xsessions}
     * are the same id and the catalogue's de-duplication silently drops the second.
     */
    public static final String SESSION_PREFIX = "session:";

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

    /**
     * The launcher-wide id of a session file — {@link #qualify} over a desktop-file name carrying
     * {@link #SESSION_PREFIX}. The marker goes inside, never in front of the container, so the id
     * still starts with {@code distro:} for a session installed in a container.
     */
    @NonNull
    public static String qualifySession(@NonNull String container, @NonNull String desktopFileId) {
        return qualify(container, SESSION_PREFIX + desktopFileId);
    }

    /**
     * The desktop file's own name behind an id, whichever kind of id it is. A session's still
     * carries {@link #SESSION_PREFIX}; {@link #desktopFileNameOf} is the one that takes it off.
     */
    @NonNull
    public static String desktopFileOf(@NonNull String id) {
        if (!id.startsWith(CONTAINER_PREFIX)) return id;
        int end = id.indexOf(':', CONTAINER_PREFIX.length());
        return end < 0 ? id : id.substring(end + 1);
    }

    /** Whether an id names a whole desktop session rather than an application. */
    public static boolean isSessionId(@NonNull String id) {
        return desktopFileOf(id).startsWith(SESSION_PREFIX);
    }

    /**
     * The name of the {@code .desktop} file behind an id, with the session marker taken off — what
     * to look an icon up by, or to show in a diagnostic. Same as {@link #desktopFileOf} for an
     * application.
     */
    @NonNull
    public static String desktopFileNameOf(@NonNull String id) {
        String file = desktopFileOf(id);
        return file.startsWith(SESSION_PREFIX) ? file.substring(SESSION_PREFIX.length()) : file;
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
