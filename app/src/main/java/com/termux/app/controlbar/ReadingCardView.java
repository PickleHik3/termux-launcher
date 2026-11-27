package com.termux.app.controlbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.statusbar.MediaWidgetView;
import com.termux.app.statusbar.TopPaneFeed;
import com.termux.app.statusbar.TopPaneMediaState;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.EnumMap;
import java.util.Map;

/**
 * The card a held reading slot opens (#428): every reading with its value, and under them the media
 * widget's controls. A sheet in the SHEETS layer: a clear scrim that takes a touch outside the card
 * as "close", and the glass card standing above the bar (beside the rail) on the slot's side.
 * Made at the hold, gone at {@link #close}; {@link #bind} keeps its numbers current while it is open.
 */
public final class ReadingCardView extends FrameLayout {

    static final float WIDTH_DP = 280f;
    private static final float GAP_DP = 8f;
    private static final float PAD_DP = 14f;
    private static final float ROW_DP = 36f;
    private static final float ICON_DP = 18f;

    /** What the card needs of the bar: its glass, its ink and its corner. */
    public interface Style {
        @Nullable Drawable glass(float radiusPx, @NonNull View anchor);

        float cornerRadiusPx();

        int ink();
    }

    /** What the card asks of its owner. */
    public interface Listener {
        /** A touch outside the card. */
        void onDismiss();
    }

    @NonNull private final Style mStyle;
    @NonNull private final Listener mListener;
    @NonNull private final LinearLayout mCard;
    @NonNull private final Map<ReadingKind, TextView> mValues = new EnumMap<>(ReadingKind.class);
    @NonNull private final MediaWidgetView mMedia;
    private View mMediaRow;
    private final float mDensity;
    @Nullable private Drawable mMediaIcon;
    @Nullable private String mMediaIconPackage;

    @SuppressLint("ClickableViewAccessibility")
    public ReadingCardView(@NonNull Context context, @NonNull Style style, @NonNull Listener listener) {
        super(context);
        mStyle = style;
        mListener = listener;
        mDensity = context.getResources().getDisplayMetrics().density;
        setContentDescription(context.getString(R.string.control_bar_reading_card));
        mCard = new LinearLayout(context);
        mCard.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(PAD_DP * mDensity);
        mCard.setPadding(pad, pad, pad, pad);
        // A touch on the card is the card's; only the scrim around it closes.
        mCard.setClickable(true);
        int ink = style.ink();
        for (ReadingKind kind : ReadingKind.values()) {
            View row = row(kind, ink);
            mCard.addView(row);
            if (kind == ReadingKind.MEDIA) mMediaRow = row;
        }
        mMedia = new MediaWidgetView(context);
        mMedia.setForm(MediaWidgetView.Form.FULL);
        mMedia.setVisibility(GONE);
        mCard.addView(mMedia, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            Math.round(MediaWidgetView.FULL_HEIGHT_DP * mDensity)));
        addView(mCard, new LayoutParams(Math.round(WIDTH_DP * mDensity),
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
        mCard.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.post(this::dress);
        });
        setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) mListener.onDismiss();
            return true;
        });
    }

    /** One reading's row: its icon, its name, and its value at the end. */
    @NonNull
    private View row(@NonNull ReadingKind kind, int ink) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Math.round(ROW_DP * mDensity));
        ImageView icon = new ImageView(context);
        Drawable glyph = ContextCompat.getDrawable(context, iconOf(kind));
        if (glyph != null) icon.setImageDrawable(glyph.mutate());
        icon.setColorFilter(ColorUtils.setAlphaComponent(ink, 220));
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = Math.round(ICON_DP * mDensity);
        row.addView(icon, new LinearLayout.LayoutParams(size, size));
        TextView name = new TextView(context);
        name.setText(nameOf(kind));
        name.setTextColor(ink);
        name.setTextSize(15f);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(Math.round(10f * mDensity));
        row.addView(name, nameParams);
        TextView value = new TextView(context);
        value.setText(kind == ReadingKind.MEDIA
            ? context.getString(R.string.control_bar_reading_no_media) : ReadingValue.UNKNOWN_NUMBER);
        value.setTextColor(ColorUtils.setAlphaComponent(ink, 200));
        value.setTextSize(15f);
        value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        mValues.put(kind, value);
        return row;
    }

    /**
     * Opens the card in the SHEETS layer above {@code bar}, at the reading slot's {@code cell}: over
     * the bar toward the slot's side in the two rows, beside the rail toward the screen's middle.
     * False when there is no sheets layer (nothing shows).
     */
    public boolean show(@NonNull View bar, @NonNull Rect cell, boolean rail) {
        if (getParent() != null) return true;
        ViewGroup host = WindowLayers.host(id -> bar.getRootView().findViewById(id), WindowLayer.SHEETS);
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return false;
        int[] bl = new int[2];
        int[] hl = new int[2];
        bar.getLocationInWindow(bl);
        host.getLocationInWindow(hl);
        int left = bl[0] - hl[0] + cell.left;
        int top = bl[1] - hl[1] + cell.top;
        int right = bl[0] - hl[0] + cell.right;
        int bottom = bl[1] - hl[1] + cell.bottom;
        int gap = Math.round(GAP_DP * mDensity);
        int width = Math.round(WIDTH_DP * mDensity);
        LayoutParams params = (LayoutParams) mCard.getLayoutParams();
        params.gravity = Gravity.BOTTOM | Gravity.START;
        if (rail) {
            // Beside the rail, on the side the screen's middle is; its bottom level with the slot's.
            boolean railOnRight = (left + right) / 2 > host.getWidth() / 2;
            params.leftMargin = railOnRight ? Math.max(gap, left - gap - width) : Math.min(
                Math.max(gap, host.getWidth() - width - gap), right + gap);
            params.bottomMargin = Math.max(gap, host.getHeight() - bottom);
        } else {
            // Over the bar, its end under the slot's end.
            params.leftMargin = Math.max(gap, Math.min(host.getWidth() - width - gap, right - width));
            params.bottomMargin = Math.max(gap, host.getHeight() - top + gap);
        }
        mCard.setLayoutParams(params);
        host.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        return true;
    }

    /** Whether the card is on screen. */
    public boolean isOpen() {
        return getParent() != null;
    }

    /** Takes the card off the screen. */
    public void close() {
        ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        if (parent != null) parent.removeView(this);
    }

    /** The glass is cut for the card's size, never from inside the layout pass. */
    private void dress() {
        if (mCard.getWidth() <= 0 || mCard.getHeight() <= 0 || !isOpen()) return;
        float radius = mStyle.cornerRadiusPx();
        Drawable glass = mStyle.glass(radius, mCard);
        if (glass == null) {
            GradientDrawable plain = new GradientDrawable();
            plain.setCornerRadius(radius);
            plain.setColor(ColorUtils.setAlphaComponent(0xFF1B1B1F, 0xE6));
            glass = plain;
        }
        mCard.setBackground(glass);
        mCard.setClipToOutline(true);
        mCard.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mCard.post(this::dress);
    }

    /** Every reading's value, and the media widget when a session plays; read again on each change. */
    public void bind(@NonNull ReadingSet set) {
        for (ReadingKind kind : ReadingKind.values()) {
            if (kind == ReadingKind.MEDIA) continue;
            ReadingValue value = set.get(kind);
            TextView text = mValues.get(kind);
            if (text != null) text.setText(value == null ? ReadingValue.UNKNOWN_NUMBER : value.full());
        }
        TopPaneMediaState media = TopPaneFeed.getMedia();
        boolean playing = media != null;
        if (mMediaRow != null) mMediaRow.setVisibility(playing ? GONE : VISIBLE);
        mMedia.setVisibility(playing ? VISIBLE : GONE);
        if (playing) mMedia.setState(media, ownerIcon(media.packageName));
    }

    @Nullable
    private Drawable ownerIcon(@NonNull String packageName) {
        if (packageName.isEmpty()) return null;
        if (!packageName.equals(mMediaIconPackage)) {
            mMediaIconPackage = packageName;
            try {
                mMediaIcon = getContext().getPackageManager().getApplicationIcon(packageName);
            } catch (Exception e) {
                mMediaIcon = null;
            }
        }
        return mMediaIcon;
    }

    static int iconOf(@NonNull ReadingKind kind) {
        switch (kind) {
            case CPU: return R.drawable.ic_ms_speed;
            case GPU: return R.drawable.ic_ms_developer_board;
            case RAM: return R.drawable.ic_ms_memory;
            case WEATHER: return R.drawable.ic_ms_cloud;
            case MEDIA: return R.drawable.ic_ms_music_note;
            case BATTERY:
            default: return R.drawable.ic_ms_battery_full;
        }
    }

    static int nameOf(@NonNull ReadingKind kind) {
        switch (kind) {
            case CPU: return R.string.control_bar_reading_cpu;
            case GPU: return R.string.control_bar_reading_gpu;
            case RAM: return R.string.control_bar_reading_ram;
            case WEATHER: return R.string.control_bar_reading_weather;
            case MEDIA: return R.string.control_bar_reading_media;
            case BATTERY:
            default: return R.string.control_bar_reading_battery;
        }
    }
}
