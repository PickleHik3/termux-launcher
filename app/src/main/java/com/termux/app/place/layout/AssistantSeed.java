package com.termux.app.place.layout;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceOrientation;

/**
 * Where the Assistant kind's layout starts (ADR 0022 section 2, as ADR 0020 section 4 started
 * Display): a copy of Home's document, taken when the Assistant page is first created. Home's
 * widget grid, which no other kind holds, its element visibility and its remembered keyboard stay
 * behind: the Assistant's dress is its own (the status bar alone, the keyboard closed).
 */
public final class AssistantSeed {

    private AssistantSeed() {}

    /** Whether the Assistant's document has never been written, so it may be seeded. */
    public static boolean needsSeed(@NonNull Layouts layouts) {
        return layouts.kind(LayoutKind.ASSISTANT).equals(KindLayout.EMPTY);
    }

    /**
     * The layouts with the Assistant document seeded from Home's when it holds nothing of its own;
     * the same object when it already does.
     */
    @NonNull
    public static Layouts seeded(@NonNull Layouts layouts) {
        return layouts.withKind(LayoutKind.ASSISTANT,
            seed(layouts.kind(LayoutKind.ASSISTANT), layouts.kind(LayoutKind.HOME)));
    }

    /** The Assistant's document: {@code assistant} when it holds anything, else Home's, trimmed. */
    @NonNull
    public static KindLayout seed(@NonNull KindLayout assistant, @NonNull KindLayout home) {
        if (!assistant.equals(KindLayout.EMPTY)) return assistant;
        KindLayout seed = home.withWide(home.wide().without(LayoutsJson.KEYBOARD_OPEN));
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            LayoutFields fields = seed.orientation(orientation);
            for (String key : fields.keys()) {
                if (key.startsWith(LayoutsJson.DRESS_PREFIX)
                    || LayoutsJson.GRID_FIELDS.contains(key)) fields = fields.without(key);
            }
            seed = seed.withOrientation(orientation, fields);
        }
        return seed;
    }
}
