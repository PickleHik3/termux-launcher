package com.termux.app.place;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.SuggestionBarView;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockMorph;
import com.termux.app.wall.PaneWallPage;

/**
 * What a crossing moves ({@link Crossing}'s adapter), and the last values it wrote, so a frame that
 * repeats one touches no view: the accessory stack's translation, its rows' sink into the
 * keyboard and a fold's, the travel alphas, the status bar's ride, the travel cover the pane
 * controller holds for the window in front, and the keyboard brought up for a slide. Only
 * views are written here; every decision is the crossing's.
 *
 * <p>The activity gives it what only the activity has through its {@link Host}: the window's
 * views, the travel's shares it keeps for its own layout passes, the pane and wall controllers,
 * the keyboard, the dock's row and the picture of the stack.
 */
public final class CrossingViews implements Crossing.Stack, Crossing.Bar, Crossing.Cover,
        Crossing.Keyboard, Crossing.Dock {

    /** What only the activity can give the adapter. */
    public interface Host {
        /** The window's view of {@code id}, found once per tree; null while it has none. */
        @Nullable View hotView(int id);
        /**
         * The accessory stack drawn {@code dockPx} lower, of which {@code keyboardPx} is the
         * keyboard's: the travel's shares kept, and the stack's translation applied.
         */
        void translateStack(float dockPx, float keyboardPx);
        /** The status bar drawn {@code px} from its rest, with the strip behind the system bar. */
        void rideStatusX(float px);
        /** The travel cover the pane controller holds for the window in front. */
        void coverWindowInFront(int px);
        /** The in-app keyboard brought up below the screen for {@code target}'s half, quietly. */
        void preRollKeyboard(@NonNull PaneWallPage target);
        /** Whether the extra keys row is on screen in the dock's rows. */
        boolean extraKeysRowOnScreen();
        /** The terminal's flush dock padding as last applied. */
        int terminalFlushPaddingPx();
        /** The dock's pinned row, which draws the morph; null before it is inflated. */
        @Nullable SuggestionBarView dockRow();
        /** The beads under the dock's icons where the row's morph draws them now. */
        void syncDockBeadsWithMorph();
        /** One of the row's icons was drawn again: the reflection shows it as the icon does. */
        void onDockRowContentChanged();
        /**
         * The view the committed half's stack is pictured in, made beside {@code stack} the first
         * time one is asked for; with a null {@code stack}, the one made already, or null.
         */
        @Nullable StackPicture stackPicture(@Nullable View stack);
        /** The wall's width; 0 while there is no wall or it is not laid out. */
        int wallWidthPx();
        /** The terminal's page laid out {@code px} shorter at its bottom than the wall. */
        void applyTerminalPreSize(int px);
    }

    /** The dock rows' layers, which sink into the keyboard together ({@link #sink}). */
    private static final int[] SEAM_ROW_LAYERS = {R.id.accessory_surface_host,
        R.id.apps_bar_az_fx_underlay, R.id.accessory_row_stack, R.id.apps_bar_az_fx_overlay,
        R.id.apps_bar_az_label_overlay};
    /** The layers drawn over the A-Z row alone, which a fold that holds that row leaves be. */
    private static final int[] SEAM_AZ_LAYERS = {R.id.apps_bar_az_fx_underlay,
        R.id.apps_bar_az_fx_overlay, R.id.apps_bar_az_label_overlay};

    private static boolean isSeamAzLayer(int id) {
        for (int az : SEAM_AZ_LAYERS) if (az == id) return true;
        return false;
    }

    /**
     * How long the picture of a stack put back at its rest stays under it: past the keyboard's
     * reveal gate's own backstop (160 ms), so a keyboard brought back up has drawn by then.
     */
    static final long PICTURE_HANDOVER_MS = 200L;

    @NonNull private final Context mContext;
    @NonNull private final Host mHost;
    /** The crossing this adapts, whose fold share and seam a fold's sink reads. */
    @Nullable private Crossing mCrossing;
    /** The clip a sunk layer is cut at, reused by every frame. */
    private final Rect mTmpTravelClip = new Rect();

    /** How far the dock rows' layers last sank into the keyboard's top edge. */
    private float mRowSinkPx;
    /** How much of the rows' layers that sink cut; and the sheet's sink and cut, for a fold. */
    private float mRowCutPx;
    private float mSheetSinkPx;
    private float mSheetCutPx;
    /** How far a fold sank the A-Z row's own layers while it held that row. */
    private float mHeldSinkPx;
    /** Whether that sink left the A-Z row's layers standing, for a fold that holds the row. */
    private boolean mSinkHoldsAz;
    /** The row stack re-reads a running fold's sink whenever it is laid out; added once. */
    private boolean mFoldListening;
    private final View.OnLayoutChangeListener mFoldLayoutListener =
        (v, l, t, r, b, ol, ot, orr, ob) -> applyFoldSink();
    /** The alphas the travel last wrote, 1 when it has put everything back. */
    private float mStatusAlpha = 1f;
    private float mSideAlpha = 1f;

    /** @param context whose display a sunk layer's clip reaches up through */
    public CrossingViews(@NonNull Context context, @NonNull Host host) {
        mContext = context;
        mHost = host;
    }

    /** The crossing this adapter moves for, once it is made with it. */
    public void attach(@NonNull Crossing crossing) {
        mCrossing = crossing;
    }

    @Override
    public void translate(float dockPx, float keyboardPx) {
        mHost.translateStack(dockPx, keyboardPx);
    }

    @Override
    public void rideX(float px) {
        mHost.rideStatusX(px);
    }

    @Override
    public void cover(int px) {
        mHost.coverWindowInFront(px);
    }

    @Override
    public void preRoll(@NonNull PaneWallPage target) {
        mHost.preRollKeyboard(target);
    }

    /**
     * The extra keys folded by the crossing's share. A fold in lays the extra keys out on this
     * frame: the sink is re-read once the stack has laid them out, before the frame is drawn.
     */
    @Override
    public void fold() {
        listenForFold();
        applyFoldSink();
    }

    // ---- The dock's pinned row, which draws the morph (SuggestionBarView) ----

    @Override
    public void beginMorph(@NonNull DockLayout leaving, @NonNull DockLayout arriving) {
        SuggestionBarView row = mHost.dockRow();
        if (row != null) row.beginDockMorph(leaving, arriving);
    }

    @Override
    public void dress(@NonNull DockMorph morph) {
        SuggestionBarView row = mHost.dockRow();
        if (row == null || !morph.rowMorphs()) return;
        row.dockMorphFrame(morph.slotIconPx(), morph.appsRowUsableWidthPx(),
            morph.appsRowEdgeInsetPx(), morph.drawerTileAlpha());
        // The beads under the icons where this frame draws them.
        mHost.syncDockBeadsWithMorph();
        // And the icons' reflection: the morph moves them by drawing alone, which the
        // reflection, a recording of the row, does not hear of otherwise.
        mHost.onDockRowContentChanged();
    }

    @Override
    public void endMorph(@NonNull DockLayout landed) {
        SuggestionBarView row = mHost.dockRow();
        if (row == null) return;
        row.endDockMorph(landed);
        // The row draws the landed rest from here, exactly as its last frame did: so do the
        // beads, until the row is laid out in it.
        mHost.syncDockBeadsWithMorph();
    }

    void listenForFold() {
        if (mFoldListening) return;
        View rowStack = mHost.hotView(R.id.accessory_row_stack);
        if (rowStack == null) return;
        rowStack.addOnLayoutChangeListener(mFoldLayoutListener);
        mFoldListening = true;
    }

    /**
     * A fold's sink from the extra keys row as the stack lays it out now: the rows go down by
     * the part of that row the fold hides, cut at their own bottom, so the row shuts or opens
     * into its own bottom edge. Wherever the row stands in the stack, the rows between it and
     * the screen's edge are held where they are laid out (EdgeStackView.holdOutside,
     * SeamCrossing.foldHolds), and only the rows on the canvas's side of it go down, to where
     * they rest without it.
     *
     * <p>Where they rest is Home's own stack, which is not the window's less the extra keys
     * (#94): the window's stack keeps the terminal's flush padding, half of it under the rows,
     * and Home's A-Z row stands on the dock's rim and carries its chin there
     * (SeamCrossing.foldRestGrowthPx). The rows and the sheet travel those too
     * (SeamTravel.foldRowsSinkPx, foldSheetSinkPx), so at Home's end the stack stands exactly
     * as Home's settle lays it out and the settle moves nothing. Only while the stack lays the
     * window's rows out, the extra keys among them; Home's own rows are at their rest. Reads
     * view heights and a padding, no store.
     */
    void applyFoldSink() {
        Crossing crossing = mCrossing;
        if (crossing == null) return;
        float share = crossing.foldShare();
        if (Float.isNaN(share)) return;
        View rowStack = mHost.hotView(R.id.accessory_row_stack);
        View keys = mHost.hotView(R.id.terminal_toolbar_host);
        boolean keysInStack = rowStack != null && keys != null && keys.getParent() == rowStack
            && mHost.extraKeysRowOnScreen();
        if (!keysInStack) {
            sink(0f);
            return;
        }
        int keysPx = keys.getHeight();
        View stack = mHost.hotView(R.id.accessory_stack_container);
        int padUnderRows = stack != null ? Math.max(0, stack.getPaddingBottom()) : 0;
        int flush = Math.max(0, mHost.terminalFlushPaddingPx());
        SeamCrossing seam = crossing.seam();
        int growth = seam.foldRestGrowthPx();
        float cut = SeamTravel.foldSinkPx(keysPx, share);
        float rows = SeamTravel.foldRowsSinkPx(keysPx, share, padUnderRows, growth);
        float sheet = SeamTravel.foldSheetSinkPx(keysPx, share, flush, growth);
        float held = SeamTravel.foldHeldSinkPx(padUnderRows, share);
        // With rows held outside the extra keys, the keys row cuts itself (holdOutside) and
        // the stack is cut only where the held rows end; with none, the stack's cut is the
        // keys'.
        boolean holdsAz = seam.foldHolds(Element.AZ);
        boolean holdsRows = holdsAz || seam.foldHolds(Element.APPS);
        applyLayerSink(rows, holdsRows ? rows - held : cut, sheet, sheet - held, held,
            holdsAz);
        if (rowStack instanceof EdgeStackView) {
            ((EdgeStackView) rowStack).holdOutside(keys, rows - held, cut);
        }
    }

    /**
     * The dock's rows drawn {@code sinkPx} lower than they are laid out, each layer cut at its
     * own laid-out bottom, which is the keyboard's top: the rows go into the keyboard's edge
     * rather than across it, while the keyboard stays where its own travel puts it. Over the
     * seam only, with a keyboard in the stack; 0 puts every layer back.
     */
    @Override
    public void sink(float sinkPx) {
        applyLayerSink(sinkPx, sinkPx, sinkPx, sinkPx, 0f, false);
        // Every sink but a fold's moves the rows together: a fold's hold, if one ran, lets go.
        View rowStack = mHost.hotView(R.id.accessory_row_stack);
        if (rowStack instanceof EdgeStackView) {
            ((EdgeStackView) rowStack).holdOutside(null, 0f);
        }
    }

    /**
     * The dock's layers drawn lower than they are laid out, each cut at its own laid-out
     * bottom less a hidden part: the rows' layers by {@code rowsPx}, cut by {@code rowsCutPx};
     * the dock's sheet by {@code sheetPx}, cut by {@code sheetCutPx}; and, for a fold that
     * holds the A-Z row, that row's own layers by {@code heldPx}, uncut. A sink into the
     * keyboard moves them all alike; a fold's differ (applyFoldSink).
     */
    private void applyLayerSink(float rowsPx, float rowsCutPx, float sheetPx,
                                float sheetCutPx, float heldPx, boolean holdsAz) {
        if (rowsPx == mRowSinkPx && rowsCutPx == mRowCutPx
            && sheetPx == mSheetSinkPx && sheetCutPx == mSheetCutPx
            && heldPx == mHeldSinkPx && holdsAz == mSinkHoldsAz) return;
        mRowSinkPx = rowsPx;
        mRowCutPx = rowsCutPx;
        mSheetSinkPx = sheetPx;
        mSheetCutPx = sheetCutPx;
        mHeldSinkPx = heldPx;
        mSinkHoldsAz = holdsAz;
        for (int id : SEAM_ROW_LAYERS) {
            View layer = mHost.hotView(id);
            if (layer == null) continue;
            boolean sheet = id == R.id.accessory_surface_host;
            boolean held = holdsAz && isSeamAzLayer(id);
            float layerSink = held ? heldPx : sheet ? sheetPx : rowsPx;
            int hidden = held ? 0 : Math.round(sheet ? sheetCutPx : rowsCutPx);
            if (layer.getTranslationY() != layerSink) layer.setTranslationY(layerSink);
            if (hidden <= 0) {
                if (layer.getClipBounds() != null) layer.setClipBounds(null);
                continue;
            }
            // Generous above and to the sides, as the stack's own clip is: only the bottom is
            // cut.
            int width = Math.max(1, layer.getWidth());
            mTmpTravelClip.set(-width, -Math.max(layer.getHeight(),
                mContext.getResources().getDisplayMetrics().heightPixels), 2 * width,
                Math.max(0, layer.getHeight() - hidden));
            layer.setClipBounds(mTmpTravelClip);
        }
        // The dock's glass samples the wallpaper where it is drawn; a transform redraws nothing.
        View backdrop = mHost.hotView(R.id.accessory_blur_backdrop);
        if (backdrop != null && backdrop.getVisibility() == View.VISIBLE) backdrop.invalidate();
    }

    /**
     * Fades the status bar's content toward a minimal place's strip, and the bars minimal mode
     * takes off the sides and the top with the dock. Alpha only, on views whose alpha nothing
     * else writes; 1 puts them back.
     */
    @Override
    public void alpha(float statusAlpha, float sideAlpha) {
        // Most frames of most slides move between two places that look alike here; those cost
        // nothing past this comparison.
        if (statusAlpha == mStatusAlpha && sideAlpha == mSideAlpha) return;
        mStatusAlpha = statusAlpha;
        mSideAlpha = sideAlpha;
        setTravelAlpha(R.id.terminal_status_row, statusAlpha);
        setTravelAlpha(R.id.terminal_status_lens, statusAlpha);
        setTravelAlpha(R.id.place_apps_bar_host, sideAlpha);
        setTravelAlpha(R.id.place_extra_keys_host, sideAlpha);
        setTravelAlpha(R.id.place_az_bar_host, sideAlpha);
        setTravelAlpha(R.id.place_off_dock_plank_host, sideAlpha);
    }

    private void setTravelAlpha(int viewId, float alpha) {
        View view = mHost.hotView(viewId);
        if (view == null) return;
        float clamped = Math.max(0f, Math.min(1f, alpha));
        if (view.getAlpha() != clamped) view.setAlpha(clamped);
    }

    // ---- The committed half's stack, pictured for a whole-stack dip (StackPicture) ----

    @Override
    public boolean picture() {
        View stack = mHost.hotView(R.id.accessory_stack_container);
        StackPicture picture = mHost.stackPicture(stack);
        return picture != null && stack != null && picture.capture(stack);
    }

    @Override
    public void pictureAt(float reveal) {
        StackPicture picture = mHost.stackPicture(null);
        if (picture != null) picture.reveal(reveal);
    }

    @Override
    public void pictureSlide(float widths) {
        StackPicture picture = mHost.stackPicture(null);
        if (picture == null) return;
        int wall = mHost.wallWidthPx();
        int width = wall > 0 ? wall : picture.getWidth();
        picture.slide(widths * width);
    }

    @Override
    public void slide(float widths) {
        View stack = mHost.hotView(R.id.accessory_stack_container);
        if (stack == null) return;
        int wall = mHost.wallWidthPx();
        int width = wall > 0 ? wall : stack.getWidth();
        float x = widths * width;
        if (stack.getTranslationX() == x) return;
        stack.setTranslationX(x);
        // Its glass samples the wallpaper where it is drawn; a transform alone redraws nothing.
        View backdrop = mHost.hotView(R.id.accessory_blur_backdrop);
        if (backdrop != null && backdrop.getVisibility() == View.VISIBLE) backdrop.invalidate();
    }

    @Override
    public void dropPicture() {
        StackPicture picture = mHost.stackPicture(null);
        if (picture == null) return;
        // Back at its rest the picture is the stack, under it: it goes once a keyboard
        // brought back up has drawn. Sunk, it shows nothing and goes now.
        if (picture.atRest()) picture.releaseAfter(PICTURE_HANDOVER_MS);
        else picture.release();
    }

    // ---- The terminal's page laid out at its landing size ahead of the settle ----

    @Override
    public void preSize(int px) {
        mHost.applyTerminalPreSize(px);
    }
}
