package com.termux.app.terminal.extrakeys;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.R;
import com.termux.shared.termux.extrakeys.DeckButtonDrawable;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeyColorRole;
import com.termux.shared.termux.extrakeys.ExtraKeysClient;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.PlaceSwitchGlyph;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.theme.ThemeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One page of keys: the configured rows as equal cells filling the page. It owns the touch on its
 * keys (press, hold, repeat, swipe-up second key) and the colours they wear; the bar above it owns
 * the page turn and the height.
 */
public final class ExtraKeysPageView extends ViewGroup {

    // ---- Defaults the theme attributes fall back to, as the old row had them.

    public static final int ATTR_BUTTON_TEXT_COLOR = R.attr.extraKeysButtonTextColor;
    public static final int ATTR_BUTTON_ACTIVE_TEXT_COLOR = R.attr.extraKeysButtonActiveTextColor;
    public static final int ATTR_BUTTON_BACKGROUND_COLOR = R.attr.extraKeysButtonBackgroundColor;
    public static final int ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR =
        R.attr.extraKeysButtonActiveBackgroundColor;
    public static final int DEFAULT_BUTTON_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_BUTTON_ACTIVE_TEXT_COLOR = 0xFF80DEEA;
    public static final int DEFAULT_BUTTON_BACKGROUND_COLOR = 0x00000000;
    public static final int DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR = 0xFF7F7F7F;

    /** The rounding a coloured cap is drawn with, and how far it sits in from its cell. */
    public static final float CAP_CORNER_RADIUS_DP = 12f;
    public static final float CAP_INSET_HORIZONTAL_DP = 2f;
    public static final float CAP_INSET_VERTICAL_DP = 3f;
    /** The deck look's button sits one dp in from its cell. */
    private static final float DECK_INSET_DP = 1f;

    public static final float BUBBLE_WIDTH_DP = 52f;
    public static final float BUBBLE_HEIGHT_DP = 60f;
    public static final float BUBBLE_RADIUS_DP = 14f;
    private static final float BUBBLE_GAP_DP = 4f;
    private static final float BUBBLE_TEXT_SP = 22f;

    // ---- Interfaces the host answers, as the old row had them.

    /** Whether a key can act where the page is currently standing. */
    public interface KeyUsabilityPolicy {
        /**
         * @param keyValue the key or macro the button sends, exactly as configured.
         * @return {@code false} to draw the key dead and let taps fall through it.
         */
        boolean isKeyUsable(@NonNull String keyValue);
    }

    /** Where a key stands relative to the place the wall is showing. */
    public enum PlaceFocus {
        /** Not a place switch: the key is drawn like any other. */
        NOT_A_PLACE,
        /** Switches to the place now in front. */
        FOCUSED,
        /** Switches to one of the places behind it. */
        UNFOCUSED
    }

    /** Which keys switch the wall to a place, and which place is in front. */
    public interface PlaceSwitchPolicy {
        /** @param keyValue the key or macro the button sends, exactly as configured. */
        @NonNull PlaceFocus placeFocusOf(@NonNull String keyValue);
    }

    /** Told the colour every place switch's glyph is painted in, whenever the page restates them. */
    public interface PlaceGlyphColorListener {
        /** @param colorsByKeyValue each place switch's glyph colour, by the key value it sends. */
        void onPlaceGlyphColors(@NonNull Map<String, Integer> colorsByKeyValue);
    }

    /**
     * A key whose touch is not the press policy's: the host takes the whole stream from the finger
     * going down to its lifting (the page switcher key, which slides along its panel). Events carry
     * raw screen coordinates.
     */
    public interface KeyTouchOwner {
        /** Whether the primary key {@code keyValue} is the owner's. */
        boolean ownsKey(@NonNull String keyValue);

        /** The finger went down on {@code cap}; false leaves the touch to the press policy. */
        boolean down(@NonNull MotionEvent ev, @NonNull View cap);

        void move(@NonNull MotionEvent ev);

        void up(@NonNull MotionEvent ev);

        void cancel();
    }

    // ---- State.

    private final float mDensity;
    private final int mTouchSlop;
    private final int mLongPressMs;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<ExtraKeyCapView> mCaps = new ArrayList<>();
    private final List<ExtraKeyButton> mKeys = new ArrayList<>();
    /** Keys on their way out during a set change: children, drawn, but not in {@link #mCaps}. */
    private final List<ExtraKeyCapView> mLeaving = new ArrayList<>();
    private boolean mMorphing;
    @Nullable private ValueAnimator mMorph;
    @Nullable private com.termux.app.window.FirstLayout.Handle mMorphPreDraw;
    @Nullable private Runnable mMorphEnd;
    @Nullable private ExtraKeysSetMorph.Plan mMorphPlan;
    @Nullable private ExtraKeyCapView[] mMorphOldCaps;
    @Nullable private KeyId[] mMorphOldIds;
    @Nullable private float[] mMorphOldRects;
    private int mMorphOldLeft;
    private int mMorphOldTop;
    private float mMorphLeaveDx;
    private float mMorphLeaveDy;
    private final Map<ExtraKeyColorRole, int[]> mRoleColors = new java.util.HashMap<>();
    private int mMatrixRows;
    private int mMatrixColumns;
    private boolean mVertical;

    private int mButtonTextColor;
    private int mButtonActiveTextColor;
    private int mButtonBackgroundColor;
    private int mButtonActiveBackgroundColor;
    private int mKeyPressFeedbackColor;
    private boolean mKeyPressFeedbackBlurAvailable = true;
    private boolean mButtonTextAllCaps = true;
    private boolean mDeckOn;
    private int mDeckKey, mDeckKeyTop, mDeckKeyFoot, mDeckLit;

    @Nullable private KeyUsabilityPolicy mUsabilityPolicy;
    @Nullable private PlaceSwitchPolicy mPlaceSwitchPolicy;
    @Nullable private PlaceGlyphColorListener mPlaceGlyphColorListener;
    @Nullable private KeyTouchOwner mTouchOwner;
    /** The page switcher key's glyph, as the host last said it; null until it has. */
    private final int[] mRawAt = new int[2];

    @Nullable private ExtraKeysClient mClient;
    @Nullable private ExtraKeyModifiers mModifiers;
    private boolean mAttached;
    private boolean mHoldActsOnKey = true;
    private final ExtraKeyModifiers.Listener mModifierListener = this::syncModifier;

    // ---- One touch.

    @Nullable private ExtraKeyCapView mActive;
    @Nullable private ExtraKeyPressPolicy mPolicy;
    /** The cap whose stream {@link #mTouchOwner} has: every event of it goes to the owner. */
    @Nullable private ExtraKeyCapView mOwnedCap;
    private float mDownX;
    private float mDownY;
    private boolean mTicked;
    private boolean mSwipeArmed;
    private final Runnable mHoldRunnable = this::onHold;
    private final Runnable mRepeatRunnable = this::onRepeat;

    @Nullable private PopupWindow mBubble;
    @Nullable private FrameLayout mBubbleRoot;
    @Nullable private TextView mBubbleText;
    @Nullable private GradientDrawable mBubbleBackground;
    private boolean mBubbleShown;
    private final int[] mLocation = new int[2];

    public ExtraKeysPageView(@NonNull Context context) {
        this(context, null);
    }

    public ExtraKeysPageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // The rows are a key layout the user wrote left to right; the page keeps its columns that
        // way in every locale.
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        // The glow is clipped to its own cap; the rim and bubble need nothing past the page.
        mDensity = context.getResources().getDisplayMetrics().density;
        ViewConfiguration config = ViewConfiguration.get(context);
        mTouchSlop = config.getScaledTouchSlop();
        mLongPressMs = ViewConfiguration.getLongPressTimeout();
        setButtonColors(
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_TEXT_COLOR, DEFAULT_BUTTON_TEXT_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_TEXT_COLOR,
                DEFAULT_BUTTON_ACTIVE_TEXT_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_BACKGROUND_COLOR,
                DEFAULT_BUTTON_BACKGROUND_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR,
                DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR));
    }

    // ---- Keys.

    /** Builds one cap per key of the matrix; null clears the page. */
    public void setKeys(@Nullable ExtraKeysInfo info) {
        endMorph();
        cancelPress();
        removeAllViews();
        mCaps.clear();
        mKeys.clear();
        buildCaps(info, null);
        syncAllModifiers();
        publishPlaceGlyphColors();
        requestLayout();
    }

    /** Adds a cap per key of the matrix, taking a cap from {@code reusable} where its id is there. */
    private void buildCaps(@Nullable ExtraKeysInfo info, @Nullable Map<KeyId, ExtraKeyCapView> reusable) {
        mMatrixRows = 0;
        mMatrixColumns = 0;
        ExtraKeyButton[][] matrix = info == null ? null : info.getMatrix();
        if (matrix == null) return;
        mMatrixRows = matrix.length;
        for (int row = 0; row < matrix.length; row++) {
            if (matrix[row] == null) continue;
            mMatrixColumns = Math.max(mMatrixColumns, matrix[row].length);
            for (int col = 0; col < matrix[row].length; col++) {
                ExtraKeyButton key = matrix[row][col];
                if (key != null) addCap(key, row, col, reusable);
            }
        }
    }

    private void addCap(@NonNull ExtraKeyButton key, int row, int col,
                        @Nullable Map<KeyId, ExtraKeyCapView> reusable) {
        String second = key.getPopup() == null ? null : key.getPopup().getKey();
        int occurrence = 0;
        for (int i = 0; i < mCaps.size(); i++) {
            KeyId other = mCaps.get(i).keyId();
            if (other.key.equals(key.getKey() == null ? "" : key.getKey())
                && (second == null ? other.second == null : second.equals(other.second))) {
                occurrence++;
            }
        }
        KeyId id = new KeyId(key.getKey(), second, occurrence);
        ExtraKeyCapView cap = reusable == null ? null : reusable.remove(id);
        boolean fresh = cap == null;
        if (fresh) {
            cap = new ExtraKeyCapView(getContext(), key, id);
            // Touch never reaches a cap (the page takes it); these serve accessibility actions.
            cap.setOnClickListener(v -> performAccessibilityTap((ExtraKeyCapView) v));
            cap.setOnLongClickListener(v -> performAccessibilityHold((ExtraKeyCapView) v));
        } else {
            cap.animate().cancel();
            cap.rebind(key);
        }
        cap.mRow = row;
        cap.mColumn = col;
        cap.setLabel(key.getDisplay(), mButtonTextAllCaps);
        if (isPageSwitcherKey(key)) {
            cap.setContentDescription(
                getContext().getString(com.termux.R.string.tool_app_page_switcher));
            // Asked for as the cap is built or bound: no refresh has to have run first.
            cap.bindPageGlyph();
        }
        mCaps.add(cap);
        mKeys.add(key);
        // Its colour, and whether it can act here at all, decided once as it is built.
        styleCap(cap);
        if (fresh) addView(cap);
    }

    // ---- Set change.

    /**
     * Changes to another key set with the keys found in both gliding to their new cells, new keys
     * growing in and gone keys shrinking out. Only translation, scale and alpha move, so no layout
     * runs while it plays. Where the page cannot show it, this is {@link #setKeys} and {@code onEnd}
     * runs at once; otherwise {@code onEnd} runs once when the morph has finished or was cut short.
     */
    public void morphTo(@Nullable ExtraKeysInfo info, @Nullable Runnable onEnd) {
        endMorph();
        if (!mAttached || !isShown() || !isLaidOut() || getWidth() <= 0 || getHeight() <= 0) {
            setKeys(info);
            if (onEnd != null) onEnd.run();
            return;
        }
        cancelPress();
        int oldCount = mCaps.size();
        mMorphOldCaps = mCaps.toArray(new ExtraKeyCapView[0]);
        mMorphOldIds = new KeyId[oldCount];
        mMorphOldRects = new float[oldCount * 4];
        mMorphOldLeft = getLeft();
        mMorphOldTop = getTop();
        Map<KeyId, ExtraKeyCapView> reusable = new HashMap<>();
        for (int i = 0; i < oldCount; i++) {
            ExtraKeyCapView cap = mMorphOldCaps[i];
            mMorphOldIds[i] = cap.keyId();
            mMorphOldRects[i * 4] = getLeft() + cap.getLeft();
            mMorphOldRects[i * 4 + 1] = getTop() + cap.getTop();
            mMorphOldRects[i * 4 + 2] = getLeft() + cap.getRight();
            mMorphOldRects[i * 4 + 3] = getTop() + cap.getBottom();
            reusable.put(cap.keyId(), cap);
        }
        mCaps.clear();
        mKeys.clear();
        buildCaps(info, reusable);
        for (int i = 0; i < oldCount; i++) {
            // Still in the map: no new key took it. It stays a child, drawn, but takes no touch.
            if (reusable.get(mMorphOldIds[i]) == mMorphOldCaps[i]) mLeaving.add(mMorphOldCaps[i]);
        }
        syncAllModifiers();
        publishPlaceGlyphColors();
        mMorphing = true;
        mMorphEnd = onEnd;
        mMorphPreDraw = com.termux.app.window.FirstLayout.beforeNextDraw(this, this::startMorph);
        requestLayout();
    }

    /** Cuts a running or waiting morph short: the final state at once, and its {@code onEnd} runs. */
    public void endMorphNow() {
        endMorph();
    }

    /** Runs once, after the layout that follows {@link #morphTo}: plans, sets t=0 and plays. */
    private boolean startMorph() {
        removeMorphPreDraw();
        if (!mMorphing || mMorph != null) return true;
        int count = mCaps.size();
        KeyId[] ids = new KeyId[count];
        float[] rects = new float[count * 4];
        for (int i = 0; i < count; i++) {
            ExtraKeyCapView cap = mCaps.get(i);
            ids[i] = cap.keyId();
            rects[i * 4] = getLeft() + cap.getLeft();
            rects[i * 4 + 1] = getTop() + cap.getTop();
            rects[i * 4 + 2] = getLeft() + cap.getRight();
            rects[i * 4 + 3] = getTop() + cap.getBottom();
        }
        mMorphPlan = ExtraKeysSetMorph.plan(mMorphOldIds, mMorphOldRects, ids, rects);
        mMorphLeaveDx = mMorphOldLeft - getLeft();
        mMorphLeaveDy = mMorphOldTop - getTop();
        applyMorph(0f);
        mMorph = ValueAnimator.ofFloat(0f, 1f);
        mMorph.setDuration(ExtraKeysSetMorph.MORPH_MS);
        mMorph.setInterpolator(new PathInterpolator(ExtraKeysSetMorph.EASE_X1,
            ExtraKeysSetMorph.EASE_Y1, ExtraKeysSetMorph.EASE_X2, ExtraKeysSetMorph.EASE_Y2));
        mMorph.addUpdateListener(a -> applyMorph((Float) a.getAnimatedValue()));
        mMorph.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endMorph();
            }
        });
        mMorph.start();
        return true;
    }

    /** Draw only: translation, scale and alpha, no layout and no text. */
    private void applyMorph(float t) {
        ExtraKeysSetMorph.Plan plan = mMorphPlan;
        ExtraKeyCapView[] oldCaps = mMorphOldCaps;
        if (plan == null || oldCaps == null) return;
        for (int n = 0; n < mCaps.size(); n++) {
            ExtraKeyCapView cap = mCaps.get(n);
            if (plan.oldOfNew[n] == ExtraKeysSetMorph.ENTER) {
                float s = ExtraKeysSetMorph.enterScale(t);
                cap.setScaleX(s);
                cap.setScaleY(s);
                cap.setAlpha(ExtraKeysSetMorph.enterAlpha(t));
            } else {
                cap.setTranslationX(ExtraKeysSetMorph.stayTranslation(plan.fromDx[n], t));
                cap.setTranslationY(ExtraKeysSetMorph.stayTranslation(plan.fromDy[n], t));
                cap.setScaleX(ExtraKeysSetMorph.stayScale(plan.fromScaleX[n], t));
                cap.setScaleY(ExtraKeysSetMorph.stayScale(plan.fromScaleY[n], t));
            }
        }
        for (int o = 0; o < oldCaps.length; o++) {
            if (!plan.leaving[o]) continue;
            ExtraKeyCapView cap = oldCaps[o];
            float s = ExtraKeysSetMorph.leaveScale(t);
            // The page may have moved; the leaving key stays where it was drawn.
            cap.setTranslationX(mMorphLeaveDx);
            cap.setTranslationY(mMorphLeaveDy);
            cap.setScaleX(s);
            cap.setScaleY(s);
            cap.setAlpha(ExtraKeysSetMorph.leaveAlpha(t));
        }
    }

    private void removeMorphPreDraw() {
        if (mMorphPreDraw == null) return;
        mMorphPreDraw.cancel();
        mMorphPreDraw = null;
    }

    /** Snaps to the final state: identity on every key, gone keys removed, then {@code onEnd}. */
    private void endMorph() {
        if (!mMorphing) return;
        mMorphing = false;
        removeMorphPreDraw();
        ValueAnimator animator = mMorph;
        mMorph = null;
        if (animator != null) {
            animator.removeAllListeners();
            animator.removeAllUpdateListeners();
            animator.cancel();
        }
        for (int i = 0; i < mCaps.size(); i++) resetTransform(mCaps.get(i));
        for (int i = 0; i < mLeaving.size(); i++) removeView(mLeaving.get(i));
        mLeaving.clear();
        mMorphPlan = null;
        mMorphOldCaps = null;
        mMorphOldIds = null;
        mMorphOldRects = null;
        Runnable end = mMorphEnd;
        mMorphEnd = null;
        if (end != null) end.run();
    }

    private static void resetTransform(@NonNull View cap) {
        cap.setTranslationX(0f);
        cap.setTranslationY(0f);
        cap.setScaleX(1f);
        cap.setScaleY(1f);
        cap.setAlpha(1f);
    }

    /** The configured rows, not the transposed ones. */
    public int rowCount() {
        return mMatrixRows;
    }

    /** The longest configured row: how many keys stand in a line when the page is vertical. */
    public int columnCount() {
        return mMatrixColumns;
    }

    public int keyCount() {
        return mCaps.size();
    }

    @Nullable
    public ExtraKeyButton keyAt(int childIndex) {
        return childIndex >= 0 && childIndex < mKeys.size() ? mKeys.get(childIndex) : null;
    }

    @Nullable
    public ExtraKeyCapView capAt(int childIndex) {
        return childIndex >= 0 && childIndex < mCaps.size() ? mCaps.get(childIndex) : null;
    }

    @Nullable
    public ExtraKeyCapView capForKey(@NonNull String key) {
        for (int i = 0; i < mCaps.size(); i++) {
            if (key.equals(mKeys.get(i).getKey())) return mCaps.get(i);
        }
        return null;
    }

    /**
     * Lay the keys out as a column: each configured row becomes a column and the keys in it run
     * top to bottom.
     */
    public void setVertical(boolean vertical) {
        if (mVertical == vertical) return;
        mVertical = vertical;
        requestLayout();
    }

    public boolean isVertical() {
        return mVertical;
    }

    public void setClient(@Nullable ExtraKeysClient client) {
        mClient = client;
    }

    /** False hands a hold to the host instead of repeating or locking; the bar sets it on a pill. */
    public void setHoldActsOnKey(boolean holdActsOnKey) {
        mHoldActsOnKey = holdActsOnKey;
    }

    // ---- Modifiers.

    @Nullable
    public ExtraKeyModifiers getModifiers() {
        return mModifiers;
    }

    public void setModifiers(@Nullable ExtraKeyModifiers modifiers) {
        if (modifiers == mModifiers) return;
        if (mAttached && mModifiers != null) mModifiers.removeListener(mModifierListener);
        mModifiers = modifiers;
        if (mAttached && modifiers != null) modifiers.addListener(mModifierListener);
        syncAllModifiers();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        if (mModifiers != null) mModifiers.addListener(mModifierListener);
        syncAllModifiers();
    }

    @Override
    protected void onDetachedFromWindow() {
        mAttached = false;
        endMorph();
        if (mModifiers != null) mModifiers.removeListener(mModifierListener);
        cancelPress();
        super.onDetachedFromWindow();
    }

    private void syncAllModifiers() {
        for (int i = 0; i < mCaps.size(); i++) {
            ExtraKeyCapView cap = mCaps.get(i);
            SpecialButton button = modifierOf(cap.key());
            cap.setModifierState(button == null || mModifiers == null
                ? ExtraKeyModifiers.State.OFF : mModifiers.state(button));
        }
    }

    private void syncModifier(SpecialButton button, ExtraKeyModifiers.State state) {
        for (int i = 0; i < mCaps.size(); i++) {
            ExtraKeyCapView cap = mCaps.get(i);
            if (modifierOf(cap.key()) == button) cap.setModifierState(state);
        }
    }

    @Nullable
    private static SpecialButton modifierOf(@NonNull ExtraKeyButton key) {
        return ExtraKeyModifiers.isModifierKey(key.getKey()) ? SpecialButton.valueOf(key.getKey()) : null;
    }

    private void announceModifier(@NonNull ExtraKeyButton key, @NonNull SpecialButton button) {
        AccessibilityManager am = (AccessibilityManager) getContext()
            .getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null || !am.isEnabled() || mModifiers == null) return;
        ExtraKeyModifiers.State state = mModifiers.state(button);
        CharSequence stateText = getResources().getText(
            state == ExtraKeyModifiers.State.OFF ? R.string.a11y_special_key_off
                : state == ExtraKeyModifiers.State.LOCKED ? R.string.a11y_special_key_latched_on
                : R.string.a11y_special_key_on);
        announceForAccessibility(key.getKey() + " " + stateText);
    }

    // ---- Colours.

    /**
     * Sets the page's colours. They apply to caps built or restyled afterwards: call
     * {@link #refreshKeyStyles()} to restate the ones already built.
     */
    public void setButtonColors(int buttonTextColor, int buttonActiveTextColor,
                                int buttonBackgroundColor, int buttonActiveBackgroundColor) {
        mButtonTextColor = buttonTextColor;
        mButtonActiveTextColor = buttonActiveTextColor;
        mButtonBackgroundColor = buttonBackgroundColor;
        mButtonActiveBackgroundColor = buttonActiveBackgroundColor;
    }

    public int getButtonTextColor() {
        return mButtonTextColor;
    }

    public void setButtonTextColor(int buttonTextColor) {
        mButtonTextColor = buttonTextColor;
    }

    public int getButtonActiveTextColor() {
        return mButtonActiveTextColor;
    }

    public int getButtonBackgroundColor() {
        return mButtonBackgroundColor;
    }

    /** Caps built later, and the ones already built, take the labels in this case. */
    public void setButtonTextAllCaps(boolean buttonTextAllCaps) {
        if (mButtonTextAllCaps == buttonTextAllCaps) return;
        mButtonTextAllCaps = buttonTextAllCaps;
        for (int i = 0; i < mCaps.size(); i++) {
            mCaps.get(i).setLabel(mKeys.get(i).getDisplay(), buttonTextAllCaps);
        }
    }

    /** Tint for the press glow; 0 falls back to the active text colour. */
    public void setKeyPressFeedbackColor(int color) {
        mKeyPressFeedbackColor = color;
        for (int i = 0; i < mCaps.size(); i++) applyAccent(mCaps.get(i));
    }

    /** Tells the glow whether a blur backdrop is softening it; without one it spreads wider. */
    public void setKeyPressFeedbackBlurAvailable(boolean available) {
        mKeyPressFeedbackBlurAvailable = available;
        for (int i = 0; i < mCaps.size(); i++) applyAccent(mCaps.get(i));
    }

    /**
     * The deck look: raised push-button keys on the slab the host wears. {@code on == false}
     * restores the normal look. {@code litInk} is accepted for the host's call shape; the ink of a locked
     * modifier is derived from the key's label ink.
     */
    public void setDeckLook(boolean on, int bed, int bedTop, int key, int keyTop, int keyFoot,
                            int lit, int litInk) {
        if (!on && !mDeckOn) return;
        mDeckOn = on;
        if (on) {
            mDeckKey = key;
            mDeckKeyTop = keyTop;
            mDeckKeyFoot = keyFoot;
            mDeckLit = lit;
        }
        restateEveryKey();
    }

    /** Re-reads every role from the theme and repaints, for a theme, scheme or palette change. */
    public void refreshKeyStyles() {
        mRoleColors.clear();
        restateEveryKey();
    }

    public void setKeyUsabilityPolicy(@Nullable KeyUsabilityPolicy policy) {
        mUsabilityPolicy = policy;
        restateEveryKey();
    }

    /** The key value of the page switcher, whose cap draws a page glyph and slides along its panel. */
    public static final String PAGE_SWITCHER_KEY =
        "tool:" + com.termux.launcherctl.LauncherToolRegistry.TOOL_APP_PAGE_SWITCHER;

    private static boolean isPageSwitcherKey(@NonNull ExtraKeyButton key) {
        return PAGE_SWITCHER_KEY.equals(key.getKey());
    }

    public void setKeyTouchOwner(@Nullable KeyTouchOwner owner) {
        mTouchOwner = owner;
    }

    /** Whether a page switcher key stands on this page. */
    public boolean hasPageSwitcherKey() {
        for (int i = 0; i < mKeys.size(); i++) if (isPageSwitcherKey(mKeys.get(i))) return true;
        return false;
    }

    /**
     * What the page switcher key's cap draws: the current page's glyph and number. Compared before
     * anything repaints; the caps built later take it too.
     */
    public void setPageSwitcherGlyph(@NonNull com.termux.app.wall.pageswitch.PageSwitchModel.Kind kind,
                                     int number) {
        for (int i = 0; i < mCaps.size(); i++) {
            if (isPageSwitcherKey(mKeys.get(i))) mCaps.get(i).setPageGlyph(kind, number);
        }
    }

    /** Whether a shown, usable page switcher cap covers the screen point; the owner's hit area. */
    public boolean pageSwitcherKeyContainsRaw(float rawX, float rawY) {
        if (!isShown()) return false;
        for (int i = 0; i < mCaps.size(); i++) {
            ExtraKeyCapView cap = mCaps.get(i);
            if (!isPageSwitcherKey(mKeys.get(i)) || cap.isDead()) continue;
            cap.getLocationOnScreen(mRawAt);
            if (rawX >= mRawAt[0] && rawX < mRawAt[0] + cap.getWidth()
                && rawY >= mRawAt[1] && rawY < mRawAt[1] + cap.getHeight()) return true;
        }
        return false;
    }

    public void setPlaceSwitchPolicy(@Nullable PlaceSwitchPolicy policy) {
        mPlaceSwitchPolicy = policy;
        restateEveryKey();
    }

    /** Setting a listener tells it the colours now; it is told again after every restatement. */
    public void setPlaceGlyphColorListener(@Nullable PlaceGlyphColorListener listener) {
        mPlaceGlyphColorListener = listener;
        publishPlaceGlyphColors();
    }

    private void restateEveryKey() {
        for (int i = 0; i < mCaps.size(); i++) styleCap(mCaps.get(i));
        publishPlaceGlyphColors();
    }

    private void publishPlaceGlyphColors() {
        if (mPlaceGlyphColorListener != null) {
            mPlaceGlyphColorListener.onPlaceGlyphColors(placeGlyphColors());
        }
    }

    private int accent() {
        return mKeyPressFeedbackColor != 0 ? mKeyPressFeedbackColor : mButtonActiveTextColor;
    }

    private void applyAccent(@NonNull ExtraKeyCapView cap) {
        int accent = accent();
        cap.setAccent(accent);
        cap.setGlowWide(!mKeyPressFeedbackBlurAvailable);
    }

    /** Black or white, whichever reads on {@code color}. */
    private static int readableOn(int color) {
        double luma = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color);
        return luma > 150 ? Color.BLACK : Color.WHITE;
    }

    private boolean isKeyUsable(@NonNull ExtraKeyButton info) {
        return mUsabilityPolicy == null || info.getKey() == null
            || mUsabilityPolicy.isKeyUsable(info.getKey());
    }

    @NonNull
    private PlaceFocus placeFocusOf(@NonNull ExtraKeyButton info) {
        if (mPlaceSwitchPolicy == null || info.getKey() == null) return PlaceFocus.NOT_A_PLACE;
        return mPlaceSwitchPolicy.placeFocusOf(info.getKey());
    }

    @Nullable
    private ExtraKeyColorRole roleFor(@NonNull ExtraKeyButton info) {
        ExtraKeyColorRole role = info.getColor();
        if (role == null && placeFocusOf(info) != PlaceFocus.NOT_A_PLACE) {
            return ExtraKeyColorRole.defaultForPlaceSwitch(info.getKey());
        }
        return role;
    }

    /** {@code {background, label}} for a role, resolved from the theme once and then remembered. */
    @NonNull
    private int[] roleColors(@NonNull ExtraKeyColorRole role) {
        int[] cached = mRoleColors.get(role);
        if (cached != null) return cached;
        int[] resolved = {role.background(getContext()), role.label(getContext())};
        mRoleColors.put(role, resolved);
        return resolved;
    }

    /** Decides one key's look. Runs when the key is built or restated, never on the draw path. */
    private void styleCap(@NonNull ExtraKeyCapView cap) {
        ExtraKeyButton info = cap.key();
        cap.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        applyAccent(cap);
        if (mDeckOn) {
            cap.setCapShape(DECK_INSET_DP * mDensity, DECK_INSET_DP * mDensity,
                DeckButtonDrawable.RADIUS_DP * mDensity);
        } else {
            cap.setCapShape(CAP_INSET_HORIZONTAL_DP * mDensity, CAP_INSET_VERTICAL_DP * mDensity,
                CAP_CORNER_RADIUS_DP * mDensity);
        }
        boolean usable = isKeyUsable(info);
        cap.setDead(!usable);
        if (!usable) {
            // Material's disabled look: the label at 38%, nothing behind it, nothing to press.
            cap.setLabelColor(withAlpha(mButtonTextColor, ExtraKeyCapView.DISABLED_LABEL_ALPHA));
            cap.setBackground(new ColorDrawable(mButtonBackgroundColor));
            return;
        }
        ExtraKeyColorRole role = roleFor(info);
        PlaceFocus placeFocus = placeFocusOf(info);
        if (placeFocus != PlaceFocus.NOT_A_PLACE) {
            // The place switches wear no cap: their role paints the glyph, full strength for the
            // place in front and held back for the ones behind it. The glyph takes the vivid
            // derivative of the role so three roles off one palette still read as three colours.
            int glyph = placeGlyphColor(cap);
            if (placeFocus == PlaceFocus.FOCUSED) {
                cap.setLabelColor(mDeckOn ? mDeckLit : glyph);
                if (PlaceSwitchGlyph.GLOW_RADIUS_DP > 0f && !mDeckOn) {
                    cap.setShadowLayer(PlaceSwitchGlyph.GLOW_RADIUS_DP * mDensity, 0f, 0f,
                        withAlpha(glyph, PlaceSwitchGlyph.GLOW_ALPHA));
                }
            } else {
                cap.setLabelColor(withAlpha(glyph, PlaceSwitchGlyph.UNFOCUSED_ALPHA));
            }
            if (mDeckOn) setDeckBackground(cap, placeFocus == PlaceFocus.FOCUSED);
            else cap.setBackground(new ColorDrawable(mButtonBackgroundColor));
            return;
        }
        if (role == null) {
            cap.setLabelColor(mButtonTextColor);
            if (mDeckOn) setDeckBackground(cap, false);
            else cap.setBackground(new ColorDrawable(mButtonBackgroundColor));
            return;
        }
        int[] colors = roleColors(role);
        cap.setLabelColor(colors[1]);
        if (mDeckOn) {
            setDeckBackground(cap, false);
            return;
        }
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(CAP_CORNER_RADIUS_DP * mDensity);
        shape.setColor(colors[0]);
        int insetH = Math.round(CAP_INSET_HORIZONTAL_DP * mDensity);
        int insetV = Math.round(CAP_INSET_VERTICAL_DP * mDensity);
        cap.setBackground(new InsetDrawable(shape, insetH, insetV, insetH, insetV));
    }

    private void setDeckBackground(@NonNull ExtraKeyCapView cap, boolean active) {
        cap.setBackground(new InsetDrawable(
            new DeckButtonDrawable(mDensity, mDeckKey, mDeckKeyTop, mDeckKeyFoot, mDeckLit, active),
            Math.round(DECK_INSET_DP * mDensity)));
    }

    /**
     * The vivid colour one place switch's glyph is painted in, read across the whole page because
     * the spread that keeps two switches on one hue apart is a property of the set.
     */
    private int placeGlyphColor(@NonNull ExtraKeyCapView cap) {
        int[] sources = new int[mCaps.size()];
        ExtraKeyCapView[] caps = new ExtraKeyCapView[mCaps.size()];
        int count = collectPlaceSwitches(sources, null, caps);
        int index = -1;
        for (int i = 0; i < count; i++) {
            if (caps[i] == cap) {
                index = i;
                break;
            }
        }
        boolean darkGlass = PlaceSwitchGlyph.isDarkGlass(mButtonTextColor);
        if (index < 0) {
            ExtraKeyColorRole role = roleFor(cap.key());
            return PlaceSwitchGlyph.vividFor(
                role == null ? mButtonTextColor : roleColors(role)[0], darkGlass);
        }
        return PlaceSwitchGlyph.vividRow(Arrays.copyOf(sources, count), darkGlass)[index];
    }

    private int collectPlaceSwitches(@NonNull int[] sources, @Nullable String[] keys,
                                     @Nullable ExtraKeyCapView[] caps) {
        int count = 0;
        for (int i = 0; i < mCaps.size(); i++) {
            ExtraKeyCapView other = mCaps.get(i);
            ExtraKeyButton info = other.key();
            if (placeFocusOf(info) == PlaceFocus.NOT_A_PLACE) continue;
            ExtraKeyColorRole role = roleFor(info);
            sources[count] = role == null ? mButtonTextColor : roleColors(role)[0];
            if (keys != null) keys[count] = info.getKey();
            if (caps != null) caps[count] = other;
            count++;
        }
        return count;
    }

    /** The colour each place switch paints its glyph in now, by the key value it sends. */
    @NonNull
    public Map<String, Integer> placeGlyphColors() {
        int[] sources = new int[mCaps.size()];
        String[] keys = new String[mCaps.size()];
        int count = collectPlaceSwitches(sources, keys, null);
        Map<String, Integer> colors = new LinkedHashMap<>();
        if (count == 0) return colors;
        int[] vivid = PlaceSwitchGlyph.vividRow(Arrays.copyOf(sources, count),
            PlaceSwitchGlyph.isDarkGlass(mButtonTextColor));
        for (int i = 0; i < count; i++) {
            if (keys[i] != null) colors.put(keys[i], vivid[i]);
        }
        return colors;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    // ---- Layout: equal cells, the rows filling the height.

    private int columns() {
        return mVertical ? mMatrixRows : mMatrixColumns;
    }

    private int rows() {
        return mVertical ? mMatrixColumns : mMatrixRows;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
            ? 0 : MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
            ? 0 : MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);
        int cols = columns();
        int rows = rows();
        for (int i = 0; i < mCaps.size(); i++) {
            ExtraKeyCapView cap = mCaps.get(i);
            int c = mVertical ? cap.mRow : cap.mColumn;
            int r = mVertical ? cap.mColumn : cap.mRow;
            cap.measure(
                MeasureSpec.makeMeasureSpec(edge(w, c + 1, cols) - edge(w, c, cols), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(edge(h, r + 1, rows) - edge(h, r, rows), MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        int cols = columns();
        int rows = rows();
        for (int i = 0; i < mCaps.size(); i++) {
            ExtraKeyCapView cap = mCaps.get(i);
            int c = mVertical ? cap.mRow : cap.mColumn;
            int row = mVertical ? cap.mColumn : cap.mRow;
            cap.layout(edge(w, c, cols), edge(h, row, rows), edge(w, c + 1, cols), edge(h, row + 1, rows));
        }
    }

    /** The start of cell {@code index} of {@code count} across {@code total} pixels, with no gaps. */
    private static int edge(int total, int index, int count) {
        return count <= 0 ? 0 : (int) ((long) total * index / count);
    }

    // ---- Touch.

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // The page takes every touch itself; the caps are labels.
        return ev.getActionMasked() == MotionEvent.ACTION_DOWN;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mOwnedCap != null && ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
            routeToOwner(ev);
            return true;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (beginOwned(ev)) return true;
                begin(ev.getX(), ev.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mActive != null) move(ev.getX(), ev.getY());
                return true;
            case MotionEvent.ACTION_UP:
                end();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelPress();
                return true;
            default:
                return true;
        }
    }

    @Nullable
    private ExtraKeyCapView capAtPoint(float x, float y) {
        for (int i = 0; i < mCaps.size(); i++) {
            ExtraKeyCapView cap = mCaps.get(i);
            if (x >= cap.getLeft() && x < cap.getRight() && y >= cap.getTop() && y < cap.getBottom()) {
                return cap;
            }
        }
        return null;
    }

    /**
     * A down on a key the owner holds: it takes this stream. The glow and the press haptic play
     * as for any key; no bubble, hold or repeat. The bar must not turn its page on the drag.
     */
    private boolean beginOwned(@NonNull MotionEvent ev) {
        KeyTouchOwner owner = mTouchOwner;
        if (owner == null) return false;
        cancelPress();
        ExtraKeyCapView cap = capAtPoint(ev.getX(), ev.getY());
        String value = cap == null ? null : cap.key().getKey();
        if (cap == null || value == null || cap.isDead() || !owner.ownsKey(value)
            || !owner.down(ev, cap)) return false;
        mOwnedCap = cap;
        cap.pressAt(ev.getX() - cap.getLeft(), ev.getY() - cap.getTop());
        pressHaptic(cap);
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        return true;
    }

    private void routeToOwner(@NonNull MotionEvent ev) {
        KeyTouchOwner owner = mTouchOwner;
        ExtraKeyCapView cap = mOwnedCap;
        if (owner == null || cap == null) return;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                owner.move(ev);
                break;
            case MotionEvent.ACTION_UP:
                mOwnedCap = null;
                cap.release();
                owner.up(ev);
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelPress();
                break;
            default:
                break;
        }
    }

    private void begin(float x, float y) {
        cancelPress();
        ExtraKeyCapView cap = capAtPoint(x, y);
        if (cap == null) return;
        boolean usable = !cap.isDead();
        mActive = cap;
        mDownX = x;
        mDownY = y;
        mTicked = false;
        mSwipeArmed = false;
        mPolicy = new ExtraKeyPressPolicy(
            ExtraKeyPressPolicy.kindOf(cap.key().getKey(), usable),
            usable && cap.key().getPopup() != null, mHoldActsOnKey);
        mPolicy.down();
        if (!usable) return;
        cap.pressAt(x - cap.getLeft(), y - cap.getTop());
        pressHaptic(cap);
        mHandler.postDelayed(mHoldRunnable, mLongPressMs);
    }

    private void move(float x, float y) {
        ExtraKeyCapView cap = mActive;
        ExtraKeyPressPolicy policy = mPolicy;
        if (cap == null || policy == null) return;
        float fire = ExtraKeyPressPolicy.SWIPE_FIRE_DP * mDensity;
        ExtraKeyPressPolicy.Move move = policy.move(x - mDownX, y - mDownY, mTouchSlop, fire);
        if (move == ExtraKeyPressPolicy.Move.PAGE_DRAG) {
            // The bar turns the page; a page on its own just lets go of the key.
            cancelPress();
        } else if (move == ExtraKeyPressPolicy.Move.SWIPE) {
            if (!mSwipeArmed) {
                mSwipeArmed = true;
                mHandler.removeCallbacks(mHoldRunnable);
                mHandler.removeCallbacks(mRepeatRunnable);
                ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                showBubble(cap, policy.swipeProgress(), mDownY - y);
            } else {
                updateBubble(policy.swipeProgress(), mDownY - y);
            }
            if (!mTicked && policy.swipeProgress() >= 1f) {
                mTicked = true;
                tick(cap);
            }
        }
    }

    private void end() {
        ExtraKeyCapView cap = mActive;
        ExtraKeyPressPolicy policy = mPolicy;
        stopTimers();
        dismissBubble();
        mActive = null;
        mPolicy = null;
        if (cap == null || policy == null) return;
        ExtraKeyPressPolicy.Action action = policy.up();
        cap.release();
        switch (action) {
            case FIRE_PRIMARY:
                send(cap, cap.key());
                break;
            case FIRE_SECOND:
                tapKey(cap, cap.key().getPopup());
                break;
            case MODIFIER_TAP:
                tapKey(cap, cap.key());
                break;
            default:
                break;
        }
    }

    /** A tap of {@code key}: a modifier arms or lets go, any other key is sent once. */
    private void tapKey(@NonNull ExtraKeyCapView cap, @Nullable ExtraKeyButton key) {
        if (key == null) return;
        SpecialButton button = modifierOf(key);
        if (button != null) {
            if (mModifiers != null) {
                mModifiers.tap(button);
                announceModifier(key, button);
            }
            return;
        }
        send(cap, key);
    }

    /** An accessibility click on a cap does what a touch tap does. */
    private void performAccessibilityTap(@NonNull ExtraKeyCapView cap) {
        if (cap.isDead()) return;
        tapKey(cap, cap.key());
    }

    /** An accessibility long click locks a modifier, as a hold does; other keys have no hold. */
    private boolean performAccessibilityHold(@NonNull ExtraKeyCapView cap) {
        SpecialButton button = cap.isDead() ? null : modifierOf(cap.key());
        if (button == null || mModifiers == null || !mHoldActsOnKey) return false;
        mModifiers.hold(button);
        announceModifier(cap.key(), button);
        return true;
    }

    /** Stops the timers, lets the glow fade, hides the bubble and fires nothing. */
    private void cancelPress() {
        ExtraKeyCapView owned = mOwnedCap;
        if (owned != null) {
            mOwnedCap = null;
            owned.release();
            if (mTouchOwner != null) mTouchOwner.cancel();
        }
        stopTimers();
        dismissBubble();
        if (mActive != null) mActive.release();
        if (mPolicy != null) mPolicy.cancel();
        mActive = null;
        mPolicy = null;
    }

    private void stopTimers() {
        mHandler.removeCallbacks(mHoldRunnable);
        mHandler.removeCallbacks(mRepeatRunnable);
    }

    private void onHold() {
        ExtraKeyCapView cap = mActive;
        ExtraKeyPressPolicy policy = mPolicy;
        if (cap == null || policy == null) return;
        switch (policy.holdElapsed()) {
            case REPEAT_FIRE:
                fireRepeat(cap);
                break;
            case MODIFIER_HOLD: {
                SpecialButton button = modifierOf(cap.key());
                if (button != null && mModifiers != null) {
                    mModifiers.hold(button);
                    announceModifier(cap.key(), button);
                }
                break;
            }
            default:
                break;
        }
    }

    private void onRepeat() {
        ExtraKeyCapView cap = mActive;
        ExtraKeyPressPolicy policy = mPolicy;
        if (cap == null || policy == null) return;
        if (policy.repeatTick() == ExtraKeyPressPolicy.Action.REPEAT_FIRE) fireRepeat(cap);
    }

    private void fireRepeat(@NonNull ExtraKeyCapView cap) {
        send(cap, cap.key());
        cap.pulse();
        tick(cap);
        mHandler.postDelayed(mRepeatRunnable, ExtraKeyPressPolicy.REPEAT_INTERVAL_MS);
    }

    private void send(@NonNull ExtraKeyCapView cap, @Nullable ExtraKeyButton key) {
        if (key == null || mClient == null) return;
        mClient.onExtraKeyClick(cap, key);
    }

    // ---- Haptics.

    private void pressHaptic(@NonNull ExtraKeyCapView cap) {
        if (mClient != null && mClient.performExtraKeyHapticFeedback(cap, cap.key())) return;
        if (systemHapticsOn()) cap.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void tick(@NonNull ExtraKeyCapView cap) {
        if (systemHapticsOn()) cap.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private boolean systemHapticsOn() {
        try {
            if (Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) == 0) {
                return false;
            }
            // Below 28, no haptics in total silence mode.
            return Build.VERSION.SDK_INT >= 28
                || Settings.Global.getInt(getContext().getContentResolver(), "zen_mode", 0) != 2;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- The swipe-up bubble.

    private void buildBubble() {
        Context context = getContext();
        int w = Math.round(BUBBLE_WIDTH_DP * mDensity);
        int h = Math.round(BUBBLE_HEIGHT_DP * mDensity);
        int lift = Math.round(ExtraKeyGesturePolicy.BUBBLE_MAX_LIFT_DP * mDensity / 2f);
        mBubbleBackground = new GradientDrawable();
        mBubbleBackground.setCornerRadius(BUBBLE_RADIUS_DP * mDensity);
        mBubbleText = new TextView(context);
        mBubbleText.setGravity(Gravity.CENTER);
        mBubbleText.setSingleLine(true);
        mBubbleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, BUBBLE_TEXT_SP);
        mBubbleText.setBackground(mBubbleBackground);
        mBubbleRoot = new FrameLayout(context);
        // The window is as tall as the bubble plus its largest lift; the bubble rises inside it.
        mBubbleRoot.addView(mBubbleText, new FrameLayout.LayoutParams(w, h,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        mBubble = new PopupWindow(mBubbleRoot, w, h + lift);
        mBubble.setTouchable(false);
        mBubble.setFocusable(false);
        mBubble.setClippingEnabled(false);
        mBubble.setBackgroundDrawable(null);
        mBubbleText.setPivotX(w / 2f);
        mBubbleText.setPivotY(h);
    }

    private void showBubble(@NonNull ExtraKeyCapView cap, float progress, float upPx) {
        ExtraKeyButton second = cap.key().getPopup();
        if (second == null) return;
        if (mBubble == null) buildBubble();
        if (mBubble == null || mBubbleText == null || mBubbleBackground == null) return;
        int accent = accent();
        mBubbleBackground.setColor(accent);
        mBubbleText.setTextColor(readableOn(accent));
        ExtraKeyCapView.applyLabel(mBubbleText, second.getDisplay(), mButtonTextAllCaps);
        updateBubble(progress, upPx);
        if (!isAttachedToWindow()) return;
        try {
            cap.getLocationOnScreen(mLocation);
            int x = mLocation[0] + cap.getWidth() / 2 - mBubble.getWidth() / 2;
            int y = mLocation[1] - mBubble.getHeight() - Math.round(BUBBLE_GAP_DP * mDensity);
            mBubble.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
            mBubbleShown = true;
        } catch (RuntimeException e) {
            // A window that cannot be added is a bubble that does not show, not a crash.
            mBubbleShown = false;
        }
    }

    private void updateBubble(float progress, float upPx) {
        if (mBubbleText == null) return;
        mBubbleText.setAlpha(ExtraKeyGesturePolicy.bubbleAlpha(progress));
        mBubbleText.setScaleX(ExtraKeyGesturePolicy.bubbleScale(progress));
        mBubbleText.setScaleY(ExtraKeyGesturePolicy.bubbleScale(progress));
        mBubbleText.setTranslationY(-ExtraKeyGesturePolicy.bubbleLiftPx(upPx,
            ExtraKeyGesturePolicy.BUBBLE_MAX_LIFT_DP * mDensity));
    }

    private void dismissBubble() {
        mBubbleShown = false;
        if (mBubble != null && mBubble.isShowing()) mBubble.dismiss();
    }

    /** Lets go of any press in progress: the glow fades and the bubble goes. */
    public void dismissPopup() {
        cancelPress();
    }
}
