package com.kirix.schedule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class ScheduleData {
    static final class Lesson {
        final String number;
        final String time;
        final String subject;
        final String teacher;
        final String room;

        Lesson(String number, String time, String subject, String teacher, String room) {
            this.number = number;
            this.time = time;
            this.subject = subject;
            this.teacher = teacher;
            this.room = room;
        }
    }

    final String group;
    final String dayName;
    final List<Lesson> lessons;
    final long updatedAtMillis;

    ScheduleData(String group, String dayName, List<Lesson> lessons, long updatedAtMillis) {
        this.group = group;
        this.dayName = dayName;
        this.lessons = lessons;
        this.updatedAtMillis = updatedAtMillis;
    }

    static ScheduleData fromJson(String rawJson) throws JSONException {
        JSONObject json = new JSONObject(rawJson);
        JSONArray rawLessons = json.optJSONArray("lessons");
        List<Lesson> lessons = new ArrayList<>();
        if (rawLessons != null) {
            for (int i = 0; i < rawLessons.length(); i++) {
                JSONObject item = rawLessons.getJSONObject(i);
                lessons.add(new Lesson(
                        item.optString("number"),
                        item.optString("time"),
                        item.optString("subject"),
                        item.optString("teacher"),
                        item.optString("room")
                ));
            }
        }
        long updatedAt = json.optLong("updatedAtMillis", System.currentTimeMillis());
        return new ScheduleData(
                json.optString("group"),
                json.optString("dayName"),
                lessons,
                updatedAt
        );
    }

    String widgetTitle() {
        String normalizedGroup = group == null || group.trim().isEmpty()
                ? "группа"
                : group.toUpperCase(Locale.forLanguageTag("ru"));
        return "Сегодня · " + normalizedGroup;
    }

    String widgetSubtitle() {
        String day = dayName == null || dayName.trim().isEmpty() ? "сегодня" : dayName;
        return day + " · " + formatTime(updatedAtMillis);
    }

    String widgetBody() {
        if (lessons.isEmpty()) {
            return "Занятий нет";
        }

        StringBuilder body = new StringBuilder();
        int limit = Math.min(lessons.size(), 4);
        for (int i = 0; i < limit; i++) {
            Lesson lesson = lessons.get(i);
            if (i > 0) {
                body.append('\n');
            }
            body.append(compactLine(clean(lesson.number), clean(lesson.time)));
            body.append('\n').append(compactLine(clean(lesson.subject), clean(lesson.room)));
        }
        if (lessons.size() > limit) {
            body.append('\n').append("+").append(lessons.size() - limit).append(" еще");
        }
        return body.toString();
    }

    private static String compactLine(String first, String second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return first + " · " + second;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String formatTime(long millis) {
        return new SimpleDateFormat("dd.MM HH:mm", Locale.forLanguageTag("ru")).format(new Date(millis));
    }
}
