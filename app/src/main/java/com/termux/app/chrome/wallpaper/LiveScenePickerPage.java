package com.termux.app.chrome.wallpaper;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.chip.Chip;
import com.termux.R;
import com.termux.app.chrome.scene.LiveWallpaperChoice;
import com.termux.app.chrome.scene.SceneThumbView;

/**
 * The Live half of the wallpaper picker: the five scenes in two columns, each a phone-proportioned
 * {@link SceneThumbView} with its name under it and "In use" on the one that is. Tapping a scene
 * opens its page ({@link Listener#onOpen}). The thumbnails animate only while shown
 * ({@link SceneThumbView} stops them when this page is hidden or detached).
 */
public final class LiveScenePickerPage {

    /** What the page asks of its host; on the main thread. */
    public interface Listener {
        void onOpen(@NonNull String kind);
    }

    private final Context mContext;
    private final View mRoot;
    private final SceneThumbView[] mThumbs = new SceneThumbView[LiveWallpaperChoice.USER_KINDS.length];
    private final View[] mTiles = new View[LiveWallpaperChoice.USER_KINDS.length];
    private final Chip[] mBadges = new Chip[LiveWallpaperChoice.USER_KINDS.length];
    private final TextView[] mNames = new TextView[LiveWallpaperChoice.USER_KINDS.length];

    public LiveScenePickerPage(@NonNull Context context, @NonNull Listener listener) {
        mContext = context;
        LayoutInflater inflater = LayoutInflater.from(context);
        mRoot = inflater.inflate(R.layout.live_scene_picker_page, null, false);
        GridLayout grid = mRoot.findViewById(R.id.live_scene_grid);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float ratio = LiveSceneLogic.frameHeight(1000, dm.widthPixels, dm.heightPixels) / 1000f;
        for (int i = 0; i < LiveWallpaperChoice.USER_KINDS.length; i++) {
            final String kind = LiveWallpaperChoice.USER_KINDS[i];
            View tile = inflater.inflate(R.layout.live_scene_tile, grid, false);
            ((LiveAspectFrame) tile.findViewById(R.id.live_scene_frame)).setRatio(ratio);
            mThumbs[i] = tile.findViewById(R.id.live_scene_thumb);
            mBadges[i] = tile.findViewById(R.id.live_scene_in_use);
            mNames[i] = tile.findViewById(R.id.live_scene_name);
            mNames[i].setText(LiveSceneLogic.nameRes(kind));
            tile.setOnClickListener(v -> listener.onOpen(kind));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            grid.addView(tile, lp);
            mTiles[i] = tile;
        }
    }

    @NonNull
    public View root() {
        return mRoot;
    }

    /** Shows {@code stored}: the scene in use wears its own knobs and the chip, the rest their defaults. */
    public void bind(@NonNull LiveWallpaperChoice stored) {
        for (int i = 0; i < LiveWallpaperChoice.USER_KINDS.length; i++) {
            String kind = LiveWallpaperChoice.USER_KINDS[i];
            boolean inUse = LiveSceneLogic.inUse(stored, kind);
            mThumbs[i].setChoice(LiveSceneLogic.detailStart(stored, kind));
            mBadges[i].setVisibility(inUse ? View.VISIBLE : View.GONE);
            String name = mContext.getString(LiveSceneLogic.nameRes(kind));
            mTiles[i].setContentDescription(inUse ? name + ", " + mContext.getString(R.string.live_in_use) : name);
        }
    }

    /** The chip of the scene at {@code index} of {@link LiveWallpaperChoice#USER_KINDS}, for tests. */
    @NonNull
    Chip badge(int index) {
        return mBadges[index];
    }

    @NonNull
    View tile(int index) {
        return mTiles[index];
    }
}
