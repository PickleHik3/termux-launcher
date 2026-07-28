package com.termux.app.terminal;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Searchable command palette over the registry's UI projection.
 *
 * <p>Entries come from {@link LauncherToolRegistry#getUiToolsByCategory()} and run
 * through {@link TerminalActionDispatcher}, so a palette selection, a keystroke,
 * and {@code /v1/agent/execute} share one execution path.
 *
 * <p>Two rules decide what appears here:
 *
 * <ul>
 *   <li>A tool needs a {@code titleRes}. That is what marks it user-facing, which
 *       is why {@code terminal.state} (an introspection tool) stays out.
 *   <li>A tool must have no required schema arguments, because the palette has
 *       nowhere to ask for them. That self-excludes {@code pane.focus_direction}
 *       and {@code pane.resize}, which only make sense from a directional key.
 * </ul>
 *
 * <p>The view is built in code rather than XML to keep the change small; a first
 * pass at Material styling comes with the eventual layout file.
 */
public final class TerminalCommandPalette {

    private static final String LOG_TAG = "TerminalCommandPalette";

    private TerminalCommandPalette() {
    }

    /** Shows the palette. Must be called on the main thread. */
    public static void show(@NonNull TermuxActivity activity) {
        List<CommandPaletteFilter.Entry> entries = buildEntries(activity);
        if (entries.isEmpty()) {
            Toast.makeText(activity, R.string.palette_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        View container = activity.getLayoutInflater().inflate(R.layout.command_palette, null);
        EditText search = container.findViewById(R.id.palette_search);
        ListView list = container.findViewById(R.id.palette_list);
        final TextView empty = container.findViewById(R.id.palette_empty);

        final PaletteAdapter adapter = new PaletteAdapter(activity, entries);
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.palette_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable editable) {
                adapter.setQuery(editable.toString());
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                CommandPaletteFilter.Entry entry = adapter.entryAt(position);
                if (entry == null) return; // a category header
                if (!entry.enabled) {
                    Toast.makeText(activity,
                        entry.disabledReason != null ? entry.disabledReason
                            : activity.getString(R.string.palette_empty),
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                if (entry.isDestructive()) {
                    confirmThenRun(activity, entry);
                } else {
                    run(activity, entry);
                }
            }
        });

        dialog.show();
    }

    private static void confirmThenRun(@NonNull TermuxActivity activity,
                                       @NonNull CommandPaletteFilter.Entry entry) {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.palette_confirm_title, entry.title))
            .setMessage(entry.subtitle)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.palette_confirm_run,
                (d, which) -> run(activity, entry))
            .show();
    }

    private static void run(@NonNull TermuxActivity activity,
                            @NonNull CommandPaletteFilter.Entry entry) {
        JSONObject result = TerminalActionDispatcher.getInstance()
            .execute(entry.toolName, new JSONObject());
        if (!result.optBoolean("ok", false)) {
            String message = result.optString("message",
                activity.getString(R.string.palette_action_failed, entry.title));
            Logger.logWarn(LOG_TAG, "Palette action " + entry.toolName + " failed: " + message);
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Builds the palette rows, grouped by category in registration order.
     * Visible for testing the inclusion rules without inflating a dialog.
     */
    @NonNull
    static List<CommandPaletteFilter.Entry> buildEntries(@NonNull TermuxActivity activity) {
        LauncherToolRegistry.ActionContext context =
            TerminalActionDispatcher.getInstance().actionContext();

        List<CommandPaletteFilter.Entry> entries = new ArrayList<>();
        Map<String, List<LauncherToolRegistry.ToolMetadata>> grouped =
            LauncherToolRegistry.getInstance().getUiToolsByCategory();
        for (Map.Entry<String, List<LauncherToolRegistry.ToolMetadata>> group : grouped.entrySet()) {
            for (LauncherToolRegistry.ToolMetadata tool : group.getValue()) {
                if (tool.titleRes == 0) continue;          // not user-facing
                if (hasRequiredArguments(tool)) continue;  // palette cannot supply them

                LauncherToolRegistry.Availability availability = tool.availabilityIn(context);
                String reason = availability.available || availability.reasonRes == 0
                    ? null
                    : activity.getString(availability.reasonRes);

                entries.add(new CommandPaletteFilter.Entry(
                    tool.name,
                    activity.getString(tool.titleRes),
                    subtitleFor(activity, tool, context),
                    tool.category,
                    strokesFor(tool, context),
                    availability.available,
                    reason,
                    tool.requiresConfirmation,
                    tool.risk));
            }
        }
        return entries;
    }

    /**
     * Subtitle is the localized description plus the stroke that currently applies,
     * so a row explains itself and teaches its keybind at the same time. Only
     * bindings whose condition holds are shown.
     */
    private static String subtitleFor(@NonNull Context context,
                                      @NonNull LauncherToolRegistry.ToolMetadata tool,
                                      @NonNull LauncherToolRegistry.ActionContext actionContext) {
        String description = tool.descriptionRes != 0
            ? context.getString(tool.descriptionRes)
            : tool.description;
        List<String> strokes = strokesFor(tool, actionContext);
        if (strokes.isEmpty()) {
            return description;
        }
        return description + "  ·  " + strokes.get(0);
    }

    /**
     * Strokes that actually apply in the current mode. A binding whose condition
     * does not hold would be a false promise on the row — Ctrl+Alt+V must not be
     * advertised next to Paste while split panes are on.
     */
    @NonNull
    static List<String> strokesFor(@NonNull LauncherToolRegistry.ToolMetadata tool,
                                   @NonNull LauncherToolRegistry.ActionContext context) {
        return TerminalKeyBindingResolver.getInstance().getStrokesForTool(tool.name, context);
    }

    static boolean hasRequiredArguments(@NonNull LauncherToolRegistry.ToolMetadata tool) {
        JSONArray required = tool.schema.optJSONArray("required");
        return required != null && required.length() > 0;
    }

    @NonNull
    static String categoryLabel(@NonNull Context context, @NonNull String category) {
        switch (category) {
            case LauncherToolRegistry.CATEGORY_SESSION: return context.getString(R.string.palette_category_session);
            case LauncherToolRegistry.CATEGORY_WINDOW: return context.getString(R.string.palette_category_window);
            case LauncherToolRegistry.CATEGORY_PANE: return context.getString(R.string.palette_category_pane);
            case LauncherToolRegistry.CATEGORY_TERMINAL: return context.getString(R.string.palette_category_terminal);
            case LauncherToolRegistry.CATEGORY_CLIPBOARD: return context.getString(R.string.palette_category_clipboard);
            case LauncherToolRegistry.CATEGORY_APPEARANCE: return context.getString(R.string.palette_category_appearance);
            case LauncherToolRegistry.CATEGORY_APP: return context.getString(R.string.palette_category_app);
            default: return category;
        }
    }


    /**
     * Flat list of rows. Category headers appear only in the unfiltered view; a
     * search shows a single ranked list, because grouping a ranked result would
     * fight the ranking.
     */
    private static final class PaletteAdapter extends BaseAdapter {

        private final Context context;
        private final List<CommandPaletteFilter.Entry> all;
        /** Null marks a header row; the parallel list holds its label. */
        private final List<CommandPaletteFilter.Entry> rows = new ArrayList<>();
        private final List<String> headers = new ArrayList<>();

        PaletteAdapter(@NonNull Context context, @NonNull List<CommandPaletteFilter.Entry> all) {
            this.context = context;
            this.all = all;
            setQuery("");
        }

        void setQuery(@Nullable String query) {
            rows.clear();
            headers.clear();
            List<CommandPaletteFilter.Entry> ranked = CommandPaletteFilter.filterAndRank(all, query);
            boolean grouped = query == null || query.trim().isEmpty();
            if (!grouped) {
                for (CommandPaletteFilter.Entry entry : ranked) {
                    rows.add(entry);
                    headers.add(null);
                }
                notifyDataSetChanged();
                return;
            }
            Map<String, List<CommandPaletteFilter.Entry>> byCategory = new LinkedHashMap<>();
            for (CommandPaletteFilter.Entry entry : ranked) {
                List<CommandPaletteFilter.Entry> group = byCategory.get(entry.category);
                if (group == null) {
                    group = new ArrayList<>();
                    byCategory.put(entry.category, group);
                }
                group.add(entry);
            }
            for (Map.Entry<String, List<CommandPaletteFilter.Entry>> group : byCategory.entrySet()) {
                rows.add(null);
                headers.add(categoryLabel(context, group.getKey()));
                for (CommandPaletteFilter.Entry entry : group.getValue()) {
                    rows.add(entry);
                    headers.add(null);
                }
            }
            notifyDataSetChanged();
        }

        @Nullable
        CommandPaletteFilter.Entry entryAt(int position) {
            return position >= 0 && position < rows.size() ? rows.get(position) : null;
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public boolean isEnabled(int position) {
            return rows.get(position) != null;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) == null ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CommandPaletteFilter.Entry entry = rows.get(position);
            LayoutInflater inflater = LayoutInflater.from(context);
            if (entry == null) {
                TextView header = (TextView) (convertView != null ? convertView
                    : inflater.inflate(R.layout.command_palette_header, parent, false));
                header.setText(headers.get(position));
                return header;
            }

            View row = convertView != null ? convertView
                : inflater.inflate(R.layout.command_palette_row, parent, false);
            TextView title = row.findViewById(R.id.palette_row_title);
            TextView subtitle = row.findViewById(R.id.palette_row_subtitle);
            title.setText(entry.title);
            title.setAlpha(entry.enabled ? 1f : 0.4f);
            subtitle.setText(entry.enabled ? entry.subtitle : entry.disabledReason);
            subtitle.setAlpha(entry.enabled ? 0.7f : 0.5f);
            return row;
        }
    }
}
