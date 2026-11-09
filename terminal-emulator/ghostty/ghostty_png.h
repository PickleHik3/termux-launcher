// The PNG decoder installed into libghostty-vt (GHOSTTY_SYS_OPT_DECODE_PNG); see ghostty_png.c.
#ifndef TERMUX_GHOSTTY_PNG_H
#define TERMUX_GHOSTTY_PNG_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include <ghostty/vt.h>

bool ghostty_png_decode(void* userdata, const GhosttyAllocator* allocator, const uint8_t* data,
                        size_t data_len, GhosttySysImage* out);

#endif
