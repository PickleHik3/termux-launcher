package com.termux.app.surfaces;

import android.graphics.Outline;
import android.os.Build;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;

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
 */
final class AppearanceEditorFrame {

    /** How far the launcher is scaled at most; smaller only when the bottom area needs the room. */
    static final float PREFERRED_SCALE = 0.76f;
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
        if (containerHeightPx <= 0)
            return PREFERRED_SCALE;
        float fit = (frameBottomPx - frameTopPx) / (float) containerHeightPx;
        return Math.max(MIN_SCALE, Math.min(PREFERRED_SCALE, fit));
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
     * Scales the root to {@code scale}, its top edge moved down by {@code translationYPx}, and clips
     * it to the device's corners. Called again on a rotation or a resize with the new numbers.
     */
    void show(float scale, float translationYPx, boolean animate) {
        mCornerRadiusPx = deviceCornerRadiusPx(mRoot);
        if (!mClipped) {
            mSavedProvider = mRoot.getOutlineProvider();
            mSavedClipToOutline = mRoot.getClipToOutline();
            mRoot.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    // In the view's own coordinates: the scale shrinks the arc with the frame, so
                    // the frame reads as a small copy of the phone rather than a card.
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mCornerRadiusPx);
                }
            });
            mRoot.setClipToOutline(true);
            mClipped = true;
        } else {
            mRoot.invalidateOutline();
        }
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
