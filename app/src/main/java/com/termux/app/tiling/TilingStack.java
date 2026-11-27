package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The tiling desktop's stack (spec "Windows and places", "Moving around"; ADR 0037): Home's pages,
 * then Workspaces 1…n, each window owned by one of them, and where focus is.
 *
 * <p>The rules it keeps: exactly one empty Workspace waits at the end, so an empty Workspace
 * anywhere else goes at once and the ones below it take its number; a closed window hands focus
 * to the next window in its group, else the group to its right, else to its left; a Workspace
 * that goes while in front leaves the one that takes its number in front. A Home page holds only
 * Floats. Geometry is the layout policies': they report each window's area for the main icon and
 * keep their own state in {@link TilingWorkspace#layoutState()}.
 *
 * <p>Pure: no Android views. Not thread-safe; the UI thread owns it.
 */
public final class TilingStack {

    /** {@link #focusedWindowId()} when nothing is focused. */
    public static final long NO_WINDOW = -1;

    /** Thrown by {@link #fromJson} for a saved form it cannot read; the caller starts fresh. */
    public static final class SavedFormException extends Exception {
        SavedFormException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final int VERSION = 1;

    /** The most Home pages a saved form may name. */
    public static final int MAX_HOME_PAGES = 64;
    /** The most Workspaces a saved form may hold. */
    public static final int MAX_WORKSPACES = 256;
    /** A stacked column holds one window over another, never more. */
    public static final int MAX_STACKED = 2;

    private final List<TilingWorkspace> workspaces = new ArrayList<>();
    private final List<List<TilingWindow>> homeFloats = new ArrayList<>();
    private final List<TilingWindow> homeFocus = new ArrayList<>();
    private int homePage;
    /** The Workspace in front by index, or -1 for Home. */
    private int current = -1;
    private long nextId = 1;

    private TilingStack(int homePageCount) {
        for (int i = 0; i < Math.max(0, homePageCount); i++) {
            homeFloats.add(new ArrayList<>());
            homeFocus.add(null);
        }
        if (homePageCount <= 0) current = 0;
        normalize();
    }

    /** Home's pages and only the waiting Workspace. With no Home pages the stack starts at 1. */
    @NonNull
    public static TilingStack empty(int homePageCount) {
        return new TilingStack(homePageCount);
    }

    /**
     * What a fresh install and an upgrade from v1 open on: Home, then Workspace 1 holding one
     * terminal in {@code shellDirectory}; Home is in front when there is one.
     */
    @NonNull
    public static TilingStack firstStart(int homePageCount, @Nullable String shellDirectory) {
        TilingStack stack = new TilingStack(homePageCount);
        stack.openIn(Place.workspace(1), WindowSpec.shell(shellDirectory), false);
        return stack;
    }

    // ---- reading ---------------------------------------------------------------------------------

    public int homePageCount() {
        return homeFloats.size();
    }

    /** Every Workspace, the waiting one included. */
    public int workspaceCount() {
        return workspaces.size();
    }

    /** Home's pages, then the Workspaces, top to bottom. */
    @NonNull
    public List<Place> places() {
        List<Place> places = new ArrayList<>();
        for (int i = 0; i < homeFloats.size(); i++) places.add(Place.home(i));
        for (int i = 0; i < workspaces.size(); i++) places.add(Place.workspace(i + 1));
        return places;
    }

    /** The place in front. */
    @NonNull
    public Place current() {
        return current < 0 ? Place.home(homePage) : Place.workspace(current + 1);
    }

    /** Workspace {@code number}, or null when there is none. */
    @Nullable
    public TilingWorkspace workspace(int number) {
        return number >= 1 && number <= workspaces.size() ? workspaces.get(number - 1) : null;
    }

    /** The Floats on Home page {@code page}; empty for a page that does not exist. */
    @NonNull
    public List<TilingWindow> homeFloats(int page) {
        if (page < 0 || page >= homeFloats.size()) return Collections.emptyList();
        return Collections.unmodifiableList(homeFloats.get(page));
    }

    /** Every pinned Float: Home page by Home page, then Workspace by Workspace. */
    @NonNull
    public List<TilingWindow> pinnedFloats() {
        List<TilingWindow> pinned = new ArrayList<>();
        for (List<TilingWindow> page : homeFloats)
            for (TilingWindow window : page) if (window.pinned) pinned.add(window);
        for (TilingWorkspace workspace : workspaces)
            for (TilingWindow window : workspace.floats) if (window.pinned) pinned.add(window);
        return pinned;
    }

    @Nullable
    public TilingWindow window(long id) {
        for (TilingWorkspace workspace : workspaces)
            for (TilingWindow window : workspace.windows()) if (window.id() == id) return window;
        for (List<TilingWindow> page : homeFloats)
            for (TilingWindow window : page) if (window.id() == id) return window;
        return null;
    }

    /** The place that owns window {@code id}, or null when there is no such window. */
    @Nullable
    public Place placeOf(long id) {
        for (int i = 0; i < workspaces.size(); i++)
            for (TilingWindow window : workspaces.get(i).windows())
                if (window.id() == id) return Place.workspace(i + 1);
        for (int i = 0; i < homeFloats.size(); i++)
            for (TilingWindow window : homeFloats.get(i))
                if (window.id() == id) return Place.home(i);
        return null;
    }

    /** The focused window of the place in front, or {@link #NO_WINDOW}. */
    public long focusedWindowId() {
        TilingWindow focused = current < 0 ? homeFocus.get(homePage) : workspaces.get(current).focused;
        return focused == null ? NO_WINDOW : focused.id();
    }

    /**
     * The kind of the window in front (the focused window of the place in front), or null when
     * that place has none. The control bar reads it to show a kind's own tools.
     */
    @Nullable
    public WindowKind frontKind() {
        long id = focusedWindowId();
        TilingWindow window = id == NO_WINDOW ? null : window(id);
        return window == null ? null : window.kind();
    }

    /** The first window of {@code kind}, Workspace by Workspace, then Home's Floats; or null. */
    @Nullable
    public TilingWindow firstOfKind(@NonNull WindowKind kind) {
        for (TilingWorkspace workspace : workspaces)
            for (TilingWindow window : workspace.windows()) if (window.kind() == kind) return window;
        for (List<TilingWindow> page : homeFloats)
            for (TilingWindow window : page) if (window.kind() == kind) return window;
        return null;
    }

    // ---- opening, closing, moving ----------------------------------------------------------------

    /**
     * Opens a window in the Workspace in front, after its focused window, and focuses it. From
     * Home it opens in the waiting Workspace and goes there.
     */
    public long open(@NonNull WindowSpec spec) {
        if (current < 0) current = workspaces.size() - 1;
        return openIn(current(), spec, false);
    }

    /**
     * Opens a window in {@code place} without going there; it becomes that place's focused
     * window. On a Workspace it tiles after the focused window unless {@code asFloat}; on a Home
     * page it is always a Float. A Workspace past the end means the waiting one.
     */
    public long openIn(@NonNull Place place, @NonNull WindowSpec spec, boolean asFloat) {
        TilingWindow window = new TilingWindow(nextId++, spec.kind, spec.name, spec.workingDirectory,
            spec.appId);
        attach(window, place, asFloat, true);
        normalize();
        return window.id();
    }

    /** Closes window {@code id}; focus falls to its neighbour. False when there is none. */
    public boolean close(long id) {
        TilingWindow window = window(id);
        if (window == null) return false;
        detach(window);
        normalize();
        return true;
    }

    /**
     * Moves window {@code id} to {@code place}: a tile goes last in a Workspace, a Float stays a
     * Float, and on a Home page it becomes one. The place in front does not change.
     */
    public boolean moveWindow(long id, @NonNull Place place) {
        TilingWindow window = window(id);
        if (window == null) return false;
        TilingWorkspace target = place.isHome() ? null : workspaceOrWaiting(place.workspaceNumber());
        if (target != null && target.windows().contains(window)) return false;
        detach(window);
        if (target != null) attachTo(target, window, window.floating, false);
        else attach(window, place, true, false);
        normalize();
        return true;
    }

    /** Makes window {@code id} a Float of its place, or tiles it last again. */
    public boolean setFloat(long id, boolean floating) {
        TilingWindow window = window(id);
        Place place = placeOf(id);
        if (window == null || place == null || place.isHome() || window.floating == floating)
            return false;
        TilingWorkspace workspace = workspaces.get(place.workspaceNumber() - 1);
        TilingWindow focused = workspace.focused;
        removeFrom(workspace, window);
        attachTo(workspace, window, floating, false);
        if (focused != null && focused != window) focusIn(workspace, focused);
        return true;
    }

    /** Pins or unpins a Float, on a Workspace or a Home page: a pinned Float shows on every Workspace. */
    public boolean setPinned(long id, boolean pinned) {
        TilingWindow window = window(id);
        if (window == null || !window.floating) return false;
        window.pinned = pinned;
        return true;
    }

    /** Puts window {@code id} into {@code targetId}'s group as a tab, just after it. */
    public boolean joinTab(long id, long targetId) {
        return join(id, targetId, TileGroup.Mode.TABS);
    }

    /** Stacks window {@code id} into {@code targetId}'s column, just below it; Dwindle has no columns to stack. */
    public boolean stack(long id, long targetId) {
        return join(id, targetId, TileGroup.Mode.STACK);
    }

    // ---- focus -----------------------------------------------------------------------------------

    /** Goes to {@code place}, landing on the window it last had focused. */
    public boolean goTo(@NonNull Place place) {
        if (place.isHome()) {
            if (place.homePage() >= homeFloats.size()) return false;
            homePage = place.homePage();
            current = -1;
            return true;
        }
        if (place.workspaceNumber() > workspaces.size()) return false;
        current = place.workspaceNumber() - 1;
        return true;
    }

    /** Focuses window {@code id} and goes to its place. */
    public boolean focusWindow(long id) {
        TilingWindow window = window(id);
        Place place = placeOf(id);
        if (window == null || place == null) return false;
        goTo(place);
        if (place.isHome()) homeFocus.set(place.homePage(), window);
        else focusIn(workspaces.get(current), window);
        return true;
    }

    /** The home press: goes to Home, or to Workspace 1 when there is no Home. */
    public boolean home() {
        if (homeFloats.isEmpty()) {
            if (current == 0) return false;
            current = 0;
            return true;
        }
        if (current < 0) return false;
        current = -1;
        return true;
    }

    /** One step left: the window before in the Workspace, or the Home page before. */
    public boolean focusLeft() {
        return sideways(-1);
    }

    /** One step right: the window after in the Workspace, or the Home page after. */
    public boolean focusRight() {
        return sideways(1);
    }

    /** One step up: the window above in a stacked column, else the place above. */
    public boolean focusUp() {
        return vertical(-1);
    }

    /** One step down: the window below in a stacked column, else the place below. */
    public boolean focusDown() {
        return vertical(1);
    }

    // ---- naming and what layouts report ----------------------------------------------------------

    /** Names Workspace {@code number}; a blank name clears it. The name stays as numbers change. */
    public boolean renameWorkspace(int number, @Nullable String name) {
        TilingWorkspace workspace = workspace(number);
        if (workspace == null) return false;
        String trimmed = name == null ? "" : name.trim();
        workspace.name = trimmed.isEmpty() ? null : trimmed;
        return true;
    }

    /**
     * Sets Workspace {@code number}'s layout. Dwindle has no stacked columns: each stacked window
     * becomes a tile of its own, in order; tabs stay one tile. Back to Scrolling each tile is a
     * column. The layout state belongs to the layout it was kept for, so the caller clears it.
     */
    public boolean setLayout(int number, @NonNull WorkspaceLayout layout) {
        TilingWorkspace workspace = workspace(number);
        if (workspace == null) return false;
        workspace.layout = layout;
        if (layout == WorkspaceLayout.DWINDLE) unstack(workspace);
        return true;
    }

    /** Each stacked group of {@code workspace} split into one group per window, in order. */
    private static void unstack(@NonNull TilingWorkspace workspace) {
        for (int g = workspace.groups.size() - 1; g >= 0; g--) {
            TileGroup group = workspace.groups.get(g);
            if (group.mode != TileGroup.Mode.STACK || group.windows.size() < 2) continue;
            workspace.groups.remove(g);
            for (int i = group.windows.size() - 1; i >= 0; i--) {
                workspace.groups.add(g, new TileGroup(group.windows.get(i)));
            }
        }
    }

    /** Keeps the layout policy's own state for Workspace {@code number}; see {@link TilingWorkspace#layoutState()}. */
    public boolean setLayoutState(int number, @Nullable String state) {
        TilingWorkspace workspace = workspace(number);
        if (workspace == null) return false;
        workspace.layoutState = state;
        return true;
    }

    /** The area the layout gave window {@code id}; the biggest window is its Workspace's main one. */
    public boolean reportArea(long id, long area) {
        TilingWindow window = window(id);
        if (window == null) return false;
        window.area = Math.max(0, area);
        return true;
    }

    /** A shell's working directory as it changes; restored with it. */
    public boolean setWorkingDirectory(long id, @Nullable String directory) {
        TilingWindow window = window(id);
        if (window == null || window.kind() != WindowKind.SHELL) return false;
        window.workingDirectory = directory;
        return true;
    }

    /** Sets a Linux window's app id, learnt after it opened; false for another kind or no window. */
    public boolean setAppId(long id, @Nullable String appId) {
        TilingWindow window = window(id);
        if (window == null || window.kind() != WindowKind.LINUX_APP) return false;
        window.appId = appId;
        return true;
    }

    public boolean renameWindow(long id, @Nullable String name) {
        TilingWindow window = window(id);
        if (window == null) return false;
        window.name = name;
        return true;
    }

    /** Changes how many Home pages there are; Floats on a page that goes move to the last one left. */
    public void setHomePageCount(int count) {
        count = Math.max(0, count);
        while (homeFloats.size() < count) {
            homeFloats.add(new ArrayList<>());
            homeFocus.add(null);
        }
        List<TilingWindow> orphans = new ArrayList<>();
        TilingWindow shownFocus = null;
        TilingWindow anyFocus = null;
        while (homeFloats.size() > count) {
            int page = homeFloats.size() - 1;
            orphans.addAll(0, homeFloats.remove(page));
            TilingWindow focused = homeFocus.remove(page);
            if (focused != null) {
                if (anyFocus == null) anyFocus = focused;
                if (current < 0 && homePage == page) shownFocus = focused;
            }
        }
        for (TilingWindow window : orphans) {
            if (count > 0) homeFloats.get(count - 1).add(window);
            else attachTo(workspaces.get(workspaces.size() - 1), window, true, false);
        }
        if (count > 0) {
            // A focused Float that moves keeps focus: always when its page was in front, else
            // when the page it lands on had nothing focused.
            if (shownFocus != null) homeFocus.set(count - 1, shownFocus);
            else if (homeFocus.get(count - 1) == null && anyFocus != null) homeFocus.set(count - 1, anyFocus);
        }
        homePage = Math.min(homePage, Math.max(0, count - 1));
        if (count == 0 && current < 0) current = 0;
        normalize();
    }

    // ---- saved form ------------------------------------------------------------------------------

    /** The saved form: everything that comes back after a launcher restart. */
    @NonNull
    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("version", VERSION);
            json.put("nextId", nextId);
            json.put("homePages", homeFloats.size());
            JSONObject at = new JSONObject();
            if (current < 0) at.put("home", homePage);
            else at.put("workspace", current + 1);
            json.put("current", at);
            json.put("homePage", homePage);
            JSONArray workspaceArray = new JSONArray();
            for (TilingWorkspace workspace : workspaces) workspaceArray.put(workspaceJson(workspace));
            json.put("workspaces", workspaceArray);
            JSONArray pages = new JSONArray();
            for (int i = 0; i < homeFloats.size(); i++) {
                JSONObject page = new JSONObject();
                page.put("floats", windowsJson(homeFloats.get(i)));
                TilingWindow focused = homeFocus.get(i);
                if (focused != null) page.put("focused", focused.id());
                pages.put(page);
            }
            json.put("home", pages);
            return json;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads a saved form back. Windows it cannot read (an unknown kind, a repeated id) are left
     * out, and the stack's rules are applied again.
     */
    @NonNull
    public static TilingStack fromJson(@NonNull JSONObject json) throws SavedFormException {
        try {
            JSONArray workspaceArray = json.getJSONArray("workspaces");
            int homePages = json.optInt("homePages", 0);
            if (homePages < 0 || homePages > MAX_HOME_PAGES)
                throw new SavedFormException("Home pages out of range: " + homePages, null);
            if (workspaceArray.length() > MAX_WORKSPACES)
                throw new SavedFormException("Too many Workspaces: " + workspaceArray.length(), null);
            TilingStack stack = new TilingStack(homePages);
            stack.workspaces.clear();
            Set<Long> seen = new HashSet<>();
            long maxId = 0;
            for (int i = 0; i < workspaceArray.length(); i++) {
                JSONObject object = workspaceArray.optJSONObject(i);
                if (object == null) continue;
                TilingWorkspace workspace = new TilingWorkspace();
                workspace.name = object.has("name") ? object.optString("name", null) : null;
                workspace.layout = WorkspaceLayout.fromKey(object.optString("layout", null));
                workspace.layoutState = object.has("layoutState")
                    ? object.optString("layoutState", null) : null;
                JSONArray groupArray = object.optJSONArray("groups");
                for (int g = 0; groupArray != null && g < groupArray.length(); g++) {
                    JSONObject groupJson = groupArray.optJSONObject(g);
                    if (groupJson == null) continue;
                    TileGroup.Mode mode = TileGroup.Mode.fromKey(groupJson.optString("mode", null));
                    List<TilingWindow> windows = readWindows(groupJson.optJSONArray("windows"), seen, false);
                    // A stack holds two at most: an over-full one is split into columns of two.
                    int size = mode == TileGroup.Mode.STACK ? MAX_STACKED : Math.max(1, windows.size());
                    for (int from = 0; from < windows.size(); from += size) {
                        TileGroup group = new TileGroup();
                        group.mode = mode;
                        group.windows.addAll(windows.subList(from, Math.min(windows.size(), from + size)));
                        int active = groupJson.optInt("active", 0) - from;
                        group.active = Math.max(0, Math.min(active, group.windows.size() - 1));
                        if (group.windows.size() == 1) group.mode = TileGroup.Mode.STACK;
                        workspace.groups.add(group);
                    }
                }
                workspace.floats.addAll(readWindows(object.optJSONArray("floats"), seen, true));
                workspace.focused = findIn(workspace.windows(), object.optLong("focused", NO_WINDOW));
                if (workspace.focused == null && !workspace.groups.isEmpty())
                    workspace.focused = workspace.groups.get(0).active();
                stack.workspaces.add(workspace);
            }
            JSONArray pages = json.optJSONArray("home");
            for (int i = 0; pages != null && i < pages.length() && i < stack.homeFloats.size(); i++) {
                JSONObject page = pages.optJSONObject(i);
                if (page == null) continue;
                stack.homeFloats.get(i).addAll(readWindows(page.optJSONArray("floats"), seen, true));
                stack.homeFocus.set(i, findIn(stack.homeFloats.get(i), page.optLong("focused", NO_WINDOW)));
            }
            for (long id : seen) maxId = Math.max(maxId, id);
            stack.nextId = Math.max(json.optLong("nextId", 1), maxId + 1);
            JSONObject at = json.optJSONObject("current");
            int lastPage = Math.max(0, stack.homeFloats.size() - 1);
            stack.homePage = Math.max(0, Math.min(json.optInt("homePage", 0), lastPage));
            if (at != null && at.has("home") && !stack.homeFloats.isEmpty()) {
                stack.current = -1;
                stack.homePage = Math.max(0, Math.min(at.optInt("home", 0), lastPage));
            } else {
                int number = at == null ? 1 : at.optInt("workspace", 1);
                stack.current = Math.max(0, number - 1);
            }
            stack.normalize();
            return stack;
        } catch (SavedFormException e) {
            throw e;
        } catch (JSONException | RuntimeException e) {
            throw new SavedFormException("Not a saved tiling stack", e);
        }
    }

    // ---- the rules -------------------------------------------------------------------------------

    /**
     * Drops every empty Workspace and leaves one waiting at the end, renumbering the rest. A
     * Workspace in front that goes leaves the one that takes its number in front.
     */
    private void normalize() {
        TilingWorkspace front = current >= 0 && current < workspaces.size() ? workspaces.get(current) : null;
        int frontIndex = current;
        TilingWorkspace waiting = !workspaces.isEmpty() && workspaces.get(workspaces.size() - 1).isEmpty()
            ? workspaces.get(workspaces.size() - 1) : new TilingWorkspace();
        List<TilingWorkspace> kept = new ArrayList<>();
        int keptAboveFront = 0;
        for (int i = 0; i < workspaces.size(); i++) {
            TilingWorkspace workspace = workspaces.get(i);
            if (workspace.isEmpty()) continue;
            kept.add(workspace);
            if (i < frontIndex) keptAboveFront++;
        }
        kept.add(waiting);
        workspaces.clear();
        workspaces.addAll(kept);
        for (int i = 0; i < workspaces.size(); i++) workspaces.get(i).number = i + 1;
        if (current >= 0 || homeFloats.isEmpty()) {
            int index = workspaces.indexOf(front);
            current = index >= 0 ? index : Math.min(Math.max(0, keptAboveFront), workspaces.size() - 1);
        }
    }

    @NonNull
    private TilingWorkspace workspaceOrWaiting(int number) {
        TilingWorkspace workspace = workspace(number);
        return workspace != null ? workspace : workspaces.get(workspaces.size() - 1);
    }

    private void attach(@NonNull TilingWindow window, @NonNull Place place, boolean asFloat,
                        boolean afterFocused) {
        if (place.isHome() && place.homePage() < homeFloats.size()) {
            window.floating = true;
            window.pinned = false;
            homeFloats.get(place.homePage()).add(window);
            homeFocus.set(place.homePage(), window);
            return;
        }
        int number = place.isHome() ? workspaces.size() : place.workspaceNumber();
        attachTo(workspaceOrWaiting(number), window, asFloat, afterFocused);
    }

    /** Adds a window to {@code workspace} and makes it that Workspace's focused window. */
    private void attachTo(@NonNull TilingWorkspace workspace, @NonNull TilingWindow window,
                          boolean asFloat, boolean afterFocused) {
        window.floating = asFloat;
        if (!asFloat) window.pinned = false;
        if (asFloat) {
            workspace.floats.add(window);
        } else {
            int at = workspace.groups.size();
            if (afterFocused && workspace.focused != null && !workspace.focused.floating) {
                int focusedGroup = groupIndexOf(workspace, workspace.focused);
                if (focusedGroup >= 0) at = focusedGroup + 1;
            }
            workspace.groups.add(at, new TileGroup(window));
        }
        focusIn(workspace, window);
    }

    /** Takes a window out of wherever it is, handing its place's focus to a neighbour. */
    private void detach(@NonNull TilingWindow window) {
        for (int i = 0; i < homeFloats.size(); i++) {
            List<TilingWindow> page = homeFloats.get(i);
            int index = page.indexOf(window);
            if (index < 0) continue;
            page.remove(index);
            if (homeFocus.get(i) == window)
                homeFocus.set(i, page.isEmpty() ? null : page.get(Math.min(index, page.size() - 1)));
            return;
        }
        for (TilingWorkspace workspace : workspaces) {
            if (workspace.windows().contains(window)) {
                removeFrom(workspace, window);
                return;
            }
        }
    }

    /** Removes a window from a Workspace; if it had focus, focus goes to its neighbour. */
    private void removeFrom(@NonNull TilingWorkspace workspace, @NonNull TilingWindow window) {
        boolean hadFocus = workspace.focused == window;
        int unit = unitIndexOf(workspace, window);
        TilingWindow next = null;
        int floatIndex = workspace.floats.indexOf(window);
        if (floatIndex >= 0) {
            workspace.floats.remove(floatIndex);
        } else {
            int g = groupIndexOf(workspace, window);
            TileGroup group = workspace.groups.get(g);
            int index = group.windows.indexOf(window);
            group.windows.remove(index);
            if (group.windows.isEmpty()) {
                workspace.groups.remove(g);
            } else {
                if (group.active > index || group.active >= group.windows.size()) group.active--;
                group.active = Math.max(0, group.active);
                if (group.windows.size() == 1) group.mode = TileGroup.Mode.STACK;
                next = group.windows.get(Math.min(index, group.windows.size() - 1));
            }
        }
        window.pinned = window.pinned && window.floating;
        if (!hadFocus) return;
        if (next == null) {
            List<TilingWindow> unitsAfter = units(workspace);
            if (!unitsAfter.isEmpty() && unit >= 0)
                next = unitsAfter.get(Math.min(unit, unitsAfter.size() - 1));
            else if (!unitsAfter.isEmpty()) next = unitsAfter.get(0);
        }
        workspace.focused = null;
        if (next != null) focusIn(workspace, next);
    }

    private boolean join(long id, long targetId, @NonNull TileGroup.Mode mode) {
        if (id == targetId) return false;
        TilingWindow window = window(id);
        TilingWindow target = window(targetId);
        Place targetPlace = placeOf(targetId);
        if (window == null || target == null || targetPlace == null || targetPlace.isHome()
            || target.floating) return false;
        TilingWorkspace workspace = workspaces.get(targetPlace.workspaceNumber() - 1);
        TileGroup group = workspace.groups.get(groupIndexOf(workspace, target));
        int joined = group.windows.size() + (group.windows.contains(window) ? 0 : 1);
        if (mode == TileGroup.Mode.STACK
            && (joined > MAX_STACKED || workspace.layout == WorkspaceLayout.DWINDLE)) return false;
        Place from = placeOf(id);
        TilingWorkspace source = from == null || from.isHome() ? null : workspaces.get(from.workspaceNumber() - 1);
        boolean wasFocused = source != null && source.focused == window;
        // A tab that joins the group in focus is shown, so focus follows it.
        boolean groupHadFocus = workspace.focused != null && group.windows.contains(workspace.focused);
        detach(window);
        window.floating = false;
        window.pinned = false;
        group.windows.add(group.windows.indexOf(target) + 1, window);
        group.mode = mode;
        if (wasFocused || workspace.focused == null
            || (mode == TileGroup.Mode.TABS && groupHadFocus)) focusIn(workspace, window);
        else if (mode == TileGroup.Mode.TABS) group.active = group.windows.indexOf(window);
        normalize();
        return true;
    }

    private static void focusIn(@NonNull TilingWorkspace workspace, @NonNull TilingWindow window) {
        workspace.focused = window;
        int g = groupIndexOf(workspace, window);
        if (g >= 0) {
            TileGroup group = workspace.groups.get(g);
            group.active = group.windows.indexOf(window);
        }
    }

    private boolean sideways(int step) {
        if (current < 0) {
            int page = homePage + step;
            if (page < 0 || page >= homeFloats.size()) return false;
            homePage = page;
            return true;
        }
        TilingWorkspace workspace = workspaces.get(current);
        List<TilingWindow> units = units(workspace);
        if (units.isEmpty()) return false;
        int at = workspace.focused == null ? -1 : unitIndexOf(workspace, workspace.focused);
        int next = at < 0 ? (step > 0 ? 0 : units.size() - 1) : at + step;
        if (next < 0 || next >= units.size()) return false;
        focusIn(workspace, units.get(next));
        return true;
    }

    private boolean vertical(int step) {
        if (current >= 0) {
            TilingWorkspace workspace = workspaces.get(current);
            TilingWindow focused = workspace.focused;
            int g = focused == null ? -1 : groupIndexOf(workspace, focused);
            if (g >= 0 && workspace.groups.get(g).mode == TileGroup.Mode.STACK) {
                TileGroup group = workspace.groups.get(g);
                int index = group.windows.indexOf(focused) + step;
                if (index >= 0 && index < group.windows.size()) {
                    focusIn(workspace, group.windows.get(index));
                    return true;
                }
            }
        }
        int top = homeFloats.isEmpty() ? 0 : -1;
        int next = current + step;
        if (next < top || next >= workspaces.size()) return false;
        current = next;
        return true;
    }

    /** What a sideways step walks: each tile group (landing on its active window), then each Float. */
    @NonNull
    private static List<TilingWindow> units(@NonNull TilingWorkspace workspace) {
        List<TilingWindow> units = new ArrayList<>();
        for (TileGroup group : workspace.groups) units.add(group.active());
        units.addAll(workspace.floats);
        return units;
    }

    private static int unitIndexOf(@NonNull TilingWorkspace workspace, @NonNull TilingWindow window) {
        int g = groupIndexOf(workspace, window);
        if (g >= 0) return g;
        int f = workspace.floats.indexOf(window);
        return f < 0 ? -1 : workspace.groups.size() + f;
    }

    private static int groupIndexOf(@NonNull TilingWorkspace workspace, @NonNull TilingWindow window) {
        for (int i = 0; i < workspace.groups.size(); i++)
            if (workspace.groups.get(i).windows.contains(window)) return i;
        return -1;
    }

    @Nullable
    private static TilingWindow findIn(@NonNull List<TilingWindow> windows, long id) {
        for (TilingWindow window : windows) if (window.id() == id) return window;
        return null;
    }

    // ---- saved form helpers ----------------------------------------------------------------------

    @NonNull
    private static JSONObject workspaceJson(@NonNull TilingWorkspace workspace) throws JSONException {
        JSONObject json = new JSONObject();
        if (workspace.name != null) json.put("name", workspace.name);
        json.put("layout", workspace.layout.key);
        if (workspace.layoutState != null) json.put("layoutState", workspace.layoutState);
        if (workspace.focused != null) json.put("focused", workspace.focused.id());
        JSONArray groups = new JSONArray();
        for (TileGroup group : workspace.groups) {
            JSONObject groupJson = new JSONObject();
            groupJson.put("mode", group.mode.key);
            groupJson.put("active", group.active);
            groupJson.put("windows", windowsJson(group.windows));
            groups.put(groupJson);
        }
        json.put("groups", groups);
        json.put("floats", windowsJson(workspace.floats));
        return json;
    }

    @NonNull
    private static JSONArray windowsJson(@NonNull List<TilingWindow> windows) throws JSONException {
        JSONArray array = new JSONArray();
        for (TilingWindow window : windows) {
            JSONObject json = new JSONObject();
            json.put("id", window.id());
            json.put("kind", window.kind().key);
            if (window.name != null) json.put("name", window.name);
            if (window.workingDirectory != null) json.put("dir", window.workingDirectory);
            if (window.appId != null) json.put("appId", window.appId);
            if (window.area > 0) json.put("area", window.area);
            if (window.pinned) json.put("pinned", true);
            array.put(json);
        }
        return array;
    }

    @NonNull
    private static List<TilingWindow> readWindows(@Nullable JSONArray array, @NonNull Set<Long> seen,
                                                  boolean floating) {
        List<TilingWindow> windows = new ArrayList<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;
            long id = json.optLong("id", NO_WINDOW);
            WindowKind kind = WindowKind.fromKey(json.optString("kind", null));
            if (id <= 0 || kind == null || !seen.add(id)) continue;
            String appId = json.has("appId") ? json.optString("appId", null) : null;
            if (kind == WindowKind.LINUX_APP && appId == null) continue;
            TilingWindow window = new TilingWindow(id, kind,
                json.has("name") ? json.optString("name", null) : null,
                json.has("dir") ? json.optString("dir", null) : null, appId);
            window.area = Math.max(0, json.optLong("area", 0));
            window.floating = floating;
            window.pinned = floating && json.optBoolean("pinned", false);
            windows.add(window);
        }
        return windows;
    }
}
