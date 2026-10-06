package com.termux.app.surfaces;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.Motion;

/**
 * The editor's frame (appearance-layout-editor SPEC §3.1): {@code terminal_root_container} — which
 * holds everything, the wallpaper backdrop included — scaled down about a top-centre pivot and
 * clipped at the device's own corner radius, so wallpaper, glass, frost, refraction and keyboard
 * scale together and the frame is a real render, not a mock.
 *
 * <p>Touch keeps working inside the frame without any help: hit testing honours a view's
 * transform, so the editor's tap targets, laid out in the container's own unscaled coordinates,
 * still land on what they cover. The chrome sampler maps its rects back into root space
 * ({@code ChromeInk#mapIntoRoot}), so the veils stay right under the scale.</p>
 *
 * <p>In wallpaper passthrough mode the wallpaper is not the launcher's to scale: the system draws
 * it behind the translucent window, and the scaled launcher would sit as a cut-out over an
 * unscaled picture. For the editor's lifetime the frame then paints the wallpaper itself, at the
 * decor's rect inside the container ({@link #showWallpaper}), so it scales with everything else.
 * What shows around the scaled frame is the Appearance surface's colorSurface scrim in the content
 * view, never a swap of the window's background.</p>
 *
 * <p><b>Wallpaper display contract.</b> The frame never replaces {@code wallpaper_backdrop}: in
 * self-drawn mode the real backdrop view is part of the container and scales, clips and glasses
 * with it, and no editor wallpaper is loaded. {@link #showWallpaper} is for passthrough mode only,
 * where the backdrop is hidden and the system draws the wallpaper.</p>
 */
final class AppearanceEditorFrame {

    /** How far the launcher is scaled at most; smaller only when the bottom area needs the room. */
    static final float PREFERRED_SCALE = 0.76f;
    /** The most the frame grows to where the sheet under it is short (a Look stop). */
    static final float MAX_SCALE = 0.86f;
    /** Never smaller than this, however short the window: past it the frame is unreadable. */
    static final float MIN_SCALE = 0.5f;
    /** The radius used where the platform cannot say what the display's corners are. */
    static final float FALLBACK_CORNER_DP = 28f;
    static final long ENTER_MS = 260L;
    static final long EXIT_MS = 220L;

    @NonNull private final View mRoot;
    private float mCornerRadiusPx;
    private boolean mClipped;
    @Nullable private ViewOutlineProvider mSavedProvider;
    private boolean mSavedClipToOutline;
    /** How far the display reaches past the container on each side, px; 0 where unknown. */
    private int mInsetLeft;
    private int mInsetTop;
    private int mInsetRight;
    private int mInsetBottom;

    AppearanceEditorFrame(@NonNull View root) {
        mRoot = root;
    }

    @NonNull
    View root() {
        return mRoot;
    }

    /**
     * The scale that fits the frame between its top and the bottom area: the preferred 0.76, or
     * less where the window is too short for that and the bottom area both.
     *
     * @param containerHeightPx the container's unscaled height
     * @param frameTopPx        where the frame's top edge stands, in the container's parent
     * @param frameBottomPx     how far down the frame may reach, in the same space
     */
    static float fitScale(int containerHeightPx, int frameTopPx, int frameBottomPx) {
        return fitScale(containerHeightPx, frameTopPx, frameBottomPx, PREFERRED_SCALE);
    }

    /** {@link #fitScale(int, int, int)} with the cap given: {@link #MAX_SCALE} for a short sheet. */
    static float fitScale(int containerHeightPx, int frameTopPx, int frameBottomPx, float cap) {
        if (containerHeightPx <= 0)
            return Math.min(cap, PREFERRED_SCALE);
        float fit = (frameBottomPx - frameTopPx) / (float) containerHeightPx;
        return Math.max(MIN_SCALE, Math.min(cap, fit));
    }

    /**
     * Puts the root at {@code scale} and {@code translationYPx} now, on the frame's pivot, without
     * touching its animations or clip: one tick of an animation the caller drives.
     */
    void setPose(float scale, float translationYPx) {
        mRoot.setPivotX(mRoot.getWidth() / 2f);
        mRoot.setPivotY(0f);
        mRoot.setScaleX(scale);
        mRoot.setScaleY(scale);
        mRoot.setTranslationY(translationYPx);
    }

    /** The display's own corner radius, in px: the platform's on API 31+, 28dp below. */
    static float deviceCornerRadiusPx(@NonNull View view) {
        float fallback = FALLBACK_CORNER_DP * view.getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return fallback;
        WindowInsets insets = view.getRootWindowInsets();
        if (insets == null)
            return fallback;
        RoundedCorner corner = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
        if (corner == null || corner.getRadius() <= 0)
            return fallback;
        return corner.getRadius();
    }

    /**
     * The display's rect in the container's own coordinates, {left, top, right, bottom}: the
     * container sits below the status bar and above the navigation bar, but the device's corners
     * are the display's, so the clip's round rect is extended by the insets. An inset of 0 gives
     * the container's own rect.
     */
    @NonNull
    static float[] outlineRect(int width, int height, int insetLeft, int insetTop, int insetRight,
                               int insetBottom) {
        return new float[] {-Math.max(0, insetLeft), -Math.max(0, insetTop),
            width + Math.max(0, insetRight), height + Math.max(0, insetBottom)};
    }

    /**
     * The rounding the container's own corner shows when the display's arc of {@code radiusPx}
     * is clipped at a corner standing {@code insetX} and {@code insetY} inside the display's: 0
     * where that corner lies within the arc (nothing is cut), else the radius less the larger
     * inset. The layout canvas's ring follows this, so it matches what the clip leaves.
     */
    static float visibleCornerRadiusPx(float radiusPx, int insetX, int insetY) {
        float x = Math.max(0, insetX);
        float y = Math.max(0, insetY);
        if (x >= radiusPx || y >= radiusPx)
            return 0f;
        float dx = radiusPx - x;
        float dy = radiusPx - y;
        if (dx * dx + dy * dy <= radiusPx * radiusPx)
            return 0f;
        return Math.max(0f, radiusPx - Math.max(x, y));
    }

    /** Where the display's edges stand around the container; see {@link #outlineRect}. */
    void setDisplayInsets(int left, int top, int right, int bottom) {
        mInsetLeft = Math.max(0, left);
        mInsetTop = Math.max(0, top);
        mInsetRight = Math.max(0, right);
        mInsetBottom = Math.max(0, bottom);
        if (mClipped)
            mRoot.invalidateOutline();
    }

    /**
     * Scales the root to {@code scale}, its top edge moved down by {@code translationYPx}, and clips
     * it to the device's corners. Called again on a rotation or a resize with the new numbers.
     */
    void show(float scale, float translationYPx, boolean animate) {
        ensureClipped();
        mRoot.setPivotX(mRoot.getWidth() / 2f);
        mRoot.setPivotY(0f);
        mRoot.animate().cancel();
        if (!animate) {
            mRoot.setScaleX(scale);
            mRoot.setScaleY(scale);
            mRoot.setTranslationY(translationYPx);
            return;
        }
        mRoot.animate().scaleX(scale).scaleY(scale).translationY(translationYPx)
            .setDuration(ENTER_MS).setInterpolator(Motion.settle()).start();
    }

    /**
     * Puts the root at {@code scale} and {@code translationYPx} at once, clipped, without
     * animating: where the hop from the Overview starts, at the Home card's rect, so the next
     * {@link #show} travels from there.
     */
    void prime(float scale, float translationYPx) {
        ensureClipped();
        mRoot.setPivotX(mRoot.getWidth() / 2f);
        mRoot.setPivotY(0f);
        mRoot.animate().cancel();
        mRoot.setScaleX(scale);
        mRoot.setScaleY(scale);
        mRoot.setTranslationY(translationYPx);
    }

    /**
     * Travels to {@code scale} and {@code translationYPx} and stays there, clipped: the way back
     * to the Overview's Home card, which covers the launcher by the time it arrives.
     */
    void moveTo(float scale, float translationYPx, @Nullable Runnable onEnd) {
        ensureClipped();
        mRoot.animate().cancel();
        mRoot.setPivotX(mRoot.getWidth() / 2f);
        mRoot.setPivotY(0f);
        if (!isScaled() && scale == 1f && translationYPx == 0f) {
            if (onEnd != null) onEnd.run();
            return;
        }
        mRoot.animate().scaleX(scale).scaleY(scale).translationY(translationYPx)
            .setDuration(EXIT_MS).setInterpolator(Motion.settle())
            .withEndAction(() -> {
                if (onEnd != null) onEnd.run();
            }).start();
    }

    private void ensureClipped() {
        mCornerRadiusPx = deviceCornerRadiusPx(mRoot);
        if (!mClipped) {
            mSavedProvider = mRoot.getOutlineProvider();
            mSavedClipToOutline = mRoot.getClipToOutline();
            mRoot.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    // In the view's own coordinates: the scale shrinks the arc with the frame, so
                    // the frame reads as a small copy of the phone rather than a card.
                    // The display's rect, not the container's: the container starts below the
                    // status bar, and the corners belong to the glass.
                    float[] rect = outlineRect(view.getWidth(), view.getHeight(), mInsetLeft,
                        mInsetTop, mInsetRight, mInsetBottom);
                    outline.setRoundRect(Math.round(rect[0]), Math.round(rect[1]),
                        Math.round(rect[2]), Math.round(rect[3]), mCornerRadiusPx);
                }
            });
            mRoot.setClipToOutline(true);
            mClipped = true;
        } else {
            mRoot.invalidateOutline();
        }
    }

    /** Back to full size, and the clip taken off once it is there. */
    void hide(boolean animate, @Nullable Runnable onEnd) {
        mRoot.animate().cancel();
        Runnable finish = () -> {
            mRoot.setScaleX(1f);
            mRoot.setScaleY(1f);
            mRoot.setTranslationY(0f);
            if (mClipped) {
                mRoot.setClipToOutline(mSavedClipToOutline);
                mRoot.setOutlineProvider(mSavedProvider);
                mClipped = false;
            }
            if (onEnd != null) onEnd.run();
        };
        if (!animate || !isScaled()) {
            finish.run();
            return;
        }
        mRoot.animate().scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(EXIT_MS).setInterpolator(Motion.settle())
            .withEndAction(finish).start();
    }

    // -------------------------------------------------------------------- the editor's wallpaper

    @Nullable private EditorWallpaperView mWallpaper;

    /**
     * Paints {@code wallpaper} under everything in the container, centre-cropped to the decor's
     * rect as the system would draw it, with {@code dimColor} over it (the dim the window's root
     * paints over the system wallpaper, which the picture now covers).
     *
     * @param decorLeftInRootPx the decor's left edge in the container's unscaled coordinates
     * @param decorTopInRootPx  its top edge
     */
    void showWallpaper(@NonNull Drawable wallpaper, int dimColor, int decorLeftInRootPx,
                       int decorTopInRootPx, int decorWidthPx, int decorHeightPx) {
        if (!(mRoot instanceof ViewGroup))
            return;
        ViewGroup container = (ViewGroup) mRoot;
        if (mWallpaper == null || mWallpaper.getParent() != container) {
            if (mWallpaper != null && mWallpaper.getParent() instanceof ViewGroup)
                ((ViewGroup) mWallpaper.getParent()).removeView(mWallpaper);
            mWallpaper = new EditorWallpaperView(container.getContext());
            // Under everything, the launcher's own backdrop included (invisible in passthrough).
            container.addView(mWallpaper, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        mWallpaper.set(wallpaper, dimColor, decorLeftInRootPx, decorTopInRootPx, decorWidthPx,
            decorHeightPx);
        mWallpaper.setVisibility(View.VISIBLE);
    }

    boolean isShowingWallpaper() {
        return mWallpaper != null && mWallpaper.getParent() != null;
    }

    /** Takes the editor's wallpaper away; the system's shows through again. */
    void hideWallpaper() {
        EditorWallpaperView view = mWallpaper;
        mWallpaper = null;
        if (view != null && view.getParent() instanceof ViewGroup)
            ((ViewGroup) view.getParent()).removeView(view);
    }

    /**
     * The picture, centre-cropped to the decor's rect (which may reach past the container, under
     * the transparent system bars), and the dim over it. Takes no touches.
     */
    private static final class EditorWallpaperView extends View {
        @Nullable private Drawable mDrawable;
        private int mDim;
        private int mDecorLeft;
        private int mDecorTop;
        private int mDecorWidth;
        private int mDecorHeight;

        EditorWallpaperView(@NonNull Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setClickable(false);
            setFocusable(false);
            setWillNotDraw(false);
        }

        void set(@NonNull Drawable drawable, int dim, int decorLeft, int decorTop,
                 int decorWidth, int decorHeight) {
            mDrawable = drawable;
            mDim = dim;
            mDecorLeft = decorLeft;
            mDecorTop = decorTop;
            mDecorWidth = decorWidth;
            mDecorHeight = decorHeight;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            Drawable drawable = mDrawable;
            if (drawable == null)
                return;
            int width = mDecorWidth > 0 ? mDecorWidth : getWidth();
            int height = mDecorHeight > 0 ? mDecorHeight : getHeight();
            int left = mDecorWidth > 0 ? mDecorLeft : 0;
            int top = mDecorHeight > 0 ? mDecorTop : 0;
            int intrinsicWidth = drawable.getIntrinsicWidth();
            int intrinsicHeight = drawable.getIntrinsicHeight();
            if (intrinsicWidth > 0 && intrinsicHeight > 0 && width > 0 && height > 0) {
                float scale = Math.max(width / (float) intrinsicWidth,
                    height / (float) intrinsicHeight);
                int drawnWidth = Math.round(intrinsicWidth * scale);
                int drawnHeight = Math.round(intrinsicHeight * scale);
                int x = left + (width - drawnWidth) / 2;
                int y = top + (height - drawnHeight) / 2;
                drawable.setBounds(x, y, x + drawnWidth, y + drawnHeight);
            } else {
                drawable.setBounds(left, top, left + width, top + height);
            }
            drawable.draw(canvas);
            if ((mDim >>> 24) != 0)
                canvas.drawColor(mDim);
        }
    }

    /**
     * Marks every view under the container for re-recording. A draw that ran while the root was
     * scaled aimed its glass at the scaled position (the top bars anchor on the framework's
     * transform-inclusive location), and a transform alone never re-records a child, so once the
     * root is back at identity nothing would draw those surfaces again.
     */
    void repaintAll() {
        repaintTree(mRoot);
    }

    private static void repaintTree(@NonNull View view) {
        view.invalidate();
        if (!(view instanceof ViewGroup))
            return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++)
            repaintTree(group.getChildAt(i));
    }

    boolean isScaled() {
        return mRoot.getScaleX() != 1f || mRoot.getScaleY() != 1f || mRoot.getTranslationY() != 0f;
    }

    /**
     * A view's top-left in {@code ancestor}'s own coordinates, transforms of {@code ancestor}
     * itself ignored — the space the editor's tap targets and outline are laid out in, which the
     * frame's scale does not change. {@code getLocationInWindow} would fold the scale in.
     *
     * @return false when {@code view} is not under {@code ancestor}
     */
    static boolean offsetIn(@NonNull View view, @NonNull View ancestor, @NonNull int[] out) {
        float x = 0f;
        float y = 0f;
        View current = view;
        while (current != null && current != ancestor) {
            x += current.getLeft() + current.getTranslationX();
            y += current.getTop() + current.getTranslationY();
            if (!(current.getParent() instanceof View))
                return false;
            View parent = (View) current.getParent();
            x -= parent.getScrollX();
            y -= parent.getScrollY();
            current = parent;
        }
        if (current == null)
            return false;
        out[0] = Math.round(x);
        out[1] = Math.round(y);
        return true;
    }
}
