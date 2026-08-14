package app.exteraless.plugins;

import org.telegram.messenger.FileLog;

/**
 * Статические методы, которые Python-SDK зовёт через Chaquopy
 * ({@code from app.exteraless.plugins import PythonBridge}).
 *
 * Мост намеренно узкий: через него ходят только String/int/boolean
 * (сложные значения — JSON-строками, кодирует/декодирует Python-сторона).
 * Вся логика — в {@link PluginsController}, этот класс только фасад,
 * безопасный для вызова из интерпретатора.
 */
public final class PythonBridge {

    private PythonBridge() {
    }

    private static PluginsController controller() {
        return PluginsController.getInstance();
    }

    // ---------- настройки плагина ----------

    /** @return JSON-значение настройки или null, если не сохранена. */
    public static String getSetting(String pluginId, String key) {
        return controller().getPluginSettingJson(pluginId, key);
    }

    public static void setSetting(String pluginId, String key, String jsonValue, boolean reloadSettings) {
        controller().setPluginSettingJson(pluginId, key, jsonValue, reloadSettings);
    }

    /** @return JSON-объект всех настроек плагина. */
    public static String exportSettings(String pluginId) {
        return controller().exportPluginSettings(pluginId);
    }

    public static void importSettings(String pluginId, String json, boolean reloadSettings) {
        controller().importPluginSettings(pluginId, json, reloadSettings);
    }

    // ---------- логирование ----------

    public static void log(String pluginId, String message) {
        FileLog.d("[plugin:" + pluginId + "] " + message);
    }

    // ---------- регистрация хуков ----------

    public static void addRequestHook(String pluginId, String requestName, boolean matchSubstring, int priority) {
        controller().registerRequestHook(pluginId, requestName, matchSubstring, priority);
    }

    public static void addSendMessageHook(String pluginId, int priority) {
        controller().registerSendMessageHook(pluginId, priority);
    }

    // ---------- меню ----------

    /** @return item_id пункта меню. onClick зовётся с Map-контекстом меню. */
    public static String addMenuItem(String pluginId, String jsonMenuItem,
                                     com.chaquo.python.PyObject onClick) {
        return controller().registerMenuItem(pluginId, jsonMenuItem, onClick);
    }

    public static void removeMenuItem(String pluginId, String itemId) {
        controller().removeMenuItem(pluginId, itemId);
    }

    // ---------- прочее ----------

    /** Абсолютный путь к каталогу плагинов (filesDir/plugins). */
    public static String getPluginsDir() {
        return controller().getPluginsDir().getAbsolutePath();
    }

    /** Версия нашего SDK (для проверки __sdk_version__). */
    public static String getSdkVersion() {
        return PluginsConstants.SDK_VERSION;
    }

    /** Попросить открытый экран настроек плагина перестроиться. */
    public static void reloadSettingsScreen(String pluginId) {
        controller().reloadSettingsScreen(pluginId);
    }
}
