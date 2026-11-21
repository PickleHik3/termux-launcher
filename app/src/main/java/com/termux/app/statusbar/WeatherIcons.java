package com.termux.app.statusbar;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.HashMap;
import java.util.Map;

/**
 * The one owner of which Roundline weather drawable a sky wears: a (WMO code, is-day) pair maps to
 * an {@link Icon}, two layers drawn in two tints. The {@code base} layer is the cloud, rain, snow
 * or fog and takes the ink the caller draws its text in; the {@code accent} layer is the sun, moon,
 * stars or bolt and takes the launcher's accent. A layer the picture does not have is 0.
 *
 * <p>Pure and allocation-free after class load: every icon is a constant, so the status bar, the
 * card and the widgets share the same few objects. A daily code has no is_day flag, so callers
 * pass {@code true} for the week's rows.
 */
public final class WeatherIcons {
    /** Prefix of an icon's {@link Icon#key}, so a widget plan can carry one in a glyph's text. */
    public static final String KEY_PREFIX = "wx:";

    /** One weather picture: its name and its two layers, each 0 when the picture lacks it. */
    public static final class Icon {
        @NonNull public final String name;
        @DrawableRes public final int base;
        @DrawableRes public final int accent;
        /** {@link #KEY_PREFIX} plus the name; what a plan's glyph text holds. */
        @NonNull public final String key;

        Icon(@NonNull String name, @DrawableRes int base, @DrawableRes int accent) {
            this.name = name; this.base = base; this.accent = accent;
            this.key = KEY_PREFIX + name;
        }
    }

    private static final Map<String, Icon> BY_KEY = new HashMap<>();

    private static Icon icon(@NonNull String name, @DrawableRes int base,
                             @DrawableRes int accent) {
        Icon icon = new Icon(name, base, accent);
        BY_KEY.put(icon.key, icon);
        return icon;
    }

    public static final Icon CLEAR_DAY = icon("clear_day",
        0,
        R.drawable.ic_rl_wx_clear_day_accent);
    public static final Icon CLEAR_NIGHT = icon("clear_night",
        0,
        R.drawable.ic_rl_wx_clear_night_accent);
    public static final Icon DROP = icon("drop",
        R.drawable.ic_rl_wx_drop_base,
        0);
    public static final Icon FOG_DAY = icon("fog_day",
        R.drawable.ic_rl_wx_fog_day_base,
        R.drawable.ic_rl_wx_fog_day_accent);
    public static final Icon FOG_NIGHT = icon("fog_night",
        R.drawable.ic_rl_wx_fog_night_base,
        R.drawable.ic_rl_wx_fog_night_accent);
    public static final Icon HUMIDITY = icon("humidity",
        R.drawable.ic_rl_wx_humidity_base,
        0);
    public static final Icon MAINLY_CLEAR_DAY = icon("mainly_clear_day",
        R.drawable.ic_rl_wx_mainly_clear_day_base,
        R.drawable.ic_rl_wx_mainly_clear_day_accent);
    public static final Icon MAINLY_CLEAR_NIGHT = icon("mainly_clear_night",
        R.drawable.ic_rl_wx_mainly_clear_night_base,
        R.drawable.ic_rl_wx_mainly_clear_night_accent);
    public static final Icon NOT_AVAILABLE = icon("not_available",
        R.drawable.ic_rl_wx_not_available_base,
        0);
    public static final Icon OVERCAST = icon("overcast",
        R.drawable.ic_rl_wx_overcast_base,
        0);
    public static final Icon OVERCAST_DAY_DRIZZLE = icon("overcast_day_drizzle",
        R.drawable.ic_rl_wx_overcast_day_drizzle_base,
        R.drawable.ic_rl_wx_overcast_day_drizzle_accent);
    public static final Icon OVERCAST_DAY_RAIN = icon("overcast_day_rain",
        R.drawable.ic_rl_wx_overcast_day_rain_base,
        R.drawable.ic_rl_wx_overcast_day_rain_accent);
    public static final Icon OVERCAST_DAY_RAIN_HEAVY = icon("overcast_day_rain_heavy",
        R.drawable.ic_rl_wx_overcast_day_rain_heavy_base,
        R.drawable.ic_rl_wx_overcast_day_rain_heavy_accent);
    public static final Icon OVERCAST_DAY_SLEET = icon("overcast_day_sleet",
        R.drawable.ic_rl_wx_overcast_day_sleet_base,
        R.drawable.ic_rl_wx_overcast_day_sleet_accent);
    public static final Icon OVERCAST_DAY_SNOW = icon("overcast_day_snow",
        R.drawable.ic_rl_wx_overcast_day_snow_base,
        R.drawable.ic_rl_wx_overcast_day_snow_accent);
    public static final Icon OVERCAST_DAY_SNOW_HEAVY = icon("overcast_day_snow_heavy",
        R.drawable.ic_rl_wx_overcast_day_snow_heavy_base,
        R.drawable.ic_rl_wx_overcast_day_snow_heavy_accent);
    public static final Icon OVERCAST_FOG = icon("overcast_fog",
        R.drawable.ic_rl_wx_overcast_fog_base,
        0);
    public static final Icon OVERCAST_NIGHT_DRIZZLE = icon("overcast_night_drizzle",
        R.drawable.ic_rl_wx_overcast_night_drizzle_base,
        R.drawable.ic_rl_wx_overcast_night_drizzle_accent);
    public static final Icon OVERCAST_NIGHT_RAIN = icon("overcast_night_rain",
        R.drawable.ic_rl_wx_overcast_night_rain_base,
        R.drawable.ic_rl_wx_overcast_night_rain_accent);
    public static final Icon OVERCAST_NIGHT_RAIN_HEAVY = icon("overcast_night_rain_heavy",
        R.drawable.ic_rl_wx_overcast_night_rain_heavy_base,
        R.drawable.ic_rl_wx_overcast_night_rain_heavy_accent);
    public static final Icon OVERCAST_NIGHT_SLEET = icon("overcast_night_sleet",
        R.drawable.ic_rl_wx_overcast_night_sleet_base,
        R.drawable.ic_rl_wx_overcast_night_sleet_accent);
    public static final Icon OVERCAST_NIGHT_SNOW = icon("overcast_night_snow",
        R.drawable.ic_rl_wx_overcast_night_snow_base,
        R.drawable.ic_rl_wx_overcast_night_snow_accent);
    public static final Icon OVERCAST_NIGHT_SNOW_HEAVY = icon("overcast_night_snow_heavy",
        R.drawable.ic_rl_wx_overcast_night_snow_heavy_base,
        R.drawable.ic_rl_wx_overcast_night_snow_heavy_accent);
    public static final Icon OVERCAST_RIME_FOG = icon("overcast_rime_fog",
        R.drawable.ic_rl_wx_overcast_rime_fog_base,
        0);
    public static final Icon PARTLY_CLOUDY_DAY = icon("partly_cloudy_day",
        R.drawable.ic_rl_wx_partly_cloudy_day_base,
        R.drawable.ic_rl_wx_partly_cloudy_day_accent);
    public static final Icon PARTLY_CLOUDY_DAY_DRIZZLE = icon("partly_cloudy_day_drizzle",
        R.drawable.ic_rl_wx_partly_cloudy_day_drizzle_base,
        R.drawable.ic_rl_wx_partly_cloudy_day_drizzle_accent);
    public static final Icon PARTLY_CLOUDY_DAY_RAIN = icon("partly_cloudy_day_rain",
        R.drawable.ic_rl_wx_partly_cloudy_day_rain_base,
        R.drawable.ic_rl_wx_partly_cloudy_day_rain_accent);
    public static final Icon PARTLY_CLOUDY_DAY_RAIN_HEAVY = icon("partly_cloudy_day_rain_heavy",
        R.drawable.ic_rl_wx_partly_cloudy_day_rain_heavy_base,
        R.drawable.ic_rl_wx_partly_cloudy_day_rain_heavy_accent);
    public static final Icon PARTLY_CLOUDY_DAY_SLEET = icon("partly_cloudy_day_sleet",
        R.drawable.ic_rl_wx_partly_cloudy_day_sleet_base,
        R.drawable.ic_rl_wx_partly_cloudy_day_sleet_accent);
    public static final Icon PARTLY_CLOUDY_DAY_SNOW = icon("partly_cloudy_day_snow",
        R.drawable.ic_rl_wx_partly_cloudy_day_snow_base,
        R.drawable.ic_rl_wx_partly_cloudy_day_snow_accent);
    public static final Icon PARTLY_CLOUDY_DAY_SNOW_HEAVY = icon("partly_cloudy_day_snow_heavy",
        R.drawable.ic_rl_wx_partly_cloudy_day_snow_heavy_base,
        R.drawable.ic_rl_wx_partly_cloudy_day_snow_heavy_accent);
    public static final Icon PARTLY_CLOUDY_NIGHT = icon("partly_cloudy_night",
        R.drawable.ic_rl_wx_partly_cloudy_night_base,
        R.drawable.ic_rl_wx_partly_cloudy_night_accent);
    public static final Icon PARTLY_CLOUDY_NIGHT_DRIZZLE = icon("partly_cloudy_night_drizzle",
        R.drawable.ic_rl_wx_partly_cloudy_night_drizzle_base,
        R.drawable.ic_rl_wx_partly_cloudy_night_drizzle_accent);
    public static final Icon PARTLY_CLOUDY_NIGHT_RAIN = icon("partly_cloudy_night_rain",
        R.drawable.ic_rl_wx_partly_cloudy_night_rain_base,
        R.drawable.ic_rl_wx_partly_cloudy_night_rain_accent);
    public static final Icon PARTLY_CLOUDY_NIGHT_RAIN_HEAVY = icon("partly_cloudy_night_rain_heavy",
        R.drawable.ic_rl_wx_partly_cloudy_night_rain_heavy_base,
        R.drawable.ic_rl_wx_partly_cloudy_night_rain_heavy_accent);
    public static final Icon PARTLY_CLOUDY_NIGHT_SLEET = icon("partly_cloudy_night_sleet",
        R.drawable.ic_rl_wx_partly_cloudy_night_sleet_base,
        R.drawable.ic_rl_wx_partly_cloudy_night_sleet_accent);
    public static final Icon PARTLY_CLOUDY_NIGHT_SNOW = icon("partly_cloudy_night_snow",
        R.drawable.ic_rl_wx_partly_cloudy_night_snow_base,
        R.drawable.ic_rl_wx_partly_cloudy_night_snow_accent);
    public static final Icon PARTLY_CLOUDY_NIGHT_SNOW_HEAVY = icon("partly_cloudy_night_snow_heavy",
        R.drawable.ic_rl_wx_partly_cloudy_night_snow_heavy_base,
        R.drawable.ic_rl_wx_partly_cloudy_night_snow_heavy_accent);
    public static final Icon SNOWFLAKE = icon("snowflake",
        R.drawable.ic_rl_wx_snowflake_base,
        0);
    public static final Icon THUNDERSTORMS_DAY = icon("thunderstorms_day",
        R.drawable.ic_rl_wx_thunderstorms_day_base,
        R.drawable.ic_rl_wx_thunderstorms_day_accent);
    public static final Icon THUNDERSTORMS_DAY_HAIL = icon("thunderstorms_day_hail",
        R.drawable.ic_rl_wx_thunderstorms_day_hail_base,
        R.drawable.ic_rl_wx_thunderstorms_day_hail_accent);
    public static final Icon THUNDERSTORMS_NIGHT = icon("thunderstorms_night",
        R.drawable.ic_rl_wx_thunderstorms_night_base,
        R.drawable.ic_rl_wx_thunderstorms_night_accent);
    public static final Icon THUNDERSTORMS_NIGHT_HAIL = icon("thunderstorms_night_hail",
        R.drawable.ic_rl_wx_thunderstorms_night_hail_base,
        R.drawable.ic_rl_wx_thunderstorms_night_hail_accent);
    public static final Icon UV = icon("uv",
        R.drawable.ic_rl_wx_uv_base,
        0);
    public static final Icon WIND = icon("wind",
        R.drawable.ic_rl_wx_wind_base,
        0);

    private WeatherIcons() {}

    /** The icon a plan's glyph text names, null when the text is not an icon's key. */
    @Nullable
    public static Icon forKey(@NonNull String key) {
        return key.startsWith(KEY_PREFIX) ? BY_KEY.get(key) : null;
    }

    /**
     * The icon for a WMO weather code. Codes that describe one sky share a picture; heavy rain
     * and snow, hail and the freezing kinds have their own. An unknown code is not "cloudy": it
     * gets the not-available picture.
     *
     * @param isDay from the provider's own is_day flag, not from the device clock
     */
    @NonNull
    public static Icon forCode(int code, boolean isDay) {
        switch (code) {
            case 0: return isDay ? CLEAR_DAY : CLEAR_NIGHT;
            case 1: return isDay ? MAINLY_CLEAR_DAY : MAINLY_CLEAR_NIGHT;
            case 2: return isDay ? PARTLY_CLOUDY_DAY : PARTLY_CLOUDY_NIGHT;
            case 3: return OVERCAST;
            case 45: return isDay ? FOG_DAY : FOG_NIGHT;
            case 48: return OVERCAST_RIME_FOG;
            case 51: case 53:
                return isDay ? PARTLY_CLOUDY_DAY_DRIZZLE : PARTLY_CLOUDY_NIGHT_DRIZZLE;
            case 55: return isDay ? OVERCAST_DAY_DRIZZLE : OVERCAST_NIGHT_DRIZZLE;
            case 56: case 57:
                return isDay ? PARTLY_CLOUDY_DAY_SLEET : PARTLY_CLOUDY_NIGHT_SLEET;
            case 61: case 63: return isDay ? OVERCAST_DAY_RAIN : OVERCAST_NIGHT_RAIN;
            case 65: return isDay ? OVERCAST_DAY_RAIN_HEAVY : OVERCAST_NIGHT_RAIN_HEAVY;
            case 66: case 67: return isDay ? OVERCAST_DAY_SLEET : OVERCAST_NIGHT_SLEET;
            case 71: case 73: return isDay ? OVERCAST_DAY_SNOW : OVERCAST_NIGHT_SNOW;
            case 75: return isDay ? OVERCAST_DAY_SNOW_HEAVY : OVERCAST_NIGHT_SNOW_HEAVY;
            case 77: return SNOWFLAKE;
            case 80: case 81:
                return isDay ? PARTLY_CLOUDY_DAY_RAIN : PARTLY_CLOUDY_NIGHT_RAIN;
            case 82:
                return isDay ? PARTLY_CLOUDY_DAY_RAIN_HEAVY : PARTLY_CLOUDY_NIGHT_RAIN_HEAVY;
            case 85: return isDay ? PARTLY_CLOUDY_DAY_SNOW : PARTLY_CLOUDY_NIGHT_SNOW;
            case 86:
                return isDay ? PARTLY_CLOUDY_DAY_SNOW_HEAVY : PARTLY_CLOUDY_NIGHT_SNOW_HEAVY;
            case 95: return isDay ? THUNDERSTORMS_DAY : THUNDERSTORMS_NIGHT;
            case 96: case 99:
                return isDay ? THUNDERSTORMS_DAY_HAIL : THUNDERSTORMS_NIGHT_HAIL;
            default: return NOT_AVAILABLE;
        }
    }
}
