package com.termux.app.dock;

import android.os.Trace;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.SuggestionBarView;
import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.chrome.SurfaceDirtyLedger;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.ResolvedArrangement;

/**
 * The dock's layout and dress (ADR 0018 §1), behind {@link DockController}: Dock geometry's
 * snapshot and halves, the layout the last geometry pass applied and its cache, the Dock design
 * each page wears and the dress a settle or a picked design puts on the slab, and the ease that
 * grows or shrinks the slab by its bands. Moved line for line out of the activity (D17, step
 * one); it reaches the activity only through {@link DockController.Host}.
 */
final class DockDress {

    @NonNull private final DockController.Host mHost;
    /** The controller this stands behind: the beads' listeners follow a re-dress. */
    @NonNull private final DockController mDock;

    DockDress(@NonNull DockController.Host host, @NonNull DockController dock) {
        mHost = host;
        mDock = dock;
    }

    /** Dock geometry, which the activity's stack layout measures the bottom stack with. */
    @NonNull
    com.termux.app.dock.DockGeometry dockGeometry() {
        return mDockGeometry;
    }

    /** The squeeze the last geometry pass found: an input to every snapshot until the next. */
    void setDockSqueezePx(int squeezePx) {
        mDockSqueezePx = squeezePx;
    }

    /** The landing's commit notes, which the settle opens and closes around its dress. */
    @NonNull
    com.termux.app.dock.CommitDockDress commitDockDress() {
        return mCommitDockDress;
    }

    /** Whether an ease of the slab's bands runs now. */
    boolean dockBandEaseRunning() {
        return mPromptFieldEase != null;
    }

    /** Where the ease stands for the Search field, 0..1; read only while one runs. */
    float promptFieldEaseValue() {
        return mPromptFieldEaseValue;
    }

    /** Where the ease stands for the Wall strip's row, 0..1; read only while one runs. */
    float wallStripEaseValue() {
        return mWallStripEaseValue;
    }

    private int getDockBaseToolbarHeightPx() {
        if (mHost.terminalToolbarDefaultHeight() > 0) {
            return Math.round(mHost.terminalToolbarDefaultHeight());
        }
        return DockLayoutPolicy.baseToolbarHeightPx(mHost.displayMetrics().density);
    }

    /** Dock geometry (ADR 0011): the one owner of the dock's measurements, memoised per snapshot. */
    private final com.termux.app.dock.DockGeometry mDockGeometry =
        new com.termux.app.dock.DockGeometry();

    /**
     * How much the bottom stack sheds to fit under its ceiling, as the last geometry pass found
     * it. An input to every snapshot, so whatever asks between two geometry passes is answered
     * with the dock that pass applied, never an unsqueezed second one.
     */
    private int mDockSqueezePx;

    /**
     * One snapshot of both halves for the dock's geometry: the arrangement a pass stands its
     * chrome in, the half whose rows it shows ({@code rowsPage}), and every raw fact the dock's
     * measurements are a function of. Nothing is worked out here; Dock geometry derives the rest.
     */
    @NonNull
    com.termux.app.dock.DockGeometry.Snapshot dockSnapshot(
            @NonNull PlaceLayout arrangement, @NonNull com.termux.app.wall.PaneWallPage rowsPage) {
        PlaceOrientation orientation = mHost.currentPlaceOrientation();
        DisplayMetrics metrics = mHost.displayMetrics();
        return com.termux.app.dock.DockGeometry.Snapshot.builder(
                dockHalf(com.termux.app.wall.PaneWallPage.WIDGETS, orientation, arrangement),
                dockHalf(com.termux.app.wall.PaneWallPage.TERMINAL, orientation, arrangement))
            .preferencesAvailable(mHost.hasPreferences())
            .rows(mHost.arrangedPageOf(rowsPage))
            .arrangement(arrangement)
            .density(metrics.density)
            .widthPx(dockRowsWidthPx(metrics))
            .cutouts(mHost.cutoutInsetLeft(), mHost.cutoutInsetRight())
            .baseToolbarHeightPx(getDockBaseToolbarHeightPx())
            .landscape(orientation == PlaceOrientation.LANDSCAPE)
            .pinnedSlotCount(dockSlotCount(metrics))
            // The Search field under the apps row and the Wall strip's row over it: all of each
            // while the page's layout turns it on, between while a toggle, the palette or a slide
            // across the seam eases the slab.
            .fieldReveal(mHost.promptFieldReveal())
            .stripReveal(mHost.wallStripReveal())
            .squeezePx(mDockSqueezePx)
            .build();
    }

    /**
     * {@code page}'s half: its resolved arrangement (layout, Style, Dock design and Search field),
     * and the Corners, Margin, dock size and Wall strip height of the layout it wears. Before the
     * preferences are read, a default half standing in {@code fallbackLayout}.
     */
    @NonNull
    com.termux.app.dock.DockGeometry.Half dockHalf(
            @NonNull com.termux.app.wall.PaneWallPage page, @NonNull PlaceOrientation orientation,
            @NonNull PlaceLayout fallbackLayout) {
        return dockHalf(mHost.arrangedPageOf(page), orientation, fallbackLayout);
    }

    /**
     * The same for {@code page} named by its id: a Home page with an id is the other Home end of
     * a crossing between two Home pages (#173 slice E, {@code DockGeometry.Snapshot#otherHome}),
     * kept apart from the Home half the chrome stands in, so neither memo is built again per
     * frame.
     */
    @NonNull
    com.termux.app.dock.DockGeometry.Half dockHalf(
            @NonNull com.termux.app.wall.PageRef page, @NonNull PlaceOrientation orientation,
            @NonNull PlaceLayout fallbackLayout) {
        PlaceLayoutStore store = mHost.placeLayoutStore();
        if (store == null)
            return com.termux.app.dock.DockGeometry.Half.builder(fallbackLayout).build();
        boolean home = page.isHome();
        boolean otherHome = home && page.id >= 0;
        // Its layout, Style, Dock design and Search field: the half's resolved arrangement.
        ResolvedArrangement arrangement = mHost.arrangementOf(page);
        com.termux.app.place.LayoutVariant variant = arrangement.variant();
        PlaceLayoutStore sizes = store.forVariant(variant);
        com.termux.app.place.WallShape shape = store.shape(home);
        float scale = sizes.dockHeightScale(orientation);
        int stripDp = sizes.wallStripHeightDp(orientation);
        // The one icon size, and the Home grid the dock's side insets follow (spec §8a).
        // The step drawn: the user's choice, capped by what the pinned apps fit at.
        int iconDp = mHost.drawnIconSize(store).dp;
        float gridEdgeDp = mHost.homeMarginDp();
        int gridColumns = store.widgetColumns(orientation);
        // The same facts as last time: the same half, so a pass per frame of a crossing builds
        // no halves (docs/agents/performance.md rule 3) and the memo matches it by identity.
        com.termux.app.dock.DockGeometry.Half last = otherHome ? mDockOtherHomeHalf
            : home ? mDockHomeHalf : mDockWorkspaceHalf;
        if (last != null && last.arrangement == arrangement
            && Float.compare(last.dockHeightScale, scale) == 0
            && last.marginDp == shape.marginDp && last.cornersDp == shape.cornersDp
            && last.wallStripHeightDp == stripDp && last.iconDp == iconDp
            && Float.compare(last.gridEdgeDp, gridEdgeDp) == 0
            && last.gridColumns == gridColumns) return last;
        com.termux.app.dock.DockGeometry.Half half =
            com.termux.app.dock.DockGeometry.Half.builder(arrangement)
                .dockHeightScale(scale)
                .marginDp(shape.marginDp)
                .cornersDp(shape.cornersDp)
                .wallStripHeightDp(stripDp)
                .iconDp(iconDp)
                .grid(gridEdgeDp, gridColumns)
                .build();
        if (otherHome) mDockOtherHomeHalf = half;
        else if (home) mDockHomeHalf = half;
        else mDockWorkspaceHalf = half;
        return half;
    }

    /** Each half as {@link #dockHalf} last built it, handed back while its facts stand. */
    @Nullable private com.termux.app.dock.DockGeometry.Half mDockHomeHalf;
    /** The other Home end's half of a crossing between two Home pages, as last built. */
    @Nullable private com.termux.app.dock.DockGeometry.Half mDockOtherHomeHalf;
    @Nullable private com.termux.app.dock.DockGeometry.Half mDockWorkspaceHalf;

    /**
     * The width the dock's rows span: the content root's, between the display cutouts it pads.
     * Before the root is laid out, the screen's less the same cutouts.
     */
    private int dockRowsWidthPx(@NonNull DisplayMetrics metrics) {
        View root = mHost.hotView(R.id.activity_termux_root_relative_layout);
        if (root != null && root.getWidth() > 0)
            return Math.max(0, root.getWidth() - root.getPaddingLeft() - root.getPaddingRight());
        return Math.max(0, metrics.widthPixels - mHost.cutoutInsetLeft()
            - mHost.cutoutInsetRight());
    }

    /** The pinned slots a page of the row holds: the preference, or the width rule when unset. */
    int dockSlotCount(@NonNull DisplayMetrics metrics) {
        int count = !mHost.hasPreferences() ? 0 : mHost.preferences().getAppLauncherButtonCount();
        return count > 0 ? count : mHost.calculateSuggestionBarMaxButtons(metrics);
    }

    /**
     * What the dock's design asks of its slab, on the committed page: the Dock design its layout
     * holds, on either half (#93). Read off the committed page rather than the stack's dress, so
     * a slide across the seam keeps the geometry it was committed with and a design that differs
     * between the halves comes and goes at settle ({@link #restyleDockSurfaceForPageIfChanged}).
     * A narrow read (no dress built), since a key's lens rebuilds the dock's glass per frame.
     */
    @NonNull
    private com.termux.app.dock.DockSurfaceSpec dockSurfaceSpec() {
        return com.termux.app.dock.DockSurfaceSpec.forStyle(committedDockDesign());
    }

    /**
     * The design the dock's rows on screen wear, for the readers of its dressing — the slab's glass
     * program, its contact shadow, the beads. Dock rows decides it
     * ({@link com.termux.app.dock.DockRows#wornSurface}): while a crossing stands, the applied
     * layout's, measured for the half whose rows the frame shows; at rest the committed half's
     * dress, so a design just picked in the Layout editor is worn before the pass that lays it out.
     */
    @NonNull
    com.termux.app.dock.DockSurfaceSpec shownDockSurfaceSpec() {
        return mHost.dockRows().wornSurface(dockSurfaceSpec(), mHost.seam().committedDesign() != null);
    }

    /** The slab the dock wears on {@code page}: its Dock design's. */
    @NonNull
    private com.termux.app.dock.DockSurfaceSpec dockSurfaceSpec(
            @NonNull com.termux.app.wall.PaneWallPage page) {
        return com.termux.app.dock.DockSurfaceSpec.forStyle(wornDockDesign(page));
    }

    /**
     * The Dock design {@code page}'s slab wears (WornDockDesign): its layout's, or none where
     * that layout shows no apps row. The stored pick stays as it is.
     */
    @NonNull
    com.termux.app.dock.DockStyle wornDockDesign(
            @NonNull com.termux.app.wall.PaneWallPage page) {
        ResolvedArrangement arranged = mHost.arrangementOf(page);
        return com.termux.app.dock.WornDockDesign.of(arranged.design(), arranged.layout());
    }

    /**
     * A settle that changes the dock's surface spec — between halves whose layouts hold different
     * designs — lays the dock out and dresses its glass again. Nothing happens while the spec is
     * the same, as it is between Home and a window on one layout: the swipe never re-dresses the
     * dock there.
     */
    void restyleDockSurfaceForPageIfChanged(@NonNull com.termux.app.wall.PaneWallPage from,
                                                    @NonNull com.termux.app.wall.PaneWallPage to) {
        if (!mHost.hasPreferences() || mHost.chrome() == null) return;
        // A pictured dip dressed the slab for this page on its first frame: it already wears it.
        boolean travelled = mHost.dockDesignTravelledTo() == to;
        if (travelled) mHost.setDockDesignTravelledTo(null);
        boolean sameDress = !travelled && dockSurfaceSpec(from).equals(dockSurfaceSpec(to))
            && mHost.arrangementOf(from).searchField() == mHost.arrangementOf(to).searchField()
            && mHost.arrangementOf(from).drawerTile() == mHost.arrangementOf(to).drawerTile();
        // The commit's place pass dressed the dock for these very inputs already, with no ease
        // under way beside it: the landed frame wears it, and a second dress changes nothing.
        boolean worn = !travelled && !sameDress && mCommitDockDress.worn(commitDockDressInputs());
        switch (com.termux.app.dock.CommitDockDress.decide(travelled, sameDress, worn,
                mPromptFieldEase != null)) {
            case NONE:
            case ALREADY_WORN:
                return;
            case SYNC_DRAWER_TILE:
                // One dress on both, but a Home layout keeps its own Drawer tile's place.
                mHost.syncDrawerTile();
                return;
            case CANCEL_EASE_AND_RESTYLE:
            default:
                // A settle lands the place's geometry at once (ADR 0003): an ease under way gives
                // way.
                cancelDockBandEase();
                restyleDockSurface();
        }
    }

    /** Notes the dresses a landing's commit makes, so its Dock design step dresses only once. */
    private final com.termux.app.dock.CommitDockDress mCommitDockDress =
        new com.termux.app.dock.CommitDockDress();

    /** What a dock dress reads that a commit can move, as it stands now (CommitDockDress). */
    @NonNull
    private com.termux.app.dock.CommitDockDress.Inputs commitDockDressInputs() {
        com.termux.app.wall.PaneWallPage rowsPage = mHost.stackDressPage();
        boolean drawerTile = mHost.hasPreferences() && mHost.arrangementOf(rowsPage).drawerTile();
        return new com.termux.app.dock.CommitDockDress.Inputs(mHost.lastWallPage(), rowsPage,
            committedDockDesign(), mHost.committedSearchField(), drawerTile, mHost.arrangementRevision(),
            mHost.isStatusBarCompact(), mHost.statusBarEdge(),
            mHost.isInAppKeyboardVisible(), mHost.halfRedressCount());
    }

    /**
     * Lays the dock out and dresses its glass again for the spec it wears now: a settle between
     * halves of different designs ({@link #restyleDockSurfaceForPageIfChanged}), or a Dock design
     * picked in the
     * Layout editor ({@code TermuxActivity#doSyncPlaceLayout}), live, with no restart. The backdrop up stays
     * until the re-cut one replaces it, so the switch never shows a bare frame.
     */
    /** The Dock design and Search field the slab was last dressed for; null before any. */
    @Nullable private com.termux.app.dock.DockStyle mDressedDockDesign;
    private boolean mDressedSearchField;

    /** Whether the slab is already dressed for {@code design} and {@code searchField}. */
    boolean slabWears(@NonNull com.termux.app.dock.DockStyle design,
                              boolean searchField) {
        return design == mDressedDockDesign && searchField == mDressedSearchField;
    }

    void restyleDockSurface() {
        if (mHost.hasPreferences()) {
            mDressedDockDesign = committedDockDesign();
            mDressedSearchField = mHost.committedSearchField();
        }
        if (!mHost.hasPreferences() || mHost.chrome() == null) return;
        Trace.beginSection("Dock.restyle");
        try {
            mDock.syncDockBeadsListeners();
            // The Drawer tile comes and goes with the page's toggle.
            mHost.syncDrawerTile();
            // The glass is re-cut even where the geometry did not move: its features are the
            // spec's.
            mHost.chrome().ledger().markDirty(SurfaceDirtyLedger.Backdrop.ACCESSORY);
            mHost.updateAppLauncherBarHeight();
            mHost.setTerminalToolbarHeight(true);
            // The Wall strip's row, laid out by the pass above, takes the Workspace's windows.
            mHost.syncWallStripFeed();
            mHost.chrome().requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME
                | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
            // Inside a landing's commit, what it was dressed for, read once it is dressed.
            if (mCommitDockDress.open()) mCommitDockDress.dressed(commitDockDressInputs());
        } finally {
            Trace.endSection();
        }
    }

    /**
     * The store pinned to the layout the committed page's dock is dressed by, where its Drawer
     * tile is kept: Home's layout on Home, the Workspace's (normal or minimal) on a window.
     */
    @Nullable
    PlaceLayoutStore committedDockStore() {
        return dockStoreOf(mHost.lastWallPage());
    }

    /** The store pinned to the layout {@code page}'s dock is dressed by. */
    @Nullable
    PlaceLayoutStore dockStoreOf(@NonNull com.termux.app.wall.PaneWallPage page) {
        PlaceLayoutStore store = mHost.placeLayoutStore();
        // A layout's dock is its own: each page wears its kind's layout (ADR 0020 §1).
        return store == null ? null : store.forVariant(mHost.layoutOf(page));
    }

    /** How long a style switch takes to grow or shrink the slab by the field. */
    private static final long PROMPT_FIELD_EASE_MS = 240L;

    /**
     * A style switch easing the slab's bands in or out (the Search field under the row,
     * the Wall strip's row over it, both at once where one style trades for the other); null at
     * rest.
     */
    @Nullable private android.animation.ValueAnimator mPromptFieldEase;
    /** Where that ease stands now, 0..1 of the field. */
    private float mPromptFieldEaseValue;
    /** Where it stands for the Wall strip's row, 0..1 of the row. */
    private float mWallStripEaseValue;

    /**
     * How much of a band only {@code design} carries (the Wall strip's row) the dock is laid
     * out with: all of it resting on a page whose dock wears the design,
     * none on one whose dock does not, and between, while a slide crosses the seam toward a half
     * of the other kind, the share of the way to the half that wears it ({@code
     * TermuxActivity#syncSeamBandShare}), so the slab eases with the page rather than stepping at the settle.
     * Halves that wear the same design keep the band whole, or without it, all the way across.
     */
    float dockBandShare(@NonNull com.termux.app.dock.DockStyle design) {
        return mHost.seam().designShare(design, committedDockDesign() == design ? 1f : 0f);
    }

    /**
     * The committed page's Dock design: the one read when the slide reached the seam while one
     * crosses it (SeamCrossing), so its frames read no store, else the dress's. A pictured dip's
     * stack is the other half's from its first frame, and wears that half's design
     * (SeamCrossing#stackDesign).
     */
    @NonNull
    com.termux.app.dock.DockStyle committedDockDesign() {
        com.termux.app.dock.DockStyle travelling = mHost.seam().stackDesign();
        return travelling != null ? travelling : wornDockDesign(mHost.lastWallPage());
    }

    /**
     * A Dock design was picked: where the Search field or the Wall strip's row comes or goes
     * on a dock in view, the slab eases from what it shows now to its new height, before any pass
     * lays the new style out, on either half. Anywhere else (the dock not laid out yet, reduced
     * motion) the passes land it at once.
     */
    boolean beginDockBandEase() {
        cancelDockBandEase();
        if (!mHost.hasPreferences() || mHost.chrome() == null) return false;
        com.termux.app.dock.DockSurfaceSpec spec = dockSurfaceSpec();
        float target = mHost.promptFieldWanted() ? 1f : 0f;
        float from = mHost.appliedPromptFieldReveal();
        float wallTarget = spec.wallStrip ? 1f : 0f;
        float wallFrom = mHost.appliedWallStripReveal();
        if ((from == target && wallFrom == wallTarget) || mHost.isReducedMotionEnabled()) return false;
        View stack = mHost.findViewById(R.id.accessory_stack_container);
        View rowHost = mHost.findViewById(R.id.apps_bar_row_host);
        if (stack == null || rowHost == null || !mHost.dockRows().stackShown()
            || !mHost.dockRows().isRowOnScreen(Element.APPS) || stack.getHeight() <= 0
            || rowHost.getParent() != mHost.findViewById(R.id.accessory_row_stack)) return false;
        android.animation.ValueAnimator ease = android.animation.ValueAnimator.ofFloat(0f, 1f);
        ease.setDuration(Math.round(PROMPT_FIELD_EASE_MS
            * Math.max(Math.abs(target - from), Math.abs(wallTarget - wallFrom))));
        ease.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));
        ease.addUpdateListener(animator -> {
            if (mPromptFieldEase != animator) return;
            float t = (float) animator.getAnimatedValue();
            mPromptFieldEaseValue = from + (target - from) * t;
            mWallStripEaseValue = wallFrom + (wallTarget - wallFrom) * t;
            // One dock pass, and the glass re-cut on the same frame when the slab moved.
            if (mHost.setTerminalToolbarHeight(false))
                mHost.chrome().requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME
                    | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
        });
        ease.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(android.animation.Animator animation) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (mPromptFieldEase != animation) return;
                mPromptFieldEase = null;
                // Landed: the style's own pass, the field wholly in or gone.
                if (!mCancelled) restyleDockSurface();
            }
        });
        mPromptFieldEaseValue = from;
        mWallStripEaseValue = wallFrom;
        mPromptFieldEase = ease;
        ease.start();
        return true;
    }

    /** Stops an ease under way where it stands; the caller's pass lands the style. */
    void cancelDockBandEase() {
        android.animation.ValueAnimator ease = mPromptFieldEase;
        if (ease == null) return;
        mPromptFieldEase = null;
        ease.cancel();
    }

    /**
     * Dock geometry's answer for the arrangement on screen, worked out for the facts as they stand
     * now: what a pass that applies the dock outside the geometry pass applies
     * ({@code TermuxActivity#updateAppLauncherBarHeight}). Readers read {@link #appliedDockLayout} instead.
     */
    @NonNull
    DockLayout getDockLayout() {
        return dockLayoutFor(mHost.placeLayout());
    }

    /**
     * The dock every writer of the dock's dressing reads: the one the last geometry pass applied
     * (ADR 0011), so no reader builds a snapshot of its own or measures a dock the pass did not
     * lay out. Dock geometry's answer for the arrangement on screen only before the first pass,
     * or while an arrangement just laid out waits for its pass ({@link #dockLayoutIn}). Read by
     * the app drawer's choreography through this same seam.
     */
    @NonNull
    DockLayout appliedDockLayout() {
        return dockLayoutIn(mHost.placeLayout());
    }

    /**
     * The dock for an arrangement a caller is laying out: the applied one when the last pass
     * measured it in that arrangement, else Dock geometry's answer for it. The answer differs
     * only for an arrangement no pass has applied yet: one a layout change lays out before its
     * geometry pass, or one an arrangement test drives against the real views.
     */
    @NonNull
    DockLayout dockLayoutIn(@NonNull PlaceLayout arrangement) {
        DockLayout applied = mAppliedDockLayout;
        // A preference write (Margin, Corners, a design or toggle) keeps the arrangement but moves
        // the dock, so the applied one is reused only while the store has not moved either.
        return applied != null && arrangement.equals(mAppliedDockArrangement)
            && dockStoreRevision() == mAppliedDockRevision
            ? applied : dockLayoutFor(arrangement);
    }

    /** The layout store's revision, or MIN_VALUE before the preferences are read. */
    private int dockStoreRevision() {
        PlaceLayoutStore store = mHost.placeLayoutStore();
        return store == null ? Integer.MIN_VALUE : store.revision();
    }

    /**
     * The dock's geometry for an arrangement the caller is applying, rather than the stored one.
     *
     * <p>Package-visible for the same reason {@code TermuxActivity#applyEdgeStacks} is: the paging tests drive a
     * whole arrangement against the real {@code activity_termux.xml} rather than re-deriving the
     * band each bar is handed.
     */
    @NonNull
    DockLayout dockLayoutFor(@NonNull PlaceLayout layout) {
        return mDockGeometry.layout(dockSnapshot(layout, mHost.stackDressPage()));
    }

    /**
     * The dock the last geometry pass applied: every writer of the dock's views reads this one
     * answer rather than working its own out.
     */
    @Nullable private DockLayout mAppliedDockLayout;
    /** The arrangement {@link #mAppliedDockLayout} was measured in; null where not known. */
    @Nullable private PlaceLayout mAppliedDockArrangement;
    /** The store revision the applied dock was measured at. */
    private int mAppliedDockRevision = Integer.MIN_VALUE;
    private int mAppliedAzRowChinPaddingPx = Integer.MIN_VALUE;
    private int mAppliedAzRowCrownPaddingPx = Integer.MIN_VALUE;

    /**
     * What the dock shows changed (Dock rows tells this before the views move). The readers that
     * depend on the pinned apps' row being on screen hear it here rather than reading the views.
     */
    void onDockRowsChanged(@NonNull com.termux.app.dock.DockRows.Shown shown) {
        SuggestionBarView bar = mHost.dockRow();
        // A drag on the pinned apps ends while its source is still attached and visible. The
        // letters' and the extra keys' rows are deliberately not part of this decision.
        if (!shown.apps && bar != null) bar.cancelActiveDockDrag();
        // Back on the dock, the row's ticks say again whether anything shows in their band.
        if (shown.pageBand == com.termux.app.dock.DockRows.PageBand.TICKS && bar != null)
            bar.publishPageIndicator();
    }

    /** True when this layout moved the dock. Package-visible for the arrangement tests. */
    boolean applyDockLayout(@NonNull DockLayout layout) {
        return applyDockLayout(layout, null);
    }

    /**
     * True when this layout moved the dock. {@code arrangement} is the one it was measured in, so
     * a caller laying out that arrangement reads it back ({@link #dockLayoutIn}); null where the
     * caller does not say.
     */
    boolean applyDockLayout(@NonNull DockLayout layout,
                                    @Nullable PlaceLayout arrangement) {
        // The rows' heights, and the design the shown rows wear, through Dock rows.
        boolean moved = mHost.dockRows().applyLayout(layout);
        // The Drawer tile follows whichever half's rows are shown: a pass that lays out the other
        // half's rows than the tile was laid out for, by whatever path the rows swapped (a
        // crossing's stand, its landing, a slide cut short), lays the tile out for them.
        if (mHost.drawerTileRowsPage() != mHost.stackDressPage()) mHost.syncDrawerTile();
        // The dock's chin is the dock's row's; a bar standing elsewhere carries the one its own
        // host was sized for, and must not have it taken away because the dock has no row. The
        // pull tab's letters on the bottom edge are such a bar: they keep their own chin.
        if (mHost.azScrubRowView() != null && mHost.azBarEdge() == PlaceLayout.Edge.BOTTOM
            && mHost.azScrubRowView() != mHost.azTabRowView()) {
            moved |= layout.azRowChinPaddingPx != mAppliedAzRowChinPaddingPx;
            mAppliedAzRowChinPaddingPx = layout.azRowChinPaddingPx;
            mHost.azScrubRowView().setChinPaddingPx(layout.azRowChinPaddingPx);
            moved |= layout.azRowCrownPaddingPx != mAppliedAzRowCrownPaddingPx;
            mAppliedAzRowCrownPaddingPx = layout.azRowCrownPaddingPx;
            mHost.azScrubRowView().setCrownPaddingPx(layout.azRowCrownPaddingPx);
        }
        moved |= mHost.updateViewBottomMargin(R.id.apps_bar_viewpager, 0);
        moved |= mHost.applyDockRowHorizontalInsets(layout);
        moved |= mHost.syncPromptLineField(layout);
        moved |= mHost.syncWallStripRow(layout);
        // The row is told its sizes here and nowhere else: its band, the icon one slot draws and
        // the rail's, in one hand-over.
        DockLayout applied = mAppliedDockLayout;
        if (mHost.dockRow() != null) {
            moved |= applied == null || layout.appsRowBandHintPx != applied.appsRowBandHintPx
                || layout.slotIconPx != applied.slotIconPx;
            mHost.dockRow().setDockLayout(layout);
        }
        mAppliedDockLayout = layout;
        mAppliedDockArrangement = arrangement;
        mAppliedDockRevision = dockStoreRevision();
        return moved;
    }
}
