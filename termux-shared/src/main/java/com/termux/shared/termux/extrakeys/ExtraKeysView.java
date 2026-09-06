package com.termux.shared.termux.extrakeys;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.GridLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.button.MaterialButton;
import com.termux.shared.R;
import com.termux.shared.termux.font.NerdFontSpans;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.shared.theme.ThemeUtils;

/**
 * A {@link View} showing extra keys (such as Escape, Ctrl, Alt) not normally available on an Android soft
 * keyboards.
 *
 * To use it, add following to a layout file and import it in your activity layout file or inflate
 * it with a {@link androidx.viewpager.widget.ViewPager}.:
 * {@code
 * <?xml version="1.0" encoding="utf-8"?>
 * <com.termux.shared.termux.extrakeys.ExtraKeysView xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:id="@+id/extra_keys"
 *     style="?android:attr/buttonBarStyle"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     android:layout_alignParentBottom="true"
 *     android:orientation="horizontal" />
 * }
 *
 * Then in your activity, get its reference by a call to {@link android.app.Activity#findViewById(int)}
 * or {@link LayoutInflater#inflate(int, ViewGroup)} if using {@link androidx.viewpager.widget.ViewPager}.
 * Then call {@link #setExtraKeysViewClient(IExtraKeysView)} and pass it the implementation of
 * {@link IExtraKeysView} so that you can receive callbacks. You can also override other values set
 * in {@link ExtraKeysView#ExtraKeysView(Context, AttributeSet)} by calling the respective functions.
 * If you extend {@link ExtraKeysView}, you can also set them in the constructor, but do call super().
 *
 * After this you will have to make a call to {@link ExtraKeysView#reload(ExtraKeysInfo, float) and pass
 * it the {@link ExtraKeysInfo} to load and display the extra keys. Read its class javadocs for more
 * info on how to create it.
 *
 * Termux app defines the view in res/layout/view_terminal_toolbar_extra_keys and
 * inflates it in TerminalToolbarViewPager.instantiateItem() and sets the {@link ExtraKeysView} client
 * and calls {@link ExtraKeysView#reload(ExtraKeysInfo).
 * The {@link ExtraKeysInfo} is created by TermuxAppSharedProperties.setExtraKeys().
 * Then its got and the view height is adjusted in TermuxActivity.setTerminalToolbarHeight().
 * The client used is TermuxTerminalExtraKeys, which extends
 * {@link TerminalExtraKeys } to handle Termux app specific logic and
 * leave the rest to the super class.
 */
public final class ExtraKeysView extends GridLayout {

    private enum KeyVisualState {
        RESTING,
        PRESSED,
        REPEAT_HELD,
        STICKY_ACTIVE,
        STICKY_LOCKED,
        POPUP_ARMED
    }

    /**
     * The client for the {@link ExtraKeysView}.
     */
    public interface IExtraKeysView {

        /**
         * This is called by {@link ExtraKeysView} when a button is clicked. This is also called
         * for {@link #mRepetitiveKeys} and {@link ExtraKeyButton} that have a popup set.
         * However, this is not called for {@link #mSpecialButtons}, whose state can instead be read
         * via a call to {@link #readSpecialButton(SpecialButton, boolean)}.
         *
         * @param view The view that was clicked.
         * @param buttonInfo The {@link ExtraKeyButton} for the button that was clicked.
         *                   The button may be a {@link ExtraKeyButton#KEY_MACRO} set which can be
         *                   checked with a call to {@link ExtraKeyButton#isMacro()}.
         * @param button The {@link MaterialButton} that was clicked.
         */
        void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button);

        /**
         * This is called by {@link ExtraKeysView} when a button is clicked so that the client
         * can perform any hepatic feedback. This is only called in the {@link MaterialButton.OnClickListener}
         * and not for every repeat. Its also called for {@link #mSpecialButtons}.
         *
         * @param view The view that was clicked.
         * @param buttonInfo The {@link ExtraKeyButton} for the button that was clicked.
         * @param button The {@link MaterialButton} that was clicked.
         * @return Return {@code true} if the client handled the feedback, otherwise {@code false}
         * so that {@link ExtraKeysView#performExtraKeyButtonHapticFeedback(View, ExtraKeyButton, MaterialButton)}
         * can handle it depending on system settings.
         */
        boolean performExtraKeyButtonHapticFeedback(View view, ExtraKeyButton buttonInfo, MaterialButton button);
    }

    /**
     * Defines the default value for {@link #mButtonTextColor} defined by current theme.
     */
    public static final int ATTR_BUTTON_TEXT_COLOR = R.attr.extraKeysButtonTextColor;

    /**
     * Defines the default value for {@link #mButtonActiveTextColor} defined by current theme.
     */
    public static final int ATTR_BUTTON_ACTIVE_TEXT_COLOR = R.attr.extraKeysButtonActiveTextColor;

    /**
     * Defines the default value for {@link #mButtonBackgroundColor} defined by current theme.
     */
    public static final int ATTR_BUTTON_BACKGROUND_COLOR = R.attr.extraKeysButtonBackgroundColor;

    /**
     * Defines the default value for {@link #mButtonActiveBackgroundColor} defined by current theme.
     */
    public static final int ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR = R.attr.extraKeysButtonActiveBackgroundColor;

    /**
     * Defines the default fallback value for {@link #mButtonTextColor} if {@link #ATTR_BUTTON_TEXT_COLOR} is undefined.
     */
    public static final int DEFAULT_BUTTON_TEXT_COLOR = 0xFFFFFFFF;

    /**
     * Defines the default fallback value for {@link #mButtonActiveTextColor} if {@link #ATTR_BUTTON_ACTIVE_TEXT_COLOR} is undefined.
     */
    public static final int DEFAULT_BUTTON_ACTIVE_TEXT_COLOR = 0xFF80DEEA;

    /**
     * Defines the default fallback value for {@link #mButtonBackgroundColor} if {@link #ATTR_BUTTON_BACKGROUND_COLOR} is undefined.
     */
    public static final int DEFAULT_BUTTON_BACKGROUND_COLOR = 0x00000000;

    /**
     * Defines the default fallback value for {@link #mButtonActiveBackgroundColor} if {@link #ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR} is undefined.
     */
    public static final int DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR = 0xFF7F7F7F;

    /**
     * Defines the minimum allowed duration in milliseconds for {@link #mLongPressTimeout}.
     */
    public static final int MIN_LONG_PRESS_DURATION = 200;

    /**
     * Defines the maximum allowed duration in milliseconds for {@link #mLongPressTimeout}.
     */
    public static final int MAX_LONG_PRESS_DURATION = 3000;

    /**
     * Defines the fallback duration in milliseconds for {@link #mLongPressTimeout}.
     */
    public static final int FALLBACK_LONG_PRESS_DURATION = 400;

    /**
     * Defines the minimum allowed duration in milliseconds for {@link #mLongPressRepeatDelay}.
     */
    public static final int MIN_LONG_PRESS__REPEAT_DELAY = 5;

    /**
     * Defines the maximum allowed duration in milliseconds for {@link #mLongPressRepeatDelay}.
     */
    public static final int MAX_LONG_PRESS__REPEAT_DELAY = 2000;

    /**
     * Defines the default duration in milliseconds for {@link #mLongPressRepeatDelay}.
     */
    public static final int DEFAULT_LONG_PRESS_REPEAT_DELAY = 80;

    /**
     * The implementation of the {@link IExtraKeysView} that acts as a client for the {@link ExtraKeysView}.
     */
    protected IExtraKeysView mExtraKeysViewClient;

    /** Fallback used when a child button receives a left swipe before the toolbar pager can. */
    @Nullable private Runnable mToolbarTextInputSwipeListener;

    /**
     * The map for the {@link SpecialButton} and their {@link SpecialButtonState}. Defaults to
     * the one returned by {@link #getDefaultSpecialButtons(ExtraKeysView)}.
     */
    protected Map<SpecialButton, SpecialButtonState> mSpecialButtons;

    /**
     * The keys for the {@link SpecialButton} added to {@link #mSpecialButtons}. This is automatically
     * set when the call to {@link #setSpecialButtons(Map)} is made.
     */
    protected Set<String> mSpecialButtonsKeys;

    /**
     * The list of keys for which auto repeat of key should be triggered if its extra keys button
     * is long pressed. This is done by calling {@link IExtraKeysView#onExtraKeyButtonClick(View, ExtraKeyButton, MaterialButton)}
     * every {@link #mLongPressRepeatDelay} seconds after {@link #mLongPressTimeout} has passed.
     * The default keys are defined by {@link ExtraKeysConstants#PRIMARY_REPETITIVE_KEYS}.
     */
    protected List<String> mRepetitiveKeys;

    /**
     * The text color for the extra keys button. Defaults to {@link #DEFAULT_BUTTON_TEXT_COLOR}.
     */
    protected int mButtonTextColor;

    /**
     * The text color for the extra keys button when its active.
     * Defaults to {@link #DEFAULT_BUTTON_ACTIVE_TEXT_COLOR}.
     */
    protected int mButtonActiveTextColor;

    /**
     * The background color for the extra keys button. Defaults to {@link #DEFAULT_BUTTON_BACKGROUND_COLOR}.
     */
    protected int mButtonBackgroundColor;

    /**
     * The background color for the extra keys button when its active. Defaults to
     * {@link #DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR}.
     */
    protected int mButtonActiveBackgroundColor;

    /**
     * Optional accent used to tint the press "glow pill". When 0 (unset) the press feedback falls
     * back to {@link #mButtonActiveBackgroundColor}. The launcher dock sets this to its accent so
     * the key feedback matches the dock's rim glow.
     */
    protected int mKeyPressFeedbackColor = 0;

    /**
     * Whether a blur backdrop is active behind the keys. When true the press feedback is a feathered
     * radial wash (it relies on the blur for softness); when false it uses a more present rounded
     * fill. The launcher dock sets this from its blur preference.
     */
    protected boolean mKeyPressFeedbackBlurAvailable = true;

    /** True for the floating capsule dock (vertical popup pill); false for the edge-to-edge dock. */
    protected boolean mPopupVerticalPill = false;

    /**
     * Defines whether text for the extra keys button should be all capitalized automatically.
     */
    protected boolean mButtonTextAllCaps = true;

    /**
     * Defines the duration in milliseconds before a press turns into a long press. The default
     * duration used is the one returned by a call to {@link ViewConfiguration#getLongPressTimeout()}
     * which will return the system defined duration which can be changed in accessibility settings.
     * The duration must be in between {@link #MIN_LONG_PRESS_DURATION} and {@link #MAX_LONG_PRESS_DURATION},
     * otherwise {@link #FALLBACK_LONG_PRESS_DURATION} is used.
     */
    protected int mLongPressTimeout;

    /**
     * Defines the duration in milliseconds for the delay between trigger of each repeat of
     * {@link #mRepetitiveKeys}. The default value is defined by {@link #DEFAULT_LONG_PRESS_REPEAT_DELAY}.
     * The duration must be in between {@link #MIN_LONG_PRESS__REPEAT_DELAY} and
     * {@link #MAX_LONG_PRESS__REPEAT_DELAY}, otherwise {@link #DEFAULT_LONG_PRESS_REPEAT_DELAY} is used.
     */
    protected int mLongPressRepeatDelay;

    protected ScheduledExecutorService mScheduledExecutor;

    protected Handler mHandler;

    protected SpecialButtonsLongHoldRunnable mSpecialButtonsLongHoldRunnable;

    protected int mLongPressCount;

    protected boolean mAccessibilityEnabled;

    /**
     * Press feedback is a per-glyph glow: a coloured {@link android.graphics.Paint#setShadowLayer
     * shadow} behind the key's text so each character gets its own material-colour halo. A tap
     * pulses it once; a press-and-hold keeps it lit until release; a latched modifier (SHIFT/CTRL/
     * ALT active or locked) keeps it lit while latched (driven by the sticky visual state).
     * {@link #mKeyGlowButton} is the key currently glowing from a press gesture.
     */
    // Two glow tiers so a quick tap and a sustained press-and-hold read differently: a tap pulses a
    // tighter halo, a hold escalates to a wider, whiter, breathing halo. KEY_GLOW_RADIUS_DP is kept
    // as the tap tier alias for existing callers.
    private static final float KEY_GLOW_RADIUS_TAP_DP = 11f;
    private static final float KEY_GLOW_RADIUS_HOLD_DP = 17f;
    private static final float KEY_GLOW_RADIUS_DP = KEY_GLOW_RADIUS_TAP_DP;
    private static final float KEY_GLOW_WHITE_MIX_TAP = 0.22f;
    private static final float KEY_GLOW_WHITE_MIX_HOLD = 0.45f;
    @Nullable private Animator mKeyGlowAnimator;
    @Nullable private MaterialButton mKeyGlowButton;
    /** Page dots: index and count of the toolbar's key pages, 0/0 while there is only one page. */
    private int mPageIndex;
    private int mPageCount;
    @Nullable private Paint mPageDotPaint;
    /** Swipe-up popup travel is active (finger is dragging the popup toward the secondary slot). */
    private boolean mBubbleArmed;
    /** The press has crossed into the persistent long-press/held state. */
    private boolean mBubbleHeld;
    /** Vertical distance (px, positive) from the key to the secondary "popup" slot above it. */
    private float mBubbleTravelDistPx;
    /** 0 (on key) .. 1 (fully at the secondary slot) — last computed swipe-up progress. */
    private float mBubbleFrac;

    /**
     * Swipe-up travel uses a {@link PopupWindow} (a separate window) rather than this view's overlay,
     * because the extra-keys ViewPager hard-clips its bounds (to hide the adjacent page) and would
     * otherwise crop a bubble travelling above the row. The popup content is the same liquid-glass
     * cap; it is repositioned each frame to glide up with the finger, carrying the secondary glyph.
     */
    @Nullable protected PopupWindow mTravelPopup;
    @Nullable private TextView mTravelBubble;
    @Nullable private GradientDrawable mTravelBubbleBg;
    private int mTravelKeyScreenX, mTravelKeyScreenY, mTravelKeyW, mTravelKeyH;
    /** Bubble (popup) dimensions — larger than the key so the secondary glyph reads prominently. */
    private int mBubbleW, mBubbleH;
    @Nullable private CharSequence mTravelSourceText, mTravelSecondaryText;
    private boolean mTravelShowingSecondary;

    /**
     * Retained (dormant) hook from the earlier glass-refraction-lens experiment. The host may set a
     * listener, but the glyph-glow feedback never fires it, so no refraction is driven. Kept so the
     * host wiring still compiles and the idea can be revisited.
     */
    public interface KeyLensListener {
        void onKeyLensShow(float screenLeft, float screenTop, float screenRight, float screenBottom);
        void onKeyLensHide();
    }
    @Nullable private KeyLensListener mKeyLensListener;

    public void setKeyLensListener(@Nullable KeyLensListener listener) {
        mKeyLensListener = listener;
    }

    /** Generic long-press hold-visual for keys that don't auto-repeat or toggle (so EVERY key shows
     *  a press-hold indication, not just repetitive/special ones). */
    @Nullable private Runnable mGenericHoldVisualRunnable;

    public ExtraKeysView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // The hold bloom lives in this view's overlay and must be allowed to draw past the
        // GridLayout content box; the dock's ancestor capsule clip still contains it.
        setClipChildren(false);
        setClipToPadding(false);
        setRepetitiveKeys(ExtraKeysConstants.PRIMARY_REPETITIVE_KEYS);
        setSpecialButtons(getDefaultSpecialButtons(this));
        setButtonColors(ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_TEXT_COLOR, DEFAULT_BUTTON_TEXT_COLOR), ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_TEXT_COLOR, DEFAULT_BUTTON_ACTIVE_TEXT_COLOR), ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_BACKGROUND_COLOR, DEFAULT_BUTTON_BACKGROUND_COLOR), ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR, DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR));
        setLongPressTimeout(ViewConfiguration.getLongPressTimeout());
        setLongPressRepeatDelay(DEFAULT_LONG_PRESS_REPEAT_DELAY);

        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        mAccessibilityEnabled = am.isEnabled();
    }

    /**
     * Get {@link #mExtraKeysViewClient}.
     */
    public IExtraKeysView getExtraKeysViewClient() {
        return mExtraKeysViewClient;
    }

    public void setToolbarTextInputSwipeListener(@Nullable Runnable listener) {
        mToolbarTextInputSwipeListener = listener;
    }

    /** True when the keys stand in a column on a screen edge instead of lying in a row. */
    private boolean mVertical;

    /**
     * Lay the keys out as a column: each configured row becomes a column and the keys in it run
     * top to bottom. Takes effect on the next {@link #reload}.
     */
    public void setVertical(boolean vertical) {
        mVertical = vertical;
    }

    public boolean isVertical() {
        return mVertical;
    }

    /**
     * Set {@link #mExtraKeysViewClient}.
     */
    public void setExtraKeysViewClient(IExtraKeysView extraKeysViewClient) {
        mExtraKeysViewClient = extraKeysViewClient;
    }

    /**
     * Get {@link #mRepetitiveKeys}.
     */
    public List<String> getRepetitiveKeys() {
        if (mRepetitiveKeys == null)
            return null;
        return mRepetitiveKeys.stream().map(String::new).collect(Collectors.toList());
    }

    /**
     * Set {@link #mRepetitiveKeys}. Must not be {@code null}.
     */
    public void setRepetitiveKeys(@NonNull List<String> repetitiveKeys) {
        mRepetitiveKeys = repetitiveKeys;
    }

    /**
     * Get {@link #mSpecialButtons}.
     */
    public Map<SpecialButton, SpecialButtonState> getSpecialButtons() {
        if (mSpecialButtons == null)
            return null;
        return mSpecialButtons.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Get {@link #mSpecialButtonsKeys}.
     */
    public Set<String> getSpecialButtonsKeys() {
        if (mSpecialButtonsKeys == null)
            return null;
        return mSpecialButtonsKeys.stream().map(String::new).collect(Collectors.toSet());
    }

    /**
     * Set {@link #mSpecialButtonsKeys}. Must not be {@code null}.
     */
    public void setSpecialButtons(@NonNull Map<SpecialButton, SpecialButtonState> specialButtons) {
        mSpecialButtons = specialButtons;
        mSpecialButtonsKeys = this.mSpecialButtons.keySet().stream().map(SpecialButton::getKey).collect(Collectors.toSet());
    }

    /**
     * Set the {@link ExtraKeysView} button colors.
     *
     * @param buttonTextColor The value for {@link #mButtonTextColor}.
     * @param buttonActiveTextColor The value for {@link #mButtonActiveTextColor}.
     * @param buttonBackgroundColor The value for {@link #mButtonBackgroundColor}.
     * @param buttonActiveBackgroundColor The value for {@link #mButtonActiveBackgroundColor}.
     */
    /** Tint for the press glow pill; set to 0 to fall back to the active background colour. */
    public void setKeyPressFeedbackColor(int color) {
        mKeyPressFeedbackColor = color;
    }

    /** Tells the press feedback whether a blur backdrop is active (soft radial vs rounded fill). */
    public void setKeyPressFeedbackBlurAvailable(boolean available) {
        mKeyPressFeedbackBlurAvailable = available;
    }

    /**
     * Swipe-up popup shape. {@code true} = a vertical "liquid" capsule that connects the source key
     * up to the revealed key (the floating capsule dock); {@code false} = a rounded-rect chip above
     * the source key (the edge-to-edge default dock).
     */
    public void setPopupCapsuleStyle(boolean verticalPill) {
        mPopupVerticalPill = verticalPill;
    }

    public void setButtonColors(int buttonTextColor, int buttonActiveTextColor, int buttonBackgroundColor, int buttonActiveBackgroundColor) {
        mButtonTextColor = buttonTextColor;
        mButtonActiveTextColor = buttonActiveTextColor;
        mButtonBackgroundColor = buttonBackgroundColor;
        mButtonActiveBackgroundColor = buttonActiveBackgroundColor;
    }

    /**
     * Get {@link #mButtonTextColor}.
     */
    public int getButtonTextColor() {
        return mButtonTextColor;
    }

    /**
     * Set {@link #mButtonTextColor}.
     */
    public void setButtonTextColor(int buttonTextColor) {
        mButtonTextColor = buttonTextColor;
    }

    /**
     * Get {@link #mButtonActiveTextColor}.
     */
    public int getButtonActiveTextColor() {
        return mButtonActiveTextColor;
    }

    /**
     * Set {@link #mButtonActiveTextColor}.
     */
    public void setButtonActiveTextColor(int buttonActiveTextColor) {
        mButtonActiveTextColor = buttonActiveTextColor;
    }

    /**
     * Get {@link #mButtonBackgroundColor}.
     */
    public int getButtonBackgroundColor() {
        return mButtonBackgroundColor;
    }

    /**
     * Set {@link #mButtonBackgroundColor}.
     */
    public void setButtonBackgroundColor(int buttonBackgroundColor) {
        mButtonBackgroundColor = buttonBackgroundColor;
    }

    /**
     * Get {@link #mButtonActiveBackgroundColor}.
     */
    public int getButtonActiveBackgroundColor() {
        return mButtonActiveBackgroundColor;
    }

    /**
     * Set {@link #mButtonActiveBackgroundColor}.
     */
    public void setButtonActiveBackgroundColor(int buttonActiveBackgroundColor) {
        mButtonActiveBackgroundColor = buttonActiveBackgroundColor;
    }

    /**
     * Set {@link #mButtonTextAllCaps}.
     */
    public void setButtonTextAllCaps(boolean buttonTextAllCaps) {
        mButtonTextAllCaps = buttonTextAllCaps;
    }

    /**
     * Sets a key-cap label with Nerd Font code points routed to the bundled symbols face, so an
     * icon key never shows tofu. A plain label keeps the stock all-caps transformation; a label
     * that needed spans gets uppercased manually instead, because the all-caps transformation
     * re-creates the text as a plain string and would silently drop the typeface spans. Icon code
     * points have no case, so the visible result is identical either way.
     */
    private void setKeyCapText(@NonNull TextView view, @Nullable CharSequence display) {
        CharSequence label = display == null ? "" : display;
        CharSequence spanned = NerdFontSpans.span(getContext(), label);
        if (spanned == label) {
            view.setAllCaps(mButtonTextAllCaps);
            view.setText(label);
            return;
        }
        view.setAllCaps(false);
        if (mButtonTextAllCaps) {
            // Case mapping can change the length (ß → SS), which would misplace the spans; span
            // the uppercased text from scratch.
            spanned = NerdFontSpans.span(getContext(), label.toString().toUpperCase(Locale.ROOT));
        }
        view.setText(spanned);
    }

    /**
     * Get {@link #mLongPressTimeout}.
     */
    public int getLongPressTimeout() {
        return mLongPressTimeout;
    }

    /**
     * Set {@link #mLongPressTimeout}.
     */
    public void setLongPressTimeout(int longPressDuration) {
        if (longPressDuration >= MIN_LONG_PRESS_DURATION && longPressDuration <= MAX_LONG_PRESS_DURATION) {
            mLongPressTimeout = longPressDuration;
        } else {
            mLongPressTimeout = FALLBACK_LONG_PRESS_DURATION;
        }
    }

    /**
     * Get {@link #mLongPressRepeatDelay}.
     */
    public int getLongPressRepeatDelay() {
        return mLongPressRepeatDelay;
    }

    /**
     * Set {@link #mLongPressRepeatDelay}.
     */
    public void setLongPressRepeatDelay(int longPressRepeatDelay) {
        if (mLongPressRepeatDelay >= MIN_LONG_PRESS__REPEAT_DELAY && mLongPressRepeatDelay <= MAX_LONG_PRESS__REPEAT_DELAY) {
            mLongPressRepeatDelay = longPressRepeatDelay;
        } else {
            mLongPressRepeatDelay = DEFAULT_LONG_PRESS_REPEAT_DELAY;
        }
    }

    /**
     * Get the default map that can be used for {@link #mSpecialButtons}.
     */
    @NonNull
    public Map<SpecialButton, SpecialButtonState> getDefaultSpecialButtons(ExtraKeysView extraKeysView) {
        return new HashMap<SpecialButton, SpecialButtonState>() {

            {
                put(SpecialButton.CTRL, new SpecialButtonState(extraKeysView));
                put(SpecialButton.ALT, new SpecialButtonState(extraKeysView));
                put(SpecialButton.SHIFT, new SpecialButtonState(extraKeysView));
                put(SpecialButton.FN, new SpecialButtonState(extraKeysView));
            }
        };
    }

    /**
     * Reload this instance of {@link ExtraKeysView} with the info passed in {@code extraKeysInfo}.
     *
     * @param extraKeysInfo The {@link ExtraKeysInfo} that defines the necessary info for the extra keys.
     * @param heightPx The height in pixels of the parent surrounding the {@link ExtraKeysView}. It must
     *                 be a single child.
     */
    @SuppressLint("ClickableViewAccessibility")
    public void reload(ExtraKeysInfo extraKeysInfo, float heightPx) {
        if (extraKeysInfo == null)
            return;
        for (SpecialButtonState state : mSpecialButtons.values()) state.buttons = new ArrayList<>();
        mGlowLevels.clear();
        removeAllViews();
        ExtraKeyButton[][] buttons = extraKeysInfo.getMatrix();
        // A column transposes the matrix: the configured rows stand side by side as columns.
        setRowCount(mVertical ? maximumLength(buttons) : buttons.length);
        setColumnCount(mVertical ? buttons.length : maximumLength(buttons));
        for (int row = 0; row < buttons.length; row++) {
            for (int col = 0; col < buttons[row].length; col++) {
                final ExtraKeyButton buttonInfo = buttons[row][col];
                MaterialButton button;
                if (isSpecialButton(buttonInfo)) {
                    button = createSpecialButton(buttonInfo.getKey(), true);
                    if (button == null)
                        return;
                } else {
                    button = new MaterialButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
                }
                button.setAccessibilityDelegate(new AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        // these should funtion like soft keyboard keys;
                        // with this flag set, they honor the TalkBack setting
                        // of hold/release to activate, 2x tap to activate or hybrid;
                        // assuming your Android and TalkBack are recent enough
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            info.setTextEntryKey(true);
                        }
                    }
                });

                setKeyCapText(button, buttonInfo.getDisplay());
                button.setTextColor(mButtonTextColor);
                // Keep multi-letter labels (SHFT, CTRL) on one line. The active/sticky background is
                // an InsetDrawable whose padding shrinks the content box; without this the last
                // letter wrapped to a second row when the key took on its pressed/active background.
                button.setSingleLine(true);
                button.setMaxLines(1);
                button.setPadding(0, 0, 0, 0);
                // MaterialButton applies a default vertical inset (~6dp top/bottom) to its background.
                // On this short row that squeezes the pressed-state background into a thin strip and
                // clips the key to its top half. Zero it so the cap fills the full button height.
                button.setInsetTop(0);
                button.setInsetBottom(0);
                button.setOnClickListener(view -> {
                    performExtraKeyButtonHapticFeedback(view, buttonInfo, button);
                    onAnyExtraKeyButtonClick(view, buttonInfo, button);
                });
                final float popupSwipeThresholdPx = Math.max(
                    ViewConfiguration.get(getContext()).getScaledTouchSlop(),
                    getResources().getDisplayMetrics().density * 8f
                );
                final float[] popupSwipeDownRawY = new float[1];
                final float[] popupSwipeDownRawX = new float[1];
                final boolean[] toolbarPageSwipeTriggered = new boolean[1];
                button.setOnTouchListener((view, event) -> {
                    switch(event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            popupSwipeDownRawY[0] = event.getRawY();
                            popupSwipeDownRawX[0] = event.getRawX();
                            toolbarPageSwipeTriggered[0] = false;
                            // Leave interception enabled for the parent ViewPager. It only intercepts
                            // horizontal-dominant movement, while vertical movement remains here for
                            // the popup gesture. Locking the full ancestor chain on an early upward
                            // movement prevents a gesture that continues left from reaching the
                            // toolbar text-input page.
                            // Instant press feedback: the key's glyphs glow up.
                            glowKeyPress(button);
                            animateKeyCapDip(button, KeyVisualState.PRESSED);
                            // Start long press scheduled executors which will be stopped in next MotionEvent
                            startScheduledExecutors(view, buttonInfo, button);
                            // Keys that neither auto-repeat nor toggle get no hold path above, so give
                            // them a generic press-hold visual so every key reacts to a long press.
                            if (!mRepetitiveKeys.contains(buttonInfo.getKey()) && !isSpecialButton(buttonInfo)) {
                                scheduleGenericHoldVisual(button, buttonInfo);
                            }
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float upwardTravelPx = popupSwipeDownRawY[0] - event.getRawY();
                            float horizontalTravelPx = Math.abs(event.getRawX() - popupSwipeDownRawX[0]);
                            float leftTravelPx = popupSwipeDownRawX[0] - event.getRawX();
                            float verticalTravelPx = Math.abs(event.getRawY() - popupSwipeDownRawY[0]);
                            boolean toolbarPageSwipe = ExtraKeysGesturePolicy.isToolbarPageSwipe(
                                leftTravelPx,
                                verticalTravelPx,
                                popupSwipeThresholdPx
                            );
                            if (!toolbarPageSwipeTriggered[0] && toolbarPageSwipe
                                && mToolbarTextInputSwipeListener != null) {
                                toolbarPageSwipeTriggered[0] = true;
                                stopScheduledExecutors();
                                animateKeyCapDip(button, KeyVisualState.RESTING);
                                dismissTravelPopup(false);
                                restoreButtonVisualState(button, buttonInfo);
                                releaseKeyGlow(button, isSpecialLatched(buttonInfo));
                                mToolbarTextInputSwipeListener.run();
                                return true;
                            }
                            // A vertical-dominant swipe past the threshold is the popup gesture. A
                            // horizontal-dominant drag remains available for the pager to intercept
                            // (→ swipe to the text-input page), and the button receives ACTION_CANCEL.
                            boolean verticalPopupGesture = buttonInfo.getPopup() != null
                                && upwardTravelPx >= popupSwipeThresholdPx
                                && upwardTravelPx > horizontalTravelPx;
                            if (buttonInfo.getPopup() != null) {
                                // Arm the swipe-up travel only on a DELIBERATE upward swipe past the
                                // threshold; below that a plain press-and-hold stays a hold.
                                if (!mBubbleArmed && verticalPopupGesture) {
                                    stopScheduledExecutors();
                                    armBubbleTravel(button, buttonInfo.getPopup());
                                    animateKeyCapDip(button, KeyVisualState.POPUP_ARMED);
                                }
                                if (mBubbleArmed) {
                                    // Bubble sits on the key at the threshold, reaches the secondary
                                    // slot one travel-distance further up; it tracks the finger between.
                                    float frac = (upwardTravelPx - popupSwipeThresholdPx) / mBubbleTravelDistPx;
                                    updateBubbleTravel(frac);
                                }
                            }
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            stopScheduledExecutors();
                            animateKeyCapDip(button, KeyVisualState.RESTING);
                            dismissTravelPopup(false);
                            // Repaint the key's persistent state, then fade the press glow out unless
                            // it is a latched modifier (which stays lit).
                            restoreButtonVisualState(button, buttonInfo);
                            releaseKeyGlow(button, isSpecialLatched(buttonInfo));
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (toolbarPageSwipeTriggered[0]) {
                                return true;
                            }
                            stopScheduledExecutors();
                            animateKeyCapDip(button, KeyVisualState.RESTING);
                            if (mBubbleArmed) {
                                // Swipe-up: commit the secondary if the popup reached the slot,
                                // otherwise treat it as a normal tap of the primary key.
                                boolean commitPopup = mBubbleFrac >= 0.5f && buttonInfo.getPopup() != null;
                                if (commitPopup) {
                                    onAnyExtraKeyButtonClick(view, buttonInfo.getPopup(), button);
                                } else if (mLongPressCount == 0) {
                                    view.performClick();
                                }
                                dismissTravelPopup(commitPopup);
                                // The source key's glow was released on arm; reflect any toggled state.
                                restoreButtonVisualState(button, buttonInfo);
                            } else {
                                if (mLongPressCount == 0) {
                                    view.performClick();
                                }
                                // performClick() may have toggled a special key's active/locked state.
                                restoreButtonVisualState(button, buttonInfo);
                                // Tap -> glow fades out (a single pulse). Latched modifier -> stays lit.
                                releaseKeyGlow(button, isSpecialLatched(buttonInfo));
                            }
                            return true;
                        default:
                            return true;
                    }
                });
                LayoutParams param = new GridLayout.LayoutParams();
                param.width = 0;
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                    param.height = (int) (heightPx + 0.5);
                } else {
                    param.height = 0;
                }
                param.setMargins(0, 0, 0, 0);
                param.columnSpec = GridLayout.spec(mVertical ? row : col, GridLayout.FILL, 1.f);
                param.rowSpec = GridLayout.spec(mVertical ? col : row, GridLayout.FILL, 1.f);
                button.setLayoutParams(param);
                addView(button);
            }
        }
    }

    public void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        if (mExtraKeysViewClient != null)
            mExtraKeysViewClient.onExtraKeyButtonClick(view, buttonInfo, button);
    }

    public void performExtraKeyButtonHapticFeedback(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        if (mExtraKeysViewClient != null) {
            // If client handled the feedback, then just return
            if (mExtraKeysViewClient.performExtraKeyButtonHapticFeedback(view, buttonInfo, button))
                return;
        }
        if (Settings.System.getInt(getContext().getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {
            if (Build.VERSION.SDK_INT >= 28) {
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } else {
                // Perform haptic feedback only if no total silence mode enabled.
                if (Settings.Global.getInt(getContext().getContentResolver(), "zen_mode", 0) != 2) {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            }
        }
    }

    public void onAnyExtraKeyButtonClick(View view, @NonNull ExtraKeyButton buttonInfo, MaterialButton button) {
        if (isSpecialButton(buttonInfo)) {
            if (mLongPressCount > 0)
                return;
            SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
            if (state == null)
                return;
            // Toggle active state and disable lock state if new state is not active
            state.setIsActive(!state.isActive);
            if (!state.isActive)
                state.setIsLocked(false);

            announceSpecialKeyStateChangeForAccessibility(buttonInfo.getKey(), state);
        } else {
            onExtraKeyButtonClick(view, buttonInfo, button);
        }
    }

    private void announceSpecialKeyStateChangeForAccessibility(CharSequence buttonName, SpecialButtonState state) {
        if(mAccessibilityEnabled) {
            CharSequence stateText;
            if(!state.isActive) {
                stateText = getResources().getText(R.string.a11y_special_key_off);
            } else if(state.isLocked) {
                stateText = getResources().getText(R.string.a11y_special_key_latched_on);
            } else {
                stateText = getResources().getText(R.string.a11y_special_key_on);

            }
            String announcementText = buttonName
                + " "
                + stateText
                ;
            announceForAccessibility(announcementText);
        }
    }


    public void startScheduledExecutors(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        stopScheduledExecutors();
        mLongPressCount = 0;
        if (mRepetitiveKeys.contains(buttonInfo.getKey())) {
            // Auto repeat key if long pressed until ACTION_UP stops it by calling stopScheduledExecutors.
            // Currently, only one (last) repeat key can run at a time. Old ones are stopped.
            mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            mScheduledExecutor.scheduleWithFixedDelay(() -> {
                mLongPressCount++;
                // Deepen the glow pill on the first repeat to signal the held/auto-repeat state.
                if (mLongPressCount == 1) {
                    button.post(() -> {
                        animateKeyCapDip(button, KeyVisualState.REPEAT_HELD);
                        glowKeyHold();
                    });
                }
                onExtraKeyButtonClick(view, buttonInfo, button);
            }, mLongPressTimeout, mLongPressRepeatDelay, TimeUnit.MILLISECONDS);
        } else if (isSpecialButton(buttonInfo)) {
            // Lock the key if long pressed by running mSpecialButtonsLongHoldRunnable after
            // waiting for mLongPressTimeout milliseconds. If user does not long press, then the
            // ACTION_UP triggered will cancel the runnable by calling stopScheduledExecutors before
            // it has a chance to run.
            SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
            if (state == null)
                return;
            if (mHandler == null)
                mHandler = new Handler(Looper.getMainLooper());
            mSpecialButtonsLongHoldRunnable = new SpecialButtonsLongHoldRunnable(state, buttonInfo, button);
            mHandler.postDelayed(mSpecialButtonsLongHoldRunnable, mLongPressTimeout);
        }
    }

    public void stopScheduledExecutors() {
        if (mScheduledExecutor != null) {
            mScheduledExecutor.shutdownNow();
            mScheduledExecutor = null;
        }
        if (mSpecialButtonsLongHoldRunnable != null && mHandler != null) {
            mHandler.removeCallbacks(mSpecialButtonsLongHoldRunnable);
            mSpecialButtonsLongHoldRunnable = null;
        }
        cancelGenericHoldVisual();
    }

    /** Schedule the generic press-hold visual (chip deepen + bloom) for a non-repeat/non-special key. */
    private void scheduleGenericHoldVisual(@NonNull MaterialButton button, @NonNull ExtraKeyButton buttonInfo) {
        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }
        cancelGenericHoldVisual();
        mGenericHoldVisualRunnable = () -> {
            animateKeyCapDip(button, KeyVisualState.REPEAT_HELD);
            glowKeyHold();
        };
        mHandler.postDelayed(mGenericHoldVisualRunnable, mLongPressTimeout);
    }

    private void cancelGenericHoldVisual() {
        if (mGenericHoldVisualRunnable != null && mHandler != null) {
            mHandler.removeCallbacks(mGenericHoldVisualRunnable);
        }
        mGenericHoldVisualRunnable = null;
    }

    public class SpecialButtonsLongHoldRunnable implements Runnable {

        public final SpecialButtonState mState;
        public final ExtraKeyButton mButtonInfo;
        public final MaterialButton mButton;

        public SpecialButtonsLongHoldRunnable(SpecialButtonState state, ExtraKeyButton buttonInfo, MaterialButton button) {
            mState = state;
            mButtonInfo = buttonInfo;
            mButton = button;
        }

        public void run() {
            // Toggle active and lock state
            mState.setIsLocked(!mState.isActive);
            mState.setIsActive(!mState.isActive);
            mLongPressCount++;
            if (mButton != null) {
                updateSpecialButtonVisualState(mButton, mState);
                animateKeyCapDip(mButton, KeyVisualState.REPEAT_HELD);
                glowKeyHold();
            }

            announceSpecialKeyStateChangeForAccessibility(mButtonInfo.getKey(), mState);
        }
    }

    void updateSpecialButtonVisualState(@NonNull MaterialButton button, @NonNull SpecialButtonState state) {
        KeyVisualState visualState = state.isLocked
            ? KeyVisualState.STICKY_LOCKED
            : (state.isActive ? KeyVisualState.STICKY_ACTIVE : KeyVisualState.RESTING);
        applyButtonVisualState(button, visualState, state.isActive);
        // A latched modifier keeps its glyphs glowing (persists across rebuilds). When it goes
        // inactive, clear its glow so a consumed one-shot modifier doesn't leave a stale halo
        // (a tap-to-toggle-off still plays its release fade via the following releaseKeyGlow call).
        if (state.isActive || state.isLocked) {
            // A latch is a sustained state — show it at the hold tier (wider, whiter halo).
            applyKeyGlow(button, 1f, glowRadiusDp(KEY_GLOW_RADIUS_HOLD_DP), KEY_GLOW_WHITE_MIX_HOLD);
        } else if (mGlowLevels.containsKey(button)) {
            applyKeyGlow(button, 0f);
        }
    }

    private void applyButtonVisualState(@NonNull MaterialButton button, @NonNull KeyVisualState state,
                                        boolean activeText) {
        // Feedback is now the glyph glow, not a pill: keep the background flat in every state and let
        // the glow (plus the active text colour) carry the pressed / latched indication.
        button.setTextColor(activeText ? mButtonActiveTextColor : mButtonTextColor);
        button.setBackground(new ColorDrawable(mButtonBackgroundColor));
    }

    private void animateKeyCapDip(@NonNull MaterialButton button, @NonNull KeyVisualState state) {
        button.animate().cancel();
        float target;
        long duration;
        boolean overshoot = false;
        switch (state) {
            // The glyph pops UP on press/hold (lifting toward the glow) rather than receding.
            case REPEAT_HELD:
                target = 1.16f;
                duration = 120L;
                overshoot = true;
                break;
            case PRESSED:
                target = 1.12f;
                duration = 90L;
                overshoot = true;
                break;
            case POPUP_ARMED:
                // Source key still recedes while the floating bubble carries the gesture above it.
                target = 0.97f;
                duration = 70L;
                break;
            default:
                target = 1f;
                duration = 120L;
                break;
        }
        if (shouldSnapKeyMotion()) {
            button.setScaleX(target);
            button.setScaleY(target);
            return;
        }
        button.animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(duration)
            .setInterpolator(overshoot ? new OvershootInterpolator(2.0f) : new DecelerateInterpolator())
            .start();
    }

    /**
     * The accent a lit key glows with: the dock accent if the host set one, else the active-text
     * accent. (Not the active *background*, which is a muted grey and reads as nothing over the
     * wallpaper.)
     */
    private int glowAccent() {
        return mKeyPressFeedbackColor != 0 ? mKeyPressFeedbackColor : mButtonActiveTextColor;
    }

    /** Glow colour mixed {@code whiteMix} toward white — a hotter, whiter halo reads as "held". */
    private int keyGlowColor(float whiteMix) {
        return lerpColor(glowAccent(), Color.WHITE, whiteMix);
    }

    /**
     * Without the backdrop refraction lens (pre-API33 / live wallpaper / blur off) the glow is the
     * only feedback, so widen it a touch to keep it legible.
     */
    private float glowRadiusDp(float baseDp) {
        if (mKeyPressFeedbackBlurAvailable) return baseDp;
        return baseDp + (baseDp >= KEY_GLOW_RADIUS_HOLD_DP ? 3f : 2f);
    }

    /** Tap-tier glow (default for existing callers). */
    private void applyKeyGlow(@NonNull MaterialButton button, float level) {
        applyKeyGlow(button, level, glowRadiusDp(KEY_GLOW_RADIUS_TAP_DP), KEY_GLOW_WHITE_MIX_TAP);
    }

    // The glow is drawn by THIS view (see dispatchDraw) rather than as a MaterialButton background:
    // MaterialButton manages its own background and silently overwrites/ignores a custom setBackground,
    // which is why earlier bloom attempts never showed. Drawing on our own canvas always renders.
    // Keyed per button so a latched modifier keeps its halo while another key is pressed.
    private static final class GlowState {
        float level;
        float radiusDp;
        float whiteMix;
    }

    private static final class GlowGradient {
        int accent;
        float whiteMix;
        int viewW;
        int viewH;
        RadialGradient gradient;
        final Matrix matrix = new Matrix();
    }

    private final Map<MaterialButton, GlowState> mGlowLevels = new HashMap<>();
    private final Map<MaterialButton, GlowGradient> mGlowGradients = new HashMap<>();
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Set a key's glow to {@code level} (0..1): a luminous accent bloom drawn behind the glyph (see
     * {@link #dispatchDraw}). {@code radiusDp} sizes the gradient and {@code whiteMix} tints the glow
     * toward white; the previously ignored parameters are now applied.
     */
    private void applyKeyGlow(@NonNull MaterialButton button, float level, float radiusDp, float whiteMix) {
        float l = clamp01(level);
        if (l <= 0.01f) {
            mGlowLevels.remove(button);
            mGlowGradients.remove(button);
        } else {
            GlowState state = mGlowLevels.get(button);
            if (state == null) {
                state = new GlowState();
                mGlowLevels.put(button, state);
            }
            state.level = l;
            state.radiusDp = radiusDp;
            state.whiteMix = whiteMix;
        }
        invalidate(); // repaint the glow layer
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        // Draw each lit key's glow behind the keys so it haloes the glyph (children draw on top).
        if (!mGlowLevels.isEmpty()) {
            int accent = glowAccent();
            int w = getWidth();
            int h = getHeight();
            for (Map.Entry<MaterialButton, GlowState> entry : mGlowLevels.entrySet()) {
                MaterialButton b = entry.getKey();
                GlowState state = entry.getValue();
                float l = state.level;
                if (b.getParent() != this || b.getWidth() <= 0 || l <= 0.01f) continue;
                float cx = b.getLeft() + b.getWidth() * 0.5f;
                float cy = b.getTop() + b.getHeight() * 0.5f;
                // Keep the whole circle inside the view so it never hard-clips at the row edge / the
                // A-Z bar divider above it — the gradient fades to nothing before any boundary.
                float maxRadius = Math.min(Math.min(cx, w - cx), Math.min(cy, h - cy));
                float radiusPx = dpToPx(state.radiusDp);
                float radius = Math.min(radiusPx, maxRadius);
                if (radius <= 0f) continue;
                // Cache a unit gradient per key; level is applied through paint alpha so a gradient
                // only needs rebuilding when the color/whiteMix or view bounds change.
                GlowGradient cached = mGlowGradients.get(b);
                if (cached == null || cached.accent != accent || cached.whiteMix != state.whiteMix
                    || cached.viewW != w || cached.viewH != h) {
                    cached = new GlowGradient();
                    cached.accent = accent;
                    cached.whiteMix = state.whiteMix;
                    cached.viewW = w;
                    cached.viewH = h;
                    int core = lerpColor(accent, Color.WHITE, state.whiteMix);
                    cached.gradient = new RadialGradient(0f, 0f, 1f,
                        new int[]{withAlpha(core, 150), withAlpha(accent, 80), withAlpha(accent, 0)},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
                    mGlowGradients.put(b, cached);
                }
                cached.matrix.setScale(radius, radius);
                cached.matrix.postTranslate(cx, cy);
                cached.gradient.setLocalMatrix(cached.matrix);
                mGlowPaint.setShader(cached.gradient);
                mGlowPaint.setAlpha(Math.round(255 * l));
                canvas.drawCircle(cx, cy, radius, mGlowPaint);
            }
            mGlowPaint.setAlpha(255);
            mGlowPaint.setShader(null);
        }
        super.dispatchDraw(canvas);
        drawPageIndicator(canvas);
    }

    /**
     * Which key page this view is and how many there are. Drawn inside the row rather than added as
     * its own band: the accessory stack is explicitly sized, and a new band would have to be folded
     * into the combined height everywhere or it would clip invisibly.
     */
    public void setPageIndicator(int page, int pageCount) {
        if (page == mPageIndex && pageCount == mPageCount) return;
        mPageIndex = page;
        mPageCount = pageCount;
        invalidate();
    }

    private void drawPageIndicator(@NonNull Canvas canvas) {
        if (mPageCount < 2) return;
        float radius = dpToPx(1.6f);
        float gap = dpToPx(5f);
        float totalWidth = mPageCount * radius * 2 + (mPageCount - 1) * (gap - radius * 2);
        float cx = (getWidth() - totalWidth) / 2f + radius;
        float cy = getHeight() - dpToPx(2.6f);
        if (mPageDotPaint == null) {
            mPageDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPageDotPaint.setStyle(Paint.Style.FILL);
        }
        int color = mButtonTextColor;
        for (int i = 0; i < mPageCount; i++) {
            mPageDotPaint.setColor(color);
            mPageDotPaint.setAlpha(i == mPageIndex ? 150 : 55);
            canvas.drawCircle(cx, cy, radius, mPageDotPaint);
            cx += gap;
        }
    }

    /** Glow the pressed key's glyphs up (a tap reads as a single tight pulse once it releases). */
    private void glowKeyPress(@NonNull MaterialButton button) {
        if (mKeyGlowAnimator != null) {
            mKeyGlowAnimator.cancel();
            mKeyGlowAnimator = null;
        }
        mKeyGlowButton = button;
        mBubbleArmed = false;
        mBubbleHeld = false;
        mBubbleFrac = 0f;
        final float r = glowRadiusDp(KEY_GLOW_RADIUS_TAP_DP);
        if (shouldSnapKeyMotion()) {
            applyKeyGlow(button, 1f, r, KEY_GLOW_WHITE_MIX_TAP);
            return;
        }
        ValueAnimator in = ValueAnimator.ofFloat(0f, 1f);
        in.setDuration(140L);
        in.setInterpolator(new DecelerateInterpolator());
        in.addUpdateListener(a -> applyKeyGlow(button, (float) a.getAnimatedValue(), r, KEY_GLOW_WHITE_MIX_TAP));
        mKeyGlowAnimator = in;
        in.start();
    }

    /**
     * Press-and-hold / auto-repeat: escalate the halo to the wider, whiter hold tier and then breathe
     * it, so a sustained hold is unmistakably different from a quick tap.
     */
    private void glowKeyHold() {
        final MaterialButton button = mKeyGlowButton;
        if (button == null)
            return;
        mBubbleHeld = true;
        if (mKeyGlowAnimator != null) {
            mKeyGlowAnimator.cancel();
            mKeyGlowAnimator = null;
        }
        final float tapR = glowRadiusDp(KEY_GLOW_RADIUS_TAP_DP);
        final float holdR = glowRadiusDp(KEY_GLOW_RADIUS_HOLD_DP);
        if (shouldSnapKeyMotion()) {
            applyKeyGlow(button, 1f, holdR, KEY_GLOW_WHITE_MIX_HOLD);
            return;
        }
        final ValueAnimator ramp = ValueAnimator.ofFloat(0f, 1f);
        ramp.setDuration(180L);
        ramp.setInterpolator(new DecelerateInterpolator());
        // cancel() dispatches onAnimationEnd synchronously, so a release mid-escalation would
        // otherwise start (and orphan) the infinite pulse. Track cancellation to suppress that.
        final boolean[] rampCancelled = { false };
        ramp.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            applyKeyGlow(button, 1f, tapR + (holdR - tapR) * t,
                KEY_GLOW_WHITE_MIX_TAP + (KEY_GLOW_WHITE_MIX_HOLD - KEY_GLOW_WHITE_MIX_TAP) * t);
        });
        ramp.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator a) {
                rampCancelled[0] = true;
            }
            @Override
            public void onAnimationEnd(Animator a) {
                if (rampCancelled[0] || mKeyGlowButton != button) return;
                ValueAnimator pulse = ValueAnimator.ofFloat(1f, 0.72f);
                pulse.setDuration(520L);
                pulse.setRepeatMode(ValueAnimator.REVERSE);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.setInterpolator(new AccelerateDecelerateInterpolator());
                pulse.addUpdateListener(an ->
                    applyKeyGlow(button, (float) an.getAnimatedValue(), holdR, KEY_GLOW_WHITE_MIX_HOLD));
                mKeyGlowAnimator = pulse;
                pulse.start();
            }
        });
        mKeyGlowAnimator = ramp;
        ramp.start();
    }

    /**
     * Release the press glow. {@code keepLit} true (a now-latched modifier) leaves the glow on at the
     * hold tier; otherwise it fades out — a quick tap therefore reads as a single glow pulse.
     */
    private void releaseKeyGlow(@NonNull MaterialButton button, boolean keepLit) {
        if (mKeyGlowAnimator != null) {
            mKeyGlowAnimator.cancel();
            mKeyGlowAnimator = null;
        }
        mKeyGlowButton = null;
        if (keepLit) {
            applyKeyGlow(button, 1f, glowRadiusDp(KEY_GLOW_RADIUS_HOLD_DP), KEY_GLOW_WHITE_MIX_HOLD);
            return;
        }
        if (shouldSnapKeyMotion()) {
            applyKeyGlow(button, 0f);
            return;
        }
        final float r = glowRadiusDp(KEY_GLOW_RADIUS_TAP_DP);
        ValueAnimator out = ValueAnimator.ofFloat(1f, 0f);
        out.setDuration(240L);
        out.setInterpolator(new AccelerateInterpolator());
        out.addUpdateListener(a -> applyKeyGlow(button, (float) a.getAnimatedValue(), r, KEY_GLOW_WHITE_MIX_TAP));
        out.start();
    }

    /** True if {@code info} is a modifier currently latched on (active or locked). */
    private boolean isSpecialLatched(@NonNull ExtraKeyButton info) {
        if (!isSpecialButton(info))
            return false;
        SpecialButtonState st = mSpecialButtons.get(SpecialButton.valueOf(info.getKey()));
        return st != null && (st.isActive || st.isLocked);
    }

    /**
     * Begin swipe-up travel. The source key dims back to resting and a floating glyph rises with the
     * finger: it shows the source glyph near the key and swaps to the (glowing) secondary glyph once
     * past the midpoint, so the user always sees which key a release will commit.
     */
    private void armBubbleTravel(@NonNull MaterialButton button, @NonNull ExtraKeyButton popup) {
        releaseKeyGlow(button, false); // source key returns to its resting colours
        dismissTravelPopup(false);
        mBubbleArmed = true;
        mBubbleHeld = true;
        mBubbleFrac = 0f;

        int[] loc = new int[2];
        button.getLocationOnScreen(loc);
        mTravelKeyW = Math.max(1, button.getWidth());
        mTravelKeyH = Math.max(1, button.getHeight());
        // The bubble is larger than the key so the secondary glyph reads prominently. Recenter the
        // popup origin (its top-left) so the enlarged bubble stays centred over the key, and clamp
        // X so it never runs off-screen.
        // A touch bigger than the key so the secondary glyph and its compact chip read clearly.
        mBubbleW = Math.round(mTravelKeyW * 1.2f);
        mBubbleH = Math.round(mTravelKeyH * 1.2f);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int centeredX = loc[0] - (mBubbleW - mTravelKeyW) / 2;
        mTravelKeyScreenX = Math.max(0, Math.min(centeredX, Math.max(0, screenW - mBubbleW)));
        mTravelKeyScreenY = loc[1] - (mBubbleH - mTravelKeyH) / 2;
        // Clear the row: the bubble rises a full key-height plus a little more.
        mBubbleTravelDistPx = mTravelKeyH + dpToPx(16f);
        mTravelSourceText = button.getText();
        mTravelSecondaryText = popup.getDisplay();
        // The popup always previews the SECONDARY (revealed) key — that's what the swipe selects.
        mTravelShowingSecondary = true;

        TextView tv = new TextView(getContext());
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        tv.setSingleLine(true);
        tv.setMaxLines(1);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, button.getTextSize() * 1.25f);
        tv.setTypeface(button.getTypeface());
        tv.setTextColor(activeTextColor());
        setKeyCapText(tv, mTravelSecondaryText);
        mTravelBubbleBg = buildFloatingSecondaryKeyBackground();
        tv.setBackground(mTravelBubbleBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tv.setElevation(dpToPx(3f));
        }

        mTravelBubble = tv;
        mTravelPopup = new PopupWindow(tv, mBubbleW, mBubbleH, false);
        mTravelPopup.setClippingEnabled(false);
        mTravelPopup.setFocusable(false);
        mTravelPopup.setOutsideTouchable(false);
        mTravelPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mTravelPopup.setElevation(dpToPx(4f));
        }
        View root = getRootView();
        if (root == null) root = button;
        try {
            mTravelPopup.showAtLocation(root, Gravity.NO_GRAVITY, mTravelKeyScreenX, mTravelKeyScreenY);
        } catch (Exception ignored) {
            mTravelPopup = null;
            mTravelBubble = null;
            mTravelBubbleBg = null;
            return;
        }
        // Alpha + scale track the swipe (set in updateBubbleTravel): the bubble grows in as the
        // finger rises and fades/shrinks away smoothly as it slides back down. Start invisible.
        tv.setPivotX(mBubbleW / 2f);
        tv.setPivotY(mBubbleH / 2f);
        tv.setAlpha(0f);
        tv.setScaleX(0.85f);
        tv.setScaleY(0.85f);
        updateBubbleTravel(0f);
    }

    /** Glide the travel popup between the key (frac 0) and the secondary slot above (frac 1). */
    private void updateBubbleTravel(float frac) {
        frac = clamp01(frac);
        mBubbleFrac = frac;
        if (mTravelPopup == null || mTravelBubble == null)
            return;
        int y = Math.max(0, Math.round(mTravelKeyScreenY - mBubbleTravelDistPx * frac));
        try {
            mTravelPopup.update(mTravelKeyScreenX, y, mBubbleW, mBubbleH);
        } catch (Exception ignored) {
        }
        // Always preview the SECONDARY glyph, glowing — the bubble shows the key the swipe selects,
        // never the source glyph (regardless of how far up/down the finger has travelled).
        if (!mTravelShowingSecondary) {
            mTravelShowingSecondary = true;
            setKeyCapText(mTravelBubble, mTravelSecondaryText);
        }
        mTravelBubble.setShadowLayer(dpToPx(KEY_GLOW_RADIUS_HOLD_DP), 0f, 0f,
            withAlpha(keyGlowColor(KEY_GLOW_WHITE_MIX_HOLD), 255));
        // Grow in as the finger rises, fade/shrink out smoothly as it slides back down.
        if (!shouldSnapKeyMotion()) {
            float a = clamp01(frac / 0.45f);
            mTravelBubble.setAlpha(a);
            mTravelBubble.setScaleX(0.85f + 0.15f * a);
            mTravelBubble.setScaleY(0.85f + 0.15f * a);
        } else {
            mTravelBubble.setAlpha(1f);
        }
        mTravelBubble.invalidate();
    }

    /** Fade and dismiss the travel popup, if any. */
    private void dismissTravelPopup(boolean committed) {
        final PopupWindow popup = mTravelPopup;
        final TextView tv = mTravelBubble;
        mTravelPopup = null;
        mTravelBubble = null;
        mTravelBubbleBg = null;
        mBubbleArmed = false;
        mBubbleHeld = false;
        mBubbleFrac = 0f;
        if (popup == null)
            return;
        if (tv == null || shouldSnapKeyMotion()) {
            safeDismiss(popup);
            return;
        }
        tv.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(110L)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(() -> safeDismiss(popup))
            .start();
    }

    private static void safeDismiss(@NonNull PopupWindow popup) {
        try {
            popup.dismiss();
        } catch (Exception ignored) {
        }
    }

    private int feedbackTint() {
        return mKeyPressFeedbackColor != 0 ? mKeyPressFeedbackColor : mButtonActiveBackgroundColor;
    }

    private int activeTextColor() {
        return mButtonActiveTextColor != 0 ? mButtonActiveTextColor : Color.WHITE;
    }

    /** Glass keycap container for the swipe-up secondary key; gesture geometry remains unchanged. */
    @NonNull
    private GradientDrawable buildFloatingSecondaryKeyBackground() {
        int accent = feedbackTint();
        int configuredBase = mButtonBackgroundColor;
        int neutralBase = Color.alpha(configuredBase) > 24
            ? configuredBase
            : lerpColor(Color.rgb(12, 18, 24), activeTextColor(), 0.08f);
        int fill = lerpColor(neutralBase, accent, 0.12f);
        int border = lerpColor(accent, activeTextColor(), 0.16f);

        GradientDrawable chip = new GradientDrawable();
        chip.setShape(GradientDrawable.RECTANGLE);
        chip.setCornerRadius(dpToPx(12f));
        chip.setColor(withAlpha(fill, mKeyPressFeedbackBlurAvailable ? 174 : 224));
        chip.setStroke(Math.max(1, Math.round(dpToPx(1f))), withAlpha(border, 148));
        chip.setDither(true);
        return chip;
    }

    private static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (0xFF << 24)
            | (Math.round(ar + (br - ar) * t) << 16)
            | (Math.round(ag + (bg - ag) * t) << 8)
            | Math.round(ab + (bb - ab) * t);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private boolean shouldSnapKeyMotion() {
        try {
            return Settings.Global.getFloat(
                getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) <= 0f;
        } catch (Exception ignored) {
            return false;
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private void restoreButtonVisualState(@NonNull MaterialButton button, @NonNull ExtraKeyButton buttonInfo) {
        if (isSpecialButton(buttonInfo)) {
            SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
            if (state != null) {
                updateSpecialButtonVisualState(button, state);
            } else {
                applyButtonVisualState(button, KeyVisualState.RESTING, false);
            }
        } else {
            applyButtonVisualState(button, KeyVisualState.RESTING, false);
        }
    }

    public void dismissPopup() {
        if (mKeyGlowButton != null) {
            releaseKeyGlow(mKeyGlowButton, false);
        }
        dismissTravelPopup(false);
    }

    /**
     * Check whether a {@link ExtraKeyButton} is a {@link SpecialButton}.
     */
    public boolean isSpecialButton(ExtraKeyButton button) {
        return mSpecialButtonsKeys.contains(button.getKey());
    }

    /**
     * Read whether {@link SpecialButton} registered in {@link #mSpecialButtons} is active or not.
     *
     * @param specialButton The {@link SpecialButton} to read.
     * @param autoSetInActive Set to {@code true} if {@link SpecialButtonState#isActive} should be
     *                        set {@code false} if button is not locked.
     * @return Returns {@code null} if button does not exist in {@link #mSpecialButtons}. If button
     *         exists, then returns {@code true} if the button is created in {@link ExtraKeysView}
     *         and is active, otherwise {@code false}.
     */
    @Nullable
    public Boolean readSpecialButton(SpecialButton specialButton, boolean autoSetInActive) {
        SpecialButtonState state = mSpecialButtons.get(specialButton);
        if (state == null)
            return null;
        if (!state.isCreated || !state.isActive)
            return false;
        // Disable active state only if not locked
        if (autoSetInActive && !state.isLocked) {
            state.setIsActive(false);
            announceSpecialKeyStateChangeForAccessibility(specialButton.getKey(), state);
        }

        return true;
    }

    public MaterialButton createSpecialButton(String buttonKey, boolean needUpdate) {
        SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonKey));
        if (state == null)
            return null;
        state.setIsCreated(true);
        MaterialButton button = new MaterialButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        updateSpecialButtonVisualState(button, state);
        if (needUpdate) {
            state.buttons.add(button);
        }
        return button;
    }

    /**
     * General util function to compute the longest column length in a matrix.
     */
    public static int maximumLength(Object[][] matrix) {
        int m = 0;
        for (Object[] row : matrix) m = Math.max(m, row.length);
        return m;
    }
}
