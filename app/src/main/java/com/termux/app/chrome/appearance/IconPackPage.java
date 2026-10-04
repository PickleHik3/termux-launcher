package com.termux.app.chrome.appearance;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.LauncherIconResolver;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Icon pack page of the Appearance surface: a phone-shaped Home preview with the user's pinned
 * apps drawn from the previewed pack, a row of round pack tiles (Default, then each installed
 * pack; the one in force ringed) and the <b>Pinned app icons only</b> switch.
 *
 * <p>A plain {@link View} tree: the surface adds {@link #root()} to its pager and supplies the top
 * bar, showing {@link #title()}. Tapping a tile previews that pack at once and applies it through
 * {@link IconPackChoices#apply}: with the switch on the choice is written to
 * {@link IconPackChoices#KEY_PINNED}; with it off to {@link IconPackChoices#KEY_GLOBAL} and the
 * pinned override is cleared, so the dock follows the global pack again. The Default tile stores
 * "" under the key in force and reads "Same as app icons" while the switch is on (the dock then
 * follows the global pack), "Default" while it is off (the system's icons).
 *
 * <p>Everything slow (the pack listing, the pinned apps, the pack and app artwork) is loaded on a
 * background executor; the tiles and icons show placeholders until it lands. Every read of the
 * launcher's state goes through {@link Backend}, so the tests run on fakes.
 */
public final class IconPackPage {

    /** What the surface hosting the page provides; the page knows nothing of the activity. */
    public interface Host {
        /** The Home wallpaper still for the preview card, null for none (the card stays tonal). */
        @Nullable Bitmap homeStill();

        /** The choice was written and the launcher is restyling; the surface may refresh its own view. */
        default void onApplied() {}
    }

    /**
     * The launcher state the page reads and writes. {@link #real} is the production one; every
     * call but {@link #current} and {@link #apply} runs off the main thread.
     */
    interface Backend {
        /** The installed packs, in listing order, without the Default row (slow: asks the package manager). */
        @NonNull List<IconPackChoices.Entry> packs();

        /** The apps pinned in the dock, in order. */
        @NonNull List<AppRef> pinnedApps();

        /** {@code ref}'s icon in {@code pack} ("" = the system's), null when it cannot be drawn. */
        @Nullable Drawable icon(@NonNull String pack, @NonNull AppRef ref);

        /** The pack's own launcher icon, null for "" or when it has none. */
        @Nullable Drawable packArt(@NonNull String pack);

        /** The stored package for {@code key}, "" when unset. */
        @NonNull String current(@NonNull String key);

        /** Stores {@code value} for {@code key} and has the launcher pick it up. */
        void apply(@NonNull String key, @NonNull String value);

        @NonNull
        static Backend real(@NonNull Context context) {
            final Context app = context.getApplicationContext();
            final LauncherIconResolver resolver = new LauncherIconResolver(app);
            return new Backend() {
                @NonNull @Override public List<IconPackChoices.Entry> packs() {
                    List<IconPackChoices.Entry> all = IconPackChoices.entries("",
                        new IconPackRepository(app).discoverIconPacks(), false);
                    return new ArrayList<>(all.subList(1, all.size()));
                }

                @NonNull @Override public List<AppRef> pinnedApps() {
                    List<AppRef> out = new ArrayList<>();
                    for (PinnedItem item : LauncherConfigRepository.getInstance(app).loadPinnedItems()) {
                        if (item instanceof PinnedAppItem) out.add(((PinnedAppItem) item).appRef);
                    }
                    return out;
                }

                @Nullable @Override public Drawable icon(@NonNull String pack, @NonNull AppRef ref) {
                    if (!pack.isEmpty()) {
                        Drawable d = resolver.loadFromPack(pack, ref);
                        if (d != null) return d;
                    }
                    try {
                        return app.getPackageManager().getActivityIcon(
                            new ComponentName(ref.packageName, ref.activityName));
                    } catch (Exception e) {
                        try {
                            return app.getPackageManager().getApplicationIcon(ref.packageName);
                        } catch (Exception ignored) {
                            return null;
                        }
                    }
                }

                @Nullable @Override public Drawable packArt(@NonNull String pack) {
                    if (pack.isEmpty()) return null;
                    try {
                        return app.getPackageManager().getApplicationIcon(pack);
                    } catch (PackageManager.NameNotFoundException e) {
                        return null;
                    }
                }

                @NonNull @Override public String current(@NonNull String key) {
                    return IconPackChoices.current(TermuxAppSharedPreferences.build(app, false), key);
                }

                @Override public void apply(@NonNull String key, @NonNull String value) {
                    IconPackChoices.apply(app, TermuxAppSharedPreferences.build(app, false), key, value);
                }
            };
        }
    }

    /** How many pinned apps the preview draws, four to a row. */
    static final int PREVIEW_APPS = 8;
    private static final int PREVIEW_COLUMNS = 4;

    @NonNull private final Context context;
    @NonNull private final Host host;
    @NonNull private final Backend backend;
    @NonNull private final Executor background;
    @NonNull private final Executor main;
    @Nullable private final ExecutorService ownedExecutor;

    @NonNull private final View root;
    @NonNull private final ImageView previewStill;
    @NonNull private final LinearLayout previewApps;
    @NonNull private final LinearLayout tiles;
    @NonNull private final MaterialSwitch pinnedOnly;

    /** The installed packs; the Default row is not in it. */
    @NonNull private List<IconPackChoices.Entry> packs = new ArrayList<>();
    @NonNull private List<AppRef> pinned = new ArrayList<>();
    /** The package in force for the scope the switch names; "" is the Default tile. */
    @NonNull private String selected = "";
    private boolean pinnedOnlyOn;
    private boolean released;
    /** Bumped on every listing load, so a slow one never lands over a newer one. */
    private int loadGeneration;
    /** Bumped on every preview, so a slow icon never draws over a newer pack's. */
    private int previewGeneration;

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
        previewStill = root.findViewById(R.id.icon_pack_preview_still);
        previewApps = root.findViewById(R.id.icon_pack_preview_apps);
        tiles = root.findViewById(R.id.icon_pack_tiles);
        pinnedOnly = root.findViewById(R.id.icon_pack_pinned_only);

        pinnedOnly.setOnCheckedChangeListener((button, checked) -> {
            if (checked == pinnedOnlyOn) return;
            pinnedOnlyOn = checked;
            writeChoice();
            rebuildTiles();
            drawPreview();
        });
        onShown();
    }

    /** The page's view tree; the surface adds it and sizes it to the pager. */
    @NonNull
    public View root() {
        return root;
    }

    /** What the surface's top bar reads while this page is up. */
    @NonNull
    public String title() {
        return context.getString(R.string.icon_pack_page_title);
    }

    /** The page came into view: re-read the choice, the wallpaper still, the packs and the pinned apps. */
    public void onShown() {
        if (released) return;
        readChoice();
        pinnedOnly.setChecked(pinnedOnlyOn);
        previewStill.setImageBitmap(host.homeStill());
        rebuildTiles();
        drawPreview();
        final int gen = ++loadGeneration;
        background.execute(() -> {
            final List<IconPackChoices.Entry> loadedPacks = backend.packs();
            final List<AppRef> loadedApps = backend.pinnedApps();
            main.execute(() -> {
                if (released || gen != loadGeneration) return;
                packs = new ArrayList<>(loadedPacks);
                pinned = new ArrayList<>(loadedApps);
                rebuildTiles();
                drawPreview();
            });
        });
    }

    /** The page went out of view; a listing still in flight is dropped. */
    public void onHidden() {
        loadGeneration++;
    }

    /** The surface is done with the page: later loads are dropped and the background thread ends. */
    public void release() {
        released = true;
        loadGeneration++;
        previewGeneration++;
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

    @NonNull
    LinearLayout previewAppsView() {
        return previewApps;
    }

    /** ON when the pinned key is set, else OFF when the global one is, else ON (nothing is set). */
    private void readChoice() {
        String pinnedPack = backend.current(IconPackChoices.KEY_PINNED);
        String globalPack = backend.current(IconPackChoices.KEY_GLOBAL);
        pinnedOnlyOn = !pinnedPack.isEmpty() || globalPack.isEmpty();
        selected = pinnedOnlyOn ? pinnedPack : globalPack;
    }

    /** Stores {@link #selected} under the key the switch names and tells the host. */
    private void writeChoice() {
        if (pinnedOnlyOn) {
            backend.apply(IconPackChoices.KEY_PINNED, selected);
        } else {
            backend.apply(IconPackChoices.KEY_GLOBAL, selected);
            backend.apply(IconPackChoices.KEY_PINNED, "");
        }
        host.onApplied();
    }

    private void select(@NonNull String pack) {
        selected = pack;
        writeChoice();
        rebuildTiles();
        drawPreview();
    }

    /** The pack the preview draws: the selection, or with Default under the pinned scope the global one. */
    @NonNull
    private String previewPack() {
        if (!selected.isEmpty() || !pinnedOnlyOn) return selected;
        return backend.current(IconPackChoices.KEY_GLOBAL);
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

    private void rebuildTiles() {
        tiles.removeAllViews();
        addTile(context.getString(pinnedOnlyOn
            ? R.string.icon_pack_page_default_global : R.string.icon_pack_page_default_system), "");
        for (IconPackChoices.Entry pack : packs) addTile(pack.label, pack.value);
    }

    private void addTile(@NonNull CharSequence label, @NonNull final String value) {
        View tile = LayoutInflater.from(context).inflate(R.layout.icon_pack_tile, tiles, false);
        final ImageView art = tile.findViewById(R.id.icon_pack_tile_art);
        TextView text = tile.findViewById(R.id.icon_pack_tile_label);
        text.setText(label);
        boolean on = value.equals(selected);
        // The ring: 3dp primary on the selected tile, a 1dp outline on the rest.
        art.setBackground(circle(color(com.google.android.material.R.attr.colorSurfaceContainerHigh),
            on ? color(androidx.appcompat.R.attr.colorPrimary)
                : color(com.google.android.material.R.attr.colorOutlineVariant), dp(on ? 3 : 1)));
        tile.setSelected(on);
        tile.setTag(value);
        tile.setContentDescription(on
            ? context.getString(R.string.icon_pack_page_tile_selected, label) : label);
        tile.setOnClickListener(v -> select(value));
        tiles.addView(tile);

        // The Default tile has no pack of its own, so it shows the platform's generic app icon at
        // once; a pack's own icon lands from the background, the bare circle holding its place.
        if (value.isEmpty()) {
            art.setImageResource(android.R.drawable.sym_def_app_icon);
            return;
        }
        background.execute(() -> {
            final Drawable d = backend.packArt(value);
            main.execute(() -> {
                if (!released) art.setImageDrawable(d);
            });
        });
    }

    /** Draws the pinned apps over the still: a placeholder circle each, then the icon as it loads. */
    private void drawPreview() {
        previewApps.removeAllViews();
        final String pack = previewPack();
        final int gen = ++previewGeneration;
        int count = Math.min(pinned.size(), PREVIEW_APPS);
        LinearLayout row = null;
        for (int i = 0; i < count; i++) {
            if (i % PREVIEW_COLUMNS == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                previewApps.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final ImageView icon = new ImageView(context);
            icon.setImageDrawable(circle(
                color(com.google.android.material.R.attr.colorSurfaceContainerHigh), 0, 0));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            // Four 30dp icons with 4dp margins make 152dp, inside the 164dp the card leaves.
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(icon, lp);
            final AppRef ref = pinned.get(i);
            background.execute(() -> {
                final Drawable d = backend.icon(pack, ref);
                main.execute(() -> {
                    if (released || gen != previewGeneration || d == null) return;
                    icon.setImageDrawable(d);
                });
            });
        }
    }
}
