package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The settings cards of the answers in this process, by message. A card is not saved with its chat
 * yet, so after a restart its answer reads as the plain line stored with it. Apply is the only
 * place the Assistant writes a setting, and it goes through the registry and the applier, so the
 * launcher refreshes as it does from Settings.
 */
public final class AssistantCards {
    private static final AssistantCards SHARED = new AssistantCards();

    private final Map<Long, SettingsCard> cards = new ConcurrentHashMap<>();

    @NonNull
    public static AssistantCards shared() {
        return SHARED;
    }

    void put(long messageId, @NonNull SettingsCard card) {
        cards.put(messageId, card);
    }

    @Nullable
    public SettingsCard get(long messageId) {
        return cards.get(messageId);
    }

    @NonNull
    Map<Long, SettingsCard> all() {
        return cards;
    }

    /**
     * Writes every change of the card, once. Returns false when there is no proposed card for the
     * message or the registry refused every change. A change the registry refuses is skipped.
     */
    public boolean apply(long messageId, @NonNull SettingsRegistry registry, @NonNull SettingsApplier applier) {
        SettingsCard card = cards.get(messageId);
        if (card == null || card.kind != SettingsCard.Kind.CHANGES
            || card.state() != SettingsCard.State.PROPOSED) return false;
        boolean wrote = false;
        for (SettingsCard.Item item : card.items) {
            SettingsRegistry.Outcome outcome = registry.set(item.id, item.newValue);
            SettingsRegistry.Change change = outcome.change();
            if (change == null) continue;
            wrote = true;
            applier.apply(change);
        }
        if (wrote) card.setState(SettingsCard.State.APPLIED);
        return wrote;
    }
}
