package com.termux.app.launcher.folder;

import android.view.Choreographer;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.Spring;

/** Shared dock/drawer popup owner with non-focusable flags and house-spring transitions. */
public final class LauncherFolderPopupController implements Choreographer.FrameCallback {
    /** Backdrop dim at full open; separates the folder from the drawer/dock behind it. */
    private static final float DIM_AMOUNT = 0.32f;

    private final Spring spring = new Spring(0f, 420f, 41f);
    @Nullable private PopupWindow popup;
    @Nullable private String folderId;
    @Nullable private Runnable onDismiss;
    private boolean closing;
    private boolean framePosted;
    private long lastFrame;

    public void show(@NonNull PopupWindow next, @NonNull String id,
                     @NonNull Runnable positionAndShow, @Nullable Runnable dismissListener) {
        dismissImmediate();
        popup = next;
        folderId = id;
        onDismiss = dismissListener;
        next.setFocusable(false);
        next.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        next.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        next.setOnDismissListener(this::finishDismiss);
        positionAndShow.run();
        closing = false;
        spring.reset(0f);
        spring.target = 1f;
        apply(0f);
        postFrame();
    }

    public boolean isShowing() { return popup != null && popup.isShowing(); }
    @Nullable public String folderId() { return folderId; }

    public void dismiss() {
        if (popup == null) return;
        closing = true;
        spring.reset(Math.max(0f, Math.min(1f, spring.value)));
        spring.target = 0f;
        postFrame();
    }

    public void dismissDeleted(@NonNull String id) {
        if (id.equals(folderId)) dismiss();
    }

    public void dismissImmediate() {
        PopupWindow current = popup;
        if (current == null) return;
        popup = null;
        if (current.isShowing()) current.dismiss();
        else finishDismiss();
    }

    @Override public void doFrame(long frameTimeNanos) {
        framePosted = false;
        float dt = lastFrame == 0L ? Spring.MIN_DT
            : (frameTimeNanos - lastFrame) / 1_000_000_000f;
        lastFrame = frameTimeNanos;
        boolean moving = spring.tick(false, Spring.clampDelta(dt));
        apply(spring.value);
        if (closing && spring.value <= 0.002f) {
            PopupWindow current = popup;
            if (current != null) current.dismiss();
            return;
        }
        if (moving) postFrame();
    }

    private void apply(float value) {
        PopupWindow current = popup;
        View root = current == null ? null : current.getContentView();
        if (root == null) return;
        float p = Math.max(0f, Math.min(1f, value));
        root.setAlpha(p);
        root.setScaleX(0.94f + 0.06f * p);
        root.setScaleY(0.94f + 0.06f * p);
        applyDimBehind(root, p);
    }

    /**
     * Rides the popup's own window: FLAG_DIM_BEHIND on its decor dims everything below —
     * drawer, dock, wallpaper — without touching any activity view, and the spring drives the
     * amount so the dim breathes in and out with the popup itself.
     */
    private void applyDimBehind(@NonNull View root, float p) {
        if (!(root.getParent() instanceof View)) return;
        View decor = (View) root.getParent();
        if (!(decor.getLayoutParams() instanceof WindowManager.LayoutParams)) return;
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) decor.getLayoutParams();
        float amount = DIM_AMOUNT * p;
        if ((params.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0
            && Math.abs(params.dimAmount - amount) < 0.004f) return;
        params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.dimAmount = amount;
        WindowManager manager = (WindowManager)
            decor.getContext().getSystemService(android.content.Context.WINDOW_SERVICE);
        if (manager == null) return;
        try {
            manager.updateViewLayout(decor, params);
        } catch (RuntimeException ignored) {
            // The decor can detach mid-frame during dismiss; the dim dies with the window anyway.
        }
    }

    private void postFrame() {
        if (framePosted) return;
        framePosted = true;
        lastFrame = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void finishDismiss() {
        popup = null;
        folderId = null;
        closing = false;
        framePosted = false;
        Runnable listener = onDismiss;
        onDismiss = null;
        if (listener != null) listener.run();
    }
}
