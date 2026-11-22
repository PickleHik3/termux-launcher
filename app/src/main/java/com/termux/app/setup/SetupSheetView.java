package com.termux.app.setup;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.chrome.ShapeTokens;

import java.util.Locale;

/**
 * The wizard's card: a fixed-height bottom sheet with the stepper, the section's meta line, the
 * section's content and the footer buttons. No drag detents; the controller sets its height.
 */
final class SetupSheetView extends LinearLayout {

    interface Callbacks {
        void onBack();
        void onSkip();
        void onContinue();
        void onKeepCurrent();
        void onStepTapped(int index);
    }

    private static final float SHEET_RADIUS_DP = 26f;

    private final SetupStepper mStepper;
    private final TextView mMeta;
    private final ScrollView mScroll;
    private final ImageButton mBack;
    private final MaterialButton mContinue;

    SetupSheetView(@NonNull Context context, boolean updater, @NonNull Callbacks callbacks) {
        super(context);
        setOrientation(VERTICAL);
        setClickable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(ColorUtils.setAlphaComponent(SetupUi.surface(this), 255));
        float radius = SetupUi.dp(context, SHEET_RADIUS_DP);
        background.setCornerRadii(new float[] {radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        setBackground(background);
        setElevation(ShapeTokens.elevationPx(context, 3));
        int side = SetupUi.dp(context, 20);
        setPadding(side, SetupUi.dp(context, 8), side, SetupUi.dp(context, 8));

        // The drag handle is decoration: the sheet has one height.
        View handle = new View(context);
        GradientDrawable line = new GradientDrawable();
        line.setCornerRadius(SetupUi.dp(context, 2));
        line.setColor(ColorUtils.setAlphaComponent(SetupUi.onSurface(this), 70));
        handle.setBackground(line);
        LayoutParams handleParams = new LayoutParams(SetupUi.dp(context, 36), SetupUi.dp(context, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        addView(handle, handleParams);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        mStepper = new SetupStepper(context);
        mStepper.setListener(callbacks::onStepTapped);
        header.addView(mStepper, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        if (updater) {
            MaterialButton keep = new MaterialButton(context, null,
                androidx.appcompat.R.attr.borderlessButtonStyle);
            keep.setText(R.string.setup_keep_current);
            keep.setAllCaps(false);
            keep.setOnClickListener(v -> callbacks.onKeepCurrent());
            header.addView(keep, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        }
        addView(header, SetupUi.matchWrap(SetupUi.dp(context, 4)));

        mMeta = SetupUi.text(context, 11.5f, false, SetupUi.onSurfaceVariant(this));
        mMeta.setTypeface(Typeface.MONOSPACE);
        mMeta.setLetterSpacing(0.08f);
        mMeta.setSingleLine(true);
        addView(mMeta, SetupUi.matchWrap(SetupUi.dp(context, 4)));

        mScroll = new ScrollView(context);
        mScroll.setFillViewport(false);
        mScroll.setVerticalScrollBarEnabled(false);
        addView(mScroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        ((LayoutParams) mScroll.getLayoutParams()).topMargin = SetupUi.dp(context, 10);

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        mBack = new ImageButton(context);
        mBack.setImageResource(R.drawable.ic_symbol_arrow_back);
        mBack.setContentDescription(context.getString(R.string.setup_back));
        mBack.setBackground(null);
        mBack.setColorFilter(SetupUi.onSurface(this));
        mBack.setOnClickListener(v -> callbacks.onBack());
        footer.addView(mBack, new LayoutParams(SetupUi.dp(context, 48), SetupUi.dp(context, 48)));
        View spacer = new View(context);
        footer.addView(spacer, new LayoutParams(0, 1, 1f));
        MaterialButton skip = new MaterialButton(context, null,
            androidx.appcompat.R.attr.borderlessButtonStyle);
        skip.setText(R.string.setup_skip);
        skip.setAllCaps(false);
        skip.setOnClickListener(v -> callbacks.onSkip());
        footer.addView(skip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        mContinue = new MaterialButton(context);
        mContinue.setAllCaps(false);
        mContinue.setOnClickListener(v -> callbacks.onContinue());
        LayoutParams continueParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT);
        continueParams.leftMargin = SetupUi.dp(context, 8);
        footer.addView(mContinue, continueParams);
        addView(footer, SetupUi.matchWrap(SetupUi.dp(context, 8)));
    }

    /** Shows one section: the stepper, the meta line, the content and the footer for it. */
    void bind(int index, int count, int furthest, @NonNull String sectionName,
              @NonNull View content, boolean last, boolean canGoBack) {
        mStepper.setSteps(count, index, furthest);
        mMeta.setText(String.format(Locale.ROOT, "%02d / %02d  %s", index + 1, count,
            sectionName.toUpperCase(Locale.ROOT)));
        mScroll.removeAllViews();
        mScroll.addView(content, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mScroll.scrollTo(0, 0);
        mBack.setVisibility(canGoBack ? VISIBLE : INVISIBLE);
        mContinue.setText(last ? R.string.setup_finish : R.string.setup_continue);
    }

    /** Pads the card's foot by the navigation bar's height. */
    void setNavInset(int px) {
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
            SetupUi.dp(getContext(), 8) + px);
    }
}
