package com.kirix.schedule;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.time.LocalDate;

import org.json.JSONException;

final class SchedulePrefs {
    private static final String PREFS = "schedule_widget";
    private static final String KEY_GROUP = "group";
    private static final String KEY_IS_TEACHER = "is_teacher";
    private static final String KEY_LAST_JSON = "last_json";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_SELECTED_DATE = "selected_date";
    private static final String KEY_WIDGET_SELECTED_DATE = "widget_selected_date";
    private static final String KEY_FORECAST_JSON = "weather_forecast_json";
    private static final String KEY_FORECAST_ERROR = "weather_forecast_error";
    private static final String KEY_CITY_SLUG = "weather_city_slug";
    private static final String KEY_CITY_NAME = "weather_city_name";

    private SchedulePrefs() {
    }

    static String getGroup(Context context) {
        return prefs(context).getString(KEY_GROUP, "");
    }

    static boolean isTeacher(Context context) {
        return prefs(context).getBoolean(KEY_IS_TEACHER, false);
    }

    static void setGroup(Context context, String group, boolean isTeacher) {
        String selectedGroup = group.trim();
        SharedPreferences preferences = prefs(context);
        boolean prevIsTeacher = preferences.getBoolean(KEY_IS_TEACHER, false);
        String prevGroup = preferences.getString(KEY_GROUP, "");
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_GROUP, selectedGroup)
                .putBoolean(KEY_IS_TEACHER, isTeacher);
        if (prevIsTeacher != isTeacher || !ScheduleArchiveStore.groupsMatch(prevGroup, selectedGroup)) {
            String today = LocalDate.now().toString();
            editor.putString(KEY_SELECTED_DATE, today);
            editor.putString(KEY_WIDGET_SELECTED_DATE, today);
        }
        editor.apply();
    }

    static void setLastSchedule(Context context, String rawJson) {
        prefs(context).edit().putString(KEY_LAST_JSON, rawJson).remove(KEY_LAST_ERROR).apply();
        try {
            ScheduleArchiveStore.saveDay(context, ScheduleData.fromJson(rawJson));
        } catch (Exception ignored) {
        }
    }

    static void setLastError(Context context, String message) {
        prefs(context).edit().putString(KEY_LAST_ERROR, message).apply();
    }

    static String getLastError(Context context) {
        return prefs(context).getString(KEY_LAST_ERROR, "");
    }

    static void clearLastError(Context context) {
        prefs(context).edit().remove(KEY_LAST_ERROR).apply();
    }

    static ScheduleData getLegacySchedule(Context context) {
        String raw = prefs(context).getString(KEY_LAST_JSON, "");
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return ScheduleData.fromJson(raw);
        } catch (JSONException ignored) {
            return null;
        }
    }

    static LocalDate getSelectedDate(Context context) {
        return parseDate(prefs(context).getString(KEY_SELECTED_DATE, ""));
    }

    static void setSelectedDate(Context context, LocalDate date) {
        prefs(context).edit().putString(KEY_SELECTED_DATE, date.toString()).apply();
    }

    static LocalDate getWidgetSelectedDate(Context context) {
        return parseDate(prefs(context).getString(KEY_WIDGET_SELECTED_DATE, ""));
    }

    static void setWidgetSelectedDate(Context context, LocalDate date) {
        prefs(context).edit().putString(KEY_WIDGET_SELECTED_DATE, date.toString()).apply();
    }

    // --- Прогноз погоды Gismeteo ---

    static String getCitySlug(Context context) {
        return prefs(context).getString(KEY_CITY_SLUG, "chelyabinsk-4565");
    }

    static void setCity(Context context, String name, String slug) {
        prefs(context).edit()
                .putString(KEY_CITY_NAME, name)
                .putString(KEY_CITY_SLUG, slug)
                .remove(KEY_FORECAST_JSON)
                .remove(KEY_FORECAST_ERROR)
                .apply();
    }

    static String getCityName(Context context) {
        return prefs(context).getString(KEY_CITY_NAME, "Челябинск");
    }

    static void setLastForecast(Context context, String rawJson) {
        prefs(context).edit()
                .putString(KEY_FORECAST_JSON, rawJson)
                .remove(KEY_FORECAST_ERROR)
                .apply();
    }

    static void setLastForecastError(Context context, String message) {
        prefs(context).edit().putString(KEY_FORECAST_ERROR, message).apply();
    }

    static String getLastForecastError(Context context) {
        return prefs(context).getString(KEY_FORECAST_ERROR, "");
    }

    static GismeteoWeatherData getLastForecast(Context context) {
        String raw = prefs(context).getString(KEY_FORECAST_JSON, "");
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return GismeteoWeatherData.fromJson(raw);
        } catch (JSONException ignored) {
            return null;
        }
    }

    // --- QR-код прохода ---

    static boolean hasQrCode(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "pass_qr.png").exists();
    }

    static void deleteQrCode(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), "pass_qr.png");
        if (file.exists()) {
            file.delete();
        }
        setWidgetShowQr(context, false);
    }

    static boolean getWidgetShowQr(Context context) {
        return prefs(context).getBoolean("widget_show_qr", false);
    }

    static void setWidgetShowQr(Context context, boolean show) {
        prefs(context).edit().putBoolean("widget_show_qr", show).apply();
    }

    // --- Режим погоды на виджете ---

    static boolean getWidgetShowWeather(Context context) {
        return prefs(context).getBoolean("widget_show_weather", false);
    }

    static void setWidgetShowWeather(Context context, boolean show) {
        prefs(context).edit().putBoolean("widget_show_weather", show).apply();
    }

    // --- Оформление ---

    static String getWidgetTheme(Context context) {
        return prefs(context).getString("widget_theme", "dark_glass");
    }

    static void setWidgetTheme(Context context, String theme) {
        prefs(context).edit().putString("widget_theme", theme).apply();
    }

    static String getAccentColor(Context context) {
        return prefs(context).getString("accent_color", "indigo");
    }

    static void setAccentColor(Context context, String accent) {
        prefs(context).edit().putString("accent_color", accent).apply();
    }

    private static LocalDate parseDate(String rawDate) {
        try {
            return LocalDate.parse(rawDate);
        } catch (RuntimeException e) {
            return LocalDate.now();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
