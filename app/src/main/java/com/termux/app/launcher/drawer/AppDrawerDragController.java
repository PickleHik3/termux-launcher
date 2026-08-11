package com.termux.app.launcher.drawer;

import android.content.ClipData;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.LauncherConfigSnapshot;
import com.termux.app.launcher.model.LauncherAppEntry;

/** Platform drag coordinator. Local state contains stable identity/revision only. */
public final class AppDrawerDragController implements AppDrawerPickupDelegate {
    public interface Host {
        boolean isFrozenPickupEligible(@NonNull String stableId);
        void armTerminalDispatchDragLatch();
        void onDragStateChanged(boolean dragging);
        void onDragLocation(@NonNull View target, float localX, float localY);
        @NonNull AppDrawerViewType frozenSourceViewType();
    }

    private final SuggestionBarView dock;
    private final AppDrawerDragOverlayView overlay;
    private final Host host;
    @Nullable private LocalState active;
    private boolean cleaned = true;

    public AppDrawerDragController(@NonNull SuggestionBarView dock,
                                   @NonNull AppDrawerDragOverlayView overlay,
                                   @NonNull Host host) {
        this.dock = dock;
        this.overlay = overlay;
        this.host = host;
    }

    @Override public boolean startPickup(@NonNull View source, @NonNull LauncherAppEntry entry) {
        if (!host.isFrozenPickupEligible(entry.appRef.stableId())) return false;
        LauncherConfigSnapshot snapshot = dock.getLauncherConfigSnapshot();
        int size = Math.max(1, source.getWidth() > 0 ? Math.min(source.getWidth(), source.getHeight()) : 48);
        Drawable drawable = dock.getRenderedIcon(entry, size);
        if (drawable == null) return false;
        host.armTerminalDispatchDragLatch();
        Rect bounds = new Rect();
        source.getGlobalVisibleRect(bounds);
        overlay.begin(drawable, size, bounds);
        active = new LocalState(entry.appRef.stableId(), snapshot.revision, bounds,
            host.frozenSourceViewType());
        cleaned = false;
        // Public payload is intentionally empty; all identity remains in process-local state.
        ClipData clip = ClipData.newPlainText("launcher-item", "");
        View.DragShadowBuilder shadow = new DrawableShadow(drawable, size);
        boolean started = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            ? source.startDragAndDrop(clip, shadow, active, 0)
            : source.startDrag(clip, shadow, active, 0);
        if (!started) cleanup();
        else host.onDragStateChanged(true);
        return started;
    }

    public void bindTarget(@NonNull View view, @NonNull AppDrawerItem item) {
        view.setOnDragListener((target, event) -> {
            if (event.getLocalState() == active && (event.getAction() == DragEvent.ACTION_DRAG_ENTERED
                || event.getAction() == DragEvent.ACTION_DRAG_LOCATION)) {
                host.onDragLocation(target, event.getX(), event.getY());
                overlay.moveTo(target, event.getX(), event.getY(),
                    item.kind == AppDrawerItem.Kind.FOLDER);
            }
            return handleTarget(event, item);
        });
    }

    private boolean handleTarget(@NonNull DragEvent event, @NonNull AppDrawerItem target) {
        if (event.getLocalState() != active || active == null) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                return true;
            case DragEvent.ACTION_DROP:
                if (active.sourceStableId.equals(target.stableId)) return false;
                LauncherConfigRepository.MutationResult result = target.kind == AppDrawerItem.Kind.FOLDER
                    ? dock.addDrawerAppToFolder(active.revision, target.stableId,
                        active.sourceStableId)
                    : dock.createDrawerFolder(active.revision, target.app,
                        active.sourceStableId);
                active.accepted = result == LauncherConfigRepository.MutationResult.APPLIED;
                if (result == LauncherConfigRepository.MutationResult.CAPACITY) {
                    overlay.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    Toast.makeText(overlay.getContext(), com.termux.R.string.folder_capacity_reached,
                        Toast.LENGTH_SHORT).show();
                }
                return active.accepted;
            case DragEvent.ACTION_DRAG_EXITED:
                overlay.setFolderHover(false);
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                cleanup();
                return true;
            default: return true;
        }
    }

    public boolean isDragging() { return active != null; }

    public void cancel() { cleanup(); }

    private void cleanup() {
        if (cleaned) return;
        cleaned = true;
        LocalState finished = active;
        active = null;
        if (finished == null) overlay.clear();
        else overlay.finish(finished.accepted, finished.frozenSourceBounds);
        host.onDragStateChanged(false);
    }

    private static final class LocalState {
        final String sourceStableId;
        final long revision;
        final Rect frozenSourceBounds;
        final AppDrawerViewType sourceViewType;
        final String sourceSurface = "drawer";
        boolean accepted;
        LocalState(String sourceStableId, long revision, Rect frozenSourceBounds,
                   AppDrawerViewType sourceViewType) {
            this.sourceStableId = sourceStableId;
            this.revision = revision;
            this.frozenSourceBounds = new Rect(frozenSourceBounds);
            this.sourceViewType = sourceViewType;
        }
    }

    private static final class DrawableShadow extends View.DragShadowBuilder {
        final Drawable drawable;
        final int size;
        DrawableShadow(Drawable drawable, int size) { this.drawable = drawable; this.size = size; }
        @Override public void onProvideShadowMetrics(@NonNull Point outSize,
                                                     @NonNull Point outTouch) {
            outSize.set(size, size);
            outTouch.set(size / 2, size / 2);
        }
        @Override public void onDrawShadow(@NonNull Canvas canvas) {
            Rect old = new Rect(drawable.getBounds());
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            drawable.setBounds(old);
        }
    }
}
