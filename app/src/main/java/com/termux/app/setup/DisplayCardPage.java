package com.termux.app.setup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.termux.GraphicsAccelerationFragment;
import com.termux.app.tour.TourEdition;
import com.termux.app.x11.GuiAppsSetup;
import com.termux.app.x11.GuiAppsSetupGuide;
import com.termux.app.x11.X11CliInstaller;
import com.termux.app.x11.X11GpuBenchmarkRun;
import com.termux.app.x11.X11GpuProbe;
import com.termux.app.x11.X11GpuProfileStore;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Display card's page: three steps in one view. 1: where desktop apps run. 2: the setup run,
 * with a progress line and a log peek ({@link SetupShellRun}). 3: the GPU profile, with the
 * benchmark ({@link X11GpuBenchmarkRun}) when the GPU is not a known freezer. Both runs belong to
 * the process, so they carry on while the page is hidden and are picked up again when it shows.
 */
final class DisplayCardPage implements SetupCardPage {

    static final String PREF_ROUTE = "setup_display_route";
    static final String PREF_DISTRO = "setup_display_distro";
    static final String PREF_USER = "setup_display_user";

    private static final String LOG_TAG = "DisplayCardPage";
    /** Where the benchmark script reads the profile table; the same file the Settings page writes. */
    private static final String TABLE_PATH = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH
        + "/x11-gpu-profiles.sh";
    private static final int LOG_HEIGHT_DP = 200;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "setup-display");
        thread.setDaemon(true);
        return thread;
    });
    /** The GPU probe, kept for the process: the renderer does not change under a running app. */
    @Nullable private static volatile X11GpuProbe.Result sProbe;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Context mContext;
    private Host mHost;
    private String mCardId;
    private LinearLayout mRoot;
    private LinearLayout mBody;
    private boolean mAlive;
    private int mSub;
    @Nullable private GuiAppsSetup.Route mRoute;
    private GuiAppsSetup.Distro mDistro = GuiAppsSetup.Distro.DEBIAN;
    private String mUser = DisplayCardLogic.DEFAULT_USER;
    /** Typed into the page and handed to the run; never stored. */
    private String mPassword = "";
    @Nullable private MaterialButton mContinue;
    private boolean mLogShown;
    private boolean mStarting;
    private boolean mPrepareFailed;
    private boolean mBenchStarting;
    private boolean mBenchNotStarted;
    private boolean mImportStarted;
    @Nullable private String mRenderedKey;
    @Nullable private TextView mProgress;
    @Nullable private TextView mLog;
    @Nullable private ScrollView mLogScroll;

    private final SetupShellRun.Listener mSetupListener = this::onRunUpdated;
    private final X11GpuBenchmarkRun.Listener mBenchListener = new X11GpuBenchmarkRun.Listener() {
        @Override public void onScreenUpdated() {
            onRunUpdated();
        }

        @Override public void onRunChanged() {
            onRunUpdated();
        }
    };

    @NonNull
    @Override
    public View createView(@NonNull Context context, @NonNull SetupCards.Card card,
                           @NonNull Host host) {
        mContext = context;
        mHost = host;
        mCardId = card.id;
        mAlive = true;
        mRenderedKey = null;
        TourEdition edition = edition();
        SharedPreferences prefs = GuiAppsSetupGuide.preferences(context);
        mRoute = DisplayCardLogic.resolveRoute(prefs.getString(PREF_ROUTE, null),
            DisplayCardLogic.offeredRoutes(edition));
        mDistro = GuiAppsSetup.Distro.of(prefs.getString(PREF_DISTRO, null));
        String storedUser = prefs.getString(PREF_USER, null);
        mUser = DisplayCardLogic.isValidUser(storedUser) ? storedUser : DisplayCardLogic.DEFAULT_USER;
        mPassword = "";
        mSub = Math.min(card.completed, 2);
        if (SetupShellRun.get().isActive()) mSub = 1;
        if (X11GpuBenchmarkRun.get(context).isActive()) mSub = 2;
        SetupShellRun.get().attach(mSetupListener);
        X11GpuBenchmarkRun.get(context).attach(mBenchListener);

        mRoot = new LinearLayout(context);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mBody = mRoot;
        render();
        return mRoot;
    }

    @Override
    public void onHidden() {
        mAlive = false;
        SetupShellRun.get().detach(mSetupListener);
        if (mContext != null) X11GpuBenchmarkRun.get(mContext).detach(mBenchListener);
        mProgress = null;
        mLog = null;
        mLogScroll = null;
    }

    // ---- shared pieces ----

    @NonNull
    private TourEdition edition() {
        return TourEdition.of(mContext.getPackageName());
    }

    private int dp(float value) {
        return SetupUi.dp(mContext, value);
    }

    private void addText(@NonNull String text, float sp, boolean bold, boolean quiet, int topDp) {
        TextView view = SetupUi.text(mContext, sp, bold,
            quiet ? SetupUi.onSurfaceVariant(mRoot) : SetupUi.onSurface(mRoot));
        view.setText(text);
        mRoot.addView(view, SetupUi.matchWrap(dp(topDp)));
    }

    private void header(int number, int nameRes, int titleRes) {
        addText(mContext.getString(R.string.setup_display_step_header, number,
            mContext.getString(nameRes)), 13f, true, true, 0);
        addText(mContext.getString(titleRes), 22f, true, false, 4);
    }

    @NonNull
    private MaterialButton button(int textRes, boolean primary, @NonNull Runnable action) {
        MaterialButton button = primary ? new MaterialButton(mContext)
            : new MaterialButton(mContext, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        button.setText(textRes);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    /** The foot: Back at the left, the given buttons at the right. */
    private void footer(@NonNull MaterialButton... right) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(button(R.string.setup_back, false, this::back));
        row.addView(new View(mContext), new LinearLayout.LayoutParams(0, 1, 1f));
        for (int i = 0; i < right.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) params.leftMargin = dp(8);
            row.addView(right[i], params);
        }
        mRoot.addView(row, SetupUi.matchWrap(dp(20)));
    }

    private void back() {
        if (mSub == 0) {
            mHost.back();
            return;
        }
        mSub--;
        render();
    }

    private void render() {
        mRoot.removeAllViews();
        mProgress = null;
        mLog = null;
        mLogScroll = null;
        switch (mSub) {
            case 0: renderWhere(); break;
            case 1: renderInstall(); break;
            default: renderGraphics(); break;
        }
        mRenderedKey = key();
    }

    /** What the visible sub-page was built from; a change in it means a rebuild, not a text update. */
    @NonNull
    private String key() {
        switch (mSub) {
            case 1:
                return "1:" + SetupShellRun.get().state() + ":" + GuiAppsSetup.markerFile().exists()
                    + ":" + mStarting + ":" + mPrepareFailed;
            case 2:
                return "2:" + (sProbe != null) + ":" + X11GpuBenchmarkRun.get(mContext).state()
                    + ":" + mBenchStarting + ":" + mBenchNotStarted;
            default:
                return "0";
        }
    }

    /** A run changed: rebuild when the state moved, else only refresh the progress and the log. */
    private void onRunUpdated() {
        if (!mAlive || mRoot == null) return;
        if (mSub != 0 && !key().equals(mRenderedKey)) {
            render();
            return;
        }
        updateLive();
    }

    private void updateLive() {
        TextView progress = mProgress;
        if (mSub == 1) {
            String transcript = SetupShellRun.get().transcript();
            if (progress != null) {
                String line = DisplayCardLogic.progressLine(transcript);
                progress.setText(line.isEmpty() ? mContext.getString(R.string.setup_display_starting)
                    : line);
            }
            setLog(transcript);
        } else if (mSub == 2) {
            X11GpuBenchmarkRun run = X11GpuBenchmarkRun.get(mContext);
            if (progress != null) {
                progress.setText(X11GpuBenchmarkRun.statusText(mContext, run.state(), run.step()));
            }
            setLog(benchmarkTranscript(run));
        }
    }

    @NonNull
    private static String benchmarkTranscript(@NonNull X11GpuBenchmarkRun run) {
        TerminalSession session = run.session();
        TerminalEmulator emulator = session == null ? null : session.getEmulator();
        if (emulator == null) return "";
        String text = emulator.getScreen().getTranscriptText();
        return text == null ? "" : text;
    }

    private void setLog(@NonNull String transcript) {
        TextView log = mLog;
        ScrollView scroll = mLogScroll;
        if (log == null || scroll == null) return;
        log.setText(DisplayCardLogic.lastLines(transcript, DisplayCardLogic.LOG_LINES));
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    /** The progress line and, under a Show log / Hide log button, the log peek. */
    private void addProgressAndLog(@NonNull String transcript, @NonNull String firstLine) {
        TextView progress = SetupUi.text(mContext, 14f, false, SetupUi.onSurface(mRoot));
        progress.setText(firstLine);
        progress.setMaxLines(1);
        progress.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mRoot.addView(progress, SetupUi.matchWrap(dp(12)));
        mProgress = progress;
        addLogToggle(transcript);
    }

    private void addLogToggle(@NonNull String transcript) {
        MaterialButton toggle = button(mLogShown ? R.string.setup_display_log_hide
            : R.string.setup_display_log_show, false, () -> {
                mLogShown = !mLogShown;
                render();
            });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        mRoot.addView(toggle, params);
        if (!mLogShown) return;
        ScrollView scroll = new ScrollView(mContext);
        scroll.setNestedScrollingEnabled(true);
        GradientDrawable ground = new GradientDrawable();
        ground.setCornerRadius(dp(8));
        ground.setColor(ColorUtils.setAlphaComponent(SetupUi.onSurface(mRoot), 20));
        scroll.setBackground(ground);
        TextView log = new TextView(mContext);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        log.setTextColor(SetupUi.onSurface(mRoot));
        log.setPadding(dp(8), dp(6), dp(8), dp(6));
        scroll.addView(log, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        mRoot.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            dp(LOG_HEIGHT_DP)));
        mLog = log;
        mLogScroll = scroll;
        setLog(transcript);
    }

    // ---- 1: where ----

    private void renderWhere() {
        header(1, R.string.setup_display_step_where, R.string.setup_display_where_title);
        List<GuiAppsSetup.Route> offered = DisplayCardLogic.offeredRoutes(edition());
        if (offered.isEmpty()) {
            addText(mContext.getString(R.string.setup_display_no_route), 14f, false, true, 8);
            footer();
            return;
        }
        for (GuiAppsSetup.Route route : offered) {
            boolean distro = route == GuiAppsSetup.Route.DISTRO;
            addChoice(distro ? R.string.setup_display_route_distro : R.string.setup_display_route_x11,
                distro ? R.string.setup_display_route_distro_line
                    : R.string.setup_display_route_x11_line, route == mRoute, () -> {
                        mRoute = route;
                        render();
                    });
            if (distro && route == mRoute) {
                addDistroChips();
                addAccountFields();
            }
        }
        MaterialButton next = button(R.string.setup_cards_continue, true, this::continueFromWhere);
        mContinue = next;
        updateContinue();
        footer(next);
    }

    /** Continue needs a route and, for a distro, a valid account. */
    private void updateContinue() {
        MaterialButton next = mContinue;
        if (next == null) return;
        next.setEnabled(mRoute != null && (mRoute != GuiAppsSetup.Route.DISTRO
            || (DisplayCardLogic.isValidUser(mUser) && DisplayCardLogic.isValidPassword(mPassword))));
    }

    /** The Linux account the run sets up: a name and a password (kept in this page only). */
    private void addAccountFields() {
        mRoot.addView(field(R.string.setup_display_username, mUser, false,
            R.string.setup_display_user_error, DisplayCardLogic::isValidUser, text -> mUser = text),
            SetupUi.matchWrap(dp(12)));
        mRoot.addView(field(R.string.setup_display_password, mPassword, true,
            R.string.setup_display_password_error, DisplayCardLogic::isValidPassword,
            text -> mPassword = text), SetupUi.matchWrap(dp(8)));
    }

    @NonNull
    private View field(int hintRes, @NonNull String value, boolean password, int errorRes,
                       @NonNull java.util.function.Predicate<String> valid,
                       @NonNull java.util.function.Consumer<String> store) {
        TextInputLayout layout = new TextInputLayout(mContext);
        layout.setHint(mContext.getString(hintRes));
        if (password) layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        TextInputEditText edit = new TextInputEditText(layout.getContext());
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        edit.setText(value);
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override public void afterTextChanged(Editable s) {
                String text = s.toString();
                store.accept(text);
                boolean ok = valid.test(text);
                layout.setError(ok || text.isEmpty() ? null : mContext.getString(errorRes));
                updateContinue();
            }
        });
        layout.addView(edit, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private void addChoice(int titleRes, int lineRes, boolean chosen, @NonNull Runnable action) {
        LinearLayout card = new LinearLayout(mContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setClickable(true);
        card.setFocusable(true);
        TextView title = SetupUi.text(mContext, 16f, true, SetupUi.onSurface(mRoot));
        title.setText(titleRes);
        card.addView(title, SetupUi.matchWrap(0));
        TextView line = SetupUi.text(mContext, 14f, false, SetupUi.onSurfaceVariant(mRoot));
        line.setText(lineRes);
        card.addView(line, SetupUi.matchWrap(dp(2)));
        SetupUi.dressChoice(card, chosen);
        card.setOnClickListener(v -> action.run());
        mRoot.addView(card, SetupUi.matchWrap(dp(12)));
    }

    private void addDistroChips() {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        GuiAppsSetup.Distro[] distros = {GuiAppsSetup.Distro.DEBIAN, GuiAppsSetup.Distro.UBUNTU,
            GuiAppsSetup.Distro.ARCH};
        int[] names = {R.string.setup_display_distro_debian, R.string.setup_display_distro_ubuntu,
            R.string.setup_display_distro_arch};
        for (int i = 0; i < distros.length; i++) {
            GuiAppsSetup.Distro distro = distros[i];
            TextView chip = SetupUi.text(mContext, 14f, distro == mDistro, SetupUi.onSurface(mRoot));
            chip.setText(names[i]);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setClickable(true);
            chip.setFocusable(true);
            SetupUi.dressChoice(chip, distro == mDistro);
            chip.setOnClickListener(v -> {
                mDistro = distro;
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) params.leftMargin = dp(8);
            row.addView(chip, params);
        }
        mRoot.addView(row, SetupUi.matchWrap(dp(10)));
    }

    private void continueFromWhere() {
        if (mRoute == null) return;
        if (mRoute == GuiAppsSetup.Route.DISTRO && !(DisplayCardLogic.isValidUser(mUser)
            && DisplayCardLogic.isValidPassword(mPassword))) return;
        SharedPreferences.Editor edit = GuiAppsSetupGuide.preferences(mContext).edit()
            .putString(PREF_ROUTE, mRoute.key)
            .putString(PREF_DISTRO, mDistro.key);
        // The name is kept for next time; the password never is.
        if (mRoute == GuiAppsSetup.Route.DISTRO) edit.putString(PREF_USER, mUser);
        edit.apply();
        mHost.completeStep(mCardId, 0);
        mSub = 1;
        render();
    }

    // ---- 2: install ----

    private void renderInstall() {
        header(2, R.string.setup_display_step_install, R.string.setup_display_install_title);
        GuiAppsSetup.Route route = mRoute;
        if (route == null) {
            addText(mContext.getString(R.string.setup_display_no_route), 14f, false, true, 8);
            footer();
            return;
        }
        addText(route == GuiAppsSetup.Route.DISTRO
            ? mContext.getString(R.string.setup_display_will_run_distro, distroName())
            : mContext.getString(R.string.setup_display_will_run_x11), 14f, false, true, 8);

        SetupShellRun run = SetupShellRun.get();
        boolean marker = GuiAppsSetup.markerFile().exists();
        switch (run.state()) {
            case RUNNING:
                addProgressAndLog(run.transcript(), progressOrStarting(run.transcript()));
                footer();
                return;
            case FINISHED:
                if (DisplayCardLogic.succeeded(run.exitStatus(), marker)) {
                    mHost.completeStep(mCardId, 1);
                    addText(mContext.getString(R.string.setup_display_install_done), 14f, true,
                        false, 12);
                    addLogToggle(run.transcript());
                    footer(button(R.string.setup_cards_continue, true, this::toGraphics));
                    return;
                }
                renderInstallFailed(run);
                return;
            case FAILED:
                renderInstallFailed(run);
                return;
            default:
                break;
        }
        if (mStarting) {
            addText(mContext.getString(R.string.setup_display_starting), 14f, false, false, 12);
            footer();
        } else if (mPrepareFailed) {
            addText(mContext.getString(R.string.setup_display_install_failed), 14f, true, false, 12);
            footer(button(R.string.setup_display_try_again, true, this::startSetup));
        } else if (marker) {
            addText(mContext.getString(R.string.setup_display_install_already), 14f, true, false, 12);
            footer(button(R.string.setup_display_run_again, false, this::startSetup),
                button(R.string.setup_cards_continue, true, this::toGraphics));
        } else {
            footer(button(R.string.setup_display_start, true, this::startSetup));
        }
    }

    private void renderInstallFailed(@NonNull SetupShellRun run) {
        addText(mContext.getString(R.string.setup_display_install_failed), 14f, true, false, 12);
        addLogToggle(run.transcript());
        footer(button(R.string.setup_display_try_again, true, () -> {
            SetupShellRun.get().dismiss();
            startSetup();
        }));
    }

    @NonNull
    private String progressOrStarting(@NonNull String transcript) {
        String line = DisplayCardLogic.progressLine(transcript);
        return line.isEmpty() ? mContext.getString(R.string.setup_display_starting) : line;
    }

    @NonNull
    private String distroName() {
        switch (mDistro) {
            case UBUNTU: return mContext.getString(R.string.setup_display_distro_ubuntu);
            case ARCH: return mContext.getString(R.string.setup_display_distro_arch);
            default: return mContext.getString(R.string.setup_display_distro_debian);
        }
    }

    private void toGraphics() {
        mSub = 2;
        render();
    }

    private void startSetup() {
        GuiAppsSetup.Route route = mRoute;
        if (route == null || mStarting) return;
        boolean distro = route == GuiAppsSetup.Route.DISTRO;
        if (distro && !(DisplayCardLogic.isValidUser(mUser)
            && DisplayCardLogic.isValidPassword(mPassword))) {
            // The password lives in the page only; a page that was left has lost it, so ask again.
            mSub = 0;
            render();
            return;
        }
        Context app = mContext.getApplicationContext();
        String password = distro ? mPassword : null;
        String command = DisplayCardLogic.command(route, mDistro, edition(),
            distro ? mUser : null, password);
        mStarting = true;
        mPrepareFailed = false;
        SetupShellRun.get().dismiss();
        render();
        EXECUTOR.execute(() -> {
            SetupShellRun.Launch launch = null;
            try {
                launch = SetupShellRun.prepare(app, command);
            } catch (java.io.IOException | RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "The setup could not be prepared", e);
            }
            SetupShellRun.Launch prepared = launch;
            mHandler.post(() -> {
                mStarting = false;
                boolean started = prepared != null && SetupShellRun.get().start(prepared, password);
                mPrepareFailed = !started && SetupShellRun.get().state() != SetupShellRun.State.FAILED;
                if (mAlive) render();
            });
        });
    }

    // ---- 3: graphics ----

    private void renderGraphics() {
        header(3, R.string.setup_display_step_graphics, R.string.setup_display_graphics_title);
        X11GpuProbe.Result probe = sProbe;
        if (probe == null) {
            addText(mContext.getString(R.string.setup_display_checking), 14f, false, true, 8);
            footer();
            probeAsync();
            return;
        }
        if (!probe.gpu.trim().isEmpty()) {
            addText(mContext.getString(R.string.setup_display_detected, probe.gpu), 14f, false,
                true, 8);
        }
        X11GpuProfileStore store = new X11GpuProfileStore(mContext);
        if (probe.knownFreeze) {
            store.setPick(X11GpuProbe.Profile.SOFTWARE.id);
            addText(mContext.getString(R.string.setup_display_freeze), 14f, true, false, 12);
            footer(button(R.string.setup_display_done, true, this::finishCard));
            return;
        }
        X11GpuBenchmarkRun run = X11GpuBenchmarkRun.get(mContext);
        switch (run.state()) {
            case RUNNING:
            case STOPPING:
                addProgressAndLog(benchmarkTranscript(run),
                    X11GpuBenchmarkRun.statusText(mContext, run.state(), run.step()));
                footer();
                return;
            case FINISHED:
                importResultsOnce(store);
                addText(mContext.getString(R.string.setup_display_benchmark_done), 14f, true, false, 12);
                addLogToggle(benchmarkTranscript(run));
                footer(button(R.string.setup_display_done, true, this::finishCard));
                return;
            case FAILED:
                store.setPick(X11GpuProbe.Profile.SOFTWARE.id);
                addText(mContext.getString(R.string.setup_display_benchmark_failed), 14f, true, false, 12);
                addLogToggle(benchmarkTranscript(run));
                footer(button(R.string.setup_display_done, true, this::finishCard));
                return;
            default:
                break;
        }
        if (mBenchStarting) {
            addText(mContext.getString(R.string.setup_display_starting), 14f, false, false, 12);
            footer();
            return;
        }
        if (mBenchNotStarted) {
            store.setPick(X11GpuProbe.Profile.SOFTWARE.id);
            addText(mContext.getString(R.string.setup_display_benchmark_failed), 14f, true, false, 12);
            footer(button(R.string.setup_display_done, true, this::finishCard));
            return;
        }
        footer(button(R.string.setup_display_benchmark_skip, false, () -> {
            new X11GpuProfileStore(mContext).setPick(X11GpuProfileStore.AUTO);
            finishCard();
        }), button(R.string.setup_display_benchmark_run, true, this::startBenchmark));
    }

    /** Takes the benchmark's results file into the store, once per run; the pick stays Auto. */
    private void importResultsOnce(@NonNull X11GpuProfileStore store) {
        if (mImportStarted) return;
        mImportStarted = true;
        EXECUTOR.execute(() -> store.importResults(new File(X11GpuProfileStore.RESULTS_FILE_PATH)));
    }

    private void finishCard() {
        X11GpuBenchmarkRun run = X11GpuBenchmarkRun.get(mContext);
        // A finished run is put away so the Settings page opens on its own state.
        if (!run.isActive()) run.dismiss();
        mHost.completeCard(mCardId);
    }

    private void probeAsync() {
        Context app = mContext.getApplicationContext();
        EXECUTOR.execute(() -> {
            if (sProbe != null) return;
            try {
                sProbe = X11GpuProbe.probe(app);
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "The GPU could not be probed", e);
                return;
            }
            mHandler.post(() -> {
                if (mAlive && mSub == 2) render();
            });
        });
    }

    private void startBenchmark() {
        X11GpuProbe.Result probe = sProbe;
        if (probe == null || mBenchStarting) return;
        Context app = mContext.getApplicationContext();
        String table = probe.toTable();
        mBenchStarting = true;
        mBenchNotStarted = false;
        mImportStarted = false;
        X11GpuBenchmarkRun.get(app).dismiss();
        render();
        EXECUTOR.execute(() -> {
            X11GpuBenchmarkRun.Launch launch = null;
            try {
                File file = new File(TABLE_PATH);
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory()) parent.mkdirs();
                Files.write(file.toPath(), table.getBytes(StandardCharsets.UTF_8));
                launch = X11GpuBenchmarkRun.prepare(app, X11CliInstaller.GPU_SETUP_SCRIPT_PATH,
                    "--yes", "--profiles=" + TABLE_PATH);
            } catch (java.io.IOException | RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "The benchmark could not be prepared", e);
            }
            X11GpuBenchmarkRun.Launch prepared = launch;
            mHandler.post(() -> {
                mBenchStarting = false;
                if (prepared != null) {
                    Intent page = SettingsActivity.createFragmentIntent(app,
                        GraphicsAccelerationFragment.class, R.string.settings_x11_gpu_title);
                    page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (X11GpuBenchmarkRun.get(app).start(prepared, page)) return;
                }
                // It would not start: the same end as a failed run, software.
                mBenchNotStarted = true;
                if (mAlive) render();
            });
        });
    }
}
