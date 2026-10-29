package com.termux.app.setup;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.termux.app.launcher.LauncherLockAccessibilityAccess;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.app.settings.UseAsSetting;
import com.termux.app.statusbar.EssentialNotificationRules;
import com.termux.app.wall.UseAs;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the setup cards read from the phone: the facts that decide which cards exist, what the
 * system says is done right now, and the stored dismissals. The engines are called, not copied.
 */
public final class SetupCardSystem {

    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";

    private SetupCardSystem() {}

    @Nullable
    private static SharedPreferences store(@NonNull Context context) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        return prefs == null ? null : prefs.getSharedPreferences();
    }

    @NonNull
    static SetupCards.Facts facts(@NonNull Context context) {
        SharedPreferences store = store(context);
        return new SetupCards.Facts(
            store != null && store.getBoolean(SetupState.KEY_WANTS_DESKTOP_APPS, false),
            store != null && store.getBoolean(SetupState.KEY_WANTS_AI, false),
            UseAsSetting.read(context) == UseAs.HOME);
    }

    @NonNull
    static SetupCards.Stored stored(@NonNull Context context) {
        SharedPreferences store = store(context);
        return SetupCards.Stored.parse(store == null ? null : store.getString(SetupCards.KEY, null));
    }

    static void save(@NonNull Context context, @NonNull SetupCards.Stored stored) {
        SharedPreferences store = store(context);
        if (store != null) store.edit().putString(SetupCards.KEY, stored.toJson()).apply();
    }

    /** The ids of the system-checked cards that read as done right now. */
    @NonNull
    static Set<String> systemDone(@NonNull Context context) {
        Set<String> done = new HashSet<>();
        PrivilegedBackendManager.BackendState state =
            PrivilegedBackendManager.getInstance().getBackendState();
        if (state == PrivilegedBackendManager.BackendState.READY
            || state == PrivilegedBackendManager.BackendState.FALLBACK_SHELL)
            done.add(SetupCards.SHIZUKU);
        if (LauncherLockAccessibilityAccess.isEnabled(context))
            done.add(SetupCards.ACCESSIBILITY);
        if (NotificationManagerCompat.from(context).areNotificationsEnabled())
            done.add(SetupCards.NOTIFICATIONS);
        if (LauncherNotificationAccess.isEnabled(context))
            done.add(SetupCards.NOTIFICATION_ACCESS);
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        if (!EssentialNotificationRules.load(prefs).isEmpty())
            done.add(SetupCards.PINNED);
        if (isDefaultHome(context))
            done.add(SetupCards.HOME);
        Set<String> selected = AiModelDownloads.selected(context);
        if (!selected.isEmpty() && AiModelDownloads.installedIds(context).containsAll(selected))
            done.add(SetupCards.AI);
        return done;
    }

    /** The cards as they stand now. */
    @NonNull
    public static List<SetupCards.Card> cards(@NonNull Context context) {
        return SetupCards.build(facts(context), stored(context), systemDone(context));
    }

    /** How many cards are open, for the Settings row. */
    public static int openCount(@NonNull Context context) {
        return SetupCards.openCount(cards(context));
    }

    /** Open cards that can be completed in this build (what brings the sheet back on start). */
    public static int completableOpenCount(@NonNull Context context) {
        boolean aiSelected = !AiModelDownloads.selected(context).isEmpty();
        int count = 0;
        for (SetupCards.Card card : cards(context))
            if (card.state == SetupCards.State.OPEN
                && SetupCards.isCompletable(card.id, aiSelected))
                count++;
        return count;
    }

    static boolean isShizukuInstalled(@NonNull Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException absent) {
            return false;
        }
    }

    /** This launcher holds the home role (Q and up), or is what the HOME intent resolves to. */
    static boolean isDefaultHome(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = context.getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME))
                return roles.isRoleHeld(RoleManager.ROLE_HOME);
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = context.getPackageManager()
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null
            && context.getPackageName().equals(resolved.activityInfo.packageName);
    }
}
