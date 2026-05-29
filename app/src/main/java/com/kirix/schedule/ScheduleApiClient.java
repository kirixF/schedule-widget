package com.kirix.schedule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ScheduleApiClient {
    private static final String BASE_URL = "https://rasp.ural-campus.ru/api";
    private static final String ORG = "college";
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT);

    private ScheduleApiClient() {
    }

    static Result fetchToday(String groupQuery) throws IOException, JSONException {
        String query = groupQuery == null ? "" : groupQuery.trim();
        if (query.isEmpty()) {
            throw new IOException("Группа не указана");
        }

        Group group = findGroup(query);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        String date = API_DATE.format(today);
        String url = BASE_URL + "/schedule/" + ORG + "/group/" + group.guid
                + "?datebegin=" + date + "&dateend=" + date;

        JSONObject root = new JSONObject(get(url));
        JSONArray schedule = extractArray(root, "schedule");
        JSONArray lessons = new JSONArray();
        String dayName = today.getDayOfWeek().toString();
        if (schedule.length() > 0) {
            JSONObject day = schedule.getJSONObject(0);
            dayName = day.optString("day", day.optString("date", dayName));
            JSONArray rawLessons = day.optJSONArray("lessons");
            if (rawLessons != null) {
                for (int i = 0; i < rawLessons.length(); i++) {
                    JSONObject raw = rawLessons.getJSONObject(i);
                    lessons.put(mapLesson(raw, i));
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("group", group.name);
        result.put("dayName", dayName);
        result.put("lessons", lessons);
        result.put("updatedAtMillis", System.currentTimeMillis());
        return new Result(ScheduleData.fromJson(result.toString()), result.toString());
    }

    private static Group findGroup(String query) throws IOException, JSONException {
        JSONObject root = new JSONObject(get(BASE_URL + "/schedule/" + ORG + "/groups"));
        JSONArray groups = extractArray(root, "groups");
        String target = normalize(query);
        Group best = null;

        for (int i = 0; i < groups.length(); i++) {
            JSONObject item = groups.getJSONObject(i);
            String guid = item.optString("guid");
            String name = item.optString("name");
            if (guid.isEmpty() || name.isEmpty()) {
                continue;
            }

            String normalizedName = normalize(name);
            boolean exactPart = false;
            for (String part : name.split(",")) {
                if (normalize(part).equals(target)) {
                    exactPart = true;
                    break;
                }
            }

            if (normalizedName.equals(target) || exactPart) {
                return new Group(guid, name);
            }

            if (normalizedName.contains(target) || target.contains(normalizedName)) {
                if (best == null || name.length() < best.name.length()) {
                    best = new Group(guid, name);
                }
            }
        }

        if (best != null) {
            return best;
        }
        throw new IOException("Группа не найдена: " + query);
    }

    private static JSONObject mapLesson(JSONObject raw, int index) throws JSONException {
        JSONObject result = new JSONObject();
        JSONObject time = raw.optJSONObject("timewindow");
        String number = time == null ? "" : time.optString("description");
        String begin = time == null ? "" : trimSeconds(time.optString("timebegin"));
        String end = time == null ? "" : trimSeconds(time.optString("timeend"));
        String room = raw.optString("classroom");
        String building = raw.optString("buildings");

        if (number.isEmpty()) {
            number = (index + 1) + " пара";
        }
        if (!building.isEmpty()) {
            room = room.isEmpty() ? building : room + ", " + building;
        }

        result.put("number", number);
        result.put("time", begin.isEmpty() || end.isEmpty() ? begin : begin + "-" + end);
        result.put("subject", raw.optString("name", "Без названия"));
        result.put("teacher", join(raw.optJSONArray("addition")));
        result.put("room", room);
        return result;
    }

    private static JSONArray extractArray(JSONObject root, String key) throws JSONException, IOException {
        if (root.has(key) && root.optJSONArray(key) != null) {
            return root.getJSONArray(key);
        }
        Object message = root.opt("message");
        if (message instanceof JSONObject) {
            JSONObject messageObject = (JSONObject) message;
            if (messageObject.optJSONArray(key) != null) {
                return messageObject.getJSONArray(key);
            }
        }
        if (message instanceof JSONArray) {
            return (JSONArray) message;
        }
        String error = root.optString("error");
        if (!error.isEmpty()) {
            throw new IOException(error);
        }
        throw new IOException("API вернул неожиданный формат данных");
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ScheduleWidget/1.0 Android");

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readAll(stream);
        connection.disconnect();

        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + ": " + body);
        }
        return body;
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                .toLowerCase(Locale.forLanguageTag("ru"))
                .replace('ё', 'е')
                .replace('–', '-')
                .replace('—', '-')
                .replace(" ", "");
    }

    private static String trimSeconds(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.trim();
        int lastColon = clean.lastIndexOf(':');
        if (lastColon > 1 && clean.length() - lastColon == 3) {
            return clean.substring(0, lastColon);
        }
        return clean;
    }

    private static String join(JSONArray array) {
        if (array == null || array.length() == 0) {
            return "";
        }
        List<String> items = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i);
            if (!value.isEmpty()) {
                items.add(value);
            }
        }
        return String.join(", ", items);
    }

    static final class Result {
        final ScheduleData data;
        final String rawJson;

        Result(ScheduleData data, String rawJson) {
            this.data = data;
            this.rawJson = rawJson;
        }
    }

    private static final class Group {
        final String guid;
        final String name;

        Group(String guid, String name) {
            this.guid = guid;
            this.name = name;
        }
    }
}
