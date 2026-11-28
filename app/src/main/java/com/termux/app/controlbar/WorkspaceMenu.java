package com.termux.app.controlbar;

import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.tiling.WorkspaceLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Workspace's menu (#420), opened by a hold on the control bar's place badge: its Layout rows,
 * Scrolling and Dwindle, the one in use checked, then Save Workspace (#431) when it has windows.
 * The Workspace list (#429) and the window menu (#425) add their rows here.
 */
final class WorkspaceMenu {

    /** One row: what it sets and whether it is the one in use. */
    static final class Row {
        @NonNull final WorkspaceLayout layout;
        @StringRes final int label;
        final boolean checked;

        Row(@NonNull WorkspaceLayout layout, @StringRes int label, boolean checked) {
            this.layout = layout;
            this.label = label;
            this.checked = checked;
        }
    }

    /** What a chosen row asks for. */
    interface Choice {
        void onLayoutChosen(@NonNull WorkspaceLayout layout);

        /** Save Workspace was chosen. */
        void onSaveChosen();
    }

    private static final int GROUP_LAYOUT = 1;
    private static final int GROUP_SAVE = 2;

    private WorkspaceMenu() {}

    /** The Layout rows for a Workspace laid out by {@code current}. */
    @NonNull
    static List<Row> layoutRows(@NonNull WorkspaceLayout current) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(WorkspaceLayout.SCROLLING, R.string.workspace_layout_scrolling,
            current == WorkspaceLayout.SCROLLING));
        rows.add(new Row(WorkspaceLayout.DWINDLE, R.string.workspace_layout_dwindle,
            current == WorkspaceLayout.DWINDLE));
        return Collections.unmodifiableList(rows);
    }

    /**
     * Shows the menu over {@code anchor} for a Workspace laid out by {@code current}; Save
     * Workspace only when {@code canSave} (it has windows). {@code onDismiss} runs as it goes.
     */
    static void show(@NonNull View anchor, @NonNull WorkspaceLayout current, boolean canSave,
                     @NonNull Choice choice, @NonNull Runnable onDismiss) {
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor, Gravity.START);
        Menu menu = popup.getMenu();
        MenuItem heading = menu.add(Menu.NONE, Menu.NONE, 0, R.string.workspace_menu_layout);
        heading.setEnabled(false);
        List<Row> rows = layoutRows(current);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            menu.add(GROUP_LAYOUT, i, i + 1, row.label).setCheckable(true).setChecked(row.checked);
        }
        menu.setGroupCheckable(GROUP_LAYOUT, true, true);
        if (canSave) menu.add(GROUP_SAVE, 0, rows.size() + 1, R.string.workspace_menu_save);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getGroupId() == GROUP_SAVE) {
                choice.onSaveChosen();
                return true;
            }
            if (item.getGroupId() != GROUP_LAYOUT || item.getItemId() < 0 || item.getItemId() >= rows.size()) {
                return false;
            }
            choice.onLayoutChosen(rows.get(item.getItemId()).layout);
            return true;
        });
        popup.setOnDismissListener(menu1 -> onDismiss.run());
        popup.show();
    }
}
