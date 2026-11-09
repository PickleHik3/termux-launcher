package com.termux.app.chrome.wallpaper;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.chrome.scene.SceneThumbView;
import com.termux.app.chrome.scene.ShaderExamples;
import com.termux.app.chrome.scene.ShaderStore;
import com.termux.app.chrome.scene.WallpaperMotion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Shader half of the wallpaper picker: the saved shaders in three columns, each a
 * phone-proportioned {@link SceneThumbView} with its name under it and a border on the one in
 * use, then New shader, and Back to photo while a video or a shader is in use. Tapping a shader
 * opens the editor ({@link Listener#onOpen}). The shaders are read off the main thread.
 */
public final class ShaderListPage {

    /** What the page asks of its host; on the main thread. */
    public interface Listener {
        /** A saved shader's editor. */
        void onOpen(@NonNull String id);

        /** A bundled example's editor, by slug. */
        void onOpenExample(@NonNull String slug);

        void onNew();

        void onBackToPhoto();
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ShaderList");
        t.setDaemon(true);
        return t;
    });

    /** One shader's source and texture, read for a tile. */
    private static final class Loaded {
        @NonNull final ShaderStore.Entry entry;
        @Nullable final String source;
        @Nullable final File texture;

        Loaded(@NonNull ShaderStore.Entry entry, @Nullable String source, @Nullable File texture) {
            this.entry = entry;
            this.source = source;
            this.texture = texture;
        }
    }

    private static final int COLUMNS = 3;

    private final Context mContext;
    private final Listener mListener;
    private final View mRoot;
    private final ScrollView mScroll;
    private final GridLayout mGrid;
    private final MaterialButton mBack;
    private final float mRatio;
    private final List<View> mTiles = new ArrayList<>();
    /** Moves with every bind, so a late read for an older one is dropped. */
    private int mGeneration;
    private boolean mReleased;

    public ShaderListPage(@NonNull Context context, @NonNull Listener listener) {
        mContext = context;
        mListener = listener;
        mRoot = LayoutInflater.from(context).inflate(R.layout.shader_list_page, null, false);
        mScroll = mRoot.findViewById(R.id.shader_list_scroll);
        mGrid = mRoot.findViewById(R.id.shader_list_grid);
        mBack = mRoot.findViewById(R.id.shader_list_back_to_photo);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mRatio = LiveSceneLogic.frameHeight(1000, dm.widthPixels, dm.heightPixels) / 1000f;
        mRoot.findViewById(R.id.shader_new).setOnClickListener(v -> mListener.onNew());
        mBack.setOnClickListener(v -> mListener.onBackToPhoto());
    }

    @NonNull
    public View root() {
        return mRoot;
    }

    public void release() {
        mReleased = true;
        mGeneration++;
    }

    /** How far the list is scrolled, to put back when it returns from the editor. */
    int scrollY() {
        return mScroll.getScrollY();
    }

    /** Scrolls the list to {@code y}; a no-op when it is already there. */
    void restoreScrollY(int y) {
        if (mScroll.getScrollY() != y) mScroll.scrollTo(0, y);
    }

    /**
     * {@link #restoreScrollY} once the list has its size: a relaunch's list is not laid out yet,
     * and a scroll before then is clamped to nothing.
     */
    void restoreScrollYWhenLaidOut(int y) {
        if (mScroll.isLaidOut() && !mScroll.isLayoutRequested()) {
            restoreScrollY(y);
            return;
        }
        mScroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                v.removeOnLayoutChangeListener(this);
                restoreScrollY(y);
            }
        });
    }

    /** Shows the saved shaders with the one in {@code stored} marked, and Back to photo while one moves. */
    public void bind(@NonNull WallpaperMotion stored) {
        mBack.setVisibility(LiveSceneLogic.showsBackToPhoto(stored) ? View.VISIBLE : View.GONE);
        final int generation = ++mGeneration;
        final Context app = mContext.getApplicationContext();
        IO.execute(() -> {
            final List<Loaded> loaded = new ArrayList<>();
            for (ShaderStore.Entry e : ShaderStore.list(app)) {
                String source = ShaderStore.readText(ShaderStore.sourceFile(app, e.id));
                ShaderStore.Meta meta = ShaderStore.readMeta(app, e.id);
                loaded.add(new Loaded(e, source, ShaderStore.textureFile(app, e.id, meta)));
            }
            final List<Loaded> examples = new ArrayList<>();
            for (ShaderExamples.Example e : ShaderExamples.load(app)) {
                examples.add(new Loaded(new ShaderStore.Entry(e.slug, e.name), e.source, null));
            }
            mRoot.post(() -> {
                if (mReleased || generation != mGeneration) return;
                show(loaded, examples, stored);
            });
        });
    }

    private void show(@NonNull List<Loaded> loaded, @NonNull List<Loaded> examples,
                      @NonNull WallpaperMotion stored) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mGrid.removeAllViews();
        mTiles.clear();
        // Three phone-aspect columns at explicit widths: a weighted column measures its tile before
        // the grid has a width, and the frame then takes the whole page.
        int gridW = mGrid.getWidth();
        if (gridW <= 0) {
            gridW = mContext.getResources().getDisplayMetrics().widthPixels
                - 2 * mContext.getResources().getDimensionPixelSize(R.dimen.settings_gutter);
        }
        final int tileW = Math.max(1, gridW / COLUMNS);
        final DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        final float radius = 16f * dm.density;
        final int accent = com.google.android.material.color.MaterialColors.getColor(mContext,
            androidx.appcompat.R.attr.colorPrimary, 0);
        for (ShaderEditorLogic.Slot slot : ShaderEditorLogic.listOrder(loaded.size(), examples.size())) {
            if (slot.kind == ShaderEditorLogic.Slot.EXAMPLES_HEADING) {
                TextView heading = new TextView(mContext);
                heading.setText(R.string.shader_examples_heading);
                heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
                heading.setTextColor(com.google.android.material.color.MaterialColors.getColor(mContext,
                    com.google.android.material.R.attr.colorOnSurface, 0));
                heading.setSingleLine(true);
                heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
                heading.setAccessibilityHeading(true);
                heading.setPadding(Math.round(6f * dm.density), Math.round(16f * dm.density), 0,
                    Math.round(8f * dm.density));
                GridLayout.LayoutParams hp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(0, COLUMNS));
                hp.width = tileW * COLUMNS;
                hp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mGrid.addView(heading, hp);
                continue;
            }
            final boolean example = slot.kind == ShaderEditorLogic.Slot.EXAMPLE;
            final Loaded l = example ? examples.get(slot.index) : loaded.get(slot.index);
            final String id = l.entry.id;
            View tile = inflater.inflate(R.layout.live_scene_tile, mGrid, false);
            ((LiveAspectFrame) tile.findViewById(R.id.live_scene_frame)).setRatio(mRatio);
            ((SceneThumbView) tile.findViewById(R.id.live_scene_thumb)).setShader(l.source, l.texture);
            TextView name = tile.findViewById(R.id.live_scene_name);
            name.setText(l.entry.name);
            boolean inUse = !example && LiveSceneLogic.inUse(stored, id);
            // In use: a border round the thumbnail (the chip is too wide for a third of the page).
            tile.findViewById(R.id.live_scene_in_use).setVisibility(View.GONE);
            LiveAspectFrame frame = tile.findViewById(R.id.live_scene_frame);
            if (inUse) {
                android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
                ring.setCornerRadius(radius);
                ring.setStroke(Math.round(2f * mContext.getResources().getDisplayMetrics().density), accent);
                frame.setForeground(ring);
            } else {
                frame.setForeground(null);
            }
            tile.setContentDescription(inUse ? l.entry.name + ", " + mContext.getString(R.string.live_in_use)
                : l.entry.name);
            tile.setOnClickListener(v -> {
                if (example) mListener.onOpenExample(id);
                else mListener.onOpen(id);
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = tileW;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mGrid.addView(tile, lp);
            mTiles.add(tile);
        }
    }

    // --- tests ---

    int tileCount() {
        return mTiles.size();
    }

    @NonNull
    View newButton() {
        return mRoot.findViewById(R.id.shader_new);
    }

    @NonNull
    View backToPhotoButton() {
        return mBack;
    }
}
