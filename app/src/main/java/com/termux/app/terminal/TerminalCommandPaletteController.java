package com.termux.app.terminal;

import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.Spring;
import com.termux.app.TermuxActivity;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherUsageStatsStore;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import juloo.keyboard2.KeyValue;

/**
 * The command palette: an in-activity glass overlay that sprouts from the in-app keyboard's
 * space bar, lands mid-terminal, and runs registry actions through
 * {@link TerminalActionDispatcher}.
 *
 * <p>It is deliberately not a {@code Dialog}. A dialog window takes focus, which summons the
 * system IME — the very thing the fork suppresses while the in-app keyboard is up. Instead the
 * palette is a view inside the activity and never asks for input focus: it reads typing by
 * claiming resolved key values in the keyboard's own pipeline
 * ({@link TerminalKeyEventHandler.KeyValueInterceptor}) before they reach the terminal, and
 * hardware strokes through {@link #handleHardwareKey}. No IME is ever involved.
 *
 * <p>Motion reuses {@link Spring} — the dock plank's integrator, constants and reduce-motion
 * rule — on two channels: one for the space bar → mid-terminal sprout, one for the
 * bottom-anchored height so result-count changes glide without the bottom edge drifting.
 */
public final class TerminalCommandPaletteController
    implements Choreographer.FrameCallback, CommandPaletteView.Callbacks,
    TerminalKeyEventHandler.KeyValueInterceptor {

    private static final String LOG_TAG = "TerminalCommandPalette";

    // Geometry, in dp, from the handoff's reference frame.
    private static final float SIDE_INSET = 14f;
    private static final float MIN_HEIGHT = 120f;
    private static final float MAX_HEIGHT = 296f;
    private static final float RADIUS_SEED = 6f;
    private static final float RADIUS_OPEN = 10f;
    private static final float SEED_WIDTH = 161f;
    private static final float SEED_HEIGHT = 52f;
    private static final float STRIP_RESERVE = 46f;
    private static final float STRIP_RISE = 8f;

    /** Bottom edge sits this far down the terminal area, then clamps clear of the strip. */
    private static final float ANCHOR_FRACTION = 0.71f;

    private static final long CONFIRMATION_MS = 2600L;

    private static final float BODY_FADE_START = 0.45f;
    private static final float BODY_FADE_END = 0.95f;
    private static final float STRIP_FADE_START = 0.65f;
    private static final float STRIP_FADE_END = 1f;

    private enum Mode { LIST, ARGUMENT, CHOICES }

    /** Fallback strip when nothing has been run yet, per the handoff's default six. */
    private static final String[][] DEFAULT_KEYCAPS = {
        {LauncherToolRegistry.TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD, "⌨", "kbd"},
        {LauncherToolRegistry.TOOL_TERMINAL_HINTS, "✎", "hints"},
        {LauncherToolRegistry.TOOL_PANE_SPLIT_VERTICAL, "⧉", "split"},
        {LauncherToolRegistry.TOOL_TERMINAL_SEARCH_SCROLLBACK, "⌕", "find"},
        {LauncherToolRegistry.TOOL_TERMINAL_FONT_SIZE_INCREASE, "A", "font"},
        {LauncherToolRegistry.TOOL_SESSION_NEW, "＋", "new"},
    };

    private final TermuxActivity mActivity;
    private final CommandPaletteActionStats mStats;
    private final LauncherAppDataProvider mAppProvider;
    private final LauncherUsageStatsStore mAppUsageStats;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final float mDensity;

    private final Spring mProgress = new Spring(0f, 170f, 17f);
    private final Spring mHeight = new Spring(0f, 170f, 24f);

    private final Rect mScratchRect = new Rect();
    private final int[] mLocation = new int[2];
    private final RectF mSeed = new RectF();
    private final RectF mFrame = new RectF();

    private FrameLayout mHost;
    private FrameLayout mGlass;
    private CommandPaletteView mView;

    private boolean mOpen;
    private boolean mFrameScheduled;
    private long mLastFrameTimeNanos;
    private float mAnchorY;
    private float mCurrentRadius;

    private final List<CommandPaletteFilter.Entry> mEntries = new ArrayList<>();
    /** Parallel to {@link #mRows}: the entry a row runs, or null for a rule or notice. */
    private final List<CommandPaletteFilter.Entry> mRowEntries = new ArrayList<>();
    private final List<CommandPaletteFilter.Entry> mKeycapEntries = new ArrayList<>();
    private List<CommandPaletteView.Row> mRows = new ArrayList<>();

    private Mode mMode = Mode.LIST;
    private String mQuery = "";
    private int mFocus = 0;
    @Nullable private CommandPaletteFilter.Entry mPendingEntry;
    private String mCrumb = "";
    private final Runnable mClearConfirmation = this::clearConfirmation;

    public TerminalCommandPaletteController(@NonNull TermuxActivity activity) {
        mActivity = activity;
        mStats = new CommandPaletteActionStats(activity);
        mAppProvider = LauncherAppDataProvider.getInstance(activity);
        mAppUsageStats = new LauncherUsageStatsStore(activity);
        mDensity = activity.getResources().getDisplayMetrics().density;
    }

    public boolean isOpen() {
        return mOpen;
    }

    /** Opens the palette, or collapses it when the same invocation arrives while it is up. */
    public void toggle() {
        if (mOpen) {
            collapse();
            return;
        }
        show();
    }

    public void show() {
        if (!bindViews()) return;
        mEntries.clear();
        mEntries.addAll(TerminalCommandPalette.buildEntries(mActivity));
        mEntries.addAll(TerminalCommandPalette.buildSessionEntries(mActivity));
        if (mEntries.isEmpty()) {
            Toast.makeText(mActivity, R.string.palette_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        mHandler.removeCallbacks(mClearConfirmation);
        mMode = Mode.LIST;
        mQuery = "";
        mCrumb = "";
        mPendingEntry = null;
        mFocus = 0;
        mOpen = true;

        mView.refreshPalette();
        mView.setConfirmation(null, 0f, 0f);
        mView.setArgumentMode(false, "", "");
        mView.setQuery("", mActivity.getString(R.string.palette_search_hint));
        rebuildKeycaps();
        rebuildRows();
        // A cold app list arrives later; the palette opens now and grows its Apps section when
        // the load lands.
        if (!mAppProvider.hasLoadedApps())
            mAppProvider.warmAsync(() -> {
                if (mOpen && mMode == Mode.LIST) rebuildRows();
            });

        resolveAnchor();
        resolveSeed();
        mProgress.reset(0f);
        mHeight.reset(targetHeight());
        mProgress.target = 1f;
        mGlass.setVisibility(View.VISIBLE);
        mHost.setVisibility(View.VISIBLE);
        applyFrame();
        mActivity.setCommandPaletteInterceptorActive(true);
        kick();
    }

    /** esc, an outside tap, or a second invocation: reverse the sprout and clear the query. */
    public void collapse() {
        if (!mOpen) return;
        mOpen = false;
        mMode = Mode.LIST;
        mQuery = "";
        mPendingEntry = null;
        mCrumb = "";
        mActivity.setCommandPaletteInterceptorActive(false);
        mProgress.target = 0f;
        kick();
    }

    /** Drops the overlay without animating, for pause, configuration change and destroy. */
    public void dismissImmediately() {
        mOpen = false;
        mHandler.removeCallbacks(mClearConfirmation);
        mActivity.setCommandPaletteInterceptorActive(false);
        mProgress.reset(0f);
        if (mView != null) mView.setConfirmation(null, 0f, 0f);
        if (mGlass != null) mGlass.setVisibility(View.INVISIBLE);
        if (mHost != null) mHost.setVisibility(View.INVISIBLE);
    }

    /** Re-resolves roles after a theme, wallpaper-color or configuration change. */
    public void refreshAppearance() {
        if (mView != null) mView.refreshPalette();
    }

    private boolean bindViews() {
        if (mView != null) return true;
        mHost = mActivity.findViewById(R.id.command_palette_host);
        mGlass = mActivity.findViewById(R.id.command_palette_glass);
        View blur = mActivity.findViewById(R.id.command_palette_blur);
        if (mHost == null || mGlass == null || blur == null) return false;
        mGlass.setClipToOutline(true);
        mGlass.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // Clipping the blur to the animated rounded rect is what keeps the backdrop
                // frost inside the palette without re-measuring a view per frame.
                outline.setRoundRect(Math.round(mFrame.left), Math.round(mFrame.top),
                    Math.round(mFrame.right), Math.round(mFrame.bottom), mCurrentRadius);
            }
        });
        mView = new CommandPaletteView(mActivity);
        mView.setCallbacks(this);
        mHost.addView(mView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return true;
    }

    // ------------------------------------------------------------------ motion

    private void kick() {
        if (mFrameScheduled) return;
        mFrameScheduled = true;
        mLastFrameTimeNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        mFrameScheduled = false;
        float dt = mLastFrameTimeNanos == 0L
            ? Spring.MIN_DT
            : (frameTimeNanos - mLastFrameTimeNanos) / 1_000_000_000f;
        mLastFrameTimeNanos = frameTimeNanos;
        dt = Spring.clampDelta(dt);
        boolean reduced = isReducedMotion();
        boolean moving = mProgress.tick(reduced, dt);
        moving |= mHeight.tick(reduced, dt);
        applyFrame();
        if (moving) {
            kick();
            return;
        }
        if (!mOpen && mProgress.value < 0.002f) onCollapsed();
    }

    /**
     * Settled shut. The host stays INVISIBLE rather than GONE so it keeps being measured — the
     * next sprout needs its width immediately — while {@code RealtimeBlurView} does no work,
     * since it gates its own blur pass on {@code isShown()}.
     */
    private void onCollapsed() {
        mGlass.setVisibility(View.INVISIBLE);
        if (mView.hasConfirmation()) return;
        mHost.setVisibility(View.INVISIBLE);
    }

    private void applyFrame() {
        if (mView == null) return;
        float p = clamp01(mProgress.value);
        float hostWidth = mHost.getWidth();
        float openLeft = dp(SIDE_INSET);
        float openRight = hostWidth - dp(SIDE_INSET);
        float openTop = mAnchorY - mHeight.value;
        mFrame.set(
            lerp(mSeed.left, openLeft, p),
            lerp(mSeed.top, openTop, p),
            lerp(mSeed.right, openRight, p),
            lerp(mSeed.bottom, mAnchorY, p));
        mCurrentRadius = dp(lerp(RADIUS_SEED, RADIUS_OPEN, p));
        float bodyAlpha = ramp(p, BODY_FADE_START, BODY_FADE_END);
        float stripAlpha = ramp(p, STRIP_FADE_START, STRIP_FADE_END);
        mView.setFrame(mFrame, mCurrentRadius, bodyAlpha, stripAlpha,
            (1f - stripAlpha) * dp(STRIP_RISE), p);
        mGlass.invalidateOutline();
    }

    /**
     * Seed rect: the rendered space bar when the in-app keyboard is up, otherwise a space
     * bar-sized rect at the bottom centre of the terminal area.
     */
    private void resolveSeed() {
        mHost.getLocationOnScreen(mLocation);
        if (mActivity.getInAppKeyboardSpaceBarRect(mScratchRect)) {
            mSeed.set(mScratchRect.left - mLocation[0], mScratchRect.top - mLocation[1],
                mScratchRect.right - mLocation[0], mScratchRect.bottom - mLocation[1]);
            return;
        }
        float width = dp(SEED_WIDTH);
        float height = dp(SEED_HEIGHT);
        float centerX = mHost.getWidth() / 2f;
        float bottom = terminalBottom() - dp(4f);
        mSeed.set(centerX - width / 2f, bottom - height, centerX + width / 2f, bottom);
    }

    /**
     * Bottom edge lands mid-terminal and is clamped so the keycap strip below it never covers
     * the last prompt rows.
     */
    private void resolveAnchor() {
        float top = terminalTop();
        float bottom = terminalBottom();
        float anchor = top + (bottom - top) * ANCHOR_FRACTION;
        float lowest = bottom - dp(STRIP_RESERVE);
        float highest = top + dp(MIN_HEIGHT);
        mAnchorY = Math.max(Math.min(anchor, lowest), Math.min(highest, lowest));
    }

    private float terminalTop() {
        View terminal = mActivity.findViewById(R.id.terminal_surface_host);
        if (terminal == null) return 0f;
        terminal.getLocationOnScreen(mLocation);
        int terminalTop = mLocation[1];
        mHost.getLocationOnScreen(mLocation);
        return terminalTop - mLocation[1];
    }

    private float terminalBottom() {
        View terminal = mActivity.findViewById(R.id.terminal_surface_host);
        if (terminal == null) return mHost.getHeight();
        return terminalTop() + terminal.getHeight();
    }

    private float targetHeight() {
        float content = mView.measuredContentHeight();
        return Math.max(dp(MIN_HEIGHT), Math.min(dp(MAX_HEIGHT), content));
    }

    private boolean isReducedMotion() {
        return Settings.Global.getFloat(mActivity.getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    // ------------------------------------------------------------------ content

    private void rebuildRows() {
        List<CommandPaletteView.Row> rows = new ArrayList<>();
        mRowEntries.clear();
        switch (mMode) {
            case ARGUMENT:
                rows.add(CommandPaletteView.Row.notice(mActivity
                    .getString(R.string.palette_argument_notice, upper(mCrumb))));
                mRowEntries.add(null);
                break;
            case CHOICES:
                buildChoiceRows(rows);
                break;
            case LIST:
            default:
                buildListRows(rows);
                break;
        }
        mRows = rows;
        clampFocus();
        mView.setRows(rows, mFocus);
        mView.setHeader(headerMeta(), mCrumb);
        mView.setQuery(mQuery, mActivity.getString(R.string.palette_search_hint));
        mHeight.target = targetHeight();
        kick();
    }

    private void buildChoiceRows(@NonNull List<CommandPaletteView.Row> rows) {
        CommandPaletteFilter.Entry pending = mPendingEntry;
        if (pending == null || pending.argumentChoices == null) return;
        rows.add(CommandPaletteView.Row.category(upper(mCrumb)));
        mRowEntries.add(null);
        String needle = mQuery.trim().toLowerCase(Locale.US);
        for (String choice : pending.argumentChoices) {
            if (!needle.isEmpty() && !choice.toLowerCase(Locale.US).contains(needle)) continue;
            rows.add(CommandPaletteView.Row.entry(choice, null, "⏎", true));
            mRowEntries.add(withArgument(pending, choice));
        }
    }

    private void buildListRows(@NonNull List<CommandPaletteView.Row> rows) {
        List<CommandPaletteFilter.Entry> ranked =
            new ArrayList<>(CommandPaletteFilter.filterAndRank(mEntries, mQuery));
        ranked.addAll(TerminalCommandPalette.buildAppEntries(mAppProvider, mAppUsageStats, mQuery));
        if (ranked.isEmpty()) {
            rows.add(CommandPaletteView.Row.notice(
                mActivity.getString(R.string.palette_no_match, upper(mQuery))));
            mRowEntries.add(null);
            return;
        }
        // A ranked result is one flat list: grouping it would fight the ranking. Only the
        // unfiltered view carries the category rules.
        if (!mQuery.trim().isEmpty()) {
            for (CommandPaletteFilter.Entry entry : ranked) addEntryRow(rows, entry);
            return;
        }
        Map<String, List<CommandPaletteFilter.Entry>> byCategory = new LinkedHashMap<>();
        for (CommandPaletteFilter.Entry entry : ranked) {
            List<CommandPaletteFilter.Entry> group = byCategory.get(entry.category);
            if (group == null) {
                group = new ArrayList<>();
                byCategory.put(entry.category, group);
            }
            group.add(entry);
        }
        for (Map.Entry<String, List<CommandPaletteFilter.Entry>> group : byCategory.entrySet()) {
            rows.add(CommandPaletteView.Row.category(
                upper(TerminalCommandPalette.categoryLabel(mActivity, group.getKey()))));
            mRowEntries.add(null);
            for (CommandPaletteFilter.Entry entry : group.getValue()) addEntryRow(rows, entry);
        }
    }

    private void addEntryRow(@NonNull List<CommandPaletteView.Row> rows,
                             @NonNull CommandPaletteFilter.Entry entry) {
        rows.add(CommandPaletteView.Row.entry(entry.title,
            entry.enabled ? entry.subtitle : entry.disabledReason,
            entry.shortcutLabel(), entry.enabled));
        mRowEntries.add(entry);
    }

    @NonNull
    private String headerMeta() {
        if (mMode == Mode.ARGUMENT)
            return mActivity.getString(R.string.palette_meta_awaiting_value);
        int count = 0;
        for (CommandPaletteFilter.Entry entry : mRowEntries) if (entry != null) count++;
        return mActivity.getResources().getQuantityString(R.plurals.palette_meta_results,
            count, count);
    }

    private void clampFocus() {
        if (mRowEntries.isEmpty()) {
            mFocus = -1;
            return;
        }
        if (mFocus < 0 || mFocus >= mRowEntries.size() || mRowEntries.get(mFocus) == null)
            mFocus = firstSelectable();
    }

    private int firstSelectable() {
        for (int i = 0; i < mRowEntries.size(); i++) if (mRowEntries.get(i) != null) return i;
        return -1;
    }

    private void moveFocus(int delta) {
        if (mRowEntries.isEmpty()) return;
        int index = mFocus;
        for (int step = 0; step < mRowEntries.size(); step++) {
            index += delta;
            if (index < 0 || index >= mRowEntries.size()) return; // clamped, not wrapping
            if (mRowEntries.get(index) != null) {
                mFocus = index;
                mView.setRows(mRows, mFocus);
                // The focused row is the taller one, so moving focus re-targets the height.
                mHeight.target = targetHeight();
                playTick();
                kick();
                return;
            }
        }
    }

    private void rebuildKeycaps() {
        mKeycapEntries.clear();
        List<CommandPaletteView.Keycap> caps = new ArrayList<>();
        List<CommandPaletteFilter.Entry> ranked =
            mStats.rank(mEntries, CommandPaletteView.KEYCAP_COUNT);
        for (CommandPaletteFilter.Entry entry : ranked) {
            mKeycapEntries.add(entry);
            caps.add(new CommandPaletteView.Keycap(glyphFor(entry.toolName),
                shortLabel(entry.toolName)));
        }
        for (String[] fallback : DEFAULT_KEYCAPS) {
            if (mKeycapEntries.size() >= CommandPaletteView.KEYCAP_COUNT) break;
            CommandPaletteFilter.Entry entry = findEntry(fallback[0]);
            if (entry == null || containsTool(mKeycapEntries, fallback[0])) continue;
            mKeycapEntries.add(entry);
            caps.add(new CommandPaletteView.Keycap(fallback[1], fallback[2]));
        }
        mView.setKeycaps(caps);
    }

    @NonNull
    private static String glyphFor(@NonNull String toolName) {
        for (String[] fallback : DEFAULT_KEYCAPS)
            if (fallback[0].equals(toolName)) return fallback[1];
        return "▸";
    }

    /** Short cap label: the tool's own verb, e.g. {@code pane.split_vertical} → {@code split}. */
    @NonNull
    private static String shortLabel(@NonNull String toolName) {
        int dot = toolName.indexOf('.');
        String tail = dot < 0 ? toolName : toolName.substring(dot + 1);
        int underscore = tail.indexOf('_');
        return underscore < 0 ? tail : tail.substring(0, underscore);
    }

    private static boolean containsTool(@NonNull List<CommandPaletteFilter.Entry> entries,
                                        @NonNull String toolName) {
        for (CommandPaletteFilter.Entry entry : entries)
            if (entry.toolName.equals(toolName)) return true;
        return false;
    }

    @Nullable
    private CommandPaletteFilter.Entry findEntry(@NonNull String toolName) {
        for (CommandPaletteFilter.Entry entry : mEntries)
            if (entry.toolName.equals(toolName) && entry.enabled) return entry;
        return null;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean interceptKeyValue(@NonNull KeyValue value, boolean ctrl, boolean alt,
                                     boolean shift) {
        if (!mOpen) return false;
        switch (value.getKind()) {
            case Char:
                if (!ctrl && !alt) appendText(String.valueOf(value.getChar()));
                return true;
            case String:
                if (!ctrl && !alt) appendText(value.getString());
                return true;
            case Editing:
                switch (value.getEditing()) {
                    case SPACE_BAR: appendText(" "); break;
                    case BACKSPACE: backspace(); break;
                    default: break;
                }
                return true;
            case Keyevent:
                handleKeyCode(value.getKeyevent());
                return true;
            case Event:
                if (value.getEvent() == KeyValue.Event.ACTION) commit();
                return true;
            case Slider:
                switch (value.getSlider()) {
                    case Cursor_up: moveFocus(-1); break;
                    case Cursor_down: moveFocus(1); break;
                    default: break;
                }
                return true;
            default:
                // Everything else is swallowed: while the palette owns the keyboard nothing
                // may leak into the shell.
                return true;
        }
    }

    /** Hardware and external-keyboard strokes, claimed before the terminal writes them. */
    public boolean handleHardwareKey(int keyCode, @NonNull KeyEvent event) {
        if (!mOpen) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (handleKeyCode(keyCode)) return true;
        if (event.isCtrlPressed() || event.isAltPressed()) return true;
        int unicode = event.getUnicodeChar();
        if (unicode >= ' ') appendText(String.valueOf((char) unicode));
        return true;
    }

    private boolean handleKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: moveFocus(-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: moveFocus(1); return true;
            case KeyEvent.KEYCODE_DEL: backspace(); return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: commit(); return true;
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BACK: collapse(); return true;
            default: return false;
        }
    }

    private void appendText(@NonNull String text) {
        if (text.isEmpty()) return;
        mQuery = mQuery + text;
        onQueryChanged();
    }

    private void backspace() {
        if (mQuery.isEmpty()) {
            if (mMode != Mode.LIST) popMode();
            return;
        }
        mQuery = mQuery.substring(0, mQuery.length() - 1);
        onQueryChanged();
    }

    private void onQueryChanged() {
        if (mMode == Mode.ARGUMENT) {
            // Nothing is focusable in argument mode; the buffer itself is the target.
            mFocus = -1;
            mView.setQuery("", "");
            mView.setArgumentMode(true, argumentPlaceholder(), mQuery);
            mView.setHeader(headerMeta(), mCrumb);
            return;
        }
        mFocus = 0;
        rebuildRows();
    }

    private void popMode() {
        mMode = Mode.LIST;
        mCrumb = "";
        mPendingEntry = null;
        mQuery = "";
        mFocus = 0;
        mView.setArgumentMode(false, "", "");
        rebuildRows();
    }

    private void commit() {
        if (mMode == Mode.ARGUMENT) {
            CommandPaletteFilter.Entry pending = mPendingEntry;
            if (pending != null) runEntry(withArgument(pending, mQuery));
            return;
        }
        if (mFocus < 0 || mFocus >= mRowEntries.size()) {
            runQueryInShell();
            return;
        }
        CommandPaletteFilter.Entry entry = mRowEntries.get(mFocus);
        if (entry == null) {
            runQueryInShell();
            return;
        }
        activate(entry);
    }

    private void activate(@NonNull CommandPaletteFilter.Entry entry) {
        if (!entry.enabled) {
            Toast.makeText(mActivity, entry.disabledReason != null
                ? entry.disabledReason : mActivity.getString(R.string.palette_empty),
                Toast.LENGTH_SHORT).show();
            return;
        }
        if (entry.isSubmenu()) {
            mMode = Mode.CHOICES;
            mPendingEntry = entry;
            mCrumb = entry.title;
            mQuery = "";
            mFocus = 0;
            rebuildRows();
            return;
        }
        if (entry.isArgumentPrompt()) {
            mMode = Mode.ARGUMENT;
            mPendingEntry = entry;
            mCrumb = entry.title;
            mQuery = "";
            mFocus = -1;
            mView.setArgumentMode(true, argumentPlaceholder(), "");
            rebuildRows();
            return;
        }
        runEntry(entry);
    }

    private void runEntry(@NonNull CommandPaletteFilter.Entry entry) {
        if (entry.isDestructive()) {
            confirmThenRun(entry);
            return;
        }
        run(entry);
    }

    private void confirmThenRun(@NonNull CommandPaletteFilter.Entry entry) {
        collapse();
        new MaterialAlertDialogBuilder(mActivity)
            .setTitle(mActivity.getString(R.string.palette_confirm_title, entry.title))
            .setMessage(entry.subtitle)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.palette_confirm_run, (dialog, which) -> run(entry))
            .show();
    }

    private void run(@NonNull CommandPaletteFilter.Entry entry) {
        mStats.recordRun(CommandPaletteActionStats.keyFor(entry));
        playTick();
        collapse();
        JSONObject result = TerminalActionDispatcher.getInstance().execute(entry.toolName,
            entry.arguments == null ? new JSONObject() : entry.arguments);
        if (!result.optBoolean("ok", false)) {
            String message = result.optString("message",
                mActivity.getString(R.string.palette_action_failed, entry.title));
            Logger.logWarn(LOG_TAG, "Palette action " + entry.toolName + " failed: " + message);
            Toast.makeText(mActivity, message, Toast.LENGTH_SHORT).show();
            return;
        }
        showConfirmation(entry.title);
    }

    /** No match: ⏎ hands the query to the shell verbatim. */
    private void runQueryInShell() {
        String command = mQuery.trim();
        collapse();
        if (command.isEmpty()) return;
        com.termux.terminal.TerminalSession session = mActivity.getCurrentSession();
        if (session == null) {
            Toast.makeText(mActivity, R.string.palette_unavailable_no_session,
                Toast.LENGTH_SHORT).show();
            return;
        }
        session.write(command + "\r");
    }

    private void showConfirmation(@NonNull String text) {
        if (mView == null) return;
        mHost.setVisibility(View.VISIBLE);
        mView.setConfirmation("✓ " + text, dp(SIDE_INSET) + dp(2f), mAnchorY);
        mHandler.removeCallbacks(mClearConfirmation);
        mHandler.postDelayed(mClearConfirmation, CONFIRMATION_MS);
    }

    private void clearConfirmation() {
        if (mView == null) return;
        mView.setConfirmation(null, 0f, 0f);
        if (!mOpen && mHost != null) mHost.setVisibility(View.INVISIBLE);
    }

    @NonNull
    private String argumentPlaceholder() {
        CommandPaletteFilter.Entry pending = mPendingEntry;
        if (pending == null || pending.argumentName == null) return "";
        return pending.argumentName;
    }

    @NonNull
    private static CommandPaletteFilter.Entry withArgument(
        @NonNull CommandPaletteFilter.Entry entry, @NonNull String value) {
        JSONObject arguments = new JSONObject();
        try {
            if (entry.arguments != null) {
                java.util.Iterator<String> keys = entry.arguments.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    arguments.put(key, entry.arguments.get(key));
                }
            }
            if (entry.argumentName != null) arguments.put(entry.argumentName, value);
        } catch (JSONException ignored) {
        }
        return new CommandPaletteFilter.Entry(entry.toolName, entry.title, entry.subtitle,
            entry.category, entry.bindings, entry.enabled, entry.disabledReason,
            entry.requiresConfirmation, entry.risk, arguments);
    }

    // ------------------------------------------------------------------ taps

    @Override
    public void onRowTapped(int index) {
        if (index < 0 || index >= mRowEntries.size()) return;
        CommandPaletteFilter.Entry entry = mRowEntries.get(index);
        if (entry == null) return;
        mFocus = index;
        activate(entry);
    }

    @Override
    public void onKeycapTapped(int index) {
        if (index < 0 || index >= mKeycapEntries.size()) return;
        runEntry(mKeycapEntries.get(index));
    }

    @Override
    public void onOutsideTapped() {
        collapse();
    }

    /** Light tick on focus change and on run, under the same preference as the dock's rows. */
    private void playTick() {
        if (mView == null || !mActivity.isRowHapticsEnabled()) return;
        mView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    @NonNull
    private static String upper(@NonNull String text) {
        return text.toUpperCase(Locale.getDefault());
    }

    private float dp(float value) {
        return value * mDensity;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** Normalized 0..1 ramp of {@code value} across {@code [start, end]}. */
    private static float ramp(float value, float start, float end) {
        if (value <= start) return 0f;
        if (value >= end) return 1f;
        return (value - start) / (end - start);
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }
}
