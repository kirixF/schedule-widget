package com.kirix.schedule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

// Модель прогноза Gismeteo: агрегаты по дням + детали по полусуткам + текущая погода.
final class GismeteoWeatherData {

    final List<DayForecast> days;
    final long updatedAtMillis;
    // Текущая погода со страницы "сейчас" (актуальна только для сегодня; -9999 = нет данных)
    final int currentTemp;
    final int feelsLike;
    final int humidity;
    final int pressureMm;
    final int windSpeedNow;
    final String windDirNow;
    final String conditionNow;
    final String iconNow;

    GismeteoWeatherData(List<DayForecast> days, long updatedAtMillis,
                        int currentTemp, int feelsLike, int humidity, int pressureMm,
                        int windSpeedNow, String windDirNow, String conditionNow, String iconNow) {
        this.days = days == null ? new ArrayList<>() : days;
        this.updatedAtMillis = updatedAtMillis;
        this.currentTemp = currentTemp;
        this.feelsLike = feelsLike;
        this.humidity = humidity;
        this.pressureMm = pressureMm;
        this.windSpeedNow = windSpeedNow;
        this.windDirNow = windDirNow == null ? "" : windDirNow;
        this.conditionNow = conditionNow == null ? "" : conditionNow;
        this.iconNow = iconNow == null ? "" : iconNow;
    }

    static GismeteoWeatherData fromJson(String rawJson) throws JSONException {
        JSONObject root = new JSONObject(rawJson);
        long updatedAt = root.optLong("updatedAtMillis", System.currentTimeMillis());
        List<DayForecast> days = new ArrayList<>();
        JSONArray arr = root.optJSONArray("days");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                days.add(DayForecast.fromJson(arr.getJSONObject(i)));
            }
        }
        return new GismeteoWeatherData(days, updatedAt,
                root.optInt("currentTemp", -9999),
                root.optInt("feelsLike", -9999),
                root.optInt("humidity", -1),
                root.optInt("pressureMm", -1),
                root.optInt("windSpeedNow", -1),
                root.optString("windDirNow", ""),
                root.optString("conditionNow", ""),
                root.optString("iconNow", ""));
    }

    String toJson() throws JSONException {
        JSONObject json = new JSONObject();
        JSONArray arr = new JSONArray();
        for (DayForecast day : days) {
            arr.put(day.toJson());
        }
        json.put("days", arr);
        json.put("updatedAtMillis", updatedAtMillis);
        json.put("currentTemp", currentTemp);
        json.put("feelsLike", feelsLike);
        json.put("humidity", humidity);
        json.put("pressureMm", pressureMm);
        json.put("windSpeedNow", windSpeedNow);
        json.put("windDirNow", windDirNow);
        json.put("conditionNow", conditionNow);
        json.put("iconNow", iconNow);
        return json.toString();
    }

    static final int MAX_DAYS = 7;

    GismeteoWeatherData mergePrevious(GismeteoWeatherData prev) {
        LocalDate today = LocalDate.now();
        Map<String, DayForecast> byDate = new LinkedHashMap<>();
        if (prev != null && prev.days != null) {
            for (DayForecast d : prev.days) {
                if (d != null && d.date != null && !d.date.isEmpty() && !byDate.containsKey(d.date)) {
                    byDate.put(d.date, d);
                }
            }
        }
        for (DayForecast d : days) {
            if (d != null && d.date != null && !d.date.isEmpty()) {
                byDate.put(d.date, d);
            }
        }
        List<DayForecast> dated = new ArrayList<>();
        for (DayForecast d : byDate.values()) {
            if (resolveFullDate(d, today) != null) {
                dated.add(d);
            }
        }
        dated.sort((a, b) -> resolveFullDate(a, today).compareTo(resolveFullDate(b, today)));
        List<DayForecast> out = new ArrayList<>();
        for (DayForecast d : dated) {
            if (out.size() >= MAX_DAYS) break;
            out.add(d);
        }
        int curTemp = currentTemp;
        int curFeels = feelsLike;
        int curHum = humidity;
        int curPress = pressureMm;
        int curWind = windSpeedNow;
        String curWindDir = windDirNow;
        String curCond = conditionNow;
        String curIcon = iconNow;
        if (prev != null && curTemp == DayForecast.NO_VALUE) {
            curTemp = prev.currentTemp;
            curFeels = prev.feelsLike;
            curHum = prev.humidity;
            curPress = prev.pressureMm;
            curWind = prev.windSpeedNow;
            curWindDir = prev.windDirNow;
            curCond = prev.conditionNow;
            curIcon = prev.iconNow;
        }
        return new GismeteoWeatherData(out, updatedAtMillis,
                curTemp, curFeels, curHum, curPress, curWind, curWindDir, curCond, curIcon);
    }

    static LocalDate resolveFullDate(DayForecast d, LocalDate today) {
        if (d == null) return null;
        if (d.fullDate != null && !d.fullDate.trim().isEmpty()) {
            try {
                LocalDate parsed = LocalDate.parse(d.fullDate.trim());
                return parsed.isBefore(today) ? null : parsed;
            } catch (RuntimeException ignored) {
            }
        }
        int[] dm = parseShortDate(d.date);
        if (dm == null) return null;
        LocalDate cand;
        try {
            cand = LocalDate.of(today.getYear(), dm[1], dm[0]);
        } catch (RuntimeException e) {
            return null;
        }
        if (!cand.isBefore(today)) return cand;
        LocalDate rolled = cand.plusYears(1);
        if (!rolled.isAfter(today.plusDays(10))) return rolled;
        return null;
    }

    static String fullDateForParse(String shortDate, LocalDate today) {
        int[] dm = parseShortDate(shortDate);
        if (dm == null) return "";
        LocalDate cand;
        try {
            cand = LocalDate.of(today.getYear(), dm[1], dm[0]);
        } catch (RuntimeException e) {
            return "";
        }
        if (cand.isBefore(today)) cand = cand.plusYears(1);
        return cand.toString();
    }

    private static int[] parseShortDate(String shortDate) {
        if (shortDate == null) return null;
        String s = shortDate.trim();
        int dot = s.indexOf(46);
        if (dot <= 0 || dot + 1 >= s.length()) return null;
        try {
            int day = Integer.parseInt(s.substring(0, dot).trim());
            int month = Integer.parseInt(s.substring(dot + 1).trim());
            if (day < 1 || day > 31 || month < 1 || month > 12) return null;
            return new int[]{day, month};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Поиск дня прогноза по строке "dd.MM"
    DayForecast dayByDate(String shortDate) {
        for (DayForecast day : days) {
            if (day.date.equals(shortDate)) {
                return day;
            }
        }
        return null;
    }

    static final class DayForecast {
        static final int NO_VALUE = -9999;

        final String dayLabel;      // "ПН", "ВТ"...
        final String date;          // "19.08"
        final int tempMin;
        final int tempMax;
        final String condition;     // условие дневного слота
        final String windDir;       // "ЮЗ", "С"...
        final int windSpeed;        // м/с
        final String iconEmoji;     // эмодзи-символ
        // Полусутки: 4 слота (утро/день/вечер/ночь)
        final int[] slotTemps;
        final String[] slotConditions;
        final int[] slotWinds;
        final String[] slotWindDirs;
        final String fullDate;
        final String[] slotLabels;

        DayForecast(String dayLabel, String date, int tempMin, int tempMax,
                    String condition, String windDir, int windSpeed, String iconEmoji,
                    String fullDate, int[] slotTemps, String[] slotConditions, int[] slotWinds,
                    String[] slotWindDirs, String[] slotLabels) {
            this.dayLabel = dayLabel == null ? "" : dayLabel;
            this.date = date == null ? "" : date;
            this.tempMin = tempMin;
            this.tempMax = tempMax;
            this.condition = condition == null ? "" : condition;
            this.windDir = windDir == null ? "" : windDir;
            this.windSpeed = windSpeed;
            this.fullDate = fullDate == null ? "" : fullDate;
            this.iconEmoji = iconEmoji == null || iconEmoji.isEmpty()
                    ? iconEmojiForCondition(condition) : iconEmoji;
            this.slotTemps = slotTemps == null ? new int[0] : slotTemps;
            this.slotConditions = slotConditions == null ? new String[0] : slotConditions;
            this.slotWinds = slotWinds == null ? new int[0] : slotWinds;
            this.slotWindDirs = slotWindDirs == null ? new String[0] : slotWindDirs;
            this.slotLabels = slotLabels == null ? new String[0] : slotLabels;
        }

        static DayForecast fromJson(JSONObject json) throws JSONException {
            return new DayForecast(
                    json.optString("dayLabel", ""),
                    json.optString("date", ""),
                    json.optInt("tempMin", 0),
                    json.optInt("tempMax", 0),
                    json.optString("condition", ""),
                    json.optString("windDir", ""),
                    json.optInt("windSpeed", 0),
                    json.optString("iconEmoji", ""),
                    json.optString("fullDate", ""),
                    intArray(json, "slotTemps"),
                    stringArray(json, "slotConditions"),
                    intArray(json, "slotWinds"),
                    stringArray(json, "slotWindDirs"),
                    stringArray(json, "slotLabels")
            );
        }

        private static int[] intArray(JSONObject json, String key) throws JSONException {
            JSONArray arr = json.optJSONArray(key);
            if (arr == null) return null;
            int[] out = new int[arr.length()];
            for (int i = 0; i < arr.length(); i++) out[i] = arr.getInt(i);
            return out;
        }

        private static String[] stringArray(JSONObject json, String key) throws JSONException {
            JSONArray arr = json.optJSONArray(key);
            if (arr == null) return null;
            String[] out = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) out[i] = arr.optString(i, "");
            return out;
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("dayLabel", dayLabel);
            json.put("date", date);
            json.put("fullDate", fullDate);
            json.put("tempMin", tempMin);
            json.put("tempMax", tempMax);
            json.put("condition", condition);
            json.put("windDir", windDir);
            json.put("windSpeed", windSpeed);
            json.put("iconEmoji", iconEmoji);
            JSONArray temps = new JSONArray();
            for (int v : slotTemps) temps.put(v);
            json.put("slotTemps", temps);
            JSONArray conds = new JSONArray();
            for (String v : slotConditions) conds.put(v);
            json.put("slotConditions", conds);
            JSONArray winds = new JSONArray();
            for (int v : slotWinds) winds.put(v);
            json.put("slotWinds", winds);
            JSONArray dirs = new JSONArray();
            for (String v : slotWindDirs) dirs.put(v);
            json.put("slotWindDirs", dirs);
            JSONArray labels = new JSONArray();
            for (String v : slotLabels) labels.put(v);
            json.put("slotLabels", labels);
            return json;
        }

        // Форматированная строка температуры: "+12° / +5°"
        String tempLabel() {
            return formatTemp(tempMax) + "\u00B0 / " + formatTemp(tempMin) + "\u00B0";
        }

        // Короткая строка: "5 м/с, ЮЗ"
        String windLabel() {
            if (windSpeed <= 0 && windDir.isEmpty()) return "";
            String dir = windDir.isEmpty() ? "" : ", " + windDir;
            return windSpeed + " м/с" + dir;
        }

        String slotTempLabel(int index) {
            if (index < 0 || index >= slotTemps.length) return "";
            int v = slotTemps[index];
            if (v == NO_VALUE) return "—";
            return formatTemp(v) + "\u00B0";
        }

        String slotLabel(int index) {
            if (index < 0 || index >= slotLabels.length) return "";
            return slotLabels[index];
        }

        private static String formatTemp(int value) {
            if (value == NO_VALUE) return "—";
            if (value > 0) return "+" + value;
            if (value == 0) return "0";
            return String.valueOf(value);
        }

        // Маппинг текстового описания погоды → эмодзи
        static String iconEmojiForCondition(String condition) {
            if (condition == null) return "\u2600\uFE0F";
            String lower = condition.toLowerCase();
            if (lower.contains("гроз")) return "\u26C8\uFE0F";         // гроза
            if (lower.contains("снег") || lower.contains("снеж")) return "\uD83C\uDF28\uFE0F"; // снег
            if (lower.contains("дожд") || lower.contains("ливн")) return "\uD83C\uDF27\uFE0F"; // дождь
            if (lower.contains("туман") || lower.contains("мгла")) return "\uD83C\uDF2B\uFE0F"; // туман
            if (lower.contains("облач") || lower.contains("пасмур")
                    || lower.contains("обл")) return "\u2601\uFE0F";     // облачно
            if (lower.contains("ясн") || lower.contains("солн")) return "\u2600\uFE0F"; // ясно
            if (lower.contains("перемен") || lower.contains("малообл")
                    || lower.contains("неб")) return "\uD83C\uDF24\uFE0F"; // переменная облачность
            return "\u2601\uFE0F"; // default: облачно
        }
    }
}
