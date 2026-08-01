package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renders the pinned essential notifications inside the widget slot. One or two matches keep a full
 * card each; three collapse to a header row plus three single-line rows, which is the ceiling the
 * 68dp slot allows.
 *
 * <p>In the three-row form the header rule starts after the mono chip clock, which the slot reports
 * through {@link #setHeaderInsetStart(float)}.
 */
public final class PinnedNotificationsView extends View {

    public interface DismissListener {
        void onDismissPinned(@NonNull PinnedNotification notification);
    }

    /** Card height for the contention layout, where the media strip takes the rest of the slot. */
    public static final float CONTENTION_CARD_HEIGHT_DP = 40f;
    private static final float HEADER_HEIGHT_DP = 14f;
    private static final float STACK_ROW_HEIGHT_DP = 16f;

    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final List<Rect> mDismissRects = new ArrayList<>();
    private final PinnedNotificationIconCache mIcons;

    private List<PinnedNotification> mItems = Collections.emptyList();
    @Nullable private DismissListener mListener;
    private float mHeaderInsetStart;
    private boolean mCompactCard;
    private int mPressedIndex = -1;

    private int mOnSurface;
    private int mOnSurfaceVariant;
    private int mTertiary;

    public PinnedNotificationsView(Context context) {
        this(context, null);
    }

    public PinnedNotificationsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(false);
        mIcons = new PinnedNotificationIconCache(context);
        resolveColors();
    }

    private void resolveColors() {
        Context context = getContext();
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mOnSurfaceVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, mOnSurface);
        mTertiary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary,
            MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
                ContextCompat.getColor(context, R.color.termux_primary)));
    }

    public void setListener(@Nullable DismissListener listener) {
        mListener = listener;
    }

    public void setItems(@NonNull List<PinnedNotification> items) {
        mItems = items.size() > TopPaneSlotMode.MAX_PINNED
            ? new ArrayList<>(items.subList(0, TopPaneSlotMode.MAX_PINNED))
            : items;
        mPressedIndex = -1;
        invalidate();
    }

    @NonNull
    public List<PinnedNotification> getItems() {
        return mItems;
    }

    /** Where the three-row header rule may start, so it clears the mono chip clock. */
    public void setHeaderInsetStart(float insetPx) {
        if (Math.abs(mHeaderInsetStart - insetPx) < .5f) return;
        mHeaderInsetStart = insetPx;
        invalidate();
    }

    /** The contention layout gives the card 40dp, so its body drops to a single line. */
    public void setCompactCard(boolean compact) {
        if (mCompactCard == compact) return;
        mCompactCard = compact;
        invalidate();
    }

    public boolean isStacked() {
        return mItems.size() >= TopPaneSlotMode.MAX_PINNED;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mDismissRects.clear();
        if (mItems.isEmpty() || getWidth() <= 0 || getHeight() <= 0) return;
        if (isStacked()) drawStack(canvas);
        else if (mItems.size() == 2) drawTwoCards(canvas);
        else drawCard(canvas, mItems.get(0), 0, 0f, getHeight(), mCompactCard);
    }

    private void drawTwoCards(Canvas canvas) {
        float gap = dp(2f);
        float cardHeight = (getHeight() - gap) / 2f;
        drawCard(canvas, mItems.get(0), 0, 0f, cardHeight, true);
        drawCard(canvas, mItems.get(1), 1, cardHeight + gap, cardHeight, true);
    }

    private void drawCard(Canvas canvas, @NonNull PinnedNotification item, int index, float top,
                          float height, boolean singleLineBody) {
        mRect.set(0f, top, getWidth(), top + height);
        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mTertiary, 26));
        canvas.drawRoundRect(mRect, dp(8f), dp(8f), mFillPaint);
        mFillPaint.setStyle(Paint.Style.STROKE);
        mFillPaint.setStrokeWidth(dp(1f));
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mTertiary, 66));
        mRect.inset(dp(.5f), dp(.5f));
        canvas.drawRoundRect(mRect, dp(8f), dp(8f), mFillPaint);
        mFillPaint.setStyle(Paint.Style.FILL);

        float paddingStart = dp(6f);
        float paddingTop = dp(5f);
        float icon = dp(18f);
        float iconTop = top + paddingTop;
        drawAppIcon(canvas, item.packageName, paddingStart, iconTop, icon, dp(5f));

        float dismiss = dp(20f);
        Rect dismissRect = new Rect(Math.round(getWidth() - dp(5f) - dismiss),
            Math.round(top + (height - dismiss) / 2f),
            Math.round(getWidth() - dp(5f)), Math.round(top + (height + dismiss) / 2f));
        drawDismiss(canvas, dismissRect, index, dp(10f));

        float textLeft = paddingStart + icon + dp(6f);
        float textWidth = dismissRect.left - dp(4f) - textLeft;
        if (textWidth <= dp(16f)) return;

        mTextPaint.setTypeface(mediumTypeface());
        mTextPaint.setTextSize(sp(10f));
        mTextPaint.setColor(mOnSurface);
        mTextPaint.setAlpha(255);
        float titleBaseline = iconTop - mTextPaint.ascent();
        CharSequence title = TextUtils.ellipsize(item.title(), mTextPaint, textWidth,
            TextUtils.TruncateAt.END);
        canvas.drawText(title, 0, title.length(), textLeft, titleBaseline, mTextPaint);

        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextSize(sp(9f));
        mTextPaint.setColor(mOnSurfaceVariant);
        mTextPaint.setAlpha(230);
        float bodyTop = titleBaseline + mTextPaint.descent() + dp(2f);
        float bodyAvailable = top + height - dp(4f) - bodyTop;
        int maxLines = singleLineBody ? 1 : 2;
        if (bodyAvailable < -mTextPaint.ascent()) return;
        StaticLayout layout = StaticLayout.Builder
            .obtain(item.body, 0, item.body.length(), mTextPaint, Math.round(textWidth))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build();
        canvas.save();
        canvas.translate(textLeft, bodyTop);
        layout.draw(canvas);
        canvas.restore();
    }

    private void drawStack(Canvas canvas) {
        float headerHeight = dp(HEADER_HEIGHT_DP);
        drawStackHeader(canvas, headerHeight);
        float rowHeight = dp(STACK_ROW_HEIGHT_DP);
        float gap = dp(1f);
        float top = headerHeight + dp(2f);
        for (int i = 0; i < mItems.size(); i++) {
            drawStackRow(canvas, mItems.get(i), i, top, rowHeight);
            top += rowHeight + gap;
        }
    }

    private void drawStackHeader(Canvas canvas, float height) {
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setTextSize(sp(7.5f));
        mTextPaint.setLetterSpacing(.1f);
        mTextPaint.setColor(ColorUtils.setAlphaComponent(mTertiary, 191));
        String label = mItems.size() + " PINNED";
        float labelWidth = mTextPaint.measureText(label);
        float baseline = height / 2f - (mTextPaint.ascent() + mTextPaint.descent()) / 2f;
        canvas.drawText(label, getWidth() - labelWidth, baseline, mTextPaint);
        mTextPaint.setLetterSpacing(0f);

        float ruleStart = Math.max(0f, mHeaderInsetStart);
        float ruleEnd = getWidth() - labelWidth - dp(6f);
        if (ruleEnd <= ruleStart) return;
        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(Color.argb(26, 230, 238, 246));
        canvas.drawRect(ruleStart, height / 2f - dp(.5f), ruleEnd, height / 2f + dp(.5f), mFillPaint);
    }

    private void drawStackRow(Canvas canvas, @NonNull PinnedNotification item, int index, float top,
                              float height) {
        mRect.set(0f, top, getWidth(), top + height);
        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mTertiary, 23));
        canvas.drawRoundRect(mRect, dp(6f), dp(6f), mFillPaint);

        float icon = dp(13f);
        drawAppIcon(canvas, item.packageName, dp(4f), top + (height - icon) / 2f, icon, dp(4f));

        float dismiss = dp(14f);
        Rect dismissRect = new Rect(Math.round(getWidth() - dp(3f) - dismiss),
            Math.round(top + (height - dismiss) / 2f), Math.round(getWidth() - dp(3f)),
            Math.round(top + (height + dismiss) / 2f));
        drawDismiss(canvas, dismissRect, index, dp(8f));

        float textLeft = dp(4f) + icon + dp(5f);
        float textWidth = dismissRect.left - dp(4f) - textLeft;
        if (textWidth <= dp(12f)) return;

        mTextPaint.setTypeface(mediumTypeface());
        mTextPaint.setTextSize(sp(8.5f));
        mTextPaint.setColor(mOnSurface);
        mTextPaint.setAlpha(255);
        float baseline = top + height / 2f - (mTextPaint.ascent() + mTextPaint.descent()) / 2f;
        String sender = item.senderOrApp();
        float senderWidth = Math.min(mTextPaint.measureText(sender), textWidth * .45f);
        CharSequence senderText = TextUtils.ellipsize(sender, mTextPaint, senderWidth,
            TextUtils.TruncateAt.END);
        canvas.drawText(senderText, 0, senderText.length(), textLeft, baseline, mTextPaint);

        float bodyLeft = textLeft + senderWidth + dp(5f);
        float bodyWidth = textLeft + textWidth - bodyLeft;
        if (bodyWidth <= dp(12f)) return;
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setColor(mOnSurfaceVariant);
        mTextPaint.setAlpha(217);
        CharSequence body = TextUtils.ellipsize(item.body, mTextPaint, bodyWidth,
            TextUtils.TruncateAt.END);
        canvas.drawText(body, 0, body.length(), bodyLeft, baseline, mTextPaint);
    }

    private void drawAppIcon(Canvas canvas, String packageName, float left, float top, float size,
                             float radius) {
        Drawable icon = mIcons.get(packageName);
        if (icon == null) {
            mRect.set(left, top, left + size, top + size);
            mFillPaint.setShader(null);
            mFillPaint.setStyle(Paint.Style.FILL);
            mFillPaint.setColor(ColorUtils.setAlphaComponent(mOnSurface, 26));
            canvas.drawRoundRect(mRect, radius, radius, mFillPaint);
            return;
        }
        canvas.save();
        mRect.set(left, top, left + size, top + size);
        canvas.clipRect(mRect);
        icon.setBounds(Math.round(left), Math.round(top), Math.round(left + size),
            Math.round(top + size));
        icon.draw(canvas);
        canvas.restore();
    }

    private void drawDismiss(Canvas canvas, Rect box, int index, float glyph) {
        mDismissRects.add(box);
        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(Color.argb(mPressedIndex == index ? 41 : 20, 230, 238, 246));
        canvas.drawCircle(box.centerX(), box.centerY(), box.width() / 2f, mFillPaint);
        Drawable cross = AppCompatResources.getDrawable(getContext(),
            R.drawable.ic_pinned_notification_dismiss);
        if (cross == null) return;
        cross = cross.mutate();
        cross.setTint(mOnSurface);
        cross.setAlpha(191);
        int half = Math.round(glyph / 2f);
        cross.setBounds(box.centerX() - half, box.centerY() - half, box.centerX() + half,
            box.centerY() + half);
        cross.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mItems.isEmpty()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mPressedIndex = hitDismiss(event.getX(), event.getY());
                if (mPressedIndex < 0) return false;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                return mPressedIndex >= 0;
            case MotionEvent.ACTION_UP: {
                int index = mPressedIndex;
                mPressedIndex = -1;
                invalidate();
                if (index < 0 || hitDismiss(event.getX(), event.getY()) != index) return true;
                if (mListener != null && index < mItems.size()) {
                    mListener.onDismissPinned(mItems.get(index));
                }
                return true;
            }
            default:
                mPressedIndex = -1;
                invalidate();
                return false;
        }
    }

    /** The visual glyphs are 14-20dp; hit rects grow to 40dp, nearest center wins on overlap. */
    private int hitDismiss(float x, float y) {
        float minimum = dp(40f);
        int nearest = -1;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < mDismissRects.size(); i++) {
            Rect rect = mDismissRects.get(i);
            float growX = Math.max(0f, (minimum - rect.width()) / 2f);
            float growY = Math.max(0f, (minimum - rect.height()) / 2f);
            if (x < rect.left - growX || x > rect.right + growX
                || y < rect.top - growY || y > rect.bottom + growY) continue;
            float dx = x - rect.exactCenterX();
            float dy = y - rect.exactCenterY();
            float distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }
        return nearest;
    }

    private static Typeface mediumTypeface() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
