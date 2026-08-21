package com.termux.app.onboarding;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.button.MaterialButton;
import com.termux.R;

/** A self-contained, once-per-install introduction to the launcher/terminal experience. */
public final class FirstLaunchOnboarding {

    private static final String PREFS_NAME = "termux_first_launch";
    private static final String KEY_COMPLETED_VERSION = "onboarding_completed_version";
    private static final int CURRENT_VERSION = 2;
    private static final int PAGE_COUNT = 3;

    private FirstLaunchOnboarding() {}

    public static void showIfNeeded(@NonNull Activity activity, boolean force) {
        showIfNeeded(activity, force, null);
    }

    /**
     * @param onFinished run once the onboarding has been dismissed — whether the user paged to the
     *     end or skipped out. Not run at all when there was no onboarding to show, so a caller can
     *     use it for first-run-only follow-up work such as asking for permissions.
     */
    public static void showIfNeeded(@NonNull Activity activity, boolean force,
                                    @Nullable Runnable onFinished) {
        SharedPreferences preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!force && preferences.getInt(KEY_COMPLETED_VERSION, 0) >= CURRENT_VERSION) return;
        ViewGroup host = activity.findViewById(android.R.id.content);
        if (host == null || host.findViewWithTag(Controller.ROOT_TAG) != null) return;
        new Controller(activity, host, preferences, onFinished).show();
    }

    private static final class Controller {
        static final String ROOT_TAG = "termux:first-launch-onboarding";
        private static final int BACKDROP = Color.rgb(7, 15, 23);
        private static final int TEXT = Color.rgb(236, 249, 249);
        private static final int MUTED = Color.rgb(157, 183, 195);
        private static final int CYAN = Color.rgb(116, 232, 232);

        private final Activity activity;
        private final ViewGroup host;
        private final SharedPreferences preferences;
        private final int oldStatusBarColor;
        private final int oldNavigationBarColor;
        private final int oldSystemUiVisibility;
        private final FrameLayout root;
        private final LinearLayout shell;
        private final ViewPager pager;
        private final LinearLayout dots;
        private final MaterialButton primaryButton;
        @Nullable private Runnable onFinished;

        Controller(Activity activity, ViewGroup host, SharedPreferences preferences,
                   @Nullable Runnable onFinished) {
            this.activity = activity;
            this.host = host;
            this.preferences = preferences;
            this.onFinished = onFinished;
            Window window = activity.getWindow();
            oldStatusBarColor = window.getStatusBarColor();
            oldNavigationBarColor = window.getNavigationBarColor();
            oldSystemUiVisibility = window.getDecorView().getSystemUiVisibility();

            root = new FrameLayout(activity);
            root.setTag(ROOT_TAG);
            root.setClickable(true);
            root.setFocusable(true);
            root.setBackgroundColor(BACKDROP);

            shell = new LinearLayout(activity);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setPadding(dp(24), dp(18), dp(24), dp(18));
            root.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
                applySafeArea();
                return windowInsets;
            });
            root.addOnLayoutChangeListener(
                (v, l, t, r, b, oldL, oldT, oldR, oldB) -> applySafeArea());

            LinearLayout top = new LinearLayout(activity);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView brand = label(R.string.onboarding_brand, 12f, CYAN, Typeface.BOLD);
            brand.setLetterSpacing(0.18f);
            top.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1f));
            TextView skip = label(R.string.onboarding_skip, 14f, MUTED, Typeface.BOLD);
            skip.setGravity(Gravity.CENTER);
            skip.setPadding(dp(18), 0, 0, 0);
            skip.setContentDescription(activity.getString(R.string.onboarding_skip_description));
            skip.setOnClickListener(v -> complete());
            top.addView(skip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
            shell.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

            pager = new ViewPager(activity);
            pager.setId(View.generateViewId());
            pager.setAdapter(new PagesAdapter());
            pager.setOffscreenPageLimit(PAGE_COUNT - 1);
            pager.setPageMargin(dp(12));
            pager.setPageTransformer(false, (page, position) -> {
                float distance = Math.min(1f, Math.abs(position));
                page.setAlpha(1f - distance * 0.34f);
                page.setScaleX(1f - distance * 0.045f);
                page.setScaleY(1f - distance * 0.045f);
            });
            shell.addView(pager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            dots = new LinearLayout(activity);
            dots.setGravity(Gravity.CENTER);
            dots.setOrientation(LinearLayout.HORIZONTAL);
            shell.addView(dots, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

            primaryButton = new MaterialButton(activity);
            primaryButton.setText(R.string.onboarding_next);
            primaryButton.setTextSize(16f);
            primaryButton.setAllCaps(false);
            primaryButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            primaryButton.setTextColor(Color.rgb(7, 26, 31));
            primaryButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CYAN));
            primaryButton.setCornerRadius(dp(18));
            primaryButton.setInsetTop(0);
            primaryButton.setInsetBottom(0);
            primaryButton.setOnClickListener(v -> advance());
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
            buttonParams.topMargin = dp(8);
            shell.addView(primaryButton, buttonParams);

            pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override public void onPageSelected(int position) { updateNavigation(position); }
            });
            updateNavigation(0);
        }

        void show() {
            Window window = activity.getWindow();
            window.setStatusBarColor(BACKDROP);
            window.setNavigationBarColor(BACKDROP);
            window.getDecorView().setSystemUiVisibility(oldSystemUiVisibility
                & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            host.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ViewCompat.requestApplyInsets(root);
            applySafeArea();
            root.setAlpha(0f);
            root.setTranslationY(dp(14));
            root.animate().alpha(1f).translationY(0f).setDuration(360L).start();
        }

        /**
         * Keeps the pages clear of the status bar, navigation bar and cutout. The insets are read
         * from the window rather than taken from the dispatch: the onboarding is added on top of
         * the activity's own content view, which consumes them before they travel this far.
         */
        private void applySafeArea() {
            WindowInsetsCompat windowInsets =
                ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
            if (windowInsets == null) return;
            Insets safeArea = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            shell.setPadding(dp(24), safeArea.top + dp(12), dp(24), safeArea.bottom + dp(14));
        }

        private void advance() {
            int next = pager.getCurrentItem() + 1;
            if (next >= PAGE_COUNT) complete();
            else pager.setCurrentItem(next, true);
        }

        private void updateNavigation(int selected) {
            dots.removeAllViews();
            for (int i = 0; i < PAGE_COUNT; i++) {
                View dot = new View(activity);
                GradientDrawable background = new GradientDrawable();
                background.setShape(GradientDrawable.RECTANGLE);
                background.setCornerRadius(dp(4));
                background.setColor(i == selected ? CYAN : Color.argb(90, 157, 183, 195));
                dot.setBackground(background);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(i == selected ? 22 : 7), dp(7));
                params.setMargins(dp(4), 0, dp(4), 0);
                dots.addView(dot, params);
            }
            primaryButton.setText(selected == PAGE_COUNT - 1
                ? R.string.onboarding_get_started : R.string.onboarding_next);
            primaryButton.setContentDescription(activity.getString(selected == PAGE_COUNT - 1
                ? R.string.onboarding_get_started_description : R.string.onboarding_next_description));
        }

        private void complete() {
            preferences.edit().putInt(KEY_COMPLETED_VERSION, CURRENT_VERSION).apply();
            root.animate().alpha(0f).translationY(dp(10)).setDuration(220L)
                .withEndAction(() -> {
                    host.removeView(root);
                    Window window = activity.getWindow();
                    window.setStatusBarColor(oldStatusBarColor);
                    window.setNavigationBarColor(oldNavigationBarColor);
                    window.getDecorView().setSystemUiVisibility(oldSystemUiVisibility);
                    // After the overlay is gone, never over it: the permission dialogs that follow
                    // are system-owned and would otherwise be judged against a backdrop the user
                    // has no context for yet.
                    Runnable finished = onFinished;
                    onFinished = null;
                    if (finished != null) finished.run();
                }).start();
        }

        private TextView label(@StringRes int text, float size, int color, int style) {
            TextView view = new TextView(activity);
            view.setText(text);
            view.setTextSize(size);
            view.setTextColor(color);
            view.setTypeface(Typeface.DEFAULT, style);
            return view;
        }

        private int dp(int value) {
            return Math.round(value * activity.getResources().getDisplayMetrics().density);
        }

        private final class PagesAdapter extends PagerAdapter {
            private final int[] eyebrows = {
                R.string.onboarding_page_one_eyebrow,
                R.string.onboarding_page_two_eyebrow,
                R.string.onboarding_page_three_eyebrow
            };
            private final int[] titles = {
                R.string.onboarding_page_one_title,
                R.string.onboarding_page_two_title,
                R.string.onboarding_page_three_title
            };
            // Page one talks package management, which differs per edition: the Nix
            // edition has no pkg/APT, and its first session installs Nix itself.
            private final boolean nix = com.termux.shared.termux.TermuxBootstrap.isAppPackageVariantNIX();
            private final int[] bodies = {
                nix ? R.string.onboarding_page_one_body_nix : R.string.onboarding_page_one_body,
                R.string.onboarding_page_two_body,
                R.string.onboarding_page_three_body
            };
            private final int[] firstTips = {
                nix ? R.string.onboarding_page_one_tip_one_nix : R.string.onboarding_page_one_tip_one,
                R.string.onboarding_page_two_tip_one,
                R.string.onboarding_page_three_tip_one
            };
            private final int[] secondTips = {
                nix ? R.string.onboarding_page_one_tip_two_nix : R.string.onboarding_page_one_tip_two,
                R.string.onboarding_page_two_tip_two,
                R.string.onboarding_page_three_tip_two
            };
            private final int[] footers = {
                nix ? R.string.onboarding_page_one_footer_nix : R.string.onboarding_page_one_footer,
                R.string.onboarding_page_two_footer,
                R.string.onboarding_page_three_footer
            };

            @Override public int getCount() { return PAGE_COUNT; }
            @Override public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }

            @NonNull
            @Override public Object instantiateItem(@NonNull ViewGroup container, int position) {
                ScrollView scroll = new ScrollView(activity);
                scroll.setFillViewport(true);
                scroll.setClipToPadding(false);
                LinearLayout page = new LinearLayout(activity);
                page.setOrientation(LinearLayout.VERTICAL);
                page.setGravity(Gravity.CENTER_VERTICAL);
                page.setPadding(0, dp(12), 0, dp(12));
                scroll.addView(page, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                OnboardingClipView clip = new OnboardingClipView(activity, position);
                clip.setContentDescription(activity.getString(footers[position]));
                // Every clip is 16:9 and the view measures itself to match.
                page.addView(clip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView eyebrow = label(eyebrows[position], 12f, CYAN, Typeface.BOLD);
                eyebrow.setLetterSpacing(0.13f);
                LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                eyebrowParams.topMargin = dp(18);
                page.addView(eyebrow, eyebrowParams);

                TextView title = label(titles[position], 30f, TEXT, Typeface.BOLD);
                title.setLineSpacing(0f, 0.96f);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                titleParams.topMargin = dp(9);
                page.addView(title, titleParams);

                TextView body = label(bodies[position], 15.5f, MUTED, Typeface.NORMAL);
                body.setLineSpacing(dp(4), 1f);
                LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bodyParams.topMargin = dp(15);
                page.addView(body, bodyParams);

                addTip(page, firstTips[position], position == 0, dp(16));
                addTip(page, secondTips[position], position == 0, dp(8));

                TextView footer = label(footers[position], 13f,
                    Color.rgb(190, 218, 224), Typeface.BOLD);
                footer.setGravity(Gravity.CENTER);
                footer.setPadding(dp(14), 0, dp(14), 0);
                GradientDrawable pill = new GradientDrawable();
                pill.setColor(Color.argb(65, 116, 232, 232));
                pill.setStroke(dp(1), Color.argb(95, 116, 232, 232));
                pill.setCornerRadius(dp(15));
                footer.setBackground(pill);
                LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
                footerParams.topMargin = dp(16);
                page.addView(footer, footerParams);

                container.addView(scroll);
                return scroll;
            }

            @Override public void destroyItem(@NonNull ViewGroup container, int position,
                                              @NonNull Object object) {
                container.removeView((View) object);
            }

            private void addTip(@NonNull LinearLayout page, @StringRes int text,
                                boolean monospace, int topMargin) {
                TextView tip = label(text, 14f, Color.rgb(216, 235, 238), Typeface.BOLD);
                if (monospace) tip.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                tip.setGravity(Gravity.CENTER_VERTICAL);
                tip.setMinHeight(dp(44));
                tip.setPadding(dp(14), dp(10), dp(14), dp(10));
                GradientDrawable background = new GradientDrawable();
                background.setColor(Color.argb(38, 116, 232, 232));
                background.setStroke(dp(1), Color.argb(68, 116, 232, 232));
                background.setCornerRadius(dp(12));
                tip.setBackground(background);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = topMargin;
                page.addView(tip, params);
            }
        }
    }
}
