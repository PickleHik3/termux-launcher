package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.layout.KindLayout;
import com.termux.app.place.layout.LayoutFields;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.Layouts;
import com.termux.app.place.layout.LayoutsJson;
import com.termux.app.place.layout.Sizes;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.WallPages.PageKind;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Migration step 17 (ADR 0020 §7, spec §4): folds the flat {@code layout.*} keys that steps 1-16
 * left into the five documents of the {@code layouts} file. Pure: it reads a snapshot of the
 * launcher preferences and the wall's stored state, and never writes or removes a flat key.
 *
 * <p>For each setting of each kind, the first of these that holds a value wins:
 * <ul>
 *   <li>Home: the {@code home.} value; the Start-in Home page's page layout (the first Home page
 *   when Start in names the Terminal or the Display); the shared NORMAL value.</li>
 *   <li>Terminal: the Terminal page's own page layout; NORMAL.</li>
 *   <li>Terminal-minimal: the {@code minimal.} value; the Terminal result.</li>
 *   <li>Display: a copy of the Terminal result, with the Display kind's own visibility.</li>
 *   <li>Sizes: NORMAL.</li>
 * </ul>
 * What none holds stays absent and reads its shipped default.
 */
final class LayoutsMigration {

    private static final String LOOK_PREFIX = "look.";
    private static final String LOOK_PAGE_SEPARATOR = "@";
    private static final String LOOK_KIND_PREFIX = "kind:";

    @NonNull private final Map<String, ?> mAll;
    /** The Start-in Home page, or the first Home page; -1 when the wall is unknown. */
    final int homePageId;
    /** The first Terminal page; -1 when there is none or the wall is unknown. */
    final int terminalPageId;

    LayoutsMigration(@NonNull Map<String, ?> all, @Nullable String wallPagesJson) {
        mAll = all;
        int home = -1;
        int terminal = -1;
        if (wallPagesJson != null) {
            try {
                JSONObject wall = new JSONObject(wallPagesJson);
                int startIn = wall.optInt("home", -1);
                JSONArray pages = wall.optJSONArray("pages");
                List<Integer> pending = new ArrayList<>();
                JSONArray p = wall.optJSONArray("pending");
                for (int i = 0; p != null && i < p.length(); i++) pending.add(p.getInt(i));
                int firstHome = -1;
                boolean startInIsHome = false;
                for (int i = 0; pages != null && i < pages.length(); i++) {
                    JSONObject page = pages.getJSONObject(i);
                    int id = page.getInt("id");
                    if (pending.contains(id)) continue;
                    String kind = page.optString("kind");
                    if (PageKind.HOME.name().equals(kind)) {
                        if (firstHome < 0) firstHome = id;
                        if (id == startIn) startInIsHome = true;
                    } else if (PageKind.TERMINAL.name().equals(kind) && terminal < 0) {
                        terminal = id;
                    }
                }
                home = startInIsHome ? startIn : firstHome;
            } catch (JSONException e) {
                home = -1;
                terminal = -1;
            }
        }
        homePageId = home;
        terminalPageId = terminal;
    }

    /** The five documents the flat keys fold into. */
    @NonNull
    Layouts layouts() {
        Map<LayoutKind, KindLayout> kinds = new EnumMap<>(LayoutKind.class);
        KindLayout home = fold(LayoutKind.HOME, homeSources(), null);
        KindLayout terminal = fold(LayoutKind.TERMINAL, terminalSources(), null);
        KindLayout minimal = fold(LayoutKind.TERMINAL_MINIMAL,
            new LayoutVariant[]{LayoutVariant.MINIMAL}, terminal);
        KindLayout display = withDress(withKeyboardOpen(terminal, PaneWallPage.DISPLAY),
            PageKind.DISPLAY);
        kinds.put(LayoutKind.HOME, home);
        kinds.put(LayoutKind.TERMINAL, withDress(
            withKeyboardOpen(terminal, PaneWallPage.TERMINAL), PageKind.TERMINAL));
        kinds.put(LayoutKind.TERMINAL_MINIMAL,
            withKeyboardOpen(minimal, PaneWallPage.TERMINAL));
        kinds.put(LayoutKind.DISPLAY, display);
        return new Layouts(kinds, sizes());
    }

    @NonNull
    private LayoutVariant[] homeSources() {
        return hasPageLayout(homePageId)
            ? new LayoutVariant[]{LayoutVariant.HOME,
                LayoutVariant.page(homePageId, PageKind.HOME), LayoutVariant.NORMAL}
            : new LayoutVariant[]{LayoutVariant.HOME, LayoutVariant.NORMAL};
    }

    @NonNull
    private LayoutVariant[] terminalSources() {
        return hasPageLayout(terminalPageId)
            ? new LayoutVariant[]{LayoutVariant.page(terminalPageId, PageKind.TERMINAL),
                LayoutVariant.NORMAL}
            : new LayoutVariant[]{LayoutVariant.NORMAL};
    }

    private boolean hasPageLayout(int pageId) {
        return pageId >= 0 && Boolean.TRUE.equals(mAll.get(PlaceLayoutStore.pageLayoutKey(pageId)));
    }

    /**
     * One kind's document: each field from the first source that holds it, then from
     * {@code fallback} (Terminal-minimal falls back to the Terminal's result).
     */
    @NonNull
    private KindLayout fold(@NonNull LayoutKind kind, @NonNull LayoutVariant[] sources,
                            @Nullable KindLayout fallback) {
        Map<String, Object> wide = new LinkedHashMap<>();
        for (String field : LayoutsJson.WIDE_FIELDS) {
            if (LayoutsJson.KEYBOARD_OPEN.equals(field)) continue;
            Object value = first(sources, null, field);
            if (value == null && fallback != null) value = fallback.wide().get(field);
            if (value != null) wide.put(field, value);
        }
        KindLayout shipped = LayoutsJson.shippedDefault(kind);
        KindLayout out = new KindLayout(KindLayout.VERSION, LayoutFields.of(wide),
            LayoutFields.EMPTY, LayoutFields.EMPTY);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            for (String field : LayoutsJson.ORIENTATION_FIELDS) {
                Object value = first(sources, orientation, field);
                if (value == null && fallback != null)
                    value = fallback.orientation(orientation).get(field);
                if (value != null) fields.put(field, value);
            }
            if (kind == LayoutKind.HOME) {
                // The widget grid has only ever been Home's.
                for (String field : LayoutsJson.GRID_FIELDS) {
                    Object value = mAll.get(
                        PlaceLayoutStore.layoutKey(LayoutVariant.HOME, orientation, field));
                    if (value != null) fields.put(field, value);
                }
                for (Element element : Element.values()) {
                    fields.put(LayoutsJson.dressField(element),
                        dress(PageKind.HOME, orientation, element, shipped));
                }
            }
            out = out.withOrientation(orientation, LayoutFields.of(fields));
        }
        return out;
    }

    @Nullable
    private Object first(@NonNull LayoutVariant[] sources, @Nullable PlaceOrientation orientation,
                         @NonNull String field) {
        for (LayoutVariant source : sources) {
            String key = orientation == null ? PlaceLayoutStore.layoutWideKey(source, field)
                : PlaceLayoutStore.layoutKey(source, orientation, field);
            Object value = mAll.get(key);
            if (value != null) return value;
        }
        return null;
    }

    /** A Terminal or Display document with that kind's own visibility (was {@code place.dress.*}). */
    @NonNull
    private KindLayout withDress(@NonNull KindLayout layout, @NonNull PageKind kind) {
        KindLayout shipped = LayoutsJson.shippedDefault(LayoutKind.of(kind, false));
        KindLayout out = layout;
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (Element element : Element.values()) {
                out = out.with(orientation, LayoutsJson.dressField(element),
                    dress(kind, orientation, element, shipped));
            }
        }
        return out;
    }

    @NonNull
    private Boolean dress(@NonNull PageKind kind, @NonNull PlaceOrientation orientation,
                          @NonNull Element element, @NonNull KindLayout shipped) {
        Object stored = mAll.get(PlaceLayoutStore.kindKey(kind, orientation, element));
        if (stored instanceof Boolean) return (Boolean) stored;
        return shipped.orientation(orientation).bool(LayoutsJson.dressField(element),
            PlaceLayoutStore.kindShowsByDefault(kind, element));
    }

    @NonNull
    private KindLayout withKeyboardOpen(@NonNull KindLayout layout, @NonNull PaneWallPage place) {
        Object open = mAll.get(PlaceLayoutStore.memoryKey(place, LayoutsJson.KEYBOARD_OPEN));
        return open instanceof Boolean
            ? layout.withWide(LayoutsJson.KEYBOARD_OPEN, open) : layout;
    }

    @NonNull
    private Sizes sizes() {
        Sizes sizes = Sizes.EMPTY;
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (String field : LayoutsJson.SIZE_FIELDS) {
                Object value = mAll.get(
                    PlaceLayoutStore.layoutKey(LayoutVariant.NORMAL, orientation, field));
                if (value != null) sizes = sizes.with(orientation, field, value);
            }
        }
        return sizes;
    }

    /**
     * The page looks that fold into kind looks: {@code look.…@<page>} of the Start-in Home page
     * into {@code @kind:home}, of the Terminal page into {@code @kind:terminal} and
     * {@code @kind:display}, each only where the kind's cell is absent. Other pages' cells are
     * left in the file and no longer read.
     */
    @NonNull
    Map<String, Object> lookCells() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> e : mAll.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(LOOK_PREFIX) || e.getValue() == null) continue;
            int at = key.lastIndexOf(LOOK_PAGE_SEPARATOR);
            if (at < 0) continue;
            int page;
            try {
                page = Integer.parseInt(key.substring(at + 1));
            } catch (NumberFormatException notAPage) {
                continue;
            }
            // Vibrancy and Margin have no kind row; their page cells stay unread.
            com.termux.shared.termux.settings.preferences.LookGrid.Key parsed =
                com.termux.shared.termux.settings.preferences.LookGrid.parse(key);
            if (parsed == null || !parsed.property.perElement) continue;
            String cell = key.substring(0, at + 1) + LOOK_KIND_PREFIX;
            if (page >= 0 && page == homePageId) {
                putAbsent(out, cell + "home", e.getValue());
            } else if (page >= 0 && page == terminalPageId) {
                putAbsent(out, cell + "terminal", e.getValue());
                putAbsent(out, cell + "display", e.getValue());
            }
        }
        return out;
    }

    private void putAbsent(@NonNull Map<String, Object> out, @NonNull String key,
                           @NonNull Object value) {
        if (!mAll.containsKey(key)) out.put(key, value);
    }
}
