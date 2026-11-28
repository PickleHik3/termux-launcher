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
 * The Workspace list's rows (#429), pure: one per Workspace, in order, each with its number, its
 * main window's kind (the icon), its own name if it has one, and the Workspace in front marked.
 * The waiting Workspace (the last, empty) is the list's "New Workspace". Home is not in the list.
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

        Row(int number, @Nullable String name, @Nullable WindowKind kind, @Nullable String appId,
            boolean front, boolean waiting) {
            this.number = number;
            this.name = name;
            this.kind = kind;
            this.appId = appId;
            this.front = front;
            this.waiting = waiting;
        }

        /** Where letting go on this row goes. */
        @NonNull
        public Place place() {
            return Place.workspace(number);
        }

        /** Whether a long press can rename it (the waiting Workspace has nothing to name). */
        public boolean renamable() {
            return !waiting;
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
        int count = stack.workspaceCount();
        for (int number = 1; number <= count; number++) {
            TilingWorkspace workspace = stack.workspace(number);
            if (workspace == null) continue;
            TilingWindow main = workspace.mainWindow();
            rows.add(new Row(number, workspace.name(), main == null ? null : main.kind(),
                main == null ? null : main.appId(),
                !current.isHome() && current.workspaceNumber() == number,
                number == count && workspace.isEmpty()));
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
