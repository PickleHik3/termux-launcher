package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The settings cards of the answers, by message, kept with their chat: each is stored as JSON in
 * its message, so reopening a chat (or restarting) shows what happened. Apply and Undo are the only
 * places the Assistant writes a setting, and both go through the registry and the applier, so the
 * launcher refreshes as it does from Settings.
 */
public final class AssistantCards {
    private static final AssistantCards SHARED = new AssistantCards();

    private final Map<Long, SettingsCard> cards = new ConcurrentHashMap<>();
    @Nullable private volatile AssistantChatStore store;

    @NonNull
    public static AssistantCards shared() {
        return SHARED;
    }

    /** Where cards are saved and read back from. */
    void attach(@Nullable AssistantChatStore store) {
        this.store = store;
    }

    void put(long messageId, @NonNull SettingsCard card) {
        cards.put(messageId, card);
        persist(messageId, card);
    }

    /** Forgets a message's card, here and in the store (a retried answer starts again). */
    void remove(long messageId) {
        cards.remove(messageId);
        AssistantChatStore s = store;
        if (s != null) s.setCard(messageId, null);
    }

    /** The card of a message: from this process, else from the chat store; null when it has none. */
    @Nullable
    public SettingsCard get(long messageId) {
        SettingsCard card = cards.get(messageId);
        if (card != null) return card;
        AssistantChatStore s = store;
        AssistantMessage m = s == null ? null : s.message(messageId);
        SettingsCard stored = m == null ? null : SettingsCard.fromJson(m.card);
        if (stored != null) cards.put(messageId, stored);
        return stored;
    }

    /**
     * Brings the cards of a chat's stored messages into this process, so what happened to them
     * shows after a reopen or a restart. A card already held here is the newer one and stays.
     */
    void adopt(@NonNull java.util.List<AssistantMessage> messages) {
        for (AssistantMessage m : messages) {
            if (m.card == null || cards.containsKey(m.id)) continue;
            SettingsCard stored = SettingsCard.fromJson(m.card);
            if (stored != null) cards.putIfAbsent(m.id, stored);
        }
    }

    /** Whether an applied card is held, so something may have changed from outside. */
    boolean anyApplied() {
        for (SettingsCard card : cards.values()) {
            if (card.state() == SettingsCard.State.APPLIED || card.state() == SettingsCard.State.CHANGED_SINCE) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    Map<Long, SettingsCard> all() {
        return cards;
    }

    private void persist(long messageId, @NonNull SettingsCard card) {
        AssistantChatStore s = store;
        if (s != null) s.setCard(messageId, card.toJson());
    }

    /**
     * Writes every change of the card, once. Returns false when there is no proposed card for the
     * message or the registry refused every change. A change the registry refuses is skipped.
     */
    public boolean apply(long messageId, @NonNull SettingsRegistry registry, @NonNull SettingsApplier applier) {
        SettingsCard card = get(messageId);
        if (card == null || card.kind != SettingsCard.Kind.CHANGES
            || card.state() != SettingsCard.State.PROPOSED) return false;
        boolean wrote = false;
        for (SettingsCard.Item item : card.items) {
            SettingsRegistry.Outcome outcome = registry.set(item.id, item.newValue);
            SettingsRegistry.Change change = outcome.change();
            if (change == null) continue;
            wrote = true;
            item.before = change.oldValue;
            item.written = change.newValue;
            applier.apply(change);
        }
        if (wrote) {
            card.setState(SettingsCard.State.APPLIED);
            persist(messageId, card);
        }
        return wrote;
    }

    /**
     * Puts back the values each setting held just before Apply, but only if every setting it wrote still holds
     * what Apply wrote. Otherwise nothing is written and the card reads "changed since". Returns
     * whether the old values are back.
     */
    public boolean undo(long messageId, @NonNull SettingsRegistry registry, @NonNull SettingsApplier applier) {
        SettingsCard card = get(messageId);
        if (card == null || card.kind != SettingsCard.Kind.CHANGES) return false;
        refresh(messageId, card, registry);
        if (card.state() != SettingsCard.State.APPLIED) return false;
        for (SettingsCard.Item item : card.items) {
            if (item.written == null) continue;
            String back = item.before != null ? item.before : item.oldValue;
            SettingsRegistry.Change change = registry.set(item.id, back).change();
            if (change != null) applier.apply(change);
        }
        card.setState(SettingsCard.State.UNDONE);
        persist(messageId, card);
        return true;
    }

    /**
     * Compares an applied card with the settings now: APPLIED while each still holds what Apply
     * wrote, CHANGED_SINCE once one does not (and back, if it is set back). Returns whether the
     * card's state changed.
     */
    public boolean refresh(long messageId, @NonNull SettingsCard card, @NonNull SettingsRegistry registry) {
        SettingsCard.State state = card.state();
        if (card.kind != SettingsCard.Kind.CHANGES
            || (state != SettingsCard.State.APPLIED && state != SettingsCard.State.CHANGED_SINCE)) return false;
        boolean same = true;
        for (SettingsCard.Item item : card.items) {
            if (item.written != null && !item.written.equals(registry.get(item.id))) same = false;
        }
        SettingsCard.State next = same ? SettingsCard.State.APPLIED : SettingsCard.State.CHANGED_SINCE;
        if (next == state) return false;
        card.setState(next);
        persist(messageId, card);
        return true;
    }

    /** {@link #refresh} for every applied card held here. */
    public void refreshAll(@NonNull SettingsRegistry registry) {
        for (Map.Entry<Long, SettingsCard> e : cards.entrySet()) refresh(e.getKey(), e.getValue(), registry);
    }
}
