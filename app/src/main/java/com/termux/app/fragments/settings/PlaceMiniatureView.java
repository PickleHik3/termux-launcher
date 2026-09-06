package com.termux.app.fragments.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceOrientation;

import java.util.EnumMap;
import java.util.Map;

/**
 * A phone-shaped miniature of one place's resolved {@link PlaceLayout}: coloured blocks for the
 * status bar, the apps row, the alphabets row, the extra keys and the canvas that is left over.
 * Every block is a screen-edge strip claimed off whatever is left of the frame, in the same order
 * the launcher stacks them — status bar first, extra keys next, the apps row, and the alphabets
 * row riding just inside the apps row when it is a bottom row rather than a rail. Tapping a block
 * reports which one, so the settings page can scroll to its row.
 */
public final class PlaceMiniatureView extends View {

    /** One coloured region of the miniature. */
    public enum Block { STATUS_BAR, APPS_ROW, ALPHABETS_ROW, EXTRA_KEYS, CANVAS }

    /** Reports a tapped block; {@code null} when the tap landed outside every block (rare). */
    public interface OnBlockTappedListener {
        void onBlockTapped(@NonNull Block block);
    }

    private static final float STATUS_BAR_FRACTION = 0.12f;
    private static final float ROW_FRACTION = 0.17f;
    private static final float ALPHABETS_FRACTION = 0.09f;
    /** Portrait: narrow and tall; landscape: wide and short — a phone silhouette either way. */
    private static final float PORTRAIT_ASPECT = 9f / 19.5f;
    private static final float LANDSCAPE_ASPECT = 19.5f / 9f;

    @Nullable private PlaceLayout mLayout;
    @NonNull private PlaceOrientation mOrientation = PlaceOrientation.PORTRAIT;
    @Nullable private OnBlockTappedListener mListener;

    private final Paint mFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mFrameRect = new RectF();
    private final android.graphics.Path mClipPath = new android.graphics.Path();
    private final Map<Block, RectF> mBlockRects = new EnumMap<>(Block.class);
    private RectF mRemaining = new RectF();

    public PlaceMiniatureView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mFramePaint.setStyle(Paint.Style.STROKE);
        mFramePaint.setStrokeWidth(dp(2));
        mFramePaint.setColor(themeColor(com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_on_surface_variant));
        mFillPaint.setStyle(Paint.Style.FILL);
    }

    public PlaceMiniatureView(@NonNull Context context) {
        this(context, null);
    }

    /** What to draw. Redraws only when the layout or the orientation actually changed. */
    public void setLayout(@NonNull PlaceLayout layout, @NonNull PlaceOrientation orientation) {
        if (layout.equals(mLayout) && orientation == mOrientation) return;
        mLayout = layout;
        mOrientation = orientation;
        // The blocks are laid out from the frame, and the frame from the view's size; a new
        // arrangement or orientation at the same size never reaches onSizeChanged, so the blocks
        // are recomputed here or the old picture would be drawn again.
        if (getWidth() > 0 && getHeight() > 0) layoutFrame(getWidth(), getHeight());
        requestLayout();
        invalidate();
    }

    /** The rectangle a block is drawn in, in view pixels, or null while it is not on the picture. */
    @Nullable
    @androidx.annotation.VisibleForTesting
    public RectF blockRect(@NonNull Block block) {
        RectF rect = mBlockRects.get(block);
        return rect == null ? null : new RectF(rect);
    }

    public void setOnBlockTappedListener(@Nullable OnBlockTappedListener listener) {
        mListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            height = Math.round(dp(140));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutFrame(w, h);
    }

    private void layoutFrame(int viewWidth, int viewHeight) {
        float pad = dp(8);
        float availableWidth = Math.max(0f, viewWidth - 2 * pad);
        float availableHeight = Math.max(0f, viewHeight - 2 * pad);
        float aspect = mOrientation == PlaceOrientation.LANDSCAPE
            ? LANDSCAPE_ASPECT : PORTRAIT_ASPECT;
        float frameWidth = availableHeight * aspect;
        float frameHeight = availableHeight;
        if (frameWidth > availableWidth) {
            frameWidth = availableWidth;
            frameHeight = availableWidth / aspect;
        }
        float left = (viewWidth - frameWidth) / 2f;
        float top = (viewHeight - frameHeight) / 2f;
        mFrameRect.set(left, top, left + frameWidth, top + frameHeight);
        computeBlocks();
    }

    private void computeBlocks() {
        mBlockRects.clear();
        mRemaining = new RectF(mFrameRect);
        if (mLayout == null) return;
        takeEdgeStrip(Block.STATUS_BAR, mLayout.statusBarEdge, STATUS_BAR_FRACTION);
        if (mLayout.extraKeys != RowPlacement.HIDDEN) {
            takeEdgeStrip(Block.EXTRA_KEYS, edgeOf(mLayout.extraKeys), ROW_FRACTION);
        }
        if (mLayout.appsRow != RowPlacement.HIDDEN) {
            takeEdgeStrip(Block.APPS_ROW, edgeOf(mLayout.appsRow), ROW_FRACTION);
        }
        if (mLayout.appsRow == RowPlacement.BOTTOM && mLayout.azRowShown) {
            takeEdgeStrip(Block.ALPHABETS_ROW, Edge.BOTTOM, ALPHABETS_FRACTION);
        }
        mBlockRects.put(Block.CANVAS, new RectF(mRemaining));
    }

    @NonNull
    private static Edge edgeOf(@NonNull RowPlacement placement) {
        if (placement == RowPlacement.LEFT) return Edge.LEFT;
        if (placement == RowPlacement.RIGHT) return Edge.RIGHT;
        return Edge.BOTTOM;
    }

    /** Claims a strip off the current remaining rect for one edge, shrinking it in place. */
    private void takeEdgeStrip(@NonNull Block block, @NonNull Edge edge, float fraction) {
        RectF rect = new RectF(mRemaining);
        switch (edge) {
            case TOP: {
                float h = mRemaining.height() * fraction;
                rect.bottom = mRemaining.top + h;
                mRemaining.top += h;
                break;
            }
            case BOTTOM: {
                float h = mRemaining.height() * fraction;
                rect.top = mRemaining.bottom - h;
                mRemaining.bottom -= h;
                break;
            }
            case LEFT: {
                float w = mRemaining.width() * fraction;
                rect.right = mRemaining.left + w;
                mRemaining.left += w;
                break;
            }
            case RIGHT: {
                float w = mRemaining.width() * fraction;
                rect.left = mRemaining.right - w;
                mRemaining.right -= w;
                break;
            }
        }
        mBlockRects.put(block, rect);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mLayout == null || mFrameRect.isEmpty()) return;
        float radius = dp(10);
        mFillPaint.setColor(themeColor(com.termux.shared.R.attr.termuxColorSurfacePanel,
            R.color.termux_on_surface_variant));
        canvas.drawRoundRect(mFrameRect, radius, radius, mFillPaint);

        // The blocks are clipped to the frame's rounded outline, so a strip that reaches a corner
        // follows the curve instead of poking a square corner past it.
        mClipPath.reset();
        mClipPath.addRoundRect(mFrameRect, radius, radius, android.graphics.Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(mClipPath);
        drawBlock(canvas, Block.CANVAS, com.termux.shared.R.attr.termuxColorSurfacePanelHigh);
        drawBlock(canvas, Block.EXTRA_KEYS, com.termux.shared.R.attr.termuxColorTertiaryContainer);
        drawBlock(canvas, Block.APPS_ROW, com.termux.shared.R.attr.termuxColorSecondary);
        drawBlock(canvas, Block.ALPHABETS_ROW, com.termux.shared.R.attr.termuxColorAccentContainer);
        drawBlock(canvas, Block.STATUS_BAR, com.termux.shared.R.attr.termuxColorPrimary);
        canvas.restoreToCount(saved);

        canvas.drawRoundRect(mFrameRect, radius, radius, mFramePaint);
    }

    private void drawBlock(@NonNull Canvas canvas, @NonNull Block block, @AttrRes int colorAttr) {
        RectF rect = mBlockRects.get(block);
        if (rect == null || rect.isEmpty()) return;
        mFillPaint.setColor(themeColor(colorAttr, R.color.termux_on_surface_variant));
        canvas.drawRect(rect, mFillPaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            Block tapped = blockAt(event.getX(), event.getY());
            if (tapped != null && mListener != null) mListener.onBlockTapped(tapped);
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Nullable
    private Block blockAt(float x, float y) {
        // Smaller, more specific blocks first, so a corner where two strips meet resolves to the
        // narrower one (the alphabets row rides a thin strip inside the wider apps row).
        Block[] order = {Block.ALPHABETS_ROW, Block.STATUS_BAR, Block.EXTRA_KEYS,
            Block.APPS_ROW, Block.CANVAS};
        for (Block block : order) {
            RectF rect = mBlockRects.get(block);
            if (rect != null && rect.contains(x, y)) return block;
        }
        return null;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @ColorInt
    private int themeColor(@AttrRes int attr, int fallbackColorRes) {
        return MaterialColors.getColor(this, attr,
            ContextCompat.getColor(getContext(), fallbackColorRes));
    }
}
