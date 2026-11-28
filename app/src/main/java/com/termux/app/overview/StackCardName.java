package com.termux.app.overview;

import androidx.annotation.Nullable;

/**
 * The name a terminal's card in Overview reads (terl-integration-1): the title its shell set, else
 * the program running in it, else the folder it stands in ({@code ~} for home), as v1's window bar
 * labels an idle pane by its directory. Null only when none is known, and the card then reads its
 * kind.
 *
 * <p>Pure.
 */
public final class StackCardName {

    private StackCardName() {}

    @Nullable
    public static String of(@Nullable String title, @Nullable String program, @Nullable String cwd,
                            @Nullable String home) {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        if (program != null && !program.trim().isEmpty()) return program.trim();
        if (cwd == null || cwd.trim().isEmpty()) return null;
        String dir = cwd.trim();
        while (dir.length() > 1 && dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
        if (home != null && dir.equals(home)) return "~";
        int cut = dir.lastIndexOf('/');
        String last = cut >= 0 && cut < dir.length() - 1 ? dir.substring(cut + 1) : dir;
        return last.isEmpty() ? null : last;
    }
}
