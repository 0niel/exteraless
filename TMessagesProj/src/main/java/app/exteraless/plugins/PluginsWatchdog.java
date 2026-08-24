package app.exteraless.plugins;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Защита от плагина, который вешает или роняет приложение. Перенос
 * {@code com.exteragram.messenger.plugins.utils.PluginsWatchdog} exteraGram.
 *
 * <h3>Детект зависаний (основная функция)</h3>
 * Вход в код плагина кладёт {@link ExecutionInfo} в {@link #executingPlugins}
 * по текущему потоку и ставит отложенную проверку на {@link #FREEZE_TIMEOUT_SECONDS}
 * секунд. Если к моменту проверки в мапе лежит тот же самый объект — плагин
 * не вернул управление: он помечается «не отвечает», уезжает в
 * {@link #frozenExecutions} и летит {@link NotificationCenter#pluginIsNotResponding}.
 * Возврат из плагина снимает пометку.
 *
 * <h3>Почему всё в памяти</h3>
 * Хуки исполняются синхронно в потоке вызывающего, и плагин, захукавший
 * что-нибудь горячее (входящий апдейт, отрисовку, исходящий запрос), проходит
 * здесь тысячи раз в секунду. Поэтому на этом пути нет ни одной операции
 * ввода-вывода: put/remove в {@link ConcurrentHashMap} и постановка задачи в
 * планировщик. Ранняя версия этого класса делала здесь
 * {@code SharedPreferences.commit()} — синхронную перезапись всего XML с
 * fsync, по две на вызов, — что само по себе давало ANR.
 *
 * <h3>Атрибуция падений</h3>
 * Смерть процесса (нативный краш, kill по памяти) не даёт ничего записать в
 * момент падения, поэтому id активного плагина заранее лежит в маленьком файле
 * {@code plugins/.watchdog}. Запись — {@link RandomAccessFile} без fsync
 * (страничный кэш переживает смерть процесса), и только когда значение меняется;
 * очистка после выхода из плагина отложена на {@link #IDLE_CLEAR_DELAY_MS}, так
 * что серия из тысяч вызовов подряд стоит одной записи. На следующем старте
 * {@link #recoverAfterCrash()} отключает виновника.
 *
 * <h3>Ошибки моста</h3>
 * Java-исключение из Python ловится в {@link PythonPluginsEngine} и приводит к
 * {@link #handlePluginError(String, Throwable)} — плагин отключается сразу.
 *
 * Safe mode ({@link PluginsConstants#KEY_SAFE_MODE}) — старт вообще без плагинов.
 */
public class PluginsWatchdog {

    /** Столько ждём возврата из плагина, прежде чем считать его зависшим. Как в exteraGram. */
    private static final int FREEZE_TIMEOUT_SECONDS = 5;
    /** Задержка очистки файла-маркера после выхода из последнего плагина. */
    private static final long IDLE_CLEAR_DELAY_MS = 2000L;
    /**
     * Столько заход должен длиться, прежде чем попадёт в маркер.
     *
     * Раньше маркер писался на каждый вход в код плагина. У плагина с хуками
     * (AdBlock ставит семь) хуки срабатывают постоянно, поэтому маркер стоял
     * практически всегда — и любая смерть процесса вешалась на него. На
     * устройстве это выглядело так: два force-stop подряд, и рабочий плагин
     * выключен с диалогом «Plugin crashed».
     *
     * Маркер нужен ровно для одного случая: процесс умер, НЕ ВЕРНУВШИСЬ из
     * кода плагина. Заход, отработавший за микросекунды, таким свидетельством
     * не является. Порог отсекает нормальные хуки и оставляет то, ради чего
     * сторож и написан: загрузку, зависание, долгий вызов.
     */
    private static final long MARKER_AFTER_MS = 1000L;
    /** Имя файла-маркера в каталоге плагинов. */
    private static final String MARKER_FILE = ".watchdog";
    /** Столько раз процесс должен умереть на плагине, прежде чем его выключат. */
    private static final int CRASH_STRIKES_BEFORE_DISABLE = 2;

    private static final long MAIN_BUDGET_WINDOW_MS = 1000L;
    private static final long MAIN_BUDGET_NANOS = 250L * 1_000_000L;
    private static final int SLOW_WINDOWS_BEFORE_ALERT = 3;

    /** Один заход в код плагина. Сравнивается по ссылке — так проверка узнаёт «тот же самый». */
    public static final class ExecutionInfo {
        final String pluginId;
        final long mainEnterNanos;

        ExecutionInfo(String pluginId, long mainEnterNanos) {
            this.pluginId = pluginId;
            this.mainEnterNanos = mainEnterNanos;
        }

        public String getPluginId() {
            return pluginId;
        }
    }

    private final SharedPreferences preferences;

    /** Поток -> что он сейчас исполняет. */
    private final ConcurrentHashMap<Thread, ExecutionInfo> executingPlugins = new ConcurrentHashMap<>();
    /** Поток -> заход, признанный зависшим. */
    private final ConcurrentHashMap<Thread, ExecutionInfo> frozenExecutions = new ConcurrentHashMap<>();
    /** Поток -> его отложенная проверка на зависание. */
    private final ConcurrentHashMap<Thread, ScheduledFuture<?>> scheduledChecks = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, AtomicLong> mainThreadNanos = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> overBudgetWindows = new ConcurrentHashMap<>();
    private final Set<String> loggedSlow = ConcurrentHashMap.newKeySet();
    private final Set<String> alertedSlow = ConcurrentHashMap.newKeySet();

    private volatile ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> budgetSweep;
    private final Thread mainThread = android.os.Looper.getMainLooper().getThread();

    /** Файл-маркер и его текущее содержимое (чтобы не писать одно и то же). */
    private volatile RandomAccessFile markerFile;
    private volatile String persistedMarker;
    private volatile ScheduledFuture<?> pendingMarkerClear;

    PluginsWatchdog(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    // ---------- жизненный цикл ----------

    /** Поднять планировщик. Зовётся при старте движка. */
    public void start() {
        ScheduledExecutorService existing = scheduler;
        if (existing == null || existing.isShutdown()) {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
                Thread thread = new Thread(r, "plugins-watchdog");
                thread.setDaemon(true);
                return thread;
            });
            // Отменённые проверки не должны копиться в очереди: их тут по одной
            // на каждый вызов плагина.
            executor.setRemoveOnCancelPolicy(true);
            scheduler = executor;
        }
        ScheduledExecutorService active = scheduler;
        if (active != null && budgetSweep == null) {
            try {
                budgetSweep = active.scheduleWithFixedDelay(this::sweepMainThreadBudget,
                        MAIN_BUDGET_WINDOW_MS, MAIN_BUDGET_WINDOW_MS, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ignored) {
            }
        }
    }

    /** Остановить планировщик и снять все пометки «не отвечает». */
    public void stop() {
        for (ScheduledFuture<?> check : scheduledChecks.values()) {
            check.cancel(false);
        }
        scheduledChecks.clear();
        ScheduledFuture<?> sweep = budgetSweep;
        if (sweep != null) {
            sweep.cancel(false);
        }
        budgetSweep = null;
        mainThreadNanos.clear();
        overBudgetWindows.clear();
        loggedSlow.clear();
        alertedSlow.clear();
        ScheduledExecutorService existing = scheduler;
        if (existing != null) {
            existing.shutdownNow();
        }
        scheduler = null;
        frozenExecutions.clear();
        executingPlugins.clear();
        clearMarkerNow();
    }

    // ---------- горячий путь ----------

    /**
     * Плагин начал исполняться в текущем потоке. Только память и постановка
     * задачи в планировщик — никакого ввода-вывода.
     */
    public void notePluginEnter(String pluginId) {
        notePluginEnter(pluginId, false);
    }

    /**
     * @param risky заход, который стоит записать в маркер сразу, не дожидаясь
     *              {@link #MARKER_AFTER_MS}. Это загрузка плагина: если процесс
     *              умрёт на ней за доли секунды, отложенная запись не успеет, и
     *              приложение уйдёт в вечный цикл падений на старте — ровно то,
     *              ради чего сторож и существует.
     */
    public void notePluginEnter(String pluginId, boolean risky) {
        if (pluginId == null) {
            return;
        }
        Thread thread = Thread.currentThread();
        ExecutionInfo info = new ExecutionInfo(pluginId,
                thread == mainThread ? System.nanoTime() : 0L);
        executingPlugins.put(thread, info);

        // Этот поток раньше висел — значит, отвис.
        ExecutionInfo wasFrozen = frozenExecutions.remove(thread);
        if (wasFrozen != null) {
            if (wasFrozen.pluginId.equals(pluginId)) {
                // Тот же плагин снова в работе: считаем, что он всё ещё висит.
                frozenExecutions.put(thread, info);
            } else {
                notifyRecoveredIfLastFrozen(wasFrozen.pluginId);
            }
        }

        if (risky) {
            noteMarker(pluginId);
        }

        ScheduledFuture<?> previous = scheduledChecks.remove(thread);
        if (previous != null) {
            previous.cancel(false);
        }
        ScheduledExecutorService executor = scheduler;
        if (executor == null) {
            return;
        }
        try {
            // Маркер ставит отложенная задача: если заход к этому моменту уже
            // закончился, писать нечего.
            if (!risky) {
                executor.schedule(() -> {
                    if (executingPlugins.get(thread) == info) {
                        noteMarker(pluginId);
                    }
                }, MARKER_AFTER_MS, TimeUnit.MILLISECONDS);
            }
            scheduledChecks.put(thread, executor.schedule(
                    () -> checkFrozen(thread, info, pluginId),
                    FREEZE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (RejectedExecutionException e) {
            // Планировщик уже гасится — просто не следим за этим заходом.
            executingPlugins.remove(thread, info);
        }
    }

    /**
     * Приложение уходит в фон. Дальше Android может убить процесс когда угодно,
     * и это не падение плагина — маркер надо снять, иначе следующий запуск
     * засчитает страйк ни за что.
     */
    public void onAppBackgrounded() {
        clearMarkerNow();
    }

    /** Сессия дожила до фона без падения — обнулить страйки плагина. */
    public void noteHealthy(String pluginId) {
        if (pluginId == null) {
            return;
        }
        String key = PluginsConstants.KEY_WATCHDOG_STRIKES_PREFIX + pluginId;
        if (preferences.getInt(key, 0) != 0) {
            preferences.edit().remove(key).apply();
        }
    }

    /** Плагин вернул управление. */
    public void notePluginExit(String pluginId) {
        if (pluginId == null) {
            return;
        }
        Thread thread = Thread.currentThread();
        ExecutionInfo info = executingPlugins.get(thread);
        if (info == null || !info.pluginId.equals(pluginId)) {
            return;
        }
        if (!executingPlugins.remove(thread, info)) {
            return;
        }
        if (info.mainEnterNanos != 0L) {
            mainThreadNanos.computeIfAbsent(pluginId, id -> new AtomicLong())
                    .addAndGet(System.nanoTime() - info.mainEnterNanos);
        }
        ScheduledFuture<?> check = scheduledChecks.remove(thread);
        if (check != null) {
            check.cancel(false);
        }
        ExecutionInfo wasFrozen = frozenExecutions.remove(thread);
        if (wasFrozen != null) {
            notifyRecoveredIfLastFrozen(wasFrozen.pluginId);
        }
        scheduleMarkerClear();
    }

    private void sweepMainThreadBudget() {
        for (Map.Entry<String, AtomicLong> entry : mainThreadNanos.entrySet()) {
            final String pluginId = entry.getKey();
            final long spent = entry.getValue().getAndSet(0L);
            AtomicInteger streak = overBudgetWindows.computeIfAbsent(pluginId, id -> new AtomicInteger());
            if (spent < MAIN_BUDGET_NANOS) {
                streak.set(0);
                continue;
            }
            if (streak.incrementAndGet() < SLOW_WINDOWS_BEFORE_ALERT) {
                continue;
            }
            streak.set(0);
            final long millis = spent / 1_000_000L;
            if (loggedSlow.add(pluginId)) {
                FileLog.e("PluginsWatchdog: plugin " + pluginId + " is holding the main thread for "
                        + millis + "ms per second");
            }
            Activity activity = currentActivity();
            if (activity != null && alertedSlow.add(pluginId)) {
                showSlowingDownAlert(pluginId, activity, millis);
            }
        }
    }

    /** Проверка «не вернулся за {@value #FREEZE_TIMEOUT_SECONDS} с». Идёт в потоке планировщика. */
    private void checkFrozen(Thread thread, ExecutionInfo expected, String pluginId) {
        boolean[] froze = new boolean[1];
        executingPlugins.computeIfPresent(thread, (t, current) -> {
            if (current == expected) {
                frozenExecutions.put(thread, expected);
                froze[0] = true;
            }
            return current;
        });
        if (froze[0]) {
            FileLog.e("PluginsWatchdog: plugin " + pluginId + " is not responding on "
                    + thread.getName());
            NotificationCenter.getGlobalInstance()
                    .postNotificationNameOnUIThread(NotificationCenter.pluginIsNotResponding, pluginId);
            showNotRespondingAlert(pluginId, currentActivity());
        }
    }

    /** Текущая Activity, если UI поднят. Диалог без неё показать негде. */
    private static Activity currentActivity() {
        try {
            org.telegram.ui.ActionBar.BaseFragment fragment =
                    org.telegram.ui.LaunchActivity.getLastFragment();
            return fragment != null ? fragment.getParentActivity() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Плагин отвис — но только если он не висит ещё и в другом потоке. */
    private void notifyRecoveredIfLastFrozen(String pluginId) {
        for (ExecutionInfo other : frozenExecutions.values()) {
            if (other.pluginId.equals(pluginId)) {
                return;
            }
        }
        NotificationCenter.getGlobalInstance()
                .postNotificationNameOnUIThread(NotificationCenter.pluginIsNotResponding, pluginId);
    }

    /** Висит ли плагин прямо сейчас (для UI). */
    public boolean isNotResponding(String pluginId) {
        if (pluginId == null) {
            return false;
        }
        for (ExecutionInfo info : frozenExecutions.values()) {
            if (info.pluginId.equals(pluginId)) {
                return true;
            }
        }
        return false;
    }

    // ---------- файл-маркер (атрибуция падений) ----------

    private File markerPath() {
        File dir = PluginsController.getInstance().getPluginsDir();
        return dir == null ? null : new File(dir, MARKER_FILE);
    }

    /**
     * Записать id плагина в маркер, если он изменился. Без fsync: нам нужно
     * пережить смерть процесса, а не отключение питания, и страничный кэш ядра
     * это обеспечивает.
     */
    private void noteMarker(String pluginId) {
        ScheduledFuture<?> pending = pendingMarkerClear;
        if (pending != null) {
            pending.cancel(false);
            pendingMarkerClear = null;
        }
        if (pluginId.equals(persistedMarker)) {
            return;
        }
        try {
            RandomAccessFile file = markerFile;
            if (file == null) {
                File path = markerPath();
                if (path == null) {
                    return;
                }
                file = new RandomAccessFile(path, "rw");
                markerFile = file;
            }
            byte[] bytes = pluginId.getBytes(StandardCharsets.UTF_8);
            file.seek(0);
            file.write(bytes);
            file.setLength(bytes.length);
            persistedMarker = pluginId;
        } catch (Throwable t) {
            FileLog.e("PluginsWatchdog: cannot write crash marker", t);
            markerFile = null;
        }
    }

    /** Очистить маркер, когда плагины перестанут исполняться. Отложенно — серия вызовов стоит одной записи. */
    private void scheduleMarkerClear() {
        if (persistedMarker == null) {
            return;
        }
        ScheduledExecutorService executor = scheduler;
        if (executor == null) {
            clearMarkerNow();
            return;
        }
        ScheduledFuture<?> pending = pendingMarkerClear;
        if (pending != null && !pending.isDone()) {
            return;
        }
        try {
            pendingMarkerClear = executor.schedule(() -> {
                if (executingPlugins.isEmpty()) {
                    clearMarkerNow();
                }
            }, IDLE_CLEAR_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            clearMarkerNow();
        }
    }

    private void clearMarkerNow() {
        if (persistedMarker == null) {
            return;
        }
        try {
            RandomAccessFile file = markerFile;
            if (file != null) {
                file.setLength(0);
            }
            persistedMarker = null;
        } catch (Throwable t) {
            FileLog.e("PluginsWatchdog: cannot clear crash marker", t);
        }
    }

    /**
     * Вызвать при старте движка, до загрузки любых плагинов.
     * Если в прошлой сессии процесс умер на плагине — отключить его.
     *
     * @return id отключённого плагина или null.
     */
    public String recoverAfterCrash() {
        // Ранняя версия писала маркер в SharedPreferences; чистим остаток и,
        // если он там был, считаем его виновником этого запуска.
        String legacy = preferences.getString(PluginsConstants.KEY_WATCHDOG_LOADING_LEGACY, null);
        if (legacy != null) {
            preferences.edit()
                    .remove(PluginsConstants.KEY_WATCHDOG_LOADING_LEGACY)
                    .putBoolean(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + legacy, false)
                    .putString(PluginsConstants.KEY_WATCHDOG_CRASHED, legacy)
                    .apply();
            return legacy;
        }
        File path = markerPath();
        if (path == null || !path.isFile() || path.length() == 0) {
            return null;
        }
        String crashed = null;
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            byte[] bytes = new byte[(int) Math.min(file.length(), 256)];
            file.readFully(bytes);
            crashed = new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            FileLog.e("PluginsWatchdog: cannot read crash marker", t);
        }
        //noinspection ResultOfMethodCallIgnored
        path.delete();
        if (crashed == null || crashed.isEmpty()) {
            return null;
        }
        String strikeKey = PluginsConstants.KEY_WATCHDOG_STRIKES_PREFIX + crashed;
        int strikes = preferences.getInt(strikeKey, 0) + 1;
        if (strikes < CRASH_STRIKES_BEFORE_DISABLE) {
            preferences.edit().putInt(strikeKey, strikes).apply();
            FileLog.e("PluginsWatchdog: plugin " + crashed + " was active when the process died ("
                    + strikes + "/" + CRASH_STRIKES_BEFORE_DISABLE + "), keeping it enabled");
            return null;
        }
        preferences.edit()
                .remove(strikeKey)
                .putBoolean(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + crashed, false)
                .putString(PluginsConstants.KEY_WATCHDOG_CRASHED, crashed)
                .apply();
        FileLog.e("PluginsWatchdog: plugin " + crashed + " active during "
                + CRASH_STRIKES_BEFORE_DISABLE + " process deaths, disabled");
        return crashed;
    }

    // ---------- отключение ----------

    /**
     * Плагин кинул исключение через мост.
     *
     * Только лог. Отключать плагин за брошенное исключение нельзя: у exteraGram
     * {@code executeGenericHook} ловит Throwable, зовёт
     * {@code onPluginExecutionFinished} и пробрасывает дальше — плагин
     * продолжает жить. Ошибка в одном хуке — это ошибка в одном хуке, а не
     * повод молча выключить плагин: из-за прежнего поведения рабочий плагин
     * терял кнопку настроек после пары выполненных команд.
     *
     * Отключение остаётся за пользователем (диалог «не отвечает») и за
     * повторными смертями процесса ({@link #recoverAfterCrash()}).
     */
    public void handlePluginError(String pluginId, Throwable t) {
        FileLog.e("PluginsWatchdog: plugin " + pluginId + " threw", t);
        notePluginExit(pluginId);
    }

    private void disablePluginPref(String pluginId) {
        preferences.edit()
                .putBoolean(PluginsConstants.KEY_PLUGIN_ENABLED_PREFIX + pluginId, false)
                .apply();
    }

    /** Отключить зависший плагин и перезапустить приложение (пункт диалога). */
    public void forceDisablePlugin(String pluginId, Activity activity) {
        disablePluginPref(pluginId);
        clearMarkerNow();
        restartApp(activity);
    }

    private void restartApp(Activity activity) {
        if (activity == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                ApplicationLoader.applicationContext.startActivity(
                        ApplicationLoader.applicationContext.getPackageManager()
                                .getLaunchIntentForPackage(
                                        ApplicationLoader.applicationContext.getPackageName())
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t) {
                FileLog.e("PluginsWatchdog: restart failed", t);
            }
            System.exit(0);
        });
    }

    /** Диалог «плагин не отвечает» с предложением его отключить. */
    public void showNotRespondingAlert(String pluginId, Activity activity) {
        if (activity == null || pluginId == null) {
            return;
        }
        String name = pluginId;
        for (Plugin plugin : PluginsController.getInstance().getPluginsSnapshot()) {
            if (pluginId.equals(plugin.id)) {
                name = plugin.getDisplayName();
                break;
            }
        }
        final String displayName = name;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                new AlertDialog.Builder(activity)
                        .setTitle(LocaleController.getString(R.string.PluginNotRespondingTitle))
                        .setMessage(LocaleController.formatString(
                                R.string.PluginNotRespondingMessage, displayName))
                        .setPositiveButton(LocaleController.getString(R.string.PluginDisable),
                                (dialog, which) -> forceDisablePlugin(pluginId, activity))
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .show();
            } catch (Throwable t) {
                FileLog.e("PluginsWatchdog: cannot show not-responding alert", t);
            }
        });
    }

    public void showSlowingDownAlert(String pluginId, Activity activity, long millisPerSecond) {
        if (activity == null || pluginId == null) {
            return;
        }
        String name = pluginId;
        for (Plugin plugin : PluginsController.getInstance().getPluginsSnapshot()) {
            if (pluginId.equals(plugin.id)) {
                name = plugin.getDisplayName();
                break;
            }
        }
        final String displayName = name;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                new AlertDialog.Builder(activity)
                        .setTitle(LocaleController.getString(R.string.PluginSlowingDownTitle))
                        .setMessage(LocaleController.formatString(
                                R.string.PluginSlowingDownMessage, displayName, millisPerSecond))
                        .setPositiveButton(LocaleController.getString(R.string.PluginDisable),
                                (dialog, which) -> forceDisablePlugin(pluginId, activity))
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .show();
            } catch (Throwable t) {
                FileLog.e("PluginsWatchdog: cannot show slowing-down alert", t);
            }
        });
    }

    public long getMainThreadMillis(String pluginId) {
        AtomicLong counter = pluginId == null ? null : mainThreadNanos.get(pluginId);
        return counter == null ? 0L : counter.get() / 1_000_000L;
    }

    /** id плагина, отключённого после падения (однократное чтение для UI, потом очистить). */
    public String consumeCrashedPlugin() {
        String id = preferences.getString(PluginsConstants.KEY_WATCHDOG_CRASHED, null);
        if (id != null) {
            preferences.edit().remove(PluginsConstants.KEY_WATCHDOG_CRASHED).apply();
        }
        return id;
    }

    /** Снимок текущих исполняющихся плагинов (диагностика). */
    public Map<Thread, ExecutionInfo> getExecutingPlugins() {
        return executingPlugins;
    }
}
