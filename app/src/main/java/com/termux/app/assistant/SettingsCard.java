package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the Assistant shows for a settings request: the changes it offers (one Apply for all), the
 * Settings rows it links to for settings it may not change itself, or "No matching setting" with a
 * link into Settings search. Built by the app from checked values; nothing here is the model's text.
 */
public final class SettingsCard {

    public enum Kind { CHANGES, LINKS, NO_MATCH }

    /**
     * Where the card is: offered, applied (Undo works), undone, or applied and then changed from
     * outside, which is when Undo no longer can.
     */
    public enum State { PROPOSED, APPLIED, UNDONE, CHANGED_SINCE }

    /** One change: the setting's title and what it goes from and to, as the person reads them. */
    public static final class Item {
        @NonNull public final String id;
        @NonNull public final String title;
        @NonNull public final String oldValue;
        @NonNull public final String newValue;
        @NonNull public final String oldLabel;
        @NonNull public final String newLabel;
        /** What Apply wrote, as the registry holds it; null while this change was not written. */
        @Nullable public volatile String written;
        /** What the setting held just before Apply wrote it; what Undo puts back. Null until applied. */
        @Nullable public volatile String before;

        Item(@NonNull String id, @NonNull String title, @NonNull String oldValue, @NonNull String newValue,
             @NonNull String oldLabel, @NonNull String newLabel) {
            this.id = id;
            this.title = title;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.oldLabel = oldLabel;
            this.newLabel = newLabel;
        }

        /** "Text size: 100 → 120". */
        @NonNull
        public String line() {
            return title + ": " + oldLabel + " → " + newLabel;
        }
    }

    /** A link to Settings search; {@code title} is the setting it stands for ("" for the user's own words). */
    public static final class Link {
        @NonNull public final String title;
        @NonNull public final String query;

        Link(@NonNull String title, @NonNull String query) {
            this.title = title;
            this.query = query;
        }
    }

    @NonNull public final Kind kind;
    @NonNull public final List<Item> items;
    @NonNull public final List<Link> links;
    private volatile State state = State.PROPOSED;

    private SettingsCard(@NonNull Kind kind, @NonNull List<Item> items, @NonNull List<Link> links) {
        this.kind = kind;
        this.items = Collections.unmodifiableList(items);
        this.links = Collections.unmodifiableList(links);
    }

    @NonNull
    static SettingsCard changes(@NonNull List<Item> items, @NonNull List<Link> links) {
        return new SettingsCard(Kind.CHANGES, new ArrayList<>(items), new ArrayList<>(links));
    }

    @NonNull
    static SettingsCard links(@NonNull List<Link> links) {
        return new SettingsCard(Kind.LINKS, new ArrayList<>(), new ArrayList<>(links));
    }

    @NonNull
    static SettingsCard noMatch(@NonNull String message) {
        List<Link> one = new ArrayList<>();
        one.add(new Link("", message));
        return new SettingsCard(Kind.NO_MATCH, new ArrayList<>(), one);
    }

    /** A card read back from its stored form. */
    @NonNull
    static SettingsCard restore(@NonNull Kind kind, @NonNull State state, @NonNull List<Item> items,
                                @NonNull List<Link> links) {
        SettingsCard card = new SettingsCard(kind, new ArrayList<>(items), new ArrayList<>(links));
        card.state = state;
        return card;
    }

    /** The card as stored with its message. */
    @NonNull
    public String toJson() {
        try {
            JSONArray itemArray = new JSONArray();
            for (Item item : items) {
                JSONObject o = new JSONObject().put("id", item.id).put("title", item.title)
                    .put("old", item.oldValue).put("new", item.newValue)
                    .put("oldLabel", item.oldLabel).put("newLabel", item.newLabel);
                String w = item.written;
                if (w != null) o.put("written", w);
                String b = item.before;
                if (b != null) o.put("before", b);
                itemArray.put(o);
            }
            JSONArray linkArray = new JSONArray();
            for (Link link : links) {
                linkArray.put(new JSONObject().put("title", link.title).put("query", link.query));
            }
            return new JSONObject().put("v", 1).put("kind", kind.name()).put("state", state.name())
                .put("items", itemArray).put("links", linkArray).toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /** Reads a stored card; null for anything that is not one, so an old or damaged chat still loads. */
    @Nullable
    public static SettingsCard fromJson(@Nullable String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(json);
            Kind kind = Kind.valueOf(o.getString("kind"));
            State state = State.valueOf(o.getString("state"));
            List<Item> items = new ArrayList<>();
            JSONArray itemArray = o.optJSONArray("items");
            for (int i = 0; itemArray != null && i < itemArray.length(); i++) {
                JSONObject e = itemArray.getJSONObject(i);
                Item item = new Item(e.getString("id"), e.getString("title"), e.getString("old"),
                    e.getString("new"), e.getString("oldLabel"), e.getString("newLabel"));
                item.written = e.has("written") ? e.getString("written") : null;
                item.before = e.has("before") ? e.getString("before") : null;
                items.add(item);
            }
            List<Link> links = new ArrayList<>();
            JSONArray linkArray = o.optJSONArray("links");
            for (int i = 0; linkArray != null && i < linkArray.length(); i++) {
                JSONObject e = linkArray.getJSONObject(i);
                links.add(new Link(e.getString("title"), e.getString("query")));
            }
            return restore(kind, state, items, links);
        } catch (JSONException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The one line the model sees of this card in a later request: what was applied. Null for a card
     * that changed nothing (offered, undone, links), whose content is not sent back.
     */
    @Nullable
    public String note() {
        if (kind != Kind.CHANGES || (state != State.APPLIED && state != State.CHANGED_SINCE)) return null;
        StringBuilder out = new StringBuilder("Applied: ");
        boolean any = false;
        for (Item item : items) {
            if (item.written == null) continue;
            if (any) out.append("; ");
            out.append(item.line());
            any = true;
        }
        return any ? out.toString() : null;
    }

    @NonNull
    public State state() {
        return state;
    }

    void setState(@NonNull State next) {
        state = next;
    }

    /** What makes the row draw differently: kind, state and size. */
    @NonNull
    public String signature() {
        return kind + "/" + state + "/" + items.size() + "/" + links.size();
    }

    /** The words stored for the answer: its changes, one per line. */
    @NonNull
    public String summary() {
        if (kind == Kind.NO_MATCH) return "No matching setting";
        StringBuilder out = new StringBuilder();
        for (Item item : items) {
            if (out.length() > 0) out.append('\n');
            out.append(item.line());
        }
        for (Link link : links) {
            if (out.length() > 0) out.append('\n');
            out.append(link.title);
        }
        return out.toString();
    }
}
