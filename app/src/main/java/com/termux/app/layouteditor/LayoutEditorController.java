package com.termux.app.layouteditor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
import com.termux.app.fragments.settings.LayoutCanvasView;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Layout editor: the layout canvas of the place the user is looking at, parked over the live
 * place, with a Portrait / Landscape toggle above it. The layout it edits is every place's (ADR
 * 0003), so it has no place to pick: a write lands on Home, the terminal and the display alike.
 *
 * <p>The sibling of the surface editor and not a page of it: this one answers where a place's
 * elements sit, that one answers how its surfaces look, and only ever one of the two is open.
 *
 * <p>The canvas is the whole editor (spec §3.5); there are no rows under it. A bar is dragged by
 * its grip to an edge, or into the tray under the phone to put it away. A tap selects an element:
 * one outline, and on the dock, the keyboard and Home's grid a handle that resizes it — the dock's
 * height, the keyboard's height and its chin, the grid's cells — with a readout in real units
 * while it is held. The keyboard, selected, shows its three type chips beside it, and is dropped
 * in the tray to switch it off. The tray lists every hidden element as a chip that brings it back
 * to the edge it left (§3.6). Every write goes straight through, so the place behind the card
 * follows every edit made in the orientation the phone is actually in; the other orientation moves
 * on the canvas alone until the phone is turned.
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
 * <p>Every decision here — what is shown, what a write does, whether the place follows, whether
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
            return LayoutCanvasView.DEFAULT_DOCK_RADIUS_DP;
        }

        /**
         * How thick the live dock's pinned apps stand, in dp, as last laid out — or -1 when there
         * is no dock on screen to measure. What the dock's handle reads out.
         */
        default int measuredDockHeightDp() {
            return -1;
        }

        /**
         * How tall the live keyboard stands, in dp, as last laid out — or -1 while it is down.
         * What the keyboard's height handle reads out.
         */
        default int measuredKeyboardHeightDp() {
            return -1;
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
        final TextView narrowNotice;
        /** The canvas and what stands over it: the tray's chips and the keyboard's type chips. */
        final FrameLayout canvasHost;
        final LayoutCanvasView miniature;
        final View tray;
        final View trayScroller;
        final ChipGroup trayChips;
        final TextView trayEmpty;
        final ChipGroup keyboardForms;

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
            narrowNotice = root.findViewById(R.id.layout_editor_narrow_notice);
            canvasHost = root.findViewById(R.id.layout_editor_canvas_host);
            miniature = root.findViewById(R.id.layout_editor_miniature);
            tray = root.findViewById(R.id.layout_editor_tray);
            trayScroller = root.findViewById(R.id.layout_editor_tray_scroller);
            trayChips = root.findViewById(R.id.layout_editor_tray_chips);
            trayEmpty = root.findViewById(R.id.layout_editor_tray_empty);
            keyboardForms = root.findViewById(R.id.layout_editor_keyboard_forms);
        }

        boolean complete() {
            return scroller != null && column != null && handle != null && header != null
                && title != null
                && revert != null && discard != null
                && done != null && chooserSlot != null && orientationRow != null
                && orientationHost != null && orientation != null && orientationNotice != null
                && narrowNotice != null && canvasHost != null && miniature != null
                && tray != null && trayScroller != null && trayChips != null && trayEmpty != null
                && keyboardForms != null;
        }
    }

    /** Each orientation segment's width: a glyph and its air, no word (spec §3.5). */
    @VisibleForTesting static final int ORIENTATION_SEGMENT_DP = 64;

    @Nullable private Card mCard;
    @Nullable private LayoutEditorPlan mPlan;
    /** True while a toggle or a chip group is being restated from the plan, so it writes nothing. */
    private boolean mRestatingToggle;
    /** The handle a finger is on, whose readout a late measurement may restate; or null. */
    @Nullable private LayoutCanvasView.Handle mHeldHandle;
    /** What the tray's chips were built for, so they are rebuilt only when that changes. */
    @NonNull private List<LayoutEditorPlan.TrayItem> mTrayShown = Collections.emptyList();
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
        // Which editor this is; the title under it says which layout, so the two never repeat.
        EditorShellHeader.applyEyebrow(card.header, R.string.termux_layout_editor_eyebrow);
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
        // The chooser is a pill of two glyphs, not a control column stretched to whatever the card
        // had left.
        ViewGroup.LayoutParams pill = card.orientationHost.getLayoutParams();
        if (pill != null) {
            pill.width = EditorShellMetrics.px(2 * ORIENTATION_SEGMENT_DP,
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
        card.miniature.setOnBarDroppedListener(new LayoutCanvasView.OnBarDroppedListener() {
            @Override
            public void onBarDropped(@NonNull LayoutCanvasView.Block bar,
                                     @Nullable PlaceLayout.Edge edge, int index) {
                LayoutEditorController.this.onBarDropped(bar, edge, index, false);
            }

            @Override
            public void onBarDropped(@NonNull LayoutCanvasView.Block bar,
                                     @Nullable PlaceLayout.Edge edge, int index,
                                     boolean underKeyboard) {
                LayoutEditorController.this.onBarDropped(bar, edge, index, underKeyboard);
            }
        });
        card.miniature.setOnCanvasEditListener(new LayoutCanvasView.OnCanvasEditListener() {
            @Override public void onSelectionChanged(@Nullable LayoutCanvasView.Block selected) {
                syncKeyboardForms(card);
            }

            @Override public void onDockHeightDragged(float scale) {
                if (mPlan != null)
                    onHandleWrite(LayoutCanvasView.Handle.DOCK_HEIGHT,
                        mPlan.setDockHeightScale(scale));
            }

            @Override public void onKeyboardHeightDragged(float scale) {
                if (mPlan != null)
                    onHandleWrite(LayoutCanvasView.Handle.KEYBOARD_HEIGHT,
                        mPlan.setKeyboardHeightScale(scale));
            }

            @Override public void onKeyboardChinDragged(int dp) {
                if (mPlan != null)
                    onHandleWrite(LayoutCanvasView.Handle.KEYBOARD_CHIN,
                        mPlan.setKeyboardChinDp(dp));
            }

            @Override public void onWidgetGridDragged(int columns, int rows) {
                if (mPlan != null)
                    onHandleWrite(LayoutCanvasView.Handle.WIDGET_GRID,
                        mPlan.setWidgetGrid(columns, rows));
            }

            @Override public void onHandleReleased() {
                mHeldHandle = null;
                card.miniature.setHandleReadout(null);
                if (mPlan != null)
                    syncDirty(card, mPlan);
            }

            @Override public void onKeyboardPutAway() {
                if (mPlan != null)
                    afterCanvasWrite(mPlan.setKeyboardShown(false));
            }
        });
        card.keyboardForms.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty())
                onKeyboardFormPicked(checkedIds.get(0));
        });
        card.keyboardForms.setContentDescription(
            mHost.context().getString(R.string.layout_editor_keyboard_forms));
        // The type chips stand beside the keyboard, which moves whenever the canvas is laid out
        // again: a new size, a new orientation, a keyboard grown by its handle. Posted, since the
        // chips' own layout params may change and a layout pass is no place to ask for another.
        card.miniature.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft,
                                                  oldTop, oldRight, oldBottom) ->
            card.miniature.post(() -> {
                if (mCard == card)
                    syncKeyboardForms(card);
            }));
        card.orientation.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || mRestatingToggle || mPlan == null)
                return;
            PlaceOrientation picked = checkedId == R.id.layout_editor_orientation_landscape
                ? PlaceOrientation.LANDSCAPE : PlaceOrientation.PORTRAIT;
            if (picked == mPlan.shownOrientation())
                return;
            mPlan.showOrientation(picked);
            // The selection was on the other orientation's picture.
            card.miniature.setSelectedBlock(null);
            sync();
        });
    }

    // ------------------------------------------------------------------------------- the drops

    /**
     * A bar dropped into a gap in an edge's stack, or in the tray when {@code edge} is null. The
     * write lands on the orientation the miniature is showing; the live place is re-laid only when
     * that is the orientation the phone is in.
     */
    @VisibleForTesting
    void onBarDropped(@NonNull LayoutCanvasView.Block block, @Nullable PlaceLayout.Edge edge,
                      int index) {
        onBarDropped(block, edge, index, false);
    }

    /** As above, on either side of the keyboard; {@code underKeyboard} is the far one. */
    @VisibleForTesting
    void onBarDropped(@NonNull LayoutCanvasView.Block block, @Nullable PlaceLayout.Edge edge,
                      int index, boolean underKeyboard) {
        MiniatureDragPolicy.Bar bar = LayoutCanvasView.barOf(block);
        if (mPlan == null || bar == null)
            return;
        if (mPlan.drop(bar, edge, index, underKeyboard) == LayoutEditorPlan.Drop.LIVE)
            mHost.applyPlaceArrangement();
        sync();
    }

    /** Re-reads the canvas, the toggle, the tray and the two unsaved glyphs from the plan. */
    private void sync() {
        Card card = mCard;
        LayoutEditorPlan plan = mPlan;
        if (card == null || plan == null)
            return;
        // The header names the editor; the layout is every place's, which needs no saying, but
        // which of the two it is does: the minimal one is edited while minimal mode is on.
        card.title.setText(plan.variant() == LayoutVariant.MINIMAL
            ? R.string.termux_layout_editor_title_minimal : R.string.termux_layout_editor_title);
        mRestatingToggle = true;
        card.orientation.check(plan.shownOrientation() == PlaceOrientation.LANDSCAPE
            ? R.id.layout_editor_orientation_landscape : R.id.layout_editor_orientation_portrait);
        mRestatingToggle = false;
        card.orientationNotice.setVisibility(
            plan.warnsOtherOrientation() ? View.VISIBLE : View.GONE);
        applyCanvasHeight(card, plan);
        card.miniature.setDockCornerRadiusDp(mHost.dockCornerRadiusDp());
        card.miniature.setSizes(plan.dockHeightScale(), plan.keyboardHeightScale(),
            plan.keyboardChinDp());
        card.miniature.setLayout(plan.shownLayout(), plan.shownOrientation(), plan.place());
        syncNotice(card, plan);
        syncTray(card, plan);
        syncKeyboardForms(card);
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
     * Sizes the canvas to the frame the shown orientation asks for and tells the sheet the two
     * heights it stands at.
     *
     * <p>The canvas is sized once, from the resting card, and never from the pull: pulling the
     * sheet up shows more of the list, it does not reshape the picture in it. The frame is bounded
     * by the card's width — a landscape frame as wide as the screen in a card a row wide was a
     * frame with dead air above and below it — and by what the resting card leaves once its chrome
     * has its share. There are no rows under the canvas any more (spec §3.5), so it keeps no peek
     * of them either, and it never shares the body with a pane beside it.
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

        int chooserPx = Math.max(card.orientationRow.getHeight(),
            Math.round(dpToPx(EditorShellMetrics.CHOOSER_DP)));
        int chromePx = cardChromePx(expandedPx, chooserPx,
            card.column.getPaddingTop() + card.column.getPaddingBottom(), noticeLines(plan),
            density);
        float frameAspect = LayoutCanvasView.frameAspect(plan.shownOrientation());
        int reservedPx = Math.round(card.miniature.reservedHeightPx());
        int columnPx = applyCardWidth(card, metrics.widthPixels, density);
        int height = LayoutEditorPlan.miniatureHeightPx(plan.shownOrientation(),
            metrics.heightPixels, frameAspect, reservedPx, columnPx, chromePx, restPx);
        ViewGroup.LayoutParams params = card.miniature.getLayoutParams();
        if (params == null || params.height == height)
            return;
        params.height = height;
        card.miniature.setLayoutParams(params);
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
     * @return the width the card's column has, which is what the canvas can be as wide as
     */
    private int applyCardWidth(@NonNull Card card, int screenWidthPx, float density) {
        // One column, as wide as the shell's widest row would be.
        EditorShellMetrics.PaneSplit shown = EditorShellMetrics.paneSplit(Math.min(
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
    // ------------------------------------------------------------------------ the card's chrome

    /** The gaps the canvas stands between: above it, and above the tray under it. */
    @VisibleForTesting static final float GAPS_DP = 16f;
    /** One line of notice under the chooser, and the air above it. */
    @VisibleForTesting static final float NOTICE_LINE_DP = 22f;

    /**
     * Everything on the resting card that is not the canvas: the handle, the header, the chooser,
     * the gaps and the notices.
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

    // ------------------------------------------------------------------------------ the canvas

    /**
     * A write from the canvas landed: the live place follows it when it was written for the
     * orientation on screen — or, for the keyboard's switch, always — and the canvas, the tray and
     * the header are read again. A write that changed nothing does neither.
     */
    private void afterCanvasWrite(@NonNull LayoutEditorPlan.Drop drop) {
        if (drop == LayoutEditorPlan.Drop.NONE)
            return;
        if (drop == LayoutEditorPlan.Drop.LIVE)
            mHost.applyPlaceArrangement();
        sync();
    }

    /**
     * A handle moved: the value written through, and its readout restated. The readout's dp come
     * from the live launcher once it has laid the new size out, so it is restated once more on the
     * next frame.
     */
    private void onHandleWrite(@NonNull LayoutCanvasView.Handle handle,
                               @NonNull LayoutEditorPlan.Drop drop) {
        mHeldHandle = handle;
        afterCanvasWrite(drop);
        Card card = mCard;
        if (card == null)
            return;
        card.miniature.setHandleReadout(readoutFor(handle));
        card.root.post(() -> {
            if (mCard == card && mHeldHandle == handle)
                card.miniature.setHandleReadout(readoutFor(handle));
        });
    }

    /**
     * What a held handle reads out, in the store's real units (spec §3.5: no bare percents). The
     * dock and the keyboard are measured on the live launcher while the canvas shows the phone's
     * own orientation; for the other one there is nothing live to measure, and the readout says
     * the multiple of the unscaled height the store keeps.
     */
    @Nullable
    private String readoutFor(@NonNull LayoutCanvasView.Handle handle) {
        LayoutEditorPlan plan = mPlan;
        if (plan == null)
            return null;
        Context context = mHost.context();
        switch (handle) {
            case DOCK_HEIGHT: {
                int dp = plan.liveFollows() ? mHost.measuredDockHeightDp() : -1;
                return dp > 0
                    ? context.getString(R.string.layout_editor_readout_dock_dp, dp)
                    : context.getString(R.string.layout_editor_readout_dock_scale,
                        scaleText(plan.dockHeightScale()));
            }
            case KEYBOARD_HEIGHT: {
                int dp = plan.liveFollows() ? mHost.measuredKeyboardHeightDp() : -1;
                return dp > 0
                    ? context.getString(R.string.layout_editor_readout_keyboard_dp, dp)
                    : context.getString(R.string.layout_editor_readout_keyboard_scale,
                        scaleText(plan.keyboardHeightScale()));
            }
            case KEYBOARD_CHIN:
                return context.getString(R.string.layout_editor_readout_chin,
                    plan.keyboardChinDp());
            case WIDGET_GRID:
            default: {
                PlaceLayout layout = plan.shownLayout();
                return context.getString(R.string.layout_editor_readout_grid,
                    layout.widgetColumns, layout.widgetRows);
            }
        }
    }

    @NonNull
    private static String scaleText(float scale) {
        return String.format(Locale.getDefault(), "%.2f", scale);
    }

    // -------------------------------------------------------------------------------- the tray

    /**
     * The restore tray (spec §3.6): a Material chip per hidden element, with the eye-off glyph,
     * that brings the element back to the edge it left; one short line when nothing is hidden. The
     * chips stand over the canvas's own tray strip, under the canvas, which draws its drop zone
     * over them while an element is lifted. Rebuilt only when what is hidden changes.
     */
    private void syncTray(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        List<LayoutEditorPlan.TrayItem> items = plan.trayItems();
        // The chips stand exactly over the canvas's strip: its height and its air under it.
        ViewGroup.LayoutParams params = card.tray.getLayoutParams();
        int height = Math.round(card.miniature.trayHeightPx());
        int inset = Math.round(card.miniature.trayBottomInsetPx());
        if (params instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams frame = (FrameLayout.LayoutParams) params;
            if (frame.height != height || frame.bottomMargin != inset) {
                frame.height = height;
                frame.bottomMargin = inset;
                frame.setMarginStart(inset);
                frame.setMarginEnd(inset);
                card.tray.setLayoutParams(frame);
            }
        }
        card.trayEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        card.trayScroller.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        if (items.equals(mTrayShown))
            return;
        mTrayShown = new ArrayList<>(items);
        card.trayChips.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mHost.context());
        for (LayoutEditorPlan.TrayItem item : items) {
            View view = inflater.inflate(R.layout.layout_editor_tray_chip, card.trayChips, false);
            if (!(view instanceof Chip))
                continue;
            Chip chip = (Chip) view;
            String name = mHost.context().getString(trayItemName(item));
            chip.setText(name);
            chip.setContentDescription(mHost.context().getString(
                R.string.layout_editor_tray_chip_description, name));
            chip.setOnClickListener(tapped -> {
                LayoutEditorPlan current = mPlan;
                if (current != null)
                    afterCanvasWrite(current.restore(item));
            });
            card.trayChips.addView(chip);
        }
    }

    @StringRes
    private static int trayItemName(@NonNull LayoutEditorPlan.TrayItem item) {
        switch (item) {
            case STATUS_BAR:
                return R.string.settings_layout_miniature_status;
            case PINNED_APPS:
                return R.string.settings_layout_miniature_apps;
            case AZ_INDEX:
                return R.string.settings_layout_miniature_alphabets;
            case EXTRA_KEYS:
                return R.string.settings_layout_miniature_keys;
            case KEYBOARD:
            default:
                return R.string.layout_editor_keyboard;
        }
    }

    // --------------------------------------------------------------------- the keyboard's type

    /** The three type chips, in the order the group holds them, and the forms they stand for. */
    private static final int[] FORM_CHIP_IDS = {
        R.id.layout_editor_keyboard_form_docked, R.id.layout_editor_keyboard_form_floating,
        R.id.layout_editor_keyboard_form_split};
    private static final PlaceLayout.KeyboardForm[] FORM_CHIP_FORMS = {
        PlaceLayout.KeyboardForm.DOCKED, PlaceLayout.KeyboardForm.FLOATING,
        PlaceLayout.KeyboardForm.SPLIT};

    /** The air between the keyboard's card and its type chips. */
    private static final float FORM_CHIPS_GAP_DP = 6f;

    /**
     * The keyboard's type chips (spec §3.5): shown beside the keyboard while it is selected, with
     * the shown orientation's form checked, and gone on deselect. Beside the phone where the
     * canvas leaves a gutter wide enough for a column of them — a portrait phone in a wide card —
     * and above the keyboard's leading end otherwise, clear of the handle on its middle.
     */
    private void syncKeyboardForms(@NonNull Card card) {
        LayoutEditorPlan plan = mPlan;
        RectF keyboard = card.miniature.keyboardRect();
        boolean shown = plan != null && keyboard != null
            && card.miniature.selectedBlock() == LayoutCanvasView.Block.KEYBOARD;
        if (!shown) {
            card.keyboardForms.setVisibility(View.GONE);
            return;
        }
        mRestatingToggle = true;
        try {
            PlaceLayout.KeyboardForm form = plan.keyboardForm();
            for (int i = 0; i < FORM_CHIP_IDS.length; i++) {
                if (FORM_CHIP_FORMS[i] == form)
                    card.keyboardForms.check(FORM_CHIP_IDS[i]);
            }
        } finally {
            mRestatingToggle = false;
        }
        card.keyboardForms.setVisibility(View.VISIBLE);
        placeKeyboardForms(card, keyboard);
    }

    private void placeKeyboardForms(@NonNull Card card, @NonNull RectF keyboard) {
        ChipGroup chips = card.keyboardForms;
        RectF frame = card.miniature.frameRect();
        int hostWidth = card.canvasHost.getWidth() > 0 ? card.canvasHost.getWidth()
            : card.miniature.getWidth();
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        View first = chips.getChildAt(0);
        if (first == null)
            return;
        first.measure(unspecified, unspecified);
        int chipWidth = first.getMeasuredWidth();
        float gap = dpToPx(FORM_CHIPS_GAP_DP);
        boolean column = hostWidth - frame.right >= chipWidth + 2 * gap;
        ViewGroup.LayoutParams params = chips.getLayoutParams();
        int wantedWidth = column ? chipWidth : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (params != null && params.width != wantedWidth) {
            params.width = wantedWidth;
            chips.setLayoutParams(params);
        }
        chips.setSingleLine(!column);
        chips.measure(column
                ? View.MeasureSpec.makeMeasureSpec(chipWidth, View.MeasureSpec.EXACTLY)
                : unspecified, unspecified);
        float width = chips.getMeasuredWidth();
        float height = chips.getMeasuredHeight();
        float x;
        float y;
        if (column) {
            x = frame.right + gap;
            y = keyboard.bottom - height;
        } else {
            x = keyboard.left + gap;
            y = keyboard.top - height - gap;
        }
        float maxX = Math.max(0f, hostWidth - width);
        chips.setTranslationX(Math.max(0f, Math.min(maxX, x)));
        chips.setTranslationY(Math.max(0f, y));
    }

    private void onKeyboardFormPicked(int checkedId) {
        LayoutEditorPlan plan = mPlan;
        if (plan == null || mRestatingToggle)
            return;
        for (int i = 0; i < FORM_CHIP_IDS.length; i++) {
            if (FORM_CHIP_IDS[i] == checkedId) {
                afterCanvasWrite(plan.setKeyboardForm(FORM_CHIP_FORMS[i]));
                return;
            }
        }
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
        mHeldHandle = null;
        mTrayShown = Collections.emptyList();
        mShowing = false;
        if (mCard != null) {
            // The next session opens with nothing selected and no chips over the keyboard.
            mCard.miniature.setSelectedBlock(null);
            mCard.keyboardForms.setVisibility(View.GONE);
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
