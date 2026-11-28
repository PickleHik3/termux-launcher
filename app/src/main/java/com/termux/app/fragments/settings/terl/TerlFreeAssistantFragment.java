package com.termux.app.fragments.settings.terl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.FreeAssistantProviders;
import com.termux.ai.TaiRemoteClient;
import com.termux.ai.TaiRemoteSettings;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;

import java.util.List;

/**
 * Settings › AI › Use a free online assistant: pick a service with a free plan (the list is data,
 * {@link FreeAssistantProviders}), get a key from it in the browser, paste the key, and Test with
 * one request. Saving makes the service the remote provider the Assistant's requests already reach
 * (ADR 0027); the key stays encrypted on the phone. Terl's own page, beside v1's On-device AI.
 */
@Keep
public final class TerlFreeAssistantFragment extends PreferenceFragmentCompat {

    private static final String STATE_CHOSEN = "terl_free_assistant_chosen";
    private static final String KEY_SERVICE = "terl_free_assistant_service";
    private static final String KEY_GET = "terl_free_assistant_get";
    private static final String KEY_PASTE = "terl_free_assistant_paste";
    private static final String KEY_TEST = "terl_free_assistant_test";
    private static final String KEY_PRIVACY = "terl_free_assistant_privacy";

    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @NonNull private List<FreeAssistantProviders.Provider> mProviders = java.util.Collections.emptyList();
    /** The service shown now: the saved one, else the first; a pick changes nothing until a key is saved. */
    @Nullable private FreeAssistantProviders.Provider mChosen;
    @Nullable private String mTestSummary;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        Context context = getPreferenceManager().getContext();
        mProviders = FreeAssistantProviders.load(context);
        String chosenId = savedInstanceState == null ? null : savedInstanceState.getString(STATE_CHOSEN);
        mChosen = byId(chosenId);
        if (mChosen == null) mChosen = FreeAssistantProviders.current(mProviders, new TaiRemoteSettings(context));
        if (mChosen == null && !mProviders.isEmpty()) mChosen = mProviders.get(0);
        build(context);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mChosen != null) outState.putString(STATE_CHOSEN, mChosen.id);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.terl_free_assistant_title);
    }

    @Nullable
    private FreeAssistantProviders.Provider byId(@Nullable String id) {
        if (id == null) return null;
        for (FreeAssistantProviders.Provider provider : mProviders) if (provider.id.equals(id)) return provider;
        return null;
    }

    private void build(@NonNull Context context) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        FreeAssistantProviders.Provider chosen = mChosen;
        if (chosen == null) {
            Preference none = row(context, KEY_SERVICE, getString(R.string.terl_free_assistant_none), null);
            none.setSelectable(false);
            screen.addPreference(none);
            setPreferenceScreen(screen);
            SettingsLayoutUtils.applyScreenLayout(this);
            return;
        }
        TaiRemoteSettings settings = new TaiRemoteSettings(context);
        boolean saved = chosen.equals(FreeAssistantProviders.current(mProviders, settings))
            && settings.hasApiKey();

        Preference service = row(context, KEY_SERVICE, getString(R.string.terl_free_assistant_service),
            chosen.note.isEmpty() ? chosen.name : chosen.name + "\n" + chosen.note);
        service.setOnPreferenceClickListener(clicked -> {
            chooseService(context);
            return true;
        });
        screen.addPreference(service);

        PreferenceCategory key = new PreferenceCategory(context);
        key.setKey("terl_free_assistant_key");
        key.setTitle(R.string.terl_free_assistant_heading_key);
        key.setPersistent(false);
        screen.addPreference(key);
        Preference get = row(context, KEY_GET, getString(R.string.terl_free_assistant_get_key),
            getString(R.string.terl_free_assistant_get_key_summary, chosen.name));
        get.setOnPreferenceClickListener(clicked -> {
            openKeyPage(context, chosen);
            return true;
        });
        key.addPreference(get);
        Preference paste = row(context, KEY_PASTE, getString(R.string.terl_free_assistant_paste),
            getString(saved ? R.string.terl_free_assistant_key_saved : R.string.terl_free_assistant_key_none));
        paste.setOnPreferenceClickListener(clicked -> {
            pasteKey(context, chosen);
            return true;
        });
        key.addPreference(paste);
        Preference test = row(context, KEY_TEST, getString(R.string.terl_free_assistant_test),
            mTestSummary != null ? mTestSummary : getString(saved
                ? R.string.terl_free_assistant_test_summary : R.string.terl_free_assistant_test_needs_key));
        test.setEnabled(saved);
        test.setOnPreferenceClickListener(clicked -> {
            test(context);
            return true;
        });
        key.addPreference(test);
        Preference privacy = row(context, KEY_PRIVACY, null,
            getString(R.string.terl_free_assistant_privacy, chosen.name));
        privacy.setSelectable(false);
        key.addPreference(privacy);

        setPreferenceScreen(screen);
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    @NonNull
    private static Preference row(@NonNull Context context, @NonNull String key, @Nullable String title,
                                  @Nullable String summary) {
        Preference row = new Preference(context);
        row.setKey(key);
        row.setPersistent(false);
        if (title != null) row.setTitle(title);
        if (summary != null) row.setSummary(summary);
        return row;
    }

    private void chooseService(@NonNull Context context) {
        CharSequence[] names = new CharSequence[mProviders.size()];
        int checked = -1;
        for (int i = 0; i < names.length; i++) {
            FreeAssistantProviders.Provider provider = mProviders.get(i);
            names[i] = provider.name;
            if (provider.equals(mChosen)) checked = i;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.terl_free_assistant_service)
            .setSingleChoiceItems(names, checked, (dialog, which) -> {
                dialog.dismiss();
                FreeAssistantProviders.Provider picked = mProviders.get(which);
                if (picked.equals(mChosen)) return;
                mChosen = picked;
                mTestSummary = null;
                if (picked.warning) {
                    new MaterialAlertDialogBuilder(context)
                        .setTitle(picked.name)
                        .setMessage(picked.note)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                }
                if (isAdded()) build(context);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static void openKeyPage(@NonNull Context context, @NonNull FreeAssistantProviders.Provider provider) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyPage));
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (RuntimeException e) {
            // No browser: the address itself is the next best thing.
            AppNotice.show(context, provider.keyPage, true);
        }
    }

    private void pasteKey(@NonNull Context context, @NonNull FreeAssistantProviders.Provider provider) {
        EditText input = new EditText(context);
        input.setHint(R.string.terl_free_assistant_paste_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true);
        FrameLayout frame = new FrameLayout(context);
        int side = Math.round(20 * context.getResources().getDisplayMetrics().density);
        frame.setPadding(side, 0, side, 0);
        frame.addView(input);
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.terl_free_assistant_paste_title, provider.name))
            .setView(frame)
            .setPositiveButton(R.string.terl_free_assistant_save, (dialog, which) -> {
                String key = input.getText().toString().trim();
                if (key.isEmpty()) return;
                String replaced = FreeAssistantProviders.replaces(mProviders,
                    new TaiRemoteSettings(context), provider);
                if (replaced == null) {
                    save(context, provider, key);
                    return;
                }
                // Another service is set up already (the remote model screen's, or another of
                // these): say so before its key goes.
                new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.terl_free_assistant_replace_title)
                    .setMessage(getString(R.string.terl_free_assistant_replace_message, replaced,
                        provider.name))
                    .setPositiveButton(R.string.terl_free_assistant_replace, (d, w) -> save(context, provider, key))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void save(@NonNull Context context, @NonNull FreeAssistantProviders.Provider provider,
                      @NonNull String key) {
        boolean saved = FreeAssistantProviders.save(new TaiRemoteSettings(context), provider, key);
        mTestSummary = null;
        AppNotice.show(context, saved ? R.string.terl_free_assistant_saved
            : R.string.terl_free_assistant_save_failed, !saved);
        if (isAdded()) build(context);
        if (saved) test(context);
    }

    /** What a failed Test says: the key refused, the day's limit, no connection, or no answer. */
    @NonNull
    private String failureText(@NonNull TaiRemoteClient.TestResult result) {
        String name = mChosen == null ? "" : mChosen.name;
        switch (FreeAssistantProviders.failureOf(result)) {
            case BAD_KEY: return getString(R.string.terl_free_assistant_test_bad_key, name);
            case LIMIT: return getString(R.string.terl_free_assistant_test_limit, name);
            case OFFLINE: return getString(R.string.terl_free_assistant_test_offline, name);
            default: return getString(R.string.terl_free_assistant_test_failed);
        }
    }

    /** One request to the saved service, off the main thread. */
    private void test(@NonNull Context context) {
        Context app = context.getApplicationContext();
        mTestSummary = getString(R.string.terl_free_assistant_testing);
        build(context);
        Thread thread = new Thread(() -> {
            TaiRemoteSettings settings = new TaiRemoteSettings(app);
            TaiRemoteClient.TestResult result = new TaiRemoteClient(app, settings.baseUrl(),
                settings.apiKey()).testConnection(settings.modelId());
            mHandler.post(() -> {
                if (!isAdded()) return;
                mTestSummary = result.ok
                    ? getString(R.string.terl_free_assistant_test_ok, (int) result.latencyMs)
                    : failureText(result);
                build(context);
            });
        }, "terl-free-assistant-test");
        thread.setDaemon(true);
        thread.start();
    }
}
