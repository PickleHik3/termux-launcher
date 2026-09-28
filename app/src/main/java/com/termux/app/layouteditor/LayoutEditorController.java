package com.termux.app.layouteditor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.Spring;
import com.termux.app.editorshell.EditorShellControlHost;
import com.termux.app.editorshell.EditorShellHeader;
import com.termux.app.editorshell.EditorShellMetrics;
import com.termux.app.editorshell.EditorShellPaint;
import com.termux.app.editorshell.EditorShellRows;
import com.termux.app.editorshell.EditorShellSheet;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.fragments.settings.PlaceMiniatureView;
import com.termux.app.place.PlaceArrangeModel;
import com.termux.app.place.PlaceArrangeModel.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Layout editor: a miniature of the place the user is looking at, parked over the live place,
 * with a Portrait / Landscape toggle above it. The layout it edits is every place's (ADR 0003), so
 * it has no place to pick: a drop lands on Home, the terminal and the display alike.
 *
 * <p>The sibling of the surface editor and not a page of it: this one answers where a place's
 * elements sit, that one answers how its surfaces look, and only ever one of the two is open. A bar
 * is dragged on the miniature to an edge or into the tray under it, and the drop writes straight
 * through, so the place behind the card follows every drop made in the orientation the phone is
 * actually in. The other orientation moves on the miniature alone until the phone is turned.
 *
 * <p>Beneath the picture stand the rows for what no bar can be dragged into — the dock's and the
 * keyboard's height, the keyboard's own choices and its chin, and Home's grid. They write through
 * the same way a drop does. The toggle, the picture and the rows are one list under the header,
 * scrolling as one, the way an Android sheet's content does.
 *
 * <p>The card is the shell's sheet ({@link EditorShellSheet}), the same one the Appearance editor
 * stands in: on a portrait screen it rests about a fifth of the screen down, a pull on its handle,
 * its header or its list grows it toward the top inset before the list scrolls, and a firm pull
 * past its resting height closes it the way Back does.
 *
 * <p>✓ keeps the edits, Discard and the revert glyph put every bar back where the editor found it,
 * and Back with something moved asks rather than choosing for the user — the live write-through
 * means leaving would otherwise mean keeping by accident.
 *
 * <p>Every decision here — what is shown, what a drop writes, whether the place follows, whether
 * anything has moved — belongs to {@link LayoutEditorPlan}; this class is the shell that draws its
 * answers. {@link Host} is the seam to the activity, the same shape the surface editor's is.
 */
public final class LayoutEditorController {

    /** What the editor needs from the activity: its views, the places, and the chrome pass. */
    public interface Host {
        @NonNull Context context();

        @Nullable <T extends View> T findView(int viewId);

        /** Where the shared arrangement is kept, or null before the preferences exist. */
        @Nullable PlaceLayoutStore places();

        /** The place the chrome on screen belongs to: what a corner tab opens the editor on. */
        @NonNull PaneWallPage placeOnScreen();

        /** The orientation the phone is in, which is the only one the live place can follow. */
        @NonNull PlaceOrientation placeOrientation();

        /**
         * A bar moved: re-lay every piece of chrome the arrangement decides, for the place on
         * screen, without tearing the editor down.
         */
        void applyPlaceArrangement();

        /**
         * Bring the pane wall to the place the editor is open on and hold its gestures, or hand
         * them back. What the miniature draws is what the user is looking at, so the wall stands
         * still on that place for as long as the editor is up.
         */
        void holdPaneWallOnPlace(@NonNull PaneWallPage place, boolean held);

        int themeColor(int attr, int fallbackRes);

        /** Whether the surface editor holds the screen; the two are never up together. */
        boolean isSurfaceEditorActive();

        /**
         * The dock's corner radius in dp, as the dock draws it: what the miniature's cards are
         * rounded from, scaled to the picture, so they read as the surfaces they stand for.
         */
        default float dockCornerRadiusDp() {
            return PlaceMiniatureView.DEFAULT_DOCK_RADIUS_DP;
        }
    }

    @NonNull private final Host mHost;

    /** The editor's fixed views, inflated once per process. */
    private static final class Card {
        final ViewGroup host;
        /** The card itself: the shell's sheet, a header over one scrolling list. */
        final EditorShellSheet root;
        /** The list, the card's one scroller. */
        final NestedScrollView scroller;
        /** The column inside the list, which is where its bottom air lives. */
        final ViewGroup column;
        /** The mark at the top that says the sheet came up from the bottom edge. */
        final View handle;
        final View header;
        final TextView title;
        final TextView revert;
        final TextView discard;
        final TextView done;
        final ViewGroup chooserSlot;
        final View orientationRow;
        final EditorShellControlHost orientationHost;
        final MaterialButtonToggleGroup orientation;
        final TextView orientationNotice;
        final LinearLayout body;
        final PlaceMiniatureView miniature;
        final TextView narrowNotice;
        /** The rows' own column, in the list under the picture or in the pane beside it. */
        final LinearLayout rowsHost;

        Card(ViewGroup host, EditorShellSheet root) {
            this.host = host;
            this.root = root;
            scroller = root.body() instanceof NestedScrollView
                ? (NestedScrollView) root.body() : null;
            column = root.findViewById(R.id.layout_editor_card_column);
            handle = root.handle();
            header = root.findViewById(R.id.editor_shell_header);
            title = root.findViewById(R.id.editor_shell_header_title);
            revert = root.findViewById(R.id.editor_shell_header_revert);
            discard = root.findViewById(R.id.editor_shell_header_discard);
            done = root.findViewById(R.id.editor_shell_header_done);
            chooserSlot = root.findViewById(R.id.editor_shell_chooser_slot);
            orientationRow = root.findViewById(R.id.layout_editor_orientation_row);
            orientationHost = root.findViewById(R.id.layout_editor_orientation_host);
            orientation = root.findViewById(R.id.layout_editor_orientation);
            orientationNotice = root.findViewById(R.id.layout_editor_orientation_notice);
            body = root.findViewById(R.id.layout_editor_body);
            miniature = root.findViewById(R.id.layout_editor_miniature);
            narrowNotice = root.findViewById(R.id.layout_editor_narrow_notice);
            rowsHost = root.findViewById(R.id.layout_editor_rows_host);
        }

        boolean complete() {
            return scroller != null && column != null && handle != null && header != null
                && title != null
                && revert != null && discard != null
                && done != null && chooserSlot != null && orientationRow != null
                && orientationHost != null && orientation != null && orientationNotice != null
                && body != null && miniature != null && narrowNotice != null && rowsHost != null;
        }
    }

    @Nullable private Card mCard;
    @Nullable private LayoutEditorPlan mPlan;
    /** True while the toggle is being restated from the plan, so it writes nothing back. */
    private boolean mRestatingToggle;
    /** The track a finger is on, which no restatement may move under it. */
    @Nullable private SeekBar mDraggedSlider;
    /** Restates every row from the store; run after anything that can move what one says. */
    @NonNull private final List<Runnable> mRowSyncs = new ArrayList<>(5);
    /** The place and orientation the rows standing there were built for, or null for none. */
    @Nullable private String mRowsKey;
    /**
     * The sheet's own channel: 1 is the card parked below the bottom edge, 0 is the card in place.
     * One spring for both directions, so a card closed while it is still opening turns round from
     * where it is rather than jumping.
     */
    @NonNull private final Spring mSheet = new Spring(1f, 420f, 41f);
    private boolean mSheetAnimating;
    private long mSheetLastFrameNanos;
    /** How far a pull has pushed the card below its resting height, from {@link EditorShellSheet}. */
    private float mOvershootPx;
    /** The wash over the live place behind the card, built with the card and never blurred. */
    @Nullable private View mScrim;
    /** Whether the card is up or coming up; false the moment something asks it to leave. */
    private boolean mShowing;

    public LayoutEditorController(@NonNull Host host) {
        mHost = host;
    }

    public boolean isActive() {
        return mPlan != null;
    }

    /** The place the editor is open on, or null while it is not. */
    @Nullable
    @VisibleForTesting
    public PaneWallPage editedPlace() {
        return mPlan == null ? null : mPlan.place();
    }

    /** The orientation the miniature is showing, or null while the editor is not open. */
    @Nullable
    @VisibleForTesting
    public PlaceOrientation shownOrientation() {
        return mPlan == null ? null : mPlan.shownOrientation();
    }

    // ------------------------------------------------------------------------------------ entry

    /**
     * Opens the editor on one place. A door opened while the editor is already up moves it to that
     * place rather than starting over, so what Discard puts back is still the arrangement the
     * session opened on.
     */
    public void enter(@NonNull PaneWallPage place) {
        PlaceLayoutStore places = mHost.places();
        // Only one editor at a time: the surface editor is already holding the screen, and the two
        // would be writing through to the same chrome from two cards.
        if (places == null || mHost.isSurfaceEditorActive())
            return;
        Card card = card();
        if (card == null)
            return;
        if (mPlan == null) mPlan = LayoutEditorPlan.enter(places, place, mHost.placeOrientation());
        else mPlan.showPlace(place);
        card.host.setVisibility(View.VISIBLE);
        card.host.setClickable(true);
        card.host.setFocusable(true);
        card.host.bringToFront();
        mHost.holdPaneWallOnPlace(place, true);
        sync();
        // A second door opened while the card is already up moves it to that place; it does not
        // play the card in again.
        if (!mShowing) {
            mShowing = true;
            // The card comes up at its resting height every time; a pull is for this visit.
            card.root.snapToRest();
            mOvershootPx = 0f;
            startSheet(card);
        }
    }

    /** Inflates the card into its host, once. */
    @Nullable
    private Card card() {
        if (mCard != null)
            return mCard;
        ViewGroup host = mHost.findView(R.id.layout_editor_host);
        if (host == null)
            return null;
        if (mScrim == null)
            mScrim = addScrim(host);
        View root = host.findViewById(R.id.layout_editor_card);
        if (root == null) {
            LayoutInflater.from(mHost.context()).inflate(R.layout.layout_editor, host, true);
            root = host.findViewById(R.id.layout_editor_card);
        }
        if (!(root instanceof EditorShellSheet))
            return null;
        Card card = new Card(host, (EditorShellSheet) root);
        if (!card.complete())
            return null;
        mCard = card;
        bind(card);
        return card;
    }

    private void bind(@NonNull Card card) {
        card.root.setBackground(cardBackground());
        EditorShellPaint.applyCardElevation(card.root,
            mHost.context().getResources().getDisplayMetrics().density);
        // Which editor this is. The place under it is what the card is pointed at.
        EditorShellHeader.applyEyebrow(card.header, R.string.termux_layout_editor_title);
        EditorShellHeader.applyDoneGlyph(card.done,
            androidx.core.content.ContextCompat.getDrawable(
                mHost.context(), R.drawable.ic_symbol_check),
            mHost.themeColor(com.termux.shared.R.attr.termuxColorOnPrimary,
                R.color.termux_on_primary));
        card.revert.setContentDescription(
            mHost.context().getString(R.string.termux_layout_editor_revert));
        card.handle.setBackground(EditorShellPaint.handleBar(
            mHost.themeColor(com.termux.shared.R.attr.termuxColorOnSurface,
                R.color.termux_on_surface),
            mHost.context().getResources().getDisplayMetrics().density));
        // The pull itself is the sheet's; what a pull below rest means is this editor's.
        card.root.setCallback(new EditorShellSheet.Callback() {
            @Override public void onSheetOvershoot(float overshootPx) {
                mOvershootPx = overshootPx;
                if (!mSheetAnimating)
                    applySheetProgress(card, mSheet.value);
            }

            @Override public boolean onSheetDismissRequested(float overshootPx) {
                return dismissFromPull(card, overshootPx);
            }
        });
        // A long list scrolls under the header with the shell's fade and its quiet scrollbar.
        EditorShellRows.applyBodyScroller(card.scroller);
        // The chooser is a pill sized to its own two words, not a control column stretched to
        // whatever the card had left.
        ViewGroup.LayoutParams pill = card.orientationHost.getLayoutParams();
        if (pill != null) {
            pill.width = EditorShellMetrics.px(2 * EditorShellMetrics.CHOOSER_SEGMENT_DP,
                mHost.context().getResources().getDisplayMetrics().density);
            card.orientationHost.setLayoutParams(pill);
        }
        // Layout has no way to save a look and no ✕: its ✓ is the only way out that keeps.
        card.orientationHost.setSegmentCount(card.orientation.getChildCount());

        card.revert.setOnClickListener(view -> revertToEntryState());
        card.discard.setOnClickListener(view -> {
            revertToEntryState();
            exit();
        });
        card.done.setOnClickListener(view -> exit());

        card.orientationNotice.setText(R.string.termux_layout_editor_other_orientation_notice);
        card.miniature.setLegendVisible(false);
        card.miniature.setOnBarDroppedListener(new PlaceMiniatureView.OnBarDroppedListener() {
            @Override
            public void onBarDropped(@NonNull PlaceMiniatureView.Block bar,
                                     @Nullable PlaceLayout.Edge edge, int index) {
                LayoutEditorController.this.onBarDropped(bar, edge, index, false);
            }

            @Override
            public void onBarDropped(@NonNull PlaceMiniatureView.Block bar,
                                     @Nullable PlaceLayout.Edge edge, int index,
                                     boolean underKeyboard) {
                LayoutEditorController.this.onBarDropped(bar, edge, index, underKeyboard);
            }
        });
        card.orientation.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || mRestatingToggle || mPlan == null)
                return;
            PlaceOrientation picked = checkedId == R.id.layout_editor_orientation_landscape
                ? PlaceOrientation.LANDSCAPE : PlaceOrientation.PORTRAIT;
            if (picked == mPlan.shownOrientation())
                return;
            mPlan.showOrientation(picked);
            sync();
        });
    }

    // ------------------------------------------------------------------------------- the canvas

    /**
     * A bar dropped into a gap in an edge's stack, or in the tray when {@code edge} is null. The
     * write lands on the orientation the miniature is showing; the live place is re-laid only when
     * that is the orientation the phone is in.
     */
    @VisibleForTesting
    void onBarDropped(@NonNull PlaceMiniatureView.Block block, @Nullable PlaceLayout.Edge edge,
                      int index) {
        onBarDropped(block, edge, index, false);
    }

    /** As above, on either side of the keyboard; {@code underKeyboard} is the far one. */
    @VisibleForTesting
    void onBarDropped(@NonNull PlaceMiniatureView.Block block, @Nullable PlaceLayout.Edge edge,
                      int index, boolean underKeyboard) {
        MiniatureDragPolicy.Bar bar = PlaceMiniatureView.barOf(block);
        if (mPlan == null || bar == null)
            return;
        if (mPlan.drop(bar, edge, index, underKeyboard) == LayoutEditorPlan.Drop.LIVE)
            mHost.applyPlaceArrangement();
        sync();
    }

    /** Re-reads the miniature, the toggle, the rows and the two unsaved glyphs from the plan. */
    private void sync() {
        Card card = mCard;
        LayoutEditorPlan plan = mPlan;
        if (card == null || plan == null)
            return;
        // The header names the editor; the one layout is every place's, which needs no saying.
        card.title.setText(R.string.termux_layout_editor_title);
        mRestatingToggle = true;
        card.orientation.check(plan.shownOrientation() == PlaceOrientation.LANDSCAPE
            ? R.id.layout_editor_orientation_landscape : R.id.layout_editor_orientation_portrait);
        mRestatingToggle = false;
        card.orientationNotice.setVisibility(
            plan.warnsOtherOrientation() ? View.VISIBLE : View.GONE);
        applyCanvasHeight(card, plan);
        card.miniature.setDockCornerRadiusDp(mHost.dockCornerRadiusDp());
        card.miniature.setLayout(plan.shownLayout(), plan.shownOrientation(), plan.place());
        syncNotice(card, plan);
        syncRows(card, plan);
        syncDirty(card, plan);
    }

    /**
     * The one slot below the miniature for what the current arrangement costs: a narrow canvas
     * from bars down the side of a portrait screen, and a status bar itself parked on a side,
     * where it never rests expanded. Neither blocks anything — the slot only says what applies,
     * and both can at once, so they share it rather than fighting over which shows.
     */
    private void syncNotice(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        boolean narrow = plan.warnsNarrowCanvas();
        boolean sideStatus = plan.warnsSideStatusBar();
        if (!narrow && !sideStatus) {
            card.narrowNotice.setVisibility(View.GONE);
            return;
        }
        Context context = mHost.context();
        StringBuilder text = new StringBuilder();
        if (narrow)
            text.append(context.getString(R.string.termux_layout_editor_narrow_notice));
        if (sideStatus) {
            if (text.length() > 0) text.append(' ');
            text.append(context.getString(R.string.termux_layout_editor_side_status_notice));
        }
        card.narrowNotice.setText(text);
        card.narrowNotice.setVisibility(View.VISIBLE);
    }

    /** How long the two unsaved glyphs take to arrive, rather than appearing between frames. */
    @VisibleForTesting static final long CHROME_FADE_MS = 150L;

    /** The revert glyph and Discard, which exist only while there is something to lose. */
    private void syncDirty(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        boolean dirty = plan.isDirty();
        fadeChrome(card.revert, dirty);
        fadeChrome(card.discard, dirty);
    }

    /**
     * One of the two unsaved glyphs. The first drop is what puts them on the header, and a glyph
     * that simply exists on the next frame reads as the header having changed shape; fading it in
     * reads as an answer to the drop. Going is immediate: there is nothing left to say.
     */
    private void fadeChrome(@NonNull View view, boolean shown) {
        if (!shown) {
            view.animate().cancel();
            view.setAlpha(1f);
            view.setVisibility(View.GONE);
            return;
        }
        // Already there, or already arriving: a second drop must not restart the fade under the
        // glyph the first one brought in.
        if (view.getVisibility() == View.VISIBLE)
            return;
        view.setVisibility(View.VISIBLE);
        if (ReducedMotion.isEnabled(mHost.context())) {
            view.setAlpha(1f);
            return;
        }
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(CHROME_FADE_MS).start();
    }

    /**
     * Sizes the canvas to the frame the shown orientation asks for, decides whether the rows stand
     * beside it or beneath it, and tells the sheet the two heights it stands at.
     *
     * <p>The canvas is sized once, from the resting card, and never from the pull: pulling the
     * sheet up shows more of the list, it does not reshape the picture in it. The frame is bounded
     * by the card's width — a landscape frame as wide as the screen in a card a row wide was a
     * frame with dead air above and below it — and by what the resting card leaves once its chrome
     * and a peek of the rows have theirs, so the first rows are in view before anything scrolls.
     *
     * <p>A portrait miniature on a landscape screen was a ~150px frame in a 1300px card with two
     * ~550px empty gutters around it. Beside it there is room for a whole row of controls, and the
     * frame gets the body's whole height instead.
     */
    private void applyCanvasHeight(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        DisplayMetrics metrics = mHost.context().getResources().getDisplayMetrics();
        float density = metrics.density;
        // What the card stands in: the room under the top inset pulled all the way up, and at rest
        // a sheet's share of a portrait screen, so the place it is a picture of stays visible
        // above it until asked; a landscape screen has no height to give away.
        int expandedPx = expandedBudgetPx(card, metrics);
        int restPx = LayoutEditorPlan.restingCardHeightPx(metrics.widthPixels,
            metrics.heightPixels, expandedPx);
        card.root.setHeights(restPx, expandedPx);
        // The header is sized from the height the card can stand in, the same question the
        // Appearance card asks: compact only where even the pulled-up card is short.
        EditorShellHeader.apply(card.header, expandedPx);

        int floorPx = Math.round(dpToPx(ROWS_PEEK_DP));
        int chooserPx = Math.max(card.orientationRow.getHeight(),
            Math.round(dpToPx(EditorShellMetrics.CHOOSER_DP)));
        int chromePx = cardChromePx(expandedPx, chooserPx,
            card.column.getPaddingTop() + card.column.getPaddingBottom(), noticeLines(plan),
            density);
        float frameAspect = PlaceMiniatureView.frameAspect(plan.shownOrientation());
        int reservedPx = Math.round(card.miniature.reservedHeightPx());

        int naturalPx = LayoutEditorPlan.miniatureNaturalWidthPx(plan.shownOrientation(),
            metrics.widthPixels, metrics.heightPixels, frameAspect);
        EditorShellMetrics.PaneSplit split = EditorShellMetrics.paneSplit(
            EditorShellMetrics.contentWidthPx(metrics.widthPixels, density), naturalPx, density);
        boolean twoPanes = rowsBesideMiniature(naturalPx, split, density);
        int columnPx = applyCardWidth(card, metrics.widthPixels, split, density, twoPanes);

        int height = twoPanes
            ? LayoutEditorPlan.miniatureHeightInPanePx(frameAspect, reservedPx,
                split.leadingWidthPx, Math.max(floorPx, restPx - chromePx))
            : LayoutEditorPlan.miniatureHeightPx(plan.shownOrientation(), metrics.heightPixels,
                frameAspect, reservedPx, columnPx, chromePx + floorPx, restPx);
        applyBodyPanes(card, split, twoPanes, height);
        ViewGroup.LayoutParams params = card.miniature.getLayoutParams();
        if (params == null || params.height == height)
            return;
        params.height = height;
        card.miniature.setLayoutParams(params);
    }

    /**
     * Whether the rows can stand beside the miniature rather than beneath it.
     *
     * <p>Two things have to be true: the frame has to fit in the leading pane at the width it
     * asked for — a frame squeezed into half the body is a frame that has been cut — and the
     * trailing pane has to hold a whole row. A landscape frame is as wide as the screen, so it
     * fails the first and the body falls back to one column, which is the case P1 bounds.
     */
    @VisibleForTesting
    static boolean rowsBesideMiniature(int naturalWidthPx,
                                       @NonNull EditorShellMetrics.PaneSplit split,
                                       float density) {
        if (split.paneCount < 2)
            return false;
        int asked = naturalWidthPx + EditorShellMetrics.px(
            EditorShellMetrics.LEADING_PANE_AIR_DP, density);
        return asked <= split.leadingWidthPx
            && split.trailingWidthPx >= EditorShellMetrics.px(
                EditorShellMetrics.ROW_MIN_INNER_DP, density);
    }

    /** Miniature and rows side by side, or one under the other. */
    private void applyBodyPanes(@NonNull Card card, @NonNull EditorShellMetrics.PaneSplit split,
                                boolean twoPanes, int miniatureHeightPx) {
        card.body.setOrientation(twoPanes
            ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        setPane(card.miniature, twoPanes ? split.leadingWidthPx
            : ViewGroup.LayoutParams.MATCH_PARENT, 0, twoPanes ? miniatureHeightPx : -1);
        setPane(card.rowsHost, twoPanes ? split.trailingWidthPx
            : ViewGroup.LayoutParams.MATCH_PARENT, twoPanes ? split.gutterPx : 0, -1);
    }

    private static void setPane(@NonNull View view, int widthPx, int startMarginPx,
                                int heightPx) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof LinearLayout.LayoutParams))
            return;
        LinearLayout.LayoutParams pane = (LinearLayout.LayoutParams) params;
        pane.width = widthPx;
        pane.setMarginStart(startMarginPx);
        if (heightPx >= 0)
            pane.height = heightPx;
        view.setLayoutParams(pane);
    }

    /**
     * The air the sheet keeps under its bottom edge: the shell's card margin, the same at every
     * side, which is what {@code layout_editor.xml} declares.
     */
    @VisibleForTesting static final int SHEET_BOTTOM_MARGIN_DP =
        EditorShellMetrics.CARD_SIDE_MARGIN_DP;

    /**
     * The card stops inheriting the screen's width. What is left over is symmetric air with the
     * live place showing through it, which is the thing the editor is a picture of.
     *
     * @return the width one column of the card's body has, which is what a canvas under the header
     *     can be as wide as
     */
    private int applyCardWidth(@NonNull Card card, int screenWidthPx,
                               @NonNull EditorShellMetrics.PaneSplit split, float density,
                               boolean twoPanes) {
        // One column wide enough for one whole row, or two panes and the gutter between them.
        EditorShellMetrics.PaneSplit shown = twoPanes ? split
            : EditorShellMetrics.paneSplit(Math.min(
                EditorShellMetrics.contentWidthPx(screenWidthPx, density),
                EditorShellMetrics.px(EditorShellMetrics.ROW_MAX_INNER_DP, density)), 0, density);
        int width = EditorShellMetrics.cardWidthPx(screenWidthPx, shown, density);
        ViewGroup.LayoutParams params = card.root.getLayoutParams();
        if (params != null && params.width != width) {
            params.width = width;
            if (params instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams frame = (FrameLayout.LayoutParams) params;
                frame.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            }
            card.root.setLayoutParams(params);
        }
        return Math.max(0, width - card.root.getPaddingStart() - card.root.getPaddingEnd());
    }

    // --------------------------------------------------------------------------------- the rows

    /** The four segment slots a pill row declares; the ones a value set does not use come off. */
    private static final int[] SEGMENT_IDS = {
        R.id.editor_shell_row_segment_0, R.id.editor_shell_row_segment_1,
        R.id.editor_shell_row_segment_2, R.id.editor_shell_row_segment_3};

    /** The gaps the miniature stands between: above it, and above the rows under it. */
    @VisibleForTesting static final float GAPS_DP = 16f;
    /** One line of notice under the chooser, and the air above it. */
    @VisibleForTesting static final float NOTICE_LINE_DP = 22f;

    /**
     * Everything on the resting card that is not the canvas or the rows: the handle, the header,
     * the chooser, the gaps and the notices.
     *
     * <p>Declared rather than derived, so the canvas's size is a question about the arrangement
     * and the card's height and never about a view measured in the pass that is sizing it. The
     * notices are counted rather than allowed for: reserving two lines that are usually not there
     * costs the picture the room it is owed.
     *
     * @param noticeLines how many lines of notice the plan says apply
     */
    @VisibleForTesting
    static int cardChromePx(int cardHeightPx, int chooserPx, int paddingPx, int noticeLines,
                            float density) {
        return EditorShellMetrics.headerHeightPx(cardHeightPx, density) + chooserPx + paddingPx
            + EditorShellMetrics.px(EditorShellPaint.HANDLE_SLOT_HEIGHT_DP + GAPS_DP, density)
            + (Math.max(0, noticeLines) * EditorShellMetrics.px(NOTICE_LINE_DP, density));
    }

    /** How many lines of notice stand under the chooser for this arrangement. */
    @VisibleForTesting
    static int noticeLines(@NonNull LayoutEditorPlan plan) {
        int lines = plan.warnsOtherOrientation() ? 1 : 0;
        if (plan.warnsNarrowCanvas())
            lines++;
        if (plan.warnsSideStatusBar())
            lines++;
        return lines;
    }

    /**
     * How much of the rows the resting card keeps in view under the canvas, however tall the
     * canvas would like to be: the list's first rows are what say there is more to scroll to.
     */
    @VisibleForTesting static final float ROWS_PEEK_DP = 96f;

    /**
     * The rows for the place and orientation on show. They are rebuilt only when one of those two
     * moves — a pick changes what a row says, not which rows there are — and restated from the
     * store every time anything else might have.
     */
    private void syncRows(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        String key = plan.place().name() + '.' + plan.shownOrientation().name() + '.'
            + plan.rows().size();
        if (!key.equals(mRowsKey)) {
            rebuildRows(card, plan);
            mRowsKey = key;
        }
        for (Runnable sync : mRowSyncs) sync.run();
    }

    private void rebuildRows(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        LinearLayout rows = card.rowsHost;
        rows.removeAllViews();
        mRowSyncs.clear();
        // The tracks being replaced are gone, finger or no finger, and the reference would outlive
        // the view.
        mDraggedSlider = null;
        Context context = mHost.context();
        Element heading = null;
        for (LayoutEditorPlan.Row row : plan.rows()) {
            if (row.element != heading) {
                EditorShellRows.addSection(context, rows, headingRes(row.element),
                    heading == null);
                heading = row.element;
            }
            if (row.group instanceof PlaceArrangeModel.Pills)
                addPillsRow(context, rows, row, (PlaceArrangeModel.Pills) row.group);
            else if (row.group instanceof PlaceArrangeModel.Track)
                addTrackRow(context, rows, row, (PlaceArrangeModel.Track) row.group);
        }
        restoreScroll(card, plan);
    }

    /**
     * Where each place-and-orientation was scrolled to. Flipping the toggle and flipping it back
     * comes back to where the user was, rather than to the top of a list they had scrolled past.
     */
    private final Map<String, Integer> mPanelScroll = new LinkedHashMap<>();
    /** Which place-and-orientation the list is scrolled for, so the outgoing one is kept. */
    @Nullable private String mScrollKey;

    private void restoreScroll(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        NestedScrollView scroller = card.scroller;
        if (mScrollKey != null)
            mPanelScroll.put(mScrollKey, scroller.getScrollY());
        String key = plan.place().name() + '.' + plan.shownOrientation().name();
        mScrollKey = key;
        Integer remembered = mPanelScroll.get(key);
        int target = remembered == null ? 0 : remembered;
        scroller.scrollTo(0, 0);
        if (target > 0)
            scroller.post(() -> scroller.scrollTo(0, target));
    }

    @StringRes
    private static int headingRes(@NonNull Element element) {
        switch (element) {
            case PINNED_APPS:
                return R.string.termux_surface_tuning_dock;
            case AZ_INDEX:
                return R.string.settings_layout_miniature_alphabets;
            case WIDGET_GRID:
                return R.string.settings_layout_widget_grid_title;
            default:
                return R.string.settings_layout_keyboard_title;
        }
    }

    /**
     * One pick row. Both the pick and the restatement re-read the plan rather than trusting the
     * group the row was built from: a rotation, or the revert, can move what this row is showing
     * without the row itself being rebuilt.
     */
    private void addPillsRow(@NonNull Context context, @NonNull ViewGroup into,
                             @NonNull LayoutEditorPlan.Row row,
                             @NonNull PlaceArrangeModel.Pills pills) {
        View view = LayoutInflater.from(context)
            .inflate(R.layout.editor_shell_pills_row, into, false);
        EditorShellRows.apply(view);
        ((TextView) view.findViewById(R.id.editor_shell_row_label)).setText(pills.labelRes);
        MaterialButtonToggleGroup group = view.findViewById(R.id.editor_shell_row_pills);
        if (group == null)
            return;
        final int count = Math.min(pills.values.length, SEGMENT_IDS.length);
        for (int i = SEGMENT_IDS.length - 1; i >= count; i--) {
            View extra = view.findViewById(SEGMENT_IDS[i]);
            if (extra != null) group.removeView(extra);
        }
        // After the unused slots come off, so the widths are for the segments actually offered.
        EditorShellControlHost host = view.findViewById(R.id.editor_shell_row_control);
        if (host != null)
            host.setSegmentCount(count);
        for (int i = 0; i < count; i++) {
            Button segment = view.findViewById(SEGMENT_IDS[i]);
            if (segment != null) segment.setText(pills.labelResIds[i]);
        }
        group.setContentDescription(context.getString(pills.labelRes));
        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (!isChecked || mRestatingToggle)
                return;
            PlaceArrangeModel.Pills current = rowPills(row);
            int picked = indexOfSegment(checkedId);
            if (current == null || picked < 0 || picked >= current.values.length)
                return;
            if (current.values[picked].equals(current.selected))
                return;
            current.writer.write(current.values[picked]);
            afterRowWrite(row);
        });
        mRowSyncs.add(() -> {
            PlaceArrangeModel.Pills current = rowPills(row);
            if (current == null)
                return;
            int selected = current.selectedIndex();
            int wanted = selected < 0 || selected >= count ? View.NO_ID : SEGMENT_IDS[selected];
            if (group.getCheckedButtonId() == wanted)
                return;
            mRestatingToggle = true;
            try {
                if (wanted == View.NO_ID) group.clearChecked();
                else group.check(wanted);
            } finally {
                mRestatingToggle = false;
            }
        });
        into.addView(view);
        mRowSyncs.get(mRowSyncs.size() - 1).run();
    }

    /**
     * One number on a track: the widget grid's two counts, and the three sizes. Both kinds are the
     * same row — a label, a track and a number in its own unit — and both write through on every
     * tick, so the live place follows a finger that is still moving.
     */
    private void addTrackRow(@NonNull Context context, @NonNull ViewGroup into,
                             @NonNull LayoutEditorPlan.Row row,
                             @NonNull PlaceArrangeModel.Track track) {
        View view = LayoutInflater.from(context)
            .inflate(R.layout.editor_shell_row, into, false);
        EditorShellRows.apply(view);
        ((TextView) view.findViewById(R.id.editor_shell_row_label)).setText(track.labelRes);
        SeekBar slider = view.findViewById(R.id.editor_shell_row_slider);
        TextView value = view.findViewById(R.id.editor_shell_row_value);
        slider.setContentDescription(context.getString(track.labelRes));
        slider.setMax(Math.max(1, track.max - track.min));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                PlaceArrangeModel.Track current = rowTrack(row);
                if (current == null)
                    return;
                int picked = current.min + progress;
                value.setText(trackValueText(current, picked));
                if (!fromUser || picked == current.value)
                    return;
                // One re-lay per tick and nothing else: the rows are left standing while the thumb
                // is down, so the one being dragged is not rebuilt out from under it.
                current.writer.write(picked);
                afterRowWrite(row);
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {
                mDraggedSlider = bar;
            }

            @Override public void onStopTrackingTouch(SeekBar bar) {
                mDraggedSlider = null;
            }
        });
        mRowSyncs.add(() -> {
            PlaceArrangeModel.Track current = rowTrack(row);
            if (current == null)
                return;
            int progress = Math.max(0, Math.min(slider.getMax(), current.value - current.min));
            // A size read back through the store's clamp can land a step off what the finger asked
            // for; moving the thumb there under the finger would fight the drag.
            if (slider.getProgress() != progress && mDraggedSlider != slider)
                slider.setProgress(progress);
            value.setText(trackValueText(current, current.value));
        });
        into.addView(view);
        mRowSyncs.get(mRowSyncs.size() - 1).run();
    }

    /** One track's number in its own unit: a bare count, a step of its range, or a length. */
    @NonNull
    private String trackValueText(@NonNull PlaceArrangeModel.Track track, int value) {
        switch (track.unit) {
            case PERCENT:
                return mHost.context().getString(
                    R.string.termux_dock_tuning_value_percent, value);
            case DP:
                return mHost.context().getString(R.string.termux_dock_tuning_value_dp, value);
            default:
                return Integer.toString(value);
        }
    }

    /**
     * A row wrote through: the place behind the card follows when the orientation on the
     * miniature is the one on screen, or when the row is one both orientations share — the
     * keyboard's on/off switch, which puts the keyboard down on that pass.
     */
    private void afterRowWrite(@NonNull LayoutEditorPlan.Row row) {
        Card card = mCard;
        LayoutEditorPlan plan = mPlan;
        if (card == null || plan == null)
            return;
        if (plan.follows(row)) mHost.applyPlaceArrangement();
        // A row can change the picture (the keyboard's block, the A–Z index's tab) and the rows
        // offered (Off takes the A–Z index's Position away), so both are re-read. The rebuild is
        // posted: the toggle group that was picked on is still dispatching its listener.
        card.miniature.setLayout(plan.shownLayout(), plan.shownOrientation(), plan.place());
        for (Runnable sync : mRowSyncs) sync.run();
        card.root.post(() -> {
            if (mCard == card && mPlan == plan) syncRows(card, plan);
        });
        syncDirty(card, plan);
    }

    /** What one row says right now, read fresh, or null where the place no longer offers it. */
    @Nullable
    private PlaceArrangeModel.Pills rowPills(@NonNull LayoutEditorPlan.Row row) {
        PlaceArrangeModel.Group group = mPlan == null ? null : mPlan.row(row.element, row.index);
        return group instanceof PlaceArrangeModel.Pills ? (PlaceArrangeModel.Pills) group : null;
    }

    @Nullable
    private PlaceArrangeModel.Track rowTrack(@NonNull LayoutEditorPlan.Row row) {
        PlaceArrangeModel.Group group = mPlan == null ? null : mPlan.row(row.element, row.index);
        return group instanceof PlaceArrangeModel.Track ? (PlaceArrangeModel.Track) group : null;
    }

    /** Which segment an id is, or -1 for anything that is not one of the four. */
    private static int indexOfSegment(int viewId) {
        for (int i = 0; i < SEGMENT_IDS.length; i++) {
            if (SEGMENT_IDS[i] == viewId) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------------- unsaved and exit

    private void revertToEntryState() {
        if (mPlan == null)
            return;
        mPlan.revert();
        // The bars have to be back on their edges before the chrome is re-read.
        mHost.applyPlaceArrangement();
        sync();
    }

    /**
     * The back press. ✓ keeps the edits, and when there is something to lose this asks rather than
     * silently choosing for the user: every drop is already written through, so leaving would
     * otherwise mean keeping by accident.
     */
    public void requestClose() {
        if (mPlan == null)
            return;
        if (!mPlan.isDirty()) {
            exit();
            return;
        }
        new MaterialAlertDialogBuilder(mHost.context())
            .setTitle(R.string.termux_surface_tuning_unsaved_title)
            .setMessage(R.string.termux_layout_editor_unsaved_message)
            .setNeutralButton(R.string.termux_surface_tuning_unsaved_keep_editing, null)
            .setNegativeButton(R.string.termux_surface_tuning_unsaved_discard,
                (dialog, which) -> {
                    revertToEntryState();
                    exit();
                })
            .setPositiveButton(R.string.termux_surface_tuning_unsaved_save,
                (dialog, which) -> exit())
            .show();
    }

    /** Leaving from outside a Back press — a HOME press — takes the same route, dirty or not. */
    public void requestExit() {
        requestClose();
    }

    /** The phone turned: the miniature goes with it, and so does what the next drop writes. */
    public void onPlaceOrientationChanged() {
        Card card = mCard;
        if (mPlan == null || card == null)
            return;
        // A rotation is delivered before the window is re-laid out, so the display metrics the
        // canvas is sized from are still the old orientation's until the next pass.
        card.host.post(() -> {
            if (mPlan == null)
                return;
            mPlan.onDeviceOrientationChanged(mHost.placeOrientation());
            sync();
        });
    }

    @VisibleForTesting
    void exit() {
        if (mPlan == null && !mShowing)
            return;
        PaneWallPage place = mPlan == null ? null : mPlan.place();
        mPlan = null;
        mRowsKey = null;
        mDraggedSlider = null;
        mShowing = false;
        if (mCard != null) {
            fadeChrome(mCard.revert, false);
            fadeChrome(mCard.discard, false);
            // The card is leaving: from here the touches are the live place's again, which is
            // where they went the moment the host disappeared before there was an animation.
            mCard.host.setClickable(false);
            mCard.host.setFocusable(false);
            startSheet(mCard);
        }
        if (place != null) mHost.holdPaneWallOnPlace(place, false);
    }

    // -------------------------------------------------------------------------------- the motion

    /** How black the wash over the live place goes while the sheet is up. */
    @VisibleForTesting static final float SCRIM_ALPHA = 0.28f;

    /**
     * The wash between the live place and the card. It is a plain fill and never a blur: what is
     * behind it is the thing being edited, and the point is to read the card against it, not to
     * take the place away.
     *
     * <p>It takes no touches of its own, so what the host did with a touch beside the card before
     * there was a scrim is what it still does.
     */
    @NonNull
    private View addScrim(@NonNull ViewGroup host) {
        View scrim = new View(mHost.context());
        scrim.setBackgroundColor(Color.BLACK);
        scrim.setAlpha(0f);
        scrim.setClickable(false);
        scrim.setFocusable(false);
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        host.addView(scrim, 0, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return scrim;
    }

    /**
     * Runs the sheet towards wherever {@link #mShowing} says it belongs — up from the bottom edge,
     * or back down past it — on the launcher's own spring. With the phone told to play no
     * animations it is simply there, or simply gone.
     */
    private void startSheet(@NonNull Card card) {
        // An open that is not interrupting a close starts from below the bottom edge; one that is
        // turns round from wherever the card had got to.
        if (mShowing && !mSheetAnimating)
            mSheet.reset(1f);
        mSheet.target = mShowing ? 0f : 1f;
        // Whatever was in flight is stale: one channel, one loop, and a frame that never ran —
        // the card was detached mid-close — must not leave the next open with nothing driving it.
        card.root.removeCallbacks(mSheetFrame);
        mSheetAnimating = false;
        if (ReducedMotion.isEnabled(mHost.context())) {
            mSheet.reset(mSheet.target);
            applySheetProgress(card, mSheet.value);
            if (!mShowing)
                hideCard(card);
            return;
        }
        applySheetProgress(card, mSheet.value);
        mSheetAnimating = true;
        mSheetLastFrameNanos = 0L;
        card.root.postOnAnimation(mSheetFrame);
    }

    private final Runnable mSheetFrame = new Runnable() {
        @Override
        public void run() {
            Card card = mCard;
            if (!mSheetAnimating || card == null)
                return;
            long now = System.nanoTime();
            float dt = mSheetLastFrameNanos == 0L ? Spring.MIN_DT
                : Spring.clampDelta((now - mSheetLastFrameNanos) / 1_000_000_000f);
            mSheetLastFrameNanos = now;
            boolean moving = mSheet.tick(false, dt);
            applySheetProgress(card, mSheet.value);
            if (moving) {
                card.root.postOnAnimation(this);
                return;
            }
            mSheetAnimating = false;
            if (!mShowing)
                hideCard(card);
        }
    };

    /**
     * How far the card travels between in place and parked below the bottom edge: its own height
     * and the margin under it, or the screen's while it has not been laid out yet — which is only
     * ever the first frame of the first open, and off screen either way.
     */
    private float slideTravelPx(@NonNull Card card) {
        return card.root.getHeight() > 0
            ? card.root.getHeight() + dpToPx(SHEET_BOTTOM_MARGIN_DP)
            : mHost.context().getResources().getDisplayMetrics().heightPixels;
    }

    /**
     * The card at one point of its travel: 1 is parked below the bottom edge, 0 is in place, and a
     * pull below rest adds its own push on top. The wash behind the card fades with both.
     */
    private void applySheetProgress(@NonNull Card card, float progress) {
        float at = Math.max(0f, Math.min(1f, progress));
        float travel = slideTravelPx(card);
        float translation = Math.min(travel, at * travel + Math.max(0f, mOvershootPx));
        card.root.setTranslationY(translation);
        if (mScrim != null)
            mScrim.setAlpha((1f - translation / Math.max(1f, travel)) * SCRIM_ALPHA);
    }

    /** The card has finished leaving. */
    private void hideCard(@NonNull Card card) {
        card.host.setVisibility(View.GONE);
        card.root.setTranslationY(0f);
        if (mScrim != null)
            mScrim.setAlpha(0f);
    }

    /**
     * A pull far enough below rest to close: the Back press. With nothing to lose the editor
     * closes at once, the card carrying on down from where the finger let it go; with something to
     * lose the question is asked, and the sheet brings the card back to rest under it rather than
     * leaving it hanging half off the screen.
     */
    private boolean dismissFromPull(@NonNull Card card, float overshootPx) {
        LayoutEditorPlan plan = mPlan;
        if (plan == null)
            return false;
        if (plan.isDirty()) {
            requestClose();
            return false;
        }
        mSheet.reset(Math.min(1f, Math.max(0f, overshootPx) / slideTravelPx(card)));
        mOvershootPx = 0f;
        exit();
        return true;
    }

    // -------------------------------------------------------------------------------- the sheet

    /** The air the sheet keeps between its top and the top inset when pulled all the way up. */
    @VisibleForTesting static final int SHEET_TOP_AIR_DP = 8;

    /**
     * The card's expanded height: what the host has under the top inset, less the air the sheet
     * keeps up there and its own bottom margin. Read off the host rather than the screen, because
     * the host is what the card actually stands in; where it reaches under the system's status
     * bar, that much is taken back off.
     */
    private int expandedBudgetPx(@NonNull Card card, @NonNull DisplayMetrics metrics) {
        int hostPx = card.host.getHeight() > 0 ? card.host.getHeight() : metrics.heightPixels;
        int[] location = new int[2];
        card.host.getLocationInWindow(location);
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(card.host);
        int statusTop = insets == null ? 0
            : insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        int underInset = Math.max(0, statusTop - location[1]);
        return LayoutEditorPlan.expandedCardBudgetPx(hostPx - underInset - EditorShellMetrics.px(
            SHEET_TOP_AIR_DP + SHEET_BOTTOM_MARGIN_DP, metrics.density));
    }

    // -------------------------------------------------------------------------------- the chrome

    @NonNull
    private Drawable cardBackground() {
        return EditorShellPaint.cardBackground(
            mHost.themeColor(com.termux.shared.R.attr.termuxColorSurfaceBase,
                R.color.termux_surface_base),
            mHost.themeColor(com.termux.shared.R.attr.termuxColorOnSurface,
                R.color.termux_on_surface),
            mHost.context().getResources().getDisplayMetrics().density);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
            mHost.context().getResources().getDisplayMetrics());
    }
}
