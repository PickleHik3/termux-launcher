package com.termux.app.surfaces;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.slider.Slider;

import com.termux.R;

/**
 * The Appearance editor's bottom area (SPEC §3.2–3.4): views only. It inflates
 * {@code appearance_editor_panel}, paints it as an M3 bottom sheet surface, places a label under
 * each tick of the Look slider, and reports what the user did through {@link Listener}. What any
 * of it means is {@link SurfaceEditorController}'s.
 *
 * <p>Every restatement from code runs with {@link #mRestating} set, so a value the controller
 * pushes in is never read back as the user's.</p>
 *
 * <p>Layout mode (SPEC §3.5) keeps the top row — the mode pill, Undo and Done, which are the whole
 * editor's — and swaps rows 1, 2 and the hint for the Layout row: the orientation toggle and the
 * restore tray. Those views are {@link com.termux.app.layouteditor.LayoutEditorController}'s to
 * drive; this class only shows and hides them.</p>
 */
final class AppearanceEditorPanel {

    /** What the user did in the bottom area. */
    interface Listener {
        /** The mode pill moved: true for Layout, false for Appearance. */
        void onModeChanged(boolean layout);
        void onUndo();
        void onDone();
        /** The Look slider settled on a stop (a drag reports each stop it crosses). */
        void onLookStop(int stop);
        void onStyle(boolean floating);
        /** Row 2's first slider (Darkness or Keys). */
        void onFirstSlider(int value, boolean dragging);
        /** Row 2's first segmented control (Legibility or Soft wallpaper), by segment index. */
        void onFirstSegment(int index);
        /** Row 2's second slider (Blur or Dim). */
        void onSecondSlider(int value, boolean dragging);
        /** A row-2 slider was released; deferred work settles here. */
        void onSliderReleased();
    }

    private static final int[] LOOK_LABELS = {
        R.string.termux_surface_preset_minimal,
        R.string.termux_surface_preset_frost,
        R.string.termux_surface_preset_stock,
        R.string.termux_surface_preset_solid,
        R.string.termux_surface_preset_custom};

    @NonNull private final Context mContext;
    @NonNull private final View mRoot;
    private final MaterialButtonToggleGroup mMode;
    private final MaterialButton mUndo;
    private final MaterialButton mDone;
    private final Slider mLook;
    private final FrameLayout mLookLabels;
    private final TextView[] mLookLabelViews = new TextView[AppearanceLooks.STOP_COUNT];
    private final MaterialButtonToggleGroup mStyle;
    private final View mRow2;
    private final TextView mRow2Name;
    private final TextView mFirstLabel;
    private final Slider mFirstSlider;
    private final MaterialButtonToggleGroup mLegibility;
    private final MaterialButtonToggleGroup mSoft;
    private final View mSecondColumn;
    private final TextView mSecondLabel;
    private final Slider mSecondSlider;
    private final TextView mHint;
    private final View mRow1;
    private final View mLayoutRow;
    private final LinearLayout mRow2Controls;
    private final View mFirstColumn;
    private final MaterialButtonToggleGroup mOrientation;
    private final View mTray;
    private final ChipGroup mTrayChips;
    private final TextView mTrayEmpty;
    private final TextView mTrayDrop;
    /** Whether Layout mode's row is showing in place of rows 1, 2 and the hint. */
    private boolean mLayoutMode;
    /** Whether row 2 was up when Layout mode took its place, so Appearance gets it back. */
    private boolean mRow2BeforeLayout;
    /** Whether row 2 stacks its two controls (a panel under {@link #NARROW_DP}). */
    private boolean mNarrow;

    /** Row 2's height side by side, as appearance_editor_panel declares it. */
    static final int ROW2_HEIGHT_DP = 88;
    /** Row 2 with its second control on a line of its own. */
    static final int ROW2_STACKED_HEIGHT_DP = 152;
    /**
     * Under this width row 2's two controls stack rather than share the row. Every phone in
     * portrait is under it: three Legibility words beside a slider ellipsised at 533dp on the
     * review device, so side by side is for tablets and landscape only.
     */
    static final int NARROW_DP = 600;

    @Nullable private Listener mListener;
    private boolean mRestating;
    private boolean mFirstDragging;
    private boolean mSecondDragging;

    private AppearanceEditorPanel(@NonNull Context context, @NonNull View root) {
        mContext = context;
        mRoot = root;
        mMode = root.findViewById(R.id.appearance_editor_mode);
        mUndo = root.findViewById(R.id.appearance_editor_undo);
        mDone = root.findViewById(R.id.appearance_editor_done);
        mLook = root.findViewById(R.id.appearance_editor_look);
        mLookLabels = root.findViewById(R.id.appearance_editor_look_labels);
        mStyle = root.findViewById(R.id.appearance_editor_style);
        mRow2 = root.findViewById(R.id.appearance_editor_row2);
        mRow2Name = root.findViewById(R.id.appearance_editor_row2_name);
        mFirstLabel = root.findViewById(R.id.appearance_editor_c1_label);
        mFirstSlider = root.findViewById(R.id.appearance_editor_c1_slider);
        mLegibility = root.findViewById(R.id.appearance_editor_c1_legibility);
        mSoft = root.findViewById(R.id.appearance_editor_c1_soft);
        mSecondColumn = root.findViewById(R.id.appearance_editor_c2);
        mSecondLabel = root.findViewById(R.id.appearance_editor_c2_label);
        mSecondSlider = root.findViewById(R.id.appearance_editor_c2_slider);
        mHint = root.findViewById(R.id.appearance_editor_hint);
        mRow1 = root.findViewById(R.id.appearance_editor_row1);
        mLayoutRow = root.findViewById(R.id.appearance_editor_layout_row);
        mRow2Controls = root.findViewById(R.id.appearance_editor_row2_controls);
        mFirstColumn = root.findViewById(R.id.appearance_editor_c1);
        mOrientation = root.findViewById(R.id.layout_editor_orientation);
        mTray = root.findViewById(R.id.layout_editor_tray);
        mTrayChips = root.findViewById(R.id.layout_editor_tray_chips);
        mTrayEmpty = root.findViewById(R.id.layout_editor_tray_empty);
        mTrayDrop = root.findViewById(R.id.layout_editor_tray_drop);
        paintSheet();
        buildLookLabels();
        bind();
    }

    /** Inflates the bottom area, unattached; the controller adds it where the frame leaves room. */
    @NonNull
    static AppearanceEditorPanel inflate(@NonNull Context context, @NonNull ViewGroup parent) {
        View root = LayoutInflater.from(context)
            .inflate(R.layout.appearance_editor_panel, parent, false);
        return new AppearanceEditorPanel(context, root);
    }

    @NonNull
    View view() {
        return mRoot;
    }

    void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    // ------------------------------------------------------------------------------ the sheet

    /** colorSurfaceContainer under the large corner family, rounded along the top only. */
    private void paintSheet() {
        TypedValue shape = new TypedValue();
        ShapeAppearanceModel model;
        if (mContext.getTheme().resolveAttribute(
                com.google.android.material.R.attr.shapeAppearanceCornerLarge, shape, true)
            && shape.resourceId != 0) {
            model = ShapeAppearanceModel.builder(mContext, shape.resourceId, 0).build();
        } else {
            model = ShapeAppearanceModel.builder().setAllCornerSizes(dp(16)).build();
        }
        model = model.toBuilder().setBottomLeftCornerSize(0f).setBottomRightCornerSize(0f).build();
        MaterialShapeDrawable sheet = new MaterialShapeDrawable(model);
        sheet.setFillColor(ColorStateList.valueOf(MaterialColors.getColor(mRoot,
            com.google.android.material.R.attr.colorSurfaceContainer,
            ContextCompat.getColor(mContext, R.color.termux_surface_panel_high))));
        mRoot.setBackground(sheet);
        mRoot.setElevation(dp(3));
    }

    // ------------------------------------------------------------------------- the Look slider

    private void buildLookLabels() {
        for (int i = 0; i < LOOK_LABELS.length; i++) {
            TextView label = new TextView(mContext);
            label.setText(LOOK_LABELS[i]);
            label.setTextAppearance(resolveTextAppearance(
                com.google.android.material.R.attr.textAppearanceLabelSmall));
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            final int stop = i;
            // A label is a way onto its stop too: the ticks are small, the words are not.
            label.setOnClickListener(view -> {
                if (AppearanceLooks.stopForSliderValue(mLook.getValue()) == stop)
                    return;
                mLook.setValue(AppearanceLooks.sliderValueForStop(stop));
                if (mListener != null) mListener.onLookStop(stop);
            });
            mLookLabels.addView(label, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mLookLabelViews[i] = label;
        }
        mLook.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeLookLabels());
        mLookLabels.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeLookLabels());
        mLook.setLabelFormatter(value -> mContext.getString(
            LOOK_LABELS[AppearanceLooks.stopForSliderValue(value)]));
    }

    /** Centres each label under its tick: ticks run from one track padding to the other. */
    private void placeLookLabels() {
        int width = mLook.getWidth();
        if (width <= 0)
            return;
        int pad = mLook.getTrackSidePadding();
        float span = Math.max(0, width - 2 * pad);
        int last = AppearanceLooks.STOP_COUNT - 1;
        for (int i = 0; i < mLookLabelViews.length; i++) {
            TextView label = mLookLabelViews[i];
            int labelWidth = label.getWidth();
            if (labelWidth <= 0) {
                label.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                labelWidth = label.getMeasuredWidth();
            }
            float centre = mLook.getLeft() + pad + span * i / last;
            float x = Math.max(0, Math.min(mLookLabels.getWidth() - labelWidth,
                centre - labelWidth / 2f));
            label.setTranslationX(x);
        }
    }

    private void styleLookLabels(int stop) {
        int active = MaterialColors.getColor(mRoot,
            com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(mContext, R.color.termux_primary));
        int quiet = MaterialColors.getColor(mRoot,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            ContextCompat.getColor(mContext, R.color.termux_on_surface));
        for (int i = 0; i < mLookLabelViews.length; i++)
            mLookLabelViews[i].setTextColor(i == stop ? active : quiet);
    }

    // ---------------------------------------------------------------------------------- wiring

    private void bind() {
        mMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            syncCheckIcons(group);
            if (mRestating || !isChecked)
                return;
            if (mListener != null)
                mListener.onModeChanged(checkedId == R.id.appearance_editor_mode_layout);
        });
        syncCheckIcons(mMode);
        mUndo.setOnClickListener(view -> {
            if (mListener != null) mListener.onUndo();
        });
        mDone.setOnClickListener(view -> {
            if (mListener != null) mListener.onDone();
        });
        mLook.addOnChangeListener((slider, value, fromUser) -> {
            int stop = AppearanceLooks.stopForSliderValue(value);
            styleLookLabels(stop);
            if (mRestating || !fromUser || mListener == null)
                return;
            mListener.onLookStop(stop);
        });
        mStyle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (mRestating || !isChecked || mListener == null)
                return;
            mListener.onStyle(checkedId == R.id.appearance_editor_style_floating);
        });
        mFirstSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (mRestating || !fromUser || mListener == null)
                return;
            mListener.onFirstSlider(Math.round(value), mFirstDragging);
        });
        mFirstSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider slider) {
                mFirstDragging = true;
            }

            @Override public void onStopTrackingTouch(@NonNull Slider slider) {
                mFirstDragging = false;
                if (mListener != null) mListener.onSliderReleased();
            }
        });
        mSecondSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (mRestating || !fromUser || mListener == null)
                return;
            mListener.onSecondSlider(Math.round(value), mSecondDragging);
        });
        mSecondSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider slider) {
                mSecondDragging = true;
            }

            @Override public void onStopTrackingTouch(@NonNull Slider slider) {
                mSecondDragging = false;
                if (mListener != null) mListener.onSliderReleased();
            }
        });
        syncCheckIcons(mOrientation);
        mOrientation.addOnButtonCheckedListener((group, checkedId, isChecked) ->
            syncCheckIcons(group));
        MaterialButtonToggleGroup.OnButtonCheckedListener segments =
            (group, checkedId, isChecked) -> {
                syncCheckIcons(group);
                if (mRestating || !isChecked || mListener == null)
                    return;
                mListener.onFirstSegment(group.indexOfChild(group.findViewById(checkedId)));
            };
        mLegibility.addOnButtonCheckedListener(segments);
        mSoft.addOnButtonCheckedListener(segments);
    }

    /**
     * The check icon on the chosen segment only. An unchecked segment carries no icon at all, so a
     * narrow three-way row spends its width on the words.
     */
    private void syncCheckIcons(@NonNull MaterialButtonToggleGroup group) {
        // Legibility's three words have no room for the icon on a narrow panel: its checked
        // segment is told by the fill alone. The orientation toggle is glyphs already.
        // The mode pill shares the top row with Undo and Done: its icon pushed Done off a
        // narrow panel.
        if (group == mLegibility || group == mOrientation || group == mMode)
            return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof MaterialButton))
                continue;
            MaterialButton button = (MaterialButton) child;
            button.setIcon(button.isChecked()
                ? ContextCompat.getDrawable(mContext, R.drawable.ic_symbol_check) : null);
        }
    }

    // ------------------------------------------------------------------------- restatements

    void showAppearanceMode() {
        setMode(false);
    }

    /**
     * Shows one mode's rows and checks its segment, without reporting it. Row 2's state is kept
     * across a visit to Layout mode, so Appearance comes back as it was left.
     */
    void setMode(boolean layout) {
        int id = layout ? R.id.appearance_editor_mode_layout
            : R.id.appearance_editor_mode_appearance;
        if (mMode.getCheckedButtonId() != id) {
            mRestating = true;
            mMode.check(id);
            mRestating = false;
        }
        syncCheckIcons(mMode);
        if (layout == mLayoutMode)
            return;
        mLayoutMode = layout;
        if (layout) {
            mRow2BeforeLayout = isRow2Shown();
            mRow1.setVisibility(View.GONE);
            mRow2.setVisibility(View.GONE);
            mHint.setVisibility(View.GONE);
            mLayoutRow.setVisibility(View.VISIBLE);
        } else {
            mLayoutRow.setVisibility(View.GONE);
            mRow1.setVisibility(View.VISIBLE);
            mRow2.setVisibility(mRow2BeforeLayout ? View.VISIBLE : View.GONE);
            mHint.setVisibility(mRow2BeforeLayout ? View.GONE : View.VISIBLE);
        }
    }

    boolean isLayoutMode() {
        return mLayoutMode;
    }

    /**
     * Row 2 side by side on a wide panel, stacked on a narrow one: three Legibility words and a
     * Blur slider do not share half a 333dp row each.
     */
    void setNarrow(boolean narrow) {
        if (mNarrow == narrow)
            return;
        mNarrow = narrow;
        mRow2Controls.setOrientation(narrow ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        mRow2Controls.setGravity(narrow ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        layoutColumn(mFirstColumn, narrow, 0);
        layoutColumn(mSecondColumn, narrow, narrow ? 0 : Math.round(dp(16)));
        ViewGroup.LayoutParams row2 = mRow2.getLayoutParams();
        if (row2 != null) {
            row2.height = Math.round(dp(row2HeightDp()));
            mRow2.setLayoutParams(row2);
        }
    }

    private static void layoutColumn(@NonNull View column, boolean narrow, int startMarginPx) {
        ViewGroup.LayoutParams params = column.getLayoutParams();
        if (!(params instanceof LinearLayout.LayoutParams))
            return;
        LinearLayout.LayoutParams linear = (LinearLayout.LayoutParams) params;
        linear.width = narrow ? ViewGroup.LayoutParams.MATCH_PARENT : 0;
        linear.weight = narrow ? 0f : 1f;
        linear.setMarginStart(startMarginPx);
        column.setLayoutParams(linear);
    }

    /** Row 2's one height for the width the panel has. */
    int row2HeightDp() {
        return mNarrow ? ROW2_STACKED_HEIGHT_DP : ROW2_HEIGHT_DP;
    }

    // ------------------------------------------------------------- Layout mode's views, lent

    @NonNull MaterialButtonToggleGroup orientationToggle() {
        return mOrientation;
    }

    @NonNull View tray() {
        return mTray;
    }

    @NonNull ChipGroup trayChips() {
        return mTrayChips;
    }

    @NonNull TextView trayEmpty() {
        return mTrayEmpty;
    }

    @NonNull TextView trayDrop() {
        return mTrayDrop;
    }

    void setDirty(boolean dirty) {
        int visibility = dirty ? View.VISIBLE : View.GONE;
        if (mUndo.getVisibility() != visibility)
            mUndo.setVisibility(visibility);
    }

    void setStop(int stop) {
        float value = AppearanceLooks.sliderValueForStop(stop);
        if (mLook.getValue() != value) {
            mRestating = true;
            mLook.setValue(value);
            mRestating = false;
        }
        styleLookLabels(stop);
    }

    void setFloating(boolean floating) {
        int id = floating ? R.id.appearance_editor_style_floating
            : R.id.appearance_editor_style_docked;
        if (mStyle.getCheckedButtonId() == id)
            return;
        mRestating = true;
        mStyle.check(id);
        mRestating = false;
    }

    void setHint(@StringRes int text) {
        mHint.setText(text);
    }

    /** Whether row 2 is up in Appearance mode (kept, though hidden, while Layout is shown). */
    boolean isRow2Shown() {
        return mLayoutMode ? mRow2BeforeLayout : mRow2.getVisibility() == View.VISIBLE;
    }

    /** Row 2 down and the hint back in its place. */
    void hideRow2() {
        if (mLayoutMode) {
            mRow2BeforeLayout = false;
            return;
        }
        mRow2.setVisibility(View.GONE);
        mHint.setVisibility(View.VISIBLE);
    }

    /** Row 2 up, with the tapped element's name; the controls are set by the calls below. */
    void showRow2(@StringRes int name) {
        mRow2Name.setText(name);
        if (mLayoutMode) {
            mRow2BeforeLayout = true;
            return;
        }
        mRow2.setVisibility(View.VISIBLE);
        mHint.setVisibility(View.GONE);
    }

    /** The first control as a slider: Darkness or Keys. */
    void setFirstSlider(@NonNull CharSequence label, int value, int max) {
        mFirstLabel.setText(label);
        mFirstSlider.setVisibility(View.VISIBLE);
        mLegibility.setVisibility(View.GONE);
        mSoft.setVisibility(View.GONE);
        restateSlider(mFirstSlider, value, max);
    }

    void setFirstLabel(@NonNull CharSequence label) {
        mFirstLabel.setText(label);
    }

    /** The first control as Legibility's three segments. */
    void setLegibility(@NonNull CharSequence label, int index) {
        mFirstLabel.setText(label);
        mFirstSlider.setVisibility(View.GONE);
        mSoft.setVisibility(View.GONE);
        mLegibility.setVisibility(View.VISIBLE);
        checkSegment(mLegibility, index);
    }

    /** The first control as Soft wallpaper's Off / On. */
    void setSoft(@NonNull CharSequence label, boolean on) {
        mFirstLabel.setText(label);
        mFirstSlider.setVisibility(View.GONE);
        mLegibility.setVisibility(View.GONE);
        mSoft.setVisibility(View.VISIBLE);
        checkSegment(mSoft, on ? 1 : 0);
    }

    /** The second control: Blur or Dim. */
    void setSecondSlider(@NonNull CharSequence label, int value, int max) {
        mSecondColumn.setVisibility(View.VISIBLE);
        mSecondLabel.setText(label);
        restateSlider(mSecondSlider, value, max);
    }

    void setSecondLabel(@NonNull CharSequence label) {
        mSecondLabel.setText(label);
    }

    private void checkSegment(@NonNull MaterialButtonToggleGroup group, int index) {
        View child = group.getChildAt(Math.max(0, Math.min(group.getChildCount() - 1, index)));
        if (child == null || group.getCheckedButtonId() == child.getId()) {
            syncCheckIcons(group);
            return;
        }
        mRestating = true;
        group.check(child.getId());
        mRestating = false;
        syncCheckIcons(group);
    }

    /** Range first, value inside it: a Slider throws for a value outside its range. */
    private void restateSlider(@NonNull Slider slider, int value, int max) {
        mRestating = true;
        float top = Math.max(1, max);
        float clamped = Math.max(0, Math.min(top, value));
        if (slider.getValue() > top)
            slider.setValue(0f);
        slider.setValueTo(top);
        slider.setValue(clamped);
        mRestating = false;
    }

    /** The label in the {@code attr} text appearance, resolved against the panel's theme. */
    private int resolveTextAppearance(int attr) {
        TypedValue value = new TypedValue();
        if (mContext.getTheme().resolveAttribute(attr, value, true) && value.resourceId != 0)
            return value.resourceId;
        return com.google.android.material.R.style.TextAppearance_Material3_LabelSmall;
    }

    private float dp(float value) {
        return value * mContext.getResources().getDisplayMetrics().density;
    }
}
