package com.termux.app.fragments.settings.terl;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.leader.Leader;
import com.termux.app.notice.AppNotice;
import com.termux.app.terminal.CommandPaletteCaptureModel;
import com.termux.app.terminal.TerminalBindingConfigWriter;
import com.termux.app.terminal.TerminalKeyBindingResolver;
import com.termux.app.terminal.keybind.ActionBindings;
import com.termux.launcherctl.LauncherToolRegistry;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Terl Settings' shortcut keys: every action with its key after the leader. Tap an action, press
 * the new key, and the binding is written leader-relative through the file the palette's capture
 * writes ({@link TerminalBindingConfigWriter#bind}); a key another action holds is shown in place
 * and waits for Replace or Cancel, and nothing is saved until then.
 */
@Keep
public final class TerlKeyBindingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        build();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.terl_settings_bindings_title);
        build();
    }

    /** The actions the page lists: ones that run as they stand, in the registry's order. */
    @NonNull
    static List<ActionBindings.Action> actions(@NonNull Context context) {
        List<ActionBindings.Action> actions = new ArrayList<>();
        for (LauncherToolRegistry.ToolMetadata tool : LauncherToolRegistry.getInstance().getUiTools()) {
            if (tool.category == null || tool.titleRes == 0) continue;
            JSONArray required = tool.schema.optJSONArray("required");
            if (required != null && required.length() > 0) continue;
            actions.add(new ActionBindings.Action(tool.name,
                context.getString(LauncherToolRegistry.titleRes(tool))));
        }
        return actions;
    }

    private void build() {
        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) screen = getPreferenceManager().createPreferenceScreen(context);
        screen.removeAll();
        TerminalKeyBindingResolver resolver = TerminalKeyBindingResolver.getInstance();
        Leader leader = resolver.leader();
        for (ActionBindings.Row row : ActionBindings.rows(actions(context),
                resolver.holdersByStoredStroke())) {
            Preference preference = new Preference(context);
            preference.setKey(row.action.tool);
            preference.setPersistent(false);
            preference.setTitle(row.action.title);
            TerminalKeyBindingResolver.DroppedDefault dropped =
                row.stroke() == null ? resolver.droppedDefault(row.action.tool) : null;
            preference.setSummary(row.stroke() != null
                ? ActionBindings.display(leader, row.stroke())
                : dropped != null
                    ? context.getString(R.string.terl_settings_bindings_dropped,
                        ActionBindings.display(leader, dropped.stored), dropped.reason)
                    : context.getString(R.string.terl_settings_bindings_not_bound));
            preference.setOnPreferenceClickListener(clicked -> {
                capture(preference, row.action);
                return true;
            });
            screen.addPreference(preference);
        }
        setPreferenceScreen(screen);
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    /**
     * Asks for the key after the leader, in place of the row: an on-screen picker (Settings has no
     * in-app keyboard and a phone may have no hardware one), and a hardware key press while open.
     */
    private void capture(@NonNull Preference row, @NonNull ActionBindings.Action action) {
        Context context = requireContext();
        Leader leader = TerminalKeyBindingResolver.getInstance().leader();
        TextView message = new TextView(context);
        message.setText(context.getString(R.string.terl_settings_bindings_press,
            ActionBindings.leaderName(leader)));
        message.setPadding(dp(context, 24), dp(context, 8), dp(context, 24), dp(context, 8));
        MaterialButton shift = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        shift.setText(R.string.terl_settings_bindings_shift);
        shift.setCheckable(true);
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(7);
        grid.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(message);
        body.addView(shift, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(grid);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(action.title)
            .setView(scroll)
            .setPositiveButton(R.string.terl_settings_bindings_replace, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        // Cancel, Back and a tap outside all leave the binding as it was and the row as it read.
        dialog.setOnDismissListener(dismissed -> build());
        String[] pending = new String[1];
        for (ActionBindings.PickerKey key : ActionBindings.pickerKeys()) {
            MaterialButton button = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(key.label);
            button.setAllCaps(false);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setInsetTop(0);
            button.setInsetBottom(0);
            button.setPadding(dp(context, 4), 0, dp(context, 4), 0);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.width = 0;
            params.height = dp(context, 44);
            grid.addView(button, params);
            button.setOnClickListener(v -> judge(context, row, action,
                ActionBindings.storedFor((shift.isChecked() ? "shift+" : "") + key.token),
                message, dialog, pending));
        }
        dialog.setOnShowListener(shown -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (pending[0] != null && save(context, row, action, pending[0], dialog)) dialog.dismiss();
            });
        });
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
                || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DEL)
                return false;
            String pressed = event.isCtrlPressed() || event.isAltPressed() ? null
                : CommandPaletteCaptureModel.strokeFor(keyCode, false, false, event.isShiftPressed());
            String stored = ActionBindings.storedFor(pressed);
            if (stored == null) return false;
            judge(context, row, action, stored, message, dialog, pending);
            return true;
        });
        dialog.show();
    }

    /** Saves a free key, refuses a taken one with its reason, or shows the clash and waits. */
    private void judge(@NonNull Context context, @NonNull Preference row,
                       @NonNull ActionBindings.Action action, @Nullable String stored,
                       @NonNull TextView message, @NonNull AlertDialog dialog,
                       @NonNull String[] pending) {
        if (stored == null) return;
        TerminalKeyBindingResolver resolver = TerminalKeyBindingResolver.getInstance();
        Leader leader = resolver.leader();
        ActionBindings.Decision decision = ActionBindings.decide(leader, stored, action.tool,
            resolver.holdersByStoredStroke(), resolver.holdersByTypedStroke());
        String chord = ActionBindings.display(leader, stored);
        pending[0] = null;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
        switch (decision.kind) {
            case REFUSED:
                message.setText(decision.reason);
                break;
            case UNCHANGED:
                dialog.dismiss();
                break;
            case CLASH: {
                String other = nameOf(context, decision.holder);
                message.setText(context.getString(R.string.terl_settings_bindings_taken, chord, other));
                row.setSummary(context.getString(R.string.terl_settings_bindings_clash_row, chord, other));
                pending[0] = stored;
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
                break;
            }
            default:
                if (save(context, row, action, stored, dialog)) dialog.dismiss();
        }
    }

    /** Writes the binding the way the palette's capture does; false (with a notice) on failure. */
    private boolean save(@NonNull Context context, @NonNull Preference row,
                         @NonNull ActionBindings.Action action, @NonNull String stored,
                         @NonNull AlertDialog dialog) {
        // Computed against the table as it stands: the action's other chords (a built-in default
        // among them) come off, and so does a clash only the leader's typing reveals.
        TerminalKeyBindingResolver resolver = TerminalKeyBindingResolver.getInstance();
        ActionBindings.Decision decision = ActionBindings.decide(resolver.leader(), stored,
            action.tool, resolver.holdersByStoredStroke(), resolver.holdersByTypedStroke());
        String error = TerminalBindingConfigWriter.bindMoving(stored, action.tool,
            ActionBindings.strokesToFree(action.tool, stored, decision,
                resolver.holdersByStoredStroke()));
        if (error != null) {
            AppNotice.show(context, context.getString(R.string.terl_settings_bindings_failed, error), false);
            return false;
        }
        // The hint card reads the table the write just reloaded, so it shows the change at once.
        AppNotice.success(context, context.getString(R.string.terl_settings_bindings_saved,
            action.title, ActionBindings.display(TerminalKeyBindingResolver.getInstance().leader(), stored)));
        build();
        return true;
    }

    @NonNull
    private static String nameOf(@NonNull Context context, @NonNull TerminalKeyBindingResolver.Hint holder) {
        if (holder.label != null) return holder.label;
        LauncherToolRegistry.ToolMetadata tool = LauncherToolRegistry.getInstance().getTool(holder.toolName);
        return tool != null && tool.titleRes != 0
            ? context.getString(LauncherToolRegistry.titleRes(tool)) : holder.toolName;
    }

    private static int dp(@NonNull Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
