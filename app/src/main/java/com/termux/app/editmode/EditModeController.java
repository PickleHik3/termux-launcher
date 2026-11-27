package com.termux.app.editmode;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import java.util.ArrayList;
import java.util.List;

/**
 * Edit mode on the live desktop (ADR 0040): a faint outline on every surface that has a card, a
 * tap raising that surface's card low on the screen, and the strip at the very bottom with the
 * change count, Undo, Revert and Done. Every change applies to the look store at once and stays;
 * the {@link EditSession} keeps the visit's history. Back closes the card, then leaves; nothing
 * asks.
 *
 * <p>Its views are made on {@link #open} and let go on {@link #leave}: nothing is held at rest.
 * They join the editor layer through the host, and the overlay itself takes no touch, so the desktop
 * under it still moves. Only the keyboard has a card so far.
 */
public final class EditModeController {

    /** The cards edit mode can raise. */
    public enum Card { KEYBOARD }

    /** What edit mode needs from the screen it edits. */
    public interface Host {
        @NonNull
        Context context();

        /** The editor layer's host ({@code WindowLayers}), or null when the window has none. */
        @Nullable
        ViewGroup editorLayer();

        @NonNull
        TerlLookStore looks();

        /** The in-app keyboard's view, or null when there is none. */
        @Nullable
        View keyboardView();

        boolean isKeyboardShown();

        /** Brings the in-app keyboard up for its card; false when it cannot come up. */
        boolean showKeyboard();

        /** Puts the keyboard a card brought up back down. */
        void hideKeyboard();
    }

    private static final int OUTLINE_STROKE_DP = 2;
    private static final int OUTLINE_RADIUS_DP = 12;
    private static final int CARD_RADIUS_DP = 24;
    private static final int GAP_DP = 12;
    private static final int TOUCH_DP = 40;
    private static final int MARKER_DP = 12;
    private static final float DISABLED_ALPHA = 0.38f;

    /** One slider row of a card. */
    private static final class RowViews {
        final TerlSurface surface;
        final TerlProperty property;
        final SeekBar slider;
        final TextView value;
        final View marker;
        final View markerTarget;
        boolean dragging;
        /** What the marker last showed: null before the first refresh. */
        @Nullable Boolean shownOwn;

        RowViews(TerlSurface surface, TerlProperty property, SeekBar slider, TextView value,
                 View marker, View markerTarget) {
            this.surface = surface;
            this.property = property;
            this.slider = slider;
            this.value = value;
            this.marker = marker;
            this.markerTarget = markerTarget;
        }
    }

    @NonNull private final Host mHost;

    @Nullable private EditSession mSession;
    @Nullable private FrameLayout mOverlay;
    @Nullable private View mOutline;
    @Nullable private LinearLayout mCard;
    @Nullable private TextView mCount;
    @Nullable private TextView mKeyboardChip;
    @Nullable private TextView mUndo;
    @Nullable private TextView mRevert;
    @Nullable private View mStrip;
    @NonNull private final List<RowViews> mRows = new ArrayList<>();
    @Nullable private Card mOpenCard;
    /** The keyboard was down and its card brought it up: closing the card puts it back. */
    private boolean mRaisedKeyboard;

    private final TerlLookStore.Listener mLookListener = this::refresh;
    /**
     * Keeps the outline on the keyboard and the card above it after any layout of the window (the
     * keyboard coming up or going down). Only compares positions; it moves a view only when the
     * keyboard moved.
     */
    private final ViewTreeObserver.OnGlobalLayoutListener mPlaceListener = this::place;

    public EditModeController(@NonNull Host host) {
        mHost = host;
    }

    public boolean isOpen() {
        return mOverlay != null;
    }

    /** The card open now, or null at rest. */
    @Nullable
    public Card openCard() {
        return mOpenCard;
    }

    /** Opens edit mode, on {@code card} when one is named. Already open, it only raises the card. */
    public void open(@Nullable Card card) {
        if (!isOpen() && !build()) return;
        if (card != null) openCard(card);
        refresh();
        place();
    }

    /** Raises {@code card}; a surface that is not on screen comes up while its card is open. */
    public void openCard(@NonNull Card card) {
        if (!isOpen() || mCard == null) return;
        if (mOpenCard == card) return;
        mOpenCard = card;
        if (!mHost.isKeyboardShown()) mRaisedKeyboard = mHost.showKeyboard();
        setVisible(mCard, true);
        refresh();
        place();
    }

    /** Closes the open card; false when none is open. */
    public boolean closeCard() {
        if (mOpenCard == null) return false;
        mOpenCard = null;
        if (mCard != null) setVisible(mCard, false);
        for (RowViews row : mRows) endDrag(row);
        if (mRaisedKeyboard) {
            mRaisedKeyboard = false;
            mHost.hideKeyboard();
        }
        refresh();
        place();
        return true;
    }

    /** Back: closes the card first, then leaves. False when edit mode is not open. */
    public boolean onBack() {
        if (!isOpen()) return false;
        if (closeCard()) return true;
        leave();
        return true;
    }

    /** Leaves edit mode. The changes stay; the history and the views go. */
    public void leave() {
        if (!isOpen()) return;
        closeCard();
        if (mSession != null) mSession.end();
        mSession = null;
        mHost.looks().removeListener(mLookListener);
        FrameLayout overlay = mOverlay;
        mOverlay = null;
        if (overlay != null) {
            ViewTreeObserver observer = overlay.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(mPlaceListener);
            if (overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
        }
        mOutline = null;
        mCard = null;
        mCount = null;
        mKeyboardChip = null;
        mUndo = null;
        mRevert = null;
        mStrip = null;
        mRows.clear();
    }

    // ---------------------------------------------------------------- building

    private boolean build() {
        ViewGroup layer = mHost.editorLayer();
        if (layer == null) return false;
        Context context = mHost.context();
        mSession = EditSession.begin(mHost.looks());

        FrameLayout overlay = new FrameLayout(context);
        // Takes no touch of its own: the desktop under it still moves while editing.
        overlay.setClickable(false);
        overlay.setFocusable(false);
        ViewCompat.setOnApplyWindowInsetsListener(overlay, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (view.getPaddingBottom() != bars.bottom || view.getPaddingLeft() != bars.left
                || view.getPaddingRight() != bars.right) {
                view.setPadding(bars.left, 0, bars.right, bars.bottom);
            }
            return insets;
        });

        mOutline = buildOutline(context);
        overlay.addView(mOutline, new FrameLayout.LayoutParams(0, 0));
        mCard = buildKeyboardCard(context);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        cardParams.leftMargin = dp(GAP_DP);
        cardParams.rightMargin = dp(GAP_DP);
        overlay.addView(mCard, cardParams);
        setVisible(mCard, false);
        mStrip = buildStrip(context);
        FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        stripParams.leftMargin = dp(GAP_DP);
        stripParams.rightMargin = dp(GAP_DP);
        stripParams.bottomMargin = dp(GAP_DP / 2);
        overlay.addView(mStrip, stripParams);

        layer.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mOverlay = overlay;
        overlay.getViewTreeObserver().addOnGlobalLayoutListener(mPlaceListener);
        ViewCompat.requestApplyInsets(overlay);
        mHost.looks().addListener(mLookListener);
        return true;
    }

    @NonNull
    private View buildOutline(@NonNull Context context) {
        View outline = new View(context);
        GradientDrawable stroke = new GradientDrawable();
        stroke.setCornerRadius(dp(OUTLINE_RADIUS_DP));
        int ink = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface,
            0xFFFFFFFF);
        stroke.setStroke(dp(OUTLINE_STROKE_DP), withAlpha(ink, 0x66));
        outline.setBackground(stroke);
        outline.setContentDescription(context.getString(R.string.edit_mode_card_keyboard));
        outline.setOnClickListener(view -> openCard(Card.KEYBOARD));
        outline.setVisibility(View.GONE);
        return outline;
    }

    @NonNull
    private LinearLayout buildKeyboardCard(@NonNull Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad / 2);
        card.setBackground(panel(context, dp(CARD_RADIUS_DP)));
        // A tap on the card's empty space stays on the card.
        card.setClickable(true);

        TextView title = text(context, R.string.edit_mode_card_keyboard, 18,
            com.google.android.material.R.attr.colorOnSurface);
        card.addView(title);
        mRows.add(addSliderRow(context, card, TerlSurface.KEYBOARD, TerlProperty.OPACITY,
            R.string.edit_mode_opacity));
        mRows.add(addSliderRow(context, card, TerlSurface.KEYBOARD, TerlProperty.BLUR,
            R.string.edit_mode_blur));
        return card;
    }

    @NonNull
    private RowViews addSliderRow(@NonNull Context context, @NonNull LinearLayout card,
                                  @NonNull TerlSurface surface, @NonNull TerlProperty property,
                                  int labelRes) {
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(8), 0, 0);

        TextView label = text(context, labelRes, 15, com.google.android.material.R.attr.colorOnSurface);
        head.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = new TextView(context);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFB0B0B0));
        value.setSingleLine(true);
        head.addView(value);

        FrameLayout target = new FrameLayout(context);
        View marker = new View(context);
        target.addView(marker, new FrameLayout.LayoutParams(dp(MARKER_DP), dp(MARKER_DP),
            Gravity.CENTER));
        target.setBackgroundResource(selectableBorderless(context));
        head.addView(target, new LinearLayout.LayoutParams(dp(TOUCH_DP), dp(TOUCH_DP)));
        card.addView(head);

        SeekBar slider = new SeekBar(context);
        slider.setMax(property.max - property.min);
        int accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary,
            0xFF8AB4F8);
        slider.setProgressTintList(ColorStateList.valueOf(accent));
        slider.setThumbTintList(ColorStateList.valueOf(accent));
        slider.setContentDescription(context.getString(labelRes));
        card.addView(slider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            dp(TOUCH_DP)));

        RowViews row = new RowViews(surface, property, slider, value, marker, target);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                if (mSession == null) return;
                row.dragging = true;
                mSession.dragStarted(surface, property);
            }

            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || mSession == null) return;
                // A drag moves its step along; a key press on the slider is a step of its own.
                mSession.set(surface, property, property.min + progress);
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                endDrag(row);
                refresh();
            }
        });
        target.setOnClickListener(view -> {
            if (mSession == null || !mHost.looks().holdsOwn(surface, property)) return;
            mSession.followGlobal(surface, property);
            refresh();
        });
        return row;
    }

    @NonNull
    private View buildStrip(@NonNull Context context) {
        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(16), dp(4), dp(4), dp(4));
        strip.setBackground(panel(context, dp(TOUCH_DP)));
        strip.setClickable(true);

        mCount = new TextView(context);
        mCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mCount.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
        mCount.setMaxLines(2);
        mCount.setEllipsize(android.text.TextUtils.TruncateAt.END);
        strip.addView(mCount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // With the keyboard down there is no outline to tap: the strip offers its card.
        mKeyboardChip = action(context, R.string.edit_mode_card_keyboard, view -> openCard(Card.KEYBOARD));
        strip.addView(mKeyboardChip);
        mUndo = action(context, R.string.edit_mode_undo, view -> {
            if (mSession == null) return;
            mSession.undo();
            refresh();
        });
        strip.addView(mUndo);
        mRevert = action(context, R.string.edit_mode_revert, view -> {
            if (mSession == null) return;
            mSession.revert();
            refresh();
        });
        strip.addView(mRevert);
        strip.addView(action(context, R.string.edit_mode_done, view -> leave()));
        return strip;
    }

    // ---------------------------------------------------------------- state

    private void endDrag(@NonNull RowViews row) {
        if (!row.dragging) return;
        row.dragging = false;
        if (mSession != null) mSession.dragEnded(row.surface, row.property);
    }

    /** Shows the store and the session as they are now. */
    private void refresh() {
        EditSession session = mSession;
        if (!isOpen() || session == null) return;
        Context context = mHost.context();
        TerlLookStore looks = mHost.looks();
        int accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary,
            0xFF8AB4F8);
        for (RowViews row : mRows) {
            int value = looks.value(row.surface, row.property);
            boolean own = looks.holdsOwn(row.surface, row.property);
            if (!row.dragging && row.slider.getProgress() != value - row.property.min) {
                row.slider.setProgress(value - row.property.min);
            }
            setText(row.value, context.getString(row.property == TerlProperty.OPACITY
                ? R.string.edit_mode_percent : R.string.edit_mode_dp, value));
            if (row.shownOwn != null && row.shownOwn == own) continue;
            row.shownOwn = own;
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            // A filled dot on an own value (a tap follows Global); a hollow ring while following.
            if (own) dot.setColor(accent);
            else dot.setStroke(dp(2), accent);
            row.marker.setBackground(dot);
            row.markerTarget.setClickable(own);
            String says = context.getString(own ? R.string.edit_mode_follow_global
                : R.string.edit_mode_follows_global);
            row.markerTarget.setContentDescription(says);
            androidx.appcompat.widget.TooltipCompat.setTooltipText(row.markerTarget, says);
        }
        int changes = session.changeCount();
        int kept = session.keptElsewhereCount();
        String count = changes == 0 ? context.getString(R.string.edit_mode_no_changes)
            : context.getResources().getQuantityString(R.plurals.edit_mode_changes, changes, changes);
        if (kept > 0) {
            count = count + " · " + context.getResources().getQuantityString(
                R.plurals.edit_mode_kept_elsewhere, kept, kept);
        }
        if (mCount != null) setText(mCount, count);
        if (mUndo != null) setEnabled(mUndo, session.canUndo());
        if (mRevert != null) setEnabled(mRevert, changes > 0);
    }

    /** Puts the outline on the keyboard and the card above it; moves a view only when it changed. */
    private void place() {
        FrameLayout overlay = mOverlay;
        if (overlay == null || mOutline == null) return;
        View keyboard = mHost.isKeyboardShown() ? mHost.keyboardView() : null;
        int top = EditModePlacement.OFF_SCREEN;
        if (keyboard != null && keyboard.isShown() && keyboard.getWidth() > 0
            && keyboard.getHeight() > 0) {
            int[] at = new int[2];
            int[] origin = new int[2];
            keyboard.getLocationOnScreen(at);
            overlay.getLocationOnScreen(origin);
            int left = at[0] - origin[0];
            top = at[1] - origin[1];
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mOutline.getLayoutParams();
            if (params.leftMargin != left || params.topMargin != top
                || params.width != keyboard.getWidth() || params.height != keyboard.getHeight()) {
                params.leftMargin = left;
                params.topMargin = top;
                params.width = keyboard.getWidth();
                params.height = keyboard.getHeight();
                mOutline.setLayoutParams(params);
            }
        }
        setVisible(mOutline, top != EditModePlacement.OFF_SCREEN);
        if (mKeyboardChip != null) {
            setVisible(mKeyboardChip, top == EditModePlacement.OFF_SCREEN && mOpenCard == null);
        }
        if (mCard != null && mStrip != null && overlay.getHeight() > 0) {
            int contentBottom = overlay.getHeight() - overlay.getPaddingBottom();
            ViewGroup.MarginLayoutParams stripParams =
                (ViewGroup.MarginLayoutParams) mStrip.getLayoutParams();
            int margin = EditModePlacement.cardBottomMarginPx(contentBottom, top,
                mStrip.getHeight() + stripParams.bottomMargin, dp(GAP_DP));
            FrameLayout.LayoutParams cardParams = (FrameLayout.LayoutParams) mCard.getLayoutParams();
            if (cardParams.bottomMargin != margin) {
                cardParams.bottomMargin = margin;
                mCard.setLayoutParams(cardParams);
            }
        }
    }

    // ---------------------------------------------------------------- small views

    @NonNull
    private TextView text(@NonNull Context context, int res, int sp, int colorAttr) {
        TextView view = new TextView(context);
        view.setText(res);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(MaterialColors.getColor(context, colorAttr, 0xFFFFFFFF));
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    @NonNull
    private TextView action(@NonNull Context context, int res, @NonNull View.OnClickListener click) {
        TextView view = text(context, res, 14, androidx.appcompat.R.attr.colorPrimary);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(TOUCH_DP + 8));
        view.setMinWidth(0);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackgroundResource(selectableBorderless(context));
        view.setOnClickListener(click);
        return view;
    }

    @NonNull
    private static GradientDrawable panel(@NonNull Context context, float radius) {
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(radius);
        fill.setColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xF0202124));
        return fill;
    }

    private static int selectableBorderless(@NonNull Context context) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,
            value, true);
        return value.resourceId;
    }

    private static void setText(@NonNull TextView view, @NonNull CharSequence text) {
        if (!text.toString().contentEquals(view.getText())) view.setText(text);
    }

    private static void setVisible(@NonNull View view, boolean visible) {
        int wanted = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != wanted) view.setVisibility(wanted);
    }

    private static void setEnabled(@NonNull View view, boolean enabled) {
        if (view.isEnabled() != enabled) view.setEnabled(enabled);
        float alpha = enabled ? 1f : DISABLED_ALPHA;
        if (view.getAlpha() != alpha) view.setAlpha(alpha);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            mHost.context().getResources().getDisplayMetrics()));
    }
}
