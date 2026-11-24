package com.termux.app.assistant;

import androidx.annotation.NonNull;

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

    public enum State { PROPOSED, APPLIED }

    /** One change: the setting's title and what it goes from and to, as the person reads them. */
    public static final class Item {
        @NonNull public final String id;
        @NonNull public final String title;
        @NonNull public final String oldValue;
        @NonNull public final String newValue;
        @NonNull public final String oldLabel;
        @NonNull public final String newLabel;

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
