package com.termux.app.surfaces;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.termux.R;
import com.termux.app.launcher.data.IconPackChoices;

/**
 * The Icons page until the real one lands: the pack list the Overview's old Icon pack menu showed,
 * as radio rows under a top bar. Picking a row applies it at once through {@link Packs}. It is a
 * stand-in only: the Icon pack page (a home-screen preview over a scrolling row of round pack tiles
 * and the "Pinned app icons only" toggle) replaces it where
 * {@code TermuxActivity.AppearanceSurfaceHost#createIconsPage} says.
 */
public final class AppearanceIconsPlaceholderPage implements AppearanceSurfaceController.Page {

    /** The packs and what picking one does; the activity's. */
    public interface Packs {
        @NonNull IconPackChoices.Listing listing();

        void choose(@NonNull String packageName);
    }

    @NonNull private final Context mContext;
    @NonNull private final Packs mPacks;
    @NonNull private final LinearLayout mRoot;
    @NonNull private final LinearLayout mRows;

    public AppearanceIconsPlaceholderPage(@NonNull Context context, @NonNull Packs packs,
                                   @NonNull AppearanceSurfaceController.Navigator navigator) {
        mContext = context;
        mPacks = packs;
        float density = context.getResources().getDisplayMetrics().density;
        mRoot = new LinearLayout(context);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setBackgroundColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurface, 0));
        mRoot.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton back = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        back.setIconResource(R.drawable.ic_symbol_arrow_back);
        back.setIconSize(Math.round(24 * density));
        back.setIconTint(android.content.res.ColorStateList.valueOf(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0)));
        back.setContentDescription(context.getString(R.string.wallpaper_picker_back));
        back.setOnClickListener(v -> navigator.back());
        bar.addView(back, new LinearLayout.LayoutParams(Math.round(48 * density), Math.round(48 * density)));
        TextView title = new TextView(context);
        title.setText(title());
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        title.setPadding(Math.round(8 * density), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.setPadding(Math.round(4 * density), 0, Math.round(16 * density), 0);
        mRoot.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            Math.round(64 * density)));

        ScrollView scroll = new ScrollView(context);
        mRows = new LinearLayout(context);
        mRows.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * density);
        mRows.setPadding(pad, 0, pad, pad);
        scroll.addView(mRows, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        mRoot.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        fill();
    }

    /** The rows from the packs' listing, the one in force checked. */
    private void fill() {
        mRows.removeAllViews();
        final IconPackChoices.Listing listing = mPacks.listing();
        for (int i = 0; i < listing.entries.size(); i++) {
            final IconPackChoices.Entry entry = listing.entries.get(i);
            MaterialRadioButton row = new MaterialRadioButton(mContext);
            row.setText(entry.label);
            row.setChecked(i == listing.checked);
            final boolean already = i == listing.checked;
            row.setOnClickListener(v -> {
                if (!already) mPacks.choose(entry.value);
                fill();
            });
            mRows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(48 * mContext.getResources().getDisplayMetrics().density)));
        }
    }

    @NonNull @Override public View root() {
        return mRoot;
    }

    @NonNull @Override public CharSequence title() {
        return mContext.getString(R.string.wallpaper_picker_icon_pack);
    }

    @Override public void onShown() {
        fill();
    }

    @Override public void onHidden() {}

    @Override public void release() {
        mRows.removeAllViews();
    }
}
