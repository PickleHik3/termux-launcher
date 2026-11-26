package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * The desktops the launcher knows, one {@link Desktop} entry each; adding a desktop is adding an
 * entry to {@link #ALL}. Pure: callers supply the file test and read answers back. "Installed" is
 * decided from files, never from a package database, because apt-based and pacman-based Termux
 * installs disagree on package revisions.
 */
public final class DesktopCatalogue {
    private DesktopCatalogue() {}

    /** The windowing system a desktop is made for. */
    public enum Kind { X11, WAYLAND }

    /** Where a desktop is, as far as the supplied file tests can tell. */
    public enum State { NOT_INSTALLED, INSTALLED, INSTALLED_IN_CONTAINER }

    /** One package of a desktop and the files, relative to the prefix, that show it is there. */
    public static final class Piece {
        @NonNull public final String packageName;
        @NonNull private final String[] files;

        Piece(@NonNull String packageName, @NonNull String... files) {
            this.packageName = packageName;
            this.files = files;
        }

        /** Any one of these files is enough; a copy. */
        @NonNull
        public String[] files() {
            return files.clone();
        }

        boolean presentIn(@NonNull Predicate<String> exists) {
            for (String file : files) if (exists.test(file)) return true;
            return false;
        }
    }

    /** One desktop. Fields are the table; the methods are answers read off it. */
    public static final class Desktop {
        @NonNull public final String id;
        @NonNull public final String name;
        @NonNull public final Kind kind;
        /** In install order; the first is the desktop itself and is its proof of install. */
        @NonNull private final Piece[] pieces;
        /** Files, relative to a distro's root, that show the desktop inside a container. */
        @NonNull private final String[] containerFiles;
        /** Folders in the user's home, relative to it, that hold the desktop's settings. */
        @NonNull private final String[] settingsFolders;
        /** The command the desktop's own documentation starts it with. */
        @NonNull public final String startCommand;
        /** Text for a starter config, or null for a desktop that needs none (X11 ones). */
        @Nullable public final String starterConfig;
        /** Whether this build offers it; false holds an entry back. */
        public final boolean offered;

        Desktop(@NonNull String id, @NonNull String name, @NonNull Kind kind,
                @NonNull Piece[] pieces, @NonNull String[] containerFiles,
                @NonNull String[] settingsFolders, @NonNull String startCommand,
                @Nullable String starterConfig, boolean offered) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.pieces = pieces;
            this.containerFiles = containerFiles;
            this.settingsFolders = settingsFolders;
            this.startCommand = startCommand;
            this.starterConfig = starterConfig;
            this.offered = offered;
        }

        /** The package names, in install order. */
        @NonNull
        public String[] packages() {
            String[] out = new String[pieces.length];
            for (int i = 0; i < out.length; i++) out[i] = pieces[i].packageName;
            return out;
        }

        @NonNull
        public String[] settingsFolders() {
            return settingsFolders.clone();
        }

        /**
         * Installed in the prefix when {@code prefixFile} finds any file of the desktop's own
         * package (relative to the prefix); else installed in a container when
         * {@code containerFile} finds one of its container files (relative to a distro root).
         */
        @NonNull
        public State state(@NonNull Predicate<String> prefixFile,
                           @NonNull Predicate<String> containerFile) {
            if (pieces[0].presentIn(prefixFile)) return State.INSTALLED;
            for (String file : containerFiles) {
                if (containerFile.test(file)) return State.INSTALLED_IN_CONTAINER;
            }
            return State.NOT_INSTALLED;
        }

        @Nullable
        Piece piece(@Nullable String packageName) {
            for (Piece piece : pieces) if (piece.packageName.equals(packageName)) return piece;
            return null;
        }
    }

    /**
     * XFCE from Termux's x11 repository. Its {@code xfce4} metapackage has no xfdesktop (a black
     * desktop with no wallpaper), so the pieces of a whole desktop are named: panel plugins,
     * whisker menu, file and archive tools, screenshots, clipboard, image viewer, editor and an
     * icon theme. An install that predates the full list has only {@code xfce4}.
     */
    public static final Desktop XFCE = new Desktop("xfce", "XFCE", Kind.X11,
        new Piece[]{
            new Piece("xfce4", "bin/startxfce4", "bin/xfce4-session"),
            new Piece("xfdesktop", "bin/xfdesktop"),
            new Piece("xfce4-pulseaudio-plugin",
                "lib/xfce4/panel/plugins/libpulseaudio-plugin.so",
                "share/xfce4/panel/plugins/pulseaudio.desktop"),
            new Piece("pavucontrol", "bin/pavucontrol"),
            new Piece("xfce4-whiskermenu-plugin",
                "lib/xfce4/panel/plugins/libwhiskermenu.so",
                "share/xfce4/panel/plugins/whiskermenu.desktop"),
            new Piece("tumbler", "lib/tumbler-1", "libexec/tumbler-1",
                "share/dbus-1/services/org.xfce.Tumbler.Thumbnailer1.service"),
            new Piece("thunar-archive-plugin", "lib/thunarx-3/thunar-archive-plugin.so"),
            new Piece("xarchiver", "bin/xarchiver"),
            new Piece("xfce4-screenshooter", "bin/xfce4-screenshooter"),
            new Piece("xfce4-clipman-plugin", "bin/xfce4-clipman",
                "lib/xfce4/panel/plugins/libclipman.so"),
            new Piece("ristretto", "bin/ristretto"),
            new Piece("mousepad", "bin/mousepad"),
            new Piece("papirus-icon-theme", "share/icons/Papirus")},
        new String[]{"usr/bin/startxfce4", "usr/bin/xfce4-session"},
        new String[]{".config/xfce4"}, "startxfce4", null, true);

    /** Every desktop, in the order they are listed. */
    private static final Desktop[] ALL = {XFCE};

    /** Every entry, offered or not. */
    @NonNull
    public static List<Desktop> all() {
        return java.util.Arrays.asList(ALL.clone());
    }

    /** The entries this build offers. */
    @NonNull
    public static List<Desktop> offered() {
        List<Desktop> out = new ArrayList<>();
        for (Desktop desktop : ALL) if (desktop.offered) out.add(desktop);
        return out;
    }

    /** The entry with {@code id}, or null. */
    @Nullable
    public static Desktop byId(@Nullable String id) {
        for (Desktop desktop : ALL) if (desktop.id.equals(id)) return desktop;
        return null;
    }

    /** The piece named {@code packageName} in any desktop, or null. */
    @Nullable
    static Piece pieceFor(@Nullable String packageName) {
        for (Desktop desktop : ALL) {
            Piece piece = desktop.piece(packageName);
            if (piece != null) return piece;
        }
        return null;
    }

    /** The packages of {@code wanted} whose files {@code exists} finds none of, in order. */
    @NonNull
    static List<String> missing(@Nullable String[] wanted, @NonNull Predicate<String> exists) {
        if (wanted == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String name : wanted) {
            Piece piece = pieceFor(name);
            if (piece != null && !piece.presentIn(exists)) out.add(name);
        }
        return out;
    }
}
