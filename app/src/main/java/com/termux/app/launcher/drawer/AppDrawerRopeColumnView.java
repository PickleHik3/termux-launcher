package com.termux.app.launcher.drawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import com.termux.R;
import com.termux.app.RowHapticTickHelper;
import com.termux.app.SuggestionBarView;

/**
 * The drawer's A-Z rope: a strip of letters down the plane's right edge that hangs off the
 * transition and scrubs the grid.
 *
 * <p><b>It owns no time source.</b> {@link #advance} is called from
 * {@link AppDrawerContentView#advanceDrawerFx}, which is called from the drawer controller's one
 * {@link android.view.Choreographer} loop, so the growing plane and the letters inside it are
 * rendered on the same frame. A loop of its own would render them a frame apart, which on the
 * opening drag is a visible seam between the panel edge and the letters standing off it.
 *
 * <p><b>The stream is claimed at {@code ACTION_DOWN} and never re-decided.</b> A scrub cannot be
 * told apart from the plane's close drag by motion — it is a sustained downward drag in the same
 * place at the same speed — so a touch that lands on the strip is a scrub for its whole life. That
 * is also why only Y is read after the down: X is spent by
 * {@link AppDrawerTouchRegions#resolve} deciding the stream belongs here, and a finger that then
 * drifts left onto the grid keeps scrubbing rather than dropping out. A tap is simply a scrub that
 * lasted 80ms; there is no timeout and no tap slop.
 *
 * <p>This view <b>never reports a close and never nested-scrolls</b>. It is a plain {@link View}, so
 * there is nothing to opt out of; the guarantee it makes to the drawer is that
 * {@link AppDrawerContentView}'s close path is unreachable from a column stream.
 *
 * <p><b>Legibility over glass.</b> The letters sit over the plane's blur/frost with the wallpaper
 * behind it, so they are drawn the way the dock's own {@code AzScrubRowView} draws them: a crisp
 * dark stroke pass under a light fill pass. Nothing here shares that view's wave, shimmer or
 * settle animators — the rope replaces all three.
 *
 * <p><b>The lean can carry the head past the plane's right edge</b> while the column is still fading
 * in. That is the entry, not a bug: the rest line is the strip's centre 15dp from the edge and the
 * anchor starts 26dp outward, so the letters come from off the panel. It is also why
 * {@link AppDrawerRopeMetrics#COLUMN_ALPHA_END} (0.60) is well before the anchor is home (0.86) —
 * by the time the column is fully opaque the head is back inside the strip, and the whole settle
 * happens in full view.
 */
public final class AppDrawerRopeColumnView extends View {

    /** What a scrub tells the grid. Deliberately two calls and nothing else. */
    public interface Callbacks {

        /** The letter under the finger changed, including the first one at {@code ACTION_DOWN}. */
        void onScrubLetterChanged(char letter);

        /** The finger left the glass, or the stream was taken away. Fired exactly once per scrub. */
        void onScrubEnded();
    }

    /**
     * How much larger the focused letter is drawn. Held under the slot height so the letter above
     * and below are pushed apart visually rather than overlapped.
     */
    public static final float FOCUS_GLYPH_SCALE = 1.45f;
    /** Same desaturated near-black the dock's A-Z row strokes with. */
    private static final int OUTLINE_DARK = 0xFF1A1F2A;
    private static final int OUTLINE_ALPHA = 195;
    private static final int OUTLINE_ALPHA_FOCUSED = 215;
    private static final float OUTLINE_STROKE_DP = 1.4f;

    private static final char[] NO_LETTERS = new char[0];
    private static final String[] NO_GLYPHS = new String[0];

    private final AppDrawerRopeModel mModel = new AppDrawerRopeModel();
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics mFontMetrics = new Paint.FontMetrics();
    private final float mDensity;
    private final int mFocusColor;

    @Nullable private Callbacks mCallbacks;
    @Nullable private SuggestionBarView mDock;
    @Nullable private AppDrawerRopeMetrics mMetrics;

    @NonNull private char[] mLetters = NO_LETTERS;
    /** One string per letter, built with the letter set: {@code drawText} takes a CharSequence. */
    @NonNull private String[] mGlyphs = NO_GLYPHS;

    private boolean mActive;
    private boolean mScrubbing;
    private int mActiveIndex = -1;
    private int mHapticIndex = -1;
    private float mProgress;
    private float mDrawAlpha;

    public AppDrawerRopeColumnView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        // The same resolution prepareOverlay uses for the plane's own accent, so the focused letter
        // and the drawer's glass tint are the one colour rather than two ideas of the theme.
        mFocusColor = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mFillPaint.setTextAlign(Paint.Align.CENTER);
        mOutlinePaint.setTextAlign(Paint.Align.CENTER);
        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setStrokeJoin(Paint.Join.ROUND);
        mOutlinePaint.setStrokeCap(Paint.Cap.ROUND);
        mOutlinePaint.setStrokeWidth(mDensity * OUTLINE_STROKE_DP);
        setAlpha(0f);
    }

    /**
     * A layer would be allocated for the fade this view spends most of the transition in, and there
     * is nothing behind the letters here to blend with: the outline pass is drawn under each glyph
     * individually.
     */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    /** The dock the base colour and the haptics preference are borrowed from. */
    public void setDock(@Nullable SuggestionBarView dock) {
        mDock = dock;
    }

    /**
     * The visible letter set, in {@link AppDrawerSectionIndex#AZ_ORDER}. Copied, and any scrub in
     * flight is dropped: the index a finger was holding means a different letter in a new set.
     */
    public void setLetters(@Nullable char[] letters) {
        int count = letters == null ? 0 : Math.min(letters.length, AppDrawerRopeModel.MAX_LETTERS);
        if (count == mLetters.length) {
            boolean same = true;
            for (int i = 0; i < count; i++) {
                if (mLetters[i] != letters[i]) {
                    same = false;
                    break;
                }
            }
            if (same) return;
        }
        cancelScrub();
        mLetters = count == 0 ? NO_LETTERS : new char[count];
        mGlyphs = count == 0 ? NO_GLYPHS : new String[count];
        for (int i = 0; i < count; i++) {
            mLetters[i] = letters[i];
            mGlyphs[i] = String.valueOf(letters[i]);
        }
        mMetrics = null;
        invalidate();
    }

    /** Letters currently drawn. */
    public int letterCount() {
        return mLetters.length;
    }

    /**
     * False when a query is up or the catalogue has fewer than two letters: the column stops drawing
     * and stops taking touches, and {@link AppDrawerContentView} resolves its strip to
     * {@link AppDrawerTouchRegions.Region#CHROME} so the close drag works there instead.
     *
     * <p>Deactivation is not animated. A query change is already a wholesale list swap with a jump
     * back to the top; a column that faded out over 200ms while the list underneath it changed
     * identity would be the only thing on the surface pretending the two lists were related.
     */
    public void setActive(boolean active) {
        if (mActive == active) return;
        mActive = active;
        if (!active) cancelScrub();
        applyDrawAlpha();
        invalidate();
    }

    public boolean isActive() {
        return mActive && mLetters.length > 0;
    }

    /**
     * One frame of the rope.
     *
     * @param p       the transition progress the anchor is a function of
     * @param dt      the controller loop's delta, in seconds
     * @param reduced true when the animator duration scale is 0: the chain collapses straight
     * @return true while the chain still needs another frame
     */
    public boolean advance(float p, float dt, boolean reduced) {
        mProgress = AppDrawerTransitionGeometry.clamp01(p);
        AppDrawerRopeMetrics metrics = metrics();
        if (metrics == null) {
            // Nothing has been laid out or there are no letters: there is no anchor to drive the
            // chain with, so the loop must not be held open waiting for one.
            mModel.reset();
            applyDrawAlpha();
            return false;
        }
        boolean moving = mModel.advance(metrics.anchorPx(mProgress), dt, reduced);
        applyDrawAlpha();
        // The offsets moved, or the fade did; either way this frame draws differently.
        invalidate();
        return moving;
    }

    /** Drops the chain back to the straight rest line, for a drawer that has closed. */
    public void resetRope() {
        mModel.reset();
        mProgress = 0f;
        applyDrawAlpha();
        invalidate();
    }

    /** @return true when a scrub was in flight and has been ended */
    public boolean cancelScrub() {
        if (!mScrubbing) return false;
        endScrub();
        return true;
    }

    public boolean isScrubbing() {
        return mScrubbing;
    }

    /** The letter under the finger, or {@code 0} when there is no scrub. */
    public char activeLetter() {
        if (!mScrubbing || mActiveIndex < 0 || mActiveIndex >= mLetters.length) return '\0';
        return mLetters[mActiveIndex];
    }

    /** The rope's own geometry, or null before layout or with no letters. */
    @Nullable
    public AppDrawerRopeMetrics metrics() {
        if (mMetrics != null) return mMetrics;
        if (mLetters.length == 0 || getHeight() <= 0) return null;
        mMetrics = AppDrawerRopeMetrics.resolve(getHeight(), mLetters.length, mDensity);
        return mMetrics;
    }

    // ------------------------------------------------------------------ draw

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // Slot and glyph size are functions of the track height; a rotation must not reuse them.
        if (height != oldHeight) mMetrics = null;
    }

    private void applyDrawAlpha() {
        float alpha = isActive() ? AppDrawerRopeMetrics.alpha(mProgress) : 0f;
        if (alpha == mDrawAlpha) return;
        mDrawAlpha = alpha;
        setAlpha(alpha);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        AppDrawerRopeMetrics metrics = metrics();
        if (metrics == null || !isActive() || mDrawAlpha <= 0f) return;
        float centerX = getWidth() * 0.5f;
        int baseColor = mDock != null ? mDock.getLauncherTextColor() : 0xFFFFFFFF;
        float baseGlyphPx = metrics.glyphTextSizePx;
        // The focused letter grows, but never past its slot: two letters that overlap read as a
        // rendering fault rather than as emphasis.
        float focusGlyphPx = Math.min(metrics.slotHeightPx, baseGlyphPx * FOCUS_GLYPH_SCALE);
        int count = Math.min(mLetters.length, metrics.letterCount);
        for (int i = 0; i < count; i++) {
            boolean focused = mScrubbing && i == mActiveIndex;
            float x = centerX + mModel.offsetPx(i);
            float y = metrics.centerYForIndex(i);
            mFillPaint.setTextSize(focused ? focusGlyphPx : baseGlyphPx);
            mFillPaint.setColor(focused ? mFocusColor : baseColor);
            applyLetterWeight(focused);
            mFillPaint.getFontMetrics(mFontMetrics);
            // Centred on the slot rather than sat on a baseline: the slot centre is what indexForY
            // maps a finger to, so the glyph the finger is over has to be the glyph drawn there.
            float baseline = y - ((mFontMetrics.ascent + mFontMetrics.descent) * 0.5f);
            mOutlinePaint.setTextSize(mFillPaint.getTextSize());
            mOutlinePaint.setTypeface(mFillPaint.getTypeface());
            mOutlinePaint.setColor(withAlpha(OUTLINE_DARK,
                focused ? OUTLINE_ALPHA_FOCUSED : OUTLINE_ALPHA));
            String glyph = mGlyphs[i];
            float tilt = mModel.tiltDeg(i);
            int saved = canvas.save();
            // About the glyph's own centre, so a tilted letter stays in its slot instead of
            // swinging away from the finger that is holding it.
            canvas.rotate(tilt, x, y);
            canvas.drawText(glyph, x, baseline, mOutlinePaint);
            canvas.drawText(glyph, x, baseline, mFillPaint);
            canvas.restoreToCount(saved);
        }
    }

    private void applyLetterWeight(boolean focused) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mFillPaint.setTypeface(Typeface.create(Typeface.DEFAULT, focused ? 900 : 500, false));
            return;
        }
        mFillPaint.setTypeface(focused ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        AppDrawerRopeMetrics metrics = metrics();
        if (!isActive() || metrics == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Insurance, not the mechanism: the plane already deferred because the content
                // claimed the point. This stops any ancestor added later from intercepting a scrub
                // halfway down the alphabet.
                requestDisallowInterceptTouchEvent();
                mScrubbing = true;
                mActiveIndex = -1;
                mHapticIndex = -1;
                updateScrub(metrics, event.getY(), false);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mScrubbing) return false;
                // Y only. X was spent at the down deciding the stream belongs here, so a finger
                // that drifts onto the grid keeps scrubbing.
                updateScrub(metrics, event.getY(), true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mScrubbing) return false;
                endScrub();
                return true;
            default:
                return mScrubbing;
        }
    }

    private void requestDisallowInterceptTouchEvent() {
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    private void updateScrub(@NonNull AppDrawerRopeMetrics metrics, float y, boolean tick) {
        int index = metrics.indexForY(y, mActiveIndex);
        if (index < 0 || index >= mLetters.length) return;
        if (index == mActiveIndex) return;
        mActiveIndex = index;
        if (tick && mDock != null && mDock.isRowHapticsEnabled()
            && RowHapticTickHelper.isBoundaryCrossing(mHapticIndex, index)) {
            // Per boundary, never per frame: every letter change also auto-scrolls the grid, and a
            // tick on each MOVE would buzz continuously for the length of a scrub.
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        mHapticIndex = index;
        invalidate();
        Callbacks callbacks = mCallbacks;
        if (callbacks != null) callbacks.onScrubLetterChanged(mLetters[index]);
    }

    /** The one exit. Idempotent by the flag, so an UP after a CANCEL reports nothing. */
    private void endScrub() {
        mScrubbing = false;
        mActiveIndex = -1;
        mHapticIndex = -1;
        invalidate();
        Callbacks callbacks = mCallbacks;
        if (callbacks != null) callbacks.onScrubEnded();
    }
}
