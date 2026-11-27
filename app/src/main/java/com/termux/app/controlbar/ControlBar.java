package com.termux.app.controlbar;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingInput;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.desktop.TilingDesktop;

import java.util.ArrayList;
import java.util.List;

/**
 * The control bar (#424, ADR 0038, behind the Terl switch): one glass card at the bottom of every
 * place. It reads the place in front from the tiling desktop ({@link TilingDesktop.FrontListener})
 * and Home's pages from the activity, turns them into rows ({@link ControlBarRows}) and hands them
 * to its view; a touch on the view comes back here and goes to the desktop or the activity.
 *
 * <p>Not here yet: the held mark's window menu (#425), navigation mode (#433), search's own sheet
 * (#435), the readings (#428). The window menu and navigation tools do nothing until then; search
 * opens the drawer's search. A Linux app has no touchpad yet, so its Mouse mode changes nothing.
 */
public final class ControlBar implements TilingDesktop.FrontListener, ControlBarView.Listener {

    /** What the bar reads of and asks of the tiling desktop ({@link #of(TilingDesktop)}). */
    public interface Desktop {
        void setFrontListener(@Nullable TilingDesktop.FrontListener listener);

        @NonNull Place front();

        @NonNull List<TilingWindow> frontWindows();

        @Nullable TilingWindow frontMainWindow();

        long frontFocusedWindowId();

        boolean focusWindow(long windowId);

        void split();

        void newTerminal();

        /** The window in front's own keyboard; false when it has none (a shell, Home). */
        boolean toggleFrontKeyboard();

        /** The window in front's mouse mode; false when it has none. */
        boolean toggleFrontMouseMode();
    }

    /** The tiling desktop as the bar reads it. */
    @NonNull
    public static Desktop of(@NonNull TilingDesktop desktop) {
        return new Desktop() {
            @Override public void setFrontListener(@Nullable TilingDesktop.FrontListener listener) {
                desktop.setFrontListener(listener);
            }
            @NonNull @Override public Place front() { return desktop.front(); }
            @NonNull @Override public List<TilingWindow> frontWindows() { return desktop.frontWindows(); }
            @Nullable @Override public TilingWindow frontMainWindow() { return desktop.frontMainWindow(); }
            @Override public long frontFocusedWindowId() { return desktop.frontFocusedWindowId(); }
            @Override public boolean focusWindow(long windowId) { return desktop.focusWindow(windowId); }
            @Override public void split() { desktop.split(); }
            @Override public void newTerminal() { desktop.run(TilingInput.Command.NEW_TERMINAL); }
            @Override public boolean toggleFrontKeyboard() { return desktop.toggleFrontKeyboard(); }
            @Override public boolean toggleFrontMouseMode() { return desktop.toggleFrontMouseMode(); }
        };
    }

    /** What the bar needs from the activity; at most a handful of methods. */
    public interface Host {
        /** How many Home pages there are. */
        int homePageCount();

        /** Which Home page is in front, from 0. */
        int homePageInFront();

        /** Turns Home to page {@code page}. */
        void showHomePage(int page);

        /** Home's pinned apps, in order, for the bar's own drawing and spoken labels. */
        @NonNull List<ControlBarView.PinnedApp> pinnedApps();

        /** Opens pinned app {@code index}. */
        void openPinnedApp(int index);

        /** The launcher's keyboard comes up or goes, typing into the focused shell. */
        void toggleKeyboard();

        /** Opens search. */
        void openSearch();

        /** The bar's glass at {@code radiusPx} for {@code anchor}: Terl's control-bar look. */
        @Nullable Drawable glass(float radiusPx, @NonNull View anchor);

        /** Terl's corner radius, in px. */
        float cornerRadiusPx();

        /** The ink for marks and tools on the bar's glass. */
        int ink();

        /** The scheme's accent, for what is lit. */
        int accent();
    }

    @NonNull private final Host mHost;
    @NonNull private final Desktop mDesktop;
    @NonNull private final ControlBarView mView;
    @Nullable private ControlBarRows mRows;

    public ControlBar(@NonNull Context context, @NonNull Host host, @NonNull Desktop desktop) {
        mHost = host;
        mDesktop = desktop;
        mView = new ControlBarView(context, this);
        mView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            // A new size needs glass cut for it; posted, never from inside the layout pass.
            if (r - l != or - ol || b - t != ob - ot) v.post(this::restyle);
        });
        desktop.setFrontListener(this);
    }

    @NonNull
    public View view() {
        return mView;
    }

    // ---- what the activity tells it --------------------------------------------------------------

    /** The place in front, its windows or Home's page may have changed: the rows are read again. */
    @Override
    public void onFrontChanged() {
        ControlBarRows rows = ControlBarRows.of(front());
        if (rows.equals(mRows)) return;
        boolean homeChanged = mRows == null || mRows.home != rows.home;
        mRows = rows;
        mView.setRows(rows);
        if (homeChanged && rows.home) refreshPinnedApps();
    }

    /** Home's pinned apps or their icons changed. */
    public void refreshPinnedApps() {
        List<ControlBarView.PinnedApp> apps = new ArrayList<>();
        for (ControlBarView.PinnedApp app : mHost.pinnedApps()) {
            Drawable icon = app.icon;
            Drawable.ConstantState state = icon == null ? null : icon.getConstantState();
            // An own instance: the bar sets bounds on it, and the drawer may draw the same one.
            apps.add(new ControlBarView.PinnedApp(
                state != null ? state.newDrawable(mView.getResources()) : icon, app.label));
        }
        mView.setPinnedApps(apps);
    }

    /** The look, the scheme or the glass's frame changed: the card is dressed again. */
    public void restyle() {
        float radius = mHost.cornerRadiusPx();
        mView.setCornerRadiusPx(radius);
        mView.setInks(mHost.ink(), mHost.accent());
        if (mView.getWidth() > 0 && mView.getHeight() > 0) mView.setBackground(mHost.glass(radius, mView));
    }

    @NonNull
    private ControlBarFront front() {
        Place place = mDesktop.front();
        if (place.isHome()) return ControlBarFront.home(mHost.homePageCount(), mHost.homePageInFront());
        List<ControlBarFront.Window> windows = new ArrayList<>();
        for (TilingWindow window : mDesktop.frontWindows()) {
            windows.add(new ControlBarFront.Window(window.id(), window.kind(), window.name()));
        }
        TilingWindow main = mDesktop.frontMainWindow();
        return ControlBarFront.workspace(place.workspaceNumber(), main == null ? null : main.kind(),
            windows, mDesktop.frontFocusedWindowId());
    }

    // ---- what a touch on the view asks -----------------------------------------------------------

    @Override
    public void onMark(int index) {
        ControlBarRows rows = mRows;
        if (rows == null || index < 0 || index >= rows.marks.size()) return;
        long target = rows.marks.get(index).target;
        if (rows.home) {
            mHost.showHomePage((int) target);
            onFrontChanged();
        } else {
            mDesktop.focusWindow(target);
        }
    }

    @Override
    public void onTool(@NonNull ControlBarTool tool) {
        switch (tool) {
            case SPLIT:
                mDesktop.split();
                break;
            case NEW_TERMINAL:
                mDesktop.newTerminal();
                break;
            case KEYBOARD:
                // A Linux app, the Display or the Assistant takes it as its own; a shell types
                // from the launcher's keyboard, which follows the focused window.
                if (!mDesktop.toggleFrontKeyboard()) mHost.toggleKeyboard();
                break;
            case MOUSE_MODE:
                // Only the window in front's own: v1's Display page is not touched.
                mDesktop.toggleFrontMouseMode();
                break;
            case SEARCH:
                mHost.openSearch();
                break;
            case WINDOW_MENU:
            case NAVIGATION:
            case PINNED_APPS:
            default:
                // The window menu (#425) and navigation mode (#433) come with their tickets.
                break;
        }
    }

    @Override
    public void onPinnedApp(int index) {
        mHost.openPinnedApp(index);
    }
}
