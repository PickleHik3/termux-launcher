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
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.notice.AppNotice;
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

    private enum Mode { LIST, ARGUMENT, CHOICES, CAPTURE }

    /** Fallback strip when nothing has been run yet, per the handoff's default six. */
    private static final String[][] DEFAULT_KEYCAPS = {
        {LauncherToolRegistry.TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD, "⌨", "kbd"},
        {LauncherToolRegistry.TOOL_TERMINAL_HINTS, "✎", "pick"},
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
    /**
     * Stable id to the chord bound to that app. Built once per show() and again when a cold app
     * cache lands or a capture writes a new binding — never per keystroke, since resolving it walks
     * every binding in the config.
     */
    @NonNull private Map<String, String> mAppShortcuts = java.util.Collections.emptyMap();

    private Mode mMode = Mode.LIST;
    /**
     * The stroke captured so far in {@link Mode#CAPTURE}, or empty while waiting for a key.
     * Deliberately not mQuery: backspace clears it whole rather than a character at a time, and it
     * must never be filtered against anything.
     */
    @NonNull private String mCaptureStroke = "";
    /** Tool already holding the captured stroke, shown as a warning rather than a refusal. */
    @Nullable private String mCaptureConflict;
    /**
     * Set when a system IME committed text while capturing. Committed text carries no key code and
     * no modifier state, so there is nothing to capture from it; the notice row says so instead of
     * leaving the user pressing keys at a surface that never answers.
     */
    private boolean mCaptureNeedsKeyEvent;
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
        mAppUsageStats = LauncherUsageStatsStore.getInstance(activity);
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
        mActivity.closeFullStatusBarImmediate();
        // Two full-screen glass surfaces must never stack: the palette is transient and summonable
        // over anything, so the drawer is the one that yields. Immediate rather than animated —
        // a plane springing shut behind a palette sprouting open reads as a glitch, not a handoff.
        mActivity.getAppDrawerController().closeImmediate();
        // Same rule for the sheet plane, which owns the same interceptor slot: the palette is the
        // one surface summonable over anything, so the modal sheets are the ones that yield.
        mActivity.getTerminalSheetController().dismissAll();
        mEntries.clear();
        mEntries.addAll(TerminalCommandPalette.buildEntries(mActivity));
        mEntries.addAll(TerminalCommandPalette.buildSessionEntries(mActivity));
        mEntries.addAll(TerminalCommandPalette.buildKeyboardLayoutEntries(mActivity));
        // Bookmarks are a file the user edits outside the app, so every open re-reads it — a
        // stat when it has not moved, and a redraw only when it has.
        com.termux.app.launcher.web.LauncherBookmarksStore.getInstance(mActivity)
            .refreshAsync(this::onBookmarksLoaded);
        if (mEntries.isEmpty()) {
            AppNotice.show(mActivity, R.string.palette_empty, false);
            return;
        }
        mHandler.removeCallbacks(mClearConfirmation);
        mMode = Mode.LIST;
        mQuery = "";
        mQueryCursor = 0;
        mCrumb = "";
        mPendingEntry = null;
        mCaptureStroke = "";
        mCaptureConflict = null;
        mFocus = 0;
        mListRevealed = false;
        mOpen = true;
        mAppShortcuts = TerminalCommandPalette.buildAppShortcuts(mAppProvider);

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
                // The chords could not resolve against a cold cache, so rebuild the index too.
                mAppShortcuts = TerminalCommandPalette.buildAppShortcuts(mAppProvider);
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
        mCaptureStroke = "";
        mCaptureConflict = null;
        mPendingEntry = null;
        mCrumb = "";
        mActivity.setCommandPaletteInterceptorActive(false);
        mProgress.target = 0f;
        kick();
    }

    /** Drops the overlay without animating, for pause, configuration change and destroy. */
    public void dismissImmediately() {
        mOpen = false;
        mMode = Mode.LIST;
        mCaptureStroke = "";
        mCaptureConflict = null;
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
            case CAPTURE:
                rows.add(CommandPaletteView.Row.notice(captureNotice()));
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

    /** The capture row's single line of guidance: the dead end first, then a conflict warning. */
    @NonNull
    private String captureNotice() {
        if (mCaptureNeedsKeyEvent)
            return mActivity.getString(R.string.palette_capture_needs_key_event);
        return mCaptureConflict == null
            ? mActivity.getString(R.string.palette_capture_notice, mCrumb)
            : mActivity.getString(R.string.palette_capture_conflict, mCaptureConflict);
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

    /**
     * The Web section, shown only behind its prefix. It replaces the ledger rather than joining
     * it: once the query starts with {@code ?}, every row the user can mean is a web row, and
     * mixing app rows in would only push the address they typed further down.
     */
    private void buildWebRows(@NonNull List<CommandPaletteView.Row> rows,
                              @NonNull String webQuery) {
        rows.add(CommandPaletteView.Row.category(
            upper(TerminalCommandPalette.categoryLabel(mActivity,
                LauncherToolRegistry.CATEGORY_WEB))));
        mRowEntries.add(null);
        List<CommandPaletteFilter.Entry> entries =
            TerminalCommandPalette.buildWebEntries(mActivity, webQuery);
        if (entries.isEmpty()) {
            rows.add(CommandPaletteView.Row.notice(mActivity.getString(R.string.palette_web_hint)));
            mRowEntries.add(null);
            return;
        }
        for (CommandPaletteFilter.Entry entry : entries) addEntryRow(rows, entry);
    }

    private void buildListRows(@NonNull List<CommandPaletteView.Row> rows) {
        // Search-first: with nothing typed the palette is only its search box and the keycap
        // strip, and it grows the ledger once there is something to narrow — or once ↓ asks for
        // the whole catalogue.
        String webQuery = TerminalCommandPalette.webQueryFor(mQuery);
        if (webQuery != null) {
            buildWebRows(rows, webQuery);
            return;
        }
        if (!mListRevealed && mQuery.trim().isEmpty()) return;
        List<CommandPaletteFilter.Entry> ranked =
            new ArrayList<>(CommandPaletteFilter.filterAndRank(mEntries, mQuery));
        ranked.addAll(TerminalCommandPalette.buildAppEntries(mActivity, mAppProvider,
            mAppUsageStats, mQuery, mAppShortcuts, mRowIcons));
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
        if (mMode == Mode.CAPTURE)
            return mActivity.getString(R.string.palette_meta_awaiting_key);
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

    /** A bookmarks file that finished loading while the palette is open redraws it in place. */
    private void onBookmarksLoaded() {
        if (!mOpen || mView == null) return;
        if (TerminalCommandPalette.webQueryFor(mQuery) == null) return;
        rebuildRows();
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
        if (mMode == Mode.CAPTURE) return interceptCaptureKeyValue(value, ctrl, alt, shift);
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
        // Capture routes first, before handleKeyCode's Esc -> collapse(): in CAPTURE, Esc backs out
        // to the list rather than closing the palette, and routing first is also what guarantees
        // Esc, Enter and Backspace can never be captured as the bound key.
        if (mMode == Mode.CAPTURE) return handleCaptureKey(keyCode, event);
        // Ctrl/Alt+⏎ on the focused app row starts a capture; ⏎ alone stays "launch".
        if ((keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
            && (event.isCtrlPressed() || event.isAltPressed())) {
            beginCaptureFocused();
            return true;
        }
        if (handleKeyCode(keyCode)) return true;
        if (event.isCtrlPressed() || event.isAltPressed()) return true;
        int unicode = event.getUnicodeChar();
        if (unicode >= ' ') appendText(String.valueOf((char) unicode));
        return true;
    }

    /**
     * Text committed by a system IME, claimed before the terminal writes it. The twin of
     * {@link #handleHardwareKey}: a third-party keyboard sends the overlay no key events at all, so
     * without this the palette opens on a modifier chord and then ignores everything typed into it.
     *
     * <p>The decision itself is in {@link CommandPaletteSoftKeyDecision}; only routing is here.
     */
    public boolean handleSoftKeyboardCodePoint(int codePoint, boolean ctrlDown) {
        CommandPaletteSoftKeyDecision.Action action = CommandPaletteSoftKeyDecision.decide(
            mOpen, mMode == Mode.CAPTURE, codePoint, ctrlDown);
        switch (action) {
            case IGNORE: return false;
            case APPEND: appendText(new String(Character.toChars(codePoint))); return true;
            case COMMIT: commit(); return true;
            case BACKSPACE: backspace(); return true;
            case COLLAPSE: collapse(); return true;
            case SWALLOW:
            default:
                // Capture is the one mode where swallowing silently would read as a broken palette.
                if (mMode == Mode.CAPTURE) noteCaptureNeedsKeyEvent();
                return true;
        }
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
        mCaptureStroke = "";
        mCaptureConflict = null;
        mFocus = 0;
        // Backing out of a submenu lands on the list that was there, not on a bare box.
        mListRevealed = true;
        mView.setArgumentMode(false, "", "");
        rebuildRows();
    }

    // ------------------------------------------------------------------ key capture

    /** Head of the argument row while capturing, in place of the default "arg ❯". */
    private static final String CAPTURE_PROMPT = "key ❯";

    /**
     * Starts capturing a key for an app row. Guarded on the Apps category: nothing else has a
     * per-row argument the binding file can address, and binding a tool row would duplicate what
     * the config file already does better.
     */
    private void beginCapture(@NonNull CommandPaletteFilter.Entry entry) {
        if (!LauncherToolRegistry.CATEGORY_APPS.equals(entry.category)
            || entry.arguments == null) return;
        mMode = Mode.CAPTURE;
        mPendingEntry = entry;
        mCrumb = entry.title;
        mQuery = "";
        mQueryCursor = 0;
        mCaptureStroke = "";
        mCaptureConflict = null;
        mCaptureNeedsKeyEvent = false;
        mFocus = -1;
        applyCaptureRow();
        rebuildRows();
        playTick();
    }

    /** Capture the focused row, if it is one that can be bound. */
    private void beginCaptureFocused() {
        if (mMode != Mode.LIST || mFocus < 0 || mFocus >= mRowEntries.size()) return;
        CommandPaletteFilter.Entry entry = mRowEntries.get(mFocus);
        if (entry != null) beginCapture(entry);
    }

    private void applyCaptureRow() {
        if (mView == null) return;
        mView.setQuery("", "");
        mView.setArgumentMode(true,
            mActivity.getString(R.string.palette_capture_placeholder), mCaptureStroke,
            CAPTURE_PROMPT);
    }

    /** Hardware strokes while capturing. Every branch returns true: nothing may reach the shell. */
    private boolean handleCaptureKey(int keyCode, @NonNull KeyEvent event) {
        return handleCaptureKeyCode(keyCode, event.isCtrlPressed(), event.isAltPressed(),
            event.isShiftPressed());
    }

    /**
     * The in-app keyboard while capturing. It carries the modifier flags itself, which is what lets
     * the overlay work on a phone with no physical keyboard — hardware-only capture would be dead UI
     * on most devices.
     */
    private boolean interceptCaptureKeyValue(@NonNull KeyValue value, boolean ctrl, boolean alt,
                                            boolean shift) {
        switch (value.getKind()) {
            case Char:
                noteCapturedStroke(CommandPaletteCaptureModel.strokeForChar(
                    value.getChar(), ctrl, alt, shift));
                return true;
            case Editing:
                switch (value.getEditing()) {
                    case SPACE_BAR:
                        noteCapturedStroke(CommandPaletteCaptureModel.strokeForChar(
                            ' ', ctrl, alt, shift));
                        break;
                    case BACKSPACE:
                        clearCapture();
                        break;
                    default:
                        break;
                }
                return true;
            case Keyevent:
                return handleCaptureKeyCode(value.getKeyevent(), ctrl, alt, shift);
            case Event:
                if (value.getEvent() == KeyValue.Event.ACTION) commitCapture();
                return true;
            default:
                // Swallowed, like every other unhandled value while the palette owns the keyboard.
                return true;
        }
    }

    /**
     * ⏎ saves, ⌫ clears, Esc backs out to the list. Documented consequence: enter, escape and
     * backspace cannot themselves be bound from the overlay — the conf file stays the escape hatch
     * for those.
     */
    private boolean handleCaptureKeyCode(int keyCode, boolean ctrl, boolean alt, boolean shift) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BACK:
                popMode();
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                commitCapture();
                return true;
            case KeyEvent.KEYCODE_DEL:
                clearCapture();
                return true;
            default:
                noteCapturedStroke(
                    CommandPaletteCaptureModel.strokeFor(keyCode, ctrl, alt, shift));
                return true;
        }
    }

    private void clearCapture() {
        mCaptureStroke = "";
        mCaptureConflict = null;
        mCaptureNeedsKeyEvent = false;
        applyCaptureRow();
        rebuildRows();
    }

    /**
     * Committed IME text reached a capture. Recorded on the notice row rather than as a toast: the
     * user is holding a keyboard that will repeat this on every keystroke, and the row says it once.
     */
    private void noteCaptureNeedsKeyEvent() {
        if (mCaptureNeedsKeyEvent) return;
        mCaptureNeedsKeyEvent = true;
        rebuildRows();
    }

    /** A modifier-only press or an unmappable key code arrives as null and simply does nothing. */
    private void noteCapturedStroke(@Nullable String stroke) {
        if (stroke == null || stroke.isEmpty()) return;
        mCaptureStroke = stroke;
        mCaptureConflict = conflictFor(stroke);
        mCaptureNeedsKeyEvent = false;
        applyCaptureRow();
        rebuildRows();
        playTick();
    }

    /**
     * The tool already holding {@code stroke}, or null. Shown as a warning, never as a refusal:
     * "mentioning a sequence replaces the defaults for it" is the documented file semantics, so
     * blocking the save here would contradict the config model.
     */
    @Nullable
    private String conflictFor(@NonNull String stroke) {
        List<TerminalKeyBindingResolver.Claim> claims =
            TerminalKeyBindingResolver.getInstance().getBindings().get(stroke);
        if (claims == null) return null;
        for (TerminalKeyBindingResolver.Claim claim : claims) {
            if (!"unmap".equals(claim.toolName)) return claim.toolName;
        }
        return null;
    }

    private void commitCapture() {
        CommandPaletteFilter.Entry pending = mPendingEntry;
        if (pending == null) {
            popMode();
            return;
        }
        if (!CommandPaletteCaptureModel.isBindable(mCaptureStroke)) {
            AppNotice.show(mActivity, R.string.palette_capture_needs_modifier, false);
            return;
        }
        String stableId = pending.arguments == null ? "" : pending.arguments.optString("query", "");
        if (stableId.isEmpty()) {
            popMode();
            return;
        }
        // The row's own title becomes the binding's --label, so the keybind legend names the app
        // instead of repeating "Launch app" for every chord captured this way.
        String error = TerminalBindingConfigWriter.bindAppLaunch(mCaptureStroke,
            CommandPaletteAppShortcuts.bindingArgumentFor(stableId,
                defaultStableIdForPackage(stableId)),
            pending.title);
        if (error != null) {
            Logger.logWarn(LOG_TAG, "Could not write binding: " + error);
            AppNotice.show(mActivity, mActivity.getString(R.string.palette_capture_failed, error), false);
            return;
        }
        mAppShortcuts = TerminalCommandPalette.buildAppShortcuts(mAppProvider);
        String saved = mActivity.getString(R.string.palette_capture_saved,
            CommandPaletteFilter.compactStroke(mCaptureStroke), pending.title);
        playTick();
        collapse();
        showConfirmation(saved);
    }

    /** Stable id of this package's default launch target, for choosing what to write. */
    @Nullable
    private String defaultStableIdForPackage(@NonNull String stableId) {
        String packageName = CommandPaletteAppShortcuts.packageOf(stableId);
        if (packageName.isEmpty()) return null;
        com.termux.app.launcher.model.LauncherAppEntry app =
            mAppProvider.findDefaultByPackage(packageName);
        return app == null ? null : app.appRef.stableId();
    }

    private void commit() {
        if (mMode == Mode.CAPTURE) {
            commitCapture();
            return;
        }
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
            AppNotice.show(mActivity, entry.disabledReason != null
                ? entry.disabledReason : mActivity.getString(R.string.palette_empty), false);
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

    /**
     * The palette is gone by the time this is asked, so the confirmation is a sheet card rather than
     * a dialog window: the palette exists to keep a destructive action off the system IME's path,
     * and confirming it in a focus-taking window would hand back exactly what was avoided.
     */
    private void confirmThenRun(@NonNull CommandPaletteFilter.Entry entry) {
        collapse();
        TerminalSheetController sheet = mActivity.getTerminalSheetController();
        LinearLayout body = TerminalSheetViews.body(mActivity);
        if (!entry.subtitle.isEmpty())
            TerminalSheetViews.addMessage(body, entry.subtitle);
        LinearLayout actions = TerminalSheetViews.addActionRow(body);
        TerminalSheetViews.addAction(actions, mActivity.getString(android.R.string.cancel),
            sheet::dismiss);
        TerminalSheetViews.addAction(actions, mActivity.getString(R.string.palette_confirm_run),
            () -> {
                sheet.dismiss();
                run(entry);
            });
        sheet.show(mActivity.getString(R.string.palette_confirm_title, entry.title), body);
    }

    /**
     * Actions whose result is plainly visible on screen, so the palette does not also announce
     * them: a new window or pane is its own confirmation, and the chip on top of it was noise.
     */
    private static final java.util.Set<String> SILENT_TOOLS = new java.util.HashSet<>(
        java.util.Arrays.asList(
            com.termux.launcherctl.LauncherToolRegistry.TOOL_WINDOW_NEW,
            com.termux.launcherctl.LauncherToolRegistry.TOOL_PANE_SPLIT_VERTICAL,
            com.termux.launcherctl.LauncherToolRegistry.TOOL_PANE_SPLIT_HORIZONTAL));

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
            AppNotice.show(mActivity, message, false);
            return;
        }
        if (!SILENT_TOOLS.contains(entry.toolName)) showConfirmation(entry.title);
    }

    /** No match: ⏎ hands the query to the shell verbatim. */
    private void runQueryInShell() {
        String command = mQuery.trim();
        collapse();
        if (command.isEmpty()) return;
        com.termux.terminal.TerminalSession session = mActivity.getCurrentSession();
        if (session == null) {
            AppNotice.show(mActivity, R.string.palette_unavailable_no_session, false);
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

    /**
     * The typed value merged into whatever arguments the row already carries — which is what lets a
     * per-session rename row supply its own index and still be prompted for a name. Package-private
     * for the test that pins exactly that.
     */
    @NonNull
    static CommandPaletteFilter.Entry withArgument(
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
    public void onRowLongPressed(int index) {
        if (index < 0 || index >= mRowEntries.size()) return;
        CommandPaletteFilter.Entry entry = mRowEntries.get(index);
        if (entry == null) return;
        mFocus = index;
        beginCapture(entry);
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
