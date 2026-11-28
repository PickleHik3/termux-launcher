package com.termux.app.overview;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.List;
import java.util.function.Consumer;

/**
 * Overview under the Terl switch (#430): {@link OverviewController} hands its opening, closing and
 * Back here once {@link OverviewController#setStack} is given one, and v1's wall Overview is left
 * as it was. It puts {@link StackOverviewView} on the sheets layer with the pages and chrome
 * hidden under it ({@link LayersUnderOverview}); the pages layer (the tiling desktop and the control
 * bar) stays drawn under the sheet, which stands clear of the bar ({@link #keepBar}),
 * reads the rows from the stack each time it opens or something moved, and carries out what a tap or
 * a drop asks through its {@link Host}.
 */
public final class StackOverview {

    /** The tiling desktop and the launcher, as Overview reads and asks them. */
    public interface Host {
        /** A copy of the stack as it stands now. */
        @NonNull TilingStack stack();
        /** Window {@code windowId}'s shell title now; null for none and for other kinds. */
        @Nullable String titleOf(long windowId);
        /** Window {@code windowId} moves to {@code place}, last in it; false when nothing moved. */
        boolean moveWindow(long windowId, @NonNull Place place);
        /** Window {@code windowId} joins {@code targetId}'s tile as a tab; false when nothing changed. */
        boolean joinTab(long windowId, long targetId);
        /** Focuses window {@code windowId}, going to its place. */
        boolean focusWindow(long windowId);
        /** Goes to {@code place}. */
        boolean goTo(@NonNull Place place);
        /** The sheet's material and the inks. */
        @NonNull OverviewLook look();
        /** A Linux app's own icon by its app id; null for its kind's glyph. */
        @Nullable Drawable appIcon(@NonNull String appId);
        /** The saved Workspaces (#431), in order, delivered to {@code onRead} on the main thread. */
        void savedWorkspaces(@NonNull Consumer<List<SavedCard>> onRead);
        /** Opens saved Workspace {@code name}. */
        void openSaved(@NonNull String name);
        /** Asks for a new name for saved Workspace {@code name}; {@code onChanged} runs once it changed. */
        void renameSaved(@NonNull String name, @NonNull Runnable onChanged);
        /** Asks before deleting saved Workspace {@code name}; {@code onChanged} runs once it is gone. */
        void deleteSaved(@NonNull String name, @NonNull Runnable onChanged);
        /** Marks saved Workspace {@code name} to open, or not, at start; {@code onChanged} runs once it is set. */
        void setOpenAtStart(@NonNull String name, boolean on, @NonNull Runnable onChanged);
        /** Opens edit mode (#442), Overview having closed. */
        void openEditMode();
        /** The camera's keep-out where Android's cutout reaches {@code cutoutPx} in, by the cutout settings. */
        int cutoutClearPx(int cutoutPx);
    }

    @NonNull private final Activity mActivity;
    @NonNull private final Host mHost;
    @NonNull private final LayersUnderOverview mUnder = new LayersUnderOverview();
    @Nullable private StackOverviewView mView;
    @Nullable private StackOverviewModel mModel;
    @Nullable private Runnable mOnClosed;

    public StackOverview(@NonNull Activity activity, @NonNull Host host) {
        mActivity = activity;
        mHost = host;
    }

    /** Runs each time Overview closes, whichever way. */
    public void setOnClosed(@Nullable Runnable onClosed) {
        mOnClosed = onClosed;
    }

    /** The control bar the sheet stands clear of; null for none. */
    @Nullable private View mBar;

    /**
     * The sheet stands clear of {@code bar}: above it, or beside it when it is a rail (round 2, as
     * the prototype keeps the bar in sight below Overview).
     */
    public void keepBar(@NonNull View bar) {
        mBar = bar;
    }

    /**
     * What the bar takes of {@code host}'s edges where it stands now (left, right and bottom, in
     * px): a row its height up from the bottom, a rail its width in from its side; none while it is
     * out of sight (full screen) or not laid out.
     */
    @NonNull
    private android.graphics.Rect barRoom(@Nullable View host) {
        android.graphics.Rect room = new android.graphics.Rect();
        View bar = mBar;
        if (bar == null || host == null || !bar.isShown() || bar.getWidth() <= 0 || bar.getHeight() <= 0
                || host.getWidth() <= 0) return room;
        int[] b = new int[2];
        int[] h = new int[2];
        bar.getLocationInWindow(b);
        host.getLocationInWindow(h);
        int left = b[0] - h[0];
        int top = b[1] - h[1];
        if (bar.getHeight() > bar.getWidth()) {
            // A rail: the side it stands on.
            if (left + bar.getWidth() / 2 < host.getWidth() / 2) room.left = Math.max(0, left + bar.getWidth());
            else room.right = Math.max(0, host.getWidth() - left);
        } else {
            room.bottom = Math.max(0, host.getHeight() - top);
        }
        return room;
    }

    public boolean isOpen() {
        return mView != null;
    }

    /** Opens Overview over the place in front; nothing when it is up. */
    public void open() {
        if (mView != null) return;
        StackOverviewView view = new StackOverviewView(mActivity, mHost.look(), mListener);
        // The sheet stands clear of the bar: above a row, beside a rail.
        view.setBarRoom(barRoom(WindowLayers.host(mActivity, WindowLayer.SHEETS)));
        mModel = read();
        view.apply(mModel);
        // A sheet (spec, "Look"): over the pages and the chrome, so the control bar and the
        // keyboard never draw over it.
        boolean added = WindowLayers.add(mActivity, WindowLayer.SHEETS, view,
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (!added) {
            mModel = null;
            return;
        }
        mView = view;
        // The pages stay drawn under the sheet, the windows and the bar among them, as the
        // prototype shows them; Overview writes no alpha there, so the desktop's own fade stays the
        // one writer of its alpha (ADR 0028). The chrome layer goes under it as before.
        mUnder.hide(null, WindowLayers.host(mActivity, WindowLayer.CHROME));
        view.requestFocus();
        readSaved();
    }

    /** The Saved row is read again from the files. */
    private void readSaved() {
        StackOverviewView view = mView;
        if (view == null) return;
        mHost.savedWorkspaces(saved -> {
            if (mView == view) view.setSaved(saved);
        });
    }

    /** A held saved Workspace's menu: Rename, Delete and Open at start. */
    private void showSavedMenu(@NonNull SavedCard saved, @NonNull View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(mActivity, anchor);
        android.view.Menu menu = popup.getMenu();
        menu.add(android.view.Menu.NONE, MENU_RENAME, 0, R.string.saved_workspace_rename);
        menu.add(android.view.Menu.NONE, MENU_DELETE, 1, R.string.saved_workspace_delete);
        menu.add(android.view.Menu.NONE, MENU_OPEN_AT_START, 2, R.string.saved_workspace_open_at_start)
            .setCheckable(true).setChecked(saved.openAtStart);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_RENAME: mHost.renameSaved(saved.name, this::readSaved); return true;
                case MENU_DELETE: mHost.deleteSaved(saved.name, this::readSaved); return true;
                case MENU_OPEN_AT_START:
                    mHost.setOpenAtStart(saved.name, !saved.openAtStart, this::readSaved);
                    return true;
                default: return false;
            }
        });
        popup.show();
    }

    private static final int MENU_RENAME = 1;
    private static final int MENU_DELETE = 2;
    private static final int MENU_OPEN_AT_START = 3;

    /** Removes the screen and shows the layers under it again. */
    /**
     * Who is told when a finger has rested on a row for {@link #LANDING_REST_MS}: that place is
     * made ready ahead of the go (live window residency); told null when the sheet closes. Null
     * for none.
     */
    public void setLandingListener(@Nullable java.util.function.Consumer<Place> listener) {
        mLandingListener = listener;
    }

    @Nullable private java.util.function.Consumer<Place> mLandingListener;
    @NonNull private final com.termux.app.tiling.LandingRest mLanding = new com.termux.app.tiling.LandingRest(
        place -> {
            java.util.function.Consumer<Place> listener = mLandingListener;
            if (listener != null && (place == null || mView != null)) listener.accept(place);
        });

    public void close() {
        StackOverviewView view = mView;
        if (view == null) return;
        mView = null;
        mLanding.closed();
        mModel = null;
        mUnder.restore();
        view.release();
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        if (mOnClosed != null) mOnClosed.run();
    }

    /** The view the Home exit fades with the others; none when closed. */
    public void exitViews(@NonNull List<View> out) {
        if (mView != null) out.add(mView);
    }

    /** The Home exit started: the fade reveals the place the press lands on, not the bare ground. */
    public void exitStarted() {
        mUnder.restore();
    }

    /** The rows are read again from the stack, after something moved. */
    private void rebuild() {
        StackOverviewView view = mView;
        if (view == null) return;
        mModel = read();
        view.apply(mModel);
    }

    @NonNull
    private StackOverviewModel read() {
        return StackOverviewModel.of(mHost.stack(), mHost::titleOf);
    }

    @NonNull private final StackOverviewView.Listener mListener = new StackOverviewView.Listener() {
        @Override public void onPlaceTouched(@Nullable Place place) {
            mLanding.over(place);
        }

        @Override public void onCard(long windowId) {
            // Closed first, so the layers are back when the desktop moves to the window.
            close();
            mHost.focusWindow(windowId);
        }

        @Override public void onPlace(@NonNull Place place) {
            close();
            mHost.goTo(place);
        }

        @Override public void onEdit() {
            close();
            mHost.openEditMode();
        }

        @Override public void onSaved(@NonNull SavedCard saved) {
            close();
            mHost.openSaved(saved.name);
        }

        @Override public void onSavedHeld(@NonNull SavedCard saved, @NonNull View card) {
            showSavedMenu(saved, card);
        }

        @Override public void onDrop(long windowId, int row, long onCard) {
            StackOverviewModel shown = mModel;
            if (shown == null) return;
            // The stack may have changed under Overview (a shell exited, a Workspace above went):
            // the row is found again by what it holds, and the drop judged on the stack as it is.
            StackOverviewModel now = read();
            OverviewDropPolicy.StackDrop drop =
                OverviewDropPolicy.stackDrop(now, windowId, shown.rowIn(now, row), onCard);
            if (drop.type == OverviewDropPolicy.StackDrop.Type.MOVE && drop.place != null) {
                mHost.moveWindow(drop.windowId, drop.place);
            } else if (drop.type == OverviewDropPolicy.StackDrop.Type.TAB) {
                mHost.joinTab(drop.windowId, drop.targetId);
            }
            // Read again either way: what Overview shows may be stale even when nothing moved.
            rebuild();
        }

        @NonNull @Override
        public OverviewDropPolicy.StackDrop judge(long windowId, int row, long onCard) {
            StackOverviewModel model = mModel;
            return model == null ? OverviewDropPolicy.StackDrop.NOTHING
                : OverviewDropPolicy.stackDrop(model, windowId, row, onCard);
        }

        @Nullable @Override public Drawable appIcon(@NonNull String appId) {
            return mHost.appIcon(appId);
        }

        @Override public int cutoutClearPx(int cutoutPx) {
            return mHost.cutoutClearPx(cutoutPx);
        }
    };
}
