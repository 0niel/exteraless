package com.exteragram.messenger.plugins;

/**
 * Основание модели плагина под именем exteraGram.
 *
 * Существует ради dex-модулей: они получают объект плагина рефлексией и
 * проверяют его тип через `isAssignableFrom`, поэтому наш `Plugin` обязан
 * быть наследником именно этого класса, а не просто одноимённым.
 */
public abstract class Plugin {

    public abstract String getId();

    public abstract String getName();

    public abstract String getAuthor();

    public abstract String getVersion();
}
