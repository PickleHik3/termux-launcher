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
import android.widget.LinearLayout;
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
import com.termux.app.surfaces.AppearanceLooks.Control;
import com.termux.app.surfaces.AppearanceLooks.Door;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Appearance / Layout editor's bottom area (SPEC §3.2–3.6): views only. It inflates
 * {@code appearance_editor_panel}, paints it as an M3 sheet surface, places a label under each
 * stop of the Look slider, and reports what the user did through {@link Listener}. What any of it
 * means is {@link SurfaceEditorController}'s and
 * {@link com.termux.app.layouteditor.LayoutEditorController}'s.
 *
 * <p>The layout is one ConstraintLayout: each
 * mode's rows are a {@link Group}, and a mode switch shows one and hides the other. Row B at
 * Appearance is the Custom row: a heading row with the selection's buttons over up to five
 * vertical {@link LegendSlider}s. It is GONE at a Look stop, so the sheet's height follows its
 * content: {@link #measureFor} is the height for what shows now, and {@link #measureTallest} the
 * height with Row B up, which the frame stands above. Row B is one height whatever is selected.</p>
 *
 * <p>Every restatement from code runs with {@link #mRestating} set, so a value the controller
 * pushes in is never read back as the user's.</p>
 *
 * <p>Layout mode's Row A ends in eye-off, the only hide drop target, whose tap opens the hidden
 * tiles in Row B's place. While the keyboard is selected its type chips and Key radius take
 * Layout's Row B in the same way. Selected segments wear primaryContainer and
 * onPrimaryContainer.</p>
 */
final class AppearanceEditorPanel {

    /** What the user did in the bottom area. */
    interface Listener {
        /** The Look slider settled on a stop (a drag reports each stop it crosses). */
        void onLookStop(int stop);
        /** Layout mode's Style toggle. */
        void onStyle(boolean floating);
        /** Layout mode's Corners slider, in dp. */
        void onCorners(int value, boolean dragging);
        /** Layout mode's Margin slider, in dp. */
        void onMargin(int value, boolean dragging);
        /** One of the Custom row's sliders moved to {@code value}, in the control's own units. */
        void onSlider(@NonNull Control control, int value, boolean dragging);
        /** A slider was released; deferred work settles here. */
        void onSliderReleased();
        /** A button of the heading row: Keyboard theme, or Clock (with the button to anchor to). */
        void onDoor(@NonNull Door door, @NonNull View anchor);
        /** The terminal's Trail menu: the cursor-trail style id the user chose. */
        void onTrailStyle(@NonNull String id);
        /** The terminal's Effect menu: the retro effect id the user chose. */
        void onRetroEffect(@NonNull String id);
    }

    /** What one of the Custom row's sliders shows: its control, value and legend. */
    static final class SliderState {
        @NonNull final Control control;
        final int value;
        final boolean enabled;
        @NonNull final com.google.android.material.slider.LabelFormatter legend;

        SliderState(@NonNull Control control, int value, boolean enabled,
                    @NonNull com.google.android.material.slider.LabelFormatter legend) {
            this.control = control;
            this.value = value;
            this.enabled = enabled;
            this.legend = legend;
        }
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

    private final Group mAppearanceGroup;
    private final Group mLayoutGroup;
    /** Icons mode's one row: the content the activity lends (setIconsContent). */
    private final FrameLayout mIconsSlot;

    private final Slider mLook;
    private final FrameLayout mLookLabels;
    private final TextView[] mLookLabelViews = new TextView[AppearanceLooks.STOP_COUNT];
    /** Each label's own typeface as its text appearance set it (family and weight kept). */
    private final Typeface[] mLookLabelBase = new Typeface[AppearanceLooks.STOP_COUNT];

    private final View mRow2;
    private final TextView mRow2Name;
    private final LinearLayout mSliderRow;
    private final LegendSlider[] mSliders = new LegendSlider[AppearanceLooks.MAX_CONTROLS];
    /** The control each slider column stands for now; null for a hidden column. */
    private final Control[] mSliderControls = new Control[AppearanceLooks.MAX_CONTROLS];
    private final boolean[] mSliderDragging = new boolean[AppearanceLooks.MAX_CONTROLS];
    /** Whether each column may be moved (Contrast is not while the palette is a scheme file). */
    private final boolean[] mSliderEnabled = new boolean[AppearanceLooks.MAX_CONTROLS];
    private final MaterialButton mKeyboardTheme;
    private final MaterialButton mClock;
    private final MaterialButton mTrail;
    private final MaterialButton mEffect;

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
    private final Slider mKeyRadius;

    /** Whether Layout mode's rows are showing in place of Appearance's. */
    private boolean mLayoutMode;
    /** The mode whose rows show. */
    @NonNull private EditorMode mMode = EditorMode.LOOK;
    /** Whether Row B is up (an element is tapped at the Custom stop); kept across Layout mode. */
    private boolean mRow2Shown;
    @NonNull private String mTrailId = "default";
    @NonNull private String mEffectId = "none";
    /** Whether the hidden tiles stand in Layout's Row B, in Corner radius and Margin's place. */
    private boolean mHiddenTilesOpen;
    /** Whether the keyboard's tools stand there (the keyboard is selected); the tiles win. */
    private boolean mKeyboardToolsShown;

    @Nullable private Listener mListener;
    private boolean mRestating;
    private boolean mCornersDragging;
    private boolean mMarginDragging;

    private AppearanceEditorPanel(@NonNull Context context, @NonNull View root) {
        mContext = context;
        mRoot = root;
        mBasePaddingBottom = root.getPaddingBottom();
        mBasePaddingStart = root.getPaddingStart();
        mBasePaddingEnd = root.getPaddingEnd();
        mAppearanceGroup = root.findViewById(R.id.appearance_editor_appearance_group);
        mLayoutGroup = root.findViewById(R.id.appearance_editor_layout_group);
        mIconsSlot = root.findViewById(R.id.appearance_editor_icons_slot);
        mLook = root.findViewById(R.id.appearance_editor_look);
        mLookLabels = root.findViewById(R.id.appearance_editor_look_labels);
        mRow2 = root.findViewById(R.id.appearance_editor_row2);
        mRow2Name = root.findViewById(R.id.appearance_editor_row2_name);
        mSliderRow = root.findViewById(R.id.appearance_editor_sliders);
        int[] sliderIds = {R.id.appearance_editor_slider_0, R.id.appearance_editor_slider_1,
            R.id.appearance_editor_slider_2, R.id.appearance_editor_slider_3,
            R.id.appearance_editor_slider_4};
        for (int i = 0; i < mSliders.length; i++)
            mSliders[i] = root.findViewById(sliderIds[i]);
        mKeyboardTheme = root.findViewById(R.id.appearance_editor_door_keyboard_theme);
        mClock = root.findViewById(R.id.appearance_editor_door_clock);
        mTrail = root.findViewById(R.id.appearance_editor_trail);
        mEffect = root.findViewById(R.id.appearance_editor_effect);
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
        applyGroups(EditorMode.LOOK);
        applyRow2(false);
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
    int measureFor(@NonNull EditorMode mode, int widthPx) {
        EditorMode shown = mMode;
        applyGroups(mode);
        int height = measureNow(widthPx);
        applyGroups(shown);
        return height;
    }

    /**
     * The tallest the sheet gets in {@code layout} mode: Appearance with Row B up, whether or not
     * the Custom stop is showing. Row B is one height whatever is selected (a 48dp heading row
     * over sliders of one fixed length), so this is the height at the Custom stop, and the frame
     * stands above it: it never moves when Row B comes and goes. Layout mode's rows are the same
     * at every moment.
     */
    int measureTallest(@NonNull EditorMode mode, int widthPx) {
        if (mode == EditorMode.LAYOUT)
            return measureLayoutTallest(widthPx);
        if (mode == EditorMode.ICONS) {
            EditorMode shownMode = mMode;
            applyGroups(EditorMode.ICONS);
            int height = measureNow(widthPx);
            applyGroups(shownMode);
            return height;
        }
        EditorMode shown = mMode;
        boolean rowShown = mRow2Shown;
        mRow2Shown = true;
        applyGroups(EditorMode.LOOK);
        int height = measureNow(widthPx);
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
        EditorMode shown = mMode;
        boolean tools = mKeyboardToolsShown;
        boolean tiles = mHiddenTilesOpen;
        mHiddenTilesOpen = false;
        mKeyboardToolsShown = false;
        applyGroups(EditorMode.LAYOUT);
        int height = measureNow(widthPx);
        mKeyboardToolsShown = true;
        applyLayoutRow2();
        height = Math.max(height, measureNow(widthPx));
        mKeyboardToolsShown = tools;
        mHiddenTilesOpen = tiles;
        applyGroups(shown);
        return height;
    }

    private int measureNow(int widthPx) {
        int width = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        // Relative compound icons contribute width after inherited direction is resolved.
        mRoot.measure(width, height);
        reserveReadoutLines();
        mRoot.measure(width, height);
        return mRoot.getMeasuredHeight();
    }

    /**
     * Each Layout label's lines, from the column width the first measure gave it: as many as the
     * widest thing it may say needs (see mReadoutRange), so a drag never adds a line.
     */
    private void reserveReadoutLines() {
        TextView[] labels = {mCornersLabel, mMarginLabel};
        for (TextView label : labels) {
            int lines = 1;
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
        // The value is one word ("24\u00a0dp"): a label that wraps breaks after the dot, never
        // between a number and its unit.
        text = text.substring(0, cut + 3) + text.substring(cut + 3).replace(' ', '\u00a0');
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
        for (int i = 0; i < mSliders.length; i++) {
            final int column = i;
            mSliders[i].addOnChangeListener((slider, value, fromUser) -> {
                Control control = mSliderControls[column];
                if (mRestating || !fromUser || mListener == null || control == null)
                    return;
                mListener.onSlider(control, Math.round(value), mSliderDragging[column]);
            });
            mSliders[i].addOnSliderTouchListener(new LegendSlider.OnSliderTouchListener() {
                @Override public void onStartTrackingTouch(@NonNull LegendSlider slider) {
                    mSliderDragging[column] = true;
                }

                @Override public void onStopTrackingTouch(@NonNull LegendSlider slider) {
                    mSliderDragging[column] = false;
                    if (mListener != null) mListener.onSliderReleased();
                }
            });
        }
        mKeyboardTheme.setOnClickListener(view -> {
            if (mListener != null) mListener.onDoor(Door.KEYBOARD_THEME, view);
        });
        mClock.setOnClickListener(view -> {
            if (mListener != null) mListener.onDoor(Door.CLOCK, view);
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
     * The terminal's Trail and Effect at the stored ids. An id this build does not know reads as
     * the first option, as the preference's own getter does.
     */
    void setTerminalLooks(@NonNull String trailId, @NonNull String effectId) {
        mTrailId = trailId;
        mEffectId = effectId;
        restateTrail();
        restateEffect();
    }

    private void restateTrail() {
        String label = optionLabel(R.array.settings_terminal_cursor_trail_style_entries,
            R.array.settings_terminal_cursor_trail_style_values, mTrailId);
        mTrail.setText(mContext.getString(R.string.appearance_editor_trail_button, label));
        mTrail.setContentDescription(
            mContext.getString(R.string.appearance_editor_trail_description, label));
    }

    private void restateEffect() {
        String label = optionLabel(R.array.settings_terminal_retro_effect_entries,
            R.array.settings_terminal_retro_effect_values, mEffectId);
        mEffect.setText(mContext.getString(R.string.appearance_editor_effect_button, label));
        mEffect.setContentDescription(
            mContext.getString(R.string.appearance_editor_effect_description, label));
    }

    // ------------------------------------------------------------------------------ the modes

    void showAppearanceMode() {
        setMode(EditorMode.LOOK);
    }

    void showLayoutMode() {
        setMode(EditorMode.LAYOUT);
    }

    void showIconsMode() {
        setMode(EditorMode.ICONS);
    }

    /**
     * Shows one mode's rows (the pill is the page bar's, not the sheet's). Row B keeps its state
     * across a visit to Layout mode: it is Appearance's, so it goes and comes back with that group.
     */
    void setMode(@NonNull EditorMode mode) {
        applyGroups(mode);
    }

    private void applyGroups(@NonNull EditorMode mode) {
        mMode = mode;
        boolean layout = mode == EditorMode.LAYOUT;
        boolean look = mode == EditorMode.LOOK;
        mLayoutMode = layout;
        mAppearanceGroup.setVisibility(look ? View.VISIBLE : View.GONE);
        mLayoutGroup.setVisibility(layout ? View.VISIBLE : View.GONE);
        mRow2.setVisibility(look && mRow2Shown ? View.VISIBLE : View.GONE);
        mIconsSlot.setVisibility(mode == EditorMode.ICONS && mIconsSlot.getChildCount() > 0
            ? View.VISIBLE : View.GONE);
        applyLayoutRow2();
    }

    /**
     * The Icons mode's content (the pack tiles and the Pinned-only switch), built by the activity:
     * the sheet shows exactly it in that mode and is as tall as it is.
     */
    void setIconsContent(@Nullable View content) {
        mIconsSlot.removeAllViews();
        if (content != null) {
            if (content.getParent() instanceof ViewGroup)
                ((ViewGroup) content.getParent()).removeView(content);
            mIconsSlot.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        applyGroups(mMode);
    }

    @NonNull
    EditorMode mode() {
        return mMode;
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
        applyRow2(false);
    }

    /** Row B up, with the selection's name; the sliders and buttons are set by the calls below. */
    void showRow2(@StringRes int name) {
        mRow2Name.setText(name);
        if (mRow2Shown)
            return;
        mRow2Shown = true;
        applyRow2(true);
    }

    /**
     * Row B's visibility, and whether its controls take touches and are read out. Row B is not
     * shown in Layout mode, whatever its state.
     */
    private void applyRow2(boolean shown) {
        mRow2.setImportantForAccessibility(shown ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for (int i = 0; i < mSliders.length; i++)
            mSliders[i].setEnabled(shown && mSliderEnabled[i]);
        for (MaterialButton button : new MaterialButton[] {mKeyboardTheme, mClock, mTrail, mEffect})
            button.setEnabled(shown);
        mRow2.setVisibility(shown && mMode == EditorMode.LOOK ? View.VISIBLE : View.GONE);
    }

    /**
     * The Custom row's sliders: one column per state, in order, the rest GONE. Each column gets
     * its control's range, its value and its legend; the 12dp gaps are between the columns that
     * show, so a hidden column leaves none.
     */
    void setSliders(@NonNull List<SliderState> states) {
        int gap = Math.round(dp(12));
        boolean first = true;
        for (int i = 0; i < mSliders.length; i++) {
            LegendSlider slider = mSliders[i];
            if (i >= states.size()) {
                mSliderControls[i] = null;
                mSliderEnabled[i] = false;
                slider.setLegend(null);
                slider.setVisibility(View.GONE);
                continue;
            }
            SliderState state = states.get(i);
            mSliderControls[i] = state.control;
            slider.setLegend(state.legend);
            slider.setContentDescription(AppearanceLooks.legendName(
                state.legend.getFormattedValue(state.value)));
            restateSlider(slider, state.control, state.value);
            // Remembered across applyRow2, which re-enables the shown row.
            mSliderEnabled[i] = state.enabled;
            slider.setEnabled(mRow2Shown && state.enabled);
            ViewGroup.LayoutParams raw = slider.getLayoutParams();
            if (raw instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
                int start = first ? 0 : gap;
                if (params.getMarginStart() != start) {
                    params.setMarginStart(start);
                    slider.setLayoutParams(params);
                }
            }
            slider.setVisibility(View.VISIBLE);
            first = false;
        }
    }

    /** The control each visible column holds now, in order, for a test to read. */
    @NonNull
    List<Control> shownControls() {
        java.util.ArrayList<Control> out = new java.util.ArrayList<>();
        for (int i = 0; i < mSliders.length; i++) {
            if (mSliders[i].getVisibility() == View.VISIBLE && mSliderControls[i] != null)
                out.add(mSliderControls[i]);
        }
        return out;
    }

    /** The slider column showing {@code control}, or null. */
    @Nullable
    LegendSlider sliderFor(@NonNull Control control) {
        for (int i = 0; i < mSliders.length; i++) {
            if (mSliderControls[i] == control && mSliders[i].getVisibility() == View.VISIBLE)
                return mSliders[i];
        }
        return null;
    }

    /** Restates one control's value after a write that moved it elsewhere (not by the user). */
    void setSliderValue(@NonNull Control control, int value) {
        LegendSlider slider = sliderFor(control);
        if (slider != null)
            restateSlider(slider, control, value);
    }

    /** Shows exactly these buttons in the heading row, in the layout's order. */
    void setDoors(@NonNull List<Door> doors) {
        mKeyboardTheme.setVisibility(doors.contains(Door.KEYBOARD_THEME) ? View.VISIBLE : View.GONE);
        mClock.setVisibility(doors.contains(Door.CLOCK) ? View.VISIBLE : View.GONE);
        mTrail.setVisibility(doors.contains(Door.TRAIL) ? View.VISIBLE : View.GONE);
        View space = mRoot.findViewById(R.id.appearance_editor_row2_space);
        if (space != null)
            space.setVisibility(doors.contains(Door.TRAIL) || doors.contains(Door.EFFECT)
                ? View.GONE : View.VISIBLE);
        mEffect.setVisibility(doors.contains(Door.EFFECT) ? View.VISIBLE : View.GONE);
    }

    /** The Clock button, which the clock popup opens upward from. */
    @NonNull MaterialButton clockButton() {
        return mClock;
    }

    /** The heading row's buttons that show now, for a test to read. */
    @NonNull
    List<Door> shownDoors() {
        java.util.ArrayList<Door> out = new java.util.ArrayList<>();
        if (mKeyboardTheme.getVisibility() == View.VISIBLE) out.add(Door.KEYBOARD_THEME);
        if (mClock.getVisibility() == View.VISIBLE) out.add(Door.CLOCK);
        if (mTrail.getVisibility() == View.VISIBLE) out.add(Door.TRAIL);
        if (mEffect.getVisibility() == View.VISIBLE) out.add(Door.EFFECT);
        return out;
    }

    /**
     * The terminal's Contrast legend for a stop: Contrast · Low, Normal or High (see
     * {@link #legibilityLabel}).
     */
    @NonNull
    String legibilityLabel(int index) {
        return mContext.getString(index <= 0 ? R.string.appearance_editor_legibility_low
            : index == 1 ? R.string.appearance_editor_legibility_normal
            : R.string.appearance_editor_legibility_high);
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

    /** A Custom row slider at a control's range, step and value. */
    private void restateSlider(@NonNull LegendSlider slider, @NonNull Control control, int value) {
        mRestating = true;
        // The range and step first, the value last: a Slider checks them together when it lays
        // out, never between these calls.
        slider.setValueFrom(control.min);
        slider.setValueTo(control.max);
        slider.setStepSize(control.step);
        slider.setValue(control.clamp(value));
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
