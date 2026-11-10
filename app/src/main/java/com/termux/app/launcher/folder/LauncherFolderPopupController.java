package com.termux.app.launcher.folder;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Shared dock/drawer popup owner with non-focusable flags. The panel grows out of its anchor's
 * icon and shrinks back into it ({@link FolderExpandMotion}); nothing dims behind it.
 */
public final class LauncherFolderPopupController {
    /**
     * Decides whether a touch outside the popup leaves it open. The popup is outside-touchable, so
     * Android dismisses it on the first touch anywhere else, including the in-app keyboard's keys
     * a rename is typed with: the press dismissed the popup, the dismissal cancelled the rename,
     * and the key then went to the terminal.
     */
    public interface OutsideTouchGuard {
        /** True when a touch at this screen point must not close the popup. */
        boolean keepsOpen(float rawX, float rawY);
    }

    @Nullable private PopupWindow popup;
    @Nullable private String folderId;
    @Nullable private Runnable onDismiss;
    @Nullable private FolderExpandMotion motion;
    @Nullable private View preDrawRoot;
    @Nullable private ViewTreeObserver.OnPreDrawListener preDraw;
    private boolean hiddenForDrag;
    @Nullable private OutsideTouchGuard outsideTouchGuard;

    /** The guard every popup shown from now on consults on an outside touch; null for none. */
    public void setOutsideTouchGuard(@Nullable OutsideTouchGuard guard) {
        outsideTouchGuard = guard;
    }

    public void show(@NonNull PopupWindow next, @NonNull String id,
                     @NonNull Runnable positionAndShow, @Nullable Runnable dismissListener) {
        show(next, id, null, positionAndShow, dismissListener);
    }

    /**
     * Shows {@code next} (placed by {@code positionAndShow}) and grows it out of {@code anchor},
     * the folder's icon; a null or detached anchor gives the small in-place settle instead.
     */
    public void show(@NonNull PopupWindow next, @NonNull String id, @Nullable View anchor,
                     @NonNull Runnable positionAndShow, @Nullable Runnable dismissListener) {
        dismissImmediate();
        popup = next;
        folderId = id;
        hiddenForDrag = false;
        onDismiss = dismissListener;
        next.setFocusable(false);
        next.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        next.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        next.setOnDismissListener(this::finishDismiss);
        next.setTouchInterceptor((view, event) -> keepsOpenOn(event));
        positionAndShow.run();
        View root = next.getContentView();
        if (root == null) return;
        // Hidden until the window has its first frame: only then is the panel's rest rect known.
        root.setAlpha(0f);
        FolderExpandMotion expand = new FolderExpandMotion(root);
        motion = expand;
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            removePreDraw();
            if (motion == expand && !hiddenForDrag) startOpen(expand, root, anchor);
            return true;
        };
        preDrawRoot = root;
        preDraw = listener;
        root.getViewTreeObserver().addOnPreDrawListener(listener);
    }

    /** The panel's and the icon's screen rects, once, then the grow. */
    private void startOpen(@NonNull FolderExpandMotion expand, @NonNull View root,
                           @Nullable View anchor) {
        int[] at = new int[2];
        if (anchor != null && anchor.isAttachedToWindow() && anchor.getWidth() > 0
            && anchor.getHeight() > 0) {
            int[] iconAt = new int[2];
            anchor.getLocationOnScreen(iconAt);
            root.getLocationOnScreen(at);
            expand.setGeometry(iconAt[0], iconAt[1], iconAt[0] + anchor.getWidth(),
                iconAt[1] + anchor.getHeight(), at[0], at[1], at[0] + root.getWidth(),
                at[1] + root.getHeight());
        } else {
            expand.setFallbackGeometry();
        }
        expand.open();
    }

    private void removePreDraw() {
        View root = preDrawRoot;
        ViewTreeObserver.OnPreDrawListener listener = preDraw;
        preDrawRoot = null;
        preDraw = null;
        if (root == null || listener == null) return;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
    }

    /**
     * Whether {@code event} is an outside touch the guard keeps the popup open for. Consuming it
     * only stops the dismissal: the popup is not touch-modal, so the touch itself still lands on
     * the window below.
     */
    @VisibleForTesting
    boolean keepsOpenOn(@NonNull MotionEvent event) {
        OutsideTouchGuard guard = outsideTouchGuard;
        return event.getActionMasked() == MotionEvent.ACTION_OUTSIDE && guard != null
            && guard.keepsOpen(event.getRawX(), event.getRawY());
    }

    public boolean isShowing() { return popup != null && popup.isShowing(); }
    @Nullable public String folderId() { return folderId; }

    public void dismiss() {
        PopupWindow current = popup;
        if (current == null) return;
        FolderExpandMotion expand = motion;
        if (hiddenForDrag || expand == null) {
            current.dismiss();
            return;
        }
        removePreDraw();
        expand.close(current::dismiss);
    }

    public void dismissDeleted(@NonNull String id) {
        if (id.equals(folderId)) dismiss();
    }

    /**
     * Mutes the popup's visuals and touchability but keeps the window alive: a system drag started
     * from a view inside this popup must not lose its source window mid-drag. The owner dismisses
     * for real (via {@link #dismissImmediate()}) when the drag ends.
     */
    public void hideForDrag() {
        PopupWindow current = popup;
        View root = current == null ? null : current.getContentView();
        if (root == null) return;
        hiddenForDrag = true;
        removePreDraw();
        if (motion != null) motion.cancel();
        root.setAlpha(0f);
        current.setTouchable(false);
        current.update();
    }

    public void dismissImmediate() {
        PopupWindow current = popup;
        if (current == null) return;
        popup = null;
        if (current.isShowing()) current.dismiss();
        else finishDismiss();
    }

    private void finishDismiss() {
        popup = null;
        folderId = null;
        hiddenForDrag = false;
        removePreDraw();
        FolderExpandMotion expand = motion;
        motion = null;
        if (expand != null) expand.cancel();
        Runnable listener = onDismiss;
        onDismiss = null;
        if (listener != null) listener.run();
    }
}
