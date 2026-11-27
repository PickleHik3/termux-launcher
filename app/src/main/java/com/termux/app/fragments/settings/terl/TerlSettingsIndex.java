package com.termux.app.fragments.settings.terl;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.fragments.settings.SettingsSearchIndex;
import com.termux.app.fragments.settings.SettingsTree;
import com.termux.R;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the search over Terl's Settings from its tree: each group, each setting row (its
 * declaration's title, summary and the words people say for it), each link, and the rows of v1's
 * index that live on a page a group opens. Home's hits carry "Home is off" under Use Terl as
 * Terminal; nothing is left out for it.
 */
final class TerlSettingsIndex {

    private TerlSettingsIndex() {}

    @NonNull
    static TerlSettingsSearch build(@NonNull Context context) {
        SettingsRegistry registry = TerlSettings.registry(context);
        boolean homeOff = TerlSettings.homeIsOff(context);
        List<TerlSettingsSearch.Entry> entries = new ArrayList<>();
        String settingsTitle = context.getString(R.string.title_activity_termux_settings);
        for (TerlSettingsTree.Group group : TerlSettingsTree.groups()) {
            if (!TerlSettings.shows(context, group)) continue;
            String groupTitle = context.getString(group.titleRes);
            String trail = note(context, groupTitle, group, homeOff);
            entries.add(new TerlSettingsSearch.Entry(TerlSettingsSearch.Kind.GROUP, groupTitle,
                context.getString(group.summaryRes), note(context, settingsTitle, group, homeOff),
                group, null, TerlSettingsTree.hostedPage(group), null));
            for (TerlSettingsTree.Item item : TerlSettingsTree.items(group)) {
                if (item.kind == TerlSettingsTree.Item.Kind.SETTING) {
                    if (!TerlSettings.has(context, item.need)) continue;
                    SettingDeclaration declaration = registry.declaration(item.id);
                    if (declaration == null) continue;
                    entries.add(new TerlSettingsSearch.Entry(TerlSettingsSearch.Kind.SETTING,
                        declaration.title, declaration.summary + " " + String.join(" ",
                        declaration.phrases), trail, group, declaration.id, null, null));
                } else if (item.kind == TerlSettingsTree.Item.Kind.LINK) {
                    entries.add(new TerlSettingsSearch.Entry(TerlSettingsSearch.Kind.LINK,
                        context.getString(item.titleRes),
                        item.summaryRes == 0 ? null : context.getString(item.summaryRes), trail,
                        group, null, item.page, null));
                }
            }
        }
        // The rows of the v1 pages the groups open, found where Terl opens them.
        for (SettingsSearchIndex.Entry row : SettingsSearchIndex.get(context).entries()) {
            // A page a group links or hosts is found as that link or group already.
            if (row.key == null && TerlSettingsTree.groupLinking(row.screenClass) != null) continue;
            // An editor opens from the row on its page (one app's page needs the app).
            SettingsTree.Screen screen = SettingsTree.find(row.screenClass);
            if (row.key == null && screen != null && screen.editor) continue;
            TerlSettingsTree.Route route = TerlSettingsTree.routeTo(row.screenClass);
            if (route == null || !TerlSettings.shows(context, route.group)) continue;
            StringBuilder trail = new StringBuilder(context.getString(route.group.titleRes));
            // A page's trail ends at the page above it; a row's at its own page.
            int end = row.key == null ? route.pages.size() - 1 : route.pages.size();
            for (SettingsTree.Screen page : route.pages.subList(0, end)) {
                if (page.className.equals(TerlSettingsTree.hostedPage(route.group))) continue;
                trail.append(SettingsSearchIndex.PATH_SEPARATOR).append(context.getString(page.titleRes));
            }
            entries.add(new TerlSettingsSearch.Entry(TerlSettingsSearch.Kind.PAGE_ROW, row.title,
                row.breadcrumb, note(context, trail.toString(), route.group, homeOff), route.group,
                null, row.screenClass, row.key));
        }
        return new TerlSettingsSearch(entries);
    }

    @NonNull
    private static String note(@NonNull Context context, @NonNull String trail,
                               @NonNull TerlSettingsTree.Group group, boolean homeOff) {
        return homeOff && group == TerlSettingsTree.Group.HOME
            ? context.getString(R.string.terl_settings_home_off_note, trail) : trail;
    }
}
