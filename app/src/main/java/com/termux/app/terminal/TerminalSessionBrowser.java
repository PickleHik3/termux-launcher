package com.termux.app.terminal;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Searchable surface for inspecting and managing sessions, windows, and panes, and everything it
 * prompts for on the way.
 *
 * <p>All of it runs on {@link TerminalSheetController} rather than on {@code AlertDialog} and
 * {@code PopupMenu}. The browser was the last terminal surface that opened windows of its own, and
 * its search box was the worst of them: a focused {@code EditText} took the {@code InputConnection}
 * off {@code TerminalView}, which collapsed the in-app keyboard and resized the terminal twice just
 * to type a filter. On the sheet plane nothing takes focus and the search field is typed from the
 * key channel, exactly like the palette and the rename chip.
 */
public final class TerminalSessionBrowser {

    private TerminalSessionBrowser() {}

    /** Shows the browser. Must be called on the main thread. */
    public static void show(@NonNull TermuxActivity activity) {
        View container = activity.getLayoutInflater().inflate(R.layout.session_browser, null);
        TextView search = container.findViewById(R.id.session_browser_search);
        ListView list = container.findViewById(R.id.session_browser_list);
        TextView empty = container.findViewById(R.id.session_browser_empty);
        BrowserAdapter adapter = new BrowserAdapter(activity);
        list.setAdapter(adapter);
        list.setEmptyView(empty);
        TerminalSheetController sheet = activity.getTerminalSheetController();

        list.setOnItemClickListener((parent, view, position, id) -> {
            SessionBrowserModel.Session session = adapter.entryAt(position);
            // Activating leaves the browser describing a layout the user has just left, so the
            // whole stack goes rather than only this card.
            if (session != null && activity.activateBrowserSession(session.index)) sheet.dismissAll();
        });

        container.findViewById(R.id.session_browser_new).setOnClickListener(v -> {
            if (activity.createBrowserSession()) adapter.reload();
            else showActionFailed(activity);
        });
        container.findViewById(R.id.session_browser_clone).setOnClickListener(v -> {
            if (activity.cloneCurrentBrowserSession()) adapter.reload();
            else showActionFailed(activity);
        });
        container.findViewById(R.id.session_browser_save).setOnClickListener(v -> promptSave(activity));

        adapter.setMenuListener((anchor, session) -> showSessionMenu(activity, adapter, session));
        // Subscribed only once the sheet is actually up: a callback registered against a surface
        // that never opened would keep reloading a browser nobody can see.
        if (!sheet.show(activity.getString(R.string.session_browser_title), container, true,
            new TerminalSheetController.TextField(search,
                activity.getString(R.string.session_browser_search_hint), adapter::setQuery),
            () -> activity.setSessionBrowserRefreshCallback(null))) return;
        activity.setSessionBrowserRefreshCallback(adapter::reload);
        activity.requestSessionBrowserForegroundRefresh();
    }

    /**
     * The per-row actions, as a sheet stacked on the browser rather than as a {@code PopupMenu}
     * anchored to the row. The anchor is gone with the popup: a menu window over a plane that is
     * itself an in-activity view had no reason to be a window.
     */
    private static void showSessionMenu(@NonNull TermuxActivity activity,
                                        @NonNull BrowserAdapter adapter,
                                        @NonNull SessionBrowserModel.Session session) {
        TerminalSheetController sheet = activity.getTerminalSheetController();
        LinearLayout body = TerminalSheetViews.body(activity);
        TerminalSheetViews.addMenuRow(body, activity.getString(R.string.session_browser_activate), () -> {
            if (activity.activateBrowserSession(session.index)) sheet.dismissAll();
            else showActionFailed(activity);
        });
        TerminalSheetViews.addMenuRow(body, activity.getString(R.string.session_browser_clone), () -> {
            sheet.dismiss();
            if (activity.cloneBrowserSession(session.index)) adapter.reload();
            else showActionFailed(activity);
        });
        TerminalSheetViews.addMenuRow(body, activity.getString(R.string.session_browser_rename),
            () -> promptRename(activity, adapter, session));
        TerminalSheetViews.addMenuRow(body, activity.getString(R.string.session_browser_close), () -> {
            sheet.dismiss();
            confirmClose(activity, adapter, session);
        });
        sheet.show(displayName(activity, session), body);
    }

    /**
     * Renames through the anchored editor rather than a prompt, so the browser's rename costs no
     * system-IME swap either. {@code beginTerminalRename} closes the sheet stack on the way — the
     * chip anchors to the session indicator, which sits behind a modal sheet — and the adapter
     * reloads when the editor ends, through the activity's own refresh.
     */
    private static void promptRename(@NonNull TermuxActivity activity,
                                     @NonNull BrowserAdapter adapter,
                                     @NonNull SessionBrowserModel.Session session) {
        if (activity.beginSessionRenameAtIndex(session.index)) adapter.reload();
        else showActionFailed(activity);
    }

    private static void confirmClose(@NonNull TermuxActivity activity,
                                     @NonNull BrowserAdapter adapter,
                                     @NonNull SessionBrowserModel.Session session) {
        TerminalSheetController sheet = activity.getTerminalSheetController();
        LinearLayout body = TerminalSheetViews.body(activity);
        TerminalSheetViews.addMessage(body, activity.getResources().getQuantityString(
            R.plurals.session_browser_close_message, session.paneCount(), session.paneCount()));
        LinearLayout actions = TerminalSheetViews.addActionRow(body);
        TerminalSheetViews.addAction(actions, activity.getString(android.R.string.cancel), sheet::dismiss);
        TerminalSheetViews.addAction(actions, activity.getString(R.string.session_browser_close), () -> {
            sheet.dismiss();
            if (activity.closeBrowserSession(session.index)) adapter.reload();
            else showActionFailed(activity);
        });
        sheet.show(activity.getString(R.string.session_browser_close_title,
            displayName(activity, session)), body);
    }

    /** Extra-keys/palette entry: the same save-name prompt the browser's Save button shows. */
    public static void promptSaveWorkspace(@NonNull TermuxActivity activity) {
        promptSave(activity);
    }

    /**
     * Picker over the saved workspaces: tapping a name asks whether to load it in place of the
     * live workspace or append its windows, then loads without running captured commands.
     */
    public static void showWorkspacePicker(@NonNull TermuxActivity activity) {
        List<com.termux.app.terminal.TerminalWorkspaceStore.Entry> entries;
        try {
            entries = activity.listWorkspaces();
        } catch (TerminalWorkspace.WorkspaceException e) {
            Toast.makeText(activity, activity.getString(R.string.workspace_picker_failed,
                e.getMessage()), Toast.LENGTH_SHORT).show();
            return;
        }
        if (entries.isEmpty()) {
            Toast.makeText(activity, R.string.workspace_picker_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        for (com.termux.app.terminal.TerminalWorkspaceStore.Entry entry : entries) {
            TerminalSheetViews.addToFrame(list, workspaceRow(activity, entry.name));
        }
        activity.getTerminalSheetController().show(
            activity.getString(R.string.workspace_picker_title), TerminalSheetViews.wrapScrolling(list));
    }

    /** One picker row: the name loads it, the trailing button deletes it. */
    @NonNull
    private static View workspaceRow(@NonNull TermuxActivity activity, @NonNull String name) {
        TerminalSheetController sheet = activity.getTerminalSheetController();
        int density = Math.round(activity.getResources().getDisplayMetrics().density);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(activity);
        label.setText(name);
        label.setTextSize(16f);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        label.setMinHeight(48 * density);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setOnClickListener(v -> {
            sheet.dismiss();
            promptWorkspaceLoadMode(activity, name);
        });
        row.addView(label, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton delete = new ImageButton(activity);
        delete.setImageResource(R.drawable.ic_delete_sweep_24);
        delete.setBackgroundColor(0x00000000);
        delete.setContentDescription(activity.getString(R.string.workspace_delete_description, name));
        delete.setOnClickListener(v -> {
            sheet.dismiss();
            promptWorkspaceDelete(activity, name);
        });
        row.addView(delete, new LinearLayout.LayoutParams(40 * density, 40 * density));
        return row;
    }

    /** Deleting a workspace file cannot be undone, so it is always confirmed by name. */
    private static void promptWorkspaceDelete(@NonNull TermuxActivity activity,
                                              @NonNull String name) {
        TerminalSheetController sheet = activity.getTerminalSheetController();
        LinearLayout body = TerminalSheetViews.body(activity);
        TerminalSheetViews.addMessage(body, activity.getString(R.string.workspace_delete_message));
        LinearLayout actions = TerminalSheetViews.addActionRow(body);
        TerminalSheetViews.addAction(actions, activity.getString(android.R.string.cancel), () -> {
            sheet.dismiss();
            showWorkspacePicker(activity);
        });
        TerminalSheetViews.addAction(actions, activity.getString(R.string.workspace_delete_confirm), () -> {
            sheet.dismiss();
            try {
                activity.deleteWorkspace(name);
                Toast.makeText(activity, activity.getString(
                    R.string.workspace_deleted, name), Toast.LENGTH_SHORT).show();
            } catch (TerminalWorkspace.WorkspaceException e) {
                Toast.makeText(activity, activity.getString(
                    R.string.workspace_picker_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
            showWorkspacePicker(activity);
        });
        sheet.show(activity.getString(R.string.workspace_delete_title, name), body);
    }

    private static void promptWorkspaceLoadMode(@NonNull TermuxActivity activity,
                                                @NonNull String name) {
        // Only offer to run commands when the workspace actually carries some. Reading the file
        // here keeps the offer honest; a workspace saved without capture never shows the box.
        int commandCount = 0;
        try {
            commandCount = new TerminalWorkspaceStore().load(name).commandCount();
        } catch (TerminalWorkspace.WorkspaceException ignored) {
            // Loading proper will surface the failure; the checkbox simply stays hidden.
        }
        TerminalSheetController sheet = activity.getTerminalSheetController();
        LinearLayout body = TerminalSheetViews.body(activity);
        TerminalSheetViews.addMessage(body, activity.getString(R.string.workspace_picker_mode_message, name));
        final CheckBox runCommands = commandCount == 0 ? null : addCheckBox(body,
            activity.getResources().getQuantityString(
                R.plurals.workspace_load_run_commands, commandCount, commandCount),
            activity.getString(R.string.workspace_load_run_commands_summary));
        LinearLayout actions = TerminalSheetViews.addActionRow(body);
        TerminalSheetViews.addAction(actions, activity.getString(android.R.string.cancel), sheet::dismiss);
        TerminalSheetViews.addAction(actions, activity.getString(R.string.workspace_picker_append), () -> {
            sheet.dismiss();
            loadWorkspace(activity, name, false, isChecked(runCommands));
        });
        TerminalSheetViews.addAction(actions, activity.getString(R.string.workspace_picker_replace), () -> {
            sheet.dismiss();
            loadWorkspace(activity, name, true, isChecked(runCommands));
        });
        sheet.show(name, body);
    }

    private static void loadWorkspace(@NonNull TermuxActivity activity, @NonNull String name,
                                      boolean replace, boolean runCommands) {
        try {
            activity.loadWorkspace(name, replace, runCommands);
            Toast.makeText(activity, activity.getString(R.string.workspace_picker_loaded, name),
                Toast.LENGTH_SHORT).show();
        } catch (TerminalWorkspace.WorkspaceException e) {
            Toast.makeText(activity, activity.getString(R.string.workspace_picker_failed,
                e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private static void promptSave(@NonNull TermuxActivity activity) {
        TerminalSheetController sheet = activity.getTerminalSheetController();
        LinearLayout body = TerminalSheetViews.body(activity);
        TextView nameField = new TextView(activity);
        nameField.setTextSize(16f);
        nameField.setSingleLine(true);
        nameField.setMinHeight(Math.round(
            44 * activity.getResources().getDisplayMetrics().density));
        nameField.setGravity(Gravity.CENTER_VERTICAL);
        TerminalSheetViews.addToFrame(body, nameField);
        CheckBox captureCommands = addCheckBox(body,
            activity.getString(R.string.workspace_save_capture_commands),
            activity.getString(R.string.workspace_save_capture_commands_summary));
        // Held in a one-slot array because the field and the action that reads it are mutually
        // recursive: ⏎ on the field saves, and saving has to ask the field for the name.
        final TerminalSheetController.TextField[] field = new TerminalSheetController.TextField[1];
        Runnable save = () -> {
            String name = field[0].value();
            sheet.dismiss();
            saveWorkspace(activity, name, false, captureCommands.isChecked());
        };
        field[0] = new TerminalSheetController.TextField(nameField,
            activity.getString(R.string.session_browser_workspace_name), null, save);
        LinearLayout actions = TerminalSheetViews.addActionRow(body);
        TerminalSheetViews.addAction(actions, activity.getString(android.R.string.cancel), sheet::dismiss);
        TerminalSheetViews.addAction(actions, activity.getString(android.R.string.ok), save);
        sheet.show(activity.getString(R.string.session_browser_workspace_name), body, false,
            field[0], null);
    }

    private static boolean isChecked(@Nullable CheckBox box) {
        return box != null && box.isChecked();
    }

    /**
     * An unchecked box with the explanation its consequence needs stacked underneath, since both
     * of these boxes change what happens to the user's shells rather than just what is stored.
     */
    @NonNull
    private static CheckBox addCheckBox(@NonNull LinearLayout frame, @NonNull String title,
                                        @NonNull String summary) {
        Context context = frame.getContext();
        int density = Math.round(context.getResources().getDisplayMetrics().density);
        CheckBox box = new CheckBox(context);
        box.setText(title);
        TerminalSheetViews.addToFrame(frame, box);

        TextView explanation = new TextView(context);
        explanation.setText(summary);
        explanation.setTextSize(12f);
        explanation.setAlpha(0.7f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 8 * density;
        frame.addView(explanation, params);
        return box;
    }

    private static void saveWorkspace(@NonNull TermuxActivity activity, @Nullable String name,
                                      boolean overwrite, final boolean captureCommands) {
        try {
            TerminalWorkspace workspace = activity.saveWorkspace(name == null ? "" : name,
                overwrite, captureCommands);
            Toast.makeText(activity,
                activity.getString(R.string.session_browser_workspace_saved, workspace.name),
                Toast.LENGTH_SHORT).show();
        } catch (TerminalWorkspace.WorkspaceException e) {
            if (!overwrite && "conflict".equals(e.code)) {
                final String requested = name == null ? "" : name.trim();
                TerminalSheetController sheet = activity.getTerminalSheetController();
                LinearLayout body = TerminalSheetViews.body(activity);
                TerminalSheetViews.addMessage(body, activity.getString(
                    R.string.session_browser_workspace_overwrite_message));
                LinearLayout actions = TerminalSheetViews.addActionRow(body);
                TerminalSheetViews.addAction(actions, activity.getString(android.R.string.cancel), sheet::dismiss);
                TerminalSheetViews.addAction(actions, activity.getString(R.string.session_browser_overwrite), () -> {
                    sheet.dismiss();
                    saveWorkspace(activity, requested, true, captureCommands);
                });
                sheet.show(activity.getString(
                    R.string.session_browser_workspace_overwrite_title, requested), body);
            } else {
                Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void showActionFailed(@NonNull Context context) {
        Toast.makeText(context, R.string.session_browser_action_failed, Toast.LENGTH_SHORT).show();
    }

    @NonNull
    private static String displayName(@NonNull Context context,
                                      @NonNull SessionBrowserModel.Session session) {
        return TextUtils.isEmpty(session.name)
            ? context.getString(R.string.session_browser_unnamed, session.index + 1)
            : context.getString(R.string.session_browser_named, session.index + 1, session.name);
    }

    private interface MenuListener {
        void onMenu(@NonNull View anchor, @NonNull SessionBrowserModel.Session session);
    }

    private static final class BrowserAdapter extends BaseAdapter {
        @NonNull private final TermuxActivity activity;
        @NonNull private List<SessionBrowserModel.Session> all = new ArrayList<>();
        @NonNull private List<SessionBrowserModel.Session> filtered = new ArrayList<>();
        @NonNull private String query = "";
        @Nullable private MenuListener menuListener;

        BrowserAdapter(@NonNull TermuxActivity activity) {
            this.activity = activity;
            reload();
        }

        void setMenuListener(@Nullable MenuListener listener) {
            menuListener = listener;
        }

        void setQuery(@Nullable String value) {
            query = value == null ? "" : value;
            filtered = SessionBrowserModel.filter(all, query);
            notifyDataSetChanged();
        }

        void reload() {
            all = activity.getSessionBrowserSessions();
            filtered = SessionBrowserModel.filter(all, query);
            notifyDataSetChanged();
        }

        @Nullable
        SessionBrowserModel.Session entryAt(int position) {
            return position >= 0 && position < filtered.size() ? filtered.get(position) : null;
        }

        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int position) { return entryAt(position); }
        @Override public long getItemId(int position) {
            SessionBrowserModel.Session session = entryAt(position);
            return session == null ? position : session.id;
        }
        @Override public boolean hasStableIds() { return true; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(activity).inflate(R.layout.session_browser_row, parent, false);
            }
            SessionBrowserModel.Session session = filtered.get(position);
            TextView title = row.findViewById(R.id.session_browser_row_title);
            TextView subtitle = row.findViewById(R.id.session_browser_row_subtitle);
            TextView more = row.findViewById(R.id.session_browser_row_more);
            String displayName = displayName(activity, session);
            title.setText(session.current
                ? displayName + " · " + activity.getString(R.string.session_browser_current)
                : displayName);
            title.setTypeface(null, session.current ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
            subtitle.setText(buildSubtitle(activity, session));
            more.setOnClickListener(v -> {
                if (menuListener != null) menuListener.onMenu(v, session);
            });
            return row;
        }
    }

    @NonNull
    private static String buildSubtitle(@NonNull Context context,
                                        @NonNull SessionBrowserModel.Session session) {
        String windows = context.getResources().getQuantityString(R.plurals.session_browser_window_count,
            session.windows.size(), session.windows.size());
        String panes = context.getResources().getQuantityString(R.plurals.session_browser_pane_count,
            session.paneCount(), session.paneCount());
        StringBuilder out = new StringBuilder(windows).append(" · ").append(panes);
        for (SessionBrowserModel.Window window : session.windows) {
            out.append('\n').append(context.getString(R.string.session_browser_window,
                window.index + 1));
            if (window.current) out.append(" •");
            for (SessionBrowserModel.Pane pane : window.panes) {
                out.append("  ");
                if (pane.cwd != null) out.append(SessionBrowserModel.displayCwd(pane.cwd));
                if (pane.foreground != null) {
                    if (pane.cwd != null) out.append(" · ");
                    out.append(pane.foreground);
                }
                if (pane.cwd == null && pane.foreground == null) out.append('—');
            }
        }
        return out.toString();
    }
}
