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
 * opens the drawer's search.
 */
public final class ControlBar implements TilingDesktop.FrontListener, ControlBarView.Listener {

    /** What the bar needs from the activity; at most a handful of methods. */
    public interface Host {
        /** How many Home pages there are. */
        int homePageCount();

        /** Which Home page is in front, from 0. */
        int homePageInFront();

        /** Turns Home to page {@code page}. */
        void showHomePage(int page);

        /** Home's pinned apps' icons, in order, for the bar's own drawing. */
        @NonNull List<Drawable> pinnedAppIcons();

        /** Opens pinned app {@code index}. */
        void openPinnedApp(int index);

        /** Runs one of the tools the activity owns: the keyboard, mouse mode, search. */
        void runTool(@NonNull ControlBarTool tool);

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
    @NonNull private final TilingDesktop mDesktop;
    @NonNull private final ControlBarView mView;
    @Nullable private ControlBarRows mRows;

    public ControlBar(@NonNull Context context, @NonNull Host host, @NonNull TilingDesktop desktop) {
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
        List<Drawable> icons = new ArrayList<>();
        for (Drawable icon : mHost.pinnedAppIcons()) {
            Drawable.ConstantState state = icon == null ? null : icon.getConstantState();
            // An own instance: the bar sets bounds on it, and the drawer may draw the same one.
            icons.add(state != null ? state.newDrawable(mView.getResources()) : icon);
        }
        mView.setPinnedIcons(icons);
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
            windows.add(new ControlBarFront.Window(window.id(), window.kind()));
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
                mDesktop.run(TilingInput.Command.NEW_TERMINAL);
                break;
            case KEYBOARD:
            case MOUSE_MODE:
            case SEARCH:
                mHost.runTool(tool);
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
