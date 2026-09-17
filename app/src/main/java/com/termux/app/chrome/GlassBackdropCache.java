package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

/**
 * What each chrome band is actually drawn over, sampled once and kept until something moves.
 *
 * <p>{@link OnGlass} does the colour arithmetic but needs an input nothing in the tree produced
 * before: the real colour behind a band. This holds that. A caller hands it a {@link Sampler} that
 * can read the wallpaper — in practice a crop of the same pre-blurred frame
 * {@link WallpaperFrostPainter} already cuts for the glass bands, so the pixels are paid for once
 * for both jobs — plus the band's rect, the launcher's wallpaper dim and the band's own glass tint,
 * and gets back a composited backdrop or a whole {@link OnGlass.Resolution}.</p>
 *
 * <h3>When it recomputes</h3>
 * <p>Never per frame; chrome bands repaint constantly and a sample plus a veil search per draw
 * would be visible in a scroll. A band's sample is taken once and reused until one of these
 * happens:</p>
 * <ul>
 *   <li>{@link #invalidate()} — the wallpaper changed, the palette changed, or the app moved
 *       between light and dark. The same events that clear {@link WallpaperBlurCache}.</li>
 *   <li>the band's rect changed — a rotation, an inset change, a bar that grew. Detected here by
 *       comparing the rect passed in, so callers need no extra bookkeeping.</li>
 *   <li>the dim, the tint, the ink, the veil colour or the target passed for that band changed —
 *       the memoised {@link OnGlass.Resolution} is keyed on all of them.</li>
 * </ul>
 *
 * <h3>Before the first sample</h3>
 * <p>A band asking before any wallpaper is readable — first layout, passthrough mode with a live
 * wallpaper, a blur frame still being decoded on the worker — must not flash an unstyled colour and
 * must not crash. It gets {@link #setFallbackWallpaper the fallback wallpaper colour} instead,
 * which the caller sets to the mode's nominal glass: the answer is then exactly what the app did
 * before this round, and it is replaced by the measured answer as soon as a sample lands. Callers
 * can read {@link #hasSample} if they want to know which they got.</p>
 *
 * <p>Main thread only, like the rest of the chrome renderer; it holds no locks and does no I/O.</p>
 */
public final class GlassBackdropCache {

    /**
     * The chrome bands that ask what they are drawn on. An enum rather than free-form keys so three
     * phases wiring different views cannot disagree about a band's name; adding one is a line here.
     */
    public enum Band {
        /** The status strip: clock, CPU/RAM widgets, weather, separator dots. */
        STATUS_BAR,
        /** The window bar's pane: window chips and their titles. */
        WINDOW_BAR,
        /** The sessions indicator chip, which can sit on either bar. */
        SESSION_CHIP,
        /** The A&ndash;Z scrub rail down the edge of the wall. */
        AZ_STRIP,
        /** The dock plank and anything riding on it. */
        DOCK,
    }

    /** Reads the pixels behind a band. Implemented by the chrome renderer, faked in tests. */
    public interface Sampler {
        /**
         * The average opaque colour of the wallpaper under {@code screenRect}, or
         * {@link #UNREADABLE} when nothing can be read right now — no wallpaper frame, a live
         * wallpaper the app cannot capture, a blur still decoding. Never throws.
         */
        @ColorInt int sampleWallpaper(@NonNull Rect screenRect);
    }

    /** What a {@link Sampler} returns when it has nothing: fully transparent, so it cannot be a colour. */
    public static final int UNREADABLE = Color.TRANSPARENT;

    /**
     * Pixels read per sample, at most. A band is a wash of blurred wallpaper, not detail: a 16x16
     * grid over the rect is within a couple of RGB units of the true mean on every wallpaper tried,
     * costs 256 {@code getPixel} calls, and happens on a wallpaper change rather than per frame.
     */
    public static final int SAMPLE_GRID = 16;

    /**
     * How many distinct resolutions one band remembers at once. A band asks for more than one: the
     * status strip alone wants body contrast for its labels, large-text contrast for its glyphs and
     * the decoration tier for its separator dots, each with its own ink. Six slots hold a band's
     * whole vocabulary; a seventh evicts the oldest, which costs one veil search, not correctness.
     */
    private static final int MEMO_SLOTS = 6;

    /** One memoised {@link OnGlass.Resolution} and every input it was computed from. */
    private static final class Memo {
        @NonNull final OnGlass.Resolution resolution;
        final int dim, tint, ink, alternateInk, veilColor;
        final double target;

        Memo(@NonNull OnGlass.Resolution resolution, int dim, int tint, int ink, int alternateInk,
             int veilColor, double target) {
            this.resolution = resolution;
            this.dim = dim;
            this.tint = tint;
            this.ink = ink;
            this.alternateInk = alternateInk;
            this.veilColor = veilColor;
            this.target = target;
        }

        boolean matches(int dim, int tint, int ink, int alternateInk, int veilColor, double target) {
            return this.dim == dim && this.tint == tint && this.ink == ink
                && this.alternateInk == alternateInk && this.veilColor == veilColor
                && Double.compare(this.target, target) == 0;
        }
    }

    private static final class Entry {
        final Rect rect = new Rect();
        @ColorInt int wallpaper = UNREADABLE;
        boolean sampled;

        /** Newest first, at most {@link #MEMO_SLOTS}; cleared whenever the sample moves. */
        final ArrayList<Memo> memos = new ArrayList<>(MEMO_SLOTS);
    }

    @NonNull private final Map<Band, Entry> mEntries = new EnumMap<>(Band.class);
    @Nullable private Sampler mSampler;
    @ColorInt private int mFallbackWallpaper = Color.TRANSPARENT;
    private int mGeneration;

    /** The sampler to read pixels through; null parks the cache on its fallback. */
    public void setSampler(@Nullable Sampler sampler) {
        if (mSampler == sampler) return;
        mSampler = sampler;
        invalidate();
    }

    /**
     * The wallpaper colour to assume until a real sample lands: the mode's nominal glass, so a band
     * that asks too early is answered with the colour the app would have used anyway.
     */
    public void setFallbackWallpaper(@ColorInt int nominalGlass) {
        if (mFallbackWallpaper == nominalGlass) return;
        mFallbackWallpaper = nominalGlass;
        invalidate();
    }

    /** The fallback set by {@link #setFallbackWallpaper}. */
    @ColorInt
    public int fallbackWallpaper() {
        return mFallbackWallpaper;
    }

    /**
     * Drops every sample and every memoised resolution: the wallpaper, the palette or the mode
     * changed. Cheap — the next band to draw re-samples itself.
     */
    public void invalidate() {
        mEntries.clear();
        mGeneration++;
    }

    /** Bumped by every {@link #invalidate}; for callers that shadow a derived value of their own. */
    public int generation() {
        return mGeneration;
    }

    /** Whether {@code band} has a real measurement, as opposed to the fallback. */
    public boolean hasSample(@NonNull Band band) {
        Entry entry = mEntries.get(band);
        return entry != null && entry.sampled;
    }

    /**
     * The raw wallpaper colour under {@code band}, sampled once per rect and generation. Returns
     * {@link #fallbackWallpaper()} when nothing has been read yet.
     */
    @ColorInt
    public int wallpaperUnder(@NonNull Band band, @NonNull Rect screenRect) {
        return entryFor(band, screenRect).wallpaper;
    }

    /**
     * What {@code band} is effectively drawn on: its wallpaper sample under the launcher's dim and
     * the band's own glass tint. Always opaque.
     *
     * @see OnGlass#backdrop
     */
    @ColorInt
    public int backdropUnder(@NonNull Band band, @NonNull Rect screenRect,
                             @ColorInt int dim, @ColorInt int glassTint) {
        return OnGlass.backdrop(wallpaperUnder(band, screenRect), dim, glassTint);
    }

    /**
     * The whole answer for {@code band}: the veil it needs, the surface that leaves and the ink to
     * draw with. Memoised per band against every input, so the repeated calls a repaint makes cost
     * a map lookup and a handful of int comparisons.
     *
     * @param screenRect the band's rect on screen; a change re-samples
     * @param dim the launcher's wallpaper dim (black at the user's slider alpha)
     * @param glassTint the band's own glass tint
     * @param preferredInk the mode's role colour for the band's content
     * @param alternateInk the other mode's colour for it, allowed to win over a backdrop that
     *     belongs to the other mode; pass {@code preferredInk} to forbid the flip
     * @param veilColor the mode's surface colour, what a veil moves toward
     * @param target one of {@link OnGlass#TARGET_BODY_TEXT}, {@link OnGlass#TARGET_LARGE_TEXT},
     *     {@link OnGlass#TARGET_DECORATION}
     */
    @NonNull
    public OnGlass.Resolution resolve(@NonNull Band band, @NonNull Rect screenRect,
                                      @ColorInt int dim, @ColorInt int glassTint,
                                      @ColorInt int preferredInk, @ColorInt int alternateInk,
                                      @ColorInt int veilColor, double target) {
        Entry entry = entryFor(band, screenRect);
        for (int i = 0; i < entry.memos.size(); i++) {
            Memo memo = entry.memos.get(i);
            if (memo.matches(dim, glassTint, preferredInk, alternateInk, veilColor, target)) {
                return memo.resolution;
            }
        }
        int backdrop = OnGlass.backdrop(entry.wallpaper, dim, glassTint);
        OnGlass.Resolution resolution =
            OnGlass.resolve(backdrop, preferredInk, alternateInk, veilColor, target);
        if (entry.memos.size() >= MEMO_SLOTS) entry.memos.remove(entry.memos.size() - 1);
        entry.memos.add(0, new Memo(resolution, dim, glassTint, preferredInk, alternateInk,
            veilColor, target));
        return resolution;
    }

    /**
     * The whole answer for {@code band} when the caller has already composed what the band stands
     * on — the wallpaper, the dim and every layer of the band's own glass, in the order they are
     * drawn.
     *
     * <p>{@link #resolve} builds the backdrop itself out of a dim and a single glass tint, which is
     * the same arithmetic but not the same 8-bit rounding as a stack of layers composited one at a
     * time. A caller that draws its glass as several layers composes it once, its own way, and
     * hands the result here, so what was measured and what is drawn cannot drift apart by the unit
     * of RGB that costs a promise. Memoised on the backdrop itself.</p>
     *
     * @param screenRect the band's rect on screen; a change re-samples the wallpaper
     * @param backdrop the opaque colour the band's content stands on before any veil
     */
    @NonNull
    public OnGlass.Resolution resolveOn(@NonNull Band band, @NonNull Rect screenRect,
                                        @ColorInt int backdrop, @ColorInt int preferredInk,
                                        @ColorInt int alternateInk, @ColorInt int veilColor,
                                        double target) {
        Entry entry = entryFor(band, screenRect);
        for (int i = 0; i < entry.memos.size(); i++) {
            Memo memo = entry.memos.get(i);
            if (memo.matches(backdrop, BACKDROP_GIVEN, preferredInk, alternateInk, veilColor, target)) {
                return memo.resolution;
            }
        }
        OnGlass.Resolution resolution =
            OnGlass.resolve(backdrop, preferredInk, alternateInk, veilColor, target);
        if (entry.memos.size() >= MEMO_SLOTS) entry.memos.remove(entry.memos.size() - 1);
        entry.memos.add(0, new Memo(resolution, backdrop, BACKDROP_GIVEN, preferredInk,
            alternateInk, veilColor, target));
        return resolution;
    }

    /**
     * The tint slot's value in a {@link #resolveOn} memo. A sentinel rather than a flag: the slot
     * holds a glass tint for {@link #resolve} and the composed backdrop for {@link #resolveOn}, and
     * the two must never match each other by accident.
     */
    private static final int BACKDROP_GIVEN = 0x00BACD09;

    /**
     * {@link #resolve(Band, Rect, int, int, int, int, int, double)} with no alternate ink: the band
     * keeps its mode's colour or a tone of it and never flips.
     */
    @NonNull
    public OnGlass.Resolution resolve(@NonNull Band band, @NonNull Rect screenRect,
                                      @ColorInt int dim, @ColorInt int glassTint,
                                      @ColorInt int preferredInk, @ColorInt int veilColor,
                                      double target) {
        return resolve(band, screenRect, dim, glassTint, preferredInk, preferredInk, veilColor,
            target);
    }

    /**
     * The band's entry, sampling if its rect moved — or if it has never had a real sample, so a
     * band that drew before the wallpaper was readable heals itself on a later frame instead of
     * waiting for an {@link #invalidate()} nobody will send. Once a sample lands it is kept until
     * the rect moves or the cache is invalidated.
     */
    @NonNull
    private Entry entryFor(@NonNull Band band, @NonNull Rect screenRect) {
        Entry entry = mEntries.get(band);
        if (entry == null) {
            entry = new Entry();
            entry.wallpaper = mFallbackWallpaper;
            mEntries.put(band, entry);
        } else if (entry.sampled && entry.rect.equals(screenRect)) {
            return entry;
        }
        entry.rect.set(screenRect);
        int read = UNREADABLE;
        if (mSampler != null && !screenRect.isEmpty()) {
            read = mSampler.sampleWallpaper(screenRect);
        }
        boolean sampled = Color.alpha(read) != 0;
        int wallpaper = sampled ? OnGlass.opaque(read) : mFallbackWallpaper;
        if (wallpaper != entry.wallpaper || sampled != entry.sampled) {
            entry.memos.clear();
        }
        entry.sampled = sampled;
        entry.wallpaper = wallpaper;
        return entry;
    }

    // ------------------------------------------------------------- sampling helpers

    /**
     * The mean of a run of ARGB pixels, ignoring fully transparent ones. Returns
     * {@link #UNREADABLE} when nothing was readable, so it composes with {@link Sampler}'s
     * contract.
     *
     * <p>Averaged in plain sRGB rather than linear light on purpose: the number this feeds is a
     * WCAG luminance, and a blurred band is already a near-uniform wash, so the two agree to within
     * a unit or so while this costs no {@code pow} per pixel.</p>
     */
    @ColorInt
    public static int averageColor(@NonNull int[] argbPixels, int offset, int count) {
        long red = 0, green = 0, blue = 0;
        int seen = 0;
        int end = Math.min(argbPixels.length, offset + count);
        for (int i = Math.max(0, offset); i < end; i++) {
            int pixel = argbPixels[i];
            if (Color.alpha(pixel) == 0) continue;
            red += Color.red(pixel);
            green += Color.green(pixel);
            blue += Color.blue(pixel);
            seen++;
        }
        if (seen == 0) return UNREADABLE;
        return Color.rgb((int) (red / seen), (int) (green / seen), (int) (blue / seen));
    }

    /**
     * The mean colour of {@code region} of {@code bitmap}, read on a grid of at most
     * {@link #SAMPLE_GRID}&sup2; pixels. {@code region} is in the bitmap's own coordinates and is
     * clamped to it; an empty intersection, or a recycled bitmap, gives {@link #UNREADABLE}.
     *
     * <p>This is the body of a {@link Sampler} over a frost crop or over the shared pre-blurred
     * wallpaper frame. It is here, rather than in the phase that wires it, so the grid and the
     * clamping are tested once.</p>
     */
    @ColorInt
    public static int sampleBitmapRegion(@Nullable Bitmap bitmap, @NonNull Rect region) {
        if (bitmap == null || bitmap.isRecycled()) return UNREADABLE;
        int left = Math.max(0, region.left);
        int top = Math.max(0, region.top);
        int right = Math.min(bitmap.getWidth(), region.right);
        int bottom = Math.min(bitmap.getHeight(), region.bottom);
        if (right <= left || bottom <= top) return UNREADABLE;
        int columns = Math.min(SAMPLE_GRID, right - left);
        int rows = Math.min(SAMPLE_GRID, bottom - top);
        int[] pixels = new int[columns * rows];
        int index = 0;
        for (int row = 0; row < rows; row++) {
            int y = top + (int) ((row + 0.5f) * (bottom - top) / rows);
            y = Math.min(bottom - 1, Math.max(top, y));
            for (int column = 0; column < columns; column++) {
                int x = left + (int) ((column + 0.5f) * (right - left) / columns);
                x = Math.min(right - 1, Math.max(left, x));
                pixels[index++] = bitmap.getPixel(x, y);
            }
        }
        return averageColor(pixels, 0, pixels.length);
    }
}
