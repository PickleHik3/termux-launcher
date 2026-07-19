package com.termux.app.activities;

import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.theme.TermuxThemeManager;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;

import java.io.File;

/** Five-card, replayable launcher tour with replaceable full-screen media slots. */
public final class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "termux_launcher_onboarding";
    private static final String KEY_COMPLETED = "completed_v1";
    private static final String KEY_PENDING_AUTOMATIC_LAUNCH = "pending_automatic_launch_v1";
    private static final String STATE_PAGE = "page";
    private static final String GUIDE_URL = "https://picklehik3.github.io/termux-launcher-site";
    private static final int REQUEST_HOME_ROLE = 1104;
    private static final int SWIPE_FLING_THRESHOLD_DP = 72;
    static final int PAGE_COUNT = 5;

    private static final int[][] PAGE_TEXT = {
        {R.string.onboarding_new_welcome_kicker, R.string.onboarding_new_welcome_title,
            R.string.onboarding_new_welcome_body, R.string.onboarding_media_home,
            R.string.onboarding_duration_20},
        {R.string.onboarding_new_launch_kicker, R.string.onboarding_new_launch_title,
            R.string.onboarding_new_launch_body, R.string.onboarding_media_search,
            R.string.onboarding_duration_15},
        {R.string.onboarding_new_personalise_kicker, R.string.onboarding_new_personalise_title,
            R.string.onboarding_new_personalise_body, R.string.onboarding_media_appearance,
            R.string.onboarding_duration_15},
        {R.string.onboarding_new_notifications_kicker, R.string.onboarding_new_notifications_title,
            R.string.onboarding_new_notifications_body, R.string.onboarding_media_notifications,
            R.string.onboarding_duration_10},
        {R.string.onboarding_new_ready_kicker, R.string.onboarding_new_ready_title,
            R.string.onboarding_new_ready_body, R.string.onboarding_media_ready, 0}
    };

    private static final int[][] PAGE_CHIPS = {
        {R.string.onboarding_chip_full_terminal, R.string.onboarding_chip_app_dock,
            R.string.onboarding_chip_ai_backends},
        {R.string.onboarding_chip_percent_search, R.string.onboarding_chip_az_rail,
            R.string.onboarding_chip_long_press},
        {R.string.onboarding_chip_material_palette, R.string.onboarding_chip_glass_blur,
            R.string.onboarding_chip_icon_packs},
        {R.string.onboarding_chip_swipe_up, R.string.onboarding_chip_inline_reply,
            R.string.onboarding_chip_pinned_apps},
        {}
    };

    private int mPage;
    private GestureDetector mGestureDetector;
    private TextView mPlaceholder;
    private TextView mDuration;
    private TextView mKicker;
    private TextView mTitle;
    private TextView mBody;
    private LinearLayout mProgressDots;
    private LinearLayout mChips;
    private HorizontalScrollView mChipsScroll;
    private ScrollView mSheetScroll;
    private View mFinalActions;
    private View mNavigation;
    private MaterialButton mBackButton;
    private MaterialButton mNextButton;
    private MaterialButton mSkipButton;
    private float mDisplayDensity;

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, OnboardingActivity.class);
    }

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

    static int clampPage(int page) {
        return Math.max(0, Math.min(page, PAGE_COUNT - 1));
    }

    static int skipTargetPage() {
        return PAGE_COUNT - 1;
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
        mDisplayDensity = getResources().getDisplayMetrics().density;
        setContentView(R.layout.activity_onboarding);
        applySystemBars();
        bindViews();
        applyEdgeToEdgeInsets();
        configureSwipeNavigation();
        mPage = savedInstanceState == null ? 0 : clampPage(savedInstanceState.getInt(STATE_PAGE, 0));
        renderPage();
    }

    private void applySystemBars() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            window.setNavigationBarContrastEnforced(false);
    }

    private void bindViews() {
        mPlaceholder = findViewById(R.id.onboarding_media_placeholder);
        mDuration = findViewById(R.id.onboarding_duration_badge);
        mKicker = findViewById(R.id.onboarding_kicker);
        mTitle = findViewById(R.id.onboarding_title);
        mBody = findViewById(R.id.onboarding_body);
        mProgressDots = findViewById(R.id.onboarding_progress_dots);
        mChips = findViewById(R.id.onboarding_chips);
        mChipsScroll = findViewById(R.id.onboarding_chips_scroll);
        mSheetScroll = findViewById(R.id.onboarding_sheet_scroll);
        mFinalActions = findViewById(R.id.onboarding_final_actions);
        mNavigation = findViewById(R.id.onboarding_navigation);
        mBackButton = findViewById(R.id.onboarding_back_button);
        mNextButton = findViewById(R.id.onboarding_next_button);
        mSkipButton = findViewById(R.id.onboarding_skip_button);

        mSkipButton.setOnClickListener(view -> showPage(skipTargetPage()));
        mBackButton.setOnClickListener(view -> showPreviousPage());
        mNextButton.setOnClickListener(view -> showPage(mPage + 1));
        findViewById(R.id.onboarding_home_button).setOnClickListener(view -> requestHomeRole());
        findViewById(R.id.onboarding_starter_profile_button).setOnClickListener(view -> {
            // TODO: Connect this to the starter-profile installer when one is added to the repo.
            Toast.makeText(this, R.string.onboarding_starter_profile_todo, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.onboarding_guide_button).setOnClickListener(view ->
            ShareUtils.openUrl(this, GUIDE_URL));
        findViewById(R.id.onboarding_explore_button).setOnClickListener(view -> completeAndFinish());
    }

    private void applyEdgeToEdgeInsets() {
        View content = findViewById(android.R.id.content);
        View topBar = (View) mSkipButton.getParent();
        View sheet = (View) mSheetScroll.getParent();
        ViewGroup.MarginLayoutParams durationParams =
            (ViewGroup.MarginLayoutParams) mDuration.getLayoutParams();
        ViewGroup.MarginLayoutParams sheetParams =
            (ViewGroup.MarginLayoutParams) sheet.getLayoutParams();
        int topBarPadding = topBar.getPaddingTop();
        int durationTopMargin = durationParams.topMargin;
        int sheetBottomMargin = sheetParams.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            topBar.setPadding(topBar.getPaddingLeft(), topBarPadding + bars.top,
                topBar.getPaddingRight(), topBar.getPaddingBottom());
            ViewGroup.MarginLayoutParams durationLayout =
                (ViewGroup.MarginLayoutParams) mDuration.getLayoutParams();
            durationLayout.topMargin = durationTopMargin + bars.top;
            mDuration.setLayoutParams(durationLayout);
            ViewGroup.MarginLayoutParams sheetLayout =
                (ViewGroup.MarginLayoutParams) sheet.getLayoutParams();
            sheetLayout.bottomMargin = sheetBottomMargin + bars.bottom;
            sheet.setLayoutParams(sheetLayout);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    private void configureSwipeNavigation() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent event) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent down, MotionEvent up, float velocityX, float velocityY) {
                if (down == null || up == null) return false;
                float dx = up.getX() - down.getX();
                if (Math.abs(dx) < dp(SWIPE_FLING_THRESHOLD_DP)
                    || Math.abs(dx) < Math.abs(up.getY() - down.getY()))
                    return false;
                if (dx < 0 && mPage < PAGE_COUNT - 1) showPage(mPage + 1);
                else if (dx > 0 && mPage > 0) showPage(mPage - 1);
                return true;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mGestureDetector != null) mGestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private void showPreviousPage() {
        if (mPage > 0) showPage(mPage - 1);
        else finish();
    }

    private void showPage(int page) {
        mPage = clampPage(page);
        renderPage();
        mSheetScroll.scrollTo(0, 0);
    }

    private void renderPage() {
        int[] text = PAGE_TEXT[mPage];
        mKicker.setText(text[0]);
        mTitle.setText(text[1]);
        mBody.setText(text[2]);
        mPlaceholder.setText(text[3]);
        if (text[4] == 0) {
            mDuration.setVisibility(View.GONE);
        } else {
            mDuration.setVisibility(View.VISIBLE);
            mDuration.setText(text[4]);
        }
        renderProgressDots();
        renderChips();
        boolean finalPage = mPage == PAGE_COUNT - 1;
        mSkipButton.setVisibility(finalPage ? View.GONE : View.VISIBLE);
        mFinalActions.setVisibility(finalPage ? View.VISIBLE : View.GONE);
        mNavigation.setVisibility(finalPage ? View.GONE : View.VISIBLE);
        mBackButton.setVisibility(mPage == 0 ? View.INVISIBLE : View.VISIBLE);
        mTitle.sendAccessibilityEvent(
            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    private void renderProgressDots() {
        mProgressDots.removeAllViews();
        int accent = MaterialColors.getColor(mProgressDots,
            com.termux.shared.R.attr.termuxColorPrimary, 0xFFA6E6B3);
        for (int i = 0; i < PAGE_COUNT; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(i == mPage ? 22 : 6), dp(6));
            if (i > 0) params.leftMargin = dp(6);
            dot.setLayoutParams(params);
            GradientDrawable background = new GradientDrawable();
            background.setColor(i == mPage ? accent : 0x38FFFFFF);
            background.setCornerRadius(dp(99));
            dot.setBackground(background);
            mProgressDots.addView(dot);
        }
    }

    private void renderChips() {
        mChips.removeAllViews();
        int[] chips = PAGE_CHIPS[mPage];
        mChipsScroll.setVisibility(chips.length == 0 ? View.GONE : View.VISIBLE);
        int accent = MaterialColors.getColor(mChips,
            com.termux.shared.R.attr.termuxColorPrimary, 0xFFA6E6B3);
        for (int chipText : chips) {
            TextView chip = new TextView(this);
            chip.setText(chipText);
            chip.setTextColor(accent);
            chip.setTextSize(10.5f);
            chip.setTypeface(android.graphics.Typeface.MONOSPACE);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            GradientDrawable background = new GradientDrawable();
            background.setColor((accent & 0x00FFFFFF) | 0x1F000000);
            background.setStroke(dp(1), (accent & 0x00FFFFFF) | 0x38000000);
            background.setCornerRadius(dp(9));
            chip.setBackground(background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(7);
            mChips.addView(chip, params);
        }
    }

    private void requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                        REQUEST_HOME_ROLE);
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                }
            }
        }
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

    private void completeAndFinish() {
        preferences(this).edit()
            .putBoolean(KEY_COMPLETED, true)
            .putBoolean(KEY_PENDING_AUTOMATIC_LAUNCH, false)
            .apply();
        finish();
    }

    private int dp(int value) {
        return Math.round(value * mDisplayDensity);
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
