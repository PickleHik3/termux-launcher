package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts the page switcher key on the extra-keys bars: the plan for the setup card's Add. Pure; the
 * stored lines come in through {@link ExtraKeySetResolver.Lines} and the planned writes go out for
 * the caller to apply.
 *
 * <p>The key goes where each bar reads its row from, and no bar changes where it reads from: a
 * bar with its own row gets the key there, a bar that follows Global gets it through Global's
 * row (written once), and a bar that shows its shipped set is left as it is.
 */
public final class PageSwitcherKeyAdd {

    /** The key as the bar stores it. */
    public static final String KEY = "tool:app.page_switcher";

    /** The bars the key goes on: every place's. */
    private static final ExtraKeysTarget[] TARGETS = {
        ExtraKeysTarget.TERMINAL, ExtraKeysTarget.DISPLAY, ExtraKeysTarget.ASSISTANT,
        ExtraKeysTarget.HOME};

    private PageSwitcherKeyAdd() {}

    /** Whether any key of the page line {@code page} is the page switcher key. */
    public static boolean holds(@Nullable String page) {
        for (List<ExtraKeysLayoutModel.Key> row : ExtraKeysLayoutModel.parse(page).rows())
            for (ExtraKeysLayoutModel.Key key : row)
                if (!key.macro && KEY.equals(key.key)) return true;
        return false;
    }

    /**
     * The page line with the key at the left end of its first row, or null when the line has no
     * keys or already holds the key (nothing to write).
     */
    @Nullable
    public static String withKey(@Nullable String page) {
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse(page);
        if (model.isEmpty() || holds(page)) return null;
        model.rows().get(0).add(0, new ExtraKeysLayoutModel.Key(KEY));
        return model.serialize();
    }

    /** Whether the key is on a bar one of the targets shows now (either page line). */
    public static boolean isOn(@NonNull ExtraKeySetResolver.Lines lines) {
        for (ExtraKeysTarget target : TARGETS) {
            ExtraKeySetResolver.Resolved resolved = ExtraKeySetResolver.resolve(target, lines);
            if (holds(resolved.page1) || holds(resolved.page2)) return true;
        }
        return false;
    }

    /**
     * What Add writes: the key at the left end of the first row of the page-1 line each bar reads,
     * where that line has keys and no page switcher yet. A target with its own row is written
     * there; a target that follows Global puts it in Global's row, once for all of them; a target
     * showing its shipped set is not written. No target is detached from what it follows.
     */
    @NonNull
    public static List<ExtraKeysTargetDrafts.Write> plan(@NonNull ExtraKeySetResolver.Lines lines) {
        List<ExtraKeysTargetDrafts.Write> out = new ArrayList<>(TARGETS.length);
        boolean globalPlanned = false;
        for (ExtraKeysTarget target : TARGETS) {
            ExtraKeySetResolver.Resolved resolved = ExtraKeySetResolver.resolve(target, lines);
            String key;
            if (resolved.source == ExtraKeySetResolver.Source.OWN) {
                key = target.page1Key();
            } else if (resolved.source == ExtraKeySetResolver.Source.GLOBAL && !globalPlanned) {
                globalPlanned = true;
                key = ExtraKeysTarget.GLOBAL.page1Key();
            } else {
                continue;
            }
            String page1 = withKey(resolved.page1);
            if (page1 != null) out.add(new ExtraKeysTargetDrafts.Write(key, page1));
        }
        return out;
    }
}
