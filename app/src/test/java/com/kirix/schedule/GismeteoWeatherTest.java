package com.kirix.schedule;

import org.junit.Test;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class GismeteoWeatherTest {

    private static final int[] EMPTY_CURRENT = {-9999, -9999, -1, -1, -1};
    private static final String[] EMPTY_TEXT = {"", "", ""};

    @Test
    public void testParseThreeDays() throws Exception {
        String html = buildHtml(true, true,
                new String[]{"Чт, 4 сентября", "Пт, 5 сентября", "Сб, 6 сентября"},
                new int[]{10, 15, 11, 16, 12, 17});
        GismeteoWeatherData data = GismeteoApiClient.parse(html, EMPTY_CURRENT.clone(), EMPTY_TEXT.clone());
        assertEquals(3, data.days.size());
        assertEquals("04.09", data.days.get(0).date);
        assertEquals(10, data.days.get(0).tempMin);
        assertEquals(15, data.days.get(0).tempMax);
        assertEquals("05.09", data.days.get(1).date);
        assertEquals(2, data.days.get(0).slotTemps.length);
        assertEquals(3, data.days.get(0).windSpeed);
        assertFalse(data.days.get(0).fullDate.isEmpty());
    }

    @Test(expected = java.io.IOException.class)
    public void testParseEmptyHtml() throws Exception {
        GismeteoApiClient.parse("   ", EMPTY_CURRENT.clone(), EMPTY_TEXT.clone());
    }

    @Test(expected = java.io.IOException.class)
    public void testParseNoDates() throws Exception {
        StringBuilder h = new StringBuilder();
        h.append("<div class=\"data-row\" data-row=\"temperature-air\">");
        h.append("<temperature-value value=\"10\"/>");
        h.append("</div>");
        GismeteoApiClient.parse(h.toString(), EMPTY_CURRENT.clone(), EMPTY_TEXT.clone());
    }

    @Test(expected = java.io.IOException.class)
    public void testParseNoTemps() throws Exception {
        String html = buildHtml(true, true,
                new String[]{"Чт, 4 сентября"},
                new int[]{});
        GismeteoApiClient.parse(html, EMPTY_CURRENT.clone(), EMPTY_TEXT.clone());
    }

    @Test
    public void testParseMissingWindAndIcons() throws Exception {
        String html = buildHtml(false, false,
                new String[]{"Чт, 4 сентября", "Пт, 5 сентября"},
                new int[]{10, 15, 11, 16});
        GismeteoWeatherData data = GismeteoApiClient.parse(html, EMPTY_CURRENT.clone(), EMPTY_TEXT.clone());
        assertEquals(2, data.days.size());
        assertEquals(0, data.days.get(0).windSpeed);
        assertEquals("", data.days.get(0).condition);
    }
    @Test
    public void testMergeNewWinsAndCapsAtSeven() {
        LocalDate today = LocalDate.now();
        List<GismeteoWeatherData.DayForecast> prevDays = new ArrayList<>();
        for (int i = 0; i < 5; i++) prevDays.add(day(today.plusDays(i), "Старо"));
        GismeteoWeatherData prev = new GismeteoWeatherData(prevDays, 1L, -9999, -9999, -1, -1, -1, "", "", "");
        List<GismeteoWeatherData.DayForecast> newDays = new ArrayList<>();
        for (int i = 3; i < 8; i++) newDays.add(day(today.plusDays(i), "Ново"));
        GismeteoWeatherData fresh = new GismeteoWeatherData(newDays, 2L, 5, 3, 60, 745, 2, "С", "Ясно", "");
        GismeteoWeatherData merged = fresh.mergePrevious(prev);
        assertEquals(7, merged.days.size());
        assertEquals("Ново", merged.dayByDate(shortOf(today.plusDays(3))).condition);
        assertEquals(5, merged.currentTemp);
        for (int i = 1; i < merged.days.size(); i++) {
            assertTrue(merged.days.get(i - 1).fullDate.compareTo(merged.days.get(i).fullDate) <= 0);
        }
    }

    @Test
    public void testMergeDropsPastDays() {
        LocalDate today = LocalDate.now();
        List<GismeteoWeatherData.DayForecast> prevDays = new ArrayList<>();
        prevDays.add(day(today.minusDays(9), "Старье"));
        prevDays.add(day(today.plusDays(1), "Живо"));
        GismeteoWeatherData prev = new GismeteoWeatherData(prevDays, 1L, -9999, -9999, -1, -1, -1, "", "", "");
        GismeteoWeatherData fresh = new GismeteoWeatherData(new ArrayList<GismeteoWeatherData.DayForecast>(), 2L, -9999, -9999, -1, -1, -1, "", "", "");
        GismeteoWeatherData merged = fresh.mergePrevious(prev);
        assertEquals(1, merged.days.size());
        assertEquals("Живо", merged.days.get(0).condition);
    }

    @Test
    public void testMergeKeepsPreviousCurrentBlock() {
        List<GismeteoWeatherData.DayForecast> prevDays = new ArrayList<>();
        prevDays.add(day(LocalDate.now().plusDays(1), "Держится"));
        GismeteoWeatherData prev = new GismeteoWeatherData(prevDays, 1L, 7, 5, 55, 750, 4, "Ю", "Облачно", "");
        GismeteoWeatherData fresh = new GismeteoWeatherData(new ArrayList<GismeteoWeatherData.DayForecast>(), 2L, -9999, -9999, -1, -1, -1, "", "", "");
        GismeteoWeatherData merged = fresh.mergePrevious(prev);
        assertEquals(7, merged.currentTemp);
        assertEquals("Ю", merged.windDirNow);
    }

    @Test
    public void testJsonRoundTripKeepsFullDate() throws Exception {
        LocalDate day = LocalDate.now().plusDays(2);
        List<GismeteoWeatherData.DayForecast> days = new ArrayList<>();
        days.add(day(day, "Круг"));
        GismeteoWeatherData data = new GismeteoWeatherData(days, 9L, 1, 2, 3, 4, 5, "В", "Дождь", "");
        GismeteoWeatherData back = GismeteoWeatherData.fromJson(data.toJson());
        assertEquals(1, back.days.size());
        assertEquals(day.toString(), back.days.get(0).fullDate);
        assertEquals("Круг", back.days.get(0).condition);
    }

    private static GismeteoWeatherData.DayForecast day(LocalDate date, String condition) {
        String shortDate = String.format("%02d.%02d", date.getDayOfMonth(), date.getMonthValue());
        return new GismeteoWeatherData.DayForecast(
                "X", shortDate, 1, 5, condition, "С", 2, "",
                date.toString(), new int[]{1, 5}, new String[]{condition, condition},
                new int[]{2, 2}, new String[]{"С", "С"}, new String[]{"a", "b"});
    }

    private static String shortOf(LocalDate date) {
        return String.format("%02d.%02d", date.getDayOfMonth(), date.getMonthValue());
    }

    private static String buildHtml(boolean withWind, boolean withIcons, String[] titles, int[] temps) {
        StringBuilder h = new StringBuilder();
        h.append("<div class=\"widget-row widget-row-tod-date\">");
        for (String t : titles) h.append("<a class=\"row-item\" href=\"#\">").append(t).append("</a>");
        h.append("</div>");
        h.append("<div class=\"widget-row widget-row-datetime-time\">");
        for (int t : temps) h.append("<div class=\"row-item-tod\">x</div>");
        h.append("</div>");
        h.append("<div class=\"data-row\" data-row=\"temperature-air\">");
        for (int t : temps) h.append("<temperature-value value=\"").append(t).append("\"/>");
        h.append("</div>");
        if (withIcons) {
            h.append("<div class=\"data-row\" data-row=\"icon-tooltip\">");
            for (int t : temps) h.append("<div class=\"row-item\" data-tooltip=\"Ясно\"><svg><use href=\"#sun\"/></svg></div>");
            h.append("</div>");
        }
        if (withWind) {
            h.append("<div class=\"data-row\" data-row=\"wind\">");
            for (int t : temps) h.append("<div class=\"row-item\"><div class=\"wind-speed\"><speed-value value=\"3\"/></div><div class=\"wind-direction-degree-90\"><svg></svg></div>С</div>");
            h.append("</div>");
        }
        return h.toString();
    }
}
