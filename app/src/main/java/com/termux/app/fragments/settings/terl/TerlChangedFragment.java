package com.termux.app.fragments.settings.terl;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsLayoutUtils;

import java.util.List;

/**
 * The Changed filter: every setting of Terl's Settings that differs from its default, with its
 * value and default, a Reset beside each, and the row's menu on a hold. A tap opens the setting
 * where it lives.
 */
@Keep
public final class TerlChangedFragment extends PreferenceFragmentCompat {

    private static final String KEY_LIST = "terl_changed";

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        build();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TerlRowMenu.attach(this, this::build);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.terl_settings_changed_title);
        build();
    }

    void build() {
        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        List<TerlChanged.Setting> changed = TerlChanged.changed(TerlSettings.registry(context),
            id -> TerlSettings.shows(context, id));
        PreferenceCategory list = new PreferenceCategory(context);
        list.setKey(KEY_LIST);
        list.setPersistent(false);
        list.setTitle(changed.isEmpty() ? context.getString(R.string.terl_settings_changed_none)
            : context.getResources().getQuantityString(R.plurals.terl_settings_changed_count,
                changed.size(), changed.size()));
        screen.addPreference(list);
        for (TerlChanged.Setting setting : changed) {
            list.addPreference(new Row(context, setting, this::build));
        }
        setPreferenceScreen(screen);
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    /** One changed setting: its value and default, Reset at its end. */
    static final class Row extends Preference {
        @NonNull private final String mId;
        @NonNull private final Runnable mAfterReset;

        Row(@NonNull Context context, @NonNull TerlChanged.Setting setting, @NonNull Runnable afterReset) {
            super(context);
            mId = setting.declaration.id;
            mAfterReset = afterReset;
            setKey(mId);
            setPersistent(false);
            setTitle(setting.declaration.title);
            setSummary(context.getString(R.string.terl_settings_changed_value,
                TerlSettings.label(context, setting.declaration, setting.value),
                TerlSettings.label(context, setting.declaration, setting.declaration.defaultValue())));
            super.setWidgetLayoutResource(R.layout.preference_widget_terl_reset);
            TerlSettingsTree.Item item = TerlSettingsTree.setting(mId);
            setOnPreferenceClickListener(row -> {
                if (item != null && getContext() instanceof androidx.fragment.app.FragmentActivity) {
                    TerlSettingsNavigator.openGroup((androidx.fragment.app.FragmentActivity) getContext(),
                        item.group, mId);
                }
                return true;
            });
        }

        /** Settings' row styling gives every row a chevron; this row's end is its Reset. */
        @Override
        public void setWidgetLayoutResource(int widgetLayoutResId) {
            // Kept: preference_widget_terl_reset.
        }

        @Override
        public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            View reset = holder.findViewById(R.id.terl_settings_reset);
            if (reset != null) reset.setOnClickListener(button -> {
                if (TerlSettings.reset(button.getContext(), mId)) mAfterReset.run();
            });
        }
    }
}
