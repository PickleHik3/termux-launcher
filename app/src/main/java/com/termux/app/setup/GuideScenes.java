package com.termux.app.setup;

import static com.termux.app.setup.GestureDemoView.DECEL;
import static com.termux.app.setup.GestureDemoView.INOUT;
import static com.termux.app.setup.GestureDemoView.KEEP;
import static com.termux.app.setup.GestureDemoView.LINEAR;

import android.content.res.Resources;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.setup.GestureDemoView.Block;
import com.termux.app.setup.GestureDemoView.Kind;
import com.termux.app.setup.GestureDemoView.Scene;

/** One static builder per chapter: the blocks and their keyframes, as data. */
final class GuideScenes {
    private GuideScenes() {}

    private static final int SWIPE = 600, SETTLE = 300, PULSE = 600, TAP = 150;
    private static final float RING = 0.07f;

    @NonNull
    static Scene forChapter(@NonNull String id, @NonNull Resources res) {
        switch (id) {
            case GuideChapters.PAGES: return pages();
            case GuideChapters.APPS: return apps();
            case GuideChapters.DOCK: return dock();
            case GuideChapters.KEYBOARD: return keyboard();
            case GuideChapters.STATUS: return status();
            case GuideChapters.PALETTE: return palette();
            case GuideChapters.HELP: return help();
            case GuideChapters.PANES: return panes();
            default: return desktop(res.getString(R.string.setup_guide_pill_starting));
        }
    }

    // ---- ring helpers -------------------------------------------------------------------------

    private static Block hold(Block b, int t) {
        return b.to(t, LINEAR, KEEP, KEEP, KEEP, KEEP, KEEP);
    }

    /** The ring appears at (x, y) at time t. */
    private static void show(Scene s, int t, float x, float y) {
        hold(s.ring, t - 1).to(t, LINEAR, x, y, RING, KEEP, 0f);
        s.ring.to(t + 150, LINEAR, KEEP, KEEP, KEEP, KEEP, 1f);
    }

    /** The ring moves to (x, y) between t0 and t1. */
    private static void drag(Scene s, int t0, int t1, float x, float y) {
        hold(s.ring, t0).to(t1, INOUT, x, y, KEEP, KEEP, KEEP);
    }

    private static void hide(Scene s, int t) {
        hold(s.ring, t).to(t + 150, LINEAR, KEEP, KEEP, KEEP, KEEP, 0f);
    }

    /** A hold: the ring swells and settles over PULSE ms. */
    private static void pulse(Scene s, int t) {
        hold(s.ring, t).to(t + PULSE / 2, INOUT, KEEP, KEEP, RING * 1.7f, KEEP, KEEP);
        s.ring.to(t + PULSE, INOUT, KEEP, KEEP, RING, KEEP, KEEP);
    }

    private static void tap(Scene s, int t) {
        hold(s.ring, t).to(t + TAP / 2, LINEAR, KEEP, KEEP, RING * 0.7f, KEEP, KEEP);
        s.ring.to(t + TAP, LINEAR, KEEP, KEEP, RING, KEEP, KEEP);
    }

    private static Block fade(Block b, int t, int ms, float a) {
        return hold(b, t).to(t + ms, DECEL, KEEP, KEEP, KEEP, KEEP, a);
    }

    private static Block move(Block b, int t, float x, float y, float w, float h, float a) {
        return hold(b, t).to(t + SETTLE, DECEL, x, y, w, h, a);
    }

    private static Scene home() {
        Scene s = new Scene();
        s.add(Kind.WALLPAPER, 0f, 0f, 1f, 1f);
        s.add(Kind.STATUS, 0.06f, 0.025f, 0.88f, 0.045f);
        return s;
    }

    // ---- chapters -----------------------------------------------------------------------------

    private static Scene pages() {
        Scene s = home();
        Block clock = s.add(Kind.CLOCK, 0.1f, 0.14f, 0.8f, 0.2f);
        s.add(Kind.DOCK, 0.06f, 0.84f, 0.88f, 0.1f);
        Block term = s.add(Kind.TERMINAL, 1.1f, 0.1f, 0.88f, 0.7f);
        show(s, 300, 0.8f, 0.55f);
        drag(s, 450, 450 + SWIPE, 0.2f, 0.55f);
        hide(s, 1200);
        hold(clock, 700).to(700 + SETTLE + 300, DECEL, -0.9f, KEEP, KEEP, KEEP, 0f);
        show(s, 2100, 0.8f, 0.55f);
        drag(s, 2250, 2250 + SWIPE, 0.2f, 0.55f);
        hide(s, 3000);
        hold(term, 2500).to(2500 + SETTLE + 300, DECEL, 0.06f, KEEP, KEEP, KEEP, KEEP);
        hold(term, 4300);
        return s;
    }

    private static Scene apps() {
        Scene s = home();
        s.add(Kind.CLOCK, 0.1f, 0.14f, 0.8f, 0.2f);
        s.add(Kind.DOCK, 0.06f, 0.84f, 0.88f, 0.1f);
        Block list = s.add(Kind.SHEET, 0.08f, -0.7f, 0.84f, 0.62f);
        show(s, 300, 0.5f, 0.86f);
        drag(s, 450, 450 + SWIPE, 0.5f, 0.95f);
        hold(list, 600).to(600 + SWIPE, DECEL, KEEP, 0.1f, KEEP, KEEP, KEEP);
        hide(s, 1300);
        hold(list, 3800);
        return s;
    }

    private static Scene dock() {
        Scene s = home();
        s.add(Kind.CLOCK, 0.1f, 0.14f, 0.8f, 0.2f);
        Block dock = s.add(Kind.DOCK, 0.06f, 0.84f, 0.88f, 0.1f);
        Block outline = s.add(Kind.OUTLINE, 0.06f, 0.84f, 0.88f, 0.1f).hidden();
        Block menu = s.add(Kind.MENU, 0.3f, 0.45f, 0.4f, 0.16f).hidden();
        show(s, 200, 0.5f, 0.55f);
        pulse(s, 400);
        fade(menu, 900, SETTLE, 1f);
        drag(s, 1300, 1600, 0.5f, 0.52f);
        tap(s, 1700);
        fade(menu, 1900, SETTLE, 0f);
        hide(s, 1900);
        move(dock, 2000, 0.06f, 0.78f, KEEP, KEEP, KEEP);
        move(outline, 2000, 0.06f, 0.78f, KEEP, KEEP, 1f);
        hold(outline, 4200);
        return s;
    }

    private static Scene keyboard() {
        Scene s = new Scene();
        s.add(Kind.WALLPAPER, 0f, 0f, 1f, 1f);
        Block term = s.add(Kind.TERMINAL, 0.06f, 0.1f, 0.88f, 0.46f);
        Block kb = s.add(Kind.KEYBOARD, 0.06f, 0.6f, 0.88f, 0.34f);
        show(s, 300, 0.5f, 0.58f);
        drag(s, 450, 450 + SWIPE, 0.5f, 0.9f);
        hold(kb, 600).to(600 + SWIPE, DECEL, KEEP, 1.0f, KEEP, KEEP, 0f);
        hold(term, 600).to(600 + SWIPE, DECEL, KEEP, KEEP, KEEP, 0.82f, KEEP);
        hide(s, 1300);
        show(s, 2300, 0.5f, 0.9f);
        drag(s, 2450, 2450 + SWIPE, 0.5f, 0.58f);
        hold(kb, 2600).to(2600 + SWIPE, DECEL, KEEP, 0.6f, KEEP, KEEP, 1f);
        hold(term, 2600).to(2600 + SWIPE, DECEL, KEEP, KEEP, KEEP, 0.46f, KEEP);
        hide(s, 3300);
        hold(s.ring, 4000);
        s.posterMs = 1500;
        return s;
    }

    private static Scene status() {
        Scene s = new Scene();
        s.add(Kind.WALLPAPER, 0f, 0f, 1f, 1f);
        s.add(Kind.TERMINAL, 0.06f, 0.22f, 0.88f, 0.7f);
        Block bar = s.add(Kind.STATUS, 0.06f, 0.03f, 0.88f, 0.045f);
        show(s, 300, 0.5f, 0.1f);
        drag(s, 450, 450 + SWIPE, 0.5f, 0.4f);
        hold(bar, 600).to(600 + SWIPE, DECEL, KEEP, KEEP, KEEP, 0.34f, KEEP);
        hide(s, 1300);
        show(s, 2300, 0.5f, 0.4f);
        drag(s, 2450, 2450 + SWIPE, 0.5f, 0.1f);
        hold(bar, 2600).to(2600 + SWIPE, DECEL, KEEP, KEEP, KEEP, 0.045f, KEEP);
        hide(s, 3300);
        hold(s.ring, 4000);
        s.posterMs = 1500;
        return s;
    }

    private static Scene palette() {
        Scene s = home();
        s.add(Kind.CLOCK, 0.1f, 0.14f, 0.8f, 0.2f);
        s.add(Kind.DOCK, 0.06f, 0.84f, 0.88f, 0.1f);
        Block sheet = s.add(Kind.SHEET, 0.08f, 1.05f, 0.84f, 0.6f);
        Block field = s.add(Kind.FIELD, 0.14f, 1.1f, 0.72f, 0.07f);
        show(s, 300, 0.5f, 0.7f);
        drag(s, 450, 450 + SWIPE, 0.5f, 0.3f);
        hold(sheet, 600).to(600 + SWIPE, DECEL, KEEP, 0.38f, KEEP, KEEP, KEEP);
        hold(field, 600).to(600 + SWIPE, DECEL, KEEP, 0.43f, KEEP, KEEP, KEEP);
        hide(s, 1300);
        hold(sheet, 3800);
        return s;
    }

    private static Scene help() {
        Scene s = new Scene();
        s.add(Kind.WALLPAPER, 0f, 0f, 1f, 1f);
        s.add(Kind.TERMINAL, 0.06f, 0.1f, 0.88f, 0.82f);
        Block d1 = s.add(Kind.DOT, 0.62f, 0.855f, 0.09f, 0.05f).hidden();
        Block d2 = s.add(Kind.DOT, 0.7f, 0.76f, 0.09f, 0.05f).hidden();
        Block d3 = s.add(Kind.DOT, "?", 0.82f, 0.68f, 0.09f, 0.05f).hidden();
        Block tint = s.add(Kind.TINT, 0f, 0f, 1f, 1f).hidden();
        Block pop = s.add(Kind.POPOVER, 0.2f, 0.28f, 0.6f, 0.16f).hidden();
        show(s, 200, 0.88f, 0.88f);
        pulse(s, 400);
        fade(d1, 900, SETTLE, 1f);
        fade(d2, 900, SETTLE, 1f);
        fade(d3, 900, SETTLE, 1f);
        drag(s, 1300, 1800, 0.865f, 0.705f);
        tap(s, 1900);
        fade(d1, 2100, SETTLE, 0f);
        fade(d2, 2100, SETTLE, 0f);
        hide(s, 2100);
        fade(tint, 2100, SETTLE, 0.6f);
        fade(pop, 2300, SETTLE, 1f);
        hold(pop, 4300);
        return s;
    }

    private static Scene panes() {
        Scene s = new Scene();
        s.add(Kind.WALLPAPER, 0f, 0f, 1f, 1f);
        Block a = s.add(Kind.TERMINAL, 0.06f, 0.1f, 0.88f, 0.82f);
        Block b = s.add(Kind.TERMINAL, 0.94f, 0.1f, 0f, 0.82f).hidden();
        Block c = s.add(Kind.TERMINAL, 0.51f, 0.92f, 0.43f, 0f).hidden();
        Block focus = s.add(Kind.OUTLINE, 0.06f, 0.1f, 0.43f, 0.82f).hidden();
        move(a, 500, 0.06f, 0.1f, 0.43f, 0.82f, 1f);
        move(b, 500, 0.51f, 0.1f, 0.43f, 0.82f, 1f);
        move(b, 1800, 0.51f, 0.1f, 0.43f, 0.4f, 1f);
        move(c, 1800, 0.51f, 0.52f, 0.43f, 0.4f, 1f);
        show(s, 3000, 0.28f, 0.5f);
        tap(s, 3300);
        fade(focus, 3400, SETTLE, 1f);
        hide(s, 3700);
        hold(focus, 4500);
        return s;
    }

    private static Scene desktop(@NonNull String pillLabel) {
        Scene s = new Scene();
        s.add(Kind.WALLPAPER, 0f, 0f, 1f, 1f);
        Block term = s.add(Kind.TERMINAL, 0.06f, 0.1f, 0.88f, 0.82f);
        Block pill = s.add(Kind.PILL, pillLabel, 0.2f, 0.03f, 0.6f, 0.05f).hidden();
        Block win = s.add(Kind.SHEET, 1.0f, 0.1f, 0.43f, 0.82f);
        fade(pill, 300, SETTLE, 1f);
        fade(pill, 2300, SETTLE, 0f);
        move(term, 2000, 0.06f, 0.1f, 0.43f, 0.82f, 1f);
        move(win, 2000, 0.51f, 0.1f, 0.43f, 0.82f, 1f);
        hold(win, 4000);
        return s;
    }
}
