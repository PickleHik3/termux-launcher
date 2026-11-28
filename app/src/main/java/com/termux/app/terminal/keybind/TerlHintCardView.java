package com.termux.app.terminal.keybind;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;

import com.termux.R;

import java.util.List;
import java.util.Locale;

/**
 * The Terl key's hint card on screen (ADR 0042; round 3's layout): a glass panel in the sheets
 * layer anchored to the top of the screen under the status bar, the screen's width less a gap each
 * side, listing every binding by intent in as many columns as fit, its height capped above the bar
 * and the keyboard. It takes no focus and no touch outside itself, so the hold that raised it keeps
 * working underneath; a tap on a key cap fires that chord, as a second finger on the keyboard does.
 */
public final class TerlHintCardView extends FrameLayout implements TerlHintCardPresenter.Surface {

    /** What the card asks of the activity. */
    public interface Host {
        /** The sheets layer the card stands in; null while there is none. */
        @Nullable ViewGroup sheetsHost();

        /** The card's bottom edge in {@code host}'s coordinates: above the bar and the keyboard. */
        int floorPx(@NonNull ViewGroup host);

        /** The card's top edge in {@code host}'s coordinates: under Android's status bar. */
        default int topPx(@NonNull ViewGroup host) {
            androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(host);
            if (insets == null) return host.getPaddingTop();
            int[] at = new int[2];
            host.getLocationInWindow(at);
            int bars = androidx.core.view.WindowInsetsCompat.Type.statusBars()
                | androidx.core.view.WindowInsetsCompat.Type.displayCutout();
            return Math.max(host.getPaddingTop(), insets.getInsets(bars).top - at[1]);
        }

        /** The sheets' glass at {@code radiusPx}, cut for {@code anchor}; null for the plain fill. */
        @Nullable Drawable glass(float radiusPx, @NonNull View anchor);

        float cornerRadiusPx();

        /** The ink the card's words wear on its glass. */
        int ink();

        boolean isReducedMotionEnabled();

        /** A cap was tapped: fires the leader chord {@code chord} (the stroke after the leader). */
        void fire(@NonNull String chord);
    }

    private static final float GAP_DP = 6f;
    private static final float PAD_DP = 12f;
    /** The narrowest a column of rows may be; the card takes as many as fit, up to {@link #MAX_COLUMNS}. */
    static final float COLUMN_DP = 240f;
    static final int MAX_COLUMNS = 4;
    private static final float CAP_DP = 26f;
    private static final float SLIDE_DP = 24f;
    private static final long ENTER_MS = 200L;
    private static final long EXIT_MS = 140L;

    @NonNull private final Host mHost;
    private final float mDensity;
    @NonNull private final CardScroll mScroll;
    @NonNull private final LinearLayout mContent;

    public TerlHintCardView(@NonNull Context context, @NonNull Host host) {
        super(context);
        mHost = host;
        mDensity = context.getResources().getDisplayMetrics().density;
        // Passive: the card is no window that takes keys, and a touch beside it is not its.
        setFocusable(false);
        setClickable(false);
        mScroll = new CardScroll(context);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setClickable(true);
        mContent = new LinearLayout(context);
        mContent.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(PAD_DP);
        mContent.setPadding(pad, pad, pad, dp(10f));
        mScroll.addView(mContent, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mScroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        mScroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.post(this::dress);
        });
    }

    // ---------------------------------------------------------------- Surface

    @Override
    public void show(@NonNull String leaderName, @NonNull List<TerlHintCard.Section> sections) {
        ViewGroup host = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : mHost.sheetsHost();
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return;
        boolean entering = getParent() == null || mLeaving;
        int gap = dp(GAP_DP);
        int width = Math.max(0, host.getWidth() - 2 * gap);
        populate(leaderName, sections, columnsFor(width));
        int top = Math.max(0, mHost.topPx(host)) + gap;
        int floor = Math.max(top, Math.min(host.getHeight(), mHost.floorPx(host)) - gap);
        mScroll.mMaxHeight = Math.max(dp(120f), floor - top);
        LayoutParams params = new LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.START);
        params.leftMargin = gap;
        params.topMargin = top;
        if (getParent() == null) {
            host.addView(this, params);
        } else {
            setLayoutParams(params);
        }
        // A card on its way out comes back as a new one does.
        if (entering) animateIn();
    }

    @Override
    public void hide() {
        ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        if (parent == null) return;
        animate().cancel();
        if (mHost.isReducedMotionEnabled()) {
            parent.removeView(this);
            return;
        }
        mLeaving = true;
        animate().alpha(0f).translationY(-dp(SLIDE_DP) / 2f).setDuration(EXIT_MS)
            .withEndAction(() -> {
                mLeaving = false;
                if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
            }).start();
    }

    @Override
    public boolean isShowing() {
        // A card on its way out is gone already: a new hold shows it again.
        return getParent() != null && getAlpha() > 0f && !mLeaving;
    }

    private boolean mLeaving;

    private void animateIn() {
        animate().cancel();
        mLeaving = false;
        if (mHost.isReducedMotionEnabled()) {
            setAlpha(1f);
            setTranslationY(0f);
            return;
        }
        // It comes down from the top edge it is anchored to.
        setAlpha(0f);
        setTranslationY(-dp(SLIDE_DP));
        animate().alpha(1f).translationY(0f).setDuration(ENTER_MS)
            .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animate().cancel();
        mLeaving = false;
        setAlpha(1f);
        setTranslationY(0f);
    }

    // ---------------------------------------------------------------- content

    /** As many columns of at least {@link #COLUMN_DP} as the card's width {@code widthPx} holds, one to {@link #MAX_COLUMNS}. */
    int columnsFor(int widthPx) {
        int fit = (int) ((widthPx - 2 * dp(PAD_DP)) / (COLUMN_DP * mDensity));
        return Math.max(1, Math.min(MAX_COLUMNS, fit));
    }

    /** Every cell and title in reading order, for the test helpers. */
    @NonNull private final java.util.List<View> mReading = new java.util.ArrayList<>();

    private void populate(@NonNull String leaderName, @NonNull List<TerlHintCard.Section> sections,
                          int columns) {
        mContent.removeAllViews();
        mReading.clear();
        int ink = mHost.ink();
        TextView header = text(getContext().getString(R.string.terl_hints_header, leaderName)
            .toUpperCase(Locale.ROOT), ink, 12f, true);
        header.setLetterSpacing(0.08f);
        header.setTextColor(ColorUtils.setAlphaComponent(ink, 190));
        mContent.addView(header);
        mReading.add(header);
        // The sections flow down the columns in order, each column taking about an equal share of
        // the lines (a title counts as one), a section never split between two.
        int lines = 0;
        for (TerlHintCard.Section section : sections) lines += section.rows.size() + 1;
        int share = (lines + columns - 1) / columns;
        LinearLayout grid = new LinearLayout(getContext());
        grid.setOrientation(LinearLayout.HORIZONTAL);
        mContent.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout column = null;
        int inColumn = 0;
        int made = 0;
        for (TerlHintCard.Section section : sections) {
            int size = section.rows.size() + 1;
            if (column == null || (inColumn > 0 && inColumn + size > share && made < columns)) {
                column = new LinearLayout(getContext());
                column.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (made > 0) params.leftMargin = dp(8f);
                grid.addView(column, params);
                made++;
                inColumn = 0;
            }
            TextView title = text(getContext().getString(groupTitle(section.group))
                .toUpperCase(Locale.ROOT), ink, 11f, true);
            title.setLetterSpacing(0.06f);
            title.setTextColor(ColorUtils.setAlphaComponent(ink, 150));
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.topMargin = dp(10f);
            titleParams.bottomMargin = dp(2f);
            column.addView(title, titleParams);
            mReading.add(title);
            for (TerlHintCard.Row row : section.rows) {
                View cell = cell(row, labelOf(row), ink);
                column.addView(cell, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
                mReading.add(cell);
            }
            inColumn += size;
        }
        // Columns not reached keep the others' widths.
        for (int i = made; i < columns; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 0, 1f);
            params.leftMargin = dp(8f);
            grid.addView(new View(getContext()), params);
        }
    }

    /** How many columns the card stands in now, for tests. */
    int columnCount() {
        if (mContent.getChildCount() < 2 || !(mContent.getChildAt(1) instanceof ViewGroup)) return 0;
        ViewGroup grid = (ViewGroup) mContent.getChildAt(1);
        int count = 0;
        for (int i = 0; i < grid.getChildCount(); i++) if (grid.getChildAt(i) instanceof LinearLayout) count++;
        return count;
    }

    @NonNull
    private View cell(@NonNull TerlHintCard.Row row, @NonNull String label, int ink) {
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setMinimumHeight(dp(32f));
        cell.setPadding(dp(4f), dp(2f), dp(4f), dp(2f));
        for (TerlHintCard.Cap cap : row.caps) {
            TextView face = text(cap.text, ink, 12f, true);
            face.setGravity(Gravity.CENTER);
            face.setMinWidth(dp(CAP_DP));
            face.setMinimumWidth(dp(CAP_DP));
            face.setPadding(dp(6f), 0, dp(6f), 0);
            GradientDrawable chip = new GradientDrawable();
            chip.setCornerRadius(dp(8f));
            chip.setColor(ColorUtils.setAlphaComponent(ink, 0x1F));
            chip.setStroke(Math.max(1, Math.round(mDensity)), ColorUtils.setAlphaComponent(ink, 0x24));
            face.setBackground(chip);
            if (cap.chord != null) {
                String chord = cap.chord;
                face.setOnClickListener(v -> mHost.fire(chord));
                // Said as the key and what it does ("Enter, Terminal"), never as its glyph.
                face.setContentDescription(getContext().getString(R.string.terl_hints_cap_description,
                    TerlHintCard.spokenKeys(chord, this::keyName), label));
            } else {
                // ⇧ and an elided run say nothing of their own: the caps beside them do.
                face.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(CAP_DP));
            params.rightMargin = dp(4f);
            cell.addView(face, params);
        }
        TextView words = text(label, ink, 12.5f, false);
        words.setTextColor(ColorUtils.setAlphaComponent(ink, 200));
        words.setSingleLine(true);
        words.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = dp(4f);
        cell.addView(words, params);
        return cell;
    }

    @NonNull
    private String labelOf(@NonNull TerlHintCard.Row row) {
        if (row.family == null) return row.label == null ? "" : row.label;
        return getContext().getString(familyWords(row.family));
    }

    /**
     * The card's fill where no glass is given: the side away from the ink, so the words the ink
     * was chosen for read on it in light and dark alike (pale ink on a dark fill, dark on pale).
     */
    static int fallbackFill(int ink) {
        return ColorUtils.calculateLuminance(ink) > 0.5d
            ? ColorUtils.setAlphaComponent(0xFF1B1B1F, 0xE6)
            : ColorUtils.setAlphaComponent(0xFFF4F4F7, 0xE6);
    }

    /** A named key's spoken name, from the resources; null for a letter or a digit. */
    @Nullable
    String keyName(@NonNull String token) {
        switch (token) {
            case "shift": return getContext().getString(R.string.terl_hints_key_shift);
            case "enter": return getContext().getString(R.string.terl_hints_key_enter);
            case "space": return getContext().getString(R.string.terl_hints_key_space);
            case "tab": return getContext().getString(R.string.terl_hints_key_tab);
            case "left": return getContext().getString(R.string.terl_hints_key_left);
            case "right": return getContext().getString(R.string.terl_hints_key_right);
            case "up": return getContext().getString(R.string.terl_hints_key_up);
            case "down": return getContext().getString(R.string.terl_hints_key_down);
            case "escape": return getContext().getString(R.string.terl_hints_key_escape);
            case "backspace": return getContext().getString(R.string.terl_hints_key_backspace);
            case ",": return getContext().getString(R.string.terl_hints_key_comma);
            case "pageup": return getContext().getString(R.string.terl_hints_key_page_up);
            case "pagedown": return getContext().getString(R.string.terl_hints_key_page_down);
            case "home": return getContext().getString(R.string.terl_hints_key_home);
            case "end": return getContext().getString(R.string.terl_hints_key_end);
            case "[": return getContext().getString(R.string.terl_hints_key_left_bracket);
            case "]": return getContext().getString(R.string.terl_hints_key_right_bracket);
            case "minus": return getContext().getString(R.string.terl_hints_key_minus);
            case "equals": return getContext().getString(R.string.terl_hints_key_equals);
            default: return null;
        }
    }

    @StringRes
    static int familyWords(@NonNull TerlHintCard.Family family) {
        switch (family) {
            case TERMINAL: return R.string.terl_hints_terminal;
            case NEW_LEFT: return R.string.terl_hints_new_left;
            case CLOSE: return R.string.terl_hints_close;
            case POP_OUT: return R.string.terl_hints_pop_out;
            case FOCUS: return R.string.terl_hints_focus;
            case FOCUS_ENDS: return R.string.terl_hints_focus_ends;
            case CENTRE: return R.string.terl_hints_centre;
            case MOVE: return R.string.terl_hints_move;
            case CONSUME: return R.string.terl_hints_consume;
            case SPLIT: return R.string.terl_hints_split;
            case SPLIT_OTHER: return R.string.terl_hints_split_other;
            case WIDTH_NEXT: return R.string.terl_hints_width_next;
            case WIDTH_PREVIOUS: return R.string.terl_hints_width_previous;
            case WIDTH_NUDGE: return R.string.terl_hints_width_nudge;
            case HEIGHT_NUDGE: return R.string.terl_hints_height_nudge;
            case FULL_WIDTH: return R.string.terl_hints_full_width;
            case GO: return R.string.terl_hints_go;
            case SEND: return R.string.terl_hints_send;
            case WORKSPACE_STEP: return R.string.terl_hints_workspace_step;
            case LAYOUT: return R.string.terl_hints_layout;
            case FLOAT: return R.string.terl_hints_float;
            case FULL_SCREEN: return R.string.terl_hints_full_screen;
            case TAB: return R.string.terl_hints_tab;
            case OVERVIEW: return R.string.terl_hints_overview;
            case SEARCH: return R.string.terl_hints_search;
            default: return R.string.terl_hints_edit_mode;
        }
    }

    @StringRes
    static int groupTitle(@NonNull TerlHintCard.Group group) {
        switch (group) {
            case OPEN_CLOSE: return R.string.terl_hints_group_open_close;
            case FOCUS: return R.string.terl_hints_group_focus;
            case MOVE: return R.string.terl_hints_group_move;
            case RESIZE_SPLIT: return R.string.terl_hints_group_resize_split;
            case WORKSPACES: return R.string.terl_hints_group_workspaces;
            case LAYOUT: return R.string.terl_hints_group_layout;
            default: return R.string.terl_hints_group_tools;
        }
    }

    @NonNull
    private TextView text(@NonNull CharSequence words, int ink, float sp, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(words);
        view.setTextColor(ink);
        view.setTextSize(sp);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return view;
    }

    private void dress() {
        if (mScroll.getWidth() <= 0 || mScroll.getHeight() <= 0 || getParent() == null) return;
        float radius = mHost.cornerRadiusPx();
        Drawable glass = mHost.glass(radius, mScroll);
        if (glass == null) {
            GradientDrawable plain = new GradientDrawable();
            plain.setCornerRadius(radius);
            plain.setColor(fallbackFill(mHost.ink()));
            glass = plain;
        }
        mScroll.setBackground(glass);
        mScroll.setClipToOutline(true);
        mScroll.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mScroll.post(this::dress);
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }

    /** The card's rows, for tests: the header, then each section's title and rows, in reading order. */
    @NonNull
    java.util.List<String> rowTexts() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (View view : mReading) {
            if (view instanceof TextView) {
                out.add(((TextView) view).getText().toString());
                continue;
            }
            ViewGroup cell = (ViewGroup) view;
            StringBuilder text = new StringBuilder();
            for (int k = 0; k < cell.getChildCount(); k++) {
                if (text.length() > 0) text.append(' ');
                text.append(((TextView) cell.getChildAt(k)).getText());
            }
            out.add(text.toString());
        }
        return out;
    }

    /** The first cap whose face is {@code face}; null when none. */
    @Nullable
    private TextView capWithFace(@NonNull String face) {
        for (View view : mReading) {
            if (!(view instanceof ViewGroup)) continue;
            ViewGroup cell = (ViewGroup) view;
            for (int k = 0; k < cell.getChildCount() - 1; k++) {
                TextView cap = (TextView) cell.getChildAt(k);
                if (face.contentEquals(cap.getText())) return cap;
            }
        }
        return null;
    }

    /** The spoken label of the first cap whose face is {@code face}, for tests; null when none. */
    @Nullable
    CharSequence capDescription(@NonNull String face) {
        TextView cap = capWithFace(face);
        return cap == null ? null : cap.getContentDescription();
    }

    /** Taps the first cap whose face is {@code face}, for tests; false when there is none. */
    boolean tapCap(@NonNull String face) {
        TextView cap = capWithFace(face);
        return cap != null && cap.performClick();
    }

    /** The card's panel, its height capped to the room between the status bar and the bar. */
    private static final class CardScroll extends ScrollView {
        int mMaxHeight = Integer.MAX_VALUE;

        CardScroll(@NonNull Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int capped = MeasureSpec.makeMeasureSpec(mMaxHeight, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, capped);
        }
    }
}
