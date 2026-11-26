package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The marks row's zoom while a finger is down: a view of its own in the chrome layer, sized once
 * at touch-down to the frame {@link com.termux.app.wall.pageswitch.MarksZoomPlan} gives, so the
 * zoomed marks can stand taller than the row, over the pane's first text line.
 *
 * <p>Its ground is Global's glass as every chrome glass wears it: with Live blur granted, the
 * recording of what is beneath it ({@link MarksZoomBackdrop}'s layered Live picture, held by a picture
 * view cut to the ground's capsule) under Global's tint; otherwise the glass stack itself carries
 * the stored wallpaper frame. The picture is taken at touch-down and given back at the lift
 * ({@link #release}): nothing re-records per move and nothing is kept after.
 *
 * <p>Drawn back to front: the band (the Display page's dark band, which no recording holds), the
 * picture, the glass, then the line's marks and title, which the line draws in its own coordinates.
 * Never touched: the stream stays the line's.
 */
final class MarksZoomView extends FrameLayout {

    /** Draws the line's touch in the line's own coordinates. */
    interface Painter {
        void drawZoom(@NonNull Canvas canvas);
    }

    /** The name the zoom's picture holds its Live slot under in the glass registry. */
    static final String GLASS_ELEMENT = "marks";

    @Nullable private Painter mPainter;
    @Nullable private Drawable mGround;
    private final RectF mGroundRect = new RectF();
    private float mGroundRadius;
    private final Paint mBand = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Where the line's origin lies in this view. */
    private float mLineX;
    private float mLineY;
    @NonNull private final ImageView mPicture;
    @NonNull private final MarksZoomBackdrop mBackdrop;
    private boolean mPictureOpen;

    MarksZoomView(@NonNull Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mPicture = new ImageView(context);
        mPicture.setScaleType(ImageView.ScaleType.FIT_XY);
        mPicture.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mPicture.setClipToOutline(true);
        mPicture.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mGroundRadius);
            }
        });
        addView(mPicture, new LayoutParams(0, 0, Gravity.TOP | Gravity.LEFT));
        mBackdrop = new MarksZoomBackdrop(mPicture);
    }

    /**
     * Places the ground for one touch: its rect and radius in this view's coordinates, the band
     * under it ({@code bandArgb}, 0 for none) and where the line's origin lies. Once per
     * touch-down, before the view joins its layer.
     */
    void place(@NonNull Painter painter, float left, float top, float right, float bottom,
               float radius, int bandArgb, float lineX, float lineY) {
        mPainter = painter;
        mGroundRect.set(left, top, right, bottom);
        mGroundRadius = radius;
        mBand.setColor(bandArgb);
        mLineX = lineX;
        mLineY = lineY;
        LayoutParams params = (LayoutParams) mPicture.getLayoutParams();
        params.width = Math.round(right - left);
        params.height = Math.round(bottom - top);
        params.leftMargin = Math.round(left);
        params.topMargin = Math.round(top);
        mPicture.setLayoutParams(params);
        mPicture.invalidateOutline();
    }

    /**
     * Asks for the Live picture of what is beneath, at {@code blurRadiusPx}, once the view stands
     * in its layer. True when Live is granted and the picture is up; false when the ground is to
     * carry the stored wallpaper frame instead (Live off, no slot, no blur, before Android 12).
     */
    boolean openPicture(float blurRadiusPx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || blurRadiusPx <= 0f
            || !isAttachedToWindow()) {
            return false;
        }
        mPictureOpen = true;
        return mBackdrop.open(blurRadiusPx);
    }

    /** The glass to wear over the picture (or carrying the stored frame); null for none. */
    void setGround(@Nullable Drawable ground) {
        if (mGround == ground) return;
        if (mGround != null) mGround.setCallback(null);
        mGround = ground;
        if (ground != null) {
            ground.setCallback(this);
            ground.setBounds(Math.round(mGroundRect.left), Math.round(mGroundRect.top),
                Math.round(mGroundRect.right), Math.round(mGroundRect.bottom));
        }
        invalidate();
    }

    /** Lets the painter, the glass and the picture go once the touch is over: no pixels kept. */
    void release() {
        mPainter = null;
        setGround(null);
        if (mPictureOpen) {
            mPictureOpen = false;
            mBackdrop.close();
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == mGround || super.verifyDrawable(who);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        Painter painter = mPainter;
        if (painter == null) return;
        if (mBand.getColor() != 0) {
            canvas.drawRoundRect(mGroundRect, mGroundRadius, mGroundRadius, mBand);
        }
        // The Live picture, when one is up.
        super.dispatchDraw(canvas);
        Drawable ground = mGround;
        if (ground != null) ground.draw(canvas);
        int save = canvas.save();
        canvas.translate(mLineX, mLineY);
        painter.drawZoom(canvas);
        canvas.restoreToCount(save);
    }
}
