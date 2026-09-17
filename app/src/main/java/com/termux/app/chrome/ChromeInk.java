package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.DockGlassRendering;

import java.util.EnumMap;
import java.util.Map;

/**
 * The one way to ask what colour a piece of chrome should be drawn in.
 *
 * <p>{@link OnGlass} is the arithmetic and {@link GlassBackdropCache} is the memory; this is the
 * owner that holds both, feeds them the wallpaper and the launcher's own glass, and answers the
 * question every chrome view actually has: <em>given where I am on screen, what ink do I use and
 * what does the band under me have to become?</em> One instance, owned by {@link ChromeRenderer};
 * everything else calls {@link #onGlass}.</p>
 *
 * <h3>The coherence rule</h3>
 * <p>The arithmetic alone resolves every band independently, and on a wallpaper that happens to sit
 * near the crossover that produces opposite answers on adjacent chrome — a veiled status bar in
 * dark ink a few hundred pixels above a bare A&ndash;Z strip in pale ink. Nobody asked for that. So
 * the launcher's chrome shares <em>one ink polarity per mode</em>, decided here once from what the
 * bands are collectively drawn on, and each band then spends only as much veil as it needs to make
 * that one polarity read. Callers cannot opt out: {@link #onGlass} takes the two candidate inks and
 * picks between them itself, and never passes an alternate to {@link OnGlass#resolve}, so no band
 * can flip on its own.</p>
 *
 * <p>The polarity is deliberately not a per-band coin flip. It is decided from the mean of every
 * band's flat glass (wallpaper, the launcher's dim, the band's own base layer — no light model, so
 * the vote does not depend on which ink is asking), and it only changes when the losing ink beats
 * the winner by {@link #POLARITY_FLIP_MARGIN}. The A&ndash;Z strip on the reporting device resolves
 * within a hundredth of its 3.0 target, which is a coin flip between two samples of the same
 * wallpaper; the margin is roughly &plusmn;0.016 of absolute luminance around the crossover, far
 * more than a blurred wash moves between samples and far less than one wallpaper differs from
 * another. The decision also survives {@link #invalidate()}, so re-sampling the same wallpaper
 * cannot oscillate.</p>
 *
 * <h3>Where a veil is drawn</h3>
 * <p>A resolved veil is the band's own base colour at the smallest alpha that works, and it is
 * drawn by {@link GlassSurfaceFactory} as a pane <em>over</em> the light model — which is where
 * {@link OnGlass} measured it. Folding it into the base layer instead would have put it under the
 * model, so the black foot would land on top of the thing meant to guarantee the ratio, and it
 * would also have quietly overridden the user's own opacity slider. Drawn where it was measured,
 * the foot is inside the promise: {@link DockGlassRendering#worstLightModelStop} finds the row of
 * the band where the model works hardest against the ink, and that row is what the veil search was
 * run against.</p>
 *
 * <p>Main thread only, like the rest of the chrome renderer.</p>
 */
public final class ChromeInk {

    /**
     * Which of the two inks the launcher's chrome is using right now. Not a mode: the light theme
     * over a dark wallpaper is {@link #PALE_INK}, which is the whole point — the user keeps their
     * wallpaper instead of getting a light slab laid over it.
     */
    public enum Polarity {
        /** The on-light ink: the mode's dark role colour, for chrome standing on a light band. */
        DARK_INK,
        /** The on-dark ink: the mode's pale role colour, for chrome standing on a dark band. */
        PALE_INK,
    }

    /**
     * How much better the losing ink has to read before the whole chrome changes polarity.
     *
     * <p>1.15 puts the dead band at roughly 0.164&ndash;0.195 relative luminance around the
     * black/white crossover at 0.179. A blurred band re-sampled from the same wallpaper moves by a
     * unit or two of RGB, nowhere near that; two different wallpapers differ by far more.</p>
     */
    public static final double POLARITY_FLIP_MARGIN = 1.15d;

    /** What a band's glass is, as far as measuring it goes: opacity, foot, and the slice it draws. */
    private static final class BandGlass {
        float opacity;
        boolean withFoot = true;
        float sliceStart;
        float sliceEnd = 1f;
    }

    @NonNull private final ChromeRenderer.Surfaces mSurfaces;
    @NonNull private final WallpaperBlurCache mBlurCache;
    @NonNull private final GlassBackdropCache mCache = new GlassBackdropCache();
    /** Run when a band's veil demand changes, so the surface that draws it is rebuilt. */
    @Nullable private final Runnable mOnVeilChanged;

    @NonNull private final Map<GlassBackdropCache.Band, BandGlass> mGlass =
        new EnumMap<>(GlassBackdropCache.Band.class);
    /** The veil each band was last told it needs; what {@link GlassSurfaceFactory} draws. */
    @NonNull private final Map<GlassBackdropCache.Band, Integer> mVeils =
        new EnumMap<>(GlassBackdropCache.Band.class);
    /** Each band's flat glass, its vote in the polarity decision. Cleared by {@link #invalidate}. */
    @NonNull private final Map<GlassBackdropCache.Band, Integer> mVotes =
        new EnumMap<>(GlassBackdropCache.Band.class);

    @Nullable private Polarity mPolarity;
    @ColorInt private int mBaseColor;
    @ColorInt private int mAccentColor;
    @ColorInt private int mDimColor;
    private boolean mModeRead;

    @NonNull private final Rect mTmpSampleRect = new Rect();
    @NonNull private final int[] mTmpLocation = new int[2];

    ChromeInk(@NonNull ChromeRenderer.Surfaces surfaces, @NonNull WallpaperBlurCache blurCache,
              @Nullable Runnable onVeilChanged) {
        mSurfaces = surfaces;
        mBlurCache = blurCache;
        mOnVeilChanged = onVeilChanged;
        mCache.setSampler(this::sampleWallpaper);
    }

    // ------------------------------------------------------------------ the accessor

    /**
     * What to draw {@code band}'s content in, and what the band has to become for it to read.
     *
     * <p>This is the one entry point. Hand it the band, where that band is on screen, the two
     * candidate inks and the contrast tier the content belongs to, and it returns the whole answer:
     * {@link OnGlass.Resolution#ink} is the colour to draw with, {@link OnGlass.Resolution#surface}
     * is the opaque colour that content is effectively standing on, and
     * {@link OnGlass.Resolution#veil} is the veil this band will be drawn with — already recorded
     * here, so no caller has to draw it or even look at it.</p>
     *
     * <p>A band carries several contrast tiers at once — the status strip has body labels, glyphs
     * and 6&nbsp;px separator dots — and a band can only have one veil. So resolve the band
     * <em>once</em>, at its strictest tier (normally {@link OnGlass#TARGET_BODY_TEXT}), keep the
     * {@link OnGlass.Resolution}, and get every other ink on the same band from
     * {@link #inkOn(OnGlass.Resolution, int, int, double)} at its own looser tier. Calling this a
     * second time for the same band at a looser target would ask for a second veil and the last
     * caller in the frame would win.</p>
     *
     * @param band which chrome band the content sits on
     * @param screenRect that band's rect in screen coordinates; a change re-samples the wallpaper
     * @param darkInk the on-light ink for this content — the mode's dark role colour
     * @param paleInk the on-dark ink for it — the other role colour, for a band standing on a dark
     *     backdrop. Pass the same colour twice for content whose hue carries meaning of its own.
     * @param target one of {@link OnGlass#TARGET_BODY_TEXT}, {@link OnGlass#TARGET_LARGE_TEXT},
     *     {@link OnGlass#TARGET_DECORATION}
     */
    @NonNull
    public OnGlass.Resolution onGlass(@NonNull GlassBackdropCache.Band band,
                                      @NonNull Rect screenRect,
                                      @ColorInt int darkInk, @ColorInt int paleInk,
                                      double target) {
        readMode();
        BandGlass glass = glassFor(band);
        int baseAlpha = ChromePolicy.dockGlassBaseAlpha(glass.opacity);
        // The band's flat glass: wallpaper, the launcher's dim, the base layer. No light model, so
        // the polarity vote is the same number whichever ink is asking.
        int flat = OnGlass.backdrop(mCache.wallpaperUnder(band, screenRect), mDimColor,
            OnGlass.withAlpha(mBaseColor, baseAlpha));
        vote(band, flat);
        int ink = polarity() == Polarity.DARK_INK ? darkInk : paleInk;

        int top = DockGlassRendering.topSheenAlpha(glass.opacity);
        int mid = DockGlassRendering.midSheenAlpha(glass.opacity);
        int foot = DockGlassRendering.footAlpha(glass.opacity, glass.withFoot);
        float worst = DockGlassRendering.worstLightModelStop(ink, flat, mBaseColor, baseAlpha,
            mAccentColor, top, mid, foot, glass.sliceStart, glass.sliceEnd);
        int tint = DockGlassRendering.glassTintAt(worst, mBaseColor, baseAlpha, mAccentColor,
            top, mid, foot);

        OnGlass.Resolution resolved;
        if (veilWouldHelp(band, screenRect, tint, ink)) {
            resolved = mCache.resolve(band, screenRect, mDimColor, tint, ink, ink, mBaseColor,
                target);
        } else {
            // Veiling toward this band's own base colour would move the surface the wrong way for
            // the ink the chrome has settled on. Leave the wallpaper alone and move the ink.
            resolved = OnGlass.resolveBare(mCache.backdropUnder(band, screenRect, mDimColor, tint),
                ink, target);
        }
        recordVeil(band, resolved.veil);
        return resolved;
    }

    /**
     * A second ink on a band already resolved by {@link #onGlass}: legible at {@code target} on the
     * surface that band ended up with, without asking for any more veil, and in the chrome's own
     * polarity so it cannot read as the opposite of the label beside it.
     */
    @ColorInt
    public int inkOn(@NonNull OnGlass.Resolution resolved, @ColorInt int darkInk,
                     @ColorInt int paleInk, double target) {
        int ink = polarity() == Polarity.DARK_INK ? darkInk : paleInk;
        return OnGlass.inkOnBand(resolved, ink, ink, target);
    }

    /** The polarity the whole chrome is drawing in; for a caller that tints an icon to match. */
    @NonNull
    public Polarity polarity() {
        readMode();
        if (mVotes.isEmpty()) {
            return mPolarity != null ? mPolarity : Polarity.DARK_INK;
        }
        int mean = meanVote();
        double dark = OnGlass.ratio(Color.BLACK, mean);
        double pale = OnGlass.ratio(Color.WHITE, mean);
        if (mPolarity == null) {
            mPolarity = dark >= pale ? Polarity.DARK_INK : Polarity.PALE_INK;
        } else if (mPolarity == Polarity.DARK_INK && pale > dark * POLARITY_FLIP_MARGIN) {
            mPolarity = Polarity.PALE_INK;
        } else if (mPolarity == Polarity.PALE_INK && dark > pale * POLARITY_FLIP_MARGIN) {
            mPolarity = Polarity.DARK_INK;
        }
        return mPolarity;
    }

    // ------------------------------------------------------------------ for the surface builder

    /**
     * What {@code band}'s glass is, told by the surface that draws it so a measurement can include
     * it. Called from {@link GlassSurfaceFactory}; nothing else needs it.
     */
    void noteBandGlass(@NonNull GlassBackdropCache.Band band, float opacity, boolean withFoot,
                       float sliceStart, float sliceEnd) {
        BandGlass glass = glassFor(band);
        glass.opacity = opacity < 0f ? 0f : (opacity > 1f ? 1f : opacity);
        glass.withFoot = withFoot;
        glass.sliceStart = sliceStart;
        glass.sliceEnd = sliceEnd;
    }

    /**
     * The veil {@code band} was last told it needs: its own base colour at an alpha, or
     * {@link Color#TRANSPARENT} when the band reads bare. Drawn over the light model.
     */
    @ColorInt
    int bandVeil(@NonNull GlassBackdropCache.Band band) {
        Integer veil = mVeils.get(band);
        return veil == null ? Color.TRANSPARENT : veil;
    }

    /**
     * Fills {@code out} with {@code band}'s own rect on screen, and says whether it could.
     *
     * <p>The companion to {@link #onGlass}, and the reason it is here rather than left to each
     * caller: a band is sampled and memoised <em>per rect</em>, so two views on the same band that
     * each pass a rect of their own — a widget passing its own bounds, the strip passing the
     * strip's — would take turns invalidating each other's sample and re-measure on every draw. The
     * content of a band asks the band where it is.</p>
     *
     * <p>False for a band whose view is not inflated or not laid out yet, and for a band the chrome
     * does not own a view for; that caller supplies its own rect and uses it consistently.</p>
     */
    public boolean bandRect(@NonNull GlassBackdropCache.Band band, @NonNull Rect out) {
        int viewId = bandViewId(band);
        if (viewId == 0) return false;
        View view = mSurfaces.findChromeView(viewId);
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        view.getLocationOnScreen(mTmpLocation);
        out.set(mTmpLocation[0], mTmpLocation[1],
            mTmpLocation[0] + view.getWidth(), mTmpLocation[1] + view.getHeight());
        return true;
    }

    /** The view each band's glass is drawn by; 0 for a band whose owning phase holds its own. */
    private static int bandViewId(@NonNull GlassBackdropCache.Band band) {
        switch (band) {
            case STATUS_BAR: return R.id.terminal_status_bar_background;
            case WINDOW_BAR: return R.id.terminal_window_bar_background;
            default: return 0;
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Drops every sample and every resolution: the wallpaper, the palette or the mode moved. Wired
     * to the same events that clear {@link WallpaperBlurCache}. The polarity deliberately survives
     * — re-sampling the same wallpaper must not be able to flip the chrome.
     */
    public void invalidate() {
        mCache.invalidate();
        mVotes.clear();
        mVeils.clear();
    }

    /** The backing cache, for tests and for a caller that wants the raw backdrop. */
    @NonNull
    @VisibleForTesting
    public GlassBackdropCache backdrops() {
        return mCache;
    }

    // ------------------------------------------------------------------ internals

    /**
     * Re-reads the theme values the whole answer hangs on and invalidates if any moved. Done here,
     * on every call, rather than left to callers: four phases wire views into this and a palette or
     * light/dark change that nobody remembered to report would show as stale ink for a whole
     * session. Three int comparisons is the price.
     */
    private void readMode() {
        int base = mSurfaces.glassBaseColor();
        int accent = mSurfaces.accentColor();
        int dim = mSurfaces.wallpaperDimColor();
        if (mModeRead && base == mBaseColor && accent == mAccentColor && dim == mDimColor) return;
        mBaseColor = base;
        mAccentColor = accent;
        mDimColor = dim;
        mModeRead = true;
        // Before anything is measured a band is answered with the mode's nominal glass, which is
        // exactly what the app drew before this round: no flash, and no crash.
        mCache.setFallbackWallpaper(base);
        invalidate();
    }

    /**
     * Whether veiling this band in its own base colour moves it toward the chosen ink at all. An
     * opaque veil is the furthest the search could ever go, so if that does not beat the bare
     * surface no alpha below it will either.
     */
    private boolean veilWouldHelp(@NonNull GlassBackdropCache.Band band, @NonNull Rect screenRect,
                                  @ColorInt int tint, @ColorInt int ink) {
        int backdrop = mCache.backdropUnder(band, screenRect, mDimColor, tint);
        return OnGlass.ratio(ink, OnGlass.opaque(mBaseColor)) > OnGlass.ratio(ink, backdrop);
    }

    private void recordVeil(@NonNull GlassBackdropCache.Band band, @ColorInt int veil) {
        Integer previous = mVeils.put(band, veil);
        if (mOnVeilChanged != null && (previous == null ? veil != Color.TRANSPARENT : previous != veil)) {
            mOnVeilChanged.run();
        }
    }

    private void vote(@NonNull GlassBackdropCache.Band band, @ColorInt int flatGlass) {
        Integer previous = mVotes.put(band, flatGlass);
        if (previous != null && previous == flatGlass) return;
        // A new or moved vote can move the mean past the hysteresis band. Nothing is recomputed
        // here — polarity() reads the votes — but the chrome drawn before the change has to be
        // redrawn, and that is the same pass a veil change asks for.
        if (mOnVeilChanged != null && previous == null && mVotes.size() > 1) mOnVeilChanged.run();
    }

    @ColorInt
    private int meanVote() {
        long red = 0, green = 0, blue = 0;
        for (Integer vote : mVotes.values()) {
            red += Color.red(vote);
            green += Color.green(vote);
            blue += Color.blue(vote);
        }
        int count = Math.max(1, mVotes.size());
        return Color.rgb((int) (red / count), (int) (green / count), (int) (blue / count));
    }

    @NonNull
    private BandGlass glassFor(@NonNull GlassBackdropCache.Band band) {
        BandGlass glass = mGlass.get(band);
        if (glass == null) {
            glass = new BandGlass();
            mGlass.put(band, glass);
        }
        return glass;
    }

    // ------------------------------------------------------------------ the sampler

    /**
     * Reads the wallpaper under a band off the shared pre-blurred frame
     * {@link WallpaperFrostPainter} already cuts the glass bands from, so the pixels are paid for
     * once for both jobs and nothing here ever starts a decode: a radius that is not already
     * resident answers {@link GlassBackdropCache#UNREADABLE} and the band heals itself on a later
     * frame, which is exactly what the cache's re-ask is for.
     *
     * <p>Two hazards the frame carries, both handled rather than hoped about:</p>
     * <ul>
     *   <li><b>{@link Bitmap.Config#HARDWARE}.</b> {@code getPixel} throws on one. A frame straight
     *       off a wallpaper decode can be hardware-backed (only the radius-0 path can return the
     *       captured bitmap untouched; every blurred frame is {@code ARGB_8888} out of
     *       {@code createScaledBitmap}). Copying a full-screen frame to software would cost ~10&nbsp;MB
     *       and a main-thread copy, so instead the band's region alone is drawn into a
     *       {@link GlassBackdropCache#SAMPLE_GRID}&sup2; software bitmap — 1&nbsp;KB — and averaged
     *       from that. The scale-down does the averaging in the process. Everything is wrapped so a
     *       config this misses still answers {@code UNREADABLE} instead of throwing.</li>
     *   <li><b>The frost filter.</b> The crop is drawn through
     *       {@link GlassFilters#frost()} — saturation 1.30, contrast 1.06, &minus;6 offset — so a
     *       sample taken from the bitmap is taken before it. Modelled here rather than ignored: it
     *       is one colour, not a bitmap, so the same matrix costs nine multiplies. Ignoring it would
     *       have biased every measurement, and in the direction that matters: on the reporting
     *       device's own mid-dark glass the filter darkens by a few RGB units, so an unmodelled
     *       sample would report the band as lighter than the user sees it.</li>
     * </ul>
     */
    @ColorInt
    private int sampleWallpaper(@NonNull Rect screenRect) {
        try {
            Bitmap frame = residentFrame();
            if (frame == null || frame.isRecycled()) return GlassBackdropCache.UNREADABLE;
            Rect frameRect = mBlurCache.frameRectRef();
            if (frameRect.isEmpty()) return GlassBackdropCache.UNREADABLE;
            float scaleX = frame.getWidth() / (float) frameRect.width();
            float scaleY = frame.getHeight() / (float) frameRect.height();
            mTmpSampleRect.set(
                Math.round((screenRect.left - frameRect.left) * scaleX),
                Math.round((screenRect.top - frameRect.top) * scaleY),
                Math.round((screenRect.right - frameRect.left) * scaleX),
                Math.round((screenRect.bottom - frameRect.top) * scaleY));
            int raw = readableInSoftware(frame.getConfig())
                ? GlassBackdropCache.sampleBitmapRegion(frame, mTmpSampleRect)
                : sampleThroughSoftwareCopy(frame, mTmpSampleRect);
            if (Color.alpha(raw) == 0) return GlassBackdropCache.UNREADABLE;
            return frostColor(raw);
        } catch (Throwable ignored) {
            // A Sampler never throws: a band that cannot be read is a band that keeps the mode's
            // nominal glass and asks again next frame.
            return GlassBackdropCache.UNREADABLE;
        }
    }

    /**
     * A pre-blurred frame already in the cache, or null. Never asks for one that is not resident:
     * a sample is worth no decode, and the band self-heals.
     */
    @Nullable
    private Bitmap residentFrame() {
        View wallpaperFrame = mSurfaces.findChromeView(R.id.activity_termux_root_view);
        if (wallpaperFrame == null) return null;
        int statusRadius = mSurfaces.effectiveStatusBarBlurRadiusDp();
        int dockRadius = mSurfaces.effectiveDockBlurRadiusDp();
        for (int radius : new int[] {statusRadius, dockRadius, 0}) {
            if (radius >= 0 && mBlurCache.hasRadius(radius)) {
                return mBlurCache.obtain(radius, wallpaperFrame);
            }
        }
        return null;
    }

    /** True when {@code config} can be read with {@code getPixel}; {@code HARDWARE} cannot. */
    @VisibleForTesting
    public static boolean readableInSoftware(@Nullable Bitmap.Config config) {
        return config != null && config != Bitmap.Config.HARDWARE;
    }

    /**
     * The mean of {@code region} of a bitmap that cannot be read directly: the region is drawn,
     * scaled, into a small software bitmap and averaged from there.
     */
    @ColorInt
    private static int sampleThroughSoftwareCopy(@NonNull Bitmap source, @NonNull Rect region) {
        int left = Math.max(0, region.left);
        int top = Math.max(0, region.top);
        int right = Math.min(source.getWidth(), region.right);
        int bottom = Math.min(source.getHeight(), region.bottom);
        if (right <= left || bottom <= top) return GlassBackdropCache.UNREADABLE;
        int width = Math.min(GlassBackdropCache.SAMPLE_GRID, right - left);
        int height = Math.min(GlassBackdropCache.SAMPLE_GRID, bottom - top);
        Bitmap scratch = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            android.graphics.Canvas canvas = new android.graphics.Canvas(scratch);
            android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(source, new Rect(left, top, right, bottom),
                new Rect(0, 0, width, height), paint);
            int[] pixels = new int[width * height];
            scratch.getPixels(pixels, 0, width, 0, 0, width, height);
            return GlassBackdropCache.averageColor(pixels, 0, pixels.length);
        } finally {
            scratch.recycle();
        }
    }

    /**
     * {@link GlassFilters#frost()} applied to a single colour: the same saturation boost and
     * contrast deepen the backdrop is drawn through, as arithmetic rather than as a
     * {@code ColorMatrixColorFilter}, so a measurement matches what is on screen.
     *
     * <p>{@code ColorMatrix.setSaturation} is the standard luminance-weighted matrix (0.213, 0.715,
     * 0.072) and the vibrancy pass that is {@code postConcat}ed onto it is diagonal, so the whole
     * filter is one saturation blend followed by {@code c * channel + t}.</p>
     */
    @ColorInt
    @VisibleForTesting
    public static int frostColor(@ColorInt int color) {
        final float saturation = 1.30f;
        final float contrast = 1.06f;
        final float offset = -6f;
        float red = Color.red(color);
        float green = Color.green(color);
        float blue = Color.blue(color);
        float luma = 0.213f * red + 0.715f * green + 0.072f * blue;
        float invSat = 1f - saturation;
        float outRed = luma * invSat + red * saturation;
        float outGreen = luma * invSat + green * saturation;
        float outBlue = luma * invSat + blue * saturation;
        return Color.rgb(
            clampChannel(outRed * contrast + offset),
            clampChannel(outGreen * contrast + offset),
            clampChannel(outBlue * contrast + offset));
    }

    private static int clampChannel(float value) {
        int rounded = Math.round(value);
        return rounded < 0 ? 0 : (rounded > 255 ? 255 : rounded);
    }
}
