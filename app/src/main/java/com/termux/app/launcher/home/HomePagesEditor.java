package com.termux.app.launcher.home;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;

/**
 * Edit pages: Home's pages as a list — move each up or down, make one the default page the Home
 * press lands on, remove one (asking first when it holds anything), add one at the end. Reached
 * from Home's long-press sheet and from Settings › Home. Every change is made at once through the
 * host and the list is drawn again from what the host then says; Done only closes.
 */
public final class HomePagesEditor {

    /** The launcher's side: Home's pages as they stand, and the changes. */
    public interface Host {
        int pageCount();
        /** How many widgets, icons and folders page {@code page} holds; they go with it. */
        int itemsOn(int page);
        /** How many apps the folders on page {@code page} hold; they go with their folders. */
        default int folderAppsOn(int page) { return 0; }
        /** How many floats stand on page {@code page}; they move to another page, shells kept. */
        int floatsOn(int page);
        int defaultPage();
        void movePage(int from, int to);
        void removePage(int page);
        void addPage();
        void setDefaultPage(int page);
        /** The editor closed, by Done or Back. */
        default void onClosed() { }
    }

    private final Context context;
    private final Host host;
    private final LinearLayout list;
    @Nullable private AlertDialog dialog;

    private HomePagesEditor(@NonNull Context context, @NonNull Host host) {
        this.context = context;
        this.host = host;
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(4), dp(8), dp(4));
    }

    /** Opens the editor over the launcher. */
    @NonNull
    public static HomePagesEditor show(@NonNull Context context, @NonNull Host host) {
        HomePagesEditor editor = new HomePagesEditor(context, host);
        ScrollView scroller = new ScrollView(context);
        scroller.addView(editor.list);
        editor.dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.home_pages_editor_title)
            .setView(scroller)
            .setNeutralButton(R.string.home_pages_editor_add, null)
            .setPositiveButton(R.string.home_pages_editor_done, null)
            .setOnDismissListener(d -> host.onClosed())
            .create();
        editor.dialog.setOnShowListener(d -> {
            // Add page keeps the editor open; the dialog's own buttons would close it.
            View add = editor.dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (add != null) add.setOnClickListener(v -> {
                host.addPage();
                editor.refresh();
            });
            View done = editor.dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (done != null) done.setOnClickListener(v -> editor.dialog.dismiss());
        });
        editor.refresh();
        editor.dialog.show();
        return editor;
    }

    /** Draws the list again from the host. */
    void refresh() {
        list.removeAllViews();
        int count = host.pageCount();
        int defaultPage = HomePageEdits.clampDefault(host.defaultPage(), count);
        for (int page = 0; page < count; page++) list.addView(row(page, count, defaultPage));
    }

    @NonNull
    private View row(int page, int count, int defaultPage) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(context.getString(R.string.home_pages_editor_page, page + 1));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        title.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(title);
        TextView detail = new TextView(context);
        int items = host.itemsOn(page) + host.floatsOn(page);
        String what = items == 0 ? context.getString(R.string.home_pages_editor_empty)
            : context.getResources().getQuantityString(R.plurals.home_pages_editor_items, items, items);
        detail.setText(page == defaultPage
            ? context.getString(R.string.home_pages_editor_default_detail, what) : what);
        detail.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(detail);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(button(R.drawable.ic_symbol_arrow_back, 90f,
            context.getString(R.string.home_pages_editor_move_up, page + 1), page > 0, () -> {
                host.movePage(page, page - 1);
                refresh();
            }));
        row.addView(button(R.drawable.ic_symbol_arrow_back, -90f,
            context.getString(R.string.home_pages_editor_move_down, page + 1), page < count - 1, () -> {
                host.movePage(page, page + 1);
                refresh();
            }));
        ImageButton home = button(R.drawable.ic_symbol_home, 0f,
            context.getString(R.string.home_pages_editor_make_default, page + 1), page != defaultPage, () -> {
                host.setDefaultPage(page);
                refresh();
            });
        // The default page's house stays lit in the accent: it says which page that is.
        if (page == defaultPage) {
            home.setAlpha(1f);
            home.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(context,
                androidx.appcompat.R.attr.colorPrimary, Color.WHITE)));
        }
        row.addView(home);
        row.addView(button(R.drawable.ic_symbol_delete, 0f,
            context.getString(R.string.home_pages_editor_remove, page + 1),
            HomePageEdits.canRemove(count), () -> confirmRemove(page)));
        return row;
    }

    private void confirmRemove(int page) {
        int items = host.itemsOn(page);
        int floats = host.floatsOn(page);
        if (!HomePageEdits.asksBeforeRemoving(items + floats)) {
            host.removePage(page);
            refresh();
            return;
        }
        StringBuilder message = new StringBuilder();
        if (items > 0) message.append(context.getResources().getQuantityString(
            R.plurals.home_pages_editor_remove_confirm_items, items, items));
        int folderApps = host.folderAppsOn(page);
        if (folderApps > 0) {
            message.append(' ').append(context.getResources().getQuantityString(
                R.plurals.home_pages_editor_remove_confirm_folder_apps, folderApps, folderApps));
        }
        if (floats > 0) {
            if (message.length() > 0) message.append(' ');
            message.append(context.getString(R.string.home_pages_editor_remove_confirm_floats));
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.home_pages_editor_remove_confirm_title, page + 1))
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.home_pages_editor_remove_confirm, (d, which) -> {
                host.removePage(page);
                refresh();
            })
            .show();
    }

    @NonNull
    private ImageButton button(@DrawableRes int icon, float rotation, @NonNull String description,
                               boolean enabled, @NonNull Runnable action) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(icon);
        button.setRotation(rotation);
        button.setContentDescription(description);
        button.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE)));
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,
            ripple, true) && ripple.resourceId != 0) {
            button.setBackgroundResource(ripple.resourceId);
        } else {
            button.setBackground(null);
        }
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.3f);
        button.setOnClickListener(v -> action.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(48)));
        return button;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
