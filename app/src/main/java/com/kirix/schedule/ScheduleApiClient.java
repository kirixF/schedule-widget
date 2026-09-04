package com.kirix.schedule;

import android.os.Looper;
import android.util.Log;

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
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes2.dex */
final class ScheduleApiClient {
    private static final String TAG = "ScheduleApiClient";
    private static final String ORG = AppConfig.SCHEDULE_ORG;
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT);
    private static final DateTimeFormatter RESPONSE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);
    private static final Locale RU = Locale.forLanguageTag("ru");

    private ScheduleApiClient() {
    }

    interface ScheduleCallback {
        void onSuccess(ArchiveResult result);
        void onError(Exception e);
    }

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "schedule-api");
        t.setDaemon(true);
        return t;
    });

    // Безопасный вызов из UI: сеть уходит в фон, колбэк приходит туда же (без привязки к Looper).
    static void fetchAllAsync(String groupQuery, boolean isTeacher, ScheduleCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                callback.onSuccess(fetchAll(groupQuery, isTeacher));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    static void fetchTodayAsync(String groupQuery, boolean isTeacher, ScheduleCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                ArchiveResult all = fetchAll(groupQuery, isTeacher);
                LocalDate today = LocalDate.now(ZoneId.systemDefault());
                ScheduleData day = all.archive.getDay(today);
                if (day == null) {
                    day = ScheduleData.empty(groupQuery.trim(), isTeacher, today,
                            today.getDayOfWeek().getDisplayName(TextStyle.FULL, RU),
                            System.currentTimeMillis());
                }
                callback.onSuccess(new ArchiveResult(all.archive));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private static void assertWorkerThread() {
        if (Looper.getMainLooper() != null && Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("ScheduleApiClient: сетевой вызов запрещен в UI-потоке, используйте fetchAllAsync");
        }
    }

    // Кэш справочников: один HTTP-запрос на 24ч вместо запроса при каждом поиске.
    private static final long DIRECTORY_TTL_MS = 24L * 60 * 60 * 1000;
    private static volatile JSONArray cachedGroups;
    private static volatile long cachedGroupsAt;
    private static volatile JSONArray cachedTeachers;
    private static volatile long cachedTeachersAt;
    private static final Map<String, Group> GROUP_INDEX = new ConcurrentHashMap<>();
    private static final Map<String, Teacher> TEACHER_INDEX = new ConcurrentHashMap<>();

    static Result fetchToday(String groupQuery, boolean isTeacher) throws JSONException, IOException {
        assertWorkerThread();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        ArchiveResult result = fetchRange(groupQuery, isTeacher, today, today, false);
        ScheduleData todaySchedule = result.archive.getDay(today);
        if (todaySchedule == null) {
            todaySchedule = ScheduleData.empty(result.archive.group, isTeacher, today, dayName(today), result.archive.updatedAtMillis);
        }
        return new Result(todaySchedule, todaySchedule.toJsonObject().toString());
    }

    static ArchiveResult fetchAll(String groupQuery, boolean isTeacher) throws JSONException, IOException {
        assertWorkerThread();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return fetchRange(groupQuery, isTeacher, today.minusDays(7L), today.plusYears(1L), true);
    }

    private static ArchiveResult fetchRange(String groupQuery, boolean isTeacher, LocalDate dateBegin, LocalDate requestDateEnd, boolean stopAtLastPublishedDay) throws JSONException, IOException {
        String url;
        String name;
        LocalDate cachedThrough;
        LocalDate cachedThrough2;
        LocalDate cachedThrough3;
        int i;
        String query = groupQuery == null ? "" : groupQuery.trim();
        if (query.isEmpty()) {
            throw new IOException(isTeacher ? "Преподаватель не указан" : "Группа не указана");
        }
        if (isTeacher) {
            Teacher teacher = findTeacher(query);
            String name2 = teacher.name;
            String url2 = AppConfig.teacherScheduleUrl(teacher.guid,
                    API_DATE.format(dateBegin), API_DATE.format(requestDateEnd));
            url = url2;
            name = name2;
        } else {
            Group group = findGroup(query);
            String name3 = group.name;
            url = AppConfig.groupScheduleUrl(group.guid,
                    API_DATE.format(dateBegin), API_DATE.format(requestDateEnd));
            name = name3;
        }
        JSONObject root = new JSONObject(get(url));
        JSONArray schedule = extractArray(root, "schedule");
        long updatedAt = System.currentTimeMillis();
        if (stopAtLastPublishedDay) {
            cachedThrough = LocalDate.now(ZoneId.systemDefault());
        } else {
            cachedThrough = requestDateEnd;
        }
        Map<String, ScheduleData> days = new TreeMap<>();
        int i2 = 0;
        while (i2 < schedule.length()) {
            JSONObject day = schedule.getJSONObject(i2);
            LocalDate date = parseResponseDate(day.optString("date"));
            if (date == null) {
                i = i2;
            } else {
                if (!date.isAfter(cachedThrough)) {
                    cachedThrough3 = cachedThrough;
                } else {
                    cachedThrough3 = date;
                }
                JSONArray lessons = new JSONArray();
                JSONArray rawLessons = day.optJSONArray("lessons");
                if (rawLessons != null) {
                    for (int lessonIndex = 0; lessonIndex < rawLessons.length(); lessonIndex++) {
                        lessons.put(mapLesson(rawLessons.getJSONObject(lessonIndex), lessonIndex));
                    }
                }
                i = i2;
                days.put(date.toString(), toScheduleData(name, date, day.optString("day"), lessons, isTeacher, updatedAt));
                cachedThrough = cachedThrough3;
            }
            i2 = i + 1;
        }
        if (!cachedThrough.isBefore(dateBegin)) {
            cachedThrough2 = cachedThrough;
        } else {
            cachedThrough2 = dateBegin;
        }
        for (LocalDate date2 = dateBegin; !date2.isAfter(cachedThrough2); date2 = date2.plusDays(1L)) {
            if (!days.containsKey(date2.toString())) {
                days.put(date2.toString(), ScheduleData.empty(name, isTeacher, date2, dayName(date2), updatedAt));
            }
        }
        ScheduleArchive archive = new ScheduleArchive(name, isTeacher, dateBegin, cachedThrough2, updatedAt, days);
        return new ArchiveResult(archive);
    }

    private static ScheduleData toScheduleData(String group, LocalDate date, String rawDayName, JSONArray lessons, boolean isTeacher, long updatedAt) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("group", group);
        result.put("isTeacher", isTeacher);
        result.put("dateKey", date.toString());
        result.put("dayName", rawDayName.trim().isEmpty() ? dayName(date) : rawDayName);
        result.put("lessons", lessons);
        result.put("updatedAtMillis", updatedAt);
        return ScheduleData.fromJsonObject(result);
    }

    private static LocalDate parseResponseDate(String rawDate) {
        try {
            return LocalDate.parse(rawDate, RESPONSE_DATE);
        } catch (DateTimeParseException e) {
            Log.w(TAG, "Bad date from API: " + rawDate, e);
            return null;
        }
    }

    private static String dayName(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, RU);
    }

    private static Group findGroup(String query) throws JSONException, IOException {
        String key = normalize(query);
        Group cached = GROUP_INDEX.get(key);
        if (cached != null) {
            return cached;
        }
        JSONArray groups = getCachedGroups();
        String strNormalize = key;
        Group best = null;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject item = groups.getJSONObject(i);
            String guid = item.optString("guid");
            String name = item.optString("name");
            if (!guid.isEmpty() && !name.isEmpty()) {
                String strNormalize2 = normalize(name);
                boolean exactPart = false;
                String[] strArrSplit = name.split(",");
                int length = strArrSplit.length;
                int i2 = 0;
                while (true) {
                    if (i2 >= length) {
                        break;
                    }
                    String part = strArrSplit[i2];
                    if (!normalize(part).equals(strNormalize)) {
                        i2++;
                    } else {
                        exactPart = true;
                        break;
                    }
                }
                if (strNormalize2.equals(strNormalize) || exactPart) {
                    Group found = new Group(guid, name);
                    GROUP_INDEX.put(key, found);
                    return found;
                }
                if ((strNormalize2.contains(strNormalize) || strNormalize.contains(strNormalize2)) && (best == null || name.length() < best.name.length())) {
                    best = new Group(guid, name);
                }
            }
        }
        if (best != null) {
            GROUP_INDEX.put(key, best);
            return best;
        }
        throw new IOException("Группа не найдена: " + query);
    }

    private static synchronized JSONArray getCachedGroups() throws JSONException, IOException {
        long now = System.currentTimeMillis();
        if (cachedGroups != null && now - cachedGroupsAt < DIRECTORY_TTL_MS) {
            return cachedGroups;
        }
        JSONObject root = new JSONObject(get(AppConfig.groupsUrl()));
        cachedGroups = extractArray(root, "groups");
        cachedGroupsAt = now;
        return cachedGroups;
    }

    private static synchronized JSONArray getCachedTeachers() throws JSONException, IOException {
        long now = System.currentTimeMillis();
        if (cachedTeachers != null && now - cachedTeachersAt < DIRECTORY_TTL_MS) {
            return cachedTeachers;
        }
        JSONObject root = new JSONObject(get(AppConfig.teachersUrl()));
        cachedTeachers = extractArray(root, "teachers");
        cachedTeachersAt = now;
        return cachedTeachers;
    }

    static void invalidateDirectoryCache() {
        cachedGroups = null;
        cachedTeachers = null;
        GROUP_INDEX.clear();
        TEACHER_INDEX.clear();
    }

    private static JSONObject mapLesson(JSONObject raw, int index) throws JSONException {
        JSONObject result = new JSONObject();
        JSONObject time = raw.optJSONObject("timewindow");
        String number = time == null ? "" : time.optString("description");
        String begin = time == null ? "" : trimSeconds(time.optString("timebegin"));
        String end = time != null ? trimSeconds(time.optString("timeend")) : "";
        String room = raw.optString("classroom");
        String building = raw.optString("buildings");
        if (number.isEmpty()) {
            number = (index + 1) + " пара";
        }
        if (!building.isEmpty()) {
            room = room.isEmpty() ? building : room + ", " + building;
        }
        result.put("number", number);
        result.put("time", (begin.isEmpty() || end.isEmpty()) ? begin : begin + "-" + end);
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
        assertWorkerThread();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ScheduleWidget/2.0 Android");
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readAll(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + ": " + body);
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    private static final int MAX_JSON_CHARS = 2_000_000;

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                if (body.length() + n > MAX_JSON_CHARS) {
                    throw new IOException("Ответ API слишком большой");
                }
                body.append(buf, 0, n);
            }
            return body.toString();
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        // ё->е, длинное тире/короткое тире -> дефис.
        return value.trim().toLowerCase(RU)
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
        int firstColon = clean.indexOf(':');
        int lastColon = clean.lastIndexOf(':');
        if (firstColon != lastColon && lastColon > 1 && clean.length() - lastColon == 3) {
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

    static final class ArchiveResult {
        final ScheduleArchive archive;

        ArchiveResult(ScheduleArchive archive) {
            this.archive = archive;
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

    private static Teacher findTeacher(String query) throws JSONException, IOException {
        String key = normalize(query);
        Teacher cached = TEACHER_INDEX.get(key);
        if (cached != null) {
            return cached;
        }
        JSONArray teachers = getCachedTeachers();
        String strNormalize = key;
        Teacher best = null;
        for (int i = 0; i < teachers.length(); i++) {
            JSONObject item = teachers.getJSONObject(i);
            String guid = item.optString("guid");
            String name = item.optString("name");
            if (!guid.isEmpty() && !name.isEmpty()) {
                String strNormalize2 = normalize(name);
                boolean exactPart = false;
                String[] strArrSplit = name.split(" ");
                int length = strArrSplit.length;
                int i2 = 0;
                while (true) {
                    if (i2 >= length) {
                        break;
                    }
                    String part = strArrSplit[i2];
                    if (!normalize(part).equals(strNormalize)) {
                        i2++;
                    } else {
                        exactPart = true;
                        break;
                    }
                }
                if (strNormalize2.equals(strNormalize) || exactPart) {
                    Teacher found = new Teacher(guid, name);
                    TEACHER_INDEX.put(key, found);
                    return found;
                }
                if ((strNormalize2.contains(strNormalize) || strNormalize.contains(strNormalize2)) && (best == null || name.length() < best.name.length())) {
                    best = new Teacher(guid, name);
                }
            }
        }
        if (best != null) {
            TEACHER_INDEX.put(key, best);
            return best;
        }
        throw new IOException("Преподаватель не найден: " + query);
    }

    private static final class Teacher {
        final String guid;
        final String name;

        Teacher(String guid, String name) {
            this.guid = guid;
            this.name = name;
        }
    }
}
