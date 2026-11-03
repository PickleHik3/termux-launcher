package com.termux.app.launcher.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.material.M3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Home sheet's Add an app: a bottom sheet of the drawer's apps with a search field, one tap
 * picks. No list of this shape existed to reuse (the pinned-apps editor's list is a checklist
 * bound to the dock's pins), so its rows copy that editor's: the same icon, label, surface and
 * search, without the tick.
 */
public final class HomeAppPicker {
    public interface Callback {
        void onAppPicked(@NonNull LauncherAppEntry entry);
    }

    private final Context context;
    private final Callback callback;
    private final float density;

    private HomeAppPicker(@NonNull Context context, @NonNull Callback callback) {
        this.context = context;
        this.callback = callback;
        this.density = context.getResources().getDisplayMetrics().density;
    }

    /** Shows the picker once the drawer's app list is loaded. */
    public static void show(@NonNull Context context, @NonNull Callback callback) {
        new HomeAppPicker(context, callback).open();
    }

    private void open() {
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);
        List<LauncherAppEntry> apps = provider.getAllApps();
        if (!apps.isEmpty()) {
            buildAndShow(apps);
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        provider.warmAsync(() -> main.post(() -> buildAndShow(provider.getAllApps())));
    }

    private void buildAndShow(@NonNull List<LauncherAppEntry> apps) {
        final BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        root.addView(new BottomSheetDragHandleView(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText(R.string.home_actions_add_app);
        M3.textAppearance(title, com.google.android.material.R.attr.textAppearanceHeadlineSmall);
        title.setTextColor(M3.onSurface(context));
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        final TextInputLayout searchLayout = new TextInputLayout(context);
        final TextInputEditText search = new TextInputEditText(searchLayout.getContext());
        search.setHint("Search apps");
        search.setSingleLine(true);
        searchLayout.addView(search, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(0, 0, 0, dp(10));
        root.addView(searchLayout, searchParams);

        final List<LauncherAppEntry> filtered = new ArrayList<>(apps);
        final AppsAdapter adapter = new AppsAdapter(filtered);
        ListView list = new ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(0, dp(6), 0, dp(6));
        list.setBackground(M3.surface(context,
            com.google.android.material.R.attr.shapeAppearanceCornerLarge,
            M3.surfaceContainer(context)));
        list.setAdapter(adapter);
        // The list scrolls itself; the sheet must not read its scroll as a pull to dismiss.
        list.setOnTouchListener((v, e) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filtered.size()) return;
            LauncherAppEntry picked = filtered.get(position);
            dialog.dismiss();
            callback.onAppPicked(picked);
        });
        root.addView(list, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                String query = s.toString().trim().toLowerCase(Locale.US);
                filtered.clear();
                for (LauncherAppEntry entry : apps) {
                    if (query.isEmpty() || matches(entry, query)) filtered.add(entry);
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        dialog.setContentView(root);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }

    private static boolean matches(@NonNull LauncherAppEntry entry, @NonNull String query) {
        return entry.labelLower.contains(query) || entry.packageLower.contains(query)
            || entry.labelNormalized.contains(query);
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    /** Icon and label, as the pinned-apps editor's rows draw them. */
    private final class AppsAdapter extends BaseAdapter {
        private final List<LauncherAppEntry> items;

        AppsAdapter(@NonNull List<LauncherAppEntry> items) { this.items = items; }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            LinearLayout row = convertView instanceof LinearLayout
                ? (LinearLayout) convertView : newRow();
            LauncherAppEntry entry = items.get(position);
            ((ImageView) row.getChildAt(0)).setImageDrawable(
                LauncherAppDataProvider.artworkFor(context, entry));
            ((TextView) row.getChildAt(1)).setText(entry.label);
            return row;
        }

        private LinearLayout newRow() {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(52));
            row.setPadding(dp(12), dp(4), dp(12), dp(4));
            row.addView(new ImageView(context), new LinearLayout.LayoutParams(dp(30), dp(30)));
            TextView label = new TextView(context);
            M3.textAppearance(label, com.google.android.material.R.attr.textAppearanceBodyLarge);
            label.setTextColor(M3.onSurface(context));
            label.setSingleLine(true);
            label.setPaddingRelative(dp(14), 0, dp(8), 0);
            row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return row;
        }
    }
}
