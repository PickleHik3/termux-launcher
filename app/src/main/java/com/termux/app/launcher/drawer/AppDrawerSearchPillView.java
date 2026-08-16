package com.termux.app.launcher.drawer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

import com.termux.R;

/**
 * The drawer's query field — which is not a field at all.
 *
 * <p>It is a plain {@link View} that paints a hint, a query and a caret, and it never takes focus.
 * That is the whole design decision: the terminal owns the window's {@code InputConnection}, the
 * in-app keyboard is not an IME, and an {@code EditText} here would either steal that connection or
 * drag in {@code beginExternalTextInput()} — which calls {@code requestAccessoryGeometrySync()},
 * exactly the path the drawer transition freezes. The palette's filter row solved the same problem
 * the same way; this is the second instance of one pattern, not a new one.
 *
 * <p>So the pill displays state and reports two taps. Everything typed arrives at
 * {@link AppDrawerSearchController} through the keyboard's own interceptor, hardware key events or
 * committed IME text, and comes back here as {@link #setQuery}.
 *
 * <p>The corner radius is supplied by the caller and clamped to a capsule
 * ({@code min(surfaceRadiusPx, height / 2)}). There is deliberately no radius constant in this file:
 * the drawer's radius is a user preference the plane already resolves, and a literal here would put
 * a second, disagreeing corner inside the first one.
 */
public final class AppDrawerSearchPillView extends View {

    /** The pill's two taps. Both are display-surface reports; the pill changes nothing itself. */
    public interface Callbacks {

        /** The clear affordance was tapped while a query was showing. */
        void onSearchPillClear();

        /** The pill itself was tapped — a request for somewhere to type. */
        void onSearchPillTapped();
    }

    /** Pill height, in dp. The caller lays it out; this is only the default it starts from. */
    public static final float HEIGHT_DP = 46f;

    private static final float PAD_H_DP = 16f;
    private static final float TEXT_SP = 16f;
    /** Leading search glyph, drawn at half strength before the query/hint. */
    private static final String SEARCH_GLYPH = "⌕";
    /**
     * Larger than the text it leads, because this glyph's ink is barely half its point size — at the
     * hint's own 16sp it measured 7dp tall against 15dp of text and read as a speck. 22sp brings its
     * ink up to the hint's cap height.
     */
    private static final float SEARCH_GLYPH_SP = 22f;
    private static final float GLYPH_TO_TEXT_DP = 8f;
    /** Width of the tap target at the trailing edge, which is larger than the glyph drawn in it. */
    private static final float CLEAR_TOUCH_DP = 44f;
    private static final float CLEAR_GLYPH_SP = 15f;
    private static final String CLEAR_GLYPH = "✕";
    /** Fill of the pill against the glass: a wash of the surface colour, not a second surface. */
    private static final int FILL_ALPHA = 15;
    private static final int STROKE_ALPHA = 33;
    /** Hint at 42% and the clear glyph at 55%, per the mock. */
    private static final int HINT_ALPHA = 0x6B;
    private static final int CLEAR_ALPHA = 0x8C;
    private static final int GLYPH_ALPHA = 0x80;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final float mDensity;
    private final float mScaledDensity;

    @Nullable private Callbacks mCallbacks;
    @NonNull private String mQuery = "";
    @NonNull private String mHint = "";
    private int mCaret;
    private float mSurfaceRadiusPx;
    /** True while a DOWN that landed on the clear affordance is still live. */
    private boolean mClearPressed;

    public AppDrawerSearchPillView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mScaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        // Never focusable, in any mode: a focus change here is a focus change away from the
        // terminal, and the terminal is what the keyboard is typing into.
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(true);
        setWillNotDraw(false);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(Math.max(1f, mDensity));
        // Weight 500 per the mock's query text.
        mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mHint = context.getString(R.string.app_drawer_search_hint);
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    /** The query and the caret offset into it, both straight off {@link AppDrawerSearchModel}. */
    public void setQuery(@NonNull String query, int caret) {
        if (mQuery.equals(query) && mCaret == caret) return;
        mQuery = query;
        mCaret = caret;
        invalidate();
    }

    public void setHint(@NonNull String hint) {
        if (mHint.equals(hint)) return;
        mHint = hint;
        invalidate();
    }

    /** The drawer surface's own corner radius; the pill clamps it to a capsule for its height. */
    public void setSurfaceRadiusPx(float radiusPx) {
        float radius = Math.max(0f, radiusPx);
        if (mSurfaceRadiusPx == radius) return;
        mSurfaceRadiusPx = radius;
        invalidate();
    }

    public boolean hasQuery() {
        return !mQuery.isEmpty();
    }

    /** The radius actually painted: the surface token, never more than a capsule. */
    public float resolveRadiusPx() {
        return Math.min(mSurfaceRadiusPx, getHeight() * 0.5f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) return;
        int onSurface = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(getContext(), R.color.termux_on_surface));
        int accent = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(getContext(), R.color.termux_primary));

        float radius = resolveRadiusPx();
        float inset = mStroke.getStrokeWidth() * 0.5f;
        mBounds.set(inset, inset, width - inset, height - inset);
        mFill.setColor(ColorUtils.setAlphaComponent(onSurface, FILL_ALPHA));
        canvas.drawRoundRect(mBounds, radius, radius, mFill);
        mStroke.setColor(ColorUtils.setAlphaComponent(onSurface, STROKE_ALPHA));
        canvas.drawRoundRect(mBounds, radius, radius, mStroke);

        float padH = PAD_H_DP * mDensity;
        mText.setTextSize(TEXT_SP * mScaledDensity);
        Paint.FontMetrics metrics = mText.getFontMetrics();
        float baseline = (height * 0.5f) - ((metrics.ascent + metrics.descent) * 0.5f);
        // Leading search glyph at half strength, then the query/hint after a small gap.
        mText.setTextSize(SEARCH_GLYPH_SP * mScaledDensity);
        mText.setColor(ColorUtils.setAlphaComponent(onSurface, GLYPH_ALPHA));
        canvas.drawText(SEARCH_GLYPH, padH, baseline, mText);
        float glyphAdvance = mText.measureText(SEARCH_GLYPH) + GLYPH_TO_TEXT_DP * mDensity;
        mText.setTextSize(TEXT_SP * mScaledDensity);
        float textLeft = padH + glyphAdvance;
        float textRight = width - padH - (hasQuery() ? clearWidthPx() : 0f);
        float available = Math.max(0f, textRight - textLeft);

        if (!hasQuery()) {
            mText.setColor(ColorUtils.setAlphaComponent(onSurface, HINT_ALPHA));
            canvas.drawText(ellipsizeStart(mHint, available), textLeft, baseline, mText);
            return;
        }

        mText.setColor(onSurface);
        String shown = ellipsizeStart(mQuery, available);
        canvas.drawText(shown, textLeft, baseline, mText);

        // Caret: the same one-pixel rule the palette draws, at the same fraction of the line.
        int caret = Math.max(0, Math.min(mCaret, mQuery.length()));
        float caretX = textLeft + Math.min(mText.measureText(mQuery, 0, caret), available);
        mFill.setColor(accent);
        canvas.drawRect(caretX, baseline + metrics.ascent * 0.86f, caretX + Math.max(1f, mDensity),
            baseline + (metrics.descent * 0.6f), mFill);

        mText.setColor(ColorUtils.setAlphaComponent(onSurface, CLEAR_ALPHA));
        mText.setTextSize(CLEAR_GLYPH_SP * mScaledDensity);
        float glyphWidth = mText.measureText(CLEAR_GLYPH);
        canvas.drawText(CLEAR_GLYPH, width - padH - glyphWidth, baseline, mText);
    }

    private float clearWidthPx() {
        return CLEAR_TOUCH_DP * mDensity;
    }

    /** Keeps the tail of an over-long query visible: what was typed last is what is being read. */
    @NonNull
    private String ellipsizeStart(@NonNull String text, float available) {
        if (available <= 0f || text.isEmpty()) return "";
        if (mText.measureText(text) <= available) return text;
        int start = 0;
        while (start < text.length() && mText.measureText("…" + text.substring(start)) > available) {
            start++;
        }
        return "…" + text.substring(start);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mClearPressed = hasQuery() && isOverClear(event.getX());
                return true;
            case MotionEvent.ACTION_UP: {
                boolean clear = mClearPressed && hasQuery() && isOverClear(event.getX());
                mClearPressed = false;
                if (mCallbacks == null) return true;
                if (clear) {
                    mCallbacks.onSearchPillClear();
                } else {
                    mCallbacks.onSearchPillTapped();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                mClearPressed = false;
                return true;
            default:
                return true;
        }
    }

    private boolean isOverClear(float x) {
        return x >= getWidth() - clearWidthPx();
    }

    @Override
    public void setFocusable(int focusable) {
        // The one way this view could ever take focus is a caller asking it to. Refused at the
        // setter rather than trusted at the constructor: focus here is focus away from the terminal,
        // which owns the window's InputConnection.
        super.setFocusable(NOT_FOCUSABLE);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        // Belt and braces against a system IME that walks the tree looking for an editor: this view
        // has no InputConnection to give and must never be offered one.
        return false;
    }
}
