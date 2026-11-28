package com.termux.app.controlbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.TilingWorkspace;
import com.termux.app.tiling.WindowKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Workspace list's rows (#429), pure: Home first when there is Home (round 2, as the
 * prototype lists it), then one per Workspace, in order, each with its number, its main window's
 * kind (the icon), its own name if it has one, how many windows it holds, and the place in front
 * marked. The waiting Workspace (the last, empty) is the list's "New Workspace".
 */
public final class WorkspaceListRows {

    /** One Workspace in the list. */
    public static final class Row {
        public final int number;
        /** Its name, or null when it has none (it is spoken and drawn as its number). */
        @Nullable public final String name;
        /** The main window's kind, or null for an empty Workspace. */
        @Nullable public final WindowKind kind;
        /** The main window's Linux app id, for that app's own icon; null for none. */
        @Nullable public final String appId;
        public final boolean front;
        /** The waiting Workspace: empty and last, shown as "New Workspace". */
        public final boolean waiting;
        /** Home's row: {@link #number} is 0 and it has no name, kind or count of its own. */
        public final boolean home;
        /** How many windows the Workspace holds (its Floats and minimised ones too); 0 for Home. */
        public final int windows;
        @NonNull private final Place mPlace;

        Row(int number, @Nullable String name, @Nullable WindowKind kind, @Nullable String appId,
            boolean front, boolean waiting, int windows) {
            this.number = number;
            this.name = name;
            this.kind = kind;
            this.appId = appId;
            this.front = front;
            this.waiting = waiting;
            this.home = false;
            this.windows = windows;
            mPlace = Place.workspace(number);
        }

        private Row(@NonNull Place home, boolean front) {
            this.number = 0;
            this.name = null;
            this.kind = null;
            this.appId = null;
            this.front = front;
            this.waiting = false;
            this.home = true;
            this.windows = 0;
            mPlace = home;
        }

        /** Where letting go on this row goes. */
        @NonNull
        public Place place() {
            return mPlace;
        }

        /** Whether a long press can rename it (Home and the waiting Workspace have nothing to name). */
        public boolean renamable() {
            return !waiting && !home;
        }
    }

    private WorkspaceListRows() {}

    /**
     * Whether two lists are the same Workspaces to the eye and by number: a list drawn from
     * {@code shown} is still true of {@code now}, so a number on it can be gone to or renamed.
     */
    public static boolean same(@NonNull List<Row> shown, @NonNull List<Row> now) {
        if (shown.size() != now.size()) return false;
        for (int i = 0; i < shown.size(); i++) {
            Row a = shown.get(i);
            Row b = now.get(i);
            if (a.number != b.number || a.front != b.front || a.waiting != b.waiting
                || a.home != b.home || a.windows != b.windows || !a.mPlace.equals(b.mPlace)
                || a.kind != b.kind || !java.util.Objects.equals(a.name, b.name)
                || !java.util.Objects.equals(a.appId, b.appId)) return false;
        }
        return true;
    }

    /** The rows for {@code stack} as it stands. */
    @NonNull
    public static List<Row> of(@NonNull TilingStack stack) {
        List<Row> rows = new ArrayList<>();
        Place current = stack.current();
        if (stack.homePageCount() > 0) rows.add(new Row(Place.home(stack.homePage()), current.isHome()));
        int count = stack.workspaceCount();
        for (int number = 1; number <= count; number++) {
            TilingWorkspace workspace = stack.workspace(number);
            if (workspace == null) continue;
            TilingWindow main = workspace.mainWindow();
            rows.add(new Row(number, workspace.name(), main == null ? null : main.kind(),
                main == null ? null : main.appId(),
                !current.isHome() && current.workspaceNumber() == number,
                number == count && workspace.isEmpty(), workspace.windows().size()));
        }
        return Collections.unmodifiableList(rows);
    }

    /**
     * The row a finger is over: the row whose span holds {@code y}, spans stacked from {@code top}
     * {@code rowHeight} apiece; -1 above, below or with no rows.
     */
    public static int indexAt(float y, float top, float rowHeight, int count) {
        if (count <= 0 || rowHeight <= 0f || y < top) return -1;
        int index = (int) ((y - top) / rowHeight);
        return index < count ? index : -1;
    }
}
