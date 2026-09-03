package com.kirix.schedule;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes2.dex */
final class ScheduleArchive {
    final LocalDate dateBegin;
    final LocalDate dateEnd;
    private final Map<String, ScheduleData> days;
    final String group;
    final boolean isTeacher;
    final long updatedAtMillis;

    ScheduleArchive(String group, boolean isTeacher, LocalDate dateBegin, LocalDate dateEnd, long updatedAtMillis, Map<String, ScheduleData> days) {
        this.group = group;
        this.isTeacher = isTeacher;
        this.dateBegin = dateBegin;
        this.dateEnd = dateEnd;
        this.updatedAtMillis = updatedAtMillis;
        this.days = new TreeMap(days);
    }

    static ScheduleArchive fromJson(String rawJson) throws JSONException {
        JSONObject json = new JSONObject(rawJson);
        Map<String, ScheduleData> days = new TreeMap<>();
        JSONArray rawDays = json.optJSONArray("days");
        if (rawDays != null) {
            for (int i = 0; i < rawDays.length(); i++) {
                ScheduleData day = ScheduleData.fromJsonObject(rawDays.getJSONObject(i));
                days.put(day.dateKey, day);
            }
        }
        LocalDate today = LocalDate.now();
        LocalDate dateBegin = parseDate(json.optString("dateBegin"), today);
        LocalDate dateEnd = parseDate(json.optString("dateEnd"), dateBegin);
        return new ScheduleArchive(json.optString("group"), json.optBoolean("isTeacher", false), dateBegin, dateEnd, json.optLong("updatedAtMillis", System.currentTimeMillis()), days);
    }

    static ScheduleArchive containingDay(ScheduleData day) {
        LocalDate date = parseDate(day.dateKey, LocalDate.now());
        Map<String, ScheduleData> days = new TreeMap<>();
        days.put(day.dateKey, day);
        return new ScheduleArchive(day.group, day.isTeacher, date, date, day.updatedAtMillis, days);
    }

    ScheduleData getDay(LocalDate date) {
        return this.days.get(date.toString());
    }

    Collection<ScheduleData> getDays() {
        return this.days.values();
    }

    int getCachedDayCount() {
        return this.days.size();
    }

    ScheduleArchive mergeHistoricalDaysFrom(ScheduleArchive previous) {
        if (previous == null || this.isTeacher != previous.isTeacher || !ScheduleArchiveStore.groupsMatch(this.group, previous.group)) {
            return this;
        }
        Map<String, ScheduleData> merged = new TreeMap<>(this.days);
        for (ScheduleData previousDay : previous.getDays()) {
            LocalDate date = parseDate(previousDay.dateKey, this.dateBegin);
            if (date.isBefore(this.dateBegin)) {
                merged.put(previousDay.dateKey, previousDay);
            }
        }
        LocalDate mergedBegin = previous.dateBegin.isBefore(this.dateBegin) ? previous.dateBegin : this.dateBegin;
        return new ScheduleArchive(this.group, this.isTeacher, mergedBegin, this.dateEnd, this.updatedAtMillis, merged);
    }

    ScheduleArchive withDay(ScheduleData day) {
        if (this.isTeacher != day.isTeacher || !ScheduleArchiveStore.groupsMatch(this.group, day.group)) {
            return containingDay(day);
        }
        LocalDate date = parseDate(day.dateKey, LocalDate.now());
        Map<String, ScheduleData> merged = new TreeMap<>(this.days);
        merged.put(day.dateKey, day);
        LocalDate mergedBegin = date.isBefore(this.dateBegin) ? date : this.dateBegin;
        LocalDate mergedEnd = date.isAfter(this.dateEnd) ? date : this.dateEnd;
        return new ScheduleArchive(this.group, this.isTeacher, mergedBegin, mergedEnd, day.updatedAtMillis, merged);
    }

    String toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("group", this.group);
        json.put("isTeacher", this.isTeacher);
        json.put("dateBegin", this.dateBegin.toString());
        json.put("dateEnd", this.dateEnd.toString());
        json.put("updatedAtMillis", this.updatedAtMillis);
        JSONArray rawDays = new JSONArray();
        for (ScheduleData day : this.days.values()) {
            rawDays.put(day.toJsonObject());
        }
        json.put("days", rawDays);
        return json.toString();
    }

    private static LocalDate parseDate(String rawDate, LocalDate fallback) {
        try {
            return LocalDate.parse(rawDate);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
