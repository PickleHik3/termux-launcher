package com.termux.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.app.activities.OnboardingActivity;
import com.termux.app.models.UserAction;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.models.ReportInfo;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Keep
public final class AboutSupportPreferencesFragment extends MaterialPreferenceFragment {
    @Override public void onCreatePreferences(Bundle state, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        setPreferencesFromResource(R.xml.about_support_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        click("quick_start_tour", preference -> { startActivity(OnboardingActivity.createIntent(context)); return true; });
        url("documentation", "https://github.com/PickleHik3/termux-launcher/tree/dev/docs");
        url("report_issue", "https://github.com/PickleHik3/termux-launcher/issues");
        url("source_code", "https://github.com/PickleHik3/termux-launcher");
        configureAbout(context);
        configureLicenses(context);
        configureDonate(context);
    }

    @Override public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_about_support);
    }

    private void url(String key, String target) { click(key, preference -> { ShareUtils.openUrl(requireContext(), target); return true; }); }
    private void click(String key, Preference.OnPreferenceClickListener listener) { Preference row = findPreference(key); if (row != null) row.setOnPreferenceClickListener(listener); }

    private void configureAbout(@NonNull Context context) {
        click("about", preference -> {
            new Thread(() -> {
                String title = getString(R.string.settings_version_build_title);
                String report = "## Termux Launcher\n\n" + TermuxUtils.getAppInfoMarkdownString(context,
                    TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES) + "\n\n"
                    + AndroidUtils.getDeviceInfoMarkdownString(context, true);
                ReportInfo info = new ReportInfo(UserAction.ABOUT.getName(), TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title);
                info.setReportString(report);
                if (isAdded() && getActivity() != null) getActivity().runOnUiThread(() -> ReportActivity.startReportActivity(context, info));
            }).start();
            return true;
        });
    }

    private void configureLicenses(@NonNull Context context) {
        click("open_source_licenses", preference -> {
            new Thread(() -> {
                StringBuilder text = new StringBuilder(readRaw(context, R.raw.third_party_notices));
                append(text, "GNU General Public License v3", readRaw(context, R.raw.license_gpl_3));
                append(text, "Apache License 2.0", readRaw(context, R.raw.license_apache_2));
                append(text, "MIT License", readRaw(context, R.raw.license_mit));
                append(text, "GPLv2 with Classpath exception", readRaw(context, R.raw.license_gpl_2_classpath));
                append(text, "BSD 2-Clause License", readRaw(context, R.raw.license_bsd_2_clause));
                ReportInfo info = new ReportInfo("OpenSourceLicenses", TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME,
                    getString(R.string.open_source_licenses_preference_title));
                info.setReportString(text.toString());
                if (isAdded() && getActivity() != null) getActivity().runOnUiThread(() -> ReportActivity.startReportActivity(context, info));
            }).start();
            return true;
        });
    }

    private void configureDonate(@NonNull Context context) {
        Preference row = findPreference("donate");
        if (row == null) return;
        String digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context);
        String release = digest == null ? null : TermuxUtils.getAPKRelease(digest);
        boolean allowed = release != null && !TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST.equals(release);
        row.setVisible(allowed);
        if (allowed) row.setOnPreferenceClickListener(preference -> { ShareUtils.openUrl(context, TermuxConstants.TERMUX_DONATE_URL); return true; });
    }

    private static void append(StringBuilder output, String title, String body) { output.append("\n\n## ").append(title).append("\n\n```text\n").append(body).append("\n```\n"); }
    private static String readRaw(Context context, int id) {
        try (InputStream input = context.getResources().openRawResource(id); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) { return "Unable to read bundled license text: " + e.getMessage(); }
    }
}
