package com.termux.app.launcher.widget;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.notice.TerminalDress;

import java.util.ArrayList;
import java.util.List;

/**
 * What a lifted icon wears above everything else in the window while it travels: its picture, and
 * the Remove and Uninstall targets ({@link HomeDropTargets}), both in the window's own overlay so
 * they draw over the status chrome, the dock and the page (each of which clips its own children).
 * Home's pane uses it for an icon lifted off a page, {@link IconCarry} for one lifted out of the
 * dock or dragged out of the App menu. The targets are measured and placed once when they come
 * up; a move only places the picture and hit-tests boxes. One picture and one pill drawable, let
 * go together.
 */
public final class LiftOverlay {

    /** Android's uids per user: the user a uid belongs to is the uid divided by this. */
    private static final int PER_USER_RANGE = 100000;

    @NonNull private final View anchor;
    /** The window's root view, whose overlay holds the picture and the pills; null while neither is up. */
    @Nullable private View holder;
    private final int[] windowLocation = new int[2];
    @Nullable private Drawable picture;
    private int pictureWidth, pictureHeight;
    /** Where the picture's corner stands from the finger, on screen. */
    private float pictureDx, pictureDy;
    @Nullable private HomeDropTargetsDrawable targets;
    private boolean targetsUp;

    /** {@code anchor} is any view in the window the lift happens in. */
    public LiftOverlay(@NonNull View anchor) {
        this.anchor = anchor;
    }

    /** The window's root, measured, or null when the anchor is not in a window. */
    @Nullable
    private View root() {
        if (holder != null) return holder;
        View root = anchor.getRootView();
        if (root == null || !anchor.isAttachedToWindow() || root.getWidth() <= 0) return null;
        root.getLocationOnScreen(windowLocation);
        holder = root;
        return root;
    }

    /**
     * Puts {@code drawable}, {@code width} by {@code height} px, under the finger at ({@code rawX},
     * {@code rawY}), its corner at ({@code dx}, {@code dy}) from the finger. False when there is no
     * window to draw it in. The drawable is drawn, never kept past {@link #release}.
     */
    public boolean carry(@NonNull Drawable drawable, int width, int height, float dx, float dy,
                         float rawX, float rawY) {
        View root = root();
        if (root == null || width <= 0 || height <= 0) return false;
        Drawable previous = picture;
        if (previous != null) root.getOverlay().remove(previous);
        picture = drawable;
        pictureWidth = width;
        pictureHeight = height;
        pictureDx = dx;
        pictureDy = dy;
        moveTo(rawX, rawY);
        root.getOverlay().add(drawable);
        return true;
    }

    /** Whether a picture is being carried. */
    public boolean isCarrying() {
        return picture != null;
    }

    /** Puts the picture under the finger. */
    public void moveTo(float rawX, float rawY) {
        Drawable drawn = picture;
        if (drawn == null) return;
        int left = Math.round(rawX + pictureDx) - windowLocation[0];
        int top = Math.round(rawY + pictureDy) - windowLocation[1];
        drawn.setBounds(left, top, left + pictureWidth, top + pictureHeight);
        drawn.invalidateSelf();
    }

    /**
     * Puts {@code kinds} up in {@code hostBand} (the status chrome along the top, on screen), or
     * just under the system bar when there is none, dressed as the notice pill {@code dress}
     * wears. Returns where they are, or null when there is no window or nothing to show. A picture
     * already carried stays on top of them.
     */
    @Nullable
    public HomeDropTargets.Layout showTargets(@NonNull List<HomeDropTargets.Target> kinds,
                                              @Nullable Rect hostBand,
                                              @Nullable TerminalDress.Source dress,
                                              boolean animate) {
        if (kinds.isEmpty()) return null;
        View root = root();
        if (root == null) return null;
        Context context = anchor.getContext();
        float density = anchor.getResources().getDisplayMetrics().density;
        List<String> labels = new ArrayList<>(kinds.size());
        for (HomeDropTargets.Target kind : kinds) {
            labels.add(context.getString(kind == HomeDropTargets.Target.UNINSTALL
                ? R.string.app_menu_uninstall : R.string.home_drop_remove));
        }
        HomeDropTargets.Box band;
        if (hostBand != null && !hostBand.isEmpty()) {
            band = new HomeDropTargets.Box(hostBand.left, hostBand.top, hostBand.right,
                hostBand.bottom);
        } else {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
            int insetTop = insets == null ? 0
                : insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            band = HomeDropTargets.fallbackBand(windowLocation[0], windowLocation[1],
                root.getWidth(), insetTop, density);
        }
        HomeDropTargetsDrawable drawable = targets;
        if (drawable == null) {
            drawable = new HomeDropTargetsDrawable(context);
            targets = drawable;
        }
        drawable.setLook(TerminalDress.resolve(context, dress),
            com.termux.app.material.M3.error(context),
            com.termux.app.material.M3.color(context,
                com.google.android.material.R.attr.colorOnError,
                com.google.android.material.R.color.m3_sys_color_dark_on_error));
        int[] widths = drawable.setTargets(context, kinds, labels);
        HomeDropTargets.Layout layout = HomeDropTargets.layout(band, widths,
            Math.round(HomeDropTargets.PILL_HEIGHT_DP * density),
            Math.round(HomeDropTargets.GAP_DP * density));
        drawable.place(layout.pills, windowLocation[0], windowLocation[1]);
        drawable.setBounds(0, 0, root.getWidth(), root.getHeight());
        root.getOverlay().add(drawable);
        targetsUp = true;
        Drawable carried = picture;
        if (carried != null) {
            // The overlay draws in the order it was given: the picture goes back on top.
            root.getOverlay().remove(carried);
            root.getOverlay().add(carried);
        }
        drawable.fadeIn(animate);
        return layout;
    }

    /** Lights the target at {@code index}, or none for -1. */
    public void highlight(int index) {
        HomeDropTargetsDrawable drawable = targets;
        if (drawable != null && targetsUp) drawable.setHighlighted(index);
    }

    /** Takes the targets down, if they are up; the picture stays. */
    public void hideTargets() {
        HomeDropTargetsDrawable drawable = targets;
        View root = holder;
        if (!targetsUp || drawable == null || root == null) return;
        targetsUp = false;
        drawable.cancelFade();
        drawable.setHighlighted(-1);
        root.getOverlay().remove(drawable);
    }

    /** Lets go of the picture and takes the targets down. */
    public void release() {
        hideTargets();
        Drawable drawn = picture;
        picture = null;
        View root = holder;
        if (drawn != null && root != null) root.getOverlay().remove(drawn);
        holder = null;
    }

    /**
     * Whether the app {@code ref} names can be uninstalled from here: an Android app of this
     * profile (not a Linux app, one of the launcher's own entries, or a work or cloned profile's
     * app) that is installed, is not part of the system image, and is not this launcher.
     */
    public static boolean canUninstall(@NonNull Context context, @Nullable AppRef ref) {
        if (ref == null) return false;
        boolean ownProfile = !ref.clonedProfile
            && (ref.userId < 0 || ref.userId == android.os.Process.myUid() / PER_USER_RANGE);
        boolean androidPackage = ownProfile && !com.termux.app.x11.X11Apps.isLinuxApp(ref)
            && !com.termux.app.launcher.LauncherEntries.isEntry(ref);
        boolean installed = false, system = false;
        if (androidPackage) {
            try {
                ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(ref.packageName, 0);
                installed = true;
                system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                installed = false;
            }
        }
        return HomeDropTargets.canUninstall(androidPackage, installed, system,
            ref.packageName.equals(context.getPackageName()));
    }
}
