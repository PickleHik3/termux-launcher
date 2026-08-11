package com.termux.app.launcher.drawer;

import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
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
        bindFolder(dock, folder, metrics == null ? 0 : Math.round(metrics.iconPx),
            metrics == null ? 0 : Math.round(metrics.rowHeightPx), clickGate);
    }

    public void bindFolder(@Nullable SuggestionBarView dock, @NonNull PinnedFolderItem folder,
                           @Nullable AppDrawerHorizontalGridMetrics metrics,
                           @NonNull ClickGate clickGate) {
        bindFolder(dock, folder, metrics == null ? 0 : Math.round(metrics.iconPx),
            metrics == null ? 0 : Math.round(metrics.rowHeightPx), clickGate);
    }

    private void bindFolder(@Nullable SuggestionBarView dock, @NonNull PinnedFolderItem folder,
                            int iconPx, int rowHeightPx, @NonNull ClickGate clickGate) {
        unbind();
        iconPx = Math.max(1, iconPx);
        applyGeometry(rowHeightPx, iconPx);
        int miniPx = Math.max(1, Math.round(iconPx * 0.42f));
        ViewGroup.LayoutParams mosaicParams = mosaic.getLayoutParams();
        mosaicParams.width = iconPx;
        mosaicParams.height = iconPx;
        mosaic.setLayoutParams(mosaicParams);
        GridLayout grid = new GridLayout(getContext());
        grid.setColumnCount(2);
        grid.setRowCount(2);
        int shown = 0;
        for (PinnedAppItem member : folder.apps) {
            if (shown >= 4 || dock == null) break;
            LauncherAppEntry entry = dock.resolveFolderMemberForDrawer(member);
            if (entry == null) continue;
            ImageView mini = new ImageView(getContext());
            mini.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            mini.setImageDrawable(dock.getRenderedIcon(entry, miniPx));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(shown / 2), GridLayout.spec(shown % 2));
            params.width = miniPx;
            params.height = miniPx;
            mini.setLayoutParams(params);
            grid.addView(mini);
            shown++;
        }
        mosaic.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        if (folder.apps.size() > 4) {
            TextView count = new TextView(getContext());
            count.setText("+" + (folder.apps.size() - 3));
            count.setTextColor(Color.WHITE);
            count.setTextSize(8f);
            count.setGravity(Gravity.CENTER);
            count.setBackgroundColor(0xB8000000);
            mosaic.addView(count, new FrameLayout.LayoutParams(miniPx, miniPx,
                Gravity.END | Gravity.BOTTOM));
        }
        label.setText(folder.title);
        label.setTextColor(dock == null ? Color.WHITE : dock.getLauncherTextColor());
        setContentDescription(folder.title);
        setOnClickListener(view -> {
            if (!clickGate.suppressCellClick() && dock != null)
                dock.openFolderFromDrawer(folder.id, this);
        });
    }

    @Override public void unbind() {
        super.unbind();
        if (mosaic != null) mosaic.removeAllViews();
    }
}
