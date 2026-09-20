package com.termux.app.firstrun;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.FocusOutlineRenderer;
import com.termux.app.notice.TerminalDress;

import java.util.List;

/**
 * The first thing a fresh install shows: one card, a row per thing the launcher would like, and a
 * Continue.
 *
 * <p>It wears the same dress as the tour's own cards — the terminal's fill, hairline and radius,
 * flat — so the run that follows it opens on the same material. It is not a tour card, though: it
 * is shown before the tour is even offered, it has to come up on an update where no tour is
 * running at all, and its rows carry controls rather than a gesture, so it lives on its own rather
 * than inside {@code TourOverlayView}'s step shell.
 *
 * <p>Unlike the tour overlay this one does take touches, and takes all of them: it is a modal step
 * of the setup, and the chrome underneath it must not be operable while it is up.
 */
public final class FirstRunPermissionsCardView extends FrameLayout {

    /** What the card's controls mean; the activity does the asking. */
    public interface Callbacks {
        /** The Allow button on a permission row was tapped. */
        void onFirstRunPermissionTapped(@NonNull FirstRunPermissionsCard.Item item);

        /** The Linux display switch was moved. */
        void onFirstRunDisplayToggled(boolean enabled);

        /** Continue: the card is done with, whatever the user granted. */
        void onFirstRunContinueTapped();
    }

    private static final long CARD_IN_MS = 220L;
    private static final float CARD_RISE_DP = 10f;
    private static final float CARD_MAX_WIDTH_DP = 340f;
    private static final float CARD_SIDE_MARGIN_DP = 20f;
    /** The scrim over the live chrome: enough to say the card is the only thing to answer. */
    private static final int SCRIM_ALPHA = 140;

    private final float mDensity;
    private final TerminalDress mDress;
    private final int mAccent;
    private final LinearLayout mCard;
    private final LinearLayout mRows;
    private final TextView mContinue;

    @Nullable private Callbacks mCallbacks;
    @Nullable private ValueAnimator mCardIn;
    /** Set while the rows are being bound, so restoring a switch does not report a user move. */
    private boolean mBinding;

    public FirstRunPermissionsCardView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mDress = TerminalDress.stored(context);
        mAccent = FocusOutlineRenderer.resolveAccent(this);
        setBackgroundColor(ColorUtils.setAlphaComponent(Color.BLACK, SCRIM_ALPHA));
        setClickable(true);
        setFocusable(true);

        mCard = new LinearLayout(context);
        mCard.setOrientation(LinearLayout.VERTICAL);
        mCard.setBackground(mDress.background(0));
        mCard.setPadding(dp(18), dp(16), dp(18), dp(10));

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
            android.graphics.Typeface.BOLD));
        title.setTextColor(mDress.textColor);
        title.setText(R.string.first_run_permissions_title);
        mCard.addView(title, matchWrap());

        TextView intro = new TextView(context);
        intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        intro.setTextColor(ColorUtils.setAlphaComponent(mDress.textColor, 204));
        intro.setLineSpacing(dp(2), 1f);
        intro.setText(R.string.first_run_permissions_intro);
        LinearLayout.LayoutParams introParams = matchWrap();
        introParams.topMargin = dp(4);
        mCard.addView(intro, introParams);

        mRows = new LinearLayout(context);
        mRows.setOrientation(LinearLayout.VERTICAL);
        // Three rows at a large font scale on a short phone is taller than the screen; the rows
        // scroll inside the card rather than pushing Continue off the bottom of it.
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.addView(mRows, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // Wrap, not weight: the card itself wraps its content, so a weighted child would measure
        // to nothing. The scroll view takes the at-most height the card can spare and keeps the
        // rest inside itself.
        LinearLayout.LayoutParams scrollParams = matchWrap();
        scrollParams.topMargin = dp(10);
        mCard.addView(scroll, scrollParams);

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        mContinue = textButton(context, view -> {
            if (mCallbacks != null) mCallbacks.onFirstRunContinueTapped();
        });
        mContinue.setText(R.string.first_run_permissions_continue);
        mContinue.setMinHeight(dp(48));
        mContinue.setMinWidth(dp(48));
        buttonRow.addView(mContinue, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams buttonRowParams = matchWrap();
        buttonRowParams.topMargin = dp(6);
        buttonRowParams.rightMargin = -dp(4);
        mCard.addView(buttonRow, buttonRowParams);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        cardParams.leftMargin = dp(CARD_SIDE_MARGIN_DP);
        cardParams.rightMargin = dp(CARD_SIDE_MARGIN_DP);
        addView(mCard, cardParams);
    }

    /** The layout params the activity adds this view to its content view with. */
    @NonNull
    public static FrameLayout.LayoutParams buildLayoutParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    /** The system bars' keep-out, so the card never rests under the status or gesture bar. */
    public void setSystemBarInsets(int top, int bottom) {
        setPadding(0, Math.max(0, top), 0, Math.max(0, bottom));
    }

    /** Builds the rows as they stand right now; called again after every answer. */
    public void bind(@NonNull List<FirstRunPermissionsCard.Row> rows) {
        mBinding = true;
        mRows.removeAllViews();
        boolean first = true;
        for (FirstRunPermissionsCard.Row row : rows) {
            mRows.addView(rowView(row, first));
            first = false;
        }
        mBinding = false;
        // The card is as wide as the screen allows, up to the width a sentence reads well at.
        requestLayout();
    }

    /** Raises the card, once. */
    public void animateIn() {
        if (mCardIn != null) mCardIn.cancel();
        mCard.setAlpha(0f);
        mCard.setTranslationY(dp(CARD_RISE_DP));
        mCardIn = ValueAnimator.ofFloat(0f, 1f);
        mCardIn.setDuration(CARD_IN_MS);
        mCardIn.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        mCardIn.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            mCard.setAlpha(t);
            mCard.setTranslationY(dp(CARD_RISE_DP) * (1f - t));
        });
        mCardIn.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mCardIn != null) {
            mCardIn.cancel();
            mCardIn = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = MeasureSpec.getSize(widthMeasureSpec) - 2 * dp(CARD_SIDE_MARGIN_DP);
        int max = dp(CARD_MAX_WIDTH_DP);
        ViewGroup.LayoutParams params = mCard.getLayoutParams();
        int wanted = available > max ? max : ViewGroup.LayoutParams.MATCH_PARENT;
        if (params.width != wanted) {
            params.width = wanted;
            mCard.setLayoutParams(params);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** Every touch stops here: the chrome below is not operable while the card is up. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    /** One row: a title, a sentence, and a control on the trailing edge. */
    @NonNull
    private View rowView(@NonNull FirstRunPermissionsCard.Row row, boolean first) {
        Context context = getContext();
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lineParams = matchWrap();
        if (!first) lineParams.topMargin = dp(14);
        line.setLayoutParams(lineParams);

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);

        TextView heading = new TextView(context);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
            android.graphics.Typeface.NORMAL));
        heading.setTextColor(mDress.textColor);
        heading.setText(row.titleRes);
        words.addView(heading, matchWrap());

        TextView copy = new TextView(context);
        copy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        copy.setTextColor(ColorUtils.setAlphaComponent(mDress.textColor, 180));
        copy.setLineSpacing(dp(1), 1f);
        copy.setText(row.copyRes);
        LinearLayout.LayoutParams copyParams = matchWrap();
        copyParams.topMargin = dp(1);
        words.addView(copy, copyParams);

        line.addView(words, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        line.addView(controlFor(row), controlParams());
        return line;
    }

    /** The row's control: a switch, a button to ask with, or the word for an answer given. */
    @NonNull
    private View controlFor(@NonNull FirstRunPermissionsCard.Row row) {
        Context context = getContext();
        if (row.isSwitch) {
            MaterialSwitch toggle = new MaterialSwitch(context);
            toggle.setChecked(row.isOn());
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (mBinding || mCallbacks == null) return;
                mCallbacks.onFirstRunDisplayToggled(checked);
            });
            return toggle;
        }
        if (row.hasButton()) {
            TextView button = textButton(context, view -> {
                if (mCallbacks != null) mCallbacks.onFirstRunPermissionTapped(row.item);
            });
            button.setText(row.buttonRes());
            button.setContentDescription(context.getString(row.buttonRes()));
            button.setMinHeight(dp(48));
            button.setMinWidth(dp(48));
            // A refused row keeps its button — a second tap asks again — and says so beside it.
            if (row.statusRes() != 0) {
                LinearLayout stack = new LinearLayout(context);
                stack.setOrientation(LinearLayout.HORIZONTAL);
                stack.setGravity(Gravity.CENTER_VERTICAL);
                stack.addView(statusLabel(row.statusRes()));
                stack.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return stack;
            }
            return button;
        }
        return statusLabel(row.statusRes());
    }

    @NonNull
    private TextView statusLabel(int statusRes) {
        TextView status = new TextView(getContext());
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        status.setTextColor(ColorUtils.setAlphaComponent(mDress.textColor, 160));
        status.setPadding(dp(8), dp(6), dp(8), dp(6));
        if (statusRes != 0) status.setText(statusRes);
        return status;
    }

    @NonNull
    private LinearLayout.LayoutParams controlParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(8);
        return params;
    }

    @NonNull
    private TextView textButton(@NonNull Context context, @NonNull OnClickListener onClick) {
        TextView button = new TextView(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        button.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
            android.graphics.Typeface.NORMAL));
        button.setTextColor(mAccent);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        button.setBackground(buttonBackground());
        button.setOnClickListener(onClick);
        return button;
    }

    @NonNull
    private GradientDrawable buttonBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(8));
        background.setColor(ColorUtils.setAlphaComponent(mAccent, 28));
        return background;
    }

    @NonNull
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }
}
