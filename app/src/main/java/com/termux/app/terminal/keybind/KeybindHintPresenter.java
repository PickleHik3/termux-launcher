package com.termux.app.terminal.keybind;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.terminal.KeybindGroupPalette;
import com.termux.app.terminal.Motion;
import com.termux.app.terminal.TerminalActionDispatcher;
import com.termux.app.terminal.TerminalKeyBindingResolver;
import com.termux.app.terminal.inappkeyboard.TerminalModifiers;
import com.termux.shared.termux.font.NerdFontSpans;
import com.termux.shared.theme.ThemeUtils;
import com.termux.launcherctl.LauncherToolRegistry;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The keybind hint surfaces: while Ctrl+Alt (optionally +Shift) is held — latched on the in-app
 * keyboard or held down on a physical one — the bound caps light up in their legend group's colour
 * on the live keyboard, and a compact strip of the essential binds takes the A–Z row's slot in the
 * dock (or drops from the status bar as a card when that row is not on screen). Pressing {@code ?}
 * under the prefix swaps the strip for the full grouped table.
 *
 * <p>Everything the surfaces <em>say</em> lives in {@link KeybindHintModel}, which is pure. This
 * class owns what <em>happens</em>: the two prefix sources and which outranks which, the spend that
 * takes the hints down the instant a bind runs, the linger that keeps them readable when the
 * modifier lifts, the strip/legend view construction and the dock-row swap choreography. The host
 * activity is reached only through {@link Surface} (view tree, dress, card host, lit caps),
 * {@link Scheduler} (delayed work) and {@link Hints} (what is bound under a prefix), so the
 * choreography can be driven with fakes.
 *
 * <p>Three rules earn their complexity:
 *
 * <ul>
 *   <li><b>A hardware hold outranks the in-app latch.</b> The keyboard reports "no modifiers" on
 *       every key it releases, and that callback must not tear down a surface a physical keyboard
 *       is still holding up.
 *   <li><b>A spent prefix stays quiet.</b> The hints answer "what can I press now"; once something
 *       was pressed the answer is nothing, so they go at once and stay gone until the prefix is let
 *       go and taken up again — on either edge of that gap.
 *   <li><b>The {@code ?} table is sticky.</b> The in-app latch is one-shot and pressing {@code ?}
 *       itself spends it, so releasing the prefix must not retire a table that just opened. It
 *       retires when a bind runs, on another {@code ?}, or on a tap anywhere else.
 * </ul>
 */
public final class KeybindHintPresenter {

    /** The host's view tree, dress, card host and keyboard lighting. */
    public interface Surface {
        /** Context for view construction, string lookup, density and theme attributes. */
        @NonNull Context context();

        /** A view from the host's tree, or null when that part of the layout is not inflated. */
        @Nullable View findView(@IdRes int viewId);

        /** The glass colour the lit caps and legend colours are contrasted against. */
        int accessoryGlassBaseColor();

        boolean isReducedMotionEnabled();

        /** Whether the hint surfaces are wanted at all: off with custom panes disabled. */
        boolean isSplitPanesEnabled();

        /** "Show key hints": with it off nothing drops on its own and only {@code ?} glows. */
        boolean isShowKeyHintsEnabled();

        /**
         * Whether the fallback card is up. The hint surfaces get their own card host in the host
         * activity, so an open stats or weather card and a chord narration never dismiss each
         * other; cards from it are passive — no focus, no swallowed outside touch — because the
         * keyboard hold that raised them must keep working underneath.
         */
        boolean isCardShowing();

        /**
         * Drops {@code content} from the status bar in the standard detail-card dress. A no-op when
         * there is nothing to anchor to.
         *
         * @param onOutsideTap retires the sticky full table on a tap anywhere else; null for the
         *     strip, which tracks the hold alone.
         */
        void showCard(@NonNull View content, @Nullable Runnable onOutsideTap);

        void dismissCard(boolean animated);

        /** Paints the bound caps on the in-app keyboard, or clears them with null. */
        void setKeyboardHintHighlights(@Nullable Map<String, Integer> litTokens);
    }

    /** Delayed work, so the linger and the readout hold can be driven by a fake clock. */
    public interface Scheduler {
        void postDelayed(@NonNull Runnable runnable, long delayMs);

        void remove(@NonNull Runnable runnable);
    }

    /** What is bound one key past a prefix, i.e. {@link TerminalKeyBindingResolver} in production. */
    public interface Hints {
        @NonNull Map<String, TerminalKeyBindingResolver.Hint> hintsForPrefix(@NonNull String prefix);
    }

    /** The live resolver under the dispatcher's current action context. */
    @NonNull
    public static Hints resolverHints() {
        return prefix -> TerminalKeyBindingResolver.getInstance()
            .hintsForPrefix(prefix, TerminalActionDispatcher.getInstance().actionContext());
    }

    // ---------------------------------------------------------------- choreography clocks

    /**
     * Lingers before the hints go: releasing the prefix is also how a stroke is typed, so
     * dismissing the instant the modifier lifts blinks the card away mid-read on every use.
     */
    public static final long LINGER_MS = 450L;
    /** The dock-row swap's clock: outgoing text clears before incoming text lands on it. */
    public static final long SWAP_OUT_MS = 50L;
    public static final long SWAP_IN_MS = 70L;
    public static final long SWAP_STAGGER_MS = 30L;
    /** A consumed bind's hints leave fast but not as a one-frame vanish under the finger. */
    public static final long CONSUMED_EXIT_MS = 60L;
    private static final long LEGEND_BASE_DELAY_MS = 60L;
    private static final long LEGEND_STAGGER_MS = 26L;
    private static final long LEGEND_ENTER_MS = 280L;
    /**
     * Long enough to read a combo at a glance, short enough that the letters are back before the
     * next deliberate scrub; a fresh press just restarts it.
     */
    public static final long EXTRA_KEY_READOUT_HOLD_MS = 600L;

    // ---------------------------------------------------------------- state

    @NonNull private final Surface mSurface;
    @NonNull private final Scheduler mScheduler;
    @NonNull private final Hints mHints;
    @NonNull private final KeybindHintModel.Labels mLabels;

    /** Prefix the in-app keyboard's latch is asking for, or null. */
    @Nullable private String mInAppPrefix;
    private boolean mInAppShift;
    /** Prefix a physical keyboard is holding, or null. Outranks the in-app latch. */
    @Nullable private String mHardwarePrefix;
    private boolean mHardwareShift;
    /** Set when a binding under the shown prefix actually ran. */
    private boolean mSpent;
    /** Effective prefix at the last refresh, to notice a fresh one being taken up. */
    @Nullable private String mLastPrefix;
    /** True while {@code ?} promoted the surface from the strip (or nothing) to the full table. */
    private boolean mFullMode;
    /** Signature of the shown content, to skip rebuilds on repeated modifier callbacks. */
    @Nullable private String mSignature;
    /** True while the dock row's content is a readout rather than the leader's hint strip. */
    private boolean mExtraKeyReadoutActive;

    private final Runnable mHideRunnable = () -> hideNow(true);

    private final Runnable mExtraKeyReadoutHide = () -> {
        if (!mExtraKeyReadoutActive) return;
        mExtraKeyReadoutActive = false;
        hideDockRow(true);
    };

    public KeybindHintPresenter(@NonNull Surface surface, @NonNull Scheduler scheduler,
                               @NonNull Hints hints) {
        this(surface, scheduler, hints, null);
    }

    /**
     * @param labels names an action for the legend; null uses the launcher tool registry, which is
     *     what production wants and what a test without a registry cannot have.
     */
    public KeybindHintPresenter(@NonNull Surface surface, @NonNull Scheduler scheduler,
                               @NonNull Hints hints,
                               @Nullable KeybindHintModel.Labels labels) {
        mSurface = surface;
        mScheduler = scheduler;
        mHints = hints;
        mLabels = labels != null ? labels : this::registryLabel;
    }

    // ---------------------------------------------------------------- driving

    /**
     * The in-app keyboard's modifier state changed. Any modifier state other than Ctrl+Alt(+Shift)
     * removes everything, so latch, lock and release all track for free.
     */
    public void onInAppModifiersChanged(@Nullable TerminalModifiers modifiers) {
        boolean latched = modifiers != null && modifiers.isCtrl() && modifiers.isAlt();
        mInAppPrefix = latched ? "ctrl+alt+" : null;
        mInAppShift = latched && modifiers.isShift();
        refresh();
    }

    /**
     * The hardware twin: a physical keyboard holding Ctrl+Alt, or a latched {@code leader} prefix
     * waiting for its second key.
     *
     * @param prefix the stroke prefix being documented, e.g. {@code "ctrl+alt+"} or
     *     {@code "ctrl+space>"}, or null when nothing is held.
     */
    public void setHardwarePrefix(@Nullable String prefix, boolean shift) {
        if (Objects.equals(prefix, mHardwarePrefix) && shift == mHardwareShift) return;
        mHardwarePrefix = prefix;
        mHardwareShift = shift;
        refresh();
    }

    /**
     * {@code ?} pressed under an active prefix: swaps between the resting surface (the strip, or
     * nothing when hints are off) and the full table. A bind that already ran under this hold is
     * forgiven — asking for the table is taking the prefix up again.
     */
    public void toggleFullPopup() {
        boolean hardware = mHardwarePrefix != null;
        String prefix = hardware ? mHardwarePrefix : mInAppPrefix;
        if (prefix == null) return;
        mFullMode = !mFullMode;
        mSpent = false;
        show(prefix, hardware ? mHardwareShift : mInAppShift);
    }

    /**
     * Takes the hints down the moment a binding runs, with no linger: the surface is an answer to a
     * question the keystroke just answered, and holding it over the action's own UI reads as a
     * stuck popup. The lingering hide is for the other ending — the prefix released without
     * pressing anything.
     */
    public void onConsumed() {
        mSpent = true;
        mFullMode = false;
        hideNow(false);
    }

    /** Whether a hint surface is on screen, i.e. whether a pending prefix is already announced. */
    public boolean isVisible() {
        return mSurface.isCardShowing() || isDockRowVisible();
    }

    /** Lets the surfaces go after the read-time linger. */
    public void hideAfterLinger() {
        if (!mSurface.isCardShowing() && !isDockRowVisible()) {
            mSignature = null;
            mSurface.setKeyboardHintHighlights(null);
            return;
        }
        mScheduler.remove(mHideRunnable);
        mScheduler.postDelayed(mHideRunnable, LINGER_MS);
    }

    /** Drops everything at once; {@code fade} is the read-time exit rather than a bind's exit. */
    public void hideNow(boolean fade) {
        cancelExtraKeyReadout();
        mSurface.setKeyboardHintHighlights(null);
        mSignature = null;
        mScheduler.remove(mHideRunnable);
        hideDockRow(fade);
        if (!mSurface.isCardShowing()) return;
        mSurface.dismissCard(fade);
    }

    /**
     * A touch on the terminal while the strip occupies the A–Z row dismisses it — the user has
     * moved on from the chord — and stays gone until the prefix is taken up afresh. The sticky full
     * card handles the same gesture through its own outside-tap watcher.
     */
    public void onTerminalTouch(@NonNull MotionEvent ev) {
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) return;
        if (!isDockRowVisible()) return;
        View stack = mSurface.findView(R.id.accessory_stack_container);
        if (stack == null || !stack.isAttachedToWindow()) return;
        int[] location = new int[2];
        stack.getLocationOnScreen(location);
        if (ev.getRawY() < location[1]) {
            mSpent = true;
            hideNow(true);
        }
    }

    /**
     * A hardware hold outranks the in-app keyboard's latch, and the spend re-arms on both edges of
     * the gap: letting the prefix go, and taking a new one up. Only the first was checked once, and
     * the spend is recorded <em>after</em> the release pass for a leader chord, so the flag
     * survived into the next prefix and swallowed its legend.
     */
    private void refresh() {
        boolean hardware = mHardwarePrefix != null;
        String prefix = hardware ? mHardwarePrefix : mInAppPrefix;
        if (prefix == null || mLastPrefix == null) mSpent = false;
        if (prefix == null) {
            if (mFullMode && mSurface.isCardShowing()) {
                mLastPrefix = null;
                return;
            }
            mFullMode = false;
        }
        mLastPrefix = prefix;
        if (mSpent) {
            hideNow(false);
            return;
        }
        show(prefix, hardware ? mHardwareShift : mInAppShift);
    }

    // ---------------------------------------------------------------- rendering

    private void show(@Nullable String basePrefix, boolean shift) {
        mScheduler.remove(mHideRunnable);
        if (basePrefix == null || !mSurface.isSplitPanesEnabled()) {
            hideAfterLinger();
            return;
        }
        String prefix = shift ? basePrefix + "shift+" : basePrefix;
        Map<String, TerminalKeyBindingResolver.Hint> hints = mHints.hintsForPrefix(prefix);
        if (hints.isEmpty()) {
            hideAfterLinger();
            return;
        }
        boolean showHints = mSurface.isShowKeyHintsEnabled();
        // A latched leader follows the same heuristics as the held Ctrl+Alt: the strip (or
        // nothing, per the preference), with ? opening the full table.
        boolean full = mFullMode;

        if (!full && !showHints) {
            // Nothing drops on its own: the ? cap glowing on the keyboard is the whole surface.
            mSignature = null;
            if (mSurface.isCardShowing()) mSurface.dismissCard(true);
            mSurface.setKeyboardHintHighlights(questionGlow());
            return;
        }

        Map<String, TerminalKeyBindingResolver.Hint> ctrlHints = java.util.Collections.emptyMap();
        if (full && !"ctrl+".equals(basePrefix)) {
            // Bindings that are one plain Ctrl stroke — pane focus lives there now — are listed
            // alongside the prefixed table. They are a different chord, so they are spelled out
            // with their own "Ctrl+" caps and they never light a key.
            ctrlHints = mHints.hintsForPrefix("ctrl+");
        }
        // Modifier callbacks repeat for the same latch state; only content changes rebuild.
        String signature = (full ? "full|" : "strip|") + prefix + '|' + hints + '|' + ctrlHints;
        if ((mSurface.isCardShowing() || isDockRowVisible()) && signature.equals(mSignature)) return;
        mSignature = signature;

        View content;
        if (full) {
            LinearLayout legend = new LinearLayout(context());
            legend.setOrientation(LinearLayout.VERTICAL);
            Map<String, Integer> litTokens = populateLegend(legend, hints, ctrlHints, shift);
            mSurface.setKeyboardHintHighlights(litTokens);
            hideDockRow(true);
            content = wrapScrolling(legend);
        } else {
            Map<String, Integer> litTokens =
                KeybindHintModel.litTokens(hints, this::groupColor);
            mSurface.setKeyboardHintHighlights(litTokens);
            View strip = buildStrip(hints, shift, litTokens);
            if (strip == null) {
                hideAfterLinger();
                return;
            }
            // The A-Z row's slot is the strip's first home: space the dock already pays for,
            // directly above the keys being pressed. The top card is only the fallback for
            // when that row is not on screen (apps bar hidden, hardware-keyboard-only).
            if (canUseDockRow()) {
                if (mSurface.isCardShowing()) mSurface.dismissCard(true);
                // The leader's strip evicts any extra-key readout still holding the slot, and its
                // pending hide must die with it or it would take the strip down mid-latch.
                cancelExtraKeyReadout();
                showDockRow(strip);
                return;
            }
            hideDockRow(true);
            content = strip;
        }
        // The sticky full table watches for a tap anywhere else and retires itself; the strip
        // tracks the hold alone.
        Runnable onOutsideTap = full ? () -> {
            mFullMode = false;
            hideNow(true);
        } : null;
        mSurface.showCard(content, onOutsideTap);
    }

    /** The one lit cap of hints-off mode: {@code ?} glowing in the primary colour. */
    @NonNull
    private Map<String, Integer> questionGlow() {
        return java.util.Collections.singletonMap("?", themeColor(
            com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary));
    }

    /**
     * One compact row of curated binds for the held table — the base list under Ctrl+Alt, the
     * session/resize list once Shift joins — closed by a {@code ?} chip that opens the full table.
     * Chips are spread evenly across the row by weighted gaps, so the strip reads as a band rather
     * than a huddle. Returns null when nothing curated is actually bound.
     */
    @Nullable
    private View buildStrip(@NonNull Map<String, TerminalKeyBindingResolver.Hint> hints,
                            boolean shift, @NonNull Map<String, Integer> litTokens) {
        List<KeybindHintModel.StripChip> chips = KeybindHintModel.stripChips(hints, shift);
        if (chips.isEmpty()) return null;
        LinearLayout strip = new LinearLayout(context());
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        int onSurface = themeColor(com.termux.shared.R.attr.termuxColorOnSurface,
            R.color.termux_on_surface);
        for (KeybindHintModel.StripChip chip : chips) {
            addStripGap(strip);
            addChip(strip, chip.caps, chip.label, litTokens.get(chip.colorToken), onSurface, false);
        }
        addStripGap(strip);
        TextView more = addChip(strip, "?", "",
            withAlphaComponent(onSurface, 140), onSurface, false);
        more.setOnClickListener(view -> toggleFullPopup());
        addStripGap(strip);
        return strip;
    }

    /** A weighted gap: in the full-width dock slot the gaps share the leftover space evenly. */
    private void addStripGap(@NonNull LinearLayout strip) {
        View gap = new View(context());
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.width = Math.round(dpToPx(6));
        strip.addView(gap, params);
    }

    /**
     * One strip chip: bold mono caps in the group colour, then a lower-case label (or none for the
     * bare {@code ?}). Nerd-symbol labels render from the bundled symbol face.
     */
    private TextView addChip(@NonNull LinearLayout strip, @NonNull String caps,
                             @NonNull String label, @Nullable Integer capColor, int onSurface,
                             boolean spaced) {
        TextView chip = new TextView(context());
        SpannableStringBuilder text = new SpannableStringBuilder();
        text.append(caps);
        text.setSpan(new ForegroundColorSpan(capColor != null ? capColor : onSurface),
            0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD),
            0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new TypefaceSpan("monospace"),
            0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!label.isEmpty()) {
            int labelStart = text.length();
            text.append(' ').append(label);
            text.setSpan(new ForegroundColorSpan(withAlphaComponent(onSurface, 199)),
                labelStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        NerdFontSpans.applyTo(context(), text);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
        chip.setSingleLine(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (spaced) params.leftMargin = Math.round(dpToPx(12));
        strip.addView(chip, params);
        return chip;
    }

    /** Scrolls a long legend instead of letting the card reach the keyboard. */
    @NonNull
    private View wrapScrolling(@NonNull View legend) {
        ScrollView scroll = new ScrollView(context()) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int cap = Math.round(getResources().getDisplayMetrics().heightPixels * 0.45f);
                super.onMeasure(widthSpec,
                    View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(legend, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /** Builds the legend and returns the binding token -> group colour map for the keyboard. */
    @NonNull
    private Map<String, Integer> populateLegend(
            @NonNull LinearLayout popup,
            @NonNull Map<String, TerminalKeyBindingResolver.Hint> hints,
            @NonNull Map<String, TerminalKeyBindingResolver.Hint> ctrlHints,
            boolean shift) {
        popup.removeAllViews();
        boolean animate = !mSurface.isReducedMotionEnabled();
        int onSurface = themeColor(com.termux.shared.R.attr.termuxColorOnSurface,
            R.color.termux_on_surface);
        KeybindHintModel.Legend legend =
            KeybindHintModel.entriesFor(hints, ctrlHints, shift, mLabels, this::groupColor);

        int groupIndex = 0;
        for (Map.Entry<KeybindGroupPalette.Group, List<KeybindHintModel.Entry>> group
                : legend.groups.entrySet()) {
            int groupColor = legend.groupColors.get(group.getKey());
            View groupView = buildGroup(group.getKey().title(), group.getValue(),
                groupColor, onSurface);
            LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (groupIndex > 0) groupParams.topMargin = Math.round(dpToPx(6));
            popup.addView(groupView, groupParams);
            if (animate) {
                groupView.setAlpha(0f);
                groupView.setTranslationY(dpToPx(8));
                groupView.animate().alpha(1f).translationY(0f).setDuration(LEGEND_ENTER_MS)
                    .setStartDelay(LEGEND_BASE_DELAY_MS + groupIndex * LEGEND_STAGGER_MS)
                    .setInterpolator(new PathInterpolator(0.2f, 0.8f, 0.2f, 1f))
                    .start();
            }
            groupIndex++;
        }
        return legend.litTokens;
    }

    @NonNull
    private View buildGroup(@NonNull String title,
                            @NonNull List<KeybindHintModel.Entry> entries,
                            int groupColor, int onSurface) {
        LinearLayout group = new LinearLayout(context());
        group.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(context());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View swatch = new View(context());
        GradientDrawable swatchShape = new GradientDrawable();
        swatchShape.setColor(groupColor);
        swatchShape.setCornerRadius(dpToPx(1));
        swatch.setBackground(swatchShape);
        int swatchSize = Math.round(dpToPx(3.5f));
        LinearLayout.LayoutParams swatchParams =
            new LinearLayout.LayoutParams(swatchSize, swatchSize);
        swatchParams.rightMargin = Math.round(dpToPx(4.5f));
        header.addView(swatch, swatchParams);

        TextView titleView = new TextView(context());
        titleView.setText(title);
        titleView.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 6.5f);
        titleView.setLetterSpacing(0.2f);
        titleView.setTextColor(groupColor);
        header.addView(titleView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View rule = new View(context());
        rule.setBackgroundColor(withAlphaComponent(groupColor, 51));
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(0,
            Math.max(1, Math.round(dpToPx(0.5f))), 1f);
        ruleParams.leftMargin = Math.round(dpToPx(6));
        header.addView(rule, ruleParams);
        group.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int labelColor = withAlphaComponent(onSurface, 199);
        LinearLayout row = null;
        for (int i = 0; i < entries.size(); i++) {
            if (row == null || i % KeybindHintModel.COLUMNS == 0) {
                row = new LinearLayout(context());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = Math.round(dpToPx(i == 0 ? 6 : 1));
                group.addView(row, rowParams);
            }
            KeybindHintModel.Entry entry = entries.get(i);
            LinearLayout cell = new LinearLayout(context());
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER_VERTICAL);

            TextView key = new TextView(context());
            key.setText(entry.cap);
            key.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            key.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 8.5f);
            key.setTextColor(groupColor);
            key.setMinWidth(Math.round(dpToPx(22)));
            cell.addView(key, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView label = new TextView(context());
            label.setText(entry.label);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f);
            label.setTextColor(labelColor);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.leftMargin = Math.round(dpToPx(4.5f));
            labelParams.rightMargin =
                i % KeybindHintModel.COLUMNS == 0 ? Math.round(dpToPx(8)) : 0;
            cell.addView(label, labelParams);

            row.addView(cell, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        // An odd trailing cell still gets its half of the row, keeping columns aligned.
        if (row != null && row.getChildCount() == 1 && KeybindHintModel.COLUMNS == 2) {
            View filler = new View(context());
            row.addView(filler, new LinearLayout.LayoutParams(0, 1, 1f));
        }
        return group;
    }

    // ---------------------------------------------------------------- the dock row's slot

    private boolean isDockRowVisible() {
        View row = mSurface.findView(R.id.keybind_hint_dock_row);
        return row != null && row.getVisibility() == View.VISIBLE;
    }

    /** Whether the A-Z row's slot is on screen and able to host the strip. */
    private boolean canUseDockRow() {
        View az = mSurface.findView(R.id.apps_bar_az_row);
        View row = mSurface.findView(R.id.keybind_hint_dock_row);
        if (az == null || row == null) return false;
        // While the strip holds the slot the letters are INVISIBLE by design; the slot is
        // still ours. Without this, Shift joining mid-latch re-decided against the slot and
        // opened the fallback card on top of the still-showing strip.
        if (row.getVisibility() == View.VISIBLE) return true;
        return az.isShown() && az.getHeight() > 0;
    }

    /**
     * Swaps the strip into the A-Z row's slot: the letters sink away as the chips settle in, the
     * exact reverse plays on the way out, and neither surface ever moves the dock's geometry
     * because the hint row is pinned to the A-Z row's own bounds.
     */
    private void showDockRow(@NonNull View strip) {
        View rowView = mSurface.findView(R.id.keybind_hint_dock_row);
        View az = mSurface.findView(R.id.apps_bar_az_row);
        if (!(rowView instanceof HorizontalScrollView) || az == null) return;
        HorizontalScrollView row = (HorizontalScrollView) rowView;
        row.setFillViewport(true);
        row.setTranslationZ(dpToPx(4));
        row.removeAllViews();
        if (strip instanceof LinearLayout) ((LinearLayout) strip).setGravity(Gravity.CENTER);
        row.addView(strip, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        boolean alreadyUp = row.getVisibility() == View.VISIBLE;
        // A hide may still be animating this row out (the consumed-bind fade); it must not win
        // the race and GONE a row that has just been repopulated.
        row.animate().cancel();
        row.setVisibility(View.VISIBLE);
        if (mSurface.isReducedMotionEnabled() || alreadyUp) {
            row.setAlpha(1f);
            row.setTranslationY(0f);
            az.setAlpha(0f);
            az.setTranslationY(0f);
            az.setVisibility(View.INVISIBLE);
            return;
        }
        // A staggered vertical swap, not a same-slot crossfade: both rows are dense text in the
        // same band, and fading them over each other collides their glyphs into an illegible
        // in-between. The letters clear out first (up and away), the chips follow from below.
        Interpolator ease = Motion.settle();
        az.animate().cancel();
        // INVISIBLE at the end of the fade, not just alpha 0: a transparent scrub row still
        // owns its touches, and a tap meant for a chip must never jump the app pages.
        az.animate().alpha(0f).translationY(-dpToPx(4)).setDuration(SWAP_OUT_MS)
            .setInterpolator(ease)
            .withEndAction(() -> {
                az.setVisibility(View.INVISIBLE);
                az.setTranslationY(0f);
            }).start();
        row.setAlpha(0f);
        row.setTranslationY(dpToPx(4));
        row.animate().alpha(1f).translationY(0f).setDuration(SWAP_IN_MS)
            .setStartDelay(SWAP_STAGGER_MS).setInterpolator(ease).start();
    }

    /** Returns the A-Z row's letters to their slot; the strip lifts away the way it came. */
    private void hideDockRow(boolean fade) {
        View rowView = mSurface.findView(R.id.keybind_hint_dock_row);
        if (!(rowView instanceof HorizontalScrollView)
            || rowView.getVisibility() != View.VISIBLE) return;
        HorizontalScrollView row = (HorizontalScrollView) rowView;
        View az = mSurface.findView(R.id.apps_bar_az_row);
        row.animate().cancel();
        if (az != null) az.animate().cancel();
        if (mSurface.isReducedMotionEnabled()) {
            row.setVisibility(View.GONE);
            row.removeAllViews();
            row.setAlpha(1f);
            row.setTranslationY(0f);
            if (az != null) {
                az.setVisibility(View.VISIBLE);
                az.setAlpha(1f);
                az.setTranslationY(0f);
            }
            return;
        }
        Interpolator ease = Motion.settle();
        if (!fade) {
            // A bind just ran: the letters are wanted back now, but a one-frame vanish of the
            // strip reads as a glitch, so it gets a short fade over the restored row.
            if (az != null) {
                az.setVisibility(View.VISIBLE);
                az.setAlpha(1f);
                az.setTranslationY(0f);
            }
            row.animate().alpha(0f).setDuration(CONSUMED_EXIT_MS)
                .setInterpolator(ease)
                .withEndAction(() -> {
                    row.setVisibility(View.GONE);
                    row.removeAllViews();
                    row.setAlpha(1f);
                    row.setTranslationY(0f);
                })
                .start();
            return;
        }
        // The enter swap in reverse: the chips sink back below, the letters return from above.
        if (az != null) {
            az.setVisibility(View.VISIBLE);
            az.setTranslationY(-dpToPx(4));
            az.animate().alpha(1f).translationY(0f).setDuration(SWAP_IN_MS)
                .setStartDelay(SWAP_STAGGER_MS).setInterpolator(ease).start();
        }
        row.animate().alpha(0f).translationY(dpToPx(4)).setDuration(SWAP_OUT_MS)
            .setInterpolator(ease)
            .withEndAction(() -> {
                row.setVisibility(View.GONE);
                row.removeAllViews();
                row.setAlpha(1f);
                row.setTranslationY(0f);
            })
            .start();
    }

    // ---------------------------------------------------------------- extra-key press readout

    /**
     * Names a pressed extra key in the A-Z row's slot — the same surface the leader's hint strip
     * borrows — so the eye never has to leave the dock to confirm what a glyph key just sent.
     * Repeated presses swap the label in place; the leader's strip outranks it and evicts it.
     */
    public void showExtraKeyPressReadout(@Nullable CharSequence label) {
        if (label == null || label.length() == 0) return;
        // While a latched prefix owns the slot (or its fallback card is up), the readout stays
        // quiet: the strip is answering a question the user is still asking.
        if (!mExtraKeyReadoutActive && (isDockRowVisible() || mSurface.isCardShowing())) return;
        if (!canUseDockRow()) return;
        LinearLayout strip = new LinearLayout(context());
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER);
        int onSurface = themeColor(com.termux.shared.R.attr.termuxColorOnSurface,
            R.color.termux_on_surface);
        int primary = themeColor(com.termux.shared.R.attr.termuxColorPrimary,
            R.color.termux_primary);
        addChip(strip, label.toString(), "", primary, onSurface, false);
        mExtraKeyReadoutActive = true;
        showDockRow(strip);
        mScheduler.remove(mExtraKeyReadoutHide);
        mScheduler.postDelayed(mExtraKeyReadoutHide, EXTRA_KEY_READOUT_HOLD_MS);
    }

    /** Drops readout state without touching the row — for when the hint strip takes the slot. */
    private void cancelExtraKeyReadout() {
        mExtraKeyReadoutActive = false;
        mScheduler.remove(mExtraKeyReadoutHide);
    }

    // ---------------------------------------------------------------- dress

    @NonNull
    private Context context() {
        return mSurface.context();
    }

    private int groupColor(@NonNull KeybindGroupPalette.Group group) {
        return KeybindGroupPalette.colorFor(group,
            themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary),
            mSurface.accessoryGlassBaseColor());
    }

    /**
     * A --label in the binding file wins: only the user knows that Ctrl+Alt+W is "WhatsApp" rather
     * than the generic "Launch app" every app chord would otherwise print.
     */
    @NonNull
    private String registryLabel(@NonNull String toolName, @Nullable String bindingLabel) {
        if (bindingLabel != null && !bindingLabel.isEmpty()) return bindingLabel;
        LauncherToolRegistry.ToolMetadata tool =
            LauncherToolRegistry.getInstance().getTool(toolName);
        if (tool != null && tool.titleRes != 0) return context().getString(tool.titleRes);
        return toolName;
    }

    private int themeColor(int attr, int fallbackRes) {
        return ThemeUtils.getSystemAttrColor(context(), attr,
            ContextCompat.getColor(context(), fallbackRes));
    }

    private float dpToPx(float dp) {
        return dp * context().getResources().getDisplayMetrics().density;
    }

    private static int withAlphaComponent(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
