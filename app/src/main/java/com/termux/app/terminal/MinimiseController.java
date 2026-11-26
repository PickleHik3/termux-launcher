package com.termux.app.terminal;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.pageswitch.PageBarModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimise from a window's controls, and the way back from Overview. A Linux window goes by its
 * own path (it waits as its chip); a Terminal window leaves the row with its shells still running
 * and waits in {@link MinimisedWindows} with the index it left. Overview shows both as cards
 * after the wall's stops ({@link #fill}), terminal windows first; a card's tap ({@link #restore})
 * puts the window back at its place and in front. The row's last Terminal window stays: a row is
 * never left without one. The minimised Terminal windows are saved with the pane layout.
 *
 * @param <W> the Terminal window
 */
public final class MinimiseController<W> {

    /** What the controller asks of the activity; places are row places, indexes terminal windows'. */
    public interface Host<W> {
        /** The Linux window at row place {@code place}; 0 when a terminal window stands there. */
        long linuxAt(int place);

        void minimiseLinux(long id);

        /** Brings minimised Linux window {@code id} back, on the Terminal page. */
        void restoreLinux(long id);

        /** The minimised Linux windows' ids, in chip order, into {@code out} (cleared first). */
        void minimisedLinuxIds(@NonNull List<Long> out);

        /** The terminal window index at row place {@code place}; -1 for none. */
        int terminalAtPlace(int place);

        /** How many terminal windows the current row has. */
        int terminalCount();

        /** Takes terminal window {@code index} out of the row, settling what is in front; the window, or null. */
        @Nullable W removeFromRow(int index);

        /** Puts {@code window} back into the row at terminal index {@code index} and shows it. */
        void returnToRow(@NonNull W window, int index);

        /** Whether {@code window} still has a running shell. */
        boolean hasShells(@NonNull W window);

        @Nullable String name(@NonNull W window);

        @Nullable String linuxName(long id);

        @Nullable String linuxIconKey(long id);

        @Nullable Bundle save(@NonNull W window);
    }

    /** Rebuilds a saved window around its running shells; null when its shells are gone. */
    public interface Restorer<W> {
        @Nullable W restore(@NonNull Bundle state);
    }

    private static final String TAG = "MARKS";
    private static final String STATE = "minimised_windows";
    private static final String STATE_WINDOW = "window";
    private static final String STATE_INDEX = "index";

    @NonNull private final Host<W> mHost;
    @NonNull private final MinimisedWindows<W> mWindows = new MinimisedWindows<>();
    @NonNull private final List<Long> mLinuxIds = new ArrayList<>();

    public MinimiseController(@NonNull Host<W> host) {
        mHost = host;
    }

    /** The minimised Terminal windows; for tests and the activity's save check. */
    @NonNull
    public MinimisedWindows<W> windows() {
        return mWindows;
    }

    /** Minimises the row's window at {@code place}; whether it left the row. */
    public boolean minimise(int place) {
        long linux = mHost.linuxAt(place);
        if (linux != 0L) {
            mHost.minimiseLinux(linux);
            return true;
        }
        int index = mHost.terminalAtPlace(place);
        int count = mHost.terminalCount();
        if (index < 0 || index >= count || count < 2) {
            android.util.Log.i(TAG, "minimise refused place=" + place + " windows=" + count);
            return false;
        }
        W window = mHost.removeFromRow(index);
        if (window == null) return false;
        mWindows.add(window, index);
        return true;
    }

    /** Brings minimised window {@code i} (in Overview's order: terminal windows, then Linux) back. */
    public void restore(int i) {
        if (i < 0) return;
        int terminals = mWindows.size();
        if (i >= terminals) {
            mHost.minimisedLinuxIds(mLinuxIds);
            int linux = i - terminals;
            if (linux < mLinuxIds.size()) mHost.restoreLinux(mLinuxIds.get(linux));
            return;
        }
        MinimisedWindows.Item<W> item = mWindows.remove(i);
        if (item == null || !mHost.hasShells(item.window)) return;
        mHost.returnToRow(item.window,
            MinimisedWindows.restoreIndex(item.index, mHost.terminalCount()));
    }

    /** Hands the page bar's model the minimised windows, for Overview's cards. */
    public void fill(@NonNull PageBarModel.Input in) {
        mHost.minimisedLinuxIds(mLinuxIds);
        int count = mWindows.size() + mLinuxIds.size();
        if (count == 0) return;
        List<String> names = new ArrayList<>(count);
        List<String> appIds = new ArrayList<>(count);
        boolean[] linux = new boolean[count];
        for (MinimisedWindows.Item<W> item : mWindows.items()) {
            names.add(mHost.name(item.window));
            appIds.add(null);
        }
        for (int i = 0; i < mLinuxIds.size(); i++) {
            long id = mLinuxIds.get(i);
            linux[mWindows.size() + i] = true;
            names.add(mHost.linuxName(id));
            appIds.add(mHost.linuxIconKey(id));
        }
        in.minimisedNames = names;
        in.minimisedLinux = linux;
        in.minimisedAppIds = appIds;
    }

    /** Saves the minimised Terminal windows into the pane layout's {@code root}. */
    public void save(@NonNull Bundle root) {
        ArrayList<Bundle> states = new ArrayList<>();
        for (MinimisedWindows.Item<W> item : mWindows.items()) {
            Bundle window = mHost.save(item.window);
            if (window == null) continue;
            Bundle state = new Bundle();
            state.putBundle(STATE_WINDOW, window);
            state.putInt(STATE_INDEX, item.index);
            states.add(state);
        }
        if (!states.isEmpty()) root.putParcelableArrayList(STATE, states);
    }

    /** The minimised Terminal windows of a saved layout, back around their running shells. */
    public void restoreState(@NonNull Bundle root, @NonNull Restorer<W> restorer) {
        mWindows.clear();
        ArrayList<Bundle> states = root.getParcelableArrayList(STATE);
        if (states == null) return;
        for (Bundle state : states) {
            Bundle saved = state == null ? null : state.getBundle(STATE_WINDOW);
            W window = saved == null ? null : restorer.restore(saved);
            if (window != null) mWindows.add(window, state.getInt(STATE_INDEX, 0));
        }
    }
}
