package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.launcher.LauncherAppLauncher;
import com.termux.app.launcher.folder.FolderRenameModel;
import com.termux.app.launcher.folder.FolderRenameTitleView;
import com.termux.app.launcher.folder.LauncherFolderPopupController;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.popup.AnchoredMenu;
import com.termux.app.launcher.popup.AnchoredMenuTheme;
import com.termux.app.launcher.popup.MenuRow;
import com.termux.app.launcher.popup.MenuRowFactory;
import com.termux.app.launcher.popup.MenuSpec;

import java.util.Collections;
import java.util.List;

/**
 * The popup a folder on Home opens: the dock's folder popup, built from the same parts. The
 * window comes from the launcher's menu module ({@link AnchoredMenu#buildDetached}, the shell
 * from {@link MenuRowFactory}) and lives under {@link LauncherFolderPopupController}, so it
 * springs in and out and dims what is behind it exactly as a dock folder does. The title is the
 * dock's {@link FolderRenameTitleView}: a tap on it starts the dock's own focusless rename through
 * the activity ({@link Callbacks#onBeginRename}).
 *
 * <p>The apps are laid out as they are on the page ({@link HomeAppIconView}: icon and label), up to
 * four across. A tap launches the app and closes the popup; a long press, or a tap on an app that
 * is gone, offers Remove from folder, which puts the app on the page in front.</p>
 */
final class HomeFolderPopup {
    interface Callbacks {
        /** Take {@code stableId} out of the folder {@code folderId} onto the page in front. */
        void onRemoveFromFolder(int folderId, @NonNull String stableId);
        /** Start renaming {@code folderId}, drawing the draft into {@code title}. */
        void onBeginRename(int folderId, @NonNull String name,
                           @NonNull FolderRenameTitleView title);
        /** The popup went away: a rename in progress ends with it. */
        void onPopupDismissed();
        /**
         * Whether a touch outside the popup at this screen point leaves it open: a key of the
         * keyboard a rename is being typed with, which would otherwise close the popup and end
         * the rename on its first stroke.
         */
        default boolean keepsOpenOnTouch(float rawX, float rawY) { return false; }
    }

    /** How many apps sit side by side in the popup. */
    static final int MAX_COLUMNS = 4;
    private static final int CELL_WIDTH_DP = 76;
    private static final int CELL_HEIGHT_DP = 84;
    /** The dock folder's default tint, the base its glass is laid over. */
    private static final int TINT_BASE = 0x202020;

    private final View host;
    private final AnchoredMenuTheme theme;
    private final HomeAppIconFactory icons;
    private final Callbacks callbacks;
    private final LauncherFolderPopupController controller = new LauncherFolderPopupController();
    private final AnchoredMenu surface;
    private final AnchoredMenu appMenu;
    private final MenuRowFactory rows;
    private int folderId;
    @Nullable private FolderRenameTitleView title;

    HomeFolderPopup(@NonNull View host, @NonNull AnchoredMenuTheme theme,
                    @NonNull HomeAppIconFactory icons, @NonNull Callbacks callbacks) {
        this.host = host;
        this.theme = theme;
        this.icons = icons;
        this.callbacks = callbacks;
        surface = new AnchoredMenu(host, theme);
        appMenu = new AnchoredMenu(host, theme);
        rows = new MenuRowFactory(host.getContext(), theme);
        controller.setOutsideTouchGuard(callbacks::keepsOpenOnTouch);
    }

    boolean isShowing() { return controller.isShowing(); }

    /** The folder on screen, while the popup is. */
    int folderId() { return controller.isShowing() ? folderId : 0; }

    /** Opens {@code folder}'s popup against {@code anchor}, replacing any one open. */
    void show(@NonNull LauncherWidgetRecord folder, @NonNull View anchor) {
        if (!folder.isFolder()) return;
        appMenu.dismiss();
        Context context = host.getContext();
        folderId = folder.appWidgetId;
        LinearLayout shell = rows.newShell();

        FolderRenameTitleView name = new FolderRenameTitleView(context);
        name.setTextColor(theme.textColor());
        name.setPadding(dp(8), 0, dp(8), 0);
        name.bind(new FolderRenameModel(folder.folderName()), false);
        final int id = folder.appWidgetId;
        final String current = folder.folderName();
        name.setOnClickListener(v -> callbacks.onBeginRename(id, current, name));
        title = name;
        shell.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        List<LauncherWidgetRecord.FolderApp> apps = folder.folderApps();
        int columns = Math.max(1, Math.min(MAX_COLUMNS, apps.size()));
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                shell.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            row.addView(cellFor(id, apps.get(i)),
                new LinearLayout.LayoutParams(dp(CELL_WIDTH_DP), dp(CELL_HEIGHT_DP)));
        }

        PopupWindow window = surface.buildDetached(MenuSpec.of(shell, TINT_BASE)
            .width(Math.max(dp(176), columns * dp(CELL_WIDTH_DP) + dp(6))).build());
        controller.show(window, String.valueOf(id),
            () -> surface.placeAtAnchor(window, anchor, false), () -> {
                appMenu.dismiss();
                title = null;
                callbacks.onPopupDismissed();
            });
    }

    /** The title view of the popup on screen, for a rename that ended to draw the name again. */
    @Nullable FolderRenameTitleView title() { return title; }

    void dismiss() {
        appMenu.dismiss();
        controller.dismiss();
    }

    void dismissImmediate() {
        appMenu.dismiss();
        controller.dismissImmediate();
    }

    @NonNull
    private View cellFor(int id, @NonNull LauncherWidgetRecord.FolderApp member) {
        HomeAppIconView cell = new HomeAppIconView(host.getContext());
        LauncherAppEntry entry = icons.entryFor(member);
        boolean gone = entry == null && icons.catalogueLoaded();
        String label = entry != null ? entry.label : member.label;
        cell.bind(icons.artworkFor(entry), label, gone);
        // The label is drawn on the popup's glass, not over a wallpaper: the theme's ink, no
        // shadow, so it reads in a light theme too.
        tintLabel(cell, theme.textColor());
        final LauncherAppEntry launch = entry != null ? entry
            : new LauncherAppEntry(member.app(), label, null);
        cell.setOnClickListener(v -> {
            if (gone) { offerRemove(id, member, v); return; }
            controller.dismiss();
            LauncherAppLauncher.launchEntry(v.getContext(), launch);
        });
        cell.setOnLongClickListener(v -> {
            offerRemove(id, member, v);
            return true;
        });
        return cell;
    }

    private static void tintLabel(@NonNull ViewGroup cell, int color) {
        for (int i = 0; i < cell.getChildCount(); i++) {
            View child = cell.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(color);
                ((TextView) child).setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
            }
        }
    }

    /** A one-row menu beside the app: Remove from folder. */
    private void offerRemove(int id, @NonNull LauncherWidgetRecord.FolderApp member,
                             @NonNull View anchor) {
        LinearLayout shell = rows.newShell();
        final String stableId = member.stableId();
        Runnable remove = () -> {
            appMenu.dismiss();
            controller.dismiss();
            callbacks.onRemoveFromFolder(id, stableId);
        };
        TextView row = rows.addActionRow(shell,
            host.getContext().getString(R.string.home_folder_remove_app), TINT_BASE, remove);
        appMenu.show(MenuSpec.of(shell, TINT_BASE).tightWrap(true)
            .rows(Collections.singletonList(new MenuRow(row, remove))).build(), anchor);
    }

    private int dp(int value) {
        return Math.round(value * host.getResources().getDisplayMetrics().density);
    }
}
