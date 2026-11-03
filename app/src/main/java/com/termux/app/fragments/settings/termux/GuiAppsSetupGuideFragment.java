package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.material.M3;
import com.termux.app.notice.AppNotice;
import com.termux.app.AppEdition;
import com.termux.app.x11.DistroSetupStore;
import com.termux.app.x11.GuiAppsSetup;
import com.termux.app.x11.GuiAppsSetupGuide;
import com.termux.app.x11.GuiAppsSetupGuide.Step;
import com.termux.app.x11.X11GpuProbe;
import com.termux.app.x11.X11GpuProfileStore;
import com.termux.shared.interact.ShareUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Set up Linux apps: a stepped guide, one decision per screen. The steps that exist depend on the
 * edition and on the source picked ({@link GuiAppsSetupGuide#stepsFor}); the progress line counts
 * only those.
 *
 * <p>The guide installs nothing itself. It builds a command with {@link GuiAppsSetup}, puts it on
 * the clipboard, and the user pastes it into the terminal, where the minutes of downloading and
 * the one password are plainly visible. Every choice is written straight to the launcher's own
 * preferences, so the guide opens on what was picked last time.
 */
@Keep
public class GuiAppsSetupGuideFragment extends Fragment {

    /** Test seam: the edition to behave as, instead of the one the package name says. */
    @VisibleForTesting static final String ARG_EDITION = "edition";
    private static final String STATE_STEP = "gui_apps_guide_step";
    private static final String NIX_DOCS_URL =
        "https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Nix_Package_Management.md";
    /** How long the launcher is given to come forward before the notice is raised on it. */
    private static final long NOTICE_DELAY_MS = 350L;

    /** The Intent that opens the guide from outside Settings. */
    @NonNull
    public static Intent intent(@NonNull Context context) {
        return SettingsActivity.createFragmentIntent(context,
            GuiAppsSetupGuideFragment.class, R.string.settings_gui_apps_title);
    }

    private AppEdition mEdition = AppEdition.TERMUX;
    private SharedPreferences mPrefs;
    @Nullable private Step mStep;
    private boolean mCopied;
    @Nullable private LinearLayout mProgress;
    @Nullable private LinearLayout mBody;
    @Nullable private ScrollView mScroll;
    @Nullable private View mFooter;
    @Nullable private MaterialButton mBack;
    @Nullable private MaterialButton mNext;
    @Nullable private TextView mGraphicsSummary;

    private final ExecutorService mProbeExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gui-apps-gpu-probe");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final OnBackPressedCallback mBackCallback = new OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed() { back(); }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().getOnBackPressedDispatcher().addCallback(this, mBackCallback);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = inflater.getContext();
        Bundle args = getArguments();
        String override = args == null ? null : args.getString(ARG_EDITION);
        mEdition = override != null ? AppEdition.valueOf(override)
            : AppEdition.of(context.getPackageName());
        mPrefs = GuiAppsSetupGuide.preferences(context);
        if (savedInstanceState != null) {
            String saved = savedInstanceState.getString(STATE_STEP);
            if (saved != null) mStep = Step.valueOf(saved);
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        mProgress = new LinearLayout(context);
        mProgress.setOrientation(LinearLayout.HORIZONTAL);
        mProgress.setGravity(Gravity.CENTER_VERTICAL);
        mProgress.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        root.addView(mProgress, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mScroll = new ScrollView(context);
        mBody = new LinearLayout(context);
        mBody.setOrientation(LinearLayout.VERTICAL);
        mBody.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        mScroll.addView(mBody, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 16));
        mBack = new MaterialButton(context);
        mBack.setText(R.string.settings_gui_apps_back);
        styleText(mBack);
        mBack.setOnClickListener(v -> back());
        footer.addView(mBack, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        footer.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));
        mNext = new MaterialButton(context);
        mNext.setMinWidth(dp(context, 96));
        mNext.setOnClickListener(v -> next());
        footer.addView(mNext, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mFooter = footer;
        root.addView(footer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        render();
        return root;
    }

    /** A text button: no fill, no outline, primary label. */
    private static void styleText(@NonNull MaterialButton button) {
        int primary = M3.primary(button.getContext());
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setTextColor(primary);
        button.setRippleColor(ColorStateList.valueOf(M3.stateLayer(primary, M3.STATE_PRESSED)));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mStep != null) outState.putString(STATE_STEP, mStep.name());
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity != null) activity.setTitle(R.string.settings_gui_apps_title);
        // Every return, so a pick made on the Graphics acceleration page shows at once.
        if (mStep == Step.GRAPHICS) probeGpu();
    }

    @Override
    public void onDestroy() {
        mProbeExecutor.shutdownNow();
        super.onDestroy();
    }

    // ---- state -------------------------------------------------------------------------------

    @NonNull
    @VisibleForTesting
    List<Step> steps() {
        return GuiAppsSetupGuide.stepsFor(mEdition, GuiAppsSetupGuide.read(mPrefs, mEdition).route);
    }

    @VisibleForTesting
    @Nullable
    Step currentStep() {
        return mStep;
    }

    @VisibleForTesting
    void goTo(@NonNull Step step) {
        mStep = step;
        render();
    }

    private void next() {
        List<Step> steps = steps();
        int at = steps.indexOf(mStep);
        if (at < 0 || at == steps.size() - 1) {
            finishGuide();
            return;
        }
        goTo(steps.get(at + 1));
    }

    private void back() {
        List<Step> steps = steps();
        int at = steps.indexOf(mStep);
        if (at > 0) {
            goTo(steps.get(at - 1));
            return;
        }
        // The first step: Back leaves the guide, as it would any page.
        mBackCallback.setEnabled(false);
        FragmentActivity activity = getActivity();
        if (activity != null) activity.getOnBackPressedDispatcher().onBackPressed();
    }

    /**
     * Done and Skip: with the command copied, Settings closes so the terminal is in front and the
     * notice follows it there; without, the guide just closes. The notice is raised after the
     * close, against the application, so it lands on the launcher the user is being sent to.
     */
    private void finishGuide() {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        if (!mCopied) {
            mBackCallback.setEnabled(false);
            activity.getOnBackPressedDispatcher().onBackPressed();
            return;
        }
        Context appContext = activity.getApplicationContext();
        activity.finish();
        // A handler of its own: finish() can run onDestroy inside the delay.
        new Handler(Looper.getMainLooper()).postDelayed(
            () -> AppNotice.show(appContext, R.string.settings_gui_apps_copied), NOTICE_DELAY_MS);
    }

    // ---- drawing -----------------------------------------------------------------------------

    private void render() {
        if (mBody == null || mProgress == null || mNext == null || mBack == null
            || mFooter == null) return;
        Context context = mBody.getContext();
        mBody.removeAllViews();
        mProgress.removeAllViews();
        mGraphicsSummary = null;
        List<Step> steps = steps();
        if (steps.isEmpty()) {
            renderNix(context);
            return;
        }
        if (mStep == null || !steps.contains(mStep)) mStep = steps.get(0);
        mFooter.setVisibility(View.VISIBLE);
        mProgress.setVisibility(View.VISIBLE);
        int at = steps.indexOf(mStep);
        mBackCallback.setEnabled(at > 0);
        for (int i = 0; i < steps.size(); i++) mProgress.addView(pill(context, steps, i, at));
        boolean last = at == steps.size() - 1;
        mNext.setText(last ? R.string.settings_gui_apps_done : R.string.settings_gui_apps_next);
        switch (mStep) {
            case SOURCE: renderSource(context); break;
            case DISTRO: renderDistro(context); break;
            case STARTERS: renderStarters(context); break;
            case COMMAND: renderCommand(context); break;
            default: renderGraphics(context); break;
        }
        if (mScroll != null) mScroll.scrollTo(0, 0);
    }

    /** completed = primary, current = primary container with its label, upcoming = outline. */
    @NonNull
    private View pill(@NonNull Context context, @NonNull List<Step> steps, int index, int current) {
        Step step = steps.get(index);
        TextView pill = new TextView(context);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        pill.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
        pill.setMinHeight(dp(context, 32));
        pill.setMinWidth(dp(context, 32));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, 16));
        LinearLayout.LayoutParams lp;
        String name = stepName(context, step);
        if (index < current) {
            shape.setColor(M3.primary(context));
            pill.setTextColor(M3.color(context, com.google.android.material.R.attr.colorOnPrimary,
                com.google.android.material.R.color.m3_sys_color_dark_on_primary));
            pill.setText("✓");
            pill.setContentDescription(context.getString(
                R.string.settings_gui_apps_step_pill_done, index + 1, steps.size(), name));
            lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else if (index == current) {
            shape.setColor(M3.color(context,
                com.google.android.material.R.attr.colorPrimaryContainer,
                com.google.android.material.R.color.m3_sys_color_dark_primary_container));
            pill.setTextColor(M3.color(context,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                com.google.android.material.R.color.m3_sys_color_dark_on_primary_container));
            pill.setText((index + 1) + "  " + name);
            pill.setEllipsize(TextUtils.TruncateAt.END);
            pill.setContentDescription(context.getString(
                R.string.settings_gui_apps_step_pill, index + 1, steps.size(), name));
            // Takes what the others leave, and gives up its label's tail before anything clips.
            lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        } else {
            shape.setColor(Color.TRANSPARENT);
            shape.setStroke(dp(context, 1), M3.color(context,
                com.google.android.material.R.attr.colorOutline,
                com.google.android.material.R.color.m3_sys_color_dark_outline));
            pill.setTextColor(M3.onSurfaceVariant(context));
            pill.setText(String.valueOf(index + 1));
            pill.setContentDescription(context.getString(
                R.string.settings_gui_apps_step_pill, index + 1, steps.size(), name));
            lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        pill.setBackground(shape);
        if (index > 0) lp.setMarginStart(dp(context, 8));
        pill.setLayoutParams(lp);
        return pill;
    }

    @NonNull
    private static String stepName(@NonNull Context context, @NonNull Step step) {
        switch (step) {
            case SOURCE: return context.getString(R.string.settings_gui_apps_step_source);
            case DISTRO: return context.getString(R.string.settings_gui_apps_step_linux);
            case STARTERS: return context.getString(R.string.settings_gui_apps_step_apps);
            case COMMAND: return context.getString(R.string.settings_gui_apps_step_command);
            default: return context.getString(R.string.settings_gui_apps_step_graphics);
        }
    }

    private void heading(@NonNull Context context, int headline, int sentence) {
        TextView title = new TextView(context);
        title.setText(headline);
        title.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        title.setTextColor(M3.onSurface(context));
        ViewCompat.setAccessibilityHeading(title, true);
        mBody.addView(title);
        TextView body = new TextView(context);
        body.setText(sentence);
        body.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        body.setTextColor(M3.onSurfaceVariant(context));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, 8);
        lp.bottomMargin = dp(context, 16);
        mBody.addView(body, lp);
    }

    private void renderSource(@NonNull Context context) {
        heading(context, R.string.settings_gui_apps_source_headline,
            R.string.settings_gui_apps_source_sentence);
        GuiAppsSetup.Route route = GuiAppsSetupGuide.read(mPrefs, mEdition).route;
        for (GuiAppsSetup.Route candidate : GuiAppsSetup.routesFor(mEdition)) {
            boolean termux = candidate == GuiAppsSetup.Route.X11_REPO;
            mBody.addView(optionCard(context,
                context.getString(termux ? R.string.settings_gui_apps_source_termux
                    : R.string.settings_gui_apps_source_linux),
                context.getString(termux ? R.string.settings_gui_apps_source_termux_summary
                    : R.string.settings_gui_apps_source_linux_summary),
                null, candidate == route, false, v -> {
                    mPrefs.edit().putString(GuiAppsSetupGuide.KEY_ROUTE, candidate.key).apply();
                    render();
                }));
        }
    }

    private void renderDistro(@NonNull Context context) {
        heading(context, R.string.settings_gui_apps_linux_headline,
            R.string.settings_gui_apps_linux_sentence);
        GuiAppsSetup.Distro picked = GuiAppsSetupGuide.read(mPrefs, mEdition).distro;
        for (GuiAppsSetup.Distro distro : GuiAppsSetup.Distro.values()) {
            int summary = distro == GuiAppsSetup.Distro.UBUNTU
                ? R.string.settings_gui_apps_distro_ubuntu_summary
                : distro == GuiAppsSetup.Distro.ARCH ? R.string.settings_gui_apps_distro_arch_summary
                : R.string.settings_gui_apps_distro_debian_summary;
            mBody.addView(optionCard(context, GuiAppsSetupGuide.distroName(context, distro),
                context.getString(summary),
                distro == GuiAppsSetup.Distro.DEBIAN
                    ? context.getString(R.string.settings_gui_apps_recommended) : null,
                distro == picked, false, v -> {
                    mPrefs.edit().putString(GuiAppsSetupGuide.KEY_DISTRO, distro.key).apply();
                    render();
                }));
        }
    }

    private void renderStarters(@NonNull Context context) {
        heading(context, R.string.settings_gui_apps_apps_headline,
            R.string.settings_gui_apps_apps_sentence);
        GuiAppsSetupGuide.Choices choices = GuiAppsSetupGuide.read(mPrefs, mEdition);
        for (GuiAppsSetup.StarterApp app
            : GuiAppsSetupGuide.startersOffered(mEdition, choices.route)) {
            boolean ticked = choices.starters.contains(app);
            String title = context.getString(app == GuiAppsSetup.StarterApp.BROWSER
                ? R.string.settings_gui_apps_starter_browser
                : R.string.settings_gui_apps_starter_files);
            // The app the command would actually fetch: Firefox ESR on Debian, Falkon on Ubuntu.
            String summary = GuiAppsSetup.starterAppName(choices.route, choices.distro, app);
            mBody.addView(optionCard(context, title, summary, null, ticked, true, v -> {
                mPrefs.edit().putBoolean(GuiAppsSetupGuide.KEY_STARTER_PREFIX + app.key, !ticked)
                    .apply();
                render();
            }));
        }
    }

    private void renderCommand(@NonNull Context context) {
        heading(context, R.string.settings_gui_apps_command_headline,
            R.string.settings_gui_apps_command_sentence);
        // Copy is the step's one action, so it sits under the heading where it is always in
        // view; the command below it can run long and scrolls with the body.
        MaterialButton copy = new MaterialButton(context);
        copy.setText(mCopied ? R.string.settings_gui_apps_copy_button_done
            : R.string.settings_gui_apps_copy_button);
        copy.setOnClickListener(v -> {
            copyCommand(context);
            copy.setText(R.string.settings_gui_apps_copy_button_done);
        });
        mBody.addView(copy, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String command = GuiAppsSetupGuide.command(mPrefs, mEdition);
        TextView code = new TextView(context);
        code.setText(command == null ? "" : command);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(12);
        code.setTextColor(M3.onSurface(context));
        code.setTextIsSelectable(true);
        int pad = dp(context, 16);
        code.setPadding(pad, pad, pad, pad);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(M3.surfaceContainerLow(context));
        shape.setCornerRadius(dp(context, 16));
        code.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, 16);
        mBody.addView(code, lp);
    }

    private void renderGraphics(@NonNull Context context) {
        heading(context, R.string.settings_gui_apps_graphics_headline,
            R.string.settings_gui_apps_graphics_sentence);
        TextView summary = new TextView(context);
        summary.setText(R.string.settings_x11_gpu_probing);
        summary.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        summary.setTextColor(M3.onSurface(context));
        int pad = dp(context, 16);
        summary.setPadding(pad, pad, pad, pad);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(M3.surfaceContainerHigh(context));
        shape.setCornerRadius(dp(context, 16));
        summary.setBackground(shape);
        mBody.addView(summary, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mGraphicsSummary = summary;
        MaterialButton open = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        open.setText(R.string.settings_gui_apps_graphics_open);
        open.setOnClickListener(v -> {
            Activity activity = getActivity();
            if (activity instanceof SettingsActivity) {
                ((SettingsActivity) activity).openScreen(GraphicsAccelerationFragment.class,
                    R.string.settings_x11_gpu_title, null);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, 12);
        mBody.addView(open, lp);
        MaterialButton skip = new MaterialButton(context);
        skip.setText(R.string.settings_gui_apps_skip);
        styleText(skip);
        skip.setOnClickListener(v -> finishGuide());
        mBody.addView(skip, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (isResumed()) probeGpu();
    }

    /** nix has no command to build: its own advice and the page that explains it. */
    private void renderNix(@NonNull Context context) {
        mProgress.setVisibility(View.GONE);
        mFooter.setVisibility(View.GONE);
        mBackCallback.setEnabled(false);
        heading(context, R.string.settings_gui_apps_nix_headline, R.string.settings_gui_apps_nix_card);
        MaterialButton docs = new MaterialButton(context);
        docs.setText(R.string.settings_gui_apps_nix_docs);
        docs.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(NIX_DOCS_URL));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                v.getContext().startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // No browser to hand this to; nothing else sensible to do.
            }
        });
        mBody.addView(docs, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * A full-width choice: a radio (one pick) or a checkbox (any), the title with an optional
     * badge that wraps under it when the text is large, and one plain line of consequence.
     */
    @NonNull
    private View optionCard(@NonNull Context context, @NonNull String title, @NonNull String summary,
                            @Nullable String badge, boolean selected, boolean multi,
                            @NonNull View.OnClickListener onClick) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(context, 16);
        card.setPadding(pad, pad, dp(context, 8), pad);
        card.setMinimumHeight(dp(context, 72));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, 16));
        int container = M3.color(context, com.google.android.material.R.attr.colorPrimaryContainer,
            com.google.android.material.R.color.m3_sys_color_dark_primary_container);
        shape.setColor(selected ? container : M3.surfaceContainerHigh(context));
        card.setBackground(shape);
        int text = selected
            ? M3.color(context, com.google.android.material.R.attr.colorOnPrimaryContainer,
                com.google.android.material.R.color.m3_sys_color_dark_on_primary_container)
            : M3.onSurface(context);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        FlowRow titleRow = new FlowRow(context, dp(context, 8));
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        titleView.setTextColor(text);
        titleRow.addView(titleView);
        if (badge != null) {
            TextView badgeView = new TextView(context);
            badgeView.setText(badge);
            badgeView.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
            badgeView.setTextColor(M3.color(context, com.google.android.material.R.attr.colorOnPrimary,
                com.google.android.material.R.color.m3_sys_color_dark_on_primary));
            badgeView.setPadding(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 2));
            GradientDrawable badgeShape = new GradientDrawable();
            badgeShape.setCornerRadius(dp(context, 12));
            badgeShape.setColor(M3.primary(context));
            badgeView.setBackground(badgeShape);
            titleRow.addView(badgeView);
        }
        column.addView(titleRow, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView summaryView = new TextView(context);
        summaryView.setText(summary);
        summaryView.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        summaryView.setTextColor(text);
        column.addView(summaryView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(column, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        CompoundButton mark = multi ? new MaterialCheckBox(context) : new MaterialRadioButton(context);
        mark.setChecked(selected);
        // The card is the control; the mark only shows its state.
        mark.setClickable(false);
        mark.setFocusable(false);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(mark, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(onClick);
        String described = badge == null ? title : title + ", " + badge;
        card.setContentDescription(described + ", " + summary);
        ViewCompat.setAccessibilityDelegate(card, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                                                          @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(multi ? "android.widget.CheckBox" : "android.widget.RadioButton");
                info.setCheckable(true);
                info.setChecked(selected);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(context, 8);
        card.setLayoutParams(lp);
        return card;
    }

    // ---- actions -----------------------------------------------------------------------------

    private void copyCommand(@NonNull Context context) {
        String command = GuiAppsSetupGuide.command(mPrefs, mEdition);
        if (command == null) return;
        Context appContext = context.getApplicationContext();
        ShareUtils.copyTextToClipboard(appContext,
            context.getString(R.string.settings_gui_apps_clipboard_label), command, null);
        mCopied = true;
        mPrefs.edit().putBoolean(GuiAppsSetupGuide.KEY_COPIED, true).apply();
        // Copying answers the Display place's offer, so the place stops making it.
        try {
            DistroSetupStore.dismissCurrent(appContext);
        } catch (RuntimeException e) {
            // Nothing the user asked for depends on this; the command is already on the clipboard.
        }
    }

    /** The Graphics step's text: the pick and its last result, read off the main thread. */
    private void probeGpu() {
        Context context = getContext();
        if (context == null || mProbeExecutor.isShutdown()) return;
        Context app = context.getApplicationContext();
        mProbeExecutor.execute(() -> {
            X11GpuProbe.Result result = X11GpuProbe.probe(app);
            X11GpuProfileStore store = new X11GpuProfileStore(app);
            // A benchmark finished in the terminal since the guide opened is news here too.
            store.importResults(new java.io.File(X11GpuProfileStore.RESULTS_FILE_PATH));
            String summary = GraphicsAccelerationFragment.rowSummary(app, result, store.pick(),
                store.results());
            mHandler.post(() -> {
                if (!isAdded() || mGraphicsSummary == null) return;
                mGraphicsSummary.setText(summary);
            });
        });
    }

    private static int dp(@NonNull Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * A row of views that wraps to a second line when they no longer fit: the title and its
     * "Recommended" badge sit side by side, and at a large font the badge drops under the title.
     */
    static final class FlowRow extends ViewGroup {
        private final int mGap;

        FlowRow(@NonNull Context context, int gap) {
            super(context);
            mGap = gap;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int maxWidth = MeasureSpec.getSize(widthSpec);
            int x = 0, y = 0, rowHeight = 0, widest = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                child.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int w = child.getMeasuredWidth();
                if (x > 0 && x + mGap + w > maxWidth) {
                    y += rowHeight + mGap / 2;
                    x = 0;
                    rowHeight = 0;
                }
                x += (x > 0 ? mGap : 0) + w;
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
                widest = Math.max(widest, x);
            }
            setMeasuredDimension(resolveSize(widest, widthSpec), y + rowHeight);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxWidth = r - l;
            int x = 0, y = 0, rowHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                int w = child.getMeasuredWidth();
                if (x > 0 && x + mGap + w > maxWidth) {
                    y += rowHeight + mGap / 2;
                    x = 0;
                    rowHeight = 0;
                }
                if (x > 0) x += mGap;
                child.layout(x, y, x + w, y + child.getMeasuredHeight());
                x += w;
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            }
        }
    }
}
