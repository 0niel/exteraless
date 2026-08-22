package com.exteragram.messenger.plugins;

/**
 * Основание контроллера плагинов под именем exteraGram.
 *
 * dex-модули берут именно этот класс (`PluginsController.class`) и перебирают его
 * `getDeclaredMethods()`, поэтому всё, что они зовут, обязано быть объявлено здесь,
 * а не только у наследника: унаследованные и объявленные ниже по иерархии методы
 * такой перебор не видит.
 */
public abstract class PluginsController {

    public static PluginsController getInstance() {
        return app.exteraless.plugins.PluginsController.getInstance();
    }

    public abstract void loadPluginSettings(String pluginId);

    public abstract java.util.Map<String, ? extends Plugin> getPlugins();
}
