package com.termux.app.layouteditor;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.termux.R;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.fragments.settings.PlaceMiniatureView;
import com.termux.app.place.PlaceArrangeModel;
import com.termux.app.place.PlaceArrangeModel.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.List;

/**
 * The Layout editor: a miniature of the place the user is looking at, parked over the live place,
 * with a Portrait / Landscape toggle above it.
 *
 * <p>The sibling of the surface editor and not a page of it: this one answers where a place's
 * elements sit, that one answers how its surfaces look, and only ever one of the two is open. A bar
 * is dragged on the miniature to an edge or into the tray under it, and the drop writes straight
 * through, so the place behind the card follows every drop made in the orientation the phone is
 * actually in. The other orientation moves on the miniature alone until the phone is turned.
 *
 * <p>Beneath the picture stand the rows for what no bar can be dragged into — the dock's and the
 * keyboard's height, the keyboard's own choices and its chin, and Home's grid. They write through
 * the same way a drop does, and scroll inside whatever room the canvas above them has left.
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

        /** Where every place keeps its arrangement, or null before the preferences exist. */
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
    }

    @NonNull private final Host mHost;

    /** The editor's fixed views, inflated once per process. */
    private static final class Card {
        final ViewGroup host;
        final LinearLayout root;
        final ImageView revert;
        final TextView discard;
        final ImageView done;
        final MaterialButtonToggleGroup orientation;
        final PlaceMiniatureView miniature;
        final TextView narrowNotice;
        final ViewGroup rowsHost;

        Card(ViewGroup host, LinearLayout root) {
            this.host = host;
            this.root = root;
            revert = root.findViewById(R.id.layout_editor_revert);
            discard = root.findViewById(R.id.layout_editor_discard);
            done = root.findViewById(R.id.layout_editor_done);
            orientation = root.findViewById(R.id.layout_editor_orientation);
            miniature = root.findViewById(R.id.layout_editor_miniature);
            narrowNotice = root.findViewById(R.id.layout_editor_narrow_notice);
            rowsHost = root.findViewById(R.id.layout_editor_rows_host);
        }

        boolean complete() {
            return revert != null && discard != null && done != null && orientation != null
                && miniature != null && narrowNotice != null && rowsHost != null;
        }
    }

    @Nullable private Card mCard;
    @Nullable private LayoutEditorPlan mPlan;
    /** True while the toggle is being restated from the plan, so it writes nothing back. */
    private boolean mRestatingToggle;
    /** The track a finger is on, which no restatement may move under it. */
    @Nullable private SeekBar mDraggedSlider;
    /** The rows' own scroller and column, built on first use and refilled per place. */
    @Nullable private ScrollView mRowsScroller;
    @Nullable private LinearLayout mRows;
    /** Restates every row from the store; run after anything that can move what one says. */
    @NonNull private final List<Runnable> mRowSyncs = new ArrayList<>(5);
    /** The place and orientation the rows standing there were built for, or null for none. */
    @Nullable private String mRowsKey;
    /** How tall the rows may grow before they scroll, from the room the canvas left. */
    private int mRowsCapPx;

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
        card.host.bringToFront();
        mHost.holdPaneWallOnPlace(place, true);
        sync();
    }

    /** Inflates the card into its host, once. */
    @Nullable
    private Card card() {
        if (mCard != null)
            return mCard;
        ViewGroup host = mHost.findView(R.id.layout_editor_host);
        if (host == null)
            return null;
        LinearLayout root = host.findViewById(R.id.layout_editor_card);
        if (root == null) {
            LayoutInflater.from(mHost.context()).inflate(R.layout.layout_editor, host, true);
            root = host.findViewById(R.id.layout_editor_card);
        }
        if (root == null)
            return null;
        Card card = new Card(host, root);
        if (!card.complete())
            return null;
        mCard = card;
        bind(card);
        return card;
    }

    private void bind(@NonNull Card card) {
        card.root.setBackground(cardBackground());
        setIcon(card.revert, R.drawable.ic_symbol_restart, false);
        setIcon(card.done, R.drawable.ic_symbol_check, true);

        card.revert.setOnClickListener(view -> revertToEntryState());
        card.discard.setOnClickListener(view -> {
            revertToEntryState();
            exit();
        });
        card.done.setOnClickListener(view -> exit());

        card.miniature.setLegendVisible(false);
        card.miniature.setOnBarDroppedListener(this::onBarDropped);
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
        MiniatureDragPolicy.Bar bar = PlaceMiniatureView.barOf(block);
        if (mPlan == null || bar == null)
            return;
        if (mPlan.drop(bar, edge, index) == LayoutEditorPlan.Drop.LIVE)
            mHost.applyPlaceArrangement();
        sync();
    }

    /** Re-reads the miniature, the toggle, the rows and the two unsaved glyphs from the plan. */
    private void sync() {
        Card card = mCard;
        LayoutEditorPlan plan = mPlan;
        if (card == null || plan == null)
            return;
        mRestatingToggle = true;
        card.orientation.check(plan.shownOrientation() == PlaceOrientation.LANDSCAPE
            ? R.id.layout_editor_orientation_landscape : R.id.layout_editor_orientation_portrait);
        mRestatingToggle = false;
        applyCanvasHeight(card, plan);
        card.miniature.setLayout(plan.shownLayout(), plan.shownOrientation(), plan.place());
        // Bars down the side of a portrait screen are allowed; this is the one line that says what
        // they cost, and it goes away as soon as the width does not.
        card.narrowNotice.setVisibility(plan.warnsNarrowCanvas() ? View.VISIBLE : View.GONE);
        syncRows(card, plan);
        syncDirty(card, plan);
    }

    /** The revert glyph and Discard, which exist only while there is something to lose. */
    private void syncDirty(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        int dirty = plan.isDirty() ? View.VISIBLE : View.GONE;
        card.revert.setVisibility(dirty);
        card.discard.setVisibility(dirty);
    }

    /** Sizes the canvas to the frame the shown orientation asks for. */
    private void applyCanvasHeight(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        DisplayMetrics metrics = mHost.context().getResources().getDisplayMetrics();
        int height = LayoutEditorPlan.miniatureHeightPx(plan.shownOrientation(),
            metrics.widthPixels, metrics.heightPixels,
            PlaceMiniatureView.frameAspect(plan.shownOrientation()),
            Math.round(card.miniature.reservedHeightPx()));
        mRowsCapPx = LayoutEditorPlan.rowsHeightCapPx(metrics.heightPixels, height,
            Math.round(dpToPx(CARD_CHROME_DP)), Math.round(dpToPx(ROWS_FLOOR_DP)));
        if (mRowsScroller != null)
            mRowsScroller.requestLayout();
        ViewGroup.LayoutParams params = card.miniature.getLayoutParams();
        if (params == null || params.height == height)
            return;
        params.height = height;
        card.miniature.setLayoutParams(params);
    }

    // --------------------------------------------------------------------------------- the rows

    /** The four segment slots a pill row declares; the ones a value set does not use come off. */
    private static final int[] SEGMENT_IDS = {
        R.id.layout_editor_row_segment_0, R.id.layout_editor_row_segment_1,
        R.id.layout_editor_row_segment_2, R.id.layout_editor_row_segment_3};

    /** The header, the toggle and the card's own padding: everything that is not the canvas. */
    private static final float CARD_CHROME_DP = 132f;
    /** The rows keep at least this much even where the canvas would have taken it all. */
    private static final float ROWS_FLOOR_DP = 96f;

    /**
     * The rows for the place and orientation on show. They are rebuilt only when one of those two
     * moves — a pick changes what a row says, not which rows there are — and restated from the
     * store every time anything else might have.
     */
    private void syncRows(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        String key = plan.place().name() + '.' + plan.shownOrientation().name();
        if (!key.equals(mRowsKey)) {
            rebuildRows(card, plan);
            mRowsKey = key;
        }
        for (Runnable sync : mRowSyncs) sync.run();
    }

    private void rebuildRows(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        LinearLayout rows = rowsColumn(card);
        rows.removeAllViews();
        mRowSyncs.clear();
        // The tracks being replaced are gone, finger or no finger, and the reference would outlive
        // the view.
        mDraggedSlider = null;
        Context context = mHost.context();
        Element heading = null;
        for (LayoutEditorPlan.Row row : plan.rows()) {
            if (row.element != heading) {
                rows.addView(sectionTitle(context, row.element));
                heading = row.element;
            }
            if (row.group instanceof PlaceArrangeModel.Pills)
                addPillsRow(context, rows, row, (PlaceArrangeModel.Pills) row.group);
            else if (row.group instanceof PlaceArrangeModel.Track)
                addTrackRow(context, rows, row, (PlaceArrangeModel.Track) row.group);
        }
        if (mRowsScroller != null) mRowsScroller.scrollTo(0, 0);
    }

    /** The column the rows stand in, inside a scroller that grows only to the room it was left. */
    @NonNull
    private LinearLayout rowsColumn(@NonNull Card card) {
        if (mRows != null)
            return mRows;
        Context context = mHost.context();
        ScrollView scroller = new ScrollView(context) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                    Math.max(1, mRowsCapPx), View.MeasureSpec.AT_MOST));
            }
        };
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        // A list cut short by a tall canvas has only the fade to say so; one that simply stops at
        // the card's edge reads as the whole list.
        scroller.setVerticalFadingEdgeEnabled(true);
        scroller.setFadingEdgeLength(Math.round(dpToPx(18)));
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(rows, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.rowsHost.addView(scroller, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mRowsScroller = scroller;
        mRows = rows;
        return rows;
    }

    /** What the rows under it are about: the dock, the keyboard, or Home's grid. */
    @NonNull
    private TextView sectionTitle(@NonNull Context context, @NonNull Element element) {
        TextView title = new TextView(context);
        title.setText(headingRes(element));
        title.setTextSize(11f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(mHost.themeColor(
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Math.round(dpToPx(4));
        params.bottomMargin = Math.round(dpToPx(2));
        title.setLayoutParams(params);
        return title;
    }

    @StringRes
    private static int headingRes(@NonNull Element element) {
        switch (element) {
            case PINNED_APPS:
                return R.string.termux_surface_tuning_dock;
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
            .inflate(R.layout.layout_editor_pills_row, into, false);
        ((TextView) view.findViewById(R.id.layout_editor_row_label)).setText(pills.labelRes);
        MaterialButtonToggleGroup group = view.findViewById(R.id.layout_editor_row_pills);
        if (group == null)
            return;
        final int count = Math.min(pills.values.length, SEGMENT_IDS.length);
        for (int i = SEGMENT_IDS.length - 1; i >= count; i--) {
            View extra = view.findViewById(SEGMENT_IDS[i]);
            if (extra != null) group.removeView(extra);
        }
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
            afterRowWrite();
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
            .inflate(R.layout.layout_editor_slider_row, into, false);
        ((TextView) view.findViewById(R.id.layout_editor_row_label)).setText(track.labelRes);
        SeekBar slider = view.findViewById(R.id.layout_editor_row_slider);
        TextView value = view.findViewById(R.id.layout_editor_row_value);
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
                afterRowWrite();
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

    /** A row wrote through: the place behind the card follows when it is the one on screen. */
    private void afterRowWrite() {
        Card card = mCard;
        LayoutEditorPlan plan = mPlan;
        if (card == null || plan == null)
            return;
        if (plan.liveFollows()) mHost.applyPlaceArrangement();
        for (Runnable sync : mRowSyncs) sync.run();
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
        PaneWallPage place = mPlan == null ? null : mPlan.place();
        mPlan = null;
        mRowsKey = null;
        mDraggedSlider = null;
        if (mCard != null) {
            mCard.revert.setVisibility(View.GONE);
            mCard.discard.setVisibility(View.GONE);
            mCard.host.setVisibility(View.GONE);
        }
        if (place != null) mHost.holdPaneWallOnPlace(place, false);
    }

    // -------------------------------------------------------------------------------- the chrome

    @NonNull
    private Drawable cardBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(mHost.themeColor(
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            R.color.termux_surface_panel_high));
        background.setCornerRadius(dpToPx(24));
        background.setStroke(Math.max(1, Math.round(dpToPx(1))), mHost.themeColor(
            com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_outline_variant));
        return background;
    }

    private void setIcon(@NonNull ImageView view, @DrawableRes int drawableRes, boolean onAccent) {
        Drawable icon = androidx.core.content.ContextCompat.getDrawable(
            mHost.context(), drawableRes);
        if (icon == null)
            return;
        icon = icon.mutate();
        icon.setTint(onAccent
            ? mHost.themeColor(com.termux.shared.R.attr.termuxColorOnAccentContainer,
                R.color.termux_on_accent_container)
            : mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary));
        view.setImageDrawable(icon);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
            mHost.context().getResources().getDisplayMetrics());
    }
}
