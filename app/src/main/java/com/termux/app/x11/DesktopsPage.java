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

    /** Where the one install run stands; the run's own phase. */
    public enum RunPhase { IDLE, RUNNING, FINISHED, FAILED }

    /** What a row shows and offers. */
    public enum Status {
        /** Install is offered. */
        NOT_INSTALLED,
        /** Another desktop's run is going; Install waits. */
        UNAVAILABLE,
        /** This desktop's run is going. */
        INSTALLING,
        /** This desktop's run ended badly; its terminal stays and Install is offered again. */
        FAILED,
        INSTALLED
    }

    /** One desktop's row. */
    public static final class Row {
        @NonNull public final String desktopId;
        @NonNull public final String name;
        public final boolean installed;
        @NonNull public final Status status;
        private final boolean dismissesRun;

        Row(@NonNull String desktopId, @NonNull String name, boolean installed,
            @NonNull Status status, boolean dismissesRun) {
            this.desktopId = desktopId;
            this.name = name;
            this.installed = installed;
            this.status = status;
            this.dismissesRun = dismissesRun;
        }

        /** An installed desktop's row opens its settings page; the others have none yet. */
        public boolean opensSettings() {
            return status == Status.INSTALLED;
        }

        public boolean canInstall() {
            return status == Status.NOT_INSTALLED || status == Status.FAILED;
        }

        /** The small terminal under the row: while its run goes, and after it fails. */
        public boolean showsTerminal() {
            return status == Status.INSTALLING || status == Status.FAILED;
        }

        /** The run for this row finished and the desktop is in place: its transcript can go. */
        public boolean dismissesRun() {
            return dismissesRun;
        }
    }

    /** The rows with no run going. */
    @NonNull
    public static List<Row> rows(@NonNull Predicate<String> prefixFile,
                                 @NonNull Predicate<String> containerFile) {
        return rows(prefixFile, containerFile, null, RunPhase.IDLE);
    }

    /**
     * The rows, in catalogue order; installed per {@link DesktopCatalogue.Desktop#state}. The run
     * ({@code runDesktopId} and its {@code phase}) outranks the files for its own desktop: while
     * it goes the row is installing, however much of the desktop is already there; once it has
     * ended the row is installed when the desktop is in place and failed when not. While a run
     * goes every other row's Install is unavailable; a failed or finished run blocks nothing.
     */
    @NonNull
    public static List<Row> rows(@NonNull Predicate<String> prefixFile,
                                 @NonNull Predicate<String> containerFile,
                                 @Nullable String runDesktopId, @NonNull RunPhase phase) {
        boolean running = phase == RunPhase.RUNNING;
        List<Row> rows = new ArrayList<>();
        for (DesktopCatalogue.Desktop desktop : DesktopCatalogue.offered()) {
            boolean installed = desktop.state(prefixFile, containerFile)
                != DesktopCatalogue.State.NOT_INSTALLED;
            boolean mine = phase != RunPhase.IDLE && desktop.id.equals(runDesktopId);
            Status status;
            boolean dismisses = false;
            if (mine && running) {
                status = Status.INSTALLING;
            } else if (mine && phase == RunPhase.FAILED) {
                status = Status.FAILED;
            } else if (mine && !installed) {
                // Finished, yet the desktop is not there: the run did not do its job.
                status = Status.FAILED;
            } else if (installed) {
                status = Status.INSTALLED;
                dismisses = mine;
            } else {
                status = running ? Status.UNAVAILABLE : Status.NOT_INSTALLED;
            }
            rows.add(new Row(desktop.id, desktop.name, installed, status, dismisses));
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

    /** Whether a whole desktop with {@code desktopNames} is one the catalogue makes for Wayland. */
    public static boolean isWaylandSession(boolean session, @Nullable String desktopNames) {
        if (!session) return false;
        for (DesktopCatalogue.Desktop desktop : DesktopCatalogue.all()) {
            if (desktop.kind == DesktopCatalogue.Kind.WAYLAND && isSessionOf(desktop, desktopNames)) {
                return true;
            }
        }
        return false;
    }
}
