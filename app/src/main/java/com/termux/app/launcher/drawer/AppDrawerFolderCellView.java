package com.termux.app.launcher.drawer;

import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;

/** Mixed-list folder target. Its four mini icons use the shared cache at their actual pixel size. */
public final class AppDrawerFolderCellView extends AppDrawerAppCellView {
    private final FrameLayout mosaic;

    public AppDrawerFolderCellView(@NonNull android.content.Context context) {
        super(context);
        removeAllViews();
        mosaic = new FrameLayout(context);
        addView(mosaic, new LayoutParams(0, 0));
        addView(label, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void bindFolder(@Nullable SuggestionBarView dock, @NonNull PinnedFolderItem folder,
                           @Nullable AppDrawerGridMetrics metrics,
                           @NonNull ClickGate clickGate) {
        bindFolder(dock, folder, metrics, clickGate, null);
    }

    public void bindFolder(@Nullable SuggestionBarView dock, @NonNull PinnedFolderItem folder,
                           @Nullable AppDrawerGridMetrics metrics,
                           @NonNull ClickGate clickGate,
                           @Nullable AppDrawerPickupDelegate pickupDelegate) {
        bindFolder(dock, folder, metrics == null ? 0 : Math.round(metrics.iconPx),
            metrics == null ? 0 : Math.round(metrics.rowHeightPx), clickGate, pickupDelegate);
    }

    public void bindFolder(@Nullable SuggestionBarView dock, @NonNull PinnedFolderItem folder,
                           @Nullable AppDrawerHorizontalGridMetrics metrics,
                           @NonNull ClickGate clickGate) {
        bindFolder(dock, folder, metrics, clickGate, null);
    }

    public void bindFolder(@Nullable SuggestionBarView dock, @NonNull PinnedFolderItem folder,
                           @Nullable AppDrawerHorizontalGridMetrics metrics,
                           @NonNull ClickGate clickGate,
                           @Nullable AppDrawerPickupDelegate pickupDelegate) {
        bindFolder(dock, folder, metrics == null ? 0 : Math.round(metrics.iconPx),
            metrics == null ? 0 : Math.round(metrics.rowHeightPx), clickGate, pickupDelegate);
    }

    /** The member-icon mosaic, used as the drag shadow when the tile is picked up. */
    @NonNull
    public android.view.View mosaicView() {
        return mosaic;
    }

    private void bindFolder(@Nullable SuggestionBarView dock, @NonNull PinnedFolderItem folder,
                            int iconPx, int rowHeightPx, @NonNull ClickGate clickGate,
                            @Nullable AppDrawerPickupDelegate pickupDelegate) {
        unbind();
        iconPx = Math.max(1, iconPx);
        applyGeometry(rowHeightPx, iconPx);
        // Explicit 2x2 geometry: two 42% minis plus a 6% gap leave 5% of quiet border on every
        // side, so the mosaic reads centered at any icon size instead of riding GridLayout's
        // wrap-content whims.
        int miniPx = Math.max(1, Math.round(iconPx * 0.42f));
        int gapPx = Math.max(1, Math.round(iconPx * 0.06f));
        int padPx = Math.max(0, (iconPx - 2 * miniPx - gapPx) / 2);
        ViewGroup.LayoutParams mosaicParams = mosaic.getLayoutParams();
        mosaicParams.width = iconPx;
        mosaicParams.height = iconPx;
        mosaic.setLayoutParams(mosaicParams);
        int shown = 0;
        for (PinnedAppItem member : folder.apps) {
            if (shown >= 4 || dock == null) break;
            LauncherAppEntry entry = dock.resolveFolderMemberForDrawer(member);
            if (entry == null) continue;
            ImageView mini = new ImageView(getContext());
            mini.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            mini.setImageDrawable(dock.getRenderedIcon(entry, miniPx));
            dock.applyIconColorFilter(mini);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(miniPx, miniPx,
                Gravity.TOP | Gravity.START);
            params.leftMargin = padPx + (shown % 2) * (miniPx + gapPx);
            params.topMargin = padPx + (shown / 2) * (miniPx + gapPx);
            mosaic.addView(mini, params);
            shown++;
        }
        if (folder.apps.size() > 4) {
            TextView count = new TextView(getContext());
            count.setText("+" + (folder.apps.size() - 3));
            count.setTextColor(Color.WHITE);
            count.setTextSize(8f);
            count.setGravity(Gravity.CENTER);
            count.setBackgroundColor(0xB8000000);
            FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(miniPx, miniPx,
                Gravity.TOP | Gravity.START);
            countParams.leftMargin = padPx + miniPx + gapPx;
            countParams.topMargin = padPx + miniPx + gapPx;
            mosaic.addView(count, countParams);
        }
        label.setText(folder.title);
        label.setTextColor(dock == null ? Color.WHITE : dock.getLauncherTextColor());
        setContentDescription(folder.title);
        setOnClickListener(view -> {
            if (!clickGate.suppressCellClick() && dock != null)
                dock.openFolderFromDrawer(folder.id, this);
        });
        // A folder has no app context menu, so the long press means one thing only: pick the tile
        // up and carry it to a new place in the drawer.
        if (pickupDelegate == null) {
            setOnLongClickListener(null);
            setLongClickable(false);
        } else {
            setOnLongClickListener(view -> pickupDelegate.startFolderPickup(this, folder));
        }
    }

    @Override public void unbind() {
        super.unbind();
        if (mosaic != null) mosaic.removeAllViews();
    }
}
