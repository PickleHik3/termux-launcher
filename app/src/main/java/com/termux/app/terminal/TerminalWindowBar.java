package com.termux.app.terminal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One compact app-owned row for the current tmux-style window list. */
public final class TerminalWindowBar extends HorizontalScrollView {

    /** Shared with the terminal surface so both pieces of the window switch settle together. */
    public static final long WINDOW_SWITCH_ANIMATION_DURATION_MS = 320L;

    public interface OnWindowSelectedListener {
        void onWindowSelected(int index);
    }

    public interface OnCreateWindowListener {
        void onCreateWindow();
    }

    /** Visual label plus a spoken label that does not expose Nerd Font private-use glyphs. */
    public static final class WindowItem {
        @NonNull public final String label;
        @NonNull public final String spokenLabel;

        public WindowItem(@NonNull String label, @NonNull String spokenLabel) {
            this.label = label;
            this.spokenLabel = spokenLabel;
        }
    }

    private final SelectionStrip mTabs;
    @Nullable private OnWindowSelectedListener mSelectionListener;
    @Nullable private OnCreateWindowListener mCreateListener;
    private int mSelectedIndex = -1;
    @NonNull private List<WindowItem> mItems = new ArrayList<>();
    private Typeface mTerminalTypeface = Typeface.MONOSPACE;
    private long mTerminalTypefaceModified;
    private boolean mCapsuleSurface;
    private float mStatusBarRadiusPx;
    private int mSelectedTextColor;
    private int mUnselectedTextColor;
    private int mUnselectedFillColor;
    private int mUnselectedStrokeColor;
    private int mSelectedFillColor;
    private int mSelectedStrokeColor;
    @Nullable private ValueAnimator mSelectionAnimator;

    public TerminalWindowBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setHorizontalScrollBarEnabled(false);
        setFillViewport(false);
        setClipToPadding(true);
        setClipChildren(true);
        setOverScrollMode(OVER_SCROLL_NEVER);
        // The parent already supplies the intended gap after the session indicator. A second
        // leading inset here made that gap look like trailing padding owned by the session chip.
        setPaddingRelative(0, dp(2), dp(5), dp(2));
        mTabs = new SelectionStrip(context);
        mTabs.setGravity(Gravity.CENTER_VERTICAL);
        mTabs.setOrientation(LinearLayout.HORIZONTAL);
        mTabs.setClipChildren(true);
        mTabs.setClipToPadding(true);
        addView(mTabs, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        reloadTerminalTypeface();
        updatePalette();
    }

    public void setOnWindowSelectedListener(@Nullable OnWindowSelectedListener listener) {
        mSelectionListener = listener;
    }

    public void setOnCreateWindowListener(@Nullable OnCreateWindowListener listener) {
        mCreateListener = listener;
    }

    /** Keep window containers square in the default bar and capsule-rounded in Rounded. */
    public void setSurfaceStyle(boolean capsule, float statusBarRadiusPx) {
        float radius = Math.max(0f, statusBarRadiusPx);
        if (mCapsuleSurface == capsule && mStatusBarRadiusPx == radius) return;
        mCapsuleSurface = capsule;
        mStatusBarRadiusPx = radius;
        updatePalette();
        applyTabSurfaceStyle();
    }

    public void setWindows(@NonNull List<WindowItem> items, int selectedIndex) {
        boolean typefaceChanged = reloadTerminalTypeface();
        if (!typefaceChanged && selectedIndex == mSelectedIndex && sameItems(mItems, items)) return;
        int previousSelected = mSelectedIndex;
        boolean canReuseTabs = !typefaceChanged && sameItems(mItems, items)
            && mTabs.getChildCount() == items.size() + 1;
        mSelectedIndex = selectedIndex;
        mItems = new ArrayList<>(items);
        updatePalette();
        if (canReuseTabs) {
            if (previousSelected >= 0 && previousSelected != selectedIndex) {
                animateSelectionSlide(previousSelected, selectedIndex);
            } else {
                cancelSelectionAnimation();
                mTabs.snapSelection(selectedIndex);
                applyStableTabSelection();
            }
            scrollSelectedIntoView(selectedIndex);
            return;
        }

        cancelSelectionAnimation();
        mTabs.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            WindowItem item = items.get(i);
            boolean selected = i == selectedIndex;
            TextView tab = createTab(item.label, selected);
            tab.setContentDescription(getResources().getString(
                R.string.termux_window_tab_content_description, i + 1, items.size(), item.spokenLabel));
            tab.setOnClickListener(v -> {
                if (mSelectionListener != null) mSelectionListener.onWindowSelected(index);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
            if (i > 0) params.setMarginStart(dp(3));
            mTabs.addView(tab, params);
        }
        addCreateButton(items.isEmpty());
        mTabs.setWindowCount(items.size());
        mTabs.snapSelection(selectedIndex);
        applyStableTabSelection();
        scrollSelectedIntoView(selectedIndex);
    }

    private TextView createTab(String label, boolean selected) {
        Context context = getContext();
        TextView tab = new TextView(context);
        tab.setGravity(Gravity.CENTER);
        tab.setMinWidth(0);
        tab.setMaxWidth(dp(104));
        // A half-dp on each side is visible at modern phone densities without making the compact
        // window row feel loose.
        tab.setPadding(dp(3.5f), 0, dp(3.5f), 0);
        tab.setSingleLine(true);
        tab.setIncludeFontPadding(false);
        tab.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        tab.setEllipsize(TextUtils.TruncateAt.END);
        tab.setText(label);
        tab.setTextColor(selected ? mSelectedTextColor : mUnselectedTextColor);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tab.setTypeface(mTerminalTypeface, selected ? Typeface.BOLD : Typeface.NORMAL);
        tab.setBackground(buildUnselectedChip());
        tab.setSelected(selected);
        tab.setFocusable(true);
        return tab;
    }

    private void addCreateButton(boolean firstItem) {
        Context context = getContext();
        int tertiary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary,
            ContextCompat.getColor(context, R.color.termux_primary));
        AppCompatImageButton add = new AppCompatImageButton(context);
        add.setImageResource(R.drawable.ic_status_bar_add_window);
        ImageViewCompat.setImageTintList(add, ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(tertiary, 184)));
        add.setScaleType(ImageView.ScaleType.CENTER);
        add.setBackground(null);
        add.setPadding(0, 0, 0, 0);
        add.setContentDescription(getResources().getString(R.string.termux_window_new_content_description));
        add.setOnClickListener(v -> {
            if (mCreateListener != null) mCreateListener.onCreateWindow();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(24), LayoutParams.MATCH_PARENT);
        if (!firstItem) params.setMarginStart(dp(3));
        mTabs.addView(add, params);
    }

    private void animateSelectionSlide(int previousSelected, int selectedIndex) {
        if (previousSelected < 0 || previousSelected >= mItems.size()
            || selectedIndex < 0 || selectedIndex >= mItems.size()) {
            mTabs.snapSelection(selectedIndex);
            applyStableTabSelection();
            return;
        }
        RectF start = new RectF();
        RectF end = new RectF();
        if (!mTabs.copyCurrentHighlightBounds(start)
            || !mTabs.copyChildBounds(selectedIndex, end)) {
            mTabs.snapSelection(selectedIndex);
            applyStableTabSelection();
            return;
        }

        cancelSelectionAnimation();
        applyAnimatedTabSelection(previousSelected, selectedIndex, 0f);
        mTabs.setAnimatedHighlight(start);
        mSelectionAnimator = ValueAnimator.ofFloat(0f, 1f);
        mSelectionAnimator.setDuration(WINDOW_SWITCH_ANIMATION_DURATION_MS);
        mSelectionAnimator.setInterpolator(settleInterpolator());
        mSelectionAnimator.addUpdateListener(animation -> {
            float progress = (Float) animation.getAnimatedValue();
            mTabs.setAnimatedHighlight(lerp(start, end, progress));
            applyAnimatedTabSelection(previousSelected, selectedIndex, progress);
        });
        mSelectionAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                if (mSelectionAnimator == animation) mSelectionAnimator = null;
                if (mCancelled) return;
                mTabs.snapSelection(selectedIndex);
                applyStableTabSelection();
            }
        });
        mSelectionAnimator.start();
    }

    private void scrollSelectedIntoView(int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= mTabs.getChildCount()) return;
        post(() -> {
            View selected = mTabs.getChildAt(selectedIndex);
            int target = Math.max(0, selected.getLeft() - dp(5));
            if (target == getScrollX()) return;
            animateScrollTo(target);
        });
    }

    private void animateScrollTo(int target) {
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(getScrollX(), target);
        animator.setDuration(WINDOW_SWITCH_ANIMATION_DURATION_MS);
        animator.setInterpolator(settleInterpolator());
        animator.addUpdateListener(value -> scrollTo((Integer) value.getAnimatedValue(), 0));
        animator.start();
    }

    private Interpolator settleInterpolator() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            ? new PathInterpolator(0.16f, 1f, 0.3f, 1f)
            : new DecelerateInterpolator(1.8f);
    }

    private void cancelSelectionAnimation() {
        if (mSelectionAnimator == null) return;
        mSelectionAnimator.cancel();
        mSelectionAnimator = null;
    }

    private void applyStableTabSelection() {
        for (int i = 0; i < mItems.size() && i < mTabs.getChildCount(); i++) {
            TextView tab = (TextView) mTabs.getChildAt(i);
            boolean selected = i == mSelectedIndex;
            tab.setSelected(selected);
            tab.setTextColor(selected ? mSelectedTextColor : mUnselectedTextColor);
            tab.setTypeface(mTerminalTypeface, selected ? Typeface.BOLD : Typeface.NORMAL);
            tab.setAlpha(1f);
            tab.setTranslationX(0f);
        }
    }

    private void applyAnimatedTabSelection(int previousSelected, int selectedIndex, float progress) {
        for (int i = 0; i < mItems.size() && i < mTabs.getChildCount(); i++) {
            TextView tab = (TextView) mTabs.getChildAt(i);
            tab.setSelected(i == selectedIndex);
            if (i == previousSelected) {
                tab.setTextColor(ColorUtils.blendARGB(
                    mSelectedTextColor, mUnselectedTextColor, progress));
                tab.setTypeface(mTerminalTypeface, Typeface.BOLD);
            } else if (i == selectedIndex) {
                tab.setTextColor(ColorUtils.blendARGB(
                    mUnselectedTextColor, mSelectedTextColor, progress));
                tab.setTypeface(mTerminalTypeface, Typeface.BOLD);
            } else {
                tab.setTextColor(mUnselectedTextColor);
                tab.setTypeface(mTerminalTypeface, Typeface.NORMAL);
            }
            tab.setAlpha(1f);
            tab.setTranslationX(0f);
        }
    }

    private void updatePalette() {
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int onSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        int onSurfaceVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(context, R.color.termux_on_surface_variant));
        int secondary = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        mSelectedTextColor = onSurface;
        mUnselectedTextColor = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(onSurfaceVariant, secondary, .18f), 148);
        mUnselectedFillColor = ColorUtils.setAlphaComponent(secondary, 16);
        mUnselectedStrokeColor = ColorUtils.setAlphaComponent(secondary, 34);
        mSelectedFillColor = ColorUtils.setAlphaComponent(primary, 58);
        mSelectedStrokeColor = ColorUtils.setAlphaComponent(primary, 112);
        mTabs.setHighlightStyle(mSelectedFillColor, mSelectedStrokeColor,
            mCapsuleSurface ? mStatusBarRadiusPx : 0f, dp(1));
    }

    private void applyTabSurfaceStyle() {
        for (int i = 0; i < mItems.size() && i < mTabs.getChildCount(); i++) {
            mTabs.getChildAt(i).setBackground(buildUnselectedChip());
        }
        applyStableTabSelection();
        mTabs.invalidate();
    }

    private GradientDrawable buildUnselectedChip() {
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(mCapsuleSurface ? mStatusBarRadiusPx : 0f);
        chip.setColor(mUnselectedFillColor);
        chip.setStroke(dp(1), mUnselectedStrokeColor);
        return chip;
    }

    private static RectF lerp(@NonNull RectF start, @NonNull RectF end, float progress) {
        return new RectF(
            start.left + (end.left - start.left) * progress,
            start.top + (end.top - start.top) * progress,
            start.right + (end.right - start.right) * progress,
            start.bottom + (end.bottom - start.bottom) * progress);
    }

    /** Draws one selected surface beneath the pills so it can travel without moving their labels. */
    private static final class SelectionStrip extends LinearLayout {

        private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mAnimatedHighlight = new RectF();
        private int mWindowCount;
        private int mSelection = -1;
        private boolean mHasAnimatedHighlight;
        private float mCornerRadius;

        SelectionStrip(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);
            mFillPaint.setStyle(Paint.Style.FILL);
            mStrokePaint.setStyle(Paint.Style.STROKE);
        }

        void setWindowCount(int windowCount) {
            mWindowCount = Math.max(0, windowCount);
        }

        void setHighlightStyle(int fillColor, int strokeColor, float cornerRadius,
                               float strokeWidth) {
            mFillPaint.setColor(fillColor);
            mStrokePaint.setColor(strokeColor);
            mStrokePaint.setStrokeWidth(strokeWidth);
            mCornerRadius = Math.max(0f, cornerRadius);
            invalidate();
        }

        void snapSelection(int selection) {
            mSelection = selection;
            mHasAnimatedHighlight = false;
            invalidate();
        }

        void setAnimatedHighlight(@NonNull RectF bounds) {
            mAnimatedHighlight.set(bounds);
            mHasAnimatedHighlight = true;
            invalidate();
        }

        boolean copyCurrentHighlightBounds(@NonNull RectF output) {
            if (mHasAnimatedHighlight && !mAnimatedHighlight.isEmpty()) {
                output.set(mAnimatedHighlight);
                return true;
            }
            return copyChildBounds(mSelection, output);
        }

        boolean copyChildBounds(int index, @NonNull RectF output) {
            if (index < 0 || index >= mWindowCount || index >= getChildCount()) return false;
            View child = getChildAt(index);
            if (child.getWidth() <= 0 || child.getHeight() <= 0) return false;
            output.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
            return true;
        }

        @Override protected void dispatchDraw(@NonNull Canvas canvas) {
            RectF bounds = new RectF();
            boolean hasBounds;
            if (mHasAnimatedHighlight) {
                bounds.set(mAnimatedHighlight);
                hasBounds = !bounds.isEmpty();
            } else {
                hasBounds = copyChildBounds(mSelection, bounds);
            }
            if (hasBounds) {
                float strokeInset = mStrokePaint.getStrokeWidth() / 2f;
                bounds.inset(strokeInset, strokeInset);
                canvas.drawRoundRect(bounds, mCornerRadius, mCornerRadius, mFillPaint);
                canvas.drawRoundRect(bounds, mCornerRadius, mCornerRadius, mStrokePaint);
            }
            super.dispatchDraw(canvas);
        }
    }

    private boolean reloadTerminalTypeface() {
        File file = TermuxConstants.TERMUX_FONT_FILE;
        long modified = file.isFile() ? file.lastModified() : 0L;
        if (modified == mTerminalTypefaceModified && mTerminalTypeface != null) return false;
        try {
            mTerminalTypeface = modified > 0L ? Typeface.createFromFile(file) : Typeface.MONOSPACE;
            mTerminalTypefaceModified = modified;
        } catch (Exception ignored) {
            mTerminalTypeface = Typeface.MONOSPACE;
            mTerminalTypefaceModified = 0L;
        }
        return true;
    }

    private static boolean sameItems(@NonNull List<WindowItem> left,
                                     @NonNull List<WindowItem> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            WindowItem a = left.get(i);
            WindowItem b = right.get(i);
            if (!a.label.equals(b.label) || !a.spokenLabel.equals(b.spokenLabel)) return false;
        }
        return true;
    }

    /** Process is represented only by a Nerd Font glyph; visible text is the compact directory. */
    @NonNull
    public static WindowItem itemFor(@Nullable TerminalSession session, int index) {
        if (session == null) {
            String fallback = "window " + (index + 1);
            return new WindowItem(glyph(0xE795) + " " + (index + 1), fallback);
        }
        String process = processName(session.getTitle());
        String directory = directoryName(session.getCwd(), session.getTitle());
        String spokenProcess = process == null ? "terminal" : process;
        return new WindowItem(processGlyph(process) + " " + directory,
            spokenProcess + " in " + directory);
    }

    /**
     * Build an item from foreground detection: the glyph reflects {@code processName}, while the
     * visible text is a pre-truncated basename (open file, process name, or directory). Callers own
     * the label priority; this only maps the glyph and pairs it with the spoken label.
     */
    @NonNull
    public static WindowItem itemForResolved(@Nullable String processName,
                                             @NonNull String displayText,
                                             @NonNull String spokenLabel) {
        return new WindowItem(processGlyph(processName) + " " + displayText, spokenLabel);
    }

    /** Terminal editors whose foreground presence means "show the open file, not the process". */
    public static boolean isEditor(@Nullable String process) {
        if (process == null) return false;
        switch (process.toLowerCase(Locale.ROOT)) {
            case "vim":
            case "vi":
            case "nvim":
            case "neovim":
            case "nano":
            case "emacs":
            case "emacsclient":
            case "hx":
            case "helix":
            case "micro":
            case "kak":
            case "kakoune":
            case "ne":
            case "joe":
            case "vis":
            case "ed":
                return true;
            default:
                return false;
        }
    }

    /** Directory basename for a foreground-derived cwd, middle-truncated like the idle label. */
    @NonNull
    public static String directoryLabel(@Nullable String cwd) {
        return directoryName(cwd, null);
    }

    @NonNull
    public static String truncateProcess(@NonNull String process) {
        return middleEllipsize(process, 12);
    }

    /**
     * Middle-truncate a filename while preserving its extension where possible, e.g.
     * {@code terminal…java}. Falls back to plain middle truncation when the extension is too long
     * to keep.
     */
    @NonNull
    public static String truncateFile(@NonNull String name) {
        int maxChars = 13;
        String cleaned = name;
        if (cleaned.length() <= maxChars) return cleaned;
        int dot = cleaned.lastIndexOf('.');
        if (dot <= 0 || dot == cleaned.length() - 1) return middleEllipsize(cleaned, maxChars);
        String base = cleaned.substring(0, dot);
        String ext = cleaned.substring(dot + 1);
        // Only worth preserving when the extension still leaves room for a meaningful head.
        if (ext.length() > maxChars - 3) return middleEllipsize(cleaned, maxChars);
        int head = maxChars - ext.length() - 1;
        if (head < 1) return middleEllipsize(cleaned, maxChars);
        return base.substring(0, head) + "…" + ext;
    }

    @NonNull
    private static String processGlyph(@Nullable String process) {
        if (process == null) return glyph(0xE795);               // dev-terminal
        switch (process) {
            case "fish": return glyph(0xF023A);                // md-fish
            case "pacman": return glyph(0xF0BAF);              // md-pac-man
            case "ssh": return glyph(0xF08C0);                 // md-ssh
            case "tmux": return glyph(0xEBC8);                 // cod-terminal-tmux
            case "bash":
            case "sh":
            case "zsh": return glyph(0xF1183);                 // md-bash
            case "python":
            case "python3": return glyph(0xE73C);              // dev-python
            case "node":
            case "nodejs": return glyph(0xE719);               // dev-nodejs
            case "docker": return glyph(0xE7B0);               // dev-docker
            case "vim":
            case "vi": return glyph(0xE7C5);                   // dev-vim
            case "nvim":
            case "neovim": return glyph(0xE6AE);               // custom-neovim
            case "nano":
            case "micro":
            case "ne":
            case "joe":
            case "vis":
            case "ed": return glyph(0xF0F6);                   // md-file-document-edit
            case "emacs":
            case "emacsclient": return glyph(0xE632);          // custom-emacs
            case "hx":
            case "helix":
            case "kak":
            case "kakoune": return glyph(0xEB0E);              // cod-edit
            case "git": return glyph(0xE702);                  // dev-git
            default: return glyph(0xE795);                       // dev-terminal
        }
    }

    @Nullable
    private static String processName(@Nullable String title) {
        String cleaned = clean(title);
        if (cleaned == null) return null;
        int inDirectory = cleaned.indexOf(" in <");
        if (inDirectory > 0) cleaned = cleaned.substring(0, inDirectory);
        int separator = cleaned.indexOf(' ');
        if (separator > 0) cleaned = cleaned.substring(0, separator);
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) cleaned = cleaned.substring(slash + 1);
        cleaned = cleaned.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._+-]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    @NonNull
    private static String directoryName(@Nullable String cwd, @Nullable String title) {
        String path = clean(cwd);
        if (path == null) {
            String cleanedTitle = clean(title);
            if (cleanedTitle != null) {
                int open = cleanedTitle.lastIndexOf('<');
                int close = cleanedTitle.lastIndexOf('>');
                if (open >= 0 && close > open) path = cleanedTitle.substring(open + 1, close);
                else if (cleanedTitle.endsWith(" ~")) path = "home";
            }
        }
        if (path == null || path.equals(TermuxConstants.TERMUX_HOME_DIR_PATH) || path.equals("~")) {
            return "home";
        }
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int slash = path.lastIndexOf('/');
        String leaf = slash >= 0 ? path.substring(slash + 1) : path;
        if (leaf.isEmpty()) leaf = "/";
        return middleEllipsize(leaf, 12);
    }

    @NonNull
    static String middleEllipsize(@NonNull String value, int maxChars) {
        if (value.length() <= maxChars || maxChars < 5) return value;
        int tail = Math.max(2, maxChars / 3);
        int head = maxChars - tail - 1;
        return value.substring(0, head) + "…" + value.substring(value.length() - tail);
    }

    @Nullable
    private static String clean(@Nullable String value) {
        if (value == null) return null;
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    @NonNull
    private static String glyph(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
