/*
 * termux-pwd-polyfill.h — make getpwuid() report the Termux home on a stock NDK sysroot.
 *
 * Bionic answers getpwuid() for an app uid with a synthesised entry whose pw_dir is the literal
 * string "/data" and whose pw_shell is "/bin/sh". Termux hides that from every package it builds
 * by patching pwd.h inside its own copy of the NDK sysroot (the pwd.h.patch under
 * termux-packages ndk-patches), so programs that resolve the home directory through passwd — as
 * Fastfetch does, in preference to $HOME — see the Termux home instead.
 *
 * The cross recipes here use the unpatched NDK sysroot, so a binary built without this header
 * resolves $HOME to "/data" on device: Fastfetch then looks for its config in /data/.config,
 * silently falls back to the built-in ASCII logo, and writes its image cache somewhere it cannot
 * write. Included with -include, this restores the Termux behaviour without touching the NDK.
 *
 * The macros are defined after the wrappers so the wrapper bodies still call libc.
 */

#ifndef TERMUX_LAUNCHER_PWD_POLYFILL_H
#define TERMUX_LAUNCHER_PWD_POLYFILL_H

#include <pwd.h>
#include <stddef.h>
#include <unistd.h>

#ifndef TERMUX_POLYFILL_PREFIX
#define TERMUX_POLYFILL_PREFIX "/data/data/com.termux/files/usr"
#endif
#ifndef TERMUX_POLYFILL_HOME
#define TERMUX_POLYFILL_HOME "/data/data/com.termux/files/home"
#endif

static inline void termux_polyfill_setup_pwd(struct passwd* pw) {
    if (pw == NULL) return;
    pw->pw_dir = (char*) TERMUX_POLYFILL_HOME;
    pw->pw_shell = access(TERMUX_POLYFILL_PREFIX "/bin/login", X_OK) == 0
        ? (char*) TERMUX_POLYFILL_PREFIX "/bin/login"
        : (char*) TERMUX_POLYFILL_PREFIX "/bin/bash";
    pw->pw_passwd = (char*) "*";
#ifdef __LP64__
    if (pw->pw_gecos == NULL) pw->pw_gecos = (char*) "";
#endif
}

static inline struct passwd* termux_polyfill_getpwuid(uid_t uid) {
    struct passwd* pw = getpwuid(uid);
    termux_polyfill_setup_pwd(pw);
    return pw;
}

static inline struct passwd* termux_polyfill_getpwnam(const char* name) {
    struct passwd* pw = getpwnam(name);
    termux_polyfill_setup_pwd(pw);
    return pw;
}

static inline int termux_polyfill_getpwuid_r(uid_t uid, struct passwd* pwd, char* buffer,
                                             size_t bufsize, struct passwd** result) {
    int ret = getpwuid_r(uid, pwd, buffer, bufsize, result);
    if (ret != 0) return ret;
    if (result != NULL && *result != NULL) termux_polyfill_setup_pwd(*result);
    return 0;
}

#define getpwuid termux_polyfill_getpwuid
#define getpwnam termux_polyfill_getpwnam
#define getpwuid_r termux_polyfill_getpwuid_r

#endif /* TERMUX_LAUNCHER_PWD_POLYFILL_H */
