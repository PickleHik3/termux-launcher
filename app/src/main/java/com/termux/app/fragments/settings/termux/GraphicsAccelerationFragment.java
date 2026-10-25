package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.notice.AppNotice;
import com.termux.app.x11.LinuxAppCatalog;
import com.termux.app.x11.X11CliInstaller;
import com.termux.app.x11.X11GpuBenchmarkEstimate;
import com.termux.app.x11.X11GpuBenchmarkRun;
import com.termux.app.x11.X11GpuProbe;
import com.termux.app.x11.X11GpuProfileStore;
import com.termux.app.x11.X11LinuxAppRunner;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Graphics acceleration (#162): the user, not the launcher, picks how Linux apps draw. The phone's
 * graphics chip on top, a warning when it is one known to freeze with the virgl profiles, every
 * profile that fits it with whether it is installed and its last benchmark result, picked like a
 * radio list (Automatic keeps the launcher's ranking), a note that software always works, and the
 * apps that draw in software whatever the pick. Run benchmark floats over the list's foot.
 *
 * <p>The benchmark's Home is the model, in shape and in parts: one list rebuilt whole on every
 * change and diffed by {@link TaiBenchListAdapter}, rows built in code from {@link TaiBenchViews},
 * the store and the device read on a single thread of its own and posted back. The benchmark
 * itself is {@code termux-x11-gpu-setup}, which installs packages and tests each profile on a
 * private display: minutes of package-manager output a terminal shows better than a progress bar.
 * Run benchmark asks once first, with the sizes the script works out ({@code --estimate}), then
 * starts it through {@link X11GpuBenchmarkRun}, which owns the run for the process. The floating
 * bar turns into a read-only terminal panel on that run, with its step and a Stop button, and
 * re-attaches whenever the page is shown again. The page hands the script
 * {@link X11GpuProbe.Result#toTable}, so the profile table has one owner, and reads its results
 * file back when the run ends or the page is next shown ({@link X11GpuProfileStore#importResults}).
 */
@Keep
public class GraphicsAccelerationFragment extends Fragment implements TaiBenchListAdapter.Factory {
    private static final int TYPE_DEVICE = 0;
    static final int TYPE_HINT = 1;
    private static final int TYPE_SECTION = 2;
    static final int TYPE_PROFILE = 3;
    private static final int TYPE_NOTE = 4;
    private static final int TYPE_APPS = 5;

    /** The run panel's terminal text, before the font scale. */
    private static final float TERMINAL_TEXT_SP = 11f;
    /** The run panel's terminal height, and the most of a short (landscape) screen it takes. */
    private static final int TERMINAL_HEIGHT_DP = 220;
    private static final float TERMINAL_HEIGHT_SCREEN_SHARE = 0.35f;
    private static final int TERMINAL_CORNER_DP = 8;
    private static final int TERMINAL_INSET_DP = 6;
    /** Where the page leaves the profile table for the script it starts; the prefix's tmp. */
    private static final String TABLE_PATH = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH
        + "/x11-gpu-profiles.sh";

    /** A probe answer for tests, which have no GL context or getprop to probe. */
    @VisibleForTesting @Nullable static volatile X11GpuProbe.Result probeForTest;

    /** Everything the page shows, read off the main thread at once. */
    private static final class Facts {
        @NonNull final X11GpuProbe.Result gpu;
        @NonNull final String pick;
        @Nullable final X11GpuProfileStore.Results results;
        @NonNull final Set<String> softwareApps;
        @NonNull final List<LinuxAppCatalog.LinuxApp> apps;
        final boolean scriptInstalled;

        Facts(@NonNull X11GpuProbe.Result gpu, @NonNull String pick,
              @Nullable X11GpuProfileStore.Results results, @NonNull Set<String> softwareApps,
              @NonNull List<LinuxAppCatalog.LinuxApp> apps, boolean scriptInstalled) {
            this.gpu = gpu;
            this.pick = pick;
            this.results = results;
            this.softwareApps = softwareApps;
            this.apps = apps;
            this.scriptInstalled = scriptInstalled;
        }
    }

    /** One choice of the radio list: Automatic ({@link #profile} null) or a profile that fits. */
    private static final class ProfileRow {
        @NonNull final String id;
        @Nullable final X11GpuProbe.Recommendation profile;
        final boolean picked;
        @Nullable final X11GpuProfileStore.Score score;
        /** What Automatic resolves to now; null on a profile's own row. */
        @Nullable final X11GpuProbe.Profile resolves;
        final boolean freeze;

        ProfileRow(@NonNull String id, @Nullable X11GpuProbe.Recommendation profile, boolean picked,
                   @Nullable X11GpuProfileStore.Score score, @Nullable X11GpuProbe.Profile resolves,
                   boolean freeze) {
            this.id = id;
            this.profile = profile;
            this.picked = picked;
            this.score = score;
            this.resolves = resolves;
            this.freeze = freeze;
        }

        @NonNull
        String signature() {
            return id + '|' + picked + '|' + (profile == null ? "" : profile.installed) + '|'
                + (score == null ? "" : score.outcome.id + score.score + score.renderer) + '|' + resolves
                + '|' + freeze;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "x11-gpu-page");
        thread.setDaemon(true);
        return thread;
    });
    private final TaiBenchListAdapter adapter = new TaiBenchListAdapter(this);
    @Nullable private Facts facts;
    @Nullable private View bar;
    @Nullable private X11GpuProfileStore store;
    /** The bar's run panel, built with the bar; its terminal view exists only while there is a run. */
    @Nullable private RunPanel runPanel;
    @Nullable private TerminalView terminal;
    @Nullable private AlertDialog confirmDialog;
    /** The run state the bar last showed, so a run that just ended reads its results once. */
    @Nullable private X11GpuBenchmarkRun.State shownRunState;

    private final X11GpuBenchmarkRun.Listener runListener = new X11GpuBenchmarkRun.Listener() {
        @Override
        public void onScreenUpdated() {
            TerminalView view = terminal;
            if (view != null) view.onScreenUpdated();
        }

        @Override
        public void onRunChanged() {
            bindBar(facts);
        }
    };

    /** The views of the run panel: the step line, the terminal's frame and the two buttons. */
    private static final class RunPanel {
        @NonNull final View root;
        @NonNull final TextView status;
        @NonNull final FrameLayout screen;
        @NonNull final TextView stop;
        @NonNull final TextView close;

        RunPanel(@NonNull View root, @NonNull TextView status, @NonNull FrameLayout screen,
                 @NonNull TextView stop, @NonNull TextView close) {
            this.root = root;
            this.status = status;
            this.screen = screen;
            this.stop = stop;
            this.close = close;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        store = new X11GpuProfileStore(context);
        RecyclerView list = new RecyclerView(context);
        list.setId(R.id.x11_gpu_list);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, TaiBenchViews.dp(context, 32));
        if (TaiMotion.reduced(context)) {
            list.setItemAnimator(null);
        } else {
            DefaultItemAnimator animator = new DefaultItemAnimator();
            animator.setSupportsChangeAnimations(false);
            animator.setMoveDuration(TaiMotion.ARRIVE_MS);
            list.setItemAnimator(animator);
        }
        list.setAdapter(adapter);
        FrameLayout root = new FrameLayout(context);
        root.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View floating = createBar(context);
        root.addView(floating, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        // The last row scrolls clear of the bar.
        floating.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int pad = (bottom - top) + TaiBenchViews.dp(context, 8);
            if (list.getPaddingBottom() != pad) list.setPadding(0, 0, 0, pad);
        });
        bar = floating;
        return root;
    }

    @Override
    public void onDestroyView() {
        AlertDialog dialog = confirmDialog;
        if (dialog != null) dialog.dismiss();
        confirmDialog = null;
        bar = null;
        runPanel = null;
        terminal = null;
        shownRunState = null;
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        // The run went on while the page was away: the panel picks up its live or finished output.
        X11GpuBenchmarkRun.get(requireContext()).attach(runListener);
        rebuild();
        TerminalView view = terminal;
        if (view != null) view.onScreenUpdated();
        // A benchmark that ended while the page was away left a new results file.
        refresh();
    }

    @Override
    public void onStop() {
        X11GpuBenchmarkRun.get(requireContext()).detach(runListener);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_x11_gpu_title);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    /** Probes, takes in a newer results file and reads the store and the apps, off the main thread. */
    private void refresh() {
        Context context = getContext();
        X11GpuProfileStore kept = store;
        if (context == null || kept == null || executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            Facts gathered;
            try {
                X11GpuProbe.Result override = probeForTest;
                X11GpuProbe.Result gpu = override != null ? override : X11GpuProbe.probe(app);
                kept.importResults(new File(X11GpuProfileStore.RESULTS_FILE_PATH));
                List<LinuxAppCatalog.LinuxApp> apps;
                try {
                    apps = LinuxAppCatalog.scan(LinuxAppCatalog.roots());
                } catch (RuntimeException e) {
                    apps = Collections.emptyList();
                }
                gathered = new Facts(gpu, kept.pick(), kept.results(), kept.softwareApps(), apps,
                    new File(X11CliInstaller.GPU_SETUP_SCRIPT_PATH).isFile());
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage("GraphicsAcceleration", "The graphics page could not be read", e);
                return;
            }
            Facts result = gathered;
            handler.post(() -> {
                if (!isAdded()) return;
                facts = result;
                rebuild();
            });
        });
    }

    /** Re-reads only the store after the user changed it here: no probe, no file work. */
    private void reread() {
        Facts f = facts;
        X11GpuProfileStore kept = store;
        if (f == null || kept == null) return;
        facts = new Facts(f.gpu, kept.pick(), f.results, kept.softwareApps(), f.apps, f.scriptInstalled);
        rebuild();
    }

    // ---- the list ----

    private void rebuild() {
        Context context = getContext();
        if (context == null) return;
        List<TaiBenchListAdapter.Item> items = new ArrayList<>();
        Facts f = facts;
        items.add(new TaiBenchListAdapter.Item(TYPE_DEVICE, "device",
            f == null ? "" : f.gpu.gpu + '|' + (f.results == null ? 0L : f.results.timestamp), f));
        if (f != null) {
            if (f.gpu.knownFreeze) {
                items.add(new TaiBenchListAdapter.Item(TYPE_HINT, "hint", "hint", null));
            }
            String header = getString(R.string.settings_x11_gpu_section_profiles);
            items.add(new TaiBenchListAdapter.Item(TYPE_SECTION, "profiles", header, header));
            boolean advancedShown = false;
            for (ProfileRow row : profileRows(f)) {
                // On a known-freeze chip every GPU profile is an advanced, warned choice, below
                // Automatic and Software.
                if (row.freeze && !advancedShown) {
                    String advanced = getString(R.string.settings_x11_gpu_section_advanced);
                    items.add(new TaiBenchListAdapter.Item(TYPE_SECTION, "advanced", advanced, advanced));
                    advancedShown = true;
                }
                items.add(new TaiBenchListAdapter.Item(TYPE_PROFILE, row.id, row.signature(), row));
            }
            String note = getString(R.string.settings_x11_gpu_note_software);
            items.add(new TaiBenchListAdapter.Item(TYPE_NOTE, "note", note, note));
            if (!f.apps.isEmpty()) {
                StringBuilder signature = new StringBuilder();
                for (LinuxAppCatalog.LinuxApp app : f.apps) {
                    signature.append(app.id).append(f.softwareApps.contains(app.id) ? "+" : "-").append('|');
                }
                items.add(new TaiBenchListAdapter.Item(TYPE_APPS, "apps", signature.toString(), f));
            }
        }
        adapter.submit(items);
        bindBar(f);
    }

    /** Automatic first, then every profile that fits the GPU, in the probe's order. */
    @NonNull
    private static List<ProfileRow> profileRows(@NonNull Facts f) {
        List<ProfileRow> rows = new ArrayList<>();
        X11LinuxAppRunner.GpuChoice auto = X11LinuxAppRunner.installedEnv(f.gpu, X11GpuProfileStore.AUTO, false);
        X11GpuProbe.Profile resolves = auto.profile != null ? auto.profile : X11GpuProbe.Profile.SOFTWARE;
        rows.add(new ProfileRow(X11GpuProfileStore.AUTO, null, X11GpuProfileStore.AUTO.equals(f.pick),
            null, resolves, false));
        for (X11GpuProbe.Recommendation r : f.gpu.ranked) {
            boolean freeze = f.gpu.knownFreeze && r.profile != X11GpuProbe.Profile.SOFTWARE;
            rows.add(new ProfileRow(r.profile.id, r, r.profile.id.equals(f.pick),
                f.results == null ? null : f.results.get(r.profile.id), null, freeze));
        }
        return rows;
    }

    // ---- rows ----

    @NonNull
    @Override
    public View create(@NonNull ViewGroup parent, int type) {
        Context context = parent.getContext();
        switch (type) {
            case TYPE_DEVICE: return createDevice(context);
            case TYPE_HINT: return createHint(context);
            case TYPE_SECTION: return TaiBenchViews.sectionHeader(context, "", "");
            case TYPE_PROFILE: return createProfile(context);
            case TYPE_APPS: return createApps(context);
            default: return createNote(context);
        }
    }

    @Override
    public void bind(@NonNull View view, @NonNull TaiBenchListAdapter.Item item) {
        switch (item.type) {
            case TYPE_DEVICE: bindDevice(view, (Facts) item.data); break;
            case TYPE_HINT: break;
            case TYPE_SECTION: ((TextView) ((ViewGroup) view).getChildAt(0)).setText((String) item.data); break;
            case TYPE_PROFILE: bindProfile(view, (ProfileRow) item.data); break;
            case TYPE_APPS: bindApps(view, (Facts) item.data); break;
            default: ((TextView) view.findViewById(R.id.x11_gpu_text)).setText((String) item.data); break;
        }
    }

    @NonNull
    private View createDevice(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        card.core.addView(TaiBenchViews.title(context, getString(R.string.settings_x11_gpu_device_title)));
        TextView gpu = TaiBenchViews.body(context, "");
        gpu.setId(R.id.x11_gpu_text);
        card.core.addView(gpu, TaiBenchViews.block(context, 4));
        TextView last = TaiBenchViews.mono(context, "");
        last.setId(R.id.x11_gpu_detail);
        card.core.addView(last, TaiBenchViews.block(context, 4));
        return card.outer;
    }

    private void bindDevice(@NonNull View view, @Nullable Facts f) {
        TextView gpu = view.findViewById(R.id.x11_gpu_text);
        TextView last = view.findViewById(R.id.x11_gpu_detail);
        gpu.setText(f == null ? getString(R.string.settings_x11_gpu_device_reading) : f.gpu.gpu);
        String lastRun = f == null || f.results == null ? ""
            : getString(R.string.settings_x11_gpu_last_run, TaiBenchViews.ago(f.results.timestamp));
        last.setText(lastRun);
        last.setVisibility(lastRun.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private View createHint(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        TextView pill = TaiBenchViews.pill(context, getString(R.string.settings_x11_gpu_hint_pill),
            TaiModelCentreRows.Tone.WARN);
        card.core.addView(pill, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.core.addView(TaiBenchViews.title(context, getString(R.string.settings_x11_gpu_hint_title)),
            TaiBenchViews.block(context, 8));
        card.core.addView(TaiBenchViews.body(context, getString(R.string.settings_x11_gpu_hint_body)),
            TaiBenchViews.block(context, 4));
        return card.outer;
    }

    @NonNull
    private View createProfile(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        MaterialRadioButton radio = new MaterialRadioButton(context);
        radio.setId(R.id.x11_gpu_radio);
        // The whole card is the target; the button only shows the state.
        radio.setClickable(false);
        radio.setFocusable(false);
        radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.addView(radio, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout middle = new LinearLayout(context);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setMinimumWidth(0);
        TextView title = TaiBenchViews.title(context, "");
        title.setId(R.id.x11_gpu_title);
        middle.addView(title);
        TextView summary = TaiBenchViews.body(context, "");
        summary.setId(R.id.x11_gpu_text);
        middle.addView(summary, TaiBenchViews.block(context, 2));
        TextView status = TaiBenchViews.body(context, "");
        status.setId(R.id.x11_gpu_status);
        middle.addView(status, TaiBenchViews.block(context, 4));
        TextView details = TaiBenchViews.mono(context, "");
        details.setId(R.id.x11_gpu_detail);
        middle.addView(details, TaiBenchViews.block(context, 2));
        TextView pill = TaiBenchViews.pill(context, "", TaiModelCentreRows.Tone.WARN);
        pill.setId(R.id.x11_gpu_pill);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillParams.topMargin = TaiBenchViews.dp(context, 6);
        middle.addView(pill, pillParams);
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        middleParams.setMarginStart(TaiBenchViews.dp(context, 4));
        line.addView(middle, middleParams);
        card.core.addView(line);
        android.util.TypedValue ripple = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        card.core.setBackgroundResource(ripple.resourceId);
        card.core.setOnClickListener(v -> {
            Object id = v.getTag();
            if (id instanceof String) pick((String) id);
        });
        // The row's bind reaches the click target through the outer view's tag.
        card.outer.setTag(card.core);
        return card.outer;
    }

    private void bindProfile(@NonNull View view, @NonNull ProfileRow row) {
        Context context = view.getContext();
        View target = (View) view.getTag();
        target.setTag(row.id);
        MaterialRadioButton radio = view.findViewById(R.id.x11_gpu_radio);
        if (radio.isChecked() != row.picked) radio.setChecked(row.picked);
        TextView title = view.findViewById(R.id.x11_gpu_title);
        TextView summary = view.findViewById(R.id.x11_gpu_text);
        TextView status = view.findViewById(R.id.x11_gpu_status);
        TextView details = view.findViewById(R.id.x11_gpu_detail);
        TextView pill = view.findViewById(R.id.x11_gpu_pill);
        String statusText;
        String detailsText;
        if (row.profile == null) {
            title.setText(R.string.settings_x11_gpu_auto_title);
            summary.setText(getString(R.string.settings_x11_gpu_auto_summary,
                profileName(context, row.resolves == null ? X11GpuProbe.Profile.SOFTWARE : row.resolves)));
            statusText = "";
            detailsText = "";
        } else {
            X11GpuProbe.Profile profile = row.profile.profile;
            title.setText(profileName(context, profile));
            summary.setText(profileSummary(context, profile));
            String installed = row.profile.installed || profile == X11GpuProbe.Profile.SOFTWARE
                ? getString(R.string.settings_x11_gpu_installed)
                : getString(R.string.settings_x11_gpu_needs, String.join(", ", row.profile.packages));
            statusText = installed + " · " + scoreText(context, row.score);
            // Mechanism names stay on this one line: the profile's id and what drew the test.
            detailsText = row.score == null || row.score.renderer.isEmpty() ? profile.id
                : profile.id + " · " + row.score.renderer;
        }
        status.setText(statusText);
        status.setVisibility(statusText.isEmpty() ? View.GONE : View.VISIBLE);
        details.setText(detailsText);
        details.setVisibility(detailsText.isEmpty() ? View.GONE : View.VISIBLE);
        String warn = !row.freeze || row.profile == null ? ""
            : getString(X11GpuProbe.isVirgl(row.profile.profile) ? R.string.settings_x11_gpu_hint_pill
                : R.string.settings_x11_gpu_untested_pill);
        pill.setText(warn);
        pill.setVisibility(warn.isEmpty() ? View.GONE : View.VISIBLE);
        target.setContentDescription(title.getText() + ". " + summary.getText()
            + (statusText.isEmpty() ? "" : ". " + statusText) + (warn.isEmpty() ? "" : ". " + warn));
    }

    @NonNull
    private View createNote(@NonNull Context context) {
        TextView note = TaiBenchViews.body(context, "");
        note.setId(R.id.x11_gpu_text);
        note.setPadding(TaiBenchViews.dp(context, 22), TaiBenchViews.dp(context, 8), TaiBenchViews.dp(context, 22), TaiBenchViews.dp(context, 4));
        return note;
    }

    @NonNull
    private View createApps(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        card.core.addView(TaiBenchViews.title(context, getString(R.string.settings_x11_gpu_apps_title)));
        card.core.addView(TaiBenchViews.body(context, getString(R.string.settings_x11_gpu_apps_summary)),
            TaiBenchViews.block(context, 2));
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setId(R.id.x11_gpu_rows);
        card.core.addView(rows, TaiBenchViews.block(context, 6));
        return card.outer;
    }

    private void bindApps(@NonNull View view, @NonNull Facts f) {
        Context context = view.getContext();
        LinearLayout rows = view.findViewById(R.id.x11_gpu_rows);
        rows.removeAllViews();
        for (LinuxAppCatalog.LinuxApp app : f.apps) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(TaiBenchViews.dp(context, 48));
            TextView name = TaiBenchViews.body(context, app.name);
            line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialSwitch toggle = new MaterialSwitch(context);
            toggle.setChecked(f.softwareApps.contains(app.id));
            toggle.setContentDescription(app.name);
            String id = app.id;
            toggle.setOnCheckedChangeListener((button, checked) -> {
                X11GpuProfileStore kept = store;
                if (kept == null) return;
                kept.setDrawsInSoftware(id, checked);
                reread();
            });
            line.addView(toggle);
            rows.addView(line, TaiBenchViews.block(context, 2));
        }
    }

    // ---- the floating bar ----

    @NonNull
    private View createBar(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        card.outer.setPadding(TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 6), TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 12));
        card.shell.setElevation(TaiBenchViews.dp(context, 6));
        TextView line = TaiBenchViews.mono(context, getString(R.string.settings_x11_gpu_run_summary));
        line.setId(R.id.x11_gpu_text);
        card.core.addView(line);
        TextView run = TaiBenchViews.goButton(context, getString(R.string.settings_x11_gpu_run_action));
        run.setId(R.id.x11_gpu_action);
        run.setMinHeight(TaiBenchViews.dp(context, 44));
        run.setOnClickListener(v -> {
            TaiMotion.tick(v);
            runBenchmark();
        });
        card.core.addView(run, TaiBenchViews.block(context, 10));
        TaiBenchViews.setEnabled(run, false);
        runPanel = createRunPanel(context);
        card.core.addView(runPanel.root);
        return card.outer;
    }

    /** The bar while a run is on: its step, its output, and Stop (or Close once it has ended). */
    @NonNull
    private RunPanel createRunPanel(@NonNull Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(View.GONE);
        TextView status = TaiBenchViews.body(context, "");
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(status);
        FrameLayout screen = new FrameLayout(context);
        int inset = TaiBenchViews.dp(context, TERMINAL_INSET_DP);
        screen.setPadding(inset, inset, inset, inset);
        GradientDrawable ground = new GradientDrawable();
        ground.setCornerRadius(TaiBenchViews.dp(context, TERMINAL_CORNER_DP));
        screen.setBackground(ground);
        screen.setClipToOutline(true);
        int height = Math.min(TaiBenchViews.dp(context, TERMINAL_HEIGHT_DP),
            Math.round(context.getResources().getDisplayMetrics().heightPixels * TERMINAL_HEIGHT_SCREEN_SHARE));
        LinearLayout.LayoutParams screenParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        screenParams.topMargin = TaiBenchViews.dp(context, 8);
        panel.addView(screen, screenParams);
        TextView stop = TaiBenchViews.errorButton(context, getString(R.string.settings_x11_gpu_run_stop));
        stop.setMinHeight(TaiBenchViews.dp(context, 44));
        stop.setOnClickListener(v -> {
            TaiMotion.tick(v);
            X11GpuBenchmarkRun.get(v.getContext()).stop();
        });
        panel.addView(stop, TaiBenchViews.block(context, 10));
        TextView close = TaiBenchViews.ghostButton(context, getString(R.string.settings_x11_gpu_run_close));
        close.setMinHeight(TaiBenchViews.dp(context, 44));
        close.setOnClickListener(v -> {
            TaiMotion.tick(v);
            X11GpuBenchmarkRun.get(v.getContext()).dismiss();
        });
        panel.addView(close, TaiBenchViews.block(context, 10));
        return new RunPanel(panel, status, screen, stop, close);
    }

    private void bindBar(@Nullable Facts f) {
        View view = bar;
        RunPanel panel = runPanel;
        Context context = getContext();
        if (view == null || panel == null || context == null) return;
        X11GpuBenchmarkRun run = X11GpuBenchmarkRun.get(context);
        X11GpuBenchmarkRun.State state = run.state();
        X11GpuBenchmarkRun.State before = shownRunState;
        shownRunState = state;
        // A run that ended while the page watched has just written its results.
        if (before != null && before != state && !run.isActive() && state != X11GpuBenchmarkRun.State.IDLE) {
            refresh();
        }
        TextView line = view.findViewById(R.id.x11_gpu_text);
        View action = view.findViewById(R.id.x11_gpu_action);
        boolean showingRun = state != X11GpuBenchmarkRun.State.IDLE;
        setShown(line, !showingRun);
        setShown(action, !showingRun);
        setShown(panel.root, showingRun);
        if (!showingRun) {
            showTerminal(panel, null);
            boolean ready = f != null && f.scriptInstalled;
            line.setText(f != null && !f.scriptInstalled ? R.string.settings_x11_gpu_run_needs_display
                : R.string.settings_x11_gpu_run_summary);
            TaiBenchViews.setEnabled(action, ready);
            return;
        }
        String status = X11GpuBenchmarkRun.statusText(context, state, run.step());
        if (!TextUtils.equals(panel.status.getText(), status)) panel.status.setText(status);
        setShown(panel.stop, run.isActive());
        TaiBenchViews.setEnabled(panel.stop, state == X11GpuBenchmarkRun.State.RUNNING);
        setShown(panel.close, !run.isActive());
        showTerminal(panel, run.session());
    }

    private static void setShown(@NonNull View view, boolean shown) {
        int visibility = shown ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    /**
     * Puts a read-only terminal view on {@code session} in the panel, or takes it out. A view is
     * made per session rather than switched between them, so a new run starts on a clean view.
     */
    private void showTerminal(@NonNull RunPanel panel, @Nullable TerminalSession session) {
        TerminalView view = terminal;
        if (view != null && view.getCurrentSession() == session) return;
        if (view != null) {
            panel.screen.removeView(view);
            terminal = null;
        }
        if (session == null) return;
        Context context = panel.screen.getContext();
        view = new TerminalView(context, null);
        view.setTerminalViewClient(new ReadOnlyTerminalClient());
        view.setTextSize(Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            TERMINAL_TEXT_SP, context.getResources().getDisplayMetrics())));
        view.setCursorSuppressed(true);
        // Nothing is typed into the run: no focus, so no keyboard and no keys reach it.
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        view.setContentDescription(getString(R.string.settings_x11_gpu_run_output));
        view.attachSession(session);
        // The ground is the terminal's own, so its colours read the same in a light or dark theme.
        // The view paints it too: it declares itself opaque and leaves default cells unpainted.
        int ground = terminalBackground(session);
        ((GradientDrawable) panel.screen.getBackground()).setColor(ground);
        view.setBackgroundColor(ground);
        panel.screen.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        terminal = view;
    }

    private static int terminalBackground(@NonNull TerminalSession session) {
        TerminalEmulator emulator = session.getEmulator();
        int color = emulator != null ? emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]
            : TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND];
        return color | 0xFF000000;
    }

    /** The panel's terminal shows output and scrolls; it takes no keys, no text and no selection. */
    private static final class ReadOnlyTerminalClient extends TermuxTerminalViewClientBase {
        @Override
        public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
            return true;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent e) {
            return true;
        }

        @Override
        public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
            return true;
        }

        @Override
        public boolean onLongPress(MotionEvent event) {
            return true;
        }

        @Override
        public boolean isTerminalViewSelected() {
            return false;
        }
    }

    // ---- actions ----

    /** The user's pick, kept and shown at once; the next app opened gets it. */
    @VisibleForTesting
    void pick(@NonNull String id) {
        X11GpuProfileStore kept = store;
        if (kept == null) return;
        kept.setPick(id);
        reread();
    }

    /**
     * Writes the profile table where the script can read it, then asks once before anything runs:
     * what the run downloads, the space it takes while it runs and what it gives back, as the
     * script works them out. Run starts it on this page.
     */
    private void runBenchmark() {
        Facts f = facts;
        Context context = getContext();
        if (f == null || context == null || !f.scriptInstalled || executor.isShutdown()) return;
        if (X11GpuBenchmarkRun.get(context).isActive() || confirmDialog != null) return;
        Context app = context.getApplicationContext();
        String table = f.gpu.toTable();
        executor.execute(() -> {
            // A script written by an older build would refuse --estimate or --profiles and stop at
            // once; the launcher rewrites it on its next start (X11CliInstaller), so say that instead.
            if (!knowsPageOptions(new File(X11CliInstaller.GPU_SETUP_SCRIPT_PATH))) {
                handler.post(() -> AppNotice.show(app, R.string.settings_x11_gpu_run_outdated));
                return;
            }
            boolean written;
            try {
                File file = new File(TABLE_PATH);
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory()) parent.mkdirs();
                Files.write(file.toPath(), table.getBytes(StandardCharsets.UTF_8));
                written = true;
            } catch (Exception e) {
                Logger.logStackTraceWithMessage("GraphicsAcceleration", "The profile table could not be written", e);
                written = false;
            }
            boolean ok = written;
            handler.post(() -> {
                if (ok && isAdded() && getView() != null) confirm();
            });
        });
    }

    /** The one question before a run, filled in with the sizes once the script has worked them out. */
    private void confirm() {
        Context context = requireContext();
        Context app = context.getApplicationContext();
        X11GpuBenchmarkRun.Estimate estimate = new X11GpuBenchmarkRun.Estimate();
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_x11_gpu_confirm_title)
            .setMessage(confirmMessage(context, null))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_x11_gpu_confirm_run, (d, which) -> start(app))
            .create();
        dialog.setOnDismissListener(d -> {
            estimate.cancel();
            if (confirmDialog == d) confirmDialog = null;
        });
        confirmDialog = dialog;
        dialog.show();
        // Run waits for the sizes: the estimate may be refreshing the package lists, and a run
        // started beside it would find them locked.
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        Thread worker = new Thread(() -> {
            X11GpuBenchmarkEstimate sizes = estimate.run(app, X11CliInstaller.GPU_SETUP_SCRIPT_PATH, TABLE_PATH);
            handler.post(() -> {
                if (confirmDialog != dialog || !dialog.isShowing()) return;
                dialog.setMessage(confirmMessage(dialog.getContext(), sizes));
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
            });
        }, "x11-gpu-estimate");
        worker.setDaemon(true);
        worker.start();
    }

    /** The confirmation's text: the three sizes (or why they are missing), then what the run does. */
    @VisibleForTesting
    @NonNull
    static String confirmMessage(@NonNull Context context, @Nullable X11GpuBenchmarkEstimate sizes) {
        StringBuilder text = new StringBuilder();
        if (sizes == null) {
            text.append(context.getString(R.string.settings_x11_gpu_confirm_working));
        } else if (sizes.noSizesKnown()) {
            text.append(context.getString(R.string.settings_x11_gpu_confirm_unknown));
        } else {
            sizeLine(context, text, R.string.settings_x11_gpu_confirm_download, sizes.downloadBytes);
            sizeLine(context, text, R.string.settings_x11_gpu_confirm_space, sizes.installBytes);
            sizeLine(context, text, R.string.settings_x11_gpu_confirm_given_back, sizes.removableBytes);
            if (!sizes.sizesKnown()) {
                text.append('\n').append(context.getString(R.string.settings_x11_gpu_confirm_partial));
            }
        }
        return text.append("\n\n").append(context.getString(R.string.settings_x11_gpu_confirm_note)).toString();
    }

    private static void sizeLine(@NonNull Context context, @NonNull StringBuilder text, int line, long bytes) {
        if (bytes == X11GpuBenchmarkEstimate.UNKNOWN) return;
        if (text.length() > 0) text.append('\n');
        text.append(context.getString(line, Formatter.formatShortFileSize(context, bytes)));
    }

    /** Starts the confirmed run, with no question of its own left to ask. */
    private void start(@NonNull Context app) {
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            X11GpuBenchmarkRun.Launch launch;
            try {
                launch = X11GpuBenchmarkRun.prepare(app, X11CliInstaller.GPU_SETUP_SCRIPT_PATH,
                    "--yes", "--profiles=" + TABLE_PATH);
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage("GraphicsAcceleration", "The benchmark could not be prepared", e);
                return;
            }
            handler.post(() -> {
                Intent page = SettingsActivity.createFragmentIntent(app, GraphicsAccelerationFragment.class,
                    R.string.settings_x11_gpu_title);
                page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                X11GpuBenchmarkRun.get(app).start(launch, page);
            });
        });
    }

    /**
     * Whether the installed benchmark script takes the options this page runs it with:
     * {@code --profiles=} and {@code --estimate}. File work.
     */
    @VisibleForTesting
    static boolean knowsPageOptions(@NonNull File script) {
        try {
            String text = new String(Files.readAllBytes(script.toPath()), StandardCharsets.UTF_8);
            return text.contains("--profiles=*)") && text.contains("--estimate");
        } catch (Exception e) {
            return false;
        }
    }

    // ---- words ----

    @NonNull
    static String profileName(@NonNull Context context, @NonNull X11GpuProbe.Profile profile) {
        switch (profile) {
            case TURNIP_ZINK: return context.getString(R.string.settings_x11_gpu_profile_turnip_zink);
            case VIRGL: return context.getString(R.string.settings_x11_gpu_profile_virgl);
            case VIRGL_ANGLE: return context.getString(R.string.settings_x11_gpu_profile_virgl_angle);
            case VULKAN_WRAPPER: return context.getString(R.string.settings_x11_gpu_profile_vulkan_wrapper);
            default: return context.getString(R.string.settings_x11_gpu_profile_software);
        }
    }

    @NonNull
    private static String profileSummary(@NonNull Context context, @NonNull X11GpuProbe.Profile profile) {
        switch (profile) {
            case TURNIP_ZINK: return context.getString(R.string.settings_x11_gpu_profile_turnip_zink_summary);
            case VIRGL: return context.getString(R.string.settings_x11_gpu_profile_virgl_summary);
            case VIRGL_ANGLE: return context.getString(R.string.settings_x11_gpu_profile_virgl_angle_summary);
            case VULKAN_WRAPPER: return context.getString(R.string.settings_x11_gpu_profile_vulkan_wrapper_summary);
            default: return context.getString(R.string.settings_x11_gpu_profile_software_summary);
        }
    }

    @NonNull
    static String scoreText(@NonNull Context context, @Nullable X11GpuProfileStore.Score score) {
        if (score == null) return context.getString(R.string.settings_x11_gpu_not_measured);
        switch (score.outcome) {
            case OK: return context.getString(R.string.settings_x11_gpu_score, score.score);
            case TIMED_OUT: return context.getString(R.string.settings_x11_gpu_outcome_timed_out);
            case NOT_ACCELERATED: return context.getString(R.string.settings_x11_gpu_outcome_not_accelerated);
            case SKIPPED: return context.getString(R.string.settings_x11_gpu_outcome_skipped);
            case UNAVAILABLE: return context.getString(R.string.settings_x11_gpu_outcome_unavailable);
            default: return context.getString(R.string.settings_x11_gpu_outcome_crashed);
        }
    }

    /**
     * The entry row's summary on Set up Linux apps: what is picked (Automatic names what it
     * resolves to) and that profile's last result.
     */
    @NonNull
    static String rowSummary(@NonNull Context context, @NonNull X11GpuProbe.Result gpu, @NonNull String pick,
                             @Nullable X11GpuProfileStore.Results results) {
        X11LinuxAppRunner.GpuChoice choice = X11LinuxAppRunner.installedEnv(gpu, pick, false);
        X11GpuProbe.Profile used = choice.profile != null ? choice.profile : X11GpuProbe.Profile.SOFTWARE;
        X11GpuProbe.Profile picked = X11GpuProbe.Profile.of(pick);
        String label = picked == null
            ? context.getString(R.string.settings_x11_gpu_auto_named, profileName(context, used))
            : profileName(context, picked);
        X11GpuProbe.Profile measured = picked != null ? picked : used;
        return context.getString(R.string.settings_x11_gpu_row_summary, label,
            scoreText(context, results == null ? null : results.get(measured.id)));
    }

    // ---- tests ----

    /** The view types on show, in order, for tests. */
    @NonNull
    List<Integer> itemTypesForTest() {
        List<Integer> types = new ArrayList<>();
        for (int i = 0; i < adapter.getItemCount(); i++) types.add(adapter.getItemViewType(i));
        return types;
    }

    /** Whether the device has been read once, for tests. */
    boolean loadedForTest() {
        return facts != null;
    }
}
