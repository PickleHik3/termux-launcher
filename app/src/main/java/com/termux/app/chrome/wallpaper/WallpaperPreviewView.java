package com.termux.app.chrome.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.ShapeTokens;

/**
 * One slot's preview card on the wallpaper picker page: the photo at the card's size. The Lock
 * card also carries the generic lock-screen cutout ({@code lock_preview_overlay}, a 360x800
 * viewport) and the time composed from the {@code lock_digit_*} glyphs in its clock area.
 *
 * <p>The card's content never mirrors in RTL: it is a picture of a phone screen. Its corners are
 * the theme's Large shape.</p>
 */
public final class WallpaperPreviewView extends View {

    /** The overlay's viewport, and where its clock sits in it (lock-wallpaper README). */
    static final float OVERLAY_W = 360f;
    static final float OVERLAY_H = 800f;
    static final float CLOCK_X = 28f;
    static final float CLOCK_TOP = 132f;
    static final float DIGIT_W = 56f;
    static final float COLON_W = 20f;
    static final float GLYPH_H = 96f;
    static final float GLYPH_GAP = 2f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect mSrc = new Rect();
    private final RectF mDst = new RectF();
    private final float mCornerPx;
    private final int mFillColor;
    private final int mGlyphColor;

    @Nullable private Bitmap mStill;
    private boolean mPhoto;
    private boolean mLock;
    @NonNull private String mClock = "";

    @Nullable private Drawable mOverlay;
    @Nullable private Drawable[] mDigits;
    @Nullable private Drawable mColon;
    @Nullable private Drawable mPhotoGlyph;

    public WallpaperPreviewView(@NonNull Context context) {
        super(context);
        mCornerPx = ShapeTokens.cornerPx(context, ShapeTokens.Corner.LARGE.attr,
            ShapeTokens.Corner.LARGE.defaultDp);
        mFillColor = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHighest, 0);
        mGlyphColor = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, 0);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mCornerPx);
            }
        });
        setClipToOutline(true);
    }

    /** What the card shows: a photo (with its picture, if it has loaded) or nothing. */
    public void show(@Nullable Bitmap still, boolean photo) {
        mStill = still;
        mPhoto = photo;
        invalidate();
    }

    /** The still (or the photo's picture) arrived for what is shown now. */
    public void setStill(@Nullable Bitmap still) {
        mStill = still;
        invalidate();
    }

    /** The picture drawn: the photo; null while it loads. */
    @Nullable
    public Bitmap still() {
        return mStill;
    }

    /** Whether the card shows a photo (with or without its picture yet). */
    public boolean showsPhoto() {
        return mPhoto;
    }

    /** The Lock card: the cutout and the composed clock over the background. */
    public void setLock(boolean lock) {
        if (mLock == lock) return;
        mLock = lock;
        invalidate();
    }

    public boolean isLock() {
        return mLock;
    }

    /** The clock's glyphs, from {@link WallpaperPickerLogic#composedTime}. */
    public void setClock(@NonNull String clock) {
        if (clock.equals(mClock)) return;
        mClock = clock;
        if (mLock) invalidate();
    }

    @NonNull
    public String clock() {
        return mClock;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        mPaint.setShader(null);
        if (mStill != null && !mStill.isRecycled()) {
            centreCrop(mStill.getWidth(), mStill.getHeight(), w, h);
            canvas.drawBitmap(mStill, mSrc, mDst, mPaint);
        } else {
            canvas.drawColor(mFillColor);
            if (mPhoto) drawPhotoGlyph(canvas, w, h);
        }
        if (mLock) drawLockCutout(canvas, w, h);
    }

    private void centreCrop(int bw, int bh, int w, int h) {
        float scale = Math.max(w / (float) bw, h / (float) bh);
        int sw = Math.round(w / scale);
        int sh = Math.round(h / scale);
        int sx = (bw - sw) / 2;
        int sy = (bh - sh) / 2;
        mSrc.set(sx, sy, sx + sw, sy + sh);
        mDst.set(0f, 0f, w, h);
    }

    private void drawPhotoGlyph(@NonNull Canvas canvas, int w, int h) {
        if (mPhotoGlyph == null) {
            Drawable d = AppCompatResources.getDrawable(getContext(), R.drawable.ic_symbol_photo);
            if (d == null) return;
            mPhotoGlyph = d.mutate();
            mPhotoGlyph.setTint(mGlyphColor);
        }
        int size = mPhotoGlyph.getIntrinsicWidth();
        int left = (w - size) / 2;
        int top = (h - size) / 2;
        mPhotoGlyph.setBounds(left, top, left + size, top + size);
        mPhotoGlyph.draw(canvas);
    }

    private void drawLockCutout(@NonNull Canvas canvas, int w, int h) {
        loadLockGlyphs();
        if (mOverlay != null) {
            mOverlay.setBounds(0, 0, w, h);
            mOverlay.draw(canvas);
        }
        float sx = w / OVERLAY_W;
        float sy = h / OVERLAY_H;
        float x = CLOCK_X;
        int top = Math.round(CLOCK_TOP * sy);
        int bottom = Math.round((CLOCK_TOP + GLYPH_H) * sy);
        for (int i = 0; i < mClock.length(); i++) {
            char c = mClock.charAt(i);
            Drawable glyph;
            float gw;
            if (c == ':') {
                glyph = mColon;
                gw = COLON_W;
            } else if (c >= '0' && c <= '9' && mDigits != null) {
                glyph = mDigits[c - '0'];
                gw = DIGIT_W;
            } else {
                continue;
            }
            if (glyph != null) {
                glyph.setBounds(Math.round(x * sx), top, Math.round((x + gw) * sx), bottom);
                glyph.draw(canvas);
            }
            x += gw + GLYPH_GAP;
        }
    }

    private void loadLockGlyphs() {
        if (mOverlay != null) return;
        Context ctx = getContext();
        mOverlay = glyph(ctx, R.drawable.lock_preview_overlay);
        if (mOverlay == null) mOverlay = new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
        mColon = glyph(ctx, R.drawable.lock_digit_colon);
        int[] ids = {R.drawable.lock_digit_0, R.drawable.lock_digit_1, R.drawable.lock_digit_2,
            R.drawable.lock_digit_3, R.drawable.lock_digit_4, R.drawable.lock_digit_5,
            R.drawable.lock_digit_6, R.drawable.lock_digit_7, R.drawable.lock_digit_8,
            R.drawable.lock_digit_9};
        mDigits = new Drawable[ids.length];
        for (int i = 0; i < ids.length; i++) mDigits[i] = glyph(ctx, ids[i]);
    }

    /** A cutout glyph, or null: a glyph that will not inflate costs the clock a digit, not the app. */
    @Nullable
    private static Drawable glyph(@NonNull Context ctx, int id) {
        try {
            return AppCompatResources.getDrawable(ctx, id);
        } catch (RuntimeException e) {
            com.termux.shared.logger.Logger.logWarn("WallpaperPreviewView",
                "Lock cutout glyph failed to load: " + e.getMessage());
            return null;
        }
    }
}
