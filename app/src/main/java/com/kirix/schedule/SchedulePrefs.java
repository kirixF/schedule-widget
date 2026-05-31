package com.kirix.schedule;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

import java.time.LocalDate;

final class SchedulePrefs {
    private static final String PREFS = "schedule_widget";
    private static final String KEY_GROUP = "group";
    private static final String KEY_LAST_JSON = "last_json";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_SELECTED_DATE = "selected_date";
    private static final String KEY_WIDGET_SELECTED_DATE = "widget_selected_date";

    private SchedulePrefs() {
    }

    static String getGroup(Context context) {
        return prefs(context).getString(KEY_GROUP, "");
    }

    static void setGroup(Context context, String group) {
        String selectedGroup = group.trim();
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor editor = preferences.edit().putString(KEY_GROUP, selectedGroup);
        if (!ScheduleArchiveStore.groupsMatch(preferences.getString(KEY_GROUP, ""), selectedGroup)) {
            String today = LocalDate.now().toString();
            editor.putString(KEY_SELECTED_DATE, today);
            editor.putString(KEY_WIDGET_SELECTED_DATE, today);
        }
        editor.apply();
    }

    static void setLastSchedule(Context context, String rawJson) {
        prefs(context).edit()
                .putString(KEY_LAST_JSON, rawJson)
                .remove(KEY_LAST_ERROR)
                .apply();
        try {
            ScheduleArchiveStore.saveDay(context, ScheduleData.fromJson(rawJson));
        } catch (Exception ignored) {
            // The legacy value remains available when the archive cannot be updated.
        }
    }

    static void setLastError(Context context, String message) {
        prefs(context).edit().putString(KEY_LAST_ERROR, message).apply();
    }

    static String getLastError(Context context) {
        return prefs(context).getString(KEY_LAST_ERROR, "");
    }

    static ScheduleData getLastSchedule(Context context) {
        return ScheduleArchiveStore.getTodaySchedule(context);
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

    static void clearLastError(Context context) {
        prefs(context).edit().remove(KEY_LAST_ERROR).apply();
    }

    static LocalDate getSelectedDate(Context context) {
        String rawDate = prefs(context).getString(KEY_SELECTED_DATE, "");
        try {
            return LocalDate.parse(rawDate);
        } catch (RuntimeException ignored) {
            return LocalDate.now();
        }
    }

    static void setSelectedDate(Context context, LocalDate date) {
        prefs(context).edit().putString(KEY_SELECTED_DATE, date.toString()).apply();
    }

    static LocalDate getWidgetSelectedDate(Context context) {
        String rawDate = prefs(context).getString(KEY_WIDGET_SELECTED_DATE, "");
        try {
            return LocalDate.parse(rawDate);
        } catch (RuntimeException ignored) {
            return LocalDate.now();
        }
    }

    static void setWidgetSelectedDate(Context context, LocalDate date) {
        prefs(context).edit().putString(KEY_WIDGET_SELECTED_DATE, date.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
