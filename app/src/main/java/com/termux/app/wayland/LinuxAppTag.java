package com.termux.app.wayland;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the Linux windows host keeps for the launcher so a restarted launcher can pick its Linux
 * apps back up: a tag on every process the host started for it ({@link #app}, {@link #desktop})
 * and a note on every window ({@link #note}). Both are opaque to the host and replayed to a new
 * listener; this class writes and reads them. Pure.
 */
public final class LinuxAppTag {

    private static final String APP = "app";
    private static final String DESKTOP = "desktop";
    private static final char SEP = '|';

    /** A tag read back; {@code display} is the desktop's X display number, -1 for an app. */
    public static final class Parsed {
        public final boolean desktop;
        @NonNull public final String key;
        public final int display;

        Parsed(boolean desktop, @NonNull String key, int display) {
            this.desktop = desktop;
            this.key = key;
            this.display = display;
        }
    }

    /** A window's note read back: the app it was opened for (null for none) and its kept scale (0 for none). */
    public static final class Note {
        @Nullable public final String key;
        public final float scale;

        Note(@Nullable String key, float scale) {
            this.key = key;
            this.scale = scale;
        }
    }

    private LinuxAppTag() {}

    /** The tag of a process started for the catalogue app {@code key}. */
    @NonNull
    public static String app(@NonNull String key) {
        return APP + SEP + key;
    }

    /** The tag of the desktop session {@code key} running on X display {@code display}. */
    @NonNull
    public static String desktop(int display, @NonNull String key) {
        return DESKTOP + SEP + display + SEP + key;
    }

    /** {@code tag} read back; null when it is not one of ours or is damaged. */
    @Nullable
    public static Parsed parse(@Nullable String tag) {
        if (tag == null) return null;
        int first = tag.indexOf(SEP);
        if (first < 0) return null;
        String kind = tag.substring(0, first);
        String rest = tag.substring(first + 1);
        if (APP.equals(kind)) return rest.isEmpty() ? null : new Parsed(false, rest, -1);
        if (!DESKTOP.equals(kind)) return null;
        int second = rest.indexOf(SEP);
        if (second <= 0 || second == rest.length() - 1) return null;
        int display;
        try {
            display = Integer.parseInt(rest.substring(0, second));
        } catch (NumberFormatException e) {
            return null;
        }
        if (display < 0) return null;
        return new Parsed(true, rest.substring(second + 1), display);
    }

    /**
     * A window's note: the app {@code key} it belongs to (null for none) and the scale kept for it
     * when it has no app (0 for none); null when there is nothing to keep.
     */
    @Nullable
    public static String note(@Nullable String key, float scale) {
        boolean hasKey = key != null && !key.isEmpty();
        boolean hasScale = scale > 0f && !Float.isInfinite(scale) && !Float.isNaN(scale);
        if (!hasKey && !hasScale) return null;
        return (hasScale ? Float.toString(scale) : "") + SEP + (hasKey ? key : "");
    }

    /** {@code note} read back; null when it is empty or damaged. */
    @Nullable
    public static Note parseNote(@Nullable String note) {
        if (note == null) return null;
        int sep = note.indexOf(SEP);
        if (sep < 0) return null;
        float scale = 0f;
        if (sep > 0) {
            try {
                scale = Float.parseFloat(note.substring(0, sep));
            } catch (NumberFormatException e) {
                return null;
            }
            if (!(scale > 0f) || Float.isInfinite(scale)) scale = 0f;
        }
        String key = note.substring(sep + 1);
        if (key.isEmpty()) key = null;
        if (key == null && scale == 0f) return null;
        return new Note(key, scale);
    }

    /**
     * Where the output of the app {@code key} goes, under {@code tmpDir}: one file per app,
     * truncated at each start, never the launcher's pipes, which end with the launcher.
     */
    @NonNull
    public static String logPath(@NonNull String tmpDir, @NonNull String key) {
        StringBuilder name = new StringBuilder(key.length());
        for (int i = 0; i < key.length() && name.length() < 80; i++) {
            char c = key.charAt(i);
            boolean plain = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '.' || c == '-' || c == '_';
            name.append(plain ? c : '_');
        }
        if (name.length() == 0) name.append('_');
        return tmpDir + "/linux-app-" + name + ".log";
    }
}
