package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.termux.app.page.PageKind;

/**
 * What the page switcher key's cap draws, asked for by the cap itself as it is built or bound, so a
 * bar that is rebuilt, swapped to another key set or brought back always starts with the current
 * page's glyph and number and never with the key's label. The activity installs the provider; a
 * cap built before one exists draws the Terminal glyph until the next refresh.
 */
public final class PageGlyphSource {

    /** One glyph and its number. */
    public static final class Glyph {
        @NonNull public final PageKind kind;
        public final int number;

        public Glyph(@NonNull PageKind kind, int number) {
            this.kind = kind;
            this.number = number;
        }
    }

    public interface Provider {
        /** The glyph for the page the wall is on, right now. */
        @NonNull Glyph current();
    }

    private static final Glyph FALLBACK = new Glyph(PageKind.TERMINAL, 1);

    @Nullable private static volatile Provider sProvider;

    private PageGlyphSource() {}

    public static void install(@Nullable Provider provider) {
        sProvider = provider;
    }

    /** Drops {@code provider} if it is still the installed one, so a later activity's is kept. */
    public static void uninstall(@NonNull Provider provider) {
        if (sProvider == provider) sProvider = null;
    }

    @NonNull
    public static Glyph current() {
        Provider provider = sProvider;
        return provider == null ? FALLBACK : provider.current();
    }
}
