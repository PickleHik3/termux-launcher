package com.termux.app.firstrun;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.FocusOutlineRenderer;
import com.termux.app.chrome.ActionButtonRow;
import com.termux.app.notice.TerminalDress;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws {@link TaiWelcomeCard}: the header, a row per download (glyph, words, checkbox), the
 * Wi-Fi-only switch, Later and "Download selected". It decides nothing: ticks live here only as
 * the set of row ids the user has toggled, and every number and state comes from the pure class.
 *
 * <p>It wears the same dress as {@link FirstRunPermissionsCardView}, and takes every touch while
 * it is up. {@link TaiWelcomeCardHost} puts it on screen and does the downloading.
 */
public final class TaiWelcomeCardView extends FrameLayout {

    /** What the card's buttons mean; the host closes the card and queues the downloads. */
    public interface Callbacks {
        /** Later: close, and never raise the card on its own again. */
        void onWelcomeLater();

        /** Download selected: the ticked row ids and the Wi-Fi-only switch as it stands. */
        void onWelcomeDownload(@NonNull Set<String> tickedRowIds, boolean wifiOnly);
    }

    private static final long CARD_IN_MS = 220L;
    private static final float CARD_RISE_DP = 10f;
    private static final float CARD_MAX_WIDTH_DP = 360f;
    private static final float CARD_SIDE_MARGIN_DP = 20f;
    private static final int SCRIM_ALPHA = 140;
    private static final float GLYPH_WELL_DP = 40f;

    private final float mDensity;
    private final TerminalDress mDress;
    private final int mAccent;
    private final LinearLayout mCard;
    private final LinearLayout mRows;
    private final TextView mHeader;
    private final TextView mModelCentreLine;
    private final TextView mFooter;
    private final TextView mFooterReason;
    private final MaterialSwitch mWifiOnly;
    private final MaterialButton mDownload;

    @Nullable private Callbacks mCallbacks;
    @Nullable private ValueAnimator mCardIn;
    private List<TaiWelcomeCard.Row> mModel = java.util.Collections.emptyList();
    private final LinkedHashSet<String> mTicked = new LinkedHashSet<>();
    private long mFreeBytes;

    public TaiWelcomeCardView(@NonNull Context context) {
        super(context);
        setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
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
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setTextColor(mDress.textColor);
        title.setText(R.string.tai_welcome_title);
        mCard.addView(title, matchWrap());

        mHeader = secondaryText(13f);
        LinearLayout.LayoutParams headerParams = matchWrap();
        headerParams.topMargin = dp(4);
        mCard.addView(mHeader, headerParams);

        mRows = new LinearLayout(context);
        mRows.setOrientation(LinearLayout.VERTICAL);
        // Six rows with their lines are taller than a short phone at a large font scale: the rows
        // scroll inside the card so the footer and the buttons stay reachable.
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.addView(mRows, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = matchWrap();
        scrollParams.topMargin = dp(10);
        mCard.addView(scroll, scrollParams);

        mModelCentreLine = secondaryText(12f);
        mModelCentreLine.setText(R.string.tai_welcome_model_centre_line);
        LinearLayout.LayoutParams centreParams = matchWrap();
        centreParams.topMargin = dp(8);
        mCard.addView(mModelCentreLine, centreParams);

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        mFooter = secondaryText(13f);
        mFooter.setTextColor(mDress.textColor);
        footer.addView(mFooter, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mWifiOnly = new MaterialSwitch(context);
        mWifiOnly.setText(R.string.tai_welcome_wifi_only);
        mWifiOnly.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        mWifiOnly.setTextColor(mDress.textColor);
        mWifiOnly.setChecked(true);
        footer.addView(mWifiOnly, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams footerParams = matchWrap();
        footerParams.topMargin = dp(10);
        mCard.addView(footer, footerParams);

        mFooterReason = secondaryText(12f);
        mFooterReason.setTextColor(mAccent);
        mCard.addView(mFooterReason, matchWrap());

        // Later and Download selected share a row while they fit, and stack with Download on top
        // when a narrow phone or a large font scale says they do not.
        ActionButtonRow buttons = new ActionButtonRow(context);
        MaterialButton later = new MaterialButton(context, null,
            androidx.appcompat.R.attr.borderlessButtonStyle);
        later.setText(R.string.tai_welcome_later);
        later.setAllCaps(false);
        later.setOnClickListener(view -> {
            if (mCallbacks != null) mCallbacks.onWelcomeLater();
        });
        buttons.addView(later);
        mDownload = new MaterialButton(context);
        mDownload.setText(R.string.tai_welcome_download);
        mDownload.setAllCaps(false);
        mDownload.setOnClickListener(view -> {
            if (mCallbacks != null) mCallbacks.onWelcomeDownload(new LinkedHashSet<>(mTicked), mWifiOnly.isChecked());
        });
        buttons.addView(mDownload);
        LinearLayout.LayoutParams buttonsParams = matchWrap();
        buttonsParams.topMargin = dp(6);
        mCard.addView(buttons, buttonsParams);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        cardParams.leftMargin = dp(CARD_SIDE_MARGIN_DP);
        cardParams.rightMargin = dp(CARD_SIDE_MARGIN_DP);
        addView(mCard, cardParams);
    }

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

    /** The Wi-Fi-only switch as it was left last time; on by default. */
    public void setWifiOnly(boolean wifiOnly) {
        mWifiOnly.setChecked(wifiOnly);
    }

    /**
     * Fills the card: the header, the rows with the policy's ticks, the footer for the storage
     * as it stands now.
     */
    public void bind(@NonNull TaiWelcomeCard.Header header, @NonNull List<TaiWelcomeCard.Row> rows,
                     boolean showModelCentreLine, long freeBytes) {
        mModel = rows;
        mFreeBytes = freeBytes;
        mTicked.clear();
        mTicked.addAll(TaiWelcomeCard.initialTicks(rows));
        mHeader.setText(header.chip.isEmpty()
            ? getContext().getString(R.string.tai_welcome_header_no_chip,
                header.tierNumber, header.ramGb, header.androidVersion)
            : getContext().getString(R.string.tai_welcome_header,
                header.tierNumber, header.ramGb, header.chip, header.androidVersion));
        mModelCentreLine.setVisibility(showModelCentreLine ? VISIBLE : GONE);
        mRows.removeAllViews();
        boolean first = true;
        for (TaiWelcomeCard.Row row : rows) {
            mRows.addView(rowView(row, first));
            first = false;
        }
        refreshFooter();
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

    private void refreshFooter() {
        TaiWelcomeCard.Footer footer = TaiWelcomeCard.footer(mModel, mTicked, mFreeBytes);
        mFooter.setText(getContext().getString(R.string.tai_welcome_footer, footer.selectedText, footer.freeText));
        mDownload.setEnabled(footer.downloadEnabled);
        if (footer.disabledReasonRes != 0) {
            mFooterReason.setText(footer.disabledReasonRes);
            mFooterReason.setVisibility(VISIBLE);
        } else {
            mFooterReason.setVisibility(GONE);
        }
    }

    /**
     * One row: the glyph in its well, the title with its model and the lines, and at the end the
     * checkbox over the size (or "Installed").
     */
    @NonNull
    private View rowView(@NonNull TaiWelcomeCard.Row row, boolean first) {
        Context context = getContext();
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams lineParams = matchWrap();
        if (!first) lineParams.topMargin = dp(10);
        line.setLayoutParams(lineParams);

        ImageView glyph = new ImageView(context);
        glyph.setImageResource(row.glyphRes);
        glyph.setImageTintList(ColorStateList.valueOf(row.installed
            ? ColorUtils.setAlphaComponent(mDress.textColor, 200) : mAccent));
        glyph.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        glyph.setPadding(dp(9), dp(9), dp(9), dp(9));
        glyph.setBackground(glyphWell());
        glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(dp(GLYPH_WELL_DP), dp(GLYPH_WELL_DP));
        glyphParams.topMargin = dp(8);
        line.addView(glyph, glyphParams);

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(0, dp(8), 0, 0);

        TextView heading = new TextView(context);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setTextColor(mDress.textColor);
        heading.setText(row.titleRes);
        words.addView(heading, matchWrap());

        TextView model = secondaryText(12f);
        model.setText(row.modelNames);
        words.addView(model, matchWrap());

        TextView plain = secondaryText(12f);
        plain.setText(row.lineRes);
        words.addView(plain, matchWrap());

        if (row.warnsBackground) {
            TextView warning = secondaryText(12f);
            warning.setTextColor(mAccent);
            warning.setText(R.string.tai_welcome_background_warning);
            words.addView(warning, matchWrap());
        }
        LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wordsParams.setMarginStart(dp(14));
        line.addView(words, wordsParams);

        LinearLayout end = new LinearLayout(context);
        end.setOrientation(LinearLayout.VERTICAL);
        end.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView size = secondaryText(12f);
        size.setGravity(Gravity.CENTER_HORIZONTAL);
        if (row.installed) {
            size.setPadding(dp(8), dp(8), 0, 0);
            size.setText(R.string.tai_welcome_installed);
        } else {
            MaterialCheckBox box = new MaterialCheckBox(context);
            box.setChecked(row.ticked);
            box.setContentDescription(context.getString(row.titleRes));
            box.setOnCheckedChangeListener((button, checked) -> {
                if (checked) mTicked.add(row.id);
                else mTicked.remove(row.id);
                refreshFooter();
            });
            end.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            size.setPadding(dp(8), 0, 0, 0);
            size.setText(row.sizeText);
            // The whole text block toggles too: a 48 dp box is the target, but nobody aims at it.
            words.setOnClickListener(view -> box.toggle());
        }
        end.addView(size, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        endParams.setMarginStart(dp(4));
        line.addView(end, endParams);
        return line;
    }

    /** The rounded square a glyph sits in, tinted from the dress so it reads on glass and solid alike. */
    @NonNull
    private GradientDrawable glyphWell() {
        GradientDrawable well = new GradientDrawable();
        well.setCornerRadius(dp(11));
        well.setColor(ColorUtils.setAlphaComponent(mDress.textColor, 20));
        well.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(mDress.textColor, 36));
        return well;
    }

    @NonNull
    private TextView secondaryText(float sp) {
        TextView text = new TextView(getContext());
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTextColor(ColorUtils.setAlphaComponent(mDress.textColor, 180));
        text.setLineSpacing(dp(1), 1f);
        return text;
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
