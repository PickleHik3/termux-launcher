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
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
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

    private static void promptSave(@NonNull TermuxActivity activity) {
        TextInputDialogUtils.textInput(activity, R.string.session_browser_workspace_name, null,
            android.R.string.ok, text -> saveWorkspace(activity, text, false),
            -1, null, -1, null, null);
    }

    private static void saveWorkspace(@NonNull TermuxActivity activity, @Nullable String name,
                                      boolean overwrite) {
        try {
            TerminalWorkspace workspace = activity.saveWorkspace(name == null ? "" : name,
                overwrite, false);
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
                        (dialog, which) -> saveWorkspace(activity, requested, true))
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
            return session == null ? position : session.index;
        }

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
