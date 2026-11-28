package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.window.WindowLayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Terl's glass in the glass service (ADR 0021, 0029, 0031 section 2, 0040): every surface that
 * wears Terl's glass (the control bar, its full-screen pill and reading card, the sheets) is an
 * entry of the registry for as long as its view is in the window, so it records what is beneath
 * it, windows and pages included, and blurs that, as v1's dock and panels do.
 *
 * <ul>
 *   <li>The control bar is the dock's entry ({@link #BAR_ELEMENT}): one element, the Live
 *   priority, the chrome layer. v1's dock stands down under Terl and holds no entry.</li>
 *   <li>Every other Terl glass is a panel in the sheets layer, at the dock's priority, as v1's
 *   drawer, palette and sheet are ({@link PanelBackdrop}), one element per view.</li>
 *   <li>Every window's glass is an entry too ({@link #windowElement}), at the tiles' or the
 *   Floats' priority (live window residency): granted Live, it draws by the route the desktop
 *   set at rest, the stored frame at the same strength on the parity route (no recorder, no
 *   slot) or the recorder's layers on the effect route.</li>
 * </ul>
 *
 * <p>The service grants the kind from the Live blur switch and its policy. Granted Live, the glass
 * is the recorder's layers (ground, pages, local) under the Terl material (the surface's own
 * Opacity and Blur, ADR 0040); granted anything else, the stored pre-blurred wallpaper frame, as
 * before. A policy event re-dresses the glass in place: the drawable a view was handed swaps what
 * it draws, so no owner has to dress again. The stored frame is re-dressed when the frame it
 * would sample lands or changes ({@link #refreshStored}), and redrawn when the wallpaper pans
 * ({@link #invalidateStored}), so the bar follows a Home page change as the dock did.</p>
 */
public final class TerlGlassWear {

    /** The control bar's element: the dock's ({@code TermuxActivity.DOCK_GLASS}). */
    public static final String BAR_ELEMENT = "dock";

    /** Builds one view's glass for what it is granted. */
    public interface Builder {
        /**
         * The glass over {@code live}, the recorder's picture of what is beneath; null for the
         * stored frame.
         */
        @Nullable
        Drawable build(@Nullable LivePicture live);

        /**
         * The stored frame the glass would sample now, compared by identity to the one it was
         * built with ({@link #restores}); null when it samples none.
         */
        @Nullable
        Object frameNow();
    }

    /** The service, read when a glass is worn: null before the window has one. */
    public interface Service {
        @Nullable
        GlassService glass();
    }

    @NonNull private final Service mService;
    @NonNull private final HashMap<View, Worn> mWorn = new HashMap<>();

    public TerlGlassWear(@NonNull Service service) {
        mService = service;
    }

    /**
     * The registry element of a Terl glass on {@code anchor}: the dock's for the control bar
     * itself, else one of the anchor's own (a sheet, the pill, the reading card), so two Terl
     * glasses never refuse each other as twins.
     */
    @NonNull
    public static String element(boolean bar, @NonNull String surfaceKey, int anchorIdentity) {
        if (bar) return BAR_ELEMENT;
        return "terl-" + surfaceKey.toLowerCase(Locale.ROOT) + "-"
            + Integer.toHexString(anchorIdentity);
    }

    /** The registry element of window {@code windowId}'s glass. */
    @NonNull
    public static String windowElement(long windowId) {
        return "terl-window-" + windowId;
    }

    /**
     * The kind a Terl glass asks for: Live where the recorder can draw it (on the GPU, Android 12
     * and later, in the launcher's window), else nothing to ask for, and the stored frame stands.
     * The service grants Live or the stored frame from the Live blur switch and its policy.
     */
    @Nullable
    public static GlassKind wants(int sdkInt, boolean samples) {
        if (!samples) return null;
        return PanelBackdropPolicy.layered(sdkInt, true) ? GlassKind.LIVE : null;
    }

    /**
     * Whether a glass that wears the stored frame is dressed again: when the frame it would sample
     * now is not the one it holds (it landed after the glass was dressed, the wallpaper or the
     * radius changed, the cache let it go). A Live glass draws the recorder's layers, never this.
     */
    public static boolean restores(boolean live, @Nullable Object heldFrame, @Nullable Object frameNow) {
        return !live && heldFrame != frameNow;
    }

    /**
     * {@code anchor}'s glass: registered as {@code element} in the window layer it stands in
     * ({@code layer} while it stands in none) while it samples what
     * is beneath ({@code samples}), built by {@code builder} for the kind the service grants. Asked
     * again on every dress; the drawable handed back re-dresses itself on a policy event.
     */
    @NonNull
    public Drawable wear(@NonNull View anchor, @NonNull String element, @NonNull WindowLayer layer,
                         float blurRadiusPx, boolean samples, @NonNull Builder builder) {
        return wear(anchor, element, layer, GlassEntry.PRIORITY_DOCK, blurRadiusPx, samples, builder);
    }

    /**
     * {@link #wear(View, String, WindowLayer, float, boolean, Builder)} at the budget's
     * {@code priority} ({@code GlassEntry.PRIORITY_*}): a window's glass is a tile's or a Float's.
     */
    @NonNull
    public Drawable wear(@NonNull View anchor, @NonNull String element, @NonNull WindowLayer layer,
                         int priority, float blurRadiusPx, boolean samples, @NonNull Builder builder) {
        Worn worn = mWorn.get(anchor);
        if (worn != null && !worn.element.equals(element)) {
            release(anchor);
            worn = null;
        }
        if (worn == null) {
            worn = new Worn(anchor, element);
            mWorn.put(anchor, worn);
        }
        worn.layer = layer;
        worn.priority = priority;
        worn.blurRadiusPx = Math.max(0f, blurRadiusPx);
        worn.samples = samples;
        worn.builder = builder;
        worn.glass = new SwappingGlass();
        dress(worn);
        return worn.glass;
    }

    /** Dresses every stored-frame glass again whose frame moved ({@link #restores}). */
    public void refreshStored() {
        if (mWorn.isEmpty()) return;
        List<Worn> worn = new ArrayList<>(mWorn.values());
        for (int i = 0; i < worn.size(); i++) {
            Worn w = worn.get(i);
            // A glass refused a Live picture asks again on the same passes: the recorder may have
            // room now (another surface let its picture go), which no grant change reports.
            if (w.refused || restores(w.live, w.heldFrame, w.builder.frameNow())) dress(w);
        }
    }

    /** The wallpaper panned: every stored-frame glass samples it where it is drawn now. */
    public void invalidateStored() {
        for (Worn w : mWorn.values()) {
            if (!w.live && w.heldFrame != null) w.anchor.invalidate();
        }
    }

    /** Whether any Terl glass holds {@code frame}: the cache must not recycle it under it. */
    public boolean holds(@Nullable Object frame) {
        if (frame == null) return false;
        for (Worn w : mWorn.values()) if (w.heldFrame == frame) return true;
        return false;
    }

    /**
     * The blur cache was cleared (a new wallpaper, a trim): every stored-frame glass is dressed
     * again now with what the cache has (nothing, until the next frame lands and
     * {@link #refreshStored} dresses it with that), so the frame it drew is let go by the drawable
     * itself. Until then {@link #holds} keeps answering for it, since the drawable still draws it:
     * the bookkeeping follows what the drawable holds (review P2). Called after the clear, posted.
     */
    public void onCacheCleared() {
        if (mWorn.isEmpty()) return;
        List<Worn> worn = new ArrayList<>(mWorn.values());
        for (int i = 0; i < worn.size(); i++) {
            Worn w = worn.get(i);
            if (!w.live && w.heldFrame != null && mWorn.get(w.anchor) == w) dress(w);
        }
    }

    /** Lets {@code anchor}'s Terl glass go, if it wears one: its entry, its picture and its frame. */
    public void letGo(@NonNull View anchor) {
        release(anchor);
    }

    /** Lets every Terl glass go: its entry, its picture and the frame it held. */
    public void releaseAll() {
        for (View anchor : new ArrayList<>(mWorn.keySet())) release(anchor);
    }

    /** The elements worn now; for tests. */
    @VisibleForTesting
    @NonNull
    List<String> elements() {
        List<String> out = new ArrayList<>();
        for (Worn w : mWorn.values()) out.add(w.element);
        return out;
    }

    /** Registers (or lets go of) the entry for what the glass samples now, then builds it. */
    private void dress(@NonNull Worn worn) {
        // The layer the view actually stands in, not one inferred from its look (review P2: a hint
        // wears the sheets' look but stands in the chrome layer); the one given only stands in
        // while the view is in no layer host.
        WindowLayer actual = com.termux.app.window.WindowLayers.layerOf(worn.anchor);
        if (actual != null) worn.layer = actual;
        GlassService glass = mService.glass();
        GlassKind wants = wants(Build.VERSION.SDK_INT, worn.samples);
        LivePicture live = null;
        if (glass != null && wants != null && worn.anchor.isAttachedToWindow()) {
            Worn w = worn;
            glass.declare(worn.element, Collections.singletonList(new GlassEntry(worn.element,
                worn.anchor, wants, worn.priority, worn.layer, null,
                kind -> onGranted(w))), false);
            // A twin (another view holds the element) is refused: it wears the stored frame.
            GlassKind granted = glass.grantedTo(worn.element, worn.anchor);
            // Asked whatever the grant: granted Live it attaches; granted anything else it lets
            // go of a recording kept from a Live grant now lost, so the slot goes to the next
            // surface (review P1: a stale recording kept a newly granted surface off Live).
            if (granted != null) {
                live = glass.livePicture(worn.element, worn.blurRadiusPx, LivePicture.class);
            }
            // Granted Live but refused a picture (the recorder full): asked again when room frees.
            // The parity route draws the stored frame by design, which is no refusal.
            worn.refused = granted == GlassKind.LIVE && live == null
                && glass.route(worn.element) != com.termux.app.glass.GlassRoute.PARITY;
            if (granted != null) cover(glass, worn);
        } else if (glass != null) {
            glass.undeclare(worn.element);
            worn.covered.setEmpty();
            worn.refused = false;
        }
        boolean wasLive = worn.live;
        worn.live = live != null;
        worn.heldFrame = live != null ? null : worn.builder.frameNow();
        Drawable built = worn.builder.build(live);
        SwappingGlass target = worn.glass;
        if (target != null) target.show(built);
        // A recording let go frees a slot: a glass refused one asks again.
        if (wasLive && !worn.live) retryRefused(worn);
    }

    /** Every glass granted Live but refused a picture asks for it again (a slot freed), but {@code except}. */
    private void retryRefused(@Nullable Worn except) {
        if (mWorn.isEmpty()) return;
        List<Worn> worn = new ArrayList<>(mWorn.values());
        for (int i = 0; i < worn.size(); i++) {
            Worn w = worn.get(i);
            if (w != except && w.refused && mWorn.get(w.anchor) == w) dress(w);
        }
    }

    /**
     * A sheet at rest covers what it hides (ADR 0029 section 4, {@code GlassCover}): the service
     * takes the Live slots of the surfaces wholly under it, so the sheet and what rides on it are
     * Live first, as v1's drawer card does. Its rest rectangle is its laid-out place in the window,
     * the translations of its opening and closing left out; told once per change, never per frame.
     * The cover goes with the sheet's entry ({@link GlassService#unregister} uncovers).
     */
    private void cover(@NonNull GlassService glass, @NonNull Worn worn) {
        if (!covers(worn.layer)) return;
        restRectInWindow(worn.anchor, mCoverRect);
        if (mCoverRect.isEmpty() || mCoverRect.equals(worn.covered)) return;
        worn.covered.set(mCoverRect);
        glass.cover(worn.element, mCoverRect);
    }

    /** Whether a Terl glass in {@code layer} covers what is beneath it at rest: the sheets do. */
    public static boolean covers(@NonNull WindowLayer layer) {
        return layer == WindowLayer.SHEETS;
    }

    /**
     * {@code view}'s laid-out rectangle in its window: its parents' layout offsets less their
     * scroll, without any translation, so a sheet sliding open is covered where it comes to rest.
     */
    static void restRectInWindow(@NonNull View view, @NonNull Rect out) {
        int left = 0;
        int top = 0;
        View at = view;
        while (true) {
            left += at.getLeft();
            top += at.getTop();
            if (!(at.getParent() instanceof View)) break;
            View parent = (View) at.getParent();
            left -= parent.getScrollX();
            top -= parent.getScrollY();
            at = parent;
        }
        out.set(left, top, left + view.getWidth(), top + view.getHeight());
    }

    @NonNull private final Rect mCoverRect = new Rect();

    /** A policy event moved the kind: the glass is dressed again as what it is granted now. */
    private void onGranted(@NonNull Worn worn) {
        if (mWorn.get(worn.anchor) != worn) return;
        dress(worn);
    }

    private void release(@NonNull View anchor) {
        Worn worn = mWorn.remove(anchor);
        if (worn == null) return;
        anchor.removeOnAttachStateChangeListener(worn);
        GlassService glass = mService.glass();
        // Letting the entry go uncovers what the sheet covered (GlassService#unregister).
        if (glass != null) glass.undeclare(worn.element);
        worn.heldFrame = null;
        worn.covered.setEmpty();
        // Its picture went with its entry: a glass refused one may take the slot.
        if (worn.live) retryRefused(null);
    }

    /** One view's Terl glass: its entry, how it was dressed, and the drawable it was handed. */
    private final class Worn implements View.OnAttachStateChangeListener {
        @NonNull final View anchor;
        @NonNull final String element;
        @NonNull WindowLayer layer = WindowLayer.SHEETS;
        int priority = GlassEntry.PRIORITY_DOCK;
        float blurRadiusPx;
        boolean samples;
        @NonNull Builder builder = NO_BUILDER;
        @Nullable SwappingGlass glass;
        /** Whether it draws the recorder's Live picture now. */
        boolean live;
        /** The stored frame it was built with; null for none (or while Live). */
        @Nullable Object heldFrame;
        /** Granted Live but refused a picture by the recorder (full): asked again when one frees. */
        boolean refused;
        /** The rest rectangle the service was told it covers; empty for none. */
        @NonNull final Rect covered = new Rect();

        Worn(@NonNull View anchor, @NonNull String element) {
            this.anchor = anchor;
            this.element = element;
            anchor.addOnAttachStateChangeListener(this);
        }

        /** Dressed before it was in the window: it registers now. */
        @Override public void onViewAttachedToWindow(@NonNull View v) {
            if (mWorn.get(anchor) == this && glass != null) dress(this);
        }

        /** A sheet that closed leaves the window: its entry and picture go, so it holds no pixels. */
        @Override public void onViewDetachedFromWindow(@NonNull View v) {
            release(anchor);
        }
    }

    private static final Builder NO_BUILDER = new Builder() {
        @Nullable @Override public Drawable build(@Nullable LivePicture live) { return null; }
        @Nullable @Override public Object frameNow() { return null; }
    };

    /**
     * The glass over the recorder's Live picture: the picture, frosted as a stored frame is
     * ({@link GlassFilters#frost(int)}), then the Terl material (tint, grain), cut to the rounded
     * outline. With {@code refract} (the control bar, which wears the dock's glass, ADR 0033) the
     * picture is drawn through the physical glass program as the dock's backdrop is
     * ({@link GlassRefraction}, the rim and the bent edge from the shader) and the material lays
     * no rim of its own: one rim. Without it, or where the program cannot run, the picture is
     * plain and the material draws the rim, as v1's panels wear their Live picture.
     */
    @NonNull
    public static Drawable overLive(@NonNull GlassSurfaceFactory glass, @NonNull GlassStack.Spec spec,
                                    float density, @NonNull LivePicture live, boolean refract) {
        Drawable picture = live.drawable();
        picture.setColorFilter(GlassFilters.frost(Math.round(spec.tintAlpha * 100f)));
        Drawable shown = new ContentBoxPicture(picture, live.surface());
        GlassRefraction.Program program = refract ? refraction(density) : null;
        if (program != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shown = new RefractedPicture(shown, program, spec.look, density, spec.cornerRadiusPx,
                spec.seams);
        }
        Drawable material = GlassStack.build(glass, liveMaterialSpec(spec, program != null),
            density, null);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {shown, material});
        return new OutlinedGlass(layers, spec.cornerRadiusPx);
    }

    /**
     * The material over a Live picture: {@code spec} with no outline of its own (the stack is cut
     * once, round both), and no rim where the picture is drawn through the program, whose rim is
     * the surface's one (ADR 0033: the rim is drawn once, by the shader).
     */
    @NonNull
    static GlassStack.Spec liveMaterialSpec(@NonNull GlassStack.Spec spec, boolean programRim) {
        GlassStack.Spec material = spec.withOwnOutline(false);
        return programRim ? material.withRim(false) : material;
    }

    /** A refraction program for a Live picture, or null where none can run (below Android 13). */
    @Nullable
    private static GlassRefraction.Program refraction(float density) {
        if (!GlassRefraction.available() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null;
        }
        try {
            // At the pass's scale: the look's dp become the smaller node's px.
            GlassRefraction.Program program = GlassRefraction.Program.create(
                density / REFRACTION_SCALE);
            if (program == null) return null;
            // The picture is the backdrop alone: it takes the glass's colour, as the dock's does.
            program.setBackdropContent(true);
            // The node's own pixels are the content: the aim is identity.
            program.setAim(1f, 1f, 0f, 0f);
            return program;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * A Live picture drawn through the physical glass program, as the dock's backdrop view is
     * ({@code GlassEffects}): the picture is recorded into a node of its own whose effect is the
     * program's runtime shader over it, so the rim, the bent edge and the light on it are the
     * shader's. The node is recorded again on each draw (it holds the picture's blur node by
     * reference, a handful of operations); the effect is built again only when the bounds move,
     * since an effect copies the program's uniforms as they are. A software canvas draws the
     * picture plain.
     */
    /**
     * How much smaller than the surface the refraction pass runs: half on each side. The picture
     * under it is the blur at a quarter (LivePicture.SCALE), so nothing of the picture is lost; the
     * pass's own offscreen layer stays inside the bar picture's share of the Live budget
     * ({@link LiveBackdrop#MAX_LAYER_BYTES}, LiveBackdropBudgetTest), where a full-size pass at the
     * worst bar (2.9 MB) would not (review P2).
     */
    static final int REFRACTION_SCALE = 2;

    /** The refraction pass's offscreen layer for a surface {@code width} by {@code height} px, in bytes. */
    static long refractionBytes(int width, int height) {
        long w = (Math.max(0, width) + REFRACTION_SCALE - 1) / REFRACTION_SCALE;
        long h = (Math.max(0, height) + REFRACTION_SCALE - 1) / REFRACTION_SCALE;
        return w * h * 4L;
    }

    /**
     * A Live picture drawn through the physical glass program, as the dock's backdrop view is
     * ({@code GlassEffects}): the picture is recorded into a node of its own, at
     * {@link #REFRACTION_SCALE}, whose effect is the program's runtime shader over it, so the rim,
     * the bent edge and the light on it are the shader's; the node is drawn scaled back up. The
     * node is recorded again on each draw (it holds the picture's blur node by reference, a
     * handful of operations); the effect is built again only when the bounds move, since an
     * effect copies the program's uniforms as they are. A software canvas draws the picture plain.
     */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    static final class RefractedPicture extends Drawable implements Drawable.Callback {
        @NonNull private final Drawable mPicture;
        @NonNull private final GlassRefraction.Program mProgram;
        @NonNull private final android.graphics.RenderNode mNode =
            new android.graphics.RenderNode("terl-bar-glass");
        private final float mRadiusPx;
        private final int mSeams;
        private final float mReachPx;
        @NonNull private final float[] mRim = new float[4];
        private int mNodeWidth;
        private int mNodeHeight;

        /** {@code program} made at the pass's density ({@link #refraction}), the surface's divided. */
        RefractedPicture(@NonNull Drawable picture, @NonNull GlassRefraction.Program program,
                         @NonNull GlassRefraction.Look look, float density, float radiusPx,
                         int seams) {
            mPicture = picture;
            mProgram = program;
            mRadiusPx = radiusPx / REFRACTION_SCALE;
            mSeams = seams;
            mReachPx = GlassRefraction.seamReachPx(look, density / REFRACTION_SCALE, mRadiusPx);
            program.setLook(look);
            picture.setCallback(this);
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            mPicture.setBounds(bounds);
            mNodeWidth = (bounds.width() + REFRACTION_SCALE - 1) / REFRACTION_SCALE;
            mNodeHeight = (bounds.height() + REFRACTION_SCALE - 1) / REFRACTION_SCALE;
            mNode.setPosition(0, 0, mNodeWidth, mNodeHeight);
            if (mNodeWidth <= 0 || mNodeHeight <= 0) return;
            GlassRefraction.rimRect(mRim, 0f, 0f, mNodeWidth, mNodeHeight, mReachPx, mSeams);
            mProgram.setRect(mRim[0], mRim[1], mRim[2], mRim[3],
                GlassRefraction.rectRadiusPx(mRim[0], mRim[1], mRim[2], mRim[3], mRadiusPx));
            mNode.setRenderEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(
                mProgram.shader(), "content"));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (!(canvas instanceof android.graphics.RecordingCanvas) || bounds.isEmpty()
                    || mNodeWidth <= 0 || mNodeHeight <= 0) {
                mPicture.draw(canvas);
                return;
            }
            android.graphics.RecordingCanvas node = mNode.beginRecording(mNodeWidth, mNodeHeight);
            try {
                node.scale(1f / REFRACTION_SCALE, 1f / REFRACTION_SCALE);
                node.translate(-bounds.left, -bounds.top);
                mPicture.draw(node);
            } finally {
                mNode.endRecording();
            }
            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(REFRACTION_SCALE, REFRACTION_SCALE);
            ((android.graphics.RecordingCanvas) canvas).drawRenderNode(mNode);
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) { mPicture.setAlpha(alpha); }

        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mPicture.setColorFilter(colorFilter);
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        @Override public void invalidateDrawable(@NonNull Drawable who) { invalidateSelf(); }

        @Override public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            unscheduleSelf(what);
        }
    }

    /**
     * The Live picture drawn as a view's background: the picture expects its holder to have moved
     * the canvas to the content box (an ImageView does), which a background is not, so it is moved
     * there first.
     */
    static final class ContentBoxPicture extends DrawableWrapper {
        @NonNull private final View mSurface;

        ContentBoxPicture(@NonNull Drawable picture, @NonNull View surface) {
            super(picture);
            mSurface = surface;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int left = mSurface.getPaddingLeft();
            int top = mSurface.getPaddingTop();
            if (left == 0 && top == 0) {
                super.draw(canvas);
                return;
            }
            int save = canvas.save();
            canvas.translate(left, top);
            super.draw(canvas);
            canvas.restoreToCount(save);
        }
    }

    /**
     * The drawable a view is handed: it draws whatever its glass was last dressed as, so a policy
     * event or a landed frame swaps the picture without the owner dressing again.
     */
    static final class SwappingGlass extends DrawableWrapper {
        SwappingGlass() {
            super(null);
        }

        void show(@Nullable Drawable glass) {
            Rect bounds = getBounds();
            setDrawable(glass);
            if (glass != null) glass.setBounds(bounds);
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (getDrawable() != null) super.draw(canvas);
        }

        @Override
        public int getOpacity() {
            return getDrawable() == null ? PixelFormat.TRANSPARENT : PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            if (getDrawable() != null) super.setColorFilter(colorFilter);
        }
    }
}
