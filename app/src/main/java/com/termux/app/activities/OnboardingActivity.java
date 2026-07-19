package com.termux.app.activities;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.theme.TermuxThemeManager;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;

import java.io.File;

/** A concise, replayable tour of the launcher surface and its optional integrations. */
public final class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "termux_launcher_onboarding";
    private static final String KEY_COMPLETED = "completed_v1";
    private static final String KEY_PENDING_AUTOMATIC_LAUNCH = "pending_automatic_launch_v1";
    private static final String STATE_PAGE = "page";
    private static final String GUIDE_URL =
        "https://picklehik3.github.io/termux-launcher-site/#wiki/install";

    private static final int[][] PAGE_TEXT = {
        {R.string.onboarding_welcome_eyebrow, R.string.onboarding_welcome_title,
            R.string.onboarding_welcome_body, R.string.onboarding_welcome_code,
            R.string.onboarding_welcome_bullet_1, R.string.onboarding_welcome_bullet_2,
            R.string.onboarding_welcome_bullet_3},
        {R.string.onboarding_search_eyebrow, R.string.onboarding_search_title,
            R.string.onboarding_search_body, R.string.onboarding_search_code,
            R.string.onboarding_search_bullet_1, R.string.onboarding_search_bullet_2,
            R.string.onboarding_search_bullet_3},
        {R.string.onboarding_style_eyebrow, R.string.onboarding_style_title,
            R.string.onboarding_style_body, R.string.onboarding_style_code,
            R.string.onboarding_style_bullet_1, R.string.onboarding_style_bullet_2,
            R.string.onboarding_style_bullet_3},
        {R.string.onboarding_shell_eyebrow, R.string.onboarding_shell_title,
            R.string.onboarding_shell_body, R.string.onboarding_shell_code,
            R.string.onboarding_shell_bullet_1, R.string.onboarding_shell_bullet_2,
            R.string.onboarding_shell_bullet_3},
        {R.string.onboarding_bridge_eyebrow, R.string.onboarding_bridge_title,
            R.string.onboarding_bridge_body, R.string.onboarding_bridge_code,
            R.string.onboarding_bridge_bullet_1, R.string.onboarding_bridge_bullet_2,
            R.string.onboarding_bridge_bullet_3},
        {R.string.onboarding_optional_eyebrow, R.string.onboarding_optional_title,
            R.string.onboarding_optional_body, R.string.onboarding_optional_code,
            R.string.onboarding_optional_bullet_1, R.string.onboarding_optional_bullet_2,
            R.string.onboarding_optional_bullet_3},
        {R.string.onboarding_ready_eyebrow, R.string.onboarding_ready_title,
            R.string.onboarding_ready_body, R.string.onboarding_ready_code,
            R.string.onboarding_ready_bullet_1, R.string.onboarding_ready_bullet_2,
            R.string.onboarding_ready_bullet_3}
    };

    private static final int[] PAGE_ICONS = {
        R.drawable.ic_settings_terminal,
        R.drawable.ic_settings_grid,
        R.drawable.ic_settings_palette,
        R.drawable.ic_settings_keyboard,
        R.drawable.ic_settings_shortcut,
        R.drawable.ic_settings_ai,
        R.drawable.ic_foreground
    };

    private static final int[] PAGE_CAPTURES = {
        R.drawable.onboarding_home,
        R.drawable.onboarding_search,
        R.drawable.onboarding_style,
        R.drawable.onboarding_home,
        R.drawable.onboarding_bridge,
        R.drawable.onboarding_optional,
        R.drawable.onboarding_ready
    };

    private int mPage;
    private ProgressBar mProgressBar;
    private TextView mProgressText;
    private ImageView mCapture;
    private ImageView mIcon;
    private TextView mCaptureNumber;
    private TextView mEyebrow;
    private TextView mTitle;
    private TextView mBody;
    private TextView mCode;
    private TextView[] mBullets;
    private ScrollView mScrollView;
    private View mFinalActions;
    private MaterialButton mBackButton;
    private MaterialButton mNextButton;

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, OnboardingActivity.class);
    }

    /**
     * Remembers a genuinely new bootstrap across activity recreation. Existing installations with
     * a working login binary are never interrupted merely because they upgraded to this build.
     */
    public static boolean prepareAutomaticLaunch(@NonNull Context context) {
        SharedPreferences preferences = preferences(context);
        boolean completed = preferences.getBoolean(KEY_COMPLETED, false);
        boolean pending = preferences.getBoolean(KEY_PENDING_AUTOMATIC_LAUNCH, false);
        boolean bootstrapPresent = new File(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "login").isFile();
        boolean shouldLaunch = shouldPrepareAutomaticLaunch(completed, pending, bootstrapPresent);
        if (shouldLaunch && !pending) {
            preferences.edit().putBoolean(KEY_PENDING_AUTOMATIC_LAUNCH, true).apply();
        }
        return shouldLaunch;
    }

    static boolean shouldPrepareAutomaticLaunch(
        boolean completed, boolean pending, boolean bootstrapPresent) {
        if (completed) return false;
        return pending || !bootstrapPresent;
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TermuxThemeUtils.setAppNightMode(this);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setTheme(R.style.Theme_TermuxApp_Onboarding);
        TermuxThemeManager.applyThemeOverlays(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        applySystemBars();
        bindViews();
        mPage = savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_PAGE, 0);
        renderPage();
    }

    private void applySystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
    }

    private void bindViews() {
        mProgressBar = findViewById(R.id.onboarding_progress_bar);
        mProgressText = findViewById(R.id.onboarding_progress_text);
        mCapture = findViewById(R.id.onboarding_capture);
        mIcon = findViewById(R.id.onboarding_icon);
        mCaptureNumber = findViewById(R.id.onboarding_capture_number);
        mEyebrow = findViewById(R.id.onboarding_eyebrow);
        mTitle = findViewById(R.id.onboarding_title);
        mBody = findViewById(R.id.onboarding_body);
        mCode = findViewById(R.id.onboarding_code);
        mBullets = new TextView[] {
            findViewById(R.id.onboarding_bullet_1).findViewById(R.id.onboarding_bullet_text),
            findViewById(R.id.onboarding_bullet_2).findViewById(R.id.onboarding_bullet_text),
            findViewById(R.id.onboarding_bullet_3).findViewById(R.id.onboarding_bullet_text)
        };
        mScrollView = findViewById(R.id.onboarding_scroll);
        mFinalActions = findViewById(R.id.onboarding_final_actions);
        mBackButton = findViewById(R.id.onboarding_back_button);
        mNextButton = findViewById(R.id.onboarding_next_button);

        findViewById(R.id.onboarding_skip_button).setOnClickListener(view -> completeAndFinish());
        mBackButton.setOnClickListener(view -> showPreviousPage());
        mNextButton.setOnClickListener(view -> {
            if (mPage == PAGE_TEXT.length - 1) completeAndFinish();
            else showPage(mPage + 1);
        });
        findViewById(R.id.onboarding_home_button).setOnClickListener(view -> openHomeSettings());
        findViewById(R.id.onboarding_guide_button).setOnClickListener(view -> openGuide());
    }

    private void showPreviousPage() {
        if (mPage > 0) showPage(mPage - 1);
        else finish();
    }

    private void showPage(int page) {
        mPage = Math.max(0, Math.min(page, PAGE_TEXT.length - 1));
        renderPage();
        mScrollView.scrollTo(0, 0);
    }

    private void renderPage() {
        int[] text = PAGE_TEXT[mPage];
        mProgressBar.setMax(PAGE_TEXT.length);
        mProgressBar.setProgress(mPage + 1);
        mProgressText.setText(getString(R.string.onboarding_progress, mPage + 1, PAGE_TEXT.length));
        mCapture.setImageResource(PAGE_CAPTURES[mPage]);
        mIcon.setImageResource(PAGE_ICONS[mPage]);
        mCaptureNumber.setText(getString(R.string.onboarding_capture_number, mPage + 1));
        mEyebrow.setText(text[0]);
        mTitle.setText(text[1]);
        mIcon.setContentDescription(getString(text[1]));
        mBody.setText(text[2]);
        mCode.setText(text[3]);
        for (int i = 0; i < mBullets.length; i++) {
            mBullets[i].setText(text[i + 4]);
        }
        boolean finalPage = mPage == PAGE_TEXT.length - 1;
        mFinalActions.setVisibility(finalPage ? View.VISIBLE : View.GONE);
        mBackButton.setVisibility(mPage == 0 ? View.INVISIBLE : View.VISIBLE);
        mNextButton.setText(finalPage ? R.string.onboarding_finish : R.string.onboarding_next);
        mTitle.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    private void completeAndFinish() {
        preferences(this).edit()
            .putBoolean(KEY_COMPLETED, true)
            .putBoolean(KEY_PENDING_AUTOMATIC_LAUNCH, false)
            .apply();
        finish();
    }

    private void openHomeSettings() {
        if (startSettingsIntent(new Intent(Settings.ACTION_HOME_SETTINGS))) return;
        if (startSettingsIntent(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) return;
        Toast.makeText(this, R.string.onboarding_settings_unavailable, Toast.LENGTH_SHORT).show();
    }

    private boolean startSettingsIntent(@NonNull Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private void openGuide() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE_URL)));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.onboarding_settings_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (mPage > 0) showPreviousPage();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_PAGE, mPage);
        super.onSaveInstanceState(outState);
    }
}
