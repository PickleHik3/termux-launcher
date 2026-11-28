package com.termux.app.colours;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The terminals' colour scheme as one choice: {@link #WALLPAPER} (Wallpaper colours on), a built-in
 * scheme's id ({@link TerminalSchemes}), or {@link #OWN} (Wallpaper colours off, with whatever
 * colors.properties the user had, or Termux's own colours when there was none). The Windows card's
 * Text tab and the {@code terminal.colours} setting both choose through it.
 *
 * <p>colors.properties is written only for a built-in scheme, and only once Wallpaper colours is
 * off: choosing turns it off first. Choosing Wallpaper colours leaves the file alone.
 */
public final class TerminalColours {

    public static final String WALLPAPER = "wallpaper";
    public static final String OWN = "own";

    /** Where Wallpaper colours is kept. */
    public interface Source {
        boolean fromWallpaper();

        void setFromWallpaper(boolean on);
    }

    @NonNull private final Source mSource;
    @NonNull private final ColourSchemeFile mFile;

    public TerminalColours(@NonNull Source source, @NonNull ColourSchemeFile file) {
        mSource = source;
        mFile = file;
    }

    /** Every value {@link #choose} takes, in the card's order. */
    @NonNull
    public static List<String> values() {
        List<String> out = new ArrayList<>();
        out.add(WALLPAPER);
        out.add(OWN);
        for (TerminalSchemes.Scheme scheme : TerminalSchemes.all()) out.add(scheme.id);
        return Collections.unmodifiableList(out);
    }

    /** The choice in force now. */
    @NonNull
    public String current() {
        if (mSource.fromWallpaper()) return WALLPAPER;
        String id = mFile.schemeId();
        return id != null && TerminalSchemes.of(id) != null ? id : OWN;
    }

    /** Whether the user has colours of their own to go back to (the {@link #OWN} chip shows). */
    public boolean hasOwn() {
        return mFile.hasOwn();
    }

    /**
     * Wears {@code value}; false, with nothing changed, for a value that names nothing or a file
     * that could not be written. True also when it was already worn.
     */
    public boolean choose(@NonNull String value) {
        if (value.equals(current())) return true;
        if (WALLPAPER.equals(value)) {
            mSource.setFromWallpaper(true);
            return true;
        }
        TerminalSchemes.Scheme scheme = TerminalSchemes.of(value);
        if (scheme == null && !OWN.equals(value)) return false;
        // Off first: the file is never written while Wallpaper colours is on.
        boolean wasWallpaper = mSource.fromWallpaper();
        if (wasWallpaper) mSource.setFromWallpaper(false);
        try {
            if (scheme != null) mFile.write(scheme);
            else mFile.restoreOwn();
            return true;
        } catch (IOException e) {
            // Nothing written: the colours stay as they were.
            if (wasWallpaper) mSource.setFromWallpaper(true);
            return false;
        }
    }
}
