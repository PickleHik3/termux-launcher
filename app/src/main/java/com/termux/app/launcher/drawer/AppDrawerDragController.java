package com.termux.app.launcher.drawer;

import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.haptics.Haptics;
import com.termux.app.notice.AppNotice;
import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.LauncherConfigSnapshot;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.widget.HomeAppDrop;

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

        /**
         * @return the anchor a dragged folder should take when dropped on {@code targetStableId}:
         *     the stable id of the first app at or after that row, or
         *     {@link com.termux.app.launcher.model.PinnedFolderItem#DRAWER_ANCHOR_END} when the
         *     drop lands past the last app.
         */
        @NonNull String drawerAnchorFor(@Nullable String targetStableId);

        /**
         * Home, for an app carried out of the drawer through its top edge; null when there is no
         * Home to hand over to, and the drag stays the drawer's own wherever it goes.
         */
        @Nullable default HomeAppDrop.Target homeAppDropTarget() { return null; }

        /**
         * The drawer's top edge on screen, which a carried app crosses to go to Home
         * ({@link DrawerHandOverPolicy}); NaN while there is none, and nothing is handed over.
         */
        default float handOverTopRawY() { return Float.NaN; }
    }

    private final SuggestionBarView dock;
    private final AppDrawerDragOverlayView overlay;
    private final Host host;
    @Nullable private LocalState active;
    private boolean cleaned = true;
    /** Whether the finger has left the drawer through its top edge, asked on every move. */
    private final DrawerHandOverPolicy handOver = new DrawerHandOverPolicy();
    /** A finger resting above the top: asked again when its hold runs out. */
    private final Runnable handOverHold = this::onHandOverHoldElapsed;
    private boolean handOverHoldPosted;
    private float lastRawX, lastRawY;
    private final int[] location = new int[2];
    /**
     * The drag this drawer handed to Home, until it ends. While the drawer closes its views still
     * catch the drag's events, and pass them on to Home rather than eat them.
     */
    @Nullable private HomeAppDrop handed;

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
        // The drag is the drawer's (folders, its drop targets) until the finger leaves through the
        // top edge. Its local state is already what Home will take it as, since a platform drag's
        // local state cannot change on the way; Home ignores it until it is handed over.
        HomeAppDrop drop = new HomeAppDrop(entry.appRef, entry.label == null ? "" : entry.label,
            drawable, size);
        active = new LocalState(entry.appRef.stableId(), snapshot.revision, bounds,
            host.frozenSourceViewType(), drop);
        cleaned = false;
        handed = null;
        resetHandOver();
        lastRawX = bounds.exactCenterX();
        lastRawY = bounds.exactCenterY();
        // Public payload is intentionally empty; all identity remains in process-local state.
        ClipData clip = ClipData.newPlainText("launcher-item", "");
        View.DragShadowBuilder shadow = new DrawableShadow(drawable, size);
        boolean started = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            ? source.startDragAndDrop(clip, shadow, drop, 0)
            : source.startDrag(clip, shadow, drop, 0);
        if (!started) cleanup();
        else host.onDragStateChanged(true);
        return started;
    }

    /**
     * The finger carrying the drawer's drag is at ({@code rawX}, {@code rawY}) on screen. An app
     * carried out through the drawer's top edge ({@link DrawerHandOverPolicy}) goes to Home, if
     * Home can take it; a folder tile never leaves the drawer.
     */
    public void onDragMoved(float rawX, float rawY) {
        LocalState state = active;
        if (state == null || state.homeDrop == null) return;
        lastRawX = rawX;
        lastRawY = rawY;
        long now = SystemClock.uptimeMillis();
        DrawerHandOverPolicy.Decision decision = handOver.onMove(rawY, host.handOverTopRawY(),
            overlay.getResources().getDisplayMetrics().density, now);
        if (decision == DrawerHandOverPolicy.Decision.HAND_OVER) {
            cancelHandOverHold();
            handOverToHome();
            return;
        }
        long wait = handOver.msUntilHandOver(now);
        if (wait < 0L) {
            cancelHandOverHold();
        } else if (!handOverHoldPosted) {
            handOverHoldPosted = true;
            overlay.postDelayed(handOverHold, Math.max(1L, wait));
        }
    }

    /** A finger held above the drawer's top: the platform sends no moves while it rests. */
    private void onHandOverHoldElapsed() {
        handOverHoldPosted = false;
        onDragMoved(lastRawX, lastRawY);
    }

    private void cancelHandOverHold() {
        if (!handOverHoldPosted) return;
        handOverHoldPosted = false;
        overlay.removeCallbacks(handOverHold);
    }

    private void resetHandOver() {
        cancelHandOverHold();
        handOver.reset();
    }

    /**
     * Launcher3's way, once the finger has left the drawer: the drawer's drag ends here and Home
     * takes the platform drag over. The platform's shadow is swapped for nothing and Home's drag
     * layer draws the icon at the same size under the same finger, so nothing jumps; Home closes
     * the drawer, turns the wall to itself and puts the icon down on its grid. Stays the drawer's
     * drag when Home cannot take it (no Home, its layout locked, another drag in flight).
     */
    private boolean handOverToHome() {
        LocalState state = active;
        if (state == null || state.homeDrop == null) return false;
        HomeAppDrop.Target home = host.homeAppDropTarget();
        if (home == null || !home.canTakeAppDrop()) return false;
        HomeAppDrop drop = state.homeDrop;
        overlay.updateDragShadow(HomeAppDrop.invisibleShadow());
        // The drawer's part ends before Home's begins: Home closes the drawer, and the close
        // cancels whatever drag the drawer still thinks it has.
        resetHandOver();
        cleaned = true;
        active = null;
        overlay.clear();
        host.onDragStateChanged(false);
        handed = drop;
        home.onAppDropStarted(drop, lastRawX, lastRawY);
        return true;
    }

    /** Whether {@code localState} is a drag this drawer handed to Home, whose events it passes on. */
    public boolean handedToHome(@Nullable Object localState) {
        return handed != null && localState == handed;
    }

    /**
     * An event of a drag handed to Home that landed on one of the drawer's views while it closes,
     * the finger at ({@code rawX}, {@code rawY}) on screen: passed on to Home's follower.
     */
    public boolean forwardToHome(@NonNull DragEvent event, float rawX, float rawY) {
        HomeAppDrop drop = handed;
        if (drop == null || event.getLocalState() != drop) return false;
        HomeAppDrop.Follower follower = drop.follower();
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_LOCATION:
                if (follower != null) follower.moveTo(rawX, rawY);
                return true;
            case DragEvent.ACTION_DROP:
                return follower != null && follower.releaseAt(rawX, rawY);
            case DragEvent.ACTION_DRAG_ENDED:
                handed = null;
                return true;
            default:
                return true;
        }
    }

    /**
     * Picks up a folder tile. Same platform drag as an app, but the drop repositions the folder in
     * the drawer instead of merging it into whatever it lands on — a folder has nothing to merge.
     */
    @Override public boolean startFolderPickup(@NonNull View source, @NonNull PinnedFolderItem folder) {
        if (!host.isFrozenPickupEligible(folder.id)) return false;
        if (!host.claimPickupDrag(folder.id)) return false;
        PickupArtwork artwork = folderPickupArtwork(source);
        if (artwork == null) return false;
        LauncherConfigSnapshot snapshot = dock.getLauncherConfigSnapshot();
        host.armTerminalDispatchDragLatch();
        Rect bounds = new Rect();
        source.getGlobalVisibleRect(bounds);
        overlay.begin(artwork.drawable, artwork.sizePx, bounds);
        active = new LocalState(folder.id, snapshot.revision, bounds,
            host.frozenSourceViewType(), true, null);
        cleaned = false;
        handed = null;
        resetHandOver();
        ClipData clip = ClipData.newPlainText("launcher-item", "");
        View.DragShadowBuilder shadow = new DrawableShadow(artwork.drawable, artwork.sizePx);
        boolean started = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            ? source.startDragAndDrop(clip, shadow, active, 0)
            : source.startDrag(clip, shadow, active, 0);
        if (!started) cleanup();
        else host.onDragStateChanged(true);
        return started;
    }

    /**
     * A folder tile has no single icon view — its artwork is the mosaic of member icons, so the
     * drag shadow is a snapshot of that mosaic rather than a shared cached drawable.
     */
    @Nullable
    static PickupArtwork folderPickupArtwork(@NonNull View source) {
        if (!(source instanceof AppDrawerFolderCellView)) return null;
        View mosaic = ((AppDrawerFolderCellView) source).mosaicView();
        int size = Math.min(mosaic.getWidth(), mosaic.getHeight());
        if (size <= 0) return null;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        mosaic.draw(new Canvas(bitmap));
        return new PickupArtwork(
            new BitmapDrawable(source.getResources(), bitmap), size);
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
            ViewGroup.LayoutParams params = cell.icon.getLayoutParams();
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
            Object local = event.getLocalState();
            if (handedToHome(local)) {
                target.getLocationOnScreen(location);
                return forwardToHome(event, location[0] + event.getX(),
                    location[1] + event.getY());
            }
            if (owns(local) && (event.getAction() == DragEvent.ACTION_DRAG_ENTERED
                || event.getAction() == DragEvent.ACTION_DRAG_LOCATION)) {
                overlay.moveTo(target, event.getX(), event.getY(),
                    item.kind == AppDrawerItem.Kind.FOLDER);
                if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
                    target.getLocationOnScreen(location);
                    onDragMoved(location[0] + event.getX(), location[1] + event.getY());
                    if (active == null) return true;
                }
            }
            return handleTarget(event, item);
        });
    }

    private boolean handleTarget(@NonNull DragEvent event, @NonNull AppDrawerItem target) {
        if (active == null || !owns(event.getLocalState())) return false;
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

    /** True while the in-flight drag carries a folder tile rather than an app. */
    public boolean isFolderSourceDrag() {
        return active != null && active.folderSource;
    }

    /**
     * Drop on drawer space no row claimed: a dragged folder parks at the end of the list. Apps have
     * nothing to do here — an app dropped on blank space keeps its alphabetical place.
     */
    public boolean dropFolderAtDrawerEnd() {
        if (active == null || !active.folderSource) return false;
        active.accepted = applyFolderMove(active.sourceStableId, active.revision,
            PinnedFolderItem.DRAWER_ANCHOR_END);
        return active.accepted;
    }

    /** The exact repository-and-visible-host seam used by the platform ACTION_DROP path. */
    boolean applyDropMutation(@NonNull String sourceStableId, long revision,
                              @NonNull AppDrawerItem target) {
        if (active != null && active.folderSource) {
            return applyFolderMove(sourceStableId, revision,
                host.drawerAnchorFor(target.stableId));
        }
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
            Haptics.tick(overlay, HapticFeedbackConstants.CLOCK_TICK);
            AppNotice.show(overlay.getContext(), com.termux.R.string.folder_capacity_reached, false);
        }
        return accepted;
    }

    private boolean applyFolderMove(@NonNull String folderId, long revision,
                                    @NonNull String anchorStableId) {
        boolean accepted = dock.moveDrawerFolder(revision, folderId, anchorStableId)
            == LauncherConfigRepository.MutationResult.APPLIED;
        if (accepted) host.onAcceptedDrop();
        return accepted;
    }

    public boolean isDragging() { return active != null; }

    /** Whether {@code localState} is the drawer's own drag in flight (not one handed to Home). */
    public boolean owns(@Nullable Object localState) {
        LocalState state = active;
        return state != null && localState != null
            && (localState == state || localState == state.homeDrop);
    }

    public void onHostDragEnded(@NonNull DragEvent event) {
        if (owns(event.getLocalState())) cleanup();
    }

    public void cancel() { cleanup(); }

    private void cleanup() {
        if (cleaned) return;
        cleaned = true;
        resetHandOver();
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
        /** A folder tile on the move; its stable id is the folder id, not an app's. */
        final boolean folderSource;
        /**
         * An app's drag: the platform drag's local state, and what Home takes over if the finger
         * leaves the drawer through its top edge. Null for a folder tile, which stays here.
         */
        @Nullable final HomeAppDrop homeDrop;
        boolean accepted;
        LocalState(String sourceStableId, long revision, Rect frozenSourceBounds,
                   AppDrawerViewType sourceViewType, @NonNull HomeAppDrop homeDrop) {
            this(sourceStableId, revision, frozenSourceBounds, sourceViewType, false, homeDrop);
        }
        LocalState(String sourceStableId, long revision, Rect frozenSourceBounds,
                   AppDrawerViewType sourceViewType, boolean folderSource,
                   @Nullable HomeAppDrop homeDrop) {
            this.sourceStableId = sourceStableId;
            this.revision = revision;
            this.frozenSourceBounds = new Rect(frozenSourceBounds);
            this.sourceViewType = sourceViewType;
            this.folderSource = folderSource;
            this.homeDrop = homeDrop;
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
