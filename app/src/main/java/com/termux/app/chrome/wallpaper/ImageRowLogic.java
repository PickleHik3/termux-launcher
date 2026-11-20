package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.scene.ShaderStore;

import java.util.Locale;

/**
 * The shader editor's Photo and Depth rows as plain state: which label, second line, progress bar
 * and (at most two) text buttons each situation shows. Pure, so every state is unit-tested and the
 * view only applies the answer.
 */
public final class ImageRowLogic {

    private ImageRowLogic() {}

    /** The first line. */
    public enum Label { PHOTO_NONE, PHOTO_NAMED, NONE, DEPTH_DOWNLOADING, DEPTH_MAKING, DEPTH_MADE, DEPTH_NAMED }

    /** The optional second line. */
    public enum Note { NONE, MAY_NOT_MATCH, FAILED }

    /** A text button. */
    public enum Action { CHOOSE, CHANGE, REMOVE, MAKE, GENERATE_DEPTH, CHOOSE_DEPTH, CANCEL, TRY_AGAIN }

    /** Where a depth run is. */
    public enum Run { IDLE, DOWNLOADING, MAKING }

    /** One row as the view shows it. */
    public static final class Row {
        @NonNull public final Label label;
        @NonNull public final Note note;
        /** The second line is drawn in the error colour. */
        public final boolean noteIsError;
        public final boolean showProgress;
        /** 0-100 for the bar when {@link #showProgress}. */
        public final int percent;
        /** A thumbnail of the row's image shows. */
        public final boolean showThumb;
        @Nullable public final Action first;
        public final boolean firstEnabled;
        @Nullable public final Action second;

        Row(@NonNull Label label, @NonNull Note note, boolean showProgress, int percent, boolean showThumb,
            @Nullable Action first, boolean firstEnabled, @Nullable Action second) {
            this.label = label;
            this.note = note;
            this.noteIsError = note == Note.FAILED;
            this.showProgress = showProgress;
            this.percent = percent;
            this.showThumb = showThumb;
            this.first = first;
            this.firstEnabled = firstEnabled;
            this.second = second;
        }
    }

    /** The Photo row: none with Choose image, or the file's name with Change and Remove. */
    @NonNull
    public static Row photo(@Nullable String photoName) {
        if (photoName == null) {
            return new Row(Label.PHOTO_NONE, Note.NONE, false, 0, false, Action.CHOOSE, true, null);
        }
        return new Row(Label.PHOTO_NAMED, Note.NONE, false, 0, true, Action.CHANGE, true, Action.REMOVE);
    }

    /**
     * The Depth area, or null while nothing about depth shows: no photo and no depth image. With a
     * photo and no depth it is the Generate depth button beside Choose depth image, with no label.
     *
     * @param photoSet     a photo is chosen
     * @param depthName    the draft's depth file, or null
     * @param photoChanged the photo changed after this depth was picked (a picked depth kept across it)
     * @param run          where a depth run is
     * @param percent      the run's 0-100
     * @param failed       the last run failed (ignored while one runs or a depth is set)
     */
    @Nullable
    public static Row depth(boolean photoSet, @Nullable String depthName, boolean photoChanged,
                            @NonNull Run run, int percent, boolean failed) {
        if (!photoSet && depthName == null && run == Run.IDLE) return null;
        int pct = Math.max(0, Math.min(100, percent));
        if (run == Run.DOWNLOADING) {
            return new Row(Label.DEPTH_DOWNLOADING, Note.NONE, true, pct, false, Action.CANCEL, true, null);
        }
        if (run == Run.MAKING) {
            return new Row(Label.DEPTH_MAKING, Note.NONE, true, pct, false, Action.CANCEL, true, null);
        }
        if (depthName == null) {
            if (failed) {
                return new Row(Label.NONE, Note.FAILED, false, 0, false, Action.TRY_AGAIN, true, Action.CHOOSE_DEPTH);
            }
            return new Row(Label.NONE, Note.NONE, false, 0, false, Action.GENERATE_DEPTH, true, Action.CHOOSE_DEPTH);
        }
        if (ShaderStore.isMadeDepth(depthName)) {
            return new Row(Label.DEPTH_MADE, Note.NONE, false, 0, true, Action.REMOVE, true, null);
        }
        if (photoChanged) {
            return new Row(Label.DEPTH_NAMED, Note.MAY_NOT_MATCH, false, 0, true, Action.MAKE, photoSet, Action.REMOVE);
        }
        return new Row(Label.DEPTH_NAMED, Note.NONE, false, 0, true, Action.CHANGE, true, Action.REMOVE);
    }

    /**
     * The download size as the app words it elsewhere: whole megabytes ("28 MB"), one decimal from a
     * gigabyte. At least "1 MB" for anything not empty.
     */
    @NonNull
    public static String formatSize(long bytes) {
        final double mb = 1024.0 * 1024.0;
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.1f GB", bytes / (mb * 1024.0));
        }
        long whole = Math.round(bytes / mb);
        return Math.max(bytes > 0 ? 1 : 0, whole) + " MB";
    }
}
