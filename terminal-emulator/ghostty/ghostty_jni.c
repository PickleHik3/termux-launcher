// JNI shim between com.termux.terminal.GhosttyNative and libghostty-vt (statically linked).
//
// Rules this file keeps:
// - Every entry point runs on the thread that owns the session (the main thread); the terminal is
//   not reentrant and nothing here locks.
// - Callbacks fire inside ghostty_terminal_vt_write/resize/reset. They only record what happened
//   or hand bytes to Java, and never write to the terminal. The one that reads it is the render
//   hold's (on_render_hold), which takes the snapshot when a hold begins, as the library asks.
// - No pointer from the library is kept across a mutating call: render-state rows and grid refs
//   are read and copied out within the entry point that produced them.
// - A null or freed handle is a no-op, never a crash.
#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <ghostty/vt.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "GhosttyCore", __VA_ARGS__)
#else
#include <stdio.h>
#define LOGW(...) do { fprintf(stderr, "GhosttyCore: " __VA_ARGS__); fputc('\n', stderr); } while (0)
#endif

// Per-cell layout of the int[] rows handed to Java; mirrored by GhosttyNative.CELL_*.
enum {
    CELL_CODEPOINT = 0,  // primary code point, 0 for an empty cell
    CELL_ATTRS = 1,      // style flags (bits 0-15) | wide << 16 (2 bits) | extra code points << 20
    CELL_FG = 2,         // packed colour: tag << 24 | palette index or 0xRRGGBB
    CELL_BG = 3,
    CELL_UNDERLINE = 4,
    CELL_INTS = 5,
};

// Indices of the int[] state block filled by nativeSync; mirrored by GhosttyNative.STATE_*.
enum {
    ST_COLS = 0, ST_ROWS, ST_CURSOR_X, ST_CURSOR_Y, ST_FLAGS, ST_SCROLLED, ST_HISTORY,
    ST_MODES, ST_KITTY_FLAGS, ST_CURSOR_STYLE, ST_COUNT,
};

// Set in a row's result (nativeSync's dirty[] entry, nativeReadHistoryRow's return) when the row
// soft-wraps into the next; the low bits are the row's count of extra code points.
#define ROW_WRAPPED (1 << 24)

// ST_FLAGS bits.
enum {
    F_CURSOR_VISIBLE = 1, F_ALT_SCREEN = 2, F_CURSOR_BLINK = 4, F_PALETTE_CHANGED = 8,
    F_TITLE_CHANGED = 16, F_PWD_CHANGED = 32, F_BELL = 64, F_DIRTY_FULL = 128,
    F_HELD = 256,        // a render hold (mode 2026) is on now
    F_HOLD_BEGAN = 512,  // a hold began since the last sync; the snapshot is the frame it froze
};

// ST_MODES bits, one per DEC private mode Java reads.
static const struct { uint16_t mode; int bit; } MODE_BITS[] = {
    {1, 1 << 0},     // application cursor keys
    {5, 1 << 1},     // reverse video
    {6, 1 << 2},     // origin
    {7, 1 << 3},     // autowrap
    {25, 1 << 4},    // cursor visible
    {66, 1 << 5},    // application keypad
    {1000, 1 << 6},  // mouse press/release
    {1002, 1 << 7},  // mouse button-event
    {1003, 1 << 8},  // mouse any-event
    {1004, 1 << 9},  // focus events
    {1006, 1 << 10}, // SGR mouse
    {1016, 1 << 11}, // SGR-pixels mouse
    {2004, 1 << 12}, // bracketed paste
    {2026, 1 << 13}, // synchronized output
    {5522, 1 << 14}, // paste events
    {9, 1 << 15},    // X10 mouse
};

typedef struct {
    GhosttyTerminal term;
    GhosttyRenderState render;
    GhosttyRenderStateRowIterator row_iter;
    GhosttyRenderStateRowCells row_cells;
    GhosttyTrackedGridRef top;     // the active area's top row at the last sync
    GhosttyTrackedGridRef bottom;  // and its bottom row, for when a prune took the top one
    bool top_set, bottom_set;
    int last_screen;
    size_t last_history;
    JNIEnv* env;       // set only while an entry point runs
    jobject sink;      // global ref to the GhosttyNative.Sink
    jmethodID on_reply;
    jmethodID on_clipboard;
    uint8_t* xtversion;  // our XTVERSION name, owned here for the terminal's life
    size_t xtversion_len;
    uint16_t cols, rows;
    uint32_t cell_w, cell_h;
    int events;
    GhosttyColorRgb palette[256];
    bool palette_known;
    // Scratch for one row and its extra code points, sized for cols; reused by every sync.
    jint* row_buf;
    jint* dirty_buf;
    uint32_t* extra_buf;
    int buf_cols, buf_rows;
    // The snapshot Java reads, from the last capture(): a sync outside a render hold, or the
    // start of one. While held, syncs leave it and the render state as the hold found them.
    bool held;
    int snap_screen;
    size_t snap_history;
    long snap_scrolled;  // rows that left the screen since the last sync handed them to Java
    uint16_t snap_cx, snap_cy;
    bool snap_visible;
} Core;

// Extra code points (combining marks, ZWJ sequences) one sync can carry; matches
// GhosttyNative.EXTRA_CAPACITY. A cluster past the cap keeps its first code point.
#define EXTRA_CAPACITY 8192

static bool alloc_buffers(Core* c) {
    if (c->row_buf != NULL && c->buf_cols >= c->cols && c->buf_rows >= c->rows) return true;
    jint* row = realloc(c->row_buf, sizeof(jint) * (size_t) c->cols * CELL_INTS);
    if (row == NULL) return false;
    c->row_buf = row;
    jint* dirty = realloc(c->dirty_buf, sizeof(jint) * (size_t) c->rows);
    if (dirty == NULL) return false;
    c->dirty_buf = dirty;
    if (c->extra_buf == NULL) {
        c->extra_buf = malloc(sizeof(uint32_t) * EXTRA_CAPACITY);
        if (c->extra_buf == NULL) return false;
    }
    c->buf_cols = c->cols;
    c->buf_rows = c->rows;
    return true;
}

static Core* core_of(jlong handle) { return (Core*) (intptr_t) handle; }

static void clear_exception(JNIEnv* env) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        LOGW("a Java callback threw; ignored");
    }
}

static void on_write_pty(GhosttyTerminal t, void* ud, const uint8_t* data, size_t len) {
    (void) t;
    Core* c = ud;
    if (c == NULL || c->env == NULL || c->sink == NULL || len == 0 || len > (1u << 24)) return;
    JNIEnv* env = c->env;
    jbyteArray arr = (*env)->NewByteArray(env, (jsize) len);
    if (arr == NULL) { clear_exception(env); return; }
    (*env)->SetByteArrayRegion(env, arr, 0, (jsize) len, (const jbyte*) data);
    (*env)->CallVoidMethod(env, c->sink, c->on_reply, arr);
    clear_exception(env);
    (*env)->DeleteLocalRef(env, arr);
}

static bool capture(Core* c);

// Render hold (mode 2026). When a hold begins the terminal holds exactly the frame the program wants
// left on screen, and nothing after the start of the hold has been parsed yet, so the snapshot is
// taken here. Syncs leave it alone until the hold ends: the program resets the mode, a reset or a
// resize ends it, or Java ends it on its timeout (nativeEndHold; the library has no clock).
static void on_render_hold(GhosttyTerminal t, void* ud, bool held) {
    (void) t;
    Core* c = ud;
    if (c == NULL) return;
    if (held) {
        capture(c);
        c->events |= F_HOLD_BEGAN;
    }
    c->held = held;
}

static void on_bell(GhosttyTerminal t, void* ud) { (void) t; if (ud) ((Core*) ud)->events |= F_BELL; }
static void on_title(GhosttyTerminal t, void* ud) { (void) t; if (ud) ((Core*) ud)->events |= F_TITLE_CHANGED; }
static void on_pwd(GhosttyTerminal t, void* ud) { (void) t; if (ud) ((Core*) ud)->events |= F_PWD_CHANGED; }

static bool on_size(GhosttyTerminal t, void* ud, GhosttySizeReportSize* out) {
    (void) t;
    Core* c = ud;
    if (c == NULL || out == NULL) return false;
    out->rows = c->rows;
    out->columns = c->cols;
    out->cell_width = c->cell_w;
    out->cell_height = c->cell_h;
    return true;
}

// OSC 52 and kitty clipboard writes: the text/plain representation goes to Java, which hands it
// to the same clipboard path the Java core uses. Anything else is answered as unsupported.
static void on_clipboard_write(GhosttyTerminal t, void* ud, const GhosttyClipboardWrite* w) {
    (void) t;
    Core* c = ud;
    GhosttyClipboardWriteReply reply = GHOSTTY_INIT_SIZED(GhosttyClipboardWriteReply);
    reply.result = GHOSTTY_CLIPBOARD_WRITE_RESULT_UNSUPPORTED;
    if (c != NULL && c->env != NULL && c->sink != NULL && w != NULL) {
        for (size_t i = 0; i < w->contents_len; i++) {
            const GhosttyClipboardContent* item = &w->contents[i];
            bool text = item->mime.len == 0
                || (item->mime.len >= 10 && memcmp(item->mime.ptr, "text/plain", 10) == 0);
            if (!text || item->data.len > (1u << 24)) continue;
            JNIEnv* env = c->env;
            jbyteArray arr = (*env)->NewByteArray(env, (jsize) item->data.len);
            if (arr == NULL) { clear_exception(env); break; }
            if (item->data.len > 0)
                (*env)->SetByteArrayRegion(env, arr, 0, (jsize) item->data.len, (const jbyte*) item->data.ptr);
            (*env)->CallVoidMethod(env, c->sink, c->on_clipboard, arr);
            clear_exception(env);
            (*env)->DeleteLocalRef(env, arr);
            reply.result = GHOSTTY_CLIPBOARD_WRITE_RESULT_SUCCESS;
            break;
        }
    }
    if (w != NULL && w->reply != NULL) w->reply(w, &reply);
}

// XTVERSION: answer with the name Java gave at creation. The bytes live in Core until core_free,
// longer than the callback requires; no JNI call here, since callbacks fire inside vt_write.
static GhosttyString on_xtversion(GhosttyTerminal t, void* ud) {
    (void) t;
    Core* c = (Core*) ud;
    GhosttyString s = {.ptr = NULL, .len = 0};
    if (c != NULL && c->xtversion != NULL) { s.ptr = c->xtversion; s.len = c->xtversion_len; }
    return s;
}

static void core_free(Core* c, JNIEnv* env) {
    if (c == NULL) return;
    if (c->top) ghostty_tracked_grid_ref_free(c->top);
    if (c->bottom) ghostty_tracked_grid_ref_free(c->bottom);
    if (c->row_cells) ghostty_render_state_row_cells_free(c->row_cells);
    if (c->row_iter) ghostty_render_state_row_iterator_free(c->row_iter);
    if (c->render) ghostty_render_state_free(c->render);
    if (c->term) ghostty_terminal_free(c->term);
    if (c->sink != NULL && env != NULL) (*env)->DeleteGlobalRef(env, c->sink);
    free(c->xtversion);
    free(c->row_buf);
    free(c->dirty_buf);
    free(c->extra_buf);
    free(c);
}

static int current_screen(Core* c) {
    GhosttyTerminalScreen screen = GHOSTTY_TERMINAL_SCREEN_PRIMARY;
    ghostty_terminal_get(c->term, GHOSTTY_TERMINAL_DATA_ACTIVE_SCREEN, &screen);
    return (int) screen;
}

static size_t history_rows(Core* c) {
    size_t n = 0;
    if (ghostty_terminal_get(c->term, GHOSTTY_TERMINAL_DATA_SCROLLBACK_ROWS, &n) != GHOSTTY_SUCCESS) return 0;
    return n;
}

static void set_history_limit(Core* c, jint lines) {
    size_t line_limit = lines > 0 ? (size_t) lines : 1;
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_SCROLLBACK_MAX_LINES, &line_limit);
}

// Pin the top and bottom rows of the active area, so the next sync can tell how many rows went to
// history. Java names rows by how many have ever scrolled off, so the count has to stay exact even
// when a long write prunes the page holding the top pin.
static void pin_top(Core* c) {
    GhosttyPoint p = {.tag = GHOSTTY_POINT_TAG_ACTIVE, .value = {.coordinate = {.x = 0, .y = 0}}};
    c->top_set = ghostty_tracked_grid_ref_set(c->top, c->term, p) == GHOSTTY_SUCCESS;
    GhosttyPoint b = {.tag = GHOSTTY_POINT_TAG_ACTIVE, .value = {.coordinate = {.x = 0, .y = (uint32_t) (c->rows - 1)}}};
    c->bottom_set = ghostty_tracked_grid_ref_set(c->bottom, c->term, b) == GHOSTTY_SUCCESS;
    c->last_screen = current_screen(c);
    c->last_history = history_rows(c);
}

static jint pack_color(GhosttyStyleColor color) {
    switch (color.tag) {
        case GHOSTTY_STYLE_COLOR_PALETTE:
            return (jint) ((1u << 24) | color.value.palette);
        case GHOSTTY_STYLE_COLOR_RGB:
            return (jint) ((2u << 24) | ((uint32_t) color.value.rgb.r << 16)
                | ((uint32_t) color.value.rgb.g << 8) | color.value.rgb.b);
        default:
            return 0;
    }
}

// A cell erased with a background colour holds no text and carries the colour in the cell itself.
static jint cell_background(GhosttyCell raw, jint styled) {
    GhosttyCellContentTag tag = GHOSTTY_CELL_CONTENT_CODEPOINT;
    ghostty_cell_get(raw, GHOSTTY_CELL_DATA_CONTENT_TAG, &tag);
    if (tag == GHOSTTY_CELL_CONTENT_BG_COLOR_PALETTE) {
        GhosttyColorPaletteIndex index = 0;
        ghostty_cell_get(raw, GHOSTTY_CELL_DATA_COLOR_PALETTE, &index);
        return (jint) ((1u << 24) | index);
    }
    if (tag == GHOSTTY_CELL_CONTENT_BG_COLOR_RGB) {
        GhosttyColorRgb rgb = {0, 0, 0};
        ghostty_cell_get(raw, GHOSTTY_CELL_DATA_COLOR_RGB, &rgb);
        return (jint) ((2u << 24) | ((uint32_t) rgb.r << 16) | ((uint32_t) rgb.g << 8) | rgb.b);
    }
    return styled;
}

static jint pack_attrs(const GhosttyStyle* style, GhosttyCellWide wide, uint32_t extra) {
    if (extra > 0xff) extra = 0xff;
    return (jint) ((uint32_t) style->flags | ((uint32_t) (wide & 3) << 16) | (extra << 20));
}

JNIEXPORT jlong JNICALL Java_com_termux_terminal_GhosttyNative_nativeCreate(
    JNIEnv* env, jclass cls, jint cols, jint rows, jint cell_w, jint cell_h, jint scrollback_lines,
    jobject sink, jbyteArray xtversion) {
    (void) cls;
    if (cols < 1 || rows < 1 || cols > 0xffff || rows > 0xffff || sink == NULL) return 0;
    Core* c = calloc(1, sizeof(Core));
    if (c == NULL) return 0;
    c->cols = (uint16_t) cols;
    c->rows = (uint16_t) rows;
    c->cell_w = cell_w > 0 ? (uint32_t) cell_w : 0;
    c->cell_h = cell_h > 0 ? (uint32_t) cell_h : 0;

    jclass sink_class = (*env)->GetObjectClass(env, sink);
    c->on_reply = (*env)->GetMethodID(env, sink_class, "onPtyReply", "([B)V");
    c->on_clipboard = (*env)->GetMethodID(env, sink_class, "onClipboardWrite", "([B)V");
    if (c->on_reply == NULL || c->on_clipboard == NULL) { clear_exception(env); free(c); return 0; }
    if (xtversion != NULL) {
        jsize n = (*env)->GetArrayLength(env, xtversion);
        if (n > 0) {
            c->xtversion = malloc((size_t) n);
            if (c->xtversion == NULL) { free(c); return 0; }
            (*env)->GetByteArrayRegion(env, xtversion, 0, n, (jbyte*) c->xtversion);
            c->xtversion_len = (size_t) n;
        }
    }
    c->sink = (*env)->NewGlobalRef(env, sink);

    if (ghostty_terminal_new(NULL, &c->term, (uint16_t) cols, (uint16_t) rows) != GHOSTTY_SUCCESS
        || ghostty_render_state_new(NULL, &c->render) != GHOSTTY_SUCCESS
        || ghostty_render_state_row_iterator_new(NULL, &c->row_iter) != GHOSTTY_SUCCESS
        || ghostty_render_state_row_cells_new(NULL, &c->row_cells) != GHOSTTY_SUCCESS) {
        core_free(c, env);
        return 0;
    }
    GhosttyPoint origin = {.tag = GHOSTTY_POINT_TAG_ACTIVE, .value = {.coordinate = {.x = 0, .y = 0}}};
    if (ghostty_terminal_grid_ref_track(c->term, origin, &c->top) != GHOSTTY_SUCCESS
        || ghostty_terminal_grid_ref_track(c->term, origin, &c->bottom) != GHOSTTY_SUCCESS
        || !alloc_buffers(c)) {
        core_free(c, env);
        return 0;
    }

    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_USERDATA, c);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_WRITE_PTY, (const void*) on_write_pty);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_BELL, (const void*) on_bell);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_TITLE_CHANGED, (const void*) on_title);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_PWD_CHANGED, (const void*) on_pwd);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_XTVERSION, (const void*) on_xtversion);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_SIZE, (const void*) on_size);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_CLIPBOARD_WRITE, (const void*) on_clipboard_write);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_RENDER_HOLD, (const void*) on_render_hold);
    // History only has to hold what the next sync copies into the Java transcript; Java picks the
    // row limit (GhosttyCore.historyLimit). Bytes are unlimited so a styled burst cannot prune rows
    // the next sync still needs.
    size_t no_byte_limit = SIZE_MAX;
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_SCROLLBACK_MAX_BYTES, &no_byte_limit);
    set_history_limit(c, scrollback_lines);
    // No images in this mode: refuse kitty graphics rather than store pixels nobody draws.
    uint64_t no_images = 0;
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_KITTY_IMAGE_STORAGE_LIMIT, &no_images);
    if (c->cell_w > 0 && c->cell_h > 0)
        ghostty_terminal_resize(c->term, c->cols, c->rows, c->cell_w, c->cell_h);
    pin_top(c);
    return (jlong) (intptr_t) c;
}

JNIEXPORT void JNICALL Java_com_termux_terminal_GhosttyNative_nativeFree(JNIEnv* env, jclass cls, jlong h) {
    (void) cls;
    core_free(core_of(h), env);
}

// Default colours, so OSC 4/10/11 queries answer what the user sees: 256 palette entries then
// foreground, background and cursor, each 0xRRGGBB.
JNIEXPORT void JNICALL Java_com_termux_terminal_GhosttyNative_nativeSetColors(
    JNIEnv* env, jclass cls, jlong h, jintArray colors) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || colors == NULL || (*env)->GetArrayLength(env, colors) < 259) return;
    jint rgb[259];
    (*env)->GetIntArrayRegion(env, colors, 0, 259, rgb);
    GhosttyColorRgb palette[256];
    for (int i = 0; i < 256; i++)
        palette[i] = (GhosttyColorRgb) {(uint8_t) (rgb[i] >> 16), (uint8_t) (rgb[i] >> 8), (uint8_t) rgb[i]};
    GhosttyColorRgb fg = {(uint8_t) (rgb[256] >> 16), (uint8_t) (rgb[256] >> 8), (uint8_t) rgb[256]};
    GhosttyColorRgb bg = {(uint8_t) (rgb[257] >> 16), (uint8_t) (rgb[257] >> 8), (uint8_t) rgb[257]};
    GhosttyColorRgb cur = {(uint8_t) (rgb[258] >> 16), (uint8_t) (rgb[258] >> 8), (uint8_t) rgb[258]};
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_COLOR_PALETTE, palette);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_COLOR_FOREGROUND, &fg);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_COLOR_BACKGROUND, &bg);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_COLOR_CURSOR, &cur);
    c->palette_known = false;
}

JNIEXPORT void JNICALL Java_com_termux_terminal_GhosttyNative_nativeWrite(
    JNIEnv* env, jclass cls, jlong h, jbyteArray data, jint offset, jint length) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || data == NULL || offset < 0 || length <= 0) return;
    if ((jlong) offset + length > (*env)->GetArrayLength(env, data)) return;
    // One vt_write for the whole range. Not a critical section: callbacks call into Java while the
    // elements are held, which GetByteArrayElements allows and GetPrimitiveArrayCritical does not.
    jbyte* bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (bytes == NULL) { clear_exception(env); return; }
    c->env = env;
    ghostty_terminal_vt_write(c->term, (const uint8_t*) bytes + offset, (size_t) length);
    c->env = NULL;
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
}

// The row limit of the primary screen's history; lowering it prunes at once.
JNIEXPORT void JNICALL Java_com_termux_terminal_GhosttyNative_nativeSetHistoryLimit(
    JNIEnv* env, jclass cls, jlong h, jint lines) {
    (void) env;
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL) return;
    set_history_limit(c, lines);
}

JNIEXPORT void JNICALL Java_com_termux_terminal_GhosttyNative_nativeResize(
    JNIEnv* env, jclass cls, jlong h, jint cols, jint rows, jint cell_w, jint cell_h) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || cols < 1 || rows < 1 || cols > 0xffff || rows > 0xffff) return;
    c->cols = (uint16_t) cols;
    c->rows = (uint16_t) rows;
    c->cell_w = cell_w > 0 ? (uint32_t) cell_w : 0;
    c->cell_h = cell_h > 0 ? (uint32_t) cell_h : 0;
    if (!alloc_buffers(c)) {
        // Keep the old size rather than read past the scratch buffers.
        LOGW("out of memory resizing to %dx%d", cols, rows);
        c->cols = (uint16_t) c->buf_cols;
        c->rows = (uint16_t) c->buf_rows;
        return;
    }
    c->env = env;
    ghostty_terminal_resize(c->term, c->cols, c->rows, c->cell_w, c->cell_h);
    c->env = NULL;
    // Reflow moves rows in and out of history; the Java buffer reflows its own copy.
    pin_top(c);
}

JNIEXPORT void JNICALL Java_com_termux_terminal_GhosttyNative_nativeReset(JNIEnv* env, jclass cls, jlong h) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL) return;
    c->env = env;
    ghostty_terminal_reset(c->term);
    c->env = NULL;
    pin_top(c);
}

// End a render hold that ran past Java's timeout: reset mode 2026 as the program would have. Setting
// the mode from here never calls on_render_hold, so the hold is marked over here too; the next sync
// takes a fresh snapshot.
JNIEXPORT void JNICALL Java_com_termux_terminal_GhosttyNative_nativeEndHold(JNIEnv* env, jclass cls, jlong h) {
    (void) env;
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || !c->held) return;
    GhosttyTerminalModeConfig cfg = {.mode = ghostty_mode_new(2026, false), .value = false};
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_MODE, &cfg);
    c->held = false;
}

static int read_modes(Core* c) {
    int bits = 0;
    for (size_t i = 0; i < sizeof(MODE_BITS) / sizeof(MODE_BITS[0]); i++) {
        GhosttyTerminalModeConfig cfg = {.mode = ghostty_mode_new(MODE_BITS[i].mode, false), .value = false};
        if (ghostty_terminal_get(c->term, GHOSTTY_TERMINAL_DATA_MODE, &cfg) == GHOSTTY_SUCCESS && cfg.value)
            bits |= MODE_BITS[i].bit;
    }
    return bits;
}

// Copy one render-state row (the iterator's current row) into cells/extra. Returns the number of
// extra code points written, or -1 when the row could not be read.
static int copy_render_row(Core* c, jint* cells, int cols, uint32_t* extra, int extra_cap) {
    if (ghostty_render_state_row_get(c->row_iter, GHOSTTY_RENDER_STATE_ROW_DATA_CELLS, &c->row_cells) != GHOSTTY_SUCCESS)
        return -1;
    int x = 0, used = 0;
    while (x < cols && ghostty_render_state_row_cells_next(c->row_cells)) {
        jint* cell = cells + x * CELL_INTS;
        GhosttyCell raw = 0;
        GhosttyStyle style;
        ghostty_style_default(&style);
        uint32_t len = 0;
        GhosttyCellWide wide = GHOSTTY_CELL_WIDE_NARROW;
        ghostty_render_state_row_cells_get(c->row_cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_RAW, &raw);
        ghostty_render_state_row_cells_get(c->row_cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_STYLE, &style);
        ghostty_render_state_row_cells_get(c->row_cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_GRAPHEMES_LEN, &len);
        ghostty_cell_get(raw, GHOSTTY_CELL_DATA_WIDE, &wide);
        uint32_t cps[16];
        uint32_t first = 0, extras = 0;
        if (len > 0) {
            GhosttyCodepointBuffer buf = {.ptr = cps, .cap = 16, .len = 0};
            if (ghostty_render_state_row_cells_get(c->row_cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_GRAPHEMES_BUF, &buf) == GHOSTTY_SUCCESS) {
                size_t n = buf.len < buf.cap ? buf.len : buf.cap;
                if (n > 0) first = cps[0];
                for (size_t i = 1; i < n && used < extra_cap; i++) { extra[used++] = cps[i]; extras++; }
            }
        }
        cell[CELL_CODEPOINT] = (jint) first;
        cell[CELL_ATTRS] = pack_attrs(&style, wide, extras);
        cell[CELL_FG] = pack_color(style.fg_color);
        cell[CELL_BG] = cell_background(raw, pack_color(style.bg_color));
        cell[CELL_UNDERLINE] = pack_color(style.underline_color);
        x++;
    }
    for (; x < cols; x++) memset(cells + x * CELL_INTS, 0, sizeof(jint) * CELL_INTS);
    return used;
}

// Take the snapshot: the render state, and the scalars that must come from the same moment (screen,
// history, rows scrolled off, cursor). Called by a sync outside a hold and when a hold begins.
static bool capture(Core* c) {
    int screen = current_screen(c);
    size_t history = history_rows(c);
    long scrolled = 0;
    if (screen == GHOSTTY_TERMINAL_SCREEN_PRIMARY && screen == c->last_screen) {
        // Screen coordinates count history first, so a row that was at active y is now at
        // history + y - scrolled.
        GhosttyPointCoordinate p;
        GhosttyResult where = c->top_set
            ? ghostty_tracked_grid_ref_point(c->top, GHOSTTY_POINT_TAG_SCREEN, &p)
            : GHOSTTY_INVALID_VALUE;
        if (where == GHOSTTY_SUCCESS) {
            scrolled = (long) history - (long) p.y;
        } else if (where == GHOSTTY_NO_VALUE) {
            // The top row's page was pruned (or erased). The bottom row may still be there; if it
            // is gone too, every row of the last sync is, and counting exactly the rows history and
            // the screen hold now keeps every older index below the oldest readable one.
            GhosttyResult bottom = c->bottom_set
                ? ghostty_tracked_grid_ref_point(c->bottom, GHOSTTY_POINT_TAG_SCREEN, &p)
                : GHOSTTY_INVALID_VALUE;
            scrolled = bottom == GHOSTTY_SUCCESS
                ? (long) history + (long) (c->rows - 1) - (long) p.y
                : (long) history + (long) c->rows;
        } else {
            scrolled = (long) history - (long) c->last_history;
        }
        if (scrolled < 0) scrolled = 0;
    }
    // Re-pin only on the primary screen: a pin left there while the alternate screen is up still
    // counts the rows a write scrolled off before switching, and the sync after the switch back
    // copies them.
    if (screen == GHOSTTY_TERMINAL_SCREEN_PRIMARY || c->last_screen != GHOSTTY_TERMINAL_SCREEN_PRIMARY) pin_top(c);

    c->snap_screen = screen;
    c->snap_history = history;
    c->snap_scrolled += scrolled;
    uint16_t cx = 0, cy = 0;
    ghostty_terminal_get(c->term, GHOSTTY_TERMINAL_DATA_CURSOR_X, &cx);
    ghostty_terminal_get(c->term, GHOSTTY_TERMINAL_DATA_CURSOR_Y, &cy);
    bool visible = false;
    ghostty_terminal_get(c->term, GHOSTTY_TERMINAL_DATA_CURSOR_VISIBLE, &visible);
    c->snap_cx = cx;
    c->snap_cy = cy;
    c->snap_visible = visible;
    return ghostty_render_state_update(c->render, c->term) == GHOSTTY_SUCCESS;
}

// Bring Java up to date after writes: fills state[] (see ST_*), and for every row that changed
// since the last sync writes its cells to screen[y * cols * CELL_INTS] and its extra code points to
// extra[], with dirty[y] = number of extras for the row (| ROW_WRAPPED), or -1 when it did not change.
JNIEXPORT jboolean JNICALL Java_com_termux_terminal_GhosttyNative_nativeSync(
    JNIEnv* env, jclass cls, jlong h, jintArray state_out, jintArray screen_out, jintArray dirty_out,
    jintArray extra_out) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || state_out == NULL || screen_out == NULL || dirty_out == NULL || extra_out == NULL) return JNI_FALSE;
    const int cols = c->cols, rows = c->rows;
    if ((*env)->GetArrayLength(env, state_out) < ST_COUNT
        || (*env)->GetArrayLength(env, screen_out) < cols * rows * CELL_INTS
        || (*env)->GetArrayLength(env, dirty_out) < rows) return JNI_FALSE;
    int extra_cap = (*env)->GetArrayLength(env, extra_out);
    if (extra_cap > EXTRA_CAPACITY) extra_cap = EXTRA_CAPACITY;

    jint st[ST_COUNT];
    memset(st, 0, sizeof(st));
    if (!c->held && !capture(c)) return JNI_FALSE;
    GhosttyRenderStateDirty dirty = GHOSTTY_RENDER_STATE_DIRTY_FULL;
    ghostty_render_state_get(c->render, GHOSTTY_RENDER_STATE_DATA_DIRTY, &dirty);
    GhosttyRenderStateCursor cursor = GHOSTTY_INIT_SIZED(GhosttyRenderStateCursor);
    ghostty_render_state_get(c->render, GHOSTTY_RENDER_STATE_DATA_CURSOR, &cursor);
    GhosttyRenderStateColors colors = GHOSTTY_INIT_SIZED(GhosttyRenderStateColors);
    if (ghostty_render_state_get(c->render, GHOSTTY_RENDER_STATE_DATA_COLORS, &colors) == GHOSTTY_SUCCESS) {
        if (c->palette_known && memcmp(c->palette, colors.palette, sizeof(c->palette)) != 0)
            c->events |= F_PALETTE_CHANGED;
        memcpy(c->palette, colors.palette, sizeof(c->palette));
        c->palette_known = true;
    }

    uint8_t kitty = 0;
    ghostty_terminal_get(c->term, GHOSTTY_TERMINAL_DATA_KITTY_KEYBOARD_FLAGS, &kitty);

    int flags = c->events;
    c->events = 0;
    if (c->snap_visible) flags |= F_CURSOR_VISIBLE;
    if (c->snap_screen == GHOSTTY_TERMINAL_SCREEN_ALTERNATE) flags |= F_ALT_SCREEN;
    if (c->held) flags |= F_HELD;
    if (cursor.blinking) flags |= F_CURSOR_BLINK;
    if (dirty == GHOSTTY_RENDER_STATE_DIRTY_FULL) flags |= F_DIRTY_FULL;
    st[ST_COLS] = cols;
    st[ST_ROWS] = rows;
    st[ST_CURSOR_X] = c->snap_cx;
    st[ST_CURSOR_Y] = c->snap_cy;
    st[ST_FLAGS] = flags;
    st[ST_SCROLLED] = (jint) c->snap_scrolled;
    c->snap_scrolled = 0;
    st[ST_HISTORY] = (jint) c->snap_history;
    st[ST_MODES] = read_modes(c);
    st[ST_KITTY_FLAGS] = kitty;
    st[ST_CURSOR_STYLE] = (jint) cursor.visual_style;
    (*env)->SetIntArrayRegion(env, state_out, 0, ST_COUNT, st);

    // Rows: copy each changed row into the reusable row buffer, then into the Java arrays.
    jint* row_cells = c->row_buf;
    uint32_t* extra = c->extra_buf;
    jint* dirty_rows = c->dirty_buf;
    for (int y = 0; y < rows; y++) dirty_rows[y] = -1;
    int extra_used = 0;
    if (dirty != GHOSTTY_RENDER_STATE_DIRTY_FALSE
        && ghostty_render_state_get(c->render, GHOSTTY_RENDER_STATE_DATA_ROW_ITERATOR, &c->row_iter) == GHOSTTY_SUCCESS) {
        int y = 0;
        while (y < rows && ghostty_render_state_row_iterator_next(c->row_iter)) {
            bool row_dirty = dirty == GHOSTTY_RENDER_STATE_DIRTY_FULL;
            if (!row_dirty) ghostty_render_state_row_get(c->row_iter, GHOSTTY_RENDER_STATE_ROW_DATA_DIRTY, &row_dirty);
            if (row_dirty) {
                int n = copy_render_row(c, row_cells, cols, extra + extra_used, extra_cap - extra_used);
                if (n >= 0) {
                    (*env)->SetIntArrayRegion(env, screen_out, y * cols * CELL_INTS, cols * CELL_INTS, row_cells);
                    GhosttyRow raw_row = 0;
                    bool wrap = false;
                    if (ghostty_render_state_row_get(c->row_iter, GHOSTTY_RENDER_STATE_ROW_DATA_RAW, &raw_row) == GHOSTTY_SUCCESS)
                        ghostty_row_get(raw_row, GHOSTTY_ROW_DATA_WRAP, &wrap);
                    dirty_rows[y] = n | (wrap ? ROW_WRAPPED : 0);
                    extra_used += n;
                }
                bool clean = false;
                ghostty_render_state_row_set(c->row_iter, GHOSTTY_RENDER_STATE_ROW_OPT_DIRTY, &clean);
            }
            y++;
        }
    }
    (*env)->SetIntArrayRegion(env, dirty_out, 0, rows, dirty_rows);
    if (extra_used > 0) (*env)->SetIntArrayRegion(env, extra_out, 0, extra_used, (const jint*) extra);
    GhosttyRenderStateDirty clean_state = GHOSTTY_RENDER_STATE_DIRTY_FALSE;
    ghostty_render_state_set(c->render, GHOSTTY_RENDER_STATE_OPT_DIRTY, &clean_state);
    return JNI_TRUE;
}

// Read one history row (0 = oldest) cell by cell into cells/extra, when a reader asks for it (Java
// keeps no copy of history). Only valid between a sync and the next write. Returns the number of extra code points (| ROW_WRAPPED), or -1 if the row is gone.
JNIEXPORT jint JNICALL Java_com_termux_terminal_GhosttyNative_nativeReadHistoryRow(
    JNIEnv* env, jclass cls, jlong h, jint y, jintArray cells_out, jintArray extra_out) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || y < 0 || cells_out == NULL || extra_out == NULL) return -1;
    const int cols = c->cols;
    if ((*env)->GetArrayLength(env, cells_out) < cols * CELL_INTS) return -1;
    int extra_cap = (*env)->GetArrayLength(env, extra_out);
    if (extra_cap > EXTRA_CAPACITY) extra_cap = EXTRA_CAPACITY;
    jint* row = c->row_buf;
    uint32_t* extra = c->extra_buf;
    memset(row, 0, sizeof(jint) * (size_t) cols * CELL_INTS);
    int used = 0;
    bool wrap = false;
    // Look the row up once: a grid ref is a page and a cell within it, and every page holds rows
    // of the terminal's full width, so the row's other cells differ only in x.
    GhosttyGridRef row_ref = {0};
    GhosttyPoint p = {.tag = GHOSTTY_POINT_TAG_HISTORY, .value = {.coordinate = {.x = 0, .y = (uint32_t) y}}};
    if (ghostty_terminal_grid_ref(c->term, p, &row_ref) != GHOSTTY_SUCCESS) return -1;
    // The terminal's own width bounds x, in case it ever differs from ours (a failed resize).
    uint16_t term_cols = 0;
    if (ghostty_terminal_get(c->term, GHOSTTY_TERMINAL_DATA_COLS, &term_cols) != GHOSTTY_SUCCESS) return -1;
    int width = term_cols < cols ? term_cols : cols;
    for (int x = 0; x < width; x++) {
        GhosttyGridRef ref = row_ref;
        ref.x = (uint16_t) x;
        GhosttyCell raw = 0;
        GhosttyStyle style;
        ghostty_style_default(&style);
        GhosttyCellWide wide = GHOSTTY_CELL_WIDE_NARROW;
        ghostty_grid_ref_cell(&ref, &raw);
        ghostty_grid_ref_style(&ref, &style);
        ghostty_cell_get(raw, GHOSTTY_CELL_DATA_WIDE, &wide);
        uint32_t cps[16];
        size_t n = 0;
        if (ghostty_grid_ref_graphemes(&ref, cps, 16, &n) != GHOSTTY_SUCCESS) n = 0;
        uint32_t extras = 0;
        for (size_t i = 1; i < n && used < extra_cap; i++) { extra[used++] = cps[i]; extras++; }
        jint* cell = row + x * CELL_INTS;
        cell[CELL_CODEPOINT] = n > 0 ? (jint) cps[0] : 0;
        cell[CELL_ATTRS] = pack_attrs(&style, wide, extras);
        cell[CELL_FG] = pack_color(style.fg_color);
        cell[CELL_BG] = cell_background(raw, pack_color(style.bg_color));
        cell[CELL_UNDERLINE] = pack_color(style.underline_color);
        if (x == 0) {
            GhosttyRow r = 0;
            if (ghostty_grid_ref_row(&ref, &r) == GHOSTTY_SUCCESS) ghostty_row_get(r, GHOSTTY_ROW_DATA_WRAP, &wrap);
        }
    }
    (*env)->SetIntArrayRegion(env, cells_out, 0, cols * CELL_INTS, row);
    if (used > 0) (*env)->SetIntArrayRegion(env, extra_out, 0, used, (const jint*) extra);
    return used | (wrap ? ROW_WRAPPED : 0);
}

// The current palette, foreground, background and cursor colour as 0xRRGGBB (259 entries).
JNIEXPORT jboolean JNICALL Java_com_termux_terminal_GhosttyNative_nativeGetColors(
    JNIEnv* env, jclass cls, jlong h, jintArray out) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || out == NULL || (*env)->GetArrayLength(env, out) < 259) return JNI_FALSE;
    GhosttyRenderStateColors colors = GHOSTTY_INIT_SIZED(GhosttyRenderStateColors);
    if (ghostty_render_state_get(c->render, GHOSTTY_RENDER_STATE_DATA_COLORS, &colors) != GHOSTTY_SUCCESS)
        return JNI_FALSE;
    jint rgb[259];
    for (int i = 0; i < 256; i++)
        rgb[i] = (jint) (0xff000000u | ((uint32_t) colors.palette[i].r << 16) | ((uint32_t) colors.palette[i].g << 8) | colors.palette[i].b);
    rgb[256] = (jint) (0xff000000u | ((uint32_t) colors.foreground.r << 16) | ((uint32_t) colors.foreground.g << 8) | colors.foreground.b);
    rgb[257] = (jint) (0xff000000u | ((uint32_t) colors.background.r << 16) | ((uint32_t) colors.background.g << 8) | colors.background.b);
    rgb[258] = colors.cursor_has_value
        ? (jint) (0xff000000u | ((uint32_t) colors.cursor.r << 16) | ((uint32_t) colors.cursor.g << 8) | colors.cursor.b)
        : rgb[256];
    (*env)->SetIntArrayRegion(env, out, 0, 259, rgb);
    return JNI_TRUE;
}

static jstring borrowed_string(JNIEnv* env, Core* c, GhosttyTerminalData what) {
    GhosttyString s = {0};
    if (c == NULL || ghostty_terminal_get(c->term, what, &s) != GHOSTTY_SUCCESS || s.ptr == NULL) return NULL;
    jbyteArray arr = (*env)->NewByteArray(env, (jsize) s.len);
    if (arr == NULL) { clear_exception(env); return NULL; }
    if (s.len > 0) (*env)->SetByteArrayRegion(env, arr, 0, (jsize) s.len, (const jbyte*) s.ptr);
    // Decoded in Java (GhosttyNative.utf8), so invalid UTF-8 cannot trip NewStringUTF.
    jclass cls = (*env)->FindClass(env, "com/termux/terminal/GhosttyNative");
    if (cls == NULL) { clear_exception(env); return NULL; }
    jmethodID utf8 = (*env)->GetStaticMethodID(env, cls, "utf8", "([B)Ljava/lang/String;");
    if (utf8 == NULL) { clear_exception(env); return NULL; }
    jstring result = (jstring) (*env)->CallStaticObjectMethod(env, cls, utf8, arr);
    clear_exception(env);
    (*env)->DeleteLocalRef(env, arr);
    return result;
}

JNIEXPORT jstring JNICALL Java_com_termux_terminal_GhosttyNative_nativeGetTitle(JNIEnv* env, jclass cls, jlong h) {
    (void) cls;
    return borrowed_string(env, core_of(h), GHOSTTY_TERMINAL_DATA_TITLE);
}

JNIEXPORT jstring JNICALL Java_com_termux_terminal_GhosttyNative_nativeGetPwd(JNIEnv* env, jclass cls, jlong h) {
    (void) cls;
    return borrowed_string(env, core_of(h), GHOSTTY_TERMINAL_DATA_PWD);
}

// The primary screen's memory, for measuring, in the order of GhosttyNative.MEM_*: resident,
// virtual and compressed bytes, pages, compressed pages, and 1 when compression can free memory
// here (0 when not); all -1 when unavailable. Looks at every page: not for every write.
JNIEXPORT jlongArray JNICALL Java_com_termux_terminal_GhosttyNative_nativeMemoryUsage(JNIEnv* env, jclass cls, jlong h) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL) return NULL;
    GhosttyTerminalMemoryUsage usage = GHOSTTY_INIT_SIZED(GhosttyTerminalMemoryUsage);
    jlong v[6] = {-1, -1, -1, -1, -1, -1};
    if (ghostty_terminal_get(c->term, GHOSTTY_TERMINAL_DATA_MEMORY_USAGE, &usage) == GHOSTTY_SUCCESS) {
        v[0] = (jlong) usage.primary_resident_bytes;
        v[1] = (jlong) usage.primary_virtual_bytes;
        v[2] = (jlong) usage.primary_compressed_bytes;
        v[3] = (jlong) usage.primary_pages;
        v[4] = (jlong) usage.primary_compressed_pages;
        v[5] = usage.compression_supported ? 1 : 0;
    }
    jlongArray arr = (*env)->NewLongArray(env, 6);
    if (arr == NULL) { clear_exception(env); return NULL; }
    (*env)->SetLongArrayRegion(env, arr, 0, 6, v);
    return arr;
}

// Compress the history once, synchronously (full) or one bounded step. Returns the library's
// result (0 unsupported, 1 more work pending, 2 complete) or -1 on error. For measuring.
JNIEXPORT jint JNICALL Java_com_termux_terminal_GhosttyNative_nativeCompress(
    JNIEnv* env, jclass cls, jlong h, jboolean full) {
    (void) env;
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL) return -1;
    GhosttyTerminalCompressionResult result = GHOSTTY_TERMINAL_COMPRESSION_RESULT_UNSUPPORTED;
    GhosttyTerminalCompressionMode mode = full ? GHOSTTY_TERMINAL_COMPRESSION_MODE_FULL
                                               : GHOSTTY_TERMINAL_COMPRESSION_MODE_INCREMENTAL;
    if (ghostty_terminal_compress(c->term, mode, &result) != GHOSTTY_SUCCESS) return -1;
    return (jint) result;
}
