package com.termux.app.surfaces;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;

/**
 * The wallpaper picker's frame over the Appearance editor: a bar with back (to the editor) and
 * Done (keeps the session and closes the editor) over the picker's own content. Opened from the
 * Appearance card's Wallpaper row; {@link AppearanceSurfaceController} stands it over the frame.
 */
public final class WallpaperSheet {

    @NonNull private final View mRoot;
    @NonNull private final View mBar;

    public WallpaperSheet(@NonNull Context context,
                          @NonNull AppearanceSurfaceController.Navigator navigator,
                          @NonNull View content) {
        mRoot = LayoutInflater.from(context).inflate(R.layout.appearance_wallpaper_sheet, null,
            false);
        mBar = mRoot.findViewById(R.id.appearance_wallpaper_bar);
        mRoot.findViewById(R.id.appearance_wallpaper_back).setOnClickListener(v -> navigator.back());
        mRoot.findViewById(R.id.appearance_wallpaper_done).setOnClickListener(v -> navigator.done());
        FrameLayout region = mRoot.findViewById(R.id.appearance_wallpaper_content);
        if (content.getParent() instanceof ViewGroup)
            ((ViewGroup) content.getParent()).removeView(content);
        region.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @NonNull
    public View root() {
        return mRoot;
    }

    /** The bar, for a test. */
    @NonNull
    View bar() {
        return mBar;
    }
}
