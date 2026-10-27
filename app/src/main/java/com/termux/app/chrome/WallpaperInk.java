package com.termux.app.chrome;

import android.graphics.Color;

import androidx.annotation.ColorInt;

import com.termux.app.theme.SchemeTone;

/**
 * Text over the wallpaper follows the wallpaper (editor-redesign §8): what the launcher writes
 * straight on the wallpaper, or on glass so thin the wallpaper is what shows, takes light or dark
 * ink from the wallpaper under it, never from the theme. The theme still decides sheets, cards and
 * glassed surfaces: on a glass face the theme's ink already reads, and it is kept.
 *
 * <p>There is no opacity threshold. A surface's face is its glass base composited at the glass's
 * own alpha over the wallpaper ({@link #faceOf}), so at opacity 0 the face <em>is</em> the
 * wallpaper and at 100 it is the theme's glass, and everything between weighs the two the way the
 * screen does. The ink is then chosen on that face ({@link #stripInk}).</p>
 *
 * <p>Where no wallpaper could be read (no blur frame resident, a wallpaper the launcher cannot
 * read), the old answer was the mode's nominal glass, which made every bare ink follow the theme:
 * a dark theme over a light wallpaper kept pale letters and pale extra-keys icons.
 * {@link #fallbackWallpaper} asks the system's own description of the wallpaper first.</p>
 *
 * <p>Pure, static and context free: every surface's rule is held by JVM tests.</p>
 */
public final class WallpaperInk {

    private WallpaperInk() {}

    /** What a wallpaper the system calls light is taken as when its primary colour reads dark. */
    static final int LIGHT_STAND_IN = 0xFFE6E6E6;
    /** What a wallpaper the system calls dark is taken as when its primary colour reads light. */
    static final int DARK_STAND_IN = 0xFF1F1F1F;

    // ------------------------------------------------------------------ the face

    /**
     * The opaque face content stands on: the wallpaper sample, the launcher's dim over it, and the
     * band's glass base at the alpha its opacity gives ({@link ChromePolicy#dockGlassBaseAlpha}).
     * The extra keys' face, and {@link ChromeInk#faceUnder}'s arithmetic.
     */
    @ColorInt
    public static int faceOf(@ColorInt int wallpaper, @ColorInt int dim, @ColorInt int glassBase,
                             float glassOpacity) {
        return faceOfAlpha(wallpaper, dim, glassBase, ChromePolicy.dockGlassBaseAlpha(glassOpacity));
    }

    /** {@link #faceOf} with the glass base's alpha already worked out. */
    @ColorInt
    public static int faceOfAlpha(@ColorInt int wallpaper, @ColorInt int dim,
                                  @ColorInt int glassBase, int baseAlpha) {
        int under = OnGlass.backdrop(wallpaper, dim, Color.TRANSPARENT);
        return OnGlass.opaque(OnGlass.composite(OnGlass.withAlpha(glassBase, baseAlpha), under));
    }

    // ------------------------------------------------------------------ the surfaces

    /**
     * The extra keys' labels and icons (and any strip whose theme ink is a role colour on a glass
     * face): {@code themeInk} wherever it reads on {@code face}; otherwise the nearest tone of it
     * that does, on whichever side reads. Glassed in the theme's own base, the face is the theme's
     * and the ink is the theme's; with the glass off, the face is the wallpaper and a dark theme
     * over a light wallpaper gets dark ink.
     */
    @ColorInt
    public static int stripInk(@ColorInt int face, @ColorInt int themeInk, double target) {
        return GlassInk.faceInk(face, themeInk, target);
    }

    /**
     * The glyph of a tile on the dock's apps row (the Drawer tile, the Command palette tile): the
     * tile's shell, the folders' wash, laid over the dock's face under the row, and on that
     * {@link #stripInk} of the theme's ink. A dark theme's pale glyph on a dock over a light
     * wallpaper turns dark; on the theme's own glass it stays the theme's. Before the dock's face
     * has been measured ({@code dockFace} transparent) the theme's ink stands.
     *
     * @param dockFace the opaque face measured under the row ({@link ChromeInk#faceUnder}), or
     *     {@link Color#TRANSPARENT} before a sample
     * @param shellFill the tile shell's own fill, translucent
     */
    @ColorInt
    public static int tileInk(@ColorInt int dockFace, @ColorInt int shellFill,
                              @ColorInt int themeInk, double target) {
        if (Color.alpha(dockFace) == 0) return themeInk;
        int face = OnGlass.opaque(OnGlass.composite(shellFill, OnGlass.opaque(dockFace)));
        return stripInk(face, themeInk, target);
    }

    /**
     * Text written straight on the wallpaper with no theme colour of its own to keep — Home's
     * icon labels, the empty page's hint, the page dots: whichever of the two inks contrasts more
     * with the wallpaper under it. A light wallpaper takes {@code darkInk} in either theme.
     */
    @ColorInt
    public static int bareInk(@ColorInt int wallpaper, @ColorInt int darkInk,
                              @ColorInt int paleInk) {
        return SchemeTone.mostLegible(OnGlass.opaque(wallpaper), darkInk, paleInk);
    }

    /**
     * The soft shadow under a bare label: away from the ink (a dark shadow under pale writing, a
     * pale glow under dark writing), at an alpha that grows the less the ink stands out from the
     * wallpaper — a mid-tone wallpaper gets more of it than a near-white one — and never so much
     * that the shadow out-reads the writing ({@link GlassInk#haloAlpha}).
     */
    @ColorInt
    public static int labelShadow(@ColorInt int ink, @ColorInt int wallpaper) {
        int base = OnGlass.opaque(wallpaper);
        return OnGlass.withAlpha(GlassInk.haloInk(ink, base),
            GlassInk.haloAlpha(ink, base, false, OnGlass.TARGET_BODY_TEXT));
    }

    // ------------------------------------------------------------------ before a sample

    /**
     * A colour standing for the wallpaper as the system describes it, or {@link Color#TRANSPARENT}
     * when it describes nothing. Its primary colour where that agrees with the system's own
     * "supports dark text" hint, else a neutral on the hint's side: the hint is about the whole
     * picture, the primary colour about its most common hue.
     *
     * @param systemPrimary the wallpaper's primary colour, {@link Color#TRANSPARENT} for none
     * @param supportsDarkText the system's {@code HINT_SUPPORTS_DARK_TEXT}
     */
    @ColorInt
    public static int hintColor(@ColorInt int systemPrimary, boolean supportsDarkText) {
        if (Color.alpha(systemPrimary) == 0) return Color.TRANSPARENT;
        int primary = OnGlass.opaque(systemPrimary);
        boolean light = SchemeTone.mostLegible(primary, Color.BLACK, Color.WHITE) == Color.BLACK;
        if (light == supportsDarkText) return primary;
        return supportsDarkText ? LIGHT_STAND_IN : DARK_STAND_IN;
    }

    /**
     * The wallpaper to assume where none could be read: the system's description
     * ({@link #hintColor}) when there is one, else the mode's nominal glass, which is what the app
     * assumed before.
     */
    @ColorInt
    public static int fallbackWallpaper(@ColorInt int nominalGlass, @ColorInt int hint) {
        return Color.alpha(hint) == 0 ? nominalGlass : OnGlass.opaque(hint);
    }
}
