package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * What the Desktops page (Settings › Linux apps) shows: one {@link Row} per offered desktop, and
 * whether it is installed. Pure; the page supplies the file tests and builds the rows.
 */
public final class DesktopsPage {
    private DesktopsPage() {}

    /** One desktop's row. */
    public static final class Row {
        @NonNull public final String desktopId;
        @NonNull public final String name;
        public final boolean installed;

        Row(@NonNull String desktopId, @NonNull String name, boolean installed) {
            this.desktopId = desktopId;
            this.name = name;
            this.installed = installed;
        }

        /** An installed desktop's row opens its settings page; the others have none yet. */
        public boolean opensSettings() {
            return installed;
        }
    }

    /** The rows, in catalogue order; installed per {@link DesktopCatalogue.Desktop#state}. */
    @NonNull
    public static List<Row> rows(@NonNull Predicate<String> prefixFile,
                                 @NonNull Predicate<String> containerFile) {
        List<Row> rows = new ArrayList<>();
        for (DesktopCatalogue.Desktop desktop : DesktopCatalogue.offered()) {
            boolean installed = desktop.state(prefixFile, containerFile)
                != DesktopCatalogue.State.NOT_INSTALLED;
            rows.add(new Row(desktop.id, desktop.name, installed));
        }
        return rows;
    }

    /** A catalogue desktop installed in a {@code proot-distro} container: listed, never managed. */
    public static final class ContainerRow {
        @NonNull public final String desktopId;
        /** {@code XFCE · Debian}. */
        @NonNull public final String name;
        /** The session entry its settings page is keyed by ({@link LinuxAppCatalog.LinuxApp#id}). */
        @NonNull public final String appId;

        ContainerRow(@NonNull String desktopId, @NonNull String name, @NonNull String appId) {
            this.desktopId = desktopId;
            this.name = name;
            this.appId = appId;
        }

        @NonNull
        public DesktopCatalogue.State state() {
            return DesktopCatalogue.State.INSTALLED_IN_CONTAINER;
        }
    }

    /**
     * One row per catalogue desktop session the app catalogue found in a distro container. The
     * caller passes what {@link LinuxAppCatalog#scan} already read, so a removed container takes
     * its rows with it.
     */
    @NonNull
    public static List<ContainerRow> containerRows(@NonNull List<LinuxAppCatalog.LinuxApp> apps) {
        List<ContainerRow> rows = new ArrayList<>();
        for (LinuxAppCatalog.LinuxApp app : apps) {
            if (!app.session || app.container.kind != ProotDistro.Container.Kind.DISTRO) continue;
            for (DesktopCatalogue.Desktop desktop : DesktopCatalogue.offered()) {
                if (!isSessionOf(desktop, app.desktopNames)) continue;
                rows.add(new ContainerRow(desktop.id,
                    desktop.name + " · " + DisplayStartWith.distroLabel(app.container.name),
                    app.id));
                break;
            }
        }
        return rows;
    }

    /**
     * Whether a session whose {@code DesktopNames} is {@code desktopNames} (semicolon separated)
     * is {@code desktop}: how the page finds the app entry its settings page is keyed by.
     */
    public static boolean isSessionOf(@NonNull DesktopCatalogue.Desktop desktop,
                                      @Nullable String desktopNames) {
        if (desktopNames == null) return false;
        for (String name : desktopNames.split(";")) {
            if (name.trim().equalsIgnoreCase(desktop.name)) return true;
        }
        return false;
    }
}
