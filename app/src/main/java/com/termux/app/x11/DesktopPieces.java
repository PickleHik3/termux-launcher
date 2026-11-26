package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which pieces of a whole Termux XFCE desktop ({@link DesktopCatalogue#XFCE}) a prefix lacks,
 * read off the files each package provides (the table lives in {@link DesktopCatalogue}). An
 * install that predates the full list has only the {@code xfce4} metapackage, which starts a
 * desktop with no wallpaper. Pure: the caller supplies the file test.
 */
final class DesktopPieces {
    private DesktopPieces() {}

    /**
     * The files, relative to the prefix, that show {@code packageName} is installed: any one of
     * them is enough. Empty for a package the catalogue does not know, which is then never
     * reported.
     */
    @NonNull
    static String[] filesFor(@Nullable String packageName) {
        DesktopCatalogue.Piece piece = DesktopCatalogue.pieceFor(packageName);
        return piece == null ? new String[0] : piece.files();
    }

    /**
     * The packages of {@code wanted} whose files {@code exists} finds none of, in order. A null
     * list, or a package with no known file, reports nothing.
     */
    @NonNull
    static List<String> missing(@Nullable String[] wanted, @NonNull Predicate<String> exists) {
        return DesktopCatalogue.missing(wanted, exists);
    }

    /**
     * {@link #missing}, but nothing when the desktop itself ({@code xfce4}) is not there: there is
     * no desktop to complete, and a prefix with none of the files (a test host) is not offered one.
     */
    @NonNull
    static List<String> missingFromInstalledDesktop(@Nullable String[] wanted,
                                                    @NonNull Predicate<String> exists) {
        List<String> out = missing(wanted, exists);
        return out.contains("xfce4") ? Collections.emptyList() : out;
    }

    /** The command that installs {@code packages} on either package manager; null for none. */
    @Nullable
    static String installCommand(@NonNull List<String> packages) {
        if (packages.isEmpty()) return null;
        return "pkg install -y x11-repo && pkg install -y " + String.join(" ", packages);
    }

    /**
     * The argv of the terminal window that runs {@code command}: a login shell, so {@code pkg} is
     * found, that waits for Enter at the end so the result can be read.
     */
    @NonNull
    static List<String> windowCommand(@NonNull String bashPath, @NonNull String command) {
        return java.util.Arrays.asList(bashPath, "-lc",
            command + "; echo; read -r -p 'Press Enter to close ' _");
    }
}
