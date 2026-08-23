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

/**
 * Mixed-list folder target. Its four mini icons reuse the grid-size render from the shared cache
 * (the ImageView downscales), so a folder never mints a second cache entry per member at its own
 * mini pixel size.
 */
public final class AppDrawerFolderCellView extends AppDrawerAppCellView {
    private final FrameLayout mosaic;
    /** Lazily created once and rebound in place: a bind must not rebuild the mosaic's view tree. */
    private final ImageView[] minis = new ImageView[4];
    @Nullable private TextView countBadge;

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
            ImageView mini = minis[shown];
            if (mini == null) {
                mini = new ImageView(getContext());
                mini.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                minis[shown] = mini;
                // Under the count badge if one already exists — the badge always draws on top.
                int insertAt = countBadge == null ? -1 : mosaic.indexOfChild(countBadge);
                mosaic.addView(mini, insertAt, new FrameLayout.LayoutParams(miniPx, miniPx,
                    Gravity.TOP | Gravity.START));
            }
            applySlotGeometry(mini, miniPx,
                padPx + (shown % 2) * (miniPx + gapPx),
                padPx + (shown / 2) * (miniPx + gapPx));
            // Grid-size render on purpose: the cache key carries the pixel size, so asking at
            // miniPx would mint a second entry per member; the view downscales the shared one.
            mini.setImageDrawable(dock.getRenderedIcon(entry, iconPx));
            dock.applyIconColorFilter(mini);
            mini.setVisibility(VISIBLE);
            shown++;
        }
        for (int i = shown; i < minis.length; i++) {
            ImageView mini = minis[i];
            if (mini == null) continue;
            mini.setImageDrawable(null);
            mini.setVisibility(GONE);
        }
        if (folder.apps.size() > 4) {
            TextView count = countBadge;
            if (count == null) {
                count = new TextView(getContext());
                count.setTextColor(Color.WHITE);
                count.setTextSize(8f);
                count.setGravity(Gravity.CENTER);
                count.setBackgroundColor(0xB8000000);
                countBadge = count;
                mosaic.addView(count, new FrameLayout.LayoutParams(miniPx, miniPx,
                    Gravity.TOP | Gravity.START));
            }
            count.setText("+" + (folder.apps.size() - 3));
            applySlotGeometry(count, miniPx, padPx + miniPx + gapPx, padPx + miniPx + gapPx);
            count.setVisibility(VISIBLE);
        } else if (countBadge != null) {
            countBadge.setVisibility(GONE);
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

    /** Positions one mosaic slot in place, touching layout only when the geometry moved. */
    private static void applySlotGeometry(@NonNull android.view.View slot, int sizePx,
                                          int leftPx, int topPx) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slot.getLayoutParams();
        if (params.width != sizePx || params.height != sizePx
            || params.leftMargin != leftPx || params.topMargin != topPx) {
            params.width = sizePx;
            params.height = sizePx;
            params.leftMargin = leftPx;
            params.topMargin = topPx;
            slot.setLayoutParams(params);
        }
    }

    @Override public void unbind() {
        super.unbind();
        // The slots are retained (a rebind must not rebuild the view tree); only their rendered
        // artwork is released.
        for (ImageView mini : minis) {
            if (mini == null) continue;
            mini.setImageDrawable(null);
            mini.setVisibility(GONE);
        }
        if (countBadge != null) countBadge.setVisibility(GONE);
    }
}
