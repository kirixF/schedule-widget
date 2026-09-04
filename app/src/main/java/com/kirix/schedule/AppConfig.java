package com.kirix.schedule;

// Централизованная конфигурация endpoints. При смене домена править только здесь.
final class AppConfig {
    private AppConfig() {
    }

    static final String SCHEDULE_API_BASE = "https://rasp.ural-campus.ru/api";
    static final String SCHEDULE_ORG = "college";
    static final String SCHEDULE_SITE_URL = "https://rasp.ural-campus.ru/schedule?org=college";

    static final String DEFAULT_CITY_SLUG = "chelyabinsk-4565";
    static final String DEFAULT_CITY_NAME = "Челябинск";

    static String groupsUrl() {
        return SCHEDULE_API_BASE + "/schedule/" + SCHEDULE_ORG + "/groups";
    }

    static String teachersUrl() {
        return SCHEDULE_API_BASE + "/schedule/" + SCHEDULE_ORG + "/teachers";
    }

    static String groupScheduleUrl(String guid, String dateBegin, String dateEnd) {
        return SCHEDULE_API_BASE + "/schedule/" + SCHEDULE_ORG + "/group/" + guid
                + "?datebegin=" + dateBegin + "&dateend=" + dateEnd;
    }

    static String teacherScheduleUrl(String guid, String dateBegin, String dateEnd) {
        return SCHEDULE_API_BASE + "/schedule/" + SCHEDULE_ORG + "/teacher/" + guid
                + "?datebegin=" + dateBegin + "&dateend=" + dateEnd;
    }
}
