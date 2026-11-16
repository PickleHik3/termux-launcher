package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts the page switcher key on the extra-keys bars of Terminal, Display and Assistant: the plan
 * for the setup card's Add. Pure; the stored lines come in through
 * {@link ExtraKeySetResolver.Lines} and the planned writes go out for the caller to apply.
 *
 * <p>A target that followed Global (Terminal by default) now keeps its own row, so later Global
 * edits no longer reach it. This is a deliberate choice pending the developer.
 */
public final class PageSwitcherKeyAdd {

    /** The key as the bar stores it. */
    public static final String KEY = "tool:app.page_switcher";

    /** The targets that have the bar and so get the key. */
    private static final ExtraKeysTarget[] TARGETS = {
        ExtraKeysTarget.TERMINAL, ExtraKeysTarget.DISPLAY, ExtraKeysTarget.ASSISTANT};

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

    /** Whether the key is on a bar one of the three targets shows now (either page line). */
    public static boolean isOn(@NonNull ExtraKeySetResolver.Lines lines) {
        for (ExtraKeysTarget target : TARGETS) {
            ExtraKeySetResolver.Resolved resolved = ExtraKeySetResolver.resolve(target, lines);
            if (holds(resolved.page1) || holds(resolved.page2)) return true;
        }
        return false;
    }

    /**
     * What Add writes: for each target whose bar shows keys without the page switcher, its own
     * page-1 line with the key added. Global and Home are never written. A target that followed
     * another set keeps its second page line too, so its bar shows the same pages as before.
     */
    @NonNull
    public static List<ExtraKeysTargetDrafts.Write> plan(@NonNull ExtraKeySetResolver.Lines lines) {
        List<ExtraKeysTargetDrafts.Write> out = new ArrayList<>(2 * TARGETS.length);
        for (ExtraKeysTarget target : TARGETS) {
            ExtraKeySetResolver.Resolved resolved = ExtraKeySetResolver.resolve(target, lines);
            String page1 = withKey(resolved.page1);
            if (page1 == null) continue;
            out.add(new ExtraKeysTargetDrafts.Write(target.page1Key(), page1));
            if (resolved.source != ExtraKeySetResolver.Source.OWN && resolved.page2 != null)
                out.add(new ExtraKeysTargetDrafts.Write(target.page2Key(), resolved.page2));
        }
        return out;
    }
}
