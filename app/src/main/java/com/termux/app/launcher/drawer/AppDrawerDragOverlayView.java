package com.termux.app.launcher.drawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.Spring;

/** Drawer-plane overlay retaining exactly one cache-produced drawable and no View snapshot. */
public final class AppDrawerDragOverlayView extends View implements Choreographer.FrameCallback {
    @Nullable private Drawable drawable;
    private int sizePx;
    private final Spring x = new Spring(0f, 520f, 45f);
    private final Spring y = new Spring(0f, 520f, 45f);
    private final Spring scale = new Spring(1f, 480f, 43f);
    private final Spring alpha = new Spring(1f, 420f, 41f);
    private boolean framePosted;
    private boolean finishing;
    private long lastFrame;
    private final int[] location = new int[2];

    public AppDrawerDragOverlayView(@NonNull Context context) { super(context); }

    public void setGhost(@Nullable Drawable drawable, int sizePx) {
        this.drawable = drawable;
        this.sizePx = Math.max(0, sizePx);
        if (drawable != null) setVisibility(VISIBLE);
        invalidate();
    }

    public void begin(@NonNull Drawable drawable, int sizePx, @NonNull Rect sourceBounds) {
        setGhost(drawable, sizePx);
        finishing = false;
        float[] center = localCenter(sourceBounds.centerX(), sourceBounds.centerY());
        x.reset(center[0]); y.reset(center[1]);
        scale.reset(0.94f); scale.target = 1f;
        alpha.reset(0f); alpha.target = 1f;
        postFrame();
    }

    public void moveTo(@NonNull View target, float localX, float localY, boolean folderHover) {
        target.getLocationOnScreen(location);
        float[] center = localCenter(location[0] + localX, location[1] + localY);
        x.target = center[0]; y.target = center[1];
        scale.target = folderHover ? 1.10f : 1f;
        postFrame();
    }

    public void setFolderHover(boolean hovered) {
        scale.target = hovered ? 1.10f : 1f;
        postFrame();
    }

    /** Accepted drops morph away; rejected/cancelled drops spring to the frozen source and fade. */
    public void finish(boolean accepted, @NonNull Rect frozenSourceBounds) {
        if (drawable == null) { clear(); return; }
        finishing = true;
        if (!accepted) {
            float[] center = localCenter(frozenSourceBounds.centerX(), frozenSourceBounds.centerY());
            x.target = center[0]; y.target = center[1];
            scale.target = 0.94f;
        } else {
            scale.target = 0.72f;
        }
        alpha.target = 0f;
        postFrame();
    }

    @Nullable public Drawable ghost() { return drawable; }
    public void clear() {
        drawable = null;
        sizePx = 0;
        finishing = false;
        framePosted = false;
        setVisibility(INVISIBLE);
        invalidate();
    }

    @Override public void doFrame(long frameTimeNanos) {
        framePosted = false;
        if (drawable == null) return;
        float dt = lastFrame == 0L ? Spring.MIN_DT
            : Spring.clampDelta((frameTimeNanos - lastFrame) / 1_000_000_000f);
        lastFrame = frameTimeNanos;
        boolean moving = x.tick(false, dt) | y.tick(false, dt)
            | scale.tick(false, dt) | alpha.tick(false, dt);
        invalidate();
        if (finishing && !moving) { clear(); return; }
        if (moving) postFrame();
    }

    private void postFrame() {
        if (framePosted) return;
        framePosted = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private float[] localCenter(float screenX, float screenY) {
        getLocationOnScreen(location);
        return new float[] {screenX - location[0], screenY - location[1]};
    }

    @Override protected void onDraw(Canvas canvas) {
        Drawable ghost = drawable;
        if (ghost == null || sizePx <= 0) return;
        Rect old = new Rect(ghost.getBounds());
        float half = sizePx * scale.value / 2f;
        int left = Math.round(x.value - half);
        int top = Math.round(y.value - half);
        int side = Math.max(1, Math.round(sizePx * scale.value));
        int oldAlpha = ghost.getAlpha();
        ghost.setAlpha(Math.max(0, Math.min(255, Math.round(255f * alpha.value))));
        ghost.setBounds(left, top, left + side, top + side);
        ghost.draw(canvas);
        ghost.setBounds(old);
        ghost.setAlpha(oldAlpha);
    }
}
