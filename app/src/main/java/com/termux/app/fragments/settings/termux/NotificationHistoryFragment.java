package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.app.notice.AppNotice;
import com.termux.launcherctl.LauncherCtlNotificationStore;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notification history for the shell, in the benchmark screens' dress: a header card that says
 * what is recorded and how an agent reads it, the retention and code-masking choices, then every
 * launchable app with a check well, none checked until the person checks it. Checked apps sort
 * first, the search box filters live, and the section header counts the recorded apps.
 *
 * <p>The choices live in {@link TermuxAppSharedPreferences}; this page only reads and writes them
 * through the notification-history accessors. Clearing empties the launcherctl store.
 */
@Keep
public class NotificationHistoryFragment extends Fragment implements TaiBenchListAdapter.Factory {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BANNER = 1;
    private static final int TYPE_OPTIONS = 2;
    private static final int TYPE_SECTION = 3;
    private static final int TYPE_SEARCH = 4;
    private static final int TYPE_APP = 5;
    private static final int TYPE_EMPTY = 6;
    private static final String STATE_QUERY = "notif_history_query";
    private static final int[] RETENTION_DAYS = {7, 30, 90, 365};
    private static final int ICON_CACHE_BYTES = 3 * 1024 * 1024;
    private static final int ICON_DP = 40;

    /** One launchable app, as listed. */
    private static final class AppInfo {
        @NonNull final String packageName;
        @NonNull final String label;

        AppInfo(@NonNull String packageName, @NonNull String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "notif-history");
        thread.setDaemon(true);
        return thread;
    });
    private final TaiBenchListAdapter adapter = new TaiBenchListAdapter(this);
    /** Small, byte-bounded icon store: the list can be hundreds of apps long. */
    private final LruCache<String, Bitmap> icons = new LruCache<String, Bitmap>(ICON_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    @Nullable private List<AppInfo> apps;
    @NonNull private String query = "";
    @Nullable private TermuxAppSharedPreferences preferences;
    private boolean binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) query = savedInstanceState.getString(STATE_QUERY, "");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        RecyclerView list = new RecyclerView(context);
        list.setId(R.id.tai_bench_list);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, TaiBenchViews.dp(context, 32));
        list.setHasFixedSize(false);
        if (TaiMotion.reduced(context)) {
            list.setItemAnimator(null);
        } else {
            DefaultItemAnimator animator = new DefaultItemAnimator();
            animator.setSupportsChangeAnimations(false);
            list.setItemAnimator(animator);
        }
        list.setAdapter(adapter);
        return list;
    }

    @Override
    public void onStart() {
        super.onStart();
        preferences = TermuxAppSharedPreferences.build(requireContext());
        rebuild();
        if (apps == null) load();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.notif_history_title);
        // Back from the system's access screen: the banner may no longer be needed.
        if (apps != null) rebuild();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, query);
    }

    @Override
    public void onDestroyView() {
        icons.evictAll();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    /** Reads the launchable apps off the main thread. */
    private void load() {
        Context context = getContext();
        if (context == null || executor.isShutdown()) return;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            PackageManager pm = app.getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            Map<String, AppInfo> byPackage = new HashMap<>();
            try {
                for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
                    if (info.activityInfo == null) continue;
                    String pkg = info.activityInfo.packageName;
                    if (byPackage.containsKey(pkg)) continue;
                    CharSequence label = info.loadLabel(pm);
                    byPackage.put(pkg, new AppInfo(pkg, label == null ? pkg : label.toString()));
                }
            } catch (RuntimeException ignored) {
            }
            List<AppInfo> result = new ArrayList<>(byPackage.values());
            Collator collator = Collator.getInstance(Locale.getDefault());
            Collections.sort(result, (a, b) -> {
                int c = collator.compare(a.label, b.label);
                return c != 0 ? c : a.packageName.compareTo(b.packageName);
            });
            handler.post(() -> {
                if (!isAdded()) return;
                apps = result;
                rebuild();
            });
        });
    }

    // ---- state ----

    @NonNull
    private Set<String> recorded() {
        Set<String> set = preferences == null ? null : preferences.getNotificationHistoryPackages();
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    private int retentionDays() {
        return preferences == null ? RETENTION_DAYS[0] : preferences.getNotificationHistoryRetentionDays();
    }

    private boolean maskCodes() {
        return preferences != null && preferences.isNotificationHistoryMaskCodesEnabled();
    }

    private static int retentionIndex(int days) {
        int best = 0;
        for (int i = 0; i < RETENTION_DAYS.length; i++) {
            if (Math.abs(RETENTION_DAYS[i] - days) < Math.abs(RETENTION_DAYS[best] - days)) best = i;
        }
        return best;
    }

    // ---- the list ----

    private void rebuild() {
        Context context = getContext();
        if (context == null) return;
        Set<String> checked = recorded();
        List<TaiBenchListAdapter.Item> items = new ArrayList<>();
        items.add(new TaiBenchListAdapter.Item(TYPE_HEADER, "header", "header", null));
        if (!LauncherNotificationAccess.isEnabled(context)) {
            items.add(new TaiBenchListAdapter.Item(TYPE_BANNER, "banner", "banner", null));
        }
        items.add(new TaiBenchListAdapter.Item(TYPE_OPTIONS, "options",
            "options|" + retentionDays() + '|' + maskCodes() + '|' + checked.size(), null));
        String count = getResources().getQuantityString(R.plurals.notif_history_apps_recorded, checked.size(), checked.size());
        items.add(new TaiBenchListAdapter.Item(TYPE_SECTION, "section", "section|" + count, count));
        items.add(new TaiBenchListAdapter.Item(TYPE_SEARCH, "search", "search", null));
        List<AppInfo> all = apps;
        if (all == null) {
            items.add(new TaiBenchListAdapter.Item(TYPE_EMPTY, "loading", "loading", getString(R.string.notif_history_loading)));
        } else {
            String needle = query.trim().toLowerCase(Locale.ROOT);
            List<AppInfo> shown = new ArrayList<>();
            for (AppInfo info : all) {
                if (!needle.isEmpty() && !info.label.toLowerCase(Locale.ROOT).contains(needle)
                    && !info.packageName.toLowerCase(Locale.ROOT).contains(needle)) continue;
                shown.add(info);
            }
            // Checked first, then the alphabetical order the list already has (the sort is stable).
            List<AppInfo> ordered = new ArrayList<>(shown.size());
            for (AppInfo info : shown) if (checked.contains(info.packageName)) ordered.add(info);
            for (AppInfo info : shown) if (!checked.contains(info.packageName)) ordered.add(info);
            if (ordered.isEmpty()) {
                items.add(new TaiBenchListAdapter.Item(TYPE_EMPTY, "none", "none|" + query,
                    getString(R.string.notif_history_no_match)));
            }
            for (AppInfo info : ordered) {
                boolean on = checked.contains(info.packageName);
                items.add(new TaiBenchListAdapter.Item(TYPE_APP, info.packageName, info.packageName + '|' + on, info));
            }
        }
        adapter.submit(items);
    }

    // ---- rows ----

    @NonNull
    @Override
    public View create(@NonNull ViewGroup parent, int type) {
        Context context = parent.getContext();
        switch (type) {
            case TYPE_HEADER: return createHeader(context);
            case TYPE_BANNER: return createBanner(context);
            case TYPE_OPTIONS: return createOptions(context);
            case TYPE_SECTION: return TaiBenchViews.sectionHeader(context, " ", " ");
            case TYPE_SEARCH: return createSearch(context);
            case TYPE_APP: return createApp(context);
            default: return createEmpty(context);
        }
    }

    @Override
    public void bind(@NonNull View view, @NonNull TaiBenchListAdapter.Item item) {
        switch (item.type) {
            case TYPE_OPTIONS: bindOptions(view); break;
            case TYPE_SECTION: {
                ViewGroup row = (ViewGroup) view;
                ((TextView) row.getChildAt(0)).setText(R.string.notif_history_apps_section);
                ((TextView) row.getChildAt(1)).setText((String) item.data);
                break;
            }
            case TYPE_SEARCH: bindSearch(view); break;
            case TYPE_APP: bindApp(view, (AppInfo) item.data); break;
            case TYPE_EMPTY: ((TextView) view).setText((String) item.data); break;
            default: break;
        }
    }

    @NonNull
    private View createHeader(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        card.core.addView(TaiBenchViews.title(context, getString(R.string.notif_history_title)));
        TextView body = TaiBenchViews.body(context, getString(R.string.notif_history_body));
        body.setLineSpacing(0f, 1.15f);
        card.core.addView(body, TaiBenchViews.block(context, 4));
        TextView command = TaiBenchViews.mono(context, getString(R.string.notif_history_command));
        command.setTextIsSelectable(true);
        command.setHorizontallyScrolling(true);
        command.setPadding(TaiBenchViews.dp(context, 10), TaiBenchViews.dp(context, 8), TaiBenchViews.dp(context, 10), TaiBenchViews.dp(context, 8));
        command.setBackgroundResource(R.drawable.tai_centre_icon_well);
        card.core.addView(command, TaiBenchViews.block(context, 10));
        return card.outer;
    }

    @NonNull
    private View createBanner(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        card.core.addView(TaiBenchViews.title(context, getString(R.string.notif_history_access_title)));
        card.core.addView(TaiBenchViews.body(context, getString(R.string.notif_history_access_body)), TaiBenchViews.block(context, 2));
        TextView grant = TaiBenchViews.goButton(context, getString(R.string.notif_history_access_grant));
        grant.setOnClickListener(v -> {
            TaiMotion.tick(v);
            NotificationsPreferencesFragment.openAccessSettings(v.getContext());
        });
        card.core.addView(grant, TaiBenchViews.block(context, 10));
        return card.outer;
    }

    @NonNull
    private View createOptions(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        card.core.addView(TaiBenchViews.title(context, getString(R.string.notif_history_keep_title)));
        TaiSegmentedTabs tabs = new TaiSegmentedTabs(context);
        tabs.setId(R.id.tai_bench_tabs);
        tabs.setContentDescription(getString(R.string.notif_history_keep_title));
        tabs.setLabels(getString(R.string.notif_history_keep_7), getString(R.string.notif_history_keep_30),
            getString(R.string.notif_history_keep_90), getString(R.string.notif_history_keep_365));
        tabs.setOnSegmentSelectedListener(index -> {
            if (binding || preferences == null || index < 0 || index >= RETENTION_DAYS.length) return;
            preferences.setNotificationHistoryRetentionDays(RETENTION_DAYS[index]);
            rebuild();
        });
        card.core.addView(tabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TaiBenchViews.dp(context, 42)));
        ((LinearLayout.LayoutParams) tabs.getLayoutParams()).topMargin = TaiBenchViews.dp(context, 8);

        LinearLayout maskRow = new LinearLayout(context);
        maskRow.setOrientation(LinearLayout.HORIZONTAL);
        maskRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(TaiBenchViews.title(context, getString(R.string.notif_history_mask_title)));
        labels.addView(TaiBenchViews.body(context, getString(R.string.notif_history_mask_summary)));
        maskRow.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch mask = new MaterialSwitch(context);
        mask.setId(R.id.tai_bench_check);
        mask.setContentDescription(getString(R.string.notif_history_mask_title));
        mask.setOnCheckedChangeListener((button, checked) -> {
            if (binding || preferences == null) return;
            TaiMotion.tick(button);
            preferences.setNotificationHistoryMaskCodesEnabled(checked);
            rebuild();
        });
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchParams.setMarginStart(TaiBenchViews.dp(context, 12));
        maskRow.addView(mask, switchParams);
        card.core.addView(maskRow, TaiBenchViews.block(context, 14));

        TextView clear = TaiBenchViews.errorButton(context, getString(R.string.notif_history_clear));
        clear.setId(R.id.tai_bench_action);
        clear.setOnClickListener(v -> {
            TaiMotion.tick(v);
            confirmClear(v.getContext());
        });
        card.core.addView(clear, TaiBenchViews.block(context, 14));
        return card.outer;
    }

    private void bindOptions(@NonNull View view) {
        binding = true;
        try {
            TaiSegmentedTabs tabs = view.findViewById(R.id.tai_bench_tabs);
            tabs.select(retentionIndex(retentionDays()), tabs.selectedIndex() >= 0);
            ((MaterialSwitch) view.findViewById(R.id.tai_bench_check)).setChecked(maskCodes());
        } finally {
            binding = false;
        }
    }

    private void confirmClear(@NonNull Context context) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.notif_history_clear_confirm_title)
            .setMessage(R.string.notif_history_clear_confirm_message)
            .setPositiveButton(R.string.notif_history_clear, (dialog, which) -> {
                LauncherCtlNotificationStore.getInstance().clearAll();
                AppNotice.show(context, R.string.notif_history_cleared, false);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @NonNull
    private View createSearch(@NonNull Context context) {
        FrameLayout outer = new FrameLayout(context);
        outer.setPadding(TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 2), TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 6));
        EditText field = new EditText(context);
        field.setId(R.id.tai_bench_text);
        field.setBackgroundResource(R.drawable.tai_centre_link_bar);
        field.setHint(R.string.notif_history_search_hint);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        field.setTextColor(TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurface));
        field.setHintTextColor(TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        field.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_symbol_search, 0, 0, 0);
        field.setCompoundDrawableTintList(ColorStateList.valueOf(TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant)));
        field.setCompoundDrawablePadding(TaiBenchViews.dp(context, 10));
        field.setMinHeight(TaiBenchViews.dp(context, 44));
        field.setPadding(TaiBenchViews.dp(context, 16), 0, TaiBenchViews.dp(context, 16), 0);
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (binding) return;
                String next = s.toString();
                if (next.equals(query)) return;
                query = next;
                rebuild();
            }
        });
        outer.addView(field, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return outer;
    }

    private void bindSearch(@NonNull View view) {
        EditText field = view.findViewById(R.id.tai_bench_text);
        if (field.getText().toString().equals(query)) return;
        binding = true;
        try {
            field.setText(query);
        } finally {
            binding = false;
        }
    }

    @NonNull
    private View createApp(@NonNull Context context) {
        TaiBenchViews.Card card = TaiBenchViews.card(context);
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(context);
        icon.setId(R.id.tai_bench_rank);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconSize = TaiBenchViews.dp(context, 36);
        line.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
        LinearLayout middle = new LinearLayout(context);
        middle.setOrientation(LinearLayout.VERTICAL);
        TextView title = TaiBenchViews.title(context, "");
        title.setId(R.id.tai_bench_title);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        middle.addView(title);
        TextView sub = TaiBenchViews.mono(context, "");
        sub.setId(R.id.tai_bench_subtitle);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        middle.addView(sub);
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        middleParams.setMarginStart(TaiBenchViews.dp(context, 12));
        line.addView(middle, middleParams);
        FrameLayout well = new FrameLayout(context);
        well.setBackgroundResource(R.drawable.tai_centre_round);
        ImageView check = new ImageView(context);
        check.setId(R.id.tai_bench_check);
        check.setImageResource(R.drawable.ic_tai_check);
        int glyph = TaiBenchViews.dp(context, 16);
        well.addView(check, new FrameLayout.LayoutParams(glyph, glyph, Gravity.CENTER));
        int size = TaiBenchViews.dp(context, 28);
        LinearLayout.LayoutParams wellParams = new LinearLayout.LayoutParams(size, size);
        wellParams.setMarginStart(TaiBenchViews.dp(context, 10));
        line.addView(well, wellParams);
        card.core.addView(line);
        card.core.setClickable(true);
        card.core.setFocusable(true);
        return card.outer;
    }

    private void bindApp(@NonNull View view, @NonNull AppInfo info) {
        Context context = view.getContext();
        boolean on = preferences != null && preferences.isNotificationHistoryPackageEnabled(info.packageName);
        ImageView check = view.findViewById(R.id.tai_bench_check);
        View well = (View) check.getParent();
        well.setBackgroundTintList(ColorStateList.valueOf(on
            ? TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorPrimary)
            : TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorSurfacePanelHighest)));
        check.setImageTintList(ColorStateList.valueOf(on
            ? TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnPrimary)
            : TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant)));
        check.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        ((TextView) view.findViewById(R.id.tai_bench_title)).setText(info.label);
        ((TextView) view.findViewById(R.id.tai_bench_subtitle)).setText(info.packageName);
        bindIcon((ImageView) view.findViewById(R.id.tai_bench_rank), info.packageName);
        View core = ((ViewGroup) ((ViewGroup) view).getChildAt(0)).getChildAt(0);
        core.setContentDescription(getString(on ? R.string.notif_history_row_on_desc : R.string.notif_history_row_off_desc, info.label));
        core.setOnClickListener(v -> {
            TaiMotion.tick(v);
            toggle(info.packageName);
        });
    }

    private void toggle(@NonNull String packageName) {
        if (preferences == null) return;
        Set<String> next = recorded();
        if (!next.remove(packageName)) next.add(packageName);
        preferences.setNotificationHistoryPackages(next);
        rebuild();
    }

    private void bindIcon(@NonNull ImageView target, @NonNull String packageName) {
        target.setTag(packageName);
        Bitmap cached = icons.get(packageName);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        target.setImageDrawable(null);
        Context app = target.getContext().getApplicationContext();
        if (executor.isShutdown()) return;
        int px = TaiBenchViews.dp(app, ICON_DP);
        executor.execute(() -> {
            Bitmap bitmap = render(app, packageName, px);
            if (bitmap == null) return;
            handler.post(() -> {
                if (!isAdded()) return;
                icons.put(packageName, bitmap);
                if (packageName.equals(target.getTag())) target.setImageBitmap(bitmap);
            });
        });
    }

    /** The app's icon rasterised at the size it is drawn, so the cache holds only what shows. */
    @Nullable
    private static Bitmap render(@NonNull Context app, @NonNull String packageName, int px) {
        try {
            Drawable drawable = app.getPackageManager().getApplicationIcon(packageName);
            Bitmap bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, px, px);
            drawable.draw(canvas);
            return bitmap;
        } catch (PackageManager.NameNotFoundException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    @NonNull
    private View createEmpty(@NonNull Context context) {
        TextView text = TaiBenchViews.body(context, "");
        text.setGravity(Gravity.CENTER);
        text.setPadding(TaiBenchViews.dp(context, 32), TaiBenchViews.dp(context, 16), TaiBenchViews.dp(context, 32), TaiBenchViews.dp(context, 12));
        return text;
    }
}
