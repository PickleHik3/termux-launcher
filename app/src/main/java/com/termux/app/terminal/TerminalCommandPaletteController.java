package com.termux.app.terminal;

import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import java.util.HashMap;
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
    /**
     * Floor for the anchor clamp only — enough room for a palette that has grown a few rows. The
     * height spring's own floor is {@link CommandPaletteView#chromeHeight()}, since the palette
     * opens as a bare search box.
     */
    private static final float MIN_HEIGHT = 120f;
    private static final float MAX_HEIGHT = 296f;
    private static final float RADIUS_SEED = 6f;
    private static final float SEED_WIDTH = 161f;
    private static final float SEED_HEIGHT = 52f;
    private static final float STRIP_RESERVE = 46f;
    private static final float STRIP_RISE = 8f;
    /** Lift of the glass pane at full sprout; the platform casts the shadow from its outline. */
    private static final float SHADOW_ELEVATION = 10f;

    /** Bottom edge sits this far down the terminal area, then clamps clear of the strip. */
    private static final float ANCHOR_FRACTION = 0.71f;

    private static final long CONFIRMATION_MS = 2600L;

    // Content reads well before the sprout settles: the rectangle is recognisable from about a
    // fifth of the way in, and an end of exactly 1 would leave the strip waiting on the spring's
    // last, invisible millimetres.
    private static final float BODY_FADE_START = 0.22f;
    private static final float BODY_FADE_END = 0.72f;
    private static final float STRIP_FADE_START = 0.50f;
    private static final float STRIP_FADE_END = 0.92f;

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

    /**
     * The dock plank's 170/17 pair is tuned for a plank that tilts continuously under a finger; a
     * surface that appears on demand wants to be there in about a fifth of a second, so both
     * channels run much stiffer. This stiffness is only safe because {@link Spring} substeps —
     * integrated in one jump it diverges on any dropped frame. The sprout keeps a trace of
     * overshoot, the height channel none, since a bouncing bottom edge would read as the list
     * resizing twice.
     */
    private final Spring mProgress = new Spring(0f, 900f, 50f);
    private final Spring mHeight = new Spring(0f, 820f, 55f);

    private final Rect mScratchRect = new Rect();
    private final int[] mLocation = new int[2];
    private final RectF mSeed = new RectF();
    private final RectF mFrame = new RectF();
    /** Terminal area, in host coordinates: the only region the overlay swallows taps over. */
    private final RectF mModalBounds = new RectF();

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

    /**
     * Row artwork for the current row list, keyed by {@link CommandPaletteFilter.Entry#iconKey}.
     * Rebuilt with the rows so a stale icon can never outlive the app row it belonged to.
     */
    private final Map<String, Drawable> mRowIcons = new HashMap<>();

    private Mode mMode = Mode.LIST;
    private String mQuery = "";
    /** Caret position inside {@link #mQuery}, clamped to [0, length]. */
    private int mQueryCursor;
    private int mFocus = 0;
    /**
     * The palette opens as a search box: no rows until there is a query. ↓ opts into the full
     * catalogue for browsing, which is what this remembers.
     */
    private boolean mListRevealed;
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
        mQueryCursor = 0;
        mCrumb = "";
        mPendingEntry = null;
        mFocus = 0;
        mListRevealed = false;
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
        mView.setModalBounds(mModalBounds);
        mView.resetScroll();
        mProgress.reset(0f);
        mHeight.reset(targetHeight());
        mProgress.target = 1f;
        applyBackdropMaterial();
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
        mQueryCursor = 0;
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
                //
                // Round INWARD, never with Math.round: the frame is fractional, and a clip half a
                // pixel outside the painted surface let the bright frosted wallpaper leak past the
                // paint as a hairline. It showed up as a light seam along the bottom edge — the
                // edge where the glass interior is darkest and the leak most visible.
                outline.setRoundRect((int) Math.ceil(mFrame.left), (int) Math.ceil(mFrame.top),
                    (int) Math.floor(mFrame.right), (int) Math.floor(mFrame.bottom),
                    mCurrentRadius);
            }
        });
        mView = new CommandPaletteView(mActivity);
        mView.setCallbacks(this);
        mHost.addView(mView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // Stay above the backdrop pane in z. Without this the whole ledger — surface tint and
        // text — renders UNDER the translucent pane and reads muted and fuzzy.
        //
        // The null outline provider is what keeps that elevation from casting a shadow. Today it
        // would be empty by accident — the default BACKGROUND provider over a view with no
        // background — so say it out loud: a future setBackground(...) would silently start casting
        // the square band applyFrame() already documents fighting.
        mView.setOutlineProvider(null);
        mView.setElevation(dp(1f));
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
        // Faded out is enough to tear down — deliberately not "and both channels have settled".
        // The height channel tracks a result count that can keep nudging it, and anything that
        // leaves it in motion would otherwise pin the blur pane open over the space bar, since
        // the pane is clipped to the collapsed frame and the seed frame is the space bar.
        if (!mOpen && mProgress.value < 0.002f) {
            mProgress.reset(0f);
            mHeight.reset(mHeight.target);
            applyFrame();
            onCollapsed();
            return;
        }
        if (moving) kick();
    }

    /**
     * Settled shut. The host stays INVISIBLE rather than GONE so it keeps being measured — the
     * next sprout needs its width immediately — while {@code RealtimeBlurView} does no work,
     * since it gates its own blur pass on {@code isShown()}.
     */
    private void onCollapsed() {
        mGlass.setVisibility(View.INVISIBLE);
        // Drop the full-screen frost bitmap while shut; the next show() rebuilds it.
        ImageView frost = mActivity.findViewById(R.id.command_palette_wallpaper_backdrop);
        if (frost != null) {
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
        }
        if (mView.hasConfirmation()) return;
        mHost.setVisibility(View.INVISIBLE);
    }

    /**
     * Picks the glass backdrop for this open: over the system wallpaper a pre-blurred wallpaper
     * frost (the live blur cannot see through the window there and renders grey mud), otherwise
     * the RealtimeBlurView blurring real window content.
     */
    private void applyBackdropMaterial() {
        ImageView frost = mActivity.findViewById(R.id.command_palette_wallpaper_backdrop);
        View blur = mActivity.findViewById(R.id.command_palette_blur);
        boolean frosted = frost != null && mActivity.applyCommandPaletteWallpaperFrost(frost);
        if (blur != null) blur.setVisibility(frosted ? View.GONE : View.VISIBLE);
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
        // Open radius matches the dock capsule, so the palette reads as the same glass kit.
        float openRadius = mActivity.resolveDockCapsuleCornerRadiusPx(
            Math.max(1, Math.round(mHeight.value)));
        mCurrentRadius = lerp(dp(RADIUS_SEED), openRadius, p);
        // No platform elevation shadow. The caster would be this full-screen glass pane, and the
        // shadow it produced was a flat 8dp band under the bottom edge with square ends that
        // ignored the rounded corners and cut off hard instead of falling off. CommandPaletteView
        // draws the shadow itself, outside the frame, following the same animated rounded rect.
        mGlass.setElevation(0f);
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
        // The overlay view fills the activity, but it may only claim touches over the terminal.
        // Below that sits the in-app keyboard, whose keys are how the palette is typed into: if
        // the overlay ate those taps, every keystroke would register as a dismissing outside tap.
        mModalBounds.set(0f, top, mHost.getWidth(), bottom);
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
        return Math.max(mView.chromeHeight(), Math.min(dp(MAX_HEIGHT), content));
    }

    private boolean isReducedMotion() {
        return Settings.Global.getFloat(mActivity.getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    // ------------------------------------------------------------------ content

    private void rebuildRows() {
        List<CommandPaletteView.Row> rows = new ArrayList<>();
        mRowEntries.clear();
        mRowIcons.clear();
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
        mView.setQueryCursor(clampedQueryCursor());
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
        // Search-first: with nothing typed the palette is only its search box and the keycap
        // strip, and it grows the ledger once there is something to narrow — or once ↓ asks for
        // the whole catalogue.
        if (!mListRevealed && mQuery.trim().isEmpty()) return;
        List<CommandPaletteFilter.Entry> ranked =
            new ArrayList<>(CommandPaletteFilter.filterAndRank(mEntries, mQuery));
        ranked.addAll(TerminalCommandPalette.buildAppEntries(mAppProvider, mAppUsageStats, mQuery,
            mRowIcons));
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
            entry.shortcutLabel(), entry.enabled,
            entry.iconKey == null ? null : mRowIcons.get(entry.iconKey)));
        mRowEntries.add(entry);
    }

    @NonNull
    private String headerMeta() {
        if (mMode == Mode.ARGUMENT)
            return mActivity.getString(R.string.palette_meta_awaiting_value);
        // Nothing typed and nothing revealed: a "0 results" count would read as a failed search
        // rather than as a search box waiting for one.
        if (mMode == Mode.LIST && !mListRevealed && mQuery.trim().isEmpty())
            return mActivity.getString(R.string.palette_meta_idle);
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
        // An arrow on the bare search box is a request to browse, not to move a focus there is
        // nothing to move.
        if (mMode == Mode.LIST && !mListRevealed && mQuery.trim().isEmpty()) {
            mListRevealed = true;
            mFocus = 0;
            rebuildRows();
            playTick();
            return;
        }
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
                    // Space-bar slider swipes walk the caret through the query.
                    case Cursor_left: moveQueryCursor(-Math.max(1, value.getSliderRepeat())); break;
                    case Cursor_right: moveQueryCursor(Math.max(1, value.getSliderRepeat())); break;
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
            case KeyEvent.KEYCODE_DPAD_LEFT: moveQueryCursor(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: moveQueryCursor(1); return true;
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
        // The space bar is also the palette's own gesture seat, so a trailing space from the
        // opening swipe must not become a filter that matches nothing.
        if (mQuery.isEmpty() && mMode != Mode.ARGUMENT && text.trim().isEmpty()) return;
        int cursor = clampedQueryCursor();
        mQuery = mQuery.substring(0, cursor) + text + mQuery.substring(cursor);
        mQueryCursor = cursor + text.length();
        onQueryChanged();
    }

    private void backspace() {
        if (mQuery.isEmpty()) {
            if (mMode != Mode.LIST) popMode();
            return;
        }
        int cursor = clampedQueryCursor();
        if (cursor <= 0) return;
        mQuery = mQuery.substring(0, cursor - 1) + mQuery.substring(cursor);
        mQueryCursor = cursor - 1;
        onQueryChanged();
    }

    private int clampedQueryCursor() {
        return Math.max(0, Math.min(mQueryCursor, mQuery.length()));
    }

    private void moveQueryCursor(int delta) {
        if (mQuery.isEmpty()) return;
        int moved = Math.max(0, Math.min(clampedQueryCursor() + delta, mQuery.length()));
        if (moved == mQueryCursor) return;
        mQueryCursor = moved;
        if (mView != null) mView.setQueryCursor(mQueryCursor);
    }

    private void onQueryChanged() {
        if (mMode == Mode.ARGUMENT) {
            // Nothing is focusable in argument mode; the buffer itself is the target.
            mFocus = -1;
            mView.setQuery("", "");
            mView.setArgumentMode(true, argumentPlaceholder(), mQuery);
            mView.setQueryCursor(clampedQueryCursor());
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
        mQueryCursor = 0;
        mFocus = 0;
        // Backing out of a submenu lands on the list that was there, not on a bare box.
        mListRevealed = true;
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
            mQueryCursor = 0;
            mFocus = 0;
            rebuildRows();
            return;
        }
        if (entry.isArgumentPrompt()) {
            mMode = Mode.ARGUMENT;
            mPendingEntry = entry;
            mCrumb = entry.title;
            mQuery = "";
            mQueryCursor = 0;
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
            entry.requiresConfirmation, entry.risk, arguments, null, null, entry.iconKey);
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
