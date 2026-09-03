package com.kirix.schedule;

/* compiled from: ScheduleWidgetProvider.java */
/* loaded from: classes2.dex */
final class ThemeRes {
    ThemeRes() {
    }

    static int getWidgetBg(String theme) {
        return "light_glass".equals(theme) ? R.drawable.widget_card_bg_light_glass : "dark".equals(theme) ? R.drawable.widget_card_bg_dark : "light".equals(theme) ? R.drawable.widget_card_bg_light : R.drawable.widget_card_bg_dark_glass;
    }

    static int getDayBg(String theme) {
        boolean isDark = "dark".equals(theme) || "dark_glass".equals(theme);
        return isDark ? R.drawable.widget_day_bg_dark : R.drawable.widget_day_bg_light;
    }

    static int getDaySelectedBg(String accent) {
        return "mint".equals(accent) ? R.drawable.widget_day_selected_bg_mint : "emerald".equals(accent) ? R.drawable.widget_day_selected_bg_emerald : "red".equals(accent) ? R.drawable.widget_day_selected_bg_red : "orange".equals(accent) ? R.drawable.widget_day_selected_bg_orange : R.drawable.widget_day_selected_bg_indigo;
    }

    static int getDayTodayBg(String accent) {
        return "mint".equals(accent) ? R.drawable.widget_day_today_bg_mint : "emerald".equals(accent) ? R.drawable.widget_day_today_bg_emerald : "red".equals(accent) ? R.drawable.widget_day_today_bg_red : "orange".equals(accent) ? R.drawable.widget_day_today_bg_orange : R.drawable.widget_day_today_bg_indigo;
    }

    static int getBadgeBg(String accent) {
        return "mint".equals(accent) ? R.drawable.widget_badge_bg_mint : "emerald".equals(accent) ? R.drawable.widget_badge_bg_emerald : "red".equals(accent) ? R.drawable.widget_badge_bg_red : "orange".equals(accent) ? R.drawable.widget_badge_bg_orange : R.drawable.widget_badge_bg_indigo;
    }

    static int getTextColor(String theme) {
        boolean isDark = "dark".equals(theme) || "dark_glass".equals(theme);
        return isDark ? -460036 : -15722456;
    }

    static int getMutedColor(String theme) {
        boolean isDark = "dark".equals(theme) || "dark_glass".equals(theme);
        return isDark ? -7035976 : -10063739;
    }

    static int getDividerColor(String theme) {
        boolean isDark = "dark".equals(theme) || "dark_glass".equals(theme);
        return isDark ? -14800581 : -1906448;
    }

    static int getAccentColorHex(String accent) {
        if ("mint".equals(accent)) {
            return -15681151;
        }
        if ("emerald".equals(accent)) {
            return -16411031;
        }
        if ("red".equals(accent)) {
            return -770210;
        }
        return "orange".equals(accent) ? -680437 : -10262799;
    }
}
