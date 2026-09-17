package com.termux.app.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.GlassBackdropCache;
import com.termux.app.chrome.OnGlass;
import com.termux.app.terminal.TerminalWindowBar.WindowItem;
import com.termux.app.terminal.WindowChipInk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The window list as a stack of chips, for the status bar standing in a column.
 *
 * <p>The row's pills carry a whole label; a column that is a finger wide cannot, so a chip here is
 * the window's number with the label's first letter under it, and the marks the row shows after a
 * label — working, asking, finished — become the chip's own rim. The selected window's chip is
 * filled in the place's accent, exactly as its pill is on the row.
 */
public final class StatusBarWindowColumn extends ScrollView {

    public interface OnWindowSelectedListener {
        void onWindowSelected(int index);
    }

    private static final float CHIP_SIZE_DP = 26f;
    private static final float CHIP_GAP_DP = 4f;

    private final LinearLayout mStack;
    @NonNull private List<WindowItem> mItems = new ArrayList<>();
    private int mSelected = -1;
    private int mAccent;
    private float mChipRadiusPx = -1f;
    @Nullable private OnWindowSelectedListener mListener;
    /**
     * Who says what this column is standing on, or null when nothing can measure it. Null keeps
     * the authored alphas exactly as they were: every measured colour here is additive.
     */
    @Nullable private com.termux.app.chrome.ChromeInk mChromeInk;
    /** The band as last measured, or null while nothing has been. */
    @Nullable private WindowChipInk.Palette mGlassPalette;
    private int mGlassGeneration = -1;
    @NonNull private final android.graphics.Rect mGlassBandRect = new android.graphics.Rect();
    @NonNull private final android.graphics.Rect mGlassMeasuredRect = new android.graphics.Rect();

    public StatusBarWindowColumn(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        // The bar's own gesture owns a drag down the column: that is how the wall is paged there,
        // and a scroll container in the way would swallow it before the bar ever saw it.
        setNestedScrollingEnabled(false);
        mStack = new LinearLayout(context);
        mStack.setOrientation(LinearLayout.VERTICAL);
        mStack.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(mStack, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        mAccent = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
    }

    public void setListener(@Nullable OnWindowSelectedListener listener) { mListener = listener; }

    /** The place's colour, so the column reads as part of the place it belongs to. */
    public void setPlaceAccent(int accent) {
        if (mAccent == accent) return;
        mAccent = accent;
        rebuild();
    }

    /**
     * Who can say what this column is standing on.
     *
     * <p>The column's chips were dressed in five hand-picked alphas — a mark at 210, a fill at 74,
     * strokes at 190, 170 and 70 — all of them tuned against a dark bar. Over the light-mode glass
     * the user reported they composite to within a percent of the band. Given this, the marks and
     * the rims are derived from the band as measured instead; given null, nothing is measured and
     * the authored alphas stand.</p>
     */
    public void setChromeInk(@Nullable com.termux.app.chrome.ChromeInk ink) {
        if (mChromeInk == ink) return;
        mChromeInk = ink;
        mGlassPalette = null;
        mGlassGeneration = -1;
        mGlassMeasuredRect.setEmpty();
        rebuild();
    }

    /** The band as last measured; null until something could measure it. For tests. */
    @Nullable
    public WindowChipInk.Palette glassPalette() {
        return mGlassPalette;
    }

    /**
     * The band under the chips, measured once and remembered until the measurement could move.
     *
     * <p>The column stands on the top pane, the same glass the status strip's content stands on,
     * so it reads {@link GlassBackdropCache.Band#STATUS_BAR}'s settled answer rather than resolving
     * it. A band
     * has one veil and therefore one question, and the strip's owner is the activity's own status
     * ink pass, which asks in the stats' hues. This used to ask as well, in the chips' neutrals, so
     * one strip of glass carried two resolutions and wore whichever of them drew last. Reading is
     * the fix: the chips get the band exactly as it is drawn, and their own ink from
     * {@link com.termux.app.chrome.ChromeInk#inkOn} in the polarity the whole chrome settled.</p>
     */
    private void measureBand() {
        if (mChromeInk == null) {
            mGlassPalette = null;
            return;
        }
        OnGlass.Resolution resolved = mChromeInk.resolution(GlassBackdropCache.Band.STATUS_BAR);
        if (resolved == null
            || !mChromeInk.bandRect(GlassBackdropCache.Band.STATUS_BAR, mGlassBandRect)) {
            // Nothing has measured the strip yet. The owner's pass runs every apply, so the chips
            // dress themselves on the next one rather than opening a second question here.
            mGlassPalette = null;
            mGlassGeneration = -1;
            mGlassMeasuredRect.setEmpty();
            return;
        }
        int onSurface = MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(getContext(), R.color.termux_on_surface));
        int surfaceBase = MaterialColors.getColor(this,
            com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(getContext(), R.color.termux_surface_base));
        boolean pale =
            mChromeInk.polarity() == com.termux.app.chrome.ChromeInk.Polarity.PALE_INK;
        int neutral = WindowChipInk.neutralSeed(onSurface, surfaceBase, pale);
        // Every window-list change rebuilds the stack; an unchanged answer is not re-derived.
        if (mGlassPalette == null
            || !mGlassPalette.matches(resolved.surface, pale, neutral, mAccent)) {
            mGlassPalette = WindowChipInk.resolve(resolved.surface, pale, neutral, mAccent);
        }
        mGlassGeneration = mChromeInk.backdrops().generation();
        mGlassMeasuredRect.set(mGlassBandRect);
    }

    /** Whether the chrome is drawing in its pale ink; false whenever nothing has been measured. */
    private boolean pale() {
        return mChromeInk != null
            && mChromeInk.polarity() == com.termux.app.chrome.ChromeInk.Polarity.PALE_INK;
    }

    @Override
    protected void dispatchDraw(@NonNull android.graphics.Canvas canvas) {
        syncGlassPalette();
        super.dispatchDraw(canvas);
    }

    /**
     * Re-measures when the measurement could have moved — a new sample generation, or a band that
     * moved on screen — and re-dresses the chips if it did. Two int comparisons per frame.
     */
    private void syncGlassPalette() {
        if (mChromeInk == null) return;
        if (!mChromeInk.bandRect(GlassBackdropCache.Band.STATUS_BAR, mGlassBandRect)) {
            if (mGlassPalette != null) redress();
            return;
        }
        if (mChromeInk.backdrops().generation() == mGlassGeneration
            && mGlassMeasuredRect.equals(mGlassBandRect)) {
            return;
        }
        redress();
    }

    /**
     * The chips' colours after a re-measure, and nothing else. Never {@link #rebuild()}: this is
     * reached from the draw pass, where adding and removing views is not allowed.
     */
    private void redress() {
        measureBand();
        for (int i = 0; i < mItems.size() && i < mStack.getChildCount(); i++) {
            View child = mStack.getChildAt(i);
            if (child instanceof TextView) dress((TextView) child, mItems.get(i), i == mSelected);
        }
    }

    /** One chip's mark and rim: the two things that carry what the window is doing. */
    private void dress(@NonNull TextView chip, @NonNull WindowItem item, boolean selected) {
        WindowChipInk.Palette glass = mGlassPalette;
        if (glass == null) {
            chip.setTextColor(selected
                ? MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorOnAccentContainer,
                    ContextCompat.getColor(getContext(), R.color.termux_on_surface))
                : ColorUtils.setAlphaComponent(markColor(item), 210));
        } else {
            // The chip's one glyph is 11sp text, so it is held to body text — on the surface the
            // chip's own wash leaves, not on the bare band.
            chip.setTextColor(selected
                ? glass.selectedLabel
                : WindowChipInk.towardPolarity(glass.restingSurface, markColor(item), pale(),
                    OnGlass.TARGET_BODY_TEXT));
        }
        chip.setBackground(chipBackground(item, selected));
    }

    /** The corner the bar's chips wear, shared with the badge and the lens icons. */
    public void setChipRadiusPx(float radiusPx) {
        if (mChipRadiusPx == radiusPx) return;
        mChipRadiusPx = radiusPx;
        rebuild();
    }

    /** The same list the row's pills are built from, and which one is on screen. */
    public void setWindows(@NonNull List<WindowItem> items, int selected) {
        mItems = new ArrayList<>(items);
        mSelected = selected;
        rebuild();
    }

    private void rebuild() {
        measureBand();
        mStack.removeAllViews();
        int size = Math.round(CHIP_SIZE_DP * density());
        int gap = Math.round(CHIP_GAP_DP * density());
        for (int i = 0; i < mItems.size(); i++) {
            final int index = i;
            WindowItem item = mItems.get(i);
            boolean selected = i == mSelected;
            TextView chip = new TextView(getContext());
            chip.setText(chipText(item, i));
            chip.setGravity(Gravity.CENTER);
            chip.setMaxLines(1);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            String agentWord = com.termux.app.terminal.TerminalWindowBar.agentStateWord(
                getContext(), item.agentState);
            chip.setContentDescription(agentWord == null ? item.spokenLabel
                : item.spokenLabel + " · " + agentWord + ".");
            dress(chip, item, selected);
            chip.setOnClickListener(v -> {
                if (mListener != null) mListener.onWindowSelected(index);
            });
            // A raised font scale is what clips fixed chrome; the chip's one glyph shrinks to fit
            // its box rather than spilling out of it.
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(chip,
                8, 11, 1, TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.topMargin = i == 0 ? 0 : gap;
            mStack.addView(chip, params);
        }
    }

    /**
     * The window's number, which is what the user counts them by. A label that starts with a
     * letter of its own says more than the number alone, so it takes the chip instead.
     */
    @NonNull
    private String chipText(@NonNull WindowItem item, int index) {
        String label = item.label.trim();
        if (!label.isEmpty()) {
            char first = label.charAt(0);
            if (Character.isLetter(first)) {
                return String.valueOf(first).toUpperCase(Locale.getDefault());
            }
        }
        return String.valueOf(index + 1);
    }

    /**
     * Working, asking and finished are the rim's colour here, as they are the pill's on a row. A
     * chip this small has no room for the row's agent dot, so an agent waiting for the user reads
     * the same way a rung bell does, and one working the same way a running command does.
     */
    private int markColor(@NonNull WindowItem item) {
        if (item.attention || item.agentState == com.termux.app.terminal.AgentStatus.State.BLOCKED) {
            return MaterialColors.getColor(this, com.google.android.material.R.attr.colorError,
                ContextCompat.getColor(getContext(), R.color.termux_error));
        }
        if (item.busy || item.done
            || item.agentState == com.termux.app.terminal.AgentStatus.State.WORKING) return mAccent;
        return MaterialColors.getColor(this,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant));
    }

    @NonNull
    private GradientDrawable chipBackground(@NonNull WindowItem item, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        float radius = mChipRadiusPx >= 0f ? mChipRadiusPx : 8f * density();
        shape.setCornerRadius(Math.min(radius, CHIP_SIZE_DP * density() / 2f));
        int mark = markColor(item);
        WindowChipInk.Palette glass = mGlassPalette;
        if (glass == null) {
            if (selected) {
                shape.setColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(mAccent, 74)));
                shape.setStroke(Math.round(density()), ColorUtils.setAlphaComponent(mAccent, 190));
            } else {
                shape.setColor(ColorStateList.valueOf(Color.TRANSPARENT));
                shape.setStroke(Math.round(density()),
                    ColorUtils.setAlphaComponent(mark, item.busy || item.attention
                        || item.agentState == com.termux.app.terminal.AgentStatus.State.BLOCKED
                        || item.agentState == com.termux.app.terminal.AgentStatus.State.WORKING
                        ? 170 : 70));
            }
            return shape;
        }
        // The rim is the whole chip here — there is no room for the row's corner dots, so working,
        // asking and finished are the rim's colour. That makes it the only carrier of its own
        // fact, which is the graphics floor; the two alphas that used to separate an active rim
        // from an idle one are gone, because the mark's own hue already says which is which and
        // neither of them may be the one that fails to read.
        shape.setColor(ColorStateList.valueOf(selected ? glass.selectedFill : glass.restingFill));
        shape.setStroke(Math.round(density()), selected
            ? glass.selectedStroke
            : WindowChipInk.towardPolarity(glass.band, mark, pale(), OnGlass.TARGET_LARGE_TEXT));
        return shape;
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }
}
