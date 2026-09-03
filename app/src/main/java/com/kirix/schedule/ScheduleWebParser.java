package com.kirix.schedule;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONException;
import org.json.JSONObject;

// Резервный парсер расписания через скрытый WebView, если API недоступно (только для групп).
final class ScheduleWebParser {
    private static final String SCHEDULE_URL = "https://rasp.ural-campus.ru/schedule?org=college";
    private static final long TIMEOUT_MS = 45_000L;
    private static final String SCRIPT = """
            (function () {
                const GROUP = __GROUP__;
                const DAY_NAMES = [
                    "воскресенье",
                    "понедельник",
                    "вторник",
                    "среда",
                    "четверг",
                    "пятница",
                    "суббота"
                ];
                const SHORT_DAY_NAMES = ["вс", "пн", "вт", "ср", "чт", "пт", "сб"];

                function norm(value) {
                    return String(value || "")
                        .trim()
                        .toLowerCase()
                        .replace(/ё/g, "е")
                        .replace(/\\s+/g, "");
                }

                function text(el) {
                    return el ? String(el.innerText || el.textContent || "").trim() : "";
                }

                function sleep(ms) {
                    return new Promise((resolve) => setTimeout(resolve, ms));
                }

                function dateTokens(date) {
                    const day = String(date.getDate());
                    const month = String(date.getMonth() + 1);
                    const paddedDay = day.padStart(2, "0");
                    const paddedMonth = month.padStart(2, "0");
                    const year = String(date.getFullYear());
                    const shortYear = year.slice(2);
                    return [
                        day + "." + month,
                        paddedDay + "." + paddedMonth,
                        day + "." + month + "." + year,
                        paddedDay + "." + paddedMonth + "." + year,
                        day + "." + month + "." + shortYear,
                        paddedDay + "." + paddedMonth + "." + shortYear,
                        year + "-" + paddedMonth + "-" + paddedDay,
                        day + "/" + month,
                        paddedDay + "/" + paddedMonth
                    ];
                }

                function isTodayCard(card, date) {
                    const headerText = [
                        text(card.querySelector(".day-of-week")),
                        text(card.querySelector(".day-date")),
                        text(card.querySelector(".date")),
                        text(card.querySelector(".schedule-date"))
                    ].filter(Boolean).join(" ");
                    const header = headerText || text(card);
                    const normalizedHeader = norm(header);
                    const fullName = DAY_NAMES[date.getDay()];
                    const shortName = SHORT_DAY_NAMES[date.getDay()];
                    if (normalizedHeader.includes(fullName) || normalizedHeader.includes(shortName)) {
                        return true;
                    }
                    const lowerCardText = text(card).toLowerCase();
                    return dateTokens(date).some((token) => lowerCardText.includes(token));
                }

                async function waitFor(check, timeoutMs) {
                    const started = Date.now();
                    while (Date.now() - started < timeoutMs) {
                        const result = check();
                        if (result) return result;
                        await sleep(250);
                    }
                    throw new Error("Сайт не отдал данные вовремя");
                }

                function dispatchInput(input) {
                    input.dispatchEvent(new Event("input", { bubbles: true }));
                    input.dispatchEvent(new Event("change", { bubbles: true }));
                    input.dispatchEvent(new KeyboardEvent("keyup", { bubbles: true, key: "Enter" }));
                }

                function findVisibleOptions() {
                    return Array.from(document.querySelectorAll(".custom-select-option"))
                        .filter((option) => !String(option.className || "").includes("hidden"))
                        .filter((option) => text(option));
                }

                function loadButton() {
                    const direct = document.querySelector(".load-button");
                    if (direct) return direct;
                    return Array.from(document.querySelectorAll("button, [role='button'], input[type='submit']"))
                        .find((button) => /загрузить\\s+расписание/i.test(text(button))
                            || String(button.type || "").toLowerCase() === "submit");
                }

                function detailsFor(lesson) {
                    const details = {};
                    for (const item of lesson.querySelectorAll(".lesson-detail-item-modern")) {
                        const label = norm(text(item.querySelector(".detail-label-modern")));
                        const value = text(item.querySelector(".detail-value-modern"));
                        if (label.includes("преподав")) details.teacher = value;
                        if (label.includes("аудитор") || label.includes("кабинет")) details.room = value;
                        if (label.includes("адрес")) details.address = value;
                    }
                    return details;
                }

                async function run() {
                    const select = await waitFor(() => document.querySelector(".custom-select-input"), 30000);
                    select.click();
                    await sleep(400);

                    const search = document.querySelector(".custom-select-search input");
                    if (search) {
                        search.focus();
                        search.value = GROUP;
                        dispatchInput(search);
                        await sleep(600);
                    }

                    const target = norm(GROUP);
                    const options = findVisibleOptions();
                    const chosen = options.find((option) => norm(text(option)) === target)
                        || options.find((option) => norm(text(option)).includes(target));
                    if (!chosen) {
                        throw new Error("Группа не найдена: " + GROUP);
                    }

                    const realGroup = text(chosen);
                    chosen.click();
                    await sleep(350);

                    const button = loadButton();
                    if (!button) {
                        throw new Error("Кнопка загрузки расписания не найдена");
                    }
                    button.click();

                    await waitFor(() => document.querySelectorAll(".day-card").length > 0
                        || /расписание\\s+не\\s+найдено/i.test(text(document.body)), 30000);

                    const now = new Date();
                    const expectedDay = DAY_NAMES[now.getDay()];
                    const cards = Array.from(document.querySelectorAll(".day-card"));
                    const today = cards.find((card) => isTodayCard(card, now));
                    if (!today) {
                        const visibleDays = cards
                            .map((card) => text(card.querySelector(".day-of-week")) || text(card).split("\\n")[0])
                            .filter(Boolean)
                            .join(", ");
                        throw new Error("Не найдена карточка сегодняшнего дня. Дни на сайте: " + visibleDays);
                    }

                    const lessons = Array.from(today.querySelectorAll(".lesson-card-modern")).map((lesson, index) => {
                        const details = detailsFor(lesson);
                        const start = text(lesson.querySelector(".pair-time-start"));
                        const end = text(lesson.querySelector(".pair-time-end"));
                        const time = end ? start + "-" + end : start;
                        return {
                            number: text(lesson.querySelector(".pair-number")) || String(index + 1),
                            time: time,
                            subject: text(lesson.querySelector(".lesson-subject-name")) || "Без названия",
                            teacher: details.teacher || "",
                            room: details.room || ""
                        };
                    });

                    return {
                        group: realGroup,
                        dayName: text(today.querySelector(".day-of-week")) || expectedDay,
                        lessons: lessons,
                        updatedAtMillis: Date.now()
                    };
                }

                run()
                    .then((result) => ScheduleBridge.onResult(JSON.stringify(result)))
                    .catch((error) => ScheduleBridge.onError(String(error && error.message ? error.message : error)));
            })();
            """;

    interface Callback {
        void onSuccess(ScheduleData data, String rawJson);

        void onError(String message);
    }

    private ScheduleWebParser() {
    }

    static void fetchToday(Context context, String group, Callback callback) {
        new Handler(Looper.getMainLooper()).post(() ->
                new ParserSession(context.getApplicationContext(), group, callback).start());
    }

    private static String buildScript(String group) {
        return SCRIPT.replace("__GROUP__", JSONObject.quote(group));
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private static final class ParserSession {
        private final Context context;
        private final String group;
        private final Callback callback;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private WebView webView;
        private boolean injected;

        private final Runnable timeout = () -> fail("Таймаут загрузки расписания");

        ParserSession(Context context, String group, Callback callback) {
            this.context = context;
            this.group = group;
            this.callback = callback;
        }

        void start() {
            try {
                webView = new WebView(context);
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setBlockNetworkImage(true);
                settings.setLoadsImagesAutomatically(false);
                webView.addJavascriptInterface(new Bridge(this), "ScheduleBridge");
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        if (!injected) {
                            injected = true;
                            view.evaluateJavascript(buildScript(group), null);
                        }
                    }
                });
                handler.postDelayed(timeout, TIMEOUT_MS);
                webView.loadUrl(SCHEDULE_URL);
            } catch (Throwable error) {
                fail("Не удалось запустить скрытый браузер: " + error.getMessage());
            }
        }

        void success(String rawJson) {
            handler.post(() -> {
                if (!finished.compareAndSet(false, true)) {
                    return;
                }
                try {
                    ScheduleData data = ScheduleData.fromJson(rawJson);
                    cleanup();
                    callback.onSuccess(data, rawJson);
                } catch (JSONException e) {
                    cleanup();
                    callback.onError("Ошибка разбора данных сайта");
                }
            });
        }

        void fail(String message) {
            handler.post(() -> {
                if (!finished.compareAndSet(false, true)) {
                    return;
                }
                cleanup();
                callback.onError(message == null || message.trim().isEmpty()
                        ? "Не удалось получить расписание" : message);
            });
        }

        private void cleanup() {
            handler.removeCallbacks(timeout);
            if (webView != null) {
                webView.stopLoading();
                webView.removeJavascriptInterface("ScheduleBridge");
                webView.destroy();
                webView = null;
            }
        }
    }

    static final class Bridge {
        private final ParserSession session;

        Bridge(ParserSession session) {
            this.session = session;
        }

        @JavascriptInterface
        public void onResult(String rawJson) {
            session.success(rawJson);
        }

        @JavascriptInterface
        public void onError(String message) {
            session.fail(message);
        }
    }
}
