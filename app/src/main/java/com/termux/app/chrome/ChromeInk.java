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
 * <h3>Panes, and bands that share one</h3>
 * <p>A band is a question; a pane is a sheet of glass. They are usually the same thing, and twice
 * in this app they are not: the status strip's content and the window chips both stand on
 * {@code terminal_window_bar_background}, at different targets and in different hues. One pane can
 * only wear one veil, so the pane wears the stronger of the two demands and both bands are then
 * toned against that — see {@link #resolveBand}. The strip of glass behind the system status bar is
 * the other half of the same fact: it continues the pane's material but carries no content, so it
 * is nobody's band and asks for no veil of its own.</p>
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

    /**
     * The standing question a band's content has asked: which two inks, at which target, and where
     * the band was when it asked. Kept so {@link #bandVeil} can answer the surface builder with the
     * same resolution {@link #onGlass} would return, in whichever order the two are called.
     */
    private static final class Contract {
        @NonNull final Rect rect = new Rect();
        int darkInk;
        int paleInk;
        double target;
        /** The veil the band last derived; only for noticing a change worth a repaint. */
        int veil = Color.TRANSPARENT;
        boolean seen;
    }

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
    /**
     * What each band's content asked for, so the band's veil can be re-derived by whoever needs it
     * next instead of being handed across a render pass. See {@link #bandVeil}.
     */
    @NonNull private final Map<GlassBackdropCache.Band, Contract> mContracts =
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
        Contract contract = contractFor(band);
        contract.rect.set(screenRect);
        contract.darkInk = darkInk;
        contract.paleInk = paleInk;
        contract.target = target;
        contract.seen = true;
        return resolveBand(band, contract);
    }

    /**
     * The band's whole answer, from its standing {@link Contract}. Every path — the content asking
     * for its ink, the surface builder asking what to veil with — comes through here, so the two
     * cannot disagree and neither has to run before the other.
     *
     * <p>This is also where the pane's one veil is settled. A pane is drawn once, so the veil it
     * wears is one number, and two bands standing on it ask for two. The answer is the stronger
     * demand — derived here from every co-tenant's own resolution rather than left to whichever of
     * them resolved last — and this band is then resolved again against it, so what comes back is
     * toned on the surface that is really under it and not on the veil it would have bought
     * alone.</p>
     */
    @NonNull
    private OnGlass.Resolution resolveBand(@NonNull GlassBackdropCache.Band band,
                                           @NonNull Contract contract) {
        OnGlass.Resolution own = resolveAt(band, contract, 0);
        int floor = Color.alpha(own.veil);
        for (GlassBackdropCache.Band other : GlassBackdropCache.Band.values()) {
            if (other == band || paneOf(other) != paneOf(band)) continue;
            Contract co = mContracts.get(other);
            if (co == null || !co.seen) continue;
            floor = Math.max(floor, Color.alpha(resolveAt(other, co, 0).veil));
        }
        OnGlass.Resolution resolved =
            floor == Color.alpha(own.veil) ? own : resolveAt(band, contract, floor);
        if (contract.veil != resolved.veil) {
            contract.veil = resolved.veil;
            // The band's material has to change, and the surface that draws it may already have
            // been built this pass. Ask for an apply before this frame is drawn rather than for a
            // plain render pass: a render pass can decline to run a successor when nothing it
            // tracks went dirty, and a veil dirties none of it — which is how a resolved veil used
            // to be resolved every pass and drawn in none of them.
            if (mOnVeilChanged != null) mOnVeilChanged.run();
        }
        return resolved;
    }

    /**
     * This band alone, with {@code floorAlpha} of its base colour taken as already drawn: the veil
     * search may ask for more, never for less. A floor of 0 is the band's own unconstrained demand,
     * which is what {@link #resolveBand} compares the co-tenants by.
     */
    @NonNull
    private OnGlass.Resolution resolveAt(@NonNull GlassBackdropCache.Band band,
                                         @NonNull Contract contract, int floorAlpha) {
        Rect screenRect = contract.rect;
        double target = contract.target;
        BandGlass glass = glassFor(band);
        int baseAlpha = ChromePolicy.dockGlassBaseAlpha(glass.opacity);
        // The band's flat glass: wallpaper, the launcher's dim, the base layer. No light model, so
        // the polarity vote is the same number whichever ink is asking.
        int wallpaper = mCache.wallpaperUnder(band, screenRect);
        int flat = OnGlass.backdrop(wallpaper, mDimColor, OnGlass.withAlpha(mBaseColor, baseAlpha));
        vote(band, flat);
        // What the band's own glass is laid on, and nothing of the glass itself: the base layer is
        // part of the tint that gets composited over this, so counting it here too would apply it
        // twice and pick the worst row off a band a shade lighter than the one being drawn.
        int under = OnGlass.backdrop(wallpaper, mDimColor, Color.TRANSPARENT);
        int ink = polarity() == Polarity.DARK_INK ? contract.darkInk : contract.paleInk;

        int top = DockGlassRendering.topSheenAlpha(glass.opacity);
        int mid = DockGlassRendering.midSheenAlpha(glass.opacity);
        int foot = DockGlassRendering.footAlpha(glass.opacity, glass.withFoot);

        // The row of the band the promise has to be made at depends on the ink, and a resolve can
        // change the ink: over a mid backdrop the seed's own polarity runs out of veil, the ink is
        // re-toned to the other one, and the row that was worst for the seed (the accent sheen, for
        // a pale ink) is no longer the row that is worst for what will be drawn (the black foot,
        // for a dark one). Measured at the seed's row alone the band came out 0.03 short at the
        // foot — a per-pixel promise broken by the one layer that was always able to break it. So
        // the row is re-checked against the ink that came back, and a second pass settles it: that
        // pass measures at the resolved ink's own worst row, and a dark ink's worst row is the
        // foot, which is the extreme of the model.
        float worst = DockGlassRendering.worstLightModelStop(ink, under, mBaseColor, baseAlpha,
            mAccentColor, top, mid, foot, glass.sliceStart, glass.sliceEnd);
        OnGlass.Resolution resolved = resolveAtStop(band, screenRect, worst, under, mBaseColor,
            baseAlpha, top, mid, foot, ink, target, floorAlpha);
        for (int pass = 0; pass < 2; pass++) {
            float again = DockGlassRendering.worstLightModelStop(resolved.ink, under, mBaseColor,
                baseAlpha, mAccentColor, top, mid, foot, glass.sliceStart, glass.sliceEnd);
            if (again == worst) break;
            worst = again;
            resolved = resolveAtStop(band, screenRect, worst, under, mBaseColor, baseAlpha, top,
                mid, foot, ink, target, floorAlpha);
        }
        return resolved;
    }

    /**
     * The band resolved with its glass measured at one model row: a veil of its own base colour
     * when that moves the surface toward {@code ink} at all, and a moved ink when it does not.
     *
     * <p>{@code floorAlpha} is what the pane is drawn with whatever this band asks — a co-tenant's
     * stronger demand. Nothing here can spend less than that, because less than that is not what
     * is on screen; a band whose own answer was bare is re-toned on the veiled surface instead.</p>
     */
    @NonNull
    private OnGlass.Resolution resolveAtStop(@NonNull GlassBackdropCache.Band band,
                                             @NonNull Rect screenRect, float stop,
                                             @ColorInt int under, @ColorInt int baseColor,
                                             int baseAlpha, int top, int mid, int foot,
                                             @ColorInt int ink, double target, int floorAlpha) {
        // Composed the way the LayerDrawable composes it, layer by layer, so the measurement and
        // the draw cannot round apart.
        int backdrop = DockGlassRendering.glassSurfaceAt(stop, under, baseColor, baseAlpha,
            mAccentColor, top, mid, foot);
        // Whichever rung the ladder ends on, a moved ink stays on the chrome's own side of the
        // band: the polarity is one decision for the whole chrome and a single band re-toning
        // across it would undo exactly what that decision is for.
        boolean pale = polarity() == Polarity.PALE_INK;
        if (OnGlass.ratio(ink, OnGlass.opaque(baseColor)) > OnGlass.ratio(ink, backdrop)) {
            OnGlass.Resolution own =
                mCache.resolveOn(band, screenRect, backdrop, ink, ink, baseColor, target, pale);
            if (Color.alpha(own.veil) >= floorAlpha) return own;
            return OnGlass.resolveUnder(backdrop, OnGlass.withAlpha(baseColor, floorAlpha), ink,
                target, pale);
        }
        // Veiling toward this band's own base colour would move the surface the wrong way for the
        // ink the chrome has settled on. Leave the wallpaper alone and move the ink — unless a
        // co-tenant of the same pane has already bought a veil, in which case the wallpaper is not
        // there to be left alone and the ink is toned on what is.
        if (floorAlpha <= 0) return OnGlass.resolveBare(backdrop, ink, target, pale);
        return OnGlass.resolveUnder(backdrop, OnGlass.withAlpha(baseColor, floorAlpha), ink,
            target, pale);
    }

    /**
     * A second ink on a band already resolved by {@link #onGlass}: legible at {@code target} on the
     * surface that band ended up with, without asking for any more veil, and in the chrome's own
     * polarity so it cannot read as the opposite of the label beside it.
     */
    @ColorInt
    public int inkOn(@NonNull OnGlass.Resolution resolved, @ColorInt int darkInk,
                     @ColorInt int paleInk, double target) {
        return inkOn(resolved, resolved.surface, darkInk, paleInk, target);
    }

    /**
     * {@link #inkOn(OnGlass.Resolution, int, int, double)} on a ground of the caller's own.
     *
     * <p>Content that draws a wash between itself and the band — a chip, a pill, a card — stands on
     * the wash, not on the band, and a ratio measured against {@code resolved.surface} is then a
     * ratio nobody sees. Such a caller composes what its content really stands on and passes it as
     * {@code ground}; the band is still what decides the polarity, which is the whole point of
     * coming through here rather than to {@link OnGlass} directly.</p>
     *
     * <p>On a ground of the caller's own the chrome's polarity is a <em>preference</em>, not the
     * mandate it is on the band. The polarity exists so that two things a few hundred pixels apart
     * on the same glass do not read as opposites; something that draws its own container has
     * already said it is not on that glass any more, and a container light enough to invert
     * locally — the session chip is one — has to be allowed its dark label. So the walk runs on the
     * chrome's side first and falls back to the legible answer when no tone on that side can clear
     * the target at all. The contrast floor is never the thing that gives way.</p>
     *
     * @param ground the opaque colour the content is really drawn on, the band's own surface with
     *     whatever the caller draws between
     */
    @ColorInt
    public int inkOn(@NonNull OnGlass.Resolution resolved, @ColorInt int ground,
                     @ColorInt int darkInk, @ColorInt int paleInk, double target) {
        boolean pale = polarity() == Polarity.PALE_INK;
        int ink = pale ? paleInk : darkInk;
        int opaqueGround = OnGlass.opaque(ground);
        int directed = OnGlass.inkOnBand(resolved, opaqueGround, ink, ink, target, pale);
        if (OnGlass.ratio(directed, opaqueGround) >= target) return directed;
        return OnGlass.inkOnBand(resolved, opaqueGround, darkInk, paleInk, target, null);
    }

    /**
     * The band's settled answer, without asking a question of your own — the read every consumer
     * that is <em>not</em> the band's owner should use.
     *
     * <p>A band has one veil, so it can only have one question: {@link #onGlass} records the ink
     * pair and the target its caller asked about, and the veil follows from those. Two consumers
     * calling {@code onGlass} on one band with different ink pairs is therefore two different
     * veils for one strip of glass, and which one it wears comes down to which of them drew last —
     * which is precisely what happened to the status bar, where the stats' own hues and the window
     * chips' neutrals each resolved it. So exactly one consumer per band calls {@link #onGlass},
     * and every other reads this and derives its own ink with
     * {@link #inkOn(OnGlass.Resolution, int, int, int, double)}.</p>
     *
     * <p>Null until the band's owner has asked; a consumer that finds null waits for the next pass
     * rather than resolving the band itself.</p>
     */
    @Nullable
    public OnGlass.Resolution resolution(@NonNull GlassBackdropCache.Band band) {
        readMode();
        Contract contract = mContracts.get(band);
        if (contract == null || !contract.seen) return null;
        return resolveBand(band, contract);
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
     * The veil {@code band}'s pane is drawn with: its own base colour at an alpha, or
     * {@link Color#TRANSPARENT} when everything standing on it reads bare. Drawn over the light
     * model. A pane two bands share answers the same number to both of them.
     */
    @ColorInt
    int bandVeil(@NonNull GlassBackdropCache.Band band) {
        OnGlass.Resolution resolved = resolution(band);
        if (resolved == null) return Color.TRANSPARENT;
        // Derived here and now, not read from something {@link #onGlass} left behind. The surface
        // builder and the band's content run in an order neither of them controls, and a veil
        // handed from one to the other across a pass is a veil that is resolved every frame and
        // drawn in none — which is exactly what a mid-tone wallpaper produced: an ink toned for a
        // veiled band, over a band that was never veiled, at 1.7:1.
        return resolved.veil;
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
     * <p>Two bands that share a pane get that pane's rect, which is the point: what they are drawn
     * over is one wash of wallpaper, and asking about it twice from two rects would sample it
     * twice and let the two answers drift.</p>
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
        switch (paneOf(band)) {
            case WINDOW_BAR: return R.id.terminal_window_bar_background;
            default: return 0;
        }
    }

    /**
     * The pane a band's glass is really drawn on. Usually the band itself; where two bands stand on
     * one sheet of glass, the one that owns it.
     *
     * <p>{@link GlassBackdropCache.Band#STATUS_BAR} is the launcher's own status strip — the
     * CPU/RAM/weather widgets, the separator dots, the lens, the sessions chip — and every one of
     * them is laid out inside {@code terminal_window_bar_host}, whose glass is
     * {@code terminal_window_bar_background}. That is the same pane
     * {@link GlassBackdropCache.Band#WINDOW_BAR}'s chips stand on. The band this used to point at,
     * {@code terminal_status_bar_background}, carries no content at all: it is the continuation of
     * that pane's glass through the system status-bar inset. Measuring the strip and veiling the
     * strip put the whole veil where nothing stands and left the content on bare glass — a whitish
     * wash under the system status bar in light mode, a dark one in dark mode, and widgets still
     * under their ratio on the pane below it.</p>
     */
    @NonNull
    private static GlassBackdropCache.Band paneOf(@NonNull GlassBackdropCache.Band band) {
        return band == GlassBackdropCache.Band.STATUS_BAR
            ? GlassBackdropCache.Band.WINDOW_BAR
            : band;
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
        for (Contract contract : mContracts.values()) contract.veil = Color.TRANSPARENT;
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

    @NonNull
    private Contract contractFor(@NonNull GlassBackdropCache.Band band) {
        Contract contract = mContracts.get(band);
        if (contract == null) {
            contract = new Contract();
            mContracts.put(band, contract);
        }
        return contract;
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

    /** Keyed by the pane, not by the band: co-tenants are drawn by one surface and share its glass. */
    @NonNull
    private BandGlass glassFor(@NonNull GlassBackdropCache.Band band) {
        GlassBackdropCache.Band pane = paneOf(band);
        BandGlass glass = mGlass.get(pane);
        if (glass == null) {
            glass = new BandGlass();
            mGlass.put(pane, glass);
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
