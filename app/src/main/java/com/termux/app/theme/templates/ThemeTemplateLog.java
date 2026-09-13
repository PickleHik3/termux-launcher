package com.termux.app.theme.templates;

/**
 * Where a skipped template or a failing hook is reported.
 *
 * <p>The loader, renderer and applier are plain Java so their rules can be tested as arithmetic;
 * they say what went wrong through this instead of reaching for {@code android.util.Log}. Production
 * hands them {@link ThemeTemplates}' logger.
 */
public interface ThemeTemplateLog {

    ThemeTemplateLog NONE = message -> {
    };

    void warn(String message);
}
