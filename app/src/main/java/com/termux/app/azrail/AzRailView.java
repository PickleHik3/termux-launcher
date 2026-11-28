package com.termux.app.azrail;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The A–Z rail on screen (#437): a column of letters against the thumb's edge and, beside it, the
 * held letter's apps. The two panels are glass views, the same glass the Workspace list and the
 * bar's search wear; the letters and rows are drawn over them from {@link AzRailModel}. It never
 * takes a touch (the finger belongs to the control bar that raised it). Nothing is built in the
 * draw path: letters, labels and icons are made when a letter is held, icons off the main thread.
 */
final class AzRailView extends FrameLayout {

    private static final ExecutorService ICONS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "az-rail-icons");
        thread.setDaemon(true);
        return thread;
    });

    @NonNull private final AzRailModel mModel;
    @NonNull private final AzRail.Host mHost;
    @NonNull private final View mRailPanel;
    @NonNull private final View mFanPanel;
    @NonNull private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final TextPaint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final TextPaint mLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final RectF mRect = new RectF();
    @NonNull private final String[] mLetters;
    /** Icons loaded for this raise, by app id; only the main thread touches it. */
    @NonNull private final Map<String, Drawable> mIcons = new HashMap<>();
    @NonNull private final Set<String> mPending = new HashSet<>();
    @NonNull private CharSequence[] mLabels = new CharSequence[0];
    private final float mDensity;
    private final float mRadius;
    private int mLetter = -1;
    private int mApp = -1;
    private int mLabelLetter = -1;
    private boolean mFanDressed;
    private boolean mReleased;

    AzRailView(@NonNull Context context, @NonNull AzRailModel model, @NonNull AzRail.Host host) {
        super(context);
        mModel = model;
        mHost = host;
        mDensity = context.getResources().getDisplayMetrics().density;
        mRadius = host.cornerRadiusPx();
        setWillNotDraw(false);
        setContentDescription(context.getString(R.string.az_rail));
        mLetterPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Readable in landscape too: a short rail packs its letters tight rather than shrinking them.
        float slot = (model.bottom() - model.top()) / Math.max(1, model.letterCount());
        mLetterPaint.setTextSize(Math.max(10f * mDensity, Math.min(13f * mDensity, slot * 0.9f)));
        mLabelPaint.setTextSize(15f * mDensity);
        mLetters = new String[model.letterCount()];
        for (int i = 0; i < mLetters.length; i++) mLetters[i] = String.valueOf(model.letterAt(i));

        mRailPanel = new View(context);
        FrameLayout.LayoutParams rail = new FrameLayout.LayoutParams(
            Math.round(model.railRight() - model.railLeft()), Math.round(model.bottom() - model.top()));
        rail.leftMargin = Math.round(model.railLeft());
        rail.topMargin = Math.round(model.top());
        addView(mRailPanel, rail);
        mFanPanel = new View(context);
        mFanPanel.setVisibility(INVISIBLE);
        addView(mFanPanel, new FrameLayout.LayoutParams(1, 1));
        post(() -> dress(mRailPanel));
    }

    /** The glass for {@code panel}, or a plain panel in the scheme's own surface when there is none. */
    private void dress(@NonNull View panel) {
        if (mReleased || panel.getWidth() <= 0 || panel.getHeight() <= 0) return;
        Drawable glass = mHost.glass(mRadius, panel);
        if (glass == null) {
            // Dark ink means a light scheme: a light panel under it, else a dark one.
            GradientDrawable plain = new GradientDrawable();
            plain.setCornerRadius(mRadius);
            plain.setColor(ColorUtils.calculateLuminance(mHost.ink()) > 0.5
                ? 0xE61B1B1F : 0xE6FFFFFF);
            glass = plain;
        }
        panel.setBackground(glass);
    }

    /** The letter held and the app under the finger (-1 for none) are now these. */
    void show(int letter, int app) {
        if (letter == mLetter && app == mApp) return;
        boolean letterChanged = letter != mLetter;
        mLetter = letter;
        mApp = app;
        if (letterChanged) {
            placeFan();
            prepare(letter);
        }
        invalidate();
    }

    private void placeFan() {
        if (mLetter < 0 || mModel.fanApps(mLetter).isEmpty()) {
            mFanPanel.setVisibility(INVISIBLE);
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mFanPanel.getLayoutParams();
        params.leftMargin = Math.round(mModel.fanLeft(mLetter));
        params.topMargin = Math.round(mModel.fanTop(mLetter));
        params.width = Math.max(1, Math.round(mModel.fanRight(mLetter) - mModel.fanLeft(mLetter)));
        params.height = Math.max(1, Math.round(mModel.fanHeight(mLetter)));
        mFanPanel.setLayoutParams(params);
        mFanPanel.setVisibility(VISIBLE);
        if (!mFanDressed) {
            mFanDressed = true;
            mFanPanel.post(() -> dress(mFanPanel));
        }
    }

    /** Builds the held letter's labels and asks for the icons it has not got yet, off the main thread. */
    private void prepare(int letter) {
        if (letter < 0 || letter == mLabelLetter) return;
        mLabelLetter = letter;
        List<LauncherAppEntry> apps = mModel.fanApps(letter);
        float pad = 10f * mDensity;
        float iconSize = mModel.itemHeight() * 0.62f;
        float width = mModel.fanColumnWidth(letter) - iconSize - pad * 3f;
        mLabels = new CharSequence[apps.size()];
        List<LauncherAppEntry> missing = new ArrayList<>();
        for (int i = 0; i < mLabels.length; i++) {
            LauncherAppEntry entry = apps.get(i);
            mLabels[i] = TextUtils.ellipsize(entry.label, mLabelPaint, Math.max(0f, width),
                TextUtils.TruncateAt.END);
            String id = entry.appRef.stableId();
            if (!mIcons.containsKey(id) && mPending.add(id)) missing.add(entry);
        }
        if (missing.isEmpty()) return;
        ICONS.execute(() -> {
            for (LauncherAppEntry entry : missing) {
                if (mReleased) return;
                Drawable art = mHost.icon(entry);
                post(() -> {
                    mPending.remove(entry.appRef.stableId());
                    if (mReleased || art == null) return;
                    mIcons.put(entry.appRef.stableId(), art);
                    invalidate();
                });
            }
        });
    }

    /** The rail is gone: pending loads land nowhere and the icons go. */
    void release() {
        mReleased = true;
        mIcons.clear();
        mPending.clear();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        int count = mLetters.length;
        if (count == 0) return;
        int ink = mHost.ink();
        int accent = mHost.accent();
        float cx = (mModel.railLeft() + mModel.railRight()) / 2f;
        float baseline = -(mLetterPaint.ascent() + mLetterPaint.descent()) / 2f;
        for (int i = 0; i < count; i++) {
            boolean lit = i == mLetter;
            mLetterPaint.setColor(lit ? accent : ink);
            mLetterPaint.setAlpha(lit ? 255 : 190);
            canvas.drawText(mLetters[i], cx, mModel.letterCenterY(i) + baseline, mLetterPaint);
        }
        if (mLetter < 0 || mLabelLetter != mLetter) return;
        List<LauncherAppEntry> apps = mModel.fanApps(mLetter);
        float itemHeight = mModel.itemHeight();
        float iconSize = itemHeight * 0.62f;
        float pad = 10f * mDensity;
        float colWidth = mModel.fanColumnWidth(mLetter);
        float labelBase = -(mLabelPaint.ascent() + mLabelPaint.descent()) / 2f;
        mLabelPaint.setColor(ink);
        for (int i = 0; i < apps.size(); i++) {
            float rowTop = mModel.appTop(mLetter, i);
            float colLeft = mModel.appLeft(mLetter, i);
            if (i == mApp) {
                mFill.setColor(ColorUtils.setAlphaComponent(accent, 0x55));
                mRect.set(colLeft + 4f * mDensity, rowTop + 2f * mDensity,
                    colLeft + colWidth - 4f * mDensity, rowTop + itemHeight - 2f * mDensity);
                canvas.drawRoundRect(mRect, mRadius / 2f, mRadius / 2f, mFill);
            }
            float left = colLeft + pad;
            float iconTop = rowTop + (itemHeight - iconSize) / 2f;
            Drawable icon = mIcons.get(apps.get(i).appRef.stableId());
            if (icon != null) {
                icon.setBounds(Math.round(left), Math.round(iconTop), Math.round(left + iconSize),
                    Math.round(iconTop + iconSize));
                icon.draw(canvas);
            }
            CharSequence label = mLabels[i];
            canvas.drawText(label, 0, label.length(), left + iconSize + pad,
                rowTop + itemHeight / 2f + labelBase, mLabelPaint);
        }
    }
}
