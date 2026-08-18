package com.exteragram.messenger.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import app.exteraless.plugins.PythonPluginsEngine;

/**
 * Шим {@code com.exteragram.messenger.plugins.PluginsController} — самый
 * востребованный класс каталога (30 плагинов из 361).
 *
 * Делегирует в {@link app.exteraless.plugins.PluginsController}. Поверхность
 * повторяет ровно то, что плагины действительно трогают; имена полей и методов
 * взяты у exteraGram, а не у нас, потому что обращения идут по именам.
 */
public final class PluginsController {

    private static volatile PluginsController instance;

    /**
     * Живая карта id → плагин. Плагины ходят сюда напрямую:
     * {@code PluginsController.getInstance().plugins.get(__id__)} — 39 обращений
     * в каталоге, самый частый вызов вообще. Ссылка на внутреннюю карту
     * контроллера, а не копия, поэтому остаётся актуальной.
     */
    public final Map<String, app.exteraless.plugins.Plugin> plugins;

    /** Каталог с файлами плагинов; у exteraGram это тоже поле, а не геттер. */
    public final File pluginsDir;

    private PluginsController() {
        plugins = delegate().getPluginsMap();
        File dir = null;
        try {
            dir = delegate().getPluginsDir();
        } catch (Throwable t) {
            // Движок ещё не инициализирован — плагин сюда попасть не должен,
            // но падать на чтении поля всё равно незачем.
            FileLog.e("PluginsController shim: plugins dir unavailable", t);
        }
        pluginsDir = dir;
    }

    private static app.exteraless.plugins.PluginsController delegate() {
        return app.exteraless.plugins.PluginsController.getInstance();
    }

    public static PluginsController getInstance() {
        if (instance == null) {
            synchronized (PluginsController.class) {
                if (instance == null) {
                    instance = new PluginsController();
                }
            }
        }
        return instance;
    }

    // ---------- плагины ----------

    public List<app.exteraless.plugins.Plugin> getPlugins() {
        return delegate().getPlugins();
    }

    public app.exteraless.plugins.Plugin getPlugin(String pluginId) {
        return delegate().getPlugin(pluginId);
    }

    /** У exteraGram — проверка «есть ли такой плагин». */
    public boolean isPlugin(String pluginId) {
        return delegate().getPlugin(pluginId) != null;
    }

    public String getPluginPath(String pluginId) {
        app.exteraless.plugins.Plugin plugin = delegate().getPlugin(pluginId);
        return plugin != null ? plugin.path : null;
    }

    public boolean setPluginEnabled(String pluginId, boolean enabled) {
        return delegate().setPluginEnabled(pluginId, enabled);
    }

    /**
     * Та же операция с колбэком: менеджеры плагинов зовут её с тремя аргументами,
     * третий — Runnable либо null.
     */
    public boolean setPluginEnabled(String pluginId, boolean enabled, Object callback) {
        boolean result = setPluginEnabled(pluginId, enabled);
        if (callback instanceof Runnable) {
            try {
                ((Runnable) callback).run();
            } catch (Throwable t) {
                FileLog.e("PluginsController shim: setPluginEnabled callback failed", t);
            }
        }
        return result;
    }

    public void reloadPlugin(String pluginId) {
        delegate().reloadPlugin(pluginId);
    }

    public boolean deletePlugin(String pluginId) {
        return delegate().uninstallPlugin(pluginId);
    }

    // ---------- настройки ----------

    /**
     * У exteraGram возвращает список элементов настроек; здесь достаточно
     * побочного эффекта — плагины зовут это перед открытием своего экрана,
     * чтобы настройки были построены (15 обращений в каталоге).
     */
    public String loadPluginSettings(String pluginId) {
        String json = delegate().getPluginSettingsJson(pluginId);
        // Плагины зовут это, когда хотят, чтобы открытый экран перерисовался
        // (у exteraGram список настроек и есть модель экрана). Сам по себе
        // пересбор JSON ничего на экране не меняет — дёргаем слушателей.
        delegate().reloadSettingsScreen(pluginId);
        return json;
    }

    public boolean hasPluginSettings(String pluginId) {
        app.exteraless.plugins.Plugin plugin = delegate().getPlugin(pluginId);
        return plugin != null && plugin.hasSettings;
    }

    public void invalidatePluginSettings(String pluginId) {
        delegate().reloadSettingsScreen(pluginId);
    }

    public String getPluginSettingString(String pluginId, String key, String defaultValue) {
        String json = delegate().getPluginSettingJson(pluginId, key);
        if (json == null) {
            return defaultValue;
        }
        // Значения хранятся в JSON; строка приезжает в кавычках.
        if (json.length() >= 2 && json.charAt(0) == '"' && json.endsWith("\"")) {
            return json.substring(1, json.length() - 1);
        }
        return json;
    }

    public boolean getPluginSettingBoolean(String pluginId, String key, boolean defaultValue) {
        String json = delegate().getPluginSettingJson(pluginId, key);
        return json == null ? defaultValue : "true".equals(json.trim());
    }

    public int getPluginSettingInt(String pluginId, String key, int defaultValue) {
        String json = delegate().getPluginSettingJson(pluginId, key);
        if (json == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(json.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void setPluginSetting(String pluginId, String key, Object value) {
        String json;
        if (value == null) {
            json = "null";
        } else if (value instanceof Boolean || value instanceof Number) {
            json = String.valueOf(value);
        } else {
            json = org.json.JSONObject.quote(String.valueOf(value));
        }
        delegate().setPluginSettingJson(pluginId, key, json, true);
    }

    /** Открыть экран настроек плагина поверх текущего фрагмента. */
    public void openPluginSettings(String pluginId) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null) {
            FileLog.e("PluginsController shim: no visible fragment to open settings on");
            return;
        }
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                fragment.presentFragment(
                        new app.exteraless.plugins.ui.PluginSettingsActivity(pluginId)));
    }

    // ---------- движок ----------

    /**
     * У exteraGram движков несколько (по языкам), у нас только Python — карта
     * повторяет форму, чтобы {@code engines.get(PluginsConstants.PYTHON)}
     * возвращал что-то осмысленное.
     */
    public final Map<String, PythonPluginsEngine> engines =
            Collections.singletonMap(PluginsConstants.PYTHON, PythonPluginsEngine.getInstance());

    public Map<String, PythonPluginsEngine> getEngines() {
        return engines;
    }

    public PythonPluginsEngine getPluginEngine(String pluginId) {
        return PythonPluginsEngine.getInstance();
    }

    public void runOnPluginsQueue(Runnable runnable) {
        Utilities.globalQueue.postRunnable(runnable);
    }

    public android.content.SharedPreferences getPreferences() {
        return delegate().getPreferences();
    }

    public boolean isInitialized() {
        return delegate().isEngineEnabled();
    }

    public void restart() {
        delegate().rescanAndLoadEnabled();
    }

    public void shutdown() {
        delegate().setEngineEnabled(false);
    }
}
