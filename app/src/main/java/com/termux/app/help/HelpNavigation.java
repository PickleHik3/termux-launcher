package com.termux.app.help;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the reader is in help, and where Back takes them. One invocation is one stack of frames:
 * home, a search, the glossary, a topic, or the screen explorer.
 *
 * <p>Pure, and separate from measured target state on purpose: opening, reading, searching and
 * closing help change nothing about the launcher, so nothing here touches a view. A frame keeps
 * its query, its scroll position and an open inline definition, so coming back lands where the
 * reader left.
 */
public final class HelpNavigation {

    /** The five things help can be showing. */
    public enum Screen { HOME, SEARCH, GLOSSARY, TOPIC, EXPLORE }

    /** One entry of the back stack. */
    public static final class Frame {
        public final Screen screen;
        /** The topic id on {@link Screen#TOPIC}, the pre-selected topic on EXPLORE, else null. */
        public final String id;
        /** What was typed in the search field on this frame. */
        public String query = "";
        /** Where the body was scrolled to. */
        public int scroll;
        /** The glossary term expanded inline in this frame, or null. */
        public String openTermId;

        Frame(Screen screen, String id) {
            this.screen = screen;
            this.id = id;
        }

        Frame copy() {
            Frame copy = new Frame(screen, id);
            copy.query = query;
            copy.scroll = scroll;
            copy.openTermId = openTermId;
            return copy;
        }

        @Override public String toString() { return id == null ? screen.name() : screen + ":" + id; }
    }

    private final List<Frame> stack = new ArrayList<>();
    private PaneWallPage place = PaneWallPage.TERMINAL;
    private String query = "";
    private boolean textEntryActive;
    private Frame practiceFrame;

    // ---- opening ------------------------------------------------------------------------------

    /** A fresh invocation for a place: help home, and no search carried over from last time. */
    public void open(PaneWallPage place) {
        reset(place);
        stack.add(new Frame(Screen.HOME, null));
    }

    /**
     * A Learn-more link: the topic itself, with nothing under it. Back leaves help and returns the
     * reader to what they were doing; the Home control is what leads to help home.
     */
    public void openTopic(PaneWallPage place, String topicId) {
        reset(place);
        stack.add(new Frame(Screen.TOPIC, topicId));
    }

    // ---- moving about -------------------------------------------------------------------------

    /** Help home. Unwinds to the home frame when there is one rather than stacking another. */
    public void home() {
        for (int i = 0; i < stack.size(); i++) {
            if (stack.get(i).screen != Screen.HOME) continue;
            while (stack.size() > i + 1) stack.remove(stack.size() - 1);
            return;
        }
        stack.clear();
        stack.add(new Frame(Screen.HOME, null));
    }

    /** The search destination, with whatever was last typed in this invocation. */
    public void search() {
        if (top().screen == Screen.SEARCH) return;
        Frame frame = new Frame(Screen.SEARCH, null);
        frame.query = query;
        stack.add(frame);
    }

    /** What is in the search field now; remembered for the rest of the invocation. */
    public void setQuery(String text) {
        query = text == null ? "" : text;
        top().query = query;
    }

    /** The A–Z list of terms. */
    public void glossary() {
        if (top().screen == Screen.GLOSSARY) return;
        stack.add(new Frame(Screen.GLOSSARY, null));
    }

    /** One topic, read from anywhere. Re-opening the topic already showing does nothing. */
    public void topic(String topicId) {
        if (topicId == null) return;
        Frame current = top();
        if (current.screen == Screen.TOPIC && topicId.equals(current.id)) return;
        stack.add(new Frame(Screen.TOPIC, topicId));
    }

    /** Explore this screen, optionally with one topic already selected. */
    public void explore(String selectTopicId) {
        stack.add(new Frame(Screen.EXPLORE, selectTopicId));
    }

    /** Expand a term's definition where it stands. */
    public void openTerm(String termId) { top().openTermId = termId; }

    /** Collapse it again. */
    public void closeTerm() { top().openTermId = null; }

    /** Where the body is scrolled to, so coming back lands in the same place. */
    public void setScroll(int y) { top().scroll = y; }

    /** Whether the search field is taking input; Back dismisses that before it navigates. */
    public void setTextEntryActive(boolean active) { textEntryActive = active; }

    /**
     * Back, in order: an open definition, then text entry, then one step of navigation.
     *
     * @return true while help stays open, false when Back has nothing left to close but help.
     */
    public boolean back() {
        Frame current = top();
        if (current.openTermId != null) {
            current.openTermId = null;
            return true;
        }
        if (textEntryActive) {
            textEntryActive = false;
            return true;
        }
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            return true;
        }
        return false;
    }

    // ---- practice -----------------------------------------------------------------------------

    /** Remember where the reader was, so End practice can bring them back to it. */
    public void practiceStart() { practiceFrame = top().copy(); }

    /**
     * Put the reader back where practice found them.
     *
     * @return the restored frame, or null when nothing was remembered.
     */
    public Frame practiceEnd() {
        if (practiceFrame == null) return null;
        Frame frame = practiceFrame;
        practiceFrame = null;
        if (stack.isEmpty() || top().screen != frame.screen
            || (frame.id != null && !frame.id.equals(top().id))) {
            stack.add(frame);
        }
        return top();
    }

    /** Whether a lesson is running with a frame held for it. */
    public boolean isPracticing() { return practiceFrame != null; }

    // ---- queries ------------------------------------------------------------------------------

    /** The place help was invoked from; it never changes within an invocation. */
    public PaneWallPage place() { return place; }

    /** The frame on top, which is what the panel draws. */
    public Frame frame() { return top(); }

    public Screen screen() { return top().screen; }

    /** The topic or term id of the current frame, or null. */
    public String id() { return top().id; }

    /** What was last typed in this invocation, whatever screen is showing. */
    public String query() { return query; }

    public boolean textEntryActive() { return textEntryActive; }

    /** How deep the back stack is; 1 means Back closes help. */
    public int depth() { return stack.size(); }

    /** The stack, oldest first, for a test or a state dump. */
    public List<Frame> frames() { return Collections.unmodifiableList(new ArrayList<>(stack)); }

    // ---- saving and restoring -----------------------------------------------------------------

    private static final String KEY_PLACE = "place";
    private static final String KEY_QUERY = "query";
    private static final String KEY_FRAMES = "frames";
    private static final String KEY_SCREEN = "screen";
    private static final String KEY_ID = "id";
    private static final String KEY_SCROLL = "scroll";
    private static final String KEY_TERM = "term";

    /**
     * The whole stack as a bundle, so the page the reader was on survives leaving the screen and
     * coming back to it. Text entry and a held practice frame are not saved: both belong to the
     * visit, not to the page.
     */
    public Bundle saveState() {
        Bundle out = new Bundle();
        out.putString(KEY_PLACE, place.name());
        out.putString(KEY_QUERY, query);
        ArrayList<Bundle> frames = new ArrayList<>();
        for (Frame frame : stack) {
            // The explorer is the launcher's view, not a page; a saved stack never holds one.
            if (frame.screen == Screen.EXPLORE) continue;
            Bundle saved = new Bundle();
            saved.putString(KEY_SCREEN, frame.screen.name());
            saved.putString(KEY_ID, frame.id);
            saved.putString(KEY_QUERY, frame.query);
            saved.putInt(KEY_SCROLL, frame.scroll);
            saved.putString(KEY_TERM, frame.openTermId);
            frames.add(saved);
        }
        out.putParcelableArrayList(KEY_FRAMES, frames);
        return out;
    }

    /**
     * Put a saved stack back. An empty or unreadable bundle leaves help on its home page rather
     * than on nothing.
     *
     * @return true when a stack was restored from the bundle.
     */
    public boolean restoreState(@Nullable Bundle state) {
        if (state == null) return false;
        PaneWallPage saved = place(state.getString(KEY_PLACE));
        reset(saved);
        query = state.getString(KEY_QUERY, "");
        ArrayList<Bundle> frames = state.getParcelableArrayList(KEY_FRAMES);
        if (frames != null) {
            for (Bundle frame : frames) {
                if (frame == null) continue;
                Screen screen = screen(frame.getString(KEY_SCREEN));
                if (screen == null || screen == Screen.EXPLORE) continue;
                Frame restored = new Frame(screen, frame.getString(KEY_ID));
                restored.query = frame.getString(KEY_QUERY, "");
                restored.scroll = frame.getInt(KEY_SCROLL);
                restored.openTermId = frame.getString(KEY_TERM);
                stack.add(restored);
            }
        }
        if (stack.isEmpty()) {
            stack.add(new Frame(Screen.HOME, null));
            return false;
        }
        return true;
    }

    private static PaneWallPage place(@Nullable String name) {
        if (name == null) return PaneWallPage.TERMINAL;
        for (PaneWallPage page : PaneWallPage.values()) if (page.name().equals(name)) return page;
        return PaneWallPage.TERMINAL;
    }

    @Nullable
    private static Screen screen(@Nullable String name) {
        if (name == null) return null;
        for (Screen screen : Screen.values()) if (screen.name().equals(name)) return screen;
        return null;
    }

    private Frame top() {
        if (stack.isEmpty()) stack.add(new Frame(Screen.HOME, null));
        return stack.get(stack.size() - 1);
    }

    private void reset(PaneWallPage place) {
        this.place = place == null ? PaneWallPage.TERMINAL : place;
        this.query = "";
        this.textEntryActive = false;
        this.practiceFrame = null;
        stack.clear();
    }
}
