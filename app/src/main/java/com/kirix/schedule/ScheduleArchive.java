package com.kirix.schedule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

final class ScheduleArchive {
    final String group;
    final LocalDate dateBegin;
    final LocalDate dateEnd;
    final long updatedAtMillis;
    private final Map<String, ScheduleData> days;

    ScheduleArchive(
            String group,
            LocalDate dateBegin,
            LocalDate dateEnd,
            long updatedAtMillis,
            Map<String, ScheduleData> days
    ) {
        this.group = group;
        this.dateBegin = dateBegin;
        this.dateEnd = dateEnd;
        this.updatedAtMillis = updatedAtMillis;
        this.days = new TreeMap<>(days);
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
        return new ScheduleArchive(
                json.optString("group"),
                dateBegin,
                dateEnd,
                json.optLong("updatedAtMillis", System.currentTimeMillis()),
                days
        );
    }

    static ScheduleArchive containingDay(ScheduleData day) {
        LocalDate date = parseDate(day.dateKey, LocalDate.now());
        Map<String, ScheduleData> days = new TreeMap<>();
        days.put(day.dateKey, day);
        return new ScheduleArchive(day.group, date, date, day.updatedAtMillis, days);
    }

    ScheduleData getDay(LocalDate date) {
        return days.get(date.toString());
    }

    Collection<ScheduleData> getDays() {
        return days.values();
    }

    int getCachedDayCount() {
        return days.size();
    }

    ScheduleArchive mergeHistoricalDaysFrom(ScheduleArchive previous) {
        if (previous == null || !ScheduleArchiveStore.groupsMatch(group, previous.group)) {
            return this;
        }

        Map<String, ScheduleData> merged = new TreeMap<>(days);
        for (ScheduleData previousDay : previous.getDays()) {
            LocalDate date = parseDate(previousDay.dateKey, dateBegin);
            if (date.isBefore(dateBegin)) {
                merged.put(previousDay.dateKey, previousDay);
            }
        }
        LocalDate mergedBegin = previous.dateBegin.isBefore(dateBegin) ? previous.dateBegin : dateBegin;
        return new ScheduleArchive(group, mergedBegin, dateEnd, updatedAtMillis, merged);
    }

    ScheduleArchive withDay(ScheduleData day) {
        if (!ScheduleArchiveStore.groupsMatch(group, day.group)) {
            return containingDay(day);
        }

        LocalDate date = parseDate(day.dateKey, LocalDate.now());
        Map<String, ScheduleData> merged = new TreeMap<>(days);
        merged.put(day.dateKey, day);
        LocalDate mergedBegin = date.isBefore(dateBegin) ? date : dateBegin;
        LocalDate mergedEnd = date.isAfter(dateEnd) ? date : dateEnd;
        return new ScheduleArchive(group, mergedBegin, mergedEnd, day.updatedAtMillis, merged);
    }

    String toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("group", group);
        json.put("dateBegin", dateBegin.toString());
        json.put("dateEnd", dateEnd.toString());
        json.put("updatedAtMillis", updatedAtMillis);
        JSONArray rawDays = new JSONArray();
        for (ScheduleData day : days.values()) {
            rawDays.put(day.toJsonObject());
        }
        json.put("days", rawDays);
        return json.toString();
    }

    private static LocalDate parseDate(String rawDate, LocalDate fallback) {
        try {
            return LocalDate.parse(rawDate);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
