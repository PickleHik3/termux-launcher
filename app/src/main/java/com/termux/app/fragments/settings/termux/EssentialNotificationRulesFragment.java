package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.app.statusbar.EssentialNotificationRule;
import com.termux.app.statusbar.EssentialNotificationRules;
import com.termux.launcherctl.LauncherCtlNotificationListener;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.List;

/**
 * Essential notification rules, in the benchmark screens' dress: a header card, the stored rules
 * as tonal cards with an on/off switch ({@link EssentialNotificationRule#enabled}, which a pinned
 * card's "Mute this rule" turns off) and a quiet Remove, and an Add card with the package and keywords fields. The
 * data model and behaviour are the old dialog's: the same {@link EssentialNotificationRules}
 * store, the same validation, and a pinned-notification refresh after every change.
 */
@Keep
public class EssentialNotificationRulesFragment extends Fragment {
    private static final String STATE_PACKAGE = "essential_rules_package";
    private static final String STATE_KEYWORDS = "essential_rules_keywords";
    private static final String STATE_CLEAR = "essential_rules_clear";

    @Nullable private TermuxAppSharedPreferences preferences;
    @Nullable private LinearLayout rulesColumn;
    @Nullable private EditText packageInput;
    @Nullable private EditText keywordsInput;
    @Nullable private MaterialSwitch clearSwitch;
    @Nullable private String savedPackage;
    @Nullable private String savedKeywords;
    private boolean savedClear;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            savedPackage = savedInstanceState.getString(STATE_PACKAGE);
            savedKeywords = savedInstanceState.getString(STATE_KEYWORDS);
            savedClear = savedInstanceState.getBoolean(STATE_CLEAR, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        preferences = TermuxAppSharedPreferences.build(context);
        ScrollView scroll = new ScrollView(context);
        scroll.setId(R.id.tai_bench_scroll);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, TaiBenchViews.dp(context, 32));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TaiBenchViews.Card header = TaiBenchViews.card(context);
        header.core.addView(TaiBenchViews.title(context, getString(R.string.settings_essential_notifications_title)));
        header.core.addView(TaiBenchViews.body(context, getString(R.string.settings_essential_notifications_summary)),
            TaiBenchViews.block(context, 4));
        column.addView(header.outer);

        column.addView(TaiBenchViews.sectionHeader(context, getString(R.string.essential_rules_section_rules), ""));
        rulesColumn = new LinearLayout(context);
        rulesColumn.setOrientation(LinearLayout.VERTICAL);
        column.addView(rulesColumn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(TaiBenchViews.sectionHeader(context, getString(R.string.essential_rules_section_add), ""));
        column.addView(buildAddCard(context));
        rebuildRules();
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_essential_notifications_title);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (packageInput != null) outState.putString(STATE_PACKAGE, packageInput.getText().toString());
        if (keywordsInput != null) outState.putString(STATE_KEYWORDS, keywordsInput.getText().toString());
        if (clearSwitch != null) outState.putBoolean(STATE_CLEAR, clearSwitch.isChecked());
    }

    @Override
    public void onDestroyView() {
        rulesColumn = null;
        packageInput = null;
        keywordsInput = null;
        clearSwitch = null;
        super.onDestroyView();
    }

    @NonNull
    private View buildAddCard(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        EditText pkg = input(context, R.string.essential_rules_package_hint,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText keywords = input(context, R.string.essential_rules_keywords_hint, InputType.TYPE_CLASS_TEXT);
        if (savedPackage != null) pkg.setText(savedPackage);
        if (savedKeywords != null) keywords.setText(savedKeywords);
        packageInput = pkg;
        keywordsInput = keywords;
        card.core.addView(pkg);
        card.core.addView(keywords, TaiBenchViews.block(context, 8));

        LinearLayout clearRow = new LinearLayout(context);
        clearRow.setOrientation(LinearLayout.HORIZONTAL);
        clearRow.setGravity(Gravity.CENTER_VERTICAL);
        clearRow.addView(TaiBenchViews.body(context, getString(R.string.essential_rules_clear_label)),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch clear = new MaterialSwitch(context);
        clear.setChecked(savedClear);
        clear.setContentDescription(getString(R.string.essential_rules_clear_label));
        clearSwitch = clear;
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchParams.setMarginStart(TaiBenchViews.dp(context, 12));
        clearRow.addView(clear, switchParams);
        card.core.addView(clearRow, TaiBenchViews.block(context, 10));

        TextView add = TaiBenchViews.goButton(context, getString(R.string.essential_rules_add));
        add.setMinHeight(TaiBenchViews.dp(context, 44));
        add.setOnClickListener(v -> {
            TaiMotion.tick(v);
            addRule(v.getContext());
        });
        card.core.addView(add, TaiBenchViews.block(context, 12));
        return card.outer;
    }

    @NonNull
    private static EditText input(@NonNull Context context, int hint, int inputType) {
        EditText field = new EditText(context);
        field.setBackgroundResource(R.drawable.tai_centre_link_bar);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        field.setTextColor(TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurface));
        field.setHintTextColor(TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        field.setMinHeight(TaiBenchViews.dp(context, 44));
        field.setPadding(TaiBenchViews.dp(context, 16), 0, TaiBenchViews.dp(context, 16), 0);
        return field;
    }

    /** The old dialog's Add: same validation and messages, then the fields reset for the next rule. */
    private void addRule(@NonNull Context context) {
        if (preferences == null || packageInput == null || keywordsInput == null || clearSwitch == null) return;
        String pkg = packageInput.getText().toString().trim();
        String keywords = keywordsInput.getText().toString().trim();
        if (pkg.isEmpty() && keywords.isEmpty()) {
            AppNotice.show(context, R.string.essential_rules_needs_field, false);
            return;
        }
        EssentialNotificationRule rule = new EssentialNotificationRule(
            EssentialNotificationRules.deriveId(pkg, keywords), pkg, keywords, clearSwitch.isChecked());
        if (EssentialNotificationRules.add(preferences, rule) == null) {
            AppNotice.show(context, R.string.essential_rules_full, false);
            return;
        }
        LauncherCtlNotificationListener.requestPinnedRefresh();
        packageInput.setText("");
        keywordsInput.setText("");
        clearSwitch.setChecked(false);
        rebuildRules();
    }

    private void rebuildRules() {
        LinearLayout column = rulesColumn;
        TermuxAppSharedPreferences prefs = preferences;
        if (column == null || prefs == null) return;
        Context context = column.getContext();
        column.removeAllViews();
        List<EssentialNotificationRule> rules = EssentialNotificationRules.load(prefs);
        if (rules.isEmpty()) {
            TextView empty = TaiBenchViews.body(context, getString(R.string.essential_rules_empty));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(TaiBenchViews.dp(context, 32), TaiBenchViews.dp(context, 12), TaiBenchViews.dp(context, 32), TaiBenchViews.dp(context, 12));
            column.addView(empty);
            return;
        }
        for (EssentialNotificationRule rule : rules) {
            column.addView(ruleCard(context, prefs, rule));
        }
    }

    @NonNull
    private View ruleCard(@NonNull Context context, @NonNull TermuxAppSharedPreferences prefs,
                          @NonNull EssentialNotificationRule rule) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout middle = new LinearLayout(context);
        middle.setOrientation(LinearLayout.VERTICAL);
        String pkg = rule.packageName.isEmpty() ? getString(R.string.essential_rules_any_package) : rule.packageName;
        String match = rule.match.isEmpty() ? getString(R.string.essential_rules_any_text) : "“" + rule.match + "”";
        TextView title = TaiBenchViews.title(context, match);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        middle.addView(title);
        TextView sub = TaiBenchViews.mono(context, pkg);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        middle.addView(sub);
        line.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (rule.clearOnDismiss) {
            TextView pill = TaiBenchViews.pill(context, getString(R.string.essential_rules_clears_pill),
                TaiModelCentreRows.Tone.NEUTRAL);
            LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pillParams.setMarginStart(TaiBenchViews.dp(context, 8));
            line.addView(pill, pillParams);
        }
        // On/off without deleting: a pinned card's "Mute this rule" turns this off, and it can be
        // turned back on here. A muted rule's text dims so the list reads which ones pin.
        if (!rule.enabled) middle.setAlpha(.55f);
        MaterialSwitch enabled = new MaterialSwitch(context);
        enabled.setChecked(rule.enabled);
        enabled.setContentDescription(getString(R.string.essential_rules_enabled_description, match));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (EssentialNotificationRules.setEnabled(prefs, rule.id, checked) != null) {
                LauncherCtlNotificationListener.requestPinnedRefresh();
            }
            button.post(this::rebuildRules);
        });
        LinearLayout.LayoutParams enabledParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        enabledParams.setMarginStart(TaiBenchViews.dp(context, 8));
        line.addView(enabled, enabledParams);
        TextView remove = TaiBenchViews.ghostButton(context, getString(R.string.essential_rules_remove));
        remove.setMinHeight(TaiBenchViews.dp(context, 32));
        remove.setContentDescription(getString(R.string.essential_rules_remove_description));
        remove.setOnClickListener(v -> {
            TaiMotion.tick(v);
            if (EssentialNotificationRules.remove(prefs, rule.id) != null) {
                LauncherCtlNotificationListener.requestPinnedRefresh();
            }
            rebuildRules();
        });
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        removeParams.setMarginStart(TaiBenchViews.dp(context, 8));
        line.addView(remove, removeParams);
        card.core.addView(line);
        return card.outer;
    }
}
