package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.haptics.Haptics;
import com.termux.app.terminal.ClipboardHistory;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.List;

/**
 * The keyboard's clipboard panel: the things copied inside the launcher, pinned ones first, each
 * a tap away from being pasted again. It takes the keys' place and size while it is up, the way
 * mouse mode's touchpad does, and is drawn the same way: one opaque rounded panel in the overlay
 * surface colour, inset a pixel as a card with the surfaces or flush against the dock, and no
 * rim. The pill in its top-left corner is the touchpad's exit arrow, here with the keyboard
 * glyph: it puts the keys back.
 *
 * <p>A row is one item — a monospace preview of its first lines, a pin, a remove — and tapping
 * the row pastes it. "Clear" takes two taps within a few seconds and never touches the pins,
 * so a stray thumb cannot empty the list. Nothing here animates on its own: the panel is
 * brought in and taken out by {@link ClipboardPanelController}, and every change re-lays the
 * rows out once.
 */
public final class ClipboardPanelView extends FrameLayout implements ClipboardHistory.Listener {

    /** What the panel asks of its host. */
    public interface Listener {
        /** A row was tapped: paste its text where the keyboard's own paste key pastes. */
        void onPasteRequested(@NonNull String text);

        /** The pill was tapped: back to the keys. */
        void onCloseRequested();
    }

    /** The four roles the panel is painted in, resolved by the host through the theme. */
    public static final class Palette {
        public final int surface;
        public final int onSurface;
        public final int onSurfaceVariant;
        public final int accent;

        public Palette(int surface, int onSurface, int onSurfaceVariant, int accent) {
            this.surface = surface;
            this.onSurface = onSurface;
            this.onSurfaceVariant = onSurfaceVariant;
            this.accent = accent;
        }
    }

    /** The touchpad's corner and pill, so the two read as one family. */
    private static final float RADIUS_DP = 20f;
    private static final float PILL_HEIGHT_DP = 28f;
    private static final float PILL_WIDTH_DP = 40f;
    /** How long a first tap on Clear stays armed for the second. */
    static final long CLEAR_ARM_MS = 3000L;
    /** How many lines of an item the preview shows. */
    private static final int PREVIEW_LINES = 2;

    private static final String GLYPH_KEYBOARD = ""; // nf-fa-keyboard_o, the touchpad's
    private static final String GLYPH_PIN = new String(Character.toChars(0xF0403)); // nf-md-pin
    private static final String GLYPH_PIN_OFF = new String(Character.toChars(0xF0404)); // nf-md-pin_off
    private static final String GLYPH_CLOSE = new String(Character.toChars(0xF0156)); // nf-md-close

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final boolean mCard;
    @NonNull private final Palette mPalette;
    @NonNull private final Listener mListener;
    @Nullable private final Typeface mSymbols;

    private final TextView mClear;
    private final TextView mCount;
    private final ScrollView mScroll;
    private final LinearLayout mList;
    private final View mEmpty;

    @Nullable private ClipboardHistory mHistory;
    private boolean mClearArmed;
    /** Its own handler rather than the view's queue: the disarm must run even if the panel is detached first. */
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDisarmClear = () -> setClearArmed(false);

    public ClipboardPanelView(@NonNull Context context, @NonNull Palette palette, boolean card,
                              @NonNull Listener listener) {
        super(context);
        mPalette = palette;
        mCard = card;
        mListener = listener;
        mSymbols = NerdFontSpans.typeface(context);
        // Opaque in both shapes, like the pad: the panel lies over the place, never over glass.
        mFillPaint.setColor(ColorUtils.setAlphaComponent(palette.surface, 255));
        setWillNotDraw(false);
        // The keys are still under the panel; a touch that lands between rows must stop here.
        setClickable(true);
        setFocusable(false);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = dp(10);
        column.setPadding(side, dp(6), side, dp(6));
        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        // Header: the pill back to the keys, the title, and Clear on the trailing side.
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        TextView pill = new TextView(context);
        pill.setText(GLYPH_KEYBOARD);
        pill.setTypeface(mSymbols);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
        pill.setTextColor(palette.accent);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(pillBackground(ColorUtils.setAlphaComponent(palette.accent, 46)));
        pill.setContentDescription(context.getString(R.string.clipboard_panel_back_to_keys));
        pill.setOnClickListener(v -> {
            Haptics.tick(v, HapticFeedbackConstants.CONTEXT_CLICK);
            mListener.onCloseRequested();
        });
        header.addView(pill, new LinearLayout.LayoutParams(dp(PILL_WIDTH_DP), dp(PILL_HEIGHT_DP)));

        TextView title = new TextView(context);
        title.setText(R.string.clipboard_panel_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(palette.onSurface);
        title.setSingleLine();
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMarginStart(dp(10));
        header.addView(title, titleParams);

        mCount = new TextView(context);
        mCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        mCount.setTextColor(palette.onSurfaceVariant);
        mCount.setSingleLine();
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        countParams.setMarginStart(dp(8));
        header.addView(mCount, countParams);

        mClear = new TextView(context);
        mClear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        mClear.setTypeface(Typeface.DEFAULT_BOLD);
        mClear.setSingleLine();
        mClear.setGravity(Gravity.CENTER);
        mClear.setMinWidth(dp(56));
        mClear.setPadding(dp(12), 0, dp(12), 0);
        mClear.setBackground(pillBackground(0));
        mClear.setOnClickListener(v -> onClearTapped());
        header.addView(mClear, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(PILL_HEIGHT_DP)));
        setClearArmed(false);

        // The rows, in a plain scroll: the list is bounded to a few dozen items.
        mScroll = new ScrollView(context);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mList = new LinearLayout(context);
        mList.setOrientation(LinearLayout.VERTICAL);
        mList.setPadding(0, dp(2), 0, dp(6));
        mScroll.addView(mList, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(mScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // The empty state, in the scroll's place: what shows up here, in a sentence.
        LinearLayout empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), 0, dp(24), dp(12));
        TextView emptyTitle = new TextView(context);
        emptyTitle.setText(R.string.clipboard_panel_empty_title);
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        emptyTitle.setTypeface(Typeface.DEFAULT_BOLD);
        emptyTitle.setTextColor(palette.onSurface);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle);
        TextView emptyBody = new TextView(context);
        emptyBody.setText(R.string.clipboard_panel_empty_body);
        emptyBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        emptyBody.setTextColor(palette.onSurfaceVariant);
        emptyBody.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(4);
        empty.addView(emptyBody, bodyParams);
        mEmpty = empty;
        column.addView(empty, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** The history this panel shows; it listens while attached and lets go when it leaves. */
    public void bind(@NonNull ClipboardHistory history) {
        if (mHistory != null) mHistory.setListener(null);
        mHistory = history;
        if (isAttachedToWindow()) history.setListener(this);
        rebuild();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mHistory != null) {
            mHistory.setListener(this);
            rebuild();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mHandler.removeCallbacks(mDisarmClear);
        mClearArmed = false;
        if (mHistory != null) mHistory.setListener(null);
        super.onDetachedFromWindow();
    }

    @Override
    public void onClipboardHistoryChanged() {
        rebuild();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = mCard ? dpF(1f) : 0f;
        mBounds.set(inset, inset, w - inset, h - inset);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float radius = dpF(RADIUS_DP);
        canvas.drawRoundRect(mBounds, radius, radius, mFillPaint);
    }

    /** Lays every row out again from the history. Bounded input, so one pass is cheap. */
    private void rebuild() {
        ClipboardHistory history = mHistory;
        mList.removeAllViews();
        if (history == null || history.isEmpty()) {
            mScroll.setVisibility(GONE);
            mEmpty.setVisibility(VISIBLE);
            mCount.setText("");
            mClear.setVisibility(GONE);
            setClearArmed(false);
            return;
        }
        mScroll.setVisibility(VISIBLE);
        mEmpty.setVisibility(GONE);
        List<ClipboardHistory.Item> items = history.items();
        mCount.setText(getResources().getQuantityString(R.plurals.clipboard_panel_count,
            items.size(), items.size()));
        mClear.setVisibility(history.recentCount() > 0 ? VISIBLE : GONE);
        if (history.recentCount() == 0) setClearArmed(false);
        boolean pinnedSection = false;
        boolean recentSection = false;
        for (ClipboardHistory.Item item : items) {
            if (item.pinned && !pinnedSection) {
                pinnedSection = true;
                mList.addView(sectionLabel(R.string.clipboard_panel_section_pinned));
            } else if (!item.pinned && !recentSection) {
                recentSection = true;
                if (pinnedSection) mList.addView(sectionLabel(R.string.clipboard_panel_section_recent));
            }
            mList.addView(row(item));
        }
    }

    private View sectionLabel(int textRes) {
        TextView label = new TextView(getContext());
        label.setText(textRes);
        label.setAllCaps(true);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        label.setLetterSpacing(0.06f);
        label.setTextColor(mPalette.onSurfaceVariant);
        label.setPadding(dp(12), dp(6), dp(12), dp(2));
        return label;
    }

    private View row(@NonNull ClipboardHistory.Item item) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        row.setPadding(dp(12), dp(4), dp(4), dp(4));
        // A pinned row sits on a faint wash of the accent; a recent one on the panel itself.
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(12));
        background.setColor(item.pinned ? ColorUtils.setAlphaComponent(mPalette.accent, 24) : 0);
        row.setBackground(background);
        row.setClickable(true);
        String preview = preview(item.text);
        row.setContentDescription(context.getString(R.string.clipboard_panel_paste_item, preview));
        row.setOnClickListener(v -> {
            Haptics.tick(v, HapticFeedbackConstants.CONTEXT_CLICK);
            mListener.onPasteRequested(item.text);
        });
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(2);
        row.setLayoutParams(rowParams);

        TextView text = new TextView(context);
        text.setText(preview);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        text.setTextColor(mPalette.onSurface);
        text.setMaxLines(PREVIEW_LINES);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(glyphButton(item.pinned ? GLYPH_PIN_OFF : GLYPH_PIN,
            item.pinned ? mPalette.accent : mPalette.onSurfaceVariant,
            context.getString(item.pinned ? R.string.clipboard_panel_unpin : R.string.clipboard_panel_pin),
            () -> {
                ClipboardHistory history = mHistory;
                if (history == null) return;
                if (item.pinned) {
                    history.unpin(item.text);
                } else if (!history.pin(item.text)) {
                    // The pins are full: the row stays, and the count line says why.
                    mCount.setText(getResources().getString(R.string.clipboard_panel_pins_full,
                        ClipboardHistory.MAX_PINNED));
                }
            }));
        row.addView(glyphButton(GLYPH_CLOSE, mPalette.onSurfaceVariant,
            context.getString(R.string.clipboard_panel_remove),
            () -> {
                ClipboardHistory history = mHistory;
                if (history != null) history.remove(item.text);
            }));
        return row;
    }

    private View glyphButton(@NonNull String glyph, int color, @NonNull String description,
                             @NonNull Runnable action) {
        TextView button = new TextView(getContext());
        button.setText(glyph);
        button.setTypeface(mSymbols);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
        button.setTextColor(color);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(pillBackground(0));
        button.setOnClickListener(v -> {
            Haptics.tick(v, HapticFeedbackConstants.CONTEXT_CLICK);
            action.run();
        });
        return sized(button, dp(40), dp(40));
    }

    private static View sized(@NonNull View view, int width, int height) {
        view.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return view;
    }

    /** A capsule fill, the touchpad's pill; transparent is a bare touch target. */
    private GradientDrawable pillBackground(int color) {
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(dpF(PILL_HEIGHT_DP) / 2f);
        pill.setColor(color);
        return pill;
    }

    private void onClearTapped() {
        ClipboardHistory history = mHistory;
        if (history == null) return;
        if (!mClearArmed) {
            setClearArmed(true);
            return;
        }
        setClearArmed(false);
        Haptics.tick(this, HapticFeedbackConstants.CONTEXT_CLICK);
        history.clearRecent();
    }

    /** Armed, Clear says what a second tap does, in the accent, and disarms itself in a moment. */
    private void setClearArmed(boolean armed) {
        mHandler.removeCallbacks(mDisarmClear);
        mClearArmed = armed;
        Context context = getContext();
        mClear.setText(armed ? R.string.clipboard_panel_clear_confirm : R.string.clipboard_panel_clear);
        mClear.setTextColor(armed ? mPalette.accent : mPalette.onSurfaceVariant);
        mClear.setContentDescription(context.getString(armed
            ? R.string.clipboard_panel_clear_confirm_description
            : R.string.clipboard_panel_clear_description));
        if (armed) mHandler.postDelayed(mDisarmClear, CLEAR_ARM_MS);
    }

    /** Whether Clear is waiting for its second tap. */
    boolean isClearArmed() {
        return mClearArmed;
    }

    /** How many rows are laid out right now, section labels excluded. */
    int rowCount() {
        int rows = 0;
        for (int i = 0; i < mList.getChildCount(); i++) {
            if (mList.getChildAt(i) instanceof LinearLayout) rows++;
        }
        return rows;
    }

    /**
     * The first lines of an item, for its row: leading blank lines and indentation dropped, tabs
     * as spaces, and cut after {@link #PREVIEW_LINES} so a long item costs no more to lay out
     * than a short one.
     */
    @NonNull
    static String preview(@NonNull String text) {
        String trimmed = text.trim().replace('\t', ' ');
        int end = trimmed.length();
        int newline = -1;
        for (int line = 0; line < PREVIEW_LINES; line++) {
            newline = trimmed.indexOf('\n', newline + 1);
            if (newline < 0) return trimmed;
        }
        // One more line than the preview shows: the ellipsis says there is more.
        end = Math.min(end, newline + 2);
        return trimmed.substring(0, end);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dpF(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
