package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import com.termux.R;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeShade;
import com.termux.app.launcher.LauncherAppLauncher;
import com.termux.app.launcher.folder.FolderRenameModel;
import com.termux.app.launcher.folder.FolderRenameTitleView;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.popup.AnchoredMenu;
import com.termux.app.launcher.popup.AnchoredMenuGeometry;
import com.termux.app.launcher.popup.AnchoredMenuTheme;
import com.termux.app.launcher.popup.MenuRow;
import com.termux.app.launcher.popup.MenuRowFactory;
import com.termux.app.launcher.popup.MenuSpec;
import com.termux.app.material.M3;
import com.termux.app.terminal.FloatTitlebarPolicy;

import java.util.Collections;
import java.util.List;

/**
 * The popup a folder on Home opens: the dock's folder popup, built from the same parts. The panel
 * comes from the launcher's menu module ({@link AnchoredMenu#buildDetached}, the shell from
 * {@link MenuRowFactory}), so it has the dock folder's glass, but it is shown in a
 * {@link HomeFolderOverlay} over the Home pane rather than in a window of its own: the in-app
 * keyboard a rename is typed with stays over it and pressable, and undimmed. It springs in and
 * out as a dock folder does. The title is the dock's {@link FolderRenameTitleView}: a tap on it
 * starts the dock's own focusless rename through the activity ({@link Callbacks#onBeginRename}).
 *
 * <p>The apps are laid out as they are on the page ({@link HomeAppIconView}: icon and label), up to
 * four across at the default density ({@link HomeFolderDensity}). A tap launches the app and
 * closes the popup; a long press, or a tap on an app that is gone, offers Remove from folder,
 * which puts the app on the page in front.</p>
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
         * Whether a touch outside the Home pane at this screen point leaves the popup open: a key
         * of the keyboard a rename is being typed with, which would otherwise close the popup and
         * end the rename on its first stroke.
         */
        default boolean keepsOpenOnTouch(float rawX, float rawY) { return false; }
    }

    /** The dock folder's default tint, the base its glass is laid over. */
    private static final int TINT_BASE = 0x202020;

    private final ViewGroup host;
    private final AnchoredMenuTheme theme;
    private final HomeAppIconFactory icons;
    private final Callbacks callbacks;
    @Nullable private HomeFolderOverlay overlay;
    private final AnchoredMenu surface;
    private final AnchoredMenu appMenu;
    private final MenuRowFactory rows;
    private int folderId;
    /** Home's layout was locked when the popup opened: nothing in it edits the folder. */
    private boolean locked;
    @Nullable private FolderRenameTitleView title;

    HomeFolderPopup(@NonNull ViewGroup host, @NonNull AnchoredMenuTheme theme,
                    @NonNull HomeAppIconFactory icons, @NonNull Callbacks callbacks) {
        this.host = host;
        this.theme = theme;
        this.icons = icons;
        this.callbacks = callbacks;
        surface = new AnchoredMenu(host, theme);
        appMenu = new AnchoredMenu(host, theme);
        rows = new MenuRowFactory(host.getContext(), theme);
    }

    boolean isShowing() { return overlay != null && overlay.isOpen(); }

    /** The folder on screen, while the popup is. */
    int folderId() { return isShowing() ? folderId : 0; }

    /** Opens {@code folder}'s popup against {@code anchor}, replacing any one open. */
    void show(@NonNull LauncherWidgetRecord folder, @NonNull View anchor) {
        show(folder, anchor, HomeFolderDensity.DEFAULT, false);
    }

    /**
     * The same, laid out at {@code density} (Settings › Home › Folders). With Home's layout
     * {@code locked} the folder is only opened and its apps launched: the title is not a rename
     * and no app offers Remove from folder.
     */
    void show(@NonNull LauncherWidgetRecord folder, @NonNull View anchor,
              @NonNull HomeFolderDensity density, boolean locked) {
        if (!folder.isFolder()) return;
        this.locked = locked;
        appMenu.dismiss();
        dismissImmediate();
        Context context = host.getContext();
        folderId = folder.appWidgetId;
        LinearLayout shell = rows.newShell();

        int ink = resolveInk();
        FolderRenameTitleView name = new FolderRenameTitleView(context);
        name.setTextColor(ink);
        name.setPadding(dp(8), 0, dp(8), 0);
        name.bind(new FolderRenameModel(folder.folderName()), false);
        final int id = folder.appWidgetId;
        final String current = folder.folderName();
        if (!locked) name.setOnClickListener(v -> callbacks.onBeginRename(id, current, name));
        title = name;
        shell.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        List<LauncherWidgetRecord.FolderApp> apps = folder.folderApps();
        // As many columns as the density takes and the pane's width fits.
        float scale = context.getResources().getDisplayMetrics().density;
        int columns = density.columnsFor(apps.size(), host.getWidth() / scale);
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                shell.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            row.addView(cellFor(id, apps.get(i), ink),
                new LinearLayout.LayoutParams(dp(density.cellWidthDp), dp(density.cellHeightDp)));
        }

        PopupWindow window = surface.buildDetached(MenuSpec.of(shell, TINT_BASE)
            .width(dp(density.popupWidthDp(columns))).build());
        View panel = panelOf(window);
        final HomeFolderOverlay[] shown = new HomeFolderOverlay[1];
        shown[0] = new HomeFolderOverlay(context, panel, window.getWidth(), window.getHeight(),
            anchor, dp(AnchoredMenuGeometry.GAP_DP), () -> {
                if (overlay != shown[0]) return;
                overlay = null;
                appMenu.dismiss();
                title = null;
                callbacks.onPopupDismissed();
            });
        overlay = shown[0];
        shown[0].showIn(host);
    }

    /**
     * The panel {@code window} was built around, taken out of it: the menu module's glass (the
     * plate, its blur and grain over the rows) is the content itself; the plain surface is the
     * window's background, worn here by a frame round the content. The window is never shown.
     */
    @NonNull
    private View panelOf(@NonNull PopupWindow window) {
        View content = window.getContentView();
        Drawable background = window.getBackground();
        float elevation = window.getElevation();
        window.setContentView(null);
        if (content.getParent() instanceof ViewGroup)
            ((ViewGroup) content.getParent()).removeView(content);
        if (background == null) {
            content.setElevation(elevation);
            return content;
        }
        FrameLayout frame = new FrameLayout(host.getContext());
        frame.setBackground(background);
        frame.setElevation(elevation);
        frame.setClipToOutline(true);
        frame.addView(content, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return frame;
    }

    /**
     * A press anywhere in the window, seen by the activity before any view: one outside the Home
     * pane closes the popup, as a tap elsewhere closed it while it was a window of its own, except
     * a key of the keyboard a rename is typed with ({@link Callbacks#keepsOpenOnTouch}). The press
     * itself goes on to whatever it landed on. Presses on the pane are the overlay's own.
     */
    void onWindowTouchDown(float rawX, float rawY) {
        HomeFolderOverlay open = overlay;
        if (open == null || !open.isOpen()) return;
        if (AnchoredMenu.isRawInsideView(host, rawX, rawY)) return;
        if (callbacks.keepsOpenOnTouch(rawX, rawY)) return;
        dismiss();
    }

    /**
     * The ink of the popup's title, its rename draft and its app labels: the titlebar's rule on the
     * chrome's polarity, as the capsule status bar takes it ({@link FloatTitlebarPolicy#glyphInk},
     * as on a focused float). On a panel the wallpaper shows through
     * ({@link #wallpaperShowsThrough}), once the chrome's polarity is published, it is the theme's
     * neutral on that polarity: the wallpaper through the panel is what the eye reads the ink
     * against, and the theme's onSurface alone came out dark on dark glass over a dark wallpaper.
     * On a panel whose own colour is most of what is seen, or before any polarity is published, it
     * is onSurface made legible on that colour ({@code surface}, opaque).
     */
    @ColorInt
    @VisibleForTesting
    static int ink(boolean throughGlass, @Nullable ChromeInk.Polarity polarity,
                   @ColorInt int onSurface, @ColorInt int surfaceBase, @ColorInt int surface) {
        return FloatTitlebarPolicy.glyphInk(throughGlass, polarity, onSurface, surfaceBase,
            surface, true);
    }

    /**
     * Whether the wallpaper is most of what is seen through a panel at {@code opacityPercent}: the
     * capsule's tint is faint, but this panel's opacity is the dock's own setting (43 by default)
     * and a dark plate at 80 over a light wallpaper is a dark ground whatever the polarity says.
     */
    @VisibleForTesting
    static boolean wallpaperShowsThrough(int opacityPercent) {
        return opacityPercent < 50;
    }

    /** {@link #ink} for this popup's panel, on the polarity published now. */
    @ColorInt
    private int resolveInk() {
        Context context = host.getContext();
        boolean plate = theme.blurEnabled() && theme.blurRadiusDp() > 0;
        boolean throughGlass = wallpaperShowsThrough(theme.opacityPercent());
        int onSurface = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        int surfaceBase = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(context, R.color.termux_surface_base));
        // The panel's own colour, opaque: the plate's tint, or the plain menu surface.
        int surface = plate ? (0xFF000000 | TINT_BASE) : (0xFF000000 | M3.surfaceContainer(context));
        return ink(throughGlass, ChromeShade.polarity(), onSurface, surfaceBase, surface);
    }

    /** The title view of the popup on screen, for a rename that ended to draw the name again. */
    @Nullable FolderRenameTitleView title() { return title; }

    void dismiss() {
        appMenu.dismiss();
        if (overlay != null) overlay.dismiss();
    }

    void dismissImmediate() {
        appMenu.dismiss();
        if (overlay != null) overlay.dismissImmediate();
    }

    @NonNull
    private View cellFor(int id, @NonNull LauncherWidgetRecord.FolderApp member,
                         @ColorInt int ink) {
        HomeAppIconView cell = new HomeAppIconView(host.getContext());
        // The page's icon size and label size; the popup's ink below replaces the label's shadow.
        cell.setStyle(icons.style());
        LauncherAppEntry entry = icons.entryFor(member);
        boolean gone = entry == null && icons.catalogueLoaded();
        String label = entry != null ? entry.label : member.label;
        cell.bind(icons.artworkFor(entry), label, gone);
        // The label is drawn on the popup's glass: the popup's ink, no shadow.
        tintLabel(cell, ink);
        final LauncherAppEntry launch = entry != null ? entry
            : new LauncherAppEntry(member.app(), label, null);
        cell.setOnClickListener(v -> {
            if (gone) {
                if (!locked) offerRemove(id, member, v);
                return;
            }
            dismiss();
            LauncherAppLauncher.launchEntry(v.getContext(), launch);
        });
        if (!locked) cell.setOnLongClickListener(v -> {
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
            dismiss();
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
