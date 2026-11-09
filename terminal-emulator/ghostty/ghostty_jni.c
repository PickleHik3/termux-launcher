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

#include "ghostty_png.h"

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
    ST_MODES, ST_KITTY_FLAGS, ST_CURSOR_STYLE, ST_MOUSE_SHAPE, ST_COUNT,
};

// Set in a row's result (nativeSync's dirty[] entry, nativeReadHistoryRow's return) when the row
// soft-wraps into the next; the low bits are the row's count of extra code points.
#define ROW_WRAPPED (1 << 24)
// Set in a row's result when OSC 133 marked it as the first line of a shell prompt.
#define ROW_PROMPT (1 << 25)

// Link runs handed to Java (GhosttyNative.LINK_*): links[0] is the run count, then per run its row,
// first column, end column (exclusive), and the offset and length of its URI in the byte array.
enum { LINK_Y = 0, LINK_X0, LINK_X1, LINK_URI_OFFSET, LINK_URI_LENGTH, LINK_INTS };
// The longest URI kept; a longer link reads as plain text, as the Java core's pool refuses it.
#define URI_CAP 8192

// Events handed to Java during a write (GhosttyNative.EVENT_*), in the order they happened.
enum { EVENT_PROMPT = 1, EVENT_PROGRESS = 2, EVENT_NOTIFICATION = 3, EVENT_KITTY_NOTIFICATION = 4 };
// EVENT_PROMPT's second value when the shell reported no exit code.
#define NO_EXIT_CODE INT32_MIN

// The OSC strings the sniffer keeps (99 and 66), at most this many bytes after "NN;"; a longer one
// is dropped whole, like the Java core drops an OSC past MAX_STRING_SEQUENCE_LENGTH.
#define OSC_CAP (16 * 1024)

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
    jmethodID on_event;
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
    // The URI of the cell being read, of the run it may extend and of the run written last: three
    // URI_CAP slices of one allocation, made on the first link.
    uint8_t* uri_buf;
    uint8_t* run_uri;
    uint8_t* last_uri;
    // OSC sniffer (see sniff_write): the string being read, its first three bytes, and whether it
    // is one we keep ("99;", "66;" or "9;4"). osc_body holds what follows those three bytes.
    bool osc_active, osc_skip, osc_overflow;
    uint8_t osc_head[3];
    int osc_head_len;
    uint8_t* osc_body;
    size_t osc_body_len;
    // OSC 66 text waiting for the stream to reach ground before it is written as plain text.
    uint8_t* text_buf;
    size_t text_len;
    // An OSC 9;4 report held until the sniffer has seen its string (see on_progress).
    bool progress_pending;
    int progress_state, progress_value;
    // The snapshot Java reads, from the last capture(): a sync outside a render hold, or the
    // start of one. While held, syncs leave it and the render state as the hold found them.
    bool held;
    int snap_screen;
    size_t snap_history;
    long snap_scrolled;  // rows that left the screen since the last sync handed them to Java
    uint16_t snap_cx, snap_cy;
    bool snap_visible;
    struct KittyState* kitty;  // kitty graphics, set up by nativeKittySetup; see the end of this file
} Core;

static void kitty_free(Core* c);
static void kitty_write(Core* c, const uint8_t* data, size_t len);

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

static jbyteArray byte_array(JNIEnv* env, const uint8_t* data, size_t len) {
    if (len > (1u << 24)) len = 0;
    jbyteArray arr = (*env)->NewByteArray(env, (jsize) len);
    if (arr == NULL) { clear_exception(env); return NULL; }
    if (len > 0) (*env)->SetByteArrayRegion(env, arr, 0, (jsize) len, (const jbyte*) data);
    return arr;
}

// Hand one event to Java, which only records it (GhosttyCore.onEvent) and acts after the write.
static void send_event(Core* c, int kind, int a, int b, const uint8_t* first, size_t first_len,
                       const uint8_t* second, size_t second_len) {
    if (c == NULL || c->env == NULL || c->sink == NULL) return;
    JNIEnv* env = c->env;
    jbyteArray one = first != NULL ? byte_array(env, first, first_len) : NULL;
    jbyteArray two = second != NULL ? byte_array(env, second, second_len) : NULL;
    (*env)->CallVoidMethod(env, c->sink, c->on_event, (jint) kind, (jint) a, (jint) b, one, two);
    clear_exception(env);
    if (one != NULL) (*env)->DeleteLocalRef(env, one);
    if (two != NULL) (*env)->DeleteLocalRef(env, two);
}

// OSC 133: the step and, on command end, the exit code. Prompt rows are read from the rows.
static void on_semantic_prompt(GhosttyTerminal t, void* ud, const GhosttyTerminalSemanticPrompt* e) {
    (void) t;
    if (e == NULL) return;
    int code = e->kind == GHOSTTY_SEMANTIC_PROMPT_COMMAND_END && e->has_exit_code ? (int) e->exit_code : NO_EXIT_CODE;
    send_event(ud, EVENT_PROMPT, (int) e->kind, code, NULL, 0, NULL, 0);
}

// OSC 9;4: state 0-4 as the Java core numbers them, percent -1 when omitted. The library reads
// "9;4;1" without a percentage as 0 where the Java core keeps the last value, so the report waits
// for the sniffer, which sees the string end right after this callback (osc_end), and goes out
// from there.
static void on_progress(GhosttyTerminal t, void* ud, const GhosttyTerminalProgressReport* r) {
    (void) t;
    Core* c = ud;
    if (c == NULL || r == NULL) return;
    c->progress_pending = true;
    c->progress_state = (int) r->state;
    c->progress_value = (int) r->progress;
}

static void send_progress(Core* c, bool percent_omitted) {
    if (!c->progress_pending) return;
    c->progress_pending = false;
    send_event(c, EVENT_PROGRESS, c->progress_state, percent_omitted ? -1 : c->progress_value, NULL, 0, NULL, 0);
}

// OSC 9 and OSC 777: an empty title means the protocol had none.
static void on_desktop_notification(GhosttyTerminal t, void* ud, const GhosttyTerminalDesktopNotification* n) {
    (void) t;
    if (n == NULL) return;
    send_event(ud, EVENT_NOTIFICATION, 0, 0, n->title.ptr != NULL ? n->title.ptr : (const uint8_t*) "",
               n->title.len, n->body.ptr != NULL ? n->body.ptr : (const uint8_t*) "", n->body.len);
}

static void core_free(Core* c, JNIEnv* env) {
    if (c == NULL) return;
    kitty_free(c);
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
    free(c->uri_buf);
    free(c->osc_body);
    free(c->text_buf);
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
    c->on_event = (*env)->GetMethodID(env, sink_class, "onEvent", "(III[B[B)V");
    if (c->on_reply == NULL || c->on_clipboard == NULL || c->on_event == NULL) { clear_exception(env); free(c); return 0; }
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

    // Grapheme clustering (mode 2027) on from the start, as the Ghostty app does: without it every
    // code point of an emoji sequence, flag or skin tone takes its own cell. As the default it is
    // also what a reset restores; a program can still turn it off with CSI ? 2027 l.
    GhosttyTerminalModeConfig clustering = {.mode = GHOSTTY_MODE_GRAPHEME_CLUSTER, .value = true};
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_MODE_DEFAULT, &clustering);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_USERDATA, c);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_WRITE_PTY, (const void*) on_write_pty);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_BELL, (const void*) on_bell);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_TITLE_CHANGED, (const void*) on_title);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_PWD_CHANGED, (const void*) on_pwd);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_XTVERSION, (const void*) on_xtversion);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_SIZE, (const void*) on_size);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_CLIPBOARD_WRITE, (const void*) on_clipboard_write);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_RENDER_HOLD, (const void*) on_render_hold);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_SEMANTIC_PROMPT, (const void*) on_semantic_prompt);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_PROGRESS_REPORT, (const void*) on_progress);
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_DESKTOP_NOTIFICATION, (const void*) on_desktop_notification);
    // History only has to hold what the next sync copies into the Java transcript; Java picks the
    // row limit (GhosttyCore.historyLimit). Bytes are unlimited so a styled burst cannot prune rows
    // the next sync still needs.
    size_t no_byte_limit = SIZE_MAX;
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_SCROLLBACK_MAX_BYTES, &no_byte_limit);
    set_history_limit(c, scrollback_lines);
    // Images stay off until GhosttyCore turns them on with its limits (nativeKittySetup).
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

// The OSC sniffer. libghostty-vt parses and drops two OSC strings the Java core acts on: OSC 99
// (kitty notifications), handed to Java whole, and OSC 66 (text sizing), whose text is written back
// as plain text so a program that did not probe loses the size but not the words. It also reads
// OSC 9;4 to tell "9;4;1" from "9;4;1;0" (see on_progress). Every byte still goes through the
// library, in order, through vt_write_until stopping at OSC boundaries: the
// library's own parser says where a string starts and ends, so a string split across writes, a
// UTF-8 sequence split across writes and a CAN or SUB inside a string behave exactly as one
// vt_write would. Only the kept strings' bytes are copied, and at most OSC_CAP of each.

static void osc_begin(Core* c) {
    c->osc_active = true;
    c->osc_skip = false;
    c->osc_overflow = false;
    c->osc_head_len = 0;
    c->osc_body_len = 0;
}

static void osc_append(Core* c, const uint8_t* p, size_t n) {
    if (!c->osc_active || c->osc_skip) return;
    while (n > 0 && c->osc_head_len < 3) {
        c->osc_head[c->osc_head_len++] = *p++;
        n--;
        if (c->osc_head_len == 3 && memcmp(c->osc_head, "99;", 3) != 0 && memcmp(c->osc_head, "66;", 3) != 0
            && memcmp(c->osc_head, "9;4", 3) != 0) {
            c->osc_skip = true;
            return;
        }
    }
    if (n == 0 || c->osc_overflow) return;
    if (c->osc_body == NULL && (c->osc_body = malloc(OSC_CAP)) == NULL) { c->osc_skip = true; return; }
    if (n > OSC_CAP - c->osc_body_len) { c->osc_overflow = true; return; }
    memcpy(c->osc_body + c->osc_body_len, p, n);
    c->osc_body_len += n;
}

// OSC 66 is "66 ; metadata ; text": queue the text, without control bytes, for the next ground.
// Text that is only spaces is not written: it carries no words, and it is what a probe sends
// (CR, OSC 66 w=2 with one space, then a cursor report), so the probe sees the cursor stay put, as
// on a terminal without text sizing.
static void queue_sized_text(Core* c) {
    const uint8_t* text = memchr(c->osc_body, ';', c->osc_body_len);
    if (text == NULL) return;
    text++;
    size_t len = c->osc_body_len - (size_t) (text - c->osc_body);
    bool words = false;
    for (size_t i = 0; i < len && !words; i++) words = text[i] > ' ' && text[i] != 0x7f;
    if (!words) return;
    if (c->text_buf == NULL && (c->text_buf = malloc(OSC_CAP)) == NULL) return;
    for (size_t i = 0; i < len && c->text_len < OSC_CAP; i++) {
        if (text[i] < ' ' || text[i] == 0x7f) continue;
        c->text_buf[c->text_len++] = text[i];
    }
}

static void osc_end(Core* c, GhosttyTerminalStringEnd end) {
    bool kept = c->osc_active && !c->osc_skip && !c->osc_overflow && c->osc_head_len == 3
        && end != GHOSTTY_TERMINAL_STRING_END_CANCELLED;
    c->osc_active = false;
    if (!kept) {
        send_progress(c, false);
        return;
    }
    if (memcmp(c->osc_head, "9;4", 3) == 0)
        send_progress(c, c->osc_body_len == 2 && memcmp(c->osc_body, ";1", 2) == 0);
    else if (memcmp(c->osc_head, "99;", 3) == 0)
        send_event(c, EVENT_KITTY_NOTIFICATION, 0, 0, c->osc_body != NULL ? c->osc_body : (const uint8_t*) "",
                   c->osc_body_len, NULL, 0);
    else if (c->osc_body_len > 0)
        queue_sized_text(c);
}

static void write_sized_text(Core* c) {
    size_t n = c->text_len;
    c->text_len = 0;
    ghostty_terminal_vt_write(c->term, c->text_buf, n);
}

// Writes data through the sniffer and returns the bytes consumed: all of them, or fewer when the
// write reached a boundary in stop_at, which *out then reports.
static size_t sniff_write(Core* c, const uint8_t* data, size_t len, uint32_t stop_at, GhosttyTerminalWriteStop* out) {
    size_t off = 0;
    for (;;) {
        if (off >= len) {
            send_progress(c, false);
            // A string that ended on the last byte: its text goes in now if the stream is at ground
            // (BEL), or after the next write's first byte completes ESC \.
            if (c->text_len == 0) return off;
            GhosttyTerminalWriteStop at = GHOSTTY_INIT_SIZED(GhosttyTerminalWriteStop);
            if (ghostty_terminal_vt_write_until(c->term, data, 0, GHOSTTY_TERMINAL_BOUNDARY_GROUND, &at) == GHOSTTY_SUCCESS
                && (at.reached & GHOSTTY_TERMINAL_BOUNDARY_GROUND))
                write_sized_text(c);
            return off;
        }
        uint32_t want = GHOSTTY_TERMINAL_BOUNDARY_OSC_START | GHOSTTY_TERMINAL_BOUNDARY_OSC_END | stop_at;
        if (c->text_len > 0) want |= GHOSTTY_TERMINAL_BOUNDARY_GROUND;
        GhosttyTerminalWriteStop stop = GHOSTTY_INIT_SIZED(GhosttyTerminalWriteStop);
        GhosttyResult r = ghostty_terminal_vt_write_until(c->term, data + off, len - off, want, &stop);
        if (r == GHOSTTY_NO_VALUE) {
            osc_append(c, data + off, len - off);
            off = len;
            continue;
        }
        if (r != GHOSTTY_SUCCESS) {
            // Never lose output: hand the rest over unsniffed.
            ghostty_terminal_vt_write(c->term, data + off, len - off);
            send_progress(c, false);
            return len;
        }
        size_t used = stop.consumed;
        if (stop.reached & GHOSTTY_TERMINAL_BOUNDARY_OSC_END) {
            // The ending byte (BEL, ESC of ESC \, CAN or SUB) is not content.
            if (used > 0) osc_append(c, data + off, used - 1);
            osc_end(c, stop.end);
        } else {
            osc_append(c, data + off, used);
        }
        if (stop.reached & GHOSTTY_TERMINAL_BOUNDARY_OSC_START) osc_begin(c);
        off += used;
        if ((stop.reached & GHOSTTY_TERMINAL_BOUNDARY_GROUND) && c->text_len > 0) write_sized_text(c);
        if ((stop.reached & stop_at) && out != NULL) {
            *out = stop;
            if (off < len) return off;
        }
    }
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
    kitty_write(c, (const uint8_t*) bytes + offset, (size_t) length);
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

// Where one sync or row read puts its link runs (see LINK_*); count includes runs already written.
typedef struct {
    jintArray runs;
    jbyteArray uris;
    int run_cap, uri_cap;
    int count, uri_used;
    int last_offset, last_len;  // the URI written last, which the next run often repeats
} LinkOut;

static void link_out_init(JNIEnv* env, LinkOut* out, jintArray runs, jbyteArray uris) {
    memset(out, 0, sizeof(*out));
    out->last_offset = -1;
    if (runs == NULL || uris == NULL) return;
    out->runs = runs;
    out->uris = uris;
    out->run_cap = ((*env)->GetArrayLength(env, runs) - 1) / LINK_INTS;
    out->uri_cap = (*env)->GetArrayLength(env, uris);
    if (out->run_cap < 0) out->run_cap = 0;
}

static void link_out_finish(JNIEnv* env, LinkOut* out) {
    if (out->runs == NULL || (*env)->GetArrayLength(env, out->runs) < 1) return;
    jint count = out->count;
    (*env)->SetIntArrayRegion(env, out->runs, 0, 1, &count);
}

// One run of cells [x0, x1) of row y sharing the URI in run_uri. A run whose URI is the one written
// last (a link that wraps onto the next row) shares its bytes.
static void emit_link(JNIEnv* env, Core* c, LinkOut* out, int y, int x0, int x1, size_t len) {
    if (out->count >= out->run_cap) return;
    int offset = -1;
    if (out->last_offset >= 0 && (size_t) out->last_len == len
        && memcmp(c->last_uri, c->run_uri, len) == 0) {
        offset = out->last_offset;
    } else {
        if ((size_t) out->uri_cap - (size_t) out->uri_used < len) return;
        offset = out->uri_used;
        (*env)->SetByteArrayRegion(env, out->uris, offset, (jsize) len, (const jbyte*) c->run_uri);
        out->uri_used += (int) len;
        out->last_offset = offset;
        out->last_len = (int) len;
        memcpy(c->last_uri, c->run_uri, len);
    }
    jint run[LINK_INTS] = {y, x0, x1, offset, (jint) len};
    (*env)->SetIntArrayRegion(env, out->runs, 1 + out->count * LINK_INTS, LINK_INTS, run);
    out->count++;
}

// The OSC 8 links of one row, as runs of cells sharing a URI. row_ref is the row's first cell.
static void read_links(JNIEnv* env, Core* c, LinkOut* out, const GhosttyGridRef* row_ref, int width, int y) {
    if (out->runs == NULL) return;
    if (c->uri_buf == NULL) c->uri_buf = malloc(3 * URI_CAP);
    if (c->uri_buf == NULL) return;
    c->run_uri = c->uri_buf + URI_CAP;
    c->last_uri = c->uri_buf + 2 * URI_CAP;
    int start = -1;
    size_t run_len = 0;
    for (int x = 0; x <= width; x++) {
        size_t n = 0;
        if (x < width) {
            GhosttyGridRef ref = *row_ref;
            ref.x = (uint16_t) x;
            GhosttyCell raw = 0;
            bool has = false;
            if (ghostty_grid_ref_cell(&ref, &raw) == GHOSTTY_SUCCESS)
                ghostty_cell_get(raw, GHOSTTY_CELL_DATA_HAS_HYPERLINK, &has);
            if (has && ghostty_grid_ref_hyperlink_uri(&ref, c->uri_buf, URI_CAP, &n) != GHOSTTY_SUCCESS) n = 0;
        }
        if (start >= 0 && n == run_len && memcmp(c->uri_buf, c->run_uri, n) == 0) continue;
        if (start >= 0) emit_link(env, c, out, y, start, x, run_len);
        start = -1;
        if (n > 0) {
            memcpy(c->run_uri, c->uri_buf, n);
            run_len = n;
            start = x;
        }
    }
}

// Whether a row is the first line of a shell prompt (OSC 133 A), as ROW_PROMPT.
static int prompt_bit(GhosttyRow row) {
    GhosttyRowSemanticPrompt semantic = GHOSTTY_ROW_SEMANTIC_NONE;
    ghostty_row_get(row, GHOSTTY_ROW_DATA_SEMANTIC_PROMPT, &semantic);
    return semantic == GHOSTTY_ROW_SEMANTIC_PROMPT ? ROW_PROMPT : 0;
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
// extra[], with dirty[y] = number of extras for the row (| ROW_WRAPPED | ROW_PROMPT), or -1 when it
// did not change. The changed rows' links go to links/uris (see LINK_*).
JNIEXPORT jboolean JNICALL Java_com_termux_terminal_GhosttyNative_nativeSync(
    JNIEnv* env, jclass cls, jlong h, jintArray state_out, jintArray screen_out, jintArray dirty_out,
    jintArray extra_out, jintArray links_out, jbyteArray uris_out) {
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
    GhosttyMouseShape mouse = GHOSTTY_MOUSE_SHAPE_TEXT;
    ghostty_terminal_get(c->term, GHOSTTY_TERMINAL_DATA_MOUSE_SHAPE, &mouse);

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
    st[ST_MOUSE_SHAPE] = (jint) mouse;
    (*env)->SetIntArrayRegion(env, state_out, 0, ST_COUNT, st);

    // Rows: copy each changed row into the reusable row buffer, then into the Java arrays.
    jint* row_cells = c->row_buf;
    uint32_t* extra = c->extra_buf;
    jint* dirty_rows = c->dirty_buf;
    for (int y = 0; y < rows; y++) dirty_rows[y] = -1;
    int extra_used = 0;
    LinkOut links;
    link_out_init(env, &links, links_out, uris_out);
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
                    bool wrap = false, linked = false;
                    int prompt = 0;
                    if (ghostty_render_state_row_get(c->row_iter, GHOSTTY_RENDER_STATE_ROW_DATA_RAW, &raw_row) == GHOSTTY_SUCCESS) {
                        ghostty_row_get(raw_row, GHOSTTY_ROW_DATA_WRAP, &wrap);
                        ghostty_row_get(raw_row, GHOSTTY_ROW_DATA_HYPERLINK, &linked);
                        prompt = prompt_bit(raw_row);
                    }
                    dirty_rows[y] = n | (wrap ? ROW_WRAPPED : 0) | prompt;
                    extra_used += n;
                    if (linked) {
                        // The viewport is never scrolled, so render row y is active row y.
                        GhosttyGridRef row_ref = {0};
                        GhosttyPoint p = {.tag = GHOSTTY_POINT_TAG_ACTIVE, .value = {.coordinate = {.x = 0, .y = (uint32_t) y}}};
                        if (ghostty_terminal_grid_ref(c->term, p, &row_ref) == GHOSTTY_SUCCESS)
                            read_links(env, c, &links, &row_ref, cols, y);
                    }
                }
                bool clean = false;
                ghostty_render_state_row_set(c->row_iter, GHOSTTY_RENDER_STATE_ROW_OPT_DIRTY, &clean);
            }
            y++;
        }
    }
    (*env)->SetIntArrayRegion(env, dirty_out, 0, rows, dirty_rows);
    if (extra_used > 0) (*env)->SetIntArrayRegion(env, extra_out, 0, extra_used, (const jint*) extra);
    link_out_finish(env, &links);
    GhosttyRenderStateDirty clean_state = GHOSTTY_RENDER_STATE_DIRTY_FALSE;
    ghostty_render_state_set(c->render, GHOSTTY_RENDER_STATE_OPT_DIRTY, &clean_state);
    return JNI_TRUE;
}

// Read one history row (0 = oldest) cell by cell into cells/extra, when a reader asks for it (Java
// keeps no copy of history). Only valid between a sync and the next write. Returns the number of
// extra code points (| ROW_WRAPPED | ROW_PROMPT), or -1 if the row is gone. Its links go to
// links/uris (see LINK_*), as row 0.
JNIEXPORT jint JNICALL Java_com_termux_terminal_GhosttyNative_nativeReadHistoryRow(
    JNIEnv* env, jclass cls, jlong h, jint y, jintArray cells_out, jintArray extra_out, jintArray links_out,
    jbyteArray uris_out) {
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
    bool wrap = false, linked = false;
    int prompt = 0;
    LinkOut links;
    link_out_init(env, &links, links_out, uris_out);
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
            if (ghostty_grid_ref_row(&ref, &r) == GHOSTTY_SUCCESS) {
                ghostty_row_get(r, GHOSTTY_ROW_DATA_WRAP, &wrap);
                ghostty_row_get(r, GHOSTTY_ROW_DATA_HYPERLINK, &linked);
                prompt = prompt_bit(r);
            }
        }
    }
    (*env)->SetIntArrayRegion(env, cells_out, 0, cols * CELL_INTS, row);
    if (used > 0) (*env)->SetIntArrayRegion(env, extra_out, 0, used, (const jint*) extra);
    if (linked) read_links(env, c, &links, &row_ref, width, 0);
    link_out_finish(env, &links);
    return used | (wrap ? ROW_WRAPPED : 0) | prompt;
}

// The first history row from `from` towards `to` (both inclusive, 0 = oldest kept) that starts a
// shell prompt, or -1. Reads only each row's prompt flag, not its cells, so a jump through a long
// history costs one row lookup per row. Valid between a sync and the next write.
JNIEXPORT jint JNICALL Java_com_termux_terminal_GhosttyNative_nativeFindPromptRow(
    JNIEnv* env, jclass cls, jlong h, jint from, jint to) {
    (void) env;
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || from < 0 || to < 0) return -1;
    int step = to >= from ? 1 : -1;
    for (jint y = from;; y += step) {
        GhosttyGridRef ref = {0};
        GhosttyPoint p = {.tag = GHOSTTY_POINT_TAG_HISTORY, .value = {.coordinate = {.x = 0, .y = (uint32_t) y}}};
        GhosttyRow r = 0;
        if (ghostty_terminal_grid_ref(c->term, p, &ref) == GHOSTTY_SUCCESS
            && ghostty_grid_ref_row(&ref, &r) == GHOSTTY_SUCCESS && prompt_bit(r) != 0)
            return y;
        if (y == to) return -1;
    }
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

// ---------------------------------------------------------------------------------------------
// Kitty graphics. Ghostty stores, decodes, places and animates images; Java keeps bitmaps only for
// the images on screen (GhosttyImages, GhosttyImageCache). Like the rest of this file, everything
// runs on the session's thread between writes, and an image handle or pixel pointer is looked up
// and copied out within the one entry point that uses it (guides/images.md, Lifetime).

// The PNG decoder is process-wide and not thread-safe, so it goes in when the library loads,
// before any terminal exists.
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void) vm;
    (void) reserved;
    ghostty_sys_set(GHOSTTY_SYS_OPT_DECODE_PNG, (const void*) ghostty_png_decode);
    return JNI_VERSION_1_6;
}

// The longest control header (the part of a kitty APC before ';') the query watch reads.
#define KQ_HEADER_MAX 256
// The largest query the Java core answers; a longer one is left to Ghostty.
#define KQ_QUERY_MAX (64 * 1024)
enum { KQ_GROUND, KQ_HEADER, KQ_QUERY };

// Ints per placement in nativeKittyPlacements; mirrored by GhosttyNative.KP_*.
enum {
    KP_IMAGE_ID = 0, KP_KIND, KP_LAYER, KP_Z, KP_COL, KP_ROW, KP_GRID_COLS, KP_GRID_ROWS,
    KP_OFFSET_X, KP_OFFSET_Y, KP_WIDTH, KP_HEIGHT, KP_SRC_X, KP_SRC_Y, KP_SRC_W, KP_SRC_H,
    KP_PLACEMENT_ID, KP_INTS,
};

// Ints per virtual placement in nativeKittyVirtualPlacements; mirrored by GhosttyNative.KV_*.
enum { KV_IMAGE_ID = 0, KV_PLACEMENT_ID, KV_COLUMNS, KV_ROWS, KV_SRC_X, KV_SRC_Y, KV_SRC_W, KV_SRC_H, KV_INTS };

typedef struct KittyState {
    GhosttyRenderStateImageIterator images;
    GhosttyKittyGraphicsPlacementIterator placements;
    jmethodID on_query;
    // The query watch: a kitty query (a=q) is answered by the Java core's own code, so both cores
    // give the same bytes, including for the media and formats this one refuses.
    int watch;
    uint8_t* held;  // the current APC's content, held back from Ghostty until we know what it is
    size_t held_len, held_cap;
} KittyState;

static void kitty_free(Core* c) {
    KittyState* k = c->kitty;
    if (k == NULL) return;
    if (k->images) ghostty_render_state_image_iterator_free(k->images);
    if (k->placements) ghostty_kitty_graphics_placement_iterator_free(k->placements);
    free(k->held);
    free(k);
    c->kitty = NULL;
}

static bool kq_hold(KittyState* k, uint8_t b) {
    if (k->held_len == k->held_cap) {
        size_t cap = k->held_cap == 0 ? 64 : k->held_cap * 2;
        if (cap > KQ_QUERY_MAX) cap = KQ_QUERY_MAX;
        if (cap <= k->held_len) return false;
        uint8_t* grown = realloc(k->held, cap);
        if (grown == NULL) return false;
        k->held = grown;
        k->held_cap = cap;
    }
    k->held[k->held_len++] = b;
    return true;
}

// Give Ghostty what was held back; it carries on with the APC as if nothing had stopped it.
static void kq_release(Core* c) {
    KittyState* k = c->kitty;
    if (k->held_len > 0) ghostty_terminal_vt_write(c->term, k->held, k->held_len);
    k->held_len = 0;
    k->watch = KQ_GROUND;
}

// Whether a held control header ("G" then key=value pairs) is a kitty query.
static bool kq_is_query(const uint8_t* h, size_t n) {
    if (n < 1 || h[0] != 'G') return false;
    size_t i = 1;
    while (i < n) {
        size_t end = i;
        while (end < n && h[end] != ',') end++;
        if (end - i == 3 && h[i] == 'a' && h[i + 1] == '=' && h[i + 2] == 'q') return true;
        i = end + 1;
    }
    return false;
}

static void kq_answer(Core* c) {
    KittyState* k = c->kitty;
    JNIEnv* env = c->env;
    if (env != NULL && c->sink != NULL && k->held_len > 0) {
        jbyteArray arr = (*env)->NewByteArray(env, (jsize) k->held_len);
        if (arr == NULL) {
            clear_exception(env);
        } else {
            (*env)->SetByteArrayRegion(env, arr, 0, (jsize) k->held_len, (const jbyte*) k->held);
            (*env)->CallVoidMethod(env, c->sink, k->on_query, arr);
            clear_exception(env);
            (*env)->DeleteLocalRef(env, arr);
        }
    }
    k->held_len = 0;
}

// vt_write with the query watch in front. Ghostty stops at each APC introducer; the APC's control
// header is then held back until it shows whether this is a query. Anything else is released to
// Ghostty unchanged. A query is kept from Ghostty entirely: it gets only the terminator, an empty
// APC it ignores, and the Java core answers (through GhosttyCore.onKittyQuery, called here between
// writes, never from a library callback). The header can span writes; what is held stays here.
static void kitty_write(Core* c, const uint8_t* data, size_t len) {
    KittyState* k = c->kitty;
    if (k == NULL || k->on_query == NULL) {
        sniff_write(c, data, len, 0, NULL);
        return;
    }
    size_t i = 0;
    while (i < len) {
        if (k->watch == KQ_GROUND) {
            // The OSC sniffer writes up to the next APC start (merge interim: nested in this loop).
            GhosttyTerminalWriteStop stop = GHOSTTY_INIT_SIZED(GhosttyTerminalWriteStop);
            i += sniff_write(c, data + i, len - i, GHOSTTY_TERMINAL_BOUNDARY_APC_START, &stop);
            if ((stop.reached & GHOSTTY_TERMINAL_BOUNDARY_APC_START) && stop.kind == GHOSTTY_TERMINAL_STRING_KIND_APC) {
                k->watch = KQ_HEADER;
                k->held_len = 0;
            }
            continue;
        }
        uint8_t b = data[i];
        bool content = b >= 0x20 && b < 0x7f;
        if (k->watch == KQ_HEADER) {
            if (content && b != ';' && k->held_len < KQ_HEADER_MAX) {
                if (kq_hold(k, b)) i++; else kq_release(c);
                continue;
            }
            if ((b == ';' || b == 0x1b || b == 0x9c) && kq_is_query(k->held, k->held_len)) {
                k->watch = KQ_QUERY;
                if (b == ';') {
                    if (kq_hold(k, b)) i++; else kq_release(c);
                }
                continue;
            }
            kq_release(c);
            continue;
        }
        // KQ_QUERY: hold the payload up to the terminator.
        if (content) {
            if (k->held_len < KQ_QUERY_MAX && kq_hold(k, b)) i++; else kq_release(c);
            continue;
        }
        if (b == 0x1b || b == 0x9c) {
            kq_answer(c);
            k->watch = KQ_GROUND;
        } else {
            kq_release(c);  // CAN, SUB or another control byte: Ghostty decides what it means
        }
    }
}

// Turn images on: Ghostty's store per screen is storage_limit bytes (KittyImageStore's, so both
// cores keep the same), direct and file media as the Java core takes them, temp files from
// temp_dir (UTF-8, may be null), and no shared memory, which Android does not have.
JNIEXPORT jboolean JNICALL Java_com_termux_terminal_GhosttyNative_nativeKittySetup(
    JNIEnv* env, jclass cls, jlong h, jlong storage_limit, jbyteArray temp_dir) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL) return JNI_FALSE;
    if (c->kitty == NULL) {
        KittyState* k = calloc(1, sizeof(KittyState));
        if (k == NULL) return JNI_FALSE;
        c->kitty = k;
        if (ghostty_render_state_image_iterator_new(NULL, &k->images) != GHOSTTY_SUCCESS
            || ghostty_kitty_graphics_placement_iterator_new(NULL, &k->placements) != GHOSTTY_SUCCESS) {
            kitty_free(c);
            return JNI_FALSE;
        }
        jclass sink_class = (*env)->GetObjectClass(env, c->sink);
        k->on_query = (*env)->GetMethodID(env, sink_class, "onKittyQuery", "([B)V");
        if (k->on_query == NULL) clear_exception(env);
    }
    uint64_t limit = storage_limit > 0 ? (uint64_t) storage_limit : 0;
    if (ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_KITTY_IMAGE_STORAGE_LIMIT, &limit) != GHOSTTY_SUCCESS)
        return JNI_FALSE;
    bool on = true;
    ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_KITTY_IMAGE_MEDIUM_FILE, &on);
    if (temp_dir != NULL) {
        jsize n = (*env)->GetArrayLength(env, temp_dir);
        uint8_t dir[1024];
        if (n > 0 && n <= (jsize) sizeof(dir)) {
            (*env)->GetByteArrayRegion(env, temp_dir, 0, n, (jbyte*) dir);
            GhosttyString s = {.ptr = dir, .len = (size_t) n};
            ghostty_terminal_set(c->term, GHOSTTY_TERMINAL_OPT_KITTY_IMAGE_MEDIUM_TEMP_FILE, &s);
        }
    }
    return JNI_TRUE;
}

static GhosttyKittyGraphics kitty_graphics(Core* c) {
    GhosttyKittyGraphics g = NULL;
    if (ghostty_terminal_get(c->term, GHOSTTY_TERMINAL_DATA_KITTY_GRAPHICS, &g) != GHOSTTY_SUCCESS) return NULL;
    return g;
}

// Once per drawn frame: out[0] the render state's placements generation (from the last sync),
// out[1] when the next animation frame is due (ms on the caller's clock) or -1, out[2] the active
// storage's generation. Animations advance only when tick is set.
JNIEXPORT jboolean JNICALL Java_com_termux_terminal_GhosttyNative_nativeKittyFrame(
    JNIEnv* env, jclass cls, jlong h, jlong now_ms, jboolean tick, jlongArray out) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || out == NULL || (*env)->GetArrayLength(env, out) < 3) return JNI_FALSE;
    jlong v[3] = {0, -1, 0};
    uint64_t placements = 0;
    if (ghostty_render_state_get(c->render, GHOSTTY_RENDER_STATE_DATA_IMAGE_PLACEMENTS_GENERATION, &placements) == GHOSTTY_SUCCESS)
        v[0] = (jlong) placements;
    GhosttyKittyGraphics g = kitty_graphics(c);
    if (g != NULL) {
        uint64_t next = 0;
        if (tick && now_ms >= 0 && ghostty_kitty_graphics_animation_tick(g, (uint64_t) now_ms, &next) == GHOSTTY_SUCCESS)
            v[1] = (jlong) next;
        uint64_t storage = 0;
        if (ghostty_kitty_graphics_get(g, GHOSTTY_KITTY_GRAPHICS_DATA_GENERATION, &storage) == GHOSTTY_SUCCESS)
            v[2] = (jlong) storage;
    }
    (*env)->SetLongArrayRegion(env, out, 0, 3, v);
    return JNI_TRUE;
}

// The render state's image list from the last sync, KP_INTS ints per Kitty placement while out has
// room. Returns how many there are, so a caller with too small an array can grow it and ask again.
JNIEXPORT jint JNICALL Java_com_termux_terminal_GhosttyNative_nativeKittyPlacements(
    JNIEnv* env, jclass cls, jlong h, jintArray out) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || c->kitty == NULL || out == NULL) return 0;
    KittyState* k = c->kitty;
    if (ghostty_render_state_get(c->render, GHOSTTY_RENDER_STATE_DATA_IMAGE_ITERATOR, &k->images) != GHOSTTY_SUCCESS)
        return 0;
    jsize cap = (*env)->GetArrayLength(env, out) / KP_INTS;
    jint n = 0;
    while (ghostty_render_state_image_iterator_next(k->images)) {
        GhosttyImageProtocol protocol = GHOSTTY_IMAGE_PROTOCOL_KITTY;
        GhosttyImageLayer layer = GHOSTTY_IMAGE_LAYER_ABOVE_TEXT;
        GhosttyRenderStateKittyPlacementKind kind = GHOSTTY_RENDER_STATE_KITTY_PLACEMENT_KIND_FIXED;
        uint32_t image_id = 0, grid_cols = 0, grid_rows = 0, off_x = 0, off_y = 0, width = 0, height = 0;
        uint32_t src_x = 0, src_y = 0, src_w = 0, src_h = 0, placement_id = 0;
        int32_t z = 0, col = 0, row = 0;
        GhosttyRenderStateImageData keys[] = {
            GHOSTTY_RENDER_STATE_IMAGE_DATA_PROTOCOL, GHOSTTY_RENDER_STATE_IMAGE_DATA_IMAGE_ID,
            GHOSTTY_RENDER_STATE_IMAGE_DATA_LAYER, GHOSTTY_RENDER_STATE_IMAGE_DATA_Z,
            GHOSTTY_RENDER_STATE_IMAGE_DATA_VIEWPORT_COL, GHOSTTY_RENDER_STATE_IMAGE_DATA_VIEWPORT_ROW,
            GHOSTTY_RENDER_STATE_IMAGE_DATA_GRID_COLS, GHOSTTY_RENDER_STATE_IMAGE_DATA_GRID_ROWS,
            GHOSTTY_RENDER_STATE_IMAGE_DATA_CELL_OFFSET_X, GHOSTTY_RENDER_STATE_IMAGE_DATA_CELL_OFFSET_Y,
            GHOSTTY_RENDER_STATE_IMAGE_DATA_PIXEL_WIDTH, GHOSTTY_RENDER_STATE_IMAGE_DATA_PIXEL_HEIGHT,
            GHOSTTY_RENDER_STATE_IMAGE_DATA_SOURCE_X, GHOSTTY_RENDER_STATE_IMAGE_DATA_SOURCE_Y,
            GHOSTTY_RENDER_STATE_IMAGE_DATA_SOURCE_WIDTH, GHOSTTY_RENDER_STATE_IMAGE_DATA_SOURCE_HEIGHT,
        };
        void* values[] = {
            &protocol, &image_id, &layer, &z, &col, &row, &grid_cols, &grid_rows, &off_x, &off_y,
            &width, &height, &src_x, &src_y, &src_w, &src_h,
        };
        if (ghostty_render_state_image_get_multi(k->images, sizeof(keys) / sizeof(keys[0]), keys, values, NULL) != GHOSTTY_SUCCESS
            || protocol != GHOSTTY_IMAGE_PROTOCOL_KITTY) continue;
        ghostty_render_state_image_get(k->images, GHOSTTY_RENDER_STATE_IMAGE_DATA_KITTY_KIND, &kind);
        ghostty_render_state_image_get(k->images, GHOSTTY_RENDER_STATE_IMAGE_DATA_KITTY_PLACEMENT_ID, &placement_id);
        if (n < cap) {
            jint p[KP_INTS];
            p[KP_IMAGE_ID] = (jint) image_id;
            p[KP_KIND] = (jint) kind;
            p[KP_LAYER] = (jint) layer;
            p[KP_Z] = z;
            p[KP_COL] = col;
            p[KP_ROW] = row;
            p[KP_GRID_COLS] = (jint) grid_cols;
            p[KP_GRID_ROWS] = (jint) grid_rows;
            p[KP_OFFSET_X] = (jint) off_x;
            p[KP_OFFSET_Y] = (jint) off_y;
            p[KP_WIDTH] = (jint) width;
            p[KP_HEIGHT] = (jint) height;
            p[KP_SRC_X] = (jint) src_x;
            p[KP_SRC_Y] = (jint) src_y;
            p[KP_SRC_W] = (jint) src_w;
            p[KP_SRC_H] = (jint) src_h;
            p[KP_PLACEMENT_ID] = (jint) placement_id;
            (*env)->SetIntArrayRegion(env, out, n * KP_INTS, KP_INTS, p);
        }
        n++;
    }
    return n;
}

// The active screen's virtual placements (the ones Unicode placeholder cells show), KV_INTS ints
// each while out has room. Returns how many there are.
JNIEXPORT jint JNICALL Java_com_termux_terminal_GhosttyNative_nativeKittyVirtualPlacements(
    JNIEnv* env, jclass cls, jlong h, jintArray out) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || c->kitty == NULL || out == NULL) return 0;
    KittyState* k = c->kitty;
    GhosttyKittyGraphics g = kitty_graphics(c);
    if (g == NULL || ghostty_kitty_graphics_get(g, GHOSTTY_KITTY_GRAPHICS_DATA_PLACEMENT_ITERATOR, &k->placements) != GHOSTTY_SUCCESS)
        return 0;
    jsize cap = (*env)->GetArrayLength(env, out) / KV_INTS;
    jint n = 0;
    while (ghostty_kitty_graphics_placement_next(k->placements)) {
        bool is_virtual = false;
        ghostty_kitty_graphics_placement_get(k->placements, GHOSTTY_KITTY_GRAPHICS_PLACEMENT_DATA_IS_VIRTUAL, &is_virtual);
        if (!is_virtual) continue;
        uint32_t image_id = 0, placement_id = 0, columns = 0, rows = 0, src_x = 0, src_y = 0, src_w = 0, src_h = 0;
        GhosttyKittyGraphicsPlacementData keys[] = {
            GHOSTTY_KITTY_GRAPHICS_PLACEMENT_DATA_IMAGE_ID, GHOSTTY_KITTY_GRAPHICS_PLACEMENT_DATA_PLACEMENT_ID,
            GHOSTTY_KITTY_GRAPHICS_PLACEMENT_DATA_COLUMNS, GHOSTTY_KITTY_GRAPHICS_PLACEMENT_DATA_ROWS,
            GHOSTTY_KITTY_GRAPHICS_PLACEMENT_DATA_SOURCE_X, GHOSTTY_KITTY_GRAPHICS_PLACEMENT_DATA_SOURCE_Y,
            GHOSTTY_KITTY_GRAPHICS_PLACEMENT_DATA_SOURCE_WIDTH, GHOSTTY_KITTY_GRAPHICS_PLACEMENT_DATA_SOURCE_HEIGHT,
        };
        void* values[] = {&image_id, &placement_id, &columns, &rows, &src_x, &src_y, &src_w, &src_h};
        if (ghostty_kitty_graphics_placement_get_multi(k->placements, sizeof(keys) / sizeof(keys[0]), keys, values, NULL) != GHOSTTY_SUCCESS)
            continue;
        if (n < cap) {
            jint v[KV_INTS] = {(jint) image_id, (jint) placement_id, (jint) columns, (jint) rows,
                               (jint) src_x, (jint) src_y, (jint) src_w, (jint) src_h};
            (*env)->SetIntArrayRegion(env, out, n * KV_INTS, KV_INTS, v);
        }
        n++;
    }
    return n;
}

static GhosttyKittyGraphicsImage kitty_image(Core* c, jint image_id) {
    GhosttyKittyGraphics g = kitty_graphics(c);
    return g == NULL ? NULL : ghostty_kitty_graphics_image(g, (uint32_t) image_id);
}

// An image's width, height and generation in out[0..2], when its pixels are there.
JNIEXPORT jboolean JNICALL Java_com_termux_terminal_GhosttyNative_nativeKittyImageInfo(
    JNIEnv* env, jclass cls, jlong h, jint image_id, jlongArray out) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || out == NULL || (*env)->GetArrayLength(env, out) < 3) return JNI_FALSE;
    GhosttyKittyGraphicsImage image = kitty_image(c, image_id);
    if (image == NULL) return JNI_FALSE;
    uint32_t width = 0, height = 0;
    uint64_t generation = 0;
    GhosttyString pixels = {0};
    if (ghostty_kitty_graphics_image_get(image, GHOSTTY_KITTY_GRAPHICS_IMAGE_DATA_PIXELS, &pixels) != GHOSTTY_SUCCESS
        || ghostty_kitty_graphics_image_get(image, GHOSTTY_KITTY_GRAPHICS_IMAGE_DATA_WIDTH, &width) != GHOSTTY_SUCCESS
        || ghostty_kitty_graphics_image_get(image, GHOSTTY_KITTY_GRAPHICS_IMAGE_DATA_HEIGHT, &height) != GHOSTTY_SUCCESS
        || ghostty_kitty_graphics_image_get(image, GHOSTTY_KITTY_GRAPHICS_IMAGE_DATA_GENERATION, &generation) != GHOSTTY_SUCCESS
        || width == 0 || height == 0)
        return JNI_FALSE;
    jlong v[3] = {(jlong) width, (jlong) height, (jlong) generation};
    (*env)->SetLongArrayRegion(env, out, 0, 3, v);
    return JNI_TRUE;
}

// Copy rows [first_row, first_row + rows) of an image into argb as straight (not premultiplied)
// 0xAARRGGBB, the form Bitmap.setPixels takes. Returns the rows copied, or -1 when the image is
// gone or no longer at that generation (an animation frame or a retransmission replaced it).
JNIEXPORT jint JNICALL Java_com_termux_terminal_GhosttyNative_nativeKittyImageCopy(
    JNIEnv* env, jclass cls, jlong h, jint image_id, jlong generation, jint first_row, jint rows, jintArray argb) {
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL || argb == NULL || first_row < 0 || rows <= 0) return -1;
    GhosttyKittyGraphicsImage image = kitty_image(c, image_id);
    if (image == NULL) return -1;
    uint32_t width = 0, height = 0;
    uint64_t current = 0;
    GhosttyKittyImageFormat format = GHOSTTY_KITTY_IMAGE_FORMAT_RGBA;
    GhosttyString pixels = {0};
    if (ghostty_kitty_graphics_image_get(image, GHOSTTY_KITTY_GRAPHICS_IMAGE_DATA_GENERATION, &current) != GHOSTTY_SUCCESS
        || (jlong) current != generation
        || ghostty_kitty_graphics_image_get(image, GHOSTTY_KITTY_GRAPHICS_IMAGE_DATA_WIDTH, &width) != GHOSTTY_SUCCESS
        || ghostty_kitty_graphics_image_get(image, GHOSTTY_KITTY_GRAPHICS_IMAGE_DATA_HEIGHT, &height) != GHOSTTY_SUCCESS
        || ghostty_kitty_graphics_image_get(image, GHOSTTY_KITTY_GRAPHICS_IMAGE_DATA_FORMAT, &format) != GHOSTTY_SUCCESS
        || ghostty_kitty_graphics_image_get(image, GHOSTTY_KITTY_GRAPHICS_IMAGE_DATA_PIXELS, &pixels) != GHOSTTY_SUCCESS)
        return -1;
    size_t bpp;
    switch (format) {
        case GHOSTTY_KITTY_IMAGE_FORMAT_RGB: bpp = 3; break;
        case GHOSTTY_KITTY_IMAGE_FORMAT_RGBA: bpp = 4; break;
        case GHOSTTY_KITTY_IMAGE_FORMAT_GRAY_ALPHA: bpp = 2; break;
        case GHOSTTY_KITTY_IMAGE_FORMAT_GRAY: bpp = 1; break;
        default: return -1;
    }
    if ((uint32_t) first_row >= height) return 0;
    uint32_t count = (uint32_t) rows;
    if (count > height - (uint32_t) first_row) count = height - (uint32_t) first_row;
    if ((size_t) (*env)->GetArrayLength(env, argb) < (size_t) count * width) return -1;
    if (pixels.ptr == NULL || pixels.len < (size_t) width * height * bpp) return -1;
    jint* dst = (*env)->GetPrimitiveArrayCritical(env, argb, NULL);
    if (dst == NULL) { clear_exception(env); return -1; }
    const uint8_t* src = pixels.ptr + (size_t) first_row * width * bpp;
    size_t n = (size_t) count * width;
    for (size_t i = 0; i < n; i++, src += bpp) {
        uint32_t r, g, b, a;
        switch (bpp) {
            case 4: r = src[0]; g = src[1]; b = src[2]; a = src[3]; break;
            case 3: r = src[0]; g = src[1]; b = src[2]; a = 255; break;
            case 2: r = g = b = src[0]; a = src[1]; break;
            default: r = g = b = src[0]; a = 255; break;
        }
        dst[i] = (jint) ((a << 24) | (r << 16) | (g << 8) | b);
    }
    (*env)->ReleasePrimitiveArrayCritical(env, argb, dst, 0);
    return (jint) count;
}

// An image's generation, or 0 when it is not stored or has no pixels yet.
JNIEXPORT jlong JNICALL Java_com_termux_terminal_GhosttyNative_nativeKittyImageGeneration(
    JNIEnv* env, jclass cls, jlong h, jint image_id) {
    (void) env;
    (void) cls;
    Core* c = core_of(h);
    if (c == NULL) return 0;
    GhosttyKittyGraphicsImage image = kitty_image(c, image_id);
    if (image == NULL) return 0;
    uint64_t generation = 0;
    GhosttyString pixels = {0};
    if (ghostty_kitty_graphics_image_get(image, GHOSTTY_KITTY_GRAPHICS_IMAGE_DATA_PIXELS, &pixels) != GHOSTTY_SUCCESS
        || ghostty_kitty_graphics_image_get(image, GHOSTTY_KITTY_GRAPHICS_IMAGE_DATA_GENERATION, &generation) != GHOSTTY_SUCCESS)
        return 0;
    return (jlong) generation;
}
