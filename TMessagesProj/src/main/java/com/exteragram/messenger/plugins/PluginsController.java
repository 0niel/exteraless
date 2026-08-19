package com.exteragram.messenger.plugins;

/**
 * Основание контроллера плагинов под именем exteraGram.
 *
 * dex-модули зовут `Class.forName(...).getMethod("getInstance")` и следом
 * `loadPluginSettings(String)`, поэтому оба метода объявлены здесь.
 */
public abstract class PluginsController {

    public static PluginsController getInstance() {
        return app.exteraless.plugins.PluginsController.getInstance();
    }

    public abstract void loadPluginSettings(String pluginId);
}
