package com.termux.app.wall;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeShade;
import com.termux.app.theme.TextScale;

/**
 * A page's still (ADR 0016): what a cell shows while its page kind's one view is bound to another
 * page. The page's last picture when it has one ({@link PageStillPolicy}), else its card: the
 * page's name over its kind's icon on a glass-tinted card. The card's ink is the chrome's one
 * legibility polarity (ADR 0014), the same the dock and status bar wear on every kind, and its
 * name follows the shared Text size. A still is seen only in motion; landing on the cell binds the
 * view to the page and the still gives way.
 */
public final class PageCardView extends View {

    /** The card's wash as authored for pale ink; {@link ChromeShade#fill} restates it. */
    @ColorInt private static final int FILL_SEED = Color.argb(40, 255, 255, 255);
    /** The card's corner radius in dp. */
    private static final float CORNER_DP = 24f;
    /** The kind's icon size in dp: Material's standard icon. */
    private static final float ICON_DP = 24f;
    /** The name's size in sp at a Text size of 1: Material's title-medium. */
    private static final float NAME_SP = 16f;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mInk = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPicturePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF mRect = new RectF();
    private final Rect mBounds = new Rect();
    private final int mPageId;
    @NonNull private final PaneWallPage mKind;
    @NonNull private String mName;
    @Nullable private Bitmap mPicture;

    /**
     * The card still of page {@code pageId}, of {@code kind}, naming {@code name} (null: none)
     * until it has a picture.
     */
    public PageCardView(@NonNull Context context, int pageId, @NonNull PaneWallPage kind,
                        @Nullable String name) {
        super(context);
        mPageId = pageId;
        mKind = kind;
        mName = name == null ? "" : name;
        float density = context.getResources().getDisplayMetrics().density;
        mInk.setStyle(Paint.Style.STROKE);
        mInk.setStrokeWidth(2f * density);
        mText.setTextAlign(Paint.Align.CENTER);
        applyInk();
        setContentDescription(mName);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The page this still stands for. */
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

    /**
     * The page's picture to show in place of its card, or null for the card. The bitmap stays
     * the caller's: drawn here, never recycled; set null before recycling it.
     */
    public void setPicture(@Nullable Bitmap picture) {
        if (picture == mPicture) return;
        mPicture = picture;
        invalidate();
    }

    /** The picture the still shows; null while it shows the card. */
    @Nullable
    public Bitmap picture() {
        return mPicture;
    }

    /** What the still shows: its picture, or its card while it has none. */
    @NonNull
    public PageStillPolicy.Shows shows() {
        return hasPicture() ? PageStillPolicy.Shows.PICTURE : PageStillPolicy.Shows.CARD;
    }

    private boolean hasPicture() {
        return mPicture != null && !mPicture.isRecycled();
    }

    /**
     * The card's ink for the chrome's {@code polarity} (ADR 0014): the pale ink on a dark ground,
     * the dark ink on a light one, as every piece of chrome on the page wears; pale while nothing
     * has measured the chrome yet, as the chrome itself starts.
     */
    @ColorInt
    public static int inkFor(@Nullable ChromeInk.Polarity polarity) {
        return polarity == ChromeInk.Polarity.DARK_INK
            ? Color.argb(222, 0, 0, 0) : Color.argb(230, 255, 255, 255);
    }

    /**
     * The name's text size in px: {@link #NAME_SP} at the shared Text size and the system font
     * scale, as the rest of the chrome's type.
     */
    public static float nameTextPx(@NonNull android.util.DisplayMetrics metrics) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, NAME_SP * TextScale.get(),
            metrics);
    }

    /** The ink, wash and type the card wears now: read on every draw, so it follows the chrome. */
    private void applyInk() {
        int ink = inkFor(ChromeShade.polarity());
        mFill.setColor(ChromeShade.fill(FILL_SEED));
        mInk.setColor(ink);
        mText.setColor(ink);
        mText.setTextSize(nameTextPx(getResources().getDisplayMetrics()));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (hasPicture()) {
            mBounds.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(mPicture, null, mBounds, mPicturePaint);
            return;
        }
        applyInk();
        float density = getResources().getDisplayMetrics().density;
        float radius = CORNER_DP * density;
        mRect.set(0f, 0f, getWidth(), getHeight());
        canvas.drawRoundRect(mRect, radius, radius, mFill);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float icon = ICON_DP * density;
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
