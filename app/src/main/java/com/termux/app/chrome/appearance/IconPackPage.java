package com.termux.app.chrome.appearance;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.launcher.data.IconPackChoices;
import com.termux.app.launcher.data.IconPackRepository;
import com.termux.app.launcher.icon.DrawablePixels;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Icon pack controls of the Appearance editor's Icons mode: a row of round pack tiles
 * (Default, then each installed pack; the one in force ringed) and the <b>Pinned app icons
 * only</b> switch. They live in the editor's bottom sheet; the real launcher stands above in the
 * frame, so a tap shows the pack on the real dock.
 *
 * <p>A plain {@link View} tree: the editor puts {@link #root()} in its sheet. A tap on a tile
 * rings it at once, parses the pack off the main thread ({@link Backend#warm}) and only then
 * writes the choice through {@link Backend#apply} (live: the preference and the artwork, no
 * restyle), so the dock's redraw never parses on the main thread. Taps in quick succession
 * collapse: only the last one is parsed and written. With the switch on the choice is written to
 * {@link IconPackChoices#KEY_PINNED}; with it off to {@link IconPackChoices#KEY_GLOBAL} and the
 * pinned override is cleared, so the dock follows the global pack again. The Default tile stores
 * "" under the key in force and reads "Same as app icons" while the switch is on (the dock then
 * follows the global pack), "Default" while it is off (the system's icons).
 *
 * <p>Everything slow (the pack listing, the pack artwork, the parse) is loaded on a background
 * executor; the tiles show placeholders until it lands. Every read of the launcher's state goes
 * through {@link Backend}, so the tests run on fakes.
 */
public final class IconPackPage {

    /** What the surface hosting the page provides; the page knows nothing of the activity. */
    public interface Host {
        /** The choice was written live (nothing restyled): the host owes a restyle when it closes. */
        default void onApplied() {}

        /**
         * The tile row was rebuilt with a different set of packs (the listing landed): the page
         * may now be a different height, so the sheet holding it measures it again.
         */
        default void onContentChanged() {}
    }

    /**
     * The launcher state the page reads and writes. {@link #real} is the production one; every
     * call but {@link #current} and {@link #apply} runs off the main thread.
     */
    interface Backend {
        /** The installed packs, in listing order, without the Default row (slow: asks the package manager). */
        @NonNull List<IconPackChoices.Entry> packs();

        /** The pack's own launcher icon, null for "" or when it has none. */
        @Nullable Drawable packArt(@NonNull String pack);

        /** Parses {@code pack} (slow) so the launcher's first icon from it is cached; off the main thread. */
        default void warm(@NonNull String pack) {}

        /** The stored package for {@code key}, "" when unset. */
        @NonNull String current(@NonNull String key);

        /** Stores {@code value} for {@code key} and has the launcher's artwork follow, restyling nothing. */
        void apply(@NonNull String key, @NonNull String value);

        @NonNull
        static Backend real(@NonNull Context context) {
            final Context app = context.getApplicationContext();
            return new Backend() {
                @NonNull @Override public List<IconPackChoices.Entry> packs() {
                    List<IconPackChoices.Entry> all = IconPackChoices.entries("",
                        new IconPackRepository(app).discoverIconPacks(), false);
                    return new ArrayList<>(all.subList(1, all.size()));
                }

                @Nullable @Override public Drawable packArt(@NonNull String pack) {
                    if (pack.isEmpty()) return null;
                    try {
                        return app.getPackageManager().getApplicationIcon(pack);
                    } catch (PackageManager.NameNotFoundException e) {
                        return null;
                    }
                }

                @Override public void warm(@NonNull String pack) {
                    IconPackChoices.warm(app, pack);
                }

                @NonNull @Override public String current(@NonNull String key) {
                    return IconPackChoices.current(TermuxAppSharedPreferences.build(app, false), key);
                }

                @Override public void apply(@NonNull String key, @NonNull String value) {
                    IconPackChoices.applyLive(app, TermuxAppSharedPreferences.build(app, false), key, value);
                }
            };
        }
    }

    /** The pack art is drawn inside the 64dp tile's 12dp padding: 40dp. */
    private static final int ART_DP = 40;

    @NonNull private final Context context;
    @NonNull private final Host host;
    @NonNull private final Backend backend;
    @NonNull private final Executor background;
    @NonNull private final Executor main;
    @Nullable private final ExecutorService ownedExecutor;

    @NonNull private final View root;
    @NonNull private final LinearLayout tiles;
    @NonNull private final MaterialSwitch pinnedOnly;

    /** The installed packs; the Default row is not in it. */
    @NonNull private List<IconPackChoices.Entry> packs = new ArrayList<>();
    /** Each installed pack's tile art, shrunk to the tile: as many small drawables as the row has tiles. */
    @NonNull private final Map<String, Drawable> art = new HashMap<>();
    /** The package in force for the scope the switch names; "" is the Default tile. */
    @NonNull private String selected = "";
    private boolean pinnedOnlyOn;
    private boolean released;
    /** Bumped on every listing load, so a slow one never lands over a newer one. */
    private int loadGeneration;
    /** Bumped on every tap, so only the last one parses and writes (read on the background thread too). */
    private volatile int applyGeneration;
    /** A tap has not been written yet: a release writes it rather than lose it. */
    private boolean writePending;
    /**
     * The tile set the row holds now (the scope and every pack's value and label), or null before
     * the first build: a choice or a listing that leaves it the same moves the ring in place and
     * never tears the row down, so nothing in the sheet blinks.
     */
    @Nullable private String shownTiles;

    /** The production page: loads on its own single background thread, until {@link #release}. */
    public IconPackPage(@NonNull Context context, @NonNull Host host) {
        this(context, host, Backend.real(context), null, null);
    }

    IconPackPage(@NonNull Context context, @NonNull Host host, @NonNull Backend backend,
                 @Nullable Executor background, @Nullable Executor main) {
        this.context = context;
        this.host = host;
        this.backend = backend;
        if (background == null) {
            ownedExecutor = Executors.newSingleThreadExecutor();
            this.background = ownedExecutor;
        } else {
            ownedExecutor = null;
            this.background = background;
        }
        if (main == null) {
            Handler handler = new Handler(Looper.getMainLooper());
            this.main = handler::post;
        } else {
            this.main = main;
        }

        root = LayoutInflater.from(context).inflate(R.layout.icon_pack_page, null, false);
        tiles = root.findViewById(R.id.icon_pack_tiles);
        pinnedOnly = root.findViewById(R.id.icon_pack_pinned_only);

        pinnedOnly.setOnCheckedChangeListener((button, checked) -> {
            if (checked == pinnedOnlyOn) return;
            pinnedOnlyOn = checked;
            commitChoice();
            showTiles();
        });
        onShown();
    }

    /** The page's view tree; the editor puts it in its sheet. */
    @NonNull
    public View root() {
        return root;
    }

    /** The mode's name, as the bar reads it. */
    @NonNull
    public String title() {
        return context.getString(R.string.icon_pack_page_title);
    }

    /**
     * The page came into view: re-read the choice and the packs. A row that already holds the
     * same tiles only moves its ring; a listing that lands with the same packs changes nothing.
     */
    public void onShown() {
        if (released) return;
        readChoice();
        pinnedOnly.setChecked(pinnedOnlyOn);
        showTiles();
        final int gen = ++loadGeneration;
        background.execute(() -> {
            final List<IconPackChoices.Entry> loadedPacks = backend.packs();
            main.execute(() -> {
                if (released || gen != loadGeneration) return;
                packs = new ArrayList<>(loadedPacks);
                if (showTiles()) host.onContentChanged();
            });
        });
    }

    /** The page went out of view; a listing still in flight is dropped. */
    public void onHidden() {
        loadGeneration++;
    }

    /**
     * The surface is done with the page: later loads are dropped and the background thread ends. A
     * tap not yet written is written first, so the choice shown is the choice kept.
     */
    public void release() {
        if (writePending && !released) writeChoice();
        released = true;
        loadGeneration++;
        applyGeneration++;
        art.clear();
        if (ownedExecutor != null) ownedExecutor.shutdownNow();
    }

    /** The pack in force for the scope the switch names ("" for Default). */
    @NonNull
    String selectedPack() {
        return selected;
    }

    boolean pinnedOnly() {
        return pinnedOnlyOn;
    }

    @NonNull
    LinearLayout tilesView() {
        return tiles;
    }

    @NonNull
    MaterialSwitch pinnedOnlySwitch() {
        return pinnedOnly;
    }

    /** ON when the pinned key is set, else OFF when the global one is, else ON (nothing is set). */
    private void readChoice() {
        // A tap still waiting for its parse is the choice in force: the stored one is behind it.
        if (writePending) return;
        String pinnedPack = backend.current(IconPackChoices.KEY_PINNED);
        String globalPack = backend.current(IconPackChoices.KEY_GLOBAL);
        pinnedOnlyOn = !pinnedPack.isEmpty() || globalPack.isEmpty();
        selected = pinnedOnlyOn ? pinnedPack : globalPack;
    }

    /** Stores {@link #selected} under the key the switch names and tells the host. */
    private void writeChoice() {
        writePending = false;
        if (pinnedOnlyOn) {
            backend.apply(IconPackChoices.KEY_PINNED, selected);
        } else {
            backend.apply(IconPackChoices.KEY_GLOBAL, selected);
            backend.apply(IconPackChoices.KEY_PINNED, "");
        }
        host.onApplied();
    }

    /**
     * Parses the chosen pack off the main thread, then writes the choice on it. Each call
     * supersedes the one before: a tap that a later one overtook neither parses nor writes.
     */
    private void commitChoice() {
        final int gen = ++applyGeneration;
        final String pack = selected;
        writePending = true;
        if (pack.isEmpty()) {
            writeChoice();
            return;
        }
        background.execute(() -> {
            if (gen != applyGeneration) return;
            backend.warm(pack);
            main.execute(() -> {
                if (released || gen != applyGeneration) return;
                writeChoice();
            });
        });
    }

    private void select(@NonNull String pack) {
        selected = pack;
        commitChoice();
        showTiles();
    }

    private int dp(float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    private int color(int attr) {
        return MaterialColors.getColor(root, attr);
    }

    @NonNull
    private GradientDrawable circle(int fill, int stroke, int strokeWidthPx) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        if (strokeWidthPx > 0) g.setStroke(strokeWidthPx, stroke);
        return g;
    }

    /**
     * Puts the row in step with the scope, the packs and the choice. The tiles are built again
     * only when the set of them changed (a new listing, the Default tile's name with the scope);
     * otherwise the ring moves on the tiles already there.
     *
     * @return whether the row was built again, and so may have a new height
     */
    private boolean showTiles() {
        String set = tileSet();
        if (set.equals(shownTiles) && tiles.getChildCount() == packs.size() + 1) {
            for (int i = 0; i < tiles.getChildCount(); i++) {
                View tile = tiles.getChildAt(i);
                Object value = tile.getTag();
                TextView text = tile.findViewById(R.id.icon_pack_tile_label);
                styleTile(tile, text.getText(), value instanceof String ? (String) value : "");
            }
            return false;
        }
        shownTiles = set;
        tiles.removeAllViews();
        addTile(context.getString(pinnedOnlyOn
            ? R.string.icon_pack_page_default_global : R.string.icon_pack_page_default_system), "");
        for (IconPackChoices.Entry pack : packs) addTile(pack.label, pack.value);
        return true;
    }

    /** What the row's tiles are: the scope (it names the Default tile) and each pack in order. */
    @NonNull
    private String tileSet() {
        StringBuilder set = new StringBuilder(pinnedOnlyOn ? "pinned" : "global");
        for (IconPackChoices.Entry pack : packs)
            set.append('\n').append(pack.value).append('\t').append(pack.label);
        return set.toString();
    }

    /** The ring, the selected state and the spoken name of one tile for the current choice. */
    private void styleTile(@NonNull View tile, @NonNull CharSequence label, @NonNull String value) {
        ImageView image = tile.findViewById(R.id.icon_pack_tile_art);
        boolean on = value.equals(selected);
        // The ring: 3dp primary on the selected tile, a 1dp outline on the rest.
        image.setBackground(circle(color(com.google.android.material.R.attr.colorSurfaceContainerHigh),
            on ? color(androidx.appcompat.R.attr.colorPrimary)
                : color(com.google.android.material.R.attr.colorOutlineVariant), dp(on ? 3 : 1)));
        tile.setSelected(on);
        tile.setContentDescription(on
            ? context.getString(R.string.icon_pack_page_tile_selected, label) : label);
    }

    private void addTile(@NonNull CharSequence label, @NonNull final String value) {
        View tile = LayoutInflater.from(context).inflate(R.layout.icon_pack_tile, tiles, false);
        final ImageView image = tile.findViewById(R.id.icon_pack_tile_art);
        TextView text = tile.findViewById(R.id.icon_pack_tile_label);
        text.setText(label);
        tile.setTag(value);
        styleTile(tile, label, value);
        tile.setOnClickListener(v -> select(value));
        tiles.addView(tile);

        // The Default tile has no pack of its own, so it shows the platform's generic app icon at
        // once; a pack's own icon lands from the background, the bare circle holding its place.
        if (value.isEmpty()) {
            image.setImageResource(android.R.drawable.sym_def_app_icon);
            return;
        }
        Drawable cached = art.get(value);
        if (cached != null) {
            image.setImageDrawable(cached);
            return;
        }
        final int size = dp(ART_DP);
        background.execute(() -> {
            // Held for the page's life and drawn at 40dp: never more pixels than that.
            final Drawable d = DrawablePixels.shrink(context.getResources(), backend.packArt(value), size);
            main.execute(() -> {
                if (released || d == null) return;
                art.put(value, d);
                image.setImageDrawable(d);
            });
        });
    }
}
