package com.kirix.schedule;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Парсер прогноза Gismeteo без WebView. Страница /3-days/ отдаёт агрегаты и полусутки
// в серверном HTML, страница /now/ — текущую погоду (ощущается, влажность, давление).
final class GismeteoApiClient {
    private static final Pattern CITY_SLUG_P = Pattern.compile("(?:weather-)?([a-z0-9]+(?:-[a-z0-9]+)*-\\d+)");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gismeteo-fetch");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final Pattern ITEM_TEXT_P = Pattern.compile("<a class=\"row-item[^>]*>\\s*([^<]+?)\\s*</a>");
    private static final Pattern SLOT_LABEL_P = Pattern.compile("row-item-tod\">\\s*([^<]+?)\\s*</div>");
    private static final Pattern TEMP_VALUE_P = Pattern.compile("temperature-value\\s+value=\"(-?\\d+)\"");
    private static final Pattern TOOLTIP_P = Pattern.compile("data-tooltip=\"([^\"]*)\"");
    private static final Pattern ICON_CODE_P = Pattern.compile("use href=\"#([a-z0-9_]+)\"");
    private static final Pattern WIND_SPEED_P = Pattern.compile("wind-speed[\\s\\S]{0,300}?<speed-value value=\"(\\d+)\"");
    private static final Pattern WIND_DIR_P = Pattern.compile("wind-direction-degree-\\d+\"[\\s\\S]*?</svg>\\s*</div>\\s*([А-Яа-яЁё]{1,5})\\s*<");
    private static final Pattern FEELS_P = Pattern.compile("ощущени[\\s\\S]{0,160}?<temperature-value\\s+value=\"(-?\\d+)\"");
    private static final Pattern NOW_DESC_P = Pattern.compile("<div class=\"now-desc\">\\s*([^<]{0,160}?)\\s*</div>");
    private static final Pattern ANY_SPEED_P = Pattern.compile("<speed-value\\s+value=\"(\\d+)\"");
    private static final Pattern WIND_NOW_P = Pattern.compile("ветер[\\s\\S]{0,120}?<speed-value\\s+value=\"(\\d+)\"");
    private static final Pattern PRESSURE_P = Pattern.compile("pressure-value\\s+value=\"(\\d+)\"\\s+from-unit=\"mmhg\"");
    private static final Pattern HUMIDITY_P = Pattern.compile("лажност[\\s\\S]{0,400}?<div class=\"item-value\">(\\d+)</div>");

    interface Callback {
        void onSuccess(GismeteoWeatherData data, String rawJson);

        void onError(String message);
    }

    private GismeteoApiClient() {
    }

    static String forecastUrl(String citySlug) {
        return "https://www.gismeteo.ru/weather-" + citySlug + "/3-days/";
    }

    static String nowUrl(String citySlug) {
        return "https://www.gismeteo.ru/weather-" + citySlug + "/now/";
    }

    // "https://www.gismeteo.ru/weather-yekaterinburg-11120/now/" → "yekaterinburg-11120"
    static String extractSlug(String input) {
        if (input == null) return null;
        Matcher m = CITY_SLUG_P.matcher(input.toLowerCase().trim());
        return m.find() ? m.group(1) : null;
    }

    // Асинхронный вызов: колбэки приходят на main thread.
    static void fetchForecast(Context context, Callback callback) {
        String citySlug = SchedulePrefs.getCitySlug(context);
        EXECUTOR.execute(() -> {
            try {
                GismeteoWeatherData data = fetchForecastSync(citySlug);
                String json = data.toJson();
                MAIN.post(() -> callback.onSuccess(data, json));
            } catch (Exception error) {
                String message = error.getMessage() == null || error.getMessage().trim().isEmpty()
                        ? "Не удалось получить прогноз погоды"
                        : error.getMessage();
                MAIN.post(() -> callback.onError(message));
            }
        });
    }

    // Блокирующий вызов для фоновых задач (JobService). Страница "сейчас" не критична.
    static GismeteoWeatherData fetchForecastSync(Context context) throws IOException {
        return fetchForecastSync(SchedulePrefs.getCitySlug(context));
    }

    static GismeteoWeatherData fetchForecastSync(String citySlug) throws IOException {
        String forecastHtml = fetchHtml(forecastUrl(citySlug));
        int[] current = new int[]{-9999, -9999, -1, -1, -1}; // temp, feels, humidity, pressure, wind
        String[] currentText = new String[]{"", "", ""};      // condition, windDir, icon
        try {
            parseNowPage(fetchHtml(nowUrl(citySlug)), current, currentText);
        } catch (Exception ignored) {
            // Текущая погода не критична — прогноз остаётся валидным.
        }
        GismeteoWeatherData data = parse(forecastHtml, current, currentText);
        return data;
    }

    private static String fetchHtml(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36");
        connection.setRequestProperty("Accept-Language", "ru,ru-RU;q=0.9");
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readAll(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new IOException("Gismeteo HTTP " + status);
        }
        return body;
    }

    private static void parseNowPage(String html, int[] values, String[] texts) {
        String nowBlock = section(html, "class=\"now-weather\"");
        Matcher temp = TEMP_VALUE_P.matcher(nowBlock);
        if (temp.find()) {
            values[0] = Integer.parseInt(temp.group(1));
        }
        String feelBlock = section(html, "class=\"now-feel\"");
        int feelsValue = firstInt(FEELS_P.matcher(feelBlock), TEMP_VALUE_P.matcher(feelBlock));
        if (feelsValue != Integer.MIN_VALUE) {
            values[1] = feelsValue;
        }
        String windBlock = section(html, "class=\"now-info\"");
        int windValue = firstInt(WIND_NOW_P.matcher(windBlock), ANY_SPEED_P.matcher(windBlock));
        if (windValue != Integer.MIN_VALUE) {
            values[4] = windValue;
        }
        Matcher pressure = PRESSURE_P.matcher(html);
        if (pressure.find()) {
            values[3] = Integer.parseInt(pressure.group(1));
        }
        Matcher humidity = HUMIDITY_P.matcher(html);
        if (humidity.find()) {
            values[2] = Integer.parseInt(humidity.group(1));
        }
        Matcher desc = NOW_DESC_P.matcher(html);
        if (desc.find()) {
            texts[0] = unescape(desc.group(1));
        }
        Matcher icon = ICON_CODE_P.matcher(nowBlock);
        if (icon.find()) {
            texts[2] = icon.group(1);
        }
    }

    // Первое совпадение из двух паттернов; MIN_VALUE — ничего не найдено.
    private static int firstInt(Matcher primary, Matcher fallback) {
        if (primary.find()) {
            return Integer.parseInt(primary.group(1));
        }
        if (fallback.find()) {
            return Integer.parseInt(fallback.group(1));
        }
        return Integer.MIN_VALUE;
    }

    // Секция страницы от маркера до следующего блока widget-row / data-row.
    private static String section(String html, String marker) {
        int start = html.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int from = start + marker.length();
        int endData = html.indexOf("data-row=\"", from);
        int endRow = html.indexOf("<div class=\"widget-row", from);
        int end = -1;
        if (endData >= 0) end = endData;
        if (endRow >= 0 && (end < 0 || endRow < end)) end = endRow;
        if (end < 0) end = html.length();
        return html.substring(start, end);
    }

    static GismeteoWeatherData parse(String html, int[] current, String[] currentText) throws IOException {
        List<String> labels = new ArrayList<>();
        List<String> dates = new ArrayList<>();
        Matcher dateMatcher = ITEM_TEXT_P.matcher(section(html, "widget-row-tod-date"));
        while (dateMatcher.find()) {
            String[] parts = unescape(dateMatcher.group(1)).split("\\s*,\\s*", 2);
            if (parts.length < 2) continue;
            labels.add(parts[0]);
            dates.add(shortDate(parts[1]));
        }

        List<String> slotLabels = allStrings(section(html, "widget-row-datetime-time"), SLOT_LABEL_P);
        List<Integer> temps = allInts(section(html, "data-row=\"temperature-air\""), TEMP_VALUE_P);
        List<String[]> icons = iconPairs(section(html, "data-row=\"icon-tooltip\""));
        List<String[]> wind = windPairs(section(html, "data-row=\"wind\""));

        int dayCount = dates.size();
        if (dayCount == 0 || temps.size() < dayCount) {
            throw new IOException("Не удалось найти прогноз на странице");
        }
        int slotsPerDay = Math.max(1, temps.size() / dayCount);

        List<GismeteoWeatherData.DayForecast> out = new ArrayList<>();
        for (int i = 0; i < dayCount && i < 7; i++) {
            int from = i * slotsPerDay;
            int to = Math.min(from + slotsPerDay, temps.size());
            Integer maxT = null;
            Integer minT = null;
            int[] slotTemps = new int[to - from];
            String[] slotConds = new String[to - from];
            int[] slotWinds = new int[to - from];
            String[] slotDirs = new String[to - from];
            String[] daySlotLabels = new String[to - from];
            for (int k = from; k < to; k++) {
                int idx = k - from;
                int v = temps.get(k);
                slotTemps[idx] = v;
                slotConds[idx] = k < icons.size() ? unescape(icons.get(k)[0]) : "";
                slotWinds[idx] = k < wind.size() ? Integer.parseInt(wind.get(k)[0]) : 0;
                slotDirs[idx] = k < wind.size() ? wind.get(k)[1] : "";
                daySlotLabels[idx] = idx < slotLabels.size() ? unescape(slotLabels.get(idx)) : "";
                if (maxT == null || v > maxT) maxT = v;
                if (minT == null || v < minT) minT = v;
            }
            if (maxT == null) maxT = 0;
            if (minT == null) minT = 0;

            int slot = Math.min(2, slotsPerDay - 1);
            String condition = slotConds[Math.min(slot, slotConds.length - 1)];
            int windSpeed = slotWinds[Math.min(slot, slotWinds.length - 1)];
            String windDir = slotDirs[Math.min(slot, slotDirs.length - 1)];

            out.add(new GismeteoWeatherData.DayForecast(
                    labels.get(i),
                    dates.get(i),
                    minT,
                    maxT,
                    condition,
                    windDir,
                    windSpeed,
                    condition.isEmpty() ? "\u2601\uFE0F" : GismeteoWeatherData.DayForecast.iconEmojiForCondition(condition),
                    slotTemps,
                    slotConds,
                    slotWinds,
                    slotDirs,
                    daySlotLabels
            ));
        }
        return new GismeteoWeatherData(out, System.currentTimeMillis(),
                current[0], current[1], current[2], current[3], current[4],
                currentText[1], currentText[0], currentText[2]);
    }

    private static List<String[]> iconPairs(String sec) {
        List<String[]> result = new ArrayList<>();
        for (String chunk : sec.split("<div class=\"row-item")) {
            Matcher tooltip = TOOLTIP_P.matcher(chunk);
            Matcher code = ICON_CODE_P.matcher(chunk);
            String tip = tooltip.find() ? tooltip.group(1) : "";
            String ic = code.find() ? code.group(1) : "";
            if (!ic.isEmpty()) {
                result.add(new String[]{tip, ic});
            }
        }
        return result;
    }

    // Пары "скорость, направление" по слотам; при штиле блок направления отсутствует.
    private static List<String[]> windPairs(String sec) {
        List<String[]> result = new ArrayList<>();
        for (String chunk : sec.split("<div class=\"row-item\">")) {
            Matcher speed = WIND_SPEED_P.matcher(chunk);
            if (!speed.find()) continue;
            Matcher dir = WIND_DIR_P.matcher(chunk);
            result.add(new String[]{speed.group(1), dir.find() ? dir.group(1) : ""});
        }
        return result;
    }

    private static List<Integer> allInts(String sec, Pattern pattern) {
        List<Integer> values = new ArrayList<>();
        Matcher m = pattern.matcher(sec);
        while (m.find()) {
            try {
                values.add(Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private static List<String> allStrings(String sec, Pattern pattern) {
        List<String> values = new ArrayList<>();
        Matcher m = pattern.matcher(sec);
        while (m.find()) {
            values.add(m.group(1));
        }
        return values;
    }

    private static final String[] MONTHS = {"янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"};

    // "24 августа" → "24.08"
    private static String shortDate(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 2) return raw;
        String monthKey = parts[1].toLowerCase().substring(0, Math.min(3, parts[1].length()));
        int m = -1;
        for (int i = 0; i < MONTHS.length; i++) {
            if (MONTHS[i].equals(monthKey)) {
                m = i;
                break;
            }
        }
        String month = m >= 0 ? String.format("%02d", m + 1) : parts[1];
        return parts[0] + "." + month;
    }

    private static String unescape(String text) {
        if (text == null) return "";
        return text.replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line).append('\n');
            }
        }
        return body.toString();
    }
}
