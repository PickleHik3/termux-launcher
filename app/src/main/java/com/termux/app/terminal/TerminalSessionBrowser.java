package com.termux.app.terminal;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.interact.TextInputDialogUtils;

import java.util.ArrayList;
import java.util.List;

/** Searchable Material surface for inspecting and managing sessions, windows, and panes. */
public final class TerminalSessionBrowser {

    private static final int MENU_ACTIVATE = 1;
    private static final int MENU_CLONE = 2;
    private static final int MENU_RENAME = 3;
    private static final int MENU_CLOSE = 4;

    private TerminalSessionBrowser() {}

    /** Shows the browser. Must be called on the main thread. */
    public static void show(@NonNull TermuxActivity activity) {
        View container = activity.getLayoutInflater().inflate(R.layout.session_browser, null);
        EditText search = container.findViewById(R.id.session_browser_search);
        ListView list = container.findViewById(R.id.session_browser_list);
        TextView empty = container.findViewById(R.id.session_browser_empty);
        BrowserAdapter adapter = new BrowserAdapter(activity);
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.session_browser_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                adapter.setQuery(editable.toString());
            }
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            SessionBrowserModel.Session session = adapter.entryAt(position);
            if (session != null && activity.activateBrowserSession(session.index)) dialog.dismiss();
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

        adapter.setMenuListener((anchor, session) -> showSessionMenu(activity, adapter, dialog,
            anchor, session));
        activity.setSessionBrowserRefreshCallback(adapter::reload);
        dialog.setOnDismissListener(ignored -> activity.setSessionBrowserRefreshCallback(null));
        dialog.show();
        activity.requestSessionBrowserForegroundRefresh();
    }

    private static void showSessionMenu(@NonNull TermuxActivity activity,
                                        @NonNull BrowserAdapter adapter,
                                        @NonNull AlertDialog browser,
                                        @NonNull View anchor,
                                        @NonNull SessionBrowserModel.Session session) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        Menu menu = popup.getMenu();
        menu.add(Menu.NONE, MENU_ACTIVATE, 0, R.string.session_browser_activate);
        menu.add(Menu.NONE, MENU_CLONE, 1, R.string.session_browser_clone);
        menu.add(Menu.NONE, MENU_RENAME, 2, R.string.session_browser_rename);
        menu.add(Menu.NONE, MENU_CLOSE, 3, R.string.session_browser_close);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_ACTIVATE:
                    if (activity.activateBrowserSession(session.index)) browser.dismiss();
                    else showActionFailed(activity);
                    return true;
                case MENU_CLONE:
                    if (activity.cloneBrowserSession(session.index)) adapter.reload();
                    else showActionFailed(activity);
                    return true;
                case MENU_RENAME:
                    promptRename(activity, adapter, session);
                    return true;
                case MENU_CLOSE:
                    confirmClose(activity, adapter, session);
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private static void promptRename(@NonNull TermuxActivity activity,
                                     @NonNull BrowserAdapter adapter,
                                     @NonNull SessionBrowserModel.Session session) {
        TextInputDialogUtils.textInput(activity, R.string.title_rename_window_session, session.name,
            R.string.action_rename_session_confirm, text -> {
                if (activity.renameBrowserSession(session.index, text)) adapter.reload();
                else showActionFailed(activity);
            }, -1, null, -1, null, null);
    }

    private static void confirmClose(@NonNull TermuxActivity activity,
                                     @NonNull BrowserAdapter adapter,
                                     @NonNull SessionBrowserModel.Session session) {
        String title = displayName(activity, session);
        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.session_browser_close_title, title))
            .setMessage(activity.getResources().getQuantityString(
                R.plurals.session_browser_close_message, session.paneCount(), session.paneCount()))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.session_browser_close, (dialog, which) -> {
                if (activity.closeBrowserSession(session.index)) adapter.reload();
                else showActionFailed(activity);
            })
            .show();
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
        LinearLayout list = dialogFrame(activity);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.workspace_picker_title)
            .setView(wrapScrolling(list))
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        for (com.termux.app.terminal.TerminalWorkspaceStore.Entry entry : entries) {
            addToFrame(list, workspaceRow(activity, entry.name, dialog));
        }
        dialog.show();
    }

    /** One picker row: the name loads it, the trailing button deletes it. */
    @NonNull
    private static View workspaceRow(@NonNull TermuxActivity activity, @NonNull String name,
                                     @NonNull AlertDialog picker) {
        int density = Math.round(activity.getResources().getDisplayMetrics().density);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView label = new TextView(activity);
        label.setText(name);
        label.setTextSize(16f);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        label.setMinHeight(48 * density);
        label.setGravity(android.view.Gravity.CENTER_VERTICAL);
        label.setOnClickListener(v -> {
            picker.dismiss();
            promptWorkspaceLoadMode(activity, name);
        });
        row.addView(label, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton delete = new ImageButton(activity);
        delete.setImageResource(R.drawable.ic_delete_sweep_24);
        delete.setBackgroundColor(0x00000000);
        delete.setContentDescription(activity.getString(R.string.workspace_delete_description, name));
        delete.setOnClickListener(v -> {
            picker.dismiss();
            promptWorkspaceDelete(activity, name);
        });
        row.addView(delete, new LinearLayout.LayoutParams(40 * density, 40 * density));
        return row;
    }

    /** Deleting a workspace file cannot be undone, so it is always confirmed by name. */
    private static void promptWorkspaceDelete(@NonNull TermuxActivity activity,
                                              @NonNull String name) {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.workspace_delete_title, name))
            .setMessage(R.string.workspace_delete_message)
            .setNegativeButton(android.R.string.cancel,
                (dialog, which) -> showWorkspacePicker(activity))
            .setPositiveButton(R.string.workspace_delete_confirm, (dialog, which) -> {
                try {
                    activity.deleteWorkspace(name);
                    Toast.makeText(activity, activity.getString(
                        R.string.workspace_deleted, name), Toast.LENGTH_SHORT).show();
                } catch (TerminalWorkspace.WorkspaceException e) {
                    Toast.makeText(activity, activity.getString(
                        R.string.workspace_picker_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
                showWorkspacePicker(activity);
            })
            .show();
    }

    /** Keeps a long workspace list reachable on a short screen. */
    @NonNull
    private static View wrapScrolling(@NonNull View content) {
        ScrollView scroll = new ScrollView(content.getContext());
        scroll.addView(content, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
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
        final CheckBox runCommands;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
            .setTitle(name)
            .setMessage(activity.getString(R.string.workspace_picker_mode_message, name));
        if (commandCount == 0) {
            runCommands = null;
        } else {
            LinearLayout frame = dialogFrame(activity);
            runCommands = addCheckBox(frame, activity.getResources().getQuantityString(
                    R.plurals.workspace_load_run_commands, commandCount, commandCount),
                activity.getString(R.string.workspace_load_run_commands_summary));
            builder.setView(frame);
        }
        builder
            .setPositiveButton(R.string.workspace_picker_replace,
                (dialog, which) -> loadWorkspace(activity, name, true, isChecked(runCommands)))
            .setNeutralButton(R.string.workspace_picker_append,
                (dialog, which) -> loadWorkspace(activity, name, false, isChecked(runCommands)))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
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
        LinearLayout frame = dialogFrame(activity);
        EditText nameField = new EditText(activity);
        nameField.setSingleLine();
        nameField.setHint(R.string.session_browser_workspace_name);
        addToFrame(frame, nameField);
        CheckBox captureCommands = addCheckBox(frame,
            activity.getString(R.string.workspace_save_capture_commands),
            activity.getString(R.string.workspace_save_capture_commands_summary));
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.session_browser_workspace_name)
            .setView(frame)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> saveWorkspace(activity,
                nameField.getText().toString(), false, captureCommands.isChecked()))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static boolean isChecked(@Nullable CheckBox box) {
        return box != null && box.isChecked();
    }

    /** A vertical dialog body with the inset a Material dialog expects around its content. */
    @NonNull
    private static LinearLayout dialogFrame(@NonNull Context context) {
        int density = Math.round(context.getResources().getDisplayMetrics().density);
        LinearLayout frame = new LinearLayout(context);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setPadding(24 * density, 8 * density, 24 * density, 0);
        return frame;
    }

    private static void addToFrame(@NonNull LinearLayout frame, @NonNull View child) {
        frame.addView(child, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        addToFrame(frame, box);

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
                new MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(
                        R.string.session_browser_workspace_overwrite_title, requested))
                    .setMessage(R.string.session_browser_workspace_overwrite_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.session_browser_overwrite,
                        (dialog, which) -> saveWorkspace(activity, requested, true, captureCommands))
                    .show();
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
