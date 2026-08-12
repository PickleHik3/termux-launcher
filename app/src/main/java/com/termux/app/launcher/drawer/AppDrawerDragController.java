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
        boolean claimPickupContext(@NonNull String stableId);
        boolean claimPickupDrag(@NonNull String stableId);
        void armTerminalDispatchDragLatch();
        void onDragStateChanged(boolean dragging);
        void onDragLocation(@NonNull View target, float localX, float localY);
        void onDragTargetExited();
        void onAcceptedDrop();
        boolean canDropOnCurrentTarget();
        @Nullable AppDrawerItem resolveCurrentDropTarget(@NonNull String stableId);
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

    @Override public boolean claimContext(@NonNull View source, @NonNull LauncherAppEntry entry) {
        return host.claimPickupContext(entry.appRef.stableId());
    }

    @Override public boolean startPickup(@NonNull View source, @NonNull LauncherAppEntry entry) {
        if (!host.isFrozenPickupEligible(entry.appRef.stableId())) return false;
        if (!host.claimPickupDrag(entry.appRef.stableId())) return false;
        LauncherConfigSnapshot snapshot = dock.getLauncherConfigSnapshot();
        PickupArtwork artwork = pickupArtwork(source);
        if (artwork == null) return false;
        int size = artwork.sizePx;
        Drawable drawable = artwork.drawable;
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

    @Nullable
    static PickupArtwork pickupArtwork(@NonNull View source) {
        if (!(source instanceof AppDrawerAppCellView)) return null;
        AppDrawerAppCellView cell = (AppDrawerAppCellView) source;
        Drawable drawable = cell.icon.getDrawable();
        if (drawable == null) return null;
        int width = cell.icon.getWidth();
        int height = cell.icon.getHeight();
        if (width <= 0 || height <= 0) {
            android.view.ViewGroup.LayoutParams params = cell.icon.getLayoutParams();
            width = params == null ? 0 : params.width;
            height = params == null ? 0 : params.height;
        }
        int size = Math.min(width, height);
        if (size <= 0) size = Math.min(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        return size <= 0 ? null : new PickupArtwork(drawable, size);
    }

    static final class PickupArtwork {
        @NonNull final Drawable drawable;
        final int sizePx;
        PickupArtwork(@NonNull Drawable drawable, int sizePx) {
            this.drawable = drawable;
            this.sizePx = sizePx;
        }
    }

    public void bindTarget(@NonNull View view, @NonNull AppDrawerItem item) {
        view.setOnDragListener((target, event) -> {
            if (event.getLocalState() == active && (event.getAction() == DragEvent.ACTION_DRAG_ENTERED
                || event.getAction() == DragEvent.ACTION_DRAG_LOCATION)) {
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
                if (!host.canDropOnCurrentTarget()) return false;
                AppDrawerItem resolved = host.resolveCurrentDropTarget(target.stableId);
                if (resolved == null || active.sourceStableId.equals(resolved.stableId)) return false;
                active.accepted = applyDropMutation(active.sourceStableId, active.revision,
                    resolved);
                return active.accepted;
            case DragEvent.ACTION_DRAG_EXITED:
                overlay.setFolderHover(false);
                host.onDragTargetExited();
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                cleanup();
                return true;
            default: return true;
        }
    }

    /** The exact repository-and-visible-host seam used by the platform ACTION_DROP path. */
    boolean applyDropMutation(@NonNull String sourceStableId, long revision,
                              @NonNull AppDrawerItem target) {
        LauncherConfigRepository.MutationResult result = target.kind == AppDrawerItem.Kind.FOLDER
            ? dock.addDrawerAppToFolder(revision, target.stableId, sourceStableId)
            : dock.createDrawerFolder(revision, target.app, sourceStableId);
        boolean accepted = result == LauncherConfigRepository.MutationResult.APPLIED;
        if (accepted) {
            // Repository listeners update persistence consumers, but the open pickup surface also
            // needs an explicit end-of-drag recompose. Otherwise the mutation remains invisible
            // until the drawer is closed and rebound.
            host.onAcceptedDrop();
        } else if (result == LauncherConfigRepository.MutationResult.CAPACITY) {
            overlay.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            Toast.makeText(overlay.getContext(), com.termux.R.string.folder_capacity_reached,
                Toast.LENGTH_SHORT).show();
        }
        return accepted;
    }

    public boolean isDragging() { return active != null; }

    public boolean owns(@Nullable Object localState) {
        return active != null && localState == active;
    }

    public void onHostDragEnded(@NonNull DragEvent event) {
        if (owns(event.getLocalState())) cleanup();
    }

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
