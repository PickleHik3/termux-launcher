// PNG decoding for kitty graphics in the Ghostty core.
//
// libghostty-vt leaves PNG to the host (GHOSTTY_SYS_OPT_DECODE_PNG) but already links Wuffs, with
// its PNG module, for its own pixel conversions. So this decoder calls the Wuffs PNG decoder that
// is already in libghostty-vt.a; the header comes from the pinned dependency tarball the build
// script unpacks, so nothing new is vendored and the only cost is the decoder's code being kept.
//
// It runs inside ghostty_terminal_vt_write on the session's thread, so it must not call into Java:
// it only reads the bytes it is given and allocates with the allocator it is given.
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <ghostty/vt.h>

// The modules libghostty-vt compiles Wuffs with (pkg/wuffs/build.zig); declarations only.
#define WUFFS_CONFIG__MODULES
#define WUFFS_CONFIG__MODULE__AUX__BASE
#define WUFFS_CONFIG__MODULE__AUX__IMAGE
#define WUFFS_CONFIG__MODULE__BASE
#define WUFFS_CONFIG__MODULE__ADLER32
#define WUFFS_CONFIG__MODULE__CRC32
#define WUFFS_CONFIG__MODULE__DEFLATE
#define WUFFS_CONFIG__MODULE__JPEG
#define WUFFS_CONFIG__MODULE__PNG
#define WUFFS_CONFIG__MODULE__ZLIB
#include "wuffs-v0.4.c"

#include "ghostty_png.h"

// Decode into straight (not premultiplied) RGBA, the layout GhosttySysImage asks for. Mirrors
// libghostty's own pkg/wuffs/src/png.zig. The pixels go in the allocator's buffer, which caps one
// image's size; the decoder and its work buffer are ours and freed here.
bool ghostty_png_decode(void* userdata, const GhosttyAllocator* allocator, const uint8_t* data,
                        size_t data_len, GhosttySysImage* out) {
    (void) userdata;
    if (allocator == NULL || data == NULL || data_len == 0 || out == NULL) return false;
    bool ok = false;
    uint8_t* pixels = NULL;
    size_t pixels_len = 0;
    uint8_t* work = NULL;
    wuffs_png__decoder* decoder = malloc(sizeof__wuffs_png__decoder());
    if (decoder == NULL) return false;
    if (wuffs_png__decoder__initialize(decoder, sizeof__wuffs_png__decoder(), WUFFS_VERSION, 0).repr != NULL)
        goto done;

    wuffs_base__io_buffer source = {
        .data = {.ptr = (uint8_t*) data, .len = data_len},
        .meta = {.wi = data_len, .ri = 0, .pos = 0, .closed = true},
    };
    wuffs_base__image_config config;
    memset(&config, 0, sizeof(config));
    if (wuffs_png__decoder__decode_image_config(decoder, &config, &source).repr != NULL) goto done;
    uint32_t width = wuffs_base__pixel_config__width(&config.pixcfg);
    uint32_t height = wuffs_base__pixel_config__height(&config.pixcfg);
    if (width == 0 || height == 0) goto done;
    wuffs_base__pixel_config__set(&config.pixcfg, WUFFS_BASE__PIXEL_FORMAT__RGBA_NONPREMUL,
                                  WUFFS_BASE__PIXEL_SUBSAMPLING__NONE, width, height);
    uint64_t size = (uint64_t) width * height * 4;
    if (size > SIZE_MAX) goto done;
    pixels_len = (size_t) size;
    // NULL when the image is over the library's per-image limit: refuse it.
    pixels = ghostty_alloc(allocator, pixels_len);
    if (pixels == NULL) goto done;

    uint64_t work_len = wuffs_png__decoder__workbuf_len(decoder).max_incl;
    if (work_len > SIZE_MAX) goto done;
    if (work_len > 0) {
        work = malloc((size_t) work_len);
        if (work == NULL) goto done;
    }
    wuffs_base__pixel_buffer buffer;
    memset(&buffer, 0, sizeof(buffer));
    if (wuffs_base__pixel_buffer__set_from_slice(&buffer, &config.pixcfg,
            wuffs_base__make_slice_u8(pixels, pixels_len)).repr != NULL) goto done;
    if (wuffs_png__decoder__decode_frame(decoder, &buffer, &source, WUFFS_BASE__PIXEL_BLEND__SRC,
            wuffs_base__make_slice_u8(work, (size_t) work_len), NULL).repr != NULL) goto done;

    out->width = width;
    out->height = height;
    out->data = pixels;
    out->data_len = pixels_len;
    pixels = NULL;
    ok = true;
done:
    if (pixels != NULL) ghostty_free(allocator, pixels, pixels_len);
    free(work);
    free(decoder);
    return ok;
}
