package com.termux.app.fragments.settings;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.fragments.settings.LayoutChooserModel.Counter;
import com.termux.app.fragments.settings.LayoutChooserModel.Group;
import com.termux.app.fragments.settings.LayoutChooserModel.Pills;

import java.util.List;

/**
 * One element's chooser, over the Layout page: the row's name, then a labelled control per
 * orientation. Picks land in {@link com.termux.app.place.PlaceLayoutStore} as they happen — there
 * is nothing to confirm — and the sheet rebuilds itself from the model afterwards, because a pick
 * can change what the rest of the sheet offers (an A–Z index left standing alone gains an edge).
 *
 * <p>The pills are {@link SegmentedPillPreference}'s own track and indicator, inflated from
 * {@code layout_chooser_pill}, so a value reads the same inside a sheet as on a settings row.
 */
public final class LayoutChooserSheet {

    /** Where the sheet's groups come from, re-asked after every pick. */
    public interface Groups {
        @NonNull
        List<Group> groups();
    }

    /** Told after every pick, so the page behind the sheet redraws. */
    public interface Host {
        void onLayoutWritten();
    }

    @NonNull private final Context mContext;
    @NonNull private final CharSequence mTitle;
    @NonNull private final Groups mGroups;
    @NonNull private final Host mHost;
    private final float mDensity;

    @Nullable private LinearLayout mBody;

    private LayoutChooserSheet(@NonNull Context context, @NonNull CharSequence title,
                               @NonNull Groups groups, @NonNull Host host) {
        mContext = context;
        mTitle = title;
        mGroups = groups;
        mHost = host;
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    /** Opens the chooser for one row. */
    public static void show(@NonNull Context context, @NonNull CharSequence title,
                            @NonNull Groups groups, @NonNull Host host) {
        new LayoutChooserSheet(context, title, groups, host).open();
    }

    private void open() {
        BottomSheetDialog dialog = new BottomSheetDialog(mContext);
        LinearLayout root = column();
        root.setPadding(dp(24), dp(12), dp(24), dp(20));

        TextView title = new TextView(mContext);
        title.setText(mTitle);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(color(com.termux.shared.R.attr.termuxColorOnSurface,
            R.color.termux_on_surface));
        title.setPadding(0, dp(4), 0, dp(8));
        root.addView(title);

        mBody = column();
        ScrollView scroller = new ScrollView(mContext);
        scroller.addView(mBody);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroller);
        render();

        dialog.setContentView(root);
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
        dialog.show();
    }

    /** Draws every group from scratch: a pick can add or drop one. */
    private void render() {
        if (mBody == null) return;
        mBody.removeAllViews();
        for (Group group : mGroups.groups()) {
            if (group.label != null) mBody.addView(groupLabel(group.label));
            if (group instanceof Pills) {
                mBody.addView(pills((Pills) group));
            } else if (group instanceof Counter) {
                mBody.addView(counter((Counter) group));
            }
        }
    }

    private void written() {
        mHost.onLayoutWritten();
        render();
    }

    // ---- Controls ------------------------------------------------------------------------------

    @NonNull
    private View pills(@NonNull Pills group) {
        FrameLayout track = (FrameLayout) LayoutInflater.from(mContext)
            .inflate(R.layout.layout_chooser_pill, null, false);
        View indicator = track.findViewById(R.id.layout_chooser_pill_indicator);
        LinearLayout segments = track.findViewById(R.id.layout_chooser_pill_segments);

        int selected = indexOf(group.values, group.selected);
        int selectedColor = color(com.termux.shared.R.attr.termuxColorOnPrimary,
            R.color.termux_on_primary);
        int idleColor = color(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant);
        for (int i = 0; i < group.values.length; i++) {
            TextView label = new TextView(mContext);
            label.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            label.setGravity(Gravity.CENTER);
            label.setText(group.labelResIds[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setMinWidth(0);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setTextColor(i == selected ? selectedColor : idleColor);
            final String value = group.values[i];
            label.setOnClickListener(view -> {
                if (value.equals(group.selected)) return;
                group.writer.write(value);
                written();
            });
            segments.addView(label);
        }

        final int segmentCount = group.values.length;
        track.post(() -> {
            float width = segmentWidth(track, segmentCount);
            if (width <= 0f) return;
            indicator.getLayoutParams().width = Math.round(width);
            indicator.requestLayout();
            indicator.setTranslationX(Math.max(0, selected) * width);
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        params.bottomMargin = dp(6);
        track.setLayoutParams(params);
        return track;
    }

    @NonNull
    private View counter(@NonNull Counter group) {
        LinearLayout holder = column();
        holder.setPadding(0, dp(2), 0, dp(10));

        LinearLayout head = new LinearLayout(mContext);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(mContext);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        title.setText(group.titleRes);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        title.setTextColor(color(com.termux.shared.R.attr.termuxColorOnSurface,
            R.color.termux_on_surface));
        head.addView(title);
        TextView value = new TextView(mContext);
        value.setText(String.valueOf(group.value));
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        value.setTextColor(color(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant));
        head.addView(value);
        holder.addView(head);

        SeekBar bar = new SeekBar(mContext);
        bar.setMax(group.max - group.min);
        bar.setProgress(Math.max(0, Math.min(group.max - group.min, group.value - group.min)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value.setText(String.valueOf(group.min + progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            /** One write when the finger lifts, not one per pixel of the drag. */
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int picked = group.min + seekBar.getProgress();
                if (picked == group.value) return;
                group.writer.write(picked);
                written();
            }
        });
        holder.addView(bar);
        return holder;
    }

    @NonNull
    private TextView groupLabel(@NonNull String text) {
        TextView label = new TextView(mContext);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        label.setTextColor(color(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant));
        label.setPadding(0, dp(8), 0, dp(6));
        return label;
    }

    // ---- Helpers -------------------------------------------------------------------------------

    @NonNull
    private LinearLayout column() {
        LinearLayout column = new LinearLayout(mContext);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    private static float segmentWidth(@NonNull FrameLayout track, int segmentCount) {
        return Math.max(0f, (track.getWidth() - track.getPaddingLeft() - track.getPaddingRight())
            / (float) segmentCount);
    }

    private static int indexOf(@NonNull String[] values, @NonNull String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return 0;
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }

    private int color(int attr, int fallbackColorRes) {
        return MaterialColors.getColor(mContext, attr,
            androidx.core.content.ContextCompat.getColor(mContext, fallbackColorRes));
    }
}
