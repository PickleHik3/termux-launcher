package com.termux.app.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.LauncherUsageStatsStore;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Self-contained, modern "Edit pinned apps" bottom sheet that can be shown from anywhere with just
 * a {@link Context} (the dock long-press and the Settings "Default apps" entry both use it). It
 * reads/writes pinned apps via {@link LauncherConfigRepository}, sources apps + icons from
 * {@link LauncherAppDataProvider}, and surfaces a "Most used" quick-pin section from
 * {@link LauncherUsageStatsStore}. On save it persists to the repository and invokes {@code onSaved}
 * so a live dock (if any) can refresh.
 */
public final class PinnedAppsEditor {

    private static final int MOST_USED_COUNT = 6;

    private final Context context;
    @Nullable private final Runnable onSaved;
    private final LauncherConfigRepository repository;
    private final LauncherUsageStatsStore usageStats;

    private final float density;
    private final int colorPanel;
    private final int colorText;
    private final int colorSubtle;
    private final int colorOutline;
    private final int colorAccent;

    private List<LauncherAppEntry> allApps = new ArrayList<>();
    private final Map<String, LauncherAppEntry> appByStableId = new LinkedHashMap<>();

    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final List<PinnedItem> orderedSelected = new ArrayList<>();

    private final boolean[] folderMode = new boolean[] {false};

    private PinnedAppsEditor(@NonNull Context context, @Nullable Runnable onSaved) {
        this.context = context;
        this.onSaved = onSaved;
        this.repository = new LauncherConfigRepository(TermuxAppSharedPreferences.build(context, false));
        this.usageStats = LauncherUsageStatsStore.getInstance(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.colorText = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface, 0xFFECEFF4);
        this.colorSubtle = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant, 0xFF9AA3B2);
        this.colorPanel = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanelHigh, 0xFF202837);
        this.colorOutline = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOutlineVariant, 0xFF3A4456);
        this.colorAccent = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary, 0xFF8AB4F8);
    }

    /** Builds and shows the editor. Loads the app list (async if needed) before presenting. */
    public static void show(@NonNull Context context, @Nullable Runnable onSaved) {
        new PinnedAppsEditor(context, onSaved).open();
    }

    private void open() {
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);
        List<LauncherAppEntry> apps = provider.getAllApps();
        if (apps != null && !apps.isEmpty()) {
            bind(apps);
            buildAndShow();
        } else {
            Handler main = new Handler(Looper.getMainLooper());
            provider.warmAsync(() -> main.post(() -> {
                bind(provider.getAllApps());
                buildAndShow();
            }));
        }
    }

    private void bind(@Nullable List<LauncherAppEntry> apps) {
        allApps = apps == null ? new ArrayList<>() : new ArrayList<>(apps);
        appByStableId.clear();
        for (LauncherAppEntry entry : allApps) {
            appByStableId.put(entry.appRef.stableId(), entry);
        }
        selectedIds.clear();
        orderedSelected.clear();
        for (PinnedItem item : repository.loadPinnedItems()) {
            String stable = stableIdForPinnedItem(item);
            if (stable == null || !selectedIds.add(stable)) continue;
            orderedSelected.add(clonePinnedItem(item));
        }
    }

    // ---- UI ----------------------------------------------------------------

    private void buildAndShow() {
        final BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        root.setClipToOutline(true);
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setCornerRadii(new float[] {
            dp(28), dp(28), dp(28), dp(28), dp(12), dp(12), dp(12), dp(12)
        });
        sheetBg.setColor(withAlpha(colorPanel, 0xFA));
        sheetBg.setStroke(dp(1), withAlpha(colorOutline, 0x55));
        root.setBackground(sheetBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setElevation(dp(16));
        }

        root.addView(textView("Edit pinned apps", 22f, colorText, Typeface.DEFAULT_BOLD, false));
        TextView subtitle = textView(
            "Choose apps, drag to reorder, or create a folder from the selected pins.",
            13f, colorSubtle, Typeface.DEFAULT, false);
        subtitle.setPadding(0, dp(6), 0, dp(16));
        root.addView(subtitle);

        root.addView(sectionHeader("Pinned order"));

        final PinnedOrderAdapter orderAdapter = new PinnedOrderAdapter();
        RecyclerView orderedRecycler = new RecyclerView(context);
        orderedRecycler.setLayoutManager(new LinearLayoutManager(context));
        orderedRecycler.setAdapter(orderAdapter);
        orderedRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        orderedRecycler.setClipToPadding(false);
        orderedRecycler.setPadding(0, dp(4), 0, dp(4));
        GradientDrawable orderedBg = new GradientDrawable();
        orderedBg.setCornerRadius(dp(18));
        orderedBg.setColor(withAlpha(colorPanel, 0xAA));
        orderedBg.setStroke(dp(1), withAlpha(colorOutline, 0x44));
        orderedRecycler.setBackground(orderedBg);
        orderedRecycler.setOnTouchListener((v, e) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from < 0 || to < 0 || from >= orderedSelected.size() || to >= orderedSelected.size()) return false;
                Collections.swap(orderedSelected, from, to);
                orderAdapter.notifyItemMoved(from, to);
                persistImmediate();
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (pos < 0 || pos >= orderedSelected.size()) return;
                removeAt(pos, orderAdapter);
            }
        });
        touchHelper.attachToRecyclerView(orderedRecycler);
        orderAdapter.touchHelper = touchHelper;
        root.addView(orderedRecycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));

        // Most used section.
        final List<LauncherAppEntry> mostUsed = computeMostUsed();
        final LinearLayout mostUsedRow = new LinearLayout(context);
        TextView mostUsedHeader = sectionHeader("Most used");
        mostUsedHeader.setPadding(0, dp(18), 0, dp(8));
        if (!mostUsed.isEmpty()) {
            root.addView(mostUsedHeader);
            mostUsedRow.setOrientation(LinearLayout.HORIZONTAL);
            HorizontalScrollView scroller = new HorizontalScrollView(context);
            scroller.setHorizontalScrollBarEnabled(false);
            scroller.addView(mostUsedRow);
            root.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        root.addView(sectionHeader("Apps", dp(18)));

        final EditText searchInput = new EditText(context);
        searchInput.setHint("Search apps");
        searchInput.setSingleLine(true);
        searchInput.setTextColor(colorText);
        searchInput.setHintTextColor(colorSubtle);
        searchInput.setTextSize(15f);
        searchInput.setMinHeight(dp(48));
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(dp(16));
        searchBg.setColor(withAlpha(colorPanel, 0xC8));
        searchBg.setStroke(dp(1), withAlpha(colorOutline, 0x55));
        searchInput.setBackground(searchBg);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(0, 0, 0, dp(10));
        root.addView(searchInput, searchParams);

        final List<LauncherAppEntry> filtered = new ArrayList<>(allApps);
        final ListView listView = new ListView(context);
        final AppsAdapter appsAdapter = new AppsAdapter(filtered);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setSelector(new ColorDrawable(0x00000000));
        listView.setCacheColorHint(0x00000000);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(6), 0, dp(6));
        GradientDrawable listBg = new GradientDrawable();
        listBg.setCornerRadius(dp(18));
        listBg.setColor(withAlpha(colorPanel, 0x88));
        listBg.setStroke(dp(1), withAlpha(colorOutline, 0x33));
        listView.setBackground(listBg);
        listView.setAdapter(appsAdapter);
        listView.setOnTouchListener((v, e) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filtered.size()) return;
            toggleApp(filtered.get(position));
            appsAdapter.notifyDataSetChanged();
            orderAdapter.notifyDataSetChanged();
        });
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));

        // Build most-used chips now that toggle/refresh hooks exist.
        final Runnable[] refreshAll = new Runnable[1];
        refreshAll[0] = () -> {
            appsAdapter.notifyDataSetChanged();
            orderAdapter.notifyDataSetChanged();
            rebuildMostUsedChips(mostUsedRow, mostUsed, refreshAll[0]);
        };
        rebuildMostUsedChips(mostUsedRow, mostUsed, refreshAll[0]);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                String q = s.toString().trim().toLowerCase(Locale.US);
                filtered.clear();
                for (LauncherAppEntry entry : allApps) {
                    if (q.isEmpty() || matches(entry, q)) filtered.add(entry);
                }
                appsAdapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Action buttons.
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);

        final ImageButton folderAction = new ImageButton(context);
        folderAction.setImageResource(R.drawable.ic_create_new_folder_24);
        folderAction.setContentDescription("Create folder from selection");
        folderAction.setColorFilter(colorText);
        folderAction.setBackgroundColor(0x00000000);
        GradientDrawable folderActionBg = new GradientDrawable();
        folderActionBg.setShape(GradientDrawable.OVAL);
        folderActionBg.setColor(withAlpha(colorPanel, 0xD8));
        folderActionBg.setStroke(dp(1), withAlpha(colorOutline, 0x55));
        folderAction.setBackground(folderActionBg);
        folderAction.setPadding(dp(6), dp(6), dp(6), dp(6));

        Button cancel = ghostButton("Close");
        cancel.setOnClickListener(v -> dialog.dismiss());
        final Button save = ghostButton("Done");

        final Runnable refreshFolderUi = () -> {
            save.setText(folderMode[0] ? "Create" : "Done");
            folderAction.setAlpha(folderMode[0] ? 1f : 0.6f);
        };
        folderAction.setOnClickListener(v -> {
            folderMode[0] = !folderMode[0];
            refreshFolderUi.run();
        });
        refreshFolderUi.run();

        save.setOnClickListener(v -> {
            persist();
            dialog.dismiss();
            if (onSaved != null) onSaved.run();
        });

        LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        folderParams.setMargins(0, 0, dp(12), 0);
        buttons.addView(folderAction, folderParams);
        buttons.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1f));
        buttons.addView(cancel);
        buttons.addView(save);
        root.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
            dialog.getWindow().setDimAmount(0.35f);
        }
    }

    private void rebuildMostUsedChips(@NonNull LinearLayout row, @NonNull List<LauncherAppEntry> mostUsed, @NonNull Runnable refreshAll) {
        row.removeAllViews();
        for (LauncherAppEntry entry : mostUsed) {
            boolean pinned = selectedIds.contains(entry.appRef.stableId());
            LinearLayout chip = new LinearLayout(context);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER_HORIZONTAL);
            chip.setPadding(dp(10), dp(8), dp(10), dp(8));
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setCornerRadius(dp(16));
            chipBg.setColor(withAlpha(pinned ? colorAccent : colorPanel, pinned ? 0x44 : 0xAA));
            chipBg.setStroke(dp(1), withAlpha(pinned ? colorAccent : colorOutline, 0x66));
            chip.setBackground(chipBg);

            ImageView icon = new ImageView(context);
            if (entry.icon != null) icon.setImageDrawable(entry.icon);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
            chip.addView(icon, iconParams);

            TextView label = new TextView(context);
            label.setText(entry.label);
            label.setTextColor(colorText);
            label.setTextSize(11f);
            label.setSingleLine(true);
            label.setMaxWidth(dp(64));
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(4), 0, 0);
            chip.addView(label);

            chip.setOnClickListener(v -> {
                toggleApp(entry);
                refreshAll.run();
            });

            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.setMargins(0, 0, dp(10), 0);
            row.addView(chip, chipParams);
        }
    }

    // ---- selection / persistence ------------------------------------------

    private void toggleApp(@NonNull LauncherAppEntry entry) {
        String stable = entry.appRef.stableId();
        if (selectedIds.contains(stable)) {
            selectedIds.remove(stable);
            removePinnedByStableId(orderedSelected, stable);
        } else {
            selectedIds.add(stable);
            orderedSelected.add(new PinnedAppItem(entry.appRef.copy()));
        }
        persistImmediate();
    }

    private void removeAt(int pos, @NonNull PinnedOrderAdapter adapter) {
        String stable = stableIdForPinnedItem(orderedSelected.get(pos));
        orderedSelected.remove(pos);
        if (stable != null) selectedIds.remove(stable);
        adapter.notifyItemRemoved(pos);
        persistImmediate();
    }

    private void persistImmediate() {
        if (folderMode[0]) return;
        persist();
        if (onSaved != null) onSaved.run();
    }

    private void persist() {
        List<PinnedItem> result = new ArrayList<>();
        if (folderMode[0]) {
            // Collapse all selected app pins into one folder, preserving existing folders.
            PinnedFolderItem folder = new PinnedFolderItem(newFolderId(), "Folder");
            for (PinnedItem item : orderedSelected) {
                if (item instanceof PinnedFolderItem) {
                    result.add(clonePinnedItem(item));
                } else if (item instanceof PinnedAppItem) {
                    PinnedAppItem app = (PinnedAppItem) item;
                    folder.apps.add(new PinnedAppItem(app.appRef.copy()));
                }
            }
            if (!folder.apps.isEmpty()) result.add(folder);
        } else {
            for (PinnedItem item : orderedSelected) {
                if (item != null) result.add(clonePinnedItem(item));
            }
        }
        repository.savePinnedItems(result);
    }

    private String newFolderId() {
        // Avoids java.util.UUID (deterministic-friendly) — id only needs to be unique per save.
        return "f" + Long.toHexString(System.currentTimeMillis()) + Integer.toHexString(orderedSelected.size());
    }

    @NonNull
    private List<LauncherAppEntry> computeMostUsed() {
        List<LauncherAppEntry> candidates = new ArrayList<>();
        for (LauncherAppEntry entry : allApps) {
            if (!selectedIds.contains(entry.appRef.stableId())) candidates.add(entry);
        }
        List<LauncherAppEntry> ranked = usageStats.rankForAz(candidates);
        if (ranked.size() > MOST_USED_COUNT) {
            return new ArrayList<>(ranked.subList(0, MOST_USED_COUNT));
        }
        return ranked;
    }

    private boolean matches(@NonNull LauncherAppEntry entry, @NonNull String lowerQuery) {
        return entry.labelLower.contains(lowerQuery)
            || entry.packageLower.contains(lowerQuery)
            || entry.labelNormalized.contains(lowerQuery);
    }

    // ---- adapters ----------------------------------------------------------

    /** Full apps checklist (icon + label + checkbox). */
    private final class AppsAdapter extends BaseAdapter {
        private final List<LauncherAppEntry> items;

        AppsAdapter(@NonNull List<LauncherAppEntry> items) {
            this.items = items;
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
            } else {
                row = appRow();
            }
            LauncherAppEntry entry = items.get(position);
            ImageView icon = (ImageView) row.getChildAt(0);
            TextView label = (TextView) row.getChildAt(1);
            CheckBox check = (CheckBox) row.getChildAt(2);
            icon.setImageDrawable(entry.icon);
            label.setText(entry.label);
            check.setChecked(selectedIds.contains(entry.appRef.stableId()));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(check.isChecked() ? withAlpha(colorAccent, 0x22) : 0x00000000);
            row.setBackground(bg);
            return row;
        }

        private LinearLayout appRow() {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(52));
            row.setPadding(dp(12), dp(4), dp(12), dp(4));
            ImageView icon = new ImageView(context);
            row.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
            TextView label = new TextView(context);
            label.setTextColor(colorText);
            label.setTextSize(15f);
            label.setSingleLine(true);
            label.setPadding(dp(14), 0, dp(8), 0);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            CheckBox check = new CheckBox(context);
            check.setClickable(false);
            check.setFocusable(false);
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(colorAccent));
            row.addView(check, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return row;
        }
    }

    /** Pinned order list (icon + label + drag handle + delete), drag-to-reorder & swipe-to-delete. */
    private final class PinnedOrderAdapter extends RecyclerView.Adapter<PinnedOrderAdapter.Holder> {
        @Nullable ItemTouchHelper touchHelper;

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView dragHandle;
            final ImageView icon;
            final TextView label;
            final ImageButton delete;

            Holder(@NonNull LinearLayout row) {
                super(row);
                dragHandle = (ImageView) row.getChildAt(0);
                icon = (ImageView) row.getChildAt(1);
                label = (TextView) row.getChildAt(2);
                delete = (ImageButton) row.getChildAt(3);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(48));
            row.setPadding(dp(10), dp(2), dp(10), dp(2));
            row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            ImageView handle = new ImageView(context);
            handle.setImageResource(R.drawable.ic_drag_indicator_24);
            handle.setColorFilter(colorSubtle);
            row.addView(handle, new LinearLayout.LayoutParams(dp(24), dp(24)));

            ImageView icon = new ImageView(context);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            iconParams.setMargins(dp(8), 0, 0, 0);
            row.addView(icon, iconParams);

            TextView label = new TextView(context);
            label.setTextColor(colorText);
            label.setTextSize(15f);
            label.setSingleLine(true);
            label.setPadding(dp(12), 0, dp(8), 0);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageButton delete = new ImageButton(context);
            delete.setImageResource(R.drawable.ic_delete_sweep_24);
            delete.setColorFilter(colorSubtle);
            delete.setBackgroundColor(0x00000000);
            row.addView(delete, new LinearLayout.LayoutParams(dp(32), dp(32)));

            return new Holder(row);
        }

        @SuppressWarnings("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PinnedItem item = orderedSelected.get(position);
            holder.icon.setImageDrawable(iconForPinned(item));
            holder.label.setText(labelForPinned(item));
            holder.delete.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos >= 0 && pos < orderedSelected.size()) removeAt(pos, this);
            });
            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && touchHelper != null) {
                    touchHelper.startDrag(holder);
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return orderedSelected.size();
        }
    }

    // ---- helpers -----------------------------------------------------------

    @Nullable
    private Drawable iconForPinned(@NonNull PinnedItem item) {
        if (item instanceof PinnedAppItem) {
            LauncherAppEntry entry = appByStableId.get(((PinnedAppItem) item).appRef.stableId());
            return entry == null ? null : entry.icon;
        }
        Drawable folder = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_folder_24);
        if (folder != null) folder.setTint(colorAccent);
        return folder;
    }

    @NonNull
    private String labelForPinned(@NonNull PinnedItem item) {
        if (item instanceof PinnedAppItem) {
            LauncherAppEntry entry = appByStableId.get(((PinnedAppItem) item).appRef.stableId());
            if (entry != null) return entry.label;
            return ((PinnedAppItem) item).appRef.packageName;
        }
        if (item instanceof PinnedFolderItem) {
            String title = ((PinnedFolderItem) item).title;
            return title == null || title.isEmpty() ? "Folder" : title;
        }
        return "";
    }

    private TextView sectionHeader(@NonNull String text) {
        return sectionHeader(text, 0);
    }

    private TextView sectionHeader(@NonNull String text, int topPad) {
        TextView header = textView(text, 13f, colorText, Typeface.DEFAULT_BOLD, true);
        header.setLetterSpacing(0.08f);
        header.setPadding(0, topPad, 0, dp(8));
        return header;
    }

    private TextView textView(@NonNull String text, float sizeSp, int color, @NonNull Typeface typeface, boolean allCaps) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTypeface(typeface);
        tv.setAllCaps(allCaps);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    private Button ghostButton(@NonNull String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(colorAccent);
        button.setBackgroundColor(0x00000000);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    @Nullable
    private static String stableIdForPinnedItem(@Nullable PinnedItem item) {
        if (item instanceof PinnedAppItem) return ((PinnedAppItem) item).appRef.stableId();
        if (item instanceof PinnedFolderItem) return "folder:" + ((PinnedFolderItem) item).id;
        return null;
    }

    private static void removePinnedByStableId(@NonNull List<PinnedItem> items, @NonNull String stableId) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (stableId.equals(stableIdForPinnedItem(items.get(i)))) items.remove(i);
        }
    }

    @NonNull
    private static PinnedItem clonePinnedItem(@NonNull PinnedItem item) {
        if (item instanceof PinnedAppItem) {
            PinnedAppItem app = (PinnedAppItem) item;
            return new PinnedAppItem(app.appRef.copy(), app.iconOverride);
        }
        if (item instanceof PinnedFolderItem) {
            PinnedFolderItem folder = (PinnedFolderItem) item;
            PinnedFolderItem copy = new PinnedFolderItem(folder.id, folder.title);
            copy.rows = folder.rows;
            copy.cols = folder.cols;
            copy.tintOverrideEnabled = folder.tintOverrideEnabled;
            copy.tintColor = folder.tintColor;
            for (PinnedAppItem folderApp : folder.apps) {
                copy.apps.add(new PinnedAppItem(
                    folderApp.appRef.copy(), folderApp.iconOverride));
            }
            return copy;
        }
        return item;
    }
}
