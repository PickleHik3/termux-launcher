package com.termux.app.fragments.settings.terl;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

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
import java.util.Collections;
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
            actions.add(new ActionBindings.Action(tool.name, context.getString(tool.titleRes)));
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
            preference.setSummary(row.stroke() == null
                ? context.getString(R.string.terl_settings_bindings_not_bound)
                : ActionBindings.display(leader, row.stroke()));
            preference.setOnPreferenceClickListener(clicked -> {
                capture(preference, row.action);
                return true;
            });
            screen.addPreference(preference);
        }
        setPreferenceScreen(screen);
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    /** Asks for the key after the leader, in place of the row. */
    private void capture(@NonNull Preference row, @NonNull ActionBindings.Action action) {
        Context context = requireContext();
        Leader leader = TerminalKeyBindingResolver.getInstance().leader();
        TextView message = new TextView(context);
        message.setText(context.getString(R.string.terl_settings_bindings_press,
            ActionBindings.leaderName(leader)));
        message.setPadding(dp(context, 24), dp(context, 8), dp(context, 24), dp(context, 8));
        EditText sink = new EditText(context);
        // No text is wanted: key events are, from a hardware keyboard and from the soft one.
        sink.setInputType(InputType.TYPE_NULL);
        sink.setCursorVisible(false);
        sink.setAlpha(0f);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(message);
        body.addView(sink, new LinearLayout.LayoutParams(dp(context, 1), dp(context, 1)));
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(action.title)
            .setView(body)
            .setPositiveButton(R.string.terl_settings_bindings_replace, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        // Cancel, Back and a tap outside all leave the binding as it was and the row as it read.
        dialog.setOnDismissListener(dismissed -> build());
        String[] pending = new String[1];
        dialog.setOnShowListener(shown -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (pending[0] != null && save(context, row, action, pending[0], dialog)) dialog.dismiss();
            });
            sink.requestFocus();
        });
        sink.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
                || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DEL)
                return false;
            String pressed = event.isCtrlPressed() || event.isAltPressed() ? null
                : CommandPaletteCaptureModel.strokeFor(keyCode, false, false, event.isShiftPressed());
            if (pressed == null && !event.isCtrlPressed() && !event.isAltPressed()) {
                int unicode = event.getUnicodeChar();
                if (unicode > 0) pressed = CommandPaletteCaptureModel.strokeForChar(
                    (char) unicode, false, false, false);
            }
            String stored = ActionBindings.storedFor(pressed);
            if (stored == null) return true;
            judge(context, row, action, stored, message, dialog, pending);
            return true;
        });
        dialog.show();
    }

    /** Saves a free key, refuses a taken one with its reason, or shows the clash and waits. */
    private void judge(@NonNull Context context, @NonNull Preference row,
                       @NonNull ActionBindings.Action action, @NonNull String stored,
                       @NonNull TextView message, @NonNull AlertDialog dialog,
                       @NonNull String[] pending) {
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
        String error = TerminalBindingConfigWriter.bind(stored, action.tool,
            Collections.<String>emptyList(), null);
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
        return tool != null && tool.titleRes != 0 ? context.getString(tool.titleRes) : holder.toolName;
    }

    private static int dp(@NonNull Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
