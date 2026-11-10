package com.termux.app.dock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.AzScrubRowView;
import com.termux.app.DockEdgeGlowView;
import com.termux.app.DockPlankController;
import com.termux.app.DockShelfView;
import com.termux.app.chrome.GlassRefraction;
import com.termux.app.DockSurfaceHostLayout;
import com.termux.R;
import com.termux.app.SuggestionBarView;
import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.ResolvedArrangement;
import com.termux.app.place.SeamCrossing;
import com.termux.app.place.layout.IconSize;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The dock's events and state (ADR 0018 §1): the Lens beads under the row's icons and the A–Z
 * row's letter beads, worked out from the views as laid out and handed to the dock's glass host
 * ({@link DockSurfaceHostLayout}) and its shelf ({@link DockShelfView}). It reaches the activity
 * only through {@link Host}.
 */
public final class DockController {

    /** What only the activity gives the dock. */
    public interface Host {
        // -------------------------------------------------------------- window and views

        /** The window's view of {@code id}, looked up afresh; null while it has none. */
        @Nullable <T extends View> T findViewById(int id);
        /** The window's view of {@code id}, found once per tree; null while it has none. */
        @Nullable <T extends View> T hotView(int id);
        /** The activity, as the Context the dock's touch light is made with. */
        @NonNull Context context();
        /** The window's decor view, which the touch light's fade steps on. */
        @NonNull View decorView();
        /** The display's density. */
        float density();
        /** {@code dp} in px at the display's density. */
        float dpToPx(float dp);
        /** {@code color} with its alpha replaced by {@code alpha} (0..255, clamped). */
        int withAlphaComponent(int color, int alpha);
        /** Whether {@code view} is shown and holds the screen point {@code x}, {@code y}. */
        boolean viewContainsScreenPoint(@Nullable View view, float x, float y);
        /** The dock's pinned row; null before it is inflated. */
        @Nullable SuggestionBarView dockRow();
        /** The Search field when it stands shown in the row's band under the icons; else null. */
        @Nullable PromptLineFieldView promptFieldInRowBand();

        // -------------------------------------------------------------- preferences and motion

        /** Whether the preferences are read yet. */
        boolean hasPreferences();
        /** Whether reduced motion is on. */
        boolean isReducedMotionEnabled();

        // -------------------------------------------------------------- keyboard and drawer

        /** Whether the in-app keyboard is shown. */
        boolean isInAppKeyboardShown();
        /** Whether the in-app keyboard is up and shares the dock's glass. */
        boolean keyboardSharesDockGlass();
        /** Whether the app drawer has taken the gesture (its open drag starts on the dock). */
        boolean isAppDrawerEngaged();

        // -------------------------------------------------------------- layout and arrangement

        /** The place's layout as arranged now. */
        @NonNull PlaceLayout placeLayout();
        /** The dock's rows, which say whether the shelf is shown. */
        @NonNull DockRows dockRows();
        /** Whether the arrangement's Style is Floating (the panes', the bar's and the keys' answer). */
        boolean floatingStyle();
        /** Whether the Peek glass card can stand now (read from none of what the placement moves). */
        boolean peekCardWanted();
        /** The Peek glass card as last cut, in the host's coordinates; null while none stands. */
        @Nullable PeekGlassGeometry.Card peekCard();
        /** Corners, the one Floating radius, px. */
        float layoutCornersPx();
        /** The page id whose look the stack's surfaces wear. */
        int stackLookPage();
        /** Whether the stack's sheets stand down on the joined frame's glass. */
        boolean stackStandsDownOnFrame();
        /** The display's metrics. */
        @NonNull DisplayMetrics displayMetrics();
        /** The orientation the place is arranged for now. */
        @NonNull PlaceOrientation currentPlaceOrientation();
        /** The display cutouts the content root pads, px, as last applied. */
        int cutoutInsetLeft();
        int cutoutInsetRight();
        /** The terminal toolbar's default height, px; 0 before it is measured. */
        float terminalToolbarDefaultHeight();
        /** The pinned slots the width rule fits where the preference is unset. */
        int calculateSuggestionBarMaxButtons(@NonNull DisplayMetrics metrics);
        /** The Home grid's margin, dp, the dock's side insets follow. */
        int homeMarginDp();

        // -------------------------------------------------------------- layout store and pages

        /** The preferences; null before they are read. */
        @Nullable TermuxAppSharedPreferences preferences();
        /** The layout store; null before the preferences are read. */
        @Nullable PlaceLayoutStore placeLayoutStore();
        /** {@code page}'s resolved arrangement: its layout, Style, Dock design and toggles. */
        @NonNull ResolvedArrangement arrangementOf(@NonNull PageRef page);
        @NonNull ResolvedArrangement arrangementOf(@NonNull PaneWallPage page);
        /** The page {@code page} stands for in the arrangement (Home's page now, or the Terminal). */
        @NonNull PageRef arrangedPageOf(@NonNull PaneWallPage page);
        /** The layout {@code page} wears. */
        @NonNull LayoutVariant layoutOf(@NonNull PaneWallPage page);
        /** The arrangement store's revision. */
        int arrangementRevision();
        /** The icon size as it draws, capped by what the pinned apps fit at. */
        @NonNull IconSize drawnIconSize(@NonNull PlaceLayoutStore store);
        /** The page the chrome is committed to. */
        @NonNull PaneWallPage lastWallPage();
        /** The half whose rows the stack stands now. */
        @NonNull PaneWallPage stackDressPage();
        /** Whether the committed page's dock carries the Search field. */
        boolean committedSearchField();
        /** Whether the slab carries the field at rest. */
        boolean promptFieldWanted();
        /** How much of the Search field, and of the Wall strip's row, the dock is laid out with. */
        float promptFieldReveal();
        float wallStripReveal();
        /** The field's, and the Wall strip row's, share as last laid out. */
        float appliedPromptFieldReveal();
        float appliedWallStripReveal();
        /** The page whose half's rows the Drawer tile was last laid out for; null before one. */
        @Nullable PaneWallPage drawerTileRowsPage();
        /** Whether the status bar is compact, and the edge it stands on. */
        boolean isStatusBarCompact();
        @NonNull PlaceLayout.Edge statusBarEdge();
        /** The count of half re-dresses that ran. */
        int halfRedressCount();
        /** The A–Z row and the pull tab's letters; null before they are made. */
        @Nullable AzScrubRowView azScrubRowView();
        @Nullable AzScrubRowView azTabRowView();
        /** The edge the A–Z bar stands on. */
        @NonNull PlaceLayout.Edge azBarEdge();
        /** Whether the in-app keyboard is made and visible. */
        boolean isInAppKeyboardVisible();

        // -------------------------------------------------------------- the chrome's shape model

        /** The shape model of the place on screen, and of {@code layout}. */
        @NonNull com.termux.app.place.ChromeShape chromeShape();
        @NonNull com.termux.app.place.ChromeShape chromeShape(@NonNull PlaceLayout layout);
        /**
         * The model's pieces of the dock's own rows, the keyboard's with them where the two share
         * one glass ({@code withKeyboard}).
         */
        @NonNull java.util.List<com.termux.app.place.ChromeShape.PieceId> dockPieceIds(
            boolean withKeyboard);
        /** The model's pieces for these elements, in order. */
        @NonNull java.util.List<com.termux.app.place.ChromeShape.PieceId> chromePieceIds(
            @NonNull java.util.List<com.termux.app.place.Element> elements);
        /** The edges of these pieces that wear the rim (ChromeEdgeRule). */
        int chromeRimEdges(@NonNull com.termux.app.place.ChromeShape.PieceId... ids);
        /** Whether the dock and the keyboard are one glass now (the chrome as it stands). */
        boolean unifiedKeyboardGlass();
        /** Whether the joined Docked frame's glass is the sheets' now. */
        boolean frameStandsDown();

        // -------------------------------------------------------------- sheets off the dock

        /** The A–Z bar's thickness, and the air a bar off the dock keeps from the screen, px. */
        int azBarThicknessPx();
        int azBarHostMarginPx();
        /** Docked with the strip behind the status bar showing: whether it leads with these. */
        boolean dockedStatusStripLeadsWith(@NonNull PlaceLayout layout,
            @NonNull java.util.List<com.termux.app.place.Element> elements);
        /** Whether {@code sheet} runs on into the strip behind the status bar. */
        boolean continuesIntoStatusInsetStrip(@Nullable View sheet);
        /** Where {@code sheet}'s slice of the strip's light model starts, 0..1. */
        float statusInsetSheetSliceStart(@Nullable View sheet);
        /** {@code view}'s padding set; whether it moved. */
        boolean updateViewPadding(@Nullable View view, int left, int top, int right, int bottom);

        // -------------------------------------------------------------- glass

        /** The chrome's renderer; null before it is made. */
        @Nullable ChromeRenderer chrome();
        /** The dock's effect rebuilt once, as a key lens's frame does. */
        void rebuildBackdropGlassEffectForLens();
        /** Whether the glass effects' parameters are valid. */
        boolean glassParamsValid();
        /** The dock's glass program; null while none is up. */
        @Nullable GlassRefraction.Program dockGlassProgram();
        /** Whether a key's lens holds the dock's glass now. */
        boolean keyLensActive();
        /** The glass origin's offset, so the dock host's coordinates map to the effect's. */
        float glassOriginLeft();
        float glassOriginTop();
        /** Whether any glass touch light may run (the Fancier look on, no lazy mode or saver). */
        boolean isGlassTouchLightAllowed();
        /** The accent the dock's specular and rim glow are tinted with. */
        int resolveDockAccentColor();
        /** {@code glass}, or the deck's slab where the deck stands in for it. */
        @Nullable Drawable deckSlabOr(@Nullable Drawable glass);

        // -------------------------------------------------------------- crossing

        /** The crossing's bead share: NaN while nothing melts across the seam. */
        float beadShare();
        /** The crossing's seam, which answers the committed and travelling designs mid-slide. */
        @NonNull SeamCrossing seam();
        /** The page a pictured dip dressed the slab for on its first frame; null otherwise. */
        @Nullable PaneWallPage dockDesignTravelledTo();
        void setDockDesignTravelledTo(@Nullable PaneWallPage page);

        // -------------------------------------------------------------- callbacks into the activity

        /** The row moved under the joined frame's band cut: the cut follows it. */
        void syncFrameBeadCut();
        /**
         * The beads were replaced: the slab's shape cut again on {@code host} where beads came or
         * went ({@code modeChanged}), the Search field's well, then the frame's cut.
         */
        void onDockBeadsReplaced(@Nullable View host, boolean modeChanged);
        /** The dock row's Drawer, palette and Float tiles laid out for the rows shown. */
        void syncDrawerTile();
        /** The apps bar's height pass; whether anything moved. */
        boolean updateAppLauncherBarHeight();
        /** The dock and geometry pass; whether the dock moved. */
        boolean setTerminalToolbarHeight(boolean requestTerminalResize);
        /** The Wall strip's row takes the Workspace's windows. */
        void syncWallStripFeed();
        /** {@code viewId}'s bottom margin set; whether it moved. */
        boolean updateViewBottomMargin(int viewId, int marginBottom);
        /** The dock rows' side insets for {@code layout}; whether they moved. */
        boolean applyDockRowHorizontalInsets(@NonNull DockLayout layout);
        /** The Search field, and the Wall strip's row, laid out as {@code layout} has them. */
        boolean syncPromptLineField(@NonNull DockLayout layout);
        boolean syncWallStripRow(@NonNull DockLayout layout);
        /**
         * Peek glass on the dock's host: the card worked out again from the row and the host as
         * laid out, the glass program re-aimed when it moved, the host dressed as the card where
         * one stands, and the reflection; whether a card stands (the host is then dressed).
         */
        boolean dressPeekCard(@NonNull View surface);
        /** Every sheet off the dock glazed again against {@code layout} (the plank's among them). */
        void refreshOffDockGlass(@NonNull PlaceLayout layout);
        /** The content root asked to apply its insets again. */
        void requestRootInsets();
    }

    @NonNull private final Host mHost;
    /** The dock's layout and dress (the third cluster), behind this. */
    @NonNull private final DockDress mDress;
    /** The dock's surface: placement, shape, glass and shelf (the fourth cluster), behind this. */
    @NonNull private final DockSurface mSurface;

    public DockController(@NonNull Host host) {
        mHost = host;
        mDress = new DockDress(host, this);
        mSurface = new DockSurface(host, this);
    }

    // ------------------------------------------------------------------ Surface
    //
    // The activity's one entry to DockSurface: whether the dock floats, the capsule's radius, the
    // shape its glass host is cut to, the style's glass on the dock's program, the shelf, and
    // whether the program draws the dock's edge.

    /** Whether the dock is placed as the Floating card (DockPlacement). */
    public boolean dockFloats() {
        return mSurface.dockFloats();
    }

    /** {@link #dockFloats} for the dock measured in {@code floatingStyle}. */
    public boolean dockFloatsIn(boolean floatingStyle) {
        return mSurface.dockFloatsIn(floatingStyle);
    }

    /** The Floating card's radius for a surface this tall, Corners capped at half of it. */
    public float resolveDockCapsuleCornerRadiusPx(int surfaceHeightPx) {
        return mSurface.resolveDockCapsuleCornerRadiusPx(surfaceHeightPx);
    }

    /** What the dock material's tint covers of the frost under it, 0..1. */
    public float dockGlassCover() {
        return mSurface.dockGlassCover();
    }

    /** Whether the host's shape was cut once, and as what; see {@link #applyDockSurfaceShape}. */
    public boolean dockShapeCut() {
        return mSurface.dockShapeCut();
    }

    public boolean dockShapeCapsule() {
        return mSurface.dockShapeCapsule();
    }

    public int dockShapeHeightPx() {
        return mSurface.dockShapeHeightPx();
    }

    /** Cuts {@code surface} (the dock's host or the strip under the pill) to the dock's shape. */
    public void applyDockSurfaceShape(@NonNull View surface, boolean capsule, int surfaceHeightPx,
                                      boolean ownsInnerEdge, boolean innerRim) {
        mSurface.applyDockSurfaceShape(surface, capsule, surfaceHeightPx, ownsInnerEdge, innerRim);
    }

    /** Whether the dock's program draws its edge now (its effect installed). */
    public boolean dockGlassDrawsEdge() {
        return mSurface.dockGlassDrawsEdge();
    }

    /** Records whether the dock's program draws its edge; a change re-cuts the host's shape. */
    public void setDockProgramEdge(boolean drawsEdge) {
        mSurface.setDockProgramEdge(drawsEdge);
    }

    /** The slab's own spec for the glass program and the shelf. */
    @NonNull
    public DockSurfaceSpec dockSlabSpec() {
        return mSurface.dockSlabSpec();
    }

    /** The dock's style's own glass on its program (GlassEffects.Host). */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void applyDockSurfaceGlass(@NonNull GlassRefraction.Program program) {
        mSurface.applyDockSurfaceGlass(program);
    }

    /** The shelf under a floating slab, shown or put away for {@code state}. */
    public void syncDockShelf(@Nullable com.termux.app.chrome.ChromeSpec state) {
        mSurface.syncDockShelf(state);
    }

    /** The edge a pinned-apps row lies down on away from the dock; null where none does. */
    @Nullable
    public PlaceLayout.Edge offDockPlankEdge(@NonNull PlaceLayout layout) {
        return mSurface.offDockPlankEdge(layout);
    }

    /** The bars sharing the off-dock plank on {@code edge}, outermost first. */
    @NonNull
    public java.util.List<com.termux.app.place.Element> offDockPlankElements(
            @NonNull PlaceLayout layout, @Nullable PlaceLayout.Edge edge) {
        return mSurface.offDockPlankElements(layout, edge);
    }

    /** The air the plank keeps outside its sheet of glass. */
    public int offDockPlankAirPx(@NonNull java.util.List<com.termux.app.place.Element> onPlank) {
        return mSurface.offDockPlankAirPx(onPlank);
    }

    /** The plank's glass thickness as last sized. */
    public int offDockPlankGlassHeightPx() {
        return mSurface.offDockPlankGlassHeightPx();
    }

    /** The off-dock plank shown, sized or put away for {@code layout}; whether it changed. */
    public boolean syncOffDockPlank(@NonNull PlaceLayout layout) {
        return mSurface.syncOffDockPlank(layout);
    }

    /** The plank's glass glazed again, while the plank is up. */
    public void refreshOffDockPlankGlass(@NonNull PlaceLayout layout) {
        mSurface.refreshOffDockPlankGlass(layout);
    }

    /** The dock's own glass behind a sheet that is not on the dock (the plank, the A–Z bar). */
    public void applyOffDockPlankGlass(int glassId, int frostId, int surfaceId,
            @NonNull com.termux.app.chrome.SurfaceDirtyLedger.FrostRect frostKey,
            @NonNull com.termux.app.chrome.ChromeShapeOutlineProvider outline,
            @NonNull PlaceLayout.Edge edge, int heightPx,
            @NonNull java.util.List<com.termux.app.place.ChromeShape.PieceId> pieces,
            @NonNull PlaceLayout layout) {
        mSurface.applyOffDockPlankGlass(glassId, frostId, surfaceId, frostKey, outline, edge,
            heightPx, pieces, layout);
    }

    /** The row's own tiles' glyphs on the dock's face at {@code dockOpacity}. */
    public void syncDockTilesInk(float dockOpacity) {
        mSurface.syncDockTilesInk(dockOpacity);
    }

    // ------------------------------------------------------------------ Layout and dress
    //
    // The activity's one entry to DockDress: Dock geometry's snapshot and halves, the applied
    // layout and its cache, the Dock design each page wears and the dress a settle or a picked
    // design puts on the slab, and the band ease.

    /** Dock geometry, which the activity's stack layout measures the bottom stack with. */
    @NonNull
    public DockGeometry dockGeometry() {
        return mDress.dockGeometry();
    }

    /** The squeeze the last geometry pass found: an input to every snapshot until the next. */
    public void setDockSqueezePx(int squeezePx) {
        mDress.setDockSqueezePx(squeezePx);
    }

    /** One snapshot of both halves for the dock's geometry, rows standing for {@code rowsPage}. */
    @NonNull
    public DockGeometry.Snapshot dockSnapshot(@NonNull PlaceLayout arrangement,
                                              @NonNull PaneWallPage rowsPage) {
        return mDress.dockSnapshot(arrangement, rowsPage);
    }

    /** {@code page}'s half, memoised while its facts stand. */
    @NonNull
    public DockGeometry.Half dockHalf(@NonNull PaneWallPage page,
                                      @NonNull PlaceOrientation orientation,
                                      @NonNull PlaceLayout fallbackLayout) {
        return mDress.dockHalf(page, orientation, fallbackLayout);
    }

    @NonNull
    public DockGeometry.Half dockHalf(@NonNull PageRef page,
                                      @NonNull PlaceOrientation orientation,
                                      @NonNull PlaceLayout fallbackLayout) {
        return mDress.dockHalf(page, orientation, fallbackLayout);
    }

    /** The pinned slots a page of the row holds: the preference, or the width rule when unset. */
    public int dockSlotCount(@NonNull DisplayMetrics metrics) {
        return mDress.dockSlotCount(metrics);
    }

    /** The design the dock's rows on screen wear (DockRows#wornSurface). */
    @NonNull
    public DockSurfaceSpec shownDockSurfaceSpec() {
        return mDress.shownDockSurfaceSpec();
    }

    /** The Dock design {@code page}'s slab wears (WornDockDesign). */
    @NonNull
    public DockStyle wornDockDesign(@NonNull PaneWallPage page) {
        return mDress.wornDockDesign(page);
    }

    /** The committed page's Dock design, or the travelling one mid-slide. */
    @NonNull
    public DockStyle committedDockDesign() {
        return mDress.committedDockDesign();
    }

    /** Whether the slab is already dressed for {@code design} and {@code searchField}. */
    public boolean slabWears(@NonNull DockStyle design, boolean searchField) {
        return mDress.slabWears(design, searchField);
    }

    /** Lays the dock out and dresses its glass again for the spec it wears now. */
    public void restyleDockSurface() {
        mDress.restyleDockSurface();
    }

    /** A settle from {@code from} to {@code to} that changes the dock's dress re-dresses it. */
    public void restyleDockSurfaceForPageIfChanged(@NonNull PaneWallPage from,
                                                   @NonNull PaneWallPage to) {
        mDress.restyleDockSurfaceForPageIfChanged(from, to);
    }

    /** The landing's commit notes (CommitDockDress), opened and closed around its dress. */
    @NonNull
    public CommitDockDress commitDockDress() {
        return mDress.commitDockDress();
    }

    /** A Dock design was picked: the slab eases to its new height; whether an ease started. */
    public boolean beginDockBandEase() {
        return mDress.beginDockBandEase();
    }

    /** Whether an ease of the slab's bands runs now. */
    public boolean dockBandEaseRunning() {
        return mDress.dockBandEaseRunning();
    }

    /** Where the ease stands for the Search field, and for the Wall strip's row, 0..1. */
    public float promptFieldEaseValue() {
        return mDress.promptFieldEaseValue();
    }

    public float wallStripEaseValue() {
        return mDress.wallStripEaseValue();
    }

    /** How much of a band only {@code design} carries the dock is laid out with. */
    public float dockBandShare(@NonNull DockStyle design) {
        return mDress.dockBandShare(design);
    }

    /** The store pinned to the layout the committed page's dock is dressed by. */
    @Nullable
    public PlaceLayoutStore committedDockStore() {
        return mDress.committedDockStore();
    }

    /** The store pinned to the layout {@code page}'s dock is dressed by. */
    @Nullable
    public PlaceLayoutStore dockStoreOf(@NonNull PaneWallPage page) {
        return mDress.dockStoreOf(page);
    }

    /** Dock geometry's answer for the arrangement on screen, worked out now. */
    @NonNull
    public DockLayout getDockLayout() {
        return mDress.getDockLayout();
    }

    /** The dock the last geometry pass applied (ADR 0011), the one every writer reads. */
    @NonNull
    public DockLayout appliedDockLayout() {
        return mDress.appliedDockLayout();
    }

    /** The applied dock when the last pass measured it in {@code arrangement}, else worked out. */
    @NonNull
    public DockLayout dockLayoutIn(@NonNull PlaceLayout arrangement) {
        return mDress.dockLayoutIn(arrangement);
    }

    /** The dock's geometry for an arrangement the caller is applying. */
    @NonNull
    public DockLayout dockLayoutFor(@NonNull PlaceLayout layout) {
        return mDress.dockLayoutFor(layout);
    }

    /** True when this layout moved the dock. */
    public boolean applyDockLayout(@NonNull DockLayout layout) {
        return mDress.applyDockLayout(layout);
    }

    /** True when this layout, measured in {@code arrangement}, moved the dock. */
    public boolean applyDockLayout(@NonNull DockLayout layout, @Nullable PlaceLayout arrangement) {
        return mDress.applyDockLayout(layout, arrangement);
    }

    /** What the dock shows changed (Dock rows' listener). */
    public void onDockRowsChanged(@NonNull DockRows.Shown shown) {
        mDress.onDockRowsChanged(shown);
    }

    /** The beads as last drawn, in the host's coordinates; null while the dock is a slab. */
    @Nullable
    public LensBeadGeometry.Beads beads() {
        return mDockBeads;
    }

    /** The letter beads in the host's coordinates, or null where the row has none. */
    @Nullable
    public AzBeadRow azBeads() {
        return mDockAzBeads;
    }

    /** The letter beads' lift as last drawn: the finger's x in the host, NaN for none. */
    public float azLiftX() {
        return mDockAzLiftX;
    }

    /** The letter beads' lift as last drawn: its peak, px. */
    public float azLiftPeakPx() {
        return mDockAzLiftPeakPx;
    }

    // ------------------------------------------------------------------ Lens beads
    //
    // Lens beads (DockSurfaceSpec.LENS_BEADS), on whichever half's dock wears the design (#93;
    // under the Workspace's extra keys or a keyboard sharing the glass the row keeps the slab,
    // LensBeadGeometry.rowWearsBeads): the dock's one glass host draws a round
    // bead under each icon instead of its slab. The host clips everything in it to the beads
    // (DockSurfaceHostLayout), the dock's one glass program draws every bead's rim, shelf and
    // reflection from the beads' centres (GlassRefraction.Program#setBeads), and the shelf view
    // draws each bead's shadow. The beads are worked out again only when the row or the host
    // lays out, before the frame that shows the new layout draws; at rest nothing runs.

    /** The beads as last drawn, in the host's coordinates; null while the dock is a slab. */
    @Nullable private com.termux.app.dock.LensBeadGeometry.Beads mDockBeads;
    /**
     * The beads where a page swipe draws the icons now ({@link #onDockIconsMoved}), in the
     * host's coordinates; null at rest, when the beads are {@link #mDockBeads}.
     */
    @Nullable private com.termux.app.dock.LensBeadGeometry.Beads mDockBeadsMoving;
    /** Where the row stands in the host sideways, and the span it draws its icons in there. */
    private float mDockBeadsRowDx;
    /** Where it stands down the host, and the bottom the beads are held above; NaN unread. */
    private float mDockBeadsRowDy = Float.NaN;
    private float mDockBeadsBottomPx = Float.NaN;
    private float mDockBeadsSpanLeft = Float.NaN, mDockBeadsSpanRight = Float.NaN;
    /** Scratch for the row's still tiles (Drawer, palette), whose beads a swipe holds still. */
    private final float[] mDockBeadStills = new float[2];
    /** Scratch for the neighbouring page's icons a swipe stages. */
    private final float[] mDockBeadNeighbours =
        new float[com.termux.app.dock.LensBeadGeometry.MAX_DRAWN_BEADS];

    /**
     * Whether the dock draws beads now (the row still has to wear them, {@link #resolveDockBeads}):
     * under Lens beads, but not where a slide has carried the beaded half wholly off; and across
     * the seam from a half without them while any of the beaded half's share shows, so they come
     * in as the page does.
     */
    public boolean dockBeadsWanted() {
        if (!mHost.hasPreferences()) return false;
        float share = mHost.beadShare();
        if (shownDockSurfaceSpec().beads) return Float.isNaN(share) || share > 0f;
        return !Float.isNaN(share) && share > 0f;
    }

    /** The beads where the row draws its icons now: moving with a swipe, or at rest. */
    @Nullable
    public com.termux.app.dock.LensBeadGeometry.Beads dockBeadsShadowed() {
        return mDockBeads != null && mDockBeadsMoving != null ? mDockBeadsMoving : mDockBeads;
    }

    /**
     * The beads as the dock's glass draws them now: where the row draws its icons, swollen by a
     * melt across the seam toward the slab they cover at its far end. The shadows stay the
     * beads' own ({@link #dockBeadsShadowed}); the swelling glass covers them.
     */
    @Nullable
    public com.termux.app.dock.LensBeadGeometry.Beads dockBeadsDrawn() {
        com.termux.app.dock.LensBeadGeometry.Beads beads = dockBeadsShadowed();
        float share = mHost.beadShare();
        if (beads == null || Float.isNaN(share) || share >= 1f) return beads;
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return beads;
        float cover = com.termux.app.dock.LensBeadGeometry.coverRadiusPx(beads, host.getWidth(),
            host.getHeight());
        return beads.withRadius(com.termux.app.dock.LensBeadGeometry.meltRadiusPx(
            beads.radiusPx, cover, share));
    }

    /** How much of the beads' shadows and glow show: all at rest, none once they cover the slab. */
    public float dockBeadShelfAlpha() {
        return com.termux.app.dock.LensBeadGeometry.beadShelfAlpha(mHost.beadShare());
    }

    /** The slab clip the host cuts melting beads to: the slab's corners; NaN at rest. */
    private float dockBeadSlabClipPx() {
        float share = mHost.beadShare();
        if (mDockBeads == null || Float.isNaN(share) || share >= 1f) return Float.NaN;
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (host == null) return Float.NaN;
        return dockFloats() ? resolveDockCapsuleCornerRadiusPx(host.getHeight()) : 0f;
    }


    /**
     * The beads drawn again for the crossing's bead share (Crossing#beadShare): the host's clip and slab cut, and the
     * dock's effect once. Beads coming or going (the beaded half's share reaching or leaving
     * nothing) dress
     * the dock again, as a row turning to beads or back does. Mid-slide the row is read again
     * only when beads are to come or go ({@code settled}: always, the slide having landed).
     */
    public void applyDockBeadMelt(boolean settled) {
        boolean wereBeads = mDockBeads != null;
        boolean modeChanged = (settled || dockBeadsWanted() != wereBeads)
            && refreshDockBeads() && wereBeads != (mDockBeads != null);
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (host instanceof DockSurfaceHostLayout) {
            DockSurfaceHostLayout beadHost = (DockSurfaceHostLayout) host;
            beadHost.setBeads(dockBeadsDrawn());
            beadHost.setSlabClip(dockBeadSlabClipPx());
        }
        DockShelfView shelf = mHost.hotView(R.id.accessory_shelf_fx);
        if (shelf != null) {
            if (mHost.dockRows().shelfShown()) shelf.setBeads(dockBeadsShadowed());
            shelf.setBeadShelfAlpha(dockBeadShelfAlpha());
        }
        if (modeChanged) {
            mHost.chrome().requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME
                | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
        } else if (mDockBeads != null) {
            mHost.rebuildBackdropGlassEffectForLens();
        }
    }

    /**
     * The row's icons moved off or back onto their places (a page swipe, a page switch): the
     * beads ride under them, from the touch or animation callback that moved them, so a bead is
     * under its icon on the very frame that draws it. Each frame of a swipe re-cuts the host's
     * clip, moves the shadows (their paints are kept) and rebuilds the dock's effect once, a
     * single pass of the glass program over the pre-blurred frost (the dock's chain has no blur
     * of its own); at rest and under the Glass tray nothing runs past the first check.
     */
    public void onDockIconsMoved() {
        SuggestionBarView row = mHost.dockRow();
        com.termux.app.dock.LensBeadGeometry.Beads rest = mDockBeads;
        com.termux.app.dock.LensBeadGeometry.Beads moving = null;
        if (row != null && rest != null && row.iconsOffTheirPlaces()) {
            int neighbours = row.drawnNeighbourIconCentersX(mDockBeadNeighbours);
            for (int i = 0; i < neighbours; i++) mDockBeadNeighbours[i] += mDockBeadsRowDx;
            float left = Float.isNaN(mDockBeadsSpanLeft) ? 0f : mDockBeadsSpanLeft;
            float right = Float.isNaN(mDockBeadsSpanRight) ? Float.MAX_VALUE : mDockBeadsSpanRight;
            // The Drawer tile (#97) and the palette tile are no page's: their beads stand still
            // under them.
            int stills = row.stillTileCentersX(mDockBeadStills);
            for (int i = 0; i < stills; i++) mDockBeadStills[i] += mDockBeadsRowDx;
            moving = com.termux.app.dock.LensBeadGeometry.follow(rest, row.drawnPageOffsetPx(),
                mDockBeadStills, stills, mDockBeadNeighbours, neighbours, left, right,
                row.drawnPageFade());
        }
        if (moving == null && mDockBeadsMoving == null) return;
        if (moving != null && moving.equals(mDockBeadsMoving)) return;
        mDockBeadsMoving = moving;
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (host instanceof DockSurfaceHostLayout) {
            DockSurfaceHostLayout beadHost = (DockSurfaceHostLayout) host;
            beadHost.setBeadSpan(moving != null ? mDockBeadsSpanLeft : Float.NaN,
                moving != null ? mDockBeadsSpanRight : Float.NaN);
            beadHost.setBeads(dockBeadsDrawn());
        }
        if (moving == null) {
            // Back at rest: the page the row settled on may hold other icons than the beads did.
            boolean wereBeads = mDockBeads != null;
            refreshDockBeads();
            if (host instanceof DockSurfaceHostLayout)
                ((DockSurfaceHostLayout) host).setBeads(mDockBeads);
            if (wereBeads != (mDockBeads != null) && mHost.chrome() != null)
                mHost.chrome().requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME
                    | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
        }
        DockShelfView shelf = mHost.hotView(R.id.accessory_shelf_fx);
        if (shelf != null && mHost.dockRows().shelfShown()) shelf.setBeads(dockBeadsShadowed());
        mHost.rebuildBackdropGlassEffectForLens();
    }
    /** The row's icon boxes, read into this; one icon more than the beads hold says "too many". */
    private final float[] mDockBeadBoxes =
        new float[(com.termux.app.dock.LensBeadGeometry.MAX_BEADS + 1) * 4];
    /** The views whose layout moves the beads, while the dock wears them. */
    @Nullable private View mDockBeadsWatchedHost;
    @Nullable private View mDockBeadsWatchedRow;
    private boolean mDockBeadsRefreshPending;
    private final View.OnLayoutChangeListener mDockBeadsLayoutListener =
        (v, l, t, r, b, ol, ot, or, ob) -> scheduleDockBeadsRefresh();
    private final ViewTreeObserver.OnPreDrawListener mDockBeadsPreDraw =
        new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                View host = mDockBeadsWatchedHost;
                if (host != null) host.getViewTreeObserver().removeOnPreDrawListener(this);
                mDockBeadsRefreshPending = false;
                onDockBeadsLayoutSettled();
                return true;
            }
        };

    /**
     * Watches the host and the row for layout while the dock's style draws beads, and lets go
     * of them otherwise, so the Glass tray and the Workspace's dock carry no listener of this.
     */
    public void syncDockBeadsListeners() {
        boolean beads = dockBeadsWanted();
        View host = beads ? mHost.findViewById(R.id.accessory_surface_host) : null;
        View row = beads ? mHost.dockRow() : null;
        if (mDockBeadsWatchedHost != host) {
            if (mDockBeadsWatchedHost != null) {
                mDockBeadsWatchedHost.removeOnLayoutChangeListener(mDockBeadsLayoutListener);
                if (mDockBeadsRefreshPending)
                    mDockBeadsWatchedHost.getViewTreeObserver().removeOnPreDrawListener(mDockBeadsPreDraw);
                mDockBeadsRefreshPending = false;
            }
            mDockBeadsWatchedHost = host;
            if (host != null) host.addOnLayoutChangeListener(mDockBeadsLayoutListener);
        }
        if (mDockBeadsWatchedRow != row) {
            if (mDockBeadsWatchedRow != null)
                mDockBeadsWatchedRow.removeOnLayoutChangeListener(mDockBeadsLayoutListener);
            mDockBeadsWatchedRow = row;
            if (row != null) row.addOnLayoutChangeListener(mDockBeadsLayoutListener);
        }
        // The A–Z row's letter beads follow its layout too.
        View azRow = beads ? mHost.findViewById(R.id.apps_bar_az_row) : null;
        if (mDockBeadsWatchedAzRow != azRow) {
            if (mDockBeadsWatchedAzRow != null)
                mDockBeadsWatchedAzRow.removeOnLayoutChangeListener(mDockBeadsLayoutListener);
            mDockBeadsWatchedAzRow = azRow;
            if (azRow != null) azRow.addOnLayoutChangeListener(mDockBeadsLayoutListener);
        }
    }

    @Nullable private View mDockBeadsWatchedAzRow;

    /** A layout moved the host or the row: the beads are read again before the next draw. */
    private void scheduleDockBeadsRefresh() {
        View host = mDockBeadsWatchedHost;
        if (mDockBeadsRefreshPending || host == null) return;
        mDockBeadsRefreshPending = true;
        host.getViewTreeObserver().addOnPreDrawListener(mDockBeadsPreDraw);
    }

    /**
     * After a layout: beads that moved re-aim the glass program (one rebuild of the dock's
     * effect) and the shadows; a row that turned to beads or back to the slab dresses the dock
     * again.
     */
    private void onDockBeadsLayoutSettled() {
        boolean wereBeads = mDockBeads != null;
        if (!refreshDockBeads()) return;
        mHost.rebuildBackdropGlassEffectForLens();
        DockShelfView shelf = mHost.findViewById(R.id.accessory_shelf_fx);
        if (shelf != null && mHost.dockRows().shelfShown()) shelf.setBeads(dockBeadsShadowed());
        if (wereBeads != (mDockBeads != null) && mHost.chrome() != null)
            mHost.chrome().requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME
                | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
    }

    /**
     * One frame of a crossing's morph under Lens beads: each bead worked out from its icon's
     * drawn box (SuggestionBarView#drawnIconBoxes) by the rule the beads at rest follow theirs
     * (LensBeadGeometry.drawnAt), on the surface the last full read found, so bead and icon
     * never part. The host, the shelf and the dock's effect once, as a swipe's frame does; at
     * rest and off beads nothing runs past the first check.
     */
    public void syncDockBeadsWithMorph() {
        com.termux.app.dock.LensBeadGeometry.Beads rest = mDockBeads;
        SuggestionBarView row = mHost.dockRow();
        if (rest == null || row == null || Float.isNaN(mDockBeadsRowDy)
            || Float.isNaN(mDockBeadsBottomPx)) return;
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (!(host instanceof DockSurfaceHostLayout) || host.getWidth() <= 0) return;
        int count = row.drawnIconBoxes(mDockBeadBoxes);
        if (count <= 0 || count > com.termux.app.dock.LensBeadGeometry.MAX_BEADS) return;
        for (int i = 0; i < count; i++) {
            mDockBeadBoxes[i * 4] += mDockBeadsRowDx;
            mDockBeadBoxes[i * 4 + 1] += mDockBeadsRowDy;
            mDockBeadBoxes[i * 4 + 2] += mDockBeadsRowDx;
            mDockBeadBoxes[i * 4 + 3] += mDockBeadsRowDy;
        }
        com.termux.app.dock.LensBeadGeometry.Beads drawn =
            com.termux.app.dock.LensBeadGeometry.drawnAt(rest, mDockBeadBoxes, count, 0f, 0f,
                host.getWidth(), mDockBeadsBottomPx, mHost.density());
        if (drawn.equals(rest)) return;
        mDockBeads = drawn;
        ((DockSurfaceHostLayout) host).setBeads(dockBeadsDrawn());
        DockShelfView shelf = mHost.hotView(R.id.accessory_shelf_fx);
        if (shelf != null && mHost.dockRows().shelfShown()) shelf.setBeads(dockBeadsShadowed());
        mHost.rebuildBackdropGlassEffectForLens();
    }

    /**
     * Works the beads out again from the row and the host as laid out and hands them to the
     * host; true when they changed. A change between beads and the slab re-cuts the host's
     * shape at once (the slab's rim and clip go, or come back).
     */
    public boolean refreshDockBeads() {
        syncDockBeadsListeners();
        com.termux.app.dock.LensBeadGeometry.Beads beads = resolveDockBeads();
        // The A–Z row's letter beads under the icons', where it shares their plank.
        boolean azChanged = applyDockAzBeads(resolveDockAzBeads(beads));
        if (beads == null ? mDockBeads == null : beads.equals(mDockBeads)) {
            // The same beads, but the row may have moved on the screen under the frame's cut.
            mHost.syncFrameBeadCut();
            return azChanged;
        }
        boolean modeChanged = (beads == null) != (mDockBeads == null);
        mDockBeads = beads;
        // Back to the slab, nothing of the beads is kept: a swipe's moving beads would otherwise
        // be what the next bead mode draws first, and the swipe's sideways cut what it clips to.
        if (beads == null) mDockBeadsMoving = null;
        View host = mHost.findViewById(R.id.accessory_surface_host);
        if (host instanceof DockSurfaceHostLayout) {
            DockSurfaceHostLayout beadHost = (DockSurfaceHostLayout) host;
            beadHost.setSlabOnlyChildren(mHost.findViewById(R.id.accessory_top_edge_fx),
                mHost.findViewById(R.id.accessory_edge_glow_fx));
            beadHost.setBeads(dockBeadsDrawn());
        }
        if (beads == null) {
            DockShelfView shelf = mHost.findViewById(R.id.accessory_shelf_fx);
            if (shelf != null) shelf.setBeads(null);
        }
        // The slab's cut again where beads came or went, the Search field's well (it nests in its
        // capsule, or in the slab again) and the frame's cut.
        mHost.onDockBeadsReplaced(host, modeChanged);
        return true;
    }

    /**
     * The beads for the row as laid out now, in the host's coordinates, or null where the dock
     * keeps its slab: a style that is not beads; the row not standing alone on the dock (another
     * band on the same glass, the keyboard sharing it, a joined Docked frame being the glass);
     * or a row the beads cannot draw ({@link com.termux.app.dock.LensBeadGeometry#compute}).
     * The Search field under the row is no band of that kind: it stands in a glass capsule of
     * its own under the beads (#84).
     */
    @Nullable
    private com.termux.app.dock.LensBeadGeometry.Beads resolveDockBeads() {
        if (!dockBeadsWanted()) return null;
        SuggestionBarView row = mHost.dockRow();
        View host = mHost.findViewById(R.id.accessory_surface_host);
        View container = mHost.findViewById(R.id.accessory_stack_container);
        if (row == null || !(host instanceof DockSurfaceHostLayout) || container == null
            || host.getWidth() <= 0 || host.getHeight() <= 0 || row.getWidth() <= 0) return null;
        PlaceLayout layout = mHost.placeLayout();
        if (!com.termux.app.dock.LensBeadGeometry.rowWearsBeadsCuttingFrame(layout,
            mHost.stackStandsDownOnFrame(),
            mHost.keyboardSharesDockGlass())) return null;
        // Where the row draws its icons: as laid out at rest, where a crossing's morph draws
        // them mid-slide and through its landing (SuggestionBarView#drawnIconBoxes).
        int count = row.drawnIconBoxes(mDockBeadBoxes);
        if (count <= 0 || count > com.termux.app.dock.LensBeadGeometry.MAX_BEADS) return null;
        float[] rowAt = layoutOffsetIn(row, container);
        float[] hostAt = layoutOffsetIn(host, container);
        if (rowAt == null || hostAt == null) return null;
        float dx = rowAt[0] - hostAt[0];
        float dy = rowAt[1] - hostAt[1];
        // The span the row clips its icons to while a swipe carries them: the beads follow there.
        mDockBeadsRowDx = dx;
        mDockBeadsSpanLeft = Math.max(0f, dx);
        mDockBeadsSpanRight = Math.min(host.getWidth(), dx + row.getWidth());
        float[] boxes = new float[count * 4];
        for (int i = 0; i < count; i++) {
            boxes[i * 4] = mDockBeadBoxes[i * 4] + dx;
            boxes[i * 4 + 1] = mDockBeadBoxes[i * 4 + 1] + dy;
            boxes[i * 4 + 2] = mDockBeadBoxes[i * 4 + 2] + dx;
            boxes[i * 4 + 3] = mDockBeadBoxes[i * 4 + 3] + dy;
        }
        float density = mHost.density();
        // The Search field under the row (#84): the beads are held to the row's own band, as
        // they are without it, and the field stands in a glass capsule of its own under them,
        // its view's bounds (LensBeadGeometry.withCapsule).
        float[] fieldAt = null;
        com.termux.app.dock.PromptLineFieldView field = mHost.promptFieldInRowBand();
        if (field != null) {
            fieldAt = layoutOffsetIn(field, container);
        }
        float bottom = host.getHeight();
        if (fieldAt != null) {
            fieldAt[0] -= hostAt[0];
            fieldAt[1] -= hostAt[1];
            int topPad = ((ViewGroup.MarginLayoutParams) field.getLayoutParams()).topMargin;
            bottom = Math.min(bottom, fieldAt[1] - topPad);
        }
        mDockBeadsRowDy = dy;
        mDockBeadsBottomPx = bottom;
        com.termux.app.dock.LensBeadGeometry.Beads beads =
            com.termux.app.dock.LensBeadGeometry.compute(boxes, count, 0f, 0f, host.getWidth(),
                bottom, density);
        if (beads == null || fieldAt == null) return beads;
        return com.termux.app.dock.LensBeadGeometry.withCapsule(beads, fieldAt[0], fieldAt[1],
            fieldAt[0] + field.getWidth(), fieldAt[1] + field.getHeight(), density);
    }

    // ------------------------------------------------------------------ Letter beads
    //
    // Under Lens beads with the A–Z row on the dock's plank under the icons
    // (LensBeadGeometry.azRowWearsBeads), each letter stands on a small bead of the icons' glass
    // (AzBeadRow): the host clips its frost to them as to the icons' beads, the dock's one glass
    // program draws their rims from the row's first centre and pitch (Program#setAzBeads), and
    // a finger on the row lifts the bead under it and its neighbours with their letters
    // (AzScrubRowView#setBeadLift). A lift's frame re-cuts the host's clip in place and rebuilds
    // the dock's effect once, as a page swipe's frame does; no layout runs, and at rest nothing.

    /** The letter beads in the host's coordinates, or null where the row has none. */
    @Nullable private com.termux.app.dock.AzBeadRow mDockAzBeads;
    /** The A–Z row's x in the host, so a finger along the row is a finger in the host. */
    private float mDockAzRowDx;
    /** The lift as last drawn: the finger's x in the host (NaN for none) and the peak, px. */
    private float mDockAzLiftX = Float.NaN;
    private float mDockAzLiftPeakPx;
    /** The row the letter beads were last handed to, so it can be let go of. */
    @Nullable private AzScrubRowView mDockAzBeadRowView;
    private final AzScrubRowView.BeadLiftListener mDockAzBeadLift = this::onDockAzBeadLift;

    /**
     * The letter beads for the A–Z row as laid out now, in the host's coordinates, or null: no
     * icons' beads ({@code beads}), the row not under the icons on the dock's plank, not shown,
     * or too crowded for a bead to read as a lens.
     */
    @Nullable
    private com.termux.app.dock.AzBeadRow resolveDockAzBeads(
            @Nullable com.termux.app.dock.LensBeadGeometry.Beads beads) {
        if (beads == null) return null;
        View view = mHost.findViewById(R.id.apps_bar_az_row);
        if (!(view instanceof AzScrubRowView)) return null;
        AzScrubRowView az = (AzScrubRowView) view;
        View host = mHost.findViewById(R.id.accessory_surface_host);
        View container = mHost.findViewById(R.id.accessory_stack_container);
        if (host == null || container == null || !az.isShown() || az.isVerticalBar()
            || az.getWidth() <= 0 || az.getHeight() <= 0
            || !com.termux.app.dock.LensBeadGeometry.azRowWearsBeads(mHost.placeLayout()))
            return null;
        float[] azAt = layoutOffsetIn(az, container);
        float[] hostAt = layoutOffsetIn(host, container);
        if (azAt == null || hostAt == null) return null;
        float dx = azAt[0] - hostAt[0];
        float dy = azAt[1] - hostAt[1];
        mDockAzRowDx = dx;
        return com.termux.app.dock.AzBeadRow.compute(dx + az.letterCenterPx(0),
            az.letterPitchPx(), az.letterCount(), dy + az.getHeight() * 0.5f, az.getHeight(),
            mHost.density());
    }

    /**
     * Hands the letter beads to the host and the row (and lets go of them with null); true when
     * they changed. The dock's effect is rebuilt by the caller, as for the icons' beads.
     */
    private boolean applyDockAzBeads(@Nullable com.termux.app.dock.AzBeadRow azBeads) {
        View rowView = mHost.findViewById(R.id.apps_bar_az_row);
        AzScrubRowView row = azBeads != null && rowView instanceof AzScrubRowView
            ? (AzScrubRowView) rowView : null;
        if (mDockAzBeadRowView != null && mDockAzBeadRowView != row)
            mDockAzBeadRowView.setBeadLift(false, null);
        mDockAzBeadRowView = row;
        if (row != null) row.setBeadLift(true, mDockAzBeadLift);
        if (azBeads == null ? mDockAzBeads == null : azBeads.equals(mDockAzBeads)) return false;
        mDockAzBeads = azBeads;
        if (azBeads == null) {
            mDockAzLiftX = Float.NaN;
            mDockAzLiftPeakPx = 0f;
        }
        View host = mHost.findViewById(R.id.accessory_surface_host);
        if (host instanceof DockSurfaceHostLayout) {
            DockSurfaceHostLayout beadHost = (DockSurfaceHostLayout) host;
            beadHost.setAzBeadLift(mDockAzLiftX, mDockAzLiftPeakPx);
            beadHost.setAzBeads(azBeads);
        }
        return true;
    }

    /**
     * One frame of the letter beads' lift (AzScrubRowView#setBeadLift): the finger along the row
     * and the spring's share. The host's clip is re-cut in place and the dock's effect rebuilt
     * once; nothing is laid out.
     */
    private void onDockAzBeadLift(float fingerAlongPx, float strength) {
        if (mDockAzBeads == null) return;
        float x = Float.isNaN(fingerAlongPx) ? Float.NaN : fingerAlongPx + mDockAzRowDx;
        float peak = strength * com.termux.app.dock.AzBeadRow.LIFT_PEAK_DP
            * mHost.density();
        if (Float.compare(x, mDockAzLiftX) == 0 && Float.compare(peak, mDockAzLiftPeakPx) == 0)
            return;
        mDockAzLiftX = x;
        mDockAzLiftPeakPx = peak;
        View host = mHost.hotView(R.id.accessory_surface_host);
        if (host instanceof DockSurfaceHostLayout)
            ((DockSurfaceHostLayout) host).setAzBeadLift(x, peak);
        mHost.rebuildBackdropGlassEffectForLens();
    }

    /**
     * A view's laid-out offset inside {@code ancestor}, from layout values only (a transform in
     * flight, such as the dock's press dip, is not baked in), or null when it is not under it.
     */
    @Nullable
    public static float[] layoutOffsetIn(@NonNull View view, @NonNull View ancestor) {
        float left = 0f;
        float top = 0f;
        for (View v = view; v != null; ) {
            if (v == ancestor) return new float[] {left, top};
            left += v.getLeft();
            top += v.getTop();
            android.view.ViewParent parent = v.getParent();
            if (parent instanceof View) {
                View p = (View) parent;
                left -= p.getScrollX();
                top -= p.getScrollY();
                v = p;
            } else {
                v = null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Plank effects and touch light
    //
    // The dock's reactive glass plank (DockPlankController: tilt, specular, accent rim glow) and
    // the dock icons' touch light (DockTouchFade), fed every touch the window dispatches, before
    // any view acts on it, and dressed with the dock's chrome pass.

    /** The plank's springs; null before the dock's views are wired ({@link #setupDockPlankFx}). */
    @Nullable
    public DockPlankController dockPlankController() {
        return mDockPlankController;
    }

    /** Reactive glass-plank physics for the dock (tilt, specular, accent rim glow). */
    @Nullable private DockPlankController mDockPlankController;
    @Nullable private View mDockPlankTarget;
    private boolean mDockPlankTouchInside = false;
    private float mDockPlankLeft = 0f;
    private float mDockPlankTop = 0f;
    private float mDockPlankWidth = 0f;
    private float mDockPlankHeight = 0f;
    private final int[] mDockPlankLocation = new int[2];
    private final int[] mDockPlankKeySurfaceLocation = new int[2];
    /**
     * The key surfaces that ride on the dock plank. Touches on these never drive the plank
     * physics — see {@link #isOnDockPlankKeySurface(float, float)}.
     */
    private static final int[] DOCK_PLANK_KEY_SURFACE_IDS = {
        R.id.inapp_keyboard_container,
        R.id.terminal_toolbar_view_pager,
    };

    /**
     * Observes (never consumes) touches over the dock to drive the reactive glass-plank physics:
     * the plank tilts toward the finger, dips on press, and the specular/rim glow track contact.
     * Bounds are captured once on ACTION_DOWN so the per-frame tilt transform can't feed back into
     * the hit-test, and reused for the rest of the gesture.
     */
    public void feedDockPlank(MotionEvent ev) {
        DockPlankController controller = mDockPlankController;
        if (controller == null) {
            return;
        }
        // The drawer's open drag starts on the dock; letting the plank keep tilting under it would
        // put two owners on the same views' transforms. The press that began the drag is handed
        // back the moment the drawer engages — otherwise the springs sit frozen at their aimed
        // targets (the UP never arrives here) and keep the rows tilted under the drawer's own lift.
        if (mHost.isAppDrawerEngaged()) {
            if (mDockPlankTouchInside) {
                mDockPlankTouchInside = false;
                controller.releaseToNeutral();
            }
            return;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                View plank = mDockPlankTarget;
                mDockPlankTouchInside = false;
                if (plank == null || plank.getVisibility() != View.VISIBLE
                    || plank.getWidth() <= 0 || plank.getHeight() <= 0) {
                    break;
                }
                plank.getLocationOnScreen(mDockPlankLocation);
                mDockPlankLeft = mDockPlankLocation[0];
                mDockPlankTop = mDockPlankLocation[1];
                mDockPlankWidth = plank.getWidth();
                mDockPlankHeight = plank.getHeight();
                float x = ev.getRawX();
                float y = ev.getRawY();
                if (x >= mDockPlankLeft && x <= mDockPlankLeft + mDockPlankWidth
                    && y >= mDockPlankTop && y <= mDockPlankTop + mDockPlankHeight
                    && !isOnDockPlankKeySurface(x, y)) {
                    mDockPlankTouchInside = true;
                    controller.onPointerDown(
                        (x - mDockPlankLeft) / mDockPlankWidth,
                        (y - mDockPlankTop) / mDockPlankHeight);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (mDockPlankTouchInside && mDockPlankWidth > 0f && mDockPlankHeight > 0f) {
                    controller.onPointerMove(
                        (ev.getRawX() - mDockPlankLeft) / mDockPlankWidth,
                        (ev.getRawY() - mDockPlankTop) / mDockPlankHeight);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDockPlankTouchInside) {
                    controller.onPointerUp();
                    mDockPlankTouchInside = false;
                }
                break;
            default:
                break;
        }
    }

    /**
     * Whether a touch lands on one of the key surfaces the plank carries rather than on the dock.
     *
     * <p>While the in-app keyboard is up the plank target is the whole accessory surface, and that
     * surface holds the keyboard and the extra-keys row alongside the dock. Feeding those touches
     * to the springs tilted and slid the entire plane on every keystroke, so the dock swayed while
     * you typed. The physics belong to pushing the dock, not to pressing a key.</p>
     */
    private boolean isOnDockPlankKeySurface(float rawX, float rawY) {
        for (int viewId : DOCK_PLANK_KEY_SURFACE_IDS) {
            View view = mHost.hotView(viewId);
            if (view == null || view.getVisibility() != View.VISIBLE
                || view.getWidth() <= 0 || view.getHeight() <= 0) {
                continue;
            }
            view.getLocationOnScreen(mDockPlankKeySurfaceLocation);
            float left = mDockPlankKeySurfaceLocation[0];
            float top = mDockPlankKeySurfaceLocation[1];
            if (rawX >= left && rawX <= left + view.getWidth()
                && rawY >= top && rawY <= top + view.getHeight()) {
                return true;
            }
        }
        return false;
    }

    /** The dock icons' touch light; built on the first press that may light, see {@link #dockTouchFade}. */
    @Nullable private com.termux.app.chrome.DockTouchFade mDockTouchFade;
    /** The finger now down landed on a dock icon and lit it. */
    private boolean mDockTouchLightDown;

    /**
     * Observes (never consumes) a press on a dock icon to light the glass under it
     * ({@link com.termux.app.chrome.DockTouchFade}): on at once on the down, nothing on a move,
     * a stepped fall on the lift. The click, the long-press and the drag are the icon's as before.
     */
    public void feedDockTouchLight(@NonNull MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mDockTouchLightDown = false;
                float[] at = dockTouchLightPoint(ev.getRawX(), ev.getRawY());
                if (at == null) return;
                mDockTouchLightDown = true;
                dockTouchFade().down(at[0], at[1]);
                return;
            }
            case MotionEvent.ACTION_MOVE:
                // The drawer's open drag starts on the dock and takes the gesture: let go as on a lift.
                if (mDockTouchLightDown && mHost.isAppDrawerEngaged()) {
                    mDockTouchLightDown = false;
                    dockTouchFade().up();
                }
                return;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mDockTouchLightDown) return;
                mDockTouchLightDown = false;
                dockTouchFade().up();
                return;
            default:
                break;
        }
    }

    /**
     * Where a press at this screen point lights the dock's glass, in the dock backdrop's effect
     * coordinates (the key lens's mapping), or null when it is not on a dock icon over that
     * backdrop: below API 33, with no refracting backdrop up, while a key's lens holds the glass,
     * on the keys, or with the row off the dock (a rail or an off-dock plank has glass of its own).
     */
    @Nullable
    private float[] dockTouchLightPoint(float rawX, float rawY) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !mHost.glassParamsValid()
            || mHost.dockGlassProgram() == null || mHost.keyLensActive() || mHost.isAppDrawerEngaged()) return null;
        SuggestionBarView bar = mHost.dockRow();
        View container = mHost.hotView(R.id.accessory_stack_container);
        View surfaceHost = mHost.hotView(R.id.accessory_surface_host);
        View backdrop = mHost.hotView(R.id.accessory_blur_backdrop);
        if (bar == null || container == null || surfaceHost == null || backdrop == null
            || backdrop.getVisibility() != View.VISIBLE
            || !mHost.viewContainsScreenPoint(surfaceHost, rawX, rawY)
            || isOnDockPlankKeySurface(rawX, rawY)) return null;
        boolean onDock = false;
        for (android.view.ViewParent p = bar.getParent(); p != null; p = p.getParent()) {
            if (p == container) {
                onDock = true;
                break;
            }
        }
        if (!onDock || !bar.isIconAt(rawX, rawY)) return null;
        int[] hostLoc = new int[2];
        surfaceHost.getLocationOnScreen(hostLoc);
        return new float[] {rawX - hostLoc[0] + mHost.glassOriginLeft(), rawY - hostLoc[1] + mHost.glassOriginTop()};
    }

    /**
     * The fade, writing the light into the dock's program and rebuilding its effect once per step,
     * as the key lens does; gated as every glass touch light is ({@link #isGlassTouchLightAllowed}).
     */
    @NonNull
    private com.termux.app.chrome.DockTouchFade dockTouchFade() {
        if (mDockTouchFade != null) return mDockTouchFade;
        mDockTouchFade = new com.termux.app.chrome.DockTouchFade(mHost.context(), mHost.decorView(),
            new com.termux.app.chrome.DockTouchFade.Sink() {
                @Override public void light(float x, float y, float intensity) {
                    com.termux.app.chrome.GlassRefraction.Program program = mHost.dockGlassProgram();
                    if (program == null) return;
                    // A key's lens took the glass meanwhile: no light, and no rebuild of its effect.
                    if (mHost.keyLensActive()) {
                        program.clearTouch();
                        return;
                    }
                    program.setTouch(x, y, com.termux.app.chrome.GlassTouchLight.RADIUS_DP
                        * mHost.density(), intensity);
                    mHost.rebuildBackdropGlassEffectForLens();
                }

                @Override public void clear() {
                    com.termux.app.chrome.GlassRefraction.Program program = mHost.dockGlassProgram();
                    if (program == null) return;
                    program.clearTouch();
                    if (!mHost.keyLensActive()) mHost.rebuildBackdropGlassEffectForLens();
                }
            }, mHost::isGlassTouchLightAllowed);
        return mDockTouchFade;
    }

    /** Lazily wires the reactive glass-plank controller to the inflated dock views. */
    public void setupDockPlankFx() {
        View plank = mHost.findViewById(mHost.isInAppKeyboardShown()
            ? R.id.accessory_surface_host : R.id.accessory_stack_container);
        if (plank == null) {
            return;
        }
        if (mDockPlankController == null || mDockPlankTarget != plank) {
            if (mDockPlankController != null)
                mDockPlankController.reset();
            View specular = mHost.findViewById(R.id.accessory_specular_fx);
            View glow = mHost.findViewById(R.id.accessory_edge_glow_fx);
            mDockPlankController = new DockPlankController(plank, specular, glow);
            mDockPlankTarget = plank;
        }
        // Every row standing on the glass follows the same springs as the glass under it: the
        // pinned-apps layer, the letters, and the band of air between them. The set is registered
        // whole rather than one row at a time — a row left out is a row that reads as thrown off
        // the surface the moment the dock is pushed.
        mDockPlankController.setContentLayers(
            mHost.findViewById(R.id.apps_bar_plank_layer),
            mHost.findViewById(R.id.apps_bar_az_row),
            mHost.findViewById(R.id.apps_bar_indicator_band));
        mDockPlankController.setReducedMotion(mHost.isReducedMotionEnabled());
    }

    /** Refreshes the plank FX drawables (accent/shape may change) and enables it for a shown dock. */
    public void refreshDockPlankFx(float materialAlpha) {
        setupDockPlankFx();
        if (mDockPlankController == null) {
            return;
        }
        int accent = mHost.resolveDockAccentColor();
        View specular = mHost.findViewById(R.id.accessory_specular_fx);
        if (specular != null) {
            specular.setBackground(buildDockSpecularDrawable(accent));
            specular.setAlpha(materialAlpha);
        }
        View glow = mHost.findViewById(R.id.accessory_edge_glow_fx);
        if (glow instanceof DockEdgeGlowView) {
            View surfaceHost = mHost.findViewById(R.id.accessory_surface_host);
            int surfaceHeightPx = surfaceHost != null ? surfaceHost.getHeight() : 0;
            float radius = dockFloats()
                ? resolveDockCapsuleCornerRadiusPx(surfaceHeightPx)
                : 0f;
            DockEdgeGlowView glowView = (DockEdgeGlowView) glow;
            glowView.setAlpha(materialAlpha);
            glowView.setAccentColor(accent);
            glowView.setCornerRadiusPx(radius);
            // Feather the rim into a soft glow instead of a hard outline (API 31+). The view draws
            // its rim inset from the edge so this blur stays inside the rounded clip at the corners.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                float blur = mHost.density() * 7f;
                glow.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
            }
        }
        // Both styles tilt now: the capsule free-floating with a press dip, the edge-to-edge bar
        // hinged at the screen edge and overscanned so its slide cannot open a strip at the sides.
        boolean capsuleDock = dockFloats();
        mDockPlankController.setMotionEnabled(true);
        mDockPlankController.setHingeMode(!capsuleDock);
        mDockPlankController.setReducedMotion(mHost.isReducedMotionEnabled());
        mDockPlankController.setEnabled(true);
    }

    /** Soft accent-tinted radial specular that rides the touch point across the glass. */
    @NonNull
    private Drawable buildDockSpecularDrawable(int accent) {
        GradientDrawable specular = new GradientDrawable();
        specular.setShape(GradientDrawable.RECTANGLE);
        specular.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        specular.setGradientCenter(0.5f, 0.5f);
        specular.setGradientRadius(mHost.dpToPx(120));
        // Wide and soft so it reads as caught light gliding over glass rather than a painted accent
        // disc under the finger — leaning white on the dark dock, and into shadow on the light one,
        // where a white pool under the finger is a pool of nothing.
        int caught = com.termux.app.chrome.ChromeShade.polarity()
            == com.termux.app.chrome.ChromeInk.Polarity.DARK_INK ? Color.BLACK : Color.WHITE;
        specular.setColors(new int[] {
            mHost.withAlphaComponent(caught, 70),
            mHost.withAlphaComponent(accent, 22),
            mHost.withAlphaComponent(accent, 0)
        });
        specular.setDither(true);
        return specular;
    }
}
