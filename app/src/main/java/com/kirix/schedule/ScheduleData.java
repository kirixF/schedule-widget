package com.kirix.schedule;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes2.dex */
final class ScheduleData {
    final String dateKey;
    final String dayName;
    final String group;
    final boolean isTeacher;
    final List<Lesson> lessons;
    final long updatedAtMillis;

    static final class Lesson {
        final String number;
        final String room;
        final String subject;
        final String teacher;
        final String time;

        Lesson(String number, String time, String subject, String teacher, String room) {
            this.number = number;
            this.time = time;
            this.subject = subject;
            this.teacher = teacher;
            this.room = room;
        }
    }

    ScheduleData(String group, boolean isTeacher, String dateKey, String dayName, List<Lesson> lessons, long updatedAtMillis) {
        this.group = group;
        this.isTeacher = isTeacher;
        this.dateKey = dateKey;
        this.dayName = dayName;
        this.lessons = lessons;
        this.updatedAtMillis = updatedAtMillis;
    }

    static ScheduleData fromJson(String rawJson) throws JSONException {
        return fromJsonObject(new JSONObject(rawJson));
    }

    static ScheduleData fromJsonObject(JSONObject json) throws JSONException {
        String dateKey;
        JSONArray rawLessons = json.optJSONArray("lessons");
        List<Lesson> lessons = new ArrayList<>();
        if (rawLessons != null) {
            for (int i = 0; i < rawLessons.length(); i++) {
                JSONObject item = rawLessons.getJSONObject(i);
                lessons.add(new Lesson(item.optString("number"), item.optString("time"), item.optString("subject"), item.optString("teacher"), item.optString("room")));
            }
        }
        long updatedAt = json.optLong("updatedAtMillis", System.currentTimeMillis());
        String dateKey2 = json.optString("dateKey");
        if (!dateKey2.trim().isEmpty()) {
            dateKey = dateKey2;
        } else {
            dateKey = Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString();
        }
        return new ScheduleData(json.optString("group"), json.optBoolean("isTeacher", false), dateKey, json.optString("dayName"), lessons, updatedAt);
    }

    static ScheduleData empty(String group, boolean isTeacher, LocalDate date, String dayName, long updatedAtMillis) {
        return new ScheduleData(group, isTeacher, date.toString(), dayName, new ArrayList(), updatedAtMillis);
    }

    JSONObject toJsonObject() throws JSONException {
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("group", this.group);
        jSONObject.put("isTeacher", this.isTeacher);
        jSONObject.put("dateKey", this.dateKey);
        jSONObject.put("dayName", this.dayName);
        JSONArray rawLessons = new JSONArray();
        for (Lesson lesson : this.lessons) {
            JSONObject item = new JSONObject();
            item.put("number", lesson.number);
            item.put("time", lesson.time);
            item.put("subject", lesson.subject);
            item.put("teacher", lesson.teacher);
            item.put("room", lesson.room);
            rawLessons.put(item);
        }
        jSONObject.put("lessons", rawLessons);
        jSONObject.put("updatedAtMillis", this.updatedAtMillis);
        return jSONObject;
    }

    String widgetTitle() {
        String normalizedGroup;
        if (this.group == null || this.group.trim().isEmpty()) {
            normalizedGroup = "группа";
        } else {
            normalizedGroup = this.group.toUpperCase(Locale.forLanguageTag("ru"));
        }
        return "Сегодня · " + normalizedGroup;
    }

    String widgetSubtitle() {
        String day = (this.dayName == null || this.dayName.trim().isEmpty()) ? "сегодня" : this.dayName;
        return day + " · " + formatTime(this.updatedAtMillis);
    }

    String widgetBody() {
        if (this.lessons.isEmpty()) {
            return "Занятий нет";
        }
        StringBuilder body = new StringBuilder();
        int limit = Math.min(this.lessons.size(), 4);
        for (int i = 0; i < limit; i++) {
            Lesson lesson = this.lessons.get(i);
            if (i > 0) {
                body.append('\n');
            }
            body.append(compactLine(clean(lesson.number), clean(lesson.time)));
            String details = clean(lesson.room);
            if (this.isTeacher && !clean(lesson.teacher).isEmpty()) {
                details = compactLine(details, "группа " + clean(lesson.teacher));
            }
            body.append('\n').append(compactLine(clean(lesson.subject), details));
        }
        if (this.lessons.size() > limit) {
            body.append('\n').append("+").append(this.lessons.size() - limit).append(" еще");
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
