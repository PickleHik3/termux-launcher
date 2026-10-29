package com.termux.app.wall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A page's card still (ADR 0016): what a cell shows while its page kind's one view is bound to
 * another page. The page's name over its kind's icon, on a plain glass-tinted card. It is seen
 * only in motion; landing on the cell binds the view to the page and the card gives way.
 */
public final class PageCardView extends View {

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mInk = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final int mPageId;
    @NonNull private final PaneWallPage mKind;
    @NonNull private String mName;

    public PageCardView(@NonNull Context context, int pageId, @NonNull PaneWallPage kind,
                        @Nullable String name) {
        super(context);
        mPageId = pageId;
        mKind = kind;
        mName = name == null ? "" : name;
        float density = context.getResources().getDisplayMetrics().density;
        mFill.setColor(Color.argb(72, 128, 128, 128));
        mInk.setColor(Color.argb(220, 255, 255, 255));
        mInk.setStyle(Paint.Style.STROKE);
        mInk.setStrokeWidth(2f * density);
        mText.setColor(Color.argb(230, 255, 255, 255));
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setTextSize(18f * density);
        setContentDescription(mName);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The page this card stands for. */
    public int pageId() { return mPageId; }

    /** The page's kind, whose icon the card wears. */
    @NonNull public PaneWallPage kind() { return mKind; }

    /** The name the card shows. */
    @NonNull public String name() { return mName; }

    /** A renamed page: the card shows the new name. */
    public void setName(@Nullable String name) {
        String next = name == null ? "" : name;
        if (next.equals(mName)) return;
        mName = next;
        setContentDescription(next);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float radius = 24f * density;
        mRect.set(0f, 0f, getWidth(), getHeight());
        canvas.drawRoundRect(mRect, radius, radius, mFill);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float icon = 24f * density;
        drawIcon(canvas, cx, cy - icon, icon);
        canvas.drawText(mName, cx, cy + icon + mText.getTextSize(), mText);
    }

    /** The kind's icon, drawn in strokes about ({@code cx}, {@code cy}), {@code size} across. */
    private void drawIcon(@NonNull Canvas canvas, float cx, float cy, float size) {
        float h = size / 2f;
        switch (mKind) {
            case WIDGETS: {
                // Four tiles: a grid of widgets.
                float gap = size / 8f;
                float tile = (size - gap) / 2f;
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 2; col++) {
                        float left = cx - h + col * (tile + gap);
                        float top = cy - h + row * (tile + gap);
                        canvas.drawRect(left, top, left + tile, top + tile, mInk);
                    }
                }
                break;
            }
            case DISPLAY: {
                // A screen on a stand.
                canvas.drawRect(cx - h, cy - h, cx + h, cy + h / 2f, mInk);
                canvas.drawLine(cx, cy + h / 2f, cx, cy + h, mInk);
                canvas.drawLine(cx - h / 2f, cy + h, cx + h / 2f, cy + h, mInk);
                break;
            }
            case TERMINAL:
            default: {
                // A prompt: a chevron and a cursor bar.
                canvas.drawLine(cx - h, cy - h / 2f, cx - h / 4f, cy, mInk);
                canvas.drawLine(cx - h / 4f, cy, cx - h, cy + h / 2f, mInk);
                canvas.drawLine(cx, cy + h / 2f, cx + h, cy + h / 2f, mInk);
                break;
            }
        }
    }
}
