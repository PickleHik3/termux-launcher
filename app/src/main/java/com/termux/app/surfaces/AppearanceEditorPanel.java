package com.termux.app.surfaces;

import android.content.Context;
import android.content.res.ColorStateList;
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
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.slider.Slider;

import com.termux.R;

/**
 * The Appearance / Layout editor's bottom area (SPEC §3.2–3.6): views only. It inflates
 * {@code appearance_editor_panel}, paints it as an M3 sheet surface, places a label under each
 * stop of the Look slider, and reports what the user did through {@link Listener}. What any of it
 * means is {@link SurfaceEditorController}'s and
 * {@link com.termux.app.layouteditor.LayoutEditorController}'s.
 *
 * <p>The layout is one ConstraintLayout: the top row (mode pill, Undo, Done) is both modes'; each
 * mode's rows are a {@link Group}, and a mode switch shows one and hides the other. Row B
 * (the tapped element's controls) is GONE until an element is tapped, so the sheet's height
 * follows its content: {@link #measureFor} is the height for what shows now, and
 * {@link #measureTallest} the height with Row B up, which the frame stands above.</p>
 *
 * <p>Every restatement from code runs with {@link #mRestating} set, so a value the controller
 * pushes in is never read back as the user's.</p>
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
        /** Layout mode's Style toggle. */
        void onStyle(boolean floating);
        /** Layout mode's Corners slider, in dp. */
        void onCorners(int value, boolean dragging);
        /** Layout mode's Margin slider, in dp. */
        void onMargin(int value, boolean dragging);
        /** Row B's first slider (Opacity or Key radius). */
        void onFirstSlider(int value, boolean dragging);
        /** Row B's first segmented control (Soften wallpaper), by segment index. */
        void onFirstSegment(int index);
        /** The terminal's Text contrast, by stop: 0 Low (Softer), 1 Normal (Default), 2 High (Harder). */
        void onLegibility(int index);
        /** Row B's last slider (Blur or Dim). */
        void onSecondSlider(int value, boolean dragging);
        /** A slider was released; deferred work settles here. */
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
    /** The bottom padding the layout declares; the controller adds the navigation inset to it. */
    private final int mBasePaddingBottom;

    private final MaterialButtonToggleGroup mMode;
    private final MaterialButton mUndo;
    private final MaterialButton mDone;
    private final Group mAppearanceGroup;
    private final Group mLayoutGroup;

    private final Slider mLook;
    private final FrameLayout mLookLabels;
    private final TextView[] mLookLabelViews = new TextView[AppearanceLooks.STOP_COUNT];

    private final View mRow2;
    private final TextView mRow2Name;
    private final TextView mFirstLabel;
    private final Slider mFirstSlider;
    private final MaterialButtonToggleGroup mSoft;
    private final TextView mLegibilityLabel;
    private final Slider mLegibility;
    private final TextView mSecondLabel;
    private final Slider mSecondSlider;

    private final MaterialButtonToggleGroup mOrientation;
    private final MaterialButtonToggleGroup mStyle;
    private final MaterialButton mTrash;
    private final TextView mCornersLabel;
    private final Slider mCorners;
    private final TextView mMarginLabel;
    private final Slider mMargin;

    /** Whether Layout mode's rows are showing in place of Appearance's. */
    private boolean mLayoutMode;
    /** Whether Row B is up (an element is tapped at the Custom stop); kept across Layout mode. */
    private boolean mRow2Shown;
    /** Whether Text contrast may be moved (the Material palette is on). */
    private boolean mLegibilityEnabled = true;

    @Nullable private Listener mListener;
    private boolean mRestating;
    private boolean mFirstDragging;
    private boolean mSecondDragging;
    private boolean mCornersDragging;
    private boolean mMarginDragging;

    private AppearanceEditorPanel(@NonNull Context context, @NonNull View root) {
        mContext = context;
        mRoot = root;
        mBasePaddingBottom = root.getPaddingBottom();
        mMode = root.findViewById(R.id.appearance_editor_mode);
        mUndo = root.findViewById(R.id.appearance_editor_undo);
        mDone = root.findViewById(R.id.appearance_editor_done);
        mAppearanceGroup = root.findViewById(R.id.appearance_editor_appearance_group);
        mLayoutGroup = root.findViewById(R.id.appearance_editor_layout_group);
        mLook = root.findViewById(R.id.appearance_editor_look);
        mLookLabels = root.findViewById(R.id.appearance_editor_look_labels);
        mRow2 = root.findViewById(R.id.appearance_editor_row2);
        mRow2Name = root.findViewById(R.id.appearance_editor_row2_name);
        mFirstLabel = root.findViewById(R.id.appearance_editor_c1_label);
        mFirstSlider = root.findViewById(R.id.appearance_editor_c1_slider);
        mSoft = root.findViewById(R.id.appearance_editor_c1_soft);
        mLegibilityLabel = root.findViewById(R.id.appearance_editor_cl_label);
        mLegibility = root.findViewById(R.id.appearance_editor_legibility);
        mSecondLabel = root.findViewById(R.id.appearance_editor_c2_label);
        mSecondSlider = root.findViewById(R.id.appearance_editor_c2_slider);
        mOrientation = root.findViewById(R.id.layout_editor_orientation);
        mStyle = root.findViewById(R.id.appearance_editor_style);
        mTrash = root.findViewById(R.id.layout_editor_tray_trash);
        mCornersLabel = root.findViewById(R.id.appearance_editor_corners_label);
        mCorners = root.findViewById(R.id.appearance_editor_corners);
        mMarginLabel = root.findViewById(R.id.appearance_editor_margin_label);
        mMargin = root.findViewById(R.id.appearance_editor_margin);
        // The groups' members, stated here as well as in the layout: a Group resolves its XML
        // names lazily, and the mode is applied before the panel is ever measured or attached.
        mAppearanceGroup.setReferencedIds(new int[] {R.id.appearance_editor_look,
            R.id.appearance_editor_look_labels});
        mLayoutGroup.setReferencedIds(new int[] {R.id.layout_editor_orientation,
            R.id.appearance_editor_style, R.id.layout_editor_tray_trash,
            R.id.appearance_editor_corners_label, R.id.appearance_editor_margin_label,
            R.id.appearance_editor_corners, R.id.appearance_editor_margin});
        applyGroups(false);
        applyRow2(false, false);
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

    /** colorSurfaceContainer under the extra-large corner family, rounded along the top only. */
    private void paintSheet() {
        TypedValue shape = new TypedValue();
        ShapeAppearanceModel model;
        if (mContext.getTheme().resolveAttribute(
                com.google.android.material.R.attr.shapeAppearanceCornerExtraLarge, shape, true)
            && shape.resourceId != 0) {
            model = ShapeAppearanceModel.builder(mContext, shape.resourceId, 0).build();
        } else {
            model = ShapeAppearanceModel.builder().build();
        }
        model = model.toBuilder().setBottomLeftCornerSize(0f).setBottomRightCornerSize(0f).build();
        MaterialShapeDrawable sheet = new MaterialShapeDrawable(model);
        sheet.setFillColor(ColorStateList.valueOf(MaterialColors.getColor(mRoot,
            com.google.android.material.R.attr.colorSurfaceContainer,
            ContextCompat.getColor(mContext, R.color.termux_surface_panel_high))));
        mRoot.setBackground(sheet);
        mRoot.setElevation(dp(3));
    }

    /** The bottom padding: the layout's own plus the navigation bar's inset under the sheet. */
    void setNavInset(int navInsetPx) {
        int bottom = mBasePaddingBottom + Math.max(0, navInsetPx);
        if (mRoot.getPaddingBottom() != bottom)
            mRoot.setPaddingRelative(mRoot.getPaddingStart(), mRoot.getPaddingTop(),
                mRoot.getPaddingEnd(), bottom);
    }

    /**
     * The sheet's height for {@code layout} mode at {@code widthPx}, padding included, for what
     * shows now: Appearance is Row A alone until an element is tapped, then Row A + Row B. The
     * mode showing is restored before returning.
     */
    int measureFor(boolean layout, int widthPx) {
        boolean shown = mLayoutMode;
        applyGroups(layout);
        int height = measureNow(widthPx);
        applyGroups(shown);
        return height;
    }

    /**
     * The tallest the sheet gets in {@code layout} mode: Appearance with Row B up and its labels
     * on two lines, whether or not an element is tapped. The frame stands above this, so it never
     * moves when Row B comes and goes. Layout mode's rows are the same at every moment.
     */
    int measureTallest(boolean layout, int widthPx) {
        if (layout)
            return measureFor(true, widthPx);
        boolean shown = mLayoutMode;
        boolean rowShown = mRow2Shown;
        mRow2Shown = true;
        TextView[] labels = {mFirstLabel, mLegibilityLabel, mSecondLabel};
        for (TextView label : labels)
            label.setMinLines(2);
        applyGroups(false);
        int height = measureNow(widthPx);
        for (TextView label : labels)
            label.setMinLines(1);
        mRow2Shown = rowShown;
        applyGroups(shown);
        return height;
    }

    private int measureNow(int widthPx) {
        mRoot.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return mRoot.getMeasuredHeight();
    }

    // ------------------------------------------------------------------------- the Look slider

    private void buildLookLabels() {
        for (int i = 0; i < LOOK_LABELS.length; i++) {
            TextView label = new TextView(mContext);
            label.setText(LOOK_LABELS[i]);
            label.setTextAppearance(resolveTextAppearance(
                com.google.android.material.R.attr.textAppearanceLabelMedium));
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            final int stop = i;
            // A label is a way onto its stop too: the stops are small, the words are not.
            label.setOnClickListener(view -> {
                if (AppearanceLooks.stopForSliderValue(mLook.getValue()) == stop)
                    return;
                mLook.setValue(AppearanceLooks.sliderValueForStop(stop));
                if (mListener != null) mListener.onLookStop(stop);
            });
            // Absolute LEFT: placeLookLabels translates each label from the strip's left edge,
            // which a START label in a right-to-left strip would not be standing on.
            mLookLabels.addView(label, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT));
            mLookLabelViews[i] = label;
        }
        mLook.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeLookLabels());
        mLookLabels.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeLookLabels());
        mLook.setLabelFormatter(value -> mContext.getString(
            LOOK_LABELS[AppearanceLooks.stopForSliderValue(value)]));
    }

    /**
     * Centres each label under its stop: the stops run from one track padding to the other, in
     * the slider's coordinates, which the label strip shares up to the two views' offset. A
     * right-to-left slider puts its first stop on the right, so the labels follow it there.
     */
    private void placeLookLabels() {
        int width = mLook.getWidth();
        if (width <= 0)
            return;
        int pad = mLook.getTrackSidePadding();
        float span = Math.max(0, width - 2 * pad);
        int last = AppearanceLooks.STOP_COUNT - 1;
        int offset = mLook.getLeft() - mLookLabels.getLeft();
        boolean rtl = mLook.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < mLookLabelViews.length; i++) {
            TextView label = mLookLabelViews[i];
            int labelWidth = label.getWidth();
            if (labelWidth <= 0) {
                label.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                labelWidth = label.getMeasuredWidth();
            }
            float along = span * i / last;
            float centre = offset + (rtl ? width - pad - along : pad + along);
            float x = Math.max(0, Math.min(mLookLabels.getWidth() - labelWidth,
                centre - labelWidth / 2f));
            label.setTranslationX(x);
        }
    }

    private void styleLookLabels(int stop) {
        int active = MaterialColors.getColor(mRoot,
            androidx.appcompat.R.attr.colorPrimary,
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
            if (mRestating || !isChecked)
                return;
            if (mListener != null)
                mListener.onModeChanged(checkedId == R.id.appearance_editor_mode_layout);
        });
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
        mCorners.addOnChangeListener((slider, value, fromUser) -> {
            if (mRestating || !fromUser || mListener == null)
                return;
            mListener.onCorners(Math.round(value), mCornersDragging);
        });
        mCorners.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider slider) {
                mCornersDragging = true;
            }

            @Override public void onStopTrackingTouch(@NonNull Slider slider) {
                mCornersDragging = false;
                if (mListener != null) mListener.onSliderReleased();
            }
        });
        mMargin.addOnChangeListener((slider, value, fromUser) -> {
            if (mRestating || !fromUser || mListener == null)
                return;
            mListener.onMargin(Math.round(value), mMarginDragging);
        });
        mMargin.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider slider) {
                mMarginDragging = true;
            }

            @Override public void onStopTrackingTouch(@NonNull Slider slider) {
                mMarginDragging = false;
                if (mListener != null) mListener.onSliderReleased();
            }
        });
        mSoft.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (mRestating || !isChecked || mListener == null)
                return;
            mListener.onFirstSegment(group.indexOfChild(group.findViewById(checkedId)));
        });
        mLegibility.addOnChangeListener((slider, value, fromUser) -> {
            if (mRestating || !fromUser || mListener == null)
                return;
            int index = Math.round(value);
            mLegibilityLabel.setText(legibilityLabel(index));
            mListener.onLegibility(index);
        });
    }

    // ------------------------------------------------------------------------------ the modes

    void showAppearanceMode() {
        setMode(false);
    }

    void showLayoutMode() {
        setMode(true);
    }

    /**
     * Shows one mode's rows and checks its segment, without reporting it. Row B keeps its state
     * across a visit to Layout mode: it is Appearance's, so it goes and comes back with that group.
     */
    void setMode(boolean layout) {
        int id = layout ? R.id.appearance_editor_mode_layout
            : R.id.appearance_editor_mode_appearance;
        if (mMode.getCheckedButtonId() != id) {
            mRestating = true;
            mMode.check(id);
            mRestating = false;
        }
        applyGroups(layout);
    }

    private void applyGroups(boolean layout) {
        mLayoutMode = layout;
        mAppearanceGroup.setVisibility(layout ? View.GONE : View.VISIBLE);
        mLayoutGroup.setVisibility(layout ? View.VISIBLE : View.GONE);
        mRow2.setVisibility(!layout && mRow2Shown ? View.VISIBLE : View.GONE);
    }

    boolean isLayoutMode() {
        return mLayoutMode;
    }

    // ------------------------------------------------------------- Layout mode's views, lent

    @NonNull MaterialButtonToggleGroup orientationToggle() {
        return mOrientation;
    }

    /** The trash: the drop target for hiding, the hidden list's anchor, the count badge's anchor. */
    @NonNull MaterialButton trash() {
        return mTrash;
    }

    // ------------------------------------------------------------------------- restatements

    /** Undo shows only while there is something to undo; the pill → Undo gap absorbs it. */
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

    /**
     * Checks the Style toggle's segment, without reporting it. Corners and Margin mean something
     * under both Styles (SPEC §3.7), so nothing else in the sheet changes when Style flips.
     */
    void setFloating(boolean floating) {
        int id = floating ? R.id.appearance_editor_style_floating
            : R.id.appearance_editor_style_docked;
        if (mStyle.getCheckedButtonId() == id)
            return;
        mRestating = true;
        mStyle.check(id);
        mRestating = false;
    }

    /** Whether Row B is up (kept, though its group is hidden, while Layout is shown). */
    boolean isRow2Shown() {
        return mRow2Shown;
    }

    /** Row B down: GONE, so the sheet's content (and the controller's height for it) shrinks. */
    void hideRow2() {
        if (!mRow2Shown)
            return;
        mRow2Shown = false;
        applyRow2(false, true);
    }

    /** Row B up, with the tapped element's name; the controls are set by the calls below. */
    void showRow2(@StringRes int name) {
        mRow2Name.setText(name);
        if (mRow2Shown)
            return;
        mRow2Shown = true;
        applyRow2(true, true);
    }

    /**
     * Row B's visibility, and whether its controls take touches and are read out. Row B is not
     * shown in Layout mode, whatever its state.
     */
    private void applyRow2(boolean shown, boolean animate) {
        mRow2.setImportantForAccessibility(shown ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        mFirstSlider.setEnabled(shown);
        for (int i = 0; i < mSoft.getChildCount(); i++)
            mSoft.getChildAt(i).setEnabled(shown);
        mLegibility.setEnabled(shown && mLegibilityEnabled);
        mSecondSlider.setEnabled(shown);
        mRow2.setVisibility(shown && !mLayoutMode ? View.VISIBLE : View.GONE);
    }

    /** The first control as a slider: Opacity or Key radius. */
    void setFirstSlider(@NonNull CharSequence label, int value, int max) {
        mFirstLabel.setText(label);
        mFirstSlider.setContentDescription(label);
        mFirstSlider.setVisibility(View.VISIBLE);
        mSoft.setVisibility(View.GONE);
        restateSlider(mFirstSlider, value, max);
        mFirstLabel.setVisibility(View.VISIBLE);
    }

    void setFirstLabel(@NonNull CharSequence label) {
        mFirstLabel.setText(label);
        mFirstSlider.setContentDescription(label);
    }

    /** The first control as Soften wallpaper's Off / On. */
    void setSoft(@NonNull CharSequence label, boolean on) {
        mFirstLabel.setText(label);
        mFirstSlider.setVisibility(View.GONE);
        mSoft.setVisibility(View.VISIBLE);
        checkSegment(mSoft, on ? 1 : 0);
        mFirstLabel.setVisibility(View.VISIBLE);
    }

    /** No first control: the status bar and the dock have Blur alone. */
    void hideFirst() {
        mFirstLabel.setVisibility(View.GONE);
        mFirstSlider.setVisibility(View.GONE);
        mSoft.setVisibility(View.GONE);
    }

    /**
     * The terminal's Text contrast at {@code index}. Not {@code enabled} where the terminal
     * palette is not the Material one it changes; the label then says so.
     */
    void setLegibility(@NonNull CharSequence label, int index, boolean enabled) {
        mLegibilityLabel.setText(label);
        restateSlider(mLegibility, index, 2);
        mLegibilityEnabled = enabled;
        mLegibility.setEnabled(enabled && mRow2Shown);
        mLegibilityLabel.setVisibility(View.VISIBLE);
        mLegibility.setVisibility(View.VISIBLE);
    }

    /** The label for a Text contrast stop: Low, Normal or High. */
    @NonNull
    String legibilityLabel(int index) {
        return mContext.getString(index <= 0 ? R.string.appearance_editor_legibility_low
            : index == 1 ? R.string.appearance_editor_legibility_normal
            : R.string.appearance_editor_legibility_high);
    }

    void hideLegibility() {
        mLegibilityLabel.setVisibility(View.GONE);
        mLegibility.setVisibility(View.GONE);
    }

    /** The last control: Blur or Dim. */
    void setSecondSlider(@NonNull CharSequence label, int value, int max) {
        mSecondLabel.setText(label);
        mSecondSlider.setContentDescription(label);
        restateSlider(mSecondSlider, value, max);
        mSecondLabel.setVisibility(View.VISIBLE);
        mSecondSlider.setVisibility(View.VISIBLE);
    }

    void setSecondLabel(@NonNull CharSequence label) {
        mSecondLabel.setText(label);
        mSecondSlider.setContentDescription(label);
    }

    // ------------------------------------------------------------ Layout mode's own controls

    /** Layout mode's Corners, at {@code value} dp of {@code max}. */
    void setCorners(@NonNull CharSequence label, int value, int max) {
        mCornersLabel.setText(label);
        restateSlider(mCorners, value, max);
    }

    void setCornersLabel(@NonNull CharSequence label) {
        mCornersLabel.setText(label);
    }

    /** Layout mode's Margin, at {@code value} dp of {@code max}. */
    void setMargin(@NonNull CharSequence label, int value, int max) {
        mMarginLabel.setText(label);
        restateSlider(mMargin, value, max);
    }

    void setMarginLabel(@NonNull CharSequence label) {
        mMarginLabel.setText(label);
    }

    private void checkSegment(@NonNull MaterialButtonToggleGroup group, int index) {
        View child = group.getChildAt(Math.max(0, Math.min(group.getChildCount() - 1, index)));
        if (child == null || group.getCheckedButtonId() == child.getId())
            return;
        mRestating = true;
        group.check(child.getId());
        mRestating = false;
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
        return com.google.android.material.R.style.TextAppearance_Material3_LabelMedium;
    }

    private float dp(float value) {
        return value * mContext.getResources().getDisplayMetrics().density;
    }
}
