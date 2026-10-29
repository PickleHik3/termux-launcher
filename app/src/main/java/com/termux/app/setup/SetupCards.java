package com.termux.app.setup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The cards the setup sheet shows after the wizard: which exist, which are done, open or dismissed.
 *
 * <p>Pure. What the system says (permissions, the default home app) goes in as the set of ids that
 * read as done right now; it is never stored. Only the dismissed ids and the manually completed
 * steps are stored, as one JSON string ({@link #KEY}).
 */
public final class SetupCards {

    /** The preference holding {@link Stored#toJson}. */
    public static final String KEY = "setup_cards_v1";

    public static final String SHIZUKU = "shizuku";
    public static final String ACCESSIBILITY = "accessibility";
    public static final String NOTIFICATIONS = "notifications";
    public static final String NOTIFICATION_ACCESS = "notificationAccess";
    public static final String PINNED = "pinned";
    public static final String DISPLAY = "display";
    public static final String AI = "ai";
    public static final String HOME = "home";
    public static final String GUIDE = "guide";

    /** Every card id, in the order the sheet lists them. */
    static final String[] ORDER = {SHIZUKU, ACCESSIBILITY, NOTIFICATIONS, NOTIFICATION_ACCESS,
        PINNED, DISPLAY, AI, HOME, GUIDE};

    public enum State { OPEN, DONE, DISMISSED }

    /** What decides which cards exist. */
    public static final class Facts {
        public final boolean wantsDesktopApps;
        public final boolean wantsAi;
        public final boolean useAsHome;

        public Facts(boolean wantsDesktopApps, boolean wantsAi, boolean useAsHome) {
            this.wantsDesktopApps = wantsDesktopApps;
            this.wantsAi = wantsAi;
            this.useAsHome = useAsHome;
        }
    }

    /** One card as the sheet draws it. */
    public static final class Card {
        @NonNull public final String id;
        @StringRes public final int name;
        @StringRes public final int description;
        @NonNull public final int[] steps;
        @NonNull public final State state;
        /** How many of {@link #steps} are done. */
        public final int completed;

        Card(@NonNull String id, int name, int description, @NonNull int[] steps,
             @NonNull State state, int completed) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.steps = steps;
            this.state = state;
            this.completed = completed;
        }
    }

    /** The stored part: dismissed ids and manually completed step indices per card. */
    public static final class Stored {
        final Set<String> dismissed = new LinkedHashSet<>();
        final Map<String, Set<Integer>> steps = new LinkedHashMap<>();

        @NonNull
        public static Stored parse(@Nullable String json) {
            Stored stored = new Stored();
            if (json == null || json.isEmpty())
                return stored;
            try {
                JSONObject root = new JSONObject(json);
                JSONArray dismissed = root.optJSONArray("dismissed");
                for (int i = 0; dismissed != null && i < dismissed.length(); i++)
                    stored.dismissed.add(dismissed.getString(i));
                JSONObject steps = root.optJSONObject("steps");
                if (steps != null) {
                    Iterator<String> keys = steps.keys();
                    while (keys.hasNext()) {
                        String id = keys.next();
                        JSONArray list = steps.optJSONArray(id);
                        Set<Integer> set = new TreeSet<>();
                        for (int i = 0; list != null && i < list.length(); i++)
                            set.add(list.getInt(i));
                        stored.steps.put(id, set);
                    }
                }
            } catch (JSONException broken) {
                return new Stored();
            }
            return stored;
        }

        @NonNull
        public String toJson() {
            JSONObject root = new JSONObject();
            try {
                JSONArray dismissed = new JSONArray();
                for (String id : this.dismissed) dismissed.put(id);
                root.put("dismissed", dismissed);
                JSONObject steps = new JSONObject();
                for (Map.Entry<String, Set<Integer>> entry : this.steps.entrySet()) {
                    JSONArray list = new JSONArray();
                    for (int step : entry.getValue()) list.put(step);
                    steps.put(entry.getKey(), list);
                }
                root.put("steps", steps);
            } catch (JSONException impossible) {
                return "{}";
            }
            return root.toString();
        }

        public boolean isDismissed(@NonNull String id) {
            return dismissed.contains(id);
        }

        public void setDismissed(@NonNull String id, boolean value) {
            if (value) dismissed.add(id);
            else dismissed.remove(id);
        }

        public void completeStep(@NonNull String id, int step) {
            Set<Integer> set = steps.get(id);
            if (set == null) {
                set = new TreeSet<>();
                steps.put(id, set);
            }
            set.add(step);
        }

        int manualCount(@NonNull String id, int stepCount) {
            Set<Integer> set = steps.get(id);
            if (set == null) return 0;
            int count = 0;
            for (int step : set)
                if (step >= 0 && step < stepCount) count++;
            return count;
        }
    }

    private SetupCards() {}

    /** Whether the card is checked against the system on every open, rather than by taps. */
    static boolean isSystemChecked(@NonNull String id) {
        return !(DISPLAY.equals(id) || AI.equals(id) || GUIDE.equals(id));
    }

    /** Whether {@code id} exists for these facts. */
    static boolean exists(@NonNull String id, @NonNull Facts facts) {
        if (DISPLAY.equals(id)) return facts.wantsDesktopApps;
        if (AI.equals(id)) return facts.wantsAi;
        if (HOME.equals(id)) return facts.useAsHome;
        return true;
    }

    /**
     * The cards that exist, in order. A card the system reports done is done even when it was
     * dismissed earlier.
     *
     * @param systemDone ids the system reports as done right now
     */
    @NonNull
    public static List<Card> build(@NonNull Facts facts, @NonNull Stored stored,
                                   @NonNull Set<String> systemDone) {
        List<Card> cards = new ArrayList<>();
        for (String id : ORDER) {
            if (!exists(id, facts)) continue;
            int[] steps = stepsOf(id);
            int completed = isSystemChecked(id) ? (systemDone.contains(id) ? steps.length : 0)
                : stored.manualCount(id, steps.length);
            State state = completed >= steps.length ? State.DONE
                : stored.isDismissed(id) ? State.DISMISSED : State.OPEN;
            cards.add(new Card(id, nameOf(id), descriptionOf(id), steps, state, completed));
        }
        return Collections.unmodifiableList(cards);
    }

    /** How many cards are open (neither done nor dismissed). */
    public static int openCount(@NonNull List<Card> cards) {
        int count = 0;
        for (Card card : cards)
            if (card.state == State.OPEN) count++;
        return count;
    }

    /** The sheet is due on a launcher start: the wizard is done and a card is still open. */
    public static boolean isDue(boolean wizardDone, @NonNull List<Card> cards) {
        if (!wizardDone)
            return false;
        // Only cards that can be completed in this build keep the sheet coming back; the stubs
        // (desktop apps, AI, guide) wait for their flows.
        for (Card card : cards)
            if (card.state == State.OPEN && isSystemChecked(card.id))
                return true;
        return false;
    }

    @StringRes
    static int nameOf(@NonNull String id) {
        switch (id) {
            case SHIZUKU: return R.string.setup_card_shizuku_name;
            case ACCESSIBILITY: return R.string.setup_card_accessibility_name;
            case NOTIFICATIONS: return R.string.setup_card_notifications_name;
            case NOTIFICATION_ACCESS: return R.string.setup_card_access_name;
            case PINNED: return R.string.setup_card_pinned_name;
            case DISPLAY: return R.string.setup_card_display_name;
            case AI: return R.string.setup_card_ai_name;
            case HOME: return R.string.setup_card_home_name;
            default: return R.string.setup_card_guide_name;
        }
    }

    @StringRes
    static int descriptionOf(@NonNull String id) {
        switch (id) {
            case SHIZUKU: return R.string.setup_card_shizuku_description;
            case ACCESSIBILITY: return R.string.setup_card_accessibility_description;
            case NOTIFICATIONS: return R.string.setup_card_notifications_description;
            case NOTIFICATION_ACCESS: return R.string.setup_card_access_description;
            case PINNED: return R.string.setup_card_pinned_description;
            case DISPLAY: return R.string.setup_card_display_description;
            case AI: return R.string.setup_card_ai_description;
            case HOME: return R.string.setup_card_home_description;
            default: return R.string.setup_card_guide_description;
        }
    }

    @NonNull
    static int[] stepsOf(@NonNull String id) {
        switch (id) {
            case SHIZUKU: return new int[] {R.string.setup_card_shizuku_step};
            case ACCESSIBILITY: return new int[] {R.string.setup_card_accessibility_step};
            case NOTIFICATIONS: return new int[] {R.string.setup_card_notifications_step};
            case NOTIFICATION_ACCESS: return new int[] {R.string.setup_card_access_step};
            case PINNED: return new int[] {R.string.setup_card_pinned_step};
            case DISPLAY: return new int[] {R.string.setup_card_display_step_where,
                R.string.setup_card_display_step_install, R.string.setup_card_display_step_gpu};
            case AI: return new int[] {R.string.setup_card_ai_step_choose,
                R.string.setup_card_ai_step_download};
            case HOME: return new int[] {R.string.setup_card_home_step};
            default: return new int[] {R.string.setup_card_guide_step};
        }
    }
}
