package com.termux.app.chrome;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * The structural half of legibility on glass: the rims, fills and plates that draw chrome's
 * <em>edges</em> rather than its text.
 *
 * <p>{@link OnGlass} answers what colour a label should be. This answers the question one layer
 * down — what colour the line around the label's container should be — and it is a different
 * question in two ways. A rim is translucent, so what the eye compares is not the rim's own colour
 * against the surface but the <em>composite</em> of the rim over the surface against the bare
 * surface beside it; that is what {@link #separation} measures. And a rim carries no information of
 * its own, so it has no role colour to preserve: it is white light or it is shadow, and which of
 * those it is depends entirely on what it is drawn on.</p>
 *
 * <p>Nearly every structural constant in the launcher was authored as white at a low alpha —
 * {@code 0x3DFFFFFF} for the glass rim, {@code 0x0EFFFFFF} for a drawer card's wash, and so on.
 * Over the dark glass those read as caught light. Over the light-mode glass they read as nothing:
 * the drawer card's 5.5% white wash on the reporting device's veiled band separates by 1.02, and a
 * grid of cards with no cards in it is what "white mode lacks contrast" looks like before a single
 * label is considered. The fix is not to darken the constant — that would break dark mode — it is
 * to let the constant follow {@link ChromeInk.Polarity}, which is the launcher's one global answer
 * to "is chrome standing on something light or something dark".</p>
 *
 * <h3>Which way the constants move</h3>
 * <ul>
 *   <li>{@link ChromeInk.Polarity#PALE_INK} — chrome stands on a dark band. The constants are left
 *       exactly as authored. This is what shipped, it was not what the user reported, and the
 *       alphas are a designer's mock rather than arithmetic; nothing here second-guesses them.</li>
 *   <li>{@link ChromeInk.Polarity#DARK_INK} — chrome stands on a light band. The same constant is
 *       restated as shadow instead of light: black at the smallest alpha, never below the
 *       authored one, that reaches the tier's {@link #separation}. Alpha is capped at
 *       {@link #MAX_STRUCTURAL_ALPHA} so a hairline never becomes a drawn black stroke.</li>
 * </ul>
 *
 * <h3>What it measures against</h3>
 * <p>The mode's nominal glass — the base colour a band veils toward — rather than a per-band
 * sample. A rim is not a band: the same {@code GlassRimRenderer} draws the drawer plane, the dock
 * capsule, every anchored menu and every terminal pane, and most of those are not bands at all, so
 * there is no {@link GlassBackdropCache.Band} to ask. The nominal glass is the right single
 * reference anyway, because it is where a veiled band ends up: on the reporting device a rim
 * resolved against the light nominal glass ({@code #E1E7F2}) and the same rim resolved against the
 * band's actual veiled surface land within a few hundredths of each other. Structure does not need
 * the precision text does.</p>
 *
 * <h3>Why the polarity is a snapshot</h3>
 * <p>{@link ChromeInk} is owned by {@link ChromeRenderer}, which is owned by the activity; the
 * views that draw these constants are three packages away and several of them (a drawer card, a
 * menu row, a pane rim) have no path to it at all. Polarity is global per mode by construction, so
 * it is published here once per chrome sync instead of threaded through every constructor. Until
 * it is published the answer is "unmeasured", and every accessor returns the authored constant
 * untouched — so a chrome that has never been measured draws exactly what it drew before.</p>
 *
 * <p>Pure arithmetic apart from that one snapshot, so all of it unit tests on the JVM.</p>
 */
public final class ChromeShade {

    private ChromeShade() {}

    /**
     * Separation the containing edge of a surface has to reach: the glass rim, a card's stroke, a
     * menu panel's border. Deliberately the same number as {@link OnGlass#TARGET_DECORATION} and
     * for the same reason — a rim carries no information, so WCAG does not govern it, but at 2.0 a
     * 1.25&nbsp;dp line is unambiguously there at arm's length.
     */
    public static final double TARGET_RIM = OnGlass.TARGET_DECORATION;

    /**
     * Separation a fill has to reach: a drawer card's wash, a row's rest state, a folder shell.
     * Lower than {@link #TARGET_RIM} because a fill is a whole region rather than a line, and the
     * eye finds a large field at a fraction of the contrast it needs to find a hairline. Pushed to
     * 2.0 the drawer's cards would read as opaque tiles and the glass under them would be gone,
     * which is not what the grid is.
     */
    public static final double TARGET_FILL = 1.30d;

    /**
     * Separation a press or selection state has to put between itself and the rest state beside
     * it. This one is a difference between two fills rather than a fill against the surface: what
     * says "this row is armed" is that it does not look like its neighbour.
     */
    public static final double TARGET_STATE = 1.25d;

    /**
     * The most alpha this class will <em>add</em> to a structural constant. Past this a rim stops
     * being an edge highlight and becomes a drawn stroke, and a wash stops being glass and becomes
     * a slab. A constant authored above the cap keeps its own strength — the cap bounds what the
     * arithmetic may spend, it does not overrule the mock.
     */
    public static final int MAX_STRUCTURAL_ALPHA = 0xB4;

    // ------------------------------------------------------------------ the snapshot

    @Nullable private static ChromeInk.Polarity sPolarity;
    @ColorInt private static int sNominalGlass = Color.TRANSPARENT;

    /**
     * Publish what the chrome has settled on. Called from {@link ChromeRenderer#requestSync}'s own
     * sync pass, which is the pass a wallpaper, palette or mode change already runs through.
     *
     * @param polarity the chrome's ink polarity, or null to go back to "unmeasured"
     * @param nominalGlass the mode's glass base colour, the surface these constants are measured
     *     against; alpha is ignored
     */
    public static void note(@Nullable ChromeInk.Polarity polarity, @ColorInt int nominalGlass) {
        sPolarity = polarity;
        sNominalGlass = OnGlass.opaque(nominalGlass);
    }

    /** Back to "unmeasured": every accessor answers with the constant as authored. */
    @VisibleForTesting
    public static void clear() {
        sPolarity = null;
        sNominalGlass = Color.TRANSPARENT;
    }

    /** The published polarity, or null when nothing has measured the chrome yet. */
    @Nullable
    public static ChromeInk.Polarity polarity() {
        return sPolarity;
    }

    /** The surface the structural constants are measured against; see the class doc. */
    @ColorInt
    public static int nominalGlass() {
        return sNominalGlass;
    }

    /**
     * The polarity a surface implies on its own — {@link ChromeInk}'s own rule (whichever of black
     * and white reads better) applied to one colour.
     *
     * <p>For the bootstrap only. {@link ChromeInk#polarity()} is the source of truth, but it is a
     * vote of the bands that have actually been measured and it answers
     * {@link ChromeInk.Polarity#DARK_INK} when no band has been. That default is wrong for a dark
     * theme, so the publisher uses this on the mode's own glass until a real vote exists.</p>
     */
    @NonNull
    public static ChromeInk.Polarity polarityOf(@ColorInt int surface) {
        int opaque = OnGlass.opaque(surface);
        return OnGlass.ratio(Color.BLACK, opaque) >= OnGlass.ratio(Color.WHITE, opaque)
            ? ChromeInk.Polarity.DARK_INK
            : ChromeInk.Polarity.PALE_INK;
    }

    // ------------------------------------------------------------------ the arithmetic

    /**
     * How far a translucent overlay lifts off what it is drawn on: the contrast ratio between the
     * composite of {@code overlay} over {@code surface} and the bare {@code surface} beside it.
     *
     * <p>This, not {@code ratio(overlay, surface)}, is what a rim or a wash is judged by. The
     * naive form asks how a 24%-alpha white differs from the surface as if the white were opaque,
     * which is a number about a colour nobody can see.</p>
     */
    public static double separation(@ColorInt int overlay, @ColorInt int surface) {
        int base = OnGlass.opaque(surface);
        return OnGlass.ratio(OnGlass.opaque(OnGlass.composite(overlay, base)), base);
    }

    /** Separation between two overlays drawn on the same surface — a pressed fill against its rest. */
    public static double separation(@ColorInt int first, @ColorInt int second,
                                    @ColorInt int surface) {
        int base = OnGlass.opaque(surface);
        return OnGlass.ratio(OnGlass.opaque(OnGlass.composite(first, base)),
            OnGlass.opaque(OnGlass.composite(second, base)));
    }

    /**
     * One structural constant restated for a polarity, against a surface, at a tier. The pure form;
     * {@link #structural(int, double)} is the same thing reading the published snapshot.
     *
     * <p>Under {@link ChromeInk.Polarity#PALE_INK} the seed is returned untouched — see the class
     * doc. Under {@link ChromeInk.Polarity#DARK_INK} the seed's hue is dropped (a 6%-alpha white
     * has no hue worth keeping) and the answer is black at the least alpha, no lower than the
     * seed's own and no higher than {@link #MAX_STRUCTURAL_ALPHA}, that reaches {@code target}.</p>
     *
     * @param paleSeed the constant as authored: a colour at the alpha the mock chose
     * @param target one of {@link #TARGET_RIM}, {@link #TARGET_FILL}, {@link #TARGET_STATE}
     */
    @ColorInt
    public static int structural(@ColorInt int paleSeed, @NonNull ChromeInk.Polarity polarity,
                                 @ColorInt int surface, double target) {
        if (polarity == ChromeInk.Polarity.PALE_INK) return paleSeed;
        int base = OnGlass.opaque(surface);
        int floor = Color.alpha(paleSeed);
        int ceiling = Math.max(MAX_STRUCTURAL_ALPHA, floor);
        for (int alpha = floor; alpha <= ceiling; alpha++) {
            int candidate = OnGlass.withAlpha(Color.BLACK, alpha);
            if (separation(candidate, base) >= target) return candidate;
        }
        return OnGlass.withAlpha(Color.BLACK, ceiling);
    }

    /**
     * One structural constant restated for the published polarity, or returned as authored when
     * nothing has measured the chrome yet.
     */
    @ColorInt
    public static int structural(@ColorInt int paleSeed, double target) {
        ChromeInk.Polarity polarity = sPolarity;
        if (polarity == null) return paleSeed;
        return structural(paleSeed, polarity, sNominalGlass, target);
    }

    /** {@link #structural(int, double)} at {@link #TARGET_RIM}: a containing edge. */
    @ColorInt
    public static int rim(@ColorInt int paleSeed) {
        return structural(paleSeed, TARGET_RIM);
    }

    /** {@link #structural(int, double)} at {@link #TARGET_FILL}: a card wash or a shell. */
    @ColorInt
    public static int fill(@ColorInt int paleSeed) {
        return structural(paleSeed, TARGET_FILL);
    }

    /**
     * A pressed or selected fill that has to stay clear of {@code restFill} beside it.
     *
     * <p>Resolved as a fill first, then pushed further until it reaches {@link #TARGET_STATE}
     * against the rest state, because the two were authored as a pair (3% and 9% white) and
     * restating them independently can land them on top of each other.</p>
     */
    @ColorInt
    public static int stateFill(@ColorInt int paleSeed, @ColorInt int restFill) {
        ChromeInk.Polarity polarity = sPolarity;
        if (polarity == null) return paleSeed;
        int resolved = structural(paleSeed, polarity, sNominalGlass, TARGET_FILL);
        if (polarity == ChromeInk.Polarity.PALE_INK) return resolved;
        int rest = structural(restFill, polarity, sNominalGlass, TARGET_FILL);
        int ceiling = Math.max(MAX_STRUCTURAL_ALPHA, Color.alpha(resolved));
        for (int alpha = Color.alpha(resolved); alpha <= ceiling; alpha++) {
            int candidate = OnGlass.withAlpha(Color.BLACK, alpha);
            if (separation(candidate, rest, sNominalGlass) >= TARGET_STATE) return candidate;
        }
        return OnGlass.withAlpha(Color.BLACK, ceiling);
    }

    /**
     * A structural stroke that carries a hue of its own — a terminal pane's rim, which doubles as
     * the focus indicator and so cannot be turned into shadow without deleting what it says.
     *
     * <p>The hue survives untouched; only the alpha climbs, and only under
     * {@link ChromeInk.Polarity#DARK_INK}. A Material role is already mode-aware, so in the mode it
     * was authored for it is left exactly as it is; what it is not aware of is the glass, and a
     * hue at 69% alpha over the light band can still sit under {@link #TARGET_RIM}.</p>
     */
    @ColorInt
    public static int tinted(@ColorInt int seed, double target) {
        ChromeInk.Polarity polarity = sPolarity;
        if (polarity == null || polarity == ChromeInk.Polarity.PALE_INK) return seed;
        if (Color.alpha(seed) == 0) return seed;
        int ceiling = Math.max(MAX_STRUCTURAL_ALPHA, Color.alpha(seed));
        for (int alpha = Color.alpha(seed); alpha <= ceiling; alpha++) {
            int candidate = OnGlass.withAlpha(seed, alpha);
            if (separation(candidate, sNominalGlass) >= target) return candidate;
        }
        return OnGlass.withAlpha(seed, ceiling);
    }

    // ------------------------------------------------------------------ plates

    /**
     * Which of two opaque plates the chrome wants: a plate is its own surface, not an overlay, so
     * it does not veil toward anything — it either belongs to a dark chrome or to a light one.
     *
     * <p>The overflow badge on a pinned folder is the case: a near-black disc carrying white text
     * is a perfectly legible object, and on a light dock it is a hole punched through it. Both
     * plates keep their own alpha, so a badge that was 72% black stays 72% of whatever it becomes.
     * </p>
     */
    @ColorInt
    public static int plate(@ColorInt int darkPlate, @ColorInt int lightPlate) {
        ChromeInk.Polarity polarity = sPolarity;
        if (polarity == null || polarity == ChromeInk.Polarity.PALE_INK) return darkPlate;
        return lightPlate;
    }

    /**
     * The opaque surface a plate presents to its own content: the plate composited over whatever it
     * is drawn on. What a panel's washes, strokes and inks are all measured against.
     */
    @ColorInt
    public static int plateSurface(@ColorInt int plateColor, @ColorInt int under) {
        return OnGlass.opaque(OnGlass.composite(plateColor, OnGlass.opaque(under)));
    }

    /**
     * A structural wash or stroke drawn <em>inside</em> a plate — a search field's background, a
     * panel's own rim — measured against the plate rather than against the chrome's glass.
     *
     * <p>A plate is its own surface, so it carries its own polarity: a dark sheet keeps its white
     * washes even when the chrome around it has gone dark-inked, and a light sheet takes shadow
     * ones even when the chrome around it has not. That is the same exemption {@link #onPlate}
     * relies on and it is not the per-band flip {@link ChromeInk} forbids — nothing here decides
     * what the chrome does, only what a card does inside itself.</p>
     */
    @ColorInt
    public static int inPlate(@ColorInt int paleSeed, @ColorInt int plateSurface, double target) {
        return structural(paleSeed, polarityOf(plateSurface), plateSurface, target);
    }

    /**
     * The ink for content standing on a plate {@link #plate} picked — read off the plate itself
     * rather than off the chrome's polarity, because a plate <em>is</em> the surface its content is
     * on. This is not the per-band flip {@link ChromeInk} forbids; it is a card being checked
     * against the card.
     *
     * @param plateColor the plate as it will be drawn, alpha included
     * @param under what the plate is drawn over, so a translucent plate is judged as composited
     */
    @ColorInt
    public static int onPlate(@ColorInt int plateColor, @ColorInt int under, double target) {
        int surface = OnGlass.opaque(OnGlass.composite(plateColor, OnGlass.opaque(under)));
        int ink = OnGlass.mostLegibleInk(surface, Color.WHITE, Color.BLACK);
        return OnGlass.inkOn(surface, ink, target);
    }
}
