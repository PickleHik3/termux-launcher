package com.termux.app.surfaces;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.StaticLayout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.PopupMenu;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Group;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.slider.Slider;

import com.termux.R;

import java.util.HashMap;
import java.util.Map;

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
 *
 * <p>Layout editor v2 (DECISIONS items 2, 3, 10, 12, 13 and 15): Layout's Row A ends in eye-off,
 * the only hide drop target, whose tap opens the hidden tiles in Row B's place; Appearance's Row B
 * carries the global Blur, Opacity and Grain at the Custom stop with nothing tapped, and the
 * keyboard's Blur with its "Keyboard theme" door. Selected segments wear primaryContainer and
 * onPrimaryContainer. While the keyboard is selected its type chips and Key radius take Layout's
 * Row B in the same way.</p>
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
        /** Row B's first slider (Opacity, the keyboard's Blur, or the global Blur). */
        void onFirstSlider(int value, boolean dragging);
        /** Row B's middle slider: the global Opacity. */
        void onMiddleSlider(int value, boolean dragging);
        /** Row B's last column as a button: the keyboard's "Keyboard theme". */
        void onSecondButton();
        /** Row B's first segmented control (Soften wallpaper), by segment index. */
        void onFirstSegment(int index);
        /** The terminal's Text contrast, by stop: 0 Low (Softer), 1 Normal (Default), 2 High (Harder). */
        void onLegibility(int index);
        /** The terminal's Trail menu: the cursor-trail style id the user chose. */
        void onTrailStyle(@NonNull String id);
        /** The terminal's Effect menu: the retro effect id the user chose. */
        void onRetroEffect(@NonNull String id);
        /** Row B's last slider (Blur, Dim or the global Grain). */
        void onSecondSlider(int value, boolean dragging);
        /** The terminal's fourth column: its Grain. */
        void onThirdSlider(int value, boolean dragging);
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
    private final int mBasePaddingStart;
    private final int mBasePaddingEnd;

    private final MaterialButtonToggleGroup mMode;
    private final MaterialButton mUndo;
    private final MaterialButton mDone;
    private final Group mAppearanceGroup;
    private final Group mLayoutGroup;

    private final Slider mLook;
    private final FrameLayout mLookLabels;
    private final TextView[] mLookLabelViews = new TextView[AppearanceLooks.STOP_COUNT];
    /** Each label's own typeface as its text appearance set it (family and weight kept). */
    private final Typeface[] mLookLabelBase = new Typeface[AppearanceLooks.STOP_COUNT];

    private final View mRow2;
    private final TextView mRow2Name;
    private final TextView mFirstLabel;
    private final Slider mFirstSlider;
    private final MaterialButtonToggleGroup mSoft;
    private final TextView mLegibilityLabel;
    private final Slider mLegibility;
    private final MaterialButton mTrail;
    private final MaterialButton mEffect;
    private final TextView mTrailLabel;
    private final TextView mEffectLabel;
    private final TextView mSecondLabel;
    private final Slider mSecondSlider;
    private final Slider mMiddleSlider;
    private final TextView mThirdLabel;
    private final Slider mThirdSlider;
    private final MaterialButton mSecondButton;

    private final MaterialButtonToggleGroup mOrientation;
    private final MaterialButtonToggleGroup mStyle;
    private final MaterialButton mHidden;
    private final View mHiddenHighlight;
    private final HorizontalScrollView mHiddenTiles;
    private final ChipGroup mHiddenTileGroup;
    private final TextView mCornersLabel;
    private final Slider mCorners;
    private final TextView mMarginLabel;
    private final Slider mMargin;
    /** The keyboard's tools in Row B: its type chips and Key radius, lent to Layout mode. */
    private final View mKeyboardTools;
    private final ChipGroup mKeyboardForms;
    private final TextView mKeyRadiusLabel;
    /**
     * What each readout label may say while its control moves (the current value and the widest
     * one). The label is measured to the widest, so a drag never adds a line and moves the slider
     * under the finger: the sheet's height is measured once, not per tick.
     */
    private final Map<TextView, CharSequence[]> mReadoutRange = new HashMap<>();
    /** Row B's label lines while measureTallest sizes the frame's anchor: 2, otherwise 1. */
    private int mRow2MinLines = 1;
    private final Slider mKeyRadius;

    /** Whether Layout mode's rows are showing in place of Appearance's. */
    private boolean mLayoutMode;
    /** Whether Row B is up (an element is tapped at the Custom stop); kept across Layout mode. */
    private boolean mRow2Shown;
    /** Whether Text contrast may be moved (the Material palette is on). */
    private boolean mLegibilityEnabled = true;
    @NonNull private String mTrailId = "default";
    @NonNull private String mEffectId = "none";
    /** Whether the hidden tiles stand in Layout's Row B, in Corner radius and Margin's place. */
    private boolean mHiddenTilesOpen;
    /** Whether the keyboard's tools stand there (the keyboard is selected); the tiles win. */
    private boolean mKeyboardToolsShown;

    @Nullable private Listener mListener;
    private boolean mRestating;
    private boolean mFirstDragging;
    private boolean mSecondDragging;
    private boolean mMiddleDragging;
    private boolean mThirdDragging;
    private boolean mCornersDragging;
    private boolean mMarginDragging;

    private AppearanceEditorPanel(@NonNull Context context, @NonNull View root) {
        mContext = context;
        mRoot = root;
        mBasePaddingBottom = root.getPaddingBottom();
        mBasePaddingStart = root.getPaddingStart();
        mBasePaddingEnd = root.getPaddingEnd();
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
        mTrail = root.findViewById(R.id.appearance_editor_trail);
        mEffect = root.findViewById(R.id.appearance_editor_effect);
        mTrailLabel = root.findViewById(R.id.appearance_editor_trail_label);
        mEffectLabel = root.findViewById(R.id.appearance_editor_effect_label);
        mSecondLabel = root.findViewById(R.id.appearance_editor_c2_label);
        mSecondSlider = root.findViewById(R.id.appearance_editor_c2_slider);
        mMiddleSlider = root.findViewById(R.id.appearance_editor_cl_slider);
        mThirdLabel = root.findViewById(R.id.appearance_editor_c3_label);
        mThirdSlider = root.findViewById(R.id.appearance_editor_c3_slider);
        mSecondButton = root.findViewById(R.id.appearance_editor_c2_button);
        mOrientation = root.findViewById(R.id.layout_editor_orientation);
        mStyle = root.findViewById(R.id.appearance_editor_style);
        mHidden = root.findViewById(R.id.layout_editor_hidden);
        TooltipCompat.setTooltipText(mHidden,
            context.getString(R.string.appearance_editor_hidden_tooltip));
        mHiddenHighlight = root.findViewById(R.id.layout_editor_hidden_highlight);
        mHiddenTiles = root.findViewById(R.id.layout_editor_hidden_tiles);
        mHiddenTileGroup = root.findViewById(R.id.layout_editor_hidden_tile_group);
        mCornersLabel = root.findViewById(R.id.appearance_editor_corners_label);
        mCorners = root.findViewById(R.id.appearance_editor_corners);
        mMarginLabel = root.findViewById(R.id.appearance_editor_margin_label);
        mMargin = root.findViewById(R.id.appearance_editor_margin);
        mKeyboardTools = root.findViewById(R.id.layout_editor_keyboard_tools);
        mKeyboardForms = root.findViewById(R.id.layout_editor_keyboard_forms);
        mKeyRadiusLabel = root.findViewById(R.id.layout_editor_key_radius_label);
        mKeyRadius = root.findViewById(R.id.layout_editor_key_radius);
        // The groups' members, stated here as well as in the layout: a Group resolves its XML
        // names lazily, and the mode is applied before the panel is ever measured or attached.
        mAppearanceGroup.setReferencedIds(new int[] {R.id.appearance_editor_look,
            R.id.appearance_editor_look_labels});
        mLayoutGroup.setReferencedIds(new int[] {R.id.layout_editor_orientation,
            R.id.appearance_editor_style, R.id.layout_editor_hidden});
        applyGroups(false);
        applyRow2(false, false);
        paintSheet();
        paintSelectedSegments(mOrientation);
        paintSelectedSegments(mStyle);
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
     * The side padding: the layout's own plus the display's side insets (a camera cutout or a
     * navigation bar in landscape) under the sheet's content. {@code leftPx} and {@code rightPx}
     * are physical, so they are mapped to start and end by the panel's layout direction. The
     * sheet's background still runs edge to edge; only its content stands clear.
     */
    void setSideInsets(int leftPx, int rightPx) {
        boolean rtl = mRoot.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int start = mBasePaddingStart + Math.max(0, rtl ? rightPx : leftPx);
        int end = mBasePaddingEnd + Math.max(0, rtl ? leftPx : rightPx);
        if (mRoot.getPaddingStart() != start || mRoot.getPaddingEnd() != end)
            mRoot.setPaddingRelative(start, mRoot.getPaddingTop(), end, mRoot.getPaddingBottom());
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
            return measureLayoutTallest(widthPx);
        boolean shown = mLayoutMode;
        boolean rowShown = mRow2Shown;
        mRow2Shown = true;
        mRow2MinLines = 2;
        // The terminal's Trail row is the tallest Row B gets, so the frame stands above it.
        int trailVisibility = mTrail.getVisibility();
        setTrailVisibility(View.VISIBLE);
        applyGroups(false);
        // Row B at its fullest, whichever element (or none) is tapped now: the first and middle
        // slots shown, so the anchor does not depend on what Row B happens to hold.
        View[] slots = {mFirstLabel, mFirstSlider, mLegibilityLabel, mLegibility, mThirdLabel,
            mThirdSlider};
        int[] slotVisibility = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            slotVisibility[i] = slots[i].getVisibility();
            slots[i].setVisibility(View.VISIBLE);
        }
        int height = measureNow(widthPx);
        // The keyboard's "Keyboard theme" door may wrap to two lines in a narrow column, which
        // can stand taller than a slider: the tallest Row B is measured with it too.
        int sliderVisibility = mSecondSlider.getVisibility();
        int buttonVisibility = mSecondButton.getVisibility();
        mSecondSlider.setVisibility(View.GONE);
        mSecondButton.setVisibility(View.VISIBLE);
        height = Math.max(height, measureNow(widthPx));
        mSecondSlider.setVisibility(sliderVisibility);
        mSecondButton.setVisibility(buttonVisibility);
        for (int i = 0; i < slots.length; i++)
            slots[i].setVisibility(slotVisibility[i]);
        setTrailVisibility(trailVisibility);
        mRow2MinLines = 1;
        mRow2Shown = rowShown;
        applyGroups(shown);
        return height;
    }

    /**
     * Layout mode's height: Row B as Corner radius and Margin, and as the keyboard's tools in
     * their place, whichever stands taller, so the sheet does not move when the keyboard is
     * selected. The state showing is restored before returning.
     */
    private int measureLayoutTallest(int widthPx) {
        boolean shown = mLayoutMode;
        boolean tools = mKeyboardToolsShown;
        boolean tiles = mHiddenTilesOpen;
        mHiddenTilesOpen = false;
        mKeyboardToolsShown = false;
        applyGroups(true);
        int height = measureNow(widthPx);
        mKeyboardToolsShown = true;
        applyLayoutRow2();
        height = Math.max(height, measureNow(widthPx));
        mKeyboardToolsShown = tools;
        mHiddenTilesOpen = tiles;
        applyGroups(shown);
        return height;
    }

    /**
     * The header is one row (mode pill, Undo, Done) while it fits the width the sheet's padding
     * leaves, which includes the display's side insets. When it does not, Undo and Done drop to a
     * second row at the end edge, 8dp under the pill, and the barrier the other rows hang from
     * follows. Touch targets and text sizes are untouched.
     */
    private void adaptHeader(int widthPx) {
        if (!(mUndo.getLayoutParams() instanceof ConstraintLayout.LayoutParams)
            || !(mDone.getLayoutParams() instanceof ConstraintLayout.LayoutParams))
            return;
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        mMode.measure(unspecified, unspecified);
        mDone.measure(unspecified, unspecified);
        int need = mMode.getMeasuredWidth() + mDone.getMeasuredWidth();
        if (mUndo.getVisibility() != View.GONE) {
            mUndo.measure(unspecified, unspecified);
            need += mUndo.getMeasuredWidth();
        }
        int available = widthPx - mRoot.getPaddingStart() - mRoot.getPaddingEnd();
        boolean wrap = need > available;
        for (View view : new View[] {mUndo, mDone}) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) view.getLayoutParams();
            int unset = ConstraintLayout.LayoutParams.UNSET;
            int topTop = wrap ? unset : R.id.appearance_editor_mode;
            int topBottom = wrap ? R.id.appearance_editor_mode : unset;
            int bottomBottom = wrap ? unset : R.id.appearance_editor_mode;
            int margin = wrap ? Math.round(dp(8)) : 0;
            if (lp.topToTop != topTop || lp.topToBottom != topBottom
                || lp.bottomToBottom != bottomBottom || lp.topMargin != margin) {
                lp.topToTop = topTop;
                lp.topToBottom = topBottom;
                lp.bottomToBottom = bottomBottom;
                lp.topMargin = margin;
                view.setLayoutParams(lp);
            }
        }
    }

    private int measureNow(int widthPx) {
        int width = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        // Relative compound icons contribute width after inherited direction is resolved.
        mRoot.measure(width, height);
        reserveReadoutLines();
        adaptHeader(widthPx);
        mRoot.measure(width, height);
        return mRoot.getMeasuredHeight();
    }

    /**
     * Each readout label's lines, from the column width the first measure gave it: as many as
     * the widest thing it may say needs (see mReadoutRange), at least mRow2MinLines in Row B.
     */
    private void reserveReadoutLines() {
        TextView[] labels = {mFirstLabel, mLegibilityLabel, mThirdLabel, mSecondLabel,
            mCornersLabel, mMarginLabel};
        for (TextView label : labels) {
            int lines = label == mCornersLabel || label == mMarginLabel ? 1 : mRow2MinLines;
            CharSequence[] range = mReadoutRange.get(label);
            int available = label.getMeasuredWidth() - label.getCompoundPaddingLeft()
                - label.getCompoundPaddingRight();
            if (range != null && label.getVisibility() != View.GONE && available > 0) {
                for (CharSequence text : range) {
                    StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(),
                        label.getPaint(), available).build();
                    lines = Math.max(lines, layout.getLineCount());
                }
            }
            label.setMinLines(Math.min(lines, label.getMaxLines()));
        }
    }

    /**
     * {@code label} and the same readout with its number at {@code max}'s digit count (Roboto's
     * figures are all one width, so 0s stand for any value up to {@code max}).
     */
    private CharSequence[] readoutRange(@NonNull CharSequence label, int max) {
        String text = label.toString();
        int cut = text.indexOf(" \u00b7 ");
        if (cut <= 0)
            return new CharSequence[] {readout(label)};
        int digits = String.valueOf(Math.max(0, max)).length();
        StringBuilder widest = new StringBuilder(text.substring(0, cut + 3));
        String value = text.substring(cut + 3);
        for (int i = 0; i < value.length(); ) {
            if (!Character.isDigit(value.charAt(i))) {
                widest.append(value.charAt(i++));
                continue;
            }
            int run = 0;
            while (i < value.length() && Character.isDigit(value.charAt(i))) {
                i++;
                run++;
            }
            for (int d = Math.max(run, digits); d > 0; d--)
                widest.append('0');
        }
        return new CharSequence[] {readout(label), readout(widest)};
    }

    /**
     * The selected segment of an icon toggle in primaryContainer with its glyph in
     * onPrimaryContainer, the others clear with the glyph in onSurfaceVariant (DECISIONS item 12):
     * theme roles only, resolved against the active scheme.
     */
    private void paintSelectedSegments(@NonNull MaterialButtonToggleGroup group) {
        int container = MaterialColors.getColor(mRoot,
            com.google.android.material.R.attr.colorPrimaryContainer,
            ContextCompat.getColor(mContext, R.color.termux_primary));
        int onContainer = MaterialColors.getColor(mRoot,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            ContextCompat.getColor(mContext, R.color.termux_on_surface));
        int quiet = MaterialColors.getColor(mRoot,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            ContextCompat.getColor(mContext, R.color.termux_on_surface));
        int[][] states = {{android.R.attr.state_checked}, {}};
        ColorStateList fill = new ColorStateList(states, new int[] {container, 0});
        ColorStateList ink = new ColorStateList(states, new int[] {onContainer, quiet});
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof MaterialButton))
                continue;
            MaterialButton button = (MaterialButton) child;
            button.setBackgroundTintList(fill);
            button.setIconTint(ink);
        }
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
            mLookLabelBase[i] = label.getTypeface();
            // The chosen stop is bold, so its width changes: centre it on what it now measures.
            label.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (r - l != or - ol) placeLookLabels();
            });
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
            // Measured with the paint it is drawn with now (bold when chosen), never a stale width.
            label.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int labelWidth = label.getMeasuredWidth();
            float along = span * i / last;
            float centre = offset + (rtl ? width - pad - along : pad + along);
            float x = Math.max(0, Math.min(mLookLabels.getWidth() - labelWidth,
                centre - labelWidth / 2f));
            label.setTranslationX(x);
        }
    }

    /**
     * A "Name · value" label with the name quiet and the value on the primary text role, so a
     * read-out stays a number beside a name instead of one fused string. Text without the
     * separator passes through untouched.
     */
    private CharSequence readout(@NonNull CharSequence label) {
        String text = label.toString();
        int cut = text.indexOf(" \u00b7 ");
        if (cut <= 0) return label;
        int quiet = MaterialColors.getColor(mRoot,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            ContextCompat.getColor(mContext, R.color.termux_on_surface));
        int strong = MaterialColors.getColor(mRoot,
            com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(mContext, R.color.termux_on_surface));
        SpannableStringBuilder out = new SpannableStringBuilder(text);
        out.setSpan(new ForegroundColorSpan(quiet), 0, cut, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(strong), cut + 3, text.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new StyleSpan(Typeface.BOLD), cut + 3, text.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return out;
    }

    private void styleLookLabels(int stop) {
        int active = MaterialColors.getColor(mRoot,
            androidx.appcompat.R.attr.colorPrimary,
            ContextCompat.getColor(mContext, R.color.termux_primary));
        int quiet = MaterialColors.getColor(mRoot,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            ContextCompat.getColor(mContext, R.color.termux_on_surface));
        for (int i = 0; i < mLookLabelViews.length; i++) {
            TextView label = mLookLabelViews[i];
            label.setTextColor(i == stop ? active : quiet);
            // The chosen stop reads by weight as well as colour.
            Typeface base = mLookLabelBase[i] != null ? mLookLabelBase[i] : label.getTypeface();
            label.setTypeface(i == stop ? Typeface.create(base, Typeface.BOLD) : base);
            label.setSelected(i == stop);
            label.requestLayout();
        }
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
        mMiddleSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (mRestating || !fromUser || mListener == null)
                return;
            mListener.onMiddleSlider(Math.round(value), mMiddleDragging);
        });
        mMiddleSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider slider) {
                mMiddleDragging = true;
            }

            @Override public void onStopTrackingTouch(@NonNull Slider slider) {
                mMiddleDragging = false;
                if (mListener != null) mListener.onSliderReleased();
            }
        });
        mThirdSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (mRestating || !fromUser || mListener == null)
                return;
            mListener.onThirdSlider(Math.round(value), mThirdDragging);
        });
        mThirdSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider slider) {
                mThirdDragging = true;
            }

            @Override public void onStopTrackingTouch(@NonNull Slider slider) {
                mThirdDragging = false;
                if (mListener != null) mListener.onSliderReleased();
            }
        });
        mSecondButton.setOnClickListener(view -> {
            if (mListener != null) mListener.onSecondButton();
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
        mTrail.setOnClickListener(view -> showChoiceMenu(mTrail,
            R.array.settings_terminal_cursor_trail_style_entries,
            R.array.settings_terminal_cursor_trail_style_values, mTrailId, id -> {
                mTrailId = id;
                restateTrail();
                if (mListener != null) mListener.onTrailStyle(id);
            }));
        mEffect.setOnClickListener(view -> showChoiceMenu(mEffect,
            R.array.settings_terminal_retro_effect_entries,
            R.array.settings_terminal_retro_effect_values, mEffectId, id -> {
                mEffectId = id;
                restateEffect();
                if (mListener != null) mListener.onRetroEffect(id);
            }));
        mLegibility.addOnChangeListener((slider, value, fromUser) -> {
            if (mRestating || !fromUser || mListener == null)
                return;
            int index = Math.round(value);
            mLegibilityLabel.setText(readout(legibilityLabel(index)));
            mListener.onLegibility(index);
        });
    }

    // ------------------------------------------------------------- Trail and Effect menus

    private interface Choice {
        void chosen(@NonNull String id);
    }

    /** Opens the entries/values arrays as a single-choice menu on {@code anchor}. */
    private void showChoiceMenu(@NonNull View anchor, int entriesRes, int valuesRes,
                                @NonNull String current, @NonNull Choice choice) {
        String[] entries = mContext.getResources().getStringArray(entriesRes);
        String[] values = mContext.getResources().getStringArray(valuesRes);
        PopupMenu menu = new PopupMenu(mContext, anchor);
        for (int i = 0; i < entries.length && i < values.length; i++) {
            MenuItem item = menu.getMenu().add(Menu.NONE, i, i, entries[i]);
            item.setCheckable(true);
            item.setChecked(values[i].equals(current));
        }
        menu.getMenu().setGroupCheckable(Menu.NONE, true, true);
        menu.setOnMenuItemClickListener(item -> {
            int index = item.getItemId();
            if (index >= 0 && index < values.length && !values[index].equals(current))
                choice.chosen(values[index]);
            return true;
        });
        menu.show();
    }

    @NonNull
    private String optionLabel(int entriesRes, int valuesRes, @NonNull String id) {
        String[] entries = mContext.getResources().getStringArray(entriesRes);
        String[] values = mContext.getResources().getStringArray(valuesRes);
        for (int i = 0; i < values.length && i < entries.length; i++)
            if (values[i].equals(id)) return entries[i];
        return entries.length > 0 ? entries[0] : id;
    }

    /**
     * The terminal's Trail (and, where {@code effectAvailable}, Effect) at the stored ids. An id
     * this build does not know reads as the first option, as the preference's own getter does.
     */
    void setTerminalLooks(@NonNull String trailId, @NonNull String effectId,
                          boolean effectAvailable) {
        mTrailId = trailId;
        mEffectId = effectId;
        restateTrail();
        restateEffect();
        setTrailVisibility(View.VISIBLE);
        setEffectVisibility(effectAvailable ? View.VISIBLE : View.GONE);
        mTrail.setEnabled(mRow2Shown);
        mEffect.setEnabled(mRow2Shown);
    }

    void hideTerminalLooks() {
        setTrailVisibility(View.GONE);
        setEffectVisibility(View.GONE);
    }

    /** A label always follows its button. */
    private void setTrailVisibility(int visibility) {
        mTrail.setVisibility(visibility);
        mTrailLabel.setVisibility(visibility);
    }

    private void setEffectVisibility(int visibility) {
        mEffect.setVisibility(visibility);
        mEffectLabel.setVisibility(visibility);
    }

    private void restateTrail() {
        String label = optionLabel(R.array.settings_terminal_cursor_trail_style_entries,
            R.array.settings_terminal_cursor_trail_style_values, mTrailId);
        mTrail.setText(label);
        mTrail.setContentDescription(
            mContext.getString(R.string.appearance_editor_trail_description, label));
    }

    private void restateEffect() {
        String label = optionLabel(R.array.settings_terminal_retro_effect_entries,
            R.array.settings_terminal_retro_effect_values, mEffectId);
        mEffect.setText(label);
        mEffect.setContentDescription(
            mContext.getString(R.string.appearance_editor_effect_description, label));
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
        applyLayoutRow2();
    }

    /**
     * Layout's Row B: Corner radius and Margin, or in their place the hidden tiles, or the
     * keyboard's tools while the keyboard is selected (the tiles win while both would). Corner
     * radius and Margin go INVISIBLE rather than GONE while either stands there, so the row keeps
     * their height and the sheet never moves.
     */
    private void applyLayoutRow2() {
        boolean tiles = mLayoutMode && mHiddenTilesOpen;
        boolean tools = mLayoutMode && mKeyboardToolsShown && !tiles;
        boolean swapped = tiles || tools;
        int shape = !mLayoutMode ? View.GONE : swapped ? View.INVISIBLE : View.VISIBLE;
        for (View view : new View[] {mCornersLabel, mMarginLabel, mCorners, mMargin}) {
            if (view.getVisibility() != shape)
                view.setVisibility(shape);
        }
        mCorners.setEnabled(mLayoutMode && !swapped);
        mMargin.setEnabled(mLayoutMode && !swapped);
        int tileVisibility = tiles ? View.VISIBLE : View.GONE;
        if (mHiddenTiles.getVisibility() != tileVisibility)
            mHiddenTiles.setVisibility(tileVisibility);
        int toolsVisibility = tools ? View.VISIBLE : View.GONE;
        if (mKeyboardTools.getVisibility() != toolsVisibility)
            mKeyboardTools.setVisibility(toolsVisibility);
    }

    boolean isLayoutMode() {
        return mLayoutMode;
    }

    // ------------------------------------------------------------- Layout mode's views, lent

    @NonNull MaterialButtonToggleGroup orientationToggle() {
        return mOrientation;
    }

    /**
     * Eye-off: the only drop target for hiding, the hidden tiles' toggle and the count badge's
     * anchor (DECISIONS item 2).
     */
    @NonNull MaterialButton hiddenControl() {
        return mHidden;
    }

    /** The 64dp highlight behind eye-off: its rect is the drop area the canvas is given. */
    @NonNull View hiddenHighlight() {
        return mHiddenHighlight;
    }

    /** The chips of the hidden tiles, filled by the layout controller. */
    @NonNull ChipGroup hiddenTileGroup() {
        return mHiddenTileGroup;
    }

    /**
     * Opens or closes the hidden tiles in Row B's place (DECISIONS item 3). The sheet's height
     * does not change either way.
     */
    void setHiddenTilesOpen(boolean open) {
        if (mHiddenTilesOpen == open)
            return;
        mHiddenTilesOpen = open;
        applyLayoutRow2();
    }

    boolean isHiddenTilesOpen() {
        return mHiddenTilesOpen;
    }

    /**
     * Puts the keyboard's tools in Row B's place while the keyboard is selected, or brings Corner
     * radius and Margin back (DECISIONS item 6). The sheet's height does not change either way.
     */
    void setKeyboardToolsShown(boolean shown) {
        if (mKeyboardToolsShown == shown)
            return;
        mKeyboardToolsShown = shown;
        applyLayoutRow2();
    }

    /** Whether the keyboard's tools stand in Row B now. */
    boolean isKeyboardToolsShown() {
        return mKeyboardTools.getVisibility() == View.VISIBLE;
    }

    /** The keyboard's tools, the row holding its type chips and Key radius. */
    @NonNull View keyboardTools() {
        return mKeyboardTools;
    }

    /** The keyboard's type chips: Docked, Floating and Split. */
    @NonNull ChipGroup keyboardForms() {
        return mKeyboardForms;
    }

    @NonNull TextView keyRadiusLabel() {
        return mKeyRadiusLabel;
    }

    @NonNull Slider keyRadius() {
        return mKeyRadius;
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
        mMiddleSlider.setEnabled(shown);
        mThirdSlider.setEnabled(shown);
        mSecondSlider.setEnabled(shown);
        mTrail.setEnabled(shown);
        mEffect.setEnabled(shown);
        mSecondButton.setEnabled(shown);
        mRow2.setVisibility(shown && !mLayoutMode ? View.VISIBLE : View.GONE);
    }

    /** The first control as a slider: Opacity, the keyboard's Blur, or the global Blur. */
    void setFirstSlider(@NonNull CharSequence label, int value, int max) {
        mFirstLabel.setText(readout(label));
        mReadoutRange.put(mFirstLabel, readoutRange(label, max));
        mFirstSlider.setContentDescription(label);
        mFirstSlider.setVisibility(View.VISIBLE);
        mSoft.setVisibility(View.GONE);
        restateSlider(mFirstSlider, value, max);
        mFirstLabel.setVisibility(View.VISIBLE);
    }

    void setFirstLabel(@NonNull CharSequence label) {
        mFirstLabel.setText(readout(label));
        mFirstSlider.setContentDescription(label);
    }

    /** The first control as Soften wallpaper's Off / On. */
    void setSoft(@NonNull CharSequence label, boolean on) {
        mFirstLabel.setText(readout(label));
        mReadoutRange.remove(mFirstLabel);
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
        mLegibilityLabel.setText(readout(label));
        mReadoutRange.put(mLegibilityLabel, new CharSequence[] {readout(label),
            readout(legibilityLabel(0)), readout(legibilityLabel(1)), readout(legibilityLabel(2))});
        restateSlider(mLegibility, index, 2);
        mLegibilityEnabled = enabled;
        mLegibility.setEnabled(enabled && mRow2Shown);
        mLegibilityLabel.setVisibility(View.VISIBLE);
        mLegibility.setVisibility(View.VISIBLE);
        mMiddleSlider.setVisibility(View.GONE);
    }

    /** The middle column as a slider of its own: the global Opacity (DECISIONS item 13). */
    void setMiddleSlider(@NonNull CharSequence label, int value, int max) {
        mLegibilityLabel.setText(readout(label));
        mReadoutRange.put(mLegibilityLabel, readoutRange(label, max));
        mMiddleSlider.setContentDescription(label);
        restateSlider(mMiddleSlider, value, max);
        mMiddleSlider.setEnabled(mRow2Shown);
        mLegibility.setVisibility(View.GONE);
        mLegibilityLabel.setVisibility(View.VISIBLE);
        mMiddleSlider.setVisibility(View.VISIBLE);
    }

    void setMiddleLabel(@NonNull CharSequence label) {
        mLegibilityLabel.setText(readout(label));
        mMiddleSlider.setContentDescription(label);
    }

    /** The label for a Text contrast stop: Low, Normal or High. */
    @NonNull
    String legibilityLabel(int index) {
        return mContext.getString(index <= 0 ? R.string.appearance_editor_legibility_low
            : index == 1 ? R.string.appearance_editor_legibility_normal
            : R.string.appearance_editor_legibility_high);
    }

    /** No middle column: neither Text contrast nor the global Opacity. */
    void hideLegibility() {
        mLegibilityLabel.setVisibility(View.GONE);
        mLegibility.setVisibility(View.GONE);
        mMiddleSlider.setVisibility(View.GONE);
    }

    /** The last control: Blur, Dim or the global Grain. */
    void setSecondSlider(@NonNull CharSequence label, int value, int max) {
        mSecondLabel.setText(readout(label));
        mReadoutRange.put(mSecondLabel, readoutRange(label, max));
        mSecondSlider.setContentDescription(label);
        restateSlider(mSecondSlider, value, max);
        mSecondLabel.setVisibility(View.VISIBLE);
        mSecondSlider.setVisibility(View.VISIBLE);
        mSecondButton.setVisibility(View.GONE);
    }

    /**
     * The last column as a button: the keyboard's "Keyboard theme" door (DECISIONS item 15). Its
     * label line stays, empty, so the button stands level with the slider beside it.
     */
    void setSecondButton(@NonNull CharSequence text) {
        mSecondLabel.setText("");
        mReadoutRange.remove(mSecondLabel);
        mSecondButton.setText(text);
        mSecondButton.setEnabled(mRow2Shown);
        mSecondLabel.setVisibility(View.VISIBLE);
        mSecondSlider.setVisibility(View.GONE);
        mSecondButton.setVisibility(View.VISIBLE);
    }

    /** Whether the last column is the button, for a test to read. */
    boolean isSecondButtonShown() {
        return mSecondButton.getVisibility() == View.VISIBLE;
    }

    /** Whether the middle column is the global Opacity slider, for a test to read. */
    boolean isMiddleSliderShown() {
        return mMiddleSlider.getVisibility() == View.VISIBLE;
    }

    /** The fourth column as a slider: the terminal's Grain. */
    void setThirdSlider(@NonNull CharSequence label, int value, int max) {
        mThirdLabel.setText(readout(label));
        mReadoutRange.put(mThirdLabel, readoutRange(label, max));
        mThirdSlider.setContentDescription(label);
        restateSlider(mThirdSlider, value, max);
        mThirdSlider.setEnabled(mRow2Shown);
        mThirdLabel.setVisibility(View.VISIBLE);
        mThirdSlider.setVisibility(View.VISIBLE);
    }

    void setThirdLabel(@NonNull CharSequence label) {
        mThirdLabel.setText(readout(label));
        mThirdSlider.setContentDescription(label);
    }

    /** No fourth column: every element but the terminal. */
    void hideThird() {
        mThirdLabel.setVisibility(View.GONE);
        mThirdSlider.setVisibility(View.GONE);
    }

    void setSecondLabel(@NonNull CharSequence label) {
        mSecondLabel.setText(readout(label));
        mSecondSlider.setContentDescription(label);
    }

    // ------------------------------------------------------------ Layout mode's own controls

    /** Layout mode's Corners, at {@code value} dp of {@code max}. */
    void setCorners(@NonNull CharSequence label, int value, int max) {
        mCornersLabel.setText(readout(label));
        mReadoutRange.put(mCornersLabel, readoutRange(label, max));
        restateSlider(mCorners, value, max);
    }

    void setCornersLabel(@NonNull CharSequence label) {
        mCornersLabel.setText(readout(label));
    }

    /** Layout mode's Margin, at {@code value} dp of {@code max}. */
    void setMargin(@NonNull CharSequence label, int value, int max) {
        mMarginLabel.setText(readout(label));
        mReadoutRange.put(mMarginLabel, readoutRange(label, max));
        restateSlider(mMargin, value, max);
    }

    void setMarginLabel(@NonNull CharSequence label) {
        mMarginLabel.setText(readout(label));
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
