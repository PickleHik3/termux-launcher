package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppDrawerCategoryOrderTest {

    private static List<String> slugs(AppDrawerCategory... categories) {
        List<String> out = new ArrayList<>();
        for (AppDrawerCategory c : categories) out.add(c.slug);
        return out;
    }

    private static List<AppDrawerCategory> all() {
        return new ArrayList<>(Arrays.asList(AppDrawerCategory.values()));
    }

    @Test public void nothingStoredIsEnumOrder() {
        assertEquals(all(), new AppDrawerCategoryOrder(null).apply(all()));
        assertEquals(all(), new AppDrawerCategoryOrder(new ArrayList<>()).apply(all()));
    }

    @Test public void storedOrderIsFollowedAndAbsentCategoriesAreSkipped() {
        AppDrawerCategoryOrder order = new AppDrawerCategoryOrder(
            slugs(AppDrawerCategory.GAMES, AppDrawerCategory.SUGGESTIONS, AppDrawerCategory.OTHER));
        List<AppDrawerCategory> present = new ArrayList<>(Arrays.asList(
            AppDrawerCategory.SUGGESTIONS, AppDrawerCategory.GAMES, AppDrawerCategory.OTHER));
        assertEquals(Arrays.asList(AppDrawerCategory.GAMES, AppDrawerCategory.SUGGESTIONS,
            AppDrawerCategory.OTHER), order.apply(present));
    }

    @Test public void syntheticCategoriesAreReorderable() {
        List<String> next = new AppDrawerCategoryOrder(null)
            .moved(AppDrawerCategory.SUGGESTIONS, AppDrawerCategory.GAMES);
        List<AppDrawerCategory> result = new AppDrawerCategoryOrder(next).apply(all());
        assertEquals(result.indexOf(AppDrawerCategory.GAMES) - 1,
            result.indexOf(AppDrawerCategory.SUGGESTIONS));
        assertEquals(AppDrawerCategory.RECENTLY_ADDED, result.get(0));
    }

    @Test public void movedBeforeNullGoesToTheEnd() {
        List<String> next = new AppDrawerCategoryOrder(null).moved(AppDrawerCategory.SOCIAL, null);
        List<AppDrawerCategory> result = new AppDrawerCategoryOrder(next).apply(all());
        assertEquals(AppDrawerCategory.SOCIAL, result.get(result.size() - 1));
        assertEquals(AppDrawerCategory.values().length, next.size());
    }

    @Test public void movedBeforeItselfChangesNothing() {
        AppDrawerCategoryOrder order = new AppDrawerCategoryOrder(null);
        assertEquals(order.toSlugs(), order.moved(AppDrawerCategory.GAMES, AppDrawerCategory.GAMES));
    }

    @Test public void movedPutsItInFrontOfTheTarget() {
        List<String> next = new AppDrawerCategoryOrder(null)
            .moved(AppDrawerCategory.OTHER, AppDrawerCategory.SOCIAL);
        List<AppDrawerCategory> result = new AppDrawerCategoryOrder(next).apply(all());
        assertEquals(AppDrawerCategory.OTHER, result.get(2));
        assertEquals(AppDrawerCategory.SOCIAL, result.get(3));
    }

    @Test public void missingCategoryTakesItsEnumPlaceNotTheEnd() {
        // A store written before UTILITIES existed: reversed tail, no UTILITIES.
        List<String> stored = slugs(AppDrawerCategory.OTHER, AppDrawerCategory.PRODUCTIVITY,
            AppDrawerCategory.SOCIAL);
        List<AppDrawerCategory> result = new AppDrawerCategoryOrder(stored).apply(all());
        assertEquals(result.indexOf(AppDrawerCategory.PRODUCTIVITY) + 1,
            result.indexOf(AppDrawerCategory.UTILITIES));
    }

    @Test public void missingFirstCategoryGoesFirst() {
        List<String> stored = slugs(AppDrawerCategory.OTHER, AppDrawerCategory.GAMES);
        assertEquals(AppDrawerCategory.SUGGESTIONS,
            new AppDrawerCategoryOrder(stored).apply(all()).get(0));
    }

    @Test public void unknownIdsAreDroppedAndDuplicatesKeepTheFirst() {
        List<String> stored = new ArrayList<>(Arrays.asList(
            "nonsense", AppDrawerCategory.GAMES.slug, null, AppDrawerCategory.SOCIAL.slug,
            AppDrawerCategory.GAMES.slug));
        AppDrawerCategoryOrder order = new AppDrawerCategoryOrder(stored);
        List<AppDrawerCategory> result = order.apply(all());
        assertEquals(AppDrawerCategory.values().length, result.size());
        // GAMES stays ahead of SOCIAL (the first GAMES wins); the rest slot in by enum order.
        assertEquals(true, result.indexOf(AppDrawerCategory.GAMES)
            < result.indexOf(AppDrawerCategory.SOCIAL));
        assertEquals(1, java.util.Collections.frequency(result, AppDrawerCategory.GAMES));
    }

    @Test public void anEmptyCategoryKeepsItsSlotForWhenItReturns() {
        AppDrawerCategoryOrder order = new AppDrawerCategoryOrder(null);
        List<String> next = order.moved(AppDrawerCategory.FINANCE, AppDrawerCategory.SOCIAL);
        AppDrawerCategoryOrder reloaded = new AppDrawerCategoryOrder(next);
        List<AppDrawerCategory> without = all();
        without.remove(AppDrawerCategory.FINANCE);
        assertEquals(without.size(), reloaded.apply(without).size());
        assertEquals(AppDrawerCategory.FINANCE, reloaded.apply(all()).get(2));
    }
}
