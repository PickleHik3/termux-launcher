package com.termux.app.azrail;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The A–Z rail on screen (#437): a column of letters against the thumb's edge and, beside it, the
 * held letter's apps. Drawn by hand from {@link AzRailModel}; it never takes a touch (the finger
 * belongs to the control bar that raised it), so it draws what the model says and nothing else.
 */
final class AzRailView extends View {

    /** What the rail wears and shows through the activity. */
    interface Style {
        @Nullable Drawable icon(@NonNull LauncherAppEntry entry);

        float cornerRadiusPx();

        int ink();

        int accent();
    }

    @NonNull private final AzRailModel mModel;
    @NonNull private final Style mStyle;
    @NonNull private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final RectF mRect = new RectF();
    @NonNull private final Map<String, Drawable> mIcons = new HashMap<>();
    private final float mDensity;
    private int mLetter = -1;
    private int mApp = -1;

    AzRailView(@NonNull Context context, @NonNull AzRailModel model, @NonNull Style style) {
        super(context);
        mModel = model;
        mStyle = style;
        mDensity = context.getResources().getDisplayMetrics().density;
        mText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setContentDescription(context.getString(R.string.az_rail));
    }

    /** The letter held and the app under the finger (-1 for none) are now these. */
    void show(int letter, int app) {
        if (letter == mLetter && app == mApp) return;
        mLetter = letter;
        mApp = app;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int count = mModel.letterCount();
        if (count == 0) return;
        float radius = mStyle.cornerRadiusPx();
        int ink = mStyle.ink();
        int accent = mStyle.accent();
        mFill.setColor(0xE61B1B1F);
        mRect.set(mModel.railLeft(), mModel.top(), mModel.railRight(), mModel.bottom());
        canvas.drawRoundRect(mRect, radius, radius, mFill);
        float slot = (mModel.bottom() - mModel.top()) / count;
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setTextSize(Math.min(13f * mDensity, slot * 0.7f));
        float cx = (mModel.railLeft() + mModel.railRight()) / 2f;
        for (int i = 0; i < count; i++) {
            boolean lit = i == mLetter;
            mText.setColor(lit ? accent : ink);
            mText.setAlpha(lit ? 255 : 190);
            float cy = mModel.letterCenterY(i);
            canvas.drawText(String.valueOf(mModel.letterAt(i)), cx,
                cy - (mText.ascent() + mText.descent()) / 2f, mText);
        }
        if (mLetter < 0) return;
        List<LauncherAppEntry> apps = mModel.fanApps(mLetter);
        if (apps.isEmpty()) return;
        float itemHeight = mModel.itemHeight();
        float top = mModel.fanTop(mLetter);
        mRect.set(mModel.fanLeft(mLetter), top, mModel.fanRight(mLetter),
            top + Math.min(apps.size(), mModel.fanRows()) * itemHeight);
        mFill.setColor(0xF01B1B1F);
        canvas.drawRoundRect(mRect, radius, radius, mFill);
        float iconSize = itemHeight * 0.62f;
        float pad = 10f * mDensity;
        mText.setTextAlign(Paint.Align.LEFT);
        mText.setTextSize(15f * mDensity);
        for (int i = 0; i < apps.size(); i++) {
            float rowTop = mModel.appTop(mLetter, i);
            float colLeft = mModel.appLeft(mLetter, i);
            float colRight = colLeft + mModel.fanColumnWidth(mLetter);
            if (i == mApp) {
                mFill.setColor(ColorUtils.setAlphaComponent(accent, 0x55));
                mRect.set(colLeft + 4f * mDensity, rowTop + 2f * mDensity,
                    colRight - 4f * mDensity, rowTop + itemHeight - 2f * mDensity);
                canvas.drawRoundRect(mRect, radius / 2f, radius / 2f, mFill);
            }
            LauncherAppEntry entry = apps.get(i);
            Drawable icon = iconOf(entry);
            float iconTop = rowTop + (itemHeight - iconSize) / 2f;
            float left = colLeft + pad;
            if (icon != null) {
                icon.setBounds(Math.round(left), Math.round(iconTop), Math.round(left + iconSize),
                    Math.round(iconTop + iconSize));
                icon.draw(canvas);
            }
            mText.setColor(ink);
            mText.setAlpha(255);
            CharSequence label = android.text.TextUtils.ellipsize(entry.label, new android.text.TextPaint(mText),
                colRight - left - iconSize - pad * 2f, android.text.TextUtils.TruncateAt.END);
            canvas.drawText(label, 0, label.length(), left + iconSize + pad,
                rowTop + itemHeight / 2f - (mText.ascent() + mText.descent()) / 2f, mText);
        }
    }

    @Nullable
    private Drawable iconOf(@NonNull LauncherAppEntry entry) {
        String id = entry.appRef.stableId();
        Drawable icon = mIcons.get(id);
        if (icon == null) {
            icon = mStyle.icon(entry);
            if (icon != null) mIcons.put(id, icon);
        }
        return icon;
    }
}
